package com.dndtool.service;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict document-local validation and canonical ordering for the format-2 DRAFT slice. */
final class CampaignArchiveV2CharacterStateValidator {
    private static final Pattern STABLE_KEY =
            Pattern.compile("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+");
    private static final Pattern ALGORITHM = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ABILITIES = Set.of(
            "ability.strength", "ability.dexterity", "ability.constitution",
            "ability.intelligence", "ability.wisdom", "ability.charisma");
    private static final Set<String> SELECTION_KINDS = Set.of(
            "ABILITY_BONUS", "SKILL", "SAVE", "LANGUAGE", "TOOL", "STARTING_OPTION");
    private static final Set<String> EVENT_TYPES = Set.of(
            "LEVEL_ONE_CHARACTER_CREATED", "CHARACTER_LEVEL_ADVANCED");

    private CampaignArchiveV2CharacterStateValidator() {
    }

    static CampaignArchiveV2CharacterState normalize(
            CampaignArchiveV2CharacterState state, Set<String> characterKeys) {
        if (state == null || characterKeys == null
                || state.archiveFormatVersion() != CampaignArchiveV2CharacterState.FORMAT_VERSION
                || state.eventTail() < 0) {
            throw invalid();
        }
        Set<String> characters = Set.copyOf(characterKeys);
        if (characters.stream().anyMatch(key -> !canonicalUuidV4(key))) throw invalid();

        Map<Long, StateEvent> events = new HashMap<>();
        for (StateEvent event : state.stateEvents()) {
            if (event == null || event.eventSequence() <= 0
                    || event.eventSequence() > state.eventTail()
                    || !EVENT_TYPES.contains(event.eventType())
                    || !characters.contains(event.subjectCharacterKey())
                    || events.putIfAbsent(event.eventSequence(), event) != null) {
                throw invalid();
            }
        }
        Set<Long> referencedEvents = new HashSet<>();

        Set<String> snapshots = new HashSet<>();
        Map<String, CreationSnapshot> snapshotByCharacter = new HashMap<>();
        for (CreationSnapshot snapshot : state.creationSnapshots()) {
            if (snapshot == null || !characters.contains(snapshot.characterKey())
                    || !snapshots.add(snapshot.characterKey())
                    || !sha(snapshot.previewDigestSha256()) || !sha(snapshot.requestDigestSha256())
                    || !stable(snapshot.abilityMethodKey()) || !stable(snapshot.raceKey())
                    || snapshot.subraceKey() != null && !stable(snapshot.subraceKey())
                    || !stable(snapshot.backgroundKey()) || !stable(snapshot.classKey())
                    || snapshot.maximumHitPoints() < 1 || snapshot.maximumHitPoints() > 65_535
                    || !validAbilities(snapshot.abilities())
                    || !event(events, referencedEvents, snapshot.createdEventSequence(),
                            snapshot.characterKey(), "LEVEL_ONE_CHARACTER_CREATED")) {
                throw invalid();
            }
            snapshotByCharacter.put(snapshot.characterKey(), snapshot);
        }
        if (!snapshots.equals(characters)) throw invalid();

        Set<String> selectionIdentity = new HashSet<>();
        Set<String> selectionOrder = new HashSet<>();
        for (CreationSelection selection : state.creationSelections()) {
            if (selection == null || !characters.contains(selection.characterKey())
                    || !SELECTION_KINDS.contains(selection.selectionKind())
                    || selection.selectionOrder() < 1 || selection.selectionOrder() > 65_535
                    || !stable(selection.selectionKey())
                    || !selectionIdentity.add(relation(
                            selection.characterKey(), selection.selectionKind(),
                            selection.selectionKey()))
                    || !selectionOrder.add(relation(
                            selection.characterKey(), selection.selectionKind(),
                            Integer.toString(selection.selectionOrder())))) {
                throw invalid();
            }
        }

        Set<String> resources = new HashSet<>();
        for (ResourceState resource : state.resources()) {
            boolean bounded = resource != null && !resource.unlimited()
                    && resource.maximumValue() > 0 && resource.currentValue() >= 0
                    && resource.currentValue() <= resource.maximumValue();
            boolean unlimited = resource != null && resource.unlimited()
                    && resource.currentValue() == 0 && resource.maximumValue() == 0;
            if (resource == null || !characters.contains(resource.characterKey())
                    || !stable(resource.resourceKey()) || (!bounded && !unlimited)
                    || !resources.add(relation(resource.characterKey(), resource.resourceKey()))) {
                throw invalid();
            }
        }

        Set<String> classes = new HashSet<>();
        Map<String, Integer> totalLevels = new HashMap<>();
        for (ClassLevel level : state.classLevels()) {
            if (level == null || !characters.contains(level.characterKey())
                    || !stable(level.classKey()) || level.classLevel() < 1 || level.classLevel() > 20
                    || !classes.add(relation(level.characterKey(), level.classKey()))) {
                throw invalid();
            }
            totalLevels.merge(level.characterKey(), level.classLevel(), Integer::sum);
        }
        for (String character : characters) {
            CreationSnapshot snapshot = snapshotByCharacter.get(character);
            if (!classes.contains(relation(character, snapshot.classKey()))
                    || totalLevels.getOrDefault(character, 0) < 1
                    || totalLevels.getOrDefault(character, 0) > 20) {
                throw invalid();
            }
        }

        Set<String> subclassClasses = new HashSet<>();
        Set<String> subclassKeys = new HashSet<>();
        for (SubclassState subclass : state.subclasses()) {
            if (subclass == null || !classes.contains(relation(
                    subclass.characterKey(), subclass.classKey()))
                    || !stable(subclass.subclassKey())
                    || subclass.selectedAtClassLevel() < 1
                    || subclass.selectedAtClassLevel() > 20
                    || !subclassClasses.add(relation(
                            subclass.characterKey(), subclass.classKey()))
                    || !subclassKeys.add(relation(
                            subclass.characterKey(), subclass.subclassKey()))
                    || !event(events, referencedEvents, subclass.acquiredEventSequence(),
                            subclass.characterKey(), null)) {
                throw invalid();
            }
        }

        Set<String> featureKeys = new HashSet<>();
        Map<String, FeatureState> featureByIdentity = new HashMap<>();
        for (FeatureState feature : state.features()) {
            String identity = feature == null ? null
                    : relation(feature.characterKey(), feature.featureKey());
            if (feature == null || !characters.contains(feature.characterKey())
                    || !stable(feature.featureKey())
                    || feature.acquiredAtClassLevel() < 1 || feature.acquiredAtClassLevel() > 20
                    || !Set.of("AUTOMATIC", "DM_ADJUDICATION", "BLOCKED")
                            .contains(feature.executionMode())
                    || feature.executionAlgorithm() == null
                    || !ALGORITHM.matcher(feature.executionAlgorithm()).matches()
                    || !featureKeys.add(identity)
                    || !event(events, referencedEvents, feature.acquiredEventSequence(),
                            feature.characterKey(), null)) {
                throw invalid();
            }
            featureByIdentity.put(identity, feature);
        }

        Set<String> choiceIdentity = new HashSet<>();
        Set<String> choiceOrder = new HashSet<>();
        for (FeatureChoice choice : state.featureChoices()) {
            String source = choice == null ? null
                    : relation(choice.characterKey(), choice.sourceFeatureKey());
            FeatureState sourceFeature = featureByIdentity.get(source);
            if (choice == null || sourceFeature == null || !stable(choice.choiceType())
                    || !stable(choice.choiceKey()) || choice.choiceOrder() < 1
                    || choice.choiceOrder() > 65_535
                    || choice.acquiredEventSequence() != sourceFeature.acquiredEventSequence()
                    || !choiceIdentity.add(relation(
                            source, choice.choiceType(), choice.choiceKey()))
                    || !choiceOrder.add(relation(source, Integer.toString(choice.choiceOrder())))
                    || !event(events, referencedEvents, choice.acquiredEventSequence(),
                            choice.characterKey(), null)) {
                throw invalid();
            }
        }

        Set<String> featKeys = new HashSet<>();
        for (FeatState feat : state.feats()) {
            if (feat == null || !characters.contains(feat.characterKey())
                    || !stable(feat.featKey())
                    || !featKeys.add(relation(feat.characterKey(), feat.featKey()))
                    || !event(events, referencedEvents, feat.acquiredEventSequence(),
                            feat.characterKey(), "CHARACTER_LEVEL_ADVANCED")) {
                throw invalid();
            }
        }

        Set<String> proficiencies = new HashSet<>();
        for (MulticlassProficiency proficiency : state.multiclassProficiencies()) {
            if (proficiency == null || !classes.contains(relation(
                    proficiency.characterKey(), proficiency.classKey()))
                    || !stable(proficiency.proficiencyKey())
                    || !proficiencies.add(relation(
                            proficiency.characterKey(), proficiency.proficiencyKey()))
                    || !event(events, referencedEvents, proficiency.acquiredEventSequence(),
                            proficiency.characterKey(), "CHARACTER_LEVEL_ADVANCED")) {
                throw invalid();
            }
        }
        if (!referencedEvents.equals(events.keySet())) throw invalid();

        return new CampaignArchiveV2CharacterState(
                state.archiveFormatVersion(), state.eventTail(),
                sorted(state.stateEvents(), Comparator.comparingLong(StateEvent::eventSequence)),
                normalizedSnapshots(state.creationSnapshots()),
                sorted(state.creationSelections(), Comparator.comparing(CreationSelection::characterKey)
                        .thenComparing(CreationSelection::selectionKind)
                        .thenComparingInt(CreationSelection::selectionOrder)),
                sorted(state.resources(), Comparator.comparing(ResourceState::characterKey)
                        .thenComparing(ResourceState::resourceKey)),
                sorted(state.classLevels(), Comparator.comparing(ClassLevel::characterKey)
                        .thenComparing(ClassLevel::classKey)),
                sorted(state.subclasses(), Comparator.comparing(SubclassState::characterKey)
                        .thenComparing(SubclassState::classKey)),
                sorted(state.features(), Comparator.comparing(FeatureState::characterKey)
                        .thenComparing(FeatureState::featureKey)),
                sorted(state.featureChoices(), Comparator.comparing(FeatureChoice::characterKey)
                        .thenComparing(FeatureChoice::sourceFeatureKey)
                        .thenComparingInt(FeatureChoice::choiceOrder)),
                sorted(state.feats(), Comparator.comparing(FeatState::characterKey)
                        .thenComparing(FeatState::featKey)),
                sorted(state.multiclassProficiencies(),
                        Comparator.comparing(MulticlassProficiency::characterKey)
                                .thenComparing(MulticlassProficiency::proficiencyKey)));
    }

    private static boolean validAbilities(List<AbilityScore> values) {
        if (values == null || values.size() != ABILITIES.size()) return false;
        Set<String> keys = new HashSet<>();
        for (AbilityScore value : values) {
            if (value == null || !ABILITIES.contains(value.abilityKey())
                    || !keys.add(value.abilityKey()) || value.baseScore() < 3
                    || value.baseScore() > 18 || value.finalScore() < 3
                    || value.finalScore() > 20) {
                return false;
            }
        }
        return keys.equals(ABILITIES);
    }

    private static boolean event(
            Map<Long, StateEvent> events,
            Set<Long> referenced,
            long sequence,
            String characterKey,
            String requiredType) {
        StateEvent event = events.get(sequence);
        if (event == null || !characterKey.equals(event.subjectCharacterKey())
                || requiredType != null && !requiredType.equals(event.eventType())) {
            return false;
        }
        referenced.add(sequence);
        return true;
    }

    private static List<CreationSnapshot> normalizedSnapshots(List<CreationSnapshot> values) {
        List<CreationSnapshot> result = new ArrayList<>();
        for (CreationSnapshot value : values) {
            result.add(new CreationSnapshot(
                    value.characterKey(), value.createdEventSequence(),
                    value.previewDigestSha256(), value.requestDigestSha256(),
                    value.abilityMethodKey(), value.raceKey(), value.subraceKey(),
                    value.backgroundKey(), value.classKey(),
                    sorted(value.abilities(), Comparator.comparing(AbilityScore::abilityKey)),
                    value.maximumHitPoints()));
        }
        result.sort(Comparator.comparing(CreationSnapshot::characterKey));
        return List.copyOf(result);
    }

    private static <T> List<T> sorted(List<T> values, Comparator<T> comparator) {
        ArrayList<T> result = new ArrayList<>(values);
        result.sort(comparator);
        return List.copyOf(result);
    }

    private static boolean stable(String value) {
        return value != null && value.length() <= 128
                && Normalizer.isNormalized(value, Normalizer.Form.NFC)
                && STABLE_KEY.matcher(value).matches();
    }

    private static boolean sha(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static boolean canonicalUuidV4(String value) {
        if (value == null) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String relation(String... values) {
        return String.join("\u0000", values);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid archive format 2 character state");
    }
}
