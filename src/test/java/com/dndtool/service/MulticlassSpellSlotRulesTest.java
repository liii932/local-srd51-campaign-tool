package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MulticlassSpellSlotRulesTest {
    private final MulticlassSpellSlotRules rules = new MulticlassSpellSlotRules();

    @Test
    void combinesFullAndGroupedHalfCasterLevelsWhileKeepingPactMagicSeparate() {
        MulticlassSpellSlotRules.Prepared prepared = rules.prepare(
                catalog(
                        entry("class.bard", "FULL"),
                        entry("class.paladin", "HALF_DOWN"),
                        entry("class.ranger", "HALF_DOWN"),
                        entry("class.warlock", "PACT_MAGIC")),
                List.of(
                        level("class.bard", 3),
                        level("class.paladin", 3),
                        level("class.ranger", 3),
                        level("class.warlock", 2)));

        assertTrue(prepared.sharedAggregationApplicable());
        assertEquals(6, prepared.effectiveSpellcasterLevel());
        assertEquals(List.of(
                new MulticlassSpellSlotRules.SpellSlotMaximum(1, 4),
                new MulticlassSpellSlotRules.SpellSlotMaximum(2, 3),
                new MulticlassSpellSlotRules.SpellSlotMaximum(3, 3)),
                prepared.sharedSpellSlots());
        assertTrue(prepared.hasPactMagic());
    }

    @Test
    void supportsTheLevelTwentySharedSlotBoundary() {
        MulticlassSpellSlotRules.Prepared prepared = rules.prepare(
                catalog(entry("class.wizard", "FULL"), entry("class.bard", "FULL")),
                List.of(level("class.wizard", 19), level("class.bard", 1)));

        assertTrue(prepared.sharedAggregationApplicable());
        assertEquals(20, prepared.effectiveSpellcasterLevel());
        assertEquals(List.of(4, 3, 3, 3, 3, 2, 2, 1, 1),
                prepared.sharedSpellSlots().stream()
                        .map(MulticlassSpellSlotRules.SpellSlotMaximum::maximum).toList());
        assertFalse(prepared.hasPactMagic());
    }

    @Test
    void nonCastersAndWarlockDoNotInventSharedSlots() {
        MulticlassSpellSlotRules.Prepared prepared = rules.prepare(
                catalog(entry("class.fighter", "NONE"),
                        entry("class.warlock", "PACT_MAGIC")),
                List.of(level("class.fighter", 4), level("class.warlock", 3)));

        assertFalse(prepared.sharedAggregationApplicable());
        assertEquals(0, prepared.effectiveSpellcasterLevel());
        assertEquals(List.of(), prepared.sharedSpellSlots());
        assertTrue(prepared.hasPactMagic());
    }

    @Test
    void doesNotApplySharedTableUntilTwoClassesHaveTheSpellcastingFeature() {
        ModuleCatalog catalog = catalog(entry("class.bard", "FULL"),
                entry("class.paladin", "HALF_DOWN"),
                entry("class.ranger", "HALF_DOWN"));

        MulticlassSpellSlotRules.Prepared notApplicable = rules.prepare(catalog,
                List.of(level("class.bard", 1), level("class.paladin", 1),
                        level("class.ranger", 1)));
        assertFalse(notApplicable.sharedAggregationApplicable());
        assertEquals(List.of(), notApplicable.sharedSpellSlots());

        MulticlassSpellSlotRules.Prepared applicable = rules.prepare(catalog,
                List.of(level("class.bard", 1), level("class.paladin", 2),
                        level("class.ranger", 1)));
        assertTrue(applicable.sharedAggregationApplicable());
        assertEquals(2, applicable.effectiveSpellcasterLevel());
        assertEquals(List.of(3), applicable.sharedSpellSlots().stream()
                .map(MulticlassSpellSlotRules.SpellSlotMaximum::maximum).toList());
    }

    @Test
    void rejectsMalformedFrozenProgressions() {
        ModuleCatalog valid = catalog(entry("class.bard", "FULL"),
                entry("class.fighter", "NONE"));
        List<ModuleCatalog.CatalogAttribute> duplicateAttributes =
                new ArrayList<>(valid.catalogAttributes());
        duplicateAttributes.add(progression("class.bard", "FULL", 2));
        ModuleCatalog duplicate = copy(valid, duplicateAttributes);

        assertCode("MALFORMED_FROZEN_CATALOG", () -> rules.prepare(duplicate,
                List.of(level("class.bard", 1), level("class.fighter", 1))));

        ModuleCatalog unknown = catalog(entry("class.bard", "CLIENT_DEFINED"),
                entry("class.fighter", "NONE"));
        assertCode("MALFORMED_FROZEN_CATALOG", () -> rules.prepare(unknown,
                List.of(level("class.bard", 1), level("class.fighter", 1))));
    }

    @Test
    void rejectsInvalidAuthoritativeClassLevelSetsBeforeCalculation() {
        ModuleCatalog catalog = catalog(entry("class.bard", "FULL"),
                entry("class.fighter", "NONE"));

        assertCode("INVALID_REQUEST", () -> rules.prepare(catalog,
                List.of(level("class.bard", 2))));
        assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> rules.prepare(catalog,
                List.of(level("class.bard", 1), level("class.bard", 1))));
        assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> rules.prepare(catalog,
                List.of(level("class.bard", 20), level("class.fighter", 1))));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "2|3", "3|4,2", "4|4,3", "5|4,3,2", "6|4,3,3",
        "7|4,3,3,1", "8|4,3,3,2", "9|4,3,3,3,1", "10|4,3,3,3,2",
        "11|4,3,3,3,2,1", "12|4,3,3,3,2,1", "13|4,3,3,3,2,1,1",
        "14|4,3,3,3,2,1,1", "15|4,3,3,3,2,1,1,1",
        "16|4,3,3,3,2,1,1,1", "17|4,3,3,3,2,1,1,1,1",
        "18|4,3,3,3,3,1,1,1,1", "19|4,3,3,3,3,2,1,1,1",
        "20|4,3,3,3,3,2,2,1,1"
    })
    void matchesEveryReachableSharedSpellcasterLevel(int casterLevel, String expected) {
        var result = rules.prepare(catalog(entry("class.bard", "FULL"),
                        entry("class.wizard", "FULL")),
                List.of(level("class.bard", 1), level("class.wizard", casterLevel - 1)));

        assertTrue(result.sharedAggregationApplicable());
        assertEquals(casterLevel, result.effectiveSpellcasterLevel());
        assertEquals(expected, result.sharedSpellSlots().stream()
                .map(slot -> Integer.toString(slot.maximum()))
                .collect(java.util.stream.Collectors.joining(",")));
        for (int index = 0; index < result.sharedSpellSlots().size(); index++) {
            assertEquals(index + 1, result.sharedSpellSlots().get(index).spellLevel());
        }
        assertThrows(UnsupportedOperationException.class, () -> result.sharedSpellSlots().clear());
    }

    @Test
    void groupsHalfCastersBeforeRoundingAndKeepsPactMagicOutOfApplicability() {
        var halves = rules.prepare(catalog(entry("class.paladin", "HALF_DOWN"),
                        entry("class.ranger", "HALF_DOWN")),
                List.of(level("class.paladin", 3), level("class.ranger", 3)));
        assertTrue(halves.sharedAggregationApplicable());
        assertEquals(3, halves.effectiveSpellcasterLevel());

        var oddSum = rules.prepare(catalog(entry("class.paladin", "HALF_DOWN"),
                        entry("class.ranger", "HALF_DOWN")),
                List.of(level("class.paladin", 2), level("class.ranger", 3)));
        assertEquals(2, oddSum.effectiveSpellcasterLevel());

        var pact = rules.prepare(catalog(entry("class.bard", "FULL"),
                        entry("class.warlock", "PACT_MAGIC")),
                List.of(level("class.bard", 3), level("class.warlock", 3)));
        assertFalse(pact.sharedAggregationApplicable());
        assertTrue(pact.hasPactMagic());
        assertEquals(0, pact.effectiveSpellcasterLevel());
        assertEquals(List.of(), pact.sharedSpellSlots());
    }

    @Test
    void rejectsMissingDuplicateAndMistypedFrozenData() {
        var levels = List.of(level("class.bard", 1), level("class.fighter", 1));
        var valid = catalog(entry("class.bard", "FULL"), entry("class.fighter", "NONE"));
        assertCode("MALFORMED_FROZEN_CATALOG", () -> rules.prepare(
                catalog(entry("class.fighter", "NONE")), levels));
        assertCode("MALFORMED_FROZEN_CATALOG", () -> rules.prepare(
                catalog(entry("class.bard", "FULL"), entry("class.bard", "FULL"),
                        entry("class.fighter", "NONE")), levels));
        assertCode("MALFORMED_FROZEN_CATALOG", () -> rules.prepare(
                copy(valid, List.of(progression("class.fighter", "NONE", 1))), levels));
        assertCode("MALFORMED_FROZEN_CATALOG", () -> rules.prepare(
                copy(valid, List.of(progression("class.bard", "FULL", 2),
                        progression("class.fighter", "NONE", 1))), levels));
        assertCode("MALFORMED_FROZEN_CATALOG", () -> rules.prepare(
                copy(valid, List.of(new ModuleCatalog.CatalogAttribute(
                        "character.class", "class.bard", MulticlassSpellSlotRules.PROGRESSION_ATTRIBUTE,
                        1, "TEXT", new ModuleCatalog.TextValue("FULL")),
                        progression("class.fighter", "NONE", 1))), levels));
    }

    @Test
    void rejectsNullMalformedAndOutOfRangeAuthoritativeInputs() {
        var valid = catalog(entry("class.bard", "FULL"), entry("class.fighter", "NONE"));
        assertCode("INVALID_REQUEST", () -> rules.prepare(null,
                List.of(level("class.bard", 1), level("class.fighter", 1))));
        assertCode("INVALID_REQUEST", () -> rules.prepare(valid, null));
        assertCode("INVALID_REQUEST", () -> rules.prepare(valid, List.of()));
        for (int invalidLevel : List.of(-1, 0, 21, Integer.MAX_VALUE)) {
            assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> rules.prepare(valid,
                    List.of(level("class.bard", invalidLevel), level("class.fighter", 1))));
        }
        for (String invalidKey : java.util.Arrays.asList(null, "", "class.Bard", "class.bard\n")) {
            assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> rules.prepare(valid,
                    List.of(level(invalidKey, 1), level("class.fighter", 1))));
        }
        assertCode("AUTHORITATIVE_STATE_MISMATCH", () -> rules.prepare(valid,
                java.util.Arrays.asList(null, level("class.fighter", 1))));
    }

    private static MulticlassSpellSlotRules.ClassLevel level(String key, int level) {
        return new MulticlassSpellSlotRules.ClassLevel(key, level);
    }

    private static Entry entry(String key, String progression) {
        return new Entry(key, progression);
    }

    private static ModuleCatalog catalog(Entry... entries) {
        List<ModuleCatalog.CatalogDefinition> definitions = new ArrayList<>();
        List<ModuleCatalog.CatalogAttribute> attributes = new ArrayList<>();
        for (int index = 0; index < entries.length; index++) {
            Entry entry = entries[index];
            definitions.add(new ModuleCatalog.CatalogDefinition(
                    "character.class", entry.classKey(), entry.classKey(),
                    entry.classKey(), index + 1));
            attributes.add(progression(entry.classKey(), entry.progression(), 1));
        }
        return new ModuleCatalog(
                new ModuleCatalog.Release("dnd5e2014_srd51_se", "1", 2,
                        "SHA-256", null, "DRAFT"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), definitions, attributes, List.of());
    }

    private static ModuleCatalog.CatalogAttribute progression(
            String classKey, String value, int order) {
        return new ModuleCatalog.CatalogAttribute(
                "character.class", classKey, "class.multiclass_spellcasting_progression",
                order, "IDENTIFIER", new ModuleCatalog.IdentifierValue(value));
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

    private static void assertCode(String expected,
            org.junit.jupiter.api.function.Executable action) {
        MulticlassSpellSlotRules.RuleException exception = assertThrows(
                MulticlassSpellSlotRules.RuleException.class, action);
        assertEquals(expected, exception.code());
    }

    private record Entry(String classKey, String progression) {
    }
}
