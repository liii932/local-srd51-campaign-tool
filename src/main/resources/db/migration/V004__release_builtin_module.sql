-- DnD Tool SE stage 2 built-in module publication
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped, after V003 and after the independent canonical hash tool
-- reports the approved digest below. This migration publishes only the one
-- reviewed built-in DRAFT and installs database-level immutable protection.
--
-- MySQL DDL commits implicitly. If trigger creation fails, stop immediately;
-- do not skip the failed statement or insert schema_meta by hand.

USE `dnd_tool_se`;

-- CHECKSUM-SCOPE-BEGIN

DELIMITER $$

-- A release must be assembled as DRAFT. Direct insertion of a published row
-- would bypass the reviewed DRAFT -> RELEASED transition.
CREATE TRIGGER `trg_module_release_bi`
BEFORE INSERT ON `module_release`
FOR EACH ROW
BEGIN
    IF NEW.`release_status` <> 'DRAFT'
            OR NEW.`content_sha256` IS NOT NULL
            OR NEW.`released_at` IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'module release must be inserted as an unhashed DRAFT';
    END IF;
END$$

-- RELEASED rows are immutable. The sole permitted transition preserves the
-- identity/format fields and atomically records the approved digest and time.
CREATE TRIGGER `trg_module_release_bu`
BEFORE UPDATE ON `module_release`
FOR EACH ROW
BEGIN
    IF OLD.`release_status` = 'RELEASED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'released module metadata is immutable';
    END IF;

    IF NEW.`release_status` = 'DRAFT' THEN
        IF NEW.`content_sha256` IS NOT NULL OR NEW.`released_at` IS NOT NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'draft module must not carry release metadata';
        END IF;
    ELSEIF NEW.`release_status` = 'RELEASED' THEN
        IF OLD.`release_status` <> 'DRAFT'
                OR NOT (NEW.`module_key` <=> OLD.`module_key`)
                OR NOT (NEW.`release_version` <=> OLD.`release_version`)
                OR NOT (NEW.`canonical_format_version`
                    <=> OLD.`canonical_format_version`)
                OR NOT (NEW.`hash_algorithm` <=> OLD.`hash_algorithm`)
                OR NEW.`content_sha256` IS NULL
                OR NEW.`released_at` IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'invalid module publication transition';
        END IF;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'unknown module release status';
    END IF;
END$$

CREATE TRIGGER `trg_module_release_bd`
BEFORE DELETE ON `module_release`
FOR EACH ROW
BEGIN
    IF OLD.`release_status` = 'RELEASED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'released module metadata is immutable';
    END IF;
END$$

-- Every definition table repeats the same small guard intentionally. The
-- migrator account has TRIGGER but not CREATE ROUTINE, and keeping the guard
-- inside each trigger avoids a privileged stored-routine dependency.

CREATE TRIGGER `trg_module_rule_constant_bi`
BEFORE INSERT ON `module_rule_constant`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release`
               WHERE `id` = NEW.`module_release_id`
                 AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_rule_constant_bu`
BEFORE UPDATE ON `module_rule_constant`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release`
               WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`)
                 AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_rule_constant_bd`
BEFORE DELETE ON `module_rule_constant`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release`
               WHERE `id` = OLD.`module_release_id`
                 AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_field_definition_bi`
BEFORE INSERT ON `module_field_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_field_definition_bu`
BEFORE UPDATE ON `module_field_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_field_definition_bd`
BEFORE DELETE ON `module_field_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_class_definition_bi`
BEFORE INSERT ON `module_class_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_class_definition_bu`
BEFORE UPDATE ON `module_class_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_class_definition_bd`
BEFORE DELETE ON `module_class_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_proficiency_tier_bi`
BEFORE INSERT ON `module_proficiency_tier`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_proficiency_tier_bu`
BEFORE UPDATE ON `module_proficiency_tier`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_proficiency_tier_bd`
BEFORE DELETE ON `module_proficiency_tier`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_proficiency_bonus_band_bi`
BEFORE INSERT ON `module_proficiency_bonus_band`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_proficiency_bonus_band_bu`
BEFORE UPDATE ON `module_proficiency_bonus_band`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_proficiency_bonus_band_bd`
BEFORE DELETE ON `module_proficiency_bonus_band`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_skill_definition_bi`
BEFORE INSERT ON `module_skill_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_skill_definition_bu`
BEFORE UPDATE ON `module_skill_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_skill_definition_bd`
BEFORE DELETE ON `module_skill_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_save_definition_bi`
BEFORE INSERT ON `module_save_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_save_definition_bu`
BEFORE UPDATE ON `module_save_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_save_definition_bd`
BEFORE DELETE ON `module_save_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_item_template_bi`
BEFORE INSERT ON `module_item_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_item_template_bu`
BEFORE UPDATE ON `module_item_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_item_template_bd`
BEFORE DELETE ON `module_item_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_entity_template_bi`
BEFORE INSERT ON `module_entity_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_bu`
BEFORE UPDATE ON `module_entity_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_bd`
BEFORE DELETE ON `module_entity_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_entity_template_value_bi`
BEFORE INSERT ON `module_entity_template_value`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_value_bu`
BEFORE UPDATE ON `module_entity_template_value`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_value_bd`
BEFORE DELETE ON `module_entity_template_value`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_entity_template_class_level_bi`
BEFORE INSERT ON `module_entity_template_class_level`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_class_level_bu`
BEFORE UPDATE ON `module_entity_template_class_level`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_class_level_bd`
BEFORE DELETE ON `module_entity_template_class_level`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_entity_template_proficiency_bi`
BEFORE INSERT ON `module_entity_template_proficiency`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_proficiency_bu`
BEFORE UPDATE ON `module_entity_template_proficiency`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_entity_template_proficiency_bd`
BEFORE DELETE ON `module_entity_template_proficiency`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_check_definition_bi`
BEFORE INSERT ON `module_check_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_check_definition_bu`
BEFORE UPDATE ON `module_check_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_check_definition_bd`
BEFORE DELETE ON `module_check_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_roll_mode_bi`
BEFORE INSERT ON `module_roll_mode`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_roll_mode_bu`
BEFORE UPDATE ON `module_roll_mode`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_roll_mode_bd`
BEFORE DELETE ON `module_roll_mode`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_event_template_bi`
BEFORE INSERT ON `module_event_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_event_template_bu`
BEFORE UPDATE ON `module_event_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_event_template_bd`
BEFORE DELETE ON `module_event_template`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_effect_definition_bi`
BEFORE INSERT ON `module_effect_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_effect_definition_bu`
BEFORE UPDATE ON `module_effect_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_effect_definition_bd`
BEFORE DELETE ON `module_effect_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_event_check_bi`
BEFORE INSERT ON `module_event_check`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_event_check_bu`
BEFORE UPDATE ON `module_event_check`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_event_check_bd`
BEFORE DELETE ON `module_event_check`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_event_effect_bi`
BEFORE INSERT ON `module_event_effect`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_event_effect_bu`
BEFORE UPDATE ON `module_event_effect`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_event_effect_bd`
BEFORE DELETE ON `module_event_effect`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_effect_parameter_bi`
BEFORE INSERT ON `module_effect_parameter`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_effect_parameter_bu`
BEFORE UPDATE ON `module_effect_parameter`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_effect_parameter_bd`
BEFORE DELETE ON `module_effect_parameter`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_map_definition_bi`
BEFORE INSERT ON `module_map_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_map_definition_bu`
BEFORE UPDATE ON `module_map_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_map_definition_bd`
BEFORE DELETE ON `module_map_definition`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_map_node_bi`
BEFORE INSERT ON `module_map_node`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_map_node_bu`
BEFORE UPDATE ON `module_map_node`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_map_node_bd`
BEFORE DELETE ON `module_map_node`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_module_map_connection_bi`
BEFORE INSERT ON `module_map_connection`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = NEW.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_map_connection_bu`
BEFORE UPDATE ON `module_map_connection`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` IN (OLD.`module_release_id`, NEW.`module_release_id`) AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$
CREATE TRIGGER `trg_module_map_connection_bd`
BEFORE DELETE ON `module_map_connection`
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `module_release` WHERE `id` = OLD.`module_release_id` AND `release_status` = 'RELEASED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'released module definition is immutable';
    END IF;
END$$

DELIMITER ;

START TRANSACTION;

-- This update is the reviewed publication action. It succeeds only against
-- the untouched V003 DRAFT identity and records the independently approved
-- canonical-format-v1 digest.
UPDATE `module_release`
SET `content_sha256` = '8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e',
    `release_status` = 'RELEASED',
    `released_at` = CURRENT_TIMESTAMP(6)
WHERE `module_key` = 'dnd5e2014_srd51_se_v1'
  AND `release_version` = '1'
  AND `canonical_format_version` = 1
  AND `hash_algorithm` = 'SHA-256'
  AND `content_sha256` IS NULL
  AND `release_status` = 'DRAFT'
  AND `released_at` IS NULL;

SET @published_row_count = ROW_COUNT();

-- CHECKSUM-SCOPE-END

-- description is NOT NULL. A missing or non-unique exact release therefore
-- aborts this transaction instead of recording a false schema version.
INSERT INTO `schema_meta` (
    `schema_version`, `script_name`, `script_sha256`, `description`
) VALUES (
    4,
    'V004__release_builtin_module.sql',
    '5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f',
    (SELECT CASE WHEN @published_row_count = 1
            AND COUNT(*) = 1
            AND (SELECT COUNT(*) FROM `module_release`) = 1 THEN
        'Stage 2 verified built-in module release and immutable triggers'
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

COMMIT;
