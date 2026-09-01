package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.CampaignArchiveDocument;
import com.dndtool.service.D20CheckCalculator;
import com.dndtool.service.CharacterVersionService;
import com.dndtool.service.CheckRequestDigest;
import com.dndtool.service.CheckRequestPolicy;
import com.dndtool.service.CheckTransactionService;
import com.dndtool.service.CheckEffectExecutionService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;

/**
 * Real MySQL checks. They are intentionally named IT and require an explicit disposable DB.
 * Temporary tables keep the suite isolated from the application's schema and business data.
 */
final class MySqlIntegrationIT {
    private static final String CHARACTER_KEY = "11111111-1111-4111-8111-111111111111";
    private static final String EXECUTOR_CHARACTER_KEY = "44444444-4444-4444-8444-444444444444";
    private static final String TARGET_CHARACTER_KEY = "55555555-5555-4555-8555-555555555555";
    private static final String REQUEST_ID = "22222222-2222-4222-8222-222222222222";
    private static final String REQUEST_DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CAMPAIGN_KEY = "33333333-3333-4333-8333-333333333333";
    private static final String MODULE_HASH =
            "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e";

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
    void idempotencyDaoPersistsAndReplaysOneImmutableResult() throws Exception {
        try (Connection connection = MySqlIntegrationTestSupport.open()) {
            createIdempotencyTables(connection);
            insertCheckSnapshot(connection);
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            JdbcCheckIdempotencyRepository repository = new JdbcCheckIdempotencyRepository();
            CheckIdempotencyRepository.Command command =
                    new CheckIdempotencyRepository.Command(REQUEST_ID, REQUEST_DIGEST, 7);
            assertEquals(CheckIdempotencyRepository.Status.NEW,
                    repository.find(connection, command).status());
            repository.complete(connection, new CheckIdempotencyRepository.Completion(
                    REQUEST_ID, REQUEST_DIGEST, 7, 91));
            connection.commit();

            connection.setAutoCommit(false);
            CheckIdempotencyRepository.Lookup replay = repository.find(connection, command);
            assertEquals(CheckIdempotencyRepository.Status.REPLAY, replay.status());
            assertEquals(91L, replay.replay().savedCheck().gameEventId());
            assertEquals(17, replay.replay().calculation().selectedValue());
            CheckIdempotencyRepository.Lookup conflict = repository.find(connection,
                    new CheckIdempotencyRepository.Command(REQUEST_ID,
                            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", 7));
            assertEquals(CheckIdempotencyRepository.Status.CONFLICT, conflict.status());
            connection.rollback();
            connection.setAutoCommit(true);
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void archiveImportRowsDisappearWhenCallerRollsBack() throws Exception {
        try (Connection connection = MySqlIntegrationTestSupport.open()) {
            createArchiveTables(connection);
            insertRelease(connection);
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            CampaignArchiveDocument document = emptyArchive();
            long campaignId = new JdbcCampaignArchiveImportRepository().importArchive(
                    connection,
                    new CampaignArchiveImportRepository.Command(document, null, 987654321L));
            assertTrue(campaignId > 0);
            assertEquals(1, scalarInt(connection, "SELECT COUNT(*) FROM campaign"));
            assertEquals(1, scalarInt(connection, "SELECT COUNT(*) FROM campaign_module"));
            connection.rollback();
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM campaign"));
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM campaign_module"));
            connection.setAutoCommit(true);
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

    @Test
    void checkAuthoritativeRowsCommitAtomically() throws Exception {
        try (Connection connection = MySqlIntegrationTestSupport.open()) {
            createAtomicCheckTables(connection);
            insertAtomicCheckFixture(connection);
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            D20CheckCalculator.Result calculation = new D20CheckCalculator.Result(
                    "roll.advantage",
                    List.of(new D20CheckCalculator.Candidate(1, 12, false),
                            new D20CheckCalculator.Candidate(2, 18, true)),
                    18, 2, 20, 15, D20CheckCalculator.Outcome.SUCCESS);
            CheckExecutionRepository.SavedCheck saved = new JdbcCheckExecutionRepository().append(
                    connection,
                    new CheckExecutionRepository.Command(
                            7, 0, 9, 1, "event.ability_check", "check.ability",
                            "ability.strength", null, calculation));

            CheckRequestPolicy.PreparedEffect plannedHp = new CheckRequestPolicy.PreparedEffect(
                    "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                            new CheckRequestPolicy.PreparedParameter(
                                    "target_character", 1, "REFERENCE",
                                    new CheckRequestPolicy.ReferenceValue(TARGET_CHARACTER_KEY)),
                            new CheckRequestPolicy.PreparedParameter(
                                    "amount", 2, "INTEGER",
                                    new CheckRequestPolicy.IntegerValue(-3))));
            CheckEffectPlanRepository.BranchPlan success = new CheckEffectPlanRepository.BranchPlan(
                    CheckEffectPlanRepository.EffectBranch.SUCCESS,
                    List.of(new CheckEffectPlanRepository.EffectPlan(1, plannedHp)));
            CheckEffectPlanRepository.BranchPlan failure = new CheckEffectPlanRepository.BranchPlan(
                    CheckEffectPlanRepository.EffectBranch.FAILURE,
                    List.of(new CheckEffectPlanRepository.EffectPlan(1,
                            new CheckRequestPolicy.PreparedEffect(
                                    "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1", List.of(
                                            new CheckRequestPolicy.PreparedParameter(
                                                    "message", 1, "TEXT",
                                                    new CheckRequestPolicy.TextValue("failure")))))));
            new JdbcCheckEffectPlanRepository().append(connection,
                    new CheckEffectPlanRepository.Command(saved.checkExecutionId(), 9, success, failure));

            CheckEffectExecutionRepository.AppliedEffects applied =
                    new JdbcCheckEffectExecutionRepository().execute(
                            connection,
                            new CheckEffectExecutionRepository.Command(
                                    saved.checkExecutionId(), 7, 9, saved.gameEventId(),
                                    new CheckEffectExecutionRepository.BranchActions(
                                            CheckEffectPlanRepository.EffectBranch.SUCCESS,
                                            List.of(new CheckEffectExecutionRepository.AdjustCurrentHp(
                                                    1, 2, TARGET_CHARACTER_KEY, -3))),
                                    new CheckEffectExecutionRepository.BranchActions(
                                            CheckEffectPlanRepository.EffectBranch.FAILURE, List.of())));
            assertEquals(Set.of(2L), applied.modifiedCharacterIds());
            assertEquals(1, applied.fieldChangeCount());

            CharacterVersionRepository.LockedCharacter executor =
                    new CharacterVersionRepository.LockedCharacter(
                            1, EXECUTOR_CHARACTER_KEY, 7, 9, 0);
            CharacterVersionRepository.LockedCharacter target =
                    new CharacterVersionRepository.LockedCharacter(
                            2, TARGET_CHARACTER_KEY, 7, 9, 0);
            Map<Long, Long> versions = new JdbcCharacterVersionRepository()
                    .advanceModifiedVersions(
                            connection,
                            new CharacterVersionRepository.LockedScope(
                                    7, 9, 0, executor, List.of(executor, target)),
                            Set.of(2L));
            assertEquals(Map.of(2L, 1L), versions);

            new JdbcCheckIdempotencyRepository().complete(
                    connection,
                    new CheckIdempotencyRepository.Completion(
                            REQUEST_ID, REQUEST_DIGEST, 7, saved.gameEventId()));
            connection.commit();

            assertEquals(1, scalarInt(connection,
                    "SELECT COUNT(*) FROM game_event WHERE event_sequence = 1"));
            assertEquals(1, scalarInt(connection,
                    "SELECT COUNT(*) FROM check_execution WHERE id = " + saved.checkExecutionId()));
            assertEquals(2, scalarInt(connection,
                    "SELECT COUNT(*) FROM dice_roll WHERE check_execution_id = "
                            + saved.checkExecutionId()));
            assertEquals(2, scalarInt(connection,
                    "SELECT COUNT(*) FROM check_effect WHERE check_execution_id = "
                            + saved.checkExecutionId()));
            assertEquals(1, scalarInt(connection,
                    "SELECT COUNT(*) FROM check_effect WHERE check_execution_id = "
                            + saved.checkExecutionId() + " AND effect_branch = 'FAILURE'"));
            assertEquals(17, scalarInt(connection,
                    "SELECT integer_value FROM character_field_value WHERE character_id = 2 "
                            + "AND field_key = 'hp.current'"));
            assertEquals(1, scalarInt(connection,
                    "SELECT COUNT(*) FROM field_change WHERE game_event_id = " + saved.gameEventId()));
            assertEquals(1, scalarInt(connection,
                    "SELECT row_version FROM character_record WHERE id = 2"));
            assertEquals(1, scalarInt(connection,
                    "SELECT internal_event_tail FROM campaign WHERE id = 7"));
            assertEquals(1, scalarInt(connection,
                    "SELECT COUNT(*) FROM host_operation WHERE request_id = '" + REQUEST_ID + "'"));
            assertEquals(1, scalarInt(connection,
                    "SELECT COUNT(*) FROM dice_roll WHERE check_execution_id = "
                            + saved.checkExecutionId() + " AND is_selected = 1"));
            assertEquals(1, scalarInt(connection,
                    "SELECT COUNT(*) FROM game_event WHERE id = " + saved.gameEventId()
                            + " AND event_text IS NULL"));
        }
    }

    @Test
    void failedCheckRollsBackEventTailAndAllAuthoritativeRows() throws Exception {
        try (Connection connection = MySqlIntegrationTestSupport.open()) {
            createAtomicCheckTables(connection);
            insertAtomicCheckFixture(connection);
            int originalIsolation = connection.getTransactionIsolation();
            boolean originalReadOnly = connection.isReadOnly();

            JdbcCheckIdempotencyRepository realIdempotency =
                    new JdbcCheckIdempotencyRepository();
            CheckIdempotencyRepository failAtCompletion =
                    new CheckIdempotencyRepository() {
                        @Override
                        public Lookup find(Connection transaction, Command command)
                                throws SQLException {
                            return realIdempotency.find(transaction, command);
                        }

                        @Override
                        public void complete(Connection transaction, Completion completion)
                                throws SQLException {
                            throw new SQLException("injected final idempotency failure");
                        }
                    };

            ModuleCatalog catalog = hostCommandCatalog();
            JdbcCharacterVersionRepository versionRepository =
                    new JdbcCharacterVersionRepository();
            CheckTransactionService service = new CheckTransactionService(
                    MySqlIntegrationTestSupport.singleConnectionDataSource(connection),
                    new D20CheckCalculator(catalog, () -> 18),
                    new CharacterVersionService(versionRepository),
                    failAtCompletion,
                    new JdbcCheckExecutionRepository(),
                    new JdbcCheckEffectPlanRepository(),
                    new CheckEffectExecutionService(catalog),
                    new JdbcCheckEffectExecutionRepository());

            CharacterVersionRepository.VersionExpectation executor =
                    new CharacterVersionRepository.VersionExpectation(
                            EXECUTOR_CHARACTER_KEY, 0);
            List<CharacterVersionRepository.VersionExpectation> targets = List.of(
                    new CharacterVersionRepository.VersionExpectation(
                            TARGET_CHARACTER_KEY, 0));
            String payloadSha256 = "c".repeat(64);
            CharacterVersionService.Request versionRequest =
                    new CharacterVersionService.Request(
                            7, 9, payloadSha256,
                            CheckRequestDigest.sha256(payloadSha256, executor, targets),
                            executor, targets);
            CheckRequestPolicy.PreparedEffect hpEffect =
                    new CheckRequestPolicy.PreparedEffect(
                            "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                                    new CheckRequestPolicy.PreparedParameter(
                                            "target_character", 1, "REFERENCE",
                                            new CheckRequestPolicy.ReferenceValue(
                                                    TARGET_CHARACTER_KEY)),
                                    new CheckRequestPolicy.PreparedParameter(
                                            "amount", 2, "INTEGER",
                                            new CheckRequestPolicy.IntegerValue(-3))));
            CheckEffectPlanRepository.BranchPlan success =
                    new CheckEffectPlanRepository.BranchPlan(
                            CheckEffectPlanRepository.EffectBranch.SUCCESS,
                            List.of(new CheckEffectPlanRepository.EffectPlan(1, hpEffect)));
            CheckEffectPlanRepository.BranchPlan failure =
                    new CheckEffectPlanRepository.BranchPlan(
                            CheckEffectPlanRepository.EffectBranch.FAILURE, List.of());
            CheckRequestPolicy.PreparedRequest check =
                    new CheckRequestPolicy.PreparedRequest(
                            "check.ability", "ABILITY", "ABILITY_MODIFIER_V1",
                            "roll.normal", "NORMAL", "ONLY_CANDIDATE_V1", 1,
                            "ability.strength", null, null, 15, List.of());

            SQLException thrown = assertThrows(SQLException.class, () -> service.execute(
                    new CheckTransactionService.Request(
                            REQUEST_ID, versionRequest, check, 2, success, failure)));
            assertEquals("injected final idempotency failure", thrown.getMessage());

            assertTrue(connection.getAutoCommit());
            assertEquals(originalReadOnly, connection.isReadOnly());
            assertEquals(originalIsolation, connection.getTransactionIsolation());
            assertEquals(0, scalarInt(connection,
                    "SELECT internal_event_tail FROM campaign WHERE id = 7"));
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM game_event"));
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM check_execution"));
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM dice_roll"));
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM check_effect"));
            assertEquals(0, scalarInt(connection,
                    "SELECT COUNT(*) FROM check_effect_parameter_value"));
            assertEquals(20, scalarInt(connection,
                    "SELECT integer_value FROM character_field_value WHERE character_id = 2 "
                            + "AND field_key = 'hp.current'"));
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM field_change"));
            assertEquals(0, scalarInt(connection,
                    "SELECT row_version FROM character_record WHERE id = 2"));
            assertEquals(0, scalarInt(connection, "SELECT COUNT(*) FROM host_operation"));
        }
    }

    private static void createCharacterTables(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE character_record ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT, campaign_id BIGINT NOT NULL, "
                        + "module_release_id BIGINT NOT NULL, character_key CHAR(36) NOT NULL, "
                        + "row_version BIGINT NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE test_character_child (character_id BIGINT NOT NULL, value_text VARCHAR(64))");
    }

    private static void createAtomicCheckTables(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE campaign (id BIGINT PRIMARY KEY, "
                        + "campaign_status VARCHAR(16) NOT NULL, "
                        + "internal_event_tail BIGINT NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE campaign_module (campaign_id BIGINT NOT NULL, "
                        + "module_release_id BIGINT NOT NULL, frozen_module_key VARCHAR(128) NOT NULL, "
                        + "frozen_release_version VARCHAR(64) NOT NULL, frozen_content_sha256 CHAR(64) NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE character_record (id BIGINT PRIMARY KEY, campaign_id BIGINT NOT NULL, "
                        + "module_release_id BIGINT NOT NULL, character_key CHAR(36) NOT NULL, "
                        + "character_status VARCHAR(16) NOT NULL, row_version BIGINT NOT NULL, "
                        + "saved_module_key VARCHAR(128) NOT NULL, saved_release_version VARCHAR(64) NOT NULL, "
                        + "saved_content_sha256 CHAR(64) NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE game_event (id BIGINT PRIMARY KEY AUTO_INCREMENT, campaign_id BIGINT NOT NULL, "
                        + "event_sequence BIGINT NOT NULL, event_type VARCHAR(64) NOT NULL, "
                        + "subject_character_id BIGINT NULL, event_text VARCHAR(2000) NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE check_execution (id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                        + "game_event_id BIGINT NOT NULL, campaign_id BIGINT NOT NULL, module_release_id BIGINT NOT NULL, "
                        + "executor_character_id BIGINT NOT NULL, event_key VARCHAR(64), check_key VARCHAR(64) NOT NULL, "
                        + "roll_mode_key VARCHAR(64) NOT NULL, modifier_source_key VARCHAR(128), manual_name VARCHAR(80), "
                        + "modifier_value INT NOT NULL, total_value INT NOT NULL, difficulty_class INT NOT NULL, "
                        + "check_result VARCHAR(16) NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE dice_roll (check_execution_id BIGINT NOT NULL, candidate_order INT NOT NULL, "
                        + "rolled_value INT NOT NULL, is_selected TINYINT NOT NULL, "
                        + "PRIMARY KEY(check_execution_id,candidate_order))");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE check_effect (id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                        + "check_execution_id BIGINT NOT NULL, module_release_id BIGINT NOT NULL, "
                        + "effect_branch VARCHAR(16) NOT NULL, effect_order INT NOT NULL, effect_key VARCHAR(128) NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE check_effect_parameter_value (id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                        + "check_effect_id BIGINT NOT NULL, module_release_id BIGINT NOT NULL, effect_key VARCHAR(128) NOT NULL, "
                        + "parameter_key VARCHAR(128) NOT NULL, parameter_order INT NOT NULL, value_type VARCHAR(16) NOT NULL, "
                        + "reference_value VARCHAR(255), integer_value BIGINT, decimal_value DECIMAL(38,18), "
                        + "text_value VARCHAR(2000), boolean_value TINYINT)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE character_field_value (character_id BIGINT NOT NULL, "
                        + "module_release_id BIGINT NOT NULL, field_key VARCHAR(128) NOT NULL, "
                        + "value_type VARCHAR(16) NOT NULL, integer_value BIGINT, "
                        + "PRIMARY KEY(character_id,field_key))");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE field_change (id BIGINT PRIMARY KEY AUTO_INCREMENT, game_event_id BIGINT NOT NULL, "
                        + "campaign_id BIGINT NOT NULL, character_id BIGINT NOT NULL, change_order INT NOT NULL, "
                        + "change_key VARCHAR(128) NOT NULL, value_type VARCHAR(16) NOT NULL, "
                        + "old_integer BIGINT, new_integer BIGINT)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE host_operation (request_id CHAR(36) PRIMARY KEY, "
                        + "request_digest_sha256 CHAR(64) NOT NULL, operation_type VARCHAR(64) NOT NULL, "
                        + "campaign_id BIGINT, game_event_id BIGINT, result_status VARCHAR(32) NOT NULL, completed_at TIMESTAMP NULL)");
    }

    private static void insertAtomicCheckFixture(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "INSERT INTO campaign VALUES(7,'ACTIVE',0)");
        MySqlIntegrationTestSupport.execute(connection,
                "INSERT INTO campaign_module VALUES(7,9,'dnd5e2014_srd51_se_v1','1','"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO character_record VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setLong(1, 1);
            statement.setLong(2, 7);
            statement.setLong(3, 9);
            statement.setString(4, EXECUTOR_CHARACTER_KEY);
            statement.setString(5, "ACTIVE");
            statement.setLong(6, 0);
            statement.setString(7, "dnd5e2014_srd51_se_v1");
            statement.setString(8, "1");
            statement.setString(9,
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            statement.executeUpdate();
            statement.setLong(1, 2);
            statement.setString(4, TARGET_CHARACTER_KEY);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO character_field_value VALUES(?,?,?,?,?)")) {
            statement.setLong(1, 2);
            statement.setLong(2, 9);
            statement.setString(3, "hp.current");
            statement.setString(4, "INTEGER");
            statement.setLong(5, 20);
            statement.executeUpdate();
            statement.setString(3, "hp.maximum");
            statement.setLong(5, 30);
            statement.executeUpdate();
        }
    }

    private static ModuleCatalog hostCommandCatalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1,
                        "SHA-256", "a".repeat(64), "RELEASED"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(
                        new ModuleCatalog.RollMode(
                                "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.advantage", "ADVANTAGE", 2,
                                "HIGHEST_FIRST_ON_TIE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.disadvantage", "DISADVANTAGE", 2,
                                "LOWEST_FIRST_ON_TIE_V1")),
                List.of(), List.of(), List.of(),
                List.of(
                        new ModuleCatalog.EffectDefinition(
                                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.grant_module_item", "GRANT_MODULE_ITEM_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1")),
                List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.entry", "Entry"),
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar", "Cellar")),
                List.of());
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

    private static void createIdempotencyTables(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE host_operation (request_id CHAR(36) PRIMARY KEY, "
                        + "request_digest_sha256 CHAR(64) NOT NULL, operation_type VARCHAR(64) NOT NULL, "
                        + "campaign_id BIGINT, game_event_id BIGINT, result_status VARCHAR(32) NOT NULL, completed_at TIMESTAMP NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE game_event (id BIGINT PRIMARY KEY, event_sequence BIGINT NOT NULL, "
                        + "event_type VARCHAR(64) NOT NULL, campaign_id BIGINT NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE check_execution (id BIGINT PRIMARY KEY, game_event_id BIGINT NOT NULL, "
                        + "campaign_id BIGINT NOT NULL, roll_mode_key VARCHAR(64) NOT NULL, modifier_value INT NOT NULL, "
                        + "total_value INT NOT NULL, difficulty_class INT NOT NULL, check_result VARCHAR(16) NOT NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE dice_roll (check_execution_id BIGINT NOT NULL, candidate_order INT NOT NULL, "
                        + "rolled_value INT NOT NULL, is_selected TINYINT NOT NULL, PRIMARY KEY(check_execution_id,candidate_order))");
    }

    private static void insertCheckSnapshot(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "INSERT INTO game_event VALUES(91,4,'CHECK_EXECUTED',7)");
        MySqlIntegrationTestSupport.execute(connection,
                "INSERT INTO check_execution VALUES(92,91,7,'roll.advantage',2,19,17,'SUCCESS')");
        MySqlIntegrationTestSupport.execute(connection,
                "INSERT INTO dice_roll VALUES(92,1,12,0),(92,2,17,1)");
    }

    private static void createArchiveTables(Connection connection) throws SQLException {
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE module_release (id BIGINT PRIMARY KEY, module_key VARCHAR(128), "
                        + "release_version VARCHAR(64), canonical_format_version INT, hash_algorithm VARCHAR(32), "
                        + "content_sha256 CHAR(64), release_status VARCHAR(32), released_at TIMESTAMP NULL)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE campaign (id BIGINT PRIMARY KEY AUTO_INCREMENT, campaign_key CHAR(36), "
                        + "campaign_name VARCHAR(255), campaign_status VARCHAR(32), host_state_epoch BIGINT, "
                        + "row_version BIGINT, internal_event_tail BIGINT)");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE campaign_module (campaign_id BIGINT, module_release_id BIGINT, "
                        + "frozen_module_key VARCHAR(128), frozen_release_version VARCHAR(64), frozen_content_sha256 CHAR(64))");
        MySqlIntegrationTestSupport.execute(connection,
                "CREATE TEMPORARY TABLE character_record (id BIGINT PRIMARY KEY AUTO_INCREMENT, campaign_id BIGINT, character_key CHAR(36))");
    }

    private static void insertRelease(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO module_release VALUES(9,'dnd5e2014_srd51_se_v1','1',1,'SHA-256',?,'RELEASED',CURRENT_TIMESTAMP)")) {
            statement.setString(1, MODULE_HASH);
            statement.executeUpdate();
        }
    }

    private static CampaignArchiveDocument emptyArchive() {
        return new CampaignArchiveDocument(
                1,
                new CampaignArchiveDocument.Campaign(CAMPAIGN_KEY, "Integration", "ARCHIVED"),
                new CampaignArchiveDocument.ModuleReference(
                        "dnd5e2014_srd51_se_v1", "1", MODULE_HASH),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static int scalarInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
