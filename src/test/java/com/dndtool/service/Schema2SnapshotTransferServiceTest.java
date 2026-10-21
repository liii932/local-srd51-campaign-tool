package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.persistence.Schema2RuntimeJdbcFixture;
import com.dndtool.persistence.Schema2SourceReadFixture;
import com.dndtool.service.Schema2SnapshotTransferService.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real coordinator and repositories over transaction SQL proxies; no MySQL or execution evidence. */
class Schema2SnapshotTransferServiceTest {
    private static final UUID OPERATION = UUID.fromString("01872103-0345-4abc-8def-0123456789ab");
    private static final Identity IDENTITY = new Identity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));
    private static final String GUARD_SQL = "SELECT control_id, protocol_version, row_version, snapshot_count FROM s2_runtime_control WHERE control_id = 1 FOR UPDATE";

    @Test void acceptedSourceCommitsAndClosesBeforeRuntimeGuardThenFullTypedReadbackMatchesGolden() throws Exception {
        var fixture = new Fixture(); List<String> ordering = new ArrayList<>();
        fixture.source.observer = event -> ordering.add("source:" + event);
        fixture.runtime.observer = event -> ordering.add("runtime:" + event);
        fixture.runtime.beforeAcquire = () -> {
            assertEquals(1, fixture.source.commitCount()); assertEquals(1, fixture.source.closeCount());
        };
        var result = fixture.service.transfer(fixture.request());
        assertEquals(Status.COMMITTED, result.status()); assertEquals("S2_SNAPSHOT_COMMITTED", result.code());
        assertEquals(IDENTITY, result.snapshot().identity()); assertGolden(result.snapshot().projection());
        assertTrue(ordering.indexOf("source:commit") < ordering.indexOf("source:close"));
        assertTrue(ordering.indexOf("source:close") < ordering.indexOf("runtime:acquire"));
        assertEquals(GUARD_SQL, fixture.runtime.sql.getFirst());
        assertEquals(1, fixture.runtime.commits); assertEquals(0, fixture.runtime.rollbacks);
        assertEquals(1, fixture.runtime.rows("s2_runtime_operation").size()); assertGuard(fixture.runtime, 1);
        assertTrue(fixture.runtime.autoCommit); assertFalse(fixture.runtime.readOnly);
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, fixture.runtime.isolation);

        fixture.source.unavailable(true); int sourceReads = fixture.source.acquisitionCount();
        var inspected = fixture.service.inspect(result.intent());
        assertEquals(Status.RESOLVED_COMMITTED, inspected.status()); assertGolden(inspected.snapshot().projection());
        assertEquals(sourceReads, fixture.source.acquisitionCount());
        try (Connection reader = fixture.runtime.dataSource().getConnection()) {
            reader.setAutoCommit(false);
            assertGolden(new JdbcSchema2RuntimeSnapshotRepository().read(reader, IDENTITY).projection());
            reader.rollback();
        }
        assertEquals(sourceReads, fixture.source.acquisitionCount());
    }

    @Test void sourceReadCommitAndCloseFailuresNeverAcquireRuntime() {
        for (String failure : List.of("execute:acceptance", "execute:zip", "commit", "close", "restoreIsolation")) {
            var fixture = new Fixture(); fixture.source.fail(failure);
            var result = fixture.service.transfer(fixture.request());
            assertEquals(Status.NOT_EXECUTED, result.status(), failure); assertNull(result.snapshot());
            assertEquals(0, fixture.runtime.acquisitions, failure); assertEquals(0, fixture.runtime.writes);
        }
    }

    @Test void sameOperationReplaysImmutableSnapshotAndConflictingIntentOrIdentityHasNoDml() {
        var fixture = new Fixture(); var accepted = fixture.service.transfer(fixture.request());
        assertEquals(Status.COMMITTED, accepted.status()); int writes = fixture.runtime.writes;
        byte[] operationDigest = ((byte[]) fixture.runtime.rows("s2_runtime_operation").getFirst().get("operation_digest_sha256")).clone();
        var replay = fixture.service.transfer(fixture.request());
        assertEquals(Status.COMMITTED, replay.status()); assertEquals("S2_SNAPSHOT_REPLAY", replay.code());
        assertGolden(replay.snapshot().projection()); assertEquals(writes, fixture.runtime.writes);
        assertArrayEquals(operationDigest, (byte[]) fixture.runtime.rows("s2_runtime_operation").getFirst().get("operation_digest_sha256"));
        var operationConflict = fixture.service.transfer(new Request(OPERATION, Identity.random(), fixture.source.selections(), null));
        assertEquals(Status.ROLLED_BACK, operationConflict.status()); assertEquals("S2_OPERATION_CONFLICT", operationConflict.code());
        var identityConflict = fixture.service.transfer(new Request(UUID.randomUUID(), IDENTITY, fixture.source.selections(), null));
        assertEquals(Status.ROLLED_BACK, identityConflict.status()); assertEquals("S2_IDENTITY_CONFLICT", identityConflict.code());
        assertEquals(writes, fixture.runtime.writes); assertEquals(1, fixture.runtime.commits); assertGuard(fixture.runtime, 1);
    }

    @Test void restoreRequiresExactMemberOrderVersionBothDigestsProfileAndProjectionDigestsBeforeRuntime() {
        var fixture = new Fixture(); var binding = binding(fixture);
        var restored = fixture.service.transfer(new Request(OPERATION, IDENTITY, fixture.source.selections(), binding));
        assertEquals(Status.COMMITTED, restored.status()); assertEquals(Kind.RESTORE, restored.intent().kind());
        assertGolden(restored.snapshot().projection());
        List<UnaryOperator<Schema2RulesBinding>> mutations = List.of(
                original -> { var members = new ArrayList<>(original.members()); Collections.swap(members, 1, 2); return changed(original, members, original.compositionSha256(), original.finalRulesSha256(), original.chainSha256()); },
                original -> memberChanged(original, "2", original.members().getFirst().rawZipSha256(), original.members().getFirst().logicalSha256()),
                original -> memberChanged(original, "1", "0".repeat(64), original.members().getFirst().logicalSha256()),
                original -> memberChanged(original, "1", original.members().getFirst().rawZipSha256(), "0".repeat(64)),
                original -> changed(original, original.members(), "0".repeat(64), original.finalRulesSha256(), original.chainSha256()),
                original -> changed(original, original.members(), original.compositionSha256(), "0".repeat(64), original.chainSha256()),
                original -> changed(original, original.members(), original.compositionSha256(), original.finalRulesSha256(), "0".repeat(64)));
        for (var mutation : mutations) {
            var rejected = new Fixture();
            var result = rejected.service.transfer(new Request(OPERATION, IDENTITY, rejected.source.selections(), mutation.apply(binding)));
            assertEquals(Status.NOT_EXECUTED, result.status()); assertEquals("S2_RULES_BINDING_MISMATCH", result.code());
            assertEquals(0, rejected.runtime.acquisitions); assertEquals(0, rejected.runtime.writes);
        }
        assertThrows(IllegalArgumentException.class, () -> new Schema2RulesBinding("other-profile", binding.compositionAlgorithm(), binding.members(), binding.compositionSha256(), binding.finalRulesSha256(), binding.chainSha256()));
        assertThrows(IllegalArgumentException.class, () -> new Schema2RulesBinding(binding.canonicalProfile(), "other-algorithm", binding.members(), binding.compositionSha256(), binding.finalRulesSha256(), binding.chainSha256()));
    }

    @Test void eachTypedTableReceiptAndGuardWriteFailureRollsBackAllRowsAndCounters() {
        var good = new Fixture(); assertEquals(Status.COMMITTED, good.service.transfer(good.request()).status());
        // One boundary from every typed table, plus immutable receipt and guard writes.
        Set<Integer> boundaries = new LinkedHashSet<>(); Set<String> tables = new HashSet<>(); int write = 0;
        for (String sql : good.runtime.sql) if (sql.startsWith("INSERT INTO ") || sql.startsWith("UPDATE ")) {
            write++; String table = sql.split(" ")[sql.startsWith("INSERT") ? 2 : 1]; if (tables.add(table)) boundaries.add(write);
        }
        assertTrue(tables.containsAll(List.of("s2_runtime_identity", "s2_runtime_language", "s2_runtime_language_set", "s2_runtime_language_entry", "s2_runtime_field_chain", "s2_runtime_chain_step", "s2_runtime_operation", "s2_runtime_control")));
        for (int boundary : boundaries) for (int count : new int[]{-1, 0, 2}) {
            var fixture = new Fixture(); fixture.runtime.failWrite = boundary; fixture.runtime.affected = count;
            var result = fixture.service.transfer(fixture.request());
            assertEquals(Status.ROLLED_BACK, result.status(), "write=" + boundary + ", affected=" + count);
            assertNull(result.snapshot()); assertEquals(0, fixture.runtime.commits); assertEquals(1, fixture.runtime.rollbacks);
            assertEmptyRuntime(fixture.runtime);
        }
    }

    @Test void actualReadbackCorruptionAndFailureAfterReceiptRollBackMaterialReceiptAndGuard() {
        var corrupt = new Fixture();
        corrupt.runtime.beforeRead = runtime -> runtime.rows("s2_runtime_language").getFirst().put("display_name", "Changed but valid");
        assertEquals(Status.ROLLED_BACK, corrupt.service.transfer(corrupt.request()).status()); assertEmptyRuntime(corrupt.runtime);

        var afterReceipt = new Fixture();
        afterReceipt.runtime.beforeQuery = query -> {
            if (query.contains("FROM s2_runtime_snapshot s JOIN") && !afterReceipt.runtime.rows("s2_runtime_operation").isEmpty())
                afterReceipt.runtime.failQuery = afterReceipt.runtime.queries;
        };
        assertEquals(Status.ROLLED_BACK, afterReceipt.service.transfer(afterReceipt.request()).status());
        assertEquals(1, afterReceipt.runtime.rollbacks); assertEmptyRuntime(afterReceipt.runtime);
    }

    @Test void lostCommitResponseStaysUnknownThenOnlySameIsolatedIntentResolvesWithoutSource() {
        for (var outcome : List.of(Schema2RuntimeJdbcFixture.LostCommit.COMMITTED, Schema2RuntimeJdbcFixture.LostCommit.ROLLED_BACK)) {
            var fixture = new Fixture(); fixture.runtime.lostCommit = outcome;
            var unknown = fixture.service.transfer(fixture.request());
            assertEquals(Status.UNKNOWN, unknown.status()); assertNotNull(unknown.intent()); assertNull(unknown.snapshot());
            assertEquals(1, fixture.runtime.commits); assertEquals(1, fixture.runtime.aborts); assertEquals(0, fixture.runtime.rollbacks);
            fixture.source.unavailable(true); int sourceReads = fixture.source.acquisitionCount(), acquisitions = fixture.runtime.acquisitions;
            var other = new Intent(UUID.randomUUID(), unknown.intent().identity(), unknown.intent().kind(), unknown.intent().rules());
            assertEquals("S2_RESOLUTION_REQUIRES_ISOLATION", fixture.service.resolve(other).code()); assertEquals(acquisitions, fixture.runtime.acquisitions);
            assertEquals("S2_TRANSFER_BLOCKED", fixture.service.transfer(fixture.request()).code());
            var resolved = fixture.service.resolve(unknown.intent());
            assertEquals(outcome == Schema2RuntimeJdbcFixture.LostCommit.COMMITTED ? Status.RESOLVED_COMMITTED : Status.NOT_COMMITTED, resolved.status());
            if (outcome == Schema2RuntimeJdbcFixture.LostCommit.COMMITTED) assertGolden(resolved.snapshot().projection()); else assertNull(resolved.snapshot());
            assertEquals(sourceReads, fixture.source.acquisitionCount()); assertEquals(1, fixture.runtime.commits);
        }
    }

    @Test void failedRollbackIsUnknownUntilConnectionAbortIsolatesWriterAndSourceFreeResolutionFindsNoReceipt() {
        var fixture = new Fixture(); fixture.runtime.failWrite = 2; fixture.runtime.rollbackFails = true;
        var unknown = fixture.service.transfer(fixture.request());
        assertEquals(Status.UNKNOWN, unknown.status()); assertNotNull(unknown.intent()); assertNull(unknown.snapshot());
        assertEquals(0, fixture.runtime.commits); assertEquals(1, fixture.runtime.rollbacks); assertEquals(1, fixture.runtime.aborts);
        assertEmptyRuntime(fixture.runtime);
        fixture.runtime.rollbackFails = false; fixture.source.unavailable(true);
        int sourceReads = fixture.source.acquisitionCount();
        var resolved = fixture.service.resolve(unknown.intent());
        assertEquals(Status.NOT_COMMITTED, resolved.status()); assertEquals(sourceReads, fixture.source.acquisitionCount());
    }

    @Test void fatalErrorAfterActualDmlRollsBackOrAbortsThenRethrowsSameErrorAndQuarantinesService() {
        for (boolean rollbackFails : List.of(false, true)) {
            var fixture = new Fixture(); var fatal = new AssertionError("Injected failure after actual identity insert");
            fixture.runtime.rollbackFails = rollbackFails;
            fixture.runtime.afterWrite = runtime -> {
                assertEquals(1, runtime.rows("s2_runtime_identity").size());
                throw fatal;
            };
            assertSame(fatal, assertThrows(AssertionError.class, () -> fixture.service.transfer(fixture.request())));
            assertEquals(1, fixture.runtime.writes); assertEquals(1, fixture.runtime.rollbacks); assertEquals(0, fixture.runtime.commits);
            assertEquals(rollbackFails ? 1 : 0, fixture.runtime.aborts); assertEquals(1, fixture.runtime.closes);
            assertEmptyRuntime(fixture.runtime);
            int sourceReads = fixture.source.acquisitionCount(), runtimeAcquisitions = fixture.runtime.acquisitions;
            var blocked = fixture.service.transfer(fixture.request());
            assertEquals(Status.NOT_EXECUTED, blocked.status()); assertEquals("S2_TRANSFER_BLOCKED", blocked.code());
            assertEquals(sourceReads, fixture.source.acquisitionCount()); assertEquals(runtimeAcquisitions, fixture.runtime.acquisitions);
        }
    }

    @Test void abortFailureRetainsQuarantineAndCannotClaimAbsentRowMeansRollback() {
        var fixture = new Fixture(); fixture.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.ROLLED_BACK; fixture.runtime.abortFails = true;
        var unknown = fixture.service.transfer(fixture.request());
        assertEquals(Status.UNKNOWN, unknown.status()); assertEquals("S2_CONNECTION_ISOLATION_FAILED", unknown.code());
        int runtimeReads = fixture.runtime.acquisitions;
        assertEquals("S2_RESOLUTION_REQUIRES_ISOLATION", fixture.service.resolve(unknown.intent()).code());
        assertEquals("S2_TRANSFER_BLOCKED", fixture.service.inspect(unknown.intent()).code());
        assertEquals(runtimeReads, fixture.runtime.acquisitions);
    }

    @Test void confirmedCommitWithCloseFailureRetainsCommitAndBlocksAutomaticRetry() {
        var fixture = new Fixture(); fixture.runtime.closeFails = true;
        var committed = fixture.service.transfer(fixture.request());
        assertEquals(Status.COMMITTED, committed.status()); assertEquals("S2_COMMITTED_CONNECTION_FAILURE", committed.code());
        assertGolden(committed.snapshot().projection()); assertGuard(fixture.runtime, 1);
        int writes = fixture.runtime.writes, sourceReads = fixture.source.acquisitionCount();
        assertEquals("S2_TRANSFER_BLOCKED", fixture.service.transfer(fixture.request()).code());
        assertEquals(writes, fixture.runtime.writes); assertEquals(sourceReads, fixture.source.acquisitionCount());
        fixture.runtime.closeFails = false; fixture.source.unavailable(true);
        var fresh = new Schema2SnapshotTransferService(fixture.source.dataSource(), fixture.runtime.dataSource());
        assertEquals(Status.RESOLVED_COMMITTED, fresh.inspect(committed.intent()).status());
        assertEquals(sourceReads, fixture.source.acquisitionCount());
    }

    @Test void freshReaderMissingReceiptIsUnprovenAndNeverInitiatesSourceOrWrites() {
        var fixture = new Fixture(); var intent = new Intent(OPERATION, IDENTITY, Kind.CAPTURE, binding(fixture));
        fixture.source.unavailable(true);
        var result = fixture.service.inspect(intent);
        assertEquals(Status.UNKNOWN, result.status()); assertEquals("S2_SNAPSHOT_OUTCOME_UNPROVEN", result.code());
        assertEquals(0, fixture.source.acquisitionCount()); assertEquals(0, fixture.runtime.writes); assertEquals(0, fixture.runtime.commits);
        assertEquals("S2_RESOLUTION_REQUIRES_ISOLATION", fixture.service.resolve(intent).code());
    }

    @Test void corruptRetainedTypedRowsBlockReplayBeforeDmlInsteadOfTrustingReceiptDigests() {
        var fixture = new Fixture(); var accepted = fixture.service.transfer(fixture.request());
        assertEquals(Status.COMMITTED, accepted.status()); int writes = fixture.runtime.writes;
        fixture.runtime.rows("s2_runtime_chain_step").getFirst().put("author_pointer", "/incorrect"); fixture.runtime.saveCommitted();
        fixture.source.unavailable(true);
        var result = fixture.service.inspect(accepted.intent());
        assertEquals(Status.ROLLED_BACK, result.status()); assertNull(result.snapshot()); assertEquals(writes, fixture.runtime.writes);
    }

    @Test void concurrentTransferReturnsBusyWithinBoundBeforeSecondSourceOrRuntimeAcquisition() throws Exception {
        var fixture = new Fixture(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        fixture.runtime.beforeQuery = query -> {
            if (!query.equals(GUARD_SQL)) return;
            entered.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Result> running = executor.submit(() -> fixture.service.transfer(fixture.request()));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                var busy = fixture.service.transfer(fixture.request()); assertEquals(Status.NOT_EXECUTED, busy.status()); assertEquals("S2_TRANSFER_BUSY", busy.code());
            });
            assertEquals(1, fixture.source.acquisitionCount()); assertEquals(1, fixture.runtime.acquisitions);
        } finally { release.countDown(); executor.shutdown(); }
        assertEquals(Status.COMMITTED, running.get(5, TimeUnit.SECONDS).status()); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static Schema2RulesBinding binding(Fixture fixture) { return Schema2RulesBinding.from(ProductionSchema2Projection.compose(fixture.source.artifacts())); }
    private static Schema2RulesBinding memberChanged(Schema2RulesBinding original, String version, String raw, String logical) {
        var members = new ArrayList<>(original.members()); var first = members.getFirst();
        members.set(0, new ProductionSchema2Projection.StoredMember(first.moduleKey(), version, first.role(), raw, logical));
        return changed(original, members, original.compositionSha256(), original.finalRulesSha256(), original.chainSha256());
    }
    private static Schema2RulesBinding changed(Schema2RulesBinding original, List<ProductionSchema2Projection.StoredMember> members, String configuration, String rules, String chains) {
        return new Schema2RulesBinding(original.canonicalProfile(), original.compositionAlgorithm(), members, configuration, rules, chains);
    }
    private static void assertEmptyRuntime(Schema2RuntimeJdbcFixture runtime) {
        assertGuard(runtime, 0);
        runtime.tables.forEach((name, rows) -> { if (!name.equals("s2_runtime_control")) assertTrue(rows.isEmpty(), name); });
        runtime.committedRows().forEach((name, rows) -> { if (!name.equals("s2_runtime_control")) assertTrue(rows.isEmpty(), "committed " + name); });
    }
    private static void assertGuard(Schema2RuntimeJdbcFixture runtime, int count) {
        var guard = runtime.rows("s2_runtime_control").getFirst();
        assertEquals(count, ((Number) guard.get("snapshot_count")).intValue()); assertEquals(count, ((Number) guard.get("row_version")).longValue());
    }
    private static void assertGolden(ProductionSchema2Projection projection) {
        assertArrayEquals(resource("configuration.json"), projection.configurationJson()); assertArrayEquals(resource("final-rules.json"), projection.finalRulesJson()); assertArrayEquals(resource("chains.json"), projection.chainsJson());
    }
    private static byte[] resource(String path) {
        try (var input = Schema2SnapshotTransferServiceTest.class.getResourceAsStream("/schema2-production/expected/" + path)) { return Objects.requireNonNull(input).readAllBytes(); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static final class Fixture {
        final Schema2SourceReadFixture source = new Schema2SourceReadFixture();
        final Schema2RuntimeJdbcFixture runtime = new Schema2RuntimeJdbcFixture();
        final Schema2SnapshotTransferService service = new Schema2SnapshotTransferService(source.dataSource(), runtime.dataSource());
        Request request() { return new Request(OPERATION, IDENTITY, source.selections(), null); }
    }
}
