package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CheckHistorySchemaTest {
    @Test void forwardMigrationStoresOneTypedImmutableResultAndPreservesEarlierHistory() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V029__schema2-character-check-history.sql"));
        assertEquals(SchemaMigrations.V029_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM schema_meta) = 28"));
        assertTrue(sql.contains("V028__schema2-check-rules.sql"));
        assertTrue(sql.contains("CREATE TABLE s2_character_check ("));
        assertTrue(sql.contains("PRIMARY KEY (character_id, event_sequence)"));
        assertTrue(sql.contains("REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("BEFORE INSERT ON s2_character_check"));
        assertTrue(sql.contains("event_kind = _binary'CHECK_RESOLVED' AND hit_die_roll IS NULL"));
        assertTrue(sql.contains("BEFORE UPDATE ON s2_character_check"));
        assertTrue(sql.contains("BEFORE DELETE ON s2_character_check"));
        assertTrue(sql.contains("difficulty_class BETWEEN 0 AND 30"));
        assertTrue(sql.contains("CHAR_LENGTH(adjudication) BETWEEN 1 AND 500"));
        assertTrue(sql.contains("effective_advantage = effective_disadvantage AND second_d20 IS NULL"));
        assertTrue(sql.contains("effective_advantage <> effective_disadvantage AND second_d20 IS NOT NULL"));
        assertTrue(sql.contains("GREATEST(first_d20, second_d20)"));
        assertTrue(sql.contains("LEAST(first_d20, second_d20)"));
        assertTrue(sql.contains("total = selected_d20 + ability_modifier + proficiency_bonus"));
        assertTrue(sql.contains("success = (total >= difficulty_class)"));
        assertTrue(sql.contains("proficiency_module IS NOT NULL AND proficiency_type IS NOT NULL AND proficiency_key IS NOT NULL"));
        for (String forbidden : List.of(" JSON", " BLOB", "CASCADE", "UPDATE s2_character_event", "INSERT INTO campaign", "UPDATE module_release", "DELETE FROM "))
            assertFalse(sql.contains(forbidden), forbidden);
        String structural = sql.replaceAll("'(?:''|[^'])*'", "''").replaceAll("--[^\\n]*", "");
        int depth = 0;
        for (char token : structural.toCharArray()) {
            if (token == '(') depth++; else if (token == ')') depth--;
            assertTrue(depth >= 0, "unexpected closing parenthesis");
            if (token == ';') assertEquals(0, depth, "unclosed SQL statement");
        }
        assertEquals(0, depth);
    }

    @Test void verificationAndLeastPrivilegesIncludeTheNewConsumerFact() throws Exception {
        String grants = Files.readString(Path.of("database/grants/schema2-character-runtime.sql"));
        assertTrue(grants.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_check` TO 'dnd_tool_se_app'@'127.0.0.1';"));
        for (String forbidden : List.of("UPDATE", "DELETE", "GRANT OPTION", "*.*")) assertFalse(grants.contains(forbidden));
        String verification = Files.readString(Path.of("database/verify/schema2-character-runtime.sql"));
        assertTrue(verification.contains("'s2_character_check'"));
        assertTrue(verification.contains("IF(e.event_kind = _binary'CHECK_RESOLVED', 1, 0)"));
        assertTrue(verification.contains("c.selected_d20 = c.first_d20"));
        assertTrue(verification.contains("p.target_module = c.proficiency_module AND p.target_type = c.proficiency_type AND p.target_key = c.proficiency_key"));
    }
}
