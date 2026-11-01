package com.dndtool.service.storage;

import com.google.gson.*;
import java.util.*;
import static com.dndtool.service.storage.CampaignData.*;
import static com.dndtool.service.storage.CampaignJson.*;

/** Complete current-version campaign codec for the supported selected profiles, independent of MySQL archive formats. */
public final class CampaignCodec {
    public static final String APPLICATION_MODEL = "srd51-vitality-1";
    public static final String STORAGE_MODEL = "sqlite-vitality-1";

    public byte[] encode(Document document) {
        uuid(document.fileKey()); CampaignData.validate(document.snapshot());
        JsonObject root = content(document.snapshot());
        root.addProperty("fileKind", "VITALITY_CAMPAIGN"); root.addProperty("formatVersion", 1);
        root.addProperty("fileKey", document.fileKey());
        JsonObject integrity = new JsonObject();
        integrity.addProperty("algorithm", "SHA-256"); integrity.addProperty("canonicalVersion", 1);
        integrity.addProperty("digest", digest("DND-VITALITY-FILE", canonical(root)));
        root.add("integrity", integrity); return canonical(root);
    }

    public Document decode(byte[] bytes) {
        JsonObject root = read(bytes);
        if (root.has("domains")) fields(root, "applicationModel", "campaign", "characters", "fileKey", "fileKind", "formatVersion", "history", "integrity", "rules", "domains");
        else fields(root, "applicationModel", "campaign", "characters", "fileKey", "fileKind", "formatVersion", "history", "integrity", "rules");
        if (!string(root, "fileKind").equals("VITALITY_CAMPAIGN") || !string(root, "applicationModel").equals(APPLICATION_MODEL)) throw bad("Unsupported campaign model");
        protocol(root, "formatVersion", 1);
        String key = uuid(string(root, "fileKey"));
        JsonObject integrity = object(root, "integrity"); fields(integrity, "algorithm", "canonicalVersion", "digest");
        protocol(integrity, "canonicalVersion", 1);
        String expected = string(integrity, "digest");
        if (!string(integrity, "algorithm").equals("SHA-256") || !expected.matches("[0-9a-f]{64}")) throw bad("Unsupported integrity binding");
        JsonObject unsigned = root.deepCopy(); unsigned.remove("integrity");
        if (!digest("DND-VITALITY-FILE", canonical(unsigned)).equals(expected)) throw bad("Campaign integrity digest differs");
        JsonObject campaign = object(root, "campaign"); fields(campaign, "campaignKey", "eventTail", "name");
        List<String> rules = new ArrayList<>();
        for (JsonElement e : array(root, "rules")) {
            if (!e.isJsonObject()) throw bad("Rule binding must be an object");
            JsonObject r = e.getAsJsonObject(); fields(r, "id", "materialDigest");
            String id = string(r, "id");
            if (!CampaignRules.materialDigest(id).equals(string(r, "materialDigest"))) throw bad("Exact bundled rule material is unavailable");
            rules.add(id);
        }
        List<CharacterFact> characters = new ArrayList<>();
        for (JsonElement e : array(root, "characters")) {
            if (!e.isJsonObject()) throw bad("Character must be an object");
            JsonObject c = e.getAsJsonObject(); fields(c, "id", "name", "maximumHp", "currentHp");
            characters.add(new CharacterFact(string(c, "id"), string(c, "name"), integer(c, "maximumHp"), integer(c, "currentHp")));
        }
        List<EventFact> history = new ArrayList<>();
        for (JsonElement e : array(root, "history")) {
            if (!e.isJsonObject()) throw bad("History event must be an object");
            JsonObject h = e.getAsJsonObject(); String type = string(h, "type");
            if (type.equals("CHARACTER_CREATED")) {
                fields(h, "sequence", "type", "characterId", "name", "maximumHp", "initialHp");
                long hp = integer(h, "initialHp");
                history.add(new EventFact(integer(h, "sequence"), type, string(h, "characterId"), hp, hp, 0, 0, string(h, "name"), integer(h, "maximumHp")));
            } else if (type.equals("HEALED")) {
                fields(h, "sequence", "type", "characterId", "beforeHp", "afterHp", "amount", "applied");
                history.add(new EventFact(integer(h, "sequence"), type, string(h, "characterId"), integer(h, "beforeHp"), integer(h, "afterHp"), integer(h, "amount"), integer(h, "applied"), "", 0));
            } else if (type.equals("DOMAIN_COMMAND") && root.has("domains")) {
                fields(h, "sequence", "type", "characterId", "beforeHp", "afterHp", "amount", "applied");
                history.add(new EventFact(integer(h, "sequence"), type, string(h, "characterId"), integer(h, "beforeHp"), integer(h, "afterHp"), integer(h, "amount"), integer(h, "applied"), "", 0));
            } else throw bad("Unsupported history type");
        }
        if (integer(campaign, "eventTail") != history.size()) throw bad("Event tail differs from full history");
        CampaignDomainData.State domains = root.has("domains") ? CampaignDomainCodec.fromJson(root.get("domains"), CampaignDomainData.State.class) : null;
        Snapshot snapshot = new Snapshot(string(campaign, "campaignKey"), string(campaign, "name"), rules, characters, history, domains);
        return new Document(key, CampaignData.validate(snapshot));
    }

    public String sourceFingerprint(Snapshot snapshot) {
        CampaignData.validate(snapshot);
        return digest("DND-VITALITY-SOURCE", canonical(content(snapshot)));
    }

    private JsonObject content(Snapshot s) {
        JsonObject root = new JsonObject(); root.addProperty("applicationModel", APPLICATION_MODEL);
        JsonObject campaign = new JsonObject(); campaign.addProperty("campaignKey", s.campaignId());
        campaign.addProperty("name", s.name()); campaign.addProperty("eventTail", Long.toString(s.history().size())); root.add("campaign", campaign);
        JsonArray rules = new JsonArray();
        for (String id : s.rules()) {
            JsonObject r = new JsonObject(); r.addProperty("id", id); r.addProperty("materialDigest", CampaignRules.materialDigest(id)); rules.add(r);
        }
        root.add("rules", rules);
        if (s.domains() != null) root.add("domains", CampaignDomainCodec.toJson(s.domains()));
        JsonArray characters = new JsonArray();
        for (CharacterFact c : s.characters()) {
            JsonObject o = new JsonObject(); o.addProperty("id", c.id()); o.addProperty("name", c.name());
            o.addProperty("maximumHp", Long.toString(c.maximumHp())); o.addProperty("currentHp", Long.toString(c.currentHp())); characters.add(o);
        }
        root.add("characters", characters);
        JsonArray history = new JsonArray();
        for (EventFact h : s.history()) {
            JsonObject o = new JsonObject(); o.addProperty("sequence", Long.toString(h.sequence())); o.addProperty("type", h.type()); o.addProperty("characterId", h.characterId());
            if (h.type().equals("CHARACTER_CREATED")) {
                o.addProperty("name", h.name()); o.addProperty("maximumHp", Long.toString(h.maximumHp())); o.addProperty("initialHp", Long.toString(h.afterHp()));
            } else {
                o.addProperty("beforeHp", Long.toString(h.beforeHp())); o.addProperty("afterHp", Long.toString(h.afterHp()));
                o.addProperty("amount", Long.toString(h.amount())); o.addProperty("applied", Long.toString(h.applied()));
            }
            history.add(o);
        }
        root.add("history", history); return root;
    }
}
