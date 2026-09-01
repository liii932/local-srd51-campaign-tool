package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Reads the active command context without exposing database ids to an HTTP DTO. */
public final class JdbcHostCommandContextRepository
        implements HostCommandContextRepository {
    private static final String FIND_ACTIVE_SQL = """
            SELECT campaign_root.id, campaign_root.campaign_key,
                   binding.module_release_id, binding.frozen_module_key,
                   binding.frozen_release_version, binding.frozen_content_sha256,
                   release_root.module_key, release_root.release_version,
                   release_root.content_sha256, release_root.release_status
            FROM campaign AS campaign_root
            JOIN campaign_module AS binding ON binding.campaign_id = campaign_root.id
            JOIN module_release AS release_root ON release_root.id = binding.module_release_id
            WHERE campaign_root.campaign_status = 'ACTIVE'
            ORDER BY campaign_root.id
            """;

    private final DataSource dataSource;

    public JdbcHostCommandContextRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<Context> findActive() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                Optional<Context> result = read(connection);
                connection.commit();
                restore(connection, original);
                return result;
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private static Optional<Context> read(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_SQL)) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Context context = new Context(
                        positiveLong(result, "id"),
                        required(result, "campaign_key"),
                        positiveLong(result, "module_release_id"),
                        required(result, "frozen_module_key"),
                        required(result, "frozen_release_version"),
                        required(result, "frozen_content_sha256"),
                        required(result, "module_key"),
                        required(result, "release_version"),
                        required(result, "content_sha256"),
                        required(result, "release_status"));
                if (result.next()) throw new SQLException("More than one active campaign exists");
                return Optional.of(context);
            }
        }
    }

    private static String required(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw new SQLException("Command context is incomplete");
        return value;
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value <= 0) throw new SQLException("Command context id is invalid");
        return value;
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

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
