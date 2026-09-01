package com.dndtool.service;

import com.dndtool.module.LevelOneRuleProfile;
import com.dndtool.persistence.ModuleCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-owned validation and derivation for canonical-v2 level-one characters. */
public final class LevelOneCharacterRules {
    public static final String ABILITY_METHOD = "ability.standard_array_v1";
    private static final List<String> ABILITIES = List.of(
            "ability.charisma", "ability.constitution", "ability.dexterity",
            "ability.intelligence", "ability.strength", "ability.wisdom");
    private static final List<Integer> STANDARD_ARRAY = List.of(8, 10, 12, 13, 14, 15);
    private final ClassFeatureRules classFeatures = new ClassFeatureRules();

    public Prepared prepare(ModuleCatalog catalog, Request request, long expectedEventTail,
            String contentSha256) {
        if (catalog == null || request == null || expectedEventTail < 0
                || contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) {
            throw invalid("INVALID_REQUEST");
        }
        String name = CharacterNamePolicy.normalize(request.characterName());
        Map<String, ModuleCatalog.CatalogDefinition> definitions = definitions(catalog);
        requireDefinition(definitions, "character.race", request.raceKey());
        requireDefinition(definitions, "character.background", request.backgroundKey());
        requireDefinition(definitions, "character.class", request.classKey());

        LevelOneRuleProfile race = profile(catalog, "character.race", request.raceKey());
        LevelOneRuleProfile background = profile(
                catalog, "character.background", request.backgroundKey());
        LevelOneRuleProfile selectedClass = profile(
                catalog, "character.class", request.classKey());
        requireAbilityMethod(catalog, request.classKey());

        LevelOneRuleProfile subrace = null;
        if (race.subraceRequired()) {
            requireDefinition(definitions, "character.subrace", request.subraceKey());
            requireSubraceParent(catalog, request.subraceKey(), request.raceKey());
            subrace = profile(catalog, "character.subrace", request.subraceKey());
        } else if (request.subraceKey() != null) {
            throw invalid("INVALID_SUBRACE");
        }

        Map<String, Integer> baseScores = validateStandardArray(request.baseAbilityScores());
        Map<String, Integer> finalScores = new LinkedHashMap<>(baseScores);
        applyBonuses(finalScores, race.abilityBonuses());
        if (subrace != null) applyBonuses(finalScores, subrace.abilityBonuses());
        List<String> abilityChoices = canonicalSelection(
                request.abilityBonusChoices(), "ability.");
        LevelOneRuleProfile.Choice bonusChoice = race.abilityBonusChoice();
        validateChoice(abilityChoices, bonusChoice, "ability.");
        for (String ability : abilityChoices) {
            finalScores.compute(ability, (ignored, value) -> value == null ? null : value + 1);
        }
        if (finalScores.values().stream().anyMatch(value -> value > 20)) {
            throw invalid("ABILITY_SCORE_OUT_OF_RANGE");
        }

        List<LevelOneRuleProfile> profiles = new ArrayList<>();
        profiles.add(race);
        if (subrace != null) profiles.add(subrace);
        profiles.add(background);
        profiles.add(selectedClass);

        List<String> skills = validateSelections(
                request.skillChoices(), profiles, SelectionType.SKILL);
        List<String> languages = validateSelections(
                request.languageChoices(), profiles, SelectionType.LANGUAGE);
        List<String> tools = validateSelections(
                request.toolChoices(), profiles, SelectionType.TOOL);
        List<String> starts = validateSelections(
                request.startingOptionChoices(), profiles, SelectionType.STARTING_OPTION);
        List<String> saves = canonicalFixed(profiles, SelectionType.SAVE);

        Integer hitPointBase = selectedClass.hitPointBase();
        if (hitPointBase == null) throw invalid("MALFORMED_FROZEN_CATALOG");
        int constitutionModifier = Math.floorDiv(finalScores.get("ability.constitution") - 10, 2);
        int maximumHitPoints = Math.max(1, hitPointBase + constitutionModifier);
        LevelAdvancementRules.InitialResources initial = LevelAdvancementRules.initialResources(
                catalog, request.classKey(), finalScores.get("ability.constitution"),
                finalScores.get("ability.charisma"));
        ClassFeatureRules.Transition featureTransition;
        try {
            featureTransition = classFeatures.transition(catalog,
                    request.classKey(), 0, 1, null, request.classSubclassKey());
        } catch (ClassFeatureRules.RuleException exception) {
            throw invalid(exception.code());
        }

        String previewDigest = previewDigest(request.campaignKey(), name, request.raceKey(),
                request.subraceKey(), request.backgroundKey(), request.classKey(),
                request.classSubclassKey(), baseScores, abilityChoices, skills, languages,
                tools, starts, expectedEventTail, contentSha256);
        return new Prepared(name, request.raceKey(), request.subraceKey(),
                request.backgroundKey(), request.classKey(), baseScores, finalScores,
                abilityChoices, skills, saves, languages, tools, starts,
                maximumHitPoints, initial.hitDieSides(), initial.resources().stream()
                        .map(value -> new InitialResource(value.resourceKey(), value.currentValue(),
                                value.maximumValue(), value.unlimited()))
                        .toList(), featureTransition, expectedEventTail, previewDigest);
    }

    private static Map<String, ModuleCatalog.CatalogDefinition> definitions(ModuleCatalog catalog) {
        Map<String, ModuleCatalog.CatalogDefinition> result = new HashMap<>();
        for (ModuleCatalog.CatalogDefinition definition : catalog.catalogDefinitions()) {
            String identity = definition.definitionType() + '\0' + definition.definitionKey();
            if (result.putIfAbsent(identity, definition) != null) {
                throw invalid("MALFORMED_FROZEN_CATALOG");
            }
        }
        return result;
    }

    private static void requireDefinition(Map<String, ModuleCatalog.CatalogDefinition> definitions,
            String type, String key) {
        if (!stableKey(key) || !definitions.containsKey(type + '\0' + key)) {
            throw invalid("UNKNOWN_CATALOG_KEY");
        }
    }

    private static LevelOneRuleProfile profile(ModuleCatalog catalog, String type, String key) {
        List<ModuleCatalog.CatalogAttribute> values = catalog.catalogAttributes().stream()
                .filter(value -> type.equals(value.definitionType())
                        && key.equals(value.definitionKey())
                        && "creation.level_one_profile".equals(value.attributeKey()))
                .toList();
        if (values.size() != 1
                || !(values.getFirst().value() instanceof ModuleCatalog.TextValue value)) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        try {
            return LevelOneRuleProfile.parse(value.value());
        } catch (IllegalArgumentException exception) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
    }

    private static void requireAbilityMethod(ModuleCatalog catalog, String classKey) {
        List<ModuleCatalog.CatalogAttribute> values = catalog.catalogAttributes().stream()
                .filter(value -> "character.class".equals(value.definitionType())
                        && classKey.equals(value.definitionKey())
                        && "creation.ability_method".equals(value.attributeKey()))
                .toList();
        if (values.size() != 1
                || !(values.getFirst().value() instanceof ModuleCatalog.IdentifierValue value)
                || !ABILITY_METHOD.equals(value.value())) {
            throw invalid("UNSUPPORTED_ABILITY_METHOD");
        }
    }

    private static void requireSubraceParent(
            ModuleCatalog catalog, String subraceKey, String raceKey) {
        long matches = catalog.catalogRelations().stream()
                .filter(value -> "character.subrace".equals(value.sourceType())
                        && subraceKey.equals(value.sourceKey())
                        && "subrace.parent_race".equals(value.relationType())
                        && "character.race".equals(value.targetType())
                        && raceKey.equals(value.targetKey()))
                .count();
        if (matches != 1) throw invalid("INVALID_SUBRACE");
    }

    private static Map<String, Integer> validateStandardArray(Map<String, Integer> input) {
        if (input == null || input.size() != ABILITIES.size() || !input.keySet().equals(Set.copyOf(ABILITIES))) {
            throw invalid("INVALID_ABILITY_ALLOCATION");
        }
        List<Integer> values = new ArrayList<>(input.values());
        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("INVALID_ABILITY_ALLOCATION");
        }
        Collections.sort(values);
        if (!STANDARD_ARRAY.equals(values)) throw invalid("INVALID_ABILITY_ALLOCATION");
        Map<String, Integer> result = new LinkedHashMap<>();
        ABILITIES.forEach(key -> result.put(key, input.get(key)));
        return Map.copyOf(result);
    }

    private static void applyBonuses(Map<String, Integer> target, Map<String, Integer> bonuses) {
        bonuses.forEach((key, bonus) -> {
            String ability = "ability." + key;
            if (!target.containsKey(ability)) throw invalid("MALFORMED_FROZEN_CATALOG");
            target.put(ability, target.get(ability) + bonus);
        });
    }

    private static List<String> validateSelections(List<String> requested,
            List<LevelOneRuleProfile> profiles, SelectionType type) {
        List<String> fixed = canonicalFixed(profiles, type);
        List<LevelOneRuleProfile.Choice> choices = profiles.stream()
                .map(profile -> choice(profile, type))
                .filter(java.util.Objects::nonNull)
                .toList();
        int expected = choices.stream().mapToInt(LevelOneRuleProfile.Choice::count).sum();
        String prefix = type.prefix;
        List<String> selected = canonicalSelection(requested, prefix);
        if (selected.size() != expected) throw invalid("INVALID_SELECTION_COUNT");
        Set<String> candidates = new HashSet<>();
        choices.forEach(value -> value.candidates().forEach(
                candidate -> candidates.add(qualify(prefix, candidate))));
        if (!candidates.containsAll(selected)) throw invalid("UNKNOWN_CATALOG_KEY");
        if (!assignable(selected, choices, prefix, 0, new int[choices.size()])) {
            throw invalid("INVALID_SELECTION_COUNT");
        }
        LinkedHashSet<String> combined = new LinkedHashSet<>(fixed);
        if (!combined.addAll(selected)) throw invalid("DUPLICATE_SELECTION");
        return combined.stream().sorted().toList();
    }

    private static List<String> canonicalFixed(
            List<LevelOneRuleProfile> profiles, SelectionType type) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (LevelOneRuleProfile profile : profiles) {
            for (String local : fixed(profile, type)) {
                if (!values.add(qualify(type.prefix, local))) throw invalid("DUPLICATE_SELECTION");
            }
        }
        return values.stream().sorted().toList();
    }

    private static boolean assignable(List<String> selected,
            List<LevelOneRuleProfile.Choice> choices, String prefix,
            int selectedIndex, int[] assigned) {
        if (selectedIndex == selected.size()) {
            for (int index = 0; index < choices.size(); index++) {
                if (assigned[index] != choices.get(index).count()) return false;
            }
            return true;
        }
        String value = selected.get(selectedIndex);
        for (int index = 0; index < choices.size(); index++) {
            LevelOneRuleProfile.Choice choice = choices.get(index);
            if (assigned[index] >= choice.count()) continue;
            boolean candidate = choice.candidates().stream()
                    .map(key -> qualify(prefix, key)).anyMatch(value::equals);
            if (!candidate) continue;
            assigned[index]++;
            if (assignable(selected, choices, prefix, selectedIndex + 1, assigned)) return true;
            assigned[index]--;
        }
        return false;
    }

    private static Set<String> fixed(LevelOneRuleProfile profile, SelectionType type) {
        return switch (type) {
            case SKILL -> profile.skills();
            case SAVE -> profile.saves();
            case LANGUAGE -> profile.languages();
            case TOOL -> profile.tools();
            case STARTING_OPTION -> Set.of();
        };
    }

    private static LevelOneRuleProfile.Choice choice(
            LevelOneRuleProfile profile, SelectionType type) {
        return switch (type) {
            case SKILL -> profile.skillChoice();
            case SAVE -> null;
            case LANGUAGE -> profile.languageChoice();
            case TOOL -> profile.toolChoice();
            case STARTING_OPTION -> profile.startingChoice();
        };
    }

    private static List<String> canonicalSelection(List<String> input, String prefix) {
        if (input == null) input = List.of();
        Set<String> unique = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : input) {
            if (!stableKey(value) || !value.startsWith(prefix) || !unique.add(value)) {
                throw invalid("DUPLICATE_OR_MALFORMED_SELECTION");
            }
            result.add(value);
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static void validateChoice(
            List<String> selected, LevelOneRuleProfile.Choice choice, String prefix) {
        if (choice == null) {
            if (!selected.isEmpty()) throw invalid("INVALID_SELECTION_COUNT");
            return;
        }
        Set<String> candidates = new HashSet<>();
        choice.candidates().forEach(value -> candidates.add(qualify(prefix, value)));
        if (selected.size() != choice.count() || !candidates.containsAll(selected)) {
            throw invalid("INVALID_SELECTION_COUNT");
        }
    }

    private static String previewDigest(Object... values) {
        StringBuilder canonical = new StringBuilder("DND_TOOL_SE_LEVEL_ONE_PREVIEW_V1\n");
        for (Object value : values) appendCanonical(canonical, value);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void appendCanonical(StringBuilder target, Object value) {
        if (value instanceof Map<?, ?> map) {
            map.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .forEach(entry -> target.append(entry.getKey()).append('=')
                            .append(entry.getValue()).append(','));
        } else if (value instanceof List<?> list) {
            list.forEach(item -> target.append(item).append(','));
        } else {
            target.append(value == null ? "-" : value);
        }
        target.append('\n');
    }

    private static String qualify(String prefix, String key) {
        return key.startsWith(prefix) ? key : prefix + key;
    }

    private static boolean stableKey(String value) {
        return value != null && value.length() <= 128
                && value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+");
    }

    private static RuleException invalid(String code) { return new RuleException(code); }

    private enum SelectionType {
        SKILL("skill."), SAVE("save."), LANGUAGE("language."),
        TOOL("tool."), STARTING_OPTION("starting.");
        private final String prefix;
        SelectionType(String prefix) { this.prefix = prefix; }
    }

    public record Request(
            String campaignKey,
            String characterName,
            String raceKey,
            String subraceKey,
            String backgroundKey,
            String classKey,
            String classSubclassKey,
            Map<String, Integer> baseAbilityScores,
            List<String> abilityBonusChoices,
            List<String> skillChoices,
            List<String> languageChoices,
            List<String> toolChoices,
            List<String> startingOptionChoices) {
        public Request {
            baseAbilityScores = baseAbilityScores == null ? null : Map.copyOf(baseAbilityScores);
            abilityBonusChoices = copy(abilityBonusChoices);
            skillChoices = copy(skillChoices);
            languageChoices = copy(languageChoices);
            toolChoices = copy(toolChoices);
            startingOptionChoices = copy(startingOptionChoices);
        }

        private static List<String> copy(List<String> value) {
            return value == null ? List.of() : List.copyOf(value);
        }
    }

    public record Prepared(
            String characterName,
            String raceKey,
            String subraceKey,
            String backgroundKey,
            String classKey,
            Map<String, Integer> baseAbilityScores,
            Map<String, Integer> finalAbilityScores,
            List<String> abilityBonusChoices,
            List<String> skills,
            List<String> saves,
            List<String> languages,
            List<String> tools,
            List<String> startingOptions,
            int maximumHitPoints,
            int hitDieSides,
            List<InitialResource> initialResources,
            ClassFeatureRules.Transition featureTransition,
            long expectedEventTail,
            String previewDigestSha256) {
        public Prepared {
            baseAbilityScores = Map.copyOf(baseAbilityScores);
            finalAbilityScores = Map.copyOf(finalAbilityScores);
            abilityBonusChoices = List.copyOf(abilityBonusChoices);
            skills = List.copyOf(skills);
            saves = List.copyOf(saves);
            languages = List.copyOf(languages);
            tools = List.copyOf(tools);
            startingOptions = List.copyOf(startingOptions);
            initialResources = List.copyOf(initialResources);
        }
    }

    public record InitialResource(String resourceKey, long currentValue, long maximumValue,
            boolean unlimited) {
    }

    public static final class RuleException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String code;
        RuleException(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
}
