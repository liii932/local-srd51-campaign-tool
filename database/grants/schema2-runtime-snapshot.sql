-- Apply only with explicit runtime grant authorization after V021/V023/V024 object audit.
-- INTERNAL typed storage grants no campaign execution or source-schema access.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_identity` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_snapshot` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_member` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_language` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_language_set` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_language_entry` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_tool` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_tool_set` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_tool_entry` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_field_chain` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_chain_step` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_operation` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, UPDATE ON `dnd_tool_se`.`s2_runtime_control` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_character_source` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_proficiency_grant` TO 'dnd_tool_se_app'@'127.0.0.1';

-- V032 combat, melee weapon and walking-speed rule mirrors.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_combat` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_weapon_attack` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_movement` TO 'dnd_tool_se_app'@'127.0.0.1';

-- Finite spell definitions and their exact casting sources share the snapshot transaction.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_spell` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_spellcasting` TO 'dnd_tool_se_app'@'127.0.0.1';

-- Wizard catalogs, learning entries and independent environmental definitions.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_wizard_spellcasting` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_environment` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_runtime_spellbook_member` TO 'dnd_tool_se_app'@'127.0.0.1';
