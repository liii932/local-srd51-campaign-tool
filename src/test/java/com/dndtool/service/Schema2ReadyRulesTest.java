package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthState;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.dndtool.service.Schema2CombatTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ReadyRulesTest {
    final Schema2CombatRules rules=new Schema2CombatRules();
    final UUID campaign=UUID.randomUUID(),combat=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    final ProductionSchema2Projection material=material();
    final Map<UUID,Context> roster=new HashMap<>(Map.of(a,context(material,campaign,a),b,context(material,campaign,b)));
    Battle battle;
    Resolution apply(Command command,int...rolls) {
        UUID op=UUID.randomUUID();var before=battle;var prepared=rules.preflight(op,combat,campaign,roster,before,command);AtomicInteger i=new AtomicInteger();
        var result=rules.resolve(prepared,sides->{assertTrue(i.get()<rolls.length,"unexpected d"+sides);return rolls[i.getAndIncrement()];});assertEquals(rolls.length,i.get());
        rules.verify(op,combat,campaign,roster,before,command,result);for(HealthChange h:result.healthChanges())roster.put(h.characterId(),withState(roster.get(h.characterId()),h.after()));battle=result.after();return result;
    }
    void start(Position first,Position second) {apply(Command.start(List.of(new Deployment(a,first,0),new Deployment(b,second,1)),List.of(a,b)),20,10);}
    UUID item(UUID id) {return roster.get(id).character().equipment().getFirst().itemId();}
    Combatant actor(UUID id) {return battle.combatants().stream().filter(v->v.characterId().equals(id)).findFirst().orElseThrow();}
    Window current() {Flow f=battle.activeFlow();Frame leaf=f.frames().stream().filter(v->v.frameKey().equals(f.leafFrameKey())).findFirst().orElseThrow();return f.windows().stream().filter(w->w.windowKey().equals(leaf.windowKey())).findFirst().orElseThrow();}
    Command pass() {Flow f=battle.activeFlow();Window w=current();Opportunity o=w.opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();return Command.reaction(Kind.PASS_REACTION,o.actorId(),null,f.flowKey(),f.leafFrameKey(),w.windowKey(),o.opportunityKey());}
    Command resume() {Flow f=battle.activeFlow();Window w=current();UUID actor=f.frames().stream().filter(v->v.frameKey().equals(w.ownerFrameKey())).findFirst().orElseThrow().kind()==FrameKind.MOVEMENT?f.movements().stream().filter(m->m.movementKey().equals(w.ownerFrameKey())).findFirst().orElseThrow().actorId():f.attacks().stream().filter(v->v.frameKey().equals(w.ownerFrameKey())).findFirst().orElseThrow().actorId();return new Command(Kind.CONTINUE_FLOW,actor,null,null,List.of(),List.of(),List.of(),f.flowKey(),f.leafFrameKey(),w.windowKey(),null);}
    Command release() {Flow f=battle.activeFlow();Window w=current();Opportunity o=w.opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();UUID ready=o.options().stream().filter(v->v.kind()==ReactionKind.READY_RELEASE).findFirst().orElseThrow().readyKey();return Command.releaseReady(o.actorId(),ready,f.flowKey(),f.leafFrameKey(),w.windowKey(),o.opportunityKey());}

    @Test void readiedAttackWaitsForCompletedEntryAndUsesOneReactionWithoutCreatingAnAttackSequence() {
        start(new Position(0,0),new Position(2,0));
        apply(Command.ready(a,new ReadyInput(new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER,b,new Position(1,0)),ReadyResponseKind.ATTACK,b,item(a),List.of(),null)));
        assertFalse(actor(a).actionAvailable());assertTrue(actor(a).reactionAvailable());assertNull(actor(a).sequence());assertNull(battle.activeFlow());
        apply(Command.action(Kind.END_TURN,a));apply(Command.move(b,List.of(new Position(1,0))));assertEquals(new Position(1,0),actor(b).position());assertEquals(TriggerKind.CREATURE_ENTER_CELL_AFTER,current().trigger().kind());
        Command release=release();apply(release,1);assertFalse(actor(a).reactionAvailable());assertEquals(ReadyStatus.RELEASED,battle.readyPreparations().getFirst().status());assertNull(actor(a).sequence());
        assertThrows(IllegalArgumentException.class,()->rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,release));apply(resume());assertNull(battle.activeFlow());
        assertEquals(1,battle.flows().getLast().attacks().size());assertEquals(1,battle.flows().getLast().readyReleases().size());
    }
    @Test void readyMoveOwnsItsAllowanceAndNestedOpportunityReturnsThroughBothParents() {
        start(new Position(0,0),new Position(0,1));
        apply(Command.ready(a,new ReadyInput(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED,b,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(-1,0),new Position(-2,0)),null)));
        apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.BEGIN_ATTACK,b));apply(Command.attack(b,a,item(b)),1);assertNotNull(battle.activeFlow());
        apply(release());assertEquals(new Position(-1,0),actor(a).position());assertEquals(TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE,current().trigger().kind());
        assertEquals(0,actor(a).movementSpentHalfFeet());assertFalse(actor(a).reactionAvailable());assertTrue(actor(b).reactionAvailable());
        apply(pass());apply(resume());assertEquals(new Position(-2,0),actor(a).position());assertEquals(WindowStatus.CLOSED,current().status());
        apply(resume());assertNull(battle.activeFlow());assertEquals(0,actor(a).movementSpentHalfFeet());
        Flow f=battle.flows().getLast();assertEquals(1,f.movements().size());assertTrue(f.movements().getFirst().readied());assertEquals(10,f.movements().getFirst().spent());assertEquals(30,f.movements().getFirst().allowanceFeet());
        assertEquals(2,f.windows().size());assertTrue(f.windows().stream().allMatch(w->w.status()==WindowStatus.CONSUMED));
    }
    @Test void aMatchingTriggerWithNoLegalCurrentResponsePreservesReadyWithoutSpendingReaction() {
        start(new Position(0,0),new Position(2,0));
        apply(Command.ready(a,new ReadyInput(new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER,b,new Position(3,0)),ReadyResponseKind.ATTACK,b,item(a),List.of(),null)));
        apply(Command.action(Kind.END_TURN,a));var moved=apply(Command.move(b,List.of(new Position(3,0))));
        assertEquals(new Position(3,0),actor(b).position());assertNull(battle.activeFlow());assertEquals(ReadyStatus.ARMED,battle.readyPreparations().getFirst().status());assertTrue(actor(a).reactionAvailable());
        assertTrue(moved.rolls().isEmpty());assertTrue(battle.flows().getLast().windows().isEmpty());assertTrue(battle.flows().getLast().readyReleases().isEmpty());assertTrue(battle.flows().getLast().attacks().isEmpty());
    }
    @Test void ignoringOneTriggerPreservesReadyAndNextOwnTurnExpiresWithoutReactionCost() {
        start(new Position(0,0),new Position(2,0));apply(Command.ready(a,new ReadyInput(new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER,b,new Position(1,0)),ReadyResponseKind.ATTACK,b,item(a),List.of(),null)));
        apply(Command.action(Kind.END_TURN,a));apply(Command.move(b,List.of(new Position(1,0))));apply(pass());apply(resume());
        assertEquals(ReadyStatus.ARMED,battle.readyPreparations().getFirst().status());assertTrue(actor(a).reactionAvailable());
        apply(Command.action(Kind.END_TURN,b));assertEquals(a,battle.activeActor());assertEquals(ReadyStatus.EXPIRED,battle.readyPreparations().getFirst().status());assertTrue(actor(a).reactionAvailable());assertNull(battle.activeFlow());
    }
}
