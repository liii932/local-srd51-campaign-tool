package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class RuleDatabaseSchemaVerifierTest {
    @Test
    void exactRoleAndWholeHistoryCloseResourcesWithoutMutation() throws Exception {
        Fixture f = new Fixture();
        new RuleDatabaseSchemaVerifier().verify(f.source());
        assertEquals(List.of(2, 2), f.limits);
        assertEquals(List.of(5, 5), f.timeouts);
        assertEquals(2, f.closedResults);
        assertEquals(2, f.closedStatements);
        assertTrue(f.closedConnection);
    }

    @Test
    void wrongOrMissingDefaultSchemaStopsBeforeLedgerQuery() {
        for (byte[] schema : new byte[][] {null, bytes("dnd_tool_se"), bytes("DND_TOOL_RULES"),
                bytes("dnd_tool_rules "), new byte[] {(byte) 0xff}}) {
            Fixture f = new Fixture();
            f.schema = schema;
            mismatch(f);
            assertEquals(1, f.prepared);
            assertEquals(1, f.closedResults);
        }
    }

    @Test
    void emptyDuplicateExtraAndEveryMalformedColumnFailClosed() {
        List<List<Object[]>> histories = new ArrayList<>();
        histories.add(List.of());
        histories.add(Arrays.asList(row(), row()));
        Object[] extra = row(); extra[0] = 2L;
        histories.add(Arrays.asList(row(), extra));
        for (int column = 0; column < 3; column++) {
            Object[] nul = row(); nul[column] = null;
            histories.add(java.util.Collections.singletonList(nul));
        }
        for (long version : new long[] {0, -1, 2, 4294967297L}) {
            Object[] bad = row(); bad[0] = version;
            histories.add(java.util.Collections.singletonList(bad));
        }
        for (int column : new int[] {1, 2}) {
            for (byte[] value : new byte[][] {bytes(""), bytes("wrong"), new byte[] {(byte) 0xff},
                    bytes(new String((byte[]) row()[column], StandardCharsets.US_ASCII) + " ")}) {
                Object[] bad = row(); bad[column] = value;
                histories.add(java.util.Collections.singletonList(bad));
            }
        }
        for (List<Object[]> history : histories) {
            Fixture f = new Fixture(); f.history = history;
            mismatch(f);
            assertEquals(2, f.closedResults);
            assertEquals(2, f.closedStatements);
        }
    }

    @Test
    void sqlFailuresAlwaysCloseAcquiredResources() {
        for (String failure : List.of("prepare1", "execute1", "next1", "prepare2", "execute2", "next2")) {
            Fixture f = new Fixture(); f.failure = failure;
            assertThrows(SQLException.class, () -> new RuleDatabaseSchemaVerifier().verify(f.source()));
            assertTrue(f.closedConnection, failure);
            assertEquals(f.openedStatements, f.closedStatements, failure);
            assertEquals(f.openedResults, f.closedResults, failure);
        }
    }

    private static void mismatch(Fixture f) {
        assertThrows(RuleDatabaseSchemaVerifier.SchemaMismatchException.class,
                () -> new RuleDatabaseSchemaVerifier().verify(f.source()));
        assertTrue(f.closedConnection);
    }

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.US_ASCII); }

    private static Object[] row() {
        var e = RuleSchemaMigrations.expectations().getFirst();
        return new Object[] {(long) e.version(), bytes(e.scriptName()), bytes(e.scriptSha256())};
    }

    /** Unexpected JDBC methods fail: transaction ownership or mutation cannot hide behind defaults. */
    private static final class Fixture {
        byte[] schema = bytes("dnd_tool_rules");
        List<Object[]> history = java.util.Collections.singletonList(row());
        String failure = "";
        boolean closedConnection;
        int prepared, openedStatements, closedStatements, openedResults, closedResults;
        List<Integer> limits = new ArrayList<>(), timeouts = new ArrayList<>();

        DataSource source() {
            return proxy(DataSource.class, (p, m, a) -> {
                if (!m.getName().equals("getConnection")) throw new AssertionError(m);
                return proxy(Connection.class, (cp, cm, ca) -> {
                    if (cm.getName().equals("close")) { closedConnection = true; return null; }
                    if (!cm.getName().equals("prepareStatement")) throw new AssertionError(cm);
                    int query = ++prepared;
                    assertEquals(query == 1 ? "SELECT CAST(DATABASE() AS BINARY)"
                            : "SELECT schema_version, script_name, script_sha256 FROM rule_schema_meta ORDER BY schema_version ASC",
                            ((String) ca[0]).replaceAll("\\s+", " ").trim());
                    fail("prepare" + query);
                    openedStatements++;
                    return proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
                        case "setMaxRows" -> { limits.add((int) sa[0]); yield null; }
                        case "setQueryTimeout" -> { timeouts.add((int) sa[0]); yield null; }
                        case "close" -> { closedStatements++; yield null; }
                        case "executeQuery" -> { fail("execute" + query); yield result(query); }
                        default -> throw new AssertionError(sm);
                    });
                });
            });
        }

        ResultSet result(int query) {
            openedResults++;
            List<Object[]> rows = query == 1 ? java.util.Collections.singletonList(new Object[] {schema}) : history;
            int[] cursor = {-1}; boolean[] wasNull = {false};
            return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
                case "next" -> { fail("next" + query); yield ++cursor[0] < rows.size(); }
                case "close" -> { closedResults++; yield null; }
                case "wasNull" -> wasNull[0];
                case "getBytes", "getLong" -> {
                    int column = a[0] instanceof Integer ? 0 : switch ((String) a[0]) {
                        case "schema_version" -> 0; case "script_name" -> 1; case "script_sha256" -> 2;
                        default -> throw new AssertionError(a[0]);
                    };
                    Object value = rows.get(cursor[0])[column]; wasNull[0] = value == null;
                    yield value == null && m.getName().equals("getLong") ? 0L : value;
                }
                default -> throw new AssertionError(m);
            });
        }

        void fail(String point) throws SQLException { if (failure.equals(point)) throw new SQLException("synthetic"); }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
