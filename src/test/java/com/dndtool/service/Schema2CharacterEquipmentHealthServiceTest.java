package com.dndtool.service;

import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CharacterRuntimeService.*;
import com.dndtool.testing.IsolatedJvmTest;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static com.dndtool.service.Schema2CharacterHealthRulesTest.time;
import static org.junit.jupiter.api.Assertions.*;

/** Actual typed command/JDBC histories, deterministic recovery and isolated fault lifetimes. */
class Schema2CharacterEquipmentHealthServiceTest {
    @Test void equipmentChecksHealthGrowthAndLongRestShareOneExactRecoverableState() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize();
        UUID armor = fixture.acquire(ITEM_MEDIUM); fixture.commit(equipment("ITEM_EQUIPPED", armor, null, "WORN"));
        UUID tool = fixture.acquire(ITEM_TOOL); fixture.commit(equipment("ITEM_EQUIPPED", tool, null, "MAIN_HAND"));
        UUID shield = fixture.acquire(ITEM_SHIELD); fixture.commit(equipment("ITEM_EQUIPPED", shield, null, "OFF_HAND"));
        assertEquals(5, fixture.state.rest().minute());
        var check = fixture.commit(check("TOOL", "ability.dexterity", TOOL, 13, true, false));
        assertEquals(new CheckResult(10, null, 10, 1, 2, 13, true, true, true), check.event().checkResult());
        assertFalse(check.event().command().checkInput().disadvantage());
        fixture.commit(new Command("RESOURCE_SPENT", ENERGY, 1, null, null));
        fixture.commit(effect(DAMAGE, null)); assertEquals(5, fixture.state.hitPoints());
        fixture.nextRolls.addAll(List.of(2, 2)); fixture.commit(effect(HEALING, null)); assertEquals(10, fixture.state.hitPoints());
        fixture.commit(effect(TEMPORARY, "REPLACE"));
        State beforeCheck = fixture.state;
        var preserved = fixture.commit(check("TOOL", "ability.wisdom", TOOL, 10, false, false)).event().after();
        assertEquals(beforeCheck.health(), preserved.health()); assertEquals(beforeCheck.equipment(), preserved.equipment());
        assertEquals(beforeCheck.rest(), preserved.rest()); assertEquals(beforeCheck.resources(), preserved.resources());
        assertEquals(beforeCheck.hitPoints(), preserved.hitPoints()); assertEquals(beforeCheck.maximumHitPoints(), preserved.maximumHitPoints());
        assertEquals(beforeCheck.hitDicePools(), preserved.hitDicePools()); assertEquals(beforeCheck.proficiencies(), preserved.proficiencies());
        assertEquals(beforeCheck.version() + 1, preserved.version());
        var equipment = fixture.state.equipment();
        fixture.commit(new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", CLASS, GrowthChoices.EMPTY));
        assertEquals(equipment, fixture.state.equipment()); assertEquals(5, fixture.state.health().temporaryHitPoints());
        fixture.commit(rest("REST_STARTED", new RestInput("LONG_REST", null, 0, 0, Map.of())));
        fixture.commit(rest("TIME_ADVANCED", new RestInput(null, "SLEEP", 480, 0, Map.of())));
        fixture.commit(rest("REST_COMPLETED", new RestInput(null, null, 0, 0, Map.of())));
        assertEquals(0, fixture.state.health().temporaryHitPoints()); assertEquals(fixture.state.maximumHitPoints(), fixture.state.hitPoints());
        assertEquals(equipment, fixture.state.equipment());
        History history = fixture.service.read(CHARACTER); fixture.source.unavailable(true);
        var target = new Schema2CheckTestFixture(); target.source.unavailable(true);
        committed(target.service.restore(history)); assertEquals(history, target.service.read(CHARACTER)); assertEquals(0, target.rolls);
        assertEquals(Status.REPLAYED, target.service.inspectRestore(history).status());
        assertTrue(target.runtime.rows("campaign").isEmpty());
    }

    @Test void handOccupancyToolPreparationAndItemOperationBindingRejectBeforeDiceOrWrites() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize(); UUID tool = fixture.acquire(ITEM_TOOL);
        int writes = fixture.runtime.writes;
        assertEquals("S2_CHECK_TOOL_UNAVAILABLE", fixture.service.execute(fixture.request(check("TOOL", "ability.strength", TOOL, 10, false, false))).code());
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
        fixture.commit(equipment("ITEM_EQUIPPED", tool, null, "MAIN_HAND"));
        UUID weapon = fixture.acquire(ITEM_TWO_HANDED); writes = fixture.runtime.writes;
        assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(equipment("ITEM_EQUIPPED", weapon, null, "BOTH_HANDS"))).status());
        assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(equipment("ITEM_REMOVED", tool, null, null))).status());
        assertThrows(IllegalArgumentException.class, () -> fixture.request(equipment("ITEM_ACQUIRED", UUID.randomUUID(), ITEM_LIGHT, "CARRIED")));
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
    }

    @Test void dyingBlocksOtherOwnersAndThreeSuccessesRecoverAtSavedDeadlineWithoutNewDice() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize(); drop(fixture);
        int writes = fixture.runtime.writes;
        for (Command command : List.of(check("ABILITY", "ability.strength", null, 10, false, false), time(1),
                new Command("RESOURCE_SPENT", ENERGY, 1, null, null),
                new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL", CLASS, GrowthChoices.EMPTY),
                rest("REST_STARTED", new RestInput("SHORT_REST", null, 0, 0, Map.of()))))
            assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(command)).status());
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
        fixture.nextRolls.addAll(List.of(10, 19, 10, 2));
        fixture.commit(deathSave()); fixture.commit(deathSave());
        Request third = fixture.request(deathSave()); var stable = fixture.execute(third); committed(stable);
        assertEquals(new HealthResult(List.of(), 10, 2, 0, 0), stable.event().healthResult());
        assertTrue(fixture.state.health().stable()); assertEquals(120L, fixture.state.health().stableRecoveryMinute());
        assertEquals(4, fixture.rolls); writes = fixture.runtime.writes;
        assertEquals(Status.REPLAYED, fixture.service.execute(third).status()); assertEquals(writes, fixture.runtime.writes);
        fixture.commit(time(119)); assertEquals(0, fixture.state.hitPoints()); assertTrue(fixture.state.health().stable());
        assertEquals(4, fixture.rolls);
        fixture.commit(time(1)); assertEquals(120, fixture.state.rest().minute());
        assertEquals(1, fixture.state.hitPoints()); assertEquals(HealthState.INITIAL, fixture.state.health());
        assertEquals(4, fixture.rolls);
        History history = fixture.service.read(CHARACTER); var target = new Schema2CheckTestFixture();
        committed(target.service.restore(history)); assertEquals(history, target.service.read(CHARACTER)); assertEquals(0, target.rolls);
    }

    @Test void deadCharactersCannotReceiveHealingTemporaryPointsOrFurtherBusinessCommands() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize(); fixture.commit(effect(MASSIVE_DAMAGE, null));
        assertTrue(fixture.state.health().dead()); int writes = fixture.runtime.writes;
        for (Command command : List.of(effect(HEALING, null), effect(TEMPORARY, "REPLACE"), deathSave(), time(10)))
            assertEquals("S2_HEALTH_DEAD", fixture.service.execute(fixture.request(command)).code());
        assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
    }

    @Test void restoreRejectsForgedHealthResultsAndEquipmentWithoutReplayingBenefits() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize();
        fixture.commit(effect(DAMAGE, null)); fixture.nextRolls.addAll(List.of(1, 1)); fixture.commit(effect(HEALING, null));
        History history = fixture.service.read(CHARACTER); Event last = history.events().getLast();
        var result = last.healthResult();
        var forged = new Event(last.operationId(), last.requestDigest(), last.command(), null, last.before(), last.after(), null,
                new HealthResult(result.dice(), null, null, result.rolledAmount(), result.appliedAmount() + 1));
        var events = new ArrayList<>(history.events()); events.set(events.size() - 1, forged);
        var target = new Schema2CheckTestFixture(); int writes = target.runtime.writes;
        assertEquals("S2_CHARACTER_RESTORE_INVALID", target.service.restore(new History(history.current(), events)).code());
        assertEquals(writes, target.runtime.writes); assertEquals(0, target.rolls); assertFalse(BusinessWriteTransactions.isBlocked());
    }

    @Test void healthWriteFailureRollsBackAllTypedFactsAndSavedState() throws Exception { IsolatedJvmTest.run(FaultProbe.class, "rollback"); }
    @Test void healthUnknownOutcomeUsesOriginalFactsAndKeepsTheSharedWriteFreeze() throws Exception {
        for (String outcome : List.of("COMMITTED", "ROLLED_BACK")) IsolatedJvmTest.run(FaultProbe.class, "unknown", outcome);
    }
    @Test void missingTypedEquipmentAndHealthHistoryRejectsReadAndExecutionBeforeDiceOrWrites() throws Exception {
        for (String table : List.of("s2_character_event_equipment", "s2_character_equipment_command", "s2_character_health_event", "s2_character_health_die"))
            IsolatedJvmTest.run(FaultProbe.class, "corrupt", table);
    }
    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            var fixture = new Schema2CheckTestFixture(); fixture.initialize(); fixture.commit(effect(DAMAGE, null));
            Request request = fixture.request(effect(HEALING, null)); History before = fixture.service.read(CHARACTER);
            if (args[0].equals("corrupt")) {
                UUID tool = fixture.acquire(ITEM_TOOL); fixture.commit(equipment("ITEM_EQUIPPED", tool, null, "MAIN_HAND"));
                fixture.commit(effect(HEALING, null));
                assertFalse(fixture.runtime.rows(args[1]).isEmpty(), args[1]);
                fixture.runtime.rows(args[1]).clear(); fixture.runtime.saveCommitted();
                int writes = fixture.runtime.writes, rolls = fixture.rolls;
                assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(check("ABILITY", "ability.wisdom", null, 10, false, false))).status());
                assertThrows(java.sql.SQLException.class, () -> fixture.service.read(CHARACTER));
                assertEquals(writes, fixture.runtime.writes); assertEquals(rolls, fixture.rolls);
                assertTrue(BusinessWriteTransactions.isBlocked());
            } else if (args[0].equals("unknown")) {
                fixture.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                var result = fixture.service.execute(request); assertEquals(Status.UNKNOWN, result.status(), result.code()); assertNull(result.event());
                assertEquals(2, fixture.rolls); fixture.source.unavailable(true); int writes = fixture.runtime.writes;
                assertEquals(args[1].equals("COMMITTED") ? Status.REPLAYED : Status.NOT_COMMITTED, fixture.service.inspect(request).status());
                assertEquals(writes, fixture.runtime.writes); assertEquals(2, fixture.rolls); assertTrue(BusinessWriteTransactions.isBlocked());
            } else {
                int previousDice = fixture.runtime.rows("s2_character_health_die").size();
                fixture.runtime.afterWrite = db -> { if (db.rows("s2_character_health_die").size() > previousDice) throw new IllegalStateException("after typed health die insert"); };
                assertEquals(Status.REJECTED, fixture.service.execute(request).status()); assertEquals(before, fixture.service.read(CHARACTER)); assertEquals(2, fixture.rolls);
            }
        }
    }
    private static void drop(Schema2CheckTestFixture fixture) { fixture.commit(effect(DAMAGE, null)); fixture.commit(effect(DAMAGE, null)); }
    private static Command rest(String kind, RestInput input) { return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, input); }
    private static void committed(Result result) { assertEquals(Status.COMMITTED, result.status(), result.code()); }
}
