package com.dndtool.service;

import com.dndtool.service.Schema2CharacterCheckRules.ContestOutcome;
import com.dndtool.service.Schema2CharacterCheckRules.PreparedRoll;
import com.dndtool.service.Schema2CharacterCheckRules.RollInput;
import com.dndtool.service.Schema2CharacterCheckRules.RolledScore;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatRules.combatant;
import static com.dndtool.service.Schema2CombatRules.key;
import static com.dndtool.service.Schema2CombatRules.replaceActor;
import static com.dndtool.service.Schema2CombatState.*;

/** Attack action replacements and the relation that owns each Grappled condition source. */
public final class Schema2MeleeControlRules {
    public static final int REACH_FEET = 5;
    private static final String FROZEN = "ACTION_FLOW_FROZEN_DATA_INVALID";
    private static final Set<Kind> REPLACEMENTS = Set.of(Kind.GRAPPLE, Kind.SHOVE_PRONE, Kind.SHOVE_PUSH);
    private final Schema2CharacterCheckRules checks = new Schema2CharacterCheckRules();

    record ContestPlan(MeleeContest contest, PreparedRoll initiator, PreparedRoll responder) { }

    static boolean replacesAttack(Kind kind) { return REPLACEMENTS.contains(kind); }

    static boolean handles(Kind kind) {
        return replacesAttack(kind) || Set.of(Kind.CHOOSE_MELEE_DEFENSE, Kind.ESCAPE_GRAPPLE, Kind.RELEASE_GRAPPLE).contains(kind);
    }

    static void validateCommand(Command command) {
        Kind kind = command.kind();
        MeleeInput input = command.meleeInput();
        require(input != null && command.actorId() != null && command.equals(Command.melee(kind, command.actorId(),
                command.targetId(), command.flowKey(), command.leafKey(), input)), "S2_COMBAT_COMMAND");
        uuid(command.actorId());
        if (command.targetId() != null) uuid(command.targetId());
        if (command.flowKey() != null) uuid(command.flowKey());
        if (command.leafKey() != null) uuid(command.leafKey());
        if (input.grappleKey() != null) uuid(input.grappleKey());
        if (input.sequenceKey() != null) uuid(input.sequenceKey());

        boolean replacement = replacesAttack(kind);
        boolean escape = kind == Kind.ESCAPE_GRAPPLE;
        boolean choice = kind == Kind.CHOOSE_MELEE_DEFENSE;
        boolean release = kind == Kind.RELEASE_GRAPPLE;
        boolean flow = command.flowKey() != null;
        require((command.targetId() != null) == replacement
                && (input.sequenceKey() != null) == replacement && (input.attackOrdinal() != null) == replacement
                && (!replacement || input.attackOrdinal() >= 0 && input.attackOrdinal() < 2)
                && (input.grappleKey() != null) == (escape || release)
                && (input.hand() != null) == (kind == Kind.GRAPPLE)
                && (input.destination() != null) == (kind == Kind.SHOVE_PUSH)
                && (input.skill() != null) == (escape || choice)
                && flow == (command.leafKey() != null) && (choice ? flow : release || !flow), "S2_COMBAT_COMMAND");
    }

    ContestPlan prepare(UUID operation, Map<UUID, Context> roster, Battle battle, Command command) {
        require(battle.status() == Status.ACTIVE, "S2_COMBAT_REQUIRED");
        UUID actorId = command.actorId();
        if (command.kind() == Kind.RELEASE_GRAPPLE) {
            checkCurrentLeaf(battle, command);
            require(activeGrapple(battle, actorId, command.meleeInput().grappleKey()) != null, "S2_COMBAT_GRAPPLE_UNAVAILABLE");
            return null;
        }

        boolean choice = command.kind() == Kind.CHOOSE_MELEE_DEFENSE;
        MeleeContest contest;
        ContestSkill firstSkill;
        ContestSkill secondSkill;
        if (choice) {
            Flow flow = checkCurrentLeaf(battle, command);
            require(flow != null && flow.frames().stream().anyMatch(frame -> frame.frameKey().equals(command.leafKey())
                    && frame.kind() == FrameKind.DOMAIN_CHOICE && frame.status() == FrameStatus.PENDING),
                    "ACTION_FLOW_STAGE_INVALID");
            contest = battle.meleeControl().contests().stream()
                    .filter(value -> value.frameKey().equals(flow.rootFrameKey()) && value.outcome() == null)
                    .findFirst().orElseThrow(() -> invalid("ACTION_FLOW_STAGE_INVALID"));
            require(contest.targetId().equals(actorId), "ACTION_CHOICE_OPTION_INVALID");
            require(contest.turnNumber() == battle.turnNumber() && contest.actorId().equals(battle.activeActor()), FROZEN);
            firstSkill = ContestSkill.ATHLETICS;
            secondSkill = command.meleeInput().skill();
        } else {
            require(actorId.equals(battle.activeActor()), "S2_COMBAT_NOT_CURRENT_TURN");
            require(battle.flows().size() < Schema2CombatRules.MAX_FLOWS, "ACTION_FLOW_LIMIT_EXCEEDED");
            var input = command.meleeInput();
            boolean escape = command.kind() == Kind.ESCAPE_GRAPPLE;
            GrappleRelation relation = escape ? grappleForEscape(battle, actorId, input.grappleKey()) : null;
            UUID targetId = escape ? relation.grapplerId() : command.targetId();
            require(!actorId.equals(targetId), "S2_COMBAT_TARGET");
            Context actorContext = context(roster, actorId);
            Context targetContext = context(roster, targetId);
            Combatant actor = combatant(battle, actorId), target = combatant(battle, targetId);
            CreatureSize actorSize = size(actorContext), targetSize = size(targetContext);
            if (escape) {
                require(actor.actionAvailable(), "S2_COMBAT_ACTION_UNAVAILABLE");
            } else {
                checkReplacement(battle, command, actorContext, actor, target, actorSize, targetSize);
            }

            CheckDefinition definition = checkDefinition(actorContext);
            UUID contestKey = key(operation, "melee-contest");
            UUID grappleKey = escape ? input.grappleKey() : command.kind() == Kind.GRAPPLE ? key(operation, "grapple") : null;
            contest = new MeleeContest(contestKey, key(operation, "melee-contest-frame"),
                    escape ? null : key(operation, "melee-choice"), command.kind(), actorId, targetId, battle.turnNumber(),
                    input.sequenceKey(), input.attackOrdinal(), grappleKey, input.hand(), actor.position(), target.position(),
                    actorSize, targetSize, input.destination(), definition.identity(), definition.contestMinimumWinningMargin(),
                    null, null, null, null);
            firstSkill = escape ? input.skill() : ContestSkill.ATHLETICS;
            secondSkill = escape ? ContestSkill.ATHLETICS : null;
        }

        Context initiatorContext = context(roster, contest.actorId());
        Context responderContext = context(roster, contest.targetId());
        CheckDefinition definition = checkDefinition(initiatorContext);
        require(definition.identity().equals(contest.checkRule())
                && definition.contestMinimumWinningMargin() == contest.minimumWinningMargin(), FROZEN);
        PreparedRoll initiator = prepareSide(initiatorContext, contest.checkRule(), firstSkill);
        if (secondSkill != null)
            return new ContestPlan(completedSelection(contest, command.meleeInput().skill()),
                    initiator, prepareSide(responderContext, contest.checkRule(), secondSkill));

        // Admit the pending choice only when both offered skills and their full consequences are executable.
        for (ContestSkill skill : ContestSkill.values()) prepareSide(responderContext, contest.checkRule(), skill);
        return new ContestPlan(contest, initiator, null);
    }

    private static Context context(Map<UUID, Context> roster, UUID actor) {
        Context context = roster.get(actor);
        require(context != null, "S2_COMBAT_RULE_CHARACTER_REQUIRED");
        require(context.character().hitPoints() > 0 && !context.character().health().dead(), "S2_COMBAT_ACTOR_INACTIVE");
        return context;
    }

    private static GrappleRelation grappleForEscape(Battle battle, UUID actor, UUID grappleKey) {
        return battle.meleeControl().grapples().stream().filter(value -> value.active() && value.targetId().equals(actor)
                && value.grappleKey().equals(grappleKey)).findFirst().orElseThrow(() -> invalid("S2_COMBAT_GRAPPLE_UNAVAILABLE"));
    }

    private static Flow checkCurrentLeaf(Battle battle, Command command) {
        Flow flow = battle.activeFlow();
        require(flow == null ? command.flowKey() == null
                : flow.flowKey().equals(command.flowKey()) && flow.leafFrameKey().equals(command.leafKey()),
                "ACTION_FLOW_TARGET_MISMATCH");
        return flow;
    }

    private static void checkReplacement(Battle battle, Command command, Context context, Combatant actor, Combatant target,
                                         CreatureSize actorSize, CreatureSize targetSize) {
        var input = command.meleeInput();
        var sequence = actor.sequence();
        require(sequence != null && !sequence.closed(), "S2_COMBAT_SEQUENCE_UNAVAILABLE");
        require(sequence.sequenceKey().equals(input.sequenceKey()) && sequence.performed() == input.attackOrdinal(),
                "ACTION_FLOW_TARGET_MISMATCH");
        require(actor.position().distanceFeet(target.position()) == REACH_FEET, "S2_COMBAT_OUT_OF_REACH");
        require(targetSize.ordinal() - actorSize.ordinal() <= 1, "S2_COMBAT_TARGET_SIZE");
        if (command.kind() == Kind.GRAPPLE) {
            require(handFree(battle, context.character(), input.hand()), "S2_COMBAT_HAND_UNAVAILABLE");
            require(activeGrapples(battle, actor.characterId()).stream().noneMatch(r -> r.targetId().equals(target.characterId())),
                    "S2_COMBAT_ALREADY_GRAPPLED");
        }
        if (command.kind() == Kind.SHOVE_PUSH) {
            require(input.destination().equals(pushDestination(actor.position(), target.position()))
                    && battle.combatants().stream().noneMatch(value -> value.position().equals(input.destination())),
                    "S2_COMBAT_SHOVE_DESTINATION");
            // Prove the area suppression write before admitting a possible push.
            Battle projected = replaceActor(battle, at(target, input.destination()));
            Schema2OngoingEffectRules.reconcile(projected, key(battle.combatId(), "shove-capacity-" + battle.version()));
        }
        for (ReadyPreparation ready : battle.readyPreparations()) {
            if (ready.status() != ReadyStatus.ARMED) continue;
            ReadyTrigger trigger = ready.input().trigger();
            boolean attack = trigger.kind() == TriggerKind.ATTACK_COMPLETED && trigger.subjectId().equals(actor.characterId());
            boolean entry = command.kind() == Kind.SHOVE_PUSH && trigger.kind() == TriggerKind.CREATURE_ENTER_CELL_AFTER
                    && trigger.subjectId().equals(target.characterId()) && input.destination().equals(trigger.position());
            require(!attack && !entry, "ACTION_FLOW_RULE_UNSUPPORTED");
        }
    }

    private static CheckDefinition checkDefinition(Context context) {
        var matches = context.material().projection().rules().stream().filter(FinalCheck.class::isInstance)
                .map(FinalCheck.class::cast).filter(FinalCheck::enabled).map(FinalCheck::definition).toList();
        require(matches.size() == 1, "S2_CHECK_RULE_UNSUPPORTED");
        CheckDefinition definition = matches.getFirst();
        require(Schema2CharacterCheckRules.ALGORITHM.equals(definition.algorithm())
                && validMargin(definition.contestMinimumWinningMargin()), "S2_CHECK_RULE_UNSUPPORTED");
        return definition;
    }

    private PreparedRoll prepareSide(Context context, RuleIdentity rule, ContestSkill skill) {
        String baseModule = context.material().members().getFirst().moduleKey();
        RuleIdentity proficiency = new RuleIdentity(baseModule, "character.proficiency", skill.ruleKey());
        return checks.prepareContestRoll(context.material(), context.character(),
                new RollInput(rule, "SKILL", skill.abilityKey(), proficiency, false, false));
    }

    Battle resolve(UUID operation, Battle before, Command command, ContestPlan plan,
                   Schema2ActionFlowRules.Roller dice, List<CombatFact> facts) {
        if (command.kind() == Kind.RELEASE_GRAPPLE)
            return end(before, command.meleeInput().grappleKey(), GrappleEndReason.RELEASED, operation, facts);

        MeleeContest contest = plan.contest();
        if (replacesAttack(command.kind())) {
            Flow flow = contestFlow(key(operation, "melee-flow"), contest, key(operation, "melee-choice-frame"));
            facts.add(new CombatFact("MELEE_DEFENSE_REQUIRED", contest.targetId(), contest.choiceKey(), contest.kind().name()));
            return withContest(Schema2CombatRules.appendFlow(before, flow), contest);
        }

        boolean escape = command.kind() == Kind.ESCAPE_GRAPPLE;
        Battle after = before;
        Combatant initiator = combatant(before, contest.actorId());
        if (escape) {
            after = replaceActor(after, new Combatant(initiator.characterId(), initiator.position(), initiator.side(), false,
                    initiator.reactionAvailable(), initiator.movementSpentHalfFeet(), initiator.movementBonus(),
                    initiator.disengaged(), initiator.dodging(), initiator.sequence(), initiator.prone()));
            facts.add(new CombatFact("ESCAPE_ACTION_SPENT", initiator.characterId(), contest.contestKey(), null));
        }
        RolledScore first = roll(dice, contest.actorId(), plan.initiator(), "MELEE_CONTEST_INITIATOR");
        RolledScore second = roll(dice, contest.targetId(), plan.responder(), "MELEE_CONTEST_RESPONDER");
        ContestOutcome outcome = Schema2CharacterCheckRules.compareContest(first.total(), second.total(), contest.minimumWinningMargin());
        MeleeContest complete = withResult(contest, first, second, outcome);

        if (escape) {
            after = Schema2CombatRules.appendFlow(after, contestFlow(key(operation, "melee-flow"), complete, null));
        } else {
            AttackSequence sequence = initiator.sequence();
            int performed = sequence.performed() + 1;
            after = replaceActor(after, Schema2CombatRules.withSequence(initiator, new AttackSequence(sequence.sequenceKey(),
                    sequence.limit(), performed, sequence.forfeited(), performed + sequence.forfeited() == sequence.limit())));
            Flow flow = before.activeFlow();
            after = replaceFlow(after, contestFlow(flow.flowKey(), complete, flow.leafFrameKey()));
            facts.add(new CombatFact("ATTACK_ORDINAL_SPENT", initiator.characterId(), contest.contestKey(), contest.kind().name()));
        }
        after = withContest(after, complete);
        facts.add(new CombatFact("MELEE_CONTEST_RESOLVED", initiator.characterId(), complete.contestKey(), outcome.name()));
        if (outcome != ContestOutcome.INITIATOR_WINS) return after;

        Combatant target = combatant(after, contest.targetId());
        return switch (contest.kind()) {
            case GRAPPLE -> establish(after, contest, facts);
            case ESCAPE_GRAPPLE -> end(after, contest.grappleKey(), GrappleEndReason.ESCAPED, operation, facts);
            case SHOVE_PRONE -> {
                facts.add(new CombatFact("SHOVED_PRONE", target.characterId(), contest.contestKey(), null));
                yield replaceActor(after, Schema2CombatRules.withPosture(target, true, target.movementSpentHalfFeet()));
            }
            case SHOVE_PUSH -> {
                facts.add(new CombatFact("SHOVED_FIVE_FEET", target.characterId(), contest.contestKey(), null));
                yield replaceActor(after, at(target, contest.destination()));
            }
            default -> throw invalid("S2_COMBAT_COMMAND");
        };
    }

    private RolledScore roll(Schema2ActionFlowRules.Roller dice, UUID subject, PreparedRoll prepared, String purpose) {
        int first = dice.draw(purpose, subject, 20);
        Integer second = prepared.secondDieRequired() ? dice.draw(purpose + "_SECOND", subject, 20) : null;
        return checks.resolveRoll(prepared, first, second);
    }

    private static MeleeContest completedSelection(MeleeContest contest, ContestSkill skill) {
        return new MeleeContest(contest.contestKey(), contest.frameKey(), contest.choiceKey(), contest.kind(),
                contest.actorId(), contest.targetId(), contest.turnNumber(), contest.sequenceKey(), contest.attackOrdinal(),
                contest.grappleKey(), contest.hand(), contest.actorPosition(), contest.targetPosition(), contest.actorSize(),
                contest.targetSize(), contest.destination(), contest.checkRule(), contest.minimumWinningMargin(),
                skill, null, null, null);
    }

    private static MeleeContest withResult(MeleeContest contest, RolledScore initiator, RolledScore responder, ContestOutcome outcome) {
        return new MeleeContest(contest.contestKey(), contest.frameKey(), contest.choiceKey(), contest.kind(),
                contest.actorId(), contest.targetId(), contest.turnNumber(), contest.sequenceKey(), contest.attackOrdinal(),
                contest.grappleKey(), contest.hand(), contest.actorPosition(), contest.targetPosition(), contest.actorSize(),
                contest.targetSize(), contest.destination(), contest.checkRule(), contest.minimumWinningMargin(),
                contest.selectedSkill(), initiator, responder, outcome);
    }

    private static Flow contestFlow(UUID flowKey, MeleeContest contest, UUID leafFrame) {
        boolean pending = contest.outcome() == null;
        Stage stage = pending ? Stage.MELEE_CONTROL_CHOICE : Stage.MELEE_CONTROL_RESOLVED;
        Frame root = new Frame(contest.frameKey(), null, FrameKind.GRAPPLE_CONTEST, contest.contestKey(),
                Invocation.ROOT, pending ? FrameStatus.WAITING_CHOICE : FrameStatus.TERMINAL, stage, null, null);
        List<Frame> frames = leafFrame == null ? List.of(root)
                : List.of(root, new Frame(leafFrame, root.frameKey(), FrameKind.DOMAIN_CHOICE, contest.choiceKey(),
                    Invocation.CONTINUATION, pending ? FrameStatus.PENDING : FrameStatus.TERMINAL, stage, null, null));
        return new Flow(flowKey, root.frameKey(), pending ? leafFrame : null, pending ? FlowStatus.ACTIVE : FlowStatus.COMPLETED,
                frames, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Battle replaceFlow(Battle battle, Flow replacement) {
        return Schema2CombatRules.copy(battle, battle.status(), battle.round(), battle.turnNumber(), battle.activeOrdinal(),
                battle.combatants(), battle.flows().stream()
                        .map(flow -> flow.flowKey().equals(replacement.flowKey()) ? replacement : flow).toList());
    }

    private static Battle withContest(Battle battle, MeleeContest contest) {
        var contests = new ArrayList<>(battle.meleeControl().contests());
        int i = 0;
        while (i < contests.size() && !contests.get(i).contestKey().equals(contest.contestKey())) i++;
        if (i == contests.size()) contests.add(contest);
        else contests.set(i, contest);
        return withControl(battle, new MeleeControl(contests, battle.meleeControl().grapples()));
    }

    private static Battle establish(Battle battle, MeleeContest contest, List<CombatFact> facts) {
        var relations = new ArrayList<>(battle.meleeControl().grapples());
        relations.add(new GrappleRelation(contest.grappleKey(), contest.actorId(), contest.targetId(), contest.hand(),
                REACH_FEET, contest.contestKey(), null, null));
        facts.add(new CombatFact("GRAPPLE_ESTABLISHED", contest.targetId(), contest.grappleKey(), null));
        Combatant target = combatant(battle, contest.targetId());
        if (target.dodging()) battle = replaceActor(battle, new Combatant(target.characterId(), target.position(), target.side(),
                target.actionAvailable(), target.reactionAvailable(), target.movementSpentHalfFeet(), target.movementBonus(),
                target.disengaged(), false, target.sequence(), target.prone()));
        return withControl(battle, new MeleeControl(battle.meleeControl().contests(), relations));
    }

    private static Battle end(Battle battle, UUID key, GrappleEndReason reason, UUID operation, List<CombatFact> facts) {
        List<GrappleRelation> relations = new ArrayList<>(battle.meleeControl().grapples());
        for (int i = 0; i < relations.size(); i++) {
            var relation = relations.get(i);
            if (!relation.active() || !relation.grappleKey().equals(key)) continue;
            relations.set(i, new GrappleRelation(key, relation.grapplerId(), relation.targetId(), relation.hand(),
                    relation.reachFeet(), relation.originatingContest(), reason, operation));
            facts.add(new CombatFact("GRAPPLE_ENDED", relation.targetId(), key, reason.name()));
            return withControl(battle, new MeleeControl(battle.meleeControl().contests(), relations));
        }
        throw invalid(FROZEN);
    }

    private static Battle withControl(Battle b, MeleeControl control) {
        return new Battle(b.combatId(), b.campaignId(), b.combatRule(), b.status(), b.round(), b.turnNumber(),
                b.activeOrdinal(), b.version(), b.combatants(), b.initiative(), b.flows(), b.ongoingEffects(),
                b.readyPreparations(), b.triggerFacts(), b.shields(), b.concentrationHolders(), b.environmentZones(),
                b.suppressionCauses(), b.monsters(), b.saveDamageBatches(), b.monsterRests(), control);
    }

    static Battle reconcile(Battle battle, Map<UUID, Context> roster, List<HealthChange> changes,
                            UUID operation, List<CombatFact> facts) {
        if (battle.meleeControl().grapples().stream().noneMatch(GrappleRelation::active)) return battle;
        require(battle.status() == Status.ACTIVE, FROZEN);
        var positions = new HashMap<UUID, Position>();
        battle.combatants().forEach(actor -> positions.put(actor.characterId(), actor.position()));
        var latest = new HashMap<UUID, State>();
        roster.forEach((id, context) -> latest.put(id, context.character()));
        changes.forEach(change -> latest.put(change.characterId(), change.after()));
        for (GrappleRelation relation : battle.meleeControl().grapples()) {
            if (!relation.active()) continue;
            GrappleEndReason reason = endReason(relation, positions, latest);
            if (reason != null) battle = end(battle, relation.grappleKey(), reason, operation, facts);
        }
        return battle;
    }

    private static GrappleEndReason endReason(GrappleRelation relation, Map<UUID, Position> positions, Map<UUID, State> latest) {
        Position grappler = positions.get(relation.grapplerId()), target = positions.get(relation.targetId());
        if (grappler == null || target == null) return GrappleEndReason.DEPARTED;
        State source = latest.get(relation.grapplerId()), held = latest.get(relation.targetId());
        require(source != null && held != null, FROZEN);
        if (source.hitPoints() == 0 || source.health().dead()) return GrappleEndReason.GRAPPLER_INCAPACITATED;
        if (held.health().dead()) return GrappleEndReason.TARGET_DIED;
        if (grappler.distanceFeet(target) > relation.reachFeet()) return GrappleEndReason.OUT_OF_REACH;
        return null;
    }

    static void validateActive(Battle battle, Map<UUID, Context> roster) {
        for (GrappleRelation relation : battle.meleeControl().grapples()) {
            if (!relation.active()) continue;
            Context source = roster.get(relation.grapplerId()), target = roster.get(relation.targetId());
            require(source != null && target != null && source.character().hitPoints() > 0
                    && !target.character().health().dead() && equipmentHandFree(source.character(), relation.hand()), FROZEN);
        }
    }

    public static boolean grappled(Battle battle, UUID actor) {
        return battle.meleeControl().grapples().stream().anyMatch(value -> value.active() && value.targetId().equals(actor));
    }

    public static List<GrappleRelation> activeGrapples(Battle battle, UUID actor) {
        return battle.meleeControl().grapples().stream().filter(value -> value.active() && value.grapplerId().equals(actor)).toList();
    }

    public static GrappleRelation activeGrapple(Battle battle, UUID actor, UUID grappleKey) {
        return activeGrapples(battle, actor).stream().filter(value -> value.grappleKey().equals(grappleKey)).findFirst().orElse(null);
    }

    public static boolean handFree(Battle battle, State character, HandSlot hand) {
        return equipmentHandFree(character, hand)
                && activeGrapples(battle, character.characterId()).stream().noneMatch(value -> value.hand() == hand);
    }

    private static boolean equipmentHandFree(State character, HandSlot hand) {
        return character.equipment().stream().noneMatch(item -> item.location().equals(hand.name()) || item.location().equals("BOTH_HANDS"));
    }

    public static CreatureSize size(Context context) {
        RuleIdentity race = context.initial().character().sources().race();
        var matches = context.material().projection().rules().stream().filter(FinalCharacterSource.class::isInstance)
                .map(FinalCharacterSource.class::cast).filter(source -> source.identity().equals(race)).toList();
        require(matches.size() == 1 && matches.getFirst().enabled() && race.ruleType().equals("character.race")
                && Set.of("race.human", "race.dwarf").contains(race.ruleKey()), "S2_COMBAT_SIZE_UNSUPPORTED");
        return CreatureSize.MEDIUM;
    }

    public static Position pushDestination(Position actor, Position target) {
        return new Position(target.x() + Integer.signum(target.x() - actor.x()),
                target.y() + Integer.signum(target.y() - actor.y()));
    }

    private static Combatant at(Combatant actor, Position position) {
        return new Combatant(actor.characterId(), position, actor.side(), actor.actionAvailable(), actor.reactionAvailable(),
                actor.movementSpentHalfFeet(), actor.movementBonus(), actor.disengaged(), actor.dodging(), actor.sequence(), actor.prone());
    }

    static void validate(Battle battle) {
        var control = battle.meleeControl();
        var contests = new HashMap<UUID, MeleeContest>();
        var flowsByRoot = new HashMap<UUID, Flow>();
        for (Flow flow : battle.flows()) {
            boolean containsContestFrame = flow.frames().stream()
                    .anyMatch(frame -> frame.kind() == FrameKind.GRAPPLE_CONTEST || frame.kind() == FrameKind.DOMAIN_CHOICE);
            if (containsContestFrame) {
                require(flow.frames().getFirst().kind() == FrameKind.GRAPPLE_CONTEST, FROZEN);
                require(flowsByRoot.put(flow.rootFrameKey(), flow) == null, FROZEN);
            }
        }
        require(control.contests().size() <= Schema2CombatRules.MAX_FLOWS
                && control.grapples().size() <= control.contests().size(), FROZEN);

        for (MeleeContest contest : control.contests()) {
            uuid(contest.contestKey());
            uuid(contest.actorId());
            uuid(contest.targetId());
            require(contests.put(contest.contestKey(), contest) == null && !contest.actorId().equals(contest.targetId())
                    && contest.turnNumber() >= 1 && contest.turnNumber() <= battle.turnNumber()
                    && contest.checkRule() != null && "adventure.check".equals(contest.checkRule().ruleType())
                    && validMargin(contest.minimumWinningMargin())
                    && contest.actorSize() != null && contest.targetSize() != null && contest.actorPosition() != null
                    && contest.targetPosition() != null && contest.actorPosition().distanceFeet(contest.targetPosition()) == REACH_FEET, FROZEN);
            boolean replacement = replacesAttack(contest.kind()), grapple = contest.kind() == Kind.GRAPPLE;
            boolean pending = contest.outcome() == null;
            require((replacement || contest.kind() == Kind.ESCAPE_GRAPPLE)
                    && replacement == (contest.sequenceKey() != null) && replacement == (contest.attackOrdinal() != null)
                    && replacement == (contest.choiceKey() != null) && grapple == (contest.hand() != null)
                    && (grapple || !replacement) == (contest.grappleKey() != null)
                    && (!replacement || contest.targetSize().ordinal() - contest.actorSize().ordinal() <= 1
                        && contest.attackOrdinal() >= 0 && contest.attackOrdinal() < 2)
                    && (contest.kind() == Kind.SHOVE_PUSH) == (contest.destination() != null)
                    && (contest.destination() == null || contest.destination().equals(pushDestination(contest.actorPosition(), contest.targetPosition()))), FROZEN);
            require(pending == (contest.selectedSkill() == null) && pending == (contest.initiator() == null)
                    && pending == (contest.responder() == null), FROZEN);

            Flow flow = flowsByRoot.remove(contest.frameKey());
            require(flow != null && flow.frames().size() == (replacement ? 2 : 1), FROZEN);
            UUID leaf = replacement ? flow.frames().getLast().frameKey() : null;
            require(flow.equals(contestFlow(flow.flowKey(), contest, leaf)), FROZEN);
            if (pending) {
                require(replacement && contest.turnNumber() == battle.turnNumber() && contest.actorId().equals(battle.activeActor())
                        && combatant(battle, contest.actorId()).position().equals(contest.actorPosition())
                        && combatant(battle, contest.targetId()).position().equals(contest.targetPosition()), FROZEN);
            } else {
                validateScore(contest.initiator());
                validateScore(contest.responder());
                require(contest.outcome() == Schema2CharacterCheckRules.compareContest(contest.initiator().total(),
                        contest.responder().total(), contest.minimumWinningMargin()), FROZEN);
            }
        }
        require(flowsByRoot.isEmpty(), FROZEN);

        Set<UUID> keys = new HashSet<>(), established = new HashSet<>();
        Set<List<UUID>> pairs = new HashSet<>();
        Set<Map.Entry<UUID, HandSlot>> hands = new HashSet<>();
        for (GrappleRelation relation : control.grapples()) {
            uuid(relation.grappleKey());
            var source = contests.get(relation.originatingContest());
            require(keys.add(relation.grappleKey()) && established.add(relation.originatingContest())
                    && source != null && source.kind() == Kind.GRAPPLE && source.outcome() == ContestOutcome.INITIATOR_WINS
                    && source.grappleKey().equals(relation.grappleKey()) && source.actorId().equals(relation.grapplerId())
                    && source.targetId().equals(relation.targetId()) && source.hand() == relation.hand()
                    && relation.reachFeet() == REACH_FEET && relation.active() == (relation.endedOperationId() == null), FROZEN);
            if (relation.active()) {
                require(battle.status() == Status.ACTIVE
                        && pairs.add(List.of(relation.grapplerId(), relation.targetId()))
                        && hands.add(Map.entry(relation.grapplerId(), relation.hand()))
                        && combatant(battle, relation.grapplerId()).position()
                            .distanceFeet(combatant(battle, relation.targetId()).position()) == relation.reachFeet(), FROZEN);
            } else uuid(relation.endedOperationId());
        }
        for (MeleeContest contest : control.contests()) {
            if (contest.kind() == Kind.GRAPPLE && contest.outcome() == ContestOutcome.INITIATOR_WINS)
                require(established.contains(contest.contestKey()), FROZEN);
            if (contest.kind() == Kind.ESCAPE_GRAPPLE) {
                var relation = control.grapples().stream().filter(r -> r.grappleKey().equals(contest.grappleKey())).findFirst().orElse(null);
                require(relation != null && relation.grapplerId().equals(contest.targetId()) && relation.targetId().equals(contest.actorId())
                        && (contest.outcome() != ContestOutcome.INITIATOR_WINS || relation.endReason() == GrappleEndReason.ESCAPED), FROZEN);
            }
        }
    }

    private static void validateScore(RolledScore score) {
        require(score.firstD20() >= 1 && score.firstD20() <= 20
                && (score.effectiveAdvantage() != score.effectiveDisadvantage()) == (score.secondD20() != null)
                && (score.secondD20() == null || score.secondD20() >= 1 && score.secondD20() <= 20)
                && score.abilityModifier() >= -5 && score.abilityModifier() <= 10
                && score.proficiencyBonus() >= 0 && score.proficiencyBonus() <= 6, FROZEN);
        int selected = score.secondD20() == null ? score.firstD20()
                : score.effectiveAdvantage() ? Math.max(score.firstD20(), score.secondD20()) : Math.min(score.firstD20(), score.secondD20());
        require(selected == score.selectedD20() && selected + score.abilityModifier() + score.proficiencyBonus() == score.total(), FROZEN);
    }

    private static boolean validMargin(int margin) {
        return margin >= MIN_CONTEST_WINNING_MARGIN && margin <= MAX_CONTEST_WINNING_MARGIN;
    }

    private static void require(boolean result, String reason) {
        if (!result) throw invalid(reason);
    }
}
