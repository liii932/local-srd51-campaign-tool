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
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcCampaignCreationRepositoryTest {
    private static final String MODULE_SHA =
            "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e";
    private static final CampaignCreationRepository.Command COMMAND =
            new CampaignCreationRepository.Command(
                    "123e4567-e89b-12d3-a456-426614174000",
                    "1".repeat(64),
                    "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                    "测试战役",
                    "dnd5e2014_srd51_se_v1",
                    "1",
                    MODULE_SHA);

    @Test
    void atomicallyCreatesCampaignSingleFrozenBindingAndSucceededOperation()
            throws Exception {
        JdbcFixture fixture = new JdbcFixture();

        CampaignCreationRepository.Result result =
                new JdbcCampaignCreationRepository(fixture.dataSource()).create(COMMAND);

        assertEquals(CampaignCreationRepository.Result.Status.CREATED, result.status());
        assertEquals(COMMAND.campaignKey(), result.campaignKey());
        assertEquals(List.of("campaign", "campaign_module", "host_operation"),
                fixture.insertTargets);
        assertEquals(1, fixture.insertTargets.stream()
                .filter("campaign_module"::equals).count());
        assertEquals(MODULE_SHA, fixture.campaignModuleValues.get(5));
        assertEquals("CREATE_CAMPAIGN", fixture.operationValues.get(3));
        assertTrue(fixture.serializableEnabled);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertRestoredAndClosed(fixture);
    }

    @Test
    void missingOrChangedReleaseRollsBackWithoutBusinessInserts() throws Exception {
        JdbcFixture missing = new JdbcFixture();
        missing.releaseRows = List.of();
        CampaignCreationRepository.Result missingResult =
                new JdbcCampaignCreationRepository(missing.dataSource()).create(COMMAND);
        assertEquals(CampaignCreationRepository.Result.Status.RELEASE_UNAVAILABLE,
                missingResult.status());
        assertTrue(missing.insertTargets.isEmpty());
        assertTrue(missing.rolledBack);
        assertRestoredAndClosed(missing);

        JdbcFixture changed = new JdbcFixture();
        changed.releaseRows = List.of(releaseRow("0".repeat(64)));
        CampaignCreationRepository.Result changedResult =
                new JdbcCampaignCreationRepository(changed.dataSource()).create(COMMAND);
        assertEquals(CampaignCreationRepository.Result.Status.RELEASE_UNAVAILABLE,
                changedResult.status());
        assertTrue(changed.insertTargets.isEmpty());
        assertTrue(changed.rolledBack);
    }

    @Test
    void existingRequestReplaysOnlyMatchingSucceededOperation() throws Exception {
        JdbcFixture replay = new JdbcFixture();
        replay.operationRows = List.of(operationRow(COMMAND.requestDigestSha256(),
                "CREATE_CAMPAIGN", 42L, "SUCCEEDED"));
        replay.campaignRows = List.of(Map.of("campaign_key", COMMAND.campaignKey()));
        CampaignCreationRepository.Result replayed =
                new JdbcCampaignCreationRepository(replay.dataSource()).create(COMMAND);
        assertEquals(CampaignCreationRepository.Result.Status.ALREADY_SUCCEEDED,
                replayed.status());
        assertEquals(COMMAND.campaignKey(), replayed.campaignKey());
        assertTrue(replay.insertTargets.isEmpty());
        assertTrue(replay.committed);

        JdbcFixture conflict = new JdbcFixture();
        conflict.operationRows = List.of(operationRow("2".repeat(64),
                "CREATE_CAMPAIGN", 42L, "SUCCEEDED"));
        CampaignCreationRepository.Result conflicted =
                new JdbcCampaignCreationRepository(conflict.dataSource()).create(COMMAND);
        assertEquals(CampaignCreationRepository.Result.Status.IDEMPOTENCY_CONFLICT,
                conflicted.status());
        assertTrue(conflict.insertTargets.isEmpty());
    }

    @Test
    void activeCampaignBlocksCreationAfterReleaseLock() throws Exception {
        JdbcFixture fixture = new JdbcFixture();
        fixture.activeCampaignRows = List.of(Map.of("id", 99L));

        CampaignCreationRepository.Result result =
                new JdbcCampaignCreationRepository(fixture.dataSource()).create(COMMAND);

        assertEquals(CampaignCreationRepository.Result.Status.ACTIVE_CAMPAIGN_EXISTS,
                result.status());
        assertTrue(fixture.insertTargets.isEmpty());
        assertTrue(fixture.rolledBack);
        assertRestoredAndClosed(fixture);
    }

    @Test
    void insertFailureRollsBackAndRestoresPooledConnection() {
        JdbcFixture fixture = new JdbcFixture();
        fixture.failUpdateNumber = 2;

        assertThrows(SQLException.class,
                () -> new JdbcCampaignCreationRepository(fixture.dataSource()).create(COMMAND));

        assertEquals(List.of("campaign", "campaign_module"), fixture.insertTargets);
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        assertRestoredAndClosed(fixture);
    }

    @Test
    void rejectsNonVersionFourCampaignKeyBeforeOpeningAConnection() {
        CampaignCreationRepository.Command invalid = new CampaignCreationRepository.Command(
                COMMAND.requestId(), COMMAND.requestDigestSha256(),
                "aaaaaaaa-bbbb-1ccc-8ddd-eeeeeeeeeeee", COMMAND.campaignName(),
                COMMAND.moduleKey(), COMMAND.releaseVersion(), COMMAND.contentSha256());
        DataSource unused = proxy(DataSource.class, (ignored, method, arguments) -> {
            throw new AssertionError("invalid key must be rejected before JDBC access");
        });

        assertThrows(SQLException.class,
                () -> new JdbcCampaignCreationRepository(unused).create(invalid));
    }

    @Test
    void rejectsInvalidStoredCampaignKeyDuringIdempotentReplay() {
        JdbcFixture fixture = new JdbcFixture();
        fixture.operationRows = List.of(operationRow(COMMAND.requestDigestSha256(),
                "CREATE_CAMPAIGN", 42L, "SUCCEEDED"));
        fixture.campaignRows = List.of(Map.of(
                "campaign_key", "aaaaaaaa-bbbb-1ccc-8ddd-eeeeeeeeeeee"));

        assertThrows(SQLException.class,
                () -> new JdbcCampaignCreationRepository(fixture.dataSource()).create(COMMAND));

        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        assertRestoredAndClosed(fixture);
    }

    private static void assertRestoredAndClosed(JdbcFixture fixture) {
        assertTrue(fixture.connectionClosed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    private static Map<String, Object> releaseRow(String sha) {
        return Map.of(
                "id", 7L,
                "module_key", COMMAND.moduleKey(),
                "release_version", COMMAND.releaseVersion(),
                "content_sha256", sha);
    }

    private static Map<String, Object> operationRow(
            String digest, String type, Long campaignId, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("request_digest_sha256", digest);
        row.put("operation_type", type);
        row.put("campaign_id", campaignId);
        row.put("result_status", status);
        return row;
    }

    private static final class JdbcFixture {
        private List<Map<String, Object>> operationRows = List.of();
        private List<Map<String, Object>> releaseRows = List.of(releaseRow(MODULE_SHA));
        private List<Map<String, Object>> activeCampaignRows = List.of();
        private List<Map<String, Object>> campaignRows = List.of();
        private final List<String> insertTargets = new ArrayList<>();
        private final Map<Integer, Object> campaignModuleValues = new HashMap<>();
        private final Map<Integer, Object> operationValues = new HashMap<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean serializableEnabled;
        private boolean committed;
        private boolean rolledBack;
        private boolean connectionClosed;
        private int updateNumber;
        private int failUpdateNumber = -1;

        private DataSource dataSource() {
            Connection connection = proxy(Connection.class, this::connection);
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? connection : defaultValue(method.getReturnType()));
        }

        private Object connection(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> { autoCommit = (boolean) arguments[0]; yield null; }
                case "isReadOnly" -> readOnly;
                case "setReadOnly" -> { readOnly = (boolean) arguments[0]; yield null; }
                case "getTransactionIsolation" -> isolation;
                case "setTransactionIsolation" -> {
                    isolation = (int) arguments[0];
                    serializableEnabled |= isolation == Connection.TRANSACTION_SERIALIZABLE;
                    yield null;
                }
                case "prepareStatement" -> statement((String) arguments[0], arguments);
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                case "close" -> { connectionClosed = true; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql, Object[] prepareArguments) {
            Map<Integer, Object> values = new HashMap<>();
            boolean generatedKeys = prepareArguments != null
                    && prepareArguments.length > 1
                    && Integer.valueOf(Statement.RETURN_GENERATED_KEYS)
                    .equals(prepareArguments[1]);
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setString", "setLong" -> {
                            values.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> resultSet(rowsFor(sql));
                        case "executeUpdate" -> executeUpdate(sql, values);
                        case "getGeneratedKeys" -> generatedKeys
                                ? resultSet(List.of(Map.of("1", 42L)))
                                : resultSet(List.of());
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private int executeUpdate(String sql, Map<Integer, Object> values) throws SQLException {
            updateNumber++;
            String target = insertTarget(sql);
            insertTargets.add(target);
            if ("campaign_module".equals(target)) {
                campaignModuleValues.putAll(values);
            } else if ("host_operation".equals(target)) {
                operationValues.putAll(values);
            }
            if (updateNumber == failUpdateNumber) {
                throw new SQLException("synthetic insert failure");
            }
            return 1;
        }

        private List<Map<String, Object>> rowsFor(String sql) {
            if (sql.contains("FROM host_operation")) return operationRows;
            if (sql.contains("FROM module_release")) return releaseRows;
            if (sql.contains("WHERE campaign_status = 'ACTIVE'")) return activeCampaignRows;
            if (sql.contains("SELECT campaign_key FROM campaign")) return campaignRows;
            throw new AssertionError("Unexpected query: " + sql);
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "getString" -> {
                            Object value = value(rows, index[0], arguments[0], wasNull);
                            yield value == null ? null : value.toString();
                        }
                        case "getLong" -> {
                            Object value = value(rows, index[0], arguments[0], wasNull);
                            yield value == null ? 0L : ((Number) value).longValue();
                        }
                        case "wasNull" -> wasNull[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object value(
                List<Map<String, Object>> rows, int index, Object column, boolean[] wasNull) {
            String key = column instanceof Integer ? column.toString() : (String) column;
            Object value = rows.get(index).get(key);
            wasNull[0] = value == null;
            return value;
        }

        private static String insertTarget(String sql) {
            String normalized = sql.stripLeading();
            int start = normalized.indexOf("INSERT INTO ") + "INSERT INTO ".length();
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
