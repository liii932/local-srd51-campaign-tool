package com.dndtool.service;

import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CharacterRuntimeService.*;
import com.dndtool.testing.IsolatedJvmTest;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static com.dndtool.service.Schema2ArcaneRecoveryTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ArcaneRecoveryRuntimeServiceTest {
    @Test void actualShortRestPersistsChoicesDailyUseReplayAndDirectRestoreWithoutSourceOrRandomness() throws Exception {
        var f = new Fixture(true); f.spend(1); f.ready();
        Request request = f.request(finish(Map.of(1, 1)));
        f.commit(request);
        assertEquals(2, slot(f.state(), 1).current()); assertEquals(0L, f.state().spellbook().lastArcaneRecoveryDay());
        assertEquals(1, f.base.runtime.rows("s2_character_arcane_recovery_choice").size());
        var row = f.base.runtime.rows("s2_character_arcane_recovery_choice").getFirst();
        assertEquals(1, row.get("slot_rank")); assertEquals(1, row.get("recovered_value"));
        int writes = f.base.runtime.writes;
        assertEquals(Status.REPLAYED, f.service.execute(request).status()); assertEquals(writes, f.base.runtime.writes);
        Request conflicting = new Request(request.operationId(), request.identity(), request.campaignId(), request.characterId(),
                request.preparationOperationId(), request.rules(), request.expectedVersion(), finish(Map.of()), Map.of());
        assertNotEquals(request.digest(), conflicting.digest());
        assertEquals("S2_CHARACTER_OPERATION_CONFLICT", f.service.execute(conflicting).code());
        History history = f.service.read(f.base.a);
        var target = new Fixture(false); target.base.source.unavailable(true);
        committed(target.service.restore(history)); assertEquals(history, target.service.read(target.base.a));
        assertEquals(Status.REPLAYED, target.service.inspectRestore(history).status()); assertEquals(0, target.rolls);
        target.commit(end()); target.spend(1); target.ready(); writes = target.base.runtime.writes;
        assertEquals("S2_ARCANE_RECOVERY_DAILY_LIMIT", target.service.execute(target.request(finish(Map.of(1, 1)))).code());
        assertEquals(writes, target.base.runtime.writes); assertEquals(0L, target.state().spellbook().lastArcaneRecoveryDay());
        assertEquals(0, f.rolls);
    }

    @Test void actualConsumerRejectsStaleEarlyLongRestAndOverAllocationWithoutAnyPartialBenefit() throws Exception {
        var f = new Fixture(true); f.spend(1); f.commit(start("SHORT_REST"));
        Request early = f.request(finish(Map.of(1, 1))); int writes = f.base.runtime.writes;
        assertEquals("S2_REST_REQUIREMENTS", f.service.execute(early).code()); assertEquals(writes, f.base.runtime.writes);
        f.commit(time("LIGHT", 60));
        writes = f.base.runtime.writes;
        assertEquals("S2_CHARACTER_STALE_VERSION", f.service.execute(early).code());
        assertEquals("S2_ARCANE_RECOVERY_CHOICE", f.service.execute(f.request(finish(Map.of(1, 2)))).code());
        assertEquals(writes, f.base.runtime.writes); assertNull(f.state().spellbook().lastArcaneRecoveryDay());
        f.commit(end()); f.commit(start("LONG_REST")); f.commit(time("SLEEP", 480));
        writes = f.base.runtime.writes; State before = f.state();
        assertEquals("S2_ARCANE_RECOVERY_REST_REQUIRED", f.service.execute(f.request(finish(Map.of(1, 1)))).code());
        assertEquals(before, f.state()); assertEquals(writes, f.base.runtime.writes); assertEquals(0, f.rolls);
    }

    @Test void shortRestDeclineDoesNotSpendDailyUseAndLongRestPreparationKeepsIt() throws Exception {
        var f = new Fixture(true); f.spend(1); f.ready(); f.commit(finish(Map.of()));
        assertNull(f.state().spellbook().lastArcaneRecoveryDay()); assertEquals(1, slot(f.state(), 1).current());
        f.commit(end()); f.ready(); f.commit(finish(Map.of(1, 1))); f.commit(end());
        f.commit(start("LONG_REST")); f.commit(time("SLEEP", 480)); f.commit(finish(Map.of()));
        f.commit(Command.spellbook("SPELLBOOK_PREPARED", new SpellbookInput(CASTING, List.of(), List.of(SHIELD))));
        assertEquals(0L, f.state().spellbook().lastArcaneRecoveryDay());
        assertEquals(0, f.rolls);
    }

    @Test void missingExtraAndTamperedChoiceRowsAndDailyMarkerAreRejectedOnIndependentRead() throws Exception {
        for (String mutation : List.of("MISSING", "EXTRA", "RANK", "AMOUNT", "DAY")) {
            var f = new Fixture(true); f.spend(1); f.ready(); f.commit(finish(Map.of(1, 1)));
            var choices = f.base.runtime.rows("s2_character_arcane_recovery_choice");
            switch (mutation) {
                case "MISSING" -> choices.clear();
                case "EXTRA" -> choices.add(new LinkedHashMap<>(choices.getFirst()));
                case "RANK" -> choices.getFirst().put("slot_rank", 2);
                case "AMOUNT" -> choices.getFirst().put("recovered_value", 2);
                case "DAY" -> f.base.runtime.rows("s2_character_event_state").stream()
                        .filter(row -> row.get("arcane_recovery_day") != null).findFirst().orElseThrow().put("arcane_recovery_day", null);
                default -> throw new AssertionError(mutation);
            }
            assertThrows(SQLException.class, () -> f.service.read(f.base.a), mutation);
        }
    }

    @Test void recoveryFailureRollsBackChoiceDailyMarkerRestAndResourceTogether() throws Exception {
        IsolatedJvmTest.run(FaultProbe.class, "rollback");
    }

    @Test void unknownRecoveryOnlyInspectsOriginalCommittedOrRolledBackFacts() throws Exception {
        for (String outcome : List.of("COMMITTED", "ROLLED_BACK")) IsolatedJvmTest.run(FaultProbe.class, "unknown", outcome);
    }

    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            var f = new Fixture(true); f.spend(1); f.ready();
            Request request = f.request(finish(Map.of(1, 1)));
            if (args[0].equals("rollback")) {
                History before = f.service.read(f.base.a);
                f.base.runtime.afterWrite = runtime -> {
                    if (!runtime.rows("s2_character_arcane_recovery_choice").isEmpty()) throw new IllegalStateException("after recovery choice");
                };
                assertEquals(Status.REJECTED, f.service.execute(request).status());
                assertEquals(before, f.service.read(f.base.a));
                assertTrue(f.base.runtime.rows("s2_character_arcane_recovery_choice").isEmpty());
                assertTrue(BusinessWriteTransactions.isBlocked());
            } else {
                f.base.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                Result unknown = f.service.execute(request); assertEquals(Status.UNKNOWN, unknown.status(), unknown.code());
                assertNull(unknown.event()); f.base.source.unavailable(true); int writes = f.base.runtime.writes;
                Result inspected = f.service.inspect(request);
                assertEquals(args[1].equals("COMMITTED") ? Status.REPLAYED : Status.NOT_COMMITTED, inspected.status(), inspected.code());
                assertEquals(writes, f.base.runtime.writes); assertTrue(BusinessWriteTransactions.isBlocked());
                assertEquals(args[1].equals("COMMITTED") ? 1 : 0, f.base.runtime.rows("s2_character_arcane_recovery_choice").size());
            }
            assertEquals(0, f.rolls);
        }
    }

    private static void committed(Result result) { assertEquals(Status.COMMITTED, result.status(), result.code()); }
    private static final class Fixture {
        final Schema2CombatRuntimeServiceTest.Fixture base;
        final Schema2CharacterRuntimeService service;
        int rolls;
        Fixture(boolean initialize) throws Exception {
            base = new Schema2CombatRuntimeServiceTest.Fixture(initialize, Schema2ArcaneRecoveryTestFixture.packages(), GrowthChoices.EMPTY, WIZARD);
            service = new Schema2CharacterRuntimeService(base.runtime.dataSource(), sides -> { rolls++; return 1; });
            if (initialize) commit(Command.spellbook("SPELLBOOK_INITIALIZED", new SpellbookInput(CASTING, INITIAL_BOOK, List.of(SHIELD))));
        }
        State state() throws SQLException { return service.read(base.a).current(); }
        Request request(Command command) throws Exception { return base.characterRequest(base.a, command); }
        void commit(Command command) throws Exception { commit(request(command)); }
        void commit(Request request) { committed(service.execute(request)); }
        void spend(int count) throws Exception { commit(new Command("RESOURCE_SPENT", SLOT1, count, null, null)); }
        void ready() throws Exception { commit(start("SHORT_REST")); commit(time("LIGHT", 60)); }
    }
}
