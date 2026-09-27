package com.dndtool.persistence;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.function.Supplier;
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
        return open(inputs(), DriverManager::getConnection);
    }

    static Connection open(Inputs inputs, ConnectionFactory connections) throws SQLException {
        Configuration configuration = configuration(inputs);
        return connections.open(
                configuration.url(), configuration.user(), configuration.password());
    }

    static BasicDataSource pooledDataSource() {
        return pooledDataSource(inputs(), BasicDataSource::new);
    }

    static BasicDataSource pooledDataSource(Inputs inputs, Supplier<BasicDataSource> pools) {
        Configuration configuration = configuration(inputs);
        BasicDataSource dataSource = pools.get();
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

    private static Inputs inputs() {
        return new Inputs(Boolean.getBoolean(ENABLE_PROPERTY),
                System.getProperty(URL_PROPERTY, ""), System.getProperty(USER_PROPERTY, ""),
                System.getenv(PASSWORD_ENV), Boolean.getBoolean(CONFIRM_PROPERTY));
    }

    private static Configuration configuration(Inputs inputs) {
        assumeTrue(inputs.enabled(),
                "Set -Ddnd.mysql.integration=true to run MySQL integration tests");
        String url = inputs.url().trim();
        String user = inputs.user().trim();
        String password = inputs.password();
        assumeTrue(!url.isEmpty() && !user.isEmpty() && password != null,
                "Provide the integration URL/user and DND_MYSQL_INTEGRATION_PASSWORD");
        assumeTrue(inputs.confirmWritable(),
                "Set -Ddnd.mysql.integration.confirmWritable=true for disposable DB writes");
        assumeTrue(isDedicatedTestTarget(url),
                "Integration tests require an unambiguous dnd_tool_se_it JDBC target");
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

    private static boolean isDedicatedTestTarget(String url) {
        // Deliberately accept a small URL grammar, not every Connector/J extension.
        // In particular, properties can override the schema or execute session SQL.
        if (!url.startsWith("jdbc:mysql://") || url.indexOf('%') >= 0) {
            return false;
        }
        try {
            URI target = new URI(url.substring("jdbc:".length()));
            int port = target.getPort();
            String host = target.getHost();
            if (host == null || target.getRawUserInfo() != null || target.getRawFragment() != null
                    || port == 0 || port > 65535
                    || !"/dnd_tool_se_it".equals(target.getRawPath())) {
                return false;
            }
            String authority = host + (port < 0 ? "" : ":" + port);
            if (!authority.equals(target.getRawAuthority())) {
                return false;
            }
            String query = target.getRawQuery();
            return query == null || query.equals("connectionTimeZone=UTC");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    record Inputs(boolean enabled, String url, String user, String password, boolean confirmWritable) {
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open(String url, String user, String password) throws SQLException;
    }

    private record Configuration(String url, String user, String password) {
    }
}
