package com.dndtool.persistence;

import com.dndtool.service.CheckTextPolicy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** JDBC note writer that intentionally creates no check, die, or effect-plan rows. */
public final class JdbcNoteEventRepository implements NoteEventRepository {
    private static final String ADVANCE_EVENT_TAIL_SQL = """
            UPDATE campaign
            SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_NOTE_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, event_text
            ) VALUES (?, ?, 'NOTE', ?)
            """;

    @Override
    public SavedNote append(Connection connection, Command command) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        validate(command);
        requireCallerTransaction(connection);

        long eventSequence;
        try {
            eventSequence = Math.addExact(command.expectedEventTail(), 1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Event sequence is exhausted", exception);
        }
        advanceEventTail(connection, command, eventSequence);
        long gameEventId = insertNote(connection, command, eventSequence);
        return new SavedNote(gameEventId, eventSequence);
    }

    private static void validate(Command command) {
        if (command == null
                || command.campaignId() <= 0
                || command.expectedEventTail() < 0
                || command.message() == null
                || !command.message().equals(
                        CheckTextPolicy.normalizeNoteMessage(command.message()))) {
            throw new IllegalArgumentException("Invalid note persistence command");
        }
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection.getAutoCommit()
                || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw new SQLException(
                    "Note persistence requires a writable caller-owned serializable transaction");
        }
    }

    private static void advanceEventTail(
            Connection connection, Command command, long eventSequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADVANCE_EVENT_TAIL_SQL)) {
            statement.setLong(1, eventSequence);
            statement.setLong(2, command.campaignId());
            statement.setLong(3, command.expectedEventTail());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Campaign event tail changed before note persistence");
            }
        }
    }

    private static long insertNote(
            Connection connection, Command command, long eventSequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_NOTE_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.campaignId());
            statement.setLong(2, eventSequence);
            statement.setString(3, command.message());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Note event was not inserted exactly once");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Note event generated key is missing");
                long id = keys.getLong(1);
                if (keys.wasNull() || id <= 0 || keys.next()) {
                    throw new SQLException("Note event generated key is invalid");
                }
                return id;
            }
        }
    }
}
