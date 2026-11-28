package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.ClassLevel;
import com.dndtool.service.Schema2CharacterRuntime.ProficiencyState;
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

    public static boolean hasSneakAttack(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_COMBAT_FEATURE_UNSUPPORTED").sneakAttack();
    }

    public static boolean hasCunningAction(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_COMBAT_FEATURE_UNSUPPORTED").cunningAction();
    }

    public static RuleIdentity rogueExpertiseFeature(ProductionSchema2Projection material, RuleIdentity characterClass) {
        return rogueFeature(material, characterClass, ROGUE_EXPERTISE_ALGORITHM, "S2_ROGUE_EXPERTISE_CHOICES");
    }

    public static boolean isRogueExpertiseTarget(ProductionSchema2Projection material, RuleIdentity target) {
        return material.projection().rules().stream().anyMatch(row -> row.enabled() && row.identity().equals(target)
                && (row instanceof FinalProficiency skill && skill.definition().category() == ProficiencyCategory.SKILL
                    || row instanceof FinalTool tool && target.ruleKey().equals(ROGUE_THIEVES_TOOLS_KEY)
                        && tool.definition().category() == ToolCategory.KIT && tool.definition().sourcePage() == 70));
    }

    static void requireRogueExpertiseTargets(ProductionSchema2Projection material, List<ProficiencyState> proficiencies,
            List<RuleIdentity> targets, String code) {
        if (targets.size() != 2 || targets.get(0).equals(targets.get(1))
                || targets.stream().filter(target -> target.ruleType().equals("character.tool")).count() > 1
                || targets.stream().anyMatch(target -> !isRogueExpertiseTarget(material, target)
                    || proficiencies.stream().noneMatch(fact -> fact.target().equals(target) && fact.acquiredAtTotalLevel() == 1)))
            throw new IllegalArgumentException(code);
    }

    public static RuleIdentity fightingStyleOption(ProductionSchema2Projection material, RuleIdentity characterClass) {
        return fighterFeature(material, characterClass, FIGHTER_DEFENSE_ALGORITHM, "S2_GROWTH_FIGHTING_STYLE_CHOICE");
    }

    static boolean hasFighterDefense(ProductionSchema2Projection material, State state) {
        return validate(material, state, "S2_EQUIPMENT_EFFECT_UNSUPPORTED").defense();
    }

    private record SupportedFeatures(boolean extraAttack, boolean improvedCritical, RuleIdentity secondWindResource,
            boolean defense, RuleIdentity actionSurgeResource, boolean sneakAttack, boolean cunningAction) { }

    private static RuleIdentity fighterFeature(ProductionSchema2Projection material, RuleIdentity owner, String algorithm, String code) {
        return owner.ruleKey().equals("class.fighter") ? classFeature(material, owner, algorithm, code) : null;
    }

    private static RuleIdentity rogueFeature(ProductionSchema2Projection material, RuleIdentity owner, String algorithm, String code) {
        return owner.ruleKey().equals("class.rogue") ? classFeature(material, owner, algorithm, code) : null;
    }

    private static RuleIdentity classFeature(ProductionSchema2Projection material, RuleIdentity owner, String algorithm, String code) {
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

    private static void requireSingleClassRogue(State state, FeatureDefinition feature, String code) {
        if (!feature.owner().ruleKey().equals("class.rogue") || !feature.owner().equals(state.characterClass())
                || !state.classes().equals(List.of(new ClassLevel(feature.owner(), state.level())))
                || state.level() < feature.level() || state.level() > 2)
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
        RuleIdentity expertise = null, sneakAttack = null, cunningAction = null;
        for (var acquired : state.features()) {
            if (!seen.add(acquired.feature())) throw new IllegalArgumentException(code);
            var definitions = material.projection().rules().stream()
                    .filter(FinalFeature.class::isInstance).map(FinalFeature.class::cast)
                    .filter(row -> row.enabled() && row.identity().equals(acquired.feature())).toList();
            if (definitions.size() != 1) throw new IllegalArgumentException(code);
            var feature = definitions.getFirst().definition();
            if (feature.kind() != FeatureKind.FEATURE || !feature.owner().equals(acquired.owner())
                    || feature.level() != acquired.acquiredAtClassLevel()
                    || !ROGUE_EXPERTISE_ALGORITHM.equals(feature.executionAlgorithm()) && !acquired.expertise().isEmpty())
                throw new IllegalArgumentException(code);

            switch (feature.executionAlgorithm()) {
                case ROGUE_EXPERTISE_ALGORITHM -> {
                    requireSingleClassRogue(state, feature, code);
                    if (expertise != null || feature.executionMode() != FeatureExecution.CHOICE
                            || feature.level() != 1 || feature.sourcePage() != 39)
                        throw new IllegalArgumentException(code);
                    requireRogueExpertiseTargets(material, state.proficiencies(), acquired.expertise(), code);
                    expertise = feature.identity();
                }
                case ROGUE_SNEAK_ATTACK_ALGORITHM -> {
                    requireSingleClassRogue(state, feature, code);
                    if (sneakAttack != null || feature.executionMode() != FeatureExecution.AUTOMATIC
                            || feature.level() != 1 || feature.sourcePage() != 39)
                        throw new IllegalArgumentException(code);
                    sneakAttack = feature.identity();
                }
                case ROGUE_CUNNING_ACTION_ALGORITHM -> {
                    requireSingleClassRogue(state, feature, code);
                    if (cunningAction != null || feature.executionMode() != FeatureExecution.AUTOMATIC
                            || feature.level() != 2 || feature.sourcePage() != 40)
                        throw new IllegalArgumentException(code);
                    cunningAction = feature.identity();
                }
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
        RuleIdentity expectedExpertise = rogueFeature(material, state.characterClass(), ROGUE_EXPERTISE_ALGORITHM, code);
        RuleIdentity expectedSneakAttack = rogueFeature(material, state.characterClass(), ROGUE_SNEAK_ATTACK_ALGORITHM, code);
        RuleIdentity expectedCunningAction = state.level() >= 2
                ? rogueFeature(material, state.characterClass(), ROGUE_CUNNING_ACTION_ALGORITHM, code) : null;
        if (!Objects.equals(expertise, expectedExpertise) || !Objects.equals(sneakAttack, expectedSneakAttack)
                || !Objects.equals(cunningAction, expectedCunningAction))
            throw new IllegalArgumentException(code);
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
        return new SupportedFeatures(extraAttack, improvedCritical, secondWindResource, defense != null,
                actionSurgeResource, sneakAttack != null, cunningAction != null);
    }
}
