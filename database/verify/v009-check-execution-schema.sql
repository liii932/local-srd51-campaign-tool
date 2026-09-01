-- Read-only verification for V009. Run the whole file after migration and
-- before granting runtime access. The first result must be PASS.

USE `dnd_tool_se`;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM `schema_meta`) = 9
    AND (SELECT COUNT(*) FROM `schema_meta`
         WHERE `schema_version` = 9
           AND `script_name` = 'V009__stage3_check_execution_schema.sql'
           AND `script_sha256` =
               'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c'
           AND `description` =
               'Stage 3 empty check execution and typed effect plan schema') = 1
    AND (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name IN (
               'check_execution', 'dice_roll', 'check_effect',
               'check_effect_parameter_value')
           AND engine = 'InnoDB'
           AND table_collation = 'utf8mb4_0900_ai_ci') = 4
    AND (SELECT COUNT(*) FROM `check_execution`) = 0
    AND (SELECT COUNT(*) FROM `dice_roll`) = 0
    AND (SELECT COUNT(*) FROM `check_effect`) = 0
    AND (SELECT COUNT(*) FROM `check_effect_parameter_value`) = 0
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'host_operation'
           AND column_name = 'game_event_id'
           AND column_type = 'bigint unsigned'
           AND is_nullable = 'YES') = 1
    AND (SELECT COUNT(*) FROM `host_operation`
         WHERE `game_event_id` IS NOT NULL) = 0
    AND (SELECT COUNT(*) FROM information_schema.referential_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name = 'host_operation'
           AND constraint_name = 'fk_host_operation_game_event'
           AND referenced_table_name = 'game_event'
           AND update_rule = 'RESTRICT'
           AND delete_rule = 'RESTRICT') = 1
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name = 'host_operation'
           AND constraint_name = 'chk_host_operation_game_event_result'
           AND constraint_type = 'CHECK'
           AND enforced = 'YES') = 1
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'host_operation'
           AND index_name = 'uq_host_operation_game_event'
           AND non_unique = 0) = 2
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'check_execution') = 15
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'dice_roll') = 6
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'check_effect') = 7
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'check_effect_parameter_value') = 12
    AND (SELECT COUNT(*) FROM information_schema.referential_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name IN (
               'check_execution', 'dice_roll', 'check_effect',
               'check_effect_parameter_value')) = 11
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name IN (
               'check_execution', 'dice_roll', 'check_effect',
               'check_effect_parameter_value')
           AND constraint_type = 'CHECK') = 13
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name IN (
               'check_execution', 'dice_roll', 'check_effect',
               'check_effect_parameter_value')
           AND extra LIKE '%STORED GENERATED%') = 2
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'module_effect_parameter'
           AND index_name = 'uq_mefp_runtime_parameter_shape') = 5
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name IN (
               'check_execution', 'dice_roll', 'check_effect',
               'check_effect_parameter_value')
           AND (data_type = 'json'
                OR column_name IN (
                    'algorithm', 'script_text', 'applied', 'is_applied',
                    'player_id', 'owner_id', 'public_id', 'attack', 'damage'))) = 0
THEN 'PASS' ELSE 'FAIL' END AS `verification_result`;

SELECT `schema_version`, `script_name`, `script_sha256`, `description`
FROM `schema_meta`
ORDER BY `schema_version`;

-- Expected: four InnoDB/utf8mb4_0900_ai_ci tables and zero rows.
SELECT `table_name`, `engine`, `table_collation`
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'check_execution', 'dice_roll', 'check_effect',
      'check_effect_parameter_value')
ORDER BY `table_name`;

SELECT 'check_execution' AS `table_name`, COUNT(*) AS `row_count`
FROM `check_execution`
UNION ALL SELECT 'dice_roll', COUNT(*) FROM `dice_roll`
UNION ALL SELECT 'check_effect', COUNT(*) FROM `check_effect`
UNION ALL SELECT 'check_effect_parameter_value', COUNT(*)
FROM `check_effect_parameter_value`
ORDER BY `table_name`;

-- Expected: one nullable BIGINT UNSIGNED column; existing rows remain NULL.
SELECT `ordinal_position`, `column_name`, `column_type`, `is_nullable`,
       `column_default`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'host_operation'
  AND column_name = 'game_event_id';
SELECT COUNT(*) AS `existing_operation_event_reference_count`
FROM `host_operation`
WHERE `game_event_id` IS NOT NULL;

-- Expected: exact documented column order, type, nullability and generated form.
SELECT `table_name`, `ordinal_position`, `column_name`, `column_type`,
       `is_nullable`, `column_default`, `extra`, `generation_expression`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'check_execution', 'dice_roll', 'check_effect',
      'check_effect_parameter_value')
ORDER BY `table_name`, `ordinal_position`;

-- Expected: all listed foreign keys use RESTRICT/RESTRICT.
SELECT `table_name`, `constraint_name`, `referenced_table_name`,
       `update_rule`, `delete_rule`
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN (
      'check_execution', 'dice_roll', 'check_effect',
      'check_effect_parameter_value', 'host_operation')
  AND constraint_name IN (
      'fk_host_operation_game_event',
      'fk_check_execution_event',
      'fk_check_execution_executor_campaign',
      'fk_check_execution_executor_release',
      'fk_check_execution_check_definition',
      'fk_check_execution_roll_mode',
      'fk_check_execution_event_template',
      'fk_dice_roll_execution',
      'fk_check_effect_execution_release',
      'fk_check_effect_definition',
      'fk_cepv_effect_definition',
      'fk_cepv_parameter_definition')
ORDER BY `table_name`, `constraint_name`;

-- Expected: enabled checks for source shape, ranges, selection and typed values.
SELECT tc.`table_name`, tc.`constraint_name`, cc.`check_clause`, tc.`enforced`
FROM information_schema.table_constraints AS tc
JOIN information_schema.check_constraints AS cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name IN (
      'host_operation', 'check_execution', 'dice_roll', 'check_effect',
      'check_effect_parameter_value')
  AND tc.constraint_type = 'CHECK'
ORDER BY tc.table_name, tc.constraint_name;

-- Expected: five ordered columns in the new immutable-definition index.
SELECT `index_name`, `non_unique`, `seq_in_index`, `column_name`
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'module_effect_parameter'
  AND index_name = 'uq_mefp_runtime_parameter_shape'
ORDER BY `seq_in_index`;

-- Expected: zero rows. Runtime values use fixed typed columns, not these fields.
SELECT `table_name`, `column_name`, `data_type`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'check_execution', 'dice_roll', 'check_effect',
      'check_effect_parameter_value')
  AND (data_type = 'json'
       OR column_name IN (
           'algorithm', 'script_text', 'applied', 'is_applied',
           'player_id', 'owner_id', 'public_id', 'attack', 'damage'))
ORDER BY `table_name`, `column_name`;
