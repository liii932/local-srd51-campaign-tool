package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded DM decisions for acquired canonical-v2 features; never executes blocked features. */
public final class ClassFeatureAdjudicationRules {
    private static final Set<String> DECISIONS = Set.of("SUCCESS", "FAILURE", "NO_EFFECT");
    private static final Map<String, String> ADJUDICATION_KEYS = Map.of(
            "BOUNDED_FEATURE_SELECTION_V1", "adjudication.feature_selection",
            "BOUNDED_SUBCLASS_SELECTION_V1", "adjudication.subclass_selection",
            "BOUNDED_DM_ADJUDICATION_V1", "adjudication.feature_use");
    private final ClassFeatureRules featureRules = new ClassFeatureRules();

    public Prepared prepare(ModuleCatalog catalog, String featureKey,
            String decision, String adjudicationKey) {
        if (catalog == null || !stableKey(featureKey) || !DECISIONS.contains(decision)
                || !stableKey(adjudicationKey)) {
            throw new RuleException("INVALID_REQUEST");
        }
        List<ClassFeatureRules.FeatureRule> matches = featureRules.inspect(catalog).features()
                .stream().filter(rule -> featureKey.equals(rule.featureKey())).toList();
        if (matches.size() != 1) {
            throw new RuleException("MALFORMED_FROZEN_CATALOG");
        }
        ClassFeatureRules.FeatureRule feature = matches.getFirst();
        if (ClassFeatureRules.BLOCKED.equals(feature.executionMode())) {
            throw new RuleException("FEATURE_BLOCKED");
        }
        if (ClassFeatureRules.AUTOMATIC.equals(feature.executionMode())) {
            throw new RuleException("AUTOMATIC_FEATURE_REQUIRES_TYPED_EFFECT");
        }
        String expectedKey = ADJUDICATION_KEYS.get(feature.executionAlgorithm());
        if (!adjudicationKey.equals(expectedKey)) {
            throw new RuleException("INVALID_ADJUDICATION");
        }
        return new Prepared(feature.featureKey(), feature.executionAlgorithm(), decision,
                adjudicationKey);
    }

    private static boolean stableKey(String value) {
        return value != null && value.length() <= 128
                && value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+");
    }

    public record Prepared(String featureKey, String executionAlgorithm,
            String decision, String adjudicationKey) {
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
