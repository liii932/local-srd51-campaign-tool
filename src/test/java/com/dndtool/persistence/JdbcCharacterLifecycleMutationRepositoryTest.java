package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CharacterLifecycleMutationRepository.Action;
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
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class JdbcCharacterLifecycleMutationRepositoryTest {
    private static final String HASH =
            "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e";
    private static final CharacterLifecycleMutationRepository.Command COMMAND =
            new CharacterLifecycleMutationRepository.Command(
                    "123e4567-e89b-42d3-a456-426614174000",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                    7,
                    Action.RENAME,
                    "Aria",
                    "dnd5e2014_srd51_se_v1",
                    "1",
                    HASH);

    @Test
    void updateVersionEventChangeAndIdempotencyCommitTogether() throws SQLException {
        JdbcFixture fixture = new JdbcFixture();
        JdbcCharacterLifecycleMutationRepository repository =
                new JdbcCharacterLifecycleMutationRepository(fixture.dataSource());

        CharacterLifecycleMutationRepository.Result result = repository.mutate(COMMAND);

        assertEquals(CharacterLifecycleMutationRepository.Status.UPDATED, result.status());
        assertEquals(8L, result.rowVersion());
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.containsSql("SET character_name = ?"));
        assertTrue(fixture.containsSql("SET internal_event_tail = ?"));
        assertTrue(fixture.containsSql("INSERT INTO game_event"));
        assertTrue(fixture.containsSql("INSERT INTO field_change"));
        assertTrue(fixture.containsSql("SET row_version = row_version + 1"));
        assertTrue(fixture.containsSql("INSERT INTO host_operation"));
        assertTrue(fixture.executedUpdates.stream()
                .filter(sql -> sql.stripLeading().startsWith("UPDATE character_record"))
                .allMatch(sql -> !sql.contains("character_key")));
        assertTrue(fixture.restored());
    }

    @Test
    void auditFailureRollsBackRootEventAndVersion() {
        JdbcFixture fixture = new JdbcFixture();
        fixture.failFieldChange = true;
        JdbcCharacterLifecycleMutationRepository repository =
                new JdbcCharacterLifecycleMutationRepository(fixture.dataSource());

        assertThrows(SQLException.class, () -> repository.mutate(COMMAND));

        assertFalse(fixture.committed);
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.containsSql("SET row_version = row_version + 1"));
        assertFalse(fixture.containsSql("INSERT INTO host_operation"));
        assertTrue(fixture.restored());
    }

    @Test
    void locksOnlyMutableRootsAndReadsFrozenDefinitionsWithoutForUpdate()
            throws SQLException {
        JdbcFixture fixture = new JdbcFixture();

        new JdbcCharacterLifecycleMutationRepository(fixture.dataSource()).mutate(COMMAND);

        String characterLock = fixture.preparedSql.stream()
                .filter(sql -> sql.contains("FROM character_record AS cr"))
                .findFirst().orElseThrow();
        String campaignLock = fixture.preparedSql.stream()
                .filter(sql -> sql.contains("FROM campaign") && sql.contains("FOR UPDATE"))
                .findFirst().orElseThrow();
        String bindingRead = fixture.preparedSql.stream()
                .filter(sql -> sql.contains("FROM campaign_module AS cm"))
                .findFirst().orElseThrow();
        assertTrue(characterLock.contains("FOR UPDATE"));
        assertFalse(characterLock.contains("campaign_module"));
        assertFalse(characterLock.contains("JOIN module_release"));
        assertTrue(campaignLock.contains("FOR UPDATE"));
        assertFalse(bindingRead.contains("FOR UPDATE"));
    }

    private static final class JdbcFixture {
        private final List<String> executedUpdates = new ArrayList<>();
        private final List<String> preparedSql = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_REPEATABLE_READ;
        private boolean committed;
        private boolean rolledBack;
        private boolean failFieldChange;

        private DataSource dataSource() {
            Connection connection = proxy(Connection.class, this::connectionCall);
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? connection : defaultValue(method.getReturnType()));
        }

        private Object connectionCall(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> { autoCommit = (boolean) arguments[0]; yield null; }
                case "isReadOnly" -> readOnly;
                case "setReadOnly" -> { readOnly = (boolean) arguments[0]; yield null; }
                case "getTransactionIsolation" -> isolation;
                case "setTransactionIsolation" -> {
                    isolation = (int) arguments[0];
                    yield null;
                }
                case "prepareStatement" -> statement((String) arguments[0]);
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql) {
            preparedSql.add(sql);
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "executeQuery" -> resultSet(queryRows(sql));
                        case "executeUpdate" -> {
                            executedUpdates.add(sql);
                            if (failFieldChange && sql.contains("INSERT INTO field_change")) {
                                throw new SQLException("synthetic audit failure");
                            }
                            yield 1;
                        }
                        case "getGeneratedKeys" -> resultSet(List.of(Map.of("1", 44L)));
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static List<Map<String, Object>> queryRows(String sql) {
            if (sql.contains("FROM host_operation")) return List.of();
            if (sql.contains("FROM character_record AS cr")) {
                return List.of(characterRow());
            }
            if (sql.contains("SELECT internal_event_tail FROM campaign")) {
                return List.of(Map.of("internal_event_tail", 5L));
            }
            if (sql.contains("FROM campaign_module AS cm")) {
                return List.of(bindingRow());
            }
            throw new AssertionError(sql);
        }

        private boolean containsSql(String fragment) {
            return executedUpdates.stream().anyMatch(sql -> sql.contains(fragment));
        }

        private boolean restored() {
            return autoCommit && !readOnly
                    && isolation == Connection.TRANSACTION_REPEATABLE_READ;
        }
    }

    private static Map<String, Object> characterRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 11L);
        row.put("campaign_id", 22L);
        row.put("module_release_id", 33L);
        row.put("character_name", "Old name");
        row.put("character_type", "PC");
        row.put("character_status", "ACTIVE");
        row.put("row_version", 7L);
        row.put("saved_module_key", "dnd5e2014_srd51_se_v1");
        row.put("saved_release_version", "1");
        row.put("saved_content_sha256", HASH);
        return row;
    }

    private static Map<String, Object> bindingRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("frozen_release_id", 33L);
        row.put("frozen_module_key", "dnd5e2014_srd51_se_v1");
        row.put("frozen_release_version", "1");
        row.put("frozen_content_sha256", HASH);
        row.put("module_key", "dnd5e2014_srd51_se_v1");
        row.put("release_version", "1");
        row.put("content_sha256", HASH);
        row.put("release_status", "RELEASED");
        return row;
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] index = {-1};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (ignored, method, arguments) ->
                switch (method.getName()) {
                    case "next" -> ++index[0] < rows.size();
                    case "getLong" -> {
                        String key = String.valueOf(arguments[0]);
                        Object value = rows.get(index[0]).get(key);
                        wasNull[0] = value == null;
                        yield value == null ? 0L : ((Number) value).longValue();
                    }
                    case "getString" -> {
                        Object value = rows.get(index[0]).get(String.valueOf(arguments[0]));
                        wasNull[0] = value == null;
                        yield value == null ? null : value.toString();
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
        if (type == char.class) return '\0';
        return 0;
    }
}
