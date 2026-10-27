package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthResult;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import java.util.*;

/** Typed INTERNAL combat facts. Character health and inventory retain their existing sole owner. */
public final class Schema2CombatState {
    private Schema2CombatState() { }
    public enum Status { ACTIVE, EXPLORATION, ENDED }
    public enum Kind { START, END_TURN, DASH, DISENGAGE, DODGE, STAND_UP, BEGIN_ATTACK, ATTACK, FORFEIT_ATTACKS,
        MOVE, PASS_REACTION, DECLARE_OPPORTUNITY_ATTACK, DECLARE_SHIELD, DECLARE_COUNTERSPELL, RELEASE_READY,
        ADVANCE_WINDOW, CONTINUE_MOVEMENT, CONTINUE_FLOW, CAST, READY_ATTACK, READY_MOVE, READY_SPELL,
        CREATE_ENVIRONMENT_ZONE, END_ENVIRONMENT_ZONE, EXPLORATION_HEALTH, EXPLORATION_ADVANCE_TIME, EXPLORATION_MOVE, REENTER_COMBAT, CLOSE_SCENE, END_CONCENTRATION, END_COMBAT }
    public enum FlowStatus { ACTIVE, COMPLETED }
    public enum FrameKind { MOVEMENT, ATTACK, CAST, READY_RELEASE, HEALTH, SAVING_THROW, REACTION_SELECTION }
    public enum Invocation { ROOT, REACTION, CONTINUATION }
    public enum FrameStatus { RUNNABLE, WAITING_WINDOW, WAITING_CHILD, PENDING, DECIDED, TERMINAL }
    public enum Stage { MOVEMENT_LEAVE_REACH_BEFORE, MOVEMENT_CONTINUE, ATTACK_HIT_BEFORE_DAMAGE,
        ATTACK_RESOLVED, CAST_BEFORE_COMPLETION, CAST_RESOLVED, READY_TRIGGER_AFTER, READY_RELEASE_RESOLVED,
        SAVING_THROW_RESOLVED, HEALTH_RESOLVED, MOVEMENT_COMPLETED, MOVEMENT_STOPPED }
    public enum WindowStatus { OPEN, CLOSED, CONSUMED }
    public enum OpportunityStatus { PENDING, PASSED, DECLARED, COMPLETED, INELIGIBLE }
    public enum MovementStatus { PENDING, COMPLETED, STOPPED }
    public enum EffectStatus { ACTIVE, ENDED }
    public enum ReactionKind { OPPORTUNITY_ATTACK, SHIELD, COUNTERSPELL, READY_RELEASE }
    public enum TriggerKind { MOVEMENT_LEAVE_REACH_BEFORE, ATTACK_HIT_BEFORE_DAMAGE, CAST_BEFORE_COMPLETION,
        CREATURE_ENTER_CELL_AFTER, ATTACK_COMPLETED, CAST_COMPLETED }
    public enum CastStatus { PENDING, HELD, RESOLVED, COUNTERED }
    public enum AttackStatus { HIT_PENDING_DAMAGE, RESOLVED }
    public enum ReadyResponseKind { ATTACK, MOVE, SPELL }
    public enum ReadyStatus { PREPARING, ARMED, RELEASED, EXPIRED, LOST }
    public enum ConcentrationKind { BLESS_EFFECT, READY_SPELL }
    public enum SuppressionReleaseReason { LEFT_ZONE, ZONE_ENDED, EFFECT_ENDED }

    /** Coordinates are cells in a bounded, unobscured, level five-foot square grid. */
    public record Position(int x, int y) {
        public Position { if (Math.abs((long)x) > 10000 || Math.abs((long)y) > 10000) throw invalid("S2_COMBAT_POSITION"); }
        public int distanceFeet(Position other) { return Math.max(Math.abs(x - other.x), Math.abs(y - other.y)) * 5; }
    }
    public record Deployment(UUID characterId, Position position, int side) {
        public Deployment { uuid(characterId); Objects.requireNonNull(position); if (side < 0 || side > 15) throw invalid("S2_COMBAT_SIDE"); }
    }
    /** An exhaustive intent union; irrelevant fields are rejected by the pure preflight. */
    public record Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path,
            List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey,
            UUID windowKey, UUID opportunityKey, CastInput castInput, ReadyInput readyInput, UUID readyKey,
            ZoneInput zoneInput, UUID zoneKey, com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthInput healthInput, int seconds) {
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path, List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey, CastInput castInput, ReadyInput readyInput, UUID readyKey, ZoneInput zoneInput, UUID zoneKey) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,castInput,readyInput,readyKey,zoneInput,zoneKey,null,0);
        }
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path,
                List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey,
                UUID windowKey, UUID opportunityKey, CastInput castInput) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,castInput,null,null,null,null);
        }
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path,
                List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,null);
        }
        public Command {
            if (kind==null || path==null || deployments==null || tieBreakOrder==null || path.size()>64 || deployments.size()>16 || tieBreakOrder.size()>16)
                throw invalid("S2_COMBAT_COMMAND");
            path = List.copyOf(path); deployments = List.copyOf(deployments); tieBreakOrder = List.copyOf(tieBreakOrder);
        }
        public static Command cast(UUID actor, CastInput input) { return new Command(Kind.CAST,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null,input); }
        public static Command start(List<Deployment> deployments, List<UUID> ties) { return new Command(Kind.START,null,null,null,List.of(),deployments,ties,null,null,null,null); }
        public static Command action(Kind kind, UUID actor) { return new Command(kind,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null); }
        public static Command attack(UUID actor, UUID target, UUID item) { return new Command(Kind.ATTACK,actor,target,item,List.of(),List.of(),List.of(),null,null,null,null); }
        public static Command move(UUID actor, List<Position> path) { return new Command(Kind.MOVE,actor,null,null,path,List.of(),List.of(),null,null,null,null); }
        public static Command reaction(Kind kind, UUID actor, UUID item, UUID flow, UUID leaf, UUID window, UUID opportunity) {
            return new Command(kind,actor,null,item,List.of(),List.of(),List.of(),flow,leaf,window,opportunity);
        }
        public static Command continuation(UUID actor, UUID flow, UUID leaf, UUID window) {
            return new Command(Kind.CONTINUE_MOVEMENT,actor,null,null,List.of(),List.of(),List.of(),flow,leaf,window,null);
        }
        public static Command advanceTime(UUID actor,int seconds) {
            return new Command(Kind.EXPLORATION_ADVANCE_TIME,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null,null,null,null,null,null,null,seconds);
        }
        public static Command exploreMove(UUID actor,List<Position> path) {
            return new Command(Kind.EXPLORATION_MOVE,actor,null,null,path,List.of(),List.of(),null,null,null,null);
        }
        public static Command worldHealth(UUID actor,UUID target,com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthInput input) {
            return new Command(Kind.EXPLORATION_HEALTH,actor,target,null,List.of(),List.of(),List.of(),null,null,null,null,null,null,null,null,null,input,0);
        }
        public static Command reenter(UUID actor,List<UUID> ties) {
            return new Command(Kind.REENTER_COMBAT,actor,null,null,List.of(),List.of(),ties,null,null,null,null);
        }
        public static Command createZone(UUID actor,ZoneInput input) {
            return new Command(Kind.CREATE_ENVIRONMENT_ZONE,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null,null,null,null,input,null);
        }
        public static Command endZone(UUID actor,UUID zone) {
            return new Command(Kind.END_ENVIRONMENT_ZONE,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null,null,null,null,null,zone);
        }
        public static Command ready(UUID actor, ReadyInput input) {
            Kind kind=switch(input.responseKind()) {case ATTACK->Kind.READY_ATTACK;case MOVE->Kind.READY_MOVE;case SPELL->Kind.READY_SPELL;};
            return new Command(kind,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null,null,input,null,null,null);
        }
        public static Command reactionSpell(Kind kind, UUID actor, CastInput input, UUID flow, UUID leaf, UUID window, UUID opportunity) {
            return new Command(kind,actor,null,null,List.of(),List.of(),List.of(),flow,leaf,window,opportunity,input);
        }
        public static Command releaseReady(UUID actor, UUID ready, UUID flow, UUID leaf, UUID window, UUID opportunity) {
            return new Command(Kind.RELEASE_READY,actor,null,null,List.of(),List.of(),List.of(),flow,leaf,window,opportunity,null,null,ready,null,null);
        }
    }
    /** The client chooses a declared spell and complete bounded target set; availability, cost and dice stay server-owned. */
    public record CastInput(RuleIdentity castingRule, RuleIdentity spell, List<UUID> targets, UUID materialItemId,
            int slotLevel, UUID targetCastFrameKey) {
        public CastInput(RuleIdentity castingRule, RuleIdentity spell, List<UUID> targets, UUID materialItemId) {
            this(castingRule,spell,targets,materialItemId,1,null);
        }
        public CastInput {
            if (castingRule == null || spell == null || targets == null || targets.isEmpty() || targets.size() > 3)
                throw invalid("S2_SPELL_COMMAND");
            if(slotLevel<1 || slotLevel>9)throw invalid("S2_SPELL_COMMAND");
            targets = List.copyOf(targets);
            if (new HashSet<>(targets).size() != targets.size()) throw invalid("S2_SPELL_TARGET");
            targets.forEach(Schema2CombatState::uuid); if (materialItemId != null) uuid(materialItemId); if(targetCastFrameKey!=null)uuid(targetCastFrameKey);
        }
    }
    public record OngoingEffect(UUID effectKey, RuleIdentity spell, UUID casterId, List<UUID> targets,
            long startedAtSecond, long expiresAtSecond, EffectStatus status, String endReason) {
        public OngoingEffect { targets = List.copyOf(targets); }
    }
    public record CastAttempt(UUID frameKey, UUID actorId, RuleIdentity castingRule, RuleIdentity spell,
            List<UUID> targets, RuleIdentity slotResource, int slotLevel, UUID materialItemId,
            List<Integer> healingDice, int healingModifier, int requestedHealing, int appliedHealing, UUID effectKey,
            CastStatus status, UUID targetCastFrameKey, UUID readyKey) {
        public CastAttempt(UUID frameKey, UUID actorId, RuleIdentity castingRule, RuleIdentity spell,
                List<UUID> targets, RuleIdentity slotResource, int slotLevel, UUID materialItemId,
                List<Integer> healingDice, int healingModifier, int requestedHealing, int appliedHealing, UUID effectKey) {
            this(frameKey,actorId,castingRule,spell,targets,slotResource,slotLevel,materialItemId,healingDice,healingModifier,requestedHealing,appliedHealing,effectKey,CastStatus.RESOLVED,null,null);
        }
        public CastAttempt { targets = List.copyOf(targets); healingDice = List.copyOf(healingDice); }
    }
    public record ReadyTrigger(TriggerKind kind, UUID subjectId, Position position) {
        public ReadyTrigger {if(kind==null)throw invalid("S2_READY_TRIGGER");uuid(subjectId);if((position!=null)!=(kind==TriggerKind.CREATURE_ENTER_CELL_AFTER))throw invalid("S2_READY_TRIGGER");}
    }
    public record ReadyInput(ReadyTrigger trigger, ReadyResponseKind responseKind, UUID targetId,
            UUID itemId, List<Position> path, CastInput spell) {
        public ReadyInput {if(trigger==null||responseKind==null||path==null||path.size()>64)throw invalid("S2_READY_COMMAND");path=List.copyOf(path);if(targetId!=null)uuid(targetId);if(itemId!=null)uuid(itemId);}
    }
    public record ReadyPreparation(UUID readyKey, UUID actorId, long declaredTurnNumber, long expiresAtTurnNumber,
            ReadyInput input, ReadyStatus status, UUID heldCastFrameKey, String endReason) { }
    public record ReadyRelease(UUID frameKey, UUID readyKey, UUID triggerKey, boolean completed, String outcome) { }
    public record TriggerFact(UUID triggerKey, TriggerKind kind, UUID sourceFrameKey, UUID subjectId,
            UUID targetId, Position position, int ordinal) { }
    public record ReactionOption(ReactionKind kind, UUID itemId, RuleIdentity castingRule,
            RuleIdentity spell, int slotLevel, UUID readyKey) { }
    public record ShieldEffect(UUID effectKey, UUID castFrameKey, UUID actorId, long startedTurnNumber,
            long expiresAtTurnNumber, EffectStatus status, String endReason) { }
    public record ConcentrationHolder(UUID actorId, ConcentrationKind kind, UUID sourceKey) { }
    public record AbilityCheckAttempt(UUID frameKey, UUID subjectId, String kind, String abilityKey,
            int d20, int abilityModifier, int proficiencyBonus, int difficultyClass, int total, boolean success) { }
    public record EnvironmentZone(UUID zoneKey, RuleIdentity rule, Position center, UUID createdOperationId,
            UUID endedOperationId) { }
    public record SuppressionCause(UUID causeKey, UUID zoneKey, UUID effectKey, UUID targetId,
            UUID establishedOperationId, UUID releasedOperationId, SuppressionReleaseReason releaseReason) { }
    public record ZoneInput(RuleIdentity rule, Position center) { }
    public record SavingThrowAttempt(UUID frameKey, UUID subjectId, String kind, String abilityKey,
            int d20, int abilityModifier, int proficiencyBonus, UUID blessingEffectKey, Integer blessingD4,
            int difficultyClass, int total, boolean success) { }
    public record Initiative(UUID characterId, int d20, int dexterityModifier, int total, int ordinal) { }
    public record AttackSequence(UUID sequenceKey, int limit, int performed, int forfeited, boolean closed) { }
    public record Combatant(UUID characterId, Position position, int side, boolean actionAvailable,
            boolean reactionAvailable, int movementSpentHalfFeet, int movementBonus, boolean disengaged,
            boolean dodging, AttackSequence sequence, boolean prone) {
        public Combatant(UUID characterId, Position position, int side, boolean actionAvailable,
                boolean reactionAvailable, int movementSpentHalfFeet, int movementBonus, boolean disengaged,
                boolean dodging, AttackSequence sequence) {
            this(characterId,position,side,actionAvailable,reactionAvailable,movementSpentHalfFeet,movementBonus,disengaged,dodging,sequence,false);
        }
    }
    public record Frame(UUID frameKey, UUID parentFrameKey, FrameKind kind, UUID ownerKey,
            Invocation invocation, FrameStatus status, Stage stage, UUID windowKey, UUID opportunityKey) { }
    public record Opportunity(UUID opportunityKey, UUID actorId, int priority, List<ReactionOption> options,
            OpportunityStatus status, UUID selectionFrameKey, UUID childFrameKey, String reason) {
        public Opportunity { options = List.copyOf(options); }
        public List<UUID> allowedItems() { return options.stream().filter(o->o.kind()==ReactionKind.OPPORTUNITY_ATTACK).map(ReactionOption::itemId).toList(); }
        public UUID attackFrameKey() { return childFrameKey; }
    }
    public record Window(UUID windowKey, UUID ownerFrameKey, int checkpointOrdinal, int boundaryIndex,
            WindowStatus status, List<Opportunity> opportunities, TriggerFact trigger) {
        public Window(UUID windowKey, UUID ownerFrameKey, int checkpointOrdinal, int boundaryIndex,
                WindowStatus status, List<Opportunity> opportunities) {
            this(windowKey,ownerFrameKey,checkpointOrdinal,boundaryIndex,status,opportunities,null);
        }
        public Window { opportunities = List.copyOf(opportunities); }
    }
    public record Movement(UUID movementKey, UUID actorId, Position origin, List<Position> path,
            int cursor, int spent, MovementStatus status, String stopReason, boolean readied, int allowanceFeet) {
        public Movement(UUID movementKey, UUID actorId, Position origin, List<Position> path,
                int cursor, int spent, MovementStatus status, String stopReason) {
            this(movementKey,actorId,origin,path,cursor,spent,status,stopReason,false,0);
        }
        public Movement { path = List.copyOf(path); }
    }
    public record AttackAttempt(UUID frameKey, UUID actorId, UUID targetId, UUID itemId,
            UUID sequenceKey, int attackOrdinal, boolean opportunityAttack, int firstD20, Integer secondD20,
            int selectedD20, int abilityModifier, int proficiencyBonus, int armorClass, boolean advantage,
            boolean disadvantage, boolean critical, boolean hit, List<Integer> damageDice, int damageAmount,
            UUID blessingEffectKey, Integer blessingD4, AttackStatus status, boolean initialHit, int finalArmorClass) {
        public AttackAttempt(UUID frameKey, UUID actorId, UUID targetId, UUID itemId,
                UUID sequenceKey, int attackOrdinal, boolean opportunityAttack, int firstD20, Integer secondD20,
                int selectedD20, int abilityModifier, int proficiencyBonus, int armorClass, boolean advantage,
                boolean disadvantage, boolean critical, boolean hit, List<Integer> damageDice, int damageAmount,
                UUID blessingEffectKey, Integer blessingD4) {
            this(frameKey,actorId,targetId,itemId,sequenceKey,attackOrdinal,opportunityAttack,firstD20,secondD20,selectedD20,abilityModifier,proficiencyBonus,armorClass,advantage,disadvantage,critical,hit,damageDice,damageAmount,blessingEffectKey,blessingD4,AttackStatus.RESOLVED,hit,armorClass);
        }
        public AttackAttempt(UUID frameKey, UUID actorId, UUID targetId, UUID itemId,
                UUID sequenceKey, int attackOrdinal, boolean opportunityAttack, int firstD20, Integer secondD20,
                int selectedD20, int abilityModifier, int proficiencyBonus, int armorClass, boolean advantage,
                boolean disadvantage, boolean critical, boolean hit, List<Integer> damageDice, int damageAmount) {
            this(frameKey,actorId,targetId,itemId,sequenceKey,attackOrdinal,opportunityAttack,firstD20,secondD20,
                    selectedD20,abilityModifier,proficiencyBonus,armorClass,advantage,disadvantage,critical,hit,damageDice,damageAmount,null,null);
        }
        public AttackAttempt { damageDice = List.copyOf(damageDice); }
    }
    public record Flow(UUID flowKey, UUID rootFrameKey, UUID leafFrameKey, FlowStatus status,
            List<Frame> frames, List<Window> windows, List<Movement> movements, List<AttackAttempt> attacks,
            List<CastAttempt> casts, List<SavingThrowAttempt> savingThrows, List<ReadyRelease> readyReleases,
            List<AbilityCheckAttempt> abilityChecks) {
        public Flow(UUID flowKey, UUID rootFrameKey, UUID leafFrameKey, FlowStatus status,
                List<Frame> frames, List<Window> windows, Movement movement, List<AttackAttempt> attacks,
                List<CastAttempt> casts, List<SavingThrowAttempt> savingThrows) {
            this(flowKey,rootFrameKey,leafFrameKey,status,frames,windows,movement==null?List.of():List.of(movement),attacks,casts,savingThrows,List.of(),List.of());
        }
        public Flow(UUID flowKey, UUID rootFrameKey, UUID leafFrameKey, FlowStatus status,
                List<Frame> frames, List<Window> windows, Movement movement, List<AttackAttempt> attacks) {
            this(flowKey,rootFrameKey,leafFrameKey,status,frames,windows,movement,attacks,List.of(),List.of());
        }
        public Flow { frames = List.copyOf(frames); windows = List.copyOf(windows); movements=List.copyOf(movements); attacks = List.copyOf(attacks); casts = List.copyOf(casts); savingThrows = List.copyOf(savingThrows); readyReleases=List.copyOf(readyReleases); abilityChecks=List.copyOf(abilityChecks); }
        public Movement movement() { return frames.stream().filter(f->f.frameKey().equals(rootFrameKey)&&f.kind()==FrameKind.MOVEMENT).findFirst().flatMap(f->movements.stream().filter(m->m.movementKey().equals(f.ownerKey())).findFirst()).orElse(null); }
    }
    public record Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status,
            int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants,
            List<Initiative> initiative, List<Flow> flows, List<OngoingEffect> ongoingEffects,
            List<ReadyPreparation> readyPreparations, List<TriggerFact> triggerFacts, List<ShieldEffect> shields,
            List<ConcentrationHolder> concentrationHolders, List<EnvironmentZone> environmentZones,
            List<SuppressionCause> suppressionCauses) {
        public Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status,
                int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants,
                List<Initiative> initiative, List<Flow> flows, List<OngoingEffect> ongoingEffects) {
            this(combatId,campaignId,combatRule,status,round,turnNumber,activeOrdinal,version,combatants,initiative,flows,ongoingEffects,List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        }
        public Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status,
                int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants,
                List<Initiative> initiative, List<Flow> flows) {
            this(combatId,campaignId,combatRule,status,round,turnNumber,activeOrdinal,version,combatants,initiative,flows,List.of());
        }
        public Battle { combatants = List.copyOf(combatants); initiative = List.copyOf(initiative); flows = List.copyOf(flows); ongoingEffects = List.copyOf(ongoingEffects); readyPreparations=List.copyOf(readyPreparations);triggerFacts=List.copyOf(triggerFacts);shields=List.copyOf(shields);concentrationHolders=List.copyOf(concentrationHolders);environmentZones=List.copyOf(environmentZones);suppressionCauses=List.copyOf(suppressionCauses); }
        public UUID activeActor() { return initiative.get(activeOrdinal).characterId(); }
        public Flow activeFlow() { return flows.stream().filter(f -> f.status() == FlowStatus.ACTIVE).findFirst().orElse(null); }
    }
    public record Roll(String purpose, UUID subjectId, int sides, int value) { }
    public record HealthChange(UUID characterId, State before, State after, UUID frameKey,
            int damageAmount, boolean critical, HealthResult healthResult, boolean deathSave,
            boolean timeAdvance, int seconds, RuleIdentity resource, long resourceAmount, boolean healing,
            com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthInput environmentInput) {
        public HealthChange(UUID characterId, State before, State after, UUID frameKey, int damageAmount,
                boolean critical, HealthResult healthResult, boolean deathSave, boolean timeAdvance, int seconds,
                RuleIdentity resource, long resourceAmount, boolean healing) {
            this(characterId,before,after,frameKey,damageAmount,critical,healthResult,deathSave,timeAdvance,seconds,resource,resourceAmount,healing,null);
        }
        public HealthChange(UUID characterId, State before, State after, UUID frameKey,
                int damageAmount, boolean critical, HealthResult healthResult, boolean deathSave, boolean timeAdvance, int seconds) {
            this(characterId,before,after,frameKey,damageAmount,critical,healthResult,deathSave,timeAdvance,seconds,null,0,false);
        }
        public HealthChange(UUID characterId, State before, State after, UUID frameKey,
                int damageAmount, boolean critical, HealthResult healthResult, boolean deathSave) {
            this(characterId,before,after,frameKey,damageAmount,critical,healthResult,deathSave,false,0);
        }
    }
    public record CombatFact(String kind, UUID subjectId, UUID referenceKey, String reason) { }
    public record Resolution(Battle after, List<HealthChange> healthChanges, List<Roll> rolls, List<CombatFact> facts) {
        public Resolution { healthChanges = List.copyOf(healthChanges); rolls = List.copyOf(rolls); facts = List.copyOf(facts); }
    }
    static void uuid(UUID id) { if (id == null || id.version() != 4 || id.variant() != 2) throw invalid("S2_COMBAT_IDENTITY"); }
    static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
