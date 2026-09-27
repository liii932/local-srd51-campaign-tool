package com.dndtool.offline.rules;

import com.dndtool.module.LanguagePartition;
import com.dndtool.persistence.JdbcRuntimeLanguageSnapshotRepository;
import com.dndtool.persistence.JdbcRuntimeLanguageSnapshotRepository.Identity;
import com.dndtool.persistence.JdbcSourceLanguageRepository;
import com.google.gson.JsonParser;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.dndtool.offline.rules.LanguageJdbcAcceptance.Role.*;
import static com.dndtool.offline.rules.LanguagePartitionOracle.*;

/** Explicit real Connector/J acceptance against one independently audited disposable instance. */
class LanguagePartitionJdbcIT {
    @TempDir Path temp;
    private LanguageJdbcAcceptance acceptance;
    private final JdbcRuntimeLanguageSnapshotRepository runtime = new JdbcRuntimeLanguageSnapshotRepository();
    private final AtomicInteger sourceConnections = new AtomicInteger();
    private boolean rejectSourceConnections;
    private static final String CONTENT = "language_key, display_name, description, category, source_page, sort_order";

    @Test void realInstallationSourceReadAndCallerOwnedSnapshotsPreserveTheLanguagePartition() throws Exception {
        assumeTrue("true".equals(System.getProperty(LanguageJdbcAcceptance.ENABLE)), "Explicit disposable acceptance required");
        acceptance = LanguageJdbcAcceptance.load(true, System.getenv());
        // A previous attempt is not permission to replay installation history or reuse an instance.
        assertEquals(0, count(SOURCE, "SELECT COUNT(*) FROM rule_release"));
        assertEquals(0, count(SOURCE, "SELECT COUNT(*) FROM rule_package_installation"));
        for (String table : List.of("runtime_run_identity", "runtime_rule_snapshot", "runtime_rule_language"))
            assertEquals(0, count(VERIFIER, "SELECT COUNT(*) FROM " + table));

        RuleArtifact baselineArtifact = artifact("");
        assertBoundary(baseline(), baselineArtifact.author().partition(), vector());
        install(baselineArtifact, 1, null);
        LanguagePartition baseline = readSource(); assertSource(baseline(), baseline, vector(), 1);
        Identity original = append(baseline(), baseline, vector());

        // Same real package/new operation advances permanent history without changing rule bytes.
        install(baselineArtifact, 2, null);
        assertSource(baseline(), readSource(), vector(), 2);
        Identity repeated = append(baseline(), readSource(), vector());
        assertNotEquals(original, repeated);

        int revision = 2; Identity changedSnapshot = null; LanguagePartition current = baseline;
        for (String field : List.of("description", "source_page", "sort_order", "display_name", "unicode")) {
            RuleArtifact artifact = artifact(field); List<Row> expected = changed(field);
            byte[] bytes = canonical(artifact.author().partition()); assertFalse(Arrays.equals(vector(), bytes));
            assertBoundary(expected, artifact.author().partition(), bytes);
            install(artifact, ++revision, null); current = readSource(); assertSource(expected, current, bytes, revision);
            changedSnapshot = append(expected, current, bytes);
            assertRuntime(original, baseline(), vector());
        }
        assertNotNull(changedSnapshot);
        try (Connection c = runtimeTransaction()) {
            Identity mismatch = new Identity(original.runId(), changedSnapshot.snapshotId());
            assertThrows(SQLException.class, () -> runtime.read(c, mismatch)); c.rollback();
        }

        // Actual installer transaction: the ninth statement reaches MySQL with page=2 and CHECK rejects it.
        var before = sourceState(); Fault sourceFault = new Fault("rule_language", "range");
        install(baselineArtifact, revision + 1, sourceFault);
        assertEquals(9, sourceFault.writes); assertNotNull(sourceFault.databaseFailure);
        assertEquals(3819, sourceFault.databaseFailure.getErrorCode(), "MySQL CHECK failure, not synthetic JDBC success");
        assertEquals(0, sourceFault.commits); assertEquals(1, sourceFault.rollbacks);
        assertTrue(before.equals(sourceState()), "Failed installation must retain all six source tables exactly");
        assertSource(changed("unicode"), readSource(), canonical(current), revision);

        for (String failure : List.of("range", "readback", "non-nfc", "later-caller"))
            runtimeFailure(failure, baseline, original);
        databaseConstraintsRejectRows(baseline, original);
        minimumPrivilegesRejectForbiddenSql();

        // A committed incomplete mirror fails independently of source availability; no repair/fallback.
        Identity missing = append(baseline(), baseline, vector());
        try (Connection c = runtimeTransaction(); var s = c.prepareStatement(
                "DELETE FROM runtime_rule_language WHERE snapshot_id = ? AND language_key = ?")) {
            s.setBytes(1, missing.snapshotBytes()); s.setBytes(2, ascii("language.giant")); assertEquals(1, s.executeUpdate()); c.commit();
        }
        rejectSourceConnections = true;
        assertThrows(SQLException.class, this::readSource); int sourceAttempts = sourceConnections.get();
        assertRuntime(original, baseline(), vector()); assertRuntime(repeated, baseline(), vector());
        assertRuntime(changedSnapshot, changed("unicode"), canonical(current));
        try (Connection c = runtimeTransaction()) { assertThrows(SQLException.class, () -> runtime.read(c, missing)); c.rollback(); }
        assertEquals(sourceAttempts, sourceConnections.get(), "Runtime partition reads cannot acquire a source connection");
    }

    private void runtimeFailure(String kind, LanguagePartition partition, Identity old) throws Exception {
        Identity failed = Identity.random(); Fault fault = new Fault("runtime_rule_language", kind);
        try (Connection real = runtimeTransaction()) {
            Connection wrapped = fault.wrap(real);
            SQLException failure;
            if (kind.equals("later-caller")) {
                assertBoundary(baseline(), runtime.append(wrapped, failed, partition).partition(), vector());
                failure = assertThrows(SQLException.class, () -> { throw new SQLException("Injected subsequent caller failure", "FI007"); });
            } else failure = assertThrows(SQLException.class, () -> runtime.append(wrapped, failed, partition));
            assertEquals(0, fault.commits); assertEquals(0, fault.rollbacks);
            if (kind.equals("range")) { assertEquals(9, fault.writes); assertEquals(3819, failure.getErrorCode()); }
            else {
                assertEquals(18, fault.writes);
                if (kind.equals("readback")) assertEquals("FI007", failure.getSQLState());
                if (kind.equals("non-nfc")) {
                    assertEquals("Invalid runtime language partition", failure.getMessage());
                    try (var s = real.prepareStatement("SELECT display_name FROM runtime_rule_language WHERE snapshot_id = ? AND language_key = ?")) {
                        s.setBytes(1, failed.snapshotBytes()); s.setBytes(2, ascii("language.giant"));
                        try (var r = s.executeQuery()) { assertTrue(r.next()); assertEquals("e\u0301", r.getString(1)); }
                    }
                }
            }
            wrapped.rollback(); assertEquals(1, fault.rollbacks);
        }
        assertAbsent(failed); assertRuntime(old, baseline(), vector());
    }

    private void databaseConstraintsRejectRows(LanguagePartition partition, Identity old) throws Exception {
        for (String bad : List.of("null", "unknown-key", "nineteenth", "wrong-category", "page-range")) {
            Identity id = Identity.random();
            try (Connection c = runtimeTransaction()) {
                runtime.append(c, id, partition);
                // Release a legal order for the unknown-key case, so only the closed key/category
                // CHECK can reject it. The separate nineteenth case keeps all eighteen original rows.
                if (!bad.equals("nineteenth")) try (var delete = c.prepareStatement(
                        "DELETE FROM runtime_rule_language WHERE snapshot_id = ? AND language_key = ?")) {
                    delete.setBytes(1, id.snapshotBytes()); delete.setBytes(2, ascii("language.giant")); assertEquals(1, delete.executeUpdate());
                }
                try (var insert = c.prepareStatement("INSERT INTO runtime_rule_language (snapshot_id, " + CONTENT + ") VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setBytes(1, id.snapshotBytes());
                    insert.setBytes(2, ascii(bad.equals("unknown-key") || bad.equals("nineteenth") ? "language.unknown" : "language.giant"));
                    insert.setString(3, "Giant"); insert.setString(4, "Giant is an SRD 5.1 language catalog entry.");
                    if (bad.equals("null")) insert.setNull(4, Types.VARCHAR);
                    insert.setBytes(5, ascii(bad.equals("wrong-category") ? "SECRET" : "STANDARD"));
                    insert.setInt(6, bad.equals("page-range") ? 2 : 59);
                    insert.setInt(7, 9);
                    SQLException rejected = assertThrows(SQLException.class, insert::executeUpdate);
                    if (bad.equals("nineteenth")) {
                        // A nineteenth row cannot have a distinct legal order in 1..18. Either
                        // the closed key/category CHECK or the unique order rejects it in MySQL.
                        assertTrue(Set.of(3819, 1062).contains(rejected.getErrorCode()));
                    } else assertEquals(bad.equals("null") ? 1048 : 3819, rejected.getErrorCode());
                }
                c.rollback();
            }
            assertAbsent(id);
        }
        assertRuntime(old, baseline(), vector());
    }

    private void minimumPrivilegesRejectForbiddenSql() throws Exception {
        Map<LanguageJdbcAcceptance.Role, List<String>> forbidden = Map.of(
                SOURCE, List.of("DELETE FROM rule_language WHERE 1=0"),
                RUNTIME, List.of("UPDATE runtime_rule_language SET source_page=source_page WHERE 1=0",
                        "DELETE FROM runtime_run_identity WHERE 1=0", "SELECT * FROM dnd_tool_rules.rule_language LIMIT 0"),
                INSTALLER, List.of("SELECT * FROM dnd_tool_se.runtime_rule_language LIMIT 0",
                        "DELETE FROM rule_schema_meta WHERE 1=0", "CREATE TABLE fi07_forbidden_probe (id INT)"));
        for (var entry : forbidden.entrySet()) for (String sql : entry.getValue()) {
            try (Connection c = acceptance.open(entry.getKey()); var s = c.createStatement()) {
                SQLException denied = assertThrows(SQLException.class, () -> s.execute(sql));
                assertTrue(Set.of(1044, 1142, 1143).contains(denied.getErrorCode()), "Expected database privilege denial");
            }
        }
    }

    private void install(RuleArtifact artifact, int revision, Fault fault) throws Exception {
        try (var tickets = new TicketStore(acceptance.state())) {
            var service = new SourceInstallation(acceptance.evidence, tickets, () -> {
                Connection c = acceptance.open(INSTALLER); return fault == null ? c : fault.wrap(c);
            });
            var ticket = service.prepare(artifact); assertEquals(revision - 1L, ticket.expectedRevision());
            var result = service.install(ticket, artifact);
            assertEquals(fault == null ? SourceInstallation.Status.COMMITTED : SourceInstallation.Status.ROLLED_BACK,
                    result.status(), "Actual SourceInstallation outcome");
            if (fault == null) {
                assertEquals((long) revision, result.acceptedRevision()); assertEquals("PARTITION", result.acceptance().scope());
                assertNull(result.acceptance().observedContentSha256());
            }
            assertNull(tickets.pending());
        }
    }

    private LanguagePartition readSource() throws SQLException {
        DataSource source = proxy(DataSource.class, (p, method, args) -> {
            if (!method.getName().equals("getConnection")) throw new AssertionError(method.getName());
            sourceConnections.incrementAndGet(); if (rejectSourceConnections) throw new SQLException("Injected source acquisition outage", "FI007");
            return acceptance.open(SOURCE);
        });
        return new JdbcSourceLanguageRepository(source).load();
    }

    private Identity append(List<Row> expected, LanguagePartition partition, byte[] bytes) throws Exception {
        Identity id = Identity.random();
        try (Connection c = runtimeTransaction()) {
            assertBoundary(expected, runtime.append(c, id, partition).partition(), bytes);
            assertStored(expected, rows(c, "SELECT snapshot_id, " + CONTENT + " FROM runtime_rule_language WHERE snapshot_id = ? ORDER BY language_key", id.snapshotBytes()), "snapshot_id");
            c.commit();
        }
        assertRuntime(id, expected, bytes); return id;
    }

    private void assertRuntime(Identity id, List<Row> expected, byte[] bytes) throws Exception {
        try (Connection c = runtimeTransaction()) { assertBoundary(expected, runtime.read(c, id).partition(), bytes); c.rollback(); }
        try (Connection c = acceptance.open(VERIFIER)) {
            assertStored(expected, rows(c, "SELECT snapshot_id, " + CONTENT + " FROM runtime_rule_language WHERE snapshot_id = ? ORDER BY language_key", id.snapshotBytes()), "snapshot_id");
            var head = rows(c, "SELECT release_status, material_scope, content_sha256 FROM runtime_rule_snapshot WHERE snapshot_id = ?", id.snapshotBytes());
            assertEquals(1, head.size()); assertArrayEquals(ascii("DRAFT"), (byte[]) head.getFirst().get("release_status"));
            assertArrayEquals(ascii("PARTITION"), (byte[]) head.getFirst().get("material_scope")); assertNull(head.getFirst().get("content_sha256"));
        }
    }

    private void assertSource(List<Row> expected, LanguagePartition partition, byte[] bytes, int revision) throws Exception {
        assertBoundary(expected, partition, bytes);
        try (Connection c = acceptance.open(SOURCE)) {
            assertStored(expected, rows(c, "SELECT release_id, " + CONTENT + " FROM rule_language ORDER BY language_key"), "release_id");
            var head = rows(c, "SELECT installation_revision, release_status, content_sha256 FROM rule_release");
            assertEquals(1, head.size()); assertEquals(revision, ((Number) head.getFirst().get("installation_revision")).intValue());
            assertArrayEquals(ascii("DRAFT"), (byte[]) head.getFirst().get("release_status")); assertNull(head.getFirst().get("content_sha256"));
            var fact = rows(c, "SELECT verification_scope, observed_content_sha256 FROM rule_package_installation WHERE installation_revision = ?", revision);
            assertEquals(1, fact.size()); assertArrayEquals(ascii("PARTITION"), (byte[]) fact.getFirst().get("verification_scope"));
            assertNull(fact.getFirst().get("observed_content_sha256"));
        }
    }

    private void assertAbsent(Identity id) throws SQLException {
        assertEquals(0, count(VERIFIER, "SELECT COUNT(*) FROM runtime_run_identity WHERE run_id = ? OR snapshot_id = ?", id.runBytes(), id.snapshotBytes()));
        assertEquals(0, count(VERIFIER, "SELECT COUNT(*) FROM runtime_rule_snapshot WHERE snapshot_id = ?", id.snapshotBytes()));
        assertEquals(0, count(VERIFIER, "SELECT COUNT(*) FROM runtime_rule_language WHERE snapshot_id = ?", id.snapshotBytes()));
    }
    private Connection runtimeTransaction() throws SQLException {
        Connection c = acceptance.open(RUNTIME); c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED); c.setAutoCommit(false); return c;
    }
    private long count(LanguageJdbcAcceptance.Role role, String sql, Object... parameters) throws SQLException {
        try (Connection c = acceptance.open(role); var s = c.prepareStatement(sql)) {
            bind(s, parameters); try (var r = s.executeQuery()) { assertTrue(r.next()); return r.getLong(1); }
        }
    }
    private List<String> sourceState() throws SQLException {
        var state = new ArrayList<String>();
        try (Connection c = acceptance.open(SOURCE)) {
            for (String table : List.of("rule_schema_meta", "rule_release", "rule_language", "rule_package_installation",
                    "rule_package_installation_partition", "rule_installation_control")) {
                try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM " + table)) {
                    while (r.next()) {
                        var row = new ArrayList<String>(); row.add(table);
                        for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
                            Object v = r.getObject(i); row.add(v instanceof byte[] b ? HexFormat.of().formatHex(b) : String.valueOf(v));
                        }
                        state.add(row.toString());
                    }
                }
            }
        }
        Collections.sort(state); return state;
    }
    private static List<Map<String, Object>> rows(Connection c, String sql, Object... parameters) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            bind(s, parameters); try (var r = s.executeQuery()) {
                var rows = new ArrayList<Map<String, Object>>();
                while (r.next()) {
                    var row = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) row.put(r.getMetaData().getColumnLabel(i), r.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    private static void bind(PreparedStatement s, Object[] values) throws SQLException {
        s.setQueryTimeout(5);
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof byte[] b) s.setBytes(i + 1, b);
            else if (values[i] instanceof Integer n) s.setInt(i + 1, n);
            else throw new AssertionError("Unexpected test SQL binding");
        }
    }

    private RuleArtifact artifact(String change) throws Exception {
        Path root = Files.createDirectory(temp.resolve("package-" + UUID.randomUUID()));
        Path author = Files.createDirectory(root.resolve("author")); Files.createDirectory(author.resolve("character"));
        for (String file : List.of("author-package.json", "character/languages.json", "package-guide.md", "notice.md"))
            Files.copy(Path.of("rule-packages/srd51-complete").resolve(file), author.resolve(file));
        if (!change.isEmpty()) {
            Path file = author.resolve("character/languages.json"); var json = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
            var common = json.get(2).getAsJsonObject();
            switch (change) {
                case "description" -> common.addProperty(change, "Revised Common description.");
                case "source_page" -> common.addProperty(change, 60);
                case "display_name" -> common.addProperty(change, "Common revised");
                case "sort_order" -> { common.addProperty(change, 4); json.get(3).getAsJsonObject().addProperty(change, 3); }
                case "unicode" -> { common.addProperty("display_name", "😀".repeat(120)); common.addProperty("description", "é" + "😀".repeat(999)); }
                default -> throw new AssertionError(change);
            }
            Files.writeString(file, json.toString());
        }
        Path output = root.resolve("artifact"); return RuleArtifact.read(output, RuleArtifact.build(author, output));
    }
    private static void assertBoundary(List<Row> expected, LanguagePartition actual, byte[] bytes) throws Exception {
        assertPartition(expected, actual); assertArrayEquals(bytes, canonical(actual));
    }
    private static byte[] ascii(String value) { return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    /** Only fault injection wraps JDBC. All normal reads/writes and rollback evidence use the real driver. */
    private static final class Fault {
        final String table, kind; int writes, commits, rollbacks; SQLException databaseFailure;
        Fault(String table, String kind) { this.table = table; this.kind = kind; }
        Connection wrap(Connection delegate) {
            return proxy(Connection.class, (p, m, args) -> {
                if (m.getName().equals("commit")) commits++;
                if (m.getName().equals("rollback")) rollbacks++;
                Object result = invoke(m, delegate, args);
                if (m.getName().equals("prepareStatement")) {
                    String sql = ((String) args[0]).strip().replaceAll("\\s+", " ");
                    PreparedStatement statement = (PreparedStatement) result;
                    return proxy(PreparedStatement.class, (q, method, values) -> {
                        if (method.getName().equals("executeUpdate") && sql.startsWith("INSERT INTO " + table + " ")) {
                            writes++;
                            if (writes == 9 && kind.equals("range")) statement.setInt(6, 2);
                            if (writes == 9 && kind.equals("non-nfc")) statement.setString(3, "e\u0301");
                        }
                        try {
                            Object actual = invoke(method, statement, values);
                            if (method.getName().equals("executeQuery") && kind.equals("readback") && sql.contains("FROM " + table + " ")) {
                                ((ResultSet) actual).close(); throw new SQLException("Injected failure after actual readback query", "FI007");
                            }
                            return actual;
                        } catch (SQLException failure) {
                            if (failure.getErrorCode() != 0) databaseFailure = failure;
                            throw failure;
                        }
                    });
                }
                return result;
            });
        }
        private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
            try { return method.invoke(target, arguments); } catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
}
