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

/** Guards the reviewed contents and fail-closed behavior of the V003 seed migration. */
class V003BuiltinModuleDraftTest {
    private static final String RESOURCE_PATH =
            "/db/migration/V003__builtin_module_draft.sql";
    private static final Pattern INSERT_TARGET =
            Pattern.compile("INSERT\\s+INTO\\s+`([^`]+)`", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSAFE_IDEMPOTENT_STATEMENT = Pattern.compile(
            "(?im)^\\s*(?:INSERT\\s+IGNORE|REPLACE\\s+INTO|UPDATE\\s+|DELETE\\s+|"
                    + "CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS|ALTER\\s+TABLE\\s+)");

    @Test
    void migrationInstallsOneCompleteDraftWithoutPublishingOrCreatingCampaigns()
            throws Exception {
        String sql = loadMigration();

        assertTrue(sql.contains("'dnd5e2014_srd51_se_v1'"));
        assertTrue(sql.contains("'DRAFT'"));
        assertTrue(sql.contains("content_sha256 remains NULL"));
        assertFalse(sql.contains("'RELEASED'"));
        assertFalse(sql.contains("INSERT INTO `campaign`"));
        assertFalse(sql.contains("INSERT INTO `campaign_module`"));

        assertFalse(UNSAFE_IDEMPOTENT_STATEMENT.matcher(sql).find());
        assertFalse(sql.contains("ON DUPLICATE KEY"));
        assertTrue(sql.contains("'" + SchemaMigrations.V003_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains("'V003__builtin_module_draft.sql'"));
    }

    @Test
    void migrationPopulatesEveryCanonicalPartitionExceptTheIntentionallyEmptyClassLevels()
            throws Exception {
        String sql = loadMigration();

        assertEquals(List.of(
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
                "module_entity_template_proficiency",
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
                "module_map_connection",
                "schema_meta"), insertTargets(sql));

        assertFalse(sql.contains("INSERT INTO `module_entity_template_class_level`"));
        assertTrue(sql.contains("template.`module_release_id`, template.`template_key`, 'SKILL'"));
        assertTrue(sql.contains("template.`module_release_id`, template.`template_key`, 'SAVING_THROW'"));
        assertEquals(3, countOccurrences(sql, "'proficiency.none'"));
    }

    @Test
    void migrationContainsTheRequiredStableDefinitionKeysAndExpectedRowCounts()
            throws Exception {
        String sql = loadMigration();

        assertTupleCount(sql, "module_rule_constant", 25);
        assertTupleCount(sql, "module_field_definition", 10);
        assertTupleCount(sql, "module_class_definition", 12);
        assertTupleCount(sql, "module_proficiency_tier", 4);
        assertTupleCount(sql, "module_proficiency_bonus_band", 5);
        assertTupleCount(sql, "module_skill_definition", 18);
        assertTupleCount(sql, "module_save_definition", 6);
        assertTupleCount(sql, "module_item_template", 3);
        assertTupleCount(sql, "module_entity_template", 3);
        assertTupleCount(sql, "module_entity_template_value", 30);
        assertTupleCount(sql, "module_check_definition", 4);
        assertTupleCount(sql, "module_roll_mode", 3);
        assertTupleCount(sql, "module_event_template", 4);
        assertTupleCount(sql, "module_effect_definition", 5);
        assertTupleCount(sql, "module_event_check", 3);
        assertTupleCount(sql, "module_event_effect", 16);
        assertTupleCount(sql, "module_effect_parameter", 13);
        assertTupleCount(sql, "module_map_definition", 1);
        assertTupleCount(sql, "module_map_node", 4);
        assertTupleCount(sql, "module_map_connection", 3);

        for (String stableKey : requiredStableKeys()) {
            assertTrue(sql.contains("'" + stableKey + "'"), stableKey);
        }
    }

    @Test
    void coreCharacterFieldsHaveReviewedIntegerDefaultsBoundsAndSingleGroundSpeed()
            throws Exception {
        String sql = loadMigration();
        String compact = compactInsertBlock(sql, "module_field_definition");

        assertTrue(compact.contains(fieldTuple(
                "ability.strength", "力量", "10", "1", "30", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "ability.dexterity", "敏捷", "10", "1", "30", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "ability.constitution", "体质", "10", "1", "30", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "ability.intelligence", "智力", "10", "1", "30", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "ability.wisdom", "感知", "10", "1", "30", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "ability.charisma", "魅力", "10", "1", "30", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "hp.maximum", "最大 HP", "1", "1", "999", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "hp.current", "当前 HP", "1", "0", "NULL",
                "'hp.maximum'", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "armor_class", "AC", "10", "0", "99", "NULL", "NULL")));
        assertTrue(compact.contains(fieldTuple(
                "speed.ground", "地面速度", "30", "0", "999", "NULL", "'英尺'")));

        assertEquals(1, countOccurrences(compact, "'speed.ground'"));
        assertFalse(Pattern.compile("'speed\\.(?!ground')[a-z_]+'").matcher(compact).find());
    }

    @Test
    void derivedFormulasAreImmutableRuleMetadataNotEditableCharacterFields()
            throws Exception {
        String sql = loadMigration();
        String fields = compactInsertBlock(sql, "module_field_definition");
        String constants = compactInsertBlock(sql, "module_rule_constant");

        for (String forbiddenFieldKey : List.of(
                "formula.ability_modifier",
                "formula.total_level",
                "formula.proficiency_contribution",
                "formula.skill_bonus",
                "formula.saving_throw_bonus")) {
            assertFalse(fields.contains("'" + forbiddenFieldKey + "'"), forbiddenFieldKey);
            assertTrue(constants.contains("'" + forbiddenFieldKey + "'"), forbiddenFieldKey);
        }
        assertEquals(10, countOccurrences(fields, "(@module_release_id"));
    }

    @Test
    void skillsAndSavingThrowsHaveTheReviewed2014AbilityMappings() throws Exception {
        String sql = loadMigration();
        String skills = compactInsertBlock(sql, "module_skill_definition");
        String saves = compactInsertBlock(sql, "module_save_definition");

        // Skill names and governing abilities are part of the published rules contract.
        String[][] expectedSkills = {
            {"skill.acrobatics", "体操", "ability.dexterity"},
            {"skill.animal_handling", "驯兽", "ability.wisdom"},
            {"skill.arcana", "奥秘", "ability.intelligence"},
            {"skill.athletics", "运动", "ability.strength"},
            {"skill.deception", "欺瞒", "ability.charisma"},
            {"skill.history", "历史", "ability.intelligence"},
            {"skill.insight", "洞悉", "ability.wisdom"},
            {"skill.intimidation", "威吓", "ability.charisma"},
            {"skill.investigation", "调查", "ability.intelligence"},
            {"skill.medicine", "医药", "ability.wisdom"},
            {"skill.nature", "自然", "ability.intelligence"},
            {"skill.perception", "察觉", "ability.wisdom"},
            {"skill.performance", "表演", "ability.charisma"},
            {"skill.persuasion", "游说", "ability.charisma"},
            {"skill.religion", "宗教", "ability.intelligence"},
            {"skill.sleight_of_hand", "巧手", "ability.dexterity"},
            {"skill.stealth", "隐匿", "ability.dexterity"},
            {"skill.survival", "求生", "ability.wisdom"}
        };
        for (String[] expected : expectedSkills) {
            String tuple = "(@module_release_id, '" + expected[0] + "', '"
                    + expected[1] + "', '" + expected[2] + "')";
            assertTrue(skills.contains(tuple), expected[0]);
        }

        String[][] expectedSaves = {
            {"save.strength", "ability.strength"},
            {"save.dexterity", "ability.dexterity"},
            {"save.constitution", "ability.constitution"},
            {"save.intelligence", "ability.intelligence"},
            {"save.wisdom", "ability.wisdom"},
            {"save.charisma", "ability.charisma"}
        };
        for (String[] expected : expectedSaves) {
            String tuple = "(@module_release_id, '" + expected[0] + "', '"
                    + expected[1] + "')";
            assertTrue(saves.contains(tuple), expected[0]);
        }

        // Exact counts reject an added, duplicated, or obsolete definition.
        assertEquals(18, countOccurrences(skills, "(@module_release_id"));
        assertEquals(6, countOccurrences(saves, "(@module_release_id"));
    }

    @Test
    void classesAndProficiencyTiersHaveTheReviewed2014Definitions() throws Exception {
        String sql = loadMigration();
        String classes = compactInsertBlock(sql, "module_class_definition");
        String proficiencyTiers = compactInsertBlock(sql, "module_proficiency_tier");

        String[][] expectedClasses = {
            {"class.barbarian", "野蛮人"},
            {"class.bard", "吟游诗人"},
            {"class.cleric", "牧师"},
            {"class.druid", "德鲁伊"},
            {"class.fighter", "战士"},
            {"class.monk", "武僧"},
            {"class.paladin", "圣武士"},
            {"class.ranger", "游侠"},
            {"class.rogue", "游荡者"},
            {"class.sorcerer", "术士"},
            {"class.warlock", "邪术师"},
            {"class.wizard", "法师"}
        };
        for (String[] expected : expectedClasses) {
            String tuple = "(@module_release_id, '" + expected[0] + "', '"
                    + expected[1] + "')";
            assertTrue(classes.contains(tuple), expected[0]);
        }

        // Fractions are stored exactly; HALF alone rounds its contribution down.
        String[][] expectedTiers = {
            {"proficiency.none", "NONE", "0", "1", "EXACT"},
            {"proficiency.half", "HALF", "1", "2", "FLOOR"},
            {"proficiency.full", "FULL", "1", "1", "EXACT"},
            {"proficiency.expertise", "EXPERTISE", "2", "1", "EXACT"}
        };
        for (String[] expected : expectedTiers) {
            String tuple = "(@module_release_id, '" + expected[0] + "', '"
                    + expected[1] + "', " + expected[2] + ", " + expected[3]
                    + ", '" + expected[4] + "')";
            assertTrue(proficiencyTiers.contains(tuple), expected[0]);
        }

        assertEquals(12, countOccurrences(classes, "(@module_release_id"));
        assertEquals(4, countOccurrences(proficiencyTiers, "(@module_release_id"));
    }

    @Test
    void simpleItemTemplatesContainOnlyTheThreeReviewedNameDescriptionPairs()
            throws Exception {
        String items = compactInsertBlock(loadMigration(), "module_item_template");

        assertTrue(items.contains(
                "(@module_release_id, 'item.backpack', '背包', '用于携带物品的普通背包')"));
        assertTrue(items.contains(
                "(@module_release_id, 'item.rope_hempen_50ft', '50 英尺麻绳', '普通麻绳')"));
        assertTrue(items.contains(
                "(@module_release_id, 'item.torch', '火把', '普通照明用火把')"));
        assertEquals(3, countOccurrences(items, "(@module_release_id"));
    }

    /** Returns one INSERT statement with formatting differences removed. */
    private static String compactInsertBlock(String sql, String table) {
        int start = sql.indexOf("INSERT INTO `" + table + "`");
        int end = sql.indexOf(';', start);
        assertTrue(start >= 0 && end > start, table);
        return sql.substring(start, end).replaceAll("\\s+", " ");
    }

    /** Builds the invariant portion of one INTEGER field tuple, excluding its description. */
    private static String fieldTuple(
            String key,
            String name,
            String defaultValue,
            String minimum,
            String maximum,
            String dependentMaximum,
            String unit) {
        return "(@module_release_id, '" + key + "', '" + name + "', 'INTEGER', "
                + "NULL, " + defaultValue + ", NULL, NULL, "
                + minimum + ", " + maximum + ", NULL, NULL, "
                + dependentMaximum + ", " + unit + ",";
    }

    private static List<String> insertTargets(String sql) {
        Matcher matcher = INSERT_TARGET.matcher(sql);
        List<String> targets = new ArrayList<>();
        while (matcher.find()) {
            targets.add(matcher.group(1));
        }
        return targets;
    }

    /** Counts direct VALUES tuples; SELECT-based explicit proficiency rows are tested separately. */
    private static void assertTupleCount(String sql, String table, int expected) {
        String marker = "INSERT INTO `" + table + "`";
        int start = sql.indexOf(marker);
        assertTrue(start >= 0, table);
        int end = sql.indexOf(';', start);
        assertTrue(end > start, table);
        assertEquals(expected, countOccurrences(sql.substring(start, end), "(@module_release_id"), table);
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

    private static List<String> requiredStableKeys() {
        return List.of(
                "ability.strength", "ability.dexterity", "ability.constitution",
                "ability.intelligence", "ability.wisdom", "ability.charisma",
                "hp.maximum", "hp.current", "armor_class", "speed.ground",
                "class.barbarian", "class.bard", "class.cleric", "class.druid",
                "class.fighter", "class.monk", "class.paladin", "class.ranger",
                "class.rogue", "class.sorcerer", "class.warlock", "class.wizard",
                "skill.acrobatics", "skill.animal_handling", "skill.arcana",
                "skill.athletics", "skill.deception", "skill.history", "skill.insight",
                "skill.intimidation", "skill.investigation", "skill.medicine",
                "skill.nature", "skill.perception", "skill.performance",
                "skill.persuasion", "skill.religion", "skill.sleight_of_hand",
                "skill.stealth", "skill.survival",
                "save.strength", "save.dexterity", "save.constitution",
                "save.intelligence", "save.wisdom", "save.charisma",
                "item.backpack", "item.rope_hempen_50ft", "item.torch",
                "npc.commoner", "npc.guard", "npc.wolf",
                "check.ability", "check.skill", "check.saving_throw", "check.manual",
                "roll.normal", "roll.advantage", "roll.disadvantage",
                "event.note", "event.ability_check", "event.skill_check",
                "event.saving_throw", "effect.adjust_current_hp",
                "effect.grant_module_item", "effect.grant_temporary_item",
                "effect.set_entity_position", "effect.append_event_message",
                "map.tavern_cellar", "node.street", "node.entry",
                "node.common_room", "node.cellar");
    }

    private static String loadMigration() throws IOException {
        try (InputStream input =
                V003BuiltinModuleDraftTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
