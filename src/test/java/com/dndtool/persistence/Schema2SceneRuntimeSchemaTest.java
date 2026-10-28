package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SceneRuntimeSchemaTest {
    private static final Path MIGRATION=Path.of("src/main/resources/db/migration/V038__schema2-reactions-and-ongoing-scenes.sql");
    private static final List<String> TABLES=List.of("s2_combat_ready_input","s2_combat_ready_point","s2_combat_ready_target",
            "s2_combat_zone_command","s2_combat_health_command","s2_combat_reaction_option","s2_combat_window_trigger",
            "s2_combat_ready_release","s2_combat_ability_check","s2_combat_ready","s2_combat_trigger","s2_combat_shield",
            "s2_combat_concentration","s2_combat_environment","s2_combat_suppression");

    @Test void forwardLedgerAndEveryTypedSceneTableAreImmutableAndAppendOnly()throws Exception {
        String sql=Files.readString(MIGRATION),sha=SchemaMigrations.canonicalPayloadSha256(sql);
        assertTrue(sql.contains("VALUES (38, '"+MIGRATION.getFileName()+"', '"+sha+"')"));
        assertTrue(SchemaMigrations.loadExpectations().stream().anyMatch(e->e.version()==38&&e.scriptSha256().equals(sha)));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM schema_meta)=37"));
        assertTrue(sql.contains("AND NOT EXISTS (SELECT 1 FROM s2_combat_runtime)"));
        assertTrue(sql.indexOf("NOT EXISTS (SELECT 1 FROM s2_combat_runtime)")<sql.indexOf("ALTER TABLE s2_character_event"));
        assertTrue(sql.contains("including ENDED histories"));
        for(String table:TABLES) {
            assertTrue(sql.contains("CREATE TABLE "+table+" ("),table);
            assertTrue(sql.contains("BEFORE UPDATE ON "+table+" FOR EACH ROW"),table);
            assertTrue(sql.contains("BEFORE DELETE ON "+table+" FOR EACH ROW"),table);
        }
        assertFalse(sql.contains(" JSON"));assertFalse(sql.contains("DROP TABLE"));
        assertTrue(sql.contains("combat_status IN (_binary'ACTIVE', _binary'EXPLORATION', _binary'ENDED')"));
        assertTrue(sql.contains("_binary'HEALTH_RESOLVED'"));
        assertTrue(sql.contains("_binary'COMBAT_ENVIRONMENT_HEALTH'"));
        assertTrue(sql.contains("NEW.seconds_advanced BETWEEN 1 AND 60"));
    }

    @Test void nestedMovementsAndIndependentSuppressionEpisodesKeepTheirExactOwners()throws Exception {
        String sql=Files.readString(MIGRATION);
        assertTrue(sql.contains("ADD PRIMARY KEY (combat_id,event_sequence,phase,flow_index,movement_index)"));
        assertTrue(sql.contains("REFERENCES s2_combat_movement (combat_id,event_sequence,phase,flow_index,movement_index) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("released_operation_id IS NULL AND release_reason IS NULL"));
        for(String field:List.of("established_operation_id","released_operation_id","created_operation_id","ended_operation_id","holder_kind","source_key","child_frame_key","target_cast_frame_key","ready_key","initial_hit","final_armor_class","cast_status")) assertTrue(sql.contains(field),field);
        for(String kind:List.of("LEFT_ZONE","ZONE_ENDED","EFFECT_ENDED","READY_SPELL","BLESS_EFFECT","PENDING","HELD","COUNTERED"))assertTrue(sql.contains("_binary'"+kind+"'"),kind);
        assertTrue(sql.contains("s2_combat_opportunity DROP COLUMN attack_frame_key"));
        assertFalse(sql.contains("CREATE TABLE s2_spell_clock"));
    }

    @Test void closedTextEnumsNeverReceiveUuidLengthOrVersionPredicates()throws Exception {
        String sql=Files.readString(MIGRATION);
        for(String field:List.of("holder_kind","release_reason")) {
            assertFalse(sql.contains("SUBSTRING("+field+","),field+" is a text enum, not a UUID");
            assertFalse(sql.contains("OCTET_LENGTH("+field+") = 16"),field+" has variable text length");
        }
        assertTrue(sql.contains("holder_kind IN (_binary'BLESS_EFFECT', _binary'READY_SPELL')"));
        assertTrue(sql.contains("released_operation_id IS NOT NULL AND release_reason IS NOT NULL AND release_reason IN (_binary'LEFT_ZONE', _binary'ZONE_ENDED', _binary'EFFECT_ENDED')"));
        assertTrue(sql.contains("OCTET_LENGTH(source_key) = 16"));
        assertTrue(sql.contains("ORD(SUBSTRING(cause_key, 7, 1))"));
    }

    @Test void readOnlyVerificationAndNarrowGrantsCoverTheCompleteNewGraph()throws Exception {
        String grants=Files.readString(Path.of("database/grants/schema2-combat-runtime.sql"))+Files.readString(Path.of("database/grants/schema2-spell-runtime.sql"));
        String verify=Files.readString(Path.of("database/verify/schema2-combat-runtime.sql"));
        for(String table:TABLES) {
            assertTrue(grants.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`"+table+"`"),table);
            assertTrue(verify.contains(table),table);
        }
        for(String book:List.of("s2_character_event_learned_spell","s2_character_event_prepared_spell","s2_character_spellbook_command","s2_character_spellbook_choice"))assertTrue(grants.contains("`"+book+"`"),book);
        assertFalse(grants.contains("GRANT ALL"));assertFalse(grants.contains("GRANT UPDATE"));assertFalse(grants.contains("GRANT DELETE"));
        assertTrue(verify.contains("s.combat_status <> _binary'ENDED'"));
        for(String count:List.of("ready_count","trigger_count","shield_count","holder_count","zone_count","cause_count","movement_count","release_count","check_count","option_count","point_count"))assertTrue(verify.contains(count+" <> (SELECT COUNT(*) FROM "),count);
    }
}
