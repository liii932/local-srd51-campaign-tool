package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2PreparationIdentity;
import com.google.gson.JsonObject;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.SUBRACE_HIT_POINTS_ALGORITHM;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2GrowthRulesTest {
    private static final RuleIdentity DWARF = new RuleIdentity(BASE, "character.race", "race.dwarf");
    private static final RuleIdentity HILL_DWARF = new RuleIdentity(BASE, "character.subrace", "subrace.hill_dwarf");
    private final Schema2CharacterProgressionRules rules = new Schema2CharacterProgressionRules();
    private final ProductionSchema2Projection material = fullProjection();
    private final InitialPlan retainedPlan = fullPlan(material, Schema2PreparationIdentity.random(), UUID.randomUUID(), UUID.randomUUID());

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

    @Test void selectedSubraceAddsIndependentFirstAndLaterHpThroughFixedRollAsiAndMulticlass() {
        var combined = materialWithSubrace(fullPackages(), 2, 3);
        var plan = dwarfPlan(combined, HILL_DWARF);
        State first = rules.initial(combined, plan, UUID.randomUUID(), fullAbilities(), fullInitialChoices());
        assertEquals(13, first.maximumHitPoints());
        assertEquals(13, first.hitPoints());
        assertEquals(1, first.maximumHitDice());
        assertEquals(11, rules.initial(combined, dwarfPlan(combined, null), UUID.randomUUID(),
                fullAbilities(), fullInitialChoices()).maximumHitPoints());

        State injured = withVitality(first, 11, 13, first.health());
        var fixed = rules.prepareAdvance(combined, plan, injured, CLASS, 2, "FIXED_AVERAGE");
        assertEquals(10, fixed.minimumHitPointIncrease());
        assertEquals(10, fixed.maximumHitPointIncrease());
        State second = rules.advance(combined, fixed, null);
        assertEquals(23, second.maximumHitPoints());
        assertEquals(21, second.hitPoints());

        var rolled = rules.prepareAdvance(combined, plan, second, CLASS, 3, "SERVER_ROLL", choices(SUBCLASS, Map.of(), null));
        assertEquals(5, rolled.minimumHitPointIncrease());
        assertEquals(14, rolled.maximumHitPointIncrease());
        State third = rules.advance(combined, rolled, 4);
        assertEquals(31, third.maximumHitPoints());
        assertEquals(29, third.hitPoints());

        var asi = choices(null, Map.of("ability.constitution", 2), null);
        State fourth = rules.advance(combined, rules.prepareAdvance(combined, plan, third, CLASS, 4, "FIXED_AVERAGE", asi), null);
        assertEquals(45, fourth.maximumHitPoints());
        assertEquals(43, fourth.hitPoints());
        var mixed = rules.prepareAdvance(combined, plan, fourth, ALT_CLASS, 5, "FIXED_AVERAGE", fullMulticlassChoices());
        State fifth = rules.advance(combined, mixed, null);
        assertEquals(54, fifth.maximumHitPoints());
        assertEquals(52, fifth.hitPoints());
        assertEquals(List.of(new HitDicePool(6, 1, 1), new HitDicePool(10, 4, 4)), fifth.hitDicePools());
    }

    @Test void selectedSubraceCannotBypassPlanHealthOrMaximumRollPreflight() {
        var combined = materialWithSubrace(fullPackages(), 2, 3);
        var plan = dwarfPlan(combined, HILL_DWARF);
        State first = rules.initial(combined, plan, UUID.randomUUID(), fullAbilities(), fullInitialChoices());
        assertCode("S2_GROWTH_INITIAL_BINDING", () -> rules.prepareAdvance(combined, null, first, CLASS, 2, "SERVER_ROLL"));
        var other = dwarfPlan(combined, HILL_DWARF);
        assertCode("S2_GROWTH_INITIAL_BINDING", () -> rules.prepareAdvance(combined, other, first, CLASS, 2, "SERVER_ROLL"));
        assertCode("S2_RULES_BINDING_MISMATCH", () -> rules.prepareAdvance(material, plan, first, CLASS, 2, "SERVER_ROLL"));

        State atZero = withVitality(first, 0, first.maximumHitPoints(), HealthState.INITIAL);
        assertCode("S2_HEALTH_UNCONSCIOUS", () -> rules.prepareAdvance(combined, plan, atZero, CLASS, 2, "SERVER_ROLL"));
        State dead = withVitality(first, 0, first.maximumHitPoints(), new HealthState(0, 0, 0, false, true, null));
        assertCode("S2_HEALTH_DEAD", () -> rules.prepareAdvance(combined, plan, dead, CLASS, 2, "SERVER_ROLL"));
        State nearMaximum = withVitality(first, 65_524, 65_524, first.health());
        assertCode("S2_GROWTH_HIT_POINT_LIMIT", () -> rules.prepareAdvance(combined, plan, nearMaximum, CLASS, 2, "SERVER_ROLL"));
    }

    @Test void additionalLevelBonusAppliesAfterTheMinimumHitPointGain() {
        var combined = materialWithSubrace(fullPackages(), 0, 3);
        var plan = dwarfPlan(combined, HILL_DWARF);
        var abilities = new HashMap<>(fullAbilities());
        abilities.put("ability.constitution", 8);
        abilities.put("ability.wisdom", 13);
        State first = rules.initial(combined, plan, UUID.randomUUID(), abilities, fullInitialChoices());
        var advance = rules.prepareAdvance(combined, plan, first, CLASS, 2, "SERVER_ROLL");
        assertEquals(4, advance.minimumHitPointIncrease());
        assertEquals(first.maximumHitPoints() + 4, rules.advance(combined, advance, 1).maximumHitPoints());
    }

    @Test void statisticsOnlyScopeRejectsSelectedSubraceHitPointsAtCreationAndAdvance() {
        var limited = materialWithSubrace(packages(), 2, 3);
        var hillDwarf = dwarfPlan(limited, HILL_DWARF);
        assertCode("S2_GROWTH_SUBRACE_HIT_POINTS_SCOPE",
                () -> rules.initial(limited, hillDwarf, UUID.randomUUID(), abilities()));
        var basePlan = dwarfPlan(limited, null);
        State dwarf = rules.initial(limited, basePlan, UUID.randomUUID(), abilities());
        var wrongPlan = new InitialPlan(basePlan.campaignId(), basePlan.characterId(),
                new Schema2InitialCharacterPlanService().prepareMaterial(basePlan.character().identity(), Schema2RulesBinding.from(limited),
                        limited, new Schema2InitialProficiencyRules.Request(hillDwarf.character().sources(), Map.of())));
        assertCode("S2_GROWTH_SUBRACE_HIT_POINTS_SCOPE",
                () -> rules.prepareAdvance(limited, wrongPlan, dwarf, CLASS, 2, "SERVER_ROLL"));
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
        var plan = fullPlan(requiringSelection, Schema2PreparationIdentity.random(), UUID.randomUUID(), UUID.randomUUID());
        State first = rules.initial(requiringSelection, plan, UUID.randomUUID(), fullAbilities(), fullInitialChoices());
        assertCode("S2_GROWTH_FEATURE_SELECTION_UNSUPPORTED", () -> rules.prepareAdvance(requiringSelection, plan,
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
        State first = rules.initial(combined, fullPlan(combined, new Schema2PreparationIdentity(UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), fullAbilities(), fullInitialChoices());
        assertTrue(first.proficiencies().contains(new ProficiencyState(slot, tool, 1)));
        assertSame(first, rules.validateState(combined, first));
    }

    private static ProductionSchema2Projection materialWithSubrace(List<Map<String, byte[]>> files, int firstBonus, int laterBonus) {
        Schema2GrantFixtures.edit(files, 0, "rules.json", root -> {
            var rows = root.getAsJsonArray();
            rows.add(ancestryDefinition(DWARF));
            var hillDwarf = ancestryDefinition(HILL_DWARF);
            hillDwarf.add("parent_race", jsonIdentity(DWARF));
            rows.add(hillDwarf);
            var hitPoints = ancestryDefinition(new RuleIdentity(BASE, "character.subrace_hit_points", "subrace_hit_points.hill_dwarf"));
            hitPoints.add("owner", jsonIdentity(HILL_DWARF));
            hitPoints.addProperty("algorithm", SUBRACE_HIT_POINTS_ALGORITHM);
            hitPoints.addProperty("first_level_bonus", firstBonus);
            hitPoints.addProperty("per_additional_level_bonus", laterBonus);
            rows.add(hitPoints);
        });
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }
    private static JsonObject ancestryDefinition(RuleIdentity identity) {
        var row = new JsonObject();
        row.add("identity", jsonIdentity(identity));
        row.addProperty("display_name", identity.ruleKey());
        row.addProperty("description", "Subrace hit point fixture.");
        row.addProperty("source_page", 4);
        return row;
    }
    private static InitialPlan dwarfPlan(ProductionSchema2Projection material, RuleIdentity subrace) {
        var sources = new Schema2InitialProficiencyRules.Sources(DWARF, subrace,
                new RuleIdentity(BASE, "character.background", "background.guard"), CLASS);
        var prepared = new Schema2InitialCharacterPlanService().prepareMaterial(Schema2PreparationIdentity.random(),
                Schema2RulesBinding.from(material), material, new Schema2InitialProficiencyRules.Request(sources, Map.of()));
        return new InitialPlan(UUID.randomUUID(), UUID.randomUUID(), prepared);
    }
    private static State withVitality(State state, long current, long maximum, HealthState health) {
        return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(),
                state.characterClass(), state.abilities(), state.level(), state.proficiencyBonus(), current, maximum, state.classes(),
                state.hitDicePools(), state.subclasses(), state.features(), state.feats(), state.proficiencies(), state.version(),
                state.resources(), state.rest(), state.equipment(), health, state.spellbook());
    }
    private State initial(Map<String, Integer> abilities) {
        return rules.initial(material, retainedPlan, UUID.randomUUID(), abilities, fullInitialChoices());
    }
    private Schema2CharacterProgressionRules.PreparedAdvance prepare(State state, RuleIdentity target, GrowthChoices choices) {
        var prepared = rules.prepareAdvance(material, retainedPlan, state, target, state.level() + 1, "FIXED_AVERAGE", choices);
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
