package com.dndtool.service;

import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Complete bounded growth facts; executing acquired combat/spell features remains a separate capability. */
final class Schema2GrowthRules {
    private static final Comparator<RuleIdentity> ORDER = Schema2InitialProficiencyRules.RULE_ORDER;
    private static final Comparator<ProficiencyState> GRANT_ORDER = Comparator.comparing(ProficiencyState::sourceSlot,
            Schema2InitialProficiencyRules.SLOT_ORDER).thenComparing(ProficiencyState::target, ORDER);
    private static final Set<String> ABILITIES = Set.of("ability.strength", "ability.dexterity", "ability.constitution",
            "ability.intelligence", "ability.wisdom", "ability.charisma");

    record Prepared(State capacityState, int hitDieSides, int constitutionModifier, int minimum, int maximum) { }

    State initial(ProductionSchema2Projection material, Schema2InitialCharacterPlanService.InitialPlan initial,
            UUID preparation, Map<String, Integer> abilities, GrowthChoices choices) {
        Catalog catalog = new Catalog(material);
        RuleIdentity starting = initial.character().sources().characterClass();
        ClassProgressionDefinition progression = catalog.growth(starting);
        if (catalog.level(progression.identity(), 1).asiAvailable()) fail("S2_GROWTH_INITIAL_ASI_UNSUPPORTED");
        if (choices == null || choices.feat() != null || !choices.abilityIncreases().isEmpty()) fail("S2_GROWTH_INITIAL_CHOICES");
        List<ClassLevel> classes = List.of(new ClassLevel(starting, 1));
        List<SubclassState> subclasses = selectSubclass(catalog, starting, 1, List.of(), choices.subclass());
        List<ProficiencyState> grants = new ArrayList<>();
        for (var grant : initial.character().grants()) if (!grant.suppressed() && grant.category() == GrantCategory.TOOL)
            grants.add(new ProficiencyState(grant.slot(), grant.target(), 1));
        grants = proficiencyGrants(catalog, progression.identity(), "starting_proficiencies", 1, grants, choices.proficiencyChoices());
        long hp = Math.max(1, progression.hitDieSides() + modifier(abilities, "ability.constitution"));
        State state = new State(initial.character().identity(), initial.campaignId(), initial.characterId(), preparation,
                starting, abilities, 1, 2, hp, hp, classes, List.of(new HitDicePool(progression.hitDieSides(), 1, 1)),
                subclasses, features(catalog, classes, subclasses), List.of(), grants, 0,
                resources(catalog, classes, abilities, Map.of()), RestState.INITIAL);
        return validate(material, state);
    }

    Prepared prepare(ProductionSchema2Projection material, State before, RuleIdentity targetClass, int targetLevel,
            String hpChoice, GrowthChoices choices) {
        Catalog catalog = new Catalog(material);
        if (choices == null || targetClass == null) fail("S2_GROWTH_REQUEST");
        if (before.level() >= 20 || targetLevel != before.level() + 1) fail("S2_GROWTH_LEVEL");
        ClassProgressionDefinition target = catalog.growth(targetClass);
        Map<RuleIdentity, Integer> levels = classLevels(before.classes());
        int previous = levels.getOrDefault(targetClass, 0), next = previous + 1;
        if (next > target.supportedMaximumLevel()) fail("S2_GROWTH_LEVEL_UNSUPPORTED");
        if (previous == 0) {
            for (RuleIdentity current : levels.keySet()) requirePrerequisite(catalog.growth(current).multiclassPrerequisite(), before.abilities());
            requirePrerequisite(target.multiclassPrerequisite(), before.abilities());
        }
        levels.put(targetClass, next);
        List<ClassLevel> classes = levels.entrySet().stream().sorted(Map.Entry.comparingByKey(ORDER))
                .map(entry -> new ClassLevel(entry.getKey(), entry.getValue())).toList();
        ClassLevelEntry levelRow = catalog.level(target.identity(), next);
        Map<String, Integer> abilities = new TreeMap<>(before.abilities());
        boolean asi = !choices.abilityIncreases().isEmpty(), feat = choices.feat() != null;
        if (levelRow.asiAvailable() != (asi || feat) || asi && feat) fail(levelRow.asiAvailable() ? "S2_GROWTH_ASI_REQUIRED" : "S2_GROWTH_UNEXPECTED_ASI");
        if (asi) {
            if (choices.abilityIncreases().values().stream().mapToInt(Integer::intValue).sum() != 2) fail("S2_GROWTH_ASI_SELECTION");
            for (var increase : choices.abilityIncreases().entrySet()) {
                int value = abilities.get(increase.getKey()) + increase.getValue();
                if (value > 20) fail("S2_GROWTH_ABILITY_LIMIT"); abilities.put(increase.getKey(), value);
            }
        }
        List<FeatState> feats = new ArrayList<>(before.feats());
        if (feat) {
            FinalRule selected = catalog.rules.get(choices.feat());
            if (!(selected instanceof FinalFeat definition) || !selected.enabled()) fail("S2_GROWTH_FEAT");
            FeatDefinition definition = ((FinalFeat) selected).definition();
            requirePrerequisite(definition.prerequisite(), abilities);
            if (feats.stream().anyMatch(value -> value.feat().equals(choices.feat()))) fail("S2_GROWTH_FEAT_DUPLICATE");
            feats.add(new FeatState(choices.feat(), targetClass, next));
        }
        feats.sort(Comparator.comparing(FeatState::feat, ORDER));
        List<SubclassState> subclasses = selectSubclass(catalog, targetClass, next, before.subclasses(), choices.subclass());
        List<ProficiencyState> grants = previous == 0 ? proficiencyGrants(catalog, target.identity(), "multiclass_proficiencies",
                targetLevel, before.proficiencies(), choices.proficiencyChoices()) : noChoices(before.proficiencies(), choices.proficiencyChoices());
        List<HitDicePool> dice = new ArrayList<>(before.hitDicePools());
        int index = -1;
        for (int i = 0; i < dice.size(); i++) if (dice.get(i).sides() == target.hitDieSides()) index = i;
        if (index < 0) dice.add(new HitDicePool(target.hitDieSides(), 1, 1));
        else { var old = dice.get(index); dice.set(index, new HitDicePool(old.sides(), old.current() + 1, old.maximum() + 1)); }
        dice.sort(Comparator.comparingInt(HitDicePool::sides));
        int constitution = modifier(abilities, "ability.constitution");
        int retroactive = before.level() * (constitution - modifier(before.abilities(), "ability.constitution"));
        int minimum = Math.max(1, (hpChoice.equals("FIXED_AVERAGE") ? target.hitDieSides() / 2 + 1 : 1) + constitution) + retroactive;
        int maximum = hpChoice.equals("FIXED_AVERAGE") ? minimum : Math.max(1, target.hitDieSides() + constitution) + retroactive;
        if (before.maximumHitPoints() + maximum > 65_535) fail("S2_GROWTH_HIT_POINT_LIMIT");
        Map<RuleIdentity, ResourceState> oldResources = new HashMap<>();
        before.resources().forEach(resource -> oldResources.put(resource.identity(), resource));
        State candidate = new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(),
                before.characterClass(), abilities, targetLevel, 2 + (targetLevel - 1) / 4,
                before.hitPoints() + minimum, before.maximumHitPoints() + minimum, classes, dice, subclasses,
                features(catalog, classes, subclasses), feats, grants, before.version() + 1,
                resources(catalog, classes, abilities, oldResources), before.rest());
        validate(material, candidate);
        return new Prepared(candidate, target.hitDieSides(), constitution, minimum, maximum);
    }

    State validate(ProductionSchema2Projection material, State state) {
        Catalog catalog = new Catalog(material);
        if (state.level() < 1 || state.level() > 20 || state.version() < 0 || state.version() >= 128
                || state.proficiencyBonus() != 2 + (state.level() - 1) / 4 || state.maximumHitPoints() < 1
                || state.maximumHitPoints() > 65_535 || state.hitPoints() < 0 || state.hitPoints() > state.maximumHitPoints()
                || state.rest() == null || !state.abilities().keySet().equals(ABILITIES)
                || state.abilities().values().stream().anyMatch(value -> value < 1 || value > 20)) fail("S2_GROWTH_STATE");
        Map<RuleIdentity, Integer> levels = classLevels(state.classes());
        if (!levels.containsKey(state.characterClass()) || levels.values().stream().mapToInt(Integer::intValue).sum() != state.level()
                || !state.classes().equals(state.classes().stream().sorted(Comparator.comparing(ClassLevel::characterClass, ORDER)).toList())) fail("S2_GROWTH_STATE");
        Map<Integer, Integer> maximumDice = new TreeMap<>();
        for (ClassLevel level : state.classes()) {
            var progression = catalog.growth(level.characterClass());
            if (level.level() > progression.supportedMaximumLevel()) fail("S2_GROWTH_STATE");
            maximumDice.merge(progression.hitDieSides(), level.level(), Integer::sum);
        }
        if (state.hitDicePools().size() != maximumDice.size()) fail("S2_GROWTH_STATE");
        int lastSides = 0;
        for (HitDicePool pool : state.hitDicePools()) {
            if (pool.sides() <= lastSides || pool.maximum() != maximumDice.getOrDefault(pool.sides(), -1)
                    || pool.current() < 0 || pool.current() > pool.maximum()) fail("S2_GROWTH_STATE"); lastSides = pool.sides();
        }
        Set<RuleIdentity> subclassClasses = new HashSet<>();
        for (SubclassState subclass : state.subclasses()) {
            FinalRule frozen = catalog.rules.get(subclass.subclass());
            if (!(frozen instanceof FinalSubclass value) || !frozen.enabled()) fail("S2_GROWTH_SUBCLASS_STATE");
            var definition = ((FinalSubclass) frozen).definition();
            if (!definition.ownerClass().equals(subclass.characterClass()) || !subclassClasses.add(subclass.characterClass())
                    || !levels.containsKey(subclass.characterClass()) || definition.selectionLevel() > levels.get(subclass.characterClass())
                    || subclass.acquiredAtClassLevel() != definition.selectionLevel()) fail("S2_GROWTH_SUBCLASS_STATE");
        }
        for (ClassLevel level : state.classes()) if (catalog.subclasses(level.characterClass()).stream()
                .anyMatch(subclass -> subclass.selectionLevel() <= level.level()) && !subclassClasses.contains(level.characterClass())) fail("S2_GROWTH_SUBCLASS_STATE");
        if (!state.subclasses().equals(state.subclasses().stream().sorted(Comparator.comparing(SubclassState::characterClass, ORDER)).toList())) fail("S2_GROWTH_SUBCLASS_STATE");
        if (!features(catalog, state.classes(), state.subclasses()).equals(state.features())) fail("S2_GROWTH_FEATURE_STATE");
        if (state.feats().size() > 20 || state.features().size() > 64 || state.proficiencies().size() > 64) fail("S2_GROWTH_BUDGET");
        Set<RuleIdentity> seenFeats = new HashSet<>();
        for (FeatState feat : state.feats()) {
            var frozen = catalog.rules.get(feat.feat());
            if (!(frozen instanceof FinalFeat) || !frozen.enabled() || !seenFeats.add(feat.feat())
                    || !levels.containsKey(feat.characterClass()) || feat.acquiredAtClassLevel() < 1
                    || feat.acquiredAtClassLevel() > levels.get(feat.characterClass())
                    || !catalog.level(catalog.growth(feat.characterClass()).identity(), feat.acquiredAtClassLevel()).asiAvailable()) fail("S2_GROWTH_FEAT_STATE");
            requirePrerequisite(((FinalFeat) frozen).definition().prerequisite(), state.abilities());
        }
        if (!state.feats().equals(state.feats().stream().sorted(Comparator.comparing(FeatState::feat, ORDER)).toList())) fail("S2_GROWTH_FEAT_STATE");
        validateProficiencies(catalog, state);
        List<ResourceState> expected = resources(catalog, state.classes(), state.abilities(), Map.of());
        if (state.resources().size() != expected.size()) fail("S2_RESOURCE_STATE");
        for (int i = 0; i < expected.size(); i++) {
            var current = state.resources().get(i); var maximum = expected.get(i);
            if (!current.identity().equals(maximum.identity()) || current.maximum() != maximum.maximum()
                    || current.unlimited() != maximum.unlimited() || current.current() < 0 || current.current() > current.maximum()
                    || current.unlimited() && current.current() != 0) fail("S2_RESOURCE_STATE");
        }
        return state;
    }

    private static List<SubclassState> selectSubclass(Catalog catalog, RuleIdentity characterClass, int level,
            List<SubclassState> previous, RuleIdentity requested) {
        List<SubclassState> result = new ArrayList<>(previous);
        var existing = previous.stream().filter(row -> row.characterClass().equals(characterClass)).findFirst();
        List<SubclassDefinition> due = catalog.subclasses(characterClass).stream().filter(row -> row.selectionLevel() <= level).toList();
        if (existing.isPresent()) {
            if (requested != null) fail("S2_GROWTH_SUBCLASS_ALREADY_SELECTED"); return List.copyOf(previous);
        }
        if (due.isEmpty()) {
            if (requested != null) fail("S2_GROWTH_SUBCLASS_NOT_DUE"); return List.copyOf(previous);
        }
        var selected = due.stream().filter(row -> row.identity().equals(requested)).findFirst();
        if (selected.isEmpty()) fail("S2_GROWTH_SUBCLASS_REQUIRED");
        var choice = selected.orElseThrow(); result.add(new SubclassState(characterClass, choice.identity(), choice.selectionLevel()));
        result.sort(Comparator.comparing(SubclassState::characterClass, ORDER)); return List.copyOf(result);
    }

    private static List<FeatureState> features(Catalog catalog, List<ClassLevel> classes, List<SubclassState> subclasses) {
        Map<RuleIdentity, Integer> levels = classLevels(classes);
        for (SubclassState subclass : subclasses) levels.put(subclass.subclass(), levels.get(subclass.characterClass()));
        List<FeatureState> result = new ArrayList<>();
        for (FinalRule rule : catalog.rules.values()) if (rule instanceof FinalFeature feature && rule.enabled()
                && feature.definition().kind() == FeatureKind.FEATURE && levels.containsKey(feature.definition().owner())
                && feature.definition().level() <= levels.get(feature.definition().owner())) {
            if (feature.definition().executionAlgorithm().equals("BOUNDED_FEATURE_SELECTION_V1"))
                fail("S2_GROWTH_FEATURE_SELECTION_UNSUPPORTED");
            if (feature.definition().executionAlgorithm().equals("BOUNDED_SUBCLASS_SELECTION_V1")
                    && subclasses.stream().noneMatch(row -> row.characterClass().equals(feature.definition().owner())
                        && row.acquiredAtClassLevel() <= feature.definition().level())) fail("S2_GROWTH_SUBCLASS_REQUIRED");
            result.add(new FeatureState(feature.identity(), feature.definition().owner(), feature.definition().level()));
        }
        if (result.size() > 64) fail("S2_GROWTH_BUDGET");
        return result.stream().sorted(Comparator.comparing(FeatureState::feature, ORDER)).toList();
    }

    private static List<ProficiencyState> proficiencyGrants(Catalog catalog, RuleIdentity progression, String collection,
            int totalLevel, List<ProficiencyState> previous, Map<EntryIdentity, List<RuleIdentity>> choices) {
        List<ProficiencyState> result = new ArrayList<>(previous);
        Set<RuleIdentity> known = new HashSet<>(); previous.forEach(row -> known.add(row.target()));
        List<GrowthProficiencyGrantEntry> slots = catalog.entries.stream().filter(FinalEntry::enabled)
                .map(FinalEntry::entry).filter(GrowthProficiencyGrantEntry.class::isInstance).map(GrowthProficiencyGrantEntry.class::cast)
                .filter(row -> row.identity().rule().equals(progression) && row.identity().collectionKey().equals(collection))
                .sorted(Comparator.comparing(GrowthProficiencyGrantEntry::identity, Schema2InitialProficiencyRules.SLOT_ORDER)).toList();
        Set<EntryIdentity> obligations = new HashSet<>();
        for (var slot : slots) if (slot.grant() instanceof FixedGrowthGrant fixed) {
            FinalRule target = catalog.rules.get(fixed.target());
            if (target != null && target.enabled()) { result.add(new ProficiencyState(slot.identity(), fixed.target(), totalLevel)); known.add(fixed.target()); }
        } else obligations.add(slot.identity());
        if (!choices.keySet().equals(obligations)) fail("S2_GROWTH_PROFICIENCY_SLOTS");
        for (var slot : slots) if (slot.grant() instanceof ChoiceGrowthGrant choice) {
            List<RuleIdentity> selected = choices.get(slot.identity());
            if (selected.size() != choice.count()) fail("S2_GROWTH_PROFICIENCY_COUNT");
            Set<RuleIdentity> available = catalog.candidates(choice.candidates());
            for (RuleIdentity identity : selected) {
                if (!available.contains(identity)) fail("S2_GROWTH_PROFICIENCY_TARGET");
                if (!known.add(identity)) fail("S2_GROWTH_PROFICIENCY_DUPLICATE");
                result.add(new ProficiencyState(slot.identity(), identity, totalLevel));
            }
        }
        if (result.size() > 64) fail("S2_GROWTH_BUDGET");
        return result.stream().sorted(GRANT_ORDER).toList();
    }

    private static void validateProficiencies(Catalog catalog, State state) {
        Set<Map.Entry<EntryIdentity, RuleIdentity>> seen = new HashSet<>();
        Map<EntryIdentity, List<ProficiencyState>> bySlot = new HashMap<>();
        for (ProficiencyState grant : state.proficiencies()) {
            if (grant.acquiredAtTotalLevel() < 1 || grant.acquiredAtTotalLevel() > state.level()
                    || !seen.add(Map.entry(grant.sourceSlot(), grant.target()))) fail("S2_GROWTH_PROFICIENCY_STATE");
            var slot = catalog.entries.stream().filter(row -> row.enabled() && row.entry().identity().equals(grant.sourceSlot())).findFirst();
            var target = catalog.rules.get(grant.target());
            if (slot.isEmpty() || target == null || !target.enabled()) fail("S2_GROWTH_PROFICIENCY_STATE");
            Entry entry = slot.orElseThrow().entry();
            if (entry instanceof GrowthProficiencyGrantEntry growth) {
                var source = catalog.rules.get(growth.identity().rule());
                if (!(source instanceof FinalClassProgression) || !source.enabled()) fail("S2_GROWTH_PROFICIENCY_STATE");
                RuleIdentity owner = ((FinalClassProgression) source).definition().ownerClass();
                if (state.classes().stream().noneMatch(row -> row.characterClass().equals(owner))) fail("S2_GROWTH_PROFICIENCY_STATE");
                String expectedCollection = owner.equals(state.characterClass()) ? "starting_proficiencies" : "multiclass_proficiencies";
                if (!growth.identity().collectionKey().equals(expectedCollection)
                        || owner.equals(state.characterClass()) && grant.acquiredAtTotalLevel() != 1
                        || !owner.equals(state.characterClass()) && grant.acquiredAtTotalLevel() < 2) fail("S2_GROWTH_PROFICIENCY_STATE");
                if (growth.grant() instanceof FixedGrowthGrant fixed && !fixed.target().equals(grant.target())
                        || growth.grant() instanceof ChoiceGrowthGrant choice && !catalog.candidates(choice.candidates()).contains(grant.target())) fail("S2_GROWTH_PROFICIENCY_STATE");
            } else if (entry instanceof ProficiencyGrantEntry initial) {
                if (grant.acquiredAtTotalLevel() != 1 || !grant.target().ruleType().equals("character.tool")) fail("S2_GROWTH_PROFICIENCY_STATE");
                if (initial.grant() instanceof FixedGrant fixed && !fixed.target().equals(grant.target())) fail("S2_GROWTH_PROFICIENCY_STATE");
                if (initial.grant() instanceof ChoiceGrant choice && (choice.category() != GrantCategory.TOOL
                        || !catalog.rules.get(choice.candidates()).enabled()
                        || catalog.entries.stream().noneMatch(row -> row.enabled() && row.entry() instanceof ToolEntry member
                            && member.identity().rule().equals(choice.candidates()) && member.tool().equals(grant.target())))) fail("S2_GROWTH_PROFICIENCY_STATE");
            } else fail("S2_GROWTH_PROFICIENCY_STATE");
            bySlot.computeIfAbsent(grant.sourceSlot(), ignored -> new ArrayList<>()).add(grant);
        }
        for (ClassLevel characterClass : state.classes()) {
            var progression = catalog.growth(characterClass.characterClass());
            String collection = characterClass.characterClass().equals(state.characterClass()) ? "starting_proficiencies" : "multiclass_proficiencies";
            for (FinalEntry row : catalog.entries) if (row.enabled() && row.entry() instanceof GrowthProficiencyGrantEntry grant
                    && grant.identity().rule().equals(progression.identity()) && grant.identity().collectionKey().equals(collection)) {
                int count = grant.grant() instanceof ChoiceGrowthGrant choice ? choice.count()
                        : catalog.rules.get(((FixedGrowthGrant) grant.grant()).target()).enabled() ? 1 : 0;
                if (bySlot.getOrDefault(grant.identity(), List.of()).size() != count) fail("S2_GROWTH_PROFICIENCY_STATE");
            }
        }
        if (!state.proficiencies().equals(state.proficiencies().stream().sorted(GRANT_ORDER).toList())) fail("S2_GROWTH_PROFICIENCY_STATE");
    }

    private static List<ProficiencyState> noChoices(List<ProficiencyState> previous, Map<EntryIdentity, List<RuleIdentity>> choices) {
        if (!choices.isEmpty()) fail("S2_GROWTH_PROFICIENCY_NOT_DUE"); return previous;
    }
    private static Map<RuleIdentity, Integer> classLevels(List<ClassLevel> classes) {
        if (classes == null || classes.isEmpty() || classes.size() > 20) fail("S2_GROWTH_STATE");
        Map<RuleIdentity, Integer> result = new HashMap<>();
        for (ClassLevel level : classes) if (level == null || level.level() < 1 || level.level() > 20
                || result.put(level.characterClass(), level.level()) != null) fail("S2_GROWTH_STATE");
        return result;
    }
    private static void requirePrerequisite(AbilityPrerequisite predicate, Map<String, Integer> abilities) {
        if (predicate == null || predicate.allOf().stream().anyMatch(group -> group.anyOf().stream()
                .noneMatch(term -> abilities.get(term.abilityKey()) >= term.minimum()))) fail("S2_GROWTH_PREREQUISITE");
    }
    private static List<ResourceState> resources(Catalog catalog, List<ClassLevel> classes,
            Map<String, Integer> abilities, Map<RuleIdentity, ResourceState> previous) {
        Map<RuleIdentity, Integer> levels = classLevels(classes);
        List<ResourceState> result = new ArrayList<>();
        for (FinalRule rule : catalog.rules.values()) if (rule instanceof FinalResource resource) {
            var definition = resource.definition();
            if (!levels.containsKey(definition.ownerClass()) || !rule.enabled() || definition.executionMode() == ResourceExecution.BLOCKED) continue;
            var maximum = AdvancementValueProfile.parse(definition.maximumProfile()).atLevel(levels.get(definition.ownerClass()), modifier(abilities, "ability.charisma"));
            if (!maximum.unlimited() && maximum.maximum() == 0) continue;
            var old = previous.get(rule.identity());
            long spent = old == null || old.unlimited() ? 0 : old.maximum() - old.current();
            result.add(new ResourceState(rule.identity(), maximum.unlimited() ? 0 : Math.max(0, maximum.maximum() - spent), maximum.maximum(), maximum.unlimited()));
        }
        if (result.size() > 32) fail("S2_RESOURCE_BUDGET");
        return result.stream().sorted(Comparator.comparing(ResourceState::identity, ORDER)).toList();
    }
    private static int modifier(Map<String, Integer> values, String key) { return Math.floorDiv(values.get(key) - 10, 2); }
    private static void fail(String code) { throw new IllegalArgumentException(code); }

    private static final class Catalog {
        final Map<RuleIdentity, FinalRule> rules = new HashMap<>();
        final List<FinalEntry> entries;
        Catalog(ProductionSchema2Projection material) {
            material.projection().rules().forEach(row -> rules.put(row.identity(), row)); entries = material.projection().entries();
        }
        ClassProgressionDefinition growth(RuleIdentity characterClass) {
            var source = rules.get(characterClass);
            if (!(source instanceof FinalCharacterSource) || !source.enabled() || !characterClass.ruleType().equals("character.class")) fail("S2_GROWTH_CLASS");
            var matches = rules.values().stream().filter(FinalClassProgression.class::isInstance).map(FinalClassProgression.class::cast)
                    .filter(row -> row.enabled() && row.definition().ownerClass().equals(characterClass)).toList();
            if (matches.size() != 1 || matches.getFirst().definition().advancementScope() != ProgressionScope.CHARACTER_ADVANCEMENT) fail("S2_GROWTH_SCOPE_UNSUPPORTED");
            return matches.getFirst().definition();
        }
        ClassLevelEntry level(RuleIdentity progression, int level) {
            var matches = entries.stream().filter(FinalEntry::enabled).map(FinalEntry::entry).filter(ClassLevelEntry.class::isInstance)
                    .map(ClassLevelEntry.class::cast).filter(row -> row.identity().rule().equals(progression) && row.level() == level).toList();
            if (matches.size() != 1) fail("S2_GROWTH_LEVEL_UNSUPPORTED"); return matches.getFirst();
        }
        List<SubclassDefinition> subclasses(RuleIdentity characterClass) {
            return rules.values().stream().filter(FinalSubclass.class::isInstance).map(FinalSubclass.class::cast)
                    .filter(row -> row.enabled() && row.definition().ownerClass().equals(characterClass)).map(FinalSubclass::definition).toList();
        }
        Set<RuleIdentity> candidates(RuleIdentity set) {
            var owner = rules.get(set); if (!(owner instanceof FinalProficiencySet) || !owner.enabled()) fail("S2_GROWTH_PROFICIENCY_TARGET");
            Set<RuleIdentity> result = new HashSet<>();
            for (FinalEntry row : entries) if (row.enabled() && row.entry() instanceof ProficiencyMemberEntry member
                    && member.identity().rule().equals(set) && rules.get(member.proficiency()).enabled()) result.add(member.proficiency());
            return result;
        }
    }
}
