-- Read-only inventories after separately authorized runtime V042 and RULES V012 installation.
-- Run each inventory on its named schema. Runtime anomaly queries must return no rows.
SELECT schema_version,script_name,script_sha256 FROM dnd_tool_se.schema_meta WHERE schema_version=42;
SELECT schema_version,script_name,script_sha256 FROM dnd_tool_rules.rule_schema_meta WHERE schema_version=12;
SELECT table_name,engine,table_collation FROM information_schema.tables
 WHERE table_schema='dnd_tool_se' AND table_name='s2_combat_spell_damage_source';
SELECT trigger_name,event_manipulation,action_timing FROM information_schema.triggers
 WHERE trigger_schema='dnd_tool_se' AND event_object_table='s2_combat_spell_damage_source' ORDER BY trigger_name;

-- Every batch has exactly one actual producer branch. Nullable action is never a fake spell action.
SELECT HEX(b.combat_id) AS combat_id,b.event_sequence,b.phase,b.batch_index
 FROM dnd_tool_se.s2_combat_save_damage_batch b
 LEFT JOIN dnd_tool_se.s2_combat_spell_damage_source s
 ON s.combat_id=b.combat_id AND s.event_sequence=b.event_sequence AND s.phase=b.phase AND s.batch_index=b.batch_index
 WHERE (b.action_rule_module IS NULL)<>(s.cast_frame_key IS NOT NULL);

-- A spell producer references its original cast and retained full spell identity and slot.
SELECT HEX(s.combat_id) AS combat_id,s.event_sequence,s.phase,s.batch_index
 FROM dnd_tool_se.s2_combat_spell_damage_source s
 JOIN dnd_tool_se.s2_combat_save_damage_batch b
 ON b.combat_id=s.combat_id AND b.event_sequence=s.event_sequence AND b.phase=s.phase AND b.batch_index=s.batch_index
 LEFT JOIN dnd_tool_se.s2_combat_cast c
 ON c.combat_id=s.combat_id AND c.event_sequence=s.event_sequence AND c.phase=s.phase AND c.frame_key=s.cast_frame_key
 WHERE c.frame_key IS NULL OR b.source_id<>c.actor_id OR s.spell_module<>c.spell_module
 OR s.spell_type<>c.spell_type OR s.spell_key<>c.spell_key OR s.slot_level<>c.slot_level
 OR c.cone_direction IS NULL OR c.cone_direction<>b.direction OR c.ready_key IS NOT NULL
 OR (b.batch_status=_binary'ROLLED' AND c.cast_status<>_binary'PENDING')
 OR (b.batch_status<>_binary'ROLLED' AND c.cast_status<>_binary'RESOLVED');

SELECT HEX(b.combat_id) AS combat_id,b.event_sequence,b.phase,b.batch_index
 FROM dnd_tool_se.s2_combat_save_damage_batch b
 JOIN dnd_tool_se.s2_combat_spell_damage_source s
 ON s.combat_id=b.combat_id AND s.event_sequence=b.event_sequence AND s.phase=b.phase AND s.batch_index=b.batch_index
 WHERE (b.batch_status=_binary'NO_TARGETS' AND (b.die_count<>0 OR b.target_count<>0))
 OR (b.batch_status<>_binary'NO_TARGETS' AND b.die_count<>s.slot_level+2);

-- These inventories supplement the strict Java reader, retained original ZIP and historical replay.
-- They do not prove complete SRD/Host/archive adoption, schema execution, grants or deployment.
