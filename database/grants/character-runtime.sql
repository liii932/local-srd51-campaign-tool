-- Runtime-account grants for authoritative character creation and later edits.
-- Run only with an administrator account that has GRANT OPTION, after V008 or a
-- later schema version that still contains these tables.
-- Grants remain table-specific and exclude DDL and GRANT OPTION. DELETE is
-- limited to class-level relationship rows so level zero can remove a class;
-- character roots and ordinary child records are never physically deleted.

GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_record`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_field_value`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON `dnd_tool_se`.`character_class_level`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_skill_proficiency`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`character_save_proficiency`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`game_event`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`field_change`
    TO 'dnd_tool_se_app'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
