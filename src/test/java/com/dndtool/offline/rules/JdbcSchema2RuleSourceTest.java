package com.dndtool.offline.rules;

import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.offline.rules.Schema2SourceJdbcFixture.*;
import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class JdbcSchema2RuleSourceTest {
    private static final UUID INSTALL = UUID.fromString("b9aa1c77-cf09-4a47-8798-3f8ed0cdd50a");
    private static final UUID ACCEPT = UUID.fromString("6403f9b3-10cd-4d13-9ed5-cd094f246de7");
    private static final String INSTALL_DIGEST = "a".repeat(64), ACCEPT_DIGEST = "b".repeat(64), REVIEW = "c".repeat(64);

    @Test
    void installationAndExplicitAcceptanceAreIndependentImmutableFactsBoundToExactBytes() throws Exception {
        var database = new Schema2SourceJdbcFixture(); var source = new JdbcSchema2RuleSource(transaction(database));
        var initial = source.read(source.lock(), 0);
        var artifact = artifact("addon", "VersionA", "Reviewed license.\n");
        var installed = source.install(initial, INSTALL, INSTALL_DIGEST, artifact);
        assertEquals(INSTALL, installed.operationId()); assertArrayEquals(artifact.originalZip(), installed.artifact().originalZip());
        assertTrue(database.acceptances.isEmpty()); assertEquals(2, database.writes);
        var installedState = source.read(source.lock(), 0);
        assertEquals(new JdbcSchema2RuleSource.Control(2, 1, 0, artifact.originalZip().length, 1), installedState.control());
        var accepted = source.accept(installedState, ACCEPT, ACCEPT_DIGEST, installed, REVIEW);
        assertEquals(INSTALL, accepted.installedOperationId()); assertEquals(INSTALL_DIGEST, accepted.installedOperationDigest());
        assertEquals(artifact.rawZipSha256(), accepted.rawZipSha256()); assertEquals(artifact.logicalSha256(), accepted.logicalSha256());
        assertEquals(REVIEW, accepted.reviewSha256()); assertNotNull(accepted.acceptedAt());
        var complete = source.read(source.lock(), 2);
        assertEquals(3, complete.control().count()); assertEquals(1, complete.control().installations()); assertEquals(1, complete.control().acceptances());
        assertEquals(0, database.commits); assertEquals(0, database.rollbacks); assertEquals(0, database.closes);
        assertTrue(database.queries.getFirst().contains("FROM rule_installation_control WHERE control_id = 1 FOR UPDATE"));
        assertArrayEquals(new Object[]{1, 1, 1L, 0L}, database.legacyControl);
    }

    @Test
    void exactRepeatsReturnOriginalFactsWhileVersionAndOperationCollisionsFailBeforeWrites() throws Exception {
        var database = new Schema2SourceJdbcFixture(); var source = new JdbcSchema2RuleSource(transaction(database));
        var artifact = artifact("addon", "1", null);
        var installed = source.install(source.read(source.lock(), 0), INSTALL, INSTALL_DIGEST, artifact);
        var state = source.read(source.lock(), 0);
        assertEquals(installed.operationId(), source.install(state, INSTALL, INSTALL_DIGEST, artifact).operationId());
        assertEquals(installed.operationId(), source.install(state, UUID.randomUUID(), "d".repeat(64), artifact).operationId());
        assertEquals(2, database.writes);
        code("S2_OPERATION_CONFLICT", () -> source.install(state, INSTALL, "d".repeat(64), artifact));
        var repacked = artifact("addon", "1", "Same logic, different exact transport.\n");
        assertEquals(artifact.logicalSha256(), repacked.logicalSha256()); assertNotEquals(artifact.rawZipSha256(), repacked.rawZipSha256());
        code("S2_VERSION_CONFLICT", () -> source.install(state, UUID.randomUUID(), "d".repeat(64), repacked));
        var accepted = source.accept(state, ACCEPT, ACCEPT_DIGEST, installed, REVIEW);
        var complete = source.read(source.lock(), 0);
        assertEquals(accepted, source.accept(complete, ACCEPT, ACCEPT_DIGEST, installed, REVIEW));
        assertEquals(accepted, source.accept(complete, UUID.randomUUID(), "d".repeat(64), installed, REVIEW));
        code("S2_OPERATION_CONFLICT", () -> source.accept(complete, ACCEPT, ACCEPT_DIGEST, installed, "e".repeat(64)));
        code("S2_ACCEPTANCE_CONFLICT", () -> source.accept(complete, UUID.randomUUID(), "d".repeat(64), installed, "e".repeat(64)));
        code("S2_VERSION_CONFLICT", () -> source.install(complete, UUID.randomUUID(), "d".repeat(64), repacked));
        assertEquals(4, database.writes);
    }

    @Test
    void readbackRejectsCorruptIdentityMetadataZipAndExactAcceptanceBinding() throws Exception {
        var faults = new ArrayList<Consumer<Schema2SourceJdbcFixture>>();
        for (String column : List.of("module_key", "release_version", "operation_digest_sha256", "canonical_profile", "package_role",
                "raw_zip_sha256", "logical_sha256"))
            faults.add(f -> f.artifacts.getFirst().put(column, b("invalid")));
        faults.add(f -> f.artifacts.getFirst().put("author_schema_version", 1));
        faults.add(f -> f.artifacts.getFirst().put("installation_sequence", 2L));
        faults.add(f -> f.artifacts.getFirst().put("raw_zip_byte_length", 1L));
        faults.add(f -> f.artifacts.getFirst().put("expanded_byte_length", 1L));
        faults.add(f -> ((byte[]) f.artifacts.getFirst().get("original_zip"))[0] ^= 1);
        faults.add(f -> f.artifacts.getFirst().put("installed_at", null));
        faults.add(f -> f.acceptances.getFirst().put("installed_operation_digest_sha256", b("d".repeat(64))));
        faults.add(f -> f.acceptances.getFirst().put("raw_zip_sha256", b("d".repeat(64))));
        faults.add(f -> f.acceptances.getFirst().put("review_sha256", null));
        faults.add(f -> f.control.put("installation_count", 0L));
        for (var fault : faults) {
            var database = installedAndAccepted(); fault.accept(database);
            var source = new JdbcSchema2RuleSource(transaction(database));
            assertThrows(IllegalStateException.class, () -> source.read(source.lock(), 0));
            assertEquals(4, database.writes); assertEquals(0, database.commits); assertEquals(0, database.rollbacks);
        }
    }

    @Test
    void metadataLengthsRowCapsAndTotalBudgetAreCheckedBeforeAnyBlobRead() throws Exception {
        var database = installedAndAccepted();
        database.artifacts.getFirst().put("raw_zip_byte_length", 2097153L);
        var source = new JdbcSchema2RuleSource(transaction(database)); int blobs = database.blobReads;
        code("S2_SOURCE_STATE", () -> source.read(source.lock(), 0)); assertEquals(blobs, database.blobReads);
        database = installedAndAccepted();
        var one = database.artifacts.getFirst();
        for (int i = 1; i < 17; i++) database.artifacts.add(new HashMap<>(one));
        final var tooMany = database; var boundedSource = new JdbcSchema2RuleSource(transaction(database)); blobs = database.blobReads;
        code("S2_SOURCE_BUDGET", () -> boundedSource.read(boundedSource.lock(), 0)); assertEquals(blobs, tooMany.blobReads);
        database = installedAndAccepted(); database.artifacts.clear(); database.acceptances.clear();
        for (int i = 0; i < 5; i++) {
            var row = new HashMap<>(one); row.put("raw_zip_byte_length", 2097152L); row.put("original_zip", new byte[2097152]); database.artifacts.add(row);
        }
        database.control = row("control_id, protocol_version, metadata_row_count, installation_count, acceptance_count, raw_zip_byte_count, row_version",
                1, 1, 6L, 5L, 0L, 8388608L, 5L);
        final var total = database; var totalSource = new JdbcSchema2RuleSource(transaction(database)); blobs = database.blobReads;
        code("S2_SOURCE_BUDGET", () -> totalSource.read(totalSource.lock(), 0)); assertEquals(blobs, total.blobReads);
        database = installedAndAccepted(); database.artifacts.clear(); database.acceptances.clear();
        for (int i = 0; i < 9; i++) {
            var expanded = new HashMap<>(one); expanded.put("expanded_byte_length", 1048576L); database.artifacts.add(expanded);
        }
        long bytes = ((byte[]) one.get("original_zip")).length * 9L;
        database.control = row("control_id, protocol_version, metadata_row_count, installation_count, acceptance_count, raw_zip_byte_count, row_version",
                1, 1, 10L, 9L, 0L, bytes, 9L);
        final var expandedTotal = database; var expandedSource = new JdbcSchema2RuleSource(transaction(database)); blobs = database.blobReads;
        code("S2_SOURCE_BUDGET", () -> expandedSource.read(expandedSource.lock(), 0)); assertEquals(blobs, expandedTotal.blobReads);
    }

    @Test
    void databaseFailuresAndUnexpectedAffectedRowsLeaveRollbackToTheCaller() throws Exception {
        for (int failed : new int[]{1, 2}) for (boolean wrongCount : new boolean[]{false, true}) {
            var database = new Schema2SourceJdbcFixture(); var connection = transaction(database);
            var source = new JdbcSchema2RuleSource(connection); var state = source.read(source.lock(), 0);
            if (wrongCount) database.wrongAffectedWrite = failed; else database.failWrite = failed;
            assertThrows(Exception.class, () -> source.install(state, INSTALL, INSTALL_DIGEST, artifact("addon", "1", null)));
            assertEquals(0, database.rollbacks); assertEquals(0, database.commits); assertEquals(0, database.closes);
            connection.rollback(); assertTrue(database.artifacts.isEmpty()); assertTrue(database.acceptances.isEmpty());
            assertEquals(1L, database.control.get("metadata_row_count"));
        }
        for (int failed : new int[]{3, 4}) {
            var database = new Schema2SourceJdbcFixture(); var source = new JdbcSchema2RuleSource(transaction(database));
            var installed = source.install(source.read(source.lock(), 0), INSTALL, INSTALL_DIGEST, artifact("addon", "1", null));
            var state = source.read(source.lock(), 0); database.failWrite = failed;
            assertThrows(Exception.class, () -> source.accept(state, ACCEPT, ACCEPT_DIGEST, installed, REVIEW));
            assertEquals(0, database.rollbacks); assertEquals(0, database.commits);
        }
    }

    @Test
    void postWriteCorruptionAndStaleCheckpointsFailClosed() throws Exception {
        var database = new Schema2SourceJdbcFixture(); var source = new JdbcSchema2RuleSource(transaction(database));
        var initial = source.read(source.lock(), 0);
        database.afterWrite = () -> {
            if (database.writes == 2) database.artifacts.getFirst().put("logical_sha256", b("f".repeat(64)));
        };
        code("S2_SOURCE_STATE", () -> source.install(initial, INSTALL, INSTALL_DIGEST, artifact("addon", "1", null)));
        assertEquals(0, database.commits); assertEquals(0, database.rollbacks);
        var fresh = new JdbcSchema2RuleSource(transaction(new Schema2SourceJdbcFixture())); fresh.lock();
        code("S2_SOURCE_STATE", () -> fresh.validateLegacyCheckpoint(1));
        code("S2_SOURCE_STATE", () -> fresh.read(fresh.lock(), 1));
    }

    @Test
    void nearFullRawAndExpandedSourceSupportsCompleteAfterImageAndRejectsAnotherPackageBeforeDml() throws Exception {
        var database = new Schema2SourceJdbcFixture(); long rawBytes = 0;
        for (int i = 0; i < 7; i++) {
            var artifact = capacityArtifact("capacity" + i); rawBytes += artifact.originalZip().length;
            UUID operation = UUID.randomUUID();
            byte[] uuid = ByteBuffer.allocate(16).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits()).array();
            database.artifacts.add(row("module_key, release_version, installation_sequence, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, raw_zip_byte_length, expanded_byte_length, original_zip, installed_at",
                    b(artifact.moduleKey()), b("1"), (long) i + 1, uuid, 1, b("d".repeat(64)), 2, 1,
                    b(artifact.canonicalProfile()), b("EXTENSION"), b(artifact.rawZipSha256()), b(artifact.logicalSha256()),
                    (long) artifact.originalZip().length, artifact.expandedByteLength(), artifact.originalZip(), LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(i)));
        }
        database.control = row("control_id, protocol_version, metadata_row_count, installation_count, acceptance_count, raw_zip_byte_count, row_version",
                1, 1, 8L, 7L, 0L, rawBytes, 7L);
        var source = new JdbcSchema2RuleSource(transaction(database));
        var before = source.read(source.lock(), 7);
        var last = capacityArtifact("capacity7");
        var installed = source.install(before, INSTALL, INSTALL_DIGEST, last);
        var after = source.read(source.lock(), 8);
        assertEquals(7, before.installations().size()); assertEquals(8, after.installations().size());
        assertEquals(last.rawZipSha256(), installed.rawZipSha256()); assertEquals(2, database.writes);
        assertTrue(after.control().zipBytes() > JdbcSchema2RuleSource.MAX_TOTAL_ZIP_BYTES - 32768);
        long expanded = after.installations().values().stream().mapToLong(JdbcSchema2RuleSource.Installed::expandedLength).sum();
        assertTrue(expanded > JdbcSchema2RuleSource.MAX_TOTAL_EXPANDED_BYTES - 32768);
        assertTrue(after.control().zipBytes() <= JdbcSchema2RuleSource.MAX_TOTAL_ZIP_BYTES);
        assertTrue(expanded <= JdbcSchema2RuleSource.MAX_TOTAL_EXPANDED_BYTES);
        var excess = capacityArtifact("capacity8");
        code("S2_SOURCE_BUDGET", () -> source.install(after, UUID.randomUUID(), "e".repeat(64), excess));
        assertEquals(2, database.writes); assertEquals(0, database.commits); assertEquals(0, database.rollbacks);
    }

    private static ProductionRuleArtifact capacityArtifact(String module) {
        var files = new TreeMap<String, byte[]>(); var declarations = new ArrayList<String>();
        for (int i = 0; i < 16; i++) declarations.add("{\"kind\":\"definitions\",\"path\":\"p" + i + ".json\"}");
        String header = new String(artifact(module, "1", null).originalMembers().get("author-package.json"), StandardCharsets.UTF_8)
                .replace("\"files\":[]", "\"files\":[" + String.join(",", declarations) + "]");
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8); files.put("author-package.json", headerBytes);
        int remaining = 1044500 - headerBytes.length;
        for (int i = 0; i < 16; i++) {
            int length = Math.min(65536, remaining - (15 - i) * 2);
            byte[] body = new byte[length]; Arrays.fill(body, (byte) ' '); body[0] = '['; body[1] = ']';
            files.put("p" + i + ".json", body); remaining -= length;
        }
        assertEquals(0, remaining);
        return ProductionRuleArtifact.read(zip(files));
    }

    @Test
    void schema2ModuleIdentityRejectsSchema1DottedAndLongKeys() {
        for (String key : List.of("a.b", "a".repeat(65), "UPPER", "with space", "a\0"))
            code("S2_SOURCE_STATE", () -> new JdbcSchema2RuleSource.Identity(key, "1"));
        assertDoesNotThrow(() -> new JdbcSchema2RuleSource.Identity("a".repeat(64), "VersionA"));
    }

    @Test
    void typedSourceStorageDoesNotBypassTheClosedProductionInstallationGate() throws Exception {
        for (var artifact : Schema2GrantFixtures.fixedArtifacts()) {
            var database = new Schema2SourceJdbcFixture(); var source = new JdbcSchema2RuleSource(transaction(database));
            var state = source.read(source.lock(), 0);
            var rejected = assertThrows(Schema2Exception.class, () -> source.install(state, INSTALL, INSTALL_DIGEST, artifact));
            assertEquals(artifact.role().name().equals("BASE") ? "S2_BASE_INCOMPLETE" : "S2_MECHANISM_UNSUPPORTED", rejected.diagnostic().code());
            assertEquals(0, database.writes); assertTrue(database.artifacts.isEmpty());
            assertTrue(database.characterTables.values().stream().allMatch(List::isEmpty));
        }
    }

    private static java.sql.Connection transaction(Schema2SourceJdbcFixture database) throws Exception {
        var connection = database.open(); connection.setAutoCommit(false); return connection;
    }

    private static Schema2SourceJdbcFixture installedAndAccepted() throws Exception {
        var database = new Schema2SourceJdbcFixture(); var source = new JdbcSchema2RuleSource(transaction(database));
        var installed = source.install(source.read(source.lock(), 0), INSTALL, INSTALL_DIGEST, artifact("addon", "1", null));
        source.accept(source.read(source.lock(), 0), ACCEPT, ACCEPT_DIGEST, installed, REVIEW);
        return database;
    }
    private static void code(String expected, Checked action) {
        assertEquals(expected, assertThrows(JdbcSchema2RuleSource.SourceException.class, action::run).code());
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}

