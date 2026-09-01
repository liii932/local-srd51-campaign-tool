package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** Writes the root-only {@code event.note} form inside a caller-owned transaction. */
public interface NoteEventRepository {
    SavedNote append(Connection connection, Command command) throws SQLException;

    /** The campaign tail and already-normalized message required for one note event. */
    record Command(long campaignId, long expectedEventTail, String message) {
    }

    /** Generated identity returned for later successful idempotency linkage. */
    record SavedNote(long gameEventId, long eventSequence) {
    }
}
