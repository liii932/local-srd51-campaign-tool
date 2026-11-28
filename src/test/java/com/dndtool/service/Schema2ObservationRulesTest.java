package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.CampaignDomainCodec;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2CheckTestFixture.*;
import static com.dndtool.service.Schema2CombatTestFixture.context;
import static com.dndtool.service.Schema2CombatTestFixture.withState;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ObservationRulesTest {
    private static final Position SHELTER = new Position(5, 0);
    private final UUID campaign = UUID.randomUUID(), scene = UUID.randomUUID();
    private final UUID hider = UUID.randomUUID(), searcher = UUID.randomUUID(), watchful = UUID.randomUUID();
    private final Schema2CombatRules rules = new Schema2CombatRules();
    private final ProductionSchema2Projection material = materialWithPerception();
    private final Map<UUID, Context> roster = new HashMap<>(Map.of(
            hider, context(material, campaign, hider), searcher, context(material, campaign, searcher),
            watchful, withWisdom(context(material, campaign, watchful), 14)));
    private Battle battle;

    @Test void hideUsesFrozenStealthAndSeparatePassiveScoresAndSearchKeepsTies() {
        Context source = roster.get(hider);
        UUID armor = UUID.randomUUID();
        var equipmentRules = new Schema2CharacterEquipmentRules();
        var carried = equipmentRules.apply(material, source.character(), equipment("ITEM_ACQUIRED", armor, ITEM_MEDIUM, "CARRIED"), armor);
        var worn = equipmentRules.apply(material, carried, equipment("ITEM_EQUIPPED", armor, null, "WORN"), UUID.randomUUID());
        roster.put(hider, new Context(material, source.initial(), worn, source.sceneSecond()));
        start();
        reject(Command.action(Kind.HIDE, hider), "S2_HIDE_UNAVAILABLE");
        set(SHELTER, true, false, Cover.HALF);
        reject(Command.action(Kind.SEARCH, searcher), "S2_COMBAT_NOT_CURRENT_TURN");
        var hidden = apply(Command.action(Kind.HIDE, hider), 19, 9);
        var check = battle.observation().checks().getLast();
        assertEquals(STEALTH, check.skill());
        assertEquals(new ObservationRoll(19, 9, 9, 1, 0, true, 10), check.roll());
        assertEquals(List.of(new Roll("OBSERVATION_STEALTH", hider, 20, 19),
                new Roll("OBSERVATION_STEALTH_SECOND", hider, 20, 9)), hidden.rolls());
        assertEquals(Map.of(searcher, 9, watchful, 12), check.contests().stream().collect(
                java.util.stream.Collectors.toMap(ObservationContest::counterpartId, ObservationContest::opposingScore)));
        assertTrue(Schema2ObservationRules.hidden(battle, searcher, hider));
        assertFalse(Schema2ObservationRules.visible(battle, searcher, hider));
        assertTrue(Schema2ObservationRules.visible(battle, watchful, hider));
        assertEquals(SHELTER, Schema2ObservationRules.knownPosition(battle, searcher, hider));
        assertEquals(0, actor(hider).actionsRemaining());
        assertEquals(1, actor(hider).bonusActionsRemaining());
        reject(Command.action(Kind.SEARCH, hider), "S2_COMBAT_ACTION_UNAVAILABLE");

        apply(Command.action(Kind.END_TURN, hider));
        apply(Command.action(Kind.SEARCH, searcher), 11);
        var tiedSearch = battle.observation().checks().getLast();
        assertEquals(10, tiedSearch.roll().total());
        assertEquals(List.of(new ObservationContest(hider, 10, check.operationId(), true)), tiedSearch.contests());
        assertTrue(Schema2ObservationRules.hidden(battle, searcher, hider));
        apply(Command.action(Kind.END_TURN, searcher));
        apply(Command.action(Kind.END_TURN, watchful));

        apply(Command.action(Kind.HIDE, hider), 20, 8);
        assertEquals(new ObservationContest(searcher, 9, check.operationId(), true),
                battle.observation().checks().getLast().contests().stream()
                    .filter(contest -> contest.counterpartId().equals(searcher)).findFirst().orElseThrow());
        assertEquals(check.operationId(), battle.observation().pairs().stream()
                .filter(pair -> pair.observerId().equals(searcher) && pair.subjectId().equals(hider))
                .findFirst().orElseThrow().hideOperationId());
        apply(Command.action(Kind.END_TURN, hider));
        apply(Command.action(Kind.SEARCH, searcher), 12);
        assertTrue(Schema2ObservationRules.visible(battle, searcher, hider));
        assertTrue(Schema2ObservationRules.visible(battle, watchful, hider));
    }

    @Test void movementRecordsLastVisibleCheckpointAndAttackRevealsOnlyItsSubject() {
        start();
        Position shelteredMiddle = new Position(5, 1), exposed = new Position(5, 2), obscuredDestination = new Position(5, 3);
        set(SHELTER, true, true, Cover.NONE);
        set(shelteredMiddle, true, false, Cover.NONE);
        set(obscuredDestination, false, true, Cover.NONE);
        set(actor(searcher).position(), true, true, Cover.NONE);
        apply(Command.action(Kind.HIDE, hider), 20);
        apply(Command.move(hider, List.of(shelteredMiddle, exposed, obscuredDestination)));
        assertFalse(Schema2ObservationRules.visible(battle, watchful, hider));
        assertEquals(exposed, Schema2ObservationRules.knownPosition(battle, watchful, hider));
        assertFalse(Schema2ObservationRules.hidden(battle, watchful, hider));

        apply(Command.action(Kind.END_TURN, hider));
        apply(Command.action(Kind.HIDE, searcher), 20);
        assertTrue(Schema2ObservationRules.hidden(battle, hider, searcher));
        Battle revealed = Schema2ObservationRules.revealAttack(battle, hider);
        assertFalse(Schema2ObservationRules.visible(revealed, watchful, hider));
        assertEquals(obscuredDestination, Schema2ObservationRules.knownPosition(revealed, watchful, hider));
        assertTrue(Schema2ObservationRules.hidden(revealed, hider, searcher));

        set(obscuredDestination, false, false, Cover.NONE);
        assertTrue(Schema2ObservationRules.visible(battle, watchful, hider));
        assertEquals(obscuredDestination, Schema2ObservationRules.knownPosition(battle, watchful, hider));
    }

    @Test void centerRayUsesHighestCoverSkipsCornerContactsAndExcludesTheOrigin() {
        start();
        Position origin = new Position(0, 0), destination = new Position(5, 5);
        set(origin, false, true, Cover.TOTAL);
        set(new Position(1, 0), false, false, Cover.TOTAL);
        set(new Position(1, 1), false, false, Cover.HALF);
        set(new Position(3, 3), false, false, Cover.THREE_QUARTERS);
        assertEquals(Cover.THREE_QUARTERS, Schema2ObservationRules.cover(battle, origin, destination));
        assertTrue(Schema2ObservationRules.visible(battle, origin, destination));
        assertEquals(Cover.TOTAL, Schema2ObservationRules.cover(battle, destination, origin));
        assertFalse(Schema2ObservationRules.visible(battle, destination, origin));
        set(new Position(2, 2), false, true, Cover.NONE);
        assertFalse(Schema2ObservationRules.visible(battle, origin, destination));
        assertEquals(Cover.THREE_QUARTERS, Schema2ObservationRules.cover(battle, origin, destination));
    }

    @Test void terrainCommandWorksInEmptyExplorationAndItsPayloadChangesTheRequestDigest() {
        var engine = new CampaignDomainEngine();
        var opened = engine.executeScene(material, List.of(), null, campaign, scene, 0, scene,
                Command.openScene(List.of(), List.of()), this::unexpectedDie);
        UUID operation = UUID.randomUUID();
        var input = new ObservationCellInput(SHELTER, true, false, Cover.HALF);
        var marked = engine.executeScene(material, List.of(), opened.history(), campaign, scene, 0, operation,
                Command.setObservationCell(input), this::unexpectedDie);
        var different = engine.executeScene(material, List.of(), opened.history(), campaign, scene, 0, operation,
                Command.setObservationCell(new ObservationCellInput(SHELTER, true, false, Cover.TOTAL)), this::unexpectedDie);
        assertEquals(List.of(input), marked.history().current().observation().cells());
        assertNotEquals(marked.history().events().getLast().requestDigest(), different.history().events().getLast().requestDigest());
        var copy = CampaignDomainCodec.fromJson(CampaignDomainCodec.toJson(marked.history()), Schema2CombatHistory.History.class);
        assertEquals(marked.history(), copy);
        assertDoesNotThrow(() -> Schema2CombatHistory.validate(copy));
        var removed = engine.executeScene(material, List.of(), copy, campaign, scene, 0, UUID.randomUUID(),
                Command.setObservationCell(new ObservationCellInput(SHELTER, false, false, Cover.NONE)), this::unexpectedDie);
        assertTrue(removed.history().current().observation().cells().isEmpty());
    }

    private int unexpectedDie(int sides) { throw new AssertionError("Unexpected d" + sides); }

    private Resolution apply(Command command, int... values) {
        UUID operation = UUID.randomUUID();
        var prepared = rules.preflight(operation, scene, campaign, roster, battle, command);
        var cursor = new AtomicInteger();
        Resolution result = rules.resolve(prepared, sides -> {
            assertTrue(cursor.get() < values.length, "Unexpected d" + sides);
            return values[cursor.getAndIncrement()];
        });
        assertEquals(values.length, cursor.get());
        rules.verify(operation, scene, campaign, roster, battle, command, result);
        for (HealthChange change : result.healthChanges()) {
            Context before = roster.get(change.characterId());
            roster.put(change.characterId(), before.after(change.after(), change.seconds()));
        }
        battle = result.after();
        return result;
    }

    private void reject(Command command, String code) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> rules.preflight(UUID.randomUUID(), scene, campaign, roster, battle, command));
        assertEquals(code, exception.getMessage());
    }

    private void start() {
        apply(Command.start(List.of(new Deployment(hider, SHELTER, 0),
                new Deployment(searcher, new Position(0, 0), 1), new Deployment(watchful, new Position(0, 5), 1)),
                List.of(hider, searcher, watchful)), 20, 10, 5);
        assertEquals(hider, battle.activeActor());
    }

    private void set(Position position, boolean hideAllowed, boolean blocksSight, Cover cover) {
        apply(Command.setObservationCell(new ObservationCellInput(position, hideAllowed, blocksSight, cover)));
    }

    private Combatant actor(UUID id) { return Schema2CombatRules.combatant(battle, id); }

    private static Context withWisdom(Context base, int wisdom) {
        var abilities = new HashMap<>(com.dndtool.module.composition.Schema2ProgressionFixtures.fullAbilities());
        abilities.put("ability.charisma", abilities.get("ability.wisdom"));
        abilities.put("ability.wisdom", wisdom);
        var initial = new Schema2CharacterProgressionRules().initial(base.material(), base.initial(),
                base.character().preparationOperationId(), abilities, INITIAL_CHOICES);
        return withState(base, initial);
    }

    private static ProductionSchema2Projection materialWithPerception() {
        var files = Schema2CombatTestFixture.packages();
        var definitions = Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
        JsonObject perception = new JsonObject();
        perception.add("identity", com.dndtool.module.composition.Schema2ProgressionFixtures.jsonIdentity(
                new com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity(STEALTH.moduleKey(),
                    "character.proficiency", "proficiency.skill.perception")));
        perception.addProperty("display_name", "Perception");
        perception.addProperty("description", "Wisdom skill for the ordinary observation fixture.");
        perception.addProperty("source_page", 56);
        perception.addProperty("category", "SKILL");
        perception.addProperty("ability_key", "ability.wisdom");
        definitions.add(perception);
        files.getFirst().put("rules.json", definitions.toString().getBytes(StandardCharsets.UTF_8));
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }
}
