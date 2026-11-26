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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CharacterRuntime.PASSIVE_CHECK;
import static com.dndtool.service.Schema2CharacterRuntime.ROLLED_CHECK;
import static com.dndtool.service.storage.CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE;
import static org.junit.jupiter.api.Assertions.*;

class CampaignPassiveCheckIntegrationTest {
    private static final String STR = "ability.strength", CON = "ability.constitution";
    private static final RuleIdentity ATHLETICS = new RuleIdentity("srd51_runtime", "character.proficiency",
            "proficiency.skill.athletics");
    private static final RuleIdentity ROLLED_DAMAGE = new RuleIdentity("test_passive_effects", "adventure.vital_effect",
            "vital_effect.passive_d4");

    @TempDir Path temporary;

    @Test void standardReopensItsDeterministicScoreAndContinuesWithEitherCheckMode() throws Exception {
        assertContinuity(BundledCampaignRules.STANDARD, STR, 10, 5);
    }

    @Test void variantReopensItsOwnDeterministicScoreAndContinuesWithEitherCheckMode() throws Exception {
        assertContinuity(BundledCampaignRules.CHECK_VARIANT, CON, 12, 3);
    }

    private void assertContinuity(String combination, String ability, int base, int adjustment) throws Exception {
        List<Integer> dice = new ArrayList<>();
        try (var service = new CampaignStorageService(temporary, boundary -> { }, sides -> {
            dice.add(sides);
            return 10;
        })) {
            if (combination.equals(BundledCampaignRules.STANDARD)) service.create("标准被动检定");
            else CampaignRuleFixtures.create(service, "家规被动检定", combination);
            UUID actor = fighter(service);
            var before = service.accessSnapshot();
            var passive = check(before, actor, PASSIVE_CHECK, ability, 14, true, false);
            String request = id();
            var accepted = submit(service, request, before, "character-command", passive);
            var after = service.accessSnapshot();
            assertEquals(expected(base, adjustment, true, false, 14), lastCheck(after).checkResult());
            assertEquals(ability, lastCheck(after).command().checkInput().abilityKey());
            assertEquals(ATHLETICS, lastCheck(after).command().checkInput().proficiency());
            assertTrue(after.snapshot().domains().roots().getLast().rolls().isEmpty());
            assertTrue(submit(service, request, before, "character-command", passive).lastCommand().replayed());
            assertCode("IDEMPOTENCY_CONFLICT", () -> submit(service, request, before, "character-command",
                    check(before, actor, ROLLED_CHECK, ability, 14, true, false)));
            assertEquals(after, service.accessSnapshot());
            assertTrue(dice.isEmpty());

            var complete = saveEndAndOpen(service, accepted, after.snapshot());
            assertTrue(dice.isEmpty());
            var file = CampaignJson.read(complete);
            var events = file.getAsJsonObject("domains").getAsJsonArray("characters").get(0).getAsJsonObject()
                    .getAsJsonObject("history").getAsJsonArray("events");
            var altered = events.asList().getLast().getAsJsonObject().getAsJsonObject("checkResult");
            altered.addProperty("passiveBaseScore", Integer.toString(base + 1));
            altered.addProperty("total", Integer.toString(base + adjustment + 5));
            var integrity = file.remove("integrity").getAsJsonObject();
            integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
            file.add("integrity", integrity);
            assertCode("INVALID_FILE", () -> new CampaignCodec().decode(CampaignJson.canonical(file)));

            var reopened = service.accessSnapshot();
            submit(service, id(), reopened, "character-command", check(reopened, actor, PASSIVE_CHECK, ability, 14, false, true));
            assertEquals(expected(base, -adjustment, false, true, 14), lastCheck(service.accessSnapshot()).checkResult());
            assertTrue(dice.isEmpty());
            reopened = service.accessSnapshot();
            submit(service, id(), reopened, "character-command", check(reopened, actor, ROLLED_CHECK, ability, 14, false, false));
            assertEquals(new CheckResult(10, null, 10, 2, 2, 14, true, false, false, 2, null),
                    lastCheck(service.accessSnapshot()).checkResult());
            assertEquals(List.of(20), dice);
        }
    }

    @Test void passiveEffectBranchesPreflightTogetherAndRollOnlyTheSelectedConsequence() throws Exception {
        List<Integer> dice = new ArrayList<>();
        var failAfterWrite = new AtomicBoolean();
        try (var service = new CampaignStorageService(temporary, boundary -> {
            if (boundary == AFTER_CHARACTER_UPDATE && failAfterWrite.getAndSet(false))
                throw new IllegalStateException("Injected passive write failure");
        }, sides -> {
            dice.add(sides);
            return 2;
        })) {
            createWithRolledDamage(service);
            UUID actor = fighter(service);
            var itemPayload = object("characterId", actor, "name", "绳索", "quantity", 3);
            itemPayload.add("itemId", JsonNull.INSTANCE);
            CampaignSpaceFixtures.command(service, "item", itemPayload);
            var before = service.accessSnapshot();
            UUID item = before.snapshot().domains().items().getFirst().id();
            long fullHp = before.snapshot().domains().characters().getFirst().history().current().hitPoints();
            var success = check(before, actor, PASSIVE_CHECK, STR, 14, false, false);
            success.add("success", branch(actor, item, 2, ROLLED_DAMAGE, "渡口可达"));
            success.add("failure", branch(actor, item, -1, null, "偏离渡口"));
            var invalid = success.deepCopy();
            invalid.getAsJsonObject("failure").addProperty("itemDelta", "-4");
            assertCode("INVALID_FILE", () -> submit(service, id(), before, "check-effects", invalid));
            assertEquals(before, service.accessSnapshot());
            assertTrue(dice.isEmpty());

            String request = id();
            failAfterWrite.set(true);
            assertEquals("Injected passive write failure", assertThrows(IllegalStateException.class,
                    () -> submit(service, request, before, "check-effects", success)).getMessage());
            assertEquals(before, service.accessSnapshot());
            assertEquals(List.of(4), dice);
            var accepted = submit(service, request, before, "check-effects", success);
            var after = service.accessSnapshot();
            assertEquals(before.revision() + 1, accepted.revision());
            assertEquals(before.snapshot().domains().roots().size() + 1, after.snapshot().domains().roots().size());
            assertEquals(expected(10, 0, false, false, 14), lastCheck(after).checkResult());
            assertBranch(after, "渡口可达", fullHp - 2, 5, List.of(new CampaignSpaceData.Die(4, 2)));
            assertTrue(submit(service, request, before, "check-effects", success).lastCommand().replayed());
            var changedDc = success.deepCopy();
            changedDc.getAsJsonObject("command").getAsJsonObject("checkInput").addProperty("difficultyClass", "15");
            assertCode("IDEMPOTENCY_CONFLICT", () -> submit(service, request, before, "check-effects", changedDc));
            assertEquals(after, service.accessSnapshot());

            changedDc.addProperty("characterVersion", Long.toString(lastEvent(after).after().version()));
            submit(service, id(), after, "check-effects", changedDc);
            var failed = service.accessSnapshot();
            assertEquals(expected(10, 0, false, false, 15), lastCheck(failed).checkResult());
            assertBranch(failed, "偏离渡口", fullHp - 2, 4, List.of());
            assertEquals(List.of(4, 4), dice);
            saveEndAndOpen(service, service.current(), failed.snapshot());
            assertEquals(List.of(4, 4), dice);
        }
    }

    private static void createWithRolledDamage(CampaignStorageService service) throws Exception {
        var artifacts = BundledCampaignRules.artifacts(BundledCampaignRules.STANDARD);
        var effect = CampaignRuleSupportTest.authorRow(BundledCampaignRules.HOUSE_RULE_MODULE, "rules.json",
                "rule_key", "vital_effect.dm_damage");
        effect.getAsJsonObject("identity").addProperty("module_key", ROLLED_DAMAGE.moduleKey());
        effect.getAsJsonObject("identity").addProperty("rule_key", ROLLED_DAMAGE.ruleKey());
        effect.addProperty("dice_count", 1);
        effect.addProperty("dice_sides", 4);
        effect.addProperty("modifier", 0);
        var extension = CampaignRuleSupportTest.extension(ROLLED_DAMAGE.moduleKey(), artifacts, List.of(effect), List.of(), List.of());
        var members = new ArrayList<>(CampaignRuleFixtures.install(service));
        var library = service.ruleLibrary();
        var member = library.importArtifact(id(), extension.originalZip()).imported().member();
        library.accept(id(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
        members.add(member);
        service.create("被动检定及后果", members);
    }

    private static void assertBranch(CampaignStorageService.AccessSnapshot snapshot, String message, long hp, long quantity,
            List<CampaignSpaceData.Die> dice) {
        var state = snapshot.snapshot().domains();
        var root = state.roots().getLast();
        var audit = CampaignDomainCodec.fromJson(JsonParser.parseString(root.after()), CampaignCheckEffects.Audit.class);
        var check = lastCheck(snapshot);
        assertEquals(check.command(), audit.input().command());
        assertEquals(check.operationId(), audit.checkOperationId());
        assertEquals(check.checkResult().success(), audit.success());
        assertEquals(message, audit.message());
        assertEquals(dice, root.rolls());
        assertEquals(hp, lastEvent(snapshot).after().hitPoints());
        assertEquals(quantity, state.items().getFirst().quantity());
        if (dice.isEmpty()) {
            assertNull(audit.healthOperationId());
            assertEquals(List.of(check.operationId()), root.characterOperations());
        } else {
            assertEquals(lastEvent(snapshot).operationId(), audit.healthOperationId());
            assertEquals(Set.of(check.operationId(), audit.healthOperationId()), Set.copyOf(root.characterOperations()));
            assertEquals(check.after(), lastEvent(snapshot).before());
            assertEquals(List.of(2), lastEvent(snapshot).healthResult().dice());
        }
    }

    private static CheckResult expected(int base, int adjustment, boolean advantage, boolean disadvantage, int dc) {
        int total = base + adjustment + 4;
        return new CheckResult(null, null, null, 2, 2, total, total >= dc, advantage, disadvantage, 2, null, base, adjustment);
    }

    private static UUID fighter(CampaignStorageService service) {
        UUID world = CampaignSpaceFixtures.world(service, "渡口");
        var created = CampaignSpaceFixtures.command(service, "character-create",
                object("name", "战士", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID actor = UUID.fromString(created.lastCommand().subjectId());
        CampaignSpaceFixtures.place(service, actor, world);
        return actor;
    }

    private static JsonObject check(CampaignStorageService.AccessSnapshot before, UUID actor, String mode, String ability,
            int dc, boolean advantage, boolean disadvantage) {
        var input = new CheckInput(before.snapshot().domains().material().restore().checkRule(), "SKILL", ability, ATHLETICS,
                dc, advantage, disadvantage, "观察长途游泳能否到达渡口。");
        var command = new Command(mode, null, 0, null, null, null, GrowthChoices.EMPTY, null, input);
        var result = object("characterId", actor, "characterVersion", lastEvent(before).after().version());
        result.add("command", CampaignDomainCodec.toJson(command));
        return result;
    }

    private static Event lastEvent(CampaignStorageService.AccessSnapshot snapshot) {
        return snapshot.snapshot().domains().characters().getFirst().history().events().getLast();
    }

    private static Event lastCheck(CampaignStorageService.AccessSnapshot snapshot) {
        return snapshot.snapshot().domains().characters().getFirst().history().events().reversed().stream()
                .filter(event -> event.checkResult() != null).findFirst().orElseThrow();
    }

    private static JsonObject branch(UUID actor, UUID item, int delta, RuleIdentity healthEffect, String message) {
        var result = object("targetId", actor, "itemId", item, "itemDelta", delta, "message", message);
        result.add("healthEffect", CampaignDomainCodec.toJson(healthEffect));
        return result;
    }

    private static byte[] saveEndAndOpen(CampaignStorageService service, CampaignStorageService.CampaignView view,
            CampaignData.Snapshot expected) throws Exception {
        String saved = service.save(id(), view.epoch(), view.revision()).fileKey();
        String ended = service.end(id(), view.epoch(), view.revision()).fileKey();
        var codec = new CampaignCodec();
        assertEquals(expected, codec.decode(Files.readAllBytes(service.savedFile(saved))).snapshot());
        byte[] complete = Files.readAllBytes(service.savedFile(ended));
        assertEquals(expected, codec.decode(complete).snapshot());
        var reopened = service.open(ended);
        assertNotEquals(view.epoch(), reopened.epoch());
        assertEquals(expected, service.accessSnapshot().snapshot());
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

    private static void assertCode(String code, Executable action) {
        assertEquals(code, assertThrows(StorageException.class, action).code());
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
