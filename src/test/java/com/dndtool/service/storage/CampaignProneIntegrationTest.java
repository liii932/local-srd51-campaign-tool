package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignProneIntegrationTest {
    @TempDir Path temporary;

    @Test void postureCostsOnlyMovementAndSurvivesTurnsAndExploration() throws Exception {
        try (var f = new Fixture(temporary)) {
            f.create();
            UUID fighter = f.character("战士", "PC"), guard = f.character("守卫", "NPC");
            UUID dagger = f.equip(fighter, "item.dagger"), guardDagger = f.equip(guard, "item.dagger");
            UUID hound = f.monster();
            f.start(List.of(new Deployment(fighter, cell(1, 1), 0), new Deployment(guard, cell(3, 1), 1),
                    new Deployment(hound, cell(8, 8), 0)), 20, 10, 1);

            Command drop = Command.action(Kind.DROP_PRONE, fighter);
            f.reject(Command.action(Kind.DROP_PRONE, guard), "S2_COMBAT_NOT_CURRENT_TURN");
            var before = f.access();
            var input = f.combatInput(drop);
            var forged = input.deepCopy();
            forged.getAsJsonObject("command").addProperty("prone", true);
            assertCode("INVALID_FILE", () -> f.submit(id(), before, forged));
            var wrongShape = input.deepCopy();
            wrongShape.getAsJsonObject("command").addProperty("targetId", guard.toString());
            assertCode("DOMAIN_REJECTED", () -> f.submit(id(), before, wrongShape));
            var staleCombat = input.deepCopy();
            staleCombat.addProperty("combatVersion", Long.toString(f.battle().version() - 1));
            assertCode("STALE_COMBAT", () -> f.submit(id(), before, staleCombat));
            assertCode("STALE_REVISION", () -> f.service.domainCommand(id(), before.epoch(), before.revision() - 1,
                    "combat-command", input));
            assertEquals(before, f.access());

            String request = id();
            f.failAfterDomainWrite = true;
            var fault = assertThrows(IllegalStateException.class, () -> f.submit(request, before, input));
            assertEquals("posture transaction failure", fault.getMessage());
            assertEquals(before, f.access());

            var card = f.card(fighter);
            var accepted = f.submit(request, before, input);
            var after = f.access();
            var replay = f.submit(request, before, input);
            assertFalse(accepted.lastCommand().replayed());
            assertTrue(replay.lastCommand().replayed());
            assertEquals(accepted.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
            assertEquals(after, f.access());
            assertEquals(1L, f.state().roots().stream().filter(root -> root.operationId().toString().equals(request)).count());
            assertEquals(card, f.card(fighter));
            assertEquals(new Combatant(fighter, cell(1, 1), 0, true, true, 0, 0, false, false, null, true),
                    f.actor(fighter));
            assertTrue(f.state().roots().getLast().rolls().isEmpty());
            assertEquals(List.of(new CombatFact("DROP_PRONE", fighter, f.battle().combatId(), null)),
                    f.state().combats().getFirst().events().getLast().resolution().facts());
            assertCode("IDEMPOTENCY_CONFLICT", () -> f.submit(request, after,
                    f.combatInput(Command.action(Kind.STAND_UP, fighter))));
            f.reject(drop, "S2_COMBAT_ALREADY_PRONE");

            f.combat(Command.move(fighter, List.of(cell(2, 1))));
            assertMovement(f.movement(), 1, 5, MovementStatus.COMPLETED);
            assertEquals(20, f.actor(fighter).movementSpentHalfFeet());
            assertTrue(f.actor(fighter).actionAvailable());
            f.reject(Command.move(fighter, List.of(cell(2, 2), cell(2, 3), cell(2, 4))),
                    "S2_COMBAT_MOVEMENT_ALLOWANCE");
            f.combat(Command.action(Kind.STAND_UP, fighter));
            assertFalse(f.actor(fighter).prone());
            assertEquals(50, f.actor(fighter).movementSpentHalfFeet());
            f.combat(Command.move(fighter, List.of(cell(2, 2))));
            assertEquals(60, f.actor(fighter).movementSpentHalfFeet());
            f.reject(Command.move(fighter, List.of(cell(2, 3))), "S2_COMBAT_MOVEMENT_ALLOWANCE");

            f.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            var sequence = f.actor(fighter).sequence();
            assertNotNull(sequence);
            f.combat(drop);
            assertEquals(sequence, f.actor(fighter).sequence());
            assertEquals(60, f.actor(fighter).movementSpentHalfFeet());
            f.reject(Command.action(Kind.STAND_UP, fighter), "S2_COMBAT_CANNOT_STAND");
            f.combat(Command.attack(fighter, guard, dagger), 17, 1);
            assertTrue(f.attack().disadvantage());
            assertEquals(1, f.attack().selectedD20());
            assertFalse(f.attack().hit());
            assertEquals(sequence.sequenceKey(), f.attack().sequenceKey());

            f.combat(Command.action(Kind.END_TURN, fighter));
            assertEquals(guard, f.battle().activeActor());
            assertTrue(f.actor(fighter).prone());
            f.reject(Command.action(Kind.STAND_UP, fighter), "S2_COMBAT_NOT_CURRENT_TURN");
            f.combat(Command.action(Kind.BEGIN_ATTACK, guard));
            f.combat(Command.attack(guard, fighter, guardDagger), 1, 17, 2);
            assertTrue(f.attack().advantage());
            assertEquals(17, f.attack().selectedD20());
            assertTrue(f.attack().hit());
            f.combat(Command.action(Kind.END_TURN, guard));
            assertEquals(hound, f.battle().activeActor());
            f.reject(Command.action(Kind.DROP_PRONE, hound), "S2_MONSTER_ACTION_UNSUPPORTED");

            f.combat(Command.action(Kind.END_COMBAT, hound));
            assertEquals(Status.EXPLORATION, f.battle().status());
            assertTrue(f.actor(fighter).prone());
            f.reject(drop, "S2_COMBAT_REQUIRED");
            f.reject(Command.exploreMove(fighter, List.of(cell(2, 3))), "S2_EXPLORATION_MOVEMENT_UNSUPPORTED");
            var exploration = f.access();
            var refused = assertCode("DOMAIN_REJECTED", () -> f.leave(fighter));
            assertEquals("S2_SCENE_OBLIGATIONS_PENDING", refused.getMessage());
            assertEquals(exploration, f.access());
            f.combat(Command.reenter(fighter, List.of(fighter, guard, hound)), 20, 10, 1);
            assertTrue(f.actor(fighter).prone());
            assertEquals(0, f.actor(fighter).movementSpentHalfFeet());
            long hp = f.card(fighter).hitPoints();
            f.combat(Command.action(Kind.SECOND_WIND, fighter), 1);
            assertEquals(hp + 2, f.card(fighter).hitPoints());
            assertTrue(f.actor(fighter).prone());
            assertTrue(f.actor(fighter).actionAvailable());

            f.combat(Command.action(Kind.END_COMBAT, fighter));
            var scene = f.state().space().focus().space();
            f.leave(guard, hound);
            PlayerSpaceFixture.focus(f.service, scene);
            f.reopen();
            var beforeStand = f.battle();
            var prone = f.actor(fighter);
            assertEquals(List.of(prone), beforeStand.combatants());
            assertTrue(prone.prone());
            long sceneSecond = f.state().space().clock(scene).second();
            long personalSecond = f.personalSecond(fighter);
            f.combat(Command.action(Kind.STAND_UP, fighter));
            assertEquals(sceneSecond + 6, f.state().space().clock(scene).second());
            assertEquals(personalSecond + 6, f.personalSecond(fighter));
            assertEquals(Status.EXPLORATION, f.battle().status());
            assertEquals(beforeStand.turnNumber(), f.battle().turnNumber());
            assertEquals(beforeStand.initiative(), f.battle().initiative());
            assertEquals(new Combatant(prone.characterId(), prone.position(), prone.side(), prone.actionAvailable(),
                    prone.reactionAvailable(), prone.movementSpentHalfFeet(), prone.movementBonus(), prone.disengaged(),
                    prone.dodging(), prone.sequence(), false), f.actor(fighter));
            f.leave(fighter);
            assertEquals("WORLD", CampaignSpaceCommands.position(f.state(), fighter).space().kind());
        }
    }

    @Test void aCrawlPrefixSurvivesSaveEndAndOpenThenPassContinuesItExactlyOnce() throws Exception {
        try (var f = new Fixture(temporary)) {
            f.create();
            UUID fighter = f.character("战士", "PC"), guard = f.character("守卫", "NPC");
            f.equip(guard, "item.dagger");
            f.startOpponents(fighter, cell(1, 1), guard, cell(2, 2));
            f.combat(Command.action(Kind.DROP_PRONE, fighter));
            f.combat(Command.move(fighter, List.of(cell(2, 1), cell(3, 1), cell(4, 1))));
            Movement prefix = f.movement();
            assertMovement(prefix, 2, 10, MovementStatus.PENDING);
            assertEquals(cell(3, 1), f.actor(fighter).position());
            assertEquals(40, f.actor(fighter).movementSpentHalfFeet());
            assertEquals(TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE, f.window().trigger().kind());
            f.reject(Command.action(Kind.STAND_UP, fighter), "ACTION_FLOW_BUSY");
            f.reject(Command.action(Kind.DROP_PRONE, fighter), "ACTION_FLOW_BUSY");

            var before = f.access();
            String saved = f.service.save(id(), before.epoch(), before.revision()).fileKey();
            assertEquals(before.snapshot(), f.decode(saved));
            f.reopen();
            assertEquals(prefix, f.movement());
            assertEquals(40, f.actor(fighter).movementSpentHalfFeet());
            assertTrue(f.actor(fighter).prone());

            Command pass = f.reaction(Kind.PASS_REACTION, guard, null);
            assertCode("STALE_EPOCH", () -> f.submit(id(), before, f.combatInput(pass)));
            f.reject(Command.reaction(Kind.PASS_REACTION, fighter, null, pass.flowKey(), pass.leafKey(),
                    pass.windowKey(), pass.opportunityKey()), "REACTION_NOT_CURRENT");
            f.combat(pass);
            assertTrue(f.actor(guard).reactionAvailable());
            assertEquals(OpportunityStatus.PASSED, f.window().opportunities().getFirst().status());
            assertEquals(prefix, f.movement());
            var closed = f.access();
            var continuation = f.combatInput(f.continuation(fighter));
            String request = id();
            f.submit(request, closed, continuation);
            var completed = f.access();
            assertTrue(f.submit(request, closed, continuation).lastCommand().replayed());
            assertEquals(completed, f.access());
            assertMovement(f.movement(), 3, 15, MovementStatus.COMPLETED);
            assertEquals(prefix.movementKey(), f.movement().movementKey());
            assertEquals(cell(4, 1), f.actor(fighter).position());
            assertEquals(60, f.actor(fighter).movementSpentHalfFeet());
            assertNull(f.battle().activeFlow());
            assertTrue(f.state().roots().getLast().rolls().isEmpty());
            assertEquals(1L, f.state().roots().stream().filter(root -> root.operationId().toString().equals(request)).count());
            f.reopen();
            assertCode("IDEMPOTENCY_CONFLICT", () -> f.submit(request, f.access(), continuation));
            assertEquals(completed.snapshot(), f.access().snapshot());
        }
    }

    @Test void aRealReactionStopsAnIncapacitatedCrawlerWithoutRechargingThePrefix() throws Exception {
        try (var f = new Fixture(temporary)) {
            f.create();
            UUID fighter = f.character("战士", "PC"), guard = f.character("守卫", "NPC");
            f.equip(fighter, "item.dagger");
            UUID sword = f.equip(guard, "item.longsword");
            f.startOpponents(fighter, cell(1, 1), guard, cell(2, 2));
            f.combat(Command.action(Kind.DROP_PRONE, fighter));
            f.combat(Command.move(fighter, List.of(cell(2, 1), cell(3, 0), cell(4, 0))));
            assertMovement(f.movement(), 1, 5, MovementStatus.PENDING);
            f.reject(f.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, guard, UUID.randomUUID()), "REACTION_OPTION_INVALID");
            f.combat(f.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, guard, sword), 4, 20, 8, 8);
            AttackAttempt attack = f.attack();
            assertTrue(attack.opportunityAttack());
            assertTrue(attack.advantage());
            assertTrue(attack.critical());
            assertEquals(List.of(8, 8), attack.damageDice());
            assertEquals(0, f.card(fighter).hitPoints());
            assertEquals("GROUND", f.card(fighter).equipment().getFirst().location());
            assertFalse(f.actor(guard).reactionAvailable());
            assertEquals(20, f.actor(fighter).movementSpentHalfFeet());
            assertMovement(f.movement(), 1, 5, MovementStatus.STOPPED);
            f.reopen();
            assertEquals(attack, f.attack());
            assertFalse(f.actor(guard).reactionAvailable());
            f.combat(f.continuation(fighter));
            assertMovement(f.movement(), 1, 5, MovementStatus.STOPPED);
            assertEquals("ACTOR_INACTIVE_OR_NO_MOVEMENT", f.movement().stopReason());
            assertEquals(cell(2, 1), f.actor(fighter).position());
            assertEquals(20, f.actor(fighter).movementSpentHalfFeet());
            assertNull(f.battle().activeFlow());
            assertEquals(1, f.battle().flows().getLast().attacks().size());
            assertTrue(f.state().roots().getLast().rolls().isEmpty());
            f.reject(Command.action(Kind.DROP_PRONE, fighter), "S2_COMBAT_ACTOR_INACTIVE");
            f.reject(Command.move(fighter, List.of(cell(3, 0))), "S2_COMBAT_ACTOR_INACTIVE");

            var beforeEnd = f.access();
            String file = f.service.end(id(), beforeEnd.epoch(), beforeEnd.revision()).fileKey();
            byte[] bytes = Files.readAllBytes(f.service.savedFile(file));
            assertTamperedFilesRejected(f.service, bytes);
            f.service.open(file);
            assertEquals(beforeEnd.snapshot(), f.access().snapshot());
        }
    }

    @Test void readyCrawlUsesSeparateAllowanceAndChecksCurrentPostureAtRelease() throws Exception {
        try (var f = new Fixture(temporary)) {
            f.create();
            UUID fighter = f.character("战士", "PC"), guard = f.character("守卫", "NPC");
            f.startOpponents(fighter, cell(1, 1), guard, cell(6, 1));
            f.combat(Command.move(fighter, List.of(cell(1, 2), cell(1, 3), cell(1, 4), cell(1, 5))));
            f.combat(Command.action(Kind.DROP_PRONE, fighter));
            var trigger = new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER, guard, cell(5, 1));
            f.combat(readyMove(fighter, trigger, List.of(cell(2, 5), cell(3, 5), cell(4, 5))));
            var ready = f.battle().readyPreparations().getLast();
            f.combat(Command.action(Kind.END_TURN, fighter));
            f.combat(Command.move(guard, List.of(cell(5, 1))));
            assertEquals(ReactionKind.READY_RELEASE, f.window().opportunities().getFirst().options().getFirst().kind());
            var flow = f.battle().activeFlow();
            var window = f.window();
            var release = Command.releaseReady(fighter, ready.readyKey(), flow.flowKey(), flow.leafFrameKey(),
                    window.windowKey(), window.opportunities().getFirst().opportunityKey());
            var beforeRelease = f.access();
            var releaseInput = f.combatInput(release);
            String request = id();
            f.submit(request, beforeRelease, releaseInput);
            assertTrue(f.submit(request, beforeRelease, releaseInput).lastCommand().replayed());
            assertFalse(f.actor(fighter).reactionAvailable());
            assertFalse(f.actor(fighter).actionAvailable());
            assertTrue(f.actor(fighter).prone());
            assertEquals(40, f.actor(fighter).movementSpentHalfFeet());
            assertEquals(cell(4, 5), f.actor(fighter).position());
            var readied = f.battle().activeFlow().movements().getLast();
            assertTrue(readied.readied());
            assertEquals(30, readied.allowanceFeet());
            assertMovement(readied, 3, 15, MovementStatus.COMPLETED);
            assertEquals("RELEASED", f.battle().activeFlow().readyReleases().getFirst().outcome());
            f.combat(f.continuation(guard));
            f.combat(Command.action(Kind.END_TURN, guard));

            var nextTrigger = new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER, guard, cell(6, 1));
            var nextPath = List.of(cell(5, 5), cell(6, 5), cell(7, 5), cell(8, 5));
            var nextReady = readyMove(fighter, nextTrigger, nextPath);
            f.reject(nextReady, "S2_COMBAT_MOVEMENT_ALLOWANCE");
            f.combat(Command.action(Kind.STAND_UP, fighter));
            f.combat(nextReady);
            f.combat(Command.action(Kind.DROP_PRONE, fighter));
            f.combat(Command.action(Kind.END_TURN, fighter));
            f.combat(Command.move(guard, List.of(cell(6, 1))));
            assertNull(f.battle().activeFlow());
            assertTrue(f.actor(fighter).reactionAvailable());
            assertEquals(cell(4, 5), f.actor(fighter).position());
            assertEquals(ReadyStatus.ARMED, f.battle().readyPreparations().getLast().status());
        }
    }

    private static void assertTamperedFilesRejected(CampaignStorageService service, byte[] complete) {
        var before = service.status();
        var material = CampaignJson.read(complete);
        var movementRule = material.getAsJsonObject("domains").getAsJsonObject("material").getAsJsonArray("rules").asList().stream()
                .map(JsonElement::getAsJsonObject).map(rule -> rule.getAsJsonObject("definition"))
                .filter(definition -> definition.get("type").getAsString().equals("rule.MovementDefinition"))
                .findFirst().orElseThrow();
        movementRule.addProperty("algorithm", "UNKNOWN_WALK_ALGORITHM");
        assertCode("INVALID_FILE", () -> service.importFile(resign(material)));

        var audit = CampaignJson.read(complete);
        var history = audit.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
        var current = history.getAsJsonObject("current");
        var movement = current.getAsJsonArray("flows").asList().getLast().getAsJsonObject()
                .getAsJsonArray("movements").get(0).getAsJsonObject();
        assertEquals(5, movement.get("spent").getAsInt());
        movement.addProperty("spent", "10");
        history.getAsJsonArray("events").asList().getLast().getAsJsonObject().getAsJsonObject("resolution")
                .add("after", current.deepCopy());
        assertCode("INVALID_FILE", () -> service.importFile(resign(audit)));
        assertEquals(before, service.status());
    }

    private static byte[] resign(JsonObject file) {
        var integrity = file.remove("integrity").getAsJsonObject();
        file.addProperty("fileKey", id());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        return CampaignJson.canonical(file);
    }

    private static Command readyMove(UUID actor, ReadyTrigger trigger, List<Position> path) {
        return Command.ready(actor, new ReadyInput(trigger, ReadyResponseKind.MOVE, null, null, path, null));
    }

    private static void assertMovement(Movement movement, int cursor, int spent, MovementStatus status) {
        assertEquals(cursor, movement.cursor());
        assertEquals(spent, movement.spent());
        assertEquals(status, movement.status());
    }

    private static StorageException assertCode(String code, Executable operation) {
        var failure = assertThrows(StorageException.class, operation);
        assertEquals(code, failure.code(), failure.getMessage());
        return failure;
    }

    private static Position cell(int x, int y) { return new Position(x, y); }
    private static String id() { return UUID.randomUUID().toString(); }

    private static JsonObject input(Object... pairs) {
        var input = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) input.addProperty((String) pairs[i], pairs[i + 1].toString());
        return input;
    }

    private static final class Fixture implements AutoCloseable {
        private final CampaignStorageService service;
        private final ArrayDeque<Integer> dice = new ArrayDeque<>();
        private CampaignRuleContext rules;
        private boolean failAfterDomainWrite;

        Fixture(Path root) throws Exception {
            service = new CampaignStorageService(root, boundary -> {
                if (boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE && failAfterDomainWrite) {
                    failAfterDomainWrite = false;
                    throw new IllegalStateException("posture transaction failure");
                }
            }, sides -> {
                Integer roll = dice.poll();
                assertNotNull(roll, "Unexpected live d" + sides);
                assertTrue(roll >= 1 && roll <= sides, "Invalid scripted d" + sides);
                return roll;
            });
        }

        void create() {
            var members = CampaignRuleFixtures.install(service);
            var preview = service.previewRules(members);
            assertEquals(members, preview.binding().members());
            service.create("倒地验收", members);
            assertEquals(preview.binding(), state().material().binding());
            rules = state().material().restore();
        }

        CampaignStorageService.AccessSnapshot access() { return service.accessSnapshot(); }
        CampaignDomainData.State state() { return access().snapshot().domains(); }
        Battle battle() { return CampaignSpaceCommands.currentBattle(state()).current(); }
        Combatant actor(UUID id) {
            return battle().combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow();
        }
        Schema2CharacterRuntime.State card(UUID id) {
            return state().characters().stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow().history().current();
        }
        long personalSecond(UUID id) {
            var rest = card(id).rest();
            return rest.minute() * 60 + rest.second();
        }
        Movement movement() { return battle().flows().getLast().movements().getFirst(); }
        AttackAttempt attack() { return battle().flows().getLast().attacks().getFirst(); }
        Window window() { return battle().activeFlow().windows().getLast(); }

        UUID character(String name, String kind) {
            UUID id = subject("character-create", input("name", name, "kind", kind, "classKey", "fighter", "backgroundKey", "acolyte"));
            assertEquals(1, card(id).level());
            return id;
        }
        UUID monster() {
            UUID world = PlayerSpaceFixture.placeAtWorld(service);
            assertEquals(CampaignSpaceData.Ref.world(world), state().space().focus().space());
            var payload = input("name", "地狱犬");
            payload.add("monster", CampaignDomainCodec.toJson(rules.rule("adventure.monster", "monster.hell_hound")));
            return subject("monster-create", payload);
        }
        UUID equip(UUID actor, String itemKey) {
            UUID item = UUID.randomUUID();
            equipment(item.toString(), actor, "ITEM_ACQUIRED", new EquipmentInput(item,
                    rules.rule("equipment.item", itemKey), "CARRIED", "主持人确认取得武器"));
            equipment(id(), actor, "ITEM_EQUIPPED", new EquipmentInput(item, null, "MAIN_HAND", "主持人确认主手装备"));
            return item;
        }
        private void equipment(String request, UUID actor, String kind, EquipmentInput equipment) {
            var payload = input("characterId", actor, "characterVersion", card(actor).version());
            payload.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command(kind, null, 0,
                    null, null, null, GrowthChoices.EMPTY, null, null, equipment, null)));
            CampaignSpaceFixtures.command(service, request, "character-command", payload);
        }
        private UUID subject(String action, JsonObject input) {
            return UUID.fromString(CampaignSpaceFixtures.command(service, action, input).lastCommand().subjectId());
        }
        void startOpponents(UUID fighter, Position start, UUID guard, Position defender) {
            start(List.of(new Deployment(fighter, start, 0), new Deployment(guard, defender, 1)), 20, 10);
        }
        void start(List<Deployment> placements, Integer... initiative) {
            var roster = placements.stream().map(Deployment::characterId).toList();
            UUID world = PlayerSpaceFixture.placeAtWorld(service, roster.toArray(UUID[]::new));
            UUID scene = subject("scene-create", input("name", "演练场", "nodeId", world, "width", 10, "height", 10,
                    "resolution", "RULES"));
            var enter = input("sceneId", scene);
            var subjects = new JsonArray();
            var state = state();
            for (var placement : placements) {
                var position = CampaignSpaceCommands.position(state, placement.characterId());
                subjects.add(input("id", placement.characterId(), "version", position.version(),
                        "x", placement.position().x(), "y", placement.position().y(), "side", placement.side()));
            }
            enter.add("subjects", subjects);
            CampaignSpaceFixtures.command(service, "scene-enter", enter);
            combat(Command.reenter(roster.getFirst(), roster), initiative);
            assertEquals(roster.getFirst(), battle().activeActor());
        }
        void leave(UUID... roster) {
            var payload = new JsonObject();
            var subjects = new JsonArray();
            var state = state();
            for (UUID actor : roster)
                subjects.add(input("id", actor, "version", CampaignSpaceCommands.position(state, actor).version()));
            payload.add("subjects", subjects);
            CampaignSpaceFixtures.command(service, "scene-leave", payload);
        }
        Command reaction(Kind kind, UUID actor, UUID item) {
            var flow = battle().activeFlow();
            var window = flow.windows().getLast();
            var opportunity = window.opportunities().getFirst();
            assertEquals(actor, opportunity.actorId());
            assertEquals(OpportunityStatus.PENDING, opportunity.status());
            return Command.reaction(kind, actor, item, flow.flowKey(), flow.leafFrameKey(), window.windowKey(),
                    opportunity.opportunityKey());
        }
        Command continuation(UUID actor) {
            var flow = battle().activeFlow();
            var window = flow.windows().getLast();
            assertEquals(WindowStatus.CLOSED, window.status());
            return Command.continuation(actor, flow.flowKey(), flow.leafFrameKey(), window.windowKey());
        }
        JsonObject combatInput(Command command) {
            var battle = battle();
            var input = input("combatId", battle.combatId(), "combatVersion", battle.version());
            input.add("command", CampaignDomainCodec.toJson(command));
            return CampaignSpaceFixtures.focused(state(), input);
        }
        CampaignStorageService.CampaignView submit(String request, CampaignStorageService.AccessSnapshot at, JsonObject input) {
            return service.domainCommand(request, at.epoch(), at.revision(), "combat-command", input);
        }
        void combat(Command command, Integer... rolls) {
            assertTrue(dice.isEmpty());
            dice.addAll(List.of(rolls));
            submit(id(), access(), combatInput(command));
            assertTrue(dice.isEmpty(), "Not all scripted dice were drawn");
        }
        void reject(Command command, String reason) {
            var before = access();
            var failure = assertCode("DOMAIN_REJECTED", () -> submit(id(), before, combatInput(command)));
            assertEquals(reason, failure.getMessage());
            assertEquals(before, access());
        }
        CampaignData.Snapshot decode(String file) throws Exception {
            return new CampaignCodec().decode(Files.readAllBytes(service.savedFile(file))).snapshot();
        }
        void reopen() throws Exception {
            var before = access();
            String file = service.end(id(), before.epoch(), before.revision()).fileKey();
            assertEquals(before.snapshot(), decode(file));
            service.open(file);
            assertNotEquals(before.epoch(), access().epoch());
            assertEquals(before.snapshot(), access().snapshot());
        }
        @Override public void close() throws Exception { service.close(); }
    }
}
