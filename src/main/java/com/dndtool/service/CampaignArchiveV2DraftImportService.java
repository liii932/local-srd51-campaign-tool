package com.dndtool.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

/**
 * Transaction harness for the unactivated format-2 character projection. It deliberately has no
 * public route or released-module dispatch; a later whole-campaign importer supplies the JDBC work.
 */
final class CampaignArchiveV2DraftImportService {
    private final DataSource dataSource;
    private final ImportWork importWork;

    CampaignArchiveV2DraftImportService(DataSource dataSource, ImportWork importWork) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.importWork = Objects.requireNonNull(importWork, "import work");
    }

    Status importState(byte[] content, Set<String> characterKeys) throws SQLException {
        byte[] stableContent = content == null ? null : content.clone();
        Set<String> stableCharacterKeys = characterKeys == null ? null : Set.copyOf(characterKeys);
        CampaignArchiveV2CharacterStateCodec.Result initial =
                CampaignArchiveV2CharacterStateCodec.read(stableContent, stableCharacterKeys);
        if (initial.status() != CampaignArchiveV2CharacterStateCodec.Status.READY) {
            return Status.INVALID_ARCHIVE;
        }

        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);
                CampaignArchiveV2CharacterStateCodec.Result repeated =
                        CampaignArchiveV2CharacterStateCodec.read(
                                stableContent, stableCharacterKeys);
                if (repeated.status() != CampaignArchiveV2CharacterStateCodec.Status.READY) {
                    connection.rollback();
                    restore(connection, original);
                    return Status.INVALID_ARCHIVE;
                }
                importWork.replace(connection, repeated.state());
                connection.commit();
                restore(connection, original);
                return Status.COMPLETED;
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
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

    enum Status {
        COMPLETED,
        INVALID_ARCHIVE
    }

    @FunctionalInterface
    interface ImportWork {
        /** Replaces the format-2 character state without committing or rolling back. */
        void replace(Connection connection, CampaignArchiveV2CharacterState state)
                throws SQLException;
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
