package com.dndtool.persistence;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;

/** Explicitly opt-in support for a disposable, writable MySQL integration database. */
final class MySqlIntegrationTestSupport {
    private static final String ENABLE_PROPERTY = "dnd.mysql.integration";
    private static final String URL_PROPERTY = "dnd.mysql.integration.url";
    private static final String USER_PROPERTY = "dnd.mysql.integration.user";
    private static final String PASSWORD_ENV = "DND_MYSQL_INTEGRATION_PASSWORD";
    private static final String CONFIRM_PROPERTY = "dnd.mysql.integration.confirmWritable";

    private MySqlIntegrationTestSupport() {
    }

    static Connection open() throws SQLException {
        Configuration configuration = configuration();
        return DriverManager.getConnection(
                configuration.url(), configuration.user(), configuration.password());
    }

    static BasicDataSource pooledDataSource() {
        Configuration configuration = configuration();
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(configuration.url());
        dataSource.setUsername(configuration.user());
        dataSource.setPassword(configuration.password());
        dataSource.setInitialSize(1);
        dataSource.setMinIdle(1);
        dataSource.setMaxIdle(1);
        dataSource.setMaxTotal(1);
        dataSource.setMaxWait(Duration.ofSeconds(3));
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setValidationQueryTimeout(Duration.ofSeconds(3));
        dataSource.setTestWhileIdle(true);
        dataSource.setDefaultAutoCommit(true);
        dataSource.setDefaultReadOnly(false);
        dataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return dataSource;
    }

    private static Configuration configuration() {
        assumeTrue(Boolean.getBoolean(ENABLE_PROPERTY),
                "Set -Ddnd.mysql.integration=true to run MySQL integration tests");
        String url = System.getProperty(URL_PROPERTY, "").trim();
        String user = System.getProperty(USER_PROPERTY, "").trim();
        String password = System.getenv(PASSWORD_ENV);
        assumeTrue(!url.isEmpty() && !user.isEmpty() && password != null,
                "Provide the integration URL/user and DND_MYSQL_INTEGRATION_PASSWORD");
        assumeTrue(Boolean.getBoolean(CONFIRM_PROPERTY),
                "Set -Ddnd.mysql.integration.confirmWritable=true for disposable DB writes");
        assumeTrue(!databaseName(url).equalsIgnoreCase("dnd_tool_se"),
                "Integration tests refuse the project database");
        return new Configuration(url, user, password);
    }

    static DataSource singleConnectionDataSource(Connection connection) {
        Connection nonClosing = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    try {
                        return method.invoke(connection, arguments);
                    } catch (java.lang.reflect.InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
        return new DataSource() {
            @Override
            public Connection getConnection() {
                return nonClosing;
            }

            @Override
            public Connection getConnection(String username, String password) {
                return nonClosing;
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getGlobal();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("Not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String databaseName(String url) {
        int slash = url.indexOf('/', url.indexOf("://") + 3);
        if (slash < 0) {
            return "";
        }
        int end = url.indexOf('?', slash);
        return url.substring(slash + 1, end < 0 ? url.length() : end);
    }

    private record Configuration(String url, String user, String password) {
    }
}
