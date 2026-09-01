package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.CheckRequestPolicy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
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

/** Verifies both immutable branches and physical parameter bindings without a real database. */
final class JdbcCheckEffectPlanRepositoryTest {
    private static final CheckRequestPolicy.PreparedEffect SUCCESS_EFFECT = new CheckRequestPolicy.PreparedEffect(
            "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                    parameter("amount", 1, "INTEGER", new CheckRequestPolicy.IntegerValue(-2)),
                    parameter("target", 2, "REFERENCE", new CheckRequestPolicy.ReferenceValue(
                            "11111111-1111-1111-1111-111111111111"))));
    private static final CheckRequestPolicy.PreparedEffect FAILURE_EFFECT = new CheckRequestPolicy.PreparedEffect(
            "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1", List.of(
                    parameter("message", 1, "TEXT", new CheckRequestPolicy.TextValue("失败消息"))));
    private static final CheckRequestPolicy.PreparedEffect ALL_TYPES_EFFECT = new CheckRequestPolicy.PreparedEffect(
            "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1", List.of(
                    parameter("reference", 1, "REFERENCE", new CheckRequestPolicy.ReferenceValue("item.key")),
                    parameter("count", 2, "INTEGER", new CheckRequestPolicy.IntegerValue(2)),
                    parameter("weight", 3, "DECIMAL", new CheckRequestPolicy.DecimalValue(new BigDecimal("1.250"))),
                    parameter("label", 4, "TEXT", new CheckRequestPolicy.TextValue("钥匙")),
                    parameter("enabled", 5, "BOOLEAN", new CheckRequestPolicy.BooleanValue(true))));

    @Test
    void persistsSuccessAndFailurePlansInOrderedTypedRows() throws Exception {
        Fixture fixture = new Fixture();
        CheckEffectPlanRepository.Command command = new CheckEffectPlanRepository.Command(
                303L, 9L,
                new CheckEffectPlanRepository.BranchPlan(
                        CheckEffectPlanRepository.EffectBranch.SUCCESS,
                        List.of(new CheckEffectPlanRepository.EffectPlan(1, SUCCESS_EFFECT))),
                new CheckEffectPlanRepository.BranchPlan(
                        CheckEffectPlanRepository.EffectBranch.FAILURE,
                        List.of(new CheckEffectPlanRepository.EffectPlan(1, FAILURE_EFFECT),
                                new CheckEffectPlanRepository.EffectPlan(2, ALL_TYPES_EFFECT))));

        CheckEffectPlanRepository.SavedPlan saved =
                new JdbcCheckEffectPlanRepository().append(fixture.connection(), command);

        assertEquals(303L, saved.checkExecutionId());
        assertEquals(List.of(501L, 502L, 503L), saved.effects().stream()
                .map(CheckEffectPlanRepository.SavedEffect::checkEffectId).toList());
        assertEquals(List.of("check_effect", "check_effect_parameter_value", "check_effect_parameter_value",
                "check_effect", "check_effect_parameter_value", "check_effect", "check_effect_parameter_value",
                "check_effect_parameter_value", "check_effect_parameter_value", "check_effect_parameter_value",
                "check_effect_parameter_value"), fixture.targets);
        assertEquals("SUCCESS", fixture.values.get(0).get(3));
        assertEquals(1, fixture.values.get(0).get(4));
        assertEquals("effect.adjust_current_hp", fixture.values.get(0).get(5));
        Map<Integer, Object> integer = fixture.values.get(1);
        assertEquals("INTEGER", integer.get(6));
        assertEquals(-2L, integer.get(8));
        assertEquals(null, integer.get(7));
        assertEquals(null, integer.get(9));
        Map<Integer, Object> text = fixture.values.get(4);
        assertEquals("TEXT", text.get(6));
        assertEquals("失败消息", text.get(10));
        Map<Integer, Object> decimal = fixture.values.get(8);
        assertEquals(new BigDecimal("1.250"), decimal.get(9));
        Map<Integer, Object> bool = fixture.values.get(10);
        assertEquals(true, bool.get(11));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.preparedSql.stream().noneMatch(sql ->
                sql.toUpperCase().contains("JSON")
                        || sql.toLowerCase().contains("script")
                        || sql.toLowerCase().contains("applied")));
    }

    @Test
    void rejectsNonContiguousDuplicateAndMalformedPlansBeforeSql() {
        Fixture fixture = new Fixture();
        CheckEffectPlanRepository.BranchPlan gap = new CheckEffectPlanRepository.BranchPlan(
                CheckEffectPlanRepository.EffectBranch.SUCCESS,
                List.of(new CheckEffectPlanRepository.EffectPlan(2, SUCCESS_EFFECT)));
        assertThrows(IllegalArgumentException.class, () -> append(fixture, gap, emptyFailure()));

        CheckRequestPolicy.PreparedEffect duplicateParameters = new CheckRequestPolicy.PreparedEffect(
                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                        parameter("amount", 1, "INTEGER", new CheckRequestPolicy.IntegerValue(1)),
                        parameter("amount", 2, "INTEGER", new CheckRequestPolicy.IntegerValue(2))));
        assertThrows(IllegalArgumentException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, duplicateParameters), emptyFailure()));

        CheckRequestPolicy.PreparedEffect mismatch = new CheckRequestPolicy.PreparedEffect(
                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                        parameter("amount", 1, "TEXT", new CheckRequestPolicy.IntegerValue(1))));
        assertThrows(IllegalArgumentException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, mismatch), emptyFailure()));
        CheckEffectPlanRepository.Command mismatchedBranches = new CheckEffectPlanRepository.Command(
                303L, 9L,
                new CheckEffectPlanRepository.BranchPlan(
                        CheckEffectPlanRepository.EffectBranch.FAILURE, List.of()),
                emptyFailure());
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcCheckEffectPlanRepository().append(fixture.connection(), mismatchedBranches));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void rejectsRepeatedMessageEffectInOneBranch() {
        Fixture fixture = new Fixture();
        CheckEffectPlanRepository.BranchPlan duplicateMessages = new CheckEffectPlanRepository.BranchPlan(
                CheckEffectPlanRepository.EffectBranch.SUCCESS,
                List.of(new CheckEffectPlanRepository.EffectPlan(1, FAILURE_EFFECT),
                        new CheckEffectPlanRepository.EffectPlan(2, FAILURE_EFFECT)));
        assertThrows(IllegalArgumentException.class, () -> append(fixture, duplicateMessages, emptyFailure()));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void allowsRepeatedNonSingletonEffectsForDifferentTargets() throws Exception {
        Fixture fixture = new Fixture();
        CheckEffectPlanRepository.BranchPlan repeated = new CheckEffectPlanRepository.BranchPlan(
                CheckEffectPlanRepository.EffectBranch.SUCCESS,
                List.of(new CheckEffectPlanRepository.EffectPlan(1, SUCCESS_EFFECT),
                        new CheckEffectPlanRepository.EffectPlan(2, SUCCESS_EFFECT)));

        CheckEffectPlanRepository.SavedPlan saved = append(fixture, repeated, emptyFailure());

        assertEquals(2, saved.effects().size());
        assertEquals(List.of(1, 2), saved.effects().stream()
                .map(CheckEffectPlanRepository.SavedEffect::effectOrder).toList());
    }

    @Test
    void rejectsInvalidReferenceTextAndDecimalValues() {
        Fixture fixture = new Fixture();
        CheckRequestPolicy.PreparedEffect badReference = new CheckRequestPolicy.PreparedEffect(
                "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1", List.of(
                        parameter("reference", 1, "REFERENCE", new CheckRequestPolicy.ReferenceValue("Not A Key"))));
        assertThrows(IllegalArgumentException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, badReference), emptyFailure()));
        CheckRequestPolicy.PreparedEffect badText = new CheckRequestPolicy.PreparedEffect(
                "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1", List.of(
                        parameter("message", 1, "TEXT", new CheckRequestPolicy.TextValue("e\u0301"))));
        assertThrows(IllegalArgumentException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, badText), emptyFailure()));
        CheckRequestPolicy.PreparedEffect badDecimal = new CheckRequestPolicy.PreparedEffect(
                "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1", List.of(
                        parameter("weight", 1, "DECIMAL", new CheckRequestPolicy.DecimalValue(
                                new BigDecimal("123456789012345678901234567890123456789")))));
        assertThrows(IllegalArgumentException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, badDecimal), emptyFailure()));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void requiresWritableSerializableCallerTransactionAndLeavesRollbackToCaller() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;
        assertThrows(SQLException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, SUCCESS_EFFECT), emptyFailure()));
        fixture.autoCommit = false;
        fixture.readOnly = true;
        assertThrows(SQLException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, SUCCESS_EFFECT), emptyFailure()));
        fixture.readOnly = false;
        fixture.isolation = Connection.TRANSACTION_READ_COMMITTED;
        assertThrows(SQLException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, SUCCESS_EFFECT), emptyFailure()));
        assertTrue(fixture.preparedSql.isEmpty());
        fixture.isolation = Connection.TRANSACTION_SERIALIZABLE;
        fixture.failParameter = true;
        assertThrows(SQLException.class, () -> append(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, SUCCESS_EFFECT), emptyFailure()));
        assertTrue(fixture.targets.contains("check_effect_parameter_value"));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    private static CheckEffectPlanRepository.SavedPlan append(
            Fixture fixture,
            CheckEffectPlanRepository.BranchPlan success,
            CheckEffectPlanRepository.BranchPlan failure) throws SQLException {
        return new JdbcCheckEffectPlanRepository().append(
                fixture.connection(), new CheckEffectPlanRepository.Command(303L, 9L, success, failure));
    }

    private static CheckEffectPlanRepository.BranchPlan branch(
            CheckEffectPlanRepository.EffectBranch branch,
            CheckRequestPolicy.PreparedEffect effect) {
        return new CheckEffectPlanRepository.BranchPlan(
                branch, List.of(new CheckEffectPlanRepository.EffectPlan(1, effect)));
    }

    private static CheckEffectPlanRepository.BranchPlan emptyFailure() {
        return new CheckEffectPlanRepository.BranchPlan(
                CheckEffectPlanRepository.EffectBranch.FAILURE, List.of());
    }

    private static CheckRequestPolicy.PreparedParameter parameter(
            String key, int order, String type, CheckRequestPolicy.Value value) {
        return new CheckRequestPolicy.PreparedParameter(key, order, type, value);
    }

    private static final class Fixture {
        private final List<String> targets = new ArrayList<>();
        private final List<String> preparedSql = new ArrayList<>();
        private final List<Map<Integer, Object>> values = new ArrayList<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean committed;
        private boolean rolledBack;
        private boolean failParameter;
        private long nextEffectId = 501L;
        private long nextParameterId = 801L;

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
                        case "setLong", "setInt", "setString", "setBoolean", "setBigDecimal" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "setNull" -> {
                            bound.put((int) arguments[0], null);
                            yield null;
                        }
                        case "executeUpdate" -> {
                            String target = target(sql);
                            targets.add(target);
                            values.add(new HashMap<>(bound));
                            if (failParameter && "check_effect_parameter_value".equals(target)) {
                                throw new SQLException("synthetic parameter failure");
                            }
                            yield 1;
                        }
                        case "getGeneratedKeys" -> {
                            String target = target(sql);
                            yield resultSet("check_effect".equals(target) ? nextEffectId++ : nextParameterId++);
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static ResultSet resultSet(long id) {
            int[] index = {-1};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] == 0;
                        case "getLong" -> id;
                        case "wasNull" -> false;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static String target(String sql) {
            String normalized = sql.stripLeading();
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
        if (type == byte.class || type == short.class || type == int.class || type == long.class) return 0;
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
