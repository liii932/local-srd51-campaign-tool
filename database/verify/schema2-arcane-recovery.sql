-- Read-only checks of Arcane Recovery facts; empty defect result sets are necessary, not engine acceptance.
SELECT schema_version, script_name, script_sha256 FROM schema_meta WHERE schema_version=41;
SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
WHERE table_schema=DATABASE() AND (table_name='s2_character_arcane_recovery_choice'
 OR (table_name='s2_character_event' AND column_name='arcane_recovery_choice_count')
 OR (table_name='s2_character_event_state' AND column_name='arcane_recovery_day'))
ORDER BY table_name,ordinal_position;
SELECT trigger_name,event_manipulation,action_statement FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND event_object_table='s2_character_arcane_recovery_choice' ORDER BY trigger_name;
SELECT HEX(e.character_id) AS character_id,e.event_sequence FROM s2_character_event e
WHERE e.arcane_recovery_choice_count <> (SELECT COUNT(*) FROM s2_character_arcane_recovery_choice c
 WHERE c.character_id=e.character_id AND c.event_sequence=e.event_sequence)
 OR (e.arcane_recovery_choice_count>0 AND e.event_kind<>_binary'REST_COMPLETED');
SELECT HEX(c.character_id) AS character_id,c.event_sequence FROM s2_character_arcane_recovery_choice c
LEFT JOIN s2_character_event e ON e.character_id=c.character_id AND e.event_sequence=c.event_sequence
WHERE e.character_id IS NULL OR e.event_kind<>_binary'REST_COMPLETED' OR e.arcane_recovery_choice_count NOT BETWEEN 1 AND 5
 OR c.slot_rank NOT BETWEEN 1 AND 5 OR c.recovered_value NOT BETWEEN 1 AND 4;
-- The complete frozen Wizard/resource identity and semantic results are independently revalidated by Service reads.
SELECT HEX(e.character_id) AS character_id,e.event_sequence FROM s2_character_event e
LEFT JOIN s2_character_event_state b ON b.character_id=e.character_id AND b.event_sequence=e.event_sequence AND b.phase=_binary'BEFORE'
LEFT JOIN s2_character_event_state a ON a.character_id=e.character_id AND a.event_sequence=e.event_sequence AND a.phase=_binary'AFTER'
WHERE e.arcane_recovery_choice_count>0 AND (b.character_id IS NULL OR a.character_id IS NULL
 OR (b.rest_kind=_binary'SHORT_REST' AND b.rest_phase=_binary'IN_PROGRESS' AND b.interrupted=0
   AND b.minute-b.rest_start_minute>=60 AND a.rest_phase=_binary'SPENDING_HIT_DICE'
   AND b.spellbook_casting_module IS NOT NULL AND a.spellbook_casting_module=b.spellbook_casting_module
   AND a.spellbook_casting_type=b.spellbook_casting_type AND a.spellbook_casting_key=b.spellbook_casting_key
   AND a.arcane_recovery_day=FLOOR(b.minute/1440)
   AND (b.arcane_recovery_day IS NULL OR b.arcane_recovery_day<a.arcane_recovery_day)
   AND b.total_level BETWEEN 1 AND 5 AND b.class_count=1
   AND (SELECT SUM(c.slot_rank*c.recovered_value) FROM s2_character_arcane_recovery_choice c
     WHERE c.character_id=e.character_id AND c.event_sequence=e.event_sequence)<=CEIL(b.total_level/2)) IS NOT TRUE);
SELECT HEX(s.character_id) AS character_id,s.event_sequence,s.phase FROM s2_character_event_state s
WHERE s.arcane_recovery_day IS NOT NULL
 AND (s.spellbook_casting_module IS NOT NULL AND s.arcane_recovery_day BETWEEN 0 AND FLOOR(s.minute/1440)) IS NOT TRUE;
-- No other command, including long rest, preparation or learning, can reset or change daily use.
SELECT HEX(e.character_id) AS character_id,e.event_sequence FROM s2_character_event e
JOIN s2_character_event_state b ON b.character_id=e.character_id AND b.event_sequence=e.event_sequence AND b.phase=_binary'BEFORE'
JOIN s2_character_event_state a ON a.character_id=e.character_id AND a.event_sequence=e.event_sequence AND a.phase=_binary'AFTER'
WHERE e.arcane_recovery_choice_count=0 AND NOT (a.arcane_recovery_day <=> b.arcane_recovery_day);
