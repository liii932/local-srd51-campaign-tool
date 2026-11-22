package com.dndtool.service;

import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.*;
import com.dndtool.service.Schema2CharacterRuntime.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.function.IntUnaryOperator;
import javax.sql.DataSource;

/**
 * INTERNAL character advancement, resource, rest and check consumer, with atomic state and history.
 * Full campaign initialization and Host routing remain separate adoption boundaries.
 */
public final class Schema2CharacterRuntimeService {
    public enum Status { COMMITTED, REPLAYED, REJECTED, UNKNOWN, NOT_COMMITTED }
    public record Result(Status status, String code, Event event) { }

    private final DataSource runtime;
    private final IntUnaryOperator dice;
    private final CampaignDomainEngine domain = new CampaignDomainEngine();
    private final JdbcSchema2CharacterRuntimeRepository characters = new JdbcSchema2CharacterRuntimeRepository();
    private final JdbcSchema2RuntimeSnapshotRepository snapshots = new JdbcSchema2RuntimeSnapshotRepository();
    private final JdbcSchema2InitialPlanRepository initialPlans = new JdbcSchema2InitialPlanRepository();
    private final JdbcSchema2SnapshotControlRepository controls = new JdbcSchema2SnapshotControlRepository();
    private volatile Pending pending;
    private record Pending(UUID characterId, String digest, History restoration, boolean isolated) { }

    public Schema2CharacterRuntimeService(DataSource runtime) {
        this(runtime, sides -> new SecureRandom().nextInt(sides) + 1);
    }
    Schema2CharacterRuntimeService(DataSource runtime, IntUnaryOperator dice) {
        this.runtime = Objects.requireNonNull(runtime); this.dice = Objects.requireNonNull(dice);
    }

    /** Every source, version, capacity and rule check precedes randomness and the first insert. */
    public Result execute(Schema2CharacterRequest request) {
        Objects.requireNonNull(request);
        if (Schema2CharacterRuntime.combatKind(request.command().kind()))
            return new Result(Status.REJECTED, "S2_CHARACTER_COMBAT_OWNER_REQUIRED", null);
        return transact(false, request.characterId(), request.digest(), null, connection -> {
            Schema2CharacterMaterial material = material(connection, request.identity(), request.preparationOperationId());
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
                if (history.events().size() >= Schema2CharacterRuntime.MAX_EVENTS)
                    throw rejected("S2_CHARACTER_CAPACITY");
            }
            Event event = domain.characterEvent(material, history, request, dice);
            State after = event.after();
            characters.append(connection, event);
            History read = characters.read(connection, request.characterId());
            List<Event> expected = new ArrayList<>(history == null ? List.of() : history.events()); expected.add(event);
            if (read == null || !read.equals(new History(after, expected))) throw new SQLException("S2_CHARACTER_READBACK");
            validateHistory(material, read);
            return new Result(Status.COMMITTED, "S2_CHARACTER_COMMITTED", event);
        });
    }

    /** Source-free inspection of the exact immutable request. It never retries a write. */
    public Result inspect(Schema2CharacterRequest request) {
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

    Schema2CharacterMaterial material(Connection connection, Schema2PreparationIdentity identity, UUID preparationOperation) throws SQLException {
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
        return new Schema2CharacterMaterial(snapshot.projection(), initial);
    }

    void validateHistory(Connection connection, History history) throws SQLException {
        State current = history.current();
        validateHistory(material(connection, current.identity(), current.preparationOperationId()), history);
        Schema2CombatRuntimeService.validateCharacterReferences(connection, history, runtime);
    }

    void validateRestoration(Connection connection, History history) throws SQLException {
        State current = history.current();
        Schema2CharacterMaterial material = material(connection, current.identity(), current.preparationOperationId());
        try { validateHistory(material, history); }
        catch (SQLException invalidInput) {
            // The retained rules were independently read above. Rejecting submitted history is not
            // evidence of a damaged database or an unknown transaction outcome.
            throw rejected("S2_CHARACTER_RESTORE_INVALID");
        }
    }

    void validateHistory(Schema2CharacterMaterial material, History history) throws SQLException {
        try { domain.validateCharacterHistory(material, history); }
        catch (Schema2RuntimeFactsException invalid) { throw new SQLException(invalid.getMessage(), invalid); }
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
    private static boolean sameBinding(Schema2CharacterRequest request, State state) {
        return request.identity().equals(state.identity()) && request.campaignId().equals(state.campaignId())
                && request.characterId().equals(state.characterId()) && request.preparationOperationId().equals(state.preparationOperationId());
    }
    private static IllegalArgumentException rejected(String code) { return new IllegalArgumentException(code); }
    @FunctionalInterface private interface Work { Result run(Connection connection) throws SQLException; }
}
