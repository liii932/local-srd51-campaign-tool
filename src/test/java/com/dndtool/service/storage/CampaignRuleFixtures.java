package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import java.util.List;
import java.util.UUID;

/** Explicit package import and DM acceptance for current-model integration fixtures. */
public final class CampaignRuleFixtures {
    private static final CampaignRuleContext RULES = CampaignDomainData.Material.of(BundledCampaignRules.projection()).restore();
    private CampaignRuleFixtures() { }
    public static CampaignRuleContext rules() { return RULES; }
    public static List<StoredMember> install(CampaignStorageService service) { return install(service, BundledCampaignRules.STANDARD); }
    public static List<StoredMember> install(CampaignStorageService service, String combination) {
        var library = service.ruleLibrary();
        return BundledCampaignRules.artifacts(combination).stream().map(artifact -> {
            var member = library.importArtifact(UUID.randomUUID().toString(), artifact.originalZip()).imported().member();
            library.accept(UUID.randomUUID().toString(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
            return member;
        }).toList();
    }
    public static CampaignStorageService.CampaignView create(CampaignStorageService service, String name) {
        return create(service, name, BundledCampaignRules.STANDARD);
    }
    public static CampaignStorageService.CampaignView create(CampaignStorageService service, String name, String combination) {
        return service.create(name, install(service, combination));
    }
    public static CampaignData.Snapshot snapshot(String id, String name) {
        return new CampaignData.Snapshot(id, name, CampaignDomainData.State.initial(rules().projection()));
    }
}
