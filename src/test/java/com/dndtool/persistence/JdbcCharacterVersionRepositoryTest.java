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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies campaign-first locking and stable character-id order without a real database. */
final class JdbcCharacterVersionRepositoryTest {
    private static final String EXECUTOR = "11111111-1111-1111-1111-111111111111";
    private static final String TARGET_A = "22222222-2222-2222-2222-222222222222";
    private static final String TARGET_B = "33333333-3333-3333-3333-333333333333";
    private static final String MODULE_KEY = "dnd5e2014_srd51_se_v1";
    private static final String RELEASE_VERSION = "1";
    private static final String SHA = "a".repeat(64);

    @Test
    void locksCampaignThenAllCharactersInAscendingDatabaseIdOrder() throws Exception {
        Fixture fixture = new Fixture();

        CharacterVersionRepository.LockResult result =
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        fixture.connection(), command());

        assertEquals(CharacterVersionRepository.Status.LOCKED, result.status());
        assertEquals(30L, result.scope().executor().id());
        assertEquals(List.of(10L, 20L, 30L), result.scope().charactersById().stream()
                .map(CharacterVersionRepository.LockedCharacter::id).toList());
        assertEquals(List.of(10L, 20L, 30L), fixture.characterLockOrder);
        assertTrue(fixture.preparedSql.getFirst().contains("FROM campaign"));
        assertTrue(fixture.preparedSql.getFirst().contains("FOR UPDATE"));
        assertTrue(fixture.preparedSql.get(1).contains("FROM campaign_module"));
        assertFalse(fixture.preparedSql.get(1).contains("FOR UPDATE"));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void advancesEachModifiedLockedAggregateOnceInAscendingOrder() throws Exception {
        Fixture fixture = new Fixture();
        JdbcCharacterVersionRepository repository =
                new JdbcCharacterVersionRepository();
        CharacterVersionRepository.LockedScope scope =
                repository.lockBeforeRoll(fixture.connection(), command()).scope();

        Map<Long, Long> advanced = repository.advanceModifiedVersions(
                fixture.connection(), scope, Set.of(30L, 10L));

        assertEquals(Map.of(10L, 5L, 30L, 3L), advanced);
        assertEquals(List.of(10L, 30L), fixture.versionUpdateOrder);
        assertEquals(List.of(4L, 2L), fixture.versionExpectedValues);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void reportsVersionConflictBeforeLaterLocksOrAnyWrite() throws Exception {
        Fixture fixture = new Fixture();
        fixture.characters.get(10L).put("row_version", 5L);

        CharacterVersionRepository.LockResult result =
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        fixture.connection(), command());

        assertEquals(CharacterVersionRepository.Status.VERSION_CONFLICT, result.status());
        assertEquals(TARGET_A, result.rejectedCharacterKey());
        assertEquals(5L, result.currentRowVersion());
        assertEquals(List.of(10L, 20L, 30L), fixture.characterLockOrder);
        assertTrue(fixture.versionUpdateOrder.isEmpty());
    }

    @Test
    void rejectsMissingOrWrongCampaignCharactersWithoutWrites() throws Exception {
        Fixture missing = new Fixture();
        missing.resolved.remove(TARGET_B);
        CharacterVersionRepository.LockResult missingResult =
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        missing.connection(), command());
        assertEquals(CharacterVersionRepository.Status.CHARACTER_NOT_FOUND,
                missingResult.status());
        assertEquals(TARGET_B, missingResult.rejectedCharacterKey());
        assertTrue(missing.characterLockOrder.isEmpty());

        Fixture wrongCampaign = new Fixture();
        wrongCampaign.characters.get(10L).put("campaign_id", 8L);
        CharacterVersionRepository.LockResult invalidResult =
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        wrongCampaign.connection(), command());
        assertEquals(CharacterVersionRepository.Status.CHARACTER_INVALID,
                invalidResult.status());
        assertTrue(wrongCampaign.versionUpdateOrder.isEmpty());
    }

    @Test
    void rejectsSavedCharacterHashDriftAfterLockingAllRowsAndBeforeAnyWrite()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.characters.get(20L).put("saved_content_sha256", "0".repeat(64));

        CharacterVersionRepository.LockResult result =
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        fixture.connection(), command());

        assertEquals(CharacterVersionRepository.Status.MODULE_HASH_MISMATCH,
                result.status());
        assertEquals(null, result.rejectedCharacterKey());
        assertEquals(null, result.currentRowVersion());
        assertEquals(List.of(10L, 20L, 30L), fixture.characterLockOrder);
        assertTrue(fixture.versionUpdateOrder.isEmpty());
    }

    @Test
    void requiresWritableSerializableTransactionAndLockedVersionSubset() throws Exception {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;
        assertThrows(SQLException.class, () ->
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        fixture.connection(), command()));
        fixture.autoCommit = false;
        fixture.readOnly = true;
        assertThrows(SQLException.class, () ->
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        fixture.connection(), command()));
        fixture.readOnly = false;
        fixture.isolation = Connection.TRANSACTION_READ_COMMITTED;
        assertThrows(SQLException.class, () ->
                new JdbcCharacterVersionRepository().lockBeforeRoll(
                        fixture.connection(), command()));
        assertTrue(fixture.preparedSql.isEmpty());

        fixture.isolation = Connection.TRANSACTION_SERIALIZABLE;
        JdbcCharacterVersionRepository repository =
                new JdbcCharacterVersionRepository();
        CharacterVersionRepository.LockedScope scope =
                repository.lockBeforeRoll(fixture.connection(), command()).scope();
        assertThrows(IllegalArgumentException.class, () ->
                repository.advanceModifiedVersions(fixture.connection(), scope, Set.of(999L)));
        assertTrue(fixture.versionUpdateOrder.isEmpty());
    }

    private static CharacterVersionRepository.LockCommand command() {
        return new CharacterVersionRepository.LockCommand(
                7L, 11L,
                new CharacterVersionRepository.VersionExpectation(EXECUTOR, 2L),
                List.of(
                        new CharacterVersionRepository.VersionExpectation(TARGET_B, 6L),
                        new CharacterVersionRepository.VersionExpectation(TARGET_A, 4L)));
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<Long> characterLockOrder = new ArrayList<>();
        private final List<Long> versionUpdateOrder = new ArrayList<>();
        private final List<Long> versionExpectedValues = new ArrayList<>();
        private final LinkedHashMap<String, Long> resolved = new LinkedHashMap<>();
        private final Map<Long, Map<Object, Object>> characters = new HashMap<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean committed;
        private boolean rolledBack;

        private Fixture() {
            resolved.put(EXECUTOR, 30L);
            resolved.put(TARGET_A, 10L);
            resolved.put(TARGET_B, 20L);
            characters.put(10L, character(TARGET_A, 4L));
            characters.put(20L, character(TARGET_B, 6L));
            characters.put(30L, character(EXECUTOR, 2L));
        }

        private Connection connection() {
            return proxy(Connection.class, this::connectionCall);
        }

        private Object connectionCall(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "prepareStatement" -> statement((String) arguments[0]);
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql) {
            preparedSql.add(sql);
            Map<Integer, Object> bound = new HashMap<>();
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setLong", "setInt", "setString" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> query(sql, bound);
                        case "executeUpdate" -> update(sql, bound);
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet query(String sql, Map<Integer, Object> bound) {
            if (sql.contains("FROM campaign\n") && sql.contains("campaign_status")) {
                return rows(List.of(Map.of("internal_event_tail", 30L)));
            }
            if (sql.contains("FROM campaign_module")) {
                return rows(List.of(Map.of(
                        "module_release_id", 11L,
                        "frozen_module_key", MODULE_KEY,
                        "frozen_release_version", RELEASE_VERSION,
                        "frozen_content_sha256", SHA)));
            }
            if (sql.contains("character_key IN")) {
                List<Map<Object, Object>> result = new ArrayList<>();
                resolved.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry ->
                        result.add(Map.of("id", entry.getValue(), "character_key", entry.getKey())));
                return rows(result);
            }
            if (sql.contains("FROM character_record") && sql.contains("WHERE id = ?")
                    && sql.contains("FOR UPDATE")) {
                long id = (long) bound.get(1);
                characterLockOrder.add(id);
                Map<Object, Object> row = characters.get(id);
                return rows(row == null ? List.of() : List.of(row));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private int update(String sql, Map<Integer, Object> bound) {
            if (!sql.contains("SET row_version = row_version + 1")) {
                throw new AssertionError("Unexpected update: " + sql);
            }
            versionUpdateOrder.add((long) bound.get(1));
            versionExpectedValues.add((long) bound.get(2));
            return 1;
        }

        private static Map<Object, Object> character(String key, long version) {
            Map<Object, Object> row = new HashMap<>();
            row.put("character_key", key);
            row.put("campaign_id", 7L);
            row.put("module_release_id", 11L);
            row.put("character_status", "ACTIVE");
            row.put("row_version", version);
            row.put("saved_module_key", MODULE_KEY);
            row.put("saved_release_version", RELEASE_VERSION);
            row.put("saved_content_sha256", SHA);
            return row;
        }
    }

    private static ResultSet rows(List<Map<Object, Object>> rows) {
        int[] index = {-1};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (ignored, method, arguments) ->
                switch (method.getName()) {
                    case "next" -> ++index[0] < rows.size();
                    case "getString" -> {
                        Object value = rows.get(index[0]).get(arguments[0]);
                        wasNull[0] = value == null;
                        yield value == null ? null : value.toString();
                    }
                    case "getLong" -> {
                        Object value = rows.get(index[0]).get(arguments[0]);
                        wasNull[0] = value == null;
                        yield value == null ? 0L : ((Number) value).longValue();
                    }
                    case "wasNull" -> wasNull[0];
                    default -> defaultValue(method.getReturnType());
                });
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
