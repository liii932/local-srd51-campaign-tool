package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Read-only preview state and atomic confirmation boundary for one level advancement. */
public interface LevelAdvancementRepository {
    Optional<PreviewContext> findPreviewContext(String characterKey) throws SQLException;

    Optional<Result> findCompleted(String requestId, String requestDigestSha256)
            throws SQLException;

    Result confirm(Command command, HitPointResolver hitPointResolver) throws SQLException;

    record PreviewContext(
            String campaignKey,
            String characterKey,
            long expectedEventTail,
            long expectedRowVersion,
            String moduleKey,
            String releaseVersion,
            String contentSha256,
            String classKey,
            int classLevel,
            int totalLevel,
            int constitutionScore,
            int charismaScore,
            List<ResourceState> resources,
            List<ClassLevel> classLevels,
            Map<String, Integer> abilityScores,
            Set<String> acquiredFeats,
            Set<String> acquiredProficiencies,
            Map<String, String> subclassesByClass,
            Set<String> acquiredFeatures) {
        public PreviewContext {
            resources = List.copyOf(resources);
            classLevels = List.copyOf(classLevels);
            abilityScores = Map.copyOf(abilityScores);
            acquiredFeats = Set.copyOf(acquiredFeats);
            acquiredProficiencies = Set.copyOf(acquiredProficiencies);
            subclassesByClass = Map.copyOf(subclassesByClass);
            acquiredFeatures = Set.copyOf(acquiredFeatures);
        }

        public PreviewContext(String campaignKey, String characterKey,
                long expectedEventTail, long expectedRowVersion, String moduleKey,
                String releaseVersion, String contentSha256, String classKey, int classLevel,
                int totalLevel, int constitutionScore, int charismaScore,
                List<ResourceState> resources) {
            this(campaignKey, characterKey, expectedEventTail, expectedRowVersion, moduleKey,
                    releaseVersion, contentSha256, classKey, classLevel, totalLevel,
                    constitutionScore, charismaScore, resources,
                    List.of(new ClassLevel(classKey, classLevel)),
                    Map.of("ability.strength", 10, "ability.dexterity", 10,
                            "ability.constitution", constitutionScore,
                            "ability.intelligence", 10, "ability.wisdom", 10,
                            "ability.charisma", charismaScore), Set.of(), Set.of(),
                    Map.of(), Set.of());
        }
    }

    record ClassLevel(String classKey, int classLevel) {
    }

    record ResourceState(String resourceKey, long currentValue, long maximumValue,
            boolean unlimited) {
    }

    record ResourceChange(String resourceKey, ResourceState previous, ResourceState next) {
    }

    record Command(
            String requestId,
            String requestDigestSha256,
            String previewDigestSha256,
            PreviewContext expected,
            String hpChoiceAlgorithm,
            int targetLevel,
            int hitDieSides,
            int constitutionModifier,
            int previousProficiencyBonus,
            int newProficiencyBonus,
            List<ResourceChange> resourceChanges,
            com.dndtool.service.CharacterAdvancementChoiceRules.Prepared advancementChoice,
            com.dndtool.service.ClassFeatureRules.Transition featureTransition) {
        public Command {
            resourceChanges = List.copyOf(resourceChanges);
        }

        public Command(String requestId, String requestDigestSha256,
                String previewDigestSha256, PreviewContext expected,
                String hpChoiceAlgorithm, int targetLevel, int hitDieSides,
                int constitutionModifier, int previousProficiencyBonus,
                int newProficiencyBonus, List<ResourceChange> resourceChanges) {
            this(requestId, requestDigestSha256, previewDigestSha256, expected,
                    hpChoiceAlgorithm, targetLevel, hitDieSides, constitutionModifier,
                    previousProficiencyBonus, newProficiencyBonus, resourceChanges, null, null);
        }
    }

    @FunctionalInterface
    interface HitPointResolver {
        HitPointResolution resolve(int hitDieSides, int constitutionModifier);
    }

    record HitPointResolution(Integer hitDieRoll, int hitPointIncrease) {
    }

    record Result(Status status, String characterKey, Long rowVersion,
            Integer hitDieRoll, Integer hitPointIncrease) {
        public enum Status {
            ADVANCED,
            ALREADY_SUCCEEDED,
            IDEMPOTENCY_CONFLICT,
            CHARACTER_UNAVAILABLE,
            MODULE_BINDING_MISMATCH,
            STALE_PREVIEW,
            STALE_ROW_VERSION,
            AUTHORITATIVE_STATE_MISMATCH
        }
    }
}
