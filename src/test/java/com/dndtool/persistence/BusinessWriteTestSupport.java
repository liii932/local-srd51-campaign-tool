package com.dndtool.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;

/** JDBC fixture adapter: production admission and session handles remain real. */
public final class BusinessWriteTestSupport {
    private static final ThreadLocal<Boolean> REMAINDER =
            ThreadLocal.withInitial(() -> false);

    private BusinessWriteTestSupport() { }

    /** Supplies only the two shared admission queries; all business SQL goes to the fixture. */
    public static DataSource withGuard(DataSource fixture) {
        return proxy(DataSource.class, (ignored, method, args) -> {
            Object value = call(fixture, method, args);
            return method.getName().equals("getConnection")
                    ? guardQueries((Connection) value) : value;
        });
    }

    public static AutoCloseableRemainder runtimeRemainder() {
        boolean previous = REMAINDER.get();
        REMAINDER.set(true);
        return new AutoCloseableRemainder(previous);
    }

    public static final class AutoCloseableRemainder implements AutoCloseable {
        private final boolean previous;
        private AutoCloseableRemainder(boolean previous) { this.previous = previous; }
        @Override public void close() { REMAINDER.set(previous); }
    }

    public static void assertGuarded(Connection connection) {
        Assertions.assertDoesNotThrow(() -> BusinessWriteTransactions.requireGuarded(connection));
    }

    public static void assertRawConnectionRejected(Write work) {
        Connection raw = proxy(Connection.class, (ignored, method, args) ->
                switch (method.getName()) {
                    case "getAutoCommit", "isReadOnly" -> false;
                    case "getTransactionIsolation" -> Connection.TRANSACTION_SERIALIZABLE;
                    case "prepareStatement", "createStatement" ->
                            throw new AssertionError("unguarded business SQL was reached");
                    default -> defaultValue(method.getReturnType());
                });
        SQLException failure = Assertions.assertThrows(SQLException.class, () -> work.execute(raw));
        Assertions.assertEquals("BWB03", failure.getSQLState());
    }

    @FunctionalInterface
    public interface Work<T> { T execute(Connection connection) throws SQLException; }

    @FunctionalInterface
    public interface Write { void execute(Connection connection) throws SQLException; }

    /**
     * Older leaf fixtures describe an already active caller transaction. Model its fresh pool
     * connection here, then let the real Session start and own that transaction. The adapter owns
     * lifecycle calls; the leaf fixture still records every attempted business JDBC operation.
     * Invalid caller fixtures go through unchanged so their rejection remains observable.
     */
    public static <T> T inTransaction(Connection fixture, Work<T> work) throws SQLException {
        if (fixture.getAutoCommit() || fixture.isReadOnly()
                || fixture.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE
                    && fixture.getTransactionIsolation() != Connection.TRANSACTION_NONE) {
            return work.execute(fixture);
        }
        boolean[] autoCommit = {true};
        boolean[] readOnly = {false};
        int[] isolation = {Connection.TRANSACTION_SERIALIZABLE};
        Connection fresh = proxy(Connection.class, (ignored, method, args) ->
                switch (method.getName()) {
                    case "getAutoCommit" -> autoCommit[0];
                    case "isReadOnly" -> readOnly[0];
                    case "getTransactionIsolation" -> isolation[0];
                    case "setAutoCommit" -> { autoCommit[0] = (boolean) args[0]; yield null; }
                    case "setReadOnly" -> { readOnly[0] = (boolean) args[0]; yield null; }
                    case "setTransactionIsolation" -> { isolation[0] = (int) args[0]; yield null; }
                    case "commit", "rollback", "close" -> null;
                    default -> call(fixture, method, args);
                });
        DataSource source = proxy(DataSource.class, (ignored, method, args) ->
                method.getName().equals("getConnection") ? fresh : null);
        try (BusinessWriteTransactions.Session transaction = BusinessWriteTransactions.openLegacy(
                withGuard(source), Connection.TRANSACTION_SERIALIZABLE)) {
            return work.execute(transaction.connection());
        }
    }

    public static void write(Connection fixture, Write work) throws SQLException {
        inTransaction(fixture, connection -> { work.execute(connection); return null; });
    }

    public static BusinessWriteTransactions.Session partition(Connection fixture) throws SQLException {
        return BusinessWriteTransactions.openPartition(idleFixture(fixture));
    }

    public static BusinessWriteTransactions.Session internal(Connection fixture) throws SQLException {
        return BusinessWriteTransactions.openInternal(idleFixture(fixture));
    }

    /** Adapts a row fixture's explicit transaction to a freshly borrowed pool connection. */
    private static DataSource idleFixture(Connection fixture) throws SQLException {
        boolean[] autoCommit = {true};
        boolean[] readOnly = {fixture.isReadOnly()};
        int[] isolation = {fixture.getTransactionIsolation()};
        Connection fresh = proxy(Connection.class, (ignored, method, args) ->
                switch (method.getName()) {
                    case "getAutoCommit" -> autoCommit[0];
                    case "isReadOnly" -> readOnly[0];
                    case "getTransactionIsolation" -> isolation[0];
                    case "setAutoCommit" -> { autoCommit[0] = (boolean) args[0]; yield null; }
                    case "setReadOnly" -> { readOnly[0] = (boolean) args[0]; yield null; }
                    case "setTransactionIsolation" -> { isolation[0] = (int) args[0]; yield null; }
                    case "close" -> null;
                    default -> call(fixture, method, args);
                });
        return withGuard(proxy(DataSource.class, (ignored, method, args) ->
                method.getName().equals("getConnection") ? fresh : null));
    }

    private static Connection guardQueries(Connection fixture) {
        boolean[] locked = {false};
        boolean[] checked = {false};
        return proxy(Connection.class, (ignored, method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                String sql = (String) args[0];
                if (sql.equals(BusinessWriteTransactions.LOCK_SQL)) {
                    Assertions.assertFalse(locked[0]);
                    locked[0] = true;
                    return statement(Map.of("guard_id", 1, "protocol_version", 1));
                }
                if (sql.equals(BusinessWriteTransactions.STATE_SQL)) {
                    Assertions.assertTrue(locked[0]);
                    checked[0] = true;
                    return statement(Map.of("legacy_active", false,
                            "schema2_remainder", REMAINDER.get(), "partition_remainder", false));
                }
                if (!fixture.getAutoCommit() && !fixture.isReadOnly()) {
                    Assertions.assertTrue(checked[0], "business SQL preceded the permanent guard");
                }
            }
            return call(fixture, method, args);
        });
    }

    private static PreparedStatement statement(Map<String, Object> row) {
        return proxy(PreparedStatement.class, (ignored, method, args) -> {
            if (method.getName().equals("executeQuery")) {
                int[] cursor = {0};
                return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
                    case "next" -> cursor[0]++ == 0;
                    case "getInt" -> row.get(a[0]);
                    case "getBoolean" -> row.get(a[0]);
                    case "wasNull" -> false;
                    default -> defaultValue(m.getReturnType());
                });
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Object call(Object receiver, Method method, Object[] args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return '\0';
    }
}
