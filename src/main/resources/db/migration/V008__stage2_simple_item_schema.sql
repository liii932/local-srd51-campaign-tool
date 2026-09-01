-- DnD Tool SE stage 2 simple item instance schema
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped and only after V007 has been recorded and verified.
-- This migration creates one empty runtime table; it does not create or
-- modify items, characters, events, campaigns, modules, accounts, or grants.
--
-- Do not add IF NOT EXISTS. An unexpected object or missing prerequisite must
-- stop the migration instead of hiding schema drift.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

-- Simple items belong to one character aggregate. MODULE rows retain their
-- immutable template source; TEMPORARY rows deliberately have no template.
-- Names and descriptions are snapshots so a save is self-contained. Java
-- performs NFC and control-character checks in the surrounding aggregate
-- transaction before the row is written.
CREATE TABLE `item_instance` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `source_kind` ENUM('MODULE', 'TEMPORARY') NOT NULL,
    `module_release_id` BIGINT UNSIGNED NULL,
    `item_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `item_name` VARCHAR(80) NOT NULL,
    `item_description` VARCHAR(500) NOT NULL,
    `quantity` SMALLINT UNSIGNED NOT NULL,
    `item_status` ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (`id`),
    KEY `ix_item_instance_character_status`
        (`character_id`, `item_status`, `id`),
    KEY `ix_item_instance_character_release`
        (`character_id`, `module_release_id`),
    KEY `ix_item_instance_module_template`
        (`module_release_id`, `item_key`),
    CONSTRAINT `fk_item_instance_character`
        FOREIGN KEY (`character_id`) REFERENCES `character_record` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_item_instance_character_release`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_item_instance_module_template`
        FOREIGN KEY (`module_release_id`, `item_key`)
        REFERENCES `module_item_template` (`module_release_id`, `item_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_item_instance_source`
        CHECK (
            (`source_kind` = 'MODULE'
                AND `module_release_id` IS NOT NULL
                AND `item_key` IS NOT NULL)
            OR (`source_kind` = 'TEMPORARY'
                AND `module_release_id` IS NULL
                AND `item_key` IS NULL)
        ),
    CONSTRAINT `chk_item_instance_name`
        CHECK (CHAR_LENGTH(TRIM(`item_name`)) BETWEEN 1 AND 80),
    CONSTRAINT `chk_item_instance_description`
        CHECK (CHAR_LENGTH(`item_description`) <= 500),
    CONSTRAINT `chk_item_instance_quantity`
        CHECK (`quantity` BETWEEN 1 AND 999)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Character-held module or temporary simple item instances';

-- CHECKSUM-SCOPE-END

-- Read schema_meta before targeting it so MySQL error 1093 cannot occur.
SELECT COUNT(*) INTO @v007_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 7
  AND `script_name` = 'V007__character_creation_idempotency.sql'
  AND `script_sha256` =
      '01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef';

-- Missing V007 metadata or an invalid built-in release makes description
-- NULL, so the NOT NULL schema_meta column fails closed.
INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    8,
    'V008__stage2_simple_item_schema.sql',
    '369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709',
    (SELECT CASE WHEN @v007_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Stage 2 empty simple item instance schema'
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

-- Intentionally no item, character, event, campaign, operation,
-- module-definition, account, grant, or public rows are inserted.
