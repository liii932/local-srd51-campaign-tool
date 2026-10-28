package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.Schema2CombatState.Command;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.BASE;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullAbilities;
import static com.dndtool.service.Schema2CheckTestFixture.ITEM_WEAPON;
import static com.dndtool.service.Schema2CheckTestFixture.equipment;
import static com.dndtool.service.Schema2CombatTestFixture.withState;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ReactionSpellRulesTest {
    final Schema2CombatRules rules=new Schema2CombatRules();final ProductionSchema2Projection material=projection();
    final UUID campaign=UUID.randomUUID(),combat=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    final Map<UUID,Context> roster=new HashMap<>();Battle battle;
    Context wizard(UUID actor,int level) {
        var sources=new Schema2InitialProficiencyRules.Sources(new RuleIdentity(BASE,"character.race","race.human"),null,new RuleIdentity(BASE,"character.background","background.guard"),WIZARD);
        var identity=new Identity(UUID.randomUUID(),UUID.randomUUID());
        var plan=new Schema2InitialCharacterPlanService().prepareMaterial(identity,com.dndtool.module.composition.Schema2RulesBinding.from(material),material,new Schema2InitialProficiencyRules.Request(sources,Map.of()));
        var initial=new Schema2InitialCharacterPlanService.InitialPlan(campaign,actor,plan);var growth=new Schema2CharacterProgressionRules();
        State state=growth.initial(material,initial,UUID.randomUUID(),fullAbilities(),GrowthChoices.EMPTY);var book=new Schema2WizardSpellbookRules();
        state=book.apply(material,state,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(SHIELD))));
        List<List<RuleIdentity>> learning=List.of(List.of(spell("detect_magic"),spell("disguise_self")),List.of(spell("expeditious_retreat"),spell("false_life")),List.of(spell("feather_fall"),spell("find_familiar")),List.of(COUNTERSPELL,spell("fog_cloud")));
        for(int next=2;next<=level;next++) {
            GrowthChoices choices=next==2?new GrowthChoices(EVOCATION,Map.of(),null,Map.of()):next==4?new GrowthChoices(null,Map.of("ability.intelligence",2),null,Map.of()):GrowthChoices.EMPTY;
            state=growth.advance(material,growth.prepareAdvance(material,state,WIZARD,next,"FIXED_AVERAGE",choices),null);
            state=book.apply(material,state,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_ADVANCED",new SpellbookInput(CASTING,learning.get(next-2),state.spellbook().prepared())));
        }
        if(level==5) {
            var rests=new Schema2CharacterRestRules();
            state=rests.apply(material,state,rest("REST_STARTED",new RestInput("LONG_REST",null,0,0,Map.of())),null);
            state=rests.apply(material,state,rest("TIME_ADVANCED",new RestInput(null,"SLEEP",480,0,Map.of())),null);
            state=rests.apply(material,state,rest("REST_COMPLETED",new RestInput(null,null,0,0,Map.of())),null);
            state=book.apply(material,state,JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(COUNTERSPELL,SHIELD))));
        }
        UUID weapon=UUID.randomUUID();var equipmentRules=new Schema2CharacterEquipmentRules();state=equipmentRules.apply(material,state,equipment("ITEM_ACQUIRED",weapon,ITEM_WEAPON,"CARRIED"),weapon);state=equipmentRules.apply(material,state,equipment("ITEM_EQUIPPED",weapon,null,"MAIN_HAND"),UUID.randomUUID());
        return new Context(material,initial,state);
    }
    static JdbcSchema2CharacterRuntimeRepository.Command rest(String kind,RestInput input) {return new JdbcSchema2CharacterRuntimeRepository.Command(kind,null,0,null,null,null,GrowthChoices.EMPTY,input);}
    Resolution apply(Command command,int...rolls) {
        UUID op=UUID.randomUUID();var before=battle;var prepared=rules.preflight(op,combat,campaign,roster,before,command);AtomicInteger i=new AtomicInteger();
        var result=rules.resolve(prepared,sides->{assertTrue(i.get()<rolls.length,"unexpected d"+sides);return rolls[i.getAndIncrement()];});assertEquals(rolls.length,i.get());rules.verify(op,combat,campaign,roster,before,command,result);
        for(HealthChange change:result.healthChanges())roster.put(change.characterId(),withState(roster.get(change.characterId()),change.after()));battle=result.after();return result;
    }
    void start(int level,boolean third) {
        roster.put(a,wizard(a,level));roster.put(b,wizard(b,level));if(third)roster.put(c,wizard(c,level));
        var deployments=new ArrayList<>(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(1,0),1)));if(third)deployments.add(new Deployment(c,new Position(0,1),2));
        apply(Command.start(deployments,third?List.of(a,b,c):List.of(a,b)),third?new int[]{20,15,10}:new int[]{20,10});
    }
    UUID item(UUID actor) {return roster.get(actor).character().equipment().getFirst().itemId();}
    long slot(UUID actor,RuleIdentity source) {return roster.get(actor).character().resources().stream().filter(v->v.identity().equals(source)).findFirst().orElseThrow().current();}
    Window window() {Flow f=battle.activeFlow();Frame leaf=f.frames().stream().filter(v->v.frameKey().equals(f.leafFrameKey())).findFirst().orElseThrow();return f.windows().stream().filter(w->w.windowKey().equals(leaf.windowKey())).findFirst().orElseThrow();}
    Command spellReaction(Kind kind) {
        Flow f=battle.activeFlow();Window w=window();Opportunity o=w.opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();boolean shield=kind==Kind.DECLARE_SHIELD;
        UUID target=shield?o.actorId():w.trigger().subjectId();CastInput input=new CastInput(CASTING,shield?SHIELD:COUNTERSPELL,List.of(target),null,shield?1:3,shield?null:w.trigger().sourceFrameKey());
        return Command.reactionSpell(kind,o.actorId(),input,f.flowKey(),f.leafFrameKey(),w.windowKey(),o.opportunityKey());
    }
    Command resume() {Flow f=battle.activeFlow();Window w=window();Frame owner=f.frames().stream().filter(v->v.frameKey().equals(w.ownerFrameKey())).findFirst().orElseThrow();UUID subject=owner.kind()==FrameKind.CAST?f.casts().stream().filter(v->v.frameKey().equals(owner.frameKey())).findFirst().orElseThrow().actorId():f.attacks().stream().filter(v->v.frameKey().equals(owner.frameKey())).findFirst().orElseThrow().actorId();return new Command(Kind.CONTINUE_FLOW,subject,null,null,List.of(),List.of(),List.of(),f.flowKey(),f.leafFrameKey(),w.windowKey(),null);}
    Command advance() {Flow f=battle.activeFlow();Window w=window();Opportunity o=w.opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();return Command.reaction(Kind.ADVANCE_WINDOW,o.actorId(),null,f.flowKey(),f.leafFrameKey(),w.windowKey(),o.opportunityKey());}

    @Test void shieldSavesInitialHitBeforeAnyDamageAndReusesOriginalAttackDie() {
        start(1,false);apply(Command.action(Kind.BEGIN_ATTACK,a));var hit=apply(Command.attack(a,b,item(a)),10);
        assertEquals(1,hit.rolls().size());assertEquals(AttackStatus.HIT_PENDING_DAMAGE,battle.activeFlow().attacks().getFirst().status());assertTrue(battle.activeFlow().attacks().getFirst().initialHit());assertEquals(0,battle.activeFlow().attacks().getFirst().damageAmount());
        apply(spellReaction(Kind.DECLARE_SHIELD));assertEquals(1,slot(b,SLOT1));assertEquals(1,battle.shields().size());assertTrue(battle.concentrationHolders().isEmpty());
        var finish=apply(resume());assertTrue(finish.rolls().isEmpty());assertNull(battle.activeFlow());AttackAttempt attack=battle.flows().getLast().attacks().getFirst();assertEquals(10,attack.selectedD20());assertTrue(attack.initialHit());assertFalse(attack.hit());assertEquals(attack.armorClass()+5,attack.finalArmorClass());assertTrue(attack.damageDice().isEmpty());assertEquals(1,battle.combatants().stream().filter(v->v.characterId().equals(a)).findFirst().orElseThrow().sequence().performed());
        apply(Command.action(Kind.END_TURN,a));assertEquals(EffectStatus.ENDED,battle.shields().getFirst().status());assertTrue(battle.combatants().stream().filter(v->v.characterId().equals(b)).findFirst().orElseThrow().reactionAvailable());
    }
    @Test void shieldNeverTurnsNaturalTwentyIntoMissOrRepeatsAttackRoll() {
        start(1,false);apply(Command.action(Kind.BEGIN_ATTACK,a));apply(Command.attack(a,b,item(a)),20);apply(spellReaction(Kind.DECLARE_SHIELD));var damage=apply(resume(),1,1);
        assertEquals(List.of("DAMAGE","DAMAGE"),damage.rolls().stream().map(Roll::purpose).toList());var attack=battle.flows().getLast().attacks().getFirst();assertTrue(attack.hit());assertTrue(attack.critical());assertEquals(20,attack.selectedD20());
    }
    @Test void counteredShieldRetainsBothCostsAndReturnsToOriginalDamageSuffix() {
        start(5,false);apply(Command.action(Kind.BEGIN_ATTACK,a));apply(Command.attack(a,b,item(a)),10);apply(spellReaction(Kind.DECLARE_SHIELD));assertEquals(TriggerKind.CAST_BEFORE_COMPLETION,window().trigger().kind());
        apply(spellReaction(Kind.DECLARE_COUNTERSPELL));assertEquals(1,slot(a,SLOT3));assertEquals(3,slot(b,SLOT1));apply(resume());assertTrue(battle.shields().isEmpty());
        var damage=apply(resume(),3);assertEquals(1,damage.rolls().size());assertEquals("DAMAGE",damage.rolls().getFirst().purpose());assertNull(battle.activeFlow());
        var casts=battle.flows().getLast().casts();assertEquals(2,casts.size());assertEquals(CastStatus.COUNTERED,casts.getFirst().status());assertEquals(CastStatus.RESOLVED,casts.getLast().status());assertEquals(10,battle.flows().getLast().attacks().getFirst().selectedD20());
    }
    @Test void innerCounterspellResolvesFirstAndRestoresShieldWithoutRefundingOuterCounter() {
        start(5,true);apply(Command.action(Kind.BEGIN_ATTACK,a));apply(Command.attack(a,b,item(a)),10);apply(spellReaction(Kind.DECLARE_SHIELD));apply(spellReaction(Kind.DECLARE_COUNTERSPELL));apply(spellReaction(Kind.DECLARE_COUNTERSPELL));
        assertEquals(1,slot(a,SLOT3));assertEquals(1,slot(c,SLOT3));apply(resume());assertEquals(c,window().opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow().actorId());apply(advance());apply(resume());apply(resume());
        assertNull(battle.activeFlow());Flow flow=battle.flows().getLast();assertEquals(List.of(CastStatus.RESOLVED,CastStatus.COUNTERED,CastStatus.RESOLVED),flow.casts().stream().map(CastAttempt::status).toList());assertEquals(1,battle.shields().size());assertFalse(flow.attacks().getFirst().hit());assertTrue(flow.windows().stream().allMatch(w->w.status()==WindowStatus.CONSUMED));assertTrue(flow.frames().stream().filter(f->f.kind()!=FrameKind.REACTION_SELECTION).allMatch(f->f.status()==FrameStatus.TERMINAL));
    }
}
