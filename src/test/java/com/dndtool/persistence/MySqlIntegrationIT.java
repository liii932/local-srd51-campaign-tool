package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;

/**
 * Real MySQL checks. They are intentionally named IT and require an explicit disposable DB.
 * Temporary tables keep the suite isolated from the application's schema and business data.
 */
final class MySqlIntegrationIT {
    private static final String CHARACTER_KEY = "11111111-1111-4111-8111-111111111111";

    @Test
    void characterDaoCommitsOptimisticWriteAndRollsBackFailure() throws Exception {
        try (Connection physical = MySqlIntegrationTestSupport.open()) {
            createCharacterTables(physical);
            insertCharacter(physical, 0);
            DataSource dataSource = MySqlIntegrationTestSupport.singleConnectionDataSource(physical);
            JdbcCharacterAggregateMutationRepository repository =
                    new JdbcCharacterAggregateMutationRepository(dataSource);

            CharacterAggregateMutationRepository.Result updated = repository.mutate(
                    new CharacterAggregateMutationRepository.Command(CHARACTER_KEY, 0),
                    (connection, character) -> insertChild(connection, character.id(), "ok"));
            assertEquals(CharacterAggregateMutationRepository.Result.Status.UPDATED, updated.status());
            assertEquals(1L, updated.rowVersion());
            assertEquals(1, scalarInt(physical, "SELECT row_version FROM character_record"));
            assertEquals(1, scalarInt(physical, "SELECT COUNT(*) FROM test_character_child"));
            assertTrue(physical.getAutoCommit());

            CharacterAggregateMutationRepository.Result conflict = repository.mutate(
                    new CharacterAggregateMutationRepository.Command(CHARACTER_KEY, 0),
                    (connection, character) -> { throw new SQLException("stale version wrote"); });
            assertEquals(CharacterAggregateMutationRepository.Result.Status.VERSION_CONFLICT,
                    conflict.status());
            assertEquals(1L, conflict.rowVersion());
            assertEquals(1, scalarInt(physical, "SELECT COUNT(*) FROM test_character_child"));

            SQLException failure = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,
                    () -> repository.mutate(
                            new CharacterAggregateMutationRepository.Command(CHARACTER_KEY, 1),
                            (connection, character) -> {
                                insertChild(connection, character.id(), "rolled-back");
                                throw new SQLException("injected integration failure");
                            }));
            assertEquals("injected integration failure", failure.getMessage());
            assertEquals(1, scalarInt(physical, "SELECT row_version FROM character_record"));
            assertEquals(1, scalarInt(physical, "SELECT COUNT(*) FROM test_character_child"));
            assertTrue(physical.getAutoCommit());
        }
    }

    @Test
    void productionPoolReusesConnectionWithRestoredState() throws Exception {
        try (BasicDataSource dataSource = MySqlIntegrationTestSupport.pooledDataSource()) {
            try (Connection setup = dataSource.getConnection()) {
                createCharacterTables(setup);
                insertCharacter(setup, 0);
            }

            JdbcCharacterAggregateMutationRepository repository =
                    new JdbcCharacterAggregateMutationRepository(dataSource);
            CharacterAggregateMutationRepository.Result result = repository.mutate(
                    new CharacterAggregateMutationRepository.Command(CHARACTER_KEY, 0),
                    (connection, character) -> insertChild(connection, character.id(), "pooled"));
            assertEquals(CharacterAggregateMutationRepository.Result.Status.UPDATED, result.status());

            try (Connection reused = dataSource.getConnection()) {
                assertTrue(reused.getAutoCommit());
                assertEquals(false, reused.isReadOnly());
                assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                        reused.getTransactionIsolation());
                assertEquals(1, scalarInt(reused, "SELECT row_version FROM character_record"));
                assertEquals(1, scalarInt(reused, "SELECT COUNT(*) FROM test_character_child"));
            }
            assertEquals(1, dataSource.getNumIdle());
            assertEquals(0, dataSource.getNumActive());
        }
    }

    private static void createBusinessGuardTables(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE business_write_guard (guard_id INT PRIMARY KEY, "
                        + "protocol_version INT NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "INSERT INTO business_write_guard VALUES (1, 1)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE s2_runtime_identity (run_id CHAR(36) PRIMARY KEY)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE runtime_run_identity (run_id CHAR(36) PRIMARY KEY)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE IF NOT EXISTS campaign (campaign_status VARCHAR(32))");
    }

    private static void createCharacterTables(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE character_record ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT, campaign_id BIGINT NOT NULL, "
                        + "module_release_id BIGINT NOT NULL, character_key CHAR(36) NOT NULL, "
                        + "row_version BIGINT NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE test_character_child (character_id BIGINT NOT NULL, value_text VARCHAR(64))");
        createBusinessGuardTables(connection);
    }

    private static void insertCharacter(Connection connection, long rowVersion) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO character_record(campaign_id,module_release_id,character_key,row_version) VALUES(7,9,?,?)")) {
            statement.setString(1, CHARACTER_KEY);
            statement.setLong(2, rowVersion);
            statement.executeUpdate();
        }
    }

    private static void insertChild(Connection connection, long id, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO test_character_child(character_id,value_text) VALUES(?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private static int scalarInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
