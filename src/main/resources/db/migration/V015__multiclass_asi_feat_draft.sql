-- DnD Tool SE canonical-v2 multiclass, ASI and feat DRAFT foundation
--
-- Execute exactly once as the migrator after V014 has been recorded and verified.
-- The complete release remains DRAFT. Spell-slot aggregation remains blocked.

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

CREATE TEMPORARY TABLE `v015_class_advancement_seed` (
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `multiclass_prerequisite` VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `multiclass_proficiency_profile` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `asi_levels` VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`class_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=ascii COLLATE=ascii_bin;

INSERT INTO `v015_class_advancement_seed` VALUES
('class.barbarian', 'ability.strength>=13', 'grant=armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple', '4,8,12,16,19'),
('class.bard', 'ability.charisma>=13', 'grant=armor.light|choice=1:skill.acrobatics,skill.animal_handling,skill.arcana,skill.athletics,skill.deception,skill.history,skill.insight,skill.intimidation,skill.investigation,skill.medicine,skill.nature,skill.perception,skill.performance,skill.persuasion,skill.religion,skill.sleight_of_hand,skill.stealth,skill.survival|choice=1:tool.bagpipes,tool.drum,tool.dulcimer,tool.flute,tool.horn,tool.lute,tool.lyre,tool.pan_flute,tool.shawm,tool.viol', '4,8,12,16,19'),
('class.cleric', 'ability.wisdom>=13', 'grant=armor.light,armor.medium,armor.shield', '4,8,12,16,19'),
('class.druid', 'ability.wisdom>=13', 'grant=armor.light,armor.medium,armor.shield', '4,8,12,16,19'),
('class.fighter', 'ability.strength>=13|ability.dexterity>=13', 'grant=armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple', '4,6,8,12,14,16,19'),
('class.monk', 'ability.dexterity>=13&ability.wisdom>=13', 'grant=weapon.short_sword,weapon.simple', '4,8,12,16,19'),
('class.paladin', 'ability.strength>=13&ability.charisma>=13', 'grant=armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple', '4,8,12,16,19'),
('class.ranger', 'ability.dexterity>=13&ability.wisdom>=13', 'grant=armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple|choice=1:skill.animal_handling,skill.athletics,skill.insight,skill.investigation,skill.nature,skill.perception,skill.stealth,skill.survival', '4,8,12,16,19'),
('class.rogue', 'ability.dexterity>=13', 'grant=armor.light,tool.thieves_tools|choice=1:skill.acrobatics,skill.athletics,skill.deception,skill.insight,skill.intimidation,skill.investigation,skill.perception,skill.performance,skill.persuasion,skill.sleight_of_hand,skill.stealth', '4,8,10,12,16,19'),
('class.sorcerer', 'ability.charisma>=13', '', '4,8,12,16,19'),
('class.warlock', 'ability.charisma>=13', 'grant=armor.light,weapon.simple', '4,8,12,16,19'),
('class.wizard', 'ability.intelligence>=13', '', '4,8,12,16,19');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`)
SELECT @complete_release_id, 'character.class', `class_key`,
       'class.multiclass_prerequisite', 1, 'TEXT', `multiclass_prerequisite`
FROM `v015_class_advancement_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`)
SELECT @complete_release_id, 'character.class', `class_key`,
       'class.multiclass_proficiency_profile', 1, 'TEXT', `multiclass_proficiency_profile`
FROM `v015_class_advancement_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`)
SELECT @complete_release_id, 'character.class', `class_key`,
       'class.asi_levels', 1, 'TEXT', `asi_levels`
FROM `v015_class_advancement_seed`;

DROP TEMPORARY TABLE `v015_class_advancement_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`)
VALUES (@complete_release_id, 'character.feat', 'feat.grappler',
        'feat.prerequisite', 1, 'TEXT', 'ability.strength>=13');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`)
VALUES
(@complete_release_id, 'character.feat', 'feat.grappler',
 'feat.execution_mode', 1, 'IDENTIFIER', 'BLOCKED'),
(@complete_release_id, 'character.feat', 'feat.grappler',
 'feat.execution_algorithm', 1, 'IDENTIFIER', 'BLOCKED_DOWNSTREAM_SYSTEM_V1');

ALTER TABLE `character_level_advancement_v2`
    DROP CHECK `chk_level_advancement_levels`,
    ADD CONSTRAINT `chk_level_advancement_levels` CHECK (
        `new_total_level` = `previous_total_level` + 1
        AND `new_class_level` = `previous_class_level` + 1
        AND `new_total_level` BETWEEN 2 AND 20
        AND `previous_class_level` BETWEEN 0 AND 19
        AND `new_class_level` BETWEEN 1 AND 20);

CREATE TABLE `character_advancement_choice_v2` (
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `target_class_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'character.class',
    `target_class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `choice_type` ENUM('NONE', 'ABILITY_SCORE_IMPROVEMENT', 'FEAT') NOT NULL,
    `feat_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT 'character.feat',
    `feat_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `spell_aggregation_status` ENUM('NOT_APPLICABLE', 'BLOCKED_PENDING_SPELL_SYSTEM') NOT NULL,
    PRIMARY KEY (`game_event_id`),
    UNIQUE KEY `uq_character_advancement_choice_event_character_release`
        (`game_event_id`, `character_id`, `module_release_id`),
    CONSTRAINT `fk_character_advancement_choice_advancement`
        FOREIGN KEY (`game_event_id`, `character_id`, `module_release_id`)
        REFERENCES `character_level_advancement_v2`
            (`game_event_id`, `character_id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_advancement_choice_class`
        FOREIGN KEY (`module_release_id`, `target_class_type`, `target_class_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_advancement_choice_feat`
        FOREIGN KEY (`module_release_id`, `feat_type`, `feat_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_advancement_choice_feat_pair` CHECK (
        (`choice_type` = 'FEAT' AND `feat_type` = 'character.feat' AND `feat_key` IS NOT NULL)
        OR (`choice_type` <> 'FEAT' AND `feat_type` IS NULL AND `feat_key` IS NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Canonical-v2 multiclass, ASI or feat choice per advancement';

CREATE TABLE `character_ability_score_change_v2` (
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `ability_key` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `previous_score` TINYINT UNSIGNED NOT NULL,
    `new_score` TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (`game_event_id`, `ability_key`),
    CONSTRAINT `fk_character_ability_score_change_choice`
        FOREIGN KEY (`game_event_id`, `character_id`, `module_release_id`)
        REFERENCES `character_advancement_choice_v2`
            (`game_event_id`, `character_id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_ability_score_change_key` CHECK (
        `ability_key` IN ('ability.strength', 'ability.dexterity', 'ability.constitution',
                          'ability.intelligence', 'ability.wisdom', 'ability.charisma')),
    CONSTRAINT `chk_character_ability_score_change_value` CHECK (
        `previous_score` BETWEEN 1 AND 20 AND `new_score` BETWEEN 2 AND 20
        AND `new_score` > `previous_score` AND `new_score` <= `previous_score` + 2)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable ASI score changes';

CREATE TABLE `character_feat_state_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `feat_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'character.feat',
    `feat_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `acquired_event_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`character_id`, `feat_key`),
    UNIQUE KEY `uq_character_feat_state_event` (`acquired_event_id`),
    CONSTRAINT `fk_character_feat_state_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_feat_state_definition`
        FOREIGN KEY (`module_release_id`, `feat_type`, `feat_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_feat_state_choice`
        FOREIGN KEY (`acquired_event_id`, `character_id`, `module_release_id`)
        REFERENCES `character_advancement_choice_v2`
            (`game_event_id`, `character_id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Extensible acquired feat state; V015 seeds only Grappler';

CREATE TABLE `character_multiclass_proficiency_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `class_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'character.class',
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `proficiency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `acquired_event_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`character_id`, `proficiency_key`),
    KEY `ix_multiclass_proficiency_class` (`module_release_id`, `class_type`, `class_key`),
    CONSTRAINT `fk_multiclass_proficiency_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_multiclass_proficiency_class`
        FOREIGN KEY (`module_release_id`, `class_type`, `class_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_multiclass_proficiency_choice`
        FOREIGN KEY (`acquired_event_id`, `character_id`, `module_release_id`)
        REFERENCES `character_advancement_choice_v2`
            (`game_event_id`, `character_id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_multiclass_proficiency_key` CHECK (
        REGEXP_LIKE(`proficiency_key`, '^[a-z][a-z0-9_]*([.][a-z0-9_]+)+$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Deterministic proficiency grants from entering a new class';

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v014_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 14
  AND `script_name` = 'V014__class_feature_lifecycle.sql'
  AND `script_sha256` = 'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361';

INSERT INTO `schema_meta` (`schema_version`, `script_name`, `script_sha256`, `description`)
VALUES (15, 'V015__multiclass_asi_feat_draft.sql',
        '550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd',
        (SELECT CASE WHEN @v014_schema_record_count = 1 AND COUNT(*) = 1 THEN
            'Canonical-v2 DRAFT multiclass, ASI and extensible feat foundation'
         ELSE NULL END
         FROM `module_release`
         WHERE `id` = @complete_release_id
           AND `release_status` = 'DRAFT'
           AND `content_sha256` IS NULL));

-- Intentionally no release, campaign, character, event, operation, account or grant is created.
