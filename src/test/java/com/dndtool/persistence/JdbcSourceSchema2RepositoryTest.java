package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.persistence.Schema2SourceLedger.Identity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Independently assembled ledger facts; the finite seeded BASE has no installation/run eligibility. */
class JdbcSourceSchema2RepositoryTest {
    private static final List<ProductionRuleArtifact> ARTIFACTS = List.of(artifact("validation_base"), artifact("validation_extension"), artifact("validation_compatibility"));
    private static final List<Identity> SELECTION = List.of(identity(2), identity(0), identity(1));

    @Test
    void readsOnlySelectsInOneFixedTransactionAndReturnsExactSelectedOrderAndProvenance() throws Exception {
        Fixture fixture = new Fixture();
        var snapshot = fixture.load(SELECTION);
        assertEquals(SELECTION, snapshot.artifacts().stream().map(item -> item.installation().identity()).toList());
        assertEquals(6, snapshot.control().version());
        assertEquals(7, snapshot.control().count());
        for (var item : snapshot.artifacts()) {
            int index = List.of(identity(0), identity(1), identity(2)).indexOf(item.installation().identity());
            var expected = ARTIFACTS.get(index);
            assertArrayEquals(expected.originalZip(), item.installation().artifact().originalZip());
            assertEquals(expected.rawZipSha256(), item.installation().rawZipSha256());
            assertEquals(expected.logicalSha256(), item.installation().logicalSha256());
            assertEquals(installationOperation(index), item.installation().operationId());
            assertEquals(acceptanceOperation(index), item.acceptance().operationId());
            assertEquals(installationOperation(index), item.acceptance().installedOperationId());
            assertEquals("a".repeat(64), item.acceptance().installedOperationDigest());
            assertEquals("c".repeat(64), item.acceptance().reviewSha256());
            assertEquals(index + 1, item.acceptance().installationSequence());
            assertNotNull(item.acceptance().acceptedAt());
        }
        assertThrows(UnsupportedOperationException.class, () -> snapshot.artifacts().clear());
        byte[] copy = snapshot.artifacts().getFirst().installation().artifact().originalZip();
        copy[0] ^= 1;
        assertArrayEquals(ARTIFACTS.get(2).originalZip(), snapshot.artifacts().getFirst().installation().artifact().originalZip());
        assertEquals(List.of("schema", "ledger", "control", "artifact", "acceptance", "zip", "zip", "zip", "rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change", "rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change", "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set", "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant", "rule_schema2_ability_requirement", "rule_schema2_rest", "rule_schema2_check", "rule_schema2_equipment_item", "rule_schema2_execution_change", "rule_schema2_health", "rule_schema2_vital_effect", "rule_schema2_combat", "rule_schema2_combat_change", "rule_schema2_movement", "rule_schema2_weapon_attack", "rule_schema2_spell", "rule_schema2_spell_change", "rule_schema2_spellcasting", "rule_schema2_environment", "rule_schema2_spellbook_member", "rule_schema2_wizard_change", "rule_schema2_wizard_spellcasting"), fixture.queries);
        assertEquals(3, fixture.blobReads);
        assertEquals(1, fixture.commits);
        assertEquals(0, fixture.rollbacks);
        fixture.assertCompleted();
    }

    @Test
    void callerOwnedVariantRequiresReadOnlyRepeatableReadAndLeavesSettlementToCaller() throws Exception {
        Fixture fixture = new Fixture();
        fixture.autoCommit = false;
        fixture.readOnly = true;
        fixture.isolation = Connection.TRANSACTION_REPEATABLE_READ;
        assertEquals(3, JdbcSourceSchema2Repository.read(fixture.connection, SELECTION).artifacts().size());
        assertEquals(0, fixture.commits + fixture.rollbacks + fixture.closes + fixture.aborts);
        assertFalse(fixture.events.stream().anyMatch(event -> event.startsWith("set")));
        fixture.assertResourcesClosed();
        for (int invalid = 0; invalid < 3; invalid++) {
            Fixture bad = new Fixture();
            bad.autoCommit = invalid == 0;
            bad.readOnly = invalid != 1;
            bad.isolation = invalid == 2 ? Connection.TRANSACTION_READ_COMMITTED : Connection.TRANSACTION_REPEATABLE_READ;
            assertThrows(SQLException.class, () -> JdbcSourceSchema2Repository.read(bad.connection, SELECTION));
            assertTrue(bad.queries.isEmpty());
            assertEquals(0, bad.commits + bad.rollbacks + bad.closes + bad.aborts);
        }
    }

    @Test
    void validatesSelectionsBeforeAcquiringAndRequiresAcceptanceForEverySelectedArtifact() throws Exception {
        for (List<Identity> invalid : List.of(List.<Identity>of(), List.of(identity(0), identity(0)))) {
            Fixture fixture = new Fixture();
            assertThrows(IllegalArgumentException.class, () -> fixture.load(invalid));
            assertEquals(0, fixture.acquisitions);
        }
        Fixture missing = new Fixture();
        missing.rows.get("acceptance").removeLast();
        missing.control().put("acceptance_count", 2L);
        missing.control().put("metadata_row_count", 6L);
        missing.control().put("row_version", 5L);
        assertRejected(missing, SELECTION);
        Fixture unselected = new Fixture();
        unselected.rows.get("acceptance").removeLast();
        unselected.control().put("acceptance_count", 2L);
        unselected.control().put("metadata_row_count", 6L);
        unselected.control().put("row_version", 5L);
        assertEquals(2, unselected.load(List.of(identity(0), identity(1))).artifacts().size());
        Fixture absent = new Fixture();
        assertRejected(absent, List.of(new Identity("missing", "1")));
    }

    @Test
    void rejectsCorruptSchemaLedgerControlArtifactsAndAcceptanceBindingIncludingUnselectedRows() {
        List<Consumer<Fixture>> faults = new ArrayList<>();
        faults.add(f -> f.rows.get("schema").getFirst().put("schema_name", bytes("dnd_tool_rules ")));
        faults.add(f -> f.rows.get("ledger").getFirst().put("schema_version", "1"));
        faults.add(f -> f.rows.get("ledger").getFirst().put("script_sha256", bytes("d".repeat(64))));
        faults.add(f -> f.rows.get("ledger").removeLast());
        for (String column : List.of("control_id", "protocol_version", "metadata_row_count", "installation_count", "acceptance_count", "raw_zip_byte_count", "row_version"))
            faults.add(f -> f.control().put(column, -1L));
        for (String column : List.of("module_key", "release_version", "canonical_profile", "package_role", "operation_digest_sha256", "raw_zip_sha256", "logical_sha256"))
            faults.add(f -> f.installed(2).put(column, bytes("invalid")));
        for (String column : List.of("installation_sequence", "operation_fingerprint_version", "author_schema_version", "artifact_protocol_version", "raw_zip_byte_length", "expanded_byte_length"))
            faults.add(f -> f.installed(2).put(column, 99L));
        faults.add(f -> f.installed(2).put("installation_sequence", 2L));
        faults.add(f -> f.installed(2).put("source_operation_id", operationBytes(installationOperation(0))));
        faults.add(f -> f.installed(2).put("source_operation_id", new byte[16]));
        faults.add(f -> f.installed(2).put("installed_at", null));
        faults.add(f -> ((byte[]) f.installed(2).get("original_zip"))[0] ^= 1);
        for (String column : List.of("module_key", "release_version", "canonical_profile", "package_role", "operation_digest_sha256", "installed_operation_digest_sha256", "raw_zip_sha256", "logical_sha256", "review_sha256"))
            faults.add(f -> f.accepted(2).put(column, bytes("invalid")));
        for (String column : List.of("acceptance_sequence", "installation_sequence", "operation_fingerprint_version", "author_schema_version", "artifact_protocol_version"))
            faults.add(f -> f.accepted(2).put(column, 99L));
        faults.add(f -> f.accepted(2).put("acceptance_operation_id", operationBytes(acceptanceOperation(0))));
        faults.add(f -> f.accepted(2).put("acceptance_operation_id", operationBytes(installationOperation(1))));
        faults.add(f -> f.accepted(2).put("installed_operation_id", operationBytes(installationOperation(1))));
        faults.add(f -> f.accepted(2).put("accepted_at", null));
        for (Consumer<Fixture> fault : faults) {
            Fixture fixture = new Fixture();
            fault.accept(fixture);
            assertRejected(fixture, List.of(identity(0)));
        }
    }

    @Test
    void checksRowAndByteBudgetsBeforeAnyBlobAndObservesOverflowWithoutReadingItsValues() {
        for (String table : List.of("schema", "ledger", "control", "artifact", "acceptance")) {
            Fixture fixture = new Fixture();
            int maximum = table.equals("artifact") || table.equals("acceptance") ? 16 : table.equals("ledger") ? RuleSchemaMigrations.expectations().size() : 1;
            while (fixture.rows.get(table).size() <= maximum) fixture.rows.get(table).add(new HashMap<>(fixture.rows.get(table).getFirst()));
            assertRejected(fixture, SELECTION);
            assertEquals(maximum, fixture.maximumValueRow.get(table));
            assertEquals(0, fixture.blobReads);
        }
        Fixture length = new Fixture();
        length.installed(0).put("raw_zip_byte_length", 2097153L);
        assertRejected(length, SELECTION);
        assertEquals(0, length.blobReads);
        for (boolean expanded : List.of(false, true)) {
            Fixture total = new Fixture();
            total.rows.get("artifact").clear(); total.rows.get("acceptance").clear();
            int count = expanded ? 9 : 5;
            for (int index = 0; index < count; index++) {
                Map<String, Object> row = new HashMap<>(new Fixture().installed(0));
                if (expanded) row.put("expanded_byte_length", 1048576L);
                else { row.put("raw_zip_byte_length", 2097152L); row.put("original_zip", new byte[2097152]); }
                total.rows.get("artifact").add(row);
            }
            total.control().put("installation_count", (long) count);
            total.control().put("acceptance_count", 0L);
            total.control().put("metadata_row_count", 1L + count);
            total.control().put("row_version", (long) count);
            total.control().put("raw_zip_byte_count", 8388608L);
            assertRejected(total, SELECTION);
            assertEquals(0, total.blobReads);
        }
    }

    @Test
    void boundedStreamsRequireExactLengthAndEofAndAlwaysClose() {
        for (int delta : new int[] {-1, 1}) {
            Fixture fixture = new Fixture();
            byte[] original = ARTIFACTS.getFirst().originalZip();
            fixture.streamOverride = Arrays.copyOf(original, original.length + delta);
            assertRejected(fixture, SELECTION);
            assertEquals(1, fixture.blobReads);
            assertEquals(1, fixture.streamCloses);
            assertTrue(fixture.streamBytes <= original.length + 1);
        }
    }

    @Test
    void transactionAndResourceFailuresFailClosedAndUnsafeConnectionsAreAbortedOrQuarantined() {
        for (String failure : List.of("setReadOnly", "setIsolation", "begin", "execute:artifact", "rsClose:acceptance", "stmtClose:zip", "commit", "restoreIsolation", "restoreReadOnly", "restoreAuto", "close")) {
            Fixture fixture = new Fixture();
            fixture.failures.add(failure);
            assertThrows(SQLException.class, () -> fixture.load(SELECTION), failure);
            if (!failure.equals("rsClose:acceptance") && !failure.equals("stmtClose:zip")) fixture.assertResourcesClosed();
            assertEquals(1, fixture.closes, failure);
        }
        Fixture unsafe = new Fixture();
        unsafe.failures.addAll(Set.of("execute:artifact", "rollback", "abort"));
        SQLException original = assertThrows(SQLException.class, () -> unsafe.load(SELECTION));
        assertEquals("execute:artifact", original.getMessage());
        assertEquals(2, original.getSuppressed().length);
        assertFalse(unsafe.events.contains("restoreAuto"));
        assertEquals(0, unsafe.closes);
        Fixture borrowed = new Fixture();
        borrowed.autoCommit = false;
        assertThrows(SQLException.class, () -> borrowed.load(SELECTION));
        assertTrue(borrowed.queries.isEmpty());
        assertEquals(0, borrowed.commits + borrowed.rollbacks);
        assertEquals(1, borrowed.aborts);
        assertEquals(1, borrowed.closes);
    }

    private static void assertRejected(Fixture fixture, List<Identity> selected) {
        assertEquals("Invalid source schema 2 ledger", assertThrows(SQLException.class, () -> fixture.load(selected)).getMessage());
        assertEquals(0, fixture.commits);
        assertEquals(1, fixture.rollbacks);
        fixture.assertCompleted();
    }
    private static Identity identity(int index) { return new Identity(ARTIFACTS.get(index).moduleKey(), ARTIFACTS.get(index).releaseVersion()); }
    private static UUID installationOperation(int index) { return UUID.fromString("00000000-0000-4000-8000-00000000000" + index); }
    private static UUID acceptanceOperation(int index) { return UUID.fromString("10000000-0000-4000-8000-00000000000" + index); }
    private static byte[] operationBytes(UUID operation) { return ByteBuffer.allocate(16).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits()).array(); }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static ProductionRuleArtifact artifact(String name) {
        try (InputStream input = JdbcSourceSchema2RepositoryTest.class.getResourceAsStream("/schema2-production/" + name + ".zip.hex")) {
            return ProductionRuleArtifact.read(HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s", "")));
        } catch (IOException failure) { throw new AssertionError(failure); }
    }

    private static final class Fixture extends Schema2SourceReadFixture { }

}
