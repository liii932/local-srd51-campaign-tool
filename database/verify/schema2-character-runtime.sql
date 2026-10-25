-- Read-only audit of typed character growth, rest, checks, equipment, health and complete immutable history.
SELECT schema_version, script_name, script_sha256 FROM schema_meta ORDER BY schema_version;
SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name IN ('s2_runtime_class_progression', 's2_runtime_resource', 's2_runtime_subclass', 's2_runtime_feature', 's2_runtime_feat', 's2_runtime_proficiency', 's2_runtime_proficiency_set', 's2_runtime_rest', 's2_runtime_class_level_entry', 's2_runtime_proficiency_entry', 's2_runtime_growth_grant', 's2_runtime_rule_prerequisite', 's2_runtime_chain_prerequisite', 's2_character_runtime', 's2_character_event', 's2_character_event_state', 's2_character_event_resource', 's2_character_event_class', 's2_character_event_hit_die', 's2_character_event_subclass', 's2_character_event_feature', 's2_character_event_feat', 's2_character_event_proficiency', 's2_character_command_ability', 's2_character_command_proficiency', 's2_character_command_hit_die', 's2_character_check', 's2_runtime_check', 's2_runtime_equipment_item', 's2_runtime_health', 's2_runtime_vital_effect', 's2_character_event_equipment', 's2_character_equipment_command', 's2_character_health_event', 's2_character_health_die')
ORDER BY table_name, ordinal_position;
SELECT trigger_name, event_object_table, event_manipulation, action_statement FROM information_schema.triggers
WHERE trigger_schema = DATABASE() AND event_object_table IN ('s2_runtime_class_progression', 's2_runtime_resource', 's2_runtime_subclass', 's2_runtime_feature', 's2_runtime_feat', 's2_runtime_proficiency', 's2_runtime_proficiency_set', 's2_runtime_rest', 's2_runtime_class_level_entry', 's2_runtime_proficiency_entry', 's2_runtime_growth_grant', 's2_runtime_rule_prerequisite', 's2_runtime_chain_prerequisite', 's2_character_runtime', 's2_character_event', 's2_character_event_state', 's2_character_event_resource', 's2_character_event_class', 's2_character_event_hit_die', 's2_character_event_subclass', 's2_character_event_feature', 's2_character_event_feat', 's2_character_event_proficiency', 's2_character_command_ability', 's2_character_command_proficiency', 's2_character_command_hit_die', 's2_character_check', 's2_runtime_check', 's2_runtime_equipment_item', 's2_runtime_health', 's2_runtime_vital_effect', 's2_character_event_equipment', 's2_character_equipment_command', 's2_character_health_event', 's2_character_health_die')
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
-- Every resolved check has exactly one typed row; other events have none.
SELECT HEX(e.character_id) AS character_id, e.event_sequence FROM s2_character_event e
WHERE (SELECT COUNT(*) FROM s2_character_check c WHERE c.character_id = e.character_id AND c.event_sequence = e.event_sequence) <> IF(e.event_kind = _binary'CHECK_RESOLVED', 1, 0);
SELECT HEX(c.character_id) AS character_id, c.event_sequence FROM s2_character_check c
LEFT JOIN s2_character_event e ON e.character_id = c.character_id AND e.event_sequence = c.event_sequence
WHERE e.character_id IS NULL OR e.event_kind <> _binary'CHECK_RESOLVED' OR e.hit_die_roll IS NOT NULL;
-- Input and result consistency is independent of engine CHECK enforcement.
SELECT HEX(c.character_id) AS character_id, c.event_sequence FROM s2_character_check c
WHERE (c.check_type = _binary'adventure.check' AND c.check_kind IN (_binary'ABILITY', _binary'SKILL', _binary'TOOL')
       AND c.ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')
       AND c.difficulty_class BETWEEN 0 AND 30 AND c.advantage IN (0, 1) AND c.disadvantage IN (0, 1)
       AND CHAR_LENGTH(c.adjudication) BETWEEN 1 AND 500 AND CHAR_LENGTH(TRIM(c.adjudication)) > 0 AND NOT REGEXP_LIKE(c.adjudication, '[[:cntrl:]]')
       AND ((c.check_kind = _binary'ABILITY' AND c.proficiency_module IS NULL AND c.proficiency_type IS NULL AND c.proficiency_key IS NULL)
         OR (c.check_kind = _binary'SKILL' AND c.proficiency_module IS NOT NULL AND c.proficiency_type = _binary'character.proficiency' AND c.proficiency_key IS NOT NULL)
         OR (c.check_kind = _binary'TOOL' AND c.proficiency_module IS NOT NULL AND c.proficiency_type = _binary'character.tool' AND c.proficiency_key IS NOT NULL))
       AND c.effective_advantage IN (0, 1) AND c.effective_disadvantage IN (0, 1) AND c.first_d20 BETWEEN 1 AND 20 AND c.selected_d20 BETWEEN 1 AND 20
       AND ((c.effective_advantage = c.effective_disadvantage AND c.second_d20 IS NULL AND c.selected_d20 = c.first_d20)
         OR (c.effective_advantage <> c.effective_disadvantage AND c.second_d20 IS NOT NULL AND c.second_d20 BETWEEN 1 AND 20
             AND c.selected_d20 = IF(c.effective_advantage = 1, GREATEST(c.first_d20, c.second_d20), LEAST(c.first_d20, c.second_d20))))
       AND c.ability_modifier BETWEEN -5 AND 10 AND c.proficiency_bonus BETWEEN 0 AND 6
       AND c.total = c.selected_d20 + c.ability_modifier + c.proficiency_bonus AND c.success IN (0, 1) AND c.success = (c.total >= c.difficulty_class)) IS NOT TRUE;
-- Saved modifiers must come from the exact BEFORE state and complete proficiency identity.
SELECT HEX(c.character_id) AS character_id, c.event_sequence FROM s2_character_check c
LEFT JOIN s2_character_event_state b ON b.character_id = c.character_id AND b.event_sequence = c.event_sequence AND b.phase = _binary'BEFORE'
WHERE b.character_id IS NULL OR b.rest_module IS NOT NULL
   OR c.ability_modifier <> FLOOR((CASE c.ability_key WHEN _binary'ability.strength' THEN b.strength WHEN _binary'ability.dexterity' THEN b.dexterity WHEN _binary'ability.constitution' THEN b.constitution WHEN _binary'ability.intelligence' THEN b.intelligence WHEN _binary'ability.wisdom' THEN b.wisdom WHEN _binary'ability.charisma' THEN b.charisma END - 10) / 2)
   OR c.proficiency_bonus <> IF(c.check_kind <> _binary'ABILITY' AND EXISTS (SELECT 1 FROM s2_character_event_proficiency p WHERE p.character_id = c.character_id AND p.event_sequence = c.event_sequence AND p.phase = _binary'BEFORE' AND p.target_module = c.proficiency_module AND p.target_type = c.proficiency_type AND p.target_key = c.proficiency_key), b.proficiency_bonus, 0);
-- Equipment snapshot counts and command/result unions remain exact.
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE s.equipment_count <> (SELECT COUNT(*) FROM s2_character_event_equipment q WHERE q.character_id = s.character_id AND q.event_sequence = s.event_sequence AND q.phase = s.phase);
SELECT HEX(e.character_id) AS character_id, e.event_sequence FROM s2_character_event e
WHERE (SELECT COUNT(*) FROM s2_character_equipment_command c WHERE c.character_id = e.character_id AND c.event_sequence = e.event_sequence) <> IF(e.event_kind IN (_binary'ITEM_ACQUIRED', _binary'ITEM_REMOVED', _binary'ITEM_EQUIPPED', _binary'ITEM_UNEQUIPPED'), 1, 0)
   OR (SELECT COUNT(*) FROM s2_character_health_event h WHERE h.character_id = e.character_id AND h.event_sequence = e.event_sequence) <> IF(e.event_kind IN (_binary'HEALTH_EFFECT_RESOLVED', _binary'DEATH_SAVE_RESOLVED'), 1, 0);
SELECT HEX(h.character_id) AS character_id, h.event_sequence FROM s2_character_health_event h
WHERE h.die_count <> (SELECT COUNT(*) FROM s2_character_health_die d WHERE d.character_id = h.character_id AND d.event_sequence = h.event_sequence);
SELECT HEX(q.character_id) AS character_id, q.event_sequence, q.phase FROM s2_character_event_equipment q
GROUP BY q.character_id, q.event_sequence, q.phase
HAVING COUNT(*) > 32 OR SUM(q.location = _binary'WORN') > 1 OR SUM(q.location IN (_binary'MAIN_HAND', _binary'BOTH_HANDS')) > 1 OR SUM(q.location IN (_binary'OFF_HAND', _binary'BOTH_HANDS')) > 1;
SELECT HEX(c.character_id) AS character_id, c.event_sequence FROM s2_character_equipment_command c
JOIN s2_character_event e ON e.character_id = c.character_id AND e.event_sequence = c.event_sequence
WHERE e.event_kind = _binary'ITEM_ACQUIRED' AND c.item_id <> e.operation_id;
SELECT HEX(s.character_id) AS character_id, s.event_sequence, s.phase FROM s2_character_event_state s
WHERE (s.temporary_hit_points BETWEEN 0 AND 65535 AND s.death_successes BETWEEN 0 AND 2 AND s.death_failures BETWEEN 0 AND 2
       AND s.stable IN (0, 1) AND s.dead IN (0, 1)
       AND ((s.dead = 1 AND s.hit_points = 0 AND s.death_successes = 0 AND s.death_failures = 0 AND s.stable = 0 AND s.stable_recovery_minute IS NULL)
         OR (s.dead = 0 AND ((s.hit_points > 0 AND s.death_successes = 0 AND s.death_failures = 0 AND s.stable = 0 AND s.stable_recovery_minute IS NULL)
           OR (s.hit_points = 0 AND ((s.stable = 1 AND s.death_successes = 0 AND s.death_failures = 0 AND s.stable_recovery_minute > s.minute AND s.stable_recovery_minute <= 1000000000)
             OR (s.stable = 0 AND s.stable_recovery_minute IS NULL))))))) IS NOT TRUE;
SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
