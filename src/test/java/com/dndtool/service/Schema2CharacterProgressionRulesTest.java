package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.ProgressionScope;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.ResourceState;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.module.composition.Schema2GrantFixtures.edit;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CharacterProgressionRulesTest {
    private final Schema2CharacterProgressionRules rules = new Schema2CharacterProgressionRules();
    private final ProductionSchema2Projection material = projection();

    @Test void initialStatisticsUseOnlyAnExactStandardArrayAndCompleteRetainedT1Plan() {
        State state = initial();
        assertEquals(1, state.level()); assertEquals(11, state.hitPoints()); assertEquals(11, state.maximumHitPoints());
        assertEquals(1, state.hitDice()); assertEquals(0, state.version());
        assertEquals(List.of(new ResourceState(ENERGY, 3, 3, false), new ResourceState(EXT_ENERGY, 1, 1, false)), state.resources());
        assertSame(state, rules.validateState(material, state));
        var inflated = new HashMap<>(abilities()); inflated.put("ability.strength", 16);
        assertCode("S2_GROWTH_STANDARD_ARRAY", () -> rules.initial(material, plan(), UUID.randomUUID(), inflated));
        var wrongKeys = new HashMap<>(abilities()); wrongKeys.put("strength", wrongKeys.remove("ability.strength"));
        assertCode("S2_GROWTH_ABILITIES", () -> rules.initial(material, plan(), UUID.randomUUID(), wrongKeys));
        var plan = plan(); var retained = plan.character();
        var altered = new Schema2InitialCharacterPlanService.Plan(retained.identity(), retained.binding(), retained.sources(),
                retained.choices(), retained.slots(), List.of(new Schema2InitialProficiencyRules.GrantFact(
                    new com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity(CLASS, "proficiency_grants", BASE, "invented"),
                    com.dndtool.module.composition.Schema2CompositionKernel.GrantCategory.TOOL,
                    com.dndtool.module.CharacterProficiencyGrant.GrantKind.FIXED,
                    new RuleIdentity(BASE, "character.tool", "tool.invented"), false)));
        var tampered = new Schema2InitialCharacterPlanService.InitialPlan(plan.campaignId(), plan.characterId(), altered);
        assertCode("S2_INITIAL_PLAN_MISMATCH", () -> rules.initial(material, tampered, UUID.randomUUID(), abilities()));
    }

    @Test void fixedStatisticsAdvancementPreservesSpentResourcesAndAddsOnlyNewCapacity() {
        State state = rules.spend(material, initial(), ENERGY, 2);
        var prepared = rules.prepareAdvance(material, state, CLASS, 2, "FIXED_AVERAGE");
        assertEquals(10, prepared.hitDieSides()); assertEquals(7, prepared.minimumHitPointIncrease());
        assertEquals(7, prepared.maximumHitPointIncrease());
        assertEquals(ProgressionScope.STATISTICS_AND_RESOURCES_ONLY, prepared.scope());
        State second = rules.advance(material, prepared, null);
        assertEquals(18, second.maximumHitPoints()); assertEquals(18, second.hitPoints()); assertEquals(2, second.hitDice());
        assertEquals(new ResourceState(ENERGY, 1, 3, false), resource(second, ENERGY));
        assertEquals(new ResourceState(EXT_ENERGY, 2, 2, false), resource(second, EXT_ENERGY));
        State third = rules.advance(material, rules.prepareAdvance(material, second, CLASS, 3, "FIXED_AVERAGE"), null);
        assertEquals(new ResourceState(ENERGY, 2, 4, false), resource(third, ENERGY));
        assertEquals(new ResourceState(LATER, 1, 1, false), resource(third, LATER));
        assertEquals(3, third.level()); assertEquals(3, third.maximumHitDice());
        assertCode("S2_GROWTH_LEVEL_UNSUPPORTED", () -> rules.prepareAdvance(material, third, CLASS, 4, "FIXED_AVERAGE"));
    }

    @Test void preflightIsClosedAndRejectsMulticlassSkippedLevelsAndUnknownChoices() {
        State state = initial();
        assertTrue(Arrays.stream(Schema2CharacterProgressionRules.PreparedAdvance.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertCode("S2_GROWTH_MULTICLASS_UNSUPPORTED", () -> rules.prepareAdvance(material, state,
                new RuleIdentity(BASE, "character.class", "class.other"), 2, "SERVER_ROLL"));
        assertCode("S2_GROWTH_LEVEL", () -> rules.prepareAdvance(material, state, CLASS, 3, "SERVER_ROLL"));
        assertCode("S2_GROWTH_HP_CHOICE", () -> rules.prepareAdvance(material, state, CLASS, 2, "CLIENT_ROLL"));
        assertCode("S2_GROWTH_HP_CHOICE", () -> rules.prepareAdvance(material, state, CLASS, 2, null));
    }

    @Test void rolledStatisticsRequireOneBoundedServerResultAndFixedChoiceRejectsARoll() {
        var prepared = rules.prepareAdvance(material, initial(), CLASS, 2, "SERVER_ROLL");
        assertEquals(2, prepared.minimumHitPointIncrease()); assertEquals(11, prepared.maximumHitPointIncrease());
        for (Integer roll : Arrays.asList(null, 0, 11)) assertCode("S2_GROWTH_ROLL", () -> rules.advance(material, prepared, roll));
        assertEquals(13, rules.advance(material, prepared, 1).maximumHitPoints());
        assertEquals(22, rules.advance(material, prepared, 10).maximumHitPoints());
        var fixed = rules.prepareAdvance(material, initial(), CLASS, 2, "FIXED_AVERAGE");
        assertCode("S2_GROWTH_ROLL", () -> rules.advance(material, fixed, 5));
    }

    @Test void resourceIdentityConsumptionAndRecoveryNeverExecuteARestOrRepeatAGain() {
        State original = initial();
        State spent = rules.spend(material, rules.spend(material, original, ENERGY, 3), EXT_ENERGY, 1);
        assertCode("S2_RESOURCE_INSUFFICIENT", () -> rules.spend(material, spent, ENERGY, 1));
        assertCode("S2_RESOURCE_REQUEST", () -> rules.spend(material, spent, ENERGY, 0));
        assertCode("S2_RESOURCE_UNAVAILABLE", () -> rules.spend(material, spent, LATER, 1));
        State shortTrigger = rules.recoverResources(material, spent, "SHORT_REST");
        assertEquals(0, resource(shortTrigger, ENERGY).current()); assertEquals(1, resource(shortTrigger, EXT_ENERGY).current());
        State longTrigger = rules.recoverResources(material, shortTrigger, "LONG_REST");
        assertEquals(3, resource(longTrigger, ENERGY).current()); assertEquals(1, resource(longTrigger, EXT_ENERGY).current());
        assertEquals(original.level(), longTrigger.level()); assertEquals(original.hitPoints(), longTrigger.hitPoints());
        assertEquals(original.maximumHitPoints(), longTrigger.maximumHitPoints());
        assertEquals(original.hitDice(), longTrigger.hitDice()); assertEquals(original.maximumHitDice(), longTrigger.maximumHitDice());
        assertEquals(original.abilities(), longTrigger.abilities());
        assertCode("S2_RESOURCE_NOTHING_TO_RECOVER", () -> rules.recoverResources(material, longTrigger, "LONG_REST"));
        assertCode("S2_RESOURCE_TRIGGER", () -> rules.recoverResources(material, spent, "COMPLETE_LONG_REST"));
    }

    @Test void retainedStateRejectsMissingExtraDuplicateBlockedAndCorruptResourceFacts() {
        State state = initial();
        for (List<ResourceState> resources : List.of(List.<ResourceState>of(),
                List.of(resource(state, ENERGY), resource(state, ENERGY)),
                List.of(new ResourceState(ENERGY, 4, 4, false), resource(state, EXT_ENERGY)),
                List.of(new ResourceState(ENERGY, -1, 3, false), resource(state, EXT_ENERGY)),
                List.of(new ResourceState(ENERGY, 0, 0, true), resource(state, EXT_ENERGY)),
                List.of(resource(state, ENERGY), resource(state, EXT_ENERGY),
                        new ResourceState(new RuleIdentity(BASE, "character.resource", "resource.blocked"), 1, 1, false)))) {
            assertCode("S2_RESOURCE_STATE", () -> rules.validateState(material, replace(state, state.level(),
                    state.hitPoints(), state.maximumHitPoints(), state.hitDice(), state.maximumHitDice(), resources)));
        }
    }

    @Test void stateValidationDoesNotReinitializeDamagedHitPointsOrHitDice() {
        State initial = initial();
        State spentDice = replace(initial, 1, 4, 11, 0, 1, initial.resources());
        assertSame(spentDice, rules.validateState(material, spentDice));
        assertEquals(4, spentDice.hitPoints()); assertEquals(0, spentDice.hitDice());
        assertCode("S2_GROWTH_STATE", () -> rules.validateState(material, replace(initial, 1, 11, 11, 1, 2, initial.resources())));
        assertCode("S2_GROWTH_STATE", () -> rules.validateState(material, replace(initial, 1, 12, 12, 1, 1, initial.resources())));
        assertCode("S2_GROWTH_STATE", () -> rules.validateState(material, replace(initial, 1, -1, 11, 1, 1, initial.resources())));
    }

    @Test void unlimitedResourcesHaveCanonicalZeroStateAndLaterFiniteCapacityStartsFull() {
        var files = packages();
        edit(files, 1, "changes.json", root -> root.getAsJsonArray().get(0).getAsJsonObject()
                .addProperty("value", "1-2:UNLIMITED,3-20:4"));
        var unlimited = ProductionSchema2Projection.compose(
                com.dndtool.module.composition.Schema2GrantFixtures.artifacts(files));
        var plan = initialPlan(unlimited, new Identity(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID());
        State state = rules.initial(unlimited, plan, UUID.randomUUID(), abilities());
        assertEquals(new ResourceState(ENERGY, 0, 0, true), resource(state, ENERGY));
        State spent = rules.spend(unlimited, state, ENERGY, 1_000_000);
        assertEquals(new ResourceState(ENERGY, 0, 0, true), resource(spent, ENERGY));
        State second = rules.advance(unlimited, rules.prepareAdvance(unlimited, spent, CLASS, 2, "FIXED_AVERAGE"), null);
        State third = rules.advance(unlimited, rules.prepareAdvance(unlimited, second, CLASS, 3, "FIXED_AVERAGE"), null);
        assertEquals(new ResourceState(ENERGY, 4, 4, false), resource(third, ENERGY));
    }

    @Test void exhaustedHistoryCapacityFailsBeforeAnyRolledStatisticsAdvancementCanBePrepared() {
        State state = initial();
        State full = new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(),
                state.characterClass(), state.abilities(), state.level(), state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(),
                state.classes(), state.hitDicePools(), state.subclasses(), state.features(), state.feats(), state.proficiencies(), 127, state.resources(), state.rest());
        assertSame(full, rules.validateState(material, full));
        assertCode("S2_GROWTH_EVENT_LIMIT", () -> rules.prepareAdvance(material, full, CLASS, 2, "SERVER_ROLL"));
    }

    private State initial() { return rules.initial(material, plan(), UUID.randomUUID(), abilities()); }
    private Schema2InitialCharacterPlanService.InitialPlan plan() {
        return initialPlan(material, new Identity(UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID());
    }
    private static ResourceState resource(State state, RuleIdentity identity) {
        return state.resources().stream().filter(value -> value.identity().equals(identity)).findFirst().orElseThrow();
    }
    private static State replace(State state, int level, long hp, long hpMax, int dice, int diceMax, List<ResourceState> resources) {
        return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(), state.characterClass(),
                state.abilities(), level, state.proficiencyBonus(), hp, hpMax, state.classes(),
                List.of(new com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HitDicePool(state.hitDicePools().getFirst().sides(), dice, diceMax)),
                state.subclasses(), state.features(), state.feats(), state.proficiencies(), state.version(), resources, state.rest());
    }
    private static void assertCode(String code, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(code, assertThrows(IllegalArgumentException.class, executable).getMessage());
    }
}
