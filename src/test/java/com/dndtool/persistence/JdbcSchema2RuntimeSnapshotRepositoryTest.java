package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Strict row/transaction proxy evidence only, not real MySQL acceptance. */
class JdbcSchema2RuntimeSnapshotRepositoryTest {
    private static final Identity ID = new Identity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"), UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));
    private static final JdbcSchema2RuntimeSnapshotRepository REPOSITORY = new JdbcSchema2RuntimeSnapshotRepository();

    @Test void fullActualTypedReadbackMatchesIndependentGoldenBytesAndHasNoSourceDependency() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture(); var projection = projection();
        var stored = REPOSITORY.store(fixture.connection, ID, projection);
        assertEquals(ID, stored.identity()); assertGolden(stored.projection());
        assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
        assertEquals(projection.members().size(), fixture.rows("s2_runtime_member").size());
        assertEquals(projection.projection().rules().size(), fixture.rows("s2_runtime_language").size() + fixture.rows("s2_runtime_language_set").size());
        assertEquals(projection.projection().entries().size(), fixture.rows("s2_runtime_language_entry").size());
        assertEquals(projection.projection().chains().size(), fixture.rows("s2_runtime_field_chain").size());
        assertEquals(projection.projection().chains().stream().mapToInt(chain -> chain.steps().size()).sum(), fixture.rows("s2_runtime_chain_step").size());
        fixture.connection.commit();
        // The read API receives no expected projection, source artifact or original ZIP.
        var restored = REPOSITORY.read(fixture.connection, ID); assertGolden(restored.projection());
        for (String sql : fixture.sql) {
            assertFalse(sql.contains("dnd_tool_rules")); assertFalse(sql.contains("runtime_rule_snapshot"));
            assertFalse(sql.toLowerCase(Locale.ROOT).contains("zip_bytes"));
            if (sql.startsWith("SELECT") && !sql.contains("DATABASE()")) assertTrue(sql.contains("LIMIT "));
        }
    }

    @Test void everyWritePointAndWrongAffectedCountNeedCallerRollbackAndNoPartialCommittedRows() throws Exception {
        var good = new Schema2RuntimeJdbcFixture(); var projection = projection(); REPOSITORY.store(good.connection, ID, projection);
        for (int write = 1; write <= good.writes; write++) for (int count : new int[]{-1, 0, 2}) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = write; fixture.affected = count;
            assertThrows(SQLException.class, () -> REPOSITORY.store(fixture.connection, ID, projection));
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
            fixture.connection.rollback(); assertTrue(fixture.tables.isEmpty());
        }
    }

    @Test void everyReadFailureAndLaterCallerFailureRemainOneCallerOwnedTransaction() throws Exception {
        var good = new Schema2RuntimeJdbcFixture(); var projection = projection(); REPOSITORY.store(good.connection, ID, projection);
        for (int query = 1; query <= good.queries; query++) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failQuery = query;
            assertThrows(SQLException.class, () -> REPOSITORY.store(fixture.connection, ID, projection));
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks); fixture.connection.rollback(); assertTrue(fixture.tables.isEmpty());
        }
        var fixture = new Schema2RuntimeJdbcFixture(); REPOSITORY.store(fixture.connection, ID, projection);
        try { throw new SQLException("later business failure"); } catch (SQLException expected) { fixture.connection.rollback(); }
        assertTrue(fixture.tables.isEmpty());
    }

    @Test void completeIdentityBindingPreventsCrossRunReadAndReuseIsNeverIdempotentSuccess() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture(); var projection = projection(); REPOSITORY.store(fixture.connection, ID, projection); fixture.connection.commit();
        var other = Identity.random();
        assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, new Identity(other.runId(), ID.snapshotId())));
        assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, new Identity(ID.runId(), other.snapshotId())));
        assertThrows(SQLException.class, () -> REPOSITORY.store(fixture.connection, ID, projection)); fixture.connection.rollback();
        assertGolden(REPOSITORY.read(fixture.connection, ID).projection());
        REPOSITORY.store(fixture.connection, other, projection); fixture.connection.commit();
        assertEquals(2, fixture.rows("s2_runtime_identity").size()); assertGolden(REPOSITORY.read(fixture.connection, other).projection());
    }

    @Test void missingExtraAndMutatedRowsFailIndependentReadInsteadOfEchoingExpectedObject() throws Exception {
        for (Consumer<Schema2RuntimeJdbcFixture> mutation : List.<Consumer<Schema2RuntimeJdbcFixture>>of(
                f -> f.rows("s2_runtime_member").getFirst().put("load_index", 1),
                f -> f.rows("s2_runtime_member").getFirst().put("logical_sha256", ascii("a".repeat(64))),
                f -> f.rows("s2_runtime_snapshot").getFirst().put("material_scope", ascii("COMPLETE")),
                f -> f.rows("s2_runtime_snapshot").getFirst().put("run_id", Identity.random().runBytes()),
                f -> f.rows("s2_runtime_language").getFirst().put("display_name", "Changed valid name"),
                f -> f.rows("s2_runtime_language_set").getFirst().put("minimum_enabled_members", 128),
                f -> f.rows("s2_runtime_language_entry").getFirst().put("language_rule_key", ascii("missing")),
                f -> f.rows("s2_runtime_chain_step").getFirst().put("author_pointer", "/999"),
                f -> f.rows("s2_runtime_chain_step").getFirst().put("origin", ascii("explicit")),
                f -> f.rows("s2_runtime_chain_step").getFirst().put("load_index", 3),
                f -> f.rows("s2_runtime_chain_step").getFirst().put("before_state", ascii("ABSENT")),
                f -> f.rows("s2_runtime_chain_step").getFirst().put("raw_zip_sha256", ascii("a".repeat(64))),
                f -> f.rows("s2_runtime_field_chain").removeLast(),
                f -> f.rows("s2_runtime_language_entry").removeLast(),
                f -> f.rows("s2_runtime_chain_step").removeLast(),
                f -> f.rows("s2_runtime_chain_step").add(new LinkedHashMap<>(f.rows("s2_runtime_chain_step").getLast())))) {
            var fixture = new Schema2RuntimeJdbcFixture(); REPOSITORY.store(fixture.connection, ID, projection()); fixture.connection.commit(); mutation.accept(fixture);
            assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, ID));
        }
    }

    @Test void jdbcCoercionUnicodeAndStateDiscriminatorsFailClosed() throws Exception {
        for (Object value : Arrays.asList(null, "3", 3.0, true, Long.MAX_VALUE)) {
            var fixture = new Schema2RuntimeJdbcFixture(); REPOSITORY.store(fixture.connection, ID, projection()); fixture.rows("s2_runtime_language").getFirst().put("source_page", value);
            assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, ID));
        }
        for (String value : List.of("e\u0301", "x\u0085", "\ud800", "😀".repeat(121))) {
            var fixture = new Schema2RuntimeJdbcFixture(); REPOSITORY.store(fixture.connection, ID, projection()); fixture.rows("s2_runtime_language").getFirst().put("display_name", value);
            assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, ID));
        }
        var fixture = new Schema2RuntimeJdbcFixture(); REPOSITORY.store(fixture.connection, ID, projection());
        fixture.rows("s2_runtime_chain_step").getFirst().put("after_integer_value", 1);
        assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, ID));
    }

    @Test void exactSchemaWritableTransactionAndSupportedIsolationAreRequiredBeforeWrites() throws Exception {
        for (Consumer<Schema2RuntimeJdbcFixture> mutation : List.<Consumer<Schema2RuntimeJdbcFixture>>of(
                f -> f.autoCommit = true, f -> f.readOnly = true, f -> f.isolation = Connection.TRANSACTION_NONE,
                f -> f.isolation = Connection.TRANSACTION_READ_UNCOMMITTED, f -> f.schema = ascii("dnd_tool_rules"))) {
            var fixture = new Schema2RuntimeJdbcFixture(); mutation.accept(fixture);
            assertThrows(SQLException.class, () -> REPOSITORY.store(fixture.connection, ID, projection())); assertEquals(0, fixture.writes);
        }
    }

    @Test void readOnlyTransactionCanIndependentlyReadWithoutWriteCapability() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture(); REPOSITORY.store(fixture.connection, ID, projection()); fixture.connection.commit();
        int writes = fixture.writes; fixture.readOnly = true;
        assertGolden(REPOSITORY.read(fixture.connection, ID).projection()); assertEquals(writes, fixture.writes);
        assertThrows(SQLException.class, () -> REPOSITORY.store(fixture.connection, Identity.random(), projection()));
    }

    @Test void exactUuidNetworkBytesAndInvalidPairsAreRejected() {
        assertArrayEquals(HexFormat.of().parseHex("00112233445546778899aabbccddeeff"), ID.runBytes());
        assertArrayEquals(HexFormat.of().parseHex("ffeeddccbbaa49888766554433221100"), ID.snapshotBytes());
        assertEquals(ID, Identity.fromBytes(ID.runBytes(), ID.snapshotBytes()));
        assertThrows(IllegalArgumentException.class, () -> new Identity(ID.runId(), ID.runId()));
        assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(new byte[16], ID.snapshotBytes()));
        assertThrows(IllegalArgumentException.class, () -> Identity.fromBytes(new byte[15], ID.snapshotBytes()));
    }

    private static ProductionSchema2Projection projection() {
        return ProductionSchema2Projection.compose(List.of("validation_base", "validation_extension", "validation_compatibility").stream()
                .map(module -> ProductionRuleArtifact.read(HexFormat.of().parseHex(new String(resource(module + ".zip.hex"), StandardCharsets.US_ASCII).strip()))).toList());
    }
    private static void assertGolden(ProductionSchema2Projection projection) {
        assertArrayEquals(resource("expected/configuration.json"), projection.configurationJson());
        assertArrayEquals(resource("expected/final-rules.json"), projection.finalRulesJson());
        assertArrayEquals(resource("expected/chains.json"), projection.chainsJson());
    }
    private static byte[] resource(String path) { try (var input = JdbcSchema2RuntimeSnapshotRepositoryTest.class.getResourceAsStream("/schema2-production/" + path)) { return Objects.requireNonNull(input).readAllBytes(); } catch (Exception failure) { throw new AssertionError(failure); } }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
}
