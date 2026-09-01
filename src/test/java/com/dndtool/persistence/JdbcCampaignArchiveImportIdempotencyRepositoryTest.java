package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
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
import org.junit.jupiter.api.Test;

final class JdbcCampaignArchiveImportIdempotencyRepositoryTest {
    private static final String REQUEST = "33333333-3333-4333-8333-333333333333";
    private static final String DIGEST = "a".repeat(64);
    private final JdbcCampaignArchiveImportIdempotencyRepository repository =
            new JdbcCampaignArchiveImportIdempotencyRepository();

    @Test
    void missingRequestLocksGlobalIdentityWithoutOwningTransaction() throws Exception {
        Fixture fixture = new Fixture();

        CampaignArchiveImportIdempotencyRepository.Lookup lookup = repository.find(
                fixture.connection(), command());

        assertEquals(CampaignArchiveImportIdempotencyRepository.Status.NEW, lookup.status());
        assertTrue(fixture.preparedSql.getFirst().contains("FOR UPDATE"));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void sameDigestAndOperationReplaysOriginalCampaign() throws Exception {
        Fixture fixture = replayFixture();

        CampaignArchiveImportIdempotencyRepository.Lookup lookup = repository.find(
                fixture.connection(), command());

        assertEquals(CampaignArchiveImportIdempotencyRepository.Status.REPLAY, lookup.status());
        assertEquals(7L, lookup.campaignId());
        assertEquals(1, fixture.preparedSql.size());
    }

    @Test
    void differentDigestOrOperationReturnsConflict() throws Exception {
        for (String changedColumn : List.of("request_digest_sha256", "operation_type")) {
            Fixture fixture = replayFixture();
            fixture.rows.getFirst().put(changedColumn,
                    "request_digest_sha256".equals(changedColumn)
                            ? "b".repeat(64) : "CREATE_CAMPAIGN");

            CampaignArchiveImportIdempotencyRepository.Lookup lookup = repository.find(
                    fixture.connection(), command());

            assertEquals(CampaignArchiveImportIdempotencyRepository.Status.CONFLICT,
                    lookup.status());
        }
    }

    @Test
    void incompleteMatchingResultFailsClosed() {
        Fixture fixture = replayFixture();
        fixture.rows.getFirst().put("campaign_id", null);

        SQLException exception = assertThrows(SQLException.class,
                () -> repository.find(fixture.connection(), command()));

        assertTrue(exception.getMessage().contains("incomplete"));
    }

    @Test
    void completionRecordsSuccessfulCampaignWithoutCommitOrRollback() throws Exception {
        Fixture fixture = new Fixture();

        repository.complete(
                fixture.connection(),
                new CampaignArchiveImportIdempotencyRepository.Completion(
                        REQUEST, DIGEST, 7L));

        assertTrue(fixture.preparedSql.getFirst().startsWith("INSERT INTO host_operation"));
        assertEquals(Map.of(
                1, REQUEST,
                2, DIGEST,
                3, JdbcCampaignArchiveImportIdempotencyRepository.OPERATION_TYPE,
                4, 7L), fixture.lastBound);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void rejectsMalformedIdentityAndConnectionsOutsideWritableSerializableTransaction() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;

        assertThrows(SQLException.class,
                () -> repository.find(fixture.connection(), command()));
        assertThrows(IllegalArgumentException.class, () -> repository.find(
                fixture.connection(),
                new CampaignArchiveImportIdempotencyRepository.Command(
                        "not-a-uuid", DIGEST)));
    }

    private static CampaignArchiveImportIdempotencyRepository.Command command() {
        return new CampaignArchiveImportIdempotencyRepository.Command(REQUEST, DIGEST);
    }

    private static Fixture replayFixture() {
        Fixture fixture = new Fixture();
        fixture.rows.add(row(
                "request_digest_sha256", DIGEST,
                "operation_type", JdbcCampaignArchiveImportIdempotencyRepository.OPERATION_TYPE,
                "campaign_id", 7L,
                "result_status", "SUCCEEDED"));
        return fixture;
    }

    private static Map<Object, Object> row(Object... values) {
        Map<Object, Object> row = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(values[index], values[index + 1]);
        }
        return row;
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<Map<Object, Object>> rows = new ArrayList<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean committed;
        private boolean rolledBack;
        private Map<Integer, Object> lastBound = Map.of();

        private Connection connection() {
            return proxy(Connection.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                case "prepareStatement" -> statement(arguments[0].toString());
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement statement(String originalSql) {
            String sql = originalSql.replaceAll("\\s+", " ").trim();
            preparedSql.add(sql);
            Map<Integer, Object> bound = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setString", "setLong" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> resultSet();
                        case "executeUpdate" -> {
                            lastBound = Map.copyOf(bound);
                            yield 1;
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet resultSet() {
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
