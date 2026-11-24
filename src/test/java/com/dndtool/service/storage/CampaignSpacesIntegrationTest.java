package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.dndtool.service.Schema2CombatHistory;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.CampaignSpaceData.Ref;
import static org.junit.jupiter.api.Assertions.*;

class CampaignSpacesIntegrationTest {
    @TempDir Path temporary;

    @Test void twoPendingRuleScenesFreezeAndResumeAcrossOneCompleteFile() throws Exception {
        AtomicInteger dice = new AtomicInteger();
        try (var service = new CampaignStorageService(temporary.resolve("rule-spaces"), ignored -> {}, sides -> {
            dice.incrementAndGet();
            return Math.min(sides, 10);
        })) {
            CampaignRuleFixtures.create(service, "并行场景");
            UUID west = node(service, "西城门");
            UUID east = node(service, "东城门");
            focus(service, Ref.world(west));
            send(service, "node-link", object("from", west, "to", east));
            UUID a1 = fighter(service, "甲");
            UUID a2 = fighter(service, "乙");
            UUID b1 = fighter(service, "丙");
            UUID b2 = fighter(service, "丁");
            for (UUID character : List.of(a1, a2, b1, b2)) {
                place(service, character, west);
                equipSword(service, character);
            }
            UUID sceneA = scene(service, west, "西侧庭院", "RULES");
            enter(service, sceneA, a1, a2);
            long placementVersion = CampaignSpaceCommands.position(state(service), a1).version();
            var direct = object("characterId", a1, "placementVersion", placementVersion, "x", 7, "y", 7);
            var ruleExploration = service.accessSnapshot();
            assertEquals("RULE_SCENE_OWNER_REQUIRED", assertThrows(StorageException.class,
                    () -> send(service, "scene-place", direct)).code());
            assertEquals(ruleExploration, service.accessSnapshot());
            combat(service, sceneA, Command.reenter(a1, List.of(a1, a2)));
            Position aDestination = openOpportunity(service, sceneA, a1);
            var rulePending = service.accessSnapshot();
            int beforeDirect = dice.get();
            assertEquals("ACTION_FLOW_BUSY", assertThrows(StorageException.class,
                    () -> send(service, "scene-place", direct)).code());
            assertEquals(rulePending, service.accessSnapshot());
            assertEquals(beforeDirect, dice.get());
            var frozenA = sceneHistory(service, sceneA);
            var frozenAClock = state(service).space().clock(Ref.scene(sceneA));
            var aBefore = character(service, a1).history();
            var stale = combatInput(service, sceneA, pass(frozenA.current()));
            stale.add("focus", CampaignSpaceCommands.focusPayload(state(service)));

            focus(service, Ref.world(west));
            travel(service, east, 3, b1, b2);
            UUID sceneB = scene(service, east, "东侧庭院", "RULES");
            enter(service, sceneB, b1, b2);
            combat(service, sceneB, Command.advanceTime(b1, 6));
            combat(service, sceneB, Command.reenter(b1, List.of(b1, b2)));
            openOpportunity(service, sceneB, b1);
            var frozenB = sceneHistory(service, sceneB);
            assertNotNull(frozenB.current().activeFlow());
            assertEquals(frozenA, sceneHistory(service, sceneA));
            assertEquals(frozenAClock, state(service).space().clock(Ref.scene(sceneA)));
            assertEquals(aBefore, character(service, a1).history());
            assertEquals(0, experiencedSeconds(service, a1));
            assertEquals(186, experiencedSeconds(service, b1));
            assertEquals(180, state(service).space().clock(Ref.world(west)).second());
            assertEquals(0, state(service).space().clock(Ref.world(east)).second());
            assertEquals(6, state(service).space().clock(Ref.scene(sceneB)).second());

            var before = service.current();
            int draws = dice.get();
            assertEquals("STALE_FOCUS", assertThrows(StorageException.class,
                    () -> send(service, "combat-command", stale)).code());
            assertEquals(draws, dice.get());
            assertEquals(before.revision(), service.current().revision());
            assertEquals(before.domain(), service.current().domain());

            String file = service.end(uuid(), before.epoch(), before.revision()).fileKey();
            var opened = service.open(file);
            assertEquals(before.domain(), opened.domain());
            assertEquals(before.history(), opened.history());
            assertEquals(draws, dice.get());
            assertEquals(Ref.scene(sceneB), state(service).space().focus().space());
            assertEquals(frozenA, sceneHistory(service, sceneA));
            assertEquals(frozenB, sceneHistory(service, sceneB));

            focus(service, Ref.scene(sceneA));
            Battle pending = sceneHistory(service, sceneA).current();
            combat(service, sceneA, pass(pending));
            Flow closed = sceneHistory(service, sceneA).current().activeFlow();
            combat(service, sceneA, Command.continuation(a1, closed.flowKey(), closed.leafFrameKey(),
                    closed.windows().getLast().windowKey()));
            Battle resumed = sceneHistory(service, sceneA).current();
            assertNull(resumed.activeFlow());
            assertEquals(aDestination, CampaignSpaceCommands.position(state(service), a1).grid());
            assertEquals(frozenB, sceneHistory(service, sceneB));
            assertEquals(6, state(service).space().clock(Ref.scene(sceneB)).second());

            combat(service, sceneA, Command.action(Kind.END_COMBAT, resumed.activeActor()));
            combat(service, sceneA, Command.exploreMove(a1, List.of(new Position(1, 1))));
            assertEquals(Status.EXPLORATION, sceneHistory(service, sceneA).current().status());
            assertEquals(new Position(1, 1), CampaignSpaceCommands.position(state(service), a1).grid());
            var leaving = new JsonObject();
            leaving.add("subjects", subjects(service, a1, a2));
            send(service, "scene-leave", leaving);
            assertEquals(Ref.world(west), state(service).space().focus().space());
            assertEquals(Ref.world(west), CampaignSpaceCommands.position(state(service), a1).space());
            assertTrue(sceneHistory(service, sceneA).current().combatants().isEmpty());
            assertEquals(frozenB, sceneHistory(service, sceneB));
        }
    }

    @Test void dmTravelAdvancesSourceAndMixedCardsUseTheSameGrid() throws Exception {
        try (var service = new CampaignStorageService(temporary.resolve("dm-spaces"), ignored -> {},
                sides -> { throw new AssertionError("DM adjudication drew a d" + sides); })) {
            CampaignRuleFixtures.create(service, "旅途和裁定");
            UUID west = node(service, "村口");
            UUID east = node(service, "桥头");
            focus(service, Ref.world(west));
            send(service, "node-link", object("from", west, "to", east));
            UUID traveler = fighter(service, "行者");
            UUID witness = fighter(service, "留守者");
            place(service, traveler, west);
            place(service, witness, west);
            travel(service, east, 13, traveler);
            assertEquals(Ref.world(east), CampaignSpaceCommands.position(state(service), traveler).space());
            assertEquals(Ref.world(west), CampaignSpaceCommands.position(state(service), witness).space());
            assertEquals(780, state(service).space().clock(Ref.world(west)).second());
            assertEquals(0, state(service).space().clock(Ref.world(east)).second());
            assertEquals(780, experiencedSeconds(service, traveler));
            assertEquals(780, experiencedSeconds(service, witness));

            UUID manual = send(service, "manual-create", object("name", "向导", "kind", "NPC", "template", "blank"));
            place(service, manual, east);
            UUID scene = scene(service, east, "桥上遭遇", "DM");
            enter(service, scene, traveler, manual);
            send(service, "scene-mode", object("mode", "COMBAT"));
            assertEquals("COMBAT", state(service).space().scene(scene).mode());
            assertTrue(state(service).combats().isEmpty());
            var blocked = object("characterId", manual, "version", CampaignSpaceCommands.position(state(service), manual).version(),
                    "minutes", 0, "adjudication", "向导试图穿过行者所在格位");
            blocked.add("path", path(1, 1));
            assertEquals("SCENE_PATH_INVALID", assertThrows(StorageException.class, () -> send(service, "space-move", blocked)).code());
            assertEquals(new Position(2, 1), CampaignSpaceCommands.position(state(service), manual).grid());
            var movement = object("characterId", manual, "version", CampaignSpaceCommands.position(state(service), manual).version(),
                    "minutes", 1, "adjudication", "向导走到桥栏观察");
            movement.add("path", path(2, 2));
            send(service, "space-move", movement);
            assertEquals(new Position(1, 1), CampaignSpaceCommands.position(state(service), traveler).grid());
            assertEquals(new Position(2, 2), CampaignSpaceCommands.position(state(service), manual).grid());
            assertEquals(60, state(service).space().clock(Ref.scene(scene)).second());
            assertEquals(840, experiencedSeconds(service, traveler));
            assertEquals(780, experiencedSeconds(service, witness));
            send(service, "scene-mode", object("mode", "EXPLORATION"));
            assertEquals("EXPLORATION", state(service).space().scene(scene).mode());
        }
    }

    @Test void campaignOperationOwnershipIsEnforcedBeforeDiceAndOnPortableReopen() throws Exception {
        AtomicInteger dice = new AtomicInteger();
        AtomicInteger commandWrites = new AtomicInteger();
        try (var service = new CampaignStorageService(temporary.resolve("operation-owners"), boundary -> {
            if (boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE) commandWrites.incrementAndGet();
        }, sides -> {
            dice.incrementAndGet();
            return 1;
        })) {
            service.create("跨场景操作身份");
            UUID world = node(service, "营地");
            focus(service, Ref.world(world));
            UUID a1 = fighter(service, "甲");
            var creation = state(service).roots().getLast();
            assertEquals(List.of(creation.operationId()), creation.characterOperations());
            assertEquals(creation.operationId(), character(service, a1).history().events().getFirst().operationId());
            UUID a2 = fighter(service, "乙");
            UUID b1 = fighter(service, "丙");
            UUID b2 = fighter(service, "丁");
            for (UUID character : List.of(a1, a2, b1, b2)) place(service, character, world);

            UUID sceneA = scene(service, world, "北侧庭院", "RULES");
            enter(service, sceneA, a1, a2);
            combat(service, sceneA, Command.advanceTime(a1, 6));
            var advance = state(service).roots().getLast();
            var sceneEvent = sceneHistory(service, sceneA).events().getLast();
            assertEquals(advance.operationId(), sceneEvent.operationId());
            assertEquals(2, advance.characterOperations().size());
            assertEquals(2, sceneEvent.characterOperations().size());
            assertEquals(Set.copyOf(advance.characterOperations()), Set.copyOf(sceneEvent.characterOperations()));
            UUID retainedChild = character(service, a1).history().events().getLast().operationId();
            assertNotEquals(advance.operationId(), retainedChild);
            assertTrue(advance.characterOperations().contains(retainedChild));

            UUID healingRoot = UUID.randomUUID();
            UUID occupied = combatChild(healingRoot, b1, 0);
            send(service, occupied.toString(), "note", object("text", "北侧庭院记录"));
            focus(service, Ref.world(world));
            UUID sceneB = scene(service, world, "南侧庭院", "RULES");
            enter(service, sceneB, b1, b2);

            var rules = CampaignRuleFixtures.rules();
            var damage = new HealthInput(rules.rule("adventure.health", "health.srd2014"),
                    rules.rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "DM裁定落石伤害");
            combat(service, sceneB, Command.worldHealth(b1, b1, damage));
            JsonObject reenter = combatInput(service, sceneB, Command.reenter(b1, List.of(b1, b2)));
            assertRejectedWithoutCommandWrites(service, dice, commandWrites, "IDEMPOTENCY_CONFLICT",
                    () -> CampaignSpaceFixtures.command(service, retainedChild.toString(), "combat-command", reenter));
            int beforeInitiative = dice.get();
            CampaignSpaceFixtures.command(service, "combat-command", reenter);
            assertEquals(beforeInitiative + 2, dice.get());
            assertEquals(b1, sceneHistory(service, sceneB).current().activeActor());

            JsonObject healing = combatInput(service, sceneB, Command.action(Kind.SECOND_WIND, b1));
            assertRejectedWithoutCommandWrites(service, dice, commandWrites, "DOMAIN_REJECTED",
                    () -> CampaignSpaceFixtures.command(service, healingRoot.toString(), "combat-command", healing));

            var beforeRetry = service.accessSnapshot();
            JsonObject retry = CampaignSpaceFixtures.focused(beforeRetry.snapshot().domains(), healing);
            String retryId = uuid();
            int beforeDraws = dice.get();
            int beforeWrites = commandWrites.get();
            long hitPoints = character(service, b1).history().current().hitPoints();
            var accepted = service.domainCommand(retryId, beforeRetry.epoch(), beforeRetry.revision(), "combat-command", retry);
            assertFalse(accepted.lastCommand().replayed());
            assertEquals(beforeRetry.revision() + 1, accepted.revision());
            assertEquals(hitPoints + 2, character(service, b1).history().current().hitPoints());
            assertEquals(beforeDraws + 1, dice.get());
            assertEquals(beforeWrites + 1, commandWrites.get());
            var retryRoot = state(service).roots().getLast();
            assertEquals(2, retryRoot.characterOperations().size());
            assertEquals(Set.of(combatChild(retryRoot.operationId(), b1, 0), combatChild(retryRoot.operationId(), b1, 1)),
                    Set.copyOf(retryRoot.characterOperations()));
            assertTrue(service.domainCommand(retryId, beforeRetry.epoch(), beforeRetry.revision(), "combat-command", retry)
                    .lastCommand().replayed());
            assertEquals(beforeDraws + 1, dice.get());
            assertEquals(beforeWrites + 1, commandWrites.get());
            assertEquals(accepted.domain(), service.current().domain());

            send(service, healingRoot.toString(), "note", object("text", "南侧庭院记录"));
            var original = service.current();
            String key = service.end(uuid(), original.epoch(), original.revision()).fileKey();
            byte[] complete = Files.readAllBytes(service.savedFile(key));
            assertEquals(3, CampaignJson.read(complete).get("formatVersion").getAsInt());
            assertPortableOperationOwnership(complete, key, original, advance, sceneA, retainedChild);
        }
    }

    private void assertPortableOperationOwnership(byte[] complete, String key, CampaignStorageService.CampaignView original,
            CampaignDomainData.Root advance, UUID scene, UUID retainedChild) throws Exception {
        AtomicInteger importWrites = new AtomicInteger();
        try (var target = new CampaignStorageService(temporary.resolve("operation-import"), boundary -> {
            if (boundary == CampaignStorageService.Boundary.AFTER_IMPORT_TEMP) importWrites.incrementAndGet();
        }, sides -> { throw new AssertionError("Portable verification drew a d" + sides); })) {
            var empty = target.status();
            assertTrue(empty.saveFiles().isEmpty());

            JsonObject conflict = CampaignJson.read(complete);
            fileRoots(conflict).asList().getLast().getAsJsonObject().addProperty("operationId", retainedChild.toString());
            assertEquals("INVALID_FILE", assertThrows(StorageException.class, () -> target.importFile(resign(conflict))).code());

            JsonObject orphan = CampaignJson.read(complete);
            var rootChildren = fileRoot(orphan, advance).getAsJsonArray("characterOperations");
            var sceneEvents = orphan.getAsJsonObject("domains").getAsJsonArray("combats").asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .filter(history -> history.getAsJsonObject("current").get("combatId").getAsString().equals(scene.toString()))
                    .findFirst().orElseThrow().getAsJsonArray("events");
            var sceneChildren = sceneEvents.asList().getLast().getAsJsonObject().getAsJsonArray("characterOperations");
            assertTrue(rootChildren.asList().removeIf(id -> id.getAsString().equals(retainedChild.toString())));
            assertTrue(sceneChildren.asList().removeIf(id -> id.getAsString().equals(retainedChild.toString())));
            assertEquals("INVALID_FILE", assertThrows(StorageException.class, () -> target.importFile(resign(orphan))).code());

            JsonObject forgedTime = CampaignJson.read(complete);
            var clocks = fileRoot(forgedTime, advance).getAsJsonObject("space").getAsJsonObject("after")
                    .getAsJsonArray("clocks");
            var clock = clocks.asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(value -> value.getAsJsonObject("space").get("id").getAsString().equals(scene.toString()))
                    .findFirst().orElseThrow();
            assertEquals("6", clock.get("second").getAsString());
            clock.addProperty("second", "7");
            assertEquals("INVALID_FILE", assertThrows(StorageException.class, () -> target.importFile(resign(forgedTime))).code());

            assertEquals(0, importWrites.get());
            assertEquals(empty, target.status());
            assertEquals(key, target.importFile(complete).fileKey());
            var opened = target.open(key);
            assertEquals(original.domain(), opened.domain());
            assertEquals(original.history(), opened.history());
        }
    }

    private static void assertRejectedWithoutCommandWrites(CampaignStorageService service, AtomicInteger dice,
            AtomicInteger writes, String code, Executable command) {
        var before = service.accessSnapshot();
        int draws = dice.get();
        int updates = writes.get();
        assertEquals(code, assertThrows(StorageException.class, command).code());
        assertEquals(draws, dice.get());
        assertEquals(updates, writes.get());
        assertEquals(before, service.accessSnapshot());
    }

    private static UUID combatChild(UUID operation, UUID character, int ordinal) {
        byte[] identity = ("schema2-combat-character/1/" + operation + "/" + character + "/" + ordinal)
                .getBytes(StandardCharsets.US_ASCII);
        UUID derived = UUID.nameUUIDFromBytes(identity);
        return new UUID((derived.getMostSignificantBits() & ~0xf000L) | 0x4000L, derived.getLeastSignificantBits());
    }

    private static JsonArray fileRoots(JsonObject file) {
        return file.getAsJsonObject("domains").getAsJsonArray("roots");
    }

    private static JsonObject fileRoot(JsonObject file, CampaignDomainData.Root root) {
        return fileRoots(file).get(Math.toIntExact(root.sequence() - 1)).getAsJsonObject();
    }

    private static byte[] resign(JsonObject file) {
        var integrity = file.remove("integrity").getAsJsonObject();
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        return CampaignJson.canonical(file);
    }

    private static UUID node(CampaignStorageService service, String name) {
        return send(service, "node-create", object("name", name));
    }

    private static UUID fighter(CampaignStorageService service, String name) {
        return send(service, "character-create", object("name", name, "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
    }

    private static void place(CampaignStorageService service, UUID subject, UUID node) {
        send(service, "position", object("characterId", subject, "nodeId", node));
    }

    private static void focus(CampaignStorageService service, Ref target) {
        var input = new JsonObject();
        input.add("target", CampaignSpaceCommands.refJson(target));
        send(service, "focus-set", input);
    }

    private static UUID scene(CampaignStorageService service, UUID node, String name, String resolution) {
        return send(service, "scene-create", object("nodeId", node, "name", name, "width", 8, "height", 8, "resolution", resolution));
    }

    private static void enter(CampaignStorageService service, UUID scene, UUID first, UUID second) {
        var selection = subjects(service, first, second);
        for (int i = 0; i < selection.size(); i++) {
            var row = selection.get(i).getAsJsonObject();
            row.addProperty("x", Integer.toString(i + 1));
            row.addProperty("y", "1");
            row.addProperty("side", Integer.toString(i));
        }
        var input = object("sceneId", scene);
        input.add("subjects", selection);
        send(service, "scene-enter", input);
    }

    private static void travel(CampaignStorageService service, UUID target, int minutes, UUID... moving) {
        var input = object("targetNodeId", target, "minutes", minutes, "adjudication", "DM按本次路况裁定经过时间");
        input.add("subjects", subjects(service, moving));
        send(service, "travel", input);
    }

    private static JsonArray subjects(CampaignStorageService service, UUID... subjects) {
        var selection = new JsonArray();
        Arrays.stream(subjects).forEach(id -> selection.add(object("id", id,
                "version", CampaignSpaceCommands.position(state(service), id).version())));
        return selection;
    }

    private static JsonArray path(int x, int y) {
        var result = new JsonArray();
        result.add(object("x", x, "y", y));
        return result;
    }

    private static Position openOpportunity(CampaignStorageService service, UUID scene, UUID actor) {
        assertEquals(actor, sceneHistory(service, scene).current().activeActor());
        Position away = new Position(0, 1);
        combat(service, scene, Command.move(actor, List.of(away)));
        assertEquals(WindowStatus.OPEN, sceneHistory(service, scene).current().activeFlow().windows().getLast().status());
        assertEquals(new Position(1, 1), CampaignSpaceCommands.position(state(service), actor).grid());
        return away;
    }

    private static Command pass(Battle battle) {
        Flow pending = battle.activeFlow();
        Window window = pending.windows().getLast();
        Opportunity opportunity = window.opportunities().stream()
                .filter(o -> o.status() == OpportunityStatus.PENDING).findFirst().orElseThrow();
        return Command.reaction(Kind.PASS_REACTION, opportunity.actorId(), null, pending.flowKey(),
                pending.leafFrameKey(), window.windowKey(), opportunity.opportunityKey());
    }

    private static void combat(CampaignStorageService service, UUID scene, Command command) {
        send(service, "combat-command", combatInput(service, scene, command));
    }

    private static JsonObject combatInput(CampaignStorageService service, UUID scene, Command command) {
        var input = object("combatId", scene, "combatVersion", sceneHistory(service, scene).current().version());
        input.add("command", CampaignDomainCodec.toJson(command));
        return input;
    }

    private static void equipSword(CampaignStorageService service, UUID character) {
        UUID item = UUID.randomUUID();
        var sword = CampaignRuleFixtures.rules().rule("equipment.item", "item.longsword");
        equipment(service, character, item.toString(), "ITEM_ACQUIRED", new EquipmentInput(item, sword, "CARRIED", "DM确认长剑"));
        equipment(service, character, uuid(), "ITEM_EQUIPPED", new EquipmentInput(item, null, "MAIN_HAND", "DM确认佩戴长剑"));
    }

    private static void equipment(CampaignStorageService service, UUID id, String operation, String kind, EquipmentInput equipment) {
        var input = object("characterId", id, "characterVersion", character(service, id).history().current().version());
        input.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command(kind, null, 0, null, null, null,
                GrowthChoices.EMPTY, null, null, equipment, null)));
        send(service, operation, "character-command", input);
    }

    private static Schema2CombatHistory.History sceneHistory(CampaignStorageService service, UUID id) {
        return state(service).combats().stream().filter(h -> h.current().combatId().equals(id)).findFirst().orElseThrow();
    }

    private static long experiencedSeconds(CampaignStorageService service, UUID id) {
        var rest = character(service, id).history().current().rest();
        return rest.minute() * 60 + rest.second();
    }

    private static CampaignDomainData.Character character(CampaignStorageService service, UUID id) {
        return state(service).characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static CampaignDomainData.State state(CampaignStorageService service) {
        return service.accessSnapshot().snapshot().domains();
    }

    private static UUID send(CampaignStorageService service, String action, JsonObject input) {
        return send(service, uuid(), action, input);
    }

    private static UUID send(CampaignStorageService service, String operation, String action, JsonObject input) {
        if (!input.has("focus")) input.add("focus", CampaignSpaceCommands.focusPayload(state(service)));
        var before = service.current();
        service.domainCommand(operation, before.epoch(), before.revision(), action, input);
        return state(service).roots().getLast().subjectId();
    }

    private static JsonObject object(Object... pairs) {
        var result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) result.addProperty((String) pairs[i], pairs[i + 1].toString());
        return result;
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
}
