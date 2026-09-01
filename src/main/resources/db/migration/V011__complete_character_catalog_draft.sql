-- DnD Tool SE complete SRD 5.1 character catalog draft
--
-- Execute exactly once as the migrator after V010 has been recorded and verified.
-- This migration installs immutable DRAFT definitions only. It does not release
-- the module, change campaign defaults, create business state or grant accounts.
--
-- Do not add IF NOT EXISTS. Unexpected objects or prerequisites fail closed.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after LF normalization.

CREATE TABLE `module_catalog_definition_v2` (
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `definition_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `definition_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(120) NOT NULL,
    `description` VARCHAR(1000) NOT NULL,
    `sort_order` SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (`module_release_id`, `definition_type`, `definition_key`),
    UNIQUE KEY `uq_module_catalog_definition_v2_order`
        (`module_release_id`, `definition_type`, `sort_order`),
    CONSTRAINT `fk_module_catalog_definition_v2_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_module_catalog_definition_v2_type`
        CHECK (`definition_type` REGEXP '^[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+$'),
    CONSTRAINT `chk_module_catalog_definition_v2_key`
        CHECK (`definition_key` REGEXP '^[a-z][a-z0-9]*(\\.[a-z0-9_]+)+$'),
    CONSTRAINT `chk_module_catalog_definition_v2_order` CHECK (`sort_order` > 0),
    CONSTRAINT `chk_module_catalog_definition_v2_text`
        CHECK (`display_name` NOT REGEXP '[[:cntrl:]]'
           AND `description` NOT REGEXP '[[:cntrl:]]')
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Canonical-v2 typed immutable catalog definitions';

CREATE TABLE `module_catalog_attribute_v2` (
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `definition_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `definition_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `attribute_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `attribute_order` SMALLINT UNSIGNED NOT NULL,
    `value_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `text_value` VARCHAR(1000) NULL,
    `identifier_value` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `integer_value` BIGINT NULL,
    `decimal_value` DECIMAL(30, 10) NULL,
    `boolean_value` BOOLEAN NULL,
    PRIMARY KEY (
        `module_release_id`, `definition_type`, `definition_key`,
        `attribute_key`, `attribute_order`),
    CONSTRAINT `fk_module_catalog_attribute_v2_definition`
        FOREIGN KEY (`module_release_id`, `definition_type`, `definition_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_module_catalog_attribute_v2_key`
        CHECK (`attribute_key` REGEXP '^[a-z][a-z0-9]*(\\.[a-z0-9_]+)+$'),
    CONSTRAINT `chk_module_catalog_attribute_v2_order` CHECK (`attribute_order` > 0),
    CONSTRAINT `chk_module_catalog_attribute_v2_value` CHECK (
        (`value_type` = 'TEXT' AND `text_value` IS NOT NULL
            AND `identifier_value` IS NULL AND `integer_value` IS NULL
            AND `decimal_value` IS NULL AND `boolean_value` IS NULL)
        OR (`value_type` = 'IDENTIFIER' AND `text_value` IS NULL
            AND `identifier_value` IS NOT NULL AND `integer_value` IS NULL
            AND `decimal_value` IS NULL AND `boolean_value` IS NULL)
        OR (`value_type` = 'INTEGER' AND `text_value` IS NULL
            AND `identifier_value` IS NULL AND `integer_value` IS NOT NULL
            AND `decimal_value` IS NULL AND `boolean_value` IS NULL)
        OR (`value_type` = 'DECIMAL' AND `text_value` IS NULL
            AND `identifier_value` IS NULL AND `integer_value` IS NULL
            AND `decimal_value` IS NOT NULL AND `boolean_value` IS NULL)
        OR (`value_type` = 'BOOLEAN' AND `text_value` IS NULL
            AND `identifier_value` IS NULL AND `integer_value` IS NULL
            AND `decimal_value` IS NULL AND `boolean_value` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Canonical-v2 ordered typed definition attributes';

CREATE TABLE `module_catalog_relation_v2` (
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `source_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `source_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `relation_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `target_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `target_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `relation_order` SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (
        `module_release_id`, `source_type`, `source_key`,
        `relation_type`, `target_type`, `target_key`),
    UNIQUE KEY `uq_module_catalog_relation_v2_order` (
        `module_release_id`, `source_type`, `source_key`,
        `relation_type`, `relation_order`),
    KEY `ix_module_catalog_relation_v2_target`
        (`module_release_id`, `target_type`, `target_key`),
    CONSTRAINT `fk_module_catalog_relation_v2_source`
        FOREIGN KEY (`module_release_id`, `source_type`, `source_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_module_catalog_relation_v2_target`
        FOREIGN KEY (`module_release_id`, `target_type`, `target_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_module_catalog_relation_v2_type`
        CHECK (`relation_type` REGEXP '^[a-z][a-z0-9]*(\\.[a-z0-9_]+)+$'),
    CONSTRAINT `chk_module_catalog_relation_v2_order` CHECK (`relation_order` > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Canonical-v2 validated directed catalog relations';

INSERT INTO `module_release` (
    `module_key`, `release_version`, `canonical_format_version`,
    `hash_algorithm`, `content_sha256`, `release_status`, `released_at`
) VALUES (
    'dnd5e2014_srd51_se', '1', 2, 'SHA-256', NULL, 'DRAFT', NULL
);

SET @complete_release_id = LAST_INSERT_ID();

CREATE TEMPORARY TABLE `v011_character_seed` (
    `definition_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `definition_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(120) NOT NULL,
    `description` VARCHAR(1000) NOT NULL,
    `source_page` SMALLINT UNSIGNED NOT NULL,
    `minimum_level` TINYINT UNSIGNED NULL,
    `category` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `hit_die_sides` TINYINT UNSIGNED NULL,
    `recovery` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `minimum_strength` TINYINT UNSIGNED NULL,
    `primary_relation_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `target_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `target_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    PRIMARY KEY (`definition_type`, `definition_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `v011_character_seed` (
    `definition_type`, `definition_key`, `display_name`, `description`,
    `source_page`, `minimum_level`, `category`, `hit_die_sides`,
    `recovery`, `minimum_strength`, `primary_relation_type`,
    `target_type`, `target_key`
) VALUES
('character.race', 'race.dwarf', 'Dwarf', 'Dwarf is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.elf', 'Elf', 'Elf is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.halfling', 'Halfling', 'Halfling is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.human', 'Human', 'Human is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.dragonborn', 'Dragonborn', 'Dragonborn is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.gnome', 'Gnome', 'Gnome is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.half_elf', 'Half-Elf', 'Half-Elf is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.half_orc', 'Half-Orc', 'Half-Orc is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.race', 'race.tiefling', 'Tiefling', 'Tiefling is an SRD 5.1 race catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.subrace', 'subrace.hill_dwarf', 'Hill Dwarf', 'Hill Dwarf is an SRD 5.1 subrace catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, 'subrace.parent_race', 'character.race', 'race.dwarf'),
('character.subrace', 'subrace.high_elf', 'High Elf', 'High Elf is an SRD 5.1 subrace catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, 'subrace.parent_race', 'character.race', 'race.elf'),
('character.subrace', 'subrace.lightfoot', 'Lightfoot', 'Lightfoot is an SRD 5.1 subrace catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, 'subrace.parent_race', 'character.race', 'race.halfling'),
('character.subrace', 'subrace.rock_gnome', 'Rock Gnome', 'Rock Gnome is an SRD 5.1 subrace catalog entry.', '3', NULL, 'BASE', NULL, NULL, NULL, 'subrace.parent_race', 'character.race', 'race.gnome'),
('character.background', 'background.acolyte', 'Acolyte', 'Acolyte is an SRD 5.1 background catalog entry.', '60', NULL, 'SAMPLE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.common', 'Common', 'Common is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.dwarvish', 'Dwarvish', 'Dwarvish is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.elvish', 'Elvish', 'Elvish is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.giant', 'Giant', 'Giant is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.gnomish', 'Gnomish', 'Gnomish is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.goblin', 'Goblin', 'Goblin is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.halfling', 'Halfling', 'Halfling is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.orc', 'Orc', 'Orc is an SRD 5.1 language catalog entry.', '59', NULL, 'STANDARD', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.abyssal', 'Abyssal', 'Abyssal is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.celestial', 'Celestial', 'Celestial is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.draconic', 'Draconic', 'Draconic is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.deep_speech', 'Deep Speech', 'Deep Speech is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.infernal', 'Infernal', 'Infernal is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.primordial', 'Primordial', 'Primordial is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.sylvan', 'Sylvan', 'Sylvan is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.undercommon', 'Undercommon', 'Undercommon is an SRD 5.1 language catalog entry.', '59', NULL, 'EXOTIC', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.druidic', 'Druidic', 'Druidic is an SRD 5.1 language catalog entry.', '59', NULL, 'SECRET', NULL, NULL, NULL, NULL, NULL, NULL),
('character.language', 'language.thieves_cant', 'Thieves Cant', 'Thieves Cant is an SRD 5.1 language catalog entry.', '59', NULL, 'SECRET', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.alchemist_supplies', 'Alchemist Supplies', 'Alchemist Supplies is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.brewer_supplies', 'Brewer Supplies', 'Brewer Supplies is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.calligrapher_supplies', 'Calligrapher Supplies', 'Calligrapher Supplies is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.carpenter_tools', 'Carpenter Tools', 'Carpenter Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.cartographer_tools', 'Cartographer Tools', 'Cartographer Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.cobbler_tools', 'Cobbler Tools', 'Cobbler Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.cook_utensils', 'Cook Utensils', 'Cook Utensils is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.glassblower_tools', 'Glassblower Tools', 'Glassblower Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.jeweler_tools', 'Jeweler Tools', 'Jeweler Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.leatherworker_tools', 'Leatherworker Tools', 'Leatherworker Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.mason_tools', 'Mason Tools', 'Mason Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.painter_supplies', 'Painter Supplies', 'Painter Supplies is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.potter_tools', 'Potter Tools', 'Potter Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.smith_tools', 'Smith Tools', 'Smith Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.tinker_tools', 'Tinker Tools', 'Tinker Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.weaver_tools', 'Weaver Tools', 'Weaver Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.woodcarver_tools', 'Woodcarver Tools', 'Woodcarver Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'ARTISAN', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.disguise_kit', 'Disguise Kit', 'Disguise Kit is an SRD 5.1 tool catalog entry.', '70', NULL, 'KIT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.forgery_kit', 'Forgery Kit', 'Forgery Kit is an SRD 5.1 tool catalog entry.', '70', NULL, 'KIT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.herbalism_kit', 'Herbalism Kit', 'Herbalism Kit is an SRD 5.1 tool catalog entry.', '70', NULL, 'KIT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.poisoner_kit', 'Poisoner Kit', 'Poisoner Kit is an SRD 5.1 tool catalog entry.', '70', NULL, 'KIT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.dice_set', 'Dice Set', 'Dice Set is an SRD 5.1 tool catalog entry.', '70', NULL, 'GAMING_SET', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.playing_card_set', 'Playing Card Set', 'Playing Card Set is an SRD 5.1 tool catalog entry.', '70', NULL, 'GAMING_SET', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.bagpipes', 'Bagpipes', 'Bagpipes is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.drum', 'Drum', 'Drum is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.dulcimer', 'Dulcimer', 'Dulcimer is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.flute', 'Flute', 'Flute is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.lute', 'Lute', 'Lute is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.lyre', 'Lyre', 'Lyre is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.horn', 'Horn', 'Horn is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.pan_flute', 'Pan Flute', 'Pan Flute is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.shawm', 'Shawm', 'Shawm is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.viol', 'Viol', 'Viol is an SRD 5.1 tool catalog entry.', '70', NULL, 'MUSICAL_INSTRUMENT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.navigator_tools', 'Navigator Tools', 'Navigator Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'NAVIGATION', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.thieves_tools', 'Thieves Tools', 'Thieves Tools is an SRD 5.1 tool catalog entry.', '70', NULL, 'KIT', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.vehicles_land', 'Vehicles (Land)', 'Vehicles (Land) is an SRD 5.1 tool catalog entry.', '70', NULL, 'VEHICLE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.tool', 'tool.vehicles_water', 'Vehicles (Water)', 'Vehicles (Water) is an SRD 5.1 tool catalog entry.', '70', NULL, 'VEHICLE', NULL, NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.barbarian', 'Barbarian', 'Barbarian is an SRD 5.1 class catalog entry.', '8', NULL, 'BASE', '12', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.bard', 'Bard', 'Bard is an SRD 5.1 class catalog entry.', '11', NULL, 'BASE', '8', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.cleric', 'Cleric', 'Cleric is an SRD 5.1 class catalog entry.', '15', NULL, 'BASE', '8', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.druid', 'Druid', 'Druid is an SRD 5.1 class catalog entry.', '19', NULL, 'BASE', '8', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.fighter', 'Fighter', 'Fighter is an SRD 5.1 class catalog entry.', '24', NULL, 'BASE', '10', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.monk', 'Monk', 'Monk is an SRD 5.1 class catalog entry.', '26', NULL, 'BASE', '8', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.paladin', 'Paladin', 'Paladin is an SRD 5.1 class catalog entry.', '30', NULL, 'BASE', '10', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.ranger', 'Ranger', 'Ranger is an SRD 5.1 class catalog entry.', '35', NULL, 'BASE', '10', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.rogue', 'Rogue', 'Rogue is an SRD 5.1 class catalog entry.', '39', NULL, 'BASE', '8', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.sorcerer', 'Sorcerer', 'Sorcerer is an SRD 5.1 class catalog entry.', '42', NULL, 'BASE', '6', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.warlock', 'Warlock', 'Warlock is an SRD 5.1 class catalog entry.', '46', NULL, 'BASE', '8', NULL, NULL, NULL, NULL, NULL),
('character.class', 'class.wizard', 'Wizard', 'Wizard is an SRD 5.1 class catalog entry.', '52', NULL, 'BASE', '6', NULL, NULL, NULL, NULL, NULL),
('character.subclass', 'subclass.berserker', 'Path of the Berserker', 'Path of the Berserker is an SRD 5.1 subclass catalog entry.', '10', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.barbarian'),
('character.subclass', 'subclass.lore', 'College of Lore', 'College of Lore is an SRD 5.1 subclass catalog entry.', '14', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.bard'),
('character.subclass', 'subclass.life', 'Life Domain', 'Life Domain is an SRD 5.1 subclass catalog entry.', '17', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.cleric'),
('character.subclass', 'subclass.land', 'Circle of the Land', 'Circle of the Land is an SRD 5.1 subclass catalog entry.', '22', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.druid'),
('character.subclass', 'subclass.champion', 'Champion', 'Champion is an SRD 5.1 subclass catalog entry.', '25', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.fighter'),
('character.subclass', 'subclass.open_hand', 'Way of the Open Hand', 'Way of the Open Hand is an SRD 5.1 subclass catalog entry.', '29', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.monk'),
('character.subclass', 'subclass.devotion', 'Oath of Devotion', 'Oath of Devotion is an SRD 5.1 subclass catalog entry.', '33', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.paladin'),
('character.subclass', 'subclass.hunter', 'Hunter', 'Hunter is an SRD 5.1 subclass catalog entry.', '37', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.ranger'),
('character.subclass', 'subclass.thief', 'Thief', 'Thief is an SRD 5.1 subclass catalog entry.', '41', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.rogue'),
('character.subclass', 'subclass.draconic_bloodline', 'Draconic Bloodline', 'Draconic Bloodline is an SRD 5.1 subclass catalog entry.', '44', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.sorcerer'),
('character.subclass', 'subclass.fiend', 'The Fiend', 'The Fiend is an SRD 5.1 subclass catalog entry.', '50', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.warlock'),
('character.subclass', 'subclass.evocation', 'School of Evocation', 'School of Evocation is an SRD 5.1 subclass catalog entry.', '54', NULL, 'BASE', NULL, NULL, NULL, 'subclass.parent_class', 'character.class', 'class.wizard'),
('character.feat', 'feat.grappler', 'Grappler', 'Grappler is an SRD 5.1 feat catalog entry.', '74', NULL, 'OPTIONAL', NULL, NULL, '13', NULL, NULL, NULL),
('character.feature', 'feature.race.dwarf.darkvision', 'Darkvision', 'Darkvision is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.dwarf'),
('character.feature', 'feature.race.dwarf.dwarven_resilience', 'Dwarven Resilience', 'Dwarven Resilience is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.dwarf'),
('character.feature', 'feature.race.dwarf.dwarven_combat_training', 'Dwarven Combat Training', 'Dwarven Combat Training is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.dwarf'),
('character.feature', 'feature.race.dwarf.stonecunning', 'Stonecunning', 'Stonecunning is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.dwarf'),
('character.feature', 'feature.race.elf.darkvision', 'Darkvision', 'Darkvision is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.elf'),
('character.feature', 'feature.race.elf.keen_senses', 'Keen Senses', 'Keen Senses is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.elf'),
('character.feature', 'feature.race.elf.fey_ancestry', 'Fey Ancestry', 'Fey Ancestry is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.elf'),
('character.feature', 'feature.race.elf.trance', 'Trance', 'Trance is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.elf'),
('character.feature', 'feature.race.halfling.lucky', 'Lucky', 'Lucky is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.halfling'),
('character.feature', 'feature.race.halfling.brave', 'Brave', 'Brave is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.halfling'),
('character.feature', 'feature.race.halfling.halfling_nimbleness', 'Halfling Nimbleness', 'Halfling Nimbleness is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.halfling'),
('character.feature', 'feature.race.human.ability_score_increase', 'Ability Score Increase', 'Ability Score Increase is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.human'),
('character.feature', 'feature.race.human.extra_language', 'Extra Language', 'Extra Language is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.human'),
('character.feature', 'feature.race.dragonborn.draconic_ancestry', 'Draconic Ancestry', 'Draconic Ancestry is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.dragonborn'),
('character.feature', 'feature.race.dragonborn.breath_weapon', 'Breath Weapon', 'Breath Weapon is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.dragonborn'),
('character.feature', 'feature.race.dragonborn.damage_resistance', 'Damage Resistance', 'Damage Resistance is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.dragonborn'),
('character.feature', 'feature.race.gnome.darkvision', 'Darkvision', 'Darkvision is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.gnome'),
('character.feature', 'feature.race.gnome.gnome_cunning', 'Gnome Cunning', 'Gnome Cunning is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.gnome'),
('character.feature', 'feature.race.half_elf.darkvision', 'Darkvision', 'Darkvision is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_elf'),
('character.feature', 'feature.race.half_elf.fey_ancestry', 'Fey Ancestry', 'Fey Ancestry is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_elf'),
('character.feature', 'feature.race.half_elf.skill_versatility', 'Skill Versatility', 'Skill Versatility is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_elf'),
('character.feature', 'feature.race.half_elf.extra_language', 'Extra Language', 'Extra Language is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_elf'),
('character.feature', 'feature.race.half_orc.darkvision', 'Darkvision', 'Darkvision is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_orc'),
('character.feature', 'feature.race.half_orc.menacing', 'Menacing', 'Menacing is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_orc'),
('character.feature', 'feature.race.half_orc.relentless_endurance', 'Relentless Endurance', 'Relentless Endurance is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_orc'),
('character.feature', 'feature.race.half_orc.savage_attacks', 'Savage Attacks', 'Savage Attacks is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.half_orc'),
('character.feature', 'feature.race.tiefling.darkvision', 'Darkvision', 'Darkvision is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.tiefling'),
('character.feature', 'feature.race.tiefling.hellish_resistance', 'Hellish Resistance', 'Hellish Resistance is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.tiefling'),
('character.feature', 'feature.race.tiefling.infernal_legacy', 'Infernal Legacy', 'Infernal Legacy is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.tiefling'),
('character.feature', 'feature.race.tiefling.extra_language', 'Infernal Language', 'Infernal Language is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.race', 'race.tiefling'),
('character.feature', 'feature.subrace.hill_dwarf.dwarven_toughness', 'Dwarven Toughness', 'Dwarven Toughness is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.subrace', 'subrace.hill_dwarf'),
('character.feature', 'feature.subrace.high_elf.elf_weapon_training', 'Elf Weapon Training', 'Elf Weapon Training is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.subrace', 'subrace.high_elf'),
('character.feature', 'feature.subrace.high_elf.cantrip', 'Cantrip', 'Cantrip is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.subrace', 'subrace.high_elf'),
('character.feature', 'feature.subrace.high_elf.extra_language', 'Extra Language', 'Extra Language is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.subrace', 'subrace.high_elf'),
('character.feature', 'feature.subrace.lightfoot.naturally_stealthy', 'Naturally Stealthy', 'Naturally Stealthy is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.subrace', 'subrace.lightfoot'),
('character.feature', 'feature.subrace.rock_gnome.artificers_lore', 'Artificers Lore', 'Artificers Lore is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.subrace', 'subrace.rock_gnome'),
('character.feature', 'feature.subrace.rock_gnome.tinker', 'Tinker', 'Tinker is an SRD 5.1 feature catalog entry.', '3', '1', 'RACIAL', NULL, NULL, NULL, 'feature.owner', 'character.subrace', 'subrace.rock_gnome'),
('character.feature', 'feature.background.acolyte.shelter_of_the_faithful', 'Shelter of the Faithful', 'Shelter of the Faithful is an SRD 5.1 feature catalog entry.', '60', '1', 'BACKGROUND', NULL, NULL, NULL, 'feature.owner', 'character.background', 'background.acolyte'),
('character.feature', 'feature.barbarian.rage', 'Rage', 'Rage is an SRD 5.1 feature catalog entry.', '8', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.unarmored_defense', 'Unarmored Defense', 'Unarmored Defense is an SRD 5.1 feature catalog entry.', '8', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.reckless_attack', 'Reckless Attack', 'Reckless Attack is an SRD 5.1 feature catalog entry.', '8', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.danger_sense', 'Danger Sense', 'Danger Sense is an SRD 5.1 feature catalog entry.', '8', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.primal_path', 'Primal Path', 'Primal Path is an SRD 5.1 feature catalog entry.', '8', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '8', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.extra_attack', 'Extra Attack', 'Extra Attack is an SRD 5.1 feature catalog entry.', '8', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.fast_movement', 'Fast Movement', 'Fast Movement is an SRD 5.1 feature catalog entry.', '8', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.feral_instinct', 'Feral Instinct', 'Feral Instinct is an SRD 5.1 feature catalog entry.', '8', '7', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.brutal_critical', 'Brutal Critical', 'Brutal Critical is an SRD 5.1 feature catalog entry.', '8', '9', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.relentless_rage', 'Relentless Rage', 'Relentless Rage is an SRD 5.1 feature catalog entry.', '8', '11', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.persistent_rage', 'Persistent Rage', 'Persistent Rage is an SRD 5.1 feature catalog entry.', '8', '15', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.indomitable_might', 'Indomitable Might', 'Indomitable Might is an SRD 5.1 feature catalog entry.', '8', '18', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.barbarian.primal_champion', 'Primal Champion', 'Primal Champion is an SRD 5.1 feature catalog entry.', '8', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.barbarian'),
('character.feature', 'feature.bard.spellcasting', 'Spellcasting', 'Spellcasting is an SRD 5.1 feature catalog entry.', '11', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.bardic_inspiration', 'Bardic Inspiration', 'Bardic Inspiration is an SRD 5.1 feature catalog entry.', '11', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.jack_of_all_trades', 'Jack of All Trades', 'Jack of All Trades is an SRD 5.1 feature catalog entry.', '11', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.song_of_rest', 'Song of Rest', 'Song of Rest is an SRD 5.1 feature catalog entry.', '11', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.bard_college', 'Bard College', 'Bard College is an SRD 5.1 feature catalog entry.', '11', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.expertise', 'Expertise', 'Expertise is an SRD 5.1 feature catalog entry.', '11', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '11', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.font_of_inspiration', 'Font of Inspiration', 'Font of Inspiration is an SRD 5.1 feature catalog entry.', '11', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.countercharm', 'Countercharm', 'Countercharm is an SRD 5.1 feature catalog entry.', '11', '6', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.magical_secrets', 'Magical Secrets', 'Magical Secrets is an SRD 5.1 feature catalog entry.', '11', '10', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.bard.superior_inspiration', 'Superior Inspiration', 'Superior Inspiration is an SRD 5.1 feature catalog entry.', '11', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.bard'),
('character.feature', 'feature.cleric.spellcasting', 'Spellcasting', 'Spellcasting is an SRD 5.1 feature catalog entry.', '15', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.cleric'),
('character.feature', 'feature.cleric.divine_domain', 'Divine Domain', 'Divine Domain is an SRD 5.1 feature catalog entry.', '15', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.cleric'),
('character.feature', 'feature.cleric.channel_divinity', 'Channel Divinity', 'Channel Divinity is an SRD 5.1 feature catalog entry.', '15', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.cleric'),
('character.feature', 'feature.cleric.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '15', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.cleric'),
('character.feature', 'feature.cleric.destroy_undead', 'Destroy Undead', 'Destroy Undead is an SRD 5.1 feature catalog entry.', '15', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.cleric'),
('character.feature', 'feature.cleric.divine_intervention', 'Divine Intervention', 'Divine Intervention is an SRD 5.1 feature catalog entry.', '15', '10', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.cleric'),
('character.feature', 'feature.druid.druidic', 'Druidic', 'Druidic is an SRD 5.1 feature catalog entry.', '19', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.druid.spellcasting', 'Spellcasting', 'Spellcasting is an SRD 5.1 feature catalog entry.', '19', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.druid.wild_shape', 'Wild Shape', 'Wild Shape is an SRD 5.1 feature catalog entry.', '19', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.druid.druid_circle', 'Druid Circle', 'Druid Circle is an SRD 5.1 feature catalog entry.', '19', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.druid.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '19', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.druid.timeless_body', 'Timeless Body', 'Timeless Body is an SRD 5.1 feature catalog entry.', '19', '18', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.druid.beast_spells', 'Beast Spells', 'Beast Spells is an SRD 5.1 feature catalog entry.', '19', '18', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.druid.archdruid', 'Archdruid', 'Archdruid is an SRD 5.1 feature catalog entry.', '19', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.druid'),
('character.feature', 'feature.fighter.fighting_style', 'Fighting Style', 'Fighting Style is an SRD 5.1 feature catalog entry.', '24', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.fighter'),
('character.feature', 'feature.fighter.second_wind', 'Second Wind', 'Second Wind is an SRD 5.1 feature catalog entry.', '24', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.fighter'),
('character.feature', 'feature.fighter.action_surge', 'Action Surge', 'Action Surge is an SRD 5.1 feature catalog entry.', '24', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.fighter'),
('character.feature', 'feature.fighter.martial_archetype', 'Martial Archetype', 'Martial Archetype is an SRD 5.1 feature catalog entry.', '24', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.fighter'),
('character.feature', 'feature.fighter.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '24', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.fighter'),
('character.feature', 'feature.fighter.extra_attack', 'Extra Attack', 'Extra Attack is an SRD 5.1 feature catalog entry.', '24', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.fighter'),
('character.feature', 'feature.fighter.indomitable', 'Indomitable', 'Indomitable is an SRD 5.1 feature catalog entry.', '24', '9', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.fighter'),
('character.feature', 'feature.monk.unarmored_defense', 'Unarmored Defense', 'Unarmored Defense is an SRD 5.1 feature catalog entry.', '26', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.martial_arts', 'Martial Arts', 'Martial Arts is an SRD 5.1 feature catalog entry.', '26', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.ki', 'Ki', 'Ki is an SRD 5.1 feature catalog entry.', '26', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.unarmored_movement', 'Unarmored Movement', 'Unarmored Movement is an SRD 5.1 feature catalog entry.', '26', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.monastic_tradition', 'Monastic Tradition', 'Monastic Tradition is an SRD 5.1 feature catalog entry.', '26', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.deflect_missiles', 'Deflect Missiles', 'Deflect Missiles is an SRD 5.1 feature catalog entry.', '26', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '26', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.slow_fall', 'Slow Fall', 'Slow Fall is an SRD 5.1 feature catalog entry.', '26', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.extra_attack', 'Extra Attack', 'Extra Attack is an SRD 5.1 feature catalog entry.', '26', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.stunning_strike', 'Stunning Strike', 'Stunning Strike is an SRD 5.1 feature catalog entry.', '26', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.ki_empowered_strikes', 'Ki-Empowered Strikes', 'Ki-Empowered Strikes is an SRD 5.1 feature catalog entry.', '26', '6', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.evasion', 'Evasion', 'Evasion is an SRD 5.1 feature catalog entry.', '26', '7', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.stillness_of_mind', 'Stillness of Mind', 'Stillness of Mind is an SRD 5.1 feature catalog entry.', '26', '7', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.purity_of_body', 'Purity of Body', 'Purity of Body is an SRD 5.1 feature catalog entry.', '26', '10', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.tongue_of_the_sun_and_moon', 'Tongue of the Sun and Moon', 'Tongue of the Sun and Moon is an SRD 5.1 feature catalog entry.', '26', '13', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.diamond_soul', 'Diamond Soul', 'Diamond Soul is an SRD 5.1 feature catalog entry.', '26', '14', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.timeless_body', 'Timeless Body', 'Timeless Body is an SRD 5.1 feature catalog entry.', '26', '15', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.empty_body', 'Empty Body', 'Empty Body is an SRD 5.1 feature catalog entry.', '26', '18', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.monk.perfect_self', 'Perfect Self', 'Perfect Self is an SRD 5.1 feature catalog entry.', '26', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.monk'),
('character.feature', 'feature.paladin.divine_sense', 'Divine Sense', 'Divine Sense is an SRD 5.1 feature catalog entry.', '30', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.lay_on_hands', 'Lay on Hands', 'Lay on Hands is an SRD 5.1 feature catalog entry.', '30', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.fighting_style', 'Fighting Style', 'Fighting Style is an SRD 5.1 feature catalog entry.', '30', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.spellcasting', 'Spellcasting', 'Spellcasting is an SRD 5.1 feature catalog entry.', '30', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.divine_smite', 'Divine Smite', 'Divine Smite is an SRD 5.1 feature catalog entry.', '30', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.divine_health', 'Divine Health', 'Divine Health is an SRD 5.1 feature catalog entry.', '30', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.sacred_oath', 'Sacred Oath', 'Sacred Oath is an SRD 5.1 feature catalog entry.', '30', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '30', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.extra_attack', 'Extra Attack', 'Extra Attack is an SRD 5.1 feature catalog entry.', '30', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.aura_of_protection', 'Aura of Protection', 'Aura of Protection is an SRD 5.1 feature catalog entry.', '30', '6', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.aura_of_courage', 'Aura of Courage', 'Aura of Courage is an SRD 5.1 feature catalog entry.', '30', '10', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.improved_divine_smite', 'Improved Divine Smite', 'Improved Divine Smite is an SRD 5.1 feature catalog entry.', '30', '11', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.paladin.cleansing_touch', 'Cleansing Touch', 'Cleansing Touch is an SRD 5.1 feature catalog entry.', '30', '14', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.paladin'),
('character.feature', 'feature.ranger.favored_enemy', 'Favored Enemy', 'Favored Enemy is an SRD 5.1 feature catalog entry.', '35', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.natural_explorer', 'Natural Explorer', 'Natural Explorer is an SRD 5.1 feature catalog entry.', '35', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.fighting_style', 'Fighting Style', 'Fighting Style is an SRD 5.1 feature catalog entry.', '35', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.spellcasting', 'Spellcasting', 'Spellcasting is an SRD 5.1 feature catalog entry.', '35', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.ranger_archetype', 'Ranger Archetype', 'Ranger Archetype is an SRD 5.1 feature catalog entry.', '35', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.primeval_awareness', 'Primeval Awareness', 'Primeval Awareness is an SRD 5.1 feature catalog entry.', '35', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '35', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.extra_attack', 'Extra Attack', 'Extra Attack is an SRD 5.1 feature catalog entry.', '35', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.lands_stride', 'Lands Stride', 'Lands Stride is an SRD 5.1 feature catalog entry.', '35', '8', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.hide_in_plain_sight', 'Hide in Plain Sight', 'Hide in Plain Sight is an SRD 5.1 feature catalog entry.', '35', '10', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.vanish', 'Vanish', 'Vanish is an SRD 5.1 feature catalog entry.', '35', '14', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.feral_senses', 'Feral Senses', 'Feral Senses is an SRD 5.1 feature catalog entry.', '35', '18', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.ranger.foe_slayer', 'Foe Slayer', 'Foe Slayer is an SRD 5.1 feature catalog entry.', '35', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.ranger'),
('character.feature', 'feature.rogue.expertise', 'Expertise', 'Expertise is an SRD 5.1 feature catalog entry.', '39', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.sneak_attack', 'Sneak Attack', 'Sneak Attack is an SRD 5.1 feature catalog entry.', '39', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.thieves_cant', 'Thieves Cant', 'Thieves Cant is an SRD 5.1 feature catalog entry.', '39', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.cunning_action', 'Cunning Action', 'Cunning Action is an SRD 5.1 feature catalog entry.', '39', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.roguish_archetype', 'Roguish Archetype', 'Roguish Archetype is an SRD 5.1 feature catalog entry.', '39', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '39', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.uncanny_dodge', 'Uncanny Dodge', 'Uncanny Dodge is an SRD 5.1 feature catalog entry.', '39', '5', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.evasion', 'Evasion', 'Evasion is an SRD 5.1 feature catalog entry.', '39', '7', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.reliable_talent', 'Reliable Talent', 'Reliable Talent is an SRD 5.1 feature catalog entry.', '39', '11', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.blindsense', 'Blindsense', 'Blindsense is an SRD 5.1 feature catalog entry.', '39', '14', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.slippery_mind', 'Slippery Mind', 'Slippery Mind is an SRD 5.1 feature catalog entry.', '39', '15', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.elusive', 'Elusive', 'Elusive is an SRD 5.1 feature catalog entry.', '39', '18', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.rogue.stroke_of_luck', 'Stroke of Luck', 'Stroke of Luck is an SRD 5.1 feature catalog entry.', '39', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.rogue'),
('character.feature', 'feature.sorcerer.spellcasting', 'Spellcasting', 'Spellcasting is an SRD 5.1 feature catalog entry.', '42', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.sorcerous_origin', 'Sorcerous Origin', 'Sorcerous Origin is an SRD 5.1 feature catalog entry.', '42', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.font_of_magic', 'Font of Magic', 'Font of Magic is an SRD 5.1 feature catalog entry.', '42', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic', 'Metamagic', 'Metamagic is an SRD 5.1 feature catalog entry.', '42', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '42', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.sorcerous_restoration', 'Sorcerous Restoration', 'Sorcerous Restoration is an SRD 5.1 feature catalog entry.', '42', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.warlock.otherworldly_patron', 'Otherworldly Patron', 'Otherworldly Patron is an SRD 5.1 feature catalog entry.', '46', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.pact_magic', 'Pact Magic', 'Pact Magic is an SRD 5.1 feature catalog entry.', '46', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.eldritch_invocations', 'Eldritch Invocations', 'Eldritch Invocations is an SRD 5.1 feature catalog entry.', '46', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.pact_boon', 'Pact Boon', 'Pact Boon is an SRD 5.1 feature catalog entry.', '46', '3', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '46', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.mystic_arcanum', 'Mystic Arcanum', 'Mystic Arcanum is an SRD 5.1 feature catalog entry.', '46', '11', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.eldritch_master', 'Eldritch Master', 'Eldritch Master is an SRD 5.1 feature catalog entry.', '46', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.wizard.spellcasting', 'Spellcasting', 'Spellcasting is an SRD 5.1 feature catalog entry.', '52', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.wizard'),
('character.feature', 'feature.wizard.arcane_recovery', 'Arcane Recovery', 'Arcane Recovery is an SRD 5.1 feature catalog entry.', '52', '1', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.wizard'),
('character.feature', 'feature.wizard.arcane_tradition', 'Arcane Tradition', 'Arcane Tradition is an SRD 5.1 feature catalog entry.', '52', '2', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.wizard'),
('character.feature', 'feature.wizard.ability_score_improvement', 'Ability Score Improvement', 'Ability Score Improvement is an SRD 5.1 feature catalog entry.', '52', '4', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.wizard'),
('character.feature', 'feature.wizard.spell_mastery', 'Spell Mastery', 'Spell Mastery is an SRD 5.1 feature catalog entry.', '52', '18', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.wizard'),
('character.feature', 'feature.wizard.signature_spells', 'Signature Spells', 'Signature Spells is an SRD 5.1 feature catalog entry.', '52', '20', 'BASE', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.wizard'),
('character.feature', 'feature.berserker.frenzy', 'Frenzy', 'Frenzy is an SRD 5.1 feature catalog entry.', '10', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.berserker'),
('character.feature', 'feature.berserker.mindless_rage', 'Mindless Rage', 'Mindless Rage is an SRD 5.1 feature catalog entry.', '10', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.berserker'),
('character.feature', 'feature.berserker.intimidating_presence', 'Intimidating Presence', 'Intimidating Presence is an SRD 5.1 feature catalog entry.', '10', '10', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.berserker'),
('character.feature', 'feature.berserker.retaliation', 'Retaliation', 'Retaliation is an SRD 5.1 feature catalog entry.', '10', '14', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.berserker'),
('character.feature', 'feature.lore.bonus_proficiencies', 'Bonus Proficiencies', 'Bonus Proficiencies is an SRD 5.1 feature catalog entry.', '14', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.lore'),
('character.feature', 'feature.lore.cutting_words', 'Cutting Words', 'Cutting Words is an SRD 5.1 feature catalog entry.', '14', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.lore'),
('character.feature', 'feature.lore.additional_magical_secrets', 'Additional Magical Secrets', 'Additional Magical Secrets is an SRD 5.1 feature catalog entry.', '14', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.lore'),
('character.feature', 'feature.lore.peerless_skill', 'Peerless Skill', 'Peerless Skill is an SRD 5.1 feature catalog entry.', '14', '14', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.lore'),
('character.feature', 'feature.life.bonus_proficiency', 'Bonus Proficiency', 'Bonus Proficiency is an SRD 5.1 feature catalog entry.', '17', '1', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.life'),
('character.feature', 'feature.life.disciple_of_life', 'Disciple of Life', 'Disciple of Life is an SRD 5.1 feature catalog entry.', '17', '1', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.life'),
('character.feature', 'feature.life.preserve_life', 'Preserve Life', 'Preserve Life is an SRD 5.1 feature catalog entry.', '17', '2', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.life'),
('character.feature', 'feature.life.blessed_healer', 'Blessed Healer', 'Blessed Healer is an SRD 5.1 feature catalog entry.', '17', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.life'),
('character.feature', 'feature.life.divine_strike', 'Divine Strike', 'Divine Strike is an SRD 5.1 feature catalog entry.', '17', '8', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.life'),
('character.feature', 'feature.life.supreme_healing', 'Supreme Healing', 'Supreme Healing is an SRD 5.1 feature catalog entry.', '17', '17', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.life'),
('character.feature', 'feature.land.bonus_cantrip', 'Bonus Cantrip', 'Bonus Cantrip is an SRD 5.1 feature catalog entry.', '22', '2', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.land'),
('character.feature', 'feature.land.natural_recovery', 'Natural Recovery', 'Natural Recovery is an SRD 5.1 feature catalog entry.', '22', '2', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.land'),
('character.feature', 'feature.land.circle_spells', 'Circle Spells', 'Circle Spells is an SRD 5.1 feature catalog entry.', '22', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.land'),
('character.feature', 'feature.land.lands_stride', 'Lands Stride', 'Lands Stride is an SRD 5.1 feature catalog entry.', '22', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.land'),
('character.feature', 'feature.land.natures_ward', 'Natures Ward', 'Natures Ward is an SRD 5.1 feature catalog entry.', '22', '10', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.land'),
('character.feature', 'feature.land.natures_sanctuary', 'Natures Sanctuary', 'Natures Sanctuary is an SRD 5.1 feature catalog entry.', '22', '14', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.land'),
('character.feature', 'feature.champion.improved_critical', 'Improved Critical', 'Improved Critical is an SRD 5.1 feature catalog entry.', '25', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.champion'),
('character.feature', 'feature.champion.remarkable_athlete', 'Remarkable Athlete', 'Remarkable Athlete is an SRD 5.1 feature catalog entry.', '25', '7', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.champion'),
('character.feature', 'feature.champion.additional_fighting_style', 'Additional Fighting Style', 'Additional Fighting Style is an SRD 5.1 feature catalog entry.', '25', '10', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.champion'),
('character.feature', 'feature.champion.superior_critical', 'Superior Critical', 'Superior Critical is an SRD 5.1 feature catalog entry.', '25', '15', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.champion'),
('character.feature', 'feature.champion.survivor', 'Survivor', 'Survivor is an SRD 5.1 feature catalog entry.', '25', '18', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.champion'),
('character.feature', 'feature.open_hand.open_hand_technique', 'Open Hand Technique', 'Open Hand Technique is an SRD 5.1 feature catalog entry.', '29', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.open_hand'),
('character.feature', 'feature.open_hand.wholeness_of_body', 'Wholeness of Body', 'Wholeness of Body is an SRD 5.1 feature catalog entry.', '29', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.open_hand'),
('character.feature', 'feature.open_hand.tranquility', 'Tranquility', 'Tranquility is an SRD 5.1 feature catalog entry.', '29', '11', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.open_hand'),
('character.feature', 'feature.open_hand.quivering_palm', 'Quivering Palm', 'Quivering Palm is an SRD 5.1 feature catalog entry.', '29', '17', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.open_hand'),
('character.feature', 'feature.devotion.oath_spells', 'Oath Spells', 'Oath Spells is an SRD 5.1 feature catalog entry.', '33', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.devotion'),
('character.feature', 'feature.devotion.sacred_weapon', 'Sacred Weapon', 'Sacred Weapon is an SRD 5.1 feature catalog entry.', '33', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.devotion'),
('character.feature', 'feature.devotion.turn_the_unholy', 'Turn the Unholy', 'Turn the Unholy is an SRD 5.1 feature catalog entry.', '33', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.devotion'),
('character.feature', 'feature.devotion.aura_of_devotion', 'Aura of Devotion', 'Aura of Devotion is an SRD 5.1 feature catalog entry.', '33', '7', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.devotion'),
('character.feature', 'feature.devotion.purity_of_spirit', 'Purity of Spirit', 'Purity of Spirit is an SRD 5.1 feature catalog entry.', '33', '15', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.devotion'),
('character.feature', 'feature.devotion.holy_nimbus', 'Holy Nimbus', 'Holy Nimbus is an SRD 5.1 feature catalog entry.', '33', '20', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.devotion'),
('character.feature', 'feature.hunter.hunters_prey', 'Hunters Prey', 'Hunters Prey is an SRD 5.1 feature catalog entry.', '37', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.defensive_tactics', 'Defensive Tactics', 'Defensive Tactics is an SRD 5.1 feature catalog entry.', '37', '7', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.multiattack', 'Multiattack', 'Multiattack is an SRD 5.1 feature catalog entry.', '37', '11', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.superior_hunters_defense', 'Superior Hunters Defense', 'Superior Hunters Defense is an SRD 5.1 feature catalog entry.', '37', '15', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.colossus_slayer', 'Colossus Slayer', 'Colossus Slayer is an SRD 5.1 feature catalog entry.', '37', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.giant_killer', 'Giant Killer', 'Giant Killer is an SRD 5.1 feature catalog entry.', '37', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.horde_breaker', 'Horde Breaker', 'Horde Breaker is an SRD 5.1 feature catalog entry.', '37', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.escape_the_horde', 'Escape the Horde', 'Escape the Horde is an SRD 5.1 feature catalog entry.', '37', '7', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.multiattack_defense', 'Multiattack Defense', 'Multiattack Defense is an SRD 5.1 feature catalog entry.', '37', '7', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.steel_will', 'Steel Will', 'Steel Will is an SRD 5.1 feature catalog entry.', '37', '7', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.volley', 'Volley', 'Volley is an SRD 5.1 feature catalog entry.', '37', '11', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.whirlwind_attack', 'Whirlwind Attack', 'Whirlwind Attack is an SRD 5.1 feature catalog entry.', '37', '11', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.evasion', 'Evasion', 'Evasion is an SRD 5.1 feature catalog entry.', '37', '15', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.stand_against_the_tide', 'Stand Against the Tide', 'Stand Against the Tide is an SRD 5.1 feature catalog entry.', '37', '15', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.hunter.uncanny_dodge', 'Uncanny Dodge', 'Uncanny Dodge is an SRD 5.1 feature catalog entry.', '37', '15', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.hunter'),
('character.feature', 'feature.thief.fast_hands', 'Fast Hands', 'Fast Hands is an SRD 5.1 feature catalog entry.', '41', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.thief'),
('character.feature', 'feature.thief.second_story_work', 'Second-Story Work', 'Second-Story Work is an SRD 5.1 feature catalog entry.', '41', '3', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.thief'),
('character.feature', 'feature.thief.supreme_sneak', 'Supreme Sneak', 'Supreme Sneak is an SRD 5.1 feature catalog entry.', '41', '9', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.thief'),
('character.feature', 'feature.thief.use_magic_device', 'Use Magic Device', 'Use Magic Device is an SRD 5.1 feature catalog entry.', '41', '13', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.thief'),
('character.feature', 'feature.thief.thiefs_reflexes', 'Thiefs Reflexes', 'Thiefs Reflexes is an SRD 5.1 feature catalog entry.', '41', '17', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.thief'),
('character.feature', 'feature.draconic_bloodline.dragon_ancestor', 'Dragon Ancestor', 'Dragon Ancestor is an SRD 5.1 feature catalog entry.', '44', '1', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.draconic_bloodline'),
('character.feature', 'feature.draconic_bloodline.draconic_resilience', 'Draconic Resilience', 'Draconic Resilience is an SRD 5.1 feature catalog entry.', '44', '1', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.draconic_bloodline'),
('character.feature', 'feature.draconic_bloodline.elemental_affinity', 'Elemental Affinity', 'Elemental Affinity is an SRD 5.1 feature catalog entry.', '44', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.draconic_bloodline'),
('character.feature', 'feature.draconic_bloodline.dragon_wings', 'Dragon Wings', 'Dragon Wings is an SRD 5.1 feature catalog entry.', '44', '14', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.draconic_bloodline'),
('character.feature', 'feature.draconic_bloodline.draconic_presence', 'Draconic Presence', 'Draconic Presence is an SRD 5.1 feature catalog entry.', '44', '18', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.draconic_bloodline'),
('character.feature', 'feature.fiend.expanded_spell_list', 'Expanded Spell List', 'Expanded Spell List is an SRD 5.1 feature catalog entry.', '50', '1', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.fiend'),
('character.feature', 'feature.fiend.dark_ones_blessing', 'Dark Ones Blessing', 'Dark Ones Blessing is an SRD 5.1 feature catalog entry.', '50', '1', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.fiend'),
('character.feature', 'feature.fiend.dark_ones_own_luck', 'Dark Ones Own Luck', 'Dark Ones Own Luck is an SRD 5.1 feature catalog entry.', '50', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.fiend'),
('character.feature', 'feature.fiend.fiendish_resilience', 'Fiendish Resilience', 'Fiendish Resilience is an SRD 5.1 feature catalog entry.', '50', '10', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.fiend'),
('character.feature', 'feature.fiend.hurl_through_hell', 'Hurl Through Hell', 'Hurl Through Hell is an SRD 5.1 feature catalog entry.', '50', '14', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.fiend'),
('character.feature', 'feature.evocation.evocation_savant', 'Evocation Savant', 'Evocation Savant is an SRD 5.1 feature catalog entry.', '54', '2', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.evocation'),
('character.feature', 'feature.evocation.sculpt_spells', 'Sculpt Spells', 'Sculpt Spells is an SRD 5.1 feature catalog entry.', '54', '2', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.evocation'),
('character.feature', 'feature.evocation.potent_cantrip', 'Potent Cantrip', 'Potent Cantrip is an SRD 5.1 feature catalog entry.', '54', '6', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.evocation'),
('character.feature', 'feature.evocation.empowered_evocation', 'Empowered Evocation', 'Empowered Evocation is an SRD 5.1 feature catalog entry.', '54', '10', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.evocation'),
('character.feature', 'feature.evocation.overchannel', 'Overchannel', 'Overchannel is an SRD 5.1 feature catalog entry.', '54', '14', 'SUBCLASS', NULL, NULL, NULL, 'feature.owner', 'character.subclass', 'subclass.evocation'),
('character.feature', 'feature.sorcerer.metamagic.careful_spell', 'Careful Spell', 'Careful Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic.distant_spell', 'Distant Spell', 'Distant Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic.empowered_spell', 'Empowered Spell', 'Empowered Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic.extended_spell', 'Extended Spell', 'Extended Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic.heightened_spell', 'Heightened Spell', 'Heightened Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic.quickened_spell', 'Quickened Spell', 'Quickened Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic.subtle_spell', 'Subtle Spell', 'Subtle Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.sorcerer.metamagic.twinned_spell', 'Twinned Spell', 'Twinned Spell is an SRD 5.1 feature catalog entry.', '43', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.sorcerer'),
('character.feature', 'feature.warlock.pact_boon.chain', 'Pact of the Chain', 'Pact of the Chain is an SRD 5.1 feature catalog entry.', '47', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.pact_boon.blade', 'Pact of the Blade', 'Pact of the Blade is an SRD 5.1 feature catalog entry.', '47', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.pact_boon.tome', 'Pact of the Tome', 'Pact of the Tome is an SRD 5.1 feature catalog entry.', '47', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.agonizing_blast', 'Agonizing Blast', 'Agonizing Blast is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.armor_of_shadows', 'Armor of Shadows', 'Armor of Shadows is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.ascendant_step', 'Ascendant Step', 'Ascendant Step is an SRD 5.1 feature catalog entry.', '48', '9', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.beast_speech', 'Beast Speech', 'Beast Speech is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.beguiling_influence', 'Beguiling Influence', 'Beguiling Influence is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.bewitching_whispers', 'Bewitching Whispers', 'Bewitching Whispers is an SRD 5.1 feature catalog entry.', '48', '7', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.book_of_ancient_secrets', 'Book of Ancient Secrets', 'Book of Ancient Secrets is an SRD 5.1 feature catalog entry.', '48', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.chains_of_carceri', 'Chains of Carceri', 'Chains of Carceri is an SRD 5.1 feature catalog entry.', '48', '15', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.devils_sight', 'Devils Sight', 'Devils Sight is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.dreadful_word', 'Dreadful Word', 'Dreadful Word is an SRD 5.1 feature catalog entry.', '48', '7', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.eldritch_sight', 'Eldritch Sight', 'Eldritch Sight is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.eldritch_spear', 'Eldritch Spear', 'Eldritch Spear is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.eyes_of_the_rune_keeper', 'Eyes of the Rune Keeper', 'Eyes of the Rune Keeper is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.fiendish_vigor', 'Fiendish Vigor', 'Fiendish Vigor is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.gaze_of_two_minds', 'Gaze of Two Minds', 'Gaze of Two Minds is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.lifedrinker', 'Lifedrinker', 'Lifedrinker is an SRD 5.1 feature catalog entry.', '48', '12', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.mask_of_many_faces', 'Mask of Many Faces', 'Mask of Many Faces is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.master_of_myriad_forms', 'Master of Myriad Forms', 'Master of Myriad Forms is an SRD 5.1 feature catalog entry.', '48', '15', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.minions_of_chaos', 'Minions of Chaos', 'Minions of Chaos is an SRD 5.1 feature catalog entry.', '48', '9', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.mire_the_mind', 'Mire the Mind', 'Mire the Mind is an SRD 5.1 feature catalog entry.', '48', '5', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.misty_visions', 'Misty Visions', 'Misty Visions is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.one_with_shadows', 'One with Shadows', 'One with Shadows is an SRD 5.1 feature catalog entry.', '48', '5', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.otherworldly_leap', 'Otherworldly Leap', 'Otherworldly Leap is an SRD 5.1 feature catalog entry.', '48', '9', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.repelling_blast', 'Repelling Blast', 'Repelling Blast is an SRD 5.1 feature catalog entry.', '48', '2', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.sculptor_of_flesh', 'Sculptor of Flesh', 'Sculptor of Flesh is an SRD 5.1 feature catalog entry.', '48', '7', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.sign_of_ill_omen', 'Sign of Ill Omen', 'Sign of Ill Omen is an SRD 5.1 feature catalog entry.', '48', '5', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.thief_of_five_fates', 'Thief of Five Fates', 'Thief of Five Fates is an SRD 5.1 feature catalog entry.', '48', '5', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.thirsting_blade', 'Thirsting Blade', 'Thirsting Blade is an SRD 5.1 feature catalog entry.', '48', '5', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.visions_of_distant_realms', 'Visions of Distant Realms', 'Visions of Distant Realms is an SRD 5.1 feature catalog entry.', '48', '15', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.voice_of_the_chain_master', 'Voice of the Chain Master', 'Voice of the Chain Master is an SRD 5.1 feature catalog entry.', '48', '3', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.whispers_of_the_grave', 'Whispers of the Grave', 'Whispers of the Grave is an SRD 5.1 feature catalog entry.', '48', '9', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.feature', 'feature.warlock.invocation.witch_sight', 'Witch Sight', 'Witch Sight is an SRD 5.1 feature catalog entry.', '48', '15', 'OPTION', NULL, NULL, NULL, 'feature.owner', 'character.class', 'class.warlock'),
('character.resource', 'resource.barbarian.rage', 'Rage Uses', 'Rage Uses is an SRD 5.1 resource catalog entry.', '8', NULL, 'CLASS', NULL, 'LONG_REST', NULL, 'resource.owner', 'character.class', 'class.barbarian'),
('character.resource', 'resource.bard.bardic_inspiration', 'Bardic Inspiration Uses', 'Bardic Inspiration Uses is an SRD 5.1 resource catalog entry.', '11', NULL, 'CLASS', NULL, 'SPECIAL', NULL, 'resource.owner', 'character.class', 'class.bard'),
('character.resource', 'resource.cleric.channel_divinity', 'Cleric Channel Divinity Uses', 'Cleric Channel Divinity Uses is an SRD 5.1 resource catalog entry.', '15', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.cleric'),
('character.resource', 'resource.druid.wild_shape', 'Wild Shape Uses', 'Wild Shape Uses is an SRD 5.1 resource catalog entry.', '19', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.druid'),
('character.resource', 'resource.fighter.second_wind', 'Second Wind Uses', 'Second Wind Uses is an SRD 5.1 resource catalog entry.', '24', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.fighter'),
('character.resource', 'resource.fighter.action_surge', 'Action Surge Uses', 'Action Surge Uses is an SRD 5.1 resource catalog entry.', '24', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.fighter'),
('character.resource', 'resource.fighter.indomitable', 'Indomitable Uses', 'Indomitable Uses is an SRD 5.1 resource catalog entry.', '24', NULL, 'CLASS', NULL, 'LONG_REST', NULL, 'resource.owner', 'character.class', 'class.fighter'),
('character.resource', 'resource.monk.ki', 'Ki Points', 'Ki Points is an SRD 5.1 resource catalog entry.', '26', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.monk'),
('character.resource', 'resource.paladin.divine_sense', 'Divine Sense Uses', 'Divine Sense Uses is an SRD 5.1 resource catalog entry.', '30', NULL, 'CLASS', NULL, 'LONG_REST', NULL, 'resource.owner', 'character.class', 'class.paladin'),
('character.resource', 'resource.paladin.lay_on_hands', 'Lay on Hands Pool', 'Lay on Hands Pool is an SRD 5.1 resource catalog entry.', '30', NULL, 'CLASS', NULL, 'LONG_REST', NULL, 'resource.owner', 'character.class', 'class.paladin'),
('character.resource', 'resource.paladin.channel_divinity', 'Paladin Channel Divinity Uses', 'Paladin Channel Divinity Uses is an SRD 5.1 resource catalog entry.', '33', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.paladin'),
('character.resource', 'resource.rogue.stroke_of_luck', 'Stroke of Luck Uses', 'Stroke of Luck Uses is an SRD 5.1 resource catalog entry.', '39', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.rogue'),
('character.resource', 'resource.sorcerer.sorcery_points', 'Sorcery Points', 'Sorcery Points is an SRD 5.1 resource catalog entry.', '42', NULL, 'CLASS', NULL, 'LONG_REST', NULL, 'resource.owner', 'character.class', 'class.sorcerer'),
('character.resource', 'resource.warlock.pact_magic', 'Pact Magic Slots', 'Pact Magic Slots is an SRD 5.1 resource catalog entry.', '46', NULL, 'CLASS', NULL, 'SHORT_REST', NULL, 'resource.owner', 'character.class', 'class.warlock'),
('character.resource', 'resource.warlock.mystic_arcanum', 'Mystic Arcanum Uses', 'Mystic Arcanum Uses is an SRD 5.1 resource catalog entry.', '46', NULL, 'CLASS', NULL, 'LONG_REST', NULL, 'resource.owner', 'character.class', 'class.warlock'),
('character.resource', 'resource.wizard.arcane_recovery', 'Arcane Recovery Uses', 'Arcane Recovery Uses is an SRD 5.1 resource catalog entry.', '52', NULL, 'CLASS', NULL, 'LONG_REST', NULL, 'resource.owner', 'character.class', 'class.wizard');

INSERT INTO `module_catalog_definition_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `display_name`, `description`, `sort_order`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       `display_name`, `description`,
       ROW_NUMBER() OVER (
           PARTITION BY `definition_type` ORDER BY `definition_key`)
FROM `v011_character_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `attribute_key`, `attribute_order`, `value_type`, `integer_value`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       'source.page', 1, 'INTEGER', `source_page`
FROM `v011_character_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `attribute_key`, `attribute_order`, `value_type`, `identifier_value`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       'catalog.category', 1, 'IDENTIFIER', `category`
FROM `v011_character_seed`
WHERE `category` IS NOT NULL;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `attribute_key`, `attribute_order`, `value_type`, `integer_value`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       'class.hit_die_sides', 1, 'INTEGER', `hit_die_sides`
FROM `v011_character_seed`
WHERE `hit_die_sides` IS NOT NULL;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `attribute_key`, `attribute_order`, `value_type`, `integer_value`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       'feature.level', 1, 'INTEGER', `minimum_level`
FROM `v011_character_seed`
WHERE `minimum_level` IS NOT NULL;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `attribute_key`, `attribute_order`, `value_type`, `identifier_value`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       'resource.recovery', 1, 'IDENTIFIER', `recovery`
FROM `v011_character_seed`
WHERE `recovery` IS NOT NULL;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`,
    `attribute_key`, `attribute_order`, `value_type`, `integer_value`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       'feat.minimum_strength', 1, 'INTEGER', `minimum_strength`
FROM `v011_character_seed`
WHERE `minimum_strength` IS NOT NULL;

INSERT INTO `module_catalog_relation_v2` (
    `module_release_id`, `source_type`, `source_key`, `relation_type`,
    `target_type`, `target_key`, `relation_order`
)
SELECT @complete_release_id, `definition_type`, `definition_key`,
       `primary_relation_type`, `target_type`, `target_key`, 1
FROM `v011_character_seed`
WHERE `primary_relation_type` IS NOT NULL;

INSERT INTO `module_catalog_relation_v2` (
    `module_release_id`, `source_type`, `source_key`, `relation_type`,
    `target_type`, `target_key`, `relation_order`
) VALUES
(@complete_release_id, 'character.feature', 'feature.warlock.invocation.book_of_ancient_secrets', 'feature.prerequisite', 'character.feature', 'feature.warlock.pact_boon.tome', 1),
(@complete_release_id, 'character.feature', 'feature.warlock.invocation.chains_of_carceri', 'feature.prerequisite', 'character.feature', 'feature.warlock.pact_boon.chain', 1),
(@complete_release_id, 'character.feature', 'feature.warlock.invocation.lifedrinker', 'feature.prerequisite', 'character.feature', 'feature.warlock.pact_boon.blade', 1),
(@complete_release_id, 'character.feature', 'feature.warlock.invocation.thirsting_blade', 'feature.prerequisite', 'character.feature', 'feature.warlock.pact_boon.blade', 1),
(@complete_release_id, 'character.feature', 'feature.warlock.invocation.voice_of_the_chain_master', 'feature.prerequisite', 'character.feature', 'feature.warlock.pact_boon.chain', 1);

DROP TEMPORARY TABLE `v011_character_seed`;

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v010_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 10
  AND `script_name` = 'V010__stage3_node_encounter_schema.sql'
  AND `script_sha256` =
      'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87';

INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    11,
    'V011__complete_character_catalog_draft.sql',
    '0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb',
    (SELECT CASE WHEN @v010_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Complete SRD 5.1 character catalog and canonical-v2 draft schema'
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

-- Intentionally no campaign, character, event, operation, account or grant rows
-- are inserted, and the DRAFT release is not eligible for business binding.
