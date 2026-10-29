package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.EquipmentItem;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthResult;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.Stage;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.service.Schema2CombatState.*;

/** The single typed continuation graph for attack, movement, casting and prepared reactions. */
final class Schema2ActionFlowRules {
    interface Roller { int draw(String purpose,UUID subject,int sides); }
    private final Schema2CombatRules combat;
    private final Schema2SpellRules spells=new Schema2SpellRules();
    private final Schema2CharacterEquipmentRules equipment=new Schema2CharacterEquipmentRules();
    private final Schema2CharacterHealthRules health=new Schema2CharacterHealthRules();
    Schema2ActionFlowRules(Schema2CombatRules combat) { this.combat=combat; }
    static boolean continuation(Kind k) { return Set.of(Kind.PASS_REACTION,Kind.DECLARE_OPPORTUNITY_ATTACK,Kind.DECLARE_SHIELD,Kind.DECLARE_COUNTERSPELL,Kind.RELEASE_READY,Kind.ADVANCE_WINDOW,Kind.CONTINUE_FLOW,Kind.CONTINUE_MOVEMENT).contains(k); }
    static boolean handles(Kind k) { return continuation(k)||Set.of(Kind.ATTACK,Kind.MOVE,Kind.CAST,Kind.READY_ATTACK,Kind.READY_MOVE,Kind.READY_SPELL).contains(k); }
    void prepare(Map<UUID,Context> roster,Battle b,Command c) {
        require(c.actorId()!=null && c.deployments().isEmpty() && c.tieBreakOrder().isEmpty() && c.zoneInput()==null && c.zoneKey()==null && c.healthInput()==null && c.seconds()==0 && c.monsterDeployments().isEmpty() && c.breathInput()==null && c.monsterRestInput()==null,"S2_COMBAT_COMMAND");
        if(continuation(c.kind())) {
            Flow f=b.activeFlow(); require(f!=null&&f.flowKey().equals(c.flowKey())&&Objects.equals(f.leafFrameKey(),c.leafKey()),"ACTION_FLOW_TARGET_MISMATCH");
            Frame leaf=frame(f,c.leafKey());Window w=window(f,c.windowKey());
            require(Objects.equals(leaf.windowKey(),w.windowKey()),"ACTION_FLOW_TARGET_MISMATCH");
            if(c.kind()==Kind.CONTINUE_FLOW||c.kind()==Kind.CONTINUE_MOVEMENT) {
                require(w.status()==WindowStatus.CLOSED&&w.ownerFrameKey().equals(leaf.frameKey())&&(leaf.kind()==FrameKind.SAVE_DAMAGE?b.saveDamageBatches().stream().filter(v->v.frameKey().equals(leaf.frameKey())).findFirst().orElseThrow().sourceId():ownerActor(f,leaf)).equals(c.actorId())&&c.opportunityKey()==null,"ACTION_FLOW_STAGE_INVALID");
                require(c.itemId()==null&&c.targetId()==null&&c.castInput()==null&&c.readyInput()==null&&c.readyKey()==null&&c.path().isEmpty(),"S2_COMBAT_COMMAND");return;
            }
            require(w.status()==WindowStatus.OPEN&&leaf.kind()==FrameKind.REACTION_SELECTION,"ACTION_FLOW_STAGE_INVALID");
            Opportunity o=current(w);require(o.opportunityKey().equals(c.opportunityKey())&&o.actorId().equals(c.actorId())&&o.selectionFrameKey().equals(leaf.frameKey()),"REACTION_NOT_CURRENT");
            require(c.targetId()==null&&c.path().isEmpty()&&c.readyInput()==null,"S2_COMBAT_COMMAND");
            boolean eligible=eligible(roster,b,f,w,o);
            if(c.kind()==Kind.PASS_REACTION||c.kind()==Kind.ADVANCE_WINDOW) {
                require(c.itemId()==null&&c.castInput()==null&&c.readyKey()==null,"S2_COMBAT_COMMAND");
                if(c.kind()==Kind.ADVANCE_WINDOW)require(!eligible,"REACTION_OPTION_INVALID");return;
            }
            require(eligible,"REACTION_OPTION_INVALID");
            ReactionOption option=select(o,c);require(option!=null,"REACTION_OPTION_INVALID");
            if(option.kind()==ReactionKind.OPPORTUNITY_ATTACK)prepareAttack(roster,b,c.actorId(),w.trigger().subjectId(),c.itemId());
            else if(option.kind()==ReactionKind.READY_RELEASE) {
                ReadyPreparation ready=ready(b,c.readyKey());require(ready.status()==ReadyStatus.ARMED,"REACTION_OPTION_INVALID");
                prepareReadyResponse(roster,b,ready);
            } else {if(option.kind()==ReactionKind.COUNTERSPELL)require(Objects.equals(c.castInput().targetCastFrameKey(),w.trigger().sourceFrameKey()),"REACTION_OPTION_INVALID");spells.prepare(roster,b,c.actorId(),c.castInput(),true);}
            int depth=0,chain=0;Frame cursor=frame(f,w.ownerFrameKey());
            while(cursor!=null) {chain++;if(cursor.invocation()==Invocation.REACTION)depth++;cursor=cursor.parentFrameKey()==null?null:frame(f,cursor.parentFrameKey());}
            require(depth<8,"REACTION_DEPTH_LIMIT");require(chain+4<=32&&f.frames().size()+4<=1024&&f.windows().size()+3<=1024,"ACTION_FLOW_LIMIT_EXCEEDED");requireCauseCapacity(roster,b,c);requireStructureCapacity(roster,b,c);requireCharacterCapacity(roster,b,c);return;
        }
        require(c.flowKey()==null&&c.leafKey()==null&&c.windowKey()==null&&c.opportunityKey()==null&&c.readyKey()==null,"S2_COMBAT_COMMAND");
        require(b.status()==Status.EXPLORATION&&c.kind()==Kind.CAST||c.actorId().equals(b.activeActor()),"S2_COMBAT_NOT_CURRENT_TURN");require(roster.containsKey(c.actorId()),"S2_MONSTER_ACTION_UNSUPPORTED");active(roster.get(c.actorId()).character());
        require(b.flows().size()<Schema2CombatRules.MAX_FLOWS,"ACTION_FLOW_LIMIT_EXCEEDED");Combatant actor=actor(b,c.actorId());
        switch(c.kind()) {
            case ATTACK -> {
                require(c.targetId()!=null&&c.itemId()!=null&&c.path().isEmpty()&&c.castInput()==null&&c.readyInput()==null,"S2_COMBAT_COMMAND");
                require(actor.sequence()!=null&&!actor.sequence().closed(),"S2_COMBAT_SEQUENCE_UNAVAILABLE");prepareAttack(roster,b,c.actorId(),c.targetId(),c.itemId());
            }
            case MOVE -> {require(c.targetId()==null&&c.itemId()==null&&c.castInput()==null&&c.readyInput()==null,"S2_COMBAT_COMMAND");prepareMove(roster,b,c.actorId(),c.path(),false);}
            case CAST -> {require(c.targetId()==null&&c.itemId()==null&&c.path().isEmpty()&&c.castInput()!=null&&c.readyInput()==null,"S2_COMBAT_COMMAND");require(b.status()==Status.EXPLORATION||actor.actionAvailable(),"S2_COMBAT_ACTION_UNAVAILABLE");spells.prepare(roster,b,c.actorId(),c.castInput());if(b.status()==Status.EXPLORATION)Schema2ExplorationRules.requireTimeSafe(roster,6);}
            case READY_ATTACK,READY_MOVE,READY_SPELL -> {
                require(c.targetId()==null&&c.itemId()==null&&c.path().isEmpty()&&c.castInput()==null&&c.readyInput()!=null,"S2_COMBAT_COMMAND");
                require(actor.actionAvailable(),"S2_COMBAT_ACTION_UNAVAILABLE");ReadyInput r=c.readyInput();validateTrigger(b,r.trigger());
                require(b.readyPreparations().stream().noneMatch(v->v.actorId().equals(c.actorId())&&Set.of(ReadyStatus.PREPARING,ReadyStatus.ARMED).contains(v.status())),"S2_READY_ALREADY_ARMED");
                if(c.kind()==Kind.READY_ATTACK) {require(r.responseKind()==ReadyResponseKind.ATTACK&&r.spell()==null&&r.path().isEmpty()&&r.targetId()!=null&&r.itemId()!=null,"S2_READY_COMMAND");require(!c.actorId().equals(r.targetId())&&actor(b,c.actorId()).side()!=actor(b,r.targetId()).side(),"S2_COMBAT_TARGET");combat.weapon(roster.get(c.actorId()),r.itemId());prepareTargetHealth(roster,b,r.targetId());}
                if(c.kind()==Kind.READY_MOVE) {require(r.responseKind()==ReadyResponseKind.MOVE&&r.spell()==null&&r.targetId()==null&&r.itemId()==null,"S2_READY_COMMAND");prepareMove(roster,b,c.actorId(),r.path(),true);}
                if(c.kind()==Kind.READY_SPELL) {require(r.responseKind()==ReadyResponseKind.SPELL&&r.spell()!=null&&r.targetId()==null&&r.itemId()==null&&r.path().isEmpty(),"S2_READY_COMMAND");spells.prepare(roster,b,c.actorId(),r.spell());}
            }
            default -> throw invalid("S2_COMBAT_COMMAND");
        }
        requireCauseCapacity(roster,b,c);requireStructureCapacity(roster,b,c);requireCharacterCapacity(roster,b,c);
    }
    private void prepareTargetHealth(Map<UUID,Context> roster,Battle b,UUID target) {
        if(roster.containsKey(target))health.prepareDamage(roster.get(target).material(),roster.get(target).character());
        else {var m=Schema2MonsterRules.instance(b,target);Schema2MonsterRules.definition(roster,m.monster());if(m.dead())throw invalid("S2_HEALTH_DEAD");}
    }
    int batchAfterCost(Map<UUID,Context> roster,Battle b,UUID source) {return b==null?0:readyCost(roster,b,TriggerKind.MONSTER_ACTION_COMPLETED,source,null);}
    Resolution afterBatch(UUID operation,Map<UUID,Context> roster,Battle b,UUID source,UUID frame,Roller dice) {
        Work w=new Work(operation,roster,b,dice);if(!w.open(frame,TriggerKind.MONSTER_ACTION_COMPLETED,source,null,null,0,0))w.finish(frame,Stage.SAVE_DAMAGE_COMPLETED);
        return new Resolution(w.snapshot(),w.changes,List.of(),w.facts);
    }
    private void prepareAttack(Map<UUID,Context> roster,Battle b,UUID source,UUID target,UUID item) {
        Combatant a=actor(b,source),t=actor(b,target);require(!source.equals(target)&&a.side()!=t.side(),"S2_COMBAT_TARGET");active(roster.get(source).character());
        WeaponAttackDefinition w=combat.weapon(roster.get(source),item);require(a.position().distanceFeet(t.position())<=w.reachFeet(),"S2_COMBAT_OUT_OF_REACH");
        prepareTargetHealth(roster,b,target);if(roster.containsKey(target))equipment.derive(roster.get(target).material(),roster.get(target).character());
    }
    private void prepareMove(Map<UUID,Context> roster,Battle b,UUID source,List<Position> path,boolean readied) {
        Combatant a=actor(b,source);
        // No supported monster OA producer or typed reach reader exists in this slice. Prove
        // the entire branch safe before moving even its first cell instead of omitting a reactor.
        for(var monster:b.monsters()) {var candidate=actor(b,monster.monsterId());if(!monster.dead()&&candidate.side()!=a.side()&&candidate.reactionAvailable())throw invalid("S2_MONSTER_REACTION_UNSUPPORTED");}
        require(!a.prone(),"S2_COMBAT_PRONE");require(!path.isEmpty()&&path.size()<=64,"S2_COMBAT_PATH");
        int allowance=readied?combat.speed(roster.get(source)):combat.speed(roster.get(source))+a.movementBonus()-a.movementSpentHalfFeet()/2;
        require(path.size()*5<=allowance,"S2_COMBAT_MOVEMENT_ALLOWANCE");Position previous=a.position();
        for(Position next:path) {require(previous.distanceFeet(next)==5&&b.combatants().stream().noneMatch(other->!other.characterId().equals(source)&&other.position().equals(next)),"S2_COMBAT_PATH");previous=next;}
        // Every currently equipped weapon and every acquisition feature was exhaustively checked by the shared root preflight.
        health.prepareDamage(roster.get(source).material(),roster.get(source).character());
    }
    private void prepareReadyResponse(Map<UUID,Context> roster,Battle battle,ReadyPreparation ready) {
        ReadyInput input=ready.input();
        if(input.responseKind()==ReadyResponseKind.ATTACK)prepareAttack(roster,battle,ready.actorId(),input.targetId(),input.itemId());
        else if(input.responseKind()==ReadyResponseKind.MOVE)prepareMove(roster,battle,ready.actorId(),input.path(),true);
        else {
            require(battle.concentrationHolders().stream().anyMatch(h->h.actorId().equals(ready.actorId())&&h.kind()==ConcentrationKind.READY_SPELL&&h.sourceKey().equals(ready.readyKey())),"S2_SPELL_CONCENTRATION_UNAVAILABLE");
            CastAttempt held=Schema2SpellRules.findCast(battle,ready.heldCastFrameKey());require(held!=null&&held.status()==CastStatus.HELD,"S2_READY_HELD_SPELL_INVALID");
            Schema2OngoingEffectRules.requireCastingAllowed(roster,battle,ready.actorId());boolean bless=isBless(roster,ready.actorId(),held.spell());
            if(bless)require(Schema2SpellRules.second(roster.get(ready.actorId()).character())<=Schema2CharacterRestRules.MAX_MINUTE*60-60,"S2_SPELL_TIME_CAPACITY");
            for(UUID target:held.targets()) {
                require(actor(battle,ready.actorId()).position().distanceFeet(actor(battle,target).position())<=(bless?30:5),"S2_SPELL_TARGET");
                if(!bless)require(!roster.get(target).character().health().dead(),"S2_HEALTH_DEAD");
            }
        }
    }
    private boolean readyAvailable(Map<UUID,Context> roster,Battle battle,ReadyPreparation ready) {
        try {prepareReadyResponse(roster,battle,ready);return true;}
        catch(IllegalArgumentException unavailable) {
            if(Set.of("S2_COMBAT_OUT_OF_REACH","S2_COMBAT_WEAPON_NOT_PREPARED","S2_COMBAT_PRONE","S2_COMBAT_MOVEMENT_ALLOWANCE","S2_COMBAT_PATH","S2_SPELL_TARGET","S2_HEALTH_DEAD","S2_SPELL_ANTIMAGIC_ZONE").contains(unavailable.getMessage()))return false;
            throw unavailable;
        }
    }
    private static void validateTrigger(Battle b,ReadyTrigger trigger) {
        require(trigger!=null&&trigger.subjectId()!=null&&b.combatants().stream().anyMatch(a->a.characterId().equals(trigger.subjectId())),"S2_READY_TRIGGER");
        require(Set.of(TriggerKind.CREATURE_ENTER_CELL_AFTER,TriggerKind.ATTACK_COMPLETED,TriggerKind.CAST_COMPLETED,TriggerKind.MONSTER_ACTION_COMPLETED).contains(trigger.kind()),"S2_READY_TRIGGER");
        require((trigger.position()!=null)==(trigger.kind()==TriggerKind.CREATURE_ENTER_CELL_AFTER),"S2_READY_TRIGGER");
    }
    private static void requireCharacterCapacity(Map<UUID,Context> roster,Battle b,Command command) {
        Map<UUID,Integer> needed=new HashMap<>();for(UUID actor:roster.keySet())needed.put(actor,0);
        Flow active=b.activeFlow();if(active!=null) {
            for(AttackAttempt attack:active.attacks())if(attack.status()==AttackStatus.HIT_PENDING_DAMAGE)needed.merge(attack.targetId(),1,Integer::sum);
            for(CastAttempt cast:active.casts())if(cast.status()==CastStatus.PENDING&&cast.readyKey()==null&&isAlgorithm(roster,cast.actorId(),cast.spell(),Schema2SpellRules.CURE_WOUNDS))needed.merge(cast.targets().getFirst(),1,Integer::sum);
            if(b.status()==Status.EXPLORATION) {
                var root=active.casts().stream().filter(c->c.frameKey().equals(active.rootFrameKey())).findFirst();if(root.isPresent()&&Set.of(CastStatus.PENDING,CastStatus.COUNTERED).contains(root.get().status()))for(UUID actor:roster.keySet())needed.merge(actor,1,Integer::sum);
            }
        }
        switch(command.kind()) {
            case CAST,READY_SPELL,DECLARE_SHIELD,DECLARE_COUNTERSPELL -> {
                needed.merge(command.actorId(),1,Integer::sum);CastInput input=command.kind()==Kind.READY_SPELL?command.readyInput().spell():command.castInput();
                if(command.kind()==Kind.CAST&&isAlgorithm(roster,command.actorId(),input.spell(),Schema2SpellRules.CURE_WOUNDS))needed.merge(input.targets().getFirst(),1,Integer::sum);
                if(command.kind()==Kind.CAST&&b.status()==Status.EXPLORATION)for(UUID actor:roster.keySet())needed.merge(actor,1,Integer::sum);
            }
            case ATTACK -> needed.merge(command.targetId(),1,Integer::sum);
            case DECLARE_OPPORTUNITY_ATTACK -> needed.merge(window(active,command.windowKey()).trigger().subjectId(),1,Integer::sum);
            case RELEASE_READY -> {ReadyPreparation ready=ready(b,command.readyKey());if(ready.input().responseKind()==ReadyResponseKind.ATTACK)needed.merge(ready.input().targetId(),1,Integer::sum);if(ready.input().responseKind()==ReadyResponseKind.SPELL&&isAlgorithm(roster,ready.actorId(),ready.input().spell().spell(),Schema2SpellRules.CURE_WOUNDS))needed.merge(ready.input().spell().targets().getFirst(),1,Integer::sum);}
            default -> { }
        }
        for(var entry:needed.entrySet())if(roster.containsKey(entry.getKey()))require(roster.get(entry.getKey()).character().version()+entry.getValue()<com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS,"ACTION_FLOW_RESERVATION_INSUFFICIENT");
    }
    private static boolean isAlgorithm(Map<UUID,Context> roster,UUID actor,RuleIdentity spell,String algorithm) {return roster.get(actor).material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).anyMatch(s->s.enabled()&&s.identity().equals(spell)&&algorithm.equals(s.definition().algorithm()));}
    private void requireStructureCapacity(Map<UUID,Context> roster,Battle b,Command command) {
        Flow flow=b.activeFlow();int suffix=mandatorySuffix(roster,b,command);
        int created=Set.of(Kind.DECLARE_OPPORTUNITY_ATTACK,Kind.DECLARE_SHIELD,Kind.DECLARE_COUNTERSPELL).contains(command.kind())?1:command.kind()==Kind.RELEASE_READY?2:flow==null&&Set.of(Kind.ATTACK,Kind.MOVE,Kind.CAST,Kind.READY_SPELL).contains(command.kind())?1:0;
        require((flow==null?0:flow.frames().size())+created+suffix<=1024&&(flow==null?0:flow.windows().size())+suffix<=1024,"ACTION_FLOW_LIMIT_EXCEEDED");
    }
    /** Complete suppression-episode frontier, proved without consuming dice or mutating a persisted graph. */
    static void requireCauseCapacity(Map<UUID,Context> roster,Battle b,Command command) {
        if(b.environmentZones().stream().noneMatch(z->z.endedOperationId()==null))return;
        UUID proof=key(b.combatId(),"cause-capacity-"+b.version());List<OngoingEffect> effects=new ArrayList<>(b.ongoingEffects());List<ShieldEffect> shields=new ArrayList<>(b.shields());
        List<CastAttempt> pending=b.flows().stream().flatMap(f->f.casts().stream()).filter(c->c.status()==CastStatus.PENDING).toList();
        for(CastAttempt c:pending)if(isBless(roster,c.actorId(),c.spell()))addProspectiveBless(effects,c.frameKey(),c.actorId(),c.spell(),c.targets(),roster);
        CastInput input=command.kind()==Kind.CAST?command.castInput():null;
        if(command.kind()==Kind.RELEASE_READY) {ReadyPreparation r=ready(b,command.readyKey());if(r.input().responseKind()==ReadyResponseKind.SPELL)input=r.input().spell();}
        if(input!=null&&isBless(roster,command.actorId(),input.spell()))addProspectiveBless(effects,proof,command.actorId(),input.spell(),input.targets(),roster);
        if(command.kind()==Kind.DECLARE_SHIELD)shields.add(new ShieldEffect(key(proof,"shield"),proof,command.actorId(),b.turnNumber(),b.turnNumber()+b.combatants().size(),EffectStatus.ACTIVE,null));
        Battle base=new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),effects,b.readyPreparations(),b.triggerFacts(),shields,b.concentrationHolders(),b.environmentZones(),b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());
        var initial=Schema2OngoingEffectRules.reconcile(base,proof,0);base=Schema2OngoingEffectRules.withCauses(base,initial.causes());int bound=initial.causes().size();
        record Route(UUID actor,List<Position> path) { }
        List<Route> routes=new ArrayList<>();for(Flow f:b.flows())if(f.status()==FlowStatus.ACTIVE)for(Movement m:f.movements())if(m.status()==MovementStatus.PENDING)routes.add(new Route(m.actorId(),m.path().subList(m.cursor(),m.path().size())));
        if(command.kind()==Kind.MOVE||command.kind()==Kind.EXPLORATION_MOVE)routes.add(new Route(command.actorId(),command.path()));
        if(command.kind()==Kind.RELEASE_READY) {ReadyPreparation r=ready(b,command.readyKey());if(r.input().responseKind()==ReadyResponseKind.MOVE)routes.add(new Route(r.actorId(),r.input().path()));}
        int ordinal=0;for(Route route:routes) {
            Battle trial=base;
            for(Position step:route.path()) {
                Combatant a=actor(trial,route.actor());Combatant moved=new Combatant(a.characterId(),step,a.side(),a.actionAvailable(),a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),a.sequence(),a.prone());
                trial=rebuild(trial,trial.combatants().stream().map(v->v.characterId().equals(route.actor())?moved:v).toList(),trial.flows(),trial.readyPreparations(),trial.triggerFacts(),trial.shields(),trial.concentrationHolders(),trial.version());
                var result=Schema2OngoingEffectRules.reconcile(trial,proof,++ordinal);trial=Schema2OngoingEffectRules.withCauses(trial,result.causes());
            }
            bound+=trial.suppressionCauses().size()-base.suppressionCauses().size();
        }
        require(bound<=Schema2OngoingEffectRules.MAX_CAUSES,"ACTION_FLOW_LIMIT_EXCEEDED");
    }
    private static void addProspectiveBless(List<OngoingEffect> effects,UUID owner,UUID actor,RuleIdentity spell,List<UUID> targets,Map<UUID,Context> roster) {
        UUID effect=key(owner,"ongoing-effect");if(effects.stream().anyMatch(e->e.effectKey().equals(effect)))return;long now=Schema2SpellRules.second(roster.get(actor).character());effects.add(new OngoingEffect(effect,spell,actor,targets,now,now+60,EffectStatus.ACTIVE,null));
    }
    private static boolean isBless(Map<UUID,Context> roster,UUID actor,RuleIdentity spell) {return roster.get(actor).material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).anyMatch(s->s.enabled()&&s.identity().equals(spell)&&Schema2SpellRules.BLESS.equals(s.definition().algorithm()));}
    /** Commands needed to finish by passing every still optional opportunity; each new child reserves its own suffix before acceptance. */
    int mandatorySuffix(Map<UUID,Context> roster,Battle b,Command command) {
        Flow flow=b==null?null:b.activeFlow();int result=0;
        if(flow!=null) {
            for(Window w:flow.windows())if(w.status()!=WindowStatus.CONSUMED)result+=1+(int)w.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).count();
            for(Frame frame:flow.frames())if(frame.status()!=FrameStatus.TERMINAL&&frame.kind()!=FrameKind.REACTION_SELECTION) {
                if(frame.kind()==FrameKind.ATTACK&&frame.stage()!=Stage.READY_TRIGGER_AFTER) {
                    AttackAttempt attack=flow.attacks().stream().filter(v->v.frameKey().equals(frame.frameKey())).findFirst().orElseThrow();result+=readyCost(roster,b,TriggerKind.ATTACK_COMPLETED,attack.actorId(),null);
                } else if(frame.kind()==FrameKind.CAST&&frame.stage()!=Stage.READY_TRIGGER_AFTER) {
                    CastAttempt cast=flow.casts().stream().filter(v->v.frameKey().equals(frame.frameKey())).findFirst().orElseThrow();result+=readyCost(roster,b,TriggerKind.CAST_COMPLETED,cast.actorId(),null);
                } else if(frame.kind()==FrameKind.MOVEMENT) {
                    Movement move=flow.movements().stream().filter(v->v.movementKey().equals(frame.ownerKey())).findFirst().orElseThrow();result+=movementCost(roster,b,flow,frame.frameKey(),move.actorId(),move.path().subList(move.cursor(),move.path().size()),move.cursor());
                }
            }
            // The command itself is accounted by the service separately.
            result=Math.max(0,result-1);
        }
        if(command.kind()==Kind.MOVE)result+=movementCost(roster,b,null,null,command.actorId(),command.path(),0);
        else if(command.kind()==Kind.ATTACK)result+=windowCost(shieldCandidates(roster,b,command.targetId()))+readyCost(roster,b,TriggerKind.ATTACK_COMPLETED,command.actorId(),null);
        else if(command.kind()==Kind.CAST||command.kind()==Kind.READY_SPELL)result+=windowCost(counterCandidates(roster,b,command.actorId()))+readyCost(roster,b,TriggerKind.CAST_COMPLETED,command.actorId(),null);
        else if(command.kind()==Kind.DECLARE_OPPORTUNITY_ATTACK) {
            Window w=window(flow,command.windowKey());result+=windowCost(shieldCandidates(roster,b,w.trigger().subjectId()))+readyCost(roster,b,TriggerKind.ATTACK_COMPLETED,command.actorId(),null);
        } else if(command.kind()==Kind.DECLARE_SHIELD||command.kind()==Kind.DECLARE_COUNTERSPELL)result+=windowCost(counterCandidates(roster,b,command.actorId()))+readyCost(roster,b,TriggerKind.CAST_COMPLETED,command.actorId(),null);
        else if(command.kind()==Kind.RELEASE_READY) {
            ReadyPreparation r=ready(b,command.readyKey());if(r.input().responseKind()==ReadyResponseKind.MOVE)result+=movementCost(roster,b,null,null,r.actorId(),r.input().path(),0);
            if(r.input().responseKind()==ReadyResponseKind.ATTACK)result+=windowCost(shieldCandidates(roster,b,r.input().targetId()))+readyCost(roster,b,TriggerKind.ATTACK_COMPLETED,r.actorId(),null);
        }
        return result;
    }
    private int readyCost(Map<UUID,Context> roster,Battle b,TriggerKind kind,UUID subject,Position position) {
        TriggerFact t=new TriggerFact(key(subject,"frontier-trigger"),kind,subject,subject,null,position,0);
        return windowCost((int)b.readyPreparations().stream().filter(r->r.status()==ReadyStatus.ARMED&&actor(b,r.actorId()).reactionAvailable()&&roster.get(r.actorId()).character().hitPoints()>0&&matches(r.input().trigger(),t)).map(ReadyPreparation::actorId).distinct().count());
    }
    private int shieldCandidates(Map<UUID,Context> roster,Battle b,UUID target) {if(!roster.containsKey(target))return 0;if(!actor(b,target).reactionAvailable()||roster.get(target).character().hitPoints()==0)return 0;return spells.reactionOptions(roster,b,target,ReactionKind.SHIELD,null).isEmpty()?0:1;}
    private int counterCandidates(Map<UUID,Context> roster,Battle b,UUID caster) {
        int count=0;for(var entry:roster.entrySet()) {
            UUID id=entry.getKey();State state=entry.getValue().character();if(id.equals(caster)||!actor(b,id).reactionAvailable()||state.hitPoints()==0||state.spellbook()==null||actor(b,id).position().distanceFeet(actor(b,caster).position())>60)continue;
            boolean available=entry.getValue().material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).anyMatch(s->s.enabled()&&Schema2SpellRules.COUNTERSPELL.equals(s.definition().algorithm())&&state.spellbook().prepared().contains(s.identity()));
            if(available&&state.resources().stream().anyMatch(r->r.current()>0&&entry.getValue().material().projection().rules().stream().filter(FinalWizardSpellcasting.class::isInstance).map(FinalWizardSpellcasting.class::cast).anyMatch(p->p.enabled()&&p.definition().rank3SlotResource().equals(r.identity()))))count++;
        }return count;
    }
    private int movementCost(Map<UUID,Context> roster,Battle b,Flow flow,UUID owner,UUID subject,List<Position> path,int initialCursor) {
        Battle projected=b;int result=0,index=initialCursor;
        for(Position next:path) {
            TriggerFact leave=new TriggerFact(key(subject,"frontier-leave-"+index),TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE,subject,subject,null,next,index);
            int ordinal=index;boolean already=flow!=null&&flow.windows().stream().anyMatch(w->w.ownerFrameKey().equals(owner)&&w.trigger().kind()==TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE&&w.trigger().ordinal()==ordinal);
            if(!already)result+=windowCost(candidates(roster,projected,null,leave).size());
            Combatant actor=actor(projected,subject);Combatant moved=new Combatant(subject,next,actor.side(),actor.actionAvailable(),actor.reactionAvailable(),actor.movementSpentHalfFeet(),actor.movementBonus(),actor.disengaged(),actor.dodging(),actor.sequence(),actor.prone());
            projected=rebuild(projected,projected.combatants().stream().map(a->a.characterId().equals(subject)?moved:a).toList(),projected.flows(),projected.readyPreparations(),projected.triggerFacts(),projected.shields(),projected.concentrationHolders(),projected.version());
            result+=readyCost(roster,projected,TriggerKind.CREATURE_ENTER_CELL_AFTER,subject,next);index++;
        }return result;
    }
    private static int windowCost(int candidates) {return candidates==0?0:candidates+1;}
    Resolution execute(UUID operation,Map<UUID,Context> roster,Battle b,Command c,Roller roller) {
        Work w=new Work(operation,roster,b,roller);
        if(continuation(c.kind()))w.decide(c);
        else if(c.kind()==Kind.READY_ATTACK||c.kind()==Kind.READY_MOVE||c.kind()==Kind.READY_SPELL)w.prepareReady(c);
        else if(c.kind()==Kind.ATTACK)w.beginAttack(c.actorId(),c.targetId(),c.itemId(),null,Invocation.ROOT,false,false);
        else if(c.kind()==Kind.MOVE)w.beginMovement(c.actorId(),c.path(),null,Invocation.ROOT,false);
        else w.beginCast(c.actorId(),c.castInput(),null,Invocation.ROOT,null);
        Battle after=w.snapshot();after=rebuild(after,after.combatants(),after.flows(),after.readyPreparations(),after.triggerFacts(),after.shields(),after.concentrationHolders(),b.version()+1);
        w.facts.add(new CombatFact(c.kind().name(),c.actorId(),b.combatId(),null));
        return new Resolution(after,w.changes,List.of(),w.facts);
    }
    private final class Work {
        final UUID operation;final Map<UUID,Context> roster;Battle b;Flow f;final Roller dice;
        final List<HealthChange> changes=new ArrayList<>();final List<CombatFact> facts=new ArrayList<>();
        final List<Frame> frames=new ArrayList<>();final List<Window> windows=new ArrayList<>();final List<Movement> moves=new ArrayList<>();final List<AttackAttempt> attacks=new ArrayList<>();final List<CastAttempt> casts=new ArrayList<>();final List<SavingThrowAttempt> saves=new ArrayList<>();final List<ReadyRelease> releases=new ArrayList<>();final List<AbilityCheckAttempt> checks=new ArrayList<>();
        UUID flowKey,root,leaf;FlowStatus flowStatus;int serial,boundarySerial;
        Work(UUID operation,Map<UUID,Context> contexts,Battle before,Roller dice) {
            this.operation=operation;this.roster=new HashMap<>(contexts);this.b=before;this.dice=dice;f=before.activeFlow();
            if(f!=null) {flowKey=f.flowKey();root=f.rootFrameKey();leaf=f.leafFrameKey();flowStatus=f.status();frames.addAll(f.frames());windows.addAll(f.windows());moves.addAll(f.movements());attacks.addAll(f.attacks());casts.addAll(f.casts());saves.addAll(f.savingThrows());releases.addAll(f.readyReleases());checks.addAll(f.abilityChecks());}
        }
        UUID next(String role) {return key(operation,role+"-"+serial++);}
        Frame get(UUID id) {return frames.stream().filter(v->v.frameKey().equals(id)).findFirst().orElseThrow(()->invalid("ACTION_FLOW_TARGET_MISMATCH"));}
        void set(Frame frame) {for(int i=0;i<frames.size();i++)if(frames.get(i).frameKey().equals(frame.frameKey())) {frames.set(i,frame);return;}frames.add(frame);}
        void stage(UUID id,FrameStatus status,Stage stage,UUID window,UUID opportunity) {Frame old=get(id);set(new Frame(id,old.parentFrameKey(),old.kind(),old.ownerKey(),old.invocation(),status,stage,window,opportunity));}
        Flow flow() {return new Flow(flowKey,root,leaf,flowStatus,frames,windows,moves,attacks,casts,saves,releases,checks);}
        Battle snapshot() {
            if(flowKey==null)return b;Flow updated=flow();List<Flow> all=new ArrayList<>(b.flows());int i=0;while(i<all.size()&&!all.get(i).flowKey().equals(flowKey))i++;
            if(i==all.size())all.add(updated);else all.set(i,updated);
            b=rebuild(b,b.combatants(),all,b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.version());return b;
        }
        void actor(Combatant value) {boolean moved=!Schema2ActionFlowRules.actor(b,value.characterId()).position().equals(value.position());b=rebuild(b,b.combatants().stream().map(a->a.characterId().equals(value.characterId())?value:a).toList(),b.flows(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.version());if(moved)reconcileBoundary();}
        void reconcileBoundary() {var result=Schema2OngoingEffectRules.reconcile(snapshot(),operation,++boundarySerial);b=Schema2OngoingEffectRules.withCauses(b,result.causes());facts.addAll(result.facts());}
        void change(HealthChange value) {changes.add(value);Context old=roster.get(value.characterId());roster.put(value.characterId(),new Context(old.material(),old.initial(),value.after()));}
        void ready(ReadyPreparation value) {List<ReadyPreparation> all=new ArrayList<>(b.readyPreparations());int i=0;while(i<all.size()&&!all.get(i).readyKey().equals(value.readyKey()))i++;if(i==all.size())all.add(value);else all.set(i,value);b=rebuild(b,b.combatants(),b.flows(),all,b.triggerFacts(),b.shields(),b.concentrationHolders(),b.version());}
        void holder(ConcentrationHolder value) {List<ConcentrationHolder> all=new ArrayList<>(b.concentrationHolders().stream().filter(h->!h.actorId().equals(value.actorId())).toList());all.add(value);b=rebuild(b,b.combatants(),b.flows(),b.readyPreparations(),b.triggerFacts(),b.shields(),all,b.version());}
        void endConcentration(UUID subject,String reason) {b=Schema2SpellRules.end(snapshot(),subject,reason,facts);}
        UUID addFrame(FrameKind kind,UUID owner,UUID parent,Invocation invocation,Stage stage) {
            UUID id=owner; if(flowKey==null) {flowKey=next("flow");root=id;flowStatus=FlowStatus.ACTIVE;}
            Frame frame=new Frame(id,parent,kind,owner,invocation,FrameStatus.RUNNABLE,stage,null,null);frames.add(frame);leaf=id;
            if(parent!=null) {
                Frame prior=get(parent);
                if(prior.kind()==FrameKind.REACTION_SELECTION) {Frame ownerFrame=get(prior.parentFrameKey());stage(ownerFrame.frameKey(),FrameStatus.WAITING_CHILD,ownerFrame.stage(),ownerFrame.windowKey(),ownerFrame.opportunityKey());}
                else stage(parent,FrameStatus.WAITING_CHILD,prior.stage(),prior.windowKey(),prior.opportunityKey());
            }return id;
        }
        void spendReaction(UUID subject) {Combatant a=Schema2ActionFlowRules.actor(b,subject);actor(new Combatant(a.characterId(),a.position(),a.side(),a.actionAvailable(),false,a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),a.sequence(),a.prone()));}
        void spendAction(UUID subject) {Combatant a=Schema2ActionFlowRules.actor(b,subject);actor(new Combatant(a.characterId(),a.position(),a.side(),false,a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),a.sequence(),a.prone()));}
        void beginAttack(UUID subject,UUID target,UUID item,UUID parent,Invocation invocation,boolean oa,boolean readied) {
            UUID id=next("attack");addFrame(FrameKind.ATTACK,id,parent,invocation,Stage.ATTACK_HIT_BEFORE_DAMAGE);
            Combatant a=Schema2ActionFlowRules.actor(b,subject),t=Schema2ActionFlowRules.actor(b,target);Context source=roster.get(subject),dest=roster.get(target);WeaponAttackDefinition profile=combat.weapon(source,item);
            int ability=mod(source.character(),"ability.strength");if(profile.abilityPolicy()==WeaponAbilityPolicy.FINESSE)ability=Math.max(ability,mod(source.character(),"ability.dexterity"));
            EquipmentItem instance=source.character().equipment().stream().filter(i->i.itemId().equals(item)).findFirst().orElseThrow();
            RuleIdentity proficiency=source.material().projection().rules().stream().filter(FinalItem.class::isInstance).map(FinalItem.class::cast).filter(v->v.enabled()&&v.identity().equals(instance.item())).findFirst().orElseThrow().definition().proficiency();
            int pb=source.character().proficiencies().stream().anyMatch(v->v.target().equals(proficiency))?source.character().proficiencyBonus():0;
            int distance=a.position().distanceFeet(t.position());boolean advantage=targetHp(target)==0||t.prone()&&distance<=5;
            boolean disadvantage=equipment.derive(source.material(),source.character()).physicalCheckDisadvantage()||t.dodging()&&targetHp(target)>0&&combat.speed(roster,b,target)>0||t.prone()&&distance>5||a.prone();
            int first=dice.draw("ATTACK_D20",subject,20);Integer second=advantage!=disadvantage?dice.draw("ATTACK_D20_SECOND",subject,20):null;int selected=second==null?first:advantage?Math.max(first,second):Math.min(first,second);
            OngoingEffect blessing=Schema2SpellRules.blessing(snapshot(),subject);Integer d4=blessing==null?null:dice.draw("BLESS_ATTACK",subject,4);int ac=armor(target);boolean hit=selected==20||selected!=1&&selected+ability+pb+(d4==null?0:d4)>=ac;
            attacks.add(new AttackAttempt(id,subject,target,item,oa||readied?null:a.sequence().sequenceKey(),oa||readied?0:a.sequence().performed(),oa,first,second,selected,ability,pb,ac,advantage,disadvantage,hit&&(selected==20||targetHp(target)==0&&distance<=5),hit,List.of(),0,blessing==null?null:blessing.effectKey(),d4,hit?AttackStatus.HIT_PENDING_DAMAGE:AttackStatus.RESOLVED,hit,ac));
            if(hit&&open(id,TriggerKind.ATTACK_HIT_BEFORE_DAMAGE,subject,target,null,0,0))return;settleAttack(id);
        }
        int targetHp(UUID subject) {return roster.containsKey(subject)?Math.toIntExact(roster.get(subject).character().hitPoints()):Schema2MonsterRules.instance(b,subject).hitPoints();}
        int armor(UUID subject) {Context c=roster.get(subject);int ac=c==null?Schema2MonsterRules.definition(roster,Schema2MonsterRules.instance(b,subject).monster()).statBlock().armorClass():equipment.derive(c.material(),c.character()).armorClass();if(b.shields().stream().anyMatch(e->e.actorId().equals(subject)&&e.status()==EffectStatus.ACTIVE&&!Schema2OngoingEffectRules.isSuppressed(b,e.effectKey(),subject)))ac+=5;return ac;}
        AttackAttempt attack(UUID id) {return attacks.stream().filter(a->a.frameKey().equals(id)).findFirst().orElseThrow();}
        void attack(AttackAttempt value) {for(int i=0;i<attacks.size();i++)if(attacks.get(i).frameKey().equals(value.frameKey()))attacks.set(i,value);}
        void settleAttack(UUID id) {
            AttackAttempt a=attack(id);int ac=armor(a.targetId());boolean hit=a.selectedD20()==20||a.selectedD20()!=1&&a.selectedD20()+a.abilityModifier()+a.proficiencyBonus()+(a.blessingD4()==null?0:a.blessingD4())>=ac;
            Context source=roster.get(a.actorId()),dest=roster.get(a.targetId());WeaponAttackDefinition profile=combat.weapon(source,a.itemId());
            boolean critical=hit&&(a.selectedD20()==20||targetHp(a.targetId())==0&&Schema2ActionFlowRules.actor(b,a.actorId()).position().distanceFeet(Schema2ActionFlowRules.actor(b,a.targetId()).position())<=5);
            List<Integer> damage=new ArrayList<>();int amount=0;
            if(hit) {for(int i=0;i<profile.diceCount()*(critical?2:1);i++)damage.add(dice.draw("DAMAGE",a.actorId(),profile.diceSides()));amount=Math.max(0,damage.stream().mapToInt(Integer::intValue).sum()+a.abilityModifier());amount=Schema2MonsterRules.effectiveDamage(roster,b,a.targetId(),profile.damageType(),true,amount);}
            attack(new AttackAttempt(a.frameKey(),a.actorId(),a.targetId(),a.itemId(),a.sequenceKey(),a.attackOrdinal(),a.opportunityAttack(),a.firstD20(),a.secondD20(),a.selectedD20(),a.abilityModifier(),a.proficiencyBonus(),a.armorClass(),a.advantage(),a.disadvantage(),critical,hit,damage,amount,a.blessingEffectKey(),a.blessingD4(),AttackStatus.RESOLVED,a.initialHit(),ac));
            if(amount>0) {
                if(dest==null)b=Schema2MonsterRules.damage(b,a.targetId(),amount);
                else {
                    var result=health.applyDamage(dest.material(),dest.character(),amount,critical);State after=result.state().hitPoints()==0?Schema2CombatEquipmentFacts.drop(result.state(),b.combatId(),Schema2ActionFlowRules.actor(b,a.targetId()).position()):result.state();
                    change(new HealthChange(a.targetId(),dest.character(),after,id,amount,critical,new HealthResult(List.of(),null,null,amount,result.appliedAmount()),false));afterDamage(id,a.targetId(),amount);
                }
                if(targetHp(a.targetId())==0) {Combatant t=Schema2ActionFlowRules.actor(b,a.targetId());actor(new Combatant(t.characterId(),t.position(),t.side(),false,t.reactionAvailable(),t.movementSpentHalfFeet(),t.movementBonus(),t.disengaged(),t.dodging(),t.sequence(),true));}
            }
            if(a.sequenceKey()!=null) {Combatant actor=Schema2ActionFlowRules.actor(b,a.actorId());AttackSequence s=actor.sequence();int performed=s.performed()+1;actor(new Combatant(actor.characterId(),actor.position(),actor.side(),actor.actionAvailable(),actor.reactionAvailable(),actor.movementSpentHalfFeet(),actor.movementBonus(),actor.disengaged(),actor.dodging(),new AttackSequence(s.sequenceKey(),s.limit(),performed,s.forfeited(),performed+s.forfeited()==s.limit()),actor.prone()));}
            if(open(id,TriggerKind.ATTACK_COMPLETED,a.actorId(),a.targetId(),null,0,0))return;finish(id,Stage.ATTACK_RESOLVED);
        }
        void afterDamage(UUID frame,UUID subject,int amount) {
            if(b.concentrationHolders().stream().noneMatch(h->h.actorId().equals(subject)))return;State state=roster.get(subject).character();
            if(state.hitPoints()==0||state.health().dead()) {endConcentration(subject,"INCAPACITATED");return;}
            int dc=Math.max(10,amount/2),ability=mod(state,"ability.constitution"),d20=dice.draw("CONCENTRATION_D20",subject,20);OngoingEffect blessing=Schema2SpellRules.blessing(snapshot(),subject);Integer d4=blessing==null?null:dice.draw("BLESS_CONCENTRATION",subject,4);int pb=Schema2SavingThrowRules.proficiency(roster.get(subject),"ability.constitution"),total=d20+ability+pb+(d4==null?0:d4);
            saves.add(new SavingThrowAttempt(frame,subject,"CONCENTRATION","ability.constitution",d20,ability,pb,blessing==null?null:blessing.effectKey(),d4,dc,total,total>=dc));if(total<dc)endConcentration(subject,"CONCENTRATION_SAVE_FAILED");
        }
        void beginMovement(UUID subject,List<Position> path,UUID parent,Invocation invocation,boolean readied) {
            UUID id=next("movement");addFrame(FrameKind.MOVEMENT,id,parent,invocation,Stage.MOVEMENT_CONTINUE);moves.add(new Movement(id,subject,Schema2ActionFlowRules.actor(b,subject).position(),path,0,0,MovementStatus.PENDING,null,readied,readied?combat.speed(roster.get(subject)):0));driveMovement(id);
        }
        Movement move(UUID id) {return moves.stream().filter(m->m.movementKey().equals(id)).findFirst().orElseThrow();}
        void move(Movement value) {for(int i=0;i<moves.size();i++)if(moves.get(i).movementKey().equals(value.movementKey()))moves.set(i,value);}
        void driveMovement(UUID id) {
            Movement m=move(id);Combatant actor=Schema2ActionFlowRules.actor(b,m.actorId());State current=roster.get(m.actorId()).character();
            if(current.hitPoints()==0||current.health().dead()||actor.prone()) {stopMovement(id,"ACTOR_INACTIVE_OR_NO_MOVEMENT");return;}
            Position expected=m.cursor()==0?m.origin():m.path().get(m.cursor()-1);if(!actor.position().equals(expected)) {stopMovement(id,"POSITION_CHANGED");return;}
            while(m.cursor()<m.path().size()) {
                actor=Schema2ActionFlowRules.actor(b,m.actorId());Position next=m.path().get(m.cursor());int allowance=m.readied()?Math.min(m.allowanceFeet(),combat.speed(roster.get(m.actorId())))-m.spent():combat.speed(roster.get(m.actorId()))+actor.movementBonus()-actor.movementSpentHalfFeet()/2;
                UUID movingActor=m.actorId();
                if(allowance<5||actor.position().distanceFeet(next)!=5||b.combatants().stream().anyMatch(v->!v.characterId().equals(movingActor)&&v.position().equals(next))) {stopMovement(id,"SUFFIX_UNAVAILABLE");return;}
                if(!consumed(id,TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE,m.cursor())&&open(id,TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE,m.actorId(),null,next,m.cursor(),m.cursor()))return;
                actor(new Combatant(actor.characterId(),next,actor.side(),actor.actionAvailable(),actor.reactionAvailable(),actor.movementSpentHalfFeet()+(m.readied()?0:10),actor.movementBonus(),actor.disengaged(),actor.dodging(),actor.sequence(),actor.prone()));
                m=new Movement(m.movementKey(),m.actorId(),m.origin(),m.path(),m.cursor()+1,m.spent()+5,MovementStatus.PENDING,null,m.readied(),m.allowanceFeet());move(m);
                if(open(id,TriggerKind.CREATURE_ENTER_CELL_AFTER,m.actorId(),null,next,m.cursor()-1,m.cursor()-1))return;
            }
            move(new Movement(m.movementKey(),m.actorId(),m.origin(),m.path(),m.cursor(),m.spent(),MovementStatus.COMPLETED,null,m.readied(),m.allowanceFeet()));facts.add(new CombatFact("MOVEMENT_COMPLETED",m.actorId(),m.movementKey(),null));finish(id,Stage.MOVEMENT_COMPLETED);
        }
        void stopMovement(UUID id,String reason) {Movement m=move(id);move(new Movement(id,m.actorId(),m.origin(),m.path(),m.cursor(),m.spent(),MovementStatus.STOPPED,reason,m.readied(),m.allowanceFeet()));facts.add(new CombatFact("MOVEMENT_STOPPED",m.actorId(),id,reason));finish(id,Stage.MOVEMENT_STOPPED);}
        void beginCast(UUID subject,CastInput input,UUID parent,Invocation invocation,UUID readyKey) {
            var prepared=spells.prepare(roster,snapshot(),subject,input,invocation==Invocation.REACTION);UUID id=next("cast");addFrame(FrameKind.CAST,id,parent,invocation,Stage.CAST_BEFORE_COMPLETION);
            State paid=spells.spend(prepared);change(new HealthChange(subject,prepared.caster(),paid,id,0,false,null,false,false,0,prepared.slotResource(),1,false));
            if(invocation==Invocation.ROOT&&b.status()==Status.ACTIVE)spendAction(subject);
            if(prepared.concentration()||readyKey!=null)endConcentration(subject,"REPLACED_BY_CONCENTRATION");
            casts.add(new CastAttempt(id,subject,prepared.castingIdentity(),input.spell(),input.targets(),prepared.slotResource(),input.slotLevel(),input.materialItemId(),List.of(),prepared.concentration()?0:prepared.healingModifier(),0,0,null,CastStatus.PENDING,input.targetCastFrameKey(),readyKey));
            if(readyKey!=null) {ReadyPreparation r=Schema2ActionFlowRules.ready(b,readyKey);ready(new ReadyPreparation(r.readyKey(),r.actorId(),r.declaredTurnNumber(),r.expiresAtTurnNumber(),r.input(),r.status(),id,null));holder(new ConcentrationHolder(subject,ConcentrationKind.READY_SPELL,readyKey));}
            if(open(id,TriggerKind.CAST_BEFORE_COMPLETION,subject,null,null,0,0))return;completeCast(id,false);
        }
        CastAttempt cast(UUID id) {return casts.stream().filter(c->c.frameKey().equals(id)).findFirst().orElseGet(()->Schema2SpellRules.findCast(snapshot(),id));}
        void cast(CastAttempt value) {
            for(int i=0;i<casts.size();i++)if(casts.get(i).frameKey().equals(value.frameKey())) {casts.set(i,value);return;}
            List<Flow> all=new ArrayList<>();for(Flow old:b.flows())all.add(new Flow(old.flowKey(),old.rootFrameKey(),old.leafFrameKey(),old.status(),old.frames(),old.windows(),old.movements(),old.attacks(),old.casts().stream().map(c->c.frameKey().equals(value.frameKey())?value:c).toList(),old.savingThrows(),old.readyReleases(),old.abilityChecks()));
            b=rebuild(b,b.combatants(),all,b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.version());
        }
        CastAttempt castResult(CastAttempt c,CastStatus status,List<Integer> healing,int requested,int applied,UUID effect) {
            return new CastAttempt(c.frameKey(),c.actorId(),c.castingRule(),c.spell(),c.targets(),c.slotResource(),c.slotLevel(),c.materialItemId(),healing,c.healingModifier(),requested,applied,effect,status,c.targetCastFrameKey(),c.readyKey());
        }
        String algorithm(CastAttempt c) {return roster.get(c.actorId()).material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).filter(s->s.enabled()&&s.identity().equals(c.spell())).findFirst().orElseThrow(()->invalid("S2_SPELL_UNAVAILABLE")).definition().algorithm();}
        void completeCast(UUID id,boolean release) {
            CastAttempt c=cast(id);
            if(!release&&get(id).parentFrameKey()==null&&b.status()==Status.EXPLORATION) {
                var time=Schema2ExplorationRules.advanceTime(roster,6,id);for(HealthChange value:time.changes())change(value);
                b=Schema2ExplorationRules.expire(b,Schema2SpellRules.second(roster.get(c.actorId()).character()),facts);reconcileBoundary();
                facts.add(new CombatFact("CAST_TIME_ADVANCED",c.actorId(),id,null));
            }
            if(c.status()==CastStatus.COUNTERED) {
                if(c.readyKey()!=null) {ReadyPreparation r=Schema2ActionFlowRules.ready(b,c.readyKey());ready(new ReadyPreparation(r.readyKey(),r.actorId(),r.declaredTurnNumber(),r.expiresAtTurnNumber(),r.input(),ReadyStatus.LOST,r.heldCastFrameKey(),"COUNTERED"));endConcentration(c.actorId(),"COUNTERED");}
            } else if(c.readyKey()!=null&&!release) {
                ReadyPreparation r=Schema2ActionFlowRules.ready(b,c.readyKey());
                if(r.status()==ReadyStatus.PREPARING) {cast(castResult(c,CastStatus.HELD,List.of(),0,0,null));ready(new ReadyPreparation(r.readyKey(),r.actorId(),r.declaredTurnNumber(),r.expiresAtTurnNumber(),r.input(),ReadyStatus.ARMED,r.heldCastFrameKey(),null));}
                else cast(castResult(c,CastStatus.RESOLVED,List.of(),0,0,null));
            } else applySpell(c,release?id:id);
            if(release)return;
            if(open(id,TriggerKind.CAST_COMPLETED,c.actorId(),null,null,0,0))return;finish(id,Stage.CAST_RESOLVED);
        }
        void applySpell(CastAttempt c,UUID resultFrame) {
            String algorithm=algorithm(c);List<Integer> healing=new ArrayList<>();int requested=0,applied=0;UUID effect=null;
            if(Schema2SpellRules.CURE_WOUNDS.equals(algorithm)) {
                UUID target=c.targets().getFirst();State before=roster.get(target).character();
                if(!before.health().dead()&&Schema2ActionFlowRules.actor(b,c.actorId()).position().distanceFeet(Schema2ActionFlowRules.actor(b,target).position())<=5) {
                    healing.add(dice.draw("CURE_WOUNDS_HEALING",c.actorId(),8));requested=Math.max(0,healing.getFirst()+c.healingModifier());var healed=health.applyHealing(before,requested);applied=healed.result().appliedAmount();
                    change(new HealthChange(target,before,healed.state(),resultFrame,requested,false,new HealthResult(healing,null,null,requested,applied),false,false,0,null,0,true));
                }
                if(c.readyKey()!=null)endConcentration(c.actorId(),"READY_RELEASED");
            } else if(Schema2SpellRules.BLESS.equals(algorithm)) {
                endConcentration(c.actorId(),"REPLACED_BY_CONCENTRATION");effect=key(c.frameKey(),"ongoing-effect");long second=Schema2SpellRules.second(roster.get(c.actorId()).character());
                List<OngoingEffect> effects=new ArrayList<>(b.ongoingEffects());effects.add(new OngoingEffect(effect,c.spell(),c.actorId(),c.targets(),second,second+60,EffectStatus.ACTIVE,null));b=Schema2SpellRules.withEffects(b,effects);holder(new ConcentrationHolder(c.actorId(),ConcentrationKind.BLESS_EFFECT,effect));facts.add(new CombatFact("ONGOING_EFFECT_STARTED",c.actorId(),effect,null));
            } else if(Schema2SpellRules.SHIELD.equals(algorithm)) {
                effect=key(c.frameKey(),"shield");List<ShieldEffect> all=new ArrayList<>(b.shields());int ordinal=b.initiative().stream().filter(i->i.characterId().equals(c.actorId())).findFirst().orElseThrow().ordinal();
                long until=b.turnNumber()+(ordinal-b.activeOrdinal()+b.initiative().size())%b.initiative().size();if(until==b.turnNumber())until+=b.initiative().size();
                all.add(new ShieldEffect(effect,c.frameKey(),c.actorId(),b.turnNumber(),until,EffectStatus.ACTIVE,null));b=rebuild(b,b.combatants(),b.flows(),b.readyPreparations(),b.triggerFacts(),all,b.concentrationHolders(),b.version());facts.add(new CombatFact("SHIELD_STARTED",c.actorId(),effect,null));
            } else if(Schema2SpellRules.COUNTERSPELL.equals(algorithm)) {
                CastAttempt target=cast(c.targetCastFrameKey());require(target!=null&&target.status()==CastStatus.PENDING,"ACTION_FLOW_STAGE_INVALID");int level=Math.max(target.slotLevel(),Schema2SpellRules.spellLevel(roster.get(target.actorId()),target.spell()));boolean success=c.slotLevel()>=level;
                if(!success) {
                    String ability="ability.intelligence";int d20=dice.draw("COUNTERSPELL_CHECK",c.actorId(),20),modifier=mod(roster.get(c.actorId()).character(),ability),dc=10+level,total=d20+modifier;
                    checks.add(new AbilityCheckAttempt(resultFrame,c.actorId(),"COUNTERSPELL",ability,d20,modifier,0,dc,total,total>=dc));success=total>=dc;
                }
                if(success)cast(castResult(target,CastStatus.COUNTERED,List.of(),0,0,null));facts.add(new CombatFact(success?"CAST_COUNTERED":"COUNTERSPELL_FAILED",c.actorId(),target.frameKey(),null));
            } else throw invalid("S2_SPELL_UNAVAILABLE");
            cast(castResult(c,CastStatus.RESOLVED,healing,requested,applied,effect));reconcileBoundary();
        }
        void prepareReady(Command c) {
            UUID key=next("ready");ReadyPreparation ready=new ReadyPreparation(key,c.actorId(),b.turnNumber(),b.turnNumber()+b.initiative().size(),c.readyInput(),c.kind()==Kind.READY_SPELL?ReadyStatus.PREPARING:ReadyStatus.ARMED,null,null);ready(ready);
            if(c.kind()==Kind.READY_SPELL)beginCast(c.actorId(),c.readyInput().spell(),null,Invocation.ROOT,key);
            else spendAction(c.actorId());facts.add(new CombatFact("READY_PREPARED",c.actorId(),key,null));
        }
        void beginReadyRelease(UUID readyKey,TriggerFact trigger,UUID parent) {
            ReadyPreparation r=Schema2ActionFlowRules.ready(b,readyKey);ready(new ReadyPreparation(r.readyKey(),r.actorId(),r.declaredTurnNumber(),r.expiresAtTurnNumber(),r.input(),ReadyStatus.RELEASED,r.heldCastFrameKey(),null));
            UUID id=next("ready-release");addFrame(FrameKind.READY_RELEASE,id,parent,Invocation.REACTION,Stage.READY_RELEASE_RESOLVED);releases.add(new ReadyRelease(id,readyKey,trigger.triggerKey(),false,null));
            ReadyInput input=r.input();
            if(input.responseKind()==ReadyResponseKind.ATTACK) {
                beginAttack(r.actorId(),input.targetId(),input.itemId(),id,Invocation.CONTINUATION,false,true);
            } else if(input.responseKind()==ReadyResponseKind.MOVE)beginMovement(r.actorId(),input.path(),id,Invocation.CONTINUATION,true);
            else {
                CastAttempt held=cast(r.heldCastFrameKey());require(held!=null&&held.status()==CastStatus.HELD,"S2_READY_HELD_SPELL_INVALID");
                if(input.spell().targets().stream().anyMatch(t->Schema2ActionFlowRules.actor(b,r.actorId()).position().distanceFeet(Schema2ActionFlowRules.actor(b,t).position())>(Schema2SpellRules.BLESS.equals(algorithm(held))?30:5))) {
                    endConcentration(r.actorId(),"READY_RELEASED");cast(castResult(held,CastStatus.RESOLVED,List.of(),0,0,null));finishRelease(id,"TARGET_UNAVAILABLE");return;
                }
                applySpell(held,id);finishRelease(id,"RELEASED");
            }
        }
        void finishRelease(UUID id,String outcome) {for(int i=0;i<releases.size();i++)if(releases.get(i).frameKey().equals(id)) {ReadyRelease old=releases.get(i);releases.set(i,new ReadyRelease(id,old.readyKey(),old.triggerKey(),true,outcome));}finish(id,Stage.READY_RELEASE_RESOLVED);}
        boolean consumed(UUID owner,TriggerKind kind,int ordinal) {return windows.stream().anyMatch(w->w.ownerFrameKey().equals(owner)&&w.trigger().kind()==kind&&w.trigger().ordinal()==ordinal&&w.status()==WindowStatus.CONSUMED);}
        boolean open(UUID owner,TriggerKind kind,UUID subject,UUID target,Position position,int ordinal,int boundary) {
            if(consumed(owner,kind,ordinal))return false;
            TriggerFact trigger=new TriggerFact(key(owner,kind.name()+"-"+ordinal),kind,owner,subject,target,position,ordinal);
            if(b.triggerFacts().stream().noneMatch(t->t.triggerKey().equals(trigger.triggerKey()))) {List<TriggerFact> all=new ArrayList<>(b.triggerFacts());all.add(trigger);b=rebuild(b,b.combatants(),b.flows(),b.readyPreparations(),all,b.shields(),b.concentrationHolders(),b.version());}
            List<Opportunity> options=candidates(roster,snapshot(),flow(),trigger);if(options.isEmpty())return false;
            UUID window=next("window");List<Opportunity> ordered=new ArrayList<>(options);Opportunity first=ordered.getFirst();UUID selection=next("selection");first=new Opportunity(first.opportunityKey(),first.actorId(),first.priority(),first.options(),first.status(),selection,null,null);ordered.set(0,first);
            Stage stage=switch(kind) {case MOVEMENT_LEAVE_REACH_BEFORE->Stage.MOVEMENT_LEAVE_REACH_BEFORE;case ATTACK_HIT_BEFORE_DAMAGE->Stage.ATTACK_HIT_BEFORE_DAMAGE;case CAST_BEFORE_COMPLETION->Stage.CAST_BEFORE_COMPLETION;default->Stage.READY_TRIGGER_AFTER;};
            windows.add(new Window(window,owner,windows.size(),boundary,WindowStatus.OPEN,ordered,trigger));stage(owner,FrameStatus.WAITING_WINDOW,stage,window,null);frames.add(new Frame(selection,owner,FrameKind.REACTION_SELECTION,first.opportunityKey(),Invocation.CONTINUATION,FrameStatus.PENDING,stage,window,first.opportunityKey()));leaf=selection;facts.add(new CombatFact(kind.name(),subject,trigger.triggerKey(),null));return true;
        }
        void decide(Command command) {
            Window w=window(flow(),command.windowKey());
            if(w.status()==WindowStatus.CLOSED) {
                int index=windows.indexOf(w);windows.set(index,new Window(w.windowKey(),w.ownerFrameKey(),w.checkpointOrdinal(),w.boundaryIndex(),WindowStatus.CONSUMED,w.opportunities(),w.trigger()));
                Frame owner=get(w.ownerFrameKey());leaf=owner.frameKey();stage(owner.frameKey(),FrameStatus.RUNNABLE,owner.stage(),null,null);
                if(owner.kind()==FrameKind.MOVEMENT)driveMovement(owner.frameKey());
                else if(owner.stage()==Stage.READY_TRIGGER_AFTER)finish(owner.frameKey(),owner.kind()==FrameKind.SAVE_DAMAGE?Stage.SAVE_DAMAGE_COMPLETED:owner.kind()==FrameKind.ATTACK?Stage.ATTACK_RESOLVED:Stage.CAST_RESOLVED);
                else if(owner.kind()==FrameKind.ATTACK)settleAttack(owner.frameKey());else if(owner.kind()==FrameKind.CAST)completeCast(owner.frameKey(),false);else throw invalid("ACTION_FLOW_STAGE_INVALID");return;
            }
            Opportunity o=current(w);
            if(command.kind()==Kind.PASS_REACTION||command.kind()==Kind.ADVANCE_WINDOW) {completeOpportunity(w,o,command.kind()==Kind.PASS_REACTION?OpportunityStatus.PASSED:OpportunityStatus.INELIGIBLE,null,command.kind()==Kind.PASS_REACTION?"PASS":"CURRENT_CONDITIONS_CHANGED");return;}
            ReactionOption option=select(o,command);spendReaction(o.actorId());stage(o.selectionFrameKey(),FrameStatus.DECIDED,get(o.selectionFrameKey()).stage(),w.windowKey(),o.opportunityKey());
            int before=frames.size();
            // The DECLARED edge is committed with the child, even when a nested child pauses before completion.
            if(option.kind()==ReactionKind.OPPORTUNITY_ATTACK)beginAttack(o.actorId(),w.trigger().subjectId(),command.itemId(),o.selectionFrameKey(),Invocation.REACTION,true,false);
            else if(option.kind()==ReactionKind.READY_RELEASE)beginReadyRelease(command.readyKey(),w.trigger(),o.selectionFrameKey());
            else beginCast(o.actorId(),command.castInput(),o.selectionFrameKey(),Invocation.REACTION,null);
            UUID child=frames.get(before).frameKey();Window latest=window(flow(),w.windowKey());Opportunity now=latest.opportunities().get(o.priority());
            if(now.status()==OpportunityStatus.PENDING) {
                List<Opportunity> all=new ArrayList<>(latest.opportunities());all.set(o.priority(),new Opportunity(o.opportunityKey(),o.actorId(),o.priority(),o.options(),OpportunityStatus.DECLARED,o.selectionFrameKey(),child,null));replaceWindow(new Window(latest.windowKey(),latest.ownerFrameKey(),latest.checkpointOrdinal(),latest.boundaryIndex(),latest.status(),all,latest.trigger()));
            }
        }
        void replaceWindow(Window w) {for(int i=0;i<windows.size();i++)if(windows.get(i).windowKey().equals(w.windowKey()))windows.set(i,w);}
        void completeOpportunity(Window w,Opportunity o,OpportunityStatus status,UUID child,String reason) {
            List<Opportunity> all=new ArrayList<>(w.opportunities());all.set(o.priority(),new Opportunity(o.opportunityKey(),o.actorId(),o.priority(),o.options(),status,o.selectionFrameKey(),child,reason));stage(o.selectionFrameKey(),FrameStatus.DECIDED,get(o.selectionFrameKey()).stage(),w.windowKey(),o.opportunityKey());
            Opportunity next=all.stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElse(null);
            if(next==null) {replaceWindow(new Window(w.windowKey(),w.ownerFrameKey(),w.checkpointOrdinal(),w.boundaryIndex(),WindowStatus.CLOSED,all,w.trigger()));Frame owner=get(w.ownerFrameKey());stage(owner.frameKey(),FrameStatus.WAITING_WINDOW,owner.stage(),w.windowKey(),null);leaf=owner.frameKey();}
            else {UUID selection=next("selection");Opportunity selected=new Opportunity(next.opportunityKey(),next.actorId(),next.priority(),next.options(),next.status(),selection,null,null);all.set(next.priority(),selected);replaceWindow(new Window(w.windowKey(),w.ownerFrameKey(),w.checkpointOrdinal(),w.boundaryIndex(),WindowStatus.OPEN,all,w.trigger()));Frame owner=get(w.ownerFrameKey());frames.add(new Frame(selection,owner.frameKey(),FrameKind.REACTION_SELECTION,next.opportunityKey(),Invocation.CONTINUATION,FrameStatus.PENDING,owner.stage(),w.windowKey(),next.opportunityKey()));leaf=selection;}
            facts.add(new CombatFact(status.name(),o.actorId(),o.opportunityKey(),reason));
        }
        void finish(UUID id,Stage terminal) {
            Frame current=get(id);stage(id,FrameStatus.TERMINAL,terminal,current.windowKey(),current.opportunityKey());
            if(current.parentFrameKey()==null) {leaf=null;flowStatus=FlowStatus.COMPLETED;return;}
            Frame parent=get(current.parentFrameKey());
            if(parent.kind()==FrameKind.REACTION_SELECTION) {Window w=window(flow(),parent.windowKey());Opportunity o=w.opportunities().stream().filter(v->v.opportunityKey().equals(parent.opportunityKey())).findFirst().orElseThrow();completeOpportunity(w,o,OpportunityStatus.COMPLETED,id,null);}
            else if(parent.kind()==FrameKind.READY_RELEASE)finishRelease(parent.frameKey(),"RELEASED");else throw invalid("ACTION_FLOW_STAGE_INVALID");
        }
    }
    private List<Opportunity> candidates(Map<UUID,Context> roster,Battle b,Flow flow,TriggerFact trigger) {
        List<Opportunity> all=new ArrayList<>();
        List<UUID> candidateOrder=b.status()==Status.EXPLORATION?b.combatants().stream().map(Combatant::characterId).sorted(Comparator.comparing(UUID::toString)).toList():b.initiative().stream().map(Initiative::characterId).toList();
        for(UUID id:candidateOrder) {if(!roster.containsKey(id))continue;Combatant actor=actor(b,id);State state=roster.get(id).character();if(!actor.reactionAvailable()||state.hitPoints()==0||state.health().dead())continue;
            List<ReactionOption> options=new ArrayList<>();
            if(trigger.kind()==TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE) {
                Combatant mover=actor(b,trigger.subjectId());if(roster.get(mover.characterId()).character().hitPoints()>0&&!roster.get(mover.characterId()).character().health().dead()&&actor.side()!=mover.side()&&!mover.disengaged()) {
                    for(EquipmentItem item:state.equipment())if(Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(item.location())) {
                        var definitions=roster.get(id).material().projection().rules().stream().filter(FinalItem.class::isInstance).map(FinalItem.class::cast).filter(v->v.enabled()&&v.identity().equals(item.item())).toList();
                        if(definitions.size()!=1)throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");if(definitions.getFirst().definition().kind()!=EquipmentKind.WEAPON)continue;
                        WeaponAttackDefinition weapon=combat.weapon(roster.get(id),item.itemId());if(actor.position().distanceFeet(mover.position())<=weapon.reachFeet()&&actor.position().distanceFeet(trigger.position())>weapon.reachFeet())options.add(new ReactionOption(ReactionKind.OPPORTUNITY_ATTACK,item.itemId(),null,null,0,null));
                    }
                }
            } else if(trigger.kind()==TriggerKind.ATTACK_HIT_BEFORE_DAMAGE&&id.equals(trigger.targetId()))options.addAll(spells.reactionOptions(roster,b,id,ReactionKind.SHIELD,null));
            else if(trigger.kind()==TriggerKind.CAST_BEFORE_COMPLETION&&!id.equals(trigger.subjectId()))options.addAll(spells.reactionOptions(roster,b,id,ReactionKind.COUNTERSPELL,trigger.sourceFrameKey()));
            else if(Set.of(TriggerKind.CREATURE_ENTER_CELL_AFTER,TriggerKind.ATTACK_COMPLETED,TriggerKind.CAST_COMPLETED,TriggerKind.MONSTER_ACTION_COMPLETED).contains(trigger.kind())) {
                for(ReadyPreparation r:b.readyPreparations())if(r.actorId().equals(id)&&r.status()==ReadyStatus.ARMED&&matches(r.input().trigger(),trigger)&&readyAvailable(roster,b,r))options.add(new ReactionOption(ReactionKind.READY_RELEASE,null,null,null,0,r.readyKey()));
            }
            if(!options.isEmpty())all.add(new Opportunity(key(trigger.triggerKey(),"actor-"+id),id,all.size(),options,OpportunityStatus.PENDING,null,null,null));
        }
        return all;
    }
    private boolean eligible(Map<UUID,Context> roster,Battle b,Flow f,Window w,Opportunity o) {
        var live=candidates(roster,b,f,w.trigger()).stream().filter(v->v.actorId().equals(o.actorId())).findFirst();return live.isPresent()&&live.get().options().stream().anyMatch(o.options()::contains);
    }
    private static ReactionOption select(Opportunity opportunity,Command command) {
        ReactionKind kind=switch(command.kind()) {case DECLARE_OPPORTUNITY_ATTACK->ReactionKind.OPPORTUNITY_ATTACK;case DECLARE_SHIELD->ReactionKind.SHIELD;case DECLARE_COUNTERSPELL->ReactionKind.COUNTERSPELL;case RELEASE_READY->ReactionKind.READY_RELEASE;default->null;};
        return opportunity.options().stream().filter(o->o.kind()==kind).filter(o->kind==ReactionKind.OPPORTUNITY_ATTACK?Objects.equals(o.itemId(),command.itemId())&&command.castInput()==null&&command.readyKey()==null:kind==ReactionKind.READY_RELEASE?Objects.equals(o.readyKey(),command.readyKey())&&command.itemId()==null&&command.castInput()==null:command.castInput()!=null&&command.itemId()==null&&command.readyKey()==null&&o.castingRule().equals(command.castInput().castingRule())&&o.spell().equals(command.castInput().spell())&&o.slotLevel()==command.castInput().slotLevel()).findFirst().orElse(null);
    }
    private static boolean matches(ReadyTrigger ready,TriggerFact fact) {return ready.kind()==fact.kind()&&ready.subjectId().equals(fact.subjectId())&&(ready.position()==null||ready.position().equals(fact.position()));}
    private static UUID ownerActor(Flow f,Frame owner) {return switch(owner.kind()) {case ATTACK->f.attacks().stream().filter(a->a.frameKey().equals(owner.frameKey())).findFirst().orElseThrow().actorId();case CAST->f.casts().stream().filter(a->a.frameKey().equals(owner.frameKey())).findFirst().orElseThrow().actorId();case MOVEMENT->f.movements().stream().filter(a->a.movementKey().equals(owner.ownerKey())).findFirst().orElseThrow().actorId();default->throw invalid("ACTION_FLOW_TARGET_MISMATCH");};}
    private static Frame frame(Flow f,UUID id) {return f.frames().stream().filter(v->v.frameKey().equals(id)).findFirst().orElseThrow(()->invalid("ACTION_FLOW_TARGET_MISMATCH"));}
    private static Window window(Flow f,UUID id) {return f.windows().stream().filter(v->v.windowKey().equals(id)).findFirst().orElseThrow(()->invalid("ACTION_FLOW_TARGET_MISMATCH"));}
    private static Opportunity current(Window w) {return w.opportunities().stream().filter(v->v.status()==OpportunityStatus.PENDING).findFirst().orElseThrow(()->invalid("REACTION_NOT_CURRENT"));}
    private static Combatant actor(Battle b,UUID id) {return b.combatants().stream().filter(a->a.characterId().equals(id)).findFirst().orElseThrow(()->invalid("S2_COMBAT_CHARACTER_NOT_FOUND"));}
    private static ReadyPreparation ready(Battle b,UUID id) {return b.readyPreparations().stream().filter(r->r.readyKey().equals(id)).findFirst().orElseThrow(()->invalid("S2_READY_NOT_FOUND"));}
    private static void active(State state) {require(state!=null&&state.hitPoints()>0&&!state.health().dead(),"S2_COMBAT_ACTOR_INACTIVE");}
    private static int mod(State state,String ability) {return Math.floorDiv(state.abilities().get(ability)-10,2);}
    static Battle rebuild(Battle b,List<Combatant> actors,List<Flow> flows,List<ReadyPreparation> ready,List<TriggerFact> triggers,List<ShieldEffect> shields,List<ConcentrationHolder> holders,long version) {
        return new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),version,actors,b.initiative(),flows,b.ongoingEffects(),ready,triggers,shields,holders,b.environmentZones(),b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());
    }
    private static UUID key(UUID operation,String role) {UUID value=UUID.nameUUIDFromBytes((operation+":"+role).getBytes(StandardCharsets.UTF_8));return new UUID((value.getMostSignificantBits()&~0xF000L)|0x4000L,value.getLeastSignificantBits());}
    private static void require(boolean valid,String code) {if(!valid)throw invalid(code);}
    private static IllegalArgumentException invalid(String code) {return new IllegalArgumentException(code);}
}
