package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** A finite Wizard studies their acquired spellbook at the actual completion of a short rest. */
public final class Schema2ArcaneRecoveryRules {
    public static final String ALGORITHM = "SRD_2014_WIZARD_ARCANE_RECOVERY_V1";

    /** Empty selection declines the benefit. This owner never completes a rest or advances time. */
    public State afterShortRest(ProductionSchema2Projection material, State before, State rested, Command command) {
        var selected = command.restInput().recoveredSpellSlots();
        if (selected.isEmpty()) return rested;
        RestAttempt active = before.rest().active();
        if (!command.kind().equals("REST_COMPLETED") || active == null || !active.kind().equals("SHORT_REST")
                || !active.phase().equals("IN_PROGRESS") || active.interrupted()
                || before.rest().minute() - active.startMinute() < 60
                || rested.rest().active() == null || !rested.rest().active().phase().equals("SPENDING_HIT_DICE"))
            throw invalid("S2_ARCANE_RECOVERY_REST_REQUIRED");
        if (before.hitPoints() < 1 || before.health().dead()) throw invalid("S2_ARCANE_RECOVERY_INACTIVE");
        var profile = profile(material, before);
        new Schema2WizardSpellbookRules().validateState(material, before);
        if (before.spellbook().learnedThroughLevel() != before.level()) throw invalid("S2_SPELLBOOK_LEARNING_REQUIRED");
        Schema2SpellRules.requireSupportedFeatures(material, before, "S2_ARCANE_RECOVERY_UNSUPPORTED");
        long day = day(before);
        if (Objects.equals(before.spellbook().lastArcaneRecoveryDay(), day)) throw invalid("S2_ARCANE_RECOVERY_DAILY_LIMIT");
        int combined = selected.entrySet().stream().mapToInt(e -> e.getKey() * e.getValue()).sum();
        if (combined > (before.level() + 1) / 2) throw invalid("S2_ARCANE_RECOVERY_CHOICE");
        Map<Integer, RuleIdentity> slots = Map.of(1, profile.slotResource(), 2, profile.rank2SlotResource(), 3, profile.rank3SlotResource());
        Map<RuleIdentity, Integer> additions = new HashMap<>();
        for (var choice : selected.entrySet()) {
            int rank = choice.getKey();
            RuleIdentity resource = slots.get(rank);
            if (resource == null || rank > (before.level() + 1) / 2) throw invalid("S2_ARCANE_RECOVERY_CHOICE");
            var matches = before.resources().stream().filter(r -> r.identity().equals(resource)).toList();
            if (matches.size() != 1 || matches.getFirst().unlimited()
                    || choice.getValue() > matches.getFirst().maximum() - matches.getFirst().current())
                throw invalid("S2_ARCANE_RECOVERY_CHOICE");
            var frozen = material.projection().rules().stream().filter(FinalResource.class::isInstance)
                    .map(FinalResource.class::cast).filter(r -> r.enabled() && r.identity().equals(resource)).toList();
            if (frozen.size() != 1 || !frozen.getFirst().definition().ownerClass().equals(profile.ownerClass())
                    || frozen.getFirst().definition().executionMode() != ResourceExecution.AUTOMATIC
                    || !frozen.getFirst().definition().recoveryProfile().equals("1-20:LONG_REST"))
                throw invalid("S2_ARCANE_RECOVERY_UNSUPPORTED");
            additions.put(resource, choice.getValue());
        }
        List<ResourceState> resources = rested.resources().stream().map(r -> new ResourceState(r.identity(),
                r.current() + additions.getOrDefault(r.identity(), 0), r.maximum(), r.unlimited())).toList();
        SpellbookState book = JdbcSchema2CharacterRuntimeRepository.withArcaneRecoveryDay(before.spellbook(), day);
        return new State(rested.identity(), rested.campaignId(), rested.characterId(), rested.preparationOperationId(), rested.characterClass(),
                rested.abilities(), rested.level(), rested.proficiencyBonus(), rested.hitPoints(), rested.maximumHitPoints(), rested.classes(),
                rested.hitDicePools(), rested.subclasses(), rested.features(), rested.feats(), rested.proficiencies(), rested.version(), resources,
                rested.rest(), rested.equipment(), rested.health(), book);
    }

    public void validateState(ProductionSchema2Projection material, State state) {
        if (state.spellbook() == null || state.spellbook().lastArcaneRecoveryDay() == null) return;
        profile(material, state);
        if (state.spellbook().lastArcaneRecoveryDay() > day(state)) throw invalid("S2_ARCANE_RECOVERY_STATE");
    }

    private static long day(State state) { return (state.rest().minute() * 60 + state.rest().second()) / 86_400; }

    private static WizardSpellcastingDefinition profile(ProductionSchema2Projection material, State state) {
        if (state.spellbook() == null || state.level() < 1 || state.level() > 5 || state.classes().size() != 1
                || state.classes().getFirst().level() != state.level()) throw invalid("S2_ARCANE_RECOVERY_UNSUPPORTED");
        var profiles = material.projection().rules().stream().filter(FinalWizardSpellcasting.class::isInstance)
                .map(FinalWizardSpellcasting.class::cast).filter(p -> p.enabled() && p.identity().equals(state.spellbook().castingRule())).toList();
        if (profiles.size() != 1) throw invalid("S2_ARCANE_RECOVERY_UNSUPPORTED");
        var profile = profiles.getFirst().definition();
        if (!profile.algorithm().equals("SRD_2014_WIZARD_PREPARED_V1") || !profile.ownerClass().equals(state.characterClass())
                || !profile.ownerClass().equals(state.classes().getFirst().characterClass())) throw invalid("S2_ARCANE_RECOVERY_UNSUPPORTED");
        var features = material.projection().rules().stream().filter(FinalFeature.class::isInstance).map(FinalFeature.class::cast)
                .filter(f -> f.enabled() && f.definition().executionAlgorithm().equals(ALGORITHM)
                        && f.definition().owner().equals(profile.ownerClass())).toList();
        if (features.size() != 1) throw invalid("S2_ARCANE_RECOVERY_UNSUPPORTED");
        var feature = features.getFirst().definition();
        if (feature.executionMode() != FeatureExecution.AUTOMATIC || feature.kind() != FeatureKind.FEATURE || feature.level() != 1
                || state.features().stream().filter(f -> f.feature().equals(feature.identity())
                    && f.owner().equals(profile.ownerClass()) && f.acquiredAtClassLevel() == 1).count() != 1)
            throw invalid("S2_ARCANE_RECOVERY_UNSUPPORTED");
        return profile;
    }

    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
