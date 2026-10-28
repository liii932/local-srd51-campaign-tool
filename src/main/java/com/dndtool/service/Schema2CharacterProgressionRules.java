package com.dndtool.service;

import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.module.ResourceRecoveryProfile;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.ResourceState;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/**
 * Internal statistics/resource execution over schema-2 facts. This capability never completes
 * class features, subclass/ASI/feat choices, multiclass advancement or an actual rest.
 */
public final class Schema2CharacterProgressionRules {
    private final Schema2GrowthRules growth = new Schema2GrowthRules();
    public static final String FIXED_AVERAGE = "FIXED_AVERAGE", SERVER_ROLL = "SERVER_ROLL";
    private static final Set<String> HP_CHOICES = Set.of(FIXED_AVERAGE, SERVER_ROLL);
    private static final Set<String> ABILITIES = Set.copyOf(JdbcSchema2CharacterRuntimeRepository.ABILITIES);
    private static final List<Integer> STANDARD_ARRAY = List.of(8, 10, 12, 13, 14, 15);
    private static final Comparator<RuleIdentity> IDENTITY_ORDER = Comparator.comparing(RuleIdentity::moduleKey)
            .thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    private static final Comparator<ResourceState> RESOURCE_ORDER = Comparator.comparing(ResourceState::identity, IDENTITY_ORDER);

    /** Internal standard-array statistics only; retained T1 sources and choices are independently checked. */
    public State initial(ProductionSchema2Projection material, Schema2InitialCharacterPlanService.InitialPlan plan,
            UUID preparationOperationId, Map<String, Integer> abilities) {
        return initial(material, plan, preparationOperationId, abilities, GrowthChoices.EMPTY);
    }
    public State initial(ProductionSchema2Projection material, Schema2InitialCharacterPlanService.InitialPlan plan,
            UUID preparationOperationId, Map<String, Integer> abilities, GrowthChoices choices) {
        if (material == null || plan == null || preparationOperationId == null) throw invalid("S2_GROWTH_REQUEST");
        abilities(abilities);
        if (!abilities.values().stream().sorted().toList().equals(STANDARD_ARRAY))
            throw invalid("S2_GROWTH_STANDARD_ARRAY");
        plan.character().binding().verify(material);
        var rebuilt = new Schema2InitialProficiencyRules().prepare(material,
                new Schema2InitialProficiencyRules.Request(plan.character().sources(), plan.character().choices()));
        if (!rebuilt.slots().equals(plan.character().slots()) || !rebuilt.grants().equals(plan.character().grants()))
            throw invalid("S2_INITIAL_PLAN_MISMATCH");
        RuleIdentity characterClass = plan.character().sources().characterClass();
        ClassProgressionDefinition progression = progression(material, characterClass);
        if (progression.advancementScope() == ProgressionScope.CHARACTER_ADVANCEMENT)
            return validateState(material, growth.initial(material, plan, preparationOperationId, abilities, choices));
        if (!GrowthChoices.EMPTY.equals(choices)) throw invalid("S2_GROWTH_CHOICES_UNSUPPORTED");
        long hitPoints = Math.max(1, progression.hitDieSides() + modifier(abilities, "ability.constitution"));
        State result = new State(plan.character().identity(), plan.campaignId(), plan.characterId(), preparationOperationId,
                characterClass, abilities, 1, 2, hitPoints, hitPoints, List.of(new ClassLevel(characterClass, 1)),
                List.of(new HitDicePool(progression.hitDieSides(), 1, 1)), List.of(), List.of(), List.of(), List.of(), 0,
                resourcesAt(material, characterClass, 1, modifier(abilities, "ability.charisma"), Map.of()), RestState.INITIAL);
        return validateState(material, result);
    }

    /** All catalog/state/target validation and resource calculations precede any server RNG call. */
    public PreparedAdvance prepareAdvance(ProductionSchema2Projection material, State state,
            RuleIdentity targetClass, int targetLevel, String hpChoiceAlgorithm) {
        return prepareAdvance(material, state, targetClass, targetLevel, hpChoiceAlgorithm, GrowthChoices.EMPTY);
    }
    public PreparedAdvance prepareAdvance(ProductionSchema2Projection material, State state,
            RuleIdentity targetClass, int targetLevel, String hpChoiceAlgorithm, GrowthChoices choices) {
        validateState(material, state);
        requireMutationCapacity(state);
        if (state.rest().active() != null) throw invalid("S2_GROWTH_REST_ACTIVE");
        if (hpChoiceAlgorithm == null || !HP_CHOICES.contains(hpChoiceAlgorithm)) throw invalid("S2_GROWTH_HP_CHOICE");
        if (progression(material, state.characterClass()).advancementScope() == ProgressionScope.CHARACTER_ADVANCEMENT) {
            var result = growth.prepare(material, state, targetClass, targetLevel, hpChoiceAlgorithm, choices);
            return new PreparedAdvance(Schema2RulesBinding.from(material), state, hpChoiceAlgorithm, targetLevel,
                    result.hitDieSides(), result.constitutionModifier(), result.minimum(), result.maximum(),
                    result.capacityState().resources(), ProgressionScope.CHARACTER_ADVANCEMENT, result.capacityState());
        }
        if (!GrowthChoices.EMPTY.equals(choices)) throw invalid("S2_GROWTH_CHOICES_UNSUPPORTED");
        if (!state.characterClass().equals(targetClass)) throw invalid("S2_GROWTH_MULTICLASS_UNSUPPORTED");
        if (state.level() >= 20 || targetLevel != state.level() + 1) throw invalid("S2_GROWTH_LEVEL");
        ClassProgressionDefinition progression = progression(material, state.characterClass());
        if (targetLevel > progression.supportedMaximumLevel()) throw invalid("S2_GROWTH_LEVEL_UNSUPPORTED");
        int constitution = modifier(state.abilities(), "ability.constitution");
        int minimum = Math.max(1, (FIXED_AVERAGE.equals(hpChoiceAlgorithm)
                ? progression.hitDieSides() / 2 + 1 : 1) + constitution);
        int maximum = FIXED_AVERAGE.equals(hpChoiceAlgorithm) ? minimum
                : Math.max(1, progression.hitDieSides() + constitution);
        if (state.maximumHitPoints() + maximum > 65_535) throw invalid("S2_GROWTH_HIT_POINT_LIMIT");
        List<ResourceState> nextResources = resourcesAt(material, state.characterClass(), targetLevel,
                modifier(state.abilities(), "ability.charisma"), indexed(state.resources()));
        return new PreparedAdvance(Schema2RulesBinding.from(material), state, hpChoiceAlgorithm, targetLevel,
                progression.hitDieSides(), constitution, minimum, maximum, nextResources, progression.advancementScope(),
                copy(state, targetLevel, state.hitPoints() + minimum, state.maximumHitPoints() + minimum,
                        state.hitDice() + 1, state.maximumHitDice() + 1, nextResources));
    }

    public State advance(ProductionSchema2Projection material, PreparedAdvance prepared, Integer serverRoll) {
        if (material == null || prepared == null) throw invalid("S2_GROWTH_REQUEST");
        prepared.binding.verify(material);
        int increase;
        if (FIXED_AVERAGE.equals(prepared.hpChoiceAlgorithm)) {
            if (serverRoll != null) throw invalid("S2_GROWTH_ROLL");
            increase = prepared.minimumHitPointIncrease;
        } else {
            if (serverRoll == null || serverRoll < 1 || serverRoll > prepared.hitDieSides)
                throw invalid("S2_GROWTH_ROLL");
            increase = Math.max(1, serverRoll + prepared.constitutionModifier)
                    + (prepared.minimumHitPointIncrease - Math.max(1, 1 + prepared.constitutionModifier));
        }
        State capacity = prepared.capacityState;
        long difference = increase - prepared.minimumHitPointIncrease;
        return validateState(material, new State(capacity.identity(), capacity.campaignId(), capacity.characterId(), capacity.preparationOperationId(),
                capacity.characterClass(), capacity.abilities(), capacity.level(), capacity.proficiencyBonus(),
                capacity.hitPoints() + difference, capacity.maximumHitPoints() + difference, capacity.classes(), capacity.hitDicePools(),
                capacity.subclasses(), capacity.features(), capacity.feats(), capacity.proficiencies(), capacity.version(), capacity.resources(), capacity.rest(), capacity.equipment(), capacity.health(), capacity.spellbook()));
    }

    public State spend(ProductionSchema2Projection material, State state, RuleIdentity identity, long amount) {
        validateState(material, state);
        requireMutationCapacity(state);
        if (state.rest().active() != null) throw invalid("S2_GROWTH_REST_ACTIVE");
        if (identity == null || amount < 1 || amount > 1_000_000) throw invalid("S2_RESOURCE_REQUEST");
        var resources = new ArrayList<>(state.resources());
        int index = -1;
        for (int i = 0; i < resources.size(); i++) if (resources.get(i).identity().equals(identity)) index = i;
        if (index < 0) throw invalid("S2_RESOURCE_UNAVAILABLE");
        ResourceState previous = resources.get(index);
        if (!previous.unlimited() && previous.current() < amount) throw invalid("S2_RESOURCE_INSUFFICIENT");
        resources.set(index, new ResourceState(identity, previous.unlimited() ? 0 : previous.current() - amount,
                previous.maximum(), previous.unlimited()));
        return validateState(material, copy(state, state.level(), state.hitPoints(), state.maximumHitPoints(),
                state.hitDice(), state.maximumHitDice(), resources));
    }

    /** Resource-profile trigger only. No elapsed time, HP, hit dice or effect lifecycle is changed. */
    public State recoverResources(ProductionSchema2Projection material, State state, String trigger) {
        validateState(material, state);
        requireMutationCapacity(state);
        if (progression(material, state.characterClass()).advancementScope() == ProgressionScope.CHARACTER_ADVANCEMENT)
            throw invalid("S2_RESOURCE_REST_REQUIRED");
        if (state.rest().active() != null) throw invalid("S2_GROWTH_REST_ACTIVE");
        ResourceRecoveryProfile.Rest completed;
        try { completed = ResourceRecoveryProfile.Rest.valueOf(trigger); }
        catch (IllegalArgumentException | NullPointerException invalid) { throw invalid("S2_RESOURCE_TRIGGER"); }
        Map<RuleIdentity, ResourceDefinition> definitions = new HashMap<>();
        for (FinalResource resource : resourceDefinitions(material, state.characterClass()))
            definitions.put(resource.identity(), resource.definition());
        List<ResourceState> next = new ArrayList<>();
        boolean changed = false;
        for (ResourceState resource : state.resources()) {
            boolean recovers = !resource.unlimited() && ResourceRecoveryProfile.parse(
                    definitions.get(resource.identity()).recoveryProfile()).atLevel(state.level()).recoversOn(completed);
            long current = recovers ? resource.maximum() : resource.current();
            changed |= current != resource.current();
            next.add(new ResourceState(resource.identity(), current, resource.maximum(), resource.unlimited()));
        }
        if (!changed) throw invalid("S2_RESOURCE_NOTHING_TO_RECOVER");
        return validateState(material, copy(state, state.level(), state.hitPoints(), state.maximumHitPoints(),
                state.hitDice(), state.maximumHitDice(), next));
    }

    /** Validates already-derived facts. It performs no level gain, grant, consumption or recovery. */
    public State validateState(ProductionSchema2Projection material, State state) {
        if (material == null || state == null || state.identity() == null) throw invalid("S2_GROWTH_STATE");
        Set<UUID> identities = new HashSet<>();
        for (UUID value : Arrays.asList(state.identity().runId(), state.identity().snapshotId(), state.campaignId(),
                state.characterId(), state.preparationOperationId())) {
            if (value == null || value.version() != 4 || value.variant() != 2 || !identities.add(value))
                throw invalid("S2_GROWTH_STATE");
        }
        abilities(state.abilities());
        ClassProgressionDefinition progression = progression(material, state.characterClass());
        if (progression.advancementScope() == ProgressionScope.CHARACTER_ADVANCEMENT) return growth.validate(material, state);
        if (!state.classes().equals(List.of(new ClassLevel(state.characterClass(), state.level())))
                || !state.subclasses().isEmpty() || !state.features().isEmpty() || !state.feats().isEmpty() || !state.proficiencies().isEmpty()
                || state.proficiencyBonus() != 2 + (state.level() - 1) / 4
                || state.hitDicePools().size() != 1 || state.hitDicePools().getFirst().sides() != progression.hitDieSides())
            throw invalid("S2_GROWTH_STATE");
        if (state.level() < 1 || state.level() > progression.supportedMaximumLevel()
                || state.maximumHitDice() != state.level() || state.hitDice() < 0 || state.hitDice() > state.maximumHitDice()
                || state.version() < 0 || state.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS
                || state.maximumHitPoints() < 1 || state.maximumHitPoints() > 65_535
                || state.hitPoints() < 0 || state.hitPoints() > state.maximumHitPoints()) throw invalid("S2_GROWTH_STATE");
        int constitution = modifier(state.abilities(), "ability.constitution");
        long initial = Math.max(1, progression.hitDieSides() + constitution);
        long minimum = initial + (state.level() - 1L) * Math.max(1, 1 + constitution);
        long maximum = initial + (state.level() - 1L) * Math.max(1, progression.hitDieSides() + constitution);
        if (state.maximumHitPoints() < minimum || state.maximumHitPoints() > maximum) throw invalid("S2_GROWTH_STATE");
        Map<RuleIdentity, ResourceState> actual = indexed(state.resources());
        List<ResourceState> expected = resourcesAt(material, state.characterClass(), state.level(),
                modifier(state.abilities(), "ability.charisma"), Map.of());
        if (actual.size() != expected.size()) throw invalid("S2_RESOURCE_STATE");
        for (ResourceState resource : expected) {
            ResourceState current = actual.get(resource.identity());
            if (current == null || current.unlimited() != resource.unlimited() || current.maximum() != resource.maximum()
                    || current.current() < 0 || current.current() > current.maximum()
                    || current.unlimited() && current.current() != 0) throw invalid("S2_RESOURCE_STATE");
        }
        if (!state.resources().equals(state.resources().stream().sorted(RESOURCE_ORDER).toList()))
            throw invalid("S2_RESOURCE_STATE");
        return state;
    }

    private static ClassProgressionDefinition progression(ProductionSchema2Projection material, RuleIdentity owner) {
        if (owner == null || !"character.class".equals(owner.ruleType()) || material.projection().rules().stream()
                .noneMatch(row -> row.identity().equals(owner) && row instanceof FinalCharacterSource && row.enabled()))
            throw invalid("S2_GROWTH_CLASS");
        List<FinalClassProgression> matches = material.projection().rules().stream()
                .filter(FinalClassProgression.class::isInstance).map(FinalClassProgression.class::cast)
                .filter(row -> row.enabled() && row.definition().ownerClass().equals(owner)).toList();
        if (matches.size() != 1) throw invalid("S2_GROWTH_UNSUPPORTED");
        ClassProgressionDefinition result = matches.getFirst().definition();
        AdvancementValueProfile.parseProficiencyBonus(result.proficiencyBonusProfile());
        return result;
    }

    private static List<FinalResource> resourceDefinitions(ProductionSchema2Projection material, RuleIdentity owner) {
        List<FinalResource> result = material.projection().rules().stream().filter(FinalResource.class::isInstance)
                .map(FinalResource.class::cast).filter(row -> row.definition().ownerClass().equals(owner)).toList();
        for (FinalResource row : result) {
            AdvancementValueProfile.parse(row.definition().maximumProfile());
            ResourceRecoveryProfile.parse(row.definition().recoveryProfile());
        }
        return result;
    }

    private static List<ResourceState> resourcesAt(ProductionSchema2Projection material, RuleIdentity owner,
            int level, int charisma, Map<RuleIdentity, ResourceState> previous) {
        List<ResourceState> result = new ArrayList<>();
        for (FinalResource resource : resourceDefinitions(material, owner)) {
            ResourceDefinition definition = resource.definition();
            if (!resource.enabled() || definition.executionMode() == ResourceExecution.BLOCKED) continue;
            var maximum = AdvancementValueProfile.parse(definition.maximumProfile()).atLevel(level, charisma);
            if (!maximum.unlimited() && maximum.maximum() == 0) continue;
            ResourceState before = previous.get(resource.identity());
            long spent = before == null || before.unlimited() ? 0 : before.maximum() - before.current();
            result.add(new ResourceState(resource.identity(), maximum.unlimited() ? 0
                    : Math.max(0, maximum.maximum() - spent), maximum.maximum(), maximum.unlimited()));
        }
        if (result.size() > JdbcSchema2CharacterRuntimeRepository.MAX_RESOURCES) throw invalid("S2_RESOURCE_BUDGET");
        return result.stream().sorted(RESOURCE_ORDER).toList();
    }

    private static Map<RuleIdentity, ResourceState> indexed(List<ResourceState> resources) {
        if (resources == null || resources.size() > JdbcSchema2CharacterRuntimeRepository.MAX_RESOURCES)
            throw invalid("S2_RESOURCE_STATE");
        Map<RuleIdentity, ResourceState> result = new HashMap<>();
        for (ResourceState resource : resources) {
            if (resource == null || resource.identity() == null || result.put(resource.identity(), resource) != null)
                throw invalid("S2_RESOURCE_STATE");
        }
        return result;
    }

    private static State copy(State state, int level, long hp, long maximumHp, int hitDice,
            int maximumHitDice, List<ResourceState> resources) {
        if (state.classes().size() != 1) return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(),
                state.characterClass(), state.abilities(), level, state.proficiencyBonus(), hp, maximumHp, state.classes(), state.hitDicePools(),
                state.subclasses(), state.features(), state.feats(), state.proficiencies(), state.version() + 1, resources, state.rest(), state.equipment(), state.health(), state.spellbook());
        List<HitDicePool> pools = level == state.level() ? state.hitDicePools()
                : List.of(new HitDicePool(state.hitDicePools().getFirst().sides(), hitDice, maximumHitDice));
        return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(),
                state.characterClass(), state.abilities(), level, 2 + (level - 1) / 4, hp, maximumHp,
                List.of(new ClassLevel(state.characterClass(), level)), pools, state.subclasses(), state.features(), state.feats(), state.proficiencies(),
                state.version() + 1, resources, state.rest(), state.equipment(), state.health(), state.spellbook());
    }

    private static void abilities(Map<String, Integer> values) {
        if (values == null || !values.keySet().equals(ABILITIES)
                || values.values().stream().anyMatch(value -> value == null || value < 1 || value > 30))
            throw invalid("S2_GROWTH_ABILITIES");
    }
    private static void requireMutationCapacity(State state) {
        if (state.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS - 1)
            throw invalid("S2_GROWTH_EVENT_LIMIT");
    }
    private static int modifier(Map<String, Integer> values, String key) { return Math.floorDiv(values.get(key) - 10, 2); }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }

    /** Only this rules object can construct a successfully preflighted statistics advancement. */
    public static final class PreparedAdvance {
        private final Schema2RulesBinding binding;
        private final State state;
        private final String hpChoiceAlgorithm;
        private final int targetLevel, hitDieSides, constitutionModifier, minimumHitPointIncrease, maximumHitPointIncrease;
        private final List<ResourceState> resources;
        private final ProgressionScope scope;
        private final State capacityState;
        private PreparedAdvance(Schema2RulesBinding binding, State state, String hpChoiceAlgorithm, int targetLevel,
                int hitDieSides, int constitutionModifier, int minimum, int maximum, List<ResourceState> resources,
                ProgressionScope scope, State capacityState) {
            this.binding = binding; this.state = state; this.hpChoiceAlgorithm = hpChoiceAlgorithm;
            this.targetLevel = targetLevel; this.hitDieSides = hitDieSides; this.constitutionModifier = constitutionModifier;
            this.minimumHitPointIncrease = minimum; this.maximumHitPointIncrease = maximum;
            this.resources = List.copyOf(resources); this.scope = scope;
            this.capacityState = capacityState;
        }
        public int hitDieSides() { return hitDieSides; }
        public int minimumHitPointIncrease() { return minimumHitPointIncrease; }
        public int maximumHitPointIncrease() { return maximumHitPointIncrease; }
        public ProgressionScope scope() { return scope; }
        /** Deterministic minimum-HP shape for row budgeting only; never a persisted RNG result. */
        public State capacityState() { return capacityState; }
    }
}
