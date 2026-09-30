package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.DatabaseSchemaStatus;
import com.dndtool.persistence.SchemaMigrations;
import com.dndtool.service.DatabaseDiagnostics;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class DatabaseDiagnosticsFactoryTest {
    @Test
    void assemblyIsLazyAndEveryRunUsesOnlyTheLegacyJndiPool() {
        List<String> lookups = new ArrayList<>();
        DataSource unavailable = proxy(DataSource.class, (ignored, method, arguments) -> {
            if (method.getName().equals("getConnection")) {
                throw new SQLException("secret connection detail");
            }
            throw new AssertionError("Unexpected DataSource call: " + method.getName());
        });
        DatabaseDiagnostics diagnostics = DatabaseDiagnosticsFactory.usingJndi(name -> {
            lookups.add(name);
            return unavailable;
        });
        assertTrue(lookups.isEmpty());

        assertEquals(DatabaseSchemaStatus.State.DATABASE_UNAVAILABLE, diagnostics.run().state());
        assertEquals(DatabaseSchemaStatus.State.DATABASE_UNAVAILABLE, diagnostics.run().state());
        assertEquals(List.of("java:comp/env/jdbc/DndToolSE", "java:comp/env/jdbc/DndToolSE"), lookups);
    }

    @Test
    void missingAndWrongTypeBindingsRemainFiniteFailures() {
        assertEquals(DatabaseSchemaStatus.State.JNDI_UNAVAILABLE,
                DatabaseDiagnosticsFactory.usingJndi(name -> {
                    throw new NamingException("secret naming detail");
                }).run().state());
        assertEquals(DatabaseSchemaStatus.State.JNDI_UNAVAILABLE,
                DatabaseDiagnosticsFactory.usingJndi(name -> "not a pool").run().state());
        assertEquals(DatabaseSchemaStatus.State.JNDI_UNAVAILABLE,
                DatabaseDiagnosticsFactory.usingJndi(name -> null).run().state());
    }

    @Test
    void schemaFailureClosesResourcesBeforeAnyModuleRead() {
        ReadOnlyFixture fixture = new ReadOnlyFixture(List.of());

        DatabaseSchemaStatus result = DatabaseDiagnosticsFactory
                .usingJndi(name -> fixture.dataSource()).run();

        assertEquals(DatabaseSchemaStatus.State.SCHEMA_MISMATCH, result.state());
        assertEquals(1, fixture.sql.size());
        assertTrue(fixture.sql.getFirst().contains("FROM schema_meta"));
        fixture.assertAllClosed(1);
    }

    @Test
    void matchingSchemaRunsModuleIntegrityOnTheSamePoolWithoutDml() throws Exception {
        ReadOnlyFixture fixture = new ReadOnlyFixture(SchemaMigrations.loadExpectations());

        DatabaseSchemaStatus result = DatabaseDiagnosticsFactory
                .usingJndi(name -> fixture.dataSource()).run();

        // No installed release: the real module verifier must reject after the schema passed.
        assertEquals(DatabaseSchemaStatus.State.MODULE_HASH_MISMATCH, result.state());
        assertEquals(2, fixture.sql.size());
        assertTrue(fixture.sql.get(0).contains("FROM schema_meta"));
        assertTrue(fixture.sql.get(1).contains("FROM module_release"));
        fixture.assertAllClosed(2);
    }

    private static final class ReadOnlyFixture {
        private final List<SchemaMigrations.Expectation> rows;
        private final List<String> sql = new ArrayList<>();
        private int borrowed;
        private int connectionsClosed;
        private int statementsClosed;
        private int resultsClosed;

        private ReadOnlyFixture(List<SchemaMigrations.Expectation> rows) {
            this.rows = rows;
        }

        private DataSource dataSource() {
            return proxy(DataSource.class, (ignored, method, arguments) -> {
                if (!method.getName().equals("getConnection")) {
                    throw new AssertionError("Unexpected DataSource call: " + method.getName());
                }
                assertEquals(borrowed, connectionsClosed, "Return each connection before the next check");
                borrowed++;
                return connection();
            });
        }

        private Connection connection() {
            boolean[] autoCommit = {true};
            return proxy(Connection.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "prepareStatement" -> statement((String) arguments[0]);
                case "getAutoCommit" -> autoCommit[0];
                case "setAutoCommit" -> { autoCommit[0] = (boolean) arguments[0]; yield null; }
                case "isReadOnly" -> false;
                case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                case "setReadOnly", "setTransactionIsolation", "commit", "rollback" -> null;
                case "close" -> { connectionsClosed++; yield null; }
                default -> throw new AssertionError("Unexpected connection call: " + method.getName());
            });
        }

        private PreparedStatement statement(String query) {
            assertTrue(query.stripLeading().startsWith("SELECT "), "Diagnostics must only query");
            sql.add(query);
            return proxy(PreparedStatement.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "setMaxRows", "setQueryTimeout", "setString" -> null;
                case "executeQuery" -> result(query.contains("FROM schema_meta"));
                case "close" -> { statementsClosed++; yield null; }
                default -> throw new AssertionError("Unexpected statement call: " + method.getName());
            });
        }

        private ResultSet result(boolean schema) {
            int[] index = {-1};
            return proxy(ResultSet.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "next" -> schema && ++index[0] < rows.size();
                case "getInt" -> rows.get(index[0]).version();
                case "getString" -> switch ((String) arguments[0]) {
                    case "script_name" -> rows.get(index[0]).scriptName();
                    case "script_sha256" -> rows.get(index[0]).scriptSha256();
                    default -> throw new AssertionError("Unexpected column");
                };
                case "wasNull" -> false;
                case "close" -> { resultsClosed++; yield null; }
                default -> throw new AssertionError("Unexpected result call: " + method.getName());
            });
        }

        private void assertAllClosed(int count) {
            assertEquals(count, borrowed);
            assertEquals(count, connectionsClosed);
            assertEquals(count, statementsClosed);
            assertEquals(count, resultsClosed);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
