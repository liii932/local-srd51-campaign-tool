package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.FinalCheck;
import com.dndtool.module.composition.Schema2CompositionKernel.FinalProficiency;
import com.dndtool.module.composition.Schema2CompositionKernel.ProficiencyCategory;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterCheckRules.PreparedRoll;
import com.dndtool.service.Schema2CharacterCheckRules.RollInput;
import com.dndtool.service.Schema2CombatRules.Context;
import java.util.*;

import static com.dndtool.service.Schema2CombatState.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.GOBLIN_MONSTER;

/** Observer-relative sight and knowledge for the current five-foot grid. */
public final class Schema2ObservationRules {
    private Schema2ObservationRules() { }

    private static final int MAX_CELLS = 128;
    private static final Comparator<UUID> UUID_ORDER = Schema2CombatHistory.UUID_ORDER;
    private static final Comparator<Position> POSITION_ORDER = Comparator.comparingInt(Position::x).thenComparingInt(Position::y);
    private static final Comparator<PairKey> PAIR_ORDER = Comparator.comparing(PairKey::observer, UUID_ORDER)
            .thenComparing(PairKey::subject, UUID_ORDER);
    private static final String STEALTH = "skill.stealth", PERCEPTION = "skill.perception";
    private static final String DEXTERITY = "ability.dexterity", WISDOM = "ability.wisdom";
    private static final Schema2CharacterCheckRules CHECKS = new Schema2CharacterCheckRules();

    private record PairKey(UUID observer, UUID subject) { }
    private record Ray(boolean blocked, Cover cover) { }
    record CheckBasis(RuleIdentity rule, RuleIdentity skill, String monsterSkill, PreparedRoll characterRoll,
                      int abilityModifier, int skillBonus) {
        boolean secondDieRequired() { return characterRoll != null && characterRoll.secondDieRequired(); }
    }
    record Plan(CheckBasis basis, RuleIdentity nimbleSource, List<ObservationContest> contests) { }

    @FunctionalInterface
    interface Draw { int d20(String purpose, UUID actor); }

    public static boolean visible(Battle battle, UUID observer, UUID subject) {
        Combatant viewer = member(battle, observer), target = member(battle, subject);
        return viewer != null && target != null && (observer.equals(subject)
                || !hidden(battle, observer, subject) && visible(battle, viewer.position(), target.position()));
    }

    public static boolean visible(Battle battle, Position source, Position target) {
        return !ray(battle.observation().cells(), source, target).blocked();
    }

    public static boolean hidden(Battle battle, UUID observer, UUID subject) {
        ObservationPair pair = pair(battle, observer, subject);
        return pair != null && pair.hideOperationId() != null;
    }

    public static Position knownPosition(Battle battle, UUID observer, UUID subject) {
        if (visible(battle, observer, subject)) return member(battle, subject).position();
        ObservationPair pair = pair(battle, observer, subject);
        return pair == null ? null : pair.lastKnownPosition();
    }

    public static Cover cover(Battle battle, UUID observer, UUID subject) {
        return cover(battle, Schema2CombatRules.combatant(battle, observer).position(),
                Schema2CombatRules.combatant(battle, subject).position());
    }

    public static Cover cover(Battle battle, Position source, Position target) {
        return ray(battle.observation().cells(), source, target).cover();
    }

    public static boolean hideAvailable(Battle battle, UUID actor) {
        Combatant combatant = member(battle, actor);
        return combatant != null && hidingCell(battle.observation().cells(), combatant.position());
    }

    private static Combatant member(Battle battle, UUID id) {
        return battle.combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElse(null);
    }

    private static ObservationPair pair(Battle battle, UUID observer, UUID subject) {
        return battle.observation().pairs().stream().filter(value -> value.observerId().equals(observer)
                && value.subjectId().equals(subject)).findFirst().orElse(null);
    }

    private static SortedMap<PairKey, ObservationPair> pairs(ObservationState state) {
        SortedMap<PairKey, ObservationPair> pairs = new TreeMap<>(PAIR_ORDER);
        for (ObservationPair pair : state.pairs()) pairs.put(new PairKey(pair.observerId(), pair.subjectId()), pair);
        return pairs;
    }

    private static Map<UUID, ObservationCheck> checks(ObservationState state) {
        Map<UUID, ObservationCheck> checks = new HashMap<>();
        for (ObservationCheck check : state.checks()) checks.put(check.operationId(), check);
        return checks;
    }

    private static boolean hidingCell(List<ObservationCellInput> cells, Position position) {
        return cells.stream().anyMatch(cell -> cell.hideAllowed() && cell.position().equals(position));
    }

    private static Ray ray(List<ObservationCellInput> cells, Position source, Position target) {
        Cover highest = Cover.NONE;
        boolean blocked = false;
        for (ObservationCellInput cell : cells) {
            if (cell.position().equals(source) || !crosses(source, target, cell.position())) continue;
            if (cell.cover() == Cover.TOTAL) return new Ray(true, Cover.TOTAL);
            if (cell.blocksSight()) blocked = true;
            if (cell.cover().ordinal() > highest.ordinal()) highest = cell.cover();
        }
        return new Ray(blocked, highest);
    }

    /** Tests the open cell interior against the center segment using integer comparisons. */
    private static boolean crosses(Position source, Position target, Position cell) {
        long dx = 2L * (target.x() - source.x()), dy = 2L * (target.y() - source.y());
        long x = 2L * (cell.x() - source.x()), y = 2L * (cell.y() - source.y());
        if (dx < 0) { dx = -dx; x = -x; }
        if (dy < 0) { dy = -dy; y = -y; }
        if (x + 1 <= 0 || x - 1 >= dx || y + 1 <= 0 || y - 1 >= dy) return false;
        if (dx == 0 || dy == 0) return true;
        return (x - 1) * dy < (y + 1) * dx && (y - 1) * dx < (x + 1) * dy;
    }

    static void prepareCell(Battle battle, ObservationCellInput input) {
        if (input == null || input.position().x() < 0 || input.position().y() < 0) throw invalid("S2_OBSERVATION_CELL");
        boolean existing = battle.observation().cells().stream().anyMatch(cell -> cell.position().equals(input.position()));
        if (!input.isDefault() && !existing && battle.observation().cells().size() == MAX_CELLS)
            throw invalid("S2_OBSERVATION_LIMIT");
    }

    static Battle setCell(Battle battle, ObservationCellInput input) {
        List<ObservationCellInput> cells = new ArrayList<>(battle.observation().cells());
        cells.removeIf(cell -> cell.position().equals(input.position()));
        if (!input.isDefault()) cells.add(input);
        cells.sort(Comparator.comparing(ObservationCellInput::position, POSITION_ORDER));
        ObservationState updated = new ObservationState(cells, battle.observation().pairs(), battle.observation().checks());
        return refresh(battle, withObservation(battle, updated));
    }

    /** Applies visibility at an atomic position, terrain, or membership boundary. */
    public static Battle refresh(Battle previous, Battle next) {
        ObservationState state = next.observation();
        if (state.cells().isEmpty() && state.pairs().isEmpty()) return next;
        Map<PairKey, ObservationPair> retained = pairs(state);
        Map<UUID, ObservationCheck> checks = checks(state);
        Map<UUID, Combatant> previousActors = new HashMap<>();
        if (previous != null) for (Combatant actor : previous.combatants()) previousActors.put(actor.characterId(), actor);
        Map<PairKey, ObservationPair> updated = new TreeMap<>(PAIR_ORDER);
        for (Combatant observer : next.combatants()) for (Combatant subject : next.combatants()) {
            UUID observerId = observer.characterId(), subjectId = subject.characterId();
            if (observerId.equals(subjectId)) continue;
            PairKey key = new PairKey(observerId, subjectId);
            ObservationPair old = retained.get(key);
            boolean clear = !ray(state.cells(), observer.position(), subject.position()).blocked();
            UUID hide = old != null && observer.side() != subject.side() ? old.hideOperationId() : null;
            if (clear && (hide == null || !retainHide(state, subject.position(), checks.get(hide)))) continue;
            Position last = null;
            if (old != null) last = old.lastKnownPosition();
            else if (previousActors.containsKey(observerId) && previousActors.containsKey(subjectId))
                last = previousActors.get(subjectId).position();
            updated.put(key, new ObservationPair(observerId, subjectId, last, hide));
        }
        List<ObservationPair> values = List.copyOf(updated.values());
        if (state.pairs().equals(values)) return next;
        return withObservation(next, new ObservationState(state.cells(), values, state.checks()));
    }

    /** Launching an attack identifies the source's current position independently of sight and outcome. */
    public static Battle revealAttack(Battle battle, UUID subject) {
        ObservationState state = battle.observation();
        if (state.pairs().isEmpty()) return battle;
        Combatant attacker = Schema2CombatRules.combatant(battle, subject);
        Map<PairKey, ObservationPair> updated = pairs(state);
        for (Combatant observer : battle.combatants()) {
            if (!observer.characterId().equals(subject)) reveal(battle, updated, observer, attacker);
        }
        return withObservation(battle, new ObservationState(state.cells(), List.copyOf(updated.values()), state.checks()));
    }

    private static void reveal(Battle battle, Map<PairKey, ObservationPair> updated, Combatant observer, Combatant subject) {
        PairKey key = new PairKey(observer.characterId(), subject.characterId());
        if (visible(battle, observer.position(), subject.position())) updated.remove(key);
        else updated.put(key, new ObservationPair(key.observer(), key.subject(), subject.position(), null));
    }

    static boolean checkAction(Kind kind) {
        return hiding(kind) || kind == Kind.SEARCH || kind == Kind.NIMBLE_DISENGAGE;
    }

    static boolean hiding(Kind kind) { return kind == Kind.HIDE || kind == Kind.NIMBLE_HIDE || kind == Kind.CUNNING_HIDE; }

    static boolean paysBonusAction(Kind kind) {
        return kind == Kind.NIMBLE_HIDE || kind == Kind.NIMBLE_DISENGAGE || kind == Kind.CUNNING_HIDE;
    }

    static boolean openingHide(Kind kind) { return kind == Kind.START || kind == Kind.REENTER_COMBAT; }

    static UUID openingHideKey(UUID operation, UUID actor) {
        return Schema2CombatRules.key(operation, "opening-stealth-" + actor);
    }

    private static boolean hideCheck(Kind kind) { return hiding(kind) || openingHide(kind); }

    private static boolean retainHide(ObservationState state, Position position, ObservationCheck check) {
        return hidingCell(state.cells(), position) || check != null && openingHide(check.kind()) && position.equals(check.position());
    }

    static UUID priorHide(Battle battle, UUID observer, UUID subject) {
        ObservationPair pair = pair(battle, observer, subject);
        return pair == null ? null : pair.hideOperationId();
    }

    static Battle applyOpeningHides(Battle battle, boolean firstEncounter, List<ObservationCheck> openingChecks) {
        if (openingChecks.isEmpty()) return battle;
        var state = battle.observation();
        var updated = pairs(state);
        for (ObservationCheck check : openingChecks) for (ObservationContest contest : check.contests()) {
            PairKey key = new PairKey(contest.counterpartId(), check.actorId());
            Position lastKnown = firstEncounter ? null : knownPosition(battle, key.observer(), key.subject());
            if (contest.hiddenAfter()) {
                updated.put(key, new ObservationPair(key.observer(), key.subject(), lastKnown, check.operationId()));
            } else if (contest.previousHideOperationId() != null) {
                Combatant observer = member(battle, key.observer());
                if (visible(battle, observer.position(), check.position())) updated.remove(key);
                else updated.put(key, new ObservationPair(key.observer(), key.subject(), lastKnown, null));
            }
        }
        List<ObservationCheck> checks = new ArrayList<>(state.checks());
        checks.addAll(openingChecks);
        return withObservation(battle, new ObservationState(state.cells(), List.copyOf(updated.values()), checks));
    }

    static long factCount(ObservationState state) {
        return (long) state.cells().size() + state.pairs().size() + state.checks().size()
                + state.checks().stream().mapToLong(check -> check.contests().size() + (check.roll() == null ? 0 : 1)).sum();
    }

    static long potentialGrowth(Battle before, Command command, int remainingPath) {
        if (openingHide(command.kind()) && command.opening() != null) {
            Map<UUID, Integer> sides = new HashMap<>();
            if (before == null) {
                for (Deployment actor : command.deployments()) sides.put(actor.characterId(), actor.side());
                for (MonsterDeployment actor : command.monsterDeployments()) sides.put(actor.monsterId(), actor.side());
            } else {
                for (Combatant actor : before.combatants()) sides.put(actor.characterId(), actor.side());
            }
            long growth = 0;
            for (OpeningGroup group : command.opening().stealthGroups()) for (UUID hider : group.members()) {
                long opponents = sides.values().stream().filter(side -> !side.equals(sides.get(hider))).count();
                growth += 2 + 2 * opponents;
            }
            return growth;
        }
        if (before == null) return 0;
        var state = before.observation();
        if (command.kind() == Kind.SET_OBSERVATION_CELL)
            return Math.max(0, factCount(setCell(before, command.observationCell()).observation()) - factCount(state));
        int participants = before.combatants().size();
        if (checkAction(command.kind())) return 2L * participants;
        boolean hasMovement = remainingPath > 0 || command.kind() == Kind.EXPLORATION_MOVE
                || command.kind() == Kind.CHOOSE_MELEE_DEFENSE || command.kind() == Kind.SHOVE_PUSH
                || before.readyPreparations().stream().anyMatch(ready -> ready.status() == ReadyStatus.ARMED
                    && ready.input().responseKind() == ReadyResponseKind.MOVE);
        boolean hasObstacles = state.cells().stream().anyMatch(cell -> cell.blocksSight() || cell.cover() == Cover.TOTAL);
        return hasMovement && hasObstacles ? (long) participants * (participants - 1) - state.pairs().size() : 0;
    }

    static Plan prepare(Map<UUID, Context> roster, Battle battle, Combatant actor, Kind kind) {
        UUID id = actor.characterId();
        RuleIdentity nimbleSource = null;
        if (kind == Kind.NIMBLE_HIDE || kind == Kind.NIMBLE_DISENGAGE) {
            nimbleSource = Schema2MonsterRules.nimbleEscapeSource(roster, battle, id);
            if (nimbleSource == null) throw invalid("S2_NIMBLE_ESCAPE_UNAVAILABLE");
        }
        if (paysBonusAction(kind)) {
            if (actor.bonusActionsRemaining() == 0) throw invalid("S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
        } else if (!actor.actionAvailable()) throw invalid("S2_COMBAT_ACTION_UNAVAILABLE");
        if (battle.observation().checks().size() >= Schema2CombatHistory.MAX_EVENTS) throw invalid("S2_OBSERVATION_LIMIT");
        if (kind == Kind.NIMBLE_DISENGAGE) return new Plan(null, nimbleSource, List.of());
        boolean hide = hiding(kind);
        if (hide && !hideAvailable(battle, id)) throw invalid("S2_HIDE_UNAVAILABLE");
        CheckBasis basis = basis(roster, battle, id, hide ? STEALTH : PERCEPTION);
        Map<UUID, ObservationCheck> checks = checks(battle.observation());
        Map<PairKey, ObservationPair> prior = pairs(battle.observation());
        List<ObservationContest> contests = new ArrayList<>();
        for (Combatant other : battle.combatants().stream().sorted(Comparator.comparing(Combatant::characterId, UUID_ORDER)).toList()) {
            UUID counterpart = other.characterId();
            if (other.side() == actor.side() || !Schema2MonsterRules.living(roster, battle, counterpart)) continue;
            ObservationPair old = prior.get(hide ? new PairKey(counterpart, id) : new PairKey(id, counterpart));
            UUID previousHide = old == null ? null : old.hideOperationId();
            if (!hide && previousHide == null) continue;
            int score = hide ? passivePerception(roster, battle, counterpart) : checks.get(previousHide).roll().total();
            contests.add(new ObservationContest(counterpart, score, previousHide, false));
        }
        return new Plan(basis, nimbleSource, List.copyOf(contests));
    }

    static int passivePerception(Map<UUID, Context> roster, Battle battle, UUID observer) {
        if (!roster.containsKey(observer)) return Schema2MonsterRules.passivePerception(roster, battle, observer);
        return CHECKS.passiveScore(basis(roster, battle, observer, PERCEPTION).characterRoll());
    }

    static CheckBasis stealthBasis(Map<UUID, Context> roster, Battle battle, UUID hider) {
        return basis(roster, battle, hider, STEALTH);
    }

    private static CheckBasis basis(Map<UUID, Context> roster, Battle battle, UUID id, String skill) {
        boolean stealth = STEALTH.equals(skill);
        String ability = stealth ? DEXTERITY : WISDOM;
        Context character = roster.get(id);
        if (character == null) {
            RuleIdentity rule = Schema2MonsterRules.instance(battle, id).monster();
            var statBlock = Schema2MonsterRules.definition(roster, rule).statBlock();
            int modifier = Schema2MonsterRules.modifier(statBlock, ability);
            int totalModifier = stealth ? Schema2MonsterRules.stealthModifier(roster, battle, id)
                    : Schema2MonsterRules.perceptionModifier(roster, battle, id);
            return new CheckBasis(rule, null, skill, null, modifier, totalModifier - modifier);
        }
        var catalog = character.material().projection().rules();
        var checks = catalog.stream().filter(FinalCheck.class::isInstance).map(FinalCheck.class::cast)
                .filter(FinalCheck::enabled).toList();
        var skills = catalog.stream().filter(FinalProficiency.class::isInstance).map(FinalProficiency.class::cast)
                .filter(FinalProficiency::enabled).filter(row -> row.identity().ruleKey().equals("proficiency." + skill)).toList();
        if (checks.size() != 1 || skills.size() != 1 || skills.getFirst().definition().category() != ProficiencyCategory.SKILL
                || !ability.equals(skills.getFirst().definition().abilityKey())) throw invalid("S2_OBSERVATION_RULE_UNSUPPORTED");
        RuleIdentity checkRule = checks.getFirst().identity(), skillRule = skills.getFirst().identity();
        var roll = CHECKS.prepareContestRoll(character.material(), character.character(),
                new RollInput(checkRule, "SKILL", ability, skillRule, false, false));
        return new CheckBasis(checkRule, skillRule, null, roll, 0, 0);
    }

    static Battle resolve(Battle battle, Combatant actor, Kind kind, UUID operation, Plan plan, Draw draws) {
        boolean nimble = plan.nimbleSource() != null;
        Combatant paid = paysBonusAction(kind) ? actor.spendBonusAction() : actor.spendAction();
        if (kind == Kind.NIMBLE_DISENGAGE) paid = paid.withDisengaged();
        battle = Schema2CombatRules.replaceActor(battle, paid);
        ObservationState state = battle.observation();
        List<ObservationCheck> checks = new ArrayList<>(state.checks());
        if (kind == Kind.NIMBLE_DISENGAGE) {
            checks.add(new ObservationCheck(operation, kind, actor.characterId(), actor.position(), plan.nimbleSource(),
                    null, null, true, null, List.of()));
            return withObservation(battle, new ObservationState(state.cells(), state.pairs(), checks));
        }
        UUID id = actor.characterId();
        boolean hide = hiding(kind);
        ObservationRoll roll = roll(plan.basis(), hide ? "OBSERVATION_STEALTH" : "OBSERVATION_PERCEPTION", id, draws);
        List<ObservationContest> contests = new ArrayList<>();
        Map<PairKey, ObservationPair> updated = pairs(state);
        for (ObservationContest contest : plan.contests()) {
            Combatant other = Schema2CombatRules.combatant(battle, contest.counterpartId());
            Combatant observer = hide ? other : actor, subject = hide ? actor : other;
            PairKey key = new PairKey(observer.characterId(), subject.characterId());
            int margin = Integer.compare(roll.total(), contest.opposingScore());
            boolean wasHidden = contest.previousHideOperationId() != null;
            boolean isHidden = hide ? margin > 0 || margin == 0 && wasHidden : margin <= 0;
            if (hide && margin > 0)
                updated.put(key, new ObservationPair(key.observer(), key.subject(), knownPosition(battle, key.observer(), id), operation));
            if (hide ? margin < 0 : margin > 0) reveal(battle, updated, observer, subject);
            contests.add(new ObservationContest(contest.counterpartId(), contest.opposingScore(), contest.previousHideOperationId(), isHidden));
        }
        CheckBasis basis = plan.basis();
        checks.add(new ObservationCheck(operation, kind, id, actor.position(), basis.rule(), basis.skill(), basis.monsterSkill(),
                nimble, roll, contests));
        return withObservation(battle, new ObservationState(state.cells(), List.copyOf(updated.values()), checks));
    }

    private static ObservationRoll roll(CheckBasis basis, String purpose, UUID id, Draw draws) {
        int first = draws.d20(purpose, id);
        Integer second = basis.secondDieRequired() ? draws.d20(purpose + "_SECOND", id) : null;
        return roll(basis, first, second);
    }

    static ObservationRoll roll(CheckBasis basis, int first, Integer second) {
        if (basis.characterRoll() == null)
            return new ObservationRoll(first, null, first, basis.abilityModifier(), basis.skillBonus(), false,
                    first + basis.abilityModifier() + basis.skillBonus());
        var result = CHECKS.resolveRoll(basis.characterRoll(), first, second);
        return new ObservationRoll(first, second, result.selectedD20(), result.abilityModifier(), result.proficiencyBonus(),
                result.effectiveDisadvantage(), result.total());
    }

    static void validate(Battle battle) {
        ObservationState state = battle.observation();
        require(state.cells().size() <= MAX_CELLS && state.pairs().size() <= battle.combatants().size() * (battle.combatants().size() - 1)
                && state.checks().size() <= Schema2CombatHistory.MAX_EVENTS);
        Position previousCell = null;
        for (ObservationCellInput cell : state.cells()) {
            Position at = cell.position();
            require(at.x() >= 0 && at.y() >= 0 && !cell.isDefault()
                    && (previousCell == null || POSITION_ORDER.compare(previousCell, at) < 0));
            previousCell = at;
        }
        Map<UUID, ObservationCheck> checks = new HashMap<>();
        for (ObservationCheck check : state.checks()) {
            validateCheck(check, checks);
            require(checks.putIfAbsent(check.operationId(), check) == null);
        }
        if (state.cells().isEmpty() && state.pairs().isEmpty()) return;
        Map<UUID, Combatant> actors = new HashMap<>();
        for (Combatant actor : battle.combatants()) actors.put(actor.characterId(), actor);
        PairKey previousPair = null;
        Set<PairKey> existing = new HashSet<>();
        for (ObservationPair pair : state.pairs()) {
            PairKey key = new PairKey(pair.observerId(), pair.subjectId());
            Combatant observer = actors.get(key.observer()), subject = actors.get(key.subject());
            require(observer != null && subject != null && !key.observer().equals(key.subject())
                    && (previousPair == null || PAIR_ORDER.compare(previousPair, key) < 0));
            boolean clear = visible(battle, observer.position(), subject.position());
            if (pair.hideOperationId() == null) require(!clear);
            else {
                var check = checks.get(pair.hideOperationId());
                require(observer.side() != subject.side() && (!clear || retainHide(state, subject.position(), check))
                        && check != null && hideCheck(check.kind()) && check.actorId().equals(key.subject())
                        && check.contests().stream().anyMatch(contest -> contest.counterpartId().equals(key.observer())
                            && check.roll().total() > contest.opposingScore()));
            }
            existing.add(key);
            previousPair = key;
        }
        boolean blocksSight = state.cells().stream().anyMatch(cell -> cell.blocksSight() || cell.cover() == Cover.TOTAL);
        if (blocksSight) for (Combatant observer : actors.values()) for (Combatant subject : actors.values()) {
            if (!observer.characterId().equals(subject.characterId()) && !visible(battle, observer.position(), subject.position()))
                require(existing.contains(new PairKey(observer.characterId(), subject.characterId())));
        }
    }

    private static void validateCheck(ObservationCheck check, Map<UUID, ObservationCheck> previous) {
        uuid(check.operationId());
        uuid(check.actorId());
        boolean opening = openingHide(check.kind());
        require((checkAction(check.kind()) || opening) && check.position() != null && check.rule() != null
                && check.contests().size() < Schema2CombatRules.MAX_COMBATANTS);
        if (opening) {
            uuid(check.openingOperationId());
            uuid(check.rollSource());
            require(check.operationId().equals(openingHideKey(check.openingOperationId(), check.actorId())));
        } else require(check.openingOperationId() == null && check.rollSource() == null);
        boolean nimble = check.kind() == Kind.NIMBLE_HIDE || check.kind() == Kind.NIMBLE_DISENGAGE;
        require(check.nimbleEscape() == nimble && (!nimble || GOBLIN_MONSTER.equals(check.rule())));
        if (check.kind() == Kind.NIMBLE_DISENGAGE) {
            require(check.roll() == null && check.skill() == null && check.monsterSkill() == null && check.contests().isEmpty());
            return;
        }
        boolean hide = hideCheck(check.kind());
        String skill = hide ? STEALTH : PERCEPTION;
        boolean monster = "adventure.monster".equals(check.rule().ruleType());
        require(check.kind() != Kind.CUNNING_HIDE || !monster);
        ObservationRoll roll = check.roll();
        require(roll != null && (monster ? check.skill() == null && skill.equals(check.monsterSkill()) && !roll.disadvantage()
                : "adventure.check".equals(check.rule().ruleType()) && check.monsterSkill() == null && check.skill() != null
                    && "character.proficiency".equals(check.skill().ruleType()) && ("proficiency." + skill).equals(check.skill().ruleKey())));
        require(roll.firstD20() >= 1 && roll.firstD20() <= 20
                && (roll.disadvantage() ? roll.secondD20() != null && roll.secondD20() >= 1 && roll.secondD20() <= 20 : roll.secondD20() == null)
                && roll.selectedD20() == (roll.disadvantage() ? Math.min(roll.firstD20(), roll.secondD20()) : roll.firstD20())
                && roll.abilityModifier() >= -5 && roll.abilityModifier() <= 10 && roll.skillBonus() >= (monster ? -30 : 0)
                && roll.skillBonus() <= 35 && roll.total() == roll.selectedD20() + roll.abilityModifier() + roll.skillBonus());
        UUID lastOpponent = null;
        for (ObservationContest contest : check.contests()) {
            UUID opponent = contest.counterpartId(), prior = contest.previousHideOperationId();
            uuid(opponent);
            require(!opponent.equals(check.actorId()) && (lastOpponent == null || UUID_ORDER.compare(lastOpponent, opponent) < 0));
            if (prior != null || !hide) {
                var old = previous.get(prior);
                UUID hider = hide ? check.actorId() : opponent, observer = hide ? opponent : check.actorId();
                require(old != null && hideCheck(old.kind()) && old.actorId().equals(hider)
                        && old.contests().stream().anyMatch(past -> past.counterpartId().equals(observer)
                            && old.roll().total() > past.opposingScore())
                        && (hide || contest.opposingScore() == old.roll().total()));
            }
            int margin = Integer.compare(roll.total(), contest.opposingScore());
            require(contest.hiddenAfter() == (hide ? margin > 0 || !opening && margin == 0 && prior != null : margin <= 0));
            lastOpponent = opponent;
        }
    }

    private static void require(boolean valid) {
        if (!valid) throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
    }

    static Battle withObservation(Battle b, ObservationState observation) {
        return new Battle(b.combatId(), b.campaignId(), b.combatRule(), b.status(), b.round(), b.turnNumber(), b.activeOrdinal(),
                b.version(), b.combatants(), b.initiative(), b.flows(), b.ongoingEffects(), b.readyPreparations(), b.triggerFacts(),
                b.shields(), b.concentrationHolders(), b.environmentZones(), b.suppressionCauses(), b.monsters(),
                b.saveDamageBatches(), b.monsterRests(), b.meleeControl(), observation);
    }
}
