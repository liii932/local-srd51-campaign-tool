package com.dndtool.service;

import com.dndtool.module.composition.Schema2ProgressionFixtures;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CharacterRuntimeService.*;
import com.dndtool.testing.IsolatedJvmTest;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real author acceptance, snapshot/plan transfer and command JDBC consumers; no MySQL claim. */
class Schema2CharacterRuntimeServiceTest {
    private static final UUID PREPARATION = UUID.fromString("01872103-0345-4abc-8def-0123456789ab");
    private static final Identity IDENTITY = new Identity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));
    private static final UUID CAMPAIGN = UUID.fromString("12345678-1234-4678-8234-123456789abc");
    private static final UUID CHARACTER = UUID.fromString("abcdef12-1234-4678-8234-123456789abc");
    private static final Command INITIAL = new Command("INITIALIZED", null, 0, null, null);
    private static final Command ROLL = new Command("STATISTICS_ADVANCED", null, 0, null, "SERVER_ROLL");

    @Test void originalAuthorRulesDriveStateResourceGrowthAndImmutableReplayAfterSourceLoss() throws Exception {
        Probe.main(new String[]{"commands"});
    }
    @Test void allRejectedRequestsAndCapacityAreValidatedBeforeRandomnessOrWrites() throws Exception {
        Probe.main(new String[]{"rejected"});
    }
    @Test void initializationWriteFailuresLeaveNoPartialCharacterHistory() throws Exception {
        for (int write = 1; write <= 5; write++) IsolatedJvmTest.run(Probe.class, "failure", Integer.toString(write));
    }
    @Test void corruptPersistedFactsBlockBeforeRandomnessOrAdditionalWrites() throws Exception {
        IsolatedJvmTest.run(Probe.class, "corrupt");
    }
    @Test void directRestorePreservesAllStateAndCommandsWithoutRandomnessOrDuplicatedBenefits() throws Exception {
        Probe.main(new String[]{"restore"});
    }
    @Test void lostCommitResponseAllowsOnlyReadOnlyResolutionAndPreservesWriteFreeze() throws Exception {
        for (String outcome : List.of("COMMITTED", "ROLLED_BACK")) IsolatedJvmTest.run(Probe.class, "unknown", outcome);
    }
    @Test void commonGuardPreventsLegacyAndPartitionCoexistence() throws Exception {
        for (String mode : List.of("legacy", "partition")) IsolatedJvmTest.run(Probe.class, "guard", mode);
    }
    @Test void fatalCleanupPreservesOriginalFailureAndSharedWriteFreeze() throws Exception {
        IsolatedJvmTest.run(Probe.class, "fatal");
    }
    @Test void completeRestAndArbitraryCommandFieldsCannotReachTheStatisticsConsumer() {
        assertThrows(IllegalArgumentException.class, () -> new Command("LONG_REST", null, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Command("STATISTICS_ADVANCED", ENERGY, 0, null, "SERVER_ROLL"));
        assertThrows(IllegalArgumentException.class, () -> new Command("RESOURCE_SPENT", ENERGY, 1, "LONG_REST", null));
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            Fixture fixture = new Fixture();
            switch (args[0]) {
                case "commands" -> {
                    Request initialize = fixture.request(-1, INITIAL);
                    Result initialized = fixture.service.execute(initialize); committed(initialized);
                    assertEquals(11, initialized.event().after().hitPoints());
                    assertEquals(0, fixture.rolls);
                    fixture.source.unavailable(true);
                    committed(fixture.service.execute(fixture.request(0, spend(ENERGY, 1))));
                    committed(fixture.service.execute(fixture.request(1, spend(EXT_ENERGY, 1))));
                    Request advance = fixture.request(2, ROLL); Result grown = fixture.service.execute(advance); committed(grown);
                    assertEquals(1, fixture.rolls); assertEquals(17, grown.event().after().hitPoints());
                    assertEquals(2, resource(grown.event().after(), ENERGY).current());
                    assertEquals(1, resource(grown.event().after(), EXT_ENERGY).current());
                    int writes = fixture.runtime.writes;
                    Result replay = fixture.service.execute(advance);
                    assertEquals(Status.REPLAYED, replay.status(), replay.code()); assertEquals(grown.event(), replay.event());
                    assertEquals(writes, fixture.runtime.writes); assertEquals(1, fixture.rolls);
                    committed(fixture.service.execute(fixture.request(3, spend(EXT_ENERGY, 1))));
                    Result shortRecovery = fixture.service.execute(fixture.request(4, recover("SHORT_REST"))); committed(shortRecovery);
                    assertEquals(2, resource(shortRecovery.event().after(), ENERGY).current());
                    assertEquals(2, resource(shortRecovery.event().after(), EXT_ENERGY).current());
                    Result longRecovery = fixture.service.execute(fixture.request(5, recover("LONG_REST"))); committed(longRecovery);
                    assertEquals(3, resource(longRecovery.event().after(), ENERGY).current());
                    assertEquals(17, longRecovery.event().after().hitPoints()); assertEquals(2, longRecovery.event().after().hitDice());
                    assertEquals(7, fixture.service.read(CHARACTER).events().size());
                    assertEquals(Status.REPLAYED, fixture.service.inspect(initialize).status());
                    assertTrue(fixture.runtime.rows("campaign").isEmpty());
                }
                case "rejected" -> {
                    Map<String, Integer> arbitrary = new HashMap<>(abilities()); arbitrary.put("ability.strength", 30);
                    int before = fixture.runtime.writes;
                    Request arbitraryRequest = new Request(UUID.randomUUID(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION,
                            fixture.binding, -1, INITIAL, arbitrary);
                    assertEquals("S2_GROWTH_STANDARD_ARRAY", fixture.service.execute(arbitraryRequest).code());
                    assertEquals(before, fixture.runtime.writes); assertEquals(0, fixture.rolls);
                    committed(fixture.service.execute(fixture.request(-1, INITIAL)));
                    Request spend = fixture.request(0, spend(ENERGY, 2)); committed(fixture.service.execute(spend));
                    before = fixture.runtime.writes;
                    assertEquals("S2_CHARACTER_STALE_VERSION", fixture.service.execute(fixture.request(0, ROLL)).code());
                    assertEquals("S2_RESOURCE_INSUFFICIENT", fixture.service.execute(fixture.request(1, spend(ENERGY, 2))).code());
                    Request conflict = new Request(spend.operationId(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, fixture.binding,
                            0, spend(ENERGY, 1), Map.of());
                    assertEquals("S2_CHARACTER_OPERATION_CONFLICT", fixture.service.execute(conflict).code());
                    assertEquals(before, fixture.runtime.writes); assertEquals(0, fixture.rolls);
                    committed(fixture.service.execute(fixture.request(1, ROLL)));
                    committed(fixture.service.execute(fixture.request(2, ROLL)));
                    before = fixture.runtime.writes;
                    assertEquals("S2_GROWTH_LEVEL_UNSUPPORTED", fixture.service.execute(fixture.request(3, ROLL)).code());
                    assertEquals(before, fixture.runtime.writes); assertEquals(2, fixture.rolls);
                }
                case "failure" -> {
                    int before = fixture.runtime.writes;
                    fixture.runtime.failWrite = before + Integer.parseInt(args[1]);
                    Result result = fixture.service.execute(fixture.request(-1, INITIAL));
                    assertEquals(Status.REJECTED, result.status(), result.code());
                    assertTrue(fixture.runtime.rows("s2_character_runtime").isEmpty());
                    assertTrue(fixture.runtime.rows("s2_character_event").isEmpty());
                    assertTrue(fixture.runtime.rows("s2_character_event_state").isEmpty());
                    assertTrue(fixture.runtime.rows("s2_character_event_resource").isEmpty());
                    assertEquals(1, fixture.runtime.commits); assertEquals(0, fixture.rolls);
                }
                case "corrupt" -> {
                    committed(fixture.service.execute(fixture.request(-1, INITIAL)));
                    fixture.runtime.rows("s2_character_event_resource").getFirst().put("current_value", 999);
                    fixture.runtime.saveCommitted(); int writes = fixture.runtime.writes;
                    Result result = fixture.service.execute(fixture.request(0, ROLL));
                    assertEquals(Status.REJECTED, result.status(), result.code());
                    assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
                    assertTrue(BusinessWriteTransactions.isBlocked());
                }
                case "restore" -> {
                    committed(fixture.service.execute(fixture.request(-1, INITIAL)));
                    committed(fixture.service.execute(fixture.request(0, spend(ENERGY, 1))));
                    committed(fixture.service.execute(fixture.request(1, ROLL)));
                    History history = fixture.service.read(CHARACTER);
                    Fixture target = new Fixture(); target.source.unavailable(true);
                    Result restored = target.service.restore(history); committed(restored);
                    assertEquals(history, target.service.read(CHARACTER)); assertEquals(0, target.rolls);
                    int writes = target.runtime.writes;
                    assertEquals(Status.REPLAYED, target.service.restore(history).status());
                    assertEquals(Status.REPLAYED, target.service.inspectRestore(history).status());
                    assertEquals(writes, target.runtime.writes); assertEquals(0, target.rolls);
                    var last = history.events().getLast();
                    var changed = new Event(last.operationId(), "0".repeat(64), last.command(), last.hitDieRoll(), last.before(), last.after());
                    var badEvents = new ArrayList<>(history.events()); badEvents.set(badEvents.size() - 1, changed);
                    assertEquals(Status.REJECTED, target.service.restore(new History(history.current(), badEvents)).status());
                    assertEquals(writes, target.runtime.writes);
                    assertFalse(BusinessWriteTransactions.isBlocked());
                    committed(target.service.execute(target.request(history.current().version(), recover("LONG_REST"))));
                }
                case "unknown" -> {
                    committed(fixture.service.execute(fixture.request(-1, INITIAL)));
                    Request request = fixture.request(0, ROLL);
                    fixture.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                    Result unknown = fixture.service.execute(request);
                    assertEquals(Status.UNKNOWN, unknown.status(), unknown.code()); assertNull(unknown.event());
                    fixture.source.unavailable(true); int writes = fixture.runtime.writes;
                    Result inspection = fixture.service.inspect(request);
                    assertEquals(args[1].equals("COMMITTED") ? Status.REPLAYED : Status.NOT_COMMITTED, inspection.status(), inspection.code());
                    assertEquals(writes, fixture.runtime.writes); assertTrue(BusinessWriteTransactions.isBlocked());
                    assertEquals(1, fixture.rolls);
                    assertEquals(Status.REJECTED, fixture.service.execute(request).status());
                    if (args[1].equals("ROLLED_BACK")) assertEquals(Status.UNKNOWN,
                            new Schema2CharacterRuntimeService(fixture.runtime.dataSource()).inspect(request).status());
                }
                case "guard" -> {
                    fixture.runtime.legacyActive = args[1].equals("legacy");
                    fixture.runtime.partitionRemainder = args[1].equals("partition");
                    int writes = fixture.runtime.writes;
                    assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(-1, INITIAL)).status());
                    assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
                }
                case "fatal" -> {
                    var failure = new AssertionError("write failed"); var cleanup = new AssertionError("cleanup failed");
                    fixture.runtime.afterWrite = ignored -> { throw failure; }; fixture.runtime.rollbackError = cleanup;
                    assertSame(failure, assertThrows(AssertionError.class, () -> fixture.service.execute(fixture.request(-1, INITIAL))));
                    assertTrue(Arrays.asList(failure.getSuppressed()).contains(cleanup));
                    assertTrue(BusinessWriteTransactions.isBlocked());
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }

    private static Command spend(com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity resource, int amount) {
        return new Command("RESOURCE_SPENT", resource, amount, null, null);
    }
    private static Command recover(String trigger) { return new Command("RESOURCES_RECOVERED", null, 0, trigger, null); }
    private static ResourceState resource(State state, com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity identity) {
        return state.resources().stream().filter(resource -> resource.identity().equals(identity)).findFirst().orElseThrow();
    }
    private static void committed(Result result) { assertEquals(Status.COMMITTED, result.status(), result.code()); }
    private static final class Fixture {
        final Schema2SourceReadFixture source = new Schema2SourceReadFixture(artifacts());
        final Schema2RuntimeJdbcFixture runtime = new Schema2RuntimeJdbcFixture();
        final Schema2RulesBinding binding = Schema2RulesBinding.from(projection());
        final Schema2CharacterRuntimeService service;
        int rolls;
        Fixture() {
            var initial = initialPlan(projection(), IDENTITY, CAMPAIGN, CHARACTER);
            var result = new Schema2SnapshotTransferService(source.dataSource(), runtime.dataSource()).transfer(
                    new Schema2SnapshotTransferService.Request(PREPARATION, IDENTITY, source.selections(), null, initial));
            assertEquals(Schema2SnapshotTransferService.Status.COMMITTED, result.status(), result.code());
            service = new Schema2CharacterRuntimeService(runtime.dataSource(), sides -> { rolls++; assertEquals(10, sides); return 5; });
        }
        Request request(long version, Command command) {
            return new Request(UUID.randomUUID(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, binding, version,
                    command, command.kind().equals("INITIALIZED") ? abilities() : Map.of());
        }
    }
}
