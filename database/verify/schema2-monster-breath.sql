-- Read-only verification after separately authorized V039/V040 installation.
-- Run against the exact runtime schema. Inventories return rows; anomaly queries should return none.
-- SQL evidence supplements, and does not replace, the strict Java historical/rules reader.
SELECT CAST(DATABASE() AS BINARY) AS runtime_schema;
SELECT schema_version,script_name,script_sha256 FROM schema_meta WHERE schema_version IN (39,40) ORDER BY schema_version;

SELECT table_name,engine,table_collation FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('s2_combat_monster_deployment','s2_combat_breath_input','s2_combat_monster','s2_combat_monster_action','s2_combat_save_damage_batch','s2_combat_breath_candidate','s2_combat_breath_target','s2_combat_breath_die','s2_combat_breath_check','s2_combat_breath_packet','s2_combat_breath_defense','s2_combat_breath_concentration','s2_combat_monster_rest_input','s2_combat_monster_rest') ORDER BY table_name;

SELECT event_object_table,trigger_name,action_timing,event_manipulation FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN ('s2_combat_monster_deployment','s2_combat_breath_input','s2_combat_monster','s2_combat_monster_action','s2_combat_save_damage_batch','s2_combat_breath_candidate','s2_combat_breath_target','s2_combat_breath_die','s2_combat_breath_check','s2_combat_breath_packet','s2_combat_breath_defense','s2_combat_breath_concentration','s2_combat_monster_rest_input','s2_combat_monster_rest') ORDER BY event_object_table,event_manipulation;

SELECT table_name,constraint_name,column_name,ordinal_position,referenced_table_schema,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name IN ('s2_combat_monster_deployment','s2_combat_breath_input','s2_combat_monster','s2_combat_monster_action','s2_combat_save_damage_batch','s2_combat_breath_candidate','s2_combat_breath_target','s2_combat_breath_die','s2_combat_breath_check','s2_combat_breath_packet','s2_combat_breath_defense','s2_combat_breath_concentration','s2_combat_monster_rest_input','s2_combat_monster_rest') AND referenced_table_name IS NOT NULL ORDER BY table_name,constraint_name,ordinal_position;

-- Missing, invisible, cross-schema or cascading parent constraints are anomalies.
WITH expected AS (SELECT 's2_combat_monster_deployment' AS table_name,'s2_combat_event' AS parent_table UNION ALL SELECT 's2_combat_breath_input' AS table_name,'s2_combat_event' AS parent_table UNION ALL SELECT 's2_combat_monster' AS table_name,'s2_combat_state' AS parent_table UNION ALL SELECT 's2_combat_monster_action' AS table_name,'s2_combat_monster' AS parent_table UNION ALL SELECT 's2_combat_save_damage_batch' AS table_name,'s2_combat_state' AS parent_table UNION ALL SELECT 's2_combat_breath_candidate' AS table_name,'s2_combat_save_damage_batch' AS parent_table UNION ALL SELECT 's2_combat_breath_target' AS table_name,'s2_combat_save_damage_batch' AS parent_table UNION ALL SELECT 's2_combat_breath_die' AS table_name,'s2_combat_save_damage_batch' AS parent_table UNION ALL SELECT 's2_combat_breath_check' AS table_name,'s2_combat_save_damage_batch' AS parent_table UNION ALL SELECT 's2_combat_breath_packet' AS table_name,'s2_combat_save_damage_batch' AS parent_table UNION ALL SELECT 's2_combat_breath_defense' AS table_name,'s2_combat_breath_packet' AS parent_table UNION ALL SELECT 's2_combat_breath_concentration' AS table_name,'s2_combat_save_damage_batch' AS parent_table UNION ALL SELECT 's2_combat_monster_rest_input' AS table_name,'s2_combat_event' AS parent_table UNION ALL SELECT 's2_combat_monster_rest' AS table_name,'s2_combat_state' AS parent_table)
SELECT e.table_name,e.parent_table FROM expected e WHERE NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints r WHERE r.constraint_schema=DATABASE() AND r.table_name=e.table_name AND r.referenced_table_name=e.parent_table AND r.unique_constraint_schema=DATABASE() AND r.update_rule='RESTRICT' AND r.delete_rule='RESTRICT');

-- Header counts cover complete stored phases, including completed historical batches.

SELECT HEX(s.combat_id) AS combat_id,s.event_sequence,s.phase FROM s2_combat_state s
WHERE s.monster_count <> (SELECT COUNT(*) FROM s2_combat_monster c WHERE c.combat_id=s.combat_id AND c.event_sequence=s.event_sequence AND c.phase=s.phase)
   OR s.batch_count <> (SELECT COUNT(*) FROM s2_combat_save_damage_batch c WHERE c.combat_id=s.combat_id AND c.event_sequence=s.event_sequence AND c.phase=s.phase)
   OR s.monster_rest_count <> (SELECT COUNT(*) FROM s2_combat_monster_rest c WHERE c.combat_id=s.combat_id AND c.event_sequence=s.event_sequence AND c.phase=s.phase);

SELECT HEX(m.combat_id) AS combat_id,m.event_sequence,m.phase FROM s2_combat_monster m
WHERE m.action_count <> (SELECT COUNT(*) FROM s2_combat_monster_action c WHERE c.combat_id=m.combat_id AND c.event_sequence=m.event_sequence AND c.phase=m.phase AND c.monster_index=m.monster_index);

SELECT HEX(b.combat_id) AS combat_id,b.event_sequence,b.phase FROM s2_combat_save_damage_batch b
WHERE b.candidate_count <> (SELECT COUNT(*) FROM s2_combat_breath_candidate c WHERE c.combat_id=b.combat_id AND c.event_sequence=b.event_sequence AND c.phase=b.phase AND c.batch_index=b.batch_index)
   OR b.target_count <> (SELECT COUNT(*) FROM s2_combat_breath_target c WHERE c.combat_id=b.combat_id AND c.event_sequence=b.event_sequence AND c.phase=b.phase AND c.batch_index=b.batch_index)
   OR b.die_count <> (SELECT COUNT(*) FROM s2_combat_breath_die c WHERE c.combat_id=b.combat_id AND c.event_sequence=b.event_sequence AND c.phase=b.phase AND c.batch_index=b.batch_index)
   OR b.check_count <> (SELECT COUNT(*) FROM s2_combat_breath_check c WHERE c.combat_id=b.combat_id AND c.event_sequence=b.event_sequence AND c.phase=b.phase AND c.batch_index=b.batch_index)
   OR b.packet_count <> (SELECT COUNT(*) FROM s2_combat_breath_packet c WHERE c.combat_id=b.combat_id AND c.event_sequence=b.event_sequence AND c.phase=b.phase AND c.batch_index=b.batch_index)
   OR b.obligation_count <> (SELECT COUNT(*) FROM s2_combat_breath_concentration c WHERE c.combat_id=b.combat_id AND c.event_sequence=b.event_sequence AND c.phase=b.phase AND c.batch_index=b.batch_index);

SELECT HEX(p.combat_id) AS combat_id,p.event_sequence,p.phase FROM s2_combat_breath_packet p
WHERE p.defense_count <> (SELECT COUNT(*) FROM s2_combat_breath_defense c WHERE c.combat_id=p.combat_id AND c.event_sequence=p.event_sequence AND c.phase=p.phase AND c.batch_index=p.batch_index AND c.packet_index=p.packet_index);

SELECT HEX(s.combat_id) AS combat_id,s.event_sequence,s.phase FROM s2_combat_state s
WHERE (SELECT COUNT(*) FROM s2_combat_actor a WHERE a.combat_id=s.combat_id AND a.event_sequence=s.event_sequence AND a.phase=s.phase) <> s.monster_count+(SELECT COUNT(*) FROM s2_combat_member m WHERE m.combat_id=s.combat_id)
   OR (SELECT COUNT(*) FROM s2_combat_initiative i WHERE i.combat_id=s.combat_id AND i.event_sequence=s.event_sequence AND i.phase=s.phase) <> s.monster_count+(SELECT COUNT(*) FROM s2_combat_member m WHERE m.combat_id=s.combat_id);
-- Parent absence and every ordinal gap are reported independently of FK visibility.

SELECT 's2_combat_monster_deployment' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_monster_deployment c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_event p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence);

SELECT 's2_combat_monster_deployment' AS entity,combat_id,event_sequence FROM s2_combat_monster_deployment GROUP BY combat_id,event_sequence HAVING MIN(deployment_index)<>0 OR MAX(deployment_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_input' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_input c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_event p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence);

SELECT 's2_combat_breath_input' AS entity,combat_id,event_sequence FROM s2_combat_breath_input GROUP BY combat_id,event_sequence HAVING MIN(input_index)<>0 OR MAX(input_index)<>COUNT(*)-1;

SELECT 's2_combat_monster' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_monster c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_state p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase);

SELECT 's2_combat_monster' AS entity,combat_id,event_sequence,phase FROM s2_combat_monster GROUP BY combat_id,event_sequence,phase HAVING MIN(monster_index)<>0 OR MAX(monster_index)<>COUNT(*)-1;

SELECT 's2_combat_monster_action' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_monster_action c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_monster p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.monster_index=c.monster_index);

SELECT 's2_combat_monster_action' AS entity,combat_id,event_sequence,phase,monster_index FROM s2_combat_monster_action GROUP BY combat_id,event_sequence,phase,monster_index HAVING MIN(action_index)<>0 OR MAX(action_index)<>COUNT(*)-1;

SELECT 's2_combat_save_damage_batch' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_save_damage_batch c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_state p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase);

SELECT 's2_combat_save_damage_batch' AS entity,combat_id,event_sequence,phase FROM s2_combat_save_damage_batch GROUP BY combat_id,event_sequence,phase HAVING MIN(batch_index)<>0 OR MAX(batch_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_candidate' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_candidate c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_save_damage_batch p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.batch_index=c.batch_index);

SELECT 's2_combat_breath_candidate' AS entity,combat_id,event_sequence,phase,batch_index FROM s2_combat_breath_candidate GROUP BY combat_id,event_sequence,phase,batch_index HAVING MIN(candidate_index)<>0 OR MAX(candidate_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_target' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_target c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_save_damage_batch p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.batch_index=c.batch_index);

SELECT 's2_combat_breath_target' AS entity,combat_id,event_sequence,phase,batch_index FROM s2_combat_breath_target GROUP BY combat_id,event_sequence,phase,batch_index HAVING MIN(target_index)<>0 OR MAX(target_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_die' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_die c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_save_damage_batch p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.batch_index=c.batch_index);

SELECT 's2_combat_breath_die' AS entity,combat_id,event_sequence,phase,batch_index FROM s2_combat_breath_die GROUP BY combat_id,event_sequence,phase,batch_index HAVING MIN(die_index)<>0 OR MAX(die_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_check' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_check c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_save_damage_batch p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.batch_index=c.batch_index);

SELECT 's2_combat_breath_check' AS entity,combat_id,event_sequence,phase,batch_index FROM s2_combat_breath_check GROUP BY combat_id,event_sequence,phase,batch_index HAVING MIN(check_index)<>0 OR MAX(check_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_packet' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_packet c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_save_damage_batch p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.batch_index=c.batch_index);

SELECT 's2_combat_breath_packet' AS entity,combat_id,event_sequence,phase,batch_index FROM s2_combat_breath_packet GROUP BY combat_id,event_sequence,phase,batch_index HAVING MIN(packet_index)<>0 OR MAX(packet_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_defense' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_defense c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_breath_packet p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.batch_index=c.batch_index AND p.packet_index=c.packet_index);

SELECT 's2_combat_breath_defense' AS entity,combat_id,event_sequence,phase,batch_index,packet_index FROM s2_combat_breath_defense GROUP BY combat_id,event_sequence,phase,batch_index,packet_index HAVING MIN(defense_index)<>0 OR MAX(defense_index)<>COUNT(*)-1;

SELECT 's2_combat_breath_concentration' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_breath_concentration c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_save_damage_batch p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase AND p.batch_index=c.batch_index);

SELECT 's2_combat_breath_concentration' AS entity,combat_id,event_sequence,phase,batch_index FROM s2_combat_breath_concentration GROUP BY combat_id,event_sequence,phase,batch_index HAVING MIN(obligation_index)<>0 OR MAX(obligation_index)<>COUNT(*)-1;

SELECT 's2_combat_monster_rest_input' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_monster_rest_input c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_event p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence);

SELECT 's2_combat_monster_rest_input' AS entity,combat_id,event_sequence FROM s2_combat_monster_rest_input GROUP BY combat_id,event_sequence HAVING MIN(input_index)<>0 OR MAX(input_index)<>COUNT(*)-1;

SELECT 's2_combat_monster_rest' AS entity,HEX(c.combat_id) AS combat_id,c.event_sequence FROM s2_combat_monster_rest c WHERE NOT EXISTS (SELECT 1 FROM s2_combat_state p WHERE p.combat_id=c.combat_id AND p.event_sequence=c.event_sequence AND p.phase=c.phase);

SELECT 's2_combat_monster_rest' AS entity,combat_id,event_sequence,phase FROM s2_combat_monster_rest GROUP BY combat_id,event_sequence,phase HAVING MIN(rest_index)<>0 OR MAX(rest_index)<>COUNT(*)-1;

-- The accepted release remains the owner of origin, roster and membership after later movement.
SELECT HEX(b.combat_id) AS combat_id,b.event_sequence,b.phase,b.batch_index FROM s2_combat_save_damage_batch b
LEFT JOIN s2_combat_event e ON e.combat_id=b.combat_id AND e.operation_id=b.release_operation_id
WHERE e.operation_id IS NULL OR e.command_kind<>_binary'MONSTER_BREATH' OR e.actor_id<>b.source_id
   OR b.candidate_count<>(SELECT COUNT(*) FROM s2_combat_actor a WHERE a.combat_id=e.combat_id AND a.event_sequence=e.event_sequence AND a.phase=_binary'AFTER')
   OR NOT EXISTS(SELECT 1 FROM s2_combat_actor a WHERE a.combat_id=e.combat_id AND a.event_sequence=e.event_sequence AND a.phase=_binary'AFTER' AND a.character_id=b.source_id AND a.x=b.origin_x AND a.y=b.origin_y)
   OR NOT EXISTS(SELECT 1 FROM s2_combat_frame f WHERE f.combat_id=b.combat_id AND f.event_sequence=b.event_sequence AND f.phase=b.phase AND f.frame_key=b.frame_key AND f.frame_kind=_binary'SAVE_DAMAGE');
SELECT HEX(c.combat_id) AS combat_id,c.event_sequence,c.phase,c.batch_index,c.candidate_index FROM s2_combat_breath_candidate c
JOIN s2_combat_save_damage_batch b ON b.combat_id=c.combat_id AND b.event_sequence=c.event_sequence AND b.phase=c.phase AND b.batch_index=c.batch_index
JOIN s2_combat_event e ON e.combat_id=b.combat_id AND e.operation_id=b.release_operation_id
WHERE NOT EXISTS(SELECT 1 FROM s2_combat_actor a WHERE a.combat_id=e.combat_id AND a.event_sequence=e.event_sequence AND a.phase=_binary'AFTER' AND a.character_id=c.creature_id AND a.x=c.x AND a.y=c.y)
   OR (c.creature_kind=_binary'CHARACTER')<>(EXISTS(SELECT 1 FROM s2_combat_member m WHERE m.combat_id=c.combat_id AND m.character_id=c.creature_id));
WITH coordinates AS (
 SELECT c.*,b.space_policy,c.x-b.origin_x AS rx,c.y-b.origin_y AS ry,
 CASE b.direction WHEN 0 THEN 1 WHEN 1 THEN 1 WHEN 2 THEN 0 WHEN 3 THEN -1 WHEN 4 THEN -1 WHEN 5 THEN -1 WHEN 6 THEN 0 WHEN 7 THEN 1 END AS dx,
 CASE b.direction WHEN 0 THEN 0 WHEN 1 THEN 1 WHEN 2 THEN 1 WHEN 3 THEN 1 WHEN 4 THEN 0 WHEN 5 THEN -1 WHEN 6 THEN -1 WHEN 7 THEN -1 END AS dy
 FROM s2_combat_breath_candidate c JOIN s2_combat_save_damage_batch b ON b.combat_id=c.combat_id AND b.event_sequence=c.event_sequence AND b.phase=c.phase AND b.batch_index=c.batch_index
), measured AS (SELECT coordinates.*,rx*dx+ry*dy AS dot_value,rx*dy-ry*dx AS cross_value FROM coordinates)
SELECT HEX(combat_id) AS combat_id,event_sequence,phase,batch_index,candidate_index FROM measured
WHERE space_policy<>_binary'S2_POINT_CONE_CHEBYSHEV_V1' OR included<>IF(dot_value>0 AND dot_value<=3*(dx*dx+dy*dy) AND 2*ABS(cross_value)<=dot_value,1,0);
SELECT HEX(b.combat_id) AS combat_id,b.event_sequence,b.phase,b.batch_index FROM s2_combat_save_damage_batch b
WHERE b.target_count<>(SELECT COUNT(*) FROM s2_combat_breath_candidate c WHERE c.combat_id=b.combat_id AND c.event_sequence=b.event_sequence AND c.phase=b.phase AND c.batch_index=b.batch_index AND c.included=1);
SELECT HEX(t.combat_id) AS combat_id,t.event_sequence,t.phase,t.batch_index,t.target_index FROM s2_combat_breath_target t
WHERE NOT EXISTS(SELECT 1 FROM s2_combat_breath_candidate c WHERE c.combat_id=t.combat_id AND c.event_sequence=t.event_sequence AND c.phase=t.phase AND c.batch_index=t.batch_index AND c.creature_kind=t.creature_kind AND c.creature_id=t.creature_id AND c.included=1);
SELECT combat_id,event_sequence,phase,batch_index,creature_kind,creature_id FROM s2_combat_breath_target GROUP BY combat_id,event_sequence,phase,batch_index,creature_kind,creature_id HAVING COUNT(*)>1;
SELECT combat_id,event_sequence,phase,batch_index,creature_kind,creature_id FROM s2_combat_breath_candidate GROUP BY combat_id,event_sequence,phase,batch_index,creature_kind,creature_id HAVING COUNT(*)>1;
WITH ordered_targets AS (
 SELECT t.*,CASE creature_kind WHEN _binary'CHARACTER' THEN 0 ELSE 1 END AS kind_order,
 LAG(CASE creature_kind WHEN _binary'CHARACTER' THEN 0 ELSE 1 END) OVER(PARTITION BY combat_id,event_sequence,phase,batch_index ORDER BY target_index) AS previous_kind,
 LAG(creature_id) OVER(PARTITION BY combat_id,event_sequence,phase,batch_index ORDER BY target_index) AS previous_id FROM s2_combat_breath_target t
)
SELECT HEX(combat_id) AS combat_id,event_sequence,phase,batch_index,target_index FROM ordered_targets WHERE previous_kind>kind_order OR (previous_kind=kind_order AND previous_id>=creature_id);
-- One six-die audit; raw and completed facts never reconstruct it from HP deltas.
SELECT HEX(b.combat_id) AS combat_id,b.event_sequence,b.phase,b.batch_index FROM s2_combat_save_damage_batch b
WHERE (b.batch_status=_binary'NO_TARGETS' AND (b.target_count<>0 OR b.shared_roll_key IS NOT NULL OR b.raw_total IS NOT NULL OR b.effective_total IS NOT NULL OR b.die_count<>0 OR b.check_count<>0 OR b.packet_count<>0 OR b.obligation_count<>0))
   OR (b.batch_status<>_binary'NO_TARGETS' AND (b.shared_roll_key IS NULL OR b.die_count<>6 OR b.raw_total<>(SELECT SUM(d.die_value) FROM s2_combat_breath_die d WHERE d.combat_id=b.combat_id AND d.event_sequence=b.event_sequence AND d.phase=b.phase AND d.batch_index=b.batch_index) OR b.effective_total<>b.raw_total))
   OR (b.batch_status=_binary'ROLLED' AND (b.check_count<>0 OR b.packet_count<>0 OR b.obligation_count<>0))
   OR (b.batch_status=_binary'COMPLETED' AND (b.packet_count<>b.target_count OR b.check_count<>b.target_count+(SELECT COUNT(*) FROM s2_combat_breath_concentration o WHERE o.combat_id=b.combat_id AND o.event_sequence=b.event_sequence AND o.phase=b.phase AND o.batch_index=b.batch_index AND o.obligation_status IN (_binary'CHECK_SUCCEEDED',_binary'CHECK_FAILED'))));
SELECT HEX(c.combat_id) AS combat_id,c.event_sequence,c.phase,c.batch_index,c.check_index FROM s2_combat_breath_check c
WHERE NOT EXISTS(SELECT 1 FROM s2_combat_breath_target t WHERE t.combat_id=c.combat_id AND t.event_sequence=c.event_sequence AND t.phase=c.phase AND t.batch_index=c.batch_index AND t.target_index=c.target_ordinal)
   OR (c.purpose=_binary'BREATH_DEX' AND (c.ability_key<>_binary'ability.dexterity' OR c.difficulty_class<>12 OR c.check_index<>c.target_ordinal))
   OR (c.purpose=_binary'CONCENTRATION_CON' AND c.ability_key<>_binary'ability.constitution')
   OR ((c.blessing_effect_key IS NULL)<>(c.blessing_d4 IS NULL))
   OR (c.check_mode=_binary'AUTO_FAILURE' AND (c.first_d20 IS NOT NULL OR c.second_d20 IS NOT NULL OR c.success<>0))
   OR (c.check_mode=_binary'ROLL' AND (c.first_d20 IS NULL OR ((c.advantage=c.disadvantage)<>(c.second_d20 IS NULL)) OR c.total<>CASE WHEN c.advantage=c.disadvantage THEN c.first_d20 WHEN c.advantage=1 THEN GREATEST(c.first_d20,c.second_d20) ELSE LEAST(c.first_d20,c.second_d20) END+c.ability_modifier+c.proficiency_bonus+IFNULL(c.blessing_d4,0) OR c.success<>IF(c.total>=c.difficulty_class,1,0)));
SELECT HEX(p.combat_id) AS combat_id,p.event_sequence,p.phase,p.batch_index,p.packet_index FROM s2_combat_breath_packet p
JOIN s2_combat_save_damage_batch b ON b.combat_id=p.combat_id AND b.event_sequence=p.event_sequence AND b.phase=p.phase AND b.batch_index=p.batch_index
LEFT JOIN s2_combat_breath_check c ON c.combat_id=p.combat_id AND c.event_sequence=p.event_sequence AND c.phase=p.phase AND c.batch_index=p.batch_index AND c.check_index=p.save_check_index
WHERE p.shared_roll_key<>b.shared_roll_key OR c.check_index IS NULL OR c.purpose<>_binary'BREATH_DEX' OR c.target_ordinal<>p.target_ordinal
   OR p.absorbed_temporary_hit_points<>LEAST(p.before_temporary_hit_points,p.damage_amount)
   OR p.after_temporary_hit_points<>p.before_temporary_hit_points-p.absorbed_temporary_hit_points
   OR p.after_hit_points<>GREATEST(0,p.before_hit_points-(p.damage_amount-p.absorbed_temporary_hit_points));
-- Defense semantics are membership predicates, so duplicate sources do not multiply the effect.
WITH amounts AS (
 SELECT p.*,IF(c.success=1,FLOOR(b.effective_total/2),b.effective_total) AS after_save,
 EXISTS(SELECT 1 FROM s2_combat_breath_defense d WHERE d.combat_id=p.combat_id AND d.event_sequence=p.event_sequence AND d.phase=p.phase AND d.batch_index=p.batch_index AND d.packet_index=p.packet_index AND d.defense_kind=_binary'IMMUNITY') AS immune,
 EXISTS(SELECT 1 FROM s2_combat_breath_defense d WHERE d.combat_id=p.combat_id AND d.event_sequence=p.event_sequence AND d.phase=p.phase AND d.batch_index=p.batch_index AND d.packet_index=p.packet_index AND d.defense_kind=_binary'RESISTANCE') AS resistant,
 EXISTS(SELECT 1 FROM s2_combat_breath_defense d WHERE d.combat_id=p.combat_id AND d.event_sequence=p.event_sequence AND d.phase=p.phase AND d.batch_index=p.batch_index AND d.packet_index=p.packet_index AND d.defense_kind=_binary'VULNERABILITY') AS vulnerable
 FROM s2_combat_breath_packet p JOIN s2_combat_save_damage_batch b ON b.combat_id=p.combat_id AND b.event_sequence=p.event_sequence AND b.phase=p.phase AND b.batch_index=p.batch_index JOIN s2_combat_breath_check c ON c.combat_id=p.combat_id AND c.event_sequence=p.event_sequence AND c.phase=p.phase AND c.batch_index=p.batch_index AND c.check_index=p.save_check_index
)
SELECT HEX(combat_id) AS combat_id,event_sequence,phase,batch_index,packet_index FROM amounts WHERE damage_amount<>IF(immune,0,IF(resistant,FLOOR(after_save/2),after_save)*IF(vulnerable,2,1));
SELECT HEX(o.combat_id) AS combat_id,o.event_sequence,o.phase,o.batch_index,o.obligation_index FROM s2_combat_breath_concentration o
LEFT JOIN s2_combat_breath_packet p ON p.combat_id=o.combat_id AND p.event_sequence=o.event_sequence AND p.phase=o.phase AND p.batch_index=o.batch_index AND p.packet_key=o.packet_key
LEFT JOIN s2_combat_breath_check c ON c.combat_id=o.combat_id AND c.event_sequence=o.event_sequence AND c.phase=o.phase AND c.batch_index=o.batch_index AND c.check_index=o.check_index
WHERE p.packet_key IS NULL OR p.target_ordinal<>o.target_ordinal OR p.damage_amount<>o.damage_amount
   OR (o.obligation_status IN (_binary'CHECK_SUCCEEDED',_binary'CHECK_FAILED') AND (o.holder_source_key IS NULL OR o.check_index IS NULL OR c.check_index IS NULL OR c.purpose<>_binary'CONCENTRATION_CON' OR c.target_ordinal<>o.target_ordinal OR o.difficulty_class<>GREATEST(10,FLOOR(o.damage_amount/2)) OR c.difficulty_class<>o.difficulty_class OR c.success<>IF(o.obligation_status=_binary'CHECK_SUCCEEDED',1,0)))
   OR (o.obligation_status IN (_binary'NOT_REQUIRED',_binary'FORCED_ENDED') AND o.check_index IS NOT NULL);
-- Rest completion is an accepted scene fact; read-only verification never advances a clock.
SELECT HEX(r.combat_id) AS combat_id,r.event_sequence,r.phase,r.rest_index FROM s2_combat_monster_rest r
WHERE NOT EXISTS(SELECT 1 FROM s2_combat_monster m WHERE m.combat_id=r.combat_id AND m.event_sequence=r.event_sequence AND m.phase=r.phase AND m.monster_id=r.monster_id)
   OR r.completes_at_second-r.started_at_second<>IF(r.rest_kind=_binary'SHORT',3600,28800)
   OR (r.rest_status=_binary'ACTIVE' AND r.finished_operation_id IS NOT NULL)
   OR (r.rest_status<>_binary'ACTIVE' AND (r.finished_operation_id IS NULL OR NOT EXISTS(SELECT 1 FROM s2_combat_event e WHERE e.combat_id=r.combat_id AND e.operation_id=r.finished_operation_id AND e.command_kind=IF(r.rest_status=_binary'COMPLETED',_binary'COMPLETE_MONSTER_REST',_binary'REENTER_COMBAT'))));
SELECT HEX(i.combat_id) AS combat_id,i.event_sequence FROM s2_combat_monster_rest_input i JOIN s2_combat_event e ON e.combat_id=i.combat_id AND e.event_sequence=i.event_sequence
WHERE e.command_kind NOT IN (_binary'BEGIN_MONSTER_REST',_binary'COMPLETE_MONSTER_REST') OR (e.command_kind=_binary'BEGIN_MONSTER_REST' AND i.rest_key IS NOT NULL) OR (e.command_kind=_binary'COMPLETE_MONSTER_REST' AND i.rest_key IS NULL);

-- Saved defense and holder references must identify the actual target owner, including ended sources.
SELECT HEX(d.combat_id) AS combat_id,d.event_sequence,d.phase,d.batch_index,d.packet_index,d.defense_index FROM s2_combat_breath_defense d
JOIN s2_combat_breath_packet p ON p.combat_id=d.combat_id AND p.event_sequence=d.event_sequence AND p.phase=d.phase AND p.batch_index=d.batch_index AND p.packet_index=d.packet_index
JOIN s2_combat_breath_target t ON t.combat_id=p.combat_id AND t.event_sequence=p.event_sequence AND t.phase=p.phase AND t.batch_index=p.batch_index AND t.target_index=p.target_ordinal
WHERE t.creature_kind<>_binary'MONSTER' OR NOT EXISTS(SELECT 1 FROM s2_combat_monster m WHERE m.combat_id=t.combat_id AND m.event_sequence=t.event_sequence AND m.phase=t.phase AND m.monster_id=t.creature_id AND m.monster_module=d.source_module AND m.monster_type=d.source_type AND m.monster_key=d.source_key);
SELECT HEX(o.combat_id) AS combat_id,o.event_sequence,o.phase,o.batch_index,o.obligation_index FROM s2_combat_breath_concentration o
JOIN s2_combat_breath_target t ON t.combat_id=o.combat_id AND t.event_sequence=o.event_sequence AND t.phase=o.phase AND t.batch_index=o.batch_index AND t.target_index=o.target_ordinal
WHERE o.holder_source_key IS NOT NULL AND NOT EXISTS(SELECT 1 FROM s2_combat_concentration h WHERE h.combat_id=o.combat_id AND h.event_sequence<=o.event_sequence AND h.actor_id=t.creature_id AND h.source_key=o.holder_source_key);
SELECT HEX(b.combat_id) AS combat_id,b.event_sequence,b.phase,b.batch_index FROM s2_combat_save_damage_batch b
JOIN s2_combat_event e ON e.combat_id=b.combat_id AND e.operation_id=b.release_operation_id
WHERE NOT EXISTS(SELECT 1 FROM s2_combat_breath_input i WHERE i.combat_id=e.combat_id AND i.event_sequence=e.event_sequence AND i.direction=b.direction AND i.action_rule_module=b.action_rule_module AND i.action_rule_type=b.action_rule_type AND i.action_rule_key=b.action_rule_key AND i.action_collection=b.action_collection AND i.action_module=b.action_module AND i.action_key=b.action_key);
