package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.CampaignDomainEngine.CharacterRecord;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.CampaignData;
import com.dndtool.service.storage.CampaignDomainCodec;
import com.dndtool.service.storage.CampaignDomainCommands;
import com.dndtool.service.storage.CampaignDomainData;
import com.dndtool.service.storage.CampaignRuleFixtures;
import com.dndtool.service.storage.CampaignSpaceCommands;
import com.dndtool.service.storage.CampaignSpaceData.Ref;
import com.dndtool.service.storage.StorageException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignDomainEngineTest {
    private static final IntUnaryOperator NO_DICE = sides -> { throw new AssertionError("Unexpected die: " + sides); };

    @Test void sharedRunCharactersRetainGrowthEquipmentAndVerifiedHistory() {
        Fixture f = new Fixture();
        assertEquals(f.characters.getFirst().history().current().identity(), f.characters.getLast().history().current().identity());
        var record = f.characters.getFirst();
        History spent = f.engine.executeCharacter(f.material, record.initial(), record.history(),
                new Schema2CharacterRuntime.Command("RESOURCE_SPENT", ENERGY, 1, null, null), UUID.randomUUID(), NO_DICE);
        assertEquals(record.history().current().resources().getFirst().current() - 1, spent.current().resources().getFirst().current());
        f.engine.validateCharacter(f.material, record.initial(), spent);
        for (var member : f.characters) {
            f.engine.validateCharacter(f.material, member.initial(), member.history());
        }
        assertEquals(3, record.history().events().size());
    }

    @Test void pendingOpportunityHistoryContinuesOnceWithoutRolling() {
        Fixture f = new Fixture(); f.start();
        f.command(Schema2CombatState.Command.move(f.a, List.of(new Position(0, 1))), NO_DICE);
        var retained = f.combat;
        var flow = retained.current().activeFlow(); var window = flow.windows().getLast();
        assertEquals(WindowStatus.OPEN, window.status());
        Schema2CombatHistory.validate(retained);
        assertEquals(new Position(1, 1), retained.current().combatants().stream()
                .filter(c -> c.characterId().equals(f.a)).findFirst().orElseThrow().position());
        assertThrows(IllegalArgumentException.class, () -> f.command(
                Schema2CombatState.Command.action(Kind.BEGIN_ATTACK, f.a), NO_DICE));
        assertEquals(retained, f.combat);
        var opportunity = window.opportunities().getFirst();
        f.command(Schema2CombatState.Command.reaction(Kind.PASS_REACTION, opportunity.actorId(), null,
                flow.flowKey(), flow.leafFrameKey(), window.windowKey(), opportunity.opportunityKey()), NO_DICE);
        flow = f.combat.current().activeFlow(); window = flow.windows().getLast();
        var continuation = Schema2CombatState.Command.continuation(f.a, flow.flowKey(), flow.leafFrameKey(), window.windowKey());
        f.command(continuation, NO_DICE);
        assertNull(f.combat.current().activeFlow());
        assertEquals(new Position(0, 1), f.combat.current().combatants().stream().filter(c -> c.characterId().equals(f.a)).findFirst().orElseThrow().position());
        assertThrows(IllegalArgumentException.class, () -> f.command(continuation, NO_DICE));
    }

    @Test void reentryReservesTheExactEventSuffixForEndingCombatAndClosingTheScene() {
        Fixture f = new Fixture();
        f.start();
        f.command(Schema2CombatState.Command.action(Kind.END_COMBAT, f.a), NO_DICE);
        var edit = Schema2CombatState.Command.setObservationCell(new ObservationCellInput(new Position(0, 0), true, false, Cover.NONE));
        int lastReentryPrefix = Schema2CombatHistory.MAX_EVENTS - 4;
        while (f.combat.events().size() < lastReentryPrefix) f.command(edit, NO_DICE);

        var validPrefix = f.combat;
        f.command(edit, NO_DICE);
        var tooLong = f.combat;
        var reenter = Schema2CombatState.Command.reenter(f.a, List.of(f.a, f.b));
        var rejected = assertThrows(IllegalArgumentException.class, () -> f.command(reenter, NO_DICE));
        assertEquals("S2_COMBAT_CAPACITY", rejected.getMessage());
        assertEquals(tooLong, f.combat);

        f.combat = validPrefix;
        f.command(reenter, sides -> 10);
        f.command(Schema2CombatState.Command.action(Kind.END_COMBAT, f.a), NO_DICE);
        f.command(Schema2CombatState.Command.sceneRoster(List.of(), List.of()), NO_DICE);
        f.characters = List.of();
        f.command(Schema2CombatState.Command.action(Kind.CLOSE_SCENE, null), NO_DICE);
        assertEquals(Status.ENDED, f.combat.current().status());
        assertEquals(Schema2CombatHistory.MAX_EVENTS, f.combat.events().size());
    }

    @Test void actualDamageAndRoundClockHaveCompleteCausalChildren() {
        Fixture f = new Fixture(); f.start();
        f.command(Schema2CombatState.Command.action(Kind.BEGIN_ATTACK, f.a), NO_DICE);
        var dice = new ArrayDeque<>(List.of(20, 1, 1));
        f.command(Schema2CombatState.Command.attack(f.a, f.b, f.items.get(f.a)), sides -> dice.removeFirst());
        assertTrue(dice.isEmpty());
        var child = f.characters.getLast().history().events().getLast();
        assertEquals("COMBAT_DAMAGE_APPLIED", child.kind());
        assertEquals(f.combat.events().getLast().operationId(), child.command().combatInput().combatOperationId());
        f.command(Schema2CombatState.Command.action(Kind.END_TURN, f.a), NO_DICE);
        f.command(Schema2CombatState.Command.action(Kind.END_TURN, f.b), NO_DICE);
        var round = f.combat.events().getLast();
        assertEquals(2, round.characterOperations().size());
        for (var record : f.characters) {
            assertEquals(6, record.history().current().rest().second());
            var time = record.history().events().getLast();
            assertTrue(round.characterOperations().contains(time.operationId()));
            assertEquals(round.operationId(), time.command().combatInput().combatOperationId());
        }
        assertEquals(96, f.sceneSecond);
        Schema2CombatHistory.validate(f.combat);
    }

    @Test void forgedHistoryAndInvalidRequestRejectBeforeRandomness() {
        Fixture f = new Fixture(); var record = f.characters.getFirst();
        var events = new ArrayList<>(record.history().events()); var last = events.getLast();
        events.set(events.size() - 1, new Event(last.operationId(), "0".repeat(64), last.command(),
                last.hitDieRoll(), last.before(), last.after(), last.checkResult(), last.healthResult()));
        var forged = new History(record.history().current(), events);
        assertThrows(IllegalArgumentException.class, () -> f.engine.executeCharacter(f.material, record.initial(), forged,
                new Schema2CharacterRuntime.Command("RESOURCE_SPENT", ENERGY, 1, null, null), UUID.randomUUID(), NO_DICE));
        assertThrows(IllegalArgumentException.class, () -> f.engine.executeCharacter(f.material, record.initial(), record.history(),
                new Schema2CharacterRuntime.Command("RESOURCE_SPENT", ENERGY, 999, null, null), UUID.randomUUID(), NO_DICE));
        f.start(); var retained = f.combat;
        assertThrows(IllegalArgumentException.class, () -> f.command(Schema2CombatState.Command.move(f.a,
                List.of(new Position(10000, 10000))), NO_DICE));
        assertEquals(retained, f.combat);
    }

    @Test void focusedTimeUsesEachRestConsumerAndRejectsCharacterCommandsInItsRuleScene() {
        var fixture = new FocusedCampaign();
        UUID first = fixture.fighter("甲");
        UUID resting = fixture.fighter("乙");
        UUID background = fixture.fighter("丙");
        UUID backgroundWorld = fixture.command("node-create", fields("name", "背景节点"));
        fixture.focus(backgroundWorld);
        fixture.command("position", fields("characterId", background, "nodeId", backgroundWorld));
        var backgroundHistory = fixture.character(background).history();

        UUID world = fixture.command("node-create", fields("name", "当前节点"));
        fixture.focus(world);
        fixture.command("position", fields("characterId", first, "nodeId", world));
        fixture.command("position", fields("characterId", resting, "nodeId", world));
        fixture.command("character-command", fixture.characterInput(resting,
                rest("REST_STARTED", "SHORT_REST", null, 0)));

        UUID timeOperation = UUID.randomUUID();
        var advanceTime = rest("TIME_ADVANCED", null, "NONE", 30);
        fixture.command("character-command", fixture.characterInput(first, advanceTime), timeOperation, NO_DICE);

        var firstRest = fixture.character(first).history().current().rest();
        var secondRest = fixture.character(resting).history().current().rest();
        assertEquals(30, firstRest.minute());
        assertEquals(firstRest.minute(), secondRest.minute());
        assertEquals(30, secondRest.active().lightMinutes());
        assertFalse(secondRest.active().interrupted());
        UUID timeChild = CampaignDomainEngine.campaignTimeChildOperation(timeOperation, resting);
        assertEquals(timeChild, fixture.character(resting).history().events().getLast().operationId());
        assertEquals(Set.of(timeOperation, timeChild), Set.copyOf(fixture.state().roots().getLast().characterOperations()));
        assertEquals(1800, fixture.state().space().clock(Ref.world(world)).second());
        assertEquals(0, fixture.state().space().clock(Ref.world(backgroundWorld)).second());
        assertEquals(backgroundHistory, fixture.character(background).history());

        fixture.command("character-command", fixture.characterInput(resting, rest("REST_ENDED", null, null, 0)));
        UUID scene = fixture.command("scene-create", fields("nodeId", world, "name", "规则场景",
                "width", 8, "height", 8, "resolution", "RULES"));
        var subjects = new JsonArray();
        subjects.add(fixture.placement(first, 1, 0));
        subjects.add(fixture.placement(resting, 2, 1));
        var enter = fields("sceneId", scene);
        enter.add("subjects", subjects);
        fixture.command("scene-enter", enter);
        var battle = CampaignSpaceCommands.currentBattle(fixture.state());
        var combatInput = fields("combatId", scene, "combatVersion", battle.current().version());
        combatInput.add("command", CampaignDomainCodec.toJson(
                Schema2CombatState.Command.reenter(first, List.of(first, resting))));
        var initiativeDice = new ArrayDeque<>(List.of(10, 10));
        fixture.command("combat-command", combatInput, UUID.randomUUID(), sides -> {
            assertEquals(20, sides);
            return initiativeDice.removeFirst();
        });
        assertTrue(initiativeDice.isEmpty());
        assertEquals(Status.ACTIVE, CampaignSpaceCommands.currentBattle(fixture.state()).current().status());
        assertEquals(Ref.scene(scene), fixture.state().space().focus().space());

        var retained = fixture.snapshot;
        var characterInput = fixture.characterInput(first, advanceTime);
        assertEquals("RULE_SCENE_OWNER_REQUIRED", assertThrows(StorageException.class,
                () -> fixture.command("character-command", characterInput)).code());
        characterInput.addProperty("characterVersion", "0");
        assertEquals("STALE_CHARACTER", assertThrows(StorageException.class,
                () -> fixture.command("character-command", characterInput)).code());
        assertEquals(retained, fixture.snapshot);
        assertEquals(backgroundHistory, fixture.character(background).history());
        assertEquals(retained, CampaignData.validate(retained));
    }

    private static Schema2CharacterRuntime.Command rest(String kind, String restKind, String activity, int minutes) {
        return new Schema2CharacterRuntime.Command(kind, null, 0, null, null, null,
                GrowthChoices.EMPTY, new RestInput(restKind, activity, minutes, 0, Map.of()));
    }

    @Test void retainedOperationRejectsFutureCausalChildBeforeDice() {
        Fixture f = new Fixture(); UUID root = UUID.randomUUID();
        UUID collision = Schema2CombatCharacterFacts.childOperation(root, f.b, 0);
        var record = f.characters.getLast();
        History changed = f.engine.executeCharacter(f.material, record.initial(), record.history(),
                new Schema2CharacterRuntime.Command("RESOURCE_SPENT", ENERGY, 1, null, null), collision, NO_DICE);
        f.characters = List.of(f.characters.getFirst(), new CharacterRecord(record.name(), record.kind(), record.initial(), changed));
        f.start(); f.command(Schema2CombatState.Command.action(Kind.BEGIN_ATTACK, f.a), NO_DICE);
        assertEquals("S2_COMBAT_OPERATION_CONFLICT", assertThrows(IllegalArgumentException.class,
                () -> f.engine.executeScene(f.material, f.characters, f.combat, f.campaign, f.combatId,
                        f.sceneSecond, root, Schema2CombatState.Command.attack(f.a, f.b, f.items.get(f.a)),
                        NO_DICE)).getMessage());
    }

    @Test void spatialRosterUsesActualVersionsAndPreservesEarlierMovement() {
        Fixture f = new Fixture();
        CharacterRecord a = f.characters.getFirst();
        CharacterRecord b = f.characters.getLast();
        History older = f.engine.executeCharacter(f.material, a.initial(), a.history(),
                CampaignDomainEngine.spaceTimeCommand(a.history().current(), 10), UUID.randomUUID(), NO_DICE);
        a = new CharacterRecord(a.name(), a.kind(), a.initial(), older);
        var aPlace = new Deployment(f.a, new Position(0, 0), 0);
        var bPlace = new Deployment(f.b, new Position(1, 0), 1);
        var opened = f.engine.executeScene(f.material, List.of(a, b), null, f.campaign, f.combatId, 90,
                UUID.randomUUID(), Schema2CombatState.Command.openScene(List.of(aPlace, bPlace), List.of()), NO_DICE);
        assertEquals(Status.EXPLORATION, opened.history().current().status());
        assertTrue(opened.history().current().initiative().isEmpty());
        var moved = f.engine.executeScene(f.material, opened.characters(), opened.history(), f.campaign, f.combatId, 90,
                UUID.randomUUID(), Schema2CombatState.Command.exploreMove(f.a, List.of(new Position(0, 1))), NO_DICE);
        assertEquals(6, moved.elapsedSeconds());
        assertEquals(10, moved.characters().getFirst().history().current().rest().minute());
        assertEquals(0, moved.characters().getLast().history().current().rest().minute());
        assertTrue(moved.characters().stream().allMatch(record -> record.history().current().rest().second() == 6));
        var departed = f.engine.executeScene(f.material, moved.characters(), moved.history(), f.campaign, f.combatId, 96,
                UUID.randomUUID(), Schema2CombatState.Command.sceneRoster(List.of(bPlace)), NO_DICE);
        var later = f.engine.executeScene(f.material, List.of(departed.characters().getLast()), departed.history(),
                f.campaign, f.combatId, 96, UUID.randomUUID(),
                Schema2CombatState.Command.exploreMove(f.b, List.of(new Position(2, 0))), NO_DICE);
        assertEquals(f.combatId, later.history().current().combatId());
        assertEquals(List.of(f.b), later.history().current().combatants().stream().map(Combatant::characterId).toList());
        assertEquals(2, later.history().current().flows().size());
        assertEquals(moved.history().current().flows().getFirst(), later.history().current().flows().getFirst());
        assertEquals(List.of(f.b), later.history().events().getLast().dependencies().stream()
                .map(Schema2CombatHistory.Dependency::characterId).toList());
        assertEquals(0, departed.elapsedSeconds());
        assertEquals(6, later.elapsedSeconds());
        assertEquals(6, departed.characters().getFirst().history().current().rest().second());
        assertEquals(12, later.characters().getFirst().history().current().rest().second());
        Schema2CombatHistory.validate(later.history());
    }

    private static JsonObject fields(Object... entries) {
        var result = new JsonObject();
        for (int i = 0; i < entries.length; i += 2) {
            result.addProperty((String) entries[i], entries[i + 1].toString());
        }
        return result;
    }

    private static final class FocusedCampaign {
        final CampaignDomainCommands commands = new CampaignDomainCommands();
        CampaignData.Snapshot snapshot = CampaignRuleFixtures.snapshot(UUID.randomUUID().toString(), "场景时钟");

        CampaignDomainData.State state() {
            return snapshot.domains();
        }

        UUID command(String action, JsonObject input) {
            return command(action, input, UUID.randomUUID(), NO_DICE);
        }

        UUID command(String action, JsonObject input, UUID operation, IntUnaryOperator dice) {
            var focused = input.deepCopy();
            focused.add("focus", CampaignSpaceCommands.focusPayload(state()));
            var after = commands.apply(snapshot, action, focused, operation, "0".repeat(64), dice);
            snapshot = new CampaignData.Snapshot(snapshot.campaignId(), snapshot.name(), after);
            return after.roots().getLast().subjectId();
        }

        UUID fighter(String name) {
            return command("character-create", fields("name", name, "kind", "PC",
                    "classKey", "fighter", "backgroundKey", "acolyte"));
        }

        void focus(UUID world) {
            var input = new JsonObject();
            input.add("target", CampaignSpaceCommands.refJson(Ref.world(world)));
            command("focus-set", input);
        }

        CampaignDomainData.Character character(UUID id) {
            return state().characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        }

        JsonObject characterInput(UUID character, Schema2CharacterRuntime.Command command) {
            var input = fields("characterId", character, "characterVersion", character(character).history().current().version());
            input.add("command", CampaignDomainCodec.toJson(command));
            return input;
        }

        JsonObject placement(UUID character, int x, int side) {
            return fields("id", character, "version", CampaignSpaceCommands.position(state(), character).version(),
                    "x", x, "y", 1, "side", side);
        }
    }

    private static final class Fixture {
        final CampaignDomainEngine engine = new CampaignDomainEngine();
        final ProductionSchema2Projection material = Schema2CombatTestFixture.material();
        final UUID campaign = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), combatId = UUID.randomUUID();
        final Schema2PreparationIdentity identity = Schema2PreparationIdentity.random();
        final Map<UUID, UUID> items = new HashMap<>();
        List<CharacterRecord> characters = new ArrayList<>();
        Schema2CombatHistory.History combat;
        long sceneSecond = 90;

        Fixture() {
            for (UUID id : List.of(a, b)) {
                var initial = fullPlan(material, identity, campaign, id);
                History history = engine.initial(material, initial, UUID.randomUUID(), fullAbilities(), INITIAL_CHOICES, UUID.randomUUID());
                UUID item = UUID.randomUUID(); items.put(id, item);
                history = engine.executeCharacter(material, initial, history, equipment("ITEM_ACQUIRED", item, ITEM_WEAPON, "CARRIED"), item, NO_DICE);
                history = engine.executeCharacter(material, initial, history, equipment("ITEM_EQUIPPED", item, null, "MAIN_HAND"), UUID.randomUUID(), NO_DICE);
                characters.add(new CharacterRecord(id.equals(a) ? "甲" : "乙", "PC", initial, history));
            }
        }
        void start() {
            var deployments = List.of(new Deployment(a, new Position(1, 1), 0),
                    new Deployment(b, new Position(2, 1), 1));
            command(Schema2CombatState.Command.openScene(deployments, List.of()), NO_DICE);
            command(Schema2CombatState.Command.reenter(a, List.of(a, b)), sides -> 10);
        }
        void command(Schema2CombatState.Command command, IntUnaryOperator dice) {
            var result = engine.executeScene(material, characters, combat, campaign, combatId, sceneSecond,
                    UUID.randomUUID(), command, dice);
            characters = result.characters();
            combat = result.history();
            sceneSecond += result.elapsedSeconds();
        }
    }
}
