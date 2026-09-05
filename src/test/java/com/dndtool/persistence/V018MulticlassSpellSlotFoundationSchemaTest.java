package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the forward-only canonical-v2 multiclass spell-slot foundation. */
final class V018MulticlassSpellSlotFoundationSchemaTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V018__multiclass_spell_slot_foundation.sql");
    private static final Path VERIFY = Path.of(
            "database/verify/v018-multiclass-spell-slot-foundation.sql");

    @Test
    void seedsAllClassProgressionsWithoutPublishingOrInventingRuntimeState() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertTrue(sql.contains("'class.multiclass_spellcasting_progression'"));
        assertTrue(sql.contains("('class.bard', 'FULL')"));
        assertTrue(sql.contains("('class.paladin', 'HALF_DOWN')"));
        assertTrue(sql.contains("('class.ranger', 'HALF_DOWN')"));
        assertTrue(sql.contains("('class.warlock', 'PACT_MAGIC')"));
        assertTrue(sql.contains("@v018_seed_count = 12"));
        assertTrue(sql.contains("@v018_inserted_count = 12"));
        assertTrue(sql.contains("'DRAFT'"));
        assertFalse(sql.contains("'RELEASED'"));
        assertFalse(sql.contains("UPDATE `module_release`"));
        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("CREATE USER"));
        assertFalse(sql.contains("GRANT "));
    }

    @Test
    void pinsTheApprovedIdentityAndReadOnlyVerification() throws Exception {
        String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        String verify = Files.readString(VERIFY, StandardCharsets.UTF_8);

        assertTrue(migration.contains("'" + SchemaMigrations.V018_SCRIPT_NAME + "'"));
        assertTrue(migration.contains("'" + SchemaMigrations.V018_APPROVED_SHA256 + "'"));
        assertTrue(migration.replace("\r\n", "\n").contains("VALUES (\n    18,"));
        assertTrue(SchemaMigrations.V018_APPROVED_SHA256.equals(
                SchemaMigrations.canonicalPayloadSha256(migration)));
        assertTrue(verify.contains("'" + SchemaMigrations.V018_SCRIPT_NAME + "'"));
        assertTrue(verify.contains("'" + SchemaMigrations.V018_APPROVED_SHA256 + "'"));
        assertTrue(verify.contains("`release_status` = 'DRAFT'"));
        assertFalse(verify.matches(
                "(?is).*\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|GRANT)\\b.*"));
    }
}
