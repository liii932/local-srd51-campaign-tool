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

/** Proves that a note writes only a campaign tail and one root message event. */
final class JdbcNoteEventRepositoryTest {
    private static final NoteEventRepository.Command COMMAND =
            new NoteEventRepository.Command(7L, 4L, "门后传来脚步声");

    @Test
    void appendsOnlyOneRootMessageEvent() throws Exception {
        Fixture fixture = new Fixture();

        NoteEventRepository.SavedNote saved =
                new JdbcNoteEventRepository().append(fixture.connection(), COMMAND);

        assertEquals(new NoteEventRepository.SavedNote(202L, 5L), saved);
        assertEquals(List.of("campaign", "game_event"), fixture.targets);
        assertEquals(Map.of(1, 5L, 2, 7L, 3, 4L), fixture.executions.get(0));
        assertEquals(Map.of(1, 7L, 2, 5L, 3, "门后传来脚步声"),
                fixture.executions.get(1));
        assertTrue(fixture.preparedSql.stream().noneMatch(sql ->
                sql.contains("check_execution")
                        || sql.contains("dice_roll")
                        || sql.contains("check_effect")));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void rejectsUnnormalizedMessageBeforePreparingSql() {
        Fixture fixture = new Fixture();
        NoteEventRepository.Command unnormalized =
                new NoteEventRepository.Command(7L, 0L, "cafe\u0301");

        assertThrows(IllegalArgumentException.class,
                () -> new JdbcNoteEventRepository().append(
                        fixture.connection(), unnormalized));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void requiresWritableSerializableCallerTransaction() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;

        assertThrows(SQLException.class,
                () -> new JdbcNoteEventRepository().append(fixture.connection(), COMMAND));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void staleTailStopsBeforeEventInsert() {
        Fixture fixture = new Fixture();
        fixture.staleTail = true;

        assertThrows(SQLException.class,
                () -> new JdbcNoteEventRepository().append(fixture.connection(), COMMAND));
        assertEquals(List.of("campaign"), fixture.targets);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void eventInsertFailureIsLeftForCallerToRollBack() {
        Fixture fixture = new Fixture();
        fixture.failEvent = true;

        assertThrows(SQLException.class,
                () -> new JdbcNoteEventRepository().append(fixture.connection(), COMMAND));
        assertEquals(List.of("campaign", "game_event"), fixture.targets);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<String> targets = new ArrayList<>();
        private final List<Map<Integer, Object>> executions = new ArrayList<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean staleTail;
        private boolean failEvent;
        private boolean committed;
        private boolean rolledBack;

        private Connection connection() {
            return proxy(Connection.class, this::connectionCall);
        }

        private Object connectionCall(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "prepareStatement" -> statement((String) arguments[0], arguments);
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql, Object[] prepareArguments) {
            preparedSql.add(sql);
            Map<Integer, Object> bound = new HashMap<>();
            boolean generated = prepareArguments != null
                    && prepareArguments.length > 1
                    && Integer.valueOf(Statement.RETURN_GENERATED_KEYS).equals(prepareArguments[1]);
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setLong", "setString" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeUpdate" -> executeUpdate(sql, bound);
                        case "getGeneratedKeys" -> generated ? resultSet(202L) : resultSet();
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private int executeUpdate(String sql, Map<Integer, Object> bound) throws SQLException {
            String target = sql.stripLeading().startsWith("UPDATE campaign")
                    ? "campaign" : "game_event";
            targets.add(target);
            executions.add(new HashMap<>(bound));
            if (staleTail && "campaign".equals(target)) return 0;
            if (failEvent && "game_event".equals(target)) {
                throw new SQLException("synthetic event failure");
            }
            return 1;
        }

        private static ResultSet resultSet(long... ids) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < ids.length;
                        case "getLong" -> {
                            long value = ids[index[0]];
                            wasNull[0] = false;
                            yield value;
                        }
                        case "wasNull" -> wasNull[0];
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
