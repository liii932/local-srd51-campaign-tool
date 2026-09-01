-- Read-only verification for V015 canonical-v2 multiclass, ASI and feat DRAFT support.
USE `dnd_tool_se`;

SELECT COUNT(*) = 1 AS `v015_schema_record_ok`
FROM `schema_meta`
WHERE `schema_version` = 15
  AND `script_name` = 'V015__multiclass_asi_feat_draft.sql'
  AND `script_sha256` = '550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd';

SELECT COUNT(*) = 1 AS `draft_release_ok`
FROM `module_release`
WHERE `module_key` = 'dnd5e2014_srd51_se'
  AND `release_version` = '1'
  AND `canonical_format_version` = 2
  AND `release_status` = 'DRAFT'
  AND `content_sha256` IS NULL
  AND `released_at` IS NULL;

SELECT COUNT(*) = 12 AS `class_multiclass_prerequisites_ok`
FROM `module_catalog_attribute_v2` AS a
JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
WHERE r.`module_key` = 'dnd5e2014_srd51_se'
  AND r.`release_version` = '1'
  AND a.`definition_type` = 'character.class'
  AND a.`attribute_key` = 'class.multiclass_prerequisite';

SELECT COUNT(*) = 12 AS `class_multiclass_proficiencies_ok`
FROM `module_catalog_attribute_v2` AS a
JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
WHERE r.`module_key` = 'dnd5e2014_srd51_se'
  AND r.`release_version` = '1'
  AND a.`definition_type` = 'character.class'
  AND a.`attribute_key` = 'class.multiclass_proficiency_profile';

SELECT COUNT(*) = 12 AS `class_asi_profiles_ok`
FROM `module_catalog_attribute_v2` AS a
JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
WHERE r.`module_key` = 'dnd5e2014_srd51_se'
  AND r.`release_version` = '1'
  AND a.`definition_type` = 'character.class'
  AND a.`attribute_key` = 'class.asi_levels';

SELECT COUNT(*) = 1 AS `grappler_framework_ok`
FROM `module_catalog_attribute_v2` AS a
JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
WHERE r.`module_key` = 'dnd5e2014_srd51_se'
  AND r.`release_version` = '1'
  AND a.`definition_type` = 'character.feat'
  AND a.`definition_key` = 'feat.grappler'
  AND a.`attribute_key` = 'feat.prerequisite'
  AND a.`text_value` = 'ability.strength>=13';

SELECT (SELECT COUNT(*) FROM `character_advancement_choice_v2`) = 0
   AND (SELECT COUNT(*) FROM `character_ability_score_change_v2`) = 0
   AND (SELECT COUNT(*) FROM `character_feat_state_v2`) = 0
   AND (SELECT COUNT(*) FROM `character_multiclass_proficiency_v2`) = 0
   AS `v015_runtime_tables_empty`;
