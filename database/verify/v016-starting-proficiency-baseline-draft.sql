-- Read-only verification for V016 canonical-v2 starting-proficiency baseline.
USE `dnd_tool_se`;

SELECT COUNT(*) = 1 AS `v016_schema_record_ok`
FROM `schema_meta`
WHERE `schema_version` = 16
  AND `script_name` = 'V016__starting_proficiency_baseline_draft.sql'
  AND `script_sha256` = '0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda';

SELECT COUNT(*) = 12 AS `starting_profiles_complete`
FROM `module_catalog_attribute_v2` AS a
JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
WHERE r.`module_key` = 'dnd5e2014_srd51_se'
  AND r.`release_version` = '1'
  AND r.`canonical_format_version` = 2
  AND r.`release_status` = 'DRAFT'
  AND r.`content_sha256` IS NULL
  AND a.`definition_type` = 'character.class'
  AND a.`attribute_key` = 'class.starting_proficiency_profile'
  AND a.`attribute_order` = 1
  AND a.`value_type` = 'TEXT'
  AND a.`text_value` IS NOT NULL;

SELECT COUNT(*) = 0 AS `starting_profiles_are_ascii_stable_lists`
FROM `module_catalog_attribute_v2` AS a
JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
WHERE r.`module_key` = 'dnd5e2014_srd51_se'
  AND r.`release_version` = '1'
  AND a.`attribute_key` = 'class.starting_proficiency_profile'
  AND (a.`text_value` = ''
       OR NOT REGEXP_LIKE(a.`text_value`,
            '^[a-z][a-z0-9_]*([.][a-z0-9_]+)+(,[a-z][a-z0-9_]*([.][a-z0-9_]+)+)*$', 'c'));
