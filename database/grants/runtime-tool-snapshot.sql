-- Separate administrator checkpoint after V020 verification and effective privilege audit.
-- The existing runtime identity/head/language grants remain required.
GRANT SELECT, INSERT, DELETE ON `dnd_tool_se`.`runtime_rule_tool` TO 'dnd_tool_se_app'@'127.0.0.1';
-- No UPDATE, DDL, source access, ledger writes or identity deletion.
