-- Read-only supplement; audit complete object definitions and effective privileges separately.
SELECT schema_version, script_name, script_sha256 FROM rule_schema_meta ORDER BY schema_version;
SHOW CREATE TABLE rule_tool;
SHOW CREATE TABLE rule_package_installation_partition;
SHOW CREATE TRIGGER rule_tool_insert;
SHOW CREATE TRIGGER rule_tool_update;
SHOW CREATE TRIGGER rule_tool_delete;
SELECT release_id, COUNT(*) AS tool_rows, MIN(sort_order) AS first_order, MAX(sort_order) AS last_order
FROM rule_tool GROUP BY release_id;
SELECT release_id, installation_revision, partition_key
FROM rule_package_installation_partition ORDER BY release_id, installation_revision, partition_key;
