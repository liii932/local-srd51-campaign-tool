-- DnD Tool SE level-one character creation schema and canonical-v2 DRAFT rules
--
-- Execute exactly once as the migrator after V011 has been recorded and verified.
-- This migration adds immutable DRAFT rule profiles and empty runtime tables. It
-- does not publish the module, bind a campaign, create a character or grant users.
-- Existence guards are prohibited; unexpected prerequisites fail closed.

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
) VALUES (
    @complete_release_id, 'character.resource', 'resource.hit_points',
    'Hit Points', 'Current and maximum hit points for a character.', 17
);

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `integer_value`
) VALUES
(@complete_release_id, 'character.resource', 'resource.hit_points',
 'source.page', 1, 'INTEGER', 8);

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`
) VALUES
(@complete_release_id, 'character.resource', 'resource.hit_points',
 'catalog.category', 1, 'IDENTIFIER', 'CORE'),
(@complete_release_id, 'character.resource', 'resource.hit_points',
 'resource.recovery', 1, 'IDENTIFIER', 'LONG_REST');

CREATE TEMPORARY TABLE `v012_level_one_profile_seed` (
    `definition_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `definition_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `profile` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`definition_type`, `definition_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=ascii COLLATE=ascii_bin;

-- Profiles use the frozen level-one-profile-v1 grammar documented in
-- docs/rules/character-creation-v2.md. Every list is ordered canonically.
INSERT INTO `v012_level_one_profile_seed` (
    `definition_type`, `definition_key`, `profile`
) VALUES
('character.race', 'race.dwarf', 'bonus=constitution+2|language=common,dwarvish|subrace=required|tool=1:brewer_supplies,mason_tools,smith_tools'),
('character.subrace', 'subrace.hill_dwarf', 'bonus=wisdom+1'),
('character.race', 'race.elf', 'bonus=dexterity+2|language=common,elvish|skill=perception|subrace=required'),
('character.subrace', 'subrace.high_elf', 'bonus=intelligence+1|language=1:abyssal,celestial,common,deep_speech,draconic,dwarvish,elvish,giant,gnomish,goblin,halfling,infernal,orc,primordial,sylvan,undercommon'),
('character.race', 'race.halfling', 'bonus=dexterity+2|language=common,halfling|subrace=required'),
('character.subrace', 'subrace.lightfoot', 'bonus=charisma+1'),
('character.race', 'race.human', 'bonus=charisma+1,constitution+1,dexterity+1,intelligence+1,strength+1,wisdom+1|language=common|language=1:abyssal,celestial,deep_speech,draconic,dwarvish,elvish,giant,gnomish,goblin,halfling,infernal,orc,primordial,sylvan,undercommon'),
('character.race', 'race.dragonborn', 'bonus=charisma+1,strength+2|language=common,draconic'),
('character.race', 'race.gnome', 'bonus=intelligence+2|language=common,gnomish|subrace=required'),
('character.subrace', 'subrace.rock_gnome', 'bonus=constitution+1'),
('character.race', 'race.half_elf', 'bonus=charisma+2|bonus_choice=2:constitution,dexterity,intelligence,strength,wisdom|language=common,elvish|language=1:abyssal,celestial,deep_speech,draconic,dwarvish,giant,gnomish,goblin,halfling,infernal,orc,primordial,sylvan,undercommon|skill=2:acrobatics,animal_handling,arcana,athletics,deception,history,insight,intimidation,investigation,medicine,nature,perception,performance,persuasion,religion,sleight_of_hand,stealth,survival'),
('character.race', 'race.half_orc', 'bonus=constitution+1,strength+2|language=common,orc|skill=intimidation'),
('character.race', 'race.tiefling', 'bonus=charisma+2,intelligence+1|language=common,infernal'),
('character.background', 'background.acolyte', 'language=2:abyssal,celestial,deep_speech,draconic,dwarvish,elvish,giant,gnomish,goblin,halfling,infernal,orc,primordial,sylvan,undercommon|skill=insight,religion|start=1:starting.background.acolyte.equipment'),
('character.class', 'class.barbarian', 'hp=12|save=constitution,strength|skill=2:animal_handling,athletics,intimidation,nature,perception,survival|start=1:starting.class.barbarian.a,starting.class.barbarian.b'),
('character.class', 'class.bard', 'hp=8|save=charisma,dexterity|skill=3:acrobatics,animal_handling,arcana,athletics,deception,history,insight,intimidation,investigation,medicine,nature,perception,performance,persuasion,religion,sleight_of_hand,stealth,survival|start=1:starting.class.bard.a,starting.class.bard.b|tool=3:bagpipes,drum,dulcimer,flute,horn,lute,lyre,pan_flute,shawm,viol'),
('character.class', 'class.cleric', 'hp=8|save=charisma,wisdom|skill=2:history,insight,medicine,persuasion,religion|start=1:starting.class.cleric.a,starting.class.cleric.b'),
('character.class', 'class.druid', 'hp=8|language=druidic|save=intelligence,wisdom|skill=2:animal_handling,arcana,insight,medicine,nature,perception,religion,survival|start=1:starting.class.druid.a,starting.class.druid.b|tool=herbalism_kit'),
('character.class', 'class.fighter', 'hp=10|save=constitution,strength|skill=2:acrobatics,animal_handling,athletics,history,insight,intimidation,perception,survival|start=1:starting.class.fighter.a,starting.class.fighter.b'),
('character.class', 'class.monk', 'hp=8|save=dexterity,strength|skill=2:acrobatics,athletics,history,insight,religion,stealth|start=1:starting.class.monk.a,starting.class.monk.b|tool=1:bagpipes,brewer_supplies,calligrapher_supplies,carpenter_tools,cartographer_tools,cobbler_tools,cook_utensils,drum,dulcimer,flute,glassblower_tools,horn,jeweler_tools,leatherworker_tools,lute,lyre,mason_tools,painter_supplies,pan_flute,potter_tools,shawm,smith_tools,tinker_tools,viol,weaver_tools,woodcarver_tools'),
('character.class', 'class.paladin', 'hp=10|save=charisma,wisdom|skill=2:athletics,insight,intimidation,medicine,persuasion,religion|start=1:starting.class.paladin.a,starting.class.paladin.b'),
('character.class', 'class.ranger', 'hp=10|save=dexterity,strength|skill=3:animal_handling,athletics,insight,investigation,nature,perception,stealth,survival|start=1:starting.class.ranger.a,starting.class.ranger.b'),
('character.class', 'class.rogue', 'hp=8|language=thieves_cant|save=dexterity,intelligence|skill=4:acrobatics,athletics,deception,insight,intimidation,investigation,perception,performance,persuasion,sleight_of_hand,stealth|start=1:starting.class.rogue.a,starting.class.rogue.b|tool=thieves_tools'),
('character.class', 'class.sorcerer', 'hp=6|save=charisma,constitution|skill=2:arcana,deception,insight,intimidation,persuasion,religion|start=1:starting.class.sorcerer.a,starting.class.sorcerer.b'),
('character.class', 'class.warlock', 'hp=8|save=charisma,wisdom|skill=2:arcana,deception,history,intimidation,investigation,nature,religion|start=1:starting.class.warlock.a,starting.class.warlock.b'),
('character.class', 'class.wizard', 'hp=6|save=intelligence,wisdom|skill=2:arcana,history,insight,investigation,medicine,religion|start=1:starting.class.wizard.a,starting.class.wizard.b');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       'creation.level_one_profile', 1, 'TEXT', `profile`
FROM `v012_level_one_profile_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`
)
SELECT @complete_release_id, 'character.class', `definition_key`,
       'creation.ability_method', 1, 'IDENTIFIER', 'ability.standard_array_v1'
FROM `v012_level_one_profile_seed`
WHERE `definition_type` = 'character.class';

DROP TEMPORARY TABLE `v012_level_one_profile_seed`;

CREATE TABLE `character_creation_snapshot_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `preview_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `request_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `ability_method_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `race_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'character.race',
    `race_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `subrace_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT 'character.subrace',
    `subrace_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `background_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'character.background',
    `background_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `class_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'character.class',
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `base_strength` TINYINT UNSIGNED NOT NULL,
    `base_dexterity` TINYINT UNSIGNED NOT NULL,
    `base_constitution` TINYINT UNSIGNED NOT NULL,
    `base_intelligence` TINYINT UNSIGNED NOT NULL,
    `base_wisdom` TINYINT UNSIGNED NOT NULL,
    `base_charisma` TINYINT UNSIGNED NOT NULL,
    `final_strength` TINYINT UNSIGNED NOT NULL,
    `final_dexterity` TINYINT UNSIGNED NOT NULL,
    `final_constitution` TINYINT UNSIGNED NOT NULL,
    `final_intelligence` TINYINT UNSIGNED NOT NULL,
    `final_wisdom` TINYINT UNSIGNED NOT NULL,
    `final_charisma` TINYINT UNSIGNED NOT NULL,
    `maximum_hit_points` SMALLINT UNSIGNED NOT NULL,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`character_id`),
    UNIQUE KEY `uq_character_creation_snapshot_preview` (`preview_digest_sha256`),
    CONSTRAINT `fk_character_creation_snapshot_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_creation_snapshot_race`
        FOREIGN KEY (`module_release_id`, `race_type`, `race_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_creation_snapshot_subrace`
        FOREIGN KEY (`module_release_id`, `subrace_type`, `subrace_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_creation_snapshot_background`
        FOREIGN KEY (`module_release_id`, `background_type`, `background_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_creation_snapshot_class`
        FOREIGN KEY (`module_release_id`, `class_type`, `class_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_creation_snapshot_scores` CHECK (
        `base_strength` BETWEEN 3 AND 18 AND `base_dexterity` BETWEEN 3 AND 18
        AND `base_constitution` BETWEEN 3 AND 18 AND `base_intelligence` BETWEEN 3 AND 18
        AND `base_wisdom` BETWEEN 3 AND 18 AND `base_charisma` BETWEEN 3 AND 18
        AND `final_strength` BETWEEN 3 AND 20 AND `final_dexterity` BETWEEN 3 AND 20
        AND `final_constitution` BETWEEN 3 AND 20 AND `final_intelligence` BETWEEN 3 AND 20
        AND `final_wisdom` BETWEEN 3 AND 20 AND `final_charisma` BETWEEN 3 AND 20),
    CONSTRAINT `chk_character_creation_snapshot_hp` CHECK (`maximum_hit_points` > 0),
    CONSTRAINT `chk_character_creation_snapshot_subrace_pair` CHECK (
        (`subrace_type` IS NULL) = (`subrace_key` IS NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable confirmed level-one canonical-v2 creation snapshot';

CREATE TABLE `character_creation_selection_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `selection_kind` ENUM('ABILITY_BONUS', 'SKILL', 'SAVE', 'LANGUAGE', 'TOOL', 'STARTING_OPTION') NOT NULL,
    `selection_order` SMALLINT UNSIGNED NOT NULL,
    `selection_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`character_id`, `selection_kind`, `selection_key`),
    UNIQUE KEY `uq_character_creation_selection_order`
        (`character_id`, `selection_kind`, `selection_order`),
    CONSTRAINT `fk_character_creation_selection_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_creation_selection_order` CHECK (`selection_order` > 0),
    CONSTRAINT `chk_character_creation_selection_key` CHECK (
        REGEXP_LIKE(`selection_key`, '^[a-z][a-z0-9_]*([.][a-z0-9_]+)+$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Canonical ordered grants and bounded choices from level-one confirmation';

CREATE TABLE `character_resource_state_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `resource_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'character.resource',
    `resource_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `current_value` BIGINT NOT NULL,
    `maximum_value` BIGINT NOT NULL,
    PRIMARY KEY (`character_id`, `resource_key`),
    CONSTRAINT `fk_character_resource_state_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_resource_state_definition`
        FOREIGN KEY (`module_release_id`, `resource_type`, `resource_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_resource_state_bounds` CHECK (
        `maximum_value` > 0 AND `current_value` BETWEEN 0 AND `maximum_value`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Authoritative canonical-v2 character resource state';

-- Stable audit contract consumed by the repository; no event row is inserted here.
SET @level_one_creation_event_type = 'LEVEL_ONE_CHARACTER_CREATED';

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v011_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 11
  AND `script_name` = 'V011__complete_character_catalog_draft.sql'
  AND `script_sha256` =
      '0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb';

INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    12,
    'V012__level_one_character_creation.sql',
    '56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2',
    (SELECT CASE WHEN @v011_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Level-one character creation schema and canonical-v2 DRAFT rules'
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

-- Intentionally no campaign, character, snapshot, resource, event, operation,
-- account or grant row is inserted, and the DRAFT release remains unpublished.
