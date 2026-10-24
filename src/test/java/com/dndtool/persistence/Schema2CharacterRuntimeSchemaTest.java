package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CharacterRuntimeSchemaTest {
    private static final List<String> TABLES = List.of("s2_runtime_class_progression", "s2_runtime_resource", "s2_runtime_subclass", "s2_runtime_feature", "s2_runtime_feat", "s2_runtime_proficiency", "s2_runtime_proficiency_set", "s2_runtime_rest", "s2_runtime_class_level_entry", "s2_runtime_proficiency_entry", "s2_runtime_growth_grant", "s2_runtime_rule_prerequisite", "s2_runtime_chain_prerequisite", "s2_character_runtime", "s2_character_event", "s2_character_event_state", "s2_character_event_resource", "s2_character_event_class", "s2_character_event_hit_die", "s2_character_event_subclass", "s2_character_event_feature", "s2_character_event_feat", "s2_character_event_proficiency", "s2_character_command_ability", "s2_character_command_proficiency", "s2_character_command_hit_die");
    @Test void typedForwardMigrationPreservesImmutableHistoryAndExactBindings() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V027__schema2-character-runtime.sql"));
        assertEquals(SchemaMigrations.V027_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        for (String table : TABLES) {
            assertTrue(sql.contains("CREATE TABLE " + table + " ("));
            assertTrue(sql.contains("BEFORE UPDATE ON " + table));
            assertTrue(sql.contains("BEFORE DELETE ON " + table));
        }
        assertTrue(sql.contains("MODIFY rule_type VARBINARY(32)"));
        assertTrue(sql.contains("MODIFY field_key VARBINARY(32)"));
        assertTrue(sql.contains("proficiency_bonus_profile"));
        assertTrue(sql.contains("recovery_profile VARCHAR(200) NOT NULL"));
        assertTrue(sql.contains("'s2_character_command_hit_die')) = 0"));
        assertTrue(sql.contains("'^progression[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$'"));
        assertTrue(sql.contains("'^resource[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$'"));
        // This is lexical regression protection, not a MySQL parser or engine acceptance.
        String structural = sql.replaceAll("'(?:''|[^'])*'", "''").replaceAll("--[^\\n]*", "");
        int parentheses = 0;
        for (char token : structural.toCharArray()) {
            if (token == '(') parentheses++;
            else if (token == ')') parentheses--;
            assertTrue(parentheses >= 0, "unexpected closing parenthesis");
            if (token == ';') assertEquals(0, parentheses, "unclosed SQL statement");
        }
        assertEquals(0, parentheses);
        assertTrue(sql.contains("STATISTICS_AND_RESOURCES_ONLY"));
        assertTrue(sql.contains("UNIQUE KEY uq_s2_character_preparation (preparation_operation_id)"));
        assertTrue(sql.contains("UNIQUE KEY uq_s2_character_run (run_id)"));
        assertTrue(sql.contains("CHARACTER_ADVANCEMENT"));
        assertTrue(sql.contains("reference_rule_type VARBINARY(32)"));
        assertTrue(sql.contains("prerequisite_groups INT"));
        assertTrue(sql.contains("consecutive_strenuous_minutes"));
        assertTrue(sql.contains("proficiency_count BETWEEN 0 AND 64"));
        assertTrue(sql.contains("UNIQUE KEY uq_s2_character_operation (operation_id)"));
        assertTrue(sql.contains("PRIMARY KEY (character_id, event_sequence, phase)"));
        assertTrue(sql.contains("row_version = event_sequence - 1"));
        assertTrue(sql.contains("row_version = event_sequence - 2"));
        assertTrue(sql.contains("character_id = NEW.character_id AND campaign_id = NEW.campaign_id AND run_id = NEW.run_id AND snapshot_id = NEW.snapshot_id"));
        for (String forbidden : List.of(" JSON", " BLOB", "CASCADE", "INSERT INTO campaign", "UPDATE module_release", "DELETE FROM ")) assertFalse(sql.contains(forbidden), forbidden);
    }
    @Test void nullableCommandUnionsCloseRequiredFieldsAndKeepT1ProvenanceInStateOnly() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V027__schema2-character-runtime.sql"));
        String resource = constraint(sql, "ck_s2_character_command_3");
        assertTrue(resource.contains("resource_module IS NOT NULL AND resource_type IS NOT NULL AND resource_type = _binary'character.resource' AND resource_key IS NOT NULL"));
        String command = constraint(sql, "ck_s2_character_command_proficiency");
        assertTrue(command.contains("source_type = _binary'character.class_progression'"));
        assertTrue(command.contains("collection_key IN (_binary'starting_proficiencies', _binary'multiclass_proficiencies')"));
        assertFalse(command.contains("proficiency_grants"));
        assertTrue(constraint(sql, "ck_s2_character_proficiency_shape").contains("collection_key = _binary'proficiency_grants'"));
        String verification = Files.readString(Path.of("database/verify/schema2-character-runtime.sql"));
        assertTrue(verification.contains("e.resource_type IS NULL"));
        assertTrue(verification.contains("multiclass_proficiencies')) IS NOT TRUE"));
    }
    private static String constraint(String sql, String name) {
        return sql.lines().map(String::trim).filter(line -> line.startsWith("CONSTRAINT " + name + " CHECK (")).findFirst().orElseThrow();
    }
    @Test void runtimeAccountGetsOnlyTypedAppendAndReadPrivileges() throws Exception {
        String sql = Files.readString(Path.of("database/grants/schema2-character-runtime.sql"));
        for (String table : TABLES) assertTrue(sql.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`" + table + "` TO 'dnd_tool_se_app'@'127.0.0.1';"));
        for (String forbidden : List.of("UPDATE", "DELETE", "GRANT OPTION", "*.*", "CREATE USER")) assertFalse(sql.contains(forbidden));
    }
}
