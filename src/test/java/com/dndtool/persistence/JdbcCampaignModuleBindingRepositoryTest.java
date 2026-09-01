package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Verifies the minimal read-only binding projection and deterministic resource closure. */
final class JdbcCampaignModuleBindingRepositoryTest {
    @Test
    void loadsBindingsIncludingBrokenLeftJoinWithoutPrivateCampaignData() throws Exception {
        Fixture fixture = new Fixture(List.of(
                Map.of(
                        "campaign_key", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                        "campaign_status", "ACTIVE",
                        "frozen_module_key", "dnd5e2014_srd51_se_v1",
                        "frozen_release_version", "1",
                        "frozen_content_sha256", "a".repeat(64)),
                new java.util.HashMap<>(Map.of(
                        "campaign_key", "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff",
                        "campaign_status", "ARCHIVED"))));

        List<CampaignModuleBindingRepository.Binding> bindings =
                new JdbcCampaignModuleBindingRepository(fixture.dataSource()).findAll();

        assertEquals(2, bindings.size());
        assertEquals("dnd5e2014_srd51_se_v1", bindings.get(0).frozenModuleKey());
        assertEquals(null, bindings.get(1).frozenModuleKey());
        assertTrue(fixture.sql.contains("LEFT JOIN campaign_module"));
        assertTrue(fixture.sql.contains("ORDER BY c.id"));
        assertEquals(64, fixture.fetchSize);
        assertEquals(5, fixture.queryTimeout);
        assertTrue(fixture.resultClosed && fixture.statementClosed && fixture.connectionClosed);
    }

    private static final class Fixture {
        private final List<Map<String, Object>> rows;
        private String sql;
        private int fetchSize;
        private int queryTimeout;
        private boolean connectionClosed;
        private boolean statementClosed;
        private boolean resultClosed;

        private Fixture(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        private DataSource dataSource() {
            Connection connection = proxy(Connection.class, (ignored, method, arguments) -> {
                if ("prepareStatement".equals(method.getName())) {
                    sql = (String) arguments[0];
                    return statement();
                }
                if ("close".equals(method.getName())) connectionClosed = true;
                return defaultValue(method);
            });
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? connection : defaultValue(method));
        }

        private PreparedStatement statement() {
            return proxy(PreparedStatement.class, (ignored, method, arguments) -> {
                return switch (method.getName()) {
                    case "setFetchSize" -> { fetchSize = (int) arguments[0]; yield null; }
                    case "setQueryTimeout" -> { queryTimeout = (int) arguments[0]; yield null; }
                    case "executeQuery" -> resultSet();
                    case "close" -> { statementClosed = true; yield null; }
                    default -> defaultValue(method);
                };
            });
        }

        private ResultSet resultSet() {
            int[] index = {-1};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "getString" -> rows.get(index[0]).get((String) arguments[0]);
                        case "close" -> { resultClosed = true; yield null; }
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
