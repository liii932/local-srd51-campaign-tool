package com.dndtool.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Permanent guard and immutable receipts for INTERNAL rule material, in a caller-owned transaction. */
public final class JdbcSchema2SnapshotControlRepository {
    public static final int MAX_SNAPSHOTS = 16;
    private static final String CONTROL = "control_id, protocol_version, row_version, snapshot_count";

    /** Locks material accounting after the shared business guard; never waits for a process gate here. */
    public Control lock(Connection connection) throws SQLException {
        transaction(connection);
        try (PreparedStatement statement = statement(connection,
                "SELECT " + CONTROL + " FROM s2_runtime_control WHERE control_id = 1 FOR UPDATE", 2);
                ResultSet row = statement.executeQuery()) {
            require(row.next());
            Control result = control(row);
            require(!row.next());
            return result;
        }
    }

    public List<Operation> read(Connection connection, Control locked) throws SQLException {
        transaction(connection);
        schema(connection);
        try (PreparedStatement statement = statement(connection,
                "SELECT " + CONTROL + " FROM s2_runtime_control ORDER BY control_id", 2);
                ResultSet row = statement.executeQuery()) {
            require(row.next() && locked.equals(control(row)) && !row.next());
        }
        List<Operation> operations = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        Set<Pair> pairs = new HashSet<>();
        Set<UUID> runs = new HashSet<>(), snapshots = new HashSet<>();
        try (PreparedStatement statement = statement(connection, "SELECT operation_id, operation_kind, "
                + "operation_digest_sha256, run_id, snapshot_id, operation_sequence, created_at "
                + "FROM s2_runtime_operation ORDER BY operation_sequence", MAX_SNAPSHOTS + 1);
                ResultSet row = statement.executeQuery()) {
            while (row.next()) {
                require(operations.size() < MAX_SNAPSHOTS);
                UUID operationId = uuid(row, "operation_id"), runId = uuid(row, "run_id"), snapshotId = uuid(row, "snapshot_id");
                String kind = ascii(row, "operation_kind", 7), digest = ascii(row, "operation_digest_sha256", 64);
                require(Set.of("CAPTURE", "RESTORE").contains(kind) && digest.matches("[0-9a-f]{64}"));
                require(!runId.equals(snapshotId) && !operationId.equals(runId) && !operationId.equals(snapshotId)
                        && ids.add(operationId) && runs.add(runId) && snapshots.add(snapshotId)
                        && pairs.add(new Pair(runId, snapshotId)));
                long sequence = number(row, "operation_sequence");
                require(sequence == operations.size() + 1L);
                LocalDateTime created = row.getObject("created_at", LocalDateTime.class);
                require(created != null);
                operations.add(new Operation(operationId, kind, digest, runId, snapshotId, sequence, created));
            }
        }
        require(operations.size() == locked.snapshotCount());
        require(pairs.equals(pairs(connection, "s2_runtime_identity"))
                && pairs.equals(pairs(connection, "s2_runtime_snapshot")));
        return List.copyOf(operations);
    }

    /** Snapshot rows must already have been appended in this same transaction. */
    public void append(Connection connection, Control old, UUID operationId, String kind, String digest,
            UUID runId, UUID snapshotId) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection);
        transaction(connection);
        require(old.snapshotCount() < MAX_SNAPSHOTS && operationId != null && runId != null && snapshotId != null
                && Set.of("CAPTURE", "RESTORE").contains(kind) && digest != null && digest.matches("[0-9a-f]{64}"));
        try (PreparedStatement statement = statement(connection, "INSERT INTO s2_runtime_operation "
                + "(operation_id, operation_kind, operation_digest_sha256, run_id, snapshot_id, operation_sequence) "
                + "VALUES (?, ?, ?, ?, ?, ?)", 0)) {
            statement.setBytes(1, bytes(operationId)); statement.setBytes(2, kind.getBytes(StandardCharsets.US_ASCII));
            statement.setBytes(3, digest.getBytes(StandardCharsets.US_ASCII)); statement.setBytes(4, bytes(runId));
            statement.setBytes(5, bytes(snapshotId)); statement.setLong(6, old.rowVersion() + 1);
            require(statement.executeUpdate() == 1);
        }
        try (PreparedStatement statement = statement(connection, "UPDATE s2_runtime_control "
                + "SET row_version = ?, snapshot_count = ? WHERE control_id = 1 AND row_version = ? AND snapshot_count = ?", 0)) {
            statement.setLong(1, old.rowVersion() + 1); statement.setInt(2, old.snapshotCount() + 1);
            statement.setLong(3, old.rowVersion()); statement.setInt(4, old.snapshotCount());
            require(statement.executeUpdate() == 1);
        }
    }

    private static Set<Pair> pairs(Connection connection, String table) throws SQLException {
        Set<Pair> result = new HashSet<>();
        try (PreparedStatement statement = statement(connection,
                "SELECT run_id, snapshot_id FROM " + table + " ORDER BY run_id", MAX_SNAPSHOTS + 1);
                ResultSet row = statement.executeQuery()) {
            while (row.next()) {
                require(result.size() < MAX_SNAPSHOTS && result.add(new Pair(uuid(row, "run_id"), uuid(row, "snapshot_id"))));
            }
        }
        return result;
    }

    private static void schema(Connection connection) throws SQLException {
        try (PreparedStatement statement = statement(connection, "SELECT CAST(DATABASE() AS BINARY) AS schema_name", 2);
                ResultSet row = statement.executeQuery()) {
            require(row.next() && "dnd_tool_se".equals(ascii(row, "schema_name", 64)) && !row.next());
        }
        List<SchemaMigrations.Expectation> expected;
        try { expected = SchemaMigrations.loadExpectations(); }
        catch (SchemaMigrations.PackagedSchemaException failure) { throw new SQLException("S2_RUNTIME_SCHEMA", failure); }
        try (PreparedStatement statement = statement(connection,
                "SELECT schema_version, script_name, script_sha256 FROM schema_meta ORDER BY schema_version ASC", expected.size() + 1);
                ResultSet row = statement.executeQuery()) {
            for (var item : expected) {
                require(row.next() && number(row, "schema_version") == item.version()
                        && item.scriptName().equals(row.getObject("script_name"))
                        && item.scriptSha256().equals(row.getObject("script_sha256")));
            }
            require(!row.next());
        }
    }

    private static Control control(ResultSet row) throws SQLException {
        require(number(row, "control_id") == 1 && number(row, "protocol_version") == 1);
        long version = number(row, "row_version"), count = number(row, "snapshot_count");
        require(count >= 0 && count <= MAX_SNAPSHOTS && version == count);
        return new Control(version, (int) count);
    }

    private static PreparedStatement statement(Connection connection, String sql, int maxRows) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.setQueryTimeout(5);
            if (maxRows > 0) statement.setMaxRows(maxRows);
            return statement;
        } catch (SQLException | RuntimeException failure) {
            try { statement.close(); } catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    private static void transaction(Connection connection) throws SQLException {
        require(connection != null && !connection.getAutoCommit() && !connection.isReadOnly()
                && connection.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED);
    }

    private static String ascii(ResultSet row, String key, int maximum) throws SQLException {
        Object value = row.getObject(key);
        require(value instanceof byte[]);
        byte[] data = (byte[]) value;
        require(data.length > 0 && data.length <= maximum);
        for (byte item : data) require(item >= 0x21 && item <= 0x7e);
        return new String(data, StandardCharsets.US_ASCII);
    }

    private static long number(ResultSet row, String key) throws SQLException {
        Object value = row.getObject(key);
        require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long);
        return ((Number) value).longValue();
    }

    private static UUID uuid(ResultSet row, String key) throws SQLException {
        Object value = row.getObject(key);
        require(value instanceof byte[] data && data.length == 16);
        ByteBuffer buffer = ByteBuffer.wrap((byte[]) value);
        UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
        require(uuid.version() == 4 && uuid.variant() == 2);
        return uuid;
    }

    private static byte[] bytes(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
    }

    private static void require(boolean valid) throws SQLException {
        if (!valid) throw new SQLException("S2_RUNTIME_STATE");
    }

    public record Control(long rowVersion, int snapshotCount) { }
    public record Operation(UUID operationId, String kind, String digest, UUID runId, UUID snapshotId,
                            long sequence, LocalDateTime createdAt) { }
    private record Pair(UUID runId, UUID snapshotId) { }
}
