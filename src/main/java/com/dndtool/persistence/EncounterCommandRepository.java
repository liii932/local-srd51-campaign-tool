package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Adds durable idempotency and one root event around initial encounter persistence. */
public interface EncounterCommandRepository {
    Lookup find(Connection connection, Command command) throws SQLException;

    SavedEvent appendEvent(Connection connection, long campaignId) throws SQLException;

    void complete(Connection connection, Completion completion) throws SQLException;

    record Command(String requestId, String requestDigestSha256, long campaignId) {
    }

    record Lookup(Status status, SavedEvent replay) {
        public static Lookup fresh() { return new Lookup(Status.FRESH, null); }
        public static Lookup replay(SavedEvent event) {
            return new Lookup(Status.REPLAY, Objects.requireNonNull(event));
        }
        public static Lookup conflict() { return new Lookup(Status.CONFLICT, null); }
    }

    enum Status { FRESH, REPLAY, CONFLICT }

    record SavedEvent(long gameEventId, long eventSequence) {
    }

    record Completion(
            String requestId, String requestDigestSha256, long campaignId, long gameEventId) {
    }
}
