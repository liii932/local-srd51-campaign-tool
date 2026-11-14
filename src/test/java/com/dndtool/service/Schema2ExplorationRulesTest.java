package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2CombatRules.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2CombatState.*;
import static com.dndtool.service.Schema2CombatTestFixture.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ExplorationRulesTest {
    final Schema2OngoingEffectRulesTest fixture=new Schema2OngoingEffectRulesTest();
    Battle exploration() {
        Battle b=fixture.battle;
        return new Battle(b.combatId(),b.campaignId(),b.combatRule(),Status.valueOf("EXPLORATION"),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),
                b.combatants(),b.initiative(),b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.environmentZones(),b.suppressionCauses());
    }
    @Test void oneAuthoritativeIntervalAdvancesEveryExistingClockOnceAndPreservesOriginalSources() {
        var plan=Schema2ExplorationRules.advanceTime(fixture.roster,60,UUID.randomUUID());
        assertEquals(2,plan.changes().size());
        for(Context c:plan.after().values()) assertEquals(60,Schema2SpellRules.second(c.character()));
        for(Context c:fixture.roster.values()) assertEquals(0,Schema2SpellRules.second(c.character()));
        assertTrue(plan.changes().stream().allMatch(c->c.timeAdvance()&&c.seconds()==60&&c.after().version()==c.before().version()+1));
        Battle ended=Schema2ExplorationRules.expire(exploration(),60,new ArrayList<>());
        assertEquals(60,ended.ongoingEffects().getFirst().expiresAtSecond());assertEquals(EffectStatus.ENDED,ended.ongoingEffects().getFirst().status());
    }
    @Test void unstableCharactersCannotSkipTheirNextRealTurnByAdvancingExploration() {
        var roster=new HashMap<>(fixture.roster);Context c=roster.get(fixture.a);
        roster.put(fixture.a,withState(c,withHealth(c.character(),0,HealthState.INITIAL)));
        assertEquals("S2_EXPLORATION_UNSTABLE_CHARACTER",assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.advanceTime(roster,6,UUID.randomUUID())).getMessage());
        assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.advanceTime(fixture.roster,0,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.advanceTime(fixture.roster,61,UUID.randomUUID()));
    }
    @Test void walkingRequiresContinuousUnoccupiedCellsWithinActualSpeedAndSixSeconds() {
        Battle b=exploration();
        assertDoesNotThrow(()->Schema2ExplorationRules.prepareMove(fixture.roster,b,fixture.a,List.of(new Position(1,0),new Position(2,0))));
        assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.prepareMove(fixture.roster,b,fixture.a,List.of(new Position(2,0))));
        var tooLong=new ArrayList<Position>();for(int x=1;x<=7;x++)tooLong.add(new Position(x,0));
        assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.prepareMove(fixture.roster,b,fixture.a,tooLong));
        assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.prepareMove(fixture.roster,fixture.battle,fixture.a,List.of(new Position(1,0))));
    }
    @Test void crawlingCannotInventAMissingFrozenMovementRule() {
        var files = packages();
        var definitions = Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
        for (int i = definitions.size() - 1; i >= 0; i--)
            if (definitions.get(i).getAsJsonObject().getAsJsonObject("identity").get("rule_type").getAsString().equals("character.movement"))
                definitions.remove(i);
        files.getFirst().put("rules.json", definitions.toString().getBytes(StandardCharsets.UTF_8));
        var missingMovement = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
        Battle original = exploration();
        var actors = original.combatants().stream().map(actor -> new Combatant(actor.characterId(), actor.position(),
                actor.side(), actor.actionAvailable(), actor.reactionAvailable(), actor.movementSpentHalfFeet(),
                actor.movementBonus(), actor.disengaged(), actor.dodging(), actor.sequence(), true)).toList();
        Battle prone = Schema2ActionFlowRules.rebuild(original, actors, original.flows(), original.readyPreparations(),
                original.triggerFacts(), original.shields(), original.concentrationHolders(), original.version());
        var context = context(missingMovement, fixture.campaign, fixture.a);
        assertEquals("ACTION_FLOW_RULE_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> Schema2ExplorationRules.prepareMove(Map.of(fixture.a, context), prone, fixture.a,
                        List.of(new Position(1, 0)))).getMessage());
        assertEquals(0, context.sceneSecond());
        assertEquals(new Position(0, 0), prone.combatants().getFirst().position());
    }
    @Test void worldHealthResolvesPublishedVitalSourceInsteadOfAcceptingAnAmount() {
        var prepared=Schema2ExplorationRules.prepareHealth(fixture.roster,exploration(),fixture.a,new HealthInput(HEALTH,DAMAGE,null,"DM confirms this direct environmental damage source."));
        var result=new Schema2CharacterHealthRules().resolve(prepared.prepared(),List.of(),null,null);
        assertEquals(6,result.result().rolledAmount());assertEquals(fixture.roster.get(fixture.a).character().hitPoints()-6,result.state().hitPoints());
        assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.prepareHealth(fixture.roster,exploration(),fixture.a,new HealthInput(HEALTH,null,null,"DM requests an unsupported source-less health transition.")));
    }
}
