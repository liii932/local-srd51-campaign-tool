package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps the one relationship-row deletion privilege narrower than aggregate deletion. */
final class CharacterRuntimeGrantsTest {
    @Test
    void deleteIsLimitedToRemovingAClassLevelRelationship() throws Exception {
        String sql = Files.readString(
                Path.of("database/grants/character-runtime.sql"), StandardCharsets.UTF_8);

        assertTrue(sql.contains(
                "GRANT SELECT, INSERT, UPDATE, DELETE ON `dnd_tool_se`.`character_class_level`"));
        assertFalse(Pattern.compile(
                "GRANT[^;]*DELETE[^;]*`(?:character_record|character_field_value|"
                        + "character_skill_proficiency|character_save_proficiency|"
                        + "game_event|field_change|item_instance)`",
                Pattern.CASE_INSENSITIVE).matcher(sql).find());
        assertFalse(Pattern.compile("(?im)^\\s*(?:DROP|ALTER|CREATE|TRUNCATE)\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?im)^\\s*GRANT[^;]*WITH\\s+GRANT\\s+OPTION")
                .matcher(sql).find());
    }

    @Test
    void existingInstallGrantOnlyAddsClassRelationshipDelete() throws Exception {
        String sql = Files.readString(
                Path.of("database/grants/character-card-runtime.sql"),
                StandardCharsets.UTF_8);
        String statements = sql.replaceAll("(?m)^\\s*--.*$", "");

        assertTrue(statements.contains(
                "GRANT DELETE ON `dnd_tool_se`.`character_class_level`"));
        assertFalse(Pattern.compile(
                "GRANT[^;]*(?:INSERT|UPDATE|CREATE|ALTER|DROP|TRUNCATE|GRANT\\s+OPTION)",
                Pattern.CASE_INSENSITIVE).matcher(statements).find());
        assertFalse(Pattern.compile(
                "GRANT[^;]*DELETE[^;]*`(?:character_record|character_field_value|"
                        + "character_skill_proficiency|character_save_proficiency|"
                        + "game_event|field_change|item_instance)`",
                Pattern.CASE_INSENSITIVE).matcher(statements).find());
    }
}
