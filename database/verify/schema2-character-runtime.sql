-- Read-only audit of typed character growth, rest rules and complete immutable history.
SELECT schema_version, script_name, script_sha256 FROM schema_meta ORDER BY schema_version;
SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name IN ('s2_runtime_class_progression', 's2_runtime_resource', 's2_runtime_subclass', 's2_runtime_feature', 's2_runtime_feat', 's2_runtime_proficiency', 's2_runtime_proficiency_set', 's2_runtime_rest', 's2_runtime_class_level_entry', 's2_runtime_proficiency_entry', 's2_runtime_growth_grant', 's2_runtime_rule_prerequisite', 's2_runtime_chain_prerequisite', 's2_character_runtime', 's2_character_event', 's2_character_event_state', 's2_character_event_resource', 's2_character_event_class', 's2_character_event_hit_die', 's2_character_event_subclass', 's2_character_event_feature', 's2_character_event_feat', 's2_character_event_proficiency', 's2_character_command_ability', 's2_character_command_proficiency', 's2_character_command_hit_die')
ORDER BY table_name, ordinal_position;
SELECT trigger_name, event_object_table, event_manipulation, action_statement FROM information_schema.triggers
WHERE trigger_schema = DATABASE() AND event_object_table IN ('s2_runtime_class_progression', 's2_runtime_resource', 's2_runtime_subclass', 's2_runtime_feature', 's2_runtime_feat', 's2_runtime_proficiency', 's2_runtime_proficiency_set', 's2_runtime_rest', 's2_runtime_class_level_entry', 's2_runtime_proficiency_entry', 's2_runtime_growth_grant', 's2_runtime_rule_prerequisite', 's2_runtime_chain_prerequisite', 's2_character_runtime', 's2_character_event', 's2_character_event_state', 's2_character_event_resource', 's2_character_event_class', 's2_character_event_hit_die', 's2_character_event_subclass', 's2_character_event_feature', 's2_character_event_feat', 's2_character_event_proficiency', 's2_character_command_ability', 's2_character_command_proficiency', 's2_character_command_hit_die')
ORDER BY trigger_name;
SELECT HEX(c.character_id) AS character_id FROM s2_character_runtime c
LEFT JOIN s2_initial_plan p ON p.operation_id = c.preparation_operation_id
WHERE p.operation_id IS NULL OR p.character_id <> c.character_id OR p.campaign_id <> c.campaign_id OR p.run_id <> c.run_id OR p.snapshot_id <> c.snapshot_id;
SELECT HEX(run_id) AS run_id, COUNT(*) AS character_count FROM s2_character_runtime GROUP BY run_id HAVING COUNT(*) <> 1;
SELECT HEX(c.character_id) AS character_id FROM s2_character_runtime c
WHERE NOT EXISTS (SELECT 1 FROM s2_character_event e WHERE e.character_id = c.character_id AND e.event_sequence = 1 AND e.event_kind = _binary'INITIALIZED');
SELECT HEX(character_id) AS character_id, COUNT(*) AS events, MIN(event_sequence) AS first_sequence, MAX(event_sequence) AS last_sequence
FROM s2_character_event GROUP BY character_id HAVING COUNT(*) <> MAX(event_sequence) OR MIN(event_sequence) <> 1 OR COUNT(*) > 128;
SELECT HEX(e.character_id) AS character_id, e.event_sequence FROM s2_character_event e
WHERE (SELECT COUNT(*) FROM s2_character_event_state s WHERE s.character_id = e.character_id AND s.event_sequence = e.event_sequence) <> IF(e.event_sequence = 1, 1, 2);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.resource_count <> (SELECT COUNT(*) FROM s2_character_event_resource r WHERE r.character_id = s.character_id AND r.event_sequence = s.event_sequence AND r.phase = s.phase);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.class_count <> (SELECT COUNT(*) FROM s2_character_event_class r WHERE r.character_id = s.character_id AND r.event_sequence = s.event_sequence AND r.phase = s.phase);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.hit_die_count <> (SELECT COUNT(*) FROM s2_character_event_hit_die r WHERE r.character_id = s.character_id AND r.event_sequence = s.event_sequence AND r.phase = s.phase);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.subclass_count <> (SELECT COUNT(*) FROM s2_character_event_subclass r WHERE r.character_id = s.character_id AND r.event_sequence = s.event_sequence AND r.phase = s.phase);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.feature_count <> (SELECT COUNT(*) FROM s2_character_event_feature r WHERE r.character_id = s.character_id AND r.event_sequence = s.event_sequence AND r.phase = s.phase);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.feat_count <> (SELECT COUNT(*) FROM s2_character_event_feat r WHERE r.character_id = s.character_id AND r.event_sequence = s.event_sequence AND r.phase = s.phase);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.proficiency_count <> (SELECT COUNT(*) FROM s2_character_event_proficiency r WHERE r.character_id = s.character_id AND r.event_sequence = s.event_sequence AND r.phase = s.phase);
SELECT HEX(e.character_id) AS character_id, e.event_sequence FROM s2_character_event e
WHERE e.ability_choice_count <> (SELECT COUNT(*) FROM s2_character_command_ability c WHERE c.character_id = e.character_id AND c.event_sequence = e.event_sequence);
SELECT HEX(e.character_id) AS character_id, e.event_sequence FROM s2_character_event e
WHERE e.proficiency_choice_count <> (SELECT COUNT(*) FROM s2_character_command_proficiency c WHERE c.character_id = e.character_id AND c.event_sequence = e.event_sequence);
SELECT HEX(e.character_id) AS character_id, e.event_sequence FROM s2_character_event e
WHERE e.recovered_hit_die_count <> (SELECT COUNT(*) FROM s2_character_command_hit_die c WHERE c.character_id = e.character_id AND c.event_sequence = e.event_sequence);
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.total_level <> (SELECT SUM(c.class_level) FROM s2_character_event_class c WHERE c.character_id = s.character_id AND c.event_sequence = s.event_sequence AND c.phase = s.phase)
   OR s.total_level <> (SELECT SUM(h.maximum_value) FROM s2_character_event_hit_die h WHERE h.character_id = s.character_id AND h.event_sequence = s.event_sequence AND h.phase = s.phase);
-- Required nullable union fields must never pass through SQL UNKNOWN.
SELECT HEX(e.character_id) AS character_id, e.event_sequence FROM s2_character_event e
WHERE e.event_kind = _binary'RESOURCE_SPENT'
  AND (e.resource_module IS NULL OR e.resource_type IS NULL OR e.resource_type <> _binary'character.resource' OR e.resource_key IS NULL);
-- T1 grant provenance belongs to state; growth commands may select only growth-owned slots.
SELECT HEX(c.character_id) AS character_id, c.event_sequence FROM s2_character_command_proficiency c
WHERE (c.source_type = _binary'character.class_progression'
       AND c.collection_key IN (_binary'starting_proficiencies', _binary'multiclass_proficiencies')) IS NOT TRUE;
SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
