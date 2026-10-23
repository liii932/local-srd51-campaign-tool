package com.dndtool.persistence;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/** Actual row storage/readback JDBC simulation; never claims MySQL engine evidence. */
public final class Schema2RuntimeJdbcFixture {
    public enum LostCommit { NONE, COMMITTED, ROLLED_BACK }
    public final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
    public final List<String> sql = new ArrayList<>(), events = new ArrayList<>();
    public int acquisitions, closes, aborts;
    public int writes, commits, rollbacks, failWrite = -1, failQuery = -1, queries, affected = -1;
    public boolean autoCommit, readOnly, unavailable, rollbackFails, abortFails, closeFails;
    public boolean legacyActive, partitionRemainder;
    public Error rollbackError, closeError;
    public LostCommit lostCommit = LostCommit.NONE;
    public Runnable beforeAcquire = () -> { };
    public Consumer<String> observer = ignored -> { };
    public Consumer<String> beforeQuery = ignored -> { };
    public Consumer<Schema2RuntimeJdbcFixture> afterWrite = ignored -> { };
    public int isolation = Connection.TRANSACTION_REPEATABLE_READ;
    public byte[] schema = ascii("dnd_tool_se");
    public Consumer<Schema2RuntimeJdbcFixture> beforeRead = ignored -> { };
    private Map<String, List<Map<String, Object>>> committed = new LinkedHashMap<>();
    public final Connection connection = proxy(Connection.class, (method, args) -> switch (method) {
        case "getAutoCommit" -> autoCommit;
        case "setAutoCommit" -> { autoCommit = (boolean) args[0]; yield null; }
        case "isReadOnly" -> readOnly;
        case "setReadOnly" -> { readOnly = (boolean) args[0]; yield null; }
        case "getTransactionIsolation" -> isolation;
        case "setTransactionIsolation" -> { isolation = (int) args[0]; yield null; }
        case "prepareStatement" -> statement((String) args[0]);
        case "commit" -> {
            commits++; event("commit");
            if (lostCommit == LostCommit.ROLLED_BACK) restoreCommitted(); else committed = copy(tables);
            if (lostCommit != LostCommit.NONE) throw new SQLException("Injected lost commit response");
            yield null;
        }
        case "rollback" -> {
            rollbacks++; event("rollback"); if (rollbackError != null) throw rollbackError;
            if (rollbackFails) throw new SQLException("Injected rollback failure");
            restoreCommitted(); yield null;
        }
        case "abort" -> {
            aborts++; event("abort"); if (abortFails) throw new SQLException("Injected abort failure");
            restoreCommitted(); yield null;
        }
        case "close" -> {
            closes++; event("close"); if (closeError != null) throw closeError;
            if (closeFails) throw new SQLException("Injected close failure"); yield null;
        }
        default -> throw new AssertionError("Unexpected connection call " + method);
    });
    /** Pool connections start idle; direct repository tests use the explicit transaction above. */
    public DataSource dataSource() {
        if (!tables.containsKey("s2_runtime_control")) {
            rows("s2_runtime_control").add(row("control_id", 1, "protocol_version", 1, "row_version", 0L, "snapshot_count", 0));
            committed = copy(tables);
        }
        return proxy(DataSource.class, (method, args) -> {
            if (!method.equals("getConnection")) throw new AssertionError("Unexpected data source call " + method);
            acquisitions++; event("acquire"); beforeAcquire.run();
            if (unavailable) throw new SQLException("Injected disconnected runtime");
            autoCommit = true; readOnly = false; isolation = Connection.TRANSACTION_REPEATABLE_READ;
            return connection;
        });
    }
    public void saveCommitted() { committed = copy(tables); }
    public Map<String, List<Map<String, Object>>> committedRows() { return copy(committed); }
    public List<Map<String, Object>> rows(String table) { return tables.computeIfAbsent(table, ignored -> new ArrayList<>()); }

    private PreparedStatement statement(String query) {
        sql.add(query); Map<Integer, Object> parameters = new HashMap<>();
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setBytes", "setString", "setInt", "setLong" -> { parameters.put((Integer) args[0], cloneValue(args[1])); yield null; }
            case "setNull" -> { parameters.put((Integer) args[0], null); yield null; }
            case "setQueryTimeout", "setMaxRows", "close" -> null;
            case "executeUpdate" -> update(query, parameters);
            case "executeQuery" -> result(select(query, parameters));
            default -> throw new AssertionError("Unexpected statement call " + method);
        });
    }
    private int update(String query, Map<Integer, Object> parameters) throws SQLException {
        writes++; if (writes == failWrite) { if (affected >= 0) return affected; throw new SQLException("Injected write"); }
        if (query.startsWith("UPDATE s2_runtime_control SET ")) {
            var control = rows("s2_runtime_control").getFirst();
            if (!equalNumber(control.get("row_version"), parameters.get(3)) || !equalNumber(control.get("snapshot_count"), parameters.get(4))) return 0;
            control.put("row_version", parameters.get(1)); control.put("snapshot_count", parameters.get(2)); afterWrite.accept(this); return 1;
        }
        var matcher = Pattern.compile("INSERT INTO ([a-z0-9_]+) \\(([^)]+)\\)").matcher(query);
        if (!matcher.find()) throw new AssertionError("Unexpected update " + query);
        String table = matcher.group(1); String[] columns = matcher.group(2).split(", ");
        Map<String, Object> row = new LinkedHashMap<>(); for (int i = 0; i < columns.length; i++) row.put(columns[i], cloneValue(parameters.get(i + 1)));
        if (table.equals("s2_runtime_identity")) for (var previous : rows(table)) {
            if (equal(previous.get("run_id"), row.get("run_id")) || equal(previous.get("snapshot_id"), row.get("snapshot_id"))) throw new SQLException("Identity collision");
        }
        if (table.equals("s2_runtime_operation")) row.put("created_at", LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(rows(table).size()));
        rows(table).add(row); afterWrite.accept(this); return 1;
    }
    private List<Map<String, Object>> select(String query, Map<Integer, Object> parameters) throws SQLException {
        queries++; beforeQuery.accept(query); if (queries == failQuery) throw new SQLException("Injected query");
        if (query.equals(BusinessWriteTransactions.LOCK_SQL)) return List.of(row("guard_id", 1, "protocol_version", 1));
        if (query.equals(BusinessWriteTransactions.STATE_SQL)) return List.of(row("legacy_active", legacyActive,
                "schema2_remainder", !rows("s2_runtime_identity").isEmpty(), "partition_remainder", partitionRemainder));
        if (query.contains("DATABASE()")) return List.of(Map.of("schema_name", schema));
        if (query.contains(" FROM schema_meta ")) {
            List<Map<String, Object>> result = new ArrayList<>();
            try {
                for (var item : SchemaMigrations.loadExpectations()) result.add(row("schema_version", item.version(), "script_name", item.scriptName(), "script_sha256", item.scriptSha256()));
            } catch (SchemaMigrations.PackagedSchemaException failure) { throw new AssertionError(failure); }
            return result;
        }
        if (query.contains(" FROM s2_runtime_control ")) return rows("s2_runtime_control");
        if (query.contains(" FROM s2_runtime_operation ")) {
            List<Map<String, Object>> result = new ArrayList<>(rows("s2_runtime_operation"));
            result.sort(Comparator.comparingLong(value -> ((Number) value.get("operation_sequence")).longValue())); return result;
        }
        if (query.startsWith("SELECT run_id, snapshot_id FROM ")) {
            String table = query.substring("SELECT run_id, snapshot_id FROM ".length(), query.indexOf(" ORDER BY"));
            return rows(table);
        }
        if (query.contains("FROM s2_runtime_snapshot s JOIN")) {
            var callback = beforeRead; beforeRead = ignored -> { }; callback.accept(this);
            List<Map<String, Object>> result = new ArrayList<>();
            for (var snapshot : rows("s2_runtime_snapshot")) {
                if (!equal(snapshot.get("run_id"), parameters.get(1)) || !equal(snapshot.get("snapshot_id"), parameters.get(2))) continue;
                for (var identity : rows("s2_runtime_identity")) if (equal(identity.get("run_id"), snapshot.get("run_id")) && equal(identity.get("snapshot_id"), snapshot.get("snapshot_id"))) {
                    Map<String, Object> row = new LinkedHashMap<>(snapshot); row.put("registered_run_id", identity.get("run_id")); row.put("registered_snapshot_id", identity.get("snapshot_id")); result.add(row);
                }
            }
            return result;
        }
        if (query.contains(" FROM rule_schema2_character_source ") || query.contains(" FROM rule_schema2_proficiency_grant ") || query.contains(" FROM rule_schema2_character_change ")) {
            String table = query.substring(query.indexOf(" FROM ") + 6, query.indexOf(" ORDER BY"));
            return rows(table);
        }
        var matcher = Pattern.compile("FROM ([a-z0-9_]+) WHERE snapshot_id = \\? ORDER BY ([a-z_, ]+) LIMIT ([0-9]+)").matcher(query);
        if (!matcher.find()) throw new AssertionError("Unexpected query " + query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : rows(matcher.group(1))) if (equal(row.get("snapshot_id"), parameters.get(1))) result.add(row);
        String[] columns = matcher.group(2).split(", ");
        result.sort((left, right) -> { for (String column : columns) { int compare = Integer.compare(((Number) left.get(column)).intValue(), ((Number) right.get(column)).intValue()); if (compare != 0) return compare; } return 0; });
        return result.subList(0, Math.min(result.size(), Integer.parseInt(matcher.group(3))));
    }
    private static ResultSet result(List<Map<String, Object>> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> ++index[0] < rows.size();
            case "getObject" -> {
                var row = rows.get(index[0]);
                Object column = args[0] instanceof Integer position ? new ArrayList<>(row.keySet()).get(position - 1) : args[0];
                if (!row.containsKey(column)) throw new AssertionError("Missing fixture column " + column);
                yield cloneValue(row.get(column));
            }
            case "getInt" -> ((Number) rows.get(index[0]).get(args[0])).intValue();
            case "getBoolean" -> (boolean) rows.get(index[0]).get(args[0]);
            case "close" -> null;
            default -> throw new AssertionError("Unexpected result call " + method);
        });
    }
    private void restoreCommitted() { tables.clear(); tables.putAll(copy(committed)); }
    private void event(String event) { events.add(event); observer.accept(event); }
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]); return result;
    }
    private static boolean equalNumber(Object left, Object right) { return left instanceof Number a && right instanceof Number b && a.longValue() == b.longValue(); }
    private static Map<String, List<Map<String, Object>>> copy(Map<String, List<Map<String, Object>>> source) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>(); source.forEach((table, rows) -> {
            List<Map<String, Object>> copied = new ArrayList<>(); for (var row : rows) { Map<String, Object> values = new LinkedHashMap<>(); row.forEach((key, value) -> values.put(key, cloneValue(value))); copied.add(values); } result.put(table, copied);
        }); return result;
    }
    private static Object cloneValue(Object value) { return value instanceof byte[] bytes ? bytes.clone() : value; }
    private static boolean equal(Object left, Object right) { return left instanceof byte[] a && right instanceof byte[] b ? Arrays.equals(a, b) : Objects.equals(left, right); }
    private static byte[] ascii(String value) { return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
    @FunctionalInterface private interface Call { Object call(String method, Object[] arguments) throws Throwable; }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) { return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> call.call(method.getName(), args == null ? new Object[0] : args)); }
}
