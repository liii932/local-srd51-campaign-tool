package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.ClassLevel;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CharacterRuntime.SubclassState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Validates acquired feature facts before bounded domain consumers use them. */
public final class Schema2FeatureSupport {
    private Schema2FeatureSupport() { }

    public static void requireSupportedFeatures(ProductionSchema2Projection material, State state, String code) {
        validate(material, state, code);
    }

    public static int attacksPerAttackAction(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_COMBAT_FEATURE_UNSUPPORTED").extraAttack() ? 2 : 1;
    }

    public static int weaponCriticalMinimum(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_COMBAT_FEATURE_UNSUPPORTED").improvedCritical() ? 19 : 20;
    }

    public static RuleIdentity secondWindResource(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_COMBAT_FEATURE_UNSUPPORTED").secondWindResource();
    }

    public static RuleIdentity actionSurgeResource(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_COMBAT_FEATURE_UNSUPPORTED").actionSurgeResource();
    }

    public static boolean isFighterActionSurgeResource(ProductionSchema2Projection material, RuleIdentity resource) {
        if (resource == null) return false;
        RuleIdentity fighter = new RuleIdentity(resource.moduleKey(), "character.class", "class.fighter");
        return resource.equals(fighterActionSurgeResource(fighter))
                && material.projection().rules().stream().anyMatch(row -> row.identity().equals(fighter));
    }

    public static RuleIdentity fightingStyleOption(ProductionSchema2Projection material, RuleIdentity characterClass) {
        return fighterFeature(material, characterClass, FIGHTER_DEFENSE_ALGORITHM, "S2_GROWTH_FIGHTING_STYLE_CHOICE");
    }

    static boolean hasFighterDefense(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_EQUIPMENT_EFFECT_UNSUPPORTED").defense();
    }

    private record SupportedFeatures(boolean extraAttack, boolean improvedCritical, RuleIdentity secondWindResource,
            boolean defense, RuleIdentity actionSurgeResource) { }

    private static RuleIdentity fighterFeature(ProductionSchema2Projection material, RuleIdentity owner, String algorithm, String code) {
        if (!owner.ruleKey().equals("class.fighter")) return null;
        var options = material.projection().rules().stream().filter(FinalFeature.class::isInstance)
                .map(FinalFeature.class::cast).filter(row -> row.enabled() && row.definition().owner().equals(owner)
                        && algorithm.equals(row.definition().executionAlgorithm())).map(FinalFeature::identity).toList();
        if (options.size() > 1) throw new IllegalArgumentException(code);
        return options.isEmpty() ? null : options.getFirst();
    }

    private static void requireSingleClassFighter(State state, FeatureDefinition feature, String code) {
        if (!feature.owner().ruleKey().equals("class.fighter") || !feature.owner().equals(state.characterClass())
                || !state.classes().equals(List.of(new ClassLevel(feature.owner(), state.level())))
                || state.level() < feature.level() || state.level() > 6)
            throw new IllegalArgumentException(code);
    }

    private static SupportedFeatures validate(ProductionSchema2Projection material, State state, String code) {
        if (!state.feats().isEmpty()) throw new IllegalArgumentException(code);
        Set<RuleIdentity> seen = new HashSet<>();
        List<FeatureDefinition> castingFeatures = new ArrayList<>();
        boolean extraAttack = false;
        boolean improvedCritical = false;
        RuleIdentity secondWindResource = null;
        RuleIdentity defense = null;
        RuleIdentity actionSurgeResource = null;
        for (var acquired : state.features()) {
            if (!seen.add(acquired.feature())) throw new IllegalArgumentException(code);
            var definitions = material.projection().rules().stream()
                    .filter(FinalFeature.class::isInstance).map(FinalFeature.class::cast)
                    .filter(row -> row.enabled() && row.identity().equals(acquired.feature())).toList();
            if (definitions.size() != 1) throw new IllegalArgumentException(code);
            var feature = definitions.getFirst().definition();
            if (feature.kind() != FeatureKind.FEATURE || !feature.owner().equals(acquired.owner())
                    || feature.level() != acquired.acquiredAtClassLevel()) throw new IllegalArgumentException(code);

            switch (feature.executionAlgorithm()) {
                case FIGHTER_DEFENSE_ALGORITHM -> {
                    requireSingleClassFighter(state, feature, code);
                    if (defense != null || feature.executionMode() != FeatureExecution.CHOICE
                            || feature.level() != 1 || feature.sourcePage() != 24)
                        throw new IllegalArgumentException(code);
                    defense = feature.identity();
                }
                case FIGHTER_ACTION_SURGE_ALGORITHM -> {
                    requireSingleClassFighter(state, feature, code);
                    if (actionSurgeResource != null || feature.executionMode() != FeatureExecution.AUTOMATIC
                            || feature.level() != 2 || feature.sourcePage() != 25)
                        throw new IllegalArgumentException(code);
                    actionSurgeResource = fighterActionSurgeResource(feature.owner());
                }
                case FIGHTER_SECOND_WIND_ALGORITHM -> {
                    if (secondWindResource != null || !feature.owner().equals(state.characterClass())
                            || !state.classes().equals(List.of(new ClassLevel(feature.owner(), state.level())))
                            || state.level() < feature.level() || state.level() > 6)
                        throw new IllegalArgumentException(code);
                    secondWindResource = fighterSecondWindResource(feature.owner());
                }
                case FIGHTER_EXTRA_ATTACK_ALGORITHM -> {
                    if (extraAttack || !feature.owner().equals(state.characterClass()) || state.classes().size() != 1
                            || !feature.owner().equals(state.classes().getFirst().characterClass())
                            || state.classes().getFirst().level() != state.level()
                            || state.level() < feature.level() || state.level() > 6)
                        throw new IllegalArgumentException(code);
                    extraAttack = true;
                }
                case CHAMPION_IMPROVED_CRITICAL_ALGORITHM -> {
                    var subclass = material.projection().rules().stream()
                            .filter(FinalSubclass.class::isInstance).map(FinalSubclass.class::cast)
                            .filter(row -> row.enabled() && row.identity().equals(feature.owner()))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException(code)).definition();
                    if (improvedCritical || !subclass.ownerClass().equals(state.characterClass())
                            || !state.classes().equals(List.of(new ClassLevel(subclass.ownerClass(), state.level())))
                            || !state.subclasses().equals(List.of(new SubclassState(subclass.ownerClass(), subclass.identity(), feature.level())))
                            || state.level() < feature.level() || state.level() > 6)
                        throw new IllegalArgumentException(code);
                    improvedCritical = true;
                }
                case Schema2SpellRules.CASTING_ALGORITHM, "SRD_2014_WIZARD_SPELLCASTING_V1",
                        Schema2ArcaneRecoveryRules.ALGORITHM, "SRD_2014_EVOCATION_SAVANT_V1", "SRD_2014_SCULPT_SPELLS_V1" ->
                    castingFeatures.add(feature);
                default -> throw new IllegalArgumentException(code);
            }
        }
        if (!Objects.equals(defense, fighterFeature(material, state.characterClass(), FIGHTER_DEFENSE_ALGORITHM, code)))
            throw new IllegalArgumentException(code);
        boolean surgeExpected = state.level() >= 2
                && fighterFeature(material, state.characterClass(), FIGHTER_ACTION_SURGE_ALGORITHM, code) != null;
        if (surgeExpected != (actionSurgeResource != null)) throw new IllegalArgumentException(code);
        if (actionSurgeResource != null) {
            RuleIdentity resource = actionSurgeResource;
            var uses = state.resources().stream().filter(row -> row.identity().equals(resource)).toList();
            if (uses.size() != 1 || uses.getFirst().unlimited() || uses.getFirst().maximum() != 1
                    || uses.getFirst().current() < 0 || uses.getFirst().current() > 1)
                throw new IllegalArgumentException(code);
        }
        if (!castingFeatures.isEmpty()) Schema2SpellRules.requireSupportedCastingFeatures(material, state, castingFeatures, code);
        return new SupportedFeatures(extraAttack, improvedCritical, secondWindResource, defense != null, actionSurgeResource);
    }
}
