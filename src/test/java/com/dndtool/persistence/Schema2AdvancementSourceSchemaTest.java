package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline structural checks; real MySQL constraint/privilege acceptance remains separate. */
class Schema2AdvancementSourceSchemaTest {
    @Test void forwardMigrationHasAnExactHistoryPreflightAndOnlyTypedOriginalMaterial() throws Exception {
        String sql = source();
        String preflight = sql.substring(0, sql.indexOf("CREATE TABLE rule_schema2_class_progression"));
        assertTrue(preflight.contains("CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'"));
        assertTrue(preflight.contains("(SELECT COUNT(*) FROM rule_schema_meta) = 4"));
        for (var previous : RuleSchemaMigrations.expectations().subList(0, 4)) {
            assertTrue(preflight.contains(previous.scriptName())); assertTrue(preflight.contains(previous.scriptSha256()));
        }
        var tables = blocks(sql, "CREATE TABLE (\\w+) \\((.*?)\\) ENGINE=InnoDB");
        assertEquals(Set.of("rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change", "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set", "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant", "rule_schema2_ability_requirement", "rule_schema2_rest"), tables.keySet());
        for (var table : tables.values()) {
            assertTrue(table.contains("PRIMARY KEY (artifact_module_key, release_version, author_index"));
            assertTrue(table.contains("REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT"));
        }
        assertTrue(tables.get("rule_schema2_class_progression").contains("supported_maximum_level BETWEEN 1 AND 20"));
        assertTrue(tables.get("rule_schema2_class_progression").contains("STATISTICS_AND_RESOURCES_ONLY"));
        assertTrue(tables.get("rule_schema2_resource").contains("execution_mode IN (_binary'AUTOMATIC', _binary'BLOCKED')"));
        assertTrue(tables.get("rule_schema2_resource").contains("recovery_profile VARCHAR(200) NOT NULL"));
        assertTrue(tables.get("rule_schema2_resource").contains("CHAR_LENGTH(recovery_profile) BETWEEN 1 AND 200"));
        for (var prefix : Map.of("rule_schema2_class_progression", "progression", "rule_schema2_resource", "resource").entrySet())
            assertTrue(tables.get(prefix.getKey()).contains("'^" + prefix.getValue() + "[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$'"));
        assertTrue(tables.get("rule_schema2_class_progression").contains("multiclass_prerequisite_present = 1"));
        assertTrue(tables.get("rule_schema2_ability_requirement").contains("group_index BETWEEN 0 AND 5 AND alternative_index BETWEEN 0 AND 1"));
        assertTrue(tables.get("rule_schema2_ability_requirement").contains("author_index, author_kind, ability_key"));
        assertTrue(tables.get("rule_schema2_class_level").contains("asi_available IN (0, 1)"));
        assertTrue(tables.get("rule_schema2_growth_grant").contains("multiclass_proficiencies"));
        assertTrue(tables.get("rule_schema2_rest").contains("source_page = 87 AND algorithm = _binary'SRD_2014_REST_V1'"));
        String changes = tables.get("rule_schema2_advancement_change");
        assertTrue(changes.contains("field_key <> _binary'recovery_profile' OR CHAR_LENGTH(text_value) <= 200"));
        assertTrue(changes.contains("rule_type = _binary'character.class_progression' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^progression[.]"));
        assertTrue(changes.contains("rule_type = _binary'character.resource' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^resource[.]"));
        assertTrue(changes.contains("reference_rule_type = _binary'character.class'"));
        assertTrue(changes.contains("operation = _binary'clear_field' AND field_key = _binary'author_note'"));
        assertTrue(changes.contains("((text_value IS NOT NULL) + (integer_value IS NOT NULL) + (boolean_value IS NOT NULL) + (reference_module_key IS NOT NULL) + (growth_grant_kind IS NOT NULL) + (prerequisite_value IS NOT NULL)) = 1"));
        for (String forbidden : List.of("ALTER TABLE", " JSON", "BLOB", "AUTO_INCREMENT", "CASCADE", "dnd_tool_se", "CREATE USER", "GRANT ", "IF NOT EXISTS"))
            assertFalse(sql.contains(forbidden), forbidden);
    }
    @Test void onlyCurrentInstallationMayInsertAndEveryNewMaterialRowIsPermanent() throws Exception {
        var triggers = blocks(source(), "CREATE TRIGGER (\\w+) (.*?)END\\$\\$");
        assertEquals(39, triggers.size());
        for (String kind : List.of("progression", "resource", "advancement", "subclass", "feature", "feat", "proficiency", "proficiency_set", "class_level", "proficiency_member", "growth_grant", "ability_requirement", "rest")) {
            String insert = triggers.get("rule_s2_" + kind + "_insert");
            assertTrue(insert.contains("FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE"));
            assertTrue(insert.contains("installation_sequence = current_count + 1"));
            for (String op : List.of("update", "delete")) {
                String body = triggers.get("rule_s2_" + kind + "_" + op);
                assertTrue(body.contains("SIGNAL SQLSTATE '45000'")); assertFalse(body.contains("IF "));
            }
        }
    }
    private static String source() throws Exception { return Files.readString(Path.of("database/rules/migration/V005__schema2-advancement-source.sql")); }
    private static Map<String, String> blocks(String sql, String regex) {
        Map<String, String> result = new LinkedHashMap<>(); var matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(sql);
        while (matcher.find()) assertNull(result.put(matcher.group(1), matcher.group(2))); return result;
    }
}
