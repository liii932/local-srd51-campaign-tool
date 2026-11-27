package com.dndtool.service.storage;

import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.CampaignExtraAttackIntegrationTest.Fixture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CampaignMeleeControlIntegrationTest {
    @TempDir Path temporary;

    @Test void standardChoiceAndOnePointWinSurviveReopen() throws Exception {
        exerciseCombination(BundledCampaignRules.STANDARD, 1);
    }

    @Test void houseChoiceAndOnePointStalemateSurviveReopen() throws Exception {
        exerciseCombination(BundledCampaignRules.CHECK_VARIANT, 2);
    }

    private void exerciseCombination(String combination, int threshold) throws Exception {
        try (var game = new Fixture(temporary.resolve(combination))) {
            CampaignRuleFixtures.create(game.service, "近战控制", combination);
            UUID fighter = game.character("抓者", "PC");
            UUID target = game.character("防守者", "PC");
            UUID defender = game.player(target, fighter);
            game.advance(fighter, 5);
            game.startBattle("城门", List.of(new Deployment(fighter, new Position(2, 2), 0),
                    new Deployment(target, new Position(3, 2), 1)), 20, 1);
            game.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            game.combat(replacement(game, Kind.GRAPPLE, fighter, target));
            assertEquals(0, game.actor(fighter).sequence().performed());
            reopen(game);
            var choices = PlayerCandidateFactory.candidates(game.access(), defender).stream()
                    .filter(c -> c.input().getAsJsonObject("command").get("kind").getAsString().equals("CHOOSE_MELEE_DEFENSE")).toList();
            assertEquals(2, choices.size());
            var prompt = PlayerCampaignProjection.project(game.access(), defender).getAsJsonObject("pending");
            assertTrue(prompt.get("own").getAsBoolean());
            assertTrue(prompt.get("description").getAsString().contains("防守"));
            var before = game.access();
            var command = game.combatInput(defense(game, target, ContestSkill.ACROBATICS));
            var request = id();
            game.dice.addAll(List.of(10, 14));
            var player = CampaignParticipationData.Actor.player(defender, target);
            game.submit(request, before, command, player);
            assertTrue(game.dice.isEmpty());
            var resolved = game.access();
            assertTrue(game.submit(request, before, command, player).lastCommand().replayed());
            assertEquals(resolved, game.access());
            var outcome = game.battle().meleeControl().contests().getLast();
            assertEquals(10, outcome.initiator().firstD20());
            assertEquals(3, outcome.initiator().abilityModifier());
            assertEquals(3, outcome.initiator().proficiencyBonus());
            assertEquals(16, outcome.initiator().total());
            assertEquals(14, outcome.responder().firstD20());
            assertEquals(1, outcome.responder().abilityModifier());
            assertEquals(15, outcome.responder().total());
            assertEquals(threshold, outcome.minimumWinningMargin());
            assertEquals(threshold == 1 ? "INITIATOR_WINS" : "UNCHANGED", outcome.outcome().name());
            assertEquals(threshold == 1 ? 1 : 0, game.battle().meleeControl().grapples().size());
            game.assertSequence(fighter, 2, 1, 0, false);
            reopen(game);
            assertEquals(outcome, game.battle().meleeControl().contests().getLast());
            game.combat(replacement(game, Kind.SHOVE_PRONE, fighter, target));
            game.combat(defense(game, target, ContestSkill.ATHLETICS), 18, 2);
            assertTrue(game.actor(target).prone());
            game.assertSequence(fighter, 2, 2, 0, true);
            if (threshold == 1) {
                var grapple = game.battle().meleeControl().grapples().getFirst();
                assertEquals(fighter, grapple.grapplerId());
                assertEquals(target, grapple.targetId());
                assertEquals(HandSlot.MAIN_HAND, grapple.hand());
                assertTrue(grapple.active());
                game.combat(Command.action(Kind.END_TURN, fighter));
                game.reject(Command.action(Kind.STAND_UP, target), "S2_COMBAT_CANNOT_STAND");
                var escape = Command.melee(Kind.ESCAPE_GRAPPLE, target, null, null, null,
                        new MeleeInput(null, null, grapple.grappleKey(), null, ContestSkill.ATHLETICS, null));
                game.combat(escape, 12, 10);
                assertFalse(game.actor(target).actionAvailable());
                assertTrue(game.battle().meleeControl().grapples().getFirst().active());
                game.combat(Command.action(Kind.END_TURN, target));
                game.combat(Command.action(Kind.END_TURN, fighter));
                game.combat(escape, 18, 4);
                assertFalse(game.battle().meleeControl().grapples().getFirst().active());
                game.combat(Command.action(Kind.STAND_UP, target));
                assertFalse(game.actor(target).prone());
            }
        }
    }

    static Command replacement(Fixture game, Kind kind, UUID fighter, UUID target) {
        var sequence = game.actor(fighter).sequence();
        return Command.melee(kind, fighter, target, null, null, new MeleeInput(sequence.sequenceKey(),
                sequence.performed(), null, kind == Kind.GRAPPLE ? HandSlot.MAIN_HAND : null, null, null));
    }

    static Command defense(Fixture game, UUID target, ContestSkill skill) {
        var flow = game.battle().activeFlow();
        return Command.melee(Kind.CHOOSE_MELEE_DEFENSE, target, null, flow.flowKey(), flow.leafFrameKey(),
                new MeleeInput(null, null, null, null, skill, null));
    }

    private static void reopen(Fixture game) throws Exception {
        var before = game.access();
        var manual = game.service.save(id(), before.epoch(), before.revision()).fileKey();
        var finalFile = game.end();
        for (String key : List.of(manual, finalFile)) {
            var decoded = new CampaignCodec().decode(Files.readAllBytes(game.service.savedFile(key)));
            assertEquals(before.snapshot(), decoded.snapshot());
        }
        game.service.open(finalFile);
        assertNotEquals(before.epoch(), game.access().epoch());
        assertEquals(before.snapshot(), game.access().snapshot());
    }

    private static String id() { return UUID.randomUUID().toString(); }
}
