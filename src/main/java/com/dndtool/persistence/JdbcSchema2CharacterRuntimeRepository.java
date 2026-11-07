package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.Schema2RuntimeFactsException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import static com.dndtool.service.Schema2CharacterRuntime.*;

/** Append-only, typed character execution facts. The caller owns the guarded transaction. */
public final class JdbcSchema2CharacterRuntimeRepository {
    /** No source connection or rules execution is used to reconstruct committed facts. */
    public History read(Connection connection, UUID characterId) throws SQLException {
        uuid(characterId);
        List<Root> roots = query(connection, "s2_character_runtime", "character_id", bytes(characterId), "character_id", 1, row -> {
            require(id(row, "character_id").equals(characterId));
            return new Root(new Schema2PreparationIdentity(id(row, "run_id"), id(row, "snapshot_id")), id(row, "campaign_id"), characterId, id(row, "preparation_operation_id"));
        });
        if (roots.isEmpty()) {
            for (String table : List.of("s2_character_event", "s2_character_event_state", "s2_character_event_resource", "s2_character_event_class", "s2_character_event_hit_die", "s2_character_event_subclass", "s2_character_event_feature", "s2_character_event_feat", "s2_character_event_proficiency", "s2_character_command_ability", "s2_character_command_proficiency", "s2_character_command_hit_die", "s2_character_arcane_recovery_choice", "s2_character_check", "s2_character_event_equipment", "s2_character_equipment_command", "s2_character_health_event", "s2_character_health_die", "s2_character_combat_input", "s2_character_event_learned_spell", "s2_character_event_prepared_spell", "s2_character_spellbook_command", "s2_character_spellbook_choice"))
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
                    row.getObject("hit_die_roll") == null ? null : integer(row, "hit_die_roll"), integer(row, "arcane_recovery_choice_count")));
            return true;
        });
        require(!headers.isEmpty());
        Map<Long, CheckFacts> checks = new HashMap<>();
        query(connection, "s2_character_check", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size());
            Object adjudication = row.getObject("adjudication"); require(adjudication instanceof String);
            int advantage = integer(row, "advantage"), disadvantage = integer(row, "disadvantage"), success = integer(row, "success");
            int effectiveAdvantage = integer(row, "effective_advantage"), effectiveDisadvantage = integer(row, "effective_disadvantage");
            require(advantage <= 1 && disadvantage <= 1 && success <= 1 && effectiveAdvantage <= 1 && effectiveDisadvantage <= 1);
            CheckInput input = new CheckInput(rule(row, "check"), ascii(row, "check_kind", 8), ascii(row, "ability_key", 32),
                    nullableRule(row, "proficiency"), integer(row, "difficulty_class"), advantage == 1, disadvantage == 1, (String) adjudication);
            CheckResult result = new CheckResult(integer(row, "first_d20"), row.getObject("second_d20") == null ? null : integer(row, "second_d20"),
                    integer(row, "selected_d20"), signedInteger(row, "ability_modifier"), integer(row, "proficiency_bonus"), signedInteger(row, "total"), success == 1, effectiveAdvantage == 1, effectiveDisadvantage == 1);
            require(checks.putIfAbsent(sequence, new CheckFacts(input, result)) == null); return true;
        });
        Map<Long, EquipmentInput> equipmentCommands = new HashMap<>();
        query(connection, "s2_character_equipment_command", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size());
            EquipmentInput input = new EquipmentInput(id(row, "item_id"), nullableRule(row, "item"), nullableAscii(row, "location", 16), text(row, "adjudication"));
            require(equipmentCommands.putIfAbsent(sequence, input) == null); return true;
        });
        Map<Long, List<Integer>> healthDice = new HashMap<>();
        query(connection, "s2_character_health_die", "character_id", bytes(characterId), "event_sequence, die_index", MAX_EVENTS * MAX_HEALTH_DICE, row -> {
            long sequence = eventSequence(row, headers.size()); var values = healthDice.computeIfAbsent(sequence, ignored -> new ArrayList<>());
            require(integer(row, "die_index") == values.size() && values.size() < MAX_HEALTH_DICE); values.add(integer(row, "die_value")); return true;
        });
        Map<Long, HealthFacts> healthEvents = new HashMap<>();
        query(connection, "s2_character_health_event", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size()); List<Integer> values = healthDice.remove(sequence); if (values == null) values = List.of();
            require(values.size() == integer(row, "die_count"));
            HealthInput input = new HealthInput(rule(row, "health"), nullableRule(row, "effect"), nullableAscii(row, "temporary_choice", 8), text(row, "adjudication"));
            HealthResult result = new HealthResult(values, row.getObject("death_d20") == null ? null : integer(row, "death_d20"),
                    row.getObject("recovery_d4") == null ? null : integer(row, "recovery_d4"), integer(row, "rolled_amount"), integer(row, "applied_amount"));
            require(healthEvents.putIfAbsent(sequence, new HealthFacts(input, result)) == null); return true;
        });
        Map<Long, CombatInput> combatInputs = new HashMap<>();
        query(connection, "s2_character_combat_input", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size());
            require(id(row, "operation_id").equals(headers.get((int) sequence - 1).operation));
            int critical = integer(row, "critical"); require(critical <= 1);
            CombatInput input = new CombatInput(id(row, "combat_id"), id(row, "combat_operation_id"), id(row, "frame_key"),
                    id(row, "actor_id"), integer(row, "damage_amount"), critical == 1, integer(row,"seconds_advanced"));
            require(combatInputs.putIfAbsent(sequence, input) == null); return true;
        });
        require(healthDice.isEmpty());
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
        Map<Long, Map<Integer, Integer>> recoveredSlots = new HashMap<>();
        query(connection, "s2_character_arcane_recovery_choice", "character_id", bytes(characterId), "event_sequence, slot_rank", MAX_EVENTS * 5, row -> {
            long sequence = eventSequence(row, headers.size());
            var recovered = recoveredSlots.computeIfAbsent(sequence, ignored -> new LinkedHashMap<>());
            require(recovered.size() < 5 && recovered.putIfAbsent(integer(row, "slot_rank"), integer(row, "recovered_value")) == null);
            return true;
        });
        var classes = readChildren(connection, characterId, "class", "class_index", MAX_CLASSES, headers.size(), row -> new ClassLevel(rule(row, "class"), integer(row, "class_level")));
        var dice = readChildren(connection, characterId, "hit_die", "pool_index", MAX_HIT_DICE_POOLS, headers.size(), row -> new HitDicePool(integer(row, "sides"), integer(row, "current_value"), integer(row, "maximum_value")));
        var subclasses = readChildren(connection, characterId, "subclass", "subclass_index", MAX_CLASSES, headers.size(), row -> new SubclassState(rule(row, "class"), rule(row, "subclass"), integer(row, "acquired_class_level")));
        var features = readChildren(connection, characterId, "feature", "feature_index", MAX_FEATURES, headers.size(), row -> new FeatureState(rule(row, "feature"), rule(row, "owner"), integer(row, "acquired_class_level")));
        var feats = readChildren(connection, characterId, "feat", "feat_index", MAX_FEATS, headers.size(), row -> new FeatState(rule(row, "feat"), rule(row, "class"), integer(row, "acquired_class_level")));
        var proficiencies = readChildren(connection, characterId, "proficiency", "proficiency_index", MAX_GRANTS, headers.size(), row -> new ProficiencyState(slot(row), rule(row, "target"), integer(row, "acquired_total_level")));
        var equipment = readChildren(connection, characterId, "equipment", "equipment_index", MAX_EQUIPMENT, headers.size(), row -> new EquipmentItem(id(row, "item_id"), rule(row, "item"), ascii(row, "location", 16),
                row.getObject("ground_combat_id")==null?null:id(row,"ground_combat_id"),
                row.getObject("ground_x")==null?null:signedInteger(row,"ground_x"),row.getObject("ground_y")==null?null:signedInteger(row,"ground_y")));

        var learnedSpells=readChildren(connection,characterId,"learned_spell","spell_index",14,headers.size(),row->new LearnedSpell(rule(row,"spell"),integer(row,"acquired_class_level")));
        var preparedSpells=readChildren(connection,characterId,"prepared_spell","spell_index",15,headers.size(),row->rule(row,"spell"));
        Map<Long,List<RuleIdentity>> learnedChoices=new HashMap<>(),preparedChoices=new HashMap<>();
        query(connection,"s2_character_spellbook_choice","character_id",bytes(characterId),"event_sequence, choice_kind, spell_index",MAX_EVENTS*21,row->{
            long seq=eventSequence(row,headers.size()); String kind=ascii(row,"choice_kind",8);require(Set.of("LEARNED","PREPARED").contains(kind));
            var choices=(kind.equals("LEARNED")?learnedChoices:preparedChoices).computeIfAbsent(seq,k->new ArrayList<>());
            require(integer(row,"spell_index")==choices.size() && choices.size()<(kind.equals("LEARNED")?6:15));choices.add(rule(row,"spell"));return true;
        });
        Map<Long,SpellbookInput> spellbookCommands=new HashMap<>();
        query(connection,"s2_character_spellbook_command","character_id",bytes(characterId),"event_sequence",MAX_EVENTS,row->{
            long seq=eventSequence(row,headers.size());var learned=learnedChoices.remove(seq);var prepared=preparedChoices.remove(seq);
            if(learned==null)learned=List.of();if(prepared==null)prepared=List.of();
            require(integer(row,"learned_count")==learned.size() && integer(row,"prepared_count")==prepared.size());
            SpellbookInput input=new SpellbookInput(rule(row,"casting"),learned,prepared);
            require(input.learned().equals(learned)&&input.prepared().equals(prepared)&&spellbookCommands.putIfAbsent(seq,input)==null);return true;
        });
        require(learnedChoices.isEmpty()&&preparedChoices.isEmpty());
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
                    take(features, key, integer(row, "feature_count")), take(feats, key, integer(row, "feat_count")), take(proficiencies, key, integer(row, "proficiency_count")), number(row, "row_version"), resources, readRest(row),
                    take(equipment, key, integer(row, "equipment_count")), readHealth(row), readSpellbook(row,take(learnedSpells,key,integer(row,"learned_spell_count")),take(preparedSpells,key,integer(row,"prepared_spell_count"))));
            require(states.putIfAbsent(key, state) == null); return true;
        });
        require(resourceRows.isEmpty() && classes.isEmpty() && dice.isEmpty() && subclasses.isEmpty() && features.isEmpty() && feats.isEmpty() && proficiencies.isEmpty() && equipment.isEmpty() && learnedSpells.isEmpty() && preparedSpells.isEmpty() && states.size() == headers.size() * 2 - 1);
        List<Event> events = new ArrayList<>();
        for (int index = 0; index < headers.size(); index++) {
            Header header = headers.get(index);
            State before = index == 0 ? null : states.remove(new StateKey(index + 1, "BEFORE"));
            State after = states.remove(new StateKey(index + 1, "AFTER"));
            require(after != null && (index == 0 || before != null));
            Map<String, Integer> increases = abilityChoices.remove(index + 1L); if (increases == null) increases = Map.of();
            List<ChoiceTarget> chosen = proficiencyChoices.remove(index + 1L); if (chosen == null) chosen = List.of();
            require(increases.size() == header.abilities && chosen.size() == header.proficiencies);
            var selected = new TreeMap<EntryIdentity, List<RuleIdentity>>(SLOT_ORDER);
            for (ChoiceTarget choice : chosen) selected.computeIfAbsent(choice.slot, ignored -> new ArrayList<>()).add(choice.target);
            try {
                GrowthChoices growth = new GrowthChoices(header.subclass, increases, header.feat, selected);
                List<ChoiceTarget> canonical = new ArrayList<>();
                growth.proficiencyChoices().forEach((slot, targets) -> targets.forEach(target -> canonical.add(new ChoiceTarget(slot, target))));
                require(canonical.equals(chosen) && new ArrayList<>(growth.abilityIncreases().keySet()).equals(new ArrayList<>(increases.keySet())));
                Map<Integer, Integer> recovered = recoveredDice.remove(index + 1L); if (recovered == null) recovered = Map.of();
                require(recovered.size() == header.recoveredDice);
                Map<Integer, Integer> slots = recoveredSlots.remove(index + 1L); if (slots == null) slots = Map.of();
                require(slots.size() == header.recoveredSlots);
                RestInput input = null;
                if (restKind(header.kind)) {
                    input = new RestInput(header.restKind, header.activity, header.minutes, header.dieSides, recovered, slots);
                    require(new ArrayList<>(input.recoveredHitDice().keySet()).equals(new ArrayList<>(recovered.keySet())));
                    require(new ArrayList<>(input.recoveredSpellSlots().keySet()).equals(new ArrayList<>(slots.keySet())));
                } else require(header.restKind == null && header.activity == null && header.minutes == 0 && header.dieSides == 0 && recovered.isEmpty() && slots.isEmpty());
                CheckFacts check = checks.remove(index + 1L);
                require("CHECK_RESOLVED".equals(header.kind) == (check != null));
                EquipmentInput equipmentInput = equipmentCommands.remove(index + 1L); HealthFacts health = healthEvents.remove(index + 1L);
                require(equipmentKind(header.kind) == (equipmentInput != null) && healthKind(header.kind) == (health != null));
                CombatInput combat = combatInputs.remove(index + 1L); require(combatKind(header.kind) == (combat != null));
                SpellbookInput book=spellbookCommands.remove(index+1L);require(spellbookKind(header.kind)==(book!=null));
                Command command = new Command(header.kind, header.resource, header.amount, header.trigger, header.hp, header.targetClass, growth, input, check == null ? null : check.input(), equipmentInput, health == null ? null : health.input(), combat, book);
                events.add(new Event(header.operation, header.digest, command, header.roll, before, after, check == null ? null : check.result(), health == null ? null : health.result()));
            } catch (IllegalArgumentException malformed) { throw new SQLException("S2_CHARACTER_RUNTIME_CORRUPT", malformed); }
        }
        require(states.isEmpty() && abilityChoices.isEmpty() && proficiencyChoices.isEmpty() && recoveredDice.isEmpty() && recoveredSlots.isEmpty() && checks.isEmpty() && equipmentCommands.isEmpty() && healthEvents.isEmpty() && combatInputs.isEmpty() && spellbookCommands.isEmpty());
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
        BusinessWriteTransactions.requireGuarded(connection);
        try {
            validateEvent(event);
        } catch (Schema2RuntimeFactsException invalid) {
            throw new SQLException(invalid.getMessage(), invalid);
        }
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

    public static void validate(History history) throws SQLException {
        try {
            Schema2CharacterRuntime.validate(history);
        } catch (Schema2RuntimeFactsException invalid) {
            throw new SQLException(invalid.getMessage(), invalid);
        }
    }

    private static SpellbookState readSpellbook(ResultSet row,List<LearnedSpell> learned,List<RuleIdentity> prepared)throws SQLException {
        RuleIdentity casting=nullableRule(row,"spellbook_casting");
        if(casting==null) {
            require(row.getObject("spellbook_learned_level")==null && row.getObject("spellbook_prepared_rest_minute")==null
                    && row.getObject("spellbook_prepared_rest_second")==null && row.getObject("arcane_recovery_day")==null && learned.isEmpty() && prepared.isEmpty());return null;
        }
        return new SpellbookState(casting,integer(row,"spellbook_learned_level"),learned,prepared,
                row.getObject("spellbook_prepared_rest_minute")==null?null:number(row,"spellbook_prepared_rest_minute"),
                row.getObject("spellbook_prepared_rest_second")==null?null:integer(row,"spellbook_prepared_rest_second"),
                row.getObject("arcane_recovery_day")==null?null:number(row,"arcane_recovery_day"));
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
        Collections.addAll(values, input == null || input.restKind() == null ? null : ascii(input.restKind()), input == null || input.activity() == null ? null : ascii(input.activity()), input == null ? 0L : input.minutes(), input == null ? 0 : input.hitDieSides(), input == null ? 0 : input.recoveredHitDice().size(), event.hitDieRoll(), input == null ? 0 : input.recoveredSpellSlots().size());
        insert(connection, "s2_character_event", "character_id, event_sequence, operation_id, request_digest, event_kind, resource_module, resource_type, resource_key, resource_amount, recovery_trigger, hp_choice, target_class_module, target_class_type, target_class_key, chosen_subclass_module, chosen_subclass_type, chosen_subclass_key, chosen_feat_module, chosen_feat_type, chosen_feat_key, ability_choice_count, proficiency_choice_count, rest_kind, rest_activity, rest_minutes, rest_hit_die_sides, recovered_hit_die_count, hit_die_roll, arcane_recovery_choice_count", values.toArray());
        if (input != null) for (var choice : input.recoveredSpellSlots().entrySet())
            insert(connection, "s2_character_arcane_recovery_choice", "character_id, event_sequence, slot_rank, recovered_value",
                    bytes(state.characterId()), state.version() + 1, choice.getKey(), choice.getValue());
        if(command.spellbookInput()!=null) {
            SpellbookInput book=command.spellbookInput();values=new ArrayList<>(List.of(bytes(state.characterId()),state.version()+1));values.addAll(ruleValues(book.castingRule()));Collections.addAll(values,book.learned().size(),book.prepared().size());
            insert(connection,"s2_character_spellbook_command","character_id, event_sequence, casting_module, casting_type, casting_key, learned_count, prepared_count",values.toArray());
            for(String kind:List.of("LEARNED","PREPARED")) {
                var spells=kind.equals("LEARNED")?book.learned():book.prepared();
                for(int n=0;n<spells.size();n++) {values=new ArrayList<>(List.of(bytes(state.characterId()),state.version()+1,ascii(kind),n));values.addAll(ruleValues(spells.get(n)));
                    insert(connection,"s2_character_spellbook_choice","character_id, event_sequence, choice_kind, spell_index, spell_module, spell_type, spell_key",values.toArray());}
            }
        }
        if (command.checkInput() != null) {
            CheckInput check = command.checkInput(); CheckResult result = event.checkResult();
            values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1));
            values.addAll(ruleValues(check.rule())); Collections.addAll(values, ascii(check.kind()), ascii(check.abilityKey())); values.addAll(ruleValues(check.proficiency()));
            Collections.addAll(values, check.difficultyClass(), check.advantage() ? 1 : 0, check.disadvantage() ? 1 : 0, check.adjudication(),
                    result.firstD20(), result.secondD20(), result.selectedD20(), result.abilityModifier(), result.proficiencyBonus(), result.total(), result.success() ? 1 : 0,
                    result.effectiveAdvantage() ? 1 : 0, result.effectiveDisadvantage() ? 1 : 0);
            insert(connection, "s2_character_check", "character_id, event_sequence, check_module, check_type, check_key, check_kind, ability_key, proficiency_module, proficiency_type, proficiency_key, difficulty_class, advantage, disadvantage, adjudication, first_d20, second_d20, selected_d20, ability_modifier, proficiency_bonus, total, success, effective_advantage, effective_disadvantage", values.toArray());
        }
        if (command.equipmentInput() != null) {
            EquipmentInput inputEquipment = command.equipmentInput(); values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1, bytes(inputEquipment.itemId())));
            values.addAll(ruleValues(inputEquipment.item())); Collections.addAll(values, inputEquipment.location() == null ? null : ascii(inputEquipment.location()), inputEquipment.adjudication());
            insert(connection, "s2_character_equipment_command", "character_id, event_sequence, item_id, item_module, item_type, item_key, location, adjudication", values.toArray());
        }
        if (command.healthInput() != null) {
            HealthInput healthInput = command.healthInput(); HealthResult result = event.healthResult(); values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1));
            values.addAll(ruleValues(healthInput.rule())); values.addAll(ruleValues(healthInput.effect()));
            Collections.addAll(values, healthInput.temporaryChoice() == null ? null : ascii(healthInput.temporaryChoice()), healthInput.adjudication(), result.dice().size(), result.deathD20(), result.recoveryD4(), result.rolledAmount(), result.appliedAmount());
            insert(connection, "s2_character_health_event", "character_id, event_sequence, health_module, health_type, health_key, effect_module, effect_type, effect_key, temporary_choice, adjudication, die_count, death_d20, recovery_d4, rolled_amount, applied_amount", values.toArray());
            for (int i = 0; i < result.dice().size(); i++) insert(connection, "s2_character_health_die", "character_id, event_sequence, die_index, die_value", bytes(state.characterId()), state.version() + 1, i, result.dice().get(i));
        }
        if (command.combatInput() != null) {
            CombatInput combat = command.combatInput();
            insert(connection, "s2_character_combat_input", "operation_id, character_id, event_sequence, combat_id, combat_operation_id, frame_key, actor_id, damage_amount, critical, seconds_advanced",
                    bytes(event.operationId()), bytes(state.characterId()), state.version() + 1, bytes(combat.combatId()),
                    bytes(combat.combatOperationId()), bytes(combat.frameKey()), bytes(combat.actorId()), combat.damageAmount(), combat.critical() ? 1 : 0, combat.secondsAdvanced());
        }
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
    private static List<Object> slotValues(EntryIdentity slot) { return List.of(ascii(slot.rule().moduleKey()), ascii(slot.rule().ruleType()), ascii(slot.rule().ruleKey()), ascii(slot.collectionKey()), ascii(slot.moduleKey()), ascii(slot.entryKey())); }
    private static EntryIdentity slot(ResultSet row) throws SQLException { return new EntryIdentity(rule(row, "source"), ascii(row, "collection_key", 32), ascii(row, "slot_module", 64), ascii(row, "slot_key", 64)); }
    private static void insertState(Connection connection, long sequence, String phase, State state) throws SQLException {
        List<Object> values = new ArrayList<>(List.of(bytes(state.characterId()), sequence, ascii(phase))); values.addAll(ruleValues(state.characterClass()));
        for (String ability : ABILITIES) values.add(state.abilities().get(ability));
        Collections.addAll(values, state.level(), state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(), state.version(), state.resources().size(), state.classes().size(), state.hitDicePools().size(), state.subclasses().size(), state.features().size(), state.feats().size(), state.proficiencies().size());
        RestState rest = state.rest(); RestAttempt attempt = rest.active();
        Collections.addAll(values, rest.minute(), rest.lastLongRestEndMinute()); values.addAll(ruleValues(attempt == null ? null : attempt.rule()));
        Collections.addAll(values, attempt == null ? null : ascii(attempt.kind()), attempt == null ? null : attempt.startMinute(), attempt == null ? null : attempt.hitPointsAtStart(),
                attempt == null ? null : attempt.sleepMinutes(), attempt == null ? null : attempt.lightMinutes(), attempt == null ? null : attempt.strenuousMinutes(), attempt == null ? null : ascii(attempt.phase()),
                attempt == null || attempt.lastActivity() == null ? null : ascii(attempt.lastActivity()), attempt == null ? null : attempt.consecutiveStrenuousMinutes(), attempt == null ? null : attempt.interrupted() ? 1 : 0);
        HealthState health = state.health(); Collections.addAll(values, state.equipment().size(), health.temporaryHitPoints(), health.deathSuccesses(), health.deathFailures(), health.stable() ? 1 : 0, health.dead() ? 1 : 0, health.stableRecoveryMinute(), rest.second(), rest.lastLongRestEndSecond(), health.stableRecoverySecond());
        SpellbookState book=state.spellbook(); values.addAll(ruleValues(book==null?null:book.castingRule()));
        Collections.addAll(values,book==null?null:book.learnedThroughLevel(),book==null?0:book.learned().size(),book==null?0:book.prepared().size(),book==null?null:book.preparedAtRestMinute(),book==null?null:book.preparedAtRestSecond(),book==null?null:book.lastArcaneRecoveryDay());
        insert(connection, "s2_character_event_state", "character_id, event_sequence, phase, class_module, class_type, class_key, strength, dexterity, constitution, intelligence, wisdom, charisma, total_level, proficiency_bonus, hit_points, maximum_hit_points, row_version, resource_count, class_count, hit_die_count, subclass_count, feature_count, feat_count, proficiency_count, minute, last_long_rest_end_minute, rest_module, rest_type, rest_key, rest_kind, rest_start_minute, rest_start_hit_points, sleep_minutes, light_minutes, strenuous_minutes, rest_phase, last_activity, consecutive_strenuous_minutes, interrupted, equipment_count, temporary_hit_points, death_successes, death_failures, stable, dead, stable_recovery_minute, rest_second, last_long_rest_end_second, stable_recovery_second, spellbook_casting_module, spellbook_casting_type, spellbook_casting_key, spellbook_learned_level, learned_spell_count, prepared_spell_count, spellbook_prepared_rest_minute, spellbook_prepared_rest_second, arcane_recovery_day", values.toArray());
        int i = 0;
        if(book!=null) {
            for(LearnedSpell value:book.learned()) {values=childValues(state,sequence,phase,i++);values.addAll(ruleValues(value.spell()));values.add(value.acquiredAtClassLevel());
                insert(connection,"s2_character_event_learned_spell","character_id, event_sequence, phase, spell_index, spell_module, spell_type, spell_key, acquired_class_level",values.toArray());}
            i=0;
            for(RuleIdentity value:book.prepared()) {values=childValues(state,sequence,phase,i++);values.addAll(ruleValues(value));
                insert(connection,"s2_character_event_prepared_spell","character_id, event_sequence, phase, spell_index, spell_module, spell_type, spell_key",values.toArray());}
            i=0;
        }
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
        i = 0;
        for (EquipmentItem value : state.equipment()) { values = childValues(state, sequence, phase, i++); values.add(bytes(value.itemId())); values.addAll(ruleValues(value.item())); Collections.addAll(values,ascii(value.location()),value.groundCombatId()==null?null:bytes(value.groundCombatId()),value.groundX(),value.groundY()); insert(connection, "s2_character_event_equipment", "character_id, event_sequence, phase, equipment_index, item_id, item_module, item_type, item_key, location, ground_combat_id, ground_x, ground_y", values.toArray()); }
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
        return new RestState(number(row, "minute"),integer(row,"rest_second"), row.getObject("last_long_rest_end_minute") == null ? null : number(row, "last_long_rest_end_minute"), row.getObject("last_long_rest_end_second")==null?null:integer(row,"last_long_rest_end_second"), attempt);
    }
    private static HealthState readHealth(ResultSet row) throws SQLException {
        int stable = integer(row, "stable"), dead = integer(row, "dead"); require(stable <= 1 && dead <= 1);
        return new HealthState(integer(row, "temporary_hit_points"), integer(row, "death_successes"), integer(row, "death_failures"), stable == 1, dead == 1,
                row.getObject("stable_recovery_minute") == null ? null : number(row, "stable_recovery_minute"),row.getObject("stable_recovery_second")==null?null:integer(row,"stable_recovery_second"));
    }
    private static String text(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof String); return (String) value; }
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
                else if (value instanceof String text) statement.setString(i + 1, text);
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
    private static int signedInteger(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long); long result = ((Number) value).longValue(); require(result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE); return (int) result; }
    private static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    private static UUID id(ResultSet row, String key) throws SQLException { byte[] value = binary(row, key); require(value.length == 16); ByteBuffer b = ByteBuffer.wrap(value); UUID id = new UUID(b.getLong(), b.getLong()); uuid(id); return id; }
    private static void uuid(UUID value) throws SQLException { require(validUuid(value)); }
    private static void require(boolean valid) throws SQLException { if (!valid) throw corrupt(); }
    private static SQLException corrupt() { return new SQLException("S2_CHARACTER_RUNTIME_CORRUPT"); }
    private record StateKey(long sequence, String phase) { }
    private record Header(UUID operation, String digest, String kind, RuleIdentity resource, long amount, String trigger, String hp, RuleIdentity targetClass, RuleIdentity subclass, RuleIdentity feat, int abilities, int proficiencies, String restKind, String activity, long minutes, int dieSides, int recoveredDice, Integer roll, int recoveredSlots) { }
    private record ChoiceTarget(EntryIdentity slot, RuleIdentity target) { }
    private record CheckFacts(CheckInput input, CheckResult result) { }
    private record HealthFacts(HealthInput input, HealthResult result) { }
    private record Root(Schema2PreparationIdentity identity, UUID campaign, UUID character, UUID preparation) { }
    @FunctionalInterface private interface Reader<T> { T read(ResultSet row) throws SQLException; }
}
