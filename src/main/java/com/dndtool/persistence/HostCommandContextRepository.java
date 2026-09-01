package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.Optional;

/** Resolves the single active campaign to server-only ids used by host commands. */
public interface HostCommandContextRepository {
    Optional<Context> findActive() throws SQLException;

    record Context(
            long campaignId,
            String campaignKey,
            long moduleReleaseId,
            String frozenModuleKey,
            String frozenReleaseVersion,
            String frozenContentSha256,
            String releaseModuleKey,
            String releaseVersion,
            String releaseContentSha256,
            String releaseStatus) {
    }
}
