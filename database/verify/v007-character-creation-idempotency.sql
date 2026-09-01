-- Read-only verification for V007. Run after the complete migration succeeds.
-- The final result is PASS only when the metadata, column, index and FK match.

SELECT CASE WHEN
    (SELECT COUNT(*) FROM `schema_meta`) = 7
    AND (SELECT COUNT(*) FROM `schema_meta`
         WHERE `schema_version` = 7
           AND `script_name` = 'V007__character_creation_idempotency.sql'
           AND `script_sha256` =
               '01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') = 1
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'host_operation'
           AND column_name = 'character_id'
           AND column_type = 'bigint unsigned'
           AND is_nullable = 'YES') = 1
    AND (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'host_operation'
           AND index_name = 'ix_host_operation_character'
           AND column_name = 'character_id'
           AND seq_in_index = 1) = 1
    AND (SELECT COUNT(*) FROM information_schema.key_column_usage
         WHERE constraint_schema = DATABASE()
           AND table_name = 'host_operation'
           AND constraint_name = 'fk_host_operation_character'
           AND column_name = 'character_id'
           AND referenced_table_name = 'character_record'
           AND referenced_column_name = 'id') = 1
THEN 'PASS' ELSE 'FAIL' END AS verification_result;

SELECT `schema_version`, `script_name`, `script_sha256`, `description`
FROM `schema_meta`
ORDER BY `schema_version`;

SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'host_operation'
  AND column_name = 'character_id';

SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'host_operation'
  AND index_name = 'ix_host_operation_character';

SELECT constraint_name, column_name, referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE constraint_schema = DATABASE()
  AND table_name = 'host_operation'
  AND constraint_name = 'fk_host_operation_character';

SELECT 'campaign' AS table_name, COUNT(*) AS row_count FROM `campaign`
UNION ALL SELECT 'host_operation', COUNT(*) FROM `host_operation`
UNION ALL SELECT 'character_record', COUNT(*) FROM `character_record`
UNION ALL SELECT 'game_event', COUNT(*) FROM `game_event`
UNION ALL SELECT 'field_change', COUNT(*) FROM `field_change`
UNION ALL SELECT 'character_field_value', COUNT(*) FROM `character_field_value`;
