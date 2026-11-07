package com.dndtool.service.storage;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.service.storage.CampaignData.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignCodecTest {
    private final CampaignCodec codec = new CampaignCodec();

    @Test void canonicalPortableProjectionHasAnIndependentKnownFieldVectorAndRoundTripsAllFacts() {
        byte[] bytes = CampaignLifecycleFixture.fileBytes();
        assertEquals(new Document(CampaignLifecycleFixture.FILE, CampaignLifecycleFixture.snapshot()), codec.decode(bytes));
        JsonObject o = CampaignJson.read(bytes);
        assertEquals(Set.of("applicationModel", "campaign", "domains", "fileKey", "fileKind", "formatVersion", "integrity"), o.keySet());
        assertEquals("RULE_BOUND_CAMPAIGN", o.get("fileKind").getAsString());
        assertEquals("{\"a\":\"测试\",\"b\":\"quote\\\"slash\\\\\"}", new String(CampaignJson.canonical(JsonParser.parseString("{\"b\":\"quote\\\"slash\\\\\",\"a\":\"测试\"}")), StandardCharsets.UTF_8));
    }

    @Test void strictReaderRejectsInvalidUtf8BomDuplicateKeysTrailingContentUnknownFieldsAndLoneSurrogates() {
        for (byte[] bad : List.of(new byte[]{(byte) 0xc3, 0x28}, "\ufeff{}".getBytes(StandardCharsets.UTF_8), "{\"x\":1,\"x\":2}".getBytes(StandardCharsets.UTF_8), "{} {}".getBytes(StandardCharsets.UTF_8), "{\"x\":\"\\ud800\"}".getBytes(StandardCharsets.UTF_8))) assertThrows(StorageException.class, () -> CampaignJson.read(bad));
        JsonObject root = CampaignJson.read(CampaignLifecycleFixture.fileBytes()); root.addProperty("unknown", true);
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(root)));
    }

    @Test void validDigestDoesNotPermitHistoricalDiscontinuityWrongCurrentHpOrUnknownRules() {
        JsonObject wrongHistory = CampaignJson.read(CampaignLifecycleFixture.fileBytes());
        wrongHistory.getAsJsonObject("domains").getAsJsonArray("roots").get(1).getAsJsonObject().addProperty("sequence", "3");
        resign(wrongHistory);
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(wrongHistory)));
        JsonObject wrongHp = CampaignJson.read(CampaignLifecycleFixture.fileBytes());
        var fields = wrongHp.getAsJsonObject("domains").getAsJsonObject("manual").getAsJsonArray("characters")
                .get(0).getAsJsonObject().getAsJsonObject("current").getAsJsonArray("fields");
        fields.asList().stream().map(JsonElement::getAsJsonArray)
                .filter(row -> row.get(0).getAsString().equals("hp.current")).findFirst().orElseThrow()
                .set(1, new JsonPrimitive("9"));
        resign(wrongHp);
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(wrongHp)));
        JsonObject unknownRules = CampaignJson.read(CampaignLifecycleFixture.fileBytes());
        unknownRules.getAsJsonObject("domains").getAsJsonObject("material").addProperty("interpretation", "unknown");
        resign(unknownRules);
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(unknownRules)));
    }

    @Test void invalidRuleCharacterHistoryReturnsTheStableFileErrorAfterDigestVerification() {
        Snapshot empty = CampaignRuleFixtures.snapshot(CampaignLifecycleFixture.CAMPAIGN, "测试");
        JsonObject input = new JsonObject();
        input.addProperty("name", "战士");
        input.addProperty("kind", "PC");
        input.addProperty("classKey", "fighter");
        input.addProperty("backgroundKey", "custom_artisan");
        var domains = new CampaignDomainCommands().apply(empty, "character-create", input,
                UUID.fromString("00000000-0000-4000-8000-000000000005"), "a".repeat(64),
                sides -> { throw new AssertionError("Character creation does not roll"); });
        var snapshot = new Snapshot(empty.campaignId(), empty.name(), domains);
        var file = CampaignJson.read(codec.encode(new Document(CampaignLifecycleFixture.FILE, snapshot)));
        file.getAsJsonObject("domains").getAsJsonArray("characters").get(0).getAsJsonObject()
                .getAsJsonObject("history").getAsJsonArray("events").get(0).getAsJsonObject()
                .getAsJsonObject("after").addProperty("version", "1");
        resign(file);

        var invalid = assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(file)));
        assertEquals("INVALID_FILE", invalid.code());
        assertEquals("Complete facts differ from retained root consequences", invalid.getMessage());
    }

    @Test void codePointsNfcAndBusinessIntegerGrammarAreStrict() {
        assertEquals("😀".repeat(80), CampaignJson.name("😀".repeat(80)));
        for (String name : List.of("😀".repeat(81), "e\u0301", "x\u0085", "x\u007f", " ")) assertThrows(StorageException.class, () -> CampaignJson.name(name));
        JsonObject changed = CampaignJson.read(CampaignLifecycleFixture.fileBytes());
        changed.getAsJsonObject("campaign").addProperty("eventTail", "020"); resign(changed);
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(changed)));
    }
    private static void resign(JsonObject root) {
        root.remove("integrity"); JsonObject integrity = new JsonObject(); integrity.addProperty("algorithm", "SHA-256"); integrity.addProperty("canonicalVersion", 1); integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(root))); root.add("integrity", integrity);
    }
}
