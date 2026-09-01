-- Read-only verification for V017 archive-format-2 DRAFT character-state origins.
USE `dnd_tool_se`;

SELECT COUNT(*) = 1 AS `v017_schema_record_ok`
FROM `schema_meta`
WHERE `schema_version` = 17
  AND `script_name` = 'V017__character_archive_v2_origin.sql'
  AND `script_sha256` = '6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0';

SELECT COUNT(*) = 2 AS `archive_state_origin_columns_ok`
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('character_feat_state_v2',
                     'character_multiclass_proficiency_v2')
  AND column_name = 'state_origin'
  AND column_type = 'enum(''ADVANCEMENT'',''ARCHIVE_RESTORE'')'
  AND is_nullable = 'NO'
  AND column_default = 'ADVANCEMENT';

-- Trigger metadata requires the verifier to have visibility for this schema.
SELECT COUNT(*) = 4 AS `archive_state_origin_triggers_ok`
FROM information_schema.triggers
WHERE trigger_schema = DATABASE()
  AND trigger_name IN (
      'trg_character_feat_state_v2_origin_insert',
      'trg_character_feat_state_v2_origin_update',
      'trg_multiclass_proficiency_v2_origin_insert',
      'trg_multiclass_proficiency_v2_origin_update');

SELECT COUNT(*) = 1 AS `complete_release_still_draft`
FROM `module_release`
WHERE `module_key` = 'dnd5e2014_srd51_se'
  AND `release_version` = '1'
  AND `canonical_format_version` = 2
  AND `release_status` = 'DRAFT'
  AND `content_sha256` IS NULL
  AND `released_at` IS NULL;

SELECT (SELECT COUNT(*) FROM `character_feat_state_v2`) = 0
   AND (SELECT COUNT(*) FROM `character_multiclass_proficiency_v2`) = 0
   AS `v017_runtime_tables_empty`;
