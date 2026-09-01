-- DnD Tool SE stage 3 node-map instance and minimal encounter schema
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped and only after V009 has been recorded and verified.
-- This migration creates empty runtime structures only; it does not create a
-- map instance, position, encounter, participant, event, operation or grant.
--
-- Do not add IF NOT EXISTS. An unexpected object or missing prerequisite must
-- stop the migration instead of hiding schema drift.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

-- These candidate keys let mutable instances prove both the campaign's frozen
-- release and the immutable NODE map shape without changing module content.
ALTER TABLE `campaign_module`
    ADD UNIQUE KEY `uq_campaign_module_release`
        (`campaign_id`, `module_release_id`);

ALTER TABLE `module_map_definition`
    ADD UNIQUE KEY `uq_mmap_runtime_node_shape`
        (`module_release_id`, `map_key`, `map_type`);

-- One campaign can instantiate each frozen map key at most once. map_type is
-- retained in the FK so a future non-NODE definition cannot enter this v1 path.
CREATE TABLE `map_instance` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `map_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `map_type` ENUM('NODE') NOT NULL,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_map_instance_campaign_key` (`campaign_id`, `map_key`),
    UNIQUE KEY `uq_map_instance_identity`
        (`id`, `campaign_id`, `module_release_id`, `map_key`),
    KEY `ix_map_instance_definition`
        (`module_release_id`, `map_key`, `map_type`),
    CONSTRAINT `fk_map_instance_campaign_release`
        FOREIGN KEY (`campaign_id`, `module_release_id`)
        REFERENCES `campaign_module` (`campaign_id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_map_instance_definition`
        FOREIGN KEY (`module_release_id`, `map_key`, `map_type`)
        REFERENCES `module_map_definition`
            (`module_release_id`, `map_key`, `map_type`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Campaign instances of frozen built-in NODE maps';

-- The party world node is independent from encounter entity positions. The
-- full instance identity and node FK keep both references in one frozen map.
CREATE TABLE `party_world_position` (
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `map_instance_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `map_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `node_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`campaign_id`),
    KEY `ix_party_world_instance`
        (`map_instance_id`, `campaign_id`, `module_release_id`, `map_key`),
    KEY `ix_party_world_node` (`module_release_id`, `map_key`, `node_key`),
    CONSTRAINT `fk_party_world_instance`
        FOREIGN KEY (`map_instance_id`, `campaign_id`, `module_release_id`, `map_key`)
        REFERENCES `map_instance`
            (`id`, `campaign_id`, `module_release_id`, `map_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_party_world_node`
        FOREIGN KEY (`module_release_id`, `map_key`, `node_key`)
        REFERENCES `module_map_node` (`module_release_id`, `map_key`, `node_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='One independent party world node per campaign';

-- active_campaign_id is NULL for closed history, allowing many closed rows but
-- making two concurrent ACTIVE encounters for one campaign impossible.
CREATE TABLE `battle_state` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `map_instance_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `map_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `battle_status` ENUM('ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    `active_campaign_id` BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN `battle_status` = 'ACTIVE' THEN `campaign_id` ELSE NULL END
    ) STORED,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    `closed_at` TIMESTAMP(6) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_battle_state_active_campaign` (`active_campaign_id`),
    UNIQUE KEY `uq_battle_state_campaign` (`id`, `campaign_id`),
    UNIQUE KEY `uq_battle_state_active_map` (
        `id`, `campaign_id`, `active_campaign_id`, `map_instance_id`,
        `module_release_id`, `map_key`),
    KEY `ix_battle_state_map_instance`
        (`map_instance_id`, `campaign_id`, `module_release_id`, `map_key`),
    CONSTRAINT `fk_battle_state_map_instance`
        FOREIGN KEY (`map_instance_id`, `campaign_id`, `module_release_id`, `map_key`)
        REFERENCES `map_instance`
            (`id`, `campaign_id`, `module_release_id`, `map_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_battle_state_completion`
        CHECK ((`battle_status` = 'ACTIVE' AND `closed_at` IS NULL)
            OR (`battle_status` = 'CLOSED' AND `closed_at` IS NOT NULL))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Minimal active or closed encounter state without turn rules';

-- Participants remain as closed-history rows. The campaign columns in both
-- FKs prove that the participant and encounter belong to the same campaign.
CREATE TABLE `battle_participant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `battle_id` BIGINT UNSIGNED NOT NULL,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `faction` ENUM('ALLY', 'ENEMY', 'NEUTRAL') NOT NULL,
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_battle_participant_character` (`battle_id`, `character_id`),
    UNIQUE KEY `uq_battle_participant_identity`
        (`battle_id`, `campaign_id`, `character_id`),
    KEY `ix_battle_participant_character_campaign`
        (`character_id`, `campaign_id`),
    CONSTRAINT `fk_battle_participant_battle`
        FOREIGN KEY (`battle_id`, `campaign_id`)
        REFERENCES `battle_state` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_battle_participant_character`
        FOREIGN KEY (`character_id`, `campaign_id`)
        REFERENCES `character_record` (`id`, `campaign_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Closed-faction membership in one minimal encounter';

-- A position can exist only while the encounter is ACTIVE. Before closing an
-- encounter its position rows must be removed; participant history can remain.
-- The active-battle FK also fixes the exact encounter map, while the node FK
-- proves the target node belongs to that same frozen map definition.
CREATE TABLE `entity_position` (
    `battle_id` BIGINT UNSIGNED NOT NULL,
    `campaign_id` BIGINT UNSIGNED NOT NULL,
    `active_campaign_id` BIGINT UNSIGNED NOT NULL,
    `map_instance_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `map_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `node_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`battle_id`, `character_id`),
    KEY `ix_entity_position_participant`
        (`battle_id`, `campaign_id`, `character_id`),
    KEY `ix_entity_position_active_map` (
        `battle_id`, `campaign_id`, `active_campaign_id`, `map_instance_id`,
        `module_release_id`, `map_key`),
    KEY `ix_entity_position_node` (`module_release_id`, `map_key`, `node_key`),
    CONSTRAINT `fk_entity_position_participant`
        FOREIGN KEY (`battle_id`, `campaign_id`, `character_id`)
        REFERENCES `battle_participant`
            (`battle_id`, `campaign_id`, `character_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_entity_position_active_battle`
        FOREIGN KEY (
            `battle_id`, `campaign_id`, `active_campaign_id`, `map_instance_id`,
            `module_release_id`, `map_key`
        ) REFERENCES `battle_state` (
            `id`, `campaign_id`, `active_campaign_id`, `map_instance_id`,
            `module_release_id`, `map_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_entity_position_node`
        FOREIGN KEY (`module_release_id`, `map_key`, `node_key`)
        REFERENCES `module_map_node` (`module_release_id`, `map_key`, `node_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_entity_position_active_campaign`
        CHECK (`active_campaign_id` = `campaign_id`)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Nodes for existing participants in the current active encounter';

-- CHECKSUM-SCOPE-END

-- Read schema_meta before targeting it so MySQL error 1093 cannot occur.
SELECT COUNT(*) INTO @v009_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 9
  AND `script_name` = 'V009__stage3_check_execution_schema.sql'
  AND `script_sha256` =
      'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c';

-- Missing V009 metadata or an invalid built-in release makes description
-- NULL, so the NOT NULL schema_meta column fails closed.
INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    10,
    'V010__stage3_node_encounter_schema.sql',
    'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87',
    (SELECT CASE WHEN @v009_schema_record_count = 1 AND COUNT(*) = 1 THEN
        'Stage 3 empty node map and minimal encounter runtime schema'
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

-- Intentionally no map instance, position, encounter, participant, event,
-- operation, character, campaign, module-definition, account or grant rows
-- are inserted.
