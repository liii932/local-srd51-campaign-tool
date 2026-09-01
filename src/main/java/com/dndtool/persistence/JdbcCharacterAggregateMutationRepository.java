package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/** JDBC optimistic-lock transaction for all character aggregate changes. */
public final class JdbcCharacterAggregateMutationRepository
        implements CharacterAggregateMutationRepository {
    private static final String LOCK_CHARACTER_SQL = """
            SELECT id, campaign_id, module_release_id, row_version
            FROM character_record
            WHERE character_key = ?
            FOR UPDATE
            """;
    private static final String ADVANCE_VERSION_SQL = """
            UPDATE character_record
            SET row_version = row_version + 1
            WHERE character_key = ? AND row_version = ?
            """;

    private final DataSource dataSource;

    public JdbcCharacterAggregateMutationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Result mutate(Command command, Mutation mutation) throws SQLException {
        validate(command, mutation);
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);

                LockedRoot root = lockCharacter(connection, command.characterKey());
                if (root == null) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Result.Status.NOT_FOUND, null);
                }
                if (root.rowVersion() != command.expectedRowVersion()) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Result.Status.VERSION_CONFLICT, root.rowVersion());
                }

                mutation.apply(connection, new LockedCharacter(
                        root.id(), root.campaignId(), root.moduleReleaseId()));
                if (advanceVersion(connection, command) != 1) {
                    throw invalidState();
                }
                long nextVersion = Math.addExact(command.expectedRowVersion(), 1L);
                connection.commit();
                restore(connection, original);
                return new Result(Result.Status.UPDATED, nextVersion);
            } catch (SQLException | RuntimeException exception) {
                rollbackAndRestore(connection, original, exception);
                throw exception;
            }
        }
    }

    private static void validate(Command command, Mutation mutation) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(mutation, "mutation");
        if (!isCanonicalUuid(command.characterKey()) || command.expectedRowVersion() < 0) {
            throw new IllegalArgumentException("Invalid character mutation identity");
        }
    }

    private static LockedRoot lockCharacter(Connection connection, String characterKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CHARACTER_SQL)) {
            statement.setString(1, characterKey);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                long id = positiveLong(result, "id");
                long campaignId = positiveLong(result, "campaign_id");
                long releaseId = positiveLong(result, "module_release_id");
                long rowVersion = result.getLong("row_version");
                if (result.wasNull() || rowVersion < 0 || result.next()) {
                    throw invalidState();
                }
                return new LockedRoot(id, campaignId, releaseId, rowVersion);
            }
        }
    }

    private static int advanceVersion(Connection connection, Command command)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADVANCE_VERSION_SQL)) {
            statement.setString(1, command.characterKey());
            statement.setLong(2, command.expectedRowVersion());
            statement.setQueryTimeout(5);
            return statement.executeUpdate();
        }
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value <= 0) {
            throw invalidState();
        }
        return value;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return java.util.UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static SQLException invalidState() {
        return new SQLException("Invalid character aggregate persistence state");
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

    private record LockedRoot(
            long id, long campaignId, long moduleReleaseId, long rowVersion) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int transactionIsolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
