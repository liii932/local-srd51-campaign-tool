package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;

/** Atomic persistence boundary for a character root, initial values and creation audit. */
public interface CharacterCreationRepository {
    Result create(Command command) throws SQLException;

    /** All values have already passed module validation and server-side normalization. */
    record Command(
            String requestId,
            String requestDigestSha256,
            String characterKey,
            String campaignKey,
            String characterType,
            String characterName,
            String moduleKey,
            String releaseVersion,
            String contentSha256,
            String templateKey,
            List<FieldValue> fieldValues,
            List<ClassLevel> classLevels,
            List<Proficiency> skillProficiencies,
            List<Proficiency> saveProficiencies) {
        public Command {
            fieldValues = List.copyOf(fieldValues);
            classLevels = List.copyOf(classLevels);
            skillProficiencies = List.copyOf(skillProficiencies);
            saveProficiencies = List.copyOf(saveProficiencies);
        }
    }

    record FieldValue(String fieldKey, String valueType, ModuleCatalog.ScalarValue value) {
    }

    record ClassLevel(String classKey, int level) {
    }

    record Proficiency(String targetKey, String proficiencyKey) {
    }

    record Result(Status status, String characterKey, Long rowVersion) {
        public enum Status {
            CREATED,
            ALREADY_SUCCEEDED,
            IDEMPOTENCY_CONFLICT,
            CAMPAIGN_UNAVAILABLE,
            MODULE_BINDING_MISMATCH
        }
    }
}
