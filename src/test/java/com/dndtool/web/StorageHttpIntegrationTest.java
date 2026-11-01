package com.dndtool.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.dndtool.service.storage.CampaignCodec;
import com.dndtool.service.storage.CampaignJson;
import com.dndtool.service.storage.CampaignDomainCodec;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.Command;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.CheckInput;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Real fixed-origin HTTP, production listeners/filters/servlets and file-backed SQLite. */
class StorageHttpIntegrationTest {
    @TempDir Path temporary;
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).build();
    private JsonObject state;

    @Test void protectedHttpCreatesHealsSavesReopensAndRecoversWithoutJndi() throws Exception {
        Path root = temporary.resolve("private-store");
        String residual;
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(root)) {
            assertEquals(303, get("/host").statusCode());
            var page = get("/host/storage"); assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("生命值战役"));
            var script = get("/host/assets/host-storage.js");
            assertEquals(200, script.statusCode());
            assertTrue(script.headers().firstValue("Content-Type").orElse("").contains("javascript"));
            assertTrue(script.body().contains("DND_STORAGE_HTTP_V1"));
            var style = get("/host/assets/host-storage.css");
            assertEquals(200, style.statusCode());
            assertTrue(style.headers().firstValue("Content-Type").orElse("").contains("text/css"));
            assertTrue(style.body().contains(":root"));
            refresh();
            assertTrue(state.get("campaign").isJsonNull());
            assertEquals("0", state.getAsJsonObject("storage").get("epoch").getAsString());
            assertEquals(409, postRaw("/api/host/campaigns", "", Map.of()).statusCode());
            assertEquals(404, get("/api/host/rules").statusCode());
            assertEquals(403, postRaw("/api/host/storage/create", "", Map.of("Origin", "https://invalid.example")).statusCode());

            var created = command("create", fields("name", "微光营地", "modules", List.of("srd51-vitality@1", "srd51-healing@1")));
            assertEquals(200, created.statusCode(), created.body());
            String newEpoch = JsonParser.parseString(created.body()).getAsJsonObject().getAsJsonObject("storage").get("epoch").getAsString();
            assertNotEquals("0", newEpoch);
            Map<String, List<String>> initialCharacter = fields("name", "艾拉", "maximumHp", "12", "currentHp", "5");
            assertEquals(409, postRaw("/api/host/storage/characters", encode(initialCharacter),
                    commandHeaders(newEpoch, "0", UUID.randomUUID().toString(), StorageRequestDigest.sha256("characters", newEpoch, "0", initialCharacter))).statusCode());
            refresh(); // The same GET that the page performs refreshes the server-owned session epoch.
            fieldsCommand("characters", fields("name", "艾拉", "maximumHp", "12", "currentHp", "5"));
            var character = campaign().getAsJsonArray("characters").get(0).getAsJsonObject();
            assertEquals(5, character.get("currentHp").getAsLong());
            assertEquals(1, character.get("version").getAsLong());
            String characterId = character.get("id").getAsString();
            Map<String, List<String>> heal = fields("characterId", characterId, "characterVersion", character.get("version").getAsString(), "amount", "4");
            String epoch = epoch(), revision = revision(), requestId = UUID.randomUUID().toString();
            String digest = StorageRequestDigest.sha256("heal", epoch, revision, heal);
            var headers = commandHeaders(epoch, revision, requestId, digest);
            var accepted = postRaw("/api/host/storage/heal", encode(heal), headers);
            assertEquals(200, accepted.statusCode(), accepted.body());
            var replayed = postRaw("/api/host/storage/heal", encode(heal), headers);
            assertEquals(200, replayed.statusCode(), replayed.body());
            var acceptance = JsonParser.parseString(accepted.body()).getAsJsonObject().getAsJsonObject("commandResult");
            var replay = JsonParser.parseString(replayed.body()).getAsJsonObject().getAsJsonObject("commandResult");
            assertFalse(acceptance.get("replayed").getAsBoolean());
            assertTrue(replay.get("replayed").getAsBoolean());
            assertEquals(acceptance.get("acceptedRevision"), replay.get("acceptedRevision"));
            assertEquals(5, replay.get("beforeHp").getAsLong());
            assertEquals(9, replay.get("afterHp").getAsLong());
            refresh();
            assertEquals(9, campaign().getAsJsonArray("characters").get(0).getAsJsonObject().get("currentHp").getAsLong());
            assertEquals(2, campaign().getAsJsonArray("history").size());

            String healedVersion = campaign().getAsJsonArray("characters").get(0).getAsJsonObject().get("version").getAsString();
            Map<String, List<String>> tampered = fields("characterId", characterId, "characterVersion", healedVersion, "amount", "99");
            assertEquals(400, postRaw("/api/host/storage/heal", encode(tampered), commandHeaders(epoch(), revision(), UUID.randomUUID().toString(), digest)).statusCode());
            Map<String, List<String>> stale = fields("characterId", characterId, "characterVersion", "0", "amount", "1");
            var staleResponse = command("heal", stale);
            assertEquals(409, staleResponse.statusCode());
            assertEquals("STALE_CHARACTER", JsonParser.parseString(staleResponse.body()).getAsJsonObject().get("code").getAsString());
            refresh(); assertEquals(2, campaign().getAsJsonArray("history").size());
            var noCsrf = commandHeaders(epoch(), revision(), UUID.randomUUID().toString(), StorageRequestDigest.sha256("save", epoch(), revision(), Map.of()));
            noCsrf.remove("X-CSRF-Token");
            assertEquals(403, postRaw("/api/host/storage/save", "", noCsrf).statusCode());

            fieldsCommand("save", Map.of());
            var ended = command("end", Map.of());
            assertEquals(200, ended.statusCode(), ended.body());
            String fileKey = JsonParser.parseString(ended.body()).getAsJsonObject().get("savedFile").getAsString();
            refresh();
            assertTrue(state.get("campaign").isJsonNull());
            fieldsCommand("open", fields("fileKey", fileKey));
            var reopenedCharacter = campaign().getAsJsonArray("characters").get(0).getAsJsonObject();
            assertEquals(9, reopenedCharacter.get("currentHp").getAsLong());
            assertEquals(2, reopenedCharacter.get("version").getAsLong());
            assertEquals(2, campaign().getAsJsonArray("history").size());
            fieldsCommand("heal", fields("characterId", characterId, "characterVersion", reopenedCharacter.get("version").getAsString(), "amount", "20"));
            assertEquals(12, campaign().getAsJsonArray("characters").get(0).getAsJsonObject().get("currentHp").getAsLong());
            residual = state.getAsJsonObject("storage").get("runId").getAsString();
        }
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(root)) {
            refresh();
            assertTrue(state.get("campaign").isJsonNull());
            fieldsCommand("recover", fields("runId", residual));
            assertEquals(12, campaign().getAsJsonArray("characters").get(0).getAsJsonObject().get("currentHp").getAsLong());
            assertEquals(3, campaign().getAsJsonArray("history").size());
            fieldsCommand("end", Map.of());
        }
    }

    @Test void unavailableStorageStillClosesLegacyWritesAndReturnsNoPaths() throws Exception {
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(Path.of("relative-root"))) {
            var unavailable = get("/api/host/storage/state");
            assertEquals(503, unavailable.statusCode());
            assertEquals("STORAGE_UNAVAILABLE", JsonParser.parseString(unavailable.body()).getAsJsonObject().get("code").getAsString());
            assertFalse(unavailable.body().contains("relative-root"));
            assertEquals(409, postRaw("/api/host/campaigns", "", Map.of()).statusCode());
        }
    }

    @Test void protectedFileImportDownloadDeleteAndExplicitResidualAbandonmentStayWithinManagedIdentities() throws Exception {
        Path root = temporary.resolve("file-store"); byte[] bytes;
        try (var input = getClass().getResourceAsStream("/storage/vitality-known-file.json")) { bytes = input.readAllBytes(); }
        String key = new CampaignCodec().decode(bytes).fileKey(), residual, finalFile;
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(root)) {
            refresh();
            String digest = StorageRequestDigest.sha256("import", epoch(), "0", fields("fileSha256", CampaignJson.sha256(bytes)));
            var headers = commandHeaders(epoch(), "0", UUID.randomUUID().toString(), digest);
            var noCsrf = new LinkedHashMap<>(headers); noCsrf.remove("X-CSRF-Token");
            assertEquals(403, upload(bytes, noCsrf).statusCode());
            byte[] tampered = bytes.clone(); tampered[tampered.length / 2] ^= 1;
            assertEquals(400, upload(tampered, headers).statusCode());
            assertEquals(200, upload(bytes, headers).statusCode()); refresh();
            assertTrue(state.get("campaign").isJsonNull());
            assertEquals("IDLE", state.getAsJsonObject("storage").get("state").getAsString());
            assertEquals(key, state.getAsJsonObject("storage").getAsJsonArray("saveFiles").get(0).getAsString());
            var downloaded = get("/api/host/storage/download?fileKey=" + key);
            assertEquals(200, downloaded.statusCode()); assertEquals(new String(bytes, StandardCharsets.UTF_8), downloaded.body());
            assertTrue(downloaded.headers().firstValue("Content-Disposition").orElse("").contains(key + ".json"));
            assertFalse(downloaded.body().contains(root.toString()));
            assertEquals(400, get("/api/host/storage/download?fileKey=" + key + "&extra=value").statusCode());
            assertEquals(400, get("/api/host/storage/download?fileKey=" + key + "&fileKey=" + key).statusCode());
            assertEquals(409, get("/api/host/storage/download?fileKey=..%2Fsecret").statusCode());
            assertEquals(409, upload(bytes, commandHeaders(epoch(), "0", UUID.randomUUID().toString(), digest)).statusCode());

            byte[] invalid = "{}".getBytes(StandardCharsets.UTF_8);
            var invalidResponse = upload(invalid, commandHeaders(epoch(), "0", UUID.randomUUID().toString(),
                    StorageRequestDigest.sha256("import", epoch(), "0", fields("fileSha256", CampaignJson.sha256(invalid)))));
            assertEquals(409, invalidResponse.statusCode()); refresh();
            assertEquals(1, state.getAsJsonObject("storage").getAsJsonArray("saveFiles").size());
            fieldsCommand("create", fields("name", "受控文件操作", "modules", List.of("srd51-vitality@1", "srd51-healing@1")));
            assertEquals(409, command("delete", fields("fileKey", key)).statusCode());
            var ended = command("end", Map.of()); assertEquals(200, ended.statusCode(), ended.body());
            finalFile = JsonParser.parseString(ended.body()).getAsJsonObject().get("savedFile").getAsString(); refresh();
            fieldsCommand("delete", fields("fileKey", key));
            assertEquals(200, get("/api/host/storage/download?fileKey=" + finalFile).statusCode());
            fieldsCommand("open", fields("fileKey", finalFile));
            residual = state.getAsJsonObject("storage").get("runId").getAsString();
        }
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(root)) {
            refresh(); assertEquals("RESIDUAL", state.getAsJsonObject("storage").get("state").getAsString());
            assertEquals(409, command("delete", fields("fileKey", finalFile)).statusCode());
            fieldsCommand("discard", fields("runId", residual));
            assertEquals("IDLE", state.getAsJsonObject("storage").get("state").getAsString());
            assertEquals(200, get("/api/host/storage/download?fileKey=" + finalFile).statusCode());
            assertEquals(409, command("discard", fields("runId", UUID.randomUUID().toString())).statusCode());
        }
    }

    private HttpResponse<String> upload(byte[] bytes, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/api/host/storage/import"))
                .header("Content-Type", "application/octet-stream");
        headers.forEach(request::header);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void fullCampaignDomainFormsPersistCharactersWorldItemsNotesAndRejectUnboundPayloads() throws Exception {
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(temporary.resolve("domain-store"))) {
            refresh();
            fieldsCommand("create", fields("name", "完整领域战役", "modules", List.of("srd51-vitality@1", "srd51-healing@1", "srd51-runtime@1")));
            assertTrue(campaign().get("domain").isJsonObject());
            String classKey = ruleKey("character.class", "fighter"), backgroundKey = ruleKey("character.background", "");
            fieldsCommand("domain", domainFields("character-create", object("name", "完整角色", "kind", "PC", "classKey", classKey, "backgroundKey", backgroundKey)));
            var character = campaign().getAsJsonObject("domain").getAsJsonArray("characters").get(0).getAsJsonObject();
            String characterId = character.get("id").getAsString();
            assertEquals(1, character.get("level").getAsInt());
            assertTrue(character.getAsJsonObject("state").get("abilities").isJsonObject());
            fieldsCommand("domain", domainFields("node-create", object("name", "石桥")));
            String nodeId = campaign().getAsJsonObject("domain").getAsJsonArray("nodes").get(0).getAsJsonObject().get("id").getAsString();
            fieldsCommand("domain", domainFields("position", object("characterId", characterId, "nodeId", nodeId)));
            fieldsCommand("domain", domainFields("item", object("itemId", null, "characterId", characterId, "name", "火把", "quantity", "3")));
            fieldsCommand("domain", domainFields("note", object("text", "石桥前扎营")));
            var checkRule = ruleRecord("adventure.check", "");
            var check = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                    new CheckInput(new RuleIdentity(checkRule.get("moduleKey").getAsString(), checkRule.get("type").getAsString(), checkRule.get("key").getAsString()),
                            "ABILITY", "ability.strength", null, 0, false, false, "DM事前确认检定与成功失败后果。"));
            var currentCharacter = campaign().getAsJsonObject("domain").getAsJsonArray("characters").get(0).getAsJsonObject();
            var conditional = object("characterId", characterId, "characterVersion", currentCharacter.get("version").getAsString());
            conditional.add("command", CampaignDomainCodec.toJson(check));
            String itemId = campaign().getAsJsonObject("domain").getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString();
            conditional.add("success", checkBranch(characterId, itemId, "1", nodeId, "成功抵达石桥"));
            conditional.add("failure", checkBranch(characterId, itemId, "-1", null, "失败消耗火把"));
            fieldsCommand("domain", domainFields("check-effects", conditional));
            assertEquals(4, campaign().getAsJsonObject("domain").getAsJsonArray("items").get(0).getAsJsonObject().get("quantity").getAsInt());
            var auditRoot = campaign().getAsJsonObject("domain").getAsJsonArray("roots").get(5).getAsJsonObject();
            assertEquals("check-effects", auditRoot.get("kind").getAsString());
            assertEquals("成功抵达石桥", JsonParser.parseString(auditRoot.get("after").getAsString()).getAsJsonObject().get("message").getAsString());
            String before = revision();
            Map<String, List<String>> validNote = domainFields("note", object("text", "原意图"));
            Map<String, List<String>> changedNote = domainFields("note", object("text", "被替换的意图"));
            assertEquals(400, postRaw("/api/host/storage/domain", encode(changedNote), commandHeaders(epoch(), before, UUID.randomUUID().toString(),
                    StorageRequestDigest.sha256("domain", epoch(), before, validNote))).statusCode());
            var unexpected = new LinkedHashMap<>(validNote); unexpected.put("unexpected", List.of("ignored"));
            assertEquals(400, command("domain", unexpected).statusCode());
            assertEquals(409, command("domain", fields("action", "note", "payload", "{\"text\":\"a\",\"text\":\"b\"}")).statusCode());
            assertEquals(400, command("domain", fields("action", "note", "payload", "{\"text\":\"" + "a".repeat(256 * 1024) + "\"}")).statusCode());
            refresh(); assertEquals(before, revision());
            fieldsCommand("save", Map.of());
            var ended = command("end", Map.of()); assertEquals(200, ended.statusCode(), ended.body());
            String fileKey = JsonParser.parseString(ended.body()).getAsJsonObject().get("savedFile").getAsString(); refresh();
            fieldsCommand("open", fields("fileKey", fileKey));
            var restored = campaign().getAsJsonObject("domain");
            assertEquals(characterId, restored.getAsJsonArray("characters").get(0).getAsJsonObject().get("id").getAsString());
            assertEquals(nodeId, restored.getAsJsonArray("positions").get(0).getAsJsonObject().get("nodeId").getAsString());
            assertEquals("火把", restored.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());
            assertEquals(4, restored.getAsJsonArray("items").get(0).getAsJsonObject().get("quantity").getAsInt());
            assertEquals(6, restored.getAsJsonArray("roots").size());
            fieldsCommand("end", Map.of());
        }
    }

    private String ruleKey(String type, String fragment) {
        return ruleRecord(type, fragment).get("key").getAsString();
    }
    @Test void freeCardsManualAndSavingChecksOrderedEffectsAndSimpleEncountersSurviveTheSameFile() throws Exception {
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(temporary.resolve("manual-store"))) {
            refresh();
            fieldsCommand("create", fields("name", "自由卡战役", "modules", List.of("srd51-vitality@1", "srd51-healing@1", "srd51-runtime@1")));
            fieldsCommand("domain", domainFields("manual-create", object("name", "自由角色", "kind", "PC", "template", "blank")));
            String character = manualCardByName("自由角色").get("id").getAsString();
            fieldsCommand("domain", domainFields("manual-create", object("name", "守卫", "kind", "NPC", "template", "guard")));
            String guard = manualCardByName("守卫").get("id").getAsString();
            fieldsCommand("domain", domainFields("manual-field", manualPayload(character, object("fieldKey", "hp.maximum", "value", "12"))));
            fieldsCommand("domain", domainFields("manual-field", manualPayload(character, object("fieldKey", "hp.current", "value", "4"))));
            fieldsCommand("domain", domainFields("manual-field", manualPayload(character, object("fieldKey", "ability.dexterity", "value", "14"))));
            fieldsCommand("domain", domainFields("manual-class", manualPayload(character, object("classKey", "class.fighter", "level", "3"))));
            String save = null;
            for (var entry : campaign().getAsJsonObject("domain").getAsJsonObject("manual").getAsJsonObject("catalog").getAsJsonArray("saves"))
                if (entry.getAsJsonObject().get("abilityKey").getAsString().equals("ability.dexterity")) save = entry.getAsJsonObject().get("key").getAsString();
            assertNotNull(save);
            fieldsCommand("domain", domainFields("manual-proficiency", manualPayload(character, object("category", "SAVE", "targetKey", save, "tier", "FULL"))));
            fieldsCommand("domain", domainFields("node-create", object("name", "门厅")));
            fieldsCommand("domain", domainFields("node-create", object("name", "庭院")));
            var nodes = campaign().getAsJsonObject("domain").getAsJsonArray("nodes");
            String hall = null, yard = null;
            for (var entry : nodes) { var node = entry.getAsJsonObject(); if (node.get("name").getAsString().equals("门厅")) hall = node.get("id").getAsString(); else yard = node.get("id").getAsString(); }
            fieldsCommand("domain", domainFields("manual-party-position", object("nodeId", hall)));
            var participants = new com.google.gson.JsonArray();
            participants.add(object("characterId", character, "side", "ALLY", "nodeId", hall)); participants.add(object("characterId", guard, "side", "ENEMY", "nodeId", hall));
            var start = object("nodeId", hall); start.add("participants", participants);
            fieldsCommand("domain", domainFields("manual-encounter-start", start));
            String encounter = campaign().getAsJsonObject("domain").getAsJsonObject("manual").getAsJsonArray("encounters").get(0).getAsJsonObject().get("id").getAsString();
            var success = new com.google.gson.JsonArray(); success.add(object("kind", "HP", "targetId", character, "amount", "3"));
            success.add(object("kind", "HP", "targetId", character, "amount", "-1")); success.add(object("kind", "MESSAGE", "text", "先恢复再消耗"));
            var failure = new com.google.gson.JsonArray(); failure.add(object("kind", "HP", "targetId", character, "amount", "-1"));
            var manual = manualPayload(character, object("checkKind", "MANUAL", "key", null, "manualName", "行动裁定", "manualModifier", "0", "rollMode", "NORMAL", "dc", "0"));
            var targets = new com.google.gson.JsonArray(); targets.add(object("characterId", character, "version", manualCardByName("自由角色").get("version").getAsString()));
            manual.add("targets", targets); manual.add("success", success); manual.add("failure", failure);
            fieldsCommand("domain", domainFields("manual-check", manual));
            assertEquals(6, manualCardByName("自由角色").getAsJsonObject("fields").get("hp.current").getAsInt());

            var failEffects = new com.google.gson.JsonArray(); failEffects.add(object("kind", "HP", "targetId", character, "amount", "-1"));
            failEffects.add(object("kind", "GRANT_TEMPORARY_ITEM", "targetId", character, "name", "检定补给", "description", "豁免失败分支记录", "quantity", "2"));
            failEffects.add(object("kind", "POSITION", "targetId", character, "nodeId", yard)); failEffects.add(object("kind", "MESSAGE", "text", "豁免失败后退至庭院"));
            var saving = manualPayload(character, object("checkKind", "SAVING_THROW", "key", save, "manualName", null, "manualModifier", null, "rollMode", "ADVANTAGE", "dc", "60"));
            targets = new com.google.gson.JsonArray(); targets.add(object("characterId", character, "version", manualCardByName("自由角色").get("version").getAsString()));
            saving.add("targets", targets); saving.add("success", success); saving.add("failure", failEffects);
            fieldsCommand("domain", domainFields("manual-check", saving));
            assertEquals(5, manualCardByName("自由角色").getAsJsonObject("fields").get("hp.current").getAsInt());
            var roots = campaign().getAsJsonObject("domain").getAsJsonArray("roots");
            var audit = JsonParser.parseString(roots.get(roots.size() - 1).getAsJsonObject().get("after").getAsString()).getAsJsonObject();
            assertFalse(audit.getAsJsonObject("result").get("success").getAsBoolean());
            assertEquals(4, audit.getAsJsonObject("result").get("modifier").getAsInt());
            assertEquals(2, audit.getAsJsonObject("result").getAsJsonArray("dice").size());
            fieldsCommand("domain", domainFields("manual-encounter-close", object("encounterId", encounter)));
            var ended = command("end", Map.of()); assertEquals(200, ended.statusCode(), ended.body());
            String file = JsonParser.parseString(ended.body()).getAsJsonObject().get("savedFile").getAsString(); refresh();
            fieldsCommand("open", fields("fileKey", file));
            assertEquals(5, manualCardByName("自由角色").getAsJsonObject("fields").get("hp.current").getAsInt());
            assertEquals(3, manualCardByName("自由角色").getAsJsonObject("classes").get("class.fighter").getAsInt());
            assertEquals("CLOSED", campaign().getAsJsonObject("domain").getAsJsonObject("manual").getAsJsonArray("encounters").get(0).getAsJsonObject().get("status").getAsString());
            assertTrue(campaign().getAsJsonObject("domain").getAsJsonArray("items").asList().stream().anyMatch(item -> item.getAsJsonObject().get("name").getAsString().equals("检定补给")));
            fieldsCommand("end", Map.of());
        }
    }
    private JsonObject manualCardByName(String name) {
        for (var entry : campaign().getAsJsonObject("domain").getAsJsonObject("manual").getAsJsonArray("characters")) {
            var card = entry.getAsJsonObject().getAsJsonObject("current"); if (card.get("name").getAsString().equals(name)) return card;
        }
        throw new AssertionError("Missing manual card: " + name);
    }
    private JsonObject manualPayload(String id, JsonObject extra) {
        JsonObject card = null;
        for (var entry : campaign().getAsJsonObject("domain").getAsJsonObject("manual").getAsJsonArray("characters")) {
            var current = entry.getAsJsonObject().getAsJsonObject("current"); if (current.get("id").getAsString().equals(id)) card = current;
        }
        assertNotNull(card); extra.addProperty("characterId", id); extra.addProperty("characterVersion", card.get("version").getAsString()); return extra;
    }
    private JsonObject ruleRecord(String type, String fragment) {
        for (var entry : campaign().getAsJsonObject("domain").getAsJsonObject("rules").getAsJsonArray("catalog")) {
            JsonObject rule = entry.getAsJsonObject();
            if (rule.get("type").getAsString().equals(type) && rule.get("key").getAsString().contains(fragment)) return rule;
        }
        throw new AssertionError("Missing supported rule: " + type + " " + fragment);
    }
    private static JsonObject checkBranch(String target, String item, String delta, String node, String message) {
        JsonObject branch = object("targetId", target, "itemId", item, "itemDelta", delta, "nodeId", node, "message", message);
        branch.add("healthEffect", com.google.gson.JsonNull.INSTANCE); return branch;
    }
    private static Map<String, List<String>> domainFields(String action, JsonObject payload) { return fields("action", action, "payload", payload.toString()); }
    private static JsonObject object(Object... pairs) {
        JsonObject result = new JsonObject();
        for (int index = 0; index < pairs.length; index += 2) {
            String key = (String) pairs[index]; Object value = pairs[index + 1];
            if (value == null) result.add(key, com.google.gson.JsonNull.INSTANCE);
            else if (value instanceof Boolean booleanValue) result.addProperty(key, booleanValue);
            else result.addProperty(key, value.toString());
        }
        return result;
    }

    private JsonObject campaign() { return state.getAsJsonObject("campaign"); }
    private String epoch() { return state.getAsJsonObject("storage").get("epoch").getAsString(); }
    private String revision() { return state.get("campaign").isJsonNull() ? "0" : campaign().get("revision").getAsString(); }
    private void refresh() throws Exception {
        var response = get("/api/host/storage/state");
        assertEquals(200, response.statusCode(), response.body());
        state = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(state.has("campaign"), "Every state response must explicitly include campaign: " + response.body());
        if (state.getAsJsonObject("storage").get("state").getAsString().equals("ACTIVE"))
            assertTrue(state.get("campaign").isJsonObject(), response.body());
        else assertTrue(state.get("campaign").isJsonNull(), response.body());
    }
    private void fieldsCommand(String action, Map<String, List<String>> fields) throws Exception {
        var response = command(action, fields);
        assertEquals(200, response.statusCode(), response.body());
        refresh();
    }
    private HttpResponse<String> command(String action, Map<String, List<String>> fields) throws Exception {
        return postRaw("/api/host/storage/" + action, encode(fields), commandHeaders(epoch(), revision(), UUID.randomUUID().toString(), StorageRequestDigest.sha256(action, epoch(), revision(), fields)));
    }
    private Map<String, String> commandHeaders(String epoch, String revision, String requestId, String digest) {
        return new LinkedHashMap<>(Map.of("Origin", "http://127.0.0.1:8080", "X-CSRF-Token", state.get("csrf").getAsString(),
                "X-Host-State-Epoch", epoch, "X-Object-Row-Version", revision, "X-Request-Id", requestId, "X-Request-Digest", digest));
    }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> postRaw(String path, String body, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080" + path))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        headers.forEach(request::header);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String encode(Map<String, List<String>> fields) {
        return fields.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(value ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining("&"));
    }
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> fields(Object... pairs) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            result.put((String) pairs[i], pairs[i + 1] instanceof List<?> list ? (List<String>) list : List.of((String) pairs[i + 1]));
        return result;
    }
}
