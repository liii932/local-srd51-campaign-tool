package com.dndtool.service.storage;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.service.storage.CampaignData.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignCodecTest {
    private static final String CAMPAIGN = "00000000-0000-4000-8000-000000000001", CHARACTER = "00000000-0000-4000-8000-000000000002", FILE = "00000000-0000-4000-8000-000000000003";
    private final CampaignCodec codec = new CampaignCodec();
    private Snapshot snapshot() { return new Snapshot(CAMPAIGN, "测试", List.of(CampaignRules.BASE, CampaignRules.HEALING), List.of(new CharacterFact(CHARACTER, "角色", 20, 11)), List.of(new EventFact(1, "CHARACTER_CREATED", CHARACTER, 3, 3, 0, 0, "角色", 20), new EventFact(2, "HEALED", CHARACTER, 3, 11, 8, 8, "", 0))); }

    @Test void canonicalPortableProjectionHasAnIndependentKnownFieldVectorAndRoundTripsAllFacts() {
        byte[] bytes = codec.encode(new Document(FILE, snapshot()));
        assertEquals(new Document(FILE, snapshot()), codec.decode(bytes));
        JsonObject o = CampaignJson.read(bytes);
        assertEquals(Set.of("applicationModel", "campaign", "characters", "fileKey", "fileKind", "formatVersion", "history", "integrity", "rules"), o.keySet());
        assertEquals("VITALITY_CAMPAIGN", o.get("fileKind").getAsString());
        assertEquals("{\"a\":\"测试\",\"b\":\"quote\\\"slash\\\\\"}", new String(CampaignJson.canonical(JsonParser.parseString("{\"b\":\"quote\\\"slash\\\\\",\"a\":\"测试\"}")), StandardCharsets.UTF_8));
    }

    @Test void strictReaderRejectsInvalidUtf8BomDuplicateKeysTrailingContentUnknownFieldsAndLoneSurrogates() {
        for (byte[] bad : List.of(new byte[]{(byte) 0xc3, 0x28}, "\ufeff{}".getBytes(StandardCharsets.UTF_8), "{\"x\":1,\"x\":2}".getBytes(StandardCharsets.UTF_8), "{} {}".getBytes(StandardCharsets.UTF_8), "{\"x\":\"\\ud800\"}".getBytes(StandardCharsets.UTF_8))) assertThrows(StorageException.class, () -> CampaignJson.read(bad));
        JsonObject root = CampaignJson.read(codec.encode(new Document(FILE, snapshot()))); root.addProperty("unknown", true);
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(root)));
    }

    @Test void validDigestDoesNotPermitHistoricalDiscontinuityWrongCurrentHpOrUnknownRules() {
        JsonObject changed = CampaignJson.read(codec.encode(new Document(FILE, snapshot())));
        changed.getAsJsonArray("history").get(1).getAsJsonObject().addProperty("beforeHp", "4"); resign(changed);
        JsonObject wrongHistory = changed;
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(wrongHistory)));
        changed = CampaignJson.read(codec.encode(new Document(FILE, snapshot())));
        changed.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("currentHp", "12"); resign(changed);
        JsonObject wrongHp = changed; assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(wrongHp)));
        assertThrows(StorageException.class, () -> CampaignRules.validate(List.of(CampaignRules.BASE, "srd51-healing@2")));
    }

    @Test void codePointsNfcAndBusinessIntegerGrammarAreStrict() {
        assertEquals("😀".repeat(80), CampaignJson.name("😀".repeat(80)));
        for (String name : List.of("😀".repeat(81), "e\u0301", "x\u0085", "x\u007f", " ")) assertThrows(StorageException.class, () -> CampaignJson.name(name));
        JsonObject changed = CampaignJson.read(codec.encode(new Document(FILE, snapshot())));
        changed.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("maximumHp", "020"); resign(changed);
        assertThrows(StorageException.class, () -> codec.decode(CampaignJson.canonical(changed)));
    }
    private static void resign(JsonObject root) {
        root.remove("integrity"); JsonObject integrity = new JsonObject(); integrity.addProperty("algorithm", "SHA-256"); integrity.addProperty("canonicalVersion", 1); integrity.addProperty("digest", CampaignJson.digest("DND-VITALITY-FILE", CampaignJson.canonical(root))); root.add("integrity", integrity);
    }
}
