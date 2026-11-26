package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.dndtool.service.storage.BundledCampaignRules.CHARACTER_MODULE;
import static com.dndtool.service.storage.BundledCampaignRules.CORE_MODULE;

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
    static CampaignStorageService.CampaignView createWithArmorMovement(CampaignStorageService service, String name,
            int dwarfWalkSpeed, String dwarfPolicy, String humanPolicy, int penaltyFeet, int plateStrengthMinimum) throws IOException {
        var base = BundledCampaignRules.artifacts(BundledCampaignRules.STANDARD);
        var changes = new ArrayList<JsonObject>();
        override(changes, CORE_MODULE, "movement.dwarf", "walk_speed_feet", new JsonPrimitive(dwarfWalkSpeed));
        override(changes, CORE_MODULE, "movement.dwarf", "heavy_armor_speed_policy", new JsonPrimitive(dwarfPolicy));
        override(changes, CORE_MODULE, "movement.human", "heavy_armor_speed_policy", new JsonPrimitive(humanPolicy));
        override(changes, CHARACTER_MODULE, "armor_movement.heavy_armor", "heavy_armor_speed_penalty_feet", new JsonPrimitive(penaltyFeet));
        override(changes, CHARACTER_MODULE, "item.plate", "strength_minimum", new JsonPrimitive(plateStrengthMinimum));
        var extension = CampaignRuleSupportTest.extension("test_armor_movement", base, List.of(), List.of(), changes);
        var members = new ArrayList<>(install(service));
        var library = service.ruleLibrary();
        var member = library.importArtifact(UUID.randomUUID().toString(), extension.originalZip()).imported().member();
        library.accept(UUID.randomUUID().toString(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
        members.add(member);
        return service.create(name, members);
    }
    private static void override(List<JsonObject> changes, String module, String key, String field, JsonPrimitive value) {
        var row = CampaignRuleSupportTest.authorRow(module, "rules.json", "rule_key", key);
        if (value.equals(row.get(field))) return;
        var change = CampaignRuleSupportTest.operation("set_field", row);
        change.addProperty("field", field);
        change.add("value", value);
        changes.add(change);
    }

    public static CampaignData.Snapshot snapshot(String id, String name) {
        return new CampaignData.Snapshot(id, name, CampaignDomainData.State.initial(rules().projection()));
    }
}
