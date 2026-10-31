package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMigrationsTest {
    @Test
    void packagedMigrationChainContainsApprovedV001ThroughV041() throws Exception {
        List<SchemaMigrations.Expectation> expectations = SchemaMigrations.loadExpectations();

        assertEquals(41, expectations.size());
        assertEquals("V041__schema2-arcane-recovery.sql", expectations.get(40).scriptName());
        assertExpectation(
                expectations.get(0),
                SchemaMigrations.V001_VERSION,
                SchemaMigrations.V001_SCRIPT_NAME,
                SchemaMigrations.V001_APPROVED_SHA256);
        assertExpectation(
                expectations.get(1),
                SchemaMigrations.V002_VERSION,
                SchemaMigrations.V002_SCRIPT_NAME,
                SchemaMigrations.V002_APPROVED_SHA256);
        assertExpectation(
                expectations.get(2),
                SchemaMigrations.V003_VERSION,
                SchemaMigrations.V003_SCRIPT_NAME,
                SchemaMigrations.V003_APPROVED_SHA256);
        assertExpectation(
                expectations.get(3),
                SchemaMigrations.V004_VERSION,
                SchemaMigrations.V004_SCRIPT_NAME,
                SchemaMigrations.V004_APPROVED_SHA256);
        assertExpectation(
                expectations.get(4),
                SchemaMigrations.V005_VERSION,
                SchemaMigrations.V005_SCRIPT_NAME,
                SchemaMigrations.V005_APPROVED_SHA256);
        assertExpectation(
                expectations.get(5),
                SchemaMigrations.V006_VERSION,
                SchemaMigrations.V006_SCRIPT_NAME,
                SchemaMigrations.V006_APPROVED_SHA256);
        assertExpectation(
                expectations.get(6),
                SchemaMigrations.V007_VERSION,
                SchemaMigrations.V007_SCRIPT_NAME,
                SchemaMigrations.V007_APPROVED_SHA256);
        assertExpectation(
                expectations.get(7),
                SchemaMigrations.V008_VERSION,
                SchemaMigrations.V008_SCRIPT_NAME,
                SchemaMigrations.V008_APPROVED_SHA256);
        assertExpectation(
                expectations.get(8),
                SchemaMigrations.V009_VERSION,
                SchemaMigrations.V009_SCRIPT_NAME,
                SchemaMigrations.V009_APPROVED_SHA256);
        assertExpectation(
                expectations.get(9),
                SchemaMigrations.V010_VERSION,
                SchemaMigrations.V010_SCRIPT_NAME,
                SchemaMigrations.V010_APPROVED_SHA256);
        assertExpectation(
                expectations.get(10),
                11,
                "V011__complete_character_catalog_draft.sql",
                SchemaMigrations.V011_APPROVED_SHA256);
        assertExpectation(
                expectations.get(11),
                12,
                "V012__level_one_character_creation.sql",
                SchemaMigrations.V012_APPROVED_SHA256);
        assertExpectation(
                expectations.get(12),
                13,
                "V013__level_advancement_hit_dice.sql",
                SchemaMigrations.V013_APPROVED_SHA256);
        assertExpectation(
                expectations.get(13),
                14,
                "V014__class_feature_lifecycle.sql",
                SchemaMigrations.V014_APPROVED_SHA256);
        assertExpectation(
                expectations.get(14),
                15,
                "V015__multiclass_asi_feat_draft.sql",
                SchemaMigrations.V015_APPROVED_SHA256);
        assertExpectation(
                expectations.get(15),
                16,
                "V016__starting_proficiency_baseline_draft.sql",
                SchemaMigrations.V016_APPROVED_SHA256);
        assertExpectation(
                expectations.get(16),
                17,
                "V017__character_archive_v2_origin.sql",
                SchemaMigrations.V017_APPROVED_SHA256);
        assertExpectation(
                expectations.get(17),
                18,
                "V018__multiclass_spell_slot_foundation.sql",
                SchemaMigrations.V018_APPROVED_SHA256);
        assertExpectation(
                expectations.get(18),
                SchemaMigrations.V019_VERSION,
                SchemaMigrations.V019_SCRIPT_NAME,
                SchemaMigrations.V019_APPROVED_SHA256);
        assertExpectation(expectations.get(19), SchemaMigrations.V020_VERSION,
                SchemaMigrations.V020_SCRIPT_NAME, SchemaMigrations.V020_APPROVED_SHA256);
        assertExpectation(expectations.get(20), SchemaMigrations.V021_VERSION,
                SchemaMigrations.V021_SCRIPT_NAME, SchemaMigrations.V021_APPROVED_SHA256);
        assertExpectation(expectations.get(21), SchemaMigrations.V022_VERSION,
                SchemaMigrations.V022_SCRIPT_NAME, SchemaMigrations.V022_APPROVED_SHA256);
        assertExpectation(expectations.get(22), SchemaMigrations.V023_VERSION,
                SchemaMigrations.V023_SCRIPT_NAME, SchemaMigrations.V023_APPROVED_SHA256);
        assertExpectation(expectations.get(23), SchemaMigrations.V024_VERSION,
                SchemaMigrations.V024_SCRIPT_NAME, SchemaMigrations.V024_APPROVED_SHA256);
        assertExpectation(expectations.get(24), SchemaMigrations.V025_VERSION,
                SchemaMigrations.V025_SCRIPT_NAME, SchemaMigrations.V025_APPROVED_SHA256);
        assertExpectation(expectations.get(25), SchemaMigrations.V026_VERSION,
                SchemaMigrations.V026_SCRIPT_NAME, SchemaMigrations.V026_APPROVED_SHA256);
        assertExpectation(expectations.get(26), SchemaMigrations.V027_VERSION,
                SchemaMigrations.V027_SCRIPT_NAME, SchemaMigrations.V027_APPROVED_SHA256);
        assertExpectation(expectations.get(27), SchemaMigrations.V028_VERSION, SchemaMigrations.V028_SCRIPT_NAME, SchemaMigrations.V028_APPROVED_SHA256);
        assertExpectation(expectations.get(28), SchemaMigrations.V029_VERSION, SchemaMigrations.V029_SCRIPT_NAME, SchemaMigrations.V029_APPROVED_SHA256);
        assertExpectation(expectations.get(29), SchemaMigrations.V030_VERSION, SchemaMigrations.V030_SCRIPT_NAME, SchemaMigrations.V030_APPROVED_SHA256);
        assertExpectation(expectations.get(30), SchemaMigrations.V031_VERSION, SchemaMigrations.V031_SCRIPT_NAME, SchemaMigrations.V031_APPROVED_SHA256);
        assertExpectation(expectations.get(31), SchemaMigrations.V032_VERSION, SchemaMigrations.V032_SCRIPT_NAME, SchemaMigrations.V032_APPROVED_SHA256);
        assertExpectation(expectations.get(32), SchemaMigrations.V033_VERSION, SchemaMigrations.V033_SCRIPT_NAME, SchemaMigrations.V033_APPROVED_SHA256);
        assertExpectation(expectations.get(33), SchemaMigrations.V034_VERSION, SchemaMigrations.V034_SCRIPT_NAME, SchemaMigrations.V034_APPROVED_SHA256);
        assertExpectation(expectations.get(34), SchemaMigrations.V035_VERSION, SchemaMigrations.V035_SCRIPT_NAME, SchemaMigrations.V035_APPROVED_SHA256);
        assertExpectation(expectations.get(35), SchemaMigrations.V036_VERSION, SchemaMigrations.V036_SCRIPT_NAME, SchemaMigrations.V036_APPROVED_SHA256);
        assertExpectation(expectations.get(36), 37, "V037__schema2-wizard-environment-rules.sql", "7937263eb20e92adab82785aed6dead9ad99bd5fa46c941505c483bbc0f68cfb");
        assertExpectation(expectations.get(37), 38, "V038__schema2-reactions-and-ongoing-scenes.sql", "3449378f943deea0fee5b3711c13f2a67fde7edaafe0646f6b650a513de37ee5");
        assertExpectation(expectations.get(38), SchemaMigrations.V039_VERSION, SchemaMigrations.V039_SCRIPT_NAME, SchemaMigrations.V039_APPROVED_SHA256);
        assertExpectation(expectations.get(39), SchemaMigrations.V040_VERSION, SchemaMigrations.V040_SCRIPT_NAME, SchemaMigrations.V040_APPROVED_SHA256);
    }

    @Test
    void canonicalChecksumIgnoresLineEndingStyle() throws Exception {
        String lf = "header\n-- CHECKSUM-SCOPE-BEGIN\nSELECT 1;\n-- CHECKSUM-SCOPE-END\n";
        String crlf = lf.replace("\n", "\r\n");

        assertEquals(
                SchemaMigrations.canonicalPayloadSha256(lf),
                SchemaMigrations.canonicalPayloadSha256(crlf));
    }

    @Test
    void malformedChecksumMarkersAreRejected() {
        assertThrows(
                SchemaMigrations.PackagedSchemaException.class,
                () -> SchemaMigrations.canonicalPayloadSha256("SELECT 1;"));
    }

    private static void assertExpectation(
            SchemaMigrations.Expectation expectation,
            int version,
            String scriptName,
            String scriptSha256) {
        assertEquals(version, expectation.version());
        assertEquals(scriptName, expectation.scriptName());
        assertEquals(scriptSha256, expectation.scriptSha256());
    }
}
