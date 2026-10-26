package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthResult;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State;
import java.util.*;

/** Typed INTERNAL combat facts. Character health and inventory retain their existing sole owner. */
public final class Schema2CombatState {
    private Schema2CombatState() { }
    public enum Status { ACTIVE, ENDED }
    public enum Kind { START, END_TURN, DASH, DISENGAGE, DODGE, STAND_UP, BEGIN_ATTACK, ATTACK, FORFEIT_ATTACKS,
        MOVE, PASS_REACTION, DECLARE_OPPORTUNITY_ATTACK, ADVANCE_WINDOW, CONTINUE_MOVEMENT, END_COMBAT }
    public enum FlowStatus { ACTIVE, COMPLETED }
    public enum FrameKind { MOVEMENT, ATTACK, REACTION_SELECTION }
    public enum Invocation { ROOT, REACTION }
    public enum FrameStatus { RUNNABLE, WAITING_WINDOW, WAITING_CHILD, PENDING, DECIDED, TERMINAL }
    public enum Stage { MOVEMENT_LEAVE_REACH_BEFORE, MOVEMENT_CONTINUE, ATTACK_RESOLVED, MOVEMENT_COMPLETED, MOVEMENT_STOPPED }
    public enum WindowStatus { OPEN, CLOSED, CONSUMED }
    public enum OpportunityStatus { PENDING, PASSED, COMPLETED, INELIGIBLE }
    public enum MovementStatus { PENDING, COMPLETED, STOPPED }

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
            UUID windowKey, UUID opportunityKey) {
        public Command {
            if (kind==null || path==null || deployments==null || tieBreakOrder==null || path.size()>64 || deployments.size()>16 || tieBreakOrder.size()>16)
                throw invalid("S2_COMBAT_COMMAND");
            path = List.copyOf(path); deployments = List.copyOf(deployments); tieBreakOrder = List.copyOf(tieBreakOrder);
        }
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
    }
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
    public record Opportunity(UUID opportunityKey, UUID actorId, int priority, List<UUID> allowedItems,
            OpportunityStatus status, UUID selectionFrameKey, UUID attackFrameKey, String reason) {
        public Opportunity { allowedItems = List.copyOf(allowedItems); }
    }
    public record Window(UUID windowKey, UUID ownerFrameKey, int checkpointOrdinal, int boundaryIndex,
            WindowStatus status, List<Opportunity> opportunities) {
        public Window { opportunities = List.copyOf(opportunities); }
    }
    public record Movement(UUID movementKey, UUID actorId, Position origin, List<Position> path,
            int cursor, int spent, MovementStatus status, String stopReason) {
        public Movement { path = List.copyOf(path); }
    }
    public record AttackAttempt(UUID frameKey, UUID actorId, UUID targetId, UUID itemId,
            UUID sequenceKey, int attackOrdinal, boolean opportunityAttack, int firstD20, Integer secondD20,
            int selectedD20, int abilityModifier, int proficiencyBonus, int armorClass, boolean advantage,
            boolean disadvantage, boolean critical, boolean hit, List<Integer> damageDice, int damageAmount) {
        public AttackAttempt { damageDice = List.copyOf(damageDice); }
    }
    public record Flow(UUID flowKey, UUID rootFrameKey, UUID leafFrameKey, FlowStatus status,
            List<Frame> frames, List<Window> windows, Movement movement, List<AttackAttempt> attacks) {
        public Flow { frames = List.copyOf(frames); windows = List.copyOf(windows); attacks = List.copyOf(attacks); }
    }
    public record Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status,
            int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants,
            List<Initiative> initiative, List<Flow> flows) {
        public Battle { combatants = List.copyOf(combatants); initiative = List.copyOf(initiative); flows = List.copyOf(flows); }
        public UUID activeActor() { return initiative.get(activeOrdinal).characterId(); }
        public Flow activeFlow() { return flows.stream().filter(f -> f.status() == FlowStatus.ACTIVE).findFirst().orElse(null); }
    }
    public record Roll(String purpose, UUID subjectId, int sides, int value) { }
    public record HealthChange(UUID characterId, State before, State after, UUID frameKey,
            int damageAmount, boolean critical, HealthResult healthResult, boolean deathSave,
            boolean timeAdvance, int seconds) {
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
