-- Read-only offline verification, explicitly connected to dnd_tool_rules.
-- SELECT/SHOW only. Inspect every result; this is not an automatic migration/repair tool.
SELECT DATABASE() AS default_schema, CURRENT_USER() AS authenticated_account,
    @@version AS mysql_version, @@session.sql_mode AS sql_mode, @@session.time_zone AS time_zone,
    @@GLOBAL.partial_revokes AS partial_revokes;
SHOW GRANTS;

SELECT schema_version, CONVERT(script_name USING latin1) AS script_name,
    CONVERT(script_sha256 USING latin1) AS script_sha256, applied_at
FROM rule_schema_meta ORDER BY schema_version LIMIT 2;
SELECT control_id, protocol_version, metadata_row_count, row_version
FROM rule_installation_control ORDER BY control_id LIMIT 2;

-- Empty-source migration checkpoint only: all four counts must be zero.
SELECT (SELECT COUNT(*) FROM rule_release) AS release_count,
    (SELECT COUNT(*) FROM rule_language) AS language_count,
    (SELECT COUNT(*) FROM rule_package_installation) AS installation_count,
    (SELECT COUNT(*) FROM rule_package_installation_partition) AS partition_count;

SELECT table_name, engine, table_collation
FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name;
SELECT table_name, column_name, column_type, is_nullable, column_default, extra,
    character_set_name, collation_name
FROM information_schema.columns WHERE table_schema = DATABASE() ORDER BY table_name, ordinal_position;
SELECT table_name, index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics WHERE table_schema = DATABASE() ORDER BY table_name, index_name, seq_in_index;
SELECT k.table_name, k.constraint_name, k.ordinal_position, k.column_name,
    k.referenced_table_schema, k.referenced_table_name, k.referenced_column_name,
    r.update_rule, r.delete_rule
FROM information_schema.key_column_usage k
JOIN information_schema.referential_constraints r
    ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
    AND r.table_name = k.table_name
WHERE k.constraint_schema = DATABASE() ORDER BY k.table_name, k.constraint_name, k.ordinal_position;
SELECT t.table_name, t.constraint_name, t.enforced, c.check_clause
FROM information_schema.table_constraints t
JOIN information_schema.check_constraints c
    ON c.constraint_schema = t.constraint_schema AND c.constraint_name = t.constraint_name
WHERE t.constraint_schema = DATABASE() AND t.constraint_type = 'CHECK'
ORDER BY t.table_name, t.constraint_name;

-- Execute the metadata section with the migrator in stopped-write maintenance:
-- MySQL filters trigger definitions by TRIGGER privilege; do not grant that privilege to readers.
SELECT trigger_name, event_manipulation, event_object_table, action_timing, action_statement,
    definer, sql_mode, character_set_client, collation_connection
FROM information_schema.triggers WHERE trigger_schema = DATABASE() ORDER BY trigger_name;
SELECT table_name, view_definition FROM information_schema.views WHERE table_schema = DATABASE();
SELECT routine_name, routine_type, routine_definition
FROM information_schema.routines WHERE routine_schema = DATABASE();
SELECT event_name, event_definition FROM information_schema.events WHERE event_schema = DATABASE();

-- Current-account grants and enabled-role inheritance require separate administrator review.
SELECT grantee, table_name, privilege_type, is_grantable
FROM information_schema.table_privileges WHERE table_schema = DATABASE() ORDER BY grantee, table_name, privilege_type;
SELECT grantee, table_name, column_name, privilege_type, is_grantable
FROM information_schema.column_privileges WHERE table_schema = DATABASE()
ORDER BY grantee, table_name, column_name, privilege_type;
