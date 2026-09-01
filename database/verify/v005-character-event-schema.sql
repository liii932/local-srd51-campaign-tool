-- V005 positive verification queries.
-- Run this whole file with a read-only account after V005 succeeds.

USE `dnd_tool_se`;

SELECT
    `schema_version`,
    `script_name`,
    `script_sha256`,
    `description`
FROM `schema_meta`
ORDER BY `schema_version`;

-- Expected: six rows, all InnoDB and utf8mb4_0900_ai_ci.
SELECT
    `table_name`,
    `engine`,
    `table_collation`
FROM `information_schema`.`tables`
WHERE `table_schema` = 'dnd_tool_se'
  AND `table_name` IN (
      'character_record',
      'character_class_level',
      'character_skill_proficiency',
      'character_save_proficiency',
      'game_event',
      'field_change'
  )
ORDER BY `table_name`;

-- Expected: every count is zero. V005 installs structure, not business data.
SELECT 'character_record' AS `table_name`, COUNT(*) AS `row_count`
FROM `character_record`
UNION ALL SELECT 'character_class_level', COUNT(*) FROM `character_class_level`
UNION ALL SELECT 'character_skill_proficiency', COUNT(*) FROM `character_skill_proficiency`
UNION ALL SELECT 'character_save_proficiency', COUNT(*) FROM `character_save_proficiency`
UNION ALL SELECT 'game_event', COUNT(*) FROM `game_event`
UNION ALL SELECT 'field_change', COUNT(*) FROM `field_change`;

-- Expected: 13 rows. Composite foreign keys prevent cross-campaign and
-- cross-release references, including a character hash different from its
-- campaign's frozen binding.
SELECT
    `table_name`,
    `constraint_name`,
    `referenced_table_name`
FROM `information_schema`.`referential_constraints`
WHERE `constraint_schema` = 'dnd_tool_se'
  AND `table_name` IN (
      'character_record',
      'character_class_level',
      'character_skill_proficiency',
      'character_save_proficiency',
      'game_event',
      'field_change'
  )
ORDER BY `table_name`, `constraint_name`;

-- Expected: 11 rows. These are the V005 CHECK constraints only.
SELECT
    tc.`table_name`,
    tc.`constraint_name`,
    cc.`check_clause`
FROM `information_schema`.`table_constraints` AS tc
JOIN `information_schema`.`check_constraints` AS cc
  ON cc.`constraint_schema` = tc.`constraint_schema`
 AND cc.`constraint_name` = tc.`constraint_name`
WHERE tc.`constraint_schema` = 'dnd_tool_se'
  AND tc.`constraint_type` = 'CHECK'
  AND tc.`table_name` IN (
      'character_record',
      'character_class_level',
      'character_skill_proficiency',
      'character_save_proficiency',
      'game_event',
      'field_change'
  )
ORDER BY tc.`table_name`, tc.`constraint_name`;

-- Expected: zero rows. No player/public/approval columns or JSON columns may
-- enter the local-only shared character model.
SELECT
    `table_name`,
    `column_name`,
    `data_type`
FROM `information_schema`.`columns`
WHERE `table_schema` = 'dnd_tool_se'
  AND `table_name` IN (
      'character_record',
      'character_class_level',
      'character_skill_proficiency',
      'character_save_proficiency',
      'game_event',
      'field_change'
  )
  AND (
      `data_type` = 'json'
      OR `column_name` LIKE 'public\\_%' ESCAPE '\\'
      OR `column_name` LIKE 'player\\_%' ESCAPE '\\'
      OR `column_name` LIKE '%approval%'
      OR `column_name` LIKE '%owner%'
  )
ORDER BY `table_name`, `column_name`;

-- Expected: zero rows. character_field_value is deliberately the next
-- migration item and must not be silently introduced by V005.
SELECT `table_name`
FROM `information_schema`.`tables`
WHERE `table_schema` = 'dnd_tool_se'
  AND `table_name` = 'character_field_value';
