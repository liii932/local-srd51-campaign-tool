package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Offline regression guards for reviewed source structure; never substitutes for MySQL acceptance. */
class RuleSourceSchemaContractTest {
    private static final Set<String> TABLES = Set.of("rule_schema_meta", "rule_release", "rule_language",
            "rule_package_installation", "rule_package_installation_partition", "rule_installation_control");

    @Test
    void sixInnoDbTablesHaveOnlyLocalRestrictedForeignKeysAndNoContentSeeds() throws Exception {
        String sql = Files.readString(RuleSchemaMigrationsTest.SOURCE);
        Map<String, String> tables = blocks(sql, "CREATE TABLE (\\w+) \\((.*?)\\) ENGINE=InnoDB");
        assertEquals(TABLES, tables.keySet());
        assertEquals(1, sql.split("AUTO_INCREMENT", -1).length - 1);
        assertTrue(tables.get("rule_release").contains("UNIQUE KEY uq_rule_release_identity (module_key, release_version)"));
        assertTrue(tables.get("rule_language").contains("UNIQUE KEY uq_rule_language_sort (release_id, sort_order)"));
        var fk = Pattern.compile("FOREIGN KEY \\(([^)]+)\\)\\s+REFERENCES (\\w+) \\(([^)]+)\\)\\s+ON UPDATE RESTRICT ON DELETE RESTRICT").matcher(sql);
        Map<String, String> foreignKeys = new LinkedHashMap<>();
        while (fk.find()) foreignKeys.put(fk.group(2) + "/" + foreignKeys.size(), fk.group(1) + "->" + fk.group(3));
        assertEquals(Map.of("rule_release/0", "release_id->id", "rule_release/1", "release_id->id",
                "rule_package_installation/2", "release_id, installation_revision->release_id, installation_revision"), foreignKeys);
        var inserts = Pattern.compile("(?m)^INSERT INTO (\\w+)").matcher(sql);
        var targets = new java.util.ArrayList<String>();
        while (inserts.find()) targets.add(inserts.group(1));
        assertEquals(List.of("rule_installation_control", "rule_schema_meta"), targets);
        assertTrue(sql.contains("VALUES (1, 1, 1, 0)"));
        for (String forbidden : List.of("dnd_tool_se.", "CASCADE", "CREATE DATABASE", "CREATE USER", "USE ")) {
            assertFalse(stripComments(sql).contains(forbidden));
        }
    }

    @Test
    void guardsProtectEveryMutationAndAvoidBinaryRegexAndAutoIncrementChecks() throws Exception {
        String sql = Files.readString(RuleSchemaMigrationsTest.SOURCE);
        Map<String, String> triggers = blocks(sql, "CREATE TRIGGER (\\w+) (.*?)END\\$\\$");
        assertEquals(18, triggers.size());
        for (String name : List.of("rule_language_insert", "rule_language_update", "rule_language_delete",
                "rule_installation_insert", "rule_partition_insert")) {
            assertTrue(triggers.get(name).contains("FOR UPDATE"), name);
            assertTrue(triggers.get(name).contains("root_status <> _binary'DRAFT'"), name);
        }
        for (String name : List.of("rule_release_delete", "rule_installation_update", "rule_installation_delete",
                "rule_partition_update", "rule_partition_delete", "rule_control_delete", "rule_schema_update", "rule_schema_delete")) {
            assertTrue(triggers.get(name).contains("SIGNAL SQLSTATE '45000'"), name);
            assertFalse(triggers.get(name).contains("IF "), name);
        }
        assertTrue(triggers.get("rule_release_id").startsWith("AFTER INSERT"));
        assertTrue(triggers.get("rule_release_id").contains("NEW.id <= 0"));
        assertTrue(triggers.get("rule_release_update").contains("OLD.release_status = _binary'RELEASED'"));
        assertFalse(triggers.get("rule_release_update").contains("SELECT"));
        assertTrue(triggers.get("rule_language_update").contains("NEW.release_id <> OLD.release_id OR NEW.language_key <> OLD.language_key"));
        assertFalse(stripComments(sql).contains("@"));
        assertFalse(Pattern.compile("CHECK\\s*\\(id\\s*[><=]").matcher(sql).find());
        var binaryRegex = Pattern.compile("REGEXP_LIKE\\((\\w+),").matcher(sql);
        while (binaryRegex.find()) assertTrue(Set.of("display_name", "description", "package_display_name").contains(binaryRegex.group(1)));
    }

    @Test
    void installerAndReadersHaveOnlyEnumeratedTableAndColumnCapabilities() throws Exception {
        for (String role : List.of("app", "agent")) {
            String grants = stripComments(Files.readString(Path.of("database/grants/rule-source-" + role + ".sql")));
            var matcher = Pattern.compile("GRANT SELECT ON `dnd_tool_rules`[.]`(\\w+)` TO 'dnd_tool_rules_" + role + "'@'127[.]0[.]0[.]1';").matcher(grants);
            var tables = new java.util.HashSet<String>();
            while (matcher.find()) tables.add(matcher.group(1));
            var currentTables = new java.util.HashSet<>(TABLES);
            currentTables.add("rule_tool");
            currentTables.addAll(Set.of("rule_schema2_artifact", "rule_schema2_acceptance", "rule_schema2_control", "rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change", "rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change", "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set", "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant", "rule_schema2_ability_requirement", "rule_schema2_rest", "rule_schema2_check", "rule_schema2_equipment_item", "rule_schema2_execution_change", "rule_schema2_health", "rule_schema2_vital_effect"));
            assertEquals(currentTables, tables);
            assertEquals("", matcher.replaceAll("").trim());
        }
        String grants = stripComments(Files.readString(Path.of("database/grants/rule-source-installer.sql")));
        var matcher = Pattern.compile("GRANT (.*?) ON `dnd_tool_rules`[.]`(\\w+)` TO 'dnd_tool_rules_installer'@'127[.]0[.]0[.]1';", Pattern.DOTALL).matcher(grants);
        Map<String, String> writes = new LinkedHashMap<>();
        int reads = 0;
        while (matcher.find()) {
            String privilege = matcher.group(1).replaceAll("\\s+", " ").trim();
            if (privilege.equals("SELECT")) reads++;
            else assertNull(writes.put(matcher.group(2), privilege));
        }
        assertEquals(30, reads);
        Map<String, String> expectedWrites = new LinkedHashMap<>(Map.of(
                "rule_release", "INSERT (module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm), UPDATE (canonical_format_version, archive_format_version, hash_algorithm, content_sha256, installation_revision)",
                "rule_language", "INSERT, DELETE, UPDATE (display_name, description, category, source_page, sort_order)",
                "rule_tool", "SELECT, INSERT, DELETE",
                "rule_package_installation", "INSERT (release_id, installation_revision, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, installation_manifest_version, installation_manifest_sha256, package_display_name, verification_scope, observed_content_sha256)",
                "rule_package_installation_partition", "INSERT",
                "rule_installation_control", "UPDATE (metadata_row_count, row_version)",
                "rule_schema2_artifact", "INSERT (module_key, release_version, installation_sequence, source_operation_id, operation_fingerprint_version, operation_digest_sha256, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, raw_zip_byte_length, expanded_byte_length, original_zip)",
                "rule_schema2_control", "UPDATE (metadata_row_count, installation_count, raw_zip_byte_count, row_version)"));
        expectedWrites.put("rule_schema2_character_source", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, parent_race_module_key, parent_race_rule_key, author_note)");
        expectedWrites.put("rule_schema2_proficiency_grant", "INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, grant_category, grant_kind, grant_target_module_key, grant_target_rule_key, grant_count, author_note)");
        expectedWrites.put("rule_schema2_character_change", "INSERT (artifact_module_key, release_version, author_index, target_kind, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key, grant_category, grant_kind, grant_target_module_key, grant_target_rule_key, grant_count)");
        expectedWrites.put("rule_schema2_class_progression", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, hit_die_sides, proficiency_bonus_profile, supported_maximum_level, advancement_scope, multiclass_prerequisite_present, author_note)");
        expectedWrites.put("rule_schema2_resource", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, maximum_profile, recovery_profile, execution_mode, author_note)");
        expectedWrites.put("rule_schema2_advancement_change", "INSERT (artifact_module_key, release_version, author_index, target_kind, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key, growth_grant_kind, growth_grant_target_module_key, growth_grant_target_rule_type, growth_grant_target_rule_key, growth_grant_count, prerequisite_value)");
        expectedWrites.put("rule_schema2_subclass", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, selection_level, author_note)");
        expectedWrites.put("rule_schema2_feature", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_module_key, owner_rule_type, owner_rule_key, level, feature_kind, execution_mode, execution_algorithm, author_note)");
        expectedWrites.put("rule_schema2_feat", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, execution_mode, execution_algorithm, author_note)");
        expectedWrites.put("rule_schema2_proficiency", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, category, ability_key, author_note)");
        expectedWrites.put("rule_schema2_proficiency_set", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, category, minimum_enabled_members, author_note)");
        expectedWrites.put("rule_schema2_class_level", "INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, level, asi_available, author_note)");
        expectedWrites.put("rule_schema2_proficiency_member", "INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, proficiency_module_key, proficiency_rule_type, proficiency_rule_key, author_note)");
        expectedWrites.put("rule_schema2_growth_grant", "INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, growth_grant_kind, growth_grant_target_module_key, growth_grant_target_rule_type, growth_grant_target_rule_key, growth_grant_count, author_note)");
        expectedWrites.put("rule_schema2_ability_requirement", "INSERT (artifact_module_key, release_version, author_index, author_kind, group_index, alternative_index, ability_key, minimum_value)");
        expectedWrites.put("rule_schema2_rest", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, algorithm, author_note)");
        expectedWrites.put("rule_schema2_check", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, algorithm, author_note)");
        expectedWrites.put("rule_schema2_equipment_item", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, kind, armor_class, strength_minimum, stealth_disadvantage, hands, proficiency_module_key, proficiency_rule_type, proficiency_rule_key, stealth_skill_module_key, stealth_skill_rule_type, stealth_skill_rule_key, algorithm, author_note)");
        expectedWrites.put("rule_schema2_health", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, algorithm, author_note)");
        expectedWrites.put("rule_schema2_vital_effect", "INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, kind, dice_count, dice_sides, modifier, damage_type, algorithm, author_note)");
        expectedWrites.put("rule_schema2_execution_change", "INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key)");
        assertEquals(expectedWrites, writes);
        assertEquals("", matcher.replaceAll("").trim());
    }

    @Test
    void migratorGrantCannotMatchAnotherSchemaThroughUnderscoreWildcards() throws Exception {
        String grants = stripComments(Files.readString(Path.of("database/grants/rule-source-migrator.sql")))
                .replaceAll("\\s+", " ").trim();
        assertEquals("GRANT CREATE, CREATE TEMPORARY TABLES, ALTER, INDEX, REFERENCES, TRIGGER, SELECT, INSERT, UPDATE, DELETE "
                + "ON `dnd\\_tool\\_rules`.* TO 'dnd_tool_rules_migrator'@'127.0.0.1';", grants);
    }

    @Test
    void offlineVerificationScriptContainsOnlyReadStatementsWithoutLocks() throws Exception {
        String sql = stripComments(Files.readString(Path.of("database/verify/rule-source-schema.sql")));
        for (String statement : sql.split(";")) {
            String normalized = statement.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
            if (!normalized.isEmpty()) {
                assertTrue(normalized.startsWith("SELECT ") || normalized.equals("SHOW GRANTS"), normalized);
                for (String forbidden : List.of(" INTO ", " FOR UPDATE", " FOR SHARE", " LOCK ", "GET_LOCK(")) {
                    assertFalse(normalized.contains(forbidden), normalized);
                }
            }
        }
    }

    @Test
    void languageMatrixIsExactAndProtocolBoundsStaySeparateFromContentApproval() throws Exception {
        String sql = Files.readString(RuleSchemaMigrationsTest.SOURCE);
        String language = blocks(sql, "CREATE TABLE (\\w+) \\((.*?)\\) ENGINE=InnoDB").get("rule_language");
        Map<String, Set<String>> matrix = Map.of(
                "STANDARD", Set.of("common", "dwarvish", "elvish", "giant", "gnomish", "goblin", "halfling", "orc"),
                "EXOTIC", Set.of("abyssal", "celestial", "deep_speech", "draconic", "infernal", "primordial", "sylvan", "undercommon"),
                "SECRET", Set.of("druidic", "thieves_cant"));
        var groups = Pattern.compile("category = _binary'(\\w+)' AND language_key IN \\((.*?)\\)", Pattern.DOTALL).matcher(language);
        Map<String, Set<String>> actual = new LinkedHashMap<>();
        while (groups.find()) {
            var keys = Pattern.compile("_binary'language[.](\\w+)'").matcher(groups.group(2));
            Set<String> values = new java.util.HashSet<>();
            while (keys.find()) assertTrue(values.add(keys.group(1)));
            assertNull(actual.put(groups.group(1), values));
        }
        assertEquals(matrix, actual);
        assertTrue(sql.contains("metadata_row_count BETWEEN 1 AND 16384"));
        assertTrue(sql.contains("operation_fingerprint_version = 1"));
        assertTrue(sql.contains("OCTET_LENGTH(source_operation_id) = 16"));
        assertTrue(sql.contains("PRIMARY KEY (release_id, installation_revision, partition_key)"));
        assertTrue(sql.contains("verification_scope = _binary'PARTITION' AND observed_content_sha256 IS NULL"));
        assertTrue(sql.contains("verification_scope = _binary'COMPLETE' AND observed_content_sha256 IS NOT NULL"));
    }

    private static Map<String, String> blocks(String sql, String pattern) {
        Map<String, String> result = new LinkedHashMap<>();
        var matcher = Pattern.compile(pattern, Pattern.DOTALL).matcher(sql);
        while (matcher.find()) assertNull(result.put(matcher.group(1), matcher.group(2)));
        return result;
    }

    private static String stripComments(String sql) { return sql.replaceAll("(?m)--[^\\r\\n]*", ""); }
}
