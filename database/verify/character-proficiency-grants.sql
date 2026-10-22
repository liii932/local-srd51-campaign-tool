-- Read-only structure audit for character grants, schema 2 tools and typed author sources/slots.
-- This does not apply migrations or establish engine/transaction acceptance.
SELECT DATABASE() AS schema_name, @@SESSION.sql_mode AS sql_mode;
SELECT schema_version, script_name, script_sha256 FROM schema_meta
WHERE schema_version IN (22, 23, 24) ORDER BY schema_version;
SELECT table_name, engine, table_collation FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN (
    'character_proficiency_grant_v2', 's2_runtime_tool', 's2_runtime_tool_set', 's2_runtime_tool_entry',
    's2_runtime_character_source', 's2_runtime_proficiency_grant')
ORDER BY table_name;
SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name IN (
    'character_proficiency_grant_v2', 's2_runtime_tool', 's2_runtime_tool_set', 's2_runtime_tool_entry',
    's2_runtime_character_source', 's2_runtime_proficiency_grant', 's2_runtime_chain_step')
ORDER BY table_name, ordinal_position;
SELECT table_name, constraint_name, constraint_type FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND table_name IN (
    'character_proficiency_grant_v2', 's2_runtime_tool', 's2_runtime_tool_set', 's2_runtime_tool_entry',
    's2_runtime_character_source', 's2_runtime_proficiency_grant', 's2_runtime_field_chain',
    's2_runtime_chain_step') ORDER BY table_name, constraint_name;
SELECT table_name, constraint_name, column_name, referenced_table_schema, referenced_table_name,
    referenced_column_name FROM information_schema.key_column_usage
WHERE table_schema = DATABASE() AND table_name IN ('s2_runtime_character_source', 's2_runtime_proficiency_grant')
ORDER BY table_name, constraint_name, ordinal_position;
SELECT table_name, index_name, non_unique, seq_in_index, column_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name IN ('s2_runtime_character_source', 's2_runtime_proficiency_grant')
ORDER BY table_name, index_name, seq_in_index;
SELECT trigger_name, event_manipulation, event_object_table, action_statement FROM information_schema.triggers
WHERE trigger_schema = DATABASE() AND event_object_table IN (
    'character_proficiency_grant_v2', 's2_runtime_tool', 's2_runtime_tool_set', 's2_runtime_tool_entry',
    's2_runtime_character_source', 's2_runtime_proficiency_grant')
ORDER BY trigger_name;
SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
