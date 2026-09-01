-- DnD Tool SE stage 3 check execution and typed effect-plan schema
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped and only after V008 has been recorded and verified.
-- This migration creates empty runtime structures and supporting constraints;
-- it does not roll dice, create events, modify characters, or grant privileges.
--
-- Do not add IF NOT EXISTS. An unexpected object or missing prerequisite must
-- stop the migration instead of hiding schema drift.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

-- Runtime parameter rows use this immutable logical identity to prove that
-- their key, order and physical value type match the frozen module definition.
-- The index changes no module data and is excluded from the module content hash.
ALTER TABLE `module_effect_parameter`
    ADD UNIQUE KEY `uq_mefp_runtime_parameter_shape` (
        `module_release_id`, `effect_key`, `parameter_key`,
        `parameter_order`, `data_type`);

-- Existing operations remain NULL. Stage 3 services set this only when the
-- surrounding operation succeeds, giving checks and notes one replay root.
ALTER TABLE `host_operation`
    ADD COLUMN `game_event_id` BIGINT UNSIGNED NULL AFTER `character_id`,
    ADD UNIQUE KEY `uq_host_operation_game_event`
        (`game_event_id`, `campaign_id`),
    ADD CONSTRAINT `fk_host_operation_game_event`
        FOREIGN KEY (`game_event_id`, `campaign_id`)
        REFERENCES `game_event` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT `chk_host_operation_game_event_result`
        CHECK (`game_event_id` IS NULL
            OR (`campaign_id` IS NOT NULL AND `result_status` = 'SUCCEEDED'));

-- One completed d20 check is rooted in exactly one campaign event. The source
-- shape distinguishes module-backed ability/skill/save checks from MANUAL
-- without storing a client-supplied algorithm or a derived editable bonus.
CREATE TABLE `check_execution` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `executor_character_id` BIGINT UNSIGNED NOT NULL,
    `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `check_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `roll_mode_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `modifier_source_key`
        VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `manual_name` VARCHAR(80) NULL,
    `modifier_value` SMALLINT NOT NULL,
    `total_value` SMALLINT NOT NULL,
    `difficulty_class` TINYINT UNSIGNED NOT NULL,
    `check_result` ENUM('SUCCESS', 'FAILURE') NOT NULL,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_check_execution_event` (`game_event_id`, `campaign_id`),
    UNIQUE KEY `uq_check_execution_id_release` (`id`, `module_release_id`),
    KEY `ix_check_execution_campaign_created`
        (`campaign_id`, `created_at`, `id`),
    KEY `ix_check_execution_executor_campaign`
        (`executor_character_id`, `campaign_id`),
    KEY `ix_check_execution_executor_release`
        (`executor_character_id`, `module_release_id`),
    KEY `ix_check_execution_check_definition`
        (`module_release_id`, `check_key`),
    KEY `ix_check_execution_roll_mode`
        (`module_release_id`, `roll_mode_key`),
    KEY `ix_check_execution_event_template`
        (`module_release_id`, `event_key`),
    CONSTRAINT `fk_check_execution_event`
        FOREIGN KEY (`game_event_id`, `campaign_id`)
        REFERENCES `game_event` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_check_execution_executor_campaign`
        FOREIGN KEY (`executor_character_id`, `campaign_id`)
        REFERENCES `character_record` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_check_execution_executor_release`
        FOREIGN KEY (`executor_character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_check_execution_check_definition`
        FOREIGN KEY (`module_release_id`, `check_key`)
        REFERENCES `module_check_definition` (`module_release_id`, `check_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_check_execution_roll_mode`
        FOREIGN KEY (`module_release_id`, `roll_mode_key`)
        REFERENCES `module_roll_mode` (`module_release_id`, `roll_mode_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_check_execution_event_template`
        FOREIGN KEY (`module_release_id`, `event_key`)
        REFERENCES `module_event_template` (`module_release_id`, `event_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_check_execution_source_key`
        CHECK (`modifier_source_key` IS NULL OR REGEXP_LIKE(
            `modifier_source_key`,
            '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')),
    CONSTRAINT `chk_check_execution_manual_name`
        CHECK (`manual_name` IS NULL OR
            (CHAR_LENGTH(`manual_name`) BETWEEN 1 AND 80
                AND `manual_name` = TRIM(`manual_name`))),
    CONSTRAINT `chk_check_execution_source_shape`
        CHECK (
            (`check_key` = 'check.ability'
                AND `event_key` IS NOT NULL
                AND `event_key` = 'event.ability_check'
                AND `modifier_source_key` IS NOT NULL
                AND `modifier_source_key` LIKE 'ability.%'
                AND `manual_name` IS NULL)
            OR (`check_key` = 'check.skill'
                AND `event_key` IS NOT NULL
                AND `event_key` = 'event.skill_check'
                AND `modifier_source_key` IS NOT NULL
                AND `modifier_source_key` LIKE 'skill.%'
                AND `manual_name` IS NULL)
            OR (`check_key` = 'check.saving_throw'
                AND `event_key` IS NOT NULL
                AND `event_key` = 'event.saving_throw'
                AND `modifier_source_key` IS NOT NULL
                AND `modifier_source_key` LIKE 'save.%'
                AND `manual_name` IS NULL)
            OR (`check_key` = 'check.manual'
                AND `event_key` IS NULL
                AND `modifier_source_key` IS NULL
                AND `manual_name` IS NOT NULL)
        ),
    CONSTRAINT `chk_check_execution_modifier`
        CHECK (`modifier_value` BETWEEN -99 AND 99),
    CONSTRAINT `chk_check_execution_dc`
        CHECK (`difficulty_class` BETWEEN 0 AND 60),
    CONSTRAINT `chk_check_execution_result`
        CHECK ((`check_result` = 'SUCCESS'
                    AND `total_value` >= `difficulty_class`)
            OR (`check_result` = 'FAILURE'
                    AND `total_value` < `difficulty_class`))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Immutable server-computed d20 check executions';

-- The generated guard is NULL for unselected candidates, so the unique index
-- permits all candidates but at most one selected roll per execution. Service
-- validation also requires exactly one and the module-defined candidate count.
CREATE TABLE `dice_roll` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `check_execution_id` BIGINT UNSIGNED NOT NULL,
    `candidate_order` TINYINT UNSIGNED NOT NULL,
    `rolled_value` TINYINT UNSIGNED NOT NULL,
    `is_selected` TINYINT UNSIGNED NOT NULL,
    `selected_guard` TINYINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN `is_selected` = 1 THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_dice_roll_execution_order`
        (`check_execution_id`, `candidate_order`),
    UNIQUE KEY `uq_dice_roll_single_selected`
        (`check_execution_id`, `selected_guard`),
    CONSTRAINT `fk_dice_roll_execution`
        FOREIGN KEY (`check_execution_id`) REFERENCES `check_execution` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_dice_roll_order`
        CHECK (`candidate_order` > 0),
    CONSTRAINT `chk_dice_roll_value`
        CHECK (`rolled_value` BETWEEN 1 AND 20),
    CONSTRAINT `chk_dice_roll_selected`
        CHECK (`is_selected` IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Ordered server-generated d20 candidates for one check';

-- Both branches are immutable request plans. The selected branch is derived
-- from check_execution.check_result; there is no writable applied flag.
-- append_event_message is the only singleton effect in the current release.
CREATE TABLE `check_effect` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `check_execution_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `effect_branch` ENUM('SUCCESS', 'FAILURE') NOT NULL,
    `effect_order` SMALLINT UNSIGNED NOT NULL,
    `effect_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `singleton_effect_key`
        VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN `effect_key` = 'effect.append_event_message'
                 THEN `effect_key` ELSE NULL END) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_check_effect_execution_branch_order`
        (`check_execution_id`, `effect_branch`, `effect_order`),
    UNIQUE KEY `uq_check_effect_singleton_per_branch`
        (`check_execution_id`, `effect_branch`, `singleton_effect_key`),
    UNIQUE KEY `uq_check_effect_id_definition`
        (`id`, `module_release_id`, `effect_key`),
    KEY `ix_check_effect_execution_release`
        (`check_execution_id`, `module_release_id`),
    KEY `ix_check_effect_definition`
        (`module_release_id`, `effect_key`),
    CONSTRAINT `fk_check_effect_execution_release`
        FOREIGN KEY (`check_execution_id`, `module_release_id`)
        REFERENCES `check_execution` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_check_effect_definition`
        FOREIGN KEY (`module_release_id`, `effect_key`)
        REFERENCES `module_effect_definition` (`module_release_id`, `effect_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_check_effect_order`
        CHECK (`effect_order` > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Ordered success and failure effect plans for one check';

-- Parameter values are physically typed and keep stable references rather
-- than database ids or JSON. The composite definition FK proves the declared
-- key, order and type; Java validates bounds, completeness and reference kind.
CREATE TABLE `check_effect_parameter_value` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `check_effect_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `effect_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `parameter_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `parameter_order` INT UNSIGNED NOT NULL,
    `value_type` ENUM('REFERENCE', 'INTEGER', 'DECIMAL', 'TEXT', 'BOOLEAN')
        NOT NULL,
    `reference_value`
        VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `integer_value` BIGINT NULL,
    `decimal_value` DECIMAL(38, 18) NULL,
    `text_value` VARCHAR(2000) NULL,
    `boolean_value` TINYINT UNSIGNED NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_cepv_effect_parameter`
        (`check_effect_id`, `parameter_key`),
    UNIQUE KEY `uq_cepv_effect_parameter_order`
        (`check_effect_id`, `parameter_order`),
    KEY `ix_cepv_effect_definition`
        (`check_effect_id`, `module_release_id`, `effect_key`),
    KEY `ix_cepv_parameter_definition` (
        `module_release_id`, `effect_key`, `parameter_key`,
        `parameter_order`, `value_type`),
    CONSTRAINT `fk_cepv_effect_definition`
        FOREIGN KEY (`check_effect_id`, `module_release_id`, `effect_key`)
        REFERENCES `check_effect` (`id`, `module_release_id`, `effect_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_cepv_parameter_definition`
        FOREIGN KEY (
            `module_release_id`, `effect_key`, `parameter_key`,
            `parameter_order`, `value_type`)
        REFERENCES `module_effect_parameter` (
            `module_release_id`, `effect_key`, `parameter_key`,
            `parameter_order`, `data_type`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_cepv_parameter_order`
        CHECK (`parameter_order` > 0),
    CONSTRAINT `chk_cepv_boolean`
        CHECK (`boolean_value` IS NULL OR `boolean_value` IN (0, 1)),
    CONSTRAINT `chk_cepv_typed_value`
        CHECK (
            (`value_type` = 'REFERENCE'
                AND `reference_value` IS NOT NULL
                AND CHAR_LENGTH(`reference_value`) > 0
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `text_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'INTEGER'
                AND `reference_value` IS NULL
                AND `integer_value` IS NOT NULL
                AND `decimal_value` IS NULL
                AND `text_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'DECIMAL'
                AND `reference_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NOT NULL
                AND `text_value` IS NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'TEXT'
                AND `reference_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `text_value` IS NOT NULL
                AND `boolean_value` IS NULL)
            OR (`value_type` = 'BOOLEAN'
                AND `reference_value` IS NULL
                AND `integer_value` IS NULL
                AND `decimal_value` IS NULL
                AND `text_value` IS NULL
                AND `boolean_value` IS NOT NULL)
        )
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Typed parameter snapshots for planned check effects';

-- CHECKSUM-SCOPE-END

-- Read schema_meta before targeting it so MySQL error 1093 cannot occur.
SELECT COUNT(*) INTO @v008_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 8
  AND `script_name` = 'V008__stage2_simple_item_schema.sql'
  AND `script_sha256` =
      '369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709';

-- Missing V008 metadata or an invalid built-in release makes description
-- NULL, so the NOT NULL schema_meta column fails closed.
INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    9,
    'V009__stage3_check_execution_schema.sql',
    'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c',
    (SELECT CASE WHEN @v008_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Stage 3 empty check execution and typed effect plan schema'
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

-- Intentionally no check, dice, effect, parameter, event, field-change,
-- operation, character, item, campaign, module-definition, account, grant,
-- or public rows are inserted.
