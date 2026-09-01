package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.module.BuiltinModuleHashManifest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the one-way V004 publication and every database immutable boundary. */
class V004BuiltinModuleReleaseTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V004__release_builtin_module.sql";
    private static final Pattern TRIGGER = Pattern.compile(
            "CREATE TRIGGER `([^`]+)`\\s+BEFORE (INSERT|UPDATE|DELETE) ON `([^`]+)`",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> PROTECTED_TABLES = List.of(
            "module_release",
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
    void protectsReleaseAndEveryDefinitionTableAgainstAllThreeDmlKinds()
            throws Exception {
        String sql = loadMigration();
        Matcher matcher = TRIGGER.matcher(sql);
        Map<String, Set<String>> eventsByTable = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
            eventsByTable.computeIfAbsent(
                    matcher.group(3), ignored -> new LinkedHashSet<>()).add(matcher.group(2));
        }

        assertEquals(69, names.size());
        assertEquals(69, new LinkedHashSet<>(names).size());
        assertEquals(new LinkedHashSet<>(PROTECTED_TABLES), eventsByTable.keySet());
        for (String table : PROTECTED_TABLES) {
            assertEquals(Set.of("INSERT", "UPDATE", "DELETE"), eventsByTable.get(table), table);
        }

        assertEquals(66, countOccurrences(sql, "released module definition is immutable"));
        assertTrue(sql.contains("OLD.`release_status` = 'RELEASED'"));
        assertTrue(sql.contains("module release must be inserted as an unhashed DRAFT"));
        assertFalse(sql.contains("DROP TRIGGER"));
        assertFalse(sql.contains("IF NOT EXISTS"));
    }

    @Test
    void publishesOnlyTheExactReviewedDraftAndFailsClosedUnlessOneRowChanged()
            throws Exception {
        String sql = loadMigration();
        String expectedDigest = BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;

        assertTrue(sql.contains("SET `content_sha256` = '" + expectedDigest + "'"));
        assertTrue(sql.contains("`release_status` = 'RELEASED'"));
        assertTrue(sql.contains("`module_key` = 'dnd5e2014_srd51_se_v1'"));
        assertTrue(sql.contains("`release_version` = '1'"));
        assertTrue(sql.contains("`canonical_format_version` = 1"));
        assertTrue(sql.contains("`hash_algorithm` = 'SHA-256'"));
        assertTrue(sql.contains("`content_sha256` IS NULL"));
        assertTrue(sql.contains("`release_status` = 'DRAFT'"));
        assertTrue(sql.contains("SET @published_row_count = ROW_COUNT()"));
        assertTrue(sql.contains("@published_row_count = 1"));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM `module_release`) = 1"));
        assertTrue(sql.contains("START TRANSACTION"));
        assertTrue(sql.contains("COMMIT"));
        assertFalse(sql.contains("INSERT IGNORE"));
        assertFalse(sql.contains("ON DUPLICATE KEY"));
    }

    @Test
    void recordsTheApprovedV004MigrationIdentityAndChecksum() throws Exception {
        String sql = loadMigration();

        assertTrue(sql.contains("'V004__release_builtin_module.sql'"));
        assertTrue(sql.contains("'" + SchemaMigrations.V004_APPROVED_SHA256 + "'"));
        assertEquals(
                SchemaMigrations.V004_APPROVED_SHA256,
                SchemaMigrations.canonicalPayloadSha256(sql));
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String loadMigration() throws IOException {
        try (InputStream input =
                V004BuiltinModuleReleaseTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
