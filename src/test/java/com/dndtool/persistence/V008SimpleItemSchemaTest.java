package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the empty V008 character-held simple item schema. */
final class V008SimpleItemSchemaTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V008__stage2_simple_item_schema.sql";

    @Test
    void createsOneEmptyItemTableWithoutBusinessOrGrantWrites() throws Exception {
        String sql = loadMigration();

        assertEquals(1, count(sql, "CREATE TABLE `"));
        assertTrue(sql.contains("CREATE TABLE `item_instance`"));
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS"));
        assertFalse(Pattern.compile(
                "INSERT\\s+INTO\\s+`(?:item_instance|character_record|game_event)`",
                Pattern.CASE_INSENSITIVE).matcher(sql).find());
        assertFalse(Pattern.compile(
                "(?im)^\\s*(?:DELETE|UPDATE|GRANT)\\s+")
                .matcher(checksumPayload(sql)).find());
    }

    @Test
    void storesOnlyHolderSourceSimpleTextQuantityAndArchiveState() throws Exception {
        String table = createTableBlock(loadMigration());

        assertTrue(table.contains("`character_id` BIGINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("`source_kind` ENUM('MODULE', 'TEMPORARY') NOT NULL"));
        assertTrue(table.contains("`item_name` VARCHAR(80) NOT NULL"));
        assertTrue(table.contains("`item_description` VARCHAR(500) NOT NULL"));
        assertTrue(table.contains("`quantity` SMALLINT UNSIGNED NOT NULL"));
        assertTrue(table.contains("`item_status` ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE'"));
        assertTrue(table.contains("CHECK (`quantity` BETWEEN 1 AND 999)"));

        for (String forbidden : new String[] {
            "weight", "price", "currency", "slot", "durability", "charges",
            "attack", "damage", "consumable", "player_id", "owner_id", "JSON"
        }) {
            assertFalse(table.toLowerCase().contains(forbidden.toLowerCase()), forbidden);
        }
    }

    @Test
    void moduleSourceIsFrozenToTheHolderReleaseAndTemporarySourceHasNoTemplate()
            throws Exception {
        String table = createTableBlock(loadMigration());

        assertTrue(table.contains("CONSTRAINT `chk_item_instance_source`"));
        assertTrue(table.contains("`source_kind` = 'MODULE'"));
        assertTrue(table.contains("`source_kind` = 'TEMPORARY'"));
        assertTrue(table.contains("FOREIGN KEY (`character_id`)"));
        assertTrue(table.contains("REFERENCES `character_record` (`id`)"));
        assertTrue(table.contains("FOREIGN KEY (`character_id`, `module_release_id`)"));
        assertTrue(table.contains("REFERENCES `character_record` (`id`, `module_release_id`)"));
        assertTrue(table.contains("FOREIGN KEY (`module_release_id`, `item_key`)"));
        assertTrue(table.contains("REFERENCES `module_item_template` (`module_release_id`, `item_key`)"));
        assertEquals(3, count(table, "FOREIGN KEY ("));
        assertEquals(4, count(table, "CONSTRAINT `chk_"));
        assertEquals(3, count(table, "ON UPDATE RESTRICT ON DELETE RESTRICT"));
    }

    @Test
    void recordsV008OnlyAfterSeparateV007PrerequisiteRead() throws Exception {
        String sql = loadMigration();

        assertEquals(
                SchemaMigrations.V008_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("SELECT COUNT(*) INTO @v007_schema_record_count"));
        assertTrue(sql.contains("@v007_schema_record_count = 1"));
        assertTrue(sql.contains("'" + SchemaMigrations.V008_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V008_APPROVED_SHA256 + "'"));
        int insertStart = sql.indexOf("INSERT INTO `schema_meta`");
        int insertEnd = sql.indexOf("\n);", insertStart);
        String insertStatement = sql.substring(insertStart, insertEnd);
        assertFalse(insertStatement.contains("FROM `schema_meta`"));
        assertFalse(sql.contains("INSERT IGNORE"));
        assertFalse(sql.contains("ON DUPLICATE KEY"));
    }

    private static String createTableBlock(String sql) {
        int start = sql.indexOf("CREATE TABLE `item_instance`");
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
        try (InputStream input =
                V008SimpleItemSchemaTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
