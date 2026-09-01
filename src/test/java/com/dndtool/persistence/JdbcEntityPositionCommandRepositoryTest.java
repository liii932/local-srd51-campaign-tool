package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies that direct positioning has no implicit join or topology side effects. */
final class JdbcEntityPositionCommandRepositoryTest {
    private static final String REQUEST = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CHARACTER = "11111111-1111-4111-8111-111111111111";

    @Test
    void movesExistingParticipantThenAppendsRootAndOperationWithoutTopologySql() throws Exception {
        Fixture fixture = new Fixture();
        JdbcEntityPositionCommandRepository repository = new JdbcEntityPositionCommandRepository();

        assertEquals(EntityPositionCommandRepository.Status.NEW,
                repository.find(fixture.connection(), identity()).status());
        EntityPositionCommandRepository.MoveResult move =
                repository.move(fixture.connection(), moveCommand());
        EntityPositionCommandRepository.SavedEvent event = repository.appendEvent(
                fixture.connection(), new EntityPositionCommandRepository.EventCommand(
                        7L, 8L, 41L, "node.cellar_floor"));
        repository.complete(fixture.connection(), new EntityPositionCommandRepository.Completion(
                REQUEST, "b".repeat(64), 7L, event.gameEventId()));

        assertTrue(move.changed());
        assertEquals("node.cellar_stairs", move.previousNodeKey());
        assertEquals(71L, event.gameEventId());
        assertEquals(9L, event.eventSequence());
        assertEquals(List.of(
                "entity_position", "campaign", "game_event", "host_operation"),
                fixture.writeTargets);
        String allSql = String.join("\n", fixture.preparedSql);
        assertFalse(allSql.contains("module_map_connection"));
        assertFalse(allSql.contains("party_world_position"));
        assertFalse(allSql.contains("INSERT INTO battle_participant"));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void sameNodeIsNoOpButStillRequiresExistingParticipant() throws Exception {
        Fixture fixture = new Fixture();
        fixture.currentNode = "node.cellar_floor";

        EntityPositionCommandRepository.MoveResult move =
                new JdbcEntityPositionCommandRepository().move(
                        fixture.connection(), moveCommand());

        assertFalse(move.changed());
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void rejectsNonParticipantBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        fixture.participantMissing = true;

        assertThrows(SQLException.class, () -> new JdbcEntityPositionCommandRepository().move(
                fixture.connection(), moveCommand()));

        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void rejectsFrozenNodeOrEncounterMismatchBeforeAnyWrite() {
        Fixture missingNode = new Fixture();
        missingNode.nodeMissing = true;
        assertThrows(SQLException.class, () -> new JdbcEntityPositionCommandRepository().move(
                missingNode.connection(), moveCommand()));
        assertTrue(missingNode.writeTargets.isEmpty());

        Fixture wrongRelease = new Fixture();
        wrongRelease.battleReleaseId = 12L;
        assertThrows(SQLException.class, () -> new JdbcEntityPositionCommandRepository().move(
                wrongRelease.connection(), moveCommand()));
        assertTrue(wrongRelease.writeTargets.isEmpty());
    }

    @Test
    void replaysMatchingRequestAndRejectsSameIdWithDifferentDigest() throws Exception {
        Fixture replay = new Fixture();
        replay.existingOperation = true;
        EntityPositionCommandRepository.Lookup found =
                new JdbcEntityPositionCommandRepository().find(replay.connection(), identity());
        assertEquals(EntityPositionCommandRepository.Status.REPLAY, found.status());
        assertEquals(71L, found.replay().gameEventId());
        assertEquals("node.cellar_floor", found.replay().nodeKey());

        Fixture conflict = new Fixture();
        conflict.existingOperation = true;
        conflict.existingDigest = "c".repeat(64);
        EntityPositionCommandRepository.Lookup rejected =
                new JdbcEntityPositionCommandRepository().find(conflict.connection(), identity());
        assertEquals(EntityPositionCommandRepository.Status.CONFLICT, rejected.status());
        assertEquals(1, conflict.preparedSql.size());
    }

    @Test
    void requiresWritableSerializableCallerTransactionBeforeSql() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;
        assertThrows(SQLException.class, () -> new JdbcEntityPositionCommandRepository().move(
                fixture.connection(), moveCommand()));
        fixture.autoCommit = false;
        fixture.readOnly = true;
        assertThrows(SQLException.class, () -> new JdbcEntityPositionCommandRepository().move(
                fixture.connection(), moveCommand()));
        fixture.readOnly = false;
        fixture.isolation = Connection.TRANSACTION_READ_COMMITTED;
        assertThrows(SQLException.class, () -> new JdbcEntityPositionCommandRepository().move(
                fixture.connection(), moveCommand()));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void leavesUpdateFailureForCallerRollback() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "entity_position";

        assertThrows(SQLException.class, () -> new JdbcEntityPositionCommandRepository().move(
                fixture.connection(), moveCommand()));

        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    private static EntityPositionCommandRepository.IdempotencyCommand identity() {
        return new EntityPositionCommandRepository.IdempotencyCommand(
                REQUEST, "b".repeat(64), 7L);
    }

    private static EntityPositionCommandRepository.MoveCommand moveCommand() {
        return new EntityPositionCommandRepository.MoveCommand(
                7L, 11L, "dnd5e2014_srd51_se_v1", "1", "a".repeat(64),
                "map.tavern_cellar", 41L, CHARACTER, "node.cellar_floor");
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<String> writeTargets = new ArrayList<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean committed;
        private boolean rolledBack;
        private boolean existingOperation;
        private String existingDigest = "b".repeat(64);
        private long battleReleaseId = 11L;
        private boolean nodeMissing;
        private boolean participantMissing;
        private String currentNode = "node.cellar_stairs";
        private String failTarget;

        private Connection connection() {
            return proxy(Connection.class, this::connectionCall);
        }

        private Object connectionCall(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "prepareStatement" -> statement((String) arguments[0]);
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql) {
            preparedSql.add(sql);
            Map<Integer, Object> bound = new HashMap<>();
            String[] lastWrite = {null};
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setLong", "setInt", "setString" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> queryResult(sql);
                        case "executeUpdate" -> {
                            String target = writeTarget(sql);
                            lastWrite[0] = target;
                            writeTargets.add(target);
                            if (target.equals(failTarget)) {
                                throw new SQLException("synthetic direct-position write failure");
                            }
                            yield 1;
                        }
                        case "getGeneratedKeys" -> {
                            if (!"game_event".equals(lastWrite[0])) {
                                throw new AssertionError("Unexpected generated key target");
                            }
                            yield rows(List.of(Map.of("1", 71L)));
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet queryResult(String sql) {
            if (sql.contains("FROM host_operation")) {
                return existingOperation ? rows(List.of(Map.of(
                        "request_digest_sha256", existingDigest,
                        "operation_type", "SET_STAGE3_ENTITY_POSITION",
                        "campaign_id", 7L,
                        "result_status", "SUCCEEDED",
                        "game_event_id", 71L))) : rows(List.of());
            }
            if (sql.contains("FROM game_event")) {
                return rows(List.of(Map.of(
                        "event_sequence", 9L,
                        "subject_character_id", 41L,
                        "character_key", CHARACTER,
                        "event_text", "node.cellar_floor")));
            }
            if (sql.contains("FROM battle_state")) {
                return rows(List.of(Map.of(
                        "id", 61L,
                        "map_instance_id", 51L,
                        "module_release_id", battleReleaseId,
                        "map_key", "map.tavern_cellar")));
            }
            if (sql.contains("FROM module_map_node")) {
                return nodeMissing ? rows(List.of())
                        : rows(List.of(Map.of("node_key", "node.cellar_floor")));
            }
            if (sql.contains("FROM battle_participant")) {
                return participantMissing ? rows(List.of()) : rows(List.of(Map.of(
                        "character_id", 41L,
                        "character_key", CHARACTER,
                        "node_key", currentNode)));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private static ResultSet rows(List<Map<String, Object>> values) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < values.size();
                        case "getString" -> {
                            Object value = get(values.get(index[0]), arguments[0]);
                            wasNull[0] = value == null;
                            yield value == null ? null : value.toString();
                        }
                        case "getLong" -> {
                            Object value = get(values.get(index[0]), arguments[0]);
                            wasNull[0] = value == null;
                            yield value == null ? 0L : ((Number) value).longValue();
                        }
                        case "wasNull" -> wasNull[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object get(Map<String, Object> row, Object key) {
            return row.get(key instanceof Integer integer ? Integer.toString(integer) : key);
        }

        private static String writeTarget(String sql) {
            String normalized = sql.stripLeading();
            if (normalized.startsWith("UPDATE ")) {
                return normalized.substring("UPDATE ".length()).split("\\s+", 2)[0];
            }
            if (normalized.startsWith("INSERT INTO ")) {
                return normalized.substring("INSERT INTO ".length()).split("[\\s(]", 2)[0];
            }
            throw new AssertionError("Unexpected write: " + sql);
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
