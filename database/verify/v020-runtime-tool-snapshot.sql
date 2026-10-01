-- Read-only supplement to full schema/definition/grant verification; this file applies nothing.
SELECT schema_version, script_name, script_sha256 FROM schema_meta ORDER BY schema_version;
SHOW CREATE TABLE runtime_rule_tool;
SHOW CREATE TRIGGER runtime_tool_update;
SELECT COUNT(*) AS tool_rows FROM runtime_rule_tool;
SELECT snapshot_id, COUNT(*) AS tool_rows, MIN(sort_order) AS first_order, MAX(sort_order) AS last_order
FROM runtime_rule_tool GROUP BY snapshot_id;
-- Nonempty supported tool partitions have 37 rows, distinct keys/orders and the exact category matrix.
-- Empty old language-only snapshots remain partial; they are not eligible for the new catalog reader.
