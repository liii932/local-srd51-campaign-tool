package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.CharacterVersionRepository;
import com.dndtool.persistence.EntityPositionCommandRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class EntityPositionTransactionServiceTest {
    private static final String REQUEST = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CHARACTER = "11111111-1111-4111-8111-111111111111";

    @Test
    void commitsMoveEventOperationAndOneVersionAdvance() throws Exception {
        Fixture fixture = new Fixture();

        EntityPositionTransactionService.Result result = fixture.service().position(request(3L));

        assertEquals(EntityPositionTransactionService.Status.COMPLETED, result.status());
        assertEquals("node.cellar_stairs", result.previousNodeKey());
        assertEquals("node.cellar_floor", result.nodeKey());
        assertEquals(4L, result.rowVersion());
        assertTrue(result.changed());
        assertFalse(result.replayed());
        assertEquals(List.of("find", "move", "event", "complete"), fixture.calls);
        assertEquals(Set.of(41L), fixture.advancedIds);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        fixture.assertRestored();
    }

    @Test
    void sameNodeCreatesReplayRootWithoutVersionAdvance() throws Exception {
        Fixture fixture = new Fixture();
        fixture.changed = false;
        fixture.previousNode = "node.cellar_floor";

        EntityPositionTransactionService.Result result = fixture.service().position(request(3L));

        assertFalse(result.changed());
        assertEquals(3L, result.rowVersion());
        assertEquals(Set.of(), fixture.advancedIds);
        assertEquals(List.of("find", "move", "event", "complete"), fixture.calls);
        assertTrue(fixture.committed);
    }

    @Test
    void replayBypassesCharacterLocksAndMovement() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lookup = EntityPositionCommandRepository.Lookup.replay(
                new EntityPositionCommandRepository.Replay(
                        71L, 9L, 41L, CHARACTER, "node.cellar_floor"));

        EntityPositionTransactionService.Result result = fixture.service().position(request(3L));

        assertTrue(result.replayed());
        assertEquals(71L, result.gameEventId());
        assertEquals("node.cellar_floor", result.nodeKey());
        assertNull(result.rowVersion());
        assertEquals(List.of("find"), fixture.calls);
        assertEquals(0, fixture.versionLocks);
        assertTrue(fixture.committed);
    }

    @Test
    void idempotencyConflictRollsBackBeforeCharacterLock() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lookup = EntityPositionCommandRepository.Lookup.conflict();

        EntityPositionTransactionService.Result result = fixture.service().position(request(3L));

        assertEquals(EntityPositionTransactionService.Status.IDEMPOTENCY_CONFLICT,
                result.status());
        assertEquals(0, fixture.versionLocks);
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
    }

    @Test
    void staleVersionRollsBackBeforePositionLookup() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lockStatus = CharacterVersionRepository.Status.VERSION_CONFLICT;

        EntityPositionTransactionService.Result result = fixture.service().position(request(3L));

        assertEquals(EntityPositionTransactionService.Status.VERSION_CONFLICT,
                result.status());
        assertEquals(List.of("find"), fixture.calls);
        assertTrue(fixture.rolledBack);
    }

    @Test
    void savedModuleHashMismatchRollsBackBeforePositionLookup() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lockStatus = CharacterVersionRepository.Status.MODULE_HASH_MISMATCH;

        EntityPositionTransactionService.Result result = fixture.service().position(
                request(3L));

        assertEquals(EntityPositionTransactionService.Status.MODULE_HASH_MISMATCH,
                result.status());
        assertEquals(List.of("find"), fixture.calls);
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
    }

    @Test
    void unknownNodeIsRejectedBeforeObtainingConnection() {
        Fixture fixture = new Fixture();

        assertThrows(EntityPositionService.DirectPositionException.class,
                () -> fixture.service().position(new EntityPositionService.Request(
                        REQUEST, 7L, 11L, CHARACTER, 3L, "node.unknown")));

        assertEquals(0, fixture.connectionRequests);
    }

    @Test
    void repositoryFailureRollsBackAndRestoresConnection() {
        Fixture fixture = new Fixture();
        fixture.failMove = true;

        assertThrows(SQLException.class, () -> fixture.service().position(request(3L)));

        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        fixture.assertRestored();
    }

    private static EntityPositionService.Request request(long version) {
        return new EntityPositionService.Request(
                REQUEST, 7L, 11L, CHARACTER, version, "node.cellar_floor");
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
                        "map.tavern_cellar", "node.cellar_floor", "Floor")),
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
        private int versionLocks;
        private CharacterVersionRepository.Status lockStatus =
                CharacterVersionRepository.Status.LOCKED;
        private EntityPositionCommandRepository.Lookup lookup =
                EntityPositionCommandRepository.Lookup.fresh();
        private boolean changed = true;
        private String previousNode = "node.cellar_stairs";
        private boolean failMove;
        private Set<Long> advancedIds = Set.of();
        private final java.util.ArrayList<String> calls = new java.util.ArrayList<>();

        private EntityPositionTransactionService service() {
            CharacterVersionRepository versions = new CharacterVersionRepository() {
                @Override
                public LockResult lockBeforeRoll(Connection connection, LockCommand command) {
                    versionLocks++;
                    if (lockStatus != Status.LOCKED) {
                        return LockResult.rejected(lockStatus, CHARACTER, 5L);
                    }
                    LockedCharacter character = new LockedCharacter(
                            41L, CHARACTER, 7L, 11L, 3L);
                    return LockResult.locked(new LockedScope(
                            7L, 11L, 8L, character, List.of(character)));
                }

                @Override
                public Map<Long, Long> advanceModifiedVersions(
                        Connection connection, LockedScope scope, Set<Long> modifiedCharacterIds) {
                    advancedIds = Set.copyOf(modifiedCharacterIds);
                    return modifiedCharacterIds.isEmpty() ? Map.of() : Map.of(41L, 4L);
                }
            };
            return new EntityPositionTransactionService(
                    dataSource(),
                    new EntityPositionService(catalog()),
                    new CharacterVersionService(versions),
                    repository());
        }

        private EntityPositionCommandRepository repository() {
            return new EntityPositionCommandRepository() {
                @Override
                public Lookup find(Connection connection, IdempotencyCommand command) {
                    calls.add("find");
                    return lookup;
                }

                @Override
                public MoveResult move(Connection connection, MoveCommand command)
                        throws SQLException {
                    calls.add("move");
                    if (failMove) throw new SQLException("synthetic direct-position failure");
                    return new MoveResult(previousNode, command.nodeKey(), changed);
                }

                @Override
                public SavedEvent appendEvent(Connection connection, EventCommand command) {
                    calls.add("event");
                    return new SavedEvent(71L, 9L, 41L, command.nodeKey());
                }

                @Override
                public void complete(Connection connection, Completion completion) {
                    calls.add("complete");
                }
            };
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
        if (type == byte.class || type == short.class || type == int.class || type == long.class) {
            return 0;
        }
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
