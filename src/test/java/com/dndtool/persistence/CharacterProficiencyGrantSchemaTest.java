package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CharacterProficiencyGrantSchemaTest {
    @Test
    void grantsHaveRealCreationAndCatalogParentsAndCannotBeRewritten() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("REFERENCES character_creation_snapshot_v2 (character_id)"));
        assertTrue(sql.contains("FOREIGN KEY (character_id, module_release_id)"));
        assertTrue(sql.contains("FOREIGN KEY (module_release_id, source_type, source_key)"));
        assertTrue(sql.contains("FOREIGN KEY (module_release_id, proficiency_type, proficiency_key)"));
        assertTrue(sql.contains("grant_slot_key, target_module_key, proficiency_key)"));
        assertTrue(sql.contains("s.race_key = NEW.source_key"));
        assertTrue(sql.contains("s.subrace_key = NEW.source_key"));
        assertTrue(sql.contains("s.background_key = NEW.source_key"));
        assertTrue(sql.contains("s.class_key = NEW.source_key"));
        assertTrue(sql.contains("CAST(NEW.source_module_key AS BINARY)"));
        assertTrue(sql.contains("CAST(NEW.target_module_key AS BINARY)"));
        assertTrue(sql.contains("CREATE TRIGGER character_grant_update BEFORE UPDATE"));
        assertFalse(sql.contains("BEFORE DELETE"), "Caller-owned archive cleanup must remain possible");
    }

    @Test
    void forwardMigrationFailsClosedForUnreviewedDraftDataWithoutPublishingOrBusinessSeeds()
            throws Exception {
        String sql = migration();
        assertTrue(sql.contains("COUNT(*) FROM schema_meta) = 21"));
        assertTrue(sql.contains("AND NOT EXISTS (SELECT 1 FROM character_creation_snapshot_v2)"));
        assertTrue(sql.contains("V021__schema2-runtime-snapshot.sql"));
        assertFalse(sql.contains("UPDATE module_release"));
        assertFalse(sql.contains("INSERT INTO character_proficiency_grant_v2"));
        assertFalse(sql.contains("CREATE USER"));
        assertFalse(sql.contains("GRANT "));
        assertEquals("9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b",
                SchemaMigrations.canonicalPayloadSha256(sql));
    }

    private static String migration() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V022__character-proficiency-grants.sql"));
    }
}
