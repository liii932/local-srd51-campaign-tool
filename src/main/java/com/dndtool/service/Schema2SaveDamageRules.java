package com.dndtool.service;

import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthResult;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.Stage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Shared save-damage consumer for the finite monster action and real spell producers. */
final class Schema2SaveDamageRules {
    static final String SPACE_POLICY="S2_POINT_CONE_CHEBYSHEV_V1";
    private static final int[][] DIRECTIONS={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
    private final Schema2CharacterHealthRules health=new Schema2CharacterHealthRules();
    static boolean handles(Battle b,Command c) {
        return c.kind()==Kind.MONSTER_BREATH||c.kind()==Kind.CONTINUE_FLOW&&c.windowKey()==null&&b!=null&&b.activeFlow()!=null
                &&b.activeFlow().frames().stream().anyMatch(f->f.frameKey().equals(b.activeFlow().leafFrameKey())&&f.kind()==FrameKind.SAVE_DAMAGE&&f.stage()==Stage.SAVE_DAMAGE_ROLLED);
    }
    void prepare(Map<UUID,Context> roster,Battle b,Command c) {
        if(b==null||b.status()!=Status.ACTIVE||c.actorId()==null||!c.monsterDeployments().isEmpty()||!c.deployments().isEmpty()||!c.tieBreakOrder().isEmpty()
                ||c.targetId()!=null||c.itemId()!=null||!c.path().isEmpty()||c.windowKey()!=null||c.opportunityKey()!=null||c.castInput()!=null||c.readyInput()!=null||c.readyKey()!=null
                ||c.zoneInput()!=null||c.zoneKey()!=null||c.healthInput()!=null||c.seconds()!=0||c.monsterRestInput()!=null)throw invalid("S2_BREATH_COMMAND");
        SaveDamageBatch batch;
        if(c.kind()==Kind.MONSTER_BREATH) {
            if(c.breathInput()==null||c.flowKey()!=null||c.leafKey()!=null||b.activeFlow()!=null)throw invalid("S2_BREATH_COMMAND");
            var monster=Schema2MonsterRules.instance(b,c.actorId());var actor=actor(b,c.actorId());
            if(!b.activeActor().equals(c.actorId()))throw invalid("S2_COMBAT_NOT_CURRENT_TURN");
            if(monster.dead()||monster.hitPoints()==0)throw invalid("S2_COMBAT_ACTOR_INACTIVE");
            if(!actor.actionAvailable())throw invalid("S2_COMBAT_ACTION_UNAVAILABLE");
            if(!c.breathInput().action().rule().equals(monster.monster()))throw invalid("S2_MONSTER_ACTION_UNSUPPORTED");
            Schema2MonsterRules.action(roster,c.breathInput().action());
            if(monster.actionUses().stream().noneMatch(u->u.action().equals(c.breathInput().action())&&u.available()))throw invalid("S2_MONSTER_RECHARGE_UNAVAILABLE");
            if(b.flows().size()>=Schema2CombatRules.MAX_FLOWS||b.version()>=Schema2CombatRules.MAX_FACTS-2)throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            batch=new SaveDamageBatch(key(b.combatId(),"preflight-batch"),key(b.combatId(),"preflight-frame"),c.actorId(),c.breathInput().action(),b.combatId(),b.turnNumber(),targets(roster,b,c.actorId(),c.breathInput().direction(),b.version()),BatchStatus.ROLLED,null,List.of(),List.of(),List.of());
        } else {
            if(c.breathInput()!=null||b.activeFlow()==null||!b.activeFlow().flowKey().equals(c.flowKey())||!Objects.equals(b.activeFlow().leafFrameKey(),c.leafKey()))throw invalid("ACTION_FLOW_TARGET_MISMATCH");
            batch=b.saveDamageBatches().stream().filter(v->v.frameKey().equals(c.leafKey())).findFirst().orElseThrow(()->invalid("ACTION_FLOW_FROZEN_DATA_INVALID"));
            if(batch.status()!=BatchStatus.ROLLED||!batch.sourceId().equals(c.actorId()))throw invalid("ACTION_FLOW_STAGE_INVALID");
            if(batch.spellSource()==null)Schema2MonsterRules.action(roster,batch.action());
            else validateSpellSource(roster,b,batch);
            if(!targets(roster,b,batch.sourceId(),batch.targetProof().direction(),batch.targetProof().rosterVersion()).equals(batch.targetProof()))throw invalid("ACTION_FLOW_TARGET_MISMATCH");
        }
        if(batch.targetProof().targets().size()>64||6+batch.targetProof().targets().size()*6>4096)throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
        prepareTargets(roster,b,batch.targetProof());
    }
    private static void prepareTargets(Map<UUID,Context> roster,Battle b,TargetSetProof proof) {
        // Includes every future health/check/effect dependency before any raw damage roll.
        for(CreatureRef ref:proof.targets()) {
            if(ref.kind()==CreatureKind.CHARACTER) {var context=roster.get(ref.creatureId());new Schema2CharacterHealthRules().prepareDamage(context.material(),context.character());}
            Schema2SavingThrowRules.prepare(roster,b,ref,"ability.dexterity");
            if(b.concentrationHolders().stream().anyMatch(h->h.actorId().equals(ref.creatureId())))Schema2SavingThrowRules.prepare(roster,b,ref,"ability.constitution");
            defenses(roster,b,ref);
        }
    }
    static void prepareSpell(Map<UUID,Context> roster,Battle b,UUID caster,CastInput input) {
        if(b.status()!=Status.ACTIVE)throw invalid("S2_SPELL_AREA_SCENE");
        if(b.environmentZones().stream().anyMatch(z->z.endedOperationId()==null))throw invalid("S2_SPELL_AREA_ANTIMAGIC_UNSUPPORTED");
        if(b.saveDamageBatches().size()>=128||b.activeFlow()==null&&b.version()>=Schema2CombatRules.MAX_FACTS-4
                ||b.activeFlow()!=null&&b.activeFlow().frames().size()>=1024)throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
        TargetSetProof proof=targets(roster,b,caster,input.coneDirection(),b.version());
        prepareNestedObjectBoundary(roster,b,caster);
        // This scene has no independent flammable-object owner. Even a known ground item
        // cannot be ignored or guessed nonflammable; such a cone is rejected before payment.
        for(Context context:roster.values())for(var item:context.character().equipment())
            if("GROUND".equals(item.location())&&b.combatId().equals(item.groundCombatId())
                    &&inside(proof.origin(),new Position(item.groundX(),item.groundY()),input.coneDirection()))throw invalid("S2_SPELL_OBJECT_EFFECT_UNSUPPORTED");
        prepareTargets(roster,b,proof);
    }
    private static void prepareNestedObjectBoundary(Map<UUID,Context> roster,Battle b,UUID caster) {
        Set<UUID> countercasters=reactionCasters(roster,b,Schema2SpellRules.COUNTERSPELL);
        if(countercasters.stream().noneMatch(id->!id.equals(caster)))return;
        Set<UUID> shieldcasters=reactionCasters(roster,b,Schema2SpellRules.SHIELD);
        // A paid declaration must cover the whole possible reaction subtree. Each actor has
        // at most CAST_COMPLETED and ATTACK_COMPLETED here, so cycles terminate without RNG.
        Set<ReadyTrigger> reachable=new HashSet<>();ArrayDeque<ReadyTrigger> pending=new ArrayDeque<>();
        for(UUID id:countercasters)pending.add(new ReadyTrigger(TriggerKind.CAST_COMPLETED,id,null));
        while(!pending.isEmpty()) {
            ReadyTrigger trigger=pending.removeFirst();if(!reachable.add(trigger))continue;
            for(var ready:b.readyPreparations())if(ready.status()==ReadyStatus.ARMED&&ready.input().trigger().equals(trigger)) {
                var response=ready.input();
                if(response.responseKind()==ReadyResponseKind.MOVE||response.responseKind()==ReadyResponseKind.ATTACK&&!response.targetId().equals(caster)&&roster.containsKey(response.targetId())
                        &&roster.get(response.targetId()).character().equipment().stream().anyMatch(i->Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(i.location())))
                    throw invalid("S2_SPELL_NESTED_OBJECT_EFFECT_UNSUPPORTED");
                if(response.responseKind()==ReadyResponseKind.ATTACK) {
                    pending.add(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED,ready.actorId(),null));
                    if(shieldcasters.contains(response.targetId()))pending.add(new ReadyTrigger(TriggerKind.CAST_COMPLETED,response.targetId(),null));
                }
            }
        }
    }
    private static Set<UUID> reactionCasters(Map<UUID,Context> roster,Battle b,String algorithm) {
        return roster.entrySet().stream().filter(e->actor(b,e.getKey()).reactionAvailable()&&e.getValue().character().hitPoints()>0
                &&e.getValue().character().spellbook()!=null&&e.getValue().material().projection().rules().stream().anyMatch(r->r instanceof FinalSpell s&&s.enabled()&&algorithm.equals(s.definition().algorithm())&&e.getValue().character().spellbook().prepared().contains(s.identity()))
                &&e.getValue().material().projection().rules().stream().anyMatch(r->r instanceof FinalWizardSpellcasting p&&p.enabled()&&e.getValue().character().resources().stream().anyMatch(v->v.identity().equals(Schema2SpellRules.COUNTERSPELL.equals(algorithm)?p.definition().rank3SlotResource():p.definition().slotResource())&&v.current()>0)))
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
    }
    static SaveDamageBatch rollSpell(UUID operation,Map<UUID,Context> roster,Battle b,CastAttempt cast,UUID frame,Schema2ActionFlowRules.Roller dice) {
        CastInput input=new CastInput(cast.castingRule(),cast.spell(),List.of(),null,cast.slotLevel(),null,cast.coneDirection());
        prepareSpell(roster,b,cast.actorId(),input);
        var state=roster.get(cast.actorId()).character();
        var source=new SpellDamageSource(cast.frameKey(),cast.spell(),cast.slotLevel(),Math.floorDiv(state.abilities().get("ability.intelligence")-10,2),state.proficiencyBonus());
        var proof=targets(roster,b,cast.actorId(),cast.coneDirection(),b.version());
        UUID batch=key(cast.frameKey(),"spell-save-damage");List<Integer> rolls=new ArrayList<>();
        if(!proof.targets().isEmpty())for(int i=0;i<source.diceCount();i++)rolls.add(dice.draw("SPELL_SHARED_DAMAGE",cast.actorId(),6));
        int total=rolls.stream().mapToInt(Integer::intValue).sum();
        SharedDamageRoll shared=rolls.isEmpty()?null:new SharedDamageRoll(key(batch,"shared-roll"),rolls,total,total);
        return new SaveDamageBatch(batch,frame,cast.actorId(),null,operation,b.turnNumber(),proof,
                rolls.isEmpty()?BatchStatus.NO_TARGETS:BatchStatus.ROLLED,shared,List.of(),List.of(),List.of(),source);
    }
    static void validateSpellSource(Map<UUID,Context> roster,Battle b,SaveDamageBatch batch) {
        var source=batch.spellSource();var cast=Schema2SpellRules.findCast(b,source.castFrameKey());
        if(batch.action()!=null||cast==null||cast.coneDirection()==null||!cast.actorId().equals(batch.sourceId())||!cast.spell().equals(source.spell())||cast.slotLevel()!=source.slotLevel()
                ||!Objects.equals(cast.coneDirection(),batch.targetProof().direction()))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        var context=roster.get(cast.actorId());var state=context.character();
        if(source.slotLevel()<1||source.slotLevel()>3||source.abilityModifier()!=Math.floorDiv(state.abilities().get("ability.intelligence")-10,2)||source.proficiencyBonus()!=state.proficiencyBonus()
                ||context.material().projection().rules().stream().noneMatch(r->r instanceof FinalSpell s&&s.enabled()&&s.identity().equals(source.spell())&&Schema2SpellRules.BURNING_HANDS.equals(s.definition().algorithm())))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
    }
    Resolution execute(UUID operation,Map<UUID,Context> roster,Battle before,Command c,Schema2ActionFlowRules.Roller dice) {
        return c.kind()==Kind.MONSTER_BREATH?release(operation,roster,before,c,dice):complete(operation,roster,before,c,dice);
    }
    private Resolution release(UUID operation,Map<UUID,Context> roster,Battle b,Command c,Schema2ActionFlowRules.Roller dice) {
        UUID batchKey=key(operation,"save-damage-batch"),frameKey=key(operation,"save-damage-frame"),flowKey=key(operation,"save-damage-flow");
        TargetSetProof proof=targets(roster,b,c.actorId(),c.breathInput().direction(),b.version());boolean empty=proof.targets().isEmpty();
        List<Integer> rolls=new ArrayList<>();if(!empty)for(int i=0;i<6;i++)rolls.add(dice.draw("BREATH_SHARED_DAMAGE",c.actorId(),6));
        int total=rolls.stream().mapToInt(Integer::intValue).sum();
        SharedDamageRoll shared=empty?null:new SharedDamageRoll(key(batchKey,"shared-roll"),rolls,total,total);
        var batch=new SaveDamageBatch(batchKey,frameKey,c.actorId(),c.breathInput().action(),operation,b.turnNumber(),proof,
                empty?BatchStatus.NO_TARGETS:BatchStatus.ROLLED,shared,List.of(),List.of(),List.of());
        var monster=Schema2MonsterRules.instance(b,c.actorId());b=Schema2MonsterRules.replace(b,new MonsterInstance(monster.monsterId(),monster.monster(),monster.hitPoints(),monster.dead(),monster.actionUses().stream().map(u->u.action().equals(batch.action())?new MonsterActionUse(u.action(),false):u).toList()));
        var actor=actor(b,c.actorId());List<Combatant> actors=b.combatants().stream().map(a->a.characterId().equals(c.actorId())?new Combatant(a.characterId(),a.position(),a.side(),false,a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),a.sequence(),a.prone()):a).toList();
        Frame frame=new Frame(frameKey,null,FrameKind.SAVE_DAMAGE,batchKey,Invocation.ROOT,empty?FrameStatus.RUNNABLE:FrameStatus.PENDING,empty?Stage.SAVE_DAMAGE_COMPLETED:Stage.SAVE_DAMAGE_ROLLED,null,null);
        Flow flow=new Flow(flowKey,frameKey,frameKey,FlowStatus.ACTIVE,List.of(frame),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        List<Flow> flows=new ArrayList<>(b.flows());flows.add(flow);List<SaveDamageBatch> batches=new ArrayList<>(b.saveDamageBatches());batches.add(batch);b=Schema2MonsterRules.with(b,b.monsters(),batches);
        b=Schema2ActionFlowRules.rebuild(b,actors,flows,b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),beforeVersion(b)+1);
        List<CombatFact> facts=new ArrayList<>(List.of(new CombatFact("MONSTER_ACTION_RELEASED",c.actorId(),batchKey,empty?"NO_TARGETS":null),new CombatFact("MONSTER_RECHARGE_SPENT",c.actorId(),batchKey,null)));
        if(empty) {var after=new Schema2ActionFlowRules(new Schema2CombatRules()).afterBatch(operation,roster,b,c.actorId(),frameKey,dice);facts.addAll(after.facts());return new Resolution(after.after(),after.healthChanges(),List.of(),facts);}
        return new Resolution(b,List.of(),List.of(),facts);
    }
    private Resolution complete(UUID operation,Map<UUID,Context> original,Battle before,Command c,Schema2ActionFlowRules.Roller dice) {
        Battle b=before;Map<UUID,Context> roster=new HashMap<>(original);List<HealthChange> changes=new ArrayList<>();List<CombatFact> facts=new ArrayList<>();
        SaveDamageBatch batch=b.saveDamageBatches().stream().filter(v->v.frameKey().equals(c.leafKey())).findFirst().orElseThrow();
        var targets=batch.targetProof().targets();List<BatchCheck> checks=new ArrayList<>();List<DamagePacket> packets=new ArrayList<>();
        for(int i=0;i<targets.size();i++) {
            CreatureRef ref=targets.get(i);var basis=Schema2SavingThrowRules.prepare(roster,b,ref,"ability.dexterity");
            checks.add(Schema2SavingThrowRules.resolve(basis,i,batch.spellSource()==null?"BREATH_DEX":"SPELL_DEX",ref.creatureId(),batch.difficultyClass(),dice));
        }
        // Build all packets and all real owner health plans against one unchanged APPLY cut.
        List<MonsterInstance> monsters=new ArrayList<>(b.monsters());
        for(int i=0;i<targets.size();i++) {
            CreatureRef ref=targets.get(i);List<DefenseContribution> defenses=defenses(roster,b,ref);
            int amount=damage(batch.sharedRoll().effectiveTotal(),checks.get(i).success(),defenses);
            int hp,temp,max,afterHp,afterTemp;boolean dead;UUID packet=key(batch.batchKey(),"packet-"+i);
            if(ref.kind()==CreatureKind.CHARACTER) {
                var ctx=roster.get(ref.creatureId());var old=ctx.character();hp=Math.toIntExact(old.hitPoints());temp=old.health().temporaryHitPoints();max=Math.toIntExact(old.maximumHitPoints());
                var after=old;
                if(amount>0) {
                    var result=health.applyDamage(ctx.material(),old,amount,false);after=result.state();
                    if(after.hitPoints()==0)after=Schema2CombatEquipmentFacts.drop(after,b.combatId(),actor(b,ref.creatureId()).position());
                    changes.add(new HealthChange(ref.creatureId(),old,after,batch.frameKey(),amount,false,new HealthResult(List.of(),null,null,amount,result.appliedAmount()),false));
                }
                afterHp=Math.toIntExact(after.hitPoints());afterTemp=after.health().temporaryHitPoints();dead=after.health().dead();
            } else {
                var m=Schema2MonsterRules.instance(b,ref.creatureId());hp=m.hitPoints();temp=0;max=Schema2MonsterRules.definition(roster,m.monster()).statBlock().hitPoints();afterHp=Math.max(0,hp-amount);afterTemp=0;dead=afterHp==0;
                var next=new MonsterInstance(m.monsterId(),m.monster(),afterHp,dead,m.actionUses());for(int j=0;j<monsters.size();j++)if(monsters.get(j).monsterId().equals(m.monsterId()))monsters.set(j,next);
            }
            packets.add(new DamagePacket(packet,i,batch.sharedRoll().rollKey(),i,hp,temp,max,defenses,amount,Math.min(temp,amount),afterHp,afterTemp,dead));
        }
        b=Schema2MonsterRules.with(b,monsters,b.saveDamageBatches());
        for(HealthChange change:changes) {var old=roster.get(change.characterId());roster.put(change.characterId(),new Context(old.material(),old.initial(),change.after()));}
        // All health facts exist before any holder or contribution is ended.
        List<Combatant> actors=new ArrayList<>(b.combatants());
        for(DamagePacket packet:packets)if(packet.afterHitPoints()==0) {
            UUID id=targets.get(packet.targetOrdinal()).creatureId();for(int j=0;j<actors.size();j++)if(actors.get(j).characterId().equals(id)) {
                var a=actors.get(j);var seq=a.sequence();if(seq!=null&&!seq.closed())seq=new AttackSequence(seq.sequenceKey(),seq.limit(),seq.performed(),seq.limit()-seq.performed(),true);
                actors.set(j,new Combatant(id,a.position(),a.side(),false,a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),seq,true));
            }
        }
        b=Schema2ActionFlowRules.rebuild(b,actors,b.flows(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.version());
        List<ConcentrationDamageObligation> obligations=new ArrayList<>();List<Integer> pending=new ArrayList<>();Map<Integer,ConcentrationHolder> holders=new HashMap<>();
        for(int i=0;i<targets.size();i++) {
            int ordinal=i;var holder=before.concentrationHolders().stream().filter(h->h.actorId().equals(targets.get(ordinal).creatureId())).findFirst().orElse(null);if(holder==null)continue;
            var packet=packets.get(i);holders.put(i,holder);
            if(packet.afterHitPoints()==0||packet.dead()) {
                b=Schema2SpellRules.end(b,holder.actorId(),"INCAPACITATED",facts);
                obligations.add(new ConcentrationDamageObligation(i,holder.sourceKey(),packet.packetKey(),packet.damageAmount(),0,ConcentrationDamageStatus.FORCED_ENDED,null));
            } else if(packet.damageAmount()==0)obligations.add(new ConcentrationDamageObligation(i,holder.sourceKey(),packet.packetKey(),0,0,ConcentrationDamageStatus.NOT_REQUIRED,null));
            else pending.add(i);
        }
        // Reconcile every forced end, then freeze the entire CON basis before any failure is consumed.
        var reconciled=Schema2OngoingEffectRules.reconcile(b,operation);b=Schema2OngoingEffectRules.withCauses(b,reconciled.causes());facts.addAll(reconciled.facts());
        Map<Integer,Schema2SavingThrowRules.Basis> cut=new LinkedHashMap<>();for(int i:pending)cut.put(i,Schema2SavingThrowRules.prepare(roster,b,targets.get(i),"ability.constitution"));
        List<UUID> failed=new ArrayList<>();
        for(int i:pending) {
            var packet=packets.get(i);int dc=Math.max(10,packet.damageAmount()/2),index=checks.size();var check=Schema2SavingThrowRules.resolve(cut.get(i),i,"CONCENTRATION_CON",targets.get(i).creatureId(),dc,dice);checks.add(check);
            obligations.add(new ConcentrationDamageObligation(i,holders.get(i).sourceKey(),packet.packetKey(),packet.damageAmount(),dc,check.success()?ConcentrationDamageStatus.CHECK_SUCCEEDED:ConcentrationDamageStatus.CHECK_FAILED,index));
            if(!check.success())failed.add(targets.get(i).creatureId());
        }
        for(UUID subject:failed)b=Schema2SpellRules.end(b,subject,"CONCENTRATION_SAVE_FAILED",facts);
        obligations.sort(Comparator.comparingInt(ConcentrationDamageObligation::targetOrdinal));
        SaveDamageBatch completed=new SaveDamageBatch(batch.batchKey(),batch.frameKey(),batch.sourceId(),batch.action(),batch.releaseOperationId(),batch.releasedTurnNumber(),batch.targetProof(),BatchStatus.COMPLETED,batch.sharedRoll(),checks,packets,obligations,batch.spellSource());
        b=Schema2MonsterRules.with(b,b.monsters(),b.saveDamageBatches().stream().map(v->v.batchKey().equals(batch.batchKey())?completed:v).toList());
        b=Schema2ActionFlowRules.rebuild(b,b.combatants(),b.flows(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),before.version()+1);
        reconciled=Schema2OngoingEffectRules.reconcile(b,operation,1);b=Schema2OngoingEffectRules.withCauses(b,reconciled.causes());facts.addAll(reconciled.facts());
        facts.add(new CombatFact("SAVE_DAMAGE_COMPLETED",batch.sourceId(),batch.batchKey(),null));
        var actions=new Schema2ActionFlowRules(new Schema2CombatRules());
        var after=batch.spellSource()==null?actions.afterBatch(operation,roster,b,batch.sourceId(),batch.frameKey(),dice):actions.afterSpellBatch(operation,roster,b,completed,dice);facts.addAll(after.facts());changes.addAll(after.healthChanges());
        return new Resolution(after.after(),changes,List.of(),facts);
    }
    static int damage(int shared,boolean saved,List<DefenseContribution> defenses) {
        long amount=saved?shared/2:shared;
        if(defenses.stream().anyMatch(d->d.kind().equals("IMMUNITY")))return 0;
        if(defenses.stream().anyMatch(d->d.kind().equals("RESISTANCE")))amount/=2;
        if(defenses.stream().anyMatch(d->d.kind().equals("VULNERABILITY")))amount=Math.multiplyExact(amount,2);
        return Math.toIntExact(amount);
    }
    static List<DefenseContribution> defenses(Map<UUID,Context> roster,Battle b,CreatureRef ref) {
        if(ref.kind()==CreatureKind.CHARACTER)return List.of();
        var m=Schema2MonsterRules.instance(b,ref.creatureId());return Schema2MonsterRules.definition(roster,m.monster()).statBlock().damageDefenses().stream()
                .filter(d->d.damageType()==DamageType.FIRE&&d.qualifier()==MonsterDefenseQualifier.ALL).map(d->new DefenseContribution(m.monster(),d.kind().name())).toList();
    }
    static TargetSetProof targets(Map<UUID,Context> roster,Battle b,UUID source,int direction,long version) {
        Position origin=actor(b,source).position();int[] dir=DIRECTIONS[direction];List<AreaCandidate> candidates=new ArrayList<>();List<CreatureRef> targets=new ArrayList<>();
        for(var a:b.combatants()) {
            boolean monster=Schema2MonsterRules.isMonster(b,a.characterId());CreatureRef ref=new CreatureRef(monster?CreatureKind.MONSTER:CreatureKind.CHARACTER,a.characterId());
            long x=(long)a.position().x()-origin.x(),y=(long)a.position().y()-origin.y(),dot=x*dir[0]+y*dir[1],cross=x*dir[1]-y*dir[0];
            boolean corpse=monster?Schema2MonsterRules.instance(b,a.characterId()).dead():roster.get(a.characterId()).character().health().dead();
            boolean included=!corpse&&dot>0&&dot<=3L*(dir[0]*dir[0]+dir[1]*dir[1])&&2*Math.abs(cross)<=dot;
            candidates.add(new AreaCandidate(ref,a.position(),included,corpse?"CORPSE_NOT_CREATURE":x==0&&y==0?"ORIGIN_EXCLUDED":included?"INSIDE_CONE":"OUTSIDE_CONE"));if(included)targets.add(ref);
        }
        Comparator<CreatureRef> order=Comparator.comparing(CreatureRef::kind).thenComparing(CreatureRef::creatureId,(a,z)-> {int high=Long.compareUnsigned(a.getMostSignificantBits(),z.getMostSignificantBits());return high!=0?high:Long.compareUnsigned(a.getLeastSignificantBits(),z.getLeastSignificantBits());});
        targets.sort(order);candidates.sort(Comparator.comparing(AreaCandidate::creature,order));
        return new TargetSetProof(SPACE_POLICY,version,origin,direction,candidates,targets);
    }
    private static boolean inside(Position origin,Position target,int direction) {
        int[] dir=DIRECTIONS[direction];long x=(long)target.x()-origin.x(),y=(long)target.y()-origin.y(),dot=x*dir[0]+y*dir[1],cross=x*dir[1]-y*dir[0];
        return dot>0&&dot<=3L*(dir[0]*dir[0]+dir[1]*dir[1])&&2*Math.abs(cross)<=dot;
    }
    private static long beforeVersion(Battle b) {return b.version();}
    private static Combatant actor(Battle b,UUID id) {return b.combatants().stream().filter(a->a.characterId().equals(id)).findFirst().orElseThrow(()->invalid("ACTION_FLOW_TARGET_MISMATCH"));}
    private static UUID key(UUID operation,String role) {UUID v=UUID.nameUUIDFromBytes((operation+":"+role).getBytes(StandardCharsets.UTF_8));return new UUID((v.getMostSignificantBits()&~0xf000L)|0x4000L,v.getLeastSignificantBits());}
}
