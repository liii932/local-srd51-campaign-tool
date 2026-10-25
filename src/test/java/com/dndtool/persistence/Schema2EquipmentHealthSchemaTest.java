package com.dndtool.persistence;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2EquipmentHealthSchemaTest {
    @Test void forwardOnlyTypedSourcesHaveClosedMechanismsScopedAtomsAndImmutableGuards()throws Exception {
        String source=Files.readString(Path.of("database/rules/migration/V007__schema2-equipment-health-source.sql"));
        assertEquals(RuleSchemaMigrations.expectations().get(6).scriptSha256(),SchemaMigrations.canonicalPayloadSha256(source));
        for(String algorithm:List.of("SRD_2014_EQUIPMENT_V1","SRD_2014_HEALTH_V1","DIRECT_VITAL_EFFECT_V1"))assertTrue(source.contains(algorithm));
        assertTrue(source.contains("modifier BETWEEN -1000 AND 1000"));assertTrue(source.contains("stealth_skill"));assertTrue(source.contains("damage_type IS NULL"));assertTrue(source.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));assertFalse(source.contains("CASCADE"));
        for(String table:List.of("equipment_item","health","vital_effect")) {
            assertTrue(source.contains("CREATE TABLE rule_schema2_"+table));
            for(String action:List.of("update","delete"))assertTrue(source.contains("rule_s2_"+table+"_"+action+" BEFORE "+action.toUpperCase()));
        }
        assertTrue(source.contains("CREATE TABLE rule_schema2_execution_change"));assertTrue(source.contains("FOR UPDATE"));
        assertTrue(source.contains("field_key IN (_binary'author_note',_binary'stealth_skill',_binary'damage_type')"));
        assertFalse(source.contains("ALTER TABLE rule_schema2_"));
    }
}
