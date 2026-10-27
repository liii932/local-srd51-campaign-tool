package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellRuntimeSchemaTest {
    private static final List<String> TABLES=List.of("s2_combat_cast_command","s2_combat_cast_command_target","s2_combat_ongoing_effect","s2_combat_ongoing_target","s2_combat_cast","s2_combat_cast_target","s2_combat_cast_die","s2_combat_saving_throw");
    @Test void forwardMigrationHasExactLedgerAndImmutableTypedSpellFacts()throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V036__schema2-spell-runtime.sql"));
        String hash=SchemaMigrations.canonicalPayloadSha256(sql);
        assertTrue(sql.contains("VALUES (36, 'V036__schema2-spell-runtime.sql', '"+hash+"')"));
        assertTrue(SchemaMigrations.loadExpectations().stream().anyMatch(e->e.version()==36&&e.scriptSha256().equals(hash)));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM schema_meta) = 35"));
        for(String table:TABLES) {
            assertTrue(sql.contains("CREATE TABLE "+table+" ("),table);
            assertTrue(sql.contains("BEFORE UPDATE ON "+table+" FOR EACH ROW"),table);
            assertTrue(sql.contains("BEFORE DELETE ON "+table+" FOR EACH ROW"),table);
        }
        assertFalse(sql.contains(" JSON"));assertFalse(sql.contains("DROP TABLE"));
        assertTrue(sql.contains("_binary'COMBAT_RESOURCE_SPENT'"));assertTrue(sql.contains("_binary'COMBAT_HEALING_APPLIED'"));
        assertTrue(sql.contains("event_kind IN (_binary'RESOURCE_SPENT', _binary'COMBAT_RESOURCE_SPENT')"));
        assertTrue(sql.contains("REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("command_kind = _binary'CAST'"));
        assertTrue(sql.contains("blessing_d4 IS NOT NULL AND blessing_d4 BETWEEN 1 AND 4"));
        assertTrue(sql.contains("expires_at_second > started_at_second"));
        assertFalse(sql.contains("OCTET_LENGTH(frame_status)"));
        assertTrue(sql.contains("_binary'CAST_RESOLVED'"));
    }
    @Test void onlyCombatDeathSaveAcceptsOneOptionalBlessDie() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V036__schema2-spell-runtime.sql"));
        assertTrue(sql.contains("event_kind = _binary'DEATH_SAVE_RESOLVED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NOT NULL"));
        assertTrue(sql.contains("event_kind = _binary'COMBAT_DEATH_SAVE_RESOLVED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count BETWEEN 0 AND 1 AND NEW.death_d20 IS NOT NULL"));
    }
    @Test void grantsRemainAppendOnlyAndVerificationCoversTargetsAndCharacterCauses()throws Exception {
        String grants=Files.readString(Path.of("database/grants/schema2-spell-runtime.sql"));
        String verify=Files.readString(Path.of("database/verify/schema2-spell-runtime.sql"));
        for(String table:TABLES) {
            assertTrue(grants.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`"+table+"`"),table);
            assertTrue(verify.contains(table),table);
        }
        assertFalse(grants.contains("GRANT ALL"));assertFalse(grants.contains("UPDATE ON"));assertFalse(grants.contains("DELETE ON"));
        assertTrue(verify.contains("root.operation_id = c.combat_operation_id"));
        assertTrue(verify.contains("m.character_id = t.target_id"));
    }
    @Test void verificationChecksEverySpellChildCountAndScopedOrdinal() throws Exception {
        String verify=Files.readString(Path.of("database/verify/schema2-spell-runtime.sql")).replace("\r\n","\n");
        for(String count:List.of("s.ongoing_effect_count", "f.cast_count", "f.save_count", "c.healing_die_count"))
            assertTrue(verify.contains(count+" <> (SELECT COUNT(*) FROM "),count);
        for(String table:List.of("s2_combat_cast_command_target", "s2_combat_cast_target"))
            assertTrue(verify.contains("c.target_count <> (SELECT COUNT(*) FROM "+table),table);
        assertTrue(verify.contains("e.target_count <> (SELECT COUNT(*) FROM s2_combat_ongoing_target"));
        for(String index:List.of("t.target_index", "e.effect_index", "c.cast_index", "d.die_index", "s.save_index"))
            assertTrue(verify.contains("HAVING MIN("+index+") <> 0 OR MAX("+index+") <> COUNT(*) - 1"),index);
        for(String scope:List.of("t.combat_id, t.event_sequence", "e.combat_id, e.event_sequence, e.phase",
                "t.combat_id, t.event_sequence, t.phase, t.effect_index", "c.combat_id, c.event_sequence, c.phase, c.flow_index",
                "t.combat_id, t.event_sequence, t.phase, t.flow_index, t.cast_index", "s.combat_id, s.event_sequence, s.phase, s.flow_index"))
            assertTrue(verify.contains("GROUP BY "+scope+"\nHAVING"),scope);
        assertTrue(verify.contains("e.event_kind = _binary'DEATH_SAVE_RESOLVED' AND h.die_count <> 0"));
        assertTrue(verify.contains("e.event_kind = _binary'COMBAT_DEATH_SAVE_RESOLVED' AND h.die_count NOT BETWEEN 0 AND 1"));
        assertTrue(verify.contains("h.die_count <> (SELECT COUNT(*) FROM s2_character_health_die"));
        assertTrue(verify.contains("d.die_value NOT BETWEEN 1 AND 4"));
    }
}
