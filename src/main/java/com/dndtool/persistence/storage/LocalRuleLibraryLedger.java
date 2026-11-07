package com.dndtool.persistence.storage;

import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import com.dndtool.service.storage.LocalRuleLibrary.AcceptanceRecord;
import com.dndtool.service.storage.LocalRuleLibrary.ImportRecord;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SQLite statements for the independent library. The caller owns every transaction. */
public final class LocalRuleLibraryLedger {
    private static final int APPLICATION_ID = 0x44524c31;
    private static final int VERSION = 1;
    private static final List<SchemaObject> SCHEMA = List.of(
            new SchemaObject("rule_operation", """
                    CREATE TABLE rule_operation (
                        operation_id TEXT PRIMARY KEY NOT NULL,
                        kind TEXT NOT NULL CHECK (kind IN ('IMPORT', 'ACCEPT')),
                        phase TEXT NOT NULL CHECK (phase IN ('PENDING', 'RETRYABLE', 'COMMITTED')),
                        module_key TEXT NOT NULL,
                        release_version TEXT NOT NULL,
                        role TEXT NOT NULL CHECK (role IN ('BASE', 'EXTENSION', 'COMPATIBILITY')),
                        raw_digest TEXT NOT NULL CHECK (length(raw_digest) = 64),
                        logical_digest TEXT NOT NULL CHECK (length(logical_digest) = 64),
                        canonical_profile TEXT NOT NULL,
                        review_protocol TEXT,
                        accepted_by TEXT,
                        result_operation_id TEXT REFERENCES rule_operation(operation_id),
                        CHECK ((kind = 'IMPORT' AND review_protocol IS NULL AND accepted_by IS NULL)
                            OR (kind = 'ACCEPT' AND review_protocol IS NOT NULL AND accepted_by IS NOT NULL)),
                        CHECK (phase <> 'COMMITTED' OR result_operation_id IS NOT NULL),
                        CHECK (phase <> 'RETRYABLE' OR result_operation_id IS NULL)
                    ) STRICT
                    """),
            new SchemaObject("one_pending_rule_operation", """
                    CREATE UNIQUE INDEX one_pending_rule_operation ON rule_operation(phase) WHERE phase = 'PENDING'
                    """),
            new SchemaObject("one_unregistered_rule_zip", """
                    CREATE UNIQUE INDEX one_unregistered_rule_zip ON rule_operation(raw_digest)
                    WHERE kind = 'IMPORT' AND result_operation_id IS NULL
                    """),
            new SchemaObject("rule_import", """
                    CREATE TABLE rule_import (
                        module_key TEXT NOT NULL,
                        release_version TEXT NOT NULL,
                        role TEXT NOT NULL CHECK (role IN ('BASE', 'EXTENSION', 'COMPATIBILITY')),
                        raw_digest TEXT UNIQUE NOT NULL CHECK (length(raw_digest) = 64),
                        logical_digest TEXT NOT NULL CHECK (length(logical_digest) = 64),
                        canonical_profile TEXT NOT NULL,
                        operation_id TEXT UNIQUE NOT NULL REFERENCES rule_operation(operation_id),
                        PRIMARY KEY (module_key, release_version)
                    ) STRICT
                    """),
            new SchemaObject("rule_acceptance", """
                    CREATE TABLE rule_acceptance (
                        module_key TEXT NOT NULL,
                        release_version TEXT NOT NULL,
                        import_operation_id TEXT NOT NULL REFERENCES rule_import(operation_id),
                        review_protocol TEXT NOT NULL,
                        accepted_by TEXT NOT NULL,
                        operation_id TEXT UNIQUE NOT NULL REFERENCES rule_operation(operation_id),
                        PRIMARY KEY (module_key, release_version),
                        FOREIGN KEY (module_key, release_version) REFERENCES rule_import(module_key, release_version)
                    ) STRICT
                    """),
            new SchemaObject("rule_operation_keep_intent", """
                    CREATE TRIGGER rule_operation_keep_intent
                    BEFORE UPDATE OF operation_id, kind, module_key, release_version, role, raw_digest,
                        logical_digest, canonical_profile, review_protocol, accepted_by ON rule_operation
                    BEGIN SELECT RAISE(ABORT, 'Immutable rule-library intent'); END
                    """),
            immutable("rule_operation", "DELETE"),
            immutable("rule_import", "UPDATE"), immutable("rule_import", "DELETE"),
            immutable("rule_acceptance", "UPDATE"), immutable("rule_acceptance", "DELETE"));

    private record SchemaObject(String name, String sql) { }
    public enum Kind { IMPORT, ACCEPT }
    public enum Phase { PENDING, RETRYABLE, COMMITTED }

    public record Intent(String operationId, Kind kind, StoredMember member, String canonicalProfile,
                         String reviewProtocol, String acceptedBy) { }
    public record Operation(Intent intent, Phase phase, String resultOperationId) { }
    public record Acceptance(AcceptanceRecord record, String importOperationId) { }

    public Connection connect(Path database, boolean readOnly) throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toUri() + "?mode=" + (readOnly ? "ro" : "rw"));
        try {
            execute(connection, "PRAGMA busy_timeout=5000");
            execute(connection, "PRAGMA foreign_keys=ON");
            execute(connection, "PRAGMA recursive_triggers=ON");
            if (!readOnly) execute(connection, "PRAGMA synchronous=EXTRA");
            else execute(connection, "PRAGMA query_only=ON");
            if (!"delete".equalsIgnoreCase(pragma(connection, "journal_mode"))
                    || !"UTF-8".equals(pragma(connection, "encoding"))
                    || !"1".equals(pragma(connection, "foreign_keys"))
                    || (!readOnly && !"3".equals(pragma(connection, "synchronous"))))
                throw new SQLException("Rule-library SQLite settings differ");
            return connection;
        } catch (SQLException | RuntimeException failure) {
            try { connection.close(); }
            catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    public boolean emptySchema(Connection connection) throws SQLException {
        return "0".equals(pragma(connection, "application_id")) && "0".equals(pragma(connection, "user_version"))
                && schema(connection).isEmpty();
    }

    public void initialize(Connection connection) throws SQLException {
        for (var object : SCHEMA) execute(connection, object.sql().strip());
        execute(connection, "PRAGMA application_id=" + APPLICATION_ID);
        execute(connection, "PRAGMA user_version=" + VERSION);
    }

    public void verify(Connection connection) throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        SCHEMA.forEach(object -> expected.put(object.name(), object.sql().strip()));
        if (!Integer.toString(APPLICATION_ID).equals(pragma(connection, "application_id"))
                || !Integer.toString(VERSION).equals(pragma(connection, "user_version"))
                || !expected.equals(schema(connection))) throw new SQLException("Rule-library ledger schema differs");
    }

    public void begin(Connection connection) throws SQLException { execute(connection, "BEGIN IMMEDIATE"); }
    public void beginRead(Connection connection) throws SQLException { execute(connection, "BEGIN"); }
    public void commit(Connection connection) throws SQLException { execute(connection, "COMMIT"); }
    public void rollback(Connection connection) throws SQLException { execute(connection, "ROLLBACK"); }

    public Operation operation(Connection connection, String id) throws SQLException {
        try (var statement = prepare(connection, "SELECT * FROM rule_operation WHERE operation_id = ?", id);
             var rows = statement.executeQuery()) {
            return rows.next() ? operation(rows) : null;
        }
    }

    public Operation pending(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM rule_operation WHERE phase = 'PENDING'");
             var rows = statement.executeQuery()) {
            return rows.next() ? operation(rows) : null;
        }
    }

    public Operation unregisteredZip(Connection connection, String rawDigest) throws SQLException {
        try (var statement = prepare(connection, """
                SELECT * FROM rule_operation WHERE raw_digest=? AND kind='IMPORT' AND result_operation_id IS NULL
                """, rawDigest); var rows = statement.executeQuery()) {
            return rows.next() ? operation(rows) : null;
        }
    }

    public void claim(Connection connection, Intent intent) throws SQLException {
        StoredMember member = intent.member();
        change(connection, """
                INSERT INTO rule_operation(operation_id, kind, phase, module_key, release_version, role, raw_digest,
                    logical_digest, canonical_profile, review_protocol, accepted_by)
                VALUES (?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?)
                """, intent.operationId(), intent.kind().name(), member.moduleKey(), member.releaseVersion(),
                member.role().name(), member.rawZipSha256(), member.logicalSha256(), intent.canonicalProfile(),
                intent.reviewProtocol(), intent.acceptedBy());
    }

    public void retry(Connection connection, String id) throws SQLException {
        change(connection, "UPDATE rule_operation SET phase='PENDING' WHERE operation_id=? AND phase='RETRYABLE'", id);
    }

    public void recordResult(Connection connection, String id, String origin) throws SQLException {
        change(connection, """
                UPDATE rule_operation SET result_operation_id=?
                WHERE operation_id=? AND phase='PENDING' AND result_operation_id IS NULL
                """, origin, id);
    }

    public void resolve(Connection connection, String id, Phase phase) throws SQLException {
        if (phase == Phase.PENDING) throw new IllegalArgumentException("Cannot resolve a pending phase");
        change(connection, "UPDATE rule_operation SET phase=? WHERE operation_id=? AND phase='PENDING'",
                phase.name(), id);
    }

    public ImportRecord imported(Connection connection, String key, String version) throws SQLException {
        try (var statement = prepare(connection, "SELECT * FROM rule_import WHERE module_key=? AND release_version=?", key, version);
             var rows = statement.executeQuery()) {
            return rows.next() ? imported(rows) : null;
        }
    }

    public List<ImportRecord> imports(Connection connection) throws SQLException {
        List<ImportRecord> result = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT * FROM rule_import ORDER BY module_key, release_version");
             var rows = statement.executeQuery()) {
            while (rows.next()) result.add(imported(rows));
        }
        return List.copyOf(result);
    }

    public void register(Connection connection, ImportRecord row) throws SQLException {
        StoredMember member = row.member();
        change(connection, """
                INSERT INTO rule_import(module_key, release_version, role, raw_digest, logical_digest,
                    canonical_profile, operation_id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, member.moduleKey(), member.releaseVersion(), member.role().name(), member.rawZipSha256(),
                member.logicalSha256(), row.canonicalProfile(), row.operationId());
    }

    public Acceptance acceptance(Connection connection, StoredMember member) throws SQLException {
        try (var statement = prepare(connection, "SELECT * FROM rule_acceptance WHERE module_key=? AND release_version=?",
                member.moduleKey(), member.releaseVersion()); var rows = statement.executeQuery()) {
            if (!rows.next()) return null;
            return new Acceptance(new AcceptanceRecord(member, rows.getString("review_protocol"),
                    rows.getString("accepted_by"), rows.getString("operation_id")), rows.getString("import_operation_id"));
        }
    }

    public void accept(Connection connection, AcceptanceRecord row, String importOperation) throws SQLException {
        change(connection, """
                INSERT INTO rule_acceptance(module_key, release_version, import_operation_id,
                    review_protocol, accepted_by, operation_id) VALUES (?, ?, ?, ?, ?, ?)
                """, row.member().moduleKey(), row.member().releaseVersion(), importOperation,
                row.reviewProtocol(), row.acceptedBy(), row.operationId());
    }

    private static SchemaObject immutable(String table, String action) {
        String name = table + "_no_" + action.toLowerCase(java.util.Locale.ROOT);
        return new SchemaObject(name, "CREATE TRIGGER " + name + " BEFORE " + action + " ON " + table
                + " BEGIN SELECT RAISE(ABORT, 'Immutable rule-library fact'); END");
    }

    private static Map<String, String> schema(Connection connection) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("SELECT name, sql FROM sqlite_schema WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'");
             var rows = statement.executeQuery()) {
            while (rows.next()) result.put(rows.getString(1), rows.getString(2));
        }
        return result;
    }

    private static String pragma(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            if (!result.next()) throw new SQLException("Missing rule-library PRAGMA " + name);
            return result.getString(1);
        }
    }

    private static Operation operation(ResultSet row) throws SQLException {
        return new Operation(new Intent(row.getString("operation_id"), Kind.valueOf(row.getString("kind")), member(row),
                row.getString("canonical_profile"), row.getString("review_protocol"), row.getString("accepted_by")),
                Phase.valueOf(row.getString("phase")), row.getString("result_operation_id"));
    }

    private static ImportRecord imported(ResultSet row) throws SQLException {
        return new ImportRecord(member(row), row.getString("canonical_profile"), row.getString("operation_id"));
    }

    private static StoredMember member(ResultSet row) throws SQLException {
        return new StoredMember(row.getString("module_key"), row.getString("release_version"), Role.valueOf(row.getString("role")),
                row.getString("raw_digest"), row.getString("logical_digest"));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static void change(Connection connection, String sql, String... parameters) throws SQLException {
        try (var statement = prepare(connection, sql, parameters)) {
            if (statement.executeUpdate() != 1) throw new SQLException("Expected one rule-library ledger row");
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, String... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < parameters.length; i++) statement.setString(i + 1, parameters[i]);
            return statement;
        } catch (SQLException failure) {
            try { statement.close(); }
            catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }
}
