package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CombatRuntimeService.*;
import com.dndtool.service.Schema2CombatRuntimeService.Status;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.Schema2CombatState.Command;
import com.dndtool.testing.IsolatedJvmTest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static com.dndtool.module.composition.Schema2MonsterFixtures.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.CLASS;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Hand-calculated spell oracle, accepted by actual preparation, snapshot and transactional consumers. */
class Schema2SpellSaveDamageRuntimeServiceTest {
    static final UUID B=UUID.fromString("20000000-0000-4000-8000-000000000002");
    static final UUID H=UUID.fromString("00000000-0000-4000-8000-000000000003"),M=UUID.fromString("00000000-0000-4000-8000-000000000004");
    static final UUID C=UUID.fromString("60000000-0000-4000-8000-000000000003"),D=UUID.fromString("70000000-0000-4000-8000-000000000004"),E=UUID.fromString("80000000-0000-4000-8000-000000000005");
    static final Schema2CompositionKernel.RuleIdentity BURNING=spell("burning_hands");
    static Schema2CombatRuntimeServiceTest.Fixture fixture(boolean initialize)throws Exception {
        var f=new Schema2CombatRuntimeServiceTest.Fixture(initialize,Schema2SpellSaveDamageFixtures.packages(),GrowthChoices.EMPTY,WIZARD,
                Map.of(B,CLASS),Map.of(B,Schema2SpellFixtures.initialChoices()));
        if(initialize) {
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(f.a,
                    JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(BURNING))))).status());
            character(f,f.a,equipment("ITEM_UNEQUIPPED",f.items.get(f.a),null,null));
            UUID focus=Schema2SpellRuntimeServiceTest.focus(B);var owner=f.members.stream().filter(m->m.characterId().equals(B)).findFirst().orElseThrow();
            var acquire=new Schema2CharacterRuntimeService.Request(focus,owner.identity(),f.campaign,B,owner.preparationOperationId(),f.binding,f.character(B).version(),equipment("ITEM_ACQUIRED",focus,Schema2SpellFixtures.HOLY_SYMBOL,"CARRIED"),Map.of());
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(acquire).status());
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(B,equipment("ITEM_EQUIPPED",focus,null,"OFF_HAND"))).status());
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(B,effect(TEMPORARY,"REPLACE"))).status());
        }
        return f;
    }
    static void start(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {
        synchronizeClocks(f);
        f.nextRolls.addAll(List.of(15,20,10,5));
        f.commit(Command.start(List.of(new Deployment(f.a,new Position(0,0),1),new Deployment(B,new Position(1,0),0)),
                List.of(new MonsterDeployment(H,HELL_HOUND,new Position(2,1),0),new MonsterDeployment(M,MUMMY,new Position(3,0),0)),List.of(B,f.a,H,M)));
        assertEquals(B,f.battle.activeActor());assertTrue(f.nextRolls.isEmpty());
    }
    static Schema2CombatRuntimeServiceTest.Fixture prepared()throws Exception {
        var f=fixture(true);start(f);f.commit(Schema2SpellRuntimeServiceTest.bless(f,B,List.of(f.a,B)));
        f.commit(Command.action(Kind.END_TURN,B));f.commit(Command.action(Kind.END_TURN,f.a));f.commit(Command.action(Kind.END_TURN,H));f.commit(Command.action(Kind.END_TURN,M));
        f.commit(Command.ready(B,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,f.a,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,-1)),null)));
        f.commit(Command.action(Kind.END_TURN,B));assertEquals(f.a,f.battle.activeActor());return f;
    }
    static Command burning(Schema2CombatRuntimeServiceTest.Fixture f,int direction,int slot) {return Command.cast(f.a,new CastInput(CASTING,BURNING,List.of(),null,slot,null,direction));}
    static SaveDamageBatch batch(Schema2CombatRuntimeServiceTest.Fixture f) {return f.battle.saveDamageBatches().getFirst();}
    static Request apply(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {var flow=f.battle.activeFlow();return f.request(Command.continueBreath(f.a,flow.flowKey(),flow.leafFrameKey()));}
    static void release(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {f.nextRolls.addAll(List.of(3,4,4));f.commit(burning(f,0,1));assertTrue(f.nextRolls.isEmpty());}
    static long slots(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {return f.character(f.a).resources().stream().filter(r->r.identity().equals(SLOT1)).findFirst().orElseThrow().current();}
    static void replay(Schema2CombatRuntimeServiceTest.Fixture f,Request request)throws Exception {var save=f.service.read(f.combat);int rolls=f.rolls,writes=f.runtime.writes;assertEquals(Status.REPLAYED,f.service.execute(request).status());assertEquals(save,f.service.read(f.combat));assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);}

    @Test void realSpellSharedRollMixedTargetsConcentrationAndAfterCastReadyRestoreWithoutReexecution()throws Exception {
        var f=prepared();int rolls=f.rolls;Request release=f.request(burning(f,0,1));f.nextRolls.addAll(List.of(3,4,4));f.commit(release);replay(f,release);
        assertEquals(rolls+3,f.rolls);assertEquals(1,slots(f));assertFalse(f.actor(f.a).actionAvailable());
        var raw=batch(f);assertNull(raw.action());assertEquals(BURNING,raw.spellSource().spell());assertEquals(2,raw.spellSource().proficiencyBonus());assertEquals(0,raw.spellSource().abilityModifier());assertEquals(10,raw.difficultyClass());
        assertEquals(List.of(B,H,M),raw.targetProof().targets().stream().map(CreatureRef::creatureId).toList());assertEquals(4,raw.targetProof().candidates().size());
        assertEquals(List.of(3,4,4),raw.sharedRoll().dice());assertEquals(11,raw.sharedRoll().rawTotal());assertTrue(raw.checks().isEmpty());assertTrue(raw.packets().isEmpty());assertEquals(9,f.character(B).hitPoints());assertEquals(5,f.character(B).health().temporaryHitPoints());
        Save save=f.service.read(f.combat);var restored=fixture(false);restored.source.unavailable(true);assertEquals(Status.COMMITTED,restored.service.restore(save).status());assertEquals(save,restored.service.read(f.combat));assertEquals(0,restored.rolls);restored.battle=save.combat().current();
        Request apply=apply(restored);restored.nextRolls.addAll(List.of(7,1,1,2,8,1));restored.commit(apply);assertTrue(restored.nextRolls.isEmpty());replay(restored,apply);
        var done=batch(restored);assertEquals(BatchStatus.COMPLETED,done.status());assertEquals(List.of(11,0,22),done.packets().stream().map(DamagePacket::damageAmount).toList());
        assertEquals(List.of(3,45,36),done.packets().stream().map(DamagePacket::afterHitPoints).toList());assertEquals(List.of(0,0,0),done.packets().stream().map(DamagePacket::afterTemporaryHitPoints).toList());
        // SRD p.322: Hell Hound DEX 12 (+1), no listed save proficiency; its immune target still rolls 1 + 1 + 0 = 2.
        var hellSave=done.checks().get(1);assertEquals("ability.dexterity",hellSave.abilityKey());assertEquals(1,hellSave.firstD20());assertEquals(1,hellSave.abilityModifier());assertEquals(0,hellSave.proficiencyBonus());
        assertEquals(List.of(9,2,1,10),done.checks().stream().map(BatchCheck::total).toList());assertEquals(ConcentrationDamageStatus.CHECK_SUCCEEDED,done.concentrationObligations().getFirst().status());
        assertEquals(EffectStatus.ACTIVE,restored.battle.ongoingEffects().getFirst().status());assertEquals(TriggerKind.CAST_COMPLETED,restored.battle.activeFlow().windows().getLast().trigger().kind());
        Save ready=restored.service.read(f.combat);var target=fixture(false);target.source.unavailable(true);assertEquals(Status.COMMITTED,target.service.restore(ready).status());assertEquals(ready,target.service.read(f.combat));assertEquals(0,target.rolls);target.battle=ready.combat().current();
        var flow=target.battle.activeFlow();var window=flow.windows().getLast();var offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();
        Request respond=target.request(Command.releaseReady(B,offer.options().getFirst().readyKey(),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));target.commit(respond);replay(target,respond);
        assertEquals(new Position(1,-1),target.actor(B).position());assertFalse(target.actor(B).reactionAvailable());assertEquals(0,target.rolls);assertEquals(1,slots(target));
        flow=target.battle.activeFlow();target.commit(new Command(Kind.CONTINUE_FLOW,target.a,null,null,List.of(),List.of(),List.of(),flow.flowKey(),flow.leafFrameKey(),flow.windows().getLast().windowKey(),null));
        assertNull(target.battle.activeFlow());assertEquals(done.packets(),batch(target).packets());assertEquals(CastStatus.RESOLVED,target.battle.flows().getLast().casts().getFirst().status());
    }
    @Test void oddHalfDamageStillRollsImmuneSaveAndZeroHpEndsConcentrationBeforeAnyCon()throws Exception {
        var f=prepared();f.nextRolls.addAll(List.of(5,5,5));f.commit(burning(f,0,1));f.nextRolls.addAll(List.of(1,1,20,20));f.commit(apply(f));
        assertEquals(List.of(15,0,14),batch(f).packets().stream().map(DamagePacket::damageAmount).toList());assertEquals(0,f.character(B).hitPoints());assertEquals(EffectStatus.ENDED,f.battle.ongoingEffects().getFirst().status());
        assertEquals(ConcentrationDamageStatus.FORCED_ENDED,batch(f).concentrationObligations().getFirst().status());assertEquals(3,batch(f).checks().size());assertTrue(f.actor(B).prone());assertNull(f.battle.activeFlow());
        assertTrue(f.character(B).equipment().stream().filter(i->Set.of(f.items.get(B),Schema2SpellRuntimeServiceTest.focus(B)).contains(i.itemId())).allMatch(i->i.location().equals("GROUND")));
        f.commit(Command.action(Kind.END_TURN,f.a));f.commit(Command.action(Kind.END_TURN,H));f.nextRolls.add(20);f.commit(Command.action(Kind.END_TURN,M));f.commit(Command.action(Kind.END_TURN,B));
        var before=f.service.read(f.combat);int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals("S2_SPELL_OBJECT_EFFECT_UNSUPPORTED",f.service.execute(f.request(burning(f,0,1))).code());assertEquals(before,f.service.read(f.combat));assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);
    }
    static List<Map<String,byte[]>> spellPackages(boolean sculpt) {
        var packages=Schema2SpellSaveDamageFixtures.packages();
        if(!sculpt) {
            var rows=Schema2GrantFixtures.json(packages.get(1).get("rules.json")).getAsJsonArray();
            for(int i=rows.size()-1;i>=0;i--) {var r=rows.get(i).getAsJsonObject();if(r.has("execution_algorithm")&&r.get("execution_algorithm").getAsString().equals("SRD_2014_SCULPT_SPELLS_V1"))rows.remove(i);}
            packages.get(1).put("rules.json",rows.toString().getBytes(StandardCharsets.UTF_8));
        }
        return packages;
    }
    static Schema2CombatRuntimeServiceTest.Fixture wizards(boolean sculpt)throws Exception {
        var f=new Schema2CombatRuntimeServiceTest.Fixture(true,spellPackages(sculpt),GrowthChoices.EMPTY,WIZARD);
        for(UUID actor:List.of(f.a,f.b))character(f,actor,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(actor.equals(f.a)?BURNING:SHIELD))));
        character(f,f.a,equipment("ITEM_UNEQUIPPED",f.items.get(f.a),null,null));
        return f;
    }
    static void character(Schema2CombatRuntimeServiceTest.Fixture f,UUID actor,JdbcSchema2CharacterRuntimeRepository.Command command)throws Exception {
        var result=f.characterService.execute(f.characterRequest(actor,command));assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,result.status(),result.code());
    }
    static void grow(Schema2CombatRuntimeServiceTest.Fixture f,UUID actor,int level)throws Exception {
        for(int next=2;next<=level;next++) {
            var choices=next==2?new GrowthChoices(EVOCATION,Map.of(),null,Map.of()):next==4?new GrowthChoices(null,Map.of("ability.intelligence",2),null,Map.of()):GrowthChoices.EMPTY;
            character(f,actor,new JdbcSchema2CharacterRuntimeRepository.Command("CHARACTER_ADVANCED",null,0,null,"FIXED_AVERAGE",WIZARD,choices));
            character(f,actor,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_ADVANCED",new SpellbookInput(CASTING,learnedAt(next),f.character(actor).spellbook().prepared())));
        }
    }
    static void synchronizeClocks(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {
        long max=0;for(var member:f.members)max=Math.max(max,f.character(member.characterId()).rest().minute());
        for(var member:f.members) {UUID actor=member.characterId();int delta=Math.toIntExact(max-f.character(actor).rest().minute());if(delta>0)character(f,actor,Schema2ArcaneRecoveryTestFixture.rest("TIME_ADVANCED",new RestInput(null,"NONE",delta,0,Map.of())));}
    }
    @Test void realRankTwoAndThreeSlotsProduceFourAndFiveDiceWithTheirActualDc()throws Exception {
        for(int slot:List.of(2,3)) {
            var f=wizards(false);grow(f,f.a,slot==2?3:5);synchronizeClocks(f);f.nextRolls.addAll(List.of(20,1));f.start();
            int count=slot+2;f.nextRolls.addAll(Collections.nCopies(count,1));f.commit(burning(f,0,slot));
            assertEquals(slot,batch(f).spellSource().slotLevel());assertEquals(count,batch(f).sharedRoll().dice().size());assertEquals(slot==2?10:12,batch(f).difficultyClass());
            f.nextRolls.add(slot==2?9:11);f.commit(apply(f));assertEquals(count/2,batch(f).packets().getFirst().damageAmount());assertEquals(7-count/2,f.character(f.b).hitPoints());
            var resource=slot==2?SLOT2:SLOT3;assertEquals(1,f.character(f.a).resources().stream().filter(r->r.identity().equals(resource)).findFirst().orElseThrow().current());assertEquals(4,slots(f));assertFalse(f.actor(f.a).actionAvailable());assertTrue(f.nextRolls.isEmpty());
        }
    }
    @Test void realCounterspellRestoresTheDeclarationWindowWithoutRefundOrInventedBatch()throws Exception {
        var f=wizards(true);grow(f,f.b,5);
        character(f,f.b,Schema2ArcaneRecoveryTestFixture.start("LONG_REST"));character(f,f.b,Schema2ArcaneRecoveryTestFixture.time("SLEEP",480));character(f,f.b,Schema2ArcaneRecoveryTestFixture.finish(Map.of()));
        character(f,f.b,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(COUNTERSPELL))));
        synchronizeClocks(f);f.nextRolls.addAll(List.of(20,1));f.start();int rolls=f.rolls;Request cast=f.request(burning(f,0,1));f.commit(cast);replay(f,cast);
        assertEquals(1,slots(f));assertTrue(f.battle.saveDamageBatches().isEmpty());assertEquals(rolls,f.rolls);
        Save declaration=f.service.read(f.combat);var target=new Schema2CombatRuntimeServiceTest.Fixture(false,Schema2SpellSaveDamageFixtures.packages(),GrowthChoices.EMPTY,WIZARD);target.source.unavailable(true);
        assertEquals(Status.COMMITTED,target.service.restore(declaration).status());assertEquals(0,target.rolls);target.battle=declaration.combat().current();
        var flow=target.battle.activeFlow();var window=flow.windows().getLast();var offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();
        target.commit(Command.reactionSpell(Kind.DECLARE_COUNTERSPELL,target.b,new CastInput(CASTING,COUNTERSPELL,List.of(target.a),null,3,window.trigger().sourceFrameKey()),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));
        flow=target.battle.activeFlow();target.commit(new Command(Kind.CONTINUE_FLOW,target.a,null,null,List.of(),List.of(),List.of(),flow.flowKey(),flow.leafFrameKey(),flow.windows().getLast().windowKey(),null));
        assertNull(target.battle.activeFlow());assertTrue(target.battle.saveDamageBatches().isEmpty());assertEquals(CastStatus.COUNTERED,target.battle.flows().getLast().casts().getFirst().status());assertEquals(1,slots(target));assertEquals(0,target.rolls);assertFalse(target.actor(target.a).actionAvailable());
        assertEquals(1,target.character(target.b).resources().stream().filter(r->r.identity().equals(SLOT3)).findFirst().orElseThrow().current());
    }
    @Test void heldAreaSpellAndAcquiredSculptRejectBeforeTheActionOrSlotIsPaid()throws Exception {
        var f=wizards(true);f.nextRolls.addAll(List.of(20,1));f.start();var before=f.service.read(f.combat);int writes=f.runtime.writes,rolls=f.rolls;
        var input=new CastInput(CASTING,BURNING,List.of(),null,1,null,0);
        assertEquals("S2_SPELL_READY_AREA_UNSUPPORTED",f.service.execute(f.request(Command.ready(f.a,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,f.b,null),ReadyResponseKind.SPELL,null,null,List.of(),input)))).code());
        assertEquals(before,f.service.read(f.combat));assertEquals(writes,f.runtime.writes);assertEquals(rolls,f.rolls);
        var sculpt=wizards(true);grow(sculpt,sculpt.a,3);synchronizeClocks(sculpt);sculpt.nextRolls.addAll(List.of(20,1));sculpt.start();before=sculpt.service.read(sculpt.combat);writes=sculpt.runtime.writes;rolls=sculpt.rolls;
        assertEquals("S2_SPELL_SCULPT_UNSUPPORTED",sculpt.service.execute(sculpt.request(burning(sculpt,0,2))).code());assertEquals(before,sculpt.service.read(sculpt.combat));assertEquals(writes,sculpt.runtime.writes);assertEquals(rolls,sculpt.rolls);
    }
    @Test void metadataAndOccupiedHandsCannotBorrowTheNewExecutableSpellContract()throws Exception {
        var old=new Schema2CombatRuntimeServiceTest.Fixture(true,Schema2WizardFixtures.packages(),GrowthChoices.EMPTY,WIZARD);
        character(old,old.a,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(BURNING))));
        character(old,old.b,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(SHIELD))));
        character(old,old.a,equipment("ITEM_UNEQUIPPED",old.items.get(old.a),null,null));old.start();var before=old.service.read(old.combat);int writes=old.runtime.writes,rolls=old.rolls;
        assertEquals("S2_SPELL_UNAVAILABLE",old.service.execute(old.request(burning(old,0,1))).code());assertEquals(before,old.service.read(old.combat));assertEquals(writes,old.runtime.writes);assertEquals(rolls,old.rolls);
        var held=wizards(false);character(held,held.a,equipment("ITEM_EQUIPPED",held.items.get(held.a),null,"MAIN_HAND"));held.start();before=held.service.read(held.combat);writes=held.runtime.writes;rolls=held.rolls;
        assertEquals("S2_SPELL_COMPONENT",held.service.execute(held.request(burning(held,0,1))).code());assertEquals(before,held.service.read(held.combat));assertEquals(writes,held.runtime.writes);assertEquals(rolls,held.rolls);
        assertThrows(IllegalArgumentException.class,()->new CastInput(CASTING,BURNING,List.of(),null,1,null,8));
        assertThrows(IllegalArgumentException.class,()->new CastInput(CASTING,BURNING,List.of(held.b),null,1,null,0));
    }
    static Schema2CombatRuntimeServiceTest.Fixture fourWizards(boolean initialize)throws Exception {
        return counterspellWizards(initialize,List.of(C,D),true);
    }
    static Schema2CombatRuntimeServiceTest.Fixture counterspellWizards(boolean initialize,List<UUID> additional,boolean sculpt)throws Exception {
        var f=new Schema2CombatRuntimeServiceTest.Fixture(initialize,spellPackages(sculpt),GrowthChoices.EMPTY,WIZARD,Map.of(),Map.of(),additional);
        if(initialize) {
            for(var member:f.members) {UUID actor=member.characterId();character(f,actor,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(actor.equals(f.a)?BURNING:SHIELD))));}
            character(f,f.a,equipment("ITEM_UNEQUIPPED",f.items.get(f.a),null,null));
            for(UUID actor:List.of(f.b,C)) {
                grow(f,actor,5);character(f,actor,Schema2ArcaneRecoveryTestFixture.start("LONG_REST"));character(f,actor,Schema2ArcaneRecoveryTestFixture.time("SLEEP",480));character(f,actor,Schema2ArcaneRecoveryTestFixture.finish(Map.of()));
                character(f,actor,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(COUNTERSPELL))));
            }
            synchronizeClocks(f);
        }
        return f;
    }
    static void armedAttack(Schema2CombatRuntimeServiceTest.Fixture f,UUID target)throws Exception {
        f.nextRolls.addAll(List.of(15,10,5,20));f.commit(Command.start(List.of(new Deployment(f.a,new Position(0,0),0),new Deployment(f.b,new Position(1,0),1),new Deployment(C,new Position(2,0),2),new Deployment(D,new Position(0,1),3)),List.of(D,f.a,f.b,C)));
        f.commit(Command.ready(D,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,C,null),ReadyResponseKind.ATTACK,target,f.items.get(D),List.of(),null)));f.commit(Command.action(Kind.END_TURN,D));assertEquals(f.a,f.battle.activeActor());
    }
    static void armedAttackSuccessor(Schema2CombatRuntimeServiceTest.Fixture f,ReadyInput successor)throws Exception {
        f.nextRolls.addAll(List.of(15,10,5,19,20));
        f.commit(Command.start(List.of(new Deployment(f.a,new Position(0,0),0),new Deployment(f.b,new Position(1,0),1),new Deployment(C,new Position(2,0),2),new Deployment(D,new Position(0,1),3),new Deployment(E,new Position(1,1),4)),List.of(E,D,f.a,f.b,C)));
        f.commit(Command.ready(E,successor));f.commit(Command.action(Kind.END_TURN,E));
        f.commit(Command.ready(D,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,C,null),ReadyResponseKind.ATTACK,f.a,f.items.get(D),List.of(),null)));
        f.commit(Command.action(Kind.END_TURN,D));assertEquals(f.a,f.battle.activeActor());assertTrue(f.nextRolls.isEmpty());
    }
    static void rejectBeforePayment(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {
        var before=f.service.read(f.combat);int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals("S2_SPELL_NESTED_OBJECT_EFFECT_UNSUPPORTED",f.service.execute(f.request(burning(f,0,1))).code());
        assertEquals(before,f.service.read(f.combat));assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);assertTrue(f.actor(f.a).actionAvailable());
    }
    static Schema2CombatRuntimeServiceTest.Fixture originalShieldScene(int level)throws Exception {
        var f=counterspellWizards(true,List.of(C,D,E),false);grow(f,f.a,level);
        character(f,f.a,Schema2ArcaneRecoveryTestFixture.start("LONG_REST"));character(f,f.a,Schema2ArcaneRecoveryTestFixture.time("SLEEP",480));character(f,f.a,Schema2ArcaneRecoveryTestFixture.finish(Map.of()));
        character(f,f.a,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(BURNING,SHIELD))));
        synchronizeClocks(f);return f;
    }
    static Window currentWindow(Schema2CombatRuntimeServiceTest.Fixture f) {
        var flow=f.battle.activeFlow();var leaf=flow.frames().stream().filter(v->v.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();return flow.windows().stream().filter(v->v.windowKey().equals(leaf.windowKey())).findFirst().orElseThrow();
    }
    static void counter(Schema2CombatRuntimeServiceTest.Fixture f,UUID actor)throws Exception {
        var flow=f.battle.activeFlow();var window=currentWindow(f);var offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();assertEquals(actor,offer.actorId());
        f.commit(Command.reactionSpell(Kind.DECLARE_COUNTERSPELL,actor,new CastInput(CASTING,COUNTERSPELL,List.of(window.trigger().subjectId()),null,3,window.trigger().sourceFrameKey()),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));
    }
    static Request resumeCast(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {
        var flow=f.battle.activeFlow();var window=currentWindow(f);assertEquals(WindowStatus.CLOSED,window.status());assertEquals(window.ownerFrameKey(),flow.leafFrameKey());
        UUID owner=flow.casts().stream().filter(v->v.frameKey().equals(window.ownerFrameKey())).findFirst().orElseThrow().actorId();
        return f.request(new Command(Kind.CONTINUE_FLOW,owner,null,null,List.of(),List.of(),List.of(),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),null));
    }
    @Test void reachableNestedDropIsRejectedBeforeOriginalPaymentOrRandomness()throws Exception {
        // Wizard 5 starts at 27 HP; two actual 6-damage effects make D's 2d8+2 critical able to drop its held weapon.
        var f=fourWizards(true);character(f,f.b,effect(DAMAGE,null));character(f,f.b,effect(DAMAGE,null));assertEquals(15,f.character(f.b).hitPoints());
        assertEquals("MAIN_HAND",f.character(f.b).equipment().stream().filter(i->i.itemId().equals(f.items.get(f.b))).findFirst().orElseThrow().location());
        assertTrue(f.character(f.b).equipment().stream().noneMatch(i->i.location().equals("GROUND")));
        armedAttack(f,f.b);var before=f.service.read(f.combat);int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals("S2_SPELL_NESTED_OBJECT_EFFECT_UNSUPPORTED",f.service.execute(f.request(burning(f,0,1))).code());assertEquals(before,f.service.read(f.combat));assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);assertEquals(2,slots(f));assertTrue(f.actor(f.a).actionAvailable());
    }
    @Test void nestedReadyAttackSuccessorCannotDropAnotherOwnerAfterOriginalPayment()throws Exception {
        var f=counterspellWizards(true,List.of(C,D,E),true);
        character(f,f.b,effect(DAMAGE,null));character(f,f.b,effect(DAMAGE,null));assertEquals(15,f.character(f.b).hitPoints());
        assertEquals("MAIN_HAND",f.character(f.b).equipment().stream().filter(i->i.itemId().equals(f.items.get(f.b))).findFirst().orElseThrow().location());
        // D can miss A, then E's real ATTACK_COMPLETED response can deal 2d8+2 to B.
        armedAttackSuccessor(f,new ReadyInput(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED,D,null),ReadyResponseKind.ATTACK,f.b,f.items.get(E),List.of(),null));
        rejectBeforePayment(f);assertEquals(2,slots(f));
    }
    @Test void nestedReadyAttackSuccessorMovementIsAlsoRejectedBeforePayment()throws Exception {
        var f=counterspellWizards(true,List.of(C,D,E),true);
        armedAttackSuccessor(f,new ReadyInput(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED,D,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,2)),null));
        rejectBeforePayment(f);assertEquals(2,slots(f));
    }
    @Test void nestedShieldCompletionIsReachableWithoutOriginalCasterCounterspell()throws Exception {
        var f=originalShieldScene(2);
        assertFalse(f.character(f.a).spellbook().prepared().contains(COUNTERSPELL));assertEquals(3,slots(f));
        // A can Shield D's nested hit; that completion precedes the pending Burning Hands.
        armedAttackSuccessor(f,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,f.a,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,2)),null));
        rejectBeforePayment(f);assertEquals(3,slots(f));
    }
    @Test void nestedAttackWithoutPreparedShieldDoesNotSeedOriginalCompletion()throws Exception {
        var f=counterspellWizards(true,List.of(C,D,E),true);assertFalse(f.character(f.a).spellbook().prepared().contains(SHIELD));
        armedAttackSuccessor(f,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,f.a,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,2)),null));
        int rolls=f.rolls;f.commit(burning(f,0,1));assertEquals(1,slots(f));assertEquals(rolls,f.rolls);assertTrue(f.battle.saveDamageBatches().isEmpty());
    }
    @Test void nestedAttackWithoutShieldSlotsDoesNotSeedOriginalCompletion()throws Exception {
        var f=originalShieldScene(3);character(f,f.a,new JdbcSchema2CharacterRuntimeRepository.Command("RESOURCE_SPENT",SLOT1,4,null,null));assertEquals(0,slots(f));
        assertTrue(f.character(f.a).spellbook().prepared().contains(SHIELD));
        armedAttackSuccessor(f,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,f.a,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,2)),null));
        int rolls=f.rolls;f.commit(burning(f,0,2));assertEquals(0,slots(f));assertEquals(1,f.character(f.a).resources().stream().filter(r->r.identity().equals(SLOT2)).findFirst().orElseThrow().current());assertEquals(rolls,f.rolls);
    }
    @Test void originalCompletionReadyMoveStillRunsWithCounterspellCandidates()throws Exception {
        var f=fourWizards(true);f.nextRolls.addAll(List.of(15,10,5,20));
        f.commit(Command.start(List.of(new Deployment(f.a,new Position(0,0),0),new Deployment(f.b,new Position(1,0),1),new Deployment(C,new Position(2,0),2),new Deployment(D,new Position(0,1),3)),List.of(D,f.a,f.b,C)));
        f.commit(Command.ready(D,new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,f.a,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,1)),null)));
        f.commit(Command.action(Kind.END_TURN,D));f.commit(burning(f,0,1));
        for(UUID reactor:List.of(f.b,C)) {
            var flow=f.battle.activeFlow();var window=currentWindow(f);var offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();assertEquals(reactor,offer.actorId());
            f.commit(Command.reaction(Kind.PASS_REACTION,reactor,null,flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));
        }
        f.nextRolls.addAll(List.of(1,1,1));f.commit(resumeCast(f));f.nextRolls.addAll(List.of(20,20));f.commit(apply(f));
        assertEquals(BatchStatus.COMPLETED,batch(f).status());var flow=f.battle.activeFlow();var window=currentWindow(f);assertEquals(f.a,window.trigger().subjectId());
        var offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();assertEquals(D,offer.actorId());
        f.commit(Command.releaseReady(D,offer.options().getFirst().readyKey(),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));
        assertEquals(new Position(1,1),f.actor(D).position());f.commit(resumeCast(f));assertNull(f.battle.activeFlow());assertEquals(1,slots(f));assertTrue(f.nextRolls.isEmpty());
    }
    @Test void actualNestedReadyAttackIncapacitatesCasterThenRestoredParentInterruptsWithoutRepayment()throws Exception {
        var f=fourWizards(true);armedAttack(f,f.a);f.commit(burning(f,0,1));counter(f,f.b);counter(f,C);
        var flow=f.battle.activeFlow();var window=currentWindow(f);var offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();assertEquals(D,offer.actorId());
        f.nextRolls.addAll(List.of(20,8,8));f.commit(Command.releaseReady(D,offer.options().getFirst().readyKey(),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));assertEquals(0,f.character(f.a).hitPoints());assertTrue(f.character(f.a).health().dead());assertEquals(1,slots(f));assertTrue(f.battle.saveDamageBatches().isEmpty());
        Save save=f.service.read(f.combat);var restored=fourWizards(false);restored.source.unavailable(true);assertEquals(Status.COMMITTED,restored.service.restore(save).status());restored.battle=save.combat().current();assertEquals(0,restored.rolls);assertEquals(save,restored.service.read(f.combat));
        assertEquals(TriggerKind.CAST_COMPLETED,currentWindow(restored).trigger().kind());assertEquals(C,currentWindow(restored).trigger().subjectId());restored.commit(resumeCast(restored));
        assertEquals(TriggerKind.CAST_BEFORE_COMPLETION,currentWindow(restored).trigger().kind());assertEquals(restored.b,currentWindow(restored).trigger().subjectId());restored.commit(resumeCast(restored));
        flow=restored.battle.activeFlow();window=currentWindow(restored);offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();
        assertEquals(TriggerKind.CAST_BEFORE_COMPLETION,window.trigger().kind());assertEquals(restored.a,window.trigger().subjectId());assertEquals(WindowStatus.OPEN,window.status());assertEquals(C,offer.actorId());assertFalse(restored.actor(C).reactionAvailable());
        restored.commit(Command.reaction(Kind.ADVANCE_WINDOW,C,null,flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));
        Request parent=resumeCast(restored);var completed=restored.commit(parent);replay(restored,parent);
        assertNull(restored.battle.activeFlow());assertTrue(restored.battle.saveDamageBatches().isEmpty());assertEquals(1,slots(restored));assertEquals(0,restored.rolls);
        assertEquals(CastStatus.INTERRUPTED,restored.battle.flows().getLast().casts().getFirst().status());assertTrue(completed.event().resolution().facts().stream().anyMatch(v->v.kind().equals("CAST_INTERRUPTED")&&"CASTER_INCAPACITATED".equals(v.reason())));
    }
    @Test void emptyConePaysOnceWithoutDamageDiceAndInvalidSlotOrLateVersionRejectsBeforePayment()throws Exception {
        var f=prepared();var before=f.service.read(f.combat);int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals("S2_SPELL_SLOT_UNAVAILABLE",f.service.execute(f.request(burning(f,0,2))).code());
        var valid=f.request(burning(f,0,1));var versions=new LinkedHashMap<>(valid.expectedCharacterVersions());versions.put(B,versions.get(B)-1);
        assertEquals("S2_COMBAT_STALE_CHARACTER",f.service.execute(new Request(valid.operationId(),valid.combatId(),valid.campaignId(),valid.rules(),valid.members(),valid.expectedVersion(),versions,valid.command())).code());
        assertEquals(before,f.service.read(f.combat));assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);
        f.commit(burning(f,4,1));assertEquals(BatchStatus.NO_TARGETS,batch(f).status());assertNull(batch(f).sharedRoll());assertTrue(batch(f).packets().isEmpty());assertEquals(rolls,f.rolls);assertEquals(1,slots(f));
    }
    @Test void missingExtraOrChangedRealSpellSourcesRejectBeforeAnyContinuation()throws Exception {
        for(String mutation:List.of("missing","extra","changed"))IsolatedJvmTest.run(FaultProbe.class,"corrupt",mutation);
    }
    @Test void spellRawAndDamageSuffixFailuresRollbackWithoutRepeatingAcceptedCost()throws Exception {
        for(String table:List.of("s2_combat_spell_damage_source","s2_combat_event","s2_character_combat_input","s2_combat_breath_packet"))IsolatedJvmTest.run(FaultProbe.class,"rollback",table);
    }
    @Test void unknownSpellDamageOutcomeIsResolvedThroughTheOriginalOperation()throws Exception {
        for(String outcome:List.of("COMMITTED","ROLLED_BACK"))IsolatedJvmTest.run(FaultProbe.class,"unknown",outcome);
    }
    public static final class FaultProbe {
        public static void main(String[] args)throws Exception {
            var f=prepared();boolean suffix=!args[0].equals("rollback")||!Set.of("s2_combat_spell_damage_source","s2_combat_event").contains(args[1]);
            if(suffix)release(f);Request request=suffix?apply(f):f.request(burning(f,0,1));Save before=f.service.read(f.combat);
            if(args[0].equals("corrupt")) {
                var rows=f.runtime.rows("s2_combat_spell_damage_source");var row=rows.getLast();
                if(args[1].equals("missing"))rows.removeLast();else if(args[1].equals("extra"))rows.add(new LinkedHashMap<>(row));else row.put("ability_modifier",1);
                f.runtime.saveCommitted();int rolls=f.rolls,writes=f.runtime.writes;assertEquals(Status.REJECTED,f.service.execute(request).status());assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);assertThrows(java.sql.SQLException.class,()->f.service.read(f.combat));return;
            }
            f.nextRolls.addAll(suffix?List.of(7,1,1,2,8,1):List.of(3,4,4));int rolls=f.rolls;
            if(args[0].equals("rollback")) {
                int count=f.runtime.rows(args[1]).size();f.runtime.afterWrite=db->{if(db.rows(args[1]).size()>count)throw new IllegalStateException("spell suffix injected failure");};
                assertEquals(Status.REJECTED,f.service.execute(request).status());assertEquals(before,f.service.read(f.combat));assertEquals(rolls+(suffix?6:3),f.rolls);
            } else {
                f.runtime.lostCommit=Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);assertEquals(Status.UNKNOWN,f.service.execute(request).status());f.source.unavailable(true);int writes=f.runtime.writes;
                assertEquals(args[1].equals("COMMITTED")?Status.REPLAYED:Status.NOT_COMMITTED,f.service.inspect(request).status());assertEquals(writes,f.runtime.writes);assertEquals(rolls+6,f.rolls);assertTrue(BusinessWriteTransactions.isBlocked());
            }
        }
    }
}
