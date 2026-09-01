-- DnD Tool SE stage 2 character and internal event schema
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped and only after V004__release_builtin_module.sql has
-- succeeded. This migration creates empty runtime tables only; it does not
-- create a character, event, campaign, account, grant, or public projection.
--
-- Do not add IF NOT EXISTS. An unexpected object or missing prerequisite must
-- stop the migration instead of hiding schema drift.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

-- This candidate key lets a character freeze all five campaign-module values
-- in one foreign key. campaign_id is already the primary key, so the binding
-- remains exactly one row per campaign.
ALTER TABLE `campaign_module`
    ADD UNIQUE KEY `uq_campaign_module_frozen_binding` (
        `campaign_id`, `module_release_id`, `frozen_module_key`,
        `frozen_release_version`, `frozen_content_sha256`
    );

-- PC and NPC share this aggregate root. Names are intentionally not unique;
-- Java performs TRIM_THEN_NFC and full control-character validation before
-- every write, while these checks preserve the most important storage bounds.
CREATE TABLE `character_record` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `character_key` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `character_type` ENUM('PC', 'NPC') NOT NULL,
    `character_name` VARCHAR(80) NOT NULL,
    `character_status` ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    `saved_module_key`
        VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `saved_release_version`
        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `saved_content_sha256`
        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_character_record_key` (`character_key`),
    UNIQUE KEY `uq_character_record_id_campaign` (`id`, `campaign_id`),
    UNIQUE KEY `uq_character_record_id_release` (`id`, `module_release_id`),
    KEY `ix_character_record_campaign_status`
        (`campaign_id`, `character_status`, `id`),
    CONSTRAINT `fk_character_record_frozen_binding`
        FOREIGN KEY (
            `campaign_id`, `module_release_id`, `saved_module_key`,
            `saved_release_version`, `saved_content_sha256`
        ) REFERENCES `campaign_module` (
            `campaign_id`, `module_release_id`, `frozen_module_key`,
            `frozen_release_version`, `frozen_content_sha256`
        ) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_record_key`
        CHECK (REGEXP_LIKE(`character_key`,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
            'c')),
    CONSTRAINT `chk_character_record_name`
        CHECK (CHAR_LENGTH(TRIM(`character_name`)) BETWEEN 1 AND 80),
    CONSTRAINT `chk_character_record_sha256`
        CHECK (REGEXP_LIKE(`saved_content_sha256`, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Shared authoritative PC and NPC aggregate roots';

-- A class may occur at most once per character. The duplicated release id is
-- constrained against both the character and the immutable class definition.
CREATE TABLE `character_class_level` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `class_level` TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (`character_id`, `class_key`),
    KEY `ix_character_class_definition` (`module_release_id`, `class_key`),
    CONSTRAINT `fk_character_class_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_class_definition`
        FOREIGN KEY (`module_release_id`, `class_key`)
        REFERENCES `module_class_definition` (`module_release_id`, `class_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_class_level`
        CHECK (`class_level` BETWEEN 1 AND 20)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Character class levels from the frozen module release';

-- Separate skill and save tables retain database-enforced references to their
-- respective immutable definition tables without a polymorphic target column.
CREATE TABLE `character_skill_proficiency` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `skill_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `proficiency_key`
        VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`character_id`, `skill_key`),
    KEY `ix_character_skill_definition` (`module_release_id`, `skill_key`),
    KEY `ix_character_skill_tier` (`module_release_id`, `proficiency_key`),
    CONSTRAINT `fk_character_skill_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_skill_definition`
        FOREIGN KEY (`module_release_id`, `skill_key`)
        REFERENCES `module_skill_definition` (`module_release_id`, `skill_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_skill_tier`
        FOREIGN KEY (`module_release_id`, `proficiency_key`)
        REFERENCES `module_proficiency_tier`
            (`module_release_id`, `proficiency_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Skill proficiency tier for one character';

CREATE TABLE `character_save_proficiency` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `save_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `proficiency_key`
        VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`character_id`, `save_key`),
    KEY `ix_character_save_definition` (`module_release_id`, `save_key`),
    KEY `ix_character_save_tier` (`module_release_id`, `proficiency_key`),
    CONSTRAINT `fk_character_save_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_save_definition`
        FOREIGN KEY (`module_release_id`, `save_key`)
        REFERENCES `module_save_definition` (`module_release_id`, `save_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_save_tier`
        FOREIGN KEY (`module_release_id`, `proficiency_key`)
        REFERENCES `module_proficiency_tier`
            (`module_release_id`, `proficiency_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Saving throw proficiency tier for one character';

-- campaign.internal_event_tail allocates the next sequence in the same write
-- transaction. Sequence zero is never an event and gaps must not be invented
-- by application code after a transaction rollback.
CREATE TABLE `game_event` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `event_sequence` BIGINT UNSIGNED NOT NULL,
    `event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `subject_character_id` BIGINT UNSIGNED NULL,
    `event_text` VARCHAR(2000) NULL,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_game_event_campaign_sequence`
        (`campaign_id`, `event_sequence`),
    UNIQUE KEY `uq_game_event_id_campaign` (`id`, `campaign_id`),
    KEY `ix_game_event_subject` (`subject_character_id`, `campaign_id`),
    CONSTRAINT `fk_game_event_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_game_event_subject`
        FOREIGN KEY (`subject_character_id`, `campaign_id`)
        REFERENCES `character_record` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_game_event_sequence`
        CHECK (`event_sequence` > 0),
    CONSTRAINT `chk_game_event_type`
        CHECK (REGEXP_LIKE(`event_type`, '^[A-Z][A-Z0-9_]{0,63}$', 'c')),
    CONSTRAINT `chk_game_event_text`
        CHECK (`event_text` IS NULL OR CHAR_LENGTH(`event_text`) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Campaign-local ordered internal events';

-- Old/new values are physically typed. Either side may be absent for create
-- or removal, but both cannot be absent and columns of other types must stay
-- NULL. Reference values store stable keys, never database ids or JSON.
CREATE TABLE `field_change` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `change_order` INT UNSIGNED NOT NULL,
    `change_key` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `value_type` ENUM('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'REFERENCE')
        NOT NULL,
    `old_text` VARCHAR(2000) NULL,
    `new_text` VARCHAR(2000) NULL,
    `old_integer` BIGINT NULL,
    `new_integer` BIGINT NULL,
    `old_decimal` DECIMAL(38, 18) NULL,
    `new_decimal` DECIMAL(38, 18) NULL,
    `old_boolean` TINYINT UNSIGNED NULL,
    `new_boolean` TINYINT UNSIGNED NULL,
    `old_reference`
        VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `new_reference`
        VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_field_change_event_order` (`game_event_id`, `change_order`),
    KEY `ix_field_change_event_campaign` (`game_event_id`, `campaign_id`),
    KEY `ix_field_change_character_campaign` (`character_id`, `campaign_id`),
    CONSTRAINT `fk_field_change_event`
        FOREIGN KEY (`game_event_id`, `campaign_id`)
        REFERENCES `game_event` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_field_change_character`
        FOREIGN KEY (`character_id`, `campaign_id`)
        REFERENCES `character_record` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_field_change_order`
        CHECK (`change_order` > 0),
    CONSTRAINT `chk_field_change_key`
        CHECK (REGEXP_LIKE(`change_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_field_change_boolean`
        CHECK ((`old_boolean` IS NULL OR `old_boolean` IN (0, 1))
            AND (`new_boolean` IS NULL OR `new_boolean` IN (0, 1))),
    CONSTRAINT `chk_field_change_typed_values`
        CHECK (
            (`value_type` = 'TEXT'
                AND (`old_text` IS NOT NULL OR `new_text` IS NOT NULL)
                AND `old_integer` IS NULL AND `new_integer` IS NULL
                AND `old_decimal` IS NULL AND `new_decimal` IS NULL
                AND `old_boolean` IS NULL AND `new_boolean` IS NULL
                AND `old_reference` IS NULL AND `new_reference` IS NULL)
            OR (`value_type` = 'INTEGER'
                AND (`old_integer` IS NOT NULL OR `new_integer` IS NOT NULL)
                AND `old_text` IS NULL AND `new_text` IS NULL
                AND `old_decimal` IS NULL AND `new_decimal` IS NULL
                AND `old_boolean` IS NULL AND `new_boolean` IS NULL
                AND `old_reference` IS NULL AND `new_reference` IS NULL)
            OR (`value_type` = 'DECIMAL'
                AND (`old_decimal` IS NOT NULL OR `new_decimal` IS NOT NULL)
                AND `old_text` IS NULL AND `new_text` IS NULL
                AND `old_integer` IS NULL AND `new_integer` IS NULL
                AND `old_boolean` IS NULL AND `new_boolean` IS NULL
                AND `old_reference` IS NULL AND `new_reference` IS NULL)
            OR (`value_type` = 'BOOLEAN'
                AND (`old_boolean` IS NOT NULL OR `new_boolean` IS NOT NULL)
                AND `old_text` IS NULL AND `new_text` IS NULL
                AND `old_integer` IS NULL AND `new_integer` IS NULL
                AND `old_decimal` IS NULL AND `new_decimal` IS NULL
                AND `old_reference` IS NULL AND `new_reference` IS NULL)
            OR (`value_type` = 'REFERENCE'
                AND (`old_reference` IS NOT NULL OR `new_reference` IS NOT NULL)
                AND `old_text` IS NULL AND `new_text` IS NULL
                AND `old_integer` IS NULL AND `new_integer` IS NULL
                AND `old_decimal` IS NULL AND `new_decimal` IS NULL
                AND `old_boolean` IS NULL AND `new_boolean` IS NULL)
        )
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Typed before and after values for one internal event';

-- CHECKSUM-SCOPE-END

-- description is NOT NULL. A missing V004 record or invalid built-in release
-- therefore prevents this script from claiming schema version 5.
SELECT COUNT(*) INTO @v004_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 4
  AND `script_name` = 'V004__release_builtin_module.sql'
  AND `script_sha256` =
      '5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f';

INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    5,
    'V005__stage2_character_event_schema.sql',
    'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6',
    (SELECT CASE WHEN @v004_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Stage 2 empty shared character and internal event schema'
     ELSE NULL END
     FROM `module_release`
     WHERE `module_key` = 'dnd5e2014_srd51_se_v1'
       AND `release_version` = '1'
       AND `canonical_format_version` = 1
       AND `hash_algorithm` = 'SHA-256'
       AND `content_sha256` =
           '8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e'
       AND `release_status` = 'RELEASED'
       AND `released_at` IS NOT NULL)
);

-- Intentionally no character, relationship, event, field-change, campaign,
-- operation, module-definition, account, grant, or public rows are inserted.
