-- DnD Tool SE stage 2 typed character field value schema
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped and only after V005 has been recorded and verified.
-- This migration creates an empty value table only; it does not create or
-- modify characters, fields, events, module data, accounts, or grants.
--
-- Do not add IF NOT EXISTS. An unexpected object or missing prerequisite must
-- stop the migration instead of hiding schema drift.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

-- Include data_type in a candidate key so each runtime value can reference
-- both the immutable field identity and its declared physical value type.
ALTER TABLE `module_field_definition`
    ADD UNIQUE KEY `uq_mfd_release_key_type`
        (`module_release_id`, `field_key`, `data_type`);

-- Exactly one row may exist for one character and stable field key. The two
-- composite foreign keys prevent cross-release definitions and a value_type
-- different from the immutable module declaration. Java additionally checks
-- field ranges, dependent maxima, NFC text, and aggregate row_version in the
-- surrounding business transaction.
CREATE TABLE `character_field_value` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `field_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `value_type` ENUM('TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN') NOT NULL,
    `text_value` VARCHAR(2000) NULL,
    `integer_value` BIGINT NULL,
    `decimal_value` DECIMAL(38, 18) NULL,
    `boolean_value` TINYINT UNSIGNED NULL,
    PRIMARY KEY (`character_id`, `field_key`),
    KEY `ix_character_field_definition`
        (`module_release_id`, `field_key`, `value_type`),
    CONSTRAINT `fk_character_field_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_field_definition`
        FOREIGN KEY (`module_release_id`, `field_key`, `value_type`)
        REFERENCES `module_field_definition`
            (`module_release_id`, `field_key`, `data_type`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_field_boolean`
        CHECK (`boolean_value` IS NULL OR `boolean_value` IN (0, 1)),
    CONSTRAINT `chk_character_field_typed_value`
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
  COMMENT='Physically typed authoritative character field values';

-- CHECKSUM-SCOPE-END

-- Read schema_meta before targeting it so MySQL error 1093 cannot occur.
SELECT COUNT(*) INTO @v005_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 5
  AND `script_name` = 'V005__stage2_character_event_schema.sql'
  AND `script_sha256` =
      'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6';

-- description is NOT NULL. Missing V005 metadata or an invalid built-in
-- release therefore prevents this script from claiming schema version 6.
INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    6,
    'V006__stage2_character_field_value_schema.sql',
    'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b',
    (SELECT CASE WHEN @v005_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Stage 2 empty typed character field value schema'
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

-- Intentionally no field-value, character, event, campaign, operation,
-- module-definition, account, grant, or public rows are inserted.
