package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V002ModuleSchemaTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V002__stage2_module_schema.sql";
    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE TABLE `([^`]+)`");
    private static final Pattern MODULE_INSERT =
            Pattern.compile("INSERT\\s+INTO\\s+`module_", Pattern.CASE_INSENSITIVE);
    private static final List<String> EXPECTED_TABLES = List.of(
            "module_rule_constant",
            "module_field_definition",
            "module_class_definition",
            "module_proficiency_tier",
            "module_proficiency_bonus_band",
            "module_skill_definition",
            "module_save_definition",
            "module_item_template",
            "module_entity_template",
            "module_entity_template_value",
            "module_entity_template_class_level",
            "module_entity_template_proficiency",
            "module_check_definition",
            "module_roll_mode",
            "module_event_template",
            "module_effect_definition",
            "module_event_check",
            "module_event_effect",
            "module_effect_parameter",
            "module_map_definition",
            "module_map_node",
            "module_map_connection");

    @Test
    void migrationCreatesTheCompleteEmptyModuleSchema() throws Exception {
        String sql = loadMigration();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        List<String> actualTables = new ArrayList<>();
        while (matcher.find()) {
            actualTables.add(matcher.group(1));
        }

        assertEquals(EXPECTED_TABLES, actualTables);
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS"));
        assertFalse(MODULE_INSERT.matcher(sql).find());
        assertTrue(sql.contains("'" + SchemaMigrations.V002_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains("'V002__stage2_module_schema.sql'"));
    }

    private static String loadMigration() throws IOException {
        try (InputStream input = V002ModuleSchemaTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
