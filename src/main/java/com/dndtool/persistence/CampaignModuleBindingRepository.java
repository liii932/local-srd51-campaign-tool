package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Read-only access to every campaign's single frozen module binding. */
public interface CampaignModuleBindingRepository {
    /**
     * Returns one projection per campaign. Nullable frozen fields deliberately preserve a broken
     * left join so the integrity service can fail closed instead of silently omitting the campaign.
     */
    List<Binding> findAll() throws SQLException;

    /** Resolves one campaign without inventing a default release identity. */
    default Optional<Binding> findByCampaignKey(String campaignKey) throws SQLException {
        Binding found = null;
        for (Binding binding : findAll()) {
            if (binding != null && campaignKey != null
                    && campaignKey.equals(binding.campaignKey())) {
                if (found != null) throw new SQLException("Duplicate campaign module binding");
                found = binding;
            }
        }
        return Optional.ofNullable(found);
    }

    record Binding(
            String campaignKey,
            String campaignStatus,
            String frozenModuleKey,
            String frozenReleaseVersion,
            String frozenContentSha256) {
    }
}
