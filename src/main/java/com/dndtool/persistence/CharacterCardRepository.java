package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Consistent read projection for one host-only simplified character card. */
public interface CharacterCardRepository {
    Optional<Snapshot> findByCharacterKey(String characterKey) throws SQLException;

    record Snapshot(
            String characterKey,
            String characterType,
            String characterName,
            String characterStatus,
            long rowVersion,
            Binding binding,
            List<FieldValue> fields,
            List<ClassLevel> classLevels,
            List<Proficiency> skillProficiencies,
            List<Proficiency> saveProficiencies,
            List<Item> items) {
        public Snapshot {
            fields = List.copyOf(fields);
            classLevels = List.copyOf(classLevels);
            skillProficiencies = List.copyOf(skillProficiencies);
            saveProficiencies = List.copyOf(saveProficiencies);
            items = List.copyOf(items);
        }
    }

    /** Three independently stored identities used to reject frozen-module drift on every load. */
    record Binding(
            String savedModuleKey,
            String savedReleaseVersion,
            String savedContentSha256,
            String frozenModuleKey,
            String frozenReleaseVersion,
            String frozenContentSha256,
            String releaseModuleKey,
            String releaseVersion,
            String releaseContentSha256,
            String releaseStatus) {
    }

    record FieldValue(String fieldKey, String valueType, ModuleCatalog.ScalarValue value) {
    }

    record ClassLevel(String classKey, int level) {
    }

    record Proficiency(String targetKey, String proficiencyKey) {
    }

    /** itemId is an internal host-operation token and is never an export identity. */
    record Item(
            long itemId,
            String sourceKind,
            String itemKey,
            String itemName,
            String itemDescription,
            int quantity,
            String itemStatus) {
    }
}
