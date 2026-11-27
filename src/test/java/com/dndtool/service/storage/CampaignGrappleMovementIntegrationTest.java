package com.dndtool.service.storage;

import com.dndtool.service.storage.CampaignExtraAttackIntegrationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CombatState.*;
import static com.dndtool.service.Schema2MeleeControlRules.handFree;
import static com.dndtool.service.storage.CampaignStorageService.Boundary;
import static org.junit.jupiter.api.Assertions.*;

class CampaignGrappleMovementIntegrationTest {
    private static final Position START = new Position(2, 2);
    private static final Position CARRIED_START = new Position(2, 1);
    private static final List<Position> ROUTE = List.of(new Position(2, 3), new Position(3, 4), new Position(4, 5));
    private static final List<Position> CARRIED_ROUTE = List.of(START, ROUTE.get(0), ROUTE.get(1));
    @TempDir Path temporary;

    @Test void pairedPrefixSurvivesSaveAndLateFailureRollsBackBothParticipantsAndSpend() throws Exception {
        try (Fixture fixture = new Fixture(temporary.resolve("drag-save"))) {
            Participants participants = start(fixture, 5);
            UUID mover = participants.mover(), carried = participants.carried(), reactor = participants.reactor();
            UUID dragRelation = grapple(fixture, mover, carried);
            UUID reactorRelation = grapple(fixture, mover, reactor, HandSlot.OFF_HAND);
            assertTrue(relation(fixture, reactorRelation).active());
            assertFalse(handFree(fixture.battle(), fixture.card(mover), HandSlot.OFF_HAND));
            var moverBefore = placement(fixture, mover);
            var carriedBefore = placement(fixture, carried);
            fixture.reject(Command.move(mover, List.of(ROUTE.get(0), ROUTE.get(1), ROUTE.get(2), new Position(5, 6)),
                    new DragInput(dragRelation, List.of(START, ROUTE.get(0), ROUTE.get(1), ROUTE.get(2)))),
                    "S2_COMBAT_MOVEMENT_ALLOWANCE");
            fixture.reject(Command.move(mover, ROUTE,
                    new DragInput(dragRelation, List.of(START, ROUTE.get(1), ROUTE.get(2)))), "S2_COMBAT_PATH");
            fixture.reject(Command.move(mover, List.of(CARRIED_START), new DragInput(dragRelation, List.of(START))),
                    "S2_COMBAT_PATH");

            fixture.combat(Command.move(mover, ROUTE, new DragInput(dragRelation, CARRIED_ROUTE)));
            assertPrefix(fixture, mover, carried);
            assertEquals(moverBefore.version() + 1, placement(fixture, mover).version());
            assertEquals(carriedBefore.version() + 1, placement(fixture, carried).version());

            Flow flow = fixture.battle().activeFlow();
            Movement movement = flow.movements().getFirst();
            assertEquals(new DragPlan(dragRelation, carried, CARRIED_START, CARRIED_ROUTE, 30), movement.drag());
            assertEquals(1, movement.cursor());
            assertEquals(5, movement.spent());
            assertEquals(10, movement.spentHalfFeet());
            Window window = flow.windows().getLast();
            assertEquals(mover, window.trigger().subjectId());
            assertEquals(List.of(reactor), window.opportunities().stream().map(Opportunity::actorId).toList());
            assertEntries(fixture, movement, 1);

            var paused = fixture.access();
            String finalFile = fixture.end();
            fixture.service.open(finalFile);
            assertEquals(paused.snapshot(), fixture.access().snapshot());
            assertEquals(flow, fixture.battle().activeFlow());
            fixture.react(Kind.DECLARE_OPPORTUNITY_ATTACK, reactor, participants.weapon(), 1);
            var checkpoint = fixture.access();

            String request = UUID.randomUUID().toString();
            var command = fixture.combatInput(resume(fixture, mover));
            fixture.failure = Boundary.AFTER_CHARACTER_UPDATE;
            assertEquals("injected transaction failure", assertThrows(IllegalStateException.class,
                    () -> fixture.submit(request, checkpoint, command, null)).getMessage());
            assertEquals(checkpoint, fixture.access());
            assertPrefix(fixture, mover, carried);
            assertTrue(relation(fixture, dragRelation).active());
            assertTrue(relation(fixture, reactorRelation).active());
            assertFalse(handFree(fixture.battle(), fixture.card(mover), HandSlot.OFF_HAND));
            assertEquals(WindowStatus.CLOSED, fixture.battle().activeFlow().windows().getLast().status());

            var accepted = fixture.submit(request, checkpoint, command, null);
            assertEquals(ROUTE.getLast(), placement(fixture, mover).grid());
            assertEquals(CARRIED_ROUTE.getLast(), placement(fixture, carried).grid());
            assertEquals(30, fixture.actor(mover).movementSpentHalfFeet());
            assertEquals(0, fixture.actor(carried).movementSpentHalfFeet());
            assertTrue(relation(fixture, dragRelation).active());
            assertFalse(handFree(fixture.battle(), fixture.card(mover), HandSlot.MAIN_HAND));
            assertTrue(handFree(fixture.battle(), fixture.card(mover), HandSlot.OFF_HAND));
            var expired = relation(fixture, reactorRelation);
            assertEquals(GrappleEndReason.OUT_OF_REACH, expired.endReason());
            assertEquals(request, expired.endedOperationId().toString());
            assertNull(fixture.battle().activeFlow());
            Movement completed = fixture.battle().flows().getLast().movements().getFirst();
            assertEquals(MovementStatus.COMPLETED, completed.status());
            assertEquals(30, completed.spentHalfFeet());
            assertEntries(fixture, completed, 3);
            var replayed = fixture.submit(request, checkpoint, command, null);
            assertTrue(replayed.lastCommand().replayed());
            assertEquals(accepted.lastCommand().acceptedRevision(), replayed.lastCommand().acceptedRevision());
            assertEquals(accepted.domain(), replayed.domain());
            assertEquals(1, fixture.state().roots().stream().filter(root -> root.operationId().toString().equals(request)).count());
        }
    }

    @Test void releaseDuringOpportunityStopsOnlyTheUnexecutedPairedSuffix() throws Exception {
        try (Fixture fixture = new Fixture(temporary.resolve("released-drag"))) {
            Participants participants = start(fixture, 1);
            UUID mover = participants.mover(), carried = participants.carried(), reactor = participants.reactor();
            UUID relation = grapple(fixture, mover, carried);
            fixture.combat(Command.move(mover, ROUTE, new DragInput(relation, CARRIED_ROUTE)));
            Flow flow = fixture.battle().activeFlow();
            var moverBefore = fixture.actor(mover);
            var carriedBefore = fixture.actor(carried);

            fixture.combat(Command.melee(Kind.RELEASE_GRAPPLE, mover, null, flow.flowKey(), flow.leafFrameKey(),
                    new MeleeInput(null, null, relation, null, null, null)));
            assertEquals(GrappleEndReason.RELEASED, fixture.battle().meleeControl().grapples().getFirst().endReason());
            assertEquals(moverBefore, fixture.actor(mover));
            assertEquals(carriedBefore, fixture.actor(carried));
            assertEquals(flow, fixture.battle().activeFlow());
            fixture.react(Kind.ADVANCE_WINDOW, reactor, null);
            fixture.continueMovement(mover);
            assertPrefix(fixture, mover, carried);
            Movement stopped = fixture.battle().flows().getLast().movements().getFirst();
            assertEquals(MovementStatus.STOPPED, stopped.status());
            assertEquals("GRAPPLE_ENDED", stopped.stopReason());
            assertEntries(fixture, stopped, 1);
        }
    }

    @Test void grappledCreatureCannotMoveOrStandAndDashProvidesNoMovement() throws Exception {
        try (Fixture fixture = new Fixture(temporary.resolve("grappled-speed"))) {
            Participants participants = start(fixture, 1);
            UUID mover = participants.mover(), carried = participants.carried();
            UUID relation = grapple(fixture, mover, carried);
            fixture.combat(Command.action(Kind.END_TURN, mover));
            assertEquals(carried, fixture.battle().activeActor());
            fixture.combat(Command.action(Kind.DROP_PRONE, carried));
            fixture.combat(Command.action(Kind.DASH, carried));
            assertEquals(0, fixture.actor(carried).movementBonus());
            fixture.reject(Command.move(carried, List.of(new Position(2, 0))), "S2_COMBAT_MOVEMENT_ALLOWANCE");
            fixture.reject(Command.action(Kind.STAND_UP, carried), "S2_COMBAT_CANNOT_STAND");

            fixture.combat(Command.melee(Kind.RELEASE_GRAPPLE, mover, null, null, null,
                    new MeleeInput(null, null, relation, null, null, null)));
            fixture.combat(Command.action(Kind.STAND_UP, carried));
            assertFalse(fixture.actor(carried).prone());
            assertEquals(30, fixture.actor(carried).movementSpentHalfFeet());
        }
    }

    private static Participants start(Fixture fixture, int moverLevel) {
        fixture.create();
        UUID mover = fixture.character("擒抱者", "PC");
        UUID carried = fixture.character("受擒者", "NPC");
        UUID reactor = fixture.character("机会攻击者", "NPC");
        fixture.equip(carried);
        UUID weapon = fixture.equip(reactor);
        fixture.advance(mover, moverLevel);
        fixture.startBattle("拖带验收", List.of(new Deployment(mover, START, 0),
                new Deployment(carried, CARRIED_START, 1), new Deployment(reactor, new Position(1, 2), 1)), 20, 10, 5);
        return new Participants(mover, carried, reactor, weapon);
    }

    private static UUID grapple(Fixture fixture, UUID mover, UUID carried) {
        fixture.combat(Command.action(Kind.BEGIN_ATTACK, mover));
        return grapple(fixture, mover, carried, HandSlot.MAIN_HAND);
    }

    private static UUID grapple(Fixture fixture, UUID mover, UUID carried, HandSlot hand) {
        var sequence = fixture.actor(mover).sequence();
        fixture.combat(Command.melee(Kind.GRAPPLE, mover, carried, null, null,
                new MeleeInput(sequence.sequenceKey(), sequence.performed(), null, hand, null, null)));
        Flow flow = fixture.battle().activeFlow();
        fixture.combat(Command.melee(Kind.CHOOSE_MELEE_DEFENSE, carried, null, flow.flowKey(), flow.leafFrameKey(),
                new MeleeInput(null, null, null, null, ContestSkill.ACROBATICS, null)), 19, 2);
        return fixture.battle().meleeControl().grapples().getLast().grappleKey();
    }

    private static GrappleRelation relation(Fixture fixture, UUID relation) {
        return fixture.battle().meleeControl().grapples().stream().filter(value -> value.grappleKey().equals(relation))
                .findFirst().orElseThrow();
    }

    private static CampaignDomainData.Position placement(Fixture fixture, UUID actor) {
        return CampaignSpaceCommands.position(fixture.state(), actor);
    }

    private static Command resume(Fixture fixture, UUID mover) {
        Flow flow = fixture.battle().activeFlow();
        return Command.continuation(mover, flow.flowKey(), flow.leafFrameKey(), flow.windows().getLast().windowKey());
    }

    private static void assertPrefix(Fixture fixture, UUID mover, UUID carried) {
        assertEquals(ROUTE.getFirst(), placement(fixture, mover).grid());
        assertEquals(CARRIED_ROUTE.getFirst(), placement(fixture, carried).grid());
        assertEquals(10, fixture.actor(mover).movementSpentHalfFeet());
        assertEquals(0, fixture.actor(carried).movementSpentHalfFeet());
    }

    private static void assertEntries(Fixture fixture, Movement movement, int count) {
        List<TriggerFact> triggers = fixture.battle().triggerFacts().stream()
                .filter(fact -> fact.sourceFrameKey().equals(movement.movementKey())).toList();
        List<TriggerFact> entries = triggers.stream().filter(fact -> fact.kind()==TriggerKind.CREATURE_ENTER_CELL_AFTER).toList();
        assertEquals(2 * count, entries.size());
        for (int segment = 0; segment < count; segment++) {
            int index = segment;
            assertEquals(List.of(movement.actorId(), movement.drag().targetId()), entries.stream()
                    .filter(fact -> fact.ordinal()==index).map(TriggerFact::subjectId).toList());
        }
        assertTrue(triggers.stream().filter(fact -> fact.kind()==TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE)
                .allMatch(fact -> fact.subjectId().equals(movement.actorId())));
    }

    private record Participants(UUID mover, UUID carried, UUID reactor, UUID weapon) { }
}
