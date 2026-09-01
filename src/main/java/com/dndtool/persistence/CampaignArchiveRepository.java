package com.dndtool.persistence;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Consistent read-only boundary for one campaign's current archive state. */
public interface CampaignArchiveRepository {
    Optional<Snapshot> findByCampaignKey(String campaignKey) throws SQLException;

    /** Contains stable business values only; JDBC identities never cross this boundary. */
    record Snapshot(
            Campaign campaign,
            ModuleBinding module,
            List<CharacterState> characters,
            List<FieldValue> fields,
            List<ClassLevel> classLevels,
            List<Proficiency> skillProficiencies,
            List<Proficiency> saveProficiencies,
            List<ItemState> items,
            List<MapState> maps,
            List<EventSnapshot> recentEvents) {
        public Snapshot {
            characters = List.copyOf(characters);
            fields = List.copyOf(fields);
            classLevels = List.copyOf(classLevels);
            skillProficiencies = List.copyOf(skillProficiencies);
            saveProficiencies = List.copyOf(saveProficiencies);
            items = List.copyOf(items);
            maps = List.copyOf(maps);
            recentEvents = List.copyOf(recentEvents);
        }
    }

    record Campaign(String campaignKey, String campaignName, String campaignStatus) {
    }

    /** Frozen and referenced release values remain separate for fail-closed validation. */
    record ModuleBinding(
            String frozenModuleKey,
            String frozenReleaseVersion,
            String frozenContentSha256,
            String releaseModuleKey,
            String releaseVersion,
            int canonicalFormatVersion,
            String hashAlgorithm,
            String releaseContentSha256,
            String releaseStatus) {
    }

    record CharacterState(
            String characterKey,
            String characterType,
            String characterName,
            String characterStatus,
            String savedModuleKey,
            String savedReleaseVersion,
            String savedContentSha256) {
    }

    /** Exactly one value component must match valueType. */
    record FieldValue(
            String characterKey,
            String fieldKey,
            String valueType,
            String textValue,
            Long integerValue,
            BigDecimal decimalValue,
            Boolean booleanValue) {
    }

    record ClassLevel(String characterKey, String classKey, int level) {
    }

    record Proficiency(String characterKey, String targetKey, String proficiencyKey) {
    }

    record ItemState(
            String characterKey,
            String sourceKind,
            String itemKey,
            String itemName,
            String itemDescription,
            int quantity,
            String itemStatus) {
    }

    record MapState(
            String mapKey,
            String mapType,
            String partyNodeKey,
            Encounter encounter) {
    }

    record Encounter(String battleStatus, List<Participant> participants) {
        public Encounter {
            participants = List.copyOf(participants);
        }
    }

    record Participant(String characterKey, String faction, String nodeKey) {
    }

    record EventSnapshot(
            long eventSequence,
            String eventType,
            String subjectCharacterKey,
            String eventText,
            CheckSnapshot check) {
    }

    record CheckSnapshot(
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
