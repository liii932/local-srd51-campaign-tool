package com.dndtool.service.storage;

import com.google.gson.JsonObject;
import static com.dndtool.service.storage.CampaignData.*;
import static com.dndtool.service.storage.CampaignJson.*;

/** Complete frozen campaign codec. Reading a file never grants local rule acceptance. */
public final class CampaignCodec {
    public static final String APPLICATION_MODEL = "campaign-spaces-3";
    public static final String STORAGE_MODEL = "sqlite-campaign-3";
    public static final String FILE_DIGEST_DOMAIN = "DND-CAMPAIGN-FILE";

    public byte[] encode(Document document) {
        uuid(document.fileKey()); CampaignData.validate(document.snapshot());
        JsonObject root = content(document.snapshot());
        root.addProperty("fileKind", "RULE_BOUND_CAMPAIGN");
        root.addProperty("formatVersion", 3);
        root.addProperty("fileKey", document.fileKey());
        JsonObject integrity = new JsonObject();
        integrity.addProperty("algorithm", "SHA-256"); integrity.addProperty("canonicalVersion", 1);
        integrity.addProperty("digest", digest(FILE_DIGEST_DOMAIN, canonical(root)));
        root.add("integrity", integrity);
        byte[] bytes = canonical(root);
        if (bytes.length > MAX_BYTES) throw bad("Complete campaign exceeds the file byte budget");
        return bytes;
    }

    public Document decode(byte[] bytes) {
        JsonObject root = read(bytes);
        fields(root, "applicationModel", "campaign", "domains", "fileKey", "fileKind", "formatVersion", "integrity");
        if (!string(root, "fileKind").equals("RULE_BOUND_CAMPAIGN")
                || !string(root, "applicationModel").equals(APPLICATION_MODEL)) throw bad("Unsupported campaign model");
        protocol(root, "formatVersion", 3);
        String key = uuid(string(root, "fileKey"));
        JsonObject integrity = object(root, "integrity"); fields(integrity, "algorithm", "canonicalVersion", "digest");
        protocol(integrity, "canonicalVersion", 1);
        String expected = string(integrity, "digest");
        if (!string(integrity, "algorithm").equals("SHA-256") || !expected.matches("[0-9a-f]{64}")) throw bad("Unsupported integrity binding");
        JsonObject unsigned = root.deepCopy(); unsigned.remove("integrity");
        if (!digest(FILE_DIGEST_DOMAIN, canonical(unsigned)).equals(expected)) throw bad("Campaign integrity digest differs");
        JsonObject campaign = object(root, "campaign"); fields(campaign, "campaignKey", "eventTail", "name");
        try {
            var domains = CampaignDomainCodec.fromJson(root.get("domains"), CampaignDomainData.State.class);
            Snapshot snapshot = new Snapshot(string(campaign, "campaignKey"), string(campaign, "name"), domains);
            if (integer(campaign, "eventTail") != snapshot.history().size()) throw bad("Event tail differs from complete domain history");
            return new Document(key, CampaignData.validate(snapshot));
        } catch (StorageException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw new StorageException("INVALID_FILE", "Campaign domain facts are invalid", invalid);
        }
    }

    public String sourceFingerprint(Snapshot snapshot) {
        CampaignData.validate(snapshot);
        return digest("DND-CAMPAIGN-SOURCE", canonical(content(snapshot)));
    }

    private JsonObject content(Snapshot snapshot) {
        JsonObject root = new JsonObject(); root.addProperty("applicationModel", APPLICATION_MODEL);
        JsonObject campaign = new JsonObject(); campaign.addProperty("campaignKey", snapshot.campaignId());
        campaign.addProperty("name", snapshot.name());
        campaign.addProperty("eventTail", Long.toString(snapshot.history().size()));
        root.add("campaign", campaign);
        root.add("domains", CampaignDomainCodec.toJson(snapshot.domains()));
        return root;
    }
}
