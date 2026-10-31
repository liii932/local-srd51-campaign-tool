-- Apply only at the separately authorized V041/account checkpoint.
-- Existing character event/state SELECT, INSERT permissions cover the added typed columns.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_character_arcane_recovery_choice` TO 'dnd_tool_se_app'@'127.0.0.1';
