-- DnD Tool SE canonical-v2 starting-proficiency baseline for DRAFT advancement
--
-- Execute exactly once after V015. This migration adds frozen catalog data only;
-- it does not publish the module or write runtime character state.

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

CREATE TEMPORARY TABLE `v016_starting_proficiency_seed` (
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `profile` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`class_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=ascii COLLATE=ascii_bin;

-- Profiles are comma-separated, strictly sorted stable keys. Skills and tools
-- selected during creation remain authoritative in character_creation_selection_v2.
INSERT INTO `v016_starting_proficiency_seed` VALUES
('class.barbarian', 'armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple'),
('class.bard', 'armor.light,weapon.hand_crossbow,weapon.longsword,weapon.rapier,weapon.shortsword,weapon.simple'),
('class.cleric', 'armor.light,armor.medium,armor.shield,weapon.simple'),
('class.druid', 'armor.light,armor.medium,armor.shield,weapon.club,weapon.dagger,weapon.dart,weapon.javelin,weapon.mace,weapon.quarterstaff,weapon.scimitar,weapon.sickle,weapon.sling,weapon.spear'),
('class.fighter', 'armor.heavy,armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple'),
('class.monk', 'weapon.shortsword,weapon.simple'),
('class.paladin', 'armor.heavy,armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple'),
('class.ranger', 'armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple'),
('class.rogue', 'armor.light,weapon.hand_crossbow,weapon.longsword,weapon.rapier,weapon.shortsword,weapon.simple'),
('class.sorcerer', 'weapon.dagger,weapon.dart,weapon.light_crossbow,weapon.quarterstaff,weapon.sling'),
('class.warlock', 'armor.light,weapon.simple'),
('class.wizard', 'weapon.dagger,weapon.dart,weapon.light_crossbow,weapon.quarterstaff,weapon.sling');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`)
SELECT @complete_release_id, 'character.class', `class_key`,
       'class.starting_proficiency_profile', 1, 'TEXT', `profile`
FROM `v016_starting_proficiency_seed`;

DROP TEMPORARY TABLE `v016_starting_proficiency_seed`;

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v015_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 15
  AND `script_name` = 'V015__multiclass_asi_feat_draft.sql'
  AND `script_sha256` = '550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd';

INSERT INTO `schema_meta` (`schema_version`, `script_name`, `script_sha256`, `description`)
VALUES (16, 'V016__starting_proficiency_baseline_draft.sql',
        '0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda',
        (SELECT CASE WHEN @v015_schema_record_count = 1 AND COUNT(*) = 1 THEN
            'Canonical-v2 DRAFT starting proficiency baseline'
         ELSE NULL END
         FROM `module_release`
         WHERE `id` = @complete_release_id
           AND `release_status` = 'DRAFT'
           AND `content_sha256` IS NULL));

-- Intentionally no runtime state, release, account or grant is created.
