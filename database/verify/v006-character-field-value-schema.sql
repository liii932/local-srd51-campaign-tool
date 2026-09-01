-- V006 positive verification queries.
-- Run this whole file with a read-only account after V006 succeeds.

USE `dnd_tool_se`;

SELECT
    `schema_version`, `script_name`, `script_sha256`, `description`
FROM `schema_meta`
ORDER BY `schema_version`;

-- Expected: one row, InnoDB and utf8mb4_0900_ai_ci.
SELECT `table_name`, `engine`, `table_collation`
FROM `information_schema`.`tables`
WHERE `table_schema` = 'dnd_tool_se'
  AND `table_name` = 'character_field_value';

-- Expected: zero. V006 installs structure, not character data.
SELECT COUNT(*) AS `character_field_value_row_count`
FROM `character_field_value`;

-- Expected: eight columns with the four nullable physical value columns.
SELECT
    `ordinal_position`, `column_name`, `column_type`, `is_nullable`
FROM `information_schema`.`columns`
WHERE `table_schema` = 'dnd_tool_se'
  AND `table_name` = 'character_field_value'
ORDER BY `ordinal_position`;

-- Expected: two rows, targeting character_record and module_field_definition.
SELECT `constraint_name`, `referenced_table_name`
FROM `information_schema`.`referential_constraints`
WHERE `constraint_schema` = 'dnd_tool_se'
  AND `table_name` = 'character_field_value'
ORDER BY `constraint_name`;

-- Expected: two rows, typed-value and boolean checks.
SELECT tc.`constraint_name`, cc.`check_clause`
FROM `information_schema`.`table_constraints` AS tc
JOIN `information_schema`.`check_constraints` AS cc
  ON cc.`constraint_schema` = tc.`constraint_schema`
 AND cc.`constraint_name` = tc.`constraint_name`
WHERE tc.`constraint_schema` = 'dnd_tool_se'
  AND tc.`table_name` = 'character_field_value'
  AND tc.`constraint_type` = 'CHECK'
ORDER BY tc.`constraint_name`;

-- Expected: three rows in sequence 1..3 and NON_UNIQUE=0.
SELECT `index_name`, `non_unique`, `seq_in_index`, `column_name`
FROM `information_schema`.`statistics`
WHERE `table_schema` = 'dnd_tool_se'
  AND `table_name` = 'module_field_definition'
  AND `index_name` = 'uq_mfd_release_key_type'
ORDER BY `seq_in_index`;

-- Expected: zero rows. Runtime values must not use JSON or public/player data.
SELECT `column_name`, `data_type`
FROM `information_schema`.`columns`
WHERE `table_schema` = 'dnd_tool_se'
  AND `table_name` = 'character_field_value'
  AND (
      `data_type` = 'json'
      OR `column_name` LIKE 'public\\_%' ESCAPE '\\'
      OR `column_name` LIKE 'player\\_%' ESCAPE '\\'
      OR `column_name` LIKE '%approval%'
      OR `column_name` LIKE '%owner%'
  );
