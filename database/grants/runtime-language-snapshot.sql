-- Reviewed incremental template; a separate administrator checkpoint applies grants.
-- No account creation. Audit effective direct/role/schema/global/DEFINER privileges first.
-- A narrow GRANT cannot cancel existing broad rights; do not automatically REVOKE them.
GRANT SELECT, INSERT ON `dnd_tool_se`.`runtime_run_identity` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, DELETE ON `dnd_tool_se`.`runtime_rule_snapshot` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, DELETE ON `dnd_tool_se`.`runtime_rule_language` TO 'dnd_tool_se_app'@'127.0.0.1';
-- No UPDATE; permanent identity also has no DELETE. No source or ledger write rights.
-- INSERT/DELETE are the future lifecycle responsibility union, not arbitrary-credential
-- immutability: grants cannot distinguish Java callers or prevent DELETE+INSERT by themselves.
-- This storage slice exposes no cleanup API and does not authorize real business initialization.
