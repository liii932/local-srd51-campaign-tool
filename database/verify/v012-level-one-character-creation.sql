-- Run as the read-only verifier after an authorized V012 migration.
-- Returns exactly one row with verification_status = READY when schema and DRAFT rules match.
SELECT CASE WHEN
    (SELECT COUNT(*) FROM `schema_meta`
      WHERE `schema_version` = 12
        AND `script_name` = 'V012__level_one_character_creation.sql'
        AND `script_sha256` = '56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') = 1
    AND (SELECT COUNT(*) FROM information_schema.tables
          WHERE table_schema = 'dnd_tool_se'
            AND table_name IN ('character_creation_snapshot_v2',
                'character_creation_selection_v2', 'character_resource_state_v2')) = 3
    AND (SELECT COUNT(*) FROM `module_release`
          WHERE `module_key` = 'dnd5e2014_srd51_se'
            AND `release_version` = '1' AND `canonical_format_version` = 2
            AND `content_sha256` IS NULL AND `release_status` = 'DRAFT'
            AND `released_at` IS NULL) = 1
    AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2` AS a
          JOIN `module_release` AS r ON r.id = a.module_release_id
          WHERE r.module_key = 'dnd5e2014_srd51_se' AND r.release_version = '1'
            AND a.attribute_key = 'creation.level_one_profile') = 26
    AND (SELECT COUNT(*) FROM `character_creation_snapshot_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_creation_selection_v2`) = 0
    AND (SELECT COUNT(*) FROM `character_resource_state_v2`) = 0
    THEN 'READY' ELSE 'MISMATCH' END AS verification_status;
