package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CombatHistory;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
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
            combat(service, sceneA, Command.reenter(a1, List.of(a1, a2)));
            Position aDestination = openOpportunity(service, sceneA, a1);
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
