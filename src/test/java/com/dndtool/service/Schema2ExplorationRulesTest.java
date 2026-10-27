package com.dndtool.service;

import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CombatRules.Context;
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
    @Test void worldHealthResolvesPublishedVitalSourceInsteadOfAcceptingAnAmount() {
        var prepared=Schema2ExplorationRules.prepareHealth(fixture.roster,exploration(),fixture.a,new HealthInput(HEALTH,DAMAGE,null,"DM confirms this direct environmental damage source."));
        var result=new Schema2CharacterHealthRules().resolve(prepared.prepared(),List.of(),null,null);
        assertEquals(6,result.result().rolledAmount());assertEquals(fixture.roster.get(fixture.a).character().hitPoints()-6,result.state().hitPoints());
        assertThrows(IllegalArgumentException.class,()->Schema2ExplorationRules.prepareHealth(fixture.roster,exploration(),fixture.a,new HealthInput(HEALTH,null,null,"DM requests an unsupported source-less health transition.")));
    }
}
