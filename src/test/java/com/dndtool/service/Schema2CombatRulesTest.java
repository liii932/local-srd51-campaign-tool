package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthState;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.RestState;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2CombatTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CombatRulesTest {
    private final Schema2CombatRules rules=new Schema2CombatRules();
    private final UUID campaign=UUID.randomUUID(),combat=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    private final ProductionSchema2Projection material=material();
    private final Map<UUID,Context> roster=new HashMap<>(Map.of(a,context(material,campaign,a),b,context(material,campaign,b)));
    private Battle battle;
    private Resolution apply(Command command,int...values) {
        UUID operation=UUID.randomUUID();var prepared=rules.preflight(operation,combat,campaign,roster,battle,command);
        AtomicInteger cursor=new AtomicInteger();
        Resolution result=rules.resolve(prepared,sides->{assertTrue(cursor.get()<values.length,"unexpected die d"+sides);return values[cursor.getAndIncrement()];});
        assertEquals(values.length,cursor.get());rules.verify(operation,combat,campaign,roster,battle,command,result);
        for(HealthChange change:result.healthChanges())roster.put(change.characterId(),withState(roster.get(change.characterId()),change.after()));
        battle=result.after();return result;
    }
    private void start(Position first,Position second) {
        apply(Command.start(List.of(new Deployment(a,first,0),new Deployment(b,second,1)),List.of(a,b)),20,10);
        assertEquals(a,battle.activeActor());
    }
    private UUID item(UUID actor) {return roster.get(actor).character().equipment().getFirst().itemId();}
    private Combatant actor(UUID id) {return battle.combatants().stream().filter(x->x.characterId().equals(id)).findFirst().orElseThrow();}
    private Command pending(Kind kind,UUID item) {
        Flow flow=battle.activeFlow();Window window=flow.windows().getLast();Opportunity opportunity=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();
        return Command.reaction(kind,opportunity.actorId(),item,flow.flowKey(),flow.leafFrameKey(),window.windowKey(),opportunity.opportunityKey());
    }
    private Command resume() {Flow f=battle.activeFlow();return Command.continuation(f.movement().actorId(),f.flowKey(),f.leafFrameKey(),f.windows().getLast().windowKey());}

    @Test void initiativeAndRealAttackConsumeOneActionAndOneHealthPacket() {
        start(new Position(0,0),new Position(1,0));
        apply(Command.action(Kind.BEGIN_ATTACK,a));assertFalse(actor(a).actionAvailable());
        var result=apply(Command.attack(a,b,item(a)),20,3,4);
        AttackAttempt attack=battle.flows().getLast().attacks().getFirst();
        assertTrue(attack.critical());assertEquals(List.of(3,4),attack.damageDice());assertEquals(9,attack.damageAmount());
        assertEquals(2,roster.get(b).character().hitPoints());assertEquals(1,result.healthChanges().size());
        assertEquals(1,actor(a).sequence().performed());assertTrue(actor(a).sequence().closed());assertNull(battle.activeFlow());
        assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,Command.attack(a,b,item(a))));
    }
    @Test void naturalOneMissHasNoDamageAndNoHealthMutation() {
        start(new Position(0,0),new Position(1,0));apply(Command.action(Kind.BEGIN_ATTACK,a));
        var result=apply(Command.attack(a,b,item(a)),1);assertTrue(result.healthChanges().isEmpty());
        assertFalse(battle.flows().getLast().attacks().getFirst().hit());assertEquals(11,roster.get(b).character().hitPoints());
    }
    @Test void fullInvalidSuffixRejectsBeforeAnyPrefixOrRandomness() {
        start(new Position(0,0),new Position(0,1));Battle original=battle;
        var bad=Command.move(a,List.of(new Position(1,0),new Position(4,0)));
        assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,bad));
        assertSame(original,battle);assertEquals(0,actor(a).movementSpentHalfFeet());
    }
    @Test void opportunityOccursAtLeavingBoundaryAndPassDoesNotSpendReaction() {
        start(new Position(0,0),new Position(0,1));apply(Command.move(a,List.of(new Position(1,0),new Position(2,0))));
        assertEquals(new Position(1,0),actor(a).position());assertEquals(10,actor(a).movementSpentHalfFeet());
        assertEquals(1,battle.activeFlow().movement().cursor());assertTrue(actor(b).reactionAvailable());
        assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,Command.action(Kind.END_TURN,a)));
        apply(pending(Kind.PASS_REACTION,null));assertTrue(actor(b).reactionAvailable());
        assertEquals(WindowStatus.CLOSED,battle.activeFlow().windows().getLast().status());
        apply(resume());assertEquals(new Position(2,0),actor(a).position());assertEquals(20,actor(a).movementSpentHalfFeet());assertNull(battle.activeFlow());
    }
    @Test void opportunityDamageStopsOriginalSuffixWithoutReturningToOrigin() {
        start(new Position(0,0),new Position(0,1));apply(Command.move(a,List.of(new Position(1,0),new Position(2,0))));
        apply(pending(Kind.DECLARE_OPPORTUNITY_ATTACK,item(b)),20,5,5);
        assertEquals(0,roster.get(a).character().hitPoints());assertFalse(actor(b).reactionAvailable());assertEquals(new Position(1,0),actor(a).position());
        apply(resume());assertNull(battle.activeFlow());assertEquals(new Position(1,0),actor(a).position());assertEquals(10,actor(a).movementSpentHalfFeet());
        assertEquals(MovementStatus.STOPPED,battle.flows().getLast().movement().status());assertEquals(1,battle.flows().getLast().attacks().size());
        assertTrue(actor(a).prone());assertEquals("GROUND",roster.get(a).character().equipment().getFirst().location());
        assertEquals(combat,roster.get(a).character().equipment().getFirst().groundCombatId());assertEquals(1,roster.get(a).character().equipment().getFirst().groundX());
    }
    @Test void disengageSuppressesEveryBoundaryAndDashExtendsExistingPool() {
        start(new Position(0,0),new Position(0,1));apply(Command.action(Kind.DISENGAGE,a));
        apply(Command.move(a,List.of(new Position(1,0),new Position(2,0))));assertNull(battle.activeFlow());assertTrue(actor(b).reactionAvailable());
        apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.END_TURN,b));assertEquals(a,battle.activeActor());
        apply(Command.action(Kind.DASH,a));assertEquals(30,actor(a).movementBonus());
        apply(Command.move(a,List.of(new Position(3,0),new Position(4,0),new Position(5,0),new Position(6,0),new Position(7,0),new Position(8,0),new Position(9,0))));
        assertEquals(70,actor(a).movementSpentHalfFeet());
    }
    @Test void zeroHpTargetGrantsAdvantageAndCriticalCountsTwoFailures() {
        Context target=roster.get(b);roster.put(b,withState(target,Schema2CombatEquipmentFacts.drop(withHealth(target.character(),0,new HealthState(20,0,0,false,false,null)),combat,new Position(1,0))));
        start(new Position(0,0),new Position(1,0));apply(Command.action(Kind.BEGIN_ATTACK,a));
        var result=apply(Command.attack(a,b,item(a)),2,12,1,1);
        AttackAttempt attack=battle.flows().getLast().attacks().getFirst();assertTrue(attack.advantage());assertTrue(attack.critical());
        assertEquals(2,roster.get(b).character().health().deathFailures());assertEquals(16,roster.get(b).character().health().temporaryHitPoints());
        assertEquals(4,result.healthChanges().getFirst().damageAmount());
    }
    @Test void automaticDeathSaveHappensOnlyOnTheActualIncomingTurn() {
        Context target=roster.get(b);roster.put(b,withState(target,Schema2CombatEquipmentFacts.drop(withHealth(target.character(),0,new HealthState(0,2,0,false,false,null)),combat,new Position(2,0))));
        start(new Position(0,0),new Position(2,0));
        var result=apply(Command.action(Kind.END_TURN,a),10,3);assertEquals(b,battle.activeActor());
        assertTrue(result.healthChanges().getFirst().deathSave());assertTrue(roster.get(b).character().health().stable());assertEquals(180L,roster.get(b).character().health().stableRecoveryMinute());
        apply(Command.action(Kind.END_TURN,b));apply(Command.action(Kind.END_TURN,a));assertTrue(roster.get(b).character().health().stable());
    }
    @Test void dodgeUsesActualSecondDieAndExpiresAtTheActorsNextTurnStart() {
        start(new Position(0,0),new Position(1,0));apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.DODGE,b));apply(Command.action(Kind.END_TURN,b));
        apply(Command.action(Kind.BEGIN_ATTACK,a));apply(Command.attack(a,b,item(a)),20,1);
        var attempt=battle.flows().getLast().attacks().getFirst();assertTrue(attempt.disadvantage());assertEquals(1,attempt.selectedD20());assertFalse(attempt.hit());
        apply(Command.action(Kind.END_TURN,a));assertFalse(actor(b).dodging());
    }
    @Test void savedDiceAndDomainOutcomeAreVerifiedWithoutAnyRng() {
        UUID operation=UUID.randomUUID();Command command=Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(2,0),1)),List.of(a,b));
        var prepared=rules.preflight(operation,combat,campaign,roster,null,command);var saved=rules.resolve(prepared,sides->10);
        rules.verify(operation,combat,campaign,roster,null,command,saved);
        var wrong=new Resolution(saved.after(),saved.healthChanges(),List.of(new Roll("INITIATIVE",a,20,1)),saved.facts());
        assertThrows(IllegalArgumentException.class,()->rules.verify(operation,combat,campaign,roster,null,command,wrong));
    }
    @Test void naturalTwentyWakesButDoesNotStandAndHalfFootBudgetKeepsTwelveAndAHalfFeet() {
        var slower=material(25,5);roster.put(a,context(slower,campaign,a));Context target=context(slower,campaign,b);
        roster.put(b,withState(target,Schema2CombatEquipmentFacts.drop(withHealth(target.character(),0,HealthState.INITIAL),combat,new Position(2,0))));
        start(new Position(0,0),new Position(2,0));apply(Command.action(Kind.END_TURN,a),20);
        assertEquals(1,roster.get(b).character().hitPoints());assertTrue(actor(b).prone());
        assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,Command.move(b,List.of(new Position(3,0)))));
        apply(Command.action(Kind.STAND_UP,b));assertFalse(actor(b).prone());assertEquals(25,actor(b).movementSpentHalfFeet());
        apply(Command.move(b,List.of(new Position(3,0))));assertEquals(35,actor(b).movementSpentHalfFeet());
    }
    @Test void proneAtTenFeetCancelsUnconsciousAdvantageAndDoesNotGrantAutomaticCritical() {
        var longer=material(30,10);roster.put(a,context(longer,campaign,a));Context target=context(longer,campaign,b);
        roster.put(b,withState(target,Schema2CombatEquipmentFacts.drop(withHealth(target.character(),0,new HealthState(20,0,0,false,false,null)),combat,new Position(2,0))));
        start(new Position(0,0),new Position(2,0));apply(Command.action(Kind.BEGIN_ATTACK,a));apply(Command.attack(a,b,item(a)),12,3);
        var attack=battle.flows().getLast().attacks().getFirst();assertTrue(attack.advantage());assertTrue(attack.disadvantage());assertNull(attack.secondD20());assertFalse(attack.critical());
        assertEquals(1,roster.get(b).character().health().deathFailures());
    }
    @Test void multipleOpportunitiesUseFrozenInitiativeOrderAndOnlyCreateTheCurrentSelection() {
        UUID c=UUID.randomUUID();roster.put(c,context(material,campaign,c));
        apply(Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(0,1),1),new Deployment(c,new Position(0,-1),1)),List.of(a,b,c)),20,15,10);
        apply(Command.move(a,List.of(new Position(1,0),new Position(2,0))));Flow flow=battle.activeFlow();Window window=flow.windows().getLast();
        assertEquals(List.of(b,c),window.opportunities().stream().map(Opportunity::actorId).toList());assertNull(window.opportunities().get(1).selectionFrameKey());
        var late=Command.reaction(Kind.PASS_REACTION,c,null,flow.flowKey(),flow.leafFrameKey(),window.windowKey(),window.opportunities().get(1).opportunityKey());
        assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,late));
        apply(pending(Kind.PASS_REACTION,null));apply(pending(Kind.PASS_REACTION,null));apply(resume());assertNull(battle.activeFlow());
        assertEquals(3,battle.flows().getLast().frames().size());
    }
    @Test void reactionUsedAfterOwnTurnIsNotRestoredByTheRoundBoundary() {
        UUID c=UUID.randomUUID();roster.put(c,context(material,campaign,c));
        apply(Command.start(List.of(new Deployment(a,new Position(10,10),0),new Deployment(b,new Position(0,1),0),new Deployment(c,new Position(0,0),1)),List.of(a,b,c)),20,15,10);
        apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.END_TURN,b));apply(Command.move(c,List.of(new Position(1,0),new Position(2,0))));
        apply(pending(Kind.DECLARE_OPPORTUNITY_ATTACK,item(b)),1);apply(resume());assertFalse(actor(b).reactionAvailable());
        apply(Command.action(Kind.END_TURN,c));assertEquals(a,battle.activeActor());assertFalse(actor(b).reactionAvailable());
        for(Context context:roster.values())assertEquals(6,context.character().rest().second());
        apply(Command.action(Kind.END_TURN,a));assertTrue(actor(b).reactionAvailable());
    }
    @Test void explicitAdvanceRecordsLostEligibilityAfterEarlierOpportunityIncapacitatesMover() {
        UUID c=UUID.randomUUID();roster.put(c,context(material,campaign,c));
        apply(Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(0,1),1),new Deployment(c,new Position(0,-1),1)),List.of(a,b,c)),20,15,10);
        apply(Command.move(a,List.of(new Position(1,0),new Position(2,0))));
        assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,pending(Kind.ADVANCE_WINDOW,null)));
        apply(pending(Kind.DECLARE_OPPORTUNITY_ATTACK,item(b)),20,5,5);assertEquals(0,roster.get(a).character().hitPoints());
        apply(pending(Kind.ADVANCE_WINDOW,null));assertTrue(actor(c).reactionAvailable());
        assertEquals(OpportunityStatus.INELIGIBLE,battle.activeFlow().windows().getLast().opportunities().get(1).status());
        apply(resume());assertEquals(MovementStatus.STOPPED,battle.flows().getLast().movement().status());assertEquals(new Position(1,0),actor(a).position());
    }
    @Test void mandatorySuffixBoundDecreasesForEachDecisionAndClosedWindowReturn() {
        start(new Position(0,0),new Position(0,1));Command move=Command.move(a,List.of(new Position(1,0),new Position(2,0)));
        int opening=rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,move).requiredSuffixEvents();
        apply(move);Command pass=pending(Kind.PASS_REACTION,null);
        int decision=rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,pass).requiredSuffixEvents();assertTrue(decision<opening);
        apply(pass);int returning=rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,resume()).requiredSuffixEvents();
        assertEquals(decision-1,returning);apply(resume());assertNull(battle.activeFlow());
    }
    @Test void missingHeldWeaponConsumerRejectsWholeCombatBeforeInitiative() {
        var files=Schema2CombatTestFixture.packages();var rows=com.dndtool.module.composition.Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
        for(int i=rows.size()-1;i>=0;i--)if(rows.get(i).getAsJsonObject().getAsJsonObject("identity").get("rule_key").getAsString().equals("weapon_attack.ordinary"))rows.remove(i);
        files.getFirst().put("rules.json",rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var incomplete=com.dndtool.module.composition.ProductionSchema2Projection.compose(com.dndtool.module.composition.Schema2GrantFixtures.artifacts(files));
        roster.put(a,context(incomplete,campaign,a));roster.put(b,context(incomplete,campaign,b));
        var command=Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(1,0),1)),List.of(a,b));
        var failure=assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,null,command));
        assertEquals("ACTION_FLOW_RULE_UNSUPPORTED",failure.getMessage());
    }
    @Test void endingCombatMidRoundEntersExplorationWithoutStartingAnotherTurn() {
        start(new Position(0,0),new Position(2,0));
        var result=apply(Command.action(Kind.END_COMBAT,a));assertEquals(Status.EXPLORATION,battle.status());
        assertEquals(1,battle.turnNumber());assertEquals(a,battle.activeActor());assertTrue(result.rolls().isEmpty());
        for(Context context:roster.values())assertEquals(6,context.character().rest().second());
        apply(Command.action(Kind.CLOSE_SCENE,a));assertEquals(Status.ENDED,battle.status());
        for(Context context:roster.values())assertEquals(6,context.character().rest().second());
    }
    @Test void endingTheFinalTurnClosesItsSequenceAndSettlesSixSecondsWithoutStartingAnotherTurn() {
        start(new Position(0,0),new Position(0,1));apply(Command.move(a,List.of(new Position(1,0),new Position(2,0))));
        apply(pending(Kind.DECLARE_OPPORTUNITY_ATTACK,item(b)),20,5,5);apply(resume());apply(Command.action(Kind.END_TURN,a));
        assertEquals(0,roster.get(a).character().hitPoints());apply(Command.action(Kind.BEGIN_ATTACK,b));
        var prepared=rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,Command.action(Kind.END_COMBAT,b));
        assertEquals(Set.of(a,b),prepared.possibleHealthTargets());assertEquals(0,prepared.requiredSuffixEvents());
        var ended=apply(Command.action(Kind.END_COMBAT,b));assertEquals(Status.EXPLORATION,battle.status());
        assertEquals(1,battle.round());assertEquals(2,battle.turnNumber());assertEquals(b,battle.activeActor());
        assertTrue(actor(b).sequence().closed());assertEquals(1,actor(b).sequence().forfeited());
        assertTrue(ended.rolls().isEmpty());assertEquals(2,ended.healthChanges().size());
        assertTrue(ended.healthChanges().stream().allMatch(change->change.timeAdvance() && change.seconds()==6 && !change.deathSave()));
        for(Context context:roster.values())assertEquals(6,context.character().rest().second());
        assertEquals(0,roster.get(a).character().health().deathFailures());
        var closed=apply(Command.action(Kind.CLOSE_SCENE,b));assertEquals(Status.ENDED,battle.status());assertTrue(closed.healthChanges().isEmpty());assertTrue(closed.rolls().isEmpty());
    }
    @Test void startReservesAllRuntimeRoundTimeAndMaximumStableRecoveryOnlyAtAdmission() {
        assertEquals(253,Schema2CombatRules.START_TIME_HEADROOM_MINUTES);
        long boundary=Schema2CharacterRestRules.MAX_MINUTE-Schema2CombatRules.START_TIME_HEADROOM_MINUTES;
        setClock(boundary+1,59);
        var command=Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(2,0),1)),List.of(a,b));
        var failure=assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,null,command));
        assertEquals("S2_COMBAT_TIME_CAPACITY",failure.getMessage());assertNull(battle);
        setClock(boundary,59);start(new Position(0,0),new Position(2,0));
        apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.END_TURN,b));
        for(Context context:roster.values()) {assertEquals(boundary+1,context.character().rest().minute());assertEquals(5,context.character().rest().second());}
        apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.END_COMBAT,b));
        assertEquals(Status.EXPLORATION,battle.status());
        apply(Command.action(Kind.CLOSE_SCENE,b));assertEquals(Status.ENDED,battle.status());
        for(Context context:roster.values())assertEquals(11,context.character().rest().second());
    }
    private void setClock(long minute,int second) {
        for(var entry:Map.copyOf(roster).entrySet()) {
            State s=entry.getValue().character();State next=new State(s.identity(),s.campaignId(),s.characterId(),s.preparationOperationId(),s.characterClass(),s.abilities(),s.level(),s.proficiencyBonus(),s.hitPoints(),s.maximumHitPoints(),s.classes(),s.hitDicePools(),s.subclasses(),s.features(),s.feats(),s.proficiencies(),s.version(),s.resources(),new RestState(minute,second,null,null,null),s.equipment(),s.health());
            roster.put(entry.getKey(),withState(entry.getValue(),next));
        }
    }
}
