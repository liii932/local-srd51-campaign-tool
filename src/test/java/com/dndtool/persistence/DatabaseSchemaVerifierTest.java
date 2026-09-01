package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseSchemaVerifierTest {
    private static final SchemaMigrations.Expectation V001 = new SchemaMigrations.Expectation(
            SchemaMigrations.V001_VERSION,
            SchemaMigrations.V001_SCRIPT_NAME,
            SchemaMigrations.V001_APPROVED_SHA256);
    private static final SchemaMigrations.Expectation V002 = new SchemaMigrations.Expectation(
            2,
            "V002__test_schema.sql",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    private static final List<SchemaMigrations.Expectation> EXPECTED_CHAIN = List.of(V001, V002);

    @Test
    void matchingMigrationChainClosesEveryJdbcResource() throws Exception {
        JdbcFixture fixture = JdbcFixture.withRows(row(V001), row(V002));

        new DatabaseSchemaVerifier().verify(fixture.dataSource(), EXPECTED_CHAIN);

        assertEquals(3, fixture.maxRows);
        assertTrue(fixture.preparedSql.contains("ORDER BY schema_version ASC"));
        assertClosed(fixture);
    }

    @Test
    void missingMigrationIsRejected() {
        JdbcFixture fixture = JdbcFixture.withRows(row(V001));

        assertMismatch(fixture);
        assertClosed(fixture);
    }

    @Test
    void extraMigrationIsRejected() {
        JdbcFixture fixture = JdbcFixture.withRows(
                row(V001),
                row(V002),
                new Object[] {3, "V003__unexpected.sql", "unexpected-checksum"});

        assertMismatch(fixture);
        assertClosed(fixture);
    }

    @Test
    void duplicateMigrationIsRejected() {
        JdbcFixture fixture = JdbcFixture.withRows(row(V001), row(V001));

        assertMismatch(fixture);
        assertClosed(fixture);
    }

    @Test
    void reorderedMigrationIsRejected() {
        JdbcFixture fixture = JdbcFixture.withRows(row(V002), row(V001));

        assertMismatch(fixture);
        assertClosed(fixture);
    }

    @Test
    void mismatchedChecksumIsRejected() {
        JdbcFixture fixture = JdbcFixture.withRows(
                row(V001),
                new Object[] {V002.version(), V002.scriptName(), "wrong-checksum"});

        assertMismatch(fixture);
        assertClosed(fixture);
    }

    @Test
    void nullMetadataIsRejected() {
        JdbcFixture fixture = JdbcFixture.withRows(
                row(V001),
                new Object[] {V002.version(), V002.scriptName(), null});

        assertMismatch(fixture);
        assertClosed(fixture);
    }

    @Test
    void queryFailureClosesStatementAndConnection() {
        JdbcFixture fixture = JdbcFixture.withQueryFailure();

        assertThrows(
                SQLException.class,
                () -> new DatabaseSchemaVerifier().verify(fixture.dataSource(), EXPECTED_CHAIN));

        assertTrue(fixture.statementClosed);
        assertTrue(fixture.connectionClosed);
    }

    private static Object[] row(SchemaMigrations.Expectation expectation) {
        return new Object[] {
            expectation.version(), expectation.scriptName(), expectation.scriptSha256()
        };
    }

    private static void assertMismatch(JdbcFixture fixture) {
        assertThrows(
                DatabaseSchemaVerifier.SchemaMismatchException.class,
                () -> new DatabaseSchemaVerifier().verify(fixture.dataSource(), EXPECTED_CHAIN));
    }

    private static void assertClosed(JdbcFixture fixture) {
        assertTrue(fixture.resultClosed);
        assertTrue(fixture.statementClosed);
        assertTrue(fixture.connectionClosed);
    }

    /** Minimal JDBC proxies keep the test focused on strict comparison and resource ownership. */
    private static final class JdbcFixture {
        private final List<Object[]> rows;
        private final boolean failQuery;
        private int rowIndex = -1;
        private boolean lastReadWasNull;
        private boolean connectionClosed;
        private boolean statementClosed;
        private boolean resultClosed;
        private int maxRows;
        private String preparedSql;

        private JdbcFixture(List<Object[]> rows, boolean failQuery) {
            this.rows = rows;
            this.failQuery = failQuery;
        }

        static JdbcFixture withRows(Object[]... rows) {
            return new JdbcFixture(List.of(rows), false);
        }

        static JdbcFixture withQueryFailure() {
            return new JdbcFixture(List.of(), true);
        }

        DataSource dataSource() {
            Connection connection = proxy(Connection.class, this::handleConnection);
            return proxy(DataSource.class, (proxy, method, arguments) -> {
                if ("getConnection".equals(method.getName())) {
                    return connection;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private Object handleConnection(Object proxy, Method method, Object[] arguments) {
            if ("prepareStatement".equals(method.getName())) {
                preparedSql = (String) arguments[0];
                return proxy(PreparedStatement.class, this::handleStatement);
            }
            if ("close".equals(method.getName())) {
                connectionClosed = true;
            }
            return defaultValue(method.getReturnType());
        }

        private Object handleStatement(Object proxy, Method method, Object[] arguments)
                throws SQLException {
            if ("setMaxRows".equals(method.getName())) {
                maxRows = (int) arguments[0];
                return null;
            }
            if ("executeQuery".equals(method.getName())) {
                if (failQuery) {
                    throw new SQLException("synthetic query failure");
                }
                return proxy(ResultSet.class, this::handleResult);
            }
            if ("close".equals(method.getName())) {
                statementClosed = true;
            }
            return defaultValue(method.getReturnType());
        }

        private Object handleResult(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "next" -> ++rowIndex < rows.size();
                case "getInt" -> {
                    Object value = currentValue(0);
                    yield value == null ? 0 : value;
                }
                case "getString" -> {
                    int column = switch ((String) arguments[0]) {
                        case "script_name" -> 1;
                        case "script_sha256" -> 2;
                        default -> throw new IllegalArgumentException("Unknown synthetic column");
                    };
                    yield currentValue(column);
                }
                case "wasNull" -> lastReadWasNull;
                case "close" -> {
                    resultClosed = true;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object currentValue(int column) {
            Object value = rows.get(rowIndex)[column];
            lastReadWasNull = value == null;
            return value;
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == byte.class || type == short.class || type == int.class || type == long.class) {
                return 0;
            }
            if (type == float.class || type == double.class) {
                return 0.0;
            }
            if (type == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
