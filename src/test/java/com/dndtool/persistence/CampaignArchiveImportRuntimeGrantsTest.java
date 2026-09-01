package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps whole-campaign replacement privileges explicit and table-specific. */
final class CampaignArchiveImportRuntimeGrantsTest {
    private static final Path GRANT_FILE =
            Path.of("database/grants/archive-import-runtime.sql");
    private static final Set<String> ADDITIONAL_DELETE_TABLES = Set.of(
            "host_operation",
            "character_feature_adjudication_v2",
            "character_resource_recovery_v2",
            "character_feature_choice_v2",
            "character_feat_state_v2",
            "character_multiclass_proficiency_v2",
            "character_feature_state_v2",
            "character_subclass_state_v2",
            "character_ability_score_change_v2",
            "character_advancement_choice_v2",
            "character_level_resource_change_v2",
            "character_level_advancement_v2",
            "character_class_level_v2",
            "character_resource_state_v2",
            "character_creation_selection_v2",
            "character_creation_snapshot_v2",
            "check_effect_parameter_value",
            "check_effect",
            "dice_roll",
            "field_change",
            "check_execution",
            "battle_state",
            "party_world_position",
            "map_instance",
            "item_instance",
            "character_field_value",
            "character_skill_proficiency",
            "character_save_proficiency",
            "game_event",
            "character_record",
            "campaign_module");

    @Test
    void grantsExactlyTheAdditionalDeletesRequiredByReplacementImport()
            throws Exception {
        String sql = statements();
        Matcher matcher = Pattern.compile(
                "(?i)GRANT\\s+DELETE\\s+ON\\s+`dnd_tool_se`\\.`([a-z0-9_]+)`")
                .matcher(sql);
        java.util.HashSet<String> actual = new java.util.HashSet<>();
        while (matcher.find()) {
            actual.add(matcher.group(1));
        }

        assertEquals(ADDITIONAL_DELETE_TABLES, actual);
        assertFalse(sql.contains("`dnd_tool_se`.`campaign` TO"));
        assertFalse(Pattern.compile(
                "(?i)GRANT[^;]*DELETE[^;]*`(?:module_release|module_[a-z_]+)`")
                .matcher(sql).find());
    }

    @Test
    void campaignUpdateCannotChangeStableIdentityOrServerOwnedTimestamps()
            throws Exception {
        String sql = statements();

        assertTrue(sql.contains(
                "GRANT UPDATE (`campaign_name`, `campaign_status`, "
                        + "`host_state_epoch`, `row_version`)"));
        assertFalse(Pattern.compile(
                "(?i)GRANT\\s+UPDATE\\s*\\([^)]*(?:campaign_key|id|created_at|updated_at)[^)]*\\)")
                .matcher(sql).find());
        assertFalse(Pattern.compile(
                "(?i)GRANT\\s+(?:ALL|UPDATE)\\s+ON\\s+`dnd_tool_se`\\.`campaign`")
                .matcher(sql).find());
    }

    @Test
    void grantFileAddsNoSchemaWideDdlDmlOrGrantOption() throws Exception {
        String sql = statements();

        assertFalse(sql.contains("`dnd_tool_se`.*"));
        assertFalse(Pattern.compile(
                "(?im)^\\s*(?:CREATE|ALTER|DROP|TRUNCATE|INSERT|UPDATE|DELETE|REVOKE)\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?i)WITH\\s+GRANT\\s+OPTION").matcher(sql).find());
    }

    private static String statements() throws Exception {
        return Files.readString(GRANT_FILE, StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*--.*$", "");
    }
}
