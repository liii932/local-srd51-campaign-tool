-- Read-only object, receipt-binding and preparation audit; never initializes a business run.
SELECT schema_version, script_name, script_sha256 FROM schema_meta ORDER BY schema_version;
SELECT table_name, engine, table_collation FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name LIKE 's2_initial_plan%' ORDER BY table_name;
SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name LIKE 's2_initial_plan%' ORDER BY table_name, ordinal_position;
SELECT table_name, constraint_name, constraint_type FROM information_schema.table_constraints
WHERE table_schema = DATABASE() AND table_name LIKE 's2_initial_plan%' ORDER BY table_name, constraint_name;
SELECT trigger_name, event_manipulation, event_object_table, action_statement FROM information_schema.triggers
WHERE trigger_schema = DATABASE() AND event_object_table LIKE 's2_initial_plan%' ORDER BY trigger_name;
SELECT HEX(p.operation_id) AS preparation_operation, HEX(p.run_id) AS run_id, HEX(p.snapshot_id) AS snapshot_id,
       p.slot_count, p.choice_count, p.grant_count
FROM s2_initial_plan p LEFT JOIN s2_runtime_operation o ON o.operation_id = p.operation_id
WHERE o.operation_id IS NULL OR o.run_id <> p.run_id OR o.snapshot_id <> p.snapshot_id;
SELECT HEX(p.operation_id) AS preparation_operation
FROM s2_initial_plan p
WHERE p.slot_count <> (SELECT COUNT(*) FROM s2_initial_plan_slot s WHERE s.operation_id = p.operation_id)
   OR p.choice_count <> (SELECT COUNT(*) FROM s2_initial_plan_choice c WHERE c.operation_id = p.operation_id)
   OR p.grant_count <> (SELECT COUNT(*) FROM s2_initial_plan_grant g WHERE g.operation_id = p.operation_id);
SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
