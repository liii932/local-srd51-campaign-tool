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

/** Verifies that host command retries restore immutable rows without owning the transaction. */
final class JdbcCheckIdempotencyRepositoryTest {
    private static final String REQUEST = "33333333-3333-3333-3333-333333333333";
    private static final String DIGEST = "a".repeat(64);
    private final JdbcCheckIdempotencyRepository repository =
            new JdbcCheckIdempotencyRepository();

    @Test
    void missingRequestLocksIdentityAndReturnsNewWithoutWriting() throws Exception {
        Fixture fixture = new Fixture();

        CheckIdempotencyRepository.Lookup lookup = repository.find(
                fixture.connection(), command());

        assertEquals(CheckIdempotencyRepository.Status.NEW, lookup.status());
        assertEquals(1, fixture.preparedSql.size());
        assertTrue(fixture.preparedSql.getFirst().contains("FOR UPDATE"));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void sameDigestRestoresOriginalIdsCandidatesAndOutcome() throws Exception {
        Fixture fixture = replayFixture();

        CheckIdempotencyRepository.Lookup lookup = repository.find(
                fixture.connection(), command());

        assertEquals(CheckIdempotencyRepository.Status.REPLAY, lookup.status());
        assertEquals(101L, lookup.replay().savedCheck().gameEventId());
        assertEquals(201L, lookup.replay().savedCheck().checkExecutionId());
        assertEquals(31L, lookup.replay().savedCheck().eventSequence());
        assertEquals(List.of(17, 8), lookup.replay().calculation().candidates().stream()
                .map(candidate -> candidate.rolledValue()).toList());
        assertEquals(17, lookup.replay().calculation().selectedValue());
        assertEquals(20, lookup.replay().calculation().totalValue());
        assertEquals("SUCCESS", lookup.replay().calculation().outcome().name());
        assertEquals(3, fixture.preparedSql.size());
    }

    @Test
    void sameRequestIdWithDifferentDigestReturnsConflictWithoutReadingCheck() throws Exception {
        Fixture fixture = replayFixture();
        fixture.operationRows.getFirst().put("request_digest_sha256", "b".repeat(64));

        CheckIdempotencyRepository.Lookup lookup = repository.find(
                fixture.connection(), command());

        assertEquals(CheckIdempotencyRepository.Status.CONFLICT, lookup.status());
        assertEquals(1, fixture.preparedSql.size());
    }

    @Test
    void inconsistentPersistedCandidateSelectionFailsClosed() {
        Fixture fixture = replayFixture();
        fixture.diceRows.get(0).put("is_selected", 0);
        fixture.diceRows.get(1).put("is_selected", 1);

        SQLException exception = assertThrows(SQLException.class,
                () -> repository.find(fixture.connection(), command()));

        assertTrue(exception.getMessage().contains("inconsistent"));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void completionInsertsSuccessfulRootWithoutCommitOrRollback() throws Exception {
        Fixture fixture = new Fixture();

        repository.complete(fixture.connection(), new CheckIdempotencyRepository.Completion(
                REQUEST, DIGEST, 7L, 101L));

        assertTrue(fixture.preparedSql.getFirst().startsWith("INSERT INTO host_operation"));
        assertEquals(Map.of(
                1, REQUEST,
                2, DIGEST,
                3, JdbcCheckIdempotencyRepository.OPERATION_TYPE,
                4, 7L,
                5, 101L), fixture.lastBound);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void rejectsAutoCommitConnectionAndMalformedIdentity() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;

        assertThrows(SQLException.class,
                () -> repository.find(fixture.connection(), command()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.find(fixture.connection(),
                        new CheckIdempotencyRepository.Command("not-a-uuid", DIGEST, 7L)));
    }

    private static CheckIdempotencyRepository.Command command() {
        return new CheckIdempotencyRepository.Command(REQUEST, DIGEST, 7L);
    }

    private static Fixture replayFixture() {
        Fixture fixture = new Fixture();
        fixture.operationRows.add(row(
                "request_digest_sha256", DIGEST,
                "operation_type", JdbcCheckIdempotencyRepository.OPERATION_TYPE,
                "campaign_id", 7L,
                "result_status", "SUCCEEDED",
                "game_event_id", 101L));
        fixture.checkRows.add(row(
                "event_sequence", 31L,
                "check_execution_id", 201L,
                "roll_mode_key", "roll.advantage",
                "modifier_value", 3,
                "total_value", 20,
                "difficulty_class", 10,
                "check_result", "SUCCESS"));
        fixture.diceRows.add(row(
                "candidate_order", 1, "rolled_value", 17, "is_selected", 1));
        fixture.diceRows.add(row(
                "candidate_order", 2, "rolled_value", 8, "is_selected", 0));
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
        private final List<Map<Object, Object>> operationRows = new ArrayList<>();
        private final List<Map<Object, Object>> checkRows = new ArrayList<>();
        private final List<Map<Object, Object>> diceRows = new ArrayList<>();
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
                        case "setString", "setLong", "setInt", "setBoolean" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> query(sql);
                        case "executeUpdate" -> {
                            lastBound = Map.copyOf(bound);
                            yield 1;
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet query(String sql) {
            if (sql.contains("FROM host_operation")) return rows(operationRows);
            if (sql.contains("FROM game_event AS ge")) return rows(checkRows);
            if (sql.contains("FROM dice_roll")) return rows(diceRows);
            throw new AssertionError("Unexpected query: " + sql);
        }
    }

    private static ResultSet rows(List<Map<Object, Object>> rows) {
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
                    case "getInt" -> {
                        Object value = rows.get(index[0]).get(arguments[0]);
                        wasNull[0] = value == null;
                        yield value == null ? 0 : ((Number) value).intValue();
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
        if (type == byte.class || type == short.class || type == int.class || type == long.class) {
            return 0;
        }
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
