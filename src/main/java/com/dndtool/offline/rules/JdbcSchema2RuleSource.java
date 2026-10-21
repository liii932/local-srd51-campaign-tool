package com.dndtool.offline.rules;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.persistence.Schema2SourceLedger;
import com.dndtool.persistence.JdbcSchema2CharacterSourceRepository;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static com.dndtool.offline.rules.JdbcRuleSource.*;

/** Immutable schema 2 transport ledger. The caller owns this connection and transaction. */
final class JdbcSchema2RuleSource {
    static final int MAX_ARTIFACTS = Schema2SourceLedger.MAX_ARTIFACTS;
    static final long MAX_TOTAL_ZIP_BYTES = Schema2SourceLedger.MAX_TOTAL_ZIP_BYTES;
    static final long MAX_TOTAL_EXPANDED_BYTES = Schema2SourceLedger.MAX_TOTAL_EXPANDED_BYTES;
    static final String PROFILE = Schema2SourceLedger.PROFILE;
    private static final String CONTROL_COLUMNS = "control_id, protocol_version, metadata_row_count, installation_count, acceptance_count, raw_zip_byte_count, row_version";
    private final Connection connection;
    private final JdbcRuleSource jdbc;
    private JdbcRuleSource.Control sharedControl;

    JdbcSchema2RuleSource(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
        this.jdbc = new JdbcRuleSource(connection);
    }

    /** S is shared with schema 1; its counters remain scoped to the original schema 1 ledger. */
    Control lock() throws SQLException {
        sharedControl = jdbc.lock();
        var rows = jdbc.rows("SELECT " + CONTROL_COLUMNS + " FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE", 2);
        if (rows.size() != 1) throw failure("S2_SOURCE_STATE");
        return control(rows.getFirst());
    }

    /** Checks the independently retained legacy checkpoint under S, without claiming a legacy content audit. */
    void validateLegacyCheckpoint(long minimumVersion) {
        if (sharedControl == null || minimumVersion < 0 || sharedControl.version() < minimumVersion)
            throw failure("S2_SOURCE_STATE");
    }

    State read(Control locked, long minimumVersion) throws SQLException {
        if (sharedControl == null || minimumVersion < 0) throw failure("S2_SOURCE_STATE");
        final Schema2SourceLedger.State ledger;
        try {
            ledger = Schema2SourceLedger.read(connection);
        } catch (Schema2SourceLedger.SourceException invalid) {
            throw failure(invalid.code(), invalid);
        }
        var sourceControl = ledger.control();
        var observed = new Control(sourceControl.count(), sourceControl.installations(), sourceControl.acceptances(),
                sourceControl.zipBytes(), sourceControl.version());
        if (!locked.equals(observed) || observed.version() < minimumVersion) throw failure("S2_SOURCE_STATE");
        Map<Identity, Installed> installations = new LinkedHashMap<>();
        Map<UUID, Installed> installationOperations = new HashMap<>();
        for (var source : ledger.installations().values()) {
            // Shared parsing does not grant installability; the offline writer retains its closed gate.
            try { Schema2InstallationEligibility.requireEligible(source.artifact()); }
            catch (RuntimeException invalid) { throw failure("S2_SOURCE_STATE", invalid); }
            var identity = new Identity(source.identity().moduleKey(), source.identity().releaseVersion());
            var fact = new Installed(identity, source.sequence(), source.operationId(), source.operationDigest(),
                    source.canonicalProfile(), source.role(), source.rawZipSha256(), source.logicalSha256(),
                    source.byteLength(), source.expandedLength(), source.artifact(), source.installedAt());
            installations.put(identity, fact);
            installationOperations.put(fact.operationId(), fact);
        }
        Map<Identity, Accepted> acceptances = new LinkedHashMap<>();
        Map<UUID, Accepted> acceptanceOperations = new HashMap<>();
        for (var source : ledger.acceptances().values()) {
            var identity = new Identity(source.identity().moduleKey(), source.identity().releaseVersion());
            var fact = new Accepted(identity, source.sequence(), source.operationId(), source.operationDigest(),
                    source.installedOperationId(), source.installedOperationDigest(), source.installationSequence(),
                    source.canonicalProfile(), source.role(), source.rawZipSha256(), source.logicalSha256(),
                    source.reviewSha256(), source.acceptedAt());
            acceptances.put(identity, fact);
            acceptanceOperations.put(fact.operationId(), fact);
        }
        return new State(observed, Map.copyOf(installations), Map.copyOf(acceptances),
                Map.copyOf(installationOperations), Map.copyOf(acceptanceOperations));
    }

    Installed install(State before, UUID operation, String operationDigest, ProductionRuleArtifact artifact) throws SQLException {
        if (sharedControl == null) throw failure("S2_SOURCE_STATE");
        requireOperation(operation, operationDigest);
        Objects.requireNonNull(artifact);
        var identity = identity(artifact);
        Installed priorOperation = before.installationOperations().get(operation);
        if (priorOperation != null) {
            if (!priorOperation.operationDigest().equals(operationDigest) || !same(priorOperation, artifact))
                throw failure("S2_OPERATION_CONFLICT");
            return priorOperation;
        }
        if (before.acceptanceOperations().containsKey(operation)) throw failure("S2_OPERATION_CONFLICT");
        Installed prior = before.installations().get(identity);
        if (prior != null) {
            if (!same(prior, artifact)) throw failure("S2_VERSION_CONFLICT");
            return prior;
        }
        Control old = before.control();
        long length = artifact.originalZip().length;
        long expanded = expandedLength(artifact);
        long expandedBefore = before.installations().values().stream().mapToLong(Installed::expandedLength).sum();
        if (old.installations() >= MAX_ARTIFACTS || old.zipBytes() + length > MAX_TOTAL_ZIP_BYTES)
            throw failure("S2_SOURCE_BUDGET");
        if (expandedBefore + expanded > MAX_TOTAL_EXPANDED_BYTES) throw failure("S2_SOURCE_BUDGET");
        if (!PROFILE.equals(artifact.canonicalProfile())) throw failure("S2_SOURCE_STATE");
        Schema2InstallationEligibility.requireEligible(artifact);
        long next = old.installations() + 1;
        execute("INSERT INTO rule_schema2_artifact (module_key, release_version, installation_sequence, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, raw_zip_byte_length, expanded_byte_length, original_zip) VALUES (?, ?, ?, ?, 1, ?, 2, 1, ?, ?, ?, ?, ?, ?, ?)",
                ascii(identity.moduleKey()), ascii(identity.releaseVersion()), next, uuidBytes(operation), ascii(operationDigest),
                ascii(artifact.canonicalProfile()), ascii(artifact.role().name()), ascii(artifact.rawZipSha256()), ascii(artifact.logicalSha256()), length, expanded, artifact.originalZip());
        JdbcSchema2CharacterSourceRepository.store(connection, artifact);
        Control expected = new Control(old.count() + 1, next, old.acceptances(), old.zipBytes() + length, old.version() + 1);
        updateControl(old, expected);
        State after = read(expected, 0);
        verifyExisting(before, after);
        Installed installed = after.installationOperations().get(operation);
        if (installed == null || !installed.operationDigest().equals(operationDigest) || !same(installed, artifact)
                || after.installations().size() != before.installations().size() + 1
                || !after.acceptances().equals(before.acceptances())) throw failure("S2_SOURCE_STATE");
        return installed;
    }

    Accepted accept(State before, UUID operation, String operationDigest, Installed expected, String reviewSha256) throws SQLException {
        if (sharedControl == null) throw failure("S2_SOURCE_STATE");
        requireOperation(operation, operationDigest); requireDigest(reviewSha256);
        Objects.requireNonNull(expected);
        Installed installed = before.installations().get(expected.identity());
        if (installed == null || !sameFact(installed, expected)) throw failure("S2_INSTALLATION_MISMATCH");
        Accepted priorOperation = before.acceptanceOperations().get(operation);
        if (priorOperation != null) {
            if (!priorOperation.operationDigest().equals(operationDigest) || !bound(priorOperation, installed)
                    || !priorOperation.reviewSha256().equals(reviewSha256)) throw failure("S2_OPERATION_CONFLICT");
            return priorOperation;
        }
        if (before.installationOperations().containsKey(operation)) throw failure("S2_OPERATION_CONFLICT");
        Accepted prior = before.acceptances().get(installed.identity());
        if (prior != null) {
            if (!prior.reviewSha256().equals(reviewSha256)) throw failure("S2_ACCEPTANCE_CONFLICT");
            return prior;
        }
        Schema2InstallationEligibility.requireEligible(installed.artifact());
        Control old = before.control();
        long next = old.acceptances() + 1;
        execute("INSERT INTO rule_schema2_acceptance (module_key, release_version, acceptance_sequence, acceptance_operation_id, operation_fingerprint_version, operation_digest_sha256, installed_operation_id, installed_operation_digest_sha256, installation_sequence, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, review_sha256) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, 2, 1, ?, ?, ?, ?, ?)",
                ascii(installed.identity().moduleKey()), ascii(installed.identity().releaseVersion()), next, uuidBytes(operation), ascii(operationDigest),
                uuidBytes(installed.operationId()), ascii(installed.operationDigest()), installed.sequence(), ascii(installed.canonicalProfile()),
                ascii(installed.role()), ascii(installed.rawZipSha256()), ascii(installed.logicalSha256()), ascii(reviewSha256));
        Control control = new Control(old.count() + 1, old.installations(), next, old.zipBytes(), old.version() + 1);
        updateControl(old, control);
        State after = read(control, 0);
        verifyExisting(before, after);
        Accepted accepted = after.acceptanceOperations().get(operation);
        if (accepted == null || !accepted.operationDigest().equals(operationDigest) || !bound(accepted, installed)
                || !accepted.reviewSha256().equals(reviewSha256)
                || after.acceptances().size() != before.acceptances().size() + 1
                || after.installations().size() != before.installations().size()) throw failure("S2_SOURCE_STATE");
        return accepted;
    }

    private void updateControl(Control old, Control next) throws SQLException {
        // Set only columns owned by the current capability; a no-op assignment is not permission-free.
        if (next.installations() != old.installations())
            execute("UPDATE rule_schema2_control SET metadata_row_count = ?, installation_count = ?, raw_zip_byte_count = ?, row_version = ? WHERE control_id = 1 AND protocol_version = 1 AND metadata_row_count = ? AND installation_count = ? AND acceptance_count = ? AND raw_zip_byte_count = ? AND row_version = ?",
                    next.count(), next.installations(), next.zipBytes(), next.version(), old.count(), old.installations(), old.acceptances(), old.zipBytes(), old.version());
        else
            execute("UPDATE rule_schema2_control SET metadata_row_count = ?, acceptance_count = ?, row_version = ? WHERE control_id = 1 AND protocol_version = 1 AND metadata_row_count = ? AND installation_count = ? AND acceptance_count = ? AND raw_zip_byte_count = ? AND row_version = ?",
                    next.count(), next.acceptances(), next.version(), old.count(), old.installations(), old.acceptances(), old.zipBytes(), old.version());
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < parameters.length; i++) {
                Object value = parameters[i];
                if (value instanceof byte[] bytes) statement.setBytes(i + 1, bytes);
                else if (value instanceof Long number) statement.setLong(i + 1, number);
                else if (value instanceof Integer number) statement.setInt(i + 1, number);
                else throw failure("S2_SOURCE_STATE");
            }
            if (statement.executeUpdate() != 1) throw failure("S2_SOURCE_STATE");
        }
    }

    private static void verifyExisting(State before, State after) {
        for (var entry : before.installations().entrySet())
            if (!sameFact(entry.getValue(), after.installations().get(entry.getKey()))) throw failure("S2_SOURCE_STATE");
        for (var entry : before.acceptances().entrySet())
            if (!entry.getValue().equals(after.acceptances().get(entry.getKey()))) throw failure("S2_SOURCE_STATE");
    }

    private static boolean sameFact(Installed left, Installed right) {
        return right != null && left.identity().equals(right.identity()) && left.sequence() == right.sequence()
                && left.operationId().equals(right.operationId()) && left.operationDigest().equals(right.operationDigest())
                && left.byteLength() == right.byteLength() && left.expandedLength() == right.expandedLength()
                && left.installedAt().equals(right.installedAt()) && same(left, right.artifact());
    }
    private static boolean same(Installed installed, ProductionRuleArtifact artifact) {
        return installed.identity().equals(identity(artifact)) && installed.canonicalProfile().equals(artifact.canonicalProfile())
                && installed.role().equals(artifact.role().name()) && installed.rawZipSha256().equals(artifact.rawZipSha256())
                && installed.logicalSha256().equals(artifact.logicalSha256())
                && Arrays.equals(installed.artifact().originalZip(), artifact.originalZip());
    }
    private static boolean bound(Accepted fact, Installed installed) {
        return fact.identity().equals(installed.identity()) && fact.installedOperationId().equals(installed.operationId())
                && fact.installedOperationDigest().equals(installed.operationDigest()) && fact.installationSequence() == installed.sequence()
                && fact.canonicalProfile().equals(installed.canonicalProfile()) && fact.role().equals(installed.role())
                && fact.rawZipSha256().equals(installed.rawZipSha256()) && fact.logicalSha256().equals(installed.logicalSha256());
    }
    private static Control control(Object[] row) {
        if (integer(row[0]) != 1 || integer(row[1]) != 1) throw failure("S2_SOURCE_STATE");
        return new Control(integer(row[2]), integer(row[3]), integer(row[4]), integer(row[5]), integer(row[6]));
    }
    private static Identity identity(ProductionRuleArtifact artifact) { return new Identity(artifact.moduleKey(), artifact.releaseVersion()); }
    private static long expandedLength(ProductionRuleArtifact artifact) {
        return artifact.expandedByteLength();
    }
    private static void requireDigest(String text) { if (text == null || !text.matches("[0-9a-f]{64}")) throw failure("S2_SOURCE_STATE"); }
    private static void requireOperation(UUID operation, String digest) {
        if (operation == null || operation.version() != 4 || operation.variant() != 2) throw failure("S2_OPERATION_CONFLICT");
        requireDigest(digest);
    }
    private static byte[] uuidBytes(UUID operation) {
        return ByteBuffer.allocate(16).putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits()).array();
    }
    static SourceException failure(String code) { return new SourceException(code, null); }
    private static SourceException failure(String code, Throwable cause) { return new SourceException(code, cause); }
    static final class SourceException extends IllegalStateException {
        private final String code;
        private SourceException(String code, Throwable cause) { super(code, cause); this.code = code; }
        String code() { return code; }
    }
    record Identity(String moduleKey, String releaseVersion) {
        Identity {
            if (moduleKey == null || !moduleKey.matches("[a-z][a-z0-9_]{0,63}")
                    || releaseVersion == null || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw failure("S2_SOURCE_STATE");
        }
    }
    record Control(long count, long installations, long acceptances, long zipBytes, long version) {
        Control {
            if (installations < 0 || installations > MAX_ARTIFACTS || acceptances < 0 || acceptances > installations
                    || count != 1 + installations + acceptances || version != installations + acceptances
                    || zipBytes < 0 || zipBytes > MAX_TOTAL_ZIP_BYTES || (installations == 0) != (zipBytes == 0))
                throw failure("S2_SOURCE_STATE");
        }
    }
    record Installed(Identity identity, long sequence, UUID operationId, String operationDigest,
            String canonicalProfile, String role, String rawZipSha256, String logicalSha256, long byteLength, long expandedLength,
            ProductionRuleArtifact artifact, LocalDateTime installedAt) { }
    record Accepted(Identity identity, long sequence, UUID operationId, String operationDigest,
            UUID installedOperationId, String installedOperationDigest, long installationSequence,
            String canonicalProfile, String role, String rawZipSha256, String logicalSha256,
            String reviewSha256, LocalDateTime acceptedAt) { }
    record State(Control control, Map<Identity, Installed> installations, Map<Identity, Accepted> acceptances,
            Map<UUID, Installed> installationOperations, Map<UUID, Accepted> acceptanceOperations) { }
}
