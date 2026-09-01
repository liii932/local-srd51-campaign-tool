-- Run this file as a MySQL administrator that has GRANT OPTION.
-- It grants only the schema-level privileges required by
-- tools/backup-database.ps1 for tables, views, and triggers.
-- The current V001-V010 schema contains no views, routines, or events.
-- EVENT, PROCESS, RELOAD, LOCK TABLES, DDL, and GRANT OPTION are intentionally
-- not granted here.

GRANT SELECT, SHOW VIEW, TRIGGER
    ON `dnd_tool_se`.*
    TO 'dnd_tool_se_migrator'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_migrator'@'127.0.0.1';
