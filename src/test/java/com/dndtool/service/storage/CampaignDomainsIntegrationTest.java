package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignDomainsIntegrationTest {
    @TempDir Path temporary;
    static final List<String> FULL = List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.RUNTIME);
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static JsonObject input(String... pairs) {
        JsonObject object = new JsonObject(); for (int i = 0; i < pairs.length; i += 2) object.addProperty(pairs[i], pairs[i + 1]); return object;
    }
    private static CampaignStorageService.CampaignView command(CampaignStorageService service, String action, JsonObject payload) {
        var current = service.current(); return service.domainCommand(uuid(), current.epoch(), current.revision(), action, payload);
    }
    private static JsonObject first(CampaignStorageService service) { return service.current().domain().getAsJsonArray("characters").get(0).getAsJsonObject(); }
    private static void createCharacter(CampaignStorageService service, String name, String profession) {
        command(service, "character-create", input("name", name, "kind", "PC", "classKey", profession, "backgroundKey", "acolyte"));
    }
    private static JsonObject characterCommand(CampaignStorageService service, Command command) {
        JsonObject character = first(service); JsonObject request = input("characterId", character.get("id").getAsString(), "characterVersion", character.get("version").getAsString());
        request.add("command", CampaignDomainCodec.toJson(command)); return request;
    }
    @Test void allDomainsUseSelectedSchemaAndWholeFileReopen() throws Exception {
        Path root = temporary.resolve("all-domains"); String file;
        try (CampaignStorageService service = new CampaignStorageService(root)) {
            service.create("远征", FULL); createCharacter(service, "战士", "fighter"); createCharacter(service, "牧师", "cleric");
            command(service, "note", input("text", "共同历史"));
            command(service, "node-create", input("name", "营地"));
            String node = service.current().domain().getAsJsonArray("nodes").get(0).getAsJsonObject().get("id").getAsString();
            String character = first(service).get("id").getAsString();
            command(service, "position", input("characterId", character, "nodeId", node));
            JsonObject item = input("characterId", character, "name", "绳索", "quantity", "2"); item.add("itemId", JsonNull.INSTANCE);
            command(service, "item", item);
            var state = service.current();
            assertEquals(2, state.domain().getAsJsonArray("characters").size()); assertEquals(6, state.history().size());
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("runs").resolve(service.status().runId()).resolve("run.db")); Statement s = c.createStatement()) {
                for (String table : List.of("characters_runtime_character", "resources_character", "equipment_instance", "spellcasting_book", "combat_scene", "actions_frame", "effects_instance", "monsters_instance", "history_runtime_root"))
                    assertDoesNotThrow(() -> { try (ResultSet ignored = s.executeQuery("SELECT COUNT(*) FROM " + table)) { assertTrue(ignored.next()); } });
            }
            file = service.end(uuid(), state.epoch(), state.revision()).fileKey();
            var restored = service.open(file);
            assertEquals(state.domain(), restored.domain()); assertEquals(state.history(), restored.history()); assertEquals(0, restored.revision());
        }
        try (CampaignStorageService restarted = new CampaignStorageService(root)) {
            assertEquals("RESIDUAL", restarted.status().state());
            var recovered = restarted.recover(restarted.status().residualRunIds().getFirst());
            assertEquals(6, recovered.history().size()); assertEquals(2, recovered.domain().getAsJsonArray("characters").size());
        }
    }
    @Test void retryStaleVersionAndRollbackDoNotDuplicateFacts() throws Exception {
        Path root = temporary.resolve("retry");
        try (CampaignStorageService service = new CampaignStorageService(root)) {
            service.create("重试", FULL); createCharacter(service, "法师", "wizard");
            var view = service.current(); String request = uuid(); JsonObject note = input("text", "只追加一次");
            var accepted = service.domainCommand(request, view.epoch(), view.revision(), "note", note);
            var replay = service.domainCommand(request, view.epoch(), view.revision(), "note", note);
            assertTrue(replay.lastCommand().replayed()); assertEquals(accepted.revision(), replay.revision());
            assertEquals("STALE_REVISION", assertThrows(StorageException.class, () -> service.domainCommand(uuid(), view.epoch(), view.revision(), "note", note)).code());
            assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(StorageException.class, () -> service.domainCommand(request, view.epoch(), view.revision(), "note", input("text", "不同意图"))).code());
            assertEquals(2, service.current().history().size());
        }
        boolean[] fail = {false};
        try (CampaignStorageService service = new CampaignStorageService(root, boundary -> { if (fail[0] && boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE) throw new IllegalStateException("injected"); })) {
            service.recover(service.status().residualRunIds().getFirst()); var before = service.current(); fail[0] = true;
            assertThrows(RuntimeException.class, () -> command(service, "note", input("text", "必须回滚")));
            fail[0] = false; assertEquals(before.domain(), service.current().domain()); assertEquals(before.revision(), service.current().revision());
        }
    }
    @Test void equipmentCheckAndGrowthShareOneCharacterAndPreserveOriginalDice() throws Exception {
        try (CampaignStorageService service = new CampaignStorageService(temporary.resolve("character"))) {
            service.create("角色业务", FULL); createCharacter(service, "战士", "fighter");
            var item = new EquipmentInput(UUID.randomUUID(), BundledCampaignRules.rule("equipment.item", "item.longsword"), "CARRIED", "DM确认装备来源");
            var acquire = new Command("ITEM_ACQUIRED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, item, null);
            command(service, "character-command", characterCommand(service, acquire));
            assertEquals(1, first(service).getAsJsonObject("state").getAsJsonArray("equipment").size());
            var check = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                    new CheckInput(BundledCampaignRules.rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 10, true, false, "DM确认检定环境"));
            command(service, "character-command", characterCommand(service, check));
            JsonObject result = first(service).getAsJsonObject("history").getAsJsonArray("events").asList().getLast().getAsJsonObject().getAsJsonObject("checkResult");
            assertTrue(result.get("firstD20").getAsInt() >= 1); assertNotNull(result.get("secondD20"));
            var advance = new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", BundledCampaignRules.rule("character.class", "class.fighter"), GrowthChoices.EMPTY);
            command(service, "character-command", characterCommand(service, advance));
            assertEquals(2, first(service).get("level").getAsInt());
            var before = service.current(); String saved = service.end(uuid(), before.epoch(), before.revision()).fileKey(); service.open(saved);
            assertEquals(before.domain(), service.current().domain());
        }
    }
    @Test void typedDocumentsRejectUnknownFieldsPolymorphicTypesAndBadProjections() throws Exception {
        var material = CampaignDomainData.Material.of(BundledCampaignRules.projection());
        JsonObject encoded = CampaignDomainCodec.toJson(material).getAsJsonObject();
        encoded.addProperty("unexpected", "x"); assertThrows(StorageException.class, () -> CampaignDomainCodec.fromJson(encoded, CampaignDomainData.Material.class));
        encoded.remove("unexpected"); encoded.addProperty("type", "java.lang.Runtime"); assertThrows(StorageException.class, () -> CampaignDomainCodec.fromJson(encoded, CampaignDomainData.Material.class));
        Path root = temporary.resolve("tamper"); String run;
        try (CampaignStorageService service = new CampaignStorageService(root)) { service.create("校验", FULL); createCharacter(service, "战士", "fighter"); run = service.status().runId(); }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("runs").resolve(run).resolve("run.db")); Statement s = c.createStatement()) { s.executeUpdate("UPDATE characters_runtime_character SET hit_points=999"); }
        try (CampaignStorageService service = new CampaignStorageService(root)) { assertThrows(StorageException.class, () -> service.recover(run)); }
    }
}
