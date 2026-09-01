package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V012LevelOneCharacterCreationSchemaTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V012__level_one_character_creation.sql");

    @Test
    void addsOnlyForwardDraftRulesAndEmptyAuthoritativeTables() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE `character_creation_snapshot_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_creation_selection_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_resource_state_v2`"));
        assertTrue(sql.contains("'dnd5e2014_srd51_se'"));
        assertTrue(sql.contains("'DRAFT'"));
        assertTrue(sql.contains("'ability.standard_array_v1'"));
        assertTrue(sql.contains("'LEVEL_ONE_CHARACTER_CREATED'"));
        assertTrue(sql.replace("\r\n", "\n").contains("VALUES (\n    12,"));
        assertFalse(Pattern.compile("(?im)^\\s*(UPDATE|DELETE)\\s+")
                .matcher(checksumPayload(sql)).find());
        assertFalse(sql.contains("IF NOT EXISTS"));
        assertFalse(sql.contains("CREATE USER"));
        assertFalse(sql.contains("GRANT "));
    }

    @Test
    void bindsEveryAuthoritativeRowToCharacterAndFrozenRelease() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("fk_character_creation_snapshot_character"));
        assertTrue(sql.contains("fk_character_creation_selection_character"));
        assertTrue(sql.contains("fk_character_resource_state_character"));
        assertTrue(sql.contains("fk_character_creation_snapshot_race"));
        assertTrue(sql.contains("fk_character_creation_snapshot_class"));
        assertTrue(sql.contains("uq_character_creation_snapshot_preview"));
    }

    private static String checksumPayload(String sql) {
        int begin = sql.indexOf("-- CHECKSUM-SCOPE-BEGIN");
        int end = sql.indexOf("-- CHECKSUM-SCOPE-END");
        return sql.substring(begin, end);
    }
}
