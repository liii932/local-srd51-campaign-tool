package com.dndtool.persistence.storage;

import static com.dndtool.persistence.storage.StorageSchemaCatalog.*;

import com.dndtool.persistence.storage.StorageSchemaCatalog.Module;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable deterministic schema closure. Execution never owns the caller's transaction. */
public final class StoragePlan {
    private final List<Module> modules;
    private final List<Table> tables;
    private final Map<String, List<Map<String, Object>>> seeds;
    private final Map<String, List<String>> reasons;
    private final List<String> statements;
    private final String canonical;
    private final String digest;

    StoragePlan(List<Module> modules, Map<String, List<String>> reasons) {
        this.modules = List.copyOf(modules);
        this.reasons = Collections.unmodifiableMap(new TreeMap<>(reasons));
        List<Table> orderedTables = new ArrayList<>();
        Map<String, List<Map<String, Object>>> seedRows = new HashMap<>();
        for (Module module : modules) {
            orderedTables.addAll(orderTables(module.tables()));
            seedRows.putAll(module.seeds());
        }
        tables = List.copyOf(orderedTables);
        seeds = Collections.unmodifiableMap(seedRows);
        List<String> ddl = new ArrayList<>();
        for (Table table : tables) ddl.add(SqliteSchemaCompiler.tableSql(table));
        for (Table table : tables) for (Index index : table.indexes()) ddl.add(SqliteSchemaCompiler.indexSql(table, index));
        statements = List.copyOf(ddl);
        canonical = canonical(normalized());
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("DND-STORAGE-PLAN\0".getBytes(StandardCharsets.US_ASCII));
            sha.update("1\0".getBytes(StandardCharsets.US_ASCII));
            digest = HexFormat.of().formatHex(sha.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public List<String> moduleIds() { return modules.stream().map(Module::id).toList(); }
    public List<String> tableNames() { return tables.stream().map(Table::name).toList(); }
    public String digest() { return digest; }
    public Map<String, List<String>> inclusionReasons() { return reasons; }
    public List<String> createStatements() { return statements; }
    public String canonicalPlan() { return canonical; }

    /** Requires an empty main schema and an already-owned write transaction with foreign keys enabled. */
    public void create(Connection connection) throws SQLException {
        requireForeignKeys(connection);
        SqliteSchemaVerifier.verifyDatabaseBoundary(connection);
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT name FROM main.sqlite_schema")) {
            if (rows.next()) throw mismatch("NONEMPTY_DATABASE");
        }
        for (String sql : statements) try (Statement statement = connection.createStatement()) { statement.execute(sql); }
        for (Table table : tables) {
            List<Map<String, Object>> rows = seeds.getOrDefault(table.name(), List.of());
            if (rows.isEmpty()) continue;
            String sql = "INSERT INTO " + SqliteSchemaCompiler.quote(table.name()) + " ("
                    + SqliteSchemaCompiler.columns(table.columns().stream().map(Column::name).toList()) + ") VALUES ("
                    + String.join(", ", Collections.nCopies(table.columns().size(), "?")) + ")";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < table.columns().size(); i++) {
                        Column column = table.columns().get(i);
                        SqliteSchemaCompiler.bind(insert, i + 1, column, row.get(column.name()));
                    }
                    if (insert.executeUpdate() != 1) throw mismatch("SEED_INSERT");
                }
            }
        }
    }

    public void verify(Connection connection) throws SQLException { new SqliteSchemaVerifier().verify(connection, this); }

    static void requireForeignKeys(Connection connection) throws SQLException {
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA foreign_keys")) {
            if (!rows.next() || rows.getInt(1) != 1 || rows.next()) throw mismatch("FOREIGN_KEYS_DISABLED");
        }
    }

    static SQLException mismatch(String code) { return new SQLException("STORAGE_" + code, "DND_SCHEMA"); }
    List<Table> tables() { return tables; }
    Map<String, List<Map<String, Object>>> seeds() { return seeds; }

    private Map<String, Object> normalized() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("declarationVersion", 1);
        root.put("applicationModel", APPLICATION_MODEL);
        root.put("storageModel", STORAGE_MODEL);
        root.put("dialect", DIALECT);
        root.put("implementationContract", "sqlite-vitality-schema-1");
        List<Object> normalizedModules = new ArrayList<>();
        for (Module module : modules) {
            List<Table> moduleTables = tables.stream().filter(table -> table.owner().equals(module.id())).toList();
            List<Object> normalizedSeeds = new ArrayList<>();
            for (Table table : moduleTables) normalizedSeeds.add(Map.of("table", table.name(), "rows", seeds.getOrDefault(table.name(), List.of())));
            normalizedModules.add(Map.of("id", module.id(), "dependsOn", module.dependsOn(), "tables", moduleTables.stream().map(StoragePlan::normalizeTable).toList(), "seeds", normalizedSeeds));
        }
        root.put("modules", normalizedModules);
        return root;
    }

    private static Map<String, Object> normalizeTable(Table table) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", table.name());
        result.put("columns", table.columns().stream().map(StoragePlan::normalizeColumn).toList());
        result.put("primaryKey", table.primaryKey());
        result.put("uniqueKeys", table.uniqueKeys().stream().map(key -> Map.of("name", key.name(), "columns", key.columns())).toList());
        result.put("foreignKeys", table.foreignKeys().stream().map(fk -> Map.of("name", fk.name(), "columns", fk.columns(), "references", Map.of("table", fk.target(), "columns", fk.targetColumns()), "onDelete", "NO_ACTION", "onUpdate", "NO_ACTION", "deferred", fk.deferred())).toList());
        result.put("checks", table.checks().stream().map(check -> Map.of("name", check.name(), "expression", check.expression())).toList());
        result.put("indexes", table.indexes().stream().map(index -> Map.of("name", index.name(), "columns", index.columns(), "unique", index.unique())).toList());
        return result;
    }

    private static Map<String, Object> normalizeColumn(Column column) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", column.name());
        result.put("type", column.type());
        result.put("nullable", column.nullable());
        result.put("hasDefault", column.hasDefault());
        result.put("default", column.defaultValue());
        switch (column.type()) {
            case "int64" -> { result.put("minimum", Long.toString(column.minimum())); result.put("maximum", Long.toString(column.maximum())); }
            case "text" -> { result.put("minCodePoints", column.minLength()); result.put("maxCodePoints", column.maxLength()); result.put("ascii", column.ascii()); }
            case "bytes" -> result.put("maxBytes", column.maxLength());
            default -> { }
        }
        return result;
    }

    /** Full FK SCCs determine order. Every edge inside a cycle must be declared deferred. */
    static List<Table> orderTables(List<Table> input) {
        Map<String, Table> byName = new TreeMap<>();
        for (Table table : input) byName.put(table.name(), table);
        Tarjan graph = new Tarjan(byName);
        for (String name : byName.keySet()) if (!graph.indices.containsKey(name)) graph.visit(name);
        Map<String, Integer> componentByTable = new HashMap<>();
        for (int i = 0; i < graph.components.size(); i++) for (String name : graph.components.get(i)) componentByTable.put(name, i);
        for (Table table : input) for (ForeignKey fk : table.foreignKeys()) {
            if (componentByTable.containsKey(fk.target()) && componentByTable.get(table.name()).equals(componentByTable.get(fk.target())) && !fk.deferred())
                throw error("IMMEDIATE_FK_CYCLE", table.name() + "." + fk.name());
        }
        Set<Integer> done = new HashSet<>();
        List<Table> result = new ArrayList<>();
        while (done.size() < graph.components.size()) {
            List<Integer> layer = new ArrayList<>();
            for (int i = 0; i < graph.components.size(); i++) {
                if (done.contains(i)) continue;
                int component = i;
                boolean ready = graph.components.get(i).stream().map(byName::get).flatMap(table -> table.foreignKeys().stream())
                        .map(fk -> componentByTable.get(fk.target())).filter(java.util.Objects::nonNull)
                        .allMatch(parent -> parent == component || done.contains(parent));
                if (ready) layer.add(i);
            }
            if (layer.isEmpty()) throw error("FK_ORDER", "tables");
            layer.sort(Comparator.comparing(component -> graph.components.get(component).get(0)));
            for (int component : layer) for (String name : graph.components.get(component)) result.add(byName.get(name));
            done.addAll(layer);
        }
        return List.copyOf(result);
    }

    private static final class Tarjan {
        final Map<String, Table> tables;
        final Map<String, Integer> indices = new HashMap<>();
        final Map<String, Integer> low = new HashMap<>();
        final List<String> stack = new ArrayList<>();
        final Set<String> active = new HashSet<>();
        final List<List<String>> components = new ArrayList<>();
        int next;
        Tarjan(Map<String, Table> tables) { this.tables = tables; }
        void visit(String name) {
            indices.put(name, next);
            low.put(name, next++);
            stack.add(name);
            active.add(name);
            for (String target : new TreeSet<>(tables.get(name).foreignKeys().stream().map(ForeignKey::target).filter(tables::containsKey).toList())) {
                if (!indices.containsKey(target)) { visit(target); low.put(name, Math.min(low.get(name), low.get(target))); }
                else if (active.contains(target)) low.put(name, Math.min(low.get(name), indices.get(target)));
            }
            if (low.get(name).equals(indices.get(name))) {
                List<String> component = new ArrayList<>();
                String member;
                do { member = stack.remove(stack.size() - 1); active.remove(member); component.add(member); } while (!member.equals(name));
                Collections.sort(component);
                components.add(List.copyOf(component));
            }
        }
    }
}
