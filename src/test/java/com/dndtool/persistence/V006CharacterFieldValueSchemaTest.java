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

/** Guards the empty V006 physically typed character field value schema. */
final class V006CharacterFieldValueSchemaTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V006__stage2_character_field_value_schema.sql";

    @Test
    void createsExactlyOneEmptyTypedValueTable() throws Exception {
        String sql = loadMigration();

        assertEquals(1, count(sql, "CREATE TABLE `"));
        assertTrue(sql.contains("CREATE TABLE `character_field_value`"));
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS"));
        assertFalse(Pattern.compile("`[^`]+`\\s+JSON\\b", Pattern.CASE_INSENSITIVE)
                .matcher(sql).find());
        assertFalse(Pattern.compile(
                "INSERT\\s+INTO\\s+`character_field_value`", Pattern.CASE_INSENSITIVE)
                .matcher(sql).find());
        assertFalse(sql.contains("player"));
        assertFalse(sql.contains("public_"));
        assertFalse(sql.contains("approval"));
    }

    @Test
    void permitsOnlyOnePhysicalColumnMatchingTheDeclaredType() throws Exception {
        String sql = loadMigration();

        assertTrue(sql.contains("ENUM('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN')"));
        assertTrue(sql.contains("`text_value` VARCHAR(2000) NULL"));
        assertTrue(sql.contains("`integer_value` BIGINT NULL"));
        assertTrue(sql.contains("`decimal_value` DECIMAL(38, 18) NULL"));
        assertTrue(sql.contains("`boolean_value` TINYINT UNSIGNED NULL"));
        assertTrue(sql.contains("CONSTRAINT `chk_character_field_typed_value`"));
        assertTrue(sql.contains("`value_type` = 'TEXT'"));
        assertTrue(sql.contains("`value_type` = 'INTEGER'"));
        assertTrue(sql.contains("`value_type` = 'DECIMAL'"));
        assertTrue(sql.contains("`value_type` = 'BOOLEAN'"));
        assertTrue(sql.contains("CHECK (`boolean_value` IS NULL OR `boolean_value` IN (0, 1))"));
    }

    @Test
    void constrainsCharacterReleaseFieldIdentityAndDefinitionType() throws Exception {
        String sql = loadMigration();

        assertTrue(sql.contains("PRIMARY KEY (`character_id`, `field_key`)"));
        assertTrue(sql.contains("ADD UNIQUE KEY `uq_mfd_release_key_type`"));
        assertTrue(sql.contains("(`module_release_id`, `field_key`, `data_type`)"));
        assertTrue(sql.contains("FOREIGN KEY (`character_id`, `module_release_id`)"));
        assertTrue(sql.contains("REFERENCES `character_record` (`id`, `module_release_id`)"));
        assertTrue(sql.contains("FOREIGN KEY (`module_release_id`, `field_key`, `value_type`)"));
        assertTrue(sql.contains("REFERENCES `module_field_definition`"));
        assertEquals(2, count(sql, "FOREIGN KEY ("));
        assertEquals(2, count(sql, "CONSTRAINT `chk_"));
    }

    @Test
    void recordsV006OnlyAfterSeparateV005PrerequisiteRead() throws Exception {
        String sql = loadMigration();

        assertEquals(
                SchemaMigrations.V006_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("SELECT COUNT(*) INTO @v005_schema_record_count"));
        assertTrue(sql.contains("@v005_schema_record_count = 1"));
        assertTrue(sql.contains("'" + SchemaMigrations.V006_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V006_APPROVED_SHA256 + "'"));
        int insertStart = sql.indexOf("INSERT INTO `schema_meta`");
        int insertEnd = sql.indexOf("\n);", insertStart);
        String insertStatement = sql.substring(insertStart, insertEnd);
        assertFalse(insertStatement.contains("FROM `schema_meta`"));
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

    private static String loadMigration() throws IOException {
        try (InputStream input =
                V006CharacterFieldValueSchemaTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
