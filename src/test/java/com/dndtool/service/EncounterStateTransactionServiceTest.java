package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.EncounterStateRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class EncounterStateTransactionServiceTest {

    @Test
    void commitsOnceAndRestoresConnectionAfterCompleteSave() throws Exception {
        Fixture fixture = new Fixture();
        EncounterStateRepository.SavedEncounter expected =
                new EncounterStateRepository.SavedEncounter(501L, 601L, List.of());
        EncounterStateRepository repository = (connection, command) -> {
            assertFalse(connection.getAutoCommit());
            assertFalse(connection.isReadOnly());
            assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                    connection.getTransactionIsolation());
            return expected;
        };

        EncounterStateRepository.SavedEncounter actual = service(fixture, repository)
                .initialize(request("node.cellar"));

        assertSame(expected, actual);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.autoCommit);
        assertTrue(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
        assertTrue(fixture.closed);
    }

    @Test
    void rollsBackAndRestoresConnectionWhenRepositoryFails() {
        Fixture fixture = new Fixture();
        EncounterStateRepository repository = (connection, command) -> {
            throw new SQLException("synthetic initialization failure");
        };

        assertThrows(SQLException.class,
                () -> service(fixture, repository).initialize(request("node.cellar")));

        assertFalse(fixture.committed);
        assertTrue(fixture.rolledBack);
        assertTrue(fixture.autoCommit);
        assertTrue(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
        assertTrue(fixture.closed);
    }

    @Test
    void rejectsUnknownNodeBeforeObtainingConnection() {
        Fixture fixture = new Fixture();
        EncounterStateRepository repository = (connection, command) -> {
            throw new AssertionError("Repository must not be called");
        };

        assertThrows(EncounterStateService.EncounterStateException.class,
                () -> service(fixture, repository).initialize(request("node.unknown")));

        assertEquals(0, fixture.connectionRequests);
    }

    private static EncounterStateTransactionService service(
            Fixture fixture,
            EncounterStateRepository repository) {
        return new EncounterStateTransactionService(
                fixture.dataSource(),
                new EncounterStateService(catalog()),
                repository);
    }

    private static EncounterStateTransactionService.Request request(String partyNode) {
        return new EncounterStateTransactionService.Request(
                7L, 11L, partyNode, List.of());
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", "a".repeat(64), "RELEASED"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(new ModuleCatalog.MapNode(
                        "map.tavern_cellar", "node.cellar", "Cellar")),
                List.of());
    }

    private static final class Fixture {
        private boolean autoCommit = true;
        private boolean readOnly = true;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean committed;
        private boolean rolledBack;
        private boolean closed;
        private int connectionRequests;

        private DataSource dataSource() {
            return proxy(DataSource.class, (ignored, method, arguments) -> {
                if ("getConnection".equals(method.getName())) {
                    connectionRequests++;
                    return connection();
                }
                return defaultValue(method.getReturnType());
            });
        }

        private Connection connection() {
            return proxy(Connection.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "isReadOnly" -> readOnly;
                        case "getTransactionIsolation" -> isolation;
                        case "setAutoCommit" -> {
                            autoCommit = (boolean) arguments[0];
                            yield null;
                        }
                        case "setReadOnly" -> {
                            readOnly = (boolean) arguments[0];
                            yield null;
                        }
                        case "setTransactionIsolation" -> {
                            isolation = (int) arguments[0];
                            yield null;
                        }
                        case "commit" -> {
                            committed = true;
                            yield null;
                        }
                        case "rollback" -> {
                            rolledBack = true;
                            yield null;
                        }
                        case "close" -> {
                            closed = true;
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class || type == short.class || type == int.class || type == long.class) {
            return 0;
        }
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
