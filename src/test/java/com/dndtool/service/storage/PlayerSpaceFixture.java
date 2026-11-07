package com.dndtool.service.storage;

import com.dndtool.service.Schema2CombatState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;

/** Arranges real placement and focus for player entry-point tests. */
public final class PlayerSpaceFixture {
    private PlayerSpaceFixture() { }

    public static CampaignStorageService.CampaignView send(CampaignStorageService service, String action, JsonObject input) {
        var access = service.accessSnapshot();
        input.add("focus", CampaignSpaceCommands.focusPayload(access.snapshot().domains()));
        return service.domainCommand(UUID.randomUUID().toString(), access.epoch(), access.revision(), action, input);
    }

    public static UUID node(CampaignStorageService service, String name) {
        send(service, "node-create", json("name", name));
        return service.accessSnapshot().snapshot().domains().nodes().stream()
                .filter(node -> node.name().equals(name)).findFirst().orElseThrow().id();
    }

    public static void focus(CampaignStorageService service, CampaignSpaceData.Ref target) {
        var state = service.accessSnapshot().snapshot().domains();
        if (target.equals(state.space().focus().space())) return;
        var payload = new JsonObject();
        payload.add("target", CampaignSpaceCommands.refJson(target));
        send(service, "focus-set", payload);
    }

    public static UUID placeAtWorld(CampaignStorageService service, UUID... actors) {
        var state = service.accessSnapshot().snapshot().domains();
        UUID node = state.nodes().isEmpty() ? node(service, "营地") : state.nodes().getFirst().id();
        focus(service, CampaignSpaceData.Ref.world(node));
        for (UUID actor : actors) {
            if (CampaignSpaceCommands.position(service.accessSnapshot().snapshot().domains(), actor) == null)
                send(service, "position", json("characterId", actor.toString(), "nodeId", node.toString()));
        }
        return node;
    }

    static void beginBattle(CampaignStorageService service, UUID pc, UUID npc) {
        UUID anchor = placeAtWorld(service, pc, npc);
        var created = send(service, "scene-create", json("name", "演练场", "nodeId", anchor.toString(),
                "width", "10", "height", "10", "resolution", "RULES"));
        String scene = created.lastCommand().requestId();
        JsonArray subjects = new JsonArray();
        for (UUID actor : List.of(pc, npc)) {
            var place = CampaignSpaceCommands.position(service.accessSnapshot().snapshot().domains(), actor);
            subjects.add(json("id", actor.toString(), "version", Long.toString(place.version()),
                    "x", actor.equals(pc) ? "1" : "2", "y", "0", "side", actor.equals(pc) ? "0" : "1"));
        }
        JsonObject input = json("sceneId", scene); input.add("subjects", subjects);
        send(service, "scene-enter", input);
        var battle = CampaignSpaceCommands.currentBattle(service.accessSnapshot().snapshot().domains()).current();
        combat(service, battle, Schema2CombatState.Command.reenter(pc, List.of(pc, npc)));
    }

    static void combat(CampaignStorageService service, Schema2CombatState.Battle battle, Schema2CombatState.Command command) {
        var payload = json("combatId", battle.combatId().toString(), "combatVersion", Long.toString(battle.version()));
        payload.add("command", CampaignDomainCodec.toJson(command));
        send(service, "combat-command", payload);
    }

    private static JsonObject json(String... fields) {
        var result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) result.addProperty(fields[i], fields[i + 1]);
        return result;
    }
}
