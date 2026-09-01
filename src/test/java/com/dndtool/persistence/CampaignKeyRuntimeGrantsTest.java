package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps the stable campaign identity outside the runtime account's update boundary. */
final class CampaignKeyRuntimeGrantsTest {
    private static final Path GRANT_FILE =
            Path.of("database/grants/campaign-key-runtime.sql");

    @Test
    void originalSchemaKeepsTheKeyCaseSensitiveCanonicalAndGloballyUnique()
            throws Exception {
        String schema = Files.readString(
                Path.of("src/main/resources/db/migration/V001__stage1_schema.sql"),
                StandardCharsets.UTF_8);

        assertTrue(schema.contains(
                "`campaign_key` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL"));
        assertTrue(schema.contains("UNIQUE KEY `uq_campaign_key` (`campaign_key`)"));
        assertTrue(schema.contains("CONSTRAINT `chk_campaign_key_uuid`"));
        assertTrue(schema.contains(
                "'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'"));
    }

    @Test
    void replacesTableUpdateWithTheOnlyColumnCurrentlyMutatedByProductionCode()
            throws Exception {
        String sql = statements();

        assertTrue(sql.contains(
                "REVOKE UPDATE ON `dnd_tool_se`.`campaign`"));
        assertTrue(sql.contains(
                "GRANT UPDATE (`internal_event_tail`) ON `dnd_tool_se`.`campaign`"));
        assertFalse(Pattern.compile(
                "GRANT\\s+UPDATE\\s*\\([^)]*(?:campaign_key|id|created_at|updated_at)[^)]*\\)",
                Pattern.CASE_INSENSITIVE).matcher(sql).find());
        assertFalse(Pattern.compile(
                "GRANT\\s+(?:ALL|UPDATE)\\s+ON\\s+`dnd_tool_se`\\.`campaign`",
                Pattern.CASE_INSENSITIVE).matcher(sql).find());
    }

    @Test
    void grantChangeAddsNoSchemaWideDdlDeleteOrGrantOption() throws Exception {
        String sql = statements();

        assertFalse(sql.contains("`dnd_tool_se`.*"));
        assertFalse(Pattern.compile(
                "(?im)^\\s*(?:CREATE|ALTER|DROP|TRUNCATE|INSERT|UPDATE|DELETE)\\s+")
                .matcher(sql).find());
        assertFalse(Pattern.compile("(?i)WITH\\s+GRANT\\s+OPTION").matcher(sql).find());
    }

    private static String statements() throws Exception {
        return Files.readString(GRANT_FILE, StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*--.*$", "");
    }
}
