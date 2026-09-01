package com.dndtool.service;

import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.persistence.LevelAdvancementRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-owned level, hit-point, hit-die, proficiency and resource derivation. */
public final class LevelAdvancementRules {
    public static final String FIXED_AVERAGE = "FIXED_AVERAGE";
    public static final String SERVER_ROLL = "SERVER_ROLL";
    private static final Set<String> HP_CHOICES = Set.of(FIXED_AVERAGE, SERVER_ROLL);
    private final CharacterAdvancementChoiceRules advancementChoices =
            new CharacterAdvancementChoiceRules();
    private final ClassFeatureRules classFeatures = new ClassFeatureRules();

    public Prepared prepare(ModuleCatalog catalog, Request request,
            LevelAdvancementRepository.PreviewContext context, String contentSha256) {
        if (catalog == null || request == null || context == null
                || !stableUuid(request.characterKey())
                || !request.characterKey().equals(context.characterKey())
                || !HP_CHOICES.contains(request.hpChoiceAlgorithm())
                || contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) {
            throw invalid("INVALID_REQUEST");
        }
        boolean extended = request.targetClassKey() != null;
        if (!extended && context.totalLevel() != context.classLevel()) {
            throw invalid("UNSUPPORTED_MULTICLASS_STATE");
        }
        if (context.totalLevel() < 1 || context.totalLevel() >= 20
                || request.targetLevel() != context.totalLevel() + 1) {
            throw invalid("INVALID_LEVEL_TRANSITION");
        }
        if (context.expectedEventTail() < 0 || context.expectedRowVersion() < 0
                || context.constitutionScore() < 1 || context.constitutionScore() > 30
                || context.charismaScore() < 1 || context.charismaScore() > 30) {
            throw invalid("AUTHORITATIVE_STATE_MISMATCH");
        }

        CharacterAdvancementChoiceRules.Prepared advancementChoice = null;
        ClassFeatureRules.Transition featureTransition = null;
        String targetClassKey = context.classKey();
        int previousClassLevel = context.classLevel();
        if (extended) {
            Map<String, Integer> levels = new HashMap<>();
            for (LevelAdvancementRepository.ClassLevel level : context.classLevels()) {
                if (level == null || levels.putIfAbsent(level.classKey(), level.classLevel()) != null) {
                    throw invalid("AUTHORITATIVE_STATE_MISMATCH");
                }
            }
            advancementChoice = advancementChoices.prepare(catalog, levels,
                    context.abilityScores(), context.acquiredFeats(),
                    context.acquiredProficiencies(),
                    new CharacterAdvancementChoiceRules.Request(request.targetClassKey(),
                            request.abilityIncreases(), request.featKey(),
                            request.proficiencyChoices()));
            targetClassKey = request.targetClassKey();
            previousClassLevel = levels.getOrDefault(targetClassKey, 0);
            String existingSubclass = context.subclassesByClass().get(targetClassKey);
            try {
                featureTransition = classFeatures.transition(catalog, targetClassKey,
                        previousClassLevel, previousClassLevel + 1, existingSubclass,
                        request.subclassKey());
            } catch (ClassFeatureRules.RuleException exception) {
                throw invalid(exception.code());
            }
            if (featureTransition.featureUnlocks().stream().map(
                    ClassFeatureRules.FeatureRule::featureKey)
                    .anyMatch(context.acquiredFeatures()::contains)) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
        }

        requireDefinition(catalog, "character.class", targetClassKey);
        int hitDieSides = integerAttribute(
                catalog, "character.class", targetClassKey, "class.hit_die_sides");
        if (!Set.of(6, 8, 10, 12).contains(hitDieSides)) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        AdvancementValueProfile proficiency = profile(catalog, "character.class",
                targetClassKey, "class.proficiency_bonus_profile");
        int previousProficiency = exactInt(
                proficiency.atLevel(context.totalLevel(), 0).maximum());
        int newProficiency = exactInt(
                proficiency.atLevel(request.targetLevel(), 0).maximum());
        if (previousProficiency < 2 || newProficiency > 6) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }

        Map<String, LevelAdvancementRepository.ResourceState> existing = resources(context);
        LevelAdvancementRepository.ResourceState hitPoints = existing.get("resource.hit_points");
        requireFiniteState(hitPoints, "resource.hit_points");
        if (hitPoints.maximumValue() > 65_535) {
            throw invalid("AUTHORITATIVE_STATE_MISMATCH");
        }

        String hitDiceKey = "resource.hit_dice.d" + hitDieSides;
        requireDefinition(catalog, "character.resource", hitDiceKey);
        LevelAdvancementRepository.ResourceState hitDice = existing.get(hitDiceKey);
        int expectedHitDice = extended
                ? hitDiceCount(catalog, context.classLevels(), hitDieSides)
                : context.totalLevel();
        if (expectedHitDice == 0) {
            if (hitDice != null) throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            hitDice = new LevelAdvancementRepository.ResourceState(hitDiceKey, 0, 0, false);
        } else {
            requireFiniteState(hitDice, hitDiceKey);
            if (hitDice.maximumValue() != expectedHitDice) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
        }

        int currentCharisma = advancementChoice == null
                ? context.charismaScore()
                : advancementChoice.abilityScores().get("ability.charisma");
        int charismaModifier = modifier(currentCharisma);
        int previousCharismaModifier = modifier(context.charismaScore());
        List<LevelAdvancementRepository.ResourceChange> changes = new ArrayList<>();
        changes.add(new LevelAdvancementRepository.ResourceChange(hitDiceKey,
                expectedHitDice == 0 ? null : hitDice,
                new LevelAdvancementRepository.ResourceState(hitDiceKey,
                        hitDice.currentValue() + 1, hitDice.maximumValue() + 1, false)));
        changes.addAll(classResourceChanges(catalog, targetClassKey, previousClassLevel,
                previousClassLevel + 1, previousCharismaModifier, charismaModifier, existing));
        if (extended && previousCharismaModifier != charismaModifier) {
            List<LevelAdvancementRepository.ClassLevel> otherClasses = new ArrayList<>(
                    context.classLevels());
            otherClasses.sort(Comparator.comparing(
                    LevelAdvancementRepository.ClassLevel::classKey));
            for (LevelAdvancementRepository.ClassLevel level : otherClasses) {
                if (targetClassKey.equals(level.classKey())) continue;
                changes.addAll(classResourceChanges(catalog, level.classKey(),
                        level.classLevel(), level.classLevel(), previousCharismaModifier,
                        charismaModifier, existing));
            }
        }
        if (changes.size() != changes.stream()
                .map(LevelAdvancementRepository.ResourceChange::resourceKey)
                .collect(java.util.stream.Collectors.toSet()).size()) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        changes.sort(Comparator.comparing(LevelAdvancementRepository.ResourceChange::resourceKey));

        int previousConstitutionModifier = modifier(context.constitutionScore());
        int nextConstitutionScore = advancementChoice == null ? context.constitutionScore()
                : advancementChoice.abilityScores().get("ability.constitution");
        int constitutionModifier = modifier(nextConstitutionScore);
        int retroactiveConstitutionIncrease = context.totalLevel()
                * (constitutionModifier - previousConstitutionModifier);
        int minimumHitPoints;
        int maximumHitPoints;
        if (FIXED_AVERAGE.equals(request.hpChoiceAlgorithm())) {
            minimumHitPoints = Math.max(1, hitDieSides / 2 + 1 + constitutionModifier)
                    + retroactiveConstitutionIncrease;
            maximumHitPoints = minimumHitPoints;
        } else {
            minimumHitPoints = Math.max(1, 1 + constitutionModifier)
                    + retroactiveConstitutionIncrease;
            maximumHitPoints = Math.max(1, hitDieSides + constitutionModifier)
                    + retroactiveConstitutionIncrease;
        }
        if ((long) hitPoints.maximumValue() + maximumHitPoints > 65_535) {
            throw invalid("HIT_POINT_LIMIT_EXCEEDED");
        }

        String digest = previewDigest(request, context, hitDieSides, constitutionModifier,
                previousProficiency, newProficiency, minimumHitPoints, maximumHitPoints,
                changes, contentSha256, advancementChoice, featureTransition);
        return new Prepared(context, request.hpChoiceAlgorithm(), request.targetLevel(),
                hitDieSides, constitutionModifier, previousProficiency, newProficiency,
                minimumHitPoints, maximumHitPoints, changes, digest, advancementChoice,
                featureTransition, retroactiveConstitutionIncrease);
    }

    static InitialResources initialResources(ModuleCatalog catalog, String classKey,
            int constitutionScore, int charismaScore) {
        requireDefinition(catalog, "character.class", classKey);
        int hitDieSides = integerAttribute(
                catalog, "character.class", classKey, "class.hit_die_sides");
        if (!Set.of(6, 8, 10, 12).contains(hitDieSides)) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        requireDefinition(catalog, "character.resource", "resource.hit_dice.d" + hitDieSides);
        AdvancementValueProfile proficiency = profile(catalog, "character.class", classKey,
                "class.proficiency_bonus_profile");
        if (proficiency.atLevel(1, 0).maximum() != 2) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        Map<String, LevelAdvancementRepository.ResourceState> absent = Map.of();
        List<LevelAdvancementRepository.ResourceChange> classResources = classResourceChanges(
                catalog, classKey, 0, 1, modifier(charismaScore), modifier(charismaScore), absent);
        List<LevelAdvancementRepository.ResourceState> states = new ArrayList<>();
        states.add(new LevelAdvancementRepository.ResourceState(
                "resource.hit_dice.d" + hitDieSides, 1, 1, false));
        classResources.forEach(change -> states.add(change.next()));
        states.sort(Comparator.comparing(LevelAdvancementRepository.ResourceState::resourceKey));
        return new InitialResources(hitDieSides, modifier(constitutionScore), states);
    }

    private static List<LevelAdvancementRepository.ResourceChange> classResourceChanges(
            ModuleCatalog catalog, String classKey, int previousLevel, int newLevel,
            int previousCharismaModifier, int newCharismaModifier,
            Map<String, LevelAdvancementRepository.ResourceState> existing) {
        List<String> resourceKeys = catalog.catalogRelations().stream()
                .filter(row -> "character.resource".equals(row.sourceType())
                        && "resource.owner".equals(row.relationType())
                        && "character.class".equals(row.targetType())
                        && classKey.equals(row.targetKey()))
                .map(ModuleCatalog.CatalogRelation::sourceKey)
                .sorted().toList();
        if (resourceKeys.size() != new HashSet<>(resourceKeys).size()) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        List<LevelAdvancementRepository.ResourceChange> changes = new ArrayList<>();
        for (String resourceKey : resourceKeys) {
            requireDefinition(catalog, "character.resource", resourceKey);
            String executionMode = identifierAttribute(catalog, "character.resource",
                    resourceKey, "resource.execution_mode");
            if ("BLOCKED".equals(executionMode)) {
                if (existing.containsKey(resourceKey)) {
                    throw invalid("AUTHORITATIVE_STATE_MISMATCH");
                }
                continue;
            }
            if (!"AUTOMATIC".equals(executionMode)) {
                throw invalid("MALFORMED_FROZEN_CATALOG");
            }
            AdvancementValueProfile maximums = profile(catalog, "character.resource", resourceKey,
                    "resource.maximum_profile");
            AdvancementValueProfile.ResolvedValue previous = previousLevel == 0
                    ? new AdvancementValueProfile.ResolvedValue(0, false)
                    : maximums.atLevel(previousLevel, previousCharismaModifier);
            AdvancementValueProfile.ResolvedValue next = maximums.atLevel(
                    newLevel, newCharismaModifier);
            LevelAdvancementRepository.ResourceState state = existing.get(resourceKey);
            validatePreviousResource(resourceKey, state, previous);
            if (same(previous, next)) continue;
            long spent = state == null || state.unlimited()
                    ? 0 : state.maximumValue() - state.currentValue();
            LevelAdvancementRepository.ResourceState nextState = next.unlimited()
                    ? new LevelAdvancementRepository.ResourceState(resourceKey, 0, 0, true)
                    : new LevelAdvancementRepository.ResourceState(resourceKey,
                            Math.max(0, next.maximum() - spent), next.maximum(), false);
            changes.add(new LevelAdvancementRepository.ResourceChange(
                    resourceKey, state, nextState));
        }
        return changes;
    }

    private static boolean same(AdvancementValueProfile.ResolvedValue left,
            AdvancementValueProfile.ResolvedValue right) {
        return left.maximum() == right.maximum() && left.unlimited() == right.unlimited();
    }

    private static void validatePreviousResource(String key,
            LevelAdvancementRepository.ResourceState state,
            AdvancementValueProfile.ResolvedValue expected) {
        if (expected.maximum() == 0 && !expected.unlimited()) {
            if (state != null) throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            return;
        }
        if (state == null || state.unlimited() != expected.unlimited()
                || state.maximumValue() != expected.maximum()
                || state.currentValue() < 0
                || !state.unlimited() && state.currentValue() > state.maximumValue()
                || state.unlimited() && state.currentValue() != 0) {
            throw invalid("AUTHORITATIVE_STATE_MISMATCH");
        }
    }

    private static Map<String, LevelAdvancementRepository.ResourceState> resources(
            LevelAdvancementRepository.PreviewContext context) {
        Map<String, LevelAdvancementRepository.ResourceState> result = new HashMap<>();
        for (LevelAdvancementRepository.ResourceState state : context.resources()) {
            if (state == null || !stableKey(state.resourceKey())
                    || result.putIfAbsent(state.resourceKey(), state) != null) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
        }
        return result;
    }

    private static void requireFiniteState(
            LevelAdvancementRepository.ResourceState state, String key) {
        if (state == null || !key.equals(state.resourceKey()) || state.unlimited()
                || state.maximumValue() <= 0 || state.currentValue() < 0
                || state.currentValue() > state.maximumValue()) {
            throw invalid("AUTHORITATIVE_STATE_MISMATCH");
        }
    }

    private static LevelAdvancementRepository.ResourceChange change(
            LevelAdvancementRepository.ResourceState previous, long current, long maximum,
            boolean unlimited) {
        return new LevelAdvancementRepository.ResourceChange(previous.resourceKey(), previous,
                new LevelAdvancementRepository.ResourceState(
                        previous.resourceKey(), current, maximum, unlimited));
    }

    private static AdvancementValueProfile profile(ModuleCatalog catalog, String type,
            String definitionKey, String attributeKey) {
        List<ModuleCatalog.CatalogAttribute> values = catalog.catalogAttributes().stream()
                .filter(row -> type.equals(row.definitionType())
                        && definitionKey.equals(row.definitionKey())
                        && attributeKey.equals(row.attributeKey()))
                .toList();
        if (values.size() != 1
                || !(values.getFirst().value() instanceof ModuleCatalog.TextValue text)) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        try {
            return AdvancementValueProfile.parse(text.value());
        } catch (IllegalArgumentException exception) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
    }

    private static int integerAttribute(ModuleCatalog catalog, String type,
            String definitionKey, String attributeKey) {
        List<ModuleCatalog.CatalogAttribute> values = catalog.catalogAttributes().stream()
                .filter(row -> type.equals(row.definitionType())
                        && definitionKey.equals(row.definitionKey())
                        && attributeKey.equals(row.attributeKey()))
                .toList();
        if (values.size() != 1
                || !(values.getFirst().value() instanceof ModuleCatalog.IntegerValue integer)
                || integer.value() < Integer.MIN_VALUE || integer.value() > Integer.MAX_VALUE) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        return (int) integer.value();
    }

    private static String identifierAttribute(ModuleCatalog catalog, String type,
            String definitionKey, String attributeKey) {
        List<ModuleCatalog.CatalogAttribute> values = catalog.catalogAttributes().stream()
                .filter(row -> type.equals(row.definitionType())
                        && definitionKey.equals(row.definitionKey())
                        && attributeKey.equals(row.attributeKey()))
                .toList();
        if (values.size() != 1
                || !(values.getFirst().value()
                        instanceof ModuleCatalog.IdentifierValue identifier)) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        return identifier.value();
    }

    private static void requireDefinition(ModuleCatalog catalog, String type, String key) {
        if (!stableKey(key) || catalog.catalogDefinitions().stream()
                .filter(row -> type.equals(row.definitionType()) && key.equals(row.definitionKey()))
                .count() != 1) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
    }

    private static int hitDiceCount(ModuleCatalog catalog,
            List<LevelAdvancementRepository.ClassLevel> levels, int sides) {
        int count = 0;
        Set<String> unique = new HashSet<>();
        for (LevelAdvancementRepository.ClassLevel level : levels) {
            if (level == null || !unique.add(level.classKey())
                    || level.classLevel() < 1 || level.classLevel() > 20) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
            int classSides = integerAttribute(catalog, "character.class", level.classKey(),
                    "class.hit_die_sides");
            if (classSides == sides) count += level.classLevel();
        }
        return count;
    }

    private static int modifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    private static int exactInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        return (int) value;
    }

    private static String previewDigest(Request request,
            LevelAdvancementRepository.PreviewContext context, int hitDieSides,
            int constitutionModifier, int previousProficiency, int newProficiency,
            int minimumHitPoints, int maximumHitPoints,
            List<LevelAdvancementRepository.ResourceChange> changes, String hash,
            CharacterAdvancementChoiceRules.Prepared advancementChoice,
            ClassFeatureRules.Transition featureTransition) {
        StringBuilder canonical = new StringBuilder(advancementChoice == null
                ? "DND_TOOL_SE_LEVEL_ADVANCEMENT_PREVIEW_V1\n"
                : "DND_TOOL_SE_LEVEL_ADVANCEMENT_PREVIEW_V2\n");
        append(canonical, context.campaignKey());
        append(canonical, context.characterKey());
        append(canonical, context.expectedEventTail());
        append(canonical, context.expectedRowVersion());
        append(canonical, context.moduleKey());
        append(canonical, context.releaseVersion());
        append(canonical, hash);
        append(canonical, context.classKey());
        append(canonical, context.classLevel());
        append(canonical, context.totalLevel());
        append(canonical, request.targetLevel());
        append(canonical, request.hpChoiceAlgorithm());
        append(canonical, hitDieSides);
        append(canonical, constitutionModifier);
        append(canonical, previousProficiency);
        append(canonical, newProficiency);
        append(canonical, minimumHitPoints);
        append(canonical, maximumHitPoints);
        context.resources().stream()
                .sorted(Comparator.comparing(LevelAdvancementRepository.ResourceState::resourceKey))
                .forEach(state -> appendResource(canonical, state));
        changes.forEach(change -> {
            append(canonical, "change");
            append(canonical, change.resourceKey());
            append(canonical, change.previous() != null);
            if (change.previous() != null) appendResource(canonical, change.previous());
            appendResource(canonical, change.next());
        });
        append(canonical, advancementChoice != null);
        if (advancementChoice != null) {
            advancementChoice.classLevels().forEach((key, value) -> {
                append(canonical, key);
                append(canonical, value);
            });
            advancementChoice.abilityIncreases().forEach((key, value) -> {
                append(canonical, key);
                append(canonical, value);
            });
            append(canonical, advancementChoice.featKey());
            advancementChoice.proficiencyGrants().forEach(value -> append(canonical, value));
            append(canonical, featureTransition.effectiveSubclassKey());
            append(canonical, featureTransition.newlySelectedSubclassKey());
            featureTransition.featureUnlocks().stream()
                    .sorted(Comparator.comparing(ClassFeatureRules.FeatureRule::featureKey))
                    .forEach(feature -> {
                        append(canonical, feature.featureKey());
                        append(canonical, feature.level());
                        append(canonical, feature.executionMode());
                        append(canonical, feature.executionAlgorithm());
                    });
        }
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void append(StringBuilder target, Object value) {
        byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        target.append(bytes.length).append(':').append(value).append('\n');
    }

    private static void appendResource(StringBuilder target,
            LevelAdvancementRepository.ResourceState value) {
        append(target, "resource");
        append(target, value.resourceKey());
        append(target, value.currentValue());
        append(target, value.maximumValue());
        append(target, value.unlimited());
    }

    private static boolean stableKey(String value) {
        return value != null && value.length() <= 128
                && value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+");
    }

    private static boolean stableUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return java.util.UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static RuleException invalid(String code) {
        return new RuleException(code);
    }

    public record Request(String characterKey, int targetLevel, String hpChoiceAlgorithm,
            String targetClassKey, String subclassKey,
            Map<String, Integer> abilityIncreases, String featKey,
            List<String> proficiencyChoices) {
        public Request {
            abilityIncreases = abilityIncreases == null ? Map.of() : Map.copyOf(abilityIncreases);
            proficiencyChoices = proficiencyChoices == null
                    ? List.of() : List.copyOf(proficiencyChoices);
        }

        public Request(String characterKey, int targetLevel, String hpChoiceAlgorithm) {
            this(characterKey, targetLevel, hpChoiceAlgorithm,
                    null, null, Map.of(), null, List.of());
        }
    }

    public record Prepared(
            LevelAdvancementRepository.PreviewContext context,
            String hpChoiceAlgorithm,
            int targetLevel,
            int hitDieSides,
            int constitutionModifier,
            int previousProficiencyBonus,
            int newProficiencyBonus,
            int minimumHitPointIncrease,
            int maximumHitPointIncrease,
            List<LevelAdvancementRepository.ResourceChange> resourceChanges,
            String previewDigestSha256,
            CharacterAdvancementChoiceRules.Prepared advancementChoice,
            ClassFeatureRules.Transition featureTransition,
            int retroactiveConstitutionIncrease) {
        public Prepared {
            resourceChanges = List.copyOf(resourceChanges);
        }
    }

    record InitialResources(int hitDieSides, int constitutionModifier,
            List<LevelAdvancementRepository.ResourceState> resources) {
        InitialResources {
            resources = List.copyOf(resources);
        }
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
