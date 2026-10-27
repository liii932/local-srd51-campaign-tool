-- Read-only structural checks after separately authorized V036–V038 installation.
-- Full typed JDBC recovery and pure rule replay also validate physical rows and rule semantics.
SELECT table_name, engine, table_collation FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN ('s2_combat_cast_command', 's2_combat_cast_command_target', 's2_combat_ongoing_effect', 's2_combat_ongoing_target', 's2_combat_cast', 's2_combat_cast_target', 's2_combat_cast_die', 's2_combat_saving_throw') ORDER BY table_name;
SELECT event_object_table, trigger_name, action_timing, event_manipulation FROM information_schema.triggers
WHERE trigger_schema = DATABASE() AND event_object_table IN ('s2_combat_cast_command', 's2_combat_cast_command_target', 's2_combat_ongoing_effect', 's2_combat_ongoing_target', 's2_combat_cast', 's2_combat_cast_target', 's2_combat_cast_die', 's2_combat_saving_throw') ORDER BY event_object_table, trigger_name;
SELECT HEX(e.combat_id) AS combat_id, e.event_sequence FROM s2_combat_event e
WHERE (e.command_kind IN (_binary'CAST',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL')) <> EXISTS (SELECT 1 FROM s2_combat_cast_command c WHERE c.combat_id = e.combat_id AND c.event_sequence = e.event_sequence);
SELECT HEX(c.combat_id) AS combat_id, c.event_sequence FROM s2_combat_cast_command c
WHERE c.target_count <> (SELECT COUNT(*) FROM s2_combat_cast_command_target t WHERE t.combat_id = c.combat_id AND t.event_sequence = c.event_sequence);
SELECT HEX(t.combat_id) AS combat_id, t.event_sequence, t.target_index FROM s2_combat_cast_command_target t
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_cast_command c WHERE c.combat_id = t.combat_id AND c.event_sequence = t.event_sequence)
   OR NOT EXISTS (SELECT 1 FROM s2_combat_member m WHERE m.combat_id = t.combat_id AND m.character_id = t.target_id);
SELECT HEX(e.combat_id) AS combat_id, e.event_sequence, e.phase, e.effect_index FROM s2_combat_ongoing_effect e
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_member m WHERE m.combat_id = e.combat_id AND m.character_id = e.caster_id)
   OR e.target_count <> (SELECT COUNT(*) FROM s2_combat_ongoing_target t WHERE t.combat_id = e.combat_id AND t.event_sequence = e.event_sequence AND t.phase = e.phase AND t.effect_index = e.effect_index);
SELECT HEX(s.combat_id) AS combat_id, s.event_sequence, s.phase FROM s2_combat_state s
WHERE s.ongoing_effect_count <> (SELECT COUNT(*) FROM s2_combat_ongoing_effect e WHERE e.combat_id = s.combat_id AND e.event_sequence = s.event_sequence AND e.phase = s.phase);
SELECT HEX(f.combat_id) AS combat_id, f.event_sequence, f.phase, f.flow_index FROM s2_combat_flow f
WHERE f.cast_count <> (SELECT COUNT(*) FROM s2_combat_cast c WHERE c.combat_id = f.combat_id AND c.event_sequence = f.event_sequence AND c.phase = f.phase AND c.flow_index = f.flow_index)
   OR f.save_count <> (SELECT COUNT(*) FROM s2_combat_saving_throw s WHERE s.combat_id = f.combat_id AND s.event_sequence = f.event_sequence AND s.phase = f.phase AND s.flow_index = f.flow_index);
SELECT HEX(t.combat_id) AS combat_id, t.event_sequence, t.phase, t.effect_index, t.target_index FROM s2_combat_ongoing_target t
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_member m WHERE m.combat_id = t.combat_id AND m.character_id = t.target_id);
SELECT HEX(c.combat_id) AS combat_id, c.event_sequence, c.phase, c.flow_index, c.cast_index FROM s2_combat_cast c
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_frame f WHERE f.combat_id = c.combat_id AND f.event_sequence = c.event_sequence AND f.phase = c.phase AND f.flow_index = c.flow_index AND f.frame_key = c.frame_key AND f.frame_kind = _binary'CAST')
   OR c.target_count <> (SELECT COUNT(*) FROM s2_combat_cast_target t WHERE t.combat_id = c.combat_id AND t.event_sequence = c.event_sequence AND t.phase = c.phase AND t.flow_index = c.flow_index AND t.cast_index = c.cast_index)
   OR c.healing_die_count <> (SELECT COUNT(*) FROM s2_combat_cast_die d WHERE d.combat_id = c.combat_id AND d.event_sequence = c.event_sequence AND d.phase = c.phase AND d.flow_index = c.flow_index AND d.cast_index = c.cast_index);
SELECT HEX(t.combat_id) AS combat_id, t.event_sequence, t.phase, t.flow_index, t.cast_index, t.target_index FROM s2_combat_cast_target t
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_member m WHERE m.combat_id = t.combat_id AND m.character_id = t.target_id);
SELECT HEX(d.combat_id) AS combat_id, d.event_sequence, d.phase, d.flow_index, d.cast_index FROM s2_combat_cast_die d
GROUP BY d.combat_id, d.event_sequence, d.phase, d.flow_index, d.cast_index
HAVING MIN(d.die_index) <> 0 OR MAX(d.die_index) <> COUNT(*) - 1 OR COUNT(*) > 20;
SELECT HEX(t.combat_id) AS combat_id, t.event_sequence FROM s2_combat_cast_command_target t
GROUP BY t.combat_id, t.event_sequence
HAVING MIN(t.target_index) <> 0 OR MAX(t.target_index) <> COUNT(*) - 1;
SELECT HEX(e.combat_id) AS combat_id, e.event_sequence, e.phase FROM s2_combat_ongoing_effect e
GROUP BY e.combat_id, e.event_sequence, e.phase
HAVING MIN(e.effect_index) <> 0 OR MAX(e.effect_index) <> COUNT(*) - 1;
SELECT HEX(t.combat_id) AS combat_id, t.event_sequence, t.phase, t.effect_index FROM s2_combat_ongoing_target t
GROUP BY t.combat_id, t.event_sequence, t.phase, t.effect_index
HAVING MIN(t.target_index) <> 0 OR MAX(t.target_index) <> COUNT(*) - 1;
SELECT HEX(c.combat_id) AS combat_id, c.event_sequence, c.phase, c.flow_index FROM s2_combat_cast c
GROUP BY c.combat_id, c.event_sequence, c.phase, c.flow_index
HAVING MIN(c.cast_index) <> 0 OR MAX(c.cast_index) <> COUNT(*) - 1;
SELECT HEX(t.combat_id) AS combat_id, t.event_sequence, t.phase, t.flow_index, t.cast_index FROM s2_combat_cast_target t
GROUP BY t.combat_id, t.event_sequence, t.phase, t.flow_index, t.cast_index
HAVING MIN(t.target_index) <> 0 OR MAX(t.target_index) <> COUNT(*) - 1;
SELECT HEX(s.combat_id) AS combat_id, s.event_sequence, s.phase, s.flow_index FROM s2_combat_saving_throw s
GROUP BY s.combat_id, s.event_sequence, s.phase, s.flow_index
HAVING MIN(s.save_index) <> 0 OR MAX(s.save_index) <> COUNT(*) - 1;
SELECT HEX(s.combat_id) AS combat_id, s.event_sequence, s.phase, s.flow_index, s.save_index FROM s2_combat_saving_throw s
WHERE NOT EXISTS (SELECT 1 FROM s2_combat_frame f WHERE f.combat_id = s.combat_id AND f.event_sequence = s.event_sequence AND f.phase = s.phase AND f.flow_index = s.flow_index AND f.frame_key = s.frame_key)
   OR s.total <> s.d20 + s.ability_modifier + s.proficiency_bonus + IFNULL(s.blessing_d4, 0)
   OR s.success <> IF(s.total >= s.difficulty_class, 1, 0);
SELECT HEX(a.combat_id) AS combat_id, a.event_sequence, a.phase, a.flow_index, a.attack_index FROM s2_combat_attack a
WHERE (a.blessing_effect_key IS NULL) <> (a.blessing_d4 IS NULL);
SELECT HEX(h.character_id) AS character_id, h.event_sequence FROM s2_character_health_event h
JOIN s2_character_event e ON e.character_id = h.character_id AND e.event_sequence = h.event_sequence
WHERE e.event_kind IN (_binary'DEATH_SAVE_RESOLVED', _binary'COMBAT_DEATH_SAVE_RESOLVED')
  AND (h.death_d20 IS NULL
       OR (e.event_kind = _binary'DEATH_SAVE_RESOLVED' AND h.die_count <> 0)
       OR (e.event_kind = _binary'COMBAT_DEATH_SAVE_RESOLVED' AND h.die_count NOT BETWEEN 0 AND 1)
       OR h.die_count <> (SELECT COUNT(*) FROM s2_character_health_die d WHERE d.character_id = h.character_id AND d.event_sequence = h.event_sequence)
       OR EXISTS (SELECT 1 FROM s2_character_health_die d WHERE d.character_id = h.character_id AND d.event_sequence = h.event_sequence AND d.die_value NOT BETWEEN 1 AND 4));
SELECT HEX(e.operation_id) AS operation_id FROM s2_character_event e
LEFT JOIN s2_character_combat_input c ON c.operation_id = e.operation_id
WHERE e.event_kind IN (_binary'COMBAT_RESOURCE_SPENT', _binary'COMBAT_HEALING_APPLIED', _binary'COMBAT_ENVIRONMENT_HEALTH')
  AND (c.operation_id IS NULL OR c.seconds_advanced <> 0 OR c.critical <> 0
       OR NOT EXISTS (SELECT 1 FROM s2_combat_character_result r JOIN s2_combat_event root ON root.combat_id = r.combat_id AND root.event_sequence = r.event_sequence WHERE r.character_operation_id = e.operation_id AND root.operation_id = c.combat_operation_id AND root.combat_id = c.combat_id));

-- Character spellbook rows have a single character history owner.
SELECT COUNT(*) AS rows_present FROM s2_character_event_learned_spell;
SELECT COUNT(*) AS rows_present FROM s2_character_event_prepared_spell;
SELECT COUNT(*) AS rows_present FROM s2_character_spellbook_command;
SELECT COUNT(*) AS rows_present FROM s2_character_spellbook_choice;
