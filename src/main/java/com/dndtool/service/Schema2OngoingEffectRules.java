package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CombatRules.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Closed environmental producer and per-application suppression; selection is a read-only projection. */
public final class Schema2OngoingEffectRules {
    public static final String ZONE_ALGORITHM = "DM_ANTIMAGIC_ZONE_V1";
    public static final int ZONE_RADIUS_FEET = 10, MAX_ZONES = 16, MAX_CAUSES = 512;
    public enum ContributionStatus { EFFECTIVE, SUPPRESSED, SHADOWED, ENDED }
    public record Contribution(UUID effectKey, UUID targetId, ContributionStatus status,
            List<UUID> suppressionCauseKeys, UUID winnerEffectKey) {
        public Contribution { suppressionCauseKeys=List.copyOf(suppressionCauseKeys); }
    }
    public record Reconciliation(List<SuppressionCause> causes, List<CombatFact> facts) {
        public Reconciliation { causes=List.copyOf(causes); facts=List.copyOf(facts); }
    }
    private record Application(UUID effectKey, UUID targetId, boolean active) { }
    private record Scope(UUID zoneKey, UUID effectKey, UUID targetId) { }

    private Schema2OngoingEffectRules() { }

    /** A DM places a published, fixed environmental rule; this is never a spell cast or a slot substitute. */
    public static EnvironmentZone createZone(Map<UUID,Context> roster, Battle battle, UUID operationId, ZoneInput input) {
        uuid(operationId);
        if(roster==null||roster.isEmpty()) throw invalid("S2_ENVIRONMENT_RULE_UNSUPPORTED");
        if (input==null || input.rule()==null || input.center()==null) throw invalid("S2_ENVIRONMENT_COMMAND");
        if (battle.environmentZones().size()>=MAX_ZONES) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
        for(Context context:roster.values()) requireRule(context.material(),input.rule());
        return new EnvironmentZone(key(operationId,"environment-zone"),input.rule(),input.center(),operationId,null);
    }

    public static EnvironmentZone endZone(EnvironmentZone zone, UUID operationId) {
        uuid(operationId);
        if (zone==null) throw invalid("S2_ENVIRONMENT_NOT_FOUND");
        if (zone.endedOperationId()!=null) throw invalid("S2_ENVIRONMENT_ALREADY_ENDED");
        return new EnvironmentZone(zone.zoneKey(),zone.rule(),zone.center(),zone.createdOperationId(),operationId);
    }

    public static void requireCastingAllowed(Map<UUID,Context> roster, Battle battle, UUID actor) {
        if(roster==null||roster.isEmpty()) throw invalid("S2_ENVIRONMENT_RULE_UNSUPPORTED");
        for (EnvironmentZone zone:battle.environmentZones()) {
            for (Context context:roster.values()) requireRule(context.material(),zone.rule());
            if (zone.endedOperationId()==null && covers(zone,position(battle,actor))) throw invalid("S2_SPELL_ANTIMAGIC_ZONE");
        }
    }

    /** Called once after each real position/effect/zone boundary, retaining every earlier episode. */
    public static Reconciliation reconcile(Battle candidate, UUID operationId, int boundaryOrdinal) {
        uuid(operationId);
        if (boundaryOrdinal<0 || boundaryOrdinal>1024) throw invalid("S2_ENVIRONMENT_BOUNDARY");
        Map<UUID,EnvironmentZone> zones=zones(candidate);
        Map<String,Application> applications=applications(candidate);
        List<SuppressionCause> result=new ArrayList<>(); List<CombatFact> facts=new ArrayList<>();
        Set<UUID> keys=new HashSet<>(); Set<Scope> live=new HashSet<>();
        for(SuppressionCause cause:candidate.suppressionCauses()) {
            validateCause(cause,zones,applications,keys);
            if(cause.releasedOperationId()==null) {
                Scope scope=new Scope(cause.zoneKey(),cause.effectKey(),cause.targetId());
                if(!live.add(scope)) throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
                EnvironmentZone zone=zones.get(cause.zoneKey()); Application app=applications.get(applicationKey(cause.effectKey(),cause.targetId()));
                SuppressionReleaseReason reason=!app.active()?SuppressionReleaseReason.EFFECT_ENDED
                        :zone.endedOperationId()!=null?SuppressionReleaseReason.ZONE_ENDED
                        :!covers(zone,position(candidate,cause.targetId()))?SuppressionReleaseReason.LEFT_ZONE:null;
                if(reason!=null) {
                    cause=new SuppressionCause(cause.causeKey(),cause.zoneKey(),cause.effectKey(),cause.targetId(),
                            cause.establishedOperationId(),operationId,reason);
                    live.remove(scope);
                    facts.add(new CombatFact("EFFECT_SUPPRESSION_RELEASED",cause.targetId(),cause.causeKey(),reason.name()));
                }
            }
            result.add(cause);
        }
        for(EnvironmentZone zone:candidate.environmentZones()) if(zone.endedOperationId()==null) {
            for(Application app:applications.values()) if(app.active() && covers(zone,position(candidate,app.targetId()))) {
                Scope scope=new Scope(zone.zoneKey(),app.effectKey(),app.targetId());
                if(live.add(scope)) {
                    UUID causeKey=key(operationId,"suppression:"+boundaryOrdinal+":"+zone.zoneKey()+":"+app.effectKey()+":"+app.targetId());
                    if(!keys.add(causeKey)) throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
                    result.add(new SuppressionCause(causeKey,zone.zoneKey(),app.effectKey(),app.targetId(),operationId,null,null));
                    facts.add(new CombatFact("EFFECT_SUPPRESSED",app.targetId(),causeKey,"ENVIRONMENT_ZONE"));
                }
            }
        }
        if(result.size()>MAX_CAUSES) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
        return new Reconciliation(result,facts);
    }

    public static Reconciliation reconcile(Battle candidate, UUID operationId) { return reconcile(candidate,operationId,0); }

    public static Battle withCauses(Battle battle,List<SuppressionCause> causes) {
        return new Battle(battle.combatId(),battle.campaignId(),battle.combatRule(),battle.status(),battle.round(),battle.turnNumber(),
                battle.activeOrdinal(),battle.version(),battle.combatants(),battle.initiative(),battle.flows(),battle.ongoingEffects(),
                battle.readyPreparations(),battle.triggerFacts(),battle.shields(),battle.concentrationHolders(),battle.environmentZones(),causes);
    }

    public static boolean isSuppressed(Battle battle, UUID effectKey, UUID targetId) {
        return battle.suppressionCauses().stream().anyMatch(c->c.effectKey().equals(effectKey)&&c.targetId().equals(targetId)&&c.releasedOperationId()==null);
    }

    public static OngoingEffect blessing(Battle battle, UUID targetId) {
        return battle.ongoingEffects().stream().filter(e->e.status()==EffectStatus.ACTIVE&&e.targets().contains(targetId)
                && !isSuppressed(battle,e.effectKey(),targetId)).findFirst().orElse(null);
    }

    public static List<Contribution> contributions(Battle battle,UUID targetId) {
        OngoingEffect winner=blessing(battle,targetId); List<Contribution> result=new ArrayList<>();
        for(OngoingEffect effect:battle.ongoingEffects()) if(effect.targets().contains(targetId)) {
            List<UUID> causes=battle.suppressionCauses().stream().filter(c->c.effectKey().equals(effect.effectKey())
                    &&c.targetId().equals(targetId)&&c.releasedOperationId()==null).map(SuppressionCause::causeKey).toList();
            ContributionStatus status=effect.status()==EffectStatus.ENDED?ContributionStatus.ENDED
                    :!causes.isEmpty()?ContributionStatus.SUPPRESSED
                    :winner!=null&&winner.effectKey().equals(effect.effectKey())?ContributionStatus.EFFECTIVE:ContributionStatus.SHADOWED;
            result.add(new Contribution(effect.effectKey(),targetId,status,causes,status==ContributionStatus.SHADOWED?winner.effectKey():null));
        }
        return List.copyOf(result);
    }

    /** Reading rejects a missing or stale relationship; it never repairs it or establishes a cause. */
    public static void validate(Map<UUID,Context> roster,Battle battle) {
        if(roster==null||roster.isEmpty()) throw invalid("S2_ENVIRONMENT_RULE_UNSUPPORTED");
        validateGraph(battle);
        for(EnvironmentZone zone:battle.environmentZones()) for(Context context:roster.values()) requireRule(context.material(),zone.rule());
    }

    /** Persistence validates relationships here; the service separately supplies every frozen rule reader. */
    public static void validateGraph(Battle battle) {
        Map<UUID,EnvironmentZone> zones=zones(battle); Map<String,Application> apps=applications(battle);
        Set<UUID> keys=new HashSet<>(); Set<Scope> live=new HashSet<>();
        for(SuppressionCause cause:battle.suppressionCauses()) {
            validateCause(cause,zones,apps,keys);
            if(cause.releasedOperationId()==null) {
                EnvironmentZone zone=zones.get(cause.zoneKey()); Application app=apps.get(applicationKey(cause.effectKey(),cause.targetId()));
                if(zone.endedOperationId()!=null || !app.active() || !covers(zone,position(battle,cause.targetId()))
                        || !live.add(new Scope(zone.zoneKey(),app.effectKey(),app.targetId()))) throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
            }
        }
        for(EnvironmentZone zone:zones.values()) if(zone.endedOperationId()==null) for(Application app:apps.values())
            if(app.active()&&covers(zone,position(battle,app.targetId()))&&!live.contains(new Scope(zone.zoneKey(),app.effectKey(),app.targetId())))
                throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
    }

    private static Map<UUID,EnvironmentZone> zones(Battle battle) {
        if(battle.environmentZones().size()>MAX_ZONES||battle.suppressionCauses().size()>MAX_CAUSES) throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
        Map<UUID,EnvironmentZone> result=new LinkedHashMap<>();
        for(EnvironmentZone zone:battle.environmentZones()) {
            uuid(zone.zoneKey());uuid(zone.createdOperationId());if(zone.endedOperationId()!=null)uuid(zone.endedOperationId());
            if(zone.rule()==null||!zone.rule().ruleType().equals("adventure.environment")||zone.center()==null||result.put(zone.zoneKey(),zone)!=null)
                throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
        }
        return result;
    }
    private static Map<String,Application> applications(Battle battle) {
        Map<String,Application> result=new LinkedHashMap<>(); Set<UUID> effects=new HashSet<>();
        for(OngoingEffect effect:battle.ongoingEffects()) {
            if(!effects.add(effect.effectKey())) throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
            for(UUID target:effect.targets().stream().sorted(Comparator.comparing(UUID::toString)).toList())
                if(result.put(applicationKey(effect.effectKey(),target),new Application(effect.effectKey(),target,effect.status()==EffectStatus.ACTIVE))!=null)
                    throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
        }
        for(ShieldEffect shield:battle.shields()) {
            if(!effects.add(shield.effectKey())) throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
            result.put(applicationKey(shield.effectKey(),shield.actorId()),new Application(shield.effectKey(),shield.actorId(),shield.status()==EffectStatus.ACTIVE));
        }
        return result;
    }
    private static void validateCause(SuppressionCause cause,Map<UUID,EnvironmentZone> zones,Map<String,Application> apps,Set<UUID> keys) {
        uuid(cause.causeKey());uuid(cause.establishedOperationId());if(cause.releasedOperationId()!=null)uuid(cause.releasedOperationId());
        if(!keys.add(cause.causeKey())||!zones.containsKey(cause.zoneKey())||!apps.containsKey(applicationKey(cause.effectKey(),cause.targetId()))
                ||(cause.releasedOperationId()==null)!=(cause.releaseReason()==null)) throw invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID");
    }
    private static void requireRule(ProductionSchema2Projection material,RuleIdentity rule) {
        var matches=material.projection().rules().stream().filter(FinalEnvironment.class::isInstance).map(FinalEnvironment.class::cast)
                .filter(r->r.enabled()&&r.identity().equals(rule)&&ZONE_ALGORITHM.equals(r.definition().algorithm())).toList();
        if(matches.size()!=1) throw invalid("S2_ENVIRONMENT_RULE_UNSUPPORTED");
    }
    private static Position position(Battle battle,UUID target) {
        return battle.combatants().stream().filter(a->a.characterId().equals(target)).map(Combatant::position).findFirst()
                .orElseThrow(()->invalid("S2_ENVIRONMENT_FROZEN_DATA_INVALID"));
    }
    private static boolean covers(EnvironmentZone zone,Position position) { return zone.center().distanceFeet(position)<=ZONE_RADIUS_FEET; }
    private static String applicationKey(UUID effect,UUID target) {return effect+":"+target;}
    private static UUID key(UUID operation,String role) {
        UUID value=UUID.nameUUIDFromBytes((operation+":"+role).getBytes(StandardCharsets.UTF_8));
        return new UUID((value.getMostSignificantBits()&~0xF000L)|0x4000L,value.getLeastSignificantBits());
    }
}
