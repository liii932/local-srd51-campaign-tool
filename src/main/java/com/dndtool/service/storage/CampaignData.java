package com.dndtool.service.storage;

import java.util.List;

/** Portable campaign facts. Local execution identities and acceptance never enter this graph. */
public final class CampaignData {
    private CampaignData() { }

    public record Snapshot(String campaignId, String name, CampaignDomainData.State domains) {
        public Snapshot {
            if (domains == null) throw CampaignJson.bad("Complete campaign domains are required");
        }
        public List<CampaignDomainData.Root> history() { return domains.roots(); }
    }
    public record Document(String fileKey, Snapshot snapshot) { }

    public static Snapshot validate(Snapshot snapshot) {
        CampaignJson.uuid(snapshot.campaignId());
        CampaignJson.name(snapshot.name());
        return CampaignDomainValidation.validate(snapshot);
    }
}
