package com.dndtool.service.storage;

import com.google.gson.JsonObject;
import java.util.UUID;

final class CampaignSpaceFixtures {
    private CampaignSpaceFixtures() { }

    static JsonObject focused(CampaignDomainData.State state, JsonObject input) {
        var result = input.deepCopy();
        result.add("focus", CampaignSpaceCommands.focusPayload(state));
        return result;
    }

    static JsonObject target(CampaignSpaceData.Ref ref) {
        var result = new JsonObject();
        result.add("target", CampaignSpaceCommands.refJson(ref));
        return result;
    }

    static CampaignStorageService.CampaignView command(CampaignStorageService service, String action, JsonObject input) {
        return command(service, UUID.randomUUID().toString(), action, input);
    }

    static CampaignStorageService.CampaignView command(CampaignStorageService service, String request, String action, JsonObject input) {
        var current = service.accessSnapshot();
        return service.domainCommand(request, current.epoch(), current.revision(), action, focused(current.snapshot().domains(), input));
    }

    static UUID world(CampaignStorageService service, String name) {
        var input = new JsonObject();
        input.addProperty("name", name);
        command(service, "node-create", input);
        UUID world = service.accessSnapshot().snapshot().domains().roots().getLast().subjectId();
        command(service, "focus-set", target(CampaignSpaceData.Ref.world(world)));
        return world;
    }

    static void place(CampaignStorageService service, UUID subject, UUID world) {
        var input = new JsonObject();
        input.addProperty("characterId", subject.toString());
        input.addProperty("nodeId", world.toString());
        command(service, "position", input);
    }
}
