package com.dndtool.service;

import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthInput;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthResult;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices;
import com.dndtool.service.Schema2CombatRules.Context;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Finite scene-owner plans; character health and the existing shared character clocks stay authoritative. */
public final class Schema2ExplorationRules {
    public record TimePlan(int seconds,List<HealthChange> changes,Map<UUID,Context> after) {
        public TimePlan {changes=List.copyOf(changes);after=Map.copyOf(after);}
    }
    public record PreparedHealth(Schema2CharacterHealthRules.PreparedHealth prepared,VitalEffectKind kind) { }
    private Schema2ExplorationRules() { }

    public static void requireExploration(Battle battle) {
        if(battle==null || battle.status()!=Status.EXPLORATION) throw invalid("S2_EXPLORATION_REQUIRED");
        if(battle.activeFlow()!=null) throw invalid("ACTION_FLOW_BUSY");
    }

    /** No fabricated turns: an unstable zero-HP character must receive care or enter a real turn order. */
    public static void requireTimeSafe(Map<UUID,Context> roster,int seconds) {
        if(seconds<1||seconds>60||roster.isEmpty()) throw invalid("S2_EXPLORATION_TIME_INPUT");
        Long now=null;
        for(Context context:roster.values()) {
            var state=context.character();long value=Schema2SpellRules.second(state);
            if(now!=null&&value!=now) throw invalid("S2_COMBAT_CLOCK_MISMATCH");now=value;
            if(state.hitPoints()==0&&!state.health().stable()&&!state.health().dead()) throw invalid("S2_EXPLORATION_UNSTABLE_CHARACTER");
            Schema2CombatTimeRules.advanceSeconds(context.material(),state,seconds);
        }
    }

    public static TimePlan advanceTime(Map<UUID,Context> roster,int seconds,UUID frameKey) {
        uuid(frameKey);requireTimeSafe(roster,seconds);
        List<HealthChange> changes=new ArrayList<>();Map<UUID,Context> after=new LinkedHashMap<>();
        for(UUID id:roster.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            Context context=roster.get(id);var before=context.character();
            var next=Schema2CombatTimeRules.advanceSeconds(context.material(),before,seconds);
            changes.add(new HealthChange(id,before,next,frameKey,0,false,
                    new HealthResult(List.of(),null,null,0,(int)Math.max(0,next.hitPoints()-before.hitPoints())),false,true,seconds));
            after.put(id,new Context(context.material(),context.initial(),next));
        }
        return new TimePlan(seconds,changes,after);
    }

    /** One unopposed walking interval takes six seconds and preserves each cell boundary for zone reconciliation. */
    public static void prepareMove(Map<UUID,Context> roster,Battle battle,UUID actor,List<Position> path) {
        requireExploration(battle);requireTimeSafe(roster,6);
        Context context=roster.get(actor);
        if(context==null||context.character().hitPoints()==0||context.character().health().dead()) throw invalid("S2_COMBAT_ACTOR_INACTIVE");
        Combatant mover=battle.combatants().stream().filter(c->c.characterId().equals(actor)).findFirst()
                .orElseThrow(()->invalid("S2_COMBAT_CHARACTER_NOT_FOUND"));
        if(mover.prone()) throw invalid("S2_EXPLORATION_MOVEMENT_UNSUPPORTED");
        var sources=context.initial().character().sources();
        var movement=context.material().projection().rules().stream().filter(FinalMovement.class::isInstance).map(FinalMovement.class::cast)
                .filter(r->r.enabled()&&(r.definition().owner().equals(sources.race())||r.definition().owner().equals(sources.subrace()))).toList();
        if(movement.size()!=1||!movement.getFirst().definition().algorithm().equals("SRD_2014_WALKING_SPEED_V1")) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        int speed=Math.max(0,movement.getFirst().definition().walkSpeedFeet()
                -new Schema2CharacterEquipmentRules().derive(context.material(),context.character()).speedPenalty());
        if(path==null||path.isEmpty()||path.size()>64||path.size()*5>speed) throw invalid("S2_EXPLORATION_PATH");
        Set<Position> occupied=new HashSet<>();
        for(Combatant other:battle.combatants()) if(!other.characterId().equals(actor)) occupied.add(other.position());
        Position from=mover.position();
        for(Position step:path) {
            if(step==null||from.distanceFeet(step)!=5||occupied.contains(step)) throw invalid("S2_EXPLORATION_PATH");
            from=step;
        }
    }

    public static PreparedHealth prepareHealth(Map<UUID,Context> roster,Battle battle,UUID target,HealthInput input) {
        requireExploration(battle);
        Context context=roster.get(target);
        if(context==null||input==null||input.effect()==null) throw invalid("S2_EXPLORATION_HEALTH_INPUT");
        var command=new JdbcSchema2CharacterRuntimeRepository.Command("HEALTH_EFFECT_RESOLVED",null,0,null,null,null,
                GrowthChoices.EMPTY,null,null,null,input);
        var prepared=new Schema2CharacterHealthRules().prepare(context.material(),context.character(),command);
        var effects=context.material().projection().rules().stream().filter(FinalVitalEffect.class::isInstance).map(FinalVitalEffect.class::cast)
                .filter(r->r.enabled()&&r.identity().equals(input.effect())).toList();
        if(effects.size()!=1) throw invalid("S2_HEALTH_EFFECT_UNSUPPORTED");
        return new PreparedHealth(prepared,effects.getFirst().definition().kind());
    }

    /** Bless has no end callback; one accepted time interval can end its complete deadline set atomically. */
    public static Battle expire(Battle battle,long second,List<CombatFact> facts) {
        List<OngoingEffect> effects=new ArrayList<>();Set<UUID> ended=new HashSet<>();
        for(OngoingEffect effect:battle.ongoingEffects()) {
            if(effect.status()==EffectStatus.ACTIVE&&effect.expiresAtSecond()<=second) {
                ended.add(effect.effectKey());
                effect=new OngoingEffect(effect.effectKey(),effect.spell(),effect.casterId(),effect.targets(),effect.startedAtSecond(),
                        effect.expiresAtSecond(),EffectStatus.ENDED,"DURATION_EXPIRED");
                facts.add(new CombatFact("CONCENTRATION_ENDED",effect.casterId(),effect.effectKey(),"DURATION_EXPIRED"));
            }
            effects.add(effect);
        }
        var holders=battle.concentrationHolders().stream().filter(h->h.kind()!=ConcentrationKind.BLESS_EFFECT||!ended.contains(h.sourceKey())).toList();
        return new Battle(battle.combatId(),battle.campaignId(),battle.combatRule(),battle.status(),battle.round(),battle.turnNumber(),
                battle.activeOrdinal(),battle.version(),battle.combatants(),battle.initiative(),battle.flows(),effects,battle.readyPreparations(),
                battle.triggerFacts(),battle.shields(),holders,battle.environmentZones(),battle.suppressionCauses());
    }
}
