-- Incremental runtime-account grants for V009 check execution records.
-- Run the whole file only with an administrator account that has GRANT OPTION,
-- after V009 has passed its read-only structure verification.
--
-- Existing installations already grant the application the required access to
-- host_operation, game_event, field_change and character/item tables. V009 rows
-- are immutable audit records, so the application receives no UPDATE or DELETE.

GRANT SELECT, INSERT ON `dnd_tool_se`.`check_execution`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`dice_roll`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`check_effect`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`check_effect_parameter_value`
    TO 'dnd_tool_se_app'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
