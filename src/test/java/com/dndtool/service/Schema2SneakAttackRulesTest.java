package com.dndtool.service;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.HealthState;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.storage.CampaignDomainData;
import com.dndtool.service.storage.CampaignRuleContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.dndtool.service.Schema2CheckTestFixture.equipment;
import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SneakAttackRulesTest {
    private static final Map<String, Integer> STRONG_ROGUE = Map.of("ability.strength", 15, "ability.dexterity", 8);
    private static final CampaignRuleContext DELIVERED_RULES = deliveredRules();

    @Test void strongerStrengthStillQualifiesWithFinesseAndTheCombinedPacketIsResistedOnce() {
        var game = new Game();
        UUID rogue = game.character("rogue", STRONG_ROGUE);
        UUID witness = game.character("fighter", Map.of());
        UUID target = UUID.randomUUID();
        UUID rapier = game.equip(rogue, "item.rapier");
        game.apply(Command.start(List.of(at(rogue, 0, 0), at(witness, 2, 2)),
                List.of(new MonsterDeployment(target, game.catalog.rule("adventure.monster", "monster.mummy"), new Position(1, 0), 1)),
                List.of(rogue, witness, target)), 20, 8, 1);
        int hp = Schema2MonsterRules.instance(game.battle, target).hitPoints();
        game.apply(Command.action(Kind.BEGIN_ATTACK, rogue));
        var prepared = game.combat.preflight(UUID.randomUUID(), game.combatId, game.campaign,
                game.roster, game.battle, Command.attack(rogue, target, rapier));
        assertEquals(1, prepared.requiredSuffixEvents());
        game.apply(Command.attack(rogue, target, rapier), 15);
        assertEquals(2, game.attack().abilityModifier());
        assertEquals(witness, game.attack().sneakAttack().nearbyEnemyId());
        assertEquals(hp, Schema2MonsterRules.instance(game.battle, target).hitPoints());
        var packet = game.apply(game.choice(Kind.USE_SNEAK_ATTACK, rogue), 3, 3);
        assertTrue(packet.healthChanges().isEmpty());
        assertEquals(4, game.attack().damageAmount());
        assertEquals(hp - 4, Schema2MonsterRules.instance(game.battle, target).hitPoints());
        assertEquals(List.of(new Roll("DAMAGE", rogue, 8, 3), new Roll("SNEAK_ATTACK_DAMAGE", rogue, 6, 3)),
                packet.rolls());

        AttackAttempt used = game.attack();
        long previousTurn = used.sneakAttack().turnNumber();
        game.apply(Command.action(Kind.END_COMBAT, rogue));
        game.apply(Command.reenter(rogue, List.of(rogue, witness, target)), 20, 8, 1);
        game.apply(Command.action(Kind.BEGIN_ATTACK, rogue));
        game.apply(Command.attack(rogue, target, rapier), 15);
        assertEquals(previousTurn + 1, game.attack().sneakAttack().turnNumber());
        game.apply(game.choice(Kind.USE_SNEAK_ATTACK, rogue), 3, 3);
        Flow last = game.battle.flows().getLast();
        var second = game.attack();
        var forged = second.withOutcome(second.finalArmorClass(), second.hit(), second.critical(), second.damageDice(),
                second.damageAmount(), second.status(), null, null, used.sneakAttack());
        var duplicate = new Flow(last.flowKey(), last.rootFrameKey(), last.leafFrameKey(), last.status(),
                last.frames(), last.windows(), last.movements(), List.of(forged), last.casts(), last.savingThrows(),
                last.readyReleases(), last.abilityChecks());
        Battle b = game.battle;
        var tampered = Schema2ActionFlowRules.rebuild(b, b.combatants(),
                b.flows().stream().map(flow -> flow.equals(last) ? duplicate : flow).toList(),
                b.readyPreparations(), b.triggerFacts(), b.shields(), b.concentrationHolders(), b.version());
        assertEquals("ACTION_FLOW_FROZEN_DATA_INVALID",
                assertThrows(IllegalArgumentException.class, () -> game.combat.validateState(tampered)).getMessage());
    }

    @Test void criticalUsesOneDamageEventAndOneConcentrationSaveAtTheCombinedDc() {
        var game = new Game();
        UUID cleric = game.character("cleric", Map.of());
        long hp = game.roster.get(cleric).character().hitPoints();
        game.setHealth(cleric, hp, new HealthState(30, 0, 0, false, false, null));
        UUID rogue = game.character("rogue", STRONG_ROGUE);
        UUID witness = game.character("fighter", Map.of());
        UUID rapier = game.equip(rogue, "item.rapier");
        UUID symbol = game.equip(cleric, "item.holy_symbol");
        game.start(at(cleric, 1, 1), at(rogue, 0, 0), at(witness, 2, 0));
        var casting = game.catalog.rule("character.spellcasting", "spellcasting.life_domain");
        var bless = game.catalog.rule("adventure.spell", "spell.bless");
        game.apply(Command.cast(cleric, new CastInput(casting, bless, List.of(cleric), symbol)));
        game.apply(Command.action(Kind.END_TURN, cleric));
        game.apply(Command.action(Kind.BEGIN_ATTACK, rogue));
        game.apply(Command.attack(rogue, cleric, rapier), 20);
        assertEquals(SneakAttackStatus.PENDING, game.attack().sneakAttack().status());
        var result = game.apply(game.choice(Kind.USE_SNEAK_ATTACK, rogue), 8, 8, 4, 2, 12, 3);
        assertEquals(hp, game.roster.get(cleric).character().hitPoints());
        assertEquals(6, game.roster.get(cleric).character().health().temporaryHitPoints());
        assertEquals(24, result.healthChanges().getFirst().damageAmount());
        assertEquals(1, result.healthChanges().size());
        var saves = game.battle.flows().getLast().savingThrows();
        assertEquals(1, saves.size());
        assertEquals(12, saves.getFirst().difficultyClass());
        assertEquals(12, saves.getFirst().d20());
        assertEquals(3, saves.getFirst().blessingD4());
        assertTrue(saves.getFirst().success());
        assertEquals(List.of("DAMAGE", "DAMAGE", "SNEAK_ATTACK_DAMAGE", "SNEAK_ATTACK_DAMAGE",
                "CONCENTRATION_D20", "BLESS_CONCENTRATION"), result.rolls().stream().map(Roll::purpose).toList());
    }

    @Test void anAlreadyLaunchedAttackRetainsTheDroppedWeaponProfile() {
        var game = new Game();
        UUID rogue = game.character("rogue", Map.of());
        UUID target = game.character("fighter", Map.of());
        UUID witness = game.character("fighter", Map.of());
        UUID rapier = game.equip(rogue, "item.rapier");
        game.start(at(rogue, 0, 0), at(target, 1, 1), at(witness, 2, 0));
        game.apply(Command.action(Kind.BEGIN_ATTACK, rogue));
        game.apply(Command.attack(rogue, target, rapier), 15);
        long originalHp = game.roster.get(rogue).character().hitPoints();
        game.setHealth(rogue, 0, HealthState.INITIAL);
        game.replace(rogue, Schema2CombatEquipmentFacts.drop(game.roster.get(rogue).character(), game.combatId,
                new Position(0, 0)));
        game.setHealth(rogue, originalHp, HealthState.INITIAL);
        assertEquals("S2_COMBAT_WEAPON_NOT_PREPARED", assertThrows(IllegalArgumentException.class,
                () -> game.combat.weapon(game.roster.get(rogue), rapier)).getMessage());
        var result = game.apply(game.choice(Kind.USE_SNEAK_ATTACK, rogue), 3, 2);
        assertEquals(List.of(new Roll("DAMAGE", rogue, 8, 3), new Roll("SNEAK_ATTACK_DAMAGE", rogue, 6, 2)),
                result.rolls());
        assertEquals(SneakAttackStatus.USED, game.attack().sneakAttack().status());
    }

    @Test void aReadiedWeaponAttackPausesAtTheChoiceAndCompletesOnTheTriggeringTurn() {
        var game = new Game();
        UUID rogue = game.character("rogue", Map.of());
        UUID target = game.character("fighter", Map.of());
        UUID witness = game.character("fighter", Map.of());
        UUID rapier = game.equip(rogue, "item.rapier");
        Position destination = new Position(1, 0);
        game.start(at(rogue, 0, 0), at(target, 2, 1), new Deployment(witness, new Position(2, 1), 0));
        var trigger = new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER, target, destination);
        game.apply(Command.ready(rogue, new ReadyInput(trigger, ReadyResponseKind.ATTACK, target, rapier, List.of(), null)));
        UUID ready = game.battle.readyPreparations().getFirst().readyKey();
        game.apply(Command.action(Kind.END_TURN, rogue));
        game.apply(Command.move(target, List.of(destination)));
        Flow moving = game.battle.activeFlow();
        Window window = moving.windows().getLast();
        game.apply(Command.releaseReady(rogue, ready, moving.flowKey(), moving.leafFrameKey(), window.windowKey(),
                window.opportunities().getFirst().opportunityKey()), 15);
        assertEquals(target, game.battle.activeActor());
        assertEquals(game.battle.turnNumber(), game.attack().sneakAttack().turnNumber());
        assertEquals(witness, game.attack().sneakAttack().nearbyEnemyId());
        assertFalse(game.attack().opportunityAttack());
        assertNull(game.attack().sequenceKey());
        assertFalse(game.battle.activeFlow().readyReleases().getLast().completed());
        assertFalse(Schema2CombatRules.combatant(game.battle, rogue).reactionAvailable());
        game.apply(game.choice(Kind.USE_SNEAK_ATTACK, rogue), 2, 3);
        Flow resumed = game.battle.activeFlow();
        assertTrue(resumed.readyReleases().getLast().completed());
        assertEquals(SneakAttackStatus.USED, game.attack().sneakAttack().status());
        game.apply(Command.continuation(target, resumed.flowKey(), resumed.leafFrameKey(), window.windowKey()));
        assertNull(game.battle.activeFlow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canceledAdvantageNeedsAConsciousNearbyEnemy(boolean witnessConscious) {
        var game = new Game();
        UUID target = game.character("fighter", Map.of());
        UUID rogue = game.character("rogue", Map.of());
        UUID witness = game.character("fighter", Map.of());
        if (!witnessConscious) game.setHealth(witness, 0, HealthState.INITIAL);
        UUID rapier = game.equip(rogue, "item.rapier");
        game.start(at(target, 1, 1), at(rogue, 0, 0), at(witness, 2, 0));
        game.apply(Command.action(Kind.DROP_PRONE, target));
        game.apply(Command.action(Kind.END_TURN, target));
        game.apply(Command.action(Kind.DROP_PRONE, rogue));
        game.apply(Command.action(Kind.BEGIN_ATTACK, rogue));
        if (witnessConscious) game.apply(Command.attack(rogue, target, rapier), 15);
        else game.apply(Command.attack(rogue, target, rapier), 15, 1);
        var attack = game.attack();
        assertTrue(attack.advantage());
        assertTrue(attack.disadvantage());
        assertNull(attack.secondD20());
        assertTrue(attack.hit());
        if (witnessConscious) {
            assertEquals(witness, attack.sneakAttack().nearbyEnemyId());
            Flow flow = game.battle.activeFlow();
            game.reject(game.choice(Kind.USE_SNEAK_ATTACK, witness), "ACTION_FLOW_TARGET_MISMATCH");
            game.reject(Command.sneakAttack(Kind.DECLINE_SNEAK_ATTACK, rogue, flow.flowKey(), UUID.randomUUID()),
                    "ACTION_FLOW_TARGET_MISMATCH");
            game.reject(Command.reaction(Kind.CONTINUE_FLOW, rogue, null, flow.flowKey(), flow.leafFrameKey(), null, null),
                    "ACTION_FLOW_STAGE_INVALID");
            game.apply(game.choice(Kind.DECLINE_SNEAK_ATTACK, rogue), 1);
            assertEquals(SneakAttackStatus.DECLINED, game.attack().sneakAttack().status());
        } else {
            assertNull(attack.sneakAttack());
            assertNull(game.battle.activeFlow());
        }
    }

    @Test void effectiveDisadvantageAndNonFinesseAttacksDoNotOfferTheChoice() {
        var game = new Game();
        UUID target = game.character("fighter", Map.of());
        UUID rogue = game.character("rogue", Map.of());
        UUID witness = game.character("fighter", Map.of());
        UUID rapier = game.equip(rogue, "item.rapier");
        game.start(at(rogue, 0, 0), at(target, 1, 1), at(witness, 2, 0));
        game.apply(Command.action(Kind.DROP_PRONE, rogue));
        game.apply(Command.action(Kind.BEGIN_ATTACK, rogue));
        game.apply(Command.attack(rogue, target, rapier), 20, 15, 2);
        assertTrue(game.attack().disadvantage());
        assertFalse(game.attack().advantage());
        assertTrue(game.attack().hit());
        assertNull(game.attack().sneakAttack());

        var other = new Game();
        UUID defender = other.character("fighter", Map.of());
        UUID attacker = other.character("rogue", Map.of());
        UUID longsword = other.equip(attacker, "item.longsword");
        other.start(at(defender, 1, 1), at(attacker, 0, 0));
        other.apply(Command.action(Kind.DROP_PRONE, defender));
        other.apply(Command.action(Kind.END_TURN, defender));
        other.apply(Command.action(Kind.BEGIN_ATTACK, attacker));
        other.apply(Command.attack(attacker, defender, longsword), 8, 16, 2);
        assertTrue(other.attack().advantage());
        assertTrue(other.attack().hit());
        assertNull(other.attack().sneakAttack());
    }

    private static Deployment at(UUID actor, int horizontal, int side) {
        return new Deployment(actor, new Position(horizontal, 0), side);
    }

    private static CampaignRuleContext deliveredRules() {
        Path directory = Path.of("src", "main", "resources", "storage", "rules");
        List<ProductionRuleArtifact> artifacts = new ArrayList<>();
        try {
            for (String name : List.of("srd51-runtime", "srd51-characters", "srd51-magic", "dm-runtime-house-rules"))
                artifacts.add(ProductionRuleArtifact.read(Files.readAllBytes(directory.resolve(name + ".zip"))));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        return CampaignDomainData.Material.of(ProductionSchema2Projection.compose(artifacts)).restore();
    }

    private static final class Game {
        final UUID campaign = UUID.randomUUID(), combatId = UUID.randomUUID();
        final Map<UUID, Context> roster = new HashMap<>();
        final CampaignRuleContext catalog = DELIVERED_RULES;
        final Schema2CombatRules combat = new Schema2CombatRules();
        final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
        final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
        Battle battle;

        UUID character(String template, Map<String, Integer> overrides) {
            UUID actor = UUID.randomUUID();
            var plan = catalog.initialPlan(campaign, actor, Schema2PreparationIdentity.random(), template, "acolyte");
            var scores = new HashMap<>(catalog.initialAbilities(template));
            scores.putAll(overrides);
            roster.put(actor, new Context(catalog.projection(), plan, growth.initial(catalog.projection(), plan,
                    UUID.randomUUID(), scores, catalog.initialChoices(template))));
            return actor;
        }
        void replace(UUID actor, State state) {
            roster.put(actor, new Context(catalog.projection(), roster.get(actor).initial(), state));
        }
        void setHealth(UUID actor, long hp, HealthState health) {
            State state = roster.get(actor).character();
            replace(actor, new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(),
                    state.characterClass(), state.abilities(), state.level(), state.proficiencyBonus(), hp, state.maximumHitPoints(),
                    state.classes(), state.hitDicePools(), state.subclasses(), state.features(), state.feats(), state.proficiencies(),
                    state.version(), state.resources(), state.rest(), state.equipment(), health, state.spellbook(), state.knownCantrips()));
        }
        UUID equip(UUID actor, String key) {
            UUID item = UUID.randomUUID();
            var initial = equipment.apply(catalog.projection(), roster.get(actor).character(),
                    equipment("ITEM_ACQUIRED", item, catalog.rule("equipment.item", key), "CARRIED"), item);
            replace(actor, equipment.apply(catalog.projection(), initial,
                    equipment("ITEM_EQUIPPED", item, null, "MAIN_HAND"), UUID.randomUUID()));
            return item;
        }
        void start(Deployment... deployments) {
            apply(Command.start(List.of(deployments), Arrays.stream(deployments).map(Deployment::characterId).toList()),
                    deployments.length == 2 ? new int[]{20, 5} : new int[]{20, 12, 1});
        }
        AttackAttempt attack() { return battle.flows().getLast().attacks().getLast(); }
        Command choice(Kind kind, UUID actor) {
            return Command.sneakAttack(kind, actor, battle.activeFlow().flowKey(), battle.activeFlow().leafFrameKey());
        }
        void reject(Command command, String code) {
            assertEquals(code, assertThrows(IllegalArgumentException.class,
                    () -> combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle, command)).getMessage());
        }
        Resolution apply(Command command, int... rawDice) {
            UUID operation = UUID.randomUUID();
            var dice = new ArrayDeque<>(Arrays.stream(rawDice).boxed().toList());
            var prepared = combat.preflight(operation, combatId, campaign, roster, battle, command);
            var result = combat.resolve(prepared, sides -> {
                assertFalse(dice.isEmpty(), "Unexpected d" + sides);
                return dice.removeFirst();
            });
            assertTrue(dice.isEmpty(), "Unused dice " + dice);
            combat.verify(operation, combatId, campaign, roster, battle, command, result);
            for (var change : result.healthChanges()) replace(change.characterId(), change.after());
            battle = result.after();
            return result;
        }
    }
}
