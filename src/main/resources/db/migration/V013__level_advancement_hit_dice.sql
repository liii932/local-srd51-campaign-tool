-- DnD Tool SE canonical-v2 level advancement and hit-dice support
--
-- Execute exactly once as the migrator after V012 has been recorded and verified.
-- This migration extends only the unpublished canonical-v2 catalog and creates
-- empty authoritative tables. It creates no campaign, character, event or grant.

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

INSERT INTO `module_catalog_definition_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `display_name`, `description`, `sort_order`
) VALUES
(@complete_release_id, 'character.resource', 'resource.hit_dice.d6',
 'd6 Hit Dice', 'Available and maximum d6 Hit Dice.', 18),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d8',
 'd8 Hit Dice', 'Available and maximum d8 Hit Dice.', 19),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d10',
 'd10 Hit Dice', 'Available and maximum d10 Hit Dice.', 20),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d12',
 'd12 Hit Dice', 'Available and maximum d12 Hit Dice.', 21);

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `integer_value`
) VALUES
(@complete_release_id, 'character.resource', 'resource.hit_dice.d6',
 'source.page', 1, 'INTEGER', 56),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d8',
 'source.page', 1, 'INTEGER', 56),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d10',
 'source.page', 1, 'INTEGER', 56),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d12',
 'source.page', 1, 'INTEGER', 56);

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`
) VALUES
(@complete_release_id, 'character.resource', 'resource.hit_dice.d6',
 'catalog.category', 1, 'IDENTIFIER', 'CORE'),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d8',
 'catalog.category', 1, 'IDENTIFIER', 'CORE'),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d10',
 'catalog.category', 1, 'IDENTIFIER', 'CORE'),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d12',
 'catalog.category', 1, 'IDENTIFIER', 'CORE'),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d6',
 'resource.recovery', 1, 'IDENTIFIER', 'LONG_REST'),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d8',
 'resource.recovery', 1, 'IDENTIFIER', 'LONG_REST'),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d10',
 'resource.recovery', 1, 'IDENTIFIER', 'LONG_REST'),
(@complete_release_id, 'character.resource', 'resource.hit_dice.d12',
 'resource.recovery', 1, 'IDENTIFIER', 'LONG_REST');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`
)
SELECT @complete_release_id, 'character.class', `definition_key`,
       'class.proficiency_bonus_profile', 1, 'TEXT',
       '1-4:2,5-8:3,9-12:4,13-16:5,17-20:6'
FROM `module_catalog_definition_v2`
WHERE `module_release_id` = @complete_release_id
  AND `definition_type` = 'character.class';

CREATE TEMPORARY TABLE `v013_resource_profile_seed` (
    `resource_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `maximum_profile` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`resource_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=ascii COLLATE=ascii_bin;

-- Profiles use the frozen advancement-value-profile-v1 grammar. A missing range
-- before the first listed level means that the resource is not yet available.
INSERT INTO `v013_resource_profile_seed` (`resource_key`, `maximum_profile`) VALUES
('resource.barbarian.rage', '1-2:2,3-5:3,6-11:4,12-16:5,17-19:6,20:UNLIMITED'),
('resource.bard.bardic_inspiration', '1-20:CHARISMA_MODIFIER_MINIMUM_ONE'),
('resource.cleric.channel_divinity', '2-5:1,6-17:2,18-20:3'),
('resource.druid.wild_shape', '2-20:2'),
('resource.fighter.action_surge', '2-16:1,17-20:2'),
('resource.fighter.indomitable', '9-12:1,13-16:2,17-20:3'),
('resource.fighter.second_wind', '1-20:1'),
('resource.monk.ki', '2-20:CLASS_LEVEL'),
('resource.paladin.channel_divinity', '3-20:1'),
('resource.paladin.divine_sense', '1-20:ONE_PLUS_CHARISMA_MODIFIER_MINIMUM_ONE'),
('resource.paladin.lay_on_hands', '1-20:FIVE_TIMES_CLASS_LEVEL'),
('resource.rogue.stroke_of_luck', '20:1'),
('resource.sorcerer.sorcery_points', '2-20:CLASS_LEVEL'),
('resource.warlock.mystic_arcanum', '11-12:1,13-14:2,15-16:3,17-20:4'),
('resource.warlock.pact_magic', '1:1,2-10:2,11-16:3,17-20:4'),
('resource.wizard.arcane_recovery', '1-20:1');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`
)
SELECT @complete_release_id, 'character.resource', `resource_key`,
       'resource.maximum_profile', 1, 'TEXT', `maximum_profile`
FROM `v013_resource_profile_seed`;

DROP TEMPORARY TABLE `v013_resource_profile_seed`;

ALTER TABLE `character_resource_state_v2`
    DROP CHECK `chk_character_resource_state_bounds`,
    ADD COLUMN `is_unlimited` TINYINT UNSIGNED NOT NULL DEFAULT 0
        AFTER `maximum_value`,
    ADD CONSTRAINT `chk_character_resource_state_bounds` CHECK (
        (`is_unlimited` = 0 AND `maximum_value` > 0
            AND `current_value` BETWEEN 0 AND `maximum_value`)
        OR (`is_unlimited` = 1 AND `current_value` = 0 AND `maximum_value` = 0));

CREATE TABLE `character_class_level_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `class_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.class',
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `class_level` TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (`character_id`, `class_key`),
    KEY `ix_character_class_level_v2_definition`
        (`module_release_id`, `class_type`, `class_key`),
    CONSTRAINT `fk_character_class_level_v2_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_class_level_v2_definition`
        FOREIGN KEY (`module_release_id`, `class_type`, `class_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_class_level_v2_level`
        CHECK (`class_level` BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Authoritative canonical-v2 class levels';

CREATE TABLE `character_level_advancement_v2` (
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `preview_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `request_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `class_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.class',
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `previous_total_level` TINYINT UNSIGNED NOT NULL,
    `new_total_level` TINYINT UNSIGNED NOT NULL,
    `previous_class_level` TINYINT UNSIGNED NOT NULL,
    `new_class_level` TINYINT UNSIGNED NOT NULL,
    `hp_choice_algorithm` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `hit_die_sides` TINYINT UNSIGNED NOT NULL,
    `hit_die_roll` TINYINT UNSIGNED NULL,
    `constitution_modifier` TINYINT NOT NULL,
    `hit_point_increase` TINYINT UNSIGNED NOT NULL,
    `previous_maximum_hit_points` SMALLINT UNSIGNED NOT NULL,
    `new_maximum_hit_points` SMALLINT UNSIGNED NOT NULL,
    `previous_proficiency_bonus` TINYINT UNSIGNED NOT NULL,
    `new_proficiency_bonus` TINYINT UNSIGNED NOT NULL,
    `previous_row_version` BIGINT UNSIGNED NOT NULL,
    `new_row_version` BIGINT UNSIGNED NOT NULL,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`game_event_id`),
    UNIQUE KEY `uq_level_advancement_character_level`
        (`character_id`, `new_total_level`),
    UNIQUE KEY `uq_level_advancement_event_character_release`
        (`game_event_id`, `character_id`, `module_release_id`),
    CONSTRAINT `fk_level_advancement_event`
        FOREIGN KEY (`game_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_level_advancement_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_level_advancement_class`
        FOREIGN KEY (`module_release_id`, `class_type`, `class_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_level_advancement_levels` CHECK (
        `new_total_level` = `previous_total_level` + 1
        AND `new_class_level` = `previous_class_level` + 1
        AND `new_total_level` BETWEEN 2 AND 20
        AND `new_class_level` BETWEEN 2 AND 20),
    CONSTRAINT `chk_level_advancement_hp_choice` CHECK (
        (`hp_choice_algorithm` = 'FIXED_AVERAGE' AND `hit_die_roll` IS NULL)
        OR (`hp_choice_algorithm` = 'SERVER_ROLL'
            AND `hit_die_roll` BETWEEN 1 AND `hit_die_sides`)),
    CONSTRAINT `chk_level_advancement_hit_die`
        CHECK (`hit_die_sides` IN (6, 8, 10, 12)),
    CONSTRAINT `chk_level_advancement_hp` CHECK (
        `hit_point_increase` > 0
        AND `new_maximum_hit_points` =
            `previous_maximum_hit_points` + `hit_point_increase`),
    CONSTRAINT `chk_level_advancement_proficiency` CHECK (
        `previous_proficiency_bonus` BETWEEN 2 AND 6
        AND `new_proficiency_bonus` BETWEEN 2 AND 6),
    CONSTRAINT `chk_level_advancement_versions`
        CHECK (`new_row_version` = `previous_row_version` + 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable canonical-v2 level advancement snapshots';

CREATE TABLE `character_level_resource_change_v2` (
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `resource_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.resource',
    `resource_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `previous_current_value` BIGINT NULL,
    `previous_maximum_value` BIGINT NULL,
    `previous_is_unlimited` TINYINT UNSIGNED NULL,
    `new_current_value` BIGINT NOT NULL,
    `new_maximum_value` BIGINT NOT NULL,
    `new_is_unlimited` TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (`game_event_id`, `resource_key`),
    KEY `ix_level_resource_change_definition`
        (`module_release_id`, `resource_type`, `resource_key`),
    CONSTRAINT `fk_level_resource_change_advancement`
        FOREIGN KEY (`game_event_id`, `character_id`, `module_release_id`)
        REFERENCES `character_level_advancement_v2`
            (`game_event_id`, `character_id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_level_resource_change_definition`
        FOREIGN KEY (`module_release_id`, `resource_type`, `resource_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_level_resource_change_previous_pair` CHECK (
        (`previous_current_value` IS NULL
            AND `previous_maximum_value` IS NULL
            AND `previous_is_unlimited` IS NULL)
        OR (`previous_is_unlimited` = 0 AND `previous_maximum_value` > 0
            AND `previous_current_value` BETWEEN 0 AND `previous_maximum_value`)
        OR (`previous_is_unlimited` = 1
            AND `previous_current_value` = 0 AND `previous_maximum_value` = 0)),
    CONSTRAINT `chk_level_resource_change_new_bounds` CHECK (
        (`new_is_unlimited` = 0 AND `new_maximum_value` > 0
            AND `new_current_value` BETWEEN 0 AND `new_maximum_value`)
        OR (`new_is_unlimited` = 1
            AND `new_current_value` = 0 AND `new_maximum_value` = 0))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Resource deltas reconstructing canonical-v2 level advancement';

-- Stable audit contract consumed by the repository; no event row is inserted here.
SET @level_advancement_event_type = 'CHARACTER_LEVEL_ADVANCED';

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v012_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 12
  AND `script_name` = 'V012__level_one_character_creation.sql'
  AND `script_sha256` =
      '56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2';

INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    13,
    'V013__level_advancement_hit_dice.sql',
    'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d',
    (SELECT CASE WHEN @v012_schema_record_count = 1 AND COUNT(*) = 1
        AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `attribute_key` = 'class.proficiency_bonus_profile') = 12
        AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `attribute_key` = 'resource.maximum_profile') = 16
        AND (SELECT COUNT(*) FROM `module_catalog_definition_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `definition_type` = 'character.resource'
               AND `definition_key` LIKE 'resource.hit_dice.d%') = 4 THEN
        'Canonical-v2 level advancement and hit-dice support'
     ELSE NULL END
     FROM `module_release`
     WHERE `module_key` = 'dnd5e2014_srd51_se'
       AND `release_version` = '1'
       AND `canonical_format_version` = 2
       AND `hash_algorithm` = 'SHA-256'
       AND `content_sha256` IS NULL
       AND `release_status` = 'DRAFT'
       AND `released_at` IS NULL)
);

-- Intentionally no campaign, character, class-level, resource, event,
-- operation, account or grant row is inserted, and DRAFT remains unpublished.
