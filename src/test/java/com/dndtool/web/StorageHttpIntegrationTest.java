package com.dndtool.web;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.service.storage.BundledCampaignRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.dndtool.service.storage.CampaignJson;
import com.dndtool.service.storage.CampaignDomainCodec;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import java.io.ByteArrayOutputStream;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    @Test void protectedHttpCreatesSavesReopensAndRecoversWithoutJndi() throws Exception {
        Path root = temporary.resolve("private-store");
        String residual;
        JsonObject retained;
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(root)) {
            assertEquals(303, get("/host").statusCode());
            var page = get("/host/storage"); assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("本机规则库"));
            var script = get("/host/assets/host-storage.js");
            assertEquals(200, script.statusCode());
            assertTrue(script.headers().firstValue("Content-Type").orElse("").contains("javascript"));
            assertTrue(script.body().contains("DND_STORAGE_HTTP_V1"));
            var style = get("/host/assets/host-storage.css");
            assertEquals(200, style.statusCode());
            assertTrue(style.headers().firstValue("Content-Type").orElse("").contains("text/css"));
            refresh();
            assertTrue(state.get("campaign").isJsonNull());
            assertEquals("0", epoch());
            assertEquals(409, postRaw("/api/host/campaigns", "", Map.of()).statusCode());
            assertEquals(404, get("/api/host/rules").statusCode());
            assertEquals(403, postRaw("/api/host/storage/create", "", Map.of("Origin", "https://invalid.example")).statusCode());

            var standardMembers = prepareBundled();
            var standardRules = result(rulePost("rule-preview", fields("members", standardMembers)));
            var created = command("create", fields("name", "微光营地", "members", standardMembers));
            assertEquals(200, created.statusCode(), created.body());
            String newEpoch = json(created).getAsJsonObject("storage").get("epoch").getAsString();
            assertNotEquals("0", newEpoch);
            var note = domainFields("note", object("text", "记下营地的路标"));
            String requestId = UUID.randomUUID().toString();
            String digest = StorageRequestDigest.sha256("domain", newEpoch, "0", note);
            var headers = commandHeaders(newEpoch, "0", requestId, digest);
            assertEquals(409, postRaw("/api/host/storage/domain", encode(note), headers).statusCode());
            refresh();
            assertEquals(404, command("characters", fields("name", "艾拉", "maximumHp", "12", "currentHp", "5")).statusCode());
            assertEquals(404, command("heal", Map.of()).statusCode());
            var standardCampaign = campaign();
            assertEquals(standardRules, standardCampaign.getAsJsonObject("domain").get("rules"));
            var variantCandidates = RuleHttpFixture.members(library(), BundledCampaignRules.CHECK_VARIANT);
            var variantMember = variantCandidates.getLast();
            result(rulePost("rule-import-bundled", fields("member", RuleHttpFixture.tuple(variantMember))));
            accept(variantMember);
            assertTrue(review(variantMember).get("acceptance").isJsonObject());
            refresh();
            assertEquals(standardCampaign, campaign());
            var accepted = postRaw("/api/host/storage/domain", encode(note), headers);
            assertEquals(200, accepted.statusCode(), accepted.body());
            var replayed = postRaw("/api/host/storage/domain", encode(note), headers);
            assertEquals(200, replayed.statusCode(), replayed.body());
            var receipt = json(accepted).getAsJsonObject("commandResult");
            var replay = json(replayed).getAsJsonObject("commandResult");
            assertFalse(receipt.get("replayed").getAsBoolean());
            assertTrue(replay.get("replayed").getAsBoolean());
            assertEquals(receipt.get("acceptedRevision"), replay.get("acceptedRevision"));
            assertEquals(receipt.get("subjectId"), replay.get("subjectId"));
            refresh();
            assertEquals(1, campaign().getAsJsonArray("history").size());
            var tampered = domainFields("note", object("text", "替换内容"));
            assertEquals(400, postRaw("/api/host/storage/domain", encode(tampered), headers).statusCode());
            var stale = postRaw("/api/host/storage/domain", encode(note), commandHeaders(newEpoch, "0", UUID.randomUUID().toString(), digest));
            assertError(stale, 409, "STALE_REVISION");
            var noCsrf = commandHeaders(epoch(), revision(), UUID.randomUUID().toString(), StorageRequestDigest.sha256("save", epoch(), revision(), Map.of()));
            noCsrf.remove("X-CSRF-Token");
            assertEquals(403, postRaw("/api/host/storage/save", "", noCsrf).statusCode());

            var saved = command("save", Map.of());
            assertEquals(200, saved.statusCode(), saved.body());
            String fileKey = json(saved).get("savedFile").getAsString();
            var download = getBytes("/api/host/storage/download?fileKey=" + fileKey);
            assertEquals(200, download.statusCode());
            refresh();
            retained = campaign().getAsJsonObject("domain");
            var history = campaign().getAsJsonArray("history");
            fieldsCommand("end", Map.of());
            var variantMembers = variantCandidates.stream().map(RuleHttpFixture::tuple).toList();
            var variantRules = result(rulePost("rule-preview", fields("members", variantMembers)));
            assertNotEquals(standardRules.get("binding"), variantRules.get("binding"));
            fieldsCommand("create", fields("name", "家规营地", "members", variantMembers));
            assertEquals(variantRules, campaign().getAsJsonObject("domain").get("rules"));
            fieldsCommand("end", Map.of());
            fieldsCommand("open", fields("fileKey", fileKey));
            assertEquals(retained, campaign().getAsJsonObject("domain"));
            assertEquals(history, campaign().getAsJsonArray("history"));
            var afterSwitch = getBytes("/api/host/storage/download?fileKey=" + fileKey);
            assertEquals(200, afterSwitch.statusCode());
            assertArrayEquals(download.body(), afterSwitch.body());
            fieldsCommand("domain", domainFields("note", object("text", "从已保存的营地继续")));
            assertEquals(2, campaign().getAsJsonArray("history").size());
            retained = campaign().getAsJsonObject("domain");
            residual = state.getAsJsonObject("storage").get("runId").getAsString();
        }
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(root)) {
            refresh();
            assertTrue(state.get("campaign").isJsonNull());
            fieldsCommand("recover", fields("runId", residual));
            assertEquals(retained, campaign().getAsJsonObject("domain"));
            assertEquals(2, campaign().getAsJsonArray("history").size());
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

    @Test void ruleLibraryPreservesOriginalZipAndFileOpenRequiresExactLocalAcceptance() throws Exception {
        String fileKey;
        byte[] file;
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(temporary.resolve("file-source"))) {
            refresh();
            assertTrue(library().getAsJsonArray("inventory").isEmpty());
            var candidates = RuleHttpFixture.members(library(), BundledCampaignRules.CHECK_VARIANT);
            var house = candidates.getLast();
            var members = candidates.stream().map(RuleHttpFixture::tuple).toList();
            var preview = fields("members", members);
            assertRuleIssue(rulePost("rule-preview", preview), "RULE_PACKAGE_MISSING", candidates.getFirst());
            byte[] zip;
            try (var input = getClass().getResourceAsStream("/storage/rules/dm-runtime-check-variant.zip")) {
                assertNotNull(input);
                zip = input.readAllBytes();
            }
            String operation = UUID.randomUUID().toString();
            var importHeaders = ruleHeaders("rule-import", fields("fileSha256", CampaignJson.sha256(zip)), operation);
            var noCsrf = new LinkedHashMap<>(importHeaders); noCsrf.remove("X-CSRF-Token");
            assertEquals(403, upload("rule-import", zip, noCsrf).statusCode());
            byte[] altered = zip.clone(); altered[altered.length / 2] ^= 1;
            assertError(upload("rule-import", altered, importHeaders), 400, "INVALID_REQUEST_DIGEST");
            assertInvalidRulePackageDiagnostics(zip, operation, house);
            assertTrue(library().getAsJsonArray("inventory").isEmpty());
            var imported = result(upload("rule-import", zip, importHeaders));
            assertEquals(imported, result(upload("rule-import", zip, importHeaders)));
            var review = review(house);
            assertEquals(house, review.getAsJsonObject("imported").get("member"));
            assertTrue(review.get("acceptance").isJsonNull());
            assertEquals("DND_LOCAL_RULE_REVIEW_V1", review.get("reviewProtocol").getAsString());
            assertTrue(review.get("declaration").isJsonObject());
            String query = ruleQuery(house) + "&rawZipSha256=" + house.get("rawZipSha256").getAsString();
            var zipResponse = getBytes("/api/host/storage/rule-download?" + query);
            assertEquals(200, zipResponse.statusCode());
            assertArrayEquals(zip, zipResponse.body());
            assertEquals("application/zip", zipResponse.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(zipResponse.headers().firstValue("Content-Disposition").orElseThrow().contains(".zip"));
            assertEquals(400, get("/api/host/storage/rule-download?" + query + "&moduleKey=extra").statusCode());
            for (var candidate : candidates.subList(0, 3)) result(rulePost("rule-import-bundled", fields("member", RuleHttpFixture.tuple(candidate))));
            assertRuleIssue(rulePost("rule-preview", preview), "RULE_PACKAGE_NOT_ACCEPTED", candidates.getFirst());
            assertRuleIssue(command("create", fields("name", "未接受", "members", members)), "RULE_PACKAGE_NOT_ACCEPTED", candidates.getFirst());
            assertTrue(state.get("campaign").isJsonNull());
            for (var candidate : candidates.subList(0, 3)) accept(candidate);
            var acceptFields = fields("member", RuleHttpFixture.tuple(house), "reviewProtocol", review.get("reviewProtocol").getAsString());
            String acceptId = UUID.randomUUID().toString();
            var accepted = result(rulePost("rule-accept", acceptFields, acceptId));
            assertEquals(accepted, result(rulePost("rule-accept", acceptFields, acceptId)));
            assertEquals("local-dm", accepted.getAsJsonObject("acceptance").get("acceptedBy").getAsString());
            assertEquals("COMMITTED", result(get("/api/host/storage/rule-operation?operationId=" + acceptId)).get("state").getAsString());
            assertEquals("COMMITTED", result(rulePost("rule-recover", fields("operationId", acceptId))).get("state").getAsString());
            var wrongOrder = new ArrayList<>(members); java.util.Collections.swap(wrongOrder, 0, 1);
            assertEquals(409, rulePost("rule-preview", fields("members", wrongOrder)).statusCode());
            var projected = result(rulePost("rule-preview", preview));
            assertEquals(house, projected.getAsJsonObject("binding").getAsJsonArray("members").asList().getLast());
            fieldsCommand("create", fields("name", "可携带规则战役", "members", members));
            assertEquals(projected.get("binding"), campaign().getAsJsonObject("domain").getAsJsonObject("rules").get("binding"));
            var ended = command("end", Map.of());
            assertEquals(200, ended.statusCode(), ended.body());
            fileKey = json(ended).get("savedFile").getAsString();
            file = getBytes("/api/host/storage/download?fileKey=" + fileKey).body();
        }

        Path target = temporary.resolve("file-target");
        String residual;
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(target)) {
            refresh();
            var importedFile = upload("import", file, ruleHeaders("import", fields("fileSha256", CampaignJson.sha256(file)), UUID.randomUUID().toString()));
            assertEquals(200, importedFile.statusCode(), importedFile.body());
            refresh();
            assertEquals("IDLE", state.getAsJsonObject("storage").get("state").getAsString());
            var open = fields("fileKey", fileKey);
            var candidates = RuleHttpFixture.members(library(), BundledCampaignRules.CHECK_VARIANT);
            assertRuleIssue(command("open", open), "RULE_PACKAGE_MISSING", candidates.getFirst());
            for (var candidate : candidates) result(rulePost("rule-import-bundled", fields("member", RuleHttpFixture.tuple(candidate))));
            assertRuleIssue(command("open", open), "RULE_PACKAGE_NOT_ACCEPTED", candidates.getFirst());
            var download = getBytes("/api/host/storage/download?fileKey=" + fileKey);
            assertEquals(200, download.statusCode()); assertArrayEquals(file, download.body());
            assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow().contains(fileKey + ".json"));
            assertEquals(400, get("/api/host/storage/download?fileKey=" + fileKey + "&extra=value").statusCode());
            assertEquals(400, get("/api/host/storage/download?fileKey=" + fileKey + "&fileKey=" + fileKey).statusCode());
            for (var candidate : candidates) accept(candidate);
            fieldsCommand("open", open);
            assertEquals("可携带规则战役", campaign().get("name").getAsString());
            assertEquals(409, command("delete", open).statusCode());
            residual = state.getAsJsonObject("storage").get("runId").getAsString();
        }
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(target)) {
            refresh(); assertEquals("RESIDUAL", state.getAsJsonObject("storage").get("state").getAsString());
            assertEquals(409, command("delete", fields("fileKey", fileKey)).statusCode());
            fieldsCommand("discard", fields("runId", residual));
            assertEquals("IDLE", state.getAsJsonObject("storage").get("state").getAsString());
            assertEquals(200, get("/api/host/storage/download?fileKey=" + fileKey).statusCode());
            assertEquals(409, command("discard", fields("runId", UUID.randomUUID().toString())).statusCode());
        }
    }

    private HttpResponse<String> upload(String action, byte[] bytes, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/api/host/storage/" + action))
                .header("Content-Type", "application/octet-stream");
        headers.forEach(request::header);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertInvalidRulePackageDiagnostics(byte[] original, String operation, JsonObject expectedMember) throws Exception {
        var members = new TreeMap<>(ProductionRuleArtifact.read(original).originalMembers());
        String rules = new String(members.get("rules.json"), StandardCharsets.UTF_8);
        members.put("rules.json", rules.replace("DM_ANTIMAGIC_ZONE_V1", "UNKNOWN_MECHANISM").getBytes(StandardCharsets.UTF_8));
        byte[] malformed = storedZip(members);
        String raw = CampaignJson.sha256(malformed);
        var response = upload("rule-import", malformed, ruleHeaders("rule-import", fields("fileSha256", raw), operation));
        assertError(response, 409, "RULE_PACKAGE_INVALID");
        var issue = json(response).getAsJsonArray("issues").get(1).getAsJsonObject();
        assertEquals("S2_TYPE", issue.get("diagnosticCode").getAsString());
        assertEquals("AUTHOR", issue.get("stage").getAsString());
        assertEquals(expectedMember.get("moduleKey"), issue.get("moduleKey"));
        assertEquals(expectedMember.get("releaseVersion"), issue.get("releaseVersion"));
        assertEquals(raw, issue.get("expectedRawZipSha256").getAsString());
        assertEquals("rules.json", issue.get("path").getAsString());
        assertEquals("/0/algorithm", issue.get("pointer").getAsString());

        String unsafePath = "/private/operator-file.json";
        byte[] unsafe = storedZip(Map.of(unsafePath, new byte[]{1}));
        var rejected = upload("rule-import", unsafe, ruleHeaders("rule-import", fields("fileSha256", CampaignJson.sha256(unsafe)), operation));
        assertError(rejected, 409, "RULE_PACKAGE_INVALID");
        assertFalse(rejected.body().contains(unsafePath));
        var zipIssue = json(rejected).getAsJsonArray("issues").get(1).getAsJsonObject();
        assertEquals("PARSE", zipIssue.get("stage").getAsString());
        assertEquals("$zip", zipIssue.get("path").getAsString());
        assertTrue(zipIssue.get("byteOffset").getAsInt() >= 0);
    }

    private static byte[] storedZip(Map<String, byte[]> members) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var member : members.entrySet()) {
                byte[] bytes = member.getValue();
                var crc = new CRC32(); crc.update(bytes);
                var entry = new ZipEntry(member.getKey());
                entry.setTimeLocal(LocalDateTime.of(2020, 1, 1, 0, 0));
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(bytes.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    @Test void fullCampaignDomainFormsPersistCharactersWorldItemsNotesAndRejectUnboundPayloads() throws Exception {
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(temporary.resolve("domain-store"))) {
            refresh();
            fieldsCommand("create", fields("name", "完整领域战役", "members", prepareBundled()));
            assertTrue(campaign().get("domain").isJsonObject());
            String classKey = ruleKey("character.class", "fighter"), backgroundKey = ruleKey("character.background", "");
            fieldsCommand("domain", domainFields("character-create", object("name", "完整角色", "kind", "PC", "classKey", classKey, "backgroundKey", backgroundKey)));
            var character = campaign().getAsJsonObject("domain").getAsJsonArray("characters").get(0).getAsJsonObject();
            String characterId = character.get("id").getAsString();
            assertEquals(1, character.get("level").getAsInt());
            assertTrue(character.getAsJsonObject("state").get("abilities").isJsonObject());
            fieldsCommand("domain", domainFields("node-create", object("name", "石桥")));
            String nodeId = campaign().getAsJsonObject("domain").getAsJsonArray("nodes").get(0).getAsJsonObject().get("id").getAsString();
            focus("WORLD", nodeId);
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
            conditional.add("success", checkBranch(characterId, itemId, "1", "成功备好火把"));
            conditional.add("failure", checkBranch(characterId, itemId, "-1", "失败消耗火把"));
            fieldsCommand("domain", domainFields("check-effects", conditional));
            assertEquals(4, campaign().getAsJsonObject("domain").getAsJsonArray("items").get(0).getAsJsonObject().get("quantity").getAsInt());
            var auditRoot = campaign().getAsJsonObject("domain").getAsJsonArray("roots").asList().getLast().getAsJsonObject();
            assertEquals("check-effects", auditRoot.get("kind").getAsString());
            assertEquals("成功备好火把", JsonParser.parseString(auditRoot.get("after").getAsString()).getAsJsonObject().get("message").getAsString());
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
            assertEquals(nodeId, restored.getAsJsonArray("positions").get(0).getAsJsonObject().getAsJsonObject("space").get("id").getAsString());
            assertEquals("火把", restored.getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());
            assertEquals(4, restored.getAsJsonArray("items").get(0).getAsJsonObject().get("quantity").getAsInt());
            assertEquals(7, restored.getAsJsonArray("roots").size());
            fieldsCommand("end", Map.of());
        }
    }

    private String ruleKey(String type, String fragment) {
        return ruleRecord(type, fragment).get("key").getAsString();
    }
    @Test void freeCardsChecksAdjudicatedSceneAndWorldTravelSurviveTheSameFile() throws Exception {
        try (StorageLoopLauncher ignored = StorageLoopLauncher.start(temporary.resolve("manual-store"))) {
            refresh();
            fieldsCommand("create", fields("name", "自由卡战役", "members", prepareBundled()));
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
            assertNotNull(hall); assertNotNull(yard);
            focus("WORLD", hall);
            fieldsCommand("domain", domainFields("node-link", object("from", hall, "to", yard)));
            for (String id : List.of(character, guard))
                fieldsCommand("domain", domainFields("position", object("characterId", id, "nodeId", hall)));
            fieldsCommand("domain", domainFields("scene-create", object("name", "门厅交手", "nodeId", hall,
                    "width", "4", "height", "4", "resolution", "DM")));
            String scene = spaces().getAsJsonArray("scenes").get(0).getAsJsonObject().get("id").getAsString();
            var participants = new com.google.gson.JsonArray();
            participants.add(object("id", character, "version", placementVersion(character), "x", "0", "y", "0", "side", "0"));
            participants.add(object("id", guard, "version", placementVersion(guard), "x", "1", "y", "0", "side", "1"));
            var start = object("sceneId", scene); start.add("subjects", participants);
            fieldsCommand("domain", domainFields("scene-enter", start));
            fieldsCommand("domain", domainFields("scene-mode", object("mode", "COMBAT")));
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
            failEffects.add(object("kind", "MESSAGE", "text", "豁免失败，消耗补给"));
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
            fieldsCommand("domain", domainFields("scene-mode", object("mode", "EXPLORATION")));
            var leave = new JsonObject(); leave.add("subjects", transfers(character, guard));
            fieldsCommand("domain", domainFields("scene-leave", leave));
            focus("SCENE", scene);
            fieldsCommand("domain", domainFields("scene-close", new JsonObject()));
            var travel = object("targetNodeId", yard, "minutes", "2", "adjudication", "经门厅走到庭院");
            travel.add("subjects", transfers(character, guard));
            fieldsCommand("domain", domainFields("travel", travel));
            var ended = command("end", Map.of()); assertEquals(200, ended.statusCode(), ended.body());
            String file = JsonParser.parseString(ended.body()).getAsJsonObject().get("savedFile").getAsString(); refresh();
            fieldsCommand("open", fields("fileKey", file));
            assertEquals(5, manualCardByName("自由角色").getAsJsonObject("fields").get("hp.current").getAsInt());
            assertEquals(3, manualCardByName("自由角色").getAsJsonObject("classes").get("class.fighter").getAsInt());
            assertEquals("CLOSED", spaces().getAsJsonArray("scenes").get(0).getAsJsonObject().get("mode").getAsString());
            assertEquals(yard, spaces().getAsJsonObject("focus").getAsJsonObject("space").get("id").getAsString());
            assertEquals(120, clockSecond(hall)); assertEquals(0, clockSecond(yard));
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
    private static JsonObject checkBranch(String target, String item, String delta, String message) {
        JsonObject branch = object("targetId", target, "itemId", item, "itemDelta", delta, "message", message);
        branch.add("healthEffect", com.google.gson.JsonNull.INSTANCE); return branch;
    }
    private JsonObject spaces() { return campaign().getAsJsonObject("domain").getAsJsonObject("space"); }
    private void focus(String kind, String id) throws Exception {
        var payload = new JsonObject(); payload.add("target", object("kind", kind, "id", id));
        fieldsCommand("domain", domainFields("focus-set", payload));
    }
    private String placementVersion(String id) {
        return campaign().getAsJsonObject("domain").getAsJsonArray("positions").asList().stream()
                .map(com.google.gson.JsonElement::getAsJsonObject).filter(p -> p.get("characterId").getAsString().equals(id))
                .findFirst().orElseThrow().get("version").getAsString();
    }
    private com.google.gson.JsonArray transfers(String... subjects) {
        var result = new com.google.gson.JsonArray();
        for (String id : subjects) result.add(object("id", id, "version", placementVersion(id)));
        return result;
    }
    private long clockSecond(String id) {
        return spaces().getAsJsonArray("clocks").asList().stream().map(com.google.gson.JsonElement::getAsJsonObject)
                .filter(clock -> clock.getAsJsonObject("space").get("id").getAsString().equals(id))
                .findFirst().orElseThrow().get("second").getAsLong();
    }
    private Map<String, List<String>> domainFields(String action, JsonObject payload) {
        if (state.get("campaign").isJsonObject()) {
            var current = spaces().getAsJsonObject("focus");
            var focus = object("generation", current.get("generation").getAsString());
            focus.add("space", current.get("space"));
            payload = payload.deepCopy(); payload.add("focus", focus);
        }
        return fields("action", action, "payload", payload.toString());
    }
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
    private List<String> prepareBundled() throws Exception {
        var candidates = RuleHttpFixture.members(library(), BundledCampaignRules.STANDARD);
        for (var member : candidates) {
            result(rulePost("rule-import-bundled", fields("member", RuleHttpFixture.tuple(member))));
            accept(member);
        }
        return candidates.stream().map(RuleHttpFixture::tuple).toList();
    }
    private JsonObject library() throws Exception { return result(get("/api/host/storage/rule-library")); }
    private JsonObject review(JsonObject member) throws Exception { return result(get("/api/host/storage/rule-review?" + ruleQuery(member))); }
    private void accept(JsonObject member) throws Exception {
        result(rulePost("rule-accept", fields("member", RuleHttpFixture.tuple(member), "reviewProtocol", review(member).get("reviewProtocol").getAsString())));
    }
    private HttpResponse<String> rulePost(String action, Map<String, List<String>> fields) throws Exception {
        return rulePost(action, fields, UUID.randomUUID().toString());
    }
    private HttpResponse<String> rulePost(String action, Map<String, List<String>> fields, String operation) throws Exception {
        return postRaw("/api/host/storage/" + action, encode(fields), ruleHeaders(action, fields, operation));
    }
    private Map<String, String> ruleHeaders(String action, Map<String, List<String>> fields, String operation) {
        return commandHeaders(epoch(), "0", operation, StorageRequestDigest.sha256(action, epoch(), "0", fields));
    }
    private static String ruleQuery(JsonObject member) {
        return "moduleKey=" + member.get("moduleKey").getAsString() + "&releaseVersion=" + member.get("releaseVersion").getAsString();
    }
    private static JsonObject json(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject(); }
    private static JsonObject result(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        return json(response).getAsJsonObject("result");
    }
    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, json(response).get("code").getAsString());
    }
    private static void assertRuleIssue(HttpResponse<String> response, String code, JsonObject member) {
        assertError(response, 409, code);
        var issue = json(response).getAsJsonArray("issues").get(0).getAsJsonObject();
        assertEquals(member.get("moduleKey"), issue.get("moduleKey"));
        assertEquals(member.get("releaseVersion"), issue.get("releaseVersion"));
        assertEquals(member.get("rawZipSha256"), issue.get("expectedRawZipSha256"));
    }
    private Map<String, String> commandHeaders(String epoch, String revision, String requestId, String digest) {
        return new LinkedHashMap<>(Map.of("Origin", "http://127.0.0.1:8080", "X-CSRF-Token", state.get("csrf").getAsString(),
                "X-Host-State-Epoch", epoch, "X-Object-Row-Version", revision, "X-Request-Id", requestId, "X-Request-Digest", digest));
    }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080" + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
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
