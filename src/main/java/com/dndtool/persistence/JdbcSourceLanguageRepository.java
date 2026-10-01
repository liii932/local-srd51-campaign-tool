package com.dndtool.persistence;

import com.dndtool.module.LanguagePartition;
import com.dndtool.module.ToolPartition;
import com.dndtool.module.CharacterCatalogPartition;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Reads the supported DRAFT language partition only; provides no release or runtime readiness. */
public final class JdbcSourceLanguageRepository {
    private static final String MODULE_KEY = "dnd5e2014_srd51_se";
    private static final String RELEASE_VERSION = "1";
    private static final String SCHEMA_SQL = "SELECT CAST(DATABASE() AS BINARY) AS schema_name";
    private static final String LEDGER_SQL = """
            SELECT schema_version, script_name, script_sha256
            FROM rule_schema_meta ORDER BY schema_version ASC
            """;
    private static final String RELEASE_SQL = """
            SELECT id, module_key, release_version, canonical_format_version,
                   archive_format_version, hash_algorithm, content_sha256,
                   release_status, installation_revision, released_at
            FROM rule_release WHERE module_key = ? AND release_version = ?
            """;
    private static final String INSTALLATION_SQL = """
            SELECT release_id, installation_revision, source_operation_id,
                   operation_fingerprint_version, operation_digest_sha256,
                   author_schema_version, installation_manifest_version,
                   installation_manifest_sha256, package_display_name,
                   verification_scope, observed_content_sha256
            FROM rule_package_installation
            WHERE release_id = ? AND installation_revision = ?
            """;
    private static final String PARTITION_SQL = """
            SELECT release_id, installation_revision, partition_key
            FROM rule_package_installation_partition
            WHERE release_id = ? AND installation_revision = ? ORDER BY partition_key
            """;
    private static final String LANGUAGE_SQL = """
            SELECT release_id, language_key, display_name, description, category, source_page, sort_order
            FROM rule_language WHERE release_id = ? ORDER BY language_key
            """;
    private static final String TOOL_SQL = """
            SELECT release_id, tool_key, display_name, description, category, source_page, sort_order
            FROM rule_tool WHERE release_id = ? ORDER BY tool_key
            """;

    private final DataSource dataSource;

    public JdbcSourceLanguageRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    /**
     * Owns one connection and one REPEATABLE_READ read-only transaction, including the rules ledger.
     * Missing or unsupported source evidence fails closed. No source identity escapes the result.
     * The supplied pool must bound acquisition and driver network waits independently of query timeout.
     */
    public LanguagePartition load() throws SQLException {
        return loadCatalog().languages();
    }

    /** Both supported domains are read from the same fixed source transaction. */
    public CharacterCatalogPartition loadCatalog() throws SQLException {
        Connection connection = dataSource.getConnection();
        State original = null;
        CharacterCatalogPartition partition = null;
        Throwable failure = null;
        boolean ownedTransaction = false;
        boolean settled = false;
        boolean restored = false;
        try {
            original = new State(connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
            // Never take over a transaction that might already belong to another borrower.
            if (!original.autoCommit()) throw new SQLException("Source connection has an active transaction");
            settled = true;
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            ownedTransaction = true;
            settled = false; // A failed setter may nevertheless have changed the server state.
            connection.setAutoCommit(false);
            partition = read(connection);
            connection.commit();
            settled = true;
        } catch (SQLException | RuntimeException | Error problem) {
            failure = problem;
            if (ownedTransaction && !settled) {
                try {
                    connection.rollback();
                    settled = true;
                } catch (SQLException | RuntimeException | Error rollbackFailure) {
                    failure = combine(failure, rollbackFailure);
                }
            }
        } finally {
            if (original != null && settled) {
                try {
                    connection.setTransactionIsolation(original.isolation());
                    connection.setReadOnly(original.readOnly());
                    // Only a confirmed commit/rollback permits a possible implicit commit here.
                    connection.setAutoCommit(original.autoCommit());
                    restored = true;
                } catch (SQLException | RuntimeException | Error restoreFailure) {
                    failure = combine(failure, restoreFailure);
                }
            }
            // If termination fails, deliberately leave the unsafe handle checked out. A pooled
            // close could reset autoCommit and implicitly commit; pool recovery belongs to its owner.
            boolean safeToClose = restored || abort(connection, failure);
            if (safeToClose) {
                try {
                    connection.close();
                } catch (SQLException | RuntimeException | Error closeFailure) {
                    failure = combine(failure, closeFailure);
                    abort(connection, failure);
                }
            }
        }
        if (failure instanceof SQLException sql) throw sql;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        return partition;
    }

    private static CharacterCatalogPartition read(Connection connection) throws SQLException {
        List<byte[]> schemas = query(connection, SCHEMA_SQL, 1, statement -> {},
                result -> binary(result, "schema_name"));
        require(schemas.size() == 1 && asciiEquals(RuleSchemaMigrations.DEFAULT_SCHEMA, schemas.get(0)));
        List<RuleSchemaMigrations.Expectation> expected = RuleSchemaMigrations.expectations();
        require(!expected.isEmpty());
        List<LedgerRow> ledger = query(connection, LEDGER_SQL, expected.size(), statement -> {},
                result -> new LedgerRow(integer(result, "schema_version", 1, Integer.MAX_VALUE),
                        binary(result, "script_name"), binary(result, "script_sha256")));
        require(ledger.size() == expected.size());
        for (int index = 0; index < expected.size(); index++) {
            var approval = expected.get(index);
            var row = ledger.get(index);
            require(RuleSchemaMigrations.SCHEMA_ROLE.equals(approval.schemaRole())
                    && row.version() == approval.version()
                    && asciiEquals(approval.scriptName(), row.scriptName())
                    && asciiEquals(approval.scriptSha256(), row.scriptSha256()));
        }
        List<ReleaseRow> releases = query(connection, RELEASE_SQL, 1, statement -> {
            statement.setBytes(1, MODULE_KEY.getBytes(StandardCharsets.US_ASCII));
            statement.setBytes(2, RELEASE_VERSION.getBytes(StandardCharsets.US_ASCII));
        }, result -> {
            exact(result, "module_key", MODULE_KEY);
            exact(result, "release_version", RELEASE_VERSION);
            require(integer(result, "canonical_format_version", 1, Integer.MAX_VALUE) == 2);
            require(integer(result, "archive_format_version", 1, Integer.MAX_VALUE) == 2);
            exact(result, "hash_algorithm", "SHA-256");
            exact(result, "release_status", "DRAFT");
            require(result.getObject("content_sha256") == null && result.getObject("released_at") == null);
            return new ReleaseRow(integer(result, "id", 1, Long.MAX_VALUE),
                    integer(result, "installation_revision", 1, Long.MAX_VALUE));
        });
        require(releases.size() == 1);
        ReleaseRow release = releases.get(0);
        // Only current evidence is relevant. In particular, historical COMPLETE digests are not
        // compared with a later PARTITION head and cannot qualify the current content (L23).
        List<Boolean> installations = query(connection, INSTALLATION_SQL, 1,
                statement -> bindInstallation(statement, release), result -> {
                    association(result, release);
                    byte[] operation = binary(result, "source_operation_id");
                    require(operation.length == 16 && (operation[6] & 0xf0) == 0x40
                            && (operation[8] & 0xc0) == 0x80);
                    require(integer(result, "operation_fingerprint_version", 1, Integer.MAX_VALUE) == 1);
                    require(integer(result, "author_schema_version", 1, Integer.MAX_VALUE) == 1);
                    require(integer(result, "installation_manifest_version", 1, Integer.MAX_VALUE) == 1);
                    digest(result, "operation_digest_sha256");
                    digest(result, "installation_manifest_sha256");
                    text(result, "package_display_name", 120);
                    exact(result, "verification_scope", "PARTITION");
                    require(result.getObject("observed_content_sha256") == null);
                    return true;
                });
        require(installations.size() == 1);
        List<String> partitions = query(connection, PARTITION_SQL, 2,
                statement -> bindInstallation(statement, release), result -> {
                    association(result, release);
                    return ascii(result, "partition_key",128);
                });
        require(partitions.equals(List.of(LanguagePartition.KEY,ToolPartition.KEY)));
        List<LanguagePartition.Language> languages = query(connection, LANGUAGE_SQL, 18,
                statement -> statement.setLong(1, release.id()), result -> {
                    require(integer(result, "release_id", 1, Long.MAX_VALUE) == release.id());
                    try {
                        return new LanguagePartition.Language(ascii(result, "language_key", 128),
                                text(result, "display_name", 120), text(result, "description", 1000),
                                LanguagePartition.Category.valueOf(ascii(result, "category", 8)),
                                (int) integer(result, "source_page", 3, 74),
                                (int) integer(result, "sort_order", 1, 18));
                    } catch (IllegalArgumentException invalid) {
                        throw invalidSource();
                    }
                });
        List<ToolPartition.Tool> tools = query(connection, TOOL_SQL, 37,
                statement -> statement.setLong(1, release.id()), result -> {
                    require(integer(result, "release_id", 1, Long.MAX_VALUE) == release.id());
                    try {
                        return new ToolPartition.Tool(ascii(result, "tool_key", 128),
                                text(result, "display_name", 120), text(result, "description", 1000),
                                ToolPartition.Category.valueOf(ascii(result, "category", 18)),
                                (int) integer(result, "source_page", 3, 74),
                                (int) integer(result, "sort_order", 1, 37));
                    } catch (IllegalArgumentException invalid) { throw invalidSource(); }
                });
        try {
            return new CharacterCatalogPartition(new LanguagePartition(languages),new ToolPartition(tools));
        } catch (IllegalArgumentException invalid) {
            throw invalidSource();
        }
    }

    private static void bindInstallation(PreparedStatement statement, ReleaseRow release) throws SQLException {
        statement.setLong(1, release.id());
        statement.setLong(2, release.revision());
    }

    private static void association(ResultSet result, ReleaseRow release) throws SQLException {
        require(integer(result, "release_id", 1, Long.MAX_VALUE) == release.id()
                && integer(result, "installation_revision", 1, Long.MAX_VALUE) == release.revision());
    }

    private static <T> List<T> query(Connection connection, String sql, int limit, Binder binder,
                                   Mapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(Math.addExact(limit, 1));
            statement.setQueryTimeout(5);
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) {
                    // Observe the extra row, but do not fetch/materialize its values.
                    require(rows.size() < limit);
                    rows.add(mapper.map(result));
                }
                return List.copyOf(rows);
            }
        }
    }

    private static long integer(ResultSet result, String column, long minimum, long maximum)
            throws SQLException {
        Object value = result.getObject(column);
        // Avoid JDBC getLong/getInt coercion of NULL, strings, floating point and DECIMAL columns.
        require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long);
        long number = ((Number) value).longValue();
        require(number >= minimum && number <= maximum);
        return number;
    }

    private static byte[] binary(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        require(value instanceof byte[]);
        return (byte[]) value;
    }

    private static String ascii(ResultSet result, String column, int maximum) throws SQLException {
        byte[] value = binary(result, column);
        require(value.length > 0 && value.length <= maximum);
        for (byte item : value) require(item >= 0x21 && item <= 0x7e);
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static void exact(ResultSet result, String column, String expected) throws SQLException {
        require(asciiEquals(expected, binary(result, column)));
    }

    private static boolean asciiEquals(String expected, byte[] actual) {
        return Arrays.equals(expected.getBytes(StandardCharsets.US_ASCII), actual);
    }

    private static void digest(ResultSet result, String column) throws SQLException {
        byte[] value = binary(result, column);
        require(value.length == 64);
        for (byte item : value) require(item >= '0' && item <= '9' || item >= 'a' && item <= 'f');
    }

    private static String text(ResultSet result, String column, int maximum) throws SQLException {
        Object raw = result.getObject(column);
        require(raw instanceof String);
        String value = (String) raw;
        // Bound normalization and scalar scans even if the schema/driver supplies an oversized value.
        require(!value.isEmpty() && value.length() <= maximum * 2);
        int count = 0;
        boolean visible = false;
        for (int index = 0; index < value.length();) {
            int cp = value.codePointAt(index);
            require(cp < 0xd800 || cp > 0xdfff);
            require(cp > 0x1f && (cp < 0x7f || cp > 0x9f));
            visible |= !Character.isWhitespace(cp) && !Character.isSpaceChar(cp);
            count++;
            index += Character.charCount(cp);
        }
        require(count <= maximum && visible && Normalizer.isNormalized(value, Normalizer.Form.NFC));
        return value;
    }

    private static void require(boolean condition) throws SQLException {
        if (!condition) throw invalidSource();
    }

    private static SQLException invalidSource() {
        return new SQLException("Invalid source language partition");
    }

    private static Throwable combine(Throwable primary, Throwable secondary) {
        if (primary == null) return secondary;
        if (primary != secondary) primary.addSuppressed(secondary);
        return primary;
    }

    private static boolean abort(Connection connection, Throwable failure) {
        try {
            // JDBC abort terminates the physical connection; close alone may return it to a pool.
            connection.abort(Runnable::run);
            return true;
        } catch (SQLException | RuntimeException | Error abortFailure) {
            combine(failure, abortFailure);
            return false;
        }
    }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
    @FunctionalInterface private interface Mapper<T> { T map(ResultSet result) throws SQLException; }
    private record State(boolean autoCommit, boolean readOnly, int isolation) {}
    private record ReleaseRow(long id, long revision) {}
    private record LedgerRow(long version, byte[] scriptName, byte[] scriptSha256) {}
}
