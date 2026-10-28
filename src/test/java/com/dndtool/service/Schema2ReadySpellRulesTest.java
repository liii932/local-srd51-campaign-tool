package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthState;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullPlan;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullAbilities;
import static com.dndtool.service.Schema2CombatTestFixture.withState;
import static com.dndtool.service.Schema2CombatTestFixture.withHealth;
import static com.dndtool.service.Schema2CheckTestFixture.ITEM_WEAPON;
import static com.dndtool.service.Schema2CheckTestFixture.equipment;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ReadySpellRulesTest {
    final Schema2CombatRules rules=new Schema2CombatRules();final ProductionSchema2Projection material=projection();
    final UUID campaign=UUID.randomUUID(),combat=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    final Map<UUID,Context> roster=new HashMap<>();Battle battle;
    Schema2ReadySpellRulesTest() {roster.put(a,context(a));roster.put(b,context(b));}
    Context context(UUID actor) {
        var plan=fullPlan(material,new Identity(UUID.randomUUID(),UUID.randomUUID()),campaign,actor);State s=new Schema2CharacterProgressionRules().initial(material,plan,UUID.randomUUID(),fullAbilities(),initialChoices());
        var eq=new Schema2CharacterEquipmentRules();UUID focus=UUID.randomUUID();s=eq.apply(material,s,equipment("ITEM_ACQUIRED",focus,HOLY_SYMBOL,"CARRIED"),focus);s=eq.apply(material,s,equipment("ITEM_EQUIPPED",focus,null,"OFF_HAND"),UUID.randomUUID());return new Context(material,plan,s);
    }
    UUID arm(UUID actor) {Context c=roster.get(actor);var eq=new Schema2CharacterEquipmentRules();UUID item=UUID.randomUUID();State s=eq.apply(material,c.character(),equipment("ITEM_ACQUIRED",item,ITEM_WEAPON,"CARRIED"),item);s=eq.apply(material,s,equipment("ITEM_EQUIPPED",item,null,"MAIN_HAND"),UUID.randomUUID());roster.put(actor,withState(c,s));return item;}
    Resolution apply(Command command,int...rolls) {UUID op=UUID.randomUUID();var before=battle;var p=rules.preflight(op,combat,campaign,roster,before,command);AtomicInteger i=new AtomicInteger();var result=rules.resolve(p,sides->{assertTrue(i.get()<rolls.length,"unexpected d"+sides);return rolls[i.getAndIncrement()];});assertEquals(rolls.length,i.get());rules.verify(op,combat,campaign,roster,before,command,result);for(HealthChange h:result.healthChanges())roster.put(h.characterId(),withState(roster.get(h.characterId()),h.after()));battle=result.after();return result;}
    void start() {apply(Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(1,0),1)),List.of(a,b)),20,10);}
    Command readyCure() {return Command.ready(a,new ReadyInput(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED,b,null),ReadyResponseKind.SPELL,null,null,List.of(),new CastInput(CASTING,CURE_WOUNDS,List.of(a),null)));}
    long slots() {return roster.get(a).character().resources().stream().filter(r->r.identity().equals(SLOT_RESOURCE)).findFirst().orElseThrow().current();}
    Command release() {Flow f=battle.activeFlow();Window w=f.windows().getLast();Opportunity o=w.opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();return Command.releaseReady(o.actorId(),o.options().getFirst().readyKey(),f.flowKey(),f.leafFrameKey(),w.windowKey(),o.opportunityKey());}
    Command resume() {Flow f=battle.activeFlow();Window w=f.windows().getLast();return new Command(Kind.CONTINUE_FLOW,b,null,null,List.of(),List.of(),List.of(),f.flowKey(),f.leafFrameKey(),w.windowKey(),null);}

    @Test void preparationPaysOnceAndHoldsEvenInstantaneousCureUntilOneReactionRelease() {
        UUID weapon=arm(b);Context source=roster.get(a);roster.put(a,withState(source,withHealth(source.character(),1,HealthState.INITIAL)));start();
        var prepared=apply(readyCure());assertTrue(prepared.rolls().isEmpty());assertEquals(1,slots());assertEquals(1,roster.get(a).character().hitPoints());assertEquals(CastStatus.HELD,battle.flows().getFirst().casts().getFirst().status());assertEquals(ConcentrationKind.READY_SPELL,battle.concentrationHolders().getFirst().kind());
        apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.BEGIN_ATTACK,b));apply(Command.attack(b,a,weapon),1);var released=apply(release(),4);
        assertEquals(1,slots());assertEquals(7,roster.get(a).character().hitPoints());assertEquals(1,released.healthChanges().size());assertNull(released.healthChanges().getFirst().resource());assertTrue(battle.concentrationHolders().isEmpty());assertEquals(CastStatus.RESOLVED,battle.flows().getFirst().casts().getFirst().status());assertEquals(List.of(4),battle.flows().getFirst().casts().getFirst().healingDice());
        apply(resume());assertNull(battle.activeFlow());assertEquals(1,battle.flows().stream().mapToInt(f->f.casts().size()).sum());assertEquals(1,battle.flows().stream().mapToInt(f->f.readyReleases().size()).sum());
    }
    @Test void failedConcentrationDiscardsHeldEnergyAndProducesNoReleaseOrRefund() {
        UUID weapon=arm(b);start();apply(readyCure());apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.BEGIN_ATTACK,b));var damage=apply(Command.attack(b,a,weapon),10,1,1);
        assertEquals("CONCENTRATION_D20",damage.rolls().getLast().purpose());assertEquals(ReadyStatus.LOST,battle.readyPreparations().getFirst().status());assertEquals("CONCENTRATION_SAVE_FAILED",battle.readyPreparations().getFirst().endReason());assertTrue(battle.concentrationHolders().isEmpty());assertEquals(1,slots());assertNull(battle.activeFlow());assertTrue(battle.flows().stream().allMatch(f->f.readyReleases().isEmpty()));
    }
    @Test void untriggeredHeldSpellExpiresAtNextOwnTurnWithoutEffectOrRefund() {
        start();apply(readyCure());apply(Command.action(Kind.END_TURN,a));apply(Command.action(Kind.END_TURN,b));
        assertEquals(ReadyStatus.EXPIRED,battle.readyPreparations().getFirst().status());assertTrue(battle.concentrationHolders().isEmpty());assertEquals(1,slots());assertTrue(battle.flows().getFirst().casts().getFirst().healingDice().isEmpty());assertNull(battle.activeFlow());
    }
}
