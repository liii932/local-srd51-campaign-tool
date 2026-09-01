package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ClassFeatureRulesTest {
    private static final List<String> CLASSES = List.of(
            "barbarian", "bard", "cleric", "druid", "fighter", "monk",
            "paladin", "ranger", "rogue", "sorcerer", "warlock", "wizard");

    @ParameterizedTest
    @MethodSource("classes")
    void everyClassAndSubclassFeatureHasExactlyOneClosedDisposition(
            String classKey, String subclassKey, int selectionLevel) {
        ClassFeatureRules.Matrix matrix = new ClassFeatureRules().inspect(catalog());

        assertEquals(6, matrix.features().stream()
                .filter(feature -> classKey.equals(feature.ownerKey())
                        || subclassKey.equals(feature.ownerKey())).count());
        assertEquals(2L, matrix.dispositionCountsByClass().get(classKey)
                .get(ClassFeatureRules.AUTOMATIC));
        assertEquals(2L, matrix.dispositionCountsByClass().get(classKey)
                .get(ClassFeatureRules.DM_ADJUDICATION));
        assertEquals(2L, matrix.dispositionCountsByClass().get(classKey)
                .get(ClassFeatureRules.BLOCKED));
        assertEquals(selectionLevel, matrix.subclasses().stream()
                .filter(rule -> subclassKey.equals(rule.subclassKey()))
                .findFirst().orElseThrow().selectionLevel());
    }

    @Test
    void subclassSelectionIsRequiredOnlyAtItsFrozenLevelAndUnlocksAreServerDerived() {
        ClassFeatureRules rules = new ClassFeatureRules();
        assertCode("SUBCLASS_SELECTION_REQUIRED", () -> rules.transition(
                catalog(), "class.fighter", 2, 3, null, null));

        ClassFeatureRules.Transition transition = rules.transition(
                catalog(), "class.fighter", 2, 3, null, "subclass.fighter_srd");

        assertEquals("subclass.fighter_srd", transition.newlySelectedSubclassKey());
        assertEquals(List.of(
                        "feature.fighter.auto",
                        "feature.fighter.blocked",
                        "feature.fighter.dm",
                        "feature.fighter_srd.auto",
                        "feature.fighter_srd.blocked",
                        "feature.fighter_srd.dm"),
                transition.featureUnlocks().stream()
                        .map(ClassFeatureRules.FeatureRule::featureKey).sorted().toList());
        assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> rules.transition(
                catalog(), "class.fighter", 3, 4, null, null));
    }

    @Test
    void duplicateDispositionAttributeAndWrongModeAlgorithmPairFailClosed() {
        ModuleCatalog valid = catalog();
        List<ModuleCatalog.CatalogAttribute> duplicate = new ArrayList<>(
                valid.catalogAttributes());
        duplicate.add(identifier("character.feature", "feature.fighter.auto",
                "feature.execution_mode", 2, "AUTOMATIC"));
        assertCode("MALFORMED_FROZEN_CATALOG",
                () -> new ClassFeatureRules().inspect(copy(valid, duplicate)));

        List<ModuleCatalog.CatalogAttribute> mismatched = new ArrayList<>(
                valid.catalogAttributes());
        int index = find(mismatched, "feature.fighter.auto", "feature.execution_algorithm");
        mismatched.set(index, identifier("character.feature", "feature.fighter.auto",
                "feature.execution_algorithm", 1, "BLOCKED_SPELL_SYSTEM_V1"));
        assertCode("MALFORMED_FROZEN_CATALOG",
                () -> new ClassFeatureRules().inspect(copy(valid, mismatched)));
    }

    private static Stream<Arguments> classes() {
        return CLASSES.stream().map(name -> Arguments.of(
                "class." + name, "subclass." + name + "_srd", selectionLevel(name)));
    }

    private static int selectionLevel(String name) {
        return switch (name) {
            case "cleric", "sorcerer", "warlock" -> 1;
            case "druid", "wizard" -> 2;
            default -> 3;
        };
    }

    private static ModuleCatalog catalog() {
        List<ModuleCatalog.CatalogDefinition> definitions = new ArrayList<>();
        List<ModuleCatalog.CatalogAttribute> attributes = new ArrayList<>();
        List<ModuleCatalog.CatalogRelation> relations = new ArrayList<>();
        int classOrder = 1;
        int subclassOrder = 1;
        int featureOrder = 1;
        for (String name : CLASSES) {
            String classKey = "class." + name;
            String subclassKey = "subclass." + name + "_srd";
            definitions.add(definition("character.class", classKey, classOrder++));
            definitions.add(definition("character.subclass", subclassKey, subclassOrder++));
            attributes.add(integer("character.subclass", subclassKey,
                    "subclass.selection_level", selectionLevel(name)));
            relations.add(new ModuleCatalog.CatalogRelation("character.subclass", subclassKey,
                    "subclass.parent_class", "character.class", classKey, 1));
            featureOrder = addFeature(definitions, attributes, relations, featureOrder,
                    "feature." + name + ".auto", "character.class", classKey,
                    "AUTOMATIC", "AUTOMATIC_RESOURCE_LIFECYCLE_V1");
            featureOrder = addFeature(definitions, attributes, relations, featureOrder,
                    "feature." + name + ".dm", "character.class", classKey,
                    "DM_ADJUDICATION", "BOUNDED_DM_ADJUDICATION_V1");
            featureOrder = addFeature(definitions, attributes, relations, featureOrder,
                    "feature." + name + ".blocked", "character.class", classKey,
                    "BLOCKED", "BLOCKED_DOWNSTREAM_SYSTEM_V1");
            featureOrder = addFeature(definitions, attributes, relations, featureOrder,
                    "feature." + name + "_srd.auto", "character.subclass", subclassKey,
                    "AUTOMATIC", "AUTOMATIC_RESOURCE_LIFECYCLE_V1");
            featureOrder = addFeature(definitions, attributes, relations, featureOrder,
                    "feature." + name + "_srd.dm", "character.subclass", subclassKey,
                    "DM_ADJUDICATION", "BOUNDED_DM_ADJUDICATION_V1");
            featureOrder = addFeature(definitions, attributes, relations, featureOrder,
                    "feature." + name + "_srd.blocked", "character.subclass", subclassKey,
                    "BLOCKED", "BLOCKED_SPELL_SYSTEM_V1");
        }
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", null, "DRAFT");
        return new ModuleCatalog(release, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), definitions, attributes, relations);
    }

    private static int addFeature(List<ModuleCatalog.CatalogDefinition> definitions,
            List<ModuleCatalog.CatalogAttribute> attributes,
            List<ModuleCatalog.CatalogRelation> relations, int order, String featureKey,
            String ownerType, String ownerKey, String mode, String algorithm) {
        definitions.add(definition("character.feature", featureKey, order));
        attributes.add(integer("character.feature", featureKey, "feature.level", 3));
        attributes.add(identifier("character.feature", featureKey,
                "catalog.category", 1, "BASE"));
        attributes.add(identifier("character.feature", featureKey,
                "feature.execution_mode", 1, mode));
        attributes.add(identifier("character.feature", featureKey,
                "feature.execution_algorithm", 1, algorithm));
        relations.add(new ModuleCatalog.CatalogRelation("character.feature", featureKey,
                "feature.owner", ownerType, ownerKey, 1));
        return order + 1;
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
            String type, String key, String attribute, int order, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, order, "IDENTIFIER",
                new ModuleCatalog.IdentifierValue(value));
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

    private static int find(List<ModuleCatalog.CatalogAttribute> values,
            String definitionKey, String attributeKey) {
        for (int index = 0; index < values.size(); index++) {
            ModuleCatalog.CatalogAttribute value = values.get(index);
            if (definitionKey.equals(value.definitionKey())
                    && attributeKey.equals(value.attributeKey())) return index;
        }
        throw new AssertionError("missing fixture attribute");
    }

    private static void assertCode(String code,
            org.junit.jupiter.api.function.Executable executable) {
        ClassFeatureRules.RuleException exception = assertThrows(
                ClassFeatureRules.RuleException.class, executable);
        assertEquals(code, exception.code());
    }
}
