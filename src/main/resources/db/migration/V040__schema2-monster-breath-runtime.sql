-- Forward-only finite monster actions, shared save-damage batches and monster rest ownership.

-- CHECKSUM-SCOPE-BEGIN
-- Refuse any prior INTERNAL scene, including ENDED histories, before persistent DDL.
CREATE TEMPORARY TABLE v040_preflight (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO v040_preflight(ok) SELECT CASE WHEN CAST(DATABASE() AS BINARY)=_binary'dnd_tool_se' AND (FIND_IN_SET('STRICT_TRANS_TABLES',@@SESSION.sql_mode)>0 OR FIND_IN_SET('STRICT_ALL_TABLES',@@SESSION.sql_mode)>0) AND NOT EXISTS (SELECT 1 FROM s2_combat_runtime) AND (SELECT COUNT(*) FROM schema_meta)=39 AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version=1 AND CAST(script_name AS BINARY)=_binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version=2 AND CAST(script_name AS BINARY)=_binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version=3 AND CAST(script_name AS BINARY)=_binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version=4 AND CAST(script_name AS BINARY)=_binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY)=_binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version=5 AND CAST(script_name AS BINARY)=_binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version=6 AND CAST(script_name AS BINARY)=_binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version=7 AND CAST(script_name AS BINARY)=_binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY)=_binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version=8 AND CAST(script_name AS BINARY)=_binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version=9 AND CAST(script_name AS BINARY)=_binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version=10 AND CAST(script_name AS BINARY)=_binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version=11 AND CAST(script_name AS BINARY)=_binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version=12 AND CAST(script_name AS BINARY)=_binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY)=_binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version=13 AND CAST(script_name AS BINARY)=_binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY)=_binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version=14 AND CAST(script_name AS BINARY)=_binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY)=_binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version=15 AND CAST(script_name AS BINARY)=_binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version=16 AND CAST(script_name AS BINARY)=_binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version=17 AND CAST(script_name AS BINARY)=_binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY)=_binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version=18 AND CAST(script_name AS BINARY)=_binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY)=_binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version=19 AND CAST(script_name AS BINARY)=_binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version=20 AND CAST(script_name AS BINARY)=_binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version=21 AND CAST(script_name AS BINARY)=_binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version=22 AND CAST(script_name AS BINARY)=_binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY)=_binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version=23 AND CAST(script_name AS BINARY)=_binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY)=_binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version=24 AND CAST(script_name AS BINARY)=_binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY)=_binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version=25 AND CAST(script_name AS BINARY)=_binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY)=_binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version=26 AND CAST(script_name AS BINARY)=_binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY)=_binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version=27 AND CAST(script_name AS BINARY)=_binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version=28 AND CAST(script_name AS BINARY)=_binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a') OR (schema_version=29 AND CAST(script_name AS BINARY)=_binary'V029__schema2-character-check-history.sql' AND CAST(script_sha256 AS BINARY)=_binary'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b') OR (schema_version=30 AND CAST(script_name AS BINARY)=_binary'V030__schema2-equipment-health-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3') OR (schema_version=31 AND CAST(script_name AS BINARY)=_binary'V031__schema2-equipment-health-history.sql' AND CAST(script_sha256 AS BINARY)=_binary'1b9f8959ad5c3d6e4b417d2672a7a06cf8df572e9059e4addfde01856e57b79e') OR (schema_version=32 AND CAST(script_name AS BINARY)=_binary'V032__schema2-combat-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'e1bc7521e953b640b42c869f734961d8e9153e2036788468c9ea13db39be5cae') OR (schema_version=33 AND CAST(script_name AS BINARY)=_binary'V033__schema2-combat-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'df5ddd066e6694d836daf2891ead4271e5ae6e2ed1cb2bf13a009d75d72c83e7') OR (schema_version=34 AND CAST(script_name AS BINARY)=_binary'V034__schema2-combat-time-ground.sql' AND CAST(script_sha256 AS BINARY)=_binary'b9872fe91f87cf0d0c8cb2c16e955c6d0e4e566aed52d56071afa89217affe1f') OR (schema_version=35 AND CAST(script_name AS BINARY)=_binary'V035__schema2-spell-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'80765ab1aa82342d0d1d6ed54f38df6caa94b539ddf19d2ca3f4872fd5ecfdaf') OR (schema_version=36 AND CAST(script_name AS BINARY)=_binary'V036__schema2-spell-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'd92de4fe6fd7b76abd3a8b4b63dce7ab98bb38352d714b9a34a61498b1f7b689') OR (schema_version=37 AND CAST(script_name AS BINARY)=_binary'V037__schema2-wizard-environment-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'7937263eb20e92adab82785aed6dead9ad99bd5fa46c941505c483bbc0f68cfb') OR (schema_version=38 AND CAST(script_name AS BINARY)=_binary'V038__schema2-reactions-and-ongoing-scenes.sql' AND CAST(script_sha256 AS BINARY)=_binary'3449378f943deea0fee5b3711c13f2a67fde7edaafe0646f6b650a513de37ee5') OR (schema_version=39 AND CAST(script_name AS BINARY)=_binary'V039__schema2-monster-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'1128fbaf4955634a04e206e40a5af0a3e532fc2035511d140ce53a3a9bd71ea4'))=39 AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('s2_combat_monster_deployment', 's2_combat_breath_input', 's2_combat_monster', 's2_combat_monster_action', 's2_combat_save_damage_batch', 's2_combat_breath_candidate', 's2_combat_breath_target', 's2_combat_breath_die', 's2_combat_breath_check', 's2_combat_breath_packet', 's2_combat_breath_defense', 's2_combat_breath_concentration', 's2_combat_monster_rest_input', 's2_combat_monster_rest'))=0 THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v040_preflight;

ALTER TABLE s2_combat_state ADD monster_count INT NOT NULL DEFAULT 0, ADD batch_count INT NOT NULL DEFAULT 0, ADD monster_rest_count INT NOT NULL DEFAULT 0, ADD CONSTRAINT ck_c40_counts CHECK (monster_count BETWEEN 0 AND 64 AND batch_count BETWEEN 0 AND 128 AND monster_rest_count BETWEEN 0 AND 128);

ALTER TABLE s2_combat_event DROP CHECK ck_c33_2;
ALTER TABLE s2_combat_event ADD CONSTRAINT ck_c33_2 CHECK (command_kind IN (_binary'START', _binary'MONSTER_BREATH', _binary'BEGIN_MONSTER_REST', _binary'COMPLETE_MONSTER_REST', _binary'END_TURN', _binary'DASH', _binary'DISENGAGE', _binary'DODGE', _binary'STAND_UP', _binary'BEGIN_ATTACK', _binary'ATTACK', _binary'FORFEIT_ATTACKS', _binary'MOVE', _binary'PASS_REACTION', _binary'DECLARE_OPPORTUNITY_ATTACK', _binary'DECLARE_SHIELD', _binary'DECLARE_COUNTERSPELL', _binary'RELEASE_READY', _binary'ADVANCE_WINDOW', _binary'CONTINUE_MOVEMENT', _binary'CONTINUE_FLOW', _binary'CAST', _binary'READY_ATTACK', _binary'READY_MOVE', _binary'READY_SPELL', _binary'CREATE_ENVIRONMENT_ZONE', _binary'END_ENVIRONMENT_ZONE', _binary'EXPLORATION_HEALTH', _binary'EXPLORATION_ADVANCE_TIME', _binary'EXPLORATION_MOVE', _binary'REENTER_COMBAT', _binary'CLOSE_SCENE', _binary'END_CONCENTRATION', _binary'END_COMBAT') AND event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(request_digest)=64 AND NOT REGEXP_LIKE(CONVERT(request_digest USING latin1),'[^0-9a-f]') AND ((command_kind=_binary'START' AND event_sequence=1 AND actor_id IS NULL) OR (command_kind<>_binary'START' AND event_sequence>1 AND actor_id IS NOT NULL)) AND ((command_kind IN (_binary'ATTACK',_binary'EXPLORATION_HEALTH') AND target_id IS NOT NULL) OR (command_kind NOT IN (_binary'ATTACK',_binary'EXPLORATION_HEALTH') AND target_id IS NULL)) AND ((command_kind IN (_binary'ATTACK',_binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NOT NULL) OR (command_kind NOT IN (_binary'ATTACK',_binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NULL)) AND ((command_kind IN (_binary'PASS_REACTION',_binary'DECLARE_OPPORTUNITY_ATTACK',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL',_binary'RELEASE_READY',_binary'ADVANCE_WINDOW',_binary'CONTINUE_MOVEMENT',_binary'CONTINUE_FLOW') AND flow_key IS NOT NULL AND leaf_key IS NOT NULL AND (command_kind=_binary'CONTINUE_FLOW' OR window_key IS NOT NULL) AND ((command_kind IN (_binary'CONTINUE_MOVEMENT',_binary'CONTINUE_FLOW') AND opportunity_key IS NULL) OR (command_kind IN (_binary'PASS_REACTION',_binary'DECLARE_OPPORTUNITY_ATTACK',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL',_binary'RELEASE_READY',_binary'ADVANCE_WINDOW') AND opportunity_key IS NOT NULL))) OR (command_kind NOT IN (_binary'PASS_REACTION',_binary'DECLARE_OPPORTUNITY_ATTACK',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL',_binary'RELEASE_READY',_binary'ADVANCE_WINDOW',_binary'CONTINUE_MOVEMENT',_binary'CONTINUE_FLOW') AND flow_key IS NULL AND leaf_key IS NULL AND window_key IS NULL AND opportunity_key IS NULL)) AND ((command_kind=_binary'RELEASE_READY' AND ready_key IS NOT NULL) OR (command_kind<>_binary'RELEASE_READY' AND ready_key IS NULL)) AND ((command_kind=_binary'END_ENVIRONMENT_ZONE' AND zone_key IS NOT NULL) OR (command_kind<>_binary'END_ENVIRONMENT_ZONE' AND zone_key IS NULL)) AND ((command_kind=_binary'EXPLORATION_ADVANCE_TIME' AND seconds BETWEEN 1 AND 60) OR (command_kind<>_binary'EXPLORATION_ADVANCE_TIME' AND seconds=0)) AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(operation_id) = 16 AND (ORD(SUBSTRING(operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(operation_id, 9, 1)) & 192) = 128) AND (actor_id IS NULL OR (OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128)) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (item_id IS NULL OR (OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)) AND (flow_key IS NULL OR (OCTET_LENGTH(flow_key) = 16 AND (ORD(SUBSTRING(flow_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(flow_key, 9, 1)) & 192) = 128)) AND (leaf_key IS NULL OR (OCTET_LENGTH(leaf_key) = 16 AND (ORD(SUBSTRING(leaf_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(leaf_key, 9, 1)) & 192) = 128)) AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)) AND (ready_key IS NULL OR (OCTET_LENGTH(ready_key) = 16 AND (ORD(SUBSTRING(ready_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(ready_key, 9, 1)) & 192) = 128)) AND (zone_key IS NULL OR (OCTET_LENGTH(zone_key) = 16 AND (ORD(SUBSTRING(zone_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(zone_key, 9, 1)) & 192) = 128)));

ALTER TABLE s2_combat_command_tie DROP CHECK ck_c33_7;
ALTER TABLE s2_combat_command_tie ADD CONSTRAINT ck_c33_7 CHECK (tie_index BETWEEN 0 AND 63 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128);

ALTER TABLE s2_combat_state DROP CHECK ck_c33_10;
ALTER TABLE s2_combat_state ADD CONSTRAINT ck_c33_10 CHECK (state_index = 0 AND rule_type = _binary'adventure.combat' AND combat_status IN (_binary'ACTIVE', _binary'EXPLORATION', _binary'ENDED') AND round_number >= 1 AND turn_number >= 1 AND active_ordinal BETWEEN 0 AND 63 AND ((phase = _binary'AFTER' AND row_version = event_sequence - 1) OR (phase = _binary'BEFORE' AND event_sequence > 1 AND row_version = event_sequence - 2)) AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128);

ALTER TABLE s2_combat_actor DROP CHECK ck_c33_11;
ALTER TABLE s2_combat_actor ADD CONSTRAINT ck_c33_11 CHECK (actor_index BETWEEN 0 AND 63 AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND side BETWEEN 0 AND 15 AND movement_spent_half_feet BETWEEN 0 AND 1000000 AND movement_bonus BETWEEN 0 AND 1000000 AND ((sequence_key IS NULL AND attack_limit IS NULL AND performed IS NULL AND forfeited IS NULL AND sequence_closed IS NULL) OR (sequence_key IS NOT NULL AND attack_limit IS NOT NULL AND performed IS NOT NULL AND forfeited IS NOT NULL AND sequence_closed IS NOT NULL AND attack_limit BETWEEN 1 AND 20 AND performed BETWEEN 0 AND attack_limit AND forfeited BETWEEN 0 AND attack_limit AND performed + forfeited <= attack_limit AND sequence_closed = IF(performed + forfeited = attack_limit, 1, 0))) AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128 AND action_available IN (0, 1) AND reaction_available IN (0, 1) AND disengaged IN (0, 1) AND dodging IN (0, 1) AND (sequence_key IS NULL OR (OCTET_LENGTH(sequence_key) = 16 AND (ORD(SUBSTRING(sequence_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(sequence_key, 9, 1)) & 192) = 128)) AND (sequence_closed IS NULL OR sequence_closed IN (0, 1)));

ALTER TABLE s2_combat_initiative DROP CHECK ck_c33_12;
ALTER TABLE s2_combat_initiative ADD CONSTRAINT ck_c33_12 CHECK (initiative_index BETWEEN 0 AND 63 AND ordinal = initiative_index AND d20 BETWEEN 1 AND 20 AND dexterity_modifier BETWEEN -5 AND 10 AND total = d20 + dexterity_modifier AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128);

ALTER TABLE s2_combat_frame DROP CHECK ck_c33_14;
ALTER TABLE s2_combat_frame ADD CONSTRAINT ck_c33_14 CHECK (frame_index BETWEEN 0 AND 1023 AND frame_kind IN (_binary'SAVE_DAMAGE', _binary'MOVEMENT', _binary'ATTACK', _binary'CAST', _binary'READY_RELEASE', _binary'HEALTH', _binary'SAVING_THROW', _binary'REACTION_SELECTION') AND invocation IN (_binary'ROOT', _binary'REACTION', _binary'CONTINUATION') AND frame_status IN (_binary'RUNNABLE', _binary'WAITING_WINDOW', _binary'WAITING_CHILD', _binary'PENDING', _binary'DECIDED', _binary'TERMINAL') AND stage IN (_binary'SAVE_DAMAGE_ROLLED', _binary'SAVE_DAMAGE_COMPLETED', _binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'MOVEMENT_CONTINUE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'ATTACK_RESOLVED', _binary'CAST_BEFORE_COMPLETION', _binary'CAST_RESOLVED', _binary'READY_TRIGGER_AFTER', _binary'READY_RELEASE_RESOLVED', _binary'SAVING_THROW_RESOLVED', _binary'HEALTH_RESOLVED', _binary'MOVEMENT_COMPLETED', _binary'MOVEMENT_STOPPED') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(owner_key) = 16 AND (ORD(SUBSTRING(owner_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(owner_key, 9, 1)) & 192) = 128 AND (parent_frame_key IS NULL OR (OCTET_LENGTH(parent_frame_key) = 16 AND (ORD(SUBSTRING(parent_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(parent_frame_key, 9, 1)) & 192) = 128)) AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)));

ALTER TABLE s2_character_combat_input DROP CHECK ck_s2_combat_seconds;
ALTER TABLE s2_character_combat_input ADD CONSTRAINT ck_s2_combat_seconds CHECK (seconds_advanced BETWEEN 0 AND 28800 AND (seconds_advanced = 0 OR (damage_amount = 0 AND critical = 0)));

ALTER TABLE s2_combat_ready_input DROP CHECK ck_s238_ready_input;
ALTER TABLE s2_combat_ready_input ADD CONSTRAINT ck_s238_ready_input CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'COMMAND',_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (trigger_subject_id IS NULL OR (OCTET_LENGTH(trigger_subject_id) = 16 AND (ORD(SUBSTRING(trigger_subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(trigger_subject_id, 9, 1)) & 192) = 128)) AND (trigger_x IS NULL OR trigger_x BETWEEN -10000 AND 10000) AND (trigger_y IS NULL OR trigger_y BETWEEN -10000 AND 10000) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (item_id IS NULL OR (OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)) AND (material_item_id IS NULL OR (OCTET_LENGTH(material_item_id) = 16 AND (ORD(SUBSTRING(material_item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(material_item_id, 9, 1)) & 192) = 128)) AND (target_cast_frame_key IS NULL OR (OCTET_LENGTH(target_cast_frame_key) = 16 AND (ORD(SUBSTRING(target_cast_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_cast_frame_key, 9, 1)) & 192) = 128)) AND ready_index BETWEEN 0 AND 127 AND trigger_kind IN (_binary'MONSTER_ACTION_COMPLETED', _binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'CAST_BEFORE_COMPLETION', _binary'CREATURE_ENTER_CELL_AFTER', _binary'ATTACK_COMPLETED', _binary'CAST_COMPLETED') AND response_kind IN (_binary'ATTACK', _binary'MOVE', _binary'SPELL') AND point_count BETWEEN 0 AND 64 AND target_count BETWEEN 0 AND 16 AND slot_level BETWEEN 0 AND 9 AND ((trigger_x IS NULL AND trigger_y IS NULL) OR (trigger_x IS NOT NULL AND trigger_y IS NOT NULL)) AND ((casting_module IS NULL AND casting_type IS NULL AND casting_key IS NULL) OR (casting_module IS NOT NULL AND casting_type IS NOT NULL AND casting_key IS NOT NULL AND casting_type IN (_binary'character.spellcasting',_binary'character.wizard_spellcasting') AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128)) AND ((spell_module IS NULL AND spell_type IS NULL AND spell_key IS NULL) OR (spell_module IS NOT NULL AND spell_type IS NOT NULL AND spell_key IS NOT NULL AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128)) AND ((response_kind=_binary'ATTACK' AND target_id IS NOT NULL AND item_id IS NOT NULL AND point_count=0 AND target_count=0 AND casting_module IS NULL AND spell_module IS NULL AND slot_level=0 AND material_item_id IS NULL AND target_cast_frame_key IS NULL) OR (response_kind=_binary'MOVE' AND target_id IS NULL AND item_id IS NULL AND point_count BETWEEN 1 AND 64 AND target_count=0 AND casting_module IS NULL AND spell_module IS NULL AND slot_level=0 AND material_item_id IS NULL AND target_cast_frame_key IS NULL) OR (response_kind=_binary'SPELL' AND target_id IS NULL AND item_id IS NULL AND point_count=0 AND target_count BETWEEN 1 AND 3 AND casting_module IS NOT NULL AND spell_module IS NOT NULL AND slot_level BETWEEN 1 AND 9)));

ALTER TABLE s2_combat_window_trigger DROP CHECK ck_s238_window_trigger;
ALTER TABLE s2_combat_window_trigger ADD CONSTRAINT ck_s238_window_trigger CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(trigger_key) = 16 AND (ORD(SUBSTRING(trigger_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(trigger_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(source_frame_key) = 16 AND (ORD(SUBSTRING(source_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(source_frame_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (x IS NULL OR x BETWEEN -10000 AND 10000) AND (y IS NULL OR y BETWEEN -10000 AND 10000) AND trigger_kind IN (_binary'MONSTER_ACTION_COMPLETED', _binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'CAST_BEFORE_COMPLETION', _binary'CREATURE_ENTER_CELL_AFTER', _binary'ATTACK_COMPLETED', _binary'CAST_COMPLETED') AND ordinal BETWEEN 0 AND 1023 AND ((x IS NULL AND y IS NULL) OR (x IS NOT NULL AND y IS NOT NULL)));

ALTER TABLE s2_combat_trigger DROP CHECK ck_s238_trigger;
ALTER TABLE s2_combat_trigger ADD CONSTRAINT ck_s238_trigger CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(trigger_key) = 16 AND (ORD(SUBSTRING(trigger_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(trigger_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(source_frame_key) = 16 AND (ORD(SUBSTRING(source_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(source_frame_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (x IS NULL OR x BETWEEN -10000 AND 10000) AND (y IS NULL OR y BETWEEN -10000 AND 10000) AND trigger_index BETWEEN 0 AND 1023 AND trigger_kind IN (_binary'MONSTER_ACTION_COMPLETED', _binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'CAST_BEFORE_COMPLETION', _binary'CREATURE_ENTER_CELL_AFTER', _binary'ATTACK_COMPLETED', _binary'CAST_COMPLETED') AND ordinal BETWEEN 0 AND 1023 AND ((x IS NULL AND y IS NULL) OR (x IS NOT NULL AND y IS NOT NULL)));

CREATE TABLE s2_combat_monster_deployment (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    deployment_index INT NOT NULL,
    monster_id VARBINARY(16) NOT NULL,
    monster_module VARBINARY(64) NOT NULL,
    monster_type VARBINARY(40) NOT NULL,
    monster_key VARBINARY(128) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    side INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, deployment_index),
    CONSTRAINT fk_c40_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_0 CHECK (event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND deployment_index BETWEEN 0 AND 63 AND OCTET_LENGTH(monster_id) = 16 AND (ORD(SUBSTRING(monster_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(monster_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(monster_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(monster_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND monster_type = _binary'adventure.monster' AND OCTET_LENGTH(monster_key) BETWEEN 1 AND 128 AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND side BETWEEN 0 AND 15)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_input (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    input_index INT NOT NULL,
    direction INT NOT NULL,
    action_rule_module VARBINARY(64) NOT NULL,
    action_rule_type VARBINARY(40) NOT NULL,
    action_rule_key VARBINARY(128) NOT NULL,
    action_collection VARBINARY(32) NOT NULL,
    action_module VARBINARY(64) NOT NULL,
    action_key VARBINARY(64) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, input_index),
    CONSTRAINT fk_c40_1 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_1 CHECK (event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND input_index BETWEEN 0 AND 0 AND direction BETWEEN 0 AND 7 AND OCTET_LENGTH(action_rule_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(action_rule_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND action_rule_type = _binary'adventure.monster' AND OCTET_LENGTH(action_rule_key) BETWEEN 1 AND 128 AND action_collection = _binary'actions' AND OCTET_LENGTH(action_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(action_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND OCTET_LENGTH(action_key) BETWEEN 1 AND 64)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_monster (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    monster_index INT NOT NULL,
    monster_id VARBINARY(16) NOT NULL,
    monster_module VARBINARY(64) NOT NULL,
    monster_type VARBINARY(40) NOT NULL,
    monster_key VARBINARY(128) NOT NULL,
    hit_points INT NOT NULL,
    dead INT NOT NULL,
    action_count INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, monster_index),
    CONSTRAINT fk_c40_2 FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_c40_monster (combat_id,event_sequence,phase,monster_id),
    CONSTRAINT ck_c40_2 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND monster_index BETWEEN 0 AND 63 AND OCTET_LENGTH(monster_id) = 16 AND (ORD(SUBSTRING(monster_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(monster_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(monster_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(monster_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND monster_type = _binary'adventure.monster' AND OCTET_LENGTH(monster_key) BETWEEN 1 AND 128 AND hit_points BETWEEN 0 AND 65535 AND dead IN (0,1) AND action_count BETWEEN 0 AND 16 AND dead = IF(hit_points=0,1,0))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_monster_action (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    monster_index INT NOT NULL,
    action_index INT NOT NULL,
    available INT NOT NULL,
    action_rule_module VARBINARY(64) NOT NULL,
    action_rule_type VARBINARY(40) NOT NULL,
    action_rule_key VARBINARY(128) NOT NULL,
    action_collection VARBINARY(32) NOT NULL,
    action_module VARBINARY(64) NOT NULL,
    action_key VARBINARY(64) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, monster_index, action_index),
    CONSTRAINT fk_c40_3 FOREIGN KEY (combat_id, event_sequence, phase, monster_index) REFERENCES s2_combat_monster (combat_id, event_sequence, phase, monster_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_3 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND monster_index BETWEEN 0 AND 63 AND action_index BETWEEN 0 AND 15 AND available IN (0,1) AND OCTET_LENGTH(action_rule_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(action_rule_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND action_rule_type = _binary'adventure.monster' AND OCTET_LENGTH(action_rule_key) BETWEEN 1 AND 128 AND action_collection = _binary'actions' AND OCTET_LENGTH(action_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(action_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND OCTET_LENGTH(action_key) BETWEEN 1 AND 64)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_save_damage_batch (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    batch_key VARBINARY(16) NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    source_id VARBINARY(16) NOT NULL,
    release_operation_id VARBINARY(16) NOT NULL,
    released_turn_number BIGINT NOT NULL,
    space_policy VARBINARY(64) NOT NULL,
    roster_version BIGINT NOT NULL,
    origin_x INT NOT NULL,
    origin_y INT NOT NULL,
    direction INT NOT NULL,
    batch_status VARBINARY(16) NOT NULL,
    shared_roll_key VARBINARY(16) NULL,
    raw_total INT NULL,
    effective_total INT NULL,
    candidate_count INT NOT NULL,
    target_count INT NOT NULL,
    die_count INT NOT NULL,
    check_count INT NOT NULL,
    packet_count INT NOT NULL,
    obligation_count INT NOT NULL,
    action_rule_module VARBINARY(64) NOT NULL,
    action_rule_type VARBINARY(40) NOT NULL,
    action_rule_key VARBINARY(128) NOT NULL,
    action_collection VARBINARY(32) NOT NULL,
    action_module VARBINARY(64) NOT NULL,
    action_key VARBINARY(64) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index),
    CONSTRAINT fk_c40_4 FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_c40_batch (combat_id,event_sequence,phase,batch_key),
    CONSTRAINT ck_c40_4 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND OCTET_LENGTH(batch_key) = 16 AND (ORD(SUBSTRING(batch_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(batch_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(source_id) = 16 AND (ORD(SUBSTRING(source_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(source_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(release_operation_id) = 16 AND (ORD(SUBSTRING(release_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(release_operation_id, 9, 1)) & 192) = 128 AND origin_x BETWEEN -10000 AND 10000 AND origin_y BETWEEN -10000 AND 10000 AND direction BETWEEN 0 AND 7 AND (shared_roll_key IS NULL OR (OCTET_LENGTH(shared_roll_key) = 16 AND (ORD(SUBSTRING(shared_roll_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(shared_roll_key, 9, 1)) & 192) = 128)) AND OCTET_LENGTH(action_rule_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(action_rule_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND action_rule_type = _binary'adventure.monster' AND OCTET_LENGTH(action_rule_key) BETWEEN 1 AND 128 AND action_collection = _binary'actions' AND OCTET_LENGTH(action_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(action_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND OCTET_LENGTH(action_key) BETWEEN 1 AND 64 AND batch_status IN (_binary'ROLLED', _binary'COMPLETED', _binary'NO_TARGETS') AND released_turn_number BETWEEN 1 AND 8192 AND roster_version BETWEEN 0 AND 127 AND candidate_count BETWEEN 2 AND 64 AND target_count BETWEEN 0 AND 64 AND die_count IN (0,6) AND check_count BETWEEN 0 AND 128 AND packet_count BETWEEN 0 AND 64 AND obligation_count BETWEEN 0 AND 64 AND ((batch_status=_binary'NO_TARGETS' AND target_count=0 AND shared_roll_key IS NULL AND die_count=0 AND raw_total IS NULL AND effective_total IS NULL) OR (batch_status<>_binary'NO_TARGETS' AND target_count>0 AND shared_roll_key IS NOT NULL AND die_count=6 AND raw_total IS NOT NULL AND effective_total IS NOT NULL AND raw_total BETWEEN 6 AND 36 AND effective_total=raw_total)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_candidate (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    candidate_index INT NOT NULL,
    creature_kind VARBINARY(16) NOT NULL,
    creature_id VARBINARY(16) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    included INT NOT NULL,
    reason VARBINARY(64) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index, candidate_index),
    CONSTRAINT fk_c40_5 FOREIGN KEY (combat_id, event_sequence, phase, batch_index) REFERENCES s2_combat_save_damage_batch (combat_id, event_sequence, phase, batch_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_5 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND candidate_index BETWEEN 0 AND 63 AND creature_kind IN (_binary'CHARACTER', _binary'MONSTER') AND OCTET_LENGTH(creature_id) = 16 AND (ORD(SUBSTRING(creature_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(creature_id, 9, 1)) & 192) = 128 AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND included IN (0,1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_target (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    target_index INT NOT NULL,
    creature_kind VARBINARY(16) NOT NULL,
    creature_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index, target_index),
    CONSTRAINT fk_c40_6 FOREIGN KEY (combat_id, event_sequence, phase, batch_index) REFERENCES s2_combat_save_damage_batch (combat_id, event_sequence, phase, batch_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_6 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND target_index BETWEEN 0 AND 63 AND creature_kind IN (_binary'CHARACTER', _binary'MONSTER') AND OCTET_LENGTH(creature_id) = 16 AND (ORD(SUBSTRING(creature_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(creature_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_die (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    die_index INT NOT NULL,
    die_value INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index, die_index),
    CONSTRAINT fk_c40_7 FOREIGN KEY (combat_id, event_sequence, phase, batch_index) REFERENCES s2_combat_save_damage_batch (combat_id, event_sequence, phase, batch_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_7 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND die_index BETWEEN 0 AND 5 AND die_value BETWEEN 1 AND 6)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_check (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    check_index INT NOT NULL,
    target_ordinal INT NOT NULL,
    purpose VARBINARY(32) NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    check_mode VARBINARY(16) NOT NULL,
    first_d20 INT NULL,
    second_d20 INT NULL,
    ability_modifier INT NOT NULL,
    proficiency_bonus INT NOT NULL,
    advantage INT NOT NULL,
    disadvantage INT NOT NULL,
    blessing_effect_key VARBINARY(16) NULL,
    blessing_d4 INT NULL,
    difficulty_class INT NOT NULL,
    total INT NOT NULL,
    success INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index, check_index),
    CONSTRAINT fk_c40_8 FOREIGN KEY (combat_id, event_sequence, phase, batch_index) REFERENCES s2_combat_save_damage_batch (combat_id, event_sequence, phase, batch_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_8 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND check_index BETWEEN 0 AND 127 AND (first_d20 IS NULL OR first_d20 BETWEEN 1 AND 20) AND (second_d20 IS NULL OR second_d20 BETWEEN 1 AND 20) AND advantage IN (0,1) AND disadvantage IN (0,1) AND (blessing_effect_key IS NULL OR (OCTET_LENGTH(blessing_effect_key) = 16 AND (ORD(SUBSTRING(blessing_effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(blessing_effect_key, 9, 1)) & 192) = 128)) AND success IN (0,1) AND purpose IN (_binary'BREATH_DEX', _binary'CONCENTRATION_CON') AND check_mode IN (_binary'ROLL', _binary'AUTO_FAILURE') AND target_ordinal BETWEEN 0 AND 63 AND ability_modifier BETWEEN -5 AND 10 AND proficiency_bonus BETWEEN 0 AND 6 AND difficulty_class BETWEEN 0 AND 32767 AND ((blessing_effect_key IS NULL AND blessing_d4 IS NULL) OR (blessing_effect_key IS NOT NULL AND blessing_d4 IS NOT NULL AND blessing_d4 BETWEEN 1 AND 4)) AND ((check_mode=_binary'AUTO_FAILURE' AND first_d20 IS NULL AND second_d20 IS NULL AND success=0) OR (check_mode=_binary'ROLL' AND first_d20 IS NOT NULL AND ((advantage=disadvantage AND second_d20 IS NULL) OR (advantage<>disadvantage AND second_d20 IS NOT NULL)))))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_packet (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    packet_index INT NOT NULL,
    packet_key VARBINARY(16) NOT NULL,
    target_ordinal INT NOT NULL,
    shared_roll_key VARBINARY(16) NOT NULL,
    save_check_index INT NOT NULL,
    before_hit_points INT NOT NULL,
    before_temporary_hit_points INT NOT NULL,
    maximum_hit_points INT NOT NULL,
    defense_count INT NOT NULL,
    damage_amount INT NOT NULL,
    absorbed_temporary_hit_points INT NOT NULL,
    after_hit_points INT NOT NULL,
    after_temporary_hit_points INT NOT NULL,
    dead INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index, packet_index),
    CONSTRAINT fk_c40_9 FOREIGN KEY (combat_id, event_sequence, phase, batch_index) REFERENCES s2_combat_save_damage_batch (combat_id, event_sequence, phase, batch_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_9 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND packet_index BETWEEN 0 AND 63 AND OCTET_LENGTH(packet_key) = 16 AND (ORD(SUBSTRING(packet_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(packet_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(shared_roll_key) = 16 AND (ORD(SUBSTRING(shared_roll_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(shared_roll_key, 9, 1)) & 192) = 128 AND save_check_index BETWEEN 0 AND 63 AND before_hit_points BETWEEN 0 AND 65535 AND before_temporary_hit_points BETWEEN 0 AND 65535 AND maximum_hit_points BETWEEN 0 AND 65535 AND damage_amount BETWEEN 0 AND 65535 AND absorbed_temporary_hit_points BETWEEN 0 AND 65535 AND after_hit_points BETWEEN 0 AND 65535 AND after_temporary_hit_points BETWEEN 0 AND 65535 AND dead IN (0,1) AND target_ordinal = packet_index AND save_check_index BETWEEN 0 AND 127 AND defense_count BETWEEN 0 AND 32 AND maximum_hit_points >= 1 AND before_hit_points <= maximum_hit_points AND after_hit_points <= maximum_hit_points AND absorbed_temporary_hit_points <= before_temporary_hit_points AND after_temporary_hit_points = before_temporary_hit_points - absorbed_temporary_hit_points)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_defense (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    packet_index INT NOT NULL,
    defense_index INT NOT NULL,
    defense_kind VARBINARY(16) NOT NULL,
    source_module VARBINARY(64) NOT NULL,
    source_type VARBINARY(40) NOT NULL,
    source_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index, packet_index, defense_index),
    CONSTRAINT fk_c40_10 FOREIGN KEY (combat_id, event_sequence, phase, batch_index, packet_index) REFERENCES s2_combat_breath_packet (combat_id, event_sequence, phase, batch_index, packet_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_10 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND packet_index BETWEEN 0 AND 63 AND defense_index BETWEEN 0 AND 31 AND OCTET_LENGTH(source_module) BETWEEN 1 AND 64 AND REGEXP_LIKE(CONVERT(source_module USING latin1), '^[a-z][a-z0-9_]*$', 'c') AND OCTET_LENGTH(source_key) BETWEEN 1 AND 128 AND defense_kind IN (_binary'IMMUNITY', _binary'RESISTANCE', _binary'VULNERABILITY'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_breath_concentration (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    batch_index INT NOT NULL,
    obligation_index INT NOT NULL,
    target_ordinal INT NOT NULL,
    holder_source_key VARBINARY(16) NULL,
    packet_key VARBINARY(16) NOT NULL,
    damage_amount INT NOT NULL,
    difficulty_class INT NOT NULL,
    obligation_status VARBINARY(24) NOT NULL,
    check_index INT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, batch_index, obligation_index),
    CONSTRAINT fk_c40_11 FOREIGN KEY (combat_id, event_sequence, phase, batch_index) REFERENCES s2_combat_save_damage_batch (combat_id, event_sequence, phase, batch_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_11 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND batch_index BETWEEN 0 AND 127 AND obligation_index BETWEEN 0 AND 63 AND (holder_source_key IS NULL OR (OCTET_LENGTH(holder_source_key) = 16 AND (ORD(SUBSTRING(holder_source_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(holder_source_key, 9, 1)) & 192) = 128)) AND OCTET_LENGTH(packet_key) = 16 AND (ORD(SUBSTRING(packet_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(packet_key, 9, 1)) & 192) = 128 AND damage_amount BETWEEN 0 AND 65535 AND check_index BETWEEN 0 AND 127 AND target_ordinal BETWEEN 0 AND 63 AND obligation_status IN (_binary'NOT_REQUIRED', _binary'FORCED_ENDED', _binary'CHECK_SUCCEEDED', _binary'CHECK_FAILED') AND ((obligation_status IN (_binary'CHECK_SUCCEEDED',_binary'CHECK_FAILED') AND holder_source_key IS NOT NULL AND check_index IS NOT NULL AND check_index BETWEEN 0 AND 127 AND damage_amount>0 AND difficulty_class=GREATEST(10,FLOOR(damage_amount/2))) OR (obligation_status IN (_binary'NOT_REQUIRED',_binary'FORCED_ENDED') AND check_index IS NULL)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_monster_rest_input (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    input_index INT NOT NULL,
    rest_key VARBINARY(16) NULL,
    rest_kind VARBINARY(8) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, input_index),
    CONSTRAINT fk_c40_12 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_12 CHECK (event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND input_index BETWEEN 0 AND 0 AND (rest_key IS NULL OR (OCTET_LENGTH(rest_key) = 16 AND (ORD(SUBSTRING(rest_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(rest_key, 9, 1)) & 192) = 128)) AND rest_kind IN (_binary'SHORT', _binary'LONG'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_monster_rest (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    rest_index INT NOT NULL,
    rest_key VARBINARY(16) NOT NULL,
    monster_id VARBINARY(16) NOT NULL,
    rest_kind VARBINARY(8) NOT NULL,
    started_at_second BIGINT NOT NULL,
    completes_at_second BIGINT NOT NULL,
    rest_status VARBINARY(16) NOT NULL,
    finished_operation_id VARBINARY(16) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, rest_index),
    CONSTRAINT fk_c40_13 FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c40_13 CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND rest_index BETWEEN 0 AND 127 AND OCTET_LENGTH(rest_key) = 16 AND (ORD(SUBSTRING(rest_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(rest_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(monster_id) = 16 AND (ORD(SUBSTRING(monster_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(monster_id, 9, 1)) & 192) = 128 AND (finished_operation_id IS NULL OR (OCTET_LENGTH(finished_operation_id) = 16 AND (ORD(SUBSTRING(finished_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(finished_operation_id, 9, 1)) & 192) = 128)) AND rest_kind IN (_binary'SHORT', _binary'LONG') AND rest_status IN (_binary'ACTIVE', _binary'COMPLETED', _binary'INTERRUPTED') AND started_at_second BETWEEN 0 AND 60000000059 AND completes_at_second BETWEEN 0 AND 60000000059 AND completes_at_second-started_at_second=IF(rest_kind=_binary'SHORT',3600,28800) AND ((rest_status=_binary'ACTIVE' AND finished_operation_id IS NULL) OR (rest_status<>_binary'ACTIVE' AND finished_operation_id IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$

DROP TRIGGER s2_character_combat_input_insert$$
CREATE TRIGGER s2_character_combat_input_insert BEFORE INSERT ON s2_character_combat_input FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM s2_character_event WHERE operation_id=NEW.operation_id AND character_id=NEW.character_id AND event_sequence=NEW.event_sequence AND ((event_kind IN (_binary'COMBAT_DAMAGE_APPLIED',_binary'COMBAT_HEALING_APPLIED',_binary'COMBAT_ENVIRONMENT_HEALTH') AND NEW.seconds_advanced=0) OR (event_kind IN (_binary'COMBAT_DEATH_SAVE_RESOLVED',_binary'COMBAT_RESOURCE_SPENT') AND NEW.seconds_advanced=0 AND NEW.damage_amount=0 AND NEW.critical=0) OR (event_kind=_binary'COMBAT_TIME_ADVANCED' AND NEW.seconds_advanced BETWEEN 1 AND 28800 AND NEW.damage_amount=0 AND NEW.critical=0))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Schema2 scene character cause mismatch';
    END IF;
END$$

CREATE TRIGGER s2_combat_monster_deployment_update BEFORE UPDATE ON s2_combat_monster_deployment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_deployment_delete BEFORE DELETE ON s2_combat_monster_deployment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_input_update BEFORE UPDATE ON s2_combat_breath_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_input_delete BEFORE DELETE ON s2_combat_breath_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_update BEFORE UPDATE ON s2_combat_monster FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_delete BEFORE DELETE ON s2_combat_monster FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_action_update BEFORE UPDATE ON s2_combat_monster_action FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_action_delete BEFORE DELETE ON s2_combat_monster_action FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_save_damage_batch_update BEFORE UPDATE ON s2_combat_save_damage_batch FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_save_damage_batch_delete BEFORE DELETE ON s2_combat_save_damage_batch FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_candidate_update BEFORE UPDATE ON s2_combat_breath_candidate FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_candidate_delete BEFORE DELETE ON s2_combat_breath_candidate FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_target_update BEFORE UPDATE ON s2_combat_breath_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_target_delete BEFORE DELETE ON s2_combat_breath_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_die_update BEFORE UPDATE ON s2_combat_breath_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_die_delete BEFORE DELETE ON s2_combat_breath_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_check_update BEFORE UPDATE ON s2_combat_breath_check FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_check_delete BEFORE DELETE ON s2_combat_breath_check FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_packet_update BEFORE UPDATE ON s2_combat_breath_packet FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_packet_delete BEFORE DELETE ON s2_combat_breath_packet FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_defense_update BEFORE UPDATE ON s2_combat_breath_defense FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_defense_delete BEFORE DELETE ON s2_combat_breath_defense FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_concentration_update BEFORE UPDATE ON s2_combat_breath_concentration FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_breath_concentration_delete BEFORE DELETE ON s2_combat_breath_concentration FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_rest_input_update BEFORE UPDATE ON s2_combat_monster_rest_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_rest_input_delete BEFORE DELETE ON s2_combat_monster_rest_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_rest_update BEFORE UPDATE ON s2_combat_monster_rest FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

CREATE TRIGGER s2_combat_monster_rest_delete BEFORE DELETE ON s2_combat_monster_rest FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 monster and save-damage fact';
END$$

DELIMITER ;

-- CHECKSUM-SCOPE-END
INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (40, 'V040__schema2-monster-breath-runtime.sql', 'a9fa6c6c3afa348cccdf01c1786103ff94cd7ac8b663389242bd4b49555d6abf');
