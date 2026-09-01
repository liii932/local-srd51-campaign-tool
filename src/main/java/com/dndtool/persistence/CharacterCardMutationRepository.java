package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;

/** Atomic, idempotent persistence boundary for simplified card and item commands. */
public interface CharacterCardMutationRepository {
    Result mutate(Command command) throws SQLException;

    record Command(
            String requestId,
            String requestDigestSha256,
            String characterKey,
            long expectedRowVersion,
            Action action,
            String targetKey,
            String textValue,
            String description,
            Integer integerValue,
            String moduleKey,
            String releaseVersion,
            String contentSha256,
            List<IntegerFieldRule> integerFieldRules) {
        public Command {
            integerFieldRules = List.copyOf(integerFieldRules);
        }
    }

    /** Immutable rules needed for dependency checks inside the locked write transaction. */
    record IntegerFieldRule(
            String fieldKey, long minimum, long maximum, String dependentMaximumFieldKey) {
    }

    record Result(Status status, Long rowVersion) {
    }

    enum Action {
        SET_FIELD("SET_CHARACTER_FIELD", "CHARACTER_FIELD_UPDATED"),
        SET_CLASS_LEVEL("SET_CHARACTER_CLASS_LEVEL", "CHARACTER_CLASS_UPDATED"),
        SET_SKILL_PROFICIENCY(
                "SET_CHARACTER_SKILL_PROFICIENCY", "CHARACTER_PROFICIENCY_UPDATED"),
        SET_SAVE_PROFICIENCY(
                "SET_CHARACTER_SAVE_PROFICIENCY", "CHARACTER_PROFICIENCY_UPDATED"),
        ADD_MODULE_ITEM("ADD_CHARACTER_MODULE_ITEM", "ITEM_ADDED"),
        ADD_TEMPORARY_ITEM("ADD_CHARACTER_TEMPORARY_ITEM", "ITEM_ADDED"),
        SET_ITEM_QUANTITY("SET_CHARACTER_ITEM_QUANTITY", "ITEM_UPDATED"),
        ARCHIVE_ITEM("ARCHIVE_CHARACTER_ITEM", "ITEM_ARCHIVED"),
        RESTORE_ITEM("RESTORE_CHARACTER_ITEM", "ITEM_RESTORED");

        private final String operationType;
        private final String eventType;

        Action(String operationType, String eventType) {
            this.operationType = operationType;
            this.eventType = eventType;
        }

        public String operationType() {
            return operationType;
        }

        public String eventType() {
            return eventType;
        }
    }

    enum Status {
        UPDATED,
        ALREADY_SUCCEEDED,
        NOT_FOUND,
        TARGET_NOT_FOUND,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        MODULE_BINDING_MISMATCH,
        NO_CHANGE
    }
}
