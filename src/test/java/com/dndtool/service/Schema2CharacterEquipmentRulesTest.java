package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2InitialProficiencyRules.Request;
import com.dndtool.service.Schema2InitialProficiencyRules.Sources;
import com.google.gson.JsonObject;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CharacterEquipmentRulesTest {
    private static final RuleIdentity HUMAN = new RuleIdentity(BASE, "character.race", "race.human");
    private static final RuleIdentity DWARF = new RuleIdentity(BASE, "character.race", "race.dwarf");
    private static final RuleIdentity SUBRACE = new RuleIdentity(BASE, "character.subrace", "subrace.hill_dwarf");
    private final Schema2CharacterEquipmentRules rules = new Schema2CharacterEquipmentRules();
    private final ProductionSchema2Projection material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages("normal")));
    private State state = new Schema2CharacterProgressionRules().initial(material,
            fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, fullAbilities(), INITIAL_CHOICES);

    @Test void armorPreparationUsesRealTimeAndCorrectAcAndKeepsInstancesAcrossOtherOwners() {
        assertEquals(11, rules.derive(material, state).armorClass());
        UUID leather = acquire(ITEM_LIGHT); equip(leather, "WORN");
        assertEquals(1, state.rest().minute()); assertEquals(12, rules.derive(material, state).armorClass());
        apply(equipment("ITEM_UNEQUIPPED", leather, null, null)); assertEquals(2, state.rest().minute());
        UUID medium = acquire(ITEM_MEDIUM); equip(medium, "WORN");
        assertEquals(7, state.rest().minute()); assertEquals(15, rules.derive(material, state).armorClass());
        assertEquals(Set.of(STEALTH), rules.derive(material, state).stealthSkills());
        apply(equipment("ITEM_UNEQUIPPED", medium, null, null));
        UUID plate = acquire(ITEM_HEAVY); equip(plate, "WORN");
        assertEquals(18, state.rest().minute()); assertEquals(18, rules.derive(material, state).armorClass());
        assertTrue(rules.derive(material, state).castingForbidden()); assertTrue(rules.derive(material, state).physicalCheckDisadvantage());
        apply(equipment("ITEM_UNEQUIPPED", plate, null, null)); assertEquals(23, state.rest().minute());
    }

    @Test void handsShieldExclusivityAndInstanceIdentityRejectImpossiblePreparation() {
        UUID shield = acquire(ITEM_SHIELD); equip(shield, "OFF_HAND");
        assertEquals(13, rules.derive(material, state).armorClass());
        UUID otherShield = acquire(ITEM_SHIELD), twoHanded = acquire(ITEM_TWO_HANDED), weapon = acquire(ITEM_WEAPON);
        assertThrows(IllegalArgumentException.class, () -> apply(equipment("ITEM_EQUIPPED", otherShield, null, "MAIN_HAND")));
        assertThrows(IllegalArgumentException.class, () -> apply(equipment("ITEM_EQUIPPED", twoHanded, null, "BOTH_HANDS")));
        equip(weapon, "MAIN_HAND");
        assertThrows(IllegalArgumentException.class, () -> apply(equipment("ITEM_REMOVED", weapon, null, null)));
        assertThrows(IllegalArgumentException.class, () -> apply(equipment("ITEM_EQUIPPED", weapon, null, "OFF_HAND")));
        assertThrows(IllegalArgumentException.class, () -> rules.apply(material, state,
                equipment("ITEM_ACQUIRED", UUID.randomUUID(), ITEM_LIGHT, "CARRIED"), UUID.randomUUID()));
        assertEquals(0, state.rest().minute());
    }

    @Test void armorAndProficiencyAutomaticallyComposeCheckDisadvantageAndPreserveDmFlags() {
        UUID medium = acquire(ITEM_MEDIUM); equip(medium, "WORN");
        var checks = new Schema2CharacterCheckRules();
        var stealth = checks.prepare(material, state, check("SKILL", "ability.dexterity", STEALTH, 10, false, false));
        var result = checks.resolve(stealth, 18, 2);
        assertTrue(result.effectiveDisadvantage()); assertFalse(result.effectiveAdvantage()); assertEquals(2, result.selectedD20());
        assertFalse(checks.prepare(material, state, check("SKILL", "ability.dexterity", ACROBATICS, 10, false, false)).secondDieRequired());
        apply(equipment("ITEM_UNEQUIPPED", medium, null, null));
        UUID shield = acquire(ITEM_SHIELD); equip(shield, "OFF_HAND");
        var command = check("ABILITY", "ability.strength", null, 10, true, false);
        var cancelled = checks.resolve(checks.prepare(material, state, command), 8, null);
        assertTrue(cancelled.effectiveAdvantage()); assertTrue(cancelled.effectiveDisadvantage());
        assertFalse(command.checkInput().disadvantage());
        assertFalse(checks.prepare(material, state, check("ABILITY", "ability.wisdom", null, 10, false, false)).secondDieRequired());
    }

    @Test void carriedToolsCannotBeUsedAndNegativeDexterityAffectsArmorClass() {
        UUID tool = acquire(ITEM_TOOL);
        assertFalse(rules.hasPreparedTool(material, state, TOOL));
        assertThrows(IllegalArgumentException.class, () -> new Schema2CharacterCheckRules().prepare(material, state,
                check("TOOL", "ability.strength", TOOL, 10, false, false)));
        equip(tool, "MAIN_HAND"); assertTrue(rules.hasPreparedTool(material, state, TOOL));
        assertFalse(rules.hasPreparedTool(material, state, OTHER_TOOL));
        var abilities = new HashMap<>(state.abilities()); abilities.put("ability.strength", 13); abilities.put("ability.dexterity", 8);
        state = copy(state, abilities);
        UUID medium = acquire(ITEM_MEDIUM); equip(medium, "WORN"); assertEquals(13, rules.derive(material, state).armorClass());
        apply(equipment("ITEM_UNEQUIPPED", medium, null, null));
        UUID plate = acquire(ITEM_HEAVY); equip(plate, "WORN");
        assertEquals(18, rules.derive(material, state).armorClass());
    }

    @Test void armorSpeedPolicyFollowsSelectedFrozenMovementAndPreservesOtherArmorEffects() {
        var movementMaterial = materialWithMovement(HeavyArmorSpeedPolicy.IGNORE, false);
        var dwarf = plan(movementMaterial, DWARF, null);
        var human = plan(movementMaterial, HUMAN, null);
        var dwarfInPlate = wearingPlate(movementMaterial, dwarf, 13);
        var humanInPlate = wearingPlate(movementMaterial, human, 13);

        assertEquals(25, rules.walkingSpeed(movementMaterial, dwarf, dwarfInPlate));
        assertEquals(20, rules.walkingSpeed(movementMaterial, human, humanInPlate));
        assertEquals(30, rules.walkingSpeed(movementMaterial, human, wearingPlate(movementMaterial, human, 15)));
        var effects = rules.derive(movementMaterial, dwarfInPlate);
        assertEquals(18, effects.armorClass());
        assertTrue(effects.castingForbidden());
        assertTrue(effects.physicalCheckDisadvantage());
        assertEquals(Set.of(STEALTH), effects.stealthSkills());
        assertEquals(effects, rules.derive(movementMaterial, humanInPlate));
    }

    @Test void applyPolicyReducesTheDwarfWalkingSpeedInWornArmor() {
        var movementMaterial = materialWithMovement(HeavyArmorSpeedPolicy.APPLY, false);
        var dwarf = plan(movementMaterial, DWARF, null);
        var armored = wearingPlate(movementMaterial, dwarf, 13);
        assertEquals(15, rules.walkingSpeed(movementMaterial, dwarf, armored));
        UUID plate = armored.equipment().getFirst().itemId();
        var carryingPlate = rules.apply(movementMaterial, armored,
                equipment("ITEM_UNEQUIPPED", plate, null, null), UUID.randomUUID());
        assertEquals(25, rules.walkingSpeed(movementMaterial, dwarf, carryingPlate));
    }

    @Test void appliedPenaltyCannotReduceWalkingSpeedBelowZero() {
        var slowMaterial = Schema2CombatTestFixture.material(5, 5);
        var human = plan(slowMaterial, HUMAN, null);
        assertEquals(0, rules.walkingSpeed(slowMaterial, human, wearingPlate(slowMaterial, human, 13)));
    }

    @Test void selectedRaceAndSubraceMustSupplyOneEnabledWalkingProfile() {
        var missing = materialWithMovement(null, false);
        var dwarf = plan(missing, DWARF, null);
        var armored = wearingPlate(missing, dwarf, 13);
        assertEquals(18, rules.derive(missing, armored).armorClass());
        assertEquals("ACTION_FLOW_RULE_UNSUPPORTED",
                assertThrows(IllegalArgumentException.class, () -> rules.walkingSpeed(missing, dwarf, armored)).getMessage());

        var subraceOnly = materialWithMovement(null, true);
        var subracePlan = plan(subraceOnly, DWARF, SUBRACE);
        assertEquals(15, rules.walkingSpeed(subraceOnly, subracePlan, wearingPlate(subraceOnly, subracePlan, 13)));

        var overlapping = materialWithMovement(HeavyArmorSpeedPolicy.IGNORE, true);
        var hillDwarf = plan(overlapping, DWARF, SUBRACE);
        assertEquals("ACTION_FLOW_RULE_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> rules.walkingSpeed(overlapping, hillDwarf, wearingPlate(overlapping, hillDwarf, 13))).getMessage());
    }

    private static ProductionSchema2Projection materialWithMovement(HeavyArmorSpeedPolicy dwarfPolicy, boolean subraceMovement) {
        var files = Schema2CombatTestFixture.packages();
        Schema2GrantFixtures.edit(files, 0, "rules.json", root -> {
            var rows = root.getAsJsonArray();
            rows.add(definition(DWARF));
            var subrace = definition(SUBRACE);
            subrace.add("parent_race", jsonIdentity(DWARF));
            rows.add(subrace);
            if (dwarfPolicy != null) rows.add(movement(DWARF, "movement.dwarf", dwarfPolicy));
            if (subraceMovement) rows.add(movement(SUBRACE, "movement.hill_dwarf", HeavyArmorSpeedPolicy.APPLY));
        });
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }

    private static JsonObject definition(RuleIdentity id) {
        var row = new JsonObject();
        row.add("identity", jsonIdentity(id));
        row.addProperty("display_name", id.ruleKey());
        row.addProperty("description", "Fixture for walking speed from selected sources.");
        row.addProperty("source_page", 3);
        return row;
    }

    private static JsonObject movement(RuleIdentity owner, String key, HeavyArmorSpeedPolicy policy) {
        var row = definition(new RuleIdentity(BASE, "character.movement", key));
        row.add("owner", jsonIdentity(owner));
        row.addProperty("walk_speed_feet", 25);
        row.addProperty("algorithm", WALKING_SPEED_ALGORITHM);
        row.addProperty("heavy_armor_speed_policy", policy.name());
        return row;
    }

    private static InitialPlan plan(ProductionSchema2Projection material, RuleIdentity race, RuleIdentity subrace) {
        var sources = new Sources(race, subrace, new RuleIdentity(BASE, "character.background", "background.guard"), CLASS);
        var plan = new Schema2InitialCharacterPlanService().prepareMaterial(IDENTITY, Schema2RulesBinding.from(material),
                material, new Request(sources, Map.of()));
        return new InitialPlan(CAMPAIGN, CHARACTER, plan);
    }

    private State wearingPlate(ProductionSchema2Projection material, InitialPlan plan, int strength) {
        var abilities = new HashMap<>(fullAbilities());
        abilities.put("ability.strength", strength);
        abilities.put("ability.constitution", strength == 13 ? 15 : 13);
        State state = new Schema2CharacterProgressionRules().initial(material, plan, PREPARATION, abilities, INITIAL_CHOICES);
        UUID plate = UUID.randomUUID();
        state = rules.apply(material, state, equipment("ITEM_ACQUIRED", plate, ITEM_HEAVY, "CARRIED"), plate);
        return rules.apply(material, state, equipment("ITEM_EQUIPPED", plate, null, "WORN"), UUID.randomUUID());
    }

    @Test void exactInstanceAndArmorTimeCapacitiesAreCheckedBeforeMutation() {
        for (int i = 0; i < 32; i++) acquire(ITEM_WEAPON);
        State full = state;
        assertThrows(IllegalArgumentException.class, () -> acquire(ITEM_WEAPON));
        assertEquals(full, state);
        State base = new Schema2CharacterProgressionRules().initial(material,
                fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, fullAbilities(), INITIAL_CHOICES);
        UUID id = UUID.randomUUID();
        state = rules.apply(material, base, equipment("ITEM_ACQUIRED", id, ITEM_HEAVY, "CARRIED"), id);
        var old = state;
        state = new State(old.identity(), old.campaignId(), old.characterId(), old.preparationOperationId(), old.characterClass(), old.abilities(),
                old.level(), old.proficiencyBonus(), old.hitPoints(), old.maximumHitPoints(), old.classes(), old.hitDicePools(), old.subclasses(), old.features(),
                old.feats(), old.proficiencies(), old.version(), old.resources(), new RestState(Schema2CharacterRestRules.MAX_MINUTE - 9, null, null), old.equipment(), old.health());
        assertThrows(IllegalArgumentException.class, () -> equip(id, "WORN"));
        assertEquals("CARRIED", state.equipment().getFirst().location());
    }

    private UUID acquire(Schema2CompositionKernel.RuleIdentity item) {
        UUID id = UUID.randomUUID(); state = rules.apply(material, state, equipment("ITEM_ACQUIRED", id, item, "CARRIED"), id); return id;
    }
    private void equip(UUID id, String location) { apply(equipment("ITEM_EQUIPPED", id, null, location)); }
    private void apply(Command command) { state = rules.apply(material, state, command, UUID.randomUUID()); }
    private static State copy(State before, Map<String, Integer> abilities) {
        return new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(), before.characterClass(), abilities,
                before.level(), before.proficiencyBonus(), before.hitPoints(), before.maximumHitPoints(), before.classes(), before.hitDicePools(), before.subclasses(),
                before.features(), before.feats(), before.proficiencies(), before.version(), before.resources(), before.rest(), before.equipment(), before.health());
    }
}
