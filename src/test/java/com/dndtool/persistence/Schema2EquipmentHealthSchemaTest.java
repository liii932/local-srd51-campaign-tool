package com.dndtool.persistence;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2EquipmentHealthSchemaTest {
    @Test void forwardOnlyTypedSourcesAndSnapshotsHaveClosedMechanismsScopedAtomsAndImmutableGuards()throws Exception {
        String source=Files.readString(Path.of("database/rules/migration/V007__schema2-equipment-health-source.sql")),runtime=Files.readString(Path.of("src/main/resources/db/migration/V030__schema2-equipment-health-rules.sql"));
        assertEquals(RuleSchemaMigrations.expectations().get(6).scriptSha256(),SchemaMigrations.canonicalPayloadSha256(source));assertEquals(SchemaMigrations.V030_APPROVED_SHA256,SchemaMigrations.canonicalPayloadSha256(runtime));
        for(String sql:List.of(source,runtime)) {
            for(String algorithm:List.of("SRD_2014_EQUIPMENT_V1","SRD_2014_HEALTH_V1","DIRECT_VITAL_EFFECT_V1"))assertTrue(sql.contains(algorithm));
            assertTrue(sql.contains("modifier BETWEEN -1000 AND 1000"));assertTrue(sql.contains("stealth_skill"));assertTrue(sql.contains("damage_type IS NULL"));assertTrue(sql.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));assertFalse(sql.contains("CASCADE"));
        }
        for(String table:List.of("equipment_item","health","vital_effect")) {
            assertTrue(source.contains("CREATE TABLE rule_schema2_"+table));assertTrue(runtime.contains("CREATE TABLE s2_runtime_"+table));
            for(String action:List.of("update","delete")){assertTrue(source.contains("rule_s2_"+table+"_"+action+" BEFORE "+action.toUpperCase()));assertTrue(runtime.contains("s2_runtime_"+table+"_"+action+" BEFORE "+action.toUpperCase()));}
        }
        assertTrue(source.contains("CREATE TABLE rule_schema2_execution_change"));assertTrue(source.contains("FOR UPDATE"));
        assertTrue(source.contains("field_key IN (_binary'author_note',_binary'stealth_skill',_binary'damage_type')"));
        assertFalse(source.contains("ALTER TABLE rule_schema2_"));
    }
}
