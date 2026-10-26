package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CombatRuntimeSchemaTest {
    private static final List<String> TABLES=List.of("s2_combat_runtime","s2_combat_member","s2_combat_event","s2_combat_dependency","s2_combat_character_result","s2_combat_command_point","s2_combat_command_deployment","s2_combat_command_tie","s2_combat_roll","s2_combat_fact","s2_combat_state","s2_combat_actor","s2_combat_initiative","s2_combat_flow","s2_combat_frame","s2_combat_window","s2_combat_opportunity","s2_combat_opportunity_item","s2_combat_movement","s2_combat_movement_point","s2_combat_attack","s2_combat_attack_die","s2_character_combat_input");
    @Test void forwardMigrationHasExactLedgerTypedGraphAndImmutableCausalHealthFacts()throws Exception{
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V033__schema2-combat-runtime.sql"));
        String hash=SchemaMigrations.canonicalPayloadSha256(sql);
        assertTrue(sql.contains("VALUES (33, 'V033__schema2-combat-runtime.sql', '"+hash+"')"));
        assertTrue(SchemaMigrations.loadExpectations().stream().anyMatch(e->e.version()==33&&e.scriptSha256().equals(hash)));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM schema_meta) = 32"));
        for(String table:TABLES){assertTrue(sql.contains("CREATE TABLE "+table+" ("),table);assertTrue(sql.contains("BEFORE UPDATE ON "+table+" FOR EACH ROW"),table);assertTrue(sql.contains("BEFORE DELETE ON "+table+" FOR EACH ROW"),table);}
        assertFalse(sql.contains(" JSON"));assertFalse(sql.contains("DROP TABLE"));
        assertTrue(sql.contains("REFERENCES s2_character_event (operation_id) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("CREATE TRIGGER s2_character_combat_input_insert"));
        assertTrue(sql.contains("event_kind IN (_binary'DEATH_SAVE_RESOLVED', _binary'COMBAT_DEATH_SAVE_RESOLVED')"));
        assertTrue(sql.contains("event_kind = _binary'COMBAT_DAMAGE_APPLIED' AND NEW.effect_module IS NULL"));
        assertTrue(sql.contains("selection_frame_key VARBINARY(16) NULL"));
        assertTrue(sql.contains("DROP CHECK ck_s2_character_command_0"));
        assertFalse(sql.contains("DROP INDEX uq_s2_character_run"));
        assertFalse(sql.contains("DROP INDEX uq_s2_initial_snapshot"));
    }
    @Test void grantsAreAppendOnlyAndVerifierChecksExactOwnerBindingsAndSavedGraph()throws Exception{
        String grants=Files.readString(Path.of("database/grants/schema2-combat-runtime.sql"));
        String verify=Files.readString(Path.of("database/verify/schema2-combat-runtime.sql"));
        for(String table:TABLES){assertTrue(grants.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`"+table+"`"),table);assertTrue(verify.contains(table),table);}
        assertFalse(grants.contains("GRANT ALL"));assertFalse(grants.contains("UPDATE ON"));assertFalse(grants.contains("DELETE ON"));
        assertTrue(verify.contains("c.campaign_id <> b.campaign_id"));assertTrue(verify.contains("cause.combat_operation_id <> e.operation_id"));
        assertTrue(verify.contains("HAVING COUNT(*) > 1"));
    }
    @Test void preciseSecondsAndGroundFactsExtendTheCandidateWithoutChangingOldFieldValues()throws Exception{
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V034__schema2-combat-time-ground.sql"));
        String hash=SchemaMigrations.canonicalPayloadSha256(sql);
        assertTrue(sql.contains("VALUES (34, 'V034__schema2-combat-time-ground.sql', '"+hash+"')"));
        assertTrue(SchemaMigrations.loadExpectations().stream().anyMatch(e->e.version()==34&&e.scriptSha256().equals(hash)));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM s2_combat_runtime) = 0"));
        assertTrue(sql.contains("ADD rest_second SMALLINT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("last_long_rest_end_second = IF(last_long_rest_end_minute IS NULL, NULL, 0)"));
        assertTrue(sql.contains("stable_recovery_second = IF(stable_recovery_minute IS NULL, NULL, 0)"));
        assertTrue(sql.contains("stable_recovery_second > rest_second"));
        assertTrue(sql.contains("last_long_rest_end_second IS NOT NULL"));
        assertTrue(sql.contains("stable_recovery_second IS NOT NULL"));
        assertTrue(sql.contains("location = _binary'GROUND' AND ground_combat_id IS NOT NULL"));
        assertTrue(sql.contains("ground_x IS NOT NULL AND ground_y IS NOT NULL"));
        assertTrue(sql.contains("RENAME COLUMN movement_spent TO movement_spent_half_feet"));
        assertTrue(sql.indexOf("DROP CHECK ck_c33_11") < sql.indexOf("RENAME COLUMN movement_spent"));
        assertTrue(sql.contains("ADD prone TINYINT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("_binary'STAND_UP'"));assertTrue(sql.contains("_binary'ADVANCE_WINDOW'"));assertTrue(sql.contains("_binary'COMBAT_TIME_ADVANCED'"));
        assertTrue(sql.contains("NEW.seconds_advanced > 0 AND NEW.damage_amount = 0 AND NEW.critical = 0"));
        assertTrue(sql.contains("event_kind = _binary'COMBAT_DEATH_SAVE_RESOLVED' AND NEW.seconds_advanced = 0 AND NEW.damage_amount = 0 AND NEW.critical = 0"));
        assertTrue(sql.contains("flow_key IS NOT NULL AND leaf_key IS NOT NULL AND window_key IS NOT NULL"));
        assertTrue(sql.contains("result_index BETWEEN 0 AND 31"));
    }
}
