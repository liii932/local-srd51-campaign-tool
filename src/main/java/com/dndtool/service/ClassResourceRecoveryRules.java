package com.dndtool.service;

import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.persistence.LevelAdvancementRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed, server-owned short/long-rest recovery for canonical-v2 class resources. */
public final class ClassResourceRecoveryRules {
    public static final String SHORT_REST = "SHORT_REST";
    public static final String LONG_REST = "LONG_REST";
    public static final String EFFECT_TYPE = "RESOURCE_CURRENT_SET_TO_MAXIMUM";
    private static final Set<String> TRIGGERS = Set.of(SHORT_REST, LONG_REST);

    public Prepared prepare(ModuleCatalog catalog, String classKey, int classLevel,
            String trigger, List<LevelAdvancementRepository.ResourceState> resourceStates) {
        if (catalog == null || !stableKey(classKey) || classLevel < 1 || classLevel > 20
                || !TRIGGERS.contains(trigger) || resourceStates == null) {
            throw new RuleException("INVALID_REQUEST");
        }
        requireDefinition(catalog, "character.class", classKey);
        Map<String, LevelAdvancementRepository.ResourceState> states = states(resourceStates);
        List<String> keys = catalog.catalogRelations().stream()
                .filter(row -> "character.resource".equals(row.sourceType())
                        && "resource.owner".equals(row.relationType())
                        && "character.class".equals(row.targetType())
                        && classKey.equals(row.targetKey()))
                .map(ModuleCatalog.CatalogRelation::sourceKey).sorted().toList();
        if (keys.size() != new HashSet<>(keys).size()) malformed();
        Set<String> ownedKeys = Set.copyOf(keys);
        for (String stateKey : states.keySet()) {
            if (!coreResource(stateKey) && !ownedKeys.contains(stateKey)) authoritative();
        }

        List<RecoveryEffect> effects = new ArrayList<>();
        for (String key : keys) {
            requireDefinition(catalog, "character.resource", key);
            String execution = identifier(catalog, key, "resource.execution_mode");
            LevelAdvancementRepository.ResourceState state = states.get(key);
            if ("BLOCKED".equals(execution)) {
                if (state != null) authoritative();
                continue;
            }
            if (!"AUTOMATIC".equals(execution)) malformed();
            AdvancementValueProfile.ResolvedValue expected = maximumProfile(catalog, key)
                    .atLevel(classLevel, 0);
            validateState(key, state, expected);
            if (expected.maximum() == 0 || expected.unlimited()
                    || !recovers(recoveryAtLevel(catalog, key, classLevel), trigger)
                    || state.currentValue() == state.maximumValue()) continue;
            effects.add(new RecoveryEffect(EFFECT_TYPE, key, state.currentValue(),
                    state.maximumValue(), state.maximumValue()));
        }
        effects.sort(Comparator.comparing(RecoveryEffect::resourceKey));
        return new Prepared(trigger, effects);
    }

    private static Map<String, LevelAdvancementRepository.ResourceState> states(
            List<LevelAdvancementRepository.ResourceState> values) {
        Map<String, LevelAdvancementRepository.ResourceState> result = new HashMap<>();
        for (LevelAdvancementRepository.ResourceState value : values) {
            if (value == null || !stableKey(value.resourceKey())
                    || result.putIfAbsent(value.resourceKey(), value) != null
                    || value.unlimited() && (value.currentValue() != 0
                            || value.maximumValue() != 0)
                    || !value.unlimited() && (value.maximumValue() <= 0
                            || value.currentValue() < 0
                            || value.currentValue() > value.maximumValue())) {
                authoritative();
            }
        }
        return result;
    }

    private static AdvancementValueProfile maximumProfile(ModuleCatalog catalog, String key) {
        List<ModuleCatalog.CatalogAttribute> rows = attributes(
                catalog, key, "resource.maximum_profile");
        if (rows.size() != 1
                || !(rows.getFirst().value() instanceof ModuleCatalog.TextValue text)) {
            malformed();
        }
        try {
            return AdvancementValueProfile.parse(
                    ((ModuleCatalog.TextValue) rows.getFirst().value()).value());
        } catch (IllegalArgumentException exception) {
            malformed();
            throw new AssertionError();
        }
    }

    private static String recoveryAtLevel(ModuleCatalog catalog, String key, int level) {
        List<ModuleCatalog.CatalogAttribute> rows = attributes(
                catalog, key, "resource.recovery_profile");
        if (rows.size() != 1
                || !(rows.getFirst().value() instanceof ModuleCatalog.TextValue text)) {
            malformed();
        }
        int expected = 1;
        String result = null;
        for (String band : ((ModuleCatalog.TextValue) rows.getFirst().value()).value()
                .split(",", -1)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "([1-9]|1[0-9]|20)-([1-9]|1[0-9]|20):(SHORT_REST|LONG_REST)")
                    .matcher(band);
            if (!matcher.matches()) malformed();
            int first = Integer.parseInt(matcher.group(1));
            int last = Integer.parseInt(matcher.group(2));
            if (first != expected || last < first) malformed();
            if (level >= first && level <= last) result = matcher.group(3);
            expected = last + 1;
        }
        if (expected != 21 || result == null) malformed();
        return result;
    }

    private static boolean recovers(String minimumRest, String completedRest) {
        return minimumRest.equals(completedRest)
                || SHORT_REST.equals(minimumRest) && LONG_REST.equals(completedRest);
    }

    private static String identifier(ModuleCatalog catalog, String key, String attribute) {
        List<ModuleCatalog.CatalogAttribute> rows = attributes(catalog, key, attribute);
        if (rows.size() != 1 || !(rows.getFirst().value()
                instanceof ModuleCatalog.IdentifierValue identifier)) malformed();
        return ((ModuleCatalog.IdentifierValue) rows.getFirst().value()).value();
    }

    private static List<ModuleCatalog.CatalogAttribute> attributes(
            ModuleCatalog catalog, String key, String attribute) {
        return catalog.catalogAttributes().stream()
                .filter(row -> "character.resource".equals(row.definitionType())
                        && key.equals(row.definitionKey())
                        && attribute.equals(row.attributeKey())).toList();
    }

    private static void validateState(String key,
            LevelAdvancementRepository.ResourceState state,
            AdvancementValueProfile.ResolvedValue expected) {
        if (expected.maximum() == 0 && !expected.unlimited()) {
            if (state != null) authoritative();
            return;
        }
        if (state == null || state.unlimited() != expected.unlimited()
                || state.maximumValue() != expected.maximum()
                || state.currentValue() < 0
                || !state.unlimited() && state.currentValue() > state.maximumValue()) {
            authoritative();
        }
    }

    private static void requireDefinition(ModuleCatalog catalog, String type, String key) {
        if (catalog.catalogDefinitions().stream().filter(row -> type.equals(row.definitionType())
                && key.equals(row.definitionKey())).count() != 1) malformed();
    }

    private static boolean stableKey(String value) {
        return value != null && value.length() <= 128
                && value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+");
    }

    private static boolean coreResource(String key) {
        return "resource.hit_points".equals(key) || key.startsWith("resource.hit_dice.");
    }

    private static void malformed() {
        throw new RuleException("MALFORMED_FROZEN_CATALOG");
    }

    private static void authoritative() {
        throw new RuleException("AUTHORITATIVE_STATE_MISMATCH");
    }

    public record RecoveryEffect(String effectType, String resourceKey,
            long previousCurrentValue, long newCurrentValue, long maximumValue) {
    }

    public record Prepared(String recoveryTrigger, List<RecoveryEffect> effects) {
        public Prepared {
            effects = List.copyOf(effects);
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
