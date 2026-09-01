-- Adds the durable result reference needed to replay CREATE_CHARACTER safely.
-- This migration creates no character, event, operation, campaign or module data.

-- CHECKSUM-SCOPE-BEGIN
ALTER TABLE `host_operation`
    ADD COLUMN `character_id` BIGINT UNSIGNED NULL AFTER `campaign_id`,
    ADD KEY `ix_host_operation_character` (`character_id`),
    ADD CONSTRAINT `fk_host_operation_character`
        FOREIGN KEY (`character_id`) REFERENCES `character_record` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
-- CHECKSUM-SCOPE-END

-- Read schema_meta before targeting it so MySQL error 1093 cannot occur.
SELECT COUNT(*) INTO @v006_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 6
  AND `script_name` = 'V006__stage2_character_field_value_schema.sql'
  AND `script_sha256` =
      'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b';

-- The placeholder is replaced with the reviewed checksum before this file is approved.
INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    7,
    'V007__character_creation_idempotency.sql',
    '01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef',
    (SELECT CASE WHEN @v006_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Character creation idempotency result reference'
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

-- Intentionally no runtime or module rows are inserted or changed.
