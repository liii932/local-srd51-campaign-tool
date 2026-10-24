-- Read-only evidence; executing this file neither migrates nor grants privileges.
SELECT DATABASE() AS schema_name, @@SESSION.sql_mode AS sql_mode;
SELECT schema_version, script_name, script_sha256 FROM schema_meta ORDER BY schema_version;
SELECT table_name, engine, table_collation FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'business_write_guard';
SELECT column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'business_write_guard' ORDER BY ordinal_position;
SELECT constraint_name, constraint_type FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND table_name = 'business_write_guard' ORDER BY constraint_name;
SELECT trigger_name, event_manipulation, action_statement FROM information_schema.triggers
WHERE trigger_schema = DATABASE() AND event_object_table = 'business_write_guard' ORDER BY trigger_name;
SELECT guard_id, protocol_version FROM business_write_guard;
SELECT EXISTS (SELECT 1 FROM campaign WHERE campaign_status = 'ACTIVE') AS legacy_active,
       EXISTS (SELECT 1 FROM s2_runtime_identity) AS schema2_remainder,
       EXISTS (SELECT 1 FROM runtime_run_identity) AS partition_remainder;
SELECT grantee, table_name, privilege_type FROM information_schema.table_privileges
WHERE table_schema = DATABASE()
  AND table_name IN ('business_write_guard', 'campaign', 's2_runtime_identity', 'runtime_run_identity')
  AND grantee = '''dnd_tool_se_app''@''127.0.0.1'''
ORDER BY table_name, privilege_type;
SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
