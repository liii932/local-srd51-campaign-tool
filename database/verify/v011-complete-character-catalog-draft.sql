-- Read-only verification for V011. Run the whole file only after separately
-- authorized migration execution. The first result must be PASS.

USE `dnd_tool_se`;

SELECT `id` INTO @complete_release_id
FROM `module_release`
WHERE `module_key` = 'dnd5e2014_srd51_se'
  AND `release_version` = '1'
  AND `canonical_format_version` = 2
  AND `hash_algorithm` = 'SHA-256'
  AND `content_sha256` IS NULL
  AND `release_status` = 'DRAFT'
  AND `released_at` IS NULL;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM `schema_meta`) = 11
    AND (SELECT COUNT(*) FROM `schema_meta`
         WHERE `schema_version` = 11
           AND `script_name` = 'V011__complete_character_catalog_draft.sql'
           AND `script_sha256` =
               '0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb'
           AND `description` =
               'Complete SRD 5.1 character catalog and canonical-v2 draft schema') = 1
    AND @complete_release_id IS NOT NULL
    AND (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name IN (
               'module_catalog_definition_v2',
               'module_catalog_attribute_v2',
               'module_catalog_relation_v2')
           AND engine = 'InnoDB'
           AND table_collation = 'utf8mb4_0900_ai_ci') = 3
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id) = 384
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
         WHERE `module_release_id` = @complete_release_id) = 1071
    AND (SELECT COUNT(*) FROM `module_catalog_relation_v2`
         WHERE `module_release_id` = @complete_release_id) = 311
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.race') = 9
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.subrace') = 4
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.background') = 1
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.language') = 18
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.tool') = 37
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.class') = 12
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.subclass') = 12
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.feature') = 274
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.resource') = 16
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
         WHERE `module_release_id` = @complete_release_id
           AND `definition_type` = 'character.feat') = 1
    AND (SELECT COUNT(*)
         FROM `campaign_module` AS cm
         WHERE cm.`module_release_id` = @complete_release_id) = 0
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name IN (
               'module_catalog_definition_v2',
               'module_catalog_attribute_v2',
               'module_catalog_relation_v2')
           AND data_type = 'json') = 0
THEN 'PASS' ELSE 'FAIL' END AS `verification_result`;

SELECT `schema_version`, `script_name`, `script_sha256`, `description`
FROM `schema_meta`
ORDER BY `schema_version`;

SELECT `definition_type`, COUNT(*) AS `definition_count`
FROM `module_catalog_definition_v2`
WHERE `module_release_id` = @complete_release_id
GROUP BY `definition_type`
ORDER BY `definition_type`;

SELECT `attribute_key`, `value_type`, COUNT(*) AS `attribute_count`
FROM `module_catalog_attribute_v2`
WHERE `module_release_id` = @complete_release_id
GROUP BY `attribute_key`, `value_type`
ORDER BY `attribute_key`, `value_type`;

SELECT `relation_type`, COUNT(*) AS `relation_count`
FROM `module_catalog_relation_v2`
WHERE `module_release_id` = @complete_release_id
GROUP BY `relation_type`
ORDER BY `relation_type`;

-- Expected: zero rows. A DRAFT release cannot be referenced by campaign state.
SELECT cm.`campaign_id`, cm.`module_release_id`
FROM `campaign_module` AS cm
WHERE cm.`module_release_id` = @complete_release_id;
