package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2ObservationRules.CheckBasis;
import com.dndtool.service.Schema2ObservationRules.Draw;
import java.util.*;

import static com.dndtool.service.Schema2CombatState.*;

/** Shared frozen preflight and dice interpretation for START and REENTER_COMBAT. */
final class Schema2EncounterOpeningRules {
    static final String STEALTH_ROLL = "OPENING_STEALTH", STEALTH_SECOND_ROLL = "OPENING_STEALTH_SECOND";
    private static final String DEXTERITY = "ability.dexterity";

    record Plan(Battle basis, List<InitiativeBasis> initiative, List<StealthGroup> stealth,
                Map<UUID, List<UUID>> opposingThreats, Map<UUID, Integer> tieOrdinals) { }
    record Result(List<Initiative> initiative, List<ObservationCheck> openingChecks) { }
    private record InitiativeBasis(UUID actor, UUID rollSource, int modifier, boolean disadvantage) { }
    private record StealthMember(Combatant actor, CheckBasis basis, List<ObservationContest> contests) { }
    private record StealthGroup(UUID rollSource, List<StealthMember> members) { }

    private Schema2EncounterOpeningRules() { }

    static Plan prepare(Map<UUID, Context> roster, Battle battle, Command command, UUID operation) {
        Map<UUID, Combatant> actors = new HashMap<>();
        for (Combatant actor : battle.combatants()) actors.put(actor.characterId(), actor);
        OpeningInput input = command.opening();
        Map<UUID, UUID> initiativeSources = input == null ? Map.of() : initiativeSources(battle, input, actors);
        Map<UUID, List<UUID>> opposingThreats = input == null || input.stealthGroups().isEmpty() ? Map.of()
                : opposingThreats(roster, battle);
        List<StealthGroup> stealth = input == null ? List.of()
                : prepareStealth(roster, battle, input, actors, opposingThreats, operation);

        List<UUID> drawOrder = new ArrayList<>();
        if (command.kind() == Kind.START) {
            for (Deployment actor : command.deployments()) drawOrder.add(actor.characterId());
            for (MonsterDeployment actor : command.monsterDeployments()) drawOrder.add(actor.monsterId());
        } else drawOrder.addAll(command.tieBreakOrder());
        List<InitiativeBasis> initiative = new ArrayList<>();
        var equipment = new Schema2CharacterEquipmentRules();
        for (UUID actor : drawOrder) {
            Context character = roster.get(actor);
            int modifier;
            boolean disadvantage = false;
            if (character == null) {
                var monster = Schema2MonsterRules.instance(battle, actor);
                modifier = Schema2MonsterRules.modifier(Schema2MonsterRules.definition(roster, monster.monster()).statBlock(), DEXTERITY);
            } else {
                modifier = Math.floorDiv(character.character().abilities().get(DEXTERITY) - 10, 2);
                disadvantage = equipment.derive(character.material(), character.character()).physicalCheckDisadvantage();
            }
            initiative.add(new InitiativeBasis(actor, initiativeSources.getOrDefault(actor, actor), modifier, disadvantage));
        }
        Map<UUID, Integer> tieOrdinals = new HashMap<>();
        for (int i = 0; i < command.tieBreakOrder().size(); i++) tieOrdinals.put(command.tieBreakOrder().get(i), i);
        return new Plan(battle, List.copyOf(initiative), List.copyOf(stealth), opposingThreats, Map.copyOf(tieOrdinals));
    }

    private static Map<UUID, UUID> initiativeSources(Battle battle, OpeningInput input, Map<UUID, Combatant> actors) {
        Map<UUID, RuleIdentity> monsters = new HashMap<>();
        for (MonsterInstance monster : battle.monsters()) monsters.put(monster.monsterId(), monster.monster());
        Map<UUID, UUID> sources = new HashMap<>();
        for (OpeningGroup group : input.initiativeGroups()) {
            UUID source = group.rollSource();
            int side = requireMember(actors, source).side();
            RuleIdentity monster = monsters.get(source);
            if (monster == null) throw invalid("S2_COMBAT_OPENING_MONSTER");
            for (UUID member : group.members()) {
                if (requireMember(actors, member).side() != side || !monster.equals(monsters.get(member)))
                    throw invalid("S2_COMBAT_OPENING_MONSTER");
                sources.put(member, source);
            }
        }
        return sources;
    }

    private static Map<UUID, List<UUID>> opposingThreats(Map<UUID, Context> roster, Battle battle) {
        List<Combatant> living = battle.combatants().stream()
                .filter(actor -> Schema2MonsterRules.living(roster, battle, actor.characterId()))
                .sorted(Comparator.comparing(Combatant::characterId, Schema2CombatHistory.UUID_ORDER)).toList();
        Map<UUID, List<UUID>> threats = new HashMap<>();
        for (Combatant actor : living) threats.put(actor.characterId(), living.stream()
                .filter(opponent -> opponent.side() != actor.side()).map(Combatant::characterId).toList());
        return threats;
    }

    private static List<StealthGroup> prepareStealth(Map<UUID, Context> roster, Battle battle, OpeningInput input,
            Map<UUID, Combatant> actors, Map<UUID, List<UUID>> threats, UUID operation) {
        List<StealthGroup> stealth = new ArrayList<>();
        Map<UUID, Integer> passive = new HashMap<>();
        Set<UUID> checkIds = new HashSet<>();
        for (ObservationCheck check : battle.observation().checks()) checkIds.add(check.operationId());
        int hiders = input.stealthGroups().stream().mapToInt(group -> group.members().size()).sum();
        if (checkIds.size() + hiders > Schema2CombatHistory.MAX_EVENTS) throw invalid("S2_OBSERVATION_LIMIT");
        for (OpeningGroup group : input.stealthGroups()) {
            int side = requireMember(actors, group.rollSource()).side();
            List<StealthMember> members = new ArrayList<>();
            for (UUID id : group.members()) {
                Combatant actor = requireMember(actors, id);
                if (actor.side() != side) throw invalid("S2_COMBAT_OPENING_GROUP");
                if (!threats.containsKey(id)) throw invalid("S2_COMBAT_OPENING_MEMBER");
                if (!checkIds.add(Schema2ObservationRules.openingHideKey(operation, id)))
                    throw invalid("S2_COMBAT_OPENING_GROUP");
                CheckBasis basis = Schema2ObservationRules.stealthBasis(roster, battle, id);
                List<ObservationContest> contests = new ArrayList<>();
                for (UUID observer : threats.get(id)) {
                    int score = passive.computeIfAbsent(observer,
                            key -> Schema2ObservationRules.passivePerception(roster, battle, key));
                    contests.add(new ObservationContest(observer, score, Schema2ObservationRules.priorHide(battle, observer, id), false));
                }
                members.add(new StealthMember(actor, basis, List.copyOf(contests)));
            }
            stealth.add(new StealthGroup(group.rollSource(), List.copyOf(members)));
        }
        return stealth;
    }

    private static Combatant requireMember(Map<UUID, Combatant> actors, UUID id) {
        Combatant actor = actors.get(id);
        if (actor == null) throw invalid("S2_COMBAT_OPENING_MEMBER");
        return actor;
    }

    static Result resolve(Plan plan, UUID operation, Kind kind, Draw draws) {
        List<ObservationCheck> checks = new ArrayList<>();
        Map<UUID, Set<UUID>> hiddenThreats = new HashMap<>();
        for (StealthGroup group : plan.stealth()) {
            UUID source = group.rollSource();
            int first = draws.d20(STEALTH_ROLL, source);
            Integer second = group.members().stream().anyMatch(member -> member.basis().secondDieRequired())
                    ? draws.d20(STEALTH_SECOND_ROLL, source) : null;
            for (StealthMember member : group.members()) {
                CheckBasis basis = member.basis();
                UUID hider = member.actor().characterId();
                ObservationRoll roll = Schema2ObservationRules.roll(basis, first, basis.secondDieRequired() ? second : null);
                List<ObservationContest> contests = new ArrayList<>();
                for (ObservationContest contest : member.contests()) {
                    UUID observer = contest.counterpartId();
                    boolean hidden = roll.total() > contest.opposingScore();
                    if (hidden) hiddenThreats.computeIfAbsent(observer, ignored -> new HashSet<>()).add(hider);
                    contests.add(new ObservationContest(observer, contest.opposingScore(), contest.previousHideOperationId(), hidden));
                }
                checks.add(new ObservationCheck(Schema2ObservationRules.openingHideKey(operation, hider), kind, hider,
                        member.actor().position(), basis.rule(), basis.skill(), basis.monsterSkill(), false, roll, contests, operation, source));
            }
        }

        Map<UUID, Integer> firstDice = new HashMap<>();
        List<Initiative> ordered = new ArrayList<>();
        for (InitiativeBasis entry : plan.initiative()) {
            UUID actor = entry.actor(), source = entry.rollSource();
            int initiative = firstDice.computeIfAbsent(source, key -> draws.d20("INITIATIVE", key));
            if (entry.disadvantage()) initiative = Math.min(initiative, draws.d20("INITIATIVE_SECOND", actor));
            List<UUID> threats = plan.opposingThreats().getOrDefault(actor, List.of());
            boolean surprised = !threats.isEmpty() && hiddenThreats.getOrDefault(actor, Set.of()).containsAll(threats);
            ordered.add(new Initiative(actor, initiative, entry.modifier(), initiative + entry.modifier(), 0, surprised, source));
        }
        ordered.sort(Comparator.comparingInt(Initiative::total).reversed()
                .thenComparingInt(entry -> plan.tieOrdinals().get(entry.characterId())));
        for (int i = 0; i < ordered.size(); i++) {
            Initiative entry = ordered.get(i);
            ordered.set(i, new Initiative(entry.characterId(), entry.d20(), entry.dexterityModifier(), entry.total(), i,
                    entry.surprised(), entry.rollSource()));
        }
        return new Result(List.copyOf(ordered), List.copyOf(checks));
    }
}
