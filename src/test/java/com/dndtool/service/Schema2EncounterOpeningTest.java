package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.BundledCampaignRules;
import com.dndtool.service.storage.CampaignRuleContext;
import com.dndtool.service.storage.CampaignRuleSupport;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.GOBLIN_MONSTER;
import static com.dndtool.service.Schema2CheckTestFixture.equipment;
import static org.junit.jupiter.api.Assertions.*;

class Schema2EncounterOpeningTest {
    private final CampaignRuleContext catalog = CampaignRuleSupport.validate(BundledCampaignRules.projection());
    private final Schema2CombatRules rules = new Schema2CombatRules();
    private final UUID campaign = UUID.randomUUID(), battleId = UUID.randomUUID();
    private final Map<UUID, Context> roster = new HashMap<>();
    private Battle battle;

    @Test void surprisedMonsterLosesItsFirstTurnAndCanReactLaterInTheSameRound() {
        UUID first = character("fighter"), last = character("fighter"), goblin = UUID.randomUUID();
        var start = Command.start(List.of(at(first, 0, 0, 0), at(last, 0, 2, 0)),
                List.of(monster(goblin, GOBLIN_MONSTER, 1, 1, 1)), List.of(first, goblin, last))
                .withOpening(new OpeningInput(List.of(group(first, last)), List.of()));
        var opening = apply(start, 20, 20, 1, 12);
        assertEquals(List.of(new Roll("OPENING_STEALTH", first, 20, 20), new Roll("INITIATIVE", first, 20, 20),
                new Roll("INITIATIVE", last, 20, 1), new Roll("INITIATIVE", goblin, 20, 12)), opening.rolls());
        assertTrue(battle.surprised(goblin));
        assertFalse(actor(goblin).reactionAvailable());
        assertTrue(Schema2ObservationRules.hidden(battle, goblin, first));
        assertNull(Schema2ObservationRules.knownPosition(battle, goblin, first));
        assertTrue(battle.observation().cells().isEmpty());

        apply(Command.move(first, List.of(new Position(1, 0), new Position(2, 0), new Position(3, 0))));
        assertNull(battle.activeFlow());
        assertTrue(Schema2ObservationRules.visible(battle, goblin, first));
        apply(Command.action(Kind.END_TURN, first));
        assertEquals(goblin, battle.activeActor());
        assertEquals(0, actor(goblin).actionsRemaining());
        assertEquals(0, actor(goblin).bonusActionsRemaining());
        assertEquals(0, rules.speed(roster, battle, goblin));
        reject(Command.action(Kind.DASH, goblin), "S2_COMBAT_ACTION_UNAVAILABLE");
        reject(Command.action(Kind.NIMBLE_DISENGAGE, goblin), "S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
        reject(Command.move(goblin, List.of(new Position(1, 2))), "S2_COMBAT_MOVEMENT_ALLOWANCE");

        apply(Command.action(Kind.END_TURN, goblin));
        assertEquals(last, battle.activeActor());
        assertEquals(1, battle.round());
        assertTrue(initiative(goblin).surprised());
        assertFalse(battle.surprised(goblin));
        assertTrue(actor(goblin).reactionAvailable());
        apply(Command.move(last, List.of(new Position(1, 2), new Position(2, 2), new Position(3, 2))));
        var opportunity = battle.activeFlow().windows().getLast().opportunities().getFirst();
        assertEquals(goblin, opportunity.actorId());
        assertEquals(ReactionKind.OPPORTUNITY_ATTACK, opportunity.options().getFirst().kind());
    }

    @Test void stealthSharesBothRawDiceAndAppliesFrozenModifiersAndDisadvantagePerMember() {
        UUID normal = character("cleric"), firstArmored = armoredFighter(), secondArmored = armoredFighter();
        UUID goblin = UUID.randomUUID();
        var start = Command.start(List.of(at(normal, 0, 0, 0), at(firstArmored, 0, 1, 0), at(secondArmored, 0, 2, 0)),
                List.of(monster(goblin, GOBLIN_MONSTER, 3, 0, 1)), List.of(normal, firstArmored, secondArmored, goblin))
                .withOpening(new OpeningInput(List.of(group(normal, firstArmored, secondArmored)), List.of()));
        var result = apply(start, 15, 8, 4, 3, 5, 12);
        assertEquals(List.of(new Roll("OPENING_STEALTH", normal, 20, 15),
                new Roll("OPENING_STEALTH_SECOND", normal, 20, 8)), result.rolls().subList(0, 2));
        assertEquals(6, result.rolls().size());
        var checks = battle.observation().checks();
        assertEquals(List.of(normal, firstArmored, secondArmored), checks.stream().map(ObservationCheck::actorId).toList());
        assertTrue(checks.stream().allMatch(check -> check.rollSource().equals(normal)));
        assertEquals(new ObservationRoll(15, null, 15, 0, 0, false, 15), checks.getFirst().roll());
        assertEquals(new ObservationRoll(15, 8, 8, 1, 0, true, 9), checks.get(1).roll());
        assertEquals(checks.get(1).roll(), checks.getLast().roll());
        assertEquals(List.of(new ObservationContest(goblin, 9, null, false)), checks.get(1).contests());
        assertTrue(Schema2ObservationRules.hidden(battle, goblin, normal));
        assertTrue(Schema2ObservationRules.visible(battle, goblin, firstArmored));
        assertFalse(initiative(goblin).surprised());
        assertEquals(1, actor(goblin).actionsRemaining());
    }

    @Test void identicalMonstersShareInitiativeAndKeepTieBreakOrderAndIndependentTurns() {
        UUID player = character("fighter"), first = UUID.randomUUID(), second = UUID.randomUUID(), skeleton = UUID.randomUUID();
        RuleIdentity skeletonRule = new RuleIdentity("srd51_magic", "adventure.monster", "monster.skeleton");
        var base = Command.start(List.of(at(player, 0, 0, 0)),
                List.of(monster(first, GOBLIN_MONSTER, 1, 0, 1), monster(second, GOBLIN_MONSTER, 2, 0, 1),
                        monster(skeleton, skeletonRule, 3, 0, 1)), List.of(first, second, player, skeleton));
        reject(base.withOpening(new OpeningInput(List.of(), List.of(group(first, skeleton)))), "S2_COMBAT_OPENING_MONSTER");
        reject(base.withOpening(new OpeningInput(List.of(), List.of(group(first, player)))), "S2_COMBAT_OPENING_MONSTER");
        reject(base.withOpening(new OpeningInput(List.of(group(first, player)), List.of())), "S2_COMBAT_OPENING_GROUP");
        reject(base.withOpening(new OpeningInput(List.of(group(UUID.randomUUID())), List.of())), "S2_COMBAT_OPENING_MEMBER");
        assertThrows(IllegalArgumentException.class, () -> new OpeningInput(List.of(group(player), group(player)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new OpeningInput(List.of(), List.of(group(first))));

        var command = base.withOpening(new OpeningInput(List.of(), List.of(group(second, first))));
        var requestId = UUID.randomUUID();
        var binding = Schema2RulesBinding.from(catalog.projection());
        var character = roster.get(player).character();
        var members = List.of(new Schema2CombatHistory.Member(player, character.identity(), character.preparationOperationId()));
        var versions = Map.of(player, character.version());
        String ungrouped = new Schema2CombatRequest(requestId, battleId, campaign, binding, members, -1, versions, base, true).digest();
        assertEquals(ungrouped, new Schema2CombatRequest(requestId, battleId, campaign, binding, members, -1, versions,
                base.withOpening(new OpeningInput(List.of(), List.of())), true).digest());
        assertNotEquals(ungrouped, new Schema2CombatRequest(requestId, battleId, campaign, binding, members, -1, versions, command, true).digest());

        var result = apply(command, 11, 10, 9);
        assertEquals(List.of(new Roll("INITIATIVE", player, 20, 11), new Roll("INITIATIVE", second, 20, 10),
                new Roll("INITIATIVE", skeleton, 20, 9)), result.rolls());
        assertEquals(List.of(first, second, player, skeleton), battle.initiative().stream().map(Initiative::characterId).toList());
        assertEquals(new Initiative(first, 10, 2, 12, 0, false, second), initiative(first));
        assertEquals(new Initiative(second, 10, 2, 12, 1, false, second), initiative(second));
        assertTrue(battle.initiative().stream().noneMatch(Initiative::surprised));
        apply(Command.action(Kind.DODGE, first));
        apply(Command.action(Kind.END_TURN, first));
        assertEquals(second, battle.activeActor());
        assertTrue(actor(first).dodging());
        assertFalse(actor(second).dodging());
        assertEquals(1, actor(second).actionsRemaining());
    }

    @Test void aSurprisedCharacterCannotUseSecondWindOrVoluntarilyDropProne() {
        UUID fighter = character("fighter"), goblin = UUID.randomUUID();
        var start = Command.start(List.of(at(fighter, 0, 0, 0)), List.of(monster(goblin, GOBLIN_MONSTER, 1, 0, 1)),
                List.of(fighter, goblin)).withOpening(new OpeningInput(List.of(group(goblin)), List.of()));
        apply(start, 20, 20, 1);
        assertEquals(fighter, battle.activeActor());
        assertTrue(battle.surprised(fighter));
        reject(Command.action(Kind.SECOND_WIND, fighter), "S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
        reject(Command.action(Kind.DROP_PRONE, fighter), "S2_COMBAT_ACTOR_SURPRISED");
        apply(Command.action(Kind.END_TURN, fighter));
        apply(Command.action(Kind.END_TURN, goblin));
        assertEquals(fighter, battle.activeActor());
        assertEquals(2, battle.round());
        apply(Command.action(Kind.DROP_PRONE, fighter));
        assertTrue(actor(fighter).prone());
    }

    @Test void reenterUsesFreshDiceAndSearchFindsAnOpeningHiderDespiteThePublicAlly() {
        UUID hider = character("fighter"), publicAlly = character("fighter"), guard = character("cleric");
        var position = new Position(0, 0);
        var deployments = List.of(at(hider, 0, 0, 0), at(publicAlly, 0, 1, 0), at(guard, 0, 2, 1));
        var ties = List.of(hider, publicAlly, guard);
        apply(Command.start(deployments, ties), 20, 10, 1);
        apply(Command.action(Kind.END_COMBAT, hider));
        var command = Command.reenter(guard, ties).withOpening(new OpeningInput(List.of(group(hider)), List.of()));
        var opened = apply(command, 13, 5, 3, 20);
        assertEquals(Kind.REENTER_COMBAT, opened.after().observation().checks().getLast().kind());
        assertEquals(new Roll("OPENING_STEALTH", hider, 20, 13), opened.rolls().getFirst());
        assertEquals(guard, battle.activeActor());
        assertFalse(initiative(guard).surprised());
        assertTrue(Schema2ObservationRules.hidden(battle, guard, hider));
        assertEquals(position, Schema2ObservationRules.knownPosition(battle, guard, hider));
        var openingCheck = battle.observation().checks().getLast();
        var search = Command.action(Kind.SEARCH, guard);
        var conflict = assertThrows(IllegalArgumentException.class, () -> rules.resolve(
                rules.preflight(openingCheck.operationId(), battleId, campaign, roster, battle, search),
                sides -> fail("Unexpected d" + sides)));
        assertEquals("S2_COMBAT_OPERATION_CONFLICT", conflict.getMessage());
        apply(search, 20);
        assertEquals(List.of(new ObservationContest(hider, 14, openingCheck.operationId(), false)),
                battle.observation().checks().getLast().contests());
        assertTrue(Schema2ObservationRules.visible(battle, guard, hider));
    }

    private UUID character(String characterClass) {
        UUID id = UUID.randomUUID();
        var initial = catalog.initialPlan(campaign, id, Schema2PreparationIdentity.random(), characterClass, "acolyte");
        var state = new Schema2CharacterProgressionRules().initial(catalog.projection(), initial, UUID.randomUUID(),
                catalog.initialAbilities(characterClass), catalog.initialChoices(characterClass));
        roster.put(id, new Context(catalog.projection(), initial, state));
        return id;
    }

    private UUID armoredFighter() {
        UUID fighter = character("fighter"), armor = UUID.randomUUID();
        var context = roster.get(fighter);
        var items = new Schema2CharacterEquipmentRules();
        var rule = catalog.rule("equipment.item", "item.scale_mail");
        var carried = items.apply(catalog.projection(), context.character(), equipment("ITEM_ACQUIRED", armor, rule, "CARRIED"), armor);
        var worn = items.apply(catalog.projection(), carried, equipment("ITEM_EQUIPPED", armor, null, "WORN"), UUID.randomUUID());
        roster.put(fighter, context.after(worn, 0));
        return fighter;
    }

    private Resolution apply(Command command, int... dice) {
        UUID operation = UUID.randomUUID();
        var prepared = rules.preflight(operation, battleId, campaign, roster, battle, command);
        AtomicInteger cursor = new AtomicInteger();
        var result = rules.resolve(prepared, sides -> {
            assertTrue(cursor.get() < dice.length, "Unexpected d" + sides);
            return dice[cursor.getAndIncrement()];
        });
        assertEquals(dice.length, cursor.get());
        rules.verify(operation, battleId, campaign, roster, battle, command, result);
        for (HealthChange change : result.healthChanges())
            roster.computeIfPresent(change.characterId(), (id, before) -> before.after(change.after(), change.seconds()));
        battle = result.after();
        return result;
    }

    private void reject(Command command, String code) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> rules.preflight(UUID.randomUUID(), battleId, campaign, roster, battle, command));
        assertEquals(code, error.getMessage());
    }

    private static OpeningGroup group(UUID... members) { return new OpeningGroup(List.of(members)); }
    private static Deployment at(UUID id, int x, int y, int side) { return new Deployment(id, new Position(x, y), side); }
    private static MonsterDeployment monster(UUID id, RuleIdentity rule, int x, int y, int side) {
        return new MonsterDeployment(id, rule, new Position(x, y), side);
    }
    private Combatant actor(UUID id) { return Schema2CombatRules.combatant(battle, id); }
    private Initiative initiative(UUID id) {
        return battle.initiative().stream().filter(entry -> entry.characterId().equals(id)).findFirst().orElseThrow();
    }
}
