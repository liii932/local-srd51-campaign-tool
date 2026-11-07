package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.CampaignDomainEngine.CharacterRecord;
import com.dndtool.service.Schema2CombatState.*;
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
        f.engine.validateCampaign(f.material, f.characters, List.of());
        assertEquals(3, record.history().events().size());
    }

    @Test void pendingOpportunityRestoresWithoutRollingAndContinuesOnce() {
        Fixture f = new Fixture(); f.start();
        f.command(Schema2CombatState.Command.move(f.a, List.of(new Position(-1, 0))), NO_DICE);
        var retained = f.combat; var characters = f.characters;
        var flow = retained.current().activeFlow(); var window = flow.windows().getLast();
        assertEquals(WindowStatus.OPEN, window.status());
        new CampaignDomainEngine().validateCampaign(f.material, characters, List.of(retained));
        assertEquals(new Position(0, 0), retained.current().combatants().stream().filter(c -> c.characterId().equals(f.a)).findFirst().orElseThrow().position());
        var opportunity = window.opportunities().getFirst();
        f.command(Schema2CombatState.Command.reaction(Kind.PASS_REACTION, opportunity.actorId(), null,
                flow.flowKey(), flow.leafFrameKey(), window.windowKey(), opportunity.opportunityKey()), NO_DICE);
        flow = f.combat.current().activeFlow(); window = flow.windows().getLast();
        var continuation = Schema2CombatState.Command.continuation(f.a, flow.flowKey(), flow.leafFrameKey(), window.windowKey());
        f.command(continuation, NO_DICE);
        assertNull(f.combat.current().activeFlow());
        assertEquals(new Position(-1, 0), f.combat.current().combatants().stream().filter(c -> c.characterId().equals(f.a)).findFirst().orElseThrow().position());
        assertThrows(IllegalArgumentException.class, () -> f.command(continuation, NO_DICE));
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
        assertEquals(2, f.combat.events().getLast().characterOperations().size());
        for (var record : f.characters) assertEquals(6, record.history().current().rest().second());
        f.engine.validateCampaign(f.material, f.characters, List.of(f.combat));
        assertThrows(IllegalArgumentException.class, () -> f.engine.validateCampaign(f.material, f.characters, List.of()));
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

    @Test void campaignTimeUsesEachRestConsumerAndRejectsActiveSceneBeforeDice() {
        Fixture f = new Fixture();
        var target = f.characters.getLast();
        var resting = f.engine.executeCharacter(f.material, target.initial(), target.history(),
                new Schema2CharacterRuntime.Command("REST_STARTED", null, 0, null, null, null,
                        GrowthChoices.EMPTY, new RestInput("SHORT_REST", null, 0, 0, Map.of())), UUID.randomUUID(), NO_DICE);
        f.characters = List.of(f.characters.getFirst(), new CharacterRecord(target.name(), target.kind(), target.initial(), resting));
        UUID root = UUID.randomUUID();
        f.characters = f.engine.executeCampaignCharacter(f.material, f.characters, List.of(), f.a,
                new Schema2CharacterRuntime.Command("TIME_ADVANCED", null, 0, null, null, null,
                        GrowthChoices.EMPTY, new RestInput(null, "NONE", 30, 0, Map.of())), root, NO_DICE);
        var first = f.characters.getFirst().history().current(); var second = f.characters.getLast().history().current();
        assertEquals(first.rest().minute(), second.rest().minute());
        assertEquals(30, second.rest().active().lightMinutes());
        assertFalse(second.rest().active().interrupted());
        assertEquals(CampaignDomainEngine.campaignTimeChildOperation(root, f.b), f.characters.getLast().history().events().getLast().operationId());

        // Close the rest explicitly before battle; time synchronization never grants rest benefits.
        target = f.characters.getLast();
        resting = f.engine.executeCharacter(f.material, target.initial(), target.history(),
                new Schema2CharacterRuntime.Command("REST_ENDED", null, 0, null, null, null,
                        GrowthChoices.EMPTY, new RestInput(null, null, 0, 0, Map.of())), UUID.randomUUID(), NO_DICE);
        f.characters = List.of(f.characters.getFirst(), new CharacterRecord(target.name(), target.kind(), target.initial(), resting));
        f.start();
        assertThrows(IllegalArgumentException.class, () -> f.engine.executeCampaignCharacter(f.material, f.characters,
                List.of(f.combat), f.a, new Schema2CharacterRuntime.Command("RESOURCE_SPENT", ENERGY, 1, null, null), UUID.randomUUID(), NO_DICE));
    }

    @Test void retainedOperationCannotCollideWithFutureCausalChildAfterDice() {
        Fixture f = new Fixture(); UUID root = UUID.randomUUID();
        UUID collision = Schema2CombatCharacterFacts.childOperation(root, f.b, 0);
        var record = f.characters.getLast();
        History changed = f.engine.executeCharacter(f.material, record.initial(), record.history(),
                new Schema2CharacterRuntime.Command("RESOURCE_SPENT", ENERGY, 1, null, null), collision, NO_DICE);
        f.characters = List.of(f.characters.getFirst(), new CharacterRecord(record.name(), record.kind(), record.initial(), changed));
        f.start(); f.command(Schema2CombatState.Command.action(Kind.BEGIN_ATTACK, f.a), NO_DICE);
        assertThrows(IllegalArgumentException.class, () -> f.engine.executeCombat(f.material, f.characters, f.combat,
                f.combatId, root, Schema2CombatState.Command.attack(f.a, f.b, f.items.get(f.a)), NO_DICE));
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
        var records = List.of(departed.characters().getFirst(), later.characters().getFirst());
        f.engine.validateCampaign(f.material, records, List.of(later.history()));
        assertEquals(6, f.engine.validateSceneEvent(f.material, records, later.history(), later.history().events().get(1), 90));
        assertEquals(6, f.engine.validateSceneEvent(f.material, records, later.history(), later.history().events().getLast(), 96));
    }

    private static final class Fixture {
        final CampaignDomainEngine engine = new CampaignDomainEngine();
        final ProductionSchema2Projection material = Schema2CombatTestFixture.material();
        final UUID campaign = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), combatId = UUID.randomUUID();
        final Schema2PreparationIdentity identity = Schema2PreparationIdentity.random();
        final Map<UUID, UUID> items = new HashMap<>();
        List<CharacterRecord> characters = new ArrayList<>();
        Schema2CombatHistory.History combat;

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
            command(Schema2CombatState.Command.start(List.of(new Deployment(a, new Position(0, 0), 0),
                    new Deployment(b, new Position(1, 0), 1)), List.of(a, b)), sides -> 10);
        }
        void command(Schema2CombatState.Command command, IntUnaryOperator dice) {
            var result = engine.executeCombat(material, characters, combat, combatId, UUID.randomUUID(), command, dice);
            characters = result.characters(); combat = result.history();
        }
    }
}
