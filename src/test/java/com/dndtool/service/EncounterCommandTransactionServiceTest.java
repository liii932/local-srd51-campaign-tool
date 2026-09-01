package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.EncounterCommandRepository;
import com.dndtool.persistence.EncounterStateRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class EncounterCommandTransactionServiceTest {
    private static final String REQUEST = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CAMPAIGN = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @Test
    void commitsStateRootEventAndIdempotencyTogether() throws Exception {
        Fixture fixture = new Fixture();

        var result = fixture.service().initialize(fixture.request());

        assertEquals(EncounterCommandTransactionService.Status.COMPLETED, result.status());
        assertEquals(9L, result.eventSequence());
        assertFalse(result.replayed());
        assertEquals(List.of("find", "state", "event", "complete"), fixture.calls);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        fixture.assertRestored();
    }

    @Test
    void replayAndConflictDoNotRewriteEncounterState() throws Exception {
        Fixture replay = new Fixture();
        replay.lookup = EncounterCommandRepository.Lookup.replay(
                new EncounterCommandRepository.SavedEvent(71L, 9L));
        var replayed = replay.service().initialize(replay.request());
        assertTrue(replayed.replayed());
        assertEquals(List.of("find"), replay.calls);
        assertTrue(replay.committed);

        Fixture conflict = new Fixture();
        conflict.lookup = EncounterCommandRepository.Lookup.conflict();
        var rejected = conflict.service().initialize(conflict.request());
        assertEquals(EncounterCommandTransactionService.Status.IDEMPOTENCY_CONFLICT,
                rejected.status());
        assertEquals(List.of("find"), conflict.calls);
        assertTrue(conflict.rolledBack);
    }

    @Test
    void digestMismatchIsRejectedBeforeObtainingConnection() throws Exception {
        Fixture fixture = new Fixture();
        var request = fixture.request();
        var rejected = fixture.service().initialize(
                new EncounterCommandTransactionService.Request(
                        request.requestId(), "f".repeat(64), request.campaignId(),
                        request.campaignKey(), request.moduleReleaseId(), request.partyNodeKey(),
                        request.participants()));

        assertEquals(EncounterCommandTransactionService.Status.INVALID_REQUEST,
                rejected.status());
        assertEquals(0, fixture.connectionRequests);
    }

    @Test
    void anyPersistenceFailureRollsBackAndRestoresConnection() {
        Fixture fixture = new Fixture();
        fixture.failState = true;

        assertThrows(SQLException.class, () -> fixture.service().initialize(fixture.request()));

        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        fixture.assertRestored();
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256",
                        "a".repeat(64), "RELEASED"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(new ModuleCatalog.MapNode(
                        "map.tavern_cellar", "node.cellar", "Cellar")), List.of());
    }

    private static final class Fixture {
        private boolean autoCommit = true;
        private boolean readOnly = true;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean committed;
        private boolean rolledBack;
        private boolean closed;
        private int connectionRequests;
        private boolean failState;
        private EncounterCommandRepository.Lookup lookup =
                EncounterCommandRepository.Lookup.fresh();
        private final List<String> calls = new ArrayList<>();

        private EncounterCommandTransactionService service() {
            EncounterStateRepository state = (connection, command) -> {
                calls.add("state");
                if (failState) throw new SQLException("synthetic state failure");
                return new EncounterStateRepository.SavedEncounter(51L, 61L, List.of());
            };
            EncounterCommandRepository commands = new EncounterCommandRepository() {
                @Override
                public Lookup find(Connection connection, Command command) {
                    calls.add("find");
                    return lookup;
                }

                @Override
                public SavedEvent appendEvent(Connection connection, long campaignId) {
                    calls.add("event");
                    return new SavedEvent(71L, 9L);
                }

                @Override
                public void complete(Connection connection, Completion completion) {
                    calls.add("complete");
                }
            };
            return new EncounterCommandTransactionService(
                    dataSource(), new EncounterStateService(catalog()), state, commands);
        }

        private EncounterCommandTransactionService.Request request() {
            List<EncounterStateService.ParticipantRequest> participants = List.of();
            String digest = EncounterRequestDigest.sha256(
                    CAMPAIGN, "node.cellar", participants);
            return new EncounterCommandTransactionService.Request(
                    REQUEST, digest, 7L, CAMPAIGN, 11L, "node.cellar", participants);
        }

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
                        case "setAutoCommit" -> { autoCommit = (boolean) arguments[0]; yield null; }
                        case "setReadOnly" -> { readOnly = (boolean) arguments[0]; yield null; }
                        case "setTransactionIsolation" -> {
                            isolation = (int) arguments[0]; yield null;
                        }
                        case "commit" -> { committed = true; yield null; }
                        case "rollback" -> { rolledBack = true; yield null; }
                        case "close" -> { closed = true; yield null; }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private void assertRestored() {
            assertTrue(autoCommit);
            assertTrue(readOnly);
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, isolation);
            assertTrue(closed);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return 0;
    }
}
