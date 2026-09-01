package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the forward-only archive-format-2 DRAFT state-origin migration. */
final class V017CharacterArchiveV2OriginSchemaTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V017__character_archive_v2_origin.sql");
    private static final Path VERIFY = Path.of(
            "database/verify/v017-character-archive-v2-origin.sql");

    @Test
    void addsExplicitRestoreOriginWithoutChangingTheDraftRelease() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("ENUM('ADVANCEMENT', 'ARCHIVE_RESTORE')"));
        assertTrue(sql.contains("`state_origin` = 'ADVANCEMENT'"));
        assertTrue(sql.contains("`event_type` = 'CHARACTER_LEVEL_ADVANCED'"));
        assertTrue(sql.contains("`subject_character_id` = NEW.`character_id`"));
        assertTrue(sql.contains("CREATE TRIGGER `trg_character_feat_state_v2_origin_insert`"));
        assertTrue(sql.contains("CREATE TRIGGER `trg_multiclass_proficiency_v2_origin_insert`"));
        assertTrue(sql.contains("Feat state is immutable"));
        assertTrue(sql.contains("Multiclass proficiency state is immutable"));
        assertTrue(sql.contains("'DRAFT'"));
        assertFalse(sql.contains("'RELEASED'"));
        assertFalse(sql.contains("UPDATE `module_release`"));
        assertFalse(sql.contains("CREATE USER"));
        assertFalse(sql.contains("GRANT "));
    }

    @Test
    void pinsTheApprovedIdentityAndReadOnlyVerification() throws Exception {
        String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String verify = Files.readString(VERIFY, StandardCharsets.UTF_8);

        assertTrue(migration.contains("'" + SchemaMigrations.V017_SCRIPT_NAME + "'"));
        assertTrue(migration.contains("'" + SchemaMigrations.V017_APPROVED_SHA256 + "'"));
        assertTrue(migration.replace("\r\n", "\n").contains("VALUES (\n    17,"));
        assertTrue(SchemaMigrations.V017_APPROVED_SHA256.equals(
                SchemaMigrations.canonicalPayloadSha256(migration)));
        assertTrue(verify.contains("'" + SchemaMigrations.V017_SCRIPT_NAME + "'"));
        assertTrue(verify.contains("'" + SchemaMigrations.V017_APPROVED_SHA256 + "'"));
        assertTrue(verify.contains("`release_status` = 'DRAFT'"));
        assertFalse(verify.matches("(?is).*\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|GRANT)\\b.*"));
    }
}
