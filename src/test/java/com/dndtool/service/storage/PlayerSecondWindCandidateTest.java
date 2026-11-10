package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.dndtool.service.Schema2CombatState.Command;
import com.dndtool.service.Schema2CombatState.Kind;
import com.dndtool.service.Schema2CombatState.Roll;
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

class PlayerSecondWindCandidateTest {
    private static final String LABEL = "回气（附赠动作）";
    @TempDir Path temporary;

    @Test void approvedControllerCanUseSecondWindOnceAndVerifyTheOriginalReceipt() throws Exception {
        var dice = new ArrayDeque<CampaignSpaceData.Die>();
        try (var storage = new CampaignStorageService(temporary.resolve("player-second-wind"), ignored -> { }, sides -> {
            var roll = dice.poll();
            assertNotNull(roll, "Unexpected live d" + sides);
            assertEquals(roll.sides(), sides);
            return roll.value();
        })) {
            CampaignRuleFixtures.create(storage, "玩家回气验收");
            var gateway = new PlayerGatewayService(storage);
            UUID fighter = character(storage, "战士", "PC"), guard = character(storage, "守卫", "NPC");
            var invitation = host(gateway, storage, "invite", new JsonObject()).get("invitation").getAsString();
            var anonymous = gateway.read(null, null, true);
            gateway.post(anonymous.sessionToken(), csrf(anonymous), null, "join", fields("invitation", invitation, "nickname", "现场玩家"), null);
            host(gateway, storage, "approve", fields("applicationId", csrf(anonymous), "memberId", ""));
            var approved = gateway.read(anonymous.sessionToken(), null, false);
            var session = approved.body().getAsJsonObject("session");
            assertEquals("APPROVED", session.get("status").getAsString());
            UUID member = UUID.fromString(session.get("playerKey").getAsString());
            host(gateway, storage, "control", fields("payload", fields("characterId", fighter, "memberId", member, "controlVersion", 0)));
            String token = approved.sessionToken(), csrf = csrf(approved);
            String connection = gateway.post(token, csrf, null, "takeover", new JsonObject(), null).body().get("connectionId").getAsString();

            PlayerSpaceFixture.placeAtWorld(storage, fighter, guard);
            var initial = card(storage.accessSnapshot(), fighter);
            assertTrue(initial.features().stream().anyMatch(feature -> feature.feature().ruleKey().equals("feature.fighter_second_wind")
                    && feature.acquiredAtClassLevel() == 1));
            assertEquals(1, uses(initial));
            var rules = CampaignRuleFixtures.rules();
            var health = new HealthInput(rules.rule("adventure.health", "health.srd2014"),
                    rules.rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "主持人确认环境伤害");
            var wound = fields("characterId", fighter, "characterVersion", initial.version());
            wound.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command("HEALTH_EFFECT_RESOLVED",
                    null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null, health)));
            PlayerSpaceFixture.send(storage, "character-command", wound);
            dice.addAll(List.of(new CampaignSpaceData.Die(20, 10), new CampaignSpaceData.Die(20, 20)));
            PlayerSpaceFixture.beginBattle(storage, fighter, guard);
            assertTrue(dice.isEmpty());
            var battle = CampaignSpaceCommands.currentBattle(storage.accessSnapshot().snapshot().domains()).current();
            assertEquals(guard, battle.activeActor());
            assertTrue(windOptions(gateway.read(token, connection, false).body()).isEmpty());
            PlayerSpaceFixture.combat(storage, battle, Command.action(Kind.END_TURN, guard));

            var before = storage.accessSnapshot();
            var playerView = gateway.read(token, connection, false).body();
            var available = windOptions(playerView);
            assertEquals(1, available.size());
            var option = available.getFirst();
            assertEquals(Set.of("id", "label", "characterId", "precondition"), option.keySet());
            assertEquals(fighter.toString(), option.get("characterId").getAsString());
            var bound = PlayerCandidateFactory.candidates(before, member).stream().filter(candidate -> candidate.label().equals(LABEL)).findFirst().orElseThrow();
            assertEquals("combat-command", bound.action());
            assertEquals(CampaignDomainCodec.toJson(Command.action(Kind.SECOND_WIND, fighter)), bound.input().get("command"));

            var receiptIdentity = fields("requestId", UUID.randomUUID(), "candidateId", option.get("id").getAsString(),
                    "precondition", option.get("precondition").getAsString());
            var request = receiptIdentity.deepCopy();
            request.addProperty("connectionId", connection);
            var forged = request.deepCopy();
            forged.addProperty("die", "10");
            forged.addProperty("amount", "0");
            assertEquals("PLAYER_INVALID_REQUEST", assertThrows(PlayerGatewayException.class,
                    () -> gateway.post(token, csrf, connection, "command", forged, null)).code());
            assertEquals(before, storage.accessSnapshot());

            dice.add(new CampaignSpaceData.Die(10, 3));
            var accepted = gateway.post(token, csrf, connection, "command", request, null).body();
            assertTrue(dice.isEmpty());
            assertTrue(windOptions(accepted).isEmpty());
            var receipt = accepted.getAsJsonObject("receipt");
            assertEquals("ACCEPTED", receipt.get("state").getAsString());
            assertEquals("COMPLETED", receipt.get("outcome").getAsString());
            var committed = storage.accessSnapshot();
            assertEquals(before.revision() + 1, committed.revision());
            var current = card(committed, fighter);
            assertEquals(card(before, fighter).hitPoints() + 4, current.hitPoints());
            assertEquals(0, uses(current));
            var root = committed.snapshot().domains().roots().getLast();
            assertEquals(CampaignParticipationData.Actor.player(member, fighter), root.actor());
            var event = CampaignSpaceCommands.currentBattle(committed.snapshot().domains()).events().getLast();
            assertEquals(root.operationId(), event.operationId());
            assertEquals(List.of(new Roll("SECOND_WIND_HEALING", fighter, 10, 3)), event.resolution().rolls());

            assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class,
                    () -> gateway.post(token, csrf, connection, "command", request, null)).code());
            var verified = gateway.post(token, csrf, connection, "receipt", receiptIdentity, null).body();
            assertEquals(receipt, verified.getAsJsonObject("receipt"));
            assertEquals(committed, storage.accessSnapshot());
        }
    }

    private static List<JsonObject> windOptions(JsonObject response) {
        return response.getAsJsonObject("view").getAsJsonArray("candidates").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(option -> option.get("label").getAsString().equals(LABEL)).toList();
    }

    private static long uses(Schema2CharacterRuntime.State state) {
        return state.resources().stream().filter(resource -> resource.identity().ruleKey().equals("resource.fighter.second_wind"))
                .findFirst().orElseThrow().current();
    }

    private static Schema2CharacterRuntime.State card(CampaignStorageService.AccessSnapshot access, UUID character) {
        return access.snapshot().domains().characters().stream().filter(row -> row.id().equals(character)).findFirst().orElseThrow().history().current();
    }

    private static UUID character(CampaignStorageService storage, String name, String kind) {
        var created = PlayerSpaceFixture.send(storage, "character-create", fields("name", name, "kind", kind, "classKey", "fighter", "backgroundKey", "acolyte"));
        return UUID.fromString(created.lastCommand().subjectId());
    }

    private static JsonObject host(PlayerGatewayService gateway, CampaignStorageService storage, String action, JsonObject input) {
        var current = storage.accessSnapshot();
        return gateway.hostCommand(action, input, UUID.randomUUID().toString(), current.epoch(), current.revision());
    }

    private static String csrf(PlayerGatewayService.Reply reply) { return reply.body().getAsJsonObject("session").get("csrf").getAsString(); }

    private static JsonObject fields(Object... pairs) {
        var result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) result.addProperty((String) pairs[i], pairs[i + 1].toString());
        return result;
    }
}
