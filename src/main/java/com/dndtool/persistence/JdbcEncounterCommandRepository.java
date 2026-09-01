package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** JDBC idempotency/event boundary for one initial encounter command. */
public final class JdbcEncounterCommandRepository
        implements EncounterCommandRepository {
    static final String OPERATION_TYPE = "INITIALIZE_STAGE3_ENCOUNTER";
    static final String EVENT_TYPE = "ENCOUNTER_INITIALIZED";

    private static final String LOCK_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, campaign_id,
                   result_status, game_event_id
            FROM host_operation
            WHERE request_id = ?
            FOR UPDATE
            """;
    private static final String LOAD_EVENT_SQL = """
            SELECT event_sequence
            FROM game_event
            WHERE id = ? AND campaign_id = ? AND event_type = 'ENCOUNTER_INITIALIZED'
            """;
    private static final String LOCK_EVENT_TAIL_SQL = """
            SELECT internal_event_tail
            FROM campaign
            WHERE id = ? AND campaign_status = 'ACTIVE'
            FOR UPDATE
            """;
    private static final String ADVANCE_EVENT_TAIL_SQL = """
            UPDATE campaign SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (campaign_id, event_sequence, event_type)
            VALUES (?, ?, 'ENCOUNTER_INITIALIZED')
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, game_event_id, result_status, completed_at
            ) VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    @Override
    public Lookup find(Connection connection, Command command) throws SQLException {
        requireTransaction(connection);
        validate(command.requestId(), command.requestDigestSha256(), command.campaignId());
        try (PreparedStatement statement = prepare(connection, LOCK_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Lookup.fresh();
                String digest = required(result, "request_digest_sha256");
                String operation = required(result, "operation_type");
                long campaignId = result.getLong("campaign_id");
                boolean missingCampaign = result.wasNull();
                String status = required(result, "result_status");
                long eventId = result.getLong("game_event_id");
                boolean missingEvent = result.wasNull();
                if (result.next()) throw new SQLException("Encounter request identity is not unique");
                if (!command.requestDigestSha256().equals(digest)
                        || !OPERATION_TYPE.equals(operation)) return Lookup.conflict();
                if (missingCampaign || missingEvent || campaignId != command.campaignId()
                        || eventId <= 0 || !"SUCCEEDED".equals(status)) {
                    throw new SQLException("Encounter replay is incomplete");
                }
                return Lookup.replay(loadEvent(connection, campaignId, eventId));
            }
        }
    }

    @Override
    public SavedEvent appendEvent(Connection connection, long campaignId) throws SQLException {
        requireTransaction(connection);
        if (campaignId <= 0) throw new SQLException("Invalid encounter campaign");
        long tail;
        try (PreparedStatement statement = prepare(connection, LOCK_EVENT_TAIL_SQL)) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Active encounter campaign is missing");
                tail = result.getLong(1);
                if (result.wasNull() || tail < 0 || tail == Long.MAX_VALUE || result.next()) {
                    throw new SQLException("Encounter event tail is invalid");
                }
            }
        }
        long sequence = tail + 1;
        try (PreparedStatement statement = prepare(connection, ADVANCE_EVENT_TAIL_SQL)) {
            statement.setLong(1, sequence);
            statement.setLong(2, campaignId);
            statement.setLong(3, tail);
            if (statement.executeUpdate() != 1) throw new SQLException("Encounter event tail conflict");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setQueryTimeout(5);
            statement.setLong(1, campaignId);
            statement.setLong(2, sequence);
            if (statement.executeUpdate() != 1) throw new SQLException("Encounter event insert failed");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Encounter event id is missing");
                long eventId = keys.getLong(1);
                if (eventId <= 0 || keys.next()) throw new SQLException("Encounter event id is invalid");
                return new SavedEvent(eventId, sequence);
            }
        }
    }

    @Override
    public void complete(Connection connection, Completion completion) throws SQLException {
        requireTransaction(connection);
        validate(completion.requestId(), completion.requestDigestSha256(), completion.campaignId());
        if (completion.gameEventId() <= 0) throw new SQLException("Encounter result event is invalid");
        try (PreparedStatement statement = prepare(connection, INSERT_OPERATION_SQL)) {
            statement.setString(1, completion.requestId());
            statement.setString(2, completion.requestDigestSha256());
            statement.setString(3, OPERATION_TYPE);
            statement.setLong(4, completion.campaignId());
            statement.setLong(5, completion.gameEventId());
            if (statement.executeUpdate() != 1) throw new SQLException("Encounter result was not recorded");
        }
    }

    private static SavedEvent loadEvent(Connection connection, long campaignId, long eventId)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, LOAD_EVENT_SQL)) {
            statement.setLong(1, eventId);
            statement.setLong(2, campaignId);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Encounter replay event is missing");
                long sequence = result.getLong(1);
                if (result.wasNull() || sequence <= 0 || result.next()) {
                    throw new SQLException("Encounter replay event is invalid");
                }
                return new SavedEvent(eventId, sequence);
            }
        }
    }

    private static void validate(String requestId, String digest, long campaignId)
            throws SQLException {
        try {
            if (requestId == null || !UUID.fromString(requestId).toString().equals(requestId)
                    || digest == null || !digest.matches("[0-9a-f]{64}")
                    || campaignId <= 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Encounter command identity is invalid");
        }
    }

    private static void requireTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw new SQLException("Encounter command requires a writable SERIALIZABLE transaction");
        }
    }

    private static String required(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw new SQLException("Encounter replay value is missing");
        return value;
    }

    private static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(5);
        return statement;
    }
}
