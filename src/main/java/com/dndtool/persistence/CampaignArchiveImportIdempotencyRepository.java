package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Locks and records the durable result of one whole-campaign import request. */
public interface CampaignArchiveImportIdempotencyRepository {
    /** Runs after confirmation validation, before any import work, in the caller-owned transaction. */
    Lookup find(Connection connection, Command command) throws SQLException;

    /** Records success only after the whole import work has completed in the same transaction. */
    void complete(Connection connection, Completion completion) throws SQLException;

    record Command(String requestId, String requestDigestSha256) {
    }

    record Completion(String requestId, String requestDigestSha256, long campaignId) {
    }

    record Lookup(Status status, Long campaignId) {
        public Lookup {
            Objects.requireNonNull(status, "import idempotency status");
            if ((status == Status.REPLAY) != (campaignId != null)) {
                throw new IllegalArgumentException("Import replay identity does not match status");
            }
            if (campaignId != null && campaignId <= 0) {
                throw new IllegalArgumentException("Invalid replayed campaign id");
            }
        }

        public static Lookup fresh() {
            return new Lookup(Status.NEW, null);
        }

        public static Lookup replay(long campaignId) {
            return new Lookup(Status.REPLAY, campaignId);
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
