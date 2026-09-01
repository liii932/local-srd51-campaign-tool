package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the forward-only canonical-v2 class-feature lifecycle migration. */
final class V014ClassFeatureLifecycleSchemaTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V014__class_feature_lifecycle.sql");
    private static final Path VERIFY = Path.of(
            "database/verify/v014-class-feature-lifecycle.sql");

    @Test
    void freezesFeatureDispositionAndSubclassSelectionInsideTheDraftCatalog()
            throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("'feature.execution_mode'"));
        assertTrue(sql.contains("'feature.execution_algorithm'"));
        assertTrue(sql.contains("'subclass.selection_level'"));
        assertTrue(sql.contains("'resource.execution_mode'"));
        assertTrue(sql.contains("'resource.recovery_profile'"));
        assertTrue(sql.contains("'AUTOMATIC'"));
        assertTrue(sql.contains("'DM_ADJUDICATION'"));
        assertTrue(sql.contains("'BLOCKED'"));
        assertTrue(sql.contains("'BLOCKED_SPELL_SYSTEM_V1'"));
        assertTrue(sql.contains("'BLOCKED_ISSUE_8_V1'"));
        assertTrue(sql.contains("= 236"));
        assertTrue(sql.contains("@v014_automatic_feature_count = 13"));
        assertTrue(sql.contains("@v014_adjudicated_feature_count = 24"));
        assertTrue(sql.contains("@v014_blocked_feature_count = 199"));
        assertTrue(sql.contains("= 12"));
        assertTrue(sql.contains("= 16"));
        assertTrue(sql.contains("'DRAFT'"));
        assertFalse(sql.contains("'RELEASED'"));
        assertFalse(sql.contains("UPDATE `module_release`"));
    }

    @Test
    void addsOnlyEventLinkedCanonicalV2RuntimeState() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE `character_subclass_state_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_feature_state_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_feature_choice_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_feature_adjudication_v2`"));
        assertTrue(sql.contains("CREATE TABLE `character_resource_recovery_v2`"));
        assertTrue(sql.contains("'CHARACTER_FEATURE_ADJUDICATED'"));
        assertTrue(sql.contains("'CHARACTER_RESOURCES_RECOVERED'"));
        assertTrue(sql.replace("\r\n", "\n").contains("VALUES (\n    14,"));
        assertEquals(SchemaMigrations.V014_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("'" + SchemaMigrations.V014_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V014_APPROVED_SHA256 + "'"));
        assertFalse(sql.contains("IF NOT EXISTS"));
        assertFalse(sql.contains("CREATE USER"));
        assertFalse(sql.contains("GRANT "));
        assertFalse(sql.contains("COMMIT"));
        assertFalse(sql.contains("ROLLBACK"));
    }

    @Test
    void verificationIsReadOnlyAndPinsTheApprovedIdentityAndEmptyTables() throws Exception {
        String sql = Files.readString(VERIFY, StandardCharsets.UTF_8);

        assertTrue(sql.contains("'" + SchemaMigrations.V014_SCRIPT_NAME + "'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V014_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains("= 13"));
        assertTrue(sql.contains("= 24"));
        assertTrue(sql.contains("= 199"));
        assertTrue(sql.contains("`release_status` = 'DRAFT'"));
        assertFalse(sql.contains("archive_format_version"));
        assertTrue(sql.contains("`character_feature_adjudication_v2`) = 0"));
        assertFalse(sql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|GRANT)\\b.*"));
    }
}
