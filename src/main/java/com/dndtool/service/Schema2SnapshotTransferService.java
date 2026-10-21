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
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;

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
                          Schema2RulesBinding restoreBinding) {
        public Request {
            validateIdentity(operationId, identity);
            if (selection == null || selection.isEmpty() || selection.size() > 4)
                throw new IllegalArgumentException("S2_SELECTION");
            selection = List.copyOf(selection);
        }
    }
    /** Retain this accurate intent when a submission is unknown; resolution never consults source. */
    public record Intent(UUID operationId, Identity identity, Kind kind, Schema2RulesBinding rules) {
        public Intent {
            validateIdentity(operationId, identity);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(rules);
        }
        public String digest() { return fingerprint(this); }
    }

    private final JdbcSourceSchema2Repository source;
    private final DataSource runtime;
    private final JdbcSchema2RuntimeSnapshotRepository snapshots = new JdbcSchema2RuntimeSnapshotRepository();
    private final JdbcSchema2SnapshotControlRepository controls = new JdbcSchema2SnapshotControlRepository();
    private final ReentrantLock gate = new ReentrantLock();
    private boolean blocked;
    private Intent pending;
    private boolean pendingIsolated;

    public Schema2SnapshotTransferService(DataSource source, DataSource runtime) {
        this.source = new JdbcSourceSchema2Repository(Objects.requireNonNull(source));
        this.runtime = Objects.requireNonNull(runtime);
    }

    public Result transfer(Request request) {
        Objects.requireNonNull(request);
        if (!gate.tryLock()) return result(Status.NOT_EXECUTED, "S2_TRANSFER_BUSY", null, null);
        try {
            if (blocked) return result(Status.NOT_EXECUTED, "S2_TRANSFER_BLOCKED", pending, null);
            ProductionSchema2Projection projection;
            Intent intent;
            try {
                // load returns only after source commit, state restoration and close all succeed.
                var observed = source.load(request.selection());
                projection = ProductionSchema2Projection.compose(observed.artifacts().stream()
                        .map(item -> item.installation().artifact()).toList());
                if (request.restoreBinding() != null) request.restoreBinding().verify(projection);
                intent = new Intent(request.operationId(), request.identity(),
                        request.restoreBinding() == null ? Kind.CAPTURE : Kind.RESTORE, Schema2RulesBinding.from(projection));
            } catch (SQLException | RuntimeException failure) {
                return result(Status.NOT_EXECUTED, code(failure, "S2_SOURCE_UNAVAILABLE"), null, null);
            }
            return transact(intent, projection, false);
        } finally { gate.unlock(); }
    }

    /**
     * Read-only resolution of this instance's isolated unknown operation. No fresh instance can
     * interpret a missing row as rollback, and resolution never republishes an execution view.
     */
    public Result resolve(Intent intent) {
        Objects.requireNonNull(intent);
        if (!gate.tryLock()) return result(Status.NOT_EXECUTED, "S2_TRANSFER_BUSY", intent, null);
        try {
            if (!blocked || pending == null || !pending.equals(intent) || !pendingIsolated)
                return result(Status.UNKNOWN, "S2_RESOLUTION_REQUIRES_ISOLATION", intent, null);
            return transact(intent, null, true);
        } finally { gate.unlock(); }
    }

    /** A source-free lookup of an immutable accepted result; it does not grant game execution. */
    public Result inspect(Intent intent) {
        Objects.requireNonNull(intent);
        if (!gate.tryLock()) return result(Status.NOT_EXECUTED, "S2_TRANSFER_BUSY", intent, null);
        try {
            if (blocked) return result(Status.UNKNOWN, "S2_TRANSFER_BLOCKED", intent, null);
            return transact(intent, null, true);
        } finally { gate.unlock(); }
    }

    private Result transact(Intent intent, ProductionSchema2Projection projection, boolean resolution) {
        Connection connection = null;
        ConnectionState original = null;
        boolean transactionStarted = false, settled = false, commitAttempted = false, committed = false;
        Snapshot candidate = null;
        Result result;
        try {
            connection = runtime.getConnection();
            original = new ConnectionState(connection.getAutoCommit(), connection.isReadOnly(), connection.getTransactionIsolation());
            if (!original.autoCommit()) throw new SQLException("S2_CONNECTION_ACTIVE");
            settled = true;
            connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            transactionStarted = true;
            settled = false;
            connection.setAutoCommit(false);
            var control = controls.lock(connection);
            var records = controls.read(connection, control);
            // Read and revalidate all retained immutable material, not only receipt counters/digests.
            List<Snapshot> material = new ArrayList<>(records.size());
            long retainedBytes = 0;
            Operation found = null;
            for (var record : records) {
                var stored = snapshots.read(connection, new Identity(record.runId(), record.snapshotId()));
                var accepted = new Intent(record.operationId(), stored.identity(), Kind.valueOf(record.kind()),
                        Schema2RulesBinding.from(stored.projection()));
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
                connection.rollback();
                settled = true;
                result = result(resolution ? Status.RESOLVED_COMMITTED : Status.COMMITTED,
                        "S2_SNAPSHOT_REPLAY", intent, candidate);
            } else if (resolution) {
                connection.rollback();
                settled = true;
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
                commitAttempted = true;
                connection.commit();
                committed = true;
                settled = true;
                result = result(Status.COMMITTED, "S2_SNAPSHOT_COMMITTED", intent, candidate);
            }
        } catch (SQLException | RuntimeException failure) {
            if (!commitAttempted && transactionStarted && connection != null) {
                try { connection.rollback(); settled = true; }
                catch (SQLException | RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            if (commitAttempted || transactionStarted && !settled) {
                blocked = true;
                if (!resolution) { pending = intent; pendingIsolated = false; }
                result = result(Status.UNKNOWN, "S2_SNAPSHOT_COMMIT_UNKNOWN", intent, null);
            } else {
                if (!(failure instanceof Rejected)) blocked = true;
                result = result(transactionStarted ? Status.ROLLED_BACK : Status.NOT_EXECUTED,
                        code(failure, "S2_RUNTIME_FAILURE"), intent, null);
            }
        } catch (Error failure) {
            // Preserve fatal failures while still releasing the caller-owned transaction safely.
            blocked = true;
            if (!commitAttempted && transactionStarted && connection != null) {
                try { connection.rollback(); settled = true; }
                catch (SQLException | RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            if (!settled && !resolution) { pending = intent; pendingIsolated = false; }
            Release released = release(connection, original, settled);
            if (!settled && !resolution && pending != null && pending.equals(intent))
                pendingIsolated = released.isolated();
            throw failure;
        }
        Release released = release(connection, original, settled);
        if (!settled && connection != null && !resolution && pending != null && pending.equals(intent))
            pendingIsolated = released.isolated();
        if (!released.clean()) {
            blocked = true;
            if (committed) return result(Status.COMMITTED, "S2_COMMITTED_CONNECTION_FAILURE", intent, candidate);
            if (result.snapshot() != null)
                return result(result.status(), "S2_ACCEPTED_CONNECTION_FAILURE", intent, null);
            if (released.isolated() && result.status() == Status.UNKNOWN) return result;
            return result(result.status(), released.isolated() ? "S2_CONNECTION_FAILURE" : "S2_CONNECTION_ISOLATION_FAILED", intent, null);
        }
        return result;
    }

    private static Release release(Connection connection, ConnectionState original, boolean settled) {
        if (connection == null) return new Release(true, false);
        boolean safe = false, isolated = false;
        if (settled && original != null && original.autoCommit()) {
            try {
                connection.setTransactionIsolation(original.isolation());
                connection.setReadOnly(original.readOnly());
                connection.setAutoCommit(true);
                safe = true;
            } catch (SQLException | RuntimeException ignored) { /* Quarantine before returning to pool. */ }
        }
        if (!safe) {
            try { connection.abort(Runnable::run); isolated = true; }
            catch (SQLException | RuntimeException ignored) { return new Release(false, false); }
        }
        try { connection.close(); return new Release(safe, isolated); }
        catch (SQLException | RuntimeException ignored) {
            try { connection.abort(Runnable::run); isolated = true; }
            catch (SQLException | RuntimeException ignoredAbort) { /* Remain blocked. */ }
            return new Release(false, isolated);
        }
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
                output.write("local-dnd/schema2-snapshot-transfer/1\0".getBytes(StandardCharsets.US_ASCII));
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

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) { }
    private record Release(boolean clean, boolean isolated) { }
    private static final class Rejected extends IllegalArgumentException {
        private Rejected(String code) { super(code); }
    }
}
