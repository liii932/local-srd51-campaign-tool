-- Local 5E Campaign Tool V002 post-migration verification
--
-- Run this read-only file in an authenticated SQL client against the intended schema
-- after V002__stage2_module_schema.sql finishes without error. Save the full
-- result as evidence. This file does not modify schema, privileges or data.

USE `dnd_tool_se`;

-- Expect exactly V001 and V002. The V002 digest must be the value below.
SELECT
    `schema_version`,
    `script_name`,
    `script_sha256`,
    `description`,
    `applied_at`
FROM `schema_meta`
ORDER BY `schema_version`;

-- Expect 23 rows: module_release from V001 plus 22 empty V002 definition tables.
SELECT
    `table_name`,
    `engine`,
    `table_collation`
FROM `information_schema`.`tables`
WHERE `table_schema` = DATABASE()
  AND LEFT(`table_name`, 7) = 'module_'
ORDER BY `table_name`;

SELECT COUNT(*) AS `module_table_count`
FROM `information_schema`.`tables`
WHERE `table_schema` = DATABASE()
  AND LEFT(`table_name`, 7) = 'module_';

-- Expect every row_count to be zero. V002 installs structure, not rule data.
SELECT 'module_release' AS `table_name`, COUNT(*) AS `row_count` FROM `module_release`
UNION ALL SELECT 'module_rule_constant', COUNT(*) FROM `module_rule_constant`
UNION ALL SELECT 'module_field_definition', COUNT(*) FROM `module_field_definition`
UNION ALL SELECT 'module_class_definition', COUNT(*) FROM `module_class_definition`
UNION ALL SELECT 'module_proficiency_tier', COUNT(*) FROM `module_proficiency_tier`
UNION ALL SELECT 'module_proficiency_bonus_band', COUNT(*) FROM `module_proficiency_bonus_band`
UNION ALL SELECT 'module_skill_definition', COUNT(*) FROM `module_skill_definition`
UNION ALL SELECT 'module_save_definition', COUNT(*) FROM `module_save_definition`
UNION ALL SELECT 'module_item_template', COUNT(*) FROM `module_item_template`
UNION ALL SELECT 'module_entity_template', COUNT(*) FROM `module_entity_template`
UNION ALL SELECT 'module_entity_template_value', COUNT(*) FROM `module_entity_template_value`
UNION ALL SELECT 'module_entity_template_class_level', COUNT(*) FROM `module_entity_template_class_level`
UNION ALL SELECT 'module_entity_template_proficiency', COUNT(*) FROM `module_entity_template_proficiency`
UNION ALL SELECT 'module_check_definition', COUNT(*) FROM `module_check_definition`
UNION ALL SELECT 'module_roll_mode', COUNT(*) FROM `module_roll_mode`
UNION ALL SELECT 'module_event_template', COUNT(*) FROM `module_event_template`
UNION ALL SELECT 'module_effect_definition', COUNT(*) FROM `module_effect_definition`
UNION ALL SELECT 'module_event_check', COUNT(*) FROM `module_event_check`
UNION ALL SELECT 'module_event_effect', COUNT(*) FROM `module_event_effect`
UNION ALL SELECT 'module_effect_parameter', COUNT(*) FROM `module_effect_parameter`
UNION ALL SELECT 'module_map_definition', COUNT(*) FROM `module_map_definition`
UNION ALL SELECT 'module_map_node', COUNT(*) FROM `module_map_node`
UNION ALL SELECT 'module_map_connection', COUNT(*) FROM `module_map_connection`
ORDER BY `table_name`;

-- These V001 runtime tables must also remain empty before campaign work begins.
SELECT 'campaign' AS `table_name`, COUNT(*) AS `row_count` FROM `campaign`
UNION ALL SELECT 'campaign_module', COUNT(*) FROM `campaign_module`
UNION ALL SELECT 'host_operation', COUNT(*) FROM `host_operation`
ORDER BY `table_name`;

-- Review all V002 foreign keys. Every business reference must remain inside
-- the same module_release_id and use RESTRICT for updates and deletes.
SELECT
    `table_name`,
    `constraint_name`,
    `referenced_table_name`,
    `update_rule`,
    `delete_rule`
FROM `information_schema`.`referential_constraints`
WHERE `constraint_schema` = DATABASE()
  AND LEFT(`table_name`, 7) = 'module_'
ORDER BY `table_name`, `constraint_name`;

-- Review primary/unique indexes used by stable-key and relationship constraints.
SELECT
    `table_name`,
    `index_name`,
    `non_unique`,
    GROUP_CONCAT(`column_name` ORDER BY `seq_in_index` SEPARATOR ',') AS `columns`
FROM `information_schema`.`statistics`
WHERE `table_schema` = DATABASE()
  AND LEFT(`table_name`, 7) = 'module_'
GROUP BY `table_name`, `index_name`, `non_unique`
ORDER BY `table_name`, `index_name`;

-- Review enforced CHECK constraints, including typed-value and range rules.
SELECT
    `tc`.`table_name`,
    `tc`.`constraint_name`,
    `cc`.`check_clause`,
    `tc`.`enforced`
FROM `information_schema`.`table_constraints` AS `tc`
JOIN `information_schema`.`check_constraints` AS `cc`
  ON `cc`.`constraint_schema` = `tc`.`constraint_schema`
 AND `cc`.`constraint_name` = `tc`.`constraint_name`
WHERE `tc`.`constraint_schema` = DATABASE()
  AND `tc`.`constraint_type` = 'CHECK'
  AND LEFT(`tc`.`table_name`, 7) = 'module_'
ORDER BY `tc`.`table_name`, `tc`.`constraint_name`;

-- Expected V002 schema_meta identity:
-- version: 2
-- script:  V002__stage2_module_schema.sql
-- SHA-256: 55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8
