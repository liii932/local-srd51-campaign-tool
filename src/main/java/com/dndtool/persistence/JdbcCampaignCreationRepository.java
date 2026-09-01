package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** JDBC implementation of the campaign creation transaction. */
public final class JdbcCampaignCreationRepository implements CampaignCreationRepository {
    private static final String OPERATION_TYPE = "CREATE_CAMPAIGN";
    private static final String LOCK_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, campaign_id, result_status
            FROM host_operation
            WHERE request_id = ?
            FOR UPDATE
            """;
    private static final String LOCK_RELEASE_SQL = """
            SELECT id, module_key, release_version, content_sha256
            FROM module_release
            WHERE module_key = ? AND release_version = ?
              AND canonical_format_version = 1
              AND hash_algorithm = 'SHA-256'
              AND release_status = 'RELEASED'
              AND released_at IS NOT NULL
            FOR SHARE
            """;
    private static final String INSERT_CAMPAIGN_SQL = """
            INSERT INTO campaign (campaign_key, campaign_name)
            VALUES (?, ?)
            """;
    private static final String LOCK_ACTIVE_CAMPAIGN_SQL = """
            SELECT id
            FROM campaign
            WHERE campaign_status = 'ACTIVE'
            FOR UPDATE
            """;
    private static final String INSERT_CAMPAIGN_MODULE_SQL = """
            INSERT INTO campaign_module (
                campaign_id, module_release_id, frozen_module_key,
                frozen_release_version, frozen_content_sha256)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, result_status, completed_at)
            VALUES (?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    private final DataSource dataSource;

    public JdbcCampaignCreationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Result create(Command command) throws SQLException {
        validateCommand(command);
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                // SERIALIZABLE makes the empty active-campaign range lock meaningful too.
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                Result existing = findExistingOperation(connection, command);
                if (existing != null) {
                    connection.commit();
                    restore(connection, original);
                    return existing;
                }

                Release release = lockRelease(connection, command);
                if (release == null) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Result.Status.RELEASE_UNAVAILABLE, null);
                }
                if (activeCampaignExists(connection)) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Result.Status.ACTIVE_CAMPAIGN_EXISTS, null);
                }

                long campaignId = insertCampaign(connection, command);
                insertCampaignModule(connection, campaignId, release);
                insertOperation(connection, command, campaignId);
                connection.commit();
                restore(connection, original);
                return new Result(Result.Status.CREATED, command.campaignKey());
            } catch (SQLException | RuntimeException exception) {
                rollbackAndRestore(connection, original, exception);
                throw exception;
            }
        }
    }

    private static Result findExistingOperation(Connection connection, Command command)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                String digest = requireString(result, "request_digest_sha256");
                String operationType = requireString(result, "operation_type");
                long campaignId = result.getLong("campaign_id");
                boolean missingCampaign = result.wasNull();
                String status = requireString(result, "result_status");
                if (result.next()) {
                    throw invalidState();
                }
                if (!digest.equals(command.requestDigestSha256())
                        || !OPERATION_TYPE.equals(operationType)) {
                    return new Result(Result.Status.IDEMPOTENCY_CONFLICT, null);
                }
                if (!"SUCCEEDED".equals(status) || missingCampaign) {
                    throw invalidState();
                }
                return new Result(
                        Result.Status.ALREADY_SUCCEEDED,
                        findCampaignKey(connection, campaignId));
            }
        }
    }

    private static String findCampaignKey(Connection connection, long campaignId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT campaign_key FROM campaign WHERE id = ?")) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw invalidState();
                }
                String campaignKey = requireString(result, "campaign_key");
                if (result.next()) {
                    throw invalidState();
                }
                // Replayed results must obey the same stable identity rule as new writes.
                if (!isCanonicalVersionFourUuid(campaignKey)) {
                    throw invalidState();
                }
                return campaignKey;
            }
        }
    }

    private static Release lockRelease(Connection connection, Command command)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_RELEASE_SQL)) {
            statement.setString(1, command.moduleKey());
            statement.setString(2, command.releaseVersion());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                long releaseId = result.getLong("id");
                if (result.wasNull() || releaseId <= 0) {
                    throw invalidState();
                }
                Release release = new Release(
                        releaseId,
                        requireString(result, "module_key"),
                        requireString(result, "release_version"),
                        requireString(result, "content_sha256"));
                if (result.next()) {
                    throw invalidState();
                }
                if (!release.contentSha256().equals(command.contentSha256())) {
                    return null;
                }
                return release;
            }
        }
    }

    private static long insertCampaign(Connection connection, Command command)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_CAMPAIGN_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, command.campaignKey());
            statement.setString(2, command.campaignName());
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw invalidState();
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw invalidState();
                }
                long id = keys.getLong(1);
                if (keys.wasNull() || id <= 0 || keys.next()) {
                    throw invalidState();
                }
                return id;
            }
        }
    }

    private static boolean activeCampaignExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_ACTIVE_CAMPAIGN_SQL)) {
            statement.setMaxRows(1);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void insertCampaignModule(
            Connection connection, long campaignId, Release release) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_CAMPAIGN_MODULE_SQL)) {
            statement.setLong(1, campaignId);
            statement.setLong(2, release.id());
            statement.setString(3, release.moduleKey());
            statement.setString(4, release.releaseVersion());
            statement.setString(5, release.contentSha256());
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw invalidState();
            }
        }
    }

    private static void insertOperation(
            Connection connection, Command command, long campaignId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setString(2, command.requestDigestSha256());
            statement.setString(3, OPERATION_TYPE);
            statement.setLong(4, campaignId);
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw invalidState();
            }
        }
    }

    private static String requireString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || result.wasNull()) {
            throw invalidState();
        }
        return value;
    }

    private static void validateCommand(Command command) throws SQLException {
        if (command == null || !isCanonicalVersionFourUuid(command.campaignKey())) {
            throw new SQLException("Invalid campaign creation command");
        }
    }

    private static boolean isCanonicalVersionFourUuid(String value) {
        if (value == null) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static SQLException invalidState() {
        return new SQLException("Invalid campaign creation persistence state");
    }

    private static void rollbackAndRestore(
            Connection connection, ConnectionState original, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        try {
            restore(connection, original);
        } catch (SQLException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private static void restore(Connection connection, ConnectionState original)
            throws SQLException {
        connection.setAutoCommit(original.autoCommit());
        connection.setReadOnly(original.readOnly());
        connection.setTransactionIsolation(original.transactionIsolation());
    }

    private record Release(long id, String moduleKey, String releaseVersion, String contentSha256) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int transactionIsolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(),
                    connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
