package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class V015MulticlassAsiFeatSchemaTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V015__multiclass_asi_feat_draft.sql");
    private static final Path VERIFY = Path.of(
            "database/verify/v015-multiclass-asi-feat-draft.sql");

    @Test
    void addsDraftCatalogProfilesWithoutPublishingTheRelease() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("'class.multiclass_prerequisite'"));
        assertTrue(sql.contains("'class.multiclass_proficiency_profile'"));
        assertTrue(sql.contains("'class.asi_levels'"));
        assertTrue(sql.contains("'feat.grappler'"));
        assertTrue(sql.contains("'feat.prerequisite'"));
        assertTrue(sql.contains("'BLOCKED_PENDING_SPELL_SYSTEM'"));
        assertTrue(sql.contains("'DRAFT'"));
        assertFalse(sql.contains("UPDATE `module_release`"));
        assertFalse(sql.contains("'RELEASED'"));
    }

    @Test
    void addsEventLinkedRuntimeStateAndPinsTheApprovedPayload() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE `character_advancement_choice_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_ability_score_change_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_feat_state_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_multiclass_proficiency_v2`"));
        assertEquals(SchemaMigrations.V015_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("'" + SchemaMigrations.V015_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V015_APPROVED_SHA256 + "'"));
        assertFalse(sql.contains("IF NOT EXISTS"));
        assertFalse(sql.contains("CREATE USER"));
        assertFalse(sql.contains("GRANT "));
        assertFalse(sql.contains("COMMIT"));
        assertFalse(sql.contains("ROLLBACK"));
    }

    @Test
    void verificationIsReadOnlyAndRequiresEmptyRuntimeTables() throws Exception {
        String sql = Files.readString(VERIFY, StandardCharsets.UTF_8);

        assertTrue(sql.contains("'" + SchemaMigrations.V015_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V015_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains("`release_status` = 'DRAFT'"));
        assertTrue(sql.contains("`character_feat_state_v2`) = 0"));
        assertFalse(sql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|GRANT)\\b.*"));
    }
}
