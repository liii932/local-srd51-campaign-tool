-- DnD Tool SE canonical-v2 class, subclass and feature lifecycle support
--
-- Execute exactly once as the migrator after V013 has been recorded and verified.
-- The complete release remains DRAFT. No campaign, character, event or grant is created here.

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

-- Every class/subclass feature receives exactly one closed runtime disposition.
-- OPTION rows and subclass roots are bounded server-validated choices. Spell-coupled
-- rows fail closed. The narrow automatic set exposes only the resource lifecycle
-- already represented by canonical-v2 resource state; it does not claim combat effects.
CREATE TEMPORARY TABLE `v014_feature_execution_seed` (
    `feature_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `execution_mode` ENUM('AUTOMATIC', 'DM_ADJUDICATION', 'BLOCKED') NOT NULL,
    `execution_algorithm` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=ascii COLLATE=ascii_bin;

INSERT INTO `v014_feature_execution_seed` (
    `feature_key`, `execution_mode`, `execution_algorithm`)
SELECT d.`definition_key`,
       CASE
           WHEN d.`definition_key` LIKE '%.ability_score_improvement'
               THEN 'BLOCKED'
           WHEN d.`definition_key` REGEXP
                '(^|[.])(spellcasting|pact_magic|mystic_arcanum|spell_mastery|signature_spells|magical_secrets|additional_magical_secrets|bonus_cantrip|circle_spells|oath_spells|expanded_spell_list|arcane_recovery)($|[.])'
               OR d.`definition_key` LIKE 'feature.warlock.invocation.%'
               OR d.`definition_key` LIKE 'feature.sorcerer.metamagic.%'
               THEN 'BLOCKED'
           WHEN category.`identifier_value` = 'OPTION'
               OR d.`definition_key` REGEXP
                '[.](primal_path|bard_college|divine_domain|druid_circle|martial_archetype|monastic_tradition|sacred_oath|ranger_archetype|roguish_archetype|sorcerous_origin|otherworldly_patron|arcane_tradition|expertise|fighting_style|favored_enemy|natural_explorer)$'
               THEN 'DM_ADJUDICATION'
           WHEN d.`definition_key` IN (
                'feature.barbarian.rage',
                'feature.bard.bardic_inspiration',
                'feature.bard.font_of_inspiration',
                'feature.cleric.channel_divinity',
                'feature.druid.wild_shape',
                'feature.fighter.second_wind',
                'feature.fighter.action_surge',
                'feature.fighter.indomitable',
                'feature.monk.ki',
                'feature.paladin.divine_sense',
                'feature.paladin.lay_on_hands',
                'feature.rogue.stroke_of_luck',
                'feature.sorcerer.font_of_magic')
               THEN 'AUTOMATIC'
           WHEN d.`definition_key` IN (
                'feature.cleric.divine_intervention',
                'feature.ranger.primeval_awareness')
               THEN 'DM_ADJUDICATION'
           ELSE 'BLOCKED'
       END,
       CASE
           WHEN d.`definition_key` LIKE '%.ability_score_improvement'
               THEN 'BLOCKED_ISSUE_8_V1'
           WHEN d.`definition_key` REGEXP
                '(^|[.])(spellcasting|pact_magic|mystic_arcanum|spell_mastery|signature_spells|magical_secrets|additional_magical_secrets|bonus_cantrip|circle_spells|oath_spells|expanded_spell_list|arcane_recovery)($|[.])'
               OR d.`definition_key` LIKE 'feature.warlock.invocation.%'
               OR d.`definition_key` LIKE 'feature.sorcerer.metamagic.%'
               THEN 'BLOCKED_SPELL_SYSTEM_V1'
           WHEN category.`identifier_value` = 'OPTION'
               THEN 'BOUNDED_FEATURE_SELECTION_V1'
           WHEN d.`definition_key` REGEXP
                '[.](primal_path|bard_college|divine_domain|druid_circle|martial_archetype|monastic_tradition|sacred_oath|ranger_archetype|roguish_archetype|sorcerous_origin|otherworldly_patron|arcane_tradition)$'
               THEN 'BOUNDED_SUBCLASS_SELECTION_V1'
           WHEN d.`definition_key` REGEXP
                '[.](expertise|fighting_style|favored_enemy|natural_explorer)$'
               THEN 'BOUNDED_FEATURE_SELECTION_V1'
           WHEN d.`definition_key` IN (
                'feature.cleric.divine_intervention',
                'feature.ranger.primeval_awareness')
               THEN 'BOUNDED_DM_ADJUDICATION_V1'
           WHEN d.`definition_key` IN (
                'feature.barbarian.rage',
                'feature.bard.bardic_inspiration',
                'feature.bard.font_of_inspiration',
                'feature.cleric.channel_divinity',
                'feature.druid.wild_shape',
                'feature.fighter.second_wind',
                'feature.fighter.action_surge',
                'feature.fighter.indomitable',
                'feature.monk.ki',
                'feature.paladin.divine_sense',
                'feature.paladin.lay_on_hands',
                'feature.rogue.stroke_of_luck',
                'feature.sorcerer.font_of_magic')
               THEN 'AUTOMATIC_RESOURCE_LIFECYCLE_V1'
           ELSE 'BLOCKED_DOWNSTREAM_SYSTEM_V1'
       END
FROM `module_catalog_definition_v2` AS d
JOIN `module_catalog_relation_v2` AS owner
  ON owner.`module_release_id` = d.`module_release_id`
 AND owner.`source_type` = d.`definition_type`
 AND owner.`source_key` = d.`definition_key`
 AND owner.`relation_type` = 'feature.owner'
LEFT JOIN `module_catalog_attribute_v2` AS category
  ON category.`module_release_id` = d.`module_release_id`
 AND category.`definition_type` = d.`definition_type`
 AND category.`definition_key` = d.`definition_key`
 AND category.`attribute_key` = 'catalog.category'
 AND category.`attribute_order` = 1
WHERE d.`module_release_id` = @complete_release_id
  AND d.`definition_type` = 'character.feature'
  AND owner.`target_type` IN ('character.class', 'character.subclass');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`)
SELECT @complete_release_id, 'character.feature', `feature_key`,
       'feature.execution_mode', 1, 'IDENTIFIER', `execution_mode`
FROM `v014_feature_execution_seed`;

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`)
SELECT @complete_release_id, 'character.feature', `feature_key`,
       'feature.execution_algorithm', 1, 'IDENTIFIER', `execution_algorithm`
FROM `v014_feature_execution_seed`;

SELECT COUNT(*) INTO @v014_automatic_feature_count
FROM `v014_feature_execution_seed`
WHERE `execution_mode` = 'AUTOMATIC';
SELECT COUNT(*) INTO @v014_adjudicated_feature_count
FROM `v014_feature_execution_seed`
WHERE `execution_mode` = 'DM_ADJUDICATION';
SELECT COUNT(*) INTO @v014_blocked_feature_count
FROM `v014_feature_execution_seed`
WHERE `execution_mode` = 'BLOCKED';

DROP TEMPORARY TABLE `v014_feature_execution_seed`;

-- The single SRD subclass for each class is selected at its actual class level.
INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `integer_value`)
SELECT @complete_release_id, 'character.subclass', d.`definition_key`,
       'subclass.selection_level', 1, 'INTEGER',
       CASE d.`definition_key`
           WHEN 'subclass.life' THEN 1
           WHEN 'subclass.draconic_bloodline' THEN 1
           WHEN 'subclass.fiend' THEN 1
           WHEN 'subclass.land' THEN 2
           WHEN 'subclass.evocation' THEN 2
           ELSE 3
       END
FROM `module_catalog_definition_v2` AS d
WHERE d.`module_release_id` = @complete_release_id
  AND d.`definition_type` = 'character.subclass';

-- Spell-state resources remain absent from authoritative state until the spell system exists.
INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `identifier_value`)
SELECT @complete_release_id, 'character.resource', d.`definition_key`,
       'resource.execution_mode', 1, 'IDENTIFIER',
       CASE WHEN d.`definition_key` IN (
                'resource.warlock.pact_magic',
                'resource.warlock.mystic_arcanum',
                'resource.wizard.arcane_recovery')
            THEN 'BLOCKED' ELSE 'AUTOMATIC' END
FROM `module_catalog_definition_v2` AS d
JOIN `module_catalog_relation_v2` AS owner
  ON owner.`module_release_id` = d.`module_release_id`
 AND owner.`source_type` = d.`definition_type`
 AND owner.`source_key` = d.`definition_key`
 AND owner.`relation_type` = 'resource.owner'
WHERE d.`module_release_id` = @complete_release_id
  AND d.`definition_type` = 'character.resource'
  AND owner.`target_type` IN ('character.class', 'character.subclass');

INSERT INTO `module_catalog_attribute_v2` (
    `module_release_id`, `definition_type`, `definition_key`, `attribute_key`,
    `attribute_order`, `value_type`, `text_value`)
SELECT @complete_release_id, 'character.resource', d.`definition_key`,
       'resource.recovery_profile', 1, 'TEXT',
       CASE WHEN d.`definition_key` = 'resource.bard.bardic_inspiration'
            THEN '1-4:LONG_REST,5-20:SHORT_REST'
            ELSE CONCAT('1-20:', recovery.`identifier_value`) END
FROM `module_catalog_definition_v2` AS d
JOIN `module_catalog_relation_v2` AS owner
  ON owner.`module_release_id` = d.`module_release_id`
 AND owner.`source_type` = d.`definition_type`
 AND owner.`source_key` = d.`definition_key`
 AND owner.`relation_type` = 'resource.owner'
JOIN `module_catalog_attribute_v2` AS recovery
  ON recovery.`module_release_id` = d.`module_release_id`
 AND recovery.`definition_type` = d.`definition_type`
 AND recovery.`definition_key` = d.`definition_key`
 AND recovery.`attribute_key` = 'resource.recovery'
 AND recovery.`attribute_order` = 1
WHERE d.`module_release_id` = @complete_release_id
  AND d.`definition_type` = 'character.resource'
  AND owner.`target_type` IN ('character.class', 'character.subclass');

CREATE TABLE `character_subclass_state_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `class_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.class',
    `class_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `subclass_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.subclass',
    `subclass_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `selected_at_class_level` TINYINT UNSIGNED NOT NULL,
    `acquired_event_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`character_id`, `class_key`),
    UNIQUE KEY `uq_character_subclass_state_subclass` (`character_id`, `subclass_key`),
    KEY `ix_character_subclass_state_class`
        (`module_release_id`, `class_type`, `class_key`),
    KEY `ix_character_subclass_state_subclass`
        (`module_release_id`, `subclass_type`, `subclass_key`),
    CONSTRAINT `fk_character_subclass_state_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_subclass_state_class`
        FOREIGN KEY (`module_release_id`, `class_type`, `class_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_subclass_state_subclass`
        FOREIGN KEY (`module_release_id`, `subclass_type`, `subclass_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_subclass_state_event`
        FOREIGN KEY (`acquired_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_subclass_state_level`
        CHECK (`selected_at_class_level` BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Authoritative canonical-v2 subclass selection';

CREATE TABLE `character_feature_state_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `feature_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.feature',
    `feature_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `acquired_at_class_level` TINYINT UNSIGNED NOT NULL,
    `execution_mode` ENUM('AUTOMATIC', 'DM_ADJUDICATION', 'BLOCKED') NOT NULL,
    `execution_algorithm` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `acquired_event_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`character_id`, `feature_key`),
    UNIQUE KEY `uq_character_feature_state_identity`
        (`character_id`, `module_release_id`, `feature_type`, `feature_key`),
    KEY `ix_character_feature_state_definition`
        (`module_release_id`, `feature_type`, `feature_key`),
    KEY `ix_character_feature_state_event` (`acquired_event_id`),
    CONSTRAINT `fk_character_feature_state_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_feature_state_definition`
        FOREIGN KEY (`module_release_id`, `feature_type`, `feature_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_feature_state_event`
        FOREIGN KEY (`acquired_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_feature_state_level`
        CHECK (`acquired_at_class_level` BETWEEN 1 AND 20),
    CONSTRAINT `chk_character_feature_state_algorithm`
        CHECK (REGEXP_LIKE(`execution_algorithm`,
            '^[A-Z][A-Z0-9_]{0,127}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Acquired class and subclass features with frozen execution disposition';

CREATE TABLE `character_feature_choice_v2` (
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `source_feature_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.feature',
    `source_feature_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `choice_order` SMALLINT UNSIGNED NOT NULL,
    `choice_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.feature',
    `choice_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `acquired_event_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`character_id`, `source_feature_key`, `choice_key`),
    UNIQUE KEY `uq_character_feature_choice_order`
        (`character_id`, `source_feature_key`, `choice_order`),
    CONSTRAINT `fk_character_feature_choice_state`
        FOREIGN KEY (`character_id`, `module_release_id`,
            `source_feature_type`, `source_feature_key`)
        REFERENCES `character_feature_state_v2`
            (`character_id`, `module_release_id`, `feature_type`, `feature_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_feature_choice_definition`
        FOREIGN KEY (`module_release_id`, `choice_type`, `choice_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_feature_choice_event`
        FOREIGN KEY (`acquired_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_feature_choice_order` CHECK (`choice_order` > 0),
    CONSTRAINT `chk_character_feature_choice_key` CHECK (
        REGEXP_LIKE(`choice_key`,
            '^[a-z][a-z0-9_]*([.][a-z0-9_]+)+$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Server-bounded choices for acquired canonical-v2 features';

CREATE TABLE `character_feature_adjudication_v2` (
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `feature_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.feature',
    `feature_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `decision` ENUM('SUCCESS', 'FAILURE', 'NO_EFFECT') NOT NULL,
    `adjudication_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`game_event_id`),
    KEY `ix_feature_adjudication_character` (`character_id`, `feature_key`),
    CONSTRAINT `fk_feature_adjudication_event`
        FOREIGN KEY (`game_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_feature_adjudication_state`
        FOREIGN KEY (`character_id`, `module_release_id`, `feature_type`, `feature_key`)
        REFERENCES `character_feature_state_v2`
            (`character_id`, `module_release_id`, `feature_type`, `feature_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_feature_adjudication_key` CHECK (
        REGEXP_LIKE(`adjudication_key`,
            '^[a-z][a-z0-9_]*([.][a-z0-9_]+)+$', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Bounded auditable DM decisions for eligible class features';

CREATE TABLE `character_resource_recovery_v2` (
    `game_event_id` BIGINT UNSIGNED NOT NULL,
    `character_id` BIGINT UNSIGNED NOT NULL,
    `module_release_id` BIGINT UNSIGNED NOT NULL,
    `recovery_trigger` ENUM('SHORT_REST', 'LONG_REST') NOT NULL,
    `resource_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        DEFAULT 'character.resource',
    `resource_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `previous_current_value` BIGINT NOT NULL,
    `new_current_value` BIGINT NOT NULL,
    `maximum_value` BIGINT NOT NULL,
    PRIMARY KEY (`game_event_id`, `resource_key`),
    KEY `ix_character_resource_recovery_character`
        (`character_id`, `resource_key`),
    CONSTRAINT `fk_character_resource_recovery_event`
        FOREIGN KEY (`game_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_resource_recovery_character`
        FOREIGN KEY (`character_id`, `module_release_id`)
        REFERENCES `character_record` (`id`, `module_release_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `fk_character_resource_recovery_definition`
        FOREIGN KEY (`module_release_id`, `resource_type`, `resource_key`)
        REFERENCES `module_catalog_definition_v2`
            (`module_release_id`, `definition_type`, `definition_key`)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT `chk_character_resource_recovery_values` CHECK (
        `maximum_value` > 0
        AND `previous_current_value` BETWEEN 0 AND `maximum_value`
        AND `new_current_value` BETWEEN `previous_current_value` AND `maximum_value`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Event-linked class-resource recovery deltas';

-- Stable audit contracts consumed by future transaction repositories.
SET @feature_adjudication_event_type = 'CHARACTER_FEATURE_ADJUDICATED';
SET @resource_recovery_event_type = 'CHARACTER_RESOURCES_RECOVERED';

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v013_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 13
  AND `script_name` = 'V013__level_advancement_hit_dice.sql'
  AND `script_sha256` =
      'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d';

INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`)
VALUES (
    14,
    'V014__class_feature_lifecycle.sql',
    'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361',
    (SELECT CASE WHEN @v013_schema_record_count = 1 AND COUNT(*) = 1
        AND @v014_automatic_feature_count = 13
        AND @v014_adjudicated_feature_count = 24
        AND @v014_blocked_feature_count = 199
        AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `attribute_key` = 'feature.execution_mode') = 236
        AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `attribute_key` = 'feature.execution_algorithm') = 236
        AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `attribute_key` = 'subclass.selection_level') = 12
        AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `attribute_key` = 'resource.execution_mode') = 16
        AND (SELECT COUNT(*) FROM `module_catalog_attribute_v2`
             WHERE `module_release_id` = @complete_release_id
               AND `attribute_key` = 'resource.recovery_profile') = 16 THEN
        'Canonical-v2 class, subclass and feature lifecycle support'
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

-- Intentionally no campaign, character, subclass, feature, event, operation,
-- account or grant row is inserted, and the complete release remains unpublished.
