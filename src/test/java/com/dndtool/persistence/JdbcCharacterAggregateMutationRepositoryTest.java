package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class JdbcCharacterAggregateMutationRepositoryTest {
    private static final String CHARACTER_KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final CharacterAggregateMutationRepository.Command COMMAND =
            new CharacterAggregateMutationRepository.Command(CHARACTER_KEY, 7);

    @Test
    void appliesChildWritesAndAdvancesRootVersionOnceInOneTransaction() throws SQLException {
        JdbcFixture fixture = new JdbcFixture();
        fixture.characterRows = List.of(characterRow(7));
        JdbcCharacterAggregateMutationRepository repository =
                new JdbcCharacterAggregateMutationRepository(fixture.dataSource());

        CharacterAggregateMutationRepository.Result result = repository.mutate(
                COMMAND, (connection, character) -> {
                    assertSame(fixture.connection, connection);
                    assertEquals(11, character.id());
                    assertEquals(22, character.campaignId());
                    assertEquals(33, character.moduleReleaseId());
                    fixture.mutationCalls++;
                });

        assertEquals(CharacterAggregateMutationRepository.Result.Status.UPDATED, result.status());
        assertEquals(8L, result.rowVersion());
        assertEquals(1, fixture.mutationCalls);
        assertEquals(1, fixture.versionUpdateCalls);
        assertEquals(CHARACTER_KEY, fixture.versionUpdateValues.get(1));
        assertEquals(7L, fixture.versionUpdateValues.get(2));
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.connectionClosed);
        assertTrue(fixture.restored());
    }

    @Test
    void staleVersionRollsBackWithoutCallingMutationOrAdvancingVersion() throws SQLException {
        JdbcFixture fixture = new JdbcFixture();
        fixture.characterRows = List.of(characterRow(8));
        JdbcCharacterAggregateMutationRepository repository =
                new JdbcCharacterAggregateMutationRepository(fixture.dataSource());

        CharacterAggregateMutationRepository.Result result = repository.mutate(
                COMMAND, (connection, character) -> fixture.mutationCalls++);

        assertEquals(
                CharacterAggregateMutationRepository.Result.Status.VERSION_CONFLICT,
                result.status());
        assertEquals(8L, result.rowVersion());
        assertEquals(0, fixture.mutationCalls);
        assertEquals(0, fixture.versionUpdateCalls);
        assertFalse(fixture.committed);
        assertTrue(fixture.rolledBack);
        assertTrue(fixture.restored());
    }

    @Test
    void missingCharacterRollsBackWithoutCallingMutation() throws SQLException {
        JdbcFixture fixture = new JdbcFixture();
        fixture.characterRows = List.of();
        JdbcCharacterAggregateMutationRepository repository =
                new JdbcCharacterAggregateMutationRepository(fixture.dataSource());

        CharacterAggregateMutationRepository.Result result = repository.mutate(
                COMMAND, (connection, character) -> fixture.mutationCalls++);

        assertEquals(CharacterAggregateMutationRepository.Result.Status.NOT_FOUND, result.status());
        assertEquals(0, fixture.mutationCalls);
        assertEquals(0, fixture.versionUpdateCalls);
        assertTrue(fixture.rolledBack);
        assertTrue(fixture.restored());
    }

    @Test
    void mutationFailureRollsBackBeforeVersionAdvance() {
        JdbcFixture fixture = new JdbcFixture();
        fixture.characterRows = List.of(characterRow(7));
        JdbcCharacterAggregateMutationRepository repository =
                new JdbcCharacterAggregateMutationRepository(fixture.dataSource());

        assertThrows(SQLException.class, () -> repository.mutate(COMMAND,
                (connection, character) -> {
                    fixture.mutationCalls++;
                    throw new SQLException("synthetic child write failure");
                }));

        assertEquals(1, fixture.mutationCalls);
        assertEquals(0, fixture.versionUpdateCalls);
        assertFalse(fixture.committed);
        assertTrue(fixture.rolledBack);
        assertTrue(fixture.restored());
    }

    @Test
    void unexpectedVersionUpdateCountRollsBackTheWholeMutation() {
        JdbcFixture fixture = new JdbcFixture();
        fixture.characterRows = List.of(characterRow(7));
        fixture.versionUpdateCount = 0;
        JdbcCharacterAggregateMutationRepository repository =
                new JdbcCharacterAggregateMutationRepository(fixture.dataSource());

        assertThrows(SQLException.class, () -> repository.mutate(
                COMMAND, (connection, character) -> fixture.mutationCalls++));

        assertEquals(1, fixture.mutationCalls);
        assertEquals(1, fixture.versionUpdateCalls);
        assertFalse(fixture.committed);
        assertTrue(fixture.rolledBack);
        assertTrue(fixture.restored());
    }

    private static Map<String, Object> characterRow(long rowVersion) {
        return Map.of(
                "id", 11L,
                "campaign_id", 22L,
                "module_release_id", 33L,
                "row_version", rowVersion);
    }

    private static final class JdbcFixture {
        private List<Map<String, Object>> characterRows = List.of();
        private final Map<Integer, Object> versionUpdateValues = new HashMap<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_REPEATABLE_READ;
        private boolean committed;
        private boolean rolledBack;
        private boolean connectionClosed;
        private int mutationCalls;
        private int versionUpdateCalls;
        private int versionUpdateCount = 1;
        private Connection connection;

        private DataSource dataSource() {
            connection = proxy(Connection.class, this::connectionCall);
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
                case "close" -> { connectionClosed = true; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql) {
            Map<Integer, Object> values = new HashMap<>();
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setString", "setLong" -> {
                            values.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> resultSet(characterRows);
                        case "executeUpdate" -> {
                            if (!sql.contains("SET row_version = row_version + 1")
                                    || !sql.contains("character_key = ? AND row_version = ?")) {
                                throw new AssertionError("Unexpected update: " + sql);
                            }
                            versionUpdateCalls++;
                            versionUpdateValues.putAll(values);
                            yield versionUpdateCount;
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "getLong" -> {
                            Object value = rows.get(index[0]).get((String) arguments[0]);
                            wasNull[0] = value == null;
                            yield value == null ? 0L : ((Number) value).longValue();
                        }
                        case "wasNull" -> wasNull[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private boolean restored() {
            return autoCommit
                    && !readOnly
                    && isolation == Connection.TRANSACTION_REPEATABLE_READ;
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
