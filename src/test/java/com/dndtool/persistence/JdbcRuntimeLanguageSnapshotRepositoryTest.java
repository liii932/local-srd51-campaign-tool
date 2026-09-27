package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.module.LanguagePartition;
import com.dndtool.persistence.JdbcRuntimeLanguageSnapshotRepository.Identity;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Strict JDBC contract simulation, not MySQL FK, permission or transaction evidence. */
class JdbcRuntimeLanguageSnapshotRepositoryTest {
    private static final byte[] U = HexFormat.of().parseHex("00112233445546778899aabbccddeeff");
    private static final byte[] V = HexFormat.of().parseHex("ffeeddccbbaa49888766554433221100");
    private static final byte[] W = HexFormat.of().parseHex("123456789abc4defa123456789abcdef");
    private static final Identity ID = new Identity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));
    private static final JdbcRuntimeLanguageSnapshotRepository REPOSITORY = new JdbcRuntimeLanguageSnapshotRepository();
    // Independent complete content fixture: no author/source/runtime mapper generates expectations.
    private static final String[][] ROWS = {
        {"abyssal", "Abyssal", "Abyssal is an SRD 5.1 language catalog entry.", "EXOTIC"},
        {"celestial", "Celestial", "Celestial is an SRD 5.1 language catalog entry.", "EXOTIC"},
        {"common", "Common", "Common is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"deep_speech", "Deep Speech", "Deep Speech is an SRD 5.1 language catalog entry.", "EXOTIC"},
        {"draconic", "Draconic", "Draconic is an SRD 5.1 language catalog entry.", "EXOTIC"},
        {"druidic", "Druidic", "Druidic is an SRD 5.1 language catalog entry.", "SECRET"},
        {"dwarvish", "Dwarvish", "Dwarvish is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"elvish", "Elvish", "Elvish is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"giant", "Giant", "Giant is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"gnomish", "Gnomish", "Gnomish is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"goblin", "Goblin", "Goblin is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"halfling", "Halfling", "Halfling is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"infernal", "Infernal", "Infernal is an SRD 5.1 language catalog entry.", "EXOTIC"},
        {"orc", "Orc", "Orc is an SRD 5.1 language catalog entry.", "STANDARD"},
        {"primordial", "Primordial", "Primordial is an SRD 5.1 language catalog entry.", "EXOTIC"},
        {"sylvan", "Sylvan", "Sylvan is an SRD 5.1 language catalog entry.", "EXOTIC"},
        {"thieves_cant", "Thieves Cant", "Thieves Cant is an SRD 5.1 language catalog entry.", "SECRET"},
        {"undercommon", "Undercommon", "Undercommon is an SRD 5.1 language catalog entry.", "EXOTIC"}
    };

    @Test void independentUuidVectorsAndDefensiveBytes() {
        assertArrayEquals(U, ID.runBytes());
        assertArrayEquals(V, ID.snapshotBytes());
        assertEquals(ID, Identity.fromBytes(U, V));
        byte[] copy = ID.runBytes();
        copy[0] = 42;
        assertArrayEquals(U, ID.runBytes());
        for (int length : new int[] {0, 15, 17}) {
            assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(Arrays.copyOf(U, length), V));
            assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(U, Arrays.copyOf(V, length)));
        }
        for (int[] change : new int[][] {{6, 0x36}, {8, 0x08}, {8, 0xc8}, {8, 0xe8}}) {
            byte[] bad = U.clone(); bad[change[0]] = (byte) change[1];
            assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(bad, V));
            assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(V, bad));
        }
        assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(U, U));
        assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(new byte[16], V));
        assertThrows(IllegalArgumentException.class, () -> new Identity(null, ID.snapshotId()));
        var fresh = Identity.random();
        assertEquals(4, fresh.runId().version()); assertEquals(2, fresh.snapshotId().variant());
        assertNotEquals(fresh.runId(), fresh.snapshotId());
    }

    @Test void appendsAllFieldsAndActuallyReadsSameTransactionBeforeCallerCommit() throws Exception {
        Fixture f = new Fixture();
        var actual = REPOSITORY.append(f.connection, ID, partition());
        assertEquals(ID, actual.identity()); assertEquals(partition(), actual.partition());
        assertEquals(List.of("schema", "identity", "head", "language"), f.writeOrder.stream().distinct().toList());
        assertEquals(20, f.pending); assertEquals(0, f.committed);
        assertEquals(0, f.commits); assertEquals(0, f.rollbacks);
        assertEquals(3, f.queries); f.assertClosed();
        f.connection.commit();
        assertEquals(20, f.committed); assertEquals(0, f.pending);
        assertEquals(partition(), REPOSITORY.read(f.connection, ID).partition());
    }

    @Test void everyWriteFailureAndAffectedCountRequireActualCallerRollback() throws Exception {
        for (int point = 1; point <= 20; point++) {
            for (int affected : new int[] {-1, 0, 2}) {
                Fixture f = new Fixture(); f.failWrite = point; f.affected = affected;
                assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, ID, partition()));
                assertEquals(0, f.commits); assertEquals(0, f.rollbacks);
                f.connection.rollback();
                assertEquals(0, f.pending); assertEquals(0, f.committed); f.assertClosed();
            }
        }
    }

    @Test void laterCallerBusinessFailureRollsBackRegistrationAndWholePartition() throws Exception {
        Fixture f = new Fixture();
        try {
            REPOSITORY.append(f.connection, ID, partition());
            throw new SQLException("later business failure");
        } catch (SQLException expected) { f.connection.rollback(); }
        assertEquals(0, f.pending); assertEquals(0, f.committed);
        assertEquals(1, f.rollbacks); assertEquals(0, f.commits);
    }

    @Test void collisionAfterCommitOrSnapshotCleanupIsNeverReplay() throws Exception {
        for (boolean cleaned : List.of(false, true)) {
            Fixture f = new Fixture(); f.registered = true; f.cleaned = cleaned;
            assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, ID, partition()));
            assertEquals(1, f.writes); assertEquals(0, f.pending);
            f.connection.rollback(); assertTrue(f.registered);
        }
        for (boolean runCollision : List.of(false, true)) {
            Fixture f = new Fixture(); f.registered = true;
            f.previousRun = runCollision ? U : W;
            f.previousSnapshot = runCollision ? W : V;
            assertDoesNotThrow(() -> Identity.fromBytes(f.previousRun, f.previousSnapshot));
            assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, ID, partition()));
            assertEquals(1, f.writes); assertEquals(0, f.pending);
            f.connection.rollback(); assertTrue(f.registered);
        }
    }

    @Test void sameContentHasIndependentRunAndSnapshotIdentityWithoutDeduplication() throws Exception {
        Identity second = new Identity(ID.snapshotId(), ID.runId());
        Fixture a = new Fixture(ID); Fixture b = new Fixture(second);
        var firstResult = REPOSITORY.append(a.connection, ID, partition());
        var secondResult = REPOSITORY.append(b.connection, second, partition());
        assertEquals(firstResult.partition(), secondResult.partition());
        assertNotEquals(firstResult.identity(), secondResult.identity());
        assertEquals(20, a.pending); assertEquals(20, b.pending);
        a.connection.commit(); b.connection.commit();
        assertEquals(20, a.committed); assertEquals(20, b.committed);
    }

    @Test void transactionAndExactDefaultSchemaAreRequiredBeforeWrites() throws Exception {
        for (Consumer<Fixture> change : List.<Consumer<Fixture>>of(
                f -> f.autoCommit = true, f -> f.readOnly = true,
                f -> f.isolation = Connection.TRANSACTION_NONE,
                f -> f.isolation = Connection.TRANSACTION_READ_UNCOMMITTED,
                f -> f.schema = bytes("dnd_tool_rules"), f -> f.schema = bytes("dnd_tool_se "),
                f -> f.schema = "dnd_tool_se", f -> f.schema = null)) {
            Fixture f = new Fixture(); change.accept(f);
            assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, ID, partition()));
            assertEquals(0, f.writes); f.assertClosed();
        }
        for (int isolation : new int[] {Connection.TRANSACTION_READ_COMMITTED,
                Connection.TRANSACTION_REPEATABLE_READ, Connection.TRANSACTION_SERIALIZABLE}) {
            Fixture f = new Fixture(); f.isolation = isolation;
            REPOSITORY.append(f.connection, ID, partition()); f.connection.rollback();
        }
    }

    @Test void allHeadFieldsAndRegisteredPairAreCheckedNotEchoed() throws Exception {
        Map<String, Object> changes = new HashMap<>();
        changes.put("run_id", V); changes.put("snapshot_id", U);
        changes.put("registered_run_id", V); changes.put("registered_snapshot_id", U);
        changes.put("module_key", bytes("dnd5e2014_srd51_se_v1"));
        changes.put("release_version", bytes("01")); changes.put("canonical_format_version", 1);
        changes.put("archive_format_version", 1); changes.put("hash_algorithm", bytes("SHA-512"));
        changes.put("release_status", bytes("RELEASED")); changes.put("material_scope", bytes("COMPLETE"));
        changes.put("content_sha256", bytes("a".repeat(64)));
        for (var entry : changes.entrySet()) {
            failedReadback(f -> f.head.put(entry.getKey(), entry.getValue()));
        }
        // Even a structurally legal COMPLETE/DRAFT or COMPLETE/RELEASED head is unsupported here.
        for (String status : List.of("DRAFT", "RELEASED")) {
            failedReadback(f -> { f.head.put("material_scope", bytes("COMPLETE"));
                f.head.put("content_sha256", bytes("a".repeat(64))); f.head.put("release_status", bytes(status)); });
        }
        failedReadback(f -> f.headCount = 0); failedReadback(f -> f.headCount = 2);
    }

    @Test void legalContentChangesStillFailReadbackEquality() throws Exception {
        for (Consumer<Fixture> change : List.<Consumer<Fixture>>of(
                f -> f.languages.get(2).put("display_name", "Common changed"),
                f -> f.languages.get(2).put("description", "Changed valid description"),
                f -> f.languages.get(2).put("source_page", 60),
                f -> { f.languages.get(2).put("sort_order", 4); f.languages.get(3).put("sort_order", 3); })) {
            failedReadback(change);
        }
    }

    @Test void wrongMembershipCategoryOrderOrAssociationAndNineteenthRowFailClosed() throws Exception {
        for (Consumer<Fixture> change : List.<Consumer<Fixture>>of(
                f -> f.languages.removeLast(), f -> f.languages.add(Map.of()),
                f -> f.languages.get(2).put("snapshot_id", U),
                f -> f.languages.get(2).put("language_key", bytes("language.unknown")),
                f -> f.languages.get(2).put("language_key", bytes("language.abyssal")),
                f -> f.languages.get(2).put("category", bytes("EXOTIC")),
                f -> f.languages.get(2).put("sort_order", 1),
                f -> f.languages.get(2).put("source_page", 75))) failedReadback(change);
    }

    @Test void jdbcTypesCannotCoerceNumbersBinaryOrText() throws Exception {
        for (Object bad : Arrays.asList(null, "59", 59.0, new BigDecimal("59"), Long.MAX_VALUE, true)) {
            failedReadback(f -> f.languages.get(2).put("source_page", bad));
        }
        for (Object bad : Arrays.asList(null, "2", 2.0, new BigDecimal("2"), 4294967298L)) {
            failedReadback(f -> f.head.put("canonical_format_version", bad));
        }
        for (Object bad : Arrays.asList(null, "language.common", bytes("language.common "), new byte[] {(byte) 0xff})) {
            failedReadback(f -> f.languages.get(2).put("language_key", bad));
        }
        failedReadback(f -> f.languages.get(2).put("display_name", bytes("Common")));
        for (byte[] bad : List.of(Arrays.copyOf(V, 15), Arrays.copyOf(V, 17), new byte[16])) {
            failedReadback(f -> f.head.put("snapshot_id", bad));
        }
    }

    @Test void unicodeReadbackRejectsRepairAndAcceptsSupplementaryCodePointLimits() throws Exception {
        for (String bad : List.of("e\u0301", "\ud800", " ", "\u00a0", "\u0000", "A\nB", "\u007f", "\u0085",
                "😀".repeat(121))) failedReadback(f -> f.languages.get(2).put("display_name", bad));
        failedReadback(f -> f.languages.get(2).put("description", "😀".repeat(1001)));
        Fixture f = new Fixture();
        f.languages.get(2).put("display_name", "😀".repeat(120));
        f.languages.get(2).put("description", "😀".repeat(1000));
        assertEquals(120, REPOSITORY.read(f.connection, ID).partition().languages().get(2)
                .displayName().codePointCount(0, 240));
    }

    @Test void queryFailureAndReadOnlyApiRequireBindingAndLeaveRollbackToCaller() throws Exception {
        for (int point = 1; point <= 3; point++) {
            Fixture f = new Fixture(); f.failQuery = point;
            assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, ID, partition()));
            assertEquals(0, f.rollbacks); f.connection.rollback();
            assertEquals(0, f.pending); assertEquals(0, f.committed); f.assertClosed();
        }
        Fixture f = new Fixture();
        assertThrows(SQLException.class, () -> REPOSITORY.read(f.connection, null));
        assertEquals(0, f.queries);
        assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, null, partition()));
        assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, ID, null));
        assertEquals(0, f.writes);
    }

    private static void failedReadback(Consumer<Fixture> change) throws Exception {
        Fixture f = new Fixture(); change.accept(f);
        assertThrows(SQLException.class, () -> REPOSITORY.append(f.connection, ID, partition()));
        assertEquals(0, f.commits); assertEquals(0, f.rollbacks);
        f.connection.rollback(); assertEquals(0, f.pending); assertEquals(0, f.committed); f.assertClosed();
    }

    private static LanguagePartition partition() {
        List<LanguagePartition.Language> rows = new ArrayList<>();
        for (int i = 0; i < ROWS.length; i++) {
            var row = ROWS[i]; rows.add(new LanguagePartition.Language("language." + row[0], row[1], row[2],
                    LanguagePartition.Category.valueOf(row[3]), 59, i + 1));
        }
        return new LanguagePartition(rows);
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static String sql(String value) { return value.strip().replaceAll("\\s+", " "); }

    private static final class Fixture {
        boolean autoCommit, readOnly, registered, cleaned;
        int isolation = Connection.TRANSACTION_READ_COMMITTED;
        int failWrite, affected = -1, writes, pending, committed, commits, rollbacks, queries, failQuery;
        int prepared, closedStatements, openedResults, closedResults, headCount = 1;
        Object schema = bytes("dnd_tool_se");
        final byte[] run;
        final byte[] snapshot;
        byte[] previousRun = U;
        byte[] previousSnapshot = V;
        final Map<String, Object> head = new HashMap<>();
        final List<Map<String, Object>> languages = new ArrayList<>();
        final List<String> writeOrder = new ArrayList<>();
        final Connection connection = proxy(Connection.class, (method, args) -> switch (method) {
            case "getAutoCommit" -> autoCommit;
            case "isReadOnly" -> readOnly;
            case "getTransactionIsolation" -> isolation;
            case "prepareStatement" -> { assertEquals(1, args.length); yield statement(sql((String) args[0])); }
            case "commit" -> { commits++; committed += pending; registered |= pending > 0; pending = 0; yield null; }
            case "rollback" -> { rollbacks++; pending = 0; yield null; }
            default -> throw new AssertionError("Forbidden connection call: " + method);
        });

        Fixture() { this(ID); }

        Fixture(Identity identity) {
            run = identity.runBytes(); snapshot = identity.snapshotBytes();
            head.put("snapshot_id", snapshot); head.put("run_id", run);
            head.put("registered_run_id", run); head.put("registered_snapshot_id", snapshot);
            head.put("module_key", bytes("dnd5e2014_srd51_se")); head.put("release_version", bytes("1"));
            head.put("canonical_format_version", 2); head.put("archive_format_version", 2);
            head.put("hash_algorithm", bytes("SHA-256")); head.put("release_status", bytes("DRAFT"));
            head.put("material_scope", bytes("PARTITION")); head.put("content_sha256", null);
            for (int i = 0; i < ROWS.length; i++) {
                var r = ROWS[i]; Map<String, Object> row = new HashMap<>();
                row.put("snapshot_id", snapshot); row.put("language_key", bytes("language." + r[0]));
                row.put("display_name", r[1]); row.put("description", r[2]); row.put("category", bytes(r[3]));
                row.put("source_page", (short) 59); row.put("sort_order", (short) (i + 1)); languages.add(row);
            }
        }

        PreparedStatement statement(String query) {
            String kind = switch (query) {
                case "SELECT CAST(DATABASE() AS BINARY) AS schema_name" -> "schema";
                case "INSERT INTO runtime_run_identity (run_id, snapshot_id) VALUES (?, ?)" -> "identity";
                case "INSERT INTO runtime_rule_snapshot (snapshot_id, run_id, module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm, release_status, material_scope, content_sha256) VALUES (?, ?, ?, ?, 2, 2, 'SHA-256', 'DRAFT', 'PARTITION', NULL)" -> "head";
                case "INSERT INTO runtime_rule_language (snapshot_id, language_key, display_name, description, category, source_page, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)" -> "language";
                case "SELECT s.snapshot_id, s.run_id, s.module_key, s.release_version, s.canonical_format_version, s.archive_format_version, s.hash_algorithm, s.release_status, s.material_scope, s.content_sha256, i.run_id AS registered_run_id, i.snapshot_id AS registered_snapshot_id FROM runtime_rule_snapshot s JOIN runtime_run_identity i ON i.run_id = s.run_id AND i.snapshot_id = s.snapshot_id WHERE s.run_id = ? AND s.snapshot_id = ?" -> "readHead";
                case "SELECT snapshot_id, language_key, display_name, description, category, source_page, sort_order FROM runtime_rule_language WHERE snapshot_id = ? ORDER BY language_key" -> "readLanguage";
                default -> throw new AssertionError("Unexpected SQL: " + query);
            };
            prepared++;
            Map<Integer, Object> parameters = new HashMap<>(); int[] limits = new int[2];
            return proxy(PreparedStatement.class, (method, args) -> switch (method) {
                case "setBytes", "setString", "setInt" -> {
                    int index = (int) args[0]; Object value = args[1];
                    if (method.equals("setBytes")) value = ((byte[]) value).clone();
                    assertNull(parameters.put(index, value)); yield null;
                }
                case "setQueryTimeout" -> { assertEquals(5, args[0]); limits[0]++; yield null; }
                case "setMaxRows" -> { assertEquals(kind.equals("readLanguage") ? 19 : 2, args[0]); limits[1]++; yield null; }
                case "executeUpdate" -> {
                    assertEquals(1, limits[0]); assertEquals(0, limits[1]);
                    writes++; writeOrder.add(kind); verifyBindings(kind, parameters);
                    if (kind.equals("identity") && registered
                            && (Arrays.equals(previousRun, (byte[]) parameters.get(1))
                            || Arrays.equals(previousSnapshot, (byte[]) parameters.get(2)))) {
                        throw new SQLException("identity collision");
                    }
                    if (writes == failWrite && affected == -1) throw new SQLException("injected write failure");
                    pending++; yield writes == failWrite ? affected : 1;
                }
                case "executeQuery" -> {
                    assertEquals(1, limits[0]); assertEquals(1, limits[1]); queries++;
                    verifyBindings(kind, parameters);
                    if (queries == failQuery) throw new SQLException("injected query failure");
                    yield switch (kind) {
                        case "schema" -> { writeOrder.add("schema"); Map<String, Object> row = new HashMap<>();
                            row.put("schema_name", schema); yield result(List.of(row)); }
                        case "readHead" -> result(cleaned ? List.of() : java.util.Collections.nCopies(headCount, head));
                        case "readLanguage" -> result(languages);
                        default -> throw new AssertionError("Not a SELECT");
                    };
                }
                case "close" -> { closedStatements++; yield null; }
                default -> throw new AssertionError("Forbidden statement call: " + method);
            });
        }

        void verifyBindings(String kind, Map<Integer, Object> p) {
            switch (kind) {
                case "identity", "readHead" -> { assertEquals(2, p.size());
                    assertArrayEquals(run, (byte[]) p.get(1)); assertArrayEquals(snapshot, (byte[]) p.get(2)); }
                case "head" -> { assertEquals(4, p.size()); assertArrayEquals(snapshot, (byte[]) p.get(1));
                    assertArrayEquals(run, (byte[]) p.get(2)); assertArrayEquals(bytes("dnd5e2014_srd51_se"), (byte[]) p.get(3));
                    assertArrayEquals(bytes("1"), (byte[]) p.get(4)); }
                case "language" -> { assertEquals(7, p.size()); int i = writes - 3; String[] r = ROWS[i];
                    assertArrayEquals(snapshot, (byte[]) p.get(1)); assertArrayEquals(bytes("language." + r[0]), (byte[]) p.get(2));
                    assertEquals(r[1], p.get(3)); assertEquals(r[2], p.get(4));
                    assertArrayEquals(bytes(r[3]), (byte[]) p.get(5)); assertEquals(59, p.get(6)); assertEquals(i + 1, p.get(7)); }
                case "readLanguage" -> { assertEquals(1, p.size()); assertArrayEquals(snapshot, (byte[]) p.get(1)); }
                case "schema" -> assertTrue(p.isEmpty());
                default -> throw new AssertionError(kind);
            }
        }

        ResultSet result(List<Map<String, Object>> rows) {
            openedResults++; int[] index = {-1};
            return proxy(ResultSet.class, (method, args) -> switch (method) {
                case "next" -> ++index[0] < rows.size();
                case "getObject", "getBytes" -> {
                    assertTrue(index[0] < 18, "Nineteenth row must be detected without fetching fields");
                    assertTrue(rows.get(index[0]).containsKey(args[0]), "Unexpected column: " + args[0]);
                    Object value = rows.get(index[0]).get(args[0]);
                    yield value instanceof byte[] b ? b.clone() : value;
                }
                case "close" -> { closedResults++; yield null; }
                default -> throw new AssertionError("Forbidden result call: " + method);
            });
        }
        void assertClosed() { assertEquals(prepared, closedStatements); assertEquals(openedResults, closedResults); }
    }

    @FunctionalInterface private interface Invocation { Object call(String method, Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> invocation.call(method.getName(), args)));
    }
}
