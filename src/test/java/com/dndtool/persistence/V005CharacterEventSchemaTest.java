package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the empty V005 shared character and internal event schema. */
final class V005CharacterEventSchemaTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V005__stage2_character_event_schema.sql";
    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE `([^`]+)`");
    private static final Pattern COLUMN_DEFINITION =
            Pattern.compile("(?m)^    `([^`]+)`\\s+");
    private static final List<String> EXPECTED_TABLES = List.of(
            "character_record",
            "character_class_level",
            "character_skill_proficiency",
            "character_save_proficiency",
            "game_event",
            "field_change");

    @Test
    void createsOnlyTheSharedCharacterRelationsAndInternalAuditTables() throws Exception {
        String sql = loadMigration();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) tables.add(matcher.group(1));

        assertEquals(EXPECTED_TABLES, tables);
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS"));
        assertFalse(sql.matches("(?s).*CREATE TABLE `character_field_value`.*"));
        assertFalse(sql.matches("(?s).*CREATE TABLE `item_instance`.*"));
        assertFalse(sql.matches("(?s).*CREATE TABLE `public_.*"));
        assertFalse(Pattern.compile("`[^`]+`\\s+JSON\\b", Pattern.CASE_INSENSITIVE)
                .matcher(sql).find());
        assertFalse(sql.contains("player"));
        assertFalse(sql.contains("approval"));
    }

    @Test
    void freezesCharacterHashAndConstrainsEveryRelationToOneRelease() throws Exception {
        String sql = loadMigration();

        assertTrue(sql.contains("`saved_module_key`"));
        assertTrue(sql.contains("`saved_release_version`"));
        assertTrue(sql.contains("`saved_content_sha256`"));
        assertTrue(sql.contains("CONSTRAINT `fk_character_record_frozen_binding`"));
        assertTrue(sql.contains("ADD UNIQUE KEY `uq_campaign_module_frozen_binding`"));
        assertTrue(sql.contains("REFERENCES `module_class_definition`"));
        assertTrue(sql.contains("REFERENCES `module_skill_definition`"));
        assertTrue(sql.contains("REFERENCES `module_save_definition`"));
        assertEquals(2, count(sql, "REFERENCES `module_proficiency_tier`"));
        assertEquals(13, count(sql, "FOREIGN KEY ("));
        assertTrue(sql.contains("CHECK (`class_level` BETWEEN 1 AND 20)"));
    }

    @Test
    void characterClassLevelsSupportSingleAndMulticlassCharacters() throws Exception {
        String table = createTableBlock(loadMigration(), "character_class_level");

        assertTrue(table.contains("`character_id` BIGINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("`module_release_id` BIGINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("`class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"));
        assertTrue(table.contains("`class_level` TINYINT UNSIGNED NOT NULL"));
        // Different class keys allow multiclass rows; the same class cannot be repeated.
        assertTrue(table.contains("PRIMARY KEY (`character_id`, `class_key`)"));
        assertTrue(table.contains("CHECK (`class_level` BETWEEN 1 AND 20)"));
        assertTrue(table.contains("FOREIGN KEY (`character_id`, `module_release_id`)"));
        assertTrue(table.contains("REFERENCES `character_record` (`id`, `module_release_id`)"));
        assertTrue(table.contains("FOREIGN KEY (`module_release_id`, `class_key`)"));
        assertTrue(table.contains("REFERENCES `module_class_definition` (`module_release_id`, `class_key`)"));
    }

    @Test
    void derivedValueInputsAreStoredWithoutEditableResultColumns() throws Exception {
        String sql = loadMigration();

        assertEquals(List.of(
                "id", "campaign_id", "module_release_id", "character_key",
                "character_type", "character_name", "character_status",
                "saved_module_key", "saved_release_version", "saved_content_sha256",
                "row_version", "created_at", "updated_at"),
                columnNames(sql, "character_record"));
        assertEquals(List.of(
                "character_id", "module_release_id", "class_key", "class_level"),
                columnNames(sql, "character_class_level"));
        assertEquals(List.of(
                "character_id", "module_release_id", "skill_key", "proficiency_key"),
                columnNames(sql, "character_skill_proficiency"));
        assertEquals(List.of(
                "character_id", "module_release_id", "save_key", "proficiency_key"),
                columnNames(sql, "character_save_proficiency"));
    }

    @Test
    void enforcesStableIdentityOrderedEventsAndTypedFieldChanges() throws Exception {
        String sql = loadMigration();

        assertTrue(sql.contains("UNIQUE KEY `uq_character_record_key` (`character_key`)"));
        assertTrue(sql.contains("ENUM('PC', 'NPC')"));
        assertTrue(sql.contains("ENUM('ACTIVE', 'ARCHIVED')"));
        assertTrue(sql.contains("UNIQUE KEY `uq_game_event_campaign_sequence`"));
        assertTrue(sql.contains("CHECK (`event_sequence` > 0)"));
        assertTrue(sql.contains("UNIQUE KEY `uq_field_change_event_order`"));
        assertTrue(sql.contains("`value_type` ENUM('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'REFERENCE')"));
        assertTrue(sql.contains("CONSTRAINT `chk_field_change_typed_values`"));
        assertEquals(11, count(sql, "CONSTRAINT `chk_"));
        assertTrue(sql.contains("REFERENCES `game_event` (`id`, `campaign_id`)"));
        assertTrue(sql.contains("REFERENCES `character_record` (`id`, `campaign_id`)"));
    }

    @Test
    void isEmptyFailClosedAndCarriesApprovedChecksum() throws Exception {
        String sql = loadMigration();

        assertEquals(
                SchemaMigrations.V005_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("'" + SchemaMigrations.V005_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V005_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains("`schema_version` = 4"));
        assertTrue(sql.contains("'V004__release_builtin_module.sql'"));
        assertTrue(sql.contains("SELECT COUNT(*) INTO @v004_schema_record_count"));
        assertTrue(sql.contains("@v004_schema_record_count = 1"));
        assertFalse(Pattern.compile(
                "INSERT\\s+INTO\\s+`schema_meta`[\\s\\S]*?EXISTS\\s*\\([\\s\\S]*?FROM\\s+`schema_meta`",
                Pattern.CASE_INSENSITIVE).matcher(sql).find());
        assertFalse(Pattern.compile(
                "INSERT\\s+INTO\\s+`(?:character_|game_event|field_change)",
                Pattern.CASE_INSENSITIVE).matcher(sql).find());
        assertFalse(sql.contains("INSERT IGNORE"));
        assertFalse(sql.contains("ON DUPLICATE KEY"));
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

    /** Extracts exactly one CREATE TABLE statement for focused schema assertions. */
    private static String createTableBlock(String sql, String table) {
        int start = sql.indexOf("CREATE TABLE `" + table + "`");
        int end = sql.indexOf(';', start);
        assertTrue(start >= 0 && end > start, table);
        return sql.substring(start, end).replaceAll("\\s+", " ");
    }

    /** Extracts physical columns only; keys and constraints cannot masquerade as state. */
    private static List<String> columnNames(String sql, String table) {
        int start = sql.indexOf("CREATE TABLE `" + table + "`");
        int end = sql.indexOf(';', start);
        assertTrue(start >= 0 && end > start, table);
        Matcher matcher = COLUMN_DEFINITION.matcher(sql.substring(start, end));
        List<String> columns = new ArrayList<>();
        while (matcher.find()) {
            columns.add(matcher.group(1));
        }
        return List.copyOf(columns);
    }

    private static String loadMigration() throws IOException {
        try (InputStream input =
                V005CharacterEventSchemaTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
