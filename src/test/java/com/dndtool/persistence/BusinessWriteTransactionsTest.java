package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import com.dndtool.testing.IsolatedJvmTest;
import org.junit.jupiter.api.Test;

class BusinessWriteTransactionsTest {
    @Test void firstSqlLocksPermanentGuardAndLegacyIsolationIsPreserved() throws Exception {
        Fixture fixture = new Fixture();
        try (var session = BusinessWriteTransactions.openLegacy(fixture.source(), Connection.TRANSACTION_SERIALIZABLE)) {
            Connection connection = session.connection();
            BusinessWriteTransactions.requireGuarded(connection);
            assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
            assertFalse(connection.getAutoCommit());
            connection.prepareStatement("INSERT INTO business_value VALUES (1)").executeUpdate();
            assertEquals(0, fixture.committed);
            session.commit();
            assertEquals(BusinessWriteTransactions.Outcome.COMMITTED, session.outcome());
            assertThrows(SQLException.class, () -> BusinessWriteTransactions.requireGuarded(connection));
        }
        assertEquals(BusinessWriteTransactions.LOCK_SQL, fixture.sql.getFirst());
        assertEquals(BusinessWriteTransactions.STATE_SQL, fixture.sql.get(1));
        assertEquals(1, fixture.committed);
        assertEquals(0, fixture.rollbacks);
        assertTrue(fixture.autoCommit);
        assertTrue(fixture.closed);
    }

    @Test void rollbackIsCompleteAndSourceClosesBeforeBorrowingRuntime() throws Exception {
        Fixture fixture = new Fixture();
        fixture.requireSourceClosed = true;
        try (var admission = BusinessWriteTransactions.enter()) {
            fixture.sourceClosed = true;
            try (var session = admission.openInternal(fixture.source())) {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED, session.connection().getTransactionIsolation());
                session.connection().prepareStatement("INSERT INTO business_value VALUES (1)").executeUpdate();
            }
        }
        assertEquals(0, fixture.committed);
        assertEquals(0, fixture.pending);
        assertEquals(1, fixture.rollbacks);
    }

    @Test void allInstancesAndModesShareOneNonblockingProcessGateBeforeBorrow() throws Exception {
        Fixture internal = new Fixture(), legacy = new Fixture();
        try (var session = BusinessWriteTransactions.openInternal(internal.source())) {
            assertEquals("BWB01", assertThrows(SQLException.class,
                    () -> BusinessWriteTransactions.openInternal(legacy.source())).getSQLState());
            try (var executor = Executors.newSingleThreadExecutor()) {
                String state = executor.submit(() -> {
                    try (var ignored = BusinessWriteTransactions.openLegacy(legacy.source(), Connection.TRANSACTION_SERIALIZABLE)) {
                        return "unexpected admission";
                    } catch (SQLException failure) { return failure.getSQLState(); }
                }).get(5, TimeUnit.SECONDS);
                assertEquals("BWB01", state);
            }
            assertEquals(0, legacy.borrowed);
        }
        try (var session = BusinessWriteTransactions.openLegacy(legacy.source(), Connection.TRANSACTION_SERIALIZABLE)) {
            assertEquals(1, legacy.borrowed);
        }
    }

    @Test void activeLegacyAndAllRuntimeRemaindersBlockNewInitialization() throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            Fixture fixture = new Fixture();
            fixture.legacy = kind == 0; fixture.schema2 = kind == 1; fixture.partition = kind == 2;
            SQLException failure = assertThrows(SQLException.class,
                    () -> BusinessWriteTransactions.openInitialization(fixture.source()));
            assertEquals(kind == 3 ? "BWB05" : "BWB04", failure.getSQLState());
            assertEquals(1, fixture.rollbacks);
            assertEquals(0, fixture.commits);
        }
        Fixture internal = new Fixture(); internal.legacy = true;
        assertEquals("BWB04", assertThrows(SQLException.class,
                () -> BusinessWriteTransactions.openInternal(internal.source())).getSQLState());
        Fixture partitionFixture = new Fixture(); partitionFixture.schema2 = true;
        assertEquals("BWB04", assertThrows(SQLException.class,
                () -> BusinessWriteTransactions.openPartition(partitionFixture.source())).getSQLState());
        Fixture existingPartition = new Fixture(); existingPartition.partition = true;
        try (var session = BusinessWriteTransactions.openPartition(existingPartition.source())) { session.rollback(); }
        for (boolean partition : List.of(false, true)) {
            Fixture legacy = new Fixture(); legacy.schema2 = !partition; legacy.partition = partition;
            assertEquals("BWB04", assertThrows(SQLException.class,
                    () -> BusinessWriteTransactions.openLegacy(legacy.source(), Connection.TRANSACTION_SERIALIZABLE)).getSQLState());
        }
    }

    @Test void rawConnectionsCannotEscapeOrSettleTheOwnedTransaction() throws Exception {
        Fixture fixture = new Fixture();
        assertThrows(SQLException.class, () -> BusinessWriteTransactions.requireGuarded(fixture.raw));
        try (var session = BusinessWriteTransactions.openInternal(fixture.source())) {
            Connection connection = session.connection();
            assertThrows(SQLException.class, connection::commit);
            assertThrows(SQLException.class, connection::rollback);
            assertThrows(SQLException.class, connection::close);
            assertThrows(SQLException.class, () -> connection.setAutoCommit(true));
            assertThrows(SQLException.class, connection::beginRequest);
            assertThrows(SQLException.class, connection::endRequest);
            assertThrows(SQLException.class, () -> connection.unwrap(Connection.class));
            try (Statement statement = connection.createStatement()) {
                assertSame(connection, statement.getConnection());
                assertThrows(SQLException.class, () -> statement.unwrap(Statement.class));
                assertThrows(SQLException.class, () -> statement.execute("COMMIT"));
                try (ResultSet rows = statement.executeQuery("SELECT value FROM business_value")) {
                    assertSame(statement, rows.getStatement());
                    assertThrows(SQLException.class, () -> rows.unwrap(ResultSet.class));
                }
            }
            assertThrows(SQLException.class, () -> connection.prepareStatement("SELECT 1; COMMIT"));
        }
    }

    @Test void metadataRetainsUsefulReadsThroughGuardedConnectionsAndRejectsUnwrap() throws Exception {
        Fixture fixture = new Fixture();
        try (var session = BusinessWriteTransactions.openInternal(fixture.source())) {
            Connection connection = session.connection();
            DatabaseMetaData database = connection.getMetaData();
            assertEquals("fixture-db", database.getDatabaseProductName());
            assertSame(connection, database.getConnection());
            assertThrows(SQLException.class, database.getConnection()::commit);
            assertThrows(SQLException.class, () -> database.unwrap(DatabaseMetaData.class));
            assertThrows(SQLException.class, () -> database.isWrapperFor(DatabaseMetaData.class));
            try (ResultSet tables = database.getTables(null, null, "business_value", null)) {
                assertTrue(tables.next());
                // Driver metadata rows can retain a raw statement; the public view cannot expose it.
                assertNull(tables.getStatement());
                assertThrows(SQLException.class, () -> tables.unwrap(ResultSet.class));
                ResultSetMetaData columns = tables.getMetaData();
                assertEquals(1, columns.getColumnCount());
                assertEquals("value", columns.getColumnLabel(1));
                assertThrows(SQLException.class, () -> columns.unwrap(ResultSetMetaData.class));
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM business_value WHERE value = ?")) {
                ResultSetMetaData columns = statement.getMetaData();
                ParameterMetaData parameters = statement.getParameterMetaData();
                assertEquals(1, columns.getColumnCount());
                assertEquals(1, parameters.getParameterCount());
                assertThrows(SQLException.class, () -> columns.unwrap(ResultSetMetaData.class));
                assertThrows(SQLException.class, () -> columns.isWrapperFor(ResultSetMetaData.class));
                assertThrows(SQLException.class, () -> parameters.unwrap(ParameterMetaData.class));
                assertThrows(SQLException.class, () -> parameters.isWrapperFor(ParameterMetaData.class));
                try (ResultSet rows = statement.executeQuery()) {
                    assertSame(statement, rows.getStatement());
                    assertEquals("value", rows.getMetaData().getColumnLabel(1));
                    assertThrows(SQLException.class, () -> rows.getMetaData().unwrap(ResultSetMetaData.class));
                }
            }
        }
        assertEquals(0, fixture.metadataUnwraps);
        assertEquals(1, fixture.metadataQueries);
        assertEquals(0, fixture.commits);
        assertEquals(1, fixture.rollbacks);
    }

    @Test void updatableResultSetMutationsNeverReachTheDriver() throws Exception {
        Fixture fixture = new Fixture();
        try (var session = BusinessWriteTransactions.openInternal(fixture.source());
                PreparedStatement statement = session.connection().prepareStatement(
                        "SELECT value FROM business_value", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                ResultSet rows = statement.executeQuery()) {
            assertEquals(ResultSet.CONCUR_UPDATABLE, rows.getConcurrency());
            assertTrue(rows.next());
            assertThrows(SQLException.class, () -> rows.updateString(1, "unaccepted"));
            assertThrows(SQLException.class, () -> rows.updateObject("value", 2));
            assertThrows(SQLException.class, rows::updateRow);
            assertThrows(SQLException.class, rows::insertRow);
            assertThrows(SQLException.class, rows::deleteRow);
            assertEquals(0, fixture.resultWrites);
            assertEquals(0, fixture.pending);
            session.rollback();
        }
        assertEquals(0, fixture.committed);
        assertEquals(0, fixture.resultWrites);
    }

    @Test void invalidatedSessionRejectsNewAndRetainedJdbcHandlesButAllowsRollback() throws Exception {
        IsolatedJvmTest.run(FaultProbe.class, "invalidated");
    }

    @Test void resolutionCannotWriteOrAutomaticallyUnblock() throws Exception {
        Fixture fixture = new Fixture();
        try (var session = BusinessWriteTransactions.openResolution(fixture.source())) {
            assertThrows(SQLException.class, () -> session.connection().prepareStatement("INSERT INTO business_value VALUES (1)"));
            assertThrows(SQLException.class, () -> session.connection().createStatement());
            session.connection().prepareStatement("SELECT value FROM business_value FOR UPDATE").executeQuery().close();
            session.rollback();
        }
        assertEquals(0, fixture.committed);
    }

    @Test void missingOrMalformedPermanentGuardFailsClosedBeforeBusinessSql() throws Exception {
        IsolatedJvmTest.run(FaultProbe.class, "missingGuard");
        IsolatedJvmTest.run(FaultProbe.class, "malformedGuard");
    }

    @Test void autoCommitSetterResponseFailureRollsBackBeforeRestoringConnection() throws Exception {
        Fixture fixture = new Fixture(); fixture.failStart = true;
        assertThrows(SQLException.class, () -> BusinessWriteTransactions.openInternal(fixture.source()));
        assertEquals(1, fixture.rollbacks);
        assertEquals(0, fixture.commits);
        assertTrue(fixture.autoCommitRestored);
        assertTrue(fixture.closed);
        assertFalse(BusinessWriteTransactions.isBlocked());
        Fixture isolation = new Fixture(); isolation.failIsolation = true;
        assertThrows(SQLException.class, () -> BusinessWriteTransactions.openInternal(isolation.source()));
        assertEquals(0, isolation.commits);
        assertTrue(isolation.closed);
        assertFalse(BusinessWriteTransactions.isBlocked());
    }

    @Test void processLifetimeFailureScenariosUseFreshJvmWithoutResettingProductionAdmission() throws Exception {
        for (String scenario : List.of("commit", "rollback", "abort", "error", "close", "closeError", "capture", "startRollback",
                "startErrorCleanup", "startSameErrorCleanup"))
            IsolatedJvmTest.run(FaultProbe.class, scenario);
    }

    @Test void repeatedTransactionAndAbortThrowableCannotReturnUnknownExecutorToPool() throws Exception {
        for (String scenario : List.of("sameCommitSql", "sameRollbackSql", "sameCommitError", "sameRollbackError"))
            IsolatedJvmTest.run(FaultProbe.class, scenario);
    }

    /** Each probe has the same application lifecycle as a fresh WebApp, with no reset hook. */
    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            String scenario = args[0]; Fixture fixture = new Fixture();
            if (List.of("sameCommitSql", "sameRollbackSql", "sameCommitError", "sameRollbackError").contains(scenario)) {
                Throwable original = scenario.endsWith("Sql") ? new SQLException("shared driver failure") : new AssertionError("shared driver failure");
                fixture.sharedTransactionAbortFailure = original;
                fixture.rollbackSharesAbort = scenario.startsWith("sameRollback");
                var session = BusinessWriteTransactions.openInternal(fixture.source());
                session.connection().prepareStatement("INSERT INTO business_value VALUES (1)").executeUpdate();
                Throwable observed = null;
                try { if (fixture.rollbackSharesAbort) session.rollback(); else session.commit(); }
                catch (Throwable failure) { observed = failure; }
                session.close();
                check(observed == original, "abort cannot replace the original transaction Throwable");
                check(original.getSuppressed().length == 0, "same Throwable must not suppress itself");
                check(session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN, "retain UNKNOWN transaction outcome");
                check(fixture.aborts == 1, "isolate the original executor exactly once");
                check(!fixture.autoCommitRestored && fixture.closeCalls == 0, "UNKNOWN executor must never enter ordinary cleanup");
                check(fixture.implicitCommits == 0 && fixture.committed == 0, "UNKNOWN cleanup must not implicitly commit pending writes");
                check(BusinessWriteTransactions.isBlocked(), "unresolved transaction blocks every writer");
                check(!BusinessWriteTransactions.isResolutionAvailable(), "failed abort cannot permit resolution");
                Fixture next = new Fixture();
                rejected("BWB02", () -> BusinessWriteTransactions.openInternal(next.source()));
                rejected("BWB02", () -> BusinessWriteTransactions.openResolution(next.source()));
                check(next.borrowed == 0, "blocked write and resolution cannot borrow a connection");
                return;
            }
            if (scenario.equals("startErrorCleanup") || scenario.equals("startSameErrorCleanup")) {
                Error original = new AssertionError("transaction-start error");
                Error cleanup = scenario.equals("startSameErrorCleanup") ? original : new LinkageError("rollback cleanup error");
                fixture.errorStart = original; fixture.errorRollback = cleanup;
                try { BusinessWriteTransactions.openInternal(fixture.source()); throw new AssertionError("startup error admitted"); }
                catch (Error observed) {
                    check(observed == original, "cleanup must preserve the original Error identity");
                    check(observed.getSuppressed().length == (cleanup == original ? 0 : 1), "preserve cleanup without self suppression");
                    if (cleanup != original) check(observed.getSuppressed()[0] == cleanup, "retain exact suppressed cleanup Error");
                }
                check(BusinessWriteTransactions.isBlocked(), "failed rollback leaves UNKNOWN admission");
                check(fixture.rollbacks == 1 && fixture.aborts == 1, "UNKNOWN executor must be isolated");
                check(!fixture.autoCommitRestored && fixture.closeCalls == 0, "UNKNOWN cannot restore or return the executor");
                Fixture next = new Fixture();
                rejected("BWB02", () -> BusinessWriteTransactions.openInternal(next.source()));
                check(next.borrowed == 0, "UNKNOWN blocks before borrowing");
                try (var resolution = BusinessWriteTransactions.openResolution(next.source())) { resolution.rollback(); }
                check(BusinessWriteTransactions.isBlocked(), "resolution cannot reset execution after UNKNOWN");
                return;
            }
            if (scenario.equals("invalidated")) {
                try (var session = BusinessWriteTransactions.openInternal(fixture.source())) {
                    Connection connection = session.connection();
                    connection.prepareStatement("INSERT INTO business_value VALUES (1)").executeUpdate();
                    try (PreparedStatement retained = connection.prepareStatement("SELECT value FROM business_value");
                            ResultSet rows = retained.executeQuery()) {
                        DatabaseMetaData database = connection.getMetaData();
                        ResultSetMetaData columns = rows.getMetaData();
                        ParameterMetaData parameters = retained.getParameterMetaData();
                        int sqlCount = fixture.sql.size(), executions = fixture.executions;
                        BusinessWriteTransactions.invalidate();
                        rejected("BWB02", () -> connection.prepareStatement("INSERT INTO business_value VALUES (2)"));
                        rejected("BWB02", retained::executeQuery);
                        rejected("BWB02", rows::next);
                        rejected("BWB02", () -> database.getTables(null, null, "business_value", null));
                        rejected("BWB02", columns::getColumnCount);
                        rejected("BWB02", parameters::getParameterCount);
                        rejected("BWB02", session::commit);
                        check(fixture.sql.size() == sqlCount && fixture.executions == executions && fixture.metadataQueries == 0,
                                "invalidated handles must not prepare or execute SQL");
                        check(fixture.commits == 0 && fixture.pending == 1, "rejected commit cannot settle or append writes");
                        session.rollback();
                        check(session.outcome() == BusinessWriteTransactions.Outcome.ROLLED_BACK, "rollback remains available");
                        rejected("BWB03", session::connection);
                    }
                }
                check(fixture.pending == 0 && fixture.committed == 0 && fixture.rollbacks == 1,
                        "invalidation must leave no partial business writes");
                check(fixture.closed && fixture.autoCommitRestored && fixture.aborts == 0,
                        "explicit successful rollback can safely return the healthy connection");
                check(BusinessWriteTransactions.isBlocked(), "rollback cannot republish invalidated execution");
                Fixture next = new Fixture();
                rejected("BWB02", () -> BusinessWriteTransactions.openInternal(next.source()));
                check(next.borrowed == 0, "new sessions stay blocked before borrow");
                return;
            }
            if (scenario.equals("missingGuard") || scenario.equals("malformedGuard")) {
                fixture.guard = scenario.equals("missingGuard") ? 0 : 2;
                try { BusinessWriteTransactions.openInternal(fixture.source()); throw new AssertionError("malformed guard admitted"); }
                catch (SQLException expected) { check("BWB03".equals(expected.getSQLState()), "stable malformed guard error"); }
                check(fixture.sql.equals(List.of(BusinessWriteTransactions.LOCK_SQL)), "no later business SQL");
                check(fixture.rollbacks == 1 && fixture.commits == 0, "malformed guard must rollback");
                check(BusinessWriteTransactions.isBlocked(), "malformed permanent guard invalidates process");
                return;
            }
            fixture.failCommit = scenario.equals("commit") || scenario.equals("abort");
            fixture.failRollback = scenario.equals("rollback"); fixture.failAbort = scenario.equals("abort");
            fixture.errorCommit = scenario.equals("error"); fixture.failClose = scenario.equals("close");
            fixture.errorClose = scenario.equals("closeError");
            if (scenario.equals("capture") || scenario.equals("startRollback")) {
                fixture.failCapture = scenario.equals("capture");
                fixture.failStart = fixture.failRollback = scenario.equals("startRollback");
                try { BusinessWriteTransactions.openInternal(fixture.source()); throw new AssertionError("startup failure admitted"); }
                catch (SQLException expected) { }
                check(BusinessWriteTransactions.isBlocked(), "unknown borrowed state must block");
                check(fixture.aborts == 1 && fixture.closeCalls == 0, "unreadable borrowed state must be isolated");
                check(!fixture.autoCommitRestored, "unreadable state cannot restore auto commit");
                return;
            }
            var session = BusinessWriteTransactions.openInternal(fixture.source());
            session.connection().prepareStatement("INSERT INTO business_value VALUES (1)").executeUpdate();
            try { if (fixture.failRollback) session.rollback(); else session.commit(); }
            catch (SQLException | AssertionError expected) { }
            boolean closeError = false;
            try { session.close(); } catch (AssertionError expected) { closeError = true; }
            check(closeError == fixture.errorClose, "severe cleanup Error must propagate");
            check(BusinessWriteTransactions.isBlocked(), "fault must block all writers");
            check(fixture.aborts == 1, "original executor must be aborted");
            check(!(fixture.failClose || fixture.errorClose) || session.outcome() == BusinessWriteTransactions.Outcome.COMMITTED,
                    "successful commit cannot turn into rollback");
            if (!fixture.failClose && !fixture.errorClose) {
                check(session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN, "must retain UNKNOWN");
                check(!fixture.autoCommitRestored, "unknown must not restore auto commit");
                check(fixture.closeCalls == 0, "unknown must never return executor to pool");
            }
            Fixture next = new Fixture();
            try { BusinessWriteTransactions.openLegacy(next.source(), Connection.TRANSACTION_SERIALIZABLE); throw new AssertionError("admitted blocked write"); }
            catch (SQLException expected) { check("BWB02".equals(expected.getSQLState()), "stable unknown error"); }
            check(next.borrowed == 0, "blocked operation cannot borrow");
            if (fixture.failAbort) {
                try { BusinessWriteTransactions.openResolution(next.source()); throw new AssertionError("unisolated resolution"); }
                catch (SQLException expected) { check("BWB02".equals(expected.getSQLState()), "unisolated must block resolution"); }
                check(next.borrowed == 0, "unisolated resolution cannot borrow");
            } else {
                try (var resolution = BusinessWriteTransactions.openResolution(next.source())) { resolution.rollback(); }
                check(BusinessWriteTransactions.isBlocked(), "resolution cannot republish execution");
            }
            System.out.println("OK");
        }
        private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
        private static void rejected(String state, SqlAction action) throws SQLException {
            try { action.run(); throw new AssertionError("invalidated handle was accepted"); }
            catch (SQLException expected) { check(state.equals(expected.getSQLState()), "stable invalidation error"); }
        }
        private interface SqlAction { void run() throws SQLException; }
    }

    private static final class Fixture {
        final List<String> sql = new ArrayList<>();
        boolean autoCommit = true, readOnly, closed, legacy, schema2, partition, sourceClosed, requireSourceClosed;
        boolean failCommit, failRollback, failAbort, errorCommit, failClose, autoCommitRestored;
        boolean failStart, failIsolation, failCapture, errorClose;
        Error errorStart, errorRollback;
        Throwable sharedTransactionAbortFailure;
        boolean rollbackSharesAbort;
        int isolation = Connection.TRANSACTION_REPEATABLE_READ, guard = 1;
        int commits, rollbacks, aborts, closeCalls, borrowed, pending, committed;
        int metadataUnwraps, metadataQueries, resultWrites, executions;
        int implicitCommits;
        final Connection raw = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> { if (failCapture) throw new SQLException("capture unavailable"); yield isolation; }
                case "setAutoCommit" -> { autoCommit = (Boolean) args[0]; if (autoCommit) { autoCommitRestored = true; if (pending > 0) { implicitCommits++; committed += pending; pending = 0; } } if (!autoCommit && errorStart != null) throw errorStart; if (!autoCommit && failStart) throw new SQLException("setter response lost"); yield null; }
                case "setReadOnly" -> { readOnly = (Boolean) args[0]; yield null; }
                case "setTransactionIsolation" -> { isolation = (Integer) args[0]; if (failIsolation) { failIsolation = false; throw new SQLException("isolation response lost"); } yield null; }
                case "prepareStatement" -> { String query = (String) args[0]; sql.add(query); yield statement(query, true); }
                case "createStatement" -> statement(null, false);
                case "getMetaData" -> metadata(DatabaseMetaData.class);
                case "commit" -> { commits++; if (sharedTransactionAbortFailure != null && !rollbackSharesAbort) throw sharedTransactionAbortFailure; if (errorCommit) throw new AssertionError("commit error"); if (failCommit) throw new SQLException("commit response lost"); committed += pending; pending = 0; yield null; }
                case "rollback" -> { rollbacks++; if (sharedTransactionAbortFailure != null && rollbackSharesAbort) throw sharedTransactionAbortFailure; if (errorRollback != null) throw errorRollback; if (failRollback) throw new SQLException("rollback lost"); pending = 0; yield null; }
                case "abort" -> { aborts++; if (sharedTransactionAbortFailure != null) throw sharedTransactionAbortFailure; if (failAbort) throw new SQLException("abort unavailable"); yield null; }
                case "close" -> { closeCalls++; if (errorClose) throw new AssertionError("pool cleanup error"); if (failClose) throw new SQLException("pool return failed"); closed = true; yield null; }
                case "isClosed" -> closed;
                default -> defaultValue(method.getReturnType());
            };
        });
        DataSource source() {
            return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    if (requireSourceClosed && !sourceClosed) throw new AssertionError("source connection still open");
                    borrowed++; return raw;
                }
                return defaultValue(method.getReturnType());
            });
        }
        Object statement(String prepared, boolean isPrepared) {
            Class<?> type = isPrepared ? PreparedStatement.class : Statement.class;
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                String query = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String value ? value : "";
                return switch (method.getName()) {
                    case "executeQuery" -> { executions++; yield rows(query, (Statement) proxy); }
                    case "executeUpdate" -> { pending++; yield 1; }
                    case "getConnection" -> raw;
                    case "getMetaData" -> metadata(ResultSetMetaData.class);
                    case "getParameterMetaData" -> metadata(ParameterMetaData.class);
                    default -> defaultValue(method.getReturnType());
                };
            });
        }
        <T> T metadata(Class<T> type) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
                case "getDatabaseProductName" -> "fixture-db";
                case "getConnection" -> raw;
                case "getColumnCount", "getParameterCount" -> 1;
                case "getColumnLabel" -> "value";
                case "getTables" -> {
                    metadataQueries++;
                    yield rows("SELECT table_name FROM metadata", (Statement) statement(null, false));
                }
                case "unwrap" -> { metadataUnwraps++; yield proxy; }
                case "isWrapperFor" -> { metadataUnwraps++; yield true; }
                default -> defaultValue(method.getReturnType());
            }));
        }
        ResultSet rows(String query, Statement origin) {
            int[] index = {0};
            return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("update") || method.getName().equals("insertRow") || method.getName().equals("deleteRow")) {
                    resultWrites++; pending++; return null;
                }
                return switch (method.getName()) {
                    case "next" -> ++index[0] == 1 && (!query.equals(BusinessWriteTransactions.LOCK_SQL) || guard != 0);
                    case "getInt" -> args[0].equals("guard_id") ? guard : 1;
                    case "getBoolean" -> switch ((String) args[0]) { case "legacy_active" -> legacy; case "schema2_remainder" -> schema2; default -> partition; };
                    case "getStatement" -> origin;
                    case "getMetaData" -> metadata(ResultSetMetaData.class);
                    case "getConcurrency" -> ResultSet.CONCUR_UPDATABLE;
                    default -> defaultValue(method.getReturnType());
                };
            });
        }
    }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return 0;
    }
}
