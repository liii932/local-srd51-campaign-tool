package com.dndtool.service;

import com.dndtool.module.CharacterProficiencyGrant.GrantKind;
import com.dndtool.module.composition.ProductionSchema2Projection;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Initial language/tool planning over complete, typed runtime rules; performs no business writes. */
public final class Schema2InitialProficiencyRules {
    public static final int MAX_SLOTS = 512, MAX_SELECTED_TARGETS = 256;
    public static final Comparator<RuleIdentity> RULE_ORDER = Comparator.comparing(RuleIdentity::moduleKey)
            .thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    public static final Comparator<EntryIdentity> SLOT_ORDER = Comparator.comparing(EntryIdentity::rule, RULE_ORDER)
            .thenComparing(EntryIdentity::collectionKey).thenComparing(EntryIdentity::moduleKey)
            .thenComparing(EntryIdentity::entryKey);
    private static final Comparator<GrantFact> GRANT_ORDER = Comparator.comparing(GrantFact::slot, SLOT_ORDER)
            .thenComparing(GrantFact::target, RULE_ORDER);

    public Prepared prepare(ProductionSchema2Projection snapshot, Request request) {
        if (snapshot == null || request == null) throw invalid("S2_INITIAL_REQUEST");
        var projection = snapshot.projection();
        Map<RuleIdentity, FinalRule> rules = new HashMap<>();
        for (FinalRule rule : projection.rules()) {
            if (rules.put(rule.identity(), rule) != null) throw invalid("S2_INITIAL_CATALOG");
        }
        List<RuleIdentity> selectedSources = request.sources().ordered();
        for (RuleIdentity identity : selectedSources) {
            if (!(rules.get(identity) instanceof FinalCharacterSource source) || !source.enabled())
                throw invalid("S2_INITIAL_SOURCE");
        }
        if (request.sources().subrace() != null) {
            var subrace = (FinalCharacterSource) rules.get(request.sources().subrace());
            if (!request.sources().race().equals(subrace.definition().parentRace()))
                throw invalid("S2_INITIAL_SUBRACE_PARENT");
        }

        // Validate the whole selected source graph, including disabled slots and unselected candidates,
        // before interpreting any submitted selection or producing a grant fact.
        List<SlotFact> slots = new ArrayList<>();
        Map<EntryIdentity, Set<RuleIdentity>> candidates = new HashMap<>();
        Set<EntryIdentity> seenEntries = new HashSet<>();
        for (FinalEntry entry : projection.entries()) {
            if (!seenEntries.add(entry.entry().identity())) throw invalid("S2_INITIAL_CATALOG");
            if (!(entry.entry() instanceof ProficiencyGrantEntry grant)
                    || !selectedSources.contains(grant.identity().rule())) continue;
            slotIdentity(grant.identity());
            GrantSpec spec = grant.grant();
            if (spec == null || spec.category() == null) throw invalid("S2_INITIAL_CATALOG");
            if (spec instanceof FixedGrant fixed) {
                target(rules, fixed.target(), spec.category());
            } else if (spec instanceof ChoiceGrant choice) {
                if (choice.count() < 1 || choice.count() > 16) throw invalid("S2_INITIAL_CATALOG");
                FinalRule set = rules.get(choice.candidates());
                boolean language = spec.category() == GrantCategory.LANGUAGE;
                if (!(language ? set instanceof FinalLanguageSet : set instanceof FinalToolSet))
                    throw invalid("S2_INITIAL_CATALOG");
                Set<RuleIdentity> available = new HashSet<>();
                for (FinalEntry member : projection.entries()) {
                    if (!member.entry().identity().rule().equals(choice.candidates())) continue;
                    if (!(language ? member.entry() instanceof LanguageEntry : member.entry() instanceof ToolEntry))
                        throw invalid("S2_INITIAL_CATALOG");
                    FinalRule candidate = target(rules, member.entry().target(), spec.category());
                    if (set.enabled() && member.enabled() && candidate.enabled()) available.add(candidate.identity());
                }
                if (entry.enabled() && available.size() < choice.count())
                    throw invalid("S2_INITIAL_CANDIDATES_INSUFFICIENT");
                candidates.put(grant.identity(), Set.copyOf(available));
            } else throw invalid("S2_INITIAL_CATALOG");
            if (slots.size() == MAX_SLOTS) throw invalid("S2_INITIAL_BUDGET");
            slots.add(new SlotFact(grant.identity(), spec, entry.enabled()));
        }
        slots.sort(Comparator.comparing(SlotFact::slot, SLOT_ORDER));

        List<GrantFact> grants = new ArrayList<>();
        Set<RuleIdentity> effectiveFixed = new HashSet<>();
        Set<EntryIdentity> obligations = new HashSet<>();
        for (SlotFact slot : slots) {
            if (slot.grant() instanceof FixedGrant fixed) {
                boolean suppressed = !slot.enabled() || !rules.get(fixed.target()).enabled();
                grants.add(new GrantFact(slot.slot(), fixed.category(), GrantKind.FIXED, fixed.target(), suppressed));
                if (!suppressed) effectiveFixed.add(fixed.target());
            } else if (slot.enabled()) obligations.add(slot.slot());
        }
        if (!obligations.containsAll(request.choices().keySet())) throw invalid("S2_INITIAL_GRANT_SLOT");
        Set<RuleIdentity> chosen = new HashSet<>();
        for (SlotFact slot : slots) {
            if (!slot.enabled() || !(slot.grant() instanceof ChoiceGrant choice)) continue;
            List<RuleIdentity> selected = request.choices().getOrDefault(slot.slot(), List.of());
            if (selected.size() != choice.count()) throw invalid("S2_INITIAL_SELECTION_COUNT");
            for (RuleIdentity identity : selected) {
                if (!candidates.get(slot.slot()).contains(identity)) throw invalid("S2_INITIAL_SELECTION");
                if (effectiveFixed.contains(identity) || !chosen.add(identity))
                    throw invalid("S2_INITIAL_DUPLICATE_SELECTION");
                grants.add(new GrantFact(slot.slot(), choice.category(), GrantKind.CHOICE, identity, false));
            }
        }
        grants.sort(GRANT_ORDER);
        return new Prepared(request.sources(), request.choices(), slots, grants);
    }

    private static FinalRule target(Map<RuleIdentity, FinalRule> rules, RuleIdentity identity, GrantCategory category) {
        FinalRule target = rules.get(identity);
        if (!(category == GrantCategory.LANGUAGE ? target instanceof FinalLanguage : target instanceof FinalTool))
            throw invalid("S2_INITIAL_CATALOG");
        return target;
    }

    public record Sources(RuleIdentity race, RuleIdentity subrace, RuleIdentity background, RuleIdentity characterClass) {
        public Sources {
            sourceIdentity(race, "character.race", "race.");
            if (subrace != null) sourceIdentity(subrace, "character.subrace", "subrace.");
            sourceIdentity(background, "character.background", "background.");
            sourceIdentity(characterClass, "character.class", "class.");
        }
        public List<RuleIdentity> ordered() {
            return subrace == null ? List.of(race, background, characterClass)
                    : List.of(race, subrace, background, characterClass);
        }
    }

    /** Only real source choices and exact slot-to-target selections are accepted from the caller. */
    public record Request(Sources sources, Map<EntryIdentity, List<RuleIdentity>> choices) {
        public Request {
            if (sources == null) throw invalid("S2_INITIAL_REQUEST");
            choices = immutableChoices(choices);
        }
    }

    /** Retains disabled CHOICE slots without inventing a selected target or a choice obligation. */
    public record SlotFact(EntryIdentity slot, GrantSpec grant, boolean enabled) {
        public SlotFact {
            slotIdentity(slot);
            if (grant == null) throw invalid("S2_INITIAL_CATALOG");
        }
    }

    /** The complete entry identity includes the slot owner, which may differ from its source owner. */
    public record GrantFact(EntryIdentity slot, GrantCategory category, GrantKind kind,
                            RuleIdentity target, boolean suppressed) {
        public GrantFact {
            slotIdentity(slot);
            if (category == null || kind == null) throw invalid("S2_INITIAL_REQUEST");
            targetIdentity(target);
            if (!target.ruleType().equals(category == GrantCategory.LANGUAGE ? "character.language" : "character.tool"))
                throw invalid("S2_INITIAL_REQUEST");
        }
        public RuleIdentity source() { return slot.rule(); }
    }

    public record Prepared(Sources sources, Map<EntryIdentity, List<RuleIdentity>> choices,
                           List<SlotFact> slots, List<GrantFact> grants) {
        public Prepared {
            if (sources == null || slots == null || grants == null || slots.size() > MAX_SLOTS
                    || grants.size() > MAX_SLOTS + MAX_SELECTED_TARGETS) throw invalid("S2_INITIAL_BUDGET");
            choices = immutableChoices(choices);
            Set<EntryIdentity> slotIds = new HashSet<>();
            for (SlotFact slot : slots) {
                if (slot == null || !slotIds.add(slot.slot())) throw invalid("S2_INITIAL_REQUEST");
            }
            Set<Map.Entry<EntryIdentity, RuleIdentity>> grantIds = new HashSet<>();
            for (GrantFact grant : grants) {
                if (grant == null || !grantIds.add(Map.entry(grant.slot(), grant.target())))
                    throw invalid("S2_INITIAL_REQUEST");
            }
            slots = List.copyOf(slots);
            grants = List.copyOf(grants);
        }
    }

    static Map<EntryIdentity, List<RuleIdentity>> immutableChoices(Map<EntryIdentity, List<RuleIdentity>> input) {
        if (input == null || input.size() > MAX_SLOTS) throw invalid("S2_INITIAL_REQUEST");
        TreeMap<EntryIdentity, List<RuleIdentity>> result = new TreeMap<>(SLOT_ORDER);
        int total = 0;
        for (var entry : input.entrySet()) {
            slotIdentity(entry.getKey());
            List<RuleIdentity> targets = entry.getValue();
            if (targets == null || targets.size() > 16 || (total += targets.size()) > MAX_SELECTED_TARGETS)
                throw invalid("S2_INITIAL_BUDGET");
            Set<RuleIdentity> distinct = new HashSet<>();
            for (RuleIdentity target : targets) {
                targetIdentity(target);
                if (!distinct.add(target)) throw invalid("S2_INITIAL_DUPLICATE_SELECTION");
            }
            result.put(entry.getKey(), targets.stream().sorted(RULE_ORDER).toList());
        }
        return Collections.unmodifiableMap(result);
    }

    private static void slotIdentity(EntryIdentity slot) {
        if (slot == null || !"proficiency_grants".equals(slot.collectionKey())) throw invalid("S2_INITIAL_REQUEST");
        RuleIdentity source = slot.rule();
        if (source == null || source.ruleType() == null) throw invalid("S2_INITIAL_REQUEST");
        String prefix = switch (source.ruleType()) {
            case "character.race" -> "race.";
            case "character.subrace" -> "subrace.";
            case "character.background" -> "background.";
            case "character.class" -> "class.";
            default -> throw invalid("S2_INITIAL_REQUEST");
        };
        sourceIdentity(source, source.ruleType(), prefix);
        module(slot.moduleKey());
        if (slot.entryKey() == null || !slot.entryKey().matches("[a-z][a-z0-9_]{0,63}")) throw invalid("S2_INITIAL_REQUEST");
    }

    private static void sourceIdentity(RuleIdentity value, String type, String prefix) {
        if (value == null || !type.equals(value.ruleType())) throw invalid("S2_INITIAL_REQUEST");
        module(value.moduleKey());
        key(value.ruleKey(), prefix);
    }

    private static void targetIdentity(RuleIdentity value) {
        if (value == null) throw invalid("S2_INITIAL_REQUEST");
        String prefix = switch (Objects.toString(value.ruleType(), "")) {
            case "character.language" -> "language.";
            case "character.tool" -> "tool.";
            default -> throw invalid("S2_INITIAL_REQUEST");
        };
        module(value.moduleKey());
        key(value.ruleKey(), prefix);
    }

    private static void module(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) throw invalid("S2_INITIAL_REQUEST");
    }

    private static void key(String value, String prefix) {
        if (value == null || value.length() > 128 || !value.startsWith(prefix)
                || !value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+")) throw invalid("S2_INITIAL_REQUEST");
    }

    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
