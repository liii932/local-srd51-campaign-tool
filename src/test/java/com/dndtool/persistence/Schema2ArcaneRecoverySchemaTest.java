package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static contract checks; no migration, grant or real database statement is executed. */
class Schema2ArcaneRecoverySchemaTest {
    @Test void forwardMigrationRecordsTypedImmutableRecoveryFactsAgainstExactHistory() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V041__schema2-arcane-recovery.sql"));
        String hash = SchemaMigrations.canonicalPayloadSha256(sql);
        assertTrue(sql.contains("VALUES (41, 'V041__schema2-arcane-recovery.sql', '" + hash + "')"));
        assertTrue(SchemaMigrations.loadExpectations().stream().anyMatch(e -> e.version() == 41 && e.scriptSha256().equals(hash)));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM schema_meta)=40"));
        assertTrue(sql.indexOf("DROP TEMPORARY TABLE v041_preflight") < sql.indexOf("ALTER TABLE s2_character_event"));
        assertTrue(sql.contains("arcane_recovery_choice_count INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("arcane_recovery_day BIGINT NULL"));
        assertTrue(sql.contains("spellbook_casting_module IS NOT NULL AND arcane_recovery_day BETWEEN 0 AND FLOOR(minute/1440)"));
        assertTrue(sql.contains("PRIMARY KEY (character_id,event_sequence,slot_rank)"));
        assertTrue(sql.contains("slot_rank BETWEEN 1 AND 5 AND recovered_value BETWEEN 1 AND 4"));
        assertTrue(sql.contains("REFERENCES s2_character_event (character_id,event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        for (String operation : List.of("INSERT", "UPDATE", "DELETE"))
            assertTrue(sql.contains("BEFORE " + operation + " ON s2_character_arcane_recovery_choice FOR EACH ROW"));
        for (String forbidden : List.of(" JSON", " BLOB", "CASCADE", "UPDATE module_release", "DELETE FROM ", "DROP TABLE ")) assertFalse(sql.contains(forbidden));
    }
    @Test void grantsAreAppendOnlyAndReadOnlyAuditIncludesChoiceCountsRestAndDailyOwnership() throws Exception {
        String grant = Files.readString(Path.of("database/grants/schema2-arcane-recovery.sql"));
        assertTrue(grant.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_arcane_recovery_choice`"));
        for (String forbidden : List.of("GRANT ALL", "UPDATE ON", "DELETE ON", "GRANT OPTION", "CREATE USER")) assertFalse(grant.contains(forbidden));
        String verify = Files.readString(Path.of("database/verify/schema2-arcane-recovery.sql"));
        assertTrue(verify.contains("e.arcane_recovery_choice_count <> (SELECT COUNT(*) FROM s2_character_arcane_recovery_choice"));
        assertTrue(verify.contains("b.minute-b.rest_start_minute>=60"));
        assertTrue(verify.contains("SUM(c.slot_rank*c.recovered_value)"));
        assertTrue(verify.contains("a.arcane_recovery_day=FLOOR(b.minute/1440)"));
        assertTrue(verify.contains("NOT (a.arcane_recovery_day <=> b.arcane_recovery_day)"));
        assertFalse(verify.contains("INSERT INTO")); assertFalse(verify.contains("UPDATE "));
    }
}
