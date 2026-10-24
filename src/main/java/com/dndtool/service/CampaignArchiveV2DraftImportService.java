package com.dndtool.service;

import com.dndtool.persistence.BusinessWriteTransactions;
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

        try (BusinessWriteTransactions.Session writeTransaction =
                BusinessWriteTransactions.openLegacy(
                        dataSource, Connection.TRANSACTION_SERIALIZABLE)) {
            Connection connection = writeTransaction.connection();
            CampaignArchiveV2CharacterStateCodec.Result repeated =
                    CampaignArchiveV2CharacterStateCodec.read(
                            stableContent, stableCharacterKeys);
            if (repeated.status() != CampaignArchiveV2CharacterStateCodec.Status.READY) {
                writeTransaction.rollback();
                return Status.INVALID_ARCHIVE;
            }
            importWork.replace(connection, repeated.state());
            writeTransaction.commit();
            return Status.COMPLETED;
        }
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
}
