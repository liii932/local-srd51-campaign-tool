package com.dndtool.persistence;

import com.dndtool.service.CampaignArchiveDocument;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Rebuilds one complete campaign archive inside a caller-owned transaction. */
@FunctionalInterface
public interface CampaignArchiveImportRepository {
    /** Returns the local campaign id after a full create or full replacement. */
    long importArchive(Connection connection, Command command) throws SQLException;

    /** The optional key is present only when preview explicitly required archiving that campaign. */
    record Command(
            CampaignArchiveDocument document,
            String confirmedArchiveCampaignKey,
            long hostStateEpoch) {
        public Command {
            Objects.requireNonNull(document, "archive document");
            if (hostStateEpoch <= 0) {
                throw new IllegalArgumentException("Imported host state epoch must be positive");
            }
        }
    }

    enum Rejection {
        ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED,
        PREVIEW_STATE_CHANGED,
        UNEXPECTED_ARCHIVE_CONFIRMATION,
        STABLE_IDENTITY_CONFLICT
    }

    /** Stable business rejection raised before rebuilding the imported campaign. */
    final class RejectionException extends SQLException {
        private final Rejection rejection;

        public RejectionException(Rejection rejection) {
            super(Objects.requireNonNull(rejection, "archive import rejection").name());
            this.rejection = rejection;
        }

        public Rejection rejection() {
            return rejection;
        }
    }
}
