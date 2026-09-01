package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only preview context and atomic confirmation boundary for level-one creation. */
public interface LevelOneCharacterCreationRepository {
    Optional<PreviewContext> findPreviewContext(String campaignKey) throws SQLException;

    Result confirm(Command command) throws SQLException;

    record PreviewContext(
            String campaignKey,
            long expectedEventTail,
            String moduleKey,
            String releaseVersion,
            String contentSha256) {
    }

    record Command(
            String requestId,
            String requestDigestSha256,
            String characterKey,
            String campaignKey,
            String characterName,
            String moduleKey,
            String releaseVersion,
            String contentSha256,
            long expectedEventTail,
            String previewDigestSha256,
            String raceKey,
            String subraceKey,
            String backgroundKey,
            String classKey,
            Map<String, Integer> baseAbilityScores,
            Map<String, Integer> finalAbilityScores,
            List<Selection> selections,
            int maximumHitPoints,
            int hitDieSides,
            List<InitialResource> initialResources,
            com.dndtool.service.ClassFeatureRules.Transition featureTransition) {
        public Command {
            baseAbilityScores = Map.copyOf(baseAbilityScores);
            finalAbilityScores = Map.copyOf(finalAbilityScores);
            selections = List.copyOf(selections);
            initialResources = List.copyOf(initialResources);
        }
    }

    record Selection(String kind, String key) {
    }

    record InitialResource(String resourceKey, long currentValue, long maximumValue,
            boolean unlimited) {
    }

    record Result(Status status, String characterKey, Long rowVersion) {
        public enum Status {
            CREATED,
            ALREADY_SUCCEEDED,
            IDEMPOTENCY_CONFLICT,
            CAMPAIGN_UNAVAILABLE,
            MODULE_BINDING_MISMATCH,
            STALE_PREVIEW
        }
    }
}
