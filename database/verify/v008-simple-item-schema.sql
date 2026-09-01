-- Read-only verification for V008. Run the whole file after migration.
-- The first result is PASS only when metadata and the exact empty structure match.

USE `dnd_tool_se`;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM `schema_meta`) = 8
    AND (SELECT COUNT(*) FROM `schema_meta`
         WHERE `schema_version` = 8
           AND `script_name` = 'V008__stage2_simple_item_schema.sql'
           AND `script_sha256` =
               '369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709'
           AND `description` = 'Stage 2 empty simple item instance schema') = 1
    AND (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name = 'item_instance'
           AND engine = 'InnoDB'
           AND table_collation = 'utf8mb4_0900_ai_ci') = 1
    AND (SELECT COUNT(*) FROM `item_instance`) = 0
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'item_instance') = 9
    AND (SELECT COUNT(*) FROM information_schema.referential_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name = 'item_instance') = 3
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name = 'item_instance'
           AND constraint_type = 'CHECK') = 4
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'item_instance'
           AND column_name IN (
               'weight', 'price', 'currency', 'slot', 'durability', 'charges',
               'attack', 'damage', 'consumable', 'player_id', 'owner_id')) = 0
THEN 'PASS' ELSE 'FAIL' END AS `verification_result`;

SELECT `schema_version`, `script_name`, `script_sha256`, `description`
FROM `schema_meta`
ORDER BY `schema_version`;

-- Expected: one InnoDB/utf8mb4_0900_ai_ci table and zero rows.
SELECT `table_name`, `engine`, `table_collation`
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'item_instance';
SELECT COUNT(*) AS `item_instance_row_count` FROM `item_instance`;

-- Expected: exactly nine columns in the documented order and nullability.
SELECT `ordinal_position`, `column_name`, `column_type`, `is_nullable`,
       `column_default`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'item_instance'
ORDER BY `ordinal_position`;

-- Expected: three RESTRICT/RESTRICT foreign keys.
SELECT `constraint_name`, `referenced_table_name`, `update_rule`, `delete_rule`
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'item_instance'
ORDER BY `constraint_name`;

-- Expected: four enabled checks for source shape, name, description and quantity.
SELECT tc.`constraint_name`, cc.`check_clause`, tc.`enforced`
FROM information_schema.table_constraints AS tc
JOIN information_schema.check_constraints AS cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name = 'item_instance'
  AND tc.constraint_type = 'CHECK'
ORDER BY tc.constraint_name;

-- Expected: PRIMARY plus three non-unique supporting indexes.
SELECT `index_name`, `non_unique`, `seq_in_index`, `column_name`
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'item_instance'
ORDER BY `index_name`, `seq_in_index`;

-- Expected: the three immutable built-in templates shown by the rules document.
SELECT i.`item_key`, i.`display_name`, i.`description`
FROM `module_item_template` AS i
JOIN `module_release` AS r ON r.`id` = i.`module_release_id`
WHERE r.`module_key` = 'dnd5e2014_srd51_se_v1'
  AND r.`release_version` = '1'
ORDER BY i.`item_key`;

-- Expected: zero rows. These features are outside the simple-item scope.
SELECT `column_name`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'item_instance'
  AND `column_name` IN (
      'weight', 'price', 'currency', 'slot', 'durability', 'charges',
      'attack', 'damage', 'consumable', 'player_id', 'owner_id');
