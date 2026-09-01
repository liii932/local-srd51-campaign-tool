package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.LevelAdvancementRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassResourceRecoveryRulesTest {
    @Test
    void shortRestProducesOnlyTypedEligibleResourceEffects() {
        ClassResourceRecoveryRules.Prepared prepared = new ClassResourceRecoveryRules().prepare(
                catalog(), "class.bard", 5, "SHORT_REST", List.of(
                        state("resource.bard.bardic_inspiration", 1, 4),
                        state("resource.bard.long_rest_pool", 1, 3)));

        assertEquals(List.of(new ClassResourceRecoveryRules.RecoveryEffect(
                        "RESOURCE_CURRENT_SET_TO_MAXIMUM",
                        "resource.bard.bardic_inspiration", 1, 4, 4)),
                prepared.effects());
    }

    @Test
    void frozenRecoveryBandChangesAtLevelFive() {
        ClassResourceRecoveryRules rules = new ClassResourceRecoveryRules();
        assertEquals(List.of(), rules.prepare(catalog(), "class.bard", 4, "SHORT_REST",
                List.of(state("resource.bard.bardic_inspiration", 1, 4),
                        state("resource.bard.long_rest_pool", 1, 3))).effects());
        assertEquals(2, rules.prepare(catalog(), "class.bard", 4, "LONG_REST",
                List.of(state("resource.bard.bardic_inspiration", 1, 4),
                        state("resource.bard.long_rest_pool", 1, 3))).effects().size());
        assertEquals(2, rules.prepare(catalog(), "class.bard", 5, "LONG_REST",
                List.of(state("resource.bard.bardic_inspiration", 1, 4),
                        state("resource.bard.long_rest_pool", 1, 3))).effects().size());
    }

    @Test
    void blockedSpellResourceMustNeverExistInAuthoritativeState() {
        List<LevelAdvancementRepository.ResourceState> invalid = new ArrayList<>();
        invalid.add(state("resource.bard.bardic_inspiration", 4, 4));
        invalid.add(state("resource.bard.long_rest_pool", 3, 3));
        invalid.add(state("resource.bard.spell_slots", 1, 1));

        assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> new ClassResourceRecoveryRules()
                .prepare(catalog(), "class.bard", 5, "LONG_REST", invalid));

        assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> new ClassResourceRecoveryRules()
                .prepare(catalog(), "class.bard", 5, "LONG_REST", List.of(
                        state("resource.bard.bardic_inspiration", 4, 4),
                        state("resource.bard.long_rest_pool", 3, 3),
                        state("resource.unknown.injected", 1, 1))));
    }

    @Test
    void malformedOrDuplicateProfilesFailClosed() {
        ModuleCatalog valid = catalog();
        List<ModuleCatalog.CatalogAttribute> attributes = new ArrayList<>(
                valid.catalogAttributes());
        attributes.add(text("resource.bard.bardic_inspiration",
                "resource.recovery_profile", 2, "1-20:LONG_REST"));

        assertCode("MALFORMED_FROZEN_CATALOG", () -> new ClassResourceRecoveryRules().prepare(
                copy(valid, attributes), "class.bard", 5, "SHORT_REST", List.of(
                        state("resource.bard.bardic_inspiration", 1, 4),
                        state("resource.bard.long_rest_pool", 1, 3))));
    }

    private static ModuleCatalog catalog() {
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", null, "DRAFT");
        List<ModuleCatalog.CatalogDefinition> definitions = List.of(
                definition("character.class", "class.bard", 1),
                definition("character.resource", "resource.bard.bardic_inspiration", 1),
                definition("character.resource", "resource.bard.long_rest_pool", 2),
                definition("character.resource", "resource.bard.spell_slots", 3));
        List<ModuleCatalog.CatalogAttribute> attributes = List.of(
                text("resource.bard.bardic_inspiration", "resource.maximum_profile", 1,
                        "1-20:4"),
                identifier("resource.bard.bardic_inspiration", "resource.execution_mode",
                        "AUTOMATIC"),
                text("resource.bard.bardic_inspiration", "resource.recovery_profile", 1,
                        "1-4:LONG_REST,5-20:SHORT_REST"),
                text("resource.bard.long_rest_pool", "resource.maximum_profile", 1,
                        "1-20:3"),
                identifier("resource.bard.long_rest_pool", "resource.execution_mode",
                        "AUTOMATIC"),
                text("resource.bard.long_rest_pool", "resource.recovery_profile", 1,
                        "1-20:LONG_REST"),
                text("resource.bard.spell_slots", "resource.maximum_profile", 1,
                        "1-20:1"),
                identifier("resource.bard.spell_slots", "resource.execution_mode", "BLOCKED"),
                text("resource.bard.spell_slots", "resource.recovery_profile", 1,
                        "1-20:LONG_REST"));
        List<ModuleCatalog.CatalogRelation> relations = List.of(
                owner("resource.bard.bardic_inspiration"),
                owner("resource.bard.long_rest_pool"),
                owner("resource.bard.spell_slots"));
        return new ModuleCatalog(release, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), definitions, attributes, relations);
    }

    private static ModuleCatalog.CatalogDefinition definition(
            String type, String key, int order) {
        return new ModuleCatalog.CatalogDefinition(type, key, key, key, order);
    }

    private static ModuleCatalog.CatalogAttribute text(
            String key, String attribute, int order, String value) {
        return new ModuleCatalog.CatalogAttribute("character.resource", key, attribute, order,
                "TEXT", new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute identifier(
            String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute("character.resource", key, attribute, 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue(value));
    }

    private static ModuleCatalog.CatalogRelation owner(String key) {
        return new ModuleCatalog.CatalogRelation("character.resource", key, "resource.owner",
                "character.class", "class.bard", 1);
    }

    private static LevelAdvancementRepository.ResourceState state(
            String key, long current, long maximum) {
        return new LevelAdvancementRepository.ResourceState(key, current, maximum, false);
    }

    private static ModuleCatalog copy(ModuleCatalog source,
            List<ModuleCatalog.CatalogAttribute> attributes) {
        return new ModuleCatalog(source.release(), source.ruleConstants(), source.fieldDefinitions(),
                source.classDefinitions(), source.proficiencyTiers(),
                source.proficiencyBonusBands(), source.skillDefinitions(), source.saveDefinitions(),
                source.itemTemplates(), source.entityTemplates(), source.entityTemplateValues(),
                source.entityTemplateClassLevels(), source.entityTemplateProficiencies(),
                source.checkDefinitions(), source.rollModes(), source.eventTemplates(),
                source.eventChecks(), source.eventEffects(), source.effectDefinitions(),
                source.effectParameters(), source.mapDefinitions(), source.mapNodes(),
                source.mapConnections(), source.catalogDefinitions(), attributes,
                source.catalogRelations());
    }

    private static void assertCode(String code,
            org.junit.jupiter.api.function.Executable executable) {
        ClassResourceRecoveryRules.RuleException exception = assertThrows(
                ClassResourceRecoveryRules.RuleException.class, executable);
        assertEquals(code, exception.code());
    }
}
