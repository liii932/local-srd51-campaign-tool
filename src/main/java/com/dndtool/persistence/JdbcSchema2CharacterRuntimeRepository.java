package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Append-only, typed character execution facts. The caller owns the guarded transaction. */
public final class JdbcSchema2CharacterRuntimeRepository {
    public static final int MAX_EVENTS = 128, MAX_RESOURCES = 32;
    public static final List<String> ABILITIES = List.of("ability.strength", "ability.dexterity", "ability.constitution", "ability.intelligence", "ability.wisdom", "ability.charisma");
    private static final Set<String> KINDS = Set.of("INITIALIZED", "CHARACTER_ADVANCED", "STATISTICS_ADVANCED", "RESOURCE_SPENT", "RESOURCES_RECOVERED", "REST_STARTED", "TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT", "REST_ENDED");
    private static final Comparator<RuleIdentity> RULE_ORDER = Comparator.comparing(RuleIdentity::moduleKey).thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    private static final Comparator<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity> SLOT_ORDER = Comparator.comparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::rule, RULE_ORDER).thenComparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::collectionKey).thenComparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::moduleKey).thenComparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::entryKey);
    private static final Comparator<ResourceState> RESOURCE_ORDER = Comparator.comparing((ResourceState r) -> r.identity().moduleKey()).thenComparing(r -> r.identity().ruleType()).thenComparing(r -> r.identity().ruleKey());

    public static final int MAX_CLASSES = 20, MAX_HIT_DICE_POOLS = 4, MAX_FEATURES = 64, MAX_FEATS = 20, MAX_GRANTS = 64;
    public static final int MAX_HISTORY_CHILD_ROWS = 32768;
    public record ResourceState(RuleIdentity identity, long current, long maximum, boolean unlimited) { }
    public record ClassLevel(RuleIdentity characterClass, int level) { }
    public record HitDicePool(int sides, int current, int maximum) { }
    public record SubclassState(RuleIdentity characterClass, RuleIdentity subclass, int acquiredAtClassLevel) { }
    public record FeatureState(RuleIdentity feature, RuleIdentity owner, int acquiredAtClassLevel) { }
    public record FeatState(RuleIdentity feat, RuleIdentity characterClass, int acquiredAtClassLevel) { }
    public record ProficiencyState(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity sourceSlot, RuleIdentity target, int acquiredAtTotalLevel) { }
    public record RestAttempt(RuleIdentity rule, String kind, long startMinute, int hitPointsAtStart,
            long sleepMinutes, long lightMinutes, long strenuousMinutes, String phase, String lastActivity, long consecutiveStrenuousMinutes, boolean interrupted) { }
    public record RestState(long minute, Long lastLongRestEndMinute, RestAttempt active) {
        public static final RestState INITIAL = new RestState(0, null, null);
    }
    public record RestInput(String restKind, String activity, long minutes, int hitDieSides, Map<Integer, Integer> recoveredHitDice) {
        public RestInput {
            boolean valid = (restKind == null || restKind.equals("SHORT_REST") || restKind.equals("LONG_REST"))
                    && (activity == null || Set.of("SLEEP", "LIGHT", "STRENUOUS", "NONE").contains(activity))
                    && minutes >= 0 && minutes <= 1440 && (hitDieSides == 0 || Set.of(6, 8, 10, 12).contains(hitDieSides)) && recoveredHitDice != null && recoveredHitDice.size() <= 4;
            var recovered = new TreeMap<Integer, Integer>(); int total = 0;
            if (valid) for (var entry : recoveredHitDice.entrySet()) {
                if (entry.getKey() == null || !Set.of(6, 8, 10, 12).contains(entry.getKey()) || entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 20) { valid = false; break; }
                recovered.put(entry.getKey(), entry.getValue()); total += entry.getValue();
            }
            if (!valid || total > 20) throw new IllegalArgumentException("S2_CHARACTER_REST_INPUT");
            recoveredHitDice = Collections.unmodifiableMap(recovered);
        }
    }
    public record State(Identity identity, UUID campaignId, UUID characterId, UUID preparationOperationId,
            RuleIdentity characterClass, Map<String, Integer> abilities, int level, int proficiencyBonus,
            long hitPoints, long maximumHitPoints, List<ClassLevel> classes, List<HitDicePool> hitDicePools,
            List<SubclassState> subclasses, List<FeatureState> features, List<FeatState> feats,
            List<ProficiencyState> proficiencies, long version, List<ResourceState> resources, RestState rest) {
        public State {
            abilities = Map.copyOf(abilities); classes = List.copyOf(classes); hitDicePools = List.copyOf(hitDicePools);
            subclasses = List.copyOf(subclasses); features = List.copyOf(features); feats = List.copyOf(feats);
            proficiencies = List.copyOf(proficiencies); resources = List.copyOf(resources);
        }
        public int hitDice() { return hitDicePools.stream().mapToInt(HitDicePool::current).sum(); }
        public int maximumHitDice() { return hitDicePools.stream().mapToInt(HitDicePool::maximum).sum(); }
    }
    public record GrowthChoices(RuleIdentity subclass, Map<String, Integer> abilityIncreases, RuleIdentity feat,
            Map<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity, List<RuleIdentity>> proficiencyChoices) {
        public static final GrowthChoices EMPTY = new GrowthChoices(null, Map.of(), null, Map.of());
        public GrowthChoices {
            if (abilityIncreases == null || proficiencyChoices == null || subclass != null && !validRule(subclass, "character.subclass")
                    || feat != null && !validRule(feat, "character.feat") || abilityIncreases.size() > 2 || proficiencyChoices.size() > MAX_GRANTS)
                throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
            var increases = new TreeMap<String, Integer>(); int total = 0;
            for (var entry : abilityIncreases.entrySet()) {
                if (entry.getKey() == null || !ABILITIES.contains(entry.getKey()) || entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 2)
                    throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                increases.put(entry.getKey(), entry.getValue()); total += entry.getValue();
            }
            if (total != 0 && total != 2 || feat != null && total != 0) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
            abilityIncreases = Collections.unmodifiableMap(increases);
            var choices = new TreeMap<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity, List<RuleIdentity>>(SLOT_ORDER);
            for (var entry : proficiencyChoices.entrySet()) {
                if (!validSlot(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().size() > 16)
                    throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                List<RuleIdentity> targets = new ArrayList<>(entry.getValue());
                for (RuleIdentity target : targets) if (!validProficiency(target)) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                targets.sort(RULE_ORDER); if (new HashSet<>(targets).size() != targets.size()) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                choices.put(entry.getKey(), List.copyOf(targets));
            }
            if (choices.values().stream().mapToInt(List::size).sum() > MAX_GRANTS) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
            proficiencyChoices = Collections.unmodifiableMap(choices);
        }
    }
    public record Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice,
            RuleIdentity targetClass, GrowthChoices growthChoices, RestInput restInput) {
        public Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice, RuleIdentity targetClass, GrowthChoices growthChoices) {
            this(kind, resource, amount, recoveryTrigger, hpChoice, targetClass, growthChoices, null);
        }
        public Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice) {
            this(kind, resource, amount, recoveryTrigger, hpChoice, null, GrowthChoices.EMPTY, null);
        }
        public Command {
            boolean valid = kind != null && amount >= 0 && amount <= 1000000 && growthChoices != null;
            if (valid) valid = switch (kind) {
                case "INITIALIZED" -> resource == null && amount == 0 && recoveryTrigger == null && hpChoice == null && targetClass == null && growthChoices.feat() == null && growthChoices.abilityIncreases().isEmpty();
                case "CHARACTER_ADVANCED" -> resource == null && amount == 0 && recoveryTrigger == null && ("FIXED_AVERAGE".equals(hpChoice) || "SERVER_ROLL".equals(hpChoice)) && validRule(targetClass, "character.class");
                case "STATISTICS_ADVANCED" -> resource == null && amount == 0 && recoveryTrigger == null && ("FIXED_AVERAGE".equals(hpChoice) || "SERVER_ROLL".equals(hpChoice)) && targetClass == null && growthChoices.equals(GrowthChoices.EMPTY);
                case "RESOURCE_SPENT" -> validRule(resource, "character.resource") && amount >= 1 && recoveryTrigger == null && hpChoice == null && targetClass == null && growthChoices.equals(GrowthChoices.EMPTY);
                case "RESOURCES_RECOVERED" -> resource == null && amount == 0 && ("SHORT_REST".equals(recoveryTrigger) || "LONG_REST".equals(recoveryTrigger)) && hpChoice == null && targetClass == null && growthChoices.equals(GrowthChoices.EMPTY);
                default -> false;
            };
            if (restKind(kind)) {
                valid = resource == null && amount == 0 && recoveryTrigger == null && hpChoice == null && targetClass == null && GrowthChoices.EMPTY.equals(growthChoices) && restInput != null;
                if (valid) valid = switch (kind) {
                    case "REST_STARTED" -> restInput.restKind() != null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() == 0 && restInput.recoveredHitDice().isEmpty();
                    case "TIME_ADVANCED" -> restInput.restKind() == null && restInput.activity() != null && restInput.minutes() >= 1 && restInput.hitDieSides() == 0 && restInput.recoveredHitDice().isEmpty();
                    case "REST_COMPLETED" -> restInput.restKind() == null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() == 0;
                    case "HIT_DIE_SPENT" -> restInput.restKind() == null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() != 0 && restInput.recoveredHitDice().isEmpty();
                    case "REST_ENDED" -> restInput.restKind() == null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() == 0 && restInput.recoveredHitDice().isEmpty();
                    default -> false;
                };
            } else valid &= restInput == null;
            if (!valid) throw new IllegalArgumentException("S2_CHARACTER_COMMAND");
        }
    }
    public record Event(UUID operationId, String requestDigest, Command command, Integer hitDieRoll, State before, State after) {
        public String kind() { return command.kind(); }
    }
    public record History(State current, List<Event> events) {
        public History { events = List.copyOf(events); }
    }

    /** No source connection or rules execution is used to reconstruct committed facts. */
    public History read(Connection connection, UUID characterId) throws SQLException {
        uuid(characterId);
        List<Root> roots = query(connection, "s2_character_runtime", "character_id", bytes(characterId), "character_id", 1, row -> {
            require(id(row, "character_id").equals(characterId));
            return new Root(new Identity(id(row, "run_id"), id(row, "snapshot_id")), id(row, "campaign_id"), characterId, id(row, "preparation_operation_id"));
        });
        if (roots.isEmpty()) {
            for (String table : List.of("s2_character_event", "s2_character_event_state", "s2_character_event_resource", "s2_character_event_class", "s2_character_event_hit_die", "s2_character_event_subclass", "s2_character_event_feature", "s2_character_event_feat", "s2_character_event_proficiency", "s2_character_command_ability", "s2_character_command_proficiency", "s2_character_command_hit_die"))
                require(query(connection, table, "character_id", bytes(characterId), "event_sequence", 1, row -> true).isEmpty());
            return null;
        }
        Root root = roots.getFirst(); verifyPlan(connection, root);
        List<Header> headers = new ArrayList<>();
        query(connection, "s2_character_event", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            require(number(row, "event_sequence") == headers.size() + 1);
            headers.add(new Header(id(row, "operation_id"), ascii(row, "request_digest", 64), ascii(row, "event_kind", 32),
                    nullableRule(row, "resource"), number(row, "resource_amount"), nullableAscii(row, "recovery_trigger", 16), nullableAscii(row, "hp_choice", 64),
                    nullableRule(row, "target_class"), nullableRule(row, "chosen_subclass"), nullableRule(row, "chosen_feat"), integer(row, "ability_choice_count"), integer(row, "proficiency_choice_count"), nullableAscii(row, "rest_kind", 16), nullableAscii(row, "rest_activity", 16), number(row, "rest_minutes"), integer(row, "rest_hit_die_sides"), integer(row, "recovered_hit_die_count"),
                    row.getObject("hit_die_roll") == null ? null : integer(row, "hit_die_roll")));
            return true;
        });
        require(!headers.isEmpty());
        Map<Long, Map<String, Integer>> abilityChoices = new HashMap<>();
        query(connection, "s2_character_command_ability", "character_id", bytes(characterId), "event_sequence, choice_index", MAX_EVENTS * 2, row -> {
            long sequence = eventSequence(row, headers.size()); var choices = abilityChoices.computeIfAbsent(sequence, ignored -> new LinkedHashMap<>());
            require(integer(row, "choice_index") == choices.size() && choices.size() < 2 && choices.putIfAbsent(ascii(row, "ability_key", 32), integer(row, "increase_value")) == null); return true;
        });
        Map<Long, List<ChoiceTarget>> proficiencyChoices = new HashMap<>();
        query(connection, "s2_character_command_proficiency", "character_id", bytes(characterId), "event_sequence, choice_index", MAX_EVENTS * MAX_GRANTS, row -> {
            long sequence = eventSequence(row, headers.size()); var choices = proficiencyChoices.computeIfAbsent(sequence, ignored -> new ArrayList<>());
            require(integer(row, "choice_index") == choices.size() && choices.size() < MAX_GRANTS);
            choices.add(new ChoiceTarget(slot(row), rule(row, "target"))); return true;
        });
        Map<Long, Map<Integer, Integer>> recoveredDice = new HashMap<>();
        query(connection, "s2_character_command_hit_die", "character_id", bytes(characterId), "event_sequence, pool_index", MAX_EVENTS * 4, row -> {
            long sequence = eventSequence(row, headers.size()); var recovered = recoveredDice.computeIfAbsent(sequence, ignored -> new LinkedHashMap<>());
            require(integer(row, "pool_index") == recovered.size() && recovered.size() < 4 && recovered.putIfAbsent(integer(row, "sides"), integer(row, "recovered_value")) == null); return true;
        });
        var classes = readChildren(connection, characterId, "class", "class_index", MAX_CLASSES, headers.size(), row -> new ClassLevel(rule(row, "class"), integer(row, "class_level")));
        var dice = readChildren(connection, characterId, "hit_die", "pool_index", MAX_HIT_DICE_POOLS, headers.size(), row -> new HitDicePool(integer(row, "sides"), integer(row, "current_value"), integer(row, "maximum_value")));
        var subclasses = readChildren(connection, characterId, "subclass", "subclass_index", MAX_CLASSES, headers.size(), row -> new SubclassState(rule(row, "class"), rule(row, "subclass"), integer(row, "acquired_class_level")));
        var features = readChildren(connection, characterId, "feature", "feature_index", MAX_FEATURES, headers.size(), row -> new FeatureState(rule(row, "feature"), rule(row, "owner"), integer(row, "acquired_class_level")));
        var feats = readChildren(connection, characterId, "feat", "feat_index", MAX_FEATS, headers.size(), row -> new FeatState(rule(row, "feat"), rule(row, "class"), integer(row, "acquired_class_level")));
        var proficiencies = readChildren(connection, characterId, "proficiency", "proficiency_index", MAX_GRANTS, headers.size(), row -> new ProficiencyState(slot(row), rule(row, "target"), integer(row, "acquired_total_level")));

        Map<StateKey, List<ResourceState>> resourceRows = new HashMap<>();
        query(connection, "s2_character_event_resource", "character_id", bytes(characterId), "event_sequence, phase, resource_index", MAX_EVENTS * MAX_RESOURCES * 2, row -> {
            StateKey key = stateKey(row, headers.size());
            List<ResourceState> resources = resourceRows.computeIfAbsent(key, ignored -> new ArrayList<>());
            require(integer(row, "resource_index") == resources.size() && resources.size() < MAX_RESOURCES);
            int unlimited = integer(row, "unlimited"); require(unlimited <= 1);
            resources.add(new ResourceState(rule(row, "resource"), number(row, "current_value"), number(row, "maximum_value"), unlimited == 1));
            return true;
        });
        Map<StateKey, State> states = new HashMap<>();
        query(connection, "s2_character_event_state", "character_id", bytes(characterId), "event_sequence, phase", MAX_EVENTS * 2, row -> {
            StateKey key = stateKey(row, headers.size());
            List<ResourceState> resources = resourceRows.remove(key); if (resources == null) resources = List.of();
            require(integer(row, "resource_count") == resources.size());
            Map<String, Integer> abilities = new LinkedHashMap<>();
            for (String ability : ABILITIES) abilities.put(ability, integer(row, ability.substring("ability.".length())));
            State state = new State(root.identity, root.campaign, root.character, root.preparation, rule(row, "class"), abilities,
                    integer(row, "total_level"), integer(row, "proficiency_bonus"), number(row, "hit_points"), number(row, "maximum_hit_points"),
                    take(classes, key, integer(row, "class_count")), take(dice, key, integer(row, "hit_die_count")), take(subclasses, key, integer(row, "subclass_count")),
                    take(features, key, integer(row, "feature_count")), take(feats, key, integer(row, "feat_count")), take(proficiencies, key, integer(row, "proficiency_count")), number(row, "row_version"), resources, readRest(row));
            require(states.putIfAbsent(key, state) == null); return true;
        });
        require(resourceRows.isEmpty() && classes.isEmpty() && dice.isEmpty() && subclasses.isEmpty() && features.isEmpty() && feats.isEmpty() && proficiencies.isEmpty() && states.size() == headers.size() * 2 - 1);
        List<Event> events = new ArrayList<>();
        for (int index = 0; index < headers.size(); index++) {
            Header header = headers.get(index);
            State before = index == 0 ? null : states.remove(new StateKey(index + 1, "BEFORE"));
            State after = states.remove(new StateKey(index + 1, "AFTER"));
            require(after != null && (index == 0 || before != null));
            Map<String, Integer> increases = abilityChoices.remove(index + 1L); if (increases == null) increases = Map.of();
            List<ChoiceTarget> chosen = proficiencyChoices.remove(index + 1L); if (chosen == null) chosen = List.of();
            require(increases.size() == header.abilities && chosen.size() == header.proficiencies);
            var selected = new TreeMap<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity, List<RuleIdentity>>(SLOT_ORDER);
            for (ChoiceTarget choice : chosen) selected.computeIfAbsent(choice.slot, ignored -> new ArrayList<>()).add(choice.target);
            try {
                GrowthChoices growth = new GrowthChoices(header.subclass, increases, header.feat, selected);
                List<ChoiceTarget> canonical = new ArrayList<>();
                growth.proficiencyChoices().forEach((slot, targets) -> targets.forEach(target -> canonical.add(new ChoiceTarget(slot, target))));
                require(canonical.equals(chosen) && new ArrayList<>(growth.abilityIncreases().keySet()).equals(new ArrayList<>(increases.keySet())));
                Map<Integer, Integer> recovered = recoveredDice.remove(index + 1L); if (recovered == null) recovered = Map.of();
                require(recovered.size() == header.recoveredDice);
                RestInput input = null;
                if (restKind(header.kind)) {
                    input = new RestInput(header.restKind, header.activity, header.minutes, header.dieSides, recovered);
                    require(new ArrayList<>(input.recoveredHitDice().keySet()).equals(new ArrayList<>(recovered.keySet())));
                } else require(header.restKind == null && header.activity == null && header.minutes == 0 && header.dieSides == 0 && recovered.isEmpty());
                Command command = new Command(header.kind, header.resource, header.amount, header.trigger, header.hp, header.targetClass, growth, input);
                events.add(new Event(header.operation, header.digest, command, header.roll, before, after));
            } catch (IllegalArgumentException malformed) { throw new SQLException("S2_CHARACTER_RUNTIME_CORRUPT", malformed); }
        }
        require(states.isEmpty() && abilityChoices.isEmpty() && proficiencyChoices.isEmpty() && recoveredDice.isEmpty());
        History history = new History(events.getLast().after(), events); validate(history); return history;
    }

    private static StateKey stateKey(ResultSet row, int maximum) throws SQLException {
        long sequence = number(row, "event_sequence"); String phase = ascii(row, "phase", 6);
        require(sequence >= 1 && sequence <= maximum && (phase.equals("AFTER") || phase.equals("BEFORE") && sequence > 1));
        return new StateKey(sequence, phase);
    }

    private static long eventSequence(ResultSet row, int maximum) throws SQLException { long sequence = number(row, "event_sequence"); require(sequence >= 1 && sequence <= maximum); return sequence; }
    private static <T> Map<StateKey, List<T>> readChildren(Connection connection, UUID character, String suffix, String index, int maximum, int events, Reader<T> decoder) throws SQLException {
        Map<StateKey, List<T>> result = new HashMap<>();
        query(connection, "s2_character_event_" + suffix, "character_id", bytes(character), "event_sequence, phase, " + index, MAX_EVENTS * maximum * 2, row -> {
            StateKey key = stateKey(row, events); var values = result.computeIfAbsent(key, ignored -> new ArrayList<>());
            require(integer(row, index) == values.size() && values.size() < maximum); values.add(decoder.read(row)); return true;
        }); return result;
    }
    private static <T> List<T> take(Map<StateKey, List<T>> rows, StateKey key, int count) throws SQLException {
        List<T> result = rows.remove(key); if (result == null) result = List.of(); require(result.size() == count); return result;
    }

    public UUID findByRun(Connection connection, UUID runId) throws SQLException {
        uuid(runId); List<UUID> characters = query(connection, "s2_character_runtime", "run_id", bytes(runId), "character_id", 1, row -> id(row, "character_id"));
        return characters.isEmpty() ? null : characters.getFirst();
    }
    public History readRunCharacter(Connection connection, UUID runId) throws SQLException { UUID character = findByRun(connection, runId); return character == null ? null : read(connection, character); }

    public Event findOperation(Connection connection, UUID operationId) throws SQLException {
        uuid(operationId);
        List<UUID> characters = query(connection, "s2_character_event", "operation_id", bytes(operationId), "event_sequence", 1, row -> id(row, "character_id"));
        if (characters.isEmpty()) return null;
        History history = read(connection, characters.getFirst());
        List<Event> matches = history.events().stream().filter(event -> event.operationId().equals(operationId)).toList();
        require(matches.size() == 1); return matches.getFirst();
    }

    /** Appends exactly one real transition, including initialization's nonempty root event. */
    public void append(Connection connection, Event event) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection); validateEvent(event);
        History previous = read(connection, event.after().characterId());
        require((previous == null) == (event.before() == null));
        require(previous == null || previous.current().equals(event.before()));
        require(findOperation(connection, event.operationId()) == null);
        List<Event> events = previous == null ? new ArrayList<>() : new ArrayList<>(previous.events()); events.add(event);
        History expected = new History(event.after(), events); validate(expected);
        if (previous == null) insertRoot(connection, root(event.after()));
        insertEvent(connection, event);
        require(expected.equals(read(connection, event.after().characterId())));
    }

    /** Restores complete validated state and immutable history directly, without re-running commands. */
    public void restore(Connection connection, History history) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection); validate(history);
        require(read(connection, history.current().characterId()) == null);
        for (Event event : history.events()) require(findOperation(connection, event.operationId()) == null);
        insertRoot(connection, root(history.current()));
        for (Event event : history.events()) insertEvent(connection, event);
        require(history.equals(read(connection, history.current().characterId())));
    }

    public static void checkCandidateCapacity(History previous, State candidate) throws SQLException { checkCandidateCapacity(previous, candidate, null); }
    public static void checkCandidateCapacity(History previous, State candidate, Command command) throws SQLException {
        validateState(candidate);
        long rows = stateRows(candidate) + (previous == null ? 0 : stateRows(previous.current())) + (command == null ? MAX_GRANTS + 6 : commandRows(command));
        if (previous != null) {
            require(previous.events().size() < MAX_EVENTS);
            for (Event event : previous.events()) rows += stateRows(event.after()) + (event.before() == null ? 0 : stateRows(event.before())) + commandRows(event.command());
        }
        require(rows <= MAX_HISTORY_CHILD_ROWS);
    }
    private static long stateRows(State state) { return 1L + state.classes().size() + state.hitDicePools().size() + state.subclasses().size() + state.features().size() + state.feats().size() + state.proficiencies().size() + state.resources().size(); }
    private static long commandRows(Command command) { return command.growthChoices().abilityIncreases().size() + command.growthChoices().proficiencyChoices().values().stream().mapToInt(List::size).sum() + (command.restInput() == null ? 0 : command.restInput().recoveredHitDice().size()); }
    public static void validate(History history) throws SQLException {
        require(history != null && !history.events().isEmpty() && history.events().size() <= MAX_EVENTS);
        Set<UUID> operations = new HashSet<>(); State previous = null; long rows = 0;
        for (Event event : history.events()) {
            validateEvent(event); require(operations.add(event.operationId()) && Objects.equals(previous, event.before())); previous = event.after();
            rows += stateRows(event.after()) + (event.before() == null ? 0 : stateRows(event.before())) + commandRows(event.command());
        }
        require(previous.equals(history.current()) && rows <= MAX_HISTORY_CHILD_ROWS);
    }
    private static boolean restKind(String kind) { return kind != null && Set.of("REST_STARTED", "TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT", "REST_ENDED").contains(kind); }
    private static boolean advancement(String kind) { return kind.equals("CHARACTER_ADVANCED") || kind.equals("STATISTICS_ADVANCED"); }
    private static void validateEvent(Event event) throws SQLException {
        require(event != null && event.command() != null); uuid(event.operationId());
        require(event.requestDigest() != null && event.requestDigest().matches("[0-9a-f]{64}") && KINDS.contains(event.kind()));
        validateState(event.after()); State after = event.after(), before = event.before(); Command command = event.command();
        require(!Set.of(after.identity().runId(), after.identity().snapshotId(), after.campaignId(), after.characterId(), after.preparationOperationId()).contains(event.operationId()));
        if (before == null) require(event.kind().equals("INITIALIZED") && after.version() == 0 && after.level() == 1 && event.hitDieRoll() == null);
        else {
            validateState(before); require(!event.kind().equals("INITIALIZED") && root(before).equals(root(after)) && before.characterClass().equals(after.characterClass()) && after.version() == before.version() + 1);
            if (advancement(event.kind())) {
                require(before.rest().equals(after.rest()));
                require(after.level() == before.level() + 1 && after.features().containsAll(before.features()) && after.feats().containsAll(before.feats()) && after.subclasses().containsAll(before.subclasses()) && after.proficiencies().containsAll(before.proficiencies()));
                RuleIdentity target = command.targetClass() == null ? before.characterClass() : command.targetClass();
                Map<RuleIdentity, Integer> old = new HashMap<>(); before.classes().forEach(value -> old.put(value.characterClass(), value.level()));
                for (ClassLevel value : after.classes()) require(value.level() == old.getOrDefault(value.characterClass(), 0) + (value.characterClass().equals(target) ? 1 : 0));
                require(after.classes().stream().map(ClassLevel::characterClass).toList().containsAll(old.keySet()));
                require(event.hitDieRoll() == null || event.hitDieRoll() >= 1 && event.hitDieRoll() <= 20);
                require("SERVER_ROLL".equals(command.hpChoice()) == (event.hitDieRoll() != null));
                if (event.kind().equals("STATISTICS_ADVANCED")) require(before.abilities().equals(after.abilities()) && before.classes().size() == after.classes().size());
            } else if (restKind(event.kind())) {
                require(before.abilities().equals(after.abilities()) && before.level() == after.level() && before.classes().equals(after.classes()) && before.subclasses().equals(after.subclasses()) && before.features().equals(after.features()) && before.feats().equals(after.feats()) && before.proficiencies().equals(after.proficiencies()));
                require(event.kind().equals("HIT_DIE_SPENT") ? event.hitDieRoll() != null && event.hitDieRoll() >= 1 && event.hitDieRoll() <= command.restInput().hitDieSides() : event.hitDieRoll() == null);
            } else require(before.rest().equals(after.rest()) && before.abilities().equals(after.abilities()) && before.level() == after.level() && before.proficiencyBonus() == after.proficiencyBonus()
                    && before.hitPoints() == after.hitPoints() && before.maximumHitPoints() == after.maximumHitPoints() && before.classes().equals(after.classes()) && before.hitDicePools().equals(after.hitDicePools())
                    && before.subclasses().equals(after.subclasses()) && before.features().equals(after.features()) && before.feats().equals(after.feats()) && before.proficiencies().equals(after.proficiencies()) && event.hitDieRoll() == null);
        }
    }
    private static void validateState(State state) throws SQLException {
        require(state != null && state.identity() != null); uuid(state.campaignId()); uuid(state.characterId()); uuid(state.preparationOperationId());
        require(new HashSet<>(List.of(state.identity().runId(), state.identity().snapshotId(), state.campaignId(), state.characterId(), state.preparationOperationId())).size() == 5);
        require(validRule(state.characterClass(), "character.class"));
        RestState rest = state.rest(); require(rest != null && rest.minute() >= 0 && rest.minute() <= 1000000000L && (rest.lastLongRestEndMinute() == null || rest.lastLongRestEndMinute() >= 0 && rest.lastLongRestEndMinute() <= rest.minute()));
        if (rest.active() != null) {
            RestAttempt attempt = rest.active(); require(validRule(attempt.rule(), "adventure.rest") && Set.of("SHORT_REST", "LONG_REST").contains(attempt.kind()) && attempt.startMinute() >= 0 && attempt.startMinute() <= rest.minute()
                    && attempt.hitPointsAtStart() >= 0 && attempt.hitPointsAtStart() <= 65535 && attempt.sleepMinutes() >= 0 && attempt.lightMinutes() >= 0 && attempt.strenuousMinutes() >= 0
                    && attempt.sleepMinutes() <= 1000000000L && attempt.lightMinutes() <= 1000000000L && attempt.strenuousMinutes() <= 1000000000L
                    && attempt.sleepMinutes() + attempt.lightMinutes() + attempt.strenuousMinutes() <= rest.minute() - attempt.startMinute()
                    && (attempt.lastActivity() == null || Set.of("SLEEP", "LIGHT", "STRENUOUS").contains(attempt.lastActivity())) && attempt.consecutiveStrenuousMinutes() >= 0 && attempt.consecutiveStrenuousMinutes() <= attempt.strenuousMinutes()
                    && ("IN_PROGRESS".equals(attempt.phase()) || "SPENDING_HIT_DICE".equals(attempt.phase()) && "SHORT_REST".equals(attempt.kind())));
        }
        require(state.abilities().keySet().equals(Set.copyOf(ABILITIES)) && state.abilities().values().stream().allMatch(value -> value != null && value >= 1 && value <= 30));
        require(state.level() >= 1 && state.level() <= 20 && state.proficiencyBonus() == 2 + (state.level() - 1) / 4 && state.maximumHitPoints() >= 1 && state.maximumHitPoints() <= 65535 && state.hitPoints() >= 0 && state.hitPoints() <= state.maximumHitPoints()
                && state.version() >= 0 && state.version() < MAX_EVENTS && state.resources().size() <= MAX_RESOURCES && !state.classes().isEmpty() && state.classes().size() <= MAX_CLASSES
                && !state.hitDicePools().isEmpty() && state.hitDicePools().size() <= MAX_HIT_DICE_POOLS && state.subclasses().size() <= MAX_CLASSES && state.features().size() <= MAX_FEATURES && state.feats().size() <= MAX_FEATS && state.proficiencies().size() <= MAX_GRANTS);
        Map<RuleIdentity, Integer> levels = new HashMap<>(); RuleIdentity previousClass = null; int total = 0;
        for (ClassLevel value : state.classes()) { require(value != null && validRule(value.characterClass(), "character.class") && value.level() >= 1 && value.level() <= 20 && (previousClass == null || RULE_ORDER.compare(previousClass, value.characterClass()) < 0)); levels.put(value.characterClass(), value.level()); previousClass = value.characterClass(); total += value.level(); }
        require(total == state.level() && levels.containsKey(state.characterClass()));
        int previousSides = 0, maximumDice = 0;
        for (HitDicePool pool : state.hitDicePools()) { require(pool != null && Set.of(6, 8, 10, 12).contains(pool.sides()) && pool.sides() > previousSides && pool.maximum() >= 1 && pool.maximum() <= 20 && pool.current() >= 0 && pool.current() <= pool.maximum()); previousSides = pool.sides(); maximumDice += pool.maximum(); }
        require(maximumDice == total);
        Map<RuleIdentity, RuleIdentity> subclassOwners = new HashMap<>(); previousClass = null;
        for (SubclassState value : state.subclasses()) { require(value != null && validRule(value.characterClass(), "character.class") && validRule(value.subclass(), "character.subclass") && levels.containsKey(value.characterClass()) && value.acquiredAtClassLevel() >= 1 && value.acquiredAtClassLevel() <= levels.get(value.characterClass()) && (previousClass == null || RULE_ORDER.compare(previousClass, value.characterClass()) < 0) && subclassOwners.putIfAbsent(value.subclass(), value.characterClass()) == null); previousClass = value.characterClass(); }
        RuleIdentity previous = null;
        for (FeatureState value : state.features()) { require(value != null && validRule(value.feature(), "character.feature") && value.owner() != null && (validRule(value.owner(), "character.class") || validRule(value.owner(), "character.subclass"))); RuleIdentity owner = value.owner().ruleType().equals("character.class") ? value.owner() : subclassOwners.get(value.owner()); require(owner != null && levels.containsKey(owner) && value.acquiredAtClassLevel() >= 1 && value.acquiredAtClassLevel() <= levels.get(owner) && (previous == null || RULE_ORDER.compare(previous, value.feature()) < 0)); previous = value.feature(); }
        previous = null;
        for (FeatState value : state.feats()) { require(value != null && validRule(value.feat(), "character.feat") && validRule(value.characterClass(), "character.class") && levels.containsKey(value.characterClass()) && value.acquiredAtClassLevel() >= 1 && value.acquiredAtClassLevel() <= levels.get(value.characterClass()) && (previous == null || RULE_ORDER.compare(previous, value.feat()) < 0)); previous = value.feat(); }
        ProficiencyState previousGrant = null;
        for (ProficiencyState value : state.proficiencies()) { require(value != null && validProficiencySourceSlot(value.sourceSlot()) && validProficiency(value.target()) && value.acquiredAtTotalLevel() >= 1 && value.acquiredAtTotalLevel() <= state.level()); if (previousGrant != null) { int compare = SLOT_ORDER.compare(previousGrant.sourceSlot(), value.sourceSlot()); require(compare < 0 || compare == 0 && RULE_ORDER.compare(previousGrant.target(), value.target()) < 0); } previousGrant = value; }
        ResourceState previousResource = null;
        for (ResourceState resource : state.resources()) { require(resource != null && validRule(resource.identity(), "character.resource") && resource.current() >= 0 && resource.maximum() >= 0 && resource.maximum() <= 1000000 && resource.current() <= resource.maximum() && (!resource.unlimited() || resource.current() == 0 && resource.maximum() == 0)); require(previousResource == null || RESOURCE_ORDER.compare(previousResource, resource) < 0); previousResource = resource; }
    }
    private static Root root(State state) { return new Root(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId()); }
    private void insertRoot(Connection connection, Root root) throws SQLException {
        verifyPlan(connection, root); require(findByRun(connection, root.identity.runId()) == null);
        insert(connection, "s2_character_runtime", "character_id, campaign_id, run_id, snapshot_id, preparation_operation_id", bytes(root.character), bytes(root.campaign), root.identity.runBytes(), root.identity.snapshotBytes(), bytes(root.preparation));
    }
    private static void verifyPlan(Connection connection, Root root) throws SQLException {
        List<Boolean> plans = query(connection, "s2_initial_plan", "operation_id", bytes(root.preparation), "operation_id", 1, row -> {
            require(root.character.equals(id(row, "character_id")) && root.campaign.equals(id(row, "campaign_id"))
                    && root.identity.runId().equals(id(row, "run_id")) && root.identity.snapshotId().equals(id(row, "snapshot_id"))); return true;
        }); require(plans.size() == 1);
    }
    private static void insertEvent(Connection connection, Event event) throws SQLException {
        State state = event.after(); Command command = event.command(); GrowthChoices growth = command.growthChoices();
        List<Object> values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1, bytes(event.operationId()), ascii(event.requestDigest()), ascii(event.kind())));
        values.addAll(ruleValues(command.resource())); values.add(command.amount()); values.add(command.recoveryTrigger() == null ? null : ascii(command.recoveryTrigger())); values.add(command.hpChoice() == null ? null : ascii(command.hpChoice()));
        values.addAll(ruleValues(command.targetClass())); values.addAll(ruleValues(growth.subclass())); values.addAll(ruleValues(growth.feat())); values.add(growth.abilityIncreases().size()); values.add((int) growth.proficiencyChoices().values().stream().mapToLong(List::size).sum()); RestInput input = command.restInput();
        Collections.addAll(values, input == null || input.restKind() == null ? null : ascii(input.restKind()), input == null || input.activity() == null ? null : ascii(input.activity()), input == null ? 0L : input.minutes(), input == null ? 0 : input.hitDieSides(), input == null ? 0 : input.recoveredHitDice().size(), event.hitDieRoll());
        insert(connection, "s2_character_event", "character_id, event_sequence, operation_id, request_digest, event_kind, resource_module, resource_type, resource_key, resource_amount, recovery_trigger, hp_choice, target_class_module, target_class_type, target_class_key, chosen_subclass_module, chosen_subclass_type, chosen_subclass_key, chosen_feat_module, chosen_feat_type, chosen_feat_key, ability_choice_count, proficiency_choice_count, rest_kind, rest_activity, rest_minutes, rest_hit_die_sides, recovered_hit_die_count, hit_die_roll", values.toArray());
        int index = 0;
        for (var choice : growth.abilityIncreases().entrySet()) insert(connection, "s2_character_command_ability", "character_id, event_sequence, choice_index, ability_key, increase_value", bytes(state.characterId()), state.version() + 1, index++, ascii(choice.getKey()), choice.getValue());
        index = 0;
        for (var slot : growth.proficiencyChoices().entrySet()) for (RuleIdentity target : slot.getValue()) {
            values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1, index++)); values.addAll(slotValues(slot.getKey())); values.addAll(ruleValues(target));
            insert(connection, "s2_character_command_proficiency", "character_id, event_sequence, choice_index, " + SLOT_COLUMNS + ", target_module, target_type, target_key", values.toArray());
        }
        index = 0;
        if (input != null) for (var pool : input.recoveredHitDice().entrySet()) insert(connection, "s2_character_command_hit_die", "character_id, event_sequence, pool_index, sides, recovered_value", bytes(state.characterId()), state.version() + 1, index++, pool.getKey(), pool.getValue());
        if (event.before() != null) insertState(connection, state.version() + 1, "BEFORE", event.before());
        insertState(connection, state.version() + 1, "AFTER", state);
    }
    private static final String SLOT_COLUMNS = "source_module, source_type, source_key, collection_key, slot_module, slot_key";
    private static List<Object> ruleValues(RuleIdentity identity) { return identity == null ? Arrays.asList(null, null, null) : List.of(ascii(identity.moduleKey()), ascii(identity.ruleType()), ascii(identity.ruleKey())); }
    private static List<Object> slotValues(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot) { return List.of(ascii(slot.rule().moduleKey()), ascii(slot.rule().ruleType()), ascii(slot.rule().ruleKey()), ascii(slot.collectionKey()), ascii(slot.moduleKey()), ascii(slot.entryKey())); }
    private static com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot(ResultSet row) throws SQLException { return new com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity(rule(row, "source"), ascii(row, "collection_key", 32), ascii(row, "slot_module", 64), ascii(row, "slot_key", 64)); }
    private static void insertState(Connection connection, long sequence, String phase, State state) throws SQLException {
        List<Object> values = new ArrayList<>(List.of(bytes(state.characterId()), sequence, ascii(phase))); values.addAll(ruleValues(state.characterClass()));
        for (String ability : ABILITIES) values.add(state.abilities().get(ability));
        Collections.addAll(values, state.level(), state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(), state.version(), state.resources().size(), state.classes().size(), state.hitDicePools().size(), state.subclasses().size(), state.features().size(), state.feats().size(), state.proficiencies().size());
        RestState rest = state.rest(); RestAttempt attempt = rest.active();
        Collections.addAll(values, rest.minute(), rest.lastLongRestEndMinute()); values.addAll(ruleValues(attempt == null ? null : attempt.rule()));
        Collections.addAll(values, attempt == null ? null : ascii(attempt.kind()), attempt == null ? null : attempt.startMinute(), attempt == null ? null : attempt.hitPointsAtStart(),
                attempt == null ? null : attempt.sleepMinutes(), attempt == null ? null : attempt.lightMinutes(), attempt == null ? null : attempt.strenuousMinutes(), attempt == null ? null : ascii(attempt.phase()),
                attempt == null || attempt.lastActivity() == null ? null : ascii(attempt.lastActivity()), attempt == null ? null : attempt.consecutiveStrenuousMinutes(), attempt == null ? null : attempt.interrupted() ? 1 : 0);
        insert(connection, "s2_character_event_state", "character_id, event_sequence, phase, class_module, class_type, class_key, strength, dexterity, constitution, intelligence, wisdom, charisma, total_level, proficiency_bonus, hit_points, maximum_hit_points, row_version, resource_count, class_count, hit_die_count, subclass_count, feature_count, feat_count, proficiency_count, minute, last_long_rest_end_minute, rest_module, rest_type, rest_key, rest_kind, rest_start_minute, rest_start_hit_points, sleep_minutes, light_minutes, strenuous_minutes, rest_phase, last_activity, consecutive_strenuous_minutes, interrupted", values.toArray());
        int i = 0;
        for (ResourceState value : state.resources()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.identity())); Collections.addAll(values, value.current(), value.maximum(), value.unlimited() ? 1 : 0); insert(connection, "s2_character_event_resource", "character_id, event_sequence, phase, resource_index, resource_module, resource_type, resource_key, current_value, maximum_value, unlimited", values.toArray()); }
        i = 0;
        for (ClassLevel value : state.classes()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.characterClass())); values.add(value.level()); insert(connection, "s2_character_event_class", "character_id, event_sequence, phase, class_index, class_module, class_type, class_key, class_level", values.toArray()); }
        i = 0;
        for (HitDicePool value : state.hitDicePools()) insert(connection, "s2_character_event_hit_die", "character_id, event_sequence, phase, pool_index, sides, current_value, maximum_value", bytes(state.characterId()), sequence, ascii(phase), i++, value.sides(), value.current(), value.maximum());
        i = 0;
        for (SubclassState value : state.subclasses()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.characterClass())); values.addAll(ruleValues(value.subclass())); values.add(value.acquiredAtClassLevel()); insert(connection, "s2_character_event_subclass", "character_id, event_sequence, phase, subclass_index, class_module, class_type, class_key, subclass_module, subclass_type, subclass_key, acquired_class_level", values.toArray()); }
        i = 0;
        for (FeatureState value : state.features()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.feature())); values.addAll(ruleValues(value.owner())); values.add(value.acquiredAtClassLevel()); insert(connection, "s2_character_event_feature", "character_id, event_sequence, phase, feature_index, feature_module, feature_type, feature_key, owner_module, owner_type, owner_key, acquired_class_level", values.toArray()); }
        i = 0;
        for (FeatState value : state.feats()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.feat())); values.addAll(ruleValues(value.characterClass())); values.add(value.acquiredAtClassLevel()); insert(connection, "s2_character_event_feat", "character_id, event_sequence, phase, feat_index, feat_module, feat_type, feat_key, class_module, class_type, class_key, acquired_class_level", values.toArray()); }
        i = 0;
        for (ProficiencyState value : state.proficiencies()) { values = childValues(state, sequence, phase, i++); values.addAll(slotValues(value.sourceSlot())); values.addAll(ruleValues(value.target())); values.add(value.acquiredAtTotalLevel()); insert(connection, "s2_character_event_proficiency", "character_id, event_sequence, phase, proficiency_index, " + SLOT_COLUMNS + ", target_module, target_type, target_key, acquired_total_level", values.toArray()); }
    }
    private static List<Object> childValues(State state, long sequence, String phase, int index) { return new ArrayList<>(List.of(bytes(state.characterId()), sequence, ascii(phase), index)); }
    private static RestState readRest(ResultSet row) throws SQLException {
        RuleIdentity rule = nullableRule(row, "rest"); RestAttempt attempt = null;
        if (rule == null) {
            for (String column : List.of("rest_kind", "rest_start_minute", "rest_start_hit_points", "sleep_minutes", "light_minutes", "strenuous_minutes", "rest_phase", "last_activity", "consecutive_strenuous_minutes", "interrupted")) require(row.getObject(column) == null);
        } else {
            int interrupted = integer(row, "interrupted"); require(interrupted <= 1);
            attempt = new RestAttempt(rule, ascii(row, "rest_kind", 16), number(row, "rest_start_minute"), integer(row, "rest_start_hit_points"), number(row, "sleep_minutes"), number(row, "light_minutes"), number(row, "strenuous_minutes"), ascii(row, "rest_phase", 24), nullableAscii(row, "last_activity", 16), number(row, "consecutive_strenuous_minutes"), interrupted == 1);
        }
        return new RestState(number(row, "minute"), row.getObject("last_long_rest_end_minute") == null ? null : number(row, "last_long_rest_end_minute"), attempt);
    }
    private static RuleIdentity nullableRule(ResultSet row, String prefix) throws SQLException { if (row.getObject(prefix + "_module") == null) { require(row.getObject(prefix + "_type") == null && row.getObject(prefix + "_key") == null); return null; } return rule(row, prefix); }
    private static RuleIdentity rule(ResultSet row, String prefix) throws SQLException { return new RuleIdentity(ascii(row, prefix + "_module", 64), ascii(row, prefix + "_type", 32), ascii(row, prefix + "_key", 128)); }
    private static void insert(Connection connection, String table, String columns, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (" + columns + ") VALUES (" + String.join(", ", Collections.nCopies(values.length, "?")) + ")")) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value == null) statement.setNull(i + 1, Types.NULL);
                else if (value instanceof byte[] binary) statement.setBytes(i + 1, binary);
                else if (value instanceof Integer number) statement.setInt(i + 1, number);
                else if (value instanceof Long number) statement.setLong(i + 1, number);
                else throw corrupt();
            } require(statement.executeUpdate() == 1);
        }
    }
    private static <T> List<T> query(Connection connection, String table, String key, byte[] value, String order, int maximum, Reader<T> reader) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + key + " = ? ORDER BY " + order + " LIMIT " + (maximum + 1))) {
            statement.setQueryTimeout(5); statement.setMaxRows(maximum + 1); statement.setBytes(1, value);
            try (ResultSet row = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (row.next()) { require(result.size() < maximum && Arrays.equals(binary(row, key), value));
                    try { result.add(reader.read(row)); } catch (IllegalArgumentException | NullPointerException malformed) { throw new SQLException("S2_CHARACTER_RUNTIME_CORRUPT", malformed); }
                } return result;
            }
        }
    }
    private static String ascii(ResultSet row, String key, int maximum) throws SQLException {
        byte[] value = binary(row, key); require(value.length >= 1 && value.length <= maximum);
        for (byte b : value) require(b >= 0x21 && b <= 0x7e); return new String(value, StandardCharsets.US_ASCII);
    }
    private static String nullableAscii(ResultSet row, String key, int maximum) throws SQLException { return row.getObject(key) == null ? null : ascii(row, key, maximum); }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static byte[] binary(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof byte[]); return (byte[]) value; }
    private static long number(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long); long result = ((Number) value).longValue(); require(result >= 0); return result; }
    private static int integer(ResultSet row, String key) throws SQLException { long value = number(row, key); require(value <= Integer.MAX_VALUE); return (int) value; }
    private static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    private static UUID id(ResultSet row, String key) throws SQLException { byte[] value = binary(row, key); require(value.length == 16); ByteBuffer b = ByteBuffer.wrap(value); UUID id = new UUID(b.getLong(), b.getLong()); uuid(id); return id; }
    private static boolean validProficiency(RuleIdentity target) { return validRule(target, "character.proficiency") || validRule(target, "character.tool"); }
    private static boolean validProficiencySourceSlot(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot) {
        if (validSlot(slot)) return true;
        return slot != null && slot.rule() != null && Set.of("character.race", "character.subrace", "character.background", "character.class").contains(slot.rule().ruleType())
                && validRule(slot.rule(), slot.rule().ruleType()) && "proficiency_grants".equals(slot.collectionKey())
                && slot.moduleKey() != null && slot.moduleKey().matches("[a-z][a-z0-9_]{0,63}") && slot.entryKey() != null && slot.entryKey().matches("[a-z][a-z0-9_]{0,63}");
    }
    private static boolean validSlot(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot) {
        return slot != null && validRule(slot.rule(), "character.class_progression") && Set.of("starting_proficiencies", "multiclass_proficiencies").contains(slot.collectionKey())
                && slot.moduleKey() != null && slot.moduleKey().matches("[a-z][a-z0-9_]{0,63}") && slot.entryKey() != null && slot.entryKey().matches("[a-z][a-z0-9_]{0,63}");
    }
    private static boolean validRule(RuleIdentity identity, String type) {
        return identity != null && type.equals(identity.ruleType()) && identity.moduleKey() != null && identity.moduleKey().matches("[a-z][a-z0-9_]{0,63}")
                && identity.ruleKey() != null && identity.ruleKey().length() <= 128 && identity.ruleKey().matches("[a-z][a-z0-9_.]{0,127}")
                && (switch (type) { case "character.resource" -> validPrefix(identity.ruleKey(), "resource"); case "character.class_progression" -> validPrefix(identity.ruleKey(), "progression"); case "character.subclass" -> validPrefix(identity.ruleKey(), "subclass"); case "character.feature" -> validPrefix(identity.ruleKey(), "feature"); case "character.feat" -> validPrefix(identity.ruleKey(), "feat"); case "character.proficiency" -> validPrefix(identity.ruleKey(), "proficiency"); case "adventure.rest" -> validPrefix(identity.ruleKey(), "rest"); default -> true; });
    }
    private static boolean validPrefix(String key, String prefix) { return key.matches(prefix + "\\.[a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)*"); }
    private static void uuid(UUID value) throws SQLException { require(value != null && value.version() == 4 && value.variant() == 2); }
    private static void require(boolean valid) throws SQLException { if (!valid) throw corrupt(); }
    private static SQLException corrupt() { return new SQLException("S2_CHARACTER_RUNTIME_CORRUPT"); }
    private record StateKey(long sequence, String phase) { }
    private record Header(UUID operation, String digest, String kind, RuleIdentity resource, long amount, String trigger, String hp, RuleIdentity targetClass, RuleIdentity subclass, RuleIdentity feat, int abilities, int proficiencies, String restKind, String activity, long minutes, int dieSides, int recoveredDice, Integer roll) { }
    private record ChoiceTarget(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot, RuleIdentity target) { }
    private record Root(Identity identity, UUID campaign, UUID character, UUID preparation) { }
    @FunctionalInterface private interface Reader<T> { T read(ResultSet row) throws SQLException; }
}
