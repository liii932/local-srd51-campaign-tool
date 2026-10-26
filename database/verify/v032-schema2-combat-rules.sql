-- Read-only V032 typed rule audit; does not execute migration or business commands.
SELECT schema_version, script_name, script_sha256 FROM schema_meta WHERE schema_version BETWEEN 1 AND 32 ORDER BY schema_version;
SELECT table_name, engine, table_collation FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN ('s2_runtime_combat','s2_runtime_weapon_attack','s2_runtime_movement') ORDER BY table_name;
SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('s2_runtime_combat','s2_runtime_weapon_attack','s2_runtime_movement') ORDER BY table_name,ordinal_position;
SELECT table_name, constraint_name, constraint_type FROM information_schema.table_constraints
WHERE table_schema=DATABASE() AND table_name IN ('s2_runtime_combat','s2_runtime_weapon_attack','s2_runtime_movement','s2_runtime_field_chain') ORDER BY table_name,constraint_name;
SELECT trigger_name,event_manipulation,event_object_table,action_statement FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND event_object_table IN ('s2_runtime_combat','s2_runtime_weapon_attack','s2_runtime_movement') ORDER BY trigger_name;
SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
