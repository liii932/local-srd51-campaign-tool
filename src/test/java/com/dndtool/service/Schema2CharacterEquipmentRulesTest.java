package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CharacterEquipmentRulesTest {
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

    @Test void carriedToolsCannotBeUsedAndStrengthPenaltyAndNegativeDexterityAreDerivedCorrectly() {
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
        assertEquals(18, rules.derive(material, state).armorClass()); assertEquals(10, rules.derive(material, state).speedPenalty());
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
