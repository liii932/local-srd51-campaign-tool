package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CheckSourceSchemaTest {
    @Test void forwardOnlyCheckSourcePinsClosedMechanismAndImmutableFacts() throws Exception {
        String source = Files.readString(Path.of("database/rules/migration/V006__schema2-check-source.sql"));
        assertEquals(RuleSchemaMigrations.expectations().get(5).scriptSha256(), SchemaMigrations.canonicalPayloadSha256(source));
        assertTrue(source.contains("source_page = 77 AND algorithm = _binary'SRD_2014_ABILITY_CHECK_V1'"));
        assertTrue(source.contains("^check[.]")); assertTrue(source.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertFalse(source.contains("CASCADE")); assertFalse(source.contains("CREATE USER"));
        assertTrue(source.contains("rule_schema2_advancement_change ADD CONSTRAINT"));
        assertTrue(source.contains("rule_s2_check_insert BEFORE INSERT")); assertTrue(source.contains("FOR UPDATE"));
        for (String operation : List.of("update", "delete")) {
            assertTrue(source.contains("rule_s2_check_" + operation + " BEFORE " + operation.toUpperCase()));
        }
    }
}
