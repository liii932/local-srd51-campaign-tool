package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Snapshot;
import com.dndtool.persistence.JdbcSchema2SnapshotControlRepository;
import com.dndtool.persistence.JdbcSchema2SnapshotControlRepository.Operation;
import com.dndtool.persistence.JdbcSourceSchema2Repository;
import com.dndtool.persistence.Schema2SourceLedger;
import com.dndtool.persistence.BusinessWriteTransactions;
import com.dndtool.persistence.JdbcSchema2InitialPlanRepository;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/**
 * Transfers INTERNAL rule material. No campaign, business write capability or game execution view
 * is created here. Full-base, mechanism, business initialization and Host gates remain separate.
 */
public final class Schema2SnapshotTransferService {
    public static final long MAX_RETAINED_CANONICAL_BYTES = 16L * 1024 * 1024;
    public enum Kind { CAPTURE, RESTORE }
    public enum Status { COMMITTED, ROLLED_BACK, NOT_EXECUTED, UNKNOWN, RESOLVED_COMMITTED, NOT_COMMITTED }
    public record Result(Status status, String code, Intent intent, Snapshot snapshot) { }
    public record Request(UUID operationId, Identity identity, List<Schema2SourceLedger.Identity> selection,
                          Schema2RulesBinding restoreBinding, InitialPlan initialPlan) {
        public Request(UUID operationId, Identity identity, List<Schema2SourceLedger.Identity> selection,
                Schema2RulesBinding restoreBinding) { this(operationId, identity, selection, restoreBinding, null); }
        public Request {
            validateIdentity(operationId, identity);
            if (selection == null || selection.isEmpty() || selection.size() > 4)
                throw new IllegalArgumentException("S2_SELECTION");
            selection = List.copyOf(selection);
            validatePlan(operationId, identity, initialPlan);
        }
    }
    /** Retain this accurate intent when a submission is unknown; resolution never consults source. */
    public record Intent(UUID operationId, Identity identity, Kind kind, Schema2RulesBinding rules, InitialPlan initialPlan) {
        public Intent(UUID operationId, Identity identity, Kind kind, Schema2RulesBinding rules) {
            this(operationId, identity, kind, rules, null);
        }
        public Intent {
            validateIdentity(operationId, identity);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(rules);
            validatePlan(operationId, identity, initialPlan);
            if (initialPlan != null && !rules.equals(initialPlan.character().binding()))
                throw new IllegalArgumentException("S2_INITIAL_PLAN_MISMATCH");
        }
        public String digest() { return fingerprint(this); }
    }

    private final JdbcSourceSchema2Repository source;
    private final DataSource runtime;
    private final JdbcSchema2RuntimeSnapshotRepository snapshots = new JdbcSchema2RuntimeSnapshotRepository();
    private final JdbcSchema2SnapshotControlRepository controls = new JdbcSchema2SnapshotControlRepository();
    private final JdbcSchema2InitialPlanRepository initialPlans = new JdbcSchema2InitialPlanRepository();
    private final Schema2InitialCharacterPlanService planner = new Schema2InitialCharacterPlanService();
    private Intent pending;
    private boolean pendingIsolated;

    public Schema2SnapshotTransferService(DataSource source, DataSource runtime) {
        this.source = new JdbcSourceSchema2Repository(Objects.requireNonNull(source));
        this.runtime = Objects.requireNonNull(runtime);
    }

    public Result transfer(Request request) {
        Objects.requireNonNull(request);
        try (var admission = BusinessWriteTransactions.enter()) {
            ProductionSchema2Projection projection;
            Intent intent;
            try {
                // load returns only after source commit, state restoration and close all succeed.
                var observed = source.load(request.selection());
                projection = ProductionSchema2Projection.compose(observed.artifacts().stream()
                        .map(item -> item.installation().artifact()).toList());
                if (request.restoreBinding() != null) request.restoreBinding().verify(projection);
                if (request.initialPlan() != null) {
                    var submitted = request.initialPlan().character();
                    var rebuilt = planner.prepareMaterial(request.identity(), submitted.binding(), projection,
                            new Schema2InitialProficiencyRules.Request(submitted.sources(), submitted.choices()));
                    if (!rebuilt.equals(submitted)) throw new IllegalArgumentException("S2_INITIAL_PLAN_MISMATCH");
                }
                intent = new Intent(request.operationId(), request.identity(),
                        request.restoreBinding() == null ? Kind.CAPTURE : Kind.RESTORE, Schema2RulesBinding.from(projection), request.initialPlan());
            } catch (SQLException | RuntimeException failure) {
                return result(Status.NOT_EXECUTED, code(failure, "S2_SOURCE_UNAVAILABLE"), null, null);
            }
            return transact(admission, intent, projection, false);
        } catch (SQLException unavailable) {
            return result(Status.NOT_EXECUTED, admissionCode(unavailable), pending, null);
        }
    }

    /** Stores only the typed INTERNAL preparation, with a fresh source review and atomic readback. */
    public Result transferPrepared(Schema2InitializationPreparationService.Prepared preparation) {
        return transfer(Objects.requireNonNull(preparation).transferRequest());
    }

    /**
     * Read-only resolution of this instance's isolated unknown operation. No fresh instance can
     * interpret a missing row as rollback, and resolution never republishes an execution view.
     */
    public Result resolve(Intent intent) {
        Objects.requireNonNull(intent);
        try (var admission = BusinessWriteTransactions.enterResolution()) {
            if (pending == null || !pending.equals(intent) || !pendingIsolated)
                return result(Status.UNKNOWN, "S2_RESOLUTION_REQUIRES_ISOLATION", intent, null);
            return transact(admission, intent, null, true);
        } catch (SQLException unavailable) {
            return result(Status.UNKNOWN, "BWB02".equals(unavailable.getSQLState())
                    ? "S2_RESOLUTION_REQUIRES_ISOLATION" : admissionCode(unavailable), intent, null);
        }
    }

    /** A source-free lookup of an immutable accepted result; it does not grant game execution. */
    public Result inspect(Intent intent) {
        Objects.requireNonNull(intent);
        try (var admission = BusinessWriteTransactions.enterResolution()) {
            return transact(admission, intent, null, true);
        } catch (SQLException unavailable) { return result(Status.UNKNOWN, admissionCode(unavailable), intent, null); }
    }

    private Result transact(BusinessWriteTransactions.Admission admission, Intent intent,
            ProductionSchema2Projection projection, boolean resolution) {
        Connection connection = null;
        BusinessWriteTransactions.Session session = null;
        Snapshot candidate = null;
        Result result;
        try {
            session = resolution ? admission.openResolution(runtime) : admission.openInternal(runtime);
            connection = session.connection();
            var control = controls.lock(connection);
            var records = controls.read(connection, control);
            initialPlans.verifyOperations(connection, records.stream().map(Operation::operationId).collect(java.util.stream.Collectors.toSet()));
            // Read and revalidate all retained immutable material, not only receipt counters/digests.
            List<Snapshot> material = new ArrayList<>(records.size());
            long retainedBytes = 0;
            Operation found = null;
            for (var record : records) {
                var stored = snapshots.read(connection, new Identity(record.runId(), record.snapshotId()));
                var initial = initialPlans.read(connection, record.operationId(), stored);
                var accepted = new Intent(record.operationId(), stored.identity(), Kind.valueOf(record.kind()),
                        Schema2RulesBinding.from(stored.projection()), initial);
                if (!accepted.digest().equals(record.digest())) throw new SQLException("S2_OPERATION_CORRUPT");
                retainedBytes += canonicalBytes(stored.projection());
                if (retainedBytes > MAX_RETAINED_CANONICAL_BYTES) throw new SQLException("S2_RUNTIME_BUDGET");
                material.add(stored);
                if (record.operationId().equals(intent.operationId())) {
                    if (!accepted.equals(intent)) throw new Rejected("S2_OPERATION_CONFLICT");
                    found = record;
                    candidate = stored;
                }
            }
            if (found != null) {
                session.rollback();
                result = result(resolution ? Status.RESOLVED_COMMITTED : Status.COMMITTED,
                        "S2_SNAPSHOT_REPLAY", intent, candidate);
            } else if (resolution) {
                session.rollback();
                // A healthy new reader has no knowledge of the original writer's isolation.
                boolean proven = pendingIsolated && intent.equals(pending);
                result = result(proven ? Status.NOT_COMMITTED : Status.UNKNOWN,
                        proven ? "S2_SNAPSHOT_NOT_COMMITTED" : "S2_SNAPSHOT_OUTCOME_UNPROVEN", intent, null);
            } else {
                if (control.snapshotCount() >= JdbcSchema2SnapshotControlRepository.MAX_SNAPSHOTS)
                    throw new Rejected("S2_RUNTIME_CAPACITY");
                if (retainedBytes + canonicalBytes(Objects.requireNonNull(projection)) > MAX_RETAINED_CANONICAL_BYTES)
                    throw new Rejected("S2_RUNTIME_CAPACITY");
                for (var stored : material) {
                    if (stored.identity().runId().equals(intent.identity().runId())
                            || stored.identity().snapshotId().equals(intent.identity().snapshotId()))
                        throw new Rejected("S2_IDENTITY_CONFLICT");
                }
                candidate = snapshots.store(connection, intent.identity(), Objects.requireNonNull(projection));
                intent.rules().verify(candidate.projection());
                controls.append(connection, control, intent.operationId(), intent.kind().name(), intent.digest(),
                        intent.identity().runId(), intent.identity().snapshotId());
                if (intent.initialPlan() != null) {
                    planner.validate(connection, intent.identity(), intent.rules(), intent.initialPlan().character());
                    initialPlans.store(connection, intent.operationId(), intent.initialPlan());
                }
                var after = controls.read(connection, new JdbcSchema2SnapshotControlRepository.Control(
                        control.rowVersion() + 1, control.snapshotCount() + 1));
                if (after.size() != records.size() + 1 || !after.subList(0, records.size()).equals(records))
                    throw new SQLException("S2_OPERATION_CORRUPT");
                Operation receipt = after.getLast();
                if (!receipt.operationId().equals(intent.operationId()) || !receipt.digest().equals(intent.digest())
                        || !receipt.runId().equals(intent.identity().runId())
                        || !receipt.snapshotId().equals(intent.identity().snapshotId())
                        || !receipt.kind().equals(intent.kind().name())) throw new SQLException("S2_OPERATION_CORRUPT");
                candidate = snapshots.read(connection, intent.identity());
                intent.rules().verify(candidate.projection());
                if (!Objects.equals(intent.initialPlan(), initialPlans.read(connection, intent.operationId(), candidate)))
                    throw new SQLException("S2_INITIAL_PLAN_CORRUPT");
                session.commit();
                result = result(Status.COMMITTED, "S2_SNAPSHOT_COMMITTED", intent, candidate);
            }
        } catch (SQLException | RuntimeException failure) {
            if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.ACTIVE) {
                try { session.rollback(); }
                catch (SQLException | RuntimeException rollback) { suppress(failure, rollback); }
                catch (Error fatal) {
                    suppress(fatal, failure); settleFatal(session, intent, resolution, fatal); throw fatal;
                }
            }
            if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN) {
                if (!resolution) { pending = intent; pendingIsolated = BusinessWriteTransactions.isResolutionAvailable(); }
                result = result(Status.UNKNOWN, "S2_SNAPSHOT_COMMIT_UNKNOWN", intent, null);
            } else {
                if (!(failure instanceof Rejected) && session != null) BusinessWriteTransactions.invalidate();
                result = result(session != null ? Status.ROLLED_BACK : Status.NOT_EXECUTED,
                        failure instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("BWB")
                                ? admissionCode(sql) : code(failure, "S2_RUNTIME_FAILURE"), intent, null);
            }
        } catch (Error failure) {
            settleFatal(session, intent, resolution, failure);
            throw failure;
        }
        if (session != null) {
            try { session.close(); }
            catch (SQLException | RuntimeException cleanup) {
                if (session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN) {
                    if (!resolution) { pending = intent; pendingIsolated = BusinessWriteTransactions.isResolutionAvailable(); }
                    return result(Status.UNKNOWN, pendingIsolated ? "S2_SNAPSHOT_COMMIT_UNKNOWN" : "S2_CONNECTION_ISOLATION_FAILED", intent, null);
                }
                return result(result.status(), "S2_CONNECTION_FAILURE", intent, null);
            }
        }
        if (result.status() == Status.UNKNOWN && !BusinessWriteTransactions.isResolutionAvailable())
            return result(Status.UNKNOWN, "S2_CONNECTION_ISOLATION_FAILED", intent, null);
        if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.COMMITTED && BusinessWriteTransactions.isBlocked())
            return result(Status.COMMITTED, "S2_COMMITTED_CONNECTION_FAILURE", intent, candidate);
        return result;
    }

    /** One bounded cleanup attempt preserves the original fatal failure and the core outcome. */
    private void settleFatal(BusinessWriteTransactions.Session session, Intent intent, boolean resolution, Error failure) {
        BusinessWriteTransactions.invalidate();
        if (session == null) return;
        if (session.outcome() == BusinessWriteTransactions.Outcome.ACTIVE) {
            try { session.rollback(); }
            catch (SQLException | RuntimeException | Error rollback) { suppress(failure, rollback); }
        }
        if (session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN && !resolution) {
            pending = intent; pendingIsolated = BusinessWriteTransactions.isResolutionAvailable();
        }
        try { session.close(); }
        catch (SQLException | RuntimeException | Error cleanup) { suppress(failure, cleanup); }
    }

    private static void suppress(Throwable original, Throwable cleanup) {
        if (original != cleanup) original.addSuppressed(cleanup);
    }

    private static String admissionCode(SQLException failure) {
        if ("BWB01".equals(failure.getSQLState())) return "S2_TRANSFER_BUSY";
        if ("BWB04".equals(failure.getSQLState())) return "S2_RUNTIME_REMAINDER";
        return "S2_TRANSFER_BLOCKED";
    }

    private static void validatePlan(UUID operation, Identity identity, InitialPlan initial) {
        if (initial != null && (!identity.equals(initial.character().identity())
                || operation.equals(initial.campaignId()) || operation.equals(initial.characterId())))
            throw new IllegalArgumentException("S2_INITIAL_IDENTITY");
    }

    private static void validateIdentity(UUID operation, Identity identity) {
        if (operation == null || operation.version() != 4 || operation.variant() != 2 || identity == null
                || operation.equals(identity.runId()) || operation.equals(identity.snapshotId()))
            throw new IllegalArgumentException("S2_OPERATION_IDENTITY");
    }

    private static Result result(Status status, String code, Intent intent, Snapshot snapshot) {
        return new Result(status, code, intent, snapshot);
    }

    private static String code(Exception failure, String fallback) {
        if (failure instanceof com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception schema)
            return schema.diagnostic().code();
        String message = failure.getMessage();
        return message != null && message.matches("S2_[A-Z_]+") ? message : fallback;
    }

    private static String fingerprint(Intent intent) {
        try {
            var buffer = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(buffer)) {
                output.write((intent.initialPlan() == null ? "local-dnd/schema2-snapshot-transfer/1\0"
                        : "local-dnd/schema2-initial-plan-transfer/1\0").getBytes(StandardCharsets.US_ASCII));
                text(output, intent.operationId().toString()); text(output, intent.kind().name());
                text(output, intent.identity().runId().toString()); text(output, intent.identity().snapshotId().toString());
                var rules = intent.rules();
                text(output, rules.canonicalProfile()); text(output, rules.compositionAlgorithm());
                output.writeInt(rules.members().size());
                for (var member : rules.members()) {
                    text(output, member.moduleKey()); text(output, member.releaseVersion()); text(output, member.role().name());
                    text(output, member.rawZipSha256()); text(output, member.logicalSha256());
                }
                text(output, rules.compositionSha256()); text(output, rules.finalRulesSha256()); text(output, rules.chainSha256());
                if (intent.initialPlan() != null) {
                    InitialPlan initial = intent.initialPlan(); var plan = initial.character();
                    text(output, initial.campaignId().toString()); text(output, initial.characterId().toString());
                    output.writeInt(plan.sources().ordered().size());
                    for (var source : plan.sources().ordered()) rule(output, source);
                    output.writeInt(plan.slots().size());
                    for (var fact : plan.slots()) {
                        slot(output, fact.slot()); text(output, fact.grant().category().name()); output.writeBoolean(fact.enabled());
                        if (fact.grant() instanceof FixedGrant fixed) { text(output, "FIXED"); rule(output, fixed.target()); }
                        else { var choice = (ChoiceGrant) fact.grant(); text(output, "CHOICE"); rule(output, choice.candidates()); output.writeInt(choice.count()); }
                    }
                    output.writeInt(plan.choices().size());
                    for (var choice : plan.choices().entrySet()) {
                        slot(output, choice.getKey()); output.writeInt(choice.getValue().size());
                        for (var target : choice.getValue()) rule(output, target);
                    }
                    output.writeInt(plan.grants().size());
                    for (var fact : plan.grants()) {
                        slot(output, fact.slot()); text(output, fact.category().name()); text(output, fact.kind().name());
                        rule(output, fact.target()); output.writeBoolean(fact.suppressed());
                    }
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static long canonicalBytes(ProductionSchema2Projection projection) {
        return projection.canonicalByteLength();
    }

    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(data.length); output.write(data);
    }
    private static void rule(DataOutputStream output, RuleIdentity value) throws IOException {
        text(output, value.moduleKey()); text(output, value.ruleType()); text(output, value.ruleKey());
    }
    private static void slot(DataOutputStream output, EntryIdentity value) throws IOException {
        rule(output, value.rule()); text(output, value.collectionKey()); text(output, value.moduleKey()); text(output, value.entryKey());
    }
    private static final class Rejected extends IllegalArgumentException {
        private Rejected(String code) { super(code); }
    }
}
