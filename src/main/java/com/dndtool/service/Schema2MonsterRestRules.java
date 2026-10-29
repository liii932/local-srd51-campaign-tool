package com.dndtool.service;

import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthResult;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.service.Schema2CombatState.*;

/** A bounded scene rest owner. It restores recharge only; it does not invent monster healing. */
final class Schema2MonsterRestRules {
    private Schema2MonsterRestRules() { }
    static boolean handles(Kind kind) {return kind==Kind.BEGIN_MONSTER_REST||kind==Kind.COMPLETE_MONSTER_REST;}
    static void prepare(Map<UUID,Context> roster,Battle b,Command c) {
        Schema2ExplorationRules.requireExploration(b);
        if(c.actorId()==null||c.monsterRestInput()==null||!c.monsterDeployments().isEmpty()||c.breathInput()!=null||c.targetId()!=null||c.itemId()!=null||!c.path().isEmpty()||!c.deployments().isEmpty()||!c.tieBreakOrder().isEmpty()
                ||c.flowKey()!=null||c.leafKey()!=null||c.windowKey()!=null||c.opportunityKey()!=null||c.castInput()!=null||c.readyInput()!=null||c.readyKey()!=null||c.zoneInput()!=null||c.zoneKey()!=null||c.healthInput()!=null||c.seconds()!=0)throw invalid("S2_MONSTER_REST_COMMAND");
        var monster=Schema2MonsterRules.instance(b,c.actorId());if(monster.dead())throw invalid("S2_COMBAT_ACTOR_INACTIVE");
        long now=Schema2SpellRules.second(roster.values().iterator().next().character());
        for(var context:roster.values()) {
            var state=context.character();if(state.hitPoints()==0&&!state.health().stable()&&!state.health().dead())throw invalid("S2_EXPLORATION_UNSTABLE_CHARACTER");
        }
        if(c.kind()==Kind.BEGIN_MONSTER_REST) {
            if(c.monsterRestInput().restKey()!=null||b.monsterRests().size()>=128||b.monsterRests().stream().anyMatch(r->r.monsterId().equals(c.actorId())&&r.status()==MonsterRestStatus.ACTIVE))throw invalid("S2_MONSTER_REST_COMMAND");
            if(now+duration(c.monsterRestInput().kind())>Schema2CharacterRestRules.MAX_MINUTE*60)throw invalid("S2_COMBAT_TIME_CAPACITY");
            for(var context:roster.values())Schema2CombatTimeRules.advanceRestSeconds(context.material(),context.character(),duration(c.monsterRestInput().kind()));
        } else {
            var rest=rest(b,c);int seconds=Math.toIntExact(Math.max(0,rest.completesAtSecond()-now));
            for(var context:roster.values()) {
                var s=context.character();if(s.hitPoints()==0&&!s.health().stable()&&!s.health().dead())throw invalid("S2_EXPLORATION_UNSTABLE_CHARACTER");
                if(seconds>0)Schema2CombatTimeRules.advanceRestSeconds(context.material(),s,seconds);
            }
            for(var action:monster.actionUses())Schema2MonsterRules.action(roster,action.action());
        }
    }
    static Resolution execute(UUID operation,Map<UUID,Context> roster,Battle b,Command c) {
        List<MonsterRest> rests=new ArrayList<>(b.monsterRests());List<HealthChange> changes=new ArrayList<>();List<CombatFact> facts=new ArrayList<>();
        long now=Schema2SpellRules.second(roster.values().iterator().next().character());UUID restKey;
        if(c.kind()==Kind.BEGIN_MONSTER_REST) {
            restKey=key(operation,"monster-rest");rests.add(new MonsterRest(restKey,c.actorId(),c.monsterRestInput().kind(),now,now+duration(c.monsterRestInput().kind()),MonsterRestStatus.ACTIVE,null));
        } else {
            var rest=rest(b,c);restKey=rest.restKey();int seconds=Math.toIntExact(Math.max(0,rest.completesAtSecond()-now));
            if(seconds>0)for(UUID id:roster.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
                var ctx=roster.get(id);var before=ctx.character();var after=Schema2CombatTimeRules.advanceRestSeconds(ctx.material(),before,seconds);
                changes.add(new HealthChange(id,before,after,restKey,0,false,new HealthResult(List.of(),null,null,0,(int)Math.max(0,after.hitPoints()-before.hitPoints())),false,true,seconds));
            }
            for(int i=0;i<rests.size();i++)if(rests.get(i).restKey().equals(restKey))rests.set(i,new MonsterRest(restKey,c.actorId(),rest.kind(),rest.startedAtSecond(),rest.completesAtSecond(),MonsterRestStatus.COMPLETED,operation));
            var monster=Schema2MonsterRules.instance(b,c.actorId());b=Schema2MonsterRules.replace(b,new MonsterInstance(monster.monsterId(),monster.monster(),monster.hitPoints(),monster.dead(),monster.actionUses().stream().map(a->new MonsterActionUse(a.action(),true)).toList()));
            b=Schema2ExplorationRules.expire(b,Math.max(now,rest.completesAtSecond()),facts);
        }
        b=withRests(b,rests);b=Schema2ActionFlowRules.rebuild(b,b.combatants(),b.flows(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.version()+1);
        var causes=Schema2OngoingEffectRules.reconcile(b,operation);b=Schema2OngoingEffectRules.withCauses(b,causes.causes());facts.addAll(causes.facts());facts.add(new CombatFact(c.kind().name(),c.actorId(),restKey,null));
        new Schema2CombatRules().validateState(b);return new Resolution(b,changes,List.of(),facts);
    }
    static Battle interrupt(Battle b,UUID operation) {
        return withRests(b,b.monsterRests().stream().map(r->r.status()==MonsterRestStatus.ACTIVE?new MonsterRest(r.restKey(),r.monsterId(),r.kind(),r.startedAtSecond(),r.completesAtSecond(),MonsterRestStatus.INTERRUPTED,operation):r).toList());
    }
    private static MonsterRest rest(Battle b,Command c) {return b.monsterRests().stream().filter(r->r.restKey().equals(c.monsterRestInput().restKey())&&r.monsterId().equals(c.actorId())&&r.kind()==c.monsterRestInput().kind()&&r.status()==MonsterRestStatus.ACTIVE).findFirst().orElseThrow(()->invalid("S2_MONSTER_REST_COMMAND"));}
    private static int duration(MonsterRestKind kind) {return kind==MonsterRestKind.SHORT?3600:28800;}
    private static Battle withRests(Battle b,List<MonsterRest> rests) {return new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.environmentZones(),b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),rests);}
    private static UUID key(UUID operation,String role) {UUID v=UUID.nameUUIDFromBytes((operation+":"+role).getBytes(StandardCharsets.UTF_8));return new UUID((v.getMostSignificantBits()&~0xf000L)|0x4000L,v.getLeastSignificantBits());}
}
