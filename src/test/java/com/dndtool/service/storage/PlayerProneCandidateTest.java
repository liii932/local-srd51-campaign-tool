package com.dndtool.service.storage;

import com.dndtool.service.Schema2CombatState.Battle;
import com.dndtool.service.Schema2CombatState.Combatant;
import com.dndtool.service.Schema2CombatState.Command;
import com.dndtool.service.Schema2CombatState.Kind;
import com.dndtool.service.Schema2CombatState.MovementStatus;
import com.dndtool.service.Schema2CombatState.Position;
import com.dndtool.service.Schema2CombatState.Status;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProneCandidateTest {
    private static final String DROP = "主动卧倒";
    private static final String CRAWL = "向南匍匐一格";
    private static final String STAND = "起身";

    @TempDir Path temporary;

    @Test void approvedControllerCanDropCrawlAndStandOnTheirTurn() throws Exception {
        var dice = new ArrayDeque<>(List.of(10, 20));
        try (var storage = new CampaignStorageService(temporary.resolve("player-prone"), ignored -> { }, sides -> {
            assertEquals(20, sides);
            assertFalse(dice.isEmpty(), "Unexpected initiative or action roll");
            return dice.remove();
        })) {
            CampaignRuleFixtures.create(storage, "玩家卧倒验收");
            var gateway = new PlayerGatewayService(storage);
            UUID fighter = character(storage, "战士", "PC"), guard = character(storage, "守卫", "NPC");
            String invitation = host(gateway, storage, "invite", new JsonObject()).get("invitation").getAsString();
            var anonymous = gateway.read(null, null, true);
            String application = anonymous.body().getAsJsonObject("session").get("csrf").getAsString();
            gateway.post(anonymous.sessionToken(), application, null, "join", fields("invitation", invitation, "nickname", "现场玩家"), null);
            host(gateway, storage, "approve", fields("applicationId", application, "memberId", ""));
            var approved = gateway.read(anonymous.sessionToken(), null, false);
            var session = approved.body().getAsJsonObject("session");
            assertEquals("APPROVED", session.get("status").getAsString());
            UUID member = UUID.fromString(session.get("playerKey").getAsString());
            host(gateway, storage, "control", fields("payload", fields("characterId", fighter, "memberId", member, "controlVersion", 0)));
            String token = approved.sessionToken(), csrf = session.get("csrf").getAsString();
            String connection = gateway.post(token, csrf, null, "takeover", new JsonObject(), null).body().get("connectionId").getAsString();

            PlayerSpaceFixture.beginBattle(storage, fighter, guard);
            assertTrue(dice.isEmpty());
            assertEquals(guard, battle(storage).activeActor());
            assertTrue(options(gateway.read(token, connection, false).body(), DROP).isEmpty());
            PlayerSpaceFixture.combat(storage, battle(storage), Command.action(Kind.END_TURN, guard));
            assertEquals(fighter, battle(storage).activeActor());

            var before = storage.accessSnapshot();
            var standing = combatant(storage, fighter);
            var view = gateway.read(token, connection, false).body();
            assertTrue(options(view, STAND).isEmpty());
            assertEquals(1, options(view, "向南移动一格").size());
            var drop = onlyOption(view, DROP);
            assertEquals(Set.of("id", "label", "characterId", "precondition"), drop.keySet());
            assertEquals(fighter.toString(), drop.get("characterId").getAsString());
            var dropped = submit(gateway, token, csrf, connection, request(connection, drop));
            assertTrue(options(dropped, DROP).isEmpty());
            assertTrue(effects(dropped).contains("倒地"));
            var prone = combatant(storage, fighter);
            assertTrue(prone.prone());
            assertEquals(standing.position(), prone.position());
            assertEquals(standing.actionAvailable(), prone.actionAvailable());
            assertEquals(standing.reactionAvailable(), prone.reactionAvailable());
            assertEquals(standing.movementSpentHalfFeet(), prone.movementSpentHalfFeet());
            var committed = storage.accessSnapshot();
            assertEquals(before.revision() + 1, committed.revision());
            var root = committed.snapshot().domains().roots().getLast();
            assertEquals(CampaignParticipationData.Actor.player(member, fighter), root.actor());
            var dropEvent = CampaignSpaceCommands.currentBattle(committed.snapshot().domains()).events().getLast();
            assertEquals(root.operationId(), dropEvent.operationId());
            assertEquals(Command.action(Kind.DROP_PRONE, fighter), dropEvent.command());

            assertTrue(options(dropped, "向南移动一格").isEmpty());
            var move = request(connection, onlyOption(dropped, CRAWL));
            var forged = move.deepCopy();
            forged.addProperty("movementCost", 5);
            assertEquals("PLAYER_INVALID_REQUEST", assertThrows(PlayerGatewayException.class,
                    () -> gateway.post(token, csrf, connection, "command", forged, null)).code());
            assertEquals(committed, storage.accessSnapshot());
            var crawled = submit(gateway, token, csrf, connection, move);
            var afterCrawl = combatant(storage, fighter);
            assertTrue(afterCrawl.prone());
            assertEquals(new Position(1, 1), afterCrawl.position());
            assertEquals(20, afterCrawl.movementSpentHalfFeet());
            var movement = battle(storage).flows().getLast().movements().getFirst();
            assertEquals(MovementStatus.COMPLETED, movement.status());
            assertEquals(5, movement.spent());

            var recovered = submit(gateway, token, csrf, connection, request(connection, onlyOption(crawled, STAND)));
            var afterStand = combatant(storage, fighter);
            assertFalse(afterStand.prone());
            assertEquals(50, afterStand.movementSpentHalfFeet());
            assertEquals(standing.actionAvailable(), afterStand.actionAvailable());
            assertEquals(standing.reactionAvailable(), afterStand.reactionAvailable());
            assertFalse(effects(recovered).contains("倒地"));
            assertTrue(options(recovered, CRAWL).isEmpty());
            assertEquals(1, options(recovered, "向南移动一格").size());
            assertTrue(options(recovered, STAND).isEmpty());

            PlayerSpaceFixture.combat(storage, battle(storage), Command.action(Kind.DROP_PRONE, fighter));
            PlayerSpaceFixture.combat(storage, battle(storage), Command.action(Kind.END_COMBAT, fighter));
            assertEquals(Status.EXPLORATION, battle(storage).status());
            assertTrue(combatant(storage, fighter).prone());
            var unauthorized = gateway.read(token, connection, false).body();
            for (String label : List.of(DROP, CRAWL, STAND, "向南移动一格"))
                assertTrue(options(unauthorized, label).isEmpty(), label);

            var scene = CampaignSpaceData.Ref.scene(battle(storage).combatId());
            var permission = fields("characterId", fighter, "permissionVersion", 0, "sceneId", scene.id());
            permission.addProperty("active", true);
            var actions = new JsonArray();
            actions.add("EXPLORATION_MOVE");
            permission.add("actions", actions);
            host(gateway, storage, "exploration", fields("payload", permission));
            var exploration = gateway.read(token, connection, false).body();
            assertTrue(options(exploration, DROP).isEmpty());
            assertEquals(1, options(exploration, CRAWL).size());
            assertTrue(options(exploration, "向南移动一格").isEmpty());
            var beforeExplorationCrawl = combatant(storage, fighter);
            long beforeCrawlSecond = storage.accessSnapshot().snapshot().domains().space().clock(scene).second();
            var explorationCrawl = onlyOption(exploration, CRAWL);
            assertEquals(Set.of("id", "label", "characterId", "precondition"), explorationCrawl.keySet());
            var crawledInExploration = submit(gateway, token, csrf, connection, request(connection, explorationCrawl));
            assertEquals(beforeCrawlSecond + 6, storage.accessSnapshot().snapshot().domains().space().clock(scene).second());
            assertEquals(new Position(1, 2), combatant(storage, fighter).position());
            assertTrue(combatant(storage, fighter).prone());
            assertEquals(beforeExplorationCrawl.movementSpentHalfFeet(), combatant(storage, fighter).movementSpentHalfFeet());
            assertEquals(Kind.EXPLORATION_MOVE, CampaignSpaceCommands.currentBattle(storage.accessSnapshot().snapshot().domains()).events().getLast().command().kind());
            var beforeExplorationStand = combatant(storage, fighter);
            long sceneSecond = storage.accessSnapshot().snapshot().domains().space().clock(scene).second();
            var explored = submit(gateway, token, csrf, connection, request(connection, onlyOption(crawledInExploration, STAND)));
            assertEquals(sceneSecond + 6, storage.accessSnapshot().snapshot().domains().space().clock(scene).second());
            assertFalse(combatant(storage, fighter).prone());
            assertEquals(beforeExplorationStand.movementSpentHalfFeet(), combatant(storage, fighter).movementSpentHalfFeet());
            assertFalse(effects(explored).contains("倒地"));
            assertEquals(1, options(explored, "向南移动一格").size());

            var beforeExplorationDrop = combatant(storage, fighter);
            long beforeDropSecond = storage.accessSnapshot().snapshot().domains().space().clock(scene).second();
            var droppedInExploration = submit(gateway, token, csrf, connection, request(connection, onlyOption(explored, DROP)));
            assertTrue(combatant(storage, fighter).prone());
            assertEquals(beforeDropSecond, storage.accessSnapshot().snapshot().domains().space().clock(scene).second());
            assertEquals(beforeExplorationDrop.position(), combatant(storage, fighter).position());
            assertEquals(beforeExplorationDrop.actionAvailable(), combatant(storage, fighter).actionAvailable());
            assertEquals(beforeExplorationDrop.reactionAvailable(), combatant(storage, fighter).reactionAvailable());
            assertEquals(beforeExplorationDrop.movementSpentHalfFeet(), combatant(storage, fighter).movementSpentHalfFeet());
            assertTrue(effects(droppedInExploration).contains("倒地"));
            assertTrue(options(droppedInExploration, DROP).isEmpty());
            assertEquals(CampaignParticipationData.Actor.player(member, fighter), storage.accessSnapshot().snapshot().domains().roots().getLast().actor());

            var staleStand = request(connection, onlyOption(droppedInExploration, STAND));
            permission.addProperty("permissionVersion", "1");
            permission.addProperty("active", false);
            host(gateway, storage, "exploration", fields("payload", permission));
            var revoked = storage.accessSnapshot();
            assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class,
                    () -> gateway.post(token, csrf, connection, "command", staleStand, null)).code());
            assertEquals(revoked, storage.accessSnapshot());
            var revokedView = gateway.read(token, connection, false).body();
            for (String label : List.of(DROP, CRAWL, STAND, "向南移动一格"))
                assertTrue(options(revokedView, label).isEmpty(), label);
            for (Kind kind : List.of(Kind.DROP_PRONE, Kind.EXPLORATION_MOVE, Kind.STAND_UP)) {
                var input = new JsonObject();
                var command = kind == Kind.EXPLORATION_MOVE ? Command.exploreMove(fighter, List.of(new Position(1, 3))) : Command.action(kind, fighter);
                input.add("command", CampaignDomainCodec.toJson(command));
                assertEquals("PLAYER_COMMAND_FORBIDDEN", assertThrows(StorageException.class,
                        () -> CampaignParticipationCommands.requireExecutionPermission(revoked.snapshot().domains(), "combat-command", input,
                                CampaignParticipationData.Actor.player(member, fighter))).code());
            }
        }
    }

    private static Battle battle(CampaignStorageService storage) {
        return CampaignSpaceCommands.currentBattle(storage.accessSnapshot().snapshot().domains()).current();
    }

    private static Combatant combatant(CampaignStorageService storage, UUID character) {
        return battle(storage).combatants().stream().filter(actor -> actor.characterId().equals(character)).findFirst().orElseThrow();
    }

    private static List<String> effects(JsonObject response) {
        return response.getAsJsonObject("view").getAsJsonArray("cards").get(0).getAsJsonObject()
                .getAsJsonArray("effects").asList().stream().map(effect -> effect.getAsJsonObject().get("label").getAsString()).toList();
    }

    private static List<JsonObject> options(JsonObject response, String label) {
        return response.getAsJsonObject("view").getAsJsonArray("candidates").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(option -> option.get("label").getAsString().equals(label)).toList();
    }

    private static JsonObject onlyOption(JsonObject response, String label) {
        var options = options(response, label);
        assertEquals(1, options.size(), label);
        return options.getFirst();
    }

    private static JsonObject request(String connection, JsonObject option) {
        return fields("connectionId", connection, "requestId", UUID.randomUUID(),
                "candidateId", option.get("id").getAsString(), "precondition", option.get("precondition").getAsString());
    }

    private static JsonObject submit(PlayerGatewayService gateway, String token, String csrf, String connection, JsonObject request) {
        var response = gateway.post(token, csrf, connection, "command", request, null).body();
        var receipt = response.getAsJsonObject("receipt");
        assertEquals("ACCEPTED", receipt.get("state").getAsString());
        assertEquals("COMPLETED", receipt.get("outcome").getAsString());
        return response;
    }

    private static UUID character(CampaignStorageService storage, String name, String kind) {
        var created = PlayerSpaceFixture.send(storage, "character-create", fields("name", name, "kind", kind,
                "classKey", "fighter", "backgroundKey", "acolyte"));
        return UUID.fromString(created.lastCommand().subjectId());
    }

    private static JsonObject host(PlayerGatewayService gateway, CampaignStorageService storage, String action, JsonObject input) {
        var access = storage.accessSnapshot();
        return gateway.hostCommand(action, input, UUID.randomUUID().toString(), access.epoch(), access.revision());
    }

    private static JsonObject fields(Object... pairs) {
        var result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) result.addProperty((String) pairs[i], pairs[i + 1].toString());
        return result;
    }
}
