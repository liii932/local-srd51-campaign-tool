package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.module.composition.Schema2CompositionKernel.ProgressionScope;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.Member;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.Dependency;
import com.dndtool.service.Schema2CharacterRuntimeService.Material;
import com.dndtool.service.Schema2CharacterRuntimeService.Request;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntUnaryOperator;

import static com.dndtool.service.Schema2CombatState.*;

/** Pure campaign domain execution and retained-fact verification; never borrows a connection. */
public final class CampaignDomainEngine {
    private final Schema2CharacterProgressionRules rules = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();
    private final Schema2CharacterCheckRules checks = new Schema2CharacterCheckRules();
    private final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
    private final Schema2WizardSpellbookRules spellbooks = new Schema2WizardSpellbookRules();
    private final Schema2CharacterHealthRules health = new Schema2CharacterHealthRules();
    private final Schema2CombatRules combatRules = new Schema2CombatRules();

    public record CharacterRecord(String name, String kind, InitialPlan initial, History history) {
        public CharacterRecord {
            name = CharacterNamePolicy.normalize(name);
            if (!Set.of("PC", "NPC").contains(kind) || initial == null || history == null)
                throw rejected("CAMPAIGN_CHARACTER_RECORD");
        }
    }
    public record CombatResult(JdbcSchema2CombatRuntimeRepository.History history, List<CharacterRecord> characters) {
        public CombatResult { characters = List.copyOf(characters); }
    }

    public History initial(ProductionSchema2Projection material, InitialPlan initial, UUID preparationOperationId,
            Map<String,Integer> abilities, UUID operationId) {
        return initial(material, initial, preparationOperationId, abilities, GrowthChoices.EMPTY, operationId);
    }
    public History initial(ProductionSchema2Projection material, InitialPlan initial, UUID preparationOperationId,
            Map<String,Integer> abilities, GrowthChoices choices, UUID operationId) {
        Objects.requireNonNull(initial);
        var command = new JdbcSchema2CharacterRuntimeRepository.Command("INITIALIZED", null, 0, null, null, null, choices, null);
        Request request = new Request(operationId, initial.character().identity(), initial.campaignId(), initial.characterId(),
                preparationOperationId, Schema2RulesBinding.from(material), -1, command, abilities);
        Event event = characterEvent(new Material(material, initial), null, request,
                sides -> { throw rejected("CAMPAIGN_INITIAL_RANDOMNESS"); });
        return append(null, event);
    }

    public History executeCharacter(ProductionSchema2Projection material, InitialPlan initial, History history,
            JdbcSchema2CharacterRuntimeRepository.Command command, UUID operationId, IntUnaryOperator dice) {
        validateCharacter(material, initial, history);
        State state = history.current();
        Request request = new Request(operationId, state.identity(), state.campaignId(), state.characterId(),
                state.preparationOperationId(), Schema2RulesBinding.from(material), state.version(), command, Map.of());
        if (history.events().stream().anyMatch(e -> e.operationId().equals(operationId)))
            throw rejected("S2_CHARACTER_OPERATION_CONFLICT");
        History result = append(history, characterEvent(new Material(material, initial), history, request, dice));
        validateCharacter(material, initial, result);
        return result;
    }

    public void validateCharacter(ProductionSchema2Projection material, InitialPlan initial, History history) {
        Objects.requireNonNull(material); Objects.requireNonNull(initial);
        try { validateCharacterHistory(new Material(material, initial), history); }
        catch (SQLException invalid) { throw new IllegalArgumentException("S2_CHARACTER_HISTORY_CORRUPT", invalid); }
    }

    /** One campaign command advances every character through its own supported time/health rules. */
    public List<CharacterRecord> executeCampaignCharacter(ProductionSchema2Projection material,
            List<CharacterRecord> supplied, List<JdbcSchema2CombatRuntimeRepository.History> combats,
            UUID targetId, JdbcSchema2CharacterRuntimeRepository.Command command, UUID operationId, IntUnaryOperator dice) {
        validateCampaign(material, supplied, combats);
        if (combats.stream().anyMatch(combat -> combat.current().status() != Status.ENDED))
            throw rejected("S2_CHARACTER_COMBAT_ACTIVE");
        var all = records(material, supplied); var target = all.get(targetId);
        if (target == null) throw rejected("S2_CHARACTER_NOT_FOUND");
        long beforeSecond = seconds(target.history().current());
        if (all.values().stream().anyMatch(record -> seconds(record.history().current()) != beforeSecond))
            throw rejected("S2_COMBAT_CLOCK_MISMATCH");
        Set<UUID> existing = new HashSet<>();
        for (var record : supplied) for (var event : record.history().events()) existing.add(event.operationId());
        for (var combat : combats) for (var event : combat.events()) existing.add(event.operationId());
        if (!existing.add(operationId)) throw rejected("S2_CHARACTER_OPERATION_CONFLICT");
        // This retained-rule minimum branch proves all time/capacity obligations before real RNG.
        History candidate = executeCharacter(material, target.initial(), target.history(), command, operationId, sides -> 1);
        long delta = seconds(candidate.current()) - beforeSecond;
        if (delta < 0 || delta % 60 != 0 || delta > 1440L * 60) throw rejected("S2_REST_TIME_CAPACITY");
        Map<UUID,History> synchronizedHistories = new LinkedHashMap<>();
        if (delta > 0) for (var record : supplied) {
            UUID id = record.history().current().characterId(); if (id.equals(targetId)) continue;
            UUID child = campaignTimeChildOperation(operationId, id);
            if (!existing.add(child)) throw rejected("S2_CHARACTER_OPERATION_CONFLICT");
            var active = record.history().current().rest().active();
            String activity = active == null ? "NONE" : "LIGHT";
            var time = new JdbcSchema2CharacterRuntimeRepository.Command("TIME_ADVANCED", null, 0, null, null, null,
                    GrowthChoices.EMPTY, new RestInput(null, activity, delta / 60, 0, Map.of()));
            synchronizedHistories.put(id, executeCharacter(material, record.initial(), record.history(), time, child,
                    sides -> { throw rejected("CAMPAIGN_TIME_RANDOMNESS"); }));
        }
        History actual = executeCharacter(material, target.initial(), target.history(), command, operationId, dice);
        if (seconds(actual.current()) != seconds(candidate.current())) throw rejected("S2_COMBAT_CLOCK_MISMATCH");
        synchronizedHistories.put(targetId, actual);
        var result = supplied.stream().map(record -> new CharacterRecord(record.name(), record.kind(), record.initial(),
                synchronizedHistories.getOrDefault(record.history().current().characterId(), record.history()))).toList();
        validateCampaign(material, result, combats);
        return result;
    }

    /** Stable causal key; the campaign root records this child together with the target operation. */
    public static UUID campaignTimeChildOperation(UUID rootOperation, UUID characterId) {
        UUID derived = UUID.nameUUIDFromBytes(("campaign-character-time/1/" + rootOperation + "/" + characterId)
                .getBytes(StandardCharsets.US_ASCII));
        return new UUID((derived.getMostSignificantBits() & ~0xf000L) | 0x4000L,
                (derived.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }

    private static long seconds(State state) { return state.rest().minute() * 60L + state.rest().second(); }

    private static History append(History previous, Event event) {
        var events = new ArrayList<Event>(previous == null ? List.of() : previous.events());
        events.add(event);
        return new History(event.after(), events);
    }

    Event characterEvent(Material material, History history, Request request, IntUnaryOperator dice) {
        Objects.requireNonNull(dice);
        if (JdbcSchema2CharacterRuntimeRepository.combatKind(request.command().kind()))
            throw rejected("S2_CHARACTER_COMBAT_OWNER_REQUIRED");
        State before = history == null ? null : history.current();
        health.requireCommand(before, request.command());
        Integer roll = null;
        CheckResult checkResult = null;
        HealthResult healthResult = null;
        State after;
        if (Schema2CharacterHealthRules.handles(request.command())) {
            var prepared = health.prepare(material.projection(), before, request.command());
            checkCapacity(history, prepared.capacityState(), request.command());
            List<Integer> drawn = new ArrayList<>();
            for (int i = 0; i < prepared.diceCount(); i++) drawn.add(dice.applyAsInt(prepared.diceSides()));
            Integer deathD20 = prepared.deathSave() ? dice.applyAsInt(20) : null;
            Integer recoveryD4 = prepared.needsRecoveryD4(deathD20) ? dice.applyAsInt(4) : null;
            var resolution = health.resolve(prepared, drawn, deathD20, recoveryD4);
            healthResult = resolution.result(); after = resolution.state();
        } else if (request.command().kind().equals("CHECK_RESOLVED")) {
            var prepared = checks.prepare(material.projection(), before, request.command());
            checkCapacity(history, prepared.capacityState(), request.command());
            int firstD20 = dice.applyAsInt(20);
            Integer secondD20 = prepared.secondDieRequired() ? dice.applyAsInt(20) : null;
            checkResult = checks.resolve(prepared, firstD20, secondD20);
            after = prepared.capacityState();
        } else if (isAdvancement(request.command())) {
            var prepared = prepareAdvance(material, before, request.command());
            checkCapacity(history, prepared.capacityState(), request.command());
            if (request.command().hpChoice().equals("SERVER_ROLL")) roll = dice.applyAsInt(prepared.hitDieSides());
            after = rules.advance(material.projection(), prepared, roll);
        } else if (request.command().kind().equals("HIT_DIE_SPENT")) {
            // A deterministic minimum result proves the full transition and capacity before
            // calling the random source. Only the actual result is retained in the event.
            State capacity = health.afterRest(before, rests.apply(material.projection(), before, request.command(), 1), request.command());
            checkCapacity(history, capacity, request.command());
            roll = dice.applyAsInt(request.command().restInput().hitDieSides());
            after = health.afterRest(before, rests.apply(material.projection(), before, request.command(), roll), request.command());
        } else {
            after = calculate(material, request, before, null, null, null);
            checkCapacity(history, after, request.command());
        }
        Event event = new Event(request.operationId(), request.digest(), request.command(), roll, before, after, checkResult, healthResult);
        History result = append(history, event);
        try { validateCharacterHistory(material, result); }
        catch (SQLException invalid) { throw new IllegalArgumentException("S2_CHARACTER_HISTORY_CORRUPT", invalid); }
        return event;
    }

    public CombatResult executeCombat(ProductionSchema2Projection material, List<CharacterRecord> supplied,
            JdbcSchema2CombatRuntimeRepository.History previous, UUID combatId, UUID operationId,
            Schema2CombatState.Command command, IntUnaryOperator dice) {
        var all = records(material, supplied);
        if (previous != null) validateCombat(material, supplied, previous);
        UUID campaign = supplied.getFirst().initial().campaignId();
        List<Member> members = previous == null
                ? supplied.stream().map(record -> new Member(record.history().current().characterId(),
                    record.history().current().identity(), record.history().current().preparationOperationId()))
                    .sorted(Comparator.comparing(Member::characterId, JdbcSchema2CombatRuntimeRepository.UUID_ORDER)).toList()
                : previous.members();
        if (!new HashSet<>(members.stream().map(Member::characterId).toList()).equals(all.keySet()))
            throw rejected("S2_COMBAT_BINDING");
        if (previous != null && previous.events().stream().anyMatch(e -> e.operationId().equals(operationId)))
            throw rejected("S2_COMBAT_OPERATION_CONFLICT");
        if (all.values().stream().anyMatch(record -> record.history().events().stream()
                .anyMatch(event -> event.operationId().equals(operationId))))
            throw rejected("S2_COMBAT_OPERATION_CONFLICT");
        var histories = histories(all); var materials = materials(material, all);
        Map<UUID,Long> versions = new LinkedHashMap<>();
        all.forEach((id, record) -> versions.put(id, record.history().current().version()));
        var request = new Schema2CombatRuntimeService.Request(operationId, combatId, campaign, Schema2RulesBinding.from(material),
                members, previous == null ? -1 : previous.current().version(), versions, command);
        var contexts = contexts(members, histories, versions, materials);
        var prepared = combatRules.preflight(operationId, combatId, campaign, contexts,
                previous == null ? null : previous.current(), command);
        reserve(previous, prepared, histories, contexts, request);
        Set<UUID> retainedOperations = new HashSet<>();
        for (var history : histories.values()) for (var event : history.events()) retainedOperations.add(event.operationId());
        if (previous != null) for (var event : previous.events()) retainedOperations.add(event.operationId());
        for (UUID target : prepared.possibleHealthTargets()) for (int ordinal = 0; ordinal < Schema2CombatRules.MAX_COMBATANTS * 2; ordinal++)
            if (retainedOperations.contains(Schema2CombatCharacterFacts.childOperation(operationId, target, ordinal)))
                throw rejected("S2_COMBAT_OPERATION_CONFLICT");
        Resolution resolution = combatRules.resolve(prepared, Objects.requireNonNull(dice));
        List<UUID> children = new ArrayList<>();
        for (var change : resolution.healthChanges()) {
            UUID child = Schema2CombatCharacterFacts.childOperation(operationId, change.characterId(), children.size());
            var childCommand = Schema2CombatCharacterFacts.command(material, combatId, operationId,
                    command.actorId() == null ? change.characterId() : command.actorId(), change);
            var before = change.before();
            Request childRequest = new Request(child, before.identity(), campaign, change.characterId(),
                    before.preparationOperationId(), Schema2RulesBinding.from(material), before.version(), childCommand, Map.of());
            if (!Schema2CombatCharacterFacts.verify(material, before, childCommand, change.healthResult(), change.after()).equals(change.after()))
                throw rejected("S2_COMBAT_HEALTH_RESULT");
            var history = histories.get(change.characterId());
            if (history == null || !history.current().equals(before)) throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
            history = append(history, new Event(child, childRequest.digest(), childCommand, null, before,
                    change.after(), null, change.healthResult()));
            histories.put(change.characterId(), history); children.add(child);
        }
        var dependencies = members.stream().map(m -> new Dependency(m.characterId(), versions.get(m.characterId()))).toList();
        var event = new JdbcSchema2CombatRuntimeRepository.Event(operationId, request.digest(), command,
                dependencies, children, previous == null ? null : previous.current(), resolution);
        var events = new ArrayList<JdbcSchema2CombatRuntimeRepository.Event>(previous == null ? List.of() : previous.events());
        events.add(event);
        var combat = new JdbcSchema2CombatRuntimeRepository.History(resolution.after(), members, events);
        var updated = supplied.stream().map(record -> new CharacterRecord(record.name(), record.kind(), record.initial(),
                histories.get(record.history().current().characterId()))).toList();
        validateCombat(material, updated, combat);
        return new CombatResult(combat, updated);
    }

    public void validateCombat(ProductionSchema2Projection material, List<CharacterRecord> supplied,
            JdbcSchema2CombatRuntimeRepository.History combat) {
        var all = records(material, supplied);
        try {
            JdbcSchema2CombatRuntimeRepository.validate(combat);
            if (!new HashSet<>(combat.members().stream().map(Member::characterId).toList()).equals(all.keySet()))
                throw rejected("S2_COMBAT_BINDING");
            for (Member member : combat.members()) {
                State state = all.get(member.characterId()).history().current();
                if (!member.identity().equals(state.identity()) || !member.preparationOperationId().equals(state.preparationOperationId()))
                    throw rejected("S2_COMBAT_BINDING");
            }
            validateCombatEvents(combat, histories(all), materials(material, all));
        } catch (SQLException invalid) { throw new IllegalArgumentException("S2_COMBAT_HISTORY_CORRUPT", invalid); }
    }

    /** Complete closed-graph validation, including children whose owner scene has already ended. */
    public void validateCampaign(ProductionSchema2Projection material, List<CharacterRecord> supplied,
            List<JdbcSchema2CombatRuntimeRepository.History> combats) {
        if (supplied != null && supplied.isEmpty()) {
            if (combats == null || !combats.isEmpty()) throw rejected("S2_COMBAT_ROSTER");
            Schema2RulesBinding.from(Objects.requireNonNull(material)).verify(material);
            return;
        }
        var all = records(material, supplied);
        long clock = seconds(supplied.getFirst().history().current());
        if (supplied.stream().anyMatch(record -> seconds(record.history().current()) != clock))
            throw rejected("S2_COMBAT_CLOCK_MISMATCH");
        Map<UUID,JdbcSchema2CombatRuntimeRepository.Event> owners = new HashMap<>();
        Set<UUID> sceneIds = new HashSet<>(), eventIds = new HashSet<>();
        if (combats == null || combats.stream().filter(combat -> combat.current().status() != Status.ENDED).count() > 1)
            throw rejected("S2_COMBAT_BINDING");
        for (var combat : combats) {
            if (!sceneIds.add(combat.current().combatId())) throw rejected("S2_COMBAT_BINDING");
            var members = combat.members().stream().map(m -> all.get(m.characterId())).toList();
            if (members.stream().anyMatch(Objects::isNull)) throw rejected("S2_COMBAT_CHARACTER_NOT_FOUND");
            validateCombat(material, members, combat);
            for (var event : combat.events()) {
                if (!eventIds.add(event.operationId())) throw rejected("S2_COMBAT_OPERATION_CONFLICT");
                for (UUID child : event.characterOperations()) if (owners.putIfAbsent(child, event) != null)
                    throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
            }
        }
        Set<UUID> retainedChildren = new HashSet<>();
        for (var record : all.values()) for (var child : record.history().events()) {
            if (!eventIds.add(child.operationId())) throw rejected("S2_CHARACTER_OPERATION_CONFLICT");
            if (child.command().combatInput() == null) continue;
            var input = child.command().combatInput(); var owner = owners.get(child.operationId());
            if (owner == null || !input.combatId().equals(owner.after().combatId())
                    || !input.combatOperationId().equals(owner.operationId()) || !retainedChildren.add(child.operationId()))
                throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
        }
        if (!retainedChildren.equals(owners.keySet())) throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
    }

    private Map<UUID,CharacterRecord> records(ProductionSchema2Projection material, List<CharacterRecord> supplied) {
        if (supplied == null || supplied.isEmpty()) throw rejected("S2_COMBAT_ROSTER");
        Map<UUID,CharacterRecord> all = new LinkedHashMap<>(); UUID campaign = null;
        for (var record : supplied) {
            if (record == null) throw rejected("CAMPAIGN_CHARACTER_RECORD");
            validateCharacter(material, record.initial(), record.history());
            State state = record.history().current();
            if (campaign != null && !campaign.equals(state.campaignId())) throw rejected("S2_COMBAT_BINDING");
            campaign = state.campaignId();
            if (all.putIfAbsent(state.characterId(), record) != null) throw rejected("S2_COMBAT_ROSTER");
        }
        return all;
    }
    private static Map<UUID,History> histories(Map<UUID,CharacterRecord> records) {
        Map<UUID,History> result = new LinkedHashMap<>(); records.forEach((id, r) -> result.put(id, r.history())); return result;
    }
    private static Map<UUID,Material> materials(ProductionSchema2Projection material, Map<UUID,CharacterRecord> records) {
        Map<UUID,Material> result = new LinkedHashMap<>(); records.forEach((id, r) -> result.put(id, new Material(material, r.initial()))); return result;
    }
    void validateCharacterHistory(Material material, History history) throws SQLException {
        try {
            JdbcSchema2CharacterRuntimeRepository.validate(history);
            for (Event event : history.events()) {
                State after = event.after(); rules.validateState(material.projection(), after); rests.validateState(material.projection(), after);
                equipment.validateState(material.projection(), after); health.validateState(after); spellbooks.validateState(material.projection(),after);
                Request request = new Request(event.operationId(), after.identity(), after.campaignId(), after.characterId(),
                        after.preparationOperationId(), Schema2RulesBinding.from(material.projection()),
                        event.before() == null ? -1 : event.before().version(), event.command(),
                        event.before() == null ? after.abilities() : Map.of());
                if (!request.digest().equals(event.requestDigest())
                        || !after.equals(JdbcSchema2CharacterRuntimeRepository.combatKind(event.kind())
                            ? Schema2CombatCharacterFacts.verify(material.projection(),event.before(),event.command(),event.healthResult(),event.after())
                            : calculate(material, request, event.before(), event.hitDieRoll(), event.checkResult(), event.healthResult())))
                    throw new SQLException("S2_CHARACTER_HISTORY_CORRUPT");
            }
        } catch (IllegalArgumentException malformed) { throw new SQLException("S2_CHARACTER_HISTORY_CORRUPT", malformed); }
    }

    private State calculate(Material material, Request request, State before, Integer roll, CheckResult checkResult, HealthResult healthResult) {
        JdbcSchema2CharacterRuntimeRepository.Command command = request.command();
        health.requireCommand(before, command);
        if (JdbcSchema2CharacterRuntimeRepository.combatKind(command.kind())) {
            if (roll != null || checkResult != null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
            throw rejected("S2_CHARACTER_COMBAT_OWNER_REQUIRED");
        }
        if (Schema2CharacterHealthRules.handles(command)) {
            if (roll != null || checkResult != null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
            return health.verify(material.projection(), before, command, healthResult);
        }
        if (healthResult != null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
        if (command.kind().equals("CHECK_RESOLVED")) {
            if (roll != null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
            return checks.verify(material.projection(), before, command, checkResult);
        }
        if (checkResult != null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
        if (Schema2WizardSpellbookRules.handles(command)) {
            if (roll!=null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
            return spellbooks.apply(material.projection(),before,command);
        }
        if (Schema2CharacterEquipmentRules.handles(command)) {
            if (roll != null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
            return equipment.apply(material.projection(), before, command, request.operationId());
        }
        if (Schema2CharacterRestRules.handles(command))
            return health.afterRest(before, rests.apply(material.projection(), before, command, roll), command);
        if (!isAdvancement(command) && roll != null) throw rejected("S2_CHARACTER_HISTORY_CORRUPT");
        return switch (command.kind()) {
            case "INITIALIZED" -> rules.initial(material.projection(), material.initial(), request.preparationOperationId(),
                    request.initialAbilities(), command.growthChoices());
            case "STATISTICS_ADVANCED", "CHARACTER_ADVANCED" -> rules.advance(material.projection(), prepareAdvance(material, before, command), roll);
            case "RESOURCE_SPENT" -> rules.spend(material.projection(), before, command.resource(), command.amount());
            case "RESOURCES_RECOVERED" -> rules.recoverResources(material.projection(), before, command.recoveryTrigger());
            default -> throw rejected("S2_CHARACTER_COMMAND_UNSUPPORTED");
        };
    }

    private static boolean isAdvancement(JdbcSchema2CharacterRuntimeRepository.Command command) {
        return command.kind().equals("STATISTICS_ADVANCED") || command.kind().equals("CHARACTER_ADVANCED");
    }

    private static void checkCapacity(History history, State candidate, JdbcSchema2CharacterRuntimeRepository.Command command) {
        try { JdbcSchema2CharacterRuntimeRepository.checkCandidateCapacity(history, candidate, command); }
        catch (SQLException exhausted) { throw new IllegalArgumentException("S2_CHARACTER_CAPACITY", exhausted); }
    }

    private Schema2CharacterProgressionRules.PreparedAdvance prepareAdvance(Material material, State before, JdbcSchema2CharacterRuntimeRepository.Command command) {
        spellbooks.requireLearningCompleteBeforeAdvance(material.projection(),before);
        var prepared = rules.prepareAdvance(material.projection(), before,
                command.kind().equals("CHARACTER_ADVANCED") ? command.targetClass() : before.characterClass(),
                before.level() + 1, command.hpChoice(), command.growthChoices());
        ProgressionScope required = command.kind().equals("CHARACTER_ADVANCED")
                ? ProgressionScope.CHARACTER_ADVANCEMENT : ProgressionScope.STATISTICS_AND_RESOURCES_ONLY;
        if (prepared.scope() != required) throw rejected("S2_CHARACTER_ADVANCEMENT_SCOPE");
        return prepared;
    }

    Map<UUID,Schema2CombatRules.Context> contexts(List<Member> members,
            Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> histories,Map<UUID,Long> versions,
            Map<UUID,Schema2CharacterRuntimeService.Material> materials) {
        Map<UUID,Schema2CombatRules.Context> result=new LinkedHashMap<>();
        for (Member member:members) {
            var history=histories.get(member.characterId()); Long version=versions.get(member.characterId());
            if (history==null||version==null||version<0||version>=history.events().size()) throw rejected("S2_COMBAT_STALE_CHARACTER");
            var state=history.events().get(Math.toIntExact(version)).after();
            var material=materials.get(member.characterId());
            result.put(member.characterId(),new Schema2CombatRules.Context(material.projection(),material.initial(),state));
        }
        return result;
    }
    void validateCombatEvents(JdbcSchema2CombatRuntimeRepository.History history,Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> all,
            Map<UUID,Schema2CharacterRuntimeService.Material> materials) throws SQLException {
        try {
            Map<UUID,Long> preceding=null;
            for (JdbcSchema2CombatRuntimeRepository.Event event:history.events()) {
                Map<UUID,Long> versions=new LinkedHashMap<>(); event.dependencies().forEach(d->versions.put(d.characterId(),d.version()));
                if (preceding!=null&&!preceding.equals(versions)) throw rejected("S2_COMBAT_DEPENDENCY_HISTORY");
                // Materials were physically verified for this validation call. Only character state varies by event.
                var roster=contexts(history.members(),all,versions,materials);
                var binding=Schema2RulesBinding.from(roster.values().iterator().next().material());
                Schema2CombatRuntimeService.Request request=new Schema2CombatRuntimeService.Request(event.operationId(),history.current().combatId(),history.current().campaignId(),binding,
                        history.members(),event.before()==null?-1:event.before().version(),versions,event.command());
                if (!request.digest().equals(event.requestDigest())) throw rejected("S2_COMBAT_REQUEST_HISTORY");
                combatRules.verify(event.operationId(),request.combatId(),request.campaignId(),roster,event.before(),event.command(),event.resolution());
                for (int i=0;i<event.resolution().healthChanges().size();i++) {
                    var change=event.resolution().healthChanges().get(i);
                    UUID child=Schema2CombatCharacterFacts.childOperation(event.operationId(),change.characterId(),i);
                    if (!event.characterOperations().get(i).equals(child)) throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
                    var actual=all.get(change.characterId()).events().get(Math.toIntExact(change.after().version()));
                    var childCommand=Schema2CombatCharacterFacts.command(roster.get(change.characterId()).material(),request.combatId(),event.operationId(),
                            event.command().actorId()==null?change.characterId():event.command().actorId(),change);
                    if (!actual.operationId().equals(child)||!actual.command().equals(childCommand)||!actual.before().equals(change.before())
                            ||!actual.after().equals(change.after())||!Objects.equals(actual.healthResult(),change.healthResult())) throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
                    versions.put(change.characterId(),change.after().version());
                }
                preceding=versions;
            }
            if (history.current().status()!=Schema2CombatState.Status.ENDED) for (var entry:all.entrySet())
                if (entry.getValue().current().version()!=preceding.get(entry.getKey())) throw rejected("S2_COMBAT_DEPENDENCY_HISTORY");
        } catch (IllegalArgumentException|IndexOutOfBoundsException invalid) { throw new SQLException("S2_COMBAT_HISTORY_CORRUPT",invalid); }
    }
    void reserve(JdbcSchema2CombatRuntimeRepository.History previous,Schema2CombatRules.Prepared prepared,
            Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> histories,Map<UUID,Schema2CombatRules.Context> contexts,Schema2CombatRuntimeService.Request request) {
        try {
            Battle before=prepared.before(); Schema2CombatState.Command command=request.command();
            int suffix=prepared.requiredSuffixEvents(); boolean closing=command.kind()==Kind.CLOSE_SCENE;
            int closeEvents=closing?0:1; // explicit terminal scene close
            if(!closing && (before==null || before.status()==Schema2CombatState.Status.ACTIVE))closeEvents++;
            if(!closing && before!=null) {
                closeEvents+=(int)before.monsterRests().stream().filter(r->r.status()==MonsterRestStatus.ACTIVE).count();
                closeEvents+=(int)before.concentrationHolders().stream().map(ConcentrationHolder::actorId).distinct().count();
                closeEvents+=(int)before.environmentZones().stream().filter(z->z.endedOperationId()==null).count();
                if(before.shields().stream().anyMatch(e->e.status()==EffectStatus.ACTIVE)
                        || before.readyPreparations().stream().anyMatch(r->r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING))closeEvents+=histories.size();
            }
            if(Set.of(Kind.CAST,Kind.READY_SPELL,Kind.CREATE_ENVIRONMENT_ZONE,Kind.BEGIN_MONSTER_REST).contains(command.kind()))closeEvents++;
            if(Set.of(Kind.READY_ATTACK,Kind.READY_MOVE,Kind.READY_SPELL,Kind.DECLARE_SHIELD).contains(command.kind()))closeEvents+=histories.size();
            int remaining=command.kind()==Kind.MOVE?command.path().size():before==null||before.activeFlow()==null?0:
                    before.activeFlow().movements().stream().filter(m->m.status()==MovementStatus.PENDING).mapToInt(m->m.path().size()-m.cursor()).sum();
            JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(previous,before,suffix+closeEvents,before==null?command.deployments().size()+command.monsterDeployments().size():before.combatants().size(),remaining);
            // The mandatory path may pass all optional reactions. Already accepted attacks/casts
            // still need their damage/healing and root exploration-time children before returning.
            Map<UUID,Integer> required=new HashMap<>();for(UUID id:histories.keySet())required.put(id,0);
            if(before!=null && before.activeFlow()!=null) {
                for(var attack:before.activeFlow().attacks())if(attack.status()==AttackStatus.HIT_PENDING_DAMAGE)required.merge(attack.targetId(),1,Integer::sum);
                for(var cast:before.activeFlow().casts())if(cast.status()==CastStatus.PENDING)for(UUID target:cast.targets())required.merge(target,1,Integer::sum);
                if(before.status()==Schema2CombatState.Status.EXPLORATION)for(UUID id:histories.keySet())required.merge(id,1,Integer::sum);
            }
            for(UUID id:prepared.possibleHealthTargets())required.merge(id,2,Integer::sum);
            for(var entry:histories.entrySet()) {
                int closure=closing?0:3+(before==null?0:(int)before.monsterRests().stream().filter(r->r.status()==MonsterRestStatus.ACTIVE).count())+(command.kind()==Kind.BEGIN_MONSTER_REST?1:0); // ending this round and any pending turn-bound facts
                JdbcSchema2CharacterRuntimeRepository.checkCombatCapacity(entry.getValue(),required.get(entry.getKey())+closure);
            }
        } catch (SQLException|IllegalArgumentException exhausted) { throw rejected("S2_COMBAT_CAPACITY"); }
    }

    private static IllegalArgumentException rejected(String code) { return new IllegalArgumentException(code); }
}
