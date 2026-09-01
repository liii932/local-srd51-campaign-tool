-- Incremental runtime-account grants for the stage 4 whole-campaign import.
-- Run the whole file only with an administrator account that has GRANT OPTION,
-- after all earlier responsibility-specific runtime grant files have been applied.
--
-- Replacement import deletes the target campaign's existing child rows before
-- rebuilding them. DELETE already granted by earlier files for
-- character_class_level, battle_participant and entity_position is not repeated.
-- Campaign identity, timestamps and the root row itself remain non-deletable;
-- UPDATE is limited to the four additional mutable columns required by import.

GRANT UPDATE (`campaign_name`, `campaign_status`, `host_state_epoch`, `row_version`)
    ON `dnd_tool_se`.`campaign`
    TO 'dnd_tool_se_app'@'127.0.0.1';

GRANT DELETE ON `dnd_tool_se`.`host_operation`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_feature_adjudication_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_resource_recovery_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_feature_choice_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_feat_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_multiclass_proficiency_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_feature_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_subclass_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_ability_score_change_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_advancement_choice_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_level_resource_change_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_level_advancement_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_class_level_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_resource_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_creation_selection_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_creation_snapshot_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`check_effect_parameter_value`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`check_effect`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`dice_roll`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`field_change`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`check_execution`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`battle_state`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`party_world_position`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`map_instance`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`item_instance`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_field_value`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_skill_proficiency`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_save_proficiency`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`game_event`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`character_record`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT DELETE ON `dnd_tool_se`.`campaign_module`
    TO 'dnd_tool_se_app'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
