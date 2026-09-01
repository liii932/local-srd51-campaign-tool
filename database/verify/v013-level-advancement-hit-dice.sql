-- Run as the read-only verifier after an authorized V013 migration.
-- Returns exactly one row with verification_status = READY when schema and DRAFT rules match.
SELECT CASE WHEN
    (SELECT COUNT(*) FROM `schema_meta`
      WHERE `schema_version` = 13
        AND `script_name` = 'V013__level_advancement_hit_dice.sql'
        AND `script_sha256` =
            'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') = 1
    AND (SELECT COUNT(*) FROM information_schema.tables
          WHERE table_schema = 'dnd_tool_se'
            AND table_name IN ('character_class_level_v2',
                'character_level_advancement_v2',
                'character_level_resource_change_v2')) = 3
    AND (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = 'dnd_tool_se'
            AND table_name = 'character_resource_state_v2'
            AND column_name = 'is_unlimited') = 1
    AND (SELECT COUNT(*) FROM `module_release`
          WHERE `module_key` = 'dnd5e2014_srd51_se'
            AND `release_version` = '1' AND `canonical_format_version` = 2
            AND `content_sha256` IS NULL AND `release_status` = 'DRAFT'
            AND `released_at` IS NULL) = 1
    AND (SELECT COUNT(*) FROM `module_catalog_definition_v2` AS d
          JOIN `module_release` AS r ON r.id = d.module_release_id
          WHERE r.module_key = 'dnd5e2014_srd51_se' AND r.release_version = '1'
            AND d.definition_type = 'character.resource'
            AND d.definition_key IN ('resource.hit_dice.d6', 'resource.hit_dice.d8',
                'resource.hit_dice.d10', 'resource.hit_dice.d12')) = 4
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
          JOIN `module_release` AS r ON r.id = a.module_release_id
          WHERE r.module_key = 'dnd5e2014_srd51_se' AND r.release_version = '1'
            AND a.attribute_key = 'class.proficiency_bonus_profile') = 12
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
          JOIN `module_release` AS r ON r.id = a.module_release_id
          WHERE r.module_key = 'dnd5e2014_srd51_se' AND r.release_version = '1'
            AND a.attribute_key = 'resource.maximum_profile') = 16
    AND (SELECT COUNT(*) FROM `character_class_level_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_level_advancement_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_level_resource_change_v2`) = 0
    THEN 'READY' ELSE 'MISMATCH' END AS verification_status;
