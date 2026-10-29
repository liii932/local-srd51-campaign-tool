package com.dndtool.persistence;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2MonsterSchemaTest {
    @Test void newForwardMigrationsKeepTypedAtomsImmutableAndSavingThrowProficienciesExplicit()throws Exception {
        String source=Files.readString(Path.of("database/rules/migration/V011__schema2-monster-source.sql")),runtime=Files.readString(Path.of("src/main/resources/db/migration/V039__schema2-monster-rules.sql"));
        assertEquals(RuleSchemaMigrations.expectations().get(10).scriptSha256(),SchemaMigrations.canonicalPayloadSha256(source));assertEquals(SchemaMigrations.loadExpectations().stream().filter(e->e.version()==39).findFirst().orElseThrow().scriptSha256(),SchemaMigrations.canonicalPayloadSha256(runtime));
        for(String text:List.of(source,runtime)){assertFalse(text.contains(" JSON"));assertFalse(text.contains("CASCADE"));assertTrue(text.contains("ON UPDATE RESTRICT ON DELETE RESTRICT"));for(String atom:List.of("HELL_HOUND_FIRE_BREATH_V1","SAVE_DAMAGE_BATCH_V1","BLOCKED_METADATA","NONMAGICAL_ATTACKS","SAVING_THROW"))assertTrue(text.contains(atom),atom);for(String suffix:List.of("monster","monster_action","monster_stat","monster_save","monster_skill","monster_defense","monster_condition","monster_language","monster_trait","monster_action_value")){String table=(text==source?"rule_schema2_":"s2_runtime_")+suffix;assertTrue(text.contains("CREATE TABLE "+table));for(String op:List.of("UPDATE","DELETE"))assertTrue(text.contains("BEFORE "+op+" ON "+table));}}
        assertTrue(source.contains("FOR UPDATE"));assertTrue(source.contains("CREATE TABLE rule_schema2_monster_change"));assertTrue(runtime.contains("field_key IN (_binary'action_profile'"));assertFalse(runtime.contains("ALTER TABLE s2_character_event"));
    }
}
