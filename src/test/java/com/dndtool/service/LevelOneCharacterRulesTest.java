package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LevelOneCharacterRulesTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void derivesServerOwnedBonusesProficienciesAndHitPoints() {
        LevelOneCharacterRules.Prepared result = new LevelOneCharacterRules().prepare(
                catalog(), validRequest(), 7L, HASH);

        assertEquals(16, result.finalAbilityScores().get("ability.constitution"));
        assertEquals(13, result.maximumHitPoints());
        assertEquals(List.of("skill.athletics", "skill.insight", "skill.religion"),
                result.skills());
        assertEquals(List.of("save.constitution", "save.strength"), result.saves());
        assertEquals(64, result.previewDigestSha256().length());
    }

    @Test
    void rejectsDuplicateUnknownOutOfRangeAndMalformedFrozenRules() {
        LevelOneCharacterRules rules = new LevelOneCharacterRules();
        LevelOneCharacterRules.Request duplicate = request(
                List.of("skill.insight"), List.of(), List.of("tool.smith_tools"));
        assertEquals("DUPLICATE_SELECTION",
                assertThrows(LevelOneCharacterRules.RuleException.class,
                        () -> rules.prepare(catalog(), duplicate, 7L, HASH)).code());

        LevelOneCharacterRules.Request unknown = request(
                List.of("skill.unknown"), List.of(), List.of("tool.smith_tools"));
        assertEquals("UNKNOWN_CATALOG_KEY",
                assertThrows(LevelOneCharacterRules.RuleException.class,
                        () -> rules.prepare(catalog(), unknown, 7L, HASH)).code());

        Map<String, Integer> badScores = Map.of(
                "ability.strength", 15, "ability.dexterity", 15,
                "ability.constitution", 14, "ability.intelligence", 13,
                "ability.wisdom", 12, "ability.charisma", 10);
        LevelOneCharacterRules.Request outOfRange = new LevelOneCharacterRules.Request(
                "11111111-2222-4333-8444-555555555555", "A", "race.dwarf",
                "subrace.hill_dwarf", "background.acolyte", "class.fighter", null, badScores,
                List.of(), List.of("skill.athletics"), List.of("language.celestial",
                "language.draconic"), List.of("tool.smith_tools"),
                List.of("starting.background.acolyte.equipment", "starting.class.fighter.a"));
        assertEquals("INVALID_ABILITY_ALLOCATION",
                assertThrows(LevelOneCharacterRules.RuleException.class,
                        () -> rules.prepare(catalog(), outOfRange, 7L, HASH)).code());
    }

    private static LevelOneCharacterRules.Request validRequest() {
        return request(List.of("skill.athletics"),
                List.of("language.celestial", "language.draconic"),
                List.of("tool.smith_tools"));
    }

    private static LevelOneCharacterRules.Request request(
            List<String> skills, List<String> languages, List<String> tools) {
        return new LevelOneCharacterRules.Request(
                "11111111-2222-4333-8444-555555555555", "山丘守卫",
                "race.dwarf", "subrace.hill_dwarf", "background.acolyte", "class.fighter",
                null, Map.of("ability.strength", 15, "ability.dexterity", 12,
                        "ability.constitution", 14, "ability.intelligence", 10,
                        "ability.wisdom", 13, "ability.charisma", 8),
                List.of(), skills, languages, tools,
                List.of("starting.background.acolyte.equipment", "starting.class.fighter.a"));
    }

    private static ModuleCatalog catalog() {
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", HASH, "RELEASED");
        List<ModuleCatalog.CatalogDefinition> definitions = List.of(
                definition("character.race", "race.dwarf", 1),
                definition("character.subrace", "subrace.hill_dwarf", 1),
                definition("character.background", "background.acolyte", 1),
                definition("character.class", "class.fighter", 1),
                definition("character.feature", "feature.fighter.second_wind", 1),
                definition("character.resource", "resource.hit_dice.d10", 1),
                definition("character.resource", "resource.fighter.second_wind", 2));
        List<ModuleCatalog.CatalogAttribute> attributes = List.of(
                profile("character.race", "race.dwarf",
                        "bonus=constitution+2|language=common,dwarvish|subrace=required|tool=1:brewer_supplies,mason_tools,smith_tools"),
                profile("character.subrace", "subrace.hill_dwarf", "bonus=wisdom+1"),
                profile("character.background", "background.acolyte",
                        "language=2:celestial,draconic|skill=insight,religion|start=1:starting.background.acolyte.equipment"),
                profile("character.class", "class.fighter",
                        "hp=10|save=constitution,strength|skill=1:athletics,history,insight|start=1:starting.class.fighter.a,starting.class.fighter.b"),
                new ModuleCatalog.CatalogAttribute("character.class", "class.fighter",
                        "creation.ability_method", 1, "IDENTIFIER",
                        new ModuleCatalog.IdentifierValue("ability.standard_array_v1")),
                new ModuleCatalog.CatalogAttribute("character.class", "class.fighter",
                        "class.hit_die_sides", 1, "INTEGER",
                        new ModuleCatalog.IntegerValue(10)),
                text("character.class", "class.fighter",
                        "class.proficiency_bonus_profile",
                        "1-4:2,5-8:3,9-12:4,13-16:5,17-20:6"),
                new ModuleCatalog.CatalogAttribute("character.feature",
                        "feature.fighter.second_wind", "feature.level", 1, "INTEGER",
                        new ModuleCatalog.IntegerValue(1)),
                identifier("character.feature", "feature.fighter.second_wind",
                        "catalog.category", "BASE"),
                identifier("character.feature", "feature.fighter.second_wind",
                        "feature.execution_mode", "AUTOMATIC"),
                identifier("character.feature", "feature.fighter.second_wind",
                        "feature.execution_algorithm", "AUTOMATIC_RESOURCE_LIFECYCLE_V1"),
                text("character.resource", "resource.fighter.second_wind",
                        "resource.maximum_profile", "1-20:1"),
                identifier("character.resource", "resource.fighter.second_wind",
                        "resource.execution_mode", "AUTOMATIC"),
                text("character.resource", "resource.fighter.second_wind",
                        "resource.recovery_profile", "1-20:SHORT_REST"));
        return new ModuleCatalog(release,
                List.<ModuleCatalog.RuleConstant>of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                definitions, attributes,
                List.of(new ModuleCatalog.CatalogRelation("character.subrace",
                                "subrace.hill_dwarf", "subrace.parent_race", "character.race",
                                "race.dwarf", 1),
                        new ModuleCatalog.CatalogRelation("character.feature",
                                "feature.fighter.second_wind", "feature.owner",
                                "character.class", "class.fighter", 1),
                        new ModuleCatalog.CatalogRelation("character.resource",
                                "resource.fighter.second_wind", "resource.owner",
                                "character.class", "class.fighter", 1)));
    }

    private static ModuleCatalog.CatalogDefinition definition(String type, String key, int order) {
        return new ModuleCatalog.CatalogDefinition(type, key, key, key, order);
    }

    private static ModuleCatalog.CatalogAttribute profile(String type, String key, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, "creation.level_one_profile", 1,
                "TEXT", new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute text(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1,
                "TEXT", new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute identifier(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue(value));
    }
}
