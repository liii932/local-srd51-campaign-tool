package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2GrowthRulesTest {
    private final Schema2CharacterProgressionRules rules = new Schema2CharacterProgressionRules();
    private final ProductionSchema2Projection material = fullProjection();

    @Test void completeGrowthRetainsChoicesBlockedAcquisitionMixedDiceAndTotalLevelBonus() {
        State first = initial(fullAbilities());
        assertEquals(11, first.maximumHitPoints()); assertEquals(2, first.proficiencyBonus());
        assertEquals(3, first.proficiencies().size());
        State second = advance(first, CLASS, GrowthChoices.EMPTY);
        assertTrue(second.features().stream().anyMatch(row -> row.feature().ruleKey().equals("feature.blocked")));
        assertTrue(second.features().stream().noneMatch(row -> row.feature().ruleKey().equals("feature.option")));
        assertCode("S2_GROWTH_SUBCLASS_REQUIRED", () -> prepare(second, CLASS, GrowthChoices.EMPTY));
        State third = advance(second, CLASS, choices(SUBCLASS, Map.of(), null));
        assertEquals(List.of(new SubclassState(CLASS, SUBCLASS, 3)), third.subclasses());
        State fourth = advance(third, CLASS, choices(null, Map.of("ability.constitution", 2), null));
        assertEquals(15, fourth.abilities().get("ability.constitution"));
        assertEquals(36, fourth.maximumHitPoints()); // 25 + fixed d10/CON +8 + three prior levels +3.
        State fifth = advance(fourth, ALT_CLASS, fullMulticlassChoices());
        assertEquals(5, fifth.level()); assertEquals(3, fifth.proficiencyBonus());
        assertEquals(List.of(new HitDicePool(6, 1, 1), new HitDicePool(10, 4, 4)), fifth.hitDicePools());
        assertEquals(2, fifth.classes().size());
        assertTrue(fifth.proficiencies().stream().anyMatch(row -> row.target().equals(WEAPON)));
        assertTrue(fifth.proficiencies().stream().anyMatch(row -> row.target().equals(ALT_WEAPON)));
        assertEquals(2, fifth.proficiencies().stream().filter(row -> row.target().equals(LIGHT_ARMOR)).count()); // two retained origins, one effective proficiency.
    }

    @Test void charismaAsiRecomputesEveryClassResourceAndPreservesItsSpentAmount() {
        State state = initial(fullAbilities());
        state = rules.spend(material, state, LATER, 1);
        state = advance(state, CLASS, GrowthChoices.EMPTY);
        state = advance(state, CLASS, choices(SUBCLASS, Map.of(), null));
        state = advance(state, CLASS, choices(null, Map.of("ability.constitution", 2), null));
        state = advance(state, ALT_CLASS, fullMulticlassChoices());
        state = rules.spend(material, state, EXT_ENERGY, 1);
        state = advance(state, CLASS, GrowthChoices.EMPTY);
        state = advance(state, CLASS, choices(null, Map.of("ability.charisma", 2), null));
        assertEquals(16, state.abilities().get("ability.charisma"));
        assertEquals(new ResourceState(LATER, 2, 3, false), resource(state, LATER));
        assertEquals(new ResourceState(EXT_ENERGY, 2, 3, false), resource(state, EXT_ENERGY));
        assertCode("S2_RESOURCE_REST_REQUIRED", () -> rules.recoverResources(material, initial(fullAbilities()), "LONG_REST"));
    }

    @Test void multiclassRequiresCurrentAndNewPrerequisitesAndPreservesOrSemantics() {
        var missingNew = new HashMap<>(fullAbilities()); missingNew.put("ability.charisma", 8); missingNew.put("ability.wisdom", 14);
        State weakCharisma = initial(missingNew);
        assertCode("S2_GROWTH_PREREQUISITE", () -> prepare(weakCharisma, ALT_CLASS, fullMulticlassChoices()));
        var missingCurrent = new HashMap<>(fullAbilities()); missingCurrent.put("ability.strength", 8); missingCurrent.put("ability.wisdom", 15);
        State weakCurrent = initial(missingCurrent);
        assertCode("S2_GROWTH_PREREQUISITE", () -> prepare(weakCurrent, ALT_CLASS, fullMulticlassChoices()));
        var dexterity = new HashMap<>(missingCurrent); dexterity.put("ability.dexterity", 15); dexterity.put("ability.wisdom", 12);
        State dexterityQualified = initial(dexterity);
        assertEquals(2, advance(dexterityQualified, ALT_CLASS, fullMulticlassChoices()).level());
    }

    @Test void asiMustBeDueAndCompleteAndProficiencyChoicesCannotRepeatKnownTargets() {
        State first = initial(fullAbilities());
        assertCode("S2_GROWTH_UNEXPECTED_ASI", () -> prepare(first, CLASS, choices(null, Map.of("ability.constitution", 2), null)));
        var duplicateSkill = new GrowthChoices(ALT_SUBCLASS, Map.of(), null, Map.of(MULTICLASS_SKILL_SLOT, List.of(ATHLETICS)));
        assertCode("S2_GROWTH_PROFICIENCY_DUPLICATE", () -> prepare(first, ALT_CLASS, duplicateSkill));
        State third = advance(advance(first, CLASS, GrowthChoices.EMPTY), CLASS, choices(SUBCLASS, Map.of(), null));
        assertCode("S2_GROWTH_ASI_REQUIRED", () -> prepare(third, CLASS, GrowthChoices.EMPTY));
        State fourth = advance(third, CLASS, choices(null,
                Map.of("ability.constitution", 1, "ability.charisma", 1), null));
        assertEquals(14, fourth.abilities().get("ability.constitution"));
        assertEquals(15, fourth.abilities().get("ability.charisma"));
        assertEquals(4, fourth.level());
        assertEquals(36, fourth.maximumHitPoints());
    }

    @Test void closedChoiceInputRejectsAnIncompleteAsiBeforeGrowthPlanning() {
        assertCode("S2_CHARACTER_CHOICES", () -> choices(null, Map.of("ability.constitution", 1), null));
    }

    @Test void featAcquisitionIsPersistedWithoutActivatingItsBlockedCombatEffectAndCannotRepeat() {
        State state = advance(advance(initial(fullAbilities()), CLASS, GrowthChoices.EMPTY), CLASS, choices(SUBCLASS, Map.of(), null));
        state = advance(state, CLASS, choices(null, Map.of(), GRAPPLER));
        assertEquals(List.of(new FeatState(GRAPPLER, CLASS, 4)), state.feats());
        state = advance(state, ALT_CLASS, fullMulticlassChoices());
        state = advance(advance(state, ALT_CLASS, GrowthChoices.EMPTY), ALT_CLASS, GrowthChoices.EMPTY);
        State due = state;
        assertCode("S2_GROWTH_FEAT_DUPLICATE", () -> prepare(due, ALT_CLASS, choices(null, Map.of(), GRAPPLER)));
    }

    @Test void restoreValidationRejectsMissingFeatureProficiencyAndForgedMixedDiceFacts() {
        State state = advance(initial(fullAbilities()), ALT_CLASS, fullMulticlassChoices());
        State noFeatures = replace(state, state.hitDicePools(), List.of(), state.proficiencies());
        assertCode("S2_GROWTH_FEATURE_STATE", () -> rules.validateState(material, noFeatures));
        State noGrants = replace(state, state.hitDicePools(), state.features(), List.of());
        assertCode("S2_GROWTH_PROFICIENCY_STATE", () -> rules.validateState(material, noGrants));
        State wrongDice = replace(state, List.of(new HitDicePool(6, 2, 2)), state.features(), state.proficiencies());
        assertCode("S2_GROWTH_STATE", () -> rules.validateState(material, wrongDice));
    }

    @Test void anUnsupportedRequiredFeatureSelectionRejectsTheWholeGrowthBeforeAPlanCanBePrepared() {
        var files = fullPackages();
        com.dndtool.module.composition.Schema2GrantFixtures.edit(files, 0, "rules.json", root -> {
            for (var item : root.getAsJsonArray()) {
                var row = item.getAsJsonObject();
                if (row.getAsJsonObject("identity").get("rule_key").getAsString().equals("feature.blocked")) {
                    row.addProperty("execution_mode", "DM_ADJUDICATION");
                    row.addProperty("execution_algorithm", "BOUNDED_FEATURE_SELECTION_V1");
                }
            }
        });
        var requiringSelection = ProductionSchema2Projection.compose(com.dndtool.module.composition.Schema2GrantFixtures.artifacts(files));
        State first = rules.initial(requiringSelection, fullPlan(requiringSelection,
                new Identity(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), fullAbilities(), fullInitialChoices());
        assertCode("S2_GROWTH_FEATURE_SELECTION_UNSUPPORTED", () -> rules.prepareAdvance(requiringSelection,
                first, CLASS, 2, "SERVER_ROLL", GrowthChoices.EMPTY));
        assertEquals(1, first.level()); assertEquals(0, first.version());
    }

    @Test void initialToolGrantsRetainTheirOriginalSourceSlotsInTheGrowthProficiencyBaseline() {
        var files = fullPackages();
        var tool = new RuleIdentity(BASE, "character.tool", "tool.flute");
        var slot = new EntryIdentity(CLASS, "proficiency_grants", BASE, "instrument");
        com.dndtool.module.composition.Schema2GrantFixtures.edit(files, 0, "rules.json", root -> {
            var row = new com.google.gson.JsonObject(); row.add("identity", jsonIdentity(tool));
            row.addProperty("display_name", "Flute"); row.addProperty("description", "A flute.");
            row.addProperty("category", "MUSICAL_INSTRUMENT"); row.addProperty("source_page", 56); root.getAsJsonArray().add(row);
        });
        com.dndtool.module.composition.Schema2GrantFixtures.edit(files, 0, "growth.json", root -> {
            var row = new com.google.gson.JsonObject(); var identity = new com.google.gson.JsonObject();
            identity.addProperty("kind", "entry"); identity.add("rule", jsonIdentity(CLASS));
            identity.addProperty("collection_key", "proficiency_grants"); identity.addProperty("module_key", BASE); identity.addProperty("entry_key", "instrument");
            row.add("identity", identity); var grant = new com.google.gson.JsonObject(); grant.addProperty("category", "TOOL");
            grant.addProperty("kind", "FIXED"); grant.add("target", jsonIdentity(tool)); row.add("grant", grant); root.getAsJsonArray().add(row);
        });
        var combined = ProductionSchema2Projection.compose(com.dndtool.module.composition.Schema2GrantFixtures.artifacts(files));
        State first = rules.initial(combined, fullPlan(combined, new Identity(UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), fullAbilities(), fullInitialChoices());
        assertTrue(first.proficiencies().contains(new ProficiencyState(slot, tool, 1)));
        assertSame(first, rules.validateState(combined, first));
    }

    private State initial(Map<String, Integer> abilities) {
        return rules.initial(material, fullPlan(material, new Identity(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), abilities, fullInitialChoices());
    }
    private Schema2CharacterProgressionRules.PreparedAdvance prepare(State state, RuleIdentity target, GrowthChoices choices) {
        var prepared = rules.prepareAdvance(material, state, target, state.level() + 1, "FIXED_AVERAGE", choices);
        assertEquals(ProgressionScope.CHARACTER_ADVANCEMENT, prepared.scope()); return prepared;
    }
    private State advance(State state, RuleIdentity target, GrowthChoices choices) { return rules.advance(material, prepare(state, target, choices), null); }
    private static GrowthChoices choices(RuleIdentity subclass, Map<String, Integer> increases, RuleIdentity feat) {
        return new GrowthChoices(subclass, increases, feat, Map.of());
    }
    private static ResourceState resource(State state, RuleIdentity identity) { return state.resources().stream().filter(row -> row.identity().equals(identity)).findFirst().orElseThrow(); }
    private static State replace(State state, List<HitDicePool> dice, List<FeatureState> features, List<ProficiencyState> grants) {
        return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(), state.characterClass(),
                state.abilities(), state.level(), state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(), state.classes(), dice,
                state.subclasses(), features, state.feats(), grants, state.version(), state.resources(), state.rest());
    }
    private static void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(IllegalArgumentException.class, action).getMessage());
    }
}
