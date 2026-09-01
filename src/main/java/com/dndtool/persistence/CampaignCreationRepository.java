package com.dndtool.persistence;

import java.sql.SQLException;

/** Atomic persistence boundary for creating one campaign and freezing its module release. */
public interface CampaignCreationRepository {
    /**
     * Creates the campaign, its sole campaign_module row, and the successful idempotency record.
     * The implementation must lock and revalidate the release metadata inside the same transaction.
     */
    Result create(Command command) throws SQLException;

    record Command(
            String requestId,
            String requestDigestSha256,
            String campaignKey,
            String campaignName,
            String moduleKey,
            String releaseVersion,
            String contentSha256) {
    }

    record Result(Status status, String campaignKey) {
        public enum Status {
            CREATED,
            ALREADY_SUCCEEDED,
            IDEMPOTENCY_CONFLICT,
            ACTIVE_CAMPAIGN_EXISTS,
            RELEASE_UNAVAILABLE
        }
    }
}
