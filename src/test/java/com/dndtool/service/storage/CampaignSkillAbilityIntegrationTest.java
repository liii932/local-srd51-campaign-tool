package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.CheckResult;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.Event;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE;
import static org.junit.jupiter.api.Assertions.*;

class CampaignSkillAbilityIntegrationTest {
    private static final String STR = "ability.strength", CON = "ability.constitution", WIS = "ability.wisdom",
            CHA = "ability.charisma";
    private static final RuleIdentity ATHLETICS = new RuleIdentity("srd51_runtime", "character.proficiency",
            "proficiency.skill.athletics");
    private static final CheckResult SUCCESS = new CheckResult(10, null, 10, 2, 2, 14, true, false, false, 2, null);

    @TempDir Path temporary;

    @Test void standardAcceptsTheDefaultAndRejectsAnAlternateBeforeDice() throws Exception {
        List<Integer> dice = new ArrayList<>();
        try (var service = new CampaignStorageService(temporary, boundary -> { }, sides -> {
            dice.add(sides);
            return 10;
        })) {
            service.create("标准技能检定");
            UUID actor = fighter(service);
            var before = service.accessSnapshot();
            var alternate = check(before, actor, CON);
            assertEquals("S2_CHECK_SKILL_UNSUPPORTED", assertCode("DOMAIN_REJECTED",
                    () -> submit(service, id(), before, "character-command", alternate)).getMessage());
            assertEquals(before, service.accessSnapshot());
            assertTrue(dice.isEmpty());

            var ordinary = check(before, actor, STR);
            String request = id();
            var accepted = submit(service, request, before, "character-command", ordinary);
            var retained = checkFields(lastEvent(accepted.domain()));
            assertEquals(STR, retained.get("abilityKey").getAsString());
            assertEquals(ATHLETICS.ruleKey(), retained.getAsJsonObject("proficiency").get("ruleKey").getAsString());
            assertEquals(SUCCESS, lastEvent(service.accessSnapshot()).checkResult());
            assertTrue(submit(service, request, before, "character-command", ordinary).lastCommand().replayed());
            assertCode("IDEMPOTENCY_CONFLICT", () -> submit(service, request, before, "character-command", alternate));
            saveEndAndReopen(service, accepted);
            assertEquals(List.of(20), dice);
            var reopened = service.accessSnapshot();
            assertCode("DOMAIN_REJECTED", () -> submit(service, id(), reopened, "character-command", check(reopened, actor, CON)));
            assertEquals(reopened, service.accessSnapshot());
            assertEquals(List.of(20), dice);
            submit(service, id(), reopened, "character-command", check(reopened, actor, STR));
            assertEquals(SUCCESS, lastEvent(service.accessSnapshot()).checkResult());
            assertEquals(List.of(20, 20), dice);
        }
    }

    @Test void variantCommitsTheSelectedAbilityAndBranchTogetherAndReplaysTheOriginalDice() throws Exception {
        List<Integer> dice = new ArrayList<>();
        var failAfterWrite = new AtomicBoolean();
        try (var service = new CampaignStorageService(temporary, boundary -> {
            if (boundary == AFTER_CHARACTER_UPDATE && failAfterWrite.getAndSet(false))
                throw new IllegalStateException("Injected check write failure");
        }, sides -> {
            dice.add(sides);
            return 10;
        })) {
            CampaignRuleFixtures.create(service, "变体技能检定", BundledCampaignRules.CHECK_VARIANT);
            UUID actor = fighter(service);
            var plain = service.accessSnapshot();
            submit(service, id(), plain, "character-command", check(plain, actor, CON));
            assertEquals(SUCCESS, lastEvent(service.accessSnapshot()).checkResult());

            var itemInput = object("characterId", actor, "name", "绳索", "quantity", 3);
            itemInput.add("itemId", JsonNull.INSTANCE);
            CampaignSpaceFixtures.command(service, "item", itemInput);
            var before = service.accessSnapshot();
            UUID item = before.snapshot().domains().items().getFirst().id();
            var alternate = check(before, actor, CON);
            alternate.add("success", branch(actor, item, 2, "找到登岸点"));
            alternate.add("failure", branch(actor, item, -1, "体力不支"));
            var invalidFailure = alternate.deepCopy();
            invalidFailure.getAsJsonObject("failure").addProperty("itemDelta", "-4");
            assertCode("INVALID_FILE", () -> submit(service, id(), before, "check-effects", invalidFailure));
            var unknownAbility = alternate.deepCopy();
            checkFields(unknownAbility).addProperty("abilityKey", "ability.luck");
            assertCode("INVALID_DOMAIN_DOCUMENT", () -> submit(service, id(), before, "check-effects", unknownAbility));
            assertEquals(before, service.accessSnapshot());
            assertEquals(List.of(20), dice);

            String request = id();
            failAfterWrite.set(true);
            assertEquals("Injected check write failure", assertThrows(IllegalStateException.class,
                    () -> submit(service, request, before, "check-effects", alternate)).getMessage());
            assertEquals(before, service.accessSnapshot());
            assertEquals(List.of(20, 20), dice);

            var accepted = submit(service, request, before, "check-effects", alternate);
            var after = service.accessSnapshot();
            var state = after.snapshot().domains();
            var event = lastEvent(after);
            var root = state.roots().getLast();
            var audit = CampaignDomainCodec.fromJson(JsonParser.parseString(root.after()), CampaignCheckEffects.Audit.class);
            assertEquals(SUCCESS, event.checkResult());
            assertEquals(CON, event.command().checkInput().abilityKey());
            assertEquals(ATHLETICS, event.command().checkInput().proficiency());
            assertEquals(event.command().checkInput(), audit.input().command().checkInput());
            assertEquals("找到登岸点", audit.message());
            assertEquals(5, state.items().getFirst().quantity());
            assertEquals(before.revision() + 1, accepted.revision());
            assertEquals(before.snapshot().domains().roots().size() + 1, state.roots().size());
            assertEquals(List.of(event.operationId()), root.characterOperations());
            assertEquals(List.of(new CampaignSpaceData.Die(20, 10)), root.rolls());
            assertTrue(submit(service, request, before, "check-effects", alternate).lastCommand().replayed());
            var changedAbility = alternate.deepCopy();
            checkFields(changedAbility).addProperty("abilityKey", WIS);
            assertCode("IDEMPOTENCY_CONFLICT", () -> submit(service, request, before, "check-effects", changedAbility));
            assertEquals(after, service.accessSnapshot());

            var saved = saveEndAndReopen(service, accepted);
            var file = CampaignJson.read(saved);
            checkFields(lastEvent(file.getAsJsonObject("domains"))).addProperty("abilityKey", STR);
            var integrity = file.remove("integrity").getAsJsonObject();
            integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
            file.add("integrity", integrity);
            assertCode("INVALID_FILE", () -> new CampaignCodec().decode(CampaignJson.canonical(file)));
            assertEquals(List.of(20, 20, 20), dice);
            var reopened = service.accessSnapshot();
            submit(service, id(), reopened, "character-command", check(reopened, actor, CHA));
            var continued = lastEvent(service.accessSnapshot());
            assertEquals(CHA, continued.command().checkInput().abilityKey());
            assertEquals(ATHLETICS, continued.command().checkInput().proficiency());
            assertEquals(new CheckResult(10, null, 10, 0, 2, 12, false, false, false, 2, null), continued.checkResult());
            assertEquals(List.of(20, 20, 20, 20), dice);
        }
    }

    private static UUID fighter(CampaignStorageService service) {
        UUID world = CampaignSpaceFixtures.world(service, "渡口");
        var created = CampaignSpaceFixtures.command(service, "character-create",
                object("name", "战士", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID actor = UUID.fromString(created.lastCommand().subjectId());
        CampaignSpaceFixtures.place(service, actor, world);
        return actor;
    }

    private static JsonObject check(CampaignStorageService.AccessSnapshot before, UUID actor, String ability) {
        var input = new CheckInput(before.snapshot().domains().material().restore().checkRule(), "SKILL", ability, ATHLETICS,
                14, false, false, "试着游过河道。");
        var command = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, input);
        var result = object("characterId", actor, "characterVersion", lastEvent(before).after().version());
        result.add("command", CampaignDomainCodec.toJson(command));
        return result;
    }

    private static Event lastEvent(CampaignStorageService.AccessSnapshot snapshot) {
        return snapshot.snapshot().domains().characters().getFirst().history().events().getLast();
    }

    private static JsonObject lastEvent(JsonObject domains) {
        var character = domains.getAsJsonArray("characters").get(0).getAsJsonObject();
        return character.getAsJsonObject("history").getAsJsonArray("events").asList().getLast().getAsJsonObject();
    }

    private static JsonObject branch(UUID actor, UUID item, int delta, String message) {
        var result = object("targetId", actor, "itemId", item, "itemDelta", delta, "message", message);
        result.add("healthEffect", JsonNull.INSTANCE);
        return result;
    }

    private static JsonObject checkFields(JsonObject input) {
        return input.getAsJsonObject("command").getAsJsonObject("checkInput");
    }

    private static byte[] saveEndAndReopen(CampaignStorageService service, CampaignStorageService.CampaignView accepted)
            throws Exception {
        var snapshot = service.accessSnapshot().snapshot();
        String saved = service.save(id(), accepted.epoch(), accepted.revision()).fileKey();
        String ended = service.end(id(), accepted.epoch(), accepted.revision()).fileKey();
        var codec = new CampaignCodec();
        assertEquals(snapshot, codec.decode(Files.readAllBytes(service.savedFile(saved))).snapshot());
        byte[] complete = Files.readAllBytes(service.savedFile(ended));
        assertEquals(snapshot, codec.decode(complete).snapshot());
        var reopened = service.open(ended);
        assertNotEquals(accepted.epoch(), reopened.epoch());
        assertEquals(accepted.domain(), reopened.domain());
        assertEquals(accepted.history(), reopened.history());
        return complete;
    }

    private static CampaignStorageService.CampaignView submit(CampaignStorageService service, String request,
            CampaignStorageService.AccessSnapshot before, String action, JsonObject input) {
        return service.domainCommand(request, before.epoch(), before.revision(), action,
                CampaignSpaceFixtures.focused(before.snapshot().domains(), input));
    }

    private static JsonObject object(Object... fields) {
        var result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) result.addProperty((String) fields[i], fields[i + 1].toString());
        return result;
    }

    private static StorageException assertCode(String code, Executable action) {
        var error = assertThrows(StorageException.class, action);
        assertEquals(code, error.code());
        return error;
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
