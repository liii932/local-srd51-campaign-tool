package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.DamageType;
import com.dndtool.module.composition.Schema2CompositionKernel.MonsterAttackDamage;
import com.dndtool.module.composition.Schema2CompositionKernel.MonsterAttackProfile;
import com.dndtool.module.composition.Schema2CompositionKernel.MonsterOnHitSave;
import static com.dndtool.module.composition.Schema2CompositionKernel.MAX_WEAPON_RANGE_FEET;
import static com.dndtool.module.composition.Schema2CompositionKernel.MONSTER_ATTACK_MAX_BONUS;
import static com.dndtool.module.composition.Schema2CompositionKernel.MONSTER_ATTACK_MIN_BONUS;
import static com.dndtool.module.composition.Schema2CompositionKernel.MONSTER_ATTACK_REACH_FEET;
import java.util.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Structural verification of the saved business graph, independent of JDBC row identifiers. */
final class Schema2CombatStateValidator {
    private Schema2CombatStateValidator() { }
    static void validate(Battle b) {
        try {
            id(b.combatId());id(b.campaignId());check(!b.combatId().equals(b.campaignId())&&b.combatRule()!=null&&b.combatRule().ruleType().equals("adventure.combat")&&b.status()!=null&&b.round()>0&&b.turnNumber()>0&&b.version()>=0&&b.version()<Schema2CombatRules.MAX_FACTS);
            check(b.combatants().size()<=64&&b.flows().size()<=128);
            check(b.initiative().isEmpty() ? b.status()!=Status.ACTIVE && b.activeOrdinal()==0
                    : b.combatants().size()>=2 && b.initiative().size()==b.combatants().size()
                        && b.activeOrdinal()>=0 && b.activeOrdinal()<b.initiative().size());
            Set<UUID> actors=new HashSet<>(),keys=new HashSet<>();Set<Position> positions=new HashSet<>();
            for(Combatant a:b.combatants()) {
                id(a.characterId());check(actors.add(a.characterId())&&positions.add(a.position())&&a.side()>=0&&a.side()<16&&validActionBudgets(a)&&a.movementSpentHalfFeet()>=0&&a.movementSpentHalfFeet()<=480&&a.movementBonus()>=0&&a.movementBonus()<=120&&a.movementBonus()%5==0);
                AttackSequence s=a.sequence();if(s!=null) {
                    id(s.sequenceKey());check(keys.add(s.sequenceKey())&&!a.actionAvailable()
                            &&s.limit()>=1&&s.limit()<=2&&s.performed()>=0&&s.forfeited()>=0
                            &&s.performed()+s.forfeited()<=s.limit()&&s.closed()==(s.performed()+s.forfeited()==s.limit()));
                    if(!s.closed())check(b.status()==Status.ACTIVE&&a.characterId().equals(b.activeActor()));
                }
            }
            Set<UUID> ordered=new HashSet<>();for(int i=0;i<b.initiative().size();i++) {Initiative v=b.initiative().get(i);check(actors.contains(v.characterId())&&ordered.add(v.characterId())&&v.ordinal()==i&&v.d20()>=1&&v.d20()<=20&&v.dexterityModifier()>=-5&&v.dexterityModifier()<=10&&v.total()==v.d20()+v.dexterityModifier()&&(i==0||b.initiative().get(i-1).total()>=v.total()));}
            check(validOpeningState(b));
            Map<UUID,Frame> allFrames=new HashMap<>();Map<UUID,CastAttempt> allCasts=new HashMap<>();Map<UUID,TriggerFact> triggers=new HashMap<>();int active=0;
            Map<UUID,List<TriggerFact>> movementTriggers=new HashMap<>();
            for(TriggerFact t:b.triggerFacts()) {
                id(t.triggerKey());check(keys.add(t.triggerKey())&&triggers.put(t.triggerKey(),t)==null&&reference(actors,t.subjectId(),false)&&(t.targetId()==null||reference(actors,t.targetId(),false))&&t.ordinal()>=0&&t.ordinal()<64&&t.kind()!=null);
                if (Set.of(TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE,TriggerKind.CREATURE_ENTER_CELL_AFTER).contains(t.kind()))
                    movementTriggers.computeIfAbsent(t.sourceFrameKey(),ignored->new ArrayList<>()).add(t);
            }
            for(Flow f:b.flows()) {
                boolean live=f.status()==FlowStatus.ACTIVE;
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
                        Opportunity o=w.opportunities().get(j);id(o.opportunityKey());check(keys.add(o.opportunityKey())&&reference(actors,o.actorId(),live)&&candidates.add(o.actorId())&&o.priority()==j&&!o.options().isEmpty()&&new HashSet<>(o.options()).size()==o.options().size());
                        for(ReactionOption option:o.options()) {check(option.kind()!=null);if(option.kind()!=ReactionKind.OPPORTUNITY_ATTACK)check(option.monsterAttack()==null);if(option.kind()==ReactionKind.OPPORTUNITY_ATTACK)check(((option.itemId()!=null) != (option.monsterAttack()!=null)) && (option.monsterAttack()==null || Schema2CombatHistory.validAction(option.monsterAttack())) &&option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0&&option.readyKey()==null);else if(option.kind()==ReactionKind.READY_RELEASE)check(option.readyKey()!=null&&option.itemId()==null&&option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0);else check(option.itemId()==null&&option.readyKey()==null&&option.castingRule()!=null&&option.spell()!=null&&option.slotLevel()>=1&&option.slotLevel()<=9);}
                        if(o.status()==OpportunityStatus.PENDING) {pending++;check(o.childFrameKey()==null&&o.reason()==null);}
                        else if(o.status()==OpportunityStatus.COMPLETED||o.status()==OpportunityStatus.DECLARED) {Frame child=frames.get(o.childFrameKey());check(child!=null&&child.invocation()==Invocation.REACTION&&Objects.equals(child.parentFrameKey(),o.selectionFrameKey())&&(o.status()==OpportunityStatus.COMPLETED)==(child.status()==FrameStatus.TERMINAL)&&o.reason()==null);}
                        else check(o.childFrameKey()==null&&o.reason()!=null);
                        if(o.selectionFrameKey()!=null) {Frame selection=frames.get(o.selectionFrameKey());check(selection!=null&&selection.kind()==FrameKind.REACTION_SELECTION&&Objects.equals(selection.parentFrameKey(),w.ownerFrameKey())&&Objects.equals(selection.windowKey(),w.windowKey())&&Objects.equals(selection.opportunityKey(),o.opportunityKey()));}
                    }
                    if(w.status()!=WindowStatus.OPEN)check(pending==0&&w.opportunities().stream().noneMatch(o->o.status()==OpportunityStatus.DECLARED));
                    if(f.status()==FlowStatus.COMPLETED)check(w.status()==WindowStatus.CONSUMED);
                }
                for(Frame v:f.frames())if(v.windowKey()!=null)check(windows.contains(v.windowKey()));
                Set<UUID> movementKeys=new HashSet<>();
                for(Movement movement:f.movements()) {
                    id(movement.movementKey());
                    check(movementKeys.add(movement.movementKey()));
                    validateMovement(b,movement,frames.get(movement.movementKey()),actors,live,
                            movementTriggers.getOrDefault(movement.movementKey(),List.of()));
                }
                for (Frame frame:f.frames()) if (frame.kind()==FrameKind.MOVEMENT) check(movementKeys.contains(frame.ownerKey()));
                for(AttackAttempt a:f.attacks()) {
                    check(a.sequenceKey()==null ? a.attackOrdinal()==0 : !a.opportunityAttack()&&a.attackOrdinal()>=0&&a.attackOrdinal()<2);
                    check(frames.containsKey(a.frameKey())&&frames.get(a.frameKey()).kind()==FrameKind.ATTACK&&reference(actors,a.actorId(),live)&&reference(actors,a.targetId(),live)&&(a.spellAttack()!=null||!a.actorId().equals(a.targetId()))&&validAttackSource(a)&&a.firstD20()>=1&&a.firstD20()<=20&&a.selectedD20()>=1&&a.selectedD20()<=20&&(a.secondD20()==null||a.secondD20()>=1&&a.secondD20()<=20)&&a.status()!=null&&a.armorClass()>0&&a.finalArmorClass()>0&&a.damageAmount()>=0&&a.damageDice().size()<=40&&a.damageDice().stream().allMatch(d->d>=1&&d<=12)&&validBless(a.blessingEffectKey(),a.blessingD4()));
                    if(a.status()==AttackStatus.HIT_PENDING_DAMAGE)check(a.initialHit()&&a.hit()&&a.damageDice().isEmpty()&&a.damageAmount()==0&&frames.get(a.frameKey()).status()!=FrameStatus.TERMINAL);
                    if(!a.hit())check(a.damageDice().isEmpty()&&a.damageAmount()==0);
                    if (a.spellAttack()!=null) check(validSpellAttackLink(f,frames.get(a.frameKey()),a));
                }
                for(CastAttempt c:f.casts()) {check(frames.containsKey(c.frameKey())&&frames.get(c.frameKey()).kind()==FrameKind.CAST&&allCasts.put(c.frameKey(),c)==null&&reference(actors,c.actorId(),live)&&validCastSource(c,frames.get(c.frameKey()))&&c.spell()!=null&&c.spell().ruleType().equals("adventure.spell")&&(c.coneDirection()==null?!c.targets().isEmpty():c.targets().isEmpty()&&c.coneDirection()>=0&&c.coneDirection()<8&&c.readyKey()==null)&&c.targets().size()<=3&&c.targets().stream().allMatch(target->reference(actors,target,live))&&new HashSet<>(c.targets()).size()==c.targets().size()&&c.status()!=null&&c.healingDice().size()<=1&&c.healingDice().stream().allMatch(d->d>=1&&d<=8)&&c.appliedHealing()>=0&&c.requestedHealing()>=c.appliedHealing());if(c.status()==CastStatus.PENDING||c.status()==CastStatus.COUNTERED||c.status()==CastStatus.HELD||c.status()==CastStatus.INTERRUPTED)check(c.healingDice().isEmpty()&&c.requestedHealing()==0&&c.appliedHealing()==0&&c.effectKey()==null);}
                for(SavingThrowAttempt s:f.savingThrows())check(frames.containsKey(s.frameKey())&&reference(actors,s.subjectId(),live)&&s.d20()>=1&&s.d20()<=20&&s.proficiencyBonus()>=0&&s.proficiencyBonus()<=6&&validBless(s.blessingEffectKey(),s.blessingD4())&&s.total()==s.d20()+s.abilityModifier()+s.proficiencyBonus()+(s.blessingD4()==null?0:s.blessingD4())&&s.success()==(s.total()>=s.difficultyClass()));
                for(AbilityCheckAttempt c:f.abilityChecks())check(frames.containsKey(c.frameKey())&&reference(actors,c.subjectId(),live)&&c.kind().equals("COUNTERSPELL")&&c.abilityKey().equals("ability.intelligence")&&c.d20()>=1&&c.d20()<=20&&c.proficiencyBonus()==0&&c.total()==c.d20()+c.abilityModifier()&&c.success()==(c.total()>=c.difficultyClass()));
                for(ReadyRelease r:f.readyReleases())check(frames.containsKey(r.frameKey())&&frames.get(r.frameKey()).kind()==FrameKind.READY_RELEASE&&triggers.containsKey(r.triggerKey())&&r.completed()==(r.outcome()!=null)&&r.completed()==(frames.get(r.frameKey()).status()==FrameStatus.TERMINAL));
            }
            validateAttackSequences(b);
            Schema2MeleeControlRules.validate(b);
            Schema2ObservationRules.validate(b);
            validateMonstersAndBatches(b,actors,allFrames,keys);
            check(active<=1&&(b.status()!=Status.ENDED||active==0));
            for(TriggerFact t:triggers.values())check(allFrames.containsKey(t.sourceFrameKey()));
            for(CastAttempt c:allCasts.values())if(c.targetCastFrameKey()!=null)check(allCasts.containsKey(c.targetCastFrameKey())&&!c.frameKey().equals(c.targetCastFrameKey()));
            Map<UUID,ReadyPreparation> preparations=new HashMap<>();for(ReadyPreparation r:b.readyPreparations()) {id(r.readyKey());boolean live=readyActive(r);check(keys.add(r.readyKey())&&preparations.put(r.readyKey(),r)==null&&reference(actors,r.actorId(),live)&&r.declaredTurnNumber()>0&&r.expiresAtTurnNumber()>r.declaredTurnNumber()&&(!live||r.expiresAtTurnNumber()==r.declaredTurnNumber()+actors.size())&&r.input()!=null&&r.status()!=null&&reference(actors,r.input().trigger().subjectId(),live)&&Set.of(TriggerKind.CREATURE_ENTER_CELL_AFTER,TriggerKind.ATTACK_COMPLETED,TriggerKind.CAST_COMPLETED,TriggerKind.MONSTER_ACTION_COMPLETED).contains(r.input().trigger().kind())&&((r.input().trigger().position()!=null)==(r.input().trigger().kind()==TriggerKind.CREATURE_ENTER_CELL_AFTER)));if(r.input().responseKind()==ReadyResponseKind.SPELL)check(r.heldCastFrameKey()!=null&&allCasts.containsKey(r.heldCastFrameKey())&&Objects.equals(allCasts.get(r.heldCastFrameKey()).readyKey(),r.readyKey()));else check(r.heldCastFrameKey()==null);}
            for(ReadyPreparation r:preparations.values()) {
                ReadyInput input=r.input();check(input.responseKind()!=null);
                if(input.responseKind()==ReadyResponseKind.ATTACK)check(input.targetId()!=null&&reference(actors,input.targetId(),readyActive(r))&&input.itemId()!=null&&input.path().isEmpty()&&input.spell()==null);
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
            check(b.ongoingEffects().size()<=128);for(OngoingEffect e:b.ongoingEffects()) {id(e.effectKey());check(keys.add(e.effectKey())&&reference(actors,e.casterId(),e.status()==EffectStatus.ACTIVE)&&e.targets().stream().allMatch(target->reference(actors,target,e.status()==EffectStatus.ACTIVE))&&!e.targets().isEmpty()&&e.targets().size()<=3&&e.expiresAtSecond()==e.startedAtSecond()+60&&e.startedAtSecond()>=0&&e.status()!=null&&((e.status()==EffectStatus.ACTIVE)==(e.endReason()==null))&&allCasts.values().stream().anyMatch(c->Objects.equals(c.effectKey(),e.effectKey())&&c.spell().equals(e.spell())&&c.actorId().equals(e.casterId())));if(e.status()==EffectStatus.ACTIVE)check(b.concentrationHolders().stream().anyMatch(h->h.kind()==ConcentrationKind.BLESS_EFFECT&&h.sourceKey().equals(e.effectKey())));}
            for(ShieldEffect e:b.shields()) {id(e.effectKey());CastAttempt c=allCasts.get(e.castFrameKey());check(keys.add(e.effectKey())&&reference(actors,e.actorId(),e.status()==EffectStatus.ACTIVE)&&e.startedTurnNumber()>0&&e.expiresAtTurnNumber()>e.startedTurnNumber()&&(e.status()!=EffectStatus.ACTIVE||e.expiresAtTurnNumber()<=e.startedTurnNumber()+actors.size())&&c!=null&&Objects.equals(c.effectKey(),e.effectKey())&&c.actorId().equals(e.actorId())&&e.status()!=null&&((e.status()==EffectStatus.ACTIVE)==(e.endReason()==null)));}
            if(b.status()==Status.ENDED)check(b.concentrationHolders().isEmpty()&&b.shields().stream().noneMatch(e->e.status()==EffectStatus.ACTIVE)&&b.readyPreparations().stream().noneMatch(r->r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING));
        } catch(NullPointerException|NoSuchElementException malformed) {throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
    }

    static boolean validOpeningState(Battle battle) {
        if (battle.initiative().isEmpty()) return true;
        Map<UUID, Initiative> initiative = new HashMap<>();
        Map<UUID, Combatant> actors = new HashMap<>();
        Map<UUID, MonsterInstance> monsters = new HashMap<>();
        for (Initiative entry : battle.initiative()) initiative.put(entry.characterId(), entry);
        for (Combatant actor : battle.combatants()) actors.put(actor.characterId(), actor);
        for (MonsterInstance monster : battle.monsters()) monsters.put(monster.monsterId(), monster);
        for (Initiative entry : battle.initiative()) {
            UUID id = entry.characterId();
            Combatant actor = actors.get(id);
            Initiative source = initiative.get(entry.rollSource());
            if (actor == null || source == null || !source.characterId().equals(source.rollSource()) || source.d20() != entry.d20())
                return false;
            if (!id.equals(source.characterId())) {
                MonsterInstance member = monsters.get(id), leader = monsters.get(source.characterId());
                if (member == null || leader == null || !member.monster().equals(leader.monster())
                        || entry.dexterityModifier() != source.dexterityModifier()
                        || actor.side() != actors.get(source.characterId()).side()) return false;
            }
            if (battle.surprised(id) && (actor.actionsRemaining() != 0 || actor.bonusActionsRemaining() != 0
                    || actor.reactionAvailable() || actor.movementSpentHalfFeet() != 0 || actor.movementBonus() != 0))
                return false;
        }
        return true;
    }

    static boolean validActionBudgets(Combatant actor) {
        return actor.actionsRemaining() >= 0 && actor.actionsRemaining() <= Combatant.TURN_ACTIONS
                && actor.bonusActionsRemaining() >= 0 && actor.bonusActionsRemaining() <= Combatant.TURN_BONUS_ACTIONS;
    }

    private static void validateMovement(Battle battle, Movement movement, Frame frame, Set<UUID> actors, boolean live,
                                         List<TriggerFact> movementTriggers) {
        check(frame!=null&&frame.kind()==FrameKind.MOVEMENT&&frame.ownerKey().equals(movement.movementKey())
                &&reference(actors,movement.actorId(),live)&&movement.origin()!=null&&movement.path().size()>=1&&movement.path().size()<=64
                &&movement.cursor()>=0&&movement.cursor()<=movement.path().size()&&movement.spent()==movement.cursor()*5
                &&movement.spentHalfFeet()>=2*movement.spent()&&movement.spentHalfFeet()<=4*movement.spent()
                &&movement.spentHalfFeet()%10==0&&movement.status()!=null&&movement.readied()==(movement.allowanceFeet()>0)
                &&movement.allowanceFeet()>=0&&movement.allowanceFeet()<=120);
        if (movement.readied()) check(movement.spentHalfFeet()<=movement.allowanceFeet()*2);
        if (movement.status()==MovementStatus.COMPLETED)
            check(frame.status()==FrameStatus.TERMINAL&&movement.cursor()==movement.path().size()&&movement.stopReason()==null);
        else if (movement.status()==MovementStatus.PENDING) check(live&&frame.status()!=FrameStatus.TERMINAL&&movement.stopReason()==null);
        else check(movement.stopReason()!=null&&(live||frame.status()==FrameStatus.TERMINAL));

        Position origin=movement.origin();
        for (Position next:movement.path()) {
            check(origin.distanceFeet(next)==5);
            origin=next;
        }
        DragPlan drag=movement.drag();
        if (drag==null) return;
        check(!movement.readied()&&frame.invocation()==Invocation.ROOT&&drag.origin()!=null
                &&reference(actors,drag.targetId(),live)&&!movement.actorId().equals(drag.targetId())
                &&drag.path().size()==movement.path().size()&&drag.allowanceHalfFeet()>=10*drag.path().size()
                &&drag.allowanceHalfFeet()>=movement.spentHalfFeet()&&drag.allowanceHalfFeet()<=480);
        GrappleRelation relation=battle.meleeControl().grapples().stream().filter(g->g.grappleKey().equals(drag.grappleKey()))
                .findFirst().orElseThrow();
        check(relation.grapplerId().equals(movement.actorId())&&relation.targetId().equals(drag.targetId())
                &&movement.origin().distanceFeet(drag.origin())>0&&movement.origin().distanceFeet(drag.origin())<=relation.reachFeet());
        origin=movement.origin();
        Position targetOrigin=drag.origin();
        for (int i=0;i<movement.path().size();i++) {
            Position next=movement.path().get(i), targetNext=drag.path().get(i);
            check(Schema2ActionFlowRules.validDragStep(origin,next,targetOrigin,targetNext,relation.reachFeet()));
            origin=next;
            targetOrigin=targetNext;
        }
        Set<Integer> departures=new HashSet<>(), moverEntries=new HashSet<>(), targetEntries=new HashSet<>();
        for (TriggerFact trigger:movementTriggers) {
            int segment=trigger.ordinal();
            check(segment<movement.path().size());
            if (trigger.kind()==TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE) {
                check(segment<=movement.cursor()&&departures.add(segment)&&trigger.subjectId().equals(movement.actorId())
                        &&drag.targetId().equals(trigger.targetId())&&movement.path().get(segment).equals(trigger.position()));
            } else {
                check(trigger.kind()==TriggerKind.CREATURE_ENTER_CELL_AFTER&&segment<movement.cursor()&&trigger.targetId()==null);
                if (trigger.subjectId().equals(movement.actorId()))
                    check(moverEntries.add(segment)&&movement.path().get(segment).equals(trigger.position()));
                else check(trigger.subjectId().equals(drag.targetId())&&targetEntries.add(segment)
                        &&drag.path().get(segment).equals(trigger.position()));
            }
        }
        check(departures.containsAll(moverEntries)&&moverEntries.equals(targetEntries)&&moverEntries.size()==movement.cursor());
    }

    static boolean validAttackSource(AttackAttempt attack) {
        var source = attack.monsterAttack();
        var ranged = attack.rangedAttack();
        if (!validAttackObservation(attack) || !validD20Selection(attack)) return false;
        if (ranged != null && !validRangedAttack(attack, ranged)) return false;
        if (attack.spellAttack() != null) return validSpellAttack(attack);
        if (source == null) return attack.itemId() != null;
        if (attack.itemId() != null || attack.sequenceKey() != null || attack.attackOrdinal() != 0
                || !attack.damageDice().isEmpty() || attack.abilityModifier() != 0 || attack.proficiencyBonus() != 0
                || !Schema2CombatHistory.validAction(source.action()) || source.packTacticsAllies().size() > 63
                || new HashSet<>(source.packTacticsAllies()).size() != source.packTacticsAllies().size()
                || source.packTacticsAllies().contains(attack.actorId()) || source.packTacticsAllies().contains(attack.targetId())
                || !source.packTacticsAllies().isEmpty() && !attack.advantage()) return false;
        int selected = attack.selectedD20();
        int total = selected + source.hitBonus() + (attack.blessingD4() == null ? 0 : attack.blessingD4());
        if (attack.initialHit() != (attack.targetAtAimedPosition() && (selected == 20 || selected != 1 && total >= attack.armorClass()))
                || attack.hit() != (attack.targetAtAimedPosition() && (selected == 20 || selected != 1 && total >= attack.finalArmorClass()))) return false;
        if (source.hitBonus() < MONSTER_ATTACK_MIN_BONUS || source.hitBonus() > MONSTER_ATTACK_MAX_BONUS
                || (ranged == null ? !MONSTER_ATTACK_REACH_FEET.contains(source.reachFeet()) : source.reachFeet() != 0)) return false;
        boolean settled = attack.hit() && attack.status() == AttackStatus.RESOLVED;
        if (!settled) return source.damageComponents().isEmpty() && attack.damageAmount() == 0 && source.onHit() == null;
        var onHit = source.onHit();
        if (onHit != null && !validMonsterOnHit(onHit)) return false;
        var profile = new ArrayList<MonsterAttackDamage>();
        try {
            for (var component : source.damageComponents())
                profile.add(new MonsterAttackDamage(component.damageType(), component.diceCount(), component.diceSides(), component.modifier()));
            new MonsterAttackProfile(source.hitBonus(), source.reachFeet(), ranged == null ? 0 : ranged.normalRangeFeet(),
                    ranged == null ? 0 : ranged.longRangeFeet(), profile,
                    onHit == null ? null : new MonsterOnHitSave(onHit.difficultyClass()));
        } catch (IllegalArgumentException invalidProfile) {
            return false;
        }
        int damage = 0;
        for (var component : source.damageComponents()) {
            if (component.dice().size() != component.diceCount() * (attack.critical() ? 2 : 1)
                    || component.dice().stream().anyMatch(d -> d < 1 || d > component.diceSides())
                    || component.rawDamage() != Math.max(0, component.dice().stream().mapToInt(Integer::intValue).sum() + component.modifier())
                    || component.defenses().stream().anyMatch(d -> d.source() == null || !Set.of("IMMUNITY", "RESISTANCE", "VULNERABILITY").contains(d.kind()))
                    || new HashSet<>(component.defenses()).size() != component.defenses().size()
                    || component.effectiveDamage() != Schema2SaveDamageRules.damage(component.rawDamage(), false, component.defenses())) return false;
            damage += component.effectiveDamage();
        }
        return damage == attack.damageAmount();
    }

    static boolean validCastSource(CastAttempt cast, Frame frame) {
        if (cast.castingRule() == null) return false;
        if (cast.slotLevel() != 0) return Set.of("character.spellcasting","character.wizard_spellcasting").contains(cast.castingRule().ruleType())
                && cast.slotResource()!=null && "character.resource".equals(cast.slotResource().ruleType())
                && cast.slotLevel()>=1 && cast.slotLevel()<=9 && cast.sacredFlame()==null;
        if (!Schema2SpellRules.CLASS_CANTRIPS.equals(cast.castingRule().ruleType()) || frame.invocation()!=Invocation.ROOT
                || cast.targets().size()!=1 || cast.slotResource()!=null || cast.materialItemId()!=null
                || cast.targetCastFrameKey()!=null || cast.readyKey()!=null || cast.coneDirection()!=null
                || cast.status()==CastStatus.HELD || cast.healingModifier()!=0 || !cast.healingDice().isEmpty()
                || cast.requestedHealing()!=0 || cast.appliedHealing()!=0 || cast.effectKey()!=null) return false;
        var sacred = cast.sacredFlame();
        if (sacred==null) return true;
        var save = sacred.savingThrow();
        return cast.status()==CastStatus.RESOLVED && save!=null && save.targetOrdinal()==0
                && Schema2ActionFlowRules.SACRED_FLAME_DEX.equals(save.purpose()) && "ability.dexterity".equals(save.abilityKey())
                && Schema2SavingThrowRules.validResult(save)
                && (save.success() ? sacred.damage()==null : validCantripDamage(sacred.damage(),DamageType.RADIANT,8,false));
    }

    static boolean validSpellAttackLink(Flow flow, Frame frame, AttackAttempt attack) {
        UUID castKey = attack.spellAttack().castFrameKey();
        return castKey!=null && castKey.equals(frame.parentFrameKey()) && frame.kind()==FrameKind.ATTACK
                && frame.invocation()==Invocation.CONTINUATION
                && flow.casts().stream().anyMatch(cast -> cast.frameKey().equals(castKey) && cast.slotLevel()==0
                    && cast.sacredFlame()==null && cast.actorId().equals(attack.actorId())
                    && cast.targets().equals(List.of(attack.targetId()))
                    && (frame.status()==FrameStatus.TERMINAL
                        ? cast.status()==CastStatus.RESOLVED : cast.status()==CastStatus.PENDING));
    }

    private static boolean validSpellAttack(AttackAttempt attack) {
        if (attack.monsterAttack()!=null || attack.itemId()!=null || attack.rangedAttack()==null
                || attack.sequenceKey()!=null || attack.attackOrdinal()!=0 || attack.opportunityAttack()
                || !attack.damageDice().isEmpty()) return false;
        int selected = attack.selectedD20();
        int total = selected + attack.abilityModifier() + attack.proficiencyBonus()
                + (attack.blessingD4()==null ? 0 : attack.blessingD4());
        boolean couldHit = attack.targetAtAimedPosition() && selected!=1;
        boolean initial = couldHit && (selected==20 || total>=attack.armorClass());
        boolean hit = couldHit && (selected==20 || total>=attack.finalArmorClass());
        if (initial!=attack.initialHit() || hit!=attack.hit() || selected==20 && hit && !attack.critical()
                || !hit && attack.critical()) return false;
        var damage = attack.spellAttack().damage();
        if (attack.spellAttack().unseenDeadAtAim())
            return !attack.spellAttack().targetDeadBeforeDamage() && !attack.targetAtAimedPosition()
                    && attack.status()==AttackStatus.RESOLVED && !hit && damage==null && attack.damageAmount()==0;
        if (attack.spellAttack().targetDeadBeforeDamage())
            return attack.status()==AttackStatus.RESOLVED && hit && damage==null && attack.damageAmount()==0;
        return attack.status()==AttackStatus.RESOLVED && hit
                ? validCantripDamage(damage,DamageType.FIRE,10,attack.critical()) && attack.damageAmount()==damage.effectiveDamage()
                : damage==null && attack.damageAmount()==0;
    }

    private static boolean validCantripDamage(AttackDamageComponent damage, DamageType type, int sides, boolean critical) {
        return damage!=null && damage.damageType()==type && damage.diceSides()==sides && damage.modifier()==0
                && damage.diceCount()>=1 && damage.diceCount()<=2
                && damage.dice().size()==damage.diceCount()*(critical?2:1)
                && damage.dice().stream().allMatch(die -> die>=1 && die<=sides)
                && damage.rawDamage()==damage.dice().stream().mapToInt(Integer::intValue).sum()
                && damage.defenses().stream().allMatch(defense -> defense.source()!=null && Set.of("IMMUNITY","RESISTANCE","VULNERABILITY").contains(defense.kind()))
                && new HashSet<>(damage.defenses()).size()==damage.defenses().size()
                && damage.effectiveDamage()==Schema2SaveDamageRules.damage(damage.rawDamage(),false,damage.defenses());
    }

    private static boolean validAttackObservation(AttackAttempt attack) {
        Cover cover = attack.targetCover();
        if (cover == null || cover == Cover.TOTAL || attack.aimedPosition() == null
                || !attack.attackerSeesTarget() && !attack.disadvantage()) return false;
        if (attack.opportunityAttack() && !attack.attackerSeesTarget()) return false;
        if (!attack.targetAtAimedPosition()) {
            int aimArmorClass = Math.max(1, cover.armorClassBonus());
            return !attack.attackerSeesTarget() && !attack.targetSeesAttacker() && !attack.advantage()
                    && !attack.initialHit() && !attack.hit() && !attack.critical() && attack.status() == AttackStatus.RESOLVED
                    && attack.armorClass() == aimArmorClass && attack.finalArmorClass() == aimArmorClass;
        }
        return (attack.targetSeesAttacker() || attack.advantage()) && attack.armorClass() > cover.armorClassBonus()
                && attack.finalArmorClass() >= attack.armorClass();
    }

    private static boolean validD20Selection(AttackAttempt attack) {
        if ((attack.secondD20() != null) != (attack.advantage() != attack.disadvantage())) return false;
        int selected = attack.firstD20();
        if (attack.secondD20() != null) selected = attack.advantage()
                ? Math.max(selected, attack.secondD20()) : Math.min(selected, attack.secondD20());
        return selected == attack.selectedD20();
    }

    private static boolean validRangedAttack(AttackAttempt attack, RangedAttackEvidence ranged) {
        boolean spell = attack.spellAttack()!=null;
        if (attack.opportunityAttack() || ranged.distanceFeet() < 0 || !spell && ranged.distanceFeet() == 0 && attack.targetAtAimedPosition()
                || ranged.distanceFeet() % 5 != 0
                || ranged.normalRangeFeet() < 5 || ranged.normalRangeFeet() % 5 != 0
                || (spell ? ranged.longRangeFeet()!=ranged.normalRangeFeet() : ranged.longRangeFeet()<=ranged.normalRangeFeet())
                || ranged.longRangeFeet() % 5 != 0
                || ranged.longRangeFeet() > MAX_WEAPON_RANGE_FEET || ranged.distanceFeet() > ranged.longRangeFeet()
                || (ranged.closeEnemy() || ranged.distanceFeet() > ranged.normalRangeFeet()) && !attack.disadvantage())
            return false;
        UUID ammunition = ranged.ammunitionItemId();
        return spell || attack.monsterAttack() != null ? ammunition == null : attack.sequenceKey() != null
                && ammunition != null && ammunition.version() == 4 && ammunition.variant() == 2
                && !ammunition.equals(attack.itemId());
    }

    private static boolean validMonsterOnHit(MonsterAttackOnHitEvidence evidence) {
        BatchCheck save = evidence.savingThrow();
        if (evidence.immunitySource() != null && !"adventure.monster".equals(evidence.immunitySource().ruleType()))
            return false;
        if (evidence.outcome() != MonsterAttackOnHitEvidence.resolveOutcome(save, evidence.alreadyProne(), evidence.immunitySource()))
            return false;
        if (save == null) return evidence.selectedD20() == null;
        return save.targetOrdinal() == 0 && Schema2ActionFlowRules.MONSTER_HIT_STR.equals(save.purpose())
                && "ability.strength".equals(save.abilityKey()) && save.difficultyClass() == evidence.difficultyClass()
                && Schema2SavingThrowRules.validResult(save)
                && Objects.equals(Schema2SavingThrowRules.selectedD20(save), evidence.selectedD20());
    }

    private static void validateAttackSequences(Battle battle) {
        record Ordinal(UUID actorId, int ordinal, boolean spent) { }
        for (Combatant actor : battle.combatants()) {
            AttackSequence sequence = actor.sequence();
            if (sequence == null) continue;
            List<Ordinal> attempts = new ArrayList<>(battle.flows().stream().flatMap(flow -> flow.attacks().stream())
                    .filter(attack -> sequence.sequenceKey().equals(attack.sequenceKey()))
                    .map(attack -> new Ordinal(attack.actorId(), attack.attackOrdinal(),
                            attack.rangedAttack() != null || attack.status() == AttackStatus.RESOLVED)).toList());
            for (MeleeContest contest:battle.meleeControl().contests())
                if (sequence.sequenceKey().equals(contest.sequenceKey()))
                    attempts.add(new Ordinal(contest.actorId(),contest.attackOrdinal(),contest.outcome()!=null));
            attempts.sort(Comparator.comparingInt(Ordinal::ordinal));
            check(attempts.size() <= sequence.limit());
            int spent = 0;
            for (int ordinal = 0; ordinal < attempts.size(); ordinal++) {
                Ordinal attack = attempts.get(ordinal);
                check(attack.actorId().equals(actor.characterId()) && attack.ordinal() == ordinal);
                if (attack.spent()) spent++;
                else check(!sequence.closed() && ordinal == sequence.performed() && ordinal == attempts.size() - 1);
            }
            check(spent == sequence.performed() && attempts.size() - spent <= 1);
        }
    }

    private static boolean readyActive(ReadyPreparation ready) {
        return ready.status()==ReadyStatus.PREPARING || ready.status()==ReadyStatus.ARMED;
    }

    private static boolean reference(Set<UUID> current, UUID actor, boolean live) {
        id(actor);
        return !live || current.contains(actor);
    }

    private static void validateMonstersAndBatches(Battle b,Set<UUID> actors,Map<UUID,Frame> frames,Set<UUID> keys) {
        Set<UUID> monsters=new HashSet<>();for(MonsterInstance m:b.monsters()) {id(m.monsterId());check(actors.contains(m.monsterId())&&monsters.add(m.monsterId())&&m.monster()!=null&&m.monster().ruleType().equals("adventure.monster")&&m.hitPoints()>=0&&m.hitPoints()<=65535&&m.dead()==(m.hitPoints()==0));Set<Object> actions=new HashSet<>();for(var u:m.actionUses())check(u.action()!=null&&actions.add(u.action())&&u.action().rule().equals(m.monster()));}
        Comparator<UUID> uuidOrder=(a,z)-> {int h=Long.compareUnsigned(a.getMostSignificantBits(),z.getMostSignificantBits());return h!=0?h:Long.compareUnsigned(a.getLeastSignificantBits(),z.getLeastSignificantBits());};
        check(b.monsters().stream().map(MonsterInstance::monsterId).toList().equals(b.monsters().stream().map(MonsterInstance::monsterId).sorted(uuidOrder).toList()));
        check(b.combatants().stream().map(Combatant::characterId).toList().equals(b.combatants().stream().map(Combatant::characterId).sorted(Comparator.comparing((UUID id)->monsters.contains(id)).thenComparing(uuidOrder)).toList()));
        Set<UUID> owners=new HashSet<>();for(SaveDamageBatch batch:b.saveDamageBatches()) {
            id(batch.batchKey());id(batch.releaseOperationId());Frame f=frames.get(batch.frameKey());boolean live=batch.status()==BatchStatus.ROLLED;check(keys.add(batch.batchKey())&&owners.add(batch.frameKey())&&(batch.spellSource()==null?reference(monsters,batch.sourceId(),live)&&batch.action()!=null:!monsters.contains(batch.sourceId())&&reference(actors,batch.sourceId(),live)&&batch.action()==null)&&batch.releasedTurnNumber()>0&&batch.releasedTurnNumber()<=b.turnNumber()&&f!=null&&f.kind()==FrameKind.SAVE_DAMAGE&&f.ownerKey().equals(batch.batchKey())&&batch.status()!=null&&batch.targetProof()!=null);
            if(batch.spellSource()!=null) {
                var source=batch.spellSource();var cast=Schema2SpellRules.findCast(b,source.castFrameKey());
                check(cast!=null&&cast.actorId().equals(batch.sourceId())&&cast.spell().equals(source.spell())&&cast.slotLevel()==source.slotLevel()&&source.slotLevel()>=1&&source.slotLevel()<=3
                        &&source.abilityModifier()>=-5&&source.abilityModifier()<=10&&source.proficiencyBonus()>=2&&source.proficiencyBonus()<=3
                        &&Objects.equals(f.parentFrameKey(),cast.frameKey())&&Objects.equals(cast.coneDirection(),batch.targetProof().direction()));
                check(batch.status()==BatchStatus.ROLLED?cast.status()==CastStatus.PENDING:cast.status()==CastStatus.RESOLVED);
            }
            var p=batch.targetProof();check(p.policy().equals(Schema2SaveDamageRules.SPACE_POLICY)&&p.rosterVersion()>=0&&p.rosterVersion()<b.version()&&p.direction()>=0&&p.direction()<8&&p.candidates().size()<=64&&(!live||p.candidates().size()==actors.size())&&p.targets().size()<=64&&new HashSet<>(p.targets()).size()==p.targets().size());
            Set<UUID> candidates=new HashSet<>();for(var candidate:p.candidates())check(reference(actors,candidate.creature().creatureId(),live)&&candidates.add(candidate.creature().creatureId())&&candidate.position()!=null&&candidate.reason()!=null&&candidate.included()==p.targets().contains(candidate.creature())&&(!live||(candidate.creature().kind()==CreatureKind.MONSTER)==monsters.contains(candidate.creature().creatureId())));
            if(batch.status()==BatchStatus.NO_TARGETS)check(p.targets().isEmpty()&&batch.sharedRoll()==null&&batch.checks().isEmpty()&&batch.packets().isEmpty());
            else {var roll=batch.sharedRoll();check(!p.targets().isEmpty()&&roll!=null&&roll.dice().size()==batch.diceCount()&&roll.dice().stream().allMatch(d->d>=1&&d<=6)&&roll.rawTotal()==roll.dice().stream().mapToInt(Integer::intValue).sum()&&roll.effectiveTotal()==roll.rawTotal());id(roll.rollKey());}
            if(batch.status()==BatchStatus.ROLLED)check(batch.checks().isEmpty()&&batch.packets().isEmpty()&&batch.concentrationObligations().isEmpty()&&f.status()==FrameStatus.PENDING&&f.stage()==Stage.SAVE_DAMAGE_ROLLED);
            if(batch.status()==BatchStatus.COMPLETED) {
                int n=p.targets().size();check(batch.packets().size()==n&&batch.checks().size()>=n&&batch.checks().size()<=n*2);
                for(int i=0;i<batch.checks().size();i++) {var c=batch.checks().get(i);check(c.targetOrdinal()>=0&&c.targetOrdinal()<n&&c.proficiencyBonus()>=-10&&c.proficiencyBonus()<=20&&c.abilityModifier()>=-5&&c.abilityModifier()<=10&&c.difficultyClass()>=1&&c.difficultyClass()<=65535);if(c.mode()==CheckMode.AUTO_FAILURE)check(c.firstD20()==null&&c.secondD20()==null&&!c.success()&&c.blessingD4()==null);else {check(c.mode()==CheckMode.ROLL&&c.firstD20()!=null&&c.firstD20()>=1&&c.firstD20()<=20&&(c.secondD20()!=null)==(c.advantage()!=c.disadvantage())&&validBless(c.blessingEffectKey(),c.blessingD4()));if(c.secondD20()!=null)check(c.secondD20()>=1&&c.secondD20()<=20);int selected=c.secondD20()==null?c.firstD20():c.advantage()?Math.max(c.firstD20(),c.secondD20()):Math.min(c.firstD20(),c.secondD20());check(c.total()==selected+c.abilityModifier()+c.proficiencyBonus()+(c.blessingD4()==null?0:c.blessingD4())&&c.success()==(c.total()>=c.difficultyClass()));}}
                for(int i=0;i<n;i++) {var check=batch.checks().get(i);check(check.targetOrdinal()==i&&check.purpose().equals(batch.spellSource()==null?"BREATH_DEX":"SPELL_DEX")&&check.abilityKey().equals("ability.dexterity")&&check.difficultyClass()==batch.difficultyClass());}
                for(int i=0;i<n;i++) {var packet=batch.packets().get(i);id(packet.packetKey());check(packet.targetOrdinal()==i&&packet.saveCheckIndex()==i&&packet.sharedRollKey().equals(batch.sharedRoll().rollKey())&&packet.damageAmount()>=0&&packet.damageAmount()<=batch.diceCount()*12&&packet.beforeHitPoints()>=0&&packet.maximumHitPoints()>=1&&packet.beforeHitPoints()<=packet.maximumHitPoints()&&packet.beforeTemporaryHitPoints()>=0&&packet.absorbedTemporaryHitPoints()==Math.min(packet.beforeTemporaryHitPoints(),packet.damageAmount())&&packet.afterTemporaryHitPoints()==packet.beforeTemporaryHitPoints()-packet.absorbedTemporaryHitPoints()&&packet.afterHitPoints()==Math.max(0,packet.beforeHitPoints()-(packet.damageAmount()-packet.absorbedTemporaryHitPoints()))&&packet.damageAmount()==Schema2SaveDamageRules.damage(batch.sharedRoll().effectiveTotal(),batch.checks().get(i).success(),packet.defenses()));}
                Set<Integer> obligations=new HashSet<>();for(var o:batch.concentrationObligations())check(o.targetOrdinal()>=0&&o.targetOrdinal()<n&&obligations.add(o.targetOrdinal())&&o.holderSourceKey()!=null&&o.packetKey().equals(batch.packets().get(o.targetOrdinal()).packetKey())&&o.damageAmount()==batch.packets().get(o.targetOrdinal()).damageAmount()&&o.status()!=null&&(Set.of(ConcentrationDamageStatus.CHECK_SUCCEEDED,ConcentrationDamageStatus.CHECK_FAILED).contains(o.status())?(o.checkIndex()!=null&&o.checkIndex()>=n&&o.checkIndex()<batch.checks().size()&&o.difficultyClass()==Math.max(10,o.damageAmount()/2)):o.checkIndex()==null));
            }
        }
        for(Frame f:frames.values())if(f.kind()==FrameKind.SAVE_DAMAGE)check(owners.contains(f.frameKey()));
        Set<UUID> activeRest=new HashSet<>();for(var rest:b.monsterRests()) {id(rest.restKey());check(keys.add(rest.restKey())&&reference(monsters,rest.monsterId(),rest.status()==MonsterRestStatus.ACTIVE)&&rest.kind()!=null&&rest.startedAtSecond()>=0&&rest.completesAtSecond()==rest.startedAtSecond()+(rest.kind()==MonsterRestKind.SHORT?3600:28800)&&rest.status()!=null&&((rest.status()==MonsterRestStatus.ACTIVE)==(rest.finishedOperationId()==null)));if(rest.status()==MonsterRestStatus.ACTIVE)check(activeRest.add(rest.monsterId())&&b.status()==Status.EXPLORATION);else id(rest.finishedOperationId());}
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
