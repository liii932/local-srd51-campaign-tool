package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the empty V009 d20 execution and typed effect-plan schema. */
final class V009CheckExecutionSchemaTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V009__stage3_check_execution_schema.sql";

    @Test
    void createsOnlyFourEmptyCheckRuntimeTablesWithoutBusinessOrGrantWrites()
            throws Exception {
        String sql = loadMigration();
        String payload = checksumPayload(sql);

        assertEquals(4, count(payload, "CREATE TABLE `"));
        for (String table : new String[] {
            "check_execution", "dice_roll", "check_effect",
            "check_effect_parameter_value"
        }) {
            assertTrue(payload.contains("CREATE TABLE `" + table + "`"), table);
        }
        assertFalse(Pattern.compile(
                "CREATE\\s+TABLE\\s+`(?:map_instance|party_world_position|"
                        + "battle_state|battle_participant|entity_position)`",
                Pattern.CASE_INSENSITIVE).matcher(payload).find());
        assertFalse(Pattern.compile(
                "(?im)^\\s*(?:INSERT|UPDATE|DELETE|GRANT|REVOKE)\\s+")
                .matcher(payload).find());
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS"));
        assertFalse(sql.contains("INSERT IGNORE"));
        assertFalse(sql.contains("ON DUPLICATE KEY"));
    }

    @Test
    void addsAReplayRootWithoutBackfillingExistingOperations() throws Exception {
        String sql = loadMigration();
        String payload = checksumPayload(sql);

        assertTrue(payload.contains(
                "ALTER TABLE `host_operation`\n"
                        + "    ADD COLUMN `game_event_id` BIGINT UNSIGNED NULL"));
        assertTrue(payload.contains("UNIQUE KEY `uq_host_operation_game_event`"));
        assertTrue(payload.contains("FOREIGN KEY (`game_event_id`, `campaign_id`)"));
        assertTrue(payload.contains("REFERENCES `game_event` (`id`, `campaign_id`)"));
        assertTrue(payload.contains("`result_status` = 'SUCCEEDED'"));
        assertFalse(Pattern.compile("(?im)^\\s*UPDATE\\s+`host_operation`")
                .matcher(sql).find());
    }

    @Test
    void executionIsBoundToOneEventExecutorReleaseAndClosedCheckSource()
            throws Exception {
        String table = createTableBlock(loadMigration(), "check_execution");

        assertTrue(table.contains("`event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL"));
        assertTrue(table.contains("`manual_name` VARCHAR(80) NULL"));
        assertTrue(table.contains("`modifier_value` SMALLINT NOT NULL"));
        assertTrue(table.contains("`difficulty_class` TINYINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("`check_result` ENUM('SUCCESS', 'FAILURE') NOT NULL"));
        assertTrue(table.contains("FOREIGN KEY (`game_event_id`, `campaign_id`)"));
        assertTrue(table.contains("FOREIGN KEY (`executor_character_id`, `campaign_id`)"));
        assertTrue(table.contains("FOREIGN KEY (`executor_character_id`, `module_release_id`)"));
        assertTrue(table.contains("REFERENCES `module_check_definition`"));
        assertTrue(table.contains("REFERENCES `module_roll_mode`"));
        assertTrue(table.contains("REFERENCES `module_event_template`"));
        for (String checkKey : new String[] {
            "check.ability", "check.skill", "check.saving_throw", "check.manual"
        }) {
            assertTrue(table.contains("`check_key` = '" + checkKey + "'"), checkKey);
        }
        assertTrue(table.contains("CHECK (`modifier_value` BETWEEN -99 AND 99)"));
        assertTrue(table.contains("CHECK (`difficulty_class` BETWEEN 0 AND 60)"));
        assertFalse(table.toLowerCase().contains("algorithm"));
        assertFalse(table.toLowerCase().contains("client_roll"));
    }

    @Test
    void diceCandidatesAreServerBoundedAndAtMostOneCanBeSelected()
            throws Exception {
        String table = createTableBlock(loadMigration(), "dice_roll");

        assertTrue(table.contains("`candidate_order` TINYINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("`rolled_value` TINYINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("`is_selected` TINYINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("GENERATED ALWAYS AS ( CASE WHEN `is_selected` = 1 THEN 1 ELSE NULL END) STORED"));
        assertTrue(table.contains("UNIQUE KEY `uq_dice_roll_execution_order`"));
        assertTrue(table.contains("UNIQUE KEY `uq_dice_roll_single_selected`"));
        assertTrue(table.contains("CHECK (`rolled_value` BETWEEN 1 AND 20)"));
        assertTrue(table.contains("CHECK (`is_selected` IN (0, 1))"));
    }

    @Test
    void effectBranchesHaveOrderedPlansWithoutAnEditableAppliedFlag()
            throws Exception {
        String table = createTableBlock(loadMigration(), "check_effect");

        assertTrue(table.contains("`effect_branch` ENUM('SUCCESS', 'FAILURE') NOT NULL"));
        assertTrue(table.contains("`effect_order` SMALLINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("UNIQUE KEY `uq_check_effect_execution_branch_order`"));
        assertTrue(table.contains("UNIQUE KEY `uq_check_effect_singleton_per_branch`"));
        assertTrue(table.contains("REFERENCES `check_execution` (`id`, `module_release_id`)"));
        assertTrue(table.contains("REFERENCES `module_effect_definition` (`module_release_id`, `effect_key`)"));
        assertFalse(Pattern.compile("(?i)(?:is_)?applied|execution_status|JSON")
                .matcher(table).find());
    }

    @Test
    void parameterSnapshotsMatchTheModuleKeyOrderAndPhysicalType() throws Exception {
        String sql = loadMigration();
        String table = createTableBlock(sql, "check_effect_parameter_value");

        assertTrue(sql.contains("UNIQUE KEY `uq_mefp_runtime_parameter_shape`"));
        assertTrue(table.contains(
                "`value_type` ENUM('REFERENCE', 'INTEGER', 'DECIMAL', 'TEXT', 'BOOLEAN') NOT NULL"));
        for (String valueColumn : new String[] {
            "reference_value", "integer_value", "decimal_value", "text_value",
            "boolean_value"
        }) {
            assertTrue(table.contains("`" + valueColumn + "`"), valueColumn);
        }
        assertTrue(table.contains(
                "FOREIGN KEY (`check_effect_id`, `module_release_id`, `effect_key`)"));
        assertTrue(table.contains(
                "`module_release_id`, `effect_key`, `parameter_key`, `parameter_order`, `value_type`) REFERENCES `module_effect_parameter`"));
        assertTrue(table.contains("CONSTRAINT `chk_cepv_typed_value`"));
        assertFalse(Pattern.compile("(?i)JSON|script_text|database_id")
                .matcher(table).find());
    }

    @Test
    void recordsV009OnlyAfterSeparateV008PrerequisiteRead() throws Exception {
        String sql = loadMigration();

        assertEquals(
                SchemaMigrations.V009_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("SELECT COUNT(*) INTO @v008_schema_record_count"));
        assertTrue(sql.contains("@v008_schema_record_count = 1"));
        assertTrue(sql.contains("'" + SchemaMigrations.V009_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V009_APPROVED_SHA256 + "'"));
        int insertStart = sql.indexOf("INSERT INTO `schema_meta`");
        int insertEnd = sql.indexOf("\n);", insertStart);
        String insertStatement = sql.substring(insertStart, insertEnd);
        assertFalse(insertStatement.contains("FROM `schema_meta`"));
    }

    @Test
    void readOnlyVerificationScriptUsesTheSameApprovedIdentity() throws Exception {
        String verify = Files.readString(
                Path.of("database/verify/v009-check-execution-schema.sql"),
                StandardCharsets.UTF_8);

        assertTrue(verify.contains("'" + SchemaMigrations.V009_SCRIPT_NAME + "'"));
        assertTrue(verify.contains("'" + SchemaMigrations.V009_APPROVED_SHA256 + "'"));
        assertTrue(verify.contains("THEN 'PASS' ELSE 'FAIL'"));
        assertFalse(Pattern.compile("(?im)^\\s*(?:INSERT|UPDATE|DELETE|ALTER|CREATE|"
                + "DROP|TRUNCATE|GRANT|REVOKE)\\s+").matcher(verify).find());
    }

    private static String createTableBlock(String sql, String tableName) {
        int start = sql.indexOf("CREATE TABLE `" + tableName + "`");
        int end = sql.indexOf(';', start);
        assertTrue(start >= 0 && end > start);
        return sql.substring(start, end).replaceAll("\\s+", " ");
    }

    private static String checksumPayload(String sql) {
        int start = sql.indexOf("-- CHECKSUM-SCOPE-BEGIN");
        int end = sql.indexOf("-- CHECKSUM-SCOPE-END");
        assertTrue(start >= 0 && end > start);
        return sql.substring(start, end);
    }

    private static int count(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String loadMigration() throws IOException {
        try (InputStream input = V009CheckExecutionSchemaTest.class
                .getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
