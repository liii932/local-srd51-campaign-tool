package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Read-only database state needed to decide the effect of an archive preview. */
public interface CampaignArchivePreviewRepository {
    Snapshot inspect(String targetCampaignKey) throws SQLException;

    record CampaignState(String campaignKey, String campaignName, String campaignStatus) {
        public CampaignState {
            Objects.requireNonNull(campaignKey, "campaign key");
            Objects.requireNonNull(campaignName, "campaign name");
            try {
                UUID uuid = UUID.fromString(campaignKey);
                if (!uuid.toString().equals(campaignKey)
                        || uuid.version() != 4 || uuid.variant() != 2) {
                    throw new IllegalArgumentException("Invalid campaign key");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid campaign key", exception);
            }
            if (campaignName.isBlank()
                    || !Set.of("ACTIVE", "ARCHIVED").contains(campaignStatus)) {
                throw new IllegalArgumentException("Invalid campaign state");
            }
        }
    }

    /** Target is nullable when the imported stable key is new; active is nullable when absent. */
    record Snapshot(CampaignState target, CampaignState active) {
        public Snapshot {
            if (active != null && !"ACTIVE".equals(active.campaignStatus())) {
                throw new IllegalArgumentException("Active campaign state is not active");
            }
            if (target != null && "ACTIVE".equals(target.campaignStatus())
                    && (active == null
                            || !target.campaignKey().equals(active.campaignKey()))) {
                throw new IllegalArgumentException("Active target is missing from active state");
            }
        }
    }
}
