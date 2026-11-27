package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE;
import static org.junit.jupiter.api.Assertions.*;

class CampaignContestCheckIntegrationTest {
    private static final RuleIdentity ATHLETICS = new RuleIdentity(
            "srd51_runtime", "character.proficiency", "proficiency.skill.athletics");
    private static final List<Integer> NARROW_WIN = List.of(4, 12, 14, 19);
    private static final List<Integer> TIE = List.of(9, 11, 14, 19);
    private static final List<Integer> RESPONDER_WIN = List.of(6, 10, 15, 19);

    @TempDir Path temporary;

    @Test void standardContestWinsByOneAndCanContinueAfterSaveEndAndReopen() throws Exception {
        assertContinuity(true);
    }

    @Test void houseContestStaysUnchangedByOneAndCanContinueAfterSaveEndAndReopen() throws Exception {
        assertContinuity(false);
    }

    private void assertContinuity(boolean standard) throws Exception {
        var dice = new Dice();
        try (var service = new CampaignStorageService(temporary, boundary -> { }, dice::next)) {
            if (standard) service.create("标准对抗检定");
            else CampaignRuleFixtures.create(service, "家规对抗检定", BundledCampaignRules.CHECK_VARIANT);
            Party party = party(service);
            int threshold = standard ? 1 : 2;
            var before = service.accessSnapshot();
            var input = input(before, party);
            String request = id();
            dice.add(NARROW_WIN);
            var accepted = submit(service, request, before, input);
            var after = service.accessSnapshot();
            assertEquals(before.revision() + 1, accepted.revision());
            assertResolved(before, after, party, threshold, NARROW_WIN, standard ? "INITIATOR_WINS" : "UNCHANGED");
            assertTrue(submit(service, request, before, input).lastCommand().replayed());
            var changed = input.deepCopy();
            changed.addProperty("adjudication", "更换对抗说明");
            assertCode("IDEMPOTENCY_CONFLICT", () -> submit(service, request, before, changed));
            assertEquals(after, service.accessSnapshot());
            assertEquals(NARROW_WIN, dice.used);

            String saved = service.save(id(), accepted.epoch(), accepted.revision()).fileKey();
            String ended = service.end(id(), accepted.epoch(), accepted.revision()).fileKey();
            var codec = new CampaignCodec();
            assertEquals(after.snapshot(), codec.decode(Files.readAllBytes(service.savedFile(saved))).snapshot());
            byte[] complete = Files.readAllBytes(service.savedFile(ended));
            assertEquals(after.snapshot(), codec.decode(complete).snapshot());
            assertCorruptAuditRejected(complete);
            var reopened = service.open(ended);
            assertNotEquals(accepted.epoch(), reopened.epoch());
            var reopenedBefore = service.accessSnapshot();
            assertEquals(after.snapshot(), reopenedBefore.snapshot());
            assertEquals(NARROW_WIN, dice.used);

            dice.add(TIE);
            submit(service, id(), reopenedBefore, input(reopenedBefore, party));
            var tied = service.accessSnapshot();
            assertResolved(reopenedBefore, tied, party, threshold, TIE, "UNCHANGED");
            dice.add(RESPONDER_WIN);
            submit(service, id(), tied, input(tied, party));
            assertResolved(tied, service.accessSnapshot(), party, threshold, RESPONDER_WIN, "RESPONDER_WINS");
            List<Integer> expected = new ArrayList<>(NARROW_WIN);
            expected.addAll(TIE);
            expected.addAll(RESPONDER_WIN);
            assertEquals(expected, dice.used);
        }
    }

    @Test void staleResponderAndOtherIllegalInputsConsumeNoDiceAndWriteNothing() throws Exception {
        var dice = new Dice();
        try (var service = new CampaignStorageService(temporary, boundary -> { }, dice::next)) {
            service.create("对抗检定预检");
            Party party = party(service);
            var before = service.accessSnapshot();
            var valid = input(before, party);

            var stale = valid.deepCopy();
            stale.getAsJsonObject("responder").addProperty("characterVersion", "99");
            assertCode("STALE_CHARACTER", () -> submit(service, id(), before, stale));

            var duplicate = valid.deepCopy();
            duplicate.add("responder", duplicate.getAsJsonObject("initiator").deepCopy());
            assertCode("CONTEST_CHECK_DUPLICATE_PARTICIPANT", () -> submit(service, id(), before, duplicate));

            var badSkill = valid.deepCopy();
            var responder = badSkill.getAsJsonObject("responder");
            responder.addProperty("kind", "SKILL");
            responder.add("proficiency", CampaignDomainCodec.toJson(ATHLETICS));
            assertCode("DOMAIN_REJECTED", () -> submit(service, id(), before, badSkill));

            var badReason = valid.deepCopy();
            badReason.addProperty("adjudication", "非法\t说明");
            assertCode("INVALID_FILE", () -> submit(service, id(), before, badReason));
            assertEquals(before, service.accessSnapshot());

            UUID remote = CampaignSpaceFixtures.world(service, "山口");
            UUID distant = placeFighter(service, remote, "远行者");
            CampaignSpaceFixtures.command(service, "focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(party.world())));
            var split = service.accessSnapshot();
            var splitParty = new Party(party.world(), party.initiator(), distant);
            assertCode("SPACE_FROZEN", () -> submit(service, id(), split, input(split, splitParty)));
            assertEquals(split, service.accessSnapshot());
            assertTrue(dice.used.isEmpty());
        }
    }

    @Test void lateFailureRollsBackResultAndReceiptAndAllowsRetry() throws Exception {
        var dice = new Dice();
        var failAfterWrite = new AtomicBoolean();
        try (var service = new CampaignStorageService(temporary, boundary -> {
            if (boundary == AFTER_CHARACTER_UPDATE && failAfterWrite.getAndSet(false))
                throw new IllegalStateException("Injected contest write failure");
        }, dice::next)) {
            service.create("对抗检定回滚");
            Party party = party(service);
            var before = service.accessSnapshot();
            var input = input(before, party);
            String request = id();
            failAfterWrite.set(true);
            dice.add(NARROW_WIN);
            assertEquals("Injected contest write failure", assertThrows(IllegalStateException.class,
                    () -> submit(service, request, before, input)).getMessage());
            assertEquals(before, service.accessSnapshot());
            assertEquals(NARROW_WIN, dice.used);

            dice.add(NARROW_WIN);
            submit(service, request, before, input);
            var after = service.accessSnapshot();
            assertResolved(before, after, party, 1, NARROW_WIN, "INITIATOR_WINS");
            assertTrue(submit(service, request, before, input).lastCommand().replayed());
            assertEquals(after, service.accessSnapshot());
            assertEquals(8, dice.used.size());
        }
    }

    private static void assertResolved(CampaignStorageService.AccessSnapshot before, CampaignStorageService.AccessSnapshot after,
            Party party, int threshold, List<Integer> dice, String outcome) {
        var earlier = before.snapshot().domains();
        var current = after.snapshot().domains();
        var root = current.roots().getLast();
        assertEquals(earlier.roots().size() + 1, current.roots().size());
        assertEquals(earlier.characters(), current.characters());
        assertEquals(earlier.positions(), current.positions());
        assertEquals(earlier.space(), current.space());
        assertEquals("contest-check", root.kind());
        assertEquals(party.world(), root.subjectId());
        assertTrue(root.characterOperations().isEmpty());
        assertEquals(dice.stream().map(value -> new CampaignSpaceData.Die(20, value)).toList(), root.rolls());
        var audit = JsonParser.parseString(root.after()).getAsJsonObject();
        assertEquals(Integer.toString(threshold), audit.get("minimumWinningMargin").getAsString());
        assertEquals(outcome, audit.get("outcome").getAsString());
        assertSide(audit.getAsJsonObject("initiator"), party.initiator(), dice.get(0), dice.get(1), dice.get(1), 2, 2, true);
        assertSide(audit.getAsJsonObject("responder"), party.responder(), dice.get(2), dice.get(3), dice.get(2), 1, 0, false);
        var retained = JsonParser.parseString(root.space().request()).getAsJsonObject();
        assertEquals("争夺过河缆绳", retained.get("adjudication").getAsString());
        assertEquals("ability.strength", retained.getAsJsonObject("initiator").get("abilityKey").getAsString());
        assertEquals("ability.dexterity", retained.getAsJsonObject("responder").get("abilityKey").getAsString());
    }

    private static void assertSide(JsonObject side, UUID id, int first, int second, int selected,
            int modifier, int proficiency, boolean advantage) {
        assertEquals(id.toString(), side.get("characterId").getAsString());
        assertEquals(Integer.toString(first), side.get("firstD20").getAsString());
        assertEquals(Integer.toString(second), side.get("secondD20").getAsString());
        assertEquals(Integer.toString(selected), side.get("selectedD20").getAsString());
        assertEquals(Integer.toString(modifier), side.get("abilityModifier").getAsString());
        assertEquals(Integer.toString(proficiency), side.get("proficiencyBonus").getAsString());
        assertEquals(Integer.toString(selected + modifier + proficiency), side.get("total").getAsString());
        assertEquals(advantage, side.get("effectiveAdvantage").getAsBoolean());
        assertEquals(!advantage, side.get("effectiveDisadvantage").getAsBoolean());
    }

    private static void assertCorruptAuditRejected(byte[] complete) {
        var file = CampaignJson.read(complete);
        var root = file.getAsJsonObject("domains").getAsJsonArray("roots").asList().getLast().getAsJsonObject();
        var audit = JsonParser.parseString(root.get("after").getAsString()).getAsJsonObject();
        audit.addProperty("outcome", "RESPONDER_WINS");
        root.addProperty("after", new String(CampaignJson.canonical(audit), StandardCharsets.UTF_8));
        var integrity = file.remove("integrity").getAsJsonObject();
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        assertCode("INVALID_FILE", () -> new CampaignCodec().decode(CampaignJson.canonical(file)));
    }

    private static Party party(CampaignStorageService service) {
        UUID world = CampaignSpaceFixtures.world(service, "渡口");
        UUID first = placeFighter(service, world, "柏树"), second = placeFighter(service, world, "岑石");
        // Give the initiator the larger identifier to prove the request's role order controls the dice.
        return first.toString().compareTo(second.toString()) > 0 ? new Party(world, first, second) : new Party(world, second, first);
    }

    private static UUID placeFighter(CampaignStorageService service, UUID world, String name) {
        var created = CampaignSpaceFixtures.command(service, "character-create",
                object("name", name, "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID actor = UUID.fromString(created.lastCommand().subjectId());
        CampaignSpaceFixtures.place(service, actor, world);
        return actor;
    }

    private static JsonObject input(CampaignStorageService.AccessSnapshot snapshot, Party party) {
        var input = object("adjudication", "争夺过河缆绳");
        input.add("rule", CampaignDomainCodec.toJson(snapshot.snapshot().domains().material().restore().checkRule()));
        var initiator = side(snapshot, party.initiator(), "SKILL", "ability.strength", true);
        initiator.add("proficiency", CampaignDomainCodec.toJson(ATHLETICS));
        input.add("initiator", initiator);
        var responder = side(snapshot, party.responder(), "ABILITY", "ability.dexterity", false);
        responder.add("proficiency", JsonNull.INSTANCE);
        input.add("responder", responder);
        return input;
    }

    private static JsonObject side(CampaignStorageService.AccessSnapshot snapshot, UUID actor, String kind,
            String ability, boolean advantage) {
        long version = snapshot.snapshot().domains().characters().stream().filter(character -> character.id().equals(actor))
                .findFirst().orElseThrow().history().current().version();
        var result = object("characterId", actor, "characterVersion", version, "kind", kind, "abilityKey", ability);
        result.addProperty("advantage", advantage);
        result.addProperty("disadvantage", !advantage);
        return result;
    }

    private static CampaignStorageService.CampaignView submit(CampaignStorageService service, String request,
            CampaignStorageService.AccessSnapshot snapshot, JsonObject input) {
        return service.domainCommand(request, snapshot.epoch(), snapshot.revision(), "contest-check",
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

    private record Party(UUID world, UUID initiator, UUID responder) { }

    private static final class Dice {
        private final ArrayDeque<Integer> queued = new ArrayDeque<>();
        private final List<Integer> used = new ArrayList<>();

        private void add(List<Integer> dice) { queued.addAll(dice); }

        private int next(int sides) {
            assertEquals(20, sides);
            assertFalse(queued.isEmpty(), "Unexpected d20 before the complete request was accepted");
            int value = queued.removeFirst();
            used.add(value);
            return value;
        }
    }
}
