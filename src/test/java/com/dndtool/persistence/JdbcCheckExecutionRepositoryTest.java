package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.D20CheckCalculator;
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

/** Verifies exact event, check and ordered-candidate writes without a real database. */
final class JdbcCheckExecutionRepositoryTest {
    private static final D20CheckCalculator.Result CALCULATION = new D20CheckCalculator.Result(
            "roll.advantage",
            List.of(
                    new D20CheckCalculator.Candidate(1, 7, false),
                    new D20CheckCalculator.Candidate(2, 16, true)),
            16,
            3,
            19,
            15,
            D20CheckCalculator.Outcome.SUCCESS);
    private static final CheckExecutionRepository.Command COMMAND =
            new CheckExecutionRepository.Command(
                    7L,
                    11L,
                    9L,
                    101L,
                    "event.ability_check",
                    "check.ability",
                    "ability.strength",
                    null,
                    CALCULATION);

    @Test
    void appendsEventCheckAndEveryCandidateInsideCallerTransaction() throws Exception {
        Fixture fixture = new Fixture();

        CheckExecutionRepository.SavedCheck saved =
                new JdbcCheckExecutionRepository().append(fixture.connection(), COMMAND);

        assertEquals(new CheckExecutionRepository.SavedCheck(202L, 303L, 12L), saved);
        assertEquals(List.of("campaign", "game_event", "check_execution", "dice_roll", "dice_roll"),
                fixture.targets);
        assertEquals(Map.of(1, 12L, 2, 7L, 3, 11L), fixture.values.get(0));
        assertEquals(202L, fixture.values.get(2).get(1));
        assertEquals("event.ability_check", fixture.values.get(2).get(5));
        assertEquals("check.ability", fixture.values.get(2).get(6));
        assertEquals("roll.advantage", fixture.values.get(2).get(7));
        assertEquals("ability.strength", fixture.values.get(2).get(8));
        assertEquals(null, fixture.values.get(2).get(9));
        assertEquals(3, fixture.values.get(2).get(10));
        assertEquals(19, fixture.values.get(2).get(11));
        assertEquals(15, fixture.values.get(2).get(12));
        assertEquals("SUCCESS", fixture.values.get(2).get(13));
        assertEquals(Map.of(1, 303L, 2, 1, 3, 7, 4, false), fixture.values.get(3));
        assertEquals(Map.of(1, 303L, 2, 2, 3, 16, 4, true), fixture.values.get(4));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void manualNullColumnsAreBoundAsSqlNulls() throws Exception {
        Fixture fixture = new Fixture();
        D20CheckCalculator.Result normal = new D20CheckCalculator.Result(
                "roll.normal",
                List.of(new D20CheckCalculator.Candidate(1, 9, true)),
                9, -2, 7, 10, D20CheckCalculator.Outcome.FAILURE);
        CheckExecutionRepository.Command manual = new CheckExecutionRepository.Command(
                7L, 0L, 9L, 101L, null, "check.manual", null, "撬锁", normal);

        new JdbcCheckExecutionRepository().append(fixture.connection(), manual);

        Map<Integer, Object> check = fixture.values.get(2);
        assertTrue(check.containsKey(5));
        assertEquals(null, check.get(5));
        assertTrue(check.containsKey(8));
        assertEquals(null, check.get(8));
        assertEquals("撬锁", check.get(9));
    }

    @Test
    void forgedCalculationIsRejectedBeforePreparingSql() {
        Fixture fixture = new Fixture();
        D20CheckCalculator.Result forged = new D20CheckCalculator.Result(
                "roll.normal",
                List.of(new D20CheckCalculator.Candidate(1, 20, true)),
                20, 0, 20, 20, D20CheckCalculator.Outcome.FAILURE);
        CheckExecutionRepository.Command command = commandWithCalculation(forged);
        D20CheckCalculator.Result forgedSelection = new D20CheckCalculator.Result(
                "roll.advantage",
                List.of(
                        new D20CheckCalculator.Candidate(1, 20, false),
                        new D20CheckCalculator.Candidate(2, 1, true)),
                1, 0, 1, 10, D20CheckCalculator.Outcome.FAILURE);

        assertThrows(IllegalArgumentException.class,
                () -> new JdbcCheckExecutionRepository().append(fixture.connection(), command));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcCheckExecutionRepository().append(
                        fixture.connection(), commandWithCalculation(forgedSelection)));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void requiresWritableSerializableCallerTransaction() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;

        assertThrows(SQLException.class,
                () -> new JdbcCheckExecutionRepository().append(fixture.connection(), COMMAND));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void staleEventTailStopsBeforeRootEventInsert() {
        Fixture fixture = new Fixture();
        fixture.staleTail = true;

        assertThrows(SQLException.class,
                () -> new JdbcCheckExecutionRepository().append(fixture.connection(), COMMAND));
        assertEquals(List.of("campaign"), fixture.targets);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void candidateInsertFailureIsLeftForCallerToRollBack() {
        Fixture fixture = new Fixture();
        fixture.failSecondDie = true;

        assertThrows(SQLException.class,
                () -> new JdbcCheckExecutionRepository().append(fixture.connection(), COMMAND));
        assertEquals(List.of("campaign", "game_event", "check_execution", "dice_roll", "dice_roll"),
                fixture.targets);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    private static CheckExecutionRepository.Command commandWithCalculation(
            D20CheckCalculator.Result calculation) {
        return new CheckExecutionRepository.Command(
                COMMAND.campaignId(),
                COMMAND.expectedEventTail(),
                COMMAND.moduleReleaseId(),
                COMMAND.executorCharacterId(),
                COMMAND.eventKey(),
                COMMAND.checkKey(),
                COMMAND.modifierSourceKey(),
                COMMAND.manualName(),
                calculation);
    }

    private static final class Fixture {
        private final List<String> targets = new ArrayList<>();
        private final List<String> preparedSql = new ArrayList<>();
        private final List<Map<Integer, Object>> values = new ArrayList<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean staleTail;
        private boolean failSecondDie;
        private int dieInsertCount;
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
                        case "setLong", "setInt", "setString", "setBoolean" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "setNull" -> {
                            bound.put((int) arguments[0], null);
                            yield null;
                        }
                        case "executeUpdate" -> executeUpdate(sql, bound);
                        case "getGeneratedKeys" -> generated
                                ? resultSet(sql.stripLeading().startsWith("INSERT INTO game_event ")
                                        ? 202L : 303L)
                                : resultSet();
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private int executeUpdate(String sql, Map<Integer, Object> bound) throws SQLException {
            String target = target(sql);
            targets.add(target);
            values.add(new HashMap<>(bound));
            if (staleTail && "campaign".equals(target)) return 0;
            if ("dice_roll".equals(target) && failSecondDie && ++dieInsertCount == 2) {
                throw new SQLException("synthetic candidate failure");
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

        private static String target(String sql) {
            String normalized = sql.stripLeading();
            if (normalized.startsWith("UPDATE campaign")) return "campaign";
            int start = "INSERT INTO ".length();
            int end = normalized.indexOf(' ', start);
            return normalized.substring(start, end);
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
