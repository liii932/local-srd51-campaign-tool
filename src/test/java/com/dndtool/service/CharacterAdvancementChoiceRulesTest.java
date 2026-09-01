package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CharacterAdvancementChoiceRulesTest {
    private final CharacterAdvancementChoiceRules rules = new CharacterAdvancementChoiceRules();

    @Test
    void multiclassRequiresBothExistingAndTargetClassPrerequisites() {
        CharacterAdvancementChoiceRules.Prepared prepared = rules.prepare(catalog(),
                Map.of("class.fighter", 4), scores(13, 10, 14, 8, 13, 8), Set.of(),
                request("class.cleric", Map.of(), null, List.of()));

        assertEquals(true, prepared.multiclass());
        assertEquals(Map.of("class.cleric", 1, "class.fighter", 4), prepared.classLevels());
        assertEquals(List.of("armor.light", "armor.medium", "armor.shield"),
                prepared.proficiencyGrants());
    }

    @Test
    void rejectsTargetWhenAnyMulticlassPrerequisiteFails() {
        assertCode("MULTICLASS_PREREQUISITE_NOT_MET", () -> rules.prepare(catalog(),
                Map.of("class.fighter", 4), scores(12, 10, 14, 8, 13, 8), Set.of(),
                request("class.cleric", Map.of(), null, List.of())));
        assertCode("MULTICLASS_PREREQUISITE_NOT_MET", () -> rules.prepare(catalog(),
                Map.of("class.fighter", 4), scores(13, 10, 14, 8, 12, 8), Set.of(),
                request("class.cleric", Map.of(), null, List.of())));
    }

    @Test
    void asiIsCanonicalAndEnforcesBudgetAndCap() {
        CharacterAdvancementChoiceRules.Prepared prepared = rules.prepare(catalog(),
                Map.of("class.fighter", 3), scores(18, 10, 14, 8, 10, 8), Set.of(),
                request("class.fighter", Map.of(
                        "ability.wisdom", 1, "ability.strength", 1), null, List.of()));

        assertEquals(Map.of("ability.strength", 19, "ability.dexterity", 10,
                "ability.constitution", 14, "ability.intelligence", 8,
                "ability.wisdom", 11, "ability.charisma", 8), prepared.abilityScores());
        assertEquals(List.of("ability.strength", "ability.wisdom"),
                prepared.abilityIncreases().keySet().stream().toList());

        assertCode("INVALID_ASI_SELECTION", () -> rules.prepare(catalog(),
                Map.of("class.fighter", 3), scores(20, 10, 14, 8, 10, 8), Set.of(),
                request("class.fighter", Map.of("ability.strength", 2), null, List.of())));
    }

    @Test
    void grapplerUsesGenericFeatFrameworkAndCannotBeRepeated() {
        CharacterAdvancementChoiceRules.Prepared prepared = rules.prepare(catalog(),
                Map.of("class.fighter", 3), scores(13, 10, 14, 8, 10, 8), Set.of(),
                request("class.fighter", Map.of(), "feat.grappler", List.of()));
        assertEquals("feat.grappler", prepared.featKey());

        assertCode("FEAT_PREREQUISITE_NOT_MET", () -> rules.prepare(catalog(),
                Map.of("class.fighter", 3), scores(12, 10, 14, 8, 10, 8), Set.of(),
                request("class.fighter", Map.of(), "feat.grappler", List.of())));
        assertCode("DUPLICATE_FEAT", () -> rules.prepare(catalog(),
                Map.of("class.fighter", 3), scores(13, 10, 14, 8, 10, 8),
                Set.of("feat.grappler"),
                request("class.fighter", Map.of(), "feat.grappler", List.of())));
    }

    @Test
    void rejectsAsiOrFeatOutsideAClassAsiLevel() {
        assertCode("UNEXPECTED_ADVANCEMENT_CHOICE", () -> rules.prepare(catalog(),
                Map.of("class.fighter", 4), scores(13, 10, 14, 8, 10, 8), Set.of(),
                request("class.fighter", Map.of("ability.strength", 2), null, List.of())));
    }

    private static CharacterAdvancementChoiceRules.Request request(String classKey,
            Map<String, Integer> increases, String feat, List<String> proficiencyChoices) {
        return new CharacterAdvancementChoiceRules.Request(
                classKey, increases, feat, proficiencyChoices);
    }

    private static Map<String, Integer> scores(int strength, int dexterity, int constitution,
            int intelligence, int wisdom, int charisma) {
        return Map.of("ability.strength", strength, "ability.dexterity", dexterity,
                "ability.constitution", constitution, "ability.intelligence", intelligence,
                "ability.wisdom", wisdom, "ability.charisma", charisma);
    }

    private static void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        CharacterAdvancementChoiceRules.RuleException exception = assertThrows(
                CharacterAdvancementChoiceRules.RuleException.class, action);
        assertEquals(code, exception.code());
    }

    private static ModuleCatalog catalog() {
        List<ModuleCatalog.CatalogDefinition> definitions = List.of(
                definition("character.class", "class.fighter", 1),
                definition("character.class", "class.cleric", 2),
                definition("character.feat", "feat.grappler", 1));
        List<ModuleCatalog.CatalogAttribute> attributes = List.of(
                text("character.class", "class.fighter", "class.multiclass_prerequisite",
                        "ability.strength>=13|ability.dexterity>=13"),
                text("character.class", "class.fighter", "class.multiclass_proficiency_profile",
                        "grant=armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple"),
                text("character.class", "class.fighter", "class.asi_levels",
                        "4,6,8,12,14,16,19"),
                text("character.class", "class.cleric", "class.multiclass_prerequisite",
                        "ability.wisdom>=13"),
                text("character.class", "class.cleric", "class.multiclass_proficiency_profile",
                        "grant=armor.light,armor.medium,armor.shield"),
                text("character.class", "class.cleric", "class.asi_levels",
                        "4,8,12,16,19"),
                text("character.feat", "feat.grappler", "feat.prerequisite",
                        "ability.strength>=13"));
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", null, "DRAFT");
        return new ModuleCatalog(release, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), definitions, attributes, List.of());
    }

    private static ModuleCatalog.CatalogDefinition definition(String type, String key, int order) {
        return new ModuleCatalog.CatalogDefinition(type, key, key, key, order);
    }

    private static ModuleCatalog.CatalogAttribute text(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "TEXT",
                new ModuleCatalog.TextValue(value));
    }
}
