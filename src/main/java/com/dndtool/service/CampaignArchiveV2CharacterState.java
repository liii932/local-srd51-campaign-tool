package com.dndtool.service;

import java.util.List;
import java.util.Set;

/**
 * Stable database-id-free character-rules projection being developed for archive format 2.
 * This type is not wired to the released archive dispatcher or any Host route.
 */
public record CampaignArchiveV2CharacterState(
        int archiveFormatVersion,
        long eventTail,
        List<StateEvent> stateEvents,
        List<CreationSnapshot> creationSnapshots,
        List<CreationSelection> creationSelections,
        List<ResourceState> resources,
        List<ClassLevel> classLevels,
        List<SubclassState> subclasses,
        List<FeatureState> features,
        List<FeatureChoice> featureChoices,
        List<FeatState> feats,
        List<MulticlassProficiency> multiclassProficiencies) {
    public static final int FORMAT_VERSION = 2;

    public CampaignArchiveV2CharacterState {
        stateEvents = List.copyOf(stateEvents);
        creationSnapshots = List.copyOf(creationSnapshots);
        creationSelections = List.copyOf(creationSelections);
        resources = List.copyOf(resources);
        classLevels = List.copyOf(classLevels);
        subclasses = List.copyOf(subclasses);
        features = List.copyOf(features);
        featureChoices = List.copyOf(featureChoices);
        feats = List.copyOf(feats);
        multiclassProficiencies = List.copyOf(multiclassProficiencies);
    }

    /** Revalidates all document-local identities and returns canonical archive ordering. */
    public CampaignArchiveV2CharacterState validated(Set<String> characterKeys) {
        return CampaignArchiveV2CharacterStateValidator.normalize(this, characterKeys);
    }

    /** Root events needed by current state, not an unbounded event history. */
    public record StateEvent(
            long eventSequence, String eventType, String subjectCharacterKey) {
    }

    public record CreationSnapshot(
            String characterKey,
            long createdEventSequence,
            String previewDigestSha256,
            String requestDigestSha256,
            String abilityMethodKey,
            String raceKey,
            String subraceKey,
            String backgroundKey,
            String classKey,
            List<AbilityScore> abilities,
            int maximumHitPoints) {
        public CreationSnapshot {
            abilities = List.copyOf(abilities);
        }
    }

    public record AbilityScore(String abilityKey, int baseScore, int finalScore) {
    }

    public record CreationSelection(
            String characterKey, String selectionKind, int selectionOrder, String selectionKey) {
    }

    public record ResourceState(
            String characterKey,
            String resourceKey,
            long currentValue,
            long maximumValue,
            boolean unlimited) {
    }

    public record ClassLevel(String characterKey, String classKey, int classLevel) {
    }

    public record SubclassState(
            String characterKey,
            String classKey,
            String subclassKey,
            int selectedAtClassLevel,
            long acquiredEventSequence) {
    }

    public record FeatureState(
            String characterKey,
            String featureKey,
            int acquiredAtClassLevel,
            String executionMode,
            String executionAlgorithm,
            long acquiredEventSequence) {
    }

    public record FeatureChoice(
            String characterKey,
            String sourceFeatureKey,
            int choiceOrder,
            String choiceType,
            String choiceKey,
            long acquiredEventSequence) {
    }

    public record FeatState(
            String characterKey, String featKey, long acquiredEventSequence) {
    }

    public record MulticlassProficiency(
            String characterKey,
            String classKey,
            String proficiencyKey,
            long acquiredEventSequence) {
    }
}
