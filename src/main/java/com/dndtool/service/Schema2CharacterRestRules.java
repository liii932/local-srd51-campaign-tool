package com.dndtool.service;

import com.dndtool.module.ResourceRecoveryProfile;
import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/**
 * Explicit SRD rest, bounded time and healing transitions for the single-character INTERNAL run.
 * Its closed command history has no ongoing-effect producer. This is not a general time/effect engine.
 */
public final class Schema2CharacterRestRules {
    public static final long MAX_MINUTE = 1_000_000_000L;
    private static final Set<String> COMMANDS = Set.of("REST_STARTED", "TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT", "REST_ENDED");
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();

    public static boolean handles(Command command) { return COMMANDS.contains(command.kind()); }

    /** A supplied roll is a previously drawn server result, never an input accepted by the command. */
    public State apply(ProductionSchema2Projection material, State before, Command command, Integer roll) {
        growth.validateState(material, before); validateState(material, before);
        if (!handles(command) || before.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS - 1)
            throw invalid("S2_REST_COMMAND");
        RestDefinition definition = definition(material);
        for (ClassLevel characterClass : before.classes()) {
            List<FinalClassProgression> owners = material.projection().rules().stream()
                    .filter(FinalClassProgression.class::isInstance).map(FinalClassProgression.class::cast)
                    .filter(row -> row.enabled() && row.definition().ownerClass().equals(characterClass.characterClass())).toList();
            if (owners.size() != 1 || owners.getFirst().definition().advancementScope() != ProgressionScope.CHARACTER_ADVANCEMENT)
                throw invalid("S2_REST_GROWTH_SCOPE");
        }
        requireSupportedResourceObligations(material, before);
        if (!command.kind().equals("HIT_DIE_SPENT") && roll != null) throw invalid("S2_REST_RANDOM_RESULT");
        RestInput input = command.restInput(); RestState clock = before.rest(); RestAttempt active = clock.active();
        long hp = before.hitPoints(); List<HitDicePool> dice = before.hitDicePools(); List<ResourceState> resources = before.resources();
        RestState next;
        switch (command.kind()) {
            case "REST_STARTED" -> {
                if (active != null) throw invalid("S2_REST_ALREADY_ACTIVE");
                if (input.restKind().equals("LONG_REST") && hp < 1) throw invalid("S2_REST_START_HEALTH");
                next = new RestState(clock.minute(), clock.second(), clock.lastLongRestEndMinute(), clock.lastLongRestEndSecond(), new RestAttempt(definition.identity(),
                        input.restKind(), clock.minute(), Math.toIntExact(hp), 0, 0, 0, "IN_PROGRESS", null, 0, false));
            }
            case "TIME_ADVANCED" -> {
                long minute = clock.minute() + input.minutes();
                if (minute > MAX_MINUTE) throw invalid("S2_REST_TIME_CAPACITY");
                if (active == null) {
                    if (!input.activity().equals("NONE")) throw invalid("S2_REST_ACTIVITY");
                    next = new RestState(minute, clock.second(), clock.lastLongRestEndMinute(), clock.lastLongRestEndSecond(), null);
                } else {
                    if (!active.phase().equals("IN_PROGRESS") || input.activity().equals("NONE")) throw invalid("S2_REST_ACTIVITY");
                    long sleep = active.sleepMinutes() + (input.activity().equals("SLEEP") ? input.minutes() : 0);
                    long light = active.lightMinutes() + (input.activity().equals("LIGHT") ? input.minutes() : 0);
                    long strenuous = active.strenuousMinutes() + (input.activity().equals("STRENUOUS") ? input.minutes() : 0);
                    long consecutive = input.activity().equals("STRENUOUS")
                            ? ("STRENUOUS".equals(active.lastActivity()) ? active.consecutiveStrenuousMinutes() : 0) + input.minutes() : 0;
                    boolean interrupted = active.interrupted() || (active.kind().equals("SHORT_REST") ? strenuous > 0 : consecutive >= 60);
                    next = new RestState(minute, clock.second(), clock.lastLongRestEndMinute(), clock.lastLongRestEndSecond(), new RestAttempt(active.rule(), active.kind(),
                            active.startMinute(), active.hitPointsAtStart(), sleep, light, strenuous, active.phase(), input.activity(), consecutive, interrupted));
                }
            }
            case "REST_COMPLETED" -> {
                if (active == null || !active.phase().equals("IN_PROGRESS")) throw invalid("S2_REST_NOT_ACTIVE");
                long duration = clock.minute() - active.startMinute();
                if (active.kind().equals("SHORT_REST")) {
                    if (duration < 60 || active.interrupted()) throw invalid("S2_REST_REQUIREMENTS");
                    if (!input.recoveredHitDice().isEmpty()) throw invalid("S2_REST_HIT_DICE_CHOICE");
                    next = new RestState(clock.minute(), clock.second(), clock.lastLongRestEndMinute(), clock.lastLongRestEndSecond(), new RestAttempt(active.rule(), active.kind(),
                            active.startMinute(), active.hitPointsAtStart(), active.sleepMinutes(), active.lightMinutes(),
                            active.strenuousMinutes(), "SPENDING_HIT_DICE", active.lastActivity(), active.consecutiveStrenuousMinutes(), active.interrupted()));
                } else {
                    if (duration < 480 || active.sleepMinutes() < 360 || active.lightMinutes() > 120
                            || active.interrupted() || active.hitPointsAtStart() < 1)
                        throw invalid("S2_REST_REQUIREMENTS");
                    if (clock.lastLongRestEndMinute() != null && (clock.minute() - clock.lastLongRestEndMinute()) * 60L + clock.second() - clock.lastLongRestEndSecond() < 86400)
                        throw invalid("S2_REST_LONG_INTERVAL");
                    dice = recoverDice(before, input.recoveredHitDice());
                    hp = Schema2CharacterHealthRules.heal(hp, before.maximumHitPoints(), before.maximumHitPoints()).after();
                    next = new RestState(clock.minute(), clock.second(), clock.minute(), clock.second(), null);
                }
                resources = recoverResources(material, before, active.kind());
            }
            case "HIT_DIE_SPENT" -> {
                if (active == null || !active.phase().equals("SPENDING_HIT_DICE")) throw invalid("S2_REST_HIT_DIE_WINDOW");
                HitDicePool selected = dice.stream().filter(pool -> pool.sides() == input.hitDieSides()).findFirst()
                        .orElseThrow(() -> invalid("S2_REST_HIT_DIE_UNAVAILABLE"));
                if (selected.current() < 1) throw invalid("S2_REST_HIT_DIE_UNAVAILABLE");
                if (roll == null || roll < 1 || roll > selected.sides()) throw invalid("S2_REST_RANDOM_RESULT");
                int modifier = Math.floorDiv(before.abilities().get("ability.constitution") - 10, 2);
                hp = Schema2CharacterHealthRules.heal(hp, before.maximumHitPoints(), Math.max(0, roll + modifier)).after();
                dice = dice.stream().map(pool -> pool.sides() == selected.sides()
                        ? new HitDicePool(pool.sides(), pool.current() - 1, pool.maximum()) : pool).toList();
                next = clock;
            }
            case "REST_ENDED" -> {
                if (active == null) throw invalid("S2_REST_NOT_ACTIVE");
                next = new RestState(clock.minute(), clock.second(), clock.lastLongRestEndMinute(), clock.lastLongRestEndSecond(), null);
            }
            default -> throw invalid("S2_REST_COMMAND");
        }
        State after = copy(before, hp, dice, resources, next);
        growth.validateState(material, after); validateState(material, after); return after;
    }

    public void validateState(ProductionSchema2Projection material, State state) {
        RestState clock = state.rest();
        if (clock == null || clock.minute() < 0 || clock.minute() > MAX_MINUTE || clock.second() < 0 || clock.second() > 59
                || (clock.lastLongRestEndMinute() == null) != (clock.lastLongRestEndSecond() == null)
                || clock.lastLongRestEndSecond() != null && (clock.lastLongRestEndSecond() < 0 || clock.lastLongRestEndSecond() > 59)
                || clock.lastLongRestEndMinute() != null && (clock.lastLongRestEndMinute() < 480 || clock.lastLongRestEndMinute() > clock.minute()
                    || clock.lastLongRestEndMinute() == clock.minute() && clock.lastLongRestEndSecond() > clock.second()))
            throw invalid("S2_REST_STATE");
        RestAttempt active = clock.active();
        if (active == null) return;
        if (!definition(material).identity().equals(active.rule()) || active.startMinute() < 0 || active.startMinute() > clock.minute()
                || active.hitPointsAtStart() < 0 || active.hitPointsAtStart() > state.maximumHitPoints()
                || active.sleepMinutes() < 0 || active.lightMinutes() < 0 || active.strenuousMinutes() < 0
                || active.consecutiveStrenuousMinutes() < 0 || active.consecutiveStrenuousMinutes() > active.strenuousMinutes()
                || !"STRENUOUS".equals(active.lastActivity()) && active.consecutiveStrenuousMinutes() != 0
                || active.sleepMinutes() + active.lightMinutes() + active.strenuousMinutes() != clock.minute() - active.startMinute())
            throw invalid("S2_REST_STATE");
        if (active.phase().equals("SPENDING_HIT_DICE") && (!active.kind().equals("SHORT_REST")
                || clock.minute() - active.startMinute() < 60 || active.strenuousMinutes() != 0)) throw invalid("S2_REST_STATE");
    }

    private static RestDefinition definition(ProductionSchema2Projection material) {
        List<FinalRest> definitions = material.projection().rules().stream().filter(FinalRest.class::isInstance)
                .map(FinalRest.class::cast).filter(FinalRest::enabled).toList();
        if (definitions.size() != 1 || !"SRD_2014_REST_V1".equals(definitions.getFirst().definition().algorithm()))
            throw invalid("S2_REST_RULE_UNSUPPORTED");
        return definitions.getFirst().definition();
    }

    private static List<HitDicePool> recoverDice(State state, Map<Integer, Integer> selected) {
        int total = selected.values().stream().mapToInt(Integer::intValue).sum();
        if (total > Math.max(1, state.level() / 2)) throw invalid("S2_REST_HIT_DICE_CHOICE");
        for (var choice : selected.entrySet()) {
            HitDicePool pool = state.hitDicePools().stream().filter(item -> item.sides() == choice.getKey()).findFirst()
                    .orElseThrow(() -> invalid("S2_REST_HIT_DICE_CHOICE"));
            if (choice.getValue() < 1 || choice.getValue() > pool.maximum() - pool.current()) throw invalid("S2_REST_HIT_DICE_CHOICE");
        }
        return state.hitDicePools().stream().map(pool -> new HitDicePool(pool.sides(),
                pool.current() + selected.getOrDefault(pool.sides(), 0), pool.maximum())).toList();
    }

    private static List<ResourceState> recoverResources(ProductionSchema2Projection material, State state, String kind) {
        Map<RuleIdentity, ResourceDefinition> definitions = new HashMap<>();
        for (var rule : material.projection().rules()) if (rule instanceof FinalResource resource) {
            ResourceRecoveryProfile.parse(resource.definition().recoveryProfile());
            definitions.put(resource.identity(), resource.definition());
        }
        Map<RuleIdentity, Integer> levels = new HashMap<>();
        for (ClassLevel characterClass : state.classes()) levels.put(characterClass.characterClass(), characterClass.level());
        var trigger = ResourceRecoveryProfile.Rest.valueOf(kind);
        return state.resources().stream().map(resource -> {
            ResourceDefinition definition = definitions.get(resource.identity());
            if (definition == null || !levels.containsKey(definition.ownerClass())) throw invalid("S2_REST_RESOURCE");
            boolean recover = ResourceRecoveryProfile.parse(definition.recoveryProfile()).atLevel(levels.get(definition.ownerClass())).recoversOn(trigger);
            return recover && !resource.unlimited() ? new ResourceState(resource.identity(), resource.maximum(), resource.maximum(), false) : resource;
        }).toList();
    }

    private static void requireSupportedResourceObligations(ProductionSchema2Projection material, State state) {
        Map<RuleIdentity, Integer> levels = new HashMap<>();
        for (ClassLevel characterClass : state.classes()) levels.put(characterClass.characterClass(), characterClass.level());
        int charisma = Math.floorDiv(state.abilities().get("ability.charisma") - 10, 2);
        for (var rule : material.projection().rules()) if (rule instanceof FinalResource resource) {
            ResourceDefinition definition = resource.definition();
            var capacity = AdvancementValueProfile.parse(definition.maximumProfile());
            ResourceRecoveryProfile.parse(definition.recoveryProfile());
            if (!resource.enabled() || !levels.containsKey(definition.ownerClass())) continue;
            var acquired = capacity.atLevel(levels.get(definition.ownerClass()), charisma);
            if ((acquired.unlimited() || acquired.maximum() > 0) && definition.executionMode() == ResourceExecution.BLOCKED)
                throw invalid("S2_REST_RESOURCE_UNSUPPORTED");
        }
    }

    private static State copy(State before, long hp, List<HitDicePool> dice, List<ResourceState> resources, RestState clock) {
        return new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(), before.characterClass(),
                before.abilities(), before.level(), before.proficiencyBonus(), hp, before.maximumHitPoints(), before.classes(), dice,
                before.subclasses(), before.features(), before.feats(), before.proficiencies(), before.version() + 1, resources, clock, before.equipment(), before.health(), before.spellbook());
    }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
