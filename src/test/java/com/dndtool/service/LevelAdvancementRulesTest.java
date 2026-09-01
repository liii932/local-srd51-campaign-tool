package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.LevelAdvancementRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LevelAdvancementRulesTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void fixedAveragePreviewsHpHitDieProficiencyAndNewLevelResource() {
        LevelAdvancementRules.Prepared prepared = new LevelAdvancementRules().prepare(
                catalog(), request(2, "FIXED_AVERAGE"), context(), HASH);

        assertEquals(8, prepared.minimumHitPointIncrease());
        assertEquals(8, prepared.maximumHitPointIncrease());
        assertEquals(2, prepared.previousProficiencyBonus());
        assertEquals(2, prepared.newProficiencyBonus());
        assertEquals(new LevelAdvancementRepository.ResourceState(
                        "resource.hit_dice.d10", 2, 2, false),
                change(prepared, "resource.hit_dice.d10").next());
        assertEquals(new LevelAdvancementRepository.ResourceState(
                        "resource.fighter.action_surge", 1, 1, false),
                change(prepared, "resource.fighter.action_surge").next());
        assertEquals(false, prepared.resourceChanges().stream()
                .anyMatch(change -> "resource.fighter.pact_magic".equals(change.resourceKey())));
    }

    @Test
    void extendedAdvancementDerivesTheDueClassFeatureFromTheFrozenMatrix() {
        LevelAdvancementRepository.PreviewContext context =
                new LevelAdvancementRepository.PreviewContext(
                        context().campaignKey(), context().characterKey(), 7, 4,
                        context().moduleKey(), context().releaseVersion(), HASH,
                        "class.fighter", 1, 1, 14, 8, context().resources(),
                        List.of(new LevelAdvancementRepository.ClassLevel("class.fighter", 1)),
                        Map.of("ability.strength", 10, "ability.dexterity", 10,
                                "ability.constitution", 14, "ability.intelligence", 10,
                                "ability.wisdom", 10, "ability.charisma", 8),
                        Set.of(), Set.of("armor.heavy", "armor.light", "armor.medium",
                                "armor.shield", "weapon.martial", "weapon.simple"),
                        Map.of(), Set.of("feature.fighter.second_wind"));
        LevelAdvancementRules.Request request = new LevelAdvancementRules.Request(
                context.characterKey(), 2, "FIXED_AVERAGE", "class.fighter", null,
                Map.of(), null, List.of());

        LevelAdvancementRules.Prepared prepared = new LevelAdvancementRules().prepare(
                catalog(), request, context, HASH);

        assertEquals(List.of("feature.fighter.action_surge"),
                prepared.featureTransition().featureUnlocks().stream()
                        .map(ClassFeatureRules.FeatureRule::featureKey).toList());
    }

    @Test
    void charismaAsiUpdatesResourcesOwnedByEveryExistingClass() {
        LevelAdvancementRepository.PreviewContext multiclass =
                new LevelAdvancementRepository.PreviewContext(
                        context().campaignKey(), context().characterKey(), 7, 4,
                        context().moduleKey(), context().releaseVersion(), HASH,
                        "class.fighter", 3, 4, 14, 13,
                        List.of(resource("resource.hit_points", 35, 35),
                                resource("resource.hit_dice.d10", 2, 3),
                                resource("resource.hit_dice.d8", 1, 1),
                                resource("resource.fighter.second_wind", 1, 1),
                                resource("resource.fighter.action_surge", 1, 1),
                                resource("resource.bard.bardic_inspiration", 0, 1)),
                        List.of(new LevelAdvancementRepository.ClassLevel("class.bard", 1),
                                new LevelAdvancementRepository.ClassLevel("class.fighter", 3)),
                        Map.of("ability.strength", 14, "ability.dexterity", 10,
                                "ability.constitution", 14, "ability.intelligence", 10,
                                "ability.wisdom", 13, "ability.charisma", 13),
                        Set.of(), Set.of(), Map.of(),
                        Set.of("feature.fighter.second_wind",
                                "feature.fighter.action_surge"));
        LevelAdvancementRules.Request request = new LevelAdvancementRules.Request(
                multiclass.characterKey(), 5, "FIXED_AVERAGE", "class.fighter", null,
                Map.of("ability.charisma", 1, "ability.wisdom", 1),
                null, List.of());

        LevelAdvancementRules.Prepared prepared = new LevelAdvancementRules().prepare(
                catalogWithBardResource(), request, multiclass, HASH);

        assertEquals(new LevelAdvancementRepository.ResourceState(
                        "resource.bard.bardic_inspiration", 1, 2, false),
                change(prepared, "resource.bard.bardic_inspiration").next());
    }

    @Test
    void serverRollPreviewIsBoundedWithoutChoosingARandomResult() {
        LevelAdvancementRules.Prepared prepared = new LevelAdvancementRules().prepare(
                catalog(), request(2, "SERVER_ROLL"), context(), HASH);

        assertEquals(3, prepared.minimumHitPointIncrease());
        assertEquals(12, prepared.maximumHitPointIncrease());
    }

    @Test
    void rejectsSkippedLevelAndMismatchedAuthoritativeResourceState() {
        LevelAdvancementRules rules = new LevelAdvancementRules();
        assertCode("INVALID_LEVEL_TRANSITION",
                () -> rules.prepare(catalog(), request(3, "FIXED_AVERAGE"), context(), HASH));

        LevelAdvancementRepository.PreviewContext malformed = new LevelAdvancementRepository
                .PreviewContext(context().campaignKey(), context().characterKey(), 7, 4,
                        context().moduleKey(), context().releaseVersion(), HASH,
                        "class.fighter", 1, 1, 14, 8,
                        List.of(resource("resource.hit_points", 20, 20),
                                resource("resource.hit_dice.d10", 1, 2),
                                resource("resource.fighter.second_wind", 1, 1)));
        assertCode("AUTHORITATIVE_STATE_MISMATCH",
                () -> rules.prepare(catalog(), request(2, "FIXED_AVERAGE"), malformed, HASH));
    }

    @Test
    void rejectsClientChosenOrUnknownHitPointAlgorithms() {
        assertCode("INVALID_REQUEST", () -> new LevelAdvancementRules().prepare(
                catalog(), request(2, "CLIENT_ROLL"), context(), HASH));
    }

    @Test
    void rejectsDuplicateOrMalformedFrozenProfiles() {
        ModuleCatalog valid = catalog();
        List<ModuleCatalog.CatalogAttribute> attributes = new ArrayList<>(
                valid.catalogAttributes());
        attributes.add(new ModuleCatalog.CatalogAttribute(
                "character.class", "class.fighter", "class.proficiency_bonus_profile", 2,
                "TEXT", new ModuleCatalog.TextValue("1-20:6")));
        ModuleCatalog duplicate = copy(valid, attributes);

        assertCode("MALFORMED_FROZEN_CATALOG", () -> new LevelAdvancementRules().prepare(
                duplicate, request(2, "FIXED_AVERAGE"), context(), HASH));
    }

    private static LevelAdvancementRepository.ResourceChange change(
            LevelAdvancementRules.Prepared prepared, String key) {
        return prepared.resourceChanges().stream().filter(value -> key.equals(value.resourceKey()))
                .findFirst().orElseThrow();
    }

    private static void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        LevelAdvancementRules.RuleException exception = assertThrows(
                LevelAdvancementRules.RuleException.class, action);
        assertEquals(code, exception.code());
    }

    private static LevelAdvancementRules.Request request(int target, String hpChoice) {
        return new LevelAdvancementRules.Request(
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", target, hpChoice);
    }

    private static LevelAdvancementRepository.PreviewContext context() {
        return new LevelAdvancementRepository.PreviewContext(
                "11111111-2222-4333-8444-555555555555",
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", 7, 4,
                "dnd5e2014_srd51_se", "1", HASH, "class.fighter", 1, 1, 14, 8,
                List.of(resource("resource.hit_points", 20, 20),
                        resource("resource.hit_dice.d10", 1, 1),
                        resource("resource.fighter.second_wind", 1, 1)));
    }

    private static LevelAdvancementRepository.ResourceState resource(
            String key, long current, long maximum) {
        return new LevelAdvancementRepository.ResourceState(key, current, maximum, false);
    }

    private static ModuleCatalog catalog() {
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", HASH, "RELEASED");
        List<ModuleCatalog.CatalogDefinition> definitions = List.of(
                definition("character.class", "class.fighter", 1),
                definition("character.feature", "feature.fighter.second_wind", 1),
                definition("character.feature", "feature.fighter.action_surge", 2),
                definition("character.resource", "resource.fighter.action_surge", 1),
                definition("character.resource", "resource.fighter.second_wind", 2),
                definition("character.resource", "resource.fighter.pact_magic", 3),
                definition("character.resource", "resource.hit_dice.d10", 4),
                definition("character.resource", "resource.hit_points", 5));
        List<ModuleCatalog.CatalogAttribute> attributes = List.of(
                integer("character.class", "class.fighter", "class.hit_die_sides", 10),
                text("character.class", "class.fighter", "class.proficiency_bonus_profile",
                        "1-4:2,5-8:3,9-12:4,13-16:5,17-20:6"),
                text("character.class", "class.fighter", "class.multiclass_prerequisite",
                        "ability.strength>=13|ability.dexterity>=13"),
                text("character.class", "class.fighter", "class.multiclass_proficiency_profile",
                        "grant=armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple"),
                text("character.class", "class.fighter", "class.asi_levels",
                        "4,6,8,12,14,16,19"),
                featureInteger("feature.fighter.second_wind", "feature.level", 1),
                identifier("character.feature", "feature.fighter.second_wind",
                        "catalog.category", "BASE"),
                identifier("character.feature", "feature.fighter.second_wind",
                        "feature.execution_mode", "AUTOMATIC"),
                identifier("character.feature", "feature.fighter.second_wind",
                        "feature.execution_algorithm", "AUTOMATIC_RESOURCE_LIFECYCLE_V1"),
                featureInteger("feature.fighter.action_surge", "feature.level", 2),
                identifier("character.feature", "feature.fighter.action_surge",
                        "catalog.category", "BASE"),
                identifier("character.feature", "feature.fighter.action_surge",
                        "feature.execution_mode", "AUTOMATIC"),
                identifier("character.feature", "feature.fighter.action_surge",
                        "feature.execution_algorithm", "AUTOMATIC_RESOURCE_LIFECYCLE_V1"),
                text("character.resource", "resource.fighter.action_surge",
                        "resource.maximum_profile", "2-16:1,17-20:2"),
                identifier("character.resource", "resource.fighter.action_surge",
                        "resource.execution_mode", "AUTOMATIC"),
                text("character.resource", "resource.fighter.action_surge",
                        "resource.recovery_profile", "1-20:SHORT_REST"),
                text("character.resource", "resource.fighter.second_wind",
                        "resource.maximum_profile", "1-20:1"),
                identifier("character.resource", "resource.fighter.second_wind",
                        "resource.execution_mode", "AUTOMATIC"),
                text("character.resource", "resource.fighter.second_wind",
                        "resource.recovery_profile", "1-20:SHORT_REST"),
                text("character.resource", "resource.fighter.pact_magic",
                        "resource.maximum_profile", "1-20:1"),
                identifier("character.resource", "resource.fighter.pact_magic",
                        "resource.execution_mode", "BLOCKED"),
                text("character.resource", "resource.fighter.pact_magic",
                        "resource.recovery_profile", "1-20:SHORT_REST"));
        List<ModuleCatalog.CatalogRelation> relations = List.of(
                featureOwner("feature.fighter.second_wind"),
                featureOwner("feature.fighter.action_surge"),
                owner("resource.fighter.action_surge"),
                owner("resource.fighter.second_wind"),
                owner("resource.fighter.pact_magic"));
        return new ModuleCatalog(release, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), definitions, attributes, relations);
    }

    private static ModuleCatalog catalogWithBardResource() {
        ModuleCatalog source = catalog();
        List<ModuleCatalog.CatalogDefinition> definitions = new ArrayList<>(
                source.catalogDefinitions());
        definitions.add(definition("character.class", "class.bard", 2));
        definitions.add(definition("character.resource",
                "resource.bard.bardic_inspiration", 6));
        definitions.add(definition("character.resource", "resource.hit_dice.d8", 7));
        List<ModuleCatalog.CatalogAttribute> attributes = new ArrayList<>(
                source.catalogAttributes());
        attributes.add(integer("character.class", "class.bard", "class.hit_die_sides", 8));
        attributes.add(text("character.class", "class.bard", "class.proficiency_bonus_profile",
                "1-4:2,5-8:3,9-12:4,13-16:5,17-20:6"));
        attributes.add(text("character.class", "class.bard", "class.multiclass_prerequisite",
                "ability.charisma>=13"));
        attributes.add(text("character.class", "class.bard",
                "class.multiclass_proficiency_profile", "grant=armor.light"));
        attributes.add(text("character.class", "class.bard", "class.asi_levels",
                "4,8,12,16,19"));
        attributes.add(text("character.resource", "resource.bard.bardic_inspiration",
                "resource.maximum_profile", "1-20:CHARISMA_MODIFIER_MINIMUM_ONE"));
        attributes.add(identifier("character.resource", "resource.bard.bardic_inspiration",
                "resource.execution_mode", "AUTOMATIC"));
        attributes.add(text("character.resource", "resource.bard.bardic_inspiration",
                "resource.recovery_profile", "1-4:LONG_REST,5-20:SHORT_REST"));
        List<ModuleCatalog.CatalogRelation> relations = new ArrayList<>(
                source.catalogRelations());
        relations.add(new ModuleCatalog.CatalogRelation("character.resource",
                "resource.bard.bardic_inspiration", "resource.owner",
                "character.class", "class.bard", 1));
        return copy(source, definitions, attributes, relations);
    }

    private static ModuleCatalog copy(ModuleCatalog source,
            List<ModuleCatalog.CatalogAttribute> attributes) {
        return copy(source, source.catalogDefinitions(), attributes, source.catalogRelations());
    }

    private static ModuleCatalog copy(ModuleCatalog source,
            List<ModuleCatalog.CatalogDefinition> definitions,
            List<ModuleCatalog.CatalogAttribute> attributes,
            List<ModuleCatalog.CatalogRelation> relations) {
        return new ModuleCatalog(source.release(), source.ruleConstants(), source.fieldDefinitions(),
                source.classDefinitions(), source.proficiencyTiers(),
                source.proficiencyBonusBands(), source.skillDefinitions(), source.saveDefinitions(),
                source.itemTemplates(), source.entityTemplates(), source.entityTemplateValues(),
                source.entityTemplateClassLevels(), source.entityTemplateProficiencies(),
                source.checkDefinitions(), source.rollModes(), source.eventTemplates(),
                source.eventChecks(), source.eventEffects(), source.effectDefinitions(),
                source.effectParameters(), source.mapDefinitions(), source.mapNodes(),
                source.mapConnections(), definitions, attributes, relations);
    }

    private static ModuleCatalog.CatalogDefinition definition(String type, String key, int order) {
        return new ModuleCatalog.CatalogDefinition(type, key, key, key, order);
    }

    private static ModuleCatalog.CatalogAttribute integer(
            String type, String key, String attribute, long value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "INTEGER",
                new ModuleCatalog.IntegerValue(value));
    }

    private static ModuleCatalog.CatalogAttribute text(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "TEXT",
                new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute identifier(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "IDENTIFIER",
                new ModuleCatalog.IdentifierValue(value));
    }

    private static ModuleCatalog.CatalogAttribute featureInteger(
            String feature, String attribute, long value) {
        return integer("character.feature", feature, attribute, value);
    }

    private static ModuleCatalog.CatalogRelation featureOwner(String feature) {
        return new ModuleCatalog.CatalogRelation("character.feature", feature,
                "feature.owner", "character.class", "class.fighter", 1);
    }

    private static ModuleCatalog.CatalogRelation owner(String resource) {
        return new ModuleCatalog.CatalogRelation("character.resource", resource,
                "resource.owner", "character.class", "class.fighter", 1);
    }
}
