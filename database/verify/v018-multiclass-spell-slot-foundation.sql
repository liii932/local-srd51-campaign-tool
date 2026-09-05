-- Read-only verification for the V018 multiclass shared spell-slot foundation.
USE `dnd_tool_se`;

SELECT COUNT(*) = 1 AS `v018_schema_record_ok`
FROM `schema_meta`
WHERE `schema_version` = 18
  AND `script_name` = 'V018__multiclass_spell_slot_foundation.sql'
  AND `script_sha256` = '8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a';

SELECT COUNT(*) = 12
   AND SUM(`identifier_value` = 'FULL') = 5
   AND SUM(`identifier_value` = 'HALF_DOWN') = 2
   AND SUM(`identifier_value` = 'PACT_MAGIC') = 1
   AND SUM(`identifier_value` = 'NONE') = 4
   AS `multiclass_spellcasting_progressions_ok`
FROM `module_catalog_attribute_v2` AS attribute_row
JOIN `module_release` AS release_row
  ON release_row.`id` = attribute_row.`module_release_id`
WHERE release_row.`module_key` = 'dnd5e2014_srd51_se'
  AND release_row.`release_version` = '1'
  AND attribute_row.`definition_type` = 'character.class'
  AND attribute_row.`attribute_key` = 'class.multiclass_spellcasting_progression'
  AND attribute_row.`attribute_order` = 1
  AND attribute_row.`value_type` = 'IDENTIFIER';

SELECT COUNT(*) = 2 AS `half_caster_mapping_ok`
FROM `module_catalog_attribute_v2` AS attribute_row
JOIN `module_release` AS release_row
  ON release_row.`id` = attribute_row.`module_release_id`
WHERE release_row.`module_key` = 'dnd5e2014_srd51_se'
  AND release_row.`release_version` = '1'
  AND attribute_row.`definition_type` = 'character.class'
  AND attribute_row.`definition_key` IN ('class.paladin', 'class.ranger')
  AND attribute_row.`attribute_key` = 'class.multiclass_spellcasting_progression'
  AND attribute_row.`identifier_value` = 'HALF_DOWN';

SELECT COUNT(*) = 1 AS `complete_release_still_draft`
FROM `module_release`
WHERE `module_key` = 'dnd5e2014_srd51_se'
  AND `release_version` = '1'
  AND `canonical_format_version` = 2
  AND `release_status` = 'DRAFT'
  AND `content_sha256` IS NULL
  AND `released_at` IS NULL;
