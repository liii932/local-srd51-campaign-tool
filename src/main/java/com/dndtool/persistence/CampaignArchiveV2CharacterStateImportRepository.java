package com.dndtool.persistence;

import com.dndtool.module.ModuleCatalog;
import com.dndtool.service.CampaignArchiveV2CharacterState;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Caller-owned transaction boundary for restoring the format-2 DRAFT character projection. */
@FunctionalInterface
public interface CampaignArchiveV2CharacterStateImportRepository {
    void append(Connection connection, Command command) throws SQLException;

    /**
     * The campaign initializer owns the authoritative event tail and must restore it before append.
     * catalog must already have reliable complete-content validation; repository header checks
     * prevent identity drift and do not certify supplied catalog contents.
     */
    record Command(
            long campaignId,
            long moduleReleaseId,
            Map<String, Long> characterIds,
            CampaignArchiveV2CharacterState state,
            ModuleCatalog catalog) {
        public Command {
            characterIds = Map.copyOf(characterIds);
            if (campaignId <= 0 || moduleReleaseId <= 0 || state == null || catalog == null
                    || characterIds.isEmpty()
                    || characterIds.values().stream().anyMatch(id -> id == null || id <= 0)) {
                throw new IllegalArgumentException("Invalid format-2 character import command");
            }
        }
    }
}
