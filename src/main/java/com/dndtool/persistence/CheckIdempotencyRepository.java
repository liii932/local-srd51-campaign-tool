package com.dndtool.persistence;

import com.dndtool.service.D20CheckCalculator;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Locks one host command request identity and restores or records its durable check result. */
public interface CheckIdempotencyRepository {

    /** Runs before character locks and randomness inside the caller-owned serializable transaction. */
    Lookup find(Connection connection, Command command) throws SQLException;

    /** Records the successful root event before the surrounding transaction may commit. */
    void complete(Connection connection, Completion completion) throws SQLException;

    record Command(String requestId, String requestDigestSha256, long campaignId) {
    }

    record Completion(
            String requestId,
            String requestDigestSha256,
            long campaignId,
            long gameEventId) {
    }

    record Replay(
            CheckExecutionRepository.SavedCheck savedCheck,
            D20CheckCalculator.Result calculation) {
        public Replay {
            Objects.requireNonNull(savedCheck, "saved check is required");
            Objects.requireNonNull(calculation, "replayed calculation is required");
        }
    }

    record Lookup(Status status, Replay replay) {
        public Lookup {
            Objects.requireNonNull(status, "idempotency lookup status is required");
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
