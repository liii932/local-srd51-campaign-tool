-- Read-only verification for the immutable V001 core schema.
-- Run only after reviewing the target database and selecting the intended schema.

SELECT `schema_version`, `script_name`, `script_sha256`, `description`
FROM `schema_meta`
WHERE `schema_version` = 1
  AND `script_name` = 'V001__stage1_schema.sql';

SELECT `table_name`
FROM `information_schema`.`tables`
WHERE `table_schema` = DATABASE()
  AND `table_name` IN (
      'schema_meta',
      'module_release',
      'campaign',
      'campaign_module',
      'host_operation')
ORDER BY `table_name`;

SELECT `index_name`, GROUP_CONCAT(`column_name` ORDER BY `seq_in_index`) AS `columns`
FROM `information_schema`.`statistics`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'host_operation'
  AND `non_unique` = 0
GROUP BY `index_name`
ORDER BY `index_name`;
