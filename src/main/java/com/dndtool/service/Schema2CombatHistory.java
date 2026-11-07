package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import java.util.*;

import static com.dndtool.service.Schema2CombatState.*;

/** Typed combat history, structural validation and capacity reservation. */
public final class Schema2CombatHistory {
    private Schema2CombatHistory() { }

    public static final int MAX_EVENTS = 128, MAX_COMBATANTS = 64, MAX_CHARACTER_MEMBERS = 16, MAX_HISTORY_CHILD_ROWS = 65536;
    public static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);
    public record Member(UUID characterId, Schema2PreparationIdentity identity, UUID preparationOperationId) { }
    public record Dependency(UUID characterId, long version) { }
    public record Event(UUID operationId, String requestDigest, Command command, List<Dependency> dependencies,
            List<UUID> characterOperations, Battle before, Resolution resolution) {
        public Event { dependencies = List.copyOf(dependencies); characterOperations = List.copyOf(characterOperations); }
        public Battle after() { return resolution.after(); }
    }
    public record History(Battle current, List<Member> members, List<Event> events) {
        public History { members = List.copyOf(members); events = List.copyOf(events); }
    }


    public static void validate(History history){
        require(history!=null&&!history.events().isEmpty()&&history.events().size()<=MAX_EVENTS&&history.members().size()>=2&&history.members().size()<=MAX_CHARACTER_MEMBERS);
        List<UUID> members=history.members().stream().map(Member::characterId).toList();require(new HashSet<>(members).size()==members.size()&&members.equals(members.stream().sorted(UUID_ORDER).toList()));
        for(Member m:history.members()){uuid(m.characterId());uuid(m.identity().runId());uuid(m.identity().snapshotId());uuid(m.preparationOperationId());}
        Set<UUID> operations=new HashSet<>(),children=new HashSet<>();Battle previous=null;long rows=0;
        for(Event e:history.events()){
            uuid(e.operationId());require(operations.add(e.operationId())&&e.requestDigest()!=null&&e.requestDigest().matches("[0-9a-f]{64}")&&e.command()!=null&&e.resolution()!=null&&Objects.equals(previous,e.before()));
            validateCommand(e.command());
            require(e.after().version()==(previous==null?0:previous.version()+1));validateState(e.after(),members);
            require(previous==null?e.command().kind()==Kind.START:e.command().kind()!=Kind.START&&previous.combatId().equals(e.after().combatId())&&previous.campaignId().equals(e.after().campaignId())&&previous.combatRule().equals(e.after().combatRule()));
            require(e.dependencies().stream().map(Dependency::characterId).toList().equals(members));
            if(e.command().castInput()!=null)require(members.containsAll(e.command().castInput().targets()));
            for(Dependency d:e.dependencies())require(d.version()>=0&&d.version()<Schema2CharacterRuntime.MAX_EVENTS);
            require(e.characterOperations().size()==e.resolution().healthChanges().size()&&e.characterOperations().size()<=MAX_COMBATANTS * 2);
            for(UUID child:e.characterOperations()){uuid(child);require(children.add(child)&&!operations.contains(child));}
            for(HealthChange change:e.resolution().healthChanges()) {
                uuid(change.frameKey()); require(members.contains(change.characterId()) && change.before()!=null && change.after()!=null
                        && change.characterId().equals(change.before().characterId()) && change.characterId().equals(change.after().characterId())
                        && change.after().version()==change.before().version()+1 && change.damageAmount()>=0 && change.damageAmount()<=65535);
                require(change.resource()!=null ? change.resource().ruleType().equals("character.resource") && change.resourceAmount()>=1
                        && change.healthResult()==null && change.damageAmount()==0 && !change.critical() && !change.deathSave() && !change.timeAdvance() && !change.healing()
                        : change.resourceAmount()==0 && change.healthResult()!=null);
                require(!change.healing() || !change.critical() && !change.deathSave() && !change.timeAdvance());
                require(!change.timeAdvance()||change.seconds()<=60||e.command().kind()==Kind.COMPLETE_MONSTER_REST);
            }
            validateCastResults(e);validateExtendedTransition(e);
            require(e.resolution().rolls().size()<=512&&e.resolution().facts().size()<=512&&e.command().path().size()<=64&&e.command().deployments().size()<=MAX_COMBATANTS&&e.command().tieBreakOrder().size()<=MAX_COMBATANTS);
            for(Roll r:e.resolution().rolls()){uuid(r.subjectId());require(token(r.purpose())&&r.sides()>=2&&r.sides()<=20&&r.value()>=1&&r.value()<=r.sides());}
            for(CombatFact f:e.resolution().facts())require(token(f.kind())&&(f.reason()==null||token(f.reason())));
            rows+=eventFactCount(e);previous=e.after();
        }
        require(previous.equals(history.current())&&Collections.disjoint(operations,children)&&rows<=MAX_HISTORY_CHILD_ROWS);
    }
    private static void validateCastResults(Event event) {
        Map<UUID,CastAttempt> retained=new HashMap<>();
        if(event.before()!=null)for(Flow flow:event.before().flows())for(CastAttempt cast:flow.casts())require(retained.put(cast.frameKey(),cast)==null);
        List<CastAttempt> created=new ArrayList<>(),completedHealing=new ArrayList<>();Set<UUID> seen=new HashSet<>();
        for(Flow flow:event.after().flows())for(CastAttempt cast:flow.casts()) {
            require(seen.add(cast.frameKey()));CastAttempt old=retained.remove(cast.frameKey());
            if(old==null)created.add(cast);
            else {
                require(old.actorId().equals(cast.actorId())&&old.castingRule().equals(cast.castingRule())&&old.spell().equals(cast.spell())&&old.targets().equals(cast.targets())
                        &&old.slotResource().equals(cast.slotResource())&&old.slotLevel()==cast.slotLevel()&&Objects.equals(old.materialItemId(),cast.materialItemId())
                        &&old.healingModifier()==cast.healingModifier()&&Objects.equals(old.targetCastFrameKey(),cast.targetCastFrameKey())&&Objects.equals(old.readyKey(),cast.readyKey())&&Objects.equals(old.coneDirection(),cast.coneDirection()));
                if(old.status()==CastStatus.RESOLVED||old.status()==CastStatus.COUNTERED||old.status()==CastStatus.INTERRUPTED)require(old.equals(cast));
                else require(cast.status()==old.status()||cast.status()==CastStatus.RESOLVED||cast.status()==CastStatus.COUNTERED||old.status()==CastStatus.PENDING&&(cast.status()==CastStatus.HELD||cast.coneDirection()!=null&&cast.status()==CastStatus.INTERRUPTED));
            }
            if(!cast.healingDice().isEmpty()&&(old==null||old.healingDice().isEmpty()))completedHealing.add(cast);
        }
        require(retained.isEmpty());
        List<HealthChange> costs=event.resolution().healthChanges().stream().filter(c->c.resource()!=null).toList();
        require(created.size()==costs.size());Set<UUID> costFrames=new HashSet<>();
        for(CastAttempt cast:created) {
            var matches=costs.stream().filter(c->c.frameKey().equals(cast.frameKey())).toList();require(matches.size()==1&&costFrames.add(cast.frameKey()));
            var cost=matches.getFirst();require(cost.characterId().equals(cast.actorId())&&cost.resource().equals(cast.slotResource())&&cost.resourceAmount()==1);
        }
        List<HealthChange> healing=event.resolution().healthChanges().stream().filter(HealthChange::healing).toList();
        if(event.command().kind()!=Kind.EXPLORATION_HEALTH)require(healing.size()==completedHealing.size());
        for(CastAttempt cast:completedHealing) {
            Set<UUID> frames=new HashSet<>(Set.of(cast.frameKey()));
            if(cast.readyKey()!=null) {
                var preparation=event.after().readyPreparations().stream().filter(r->r.readyKey().equals(cast.readyKey())&&Objects.equals(r.heldCastFrameKey(),cast.frameKey())).toList();require(preparation.size()==1);
                Set<UUID> earlier=new HashSet<>();if(event.before()!=null)for(Flow flow:event.before().flows())for(ReadyRelease release:flow.readyReleases())earlier.add(release.frameKey());
                for(Flow flow:event.after().flows())for(ReadyRelease release:flow.readyReleases())if(release.readyKey().equals(cast.readyKey())&&release.completed()&&!earlier.contains(release.frameKey()))frames.add(release.frameKey());
            }
            var matches=healing.stream().filter(c->frames.contains(c.frameKey())).toList();require(matches.size()==1&&cast.targets().size()==1);
            var result=matches.getFirst();require(result.characterId().equals(cast.targets().getFirst())&&result.damageAmount()==cast.requestedHealing()
                    &&result.healthResult().dice().equals(cast.healingDice())&&result.healthResult().rolledAmount()==cast.requestedHealing()&&result.healthResult().appliedAmount()==cast.appliedHealing());
        }
    }
    /** Reserves the complete mandatory suffix before an accepted prefix consumes any die. */
    public static void checkReservedCapacity(History previous, Battle before, int suffixEvents,
            int rosterSize, int remainingPath) {
        if (suffixEvents < 0 || rosterSize < 2 || rosterSize > MAX_COMBATANTS || remainingPath < 0 || remainingPath > 64
                || (previous == null ? 0 : previous.events().size()) + 1L + suffixEvents > MAX_EVENTS)
            throw new Schema2RuntimeFactsException("S2_COMBAT_CAPACITY");
        long retained = historyFactCount(previous);
        long stateSize = before == null ? 1L + 2L * rosterSize : stateFactCount(before);
        // The root allowance includes one ordinary attack flow. Each remaining movement
        // boundary can retain a window, all selection/attack frames, source items and dice.
        // Every suffix event saves both snapshots.
        long addedState = 64L + 100L * rosterSize * remainingPath;
        long perEvent = 2048L + 2L * (stateSize + addedState);
        if (retained + (suffixEvents + 1L) * perEvent > MAX_HISTORY_CHILD_ROWS)
            throw new Schema2RuntimeFactsException("S2_COMBAT_CAPACITY");
    }
    private static void validateState(Battle b,List<UUID> characterMembers){
        List<UUID> members=validateMonsterState(b,characterMembers);
        uuid(b.combatId());uuid(b.campaignId());require(b.combatRule()!=null&&b.combatRule().ruleType().equals("adventure.combat")&&b.status()!=null&&b.round()>=1&&b.turnNumber()>=1&&b.activeOrdinal()>=0&&b.activeOrdinal()<members.size()&&b.version()>=0);
        require(b.combatants().stream().map(Combatant::characterId).toList().equals(members)&&b.initiative().size()==members.size());
        Set<UUID> initiative=new HashSet<>();for(int i=0;i<b.initiative().size();i++){Initiative n=b.initiative().get(i);require(initiative.add(n.characterId())&&members.contains(n.characterId())&&n.ordinal()==i&&n.d20()>=1&&n.d20()<=20&&n.dexterityModifier()>=-5&&n.dexterityModifier()<=10&&n.total()==n.d20()+n.dexterityModifier());}
        for(Combatant c:b.combatants()){require(c.position()!=null&&c.side()>=0&&c.side()<=15&&c.movementSpentHalfFeet()>=0&&c.movementBonus()>=0);if(c.sequence()!=null){AttackSequence s=c.sequence();uuid(s.sequenceKey());require(s.limit()>=1&&s.limit()<=20&&s.performed()>=0&&s.forfeited()>=0&&s.performed()+s.forfeited()<=s.limit()&&s.closed()==(s.performed()+s.forfeited()==s.limit()));}}
        require(b.ongoingEffects().size()<=128);Set<UUID> effectKeys=new HashSet<>();
        for(OngoingEffect effect:b.ongoingEffects()) {
            uuid(effect.effectKey()); require(effectKeys.add(effect.effectKey()) && ruleType(effect.spell(),"adventure.spell") && members.contains(effect.casterId())
                    && !effect.targets().isEmpty() && effect.targets().size()<=MAX_COMBATANTS && new HashSet<>(effect.targets()).size()==effect.targets().size()
                    && members.containsAll(effect.targets()) && effect.startedAtSecond()>=0 && effect.expiresAtSecond()>effect.startedAtSecond() && effect.expiresAtSecond()<=60000000059L
                    && effect.status()!=null && (effect.status()==EffectStatus.ACTIVE ? effect.endReason()==null : token(effect.endReason())));
        }
        require(b.flows().size()<=128&&b.flows().stream().filter(f->f.status()==FlowStatus.ACTIVE).count()<=1);Set<UUID> keys=new HashSet<>();
        for(Flow f:b.flows()){
            uuid(f.flowKey());require(keys.add(f.flowKey())&&f.frames().size()>=1&&f.frames().size()<=1024&&f.windows().size()<=1024&&f.attacks().size()<=1024&&f.casts().size()<=1024&&f.savingThrows().size()<=1024);
            Map<UUID,Frame> frames=new HashMap<>();for(Frame r:f.frames()){uuid(r.frameKey());uuid(r.ownerKey());require(keys.add(r.frameKey())&&frames.put(r.frameKey(),r)==null&&r.kind()!=null&&r.status()!=null&&r.invocation()!=null&&r.stage()!=null);}
            Frame root=frames.get(f.rootFrameKey());require(root!=null&&root.parentFrameKey()==null);require(f.status()==FlowStatus.ACTIVE?frames.containsKey(f.leafFrameKey()):f.leafFrameKey()==null);
            for(Frame r:f.frames()){Set<UUID> chain=new HashSet<>();Frame cursor=r;while(cursor.parentFrameKey()!=null){require(chain.add(cursor.frameKey())&&chain.size()<=32);cursor=frames.get(cursor.parentFrameKey());require(cursor!=null);}require(cursor.frameKey().equals(f.rootFrameKey()));}
            Set<UUID> windows=new HashSet<>();for(Window w:f.windows()){uuid(w.windowKey());require(keys.add(w.windowKey())&&windows.add(w.windowKey())&&frames.containsKey(w.ownerFrameKey())&&w.checkpointOrdinal()>=0&&w.checkpointOrdinal()<64&&w.boundaryIndex()>=0&&w.boundaryIndex()<64&&w.opportunities().size()<=128);Set<UUID>actors=new HashSet<>();
                for(int i=0;i<w.opportunities().size();i++){Opportunity o=w.opportunities().get(i);uuid(o.opportunityKey());require(keys.add(o.opportunityKey())&&actors.add(o.actorId())&&members.contains(o.actorId())&&o.priority()==i&&o.options().size()>=1&&o.options().size()<=32&&new HashSet<>(o.options()).size()==o.options().size()&&(o.selectionFrameKey()==null?o.status()==OpportunityStatus.PENDING:frames.containsKey(o.selectionFrameKey()))&&(o.attackFrameKey()==null||frames.containsKey(o.attackFrameKey()))&&(o.reason()==null||token(o.reason())));for(ReactionOption option:o.options())validateOption(option);}}
            for(Movement m:f.movements()){uuid(m.movementKey());require(members.contains(m.actorId())&&m.path().size()>=1&&m.path().size()<=64&&m.cursor()>=0&&m.cursor()<=m.path().size()&&m.spent()>=0&&m.status()!=null&&(m.stopReason()==null||token(m.stopReason())));}
            for(AttackAttempt t:f.attacks())require(frames.containsKey(t.frameKey())&&members.contains(t.actorId())&&members.contains(t.targetId())&&!t.actorId().equals(t.targetId())&&t.firstD20()>=1&&t.firstD20()<=20&&(t.secondD20()==null||t.secondD20()>=1&&t.secondD20()<=20)&&t.selectedD20()>=1&&t.selectedD20()<=20&&t.damageDice().size()<=40&&t.damageDice().stream().allMatch(v->v!=null&&v>=1&&v<=12)&&t.damageAmount()>=0&&validBlessing(t.blessingEffectKey(),t.blessingD4()));
            Set<UUID> castFrames=new HashSet<>();
            for(CastAttempt cast:f.casts()) {
                require(frames.containsKey(cast.frameKey()) && frames.get(cast.frameKey()).kind()==FrameKind.CAST && castFrames.add(cast.frameKey()) && members.contains(cast.actorId())
                        && castingType(cast.castingRule()) && ruleType(cast.spell(),"adventure.spell") && ruleType(cast.slotResource(),"character.resource")
                        && cast.slotLevel()>=1 && cast.slotLevel()<=9 && (cast.coneDirection()==null?!cast.targets().isEmpty():cast.targets().isEmpty()&&cast.coneDirection()>=0&&cast.coneDirection()<8&&cast.readyKey()==null) && cast.targets().size()<=MAX_COMBATANTS
                        && new HashSet<>(cast.targets()).size()==cast.targets().size() && members.containsAll(cast.targets())
                        && cast.healingDice().size()<=20 && cast.healingDice().stream().allMatch(d->d!=null&&d>=1&&d<=12)
                        && cast.status()!=null && cast.healingModifier()>=-5 && cast.healingModifier()<=10 && cast.requestedHealing()>=0 && cast.requestedHealing()<=65535
                        && cast.appliedHealing()>=0 && cast.appliedHealing()<=cast.requestedHealing()
                        && (cast.healingDice().isEmpty() ? cast.requestedHealing()==0 && cast.appliedHealing()==0
                            : cast.effectKey()==null && cast.requestedHealing()==Math.max(0,cast.healingDice().stream().mapToInt(Integer::intValue).sum()+cast.healingModifier())));
                if(cast.materialItemId()!=null)uuid(cast.materialItemId());if(cast.effectKey()!=null)uuid(cast.effectKey());
            }
            for(SavingThrowAttempt save:f.savingThrows()) {
                require(frames.containsKey(save.frameKey()) && members.contains(save.subjectId()) && token(save.kind())
                        && (save.abilityKey()==null || Schema2CharacterRuntime.ABILITIES.contains(save.abilityKey()))
                        && save.d20()>=1 && save.d20()<=20 && save.abilityModifier()>=-5 && save.abilityModifier()<=10
                        && save.proficiencyBonus()>=0 && save.proficiencyBonus()<=6 && validBlessing(save.blessingEffectKey(),save.blessingD4())
                        && save.difficultyClass()>=0 && save.difficultyClass()<=32767
                        && save.total()==save.d20()+save.abilityModifier()+save.proficiencyBonus()+(save.blessingD4()==null?0:save.blessingD4())
                        && save.success()==(save.total()>=save.difficultyClass()));
            }
        }
        validateExtendedState(b,members);
    }
    private static void validateExtendedTransition(Event event) {
        Battle before=event.before(),after=event.after();Map<UUID,EnvironmentZone> zones=new HashMap<>();Map<UUID,SuppressionCause> causes=new HashMap<>();
        if(before!=null) {for(var z:before.environmentZones())zones.put(z.zoneKey(),z);for(var c:before.suppressionCauses())causes.put(c.causeKey(),c);}
        for(var z:after.environmentZones()) {var old=zones.remove(z.zoneKey());
            if(old==null)require(z.createdOperationId().equals(event.operationId())&&z.endedOperationId()==null);
            else {require(old.rule().equals(z.rule())&&old.center().equals(z.center())&&old.createdOperationId().equals(z.createdOperationId()));require(old.endedOperationId()!=null?old.equals(z):z.endedOperationId()==null||z.endedOperationId().equals(event.operationId()));}}
        require(zones.isEmpty());
        for(var c:after.suppressionCauses()) {var old=causes.remove(c.causeKey());
            if(old==null)require(c.establishedOperationId().equals(event.operationId())&&(c.releasedOperationId()==null||c.releasedOperationId().equals(event.operationId())));
            else {require(old.zoneKey().equals(c.zoneKey())&&old.effectKey().equals(c.effectKey())&&old.targetId().equals(c.targetId())&&old.establishedOperationId().equals(c.establishedOperationId()));require(old.releasedOperationId()!=null?old.equals(c):c.releasedOperationId()==null||c.releasedOperationId().equals(event.operationId()));}}
        require(causes.isEmpty());
        if(before!=null) {
            require(after.triggerFacts().size()>=before.triggerFacts().size()&&after.triggerFacts().subList(0,before.triggerFacts().size()).equals(before.triggerFacts()));
            require(after.ongoingEffects().size()>=before.ongoingEffects().size());
            for(int i=0;i<before.ongoingEffects().size();i++) {var old=before.ongoingEffects().get(i);var next=after.ongoingEffects().get(i);
                require(old.effectKey().equals(next.effectKey())&&old.spell().equals(next.spell())&&old.casterId().equals(next.casterId())&&old.targets().equals(next.targets())&&old.startedAtSecond()==next.startedAtSecond()&&old.expiresAtSecond()==next.expiresAtSecond());
                if(old.status()==EffectStatus.ENDED)require(old.equals(next));}
        }
    }
    private static void validateExtendedState(Battle b,List<UUID> members) {
        require(b.readyPreparations().size()<=128&&b.triggerFacts().size()<=1024&&b.shields().size()<=128&&b.concentrationHolders().size()<=members.size()&&b.environmentZones().size()<=16&&b.suppressionCauses().size()<=512);
        Map<UUID,ReadyPreparation> readies=new HashMap<>();Map<UUID,TriggerFact> triggers=new HashMap<>();Map<UUID,EnvironmentZone> zones=new HashMap<>();
        Map<UUID,Frame> frames=new HashMap<>();for(var f:b.flows())for(var r:f.frames())frames.put(r.frameKey(),r);
        for(var r:b.readyPreparations()) {uuid(r.readyKey());require(readies.put(r.readyKey(),r)==null&&members.contains(r.actorId())&&r.declaredTurnNumber()>=1&&r.expiresAtTurnNumber()>r.declaredTurnNumber()&&r.status()!=null);validateReadyInput(r.input());if(r.heldCastFrameKey()!=null)require(frames.containsKey(r.heldCastFrameKey()));}
        for(var t:b.triggerFacts()) {uuid(t.triggerKey());require(triggers.put(t.triggerKey(),t)==null&&t.kind()!=null&&frames.containsKey(t.sourceFrameKey())&&members.contains(t.subjectId())&&(t.targetId()==null||members.contains(t.targetId()))&&t.ordinal()>=0&&t.ordinal()<1024);}
        Set<UUID> effects=new HashSet<>();for(var e:b.ongoingEffects())effects.add(e.effectKey());
        for(var sh:b.shields()) {uuid(sh.effectKey());require(effects.add(sh.effectKey())&&frames.containsKey(sh.castFrameKey())&&members.contains(sh.actorId())&&sh.startedTurnNumber()>=1&&sh.expiresAtTurnNumber()>sh.startedTurnNumber()&&sh.status()!=null&&(sh.status()==EffectStatus.ACTIVE?sh.endReason()==null:token(sh.endReason())));}
        Set<UUID> holders=new HashSet<>();
        for(var h:b.concentrationHolders()) {uuid(h.sourceKey());require(holders.add(h.actorId())&&members.contains(h.actorId())&&h.kind()!=null);
            if(h.kind()==ConcentrationKind.BLESS_EFFECT)require(b.ongoingEffects().stream().anyMatch(e->e.effectKey().equals(h.sourceKey())&&e.casterId().equals(h.actorId())&&e.status()==EffectStatus.ACTIVE));
            else {var r=readies.get(h.sourceKey());require(r!=null&&r.actorId().equals(h.actorId())&&r.input().responseKind()==ReadyResponseKind.SPELL&&(r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING));}}
        for(var e:b.ongoingEffects())if(e.status()==EffectStatus.ACTIVE)require(b.concentrationHolders().stream().anyMatch(h->h.kind()==ConcentrationKind.BLESS_EFFECT&&h.actorId().equals(e.casterId())&&h.sourceKey().equals(e.effectKey())));
        for(var z:b.environmentZones()) {uuid(z.zoneKey());uuid(z.createdOperationId());if(z.endedOperationId()!=null)uuid(z.endedOperationId());require(zones.put(z.zoneKey(),z)==null&&ruleType(z.rule(),"adventure.environment")&&z.center()!=null);}
        Set<UUID> causes=new HashSet<>();Set<String> live=new HashSet<>();
        for(var c:b.suppressionCauses()) {uuid(c.causeKey());uuid(c.establishedOperationId());if(c.releasedOperationId()!=null)uuid(c.releasedOperationId());require(causes.add(c.causeKey())&&zones.containsKey(c.zoneKey())&&effects.contains(c.effectKey())&&members.contains(c.targetId())&&(c.releasedOperationId()==null)==(c.releaseReason()==null));if(c.releasedOperationId()==null)require(live.add(c.zoneKey()+":"+c.effectKey()+":"+c.targetId()));}
        try {
            Schema2OngoingEffectRules.validateGraph(b);
        } catch (IllegalArgumentException malformed) {
            throw corrupt();
        }
        for(var f:b.flows()) {
            for(var cast:f.casts()) {if(cast.targetCastFrameKey()!=null)require(frames.containsKey(cast.targetCastFrameKey())&&frames.get(cast.targetCastFrameKey()).kind()==FrameKind.CAST);if(cast.readyKey()!=null)require(readies.containsKey(cast.readyKey()));}
            require(f.movements().size()<=1024&&f.readyReleases().size()<=1024&&f.abilityChecks().size()<=1024);
            for(var w:f.windows())if(w.trigger()!=null)require(w.trigger().equals(triggers.get(w.trigger().triggerKey())));
            for(var r:f.readyReleases())require(frames.containsKey(r.frameKey())&&readies.containsKey(r.readyKey())&&triggers.containsKey(r.triggerKey())&&(r.completed()?token(r.outcome()):r.outcome()==null));
            for(var c:f.abilityChecks())require(frames.containsKey(c.frameKey())&&members.contains(c.subjectId())&&token(c.kind())&&Schema2CharacterRuntime.ABILITIES.contains(c.abilityKey())&&c.d20()>=1&&c.d20()<=20&&c.proficiencyBonus()>=0&&c.proficiencyBonus()<=6&&c.difficultyClass()>=0&&c.difficultyClass()<=32767&&c.total()==c.d20()+c.abilityModifier()+c.proficiencyBonus()&&c.success()==(c.total()>=c.difficultyClass()));
        }
        if(b.status()==Status.ENDED)require(holders.isEmpty()&&b.activeFlow()==null&&b.ongoingEffects().stream().allMatch(e->e.status()==EffectStatus.ENDED)&&b.shields().stream().allMatch(e->e.status()==EffectStatus.ENDED)&&b.readyPreparations().stream().noneMatch(r->r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING)&&b.environmentZones().stream().allMatch(z->z.endedOperationId()!=null)&&b.suppressionCauses().stream().allMatch(c->c.releasedOperationId()!=null));
    }
    private static void validateCommand(Command c) {
        require(c.kind()!=null);boolean start=c.kind()==Kind.START,reenter=c.kind()==Kind.REENTER_COMBAT;
        boolean move=c.kind()==Kind.MOVE||c.kind()==Kind.EXPLORATION_MOVE,attack=c.kind()==Kind.ATTACK,health=c.kind()==Kind.EXPLORATION_HEALTH;
        boolean reaction=Set.of(Kind.DECLARE_OPPORTUNITY_ATTACK,Kind.DECLARE_SHIELD,Kind.DECLARE_COUNTERSPELL,Kind.RELEASE_READY,Kind.PASS_REACTION,Kind.ADVANCE_WINDOW).contains(c.kind());
        boolean continuation=c.kind()==Kind.CONTINUE_MOVEMENT||c.kind()==Kind.CONTINUE_FLOW;
        boolean ready=Set.of(Kind.READY_ATTACK,Kind.READY_MOVE,Kind.READY_SPELL).contains(c.kind());
        boolean cast=c.kind()==Kind.CAST||c.kind()==Kind.DECLARE_SHIELD||c.kind()==Kind.DECLARE_COUNTERSPELL;
        require(start?c.actorId()==null:c.actorId()!=null);
        require((c.targetId()!=null)==(attack||health)&&(c.itemId()!=null)==(attack||c.kind()==Kind.DECLARE_OPPORTUNITY_ATTACK)
                &&(c.castInput()!=null)==cast&&(c.readyInput()!=null)==ready&&(c.healthInput()!=null)==health
                &&(c.zoneInput()!=null)==(c.kind()==Kind.CREATE_ENVIRONMENT_ZONE)&&(c.zoneKey()!=null)==(c.kind()==Kind.END_ENVIRONMENT_ZONE)
                &&(c.readyKey()!=null)==(c.kind()==Kind.RELEASE_READY)
                &&(move?!c.path().isEmpty()&&c.path().size()<=64:c.path().isEmpty())
                &&(start?c.deployments().size()>=2&&c.deployments().size()<=MAX_CHARACTER_MEMBERS&&c.deployments().size()+c.monsterDeployments().size()<=MAX_COMBATANTS:c.deployments().isEmpty()&&c.monsterDeployments().isEmpty())
                &&(start||reenter||c.tieBreakOrder().isEmpty())&&(c.flowKey()!=null)==(reaction||continuation)
                &&(c.leafKey()!=null)==(reaction||continuation)&&(reaction?c.windowKey()!=null:continuation?c.kind()==Kind.CONTINUE_FLOW||c.windowKey()!=null:c.windowKey()==null)&&(c.opportunityKey()!=null)==reaction
                &&(c.kind()==Kind.EXPLORATION_ADVANCE_TIME?c.seconds()>=1&&c.seconds()<=60:c.seconds()==0));
        for(UUID id:Arrays.asList(c.actorId(),c.targetId(),c.itemId(),c.flowKey(),c.leafKey(),c.windowKey(),c.opportunityKey(),c.readyKey(),c.zoneKey()))if(id!=null)uuid(id);
        require((c.breathInput()!=null)==(c.kind()==Kind.MONSTER_BREATH));
        require((c.monsterRestInput()!=null)==(c.kind()==Kind.BEGIN_MONSTER_REST||c.kind()==Kind.COMPLETE_MONSTER_REST));
        if(c.castInput()!=null)validateCastInput(c.castInput());if(c.readyInput()!=null)validateReadyInput(c.readyInput());
        if(c.zoneInput()!=null)require(ruleType(c.zoneInput().rule(),"adventure.environment")&&c.zoneInput().center()!=null);
    }
    private static void validateCastInput(CastInput input) {
        require(castingType(input.castingRule())&&ruleType(input.spell(),"adventure.spell")&&input.slotLevel()>=1&&input.slotLevel()<=9
                &&(input.coneDirection()==null?!input.targets().isEmpty():input.targets().isEmpty()&&input.coneDirection()>=0&&input.coneDirection()<8)&&input.targets().size()<=MAX_COMBATANTS&&new HashSet<>(input.targets()).size()==input.targets().size());
        for(UUID target:input.targets())uuid(target);if(input.materialItemId()!=null)uuid(input.materialItemId());if(input.targetCastFrameKey()!=null)uuid(input.targetCastFrameKey());
    }
    private static boolean castingType(RuleIdentity rule) {return ruleType(rule,"character.spellcasting")||ruleType(rule,"character.wizard_spellcasting");}
    private static void validateReadyInput(ReadyInput input) {
        require(input.trigger()!=null&&input.trigger().kind()!=null&&input.responseKind()!=null&&input.path().size()<=64);
        if(input.trigger().subjectId()!=null)uuid(input.trigger().subjectId());if(input.targetId()!=null)uuid(input.targetId());if(input.itemId()!=null)uuid(input.itemId());
        require((input.spell()!=null)==(input.responseKind()==ReadyResponseKind.SPELL));if(input.spell()!=null) {validateCastInput(input.spell());require(input.spell().coneDirection()==null);}
    }
    private static void validateOption(ReactionOption option) {
        require(option!=null&&option.kind()!=null);
        switch(option.kind()) {
            case OPPORTUNITY_ATTACK -> {uuid(option.itemId());require(option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0&&option.readyKey()==null);}
            case SHIELD,COUNTERSPELL -> {require(option.itemId()==null&&castingType(option.castingRule())&&ruleType(option.spell(),"adventure.spell")&&option.slotLevel()>=1&&option.slotLevel()<=9&&option.readyKey()==null);}
            case READY_RELEASE -> {uuid(option.readyKey());require(option.itemId()==null&&option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0);}
        }
    }
    private static boolean validBlessing(UUID effect,Integer die) {
        if(effect==null)return die==null;uuid(effect);return die!=null&&die>=1&&die<=4;
    }
    private static boolean ruleType(RuleIdentity rule,String type) {
        String prefix=switch(type) {case "character.spellcasting","character.wizard_spellcasting"->"spellcasting";case "adventure.environment"->"environment";case "adventure.spell"->"spell";case "character.resource"->"resource";default->"";};
        return rule!=null && type.equals(rule.ruleType()) && rule.moduleKey()!=null && rule.moduleKey().matches("[a-z][a-z0-9_]{0,63}")
                && rule.ruleKey()!=null && rule.ruleKey().length()<=128 && rule.ruleKey().matches(prefix+"[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*");
    }

    private static List<UUID> validateMonsterState(Battle state,List<UUID> characters) {
        Set<UUID> ids=new HashSet<>(characters);require(state.monsters().size()+characters.size()<=64);
        for(var m:state.monsters()) {
            require(m.monsterId()!=null&&m.monsterId().version()==4&&m.monsterId().variant()==2&&ids.add(m.monsterId())
                    &&validRule(m.monster(),"adventure.monster")&&m.hitPoints()>=0&&m.hitPoints()<=65535&&m.dead()==(m.hitPoints()==0)&&m.actionUses().size()<=16);
            Set<EntryIdentity> actions=new HashSet<>();for(var a:m.actionUses())require(validAction(a.action())&&a.action().rule().equals(m.monster())&&actions.add(a.action()));
        }
        require(state.monsterRests().size()<=128);Set<UUID> rests=new HashSet<>(),resting=new HashSet<>();
        for(var r:state.monsterRests()) {
            require(r.restKey()!=null&&r.restKey().version()==4&&rests.add(r.restKey())&&state.monsters().stream().anyMatch(m->m.monsterId().equals(r.monsterId()))
                    &&r.kind()!=null&&r.status()!=null&&r.startedAtSecond()>=0&&r.completesAtSecond()-r.startedAtSecond()==(r.kind()==MonsterRestKind.SHORT?3600:28800)
                    &&(r.status()==MonsterRestStatus.ACTIVE?r.finishedOperationId()==null&&resting.add(r.monsterId()):r.finishedOperationId()!=null));
        }
        Set<UUID> batchKeys=new HashSet<>(),rollKeys=new HashSet<>(),packetKeys=new HashSet<>();require(state.saveDamageBatches().size()<=128);
        for(var batch:state.saveDamageBatches()) {
            require(batch.batchKey()!=null&&batch.batchKey().version()==4&&batchKeys.add(batch.batchKey())&&batch.releasedTurnNumber()>=1
                    &&batch.releaseOperationId()!=null&&batch.releaseOperationId().version()==4&&batch.status()!=null);
            if(batch.spellSource()==null)require(validAction(batch.action())&&state.monsters().stream().anyMatch(m->m.monsterId().equals(batch.sourceId())&&m.monster().equals(batch.action().rule())));
            else {
                var source=batch.spellSource();require(batch.action()==null&&validRule(source.spell(),"adventure.spell")&&source.slotLevel()>=1&&source.slotLevel()<=3&&source.abilityModifier()>=-5&&source.abilityModifier()<=10&&source.proficiencyBonus()>=2&&source.proficiencyBonus()<=3);
                var casts=state.flows().stream().flatMap(f->f.casts().stream()).filter(c->c.frameKey().equals(source.castFrameKey())).toList();require(casts.size()==1);
                var cast=casts.getFirst();require(cast.actorId().equals(batch.sourceId())&&characters.contains(batch.sourceId())&&cast.spell().equals(source.spell())&&cast.slotLevel()==source.slotLevel()
                        &&Objects.equals(cast.coneDirection(),batch.targetProof().direction()));
            }
            List<Frame> frames=state.flows().stream().flatMap(f->f.frames().stream()).filter(f->f.frameKey().equals(batch.frameKey())).toList();
            require(frames.size()==1&&frames.getFirst().kind()==FrameKind.SAVE_DAMAGE&&frames.getFirst().ownerKey().equals(batch.batchKey()));
            var proof=batch.targetProof();require(proof!=null&&proof.policy()!=null&&proof.policy().matches("[A-Z][A-Z0-9_]{0,63}")&&proof.rosterVersion()>=0&&proof.rosterVersion()<=state.version()
                    &&proof.origin()!=null&&proof.direction()>=0&&proof.direction()<8&&proof.candidates().size()==ids.size()&&proof.targets().size()<=64);
            Set<CreatureRef> candidates=new HashSet<>();List<CreatureRef> included=new ArrayList<>();
            for(var c:proof.candidates()) {
                require(c.creature()!=null&&candidates.add(c.creature())&&ids.contains(c.creature().creatureId())&&c.position()!=null&&c.reason()!=null&&c.reason().matches("[A-Z][A-Z0-9_]{0,63}")
                        &&(characters.contains(c.creature().creatureId())?c.creature().kind()==CreatureKind.CHARACTER:c.creature().kind()==CreatureKind.MONSTER));
                if(c.included())included.add(c.creature());
            }
            included.sort(Comparator.comparing((CreatureRef c)->c.kind().name()).thenComparing(c->c.creatureId().toString()));
            require(included.equals(proof.targets()));
            if(batch.status()==BatchStatus.NO_TARGETS) require(proof.targets().isEmpty()&&batch.sharedRoll()==null&&batch.checks().isEmpty()&&batch.packets().isEmpty()&&batch.concentrationObligations().isEmpty());
            else {
                var roll=batch.sharedRoll();require(!proof.targets().isEmpty()&&roll!=null&&roll.rollKey()!=null&&rollKeys.add(roll.rollKey())&&roll.dice().size()==batch.diceCount()
                        &&roll.dice().stream().allMatch(v->v!=null&&v>=1&&v<=6)&&roll.rawTotal()==roll.dice().stream().mapToInt(Integer::intValue).sum()&&roll.effectiveTotal()==roll.rawTotal());
                if(batch.status()==BatchStatus.ROLLED)require(batch.checks().isEmpty()&&batch.packets().isEmpty()&&batch.concentrationObligations().isEmpty());
                else validateCompleted(batch,packetKeys);
            }
        }
        List<UUID> ordered=new ArrayList<>(characters);ordered.addAll(state.monsters().stream().map(MonsterInstance::monsterId).sorted(UUID_ORDER).toList());return List.copyOf(ordered);
    }

    private static void validateCompleted(SaveDamageBatch batch,Set<UUID> packetKeys) {
        var targets=batch.targetProof().targets();require(batch.packets().size()==targets.size()&&batch.checks().size()>=targets.size()&&batch.checks().size()<=2*targets.size());
        for(var c:batch.checks()) {
            require(c.targetOrdinal()>=0&&c.targetOrdinal()<targets.size()&&Set.of(batch.spellSource()==null?"BREATH_DEX":"SPELL_DEX","CONCENTRATION_CON").contains(c.purpose())
                    &&c.abilityKey().equals(c.purpose().equals("CONCENTRATION_CON")?"ability.constitution":"ability.dexterity")&&c.mode()!=null
                    &&c.abilityModifier()>=-5&&c.abilityModifier()<=10&&c.proficiencyBonus()>=0&&c.proficiencyBonus()<=6&&c.difficultyClass()>=0&&c.difficultyClass()<=32767
                    &&(c.blessingEffectKey()==null?c.blessingD4()==null:c.blessingD4()!=null&&c.blessingD4()>=1&&c.blessingD4()<=4));
            if(c.mode()==CheckMode.AUTO_FAILURE)require(c.firstD20()==null&&c.secondD20()==null&&!c.success());
            else {
                require(c.firstD20()!=null&&c.firstD20()>=1&&c.firstD20()<=20&&((c.advantage()!=c.disadvantage())==(c.secondD20()!=null)));
                if(c.secondD20()!=null)require(c.secondD20()>=1&&c.secondD20()<=20);
                int selected=c.secondD20()==null?c.firstD20():c.advantage()?Math.max(c.firstD20(),c.secondD20()):Math.min(c.firstD20(),c.secondD20());
                require(c.total()==selected+c.abilityModifier()+c.proficiencyBonus()+(c.blessingD4()==null?0:c.blessingD4())&&c.success()==(c.total()>=c.difficultyClass()));
            }
        }
        for(int i=0;i<targets.size();i++) {
            var p=batch.packets().get(i);require(p.targetOrdinal()==i&&p.packetKey()!=null&&packetKeys.add(p.packetKey())&&p.sharedRollKey().equals(batch.sharedRoll().rollKey())
                    &&p.saveCheckIndex()>=0&&p.saveCheckIndex()<batch.checks().size());
            var save=batch.checks().get(p.saveCheckIndex());require(save.purpose().equals(batch.spellSource()==null?"BREATH_DEX":"SPELL_DEX")&&save.targetOrdinal()==i&&save.difficultyClass()==batch.difficultyClass());
            require(p.maximumHitPoints()>=1&&p.maximumHitPoints()<=65535&&p.beforeHitPoints()>=0&&p.beforeHitPoints()<=p.maximumHitPoints()&&p.beforeTemporaryHitPoints()>=0&&p.beforeTemporaryHitPoints()<=65535&&p.defenses().size()<=32);
            Set<String> kinds=new HashSet<>();for(var d:p.defenses()){require(d.source()!=null&&Set.of("IMMUNITY","RESISTANCE","VULNERABILITY").contains(d.kind()));kinds.add(d.kind());}
            int damage=save.success()?batch.sharedRoll().effectiveTotal()/2:batch.sharedRoll().effectiveTotal();
            if(kinds.contains("IMMUNITY"))damage=0;else {if(kinds.contains("RESISTANCE"))damage/=2;if(kinds.contains("VULNERABILITY"))damage*=2;}
            require(p.damageAmount()==damage&&p.absorbedTemporaryHitPoints()==Math.min(p.beforeTemporaryHitPoints(),damage)
                    &&p.afterTemporaryHitPoints()==p.beforeTemporaryHitPoints()-p.absorbedTemporaryHitPoints()
                    &&p.afterHitPoints()==Math.max(0,p.beforeHitPoints()-(damage-p.absorbedTemporaryHitPoints()))&&(!p.dead()||p.afterHitPoints()==0));
            if(targets.get(i).kind()==CreatureKind.MONSTER)require(p.dead()==(p.afterHitPoints()==0));
        }
        Set<Integer> obligations=new HashSet<>();
        for(var o:batch.concentrationObligations()) {
            require(o.targetOrdinal()>=0&&o.targetOrdinal()<targets.size()&&obligations.add(o.targetOrdinal())&&o.status()!=null);
            var packet=batch.packets().get(o.targetOrdinal());require(o.packetKey().equals(packet.packetKey())&&o.damageAmount()==packet.damageAmount());
            boolean checked=o.status()==ConcentrationDamageStatus.CHECK_SUCCEEDED||o.status()==ConcentrationDamageStatus.CHECK_FAILED;
            require(checked==(o.checkIndex()!=null));
            if(checked) {require(o.holderSourceKey()!=null&&o.damageAmount()>0&&o.difficultyClass()==Math.max(10,o.damageAmount()/2)&&o.checkIndex()>=0&&o.checkIndex()<batch.checks().size());var c=batch.checks().get(o.checkIndex());require(c.purpose().equals("CONCENTRATION_CON")&&c.targetOrdinal()==o.targetOrdinal()&&c.difficultyClass()==o.difficultyClass()&&c.success()==(o.status()==ConcentrationDamageStatus.CHECK_SUCCEEDED));}
        }
    }
    private static boolean validRule(RuleIdentity r,String type) {return r!=null&&type.equals(r.ruleType())&&r.moduleKey()!=null&&r.ruleKey()!=null&&r.moduleKey().matches("[a-z][a-z0-9_]{0,63}")&&r.ruleKey().matches("[a-z][a-z0-9_.]{0,127}");}
    private static boolean validAction(EntryIdentity e) {return e!=null&&validRule(e.rule(),"adventure.monster")&&"actions".equals(e.collectionKey())&&e.moduleKey()!=null&&e.entryKey()!=null&&e.moduleKey().matches("[a-z][a-z0-9_]{0,63}")&&e.entryKey().matches("[a-z][a-z0-9_]{0,63}");}


    private static long historyFactCount(History history) {
        return history == null ? 0 : history.events().stream().mapToLong(Schema2CombatHistory::eventFactCount).sum();
    }

    /** Counts the event and all facts retained with it, including both state snapshots. */
    public static long eventFactCount(Event event) {
        Command command = event.command();
        long count = 1L + command.path().size() + command.deployments().size() + command.tieBreakOrder().size()
                + command.monsterDeployments().size() + event.dependencies().size() + event.characterOperations().size()
                + event.resolution().rolls().size() + event.resolution().facts().size();
        if (command.castInput() != null) count += 1L + command.castInput().targets().size();
        if (command.readyInput() != null) count += readyInputFactCount(command.readyInput());
        if (command.zoneInput() != null) count++;
        if (command.healthInput() != null) count++;
        if (command.breathInput() != null) count++;
        if (command.monsterRestInput() != null) count++;
        if (event.before() != null) count += stateFactCount(event.before());
        return count + stateFactCount(event.after());
    }

    private static long stateFactCount(Battle state) {
        long count = 1L + state.combatants().size() + state.initiative().size() + state.triggerFacts().size()
                + state.shields().size() + state.concentrationHolders().size() + state.environmentZones().size()
                + state.suppressionCauses().size() + state.monsterRests().size();
        for (OngoingEffect effect : state.ongoingEffects()) count += 1L + effect.targets().size();
        for (ReadyPreparation ready : state.readyPreparations()) count += 1L + readyInputFactCount(ready.input());
        for (Flow flow : state.flows()) {
            count += 1L + flow.frames().size() + flow.readyReleases().size() + flow.abilityChecks().size() + flow.savingThrows().size();
            for (Window window : flow.windows()) {
                count += window.trigger() == null ? 1 : 2;
                for (Opportunity opportunity : window.opportunities()) count += 1L + opportunity.options().size();
            }
            for (Movement movement : flow.movements()) count += 1L + movement.path().size();
            for (AttackAttempt attack : flow.attacks()) count += 1L + attack.damageDice().size();
            for (CastAttempt cast : flow.casts()) count += 1L + cast.targets().size() + cast.healingDice().size();
        }
        for (MonsterInstance monster : state.monsters()) count += 1L + monster.actionUses().size();
        for (SaveDamageBatch batch : state.saveDamageBatches()) {
            count += 1L + batch.targetProof().candidates().size() + batch.targetProof().targets().size()
                    + batch.checks().size() + batch.concentrationObligations().size();
            if (batch.sharedRoll() != null) count += batch.sharedRoll().dice().size();
            if (batch.spellSource() != null) count++;
            for (DamagePacket packet : batch.packets()) count += 1L + packet.defenses().size();
        }
        return count;
    }

    private static long readyInputFactCount(ReadyInput input) {
        return 1L + input.path().size() + (input.spell() == null ? 0 : input.spell().targets().size());
    }

    private static boolean token(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,127}");
    }

    private static void uuid(UUID id) {
        require(id != null && id.version() == 4 && id.variant() == 2);
    }

    private static void require(boolean valid) {
        if (!valid) throw corrupt();
    }

    private static Schema2RuntimeFactsException corrupt() {
        return new Schema2RuntimeFactsException("S2_COMBAT_RUNTIME_CORRUPT");
    }
}
