-- Run with an administrator account that has GRANT OPTION only after V008.
-- Item deletion is deliberately omitted: normal removal uses ARCHIVED status.

GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`item_instance`
    TO 'dnd_tool_se_app'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
