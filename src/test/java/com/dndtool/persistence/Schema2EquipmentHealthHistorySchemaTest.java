package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2EquipmentHealthHistorySchemaTest {
    private static final List<String> HISTORY_TABLES = List.of("s2_character_event_equipment", "s2_character_equipment_command", "s2_character_health_event", "s2_character_health_die");
    private static final List<String> SNAPSHOT_TABLES = List.of("s2_runtime_check", "s2_runtime_equipment_item", "s2_runtime_health", "s2_runtime_vital_effect");

    @Test void forwardMigrationKeepsTypedStateInputsDiceAndImmutableRestrictedReferences() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V031__schema2-equipment-health-history.sql"));
        assertEquals(SchemaMigrations.V031_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM schema_meta) = 30"));
        assertTrue(sql.contains("V030__schema2-equipment-health-rules.sql"));
        for (String table : HISTORY_TABLES) {
            assertTrue(sql.contains("CREATE TABLE " + table + " ("), table);
            assertTrue(sql.contains("BEFORE UPDATE ON " + table), table);
            assertTrue(sql.contains("BEFORE DELETE ON " + table), table);
        }
        assertTrue(sql.contains("REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("REFERENCES s2_character_health_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("UNIQUE KEY uq_s2_equipment_item"));
        assertTrue(sql.contains("UNIQUE KEY uq_s2_equipment_worn"));
        assertTrue(sql.contains("UNIQUE KEY uq_s2_equipment_main_hand"));
        assertTrue(sql.contains("UNIQUE KEY uq_s2_equipment_off_hand"));
        assertTrue(sql.contains("equipment_count BETWEEN 0 AND 32"));
        assertTrue(sql.contains("death_successes BETWEEN 0 AND 2"));
        assertTrue(sql.contains("stable_recovery_minute > minute"));
        assertTrue(sql.contains("NEW.item_id = operation_id"));
        assertTrue(sql.contains("die_count BETWEEN 0 AND 20"));
        assertTrue(sql.contains("die_index BETWEEN 0 AND 19 AND die_value BETWEEN 1 AND 12"));
        assertTrue(sql.contains("NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NOT NULL"));
        for (String forbidden : List.of(" JSON", " BLOB", "CASCADE", "INSERT INTO campaign", "UPDATE module_release", "DELETE FROM ")) assertFalse(sql.contains(forbidden), forbidden);
        // This checks source structure only; actual MySQL CHECK, generated columns and triggers require engine acceptance.
        String structural = sql.replaceAll("'(?:''|[^'])*'", "''").replaceAll("--[^\\n]*", "");
        int depth = 0;
        for (char token : structural.toCharArray()) {
            if (token == '(') depth++; else if (token == ')') depth--;
            assertTrue(depth >= 0, "unexpected closing parenthesis");
            if (token == ';') assertEquals(0, depth, "unclosed SQL statement");
        }
        assertEquals(0, depth);
    }

    @Test void everyNewSnapshotAndHistoryTableHasMinimalGrantsAndInspectionCoverage() throws Exception {
        String grants = Files.readString(Path.of("database/grants/schema2-character-runtime.sql"));
        String verification = Files.readString(Path.of("database/verify/schema2-character-runtime.sql"));
        for (String table : java.util.stream.Stream.concat(HISTORY_TABLES.stream(), SNAPSHOT_TABLES.stream()).toList()) {
            assertTrue(grants.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`" + table + "` TO 'dnd_tool_se_app'@'127.0.0.1';"), table);
            assertTrue(verification.contains("'" + table + "'"), table);
        }
        assertTrue(grants.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_check` TO 'dnd_tool_se_app'@'127.0.0.1';"));
        for (String forbidden : List.of("UPDATE", "DELETE", "GRANT OPTION", "*.*", "CREATE USER")) assertFalse(grants.contains(forbidden));
        assertTrue(verification.contains("s.equipment_count <> (SELECT COUNT(*) FROM s2_character_event_equipment"));
        assertTrue(verification.contains("h.die_count <> (SELECT COUNT(*) FROM s2_character_health_die"));
    }
}
