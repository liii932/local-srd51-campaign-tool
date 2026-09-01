-- DnD Tool SE stage 2 module definition schema
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped and only after V001__stage1_schema.sql has succeeded.
-- This migration creates definition tables only: it does not install a module
-- release, create a campaign, grant privileges, or change database accounts.
--
-- Do not add IF NOT EXISTS. An unexpected existing object or missing V001
-- prerequisite must stop the migration instead of hiding schema drift.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

-- Typed constants hold formulas and cross-entity limits that participate in
-- the canonical module hash without introducing executable scripts or JSON.
CREATE TABLE `module_rule_constant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `constant_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `value_type` ENUM('TEXT', 'IDENTIFIER', 'INTEGER', 'DECIMAL', 'BOOLEAN') NOT NULL,
    `text_value` VARCHAR(2000) NULL,
    `identifier_value` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `integer_value` BIGINT NULL,
    `decimal_value` DECIMAL(38, 18) NULL,
    `boolean_value` TINYINT UNSIGNED NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mrc_release_key` (`module_release_id`, `constant_key`),
    CONSTRAINT `fk_mrc_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mrc_key`
        CHECK (REGEXP_LIKE(`constant_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mrc_boolean`
        CHECK (`boolean_value` IS NULL OR `boolean_value` IN (0, 1)),
    CONSTRAINT `chk_mrc_typed_value`
        CHECK (
            (`value_type` = 'TEXT'
                AND `text_value` IS NOT NULL
                AND `identifier_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'IDENTIFIER'
                AND `text_value` IS NULL
                AND `identifier_value` IS NOT NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'INTEGER'
                AND `text_value` IS NULL
                AND `identifier_value` IS NULL
                AND `integer_value` IS NOT NULL
                AND `decimal_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'DECIMAL'
                AND `text_value` IS NULL
                AND `identifier_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NOT NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'BOOLEAN'
                AND `text_value` IS NULL
                AND `identifier_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `boolean_value` IS NOT NULL)
        )
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Typed canonical constants for one module release';

-- Definition values are physically typed. Java additionally verifies that
-- dependent_max_field_key points to a compatible field in the same release.
CREATE TABLE `module_field_definition` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `field_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(80) NOT NULL,
    `data_type` ENUM('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN') NOT NULL,
    `default_text` VARCHAR(2000) NULL,
    `default_integer` BIGINT NULL,
    `default_decimal` DECIMAL(38, 18) NULL,
    `default_boolean` TINYINT UNSIGNED NULL,
    `minimum_integer` BIGINT NULL,
    `maximum_integer` BIGINT NULL,
    `minimum_decimal` DECIMAL(38, 18) NULL,
    `maximum_decimal` DECIMAL(38, 18) NULL,
    `dependent_max_field_key`
        VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `unit` VARCHAR(32) NULL,
    `description` VARCHAR(500) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mfd_release_key` (`module_release_id`, `field_key`),
    KEY `ix_mfd_dependent_max` (`module_release_id`, `dependent_max_field_key`),
    CONSTRAINT `fk_mfd_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_mfd_dependent_max`
        FOREIGN KEY (`module_release_id`, `dependent_max_field_key`)
        REFERENCES `module_field_definition` (`module_release_id`, `field_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mfd_key`
        CHECK (REGEXP_LIKE(`field_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mfd_name`
        CHECK (CHAR_LENGTH(TRIM(`display_name`)) > 0),
    CONSTRAINT `chk_mfd_description`
        CHECK (CHAR_LENGTH(TRIM(`description`)) > 0),
    CONSTRAINT `chk_mfd_default_boolean`
        CHECK (`default_boolean` IS NULL OR `default_boolean` IN (0, 1)),
    CONSTRAINT `chk_mfd_typed_values`
        CHECK (
            (`data_type` = 'TEXT'
                AND `default_text` IS NOT NULL
                AND `default_integer` IS NULL
                AND `default_decimal` IS NULL
                AND `default_boolean` IS NULL
                AND `minimum_integer` IS NULL
                AND `maximum_integer` IS NULL
                AND `minimum_decimal` IS NULL
                AND `maximum_decimal` IS NULL)
            OR (`data_type` = 'INTEGER'
                AND `default_text` IS NULL
                AND `default_integer` IS NOT NULL
                AND `default_decimal` IS NULL
                AND `default_boolean` IS NULL
                AND `minimum_decimal` IS NULL
                AND `maximum_decimal` IS NULL)
            OR (`data_type` = 'DECIMAL'
                AND `default_text` IS NULL
                AND `default_integer` IS NULL
                AND `default_decimal` IS NOT NULL
                AND `default_boolean` IS NULL
                AND `minimum_integer` IS NULL
                AND `maximum_integer` IS NULL)
            OR (`data_type` = 'BOOLEAN'
                AND `default_text` IS NULL
                AND `default_integer` IS NULL
                AND `default_decimal` IS NULL
                AND `default_boolean` IS NOT NULL
                AND `minimum_integer` IS NULL
                AND `maximum_integer` IS NULL
                AND `minimum_decimal` IS NULL
                AND `maximum_decimal` IS NULL)
        ),
    CONSTRAINT `chk_mfd_integer_range`
        CHECK (`minimum_integer` IS NULL
            OR `maximum_integer` IS NULL
            OR `minimum_integer` <= `maximum_integer`),
    CONSTRAINT `chk_mfd_decimal_range`
        CHECK (`minimum_decimal` IS NULL
            OR `maximum_decimal` IS NULL
            OR `minimum_decimal` <= `maximum_decimal`),
    CONSTRAINT `chk_mfd_dependent_type`
        CHECK (`dependent_max_field_key` IS NULL
            OR (`data_type` = 'INTEGER' AND `maximum_integer` IS NULL))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Typed character field definitions for one module release';

CREATE TABLE `module_class_definition` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(80) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mcd_release_key` (`module_release_id`, `class_key`),
    CONSTRAINT `fk_mcd_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mcd_key`
        CHECK (REGEXP_LIKE(`class_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mcd_name`
        CHECK (CHAR_LENGTH(TRIM(`display_name`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Class names used by character class-level relations';

CREATE TABLE `module_proficiency_tier` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `proficiency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `enum_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `numerator` INT UNSIGNED NOT NULL,
    `denominator` INT UNSIGNED NOT NULL,
    `rounding_algorithm` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mpt_release_key` (`module_release_id`, `proficiency_key`),
    UNIQUE KEY `uq_mpt_release_code` (`module_release_id`, `enum_code`),
    CONSTRAINT `fk_mpt_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mpt_key`
        CHECK (REGEXP_LIKE(`proficiency_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mpt_code`
        CHECK (REGEXP_LIKE(`enum_code`, '^[A-Z][A-Z0-9_]*$', 'c')),
    CONSTRAINT `chk_mpt_denominator`
        CHECK (`denominator` > 0),
    CONSTRAINT `chk_mpt_rounding`
        CHECK (`rounding_algorithm` IN ('EXACT', 'FLOOR'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Closed proficiency multiplier definitions';

CREATE TABLE `module_proficiency_bonus_band` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `minimum_total_level` TINYINT UNSIGNED NOT NULL,
    `maximum_total_level` TINYINT UNSIGNED NOT NULL,
    `bonus` TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mpbb_band` (
        `module_release_id`, `minimum_total_level`, `maximum_total_level`),
    CONSTRAINT `fk_mpbb_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mpbb_level_range`
        CHECK (`minimum_total_level` <= `maximum_total_level`
            AND `maximum_total_level` <= 20),
    CONSTRAINT `chk_mpbb_bonus`
        CHECK (`bonus` > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Total-level bands for proficiency bonus';

CREATE TABLE `module_skill_definition` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `skill_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(80) NOT NULL,
    `ability_field_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_msd_release_key` (`module_release_id`, `skill_key`),
    KEY `ix_msd_ability` (`module_release_id`, `ability_field_key`),
    CONSTRAINT `fk_msd_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_msd_ability`
        FOREIGN KEY (`module_release_id`, `ability_field_key`)
        REFERENCES `module_field_definition` (`module_release_id`, `field_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_msd_key`
        CHECK (REGEXP_LIKE(`skill_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_msd_name`
        CHECK (CHAR_LENGTH(TRIM(`display_name`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Skill definitions and their fixed ability references';

CREATE TABLE `module_save_definition` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `save_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `ability_field_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_msvd_release_key` (`module_release_id`, `save_key`),
    KEY `ix_msvd_ability` (`module_release_id`, `ability_field_key`),
    CONSTRAINT `fk_msvd_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_msvd_ability`
        FOREIGN KEY (`module_release_id`, `ability_field_key`)
        REFERENCES `module_field_definition` (`module_release_id`, `field_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_msvd_key`
        CHECK (REGEXP_LIKE(`save_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Saving throw definitions and fixed ability references';

CREATE TABLE `module_item_template` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `item_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(80) NOT NULL,
    `description` VARCHAR(500) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mit_release_key` (`module_release_id`, `item_key`),
    CONSTRAINT `fk_mit_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mit_key`
        CHECK (REGEXP_LIKE(`item_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mit_name`
        CHECK (CHAR_LENGTH(TRIM(`display_name`)) > 0),
    CONSTRAINT `chk_mit_description`
        CHECK (CHAR_LENGTH(TRIM(`description`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Built-in simple item templates';

CREATE TABLE `module_entity_template` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `template_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(80) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_met_release_key` (`module_release_id`, `template_key`),
    CONSTRAINT `fk_met_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_met_key`
        CHECK (REGEXP_LIKE(`template_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_met_name`
        CHECK (CHAR_LENGTH(TRIM(`display_name`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='NPC and other built-in entity templates';

CREATE TABLE `module_entity_template_value` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `template_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `field_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `value_type` ENUM('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN') NOT NULL,
    `text_value` VARCHAR(2000) NULL,
    `integer_value` BIGINT NULL,
    `decimal_value` DECIMAL(38, 18) NULL,
    `boolean_value` TINYINT UNSIGNED NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_metv_template_field` (
        `module_release_id`, `template_key`, `field_key`),
    KEY `ix_metv_field` (`module_release_id`, `field_key`),
    CONSTRAINT `fk_metv_template`
        FOREIGN KEY (`module_release_id`, `template_key`)
        REFERENCES `module_entity_template` (`module_release_id`, `template_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_metv_field`
        FOREIGN KEY (`module_release_id`, `field_key`)
        REFERENCES `module_field_definition` (`module_release_id`, `field_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_metv_boolean`
        CHECK (`boolean_value` IS NULL OR `boolean_value` IN (0, 1)),
    CONSTRAINT `chk_metv_typed_value`
        CHECK (
            (`value_type` = 'TEXT'
                AND `text_value` IS NOT NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'INTEGER'
                AND `text_value` IS NULL
                AND `integer_value` IS NOT NULL
                AND `decimal_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'DECIMAL'
                AND `text_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NOT NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'BOOLEAN'
                AND `text_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `boolean_value` IS NOT NULL)
        )
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Typed field defaults for built-in entity templates';

CREATE TABLE `module_entity_template_class_level` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `template_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `level` TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_metcl_template_class` (
        `module_release_id`, `template_key`, `class_key`),
    KEY `ix_metcl_class` (`module_release_id`, `class_key`),
    CONSTRAINT `fk_metcl_template`
        FOREIGN KEY (`module_release_id`, `template_key`)
        REFERENCES `module_entity_template` (`module_release_id`, `template_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_metcl_class`
        FOREIGN KEY (`module_release_id`, `class_key`)
        REFERENCES `module_class_definition` (`module_release_id`, `class_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_metcl_level`
        CHECK (`level` BETWEEN 1 AND 20)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Optional class levels on built-in entity templates';

-- target_key is generated so canonical readers see one stable target while
-- the database still enforces a real FK to either a skill or a saving throw.
CREATE TABLE `module_entity_template_proficiency` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `template_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `target_kind` ENUM('SKILL', 'SAVING_THROW') NOT NULL,
    `skill_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `save_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `target_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE `target_kind`
                WHEN 'SKILL' THEN `skill_key`
                WHEN 'SAVING_THROW' THEN `save_key`
            END
        ) STORED,
    `proficiency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_metp_template_target` (
        `module_release_id`, `template_key`, `target_kind`, `target_key`),
    KEY `ix_metp_skill` (`module_release_id`, `skill_key`),
    KEY `ix_metp_save` (`module_release_id`, `save_key`),
    KEY `ix_metp_tier` (`module_release_id`, `proficiency_key`),
    CONSTRAINT `fk_metp_template`
        FOREIGN KEY (`module_release_id`, `template_key`)
        REFERENCES `module_entity_template` (`module_release_id`, `template_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_metp_skill`
        FOREIGN KEY (`module_release_id`, `skill_key`)
        REFERENCES `module_skill_definition` (`module_release_id`, `skill_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_metp_save`
        FOREIGN KEY (`module_release_id`, `save_key`)
        REFERENCES `module_save_definition` (`module_release_id`, `save_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_metp_tier`
        FOREIGN KEY (`module_release_id`, `proficiency_key`)
        REFERENCES `module_proficiency_tier` (`module_release_id`, `proficiency_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_metp_target`
        CHECK ((`target_kind` = 'SKILL'
                AND `skill_key` IS NOT NULL
                AND `save_key` IS NULL)
            OR (`target_kind` = 'SAVING_THROW'
                AND `skill_key` IS NULL
                AND `save_key` IS NOT NULL))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Explicit skill and save proficiency defaults for templates';

CREATE TABLE `module_check_definition` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `check_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `enum_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `modifier_algorithm` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mchk_release_key` (`module_release_id`, `check_key`),
    UNIQUE KEY `uq_mchk_release_code` (`module_release_id`, `enum_code`),
    CONSTRAINT `fk_mchk_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mchk_key`
        CHECK (REGEXP_LIKE(`check_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mchk_code`
        CHECK (REGEXP_LIKE(`enum_code`, '^[A-Z][A-Z0-9_]*$', 'c')),
    CONSTRAINT `chk_mchk_algorithm`
        CHECK (REGEXP_LIKE(`modifier_algorithm`, '^[A-Z][A-Z0-9_]*$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Closed check kinds and server modifier algorithms';

CREATE TABLE `module_roll_mode` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `roll_mode_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `enum_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `candidate_count` TINYINT UNSIGNED NOT NULL,
    `selection_algorithm` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mrm_release_key` (`module_release_id`, `roll_mode_key`),
    UNIQUE KEY `uq_mrm_release_code` (`module_release_id`, `enum_code`),
    CONSTRAINT `fk_mrm_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mrm_key`
        CHECK (REGEXP_LIKE(`roll_mode_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mrm_code`
        CHECK (REGEXP_LIKE(`enum_code`, '^[A-Z][A-Z0-9_]*$', 'c')),
    CONSTRAINT `chk_mrm_candidates`
        CHECK (`candidate_count` > 0),
    CONSTRAINT `chk_mrm_algorithm`
        CHECK (REGEXP_LIKE(`selection_algorithm`, '^[A-Z][A-Z0-9_]*$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Normal, advantage and disadvantage roll modes';

CREATE TABLE `module_event_template` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(80) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mevt_release_key` (`module_release_id`, `event_key`),
    CONSTRAINT `fk_mevt_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mevt_key`
        CHECK (REGEXP_LIKE(`event_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mevt_name`
        CHECK (CHAR_LENGTH(TRIM(`display_name`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Script-free event templates';

CREATE TABLE `module_effect_definition` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `effect_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `execution_algorithm` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_meff_release_key` (`module_release_id`, `effect_key`),
    CONSTRAINT `fk_meff_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_meff_key`
        CHECK (REGEXP_LIKE(`effect_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_meff_algorithm`
        CHECK (REGEXP_LIKE(`execution_algorithm`, '^[A-Z][A-Z0-9_]*$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Closed, typed effect kinds without executable scripts';

CREATE TABLE `module_event_check` (
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `check_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`module_release_id`, `event_key`, `check_key`),
    KEY `ix_mec_check` (`module_release_id`, `check_key`),
    CONSTRAINT `fk_mec_event`
        FOREIGN KEY (`module_release_id`, `event_key`)
        REFERENCES `module_event_template` (`module_release_id`, `event_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_mec_check`
        FOREIGN KEY (`module_release_id`, `check_key`)
        REFERENCES `module_check_definition` (`module_release_id`, `check_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Checks permitted by each event template';

CREATE TABLE `module_event_effect` (
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `effect_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`module_release_id`, `event_key`, `effect_key`),
    KEY `ix_mee_effect` (`module_release_id`, `effect_key`),
    CONSTRAINT `fk_mee_event`
        FOREIGN KEY (`module_release_id`, `event_key`)
        REFERENCES `module_event_template` (`module_release_id`, `event_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_mee_effect`
        FOREIGN KEY (`module_release_id`, `effect_key`)
        REFERENCES `module_effect_definition` (`module_release_id`, `effect_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Effects permitted by each event template';

-- Bounds use integer columns for integer values and text code-point counts;
-- decimal values use decimal columns. Java maps them to canonical V scalars.
CREATE TABLE `module_effect_parameter` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `effect_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `parameter_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `data_type` ENUM('REFERENCE', 'INTEGER', 'DECIMAL', 'TEXT', 'BOOLEAN') NOT NULL,
    `reference_kind` ENUM('CHARACTER', 'ITEM_TEMPLATE', 'MAP', 'NODE') NULL,
    `minimum_integer` BIGINT NULL,
    `maximum_integer` BIGINT NULL,
    `minimum_decimal` DECIMAL(38, 18) NULL,
    `maximum_decimal` DECIMAL(38, 18) NULL,
    `text_normalization` ENUM('NFC', 'TRIM_THEN_NFC') NULL,
    `reject_control_characters` TINYINT UNSIGNED NULL,
    `parameter_order` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mefp_effect_parameter` (
        `module_release_id`, `effect_key`, `parameter_key`),
    UNIQUE KEY `uq_mefp_effect_order` (
        `module_release_id`, `effect_key`, `parameter_order`),
    CONSTRAINT `fk_mefp_effect`
        FOREIGN KEY (`module_release_id`, `effect_key`)
        REFERENCES `module_effect_definition` (`module_release_id`, `effect_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mefp_key`
        CHECK (REGEXP_LIKE(`parameter_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mefp_controls`
        CHECK (`reject_control_characters` IS NULL
            OR `reject_control_characters` IN (0, 1)),
    CONSTRAINT `chk_mefp_order`
        CHECK (`parameter_order` > 0),
    CONSTRAINT `chk_mefp_integer_range`
        CHECK (`minimum_integer` IS NULL
            OR `maximum_integer` IS NULL
            OR `minimum_integer` <= `maximum_integer`),
    CONSTRAINT `chk_mefp_decimal_range`
        CHECK (`minimum_decimal` IS NULL
            OR `maximum_decimal` IS NULL
            OR `minimum_decimal` <= `maximum_decimal`),
    CONSTRAINT `chk_mefp_typed_bounds`
        CHECK (
            (`data_type` = 'REFERENCE'
                AND `reference_kind` IS NOT NULL
                AND `minimum_integer` IS NULL
                AND `maximum_integer` IS NULL
                AND `minimum_decimal` IS NULL
                AND `maximum_decimal` IS NULL
                AND `text_normalization` IS NULL
                AND `reject_control_characters` IS NULL)
            OR (`data_type` = 'INTEGER'
                AND `reference_kind` IS NULL
                AND `minimum_decimal` IS NULL
                AND `maximum_decimal` IS NULL
                AND `text_normalization` IS NULL
                AND `reject_control_characters` IS NULL)
            OR (`data_type` = 'DECIMAL'
                AND `reference_kind` IS NULL
                AND `minimum_integer` IS NULL
                AND `maximum_integer` IS NULL
                AND `text_normalization` IS NULL
                AND `reject_control_characters` IS NULL)
            OR (`data_type` = 'TEXT'
                AND `reference_kind` IS NULL
                AND `minimum_decimal` IS NULL
                AND `maximum_decimal` IS NULL
                AND `text_normalization` IS NOT NULL
                AND `reject_control_characters` IS NOT NULL)
            OR (`data_type` = 'BOOLEAN'
                AND `reference_kind` IS NULL
                AND `minimum_integer` IS NULL
                AND `maximum_integer` IS NULL
                AND `minimum_decimal` IS NULL
                AND `maximum_decimal` IS NULL
                AND `text_normalization` IS NULL
                AND `reject_control_characters` IS NULL)
        )
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Ordered typed parameters for closed effect definitions';

CREATE TABLE `module_map_definition` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `map_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `map_type` ENUM('NODE') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mmap_release_key` (`module_release_id`, `map_key`),
    CONSTRAINT `fk_mmap_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mmap_key`
        CHECK (REGEXP_LIKE(`map_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Built-in node map definitions';

CREATE TABLE `module_map_node` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `map_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `node_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `display_name` VARCHAR(80) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mmn_map_node` (`module_release_id`, `map_key`, `node_key`),
    CONSTRAINT `fk_mmn_map`
        FOREIGN KEY (`module_release_id`, `map_key`)
        REFERENCES `module_map_definition` (`module_release_id`, `map_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mmn_node_key`
        CHECK (REGEXP_LIKE(`node_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_mmn_name`
        CHECK (CHAR_LENGTH(TRIM(`display_name`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Stable nodes within built-in node maps';

CREATE TABLE `module_map_connection` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `map_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `endpoint_low_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `endpoint_high_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_mmc_connection` (
        `module_release_id`, `map_key`, `endpoint_low_key`, `endpoint_high_key`),
    KEY `ix_mmc_high_endpoint` (
        `module_release_id`, `map_key`, `endpoint_high_key`),
    CONSTRAINT `fk_mmc_low_node`
        FOREIGN KEY (`module_release_id`, `map_key`, `endpoint_low_key`)
        REFERENCES `module_map_node` (`module_release_id`, `map_key`, `node_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_mmc_high_node`
        FOREIGN KEY (`module_release_id`, `map_key`, `endpoint_high_key`)
        REFERENCES `module_map_node` (`module_release_id`, `map_key`, `node_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_mmc_endpoint_order`
        CHECK (`endpoint_low_key` < `endpoint_high_key`)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Canonical undirected connections between map nodes';

-- CHECKSUM-SCOPE-END

INSERT INTO `schema_meta` (
    `schema_version`,
    `script_name`,
    `script_sha256`,
    `description`
) VALUES (
    2,
    'V002__stage2_module_schema.sql',
    '55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8',
    'Stage 2 empty module definition schema'
);

-- Intentionally no module_release or module_* definition rows are inserted.
-- V003+ will install the reviewed built-in release as DRAFT data.
