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

/** Guards the empty V010 NODE-map and minimal encounter runtime schema. */
final class V010NodeEncounterSchemaTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V010__stage3_node_encounter_schema.sql";

    @Test
    void createsExactlyFiveEmptyRuntimeTablesWithoutBusinessOrGrantWrites()
            throws Exception {
        String sql = loadMigration();
        String payload = checksumPayload(sql);

        assertEquals(5, count(payload, "CREATE TABLE `"));
        for (String table : new String[] {
            "map_instance", "party_world_position", "battle_state",
            "battle_participant", "entity_position"
        }) {
            assertTrue(payload.contains("CREATE TABLE `" + table + "`"), table);
        }
        assertFalse(Pattern.compile(
                "(?im)^\\s*(?:INSERT|UPDATE|DELETE|GRANT|REVOKE)\\s+")
                .matcher(payload).find());
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS"));
        assertFalse(sql.contains("INSERT IGNORE"));
        assertFalse(sql.contains("ON DUPLICATE KEY"));
    }

    @Test
    void mapInstancesAreFrozenNodeMapsUniqueWithinCampaign() throws Exception {
        String sql = loadMigration();
        String table = createTableBlock(sql, "map_instance");

        assertTrue(sql.contains("UNIQUE KEY `uq_campaign_module_release`"));
        assertTrue(sql.contains("UNIQUE KEY `uq_mmap_runtime_node_shape`"));
        assertTrue(table.contains("`map_type` ENUM('NODE') NOT NULL"));
        assertTrue(table.contains(
                "UNIQUE KEY `uq_map_instance_campaign_key` (`campaign_id`, `map_key`)"));
        assertTrue(table.contains(
                "FOREIGN KEY (`campaign_id`, `module_release_id`) REFERENCES `campaign_module`"));
        assertTrue(table.contains(
                "FOREIGN KEY (`module_release_id`, `map_key`, `map_type`) REFERENCES `module_map_definition`"));
    }

    @Test
    void partyPositionUsesOneCampaignRowAndOneNodeInTheSameMap() throws Exception {
        String table = createTableBlock(loadMigration(), "party_world_position");

        assertTrue(table.contains("PRIMARY KEY (`campaign_id`)"));
        assertTrue(table.contains(
                "FOREIGN KEY (`map_instance_id`, `campaign_id`, `module_release_id`, `map_key`) REFERENCES `map_instance`"));
        assertTrue(table.contains(
                "FOREIGN KEY (`module_release_id`, `map_key`, `node_key`) REFERENCES `module_map_node`"));
        assertFalse(table.contains("character_id"));
        assertFalse(table.contains("battle_id"));
    }

    @Test
    void activeEncounterUniquenessIsDatabaseEnforcedWithoutTurnState() throws Exception {
        String table = createTableBlock(loadMigration(), "battle_state");

        assertTrue(table.contains("`battle_status` ENUM('ACTIVE', 'CLOSED') NOT NULL"));
        assertTrue(table.contains(
                "CASE WHEN `battle_status` = 'ACTIVE' THEN `campaign_id` ELSE NULL END"));
        assertTrue(table.contains(
                "UNIQUE KEY `uq_battle_state_active_campaign` (`active_campaign_id`)"));
        assertTrue(table.contains("CONSTRAINT `chk_battle_state_completion`"));
        assertTrue(table.contains(
                "FOREIGN KEY (`map_instance_id`, `campaign_id`, `module_release_id`, `map_key`) REFERENCES `map_instance`"));
        assertFalse(Pattern.compile(
                "(?i)`(?:initiative|round|turn|action|reaction|speed|distance|attack|damage)`")
                .matcher(table).find());
    }

    @Test
    void participantFactionAndEncounterCharacterPairAreClosed() throws Exception {
        String table = createTableBlock(loadMigration(), "battle_participant");

        assertTrue(table.contains("`faction` ENUM('ALLY', 'ENEMY', 'NEUTRAL') NOT NULL"));
        assertTrue(table.contains(
                "UNIQUE KEY `uq_battle_participant_character` (`battle_id`, `character_id`)"));
        assertTrue(table.contains(
                "FOREIGN KEY (`battle_id`, `campaign_id`) REFERENCES `battle_state`"));
        assertTrue(table.contains(
                "FOREIGN KEY (`character_id`, `campaign_id`) REFERENCES `character_record`"));
    }

    @Test
    void entityPositionRequiresExistingParticipantActiveBattleAndItsNode()
            throws Exception {
        String table = createTableBlock(loadMigration(), "entity_position");

        assertTrue(table.contains("PRIMARY KEY (`battle_id`, `character_id`)"));
        assertTrue(table.contains(
                "FOREIGN KEY (`battle_id`, `campaign_id`, `character_id`) REFERENCES `battle_participant`"));
        assertTrue(table.contains("CONSTRAINT `fk_entity_position_active_battle`"));
        assertTrue(table.contains(
                ") REFERENCES `battle_state` ( `id`, `campaign_id`, `active_campaign_id`, `map_instance_id`, `module_release_id`, `map_key`)"));
        assertTrue(table.contains(
                "FOREIGN KEY (`module_release_id`, `map_key`, `node_key`) REFERENCES `module_map_node`"));
        assertTrue(table.contains("CHECK (`active_campaign_id` = `campaign_id`)"));
        assertFalse(Pattern.compile("(?i)implicit|auto_join|grid_[xy]")
                .matcher(table).find());
    }

    @Test
    void recordsV010OnlyAfterSeparateV009PrerequisiteRead() throws Exception {
        String sql = loadMigration();

        assertEquals(
                SchemaMigrations.V010_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("SELECT COUNT(*) INTO @v009_schema_record_count"));
        assertTrue(sql.contains("@v009_schema_record_count = 1"));
        assertTrue(sql.contains("'" + SchemaMigrations.V010_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V010_APPROVED_SHA256 + "'"));
        int insertStart = sql.indexOf("INSERT INTO `schema_meta`");
        int insertEnd = sql.indexOf("\n);", insertStart);
        String insertStatement = sql.substring(insertStart, insertEnd);
        assertFalse(insertStatement.contains("FROM `schema_meta`"));
    }

    @Test
    void readOnlyVerificationScriptUsesTheSameApprovedIdentity() throws Exception {
        String verify = Files.readString(
                Path.of("database/verify/v010-node-encounter-schema.sql"),
                StandardCharsets.UTF_8);

        assertTrue(verify.contains("'" + SchemaMigrations.V010_SCRIPT_NAME + "'"));
        assertTrue(verify.contains("'" + SchemaMigrations.V010_APPROVED_SHA256 + "'"));
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
        try (InputStream input = V010NodeEncounterSchemaTest.class
                .getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
