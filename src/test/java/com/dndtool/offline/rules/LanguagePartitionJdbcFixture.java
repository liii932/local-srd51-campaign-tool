package com.dndtool.offline.rules;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import javax.sql.DataSource;
import com.dndtool.persistence.RuleSchemaMigrations;
import static org.junit.jupiter.api.Assertions.*;

/** In-memory JDBC protocol model, not evidence of MySQL constraints, privileges or durability. */
final class LanguagePartitionJdbcFixture {
    static final String LANGUAGE_COLUMNS = "language_key, display_name, description, category, source_page, sort_order";
    final SourceInstallationTest.Database installed;
    int sourceOpens, sourceQueries, runtimeQueries, commits, rollbacks, languageWrites;
    boolean sourceOffline, reverseRows;
    int failLanguageWrite;
    Consumer<List<Map<String, Object>>> sourceFault = rows -> {};
    Consumer<List<Map<String, Object>>> runtimeFault = rows -> {};
    List<Map<String, Object>> registrations = new ArrayList<>(), heads = new ArrayList<>(), languages = new ArrayList<>();

    LanguagePartitionJdbcFixture(SourceInstallationTest.Database installed) { this.installed = installed; }

    static Connection installationConnection(SourceInstallationTest.Database installed) throws SQLException {
        Connection delegate = installed.open();
        return proxy(Connection.class, (p, method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                String sql = ((String) args[0]).strip().replaceAll("\\s+", " ");
                // The reused transaction fixture stores parameter positions. Pin the column order
                // so a changed INSERT/SELECT cannot silently give those positions new meanings.
                if (sql.startsWith("INSERT INTO rule_language")) assertEquals(
                        "INSERT INTO rule_language (release_id, " + LANGUAGE_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)", sql);
                if (sql.startsWith("SELECT ") && sql.contains("FROM rule_language")) assertEquals(
                        "SELECT release_id, " + LANGUAGE_COLUMNS + " FROM rule_language ORDER BY release_id, language_key", sql);
            }
            try { return method.invoke(delegate, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    DataSource source() {
        return proxy(DataSource.class, (p, m, a) -> {
            if (!m.getName().equals("getConnection")) throw new AssertionError(m.getName());
            sourceOpens++;
            if (sourceOffline) throw new SQLException("Source unavailable");
            boolean[] auto = {true}, readOnly = {false}; int[] isolation = {Connection.TRANSACTION_READ_COMMITTED};
            return proxy(Connection.class, (q, n, b) -> switch (n.getName()) {
                case "getAutoCommit" -> auto[0];
                case "isReadOnly" -> readOnly[0];
                case "getTransactionIsolation" -> isolation[0];
                case "setAutoCommit" -> { auto[0] = (boolean) b[0]; yield null; }
                case "setReadOnly" -> { readOnly[0] = (boolean) b[0]; yield null; }
                case "setTransactionIsolation" -> { isolation[0] = (int) b[0]; yield null; }
                case "prepareStatement" -> {
                    assertFalse(auto[0]); assertTrue(readOnly[0]);
                    assertEquals(Connection.TRANSACTION_REPEATABLE_READ, isolation[0]);
                    yield statement((String) b[0], true);
                }
                case "commit", "rollback", "close", "abort" -> null;
                default -> throw new AssertionError("Unexpected source connection call " + n.getName());
            });
        });
    }

    Connection runtime() {
        // Captures the committed baseline of this caller-owned transaction, including previous runs.
        var oldRegistrations = copy(registrations); var oldHeads = copy(heads); var oldLanguages = copy(languages);
        return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
            case "getAutoCommit", "isReadOnly" -> false;
            case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
            case "prepareStatement" -> statement((String) a[0], false);
            case "commit" -> { commits++; yield null; }
            case "rollback" -> {
                rollbacks++; registrations = copy(oldRegistrations); heads = copy(oldHeads); languages = copy(oldLanguages);
                yield null;
            }
            case "close" -> null;
            default -> throw new AssertionError("Unexpected runtime connection call " + m.getName());
        });
    }

    private PreparedStatement statement(String original, boolean source) {
        String sql = original.strip().replaceAll("\\s+", " ");
        Map<Integer, Object> params = new HashMap<>(); int[] max = {0};
        return proxy(PreparedStatement.class, (p, m, a) -> switch (m.getName()) {
            case "setBytes", "setString", "setInt", "setLong" -> {
                Object value = a[1] instanceof byte[] bytes ? bytes.clone() : a[1];
                assertFalse(params.containsKey((int) a[0])); params.put((int) a[0], value); yield null;
            }
            case "setQueryTimeout" -> { assertEquals(5, a[0]); yield null; }
            case "setMaxRows" -> { max[0] = (int) a[0]; assertTrue(max[0] > 0 && max[0] <= 19); yield null; }
            case "executeQuery" -> {
                assertTrue(max[0] > 0); List<Map<String, Object>> rows;
                if (source) { sourceQueries++; rows = sourceQuery(sql, params); }
                else { runtimeQueries++; rows = runtimeQuery(sql, params); }
                yield result(project(sql, rows).subList(0, Math.min(max[0], rows.size())));
            }
            case "executeUpdate" -> {
                assertFalse(source, "Read-only adapter attempted DML"); insert(sql, params); yield 1;
            }
            case "close" -> null;
            default -> throw new AssertionError("Unexpected statement call " + m.getName());
        });
    }

    private List<Map<String, Object>> sourceQuery(String sql, Map<Integer, Object> p) {
        if (sql.equals("SELECT CAST(DATABASE() AS BINARY) AS schema_name")) return List.of(Map.of("schema_name", b("dnd_tool_rules")));
        if (sql.equals("SELECT schema_version, script_name, script_sha256 FROM rule_schema_meta ORDER BY schema_version ASC")) {
            return RuleSchemaMigrations.expectations().stream().map(e -> row("schema_version, script_name, script_sha256",
                    e.version(), b(e.scriptName()), b(e.scriptSha256()))).toList();
        }
        if (sql.endsWith("FROM rule_release WHERE module_key = ? AND release_version = ?")) {
            assertEquals(2, p.size());
            return installed.roots.stream().filter(r -> equal(r[1], p.get(1)) && equal(r[2], p.get(2)))
                    .map(r -> row("id, module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm, content_sha256, release_status, installation_revision, created_at, released_at", r)).toList();
        }
        if (sql.endsWith("FROM rule_package_installation WHERE release_id = ? AND installation_revision = ?")) {
            assertEquals(2, p.size());
            return installed.facts.stream().filter(r -> equal(r[0], p.get(1)) && equal(r[1], p.get(2)))
                    .map(r -> row("release_id, installation_revision, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, installation_manifest_version, installation_manifest_sha256, package_display_name, verification_scope, observed_content_sha256, installed_at", r)).toList();
        }
        if (sql.endsWith("FROM rule_package_installation_partition WHERE release_id = ? AND installation_revision = ? ORDER BY partition_key")) {
            assertEquals(2, p.size());
            return installed.partitions.stream().filter(r -> equal(r[0], p.get(1)) && equal(r[1], p.get(2)))
                    .map(r -> row("release_id, installation_revision, partition_key", r)).toList();
        }
        if (sql.endsWith("FROM rule_language WHERE release_id = ? ORDER BY language_key")) {
            assertEquals(1, p.size());
            var rows = new ArrayList<>(installed.languages.stream().filter(r -> equal(r[0], p.get(1)))
                    .map(r -> row("release_id, " + LANGUAGE_COLUMNS, r)).toList());
            sourceFault.accept(rows); if (reverseRows) Collections.reverse(rows); return rows;
        }
        throw new AssertionError("Unexpected source SQL " + sql);
    }

    private void insert(String sql, Map<Integer, Object> p) throws SQLException {
        if (sql.equals("INSERT INTO runtime_run_identity (run_id, snapshot_id) VALUES (?, ?)")) {
            assertEquals(2, p.size());
            for (var r : registrations) if (equal(r.get("run_id"), p.get(1)) || equal(r.get("snapshot_id"), p.get(2)))
                throw new SQLException("Duplicate identity");
            registrations.add(row("run_id, snapshot_id", values(p, 2))); return;
        }
        if (sql.equals("INSERT INTO runtime_rule_snapshot (snapshot_id, run_id, module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm, release_status, material_scope, content_sha256) VALUES (?, ?, ?, ?, 2, 2, 'SHA-256', 'DRAFT', 'PARTITION', NULL)")) {
            assertEquals(4, p.size());
            assertTrue(registrations.stream().anyMatch(r -> equal(r.get("snapshot_id"), p.get(1)) && equal(r.get("run_id"), p.get(2))));
            heads.add(row("snapshot_id, run_id, module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm, release_status, material_scope, content_sha256",
                    p.get(1), p.get(2), p.get(3), p.get(4), 2, 2, b("SHA-256"), b("DRAFT"), b("PARTITION"), null)); return;
        }
        if (sql.equals("INSERT INTO runtime_rule_language (snapshot_id, " + LANGUAGE_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            assertEquals(7, p.size()); languageWrites++;
            if (languageWrites == failLanguageWrite) throw new SQLException("Ninth language write failed");
            assertTrue(heads.stream().anyMatch(r -> equal(r.get("snapshot_id"), p.get(1))));
            languages.add(row("snapshot_id, " + LANGUAGE_COLUMNS, values(p, 7))); return;
        }
        throw new AssertionError("Unexpected runtime write " + sql);
    }

    private List<Map<String, Object>> runtimeQuery(String sql, Map<Integer, Object> p) {
        if (sql.equals("SELECT CAST(DATABASE() AS BINARY) AS schema_name")) return List.of(Map.of("schema_name", b("dnd_tool_se")));
        if (sql.endsWith("FROM runtime_rule_snapshot s JOIN runtime_run_identity i ON i.run_id = s.run_id AND i.snapshot_id = s.snapshot_id WHERE s.run_id = ? AND s.snapshot_id = ?")) {
            assertEquals(2, p.size()); var rows = new ArrayList<Map<String, Object>>();
            for (var h : heads) for (var i : registrations) {
                if (equal(h.get("run_id"), p.get(1)) && equal(h.get("snapshot_id"), p.get(2))
                        && equal(h.get("run_id"), i.get("run_id")) && equal(h.get("snapshot_id"), i.get("snapshot_id"))) {
                    var r = new HashMap<>(h); r.put("registered_run_id", i.get("run_id"));
                    r.put("registered_snapshot_id", i.get("snapshot_id")); rows.add(r);
                }
            }
            return rows;
        }
        if (sql.endsWith("FROM runtime_rule_language WHERE snapshot_id = ? ORDER BY language_key")) {
            assertEquals(1, p.size());
            var rows = copy(languages.stream().filter(r -> equal(r.get("snapshot_id"), p.get(1))).toList());
            runtimeFault.accept(rows); if (reverseRows) Collections.reverse(rows); return rows;
        }
        throw new AssertionError("Unexpected runtime query " + sql);
    }

    /** Only SELECTed labels exist; omitting a field from SQL cannot be hidden by the fixture. */
    private static List<Map<String, Object>> project(String sql, List<Map<String, Object>> rows) {
        if (sql.equals("SELECT CAST(DATABASE() AS BINARY) AS schema_name")) return rows;
        String select = sql.substring(7, sql.indexOf(" FROM "));
        for (String column : select.split(", ")) if (column.contains(" AS ")) {
            assertTrue(column.equals("i.run_id AS registered_run_id")
                    || column.equals("i.snapshot_id AS registered_snapshot_id"), "Unsupported alias expression " + column);
        }
        var labels = Arrays.stream(select.split(", ")).map(c -> c.contains(" AS ")
                ? c.substring(c.indexOf(" AS ") + 4) : c.substring(c.indexOf('.') + 1)).toList();
        return rows.stream().map(r -> {
            Map<String, Object> projected = new HashMap<>();
            for (String label : labels) { assertTrue(r.containsKey(label), "Unknown SELECT column " + label); projected.put(label, r.get(label)); }
            return projected;
        }).toList();
    }

    private static ResultSet result(List<Map<String, Object>> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getObject", "getBytes" -> {
                assertTrue(rows.get(index[0]).containsKey(a[0]), "Column not selected: " + a[0]);
                Object value = rows.get(index[0]).get(a[0]); yield value instanceof byte[] bytes ? bytes.clone() : value;
            }
            case "close" -> null;
            default -> throw new AssertionError("Unexpected result call " + m.getName());
        });
    }

    static Map<String, Object> row(String columns, Object... values) {
        var names = columns.split(", "); assertEquals(names.length, values.length);
        var row = new HashMap<String, Object>();
        for (int i = 0; i < names.length; i++) row.put(names[i], values[i] instanceof byte[] b ? b.clone() : values[i]);
        return row;
    }
    static List<Map<String, Object>> copy(List<Map<String, Object>> input) {
        var copy = new ArrayList<Map<String, Object>>(); input.forEach(r -> copy.add(new HashMap<>(r))); return copy;
    }
    static byte[] b(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static Object[] values(Map<Integer, Object> p, int length) {
        Object[] values = new Object[length]; for (int i = 0; i < length; i++) values[i] = p.get(i + 1); return values;
    }
    private static boolean equal(Object a, Object b) {
        return a instanceof byte[] x && b instanceof byte[] y ? Arrays.equals(x, y) : Objects.equals(a, b);
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
