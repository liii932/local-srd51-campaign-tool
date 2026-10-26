package com.dndtool.persistence;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CombatSchemaTest {
    @Test void sourceAndSnapshotMigrationsKeepExactTypedMechanicsReferencesAndImmutableHistory() throws Exception {
        String source=Files.readString(Path.of("database/rules/migration/V008__schema2-combat-source.sql"));
        String runtime=Files.readString(Path.of("src/main/resources/db/migration/V032__schema2-combat-rules.sql"));
        assertEquals(RuleSchemaMigrations.expectations().get(7).scriptSha256(),SchemaMigrations.canonicalPayloadSha256(source));
        assertEquals(SchemaMigrations.V032_APPROVED_SHA256,SchemaMigrations.canonicalPayloadSha256(runtime));
        for(String sql:List.of(source,runtime)) {
            for(String algorithm:List.of("SRD_2014_COMBAT_V1","SRD_2014_MELEE_WEAPON_ATTACK_V1","SRD_2014_WALKING_SPEED_V1"))assertTrue(sql.contains(algorithm));
            assertTrue(sql.contains("dice_count BETWEEN 1 AND 4"));assertTrue(sql.contains("reach_feet IN (5,10)"));
            assertTrue(sql.contains("walk_speed_feet BETWEEN 5 AND 120 AND MOD(walk_speed_feet,5)=0"));
            assertTrue(sql.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));assertFalse(sql.contains("CASCADE"));
        }
        for(String table:List.of("combat","weapon_attack","movement")) {
            assertTrue(source.contains("CREATE TABLE rule_schema2_"+table));assertTrue(runtime.contains("CREATE TABLE s2_runtime_"+table));
            for(String action:List.of("update","delete")) {assertTrue(source.contains("rule_s2_"+table+"_"+action+" BEFORE "+action.toUpperCase()));assertTrue(runtime.contains("s2_runtime_"+table+"_"+action+" BEFORE "+action.toUpperCase()));}
        }
        assertTrue(source.contains("CREATE TABLE rule_schema2_combat_change"));assertTrue(source.contains("FOR UPDATE"));
        assertTrue(runtime.contains("before_reference_rule_type IN (_binary'character.class', _binary'character.subclass', _binary'character.proficiency', _binary'character.tool', _binary'character.race', _binary'character.subrace', _binary'equipment.item')"));
        assertTrue(runtime.contains("fk_run_weapon_item"));assertTrue(runtime.contains("fk_run_movement_owner"));
        assertFalse(runtime.contains("ALTER TABLE s2_character_event"));
    }
}
