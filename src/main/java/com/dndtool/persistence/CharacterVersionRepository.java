package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Locks and versions every character identity participating in one host command check. */
public interface CharacterVersionRepository {

    LockResult lockBeforeRoll(Connection connection, LockCommand command) throws SQLException;

    /** Advances each actually modified locked aggregate once, still inside the caller transaction. */
    Map<Long, Long> advanceModifiedVersions(
            Connection connection, LockedScope scope, Set<Long> modifiedCharacterIds)
            throws SQLException;

    record LockCommand(
            long campaignId,
            long moduleReleaseId,
            VersionExpectation executor,
            List<VersionExpectation> possibleTargets) {
        public LockCommand {
            Objects.requireNonNull(executor, "executor version is required");
            possibleTargets = List.copyOf(possibleTargets);
        }
    }

    record VersionExpectation(String characterKey, long expectedRowVersion) {
    }

    record LockedCharacter(
            long id,
            String characterKey,
            long campaignId,
            long moduleReleaseId,
            long rowVersion) {
    }

    record LockedScope(
            long campaignId,
            long moduleReleaseId,
            long expectedEventTail,
            LockedCharacter executor,
            List<LockedCharacter> charactersById) {
        public LockedScope {
            Objects.requireNonNull(executor, "locked executor is required");
            charactersById = List.copyOf(charactersById);
        }
    }

    record LockResult(
            Status status,
            LockedScope scope,
            String rejectedCharacterKey,
            Long currentRowVersion) {
        public LockResult {
            Objects.requireNonNull(status, "lock status is required");
        }

        public static LockResult locked(LockedScope scope) {
            return new LockResult(Status.LOCKED, Objects.requireNonNull(scope), null, null);
        }

        public static LockResult rejected(
                Status status, String characterKey, Long currentRowVersion) {
            if (status == Status.LOCKED) throw new IllegalArgumentException("LOCKED requires scope");
            return new LockResult(status, null, characterKey, currentRowVersion);
        }
    }

    enum Status {
        LOCKED,
        CAMPAIGN_NOT_FOUND,
        CHARACTER_NOT_FOUND,
        CHARACTER_INVALID,
        MODULE_HASH_MISMATCH,
        VERSION_CONFLICT
    }
}
