package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Persists one idempotent DM direct-position command in a caller-owned transaction. */
public interface EntityPositionCommandRepository {

    Lookup find(Connection connection, IdempotencyCommand command) throws SQLException;

    MoveResult move(Connection connection, MoveCommand command) throws SQLException;

    SavedEvent appendEvent(Connection connection, EventCommand command) throws SQLException;

    void complete(Connection connection, Completion completion) throws SQLException;

    record IdempotencyCommand(String requestId, String requestDigestSha256, long campaignId) {
    }

    record MoveCommand(
            long campaignId,
            long moduleReleaseId,
            String moduleKey,
            String releaseVersion,
            String contentSha256,
            String mapKey,
            long characterId,
            String characterKey,
            String nodeKey) {
    }

    record MoveResult(String previousNodeKey, String nodeKey, boolean changed) {
        public MoveResult {
            Objects.requireNonNull(previousNodeKey, "previous node is required");
            Objects.requireNonNull(nodeKey, "destination node is required");
        }
    }

    record EventCommand(
            long campaignId,
            long expectedEventTail,
            long characterId,
            String nodeKey) {
    }

    record SavedEvent(long gameEventId, long eventSequence, long characterId, String nodeKey) {
    }

    record Completion(
            String requestId,
            String requestDigestSha256,
            long campaignId,
            long gameEventId) {
    }

    record Replay(
            long gameEventId,
            long eventSequence,
            long characterId,
            String characterKey,
            String nodeKey) {
    }

    record Lookup(Status status, Replay replay) {
        public Lookup {
            Objects.requireNonNull(status, "lookup status is required");
            if ((status == Status.REPLAY) != (replay != null)) {
                throw new IllegalArgumentException("Replay data does not match lookup status");
            }
        }

        public static Lookup fresh() {
            return new Lookup(Status.NEW, null);
        }

        public static Lookup replay(Replay replay) {
            return new Lookup(Status.REPLAY, Objects.requireNonNull(replay));
        }

        public static Lookup conflict() {
            return new Lookup(Status.CONFLICT, null);
        }
    }

    enum Status {
        NEW,
        REPLAY,
        CONFLICT
    }
}
