package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2CombatState.Command;
import com.dndtool.service.Schema2CombatState.Stage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntUnaryOperator;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Closed ordinary-melee combat consumer. All preflight is pure and precedes the first die. */
public final class Schema2CombatRules {
    public static final int MAX_COMBATANTS = 16, MAX_FLOWS = 128, MAX_PATH = 64, MAX_FACTS = 4096;
    public static final int START_TIME_HEADROOM_MINUTES = (JdbcSchema2CombatRuntimeRepository.MAX_EVENTS * 6 + 59) / 60 + 240;
    public record Context(ProductionSchema2Projection material, InitialPlan initial, State character) { }
    public static final class Prepared {
        private final UUID operationId, combatId, campaignId;
        private final Map<UUID,Context> roster;
        private final Battle before;
        private final Command command;
        private Prepared(UUID operationId, UUID combatId, UUID campaignId, Map<UUID,Context> roster, Battle before, Command command) {
            this.operationId = operationId; this.combatId = combatId; this.campaignId = campaignId;
            this.roster = Map.copyOf(roster); this.before = before; this.command = command;
        }
        public Battle before() { return before; }
        public Command command() { return command; }
        public int requiredSuffixEvents() {
            if (command.kind() == Kind.MOVE) return command.path().size() * roster.size() + 1;
            Flow flow = before == null ? null : before.activeFlow();
            if (flow == null) return 0;
            Window window = lastWindow(flow);
            int pending = (int)window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).count();
            int futureCells = Math.max(0,flow.movement().path().size()-flow.movement().cursor()-1);
            // The current command consumes one of these obligations. The final margin covers stop/completion.
            return pending + 1 + futureCells * roster.size();
        }
        public Set<UUID> possibleHealthTargets() {
            if (command.kind() == Kind.START) return roster.entrySet().stream().filter(e->e.getValue().character().hitPoints()==0 && !e.getValue().character().health().dead() && !e.getValue().character().health().stable()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (command.kind() == Kind.END_COMBAT) return Set.copyOf(roster.keySet());
            if (command.kind() == Kind.ATTACK) return Set.of(command.targetId());
            if (command.kind() == Kind.DECLARE_OPPORTUNITY_ATTACK || command.kind() == Kind.MOVE || before != null && before.activeFlow()!=null) return Set.of(command.kind()==Kind.MOVE?command.actorId():before.activeFlow().movement().actorId());
            if (command.kind() == Kind.END_TURN) return before.activeOrdinal()+1==before.initiative().size()?Set.copyOf(roster.keySet()):Set.of(before.initiative().get(before.activeOrdinal()+1).characterId());
            return Set.of();
        }
    }
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();
    private final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
    private final Schema2CharacterHealthRules health = new Schema2CharacterHealthRules();

    public Prepared preflight(UUID operationId, UUID combatId, UUID campaignId, Map<UUID,Context> roster, Battle before, Command command) {
        uuid(operationId); uuid(combatId); uuid(campaignId);
        if (operationId.equals(combatId) || operationId.equals(campaignId) || combatId.equals(campaignId)) throw invalid("S2_COMBAT_IDENTITY");
        if (roster == null || roster.size() < 2 || roster.size() > MAX_COMBATANTS || command == null) throw invalid("S2_COMBAT_ROSTER");
        validateContexts(campaignId, roster);
        validateCommand(command);
        if (before != null) {
            validateState(before);
            if (!before.combatId().equals(combatId) || !before.campaignId().equals(campaignId)
                    || !roster.keySet().equals(before.combatants().stream().map(Combatant::characterId).collect(java.util.stream.Collectors.toSet())))
                throw invalid("ACTION_FLOW_TARGET_MISMATCH");
            if (before.status() != Status.ACTIVE) throw invalid("S2_COMBAT_ENDED");
            if (before.version() >= MAX_FACTS - 1) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            boolean continuation = Set.of(Kind.PASS_REACTION,Kind.DECLARE_OPPORTUNITY_ATTACK,Kind.ADVANCE_WINDOW,Kind.CONTINUE_MOVEMENT).contains(command.kind());
            if (before.activeFlow() != null && !continuation) throw invalid("ACTION_FLOW_BUSY");
            if (before.activeFlow() == null && continuation) throw invalid("ACTION_FLOW_TARGET_MISMATCH");
            if (command.kind() == Kind.START) throw invalid("S2_COMBAT_EXISTS");
        } else if (command.kind() != Kind.START) throw invalid("S2_COMBAT_NOT_FOUND");
        Prepared prepared = new Prepared(operationId,combatId,campaignId,roster,before,command);
        // The minimum branch checks every deterministic dependency and permitted future boundary.
        preflightIntent(prepared);
        return prepared;
    }

    public Resolution resolve(Prepared prepared, IntUnaryOperator dice) {
        Objects.requireNonNull(prepared); Objects.requireNonNull(dice);
        return execute(prepared,new Draws(dice));
    }

    /** Conservative finite command frontier, including explicit opportunity decisions and owner returns. */
    public int requiredSuffixEvents(Prepared prepared) {
        return prepared.requiredSuffixEvents();
    }

    /** Rebuilds from the original dependencies and retained dice; never calls a random source. */
    public void verify(UUID operationId, UUID combatId, UUID campaignId, Map<UUID,Context> roster,
            Battle before, Command command, Resolution saved) {
        if (saved == null) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        Prepared prepared = preflight(operationId,combatId,campaignId,roster,before,command);
        Draws draws = new Draws(saved.rolls());
        Resolution rebuilt = execute(prepared,draws);
        draws.complete();
        if (!rebuilt.equals(saved)) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
    }

    private void validateContexts(UUID campaign, Map<UUID,Context> roster) {
        Schema2RulesBinding common = null;
        Long minute=null;Integer second=null;
        for (var entry : roster.entrySet()) {
            uuid(entry.getKey()); Context context = Objects.requireNonNull(entry.getValue());
            State character = context.character(); InitialPlan initial = context.initial();
            if (context.material() == null || character == null || initial == null || !entry.getKey().equals(character.characterId())
                    || !campaign.equals(character.campaignId()) || !campaign.equals(initial.campaignId())
                    || !character.characterId().equals(initial.characterId()) || !character.identity().equals(initial.character().identity()))
                throw invalid("S2_COMBAT_CHARACTER_BINDING");
            Schema2RulesBinding binding = Schema2RulesBinding.from(context.material());
            binding.verify(context.material()); initial.character().binding().verify(context.material());
            if (common != null && !common.equals(binding)) throw invalid("S2_COMBAT_RULE_BINDING");
            common = binding;
            if(minute!=null && (minute!=character.rest().minute() || second!=character.rest().second()))throw invalid("S2_COMBAT_CLOCK_MISMATCH");
            minute=character.rest().minute();second=character.rest().second();
            growth.validateState(context.material(),character); rests.validateState(context.material(),character);
            equipment.validateState(context.material(),character); health.validateState(character);
            Schema2CharacterEquipmentRules.requireCompleteScope(context.material(),character);
            Schema2CharacterEquipmentRules.requireNoUnownedEffects(character);
            if (character.rest().active() != null) throw invalid("S2_COMBAT_REST_ACTIVE");
            combatRule(context); speed(context);
            // Every held weapon is a possible OA source; a missing reader cannot be silently omitted.
            for (EquipmentItem item : character.equipment()) if (preparedItem(item) && itemDefinition(context,item).kind() == EquipmentKind.WEAPON) weapon(context,item.itemId());
        }
    }

    private void validateCommand(Command command) {
        boolean start = command.kind() == Kind.START, move = command.kind() == Kind.MOVE;
        boolean attack = command.kind() == Kind.ATTACK, declare = command.kind() == Kind.DECLARE_OPPORTUNITY_ATTACK;
        boolean reaction = declare || command.kind() == Kind.PASS_REACTION || command.kind() == Kind.ADVANCE_WINDOW, continuation = command.kind() == Kind.CONTINUE_MOVEMENT;
        if (start ? command.actorId() != null : command.actorId() == null) throw invalid("S2_COMBAT_COMMAND");
        if ((command.targetId() != null) != attack || (command.itemId() != null) != (attack || declare)
                || (move ? command.path().isEmpty() || command.path().size() > MAX_PATH : !command.path().isEmpty())
                || (start ? command.deployments().size() < 2 || command.deployments().size() > MAX_COMBATANTS : !command.deployments().isEmpty())
                || (!start && !command.tieBreakOrder().isEmpty())
                || ((command.flowKey() != null) != (reaction || continuation)) || ((command.leafKey() != null) != (reaction || continuation))
                || ((command.windowKey() != null) != (reaction || continuation)) || ((command.opportunityKey() != null) != reaction))
            throw invalid("S2_COMBAT_COMMAND");
        for (UUID id : Arrays.asList(command.actorId(),command.targetId(),command.itemId(),command.flowKey(),command.leafKey(),command.windowKey(),command.opportunityKey())) if (id != null) uuid(id);
    }

    private RuleIdentity combatRule(Context context) {
        var rows = context.material().projection().rules().stream().filter(FinalCombat.class::isInstance).map(FinalCombat.class::cast).filter(FinalCombat::enabled).toList();
        if (rows.size() != 1 || !rows.getFirst().definition().algorithm().equals("SRD_2014_COMBAT_V1")) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return rows.getFirst().identity();
    }
    private int speed(Context context) {
        var sources = context.initial().character().sources();
        var rows = context.material().projection().rules().stream().filter(FinalMovement.class::isInstance).map(FinalMovement.class::cast)
                .filter(row -> row.enabled() && (row.definition().owner().equals(sources.race()) || row.definition().owner().equals(sources.subrace()))).toList();
        if (rows.size() != 1 || !rows.getFirst().definition().algorithm().equals("SRD_2014_WALKING_SPEED_V1")) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return Math.max(0,rows.getFirst().definition().walkSpeedFeet() - equipment.derive(context.material(),context.character()).speedPenalty());
    }
    private ItemDefinition itemDefinition(Context context, EquipmentItem instance) {
        var rows = context.material().projection().rules().stream().filter(FinalItem.class::isInstance).map(FinalItem.class::cast)
                .filter(row -> row.enabled() && row.identity().equals(instance.item())).toList();
        if (rows.size() != 1) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return rows.getFirst().definition();
    }
    private WeaponAttackDefinition weapon(Context context, UUID itemId) {
        EquipmentItem instance = context.character().equipment().stream().filter(item -> item.itemId().equals(itemId) && preparedItem(item))
                .findFirst().orElseThrow(() -> invalid("S2_COMBAT_WEAPON_NOT_PREPARED"));
        if (itemDefinition(context,instance).kind() != EquipmentKind.WEAPON) throw invalid("S2_COMBAT_WEAPON_NOT_PREPARED");
        var rows = context.material().projection().rules().stream().filter(FinalWeaponAttack.class::isInstance).map(FinalWeaponAttack.class::cast)
                .filter(row -> row.enabled() && row.definition().item().equals(instance.item())).toList();
        if (rows.size() != 1 || !rows.getFirst().definition().algorithm().equals("SRD_2014_MELEE_WEAPON_ATTACK_V1")) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return rows.getFirst().definition();
    }
    private static boolean preparedItem(EquipmentItem item) { return Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(item.location()); }

    private void preflightIntent(Prepared p) {
        Command command = p.command;
        if (command.kind() == Kind.START) {
            for(Context context:p.roster.values())
                if(context.character().rest().minute()>Schema2CharacterRestRules.MAX_MINUTE-START_TIME_HEADROOM_MINUTES)
                    throw invalid("S2_COMBAT_TIME_CAPACITY");
            Set<UUID> ids = new HashSet<>(); Set<Position> positions = new HashSet<>(); Set<Integer> sides = new HashSet<>();
            for (Deployment deployment : command.deployments()) {
                if (!ids.add(deployment.characterId()) || !positions.add(deployment.position())) throw invalid("S2_COMBAT_DEPLOYMENT");
                sides.add(deployment.side());
                Context context = p.roster.get(deployment.characterId());
                if (context == null || context.character().health().dead()) throw invalid("S2_COMBAT_DEPLOYMENT");
                if(context.character().hitPoints()==0 && context.character().equipment().stream().anyMatch(Schema2CombatRules::preparedItem))throw invalid("S2_COMBAT_UNCONSCIOUS_EQUIPMENT");
                prepareTurn(context);
            }
            if (!ids.equals(p.roster.keySet()) || sides.size() < 2 || command.tieBreakOrder().size() != ids.size()
                    || !new HashSet<>(command.tieBreakOrder()).equals(ids)) throw invalid("S2_COMBAT_DEPLOYMENT");
            return;
        }
        Combatant actor = combatant(p.before,command.actorId());
        boolean reaction = Set.of(Kind.PASS_REACTION,Kind.DECLARE_OPPORTUNITY_ATTACK,Kind.ADVANCE_WINDOW,Kind.CONTINUE_MOVEMENT).contains(command.kind());
        if (reaction) {
            Flow flow = p.before.activeFlow();
            if (!flow.flowKey().equals(command.flowKey()) || !Objects.equals(flow.leafFrameKey(),command.leafKey())) throw invalid("ACTION_FLOW_TARGET_MISMATCH");
            Window window = lastWindow(flow);
            if (!window.windowKey().equals(command.windowKey())) throw invalid("ACTION_FLOW_TARGET_MISMATCH");
            if (command.kind() == Kind.CONTINUE_MOVEMENT) {
                if (!flow.movement().actorId().equals(command.actorId())) throw invalid("ACTION_FLOW_TARGET_MISMATCH");
                if (window.status() != WindowStatus.CLOSED) throw invalid("ACTION_FLOW_STAGE_INVALID");
                return;
            }
            if (window.status() != WindowStatus.OPEN) throw invalid("ACTION_FLOW_STAGE_INVALID");
            Opportunity opportunity = currentOpportunity(window);
            if (!opportunity.opportunityKey().equals(command.opportunityKey()) || !opportunity.actorId().equals(command.actorId())) throw invalid("REACTION_NOT_CURRENT");
            if(command.kind()==Kind.ADVANCE_WINDOW && eligible(p.before,p.roster,flow,window,opportunity))throw invalid("REACTION_OPTION_INVALID");
            if (command.kind() == Kind.DECLARE_OPPORTUNITY_ATTACK) {
                if (!opportunity.allowedItems().contains(command.itemId()) || !eligible(p.before,p.roster,flow,window,opportunity)) throw invalid("REACTION_OPTION_INVALID");
                prepareAttack(p,actor,combatant(p.before,flow.movement().actorId()),command.itemId());
                if (flow.frames().size() + 2 > 1024) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            }
            return;
        }
        if (!command.actorId().equals(p.before.activeActor())) throw invalid("S2_COMBAT_NOT_CURRENT_TURN");
        if (command.kind() == Kind.END_TURN) {
            int next = (p.before.activeOrdinal() + 1) % p.before.initiative().size();
            if (p.before.turnNumber() >= 1000000) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            Map<UUID,Context> turnRoster=next==0?afterRound(p.roster):p.roster;
            prepareTurn(turnRoster.get(p.before.initiative().get(next).characterId())); return;
        }
        if (command.kind() == Kind.END_COMBAT) {
            if(p.before.activeOrdinal()!=p.before.initiative().size()-1)throw invalid("S2_COMBAT_ROUND_NOT_CLOSED");
            afterRound(p.roster);return;
        }
        active(p.roster.get(actor.characterId()));
        switch (command.kind()) {
            case DASH, DISENGAGE, DODGE, BEGIN_ATTACK -> {
                if (!actor.actionAvailable()) throw invalid("S2_COMBAT_ACTION_UNAVAILABLE");
                if (command.kind() == Kind.BEGIN_ATTACK && actor.sequence() != null && !actor.sequence().closed()) throw invalid("S2_COMBAT_SEQUENCE_ACTIVE");
            }
            case ATTACK -> {
                if (actor.sequence() == null || actor.sequence().closed() || actor.sequence().performed() + actor.sequence().forfeited() >= actor.sequence().limit()) throw invalid("S2_COMBAT_SEQUENCE_UNAVAILABLE");
                prepareAttack(p,actor,combatant(p.before,command.targetId()),command.itemId());
                if (p.before.flows().size() >= MAX_FLOWS) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            }
            case FORFEIT_ATTACKS -> { if (actor.sequence() == null || actor.sequence().closed()) throw invalid("S2_COMBAT_SEQUENCE_UNAVAILABLE"); }
            case STAND_UP -> {
                int speed=speed(p.roster.get(actor.characterId()));
                if(!actor.prone() || speed==0 || 2*(speed+actor.movementBonus())-actor.movementSpentHalfFeet()<speed)throw invalid("S2_COMBAT_CANNOT_STAND");
            }
            case MOVE -> prepareMovement(p,actor);
            default -> throw invalid("S2_COMBAT_COMMAND");
        }
    }

    private void prepareTurn(Context context) {
        State character = context.character();
        if (character.hitPoints() == 0 && !character.health().stable() && !character.health().dead()) health.prepare(context.material(),character,deathCommand(context));
    }
    private JdbcSchema2CharacterRuntimeRepository.Command deathCommand(Context context) {
        var owners = context.material().projection().rules().stream().filter(FinalHealth.class::isInstance).map(FinalHealth.class::cast).filter(FinalHealth::enabled).toList();
        if (owners.size() != 1) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return new JdbcSchema2CharacterRuntimeRepository.Command("DEATH_SAVE_RESOLVED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,
                new HealthInput(owners.getFirst().identity(),null,null,"Combat turn begins"));
    }
    private void prepareAttack(Prepared p, Combatant actor, Combatant target, UUID itemId) {
        if (actor.characterId().equals(target.characterId()) || actor.side() == target.side()) throw invalid("S2_COMBAT_TARGET");
        Context source = p.roster.get(actor.characterId()), destination = p.roster.get(target.characterId());
        active(source); WeaponAttackDefinition weapon = weapon(source,itemId);
        if (actor.position().distanceFeet(target.position()) > weapon.reachFeet()) throw invalid("S2_COMBAT_OUT_OF_REACH");
        health.prepareDamage(destination.character());
        equipment.derive(destination.material(),destination.character());
        if ((long)weapon.diceCount() * 2 * weapon.diceSides() + 10 > 65535) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
    }
    private void prepareMovement(Prepared p, Combatant actor) {
        if (p.before.flows().size() >= MAX_FLOWS) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
        if(actor.prone())throw invalid("S2_COMBAT_PRONE");
        int allowance = 2*(speed(p.roster.get(actor.characterId())) + actor.movementBonus()) - actor.movementSpentHalfFeet();
        if (p.command.path().size() * 10 > allowance) throw invalid("S2_COMBAT_MOVEMENT_ALLOWANCE");
        Position prior = actor.position(); int windows = 0, opportunities = 0; Set<UUID> possibleAttackers = new HashSet<>();
        for (Position position : p.command.path()) {
            if (position.distanceFeet(prior) != 5 || p.before.combatants().stream().anyMatch(other -> !other.characterId().equals(actor.characterId()) && other.position().equals(position)))
                throw invalid("S2_COMBAT_PATH");
            var candidates = candidates(p.before,p.roster,actor,prior,position);
            if (!candidates.isEmpty()) windows++;
            opportunities += candidates.size(); possibleAttackers.addAll(candidates.keySet()); prior = position;
        }
        if (windows > 64 || 1 + opportunities * 2 > 1024) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
        if (!possibleAttackers.isEmpty()) {
            health.prepareDamage(p.roster.get(actor.characterId()).character());
            if (p.roster.get(actor.characterId()).character().version() + possibleAttackers.size() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS)
                throw invalid("ACTION_FLOW_RESERVATION_INSUFFICIENT");
        }
    }

    private Resolution execute(Prepared p, Draws dice) {
        List<CombatFact> facts = new ArrayList<>(); List<HealthChange> changes = new ArrayList<>();
        Battle battle = p.before;
        if (p.command.kind() == Kind.START) {
            List<Initiative> initiatives = new ArrayList<>();
            for (Deployment deployment : p.command.deployments()) {
                int dex = modifier(p.roster.get(deployment.characterId()).character(),"ability.dexterity");
                int d20 = dice.draw("INITIATIVE",deployment.characterId(),20);
                Context initiativeContext=p.roster.get(deployment.characterId());
                if(equipment.derive(initiativeContext.material(),initiativeContext.character()).physicalCheckDisadvantage())
                    d20=Math.min(d20,dice.draw("INITIATIVE_SECOND",deployment.characterId(),20));
                initiatives.add(new Initiative(deployment.characterId(),d20,dex,d20 + dex,0));
            }
            initiatives.sort(Comparator.comparingInt(Initiative::total).reversed().thenComparingInt(i -> p.command.tieBreakOrder().indexOf(i.characterId())));
            List<Initiative> ordered = new ArrayList<>();
            for (int i=0;i<initiatives.size();i++) { var value = initiatives.get(i); ordered.add(new Initiative(value.characterId(),value.d20(),value.dexterityModifier(),value.total(),i)); }
            List<Combatant> combatants = p.command.deployments().stream().sorted(Comparator.comparing(d->d.characterId().toString())).map(d -> new Combatant(d.characterId(),d.position(),d.side(),false,true,0,0,false,false,null,p.roster.get(d.characterId()).character().hitPoints()==0)).toList();
            battle = new Battle(p.combatId,p.campaignId,combatRule(p.roster.values().iterator().next()),Status.ACTIVE,1,1,0,0,combatants,ordered,List.of());
            battle = beginTurn(p,battle,dice,changes,facts);
            facts.add(new CombatFact("COMBAT_STARTED",battle.activeActor(),p.combatId,null));
        } else {
            Combatant actor = combatant(battle,p.command.actorId());
            switch (p.command.kind()) {
                case END_TURN -> {
                    battle = replaceActor(battle,closeSequence(actor));
                    int next = (battle.activeOrdinal() + 1) % battle.initiative().size();
                    battle = copy(battle,battle.status(),battle.round() + (next == 0 ? 1 : 0),battle.turnNumber() + 1,next,battle.combatants(),battle.flows());
                    Prepared turnPrepared=p;
                    if(next==0) {
                        Map<UUID,Context> contexts=recordRoundTime(p,battle,changes,facts);
                        turnPrepared=new Prepared(p.operationId,p.combatId,p.campaignId,contexts,p.before,p.command);
                    }
                    battle = beginTurn(turnPrepared,battle,dice,changes,facts);
                }
                case END_COMBAT -> {
                    recordRoundTime(p,battle,changes,facts);
                    battle=copy(battle,Status.ENDED,battle.round(),battle.turnNumber(),battle.activeOrdinal(),battle.combatants().stream().map(Schema2CombatRules::closeSequence).toList(),battle.flows());
                }
                case DASH, DISENGAGE, DODGE, BEGIN_ATTACK -> {
                    AttackSequence sequence = p.command.kind() == Kind.BEGIN_ATTACK ? new AttackSequence(key(p.operationId,"sequence"),1,0,0,false) : actor.sequence();
                    battle = replaceActor(battle,new Combatant(actor.characterId(),actor.position(),actor.side(),false,actor.reactionAvailable(),actor.movementSpentHalfFeet(),
                            actor.movementBonus() + (p.command.kind() == Kind.DASH ? speed(p.roster.get(actor.characterId())) : 0),
                            actor.disengaged() || p.command.kind() == Kind.DISENGAGE,actor.dodging() || p.command.kind() == Kind.DODGE,sequence,actor.prone()));
                }
                case STAND_UP -> battle=replaceActor(battle,new Combatant(actor.characterId(),actor.position(),actor.side(),actor.actionAvailable(),actor.reactionAvailable(),actor.movementSpentHalfFeet()+speed(p.roster.get(actor.characterId())),actor.movementBonus(),actor.disengaged(),actor.dodging(),actor.sequence(),false));
                case FORFEIT_ATTACKS -> battle = replaceActor(battle,closeSequence(actor));
                case ATTACK -> {
                    UUID flowKey = key(p.operationId,"flow"), frameKey = key(p.operationId,"attack");
                    var attempt = attack(p,battle,actor,combatant(battle,p.command.targetId()),p.command.itemId(),frameKey,false,dice,changes);
                    AttackSequence old = actor.sequence();
                    battle = replaceActor(battle,withSequence(actor,new AttackSequence(old.sequenceKey(),old.limit(),old.performed()+1,old.forfeited(),old.performed()+old.forfeited()+1==old.limit())));
                    battle = reconcileInactivity(battle,changes);
                    Frame frame = new Frame(frameKey,null,FrameKind.ATTACK,frameKey,Invocation.ROOT,FrameStatus.TERMINAL,Stage.ATTACK_RESOLVED,null,null);
                    battle = appendFlow(battle,new Flow(flowKey,frameKey,null,FlowStatus.COMPLETED,List.of(frame),List.of(),null,List.of(attempt)));
                }
                case MOVE -> {
                    UUID flowKey = key(p.operationId,"flow"), frameKey = key(p.operationId,"movement-frame"), moveKey = key(p.operationId,"movement");
                    Frame frame = new Frame(frameKey,null,FrameKind.MOVEMENT,moveKey,Invocation.ROOT,FrameStatus.RUNNABLE,Stage.MOVEMENT_CONTINUE,null,null);
                    Flow flow = new Flow(flowKey,frameKey,frameKey,FlowStatus.ACTIVE,List.of(frame),List.of(),new Movement(moveKey,actor.characterId(),actor.position(),p.command.path(),0,0,MovementStatus.PENDING,null),List.of());
                    battle = appendFlow(battle,flow); battle = advanceMovement(p,battle,flow,facts);
                }
                case PASS_REACTION, DECLARE_OPPORTUNITY_ATTACK, ADVANCE_WINDOW, CONTINUE_MOVEMENT -> battle = continueFlow(p,battle,dice,changes,facts);
                default -> throw invalid("S2_COMBAT_COMMAND");
            }
            battle = new Battle(battle.combatId(),battle.campaignId(),battle.combatRule(),battle.status(),battle.round(),battle.turnNumber(),battle.activeOrdinal(),p.before.version()+1,battle.combatants(),battle.initiative(),battle.flows());
            facts.add(new CombatFact(p.command.kind().name(),p.command.actorId(),p.combatId,null));
        }
        validateState(battle);
        return new Resolution(battle,changes,dice.actual,facts);
    }

    private Battle beginTurn(Prepared p, Battle battle, Draws dice, List<HealthChange> changes, List<CombatFact> facts) {
        Combatant actor = combatant(battle,battle.activeActor()); Context context = p.roster.get(actor.characterId()); State current = context.character();
        if (current.hitPoints() == 0 && !current.health().dead() && !current.health().stable()) {
            var prepared = health.prepare(context.material(),current,deathCommand(context));
            int d20 = dice.draw("TURN_DEATH_SAVE",actor.characterId(),20);
            Integer recovery = prepared.needsRecoveryD4(d20) ? dice.draw("STABLE_RECOVERY",actor.characterId(),4) : null;
            var resolved = health.resolve(prepared,List.of(),d20,recovery);
            UUID turnKey = key(p.operationId,"turn-start");
            changes.add(new HealthChange(actor.characterId(),current,resolved.state(),turnKey,0,false,resolved.result(),true));
            current = resolved.state(); facts.add(new CombatFact("TURN_DEATH_SAVE_RESOLVED",actor.characterId(),turnKey,null));
        }
        return replaceActor(battle,new Combatant(actor.characterId(),actor.position(),actor.side(),current.hitPoints()>0 && !current.health().dead(),true,0,0,false,false,null,actor.prone() || current.hitPoints()==0));
    }
    private static Map<UUID,Context> afterRound(Map<UUID,Context> roster) {
        Map<UUID,Context> result=new HashMap<>();
        for(var entry:roster.entrySet()) {Context c=entry.getValue();result.put(entry.getKey(),new Context(c.material(),c.initial(),Schema2CombatTimeRules.advanceSeconds(c.material(),c.character(),6)));}
        return Map.copyOf(result);
    }
    private static Map<UUID,Context> recordRoundTime(Prepared p,Battle battle,List<HealthChange> changes,List<CombatFact> facts) {
        Map<UUID,Context> contexts=afterRound(p.roster);
        for(Combatant member:battle.combatants()) {
            State before=p.roster.get(member.characterId()).character(),after=contexts.get(member.characterId()).character();
            UUID timeKey=key(p.operationId,"round-time-"+member.characterId());
            changes.add(new HealthChange(member.characterId(),before,after,timeKey,0,false,
                    new HealthResult(List.of(),null,null,0,(int)Math.max(0,after.hitPoints()-before.hitPoints())),false,true,6));
        }
        facts.add(new CombatFact("ROUND_TIME_ADVANCED",null,p.combatId,null));return contexts;
    }

    private AttackAttempt attack(Prepared p, Battle battle, Combatant actor, Combatant target, UUID itemId, UUID frameKey, boolean opportunity, Draws dice, List<HealthChange> changes) {
        Context source = p.roster.get(actor.characterId()), destination = p.roster.get(target.characterId());
        WeaponAttackDefinition profile = weapon(source,itemId);
        int ability = modifier(source.character(),"ability.strength");
        if (profile.abilityPolicy() == WeaponAbilityPolicy.FINESSE) ability = Math.max(ability,modifier(source.character(),"ability.dexterity"));
        EquipmentItem instance = source.character().equipment().stream().filter(i -> i.itemId().equals(itemId)).findFirst().orElseThrow();
        RuleIdentity proficiency = itemDefinition(source,instance).proficiency();
        int pb = source.character().proficiencies().stream().anyMatch(f -> f.target().equals(proficiency)) ? source.character().proficiencyBonus() : 0;
        int distance=actor.position().distanceFeet(target.position());
        boolean advantage = destination.character().hitPoints() == 0 || target.prone() && distance<=5;
        boolean disadvantage = equipment.derive(source.material(),source.character()).physicalCheckDisadvantage()
                || target.dodging() && destination.character().hitPoints() > 0 && speed(destination) > 0
                || target.prone() && distance>5 || actor.prone();
        int first = dice.draw("ATTACK_D20",actor.characterId(),20);
        Integer second = advantage != disadvantage ? dice.draw("ATTACK_D20_SECOND",actor.characterId(),20) : null;
        int selected = second == null ? first : advantage ? Math.max(first,second) : Math.min(first,second);
        int ac = equipment.derive(destination.material(),destination.character()).armorClass();
        boolean hit = selected == 20 || selected != 1 && selected + ability + pb >= ac;
        boolean critical = hit && (selected == 20 || destination.character().hitPoints() == 0 && actor.position().distanceFeet(target.position()) <= 5);
        List<Integer> damageDice = new ArrayList<>(); int amount = 0;
        if (hit) {
            for (int i=0;i<profile.diceCount()*(critical?2:1);i++) damageDice.add(dice.draw("DAMAGE",actor.characterId(),profile.diceSides()));
            amount = Math.max(0,damageDice.stream().mapToInt(Integer::intValue).sum()+ability);
            if (amount > 0) {
                var result = health.applyDamage(destination.character(),amount,critical);
                State after=result.state().hitPoints()==0 ? Schema2CombatEquipmentFacts.drop(result.state(),battle.combatId(),target.position()):result.state();
                changes.add(new HealthChange(target.characterId(),destination.character(),after,frameKey,amount,critical,
                        new HealthResult(List.of(),null,null,amount,result.appliedAmount()),false));
            }
        }
        return new AttackAttempt(frameKey,actor.characterId(),target.characterId(),itemId,opportunity?null:actor.sequence().sequenceKey(),
                opportunity?0:actor.sequence().performed(),opportunity,first,second,selected,ability,pb,ac,advantage,disadvantage,critical,hit,damageDice,amount);
    }

    private Battle advanceMovement(Prepared p, Battle battle, Flow flow, List<CombatFact> facts) {
        Movement move = flow.movement(); Combatant actor = combatant(battle,move.actorId()); Context context = p.roster.get(move.actorId());
        if (context.character().hitPoints() == 0 || context.character().health().dead() || actor.prone() || 2*(speed(context)+actor.movementBonus()) <= actor.movementSpentHalfFeet())
            return stopMovement(battle,flow,"ACTOR_INACTIVE_OR_NO_MOVEMENT",facts);
        Position expected = move.cursor() == 0 ? move.origin() : move.path().get(move.cursor()-1);
        if (!actor.position().equals(expected)) return stopMovement(battle,flow,"POSITION_CHANGED",facts);
        int cursor = move.cursor(), spent = move.spent();
        while (cursor < move.path().size()) {
            actor = combatant(battle,move.actorId()); Position next = move.path().get(cursor);
            if (2*(speed(context)+actor.movementBonus())-actor.movementSpentHalfFeet()<10 || actor.position().distanceFeet(next)!=5
                    || battle.combatants().stream().anyMatch(other -> !other.characterId().equals(move.actorId()) && other.position().equals(next)))
                return stopMovement(battle,withMovement(flow,new Movement(move.movementKey(),move.actorId(),move.origin(),move.path(),cursor,spent,MovementStatus.PENDING,null)),"SUFFIX_UNAVAILABLE",facts);
            int boundary = cursor;
            boolean consumed = flow.windows().stream().anyMatch(w -> w.boundaryIndex()==boundary && w.status()==WindowStatus.CONSUMED);
            var options = consumed ? Map.<UUID,List<UUID>>of() : candidates(battle,p.roster,actor,actor.position(),next);
            if (!options.isEmpty()) {
                int ordinal = flow.windows().size(); UUID windowKey=key(flow.flowKey(),"window-"+ordinal);
                List<Opportunity> opportunities = new ArrayList<>(); List<Frame> frames = new ArrayList<>(flow.frames());
                for (Initiative initiative : battle.initiative()) if (options.containsKey(initiative.characterId())) {
                    int priority=opportunities.size(); UUID opportunity=key(windowKey,"opportunity-"+priority);
                    opportunities.add(new Opportunity(opportunity,initiative.characterId(),priority,options.get(initiative.characterId()),OpportunityStatus.PENDING,null,null,null));
                }
                Opportunity first=withSelection(opportunities.getFirst()); opportunities.set(0,first); frames=replaceFrame(frames,flow.rootFrameKey(),FrameStatus.WAITING_WINDOW,Stage.MOVEMENT_LEAVE_REACH_BEFORE,windowKey,null);
                frames.add(selection(flow,windowKey,first));
                List<Window> windows=new ArrayList<>(flow.windows()); windows.add(new Window(windowKey,flow.rootFrameKey(),ordinal,cursor,WindowStatus.OPEN,opportunities));
                Flow waiting=new Flow(flow.flowKey(),flow.rootFrameKey(),first.selectionFrameKey(),FlowStatus.ACTIVE,frames,windows,
                        new Movement(move.movementKey(),move.actorId(),move.origin(),move.path(),cursor,spent,MovementStatus.PENDING,null),flow.attacks());
                facts.add(new CombatFact("MOVEMENT_LEAVE_REACH_BEFORE",move.actorId(),windowKey,null));
                return replaceFlow(battle,waiting);
            }
            battle=replaceActor(battle,new Combatant(actor.characterId(),next,actor.side(),actor.actionAvailable(),actor.reactionAvailable(),actor.movementSpentHalfFeet()+10,actor.movementBonus(),actor.disengaged(),actor.dodging(),actor.sequence(),actor.prone()));
            cursor++; spent+=5;
        }
        List<Frame> frames=replaceFrame(flow.frames(),flow.rootFrameKey(),FrameStatus.TERMINAL,Stage.MOVEMENT_COMPLETED,null,null);
        Flow completed=new Flow(flow.flowKey(),flow.rootFrameKey(),null,FlowStatus.COMPLETED,frames,flow.windows(),new Movement(move.movementKey(),move.actorId(),move.origin(),move.path(),cursor,spent,MovementStatus.COMPLETED,null),flow.attacks());
        facts.add(new CombatFact("MOVEMENT_COMPLETED",move.actorId(),move.movementKey(),null)); return replaceFlow(battle,completed);
    }

    private Battle continueFlow(Prepared p, Battle battle, Draws dice, List<HealthChange> changes, List<CombatFact> facts) {
        Flow flow=battle.activeFlow(); Window window=lastWindow(flow);
        if (window.status()==WindowStatus.CLOSED) {
            List<Window> windows=new ArrayList<>(flow.windows()); windows.set(windows.size()-1,new Window(window.windowKey(),window.ownerFrameKey(),window.checkpointOrdinal(),window.boundaryIndex(),WindowStatus.CONSUMED,window.opportunities()));
            Flow next=new Flow(flow.flowKey(),flow.rootFrameKey(),flow.rootFrameKey(),FlowStatus.ACTIVE,replaceFrame(flow.frames(),flow.rootFrameKey(),FrameStatus.RUNNABLE,Stage.MOVEMENT_CONTINUE,null,null),windows,flow.movement(),flow.attacks());
            return advanceMovement(p,replaceFlow(battle,next),next,facts);
        }
        Opportunity opportunity=currentOpportunity(window); OpportunityStatus status; UUID childKey=null; String reason=null;
        List<AttackAttempt> attacks=new ArrayList<>(flow.attacks()); List<Frame> frames=new ArrayList<>(flow.frames());
        if (p.command.kind()==Kind.DECLARE_OPPORTUNITY_ATTACK) {
            Combatant actor=combatant(battle,opportunity.actorId()); childKey=key(p.operationId,"opportunity-attack");
            attacks.add(attack(p,battle,actor,combatant(battle,flow.movement().actorId()),p.command.itemId(),childKey,true,dice,changes));
            battle=replaceActor(battle,new Combatant(actor.characterId(),actor.position(),actor.side(),actor.actionAvailable(),false,actor.movementSpentHalfFeet(),actor.movementBonus(),actor.disengaged(),actor.dodging(),actor.sequence(),actor.prone()));
            battle=reconcileInactivity(battle,changes);
            frames.add(new Frame(childKey,opportunity.selectionFrameKey(),FrameKind.ATTACK,childKey,Invocation.REACTION,FrameStatus.TERMINAL,Stage.ATTACK_RESOLVED,window.windowKey(),opportunity.opportunityKey()));
            status=OpportunityStatus.COMPLETED;
        } else if (p.command.kind()==Kind.PASS_REACTION) { status=OpportunityStatus.PASSED; reason="PASS"; }
        else { status=OpportunityStatus.INELIGIBLE; reason="CURRENT_CONDITIONS_CHANGED"; }
        frames=replaceFrame(frames,opportunity.selectionFrameKey(),FrameStatus.DECIDED,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window.windowKey(),opportunity.opportunityKey());
        List<Opportunity> opportunities=new ArrayList<>(window.opportunities()); opportunities.set(opportunity.priority(),new Opportunity(opportunity.opportunityKey(),opportunity.actorId(),opportunity.priority(),opportunity.allowedItems(),status,opportunity.selectionFrameKey(),childKey,reason));
        Opportunity next=opportunities.stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElse(null);
        if(next!=null) {next=withSelection(next);opportunities.set(next.priority(),next);}
        UUID leaf=next==null?flow.rootFrameKey():next.selectionFrameKey();
        if(next!=null)frames.add(selection(flow,window.windowKey(),next));
        frames=replaceFrame(frames,flow.rootFrameKey(),FrameStatus.WAITING_WINDOW,next==null?Stage.MOVEMENT_CONTINUE:Stage.MOVEMENT_LEAVE_REACH_BEFORE,window.windowKey(),null);
        List<Window> windows=new ArrayList<>(flow.windows()); windows.set(windows.size()-1,new Window(window.windowKey(),window.ownerFrameKey(),window.checkpointOrdinal(),window.boundaryIndex(),next==null?WindowStatus.CLOSED:WindowStatus.OPEN,opportunities));
        facts.add(new CombatFact(status.name(),opportunity.actorId(),opportunity.opportunityKey(),reason));
        return replaceFlow(battle,new Flow(flow.flowKey(),flow.rootFrameKey(),leaf,FlowStatus.ACTIVE,frames,windows,flow.movement(),attacks));
    }

    private Map<UUID,List<UUID>> candidates(Battle battle, Map<UUID,Context> contexts, Combatant mover, Position from, Position to) {
        Map<UUID,List<UUID>> result=new LinkedHashMap<>();
        if(mover.disengaged() || contexts.get(mover.characterId()).character().hitPoints()==0)return result;
        for(Initiative initiative:battle.initiative()) {
            Combatant actor=combatant(battle,initiative.characterId()); Context context=contexts.get(actor.characterId());
            if(actor.side()==mover.side() || !actor.reactionAvailable() || context.character().hitPoints()==0 || context.character().health().dead())continue;
            List<UUID> items=new ArrayList<>();
            for(EquipmentItem item:context.character().equipment())if(preparedItem(item) && itemDefinition(context,item).kind()==EquipmentKind.WEAPON) {
                int reach=weapon(context,item.itemId()).reachFeet();
                if(actor.position().distanceFeet(from)<=reach && actor.position().distanceFeet(to)>reach)items.add(item.itemId());
            }
            if(!items.isEmpty()) {items.sort(Comparator.comparing(UUID::toString));result.put(actor.characterId(),List.copyOf(items));}
        }
        return result;
    }
    private boolean eligible(Battle battle,Map<UUID,Context> contexts,Flow flow,Window window,Opportunity opportunity) {
        Combatant mover=combatant(battle,flow.movement().actorId());
        return flow.movement().cursor()<flow.movement().path().size()
                && candidates(battle,contexts,mover,mover.position(),flow.movement().path().get(flow.movement().cursor())).containsKey(opportunity.actorId());
    }
    private Battle stopMovement(Battle battle,Flow flow,String reason,List<CombatFact> facts) {
        Movement move=flow.movement();
        Flow stopped=new Flow(flow.flowKey(),flow.rootFrameKey(),null,FlowStatus.COMPLETED,replaceFrame(flow.frames(),flow.rootFrameKey(),FrameStatus.TERMINAL,Stage.MOVEMENT_STOPPED,null,null),flow.windows(),
                new Movement(move.movementKey(),move.actorId(),move.origin(),move.path(),move.cursor(),move.spent(),MovementStatus.STOPPED,reason),flow.attacks());
        facts.add(new CombatFact("MOVEMENT_STOPPED",move.actorId(),move.movementKey(),reason));return replaceFlow(battle,stopped);
    }
    private static Combatant combatant(Battle battle,UUID id) { return battle.combatants().stream().filter(a->a.characterId().equals(id)).findFirst().orElseThrow(()->invalid("S2_COMBAT_CHARACTER_NOT_FOUND")); }
    private static void active(Context context) {if(context.character().hitPoints()==0 || context.character().health().dead())throw invalid("S2_COMBAT_ACTOR_INACTIVE");}
    private static int modifier(State state,String ability) {return Math.floorDiv(state.abilities().get(ability)-10,2);}
    private static Window lastWindow(Flow flow) {if(flow.windows().isEmpty())throw invalid("ACTION_FLOW_STAGE_INVALID");return flow.windows().getLast();}
    private static Opportunity currentOpportunity(Window window) {return window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow(()->invalid("REACTION_NOT_CURRENT"));}
    private static Frame selection(Flow flow,UUID window,Opportunity opportunity) {return new Frame(opportunity.selectionFrameKey(),flow.rootFrameKey(),FrameKind.REACTION_SELECTION,opportunity.opportunityKey(),Invocation.ROOT,FrameStatus.PENDING,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window,opportunity.opportunityKey());}
    private static Opportunity withSelection(Opportunity o) {return new Opportunity(o.opportunityKey(),o.actorId(),o.priority(),o.allowedItems(),o.status(),key(o.opportunityKey(),"selection"),o.attackFrameKey(),o.reason());}
    private static List<Frame> replaceFrame(List<Frame> frames,UUID id,FrameStatus status,Stage stage,UUID window,UUID opportunity) {
        return new ArrayList<>(frames.stream().map(f->f.frameKey().equals(id)?new Frame(f.frameKey(),f.parentFrameKey(),f.kind(),f.ownerKey(),f.invocation(),status,stage,window,opportunity):f).toList());
    }
    private static Flow withMovement(Flow f,Movement m) {return new Flow(f.flowKey(),f.rootFrameKey(),f.leafFrameKey(),f.status(),f.frames(),f.windows(),m,f.attacks());}
    private static Combatant withSequence(Combatant a,AttackSequence s) {return new Combatant(a.characterId(),a.position(),a.side(),a.actionAvailable(),a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),s,a.prone());}
    private static Combatant closeSequence(Combatant a) {AttackSequence s=a.sequence();return s==null || s.closed()?a:withSequence(a,new AttackSequence(s.sequenceKey(),s.limit(),s.performed(),s.limit()-s.performed(),true));}
    private static Battle reconcileInactivity(Battle b,List<HealthChange> changes) {
        for(HealthChange c:changes)if(c.after().hitPoints()==0 || c.after().health().dead()) {
            Combatant a=closeSequence(combatant(b,c.characterId()));
            b=replaceActor(b,new Combatant(a.characterId(),a.position(),a.side(),false,a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),a.sequence(),true));
        }return b;
    }
    private static Battle copy(Battle b,Status status,int round,long turn,int ordinal,List<Combatant> actors,List<Flow> flows) {return new Battle(b.combatId(),b.campaignId(),b.combatRule(),status,round,turn,ordinal,b.version(),actors,b.initiative(),flows);}
    private static Battle replaceActor(Battle b,Combatant actor) {return copy(b,b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.combatants().stream().map(a->a.characterId().equals(actor.characterId())?actor:a).toList(),b.flows());}
    private static Battle replaceFlow(Battle b,Flow flow) {return copy(b,b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.combatants(),b.flows().stream().map(f->f.flowKey().equals(flow.flowKey())?flow:f).toList());}
    private static Battle appendFlow(Battle b,Flow flow) {var flows=new ArrayList<>(b.flows());flows.add(flow);return copy(b,b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.combatants(),flows);}

    public void validateState(Battle state) {
        try {
            uuid(state.combatId());uuid(state.campaignId());
            if(state.combatId().equals(state.campaignId()) || state.status()==null || state.combatRule()==null || !state.combatRule().ruleType().equals("adventure.combat")
                    || state.round()<1 || state.turnNumber()<1 || state.version()<0 || state.version()>=MAX_FACTS || state.combatants().size()<2 || state.combatants().size()>MAX_COMBATANTS
                    || state.initiative().size()!=state.combatants().size() || state.activeOrdinal()<0 || state.activeOrdinal()>=state.initiative().size() || state.flows().size()>MAX_FLOWS)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
            Set<UUID> actors=new HashSet<>(),ordered=new HashSet<>(),keys=new HashSet<>();Set<Position> positions=new HashSet<>();
            for(Combatant actor:state.combatants()) {
                uuid(actor.characterId());
                if(!actors.add(actor.characterId()) || !positions.add(actor.position()) || actor.side()<0 || actor.side()>15 || actor.movementSpentHalfFeet()<0 || actor.movementSpentHalfFeet()>480 || actor.movementBonus()<0 || actor.movementBonus()>120 || actor.movementBonus()%5!=0)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                AttackSequence s=actor.sequence();if(s!=null) {uuid(s.sequenceKey());if(s.limit()!=1 || s.performed()<0 || s.forfeited()<0 || s.performed()+s.forfeited()>s.limit() || s.closed()!=(s.performed()+s.forfeited()==s.limit()))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
            }
            for(int i=0;i<state.initiative().size();i++) {Initiative value=state.initiative().get(i);if(!ordered.add(value.characterId()) || value.ordinal()!=i || value.d20()<1 || value.d20()>20 || value.dexterityModifier()<-5 || value.dexterityModifier()>10 || value.total()!=value.d20()+value.dexterityModifier()
                    || i>0 && state.initiative().get(i-1).total()<value.total())throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
            if(!actors.equals(ordered))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
            int active=0;
            for(Flow flow:state.flows()) {
                uuid(flow.flowKey());if(!keys.add(flow.flowKey()) || flow.frames().isEmpty() || flow.frames().size()>1024 || flow.windows().size()>64)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                Map<UUID,Frame> frames=new HashMap<>();for(Frame frame:flow.frames()) {uuid(frame.frameKey());if(!keys.add(frame.frameKey()) || frames.put(frame.frameKey(),frame)!=null || frame.status()==null || frame.kind()==null || frame.stage()==null || frame.invocation()==null)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
                Frame root=frames.get(flow.rootFrameKey());if(root==null || root.parentFrameKey()!=null || root.invocation()!=Invocation.ROOT)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                for(Frame frame:flow.frames()) {
                    Set<UUID> chain=new HashSet<>();Frame cursor=frame;
                    while(cursor.parentFrameKey()!=null) {if(!chain.add(cursor.frameKey()) || chain.size()>32)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");cursor=frames.get(cursor.parentFrameKey());if(cursor==null)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
                }
                if(flow.status()==FlowStatus.ACTIVE) {active++;if(flow.leafFrameKey()==null || !frames.containsKey(flow.leafFrameKey()) || root.status()==FrameStatus.TERMINAL)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
                else if(flow.status()!=FlowStatus.COMPLETED || flow.leafFrameKey()!=null || root.status()!=FrameStatus.TERMINAL)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                int pending=0;
                for(int w=0;w<flow.windows().size();w++) {
                    Window window=flow.windows().get(w);uuid(window.windowKey());
                    if(!keys.add(window.windowKey()) || !window.ownerFrameKey().equals(root.frameKey()) || window.checkpointOrdinal()!=w || window.opportunities().isEmpty() || window.opportunities().size()>MAX_COMBATANTS)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                    Set<UUID> windowActors=new HashSet<>();int previous=-1;
                    for(int i=0;i<window.opportunities().size();i++) {
                        Opportunity o=window.opportunities().get(i);uuid(o.opportunityKey());int ordinal=state.initiative().stream().filter(v->v.characterId().equals(o.actorId())).findFirst().orElseThrow().ordinal();
                        if(!keys.add(o.opportunityKey()) || !windowActors.add(o.actorId()) || o.priority()!=i || ordinal<=previous || o.allowedItems().isEmpty() || new HashSet<>(o.allowedItems()).size()!=o.allowedItems().size())throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");previous=ordinal;
                        if(o.status()==OpportunityStatus.PENDING) {pending++;if(o.attackFrameKey()!=null || o.reason()!=null)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
                        else if(o.status()==OpportunityStatus.COMPLETED) {Frame child=frames.get(o.attackFrameKey());if(child==null || !o.selectionFrameKey().equals(child.parentFrameKey()) || child.invocation()!=Invocation.REACTION || child.status()!=FrameStatus.TERMINAL)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
                        else if(o.attackFrameKey()!=null || o.reason()==null)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                    }
                    if(window.status()!=WindowStatus.OPEN && window.opportunities().stream().anyMatch(o->o.status()==OpportunityStatus.PENDING))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                    if(w<flow.windows().size()-1 && window.status()!=WindowStatus.CONSUMED)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                }
                if(flow.status()==FlowStatus.COMPLETED && flow.windows().stream().anyMatch(w->w.status()!=WindowStatus.CONSUMED))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                if(flow.movement()!=null) {
                    Movement m=flow.movement();uuid(m.movementKey());if(!actors.contains(m.actorId()) || m.path().isEmpty() || m.path().size()>MAX_PATH || m.cursor()<0 || m.cursor()>m.path().size() || m.spent()!=m.cursor()*5 || m.status()==null)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                    Position previous=m.origin();for(Position position:m.path()) {if(previous.distanceFeet(position)!=5)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");previous=position;}
                    if(m.status()==MovementStatus.COMPLETED && m.cursor()!=m.path().size() || m.status()==MovementStatus.STOPPED && m.stopReason()==null || m.status()!=MovementStatus.STOPPED && m.stopReason()!=null)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                    if(flow.status()==FlowStatus.ACTIVE) {
                        if(m.status()!=MovementStatus.PENDING || flow.windows().isEmpty())throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                        Position boundary=m.cursor()==0?m.origin():m.path().get(m.cursor()-1);
                        if(!combatant(state,m.actorId()).position().equals(boundary) || combatant(state,m.actorId()).movementSpentHalfFeet()<m.spent()*2)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                        Window last=lastWindow(flow);Frame leaf=frames.get(flow.leafFrameKey());
                        if(last.boundaryIndex()!=m.cursor() || last.status()==WindowStatus.CONSUMED || last.status()==WindowStatus.CLOSED && !leaf.frameKey().equals(root.frameKey())
                                || last.status()==WindowStatus.OPEN && (!leaf.frameKey().equals(currentOpportunity(last).selectionFrameKey()) || leaf.status()!=FrameStatus.PENDING))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                    }
                } else if(root.kind()!=FrameKind.ATTACK || flow.status()!=FlowStatus.COMPLETED || flow.attacks().size()!=1)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                for(AttackAttempt attack:flow.attacks())if(!frames.containsKey(attack.frameKey()) || !actors.contains(attack.actorId()) || !actors.contains(attack.targetId()) || attack.firstD20()<1 || attack.firstD20()>20 || attack.damageAmount()<0 || !attack.hit() && (!attack.damageDice().isEmpty() || attack.damageAmount()!=0))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
            }
            if(active>1 || state.status()==Status.ENDED && active!=0)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        } catch(NullPointerException | NoSuchElementException malformed) {throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");}
    }

    private static UUID key(UUID operation, String role) {
        UUID value = UUID.nameUUIDFromBytes((operation + ":" + role).getBytes(StandardCharsets.UTF_8));
        return new UUID((value.getMostSignificantBits() & ~0xF000L) | 0x4000L,value.getLeastSignificantBits());
    }
    private static final class Draws {
        private final IntUnaryOperator source;
        private final List<Roll> saved;
        private final List<Roll> actual = new ArrayList<>();
        private int cursor;
        Draws(IntUnaryOperator source) { this.source = source; this.saved = null; }
        Draws(List<Roll> saved) { this.source = null; this.saved = List.copyOf(saved); }
        int draw(String purpose, UUID subject, int sides) {
            int value;
            if (saved == null) value = source.applyAsInt(sides);
            else {
                if (cursor >= saved.size()) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                Roll roll = saved.get(cursor++);
                if (!roll.purpose().equals(purpose) || !roll.subjectId().equals(subject) || roll.sides() != sides) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                value = roll.value();
            }
            if (value < 1 || value > sides) throw invalid("S2_COMBAT_RANDOM_RESULT");
            actual.add(new Roll(purpose,subject,sides,value)); return value;
        }
        void complete() { if (saved != null && cursor != saved.size()) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID"); }
    }
}
