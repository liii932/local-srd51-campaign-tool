package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Server-owned multiclass, ASI and feat choices for one canonical-v2 class level. */
public final class CharacterAdvancementChoiceRules {
    private static final List<String> ABILITIES = List.of(
            "ability.charisma", "ability.constitution", "ability.dexterity",
            "ability.intelligence", "ability.strength", "ability.wisdom");

    public Prepared prepare(ModuleCatalog catalog, Map<String, Integer> currentClassLevels,
            Map<String, Integer> currentAbilityScores, Set<String> acquiredFeats, Request request) {
        return prepare(catalog, currentClassLevels, currentAbilityScores, acquiredFeats,
                Set.of(), request);
    }

    public Prepared prepare(ModuleCatalog catalog, Map<String, Integer> currentClassLevels,
            Map<String, Integer> currentAbilityScores, Set<String> acquiredFeats,
            Set<String> acquiredProficiencies, Request request) {
        if (catalog == null || request == null || currentClassLevels == null
                || currentAbilityScores == null || acquiredFeats == null
                || acquiredProficiencies == null || !stableKey(request.targetClassKey())
                || currentClassLevels.isEmpty()) {
            throw invalid("INVALID_REQUEST");
        }
        Map<String, Integer> classes = canonicalLevels(currentClassLevels);
        Map<String, Integer> abilities = canonicalAbilities(currentAbilityScores);
        Set<String> feats = canonicalFeats(acquiredFeats);
        requireDefinition(catalog, "character.class", request.targetClassKey());
        int totalLevel = classes.values().stream().mapToInt(Integer::intValue).sum();
        if (totalLevel < 1 || totalLevel >= 20) throw invalid("INVALID_LEVEL_TRANSITION");

        boolean multiclass = !classes.containsKey(request.targetClassKey());
        int previousClassLevel = classes.getOrDefault(request.targetClassKey(), 0);
        int nextClassLevel = previousClassLevel + 1;
        if (multiclass) {
            for (String classKey : classes.keySet()) {
                requirePrerequisite(catalog, "character.class", classKey,
                        "class.multiclass_prerequisite", abilities,
                        "MULTICLASS_PREREQUISITE_NOT_MET");
            }
            requirePrerequisite(catalog, "character.class", request.targetClassKey(),
                    "class.multiclass_prerequisite", abilities,
                    "MULTICLASS_PREREQUISITE_NOT_MET");
        }

        List<Integer> asiLevels = integerListAttribute(
                catalog, "character.class", request.targetClassKey(), "class.asi_levels");
        boolean asiAvailable = asiLevels.contains(nextClassLevel);
        Map<String, Integer> increases = canonicalIncreases(request.abilityIncreases());
        String featKey = request.featKey();
        boolean selectedAsi = !increases.isEmpty();
        boolean selectedFeat = featKey != null;
        if (asiAvailable != (selectedAsi || selectedFeat) || selectedAsi && selectedFeat) {
            throw invalid(asiAvailable ? "INVALID_ASI_SELECTION" : "UNEXPECTED_ADVANCEMENT_CHOICE");
        }
        if (selectedAsi) applyIncreases(abilities, increases);
        if (selectedFeat) {
            requireDefinition(catalog, "character.feat", featKey);
            if (feats.contains(featKey)) throw invalid("DUPLICATE_FEAT");
            requirePrerequisite(catalog, "character.feat", featKey, "feat.prerequisite",
                    abilities, "FEAT_PREREQUISITE_NOT_MET");
        }

        List<String> proficiencyGrants = multiclass
                ? proficiencyGrants(catalog, request.targetClassKey(),
                        request.proficiencyChoices(), acquiredProficiencies)
                : requireNoProficiencyChoices(request.proficiencyChoices());
        classes.put(request.targetClassKey(), nextClassLevel);
        return new Prepared(request.targetClassKey(), previousClassLevel, multiclass,
                Map.copyOf(classes), Map.copyOf(abilities), Map.copyOf(increases),
                featKey, proficiencyGrants);
    }

    private static Map<String, Integer> canonicalLevels(Map<String, Integer> values) {
        Map<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!stableKey(entry.getKey()) || !entry.getKey().startsWith("class.")
                    || entry.getValue() == null || entry.getValue() < 1
                    || entry.getValue() > 20 || result.put(entry.getKey(), entry.getValue()) != null) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
        }
        if (result.values().stream().mapToInt(Integer::intValue).sum() > 20) {
            throw invalid("AUTHORITATIVE_STATE_MISMATCH");
        }
        return result;
    }

    private static Map<String, Integer> canonicalAbilities(Map<String, Integer> values) {
        if (!values.keySet().equals(Set.copyOf(ABILITIES))) {
            throw invalid("AUTHORITATIVE_STATE_MISMATCH");
        }
        Map<String, Integer> result = new TreeMap<>();
        for (String ability : ABILITIES) {
            Integer value = values.get(ability);
            if (value == null || value < 1 || value > 30) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
            result.put(ability, value);
        }
        return result;
    }

    private static Set<String> canonicalFeats(Set<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (!stableKey(value) || !value.startsWith("feat.") || !result.add(value)) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
        }
        return Set.copyOf(result);
    }

    private static Map<String, Integer> canonicalIncreases(Map<String, Integer> values) {
        if (values == null) values = Map.of();
        Map<String, Integer> result = new TreeMap<>();
        int total = 0;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!ABILITIES.contains(entry.getKey()) || entry.getValue() == null
                    || entry.getValue() < 1 || entry.getValue() > 2
                    || result.put(entry.getKey(), entry.getValue()) != null) {
                throw invalid("INVALID_ASI_SELECTION");
            }
            total += entry.getValue();
        }
        if (!result.isEmpty() && total != 2) throw invalid("INVALID_ASI_SELECTION");
        return result;
    }

    private static void applyIncreases(
            Map<String, Integer> abilities, Map<String, Integer> increases) {
        for (Map.Entry<String, Integer> entry : increases.entrySet()) {
            int next = abilities.get(entry.getKey()) + entry.getValue();
            if (next > 20) throw invalid("INVALID_ASI_SELECTION");
            abilities.put(entry.getKey(), next);
        }
    }

    private static void requirePrerequisite(ModuleCatalog catalog, String type, String key,
            String attributeKey, Map<String, Integer> abilities, String error) {
        String expression = textAttribute(catalog, type, key, attributeKey);
        boolean matched = false;
        for (String alternative : expression.split("\\|", -1)) {
            boolean all = true;
            for (String term : alternative.split("&", -1)) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "(ability\\.(?:strength|dexterity|constitution|intelligence|wisdom|charisma))>=(1[0-9]|20)")
                        .matcher(term);
                if (!matcher.matches()) throw invalid("MALFORMED_FROZEN_CATALOG");
                all &= abilities.get(matcher.group(1)) >= Integer.parseInt(matcher.group(2));
            }
            matched |= all;
        }
        if (!matched) throw invalid(error);
    }

    private static List<String> proficiencyGrants(ModuleCatalog catalog, String classKey,
            List<String> requested, Set<String> acquiredProficiencies) {
        String profile = textAttribute(catalog, "character.class", classKey,
                "class.multiclass_proficiency_profile");
        Set<String> fixed = new HashSet<>();
        List<Choice> choices = new ArrayList<>();
        if (!profile.isEmpty()) {
            for (String part : profile.split("\\|", -1)) {
                if (part.startsWith("grant=")) {
                    addStableList(fixed, part.substring(6));
                } else {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "choice=([1-9]):(.+)").matcher(part);
                    if (!matcher.matches()) throw invalid("MALFORMED_FROZEN_CATALOG");
                    Set<String> candidates = new HashSet<>();
                    addStableList(candidates, matcher.group(2));
                    choices.add(new Choice(Integer.parseInt(matcher.group(1)), candidates));
                }
            }
        }
        List<String> selected = canonicalStableList(requested);
        List<String> existing = canonicalStableList(new ArrayList<>(acquiredProficiencies));
        if (!java.util.Collections.disjoint(selected, existing)
                || selected.size() != choices.stream().mapToInt(Choice::count).sum()
                || !assignable(selected, choices, 0, new int[choices.size()])) {
            throw invalid("INVALID_MULTICLASS_PROFICIENCY_SELECTION");
        }
        if (!java.util.Collections.disjoint(fixed, selected)) {
            throw invalid("DUPLICATE_SELECTION");
        }
        fixed.addAll(selected);
        fixed.removeAll(existing);
        return fixed.stream().sorted().toList();
    }

    private static List<String> requireNoProficiencyChoices(List<String> requested) {
        List<String> selected = canonicalStableList(requested);
        if (!selected.isEmpty()) throw invalid("UNEXPECTED_MULTICLASS_PROFICIENCY_SELECTION");
        return List.of();
    }

    private static boolean assignable(
            List<String> selected, List<Choice> choices, int index, int[] assigned) {
        if (index == selected.size()) {
            for (int choice = 0; choice < choices.size(); choice++) {
                if (assigned[choice] != choices.get(choice).count()) return false;
            }
            return true;
        }
        for (int choice = 0; choice < choices.size(); choice++) {
            Choice value = choices.get(choice);
            if (assigned[choice] >= value.count() || !value.candidates().contains(selected.get(index))) {
                continue;
            }
            assigned[choice]++;
            if (assignable(selected, choices, index + 1, assigned)) return true;
            assigned[choice]--;
        }
        return false;
    }

    private static void addStableList(Set<String> target, String encoded) {
        if (encoded.isEmpty()) return;
        for (String value : encoded.split(",", -1)) {
            if (!stableKey(value) || !target.add(value)) throw invalid("MALFORMED_FROZEN_CATALOG");
        }
    }

    private static List<String> canonicalStableList(List<String> values) {
        if (values == null) values = List.of();
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (!stableKey(value) || !result.add(value)) {
                throw invalid("DUPLICATE_OR_MALFORMED_SELECTION");
            }
        }
        return result.stream().sorted().toList();
    }

    private static List<Integer> integerListAttribute(
            ModuleCatalog catalog, String type, String key, String attribute) {
        String value = textAttribute(catalog, type, key, attribute);
        List<Integer> result = new ArrayList<>();
        int previous = 0;
        for (String token : value.split(",", -1)) {
            try {
                int level = Integer.parseInt(token);
                if (level <= previous || level < 1 || level > 20) {
                    throw invalid("MALFORMED_FROZEN_CATALOG");
                }
                result.add(level);
                previous = level;
            } catch (NumberFormatException exception) {
                throw invalid("MALFORMED_FROZEN_CATALOG");
            }
        }
        return List.copyOf(result);
    }

    private static String textAttribute(
            ModuleCatalog catalog, String type, String key, String attribute) {
        List<ModuleCatalog.CatalogAttribute> values = catalog.catalogAttributes().stream()
                .filter(row -> type.equals(row.definitionType()) && key.equals(row.definitionKey())
                        && attribute.equals(row.attributeKey())).toList();
        if (values.size() != 1
                || !(values.getFirst().value() instanceof ModuleCatalog.TextValue text)) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        return text.value();
    }

    private static void requireDefinition(ModuleCatalog catalog, String type, String key) {
        if (!stableKey(key) || catalog.catalogDefinitions().stream()
                .filter(row -> type.equals(row.definitionType()) && key.equals(row.definitionKey()))
                .count() != 1) throw invalid("UNKNOWN_CATALOG_KEY");
    }

    private static boolean stableKey(String value) {
        return value != null && value.length() <= 128
                && value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+");
    }

    private static RuleException invalid(String code) {
        return new RuleException(code);
    }

    public record Request(String targetClassKey, Map<String, Integer> abilityIncreases,
            String featKey, List<String> proficiencyChoices) {
        public Request {
            abilityIncreases = abilityIncreases == null ? Map.of() : Map.copyOf(abilityIncreases);
            proficiencyChoices = proficiencyChoices == null
                    ? List.of() : List.copyOf(proficiencyChoices);
        }
    }

    public record Prepared(String targetClassKey, int previousClassLevel,
            boolean multiclass, Map<String, Integer> classLevels,
            Map<String, Integer> abilityScores, Map<String, Integer> abilityIncreases,
            String featKey, List<String> proficiencyGrants) {
        public Prepared {
            classLevels = orderedMap(classLevels);
            abilityScores = orderedMap(abilityScores);
            abilityIncreases = orderedMap(abilityIncreases);
            proficiencyGrants = List.copyOf(proficiencyGrants);
        }

        private static <V> Map<String, V> orderedMap(Map<String, V> source) {
            Map<String, V> result = new LinkedHashMap<>();
            source.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
            return java.util.Collections.unmodifiableMap(result);
        }
    }

    private record Choice(int count, Set<String> candidates) {
    }

    public static final class RuleException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String code;

        RuleException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
