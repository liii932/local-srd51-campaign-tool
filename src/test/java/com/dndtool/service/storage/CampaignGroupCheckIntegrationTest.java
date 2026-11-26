package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.Event;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CharacterRuntime.ROLLED_CHECK;
import static com.dndtool.service.storage.CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE;
import static org.junit.jupiter.api.Assertions.*;

class CampaignGroupCheckIntegrationTest {
    private static final RuleIdentity ATHLETICS = new RuleIdentity(
            "srd51_runtime", "character.proficiency", "proficiency.skill.athletics");
    private static final List<Integer> NATURAL_DICE = List.of(5, 11, 16, 20, 8, 6);
    private static final List<Integer> REOPEN_DICE = List.of(5, 11, 16, 20, 8, 12);
    private static final String SUCCESS_MESSAGE = "队伍稳住了渡船。";
    private static final String FAILURE_MESSAGE = "队伍仍需攀住缆绳。";

    @TempDir Path temporary;

    @Test void standardTeamCanContinueAfterSaveEndAndReopen() throws Exception {
        assertContinuity(true);
    }

    @Test void houseThresholdChangesTeamResultAndSurvivesSaveEndAndReopen() throws Exception {
        assertContinuity(false);
    }

    private void assertContinuity(boolean standard) throws Exception {
        var dice = new Dice();
        try (var service = new CampaignStorageService(temporary, boundary -> { }, dice::next)) {
            if (standard) service.create("标准团队属性检定");
            else CampaignRuleFixtures.create(service, "家规团队属性检定", BundledCampaignRules.CHECK_VARIANT);
            UUID world = CampaignSpaceFixtures.world(service, "渡口");
            List<UUID> party = party(service, world);
            var before = service.accessSnapshot();
            var input = input(before, party);
            String request = id();
            dice.addCheck();
            var accepted = submit(service, request, before, input);
            var after = service.accessSnapshot();
            assertEquals(before.revision() + 1, accepted.revision());
            assertResolved(before, after, party, world, standard, NATURAL_DICE, 2);
            assertEquals(NATURAL_DICE, dice.used);
            assertTrue(submit(service, request, before, input).lastCommand().replayed());
            var changed = input.deepCopy();
            changed.addProperty("successMessage", "更换成功消息");
            assertCode("IDEMPOTENCY_CONFLICT", () -> submit(service, request, before, changed));
            assertEquals(after, service.accessSnapshot());
            assertEquals(NATURAL_DICE, dice.used);

            var saved = service.save(id(), accepted.epoch(), accepted.revision());
            var ended = service.end(id(), accepted.epoch(), accepted.revision());
            var codec = new CampaignCodec();
            assertEquals(after.snapshot(), codec.decode(Files.readAllBytes(service.savedFile(saved.fileKey()))).snapshot());
            byte[] complete = Files.readAllBytes(service.savedFile(ended.fileKey()));
            assertEquals(after.snapshot(), codec.decode(complete).snapshot());
            assertCorruptAuditRejected(complete);
            var reopened = service.open(ended.fileKey());
            assertNotEquals(accepted.epoch(), reopened.epoch());
            var reopenedBefore = service.accessSnapshot();
            assertEquals(after.snapshot(), reopenedBefore.snapshot());
            assertEquals(NATURAL_DICE, dice.used);

            dice.queued.addAll(REOPEN_DICE);
            submit(service, id(), reopenedBefore, input(reopenedBefore, party));
            assertResolved(reopenedBefore, service.accessSnapshot(), party, world, standard, REOPEN_DICE, 3);
            List<Integer> expected = new ArrayList<>(NATURAL_DICE);
            expected.addAll(REOPEN_DICE);
            assertEquals(expected, dice.used);
        }
    }

    @Test void invalidFinalParticipantAndInvalidUnselectedMessageConsumeNoDiceAndWriteNothing() throws Exception {
        var dice = new Dice();
        try (var service = new CampaignStorageService(temporary, boundary -> { }, dice::next)) {
            service.create("团队属性检定预检");
            UUID world = CampaignSpaceFixtures.world(service, "渡口");
            List<UUID> party = party(service, world);
            var before = service.accessSnapshot();
            var valid = input(before, party);
            var stale = valid.deepCopy();
            stale.getAsJsonArray("participants").asList().getLast().getAsJsonObject().addProperty("characterVersion", "99");
            assertCode("STALE_CHARACTER", () -> submit(service, id(), before, stale));

            var duplicate = valid.deepCopy();
            var duplicateRows = duplicate.getAsJsonArray("participants");
            duplicateRows.set(0, duplicateRows.get(1).deepCopy());
            assertCode("GROUP_CHECK_DUPLICATE_PARTICIPANT", () -> submit(service, id(), before, duplicate));

            var invalidMessage = valid.deepCopy();
            invalidMessage.addProperty("failureMessage", "失败\t消息");
            assertCode("INVALID_FILE", () -> submit(service, id(), before, invalidMessage));
            assertEquals(before, service.accessSnapshot());

            UUID otherWorld = CampaignSpaceFixtures.world(service, "山口");
            UUID distant = placeFighter(service, otherWorld, "远行者");
            CampaignSpaceFixtures.command(service, "focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(world)));
            var splitParty = service.accessSnapshot();
            var splitMembers = new ArrayList<>(party);
            splitMembers.set(splitMembers.size() - 1, distant);
            assertCode("SPACE_FROZEN", () -> submit(service, id(), splitParty, input(splitParty, splitMembers)));
            assertEquals(splitParty, service.accessSnapshot());
            assertTrue(dice.used.isEmpty());
        }
    }

    @Test void lateWriteFailureRollsBackTheWholeTeamAndAllowsRetry() throws Exception {
        var dice = new Dice();
        var failAfterWrite = new AtomicBoolean();
        try (var service = new CampaignStorageService(temporary, boundary -> {
            if (boundary == AFTER_CHARACTER_UPDATE && failAfterWrite.getAndSet(false))
                throw new IllegalStateException("Injected group check write failure");
        }, dice::next)) {
            service.create("团队属性检定回滚");
            UUID world = CampaignSpaceFixtures.world(service, "渡口");
            List<UUID> party = party(service, world);
            var before = service.accessSnapshot();
            var input = input(before, party);
            String request = id();
            failAfterWrite.set(true);
            dice.addCheck();
            assertEquals("Injected group check write failure", assertThrows(IllegalStateException.class,
                    () -> submit(service, request, before, input)).getMessage());
            assertEquals(before, service.accessSnapshot());
            assertEquals(NATURAL_DICE, dice.used);

            dice.addCheck();
            submit(service, request, before, input);
            var after = service.accessSnapshot();
            assertResolved(before, after, party, world, true, NATURAL_DICE, 2);
            assertTrue(submit(service, request, before, input).lastCommand().replayed());
            assertEquals(after, service.accessSnapshot());
            assertEquals(NATURAL_DICE.size() * 2, dice.used.size());
        }
    }

    private static void assertResolved(CampaignStorageService.AccessSnapshot before,
            CampaignStorageService.AccessSnapshot after, List<UUID> party, UUID world, boolean standard,
            List<Integer> dice, int successCount) {
        var domains = after.snapshot().domains();
        var root = domains.roots().getLast();
        assertEquals(before.snapshot().domains().roots().size() + 1, domains.roots().size());
        assertEquals("group-check", root.kind());
        assertEquals(world, root.subjectId());
        assertEquals(dice.stream().map(value -> new CampaignSpaceData.Die(20, value)).toList(), root.rolls());
        var audit = JsonParser.parseString(root.after()).getAsJsonObject();
        assertEquals(Integer.toString(successCount), audit.get("successCount").getAsString());
        assertEquals(standard ? "1" : "2", audit.get("successNumerator").getAsString());
        assertEquals(standard ? "2" : "3", audit.get("successDenominator").getAsString());
        assertEquals(standard ? "2" : "3", audit.get("requiredSuccessCount").getAsString());
        boolean success = standard || successCount == 3;
        assertEquals(success, audit.get("success").getAsBoolean());
        assertEquals(success ? SUCCESS_MESSAGE : FAILURE_MESSAGE, audit.get("message").getAsString());
        assertFalse(root.after().contains(success ? FAILURE_MESSAGE : SUCCESS_MESSAGE));
        var references = audit.getAsJsonArray("participants");
        assertEquals(party.size(), references.size());
        List<UUID> children = new ArrayList<>();
        for (int index = 0; index < party.size(); index++) {
            UUID actor = party.get(index);
            Event event = character(after, actor).history().events().getLast();
            children.add(event.operationId());
            var reference = references.get(index).getAsJsonObject();
            assertEquals(actor.toString(), reference.get("characterId").getAsString());
            assertEquals(event.operationId().toString(), reference.get("checkOperationId").getAsString());
            assertEquals(ROLLED_CHECK, event.command().kind());
            assertEquals(ATHLETICS, event.command().checkInput().proficiency());
            assertEquals(character(before, actor).history().current(), event.before());
            assertEquals(event.before().version() + 1, event.after().version());
            var result = event.checkResult();
            assertEquals(List.of(5, 16, 20, dice.getLast()).get(index), result.firstD20());
            if (index == 0 || index == 2) assertEquals(index == 0 ? 11 : 8, result.secondD20());
            else assertNull(result.secondD20());
            assertEquals(List.of(11, 16, 8, dice.getLast()).get(index), result.selectedD20());
            assertEquals(List.of(15, 20, 12, dice.getLast() + 4).get(index), result.total());
            assertEquals(index < 2 || index == 3 && successCount == 3, result.success());
        }
        assertEquals(children.stream().sorted(Comparator.comparing(UUID::toString)).toList(), root.characterOperations());
    }

    private static void assertCorruptAuditRejected(byte[] complete) {
        var file = CampaignJson.read(complete);
        var roots = file.getAsJsonObject("domains").getAsJsonArray("roots");
        var root = roots.asList().getLast().getAsJsonObject();
        var audit = JsonParser.parseString(root.get("after").getAsString()).getAsJsonObject();
        audit.addProperty("successCount", "3");
        root.addProperty("after", new String(CampaignJson.canonical(audit), java.nio.charset.StandardCharsets.UTF_8));
        var integrity = file.remove("integrity").getAsJsonObject();
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        assertCode("INVALID_FILE", () -> new CampaignCodec().decode(CampaignJson.canonical(file)));
    }

    private static CampaignDomainData.Character character(CampaignStorageService.AccessSnapshot snapshot, UUID id) {
        return snapshot.snapshot().domains().characters().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<UUID> party(CampaignStorageService service, UUID world) {
        List<UUID> actors = new ArrayList<>();
        for (String name : List.of("阿尔", "柏树", "岑石", "丹岩")) actors.add(placeFighter(service, world, name));
        return actors.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private static UUID placeFighter(CampaignStorageService service, UUID world, String name) {
        var created = CampaignSpaceFixtures.command(service, "character-create",
                object("name", name, "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID actor = UUID.fromString(created.lastCommand().subjectId());
        CampaignSpaceFixtures.place(service, actor, world);
        return actor;
    }

    private static JsonObject input(CampaignStorageService.AccessSnapshot snapshot, List<UUID> party) {
        var result = object("kind", "SKILL", "abilityKey", "ability.strength", "difficultyClass", 15,
                "adjudication", "全队稳住渡船", "successMessage", SUCCESS_MESSAGE, "failureMessage", FAILURE_MESSAGE);
        result.add("rule", CampaignDomainCodec.toJson(snapshot.snapshot().domains().material().restore().checkRule()));
        result.add("proficiency", CampaignDomainCodec.toJson(ATHLETICS));
        var participants = new JsonArray();
        // Reverse submission proves rolls are assigned by the server's identifier order.
        for (int index = party.size() - 1; index >= 0; index--) {
            UUID actor = party.get(index);
            var row = object("characterId", actor, "characterVersion", character(snapshot, actor).history().current().version());
            row.addProperty("advantage", index == 0 || index == 3);
            row.addProperty("disadvantage", index == 2 || index == 3);
            participants.add(row);
        }
        result.add("participants", participants);
        return result;
    }

    private static CampaignStorageService.CampaignView submit(CampaignStorageService service, String request,
            CampaignStorageService.AccessSnapshot snapshot, JsonObject input) {
        return service.domainCommand(request, snapshot.epoch(), snapshot.revision(), "group-check",
                CampaignSpaceFixtures.focused(snapshot.snapshot().domains(), input));
    }

    private static JsonObject object(Object... fields) {
        var result = new JsonObject();
        for (int index = 0; index < fields.length; index += 2)
            result.addProperty((String) fields[index], fields[index + 1].toString());
        return result;
    }

    private static void assertCode(String code, Executable action) {
        assertEquals(code, assertThrows(StorageException.class, action).code());
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static final class Dice {
        private final ArrayDeque<Integer> queued = new ArrayDeque<>();
        private final List<Integer> used = new ArrayList<>();

        private void addCheck() { queued.addAll(NATURAL_DICE); }

        private int next(int sides) {
            assertEquals(20, sides);
            assertFalse(queued.isEmpty(), "Unexpected d20 before the complete request was accepted");
            int value = queued.removeFirst();
            used.add(value);
            return value;
        }
    }
}
