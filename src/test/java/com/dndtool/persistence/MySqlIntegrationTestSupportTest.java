package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.TestAbortedException;

/** Exercises both connection entry paths with complete inputs and no network-capable factory. */
final class MySqlIntegrationTestSupportTest {
    private static final String USER = "disposable-test-user";
    private static final String PASSWORD = "dummy-not-a-database-password";
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/dnd_tool_se_it";
    private static final String TARGET_REJECTION =
            "Integration tests require an unambiguous dnd_tool_se_it JDBC target";
    private static final List<String> SQL = List.of(
            "SELECT 1", "CREATE TEMPORARY TABLE fixture (id INT)", "INSERT INTO fixture VALUES (1)");

    @ParameterizedTest
    @MethodSource("rejectedTargets")
    void directEntryRejectsBeforeConnectingOrExecutingSql(String url) {
        RecordingConnections calls = new RecordingConnections();
        TestAbortedException rejected = assertThrows(TestAbortedException.class, () -> {
            try (Connection connection = MySqlIntegrationTestSupport.open(enabledInputs(url), calls)) {
                exerciseSql(connection);
            }
        });
        assertTrue(rejected.getMessage().contains(TARGET_REJECTION));
        calls.assertNoActivity();
    }

    @ParameterizedTest
    @MethodSource("rejectedTargets")
    void pooledEntryRejectsBeforeConstructingInitializingBorrowingOrExecutingSql(String url) {
        RecordingConnections calls = new RecordingConnections();
        TestAbortedException rejected = assertThrows(TestAbortedException.class, () -> {
            try (BasicDataSource pool = MySqlIntegrationTestSupport.pooledDataSource(enabledInputs(url), calls)) {
                pool.start();
                try (Connection connection = pool.getConnection()) {
                    exerciseSql(connection);
                }
            }
        });
        assertTrue(rejected.getMessage().contains(TARGET_REJECTION));
        calls.assertNoActivity();
    }

    @ParameterizedTest
    @MethodSource("allowedTargets")
    void dedicatedDirectTargetReachesConnectionFactory(String url) throws Exception {
        RecordingConnections calls = new RecordingConnections();
        try (Connection connection = MySqlIntegrationTestSupport.open(enabledInputs(url), calls)) {
            assertSame(calls.connection, connection);
            exerciseSql(connection);
        }
        assertEquals(1, calls.connectionCalls);
        assertEquals(List.of(url, USER, PASSWORD), calls.arguments);
        assertEquals(SQL, calls.sql);
        assertEquals(0, calls.poolConstructions);
    }

    @ParameterizedTest
    @MethodSource("allowedTargets")
    void dedicatedPooledTargetReachesFactoryAndKeepsLazyConnectionSettings(String url) throws Exception {
        RecordingConnections calls = new RecordingConnections();
        try (BasicDataSource pool = MySqlIntegrationTestSupport.pooledDataSource(enabledInputs(url), calls)) {
            assertEquals(1, calls.poolConstructions);
            assertEquals(url, pool.getUrl());
            assertEquals(USER, pool.getUsername());
            assertEquals(PASSWORD, pool.getPassword());
            assertEquals("com.mysql.cj.jdbc.Driver", pool.getDriverClassName());
            assertEquals(1, pool.getInitialSize());
            assertEquals(1, pool.getMaxTotal());
            assertEquals("SELECT 1", pool.getValidationQuery());
            assertEquals(0, calls.poolInitializations);
            assertEquals(0, calls.poolBorrows);
            assertEquals(0, calls.connectionCalls);
            assertTrue(calls.sql.isEmpty());
            // Prove the same injected pool detects initialization, borrowing and every SQL category.
            pool.start();
            try (Connection connection = pool.getConnection()) {
                exerciseSql(connection);
            }
        }
        assertEquals(1, calls.poolInitializations);
        assertEquals(1, calls.poolBorrows);
        assertEquals(1, calls.connectionCalls);
        assertEquals(SQL, calls.sql);
    }

    @Test
    void existingOptInAndCompleteConfigurationGatesRemainBeforeFactories() {
        List<MySqlIntegrationTestSupport.Inputs> inputs = List.of(
                new MySqlIntegrationTestSupport.Inputs(false, URL, USER, PASSWORD, true),
                new MySqlIntegrationTestSupport.Inputs(true, "", USER, PASSWORD, true),
                new MySqlIntegrationTestSupport.Inputs(true, URL, "", PASSWORD, true),
                new MySqlIntegrationTestSupport.Inputs(true, URL, USER, null, true),
                new MySqlIntegrationTestSupport.Inputs(true, URL, USER, PASSWORD, false));
        for (MySqlIntegrationTestSupport.Inputs input : inputs) {
            RecordingConnections calls = new RecordingConnections();
            assertThrows(TestAbortedException.class, () -> MySqlIntegrationTestSupport.open(input, calls));
            assertThrows(TestAbortedException.class,
                    () -> MySqlIntegrationTestSupport.pooledDataSource(input, calls));
            calls.assertNoActivity();
        }
    }

    private static MySqlIntegrationTestSupport.Inputs enabledInputs(String url) {
        return new MySqlIntegrationTestSupport.Inputs(true, url, USER, PASSWORD, true);
    }

    private static Stream<String> allowedTargets() {
        return Stream.of(URL, URL + "?connectionTimeZone=UTC",
                "jdbc:mysql://localhost/dnd_tool_se_it",
                "jdbc:mysql://[::1]:3306/dnd_tool_se_it");
    }

    private static Stream<String> rejectedTargets() {
        return Stream.of(
                "jdbc:mysql://127.0.0.1:3306/dnd_tool_se",
                "jdbc:mysql://127.0.0.1:3306/dnd_tool_rules",
                "jdbc:mysql://127.0.0.1:3306/DND_TOOL_SE_IT",
                "jdbc:mysql://127.0.0.1:3306/other",
                "jdbc:mysql://127.0.0.1:3306/",
                "jdbc:mysql://127.0.0.1:3306",
                "jdbc:mysql:///dnd_tool_se_it",
                "jdbc:mysql:dnd_tool_se_it",
                "not-a-jdbc-url",
                "jdbc:postgresql://127.0.0.1:3306/dnd_tool_se_it",
                "JDBC:mysql://127.0.0.1:3306/dnd_tool_se_it",
                "jdbc:mysql:loadbalance://127.0.0.1:3306/dnd_tool_se_it",
                "jdbc:mysql:replication://127.0.0.1:3306/dnd_tool_se_it",
                "jdbc:mysql+srv://localhost/dnd_tool_se_it",
                "jdbc:mysql://127.0.0.1,localhost/dnd_tool_se_it",
                "jdbc:mysql://address=(host=localhost)(dbname=dnd_tool_rules)/dnd_tool_se_it",
                "jdbc:mysql://user:password@localhost/dnd_tool_se_it",
                "jdbc:mysql://localhost:0/dnd_tool_se_it",
                "jdbc:mysql://localhost:65536/dnd_tool_se_it",
                "jdbc:mysql://localhost:-1/dnd_tool_se_it",
                "jdbc:mysql://localhost:/dnd_tool_se_it",
                "jdbc:mysql://localhost:abc/dnd_tool_se_it",
                "jdbc:mysql://[::1/dnd_tool_se_it",
                "jdbc:mysql://local host/dnd_tool_se_it",
                "jdbc:mysql://local\nhost/dnd_tool_se_it",
                "jdbc:mysql://local\\host/dnd_tool_se_it",
                "jdbc:mysql://localhost/%64nd_tool_se_it",
                "jdbc:mysql://localhost/dnd_tool_se_it%2f..%2fdnd_tool_rules",
                "jdbc:mysql://localhost/dnd_tool_se_it%00",
                "jdbc:mysql://localhost/dnd_tool_se_it%ZZ",
                URL + "/", URL + "/../dnd_tool_rules", URL + ";databaseName=dnd_tool_rules",
                URL + "#dnd_tool_rules", URL + "?", URL + "?databaseName=dnd_tool_rules",
                URL + "?DBNAME=dnd_tool_rules", URL + "?dbname=dnd_tool_se_it",
                URL + "?user=other", URL + "?host=elsewhere", URL + "?port=3307",
                URL + "?propertiesTransform=other.Transform", URL + "?useConfigs=other",
                URL + "?sessionVariables=sql_mode='ANSI'", URL + "?initSQL=USE+dnd_tool_rules",
                URL + "?connectionTimeZone=UTC&DBNAME=dnd_tool_rules",
                URL + "?connectionTimeZone=UTC&connectionTimeZone=UTC",
                URL + "?connectionTimeZone=UTC;DBNAME=dnd_tool_rules",
                URL + "?%64bname=dnd_tool_rules", URL + "?connectionTimeZone=%55TC");
    }

    private static void exerciseSql(Connection connection) throws Exception {
        for (String sql : SQL) {
            MySqlIntegrationTestSupport.execute(connection, sql);
        }
    }

    private static final class RecordingConnections
            implements MySqlIntegrationTestSupport.ConnectionFactory, Supplier<BasicDataSource> {
        private int connectionCalls;
        private int poolConstructions;
        private int poolInitializations;
        private int poolBorrows;
        private List<String> arguments = List.of();
        private final List<String> sql = new ArrayList<>();
        private final Statement statement = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("execute")) {
                        sql.add((String) args[0]);
                        return false;
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new AssertionError("Unexpected statement operation: " + method.getName());
                });
        private final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createStatement")) {
                        return statement;
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new AssertionError("Unexpected connection operation: " + method.getName());
                });

        @Override
        public Connection open(String url, String user, String password) {
            connectionCalls++;
            arguments = List.of(url, user, password);
            return connection;
        }

        @Override
        public BasicDataSource get() {
            poolConstructions++;
            return new BasicDataSource() {
                @Override
                public synchronized void start() {
                    poolInitializations++;
                }

                @Override
                public Connection getConnection() {
                    poolBorrows++;
                    connectionCalls++;
                    return connection;
                }
            };
        }

        private void assertNoActivity() {
            assertEquals(0, connectionCalls, "physical connection factory calls");
            assertEquals(0, poolConstructions, "pool constructions");
            assertEquals(0, poolInitializations, "pool initialization calls");
            assertEquals(0, poolBorrows, "pool getConnection calls");
            assertTrue(sql.isEmpty(), "no probes, temporary DDL or DML");
        }
    }
}
