-- Typed INTERNAL casts, ongoing spell instances, concentration and character-owned slot costs.
-- Existing approved migrations remain immutable. No business facts are synthesized.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v036_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v036_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 35
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version = 24 AND CAST(script_name AS BINARY) = _binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version = 25 AND CAST(script_name AS BINARY) = _binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version = 26 AND CAST(script_name AS BINARY) = _binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY) = _binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version = 27 AND CAST(script_name AS BINARY) = _binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY) = _binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version = 28 AND CAST(script_name AS BINARY) = _binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a') OR (schema_version = 29 AND CAST(script_name AS BINARY) = _binary'V029__schema2-character-check-history.sql' AND CAST(script_sha256 AS BINARY) = _binary'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b') OR (schema_version = 30 AND CAST(script_name AS BINARY) = _binary'V030__schema2-equipment-health-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3') OR (schema_version = 31 AND CAST(script_name AS BINARY) = _binary'V031__schema2-equipment-health-history.sql' AND CAST(script_sha256 AS BINARY) = _binary'1b9f8959ad5c3d6e4b417d2672a7a06cf8df572e9059e4addfde01856e57b79e') OR (schema_version = 32 AND CAST(script_name AS BINARY) = _binary'V032__schema2-combat-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1bc7521e953b640b42c869f734961d8e9153e2036788468c9ea13db39be5cae') OR (schema_version = 33 AND CAST(script_name AS BINARY) = _binary'V033__schema2-combat-runtime.sql' AND CAST(script_sha256 AS BINARY) = _binary'df5ddd066e6694d836daf2891ead4271e5ae6e2ed1cb2bf13a009d75d72c83e7') OR (schema_version = 34 AND CAST(script_name AS BINARY) = _binary'V034__schema2-combat-time-ground.sql' AND CAST(script_sha256 AS BINARY) = _binary'b9872fe91f87cf0d0c8cb2c16e955c6d0e4e566aed52d56071afa89217affe1f') OR (schema_version = 35 AND CAST(script_name AS BINARY) = _binary'V035__schema2-spell-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'80765ab1aa82342d0d1d6ed54f38df6caa94b539ddf19d2ca3f4872fd5ecfdaf')) = 35
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('s2_combat_cast_command', 's2_combat_cast_command_target', 's2_combat_ongoing_effect', 's2_combat_ongoing_target', 's2_combat_cast', 's2_combat_cast_target', 's2_combat_cast_die', 's2_combat_saving_throw')) = 0
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND ((table_name = 's2_combat_state' AND column_name = 'ongoing_effect_count') OR (table_name = 's2_combat_flow' AND column_name IN ('cast_count', 'save_count')) OR (table_name = 's2_combat_attack' AND column_name IN ('blessing_effect_key', 'blessing_d4')))) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v036_preflight;

ALTER TABLE s2_character_event DROP CHECK ck_s2_character_command_0;
ALTER TABLE s2_character_event ADD CONSTRAINT ck_s2_character_command_0 CHECK (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED', _binary'RESOURCE_SPENT', _binary'RESOURCES_RECOVERED', _binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED', _binary'CHECK_RESOLVED', _binary'ITEM_ACQUIRED', _binary'ITEM_REMOVED', _binary'ITEM_EQUIPPED', _binary'ITEM_UNEQUIPPED', _binary'HEALTH_EFFECT_RESOLVED', _binary'DEATH_SAVE_RESOLVED', _binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_DEATH_SAVE_RESOLVED', _binary'COMBAT_TIME_ADVANCED', _binary'COMBAT_RESOURCE_SPENT', _binary'COMBAT_HEALING_APPLIED'));
ALTER TABLE s2_character_event DROP CHECK ck_s2_character_command_3;
ALTER TABLE s2_character_event ADD CONSTRAINT ck_s2_character_command_3 CHECK (((event_kind IN (_binary'RESOURCE_SPENT', _binary'COMBAT_RESOURCE_SPENT') AND resource_module IS NOT NULL AND resource_type IS NOT NULL AND resource_type = _binary'character.resource' AND resource_key IS NOT NULL AND resource_amount BETWEEN 1 AND 1000000) OR (event_kind NOT IN (_binary'RESOURCE_SPENT', _binary'COMBAT_RESOURCE_SPENT') AND resource_module IS NULL AND resource_type IS NULL AND resource_key IS NULL AND resource_amount = 0)));
ALTER TABLE s2_combat_event DROP CHECK ck_c33_2;
ALTER TABLE s2_combat_event ADD CONSTRAINT ck_c33_2 CHECK (event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(request_digest) = 64 AND NOT REGEXP_LIKE(CONVERT(request_digest USING latin1), '[^0-9a-f]') AND command_kind IN (_binary'START', _binary'END_TURN', _binary'DASH', _binary'DISENGAGE', _binary'DODGE', _binary'BEGIN_ATTACK', _binary'ATTACK', _binary'FORFEIT_ATTACKS', _binary'MOVE', _binary'PASS_REACTION', _binary'DECLARE_OPPORTUNITY_ATTACK', _binary'CONTINUE_MOVEMENT', _binary'END_COMBAT', _binary'STAND_UP', _binary'ADVANCE_WINDOW', _binary'CAST', _binary'END_CONCENTRATION') AND ((command_kind = _binary'START' AND event_sequence = 1 AND actor_id IS NULL) OR (command_kind <> _binary'START' AND event_sequence > 1 AND actor_id IS NOT NULL)) AND ((command_kind = _binary'ATTACK' AND target_id IS NOT NULL) OR (command_kind <> _binary'ATTACK' AND target_id IS NULL)) AND ((command_kind IN (_binary'ATTACK', _binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NOT NULL) OR (command_kind NOT IN (_binary'ATTACK', _binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NULL))  AND ((command_kind IN (_binary'PASS_REACTION', _binary'DECLARE_OPPORTUNITY_ATTACK', _binary'ADVANCE_WINDOW', _binary'CONTINUE_MOVEMENT') AND flow_key IS NOT NULL AND leaf_key IS NOT NULL AND window_key IS NOT NULL AND ((command_kind = _binary'CONTINUE_MOVEMENT' AND opportunity_key IS NULL) OR (command_kind <> _binary'CONTINUE_MOVEMENT' AND opportunity_key IS NOT NULL))) OR (command_kind NOT IN (_binary'PASS_REACTION', _binary'DECLARE_OPPORTUNITY_ATTACK', _binary'ADVANCE_WINDOW', _binary'CONTINUE_MOVEMENT') AND flow_key IS NULL AND leaf_key IS NULL AND window_key IS NULL AND opportunity_key IS NULL)) AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(operation_id) = 16 AND (ORD(SUBSTRING(operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(operation_id, 9, 1)) & 192) = 128 AND (actor_id IS NULL OR (OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128)) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (item_id IS NULL OR (OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)) AND (flow_key IS NULL OR (OCTET_LENGTH(flow_key) = 16 AND (ORD(SUBSTRING(flow_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(flow_key, 9, 1)) & 192) = 128)) AND (leaf_key IS NULL OR (OCTET_LENGTH(leaf_key) = 16 AND (ORD(SUBSTRING(leaf_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(leaf_key, 9, 1)) & 192) = 128)) AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)));
-- frame_status is a finite token; only identity columns carry UUID checks.
ALTER TABLE s2_combat_frame DROP CHECK ck_c33_14;
ALTER TABLE s2_combat_frame ADD CONSTRAINT ck_c33_14 CHECK (frame_index BETWEEN 0 AND 1023 AND frame_kind IN (_binary'MOVEMENT', _binary'ATTACK', _binary'REACTION_SELECTION', _binary'CAST', _binary'SAVING_THROW') AND invocation IN (_binary'ROOT', _binary'REACTION') AND frame_status IN (_binary'RUNNABLE', _binary'WAITING_WINDOW', _binary'WAITING_CHILD', _binary'PENDING', _binary'DECIDED', _binary'TERMINAL') AND stage IN (_binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'MOVEMENT_CONTINUE', _binary'ATTACK_RESOLVED', _binary'MOVEMENT_COMPLETED', _binary'MOVEMENT_STOPPED', _binary'CAST_RESOLVED', _binary'SAVING_THROW_RESOLVED') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(owner_key) = 16 AND (ORD(SUBSTRING(owner_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(owner_key, 9, 1)) & 192) = 128 AND (parent_frame_key IS NULL OR (OCTET_LENGTH(parent_frame_key) = 16 AND (ORD(SUBSTRING(parent_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(parent_frame_key, 9, 1)) & 192) = 128)) AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)));
ALTER TABLE s2_combat_state ADD ongoing_effect_count INT NOT NULL DEFAULT 0, ADD CONSTRAINT ck_s236_effect_count CHECK (ongoing_effect_count BETWEEN 0 AND 128);
ALTER TABLE s2_combat_flow ADD cast_count INT NOT NULL DEFAULT 0, ADD save_count INT NOT NULL DEFAULT 0, ADD CONSTRAINT ck_s236_flow_count CHECK (cast_count BETWEEN 0 AND 1024 AND save_count BETWEEN 0 AND 1024);
ALTER TABLE s2_combat_attack ADD blessing_effect_key VARBINARY(16) NULL, ADD blessing_d4 INT NULL,
    ADD CONSTRAINT ck_s2_attack_blessing CHECK ((blessing_effect_key IS NULL AND blessing_d4 IS NULL) OR (blessing_effect_key IS NOT NULL AND blessing_d4 IS NOT NULL AND blessing_d4 BETWEEN 1 AND 4 AND OCTET_LENGTH(blessing_effect_key) = 16 AND (ORD(SUBSTRING(blessing_effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(blessing_effect_key, 9, 1)) & 192) = 128));

CREATE TABLE s2_combat_cast_command (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    command_index INT NOT NULL,
    material_item_id VARBINARY(16) NULL,
    target_count INT NOT NULL,
    casting_module VARBINARY(64) NOT NULL,
    casting_type VARBINARY(40) NOT NULL,
    casting_key VARBINARY(128) NOT NULL,
    spell_module VARBINARY(64) NOT NULL,
    spell_type VARBINARY(40) NOT NULL,
    spell_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, command_index),
    CONSTRAINT fk_s236_1 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_1 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND command_index = 0 AND target_count BETWEEN 1 AND 16 AND (material_item_id IS NULL OR (OCTET_LENGTH(material_item_id) = 16 AND (ORD(SUBSTRING(material_item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(material_item_id, 9, 1)) & 192) = 128)) AND casting_type = _binary'character.spellcasting' AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128 AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_cast_command_target (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    target_index INT NOT NULL,
    target_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, target_index),
    UNIQUE KEY uq_s236_2_0 (combat_id, event_sequence, target_id),
    CONSTRAINT fk_s236_2 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_2 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND target_index BETWEEN 0 AND 15 AND OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_ongoing_effect (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    effect_index INT NOT NULL,
    effect_key VARBINARY(16) NOT NULL,
    caster_id VARBINARY(16) NOT NULL,
    started_at_second BIGINT NOT NULL,
    expires_at_second BIGINT NOT NULL,
    effect_status VARBINARY(8) NOT NULL,
    end_reason VARBINARY(128) NULL,
    target_count INT NOT NULL,
    spell_module VARBINARY(64) NOT NULL,
    spell_type VARBINARY(40) NOT NULL,
    spell_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, effect_index),
    UNIQUE KEY uq_s236_3_0 (combat_id, event_sequence, phase, effect_key),
    CONSTRAINT fk_s236_3 FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_3 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND effect_index BETWEEN 0 AND 127 AND target_count BETWEEN 1 AND 16 AND OCTET_LENGTH(effect_key) = 16 AND (ORD(SUBSTRING(effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(effect_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(caster_id) = 16 AND (ORD(SUBSTRING(caster_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(caster_id, 9, 1)) & 192) = 128 AND started_at_second BETWEEN 0 AND 60000000059 AND expires_at_second > started_at_second AND expires_at_second <= 60000000059 AND ((effect_status = _binary'ACTIVE' AND end_reason IS NULL) OR (effect_status = _binary'ENDED' AND end_reason IS NOT NULL AND OCTET_LENGTH(end_reason) BETWEEN 1 AND 128 AND NOT REGEXP_LIKE(CONVERT(end_reason USING latin1), '[^A-Z0-9_]', 'c'))) AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_ongoing_target (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    effect_index INT NOT NULL,
    target_index INT NOT NULL,
    target_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, effect_index, target_index),
    UNIQUE KEY uq_s236_4_0 (combat_id, event_sequence, phase, effect_index, target_id),
    CONSTRAINT fk_s236_4 FOREIGN KEY (combat_id, event_sequence, phase, effect_index) REFERENCES s2_combat_ongoing_effect (combat_id, event_sequence, phase, effect_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_4 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND target_index BETWEEN 0 AND 15 AND OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128 AND phase IN (_binary'BEFORE', _binary'AFTER'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_cast (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    cast_index INT NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    slot_level INT NOT NULL,
    material_item_id VARBINARY(16) NULL,
    healing_modifier INT NOT NULL,
    requested_healing INT NOT NULL,
    applied_healing INT NOT NULL,
    effect_key VARBINARY(16) NULL,
    target_count INT NOT NULL,
    healing_die_count INT NOT NULL,
    casting_module VARBINARY(64) NOT NULL,
    casting_type VARBINARY(40) NOT NULL,
    casting_key VARBINARY(128) NOT NULL,
    spell_module VARBINARY(64) NOT NULL,
    spell_type VARBINARY(40) NOT NULL,
    spell_key VARBINARY(128) NOT NULL,
    slot_module VARBINARY(64) NOT NULL,
    slot_type VARBINARY(40) NOT NULL,
    slot_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, cast_index),
    UNIQUE KEY uq_s236_5_0 (combat_id, event_sequence, phase, flow_index, frame_key),
    CONSTRAINT fk_s236_5 FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_5 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND cast_index BETWEEN 0 AND 1023 AND target_count BETWEEN 1 AND 16 AND healing_die_count BETWEEN 0 AND 20 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128 AND slot_level BETWEEN 1 AND 9 AND (material_item_id IS NULL OR (OCTET_LENGTH(material_item_id) = 16 AND (ORD(SUBSTRING(material_item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(material_item_id, 9, 1)) & 192) = 128)) AND (effect_key IS NULL OR (OCTET_LENGTH(effect_key) = 16 AND (ORD(SUBSTRING(effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(effect_key, 9, 1)) & 192) = 128)) AND healing_modifier BETWEEN -5 AND 10 AND requested_healing BETWEEN 0 AND 65535 AND applied_healing BETWEEN 0 AND requested_healing AND casting_type = _binary'character.spellcasting' AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128 AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128 AND slot_type = _binary'character.resource' AND OCTET_LENGTH(slot_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(slot_key) BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND flow_index BETWEEN 0 AND 127)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_cast_target (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    cast_index INT NOT NULL,
    target_index INT NOT NULL,
    target_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, cast_index, target_index),
    UNIQUE KEY uq_s236_6_0 (combat_id, event_sequence, phase, flow_index, cast_index, target_id),
    CONSTRAINT fk_s236_6 FOREIGN KEY (combat_id, event_sequence, phase, flow_index, cast_index) REFERENCES s2_combat_cast (combat_id, event_sequence, phase, flow_index, cast_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_6 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND target_index BETWEEN 0 AND 15 AND OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND flow_index BETWEEN 0 AND 127)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_cast_die (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    cast_index INT NOT NULL,
    die_index INT NOT NULL,
    die_value INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, cast_index, die_index),
    CONSTRAINT fk_s236_7 FOREIGN KEY (combat_id, event_sequence, phase, flow_index, cast_index) REFERENCES s2_combat_cast (combat_id, event_sequence, phase, flow_index, cast_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_7 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND die_index BETWEEN 0 AND 19 AND die_value BETWEEN 1 AND 12 AND phase IN (_binary'BEFORE', _binary'AFTER') AND flow_index BETWEEN 0 AND 127)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_saving_throw (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    save_index INT NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    subject_id VARBINARY(16) NOT NULL,
    save_kind VARBINARY(128) NOT NULL,
    ability_key VARBINARY(32) NULL,
    d20 INT NOT NULL,
    ability_modifier INT NOT NULL,
    proficiency_bonus INT NOT NULL,
    blessing_effect_key VARBINARY(16) NULL,
    blessing_d4 INT NULL,
    difficulty_class INT NOT NULL,
    total INT NOT NULL,
    success TINYINT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, save_index),
    CONSTRAINT fk_s236_8 FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s236_8 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND save_index BETWEEN 0 AND 1023 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(save_kind) BETWEEN 1 AND 128 AND NOT REGEXP_LIKE(CONVERT(save_kind USING latin1), '[^A-Z0-9_]', 'c') AND (ability_key IS NULL OR ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) AND d20 BETWEEN 1 AND 20 AND ability_modifier BETWEEN -5 AND 10 AND proficiency_bonus BETWEEN 0 AND 6 AND ((blessing_effect_key IS NULL AND blessing_d4 IS NULL) OR (blessing_effect_key IS NOT NULL AND blessing_d4 IS NOT NULL AND blessing_d4 BETWEEN 1 AND 4 AND OCTET_LENGTH(blessing_effect_key) = 16 AND (ORD(SUBSTRING(blessing_effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(blessing_effect_key, 9, 1)) & 192) = 128)) AND difficulty_class BETWEEN 0 AND 32767 AND total = d20 + ability_modifier + proficiency_bonus + IFNULL(blessing_d4, 0) AND success = IF(total >= difficulty_class, 1, 0) AND phase IN (_binary'BEFORE', _binary'AFTER') AND flow_index BETWEEN 0 AND 127)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DROP TRIGGER s2_character_combat_input_insert;
DROP TRIGGER s2_health_event_insert;
DELIMITER $$
CREATE TRIGGER s2_character_combat_input_insert BEFORE INSERT ON s2_character_combat_input FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE operation_id = NEW.operation_id AND character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND ((event_kind IN (_binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_HEALING_APPLIED') AND NEW.seconds_advanced = 0) OR (event_kind IN (_binary'COMBAT_DEATH_SAVE_RESOLVED', _binary'COMBAT_RESOURCE_SPENT') AND NEW.seconds_advanced = 0 AND NEW.damage_amount = 0 AND NEW.critical = 0) OR (event_kind = _binary'COMBAT_TIME_ADVANCED' AND NEW.seconds_advanced = 6 AND NEW.damage_amount = 0 AND NEW.critical = 0))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 combat character cause mismatch';
    END IF;
END$$
CREATE TRIGGER s2_health_event_insert BEFORE INSERT ON s2_character_health_event FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND ((event_kind = _binary'HEALTH_EFFECT_RESOLVED' AND NEW.effect_module IS NOT NULL AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL) OR (event_kind = _binary'DEATH_SAVE_RESOLVED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NOT NULL) OR (event_kind = _binary'COMBAT_DEATH_SAVE_RESOLVED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count BETWEEN 0 AND 1 AND NEW.death_d20 IS NOT NULL) OR (event_kind IN (_binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_TIME_ADVANCED') AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL) OR (event_kind = _binary'COMBAT_HEALING_APPLIED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 health event mismatch';
    END IF;
END$$
CREATE TRIGGER s2_combat_cast_command_update BEFORE UPDATE ON s2_combat_cast_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_command_delete BEFORE DELETE ON s2_combat_cast_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_command_target_update BEFORE UPDATE ON s2_combat_cast_command_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_command_target_delete BEFORE DELETE ON s2_combat_cast_command_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_ongoing_effect_update BEFORE UPDATE ON s2_combat_ongoing_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_ongoing_effect_delete BEFORE DELETE ON s2_combat_ongoing_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_ongoing_target_update BEFORE UPDATE ON s2_combat_ongoing_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_ongoing_target_delete BEFORE DELETE ON s2_combat_ongoing_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_update BEFORE UPDATE ON s2_combat_cast FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_delete BEFORE DELETE ON s2_combat_cast FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_target_update BEFORE UPDATE ON s2_combat_cast_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_target_delete BEFORE DELETE ON s2_combat_cast_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_die_update BEFORE UPDATE ON s2_combat_cast_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_die_delete BEFORE DELETE ON s2_combat_cast_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_saving_throw_update BEFORE UPDATE ON s2_combat_saving_throw FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_saving_throw_delete BEFORE DELETE ON s2_combat_saving_throw FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 spell execution fact';
END$$
CREATE TRIGGER s2_combat_cast_command_insert BEFORE INSERT ON s2_combat_cast_command FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_combat_event WHERE combat_id = NEW.combat_id AND event_sequence = NEW.event_sequence AND command_kind = _binary'CAST') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 cast command mismatch';
    END IF;
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END
INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (36, 'V036__schema2-spell-runtime.sql', 'd92de4fe6fd7b76abd3a8b4b63dce7ab98bb38352d714b9a34a61498b1f7b689');
