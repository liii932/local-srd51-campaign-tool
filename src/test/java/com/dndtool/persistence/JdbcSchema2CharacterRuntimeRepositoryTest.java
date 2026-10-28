package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CharacterRuntimeRepositoryTest {
    private static final JdbcSchema2CharacterRuntimeRepository REPOSITORY = new JdbcSchema2CharacterRuntimeRepository();
    private static final Identity IDENTITY = Identity.random();
    private static final UUID CAMPAIGN = UUID.randomUUID(), CHARACTER = UUID.randomUUID(), PREPARATION = UUID.randomUUID();
    private static final RuleIdentity CLASS = new RuleIdentity("example", "character.class", "class.guard");
    private static final RuleIdentity RESOURCE = new RuleIdentity("example", "character.resource", "resource.energy");
    private static final Map<String, Integer> ABILITIES = Map.of("ability.strength", 15, "ability.dexterity", 14, "ability.constitution", 13, "ability.intelligence", 12, "ability.wisdom", 10, "ability.charisma", 8);

    @Test void writesRequireSharedAdmissionBeforeAnyBusinessSql() {
        BusinessWriteTestSupport.assertRawConnectionRejected(c -> REPOSITORY.append(c, history().events().getFirst()));
        BusinessWriteTestSupport.assertRawConnectionRejected(c -> REPOSITORY.restore(c, history()));
    }
    @Test void commandUnionRejectsMalformedUnusedFieldsBeforeAnyTransactionOrRandomness() {
        for (Runnable invalid : List.<Runnable>of(
                () -> new Command(null, null, 0, null, null),
                () -> new Command("UNKNOWN", null, 0, null, null),
                () -> new Command("INITIALIZED", RESOURCE, 0, null, null),
                () -> new Command("STATISTICS_ADVANCED", null, 0, null, null),
                () -> new Command("STATISTICS_ADVANCED", null, 1, null, "SERVER_ROLL"),
                () -> new Command("RESOURCE_SPENT", RESOURCE, 1000001, null, null),
                () -> new Command("RESOURCE_SPENT", new RuleIdentity("bad module", "character.resource", "resource.energy"), 1, null, null),
                () -> new Command("RESOURCE_SPENT", CLASS, 1, null, null),
                () -> new Command("RESOURCE_SPENT", new RuleIdentity("example", "character.resource", "resource..energy"), 1, null, null),
                () -> new Command("RESOURCES_RECOVERED", null, 0, null, null),
                () -> new Command("RESOURCES_RECOVERED", null, 0, "LONG_REST", "SERVER_ROLL")))
            assertEquals("S2_CHARACTER_COMMAND", assertThrows(IllegalArgumentException.class, invalid::run).getMessage());
    }
    @Test void actualRowsPreserveFullBeforeAfterCommandsAndGlobalOperationIdentity() throws Exception {
        var fixture = seeded(); History expected = history();
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
            for (Event event : expected.events()) REPOSITORY.append(transaction.connection(), event);
            transaction.commit();
        }
        assertEquals(expected, REPOSITORY.read(fixture.connection, CHARACTER));
        assertEquals(expected.events().get(2), REPOSITORY.findOperation(fixture.connection, expected.events().get(2).operationId()));
        assertEquals(1, fixture.rows("s2_character_runtime").size());
        assertEquals(4, fixture.rows("s2_character_event").size());
        assertEquals(7, fixture.rows("s2_character_event_state").size());
        assertEquals(7, fixture.rows("s2_character_event_resource").size());
        assertEquals(2L, fixture.rows("s2_character_event").get(2).get("resource_amount"));
        assertEquals(0L, fixture.rows("s2_character_event_state").getFirst().get("row_version"));
        assertEquals(1, fixture.commits); assertEquals(0, fixture.rollbacks);
    }
    @Test void directRestoreWritesFactsWithoutExecutingCommandsAndRejectsEveryConflictBeforeWriting() throws Exception {
        History history = history(); var fixture = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { REPOSITORY.restore(transaction.connection(), history); transaction.commit(); }
        assertEquals(history, REPOSITORY.read(fixture.connection, CHARACTER));
        int writes = fixture.writes;
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
            assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), history));
            transaction.rollback();
        }
        assertEquals(writes, fixture.writes);
        State stale = history.events().getFirst().after();
        Event conflict = new Event(UUID.randomUUID(), "b".repeat(64), new Command("RESOURCE_SPENT", RESOURCE, 1, null, null), null, stale, state(1, 1, 9, 1, 2, 3));
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { assertThrows(SQLException.class, () -> REPOSITORY.append(transaction.connection(), conflict)); transaction.rollback(); }
        assertEquals(writes, fixture.writes);
    }
    @Test void malformedMissingExtraCrossCharacterAndDiscontinuousHistoryCannotBeReadOrRestored() throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations = List.of(
                f -> f.rows("s2_character_event_state").removeFirst(),
                f -> f.rows("s2_character_event_state").add(new LinkedHashMap<>(f.rows("s2_character_event_state").getFirst())),
                f -> f.rows("s2_character_event_state").getFirst().put("strength", 31),
                f -> f.rows("s2_character_event_state").get(1).put("hit_points", 8L),
                f -> f.rows("s2_character_event_resource").getLast().put("resource_index", 1),
                f -> f.rows("s2_character_event_resource").getLast().put("maximum_value", 1L),
                f -> f.rows("s2_character_event").get(1).put("event_sequence", 9L),
                f -> f.rows("s2_character_event").get(2).put("resource_amount", -1L),
                f -> f.rows("s2_character_runtime").getFirst().put("campaign_id", bytes(UUID.randomUUID())));
        for (var mutation : mutations) {
            var fixture = seeded(); try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { REPOSITORY.restore(transaction.connection(), history()); transaction.commit(); }
            mutation.accept(fixture); assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, CHARACTER));
        }
        var badEvents = new ArrayList<>(history().events()); badEvents.remove(1);
        var fixture = seeded(); int writes = fixture.writes;
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), new History(history().current(), badEvents))); transaction.rollback(); }
        assertEquals(writes, fixture.writes);
    }
    @Test void everyRestoreWriteFailureAndReadbackTamperRollsBackTheWholeCandidate() throws Exception {
        var good = seeded(); try (var transaction = BusinessWriteTransactions.openInternal(good.dataSource())) { REPOSITORY.restore(transaction.connection(), history()); transaction.commit(); }
        for (int point = 1; point <= good.writes; point++) {
            var fixture = seeded(); fixture.failWrite = point;
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
                assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), history()));
                transaction.rollback();
            }
            assertTrue(fixture.rows("s2_character_runtime").isEmpty()); assertTrue(fixture.rows("s2_character_event").isEmpty());
            assertTrue(fixture.rows("s2_character_event_state").isEmpty()); assertTrue(fixture.rows("s2_character_event_resource").isEmpty());
            assertEquals(1, fixture.rows("s2_initial_plan").size()); assertEquals(0, fixture.commits);
        }
        var fixture = seeded(); fixture.afterWrite = f -> { if (!f.rows("s2_character_event_state").isEmpty()) f.rows("s2_character_event_state").getFirst().put("strength", 99); };
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), history())); transaction.rollback(); }
        assertTrue(fixture.rows("s2_character_runtime").isEmpty());
    }
    @Test void combatDeathSavePreservesOptionalBlessDieInAppendAndDirectRestore() throws Exception {
        for (List<Integer> dice : List.of(List.<Integer>of(), List.of(1), List.of(2), List.of(3), List.of(4))) {
            History expected = deathSaveHistory("COMBAT_DEATH_SAVE_RESOLVED", dice);
            var fixture = seeded();
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
                for (Event event : expected.events()) REPOSITORY.append(transaction.connection(), event);
                transaction.commit();
            }
            assertEquals(expected, REPOSITORY.read(fixture.connection, CHARACTER));
            assertEquals(dice.size(), fixture.rows("s2_character_health_die").size());
            assertEquals(dice.size(), fixture.rows("s2_character_health_event").getLast().get("die_count"));
            var restored = seeded();
            try (var transaction = BusinessWriteTransactions.openInternal(restored.dataSource())) {
                REPOSITORY.restore(transaction.connection(), expected);
                transaction.commit();
            }
            assertEquals(expected, REPOSITORY.read(restored.connection, CHARACTER));
            if (!dice.isEmpty()) {
                restored.rows("s2_character_health_die").getLast().put("die_value", 5);
                assertThrows(SQLException.class, () -> REPOSITORY.read(restored.connection, CHARACTER));
            }
        }
    }
    @Test void deathSaveRejectsMultipleOrNonD4BonusesAndStandaloneBlessBeforeWriting() throws Exception {
        for (History malformed : List.of(
                deathSaveHistory("COMBAT_DEATH_SAVE_RESOLVED", List.of(5)),
                deathSaveHistory("COMBAT_DEATH_SAVE_RESOLVED", List.of(1, 2)),
                deathSaveHistory("DEATH_SAVE_RESOLVED", List.of(4)))) {
            var fixture = seeded();
            int writes = fixture.writes;
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
                assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), malformed));
                transaction.rollback();
            }
            assertEquals(writes, fixture.writes);
        }
        assertDoesNotThrow(() -> JdbcSchema2CharacterRuntimeRepository.validate(deathSaveHistory("DEATH_SAVE_RESOLVED", List.of())));
    }
    private static History deathSaveHistory(String kind, List<Integer> dice) {
        State initial = state(0, 1, 9, 1, 3, 3);
        State down = healthState(initial, 1, HealthState.INITIAL);
        State saved = healthState(initial, 2, new HealthState(0, 1, 0, false, false, null));
        RuleIdentity health = new RuleIdentity("example", "adventure.health", "health.standard");
        UUID combat = UUID.randomUUID(), operation = UUID.randomUUID();
        HealthInput input = new HealthInput(health, null, null, "Combat turn begins");
        Command damage = new Command("COMBAT_DAMAGE_APPLIED", null, 0, null, null, null, GrowthChoices.EMPTY,
                null, null, null, input, new CombatInput(combat, operation, UUID.randomUUID(), CHARACTER, 9, false));
        Command death = new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY,
                null, null, null, input, kind.equals("COMBAT_DEATH_SAVE_RESOLVED")
                        ? new CombatInput(combat, operation, UUID.randomUUID(), CHARACTER, 0, false) : null);
        return new History(saved, List.of(
                event(new Command("INITIALIZED", null, 0, null, null), null, initial),
                new Event(UUID.randomUUID(), "a".repeat(64), damage, null, initial, down, null, new HealthResult(List.of(), null, null, 9, 9)),
                new Event(UUID.randomUUID(), "a".repeat(64), death, null, down, saved, null, new HealthResult(dice, 10, null, 0, 0))));
    }
    private static State healthState(State initial, long version, HealthState health) {
        return new State(initial.identity(), initial.campaignId(), initial.characterId(), initial.preparationOperationId(),
                initial.characterClass(), initial.abilities(), initial.level(), initial.proficiencyBonus(), 0, initial.maximumHitPoints(),
                initial.classes(), initial.hitDicePools(), initial.subclasses(), initial.features(), initial.feats(), initial.proficiencies(),
                version, initial.resources(), initial.rest(), initial.equipment(), health);
    }
    private static History history() {
        State initial = state(0, 1, 9, 1, 3, 3), advanced = state(1, 2, 15, 2, 4, 4), spent = state(2, 2, 15, 2, 2, 4), recovered = state(3, 2, 15, 2, 4, 4);
        return new History(recovered, List.of(
                event(new Command("INITIALIZED", null, 0, null, null), null, initial),
                event(new Command("STATISTICS_ADVANCED", null, 0, null, "FIXED_AVERAGE"), initial, advanced),
                event(new Command("RESOURCE_SPENT", RESOURCE, 2, null, null), advanced, spent),
                event(new Command("RESOURCES_RECOVERED", null, 0, "LONG_REST", null), spent, recovered)));
    }
    private static Event event(Command command, State before, State after) { return new Event(UUID.randomUUID(), "a".repeat(64), command, null, before, after); }
    private static State state(long version, int level, long hp, int hitDice, long current, long maximum) { return new State(IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, CLASS, ABILITIES, level, 2 + (level - 1) / 4, hp, hp, List.of(new ClassLevel(CLASS, level)), List.of(new HitDicePool(10, hitDice, level)), List.of(), List.of(), List.of(), List.of(), version, List.of(new ResourceState(RESOURCE, current, maximum, false)), RestState.INITIAL); }
    private static Schema2RuntimeJdbcFixture seeded() {
        var fixture = new Schema2RuntimeJdbcFixture();
        fixture.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id", bytes(PREPARATION), "run_id", IDENTITY.runBytes(), "snapshot_id", IDENTITY.snapshotBytes(), "campaign_id", bytes(CAMPAIGN), "character_id", bytes(CHARACTER))));
        fixture.dataSource(); fixture.saveCommitted(); return fixture;
    }
    private static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
}
