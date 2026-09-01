package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-owned canonical-v2 class, subclass and feature disposition matrix. */
public final class ClassFeatureRules {
    public static final String AUTOMATIC = "AUTOMATIC";
    public static final String DM_ADJUDICATION = "DM_ADJUDICATION";
    public static final String BLOCKED = "BLOCKED";
    private static final Set<String> MODES = Set.of(AUTOMATIC, DM_ADJUDICATION, BLOCKED);
    private static final Set<String> AUTOMATIC_ALGORITHMS = Set.of(
            "AUTOMATIC_RESOURCE_LIFECYCLE_V1");
    private static final Set<String> ADJUDICATION_ALGORITHMS = Set.of(
            "BOUNDED_FEATURE_SELECTION_V1",
            "BOUNDED_SUBCLASS_SELECTION_V1",
            "BOUNDED_DM_ADJUDICATION_V1");
    private static final Set<String> BLOCKED_ALGORITHMS = Set.of(
            "BLOCKED_ISSUE_8_V1",
            "BLOCKED_SPELL_SYSTEM_V1",
            "BLOCKED_DOWNSTREAM_SYSTEM_V1");

    public Matrix inspect(ModuleCatalog catalog) {
        if (catalog == null) throw invalid();
        Map<Identity, ModuleCatalog.CatalogDefinition> definitions = definitions(catalog);
        Map<Identity, ModuleCatalog.CatalogRelation> owners = owners(catalog, definitions);
        List<SubclassRule> subclasses = subclassRules(catalog, definitions);
        List<FeatureRule> features = featureRules(catalog, definitions, owners);
        return new Matrix(subclasses, features);
    }

    /** Resolves one single-class level transition without deriving any rule from client input. */
    public Transition transition(ModuleCatalog catalog, String classKey, int previousLevel,
            int targetLevel, String existingSubclassKey, String requestedSubclassKey) {
        if (!stableKey(classKey) || previousLevel < 0 || previousLevel >= 20
                || targetLevel != previousLevel + 1
                || existingSubclassKey != null && !stableKey(existingSubclassKey)
                || requestedSubclassKey != null && !stableKey(requestedSubclassKey)) {
            throw new RuleException("INVALID_REQUEST");
        }
        Matrix matrix = inspect(catalog);
        if (!matrix.classKeys().contains(classKey)) {
            throw new RuleException("MALFORMED_FROZEN_CATALOG");
        }
        List<SubclassRule> candidates = matrix.subclasses().stream()
                .filter(rule -> classKey.equals(rule.classKey())).toList();
        String selected = resolveSubclass(
                candidates, previousLevel, targetLevel, existingSubclassKey, requestedSubclassKey);
        List<FeatureRule> unlocks = matrix.features().stream()
                .filter(rule -> rule.level() == targetLevel)
                .filter(rule -> "character.class".equals(rule.ownerType())
                        && classKey.equals(rule.ownerKey())
                        || "character.subclass".equals(rule.ownerType())
                        && rule.ownerKey().equals(selected))
                .filter(rule -> !"OPTION".equals(rule.category()))
                .toList();
        return new Transition(selected,
                existingSubclassKey == null && selected != null ? selected : null, unlocks);
    }

    private static String resolveSubclass(List<SubclassRule> candidates, int previousLevel,
            int targetLevel, String existing, String requested) {
        if (existing != null) {
            if (candidates.stream().noneMatch(rule -> existing.equals(rule.subclassKey()))
                    || requested != null && !existing.equals(requested)) {
                throw new RuleException("INVALID_SUBCLASS_SELECTION");
            }
            return existing;
        }
        if (requested != null
                && candidates.stream().noneMatch(rule -> requested.equals(rule.subclassKey()))) {
            throw new RuleException("INVALID_SUBCLASS_SELECTION");
        }
        List<SubclassRule> due = candidates.stream()
                .filter(rule -> previousLevel < rule.selectionLevel()
                        && targetLevel >= rule.selectionLevel()).toList();
        if (due.isEmpty()) {
            if (candidates.stream().anyMatch(rule -> rule.selectionLevel() <= previousLevel)) {
                throw new RuleException("AUTHORITATIVE_STATE_MISMATCH");
            }
            if (requested != null) throw new RuleException("SUBCLASS_SELECTION_NOT_AVAILABLE");
            return null;
        }
        if (requested == null) throw new RuleException("SUBCLASS_SELECTION_REQUIRED");
        if (due.stream().noneMatch(rule -> requested.equals(rule.subclassKey()))) {
            throw new RuleException("INVALID_SUBCLASS_SELECTION");
        }
        return requested;
    }

    private static Map<Identity, ModuleCatalog.CatalogDefinition> definitions(
            ModuleCatalog catalog) {
        Map<Identity, ModuleCatalog.CatalogDefinition> result = new HashMap<>();
        Map<String, Set<Integer>> orders = new HashMap<>();
        for (ModuleCatalog.CatalogDefinition definition : catalog.catalogDefinitions()) {
            if (definition == null || !stableKey(definition.definitionKey())
                    || definition.sortOrder() <= 0) throw invalid();
            Identity identity = new Identity(
                    definition.definitionType(), definition.definitionKey());
            if (result.putIfAbsent(identity, definition) != null
                    || !orders.computeIfAbsent(definition.definitionType(), ignored ->
                                    new HashSet<>())
                            .add(definition.sortOrder())) {
                throw invalid();
            }
        }
        return result;
    }

    private static Map<Identity, ModuleCatalog.CatalogRelation> owners(
            ModuleCatalog catalog,
            Map<Identity, ModuleCatalog.CatalogDefinition> definitions) {
        Map<Identity, ModuleCatalog.CatalogRelation> result = new HashMap<>();
        for (ModuleCatalog.CatalogRelation relation : catalog.catalogRelations()) {
            if (relation == null || !("feature.owner".equals(relation.relationType())
                    || "subclass.parent_class".equals(relation.relationType()))) continue;
            Identity source = new Identity(relation.sourceType(), relation.sourceKey());
            Identity target = new Identity(relation.targetType(), relation.targetKey());
            if (!definitions.containsKey(source) || !definitions.containsKey(target)
                    || result.putIfAbsent(source, relation) != null) {
                throw invalid();
            }
        }
        return result;
    }

    private static List<SubclassRule> subclassRules(ModuleCatalog catalog,
            Map<Identity, ModuleCatalog.CatalogDefinition> definitions) {
        Map<Identity, ModuleCatalog.CatalogRelation> parents = owners(catalog, definitions);
        List<SubclassRule> result = new ArrayList<>();
        for (Identity identity : definitions.keySet()) {
            if (!"character.subclass".equals(identity.type())) continue;
            ModuleCatalog.CatalogRelation parent = parents.get(identity);
            int level = integerAttribute(
                    catalog, identity, "subclass.selection_level");
            if (parent == null || !"character.class".equals(parent.targetType())
                    || level < 1 || level > 3) throw invalid();
            result.add(new SubclassRule(identity.key(), parent.targetKey(), level));
        }
        result.sort(Comparator.comparing(SubclassRule::classKey)
                .thenComparing(SubclassRule::subclassKey));
        return List.copyOf(result);
    }

    private static List<FeatureRule> featureRules(ModuleCatalog catalog,
            Map<Identity, ModuleCatalog.CatalogDefinition> definitions,
            Map<Identity, ModuleCatalog.CatalogRelation> owners) {
        List<FeatureRule> result = new ArrayList<>();
        for (Identity identity : definitions.keySet()) {
            if (!"character.feature".equals(identity.type())) continue;
            ModuleCatalog.CatalogRelation owner = owners.get(identity);
            if (owner == null || !Set.of("character.class", "character.subclass")
                    .contains(owner.targetType())) continue;
            int level = integerAttribute(catalog, identity, "feature.level");
            String category = identifierAttribute(catalog, identity, "catalog.category");
            String mode = identifierAttribute(catalog, identity, "feature.execution_mode");
            String algorithm = identifierAttribute(
                    catalog, identity, "feature.execution_algorithm");
            if (level < 1 || level > 20 || !MODES.contains(mode)
                    || !validPair(mode, algorithm)) throw invalid();
            result.add(new FeatureRule(identity.key(), owner.targetType(), owner.targetKey(),
                    level, category, mode, algorithm));
        }
        result.sort(Comparator.comparing(FeatureRule::ownerKey)
                .thenComparingInt(FeatureRule::level)
                .thenComparing(FeatureRule::featureKey));
        return List.copyOf(result);
    }

    private static boolean validPair(String mode, String algorithm) {
        return switch (mode) {
            case AUTOMATIC -> AUTOMATIC_ALGORITHMS.contains(algorithm);
            case DM_ADJUDICATION -> ADJUDICATION_ALGORITHMS.contains(algorithm);
            case BLOCKED -> BLOCKED_ALGORITHMS.contains(algorithm);
            default -> false;
        };
    }

    private static int integerAttribute(ModuleCatalog catalog, Identity identity, String key) {
        List<ModuleCatalog.CatalogAttribute> rows = attributes(catalog, identity, key);
        if (rows.size() != 1 || !(rows.getFirst().value()
                instanceof ModuleCatalog.IntegerValue integer)
                || integer.value() < Integer.MIN_VALUE || integer.value() > Integer.MAX_VALUE) {
            throw invalid();
        }
        return (int) integer.value();
    }

    private static String identifierAttribute(
            ModuleCatalog catalog, Identity identity, String key) {
        List<ModuleCatalog.CatalogAttribute> rows = attributes(catalog, identity, key);
        if (rows.size() != 1 || !(rows.getFirst().value()
                instanceof ModuleCatalog.IdentifierValue identifier)) throw invalid();
        return identifier.value();
    }

    private static List<ModuleCatalog.CatalogAttribute> attributes(
            ModuleCatalog catalog, Identity identity, String key) {
        return catalog.catalogAttributes().stream()
                .filter(row -> identity.type().equals(row.definitionType())
                        && identity.key().equals(row.definitionKey())
                        && key.equals(row.attributeKey())).toList();
    }

    private static boolean stableKey(String value) {
        return value != null && value.length() <= 128
                && value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+");
    }

    private static RuleException invalid() {
        return new RuleException("MALFORMED_FROZEN_CATALOG");
    }

    public record SubclassRule(String subclassKey, String classKey, int selectionLevel) {
    }

    public record FeatureRule(String featureKey, String ownerType, String ownerKey,
            int level, String category, String executionMode, String executionAlgorithm) {
    }

    public record Matrix(List<SubclassRule> subclasses, List<FeatureRule> features) {
        public Matrix {
            subclasses = List.copyOf(subclasses);
            features = List.copyOf(features);
        }

        public Set<String> classKeys() {
            Set<String> keys = new HashSet<>();
            subclasses.forEach(rule -> keys.add(rule.classKey()));
            features.stream().filter(rule -> "character.class".equals(rule.ownerType()))
                    .forEach(rule -> keys.add(rule.ownerKey()));
            return Set.copyOf(keys);
        }

        public Map<String, Map<String, Long>> dispositionCountsByClass() {
            Map<String, Map<String, Long>> result = new LinkedHashMap<>();
            for (String classKey : classKeys().stream().sorted().toList()) {
                Set<String> subclassKeys = subclasses.stream()
                        .filter(rule -> classKey.equals(rule.classKey()))
                        .map(SubclassRule::subclassKey).collect(java.util.stream.Collectors.toSet());
                Map<String, Long> counts = new LinkedHashMap<>();
                for (String mode : List.of(AUTOMATIC, DM_ADJUDICATION, BLOCKED)) {
                    long count = features.stream()
                            .filter(rule -> classKey.equals(rule.ownerKey())
                                    || subclassKeys.contains(rule.ownerKey()))
                            .filter(rule -> mode.equals(rule.executionMode())).count();
                    counts.put(mode, count);
                }
                result.put(classKey, Map.copyOf(counts));
            }
            return Map.copyOf(result);
        }
    }

    public record Transition(String effectiveSubclassKey, String newlySelectedSubclassKey,
            List<FeatureRule> featureUnlocks) {
        public Transition {
            featureUnlocks = List.copyOf(featureUnlocks);
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

    private record Identity(String type, String key) {
    }
}
