package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.module.composition.Schema2SpellFixtures;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.CampaignDomainCodec;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullAbilities;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullPlan;
import static com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.DROP_PRONE_SQLITE_REQUIRED;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static com.dndtool.service.Schema2CombatTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ProneRulesTest {
    private final Schema2CombatRules rules = new Schema2CombatRules();
    private final UUID campaign = UUID.randomUUID(), combat = UUID.randomUUID();
    private final UUID mover = UUID.randomUUID(), opponent = UUID.randomUUID();
    private final Map<UUID, Context> roster = new HashMap<>();
    private Battle battle;

    @Test void dropOnlyChangesPostureAndPreservesAnOpenAttackSequence() {
        start(30, new Position(1, 0));
        reject("S2_COMBAT_NOT_CURRENT_TURN", Command.action(Kind.DROP_PRONE, opponent));
        reject("S2_COMBAT_CHARACTER_NOT_FOUND", Command.action(Kind.DROP_PRONE, UUID.randomUUID()));
        reject("S2_COMBAT_COMMAND", new Command(Kind.DROP_PRONE, mover, opponent, null,
                List.of(), List.of(), List.of(), null, null, null, null));

        apply(Command.action(Kind.BEGIN_ATTACK, mover));
        Combatant before = actor(mover);
        Resolution dropped = apply(Command.action(Kind.DROP_PRONE, mover));
        assertEquals(new Combatant(mover, before.position(), before.side(), before.actionAvailable(),
                before.reactionAvailable(), before.movementSpentHalfFeet(), before.movementBonus(),
                before.disengaged(), before.dodging(), before.sequence(), true), actor(mover));
        assertEquals(List.of(new CombatFact("DROP_PRONE", mover, combat, null)), dropped.facts());
        assertTrue(dropped.healthChanges().isEmpty());
        reject("S2_COMBAT_ALREADY_PRONE", Command.action(Kind.DROP_PRONE, mover));

        apply(Command.attack(mover, opponent, item(mover)), 20, 1);
        AttackAttempt proneAttack = battle.flows().getLast().attacks().getFirst();
        assertTrue(proneAttack.disadvantage());
        assertEquals(1, proneAttack.selectedD20());
        apply(Command.action(Kind.END_TURN, mover));
        apply(Command.action(Kind.BEGIN_ATTACK, opponent));
        apply(Command.attack(opponent, mover, item(opponent)), 1, 2);
        AttackAttempt attackAgainstProne = battle.flows().getLast().attacks().getFirst();
        assertTrue(attackAgainstProne.advantage());
        assertEquals(2, attackAgainstProne.selectedD20());
        apply(Command.action(Kind.END_COMBAT, opponent));
        reject("S2_COMBAT_REQUIRED", Command.action(Kind.DROP_PRONE, mover));
        assertTrue(actor(mover).prone());
    }

    @Test void dashExtendsTheSameMovementPoolWithoutChangingCrawlCost() {
        start(30, new Position(0, 8));
        apply(Command.action(Kind.DROP_PRONE, mover));
        List<Position> path = List.of(cell(1), cell(2), cell(3), cell(4), cell(5), cell(6));
        reject("S2_COMBAT_MOVEMENT_ALLOWANCE", Command.move(mover, path.subList(0, 4)));
        assertEquals(0, actor(mover).movementSpentHalfFeet());
        assertTrue(actor(mover).actionAvailable());

        apply(Command.action(Kind.DASH, mover));
        apply(Command.move(mover, path));
        assertEquals(120, actor(mover).movementSpentHalfFeet());
        assertEquals(30, battle.flows().getLast().movement().spent());
        reject("S2_COMBAT_MOVEMENT_ALLOWANCE", Command.move(mover, List.of(cell(7))));
        reject("S2_COMBAT_CANNOT_STAND", Command.action(Kind.STAND_UP, mover));
    }

    @Test void standingAtSpeedTwentyFiveKeepsTheExactFractionalBudget() {
        start(25, new Position(0, 8));
        apply(Command.action(Kind.DROP_PRONE, mover));
        apply(Command.move(mover, List.of(cell(1))));
        assertEquals(20, actor(mover).movementSpentHalfFeet());
        apply(Command.action(Kind.STAND_UP, mover));
        assertFalse(actor(mover).prone());
        assertEquals(45, actor(mover).movementSpentHalfFeet());
        assertTrue(actor(mover).actionAvailable());
        assertTrue(actor(mover).reactionAvailable());
        reject("S2_COMBAT_MOVEMENT_ALLOWANCE", Command.move(mover, List.of(cell(2))));
    }

    @Test void explorationStandPreservesTurnBudgetAndAdvancesOnlySceneTime() {
        start(30, new Position(0, 8));
        apply(Command.action(Kind.DROP_PRONE, mover));
        apply(Command.move(mover, List.of(cell(1))));
        apply(Command.action(Kind.END_TURN, mover));
        apply(Command.action(Kind.END_COMBAT, opponent));
        reject("S2_SCENE_OBLIGATIONS_PENDING", Command.action(Kind.CLOSE_SCENE, opponent));
        reject("S2_EXPLORATION_MOVEMENT_UNSUPPORTED", Command.exploreMove(mover, List.of(cell(2))));
        long turn = battle.turnNumber();
        int round = battle.round();
        Combatant before = actor(mover);

        var stood = apply(Command.action(Kind.STAND_UP, mover));
        assertEquals(Status.EXPLORATION, battle.status());
        assertEquals(turn, battle.turnNumber());
        assertEquals(round, battle.round());
        assertEquals(new Combatant(mover, before.position(), before.side(), before.actionAvailable(),
                before.reactionAvailable(), 20, before.movementBonus(), before.disengaged(), before.dodging(),
                before.sequence(), false), actor(mover));
        assertEquals(2, stood.healthChanges().size());
        assertTrue(stood.healthChanges().stream().allMatch(change -> change.timeAdvance() && change.seconds() == 6));
        assertEquals(12, roster.get(mover).sceneSecond());
        reject("S2_COMBAT_CANNOT_STAND", Command.action(Kind.STAND_UP, mover));
        apply(Command.exploreMove(mover, List.of(cell(2))));
    }

    @Test void zeroSpeedStillAllowsDroppingAndPreventsStandingInEitherMode() {
        var material = material(10, 5);
        var initial = fullPlan(material, Schema2PreparationIdentity.random(), campaign, mover);
        var scores = new HashMap<>(fullAbilities());
        scores.put("ability.strength", 10);
        scores.put("ability.intelligence", 15);
        var state = new Schema2CharacterProgressionRules().initial(material, initial, UUID.randomUUID(), scores, INITIAL_CHOICES);
        var equipment = new Schema2CharacterEquipmentRules();
        UUID armor = UUID.randomUUID();
        state = equipment.apply(material, state, equipment("ITEM_ACQUIRED", armor, ITEM_HEAVY, "CARRIED"), armor);
        state = equipment.apply(material, state, equipment("ITEM_EQUIPPED", armor, null, "WORN"), UUID.randomUUID());
        Context armored = new Context(material, initial, state);
        Context other = context(material, campaign, opponent);
        roster.put(mover, armored);
        roster.put(opponent, new Context(material, other.initial(), other.character(), armored.sceneSecond()));

        apply(Command.start(List.of(new Deployment(mover, cell(0), 0), new Deployment(opponent, cell(2), 1)),
                List.of(mover, opponent)), 20, 20, 10);
        assertEquals(0, rules.speed(armored));
        apply(Command.action(Kind.DROP_PRONE, mover));
        reject("S2_COMBAT_CANNOT_STAND", Command.action(Kind.STAND_UP, mover));
        apply(Command.action(Kind.END_COMBAT, mover));
        reject("S2_COMBAT_CANNOT_STAND", Command.action(Kind.STAND_UP, mover));
    }

    @Test void crawlPausesBeforeCrossingAndStopsIfOpportunityAttackDropsMover() {
        start(30, new Position(0, 1));
        apply(Command.action(Kind.DROP_PRONE, mover));
        apply(Command.move(mover, List.of(cell(1), cell(2))));
        assertEquals(cell(1), actor(mover).position());
        assertEquals(20, actor(mover).movementSpentHalfFeet());
        assertEquals(1, battle.activeFlow().movement().cursor());
        reject("ACTION_FLOW_BUSY", Command.action(Kind.STAND_UP, mover));
        reject("ACTION_FLOW_BUSY", Command.action(Kind.DROP_PRONE, mover));

        Flow flow = battle.activeFlow();
        Window window = flow.windows().getLast();
        Opportunity opportunity = window.opportunities().getFirst();
        apply(Command.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, opponent, item(opponent),
                flow.flowKey(), flow.leafFrameKey(), window.windowKey(), opportunity.opportunityKey()), 5, 20, 5, 5);
        assertEquals(0, roster.get(mover).character().hitPoints());
        apply(resume(mover));
        Movement stopped = battle.flows().getLast().movement();
        assertEquals(MovementStatus.STOPPED, stopped.status());
        assertEquals(5, stopped.spent());
        assertEquals(cell(1), actor(mover).position());
        assertEquals(20, actor(mover).movementSpentHalfFeet());
        reject("S2_COMBAT_ACTOR_INACTIVE", Command.action(Kind.DROP_PRONE, mover));
    }

    @Test void readiedCrawlUsesItsReactionAllowanceAcrossAnOpportunityWindow() {
        start(30, new Position(0, 1));
        apply(Command.move(mover, List.of(cell(1))));
        apply(Command.action(Kind.DROP_PRONE, mover));
        reject("S2_COMBAT_MOVEMENT_ALLOWANCE", readyMove(cell(2), cell(3), cell(4), cell(5)));
        apply(readyMove(cell(2), cell(3), cell(4)));
        apply(Command.action(Kind.END_TURN, mover));
        apply(Command.action(Kind.BEGIN_ATTACK, opponent));
        apply(Command.attack(opponent, mover, item(opponent)), 1, 1);

        Flow flow = battle.activeFlow();
        Window window = flow.windows().getLast();
        Opportunity ready = window.opportunities().getFirst();
        apply(Command.releaseReady(mover, ready.options().getFirst().readyKey(),
                flow.flowKey(), flow.leafFrameKey(), window.windowKey(), ready.opportunityKey()));
        assertEquals(cell(1), actor(mover).position());
        assertFalse(actor(mover).reactionAvailable());
        assertEquals(10, actor(mover).movementSpentHalfFeet());
        flow = battle.activeFlow();
        window = flow.windows().getLast();
        apply(Command.reaction(Kind.PASS_REACTION, opponent, null, flow.flowKey(), flow.leafFrameKey(),
                window.windowKey(), window.opportunities().getFirst().opportunityKey()));
        apply(resume(mover));
        apply(resume(opponent));

        Movement movement = battle.flows().getLast().movements().getFirst();
        assertTrue(movement.readied());
        assertEquals(30, movement.allowanceFeet());
        assertEquals(15, movement.spent());
        assertEquals(cell(4), actor(mover).position());
        assertEquals(10, actor(mover).movementSpentHalfFeet());
        assertTrue(actor(mover).prone());
    }

    @Test void readyMoveRechecksTheCurrentPostureBeforeOfferingARelease() {
        start(30, new Position(0, 1));
        apply(readyMove(cell(1), cell(2), cell(3), cell(4)));
        apply(Command.action(Kind.DROP_PRONE, mover));
        apply(Command.action(Kind.END_TURN, mover));
        apply(Command.action(Kind.BEGIN_ATTACK, opponent));
        apply(Command.attack(opponent, mover, item(opponent)), 1, 1);

        assertNull(battle.activeFlow());
        assertEquals(ReadyStatus.ARMED, battle.readyPreparations().getFirst().status());
        assertEquals(cell(0), actor(mover).position());
        assertTrue(actor(mover).reactionAvailable());
    }

    @Test void aSavedInterruptedMoveDoesNotResumeAfterANestedReadyHeal() {
        UUID healer = UUID.randomUUID();
        var material = Schema2SpellFixtures.projection();
        for (UUID id : List.of(mover, opponent, healer)) {
            var initial = fullPlan(material, Schema2PreparationIdentity.random(), campaign, id);
            var state = new Schema2CharacterProgressionRules().initial(material, initial, UUID.randomUUID(),
                    fullAbilities(), Schema2SpellFixtures.initialChoices());
            roster.put(id, new Context(material, initial, state));
        }
        Context fighter = roster.get(opponent);
        var equipment = new Schema2CharacterEquipmentRules();
        UUID weapon = UUID.randomUUID();
        var equipped = equipment.apply(material, fighter.character(), equipment("ITEM_ACQUIRED", weapon, ITEM_WEAPON, "CARRIED"), weapon);
        equipped = equipment.apply(material, equipped, equipment("ITEM_EQUIPPED", weapon, null, "MAIN_HAND"), UUID.randomUUID());
        roster.put(opponent, withState(fighter, equipped));

        apply(Command.start(List.of(new Deployment(healer, new Position(1, -1), 0),
                new Deployment(mover, cell(0), 0), new Deployment(opponent, new Position(0, 1), 1)),
                List.of(healer, mover, opponent)), 20, 15, 10);
        assertEquals(healer, battle.activeActor());
        var cast = new CastInput(Schema2SpellFixtures.CASTING, Schema2SpellFixtures.CURE_WOUNDS, List.of(mover), null);
        apply(Command.ready(healer, new ReadyInput(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED, opponent, null),
                ReadyResponseKind.SPELL, null, null, List.of(), cast)));
        apply(Command.action(Kind.END_TURN, healer));
        apply(Command.move(mover, List.of(cell(1), cell(2))));
        Flow flow = battle.activeFlow();
        Window window = flow.windows().getLast();
        apply(Command.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, opponent, weapon, flow.flowKey(), flow.leafFrameKey(),
                window.windowKey(), window.opportunities().getFirst().opportunityKey()), 20, 5, 5);
        assertEquals(0, roster.get(mover).character().hitPoints());
        assertEquals(MovementStatus.STOPPED, battle.activeFlow().movement().status());
        assertEquals(10, actor(mover).movementSpentHalfFeet());

        battle = CampaignDomainCodec.decode(CampaignDomainCodec.encode(battle), Battle.class);
        flow = battle.activeFlow();
        window = flow.windows().getLast();
        Opportunity ready = window.opportunities().getFirst();
        apply(Command.releaseReady(healer, ready.options().getFirst().readyKey(),
                flow.flowKey(), flow.leafFrameKey(), window.windowKey(), ready.opportunityKey()), 8);
        assertTrue(roster.get(mover).character().hitPoints() > 0);
        apply(resume(opponent));
        apply(resume(mover));
        assertNull(battle.activeFlow());
        assertTrue(actor(mover).prone());
        assertEquals(cell(1), actor(mover).position());
        assertEquals(10, actor(mover).movementSpentHalfFeet());
        assertEquals("ACTOR_INACTIVE_OR_NO_MOVEMENT", battle.flows().getLast().movement().stopReason());
    }

    @Test void mysqlRejectsDropCommandsAndHistoriesBeforeConnecting() {
        var material = material();
        var engine = new CampaignDomainEngine();
        var records = List.of(initialRecord(material, engine, mover), initialRecord(material, engine, opponent));
        var start = Command.start(List.of(new Deployment(mover, cell(0), 0), new Deployment(opponent, cell(1), 1)),
                List.of(mover, opponent));
        var started = engine.executeCombat(material, records, null, combat, UUID.randomUUID(), start, sides -> 10);
        Command drop = Command.action(Kind.DROP_PRONE, started.history().current().activeActor());
        var versions = started.characters().stream().collect(Collectors.toMap(record -> record.initial().characterId(),
                record -> record.history().current().version()));
        var request = new Schema2CombatRuntimeService.Request(UUID.randomUUID(), combat, campaign, Schema2RulesBinding.from(material),
                started.history().members(), started.history().current().version(), versions, drop);
        var source = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, args) -> fail("unexpected connection method " + method.getName()));
        var service = new Schema2CombatRuntimeService(source, sides -> fail("unexpected d" + sides));
        assertEquals(DROP_PRONE_SQLITE_REQUIRED, service.execute(request).code());
        assertEquals(DROP_PRONE_SQLITE_REQUIRED, service.inspect(request).code());

        var dropped = engine.executeCombat(material, started.characters(), started.history(), combat,
                request.operationId(), drop, sides -> fail("unexpected d" + sides));
        var save = new Schema2CombatRuntimeService.Save(dropped.history(),
                dropped.characters().stream().map(CampaignDomainEngine.CharacterRecord::history).toList());
        assertEquals(DROP_PRONE_SQLITE_REQUIRED, service.restore(save).code());
        assertEquals(DROP_PRONE_SQLITE_REQUIRED, service.inspectRestore(save).code());
    }

    private CampaignDomainEngine.CharacterRecord initialRecord(ProductionSchema2Projection material,
            CampaignDomainEngine engine, UUID id) {
        var initial = fullPlan(material, Schema2PreparationIdentity.random(), campaign, id);
        var history = engine.initial(material, initial, UUID.randomUUID(), fullAbilities(), INITIAL_CHOICES, UUID.randomUUID());
        return new CampaignDomainEngine.CharacterRecord("Tester", "PC", initial, history);
    }

    private Command readyMove(Position... path) {
        return Command.ready(mover, new ReadyInput(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED, opponent, null),
                ReadyResponseKind.MOVE, null, null, List.of(path), null));
    }

    private Command resume(UUID actor) {
        Flow flow = battle.activeFlow();
        Frame leaf = flow.frames().stream().filter(frame -> frame.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
        return new Command(Kind.CONTINUE_FLOW, actor, null, null, List.of(), List.of(), List.of(),
                flow.flowKey(), flow.leafFrameKey(), leaf.windowKey(), null);
    }

    private void start(int speed, Position opponentPosition) {
        var material = material(speed, 5);
        roster.put(mover, context(material, campaign, mover));
        roster.put(opponent, context(material, campaign, opponent));
        apply(Command.start(List.of(new Deployment(mover, cell(0), 0), new Deployment(opponent, opponentPosition, 1)),
                List.of(mover, opponent)), 20, 10);
    }

    private static Position cell(int x) { return new Position(x, 0); }
    private UUID item(UUID id) { return roster.get(id).character().equipment().getFirst().itemId(); }

    private Combatant actor(UUID id) {
        return battle.combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow();
    }

    private void reject(String code, Command command) {
        assertEquals(code, assertThrows(IllegalArgumentException.class,
                () -> rules.preflight(UUID.randomUUID(), combat, campaign, roster, battle, command)).getMessage());
    }

    private Resolution apply(Command command, int... rolls) {
        UUID operation = UUID.randomUUID();
        var prepared = rules.preflight(operation, combat, campaign, roster, battle, command);
        AtomicInteger cursor = new AtomicInteger();
        Resolution resolution = rules.resolve(prepared, sides -> {
            assertTrue(cursor.get() < rolls.length, "unexpected d" + sides);
            return rolls[cursor.getAndIncrement()];
        });
        assertEquals(rolls.length, cursor.get());
        rules.verify(operation, combat, campaign, roster, battle, command, resolution);
        for (HealthChange change : resolution.healthChanges())
            roster.computeIfPresent(change.characterId(), (id, context) ->
                    context.after(change.after(), change.timeAdvance() ? change.seconds() : 0));
        battle = resolution.after();
        return resolution;
    }
}
