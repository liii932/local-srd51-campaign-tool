-- V004 positive verification queries.
-- Run this whole file as dnd_tool_se_migrator@127.0.0.1 after V004 succeeds.
-- The migrator connection is required because MySQL filters trigger metadata
-- according to the inspecting account's TRIGGER privilege.

USE `dnd_tool_se`;

SELECT
    `schema_version`,
    `script_name`,
    `script_sha256`,
    `description`
FROM `schema_meta`
ORDER BY `schema_version`;

SELECT
    `module_key`,
    `release_version`,
    `canonical_format_version`,
    `hash_algorithm`,
    `content_sha256`,
    `release_status`,
    `released_at` IS NOT NULL AS `released_at_present`
FROM `module_release`;

SELECT
    COUNT(*) AS `trigger_count`,
    SUM(`event_manipulation` = 'INSERT') AS `before_insert_count`,
    SUM(`event_manipulation` = 'UPDATE') AS `before_update_count`,
    SUM(`event_manipulation` = 'DELETE') AS `before_delete_count`,
    SUM(`action_timing` <> 'BEFORE') AS `non_before_count`
FROM `information_schema`.`triggers`
WHERE `trigger_schema` = 'dnd_tool_se';

-- Expected: zero rows. Every protected table must have one BEFORE trigger for
-- each DML kind. The total-count query above additionally rejects extras.
WITH `protected_table` (`table_name`) AS (
    SELECT 'module_release'
    UNION ALL SELECT 'module_rule_constant'
    UNION ALL SELECT 'module_field_definition'
    UNION ALL SELECT 'module_class_definition'
    UNION ALL SELECT 'module_proficiency_tier'
    UNION ALL SELECT 'module_proficiency_bonus_band'
    UNION ALL SELECT 'module_skill_definition'
    UNION ALL SELECT 'module_save_definition'
    UNION ALL SELECT 'module_item_template'
    UNION ALL SELECT 'module_entity_template'
    UNION ALL SELECT 'module_entity_template_value'
    UNION ALL SELECT 'module_entity_template_class_level'
    UNION ALL SELECT 'module_entity_template_proficiency'
    UNION ALL SELECT 'module_check_definition'
    UNION ALL SELECT 'module_roll_mode'
    UNION ALL SELECT 'module_event_template'
    UNION ALL SELECT 'module_effect_definition'
    UNION ALL SELECT 'module_event_check'
    UNION ALL SELECT 'module_event_effect'
    UNION ALL SELECT 'module_effect_parameter'
    UNION ALL SELECT 'module_map_definition'
    UNION ALL SELECT 'module_map_node'
    UNION ALL SELECT 'module_map_connection'
),
`required_event` (`event_manipulation`) AS (
    SELECT 'INSERT'
    UNION ALL SELECT 'UPDATE'
    UNION ALL SELECT 'DELETE'
)
SELECT
    protected.`table_name`,
    required.`event_manipulation`
FROM `protected_table` AS protected
CROSS JOIN `required_event` AS required
LEFT JOIN `information_schema`.`triggers` AS actual
  ON actual.`trigger_schema` = 'dnd_tool_se'
 AND actual.`event_object_table` = protected.`table_name`
 AND actual.`event_manipulation` = required.`event_manipulation`
 AND actual.`action_timing` = 'BEFORE'
WHERE actual.`trigger_name` IS NULL
ORDER BY protected.`table_name`, required.`event_manipulation`;

-- Expected: zero rows. This also catches a partially recorded publication.
SELECT
    `module_key`,
    `release_version`,
    `content_sha256`,
    `release_status`,
    `released_at`
FROM `module_release`
WHERE `module_key` <> 'dnd5e2014_srd51_se_v1'
   OR `release_version` <> '1'
   OR `canonical_format_version` <> 1
   OR `hash_algorithm` <> 'SHA-256'
   OR `content_sha256` <>
      '8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e'
   OR `release_status` <> 'RELEASED'
   OR `released_at` IS NULL;
