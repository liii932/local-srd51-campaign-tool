package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CombatState;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.CampaignSpaceFixtures.command;
import static com.dndtool.service.storage.CampaignSpaceFixtures.focused;
import static com.dndtool.service.storage.CampaignSpaceFixtures.place;
import static com.dndtool.service.storage.CampaignSpaceFixtures.target;
import static com.dndtool.service.storage.CampaignSpaceFixtures.world;
import static com.dndtool.service.storage.CampaignStorageService.Boundary;
import static org.junit.jupiter.api.Assertions.*;

class CampaignScenePlacementIntegrationTest {
    @TempDir Path temporary;

    @Test void directPlacementKeepsSceneSideAndClocksAndRequiresAnEmptyCellInFocus() throws Exception {
        AtomicInteger draws = new AtomicInteger();
        try (var service = new CampaignStorageService(temporary.resolve("direct"), boundary -> {},
                sides -> { draws.incrementAndGet(); return sides; })) {
            CampaignRuleFixtures.create(service, "裁定直放");
            UUID pc = ruleCharacter(service, "甲");
            UUID npc = manualNpc(service, "乙");
            UUID absent = manualNpc(service, "未入场");
            UUID node = world(service, "城门");
            UUID scene = enterAdjudicatedScene(service, node, "室内", pc, npc);
            command(service, "scene-mode", object("mode", "COMBAT"));

            var prior = placement(service, pc);
            reject(service, "scene-place", direct(pc, prior.version(), 1, 0), "SCENE_CELL_OCCUPIED");
            reject(service, "scene-place", direct(pc, prior.version(), 0, 0), "SCENE_CELL_OCCUPIED");
            reject(service, "scene-place", direct(pc, prior.version(), 6, 0), "SCENE_GRID_BOUNDS");
            reject(service, "scene-place", direct(pc, prior.version() - 1, 5, 5), "STALE_POSITION");
            reject(service, "scene-place", direct(absent, 0, 5, 5), "SUBJECT_UNPLACED");

            var before = state(service);
            command(service, "scene-place", direct(pc, prior.version(), 5, 5));
            var after = state(service);
            var placed = placement(service, pc);
            assertEquals(CampaignSpaceData.Ref.scene(scene), placed.space());
            assertEquals(new Schema2CombatState.Position(5, 5), placed.grid());
            assertEquals(prior.side(), placed.side());
            assertEquals(prior.version() + 1, placed.version());
            assertEquals(before.space(), after.space());
            assertEquals(before.characters(), after.characters());
            assertEquals(before.combats(), after.combats());
            var root = after.roots().getLast();
            assertEquals(pc, root.subjectId());
            assertEquals(before.positions(), root.space().beforePositions());
            assertEquals(after.positions(), root.space().afterPositions());
            assertTrue(root.rolls().isEmpty());

            command(service, "focus-set", target(CampaignSpaceData.Ref.world(node)));
            reject(service, "scene-place", direct(pc, placed.version(), 2, 2), "SCENE_FOCUS_REQUIRED");
            UUID otherScene = createAdjudicatedScene(service, node, "院落");
            command(service, "focus-set", target(CampaignSpaceData.Ref.scene(otherScene)));
            reject(service, "scene-place", direct(pc, placed.version(), 2, 2), "SPACE_FROZEN");
            assertEquals(placed, placement(service, pc));
            assertEquals(0, draws.get());
        }
    }

    @Test void ruleBranchPositionAndOtherConsequencesSurviveRollbackReceiptRecoveryAndFiles() throws Exception {
        var savedRoot = temporary.resolve("rule-branch");
        var fail = new AtomicReference<Boundary>();
        var draws = new AtomicInteger();
        String request = UUID.randomUUID().toString();
        long revision;
        String runId;
        JsonObject payload;
        CampaignDomainData.Position oldPosition;
        try (var service = new CampaignStorageService(savedRoot, boundary -> {
            if (fail.compareAndSet(boundary, null)) throw new IllegalStateException("placement fault");
        }, sides -> { draws.incrementAndGet(); return sides; })) {
            CampaignRuleFixtures.create(service, "规则后果");
            UUID actor = ruleCharacter(service, "甲");
            UUID recipient = ruleCharacter(service, "乙");
            UUID node = world(service, "城门");
            enterAdjudicatedScene(service, node, "室内", actor, recipient);
            command(service, "item", object("characterId", recipient, "name", "绳索", "quantity", 3, "itemId", JsonNull.INSTANCE));
            UUID item = state(service).items().getFirst().id();
            oldPosition = placement(service, recipient);
            var before = service.accessSnapshot();
            revision = before.revision();
            runId = before.runId();
            var input = object("characterId", actor, "characterVersion", 0);
            var check = new CheckInput(CampaignRuleFixtures.rules().rule("adventure.check", "check.srd2014"),
                    "ABILITY", "ability.strength", null, 0, false, false, "机关检定");
            input.add("command", CampaignDomainCodec.toJson(new Command("CHECK_RESOLVED", null, 0, null,
                    null, null, GrowthChoices.EMPTY, null, check)));
            var success = object("targetId", recipient, "itemId", item, "itemDelta", 2, "message", "绳索固定");
            success.add("healthEffect", CampaignDomainCodec.toJson(
                    CampaignRuleFixtures.rules().rule("adventure.vital_effect", "vital_effect.dm_damage")));
            success.add("position", position(oldPosition.version(), 4, 4));
            input.add("success", success);
            var failure = object("targetId", recipient, "itemId", JsonNull.INSTANCE, "itemDelta", 0,
                    "message", "", "healthEffect", JsonNull.INSTANCE);
            input.add("failure", failure);
            failure.add("position", position(oldPosition.version(), 0, 0));
            reject(service, "check-effects", input, "SCENE_CELL_OCCUPIED");
            failure.add("position", position(oldPosition.version() - 1, 3, 3));
            reject(service, "check-effects", input, "STALE_POSITION");
            assertEquals(0, draws.get());

            failure.add("position", position(oldPosition.version(), 3, 3));
            payload = focused(before.snapshot().domains(), input);
            fail.set(Boundary.AFTER_CHARACTER_UPDATE);
            assertThrows(RuntimeException.class, () -> service.domainCommand(request, before.epoch(), revision, "check-effects", payload));
            assertEquals(1, draws.get());
            assertEquals(before, service.accessSnapshot());

            fail.set(Boundary.AFTER_BUSINESS_COMMIT);
            assertEquals("COMMIT_UNKNOWN", assertThrows(StorageException.class,
                    () -> service.domainCommand(request, before.epoch(), revision, "check-effects", payload)).code());
            assertEquals(2, draws.get());
        }

        try (var service = new CampaignStorageService(savedRoot, boundary -> {},
                sides -> { throw new AssertionError("Recovery or replay drew a live die"); })) {
            var recovered = service.recover(runId);
            assertEquals(revision + 1, recovered.revision());
            var replay = service.domainCommand(request, recovered.epoch(), revision, "check-effects", payload);
            assertTrue(replay.lastCommand().replayed());
            assertEquals(recovered.domain(), replay.domain());
            var root = state(service).roots().getLast();
            var actual = placement(service, oldPosition.characterId());
            assertEquals(new Schema2CombatState.Position(4, 4), actual.grid());
            assertEquals(oldPosition.version() + 1, actual.version());
            assertTrue(root.space().beforePositions().contains(oldPosition));
            assertTrue(root.space().afterPositions().contains(actual));
            assertEquals(2, root.characterOperations().size());
            assertEquals(5, state(service).items().getFirst().quantity());
            assertEquals(List.of(new CampaignSpaceData.Die(20, 20)), root.rolls());
            assertEquals(root.space().before(), root.space().after());
            String checkpoint = service.save(UUID.randomUUID().toString(), recovered.epoch(), recovered.revision()).fileKey();
            String finalFile = service.end(UUID.randomUUID().toString(), recovered.epoch(), recovered.revision()).fileKey();
            assertNotEquals(checkpoint, finalFile);
            var opened = service.open(finalFile);
            assertEquals(recovered.domain(), opened.domain());
            assertEquals(recovered.history(), opened.history());
        }
    }

    @Test void manualPlacementEffectsRespectBranchOrderAndCanMoveARecipientOnFailure() throws Exception {
        AtomicInteger draws = new AtomicInteger();
        try (var service = new CampaignStorageService(temporary.resolve("manual-branch"), boundary -> {},
                sides -> { draws.incrementAndGet(); return 10; })) {
            CampaignRuleFixtures.create(service, "自由卡后果");
            UUID first = manualNpc(service, "甲");
            UUID second = manualNpc(service, "乙");
            UUID node = world(service, "城门");
            enterAdjudicatedScene(service, node, "室内", first, second);
            var firstBefore = placement(service, first);
            var secondBefore = placement(service, second);
            var input = object("characterId", first, "characterVersion", 0, "checkKind", "MANUAL", "key",
                    JsonNull.INSTANCE, "manualName", "机关", "manualModifier", -99, "rollMode", "NORMAL", "dc", 0);
            var targets = new JsonArray();
            targets.add(object("characterId", first, "version", 0));
            targets.add(object("characterId", second, "version", 0));
            input.add("targets", targets);
            var success = new JsonArray();
            success.add(manualPlacement(first, firstBefore.version(), 3, 3));
            input.add("success", success);
            var failure = new JsonArray();
            failure.add(manualPlacement(second, secondBefore.version(), 0, 0));
            failure.add(manualPlacement(first, firstBefore.version(), 5, 5));
            input.add("failure", failure);
            var selectedSuccess = input.deepCopy();
            selectedSuccess.addProperty("manualModifier", "99");
            reject(service, "manual-check", selectedSuccess, "SCENE_CELL_OCCUPIED");
            assertEquals(0, draws.get());

            failure.set(0, manualPlacement(first, firstBefore.version(), 5, 5));
            failure.set(1, manualPlacement(second, secondBefore.version(), 0, 0));
            failure.add(manualPlacement(first, firstBefore.version() + 1, 4, 4));
            failure.add(object("kind", "HP", "targetId", second, "amount", -1));
            failure.add(object("kind", "MESSAGE", "text", "机关触发"));
            command(service, "manual-check", input);
            assertEquals(1, draws.get());
            var firstAfter = placement(service, first);
            var secondAfter = placement(service, second);
            assertEquals(new Schema2CombatState.Position(4, 4), firstAfter.grid());
            assertEquals(new Schema2CombatState.Position(0, 0), secondAfter.grid());
            assertEquals(firstBefore.version() + 2, firstAfter.version());
            assertEquals(secondBefore.version() + 1, secondAfter.version());
            var root = state(service).roots().getLast();
            var audit = CampaignDomainCodec.fromJson(CampaignJson.read(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    CampaignManualCommands.Audit.class);
            assertFalse(audit.result().success());
            assertEquals(List.of(10), audit.result().dice());
            assertEquals(second, audit.cards().getFirst().after().id());
            assertEquals(10, audit.cards().getFirst().after().current().fields().get("hp.current"));
            assertEquals(1, audit.cards().size());
            var accepted = service.current();
            String finalFile = service.end(UUID.randomUUID().toString(), accepted.epoch(), accepted.revision()).fileKey();
            assertEquals(accepted.domain(), service.open(finalFile).domain());
        }
    }

    private static UUID manualNpc(CampaignStorageService service, String name) {
        command(service, "manual-create", object("name", name, "kind", "NPC", "template", "guard"));
        return state(service).manual().characters().stream().filter(c -> c.current().name().equals(name)).findFirst().orElseThrow().id();
    }

    private static UUID ruleCharacter(CampaignStorageService service, String name) {
        command(service, "character-create", object("name", name, "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        return state(service).roots().getLast().subjectId();
    }

    private static UUID createAdjudicatedScene(CampaignStorageService service, UUID node, String name) {
        command(service, "scene-create", object("name", name, "nodeId", node, "width", 6, "height", 6, "resolution", "DM"));
        return state(service).roots().getLast().subjectId();
    }

    private static UUID enterAdjudicatedScene(CampaignStorageService service, UUID node, String name, UUID... entrants) {
        var subjects = new JsonArray();
        for (int i = 0; i < entrants.length; i++) {
            place(service, entrants[i], node);
            subjects.add(object("id", entrants[i], "version", placement(service, entrants[i]).version(), "x", i, "y", 0, "side", i));
        }
        UUID scene = createAdjudicatedScene(service, node, name);
        var enter = object("sceneId", scene);
        enter.add("subjects", subjects);
        command(service, "scene-enter", enter);
        return scene;
    }

    private static CampaignDomainData.State state(CampaignStorageService service) {
        return service.accessSnapshot().snapshot().domains();
    }

    private static CampaignDomainData.Position placement(CampaignStorageService service, UUID id) {
        return CampaignSpaceCommands.position(state(service), id);
    }

    private static JsonObject position(long version, int x, int y) {
        return object("placementVersion", version, "x", x, "y", y);
    }

    private static JsonObject direct(UUID id, long version, int x, int y) {
        var request = position(version, x, y);
        request.addProperty("characterId", id.toString());
        return request;
    }

    private static JsonObject manualPlacement(UUID id, long version, int x, int y) {
        var request = position(version, x, y);
        request.addProperty("kind", "POSITION");
        request.addProperty("targetId", id.toString());
        return request;
    }

    private static void reject(CampaignStorageService service, String action, JsonObject request, String code) {
        var before = service.accessSnapshot();
        assertEquals(code, assertThrows(StorageException.class, () -> command(service, action, request)).code());
        assertEquals(before, service.accessSnapshot());
    }

    private static JsonObject object(Object... fields) {
        var result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) {
            Object value = fields[i + 1];
            result.add((String) fields[i], value == JsonNull.INSTANCE ? JsonNull.INSTANCE : new JsonPrimitive(value.toString()));
        }
        return result;
    }
}
