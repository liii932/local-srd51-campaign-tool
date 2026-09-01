package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.module.AdvancementValueProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V013LevelAdvancementSchemaTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V013__level_advancement_hit_dice.sql");

    @Test
    void addsOnlyForwardDraftCatalogAndEmptyAuthoritativeTables() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE `character_class_level_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_level_advancement_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_level_resource_change_v2`"));
        assertTrue(sql.contains("ADD COLUMN `is_unlimited`"));
        assertTrue(sql.contains("'class.proficiency_bonus_profile'"));
        assertTrue(sql.contains("'resource.maximum_profile'"));
        assertTrue(sql.contains("'resource.hit_dice.d6'"));
        assertTrue(sql.contains("'resource.hit_dice.d8'"));
        assertTrue(sql.contains("'resource.hit_dice.d10'"));
        assertTrue(sql.contains("'resource.hit_dice.d12'"));
        assertTrue(sql.contains("'CHARACTER_LEVEL_ADVANCED'"));
        assertTrue(sql.contains("'DRAFT'"));
        assertTrue(sql.replace("\r\n", "\n").contains("VALUES (\n    13,"));
        assertFalse(sql.contains("IF NOT EXISTS"));
        assertFalse(sql.contains("CREATE USER"));
        assertFalse(sql.contains("GRANT "));
    }

    @Test
    void seedsOneMaximumProfileForEveryExistingClassResource() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\\('(resource\\.[a-z_]+(?:\\.[a-z_]+)?)', '([^']+)'\\)")
                .matcher(seedBlock(sql));
        int count = 0;
        while (matcher.find()) {
            AdvancementValueProfile.parse(matcher.group(2));
            count++;
        }

        assertEquals(16, count);
    }

    @Test
    void bindsEveryNewAuthoritativeRowToCharacterReleaseAndRootEvent() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("fk_character_class_level_v2_character"));
        assertTrue(sql.contains("fk_character_class_level_v2_definition"));
        assertTrue(sql.contains("fk_level_advancement_event"));
        assertTrue(sql.contains("fk_level_advancement_character"));
        assertTrue(sql.contains("fk_level_resource_change_advancement"));
        assertTrue(sql.contains("fk_level_resource_change_definition"));
        assertTrue(sql.contains("uq_level_advancement_character_level"));
    }

    private static String seedBlock(String sql) {
        int begin = sql.indexOf("CREATE TEMPORARY TABLE `v013_resource_profile_seed`");
        int end = sql.indexOf("DROP TEMPORARY TABLE `v013_resource_profile_seed`");
        return sql.substring(begin, end);
    }
}
