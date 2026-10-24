-- Apply only after explicit review of V027 and the application account.
-- Append-only character growth/rest facts; the INTERNAL clock is limited to one character per run.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_class_progression` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_resource` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_subclass` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_feature` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_feat` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_proficiency` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_proficiency_set` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_rest` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_class_level_entry` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_proficiency_entry` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_growth_grant` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_rule_prerequisite` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_chain_prerequisite` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_runtime` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_state` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_resource` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_class` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_hit_die` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_subclass` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_feature` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_feat` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_proficiency` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_command_ability` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_command_proficiency` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_command_hit_die` TO 'dnd_tool_se_app'@'127.0.0.1';
