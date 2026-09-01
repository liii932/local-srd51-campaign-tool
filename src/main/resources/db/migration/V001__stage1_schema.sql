-- DnD Tool SE stage 1 schema
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped. The database and users are intentionally managed outside
-- this repository, so this migration neither creates accounts nor grants them.
--
-- Do not add IF NOT EXISTS: an unexpected existing object must stop the
-- migration instead of hiding schema drift. Future changes belong in V002+
-- files; an applied migration is never edited.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

CREATE TABLE `schema_meta` (
    `schema_version` INT UNSIGNED NOT NULL,
    `script_name` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `script_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `description` VARCHAR(255) NOT NULL,
    `applied_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`schema_version`),
    UNIQUE KEY `uq_schema_meta_script_name` (`script_name`),
    CONSTRAINT `chk_schema_meta_script_sha256`
        CHECK (REGEXP_LIKE(`script_sha256`, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Applied additive schema migrations';

CREATE TABLE `module_release` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `module_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `release_version` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `canonical_format_version` INT UNSIGNED NOT NULL,
    `hash_algorithm` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'SHA-256',
    `content_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `release_status` ENUM('DRAFT', 'RELEASED') NOT NULL DEFAULT 'DRAFT',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `released_at` TIMESTAMP(6) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_module_release_identity` (`module_key`, `release_version`),
    CONSTRAINT `chk_module_release_format_version`
        CHECK (`canonical_format_version` > 0),
    CONSTRAINT `chk_module_release_hash_algorithm`
        CHECK (`hash_algorithm` = 'SHA-256'),
    CONSTRAINT `chk_module_release_content_sha256`
        CHECK (`content_sha256` IS NULL
            OR REGEXP_LIKE(`content_sha256`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `chk_module_release_released_state`
        CHECK ((`release_status` = 'DRAFT' AND `released_at` IS NULL)
            OR (`release_status` = 'RELEASED'
                AND `content_sha256` IS NOT NULL
                AND `released_at` IS NOT NULL))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Versioned built-in module releases; no release rows are installed in stage 1';

CREATE TABLE `campaign` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `campaign_key` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `campaign_name` VARCHAR(80) NOT NULL,
    `campaign_status` ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    `host_state_epoch` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `internal_event_tail` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_campaign_key` (`campaign_key`),
    CONSTRAINT `chk_campaign_key_uuid`
        CHECK (REGEXP_LIKE(`campaign_key`,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
            'c')),
    CONSTRAINT `chk_campaign_name_not_blank`
        CHECK (CHAR_LENGTH(TRIM(`campaign_name`)) > 0)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Locally managed campaigns; stage 1 creates no campaign rows';

CREATE TABLE `campaign_module` (
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `frozen_module_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `frozen_release_version` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `frozen_content_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `frozen_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`campaign_id`),
    KEY `ix_campaign_module_release` (`module_release_id`),
    CONSTRAINT `fk_campaign_module_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_campaign_module_release`
        FOREIGN KEY (`module_release_id`) REFERENCES `module_release` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_campaign_module_content_sha256`
        CHECK (REGEXP_LIKE(`frozen_content_sha256`, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Exactly one frozen module release reference per campaign';

CREATE TABLE `host_operation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `request_digest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `operation_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `campaign_id` BIGINT UNSIGNED NULL,
    `result_status` ENUM('PENDING', 'SUCCEEDED') NOT NULL DEFAULT 'PENDING',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `completed_at` TIMESTAMP(6) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_host_operation_request_id` (`request_id`),
    KEY `ix_host_operation_campaign` (`campaign_id`),
    CONSTRAINT `fk_host_operation_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `campaign` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_host_operation_request_id_uuid`
        CHECK (REGEXP_LIKE(`request_id`,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
            'c')),
    CONSTRAINT `chk_host_operation_digest`
        CHECK (REGEXP_LIKE(`request_digest_sha256`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `chk_host_operation_completion`
        CHECK ((`result_status` = 'PENDING' AND `completed_at` IS NULL)
            OR (`result_status` = 'SUCCEEDED' AND `completed_at` IS NOT NULL))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Durable idempotency records for host commands';

-- CHECKSUM-SCOPE-END

INSERT INTO `schema_meta` (
    `schema_version`,
    `script_name`,
    `script_sha256`,
    `description`
) VALUES (
    1,
    'V001__stage1_schema.sql',
    '29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644',
    'Stage 1 empty persistence skeleton'
);

-- Intentionally no module_release, campaign, campaign_module, or
-- host_operation business rows are inserted by this migration.
