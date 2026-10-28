package com.dndtool.persistence;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellSchemaTest {
    @Test void typedForwardSourceAndSnapshotMigrationsPreserveReferencesAndImmutability() throws Exception {
        String source=Files.readString(Path.of("database/rules/migration/V009__schema2-spell-source.sql"));
        String runtime=Files.readString(Path.of("src/main/resources/db/migration/V035__schema2-spell-rules.sql"));
        assertEquals(RuleSchemaMigrations.expectations().get(8).scriptSha256(),SchemaMigrations.canonicalPayloadSha256(source));
        assertEquals(SchemaMigrations.loadExpectations().stream().filter(v->v.version()==35).findFirst().orElseThrow().scriptSha256(),SchemaMigrations.canonicalPayloadSha256(runtime));
        for(String sql:List.of(source,runtime)) {
            for(String algorithm:List.of("SRD_2014_BLESS_V1","SRD_2014_CURE_WOUNDS_V1","LIFE_DOMAIN_LEVEL1_V1"))assertTrue(sql.contains(algorithm));
            for(String ref:List.of("owner_class","required_subclass","required_feature","slot_resource","bless","cure_wounds","material_item"))
                for(String suffix:List.of("module_key","rule_type","rule_key"))assertTrue(sql.contains(ref+"_"+suffix));
            assertTrue(sql.contains("HOLY_SYMBOL"));assertTrue(sql.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));assertFalse(sql.contains("CASCADE"));
        }
        for(String table:List.of("spell","spellcasting")) {
            assertTrue(source.contains("CREATE TABLE rule_schema2_"+table));assertTrue(runtime.contains("CREATE TABLE s2_runtime_"+table));
            for(String action:List.of("update","delete")){assertTrue(source.contains("rule_s2_"+table+"_"+action+" BEFORE "+action.toUpperCase()));assertTrue(runtime.contains("s2_runtime_"+table+"_"+action+" BEFORE "+action.toUpperCase()));}
        }
        assertTrue(source.contains("CREATE TABLE rule_schema2_spell_change"));assertTrue(source.contains("FOR UPDATE"));
        assertTrue(runtime.contains("fk_run_spellcasting_slot_resource"));assertTrue(runtime.contains("fk_run_spellcasting_required_feature"));
        assertFalse(runtime.contains("ALTER TABLE s2_character_event"));
    }
}
