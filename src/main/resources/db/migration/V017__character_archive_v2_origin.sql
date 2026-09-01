-- DnD Tool SE archive-format-2 character-state origin support
--
-- Execute exactly once as the migrator after V016 has been recorded and verified.
-- This migration evolves only unpublished canonical-v2 runtime state. It does not
-- publish the module, activate archive format 2, bind a campaign or insert data.

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

-- Live writes continue to require the V015 advancement-choice parent. Archive
-- restore writes instead reference a reconstructed, stable-sequence advancement
-- event. The explicit discriminator prevents either interpretation being guessed.
ALTER TABLE `character_feat_state_v2`
    DROP FOREIGN KEY `fk_character_feat_state_choice`,
    ADD COLUMN `state_origin` ENUM('ADVANCEMENT', 'ARCHIVE_RESTORE')
        NOT NULL DEFAULT 'ADVANCEMENT' AFTER `acquired_event_id`,
    ADD CONSTRAINT `fk_character_feat_state_event`
        FOREIGN KEY (`acquired_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `character_multiclass_proficiency_v2`
    DROP FOREIGN KEY `fk_multiclass_proficiency_choice`,
    ADD COLUMN `state_origin` ENUM('ADVANCEMENT', 'ARCHIVE_RESTORE')
        NOT NULL DEFAULT 'ADVANCEMENT' AFTER `acquired_event_id`,
    ADD CONSTRAINT `fk_multiclass_proficiency_event`
        FOREIGN KEY (`acquired_event_id`) REFERENCES `game_event` (`id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

DELIMITER $$

CREATE TRIGGER `trg_character_feat_state_v2_origin_insert`
BEFORE INSERT ON `character_feat_state_v2`
FOR EACH ROW
BEGIN
    IF NEW.`state_origin` = 'ADVANCEMENT' THEN
        IF NOT EXISTS (
            SELECT 1
            FROM `character_advancement_choice_v2` AS choice_root
            WHERE choice_root.`game_event_id` = NEW.`acquired_event_id`
              AND choice_root.`character_id` = NEW.`character_id`
              AND choice_root.`module_release_id` = NEW.`module_release_id`
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Feat state advancement origin is invalid';
        END IF;
    ELSEIF NOT EXISTS (
        SELECT 1
        FROM `game_event` AS event_root
        JOIN `character_record` AS character_root
          ON character_root.`id` = NEW.`character_id`
         AND character_root.`module_release_id` = NEW.`module_release_id`
         AND character_root.`campaign_id` = event_root.`campaign_id`
        WHERE event_root.`id` = NEW.`acquired_event_id`
          AND event_root.`subject_character_id` = NEW.`character_id`
          AND event_root.`event_type` = 'CHARACTER_LEVEL_ADVANCED'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Feat state archive origin is invalid';
    END IF;
END$$

CREATE TRIGGER `trg_character_feat_state_v2_origin_update`
BEFORE UPDATE ON `character_feat_state_v2`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Feat state is immutable';
END$$

CREATE TRIGGER `trg_multiclass_proficiency_v2_origin_insert`
BEFORE INSERT ON `character_multiclass_proficiency_v2`
FOR EACH ROW
BEGIN
    IF NEW.`state_origin` = 'ADVANCEMENT' THEN
        IF NOT EXISTS (
            SELECT 1
            FROM `character_advancement_choice_v2` AS choice_root
            WHERE choice_root.`game_event_id` = NEW.`acquired_event_id`
              AND choice_root.`character_id` = NEW.`character_id`
              AND choice_root.`module_release_id` = NEW.`module_release_id`
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Multiclass proficiency advancement origin is invalid';
        END IF;
    ELSEIF NOT EXISTS (
        SELECT 1
        FROM `game_event` AS event_root
        JOIN `character_record` AS character_root
          ON character_root.`id` = NEW.`character_id`
         AND character_root.`module_release_id` = NEW.`module_release_id`
         AND character_root.`campaign_id` = event_root.`campaign_id`
        WHERE event_root.`id` = NEW.`acquired_event_id`
          AND event_root.`subject_character_id` = NEW.`character_id`
          AND event_root.`event_type` = 'CHARACTER_LEVEL_ADVANCED'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Multiclass proficiency archive origin is invalid';
    END IF;
END$$

CREATE TRIGGER `trg_multiclass_proficiency_v2_origin_update`
BEFORE UPDATE ON `character_multiclass_proficiency_v2`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Multiclass proficiency state is immutable';
END$$

DELIMITER ;

-- CHECKSUM-SCOPE-END

SELECT COUNT(*) INTO @v016_schema_record_count
FROM `schema_meta`
WHERE `schema_version` = 16
  AND `script_name` = 'V016__starting_proficiency_baseline_draft.sql'
  AND `script_sha256` =
      '0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda';

INSERT INTO `schema_meta` (`schema_version`, `script_name`, `script_sha256`, `description`)
VALUES (
    17,
    'V017__character_archive_v2_origin.sql',
    '6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0',
    (SELECT CASE WHEN @v016_schema_record_count = 1 AND COUNT(*) = 1
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name IN ('character_feat_state_v2',
                                  'character_multiclass_proficiency_v2')
               AND column_name = 'state_origin') = 2
        AND (SELECT COUNT(*) FROM information_schema.triggers
             WHERE trigger_schema = DATABASE()
               AND trigger_name IN (
                   'trg_character_feat_state_v2_origin_insert',
                   'trg_character_feat_state_v2_origin_update',
                   'trg_multiclass_proficiency_v2_origin_insert',
                   'trg_multiclass_proficiency_v2_origin_update')) = 4 THEN
        'Archive-format-2 character-state origin support'
     ELSE NULL END
     FROM `module_release`
     WHERE `id` = @complete_release_id
       AND `release_status` = 'DRAFT'
       AND `content_sha256` IS NULL
       AND `released_at` IS NULL));

-- Intentionally no release, campaign, character, event, operation, account or grant is created.
