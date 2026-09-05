-- Apply separately as an account administrator only after V018 is verified.
-- This file does not create users and does not grant schema-changing privileges.
GRANT SELECT ON `dnd_tool_se`.`module_catalog_definition_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_catalog_attribute_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_catalog_relation_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_creation_snapshot_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_creation_selection_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_resource_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_class_level_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_level_advancement_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_level_resource_change_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_subclass_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feature_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feature_choice_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feature_adjudication_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_resource_recovery_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_advancement_choice_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_ability_score_change_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_feat_state_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`character_multiclass_proficiency_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
