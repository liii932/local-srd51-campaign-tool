package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.storage.BundledCampaignRules;
import com.dndtool.service.storage.CampaignRuleContext;
import com.dndtool.service.storage.CampaignRuleSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Schema2ExtraAttackRulesTest {
    private final CampaignRuleContext rules = CampaignRuleSupport.validate(BundledCampaignRules.projection());
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final RuleIdentity fighter = rules.rule("character.class", "class.fighter");

    @Test void missingPrematureDuplicateAndMisattributedGrantsCannotEnableExtraAttack() {
        var plan = rules.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(),
                "fighter", "custom_artisan");
        State first = growth.initial(rules.projection(), plan, UUID.randomUUID(),
                rules.initialAbilities("fighter"), rules.initialChoices("fighter"));
        State second = advance(plan, first, GrowthChoices.EMPTY);
        State third = advance(plan, second, new GrowthChoices(rules.rule("character.subclass", "subclass.champion"),
                Map.of(), null, Map.of()));
        State fourth = advance(plan, third, new GrowthChoices(null, Map.of("ability.constitution", 2), null, Map.of()));
        State fifth = advance(plan, fourth, GrowthChoices.EMPTY);

        var extraAttack = rules.rule("character.feature", "feature.fighter_extra_attack");
        var grant = new FeatureState(extraAttack, fighter, 5);
        assertEquals(2, Schema2FeatureSupport.attacksPerAttackAction(rules.projection(), fifth));
        State missing = withFeatures(fifth, fourth.features());
        assertEquals(1, Schema2FeatureSupport.attacksPerAttackAction(rules.projection(), missing));
        assertEquals("S2_GROWTH_FEATURE_STATE", assertThrows(IllegalArgumentException.class,
                () -> growth.validateState(rules.projection(), missing)).getMessage());

        assertRejectedGrant(fourth, grant);
        assertRejectedGrant(fifth, grant);
        assertRejectedGrant(missing, new FeatureState(extraAttack, fighter, 4));
        assertRejectedGrant(missing, new FeatureState(extraAttack, rules.rule("character.class", "class.wizard"), 5));
        assertRejectedGrant(missing, new FeatureState(
                new RuleIdentity("other_module", extraAttack.ruleType(), extraAttack.ruleKey()), fighter, 5));
    }

    private void assertRejectedGrant(State state, FeatureState grant) {
        var features = new ArrayList<>(state.features());
        features.add(grant);
        State forged = withFeatures(state, features);
        assertEquals("S2_COMBAT_FEATURE_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> Schema2FeatureSupport.attacksPerAttackAction(rules.projection(), forged)).getMessage());
    }

    private State advance(InitialPlan initial, State state, GrowthChoices choices) {
        var step = growth.prepareAdvance(rules.projection(), initial, state, fighter, state.level() + 1, "FIXED_AVERAGE", choices);
        return growth.advance(rules.projection(), step, null);
    }

    private static State withFeatures(State state, List<FeatureState> features) {
        return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(),
                state.characterClass(), state.abilities(), state.level(), state.proficiencyBonus(), state.hitPoints(),
                state.maximumHitPoints(), state.classes(), state.hitDicePools(), state.subclasses(), features,
                state.feats(), state.proficiencies(), state.version(), state.resources(), state.rest(), state.equipment(),
                state.health(), state.spellbook());
    }
}
