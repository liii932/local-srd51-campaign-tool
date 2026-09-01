package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class V016StartingProficiencyBaselineSchemaTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V016__starting_proficiency_baseline_draft.sql");
    private static final Path VERIFY = Path.of(
            "database/verify/v016-starting-proficiency-baseline-draft.sql");

    @Test
    void addsExactlyOneFrozenStartingProfilePerClassWithoutRuntimeWritesOrRelease() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertEquals(12, count(sql, "('class."));
        assertTrue(sql.contains("'class.starting_proficiency_profile'"));
        assertTrue(sql.contains("'armor.heavy,armor.light,armor.medium,armor.shield,"));
        assertFalse(sql.contains("tool.thieves_tools"),
                "fixed tools are reconstructed from level-one selections");
        assertTrue(sql.contains("`release_status` = 'DRAFT'"));
        assertFalse(sql.contains("UPDATE `module_release`"));
        assertFalse(sql.contains("CREATE TABLE `character_"));
        assertFalse(sql.contains("IF NOT EXISTS"));
        assertEquals(SchemaMigrations.V016_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("'" + SchemaMigrations.V016_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V016_APPROVED_SHA256 + "'"));
    }

    @Test
    void verificationIsReadOnlyAndPinsTheDraftProfileCount() throws Exception {
        String sql = Files.readString(VERIFY, StandardCharsets.UTF_8);
        assertTrue(sql.contains("'" + SchemaMigrations.V016_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V016_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains("COUNT(*) = 12"));
        assertTrue(sql.contains("`release_status` = 'DRAFT'"));
        assertFalse(sql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|GRANT)\\b.*"));
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(needle, index)) >= 0;
                index += needle.length()) count++;
        return count;
    }
}
