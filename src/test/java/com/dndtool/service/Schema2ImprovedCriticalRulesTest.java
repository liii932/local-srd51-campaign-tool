package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2GrantFixtures.artifacts;
import static com.dndtool.module.composition.Schema2GrantFixtures.edit;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ImprovedCriticalRulesTest {
    private static final RuleIdentity FIGHTER = new RuleIdentity(BASE, "character.class", "class.fighter");
    private static final RuleIdentity CHAMPION = new RuleIdentity(BASE, "character.subclass", "subclass.champion");
    private static final RuleIdentity IMPROVED_CRITICAL = new RuleIdentity(BASE, "character.feature", "feature.improved_critical");
    private static final FeatureState GRANT = new FeatureState(IMPROVED_CRITICAL, CHAMPION, 3);

    private final ProductionSchema2Projection material = championMaterial();
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final Schema2CombatRules combat = new Schema2CombatRules();
    private final UUID campaign = UUID.randomUUID(), scene = UUID.randomUUID();

    @Test void acquiredNaturalNineteenHitsAboveItsTotalAndOnlyDoublesTheDamageDice() {
        AttackAttempt attack = strike(CHAMPION, true, false, 19, 3, 4);
        assertEquals(19, attack.selectedD20());
        assertTrue(attack.selectedD20() + attack.abilityModifier() + attack.proficiencyBonus() < attack.armorClass());
        assertTrue(attack.initialHit());
        assertTrue(attack.hit());
        assertTrue(attack.critical());
        assertEquals(List.of(3, 4), attack.damageDice());
        assertEquals(9, attack.damageAmount());
    }

    @Test void theOtherSubclassStillNeedsToMeetArmorClassAndDoesNotCriticallyHitOnNineteen() {
        AttackAttempt miss = strike(SUBCLASS, true, false, 19);
        assertFalse(miss.hit());
        assertFalse(miss.critical());
        AttackAttempt hit = strike(SUBCLASS, false, false, 19, 3);
        assertTrue(hit.hit());
        assertFalse(hit.critical());
        assertEquals(List.of(3), hit.damageDice());
    }

    @Test void disadvantageUsesTheSelectedDieAndNaturalOneStillMisses() {
        AttackAttempt hit = strike(CHAMPION, false, true, 19, 18, 3);
        assertTrue(hit.disadvantage());
        assertEquals(19, hit.firstD20());
        assertEquals(18, hit.selectedD20());
        assertTrue(hit.hit());
        assertFalse(hit.critical());
        assertEquals(List.of(3), hit.damageDice());
        AttackAttempt miss = strike(CHAMPION, false, true, 20, 1);
        assertEquals(1, miss.selectedD20());
        assertFalse(miss.hit());
        assertFalse(miss.critical());
        assertTrue(miss.damageDice().isEmpty());
    }

    @Test void actualChampionAcquisitionIsRequiredAndMulticlassPreflightRejectsTheGrant() {
        State second = advance(character(UUID.randomUUID()).character(), GrowthChoices.EMPTY);
        assertTrue(second.features().isEmpty());
        assertEquals(20, Schema2FeatureSupport.weaponCriticalMinimum(material, second));
        State champion = advance(second, subclass(CHAMPION));
        assertEquals(List.of(GRANT), champion.features());
        assertEquals(19, Schema2FeatureSupport.weaponCriticalMinimum(material, champion));
        State other = advance(second, subclass(SUBCLASS));
        assertEquals(20, Schema2FeatureSupport.weaponCriticalMinimum(material, other));
        State forged = new State(other.identity(), other.campaignId(), other.characterId(), other.preparationOperationId(),
                other.characterClass(), other.abilities(), other.level(), other.proficiencyBonus(), other.hitPoints(),
                other.maximumHitPoints(), other.classes(), other.hitDicePools(), other.subclasses(), List.of(GRANT), other.feats(),
                other.proficiencies(), other.version(), other.resources(), other.rest(), other.equipment(), other.health());
        assertEquals("S2_COMBAT_FEATURE_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> Schema2FeatureSupport.weaponCriticalMinimum(material, forged)).getMessage());
        assertEquals("S2_GROWTH_IMPROVED_CRITICAL_SCOPE", assertThrows(IllegalArgumentException.class,
                () -> growth.prepareAdvance(material, champion, ALT_CLASS, 4, "SERVER_ROLL", fullMulticlassChoices())).getMessage());
    }

    private AttackAttempt strike(RuleIdentity subclass, boolean armored, boolean dodge, int... rolls) {
        UUID attacker = UUID.randomUUID(), target = UUID.randomUUID();
        Context source = character(attacker);
        State trained = advance(advance(source.character(), GrowthChoices.EMPTY), subclass(subclass));
        State armed = equip(trained, ITEM_WEAPON, "MAIN_HAND");
        Context opponent = character(target);
        State defense = armored ? equip(equip(opponent.character(), ITEM_HEAVY, "WORN"), ITEM_SHIELD, "MAIN_HAND") : opponent.character();
        long sceneSecond = Schema2SpellRules.second(defense);
        var roster = Map.of(attacker, new Context(material, source.initial(), armed, sceneSecond),
                target, new Context(material, opponent.initial(), defense, sceneSecond));
        var start = Command.start(List.of(new Deployment(attacker, new Position(0, 0), 0),
                new Deployment(target, new Position(1, 0), 1)), List.of(attacker, target));
        Battle battle = resolve(roster, null, start, dodge ? 10 : 20, dodge ? 20 : 10).after();
        if (dodge) {
            battle = resolve(roster, battle, Command.action(Kind.DODGE, target)).after();
            battle = resolve(roster, battle, Command.action(Kind.END_TURN, target)).after();
        }
        battle = resolve(roster, battle, Command.action(Kind.BEGIN_ATTACK, attacker)).after();
        return resolve(roster, battle, Command.attack(attacker, target, armed.equipment().getFirst().itemId()), rolls)
                .after().flows().getLast().attacks().getFirst();
    }

    private Resolution resolve(Map<UUID, Context> roster, Battle before, Command command, int... rolls) {
        UUID operation = UUID.randomUUID();
        var dice = new ArrayDeque<>(Arrays.stream(rolls).boxed().toList());
        var prepared = combat.preflight(operation, scene, campaign, roster, before, command);
        var result = combat.resolve(prepared, sides -> {
            assertFalse(dice.isEmpty(), "Unexpected d" + sides);
            return dice.removeFirst();
        });
        assertTrue(dice.isEmpty(), "Unconsumed dice " + dice);
        combat.verify(operation, scene, campaign, roster, before, command, result);
        return result;
    }

    private Context character(UUID actor) {
        var sources = new Schema2InitialProficiencyRules.Sources(new RuleIdentity(BASE, "character.race", "race.human"),
                null, new RuleIdentity(BASE, "character.background", "background.guard"), FIGHTER);
        var plan = new Schema2InitialCharacterPlanService().prepareMaterial(Schema2PreparationIdentity.random(),
                Schema2RulesBinding.from(material), material, new Schema2InitialProficiencyRules.Request(sources, Map.of()));
        var initial = new Schema2InitialCharacterPlanService.InitialPlan(campaign, actor, plan);
        return new Context(material, initial, growth.initial(material, initial, UUID.randomUUID(), fullAbilities(), INITIAL_CHOICES));
    }

    private State advance(State before, GrowthChoices choices) {
        return growth.advance(material,
                growth.prepareAdvance(material, before, FIGHTER, before.level() + 1, "FIXED_AVERAGE", choices), null);
    }

    private State equip(State before, RuleIdentity item, String location) {
        UUID instance = UUID.randomUUID();
        var rules = new Schema2CharacterEquipmentRules();
        State acquired = rules.apply(material, before, equipment("ITEM_ACQUIRED", instance, item, "CARRIED"), instance);
        return rules.apply(material, acquired, equipment("ITEM_EQUIPPED", instance, null, location), UUID.randomUUID());
    }

    private static GrowthChoices subclass(RuleIdentity subclass) {
        return new GrowthChoices(subclass, Map.of(), null, Map.of());
    }

    private static ProductionSchema2Projection championMaterial() {
        var packages = Schema2CombatTestFixture.packages();
        edit(packages, 0, "rules.json", rows -> {
            var rules = rows.getAsJsonArray();
            for (var element : rules) {
                var row = element.getAsJsonObject();
                if (jsonIdentity(CLASS).equals(row.get("identity"))) row.add("identity", jsonIdentity(FIGHTER));
                if (jsonIdentity(CLASS).equals(row.get("owner_class"))) row.add("owner_class", jsonIdentity(FIGHTER));
                if (jsonIdentity(ITEM_HEAVY).equals(row.get("identity"))) row.addProperty("armor_class", 20);
                if (jsonIdentity(ITEM_HEAVY).equals(row.get("identity")) || jsonIdentity(ITEM_SHIELD).equals(row.get("identity")))
                    row.add("proficiency", jsonIdentity(LIGHT_ARMOR));
            }
            var champion = new JsonObject();
            champion.add("identity", jsonIdentity(CHAMPION));
            champion.addProperty("display_name", "Champion");
            champion.addProperty("description", "Finite Champion combat fixture.");
            champion.addProperty("source_page", 25);
            champion.add("owner_class", jsonIdentity(FIGHTER));
            champion.addProperty("selection_level", 3);
            rules.add(champion);
            var feature = new JsonObject();
            feature.add("identity", jsonIdentity(IMPROVED_CRITICAL));
            feature.addProperty("display_name", "Improved Critical");
            feature.addProperty("description", "Finite Champion combat fixture.");
            feature.addProperty("source_page", 25);
            feature.add("owner", jsonIdentity(CHAMPION));
            feature.addProperty("level", 3);
            feature.addProperty("feature_kind", "FEATURE");
            feature.addProperty("execution_mode", "AUTOMATIC");
            feature.addProperty("execution_algorithm", CHAMPION_IMPROVED_CRITICAL_ALGORITHM);
            rules.add(feature);
        });
        // Use an untrained weapon to make 19 + Strength fall short of AC 22.
        edit(packages, 0, "growth.json", rows -> rows.getAsJsonArray().asList().removeIf(element -> {
            var row = element.getAsJsonObject();
            return row.has("growth_grant") && jsonIdentity(WEAPON).equals(row.getAsJsonObject("growth_grant").get("target"));
        }));
        return ProductionSchema2Projection.compose(artifacts(packages));
    }
}
