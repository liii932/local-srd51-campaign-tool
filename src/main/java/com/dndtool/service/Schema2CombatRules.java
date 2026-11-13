package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.*;
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
    static final String SECOND_WIND_ROLL = "SECOND_WIND_HEALING";
    public static final int MAX_COMBATANTS = 64, MAX_FLOWS = 128, MAX_PATH = 64, MAX_FACTS = 4096;
    public static final int START_TIME_HEADROOM_MINUTES = (Schema2CombatHistory.MAX_EVENTS * 6 + 59) / 60 + 240;
    public record Context(ProductionSchema2Projection material, InitialPlan initial, State character, long sceneSecond) {
        public Context(ProductionSchema2Projection material, InitialPlan initial, State character) {
            this(material, initial, character, Schema2SpellRules.second(character));
        }
        Context after(State state, int seconds) {
            return new Context(material, initial, state, Math.addExact(sceneSecond, seconds));
        }
    }
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
            if(command.kind()==Kind.BEGIN_MONSTER_REST)return 1;
            var actions=new Schema2ActionFlowRules(new Schema2CombatRules());
            if(Schema2SaveDamageRules.handles(before,command)) {
                var batch=before==null?null:before.saveDamageBatches().stream().filter(v->v.frameKey().equals(command.leafKey())).findFirst().orElse(null);
                return (command.kind()==Kind.MONSTER_BREATH?1:0)+(batch!=null&&batch.spellSource()!=null?actions.spellBatchAfterCost(roster,before,command.actorId()):actions.batchAfterCost(roster,before,command.actorId()));
            }
            return Schema2ActionFlowRules.handles(command.kind())?actions.mandatorySuffix(roster,before,command):0;
        }
        public Set<UUID> possibleHealthTargets() {
            if (command.kind() == Kind.SECOND_WIND) return Set.of(command.actorId());
            if(Schema2SaveDamageRules.handles(before,command)||Schema2ActionFlowRules.handles(command.kind())||Schema2MonsterRestRules.handles(command.kind())||worldCommand(command.kind())||explorationStand(before,command.kind())||command.kind()==Kind.END_COMBAT)return Set.copyOf(roster.keySet());
            if(command.kind()==Kind.START)return roster.entrySet().stream().filter(e->e.getValue().character().hitPoints()==0&&!e.getValue().character().health().dead()&&!e.getValue().character().health().stable()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
            if(command.kind()==Kind.END_TURN)return before.activeOrdinal()+1==before.initiative().size()?Set.copyOf(roster.keySet()):roster.containsKey(before.initiative().get(before.activeOrdinal()+1).characterId())?Set.of(before.initiative().get(before.activeOrdinal()+1).characterId()):Set.of();
            return Set.of();
        }
    }
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();
    private final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
    private final Schema2CharacterHealthRules health = new Schema2CharacterHealthRules();
    private final Schema2SpellRules spells = new Schema2SpellRules();

    static boolean placementCommand(Command command) {
        return command.kind() == Kind.OPEN_SCENE || command.kind() == Kind.SET_SCENE_ROSTER
                || command.kind() == Kind.CLOSE_SCENE && command.actorId() == null;
    }

    Resolution place(ProductionSchema2Projection material, UUID operationId, UUID sceneId, UUID campaignId,
            Map<UUID,Context> dependencies, Battle before, Command command, Map<UUID,MonsterInstance> arriving) {
        uuid(operationId); uuid(sceneId); uuid(campaignId);
        if (!placementCommand(command) || operationId.equals(campaignId) || sceneId.equals(campaignId)
                || operationId.equals(sceneId) && command.kind() != Kind.OPEN_SCENE)
            throw invalid("S2_COMBAT_COMMAND");
        boolean opening = command.kind() == Kind.OPEN_SCENE;
        boolean closing = command.kind() == Kind.CLOSE_SCENE;
        Command intent = opening ? Command.openScene(command.deployments(), command.monsterDeployments())
                : closing ? Command.action(Kind.CLOSE_SCENE, null)
                : Command.sceneRoster(command.deployments(), command.monsterDeployments());
        if (!intent.equals(command) || opening != (before == null))
            throw invalid("S2_COMBAT_COMMAND");
        if (before != null) {
            validateState(before);
            if (!before.combatId().equals(sceneId) || !before.campaignId().equals(campaignId))
                throw invalid("ACTION_FLOW_TARGET_MISMATCH");
            Schema2ExplorationRules.requireExploration(before);
            if (closing && (!before.combatants().isEmpty() || !before.concentrationHolders().isEmpty()
                    || before.environmentZones().stream().anyMatch(z -> z.endedOperationId() == null)))
                throw invalid("S2_SCENE_OBLIGATIONS_PENDING");
        }
        validateContexts(campaignId, dependencies);
        var oldActors = new HashMap<UUID,Combatant>();
        var oldMonsters = new HashMap<UUID,MonsterInstance>();
        if (before != null) {
            before.combatants().forEach(actor -> oldActors.put(actor.characterId(), actor));
            before.monsters().forEach(monster -> oldMonsters.put(monster.monsterId(), monster));
        }
        Set<UUID> ids = new HashSet<>(), dependencyIds = new HashSet<>(oldActors.keySet());
        dependencyIds.removeAll(oldMonsters.keySet());
        Set<Position> cells = new HashSet<>();
        List<Combatant> actors = new ArrayList<>();
        List<MonsterInstance> monsters = new ArrayList<>();
        for (Deployment deployment : command.deployments().stream()
                .sorted(Comparator.comparing(Deployment::characterId, Schema2CombatHistory.UUID_ORDER)).toList()) {
            UUID id = deployment.characterId();
            if (!ids.add(id) || !cells.add(deployment.position()) || !dependencies.containsKey(id) || oldMonsters.containsKey(id))
                throw invalid("S2_COMBAT_DEPLOYMENT");
            dependencyIds.add(id);
            actors.add(placedActor(oldActors.get(id), id, deployment.position(), deployment.side(),
                    dependencies.get(id).character().hitPoints() == 0));
        }
        Set<UUID> arrived = new HashSet<>();
        for (MonsterDeployment deployment : command.monsterDeployments().stream()
                .sorted(Comparator.comparing(MonsterDeployment::monsterId, Schema2CombatHistory.UUID_ORDER)).toList()) {
            UUID id = deployment.monsterId();
            if (!ids.add(id) || !cells.add(deployment.position()) || dependencies.containsKey(id))
                throw invalid("S2_COMBAT_DEPLOYMENT");
            MonsterInstance instance = oldMonsters.get(id);
            if (instance == null) {
                instance = arriving.get(id);
                arrived.add(id);
            }
            MonsterInstance initial = Schema2MonsterRules.initial(material, deployment);
            if (instance == null || !id.equals(instance.monsterId()) || !deployment.monster().equals(instance.monster())
                    || instance.hitPoints() < 0 || instance.hitPoints() > initial.hitPoints() || instance.dead() != (instance.hitPoints() == 0)
                    || !initial.actionUses().stream().map(MonsterActionUse::action).toList()
                        .equals(instance.actionUses().stream().map(MonsterActionUse::action).toList()))
                throw invalid("S2_COMBAT_DEPLOYMENT");
            monsters.add(instance);
            actors.add(placedActor(oldActors.get(id), id, deployment.position(), deployment.side(), false));
        }
        if (!arrived.equals(arriving.keySet()) || !dependencyIds.equals(dependencies.keySet())
                || ids.size() > MAX_COMBATANTS || command.deployments().size() > Schema2CombatHistory.MAX_CHARACTER_MEMBERS)
            throw invalid("S2_COMBAT_DEPLOYMENT");
        if (before != null) requireDeparturesSafe(before, ids, dependencies);
        Battle after = opening
                ? new Battle(sceneId, campaignId, combatRule(material), Status.EXPLORATION, 1, 1, 0, 0, actors,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), monsters, List.of(), List.of())
                : new Battle(sceneId, campaignId, before.combatRule(), closing ? Status.ENDED : Status.EXPLORATION,
                        before.round(), before.turnNumber(), 0, before.version() + 1, actors, List.of(), before.flows(),
                        before.ongoingEffects(), before.readyPreparations(), before.triggerFacts(), before.shields(),
                        before.concentrationHolders(), before.environmentZones(), before.suppressionCauses(),
                        monsters, before.saveDamageBatches(), before.monsterRests());
        validateState(after);
        return new Resolution(after, List.of(), List.of(), List.of(new CombatFact(command.kind().name(), null, sceneId, null)));
    }

    private static Combatant placedActor(Combatant existing, UUID id, Position position, int side, boolean prone) {
        if (existing != null) {
            if (!existing.position().equals(position) || existing.side() != side) throw invalid("S2_COMBAT_DEPLOYMENT");
            return existing;
        }
        return new Combatant(id, position, side, false, true, 0, 0, false, false, null, prone);
    }

    private static void requireDeparturesSafe(Battle battle, Set<UUID> remaining, Map<UUID,Context> roster) {
        boolean rosterChanged = !remaining.equals(battle.combatants().stream().map(Combatant::characterId)
                .collect(java.util.stream.Collectors.toSet()));
        if (battle.combatants().stream().anyMatch(actor -> actor.prone() && !remaining.contains(actor.characterId())
                    && roster.containsKey(actor.characterId()) && !roster.get(actor.characterId()).character().health().dead())
                || battle.ongoingEffects().stream().anyMatch(e -> e.status() == EffectStatus.ACTIVE
                    && (!remaining.contains(e.casterId()) || !remaining.containsAll(e.targets())))
                || battle.concentrationHolders().stream().anyMatch(h -> !remaining.contains(h.actorId()))
                || rosterChanged && (battle.shields().stream().anyMatch(s -> s.status() == EffectStatus.ACTIVE)
                    || battle.readyPreparations().stream().anyMatch(r -> Set.of(ReadyStatus.PREPARING, ReadyStatus.ARMED).contains(r.status())))
                || remaining.isEmpty() && battle.environmentZones().stream().anyMatch(z -> z.endedOperationId() == null)
                || battle.monsterRests().stream().anyMatch(r -> r.status() == MonsterRestStatus.ACTIVE && !remaining.contains(r.monsterId())))
            throw invalid("S2_SCENE_OBLIGATIONS_PENDING");
    }

    public Prepared preflight(UUID operationId, UUID combatId, UUID campaignId, Map<UUID,Context> roster, Battle before, Command command) {
        uuid(operationId); uuid(combatId); uuid(campaignId);
        if (operationId.equals(combatId) || operationId.equals(campaignId) || combatId.equals(campaignId)) throw invalid("S2_COMBAT_IDENTITY");
        if (roster == null || roster.isEmpty() || roster.size() > MAX_COMBATANTS || command == null) throw invalid("S2_COMBAT_ROSTER");
        if (placementCommand(command)) throw invalid("S2_COMBAT_COMMAND");
        validateContexts(campaignId, roster);
        validateCommand(command);
        if (before != null) {
            validateState(before);
            if (!before.combatId().equals(combatId) || !before.campaignId().equals(campaignId))
                throw invalid("ACTION_FLOW_TARGET_MISMATCH");
            Schema2MonsterRules.validate(roster,before);
            validateAttackAllowances(roster,before);
            validateOngoing(roster,before);
            if (before.status() == Status.ENDED) throw invalid("S2_COMBAT_ENDED");
            if(before.status()==Status.EXPLORATION && !worldCommand(command.kind()) && !explorationStand(before,command.kind()) && !Schema2MonsterRestRules.handles(command.kind()) && command.kind()!=Kind.CAST && command.kind()!=Kind.END_CONCENTRATION && !Schema2ActionFlowRules.continuation(command.kind()))throw invalid("S2_COMBAT_REQUIRED");
            if (before.version() >= MAX_FACTS - 1) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            boolean continuation = Schema2ActionFlowRules.continuation(command.kind());
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
        Long second = null;
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
            if (context.sceneSecond() < 0 || context.sceneSecond() > Schema2CharacterRestRules.MAX_MINUTE * 60
                    || second != null && second != context.sceneSecond()) throw invalid("S2_COMBAT_CLOCK_MISMATCH");
            second = context.sceneSecond();
            growth.validateState(context.material(),character); rests.validateState(context.material(),character);
            equipment.validateState(context.material(),character); health.validateState(character);
            Schema2CharacterEquipmentRules.requireCompleteScope(context.material(),character);
            Schema2CharacterEquipmentRules.requireNoUnownedEffects(context.material(),character);
            if (character.rest().active() != null) throw invalid("S2_COMBAT_REST_ACTIVE");
            combatRule(context.material()); speed(context);
            // Every held weapon is a possible OA source; a missing reader cannot be silently omitted.
            for (EquipmentItem item : character.equipment()) if (preparedItem(item) && itemDefinition(context,item).kind() == EquipmentKind.WEAPON) weapon(context,item.itemId());
        }
    }

    private void validateCommand(Command command) {
        for(UUID id:Arrays.asList(command.actorId(),command.targetId(),command.itemId(),command.flowKey(),command.leafKey(),command.windowKey(),command.opportunityKey(),command.readyKey(),command.zoneKey()))if(id!=null)uuid(id);
        if(Schema2SaveDamageRules.handles(null,command)||Schema2MonsterRestRules.handles(command.kind())||Schema2ActionFlowRules.handles(command.kind())||worldCommand(command.kind()))return;
        boolean start=command.kind()==Kind.START;
        if((start?command.actorId()!=null:command.actorId()==null)||command.targetId()!=null||command.itemId()!=null||!command.path().isEmpty()
                ||(start?command.deployments().isEmpty():!command.deployments().isEmpty())||(!start&&!command.tieBreakOrder().isEmpty())
                ||command.flowKey()!=null||command.leafKey()!=null||command.windowKey()!=null||command.opportunityKey()!=null||command.castInput()!=null
                ||command.readyInput()!=null||command.readyKey()!=null||command.zoneInput()!=null||command.zoneKey()!=null||command.healthInput()!=null||command.seconds()!=0||command.breathInput()!=null||command.monsterRestInput()!=null||(!start&&!command.monsterDeployments().isEmpty()))throw invalid("S2_COMBAT_COMMAND");
    }

    /** Checks the rule-only combat dependencies of the current character and deployment entry points. */
    public static void validateFrozenSupport(ProductionSchema2Projection material, RuleIdentity race) {
        combatRule(material);
        movement(material, race, null);
        for (FinalRule row : material.projection().rules())
            if (row instanceof FinalItem item && item.enabled() && item.definition().kind() == EquipmentKind.WEAPON)
                weaponProfile(material, item.identity());
        Schema2MonsterRules.validateFrozenSupport(material);
    }

    private static RuleIdentity combatRule(ProductionSchema2Projection material) {
        var rows = material.projection().rules().stream().filter(FinalCombat.class::isInstance).map(FinalCombat.class::cast).filter(FinalCombat::enabled).toList();
        if (rows.size() != 1 || !rows.getFirst().definition().algorithm().equals("SRD_2014_COMBAT_V1")) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return rows.getFirst().identity();
    }
    int speed(Context context) {
        var sources = context.initial().character().sources();
        int speed = movement(context.material(), sources.race(), sources.subrace()).walkSpeedFeet();
        return Math.max(0, speed - equipment.derive(context.material(), context.character()).speedPenalty());
    }
    private static MovementDefinition movement(ProductionSchema2Projection material, RuleIdentity race, RuleIdentity subrace) {
        var rows = material.projection().rules().stream().filter(FinalMovement.class::isInstance).map(FinalMovement.class::cast)
                .filter(row -> row.enabled() && (row.definition().owner().equals(race) || row.definition().owner().equals(subrace))).toList();
        if (rows.size() != 1 || !rows.getFirst().definition().algorithm().equals("SRD_2014_WALKING_SPEED_V1")) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return rows.getFirst().definition();
    }
    private ItemDefinition itemDefinition(Context context, EquipmentItem instance) {
        var rows = context.material().projection().rules().stream().filter(FinalItem.class::isInstance).map(FinalItem.class::cast)
                .filter(row -> row.enabled() && row.identity().equals(instance.item())).toList();
        if (rows.size() != 1) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return rows.getFirst().definition();
    }
    WeaponAttackDefinition weapon(Context context, UUID itemId) {
        EquipmentItem instance = context.character().equipment().stream().filter(item -> item.itemId().equals(itemId) && preparedItem(item))
                .findFirst().orElseThrow(() -> invalid("S2_COMBAT_WEAPON_NOT_PREPARED"));
        if (itemDefinition(context,instance).kind() != EquipmentKind.WEAPON) throw invalid("S2_COMBAT_WEAPON_NOT_PREPARED");
        return weaponProfile(context.material(), instance.item());
    }
    private static WeaponAttackDefinition weaponProfile(ProductionSchema2Projection material, RuleIdentity item) {
        var rows = material.projection().rules().stream().filter(FinalWeaponAttack.class::isInstance).map(FinalWeaponAttack.class::cast)
                .filter(row -> row.enabled() && row.definition().item().equals(item)).toList();
        if (rows.size() != 1 || !rows.getFirst().definition().algorithm().equals("SRD_2014_MELEE_WEAPON_ATTACK_V1")) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return rows.getFirst().definition();
    }
    int speed(Map<UUID,Context> roster,Battle b,UUID id) { return Schema2MonsterRules.isMonster(b,id)?Schema2MonsterRules.definition(roster,Schema2MonsterRules.instance(b,id).monster()).statBlock().walkSpeedFeet():speed(roster.get(id)); }
    private static boolean preparedItem(EquipmentItem item) { return Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(item.location()); }

    private void preflightIntent(Prepared p) {
        Command command = p.command;
        if(Schema2SaveDamageRules.handles(p.before,command)) {new Schema2SaveDamageRules().prepare(p.roster,p.before,command);return;}
        if(Schema2MonsterRestRules.handles(command.kind())) {Schema2MonsterRestRules.prepare(p.roster,p.before,command);return;}
        if(Schema2ActionFlowRules.handles(command.kind())) {new Schema2ActionFlowRules(this).prepare(p.roster,p.before,command);return;}
        if(worldCommand(command.kind()) || explorationStand(p.before,command.kind())) {prepareWorld(p);return;}
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
            if(!ids.equals(p.roster.keySet()))throw invalid("S2_COMBAT_DEPLOYMENT");
            for(MonsterDeployment deployment:command.monsterDeployments()) {
                if(!ids.add(deployment.monsterId())||!positions.add(deployment.position()))throw invalid("S2_COMBAT_DEPLOYMENT");
                sides.add(deployment.side());Schema2MonsterRules.initial(p.roster,deployment);
            }
            if(ids.size()<2||ids.size()>MAX_COMBATANTS||sides.size()<2||command.tieBreakOrder().size()!=ids.size()
                    ||!new HashSet<>(command.tieBreakOrder()).equals(ids))throw invalid("S2_COMBAT_DEPLOYMENT");
            return;
        }
        Combatant actor = combatant(p.before,command.actorId());
        if (command.kind()==Kind.END_CONCENTRATION) {
            if (p.before.concentrationHolders().stream().noneMatch(e -> e.actorId().equals(command.actorId())))
                throw invalid("S2_SPELL_CONCENTRATION_UNAVAILABLE");
            return;
        }
        if (!command.actorId().equals(p.before.activeActor())) throw invalid("S2_COMBAT_NOT_CURRENT_TURN");
        if (command.kind() == Kind.END_TURN) {
            int next = (p.before.activeOrdinal() + 1) % p.before.initiative().size();
            if (p.before.turnNumber() >= 1000000) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            Map<UUID,Context> turnRoster=next==0?afterRound(p.roster):p.roster;
            Context incoming=turnRoster.get(p.before.initiative().get(next).characterId());
            if (incoming!=null && incoming.character().hitPoints()==0 && !incoming.character().health().stable() && !incoming.character().health().dead()
                    && Schema2SpellRules.blessing(p.before,incoming.character().characterId())!=null && p.before.flows().size()>=MAX_FLOWS)
                throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            if(incoming!=null)prepareTurn(incoming); return;
        }
        if (command.kind() == Kind.END_COMBAT) {
            if(p.before.shields().stream().anyMatch(e->e.status()==EffectStatus.ACTIVE)||p.before.readyPreparations().stream().anyMatch(r->r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING))throw invalid("S2_COMBAT_TURN_FACTS_PENDING");
            afterRound(p.roster);return;
        }
        if(Schema2MonsterRules.isMonster(p.before,actor.characterId())) {if(Schema2MonsterRules.instance(p.before,actor.characterId()).dead())throw invalid("S2_COMBAT_ACTOR_INACTIVE");if(command.kind()==Kind.BEGIN_ATTACK)throw invalid("S2_MONSTER_ACTION_UNSUPPORTED");}
        else active(p.roster.get(actor.characterId()));
        switch (command.kind()) {
            case DASH, DISENGAGE, DODGE, BEGIN_ATTACK -> {
                if (!actor.actionAvailable()) throw invalid("S2_COMBAT_ACTION_UNAVAILABLE");
                if (command.kind() == Kind.BEGIN_ATTACK && actor.sequence() != null && !actor.sequence().closed()) throw invalid("S2_COMBAT_SEQUENCE_ACTIVE");
                if (command.kind() == Kind.BEGIN_ATTACK) attackAllowance(p.roster.get(actor.characterId()));
            }
            case FORFEIT_ATTACKS -> { if (actor.sequence() == null || actor.sequence().closed()) throw invalid("S2_COMBAT_SEQUENCE_UNAVAILABLE"); }
            case SECOND_WIND -> {
                State paid = paySecondWind(p.roster.get(actor.characterId()));
                health.applyHealing(paid, 1 + paid.level());
            }
            case DROP_PRONE -> {
                if (!p.roster.containsKey(actor.characterId())) throw invalid("S2_MONSTER_ACTION_UNSUPPORTED");
                if (actor.prone()) throw invalid("S2_COMBAT_ALREADY_PRONE");
            }
            case STAND_UP -> {
                int speed=speed(p.roster,p.before,actor.characterId());
                if(!actor.prone() || speed==0 || 2*(speed+actor.movementBonus())-actor.movementSpentHalfFeet()<speed)throw invalid("S2_COMBAT_CANNOT_STAND");
            }
            default -> throw invalid("S2_COMBAT_COMMAND");
        }
    }

    private void prepareTurn(Context context) {
        State character = context.character();
        if (character.hitPoints() == 0 && !character.health().stable() && !character.health().dead()) health.prepare(context.material(),character,deathCommand(context));
    }
    private Schema2CharacterRuntime.Command deathCommand(Context context) {
        var owners = context.material().projection().rules().stream().filter(FinalHealth.class::isInstance).map(FinalHealth.class::cast).filter(FinalHealth::enabled).toList();
        if (owners.size() != 1) throw invalid("ACTION_FLOW_RULE_UNSUPPORTED");
        return new Schema2CharacterRuntime.Command("DEATH_SAVE_RESOLVED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,
                new HealthInput(owners.getFirst().identity(),null,null,"Combat turn begins"));
    }
    private Resolution execute(Prepared p, Draws dice) {
        if(Schema2SaveDamageRules.handles(p.before,p.command)) {
            Resolution result=new Schema2SaveDamageRules().execute(p.operationId,p.roster,p.before,p.command,dice::draw);validateState(result.after());
            return new Resolution(result.after(),result.healthChanges(),dice.actual,result.facts());
        }
        if(Schema2MonsterRestRules.handles(p.command.kind()))return Schema2MonsterRestRules.execute(p.operationId,p.roster,p.before,p.command);
        if(worldCommand(p.command.kind()) || explorationStand(p.before,p.command.kind()))return executeWorld(p,dice);
        if(Schema2ActionFlowRules.handles(p.command.kind())) {
            Resolution result=new Schema2ActionFlowRules(this).execute(p.operationId,p.roster,p.before,p.command,dice::draw);
            List<CombatFact> facts=new ArrayList<>(result.facts());
            var reconciliation=Schema2OngoingEffectRules.reconcile(result.after(),p.operationId);facts.addAll(reconciliation.facts());
            Battle after=Schema2OngoingEffectRules.withCauses(result.after(),reconciliation.causes());validateState(after);
            return new Resolution(after,result.healthChanges(),dice.actual,facts);
        }
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
            List<MonsterInstance> monsters=new ArrayList<>();
            for(MonsterDeployment deployment:p.command.monsterDeployments()) {
                MonsterInstance m=Schema2MonsterRules.initial(p.roster,deployment);monsters.add(m);
                int dex=Schema2MonsterRules.modifier(Schema2MonsterRules.definition(p.roster,m.monster()).statBlock(),"ability.dexterity"),d20=dice.draw("INITIATIVE",m.monsterId(),20);
                initiatives.add(new Initiative(m.monsterId(),d20,dex,d20+dex,0));
            }
            monsters.sort(Comparator.comparing(MonsterInstance::monsterId,(a,b)->{int h=Long.compareUnsigned(a.getMostSignificantBits(),b.getMostSignificantBits());return h!=0?h:Long.compareUnsigned(a.getLeastSignificantBits(),b.getLeastSignificantBits());}));
            initiatives.sort(Comparator.comparingInt(Initiative::total).reversed().thenComparingInt(i -> p.command.tieBreakOrder().indexOf(i.characterId())));
            List<Initiative> ordered = new ArrayList<>();
            for (int i=0;i<initiatives.size();i++) { var value = initiatives.get(i); ordered.add(new Initiative(value.characterId(),value.d20(),value.dexterityModifier(),value.total(),i)); }
            List<Combatant> combatants = new ArrayList<>(p.command.deployments().stream().sorted(Comparator.comparing(d->d.characterId().toString())).map(d -> new Combatant(d.characterId(),d.position(),d.side(),false,true,0,0,false,false,null,p.roster.get(d.characterId()).character().hitPoints()==0)).toList());
            for(MonsterDeployment d:p.command.monsterDeployments().stream().sorted(Comparator.comparing(MonsterDeployment::monsterId,(a,b)->{int h=Long.compareUnsigned(a.getMostSignificantBits(),b.getMostSignificantBits());return h!=0?h:Long.compareUnsigned(a.getLeastSignificantBits(),b.getLeastSignificantBits());})).toList())combatants.add(new Combatant(d.monsterId(),d.position(),d.side(),false,true,0,0,false,false,null,false));
            battle = new Battle(p.combatId,p.campaignId,combatRule(p.roster.values().iterator().next().material()),Status.ACTIVE,1,1,0,0,combatants,ordered,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),monsters,List.of(),List.of());
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
                    battle = reconcileEffects(turnPrepared,battle,changes,facts);
                    battle = beginTurn(turnPrepared,battle,dice,changes,facts);
                }
                case END_COMBAT -> {
                    Map<UUID,Context> contexts=recordRoundTime(p,battle,changes,facts);
                    battle=reconcileEffects(new Prepared(p.operationId,p.combatId,p.campaignId,contexts,p.before,p.command),battle,changes,facts);
                    battle=copy(battle,Status.EXPLORATION,battle.round(),battle.turnNumber(),battle.activeOrdinal(),battle.combatants().stream().map(Schema2CombatRules::closeSequence).toList(),battle.flows());
                }
                case DASH, DISENGAGE, DODGE, BEGIN_ATTACK -> {
                    AttackSequence sequence = p.command.kind() == Kind.BEGIN_ATTACK
                            ? new AttackSequence(key(p.operationId,"sequence"),attackAllowance(p.roster.get(actor.characterId())),0,0,false)
                            : actor.sequence();
                    battle = replaceActor(battle,new Combatant(actor.characterId(),actor.position(),actor.side(),false,actor.reactionAvailable(),actor.movementSpentHalfFeet(),
                            actor.movementBonus() + (p.command.kind() == Kind.DASH ? speed(p.roster,battle,actor.characterId()) : 0),
                            actor.disengaged() || p.command.kind() == Kind.DISENGAGE,actor.dodging() || p.command.kind() == Kind.DODGE,sequence,actor.prone()));
                }
                case END_CONCENTRATION -> battle=Schema2SpellRules.end(battle,p.command.actorId(),"VOLUNTARY",facts);
                case SECOND_WIND -> applySecondWind(p, dice, changes);
                case DROP_PRONE -> battle = replaceActor(battle, withPosture(actor, true, actor.movementSpentHalfFeet()));
                case STAND_UP -> battle = replaceActor(battle,
                        withPosture(actor, false, actor.movementSpentHalfFeet() + speed(p.roster,battle,actor.characterId())));
                case FORFEIT_ATTACKS -> battle = replaceActor(battle,closeSequence(actor));
                default -> throw invalid("S2_COMBAT_COMMAND");
            }
            battle = new Battle(battle.combatId(),battle.campaignId(),battle.combatRule(),battle.status(),battle.round(),battle.turnNumber(),battle.activeOrdinal(),p.before.version()+1,battle.combatants(),battle.initiative(),battle.flows(),battle.ongoingEffects(),battle.readyPreparations(),battle.triggerFacts(),battle.shields(),battle.concentrationHolders(),battle.environmentZones(),battle.suppressionCauses(),battle.monsters(),battle.saveDamageBatches(),battle.monsterRests());
            boolean secondWind = p.command.kind() == Kind.SECOND_WIND;
            facts.add(new CombatFact(p.command.kind().name(), p.command.actorId(),
                    secondWind ? secondWindReference(p.operationId) : p.combatId, secondWind ? "BONUS_ACTION" : null));
        }
        battle=reconcileEffects(p,battle,changes,facts);
        var reconciliation=Schema2OngoingEffectRules.reconcile(battle,p.operationId);facts.addAll(reconciliation.facts());battle=Schema2OngoingEffectRules.withCauses(battle,reconciliation.causes());
        validateState(battle);
        return new Resolution(battle,changes,dice.actual,facts);
    }

    private State paySecondWind(Context context) {
        if (context == null) throw invalid("S2_COMBAT_SECOND_WIND_UNAVAILABLE");
        State state = context.character();
        RuleIdentity resource = Schema2FeatureSupport.secondWindResource(context.material(), state);
        if (resource == null) throw invalid("S2_COMBAT_SECOND_WIND_UNAVAILABLE");
        // This is the only supported bonus action. Its single use cannot recover within combat.
        return growth.spend(context.material(), state, resource, 1);
    }

    private void applySecondWind(Prepared prepared, Draws dice, List<HealthChange> changes) {
        UUID actor = prepared.command.actorId();
        Context context = prepared.roster.get(actor);
        State before = context.character(), paid = paySecondWind(context);
        RuleIdentity resource = fighterSecondWindResource(before.characterClass());
        UUID reference = secondWindReference(prepared.operationId);
        int roll = dice.draw(SECOND_WIND_ROLL, actor, 10);
        int requested = roll + before.level();
        var healed = health.applyHealing(paid, requested);
        changes.add(new HealthChange(actor, before, paid, reference, 0, false, null, false, false, 0, resource, 1, false));
        var result = new HealthResult(List.of(roll), null, null, requested, healed.result().appliedAmount());
        changes.add(new HealthChange(actor, paid, healed.state(), reference, requested, false, result, false, false, 0, null, 0, true));
    }

    static UUID secondWindReference(UUID operationId) {
        return key(operationId, "second-wind");
    }

    private static boolean worldCommand(Kind kind) {return Set.of(Kind.CREATE_ENVIRONMENT_ZONE,Kind.END_ENVIRONMENT_ZONE,Kind.EXPLORATION_HEALTH,Kind.EXPLORATION_ADVANCE_TIME,Kind.EXPLORATION_MOVE,Kind.REENTER_COMBAT,Kind.CLOSE_SCENE).contains(kind);}
    private static boolean explorationStand(Battle battle, Kind kind) {
        return kind == Kind.STAND_UP && battle != null && battle.status() == Status.EXPLORATION;
    }
    private void prepareWorld(Prepared p) {
        Command c=p.command;Battle b=p.before;
        if(c.actorId()==null||!p.roster.containsKey(c.actorId())||!c.deployments().isEmpty()||c.itemId()!=null||c.flowKey()!=null||c.leafKey()!=null||c.windowKey()!=null||c.opportunityKey()!=null||c.castInput()!=null||c.readyInput()!=null||c.readyKey()!=null||!c.monsterDeployments().isEmpty()||c.breathInput()!=null||c.monsterRestInput()!=null)throw invalid("S2_COMBAT_COMMAND");
        boolean healthCommand=c.kind()==Kind.EXPLORATION_HEALTH,move=c.kind()==Kind.EXPLORATION_MOVE,reenter=c.kind()==Kind.REENTER_COMBAT;
        if((c.targetId()!=null)!=healthCommand||(c.healthInput()!=null)!=healthCommand||(c.zoneInput()!=null)!=(c.kind()==Kind.CREATE_ENVIRONMENT_ZONE)||(c.zoneKey()!=null)!=(c.kind()==Kind.END_ENVIRONMENT_ZONE)||(move?c.path().isEmpty():!c.path().isEmpty())||(!reenter&&!c.tieBreakOrder().isEmpty())||((c.seconds()!=0)!=(c.kind()==Kind.EXPLORATION_ADVANCE_TIME)))throw invalid("S2_COMBAT_COMMAND");
        if(c.kind()==Kind.CREATE_ENVIRONMENT_ZONE) {
            var zone=Schema2OngoingEffectRules.createZone(p.roster,b,p.operationId,c.zoneInput());List<EnvironmentZone> zones=new ArrayList<>(b.environmentZones());zones.add(zone);
            Battle candidate=new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),zones,b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());
            Schema2OngoingEffectRules.reconcile(candidate,p.operationId);return;
        }
        if(c.kind()==Kind.END_ENVIRONMENT_ZONE) {Schema2OngoingEffectRules.endZone(b.environmentZones().stream().filter(z->z.zoneKey().equals(c.zoneKey())).findFirst().orElseThrow(()->invalid("S2_ENVIRONMENT_NOT_FOUND")),p.operationId);return;}
        Schema2ExplorationRules.requireExploration(b);
        switch(c.kind()) {
            case EXPLORATION_ADVANCE_TIME -> Schema2ExplorationRules.requireTimeSafe(p.roster,c.seconds());
            case EXPLORATION_MOVE -> {if(b.flows().size()>=MAX_FLOWS)throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");Schema2ExplorationRules.prepareMove(p.roster,b,c.actorId(),c.path());Schema2ActionFlowRules.requireCauseCapacity(p.roster,b,c);}
            case STAND_UP -> {
                Context context = p.roster.get(c.actorId());
                active(context);
                if (!combatant(b, c.actorId()).prone() || speed(context) == 0) throw invalid("S2_COMBAT_CANNOT_STAND");
                Schema2ExplorationRules.requireTimeSafe(p.roster, 6);
            }
            case EXPLORATION_HEALTH -> {Schema2ExplorationRules.prepareHealth(p.roster,b,c.targetId(),c.healthInput());if(b.flows().size()>=MAX_FLOWS)throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");}
            case REENTER_COMBAT -> {if(b.flows().size()>=MAX_FLOWS&&p.roster.entrySet().stream().anyMatch(e->e.getValue().character().hitPoints()==0&&!e.getValue().character().health().stable()&&!e.getValue().character().health().dead()&&Schema2SpellRules.blessing(b,e.getKey())!=null))throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");if(b.combatants().size()<2||c.tieBreakOrder().size()!=b.combatants().size()||!new HashSet<>(c.tieBreakOrder()).equals(b.combatants().stream().map(Combatant::characterId).collect(java.util.stream.Collectors.toSet())))throw invalid("S2_COMBAT_DEPLOYMENT");for(Context context:p.roster.values())prepareTurn(context);}
            case CLOSE_SCENE -> requireDeparturesSafe(b, Set.of(), p.roster);
            default -> throw invalid("S2_COMBAT_COMMAND");
        }
    }
    private Resolution executeWorld(Prepared p,Draws dice) {
        Battle b=p.before;Command c=p.command;List<HealthChange> changes=new ArrayList<>();List<CombatFact> facts=new ArrayList<>();UUID owner=key(p.operationId,"scene-owner");
        switch(c.kind()) {
            case CREATE_ENVIRONMENT_ZONE,END_ENVIRONMENT_ZONE -> {
                List<EnvironmentZone> zones=new ArrayList<>(b.environmentZones());
                if(c.kind()==Kind.CREATE_ENVIRONMENT_ZONE)zones.add(Schema2OngoingEffectRules.createZone(p.roster,b,p.operationId,c.zoneInput()));
                else for(int i=0;i<zones.size();i++)if(zones.get(i).zoneKey().equals(c.zoneKey()))zones.set(i,Schema2OngoingEffectRules.endZone(zones.get(i),p.operationId));
                b=new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),zones,b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());
            }
            case EXPLORATION_ADVANCE_TIME,EXPLORATION_MOVE,STAND_UP -> {
                if (c.kind() == Kind.STAND_UP) {
                    Combatant actor = combatant(b, c.actorId());
                    b = replaceActor(b, withPosture(actor, false, actor.movementSpentHalfFeet()));
                }
                if(c.kind()==Kind.EXPLORATION_MOVE) {
                    Combatant mover=combatant(b,c.actorId());Position origin=mover.position();int ordinal=0;
                    for(Position step:c.path()) {
                        mover=new Combatant(mover.characterId(),step,mover.side(),mover.actionAvailable(),mover.reactionAvailable(),mover.movementSpentHalfFeet(),mover.movementBonus(),mover.disengaged(),mover.dodging(),mover.sequence(),mover.prone());b=replaceActor(b,mover);
                        var boundary=Schema2OngoingEffectRules.reconcile(b,p.operationId,++ordinal);facts.addAll(boundary.facts());b=Schema2OngoingEffectRules.withCauses(b,boundary.causes());
                    }
                    Movement movement=new Movement(owner,c.actorId(),origin,c.path(),c.path().size(),c.path().size()*5,MovementStatus.COMPLETED,null,false,0);
                    Frame frame=new Frame(owner,null,FrameKind.MOVEMENT,owner,Invocation.ROOT,FrameStatus.TERMINAL,Stage.MOVEMENT_COMPLETED,null,null);
                    b=appendFlow(b,new Flow(key(p.operationId,"scene-flow"),owner,null,FlowStatus.COMPLETED,List.of(frame),List.of(),List.of(movement),List.of(),List.of(),List.of(),List.of(),List.of()));
                }
                var plan=Schema2ExplorationRules.advanceTime(p.roster,c.kind()==Kind.EXPLORATION_ADVANCE_TIME?c.seconds():6,owner);changes.addAll(plan.changes());b=Schema2ExplorationRules.expire(b,plan.after().values().iterator().next().sceneSecond(),facts);
            }
            case EXPLORATION_HEALTH -> {
                var prepared=Schema2ExplorationRules.prepareHealth(p.roster,b,c.targetId(),c.healthInput());List<Integer> rolls=new ArrayList<>();for(int i=0;i<prepared.prepared().diceCount();i++)rolls.add(dice.draw("EXPLORATION_HEALTH",c.targetId(),prepared.prepared().diceSides()));
                var result=health.resolve(prepared.prepared(),rolls,null,null);State before=p.roster.get(c.targetId()).character(),after=result.state();boolean damage=prepared.kind()==VitalEffectKind.DAMAGE,healing=prepared.kind()==VitalEffectKind.HEALING;
                if(after.hitPoints()==0)after=Schema2CombatEquipmentFacts.drop(after,b.combatId(),combatant(b,c.targetId()).position());
                changes.add(new HealthChange(c.targetId(),before,after,owner,damage?result.result().rolledAmount():0,false,result.result(),false,false,0,null,0,false,c.healthInput()));
                List<SavingThrowAttempt> saves=new ArrayList<>();if(damage)b=afterDamage(p,b,owner,dice,changes,saves,facts);b=reconcileInactivity(b,changes);
                Frame frame=new Frame(owner,null,FrameKind.HEALTH,owner,Invocation.ROOT,FrameStatus.TERMINAL,Stage.HEALTH_RESOLVED,null,null);
                b=appendFlow(b,new Flow(key(p.operationId,"scene-flow"),owner,null,FlowStatus.COMPLETED,List.of(frame),List.of(),List.of(),List.of(),List.of(),saves,List.of(),List.of()));
            }
            case REENTER_COMBAT -> {
                b=Schema2MonsterRestRules.interrupt(b,p.operationId);
                List<Initiative> order=new ArrayList<>();for(UUID actor:c.tieBreakOrder()) {Context context=p.roster.get(actor);int modifier=context==null?Schema2MonsterRules.modifier(Schema2MonsterRules.definition(p.roster,Schema2MonsterRules.instance(b,actor).monster()).statBlock(),"ability.dexterity"):modifier(context.character(),"ability.dexterity"),roll=dice.draw("INITIATIVE",actor,20);if(context!=null&&equipment.derive(context.material(),context.character()).physicalCheckDisadvantage())roll=Math.min(roll,dice.draw("INITIATIVE_SECOND",actor,20));order.add(new Initiative(actor,roll,modifier,roll+modifier,0));}
                order.sort(Comparator.comparingInt(Initiative::total).reversed().thenComparingInt(i->c.tieBreakOrder().indexOf(i.characterId())));List<Initiative> numbered=new ArrayList<>();for(int i=0;i<order.size();i++) {Initiative value=order.get(i);numbered.add(new Initiative(value.characterId(),value.d20(),value.dexterityModifier(),value.total(),i));}
                var actors=b.combatants().stream().map(a->new Combatant(a.characterId(),a.position(),a.side(),false,true,0,0,false,false,null,a.prone())).toList();
                b=new Battle(b.combatId(),b.campaignId(),b.combatRule(),Status.ACTIVE,1,b.turnNumber()+1,0,b.version(),actors,numbered,b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.environmentZones(),b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());b=beginTurn(p,b,dice,changes,facts);
            }
            case CLOSE_SCENE -> b=copy(b,Status.ENDED,b.round(),b.turnNumber(),b.activeOrdinal(),b.combatants(),b.flows());
            default -> throw invalid("S2_COMBAT_COMMAND");
        }
        b=reconcileEffects(p,b,changes,facts);var reconciliation=Schema2OngoingEffectRules.reconcile(b,p.operationId);facts.addAll(reconciliation.facts());b=Schema2OngoingEffectRules.withCauses(b,reconciliation.causes());
        b=Schema2ActionFlowRules.rebuild(b,b.combatants(),b.flows(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),p.before.version()+1);facts.add(new CombatFact(c.kind().name(),c.actorId(),owner,null));validateState(b);return new Resolution(b,changes,dice.actual,facts);
    }

    private Battle beginTurn(Prepared p, Battle battle, Draws dice, List<HealthChange> changes, List<CombatFact> facts) {
        if(Schema2MonsterRules.isMonster(battle,battle.activeActor())) {
            var monster=Schema2MonsterRules.instance(battle,battle.activeActor());List<MonsterActionUse> uses=new ArrayList<>();
            for(var use:monster.actionUses()) {
                boolean available=use.available();var profile=Schema2MonsterRules.action(p.roster,use.action());
                if(!available&&battle.turnNumber()>1&&!monster.dead()) {int roll=dice.draw("MONSTER_RECHARGE",monster.monsterId(),profile.recharge().diceSides());available=roll>=profile.recharge().minimumRoll();facts.add(new CombatFact("MONSTER_RECHARGE_ROLLED",monster.monsterId(),key(p.operationId,"recharge-"+use.action().moduleKey()+":"+use.action().entryKey()),available?"RECHARGED":"NOT_RECHARGED"));}
                uses.add(new MonsterActionUse(use.action(),available));
            }
            battle=Schema2MonsterRules.replace(battle,new MonsterInstance(monster.monsterId(),monster.monster(),monster.hitPoints(),monster.dead(),uses));
            Combatant actor=combatant(battle,monster.monsterId());return replaceActor(battle,new Combatant(actor.characterId(),actor.position(),actor.side(),!monster.dead(),true,0,0,false,false,null,actor.prone()));
        }
        battle=Schema2CombatStateValidator.expireTurnFacts(battle,facts);
        Combatant actor = combatant(battle,battle.activeActor()); Context context = p.roster.get(actor.characterId()); State current = context.character();
        if (current.hitPoints() == 0 && !current.health().dead() && !current.health().stable()) {
            var prepared = health.prepare(context.material(),current,deathCommand(context));
            int d20 = dice.draw("TURN_DEATH_SAVE",actor.characterId(),20);
            OngoingEffect blessing=Schema2SpellRules.blessing(battle,actor.characterId());
            Integer d4=blessing==null?null:dice.draw("BLESS_DEATH_SAVE",actor.characterId(),4);
            Integer recovery = prepared.needsRecoveryD4(d20,d4==null?0:d4) ? dice.draw("STABLE_RECOVERY",actor.characterId(),4) : null;
            var resolved = health.resolveDeathSave(prepared,d20,d4==null?0:d4,recovery);
            UUID turnKey = key(p.operationId,"turn-start");
            if (blessing!=null) {
                UUID frameKey=turnKey;
                SavingThrowAttempt save=new SavingThrowAttempt(frameKey,actor.characterId(),"DEATH",null,d20,0,0,blessing.effectKey(),d4,10,d20+d4,d20+d4>=10);
                Frame frame=new Frame(frameKey,null,FrameKind.SAVING_THROW,frameKey,Invocation.ROOT,FrameStatus.TERMINAL,Stage.SAVING_THROW_RESOLVED,null,null);
                battle=appendFlow(battle,new Flow(key(p.operationId,"death-save-flow"),frameKey,null,FlowStatus.COMPLETED,List.of(frame),List.of(),null,List.of(),List.of(),List.of(save)));
            }
            changes.add(new HealthChange(actor.characterId(),current,resolved.state(),turnKey,0,false,resolved.result(),true));
            current = resolved.state(); facts.add(new CombatFact("TURN_DEATH_SAVE_RESOLVED",actor.characterId(),turnKey,null));
        }
        return replaceActor(battle,new Combatant(actor.characterId(),actor.position(),actor.side(),current.hitPoints()>0 && !current.health().dead(),true,0,0,false,false,null,actor.prone() || current.hitPoints()==0));
    }
    private static Map<UUID,Context> afterRound(Map<UUID,Context> roster) {
        Map<UUID,Context> result=new HashMap<>();
        for(var entry:roster.entrySet()) {Context c=entry.getValue();Schema2ExplorationRules.requireSceneTime(c.sceneSecond(),6);result.put(entry.getKey(),c.after(Schema2CombatTimeRules.advanceSeconds(c.material(),c.character(),6),6));}
        return Map.copyOf(result);
    }
    private static Map<UUID,Context> recordRoundTime(Prepared p,Battle battle,List<HealthChange> changes,List<CombatFact> facts) {
        Map<UUID,Context> contexts=afterRound(p.roster);
        for(UUID memberId:p.roster.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            State before=p.roster.get(memberId).character(),after=contexts.get(memberId).character();
            UUID timeKey=key(p.operationId,"round-time-"+memberId);
            changes.add(new HealthChange(memberId,before,after,timeKey,0,false,
                    new HealthResult(List.of(),null,null,0,(int)Math.max(0,after.hitPoints()-before.hitPoints())),false,true,6));
        }
        facts.add(new CombatFact("ROUND_TIME_ADVANCED",null,p.combatId,null));return contexts;
    }

    private Battle afterDamage(Prepared p,Battle battle,UUID frameKey,Draws dice,List<HealthChange> changes,
            List<SavingThrowAttempt> saves,List<CombatFact> facts) {
        for (HealthChange change:changes) {
            if (!change.frameKey().equals(frameKey) || change.damageAmount()==0 || change.healing() || change.resource()!=null) continue;
            UUID subject=change.characterId();
            if (battle.concentrationHolders().stream().noneMatch(e -> e.actorId().equals(subject))) continue;
            if (change.after().hitPoints()==0 || change.after().health().dead()) {
                battle=Schema2SpellRules.end(battle,subject,"INCAPACITATED",facts); continue;
            }
            int dc=Math.max(10,change.damageAmount()/2),mod=modifier(change.after(),"ability.constitution");
            int d20=dice.draw("CONCENTRATION_D20",subject,20);
            OngoingEffect blessing=Schema2SpellRules.blessing(battle,subject);
            Integer d4=blessing==null?null:dice.draw("BLESS_CONCENTRATION",subject,4);
            int pb=Schema2SavingThrowRules.proficiency(p.roster.get(subject),"ability.constitution");int total=d20+mod+pb+(d4==null?0:d4); boolean success=total>=dc;
            saves.add(new SavingThrowAttempt(frameKey,subject,"CONCENTRATION","ability.constitution",d20,mod,pb,
                    blessing==null?null:blessing.effectKey(),d4,dc,total,success));
            if (!success) battle=Schema2SpellRules.end(battle,subject,"CONCENTRATION_SAVE_FAILED",facts);
        }
        return battle;
    }

    private Battle reconcileEffects(Prepared p,Battle battle,List<HealthChange> changes,List<CombatFact> facts) {
        for (OngoingEffect effect:List.copyOf(battle.ongoingEffects())) if (effect.status()==EffectStatus.ACTIVE) {
            Context context=p.roster.get(effect.casterId());
            State current=context.character();
            for (HealthChange change:changes) if (change.characterId().equals(effect.casterId())) current=change.after();
            long now = Math.addExact(context.sceneSecond(), Schema2SpellRules.second(current)
                    - Schema2SpellRules.second(context.character()));
            if (current.hitPoints()==0 || current.health().dead()) battle=Schema2SpellRules.end(battle,effect.casterId(),"INCAPACITATED",facts);
            else if (now>=effect.expiresAtSecond()) battle=Schema2SpellRules.end(battle,effect.casterId(),"DURATION_EXPIRED",facts);
        }
        return battle;
    }

    private static Combatant combatant(Battle battle,UUID id) { return battle.combatants().stream().filter(a->a.characterId().equals(id)).findFirst().orElseThrow(()->invalid("S2_COMBAT_CHARACTER_NOT_FOUND")); }
    private static void active(Context context) {if(context.character().hitPoints()==0 || context.character().health().dead())throw invalid("S2_COMBAT_ACTOR_INACTIVE");}
    private static int modifier(State state,String ability) {return Math.floorDiv(state.abilities().get(ability)-10,2);}
    private static Combatant withSequence(Combatant a,AttackSequence s) {return new Combatant(a.characterId(),a.position(),a.side(),a.actionAvailable(),a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),s,a.prone());}
    private static Combatant withPosture(Combatant actor, boolean prone, int spent) {
        return new Combatant(actor.characterId(), actor.position(), actor.side(), actor.actionAvailable(),
                actor.reactionAvailable(), spent, actor.movementBonus(), actor.disengaged(), actor.dodging(), actor.sequence(), prone);
    }
    private static Combatant closeSequence(Combatant a) {AttackSequence s=a.sequence();return s==null || s.closed()?a:withSequence(a,new AttackSequence(s.sequenceKey(),s.limit(),s.performed(),s.limit()-s.performed(),true));}
    private static Battle reconcileInactivity(Battle b,List<HealthChange> changes) {
        for(HealthChange c:changes)if(c.after().hitPoints()==0 || c.after().health().dead()) {
            Combatant a=closeSequence(combatant(b,c.characterId()));
            b=replaceActor(b,new Combatant(a.characterId(),a.position(),a.side(),false,a.reactionAvailable(),a.movementSpentHalfFeet(),a.movementBonus(),a.disengaged(),a.dodging(),a.sequence(),true));
        }return b;
    }
    private static Battle copy(Battle b,Status status,int round,long turn,int ordinal,List<Combatant> actors,List<Flow> flows) {return new Battle(b.combatId(),b.campaignId(),b.combatRule(),status,round,turn,ordinal,b.version(),actors,b.initiative(),flows,b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.environmentZones(),b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());}
    private static Battle replaceActor(Battle b,Combatant actor) {return copy(b,b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.combatants().stream().map(a->a.characterId().equals(actor.characterId())?actor:a).toList(),b.flows());}
    private static Battle replaceFlow(Battle b,Flow flow) {return copy(b,b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.combatants(),b.flows().stream().map(f->f.flowKey().equals(flow.flowKey())?flow:f).toList());}
    private static Battle appendFlow(Battle b,Flow flow) {var flows=new ArrayList<>(b.flows());flows.add(flow);return copy(b,b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.combatants(),flows);}

    public void validateState(Battle state) { Schema2CombatStateValidator.validate(state); }
    private static int attackAllowance(Context context) {
        return Schema2FeatureSupport.attacksPerAttackAction(context.material(),context.character());
    }
    private static void validateAttackAllowances(Map<UUID,Context> roster,Battle battle) {
        for (Combatant actor : battle.combatants()) {
            AttackSequence sequence = actor.sequence();
            if (sequence == null || sequence.closed()) continue;
            Context context = roster.get(actor.characterId());
            if (context == null || sequence.limit() != attackAllowance(context))
                throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        }
    }
    private void validateOngoing(Map<UUID,Context> roster,Battle battle) {
        Schema2OngoingEffectRules.validate(roster,battle);
        for(Flow flow:battle.flows())for(CastAttempt cast:flow.casts()) {
            Context source=roster.get(cast.actorId());
            if (source == null) {
                if (flow.status() == FlowStatus.ACTIVE || cast.status() == CastStatus.HELD)
                    throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                continue;
            }
            Schema2FeatureSupport.requireSupportedFeatures(source.material(),source.character(),"ACTION_FLOW_FROZEN_DATA_INVALID");
            var definitions=source.material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).filter(r->r.enabled()&&r.identity().equals(cast.spell())).toList();
            if(definitions.size()!=1)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");String algorithm=definitions.getFirst().definition().algorithm();
            if(cast.castingRule().ruleType().equals("character.spellcasting")) {
                var profiles=source.material().projection().rules().stream().filter(FinalSpellcasting.class::isInstance).map(FinalSpellcasting.class::cast).filter(r->r.enabled()&&r.identity().equals(cast.castingRule())).toList();
                if(profiles.size()!=1)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");var profile=profiles.getFirst().definition();
                if(cast.slotLevel()!=1||!cast.slotResource().equals(profile.slotResource())||!(Schema2SpellRules.BLESS.equals(algorithm)&&cast.spell().equals(profile.bless())||Schema2SpellRules.CURE_WOUNDS.equals(algorithm)&&cast.spell().equals(profile.cureWounds())))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
            } else {
                var profiles=source.material().projection().rules().stream().filter(FinalWizardSpellcasting.class::isInstance).map(FinalWizardSpellcasting.class::cast).filter(r->r.enabled()&&r.identity().equals(cast.castingRule())).toList();
                if(profiles.size()!=1)throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");var profile=profiles.getFirst().definition();
                boolean burning=Schema2SpellRules.BURNING_HANDS.equals(algorithm);
                if(!(Schema2SpellRules.SHIELD.equals(algorithm)&&cast.slotLevel()==1&&cast.slotResource().equals(profile.slotResource())||Schema2SpellRules.COUNTERSPELL.equals(algorithm)&&cast.slotLevel()==3&&cast.slotResource().equals(profile.rank3SlotResource())
                        ||burning&&cast.slotLevel()>=1&&cast.slotLevel()<=3&&cast.slotResource().equals(Schema2SpellRules.slotResource(profile,cast.slotLevel()))))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                if(burning)Schema2SpellRules.requireEvocationScope(source);
            }
            if((cast.coneDirection()!=null)!=Schema2SpellRules.BURNING_HANDS.equals(algorithm))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        }
        for(var batch:battle.saveDamageBatches())
            if(batch.spellSource()!=null && (batch.status()==BatchStatus.ROLLED || roster.containsKey(batch.sourceId())))
                Schema2SaveDamageRules.validateSpellSource(roster,battle,batch);
        for(OngoingEffect effect:battle.ongoingEffects()) {
            Context source=roster.get(effect.casterId());
            if (source == null) {
                if (effect.status() == EffectStatus.ACTIVE) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                continue;
            }
            var spells=source.material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).filter(r->r.enabled()&&r.identity().equals(effect.spell())&&Schema2SpellRules.BLESS.equals(r.definition().algorithm())).toList();
            if(spells.size()!=1||battle.flows().stream().flatMap(f->f.casts().stream()).noneMatch(c->Objects.equals(c.effectKey(),effect.effectKey())&&c.targets().equals(effect.targets()))||effect.startedAtSecond()>source.sceneSecond()||effect.status()==EffectStatus.ACTIVE&&(effect.expiresAtSecond()<=source.sceneSecond()||source.character().hitPoints()==0||source.character().health().dead()))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        }
        for(ShieldEffect effect:battle.shields()) {
            CastAttempt cast=Schema2SpellRules.findCast(battle,effect.castFrameKey());Context source=roster.get(effect.actorId());
            if (source == null) {
                if (effect.status() == EffectStatus.ACTIVE) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
                continue;
            }
            if(cast==null||!cast.targets().equals(List.of(effect.actorId()))||source.material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).noneMatch(r->r.enabled()&&r.identity().equals(cast.spell())&&Schema2SpellRules.SHIELD.equals(r.definition().algorithm()))||effect.status()==EffectStatus.ACTIVE&&effect.expiresAtTurnNumber()<=battle.turnNumber())throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        }
        for(ReadyPreparation ready:battle.readyPreparations())if(Set.of(ReadyStatus.ARMED,ReadyStatus.PREPARING).contains(ready.status())&&ready.expiresAtTurnNumber()<=battle.turnNumber())throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
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
