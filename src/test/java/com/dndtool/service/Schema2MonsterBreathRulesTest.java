package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2MonsterFixtures.*;
import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullPlan;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullAbilities;
import static com.dndtool.service.Schema2CombatTestFixture.withState;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual author -> character/spell/health -> monster action consumer; no injected batch/effect/HP model. */
class Schema2MonsterBreathRulesTest {
    static UUID id(int value) {return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",value));}
    static final UUID A=id(1),B=id(2),H=id(3),I=id(4),M=id(5);
    static final class Fixture {
        final Schema2CombatRules rules=new Schema2CombatRules();final UUID campaign=UUID.randomUUID(),combat=UUID.randomUUID();
        final ProductionSchema2Projection material;final Map<UUID,Context> roster=new LinkedHashMap<>();Battle battle;
        Fixture(boolean spell) {this(spell,spell?spellProjection():Schema2MonsterFixtures.projection());}
        Fixture(boolean spell,ProductionSchema2Projection projection) {
            material=projection;
            for(UUID actor:List.of(A,B)) {
                Context context;
                if(spell) {
                    var plan=fullPlan(material,new Identity(UUID.randomUUID(),UUID.randomUUID()),campaign,actor);
                    State state=new Schema2CharacterProgressionRules().initial(material,plan,UUID.randomUUID(),fullAbilities(),initialChoices());
                    var eq=new Schema2CharacterEquipmentRules();UUID symbol=id(actor.equals(A)?101:102);
                    state=eq.apply(material,state,equipment("ITEM_ACQUIRED",symbol,HOLY_SYMBOL,"CARRIED"),symbol);
                    state=eq.apply(material,state,equipment("ITEM_EQUIPPED",symbol,null,"OFF_HAND"),UUID.randomUUID());
                    if(actor.equals(A)) {UUID weapon=id(201);state=eq.apply(material,state,equipment("ITEM_ACQUIRED",weapon,ITEM_WEAPON,"CARRIED"),weapon);state=eq.apply(material,state,equipment("ITEM_EQUIPPED",weapon,null,"MAIN_HAND"),UUID.randomUUID());}
                    context=new Context(material,plan,state);
                } else context=Schema2CombatTestFixture.context(material,campaign,actor);
                roster.put(actor,context);
            }
        }
        Resolution apply(Command command,int...rolls) {
            UUID operation=UUID.randomUUID();var before=battle;var p=rules.preflight(operation,combat,campaign,roster,before,command);AtomicInteger cursor=new AtomicInteger();
            var result=rules.resolve(p,sides->{assertTrue(cursor.get()<rolls.length,"unexpected d"+sides+" at "+cursor.get());return rolls[cursor.getAndIncrement()];});assertEquals(rolls.length,cursor.get());
            rules.verify(operation,combat,campaign,roster,before,command,result);
            for(var change:result.healthChanges())roster.put(change.characterId(),withState(roster.get(change.characterId()),change.after()));battle=result.after();return result;
        }
        void start(boolean sourceFirst) {apply(Command.start(List.of(new Deployment(A,new Position(1,0),0),new Deployment(B,new Position(2,0),0)),List.of(new MonsterDeployment(H,HELL_HOUND,new Position(0,0),1),new MonsterDeployment(I,HELL_HOUND,new Position(2,1),1),new MonsterDeployment(M,MUMMY,new Position(3,0),1)),sourceFirst?List.of(H,A,B,I,M):List.of(A,B,H,I,M)),sourceFirst?1:20,sourceFirst?1:15,sourceFirst?20:10,5,4);}
        void temporary(UUID actor) {
            var ctx=roster.get(actor);var health=new Schema2CharacterHealthRules();var p=health.prepare(material,ctx.character(),effect(TEMPORARY,"REPLACE"));
            roster.put(actor,withState(ctx,health.resolve(p,List.of(),null,null).state()));
        }
        Command bless() {return Command.cast(A,new CastInput(CASTING,BLESS,List.of(A,B),id(101)));}
        Command ready() {return Command.ready(B,new ReadyInput(new ReadyTrigger(TriggerKind.MONSTER_ACTION_COMPLETED,H,null),ReadyResponseKind.SPELL,null,null,List.of(),new CastInput(CASTING,CURE_WOUNDS,List.of(A),null)));}
        void readyThenSource() {apply(bless());apply(Command.action(Kind.END_TURN,A));apply(ready());apply(Command.action(Kind.END_TURN,B));assertEquals(H,battle.activeActor());}
        Command continueRaw() {var f=battle.activeFlow();return Command.continueBreath(H,f.flowKey(),f.leafFrameKey());}
        Command reaction(Kind kind) {var f=battle.activeFlow();var w=f.windows().getLast();var o=w.opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();return kind==Kind.RELEASE_READY?Command.releaseReady(o.actorId(),o.options().getFirst().readyKey(),f.flowKey(),f.leafFrameKey(),w.windowKey(),o.opportunityKey()):Command.reaction(kind,o.actorId(),null,f.flowKey(),f.leafFrameKey(),w.windowKey(),o.opportunityKey());}
        Command finishAfter() {var f=battle.activeFlow();return new Command(Kind.CONTINUE_FLOW,H,null,null,List.of(),List.of(),List.of(),f.flowKey(),f.leafFrameKey(),f.windows().getLast().windowKey(),null);}
    }
    @Test void independentGoldenRunsRealBlessTemporaryHealthMonsterDefensesAndAfterBatchReady() throws Exception {
        JsonObject golden;try(var input=getClass().getResourceAsStream("/schema2-monster/breath-runtime-golden.json")) {assertNotNull(input);golden=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();}
        var f=new Fixture(true);f.temporary(B);f.start(false);f.readyThenSource();
        var released=f.apply(Command.breath(H,FIRE_BREATH,0),1,2,1,2,2,3);assertTrue(released.healthChanges().isEmpty());
        var pending=f.battle;assertEquals(BatchStatus.ROLLED,pending.saveDamageBatches().getFirst().status());assertEquals(List.of(A,B,I,M),pending.saveDamageBatches().getFirst().targetProof().targets().stream().map(CreatureRef::creatureId).toList());
        var completed=f.apply(f.continueRaw(),9,1,10,1,1,2,9);var batch=f.battle.saveDamageBatches().getFirst();
        assertEquals(BatchStatus.COMPLETED,batch.status());assertEquals(ints(golden,"damage"),batch.packets().stream().map(DamagePacket::damageAmount).toList());
        assertEquals(ints(golden,"hp_after"),batch.packets().stream().map(DamagePacket::afterHitPoints).toList());assertEquals(ints(golden,"temp_after"),batch.packets().stream().map(DamagePacket::afterTemporaryHitPoints).toList());
        assertEquals(2,completed.healthChanges().size());assertEquals(EffectStatus.ENDED,f.battle.ongoingEffects().getFirst().status());
        assertEquals(ConcentrationDamageStatus.FORCED_ENDED,batch.concentrationObligations().getFirst().status());var con=batch.checks().getLast();assertEquals("CONCENTRATION_CON",con.purpose());assertNull(con.blessingEffectKey());assertTrue(con.success());
        assertNotNull(f.battle.activeFlow());assertEquals(TriggerKind.MONSTER_ACTION_COMPLETED,f.battle.activeFlow().windows().getLast().trigger().kind());
        var packets=batch.packets();var healing=f.apply(f.reaction(Kind.RELEASE_READY),golden.get("after_ready_healing_die").getAsInt());assertEquals(golden.get("after_ready_a_hp").getAsInt(),f.roster.get(A).character().hitPoints());assertFalse(healing.healthChanges().isEmpty());
        f.apply(f.finishAfter());assertNull(f.battle.activeFlow());assertEquals(packets,f.battle.saveDamageBatches().getFirst().packets());assertTrue(f.battle.combatants().stream().filter(c->c.characterId().equals(A)).findFirst().orElseThrow().prone());assertEquals("GROUND",f.roster.get(A).character().equipment().stream().filter(i->i.itemId().equals(id(201))).findFirst().orElseThrow().location());
    }
    @Test void allConcentrationChecksUseTheSameCutBeforeTheFirstFailureEndsBless() {
        var f=new Fixture(true);f.temporary(B);f.start(false);f.readyThenSource();f.apply(Command.breath(H,FIRE_BREATH,0),1,1,1,1,1,1);
        f.apply(f.continueRaw(),1,1,1,1,1,1,1,1,8,1);var batch=f.battle.saveDamageBatches().getFirst();
        var cons=batch.checks().stream().filter(c->c.purpose().equals("CONCENTRATION_CON")).toList();assertEquals(2,cons.size());assertFalse(cons.getFirst().success());assertTrue(cons.getLast().success());assertEquals(cons.getFirst().blessingEffectKey(),cons.getLast().blessingEffectKey());
        assertEquals(EffectStatus.ENDED,f.battle.ongoingEffects().getFirst().status());assertTrue(f.battle.concentrationHolders().stream().anyMatch(h->h.actorId().equals(B)));
    }
    @Test void realInitialSavingThrowGrantsApplyPbOnceAndUnconsciousDexDoesNotInventD20() {
        var f=new Fixture(false);assertEquals(2,Schema2SavingThrowRules.proficiency(f.roster.get(A),"ability.dexterity"));assertEquals(2,Schema2SavingThrowRules.proficiency(f.roster.get(A),"ability.constitution"));
        f.start(true);f.apply(Command.breath(H,FIRE_BREATH,0),1,1,1,1,1,1);f.apply(f.continueRaw(),9,9,1,1);
        var checks=f.battle.saveDamageBatches().getFirst().checks();assertEquals(2,checks.getFirst().proficiencyBonus());assertEquals(12,checks.getFirst().total());assertTrue(checks.getFirst().success());
        var basis=new Schema2SavingThrowRules.Basis("ability.dexterity",1,2,true,true,true,null);
        var auto=Schema2SavingThrowRules.resolve(basis,0,"BREATH_DEX",A,12,(p,id,s)->{fail("AUTO_FAILURE cannot consume a die");return 1;});assertEquals(CheckMode.AUTO_FAILURE,auto.mode());assertNull(auto.firstD20());
    }
    @Test void emptyAreaStillConsumesActionAndRechargeWithoutAnyDieOrHealthPacket() {
        var f=new Fixture(false);f.start(true);var result=f.apply(Command.breath(H,FIRE_BREATH,4));assertTrue(result.rolls().isEmpty());assertTrue(result.healthChanges().isEmpty());assertNull(f.battle.activeFlow());assertEquals(BatchStatus.NO_TARGETS,f.battle.saveDamageBatches().getFirst().status());assertFalse(Schema2MonsterRules.instance(f.battle,H).actionUses().getFirst().available());
    }
    @Test void rechargeHappensAtTheActualSubsequentOwnTurnAndRestCompletionAdvancesTheSameClock() {
        var f=new Fixture(false);f.start(true);f.apply(Command.breath(H,FIRE_BREATH,4));
        boolean incomingSource;do {incomingSource=f.battle.initiative().get((f.battle.activeOrdinal()+1)%f.battle.initiative().size()).characterId().equals(H);if(incomingSource)f.apply(Command.action(Kind.END_TURN,f.battle.activeActor()),4);else f.apply(Command.action(Kind.END_TURN,f.battle.activeActor()));}while(!incomingSource);assertEquals(H,f.battle.activeActor());assertFalse(Schema2MonsterRules.instance(f.battle,H).actionUses().getFirst().available());
        f.apply(Command.action(Kind.END_COMBAT,H));long before=Schema2SpellRules.second(f.roster.get(A).character());
        f.apply(Command.monsterRest(Kind.BEGIN_MONSTER_REST,H,null,MonsterRestKind.LONG));UUID rest=f.battle.monsterRests().getFirst().restKey();
        var completed=f.apply(Command.monsterRest(Kind.COMPLETE_MONSTER_REST,H,rest,MonsterRestKind.LONG));assertEquals(2,completed.healthChanges().size());assertEquals(before+28800,Schema2SpellRules.second(f.roster.get(A).character()));assertTrue(Schema2MonsterRules.instance(f.battle,H).actionUses().getFirst().available());assertEquals(45,Schema2MonsterRules.instance(f.battle,H).hitPoints());
    }
    @Test void unregisteredActionAndExtraIntentFieldsRejectBeforeAnyRollOrSpend() {
        var f=new Fixture(false);f.start(true);Battle prior=f.battle;
        var blocked=new Schema2CompositionKernel.EntryIdentity(HELL_HOUND,"actions","monster_base","bite");assertThrows(IllegalArgumentException.class,()->f.rules.preflight(UUID.randomUUID(),f.combat,f.campaign,f.roster,f.battle,Command.breath(H,blocked,0)));assertSame(prior,f.battle);assertTrue(Schema2MonsterRules.instance(prior,H).actionUses().getFirst().available());
        assertThrows(IllegalArgumentException.class,()->new BreathInput(FIRE_BREATH,8));
    }
    @Test void unresolvedMonsterOpportunityThreatRejectsEveryMovementPrefixBeforeDice() {
        var f=new Fixture(false);f.start(false);Battle before=f.battle;var move=Command.move(A,List.of(new Position(1,-1)));
        assertEquals("S2_MONSTER_REACTION_UNSUPPORTED",assertThrows(IllegalArgumentException.class,()->f.rules.preflight(UUID.randomUUID(),f.combat,f.campaign,f.roster,f.battle,move)).getMessage());assertSame(before,f.battle);
        var ready=Command.ready(A,new ReadyInput(new ReadyTrigger(TriggerKind.MONSTER_ACTION_COMPLETED,H,null),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,-1)),null));
        assertEquals("S2_MONSTER_REACTION_UNSUPPORTED",assertThrows(IllegalArgumentException.class,()->f.rules.preflight(UUID.randomUUID(),f.combat,f.campaign,f.roster,f.battle,ready)).getMessage());assertTrue(f.battle.combatants().stream().filter(c->c.characterId().equals(A)).findFirst().orElseThrow().actionAvailable());
    }
    @Test void dodgeWithZeroWalkingSpeedCannotGrantDexteritySaveAdvantage() {
        var files=Schema2MonsterFixtures.packages();var changes=Schema2GrantFixtures.json(files.getLast().get("changes.json")).getAsJsonArray();
        for(var element:changes) {var change=element.getAsJsonObject();if(change.has("field")&&change.get("field").getAsString().equals("stat_block"))change.getAsJsonObject("value").addProperty("walk_speed_feet",0);}
        files.getLast().put("changes.json",changes.toString().getBytes(StandardCharsets.UTF_8));var f=new Fixture(false,ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
        f.start(false);f.apply(Command.action(Kind.END_TURN,A));f.apply(Command.action(Kind.END_TURN,B));f.apply(Command.action(Kind.DODGE,H));f.apply(Command.action(Kind.END_TURN,H));assertEquals(I,f.battle.activeActor());
        f.apply(Command.breath(I,FIRE_BREATH,5),1,1,1,1,1,1);var flow=f.battle.activeFlow();f.apply(Command.continueBreath(I,flow.flowKey(),flow.leafFrameKey()),1,1);
        var batch=f.battle.saveDamageBatches().getFirst();assertEquals(List.of(A,H),batch.targetProof().targets().stream().map(CreatureRef::creatureId).toList());var save=batch.checks().getLast();assertFalse(save.advantage());assertNull(save.secondD20());
    }
    @Test void unknownTraitAndUnrepresentableVitalsOrNativeSaveBonusRejectAtAdmission() {
        for(int variant=0;variant<3;variant++) {
            var files=Schema2MonsterFixtures.packages();var changes=Schema2GrantFixtures.json(files.getLast().get("changes.json")).getAsJsonArray();
            for(var element:changes) {var change=element.getAsJsonObject();if(change.has("field")&&change.get("field").getAsString().equals("stat_block")) {
                var block=change.getAsJsonObject("value");if(variant==0)block.getAsJsonArray("traits").get(0).getAsJsonObject().addProperty("scope","UNSUPPORTED");
                if(variant==1)block.addProperty("hit_points",65536);
                if(variant==2) {var save=new JsonObject();save.addProperty("ability_key","ability.dexterity");save.addProperty("bonus",8);block.getAsJsonArray("saving_throws").add(save);}
            }}
            files.getLast().put("changes.json",changes.toString().getBytes(StandardCharsets.UTF_8));
            if(variant==1) {assertThrows(IllegalArgumentException.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));continue;}
            var f=new Fixture(false,ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
            var command=Command.start(List.of(new Deployment(A,new Position(1,0),0),new Deployment(B,new Position(2,0),0)),List.of(new MonsterDeployment(H,HELL_HOUND,new Position(0,0),1)),List.of(H,A,B));
            assertThrows(IllegalArgumentException.class,()->f.rules.preflight(UUID.randomUUID(),f.combat,f.campaign,f.roster,null,command));assertNull(f.battle);
        }
    }
    @Test void newMonsterInputsRejectNullMalformedAndOversizedIdentityPartsWithStableErrors() {
        for(var rule:Arrays.asList(null,new Schema2CompositionKernel.RuleIdentity(null,"adventure.monster","monster.hound"),new Schema2CompositionKernel.RuleIdentity("monster_base",null,"monster.hound"),new Schema2CompositionKernel.RuleIdentity("monster_base","adventure.monster",null),new Schema2CompositionKernel.RuleIdentity("mønst","adventure.monster","monster.hound"),new Schema2CompositionKernel.RuleIdentity("monster_base","adventure.monster","monster..hound"),new Schema2CompositionKernel.RuleIdentity("monster_base","adventure.monster","monster."+"a".repeat(121)))) {
            assertEquals("S2_COMBAT_DEPLOYMENT",assertThrows(IllegalArgumentException.class,()->new MonsterDeployment(H,rule,new Position(0,0),1)).getMessage());
            assertEquals("S2_BREATH_COMMAND",assertThrows(IllegalArgumentException.class,()->new BreathInput(new Schema2CompositionKernel.EntryIdentity(rule,"actions","monster_base","fire_breath"),0)).getMessage());
        }
        for(var action:Arrays.asList(null,new Schema2CompositionKernel.EntryIdentity(HELL_HOUND,null,"monster_base","fire_breath"),new Schema2CompositionKernel.EntryIdentity(HELL_HOUND,"actions",null,"fire_breath"),new Schema2CompositionKernel.EntryIdentity(HELL_HOUND,"actions","monster_base",null),new Schema2CompositionKernel.EntryIdentity(HELL_HOUND,"actions","monster_base","a".repeat(65)),new Schema2CompositionKernel.EntryIdentity(HELL_HOUND,"actions","a".repeat(65),"fire_breath")))assertEquals("S2_BREATH_COMMAND",assertThrows(IllegalArgumentException.class,()->new BreathInput(action,0)).getMessage());
        assertEquals("S2_MONSTER_REST_COMMAND",assertThrows(IllegalArgumentException.class,()->new MonsterRestInput(null,null)).getMessage());
        assertEquals("S2_COMBAT_DEPLOYMENT",assertThrows(IllegalArgumentException.class,()->new MonsterDeployment(H,HELL_HOUND,null,1)).getMessage());
        assertEquals("S2_COMBAT_COMMAND",assertThrows(IllegalArgumentException.class,()->Command.start(List.of(),Arrays.asList((MonsterDeployment)null),List.of())).getMessage());
        var bounded=new Schema2CompositionKernel.RuleIdentity("a".repeat(64),"adventure.monster","monster."+"a".repeat(120));
        assertDoesNotThrow(()->new BreathInput(new Schema2CompositionKernel.EntryIdentity(bounded,"actions","a".repeat(64),"a".repeat(64)),7));
    }
    private static List<Integer> ints(JsonObject object,String field) {List<Integer> result=new ArrayList<>();for(var value:object.getAsJsonArray(field))result.add(value.getAsInt());return result;}
}
