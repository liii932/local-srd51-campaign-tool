-- Apply only after explicitly authorized V025 migration and object audit.
-- Locking reads require SELECT; the runtime account cannot alter or remove this singleton.
GRANT SELECT ON `dnd_tool_se`.`business_write_guard` TO 'dnd_tool_se_app'@'127.0.0.1';
-- Admission reads all lifecycle roots even when a legacy command does not otherwise use them.
GRANT SELECT ON `dnd_tool_se`.`campaign` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`s2_runtime_identity` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`runtime_run_identity` TO 'dnd_tool_se_app'@'127.0.0.1';
