package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.ClassLevel;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CharacterRuntime.SubclassState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    private record SupportedFeatures(boolean extraAttack, boolean improvedCritical) { }

    private static SupportedFeatures validate(ProductionSchema2Projection material, State state, String code) {
        if (!state.feats().isEmpty()) throw new IllegalArgumentException(code);
        Set<RuleIdentity> seen = new HashSet<>();
        List<FeatureDefinition> castingFeatures = new ArrayList<>();
        boolean extraAttack = false;
        boolean improvedCritical = false;
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
        if (!castingFeatures.isEmpty()) Schema2SpellRules.requireSupportedCastingFeatures(material, state, castingFeatures, code);
        return new SupportedFeatures(extraAttack, improvedCritical);
    }
}
