package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/** Reads target and active campaign identity in one non-locking consistent snapshot. */
public final class JdbcCampaignArchivePreviewRepository
        implements CampaignArchivePreviewRepository {
    private static final String STATE_SQL = """
            SELECT campaign_key, campaign_name, campaign_status
            FROM campaign
            WHERE campaign_key = ? OR campaign_status = 'ACTIVE'
            ORDER BY campaign_key
            """;
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;

    public JdbcCampaignArchivePreviewRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Snapshot inspect(String targetCampaignKey) throws SQLException {
        if (!canonicalUuidV4(targetCampaignKey)) {
            throw invalidState("Invalid archive campaign key");
        }
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                Snapshot snapshot = readState(connection, targetCampaignKey);
                connection.commit();
                restore(connection, original);
                return snapshot;
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private static Snapshot readState(Connection connection, String targetCampaignKey)
            throws SQLException {
        CampaignState target = null;
        CampaignState active = null;
        try (PreparedStatement statement = connection.prepareStatement(STATE_SQL)) {
            statement.setString(1, targetCampaignKey);
            statement.setMaxRows(3);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    CampaignState state = new CampaignState(
                            requiredString(result, "campaign_key"),
                            requiredString(result, "campaign_name"),
                            requiredStatus(result, "campaign_status"));
                    if (targetCampaignKey.equals(state.campaignKey())) {
                        if (target != null) throw invalidState("Duplicate target campaign");
                        target = state;
                    }
                    if ("ACTIVE".equals(state.campaignStatus())) {
                        if (active != null) throw invalidState("Multiple active campaigns");
                        active = state;
                    }
                }
            }
        }
        try {
            return new Snapshot(target, active);
        } catch (IllegalArgumentException exception) {
            throw invalidState("Inconsistent campaign preview state");
        }
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Missing campaign preview value");
        return value;
    }

    private static String requiredStatus(ResultSet result, String column) throws SQLException {
        String value = requiredString(result, column);
        if (!Set.of("ACTIVE", "ARCHIVED").contains(value)) {
            throw invalidState("Invalid campaign preview status");
        }
        return value;
    }

    private static boolean canonicalUuidV4(String value) {
        if (value == null) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
        connection.setTransactionIsolation(original.isolation());
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
