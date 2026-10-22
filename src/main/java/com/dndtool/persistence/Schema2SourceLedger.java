package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Shared SELECT-only transport validation. Parsing a ledger grants no installation or execution authority. */
public final class Schema2SourceLedger {
    public static final int MAX_ARTIFACTS = 16;
    public static final long MAX_TOTAL_ZIP_BYTES = 8L * 1024 * 1024;
    public static final long MAX_TOTAL_EXPANDED_BYTES = 8L * 1024 * 1024;
    public static final String PROFILE = ProductionRuleArtifact.CANONICAL_PROFILE;
    private static final String CONTROL_COLUMNS = "control_id, protocol_version, metadata_row_count, installation_count, acceptance_count, raw_zip_byte_count, row_version";
    private static final String INSTALL_COLUMNS = "module_key, release_version, installation_sequence, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, raw_zip_byte_length, expanded_byte_length, OCTET_LENGTH(original_zip), installed_at";
    private static final String ACCEPT_COLUMNS = "module_key, release_version, acceptance_sequence, acceptance_operation_id, operation_fingerprint_version, operation_digest_sha256, installed_operation_id, installed_operation_digest_sha256, installation_sequence, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, review_sha256, accepted_at";

    private Schema2SourceLedger() { }

    /** The caller owns the connection, transaction, consistency level and its settlement. */
    public static State read(Connection connection) throws SQLException {
        Objects.requireNonNull(connection);
        var controls = rows(connection, "SELECT " + CONTROL_COLUMNS + " FROM rule_schema2_control ORDER BY control_id", 1, 7);
        require(controls.size() == 1);
        Control control = control(controls.getFirst());
        var metadata = rows(connection, "SELECT " + INSTALL_COLUMNS + " FROM rule_schema2_artifact ORDER BY installation_sequence", MAX_ARTIFACTS, 16);
        var acceptanceRows = rows(connection, "SELECT " + ACCEPT_COLUMNS + " FROM rule_schema2_acceptance ORDER BY acceptance_sequence", MAX_ARTIFACTS, 17);
        require(control.installations() == metadata.size() && control.acceptances() == acceptanceRows.size());
        // Inspect the entire declared byte budget before opening any BLOB stream.
        long total = 0, expandedTotal = 0;
        for (Object[] row : metadata) {
            long declared = positive(row[12]), expanded = positive(row[13]), actual = positive(row[14]);
            require(declared == actual && declared <= ProductionRuleArtifact.MAX_ZIP_BYTES && expanded <= 1146880);
            total += declared;
            expandedTotal += expanded;
            if (total > MAX_TOTAL_ZIP_BYTES || expandedTotal > MAX_TOTAL_EXPANDED_BYTES) throw failure("S2_SOURCE_BUDGET");
        }
        require(control.zipBytes() == total);
        Map<Identity, Installed> installations = new LinkedHashMap<>();
        Map<UUID, Installed> installationOperations = new HashMap<>();
        long sequence = 0;
        for (Object[] row : metadata) {
            Identity identity = new Identity(binary(row[0]), binary(row[1]));
            long current = positive(row[2]);
            UUID operation = uuid(row[3]);
            require(current == ++sequence && integer(row[4]) == 1 && integer(row[6]) == 2 && integer(row[7]) == 1);
            String operationDigest = digest(row[5]), profile = binary(row[8]), role = binary(row[9]);
            String raw = digest(row[10]), logical = digest(row[11]);
            ProductionRuleArtifact artifact;
            try {
                artifact = ProductionRuleArtifact.read(zip(connection, identity, (int) positive(row[12])));
            } catch (RuntimeException invalid) {
                throw new SourceException("S2_SOURCE_STATE", invalid);
            }
            require(PROFILE.equals(profile) && identity.equals(new Identity(artifact.moduleKey(), artifact.releaseVersion()))
                    && role.equals(artifact.role().name()) && raw.equals(artifact.rawZipSha256())
                    && logical.equals(artifact.logicalSha256()) && profile.equals(artifact.canonicalProfile())
                    && artifact.expandedByteLength() == positive(row[13]));
            Installed fact = new Installed(identity, current, operation, operationDigest, profile, role, raw, logical,
                    positive(row[12]), positive(row[13]), artifact, timestamp(row[15]));
            require(installations.put(identity, fact) == null && installationOperations.put(operation, fact) == null);
        }
        Map<Identity, Accepted> acceptances = new LinkedHashMap<>();
        Map<UUID, Accepted> acceptanceOperations = new HashMap<>();
        sequence = 0;
        for (Object[] row : acceptanceRows) {
            Identity identity = new Identity(binary(row[0]), binary(row[1]));
            UUID operation = uuid(row[3]), installedOperation = uuid(row[6]);
            require(positive(row[2]) == ++sequence && integer(row[4]) == 1 && integer(row[9]) == 2 && integer(row[10]) == 1);
            Accepted fact = new Accepted(identity, sequence, operation, digest(row[5]), installedOperation,
                    digest(row[7]), positive(row[8]), binary(row[11]), binary(row[12]), digest(row[13]),
                    digest(row[14]), digest(row[15]), timestamp(row[16]));
            Installed installed = installations.get(identity);
            require(installed != null && bound(fact, installed) && !installationOperations.containsKey(operation)
                    && acceptances.put(identity, fact) == null && acceptanceOperations.put(operation, fact) == null);
        }
        JdbcSchema2CharacterSourceRepository.verifyAll(connection, installations.values().stream().map(Installed::artifact).toList());
        return new State(control, installations, acceptances, installationOperations, acceptanceOperations);
    }

    private static boolean bound(Accepted fact, Installed installed) {
        return fact.identity().equals(installed.identity()) && fact.installedOperationId().equals(installed.operationId())
                && fact.installedOperationDigest().equals(installed.operationDigest()) && fact.installationSequence() == installed.sequence()
                && fact.canonicalProfile().equals(installed.canonicalProfile()) && fact.role().equals(installed.role())
                && fact.rawZipSha256().equals(installed.rawZipSha256()) && fact.logicalSha256().equals(installed.logicalSha256());
    }

    private static List<Object[]> rows(Connection connection, String sql, int maximum, int columns) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(maximum + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                while (result.next()) {
                    // Observe overflow without materializing any values from its extra row.
                    if (rows.size() == maximum) throw failure(maximum == MAX_ARTIFACTS ? "S2_SOURCE_BUDGET" : "S2_SOURCE_STATE");
                    Object[] row = new Object[columns];
                    for (int index = 0; index < columns; index++) row[index] = result.getObject(index + 1);
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private static byte[] zip(Connection connection, Identity identity, int expectedLength) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT original_zip FROM rule_schema2_artifact WHERE module_key = ? AND release_version = ?")) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(2);
            statement.setBytes(1, identity.moduleKey().getBytes(StandardCharsets.US_ASCII));
            statement.setBytes(2, identity.releaseVersion().getBytes(StandardCharsets.US_ASCII));
            try (ResultSet result = statement.executeQuery()) {
                require(result.next());
                byte[] zip;
                try (var stream = result.getBinaryStream(1)) {
                    require(stream != null);
                    zip = stream.readNBytes(expectedLength + 1);
                } catch (IOException invalid) {
                    throw new SourceException("S2_SOURCE_STATE", invalid);
                }
                require(zip.length == expectedLength && !result.next());
                return zip;
            }
        }
    }

    private static Control control(Object[] row) {
        require(integer(row[0]) == 1 && integer(row[1]) == 1);
        return new Control(integer(row[2]), integer(row[3]), integer(row[4]), integer(row[5]), integer(row[6]));
    }
    private static long integer(Object value) {
        require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long);
        return ((Number) value).longValue();
    }
    private static long positive(Object value) { long number = integer(value); require(number > 0); return number; }
    private static String binary(Object value) {
        require(value instanceof byte[]);
        byte[] bytes = (byte[]) value;
        require(bytes.length > 0 && bytes.length <= 64);
        for (byte item : bytes) require(item >= 0x21 && item <= 0x7e);
        return new String(bytes, StandardCharsets.US_ASCII);
    }
    private static String digest(Object value) { String text = binary(value); require(text.matches("[0-9a-f]{64}")); return text; }
    private static UUID uuid(Object value) {
        require(value instanceof byte[] && ((byte[]) value).length == 16);
        ByteBuffer buffer = ByteBuffer.wrap((byte[]) value);
        UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
        require(uuid.version() == 4 && uuid.variant() == 2);
        return uuid;
    }
    private static LocalDateTime timestamp(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        require(value instanceof LocalDateTime);
        return (LocalDateTime) value;
    }
    private static void require(boolean condition) { if (!condition) throw failure("S2_SOURCE_STATE"); }
    private static SourceException failure(String code) { return new SourceException(code, null); }

    public static final class SourceException extends IllegalStateException {
        private final String code;
        private SourceException(String code, Throwable cause) { super(code, cause); this.code = code; }
        public String code() { return code; }
    }
    public record Identity(String moduleKey, String releaseVersion) {
        public Identity {
            require(moduleKey != null && moduleKey.matches("[a-z][a-z0-9_]{0,63}")
                    && releaseVersion != null && releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"));
        }
    }
    public record Control(long count, long installations, long acceptances, long zipBytes, long version) {
        public Control {
            require(installations >= 0 && installations <= MAX_ARTIFACTS && acceptances >= 0 && acceptances <= installations
                    && count == 1 + installations + acceptances && version == installations + acceptances
                    && zipBytes >= 0 && zipBytes <= MAX_TOTAL_ZIP_BYTES && (installations == 0) == (zipBytes == 0));
        }
    }
    public record Installed(Identity identity, long sequence, UUID operationId, String operationDigest,
            String canonicalProfile, String role, String rawZipSha256, String logicalSha256, long byteLength, long expandedLength,
            ProductionRuleArtifact artifact, LocalDateTime installedAt) { }
    public record Accepted(Identity identity, long sequence, UUID operationId, String operationDigest,
            UUID installedOperationId, String installedOperationDigest, long installationSequence,
            String canonicalProfile, String role, String rawZipSha256, String logicalSha256,
            String reviewSha256, LocalDateTime acceptedAt) { }
    public record State(Control control, Map<Identity, Installed> installations, Map<Identity, Accepted> acceptances,
            Map<UUID, Installed> installationOperations, Map<UUID, Accepted> acceptanceOperations) {
        public State {
            Objects.requireNonNull(control);
            installations = immutable(installations);
            acceptances = immutable(acceptances);
            installationOperations = immutable(installationOperations);
            acceptanceOperations = immutable(acceptanceOperations);
        }
        private static <K, V> Map<K, V> immutable(Map<K, V> source) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
}
