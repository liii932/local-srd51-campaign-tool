package com.dndtool.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.sql.DataSource;

/** One process admission boundary for legacy writes and schema 2 runtime material. */
public final class BusinessWriteTransactions {
    public static final String LOCK_SQL = "SELECT guard_id, protocol_version FROM business_write_guard WHERE guard_id = 1 FOR UPDATE";
    public static final String STATE_SQL = "SELECT EXISTS (SELECT 1 FROM campaign WHERE campaign_status = 'ACTIVE') AS legacy_active, EXISTS (SELECT 1 FROM s2_runtime_identity) AS schema2_remainder, EXISTS (SELECT 1 FROM runtime_run_identity) AS partition_remainder";
    private static final ReentrantReadWriteLock PROCESS = new ReentrantReadWriteLock(true);
    // Unknown executors stay strongly referenced until process shutdown if abort cannot isolate them.
    private static final List<Connection> QUARANTINED = new ArrayList<>();
    private static volatile boolean blocked;
    private static volatile boolean isolationFailed;

    private BusinessWriteTransactions() { }

    public enum Outcome { ACTIVE, COMMITTED, ROLLED_BACK, UNKNOWN }
    private enum Mode { LEGACY, INTERNAL, PARTITION, INITIALIZATION, RESOLUTION }

    public static boolean isBlocked() { return blocked; }
    public static boolean isResolutionAvailable() { return !isolationFailed; }
    /** Invalidates execution after a verified integrity fault without inventing a commit outcome. */
    public static void invalidate() { blocked = true; }

    /** Nonblocking admission; never borrow a runtime connection before obtaining this lease. */
    public static Admission enter() throws SQLException { return enter(false); }

    public static Admission enterResolution() throws SQLException { return enter(true); }

    private static Admission enter(boolean resolution) throws SQLException {
        if (PROCESS.isWriteLockedByCurrentThread()) throw failure("BW_BUSY", "BWB01");
        if (!PROCESS.writeLock().tryLock()) throw failure("BW_BUSY", "BWB01");
        if ((!resolution && blocked) || resolution && isolationFailed) {
            PROCESS.writeLock().unlock();
            throw failure("BW_UNKNOWN", "BWB02");
        }
        return new Admission(resolution);
    }

    public static Session openLegacy(DataSource source, int isolation) throws SQLException {
        return openOwned(source, Mode.LEGACY, isolation);
    }
    public static Session openInternal(DataSource source) throws SQLException {
        return openOwned(source, Mode.INTERNAL, Connection.TRANSACTION_READ_COMMITTED);
    }
    public static Session openPartition(DataSource source) throws SQLException {
        return openOwned(source, Mode.PARTITION, Connection.TRANSACTION_READ_COMMITTED);
    }
    public static Session openInitialization(DataSource source) throws SQLException {
        return openOwned(source, Mode.INITIALIZATION, Connection.TRANSACTION_READ_COMMITTED);
    }
    public static Session openResolution(DataSource source) throws SQLException {
        return openOwned(source, Mode.RESOLUTION, Connection.TRANSACTION_READ_COMMITTED);
    }
    private static Session openOwned(DataSource source, Mode mode, int isolation) throws SQLException {
        Admission admission = enter(mode == Mode.RESOLUTION);
        try { return admission.open(source, mode, isolation, true); }
        catch (SQLException | RuntimeException | Error failure) { admission.close(); throw failure; }
    }

    /** Caller-owned JDBC writers use this check before any business SQL. */
    public static void requireGuarded(Connection connection) throws SQLException {
        if (connection == null || !Proxy.isProxyClass(connection.getClass())
                || !(Proxy.getInvocationHandler(connection) instanceof ConnectionHandler handler)) {
            throw failure("BW_ADMISSION_REQUIRED", "BWB03");
        }
        handler.session.active();
    }

    public static final class Admission implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final boolean resolution;
        private boolean closed;
        private Session current;
        private Admission(boolean resolution) { this.resolution = resolution; }
        public Session openInternal(DataSource source) throws SQLException {
            return open(source, Mode.INTERNAL, Connection.TRANSACTION_READ_COMMITTED, false);
        }
        public Session openInitialization(DataSource source) throws SQLException {
            return open(source, Mode.INITIALIZATION, Connection.TRANSACTION_READ_COMMITTED, false);
        }
        public Session openResolution(DataSource source) throws SQLException {
            return open(source, Mode.RESOLUTION, Connection.TRANSACTION_READ_COMMITTED, false);
        }
        private Session open(DataSource source, Mode mode, int isolation, boolean ownsAdmission) throws SQLException {
            check();
            if (current != null || (mode == Mode.RESOLUTION) != resolution || !resolution && blocked)
                throw failure("BW_UNKNOWN", "BWB02");
            Session session = new Session(this, Objects.requireNonNull(source), mode, isolation, ownsAdmission);
            current = session;
            try { session.begin(); return session; }
            catch (SQLException | RuntimeException | Error failure) {
                try { session.close(); }
                catch (SQLException | RuntimeException | Error cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
        private void check() throws SQLException {
            if (closed || owner != Thread.currentThread()) throw failure("BW_SCOPE_CLOSED", "BWB03");
        }
        @Override public void close() {
            if (closed) return;
            if (owner != Thread.currentThread()) throw new IllegalStateException("Admission thread changed");
            if (current != null) throw new IllegalStateException("Close the transaction before its admission");
            closed = true;
            PROCESS.writeLock().unlock();
        }
    }

    public static final class Session implements AutoCloseable {
        private final Admission admission;
        private final DataSource source;
        private final Mode mode;
        private final int isolation;
        private final boolean ownsAdmission;
        private Connection raw;
        private Connection view;
        private boolean originalAutoCommit, originalReadOnly, captured, begun, closed;
        private int originalIsolation;
        private Outcome outcome = Outcome.ACTIVE;
        private Session(Admission admission, DataSource source, Mode mode, int isolation, boolean ownsAdmission) {
            this.admission = admission; this.source = source; this.mode = mode;
            this.isolation = isolation; this.ownsAdmission = ownsAdmission;
        }
        private void begin() throws SQLException {
            if (isolation != Connection.TRANSACTION_READ_COMMITTED
                    && isolation != Connection.TRANSACTION_REPEATABLE_READ
                    && isolation != Connection.TRANSACTION_SERIALIZABLE)
                throw failure("BW_INVALID_ISOLATION", "BWB03");
            raw = source.getConnection();
            originalAutoCommit = raw.getAutoCommit(); originalReadOnly = raw.isReadOnly();
            originalIsolation = raw.getTransactionIsolation(); captured = true;
            if (!originalAutoCommit) {
                SQLException failure = failure("BW_TRANSACTION_ALREADY_ACTIVE", "BWB03");
                unknown(failure);
                throw failure;
            }
            raw.setReadOnly(false);
            raw.setTransactionIsolation(isolation);
            // A driver may change server state before reporting a setter failure.
            begun = true;
            raw.setAutoCommit(false);
            try (PreparedStatement statement = raw.prepareStatement(LOCK_SQL)) {
                statement.setQueryTimeout(5);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getInt("guard_id") != 1
                            || rows.getInt("protocol_version") != 1 || rows.next()) {
                        invalidate();
                        throw failure("BW_INVALID_GUARD", "BWB03");
                    }
                }
            }
            try (PreparedStatement statement = raw.prepareStatement(STATE_SQL)) {
                statement.setQueryTimeout(5);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) { invalidate(); throw failure("BW_INVALID_STATE", "BWB03"); }
                    boolean legacy = rows.getBoolean("legacy_active");
                    boolean schema2 = rows.getBoolean("schema2_remainder");
                    boolean partition = rows.getBoolean("partition_remainder");
                    if (rows.next()) { invalidate(); throw failure("BW_INVALID_STATE", "BWB03"); }
                    if (mode == Mode.LEGACY && (schema2 || partition)
                            || mode == Mode.INTERNAL && (legacy || partition)
                            || mode == Mode.PARTITION && (legacy || schema2)
                            || mode == Mode.INITIALIZATION && (legacy || schema2 || partition))
                        throw failure("BW_RUNTIME_REMAINDER", "BWB04");
                }
            }
            // No complete business initializer exists yet; an empty database is not execution authority.
            if (mode == Mode.INITIALIZATION) throw failure("BW_INITIALIZATION_UNAVAILABLE", "BWB05");
            view = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, new ConnectionHandler(this));
        }
        public Connection connection() throws SQLException { active(); return view; }
        public Outcome outcome() { return outcome; }
        private void active() throws SQLException {
            ownedActive();
            if (blocked && mode != Mode.RESOLUTION) throw failure("BW_UNKNOWN", "BWB02");
        }
        private void ownedActive() throws SQLException {
            admission.check();
            if (closed || outcome != Outcome.ACTIVE || view == null) throw failure("BW_TRANSACTION_SETTLED", "BWB03");
        }
        public void commit() throws SQLException {
            active();
            try { raw.commit(); outcome = Outcome.COMMITTED; }
            catch (SQLException | RuntimeException | Error failure) { unknown(failure); throw failure; }
        }
        public void rollback() throws SQLException {
            ownedActive();
            try { raw.rollback(); outcome = Outcome.ROLLED_BACK; }
            catch (SQLException | RuntimeException | Error failure) { unknown(failure); throw failure; }
        }
        private void unknown(Throwable failure) {
            outcome = Outcome.UNKNOWN; blocked = true;
            quarantine(failure);
        }
        private void quarantine(Throwable failure) {
            if (raw == null) return;
            Connection executor = raw;
            try { executor.abort(Runnable::run); }
            catch (SQLException | RuntimeException | Error abortFailure) {
                isolationFailed = true;
                QUARANTINED.add(executor);
                if (abortFailure != failure) failure.addSuppressed(abortFailure);
            } finally {
                // Isolation bookkeeping must never send an unknown executor into normal cleanup.
                raw = null;
            }
        }
        @Override public void close() throws SQLException {
            if (closed) return;
            admission.check();
            Throwable cleanup = null;
            try {
                if (raw != null && !captured) {
                    cleanup = failure("BW_CONNECTION_STATE_UNKNOWN", "BWB02");
                    unknown(cleanup);
                }
                if (raw != null && begun && outcome == Outcome.ACTIVE) {
                    try { raw.rollback(); outcome = Outcome.ROLLED_BACK; }
                    catch (SQLException | RuntimeException | Error failure) { cleanup = failure; unknown(failure); }
                }
                if (raw != null) {
                    try {
                        if (captured) {
                            raw.setAutoCommit(originalAutoCommit);
                            raw.setTransactionIsolation(originalIsolation);
                            raw.setReadOnly(originalReadOnly);
                        }
                        raw.close(); raw = null;
                    } catch (SQLException | RuntimeException | Error failure) {
                        cleanup = combine(cleanup, failure); blocked = true; quarantine(failure);
                    }
                }
            } finally {
                closed = true; admission.current = null;
                if (ownsAdmission) admission.close();
            }
            // A successful commit remains successful when pool cleanup fails.
            if (cleanup != null && (outcome != Outcome.COMMITTED || cleanup instanceof Error)) {
                if (cleanup instanceof SQLException sql) throw sql;
                if (cleanup instanceof RuntimeException runtime) throw runtime;
                throw (Error) cleanup;
            }
        }
    }

    private record ConnectionHandler(Session session) implements java.lang.reflect.InvocationHandler {
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            session.active();
            if (name.equals("unwrap") || name.equals("isWrapperFor") || name.equals("commit")
                    || name.equals("rollback") || name.equals("close") || name.equals("abort")
                    || name.equals("beginRequest") || name.equals("endRequest")
                    || name.startsWith("set") || name.equals("releaseSavepoint"))
                throw failure("BW_SESSION_OWNS_TRANSACTION", "BWB03");
            if (name.equals("prepareCall") || name.equals("nativeSQL")) throw failure("BW_UNSUPPORTED_SQL", "BWB03");
            if (name.equals("prepareStatement")) requireBusinessSql((String) args[0]);
            if (session.mode == Mode.RESOLUTION && (name.equals("createStatement")
                    || name.equals("prepareStatement") && !selectOnly((String) args[0])))
                throw failure("BW_RESOLUTION_READ_ONLY", "BWB03");
            Object value = call(session.raw, method, args);
            if (value instanceof Statement statement) return wrapStatement(statement, session);
            if (value instanceof DatabaseMetaData metadata) return Proxy.newProxyInstance(
                    DatabaseMetaData.class.getClassLoader(), new Class<?>[]{DatabaseMetaData.class}, (p, m, a) -> {
                        session.active();
                        if (m.getName().equals("getConnection")) return session.view;
                        if (m.getName().equals("unwrap") || m.getName().equals("isWrapperFor")) throw failure("BW_NO_UNWRAP", "BWB03");
                        Object result = call(metadata, m, a);
                        return wrapValue(result, session, null);
                    });
            return value;
        }
    }
    private static Object wrapStatement(Statement raw, Session session) {
        Class<?> type = raw instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        final Object[] holder = new Object[1];
        holder[0] = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            if (name.equals("close")) return call(raw, method, args);
            session.active();
            if (name.equals("getConnection")) return session.view;
            if (name.equals("unwrap") || name.equals("isWrapperFor")) throw failure("BW_NO_UNWRAP", "BWB03");
            if (args != null && args.length > 0 && args[0] instanceof String sql
                    && (name.startsWith("execute") || name.equals("addBatch"))) requireBusinessSql(sql);
            if (session.mode == Mode.RESOLUTION && (name.equals("addBatch") || name.contains("Update")
                    || name.contains("Batch") || args != null && args.length > 0 && args[0] instanceof String sql && !selectOnly(sql)))
                throw failure("BW_RESOLUTION_READ_ONLY", "BWB03");
            Object value = call(raw, method, args);
            return wrapValue(value, session, holder[0]);
        });
        return holder[0];
    }
    private static ResultSet wrapRows(ResultSet raw, Session session, Object statement) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            if (name.equals("close")) return call(raw, method, args);
            session.active();
            if (name.equals("getStatement")) return statement;
            if (name.equals("unwrap") || name.equals("isWrapperFor") || name.startsWith("update")
                    || name.equals("insertRow") || name.equals("deleteRow")) throw failure("BW_NO_UNWRAP", "BWB03");
            return wrapValue(call(raw, method, args), session, statement);
        });
    }
    private static Object wrapValue(Object value, Session session, Object statement) {
        if (value instanceof ResultSet rows) return wrapRows(rows, session, statement);
        Class<?> metadataType = value instanceof ResultSetMetaData ? ResultSetMetaData.class
                : value instanceof ParameterMetaData ? ParameterMetaData.class : null;
        if (metadataType == null) return value;
        return Proxy.newProxyInstance(metadataType.getClassLoader(), new Class<?>[]{metadataType}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            session.active();
            if (method.getName().equals("unwrap") || method.getName().equals("isWrapperFor"))
                throw failure("BW_NO_UNWRAP", "BWB03");
            return call(value, method, args);
        });
    }
    private static boolean selectOnly(String sql) {
        String normalized = sql.strip().toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("SELECT ") && !normalized.contains(";")
                && !normalized.contains("INTO ") && !normalized.contains("FOR UPDATE;");
    }
    private static void requireBusinessSql(String sql) throws SQLException {
        String normalized = sql.strip().toUpperCase(java.util.Locale.ROOT);
        if (!(normalized.startsWith("SELECT ") || normalized.startsWith("INSERT ")
                || normalized.startsWith("UPDATE ") || normalized.startsWith("DELETE "))
                || normalized.contains(";") || normalized.contains("/*") || normalized.contains("--"))
            throw failure("BW_UNSUPPORTED_SQL", "BWB03");
    }
    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0]; case "hashCode" -> System.identityHashCode(proxy);
            default -> "Guarded business JDBC handle";
        };
    }
    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        first.addSuppressed(next); return first;
    }
    private static SQLException failure(String message, String state) { return new SQLException(message, state); }
}
