-- Read-only structural evidence; run with the read-only verifier after separately applying V035.
SELECT schema_version, script_name, script_sha256 FROM schema_meta WHERE schema_version=35;
SELECT table_name, table_type, engine FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN ('s2_runtime_spell','s2_runtime_spellcasting') ORDER BY table_name;
SELECT table_name, ordinal_position, column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('s2_runtime_spell','s2_runtime_spellcasting') ORDER BY table_name,ordinal_position;
SELECT table_name, constraint_name, constraint_type FROM information_schema.table_constraints
WHERE table_schema=DATABASE() AND table_name IN ('s2_runtime_spell','s2_runtime_spellcasting','s2_runtime_field_chain','s2_runtime_equipment_item') ORDER BY table_name,constraint_name;
SELECT trigger_name, event_manipulation, event_object_table, action_timing FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND event_object_table IN ('s2_runtime_spell','s2_runtime_spellcasting') ORDER BY trigger_name;
