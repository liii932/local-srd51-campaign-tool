package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CheckSourceSchemaTest {
    @Test void forwardOnlyCheckSourceAndSnapshotPinClosedMechanismAndImmutableFacts() throws Exception {
        String source = Files.readString(Path.of("database/rules/migration/V006__schema2-check-source.sql"));
        String runtime = Files.readString(Path.of("src/main/resources/db/migration/V028__schema2-check-rules.sql"));
        assertEquals(RuleSchemaMigrations.expectations().get(5).scriptSha256(), SchemaMigrations.canonicalPayloadSha256(source));
        assertEquals(SchemaMigrations.V028_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(runtime));
        for (String sql : List.of(source, runtime)) {
            assertTrue(sql.contains("source_page = 77 AND algorithm = _binary'SRD_2014_ABILITY_CHECK_V1'"));
            assertTrue(sql.contains("^check[.]")); assertTrue(sql.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));
            assertFalse(sql.contains("CASCADE")); assertFalse(sql.contains("CREATE USER"));
        }
        assertTrue(source.contains("rule_schema2_advancement_change ADD CONSTRAINT"));
        assertTrue(source.contains("rule_s2_check_insert BEFORE INSERT")); assertTrue(source.contains("FOR UPDATE"));
        for (String operation : List.of("update", "delete")) {
            assertTrue(source.contains("rule_s2_check_" + operation + " BEFORE " + operation.toUpperCase()));
            assertTrue(runtime.contains("s2_runtime_check_" + operation + " BEFORE " + operation.toUpperCase()));
        }
        assertTrue(runtime.contains("rule_type IN (_binary'adventure.rest', _binary'adventure.check')"));
    }
}
