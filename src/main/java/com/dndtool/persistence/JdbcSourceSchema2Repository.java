package com.dndtool.persistence;

import com.dndtool.persistence.Schema2SourceLedger.Accepted;
import com.dndtool.persistence.Schema2SourceLedger.Control;
import com.dndtool.persistence.Schema2SourceLedger.Identity;
import com.dndtool.persistence.Schema2SourceLedger.Installed;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Reads accepted original schema-2 materials; confers no installation, release or runtime readiness. */
public final class JdbcSourceSchema2Repository {
    private final DataSource dataSource;

    public JdbcSourceSchema2Repository(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    /** Owns one read-only REPEATABLE_READ transaction; the pool separately bounds acquisition/network waits. */
    public Snapshot load(List<Identity> selections) throws SQLException {
        List<Identity> selected = selections(selections);
        Connection connection = dataSource.getConnection();
        ConnectionState original = null;
        Snapshot snapshot = null;
        Throwable failure = null;
        boolean ownedTransaction = false, settled = false, restored = false;
        try {
            original = new ConnectionState(connection.getAutoCommit(), connection.isReadOnly(), connection.getTransactionIsolation());
            if (!original.autoCommit()) throw new SQLException("Source connection has an active transaction");
            settled = true;
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            ownedTransaction = true;
            settled = false;
            connection.setAutoCommit(false);
            snapshot = read(connection, selected);
            connection.commit();
            settled = true;
        } catch (SQLException | RuntimeException | Error problem) {
            failure = problem;
            if (ownedTransaction && !settled) {
                try { connection.rollback(); settled = true; }
                catch (SQLException | RuntimeException | Error rollbackFailure) { failure = combine(failure, rollbackFailure); }
            }
        } finally {
            if (original != null && settled) {
                try {
                    connection.setTransactionIsolation(original.isolation());
                    connection.setReadOnly(original.readOnly());
                    connection.setAutoCommit(original.autoCommit());
                    restored = true;
                } catch (SQLException | RuntimeException | Error restoreFailure) { failure = combine(failure, restoreFailure); }
            }
            // Never pool-close an unresolved transaction: resetting autoCommit could implicitly commit it.
            boolean safeToClose = restored || abort(connection, failure);
            if (safeToClose) {
                try { connection.close(); }
                catch (SQLException | RuntimeException | Error closeFailure) {
                    failure = combine(failure, closeFailure);
                    abort(connection, failure);
                }
            }
        }
        if (failure instanceof SQLException sql) throw sql;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        return snapshot;
    }

    /** Caller-owned variant: never changes, settles or closes the supplied read-only RR transaction. */
    public static Snapshot read(Connection connection, List<Identity> selections) throws SQLException {
        Objects.requireNonNull(connection);
        List<Identity> selected = selections(selections);
        require(!connection.getAutoCommit() && connection.isReadOnly()
                && connection.getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ);
        verifySchema(connection);
        final Schema2SourceLedger.State ledger;
        try { ledger = Schema2SourceLedger.read(connection); }
        catch (Schema2SourceLedger.SourceException invalid) { throw new SQLException("Invalid source schema 2 ledger", invalid); }
        List<AcceptedArtifact> artifacts = new ArrayList<>();
        for (Identity identity : selected) {
            Installed installed = ledger.installations().get(identity);
            Accepted accepted = ledger.acceptances().get(identity);
            require(installed != null && accepted != null);
            artifacts.add(new AcceptedArtifact(installed, accepted));
        }
        return new Snapshot(artifacts, ledger.control());
    }

    private static void verifySchema(Connection connection) throws SQLException {
        List<Object[]> schemas = query(connection, "SELECT CAST(DATABASE() AS BINARY) AS schema_name", 1, 1);
        require(schemas.size() == 1 && exact(RuleSchemaMigrations.DEFAULT_SCHEMA, schemas.getFirst()[0]));
        List<RuleSchemaMigrations.Expectation> expected = RuleSchemaMigrations.expectations();
        require(!expected.isEmpty());
        List<Object[]> ledger = query(connection, "SELECT schema_version, script_name, script_sha256 FROM rule_schema_meta ORDER BY schema_version ASC", expected.size(), 3);
        require(ledger.size() == expected.size());
        for (int index = 0; index < expected.size(); index++) {
            Object[] row = ledger.get(index);
            RuleSchemaMigrations.Expectation approval = expected.get(index);
            require(RuleSchemaMigrations.SCHEMA_ROLE.equals(approval.schemaRole())
                    && integer(row[0]) == approval.version() && exact(approval.scriptName(), row[1])
                    && exact(approval.scriptSha256(), row[2]));
        }
    }

    private static List<Object[]> query(Connection connection, String sql, int maximum, int columns) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(maximum + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                while (result.next()) {
                    require(rows.size() < maximum);
                    Object[] row = new Object[columns];
                    for (int index = 0; index < columns; index++) row[index] = result.getObject(index + 1);
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    private static List<Identity> selections(List<Identity> selections) {
        Objects.requireNonNull(selections);
        if (selections.isEmpty() || selections.size() > Schema2SourceLedger.MAX_ARTIFACTS)
            throw new IllegalArgumentException("Invalid source schema 2 selection");
        List<Identity> selected = List.copyOf(selections);
        if (new HashSet<>(selected).size() != selected.size()) throw new IllegalArgumentException("Duplicate source schema 2 selection");
        return selected;
    }
    private static boolean exact(String expected, Object actual) {
        return actual instanceof byte[] bytes && Arrays.equals(expected.getBytes(StandardCharsets.US_ASCII), bytes);
    }
    private static long integer(Object value) throws SQLException {
        require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long);
        return ((Number) value).longValue();
    }
    private static void require(boolean condition) throws SQLException {
        if (!condition) throw new SQLException("Invalid source schema 2 ledger");
    }
    private static Throwable combine(Throwable primary, Throwable secondary) {
        if (primary == null) return secondary;
        if (primary != secondary) primary.addSuppressed(secondary);
        return primary;
    }
    private static boolean abort(Connection connection, Throwable failure) {
        try { connection.abort(Runnable::run); return true; }
        catch (SQLException | RuntimeException | Error abortFailure) { combine(failure, abortFailure); return false; }
    }

    public record AcceptedArtifact(Installed installation, Accepted acceptance) {
        public AcceptedArtifact { Objects.requireNonNull(installation); Objects.requireNonNull(acceptance); }
    }
    public record Snapshot(List<AcceptedArtifact> artifacts, Control control) {
        public Snapshot { artifacts = List.copyOf(artifacts); Objects.requireNonNull(control); }
    }
    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) { }
}
