package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers all integer boundaries and frozen attribute mappings used by derived values. */
final class CharacterDerivedValueCalculatorTest {
    private static final List<CharacterDerivedValueCalculator.ClassLevel> LEVEL_ZERO = List.of();
    private static final List<CharacterDerivedValueCalculator.ClassLevel> LEVEL_FIVE =
            List.of(new CharacterDerivedValueCalculator.ClassLevel("class.fighter", 5));
    private static final Map<String, Integer> ABILITY_SCORES = Map.of(
            "ability.strength", 8,
            "ability.dexterity", 10,
            "ability.constitution", 12,
            "ability.intelligence", 14,
            "ability.wisdom", 16,
            "ability.charisma", 18);

    @Test
    void abilityModifierUsesFloorDivisionAcrossNegativeAndPositiveBoundaries() {
        assertEquals(-5, CharacterDerivedValueCalculator.abilityModifier(1));
        assertEquals(-1, CharacterDerivedValueCalculator.abilityModifier(9));
        assertEquals(0, CharacterDerivedValueCalculator.abilityModifier(10));
        assertEquals(0, CharacterDerivedValueCalculator.abilityModifier(11));
        assertEquals(10, CharacterDerivedValueCalculator.abilityModifier(30));

        assertThrows(IllegalArgumentException.class,
                () -> CharacterDerivedValueCalculator.abilityModifier(0));
        assertThrows(IllegalArgumentException.class,
                () -> CharacterDerivedValueCalculator.abilityModifier(31));
    }

    @Test
    void totalLevelSupportsClasslessAndMulticlassCharacters() {
        CharacterDerivedValueCalculator calculator = calculator();

        assertEquals(0, calculator.totalLevel(LEVEL_ZERO));
        assertEquals(20, calculator.totalLevel(List.of(
                new CharacterDerivedValueCalculator.ClassLevel("class.fighter", 8),
                new CharacterDerivedValueCalculator.ClassLevel("class.wizard", 12))));
    }

    @Test
    void totalLevelRejectsUnknownDuplicateAndOutOfRangeRows() {
        CharacterDerivedValueCalculator calculator = calculator();

        assertThrows(IllegalArgumentException.class, () -> calculator.totalLevel(List.of(
                new CharacterDerivedValueCalculator.ClassLevel("class.unknown", 1))));
        assertThrows(IllegalArgumentException.class, () -> calculator.totalLevel(List.of(
                new CharacterDerivedValueCalculator.ClassLevel("class.fighter", 1),
                new CharacterDerivedValueCalculator.ClassLevel("class.fighter", 1))));
        assertThrows(IllegalArgumentException.class, () -> calculator.totalLevel(List.of(
                new CharacterDerivedValueCalculator.ClassLevel("class.fighter", 0))));
        assertThrows(IllegalArgumentException.class, () -> calculator.totalLevel(List.of(
                new CharacterDerivedValueCalculator.ClassLevel("class.fighter", 20),
                new CharacterDerivedValueCalculator.ClassLevel("class.wizard", 1))));
    }

    @Test
    void proficiencyBonusMatchesEveryLevelFromZeroThroughTwenty() {
        CharacterDerivedValueCalculator calculator = calculator();
        int[] expected = {
            2, 2, 2, 2, 2,
            3, 3, 3, 3,
            4, 4, 4, 4,
            5, 5, 5, 5,
            6, 6, 6, 6
        };

        for (int level = 0; level <= 20; level++) {
            assertEquals(expected[level], calculator.proficiencyBonus(level));
        }
        assertThrows(IllegalArgumentException.class, () -> calculator.proficiencyBonus(-1));
        assertThrows(IllegalArgumentException.class, () -> calculator.proficiencyBonus(21));
    }

    @Test
    void allFourProficiencyTiersUseIntegerAlgorithms() {
        CharacterDerivedValueCalculator calculator = calculator();

        assertEquals(0, calculator.proficiencyContribution(5, "proficiency.none"));
        assertEquals(1, calculator.proficiencyContribution(5, "proficiency.half"));
        assertEquals(3, calculator.proficiencyContribution(5, "proficiency.full"));
        assertEquals(6, calculator.proficiencyContribution(5, "proficiency.expertise"));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.proficiencyContribution(5, "proficiency.other"));
    }

    @Test
    void everySkillUsesItsFrozenGoverningAbility() {
        CharacterDerivedValueCalculator calculator = calculator();
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("skill.acrobatics", 3),
                Map.entry("skill.animal_handling", 6),
                Map.entry("skill.arcana", 5),
                Map.entry("skill.athletics", 2),
                Map.entry("skill.deception", 7),
                Map.entry("skill.history", 5),
                Map.entry("skill.insight", 6),
                Map.entry("skill.intimidation", 7),
                Map.entry("skill.investigation", 5),
                Map.entry("skill.medicine", 6),
                Map.entry("skill.nature", 5),
                Map.entry("skill.perception", 6),
                Map.entry("skill.performance", 7),
                Map.entry("skill.persuasion", 7),
                Map.entry("skill.religion", 5),
                Map.entry("skill.sleight_of_hand", 3),
                Map.entry("skill.stealth", 3),
                Map.entry("skill.survival", 6));

        expected.forEach((skillKey, bonus) -> assertEquals(
                bonus,
                calculator.skillBonus(
                        skillKey, ABILITY_SCORES, LEVEL_FIVE, "proficiency.full"),
                skillKey));
    }

    @Test
    void everySavingThrowUsesItsSameNamedAbility() {
        CharacterDerivedValueCalculator calculator = calculator();
        Map<String, Integer> expected = Map.of(
                "save.strength", 2,
                "save.dexterity", 3,
                "save.constitution", 4,
                "save.intelligence", 5,
                "save.wisdom", 6,
                "save.charisma", 7);

        expected.forEach((saveKey, bonus) -> assertEquals(
                bonus,
                calculator.savingThrowBonus(
                        saveKey, ABILITY_SCORES, LEVEL_FIVE, "proficiency.full"),
                saveKey));
    }

    @Test
    void missingInputsAndIncompleteRuleBandsFailClosed() {
        CharacterDerivedValueCalculator calculator = calculator();

        assertThrows(IllegalArgumentException.class, () -> calculator.skillBonus(
                "skill.unknown", ABILITY_SCORES, LEVEL_ZERO, "proficiency.none"));
        assertThrows(IllegalArgumentException.class, () -> calculator.savingThrowBonus(
                "save.strength", Map.of(), LEVEL_ZERO, "proficiency.none"));
        assertThrows(IllegalArgumentException.class, () -> calculator.skillBonus(
                "skill.athletics",
                Map.of("ability.strength", 31),
                LEVEL_ZERO,
                "proficiency.none"));

        assertThrows(IllegalStateException.class, () -> new CharacterDerivedValueCalculator(
                catalog(List.of(new ModuleCatalog.ProficiencyBonusBand(0, 19, 2)))));
        assertThrows(IllegalArgumentException.class, () -> calculator.skillBonus(
                null, ABILITY_SCORES, LEVEL_ZERO, "proficiency.none"));
        assertThrows(IllegalArgumentException.class, () -> calculator.totalLevel(List.of(
                new CharacterDerivedValueCalculator.ClassLevel(null, 1))));
    }

    private static CharacterDerivedValueCalculator calculator() {
        return new CharacterDerivedValueCalculator(catalog(List.of(
                new ModuleCatalog.ProficiencyBonusBand(0, 4, 2),
                new ModuleCatalog.ProficiencyBonusBand(5, 8, 3),
                new ModuleCatalog.ProficiencyBonusBand(9, 12, 4),
                new ModuleCatalog.ProficiencyBonusBand(13, 16, 5),
                new ModuleCatalog.ProficiencyBonusBand(17, 20, 6))));
    }

    private static ModuleCatalog catalog(List<ModuleCatalog.ProficiencyBonusBand> bands) {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", null, "RELEASED"),
                List.of(),
                List.of(),
                List.of(
                        new ModuleCatalog.ClassDefinition("class.fighter", "战士"),
                        new ModuleCatalog.ClassDefinition("class.wizard", "法师")),
                List.of(
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.none", "NONE", 0, 1, "EXACT"),
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.half", "HALF", 1, 2, "FLOOR"),
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.full", "FULL", 1, 1, "EXACT"),
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.expertise", "EXPERTISE", 2, 1, "EXACT")),
                bands,
                skills(),
                List.of(
                        new ModuleCatalog.SaveDefinition(
                                "save.strength", "ability.strength"),
                        new ModuleCatalog.SaveDefinition(
                                "save.dexterity", "ability.dexterity"),
                        new ModuleCatalog.SaveDefinition(
                                "save.constitution", "ability.constitution"),
                        new ModuleCatalog.SaveDefinition(
                                "save.intelligence", "ability.intelligence"),
                        new ModuleCatalog.SaveDefinition(
                                "save.wisdom", "ability.wisdom"),
                        new ModuleCatalog.SaveDefinition(
                                "save.charisma", "ability.charisma")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    private static List<ModuleCatalog.SkillDefinition> skills() {
        return List.of(
                skill("skill.acrobatics", "ability.dexterity"),
                skill("skill.animal_handling", "ability.wisdom"),
                skill("skill.arcana", "ability.intelligence"),
                skill("skill.athletics", "ability.strength"),
                skill("skill.deception", "ability.charisma"),
                skill("skill.history", "ability.intelligence"),
                skill("skill.insight", "ability.wisdom"),
                skill("skill.intimidation", "ability.charisma"),
                skill("skill.investigation", "ability.intelligence"),
                skill("skill.medicine", "ability.wisdom"),
                skill("skill.nature", "ability.intelligence"),
                skill("skill.perception", "ability.wisdom"),
                skill("skill.performance", "ability.charisma"),
                skill("skill.persuasion", "ability.charisma"),
                skill("skill.religion", "ability.intelligence"),
                skill("skill.sleight_of_hand", "ability.dexterity"),
                skill("skill.stealth", "ability.dexterity"),
                skill("skill.survival", "ability.wisdom"));
    }

    private static ModuleCatalog.SkillDefinition skill(String key, String abilityKey) {
        return new ModuleCatalog.SkillDefinition(key, key, abilityKey);
    }
}
