package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.DamageType;
import com.dndtool.service.Schema2CharacterRuntime.HealthResult;
import com.dndtool.service.Schema2CharacterRuntime.State;
import java.util.*;

/** Typed INTERNAL combat facts. Character health and inventory retain their existing sole owner. */
public final class Schema2CombatState {
    private Schema2CombatState() { }
    public enum Status { ACTIVE, EXPLORATION, ENDED }
    public enum Kind { START, OPEN_SCENE, SET_SCENE_ROSTER, MONSTER_BREATH, MONSTER_ATTACK, BEGIN_MONSTER_REST, COMPLETE_MONSTER_REST, END_TURN, DASH, DISENGAGE, DODGE, DROP_PRONE, STAND_UP, SECOND_WIND, BEGIN_ATTACK, ATTACK, FORFEIT_ATTACKS,
        GRAPPLE, SHOVE_PRONE, SHOVE_PUSH, CHOOSE_MELEE_DEFENSE, ESCAPE_GRAPPLE, RELEASE_GRAPPLE,
        HIDE, SEARCH, NIMBLE_HIDE, NIMBLE_DISENGAGE, SET_OBSERVATION_CELL,
        MOVE, PASS_REACTION, DECLARE_OPPORTUNITY_ATTACK, DECLARE_SHIELD, DECLARE_COUNTERSPELL, RELEASE_READY,
        ADVANCE_WINDOW, CONTINUE_MOVEMENT, CONTINUE_FLOW, CAST, READY_ATTACK, READY_MOVE, READY_SPELL,
        CREATE_ENVIRONMENT_ZONE, END_ENVIRONMENT_ZONE, EXPLORATION_HEALTH, EXPLORATION_ADVANCE_TIME, EXPLORATION_MOVE, REENTER_COMBAT, CLOSE_SCENE, END_CONCENTRATION, END_COMBAT }
    public enum FlowStatus { ACTIVE, COMPLETED }
    public enum FrameKind { SAVE_DAMAGE, MOVEMENT, ATTACK, CAST, READY_RELEASE, HEALTH, SAVING_THROW, REACTION_SELECTION, GRAPPLE_CONTEST, DOMAIN_CHOICE }
    public enum Invocation { ROOT, REACTION, CONTINUATION }
    public enum FrameStatus { RUNNABLE, WAITING_WINDOW, WAITING_CHILD, WAITING_CHOICE, PENDING, DECIDED, TERMINAL }
    public enum Stage { SAVE_DAMAGE_ROLLED, SAVE_DAMAGE_COMPLETED, MOVEMENT_LEAVE_REACH_BEFORE, MOVEMENT_CONTINUE, ATTACK_HIT_BEFORE_DAMAGE,
        ATTACK_RESOLVED, CAST_BEFORE_COMPLETION, CAST_RESOLVED, READY_TRIGGER_AFTER, READY_RELEASE_RESOLVED,
        SAVING_THROW_RESOLVED, HEALTH_RESOLVED, MOVEMENT_COMPLETED, MOVEMENT_STOPPED, MELEE_CONTROL_CHOICE, MELEE_CONTROL_RESOLVED }
    public enum WindowStatus { OPEN, CLOSED, CONSUMED }
    public enum OpportunityStatus { PENDING, PASSED, DECLARED, COMPLETED, INELIGIBLE }
    public enum MovementStatus { PENDING, COMPLETED, STOPPED }
    public enum EffectStatus { ACTIVE, ENDED }
    public enum ReactionKind { OPPORTUNITY_ATTACK, SHIELD, COUNTERSPELL, READY_RELEASE }
    public enum TriggerKind { MOVEMENT_LEAVE_REACH_BEFORE, ATTACK_HIT_BEFORE_DAMAGE, CAST_BEFORE_COMPLETION,
        CREATURE_ENTER_CELL_AFTER, ATTACK_COMPLETED, CAST_COMPLETED, MONSTER_ACTION_COMPLETED }
    public enum CastStatus { PENDING, HELD, RESOLVED, COUNTERED, INTERRUPTED }
    public enum AttackStatus { HIT_PENDING_DAMAGE, RESOLVED }
    public enum OnHitOutcome { SAVE_SUCCEEDED, PRONE_APPLIED, ALREADY_PRONE, CONDITION_IMMUNE, TARGET_DEAD }
    public enum ReadyResponseKind { ATTACK, MOVE, SPELL }
    public enum ReadyStatus { PREPARING, ARMED, RELEASED, EXPIRED, LOST }
    public enum ConcentrationKind { BLESS_EFFECT, READY_SPELL }
    public enum SuppressionReleaseReason { LEFT_ZONE, ZONE_ENDED, EFFECT_ENDED }

    /** Coordinates are cells in a bounded, level five-foot square grid. */
    public record Position(int x, int y) {
        public Position { if (Math.abs((long)x) > 10000 || Math.abs((long)y) > 10000) throw invalid("S2_COMBAT_POSITION"); }
        public int distanceFeet(Position other) { return Math.max(Math.abs(x - other.x), Math.abs(y - other.y)) * 5; }
    }
    public record Deployment(UUID characterId, Position position, int side) {
        public Deployment { uuid(characterId); Objects.requireNonNull(position); if (side < 0 || side > 15) throw invalid("S2_COMBAT_SIDE"); }
    }
    public enum Cover {
        NONE(0), HALF(2), THREE_QUARTERS(5), TOTAL(0);

        private final int armorClassBonus;

        Cover(int armorClassBonus) { this.armorClassBonus = armorClassBonus; }

        public int armorClassBonus() { return armorClassBonus; }
    }
    public record ObservationCellInput(Position position, boolean hideAllowed, boolean blocksSight, Cover cover) {
        public ObservationCellInput {
            if (position == null || cover == null) throw invalid("S2_OBSERVATION_CELL");
        }

        public boolean isDefault() { return !hideAllowed && !blocksSight && cover == Cover.NONE; }
    }
    /** A fully seen, unhidden subject has no retained pair. */
    public record ObservationPair(UUID observerId, UUID subjectId, Position lastKnownPosition, UUID hideOperationId) { }
    public record ObservationRoll(int firstD20, Integer secondD20, int selectedD20,
            int abilityModifier, int skillBonus, boolean disadvantage, int total) { }
    public record ObservationContest(UUID counterpartId, int opposingScore, UUID previousHideOperationId, boolean hiddenAfter) { }
    public record ObservationCheck(UUID operationId, Kind kind, UUID actorId, Position position, RuleIdentity rule,
            RuleIdentity skill, String monsterSkill, boolean nimbleEscape, ObservationRoll roll, List<ObservationContest> contests,
            UUID openingOperationId, UUID rollSource) {
        public ObservationCheck(UUID operationId, Kind kind, UUID actorId, Position position, RuleIdentity rule,
                RuleIdentity skill, String monsterSkill, boolean nimbleEscape, ObservationRoll roll, List<ObservationContest> contests) {
            this(operationId, kind, actorId, position, rule, skill, monsterSkill, nimbleEscape, roll, contests, null, null);
        }
        public ObservationCheck { contests = List.copyOf(contests); }
    }
    public record ObservationState(List<ObservationCellInput> cells, List<ObservationPair> pairs, List<ObservationCheck> checks) {
        public static final ObservationState EMPTY = new ObservationState(List.of(), List.of(), List.of());

        public ObservationState {
            cells = List.copyOf(cells);
            pairs = List.copyOf(pairs);
            checks = List.copyOf(checks);
        }
    }
    public record OpeningGroup(List<UUID> members) {
        public OpeningGroup {
            if (members == null || members.isEmpty() || members.size() > Schema2CombatRules.MAX_COMBATANTS)
                throw invalid("S2_COMBAT_OPENING_GROUP");
            members.forEach(Schema2CombatState::uuid);
            members = List.copyOf(members);
        }
        public UUID rollSource() { return members.getFirst(); }
    }
    public record OpeningInput(List<OpeningGroup> stealthGroups, List<OpeningGroup> initiativeGroups) {
        public OpeningInput {
            stealthGroups = openingGroups(stealthGroups, 1);
            initiativeGroups = openingGroups(initiativeGroups, 2);
        }

        private static List<OpeningGroup> openingGroups(List<OpeningGroup> groups, int minimumMembers) {
            if (groups == null || groups.size() > Schema2CombatRules.MAX_COMBATANTS)
                throw invalid("S2_COMBAT_OPENING_GROUP");
            Set<UUID> seen = new HashSet<>();
            for (OpeningGroup group : groups) {
                if (group == null || group.members().size() < minimumMembers)
                    throw invalid("S2_COMBAT_OPENING_GROUP");
                for (UUID member : group.members())
                    if (!seen.add(member) || seen.size() > Schema2CombatRules.MAX_COMBATANTS)
                        throw invalid("S2_COMBAT_OPENING_GROUP");
            }
            return List.copyOf(groups);
        }
    }
    /** An exhaustive intent union; irrelevant fields are rejected by the pure preflight. */
    public record Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path,
            List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey,
            UUID windowKey, UUID opportunityKey, CastInput castInput, ReadyInput readyInput, UUID readyKey,
            ZoneInput zoneInput, UUID zoneKey, com.dndtool.service.Schema2CharacterRuntime.HealthInput healthInput, int seconds,
            List<MonsterDeployment> monsterDeployments, BreathInput breathInput, MonsterRestInput monsterRestInput,
            MeleeInput meleeInput, DragInput dragInput, MonsterAttackInput monsterAttackInput, ObservationCellInput observationCell,
            OpeningInput opening) {
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path, List<Deployment> deployments,
                List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey, CastInput castInput,
                ReadyInput readyInput, UUID readyKey, ZoneInput zoneInput, UUID zoneKey,
                com.dndtool.service.Schema2CharacterRuntime.HealthInput healthInput, int seconds,
                List<MonsterDeployment> monsterDeployments, BreathInput breathInput, MonsterRestInput monsterRestInput,
                MeleeInput meleeInput, DragInput dragInput, MonsterAttackInput monsterAttackInput, ObservationCellInput observationCell) {
            this(kind, actorId, targetId, itemId, path, deployments, tieBreakOrder, flowKey, leafKey, windowKey, opportunityKey,
                    castInput, readyInput, readyKey, zoneInput, zoneKey, healthInput, seconds, monsterDeployments, breathInput,
                    monsterRestInput, meleeInput, dragInput, monsterAttackInput, observationCell, null);
        }
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path, List<Deployment> deployments,
                List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey, CastInput castInput,
                ReadyInput readyInput, UUID readyKey, ZoneInput zoneInput, UUID zoneKey,
                com.dndtool.service.Schema2CharacterRuntime.HealthInput healthInput, int seconds,
                List<MonsterDeployment> monsterDeployments, BreathInput breathInput, MonsterRestInput monsterRestInput,
                MeleeInput meleeInput, DragInput dragInput, MonsterAttackInput monsterAttackInput) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,castInput,
                    readyInput,readyKey,zoneInput,zoneKey,healthInput,seconds,monsterDeployments,breathInput,monsterRestInput,
                    meleeInput,dragInput,monsterAttackInput,null);
        }
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path, List<Deployment> deployments,
                List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey, CastInput castInput,
                ReadyInput readyInput, UUID readyKey, ZoneInput zoneInput, UUID zoneKey,
                com.dndtool.service.Schema2CharacterRuntime.HealthInput healthInput, int seconds,
                List<MonsterDeployment> monsterDeployments, BreathInput breathInput, MonsterRestInput monsterRestInput,
                MeleeInput meleeInput, DragInput dragInput) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,castInput,
                    readyInput,readyKey,zoneInput,zoneKey,healthInput,seconds,monsterDeployments,breathInput,monsterRestInput,
                    meleeInput,dragInput,null);
        }
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path, List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey, CastInput castInput, ReadyInput readyInput, UUID readyKey, ZoneInput zoneInput, UUID zoneKey, com.dndtool.service.Schema2CharacterRuntime.HealthInput healthInput, int seconds, List<MonsterDeployment> monsterDeployments, BreathInput breathInput, MonsterRestInput monsterRestInput) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,castInput,readyInput,readyKey,zoneInput,zoneKey,healthInput,seconds,monsterDeployments,breathInput,monsterRestInput,null,null);
        }
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path, List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey, CastInput castInput, ReadyInput readyInput, UUID readyKey, ZoneInput zoneInput, UUID zoneKey, com.dndtool.service.Schema2CharacterRuntime.HealthInput healthInput, int seconds, List<MonsterDeployment> monsterDeployments, BreathInput breathInput) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,castInput,readyInput,readyKey,zoneInput,zoneKey,healthInput,seconds,monsterDeployments,breathInput,null);
        }
        public Command(Kind kind, UUID actorId, UUID targetId, UUID itemId, List<Position> path, List<Deployment> deployments, List<UUID> tieBreakOrder, UUID flowKey, UUID leafKey, UUID windowKey, UUID opportunityKey, CastInput castInput, ReadyInput readyInput, UUID readyKey, ZoneInput zoneInput, UUID zoneKey, com.dndtool.service.Schema2CharacterRuntime.HealthInput healthInput, int seconds) {
            this(kind,actorId,targetId,itemId,path,deployments,tieBreakOrder,flowKey,leafKey,windowKey,opportunityKey,castInput,readyInput,readyKey,zoneInput,zoneKey,healthInput,seconds,List.of(),null);
        }
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
            if (kind==null || path==null || deployments==null || tieBreakOrder==null || path.size()>64 || deployments.size()>64 || tieBreakOrder.size()>64 || monsterDeployments==null || monsterDeployments.size()>64 || monsterDeployments.stream().anyMatch(Objects::isNull))
                throw invalid("S2_COMBAT_COMMAND");
            path = List.copyOf(path); deployments = List.copyOf(deployments); tieBreakOrder = List.copyOf(tieBreakOrder); monsterDeployments=List.copyOf(monsterDeployments);
            if (opening != null && opening.stealthGroups().isEmpty() && opening.initiativeGroups().isEmpty()) opening = null;
        }
        public Command withOpening(OpeningInput input) {
            return new Command(kind, actorId, targetId, itemId, path, deployments, tieBreakOrder, flowKey, leafKey, windowKey,
                    opportunityKey, castInput, readyInput, readyKey, zoneInput, zoneKey, healthInput, seconds, monsterDeployments,
                    breathInput, monsterRestInput, meleeInput, dragInput, monsterAttackInput, observationCell, input);
        }
        public static Command monsterRest(Kind kind, UUID monster, UUID rest, MonsterRestKind restKind) {
            return new Command(kind,monster,null,null,List.of(),List.of(),List.of(),null,null,null,null,null,null,null,null,null,null,0,List.of(),null,new MonsterRestInput(rest,restKind));
        }
        public static Command start(List<Deployment> characters, List<MonsterDeployment> monsters, List<UUID> ties) {
            return new Command(Kind.START,null,null,null,List.of(),characters,ties,null,null,null,null,null,null,null,null,null,null,0,monsters,null);
        }
        public static Command breath(UUID monster, EntryIdentity action, int direction) {
            return new Command(Kind.MONSTER_BREATH,monster,null,null,List.of(),List.of(),List.of(),null,null,null,null,null,null,null,null,null,null,0,List.of(),new BreathInput(action,direction));
        }
        public static Command continueBreath(UUID monster,UUID flow,UUID leaf) {
            return new Command(Kind.CONTINUE_FLOW,monster,null,null,List.of(),List.of(),List.of(),flow,leaf,null,null);
        }
        public static Command cast(UUID actor, CastInput input) { return new Command(Kind.CAST,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null,input); }
        public static Command start(List<Deployment> deployments, List<UUID> ties) { return new Command(Kind.START,null,null,null,List.of(),deployments,ties,null,null,null,null); }
        public static Command openScene(List<Deployment> deployments, List<MonsterDeployment> monsters) {
            return scene(Kind.OPEN_SCENE, deployments, monsters);
        }
        public static Command sceneRoster(List<Deployment> deployments) {
            return sceneRoster(deployments, List.of());
        }
        public static Command sceneRoster(List<Deployment> deployments, List<MonsterDeployment> monsters) {
            return scene(Kind.SET_SCENE_ROSTER, deployments, monsters);
        }
        private static Command scene(Kind kind, List<Deployment> deployments, List<MonsterDeployment> monsters) {
            return new Command(kind,null,null,null,List.of(),deployments,List.of(),null,null,null,null,null,null,null,null,null,null,0,monsters,null);
        }
        public static Command action(Kind kind, UUID actor) { return new Command(kind,actor,null,null,List.of(),List.of(),List.of(),null,null,null,null); }
        public static Command setObservationCell(ObservationCellInput input) {
            return new Command(Kind.SET_OBSERVATION_CELL,null,null,null,List.of(),List.of(),List.of(),null,null,null,null,
                    null,null,null,null,null,null,0,List.of(),null,null,null,null,null,input);
        }
        public static Command attack(UUID actor, UUID target, UUID item) { return new Command(Kind.ATTACK,actor,target,item,List.of(),List.of(),List.of(),null,null,null,null); }
        public static Command monsterAttack(UUID actor, UUID target, EntryIdentity action) {
            return new Command(Kind.MONSTER_ATTACK,actor,target,null,List.of(),List.of(),List.of(),null,null,null,null,
                    null,null,null,null,null,null,0,List.of(),null,null,null,null,new MonsterAttackInput(action));
        }
        public static Command move(UUID actor, List<Position> path) { return new Command(Kind.MOVE,actor,null,null,path,List.of(),List.of(),null,null,null,null); }
        public static Command move(UUID actor, List<Position> path, DragInput drag) {
            return new Command(Kind.MOVE,actor,null,null,path,List.of(),List.of(),null,null,null,null,
                    null,null,null,null,null,null,0,List.of(),null,null,null,drag);
        }
        public static Command melee(Kind kind, UUID actor, UUID target, UUID flow, UUID leaf, MeleeInput input) {
            return new Command(kind,actor,target,null,List.of(),List.of(),List.of(),flow,leaf,null,null,
                    null,null,null,null,null,null,0,List.of(),null,null,input,null);
        }
        public static Command reaction(Kind kind, UUID actor, UUID item, UUID flow, UUID leaf, UUID window, UUID opportunity) {
            return new Command(kind,actor,null,item,List.of(),List.of(),List.of(),flow,leaf,window,opportunity);
        }
        public static Command monsterOpportunityAttack(UUID actor, EntryIdentity action, UUID flow, UUID leaf, UUID window, UUID opportunity) {
            return new Command(Kind.DECLARE_OPPORTUNITY_ATTACK,actor,null,null,List.of(),List.of(),List.of(),flow,leaf,window,
                    opportunity,null,null,null,null,null,null,0,List.of(),null,null,null,null,new MonsterAttackInput(action));
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
        public static Command worldHealth(UUID actor,UUID target,com.dndtool.service.Schema2CharacterRuntime.HealthInput input) {
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
            int slotLevel, UUID targetCastFrameKey, Integer coneDirection) {
        public CastInput(RuleIdentity castingRule, RuleIdentity spell, List<UUID> targets, UUID materialItemId,
                int slotLevel, UUID targetCastFrameKey) { this(castingRule,spell,targets,materialItemId,slotLevel,targetCastFrameKey,null); }
        public CastInput(RuleIdentity castingRule, RuleIdentity spell, List<UUID> targets, UUID materialItemId) {
            this(castingRule,spell,targets,materialItemId,1,null);
        }
        public CastInput {
            if (castingRule == null || spell == null || targets == null || targets.size() > 3
                    || (coneDirection==null?targets.isEmpty():!targets.isEmpty()||coneDirection<0||coneDirection>=8))
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
            CastStatus status, UUID targetCastFrameKey, UUID readyKey, Integer coneDirection) {
        public CastAttempt(UUID frameKey, UUID actorId, RuleIdentity castingRule, RuleIdentity spell,
                List<UUID> targets, RuleIdentity slotResource, int slotLevel, UUID materialItemId,
                List<Integer> healingDice, int healingModifier, int requestedHealing, int appliedHealing, UUID effectKey,
                CastStatus status, UUID targetCastFrameKey, UUID readyKey) {
            this(frameKey,actorId,castingRule,spell,targets,slotResource,slotLevel,materialItemId,healingDice,healingModifier,requestedHealing,appliedHealing,effectKey,status,targetCastFrameKey,readyKey,null);
        }
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
            RuleIdentity spell, int slotLevel, UUID readyKey, EntryIdentity monsterAttack) {
        public ReactionOption(ReactionKind kind, UUID itemId, RuleIdentity castingRule, RuleIdentity spell, int slotLevel, UUID readyKey) {
            this(kind,itemId,castingRule,spell,slotLevel,readyKey,null);
        }
    }
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
    public record Initiative(UUID characterId, int d20, int dexterityModifier, int total, int ordinal,
            boolean surprised, UUID rollSource) {
        public Initiative(UUID characterId, int d20, int dexterityModifier, int total, int ordinal) {
            this(characterId, d20, dexterityModifier, total, ordinal, false, characterId);
        }
    }
    public record AttackSequence(UUID sequenceKey, int limit, int performed, int forfeited, boolean closed) { }
    public record Combatant(UUID characterId, Position position, int side, int actionsRemaining,
            int bonusActionsRemaining, boolean reactionAvailable, int movementSpentHalfFeet, int movementBonus,
            boolean disengaged, boolean dodging, AttackSequence sequence, boolean prone) {
        static final int TURN_ACTIONS = 1;
        static final int TURN_BONUS_ACTIONS = 1;

        public boolean actionAvailable() {
            return actionsRemaining > 0;
        }

        public int remainingMovementHalfFeet(int effectiveSpeedFeet) {
            if (effectiveSpeedFeet <= 0) return 0;
            return Math.max(0, 2 * (effectiveSpeedFeet + movementBonus) - movementSpentHalfFeet);
        }

        Combatant spendAction() {
            if (!actionAvailable()) throw invalid("S2_COMBAT_ACTION_UNAVAILABLE");
            return new Combatant(characterId, position, side, actionsRemaining - 1, bonusActionsRemaining,
                    reactionAvailable, movementSpentHalfFeet, movementBonus, disengaged, dodging, sequence, prone);
        }

        Combatant spendBonusAction() {
            if (bonusActionsRemaining <= 0) throw invalid("S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
            return new Combatant(characterId, position, side, actionsRemaining, bonusActionsRemaining - 1,
                    reactionAvailable, movementSpentHalfFeet, movementBonus, disengaged, dodging, sequence, prone);
        }

        Combatant withDisengaged() {
            return new Combatant(characterId, position, side, actionsRemaining, bonusActionsRemaining,
                    reactionAvailable, movementSpentHalfFeet, movementBonus, true, dodging, sequence, prone);
        }

        Combatant spendReaction() {
            return withReactionAvailable(false);
        }

        Combatant withReactionAvailable(boolean available) {
            return new Combatant(characterId, position, side, actionsRemaining, bonusActionsRemaining, available,
                    movementSpentHalfFeet, movementBonus, disengaged, dodging, sequence, prone);
        }

        Combatant startTurn(boolean living, boolean isProne) {
            return new Combatant(characterId, position, side, living ? TURN_ACTIONS : 0,
                    living ? TURN_BONUS_ACTIONS : 0, true, 0, 0, false, false, null, isProne);
        }

        Combatant withPosition(Position destination) {
            return withMovement(destination, movementSpentHalfFeet, prone);
        }

        Combatant withProne(boolean isProne) {
            return withMovement(position, movementSpentHalfFeet, isProne);
        }

        Combatant withMovement(Position destination, int spentHalfFeet, boolean isProne) {
            return new Combatant(characterId, destination, side, actionsRemaining, bonusActionsRemaining,
                    reactionAvailable, spentHalfFeet, movementBonus, disengaged, dodging, sequence, isProne);
        }

        Combatant withSequence(AttackSequence next) {
            return new Combatant(characterId, position, side, actionsRemaining, bonusActionsRemaining,
                    reactionAvailable, movementSpentHalfFeet, movementBonus, disengaged, dodging, next, prone);
        }

        Combatant closeSequence() {
            if (sequence == null || sequence.closed()) return this;
            return withSequence(new AttackSequence(sequence.sequenceKey(), sequence.limit(), sequence.performed(),
                    sequence.limit() - sequence.performed(), true));
        }

        Combatant incapacitated() {
            return new Combatant(characterId, position, side, 0, 0, reactionAvailable, movementSpentHalfFeet,
                    movementBonus, disengaged, false, sequence, true).closeSequence();
        }
    }
    public record Frame(UUID frameKey, UUID parentFrameKey, FrameKind kind, UUID ownerKey,
            Invocation invocation, FrameStatus status, Stage stage, UUID windowKey, UUID opportunityKey) { }
    public record Opportunity(UUID opportunityKey, UUID actorId, int priority, List<ReactionOption> options,
            OpportunityStatus status, UUID selectionFrameKey, UUID childFrameKey, String reason) {
        public Opportunity { options = List.copyOf(options); }
        public List<UUID> allowedItems() { return options.stream().filter(o->o.kind()==ReactionKind.OPPORTUNITY_ATTACK&&o.itemId()!=null).map(ReactionOption::itemId).toList(); }
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
    /** Spent records physical feet traveled. The combatant tracks turn movement cost in half-feet. */
    public record Movement(UUID movementKey, UUID actorId, Position origin, List<Position> path,
            int cursor, int spent, MovementStatus status, String stopReason, boolean readied, int allowanceFeet,
            DragPlan drag, int spentHalfFeet) {
        public Movement(UUID movementKey, UUID actorId, Position origin, List<Position> path,
                int cursor, int spent, MovementStatus status, String stopReason, boolean readied, int allowanceFeet) {
            this(movementKey, actorId, origin, path, cursor, spent, status, stopReason, readied, allowanceFeet, null, 2 * spent);
        }
        public Movement(UUID movementKey, UUID actorId, Position origin, List<Position> path,
                int cursor, int spent, MovementStatus status, String stopReason) {
            this(movementKey,actorId,origin,path,cursor,spent,status,stopReason,false,0);
        }
        public Movement { path = List.copyOf(path); }
        Movement stopped(String reason) {
            return new Movement(movementKey, actorId, origin, path, cursor, spent, MovementStatus.STOPPED, reason,
                    readied, allowanceFeet, drag, spentHalfFeet);
        }
    }
    public record DragInput(UUID grappleKey, List<Position> path) {
        public DragInput {
            uuid(grappleKey);
            if (path == null || path.isEmpty() || path.size() > 64) throw invalid("S2_COMBAT_PATH");
            path = List.copyOf(path);
        }
    }
    public record DragPlan(UUID grappleKey, UUID targetId, Position origin, List<Position> path, int allowanceHalfFeet) {
        public DragPlan { path = List.copyOf(path); }
    }
    public enum CreatureSize { TINY, SMALL, MEDIUM, LARGE, HUGE, GARGANTUAN }
    public enum HandSlot { MAIN_HAND, OFF_HAND }
    public enum ContestSkill {
        ATHLETICS("ability.strength", "proficiency.skill.athletics"),
        ACROBATICS("ability.dexterity", "proficiency.skill.acrobatics");

        private final String abilityKey;
        private final String ruleKey;
        ContestSkill(String abilityKey, String ruleKey) { this.abilityKey = abilityKey; this.ruleKey = ruleKey; }
        public String abilityKey() { return abilityKey; }
        public String ruleKey() { return ruleKey; }
    }
    public enum GrappleEndReason { RELEASED, ESCAPED, GRAPPLER_INCAPACITATED, TARGET_DIED, OUT_OF_REACH, DEPARTED }
    public record MeleeInput(UUID sequenceKey, Integer attackOrdinal, UUID grappleKey, HandSlot hand,
            ContestSkill skill, Position destination) { }
    public record GrappleRelation(UUID grappleKey, UUID grapplerId, UUID targetId, HandSlot hand, int reachFeet,
            UUID originatingContest, GrappleEndReason endReason, UUID endedOperationId) {
        public boolean active() { return endReason == null; }
    }
    public record MeleeContest(UUID contestKey, UUID frameKey, UUID choiceKey, Kind kind, UUID actorId, UUID targetId,
            long turnNumber, UUID sequenceKey, Integer attackOrdinal, UUID grappleKey, HandSlot hand,
            Position actorPosition, Position targetPosition, CreatureSize actorSize, CreatureSize targetSize,
            Position destination, RuleIdentity checkRule, int minimumWinningMargin, ContestSkill selectedSkill,
            Schema2CharacterCheckRules.RolledScore initiator, Schema2CharacterCheckRules.RolledScore responder,
            Schema2CharacterCheckRules.ContestOutcome outcome) { }
    public record MeleeControl(List<MeleeContest> contests, List<GrappleRelation> grapples) {
        public static final MeleeControl EMPTY = new MeleeControl(List.of(), List.of());
        public MeleeControl {
            contests = List.copyOf(contests);
            grapples = List.copyOf(grapples);
        }
    }
    public record AttackAttempt(UUID frameKey, UUID actorId, UUID targetId, UUID itemId,
            UUID sequenceKey, int attackOrdinal, boolean opportunityAttack, int firstD20, Integer secondD20,
            int selectedD20, int abilityModifier, int proficiencyBonus, int armorClass, boolean advantage,
            boolean disadvantage, boolean critical, boolean hit, List<Integer> damageDice, int damageAmount,
            UUID blessingEffectKey, Integer blessingD4, AttackStatus status, boolean initialHit, int finalArmorClass,
            MonsterAttackEvidence monsterAttack, RangedAttackEvidence rangedAttack, boolean attackerSeesTarget,
            boolean targetSeesAttacker, Cover targetCover, Position aimedPosition, boolean targetAtAimedPosition) {
        public AttackAttempt { damageDice = List.copyOf(damageDice); }
    }
    public record RangedAttackEvidence(int distanceFeet, int normalRangeFeet, int longRangeFeet, boolean closeEnemy,
            UUID ammunitionItemId) { }
    public record MonsterAttackEvidence(EntryIdentity action, int hitBonus, int reachFeet, List<UUID> packTacticsAllies,
            List<AttackDamageComponent> damageComponents, MonsterAttackOnHitEvidence onHit) {
        public MonsterAttackEvidence {
            packTacticsAllies = List.copyOf(packTacticsAllies);
            damageComponents = List.copyOf(damageComponents);
        }
    }
    public record MonsterAttackOnHitEvidence(int difficultyClass, BatchCheck savingThrow, Integer selectedD20,
            boolean alreadyProne, RuleIdentity immunitySource, OnHitOutcome outcome) {
        static OnHitOutcome resolveOutcome(BatchCheck savingThrow, boolean alreadyProne, RuleIdentity immunitySource) {
            if (savingThrow == null) return OnHitOutcome.TARGET_DEAD;
            if (savingThrow.success()) return OnHitOutcome.SAVE_SUCCEEDED;
            if (immunitySource != null) return OnHitOutcome.CONDITION_IMMUNE;
            return alreadyProne ? OnHitOutcome.ALREADY_PRONE : OnHitOutcome.PRONE_APPLIED;
        }
    }
    public record AttackDamageComponent(DamageType damageType, int diceCount, int diceSides, int modifier,
            List<Integer> dice, int rawDamage, int effectiveDamage, List<DefenseContribution> defenses) {
        public AttackDamageComponent {
            dice = List.copyOf(dice);
            defenses = List.copyOf(defenses);
        }
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
    public enum MonsterRestKind { SHORT, LONG }
    public enum MonsterRestStatus { ACTIVE, COMPLETED, INTERRUPTED }
    public record MonsterRestInput(UUID restKey, MonsterRestKind kind) {
        public MonsterRestInput { if(kind==null)throw invalid("S2_MONSTER_REST_COMMAND");if(restKey!=null)uuid(restKey); }
    }
    public record MonsterRest(UUID restKey, UUID monsterId, MonsterRestKind kind, long startedAtSecond, long completesAtSecond, MonsterRestStatus status, UUID finishedOperationId) { }
    public enum CreatureKind { CHARACTER, MONSTER }
    public enum BatchStatus { ROLLED, COMPLETED, NO_TARGETS }
    public enum CheckMode { ROLL, AUTO_FAILURE }
    public enum ConcentrationDamageStatus { NOT_REQUIRED, FORCED_ENDED, CHECK_SUCCEEDED, CHECK_FAILED }
    public record CreatureRef(CreatureKind kind, UUID creatureId) {
        public CreatureRef { if(kind==null)throw invalid("S2_COMBAT_IDENTITY");uuid(creatureId); }
    }
    public record MonsterDeployment(UUID monsterId, RuleIdentity monster, Position position, int side) {
        public MonsterDeployment { uuid(monsterId); if(!validMonsterIdentity(monster)||position==null)throw invalid("S2_COMBAT_DEPLOYMENT"); if(side<0||side>15)throw invalid("S2_COMBAT_SIDE"); }
    }
    /** Direction is one of the eight server enumerated compass cones, clockwise from east. */
    public record BreathInput(EntryIdentity action, int direction) {
        public BreathInput { if(!validMonsterActionIdentity(action))throw invalid("S2_BREATH_COMMAND");if(direction<0||direction>7)throw invalid("S2_BREATH_AREA"); }
    }
    public record MonsterAttackInput(EntryIdentity action) {
        public MonsterAttackInput {
            if (!validMonsterActionIdentity(action)) throw invalid("S2_MONSTER_ATTACK_COMMAND");
        }
    }
    public record MonsterActionUse(EntryIdentity action, boolean available) { }
    /** The sole mutable health owner for this finite monster domain; monsters die at zero HP. */
    public record MonsterInstance(UUID monsterId, RuleIdentity monster, int hitPoints, boolean dead,
            List<MonsterActionUse> actionUses) {
        public MonsterInstance { actionUses=List.copyOf(actionUses); }
    }
    public record AreaCandidate(CreatureRef creature, Position position, boolean included, String reason) { }
    public record TargetSetProof(String policy, long rosterVersion, Position origin, int direction,
            List<AreaCandidate> candidates, List<CreatureRef> targets) {
        public TargetSetProof { candidates=List.copyOf(candidates);targets=List.copyOf(targets); }
    }
    public record SharedDamageRoll(UUID rollKey, List<Integer> dice, int rawTotal, int effectiveTotal) {
        public SharedDamageRoll { dice=List.copyOf(dice); }
    }
    public record BatchCheck(int targetOrdinal, String purpose, String abilityKey, CheckMode mode,
            Integer firstD20, Integer secondD20, int abilityModifier, int proficiencyBonus,
            boolean advantage, boolean disadvantage, UUID blessingEffectKey, Integer blessingD4,
            int difficultyClass, int total, boolean success) { }
    public record DefenseContribution(RuleIdentity source, String kind) { }
    public record DamagePacket(UUID packetKey, int targetOrdinal, UUID sharedRollKey, int saveCheckIndex,
            int beforeHitPoints, int beforeTemporaryHitPoints, int maximumHitPoints,
            List<DefenseContribution> defenses, int damageAmount, int absorbedTemporaryHitPoints,
            int afterHitPoints, int afterTemporaryHitPoints, boolean dead) {
        public DamagePacket { defenses=List.copyOf(defenses); }
    }
    public record ConcentrationDamageObligation(int targetOrdinal, UUID holderSourceKey, UUID packetKey,
            int damageAmount, int difficultyClass, ConcentrationDamageStatus status, Integer checkIndex) { }
    /** A real spell producer, distinct from a monster action. Its DC contributions are retained facts. */
    public record SpellDamageSource(UUID castFrameKey, RuleIdentity spell, int slotLevel, int abilityModifier, int proficiencyBonus) {
        public int diceCount() { return slotLevel+2; }
        public int difficultyClass() { return 8+abilityModifier+proficiencyBonus; }
    }
    public record SaveDamageBatch(UUID batchKey, UUID frameKey, UUID sourceId, EntryIdentity action,
            UUID releaseOperationId, long releasedTurnNumber, TargetSetProof targetProof,
            BatchStatus status, SharedDamageRoll sharedRoll, List<BatchCheck> checks,
            List<DamagePacket> packets, List<ConcentrationDamageObligation> concentrationObligations, SpellDamageSource spellSource) {
        public SaveDamageBatch(UUID batchKey, UUID frameKey, UUID sourceId, EntryIdentity action,
                UUID releaseOperationId, long releasedTurnNumber, TargetSetProof targetProof,
                BatchStatus status, SharedDamageRoll sharedRoll, List<BatchCheck> checks,
                List<DamagePacket> packets, List<ConcentrationDamageObligation> concentrationObligations) {
            this(batchKey,frameKey,sourceId,action,releaseOperationId,releasedTurnNumber,targetProof,status,sharedRoll,checks,packets,concentrationObligations,null);
        }
        public int diceCount() { return spellSource==null?6:spellSource.diceCount(); }
        public int difficultyClass() { return spellSource==null?12:spellSource.difficultyClass(); }
        public SaveDamageBatch { checks=List.copyOf(checks);packets=List.copyOf(packets);concentrationObligations=List.copyOf(concentrationObligations); }
    }
    public record Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status,
            int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants,
            List<Initiative> initiative, List<Flow> flows, List<OngoingEffect> ongoingEffects,
            List<ReadyPreparation> readyPreparations, List<TriggerFact> triggerFacts, List<ShieldEffect> shields,
            List<ConcentrationHolder> concentrationHolders, List<EnvironmentZone> environmentZones,
            List<SuppressionCause> suppressionCauses, List<MonsterInstance> monsters, List<SaveDamageBatch> saveDamageBatches, List<MonsterRest> monsterRests,
            MeleeControl meleeControl, ObservationState observation) {
        public Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status, int round, long turnNumber,
                int activeOrdinal, long version, List<Combatant> combatants, List<Initiative> initiative, List<Flow> flows,
                List<OngoingEffect> ongoingEffects, List<ReadyPreparation> readyPreparations, List<TriggerFact> triggerFacts,
                List<ShieldEffect> shields, List<ConcentrationHolder> concentrationHolders, List<EnvironmentZone> environmentZones,
                List<SuppressionCause> suppressionCauses, List<MonsterInstance> monsters, List<SaveDamageBatch> saveDamageBatches,
                List<MonsterRest> monsterRests, MeleeControl meleeControl) {
            this(combatId,campaignId,combatRule,status,round,turnNumber,activeOrdinal,version,combatants,initiative,flows,
                    ongoingEffects,readyPreparations,triggerFacts,shields,concentrationHolders,environmentZones,suppressionCauses,
                    monsters,saveDamageBatches,monsterRests,meleeControl,ObservationState.EMPTY);
        }
        public Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status, int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants, List<Initiative> initiative, List<Flow> flows, List<OngoingEffect> ongoingEffects, List<ReadyPreparation> readyPreparations, List<TriggerFact> triggerFacts, List<ShieldEffect> shields, List<ConcentrationHolder> concentrationHolders, List<EnvironmentZone> environmentZones, List<SuppressionCause> suppressionCauses, List<MonsterInstance> monsters, List<SaveDamageBatch> saveDamageBatches, List<MonsterRest> monsterRests) {
            this(combatId,campaignId,combatRule,status,round,turnNumber,activeOrdinal,version,combatants,initiative,flows,
                    ongoingEffects,readyPreparations,triggerFacts,shields,concentrationHolders,environmentZones,suppressionCauses,
                    monsters,saveDamageBatches,monsterRests,MeleeControl.EMPTY);
        }
        public Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status, int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants, List<Initiative> initiative, List<Flow> flows, List<OngoingEffect> ongoingEffects, List<ReadyPreparation> readyPreparations, List<TriggerFact> triggerFacts, List<ShieldEffect> shields, List<ConcentrationHolder> concentrationHolders, List<EnvironmentZone> environmentZones, List<SuppressionCause> suppressionCauses, List<MonsterInstance> monsters, List<SaveDamageBatch> saveDamageBatches) {
            this(combatId,campaignId,combatRule,status,round,turnNumber,activeOrdinal,version,combatants,initiative,flows,ongoingEffects,readyPreparations,triggerFacts,shields,concentrationHolders,environmentZones,suppressionCauses,monsters,saveDamageBatches,List.of());
        }
        public Battle(UUID combatId, UUID campaignId, RuleIdentity combatRule, Status status, int round, long turnNumber, int activeOrdinal, long version, List<Combatant> combatants, List<Initiative> initiative, List<Flow> flows, List<OngoingEffect> ongoingEffects, List<ReadyPreparation> readyPreparations, List<TriggerFact> triggerFacts, List<ShieldEffect> shields, List<ConcentrationHolder> concentrationHolders, List<EnvironmentZone> environmentZones, List<SuppressionCause> suppressionCauses) {
            this(combatId,campaignId,combatRule,status,round,turnNumber,activeOrdinal,version,combatants,initiative,flows,ongoingEffects,readyPreparations,triggerFacts,shields,concentrationHolders,environmentZones,suppressionCauses,List.of(),List.of());
        }
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
        public Battle { combatants = List.copyOf(combatants); initiative = List.copyOf(initiative); flows = List.copyOf(flows); ongoingEffects = List.copyOf(ongoingEffects); readyPreparations=List.copyOf(readyPreparations);triggerFacts=List.copyOf(triggerFacts);shields=List.copyOf(shields);concentrationHolders=List.copyOf(concentrationHolders);environmentZones=List.copyOf(environmentZones);suppressionCauses=List.copyOf(suppressionCauses);monsters=List.copyOf(monsters);saveDamageBatches=List.copyOf(saveDamageBatches);monsterRests=List.copyOf(monsterRests); if (meleeControl == null || observation == null) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID"); }
        public UUID activeActor() { return initiative.isEmpty() ? null : initiative.get(activeOrdinal).characterId(); }
        public boolean surprised(UUID actorId) {
            return status == Status.ACTIVE && round == 1 && initiative.stream().anyMatch(value ->
                    value.surprised() && value.ordinal() >= activeOrdinal && value.characterId().equals(actorId));
        }
        public Flow activeFlow() { return flows.stream().filter(f -> f.status() == FlowStatus.ACTIVE).findFirst().orElse(null); }
    }
    public record Roll(String purpose, UUID subjectId, int sides, int value) { }
    public record HealthChange(UUID characterId, State before, State after, UUID frameKey,
            int damageAmount, boolean critical, HealthResult healthResult, boolean deathSave,
            boolean timeAdvance, int seconds, RuleIdentity resource, long resourceAmount, boolean healing,
            com.dndtool.service.Schema2CharacterRuntime.HealthInput environmentInput) {
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
    public record AmmunitionUse(UUID characterId, State before, State after, UUID frameKey, UUID itemId) { }
    public record CombatFact(String kind, UUID subjectId, UUID referenceKey, String reason) { }
    public record Resolution(Battle after, List<HealthChange> healthChanges, List<Roll> rolls, List<CombatFact> facts,
            List<AmmunitionUse> ammunitionUses) {
        public Resolution(Battle after, List<HealthChange> healthChanges, List<Roll> rolls, List<CombatFact> facts) {
            this(after, healthChanges, rolls, facts, List.of());
        }
        public Resolution {
            healthChanges = List.copyOf(healthChanges);
            rolls = List.copyOf(rolls);
            facts = List.copyOf(facts);
            ammunitionUses = List.copyOf(ammunitionUses);
        }
    }
    private static boolean validMonsterIdentity(RuleIdentity rule) {
        return rule!=null&&"adventure.monster".equals(rule.ruleType())&&rule.moduleKey()!=null&&rule.moduleKey().matches("[a-z][a-z0-9_]{0,63}")
                &&rule.ruleKey()!=null&&rule.ruleKey().matches("[a-z][a-z0-9_.]{0,127}")&&rule.ruleKey().matches("monster[.][a-z][a-z0-9_]*(?:[.][a-z0-9_]+)*");
    }
    private static boolean validMonsterActionIdentity(EntryIdentity entry) {
        return entry!=null&&validMonsterIdentity(entry.rule())&&"actions".equals(entry.collectionKey())&&entry.moduleKey()!=null&&entry.moduleKey().matches("[a-z][a-z0-9_]{0,63}")
                &&entry.entryKey()!=null&&entry.entryKey().matches("[a-z][a-z0-9_]{0,63}");
    }
    static void uuid(UUID id) { if (id == null || id.version() != 4 || id.variant() != 2) throw invalid("S2_COMBAT_IDENTITY"); }
    static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
