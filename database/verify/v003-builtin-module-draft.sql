-- Local 5E Campaign Tool V003 post-migration read-only verification
--
-- Run every statement with dnd_tool_se_agent@127.0.0.1 after the migrator
-- finishes V003__builtin_module_draft.sql. This file contains SELECT only and
-- is safe for the repository's dedicated read-only Agent connection.

-- Expect exactly V001, V002, and V003 in this order. The V003 digest must be
-- 2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225.
SELECT
    `schema_version`,
    `script_name`,
    `script_sha256`,
    `description`
FROM `schema_meta`
ORDER BY `schema_version`;

-- Expect one DRAFT release. Hash and release time must remain NULL because the
-- canonical encoder and independent expected content digest are not yet done.
SELECT
    `module_key`,
    `release_version`,
    `canonical_format_version`,
    `hash_algorithm`,
    `content_sha256`,
    `release_status`,
    `released_at`
FROM `module_release`;

-- Expect every actual_count to equal expected_count. The class-level partition
-- is intentionally empty; the three level-zero NPC templates have no classes.
SELECT 'module_release' AS `table_name`, 1 AS `expected_count`, COUNT(*) AS `actual_count`
FROM `module_release`
UNION ALL SELECT 'module_rule_constant', 25, COUNT(*) FROM `module_rule_constant`
UNION ALL SELECT 'module_field_definition', 10, COUNT(*) FROM `module_field_definition`
UNION ALL SELECT 'module_class_definition', 12, COUNT(*) FROM `module_class_definition`
UNION ALL SELECT 'module_proficiency_tier', 4, COUNT(*) FROM `module_proficiency_tier`
UNION ALL SELECT 'module_proficiency_bonus_band', 5, COUNT(*) FROM `module_proficiency_bonus_band`
UNION ALL SELECT 'module_skill_definition', 18, COUNT(*) FROM `module_skill_definition`
UNION ALL SELECT 'module_save_definition', 6, COUNT(*) FROM `module_save_definition`
UNION ALL SELECT 'module_item_template', 3, COUNT(*) FROM `module_item_template`
UNION ALL SELECT 'module_entity_template', 3, COUNT(*) FROM `module_entity_template`
UNION ALL SELECT 'module_entity_template_value', 30, COUNT(*) FROM `module_entity_template_value`
UNION ALL SELECT 'module_entity_template_class_level', 0, COUNT(*) FROM `module_entity_template_class_level`
UNION ALL SELECT 'module_entity_template_proficiency', 72, COUNT(*) FROM `module_entity_template_proficiency`
UNION ALL SELECT 'module_check_definition', 4, COUNT(*) FROM `module_check_definition`
UNION ALL SELECT 'module_roll_mode', 3, COUNT(*) FROM `module_roll_mode`
UNION ALL SELECT 'module_event_template', 4, COUNT(*) FROM `module_event_template`
UNION ALL SELECT 'module_effect_definition', 5, COUNT(*) FROM `module_effect_definition`
UNION ALL SELECT 'module_event_check', 3, COUNT(*) FROM `module_event_check`
UNION ALL SELECT 'module_event_effect', 16, COUNT(*) FROM `module_event_effect`
UNION ALL SELECT 'module_effect_parameter', 13, COUNT(*) FROM `module_effect_parameter`
UNION ALL SELECT 'module_map_definition', 1, COUNT(*) FROM `module_map_definition`
UNION ALL SELECT 'module_map_node', 4, COUNT(*) FROM `module_map_node`
UNION ALL SELECT 'module_map_connection', 3, COUNT(*) FROM `module_map_connection`;

-- Expect no rows. These two queries make count drift easy to spot without
-- relying on manual comparison of every line above.
SELECT `table_name`, `expected_count`, `actual_count`
FROM (
    SELECT 'module_release' AS `table_name`, 1 AS `expected_count`, COUNT(*) AS `actual_count`
    FROM `module_release`
    UNION ALL SELECT 'module_rule_constant', 25, COUNT(*) FROM `module_rule_constant`
    UNION ALL SELECT 'module_field_definition', 10, COUNT(*) FROM `module_field_definition`
    UNION ALL SELECT 'module_class_definition', 12, COUNT(*) FROM `module_class_definition`
    UNION ALL SELECT 'module_proficiency_tier', 4, COUNT(*) FROM `module_proficiency_tier`
    UNION ALL SELECT 'module_proficiency_bonus_band', 5, COUNT(*) FROM `module_proficiency_bonus_band`
    UNION ALL SELECT 'module_skill_definition', 18, COUNT(*) FROM `module_skill_definition`
    UNION ALL SELECT 'module_save_definition', 6, COUNT(*) FROM `module_save_definition`
    UNION ALL SELECT 'module_item_template', 3, COUNT(*) FROM `module_item_template`
    UNION ALL SELECT 'module_entity_template', 3, COUNT(*) FROM `module_entity_template`
    UNION ALL SELECT 'module_entity_template_value', 30, COUNT(*) FROM `module_entity_template_value`
    UNION ALL SELECT 'module_entity_template_class_level', 0, COUNT(*) FROM `module_entity_template_class_level`
    UNION ALL SELECT 'module_entity_template_proficiency', 72, COUNT(*) FROM `module_entity_template_proficiency`
    UNION ALL SELECT 'module_check_definition', 4, COUNT(*) FROM `module_check_definition`
    UNION ALL SELECT 'module_roll_mode', 3, COUNT(*) FROM `module_roll_mode`
    UNION ALL SELECT 'module_event_template', 4, COUNT(*) FROM `module_event_template`
    UNION ALL SELECT 'module_effect_definition', 5, COUNT(*) FROM `module_effect_definition`
    UNION ALL SELECT 'module_event_check', 3, COUNT(*) FROM `module_event_check`
    UNION ALL SELECT 'module_event_effect', 16, COUNT(*) FROM `module_event_effect`
    UNION ALL SELECT 'module_effect_parameter', 13, COUNT(*) FROM `module_effect_parameter`
    UNION ALL SELECT 'module_map_definition', 1, COUNT(*) FROM `module_map_definition`
    UNION ALL SELECT 'module_map_node', 4, COUNT(*) FROM `module_map_node`
    UNION ALL SELECT 'module_map_connection', 3, COUNT(*) FROM `module_map_connection`
) AS `counts`
WHERE `actual_count` <> `expected_count`;

SELECT 'invalid_release_state' AS `problem`
FROM `module_release`
WHERE `module_key` <> 'dnd5e2014_srd51_se_v1'
   OR `release_version` <> '1'
   OR `canonical_format_version` <> 1
   OR `hash_algorithm` <> 'SHA-256'
   OR `content_sha256` IS NOT NULL
   OR `release_status` <> 'DRAFT'
   OR `released_at` IS NOT NULL;

-- Expect three rows, each with 10 field values, 18 skill defaults, six save
-- defaults, zero class levels, and 24 total NONE proficiency rows.
SELECT
    template.`template_key`,
    COUNT(DISTINCT value_row.`field_key`) AS `field_value_count`,
    COUNT(DISTINCT CASE WHEN proficiency.`target_kind` = 'SKILL'
        THEN proficiency.`target_key` END) AS `skill_proficiency_count`,
    COUNT(DISTINCT CASE WHEN proficiency.`target_kind` = 'SAVING_THROW'
        THEN proficiency.`target_key` END) AS `save_proficiency_count`,
    COUNT(DISTINCT class_level.`class_key`) AS `class_level_count`,
    COUNT(DISTINCT CASE WHEN proficiency.`proficiency_key` = 'proficiency.none'
        THEN CONCAT(proficiency.`target_kind`, ':', proficiency.`target_key`) END)
        AS `none_proficiency_count`
FROM `module_entity_template` AS template
LEFT JOIN `module_entity_template_value` AS value_row
    ON value_row.`module_release_id` = template.`module_release_id`
   AND value_row.`template_key` = template.`template_key`
LEFT JOIN `module_entity_template_proficiency` AS proficiency
    ON proficiency.`module_release_id` = template.`module_release_id`
   AND proficiency.`template_key` = template.`template_key`
LEFT JOIN `module_entity_template_class_level` AS class_level
    ON class_level.`module_release_id` = template.`module_release_id`
   AND class_level.`template_key` = template.`template_key`
GROUP BY template.`template_key`
ORDER BY template.`template_key`;

-- Expect exactly the documented event/check cardinalities: note has no check
-- and one effect; every check event has one matching check and all five effects.
SELECT
    event_template.`event_key`,
    COUNT(DISTINCT event_check.`check_key`) AS `check_count`,
    COUNT(DISTINCT event_effect.`effect_key`) AS `effect_count`
FROM `module_event_template` AS event_template
LEFT JOIN `module_event_check` AS event_check
    ON event_check.`module_release_id` = event_template.`module_release_id`
   AND event_check.`event_key` = event_template.`event_key`
LEFT JOIN `module_event_effect` AS event_effect
    ON event_effect.`module_release_id` = event_template.`module_release_id`
   AND event_effect.`event_key` = event_template.`event_key`
GROUP BY event_template.`event_key`
ORDER BY event_template.`event_key`;

-- Expect three canonical connections and endpoint_order_valid=1 for every row.
SELECT
    `map_key`,
    `endpoint_low_key`,
    `endpoint_high_key`,
    (`endpoint_low_key` < `endpoint_high_key`) AS `endpoint_order_valid`
FROM `module_map_connection`
ORDER BY `map_key`, `endpoint_low_key`, `endpoint_high_key`;

-- V003 installs definitions only. All runtime tables must remain empty.
SELECT 'campaign' AS `table_name`, COUNT(*) AS `row_count` FROM `campaign`
UNION ALL SELECT 'campaign_module', COUNT(*) FROM `campaign_module`
UNION ALL SELECT 'host_operation', COUNT(*) FROM `host_operation`;
