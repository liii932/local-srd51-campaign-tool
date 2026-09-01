package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** JDBC idempotency boundary for whole-campaign imports. */
public final class JdbcCampaignArchiveImportIdempotencyRepository
        implements CampaignArchiveImportIdempotencyRepository {
    static final String OPERATION_TYPE = "IMPORT_CAMPAIGN_ARCHIVE";
    private static final String LOCK_SQL = """
            SELECT request_digest_sha256, operation_type, campaign_id, result_status
            FROM host_operation
            WHERE request_id = ?
            FOR UPDATE
            """;
    private static final String INSERT_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, result_status, completed_at)
            VALUES (?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    @Override
    public Lookup find(Connection connection, Command command) throws SQLException {
        validate(command);
        requireCallerTransaction(connection);
        try (PreparedStatement statement = connection.prepareStatement(LOCK_SQL)) {
            statement.setString(1, command.requestId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Lookup.fresh();
                String digest = requiredString(result, "request_digest_sha256");
                String operation = requiredString(result, "operation_type");
                Long campaignId = nullablePositiveLong(result, "campaign_id");
                String status = requiredString(result, "result_status");
                if (result.next()) throw invalidState("Import request identity was not unique");
                if (!digest.equals(command.requestDigestSha256())
                        || !OPERATION_TYPE.equals(operation)) {
                    return Lookup.conflict();
                }
                if (campaignId == null || !"SUCCEEDED".equals(status)) {
                    throw invalidState("Import idempotency result is incomplete");
                }
                return Lookup.replay(campaignId);
            }
        }
    }

    @Override
    public void complete(Connection connection, Completion completion) throws SQLException {
        validate(completion);
        requireCallerTransaction(connection);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, completion.requestId());
            statement.setString(2, completion.requestDigestSha256());
            statement.setString(3, OPERATION_TYPE);
            statement.setLong(4, completion.campaignId());
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw invalidState("Import idempotency result was not inserted exactly once");
            }
        }
    }

    private static void validate(Command command) {
        if (command == null || !canonicalUuid(command.requestId())
                || !sha256(command.requestDigestSha256())) {
            throw new IllegalArgumentException("Invalid import idempotency command");
        }
    }

    private static void validate(Completion completion) {
        if (completion == null || !canonicalUuid(completion.requestId())
                || !sha256(completion.requestDigestSha256()) || completion.campaignId() <= 0) {
            throw new IllegalArgumentException("Invalid import idempotency completion");
        }
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw invalidState("Import idempotency requires a caller-owned serializable transaction");
        }
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Missing import operation value");
        return value;
    }

    private static Long nullablePositiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) return null;
        if (value <= 0) throw invalidState("Invalid import operation campaign id");
        return value;
    }

    private static boolean canonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }
}
