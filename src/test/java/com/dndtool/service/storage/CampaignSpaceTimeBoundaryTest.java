package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CampaignSpaceTimeBoundaryTest {
    @TempDir Path temporary;

    @Test void reenteringTheSameWorldNeverReusesAPlacementVersion() {
        var fixture = new Fixture();
        UUID node = fixture.node("营地"); fixture.focus(node);
        fixture.send("manual-create", object("name", "向导", "kind", "NPC", "template", "blank"));
        UUID character = fixture.state().manual().characters().getFirst().id();
        fixture.send("position", object("characterId", character, "nodeId", node));
        fixture.send("space-unplace", object("characterId", character, "version", 0, "adjudication", "离开本次冒险"));
        fixture.send("position", object("characterId", character, "nodeId", node));
        assertEquals(1, CampaignSpaceCommands.position(fixture.state(), character).version());
        var before = fixture.snapshot;
        assertEquals("STALE_POSITION", assertThrows(StorageException.class, () -> fixture.send("space-unplace",
                object("characterId", character, "version", 0, "adjudication", "过期提交"))).code());
        assertEquals(before, fixture.snapshot);
    }

    @Test void stableRecoveryCarriesRemainingTimeAcrossOlderAndNewerClocks() {
        var fixture = new Fixture();
        UUID source = fixture.node("旧营地"), destination = fixture.node("先行营地");
        fixture.focus(destination);
        fixture.time(500);
        fixture.focus(source);
        fixture.send("node-link", object("from", source, "to", destination));
        fixture.send("character-create", object("name", "伤员", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID character = fixture.state().characters().getFirst().id();
        fixture.send("position", object("characterId", character, "nodeId", source));
        fixture.time(100);
        var rules = CampaignRuleFixtures.rules();
        var damage = new HealthInput(rules.rule("adventure.health", "health.srd2014"),
                rules.rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "DM裁定落石伤害");
        fixture.character(character, health("HEALTH_EFFECT_RESOLVED", damage));
        fixture.character(character, health("HEALTH_EFFECT_RESOLVED", damage));
        assertEquals(0, fixture.character().hitPoints());
        var death = new HealthInput(damage.rule(), null, null, "DM确认死亡豁免");
        for (int i = 0; i < 3; i++) fixture.character(character, health("DEATH_SAVE_RESOLVED", death));
        assertTrue(fixture.character().health().stable());
        assertEquals(160, fixture.character().health().stableRecoveryMinute());

        JsonObject travel = object("targetNodeId", destination, "minutes", 10, "adjudication", "同伴护送到先行营地");
        JsonArray selection = new JsonArray(); selection.add(object("id", character, "version", 0)); travel.add("subjects", selection);
        fixture.send("travel", travel);
        assertEquals(110, fixture.character().rest().minute());
        assertEquals(500 * 60, fixture.state().space().clock(CampaignSpaceData.Ref.world(destination)).second());
        assertEquals(160, fixture.character().health().stableRecoveryMinute());
        var saved = new CampaignCodec().encode(new CampaignData.Document(UUID.randomUUID().toString(), fixture.snapshot));
        assertEquals(fixture.snapshot, new CampaignCodec().decode(saved).snapshot());

        fixture.focus(source); fixture.time(20);
        assertEquals(110, fixture.character().rest().minute());
        fixture.focus(destination); fixture.time(49);
        assertEquals(0, fixture.character().hitPoints());
        fixture.time(1);
        assertEquals(1, fixture.character().hitPoints());
        assertFalse(fixture.character().health().stable());
        assertEquals(160, fixture.character().rest().minute());
    }

    @Test void spatialMutationRollsBackAndDamagedClockProjectionNeverRepairsItself() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        Path root = temporary.resolve("spatial-projection");
        try (var service = new CampaignStorageService(root, boundary -> {
            if (fail.get() && boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE)
                throw new IllegalStateException("injected spatial write failure");
        })) {
            CampaignRuleFixtures.create(service, "空间原子性");
            UUID node = CampaignSpaceFixtures.world(service, "营地");
            var before = service.current(); fail.set(true);
            assertThrows(IllegalStateException.class, () -> CampaignSpaceFixtures.command(service, "space-time",
                    object("minutes", 1, "adjudication", "等待一分钟")));
            fail.set(false);
            assertEquals(before.domain(), service.current().domain());
            assertEquals(before.revision(), service.current().revision());
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("runs").resolve(service.status().runId()).resolve("run.db"));
                 var statement = connection.createStatement()) {
                assertEquals(1, statement.executeUpdate("UPDATE scenes_clock SET second=1"));
            }
            assertThrows(StorageException.class, service::current);
            assertEquals("BLOCKED", service.status().state());
        }
    }

    @Test void coherentReplacementFactsStillCannotChangeTheAcceptedRequest() throws Exception {
        Path root = temporary.resolve("receipt-request");
        try (var service = new CampaignStorageService(root)) {
            CampaignRuleFixtures.create(service, "准确请求");
            var initial = service.accessSnapshot().snapshot();
            CampaignSpaceFixtures.command(service, "manual-create", object("name", "原卡", "kind", "NPC", "template", "blank"));
            var accepted = service.accessSnapshot().snapshot().domains().roots().getFirst();
            var replacement = new CampaignDomainCommands().apply(initial, "manual-create",
                    CampaignSpaceFixtures.focused(initial.domains(), object("name", "伪造卡", "kind", "NPC", "template", "blank")),
                    accepted.operationId(), accepted.intent(), sides -> { throw new AssertionError(); }, accepted.actor());
            CampaignData.validate(new CampaignData.Snapshot(initial.campaignId(), initial.name(), replacement));
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("runs").resolve(service.status().runId()).resolve("run.db"))) {
                connection.setAutoCommit(false);
                new com.dndtool.persistence.storage.CampaignDomainRepository().write(connection, replacement);
                connection.commit();
            }
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, service::current).code());
        }
    }

    private static Command health(String kind, HealthInput input) {
        return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null, input);
    }
    private static JsonObject object(Object... fields) {
        JsonObject input = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) input.addProperty(fields[i].toString(), fields[i + 1].toString());
        return input;
    }
    private static final class Fixture {
        CampaignData.Snapshot snapshot = CampaignRuleFixtures.snapshot(UUID.randomUUID().toString(), "个人恢复时间");
        CampaignDomainData.State state() { return snapshot.domains(); }
        Schema2CharacterRuntime.State character() { return state().characters().getFirst().history().current(); }
        void send(String action, JsonObject input) {
            var state = new CampaignDomainCommands().apply(snapshot, action, CampaignSpaceFixtures.focused(state(), input),
                    UUID.randomUUID(), "0".repeat(64), sides -> sides == 20 ? 10 : 1);
            snapshot = CampaignData.validate(new CampaignData.Snapshot(snapshot.campaignId(), snapshot.name(), state));
        }
        UUID node(String name) { send("node-create", object("name", name)); return state().roots().getLast().subjectId(); }
        void focus(UUID node) { send("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(node))); }
        void time(int minutes) { send("space-time", object("minutes", minutes, "adjudication", "DM裁定经过时间")); }
        void character(UUID id, Command command) {
            var input = object("characterId", id, "characterVersion", character().version());
            input.add("command", CampaignDomainCodec.toJson(command)); send("character-command", input);
        }
    }
}
