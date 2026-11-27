package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.service.Schema2CharacterCheckRules.ContestOutcome;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.CampaignDomainCodec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2CombatTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2MeleeControlRulesTest {
    @Test void pendingChoiceKeepsOrdinalAndUsesFrozenMarginAfterReadback() {
        for (int margin : List.of(1, 2)) {
            var game = new Fixture(margin, new Position(1, 0));
            game.apply(Command.action(Kind.BEGIN_ATTACK, game.source));
            game.reject(game.replacement(Kind.GRAPPLE, HandSlot.MAIN_HAND, null), "S2_COMBAT_HAND_UNAVAILABLE");

            var opening = game.apply(game.replacement(Kind.GRAPPLE, HandSlot.OFF_HAND, null));
            var pending = game.battle.meleeControl().contests().getFirst();
            assertEquals(0, pending.attackOrdinal());
            assertEquals(margin, pending.minimumWinningMargin());
            assertNull(pending.selectedSkill());
            assertNull(pending.initiator());
            assertEquals(0, game.actor(game.source).sequence().performed());
            assertFalse(game.actor(game.source).actionAvailable());
            assertEquals(FrameKind.DOMAIN_CHOICE, opening.after().activeFlow().frames().getLast().kind());

            game.battle = CampaignDomainCodec.decode(CampaignDomainCodec.encode(game.battle), Battle.class);
            assertEquals(opening.after(), game.battle);
            game.reject(Command.melee(Kind.CHOOSE_MELEE_DEFENSE, game.target, null, game.battle.activeFlow().flowKey(),
                    UUID.randomUUID(), new MeleeInput(null, null, null, null, ContestSkill.ATHLETICS, null)),
                    "ACTION_FLOW_TARGET_MISMATCH");
            game.reject(game.choice(game.source, ContestSkill.ACROBATICS), "ACTION_CHOICE_OPTION_INVALID");
            game.reject(Command.attack(game.source, game.target, game.weapon(game.source)), "ACTION_FLOW_BUSY");
            var choice = game.choice(game.target, ContestSkill.ACROBATICS);
            var finalResult = game.apply(choice, 12, 14);
            var result = game.battle.meleeControl().contests().getFirst();
            assertEquals(16, result.initiator().total());
            assertEquals(15, result.responder().total());
            assertEquals(margin == 1 ? ContestOutcome.INITIATOR_WINS : ContestOutcome.UNCHANGED, result.outcome());
            assertEquals(margin == 1, Schema2MeleeControlRules.grappled(game.battle, game.target));
            assertTrue(finalResult.healthChanges().isEmpty());
            assertTrue(finalResult.after().flows().getLast().attacks().isEmpty());
            assertEquals(1, game.actor(game.source).sequence().performed());
            assertTrue(game.actor(game.source).sequence().closed());
            assertNull(game.battle.activeFlow());
            game.reject(choice, "ACTION_FLOW_TARGET_MISMATCH");
        }
    }

    @Test void outOfReachAndWrongPushDestinationRejectBeforeChoice() {
        var far = new Fixture(1, new Position(2, 0));
        far.apply(Command.action(Kind.BEGIN_ATTACK, far.source));
        far.reject(far.replacement(Kind.GRAPPLE, HandSlot.OFF_HAND, null), "S2_COMBAT_OUT_OF_REACH");
        assertTrue(far.battle.meleeControl().contests().isEmpty());

        var game = new Fixture(1, new Position(1, 0));
        game.apply(Command.action(Kind.BEGIN_ATTACK, game.source));
        game.reject(game.replacement(Kind.SHOVE_PUSH, null, new Position(2, 1)), "S2_COMBAT_SHOVE_DESTINATION");
        game.apply(game.replacement(Kind.SHOVE_PUSH, null, new Position(2, 0)));
        game.apply(game.choice(game.target, ContestSkill.ATHLETICS), 18, 3);
        assertEquals(new Position(2, 0), game.actor(game.target).position());
        assertEquals(0, game.actor(game.target).movementSpentHalfFeet());
        assertTrue(game.actor(game.source).reactionAvailable());
        assertTrue(game.battle.flows().getLast().windows().isEmpty());
        assertNull(game.battle.activeFlow());
    }

    @Test void releaseIsFreeOnAnotherTurnAndIncapacitationEndsItsOwnSource() {
        for (boolean release : List.of(false, true)) {
            var game = new Fixture(1, new Position(1, 0));
            game.apply(Command.action(Kind.BEGIN_ATTACK, game.source));
            game.apply(game.replacement(Kind.GRAPPLE, HandSlot.OFF_HAND, null));
            game.apply(game.choice(game.target, ContestSkill.ATHLETICS), 18, 3);
            var relation = game.battle.meleeControl().grapples().getFirst();
            game.reject(Command.action(Kind.END_COMBAT, game.source), "S2_COMBAT_GRAPPLE_PENDING");
            game.apply(Command.action(Kind.END_TURN, game.source));
            if (release) {
                var before = game.battle.combatants();
                game.apply(Command.melee(Kind.RELEASE_GRAPPLE, game.source, null, null, null,
                        new MeleeInput(null, null, relation.grappleKey(), null, null, null)));
                assertEquals(before, game.battle.combatants());
            } else {
                game.apply(Command.action(Kind.BEGIN_ATTACK, game.target));
                game.apply(Command.attack(game.target, game.source, game.weapon(game.target)), 20, 8, 8);
            }
            assertFalse(Schema2MeleeControlRules.grappled(game.battle, game.target));
            assertEquals(release ? GrappleEndReason.RELEASED : GrappleEndReason.GRAPPLER_INCAPACITATED,
                    game.battle.meleeControl().grapples().getFirst().endReason());
        }
    }

    @Test void shovingAnAlreadyHeldTargetBeyondReachEndsThatRelationAndFreesItsHand() {
        var game = new Fixture(1, new Position(1, 0));
        game.apply(Command.action(Kind.BEGIN_ATTACK, game.source));
        Command grab = game.replacement(Kind.GRAPPLE, HandSlot.OFF_HAND, null);
        game.apply(grab);
        game.apply(game.choice(game.target, ContestSkill.ACROBATICS), 18, 3);
        var relation = game.battle.meleeControl().grapples().getFirst();
        var source = game.roster.get(game.source).character();
        assertFalse(Schema2MeleeControlRules.handFree(game.battle, source, HandSlot.OFF_HAND));
        game.apply(Command.action(Kind.END_TURN, game.source));
        game.apply(Command.action(Kind.END_TURN, game.target));
        game.apply(Command.action(Kind.BEGIN_ATTACK, game.source));
        game.reject(grab, "ACTION_FLOW_TARGET_MISMATCH");

        game.apply(game.replacement(Kind.SHOVE_PUSH, null, new Position(2, 0)));
        var result = game.apply(game.choice(game.target, ContestSkill.ATHLETICS), 18, 3);
        var ended = game.battle.meleeControl().grapples().getFirst();
        assertEquals(relation.grappleKey(), ended.grappleKey());
        assertEquals(GrappleEndReason.OUT_OF_REACH, ended.endReason());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.kind().equals("GRAPPLE_ENDED")
                && fact.referenceKey().equals(relation.grappleKey()) && fact.reason().equals("OUT_OF_REACH")));
        assertEquals(new Position(2, 0), game.actor(game.target).position());
        assertTrue(Schema2MeleeControlRules.handFree(game.battle, source, HandSlot.OFF_HAND));
        assertFalse(Schema2MeleeControlRules.handFree(game.battle, source, HandSlot.MAIN_HAND));
    }

    private static final class Fixture {
        private final UUID campaign = UUID.randomUUID(), scene = UUID.randomUUID();
        private final UUID source = UUID.randomUUID(), target = UUID.randomUUID();
        private final Schema2CombatRules rules = new Schema2CombatRules();
        private final Map<UUID, Context> roster;
        private Battle battle;

        Fixture(int margin, Position destination) {
            var files = packages();
            var rows = Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
            for (var element : rows) {
                var row = element.getAsJsonObject();
                String key = row.getAsJsonObject("identity").get("rule_key").getAsString();
                if (key.equals("check.srd2014")) {
                    row.addProperty("contest_minimum_winning_margin", margin);
                    row.addProperty("allow_alternate_skill_ability", margin == 2);
                }
                if (margin == 2 && key.equals("proficiency.skill.athletics")) row.addProperty("ability_key", "ability.wisdom");
            }
            files.getFirst().put("rules.json", rows.toString().getBytes(StandardCharsets.UTF_8));
            var material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
            roster = new HashMap<>(Map.of(source, context(material, campaign, source), target, context(material, campaign, target)));
            apply(Command.start(List.of(new Deployment(source, new Position(0, 0), 0), new Deployment(target, destination, 1)),
                    List.of(source, target)), 20, 1);
        }

        Combatant actor(UUID id) { return Schema2CombatRules.combatant(battle, id); }

        UUID weapon(UUID id) { return roster.get(id).character().equipment().getFirst().itemId(); }

        Command replacement(Kind kind, HandSlot hand, Position destination) {
            var sequence = actor(source).sequence();
            return Command.melee(kind, source, target, null, null,
                    new MeleeInput(sequence.sequenceKey(), sequence.performed(), null, hand, null, destination));
        }

        Command choice(UUID actor, ContestSkill skill) {
            return Command.melee(Kind.CHOOSE_MELEE_DEFENSE, actor, null, battle.activeFlow().flowKey(),
                    battle.activeFlow().leafFrameKey(), new MeleeInput(null, null, null, null, skill, null));
        }

        Resolution apply(Command command, int... rolls) {
            UUID operation = UUID.randomUUID();
            var prepared = rules.preflight(operation, scene, campaign, roster, battle, command);
            var cursor = new AtomicInteger();
            var result = rules.resolve(prepared, sides -> {
                int index = cursor.getAndIncrement();
                assertTrue(index < rolls.length, "Unexpected d" + sides);
                return rolls[index];
            });
            assertEquals(rolls.length, cursor.get());
            rules.verify(operation, scene, campaign, roster, battle, command, result);
            for (HealthChange change : result.healthChanges())
                roster.put(change.characterId(), withState(roster.get(change.characterId()), change.after()));
            battle = result.after();
            return result;
        }

        void reject(Command command, String error) {
            assertEquals(error, assertThrows(IllegalArgumentException.class,
                    () -> rules.preflight(UUID.randomUUID(), scene, campaign, roster, battle, command)).getMessage());
        }
    }
}
