package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.DamageType;
import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.service.storage.CampaignExtraAttackIntegrationTest.Fixture;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignMonsterAttackIntegrationTest {
    private static final Position START = new Position(2, 2);
    private static final Position CARRIED_START = new Position(2, 1);
    private static final List<Position> ROUTE = List.of(new Position(2, 3), new Position(3, 4), new Position(4, 5));
    private static final List<Position> CARRIED_ROUTE = List.of(START, ROUTE.get(0), ROUTE.get(1));
    @TempDir Path temporary;

    @Test void ordinaryBiteUsesAnActionAndRetainsTwoDamageComponentsInTheCompleteFile() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("normal"))) {
            var actors = start(fixture);
            while (!fixture.battle().activeActor().equals(actors.hound()))
                fixture.combat(Command.action(Kind.END_TURN, fixture.battle().activeActor()));
            var hound = fixture.actor(actors.hound());
            long hitPoints = fixture.card(actors.mover()).hitPoints();
            fixture.combat(Command.monsterAttack(actors.hound(), actors.mover(), actors.bite()), 19, 1, 2, 3);

            var attack = attempts(fixture).getFirst();
            assertBite(actors, attack, false, 1, 2, 3);
            assertEquals(hitPoints - 9, fixture.card(actors.mover()).hitPoints());
            assertFalse(fixture.actor(actors.hound()).actionAvailable());
            assertEquals(hound.reactionAvailable(), fixture.actor(actors.hound()).reactionAvailable());
            assertNull(fixture.actor(actors.hound()).sequence());
            assertEquals(List.of(19, 1, 2, 3), draws(fixture));
            var retained = fixture.access().snapshot();
            String file = fixture.end();
            byte[] complete = Files.readAllBytes(fixture.service.savedFile(file));
            assertForgedBiteRejected(fixture.service, complete);
            fixture.service.open(file);
            assertEquals(retained, fixture.access().snapshot());
            assertEquals(List.of(attack), attempts(fixture));
        }
    }

    @Test void passAfterReopenPreservesTheReactionAndCompletesBothPairedPositionsExactlyOnce() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("pass"))) {
            var actors = start(fixture);
            UUID grapple = pauseDrag(fixture, actors);
            Flow checkpoint = fixture.battle().activeFlow();
            reopen(fixture);
            assertEquals(checkpoint, fixture.battle().activeFlow());
            assertPrefix(fixture, actors);
            assertMonsterChoice(fixture, actors);

            fixture.react(Kind.PASS_REACTION, actors.hound(), null);
            assertEquals(List.of(), draws(fixture));
            assertTrue(fixture.actor(actors.hound()).reactionAvailable());
            var at = fixture.access();
            var continuation = fixture.combatInput(resume(fixture, actors.mover()));
            String request = UUID.randomUUID().toString();
            var accepted = fixture.submit(request, at, continuation, null);
            var replay = fixture.submit(request, at, continuation, null);
            assertReplay(fixture, request, accepted, replay);
            assertCompleted(fixture, actors, grapple);
            assertTrue(fixture.actor(actors.hound()).reactionAvailable());
            assertTrue(attempts(fixture).isEmpty());
        }
    }

    @Test void nonlethalOpportunityKeepsThePairAndCompletesTheOriginalSuffix() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("nonlethal"))) {
            var actors = start(fixture);
            UUID grapple = pauseDrag(fixture, actors);
            var opportunity = assertMonsterChoice(fixture, actors);
            Flow flow = fixture.battle().activeFlow();
            fixture.combat(Command.monsterOpportunityAttack(actors.hound(), actors.bite(), flow.flowKey(),
                    flow.leafFrameKey(), flow.windows().getLast().windowKey(), opportunity.opportunityKey()), 19, 1, 1, 1);
            assertBite(actors, attempts(fixture).getFirst(), true, 1, 1, 1);
            assertTrue(fixture.card(actors.mover()).hitPoints() > 0);
            assertFalse(fixture.actor(actors.hound()).reactionAvailable());
            assertPrefix(fixture, actors);
            fixture.continueMovement(actors.mover());
            assertEquals(List.of(), draws(fixture));
            assertCompleted(fixture, actors, grapple);
            assertFalse(fixture.actor(actors.hound()).reactionAvailable());
            assertEquals(1, attempts(fixture).size());
        }
    }

    @Test void savedMonsterOpportunityUsesOneReactionAndIncapacitationStopsOnlyTheUnexecutedSuffix() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("bite"))) {
            var actors = start(fixture);
            UUID member = fixture.player(actors.mover(), actors.carried());
            UUID grapple = pauseDrag(fixture, actors);
            assertMonsterChoice(fixture, actors);
            var moverPosition = position(fixture, actors.mover());
            var carriedPosition = position(fixture, actors.carried());
            Flow checkpoint = fixture.battle().activeFlow();

            reopen(fixture);
            assertEquals(checkpoint, fixture.battle().activeFlow());
            assertPrefix(fixture, actors);
            var opportunity = assertMonsterChoice(fixture, actors);
            var candidates = PlayerCandidateFactory.candidates(fixture.access(), member);
            assertTrue(candidates.stream().anyMatch(candidate -> candidate.action().equals("combat-command")
                    && candidate.input().getAsJsonObject("command").get("kind").getAsString().equals("RELEASE_GRAPPLE")));
            candidates.forEach(candidate -> assertNotEquals(actors.hound().toString(), candidate.characterId()));
            Window window = checkpoint.windows().getLast();
            var bite = Command.monsterOpportunityAttack(actors.hound(), actors.bite(), checkpoint.flowKey(),
                    checkpoint.leafFrameKey(), window.windowKey(), opportunity.opportunityKey());
            var at = fixture.access();
            var input = fixture.combatInput(bite);
            String request = UUID.randomUUID().toString();
            boolean actionAvailable = fixture.actor(actors.hound()).actionAvailable();
            fixture.dice.addAll(List.of(19, 4, 5, 6));
            var accepted = fixture.submit(request, at, input, null);
            assertTrue(fixture.dice.isEmpty());
            assertEquals(List.of(19, 4, 5, 6), draws(fixture));
            assertReplay(fixture, request, accepted, fixture.submit(request, at, input, null));
            assertFalse(fixture.actor(actors.hound()).reactionAvailable());
            assertEquals(actionAvailable, fixture.actor(actors.hound()).actionAvailable());
            assertEquals(0, fixture.card(actors.mover()).hitPoints());
            assertEquals(GrappleEndReason.GRAPPLER_INCAPACITATED, relation(fixture, grapple).endReason());
            assertEquals(request, relation(fixture, grapple).endedOperationId().toString());
            assertEquals(1, attempts(fixture).size());
            var attack = attempts(fixture).getFirst();
            assertBite(actors, attack, true, 4, 5, 6);
            assertPrefix(fixture, actors);
            assertEquals(moverPosition, position(fixture, actors.mover()));
            assertEquals(carriedPosition, position(fixture, actors.carried()));

            fixture.continueMovement(actors.mover());
            assertEquals(MovementStatus.STOPPED, movement(fixture).status());
            assertEquals("ACTOR_INACTIVE_OR_NO_MOVEMENT", movement(fixture).stopReason());
            assertEquals(1, movement(fixture).cursor());
            assertEquals(10, movement(fixture).spentHalfFeet());
            assertPrefix(fixture, actors);
            assertEquals(moverPosition, position(fixture, actors.mover()));
            assertEquals(carriedPosition, position(fixture, actors.carried()));
            assertEquals(List.of(), draws(fixture));
            reopen(fixture);
            assertEquals(List.of(attack), attempts(fixture));
            assertEquals(MovementStatus.STOPPED, movement(fixture).status());
        }
    }

    private static Actors start(Fixture fixture) {
        fixture.create();
        UUID mover = fixture.character("抓者", "PC");
        UUID carried = fixture.character("被拖带的队友", "PC");
        PlayerSpaceFixture.placeAtWorld(fixture.service, mover, carried);
        var rule = fixture.rule("adventure.monster", "monster.hell_hound");
        var create = new JsonObject();
        create.addProperty("name", "地狱犬");
        create.add("monster", CampaignDomainCodec.toJson(rule));
        UUID hound = UUID.fromString(CampaignSpaceFixtures.command(fixture.service, "monster-create", create)
                .lastCommand().subjectId());
        fixture.startBattle("怪物借机攻击", List.of(new Deployment(mover, START, 0),
                new Deployment(carried, CARRIED_START, 0), new Deployment(hound, new Position(1, 2), 1)), 20, 10, 5);
        return new Actors(mover, carried, hound, new EntryIdentity(rule, "actions", rule.moduleKey(), "bite"));
    }

    private static UUID pauseDrag(Fixture fixture, Actors actors) {
        UUID mover = actors.mover(), carried = actors.carried();
        fixture.combat(Command.action(Kind.BEGIN_ATTACK, mover));
        var sequence = fixture.actor(mover).sequence();
        fixture.combat(Command.melee(Kind.GRAPPLE, mover, carried, null, null,
                new MeleeInput(sequence.sequenceKey(), 0, null, HandSlot.MAIN_HAND, null, null)));
        var contest = fixture.battle().activeFlow();
        fixture.combat(Command.melee(Kind.CHOOSE_MELEE_DEFENSE, carried, null, contest.flowKey(), contest.leafFrameKey(),
                new MeleeInput(null, null, null, null, ContestSkill.ACROBATICS, null)), 19, 2);
        UUID grapple = fixture.battle().meleeControl().grapples().getLast().grappleKey();

        var at = fixture.access();
        var beforeMover = position(fixture, mover);
        var beforeCarried = position(fixture, carried);
        var move = fixture.combatInput(Command.move(mover, ROUTE, new DragInput(grapple, CARRIED_ROUTE)));
        String request = UUID.randomUUID().toString();
        var accepted = fixture.submit(request, at, move, null);
        assertReplay(fixture, request, accepted, fixture.submit(request, at, move, null));
        assertPrefix(fixture, actors);
        assertEquals(beforeMover.version() + 1, position(fixture, mover).version());
        assertEquals(beforeCarried.version() + 1, position(fixture, carried).version());
        assertEquals(new DragPlan(grapple, carried, CARRIED_START, CARRIED_ROUTE, 30), movement(fixture).drag());
        assertEquals(MovementStatus.PENDING, movement(fixture).status());
        assertEquals(1, movement(fixture).cursor());
        assertEquals(List.of(), draws(fixture));
        assertTrue(fixture.actor(actors.hound()).reactionAvailable());
        return grapple;
    }

    private static Opportunity assertMonsterChoice(Fixture fixture, Actors actors) {
        var flow = fixture.battle().activeFlow();
        var window = flow.windows().getLast();
        assertEquals(WindowStatus.OPEN, window.status());
        assertEquals(TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE, window.trigger().kind());
        assertEquals(actors.mover(), window.trigger().subjectId());
        assertEquals(List.of(actors.hound()), window.opportunities().stream().map(Opportunity::actorId).toList());
        var opportunity = window.opportunities().getFirst();
        assertEquals(OpportunityStatus.PENDING, opportunity.status());
        assertEquals(flow.leafFrameKey(), opportunity.selectionFrameKey());
        var option = opportunity.options().getFirst();
        assertEquals(1, opportunity.options().size());
        assertEquals(ReactionKind.OPPORTUNITY_ATTACK, option.kind());
        assertEquals(actors.bite(), option.monsterAttack());
        assertNull(option.itemId());
        return opportunity;
    }

    private static void assertBite(Actors actors, AttackAttempt attack, boolean opportunity, int piercing, int fireFirst, int fireSecond) {
        assertEquals(actors.hound(), attack.actorId());
        assertEquals(actors.mover(), attack.targetId());
        assertEquals(opportunity, attack.opportunityAttack());
        assertNull(attack.itemId());
        assertNull(attack.sequenceKey());
        assertEquals(19, attack.firstD20());
        assertNull(attack.secondD20());
        assertTrue(attack.hit());
        assertEquals(AttackStatus.RESOLVED, attack.status());
        assertEquals(actors.bite(), attack.monsterAttack().action());
        assertEquals(5, attack.monsterAttack().reachFeet());
        assertEquals(5, attack.monsterAttack().hitBonus());
        assertEquals(List.of(), attack.monsterAttack().packTacticsAllies());
        assertEquals(List.of(
                new AttackDamageComponent(DamageType.PIERCING, 1, 8, 3, List.of(piercing), piercing + 3, piercing + 3, List.of()),
                new AttackDamageComponent(DamageType.FIRE, 2, 6, 0, List.of(fireFirst, fireSecond), fireFirst + fireSecond, fireFirst + fireSecond, List.of())),
                attack.monsterAttack().damageComponents());
        assertEquals(piercing + 3 + fireFirst + fireSecond, attack.damageAmount());
    }

    private static void assertForgedBiteRejected(CampaignStorageService service, byte[] complete) {
        var forged = CampaignJson.read(complete);
        var history = forged.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
        var resolution = history.getAsJsonArray("events").asList().getLast().getAsJsonObject().getAsJsonObject("resolution");
        var piercingRoll = resolution.getAsJsonArray("rolls").get(1).getAsJsonObject();
        assertEquals("MONSTER_ATTACK_DAMAGE_PIERCING", piercingRoll.get("purpose").getAsString());
        piercingRoll.addProperty("value", "2");
        var integrity = forged.remove("integrity").getAsJsonObject();
        forged.addProperty("fileKey", UUID.randomUUID().toString());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(forged)));
        forged.add("integrity", integrity);

        var before = service.status();
        var files = service.savedCampaigns();
        var failure = assertThrows(StorageException.class, () -> service.importFile(CampaignJson.canonical(forged)));
        assertEquals("INVALID_FILE", failure.code());
        assertEquals(before, service.status());
        assertEquals(files, service.savedCampaigns());
    }

    private static void assertReplay(Fixture fixture, String request, CampaignStorageService.CampaignView accepted,
            CampaignStorageService.CampaignView replay) {
        assertTrue(replay.lastCommand().replayed());
        assertEquals(accepted.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
        assertEquals(accepted.domain(), replay.domain());
        assertEquals(1, fixture.state().roots().stream().filter(root -> root.operationId().toString().equals(request)).count());
    }

    private static void reopen(Fixture fixture) {
        var snapshot = fixture.access().snapshot();
        fixture.service.open(fixture.end());
        assertEquals(snapshot, fixture.access().snapshot());
    }

    private static List<Integer> draws(Fixture fixture) {
        var event = CampaignSpaceCommands.currentBattle(fixture.state()).events().getLast();
        return event.resolution().rolls().stream().map(Roll::value).toList();
    }

    private static List<AttackAttempt> attempts(Fixture fixture) {
        return fixture.battle().flows().stream().flatMap(flow -> flow.attacks().stream()).toList();
    }

    private static Movement movement(Fixture fixture) {
        return fixture.battle().flows().stream().flatMap(flow -> flow.movements().stream()).findFirst().orElseThrow();
    }

    private static Command resume(Fixture fixture, UUID mover) {
        Flow flow = fixture.battle().activeFlow();
        return Command.continuation(mover, flow.flowKey(), flow.leafFrameKey(), flow.windows().getLast().windowKey());
    }

    private static GrappleRelation relation(Fixture fixture, UUID id) {
        return fixture.battle().meleeControl().grapples().stream().filter(value -> value.grappleKey().equals(id)).findFirst().orElseThrow();
    }

    private static CampaignDomainData.Position position(Fixture fixture, UUID id) {
        return CampaignSpaceCommands.position(fixture.state(), id);
    }

    private static void assertPrefix(Fixture fixture, Actors actors) {
        assertEquals(ROUTE.getFirst(), position(fixture, actors.mover()).grid());
        assertEquals(CARRIED_ROUTE.getFirst(), position(fixture, actors.carried()).grid());
        assertEquals(10, fixture.actor(actors.mover()).movementSpentHalfFeet());
        assertEquals(0, fixture.actor(actors.carried()).movementSpentHalfFeet());
    }

    private static void assertCompleted(Fixture fixture, Actors actors, UUID grapple) {
        assertEquals(ROUTE.getLast(), position(fixture, actors.mover()).grid());
        assertEquals(CARRIED_ROUTE.getLast(), position(fixture, actors.carried()).grid());
        assertEquals(30, fixture.actor(actors.mover()).movementSpentHalfFeet());
        assertEquals(0, fixture.actor(actors.carried()).movementSpentHalfFeet());
        assertTrue(relation(fixture, grapple).active());
        assertEquals(MovementStatus.COMPLETED, movement(fixture).status());
        assertEquals(3, movement(fixture).cursor());
    }

    private record Actors(UUID mover, UUID carried, UUID hound, EntryIdentity bite) { }
}
