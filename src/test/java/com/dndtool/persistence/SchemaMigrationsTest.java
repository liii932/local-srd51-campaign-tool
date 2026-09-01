package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaMigrationsTest {
    @Test
    void packagedMigrationChainContainsApprovedV001ThroughV017() throws Exception {
        List<SchemaMigrations.Expectation> expectations = SchemaMigrations.loadExpectations();

        assertEquals(17, expectations.size());
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
                SchemaMigrations.V011_VERSION,
                SchemaMigrations.V011_SCRIPT_NAME,
                SchemaMigrations.V011_APPROVED_SHA256);
        assertExpectation(
                expectations.get(11),
                SchemaMigrations.V012_VERSION,
                SchemaMigrations.V012_SCRIPT_NAME,
                SchemaMigrations.V012_APPROVED_SHA256);
        assertExpectation(
                expectations.get(12),
                SchemaMigrations.V013_VERSION,
                SchemaMigrations.V013_SCRIPT_NAME,
                SchemaMigrations.V013_APPROVED_SHA256);
        assertExpectation(
                expectations.get(13),
                SchemaMigrations.V014_VERSION,
                SchemaMigrations.V014_SCRIPT_NAME,
                SchemaMigrations.V014_APPROVED_SHA256);
        assertExpectation(
                expectations.get(14),
                SchemaMigrations.V015_VERSION,
                SchemaMigrations.V015_SCRIPT_NAME,
                SchemaMigrations.V015_APPROVED_SHA256);
        assertExpectation(
                expectations.get(15),
                SchemaMigrations.V016_VERSION,
                SchemaMigrations.V016_SCRIPT_NAME,
                SchemaMigrations.V016_APPROVED_SHA256);
        assertExpectation(
                expectations.get(16),
                SchemaMigrations.V017_VERSION,
                SchemaMigrations.V017_SCRIPT_NAME,
                SchemaMigrations.V017_APPROVED_SHA256);
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
