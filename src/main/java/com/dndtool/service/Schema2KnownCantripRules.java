package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.ClassCantrips;
import com.dndtool.service.Schema2CharacterRuntime.ClassLevel;
import com.dndtool.service.Schema2CharacterRuntime.KnownCantrip;
import com.dndtool.service.Schema2CharacterRuntime.State;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import static com.dndtool.module.composition.Schema2CompositionKernel.SpellDefinition;

/** Class-level known cantrip acquisition from the frozen class options. */
public final class Schema2KnownCantripRules {
    private Schema2KnownCantripRules() { }

    public static List<RuleIdentity> initialDefaults(ClassCantrips profile) {
        return defaults(profile, profile.definition().knownCount(1), Set.of());
    }

    static List<KnownCantrip> acquire(ProductionSchema2Projection material, RuleIdentity owner, int level,
            List<RuleIdentity> requested, List<KnownCantrip> retained) {
        var found = material.classCantrips(owner);
        if (found.isEmpty()) {
            if (!requested.isEmpty()) throw choices();
            return retained;
        }
        ClassCantrips profile = found.orElseThrow();
        int due = due(profile, level);
        Set<RuleIdentity> previouslyKnown = retained.stream().filter(fact -> fact.characterClass().equals(owner))
                .map(KnownCantrip::spell).collect(Collectors.toSet());
        List<RuleIdentity> selected = requested.isEmpty() ? defaults(profile, due, previouslyKnown) : requested;
        Set<RuleIdentity> options = options(profile);
        if (selected.size() != due || new HashSet<>(selected).size() != due || !options.containsAll(selected)
                || selected.stream().anyMatch(previouslyKnown::contains)) throw choices();
        if (due == 0) return retained;
        List<KnownCantrip> result = new ArrayList<>(retained);
        for (RuleIdentity spell : selected) result.add(new KnownCantrip(spell, owner, profile.definition().identity(), level));
        result.sort(Schema2CharacterRuntime.CANTRIP_ORDER);
        return List.copyOf(result);
    }

    static void validate(ProductionSchema2Projection material, State state) {
        List<KnownCantrip> facts = state.knownCantrips();
        if (!facts.equals(facts.stream().sorted(Schema2CharacterRuntime.CANTRIP_ORDER).toList())) throw state();
        var byClass = facts.stream().collect(Collectors.groupingBy(KnownCantrip::characterClass));
        if (!state.classes().stream().map(ClassLevel::characterClass).collect(Collectors.toSet()).containsAll(byClass.keySet()))
            throw state();
        for (ClassLevel owner : state.classes()) {
            var profile = material.classCantrips(owner.characterClass()).orElse(null);
            List<KnownCantrip> known = byClass.getOrDefault(owner.characterClass(), List.of());
            if (profile == null) {
                if (!known.isEmpty()) throw state();
                continue;
            }
            Set<RuleIdentity> options = options(profile);
            Set<RuleIdentity> unique = new HashSet<>();
            var counts = new int[owner.level() + 1];
            if (profile.definition().knownCount(owner.level()) == 0) throw state();
            for (KnownCantrip fact : known) {
                if (!fact.cantripProfile().equals(profile.definition().identity()) || !options.contains(fact.spell())
                        || !unique.add(fact.spell()) || fact.acquiredAtClassLevel() > owner.level()) throw state();
                counts[fact.acquiredAtClassLevel()]++;
            }
            for (int level = 1; level <= owner.level(); level++) if (counts[level] != due(profile, level)) throw state();
        }
    }

    private static int due(ClassCantrips profile, int level) {
        int count = profile.definition().knownCount(level);
        int previous = level == 1 ? 0 : profile.definition().knownCount(level - 1);
        if (count == 0 || count < previous) throw state();
        return count - previous;
    }

    private static Set<RuleIdentity> options(ClassCantrips profile) {
        return profile.options().stream().map(SpellDefinition::identity).collect(Collectors.toUnmodifiableSet());
    }

    private static List<RuleIdentity> defaults(ClassCantrips profile, int count, Set<RuleIdentity> retained) {
        return profile.options().stream().filter(spell -> !retained.contains(spell.identity()))
                .sorted(Comparator.comparing((SpellDefinition spell) -> spell.cantripProfile() == null)
                        .thenComparing(SpellDefinition::identity, Schema2InitialProficiencyRules.RULE_ORDER))
                .limit(count).map(SpellDefinition::identity).toList();
    }

    private static IllegalArgumentException choices() { return new IllegalArgumentException("S2_KNOWN_CANTRIP_CHOICES"); }
    private static IllegalArgumentException state() { return new IllegalArgumentException("S2_KNOWN_CANTRIP_STATE"); }
}
