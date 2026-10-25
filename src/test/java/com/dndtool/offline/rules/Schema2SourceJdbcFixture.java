package com.dndtool.offline.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.dndtool.module.composition.ProductionRuleArtifact;

/** SQL protocol fixture only; provides no MySQL constraint, privilege, or durability evidence. */
final class Schema2SourceJdbcFixture {
    Object[] legacyControl = {1, 1, 1L, 0L};
    Map<String, Object> control = row("control_id, protocol_version, metadata_row_count, installation_count, acceptance_count, raw_zip_byte_count, row_version",
            1, 1, 1L, 0L, 0L, 0L, 0L);
    List<Map<String, Object>> artifacts = new ArrayList<>(), acceptances = new ArrayList<>();
    final Map<String, List<Map<String, Object>>> characterTables = new LinkedHashMap<>();
    int writes, failWrite, wrongAffectedWrite, blobReads, statementsClosed, resultsClosed, commits, rollbacks, closes;
    boolean commitResponseLost, rollbackFails;
    Runnable afterWrite = () -> {};
    final List<String> queries = new ArrayList<>();

    static ProductionRuleArtifact artifact(String module, String version, String notice) {
        String header = "{\"author_schema_version\":2,\"module_key\":\"" + module + "\",\"release_version\":\"" + version
                + "\",\"package_display_name\":\"Empty add-on\",\"role\":\"extension\","
                + "\"dependencies\":[{\"module_key\":\"base\",\"supported_versions\":[\"1\"]}],"
                + "\"compatible_combinations\":[{\"base\":\"1\"}],\"load_order_constraints\":[],\"files\":[]}";
        var files = new TreeMap<String, byte[]>(); files.put("author-package.json", header.getBytes(StandardCharsets.UTF_8));
        if (notice != null) files.put("NOTICE", notice.getBytes(StandardCharsets.UTF_8));
        return ProductionRuleArtifact.read(zip(files));
    }

    static byte[] zip(Map<String, byte[]> files) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                for (var file : new TreeMap<>(files).entrySet()) {
                    var crc = new CRC32(); crc.update(file.getValue()); var entry = new ZipEntry(file.getKey());
                    entry.setMethod(ZipEntry.STORED); entry.setSize(file.getValue().length); entry.setCompressedSize(file.getValue().length);
                    // 1980-01-01 is the JDK's pre-DOS sentinel and adds an unsupported timestamp extra.
                    entry.setCrc(crc.getValue()); entry.setTimeLocal(LocalDateTime.of(2000, 1, 1, 0, 0));
                    zip.putNextEntry(entry); zip.write(file.getValue()); zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }

    Connection open() {
        Map<String, List<Map<String, Object>>> priorCharacters = new LinkedHashMap<>();
        characterTables.forEach((table, rows) -> priorCharacters.put(table, copy(rows)));
        var priorControl = copy(control); var priorArtifacts = copy(artifacts); var priorAcceptances = copy(acceptances);
        boolean[] auto = {true}; int[] isolation = {Connection.TRANSACTION_READ_COMMITTED};
        return proxy(Connection.class, (p, method, arguments) -> switch (method.getName()) {
            case "getAutoCommit" -> auto[0];
            case "setAutoCommit" -> { auto[0] = (boolean) arguments[0]; yield null; }
            case "getTransactionIsolation" -> isolation[0];
            case "setTransactionIsolation" -> { isolation[0] = (int) arguments[0]; yield null; }
            case "isReadOnly" -> false;
            case "setReadOnly" -> null;
            case "prepareStatement" -> statement((String) arguments[0]);
            case "commit" -> { commits++; if (commitResponseLost) throw new SQLException("commit response lost"); yield null; }
            case "rollback" -> {
                rollbacks++; if (rollbackFails) throw new SQLException("rollback failed");
                control = copy(priorControl); artifacts = copy(priorArtifacts); acceptances = copy(priorAcceptances);
                characterTables.clear(); priorCharacters.forEach((table, rows) -> characterTables.put(table, copy(rows))); yield null;
            }
            case "close" -> { closes++; yield null; }
            default -> throw new AssertionError("Unexpected connection call " + method);
        });
    }

    private PreparedStatement statement(String sql) {
        Map<Integer, Object> parameters = new HashMap<>();
        int[] limit = {0};
        return proxy(PreparedStatement.class, (p, method, args) -> switch (method.getName()) {
            case "setBytes", "setLong", "setInt", "setString" -> {
                assertNull(parameters.put((int) args[0], cloneValue(args[1]))); yield null;
            }
            case "setNull" -> { parameters.put((int) args[0], null); yield null; }
            case "setQueryTimeout" -> { assertEquals(5, args[0]); yield null; }
            case "setMaxRows" -> { limit[0] = (int) args[0]; assertTrue(limit[0] >= 1 && limit[0] <= 65537); yield null; }
            case "executeQuery" -> {
                assertTrue(limit[0] > 0); queries.add(sql);
                List<Object[]> rows = select(sql, parameters);
                yield result(rows.subList(0, Math.min(rows.size(), limit[0])), sql);
            }
            case "executeUpdate" -> {
                writes++;
                if (writes == failWrite) throw new SQLException("synthetic write failure");
                mutate(sql, parameters); afterWrite.run();
                yield writes == wrongAffectedWrite ? 0 : 1;
            }
            case "close" -> { statementsClosed++; yield null; }
            default -> throw new AssertionError("Unexpected statement call " + method);
        });
    }

    private List<Object[]> select(String sql, Map<Integer, Object> parameters) {
        if (sql.startsWith("SELECT control_id, protocol_version, metadata_row_count, row_version FROM rule_installation_control"))
            return Collections.singletonList(legacyControl.clone());
        List<Map<String, Object>> source;
        if (sql.contains(" FROM rule_schema2_control")) source = List.of(control);
        else if (sql.contains(" FROM rule_schema2_artifact")) {
            source = artifacts;
            if (sql.contains(" WHERE module_key = ?")) {
                assertEquals(2, parameters.size());
                source = source.stream().filter(r -> equal(r.get("module_key"), parameters.get(1))
                        && equal(r.get("release_version"), parameters.get(2))).toList();
            }
        } else if (sql.contains(" FROM rule_schema2_acceptance")) source = acceptances;
        else if (sql.contains(" FROM rule_schema2_")) {
            String table = sql.substring(sql.indexOf(" FROM ") + 6, sql.indexOf(" ORDER BY"));
            source = characterTables.computeIfAbsent(table, ignored -> new ArrayList<>());
        }
        else throw new AssertionError("Unexpected query " + sql);
        String columns = sql.substring(7, sql.indexOf(" FROM "));
        List<Object[]> result = new ArrayList<>();
        for (var row : source) {
            var names = columns.split(", "); Object[] values = new Object[names.length];
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals("OCTET_LENGTH(original_zip)")) values[i] = (long) ((byte[]) row.get("original_zip")).length;
                else { assertTrue(row.containsKey(names[i]), "Unselected fixture column " + names[i]); values[i] = cloneValue(row.get(names[i])); }
            }
            result.add(values);
        }
        return result;
    }

    private void mutate(String sql, Map<Integer, Object> parameters) {
        if (sql.startsWith("INSERT INTO ")) {
            String table = sql.substring(12, sql.indexOf(" ("));
            int close = sql.indexOf(") VALUES (");
            String[] columns = sql.substring(sql.indexOf('(') + 1, close).split(", ");
            String[] values = sql.substring(close + 10, sql.length() - 1).split(", ");
            assertEquals(columns.length, values.length);
            Map<String, Object> row = new HashMap<>(); int parameter = 1;
            for (int i = 0; i < columns.length; i++)
                row.put(columns[i], values[i].equals("?") ? cloneValue(parameters.get(parameter++)) : Integer.valueOf(values[i]));
            assertEquals(parameters.size(), parameter - 1);
            if (table.equals("rule_schema2_artifact")) { row.put("installed_at", LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(artifacts.size())); artifacts.add(row); }
            else if (table.equals("rule_schema2_acceptance")) { row.put("accepted_at", LocalDateTime.of(2026, 2, 1, 0, 0).plusSeconds(acceptances.size())); acceptances.add(row); }
            else if (Set.of("rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change", "rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change", "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set", "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant", "rule_schema2_ability_requirement", "rule_schema2_rest", "rule_schema2_check", "rule_schema2_equipment_item", "rule_schema2_execution_change", "rule_schema2_health", "rule_schema2_vital_effect").contains(table))
                characterTables.computeIfAbsent(table, ignored -> new ArrayList<>()).add(row);
            else throw new AssertionError("Unexpected insert " + sql);
            return;
        }
        if (sql.startsWith("UPDATE rule_schema2_control SET ")) {
            String assignments = sql.substring(sql.indexOf(" SET ") + 5, sql.indexOf(" WHERE "));
            int parameter = 1;
            Map<String, Object> updated = new HashMap<>(control);
            for (String assignment : assignments.split(", ")) updated.put(assignment.substring(0, assignment.indexOf(" = ")), cloneValue(parameters.get(parameter++)));
            var conditions = sql.substring(sql.indexOf(" WHERE ") + 7).split(" AND ");
            for (String condition : conditions) {
                String[] pair = condition.split(" = ");
                Object expected = pair[1].equals("?") ? parameters.get(parameter++) : Integer.valueOf(pair[1]);
                assertEquals(((Number) expected).longValue(), ((Number) control.get(pair[0])).longValue(), condition);
            }
            assertEquals(parameters.size(), parameter - 1); control = updated; return;
        }
        throw new AssertionError("Unexpected mutation " + sql);
    }

    private ResultSet result(List<Object[]> rows, String sql) {
        int[] cursor = {-1};
        int count = rows.isEmpty() ? sql.substring(7, sql.indexOf(" FROM ")).split(", ").length : rows.getFirst().length;
        return proxy(ResultSet.class, (p, method, args) -> switch (method.getName()) {
            case "next" -> ++cursor[0] < rows.size();
            case "getObject" -> cloneValue(rows.get(cursor[0])[(int) args[0] - 1]);
            case "getBinaryStream" -> { blobReads++; yield new ByteArrayInputStream((byte[]) rows.get(cursor[0])[(int) args[0] - 1]); }
            case "getMetaData" -> proxy(ResultSetMetaData.class, (q, m, a) -> {
                if (!m.getName().equals("getColumnCount")) throw new AssertionError(m);
                return count;
            });
            case "close" -> { resultsClosed++; yield null; }
            default -> throw new AssertionError("Unexpected result call " + method);
        });
    }
    static byte[] b(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    static Map<String, Object> row(String columns, Object... values) {
        String[] names = columns.split(", "); assertEquals(names.length, values.length);
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < names.length; i++) result.put(names[i], cloneValue(values[i]));
        return result;
    }
    private static boolean equal(Object a, Object b) {
        return a instanceof byte[] x && b instanceof byte[] y ? Arrays.equals(x, y) : Objects.equals(a, b);
    }
    private static Object cloneValue(Object value) { return value instanceof byte[] bytes ? bytes.clone() : value; }
    private static Map<String, Object> copy(Map<String, Object> original) {
        Map<String, Object> copy = new HashMap<>(); original.forEach((key, value) -> copy.put(key, cloneValue(value))); return copy;
    }
    private static List<Map<String, Object>> copy(List<Map<String, Object>> source) { return new ArrayList<>(source.stream().map(Schema2SourceJdbcFixture::copy).toList()); }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}

