-- Run as the read-only verifier after an authorized V014 migration.
-- This script performs no writes and does not publish canonical/archive format 2.

SELECT
    (SELECT COUNT(*) FROM `schema_meta`) = 14
    AND (SELECT COUNT(*) FROM `schema_meta`
         WHERE `schema_version` = 14
           AND `script_name` = 'V014__class_feature_lifecycle.sql'
           AND `script_sha256` =
               'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') = 1
    AND (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = 'dnd_tool_se'
           AND table_name IN (
               'character_subclass_state_v2',
               'character_feature_state_v2',
               'character_feature_choice_v2',
               'character_feature_adjudication_v2',
               'character_resource_recovery_v2')) = 5
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'feature.execution_mode') = 236
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'feature.execution_mode'
           AND a.`identifier_value` = 'AUTOMATIC') = 13
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'feature.execution_mode'
           AND a.`identifier_value` = 'DM_ADJUDICATION') = 24
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'feature.execution_mode'
           AND a.`identifier_value` = 'BLOCKED') = 199
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'feature.execution_algorithm') = 236
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'subclass.selection_level') = 12
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'resource.execution_mode') = 16
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
         JOIN `module_release` AS r ON r.`id` = a.`module_release_id`
         WHERE r.`module_key` = 'dnd5e2014_srd51_se'
           AND r.`release_version` = '1'
           AND r.`canonical_format_version` = 2
           AND a.`attribute_key` = 'resource.recovery_profile') = 16
    AND (SELECT COUNT(*) FROM `module_release`
         WHERE `module_key` = 'dnd5e2014_srd51_se'
           AND `release_version` = '1'
           AND `canonical_format_version` = 2
           AND `release_status` = 'DRAFT'
           AND `content_sha256` IS NULL
           AND `released_at` IS NULL) = 1
    AND (SELECT COUNT(*) FROM `character_subclass_state_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_feature_state_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_feature_choice_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_feature_adjudication_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_resource_recovery_v2`) = 0
    AS `v014_class_feature_lifecycle_ok`;
