-- Read-only evidence after a separately authorized migration. Run with an explicit target.
-- Trigger metadata is permission-filtered: a stopped migrator must inspect full definitions;
-- do not broaden the application's account to make these diagnostic rows visible.
SELECT DATABASE(), CURRENT_USER(), @@SESSION.sql_mode;
SELECT schema_version, script_name, script_sha256 FROM dnd_tool_se.schema_meta ORDER BY schema_version;
SELECT COUNT(*) = 1 AS v019_ledger_matches FROM dnd_tool_se.schema_meta
WHERE schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql'
    AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173';
SELECT table_name, engine, table_collation FROM information_schema.tables
WHERE table_schema = 'dnd_tool_se' AND table_name IN
    ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language');
SELECT table_name, column_name, column_type, is_nullable, column_default, collation_name
FROM information_schema.columns WHERE table_schema = 'dnd_tool_se' AND table_name IN
    ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language')
ORDER BY table_name, ordinal_position;
SELECT table_name, index_name, non_unique, seq_in_index, column_name FROM information_schema.statistics
WHERE table_schema = 'dnd_tool_se' AND table_name IN
    ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language')
ORDER BY table_name, index_name, seq_in_index;
SELECT k.table_name, k.constraint_name, k.column_name, k.referenced_table_schema,
       k.referenced_table_name, k.referenced_column_name, r.update_rule, r.delete_rule
FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r
    ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
WHERE k.table_schema = 'dnd_tool_se' AND k.table_name IN
    ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language')
ORDER BY k.table_name, k.constraint_name, k.ordinal_position;
SELECT c.table_name, c.constraint_name, c.enforced, x.check_clause
FROM information_schema.table_constraints c JOIN information_schema.check_constraints x
    ON c.constraint_schema = x.constraint_schema AND c.constraint_name = x.constraint_name
WHERE c.table_schema = 'dnd_tool_se' AND c.table_name IN
    ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language');
SELECT trigger_name, event_manipulation, event_object_table, action_timing, action_statement, definer
FROM information_schema.triggers WHERE trigger_schema = 'dnd_tool_se' AND event_object_table IN
    ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language');
SHOW CREATE TABLE dnd_tool_se.runtime_run_identity;
SHOW CREATE TABLE dnd_tool_se.runtime_rule_snapshot;
SHOW CREATE TABLE dnd_tool_se.runtime_rule_language;
SELECT 'identity' AS object_name, COUNT(*) AS row_count FROM dnd_tool_se.runtime_run_identity
UNION ALL SELECT 'snapshot', COUNT(*) FROM dnd_tool_se.runtime_rule_snapshot
UNION ALL SELECT 'language', COUNT(*) FROM dnd_tool_se.runtime_rule_language;
SHOW GRANTS;
-- Empty counts apply only to the migration checkpoint. Inspect actual effective app grants
-- separately, including enabled/mandatory roles and executable DEFINER routines.
