package com.dndtool.service;

import com.dndtool.module.CharacterProficiencyGrant.GrantKind;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.persistence.BusinessWriteTransactions;
import com.dndtool.persistence.JdbcSchema2InitialPlanRepository;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.persistence.Schema2RuntimeJdbcFixture;
import com.dndtool.persistence.Schema2SourceReadFixture;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2InitialCharacterPlanService.Plan;
import com.dndtool.service.Schema2InitialProficiencyRules.*;
import com.dndtool.service.Schema2SnapshotTransferService.Status;
import com.dndtool.testing.IsolatedJvmTest;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original author bytes, real preparation/planner/JDBC consumers, isolated process fault lifetimes. */
class Schema2InitializationPreparationServiceTest {
    private static final UUID OPERATION = UUID.fromString("01872103-0345-4abc-8def-0123456789ab");
    private static final Identity IDENTITY = new Identity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));
    private static final UUID CAMPAIGN = UUID.fromString("12345678-1234-4678-8234-123456789abc");
    private static final UUID CHARACTER = UUID.fromString("abcdef12-1234-4678-8234-123456789abc");
    private static final RuleIdentity RACE = rule("character.race", "race.human");
    private static final RuleIdentity SUBRACE = rule("character.subrace", "subrace.north");
    private static final RuleIdentity BACKGROUND = rule("character.background", "background.sailor");
    private static final RuleIdentity CLASS = rule("character.class", "class.scholar");
    private static final Request CHARACTER_REQUEST = new Request(new Sources(RACE, SUBRACE, BACKGROUND, CLASS), Map.of(
            slot(RACE, "extra_languages"), List.of(rule("character.language", "language.elvish")),
            slot(BACKGROUND, "trade_tools"), List.of(rule("character.tool", "tool.smith_tools"))));

    @Test void sourcePreparationAndAtomicSnapshotPlanReadbackPreserveRealGrantFacts() throws Exception {
        IsolatedJvmTest.run(Probe.class, "readback");
    }
    @Test void fullInitializationRereadsSourceAndReportsTheMissingBusinessClosureWithoutWrites() throws Exception {
        IsolatedJvmTest.run(Probe.class, "closed");
    }
    @Test void sameOperationWithAnotherCampaignConflictsAndExactReplayDoesNotWrite() throws Exception {
        IsolatedJvmTest.run(Probe.class, "replay");
    }
    @Test void forgedPlanFailsFreshSourceRebuildBeforeRuntimeAcquisition() throws Exception {
        IsolatedJvmTest.run(Probe.class, "forged");
    }
    @Test void everyPreparationWriteFailureRollsBackSnapshotPlanControlAndReceipt() throws Exception {
        for (String table : List.of("s2_initial_plan", "s2_initial_plan_slot", "s2_initial_plan_choice", "s2_initial_plan_grant"))
            for (int affected : new int[]{-1, 0, 2}) IsolatedJvmTest.run(Probe.class, "write", table, Integer.toString(affected));
    }
    @Test void independentlyReadCorruptPlansCannotCommitOrReplay() throws Exception {
        for (String point : List.of("beforeCommit", "retained")) IsolatedJvmTest.run(Probe.class, "corrupt", point);
    }
    @Test void unknownPreparationCommitResolvesTheCompleteImmutableIntentWithoutSourceOrWrites() throws Exception {
        for (String outcome : List.of("COMMITTED", "ROLLED_BACK")) IsolatedJvmTest.run(Probe.class, "unknown", outcome);
    }
    @Test void activeLegacyCampaignRejectsInternalPreparationTransferAtTheCommonGuard() throws Exception {
        IsolatedJvmTest.run(Probe.class, "legacy");
    }
    @Test void orphanPreparationLeavesFailClosedBeforeAnySnapshotWrite() throws Exception {
        for (String table : List.of("s2_initial_plan_slot", "s2_initial_plan_choice", "s2_initial_plan_grant"))
            IsolatedJvmTest.run(Probe.class, "orphan", table);
    }
    @Test void unknownIntentCanBeResolvedOnAnotherThreadWithoutRestoringWriteEligibility() throws Exception {
        IsolatedJvmTest.run(Probe.class, "crossThread");
    }
    @Test void fatalRollbackOrCloseErrorsCannotReplaceTheOriginalFatalBusinessFailure() throws Exception {
        for (String cleanup : List.of("rollback", "close")) IsolatedJvmTest.run(Probe.class, "fatalCleanup", cleanup);
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            var fixture = new Fixture();
            var prepared = fixture.preparations.prepare(fixture.request(CAMPAIGN));
            assertEquals(0, fixture.runtime.acquisitions);
            assertEquals(0, fixture.runtime.writes);
            assertEquals(1, fixture.source.commitCount());
            assertEquals(1, fixture.source.closeCount());
            switch (args[0]) {
                case "readback" -> {
                    var checked = fixture.preparations.prepare(new Schema2InitializationPreparationService.Request(
                            OPERATION, IDENTITY, CAMPAIGN, CHARACTER, fixture.source.selections(), prepared.rules(), CHARACTER_REQUEST));
                    fixture.runtime.beforeAcquire = () -> assertEquals(3, fixture.source.closeCount());
                    var accepted = fixture.transfers.transferPrepared(checked);
                    assertEquals(Status.COMMITTED, accepted.status(), accepted.code());
                    assertEquals(Schema2SnapshotTransferService.Kind.CAPTURE, accepted.intent().kind());
                    assertEquals(CAMPAIGN, accepted.intent().initialPlan().campaignId());
                    assertEquals(CHARACTER, accepted.intent().initialPlan().characterId());
                    assertEquals(IDENTITY, accepted.snapshot().identity());
                    // Independent stdlib oracle: schema2-grants/expected/initial-plan-fingerprint.py.
                    assertEquals("59dcfbf8cfb0c594400c196a09c5bb7556655099c5c4f9954017139678cacce7", accepted.intent().digest());
                    assertEquals(8, fixture.runtime.rows("s2_initial_plan_slot").size());
                    assertEquals(2, fixture.runtime.rows("s2_initial_plan_choice").size());
                    assertEquals(8, fixture.runtime.rows("s2_initial_plan_grant").size());
                    assertArrayEquals(Schema2GrantFixtures.resource("expected/final-rules.json"), accepted.snapshot().projection().finalRulesJson());
                    fixture.source.unavailable(true);
                    try (var session = BusinessWriteTransactions.openResolution(fixture.runtime.dataSource())) {
                        var read = new JdbcSchema2InitialPlanRepository().read(session.connection(), OPERATION, accepted.snapshot());
                        assertEquals(prepared.initialPlan(), read);
                        assertEquals(List.of(
                                fixed(BACKGROUND, "shared_language", GrantCategory.LANGUAGE, rule("character.language", "language.common"), false),
                                new GrantFact(slot(BACKGROUND, "trade_tools"), GrantCategory.TOOL, GrantKind.CHOICE, rule("character.tool", "tool.smith_tools"), false),
                                fixed(CLASS, "disabled_tool", GrantCategory.TOOL, rule("character.tool", "tool.herbalism"), true),
                                fixed(CLASS, "lost_language", GrantCategory.LANGUAGE, rule("character.language", "language.dwarvish"), true),
                                new GrantFact(slot(RACE, "extra_languages"), GrantCategory.LANGUAGE, GrantKind.CHOICE, rule("character.language", "language.elvish"), false),
                                fixed(RACE, "native_language", GrantCategory.LANGUAGE, rule("character.language", "language.common"), false),
                                new GrantFact(new EntryIdentity(RACE, "proficiency_grants", Schema2GrantFixtures.EXT, "extension_language"),
                                        GrantCategory.LANGUAGE, GrantKind.FIXED, new RuleIdentity(Schema2GrantFixtures.EXT, "character.language", "language.common"), false),
                                fixed(SUBRACE, "instrument", GrantCategory.TOOL, rule("character.tool", "tool.flute"), false)), read.character().grants());
                        session.rollback();
                    }
                    assertTrue(fixture.runtime.rows("campaign").isEmpty());
                    assertTrue(fixture.runtime.rows("character").isEmpty());
                }
                case "closed" -> {
                    var rejected = fixture.preparations.initialize(prepared);
                    assertEquals("S2_INITIALIZATION_INCOMPLETE", rejected.code());
                    assertEquals(EnumSet.allOf(Schema2InitializationPreparationService.Missing.class), EnumSet.copyOf(rejected.missing()));
                    assertEquals(2, fixture.source.acquisitionCount());
                    assertEquals(0, fixture.runtime.acquisitions);
                    assertEquals(0, fixture.runtime.writes);
                    fixture.source.unavailable(true);
                    assertThrows(java.sql.SQLException.class, () -> fixture.preparations.initialize(prepared));
                    assertEquals(0, fixture.runtime.acquisitions);
                }
                case "replay" -> {
                    var accepted = fixture.transfers.transferPrepared(prepared);
                    assertEquals(Status.COMMITTED, accepted.status(), accepted.code());
                    int writes = fixture.runtime.writes;
                    assertEquals("S2_SNAPSHOT_REPLAY", fixture.transfers.transferPrepared(prepared).code());
                    var other = fixture.preparations.prepare(fixture.request(UUID.randomUUID()));
                    assertEquals("S2_OPERATION_CONFLICT", fixture.transfers.transferPrepared(other).code());
                    assertEquals(writes, fixture.runtime.writes);
                    assertEquals(1, fixture.runtime.commits);
                }
                case "forged" -> {
                    var plan = prepared.initialPlan().character();
                    var grants = new ArrayList<>(plan.grants()); var first = grants.getFirst();
                    grants.set(0, new GrantFact(first.slot(), first.category(), first.kind(), first.target(), !first.suppressed()));
                    var forged = new InitialPlan(CAMPAIGN, CHARACTER,
                            new Plan(plan.identity(), plan.binding(), plan.sources(), plan.choices(), plan.slots(), grants));
                    var rejected = fixture.transfers.transfer(new Schema2SnapshotTransferService.Request(OPERATION, IDENTITY,
                            fixture.source.selections(), null, forged));
                    assertEquals("S2_INITIAL_PLAN_MISMATCH", rejected.code());
                    assertEquals(0, fixture.runtime.acquisitions);
                    assertEquals(0, fixture.runtime.writes);
                }
                case "write" -> {
                    var good = fixture.transfers.transferPrepared(prepared);
                    assertEquals(Status.COMMITTED, good.status(), good.code());
                    var writes = fixture.runtime.sql.stream().filter(sql -> sql.startsWith("INSERT INTO ") || sql.startsWith("UPDATE ")).toList();
                    int boundary = java.util.stream.IntStream.range(0, writes.size())
                            .filter(i -> writes.get(i).startsWith("INSERT INTO " + args[1] + " (")).findFirst().orElseThrow() + 1;
                    var failing = new Fixture(); var fresh = failing.preparations.prepare(failing.request(CAMPAIGN));
                    failing.runtime.failWrite = boundary; failing.runtime.affected = Integer.parseInt(args[2]);
                    var failed = failing.transfers.transferPrepared(fresh);
                    assertEquals(Status.ROLLED_BACK, failed.status(), failed.code());
                    assertNull(failed.snapshot()); assertEquals(0, failing.runtime.commits); assertEquals(1, failing.runtime.rollbacks);
                    assertEmpty(failing.runtime);
                }
                case "corrupt" -> {
                    if (args[1].equals("beforeCommit")) {
                        fixture.runtime.afterWrite = db -> {
                            if (!db.rows("s2_initial_plan_grant").isEmpty()) db.rows("s2_initial_plan_grant").getFirst().put("suppressed", 1);
                        };
                        var rejected = fixture.transfers.transferPrepared(prepared);
                        assertEquals(Status.ROLLED_BACK, rejected.status(), rejected.code());
                        assertEmpty(fixture.runtime);
                    } else {
                        var accepted = fixture.transfers.transferPrepared(prepared);
                        assertEquals(Status.COMMITTED, accepted.status(), accepted.code());
                        fixture.runtime.rows("s2_initial_plan").getFirst().put("campaign_id", bytes(UUID.randomUUID()));
                        fixture.runtime.saveCommitted(); fixture.source.unavailable(true);
                        int writes = fixture.runtime.writes;
                        assertEquals(Status.ROLLED_BACK, fixture.transfers.inspect(accepted.intent()).status());
                        assertEquals(writes, fixture.runtime.writes);
                    }
                }
                case "unknown" -> {
                    fixture.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                    var unknown = fixture.transfers.transferPrepared(prepared);
                    assertEquals(Status.UNKNOWN, unknown.status(), unknown.code()); assertNull(unknown.snapshot());
                    assertEquals(prepared.initialPlan(), unknown.intent().initialPlan());
                    fixture.source.unavailable(true); int writes = fixture.runtime.writes;
                    var settled = fixture.transfers.resolve(unknown.intent());
                    assertEquals(args[1].equals("COMMITTED") ? Status.RESOLVED_COMMITTED : Status.NOT_COMMITTED, settled.status(), settled.code());
                    assertEquals(writes, fixture.runtime.writes); assertEquals(2, fixture.source.acquisitionCount());
                    assertEquals("S2_TRANSFER_BLOCKED", fixture.transfers.transferPrepared(prepared).code());
                }
                case "legacy" -> {
                    fixture.runtime.legacyActive = true;
                    var rejected = fixture.transfers.transferPrepared(prepared);
                    assertEquals("S2_RUNTIME_REMAINDER", rejected.code()); assertNull(rejected.snapshot());
                    assertEquals(0, fixture.runtime.writes);
                    assertEquals(BusinessWriteTransactions.LOCK_SQL, fixture.runtime.sql.getFirst());
                }
                case "orphan" -> {
                    fixture.runtime.rows(args[1]).add(new LinkedHashMap<>(Map.of("operation_id", bytes(UUID.randomUUID()))));
                    fixture.runtime.saveCommitted();
                    var rejected = fixture.transfers.transferPrepared(prepared);
                    assertEquals(Status.ROLLED_BACK, rejected.status(), rejected.code());
                    assertEquals("S2_INITIAL_PLAN_CORRUPT", rejected.code());
                    assertEquals(0, fixture.runtime.writes);
                }
                case "crossThread" -> {
                    fixture.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.COMMITTED;
                    var unknown = fixture.transfers.transferPrepared(prepared);
                    assertEquals(Status.UNKNOWN, unknown.status(), unknown.code());
                    fixture.source.unavailable(true);
                    var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
                    try {
                        // Allow cold child-JVM readback; this timeout does not define product latency.
                        var settled = executor.submit(() -> fixture.transfers.resolve(unknown.intent())).get(20, java.util.concurrent.TimeUnit.SECONDS);
                        assertEquals(Status.RESOLVED_COMMITTED, settled.status(), settled.code());
                        assertEquals(prepared.initialPlan(), settled.intent().initialPlan());
                    } finally { executor.shutdownNow(); }
                    assertTrue(BusinessWriteTransactions.isBlocked());
                    assertEquals("S2_TRANSFER_BLOCKED", fixture.transfers.transferPrepared(prepared).code());
                }
                case "fatalCleanup" -> {
                    var original = new AssertionError("Fatal write failure");
                    var cleanup = new AssertionError("Fatal cleanup failure");
                    fixture.runtime.afterWrite = db -> { throw original; };
                    if (args[1].equals("rollback")) fixture.runtime.rollbackError = cleanup;
                    else fixture.runtime.closeError = cleanup;
                    assertSame(original, assertThrows(AssertionError.class, () -> fixture.transfers.transferPrepared(prepared)));
                    assertTrue(Arrays.asList(original.getSuppressed()).contains(cleanup));
                    assertEquals(1, fixture.runtime.rollbacks);
                    assertTrue(BusinessWriteTransactions.isBlocked());
                    assertEquals("S2_TRANSFER_BLOCKED", fixture.transfers.transferPrepared(prepared).code());
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }

    private static RuleIdentity rule(String type, String key) { return new RuleIdentity(Schema2GrantFixtures.BASE, type, key); }
    private static EntryIdentity slot(RuleIdentity source, String key) { return new EntryIdentity(source, "proficiency_grants", Schema2GrantFixtures.BASE, key); }
    private static GrantFact fixed(RuleIdentity source, String key, GrantCategory category, RuleIdentity target, boolean suppressed) {
        return new GrantFact(slot(source, key), category, GrantKind.FIXED, target, suppressed);
    }
    private static byte[] bytes(UUID id) { return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    private static void assertEmpty(Schema2RuntimeJdbcFixture db) {
        db.tables.forEach((table, rows) -> {
            if (!table.equals("s2_runtime_control")) assertTrue(rows.isEmpty(), table);
        });
        assertEquals(0L, ((Number) db.rows("s2_runtime_control").getFirst().get("row_version")).longValue());
    }
    private static final class Fixture {
        final Schema2SourceReadFixture source = new Schema2SourceReadFixture(Schema2GrantFixtures.fixedArtifacts());
        final Schema2RuntimeJdbcFixture runtime = new Schema2RuntimeJdbcFixture();
        final Schema2InitializationPreparationService preparations = new Schema2InitializationPreparationService(source.dataSource());
        final Schema2SnapshotTransferService transfers = new Schema2SnapshotTransferService(source.dataSource(), runtime.dataSource());
        Schema2InitializationPreparationService.Request request(UUID campaign) {
            return new Schema2InitializationPreparationService.Request(OPERATION, IDENTITY, campaign, CHARACTER, source.selections(), null, CHARACTER_REQUEST);
        }
    }
}
