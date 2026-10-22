package com.dndtool.service;

import com.dndtool.module.ModuleCatalog;
import com.dndtool.persistence.JdbcCampaignArchiveV2CharacterStateRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

/** Internal DRAFT export only; never registered in the released format dispatcher or Host routes. */
final class CampaignArchiveV2DraftExportService {
    private final DataSource dataSource;
    private final ReadWork reader;

    CampaignArchiveV2DraftExportService(DataSource dataSource) {
        this(dataSource, new JdbcCampaignArchiveV2CharacterStateRepository()::read);
    }

    CampaignArchiveV2DraftExportService(DataSource dataSource, ReadWork reader) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.reader = Objects.requireNonNull(reader);
    }

    byte[] exportState(long campaignId, long moduleReleaseId, ModuleCatalog catalog) throws SQLException {
        if (campaignId <= 0 || moduleReleaseId <= 0 || catalog == null) {
            throw new IllegalArgumentException("Invalid internal character export context");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            boolean readOnly = connection.isReadOnly();
            int isolation = connection.getTransactionIsolation();
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                var state = reader.read(connection, campaignId, moduleReleaseId, catalog);
                Set<String> characters = state.creationSnapshots().stream()
                        .map(CampaignArchiveV2CharacterState.CreationSnapshot::characterKey)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                byte[] bytes = CampaignArchiveV2CharacterStateCodec.write(
                        state.validated(characters, catalog), characters);
                if (bytes.length > CampaignSaveFileService.MAX_BYTES) {
                    throw new IllegalArgumentException("Internal character archive exceeds byte limit");
                }
                connection.commit();
                restore(connection, autoCommit, readOnly, isolation);
                return bytes;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                try { restore(connection, autoCommit, readOnly, isolation); }
                catch (SQLException restore) { failure.addSuppressed(restore); }
                throw failure;
            }
        }
    }

    private static void restore(Connection connection, boolean autoCommit, boolean readOnly,
            int isolation) throws SQLException {
        connection.setAutoCommit(autoCommit);
        connection.setReadOnly(readOnly);
        connection.setTransactionIsolation(isolation);
    }

    @FunctionalInterface
    interface ReadWork {
        CampaignArchiveV2CharacterState read(Connection connection, long campaignId,
                long moduleReleaseId, ModuleCatalog catalog) throws SQLException;
    }
}
