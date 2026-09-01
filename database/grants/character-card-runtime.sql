-- One-time incremental runtime grant for an installation that already applied
-- character-runtime.sql before the character-card editor existed.
-- Run with an administrator account that has GRANT OPTION. This privilege only
-- removes a class relationship row when the DM sets that class level to zero;
-- it cannot delete character roots, fields, proficiencies, events, or items.

GRANT DELETE ON `dnd_tool_se`.`character_class_level`
    TO 'dnd_tool_se_app'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
