package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class JdbcHostStateEpochRepositoryTest {
    @Test
    void returnsZeroWithoutAnActiveCampaignAndReturnsTheSingleStoredEpoch()
            throws Exception {
        Fixture empty = new Fixture(List.of());
        assertEquals(0L, new JdbcHostStateEpochRepository(empty.dataSource())
                .currentActiveEpoch());
        assertTrue(empty.closed);

        Fixture active = new Fixture(List.of(73L));
        assertEquals(73L, new JdbcHostStateEpochRepository(active.dataSource())
                .currentActiveEpoch());
        assertEquals(2, active.maxRows);
        assertEquals(5, active.queryTimeout);
        assertTrue(active.sql.contains("WHERE campaign_status = 'ACTIVE'"));
        assertTrue(active.sql.contains("ORDER BY id"));
        assertTrue(active.closed);
    }

    @Test
    void failsClosedForInvalidOrMultipleActiveEpochs() {
        assertThrows(SQLException.class, () -> new JdbcHostStateEpochRepository(
                new Fixture(List.of(-1L)).dataSource()).currentActiveEpoch());
        assertThrows(SQLException.class, () -> new JdbcHostStateEpochRepository(
                new Fixture(List.of(1L, 2L)).dataSource()).currentActiveEpoch());
    }

    private static final class Fixture {
        private final List<Long> values;
        private String sql;
        private int maxRows;
        private int queryTimeout;
        private boolean closed;

        private Fixture(List<Long> values) {
            this.values = values;
        }

        private DataSource dataSource() {
            ResultSet result = resultSet();
            PreparedStatement statement = proxy(PreparedStatement.class,
                    (ignored, method, arguments) -> switch (method.getName()) {
                        case "setMaxRows" -> { maxRows = (int) arguments[0]; yield null; }
                        case "setQueryTimeout" -> {
                            queryTimeout = (int) arguments[0];
                            yield null;
                        }
                        case "executeQuery" -> result;
                        default -> defaultValue(method);
                    });
            Connection connection = proxy(Connection.class,
                    (ignored, method, arguments) -> switch (method.getName()) {
                        case "prepareStatement" -> {
                            sql = arguments[0].toString().replaceAll("\\s+", " ").trim();
                            yield statement;
                        }
                        case "close" -> { closed = true; yield null; }
                        default -> defaultValue(method);
                    });
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? connection : defaultValue(method));
        }

        private ResultSet resultSet() {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < values.size();
                        case "getLong" -> {
                            Long value = values.get(index[0]);
                            wasNull[0] = value == null;
                            yield value == null ? 0L : value;
                        }
                        case "wasNull" -> wasNull[0];
                        default -> defaultValue(method);
                    });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == float.class || type == double.class) return 0.0;
        return 0;
    }
}
