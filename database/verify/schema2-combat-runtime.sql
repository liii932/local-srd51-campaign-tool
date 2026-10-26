-- Read-only structural verification after separately authorized V033 installation.
-- JDBC and service readers additionally validate the complete typed graph and rule semantics.
SELECT table_name, engine, table_collation FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN ('s2_combat_runtime', 's2_combat_member', 's2_combat_event', 's2_combat_dependency', 's2_combat_character_result', 's2_combat_command_point', 's2_combat_command_deployment', 's2_combat_command_tie', 's2_combat_roll', 's2_combat_fact', 's2_combat_state', 's2_combat_actor', 's2_combat_initiative', 's2_combat_flow', 's2_combat_frame', 's2_combat_window', 's2_combat_opportunity', 's2_combat_opportunity_item', 's2_combat_movement', 's2_combat_movement_point', 's2_combat_attack', 's2_combat_attack_die', 's2_character_combat_input') ORDER BY table_name;
SELECT event_object_table, trigger_name, action_timing, event_manipulation FROM information_schema.triggers
WHERE trigger_schema = DATABASE() AND event_object_table IN ('s2_combat_runtime', 's2_combat_member', 's2_combat_event', 's2_combat_dependency', 's2_combat_character_result', 's2_combat_command_point', 's2_combat_command_deployment', 's2_combat_command_tie', 's2_combat_roll', 's2_combat_fact', 's2_combat_state', 's2_combat_actor', 's2_combat_initiative', 's2_combat_flow', 's2_combat_frame', 's2_combat_window', 's2_combat_opportunity', 's2_combat_opportunity_item', 's2_combat_movement', 's2_combat_movement_point', 's2_combat_attack', 's2_combat_attack_die', 's2_character_combat_input') ORDER BY event_object_table, trigger_name;
SELECT HEX(m.combat_id) AS combat_id, m.member_index FROM s2_combat_member m
JOIN s2_combat_runtime b ON b.combat_id = m.combat_id
LEFT JOIN s2_character_runtime c ON c.character_id = m.character_id
WHERE c.character_id IS NULL OR c.campaign_id <> b.campaign_id OR c.run_id <> m.run_id
   OR c.snapshot_id <> m.snapshot_id OR c.preparation_operation_id <> m.preparation_operation_id;
SELECT HEX(combat_id) AS combat_id FROM s2_combat_member GROUP BY combat_id
HAVING COUNT(*) NOT BETWEEN 2 AND 16 OR MIN(member_index) <> 0 OR MAX(member_index) <> COUNT(*) - 1;
SELECT HEX(combat_id) AS combat_id FROM s2_combat_event GROUP BY combat_id
HAVING MIN(event_sequence) <> 1 OR MAX(event_sequence) <> COUNT(*) OR COUNT(*) > 128;
SELECT HEX(e.combat_id) AS combat_id, e.event_sequence FROM s2_combat_event e
WHERE (SELECT COUNT(*) FROM s2_combat_state s WHERE s.combat_id = e.combat_id AND s.event_sequence = e.event_sequence) <> IF(e.event_sequence = 1, 1, 2)
   OR (SELECT COUNT(*) FROM s2_combat_dependency d WHERE d.combat_id = e.combat_id AND d.event_sequence = e.event_sequence) <> (SELECT COUNT(*) FROM s2_combat_member m WHERE m.combat_id = e.combat_id);
SELECT HEX(b.campaign_id) AS campaign_id FROM s2_combat_runtime b
JOIN s2_combat_state s ON s.combat_id = b.combat_id AND s.phase = _binary'AFTER'
 AND s.event_sequence = (SELECT MAX(e.event_sequence) FROM s2_combat_event e WHERE e.combat_id = b.combat_id)
WHERE s.combat_status = _binary'ACTIVE' GROUP BY b.campaign_id HAVING COUNT(*) > 1;
SELECT HEX(s.combat_id) AS combat_id, s.event_sequence, s.phase FROM s2_combat_state s
WHERE (SELECT COUNT(*) FROM s2_combat_actor a WHERE a.combat_id = s.combat_id AND a.event_sequence = s.event_sequence AND a.phase = s.phase) <> (SELECT COUNT(*) FROM s2_combat_member m WHERE m.combat_id = s.combat_id)
   OR (SELECT COUNT(*) FROM s2_combat_initiative i WHERE i.combat_id = s.combat_id AND i.event_sequence = s.event_sequence AND i.phase = s.phase) <> (SELECT COUNT(*) FROM s2_combat_member m WHERE m.combat_id = s.combat_id)
   OR (SELECT COUNT(*) FROM s2_combat_flow f WHERE f.combat_id = s.combat_id AND f.event_sequence = s.event_sequence AND f.phase = s.phase AND f.flow_status = _binary'ACTIVE') > 1;
SELECT HEX(f.combat_id) AS combat_id, f.event_sequence, f.phase, f.flow_index FROM s2_combat_flow f
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_frame r WHERE r.combat_id = f.combat_id AND r.event_sequence = f.event_sequence AND r.phase = f.phase AND r.flow_index = f.flow_index AND r.frame_key = f.root_frame_key AND r.parent_frame_key IS NULL)
   OR (f.flow_status = _binary'ACTIVE' AND NOT EXISTS (SELECT 1 FROM s2_combat_frame r WHERE r.combat_id = f.combat_id AND r.event_sequence = f.event_sequence AND r.phase = f.phase AND r.flow_index = f.flow_index AND r.frame_key = f.leaf_frame_key));
SELECT HEX(w.combat_id) AS combat_id, w.event_sequence, w.phase, w.flow_index, w.window_index FROM s2_combat_window w
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_frame r WHERE r.combat_id = w.combat_id AND r.event_sequence = w.event_sequence AND r.phase = w.phase AND r.flow_index = w.flow_index AND r.frame_key = w.owner_frame_key);
SELECT HEX(o.combat_id) AS combat_id, o.event_sequence, o.phase, o.flow_index, o.window_index, o.opportunity_index FROM s2_combat_opportunity o
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_opportunity_item i WHERE i.combat_id = o.combat_id AND i.event_sequence = o.event_sequence AND i.phase = o.phase AND i.flow_index = o.flow_index AND i.window_index = o.window_index AND i.opportunity_index = o.opportunity_index)
   OR (o.opportunity_status <> _binary'PENDING' AND o.selection_frame_key IS NULL);
SELECT HEX(m.combat_id) AS combat_id, m.event_sequence, m.phase, m.flow_index FROM s2_combat_movement m
WHERE m.cursor > (SELECT COUNT(*) FROM s2_combat_movement_point p WHERE p.combat_id = m.combat_id AND p.event_sequence = m.event_sequence AND p.phase = m.phase AND p.flow_index = m.flow_index);
SELECT HEX(a.combat_id) AS combat_id, a.event_sequence, a.phase, a.flow_index, a.attack_index FROM s2_combat_attack a
WHERE (SELECT COUNT(*) FROM s2_combat_attack_die d WHERE d.combat_id = a.combat_id AND d.event_sequence = a.event_sequence AND d.phase = a.phase AND d.flow_index = a.flow_index AND d.attack_index = a.attack_index) > 40;
SELECT HEX(r.combat_id) AS combat_id, r.event_sequence, r.result_index FROM s2_combat_character_result r
JOIN s2_combat_event e ON e.combat_id = r.combat_id AND e.event_sequence = r.event_sequence
LEFT JOIN s2_character_combat_input cause ON cause.operation_id = r.character_operation_id
WHERE cause.operation_id IS NULL OR cause.combat_id <> e.combat_id OR cause.combat_operation_id <> e.operation_id;
SELECT HEX(cause.operation_id) AS operation_id FROM s2_character_combat_input cause
LEFT JOIN s2_character_event c ON c.operation_id = cause.operation_id AND c.character_id = cause.character_id AND c.event_sequence = cause.event_sequence
WHERE c.operation_id IS NULL OR c.event_kind NOT IN (_binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_DEATH_SAVE_RESOLVED', _binary'COMBAT_TIME_ADVANCED')
   OR NOT EXISTS (SELECT 1 FROM s2_combat_character_result r JOIN s2_combat_event e ON e.combat_id = r.combat_id AND e.event_sequence = r.event_sequence WHERE r.character_operation_id = cause.operation_id AND e.operation_id = cause.combat_operation_id AND e.combat_id = cause.combat_id);
SELECT HEX(p.combat_id) AS combat_id, p.event_sequence FROM s2_combat_command_point p
JOIN s2_combat_event e ON e.combat_id = p.combat_id AND e.event_sequence = p.event_sequence WHERE e.command_kind <> _binary'MOVE';
SELECT HEX(d.combat_id) AS combat_id, d.event_sequence FROM s2_combat_command_deployment d
JOIN s2_combat_event e ON e.combat_id = d.combat_id AND e.event_sequence = d.event_sequence WHERE e.command_kind <> _binary'START';
SELECT HEX(t.combat_id) AS combat_id, t.event_sequence FROM s2_combat_command_tie t
JOIN s2_combat_event e ON e.combat_id = t.combat_id AND e.event_sequence = t.event_sequence WHERE e.command_kind <> _binary'START';
SELECT HEX(r.combat_id) AS combat_id, r.event_sequence, r.roll_index FROM s2_combat_roll r WHERE r.die_value NOT BETWEEN 1 AND r.sides;
SELECT HEX(f.combat_id) AS combat_id, f.event_sequence, f.fact_index FROM s2_combat_fact f WHERE OCTET_LENGTH(f.fact_kind) = 0;
SELECT HEX(c.character_id) AS character_id, c.event_sequence, c.phase FROM s2_character_event_state c
WHERE c.rest_second NOT BETWEEN 0 AND 59
   OR (c.last_long_rest_end_minute IS NULL) <> (c.last_long_rest_end_second IS NULL)
   OR (c.stable_recovery_minute IS NULL) <> (c.stable_recovery_second IS NULL);
SELECT HEX(e.character_id) AS character_id, e.event_sequence, e.phase, e.equipment_index FROM s2_character_event_equipment e
WHERE (e.location = _binary'GROUND') <> (e.ground_combat_id IS NOT NULL AND e.ground_x IS NOT NULL AND e.ground_y IS NOT NULL);
SELECT HEX(a.combat_id) AS combat_id, a.event_sequence, a.phase, a.actor_index FROM s2_combat_actor a
WHERE a.movement_spent_half_feet < 0 OR a.prone NOT IN (0, 1);
SELECT HEX(c.operation_id) AS operation_id FROM s2_character_combat_input c
JOIN s2_character_event e ON e.operation_id = c.operation_id
WHERE (e.event_kind = _binary'COMBAT_TIME_ADVANCED') <> (c.seconds_advanced > 0);
