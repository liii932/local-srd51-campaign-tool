package com.dndtool.service.storage;

import com.dndtool.service.storage.CampaignData.Document;
import com.dndtool.service.storage.CampaignData.Snapshot;
import com.dndtool.service.storage.CampaignStorageService.CampaignView;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.UUID;

/** Free-card commands used to exercise complete campaign persistence and recovery. */
final class CampaignLifecycleFixture {
    static final String CAMPAIGN = "00000000-0000-4000-8000-000000000001";
    static final String FILE = "00000000-0000-4000-8000-000000000003";

    private CampaignLifecycleFixture() { }

    static CampaignView addCard(CampaignStorageService service, String request, long epoch, long revision, String name) {
        return service.domainCommand(request, epoch, revision, "manual-create", cardInput(name));
    }

    static CampaignView setHp(CampaignStorageService service, String request, long epoch, long revision,
                              String character, long version, int hp) {
        return service.domainCommand(request, epoch, revision, "manual-field", hpInput(character, version, hp));
    }

    static String cardId(CampaignView view) { return currentCard(view).get("id").getAsString(); }
    static int hp(CampaignView view) { return currentCard(view).getAsJsonObject("fields").get("hp.current").getAsInt(); }
    static long version(CampaignView view) { return currentCard(view).get("version").getAsLong(); }
    static int cards(CampaignView view) { return view.domain().getAsJsonObject("manual").getAsJsonArray("characters").size(); }

    private static JsonObject currentCard(CampaignView view) {
        return view.domain().getAsJsonObject("manual").getAsJsonArray("characters").get(0).getAsJsonObject().getAsJsonObject("current");
    }

    static Snapshot snapshot() {
        Snapshot empty = CampaignRuleFixtures.snapshot(CAMPAIGN, "测试");
        var commands = new CampaignDomainCommands();
        var created = commands.apply(empty, "manual-create", cardInput("角色"),
                UUID.fromString("00000000-0000-4000-8000-000000000002"), "a".repeat(64), CampaignLifecycleFixture::unexpectedDie);
        String character = created.manual().characters().getFirst().id().toString();
        var edited = commands.apply(new Snapshot(CAMPAIGN, empty.name(), created), "manual-field", hpInput(character, 0, 8),
                UUID.fromString("00000000-0000-4000-8000-000000000004"), "b".repeat(64), CampaignLifecycleFixture::unexpectedDie);
        return new Snapshot(CAMPAIGN, empty.name(), edited);
    }

    static byte[] fileBytes() { return Portable.BYTES.clone(); }

    private static final class Portable {
        private static final byte[] BYTES = new CampaignCodec().encode(new Document(FILE, snapshot()));
    }

    private static int unexpectedDie(int sides) { throw new AssertionError("Unexpected die"); }

    private static JsonObject cardInput(String name) {
        JsonObject input = new JsonObject();
        input.addProperty("name", name);
        input.addProperty("kind", "NPC");
        input.addProperty("template", "guard");
        return input;
    }

    private static JsonObject hpInput(String character, long version, int hp) {
        JsonObject input = new JsonObject();
        input.addProperty("characterId", character);
        input.addProperty("characterVersion", Long.toString(version));
        input.addProperty("fieldKey", "hp.current");
        input.addProperty("value", Integer.toString(hp));
        JsonObject focus = new JsonObject();
        focus.add("space", JsonNull.INSTANCE);
        focus.addProperty("generation", "0");
        input.add("focus", focus);
        return input;
    }
}
