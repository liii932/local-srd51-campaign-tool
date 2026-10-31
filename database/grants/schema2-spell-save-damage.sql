-- Apply only after separately authorized V042 installation and application-account review.
-- Existing cast, shared batch, character-health and snapshot grants remain prerequisites.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_combat_spell_damage_source` TO 'dnd_tool_se_app'@'127.0.0.1';
