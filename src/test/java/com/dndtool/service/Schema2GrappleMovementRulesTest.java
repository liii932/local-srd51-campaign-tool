package com.dndtool.service;

import com.dndtool.service.Schema2CharacterRuntime.HealthState;
import com.dndtool.service.Schema2CombatRules.Context;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2CombatState.*;
import static com.dndtool.service.Schema2CombatTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2GrappleMovementRulesTest {
    private final Schema2CombatRules rules = new Schema2CombatRules();
    private final UUID campaign = UUID.randomUUID(), combat = UUID.randomUUID();
    private final UUID mover = UUID.randomUUID(), carried = UUID.randomUUID(), reactor = UUID.randomUUID();
    private final Map<UUID, Context> roster = new HashMap<>();
    private Battle battle;

    @Test void incapacitatingOpportunityEndsGrappleAndKeepsBothCommittedPositions() {
        Position origin = new Position(2, 2), heldOrigin = new Position(2, 1), first = new Position(2, 3);
        start(origin, heldOrigin, new Position(1, 2), true);
        UUID relation = grapple();
        apply(Command.move(mover, List.of(first, new Position(3, 4)),
                new DragInput(relation, List.of(origin, first))));
        Flow flow = battle.activeFlow();
        Window window = flow.windows().getLast();
        assertEquals(reactor, window.opportunities().getFirst().actorId());
        UUID weapon = roster.get(reactor).character().equipment().getFirst().itemId();
        apply(Command.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, reactor, weapon, flow.flowKey(), flow.leafFrameKey(),
                window.windowKey(), window.opportunities().getFirst().opportunityKey()), 20, 1, 1);
        assertEquals(0, roster.get(mover).character().hitPoints());
        assertEquals(GrappleEndReason.GRAPPLER_INCAPACITATED, battle.meleeControl().grapples().getFirst().endReason());
        assertEquals(MovementStatus.STOPPED, battle.activeFlow().movements().getFirst().status());
        apply(Command.continuation(mover, flow.flowKey(), battle.activeFlow().leafFrameKey(), window.windowKey()));
        assertNull(battle.activeFlow());
        assertEquals(first, actor(mover).position());
        assertEquals(origin, actor(carried).position());
        assertEquals(10, actor(mover).movementSpentHalfFeet());
        assertEquals(0, actor(carried).movementSpentHalfFeet());
        assertEquals(1, battle.flows().getLast().movements().getFirst().cursor());
    }

    @Test void ordinaryMovementAlreadySpentReducesDragBudget() {
        Position origin = new Position(0, 0), heldOrigin = new Position(0, 1), first = new Position(1, 0);
        start(origin, heldOrigin, new Position(0, 8), false);
        UUID relation = grapple();
        apply(Command.move(mover, List.of(first)));
        List<Position> route = List.of(new Position(2, 0), new Position(3, 0), new Position(4, 0));
        List<Position> paired = List.of(first, route.getFirst(), route.get(1));
        reject(Command.move(mover, route, new DragInput(relation, paired)), "S2_COMBAT_MOVEMENT_ALLOWANCE");
        apply(Command.move(mover, route.subList(0,2), new DragInput(relation, paired.subList(0,2))));
        assertEquals(30, actor(mover).movementSpentHalfFeet());
        assertEquals(20, battle.flows().getLast().movements().getFirst().spentHalfFeet());
        assertEquals(0, actor(carried).movementSpentHalfFeet());
    }

    @Test void bothEntryFactsCommitBeforeEitherPreparedReactionAndResumeOnceEach() {
        Position origin = new Position(2, 2), heldOrigin = new Position(2, 1), first = new Position(2, 3);
        start(origin, heldOrigin, new Position(1, 2), false);
        UUID relation = grapple();
        apply(Command.action(Kind.END_TURN, mover));
        readyAttackOnEntry(carried, mover, first);
        apply(Command.action(Kind.END_TURN, carried));
        readyAttackOnEntry(reactor, carried, origin);
        apply(Command.action(Kind.END_TURN, reactor));

        apply(Command.move(mover, List.of(first), new DragInput(relation, List.of(origin))));
        Flow flow = battle.activeFlow();
        Movement movement = flow.movements().getFirst();
        assertEquals(first, actor(mover).position());
        assertEquals(origin, actor(carried).position());
        assertEquals(List.of(mover, carried), battle.triggerFacts().stream()
                .filter(trigger -> trigger.sourceFrameKey().equals(movement.movementKey()))
                .filter(trigger -> trigger.kind()==TriggerKind.CREATURE_ENTER_CELL_AFTER).map(TriggerFact::subjectId).toList());
        passAndContinue(carried, mover);
        assertEquals(carried, battle.activeFlow().windows().getLast().trigger().subjectId());
        passAndContinue(reactor, mover);
        assertNull(battle.activeFlow());
        assertEquals(List.of(WindowStatus.CONSUMED, WindowStatus.CONSUMED),
                battle.flows().getLast().windows().stream().map(Window::status).toList());
        assertEquals(MovementStatus.COMPLETED, battle.flows().getLast().movements().getFirst().status());
        assertEquals(10, actor(mover).movementSpentHalfFeet());
    }

    @Test void grappledCreatureHasZeroReadyMovementAndCannotUseStoredDashBonus() {
        start(new Position(0, 0), new Position(0, 1), new Position(0, 8), false);
        grapple();
        apply(Command.action(Kind.END_TURN, mover));
        var path = List.of(new Position(0, 2));
        reject(Command.ready(carried, new ReadyInput(new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER,
                mover, new Position(1, 0)), ReadyResponseKind.MOVE, null, null, path, null)), "S2_COMBAT_MOVEMENT_ALLOWANCE");

        Combatant held = actor(carried);
        battle = Schema2CombatRules.replaceActor(battle, new Combatant(carried, held.position(), held.side(), false,
                held.reactionAvailable(), held.movementSpentHalfFeet(), 30, held.disengaged(), held.dodging(), null, true));
        reject(Command.move(carried, path), "S2_COMBAT_MOVEMENT_ALLOWANCE");
        reject(Command.action(Kind.STAND_UP, carried), "S2_COMBAT_CANNOT_STAND");
    }

    @Test void captureEndsDexteritySaveDodgeEvenWhenReleasedBeforeTheNextTurn() {
        start(new Position(0, 0), new Position(0, 1), new Position(0, 8), false);
        apply(Command.action(Kind.END_TURN, mover));
        apply(Command.action(Kind.DODGE, carried));
        apply(Command.action(Kind.END_TURN, carried));
        apply(Command.action(Kind.END_TURN, reactor));
        assertTrue(dexteritySaveAdvantage());

        UUID relation = grapple();
        assertFalse(dexteritySaveAdvantage());
        apply(Command.melee(Kind.RELEASE_GRAPPLE, mover, null, null, null,
                new MeleeInput(null, null, relation, null, null, null)));
        assertFalse(dexteritySaveAdvantage());
    }

    @Test void dragSpeedExceptionRequiresTwoSizeCategoriesAndPreservesOddHalfFeet() {
        assertEquals(25, Schema2ActionFlowRules.dragAllowanceHalfFeet(25, CreatureSize.MEDIUM, CreatureSize.SMALL));
        assertEquals(50, Schema2ActionFlowRules.dragAllowanceHalfFeet(25, CreatureSize.MEDIUM, CreatureSize.TINY));
        assertEquals(25, Schema2ActionFlowRules.dragAllowanceHalfFeet(25, CreatureSize.MEDIUM, CreatureSize.LARGE));
    }

    private void start(Position origin, Position heldOrigin, Position reactorOrigin, boolean lowHealth) {
        var material = material();
        Context moving = context(material, campaign, mover);
        if (lowHealth) moving = withState(moving, withHealth(moving.character(), 1, HealthState.INITIAL));
        roster.put(mover, moving);
        roster.put(carried, context(material, campaign, carried));
        roster.put(reactor, context(material, campaign, reactor));
        apply(Command.start(List.of(new Deployment(mover, origin, 0), new Deployment(carried, heldOrigin, 1),
                new Deployment(reactor, reactorOrigin, 1)), List.of(mover,carried,reactor)), 20, 10, 5);
    }

    private UUID grapple() {
        apply(Command.action(Kind.BEGIN_ATTACK, mover));
        apply(Command.melee(Kind.GRAPPLE, mover, carried, null, null,
                new MeleeInput(actor(mover).sequence().sequenceKey(), 0, null, HandSlot.OFF_HAND, null, null)));
        Flow flow = battle.activeFlow();
        apply(Command.melee(Kind.CHOOSE_MELEE_DEFENSE, carried, null, flow.flowKey(), flow.leafFrameKey(),
                new MeleeInput(null, null, null, null, ContestSkill.ACROBATICS, null)), 19, 2);
        return battle.meleeControl().grapples().getFirst().grappleKey();
    }

    private void readyAttackOnEntry(UUID reactor, UUID entering, Position location) {
        UUID weapon = roster.get(reactor).character().equipment().getFirst().itemId();
        apply(Command.ready(reactor, new ReadyInput(new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER,
                entering, location), ReadyResponseKind.ATTACK, mover, weapon, List.of(), null)));
    }

    private void passAndContinue(UUID reactor, UUID mover) {
        Flow flow = battle.activeFlow();
        Window window = flow.windows().getLast();
        apply(Command.reaction(Kind.PASS_REACTION, reactor, null, flow.flowKey(), flow.leafFrameKey(),
                window.windowKey(), window.opportunities().getFirst().opportunityKey()));
        apply(Command.continuation(mover, flow.flowKey(), battle.activeFlow().leafFrameKey(), window.windowKey()));
    }

    private Combatant actor(UUID actor) {
        return battle.combatants().stream().filter(value -> value.characterId().equals(actor)).findFirst().orElseThrow();
    }

    private boolean dexteritySaveAdvantage() {
        return Schema2SavingThrowRules.prepare(roster, battle, new CreatureRef(CreatureKind.CHARACTER, carried),
                "ability.dexterity").advantage();
    }

    private void reject(Command command, String reason) {
        assertEquals(reason, assertThrows(IllegalArgumentException.class,
                () -> rules.preflight(UUID.randomUUID(), combat, campaign, roster, battle, command)).getMessage());
    }

    private void apply(Command command, Integer... scripted) {
        var rolls = new ArrayDeque<>(Arrays.asList(scripted));
        UUID operation = UUID.randomUUID();
        var prepared = rules.preflight(operation, combat, campaign, roster, battle, command);
        var result = rules.resolve(prepared, sides -> {
            assertFalse(rolls.isEmpty(), "Unexpected live d" + sides);
            return rolls.removeFirst();
        });
        assertTrue(rolls.isEmpty(), "Unused scripted dice");
        rules.verify(operation, combat, campaign, roster, battle, command, result);
        battle = result.after();
        for (HealthChange change : result.healthChanges()) {
            Context current = roster.get(change.characterId());
            roster.put(change.characterId(), current.after(change.after(), change.timeAdvance() ? change.seconds() : 0));
        }
    }
}
