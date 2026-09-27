package com.dndtool.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;

/** Standalone read-only rules-ledger diagnostic; not registered with Web/JNDI/health startup. */
final class RuleDatabaseSchemaVerifier {
    void verify(DataSource dataSource) throws SQLException, SchemaMismatchException {
        try (Connection connection = dataSource.getConnection()) {
            verifySchema(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT schema_version, script_name, script_sha256
                    FROM rule_schema_meta ORDER BY schema_version ASC
                    """)) {
                statement.setMaxRows(Math.addExact(RuleSchemaMigrations.expectations().size(), 1));
                statement.setQueryTimeout(5);
                try (ResultSet result = statement.executeQuery()) {
                    for (RuleSchemaMigrations.Expectation expected : RuleSchemaMigrations.expectations()) {
                        if (!result.next() || !matches(result, expected)) {
                            throw new SchemaMismatchException();
                        }
                    }
                    if (result.next()) {
                        throw new SchemaMismatchException();
                    }
                }
            }
        }
    }

    private static void verifySchema(Connection connection) throws SQLException, SchemaMismatchException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CAST(DATABASE() AS BINARY)")) {
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !asciiEquals(RuleSchemaMigrations.DEFAULT_SCHEMA, result.getBytes(1))
                        || result.next()) {
                    throw new SchemaMismatchException();
                }
            }
        }
    }

    private static boolean matches(ResultSet result, RuleSchemaMigrations.Expectation expected)
            throws SQLException {
        long version = result.getLong("schema_version");
        boolean nullVersion = result.wasNull();
        byte[] name = result.getBytes("script_name");
        byte[] digest = result.getBytes("script_sha256");
        return !nullVersion && version == expected.version()
                && asciiEquals(expected.scriptName(), name) && asciiEquals(expected.scriptSha256(), digest);
    }

    private static boolean asciiEquals(String expected, byte[] actual) {
        return Arrays.equals(expected.getBytes(StandardCharsets.US_ASCII), actual);
    }

    static final class SchemaMismatchException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
