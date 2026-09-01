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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies pre-write validation, deterministic locking and caller transaction ownership. */
final class JdbcEncounterStateRepositoryTest {
    private static final String CHARACTER_A = "11111111-1111-4111-8111-111111111111";
    private static final String CHARACTER_B = "22222222-2222-4222-8222-222222222222";

    @Test
    void initializesMapPartyEncounterParticipantsAndPositionsAtomically() throws Exception {
        Fixture fixture = new Fixture();

        EncounterStateRepository.SavedEncounter result = initialize(fixture, command());

        assertEquals(501L, result.mapInstanceId());
        assertEquals(601L, result.battleId());
        assertEquals(List.of(CHARACTER_B, CHARACTER_A), result.participants().stream()
                .map(EncounterStateRepository.SavedParticipant::characterKey)
                .toList());
        assertEquals(List.of(41L, 42L), fixture.lockedCharacterIds);
        assertEquals(List.of(
                "map_instance",
                "party_world_position",
                "battle_state",
                "battle_participant",
                "entity_position",
                "battle_participant",
                "entity_position"), fixture.writeTargets);
        assertEquals(7, fixture.operations.indexOf("W:map_instance"));
        assertEquals(7L, fixture.writeValues.get(4).get(2));
        assertEquals(7L, fixture.writeValues.get(4).get(3));
        assertEquals("node.cellar_floor", fixture.writeValues.get(4).get(8));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void requiresWritableSerializableCallerTransactionBeforeSql() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;
        assertThrows(SQLException.class, () -> initialize(fixture, command()));
        fixture.autoCommit = false;
        fixture.readOnly = true;
        assertThrows(SQLException.class, () -> initialize(fixture, command()));
        fixture.readOnly = false;
        fixture.isolation = Connection.TRANSACTION_READ_COMMITTED;
        assertThrows(SQLException.class, () -> initialize(fixture, command()));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void rejectsUnknownNodeBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        fixture.nodeRows = List.of("node.cellar_stairs");

        assertThrows(SQLException.class, () -> initialize(fixture, command()));

        assertTrue(fixture.writeTargets.isEmpty());
        assertTrue(fixture.lockedCharacterIds.isEmpty());
    }

    @Test
    void rejectsMissingCampaignCharacterBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        fixture.omitCharacterB = true;

        assertThrows(SQLException.class, () -> initialize(fixture, command()));

        assertTrue(fixture.writeTargets.isEmpty());
        assertTrue(fixture.lockedCharacterIds.isEmpty());
    }

    @Test
    void rejectsParticipantSavedHashDriftBeforeAnyEncounterWrite() {
        Fixture fixture = new Fixture();
        fixture.savedContentSha256 = "0".repeat(64);

        assertThrows(EncounterStateRepository.ModuleHashMismatchException.class,
                () -> initialize(fixture, command()));

        assertEquals(List.of(41L), fixture.lockedCharacterIds);
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void rejectsBypassedDuplicateParticipantBeforeSql() {
        EncounterStateRepository.Command duplicate = new EncounterStateRepository.Command(
                7L,
                11L,
                "dnd5e2014_srd51_se_v1",
                "1",
                "a".repeat(64),
                "map.tavern_cellar",
                "NODE",
                "node.cellar_stairs",
                List.of(
                        placement(CHARACTER_A, "node.cellar_floor"),
                        placement(CHARACTER_A, "node.cellar_stairs")));
        Fixture fixture = new Fixture();

        assertThrows(SQLException.class, () -> initialize(fixture, duplicate));

        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void leavesMidWriteFailureForCallerRollback() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "entity_position";

        assertThrows(SQLException.class, () -> initialize(fixture, command()));

        assertEquals(List.of(
                "map_instance", "party_world_position", "battle_state",
                "battle_participant", "entity_position"), fixture.writeTargets);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    private static EncounterStateRepository.SavedEncounter initialize(
            Fixture fixture,
            EncounterStateRepository.Command command) throws SQLException {
        return new JdbcEncounterStateRepository().initialize(fixture.connection(), command);
    }

    private static EncounterStateRepository.Command command() {
        return new EncounterStateRepository.Command(
                7L,
                11L,
                "dnd5e2014_srd51_se_v1",
                "1",
                "a".repeat(64),
                "map.tavern_cellar",
                "NODE",
                "node.cellar_stairs",
                List.of(
                        placement(CHARACTER_B, "node.cellar_floor"),
                        placement(CHARACTER_A, "node.cellar_stairs")));
    }

    private static EncounterStateRepository.ParticipantPlacement placement(
            String characterKey,
            String nodeKey) {
        return new EncounterStateRepository.ParticipantPlacement(
                characterKey, EncounterStateRepository.Faction.ALLY, nodeKey);
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<String> operations = new ArrayList<>();
        private final List<String> writeTargets = new ArrayList<>();
        private final List<Map<Integer, Object>> writeValues = new ArrayList<>();
        private final List<Long> lockedCharacterIds = new ArrayList<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean committed;
        private boolean rolledBack;
        private boolean omitCharacterB;
        private String savedContentSha256 = "a".repeat(64);
        private List<String> nodeRows = List.of("node.cellar_floor", "node.cellar_stairs");
        private String failTarget;
        private long nextParticipantId = 701L;

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
            String[] lastWriteTarget = {null};
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setLong", "setInt", "setString" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> {
                            operations.add("Q");
                            yield queryResult(sql, bound);
                        }
                        case "executeUpdate" -> {
                            String target = writeTarget(sql);
                            lastWriteTarget[0] = target;
                            operations.add("W:" + target);
                            writeTargets.add(target);
                            writeValues.add(new HashMap<>(bound));
                            if (target.equals(failTarget)) {
                                throw new SQLException("synthetic encounter write failure");
                            }
                            yield 1;
                        }
                        case "getGeneratedKeys" -> generatedKeys(generatedId(lastWriteTarget[0]));
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet queryResult(String sql, Map<Integer, Object> bound) {
            if (sql.contains("FROM campaign\n")) {
                return rows(List.of(Map.of(1, "ACTIVE")));
            }
            if (sql.contains("FROM campaign_module")) {
                return rows(List.of(Map.of(
                        1, 11L,
                        2, "dnd5e2014_srd51_se_v1",
                        3, "1",
                        4, "a".repeat(64))));
            }
            if (sql.contains("FROM module_map_definition")) {
                return rows(List.of(Map.of(1, "NODE")));
            }
            if (sql.contains("FROM module_map_node")) {
                return rows(nodeRows.stream().map(node -> Map.<Object, Object>of(1, node)).toList());
            }
            if (sql.contains("character_key IN")) {
                List<Map<Object, Object>> result = new ArrayList<>();
                if (!omitCharacterB) {
                    result.add(Map.of(1, 41L, 2, CHARACTER_B));
                }
                result.add(Map.of(1, 42L, 2, CHARACTER_A));
                return rows(result);
            }
            if (sql.contains("FROM character_record") && sql.contains("FOR UPDATE")) {
                long id = (long) bound.get(1);
                lockedCharacterIds.add(id);
                String key = id == 41L ? CHARACTER_B : CHARACTER_A;
                return rows(List.of(Map.of(
                        1, key,
                        2, 7L,
                        3, 11L,
                        4, "ACTIVE",
                        5, "dnd5e2014_srd51_se_v1",
                        6, "1",
                        7, savedContentSha256)));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private long generatedId(String target) {
            return switch (target) {
                case "map_instance" -> 501L;
                case "battle_state" -> 601L;
                case "battle_participant" -> nextParticipantId++;
                default -> throw new AssertionError("Unexpected generated-key target: " + target);
            };
        }

        private static ResultSet generatedKeys(long id) {
            return rows(List.of(Map.of(1, id)));
        }

        private static ResultSet rows(List<Map<Object, Object>> rows) {
            int[] index = {-1};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "getString" -> {
                            Object value = rows.get(index[0]).get(arguments[0]);
                            yield value == null ? null : value.toString();
                        }
                        case "getLong" -> {
                            Object value = rows.get(index[0]).get(arguments[0]);
                            yield value == null ? 0L : ((Number) value).longValue();
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static String writeTarget(String sql) {
            String normalized = sql.stripLeading();
            return normalized.substring("INSERT INTO ".length()).split("\\s+", 2)[0];
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
