-- DnD Tool SE canonical-v2 multiclass shared spell-slot DRAFT foundation
--
-- Execute exactly once as the migrator after V017 has been recorded and verified.
-- This migration adds frozen class contribution metadata only. It does not persist
-- spell slots, enable spell execution, publish the module or activate archive format 2.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN

SELECT `id` INTO @complete_release_id
FROM `module_release`
WHERE `module_key` = 'dnd5e2014_srd51_se'
  AND `release_version` = '1'
  AND `canonical_format_version` = 2
  AND `hash_algorithm` = 'SHA-256'
  AND `content_sha256` IS NULL
  AND `release_status` = 'DRAFT'
  AND `released_at` IS NULL;

CREATE TEMPORARY TABLE `v018_multiclass_spellcasting_seed` (
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `progression` ENUM('NONE', 'FULL', 'HALF_DOWN', 'PACT_MAGIC') NOT NULL,
    PRIMARY KEY (`class_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=ascii COLLATE=ascii_bin;

-- FULL levels are added directly. Paladin and ranger HALF_DOWN levels are grouped
-- before division by two. Pact Magic remains a separate resource progression.
INSERT INTO `v018_multiclass_spellcasting_seed` (`class_key`, `progression`) VALUES
('class.barbarian', 'NONE'),
('class.bard', 'FULL'),
('class.cleric', 'FULL'),
('class.druid', 'FULL'),
('class.fighter', 'NONE'),
('class.monk', 'NONE'),
('class.paladin', 'HALF_DOWN'),
('class.ranger', 'HALF_DOWN'),
('class.rogue', 'NONE'),
('class.sorcerer', 'FULL'),
('class.warlock', 'PACT_MAGIC'),
('class.wizard', 'FULL');

SELECT COUNT(*) INTO @v018_seed_count
FROM `v018_multiclass_spellcasting_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`)
SELECT @complete_release_id, 'character.class', seed.`class_key`,
       'class.multiclass_spellcasting_progression', 1, 'IDENTIFIER', seed.`progression`
FROM `v018_multiclass_spellcasting_seed` AS seed
JOIN `module_catalog_definition_v2` AS definition
  ON definition.`module_release_id` = @complete_release_id
 AND definition.`definition_type` = 'character.class'
 AND definition.`definition_key` = seed.`class_key`;

SELECT COUNT(*) INTO @v018_inserted_count
FROM `module_catalog_attribute_v2`
WHERE `module_release_id` = @complete_release_id
  AND `definition_type` = 'character.class'
  AND `attribute_key` = 'class.multiclass_spellcasting_progression'
  AND `attribute_order` = 1
  AND `value_type` = 'IDENTIFIER'
  AND `identifier_value` IN ('NONE', 'FULL', 'HALF_DOWN', 'PACT_MAGIC');

DROP TEMPORARY TABLE `v018_multiclass_spellcasting_seed`;

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v017_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 17
  AND `script_name` = 'V017__character_archive_v2_origin.sql'
  AND `script_sha256` =
      '6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0';

INSERT INTO `schema_meta` (`schema_version`, `script_name`, `script_sha256`, `description`)
VALUES (
    18,
    'V018__multiclass_spell_slot_foundation.sql',
    '8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a',
    (SELECT CASE WHEN @v017_schema_record_count = 1
        AND @v018_seed_count = 12
        AND @v018_inserted_count = 12
        AND COUNT(*) = 1 THEN
        'Canonical-v2 DRAFT multiclass shared spell-slot foundation'
     ELSE NULL END
     FROM `module_release`
     WHERE `id` = @complete_release_id
       AND `release_status` = 'DRAFT'
       AND `content_sha256` IS NULL
       AND `released_at` IS NULL));

-- Intentionally no runtime state, release, campaign, character, event, operation,
-- account or grant is created.
