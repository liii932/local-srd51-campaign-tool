package com.dndtool.persistence.storage;

import static com.dndtool.persistence.storage.StorageSchemaCatalog.*;
import static com.dndtool.persistence.storage.StoragePlan.mismatch;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Read-only structural and static-data verification against the application's plan, never database claims. */
public final class SqliteSchemaVerifier {
    public void verify(Connection connection, StoragePlan plan) throws SQLException {
        StoragePlan.requireForeignKeys(connection);
        verifyDatabaseBoundary(connection);
        Map<String, ExpectedObject> expected = new HashMap<>();
        Map<String, Table> tables = new HashMap<>();
        for (Table table : plan.tables()) {
            tables.put(table.name(), table);
            expected.put(table.name(), new ExpectedObject("table", table.name(), SqliteSchemaCompiler.tableSql(table)));
            for (Index index : table.indexes()) expected.put(index.name(), new ExpectedObject("index", table.name(), SqliteSchemaCompiler.indexSql(table, index)));
        }
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT type,name,tbl_name,sql FROM main.sqlite_schema ORDER BY name")) {
            Set<String> seen = new HashSet<>();
            while (rows.next()) {
                String type = rows.getString("type"), name = rows.getString("name"), table = rows.getString("tbl_name"), sql = rows.getString("sql");
                ExpectedObject object = expected.get(name);
                if (object == null) {
                    // SQLite's own unique-constraint indexes are the only persisted internal objects in this dialect.
                    if (!type.equals("index") || sql != null || !tables.containsKey(table)
                            || !name.matches("sqlite_autoindex_" + table + "_[1-9][0-9]*")) throw mismatch("UNKNOWN_OBJECT");
                } else if (!seen.add(name) || !object.type().equals(type) || !object.table().equals(table) || !object.sql().equals(sql)) {
                    throw mismatch("OBJECT_DEFINITION");
                }
            }
            if (!seen.equals(expected.keySet())) throw mismatch("MISSING_OBJECT");
        }
        verifyTableFlags(connection, tables);
        for (Table table : plan.tables()) {
            verifyColumns(connection, table);
            verifyForeignKeys(connection, table);
            verifyIndexes(connection, table);
        }
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA main.integrity_check")) {
            if (!rows.next() || !"ok".equals(rows.getString(1)) || rows.next()) throw mismatch("INTEGRITY");
        }
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA main.foreign_key_check")) {
            if (rows.next()) throw mismatch("FOREIGN_KEY_DATA");
        }
        verifySeeds(connection, plan);
    }

    static void verifyDatabaseBoundary(Connection connection) throws SQLException {
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA database_list")) {
            while (rows.next()) if (!Set.of("main", "temp").contains(rows.getString("name"))) throw mismatch("ATTACHED_DATABASE");
        }
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT name FROM temp.sqlite_schema")) {
            if (rows.next()) throw mismatch("TEMP_OBJECT");
        }
    }

    private static void verifyTableFlags(Connection connection, Map<String, Table> expected) throws SQLException {
        Set<String> seen = new HashSet<>();
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA main.table_list")) {
            while (rows.next()) {
                String name = rows.getString("name");
                if (name.equals("sqlite_schema")) continue;
                Table table = expected.get(name);
                if (table == null || !seen.add(name) || !rows.getString("type").equals("table")
                        || rows.getInt("ncol") != table.columns().size() || rows.getInt("wr") != 1 || rows.getInt("strict") != 1) throw mismatch("TABLE_FLAGS");
            }
        }
        if (!seen.equals(expected.keySet())) throw mismatch("TABLE_FLAGS");
    }

    private static void verifyColumns(Connection connection, Table table) throws SQLException {
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA main.table_xinfo(" + SqliteSchemaCompiler.quote(table.name()) + ")")) {
            for (int i = 0; i < table.columns().size(); i++) {
                Column column = table.columns().get(i);
                String defaultSql = column.hasDefault() ? SqliteSchemaCompiler.literal(column.type(), column.defaultValue()) : null;
                if (!rows.next() || rows.getInt("cid") != i || !column.name().equals(rows.getString("name"))
                        || !SqliteSchemaCompiler.sqlType(column).equals(rows.getString("type"))
                        || rows.getInt("notnull") != (column.nullable() ? 0 : 1)
                        || !java.util.Objects.equals(defaultSql, rows.getString("dflt_value"))
                        || rows.getInt("pk") != table.primaryKey().indexOf(column.name()) + 1 || rows.getInt("hidden") != 0) throw mismatch("COLUMN_DEFINITION");
            }
            if (rows.next()) throw mismatch("COLUMN_DEFINITION");
        }
    }

    private static void verifyForeignKeys(Connection connection, Table table) throws SQLException {
        Map<Integer, List<ForeignPart>> actual = new TreeMap<>();
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA main.foreign_key_list(" + SqliteSchemaCompiler.quote(table.name()) + ")")) {
            while (rows.next()) {
                if (!rows.getString("on_delete").equals("NO ACTION") || !rows.getString("on_update").equals("NO ACTION")
                        || !rows.getString("match").equals("NONE")) throw mismatch("FOREIGN_KEY_DEFINITION");
                actual.computeIfAbsent(rows.getInt("id"), ignored -> new ArrayList<>()).add(new ForeignPart(rows.getInt("seq"), rows.getString("table"), rows.getString("from"), rows.getString("to")));
            }
        }
        List<String> observed = new ArrayList<>();
        for (List<ForeignPart> parts : actual.values()) {
            parts.sort(Comparator.comparingInt(ForeignPart::sequence));
            for (int i = 0; i < parts.size(); i++) if (parts.get(i).sequence() != i || !parts.get(i).table().equals(parts.get(0).table())) throw mismatch("FOREIGN_KEY_DEFINITION");
            observed.add(canonical(List.of(parts.get(0).table(), parts.stream().map(ForeignPart::from).toList(), parts.stream().map(ForeignPart::to).toList())));
        }
        List<String> expected = table.foreignKeys().stream().map(fk -> canonical(List.of(fk.target(), fk.columns(), fk.targetColumns()))).sorted().toList();
        observed.sort(Comparator.naturalOrder());
        if (!observed.equals(expected)) throw mismatch("FOREIGN_KEY_DEFINITION");
    }

    private static void verifyIndexes(Connection connection, Table table) throws SQLException {
        Map<String, Index> explicit = new HashMap<>();
        for (Index index : table.indexes()) explicit.put(index.name(), index);
        List<List<String>> expectedUnique = new ArrayList<>(table.uniqueKeys().stream().map(Key::columns)
                .filter(columns -> !columns.equals(table.primaryKey())).distinct().toList());
        Set<String> seen = new HashSet<>();
        boolean primarySeen = false;
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA main.index_list(" + SqliteSchemaCompiler.quote(table.name()) + ")")) {
            while (rows.next()) {
                String name = rows.getString("name"), origin = rows.getString("origin");
                if (rows.getInt("partial") != 0) throw mismatch("INDEX_DEFINITION");
                List<String> columns = indexColumns(connection, name);
                if (origin.equals("c")) {
                    Index index = explicit.get(name);
                    if (index == null || !seen.add(name) || rows.getInt("unique") != (index.unique() ? 1 : 0) || !columns.equals(index.columns())) throw mismatch("INDEX_DEFINITION");
                } else if (origin.equals("pk")) {
                    if (primarySeen || rows.getInt("unique") != 1 || !columns.equals(table.primaryKey())) throw mismatch("INDEX_DEFINITION");
                    primarySeen = true;
                } else if (origin.equals("u")) {
                    if (rows.getInt("unique") != 1 || !expectedUnique.remove(columns)) throw mismatch("INDEX_DEFINITION");
                } else throw mismatch("INDEX_DEFINITION");
            }
        }
        if (!primarySeen || !expectedUnique.isEmpty() || !seen.equals(explicit.keySet())) throw mismatch("INDEX_DEFINITION");
    }

    private static List<String> indexColumns(Connection connection, String name) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("PRAGMA main.index_xinfo(" + SqliteSchemaCompiler.quote(name) + ")")) {
            while (rows.next()) {
                if (rows.getInt("key") != 1) continue; // WITHOUT ROWID indexes carry auxiliary primary-key columns.
                if (rows.getInt("cid") < 0 || rows.getInt("desc") != 0 || !"BINARY".equals(rows.getString("coll")) || rows.getInt("seqno") != columns.size()) throw mismatch("INDEX_DEFINITION");
                columns.add(rows.getString("name"));
            }
        }
        return List.copyOf(columns);
    }

    private static void verifySeeds(Connection connection, StoragePlan plan) throws SQLException {
        for (Table table : plan.tables()) {
            if (!plan.seeds().containsKey(table.name())) continue;
            List<Map<String, Object>> expectedRows = plan.seeds().get(table.name());
            String sql = "SELECT " + SqliteSchemaCompiler.columns(table.columns().stream().map(Column::name).toList()) + " FROM main."
                    + SqliteSchemaCompiler.quote(table.name()) + " ORDER BY " + SqliteSchemaCompiler.columns(table.primaryKey());
            try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(sql)) {
                for (Map<String, Object> expected : expectedRows) {
                    if (!rows.next()) throw mismatch("STATIC_SEED");
                    for (int i = 0; i < table.columns().size(); i++) {
                        Column column = table.columns().get(i);
                        Object value = expected.get(column.name());
                        Object actual = rows.getObject(i + 1);
                        if (value == null) { if (actual != null) throw mismatch("STATIC_SEED"); }
                        else if (actual == null) throw mismatch("STATIC_SEED");
                        else switch (column.type()) {
                            case "int64" -> { if (!(actual instanceof Number) || rows.getLong(i + 1) != decimal(value, "seed")) throw mismatch("STATIC_SEED"); }
                            case "boolean" -> { if (!(actual instanceof Number) || rows.getInt(i + 1) != ((Boolean) value ? 1 : 0)) throw mismatch("STATIC_SEED"); }
                            case "text" -> { if (!(actual instanceof String) || !actual.equals(value)) throw mismatch("STATIC_SEED"); }
                            default -> { if (!(actual instanceof byte[]) || !Arrays.equals(rows.getBytes(i + 1), SqliteSchemaCompiler.binary(column.type(), (String) value))) throw mismatch("STATIC_SEED"); }
                        }
                    }
                }
                if (rows.next()) throw mismatch("STATIC_SEED");
            }
        }
    }

    private record ExpectedObject(String type, String table, String sql) { }
    private record ForeignPart(int sequence, String table, String from, String to) { }

}
