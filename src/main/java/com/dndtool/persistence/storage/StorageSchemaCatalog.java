package com.dndtool.persistence.storage;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Strict, bounded application-owned schema declarations. This is not a user schema API. */
public final class StorageSchemaCatalog {
    public static final String APPLICATION_MODEL = "campaign-spaces-3";
    public static final String STORAGE_MODEL = "sqlite-campaign-3";
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    static final String DIALECT = "sqlite-strict-1";
    private final Map<String, Module> modules;

    private StorageSchemaCatalog(Map<String, Module> modules) {
        this.modules = Collections.unmodifiableMap(new TreeMap<>(modules));
    }

    public static StorageSchemaCatalog bundled() {
        try (InputStream input = StorageSchemaCatalog.class.getResourceAsStream("/storage/campaign-schema.json")) {
            if (input == null) throw error("RESOURCE_MISSING", "$");
            return read(input.readNBytes(MAX_BYTES + 1));
        } catch (IOException failure) {
            throw error("RESOURCE_READ", "$");
        }
    }

    public static StorageSchemaCatalog read(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw error("BUDGET", "$");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) { throw error("UTF8", "$"); }
        if (text.charAt(0) == '\ufeff') throw error("BOM", "$");
        Object parsed;
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = readJson(reader, "$", 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw error("TRAILING_JSON", "$");
        } catch (IOException | IllegalStateException failure) { throw error("JSON", "$"); }
        Map<String, Object> root = object(parsed, "$");
        fields(root, Set.of("declarationVersion", "applicationModel", "storageModel", "dialect", "modules"), Set.of(), "$");
        if (integer(root.get("declarationVersion"), 1, 1, "$.declarationVersion") != 1
                || !APPLICATION_MODEL.equals(string(root.get("applicationModel"), "$.applicationModel"))
                || !STORAGE_MODEL.equals(string(root.get("storageModel"), "$.storageModel"))
                || !DIALECT.equals(string(root.get("dialect"), "$.dialect"))) throw error("UNSUPPORTED_MODEL", "$");
        List<Object> declarations = array(root.get("modules"), "$.modules");
        bounded(declarations.size(), 1, 128, "$.modules");
        Map<String, Module> modules = new TreeMap<>();
        Set<String> globalNames = new HashSet<>();
        int tableCount = 0;
        int seedCount = 0;
        for (int i = 0; i < declarations.size(); i++) {
            String path = "$.modules[" + i + "]";
            Map<String, Object> declaration = object(declarations.get(i), path);
            fields(declaration, Set.of("id", "dependsOn", "tables", "seeds"), Set.of(), path);
            String id = string(declaration.get("id"), path + ".id");
            if (!id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") || id.length() > 63) throw error("IDENTIFIER", path + ".id");
            List<String> dependencies = names(declaration.get("dependsOn"), false, path + ".dependsOn", false);
            List<Table> tables = new ArrayList<>();
            for (Object rawTable : array(declaration.get("tables"), path + ".tables")) {
                tables.add(readTable(rawTable, id, globalNames, path + ".tables[" + tables.size() + "]"));
                if (++tableCount > 512) throw error("BUDGET", path + ".tables");
            }
            if (tables.isEmpty()) throw error("EMPTY_MODULE", path);
            Map<String, Table> localTables = new HashMap<>();
            for (Table table : tables) localTables.put(table.name(), table);
            Map<String, List<Map<String, Object>>> seeds = new TreeMap<>();
            for (Object rawSeed : array(declaration.get("seeds"), path + ".seeds")) {
                Map<String, Object> seed = object(rawSeed, path + ".seeds");
                fields(seed, Set.of("table", "rows"), Set.of(), path + ".seeds");
                String tableName = identifier(seed.get("table"), path + ".seeds.table");
                Table table = localTables.get(tableName);
                if (table == null || seeds.containsKey(tableName)) throw error("SEED_OWNER", path + ".seeds");
                List<Map<String, Object>> rows = new ArrayList<>();
                Set<String> keys = new HashSet<>();
                for (Object rawRow : array(seed.get("rows"), path + ".seeds.rows")) {
                    if (++seedCount > 10000) throw error("BUDGET", path + ".seeds.rows");
                    Map<String, Object> row = object(rawRow, path + ".seeds.rows");
                    fields(row, table.columnMap().keySet(), Set.of(), path + ".seeds.rows");
                    for (Column column : table.columns()) column.validate(row.get(column.name()), path + ".seeds.rows." + column.name());
                    for (Check check : table.checks()) if (!Boolean.TRUE.equals(evaluate(check.expression(), table.columnMap(), row)))
                        throw error("SEED_CHECK", path + ".seeds.rows." + check.name());
                    String key = canonical(table.primaryKey().stream().map(row::get).toList());
                    if (!keys.add(key)) throw error("DUPLICATE_SEED", path + ".seeds.rows");
                    rows.add(immutableMap(row));
                }
                List<List<String>> uniqueMembers = new ArrayList<>(table.uniqueKeys().stream().map(Key::columns).toList());
                uniqueMembers.addAll(table.indexes().stream().filter(Index::unique).map(Index::columns).toList());
                for (List<String> members : uniqueMembers) {
                    Set<String> unique = new HashSet<>();
                    for (Map<String, Object> row : rows) {
                        List<Object> values = members.stream().map(row::get).toList();
                        if (values.stream().noneMatch(java.util.Objects::isNull) && !unique.add(canonical(values)))
                            throw error("SEED_UNIQUE", path + ".seeds.rows");
                    }
                }
                rows.sort((left, right) -> compareKeys(table, left, right));
                seeds.put(tableName, List.copyOf(rows));
            }
            Module module = new Module(id, sorted(dependencies), List.copyOf(tables), Collections.unmodifiableMap(seeds));
            if (modules.putIfAbsent(id, module) != null) throw error("DUPLICATE_MODULE", path + ".id");
        }
        validateGraph(modules);
        return new StorageSchemaCatalog(modules);
    }

    /** The caller resolves domain requirements; this method only closes application structure dependencies. */
    public StoragePlan plan(Set<String> roots) {
        if (roots == null || roots.isEmpty()) throw error("EMPTY_ROOTS", "roots");
        Map<String, List<String>> reasons = new TreeMap<>();
        for (String root : new TreeSet<>(roots)) collect(root, List.of(root), reasons);
        List<Module> ordered = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (done.size() < reasons.size()) {
            List<String> layer = reasons.keySet().stream().filter(id -> !done.contains(id))
                    .filter(id -> done.containsAll(modules.get(id).dependsOn())).toList();
            if (layer.isEmpty()) throw error("DEPENDENCY_CYCLE", "modules");
            for (String id : layer) ordered.add(modules.get(id));
            done.addAll(layer);
        }
        return new StoragePlan(ordered, reasons);
    }

    private void collect(String id, List<String> path, Map<String, List<String>> reasons) {
        Module module = modules.get(id);
        if (module == null) throw error("UNKNOWN_MODULE", "roots." + id);
        if (reasons.putIfAbsent(id, List.copyOf(path)) != null) return;
        for (String dependency : module.dependsOn()) {
            List<String> next = new ArrayList<>(path);
            next.add(dependency);
            collect(dependency, next, reasons);
        }
    }

    private static Table readTable(Object raw, String owner, Set<String> globalNames, String path) {
        Map<String, Object> table = object(raw, path);
        fields(table, Set.of("name", "columns", "primaryKey", "uniqueKeys", "foreignKeys", "checks", "indexes"), Set.of(), path);
        String name = identifier(table.get("name"), path + ".name");
        if (!name.startsWith(owner.replace('-', '_') + "_")) throw error("TABLE_OWNER", path + ".name");
        uniqueName(name, globalNames, path);
        List<Object> rawColumns = array(table.get("columns"), path + ".columns");
        bounded(rawColumns.size(), 1, 256, path + ".columns");
        List<Column> columns = new ArrayList<>();
        Map<String, Column> columnMap = new LinkedHashMap<>();
        for (Object rawColumn : rawColumns) {
            Column column = readColumn(rawColumn, path + ".columns[" + columns.size() + "]");
            if (columnMap.putIfAbsent(column.name(), column) != null) throw error("DUPLICATE_COLUMN", path + ".columns");
            columns.add(column);
        }
        List<String> pk = names(table.get("primaryKey"), true, path + ".primaryKey", true);
        requireColumns(pk, columnMap, path + ".primaryKey");
        for (String key : pk) if (columnMap.get(key).nullable()) throw error("NULLABLE_PRIMARY_KEY", path + ".primaryKey");
        List<Key> uniqueKeys = new ArrayList<>();
        for (Object rawKey : array(table.get("uniqueKeys"), path + ".uniqueKeys")) {
            Map<String, Object> key = object(rawKey, path + ".uniqueKeys");
            fields(key, Set.of("name", "columns"), Set.of(), path + ".uniqueKeys");
            String keyName = named(key, globalNames, path + ".uniqueKeys");
            List<String> members = names(key.get("columns"), true, path + ".uniqueKeys.columns", true);
            requireColumns(members, columnMap, path + ".uniqueKeys");
            uniqueKeys.add(new Key(keyName, members));
        }
        List<ForeignKey> fks = new ArrayList<>();
        for (Object rawFk : array(table.get("foreignKeys"), path + ".foreignKeys")) {
            Map<String, Object> fk = object(rawFk, path + ".foreignKeys");
            fields(fk, Set.of("name", "columns", "references", "onDelete", "onUpdate", "deferred"), Set.of(), path + ".foreignKeys");
            String fkName = named(fk, globalNames, path + ".foreignKeys");
            List<String> members = names(fk.get("columns"), true, path + ".foreignKeys.columns", true);
            requireColumns(members, columnMap, path + ".foreignKeys");
            if (members.size() > 1 && members.stream().anyMatch(member -> columnMap.get(member).nullable()))
                throw error("UNSUPPORTED_NULLABLE_COMPOSITE_FK", path + ".foreignKeys");
            Map<String, Object> ref = object(fk.get("references"), path + ".foreignKeys.references");
            fields(ref, Set.of("table", "columns"), Set.of(), path + ".foreignKeys.references");
            if (!"NO_ACTION".equals(string(fk.get("onDelete"), path)) || !"NO_ACTION".equals(string(fk.get("onUpdate"), path)))
                throw error("FK_ACTION", path + ".foreignKeys");
            List<String> targetColumns = names(ref.get("columns"), true, path + ".foreignKeys.references.columns", true);
            if (members.size() != targetColumns.size()) throw error("FK_ARITY", path + ".foreignKeys");
            fks.add(new ForeignKey(fkName, members, identifier(ref.get("table"), path), targetColumns, bool(fk.get("deferred"), path)));
        }
        List<Check> checks = new ArrayList<>();
        for (Object rawCheck : array(table.get("checks"), path + ".checks")) {
            Map<String, Object> check = object(rawCheck, path + ".checks");
            fields(check, Set.of("name", "expression"), Set.of(), path + ".checks");
            String checkName = named(check, globalNames, path + ".checks");
            Map<String, Object> expression = expression(check.get("expression"), columnMap, path + ".checks.expression", 1, new int[1]);
            checks.add(new Check(checkName, expression));
        }
        bounded(uniqueKeys.size() + fks.size() + checks.size(), 0, 128, path + ".constraints");
        List<Index> indexes = new ArrayList<>();
        for (Object rawIndex : array(table.get("indexes"), path + ".indexes")) {
            Map<String, Object> index = object(rawIndex, path + ".indexes");
            fields(index, Set.of("name", "columns", "unique"), Set.of(), path + ".indexes");
            String indexName = named(index, globalNames, path + ".indexes");
            List<String> members = names(index.get("columns"), true, path + ".indexes.columns", true);
            requireColumns(members, columnMap, path + ".indexes");
            indexes.add(new Index(indexName, members, bool(index.get("unique"), path)));
        }
        bounded(indexes.size(), 0, 128, path + ".indexes");
        for (ForeignKey fk : fks) {
            boolean indexed = prefix(pk, fk.columns()) || uniqueKeys.stream().anyMatch(key -> prefix(key.columns(), fk.columns()))
                    || indexes.stream().anyMatch(index -> prefix(index.columns(), fk.columns()));
            if (!indexed) throw error("FK_CHILD_INDEX", path + ".foreignKeys." + fk.name());
        }
        uniqueKeys.sort(Comparator.comparing(Key::name));
        fks.sort(Comparator.comparing(ForeignKey::name));
        checks.sort(Comparator.comparing(Check::name));
        indexes.sort(Comparator.comparing(Index::name));
        return new Table(name, owner, List.copyOf(columns), Collections.unmodifiableMap(columnMap), pk,
                List.copyOf(uniqueKeys), List.copyOf(fks), List.copyOf(checks), List.copyOf(indexes));
    }

    private static Column readColumn(Object raw, String path) {
        Map<String, Object> column = object(raw, path);
        String name = identifier(column.get("name"), path + ".name");
        String type = string(column.get("type"), path + ".type");
        Set<String> required = new HashSet<>(Set.of("name", "type", "nullable"));
        Set<String> optional = new HashSet<>(Set.of("default"));
        switch (type) {
            case "int64" -> optional.addAll(Set.of("minimum", "maximum"));
            case "text" -> required.addAll(Set.of("minCodePoints", "maxCodePoints", "ascii"));
            case "bytes" -> required.add("maxBytes");
            case "boolean", "uuid", "digest256" -> { }
            default -> throw error("UNSUPPORTED_TYPE", path + ".type");
        }
        fields(column, required, optional, path);
        long min = column.containsKey("minimum") ? decimal(column.get("minimum"), path + ".minimum") : Long.MIN_VALUE;
        long max = column.containsKey("maximum") ? decimal(column.get("maximum"), path + ".maximum") : Long.MAX_VALUE;
        if (min > max) throw error("RANGE", path);
        int minLength = type.equals("text") ? integer(column.get("minCodePoints"), 0, MAX_BYTES, path) : 0;
        int maxLength = type.equals("text") ? integer(column.get("maxCodePoints"), minLength, MAX_BYTES, path)
                : type.equals("bytes") ? integer(column.get("maxBytes"), 0, MAX_BYTES, path) : 0;
        Column result = new Column(name, type, bool(column.get("nullable"), path), min, max, minLength, maxLength,
                type.equals("text") && bool(column.get("ascii"), path), column.containsKey("default"), column.get("default"));
        if (result.hasDefault()) result.validate(result.defaultValue(), path + ".default");
        return result;
    }

    private static Map<String, Object> expression(Object raw, Map<String, Column> columns, String path, int depth, int[] nodes) {
        if (depth > 16 || ++nodes[0] > 1024) throw error("PREDICATE_BUDGET", path);
        Map<String, Object> expr = object(raw, path);
        String op = string(expr.get("op"), path + ".op");
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("op", op);
        switch (op) {
            case "and", "or" -> {
                fields(expr, Set.of("op", "args"), Set.of(), path);
                List<Object> args = array(expr.get("args"), path + ".args");
                if (args.isEmpty()) throw error("PREDICATE", path);
                List<Object> children = new ArrayList<>();
                for (Object arg : args) children.add(expression(arg, columns, path + ".args", depth + 1, nodes));
                normalized.put("args", List.copyOf(children));
            }
            case "not" -> {
                fields(expr, Set.of("op", "arg"), Set.of(), path);
                normalized.put("arg", expression(expr.get("arg"), columns, path + ".arg", depth + 1, nodes));
            }
            case "isNull", "notNull" -> {
                fields(expr, Set.of("op", "column"), Set.of(), path);
                String column = identifier(expr.get("column"), path);
                requireColumns(List.of(column), columns, path);
                normalized.put("column", column);
            }
            case "in" -> {
                fields(expr, Set.of("op", "column", "values"), Set.of(), path);
                String column = identifier(expr.get("column"), path);
                requireColumns(List.of(column), columns, path);
                List<Object> values = array(expr.get("values"), path + ".values");
                if (values.isEmpty() || values.size() > 1024) throw error("PREDICATE", path);
                Set<String> seen = new HashSet<>();
                for (Object value : values) {
                    if (value == null) throw error("PREDICATE_NULL", path);
                    columns.get(column).validate(value, path);
                    if (!seen.add(canonical(value))) throw error("DUPLICATE_VALUE", path);
                }
                normalized.put("column", column);
                normalized.put("values", List.copyOf(values));
            }
            case "eq", "ne", "lt", "le", "gt", "ge" -> {
                fields(expr, Set.of("op", "left", "right"), Set.of(), path);
                Map<String, Object> left = operand(expr.get("left"), columns, path + ".left");
                Map<String, Object> right = operand(expr.get("right"), columns, path + ".right");
                String type = operandType(left, columns);
                if (!type.equals(operandType(right, columns))) throw error("PREDICATE_TYPE", path);
                if (!Set.of("eq", "ne").contains(op) && !Set.of("int64", "text").contains(type)) throw error("PREDICATE_TYPE", path);
                normalized.put("left", left);
                normalized.put("right", right);
            }
            default -> throw error("PREDICATE_OPERATOR", path);
        }
        return immutableMap(normalized);
    }

    private static Map<String, Object> operand(Object raw, Map<String, Column> columns, String path) {
        Map<String, Object> value = object(raw, path);
        if (value.containsKey("column")) {
            fields(value, Set.of("column"), Set.of(), path);
            requireColumns(List.of(identifier(value.get("column"), path)), columns, path);
        } else {
            fields(value, Set.of("literal", "type"), Set.of(), path);
            String type = string(value.get("type"), path);
            if (!Set.of("int64", "boolean", "uuid", "digest256", "text", "bytes").contains(type)) throw error("PREDICATE_TYPE", path);
            if (value.get("literal") == null) throw error("PREDICATE_NULL", path);
            new Column("literal", type, false, Long.MIN_VALUE, Long.MAX_VALUE, 0, MAX_BYTES, false, false, null)
                    .validate(value.get("literal"), path);
        }
        return immutableMap(value);
    }

    static String operandType(Map<String, Object> operand, Map<String, Column> columns) {
        return operand.containsKey("column") ? columns.get((String) operand.get("column")).type() : (String) operand.get("type");
    }

    private static Boolean evaluate(Map<String, Object> expression, Map<String, Column> columns, Map<String, Object> row) {
        String op = (String) expression.get("op");
        return switch (op) {
            case "isNull" -> row.get(expression.get("column")) == null;
            case "notNull" -> row.get(expression.get("column")) != null;
            case "not" -> {
                Boolean value = evaluate(object(expression.get("arg"), "predicate"), columns, row);
                if (value == null) yield null;
                yield !value;
            }
            case "and", "or" -> {
                boolean unknown = false;
                boolean decisive = false;
                for (Object child : array(expression.get("args"), "predicate")) {
                    Boolean value = evaluate(object(child, "predicate"), columns, row);
                    if (value == null) unknown = true;
                    else if (value == op.equals("or")) decisive = true;
                }
                if (decisive) yield op.equals("or");
                if (unknown) yield null;
                yield op.equals("and");
            }
            case "in" -> {
                Object value = row.get(expression.get("column"));
                yield value == null ? null : array(expression.get("values"), "predicate").contains(value);
            }
            default -> {
                Map<String, Object> leftOperand = object(expression.get("left"), "predicate");
                Map<String, Object> rightOperand = object(expression.get("right"), "predicate");
                Object left = leftOperand.containsKey("column") ? row.get(leftOperand.get("column")) : leftOperand.get("literal");
                Object right = rightOperand.containsKey("column") ? row.get(rightOperand.get("column")) : rightOperand.get("literal");
                if (left == null || right == null) yield null;
                String type = operandType(leftOperand, columns);
                int comparison = switch (type) {
                    case "int64" -> Long.compare(decimal(left, "predicate"), decimal(right, "predicate"));
                    case "boolean" -> Boolean.compare((Boolean) left, (Boolean) right);
                    case "uuid", "digest256", "bytes" -> java.util.Arrays.compareUnsigned(SqliteSchemaCompiler.binary(type, (String) left), SqliteSchemaCompiler.binary(type, (String) right));
                    default -> java.util.Arrays.compareUnsigned(((String) left).getBytes(StandardCharsets.UTF_8), ((String) right).getBytes(StandardCharsets.UTF_8));
                };
                yield switch (op) { case "eq" -> comparison == 0; case "ne" -> comparison != 0; case "lt" -> comparison < 0; case "le" -> comparison <= 0; case "gt" -> comparison > 0; case "ge" -> comparison >= 0; default -> throw error("PREDICATE_OPERATOR", "predicate"); };
            }
        };
    }

    private static void validateGraph(Map<String, Module> modules) {
        Map<String, Table> tables = new HashMap<>();
        for (Module module : modules.values()) {
            for (String dependency : module.dependsOn()) {
                if (!modules.containsKey(dependency)) throw error("UNKNOWN_DEPENDENCY", "modules." + module.id());
                if (dependency.equals(module.id())) throw error("DEPENDENCY_CYCLE", "modules." + module.id());
            }
            for (Table table : module.tables()) tables.put(table.name(), table);
        }
        Set<String> visited = new HashSet<>();
        for (String module : modules.keySet()) visit(module, modules, visited, new HashSet<>());
        for (Table table : tables.values()) {
            for (ForeignKey fk : table.foreignKeys()) {
                Table target = tables.get(fk.target());
                if (target == null) throw error("FK_TARGET", table.name() + "." + fk.name());
                requireColumns(fk.targetColumns(), target.columnMap(), table.name() + "." + fk.name());
                boolean parentKey = target.primaryKey().equals(fk.targetColumns())
                        || target.uniqueKeys().stream().anyMatch(key -> key.columns().equals(fk.targetColumns()));
                if (!parentKey) throw error("FK_PARENT_KEY", table.name() + "." + fk.name());
                for (int i = 0; i < fk.columns().size(); i++) {
                    if (!table.columnMap().get(fk.columns().get(i)).type().equals(target.columnMap().get(fk.targetColumns().get(i)).type()))
                        throw error("FK_TYPE", table.name() + "." + fk.name());
                }
                if (!target.owner().equals(table.owner()) && !modules.get(table.owner()).dependsOn().contains(target.owner()))
                    throw error("FK_DEPENDENCY", table.name() + "." + fk.name());
            }
        }
        // An immediate table cycle has no valid parent-before-child seed order in this subset.
        for (Table table : tables.values()) visitTable(table.name(), tables, new HashSet<>(), new HashSet<>());
        for (Module module : modules.values()) StoragePlan.orderTables(module.tables());
    }

    private static void visit(String id, Map<String, Module> modules, Set<String> done, Set<String> active) {
        if (done.contains(id)) return;
        if (!active.add(id)) throw error("DEPENDENCY_CYCLE", "modules." + id);
        for (String dependency : modules.get(id).dependsOn()) visit(dependency, modules, done, active);
        active.remove(id);
        done.add(id);
    }

    private static void visitTable(String name, Map<String, Table> tables, Set<String> done, Set<String> active) {
        if (done.contains(name)) return;
        if (!active.add(name)) throw error("IMMEDIATE_FK_CYCLE", name);
        for (ForeignKey fk : tables.get(name).foreignKeys()) if (!fk.deferred()) visitTable(fk.target(), tables, done, active);
        active.remove(name);
        done.add(name);
    }

    static Object readJson(JsonReader reader, String path, int depth) throws IOException {
        if (depth > 64) throw error("JSON_BUDGET", path);
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    unicode(name, path);
                    if (map.containsKey(name)) throw error("DUPLICATE_JSON_KEY", path + "." + name);
                    map.put(name, readJson(reader, path + "." + name, depth + 1));
                }
                reader.endObject();
                yield map;
            }
            case BEGIN_ARRAY -> {
                List<Object> list = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) list.add(readJson(reader, path + "[" + list.size() + "]", depth + 1));
                reader.endArray();
                yield list;
            }
            case STRING -> { String value = reader.nextString(); unicode(value, path); yield value; }
            case NUMBER -> new JsonNumber(reader.nextString());
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> { reader.nextNull(); yield null; }
            default -> throw error("JSON", path);
        };
    }

    static void unicode(String value, String path) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isHighSurrogate(character)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw error("UNICODE", path);
            } else if (Character.isLowSurrogate(character)) throw error("UNICODE", path);
        }
    }

    static String string(Object raw, String path) {
        if (!(raw instanceof String value)) throw error("STRING", path);
        return value;
    }

    static boolean bool(Object raw, String path) {
        if (!(raw instanceof Boolean value)) throw error("BOOLEAN", path);
        return value;
    }

    static int integer(Object raw, int minimum, int maximum, String path) {
        if (!(raw instanceof JsonNumber number) || !number.text().matches("0|[1-9][0-9]*")) throw error("INTEGER", path);
        try {
            int value = Integer.parseInt(number.text());
            bounded(value, minimum, maximum, path);
            return value;
        } catch (NumberFormatException failure) { throw error("INTEGER", path); }
    }

    static long decimal(Object raw, String path) {
        String value = string(raw, path);
        if (!value.matches("0|-?[1-9][0-9]*")) throw error("INT64", path);
        try { return Long.parseLong(value); }
        catch (NumberFormatException failure) { throw error("INT64", path); }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object raw, String path) {
        if (!(raw instanceof Map<?, ?>)) throw error("OBJECT", path);
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object raw, String path) {
        if (!(raw instanceof List<?>)) throw error("ARRAY", path);
        return (List<Object>) raw;
    }

    static void fields(Map<String, Object> value, Set<String> required, Set<String> optional, String path) {
        if (!value.keySet().containsAll(required)) throw error("MISSING_FIELD", path);
        for (String field : value.keySet()) if (!required.contains(field) && !optional.contains(field)) throw error("UNKNOWN_FIELD", path + "." + field);
    }

    static String identifier(Object raw, String path) {
        String value = string(raw, path);
        if (!value.matches("[a-z][a-z0-9_]{0,62}") || value.startsWith("sqlite_")) throw error("IDENTIFIER", path);
        return value;
    }

    private static List<String> names(Object raw, boolean identifiers, String path, boolean nonempty) {
        List<Object> values = array(raw, path);
        if (nonempty && values.isEmpty()) throw error("EMPTY_KEY", path);
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object value : values) {
            String name = identifiers ? identifier(value, path) : string(value, path);
            if (!seen.add(name)) throw error("DUPLICATE_NAME", path);
            result.add(name);
        }
        return List.copyOf(result);
    }

    static void requireColumns(List<String> names, Map<String, Column> columns, String path) {
        for (String name : names) if (!columns.containsKey(name)) throw error("UNKNOWN_COLUMN", path + "." + name);
    }

    private static String named(Map<String, Object> object, Set<String> names, String path) {
        String name = identifier(object.get("name"), path + ".name");
        uniqueName(name, names, path);
        return name;
    }

    private static void uniqueName(String name, Set<String> names, String path) {
        if (!names.add(name)) throw error("DUPLICATE_NAME", path + "." + name);
    }

    static boolean prefix(List<String> haystack, List<String> needle) {
        return haystack.size() >= needle.size() && haystack.subList(0, needle.size()).equals(needle);
    }

    static <T extends Comparable<? super T>> List<T> sorted(List<T> values) { return values.stream().sorted().toList(); }
    static <T> Map<String, T> immutableMap(Map<String, T> map) { return Collections.unmodifiableMap(new LinkedHashMap<>(map)); }
    static void bounded(int value, int minimum, int maximum, String path) {
        if (value < minimum || value > maximum) throw error("BUDGET", path);
    }
    static StorageSchemaException error(String code, String path) { return new StorageSchemaException(code, path); }

    static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) return value.toString();
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(StorageSchemaCatalog::canonical).toList()) + "]";
        if (value instanceof Map<?, ?> map) {
            List<String> keys = map.keySet().stream().map(String.class::cast).sorted().toList();
            return "{" + String.join(",", keys.stream().map(key -> canonical(key) + ":" + canonical(map.get(key))).toList()) + "}";
        }
        throw new IllegalArgumentException("Noncanonical schema value");
    }

    private static int compareKeys(Table table, Map<String, Object> left, Map<String, Object> right) {
        for (String key : table.primaryKey()) {
            Column column = table.columnMap().get(key);
            Object a = left.get(key), b = right.get(key);
            int result = switch (column.type()) {
                case "int64" -> Long.compare(decimal(a, key), decimal(b, key));
                case "boolean" -> Boolean.compare((Boolean) a, (Boolean) b);
                case "bytes" -> java.util.Arrays.compareUnsigned(Base64.getDecoder().decode((String) a), Base64.getDecoder().decode((String) b));
                default -> java.util.Arrays.compareUnsigned(((String) a).getBytes(StandardCharsets.UTF_8), ((String) b).getBytes(StandardCharsets.UTF_8));
            };
            if (result != 0) return result;
        }
        return 0;
    }

    record JsonNumber(String text) { }
    record Module(String id, List<String> dependsOn, List<Table> tables, Map<String, List<Map<String, Object>>> seeds) { }
    record Table(String name, String owner, List<Column> columns, Map<String, Column> columnMap, List<String> primaryKey,
                 List<Key> uniqueKeys, List<ForeignKey> foreignKeys, List<Check> checks, List<Index> indexes) { }
    record Key(String name, List<String> columns) { }
    record ForeignKey(String name, List<String> columns, String target, List<String> targetColumns, boolean deferred) { }
    record Check(String name, Map<String, Object> expression) { }
    record Index(String name, List<String> columns, boolean unique) { }
    record Column(String name, String type, boolean nullable, long minimum, long maximum, int minLength,
                  int maxLength, boolean ascii, boolean hasDefault, Object defaultValue) {
        void validate(Object value, String path) {
            if (value == null) {
                if (!nullable) throw error("NULL_VALUE", path);
                return;
            }
            switch (type) {
                case "int64" -> {
                    long number = decimal(value, path);
                    if (number < minimum || number > maximum) throw error("RANGE", path);
                }
                case "boolean" -> bool(value, path);
                case "uuid" -> {
                    if (!string(value, path).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw error("UUID", path);
                }
                case "digest256" -> {
                    if (!string(value, path).matches("[0-9a-f]{64}")) throw error("DIGEST", path);
                }
                case "text" -> {
                    String text = string(value, path);
                    unicode(text, path);
                    int length = text.codePointCount(0, text.length());
                    if (length < minLength || length > maxLength || !Normalizer.isNormalized(text, Normalizer.Form.NFC)) throw error("TEXT", path);
                    if (text.codePoints().anyMatch(cp -> cp < 32 || (cp >= 127 && cp <= 159) || (ascii && cp > 127))) throw error("TEXT", path);
                }
                case "bytes" -> {
                    String text = string(value, path);
                    try {
                        byte[] decoded = Base64.getDecoder().decode(text);
                        if (decoded.length > maxLength || !Base64.getEncoder().encodeToString(decoded).equals(text)) throw error("BYTES", path);
                    } catch (IllegalArgumentException failure) { throw error("BYTES", path); }
                }
                default -> throw error("UNSUPPORTED_TYPE", path);
            }
        }
    }
}
