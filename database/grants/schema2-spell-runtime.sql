-- Apply only after separately authorized V036–V038 installation and account review.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_cast_command` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_cast_command_target` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_ongoing_effect` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_ongoing_target` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_cast` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_cast_target` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_cast_die` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_saving_throw` TO 'dnd_tool_se_app'@'127.0.0.1';

-- Type-preserving reactions, persistent scenes and character spellbook facts.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_learned_spell` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_event_prepared_spell` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_spellbook_command` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_spellbook_choice` TO 'dnd_tool_se_app'@'127.0.0.1';
