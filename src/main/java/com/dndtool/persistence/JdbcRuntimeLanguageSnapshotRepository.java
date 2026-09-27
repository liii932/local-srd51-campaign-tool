package com.dndtool.persistence;

import com.dndtool.module.LanguagePartition;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Isolated DRAFT language storage, not a business initialization or execution capability. */
public final class JdbcRuntimeLanguageSnapshotRepository {
    private static final String SCHEMA = "SELECT CAST(DATABASE() AS BINARY) AS schema_name";
    private static final String REGISTER = """
            INSERT INTO runtime_run_identity (run_id, snapshot_id) VALUES (?, ?)
            """;
    private static final String INSERT_HEAD = """
            INSERT INTO runtime_rule_snapshot (snapshot_id, run_id, module_key, release_version,
                canonical_format_version, archive_format_version, hash_algorithm,
                release_status, material_scope, content_sha256)
            VALUES (?, ?, ?, ?, 2, 2, 'SHA-256', 'DRAFT', 'PARTITION', NULL)
            """;
    private static final String INSERT_LANGUAGE = """
            INSERT INTO runtime_rule_language (snapshot_id, language_key, display_name,
                description, category, source_page, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String READ_HEAD = """
            SELECT s.snapshot_id, s.run_id, s.module_key, s.release_version,
                   s.canonical_format_version, s.archive_format_version, s.hash_algorithm,
                   s.release_status, s.material_scope, s.content_sha256,
                   i.run_id AS registered_run_id, i.snapshot_id AS registered_snapshot_id
            FROM runtime_rule_snapshot s JOIN runtime_run_identity i
                ON i.run_id = s.run_id AND i.snapshot_id = s.snapshot_id
            WHERE s.run_id = ? AND s.snapshot_id = ?
            """;
    private static final String READ_LANGUAGE = """
            SELECT snapshot_id, language_key, display_name, description, category, source_page, sort_order
            FROM runtime_rule_language WHERE snapshot_id = ? ORDER BY language_key
            """;

    /**
     * Caller supplies a fresh server-owned identity and a fully validated language partition.
     * All writes and actual readback share its transaction; on ANY failure the caller must roll
     * back the entire transaction, including subsequent business work. Collisions are failures,
     * never replay. No entry point reuses an existing registration or upgrades a partition.
     */
    public Snapshot append(Connection connection, Identity identity, LanguagePartition partition)
            throws SQLException {
        require(identity != null && partition != null);
        // Recheck the complete immutable value before touching JDBC or consuming write capacity.
        LanguagePartition expected = new LanguagePartition(partition.languages());
        checkConnection(connection);
        insert(connection, REGISTER, statement -> bindIdentity(statement, identity));
        insert(connection, INSERT_HEAD, statement -> {
            statement.setBytes(1, identity.snapshotBytes());
            statement.setBytes(2, identity.runBytes());
            statement.setBytes(3, ascii("dnd5e2014_srd51_se"));
            statement.setBytes(4, ascii("1"));
        });
        for (var row : expected.languages()) {
            insert(connection, INSERT_LANGUAGE, statement -> {
                statement.setBytes(1, identity.snapshotBytes());
                statement.setBytes(2, ascii(row.languageKey()));
                statement.setString(3, row.displayName());
                statement.setString(4, row.description());
                statement.setBytes(5, ascii(row.category().name()));
                statement.setInt(6, row.sourcePage());
                statement.setInt(7, row.sortOrder());
            });
        }
        Snapshot actual = readRows(connection, identity);
        require(expected.equals(actual.partition()));
        return actual;
    }

    /** Server-owned current binding is mandatory. This returns no full catalog or active context. */
    public Snapshot read(Connection connection, Identity currentIdentity) throws SQLException {
        require(currentIdentity != null);
        checkConnection(connection);
        return readRows(connection, currentIdentity);
    }

    private static Snapshot readRows(Connection connection, Identity identity) throws SQLException {
        var heads = query(connection, READ_HEAD, 1, statement -> bindIdentity(statement, identity), result -> {
            require(Arrays.equals(identity.runBytes(), uuidBytes(result, "run_id"))
                    && Arrays.equals(identity.snapshotBytes(), uuidBytes(result, "snapshot_id"))
                    && Arrays.equals(identity.runBytes(), uuidBytes(result, "registered_run_id"))
                    && Arrays.equals(identity.snapshotBytes(), uuidBytes(result, "registered_snapshot_id")));
            exact(result, "module_key", "dnd5e2014_srd51_se");
            exact(result, "release_version", "1");
            require(integer(result, "canonical_format_version") == 2
                    && integer(result, "archive_format_version") == 2);
            exact(result, "hash_algorithm", "SHA-256");
            exact(result, "release_status", "DRAFT");
            exact(result, "material_scope", "PARTITION");
            require(result.getObject("content_sha256") == null);
            return true;
        });
        require(heads.size() == 1);
        var languages = query(connection, READ_LANGUAGE, 18,
                statement -> statement.setBytes(1, identity.snapshotBytes()), result -> {
                    require(Arrays.equals(identity.snapshotBytes(), uuidBytes(result, "snapshot_id")));
                    try {
                        return new LanguagePartition.Language(binaryAscii(result, "language_key", 128),
                                text(result, "display_name", 120), text(result, "description", 1000),
                                LanguagePartition.Category.valueOf(binaryAscii(result, "category", 8)),
                                integer(result, "source_page"), integer(result, "sort_order"));
                    } catch (IllegalArgumentException invalid) {
                        throw invalid();
                    }
                });
        try {
            return new Snapshot(identity, new LanguagePartition(languages));
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        }
    }

    private static void checkConnection(Connection connection) throws SQLException {
        require(connection != null && !connection.getAutoCommit() && !connection.isReadOnly());
        int isolation = connection.getTransactionIsolation();
        require(isolation == Connection.TRANSACTION_READ_COMMITTED
                || isolation == Connection.TRANSACTION_REPEATABLE_READ
                || isolation == Connection.TRANSACTION_SERIALIZABLE);
        var schemas = query(connection, SCHEMA, 1, statement -> {}, result -> {
            exact(result, "schema_name", "dnd_tool_se");
            return true;
        });
        require(schemas.size() == 1);
    }

    private static void bindIdentity(PreparedStatement statement, Identity identity) throws SQLException {
        statement.setBytes(1, identity.runBytes());
        statement.setBytes(2, identity.snapshotBytes());
    }

    private static void insert(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            binder.bind(statement);
            require(statement.executeUpdate() == 1);
        }
    }

    private static <T> List<T> query(Connection connection, String sql, int limit, Binder binder,
                                   Mapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(limit + 1);
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) {
                    require(rows.size() < limit); // Observe the nineteenth row without materializing it.
                    rows.add(mapper.map(result));
                }
                return List.copyOf(rows);
            }
        }
    }

    private static int integer(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long);
        long number = ((Number) value).longValue();
        require(number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE);
        return (int) number;
    }

    private static byte[] binary(ResultSet result, String column) throws SQLException {
        require(result.getObject(column) instanceof byte[]);
        byte[] value = result.getBytes(column);
        require(value != null);
        return value;
    }

    private static byte[] uuidBytes(ResultSet result, String column) throws SQLException {
        byte[] value = binary(result, column);
        try {
            Identity.decode(value);
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        }
        return value;
    }

    private static String binaryAscii(ResultSet result, String column, int maximum) throws SQLException {
        byte[] value = binary(result, column);
        require(value.length > 0 && value.length <= maximum);
        for (byte item : value) require(item >= 0x21 && item <= 0x7e);
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static void exact(ResultSet result, String column, String expected) throws SQLException {
        require(Arrays.equals(ascii(expected), binary(result, column)));
    }

    private static String text(ResultSet result, String column, int maximum) throws SQLException {
        Object raw = result.getObject(column);
        require(raw instanceof String && ((String) raw).length() <= maximum * 2);
        return (String) raw; // Language validates scalar values, NFC, controls and code-point limits.
    }

    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static void require(boolean condition) throws SQLException { if (!condition) throw invalid(); }
    private static SQLException invalid() { return new SQLException("Invalid runtime language partition"); }
    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
    @FunctionalInterface private interface Mapper<T> { T map(ResultSet result) throws SQLException; }

    public record Snapshot(Identity identity, LanguagePartition partition) {}

    /** Technical IDs owned by a server caller, never parsed from a client/archive or used as authority. */
    public record Identity(UUID runId, UUID snapshotId) {
        public Identity {
            if (!valid(runId) || !valid(snapshotId) || runId.equals(snapshotId)) {
                throw new IllegalArgumentException("Invalid runtime identity");
            }
        }

        public static Identity random() {
            UUID run = UUID.randomUUID();
            UUID snapshot;
            do { snapshot = UUID.randomUUID(); } while (snapshot.equals(run));
            return new Identity(run, snapshot);
        }

        public static Identity fromBytes(byte[] run, byte[] snapshot) {
            return new Identity(decode(run), decode(snapshot));
        }

        public byte[] runBytes() { return encode(runId); }
        public byte[] snapshotBytes() { return encode(snapshotId); }

        private static boolean valid(UUID value) {
            return value != null && value.version() == 4 && value.variant() == 2;
        }

        private static UUID decode(byte[] value) {
            if (value == null || value.length != 16) throw new IllegalArgumentException("Invalid runtime UUID");
            ByteBuffer buffer = ByteBuffer.wrap(value);
            UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
            if (!valid(uuid)) throw new IllegalArgumentException("Invalid runtime UUID");
            return uuid;
        }

        private static byte[] encode(UUID value) {
            return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits())
                    .putLong(value.getLeastSignificantBits()).array();
        }
    }
}
