package com.dndtool.persistence;

import java.sql.SQLException;

/** Atomic persistence boundary for one audited character lifecycle command. */
public interface CharacterLifecycleMutationRepository {
    Result mutate(Command command) throws SQLException;

    /** All values needed to verify the frozen module and persist a durable command result. */
    record Command(
            String requestId,
            String requestDigestSha256,
            String characterKey,
            long expectedRowVersion,
            Action action,
            String newValue,
            String moduleKey,
            String releaseVersion,
            String contentSha256) {
    }

    record Result(Status status, Long rowVersion) {
    }

    enum Action {
        RENAME("RENAME_CHARACTER", "CHARACTER_RENAMED", "character.name"),
        CHANGE_TYPE("CHANGE_CHARACTER_TYPE", "CHARACTER_TYPE_CHANGED", "character.type"),
        ARCHIVE("ARCHIVE_CHARACTER", "CHARACTER_ARCHIVED", "character.status"),
        RESTORE("RESTORE_CHARACTER", "CHARACTER_RESTORED", "character.status");

        private final String operationType;
        private final String eventType;
        private final String changeKey;

        Action(String operationType, String eventType, String changeKey) {
            this.operationType = operationType;
            this.eventType = eventType;
            this.changeKey = changeKey;
        }

        public String operationType() {
            return operationType;
        }

        public String eventType() {
            return eventType;
        }

        public String changeKey() {
            return changeKey;
        }
    }

    enum Status {
        UPDATED,
        ALREADY_SUCCEEDED,
        NOT_FOUND,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        MODULE_BINDING_MISMATCH,
        NO_CHANGE
    }
}
