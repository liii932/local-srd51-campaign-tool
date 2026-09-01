package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassFeatureAdjudicationRulesTest {
    @Test
    void acceptsOnlyTheServerBoundedDecisionKey() {
        ClassFeatureAdjudicationRules.Prepared prepared =
                new ClassFeatureAdjudicationRules().prepare(
                        catalog(), "feature.fighter.dm", "SUCCESS",
                        "adjudication.feature_use");

        assertEquals("BOUNDED_DM_ADJUDICATION_V1", prepared.executionAlgorithm());
        assertEquals("SUCCESS", prepared.decision());
    }

    @Test
    void automaticAndBlockedFeaturesCannotBeDisguisedAsDmAdjudication() {
        assertCode("AUTOMATIC_FEATURE_REQUIRES_TYPED_EFFECT", () ->
                new ClassFeatureAdjudicationRules().prepare(catalog(),
                        "feature.fighter.auto", "SUCCESS", "adjudication.feature_use"));
        assertCode("FEATURE_BLOCKED", () -> new ClassFeatureAdjudicationRules().prepare(
                catalog(), "feature.fighter.spellcasting", "SUCCESS",
                "adjudication.feature_use"));
    }

    @Test
    void unknownDecisionOrAlgorithmSpecificKeyFailsClosed() {
        assertCode("INVALID_REQUEST", () -> new ClassFeatureAdjudicationRules().prepare(
                catalog(), "feature.fighter.dm", "MAYBE", "adjudication.feature_use"));
        assertCode("INVALID_ADJUDICATION", () ->
                new ClassFeatureAdjudicationRules().prepare(catalog(),
                        "feature.fighter.dm", "SUCCESS", "adjudication.subclass_selection"));
    }

    private static ModuleCatalog catalog() {
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", null, "DRAFT");
        List<ModuleCatalog.CatalogDefinition> definitions = new ArrayList<>();
        List<ModuleCatalog.CatalogAttribute> attributes = new ArrayList<>();
        List<ModuleCatalog.CatalogRelation> relations = new ArrayList<>();
        definitions.add(definition("character.class", "class.fighter", 1));
        definitions.add(definition("character.subclass", "subclass.champion", 1));
        attributes.add(integer("character.subclass", "subclass.champion",
                "subclass.selection_level", 3));
        relations.add(new ModuleCatalog.CatalogRelation("character.subclass",
                "subclass.champion", "subclass.parent_class", "character.class",
                "class.fighter", 1));
        addFeature(definitions, attributes, relations, "feature.fighter.auto", 1,
                "AUTOMATIC", "AUTOMATIC_RESOURCE_LIFECYCLE_V1");
        addFeature(definitions, attributes, relations, "feature.fighter.dm", 2,
                "DM_ADJUDICATION", "BOUNDED_DM_ADJUDICATION_V1");
        addFeature(definitions, attributes, relations, "feature.fighter.spellcasting", 3,
                "BLOCKED", "BLOCKED_SPELL_SYSTEM_V1");
        return new ModuleCatalog(release, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), definitions, attributes, relations);
    }

    private static void addFeature(List<ModuleCatalog.CatalogDefinition> definitions,
            List<ModuleCatalog.CatalogAttribute> attributes,
            List<ModuleCatalog.CatalogRelation> relations, String key, int order,
            String mode, String algorithm) {
        definitions.add(definition("character.feature", key, order));
        attributes.add(integer("character.feature", key, "feature.level", 1));
        attributes.add(identifier(key, "catalog.category", "BASE"));
        attributes.add(identifier(key, "feature.execution_mode", mode));
        attributes.add(identifier(key, "feature.execution_algorithm", algorithm));
        relations.add(new ModuleCatalog.CatalogRelation("character.feature", key,
                "feature.owner", "character.class", "class.fighter", 1));
    }

    private static ModuleCatalog.CatalogDefinition definition(
            String type, String key, int order) {
        return new ModuleCatalog.CatalogDefinition(type, key, key, key, order);
    }

    private static ModuleCatalog.CatalogAttribute integer(
            String type, String key, String attribute, long value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "INTEGER",
                new ModuleCatalog.IntegerValue(value));
    }

    private static ModuleCatalog.CatalogAttribute identifier(
            String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute("character.feature", key, attribute, 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue(value));
    }

    private static void assertCode(String code,
            org.junit.jupiter.api.function.Executable executable) {
        ClassFeatureAdjudicationRules.RuleException exception = assertThrows(
                ClassFeatureAdjudicationRules.RuleException.class, executable);
        assertEquals(code, exception.code());
    }
}
