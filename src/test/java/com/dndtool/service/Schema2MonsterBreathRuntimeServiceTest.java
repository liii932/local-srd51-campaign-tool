package com.dndtool.service;

import com.dndtool.module.composition.Schema2MonsterFixtures;
import com.dndtool.module.composition.Schema2SpellFixtures;
import com.dndtool.persistence.BusinessWriteTransactions;
import com.dndtool.persistence.Schema2RuntimeJdbcFixture;
import com.dndtool.service.Schema2CombatRuntimeService.*;
import com.dndtool.service.Schema2CombatRuntimeService.Status;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.testing.IsolatedJvmTest;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2MonsterFixtures.*;
import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual source/preparation, snapshots, character and combat transactions, including saved continuation cuts. */
class Schema2MonsterBreathRuntimeServiceTest {
    private static final UUID H=id(3),I=id(4),M=id(5);
    private static final List<Integer> SHARED=List.of(1,2,1,2,2,3),SAVES_AND_CON=List.of(9,1,10,1,1,2,9);
    private static UUID id(int value){return UUID.fromString("00000000-0000-4000-8000-"+String.format(Locale.ROOT,"%012d",value));}

    private static Schema2CombatRuntimeServiceTest.Fixture fixture(boolean initialize,boolean heldSpell)throws Exception {
        var f=new Schema2CombatRuntimeServiceTest.Fixture(initialize,Schema2MonsterFixtures.spellPackages(),Schema2SpellFixtures.initialChoices());
        if(initialize&&heldSpell){
            for(UUID actor:List.of(f.a,f.b)){
                UUID focus=Schema2SpellRuntimeServiceTest.focus(actor);var owner=f.members.stream().filter(m->m.characterId().equals(actor)).findFirst().orElseThrow();
                var acquire=new Schema2CharacterRuntimeService.Request(focus,owner.identity(),f.campaign,actor,owner.preparationOperationId(),f.binding,f.character(actor).version(),equipment("ITEM_ACQUIRED",focus,HOLY_SYMBOL,"CARRIED"),Map.of());
                assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(acquire).status());
                assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(actor,equipment("ITEM_EQUIPPED",focus,null,"OFF_HAND"))).status());
            }
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(f.b,equipment("ITEM_UNEQUIPPED",f.items.get(f.b),null,null))).status());
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(f.b,effect(TEMPORARY,"REPLACE"))).status());
            assertEquals(5,f.character(f.b).health().temporaryHitPoints());
        }
        return f;
    }
    private static List<UUID> ties(Schema2CombatRuntimeServiceTest.Fixture f){return List.of(f.a,f.b,H,I,M);}
    private static void start(Schema2CombatRuntimeServiceTest.Fixture f,boolean sourceFirst)throws Exception {
        f.nextRolls.addAll(sourceFirst?List.of(1,1,20,5,4):List.of(20,15,10,5,4));
        f.commit(Command.start(List.of(new Deployment(f.a,new Position(1,0),0),new Deployment(f.b,new Position(2,0),0)),
                List.of(new MonsterDeployment(H,HELL_HOUND,new Position(0,0),1),new MonsterDeployment(I,HELL_HOUND,new Position(2,1),1),new MonsterDeployment(M,MUMMY,new Position(3,0),1)),ties(f)));
        assertTrue(f.nextRolls.isEmpty());assertEquals(sourceFirst?H:f.a,f.battle.activeActor());
    }
    private static void readyThenSource(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {
        f.commit(Schema2SpellRuntimeServiceTest.bless(f,f.a,List.of(f.a,f.b)));f.commit(Command.action(Kind.END_TURN,f.a));
        var held=new CastInput(CASTING,CURE_WOUNDS,List.of(f.a),null);
        f.commit(Command.ready(f.b,new ReadyInput(new ReadyTrigger(TriggerKind.MONSTER_ACTION_COMPLETED,H,null),ReadyResponseKind.SPELL,null,null,List.of(),held)));
        f.commit(Command.action(Kind.END_TURN,f.b));assertEquals(H,f.battle.activeActor());
    }
    private static Schema2CombatRuntimeServiceTest.Fixture preparedBreath()throws Exception {var f=fixture(true,true);start(f,false);readyThenSource(f);return f;}
    private static Request release(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {return f.request(Command.breath(H,FIRE_BREATH,0));}
    private static Request continueRaw(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {Flow flow=f.battle.activeFlow();return f.request(Command.continueBreath(H,flow.flowKey(),flow.leafFrameKey()));}
    private static Request readyRelease(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {
        Flow flow=f.battle.activeFlow();Window window=flow.windows().getLast();Opportunity offer=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();
        assertEquals(f.b,offer.actorId());return f.request(Command.releaseReady(f.b,offer.options().getFirst().readyKey(),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),offer.opportunityKey()));
    }
    private static Request finishAfter(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {Flow flow=f.battle.activeFlow();return f.request(new Command(Kind.CONTINUE_FLOW,H,null,null,List.of(),List.of(),List.of(),flow.flowKey(),flow.leafFrameKey(),flow.windows().getLast().windowKey(),null));}
    private static void rollShared(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {f.nextRolls.addAll(SHARED);f.commit(release(f));assertTrue(f.nextRolls.isEmpty());}
    private static void applyShared(Schema2CombatRuntimeServiceTest.Fixture f)throws Exception {f.nextRolls.addAll(SAVES_AND_CON);f.commit(continueRaw(f));assertTrue(f.nextRolls.isEmpty());}
    private static SaveDamageBatch batch(Schema2CombatRuntimeServiceTest.Fixture f){return f.battle.saveDamageBatches().getFirst();}
    private static MonsterInstance monster(Schema2CombatRuntimeServiceTest.Fixture f,UUID actor){return f.battle.monsters().stream().filter(m->m.monsterId().equals(actor)).findFirst().orElseThrow();}
    private static boolean recharge(Schema2CombatRuntimeServiceTest.Fixture f){return monster(f,H).actionUses().getFirst().available();}
    private static long slots(Schema2CombatRuntimeServiceTest.Fixture f,UUID actor)throws Exception{return Schema2SpellRuntimeServiceTest.slots(f,actor);}
    private static void noReplayWork(Schema2CombatRuntimeServiceTest.Fixture f,Request request)throws Exception {int rolls=f.rolls,writes=f.runtime.writes;Save before=f.service.read(f.combat);assertEquals(Status.REPLAYED,f.service.execute(request).status());assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);assertEquals(before,f.service.read(f.combat));}

    @Test void independentGoldenRestoresBothRawAndReadyOpenCutsWithoutSourceDiceOrImplicitPass()throws Exception {
        JsonObject golden=golden();var f=preparedBreath();int beforeRolls=f.rolls;rollShared(f);
        assertEquals(beforeRolls+6,f.rolls);assertEquals(BatchStatus.ROLLED,batch(f).status());assertEquals(ints(golden,"shared_dice"),batch(f).sharedRoll().dice());assertEquals(11,batch(f).sharedRoll().rawTotal());assertEquals(11,batch(f).sharedRoll().effectiveTotal());
        assertEquals(List.of(f.a,f.b,I,M),batch(f).targetProof().targets().stream().map(CreatureRef::creatureId).toList());assertEquals(5,batch(f).targetProof().candidates().size());assertFalse(batch(f).targetProof().candidates().stream().filter(c->c.creature().creatureId().equals(H)).findFirst().orElseThrow().included());
        assertEquals(Schema2CombatState.Stage.SAVE_DAMAGE_ROLLED,f.battle.activeFlow().frames().getFirst().stage());assertEquals(FrameStatus.PENDING,f.battle.activeFlow().frames().getFirst().status());assertTrue(f.battle.activeFlow().windows().isEmpty());assertTrue(batch(f).checks().isEmpty());assertTrue(batch(f).packets().isEmpty());assertFalse(f.actor(H).actionAvailable());assertFalse(recharge(f));assertEquals(9,f.character(f.a).hitPoints());assertEquals(9,f.character(f.b).hitPoints());assertEquals(5,f.character(f.b).health().temporaryHitPoints());
        Save raw=f.service.read(f.combat);var restored=fixture(false,false);restored.source.unavailable(true);assertEquals(Status.COMMITTED,restored.service.restore(raw).status());assertEquals(0,restored.rolls);assertEquals(raw,restored.service.read(f.combat));restored.battle=raw.combat().current();applyShared(restored);
        assertEquals(BatchStatus.COMPLETED,batch(restored).status());assertEquals(ints(golden,"damage"),batch(restored).packets().stream().map(DamagePacket::damageAmount).toList());assertEquals(ints(golden,"hp_after"),batch(restored).packets().stream().map(DamagePacket::afterHitPoints).toList());assertEquals(ints(golden,"temp_after"),batch(restored).packets().stream().map(DamagePacket::afterTemporaryHitPoints).toList());
        var dex=batch(restored).checks().stream().filter(c->c.purpose().equals("BREATH_DEX")).toList();assertEquals(List.of(11,12,2,1),dex.stream().map(BatchCheck::total).toList());assertEquals(List.of(false,true,false,false),dex.stream().map(BatchCheck::success).toList());assertEquals(ConcentrationDamageStatus.FORCED_ENDED,batch(restored).concentrationObligations().getFirst().status());
        var con=batch(restored).checks().stream().filter(c->c.purpose().equals("CONCENTRATION_CON")).toList();assertEquals(1,con.size());assertEquals(1,con.getFirst().targetOrdinal());assertEquals(10,con.getFirst().difficultyClass());assertEquals(10,con.getFirst().total());assertTrue(con.getFirst().success());assertNull(con.getFirst().blessingEffectKey());assertNull(con.getFirst().blessingD4());
        assertEquals(EffectStatus.ENDED,restored.battle.ongoingEffects().getFirst().status());assertEquals("INCAPACITATED",restored.battle.ongoingEffects().getFirst().endReason());assertEquals(ReadyStatus.ARMED,restored.battle.readyPreparations().getFirst().status());Window open=restored.battle.activeFlow().windows().getLast();assertEquals(WindowStatus.OPEN,open.status());assertEquals(TriggerKind.MONSTER_ACTION_COMPLETED,open.trigger().kind());assertEquals(H,open.trigger().subjectId());
        Save after=restored.service.read(f.combat);var target=fixture(false,false);target.source.unavailable(true);assertEquals(Status.COMMITTED,target.service.restore(after).status());assertEquals(Status.REPLAYED,target.service.inspectRestore(after).status());assertEquals(after,target.service.read(f.combat));assertEquals(0,target.rolls);target.battle=after.combat().current();
        var packets=batch(target).packets();long aSlots=slots(target,target.a),bSlots=slots(target,target.b);long second=Schema2SpellRules.second(target.character(target.a));target.nextRolls.add(golden.get("after_ready_healing_die").getAsInt());target.commit(readyRelease(target));assertEquals(1,target.rolls);assertEquals(golden.get("after_ready_a_hp").getAsInt(),target.character(target.a).hitPoints());assertEquals(WindowStatus.CLOSED,target.battle.activeFlow().windows().getLast().status());assertFalse(target.actor(target.b).reactionAvailable());
        target.commit(finishAfter(target));assertNull(target.battle.activeFlow());assertEquals(packets,batch(target).packets());assertEquals(aSlots,slots(target,target.a));assertEquals(bSlots,slots(target,target.b));assertEquals(second,Schema2SpellRules.second(target.character(target.a)));assertFalse(target.actor(H).actionAvailable());assertFalse(recharge(target));assertTrue(target.actor(target.a).prone());assertEquals("GROUND",target.character(target.a).equipment().stream().filter(i->i.itemId().equals(target.items.get(target.a))).findFirst().orElseThrow().location());assertEquals(EffectStatus.ENDED,target.battle.ongoingEffects().getFirst().status());
        Save completed=target.service.read(target.combat);assertEquals(completed,target.service.read(target.combat));assertEquals(1,target.rolls);
    }

    @Test void releaseApplyAndReadyRequestsReplayWithoutDiceWritesCostsOrRepeatedPackets()throws Exception {
        var f=preparedBreath();Request release=release(f);f.nextRolls.addAll(SHARED);f.commit(release);noReplayWork(f,release);
        Request apply=continueRaw(f);f.nextRolls.addAll(SAVES_AND_CON);f.commit(apply);noReplayWork(f,release);noReplayWork(f,apply);var packets=batch(f).packets();
        Request ready=readyRelease(f);f.nextRolls.add(4);f.commit(ready);noReplayWork(f,ready);Request finish=finishAfter(f);f.commit(finish);noReplayWork(f,finish);assertEquals(packets,batch(f).packets());assertEquals(1,slots(f,f.a));assertEquals(1,slots(f,f.b));assertEquals(6,f.character(f.a).hitPoints());
    }

    @Test void lateCharacterVersionIsValidatedBeforeAnyBreathRollOrResourceWrite()throws Exception {
        var f=preparedBreath();Request valid=release(f);var versions=new LinkedHashMap<>(valid.expectedCharacterVersions());versions.put(f.b,versions.get(f.b)-1);var stale=new Request(valid.operationId(),valid.combatId(),valid.campaignId(),valid.rules(),valid.members(),valid.expectedVersion(),versions,valid.command());
        Save before=f.service.read(f.combat);int rolls=f.rolls,writes=f.runtime.writes;assertEquals("S2_COMBAT_STALE_CHARACTER",f.service.execute(stale).code());assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);assertEquals(before,f.service.read(f.combat));assertTrue(recharge(f));assertTrue(f.actor(H).actionAvailable());assertEquals(Status.REJECTED,f.service.execute(f.request(Command.breath(H,new com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity(HELL_HOUND,"actions","monster_base","bite"),0))).status());assertEquals(rolls,f.rolls);assertEquals(writes,f.runtime.writes);
    }

    @Test void ordinaryWeaponAttacksUpdateTheRealMonsterHealthOwnerAndApplyTheActualDeliveryDefense()throws Exception {
        var f=fixture(true,false);start(f,false);f.commit(Command.action(Kind.BEGIN_ATTACK,f.a));f.nextRolls.addAll(List.of(15,4));f.commit(Command.attack(f.a,H,f.items.get(f.a)));assertEquals(39,monster(f,H).hitPoints());assertFalse(monster(f,H).dead());assertEquals(45,monster(f,I).hitPoints());
        f.commit(Command.action(Kind.END_TURN,f.a));f.commit(Command.action(Kind.BEGIN_ATTACK,f.b));f.nextRolls.addAll(List.of(15,4));f.commit(Command.attack(f.b,M,f.items.get(f.b)));assertEquals(55,monster(f,M).hitPoints());assertTrue(f.nextRolls.isEmpty());assertEquals(9,f.character(f.a).hitPoints());assertEquals(9,f.character(f.b).hitPoints());
        Save saved=f.service.read(f.combat);var restored=fixture(false,false);restored.source.unavailable(true);assertEquals(Status.COMMITTED,restored.service.restore(saved).status());assertEquals(saved,restored.service.read(f.combat));assertEquals(0,restored.rolls);
    }

    @Test void depletedBreathOnlyRechargesAtTheSubsequentActualOwnTurn()throws Exception {
        var f=fixture(true,false);start(f,true);int rolls=f.rolls;f.commit(Command.breath(H,FIRE_BREATH,4));assertEquals(BatchStatus.NO_TARGETS,batch(f).status());assertFalse(recharge(f));assertEquals(rolls,f.rolls);
        while(!f.battle.initiative().get((f.battle.activeOrdinal()+1)%f.battle.initiative().size()).characterId().equals(H))f.commit(Command.action(Kind.END_TURN,f.battle.activeActor()));
        assertFalse(recharge(f));assertEquals(rolls,f.rolls);f.nextRolls.add(5);var turn=f.commit(Command.action(Kind.END_TURN,f.battle.activeActor()));assertEquals(H,f.battle.activeActor());assertTrue(recharge(f));assertTrue(f.actor(H).actionAvailable());assertEquals(rolls+1,f.rolls);assertEquals(List.of("MONSTER_RECHARGE"),turn.event().resolution().rolls().stream().map(Roll::purpose).toList());assertTrue(f.nextRolls.isEmpty());
    }

    @Test void shortAndLongMonsterRestsAdvanceTheSharedClockWithoutHealingAndReentryInterruptsTheNextRest()throws Exception {
        var f=fixture(true,false);start(f,false);f.commit(Command.action(Kind.BEGIN_ATTACK,f.a));f.nextRolls.addAll(List.of(15,4));f.commit(Command.attack(f.a,H,f.items.get(f.a)));f.commit(Command.action(Kind.END_TURN,f.a));f.commit(Command.action(Kind.END_TURN,f.b));f.commit(Command.breath(H,FIRE_BREATH,4));f.commit(Command.action(Kind.END_COMBAT,H));assertFalse(recharge(f));long start=Schema2SpellRules.second(f.character(f.a));
        for(var kind:List.of(MonsterRestKind.SHORT,MonsterRestKind.LONG)){f.commit(Command.monsterRest(Kind.BEGIN_MONSTER_REST,H,null,kind));var rest=f.battle.monsterRests().getLast();long before=Schema2SpellRules.second(f.character(f.a));int rolls=f.rolls;f.commit(Command.monsterRest(Kind.COMPLETE_MONSTER_REST,H,rest.restKey(),kind));assertEquals(before+(kind==MonsterRestKind.SHORT?3600:28800),Schema2SpellRules.second(f.character(f.a)));assertEquals(Schema2SpellRules.second(f.character(f.a)),Schema2SpellRules.second(f.character(f.b)));assertEquals(MonsterRestStatus.COMPLETED,f.battle.monsterRests().getLast().status());assertTrue(recharge(f));assertEquals(39,monster(f,H).hitPoints());assertEquals(rolls,f.rolls);}
        assertEquals(start+32400,Schema2SpellRules.second(f.character(f.a)));f.commit(Command.monsterRest(Kind.BEGIN_MONSTER_REST,H,null,MonsterRestKind.SHORT));var interrupted=f.battle.monsterRests().getLast();Save pending=f.service.read(f.combat);var restored=fixture(false,false);restored.source.unavailable(true);assertEquals(Status.COMMITTED,restored.service.restore(pending).status());restored.battle=pending.combat().current();assertEquals(0,restored.rolls);
        restored.nextRolls.addAll(List.of(1,1,20,5,4));restored.commit(Command.reenter(restored.a,ties(restored)));assertEquals(MonsterRestStatus.INTERRUPTED,restored.battle.monsterRests().getLast().status());assertEquals(5,restored.rolls);restored.commit(Command.action(Kind.END_COMBAT,H));int writes=restored.runtime.writes;assertEquals(Status.REJECTED,restored.service.execute(restored.request(Command.monsterRest(Kind.COMPLETE_MONSTER_REST,H,interrupted.restKey(),MonsterRestKind.SHORT))).status());assertEquals(5,restored.rolls);assertEquals(writes,restored.runtime.writes);
    }

    @Test void rawSharedMetadataAndCompleteCandidateRowsRejectMissingExtraAndAlteredStorage()throws Exception {for(String table:List.of("s2_combat_save_damage_batch","s2_combat_breath_candidate"))for(String mutation:List.of("missing","extra","changed"))IsolatedJvmTest.run(FaultProbe.class,"corrupt","RAW",table,mutation);}
    @Test void appliedPacketsAndMonsterStateRejectMissingExtraAndAlteredStorage()throws Exception {for(String table:List.of("s2_combat_breath_packet","s2_combat_monster"))for(String mutation:List.of("missing","extra","changed"))IsolatedJvmTest.run(FaultProbe.class,"corrupt","APPLY",table,mutation);}
    @Test void actualMonsterRestHistoryRejectsMissingExtraAndAlteredStorage()throws Exception {for(String mutation:List.of("missing","extra","changed"))IsolatedJvmTest.run(FaultProbe.class,"corrupt","REST","s2_combat_monster_rest",mutation);}
    @Test void releaseAndLateApplyFailuresRollbackTheWholeSuffixWhilePreservingTheAcceptedRawPrefix()throws Exception {for(String point:List.of("batch","event"))IsolatedJvmTest.run(FaultProbe.class,"rollback","RAW",point);for(String point:List.of("late_character","packet","event"))IsolatedJvmTest.run(FaultProbe.class,"rollback","APPLY",point);}
    @Test void unknownReleaseAndApplyOutcomesAreInspectedWithoutBlindRerollsOrRepeatedWrites()throws Exception {for(String phase:List.of("RAW","APPLY"))for(String outcome:List.of("COMMITTED","ROLLED_BACK"))IsolatedJvmTest.run(FaultProbe.class,"unknown",phase,outcome);}

    /** Global write admission failure is isolated to this child process; production has no reset hook. */
    public static final class FaultProbe {
        public static void main(String[] args)throws Exception {
            String mode=args[0],phase=args[1];Schema2CombatRuntimeServiceTest.Fixture f;
            if(phase.equals("REST")){f=fixture(true,false);start(f,true);f.commit(Command.breath(H,FIRE_BREATH,4));f.commit(Command.action(Kind.END_COMBAT,H));f.commit(Command.monsterRest(Kind.BEGIN_MONSTER_REST,H,null,MonsterRestKind.SHORT));}
            else {f=preparedBreath();if(phase.equals("APPLY"))rollShared(f);}
            Request request=phase.equals("RAW")?release(f):phase.equals("APPLY")?continueRaw(f):f.request(Command.monsterRest(Kind.COMPLETE_MONSTER_REST,H,f.battle.monsterRests().getLast().restKey(),MonsterRestKind.SHORT));
            Save before=f.service.read(f.combat);int rolls=f.rolls;
            if(mode.equals("corrupt")){
                if(phase.equals("RAW"))rollShared(f);else if(phase.equals("APPLY"))applyShared(f);
                Request next=phase.equals("RAW")?continueRaw(f):phase.equals("APPLY")?readyRelease(f):request;
                var rows=f.runtime.rows(args[2]);assertFalse(rows.isEmpty());Map<String,Object> row=rows.getLast();
                switch(args[3]){case "missing"->rows.removeLast();case "extra"->rows.add(new LinkedHashMap<>(row));case "changed"->{String column=switch(args[2]){case "s2_combat_save_damage_batch"->"raw_total";case "s2_combat_breath_candidate"->"x";case "s2_combat_breath_packet"->"damage_amount";case "s2_combat_monster"->"hit_points";default->"completes_at_second";};Object value=row.get(column);row.put(column,value instanceof Long n?n+1:((Number)value).intValue()+1);}default->throw new AssertionError(args[3]);}
                f.runtime.saveCommitted();int writes=f.runtime.writes;rolls=f.rolls;assertThrows(SQLException.class,()->f.service.read(f.combat));assertEquals(Status.REJECTED,f.service.execute(next).status());assertEquals(writes,f.runtime.writes);assertEquals(rolls,f.rolls);return;
            }
            f.nextRolls.addAll(phase.equals("RAW")?SHARED:SAVES_AND_CON);int draws=phase.equals("RAW")?6:7;
            if(mode.equals("rollback")){
                String point=args[2],table=point.equals("batch")?"s2_combat_save_damage_batch":point.equals("packet")?"s2_combat_breath_packet":point.equals("late_character")?"s2_character_combat_input":"s2_combat_event";int baseline=f.runtime.rows(table).size();
                f.runtime.afterWrite=db->{var rows=db.rows(table);if(rows.size()<=baseline)return;var last=rows.getLast();boolean hit=point.equals("late_character")?Arrays.equals(bytes(f.b),(byte[])last.get("character_id")):point.equals("packet")?text(last.get("phase")).equals("AFTER")&&((Number)last.get("packet_index")).intValue()==3:point.equals("batch")?text(last.get("phase")).equals("AFTER"):true;if(hit)throw new IllegalStateException("Injected late monster transaction write");};
                assertEquals(Status.REJECTED,f.service.execute(request).status());assertEquals(rolls+draws,f.rolls);assertTrue(f.nextRolls.isEmpty());assertEquals(before,f.service.read(f.combat));assertTrue(BusinessWriteTransactions.isBlocked());
                Save after=f.service.read(f.combat);assertEquals(before.combat().current().saveDamageBatches(),after.combat().current().saveDamageBatches());assertEquals(before.characters(),after.characters());
            } else {
                f.runtime.lostCommit=Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[2]);assertEquals(Status.UNKNOWN,f.service.execute(request).status());assertEquals(rolls+draws,f.rolls);assertTrue(f.nextRolls.isEmpty());f.source.unavailable(true);int writes=f.runtime.writes;int actualRolls=f.rolls;
                var inspected=f.service.inspect(request);assertEquals(args[2].equals("COMMITTED")?Status.REPLAYED:Status.NOT_COMMITTED,inspected.status());assertEquals(writes,f.runtime.writes);assertEquals(actualRolls,f.rolls);assertTrue(BusinessWriteTransactions.isBlocked());
                if(inspected.status()==Status.REPLAYED){var accepted=inspected.event().after();assertEquals(phase.equals("RAW")?BatchStatus.ROLLED:BatchStatus.COMPLETED,accepted.saveDamageBatches().getFirst().status());assertFalse(accepted.monsters().stream().filter(m->m.monsterId().equals(H)).findFirst().orElseThrow().actionUses().getFirst().available());}else assertEquals(before,f.service.read(f.combat));
            }
        }
    }
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    private static String text(Object raw){return new String((byte[])raw,StandardCharsets.US_ASCII);}
    private static JsonObject golden()throws Exception {try(var input=Schema2MonsterBreathRuntimeServiceTest.class.getResourceAsStream("/schema2-monster/breath-runtime-golden.json")){assertNotNull(input);return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();}}
    private static List<Integer> ints(JsonObject object,String key){List<Integer> values=new ArrayList<>();for(var value:object.getAsJsonArray(key))values.add(value.getAsInt());return values;}
}
