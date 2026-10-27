package com.dndtool.service;

import java.util.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Structural verification of the saved business graph, independent of JDBC row identifiers. */
final class Schema2CombatStateValidator {
    private Schema2CombatStateValidator() { }
    static void validate(Battle b) {
        try {
            id(b.combatId());id(b.campaignId());check(!b.combatId().equals(b.campaignId())&&b.combatRule()!=null&&b.combatRule().ruleType().equals("adventure.combat")&&b.status()!=null&&b.round()>0&&b.turnNumber()>0&&b.version()>=0&&b.version()<Schema2CombatRules.MAX_FACTS);
            check(b.combatants().size()>=2&&b.combatants().size()<=16&&b.initiative().size()==b.combatants().size()&&b.activeOrdinal()>=0&&b.activeOrdinal()<b.initiative().size()&&b.flows().size()<=128);
            Set<UUID> actors=new HashSet<>(),keys=new HashSet<>();Set<Position> positions=new HashSet<>();
            for(Combatant a:b.combatants()) {
                id(a.characterId());check(actors.add(a.characterId())&&positions.add(a.position())&&a.side()>=0&&a.side()<16&&a.movementSpentHalfFeet()>=0&&a.movementSpentHalfFeet()<=480&&a.movementBonus()>=0&&a.movementBonus()<=120&&a.movementBonus()%5==0);
                AttackSequence s=a.sequence();if(s!=null) {id(s.sequenceKey());check(s.limit()==1&&s.performed()>=0&&s.forfeited()>=0&&s.performed()+s.forfeited()<=1&&s.closed()==(s.performed()+s.forfeited()==1));}
            }
            Set<UUID> ordered=new HashSet<>();for(int i=0;i<b.initiative().size();i++) {Initiative v=b.initiative().get(i);check(actors.contains(v.characterId())&&ordered.add(v.characterId())&&v.ordinal()==i&&v.d20()>=1&&v.d20()<=20&&v.dexterityModifier()>=-5&&v.dexterityModifier()<=10&&v.total()==v.d20()+v.dexterityModifier()&&(i==0||b.initiative().get(i-1).total()>=v.total()));}
            Map<UUID,Frame> allFrames=new HashMap<>();Map<UUID,CastAttempt> allCasts=new HashMap<>();Map<UUID,TriggerFact> triggers=new HashMap<>();int active=0;
            for(TriggerFact t:b.triggerFacts()) {id(t.triggerKey());check(keys.add(t.triggerKey())&&triggers.put(t.triggerKey(),t)==null&&actors.contains(t.subjectId())&&(t.targetId()==null||actors.contains(t.targetId()))&&t.ordinal()>=0&&t.ordinal()<64&&t.kind()!=null);}
            for(Flow f:b.flows()) {
                id(f.flowKey());check(keys.add(f.flowKey())&&!f.frames().isEmpty()&&f.frames().size()<=1024&&f.windows().size()<=1024);Map<UUID,Frame> frames=new HashMap<>();
                for(Frame v:f.frames()) {id(v.frameKey());id(v.ownerKey());check(keys.add(v.frameKey())&&frames.put(v.frameKey(),v)==null&&allFrames.put(v.frameKey(),v)==null&&v.kind()!=null&&v.invocation()!=null&&v.status()!=null&&v.stage()!=null);}
                Frame root=frames.get(f.rootFrameKey());check(root!=null&&root.parentFrameKey()==null&&root.invocation()==Invocation.ROOT);
                for(Frame v:f.frames()) {
                    Set<UUID> chain=new HashSet<>();Frame cursor=v;int depth=0;
                    while(cursor!=null) {check(chain.add(cursor.frameKey())&&chain.size()<=32);if(cursor.invocation()==Invocation.REACTION)depth++;check(depth<=8);cursor=cursor.parentFrameKey()==null?null:frames.get(cursor.parentFrameKey());if(cursor==null)check(chain.contains(root.frameKey()));}
                }
                if(f.status()==FlowStatus.ACTIVE) {active++;check(f.leafFrameKey()!=null&&frames.containsKey(f.leafFrameKey())&&root.status()!=FrameStatus.TERMINAL);}
                else check(f.status()==FlowStatus.COMPLETED&&f.leafFrameKey()==null&&root.status()==FrameStatus.TERMINAL);
                Set<UUID> windows=new HashSet<>();for(int i=0;i<f.windows().size();i++) {
                    Window w=f.windows().get(i);id(w.windowKey());check(keys.add(w.windowKey())&&windows.add(w.windowKey())&&frames.containsKey(w.ownerFrameKey())&&w.checkpointOrdinal()==i&&w.boundaryIndex()>=0&&w.boundaryIndex()<64&&w.trigger()!=null&&w.trigger().equals(triggers.get(w.trigger().triggerKey()))&&w.ownerFrameKey().equals(w.trigger().sourceFrameKey())&&!w.opportunities().isEmpty()&&w.opportunities().size()<=128);
                    Set<UUID> candidates=new HashSet<>();int pending=0;
                    for(int j=0;j<w.opportunities().size();j++) {
                        Opportunity o=w.opportunities().get(j);id(o.opportunityKey());check(keys.add(o.opportunityKey())&&actors.contains(o.actorId())&&candidates.add(o.actorId())&&o.priority()==j&&!o.options().isEmpty()&&new HashSet<>(o.options()).size()==o.options().size());
                        for(ReactionOption option:o.options()) {check(option.kind()!=null);if(option.kind()==ReactionKind.OPPORTUNITY_ATTACK)check(option.itemId()!=null&&option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0&&option.readyKey()==null);else if(option.kind()==ReactionKind.READY_RELEASE)check(option.readyKey()!=null&&option.itemId()==null&&option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0);else check(option.itemId()==null&&option.readyKey()==null&&option.castingRule()!=null&&option.spell()!=null&&option.slotLevel()>=1&&option.slotLevel()<=9);}
                        if(o.status()==OpportunityStatus.PENDING) {pending++;check(o.childFrameKey()==null&&o.reason()==null);}
                        else if(o.status()==OpportunityStatus.COMPLETED||o.status()==OpportunityStatus.DECLARED) {Frame child=frames.get(o.childFrameKey());check(child!=null&&child.invocation()==Invocation.REACTION&&Objects.equals(child.parentFrameKey(),o.selectionFrameKey())&&(o.status()==OpportunityStatus.COMPLETED)==(child.status()==FrameStatus.TERMINAL)&&o.reason()==null);}
                        else check(o.childFrameKey()==null&&o.reason()!=null);
                        if(o.selectionFrameKey()!=null) {Frame selection=frames.get(o.selectionFrameKey());check(selection!=null&&selection.kind()==FrameKind.REACTION_SELECTION&&Objects.equals(selection.parentFrameKey(),w.ownerFrameKey())&&Objects.equals(selection.windowKey(),w.windowKey())&&Objects.equals(selection.opportunityKey(),o.opportunityKey()));}
                    }
                    if(w.status()!=WindowStatus.OPEN)check(pending==0&&w.opportunities().stream().noneMatch(o->o.status()==OpportunityStatus.DECLARED));
                    if(f.status()==FlowStatus.COMPLETED)check(w.status()==WindowStatus.CONSUMED);
                }
                for(Frame v:f.frames())if(v.windowKey()!=null)check(windows.contains(v.windowKey()));
                Set<UUID> movementKeys=new HashSet<>();for(Movement m:f.movements()) {id(m.movementKey());check(movementKeys.add(m.movementKey())&&actors.contains(m.actorId())&&m.path().size()>=1&&m.path().size()<=64&&m.cursor()>=0&&m.cursor()<=m.path().size()&&m.spent()==m.cursor()*5&&m.status()!=null&&m.readied()==(m.allowanceFeet()>0)&&m.allowanceFeet()<=120&&f.frames().stream().anyMatch(v->v.kind()==FrameKind.MOVEMENT&&v.ownerKey().equals(m.movementKey())));if(m.status()==MovementStatus.COMPLETED)check(m.cursor()==m.path().size()&&m.stopReason()==null);else if(m.status()==MovementStatus.PENDING)check(m.stopReason()==null);else check(m.stopReason()!=null);}
                for(AttackAttempt a:f.attacks()) {
                    check(frames.containsKey(a.frameKey())&&frames.get(a.frameKey()).kind()==FrameKind.ATTACK&&actors.contains(a.actorId())&&actors.contains(a.targetId())&&!a.actorId().equals(a.targetId())&&a.itemId()!=null&&a.firstD20()>=1&&a.firstD20()<=20&&a.selectedD20()>=1&&a.selectedD20()<=20&&(a.secondD20()==null||a.secondD20()>=1&&a.secondD20()<=20)&&a.status()!=null&&a.armorClass()>0&&a.finalArmorClass()>0&&a.damageAmount()>=0&&a.damageDice().size()<=40&&a.damageDice().stream().allMatch(d->d>=1&&d<=12)&&validBless(a.blessingEffectKey(),a.blessingD4()));
                    if(a.status()==AttackStatus.HIT_PENDING_DAMAGE)check(a.initialHit()&&a.hit()&&a.damageDice().isEmpty()&&a.damageAmount()==0&&frames.get(a.frameKey()).status()!=FrameStatus.TERMINAL);
                    if(!a.hit())check(a.damageDice().isEmpty()&&a.damageAmount()==0);
                }
                for(CastAttempt c:f.casts()) {check(frames.containsKey(c.frameKey())&&frames.get(c.frameKey()).kind()==FrameKind.CAST&&allCasts.put(c.frameKey(),c)==null&&actors.contains(c.actorId())&&c.castingRule()!=null&&Set.of("character.spellcasting","character.wizard_spellcasting").contains(c.castingRule().ruleType())&&c.spell()!=null&&c.spell().ruleType().equals("adventure.spell")&&c.slotResource()!=null&&c.slotResource().ruleType().equals("character.resource")&&c.slotLevel()>=1&&c.slotLevel()<=9&&!c.targets().isEmpty()&&c.targets().size()<=3&&actors.containsAll(c.targets())&&new HashSet<>(c.targets()).size()==c.targets().size()&&c.status()!=null&&c.healingDice().size()<=1&&c.healingDice().stream().allMatch(d->d>=1&&d<=8)&&c.appliedHealing()>=0&&c.requestedHealing()>=c.appliedHealing());if(c.status()==CastStatus.PENDING||c.status()==CastStatus.COUNTERED||c.status()==CastStatus.HELD)check(c.healingDice().isEmpty()&&c.requestedHealing()==0&&c.appliedHealing()==0&&c.effectKey()==null);}
                for(SavingThrowAttempt s:f.savingThrows())check(frames.containsKey(s.frameKey())&&actors.contains(s.subjectId())&&s.d20()>=1&&s.d20()<=20&&s.proficiencyBonus()==0&&validBless(s.blessingEffectKey(),s.blessingD4())&&s.total()==s.d20()+s.abilityModifier()+(s.blessingD4()==null?0:s.blessingD4())&&s.success()==(s.total()>=s.difficultyClass()));
                for(AbilityCheckAttempt c:f.abilityChecks())check(frames.containsKey(c.frameKey())&&actors.contains(c.subjectId())&&c.kind().equals("COUNTERSPELL")&&c.abilityKey().equals("ability.intelligence")&&c.d20()>=1&&c.d20()<=20&&c.proficiencyBonus()==0&&c.total()==c.d20()+c.abilityModifier()&&c.success()==(c.total()>=c.difficultyClass()));
                for(ReadyRelease r:f.readyReleases())check(frames.containsKey(r.frameKey())&&frames.get(r.frameKey()).kind()==FrameKind.READY_RELEASE&&triggers.containsKey(r.triggerKey())&&r.completed()==(r.outcome()!=null)&&r.completed()==(frames.get(r.frameKey()).status()==FrameStatus.TERMINAL));
            }
            check(active<=1&&(b.status()!=Status.ENDED||active==0));
            for(TriggerFact t:triggers.values())check(allFrames.containsKey(t.sourceFrameKey()));
            for(CastAttempt c:allCasts.values())if(c.targetCastFrameKey()!=null)check(allCasts.containsKey(c.targetCastFrameKey())&&!c.frameKey().equals(c.targetCastFrameKey()));
            Map<UUID,ReadyPreparation> preparations=new HashMap<>();for(ReadyPreparation r:b.readyPreparations()) {id(r.readyKey());check(keys.add(r.readyKey())&&preparations.put(r.readyKey(),r)==null&&actors.contains(r.actorId())&&r.declaredTurnNumber()>0&&r.expiresAtTurnNumber()==r.declaredTurnNumber()+actors.size()&&r.input()!=null&&r.status()!=null&&actors.contains(r.input().trigger().subjectId())&&Set.of(TriggerKind.CREATURE_ENTER_CELL_AFTER,TriggerKind.ATTACK_COMPLETED,TriggerKind.CAST_COMPLETED).contains(r.input().trigger().kind())&&((r.input().trigger().position()!=null)==(r.input().trigger().kind()==TriggerKind.CREATURE_ENTER_CELL_AFTER)));if(r.input().responseKind()==ReadyResponseKind.SPELL)check(r.heldCastFrameKey()!=null&&allCasts.containsKey(r.heldCastFrameKey())&&Objects.equals(allCasts.get(r.heldCastFrameKey()).readyKey(),r.readyKey()));else check(r.heldCastFrameKey()==null);}
            for(ReadyPreparation r:preparations.values()) {
                ReadyInput input=r.input();check(input.responseKind()!=null);
                if(input.responseKind()==ReadyResponseKind.ATTACK)check(input.targetId()!=null&&actors.contains(input.targetId())&&input.itemId()!=null&&input.path().isEmpty()&&input.spell()==null);
                if(input.responseKind()==ReadyResponseKind.MOVE)check(input.targetId()==null&&input.itemId()==null&&!input.path().isEmpty()&&input.path().size()<=64&&input.spell()==null);
                if(input.responseKind()==ReadyResponseKind.SPELL) {
                    check(input.targetId()==null&&input.itemId()==null&&input.path().isEmpty()&&input.spell()!=null);
                    CastAttempt held=allCasts.get(r.heldCastFrameKey());check(held.actorId().equals(r.actorId())&&held.castingRule().equals(input.spell().castingRule())&&held.spell().equals(input.spell().spell())&&held.targets().equals(input.spell().targets()));
                    if(r.status()==ReadyStatus.ARMED)check(held.status()==CastStatus.HELD);
                    long holders=b.concentrationHolders().stream().filter(h->h.actorId().equals(r.actorId())&&h.kind()==ConcentrationKind.READY_SPELL&&h.sourceKey().equals(r.readyKey())).count();
                    check(holders==(Set.of(ReadyStatus.ARMED,ReadyStatus.PREPARING).contains(r.status())?1:0));
                }
                long releases=b.flows().stream().flatMap(f->f.readyReleases().stream()).filter(v->v.readyKey().equals(r.readyKey())).count();check(releases==(r.status()==ReadyStatus.RELEASED?1:0));
            }
            for(Flow flow:b.flows())for(ReadyRelease release:flow.readyReleases()) {
                ReadyPreparation preparation=preparations.get(release.readyKey());TriggerFact trigger=triggers.get(release.triggerKey());check(preparation!=null&&preparation.status()==ReadyStatus.RELEASED&&preparation.input().trigger().kind()==trigger.kind()&&preparation.input().trigger().subjectId().equals(trigger.subjectId())&&(preparation.input().trigger().position()==null||preparation.input().trigger().position().equals(trigger.position())));
                if(release.completed())check(Set.of("RELEASED","RESPONSE_UNAVAILABLE","TARGET_UNAVAILABLE").contains(release.outcome()));
            }
            for(CastAttempt cast:allCasts.values())if(cast.readyKey()!=null)check(preparations.containsKey(cast.readyKey())&&Objects.equals(preparations.get(cast.readyKey()).heldCastFrameKey(),cast.frameKey()));
            Set<UUID> concentrating=new HashSet<>();for(ConcentrationHolder h:b.concentrationHolders()) {check(actors.contains(h.actorId())&&concentrating.add(h.actorId())&&h.kind()!=null&&h.sourceKey()!=null);if(h.kind()==ConcentrationKind.BLESS_EFFECT)check(b.ongoingEffects().stream().anyMatch(e->e.effectKey().equals(h.sourceKey())&&e.casterId().equals(h.actorId())&&e.status()==EffectStatus.ACTIVE));else {ReadyPreparation r=preparations.get(h.sourceKey());check(r!=null&&r.actorId().equals(h.actorId())&&Set.of(ReadyStatus.PREPARING,ReadyStatus.ARMED).contains(r.status()));}}
            check(b.ongoingEffects().size()<=128);for(OngoingEffect e:b.ongoingEffects()) {id(e.effectKey());check(keys.add(e.effectKey())&&actors.contains(e.casterId())&&actors.containsAll(e.targets())&&!e.targets().isEmpty()&&e.targets().size()<=3&&e.expiresAtSecond()==e.startedAtSecond()+60&&e.startedAtSecond()>=0&&e.status()!=null&&((e.status()==EffectStatus.ACTIVE)==(e.endReason()==null))&&allCasts.values().stream().anyMatch(c->Objects.equals(c.effectKey(),e.effectKey())&&c.spell().equals(e.spell())&&c.actorId().equals(e.casterId())));if(e.status()==EffectStatus.ACTIVE)check(b.concentrationHolders().stream().anyMatch(h->h.kind()==ConcentrationKind.BLESS_EFFECT&&h.sourceKey().equals(e.effectKey())));}
            for(ShieldEffect e:b.shields()) {id(e.effectKey());CastAttempt c=allCasts.get(e.castFrameKey());check(keys.add(e.effectKey())&&actors.contains(e.actorId())&&e.startedTurnNumber()>0&&e.expiresAtTurnNumber()>e.startedTurnNumber()&&e.expiresAtTurnNumber()<=e.startedTurnNumber()+actors.size()&&c!=null&&Objects.equals(c.effectKey(),e.effectKey())&&c.actorId().equals(e.actorId())&&e.status()!=null&&((e.status()==EffectStatus.ACTIVE)==(e.endReason()==null)));}
            if(b.status()==Status.ENDED)check(b.concentrationHolders().isEmpty()&&b.shields().stream().noneMatch(e->e.status()==EffectStatus.ACTIVE)&&b.readyPreparations().stream().noneMatch(r->r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING));
        } catch(NullPointerException|NoSuchElementException malformed) {throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
    }
    static Battle expireTurnFacts(Battle b,List<CombatFact> facts) {
        UUID incoming=b.activeActor();List<ReadyPreparation> ready=new ArrayList<>();Set<UUID> expired=new HashSet<>();
        for(ReadyPreparation r:b.readyPreparations()) {if(r.actorId().equals(incoming)&&r.expiresAtTurnNumber()<=b.turnNumber()&&Set.of(ReadyStatus.PREPARING,ReadyStatus.ARMED).contains(r.status())) {r=new ReadyPreparation(r.readyKey(),r.actorId(),r.declaredTurnNumber(),r.expiresAtTurnNumber(),r.input(),ReadyStatus.EXPIRED,r.heldCastFrameKey(),"TURN_STARTED");expired.add(r.readyKey());facts.add(new CombatFact("READY_EXPIRED",incoming,r.readyKey(),"TURN_STARTED"));}ready.add(r);}
        List<ShieldEffect> shields=new ArrayList<>();for(ShieldEffect e:b.shields()) {if(e.actorId().equals(incoming)&&e.expiresAtTurnNumber()<=b.turnNumber()&&e.status()==EffectStatus.ACTIVE) {e=new ShieldEffect(e.effectKey(),e.castFrameKey(),e.actorId(),e.startedTurnNumber(),e.expiresAtTurnNumber(),EffectStatus.ENDED,"TURN_STARTED");facts.add(new CombatFact("SHIELD_ENDED",incoming,e.effectKey(),"TURN_STARTED"));}shields.add(e);}
        return Schema2ActionFlowRules.rebuild(b,b.combatants(),b.flows(),ready,b.triggerFacts(),shields,b.concentrationHolders().stream().filter(h->h.kind()!=ConcentrationKind.READY_SPELL||!expired.contains(h.sourceKey())).toList(),b.version());
    }
    private static boolean validBless(UUID source,Integer value) {return (source==null)==(value==null)&&(value==null||value>=1&&value<=4);}
    private static void id(UUID value) {uuid(value);}
    private static void check(boolean valid) {if(!valid)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
}
