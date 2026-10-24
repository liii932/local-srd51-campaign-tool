package com.dndtool.service;

import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CharacterRuntimeService.*;
import com.dndtool.testing.IsolatedJvmTest;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Complete typed source/snapshot/command/history consumers inside the bounded INTERNAL run. */
class Schema2CharacterAdvancementAndRestServiceTest {
    private static final Identity IDENTITY = new Identity(UUID.fromString("10912233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("f0eeddcc-bbaa-4988-8766-554433221100"));
    private static final UUID PREPARATION = UUID.fromString("10872103-0345-4abc-8def-0123456789ab");
    private static final UUID CAMPAIGN = UUID.fromString("22345678-1234-4678-8234-123456789abc");
    private static final UUID CHARACTER = UUID.fromString("bbcdef12-1234-4678-8234-123456789abc");

    @Test void completeGrowthChoicesReachRealStateAndRestoreWithoutRepeatingBenefits() throws Exception {
        Fixture fixture = new Fixture(); fixture.initialize(); fixture.source.unavailable(true);
        assertEquals(11, fixture.state.hitPoints());
        fixture.commit(advance(CLASS, GrowthChoices.EMPTY));
        fixture.commit(advance(CLASS, new GrowthChoices(SUBCLASS, Map.of(), null, Map.of())));
        fixture.commit(advance(CLASS, new GrowthChoices(null, Map.of("ability.constitution", 2), null, Map.of())));
        assertEquals(36, fixture.state.hitPoints()); assertEquals(15, fixture.state.abilities().get("ability.constitution"));
        fixture.commit(advance(ALT_CLASS, fullMulticlassChoices()));
        assertEquals(5, fixture.state.level()); assertEquals(3, fixture.state.proficiencyBonus());
        assertEquals(List.of(new HitDicePool(6, 1, 1), new HitDicePool(10, 4, 4)), fixture.state.hitDicePools());
        assertEquals(2, fixture.state.subclasses().size());
        assertTrue(fixture.state.features().stream().anyMatch(value -> value.feature().ruleKey().equals("feature.blocked")));
        assertFalse(fixture.state.features().stream().anyMatch(value -> value.feature().ruleKey().equals("feature.option")));
        assertTrue(fixture.state.proficiencies().stream().anyMatch(value -> value.target().equals(WEAPON)));
        assertTrue(fixture.state.proficiencies().stream().anyMatch(value -> value.target().equals(ALT_WEAPON)));
        fixture.commit(spend(LATER, 1)); fixture.commit(spend(EXT_ENERGY, 1));
        fixture.commit(advance(CLASS, GrowthChoices.EMPTY));
        fixture.commit(advance(CLASS, new GrowthChoices(null, Map.of("ability.charisma", 2), null, Map.of())));
        assertEquals(3, resource(fixture.state, LATER).maximum()); assertEquals(2, resource(fixture.state, LATER).current());
        assertEquals(3, resource(fixture.state, EXT_ENERGY).maximum()); assertEquals(2, resource(fixture.state, EXT_ENERGY).current());
        fixture.commit(advance(ALT_CLASS, GrowthChoices.EMPTY)); fixture.commit(advance(ALT_CLASS, GrowthChoices.EMPTY));
        Request featRequest = fixture.request(advance(ALT_CLASS, new GrowthChoices(null, Map.of(), GRAPPLER, Map.of())));
        Result gained = fixture.execute(featRequest); committed(gained);
        assertEquals(10, fixture.state.level()); assertEquals(4, fixture.state.proficiencyBonus());
        assertEquals(1, fixture.state.feats().size()); assertEquals(GRAPPLER, fixture.state.feats().getFirst().feat());
        assertEquals(76, fixture.state.hitPoints());
        int writes = fixture.runtime.writes;
        assertEquals(Status.REPLAYED, fixture.service.execute(featRequest).status());
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
        History history = fixture.service.read(CHARACTER);
        Fixture restored = new Fixture(); restored.source.unavailable(true);
        committed(restored.service.restore(history)); assertEquals(history, restored.service.read(CHARACTER));
        assertEquals(0, restored.rolls);
    }

    @Test void missingChoicesScopesAndStaleRequestsRejectBeforeRandomnessOrWrites() throws Exception {
        Fixture fixture = new Fixture(); fixture.initialize();
        fixture.commit(advance(CLASS, GrowthChoices.EMPTY));
        int writes = fixture.runtime.writes;
        Command missing = new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL", CLASS, GrowthChoices.EMPTY);
        assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(missing)).status());
        assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(new Command("STATISTICS_ADVANCED", null, 0, null, "SERVER_ROLL"))).status());
        assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(new Command("RESOURCES_RECOVERED", null, 0, "LONG_REST", null))).status());
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
        Request selected = fixture.request(new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL", CLASS,
                new GrowthChoices(SUBCLASS, Map.of(), null, Map.of())));
        committed(fixture.execute(selected)); assertEquals(1, fixture.rolls);
        writes = fixture.runtime.writes;
        Request stale = new Request(UUID.randomUUID(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, fixture.binding,
                selected.expectedVersion(), selected.command(), Map.of());
        assertEquals("S2_CHARACTER_STALE_VERSION", fixture.service.execute(stale).code());
        assertEquals(writes, fixture.runtime.writes); assertEquals(1, fixture.rolls);
    }

    @Test void realShortAndLongRestPersistTimeDiceRecoveryIntervalAndDirectHistory() throws Exception {
        Fixture fixture = new Fixture(); fixture.initialize(); fixture.commit(advance(ALT_CLASS, fullMulticlassChoices()));
        fixture.commit(spend(ENERGY, 1)); fixture.commit(spend(EXT_ENERGY, 1));
        fixture.commit(start("SHORT_REST")); fixture.commit(time("LIGHT", 60)); fixture.commit(finish(Map.of()));
        assertEquals(2, resource(fixture.state, ENERGY).current());
        assertEquals(resource(fixture.state, EXT_ENERGY).maximum(), resource(fixture.state, EXT_ENERGY).current());
        Request firstDie = fixture.request(die(10)); committed(fixture.execute(firstDie));
        fixture.commit(die(6)); assertEquals(2, fixture.rolls); assertEquals(0, fixture.state.hitDice());
        assertEquals(fixture.state.maximumHitPoints(), fixture.state.hitPoints());
        int writes = fixture.runtime.writes;
        assertEquals("S2_REST_HIT_DIE_UNAVAILABLE", fixture.service.execute(fixture.request(die(6))).code());
        assertEquals(Status.REPLAYED, fixture.service.execute(firstDie).status());
        assertEquals(writes, fixture.runtime.writes); assertEquals(2, fixture.rolls);
        fixture.commit(end()); fixture.commit(start("LONG_REST"));
        fixture.commit(time("SLEEP", 360)); fixture.commit(time("LIGHT", 120));
        Request firstLong = fixture.request(finish(Map.of(10, 1))); committed(fixture.execute(firstLong));
        assertEquals(540, fixture.state.rest().minute()); assertEquals(540L, fixture.state.rest().lastLongRestEndMinute());
        assertEquals(1, fixture.state.hitDice()); assertEquals(3, resource(fixture.state, ENERGY).current());
        fixture.commit(start("LONG_REST")); fixture.commit(time("SLEEP", 480));
        writes = fixture.runtime.writes;
        assertEquals("S2_REST_LONG_INTERVAL", fixture.service.execute(fixture.request(finish(Map.of(6, 1)))).code());
        assertEquals(writes, fixture.runtime.writes); assertEquals(1, fixture.state.hitDice());
        fixture.commit(time("SLEEP", 960)); fixture.commit(finish(Map.of(6, 1)));
        assertEquals(1980, fixture.state.rest().minute()); assertEquals(2, fixture.state.hitDice());
        writes = fixture.runtime.writes;
        assertEquals(Status.REPLAYED, fixture.service.execute(firstLong).status()); assertEquals(writes, fixture.runtime.writes);
        History history = fixture.service.read(CHARACTER);
        Fixture restored = new Fixture(); restored.source.unavailable(true);
        committed(restored.service.restore(history)); assertEquals(history, restored.service.read(CHARACTER));
        assertEquals(0, restored.rolls); assertEquals(Status.REPLAYED, restored.service.inspectRestore(history).status());
    }

    @Test void restDurationActivityAndAllocationAreValidatedBeforeBenefits() throws Exception {
        Fixture fixture = new Fixture(); fixture.initialize();
        fixture.commit(start("SHORT_REST")); fixture.commit(time("LIGHT", 59));
        int writes = fixture.runtime.writes;
        assertEquals("S2_REST_REQUIREMENTS", fixture.service.execute(fixture.request(finish(Map.of()))).code());
        assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(advance(CLASS, GrowthChoices.EMPTY))).status());
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
        fixture.commit(time("STRENUOUS", 1)); fixture.commit(time("LIGHT", 60));
        assertEquals("S2_REST_REQUIREMENTS", fixture.service.execute(fixture.request(finish(Map.of()))).code());
        fixture.commit(end()); fixture.commit(start("LONG_REST"));
        fixture.commit(time("SLEEP", 480)); fixture.commit(time("STRENUOUS", 30)); fixture.commit(time("STRENUOUS", 30));
        assertEquals("S2_REST_REQUIREMENTS", fixture.service.execute(fixture.request(finish(Map.of()))).code());
        fixture.commit(end()); fixture.commit(start("LONG_REST")); fixture.commit(time("SLEEP", 480));
        writes = fixture.runtime.writes;
        assertEquals("S2_REST_HIT_DICE_CHOICE", fixture.service.execute(fixture.request(finish(Map.of(10, 1)))).code());
        assertEquals(writes, fixture.runtime.writes); fixture.commit(finish(Map.of()));
        assertEquals(0, fixture.rolls);
    }

    @Test void healingOwnerCapsAtMaximumAndRejectsInvalidHealthInputs() {
        assertEquals(new Schema2CharacterHealthRules.Healing(3, 20, 8, 8, 11), Schema2CharacterHealthRules.heal(3, 20, 8));
        assertEquals(20, Schema2CharacterHealthRules.heal(18, 20, 30).after());
        assertEquals(0, Schema2CharacterHealthRules.heal(20, 20, 6).applied());
        assertThrows(IllegalArgumentException.class, () -> Schema2CharacterHealthRules.heal(-1, 20, 3));
        assertThrows(IllegalArgumentException.class, () -> Schema2CharacterHealthRules.heal(21, 20, 3));
        assertThrows(IllegalArgumentException.class, () -> Schema2CharacterHealthRules.heal(10, 20, -1));
    }

    @Test void acquiredUnsupportedResourceBlocksRestBeforeAnyWriteOrRandomDraw() {
        Fixture fixture = new Fixture(true); fixture.initialize(); int writes = fixture.runtime.writes;
        Result result = fixture.service.execute(fixture.request(start("SHORT_REST")));
        assertEquals("S2_REST_RESOURCE_UNSUPPORTED", result.code()); assertEquals(Status.REJECTED, result.status());
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
        assertEquals(RestState.INITIAL, fixture.state.rest()); assertFalse(BusinessWriteTransactions.isBlocked());
    }

    @Test void failedLongRestWriteRollsBackTimeBenefitsHealthDiceAndResources() throws Exception {
        IsolatedJvmTest.run(FaultProbe.class, "rollback");
    }

    @Test void unknownHitDieCommitIsOnlyResolvedByReadingOriginalFacts() throws Exception {
        for (String outcome : List.of("COMMITTED", "ROLLED_BACK")) IsolatedJvmTest.run(FaultProbe.class, "unknown", outcome);
    }

    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            Fixture fixture = new Fixture(); fixture.initialize();
            fixture.commit(spend(ENERGY, 1)); fixture.commit(start("SHORT_REST"));
            fixture.commit(time("LIGHT", 60)); fixture.commit(finish(Map.of()));
            if (args[0].equals("unknown")) {
                Request request = fixture.request(die(10));
                fixture.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                Result result = fixture.service.execute(request);
                assertEquals(Status.UNKNOWN, result.status(), result.code()); assertNull(result.event());
                assertEquals(1, fixture.rolls); fixture.source.unavailable(true); int writes = fixture.runtime.writes;
                Result inspected = fixture.service.inspect(request);
                assertEquals(args[1].equals("COMMITTED") ? Status.REPLAYED : Status.NOT_COMMITTED, inspected.status(), inspected.code());
                assertEquals(writes, fixture.runtime.writes); assertEquals(1, fixture.rolls);
                assertTrue(BusinessWriteTransactions.isBlocked());
            } else {
                fixture.commit(die(10)); fixture.commit(end()); fixture.commit(start("LONG_REST")); fixture.commit(time("SLEEP", 480));
                History before = fixture.service.read(CHARACTER);
                fixture.runtime.afterWrite = runtime -> {
                    if (!runtime.rows("s2_character_command_hit_die").isEmpty()) throw new IllegalStateException("after recovered dice choice");
                };
                Result result = fixture.service.execute(fixture.request(finish(Map.of(10, 1))));
                assertEquals(Status.REJECTED, result.status(), result.code());
                assertEquals(before, fixture.service.read(CHARACTER));
                assertEquals(1, fixture.rolls); assertTrue(fixture.runtime.rows("s2_character_command_hit_die").isEmpty());
            }
        }
    }

    private static Command advance(RuleIdentity target, GrowthChoices choices) {
        return new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", target, choices);
    }
    private static Command spend(RuleIdentity resource, int amount) { return new Command("RESOURCE_SPENT", resource, amount, null, null); }
    private static Command rest(String kind, RestInput input) { return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, input); }
    private static Command start(String kind) { return rest("REST_STARTED", new RestInput(kind, null, 0, 0, Map.of())); }
    private static Command time(String activity, int minutes) { return rest("TIME_ADVANCED", new RestInput(null, activity, minutes, 0, Map.of())); }
    private static Command finish(Map<Integer, Integer> dice) { return rest("REST_COMPLETED", new RestInput(null, null, 0, 0, dice)); }
    private static Command die(int sides) { return rest("HIT_DIE_SPENT", new RestInput(null, null, 0, sides, Map.of())); }
    private static Command end() { return rest("REST_ENDED", new RestInput(null, null, 0, 0, Map.of())); }
    private static ResourceState resource(State state, RuleIdentity identity) {
        return state.resources().stream().filter(value -> value.identity().equals(identity)).findFirst().orElseThrow();
    }
    private static void committed(Result result) { assertEquals(Status.COMMITTED, result.status(), result.code()); }

    private static final class Fixture {
        final Schema2SourceReadFixture source;
        final Schema2RuntimeJdbcFixture runtime = new Schema2RuntimeJdbcFixture();
        final Schema2RulesBinding binding;
        final Schema2CharacterRuntimeService service;
        State state; int rolls;
        Fixture() { this(false); }
        Fixture(boolean acquiredBlockedResource) {
            var files = fullPackages();
            if (acquiredBlockedResource) {
                var definitions = com.dndtool.module.composition.Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
                int changed = 0;
                for (var value : definitions) {
                    var definition = value.getAsJsonObject();
                    if (definition.getAsJsonObject("identity").get("rule_key").getAsString().equals("resource.blocked")) {
                        definition.addProperty("maximum_profile", "1-20:1"); changed++;
                    }
                }
                assertEquals(1, changed);
                files.getFirst().put("rules.json", definitions.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            var artifacts = com.dndtool.module.composition.Schema2GrantFixtures.artifacts(files);
            var material = com.dndtool.module.composition.ProductionSchema2Projection.compose(artifacts);
            source = new Schema2SourceReadFixture(artifacts); binding = Schema2RulesBinding.from(material);
            var initial = fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER);
            var result = new Schema2SnapshotTransferService(source.dataSource(), runtime.dataSource()).transfer(
                    new Schema2SnapshotTransferService.Request(PREPARATION, IDENTITY, source.selections(), null, initial));
            assertEquals(Schema2SnapshotTransferService.Status.COMMITTED, result.status(), result.code());
            service = new Schema2CharacterRuntimeService(runtime.dataSource(), sides -> { rolls++; return Math.min(5, sides); });
        }
        void initialize() { commit(new Command("INITIALIZED", null, 0, null, null, null, fullInitialChoices())); }
        Request request(Command command) {
            return new Request(UUID.randomUUID(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, binding, state == null ? -1 : state.version(),
                    command, state == null ? fullAbilities() : Map.of());
        }
        Result execute(Request request) {
            Result result = service.execute(request); if (result.status() == Status.COMMITTED) state = result.event().after(); return result;
        }
        void commit(Command command) { committed(execute(request(command))); }
    }
}
