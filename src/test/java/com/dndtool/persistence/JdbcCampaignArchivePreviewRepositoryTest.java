package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class JdbcCampaignArchivePreviewRepositoryTest {
    private static final String TARGET_KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String ACTIVE_KEY = "ffffffff-eeee-4ddd-8ccc-bbbbbbbbbbbb";

    @Test
    void readsNewTargetAndNoActiveCampaignWithoutLocksOrWrites() throws Exception {
        Fixture fixture = new Fixture();

        CampaignArchivePreviewRepository.Snapshot snapshot = repository(fixture)
                .inspect(TARGET_KEY);

        assertNull(snapshot.target());
        assertNull(snapshot.active());
        assertEquals(TARGET_KEY, fixture.boundKey);
        assertEquals(3, fixture.maxRows);
        assertTrue(fixture.readOnlyEnabled);
        assertTrue(fixture.repeatableReadEnabled);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.closed);
        assertTrue(fixture.sql.stream().noneMatch(value -> value.contains("FOR UPDATE")));
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    @Test
    void returnsExistingArchivedTargetAndDifferentActiveCampaign() throws Exception {
        Fixture fixture = new Fixture();
        fixture.rows = List.of(
                row(TARGET_KEY, "目标战役", "ARCHIVED"),
                row(ACTIVE_KEY, "当前战役", "ACTIVE"));

        CampaignArchivePreviewRepository.Snapshot snapshot = repository(fixture)
                .inspect(TARGET_KEY);

        assertEquals(TARGET_KEY, snapshot.target().campaignKey());
        assertEquals("ARCHIVED", snapshot.target().campaignStatus());
        assertEquals(ACTIVE_KEY, snapshot.active().campaignKey());
    }

    @Test
    void sameActiveTargetIsReturnedAsBothRoles() throws Exception {
        Fixture fixture = new Fixture();
        fixture.rows = List.of(row(TARGET_KEY, "目标战役", "ACTIVE"));

        CampaignArchivePreviewRepository.Snapshot snapshot = repository(fixture)
                .inspect(TARGET_KEY);

        assertEquals(snapshot.target(), snapshot.active());
    }

    @Test
    void rejectsMultipleActiveCampaignsAndRestoresConnection() {
        Fixture fixture = new Fixture();
        fixture.rows = List.of(
                row(TARGET_KEY, "目标战役", "ACTIVE"),
                row(ACTIVE_KEY, "另一战役", "ACTIVE"));

        assertThrows(SQLException.class, () -> repository(fixture).inspect(TARGET_KEY));

        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    @Test
    void queryFailureRollsBackAndInvalidKeyNeverOpensConnection() {
        Fixture failure = new Fixture();
        failure.failQuery = true;
        assertThrows(SQLException.class, () -> repository(failure).inspect(TARGET_KEY));
        assertTrue(failure.rolledBack);
        assertTrue(failure.closed);

        DataSource unused = proxy(DataSource.class, (ignored, method, arguments) -> {
            throw new AssertionError("invalid key must be rejected before JDBC access");
        });
        assertThrows(SQLException.class,
                () -> new JdbcCampaignArchivePreviewRepository(unused)
                        .inspect("not-a-campaign-key"));
    }

    private static JdbcCampaignArchivePreviewRepository repository(Fixture fixture) {
        return new JdbcCampaignArchivePreviewRepository(fixture.dataSource());
    }

    private static Map<String, Object> row(String key, String name, String status) {
        return Map.of(
                "campaign_key", key,
                "campaign_name", name,
                "campaign_status", status);
    }

    private static final class Fixture {
        private List<Map<String, Object>> rows = List.of();
        private final List<String> sql = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean readOnlyEnabled;
        private boolean repeatableReadEnabled;
        private boolean committed;
        private boolean rolledBack;
        private boolean closed;
        private boolean failQuery;
        private String boundKey;
        private int maxRows;

        private DataSource dataSource() {
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? connection() : defaultValue(method.getReturnType()));
        }

        private Connection connection() {
            return proxy(Connection.class, this::connectionCall);
        }

        private Object connectionCall(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) arguments[0];
                    yield null;
                }
                case "setReadOnly" -> {
                    readOnly = (boolean) arguments[0];
                    readOnlyEnabled |= readOnly;
                    yield null;
                }
                case "setTransactionIsolation" -> {
                    isolation = (int) arguments[0];
                    repeatableReadEnabled |= isolation == Connection.TRANSACTION_REPEATABLE_READ;
                    yield null;
                }
                case "prepareStatement" -> statement((String) arguments[0]);
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
            };
        }

        private PreparedStatement statement(String query) {
            sql.add(query);
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setString" -> {
                            boundKey = (String) arguments[1];
                            yield null;
                        }
                        case "setMaxRows" -> {
                            maxRows = (int) arguments[0];
                            yield null;
                        }
                        case "setQueryTimeout" -> null;
                        case "executeQuery" -> {
                            if (failQuery) throw new SQLException("synthetic preview read failure");
                            yield resultSet(rows);
                        }
                        case "executeUpdate" -> throw new AssertionError(
                                "Preview repository must never write");
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] index = {-1};
        return proxy(ResultSet.class, (ignored, method, arguments) ->
                switch (method.getName()) {
                    case "next" -> ++index[0] < rows.size();
                    case "getString" -> rows.get(index[0]).get(arguments[0]).toString();
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
        if (type == float.class || type == double.class) return 0.0;
        return 0;
    }
}
