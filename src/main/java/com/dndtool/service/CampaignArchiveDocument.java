package com.dndtool.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Parsed version-1 campaign archive. This DTO contains stable save-file values only; database
 * identities and proof that the referenced module is installed are deliberately absent.
 */
public record CampaignArchiveDocument(
        int formatVersion,
        Campaign campaign,
        ModuleReference module,
        List<CharacterState> characters,
        List<FieldValue> fields,
        List<ClassLevel> classLevels,
        List<Proficiency> skillProficiencies,
        List<Proficiency> saveProficiencies,
        List<ItemState> items,
        List<MapState> maps,
        List<EventSnapshot> recentEvents) {
    public CampaignArchiveDocument {
        characters = List.copyOf(characters);
        fields = List.copyOf(fields);
        classLevels = List.copyOf(classLevels);
        skillProficiencies = List.copyOf(skillProficiencies);
        saveProficiencies = List.copyOf(saveProficiencies);
        items = List.copyOf(items);
        maps = List.copyOf(maps);
        recentEvents = List.copyOf(recentEvents);
    }

    public record Campaign(String campaignKey, String campaignName, String campaignStatus) {
    }

    public record ModuleReference(
            String moduleKey, String releaseVersion, String contentSha256) {
    }

    public record CharacterState(
            String characterKey,
            String characterType,
            String characterName,
            String characterStatus) {
    }

    /** Exactly one typed component is populated after strict scalar parsing. */
    public record FieldValue(
            String characterKey,
            String fieldKey,
            String valueType,
            String textValue,
            Long integerValue,
            BigDecimal decimalValue,
            Boolean booleanValue) {
    }

    public record ClassLevel(String characterKey, String classKey, int level) {
    }

    public record Proficiency(String characterKey, String targetKey, String proficiencyKey) {
    }

    public record ItemState(
            String characterKey,
            String sourceKind,
            String itemKey,
            String itemName,
            String itemDescription,
            int quantity,
            String itemStatus) {
    }

    public record MapState(
            String mapKey,
            String mapType,
            String partyNodeKey,
            Encounter encounter) {
    }

    public record Encounter(String battleStatus, List<Participant> participants) {
        public Encounter {
            participants = List.copyOf(participants);
        }
    }

    public record Participant(String characterKey, String faction, String nodeKey) {
    }

    public record EventSnapshot(
            long eventSequence,
            String eventType,
            String subjectCharacterKey,
            String eventText,
            CheckSnapshot check) {
    }

    public record CheckSnapshot(
            String eventKey,
            String checkKey,
            String rollModeKey,
            String modifierSourceKey,
            String manualName,
            int modifierValue,
            int totalValue,
            int difficultyClass,
            String checkResult) {
    }
}
