package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

/** Reads and strictly compares the complete schema history without changing database data. */
final class DatabaseSchemaVerifier {
    private static final String SCHEMA_HISTORY_SQL = """
            SELECT schema_version, script_name, script_sha256
            FROM schema_meta
            ORDER BY schema_version ASC
            """;

    void verify(DataSource dataSource, List<SchemaMigrations.Expectation> expectedMigrations)
            throws SQLException, SchemaMismatchException {
        if (expectedMigrations.isEmpty()) {
            throw new IllegalArgumentException("At least one schema migration is required");
        }

        // Nested try-with-resources makes closure deterministic on success and every failure path.
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SCHEMA_HISTORY_SQL)) {
            // One extra row is enough to detect a database newer than this application.
            statement.setMaxRows(Math.addExact(expectedMigrations.size(), 1));
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                for (SchemaMigrations.Expectation expected : expectedMigrations) {
                    if (!result.next() || !rowMatches(result, expected)) {
                        throw new SchemaMismatchException();
                    }
                }

                // Any remaining row is an unapproved or unexpected migration.
                if (result.next()) {
                    throw new SchemaMismatchException();
                }
            }
        }
    }

    private static boolean rowMatches(
            ResultSet result,
            SchemaMigrations.Expectation expected) throws SQLException {
        int actualVersion = result.getInt("schema_version");
        boolean versionWasNull = result.wasNull();
        String actualScript = result.getString("script_name");
        boolean scriptWasNull = result.wasNull();
        String actualSha256 = result.getString("script_sha256");
        boolean sha256WasNull = result.wasNull();

        return !versionWasNull
                && !scriptWasNull
                && !sha256WasNull
                && actualVersion == expected.version()
                && expected.scriptName().equals(actualScript)
                && expected.scriptSha256().equals(actualSha256);
    }

    /** The mismatch has no message so database values cannot accidentally reach a client. */
    static final class SchemaMismatchException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
