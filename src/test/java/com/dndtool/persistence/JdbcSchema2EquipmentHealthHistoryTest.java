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

class JdbcSchema2EquipmentHealthHistoryTest {
    private static final JdbcSchema2CharacterRuntimeRepository REPOSITORY = new JdbcSchema2CharacterRuntimeRepository();
    private static final Identity IDENTITY = Identity.random();
    private static final UUID CAMPAIGN = UUID.randomUUID(), CHARACTER = UUID.randomUUID(), PREPARATION = UUID.randomUUID(), ITEM_ID = UUID.randomUUID();
    private static final RuleIdentity CLASS = rule("character.class", "class.guard"), ITEM = rule("equipment.item", "item.leather_armor"),
            HEALTH = rule("adventure.health", "health.srd2014"), DAMAGE = rule("adventure.vital_effect", "vital_effect.falling");
    private static final Map<String, Integer> ABILITIES = Map.of("ability.strength", 15, "ability.dexterity", 14,
            "ability.constitution", 13, "ability.intelligence", 12, "ability.wisdom", 10, "ability.charisma", 8);

    @Test void actualRowsPreserveEquipmentBeforeAfterHealthDiceAndStableRecoveryAcrossRestore() throws Exception {
        History expected = history(); var fixture = stored(expected);
        assertEquals(expected, REPOSITORY.read(fixture.connection, CHARACTER));
        assertEquals(expected.events().get(5), REPOSITORY.findOperation(fixture.connection, expected.events().get(5).operationId()));
        assertEquals(4, fixture.rows("s2_character_equipment_command").size());
        assertEquals(4, fixture.rows("s2_character_health_event").size());
        assertEquals(2, fixture.rows("s2_character_health_die").size());
        assertEquals(List.of(5, 6), expected.events().get(5).healthResult().dice());
        assertEquals(122L, expected.events().get(8).after().health().stableRecoveryMinute());
        assertEquals(1, expected.current().hitPoints()); assertEquals(HealthState.INITIAL, expected.current().health());
        var target = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(target.dataSource())) {
            for (Event event : expected.events()) REPOSITORY.append(transaction.connection(), event);
            transaction.commit();
        }
        assertEquals(expected, REPOSITORY.read(target.connection, CHARACTER));
    }

    @Test void missingExtraOrphanAndMalformedTypedFactsFailClosed() throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations = List.of(
                f -> f.rows("s2_character_event_equipment").removeFirst(),
                f -> f.rows("s2_character_equipment_command").removeFirst(),
                f -> f.rows("s2_character_health_event").removeFirst(),
                f -> f.rows("s2_character_health_die").removeFirst(),
                f -> f.rows("s2_character_health_die").getFirst().put("die_index", 1),
                f -> f.rows("s2_character_health_die").getFirst().put("die_value", 13),
                f -> f.rows("s2_character_health_event").getFirst().put("die_count", 1),
                f -> f.rows("s2_character_health_event").getFirst().put("death_d20", 1),
                f -> f.rows("s2_character_health_event").add(new LinkedHashMap<>(f.rows("s2_character_health_event").getFirst())),
                f -> f.rows("s2_character_event_equipment").getFirst().put("location", "INVALID".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                f -> f.rows("s2_character_event_state").getLast().put("stable", 1),
                f -> f.rows("s2_character_event_state").getLast().put("dead", 1),
                f -> f.rows("s2_character_event_state").getLast().put("death_successes", 3),
                f -> f.rows("s2_character_event_state").stream().filter(row -> Objects.equals(122L, row.get("stable_recovery_minute"))).findFirst().orElseThrow().put("stable_recovery_minute", 0L));
        for (var mutation : mutations) {
            var fixture = stored(); mutation.accept(fixture);
            assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, CHARACTER));
        }
        for (String table : List.of("s2_character_event_equipment", "s2_character_equipment_command", "s2_character_health_event", "s2_character_health_die")) {
            var fixture = seeded(); fixture.rows(table).add(new LinkedHashMap<>(stored().rows(table).getFirst()));
            assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, CHARACTER), table);
        }
    }

    @Test void malformedCommandUnionsAndForbiddenCrossDomainChangesCannotWrite() throws Exception {
        for (Runnable invalid : List.<Runnable>of(
                () -> equipment("ITEM_ACQUIRED", new EquipmentInput(ITEM_ID, ITEM, "WORN", "获得")),
                () -> equipment("ITEM_EQUIPPED", new EquipmentInput(ITEM_ID, ITEM, "WORN", "穿戴")),
                () -> equipment("ITEM_UNEQUIPPED", new EquipmentInput(ITEM_ID, null, "CARRIED", "卸下")),
                () -> health("DEATH_SAVE_RESOLVED", new HealthInput(HEALTH, DAMAGE, null, "豁免")),
                () -> health("HEALTH_EFFECT_RESOLVED", new HealthInput(HEALTH, null, null, "伤害")),
                () -> new HealthInput(HEALTH, DAMAGE, "ADD", "临时HP"),
                () -> new HealthResult(List.of(13), null, null, 1, 1),
                () -> new HealthResult(Collections.nCopies(21, 1), null, null, 1, 1),
                () -> new HealthState(0, 3, 0, false, false, null))) assertThrows(IllegalArgumentException.class, invalid::run);
        History good = history(); Event damage = good.events().get(5);
        State changedEquipment = state(5, 0, List.of(new EquipmentItem(ITEM_ID, ITEM, "CARRIED")), HealthState.INITIAL, 0);
        Event invalid = new Event(damage.operationId(), damage.requestDigest(), damage.command(), null, damage.before(), changedEquipment, null, damage.healthResult());
        var fixture = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
            assertThrows(SQLException.class, () -> REPOSITORY.append(transaction.connection(), invalid)); transaction.rollback();
        }
        assertEquals(0, fixture.writes);
        var unsorted = List.of(new EquipmentItem(UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff"), ITEM, "CARRIED"),
                new EquipmentItem(UUID.fromString("00000000-0000-4000-8000-000000000001"), ITEM, "CARRIED"));
        assertThrows(SQLException.class, () -> JdbcSchema2CharacterRuntimeRepository.checkCandidateCapacity(null, state(0, 11, unsorted, HealthState.INITIAL, 0)));
        var conflict = List.of(new EquipmentItem(UUID.fromString("00000000-0000-4000-8000-000000000001"), ITEM, "BOTH_HANDS"),
                new EquipmentItem(UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff"), ITEM, "MAIN_HAND"));
        assertThrows(SQLException.class, () -> JdbcSchema2CharacterRuntimeRepository.checkCandidateCapacity(null, state(0, 11, conflict, HealthState.INITIAL, 0)));
    }

    @Test void everyWriteFailureRollsBackEquipmentHealthAndDiceTogether() throws Exception {
        int writes = stored().writes;
        for (int point = 1; point <= writes; point++) {
            var fixture = seeded(); fixture.failWrite = point;
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
                assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), history())); transaction.rollback();
            }
            for (String table : List.of("s2_character_runtime", "s2_character_event", "s2_character_event_state", "s2_character_event_equipment", "s2_character_equipment_command", "s2_character_health_event", "s2_character_health_die"))
                assertTrue(fixture.rows(table).isEmpty(), table + " retained at failure " + point);
            assertEquals(0, fixture.commits); assertEquals(1, fixture.rows("s2_initial_plan").size());
        }
    }

    private static History history() {
        List<Event> events = new ArrayList<>(); State initial = state(0, 11, List.of(), HealthState.INITIAL, 0);
        events.add(event(UUID.randomUUID(), new Command("INITIALIZED", null, 0, null, null), null, initial, null));
        State carried = state(1, 11, List.of(new EquipmentItem(ITEM_ID, ITEM, "CARRIED")), HealthState.INITIAL, 0);
        events.add(event(ITEM_ID, equipment("ITEM_ACQUIRED", new EquipmentInput(ITEM_ID, ITEM, "CARRIED", "获得护甲")), initial, carried, null));
        State worn = state(2, 11, List.of(new EquipmentItem(ITEM_ID, ITEM, "WORN")), HealthState.INITIAL, 1);
        events.add(event(UUID.randomUUID(), equipment("ITEM_EQUIPPED", new EquipmentInput(ITEM_ID, null, "WORN", "穿戴护甲")), carried, worn, null));
        State takenOff = state(3, 11, List.of(new EquipmentItem(ITEM_ID, ITEM, "CARRIED")), HealthState.INITIAL, 2);
        events.add(event(UUID.randomUUID(), equipment("ITEM_UNEQUIPPED", new EquipmentInput(ITEM_ID, null, null, "脱下护甲")), worn, takenOff, null));
        State removed = state(4, 11, List.of(), HealthState.INITIAL, 2);
        events.add(event(UUID.randomUUID(), equipment("ITEM_REMOVED", new EquipmentInput(ITEM_ID, null, null, "移除护甲")), takenOff, removed, null));
        State dying = state(5, 0, List.of(), HealthState.INITIAL, 2);
        events.add(event(UUID.randomUUID(), health("HEALTH_EFFECT_RESOLVED", new HealthInput(HEALTH, DAMAGE, null, "坠落")), removed, dying, new HealthResult(List.of(5, 6), null, null, 11, 11)));
        State successOne = state(6, 0, List.of(), new HealthState(0, 1, 0, false, false, null), 2);
        events.add(event(UUID.randomUUID(), health("DEATH_SAVE_RESOLVED", new HealthInput(HEALTH, null, null, "死亡豁免")), dying, successOne, new HealthResult(List.of(), 10, null, 0, 0)));
        State successTwo = state(7, 0, List.of(), new HealthState(0, 2, 0, false, false, null), 2);
        events.add(event(UUID.randomUUID(), health("DEATH_SAVE_RESOLVED", new HealthInput(HEALTH, null, null, "死亡豁免")), successOne, successTwo, new HealthResult(List.of(), 10, null, 0, 0)));
        State stable = state(8, 0, List.of(), new HealthState(0, 0, 0, true, false, 122L), 2);
        events.add(event(UUID.randomUUID(), health("DEATH_SAVE_RESOLVED", new HealthInput(HEALTH, null, null, "死亡豁免")), successTwo, stable, new HealthResult(List.of(), 10, 2, 0, 0)));
        State recovered = state(9, 1, List.of(), HealthState.INITIAL, 122);
        events.add(event(UUID.randomUUID(), new Command("TIME_ADVANCED", null, 0, null, null, null, GrowthChoices.EMPTY, new RestInput(null, "NONE", 120, 0, Map.of())), stable, recovered, null));
        return new History(recovered, events);
    }
    private static Command equipment(String kind, EquipmentInput input) { return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null, input, null); }
    private static Command health(String kind, HealthInput input) { return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null, input); }
    private static Event event(UUID operation, Command command, State before, State after, HealthResult result) { return new Event(operation, "a".repeat(64), command, null, before, after, null, result); }
    private static State state(long version, long hp, List<EquipmentItem> equipment, HealthState health, long minute) {
        return new State(IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, CLASS, ABILITIES, 1, 2, hp, 11, List.of(new ClassLevel(CLASS, 1)),
                List.of(new HitDicePool(10, 1, 1)), List.of(), List.of(), List.of(), List.of(), version, List.of(), new RestState(minute, null, null), equipment, health);
    }
    private static Schema2RuntimeJdbcFixture stored() throws Exception { return stored(history()); }
    private static Schema2RuntimeJdbcFixture stored(History history) throws Exception { var fixture = seeded(); try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { REPOSITORY.restore(transaction.connection(), history); transaction.commit(); } return fixture; }
    private static Schema2RuntimeJdbcFixture seeded() {
        var fixture = new Schema2RuntimeJdbcFixture(); fixture.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id", bytes(PREPARATION), "run_id", IDENTITY.runBytes(), "snapshot_id", IDENTITY.snapshotBytes(), "campaign_id", bytes(CAMPAIGN), "character_id", bytes(CHARACTER))));
        fixture.dataSource(); fixture.saveCommitted(); return fixture;
    }
    private static RuleIdentity rule(String type, String key) { return new RuleIdentity("base", type, key); }
    private static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
}
