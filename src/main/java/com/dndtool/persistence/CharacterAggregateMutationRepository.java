package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** Transaction boundary for one optimistic character-aggregate mutation. */
public interface CharacterAggregateMutationRepository {
    /**
     * Locks the stable character identity, checks {@code expectedRowVersion}, applies the supplied
     * aggregate writes, and increments the root version exactly once in the same transaction.
     */
    Result mutate(Command command, Mutation mutation) throws SQLException;

    record Command(String characterKey, long expectedRowVersion) {
    }

    record Result(Status status, Long rowVersion) {
        public enum Status {
            UPDATED,
            NOT_FOUND,
            VERSION_CONFLICT
        }
    }

    /**
     * Runs only after the character row is locked and its version matches. Implementations must
     * use this connection for every child-table, field-change and event write.
     */
    @FunctionalInterface
    interface Mutation {
        void apply(Connection connection, LockedCharacter character) throws SQLException;
    }

    /** Minimal locked root identity exposed to the aggregate write operation. */
    record LockedCharacter(long id, long campaignId, long moduleReleaseId) {
    }
}
