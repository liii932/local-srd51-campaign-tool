package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LevelOneCharacterRuntimeGrantsTest {
    @Test
    void grantsOnlyRequiredCatalogReadsAndRuntimeWrites() throws Exception {
        String sql = Files.readString(Path.of(
                "database/grants/level-one-character-runtime.sql"), StandardCharsets.UTF_8);
        assertTrue(sql.contains("GRANT SELECT ON `dnd_tool_se`.`module_catalog_definition_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_creation_snapshot_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_resource_state_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_class_level_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_level_advancement_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_level_resource_change_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_subclass_state_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feature_state_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feature_choice_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feature_adjudication_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_resource_recovery_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_advancement_choice_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_ability_score_change_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feat_state_v2`"));
        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`character_multiclass_proficiency_v2`"));
        assertFalse(Pattern.compile("(?im)^\\s*(?:CREATE|ALTER|DROP|TRUNCATE|DELETE)\\s+")
                .matcher(sql).find());
        assertFalse(sql.contains("GRANT OPTION"));
        assertFalse(sql.contains("'%'"));
    }
}
