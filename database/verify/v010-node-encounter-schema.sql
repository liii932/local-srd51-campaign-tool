-- Read-only verification for V010. Run the whole file after migration and
-- before granting runtime access. The first result must be PASS.

USE `dnd_tool_se`;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM `schema_meta`) = 10
    AND (SELECT COUNT(*) FROM `schema_meta`
         WHERE `schema_version` = 10
           AND `script_name` = 'V010__stage3_node_encounter_schema.sql'
           AND `script_sha256` =
               'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87'
           AND `description` =
               'Stage 3 empty node map and minimal encounter runtime schema') = 1
    AND (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name IN (
               'map_instance', 'party_world_position', 'battle_state',
               'battle_participant', 'entity_position')
           AND engine = 'InnoDB'
           AND table_collation = 'utf8mb4_0900_ai_ci') = 5
    AND (SELECT COUNT(*) FROM `map_instance`) = 0
    AND (SELECT COUNT(*) FROM `party_world_position`) = 0
    AND (SELECT COUNT(*) FROM `battle_state`) = 0
    AND (SELECT COUNT(*) FROM `battle_participant`) = 0
    AND (SELECT COUNT(*) FROM `entity_position`) = 0
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'map_instance') = 6
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'party_world_position') = 6
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_state') = 10
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_participant') = 6
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'entity_position') = 9
    AND (SELECT COUNT(*) FROM information_schema.referential_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name IN (
               'map_instance', 'party_world_position', 'battle_state',
               'battle_participant', 'entity_position')
           AND update_rule = 'RESTRICT'
           AND delete_rule = 'RESTRICT') = 10
    AND (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema = DATABASE()
           AND table_name IN ('battle_state', 'entity_position')
           AND constraint_type = 'CHECK'
           AND enforced = 'YES') = 2
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_state'
           AND column_name = 'battle_status'
           AND column_type = 'enum(''ACTIVE'',''CLOSED'')'
           AND is_nullable = 'NO'
           AND column_default = 'ACTIVE') = 1
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_participant'
           AND column_name = 'faction'
           AND column_type = 'enum(''ALLY'',''ENEMY'',''NEUTRAL'')'
           AND is_nullable = 'NO') = 1
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_state'
           AND column_name = 'active_campaign_id'
           AND column_type = 'bigint unsigned'
           AND extra LIKE '%STORED GENERATED%'
           AND generation_expression LIKE '%battle_status%ACTIVE%campaign_id%') = 1
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_state'
           AND index_name = 'uq_battle_state_active_campaign'
           AND non_unique = 0
           AND seq_in_index = 1
           AND column_name = 'active_campaign_id') = 1
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_participant'
           AND index_name = 'uq_battle_participant_character'
           AND non_unique = 0) = 2
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'battle_participant'
           AND index_name = 'uq_battle_participant_character'
           AND non_unique = 0
           AND ((seq_in_index = 1 AND column_name = 'battle_id')
             OR (seq_in_index = 2 AND column_name = 'character_id'))) = 2
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'campaign_module'
           AND index_name = 'uq_campaign_module_release'
           AND non_unique = 0) = 2
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'module_map_definition'
           AND index_name = 'uq_mmap_runtime_node_shape'
           AND non_unique = 0) = 3
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name IN (
               'map_instance', 'party_world_position', 'battle_state',
               'battle_participant', 'entity_position')
           AND (data_type = 'json'
                OR column_name IN (
                    'initiative', 'round', 'turn', 'action', 'reaction',
                    'speed', 'distance', 'attack', 'damage', 'grid_x', 'grid_y',
                    'player_id', 'owner_id', 'public_id'))) = 0
THEN 'PASS' ELSE 'FAIL' END AS `verification_result`;

SELECT `schema_version`, `script_name`, `script_sha256`, `description`
FROM `schema_meta`
ORDER BY `schema_version`;

-- Expected: five InnoDB/utf8mb4_0900_ai_ci tables and zero rows.
SELECT `table_name`, `engine`, `table_collation`
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'map_instance', 'party_world_position', 'battle_state',
      'battle_participant', 'entity_position')
ORDER BY `table_name`;

SELECT 'map_instance' AS `table_name`, COUNT(*) AS `row_count`
FROM `map_instance`
UNION ALL SELECT 'party_world_position', COUNT(*) FROM `party_world_position`
UNION ALL SELECT 'battle_state', COUNT(*) FROM `battle_state`
UNION ALL SELECT 'battle_participant', COUNT(*) FROM `battle_participant`
UNION ALL SELECT 'entity_position', COUNT(*) FROM `entity_position`
ORDER BY `table_name`;

-- Expected: exact documented columns and one stored generated active guard.
SELECT `table_name`, `ordinal_position`, `column_name`, `column_type`,
       `is_nullable`, `column_default`, `extra`, `generation_expression`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'map_instance', 'party_world_position', 'battle_state',
      'battle_participant', 'entity_position')
ORDER BY `table_name`, `ordinal_position`;

-- Expected: every listed relationship uses RESTRICT/RESTRICT.
SELECT `table_name`, `constraint_name`, `referenced_table_name`,
       `update_rule`, `delete_rule`
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN (
      'map_instance', 'party_world_position', 'battle_state',
      'battle_participant', 'entity_position')
ORDER BY `table_name`, `constraint_name`;

-- Expected: enabled battle completion and active-campaign checks.
SELECT tc.`table_name`, tc.`constraint_name`, cc.`check_clause`, tc.`enforced`
FROM information_schema.table_constraints AS tc
JOIN information_schema.check_constraints AS cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name IN ('battle_state', 'entity_position')
  AND tc.constraint_type = 'CHECK'
ORDER BY tc.table_name, tc.constraint_name;

-- Expected: active-campaign and encounter/character guards plus two supporting keys.
SELECT `table_name`, `index_name`, `non_unique`, `seq_in_index`, `column_name`
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND ((table_name = 'battle_state'
        AND index_name = 'uq_battle_state_active_campaign')
    OR (table_name = 'battle_participant'
        AND index_name = 'uq_battle_participant_character')
    OR (table_name = 'campaign_module'
        AND index_name = 'uq_campaign_module_release')
    OR (table_name = 'module_map_definition'
        AND index_name = 'uq_mmap_runtime_node_shape'))
ORDER BY `table_name`, `index_name`, `seq_in_index`;

-- Expected: zero rows. V010 is not a turn, distance or combat engine.
SELECT `table_name`, `column_name`, `data_type`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'map_instance', 'party_world_position', 'battle_state',
      'battle_participant', 'entity_position')
  AND (data_type = 'json'
       OR column_name IN (
           'initiative', 'round', 'turn', 'action', 'reaction',
           'speed', 'distance', 'attack', 'damage', 'grid_x', 'grid_y',
           'player_id', 'owner_id', 'public_id'))
ORDER BY `table_name`, `column_name`;
