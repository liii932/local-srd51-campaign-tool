package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CharacterCheckRulesTest {
    private final Schema2CharacterCheckRules rules = new Schema2CharacterCheckRules();
    private final ProductionSchema2Projection material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages("normal")));
    private final State initial = Schema2CheckTestFixture.withPreparedTools(new Schema2CharacterProgressionRules().initial(material,
            fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, fullAbilities(), INITIAL_CHOICES));

    @Test void defaultSkillAndAnyToolAbilityUseOneBonusDespiteMultipleGrantSources() {
        assertEquals(2, initial.proficiencies().stream().filter(value -> value.target().equals(ATHLETICS)).count());
        assertEquals(2, initial.proficiencies().stream().filter(value -> value.target().equals(TOOL)).count());
        var skill = rules.prepare(material, initial, check("SKILL", "ability.strength", ATHLETICS, 14, false, false));
        assertEquals(new CheckResult(10, null, 10, 2, 2, 14, true, false, false), rules.resolve(skill, 10, null));
        var tool = rules.prepare(material, initial, check("TOOL", "ability.wisdom", TOOL, 11, false, false));
        assertEquals(new CheckResult(10, null, 10, -1, 2, 11, true, false, false), rules.resolve(tool, 10, null));
        assertEquals(initial.version() + 1, tool.capacityState().version());
        assertEquals(initial.resources(), tool.capacityState().resources());
        assertEquals(initial.proficiencies(), tool.capacityState().proficiencies());
    }

    @Test void untrainedTargetsAndCrossModuleSameKeysDoNotReceiveAnotherTargetsBonus() {
        for (Command command : List.of(check("SKILL", "ability.dexterity", ACROBATICS, 11, false, false),
                check("SKILL", "ability.strength", OTHER_SKILL, 12, false, false),
                check("TOOL", "ability.strength", OTHER_TOOL, 12, false, false),
                check("ABILITY", "ability.strength", null, 12, false, false))) {
            assertEquals(0, rules.resolve(rules.prepare(material, initial, command), 10, null).proficiencyBonus());
        }
    }

    @Test void advantageDisadvantageAndCancellationUseExactDiceAndNoNaturalOneOrTwentyOverride() {
        for (boolean advantage : List.of(false, true)) for (boolean disadvantage : List.of(false, true)) {
            var prepared = rules.prepare(material, initial, check("ABILITY", "ability.strength", null, 10, advantage, disadvantage));
            assertEquals(advantage != disadvantage, prepared.secondDieRequired());
            var result = rules.resolve(prepared, 3, prepared.secondDieRequired() ? 18 : null);
            assertEquals(advantage && !disadvantage ? 18 : 3, result.selectedD20());
            assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, 3, prepared.secondDieRequired() ? null : 18));
        }
        assertTrue(rules.resolve(rules.prepare(material, initial, check("ABILITY", "ability.strength", null, 0, false, false)), 1, null).success());
        assertFalse(rules.resolve(rules.prepare(material, initial, check("ABILITY", "ability.wisdom", null, 30, false, false)), 20, null).success());
    }

    @Test void unknownTargetsNonSkillsAlternateSkillAbilitiesAndInvalidDiceFailClosed() {
        for (Command command : List.of(check("SKILL", "ability.strength", WEAPON, 10, false, false),
                check("SKILL", "ability.constitution", ATHLETICS, 10, false, false),
                check("SKILL", "ability.strength", new Schema2CompositionKernel.RuleIdentity(BASE, "character.proficiency", "proficiency.missing"), 10, false, false),
                check("TOOL", "ability.strength", new Schema2CompositionKernel.RuleIdentity(BASE, "character.tool", "tool.missing"), 10, false, false)))
            assertThrows(IllegalArgumentException.class, () -> rules.prepare(material, initial, command));
        var prepared = rules.prepare(material, initial, check("ABILITY", "ability.strength", null, 10, true, false));
        for (int roll : List.of(0, 21)) {
            assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, roll, 10));
            assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, 10, roll));
        }
        assertThrows(IllegalArgumentException.class, () -> rules.verify(material, initial,
                check("ABILITY", "ability.strength", null, 10, true, false), new CheckResult(3, 18, 18, 2, 0, 20, false, true, false)));
    }

    @Test void boundedInputRejectsControlsBadUnicodeNonNfcAndForeignCommandFields() {
        for (String text : Arrays.asList(null, "", " ", "a\u0000b", "a\u0085b", "e\u0301", "\ud800", "a".repeat(501)))
            assertThrows(IllegalArgumentException.class, () -> new CheckInput(CHECK, "ABILITY", "ability.strength", null, 10, false, false, text));
        String astral = "😀".repeat(500);
        assertEquals(astral, new CheckInput(CHECK, "ABILITY", "ability.strength", null, 30, false, false, astral).adjudication());
        for (int dc : List.of(-1, 31)) assertThrows(IllegalArgumentException.class,
                () -> new CheckInput(CHECK, "ABILITY", "ability.strength", null, dc, false, false, "DM"));
        CheckInput input = new CheckInput(CHECK, "ABILITY", "ability.strength", null, 0, false, false, "DM");
        assertThrows(IllegalArgumentException.class, () -> new Command("CHECK_RESOLVED", ENERGY, 0, null, null, null, GrowthChoices.EMPTY, null, input));
        assertThrows(IllegalArgumentException.class, () -> new Command("RESOURCE_SPENT", ENERGY, 1, null, null, null, GrowthChoices.EMPTY, null, input));
    }

    @Test void statisticsOnlyStateCannotPretendToHaveRealSkillOrToolFacts() {
        var statistics = Schema2ProgressionFixtures.projection();
        var state = new Schema2CharacterProgressionRules().initial(statistics,
                initialPlan(statistics, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, abilities(), GrowthChoices.EMPTY);
        var error = assertThrows(IllegalArgumentException.class,
                () -> rules.prepare(statistics, state, check("ABILITY", "ability.strength", null, 10, false, false)));
        assertEquals("S2_CHECK_GROWTH_SCOPE", error.getMessage());
    }
}
