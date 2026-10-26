package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.function.IntUnaryOperator;
import javax.sql.DataSource;

import static com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import static com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import static com.dndtool.module.composition.Schema2CompositionKernel.ProgressionScope;

/**
 * INTERNAL character advancement, resource, rest and check consumer, with atomic state and history.
 * Full campaign initialization and Host routing remain separate adoption boundaries.
 */
public final class Schema2CharacterRuntimeService {
    public enum Status { COMMITTED, REPLAYED, REJECTED, UNKNOWN, NOT_COMMITTED }
    public record Result(Status status, String code, Event event) { }
    public record Request(UUID operationId, Identity identity, UUID campaignId, UUID characterId,
            UUID preparationOperationId, Schema2RulesBinding rules, long expectedVersion,
            Command command, Map<String, Integer> initialAbilities) {
        public Request {
            if (identity == null || rules == null || command == null || initialAbilities == null)
                throw rejected("S2_CHARACTER_REQUEST");
            List<UUID> ids = Arrays.asList(operationId, identity.runId(), identity.snapshotId(), campaignId,
                    characterId, preparationOperationId);
            if (ids.stream().anyMatch(id -> id == null || id.version() != 4 || id.variant() != 2)
                    || new HashSet<>(ids).size() != ids.size()) throw rejected("S2_CHARACTER_IDENTITY");
            if (command.kind().equals("INITIALIZED")) {
                if (expectedVersion != -1 || !initialAbilities.keySet().equals(Set.copyOf(JdbcSchema2CharacterRuntimeRepository.ABILITIES)))
                    throw rejected("S2_CHARACTER_REQUEST");
            } else if (expectedVersion < 0 || !initialAbilities.isEmpty()) throw rejected("S2_CHARACTER_REQUEST");
            if (initialAbilities.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                    || entry.getValue() < 1 || entry.getValue() > 30))
                throw rejected("S2_CHARACTER_REQUEST");
            initialAbilities = Collections.unmodifiableMap(new TreeMap<>(initialAbilities));
            if (command.kind().equals("ITEM_ACQUIRED") && !operationId.equals(command.equipmentInput().itemId()))
                throw rejected("S2_EQUIPMENT_INSTANCE");
        }
        public String digest() { return fingerprint(this); }
    }

    private final DataSource runtime;
    private final IntUnaryOperator dice;
    private final Schema2CharacterProgressionRules rules = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();
    private final Schema2CharacterCheckRules checks = new Schema2CharacterCheckRules();
    private final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
    private final Schema2CharacterHealthRules health = new Schema2CharacterHealthRules();
    private final JdbcSchema2CharacterRuntimeRepository characters = new JdbcSchema2CharacterRuntimeRepository();
    private final JdbcSchema2RuntimeSnapshotRepository snapshots = new JdbcSchema2RuntimeSnapshotRepository();
    private final JdbcSchema2InitialPlanRepository initialPlans = new JdbcSchema2InitialPlanRepository();
    private final JdbcSchema2SnapshotControlRepository controls = new JdbcSchema2SnapshotControlRepository();
    private volatile Pending pending;
    private record Pending(UUID characterId, String digest, History restoration, boolean isolated) { }
    record Material(ProductionSchema2Projection projection, InitialPlan initial) { }

    public Schema2CharacterRuntimeService(DataSource runtime) {
        this(runtime, sides -> new SecureRandom().nextInt(sides) + 1);
    }
    Schema2CharacterRuntimeService(DataSource runtime, IntUnaryOperator dice) {
        this.runtime = Objects.requireNonNull(runtime); this.dice = Objects.requireNonNull(dice);
    }

    /** Every source, version, capacity and rule check precedes randomness and the first insert. */
    public Result execute(Request request) {
        Objects.requireNonNull(request);
        if (JdbcSchema2CharacterRuntimeRepository.combatKind(request.command().kind()))
            return new Result(Status.REJECTED, "S2_CHARACTER_COMBAT_OWNER_REQUIRED", null);
        return transact(false, request.characterId(), request.digest(), null, connection -> {
            Material material = material(connection, request.identity(), request.preparationOperationId());
            request.rules().verify(material.projection());
            if (!request.characterId().equals(material.initial().characterId())
                    || !request.campaignId().equals(material.initial().campaignId())) throw rejected("S2_CHARACTER_BINDING");
            History history = characters.read(connection, request.characterId());
            if (history != null) { validateHistory(material, history); Schema2CombatRuntimeService.validateCharacterReferences(connection, history, runtime); }
            Event previous = characters.findOperation(connection, request.operationId());
            if (previous != null) {
                if (!previous.requestDigest().equals(request.digest()) || history == null
                        || !history.events().contains(previous)) throw rejected("S2_CHARACTER_OPERATION_CONFLICT");
                return new Result(Status.REPLAYED, "S2_CHARACTER_REPLAY", previous);
            }
            Schema2CombatRuntimeService.requireNoActiveCombat(connection, request.campaignId());
            if (request.command().kind().equals("INITIALIZED")) {
                if (history != null) throw rejected("S2_CHARACTER_EXISTS");
                if (characters.findByRun(connection, request.identity().runId()) != null)
                    throw rejected("S2_CHARACTER_RUN_OCCUPIED");
            } else {
                if (history == null) throw rejected("S2_CHARACTER_NOT_FOUND");
                if (!sameBinding(request, history.current())) throw rejected("S2_CHARACTER_BINDING");
                if (history.current().version() != request.expectedVersion()) throw rejected("S2_CHARACTER_STALE_VERSION");
                if (history.events().size() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS)
                    throw rejected("S2_CHARACTER_CAPACITY");
            }
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
            characters.append(connection, event);
            History read = characters.read(connection, request.characterId());
            List<Event> expected = new ArrayList<>(history == null ? List.of() : history.events()); expected.add(event);
            if (read == null || !read.equals(new History(after, expected))) throw new SQLException("S2_CHARACTER_READBACK");
            validateHistory(material, read);
            return new Result(Status.COMMITTED, "S2_CHARACTER_COMMITTED", event);
        });
    }

    /** Source-free inspection of the exact immutable request. It never retries a write. */
    public Result inspect(Request request) {
        Objects.requireNonNull(request);
        return transact(true, request.characterId(), request.digest(), null, connection -> {
            History history = characters.read(connection, request.characterId());
            if (history != null) validateHistory(connection, history);
            Event found = characters.findOperation(connection, request.operationId());
            if (found != null) {
                if (history == null || !history.events().contains(found) || !found.requestDigest().equals(request.digest()))
                    throw rejected("S2_CHARACTER_OPERATION_CONFLICT");
                return new Result(Status.REPLAYED, "S2_CHARACTER_REPLAY", found);
            }
            Pending known = pending;
            boolean proven = known != null && known.isolated() && known.restoration() == null
                    && known.characterId().equals(request.characterId()) && known.digest().equals(request.digest());
            return new Result(proven ? Status.NOT_COMMITTED : Status.UNKNOWN,
                    proven ? "S2_CHARACTER_NOT_COMMITTED" : "S2_CHARACTER_OUTCOME_UNPROVEN", null);
        });
    }

    /** Bounded direct recovery into the same exact prepared snapshot; no random draw or command replay. */
    public Result restore(History history) {
        try { JdbcSchema2CharacterRuntimeRepository.validate(history); }
        catch (SQLException | RuntimeException malformed) { return new Result(Status.REJECTED, "S2_CHARACTER_RESTORE_INVALID", null); }
        State state = history.current(); String digest = history.events().getLast().requestDigest();
        return transact(false, state.characterId(), digest, history, connection -> {
            validateRestoration(connection, history);
            History existing = characters.read(connection, state.characterId());
            if (existing != null) {
                validateHistory(connection, existing);
                if (!existing.equals(history)) throw rejected("S2_CHARACTER_RESTORE_CONFLICT");
                return new Result(Status.REPLAYED, "S2_CHARACTER_RESTORE_REPLAY", history.events().getLast());
            }
            if (characters.findByRun(connection, state.identity().runId()) != null)
                throw rejected("S2_CHARACTER_RUN_OCCUPIED");
            if (history.events().stream().anyMatch(event -> event.command().combatInput() != null))
                throw rejected("S2_CHARACTER_COMBAT_RESTORE_BUNDLE_REQUIRED");
            for (Event event : history.events()) if (characters.findOperation(connection, event.operationId()) != null)
                throw rejected("S2_CHARACTER_OPERATION_CONFLICT");
            Schema2CombatRuntimeService.requireNoActiveCombat(connection, state.campaignId());
            characters.restore(connection, history);
            if (!history.equals(characters.read(connection, state.characterId()))) throw new SQLException("S2_CHARACTER_READBACK");
            return new Result(Status.COMMITTED, "S2_CHARACTER_RESTORED", history.events().getLast());
        });
    }

    public Result inspectRestore(History expected) {
        try { JdbcSchema2CharacterRuntimeRepository.validate(expected); }
        catch (SQLException | RuntimeException malformed) { return new Result(Status.REJECTED, "S2_CHARACTER_RESTORE_INVALID", null); }
        UUID character = expected.current().characterId(); String digest = expected.events().getLast().requestDigest();
        return transact(true, character, digest, expected, connection -> {
            validateRestoration(connection, expected);
            History found = characters.read(connection, character);
            if (found != null) {
                validateHistory(connection, found);
                if (!found.equals(expected)) throw rejected("S2_CHARACTER_RESTORE_CONFLICT");
                return new Result(Status.REPLAYED, "S2_CHARACTER_RESTORE_REPLAY", found.events().getLast());
            }
            Pending known = pending;
            boolean proven = known != null && known.isolated() && expected.equals(known.restoration());
            return new Result(proven ? Status.NOT_COMMITTED : Status.UNKNOWN,
                    proven ? "S2_CHARACTER_NOT_COMMITTED" : "S2_CHARACTER_OUTCOME_UNPROVEN", null);
        });
    }

    /** Current state, complete history and preparation facts remain inspectable after source loss. */
    public History read(UUID characterId) throws SQLException {
        try (var session = BusinessWriteTransactions.openResolution(runtime)) {
            History history = characters.read(session.connection(), characterId);
            if (history != null) validateHistory(session.connection(), history);
            session.rollback(); return history;
        }
    }

    Material material(Connection connection, Identity identity, UUID preparationOperation) throws SQLException {
        var snapshot = snapshots.read(connection, identity);
        if (snapshot == null) throw rejected("S2_CHARACTER_BINDING");
        var initial = initialPlans.read(connection, preparationOperation, snapshot);
        if (initial == null) throw rejected("S2_CHARACTER_BINDING");
        var operations = controls.read(connection, controls.lock(connection));
        var accepted = operations.stream().filter(item -> item.operationId().equals(preparationOperation)).findFirst()
                .orElseThrow(() -> new SQLException("S2_CHARACTER_PREPARATION_CORRUPT"));
        var intent = new Schema2SnapshotTransferService.Intent(preparationOperation, identity,
                Schema2SnapshotTransferService.Kind.valueOf(accepted.kind()), Schema2RulesBinding.from(snapshot.projection()), initial);
        if (!identity.runId().equals(accepted.runId()) || !identity.snapshotId().equals(accepted.snapshotId())
                || !intent.digest().equals(accepted.digest())) throw new SQLException("S2_CHARACTER_PREPARATION_CORRUPT");
        return new Material(snapshot.projection(), initial);
    }

    void validateHistory(Connection connection, History history) throws SQLException {
        State current = history.current();
        validateHistory(material(connection, current.identity(), current.preparationOperationId()), history);
        Schema2CombatRuntimeService.validateCharacterReferences(connection, history, runtime);
    }

    void validateRestoration(Connection connection, History history) throws SQLException {
        State current = history.current();
        Material material = material(connection, current.identity(), current.preparationOperationId());
        try { validateHistory(material, history); }
        catch (SQLException invalidInput) {
            // The retained rules were independently read above. Rejecting submitted history is not
            // evidence of a damaged database or an unknown transaction outcome.
            throw rejected("S2_CHARACTER_RESTORE_INVALID");
        }
    }

    void validateHistory(Material material, History history) throws SQLException {
        try {
            JdbcSchema2CharacterRuntimeRepository.validate(history);
            for (Event event : history.events()) {
                State after = event.after(); rules.validateState(material.projection(), after); rests.validateState(material.projection(), after);
                equipment.validateState(material.projection(), after); health.validateState(after);
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
        Command command = request.command();
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

    private static boolean isAdvancement(Command command) {
        return command.kind().equals("STATISTICS_ADVANCED") || command.kind().equals("CHARACTER_ADVANCED");
    }

    private static void checkCapacity(History history, State candidate, Command command) {
        try { JdbcSchema2CharacterRuntimeRepository.checkCandidateCapacity(history, candidate, command); }
        catch (SQLException exhausted) { throw new IllegalArgumentException("S2_CHARACTER_CAPACITY", exhausted); }
    }

    private Schema2CharacterProgressionRules.PreparedAdvance prepareAdvance(Material material, State before, Command command) {
        var prepared = rules.prepareAdvance(material.projection(), before,
                command.kind().equals("CHARACTER_ADVANCED") ? command.targetClass() : before.characterClass(),
                before.level() + 1, command.hpChoice(), command.growthChoices());
        ProgressionScope required = command.kind().equals("CHARACTER_ADVANCED")
                ? ProgressionScope.CHARACTER_ADVANCEMENT : ProgressionScope.STATISTICS_AND_RESOURCES_ONLY;
        if (prepared.scope() != required) throw rejected("S2_CHARACTER_ADVANCEMENT_SCOPE");
        return prepared;
    }

    private Result transact(boolean inspection, UUID characterId, String digest, History restoration, Work work) {
        try (var admission = inspection ? BusinessWriteTransactions.enterResolution() : BusinessWriteTransactions.enter()) {
            BusinessWriteTransactions.Session session = null;
            Result result;
            try {
                session = inspection ? admission.openResolution(runtime) : admission.openInternal(runtime);
                result = work.run(session.connection());
                if (result.status() == Status.COMMITTED) session.commit(); else session.rollback();
            } catch (SQLException | RuntimeException failure) {
                if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.ACTIVE) {
                    try { session.rollback(); } catch (SQLException | RuntimeException rollback) { suppress(failure, rollback); }
                    catch (Error fatal) {
                        suppress(fatal, failure); fatal(session, fatal);
                        if (session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN && !inspection)
                            pending = new Pending(characterId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                        throw fatal;
                    }
                }
                if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN) {
                    if (!inspection) pending = new Pending(characterId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                    result = new Result(Status.UNKNOWN, "S2_CHARACTER_COMMIT_UNKNOWN", null);
                } else {
                    if (!(failure instanceof IllegalArgumentException) && session != null) BusinessWriteTransactions.invalidate();
                    String code = failure.getMessage();
                    if (code == null || !code.matches("(?:S2_|BW_)[A-Z_]+")) code = "S2_CHARACTER_RUNTIME_FAILURE";
                    result = new Result(inspection && !(failure instanceof IllegalArgumentException) ? Status.UNKNOWN : Status.REJECTED,
                            code, null);
                }
            } catch (Error failure) {
                fatal(session, failure);
                if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN && !inspection)
                    pending = new Pending(characterId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                throw failure;
            }
            if (session != null) {
                try { session.close(); }
                catch (SQLException | RuntimeException cleanup) {
                    if (session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN) {
                        if (!inspection) pending = new Pending(characterId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                        return new Result(Status.UNKNOWN, "S2_CHARACTER_COMMIT_UNKNOWN", null);
                    }
                    return new Result(result.status(), "S2_CHARACTER_CONNECTION_FAILURE", result.event());
                }
            }
            return result;
        } catch (SQLException unavailable) {
            return new Result(inspection ? Status.UNKNOWN : Status.REJECTED, unavailable.getMessage(), null);
        }
    }

    private static void fatal(BusinessWriteTransactions.Session session, Error failure) {
        BusinessWriteTransactions.invalidate();
        if (session == null) return;
        if (session.outcome() == BusinessWriteTransactions.Outcome.ACTIVE) {
            try { session.rollback(); } catch (SQLException | RuntimeException | Error cleanup) { suppress(failure, cleanup); }
        }
        try { session.close(); } catch (SQLException | RuntimeException | Error cleanup) { suppress(failure, cleanup); }
    }
    private static void suppress(Throwable original, Throwable cleanup) { if (original != cleanup) original.addSuppressed(cleanup); }
    private static boolean sameBinding(Request request, State state) {
        return request.identity().equals(state.identity()) && request.campaignId().equals(state.campaignId())
                && request.characterId().equals(state.characterId()) && request.preparationOperationId().equals(state.preparationOperationId());
    }
    private static IllegalArgumentException rejected(String code) { return new IllegalArgumentException(code); }
    @FunctionalInterface private interface Work { Result run(Connection connection) throws SQLException; }

    private static String fingerprint(Request request) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.write("local-dnd/schema2-character-command/1\0".getBytes(StandardCharsets.US_ASCII));
            for (UUID id : List.of(request.operationId(), request.identity().runId(), request.identity().snapshotId(),
                    request.campaignId(), request.characterId(), request.preparationOperationId())) text(out, id.toString());
            Schema2RulesBinding binding = request.rules();
            text(out, binding.canonicalProfile()); text(out, binding.compositionAlgorithm());
            out.writeInt(binding.members().size());
            for (var member : binding.members()) {
                text(out, member.moduleKey()); text(out, member.releaseVersion()); text(out, member.role().name());
                text(out, member.rawZipSha256()); text(out, member.logicalSha256());
            }
            text(out, binding.compositionSha256()); text(out, binding.finalRulesSha256()); text(out, binding.chainSha256());
            out.writeLong(request.expectedVersion()); Command command = request.command();
            text(out, command.kind()); RuleIdentity resource = command.resource(); out.writeBoolean(resource != null);
            if (resource != null) { text(out, resource.moduleKey()); text(out, resource.ruleType()); text(out, resource.ruleKey()); }
            out.writeLong(command.amount()); text(out, Objects.toString(command.recoveryTrigger(), ""));
            text(out, Objects.toString(command.hpChoice(), ""));
            nullableRule(out, command.targetClass());
            GrowthChoices choices = command.growthChoices();
            nullableRule(out, choices.subclass()); nullableRule(out, choices.feat());
            var increases = new TreeMap<>(choices.abilityIncreases()); out.writeInt(increases.size());
            for (var entry : increases.entrySet()) { text(out, entry.getKey()); out.writeInt(entry.getValue()); }
            var grants = new ArrayList<>(choices.proficiencyChoices().entrySet());
            grants.sort(Map.Entry.comparingByKey(ENTRY_ORDER)); out.writeInt(grants.size());
            for (var grant : grants) {
                EntryIdentity slot = grant.getKey(); rule(out, slot.rule()); text(out, slot.collectionKey());
                text(out, slot.moduleKey()); text(out, slot.entryKey());
                var selected = grant.getValue().stream().sorted(RULE_ORDER).toList(); out.writeInt(selected.size());
                for (RuleIdentity target : selected) rule(out, target);
            }
            RestInput rest = command.restInput(); out.writeBoolean(rest != null);
            if (rest != null) {
                text(out, Objects.toString(rest.restKind(), "")); text(out, Objects.toString(rest.activity(), ""));
                out.writeLong(rest.minutes()); out.writeInt(rest.hitDieSides());
                var recovered = new TreeMap<>(rest.recoveredHitDice()); out.writeInt(recovered.size());
                for (var entry : recovered.entrySet()) { out.writeInt(entry.getKey()); out.writeInt(entry.getValue()); }
            }
            // The command discriminant gives this extension an unambiguous position while
            // preserving every earlier command's persisted request bytes and digest.
            CheckInput check = command.checkInput();
            if (check != null) {
                rule(out, check.rule()); text(out, check.kind()); text(out, check.abilityKey());
                nullableRule(out, check.proficiency()); out.writeInt(check.difficultyClass());
                out.writeBoolean(check.advantage()); out.writeBoolean(check.disadvantage()); text(out, check.adjudication());
            }
            EquipmentInput item = command.equipmentInput();
            if (item != null) {
                text(out, item.itemId().toString()); nullableRule(out, item.item());
                text(out, Objects.toString(item.location(), "")); text(out, item.adjudication());
            }
            HealthInput health = command.healthInput();
            if (health != null) {
                rule(out, health.rule()); nullableRule(out, health.effect());
                text(out, Objects.toString(health.temporaryChoice(), "")); text(out, health.adjudication());
            }
            CombatInput combat = command.combatInput();
            if (combat != null) {
                text(out, combat.combatId().toString()); text(out, combat.combatOperationId().toString());
                text(out, combat.frameKey().toString()); text(out, combat.actorId().toString());
                out.writeInt(combat.damageAmount()); out.writeBoolean(combat.critical()); out.writeInt(combat.secondsAdvanced());
            }
            out.writeInt(request.initialAbilities().size());
            for (var entry : request.initialAbilities().entrySet()) { text(out, entry.getKey()); out.writeInt(entry.getValue()); }
            out.flush(); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private static final Comparator<RuleIdentity> RULE_ORDER = Comparator.comparing(RuleIdentity::moduleKey)
            .thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    private static final Comparator<EntryIdentity> ENTRY_ORDER = Comparator.comparing(EntryIdentity::rule, RULE_ORDER)
            .thenComparing(EntryIdentity::collectionKey).thenComparing(EntryIdentity::moduleKey).thenComparing(EntryIdentity::entryKey);
    private static void nullableRule(DataOutputStream out, RuleIdentity identity) throws IOException {
        out.writeBoolean(identity != null); if (identity != null) rule(out, identity);
    }
    private static void rule(DataOutputStream out, RuleIdentity identity) throws IOException {
        text(out, identity.moduleKey()); text(out, identity.ruleType()); text(out, identity.ruleKey());
    }
}
