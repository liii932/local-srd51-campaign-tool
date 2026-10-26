-- Typed shared combat, movement continuation, attack audits and causal character health facts.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v033_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v033_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 32
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version = 24 AND CAST(script_name AS BINARY) = _binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version = 25 AND CAST(script_name AS BINARY) = _binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version = 26 AND CAST(script_name AS BINARY) = _binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY) = _binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version = 27 AND CAST(script_name AS BINARY) = _binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY) = _binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version = 28 AND CAST(script_name AS BINARY) = _binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a') OR (schema_version = 29 AND CAST(script_name AS BINARY) = _binary'V029__schema2-character-check-history.sql' AND CAST(script_sha256 AS BINARY) = _binary'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b') OR (schema_version = 30 AND CAST(script_name AS BINARY) = _binary'V030__schema2-equipment-health-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3') OR (schema_version = 31 AND CAST(script_name AS BINARY) = _binary'V031__schema2-equipment-health-history.sql' AND CAST(script_sha256 AS BINARY) = _binary'1b9f8959ad5c3d6e4b417d2672a7a06cf8df572e9059e4addfde01856e57b79e') OR (schema_version = 32 AND CAST(script_name AS BINARY) = _binary'V032__schema2-combat-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1bc7521e953b640b42c869f734961d8e9153e2036788468c9ea13db39be5cae')) = 32
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('s2_combat_runtime', 's2_combat_member', 's2_combat_event', 's2_combat_dependency', 's2_combat_character_result', 's2_combat_command_point', 's2_combat_command_deployment', 's2_combat_command_tie', 's2_combat_roll', 's2_combat_fact', 's2_combat_state', 's2_combat_actor', 's2_combat_initiative', 's2_combat_flow', 's2_combat_frame', 's2_combat_window', 's2_combat_opportunity', 's2_combat_opportunity_item', 's2_combat_movement', 's2_combat_movement_point', 's2_combat_attack', 's2_combat_attack_die', 's2_character_combat_input')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v033_preflight;

CREATE TABLE s2_combat_runtime (
    combat_id VARBINARY(16) NOT NULL,
    campaign_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id),
    CONSTRAINT ck_c33_0 CHECK (combat_id <> campaign_id AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(campaign_id) = 16 AND (ORD(SUBSTRING(campaign_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(campaign_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_member (
    combat_id VARBINARY(16) NOT NULL,
    member_index INT NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    run_id VARBINARY(16) NOT NULL,
    snapshot_id VARBINARY(16) NOT NULL,
    preparation_operation_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, member_index),
    UNIQUE KEY uq_c33_1_0 (combat_id, character_id),
    CONSTRAINT fk_c33_1_0 FOREIGN KEY (combat_id) REFERENCES s2_combat_runtime (combat_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_c33_1_1 FOREIGN KEY (character_id) REFERENCES s2_character_runtime (character_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_c33_1_2 FOREIGN KEY (run_id, snapshot_id) REFERENCES s2_runtime_snapshot (run_id, snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_c33_1_3 FOREIGN KEY (preparation_operation_id) REFERENCES s2_initial_plan (operation_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_1 CHECK (member_index BETWEEN 0 AND 15 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(run_id) = 16 AND (ORD(SUBSTRING(run_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(run_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(snapshot_id) = 16 AND (ORD(SUBSTRING(snapshot_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(snapshot_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(preparation_operation_id) = 16 AND (ORD(SUBSTRING(preparation_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(preparation_operation_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_event (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    operation_id VARBINARY(16) NOT NULL,
    request_digest VARBINARY(64) NOT NULL,
    command_kind VARBINARY(32) NOT NULL,
    actor_id VARBINARY(16) NULL,
    target_id VARBINARY(16) NULL,
    item_id VARBINARY(16) NULL,
    flow_key VARBINARY(16) NULL,
    leaf_key VARBINARY(16) NULL,
    window_key VARBINARY(16) NULL,
    opportunity_key VARBINARY(16) NULL,
    PRIMARY KEY (combat_id, event_sequence),
    UNIQUE KEY uq_c33_2_0 (operation_id),
    CONSTRAINT fk_c33_2_0 FOREIGN KEY (combat_id) REFERENCES s2_combat_runtime (combat_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_2 CHECK (event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(request_digest) = 64 AND NOT REGEXP_LIKE(CONVERT(request_digest USING latin1), '[^0-9a-f]') AND command_kind IN (_binary'START', _binary'END_TURN', _binary'DASH', _binary'DISENGAGE', _binary'DODGE', _binary'BEGIN_ATTACK', _binary'ATTACK', _binary'FORFEIT_ATTACKS', _binary'MOVE', _binary'PASS_REACTION', _binary'DECLARE_OPPORTUNITY_ATTACK', _binary'CONTINUE_MOVEMENT', _binary'END_COMBAT') AND ((command_kind = _binary'START' AND event_sequence = 1 AND actor_id IS NULL) OR (command_kind <> _binary'START' AND event_sequence > 1 AND actor_id IS NOT NULL)) AND ((command_kind = _binary'ATTACK' AND target_id IS NOT NULL) OR (command_kind <> _binary'ATTACK' AND target_id IS NULL)) AND ((command_kind IN (_binary'ATTACK', _binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NOT NULL) OR (command_kind NOT IN (_binary'ATTACK', _binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NULL)) AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(operation_id) = 16 AND (ORD(SUBSTRING(operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(operation_id, 9, 1)) & 192) = 128 AND (actor_id IS NULL OR (OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128)) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (item_id IS NULL OR (OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)) AND (flow_key IS NULL OR (OCTET_LENGTH(flow_key) = 16 AND (ORD(SUBSTRING(flow_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(flow_key, 9, 1)) & 192) = 128)) AND (leaf_key IS NULL OR (OCTET_LENGTH(leaf_key) = 16 AND (ORD(SUBSTRING(leaf_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(leaf_key, 9, 1)) & 192) = 128)) AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_dependency (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    dependency_index INT NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    character_version BIGINT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, dependency_index),
    UNIQUE KEY uq_c33_3_0 (combat_id, event_sequence, character_id),
    CONSTRAINT fk_c33_3_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_3 CHECK (dependency_index BETWEEN 0 AND 15 AND character_version BETWEEN 0 AND 127 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_character_result (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    result_index INT NOT NULL,
    character_operation_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, result_index),
    UNIQUE KEY uq_c33_4_0 (character_operation_id),
    CONSTRAINT fk_c33_4_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_c33_4_1 FOREIGN KEY (character_operation_id) REFERENCES s2_character_event (operation_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_4 CHECK (result_index BETWEEN 0 AND 15 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_operation_id) = 16 AND (ORD(SUBSTRING(character_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_operation_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_command_point (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    point_index INT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, point_index),
    CONSTRAINT fk_c33_5_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_5 CHECK (point_index BETWEEN 0 AND 63 AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_command_deployment (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    deployment_index INT NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    side INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, deployment_index),
    UNIQUE KEY uq_c33_6_0 (combat_id, event_sequence, character_id),
    CONSTRAINT fk_c33_6_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_6 CHECK (deployment_index BETWEEN 0 AND 15 AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND side BETWEEN 0 AND 15 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_command_tie (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    tie_index INT NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, tie_index),
    UNIQUE KEY uq_c33_7_0 (combat_id, event_sequence, character_id),
    CONSTRAINT fk_c33_7_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_7 CHECK (tie_index BETWEEN 0 AND 15 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_roll (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    roll_index INT NOT NULL,
    purpose VARBINARY(128) NOT NULL,
    subject_id VARBINARY(16) NOT NULL,
    sides INT NOT NULL,
    die_value INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, roll_index),
    CONSTRAINT fk_c33_8_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_8 CHECK (roll_index BETWEEN 0 AND 511 AND sides BETWEEN 2 AND 20 AND die_value BETWEEN 1 AND sides AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_fact (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    fact_index INT NOT NULL,
    fact_kind VARBINARY(128) NOT NULL,
    subject_id VARBINARY(16) NULL,
    reference_key VARBINARY(16) NULL,
    reason VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, fact_index),
    CONSTRAINT fk_c33_9_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_9 CHECK (fact_index BETWEEN 0 AND 511 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND (subject_id IS NULL OR (OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128)) AND (reference_key IS NULL OR (OCTET_LENGTH(reference_key) = 16 AND (ORD(SUBSTRING(reference_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(reference_key, 9, 1)) & 192) = 128)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_state (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    state_index INT NOT NULL,
    rule_module VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    combat_status VARBINARY(8) NOT NULL,
    round_number INT NOT NULL,
    turn_number BIGINT NOT NULL,
    active_ordinal INT NOT NULL,
    row_version BIGINT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase),
    CONSTRAINT fk_c33_10_0 FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_10 CHECK (state_index = 0 AND rule_type = _binary'adventure.combat' AND combat_status IN (_binary'ACTIVE', _binary'ENDED') AND round_number >= 1 AND turn_number >= 1 AND active_ordinal BETWEEN 0 AND 15 AND ((phase = _binary'AFTER' AND row_version = event_sequence - 1) OR (phase = _binary'BEFORE' AND event_sequence > 1 AND row_version = event_sequence - 2)) AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_actor (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    actor_index INT NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    side INT NOT NULL,
    action_available TINYINT NOT NULL,
    reaction_available TINYINT NOT NULL,
    movement_spent INT NOT NULL,
    movement_bonus INT NOT NULL,
    disengaged TINYINT NOT NULL,
    dodging TINYINT NOT NULL,
    sequence_key VARBINARY(16) NULL,
    attack_limit INT NULL,
    performed INT NULL,
    forfeited INT NULL,
    sequence_closed TINYINT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, actor_index),
    UNIQUE KEY uq_c33_11_0 (combat_id, event_sequence, phase, character_id),
    CONSTRAINT fk_c33_11_0 FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_11 CHECK (actor_index BETWEEN 0 AND 15 AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND side BETWEEN 0 AND 15 AND movement_spent BETWEEN 0 AND 1000000 AND movement_bonus BETWEEN 0 AND 1000000 AND ((sequence_key IS NULL AND attack_limit IS NULL AND performed IS NULL AND forfeited IS NULL AND sequence_closed IS NULL) OR (sequence_key IS NOT NULL AND attack_limit BETWEEN 1 AND 20 AND performed BETWEEN 0 AND attack_limit AND forfeited BETWEEN 0 AND attack_limit AND performed + forfeited <= attack_limit AND sequence_closed = IF(performed + forfeited = attack_limit, 1, 0))) AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128 AND action_available IN (0, 1) AND reaction_available IN (0, 1) AND disengaged IN (0, 1) AND dodging IN (0, 1) AND (sequence_key IS NULL OR (OCTET_LENGTH(sequence_key) = 16 AND (ORD(SUBSTRING(sequence_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(sequence_key, 9, 1)) & 192) = 128)) AND (sequence_closed IS NULL OR sequence_closed IN (0, 1)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_initiative (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    initiative_index INT NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    d20 INT NOT NULL,
    dexterity_modifier INT NOT NULL,
    total INT NOT NULL,
    ordinal INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, initiative_index),
    UNIQUE KEY uq_c33_12_0 (combat_id, event_sequence, phase, character_id),
    CONSTRAINT fk_c33_12_0 FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_12 CHECK (initiative_index BETWEEN 0 AND 15 AND ordinal = initiative_index AND d20 BETWEEN 1 AND 20 AND dexterity_modifier BETWEEN -5 AND 10 AND total = d20 + dexterity_modifier AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_flow (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    flow_key VARBINARY(16) NOT NULL,
    root_frame_key VARBINARY(16) NOT NULL,
    leaf_frame_key VARBINARY(16) NULL,
    flow_status VARBINARY(12) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index),
    UNIQUE KEY uq_c33_13_0 (combat_id, event_sequence, phase, flow_key),
    CONSTRAINT fk_c33_13_0 FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_13 CHECK (flow_index BETWEEN 0 AND 127 AND flow_status IN (_binary'ACTIVE', _binary'COMPLETED') AND ((flow_status = _binary'ACTIVE' AND leaf_frame_key IS NOT NULL) OR (flow_status = _binary'COMPLETED' AND leaf_frame_key IS NULL)) AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(flow_key) = 16 AND (ORD(SUBSTRING(flow_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(flow_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(root_frame_key) = 16 AND (ORD(SUBSTRING(root_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(root_frame_key, 9, 1)) & 192) = 128 AND (leaf_frame_key IS NULL OR (OCTET_LENGTH(leaf_frame_key) = 16 AND (ORD(SUBSTRING(leaf_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(leaf_frame_key, 9, 1)) & 192) = 128)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_frame (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    frame_index INT NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    parent_frame_key VARBINARY(16) NULL,
    frame_kind VARBINARY(20) NOT NULL,
    owner_key VARBINARY(16) NOT NULL,
    invocation VARBINARY(8) NOT NULL,
    frame_status VARBINARY(16) NOT NULL,
    stage VARBINARY(40) NOT NULL,
    window_key VARBINARY(16) NULL,
    opportunity_key VARBINARY(16) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, frame_index),
    UNIQUE KEY uq_c33_14_0 (combat_id, event_sequence, phase, flow_index, frame_key),
    CONSTRAINT fk_c33_14_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_14 CHECK (frame_index BETWEEN 0 AND 1023 AND frame_kind IN (_binary'MOVEMENT', _binary'ATTACK', _binary'REACTION_SELECTION') AND invocation IN (_binary'ROOT', _binary'REACTION') AND frame_status IN (_binary'RUNNABLE', _binary'WAITING_WINDOW', _binary'WAITING_CHILD', _binary'PENDING', _binary'DECIDED', _binary'TERMINAL') AND stage IN (_binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'MOVEMENT_CONTINUE', _binary'ATTACK_RESOLVED', _binary'MOVEMENT_COMPLETED', _binary'MOVEMENT_STOPPED') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND (parent_frame_key IS NULL OR (OCTET_LENGTH(parent_frame_key) = 16 AND (ORD(SUBSTRING(parent_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(parent_frame_key, 9, 1)) & 192) = 128)) AND OCTET_LENGTH(owner_key) = 16 AND (ORD(SUBSTRING(owner_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(owner_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_status) = 16 AND (ORD(SUBSTRING(frame_status, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_status, 9, 1)) & 192) = 128 AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_window (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    window_index INT NOT NULL,
    window_key VARBINARY(16) NOT NULL,
    owner_frame_key VARBINARY(16) NOT NULL,
    checkpoint_ordinal INT NOT NULL,
    boundary_index INT NOT NULL,
    window_status VARBINARY(8) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, window_index),
    UNIQUE KEY uq_c33_15_0 (combat_id, event_sequence, phase, flow_index, window_key),
    CONSTRAINT fk_c33_15_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_15 CHECK (window_index BETWEEN 0 AND 1023 AND checkpoint_ordinal BETWEEN 0 AND 63 AND boundary_index BETWEEN 0 AND 63 AND window_status IN (_binary'OPEN', _binary'CLOSED', _binary'CONSUMED') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(owner_frame_key) = 16 AND (ORD(SUBSTRING(owner_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(owner_frame_key, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_opportunity (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    window_index INT NOT NULL,
    opportunity_index INT NOT NULL,
    opportunity_key VARBINARY(16) NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    priority INT NOT NULL,
    opportunity_status VARBINARY(12) NOT NULL,
    selection_frame_key VARBINARY(16) NULL,
    attack_frame_key VARBINARY(16) NULL,
    reason VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index),
    UNIQUE KEY uq_c33_16_0 (combat_id, event_sequence, phase, flow_index, window_index, actor_id),
    UNIQUE KEY uq_c33_16_1 (combat_id, event_sequence, phase, flow_index, window_index, opportunity_key),
    CONSTRAINT fk_c33_16_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index, window_index) REFERENCES s2_combat_window (combat_id, event_sequence, phase, flow_index, window_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_16 CHECK (opportunity_index BETWEEN 0 AND 127 AND priority = opportunity_index AND opportunity_status IN (_binary'PENDING', _binary'PASSED', _binary'COMPLETED', _binary'INELIGIBLE') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128 AND (selection_frame_key IS NULL OR (OCTET_LENGTH(selection_frame_key) = 16 AND (ORD(SUBSTRING(selection_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(selection_frame_key, 9, 1)) & 192) = 128)) AND (attack_frame_key IS NULL OR (OCTET_LENGTH(attack_frame_key) = 16 AND (ORD(SUBSTRING(attack_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(attack_frame_key, 9, 1)) & 192) = 128)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_opportunity_item (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    window_index INT NOT NULL,
    opportunity_index INT NOT NULL,
    item_index INT NOT NULL,
    item_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index, item_index),
    UNIQUE KEY uq_c33_17_0 (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index, item_id),
    CONSTRAINT fk_c33_17_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index) REFERENCES s2_combat_opportunity (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_17 CHECK (item_index BETWEEN 0 AND 31 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_movement (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    movement_index INT NOT NULL,
    movement_key VARBINARY(16) NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    origin_x INT NOT NULL,
    origin_y INT NOT NULL,
    cursor INT NOT NULL,
    spent INT NOT NULL,
    movement_status VARBINARY(12) NOT NULL,
    stop_reason VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index),
    CONSTRAINT fk_c33_18_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_18 CHECK (movement_index = 0 AND origin_x BETWEEN -10000 AND 10000 AND origin_y BETWEEN -10000 AND 10000 AND cursor BETWEEN 0 AND 64 AND spent BETWEEN 0 AND 1000000 AND movement_status IN (_binary'PENDING', _binary'COMPLETED', _binary'STOPPED') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(movement_key) = 16 AND (ORD(SUBSTRING(movement_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(movement_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_movement_point (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    point_index INT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, point_index),
    CONSTRAINT fk_c33_19_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_movement (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_19 CHECK (point_index BETWEEN 0 AND 63 AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_attack (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    attack_index INT NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    target_id VARBINARY(16) NOT NULL,
    item_id VARBINARY(16) NOT NULL,
    sequence_key VARBINARY(16) NULL,
    attack_ordinal INT NOT NULL,
    opportunity_attack TINYINT NOT NULL,
    first_d20 INT NOT NULL,
    second_d20 INT NULL,
    selected_d20 INT NOT NULL,
    ability_modifier INT NOT NULL,
    proficiency_bonus INT NOT NULL,
    armor_class INT NOT NULL,
    advantage TINYINT NOT NULL,
    disadvantage TINYINT NOT NULL,
    critical TINYINT NOT NULL,
    hit TINYINT NOT NULL,
    damage_amount INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, attack_index),
    UNIQUE KEY uq_c33_20_0 (combat_id, event_sequence, phase, flow_index, frame_key),
    CONSTRAINT fk_c33_20_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_20 CHECK (attack_index BETWEEN 0 AND 1023 AND actor_id <> target_id AND attack_ordinal BETWEEN 0 AND 20 AND first_d20 BETWEEN 1 AND 20 AND (second_d20 IS NULL OR second_d20 BETWEEN 1 AND 20) AND selected_d20 BETWEEN 1 AND 20 AND ability_modifier BETWEEN -5 AND 10 AND proficiency_bonus BETWEEN 0 AND 6 AND armor_class BETWEEN 0 AND 100 AND damage_amount BETWEEN 0 AND 65535 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128 AND (sequence_key IS NULL OR (OCTET_LENGTH(sequence_key) = 16 AND (ORD(SUBSTRING(sequence_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(sequence_key, 9, 1)) & 192) = 128)) AND opportunity_attack IN (0, 1) AND advantage IN (0, 1) AND disadvantage IN (0, 1) AND critical IN (0, 1) AND hit IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_attack_die (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    attack_index INT NOT NULL,
    die_index INT NOT NULL,
    die_value INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, attack_index, die_index),
    CONSTRAINT fk_c33_21_0 FOREIGN KEY (combat_id, event_sequence, phase, flow_index, attack_index) REFERENCES s2_combat_attack (combat_id, event_sequence, phase, flow_index, attack_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_21 CHECK (die_index BETWEEN 0 AND 39 AND die_value BETWEEN 1 AND 12 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_combat_input (
    operation_id VARBINARY(16) NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    combat_id VARBINARY(16) NOT NULL,
    combat_operation_id VARBINARY(16) NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    damage_amount INT NOT NULL,
    critical TINYINT NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uq_c33_22_0 (character_id, event_sequence),
    CONSTRAINT fk_c33_22_0 FOREIGN KEY (operation_id) REFERENCES s2_character_event (operation_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_c33_22_1 FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_c33_22 CHECK (damage_amount BETWEEN 0 AND 65535 AND OCTET_LENGTH(operation_id) = 16 AND (ORD(SUBSTRING(operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(operation_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(combat_operation_id) = 16 AND (ORD(SUBSTRING(combat_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_operation_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128 AND critical IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE s2_character_event DROP CHECK ck_s2_character_command_0;
ALTER TABLE s2_character_event ADD CONSTRAINT ck_s2_character_command_0 CHECK (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED', _binary'RESOURCE_SPENT', _binary'RESOURCES_RECOVERED', _binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED', _binary'CHECK_RESOLVED', _binary'ITEM_ACQUIRED', _binary'ITEM_REMOVED', _binary'ITEM_EQUIPPED', _binary'ITEM_UNEQUIPPED', _binary'HEALTH_EFFECT_RESOLVED', _binary'DEATH_SAVE_RESOLVED', _binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_DEATH_SAVE_RESOLVED'));
DROP TRIGGER s2_health_event_insert;
DELIMITER $$
CREATE TRIGGER s2_health_event_insert BEFORE INSERT ON s2_character_health_event FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND ((event_kind = _binary'HEALTH_EFFECT_RESOLVED' AND NEW.effect_module IS NOT NULL AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL) OR (event_kind IN (_binary'DEATH_SAVE_RESOLVED', _binary'COMBAT_DEATH_SAVE_RESOLVED') AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NOT NULL) OR (event_kind = _binary'COMBAT_DAMAGE_APPLIED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 health event mismatch';
    END IF;
END$$
CREATE TRIGGER s2_character_combat_input_insert BEFORE INSERT ON s2_character_combat_input FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE operation_id = NEW.operation_id AND character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND event_kind IN (_binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_DEATH_SAVE_RESOLVED')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 combat health cause mismatch';
    END IF;
END$$
CREATE TRIGGER s2_combat_member_insert BEFORE INSERT ON s2_combat_member FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_runtime c JOIN s2_combat_runtime b ON b.combat_id = NEW.combat_id WHERE c.character_id = NEW.character_id AND c.campaign_id = b.campaign_id AND c.run_id = NEW.run_id AND c.snapshot_id = NEW.snapshot_id AND c.preparation_operation_id = NEW.preparation_operation_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 combat member binding mismatch';
    END IF;
END$$
CREATE TRIGGER s2_combat_event_insert BEFORE INSERT ON s2_combat_event FOR EACH ROW
BEGIN
    IF NEW.event_sequence <> (SELECT COALESCE(MAX(event_sequence), 0) + 1 FROM s2_combat_event WHERE combat_id = NEW.combat_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 combat event gap';
    END IF;
END$$
CREATE TRIGGER s2_combat_runtime_update BEFORE UPDATE ON s2_combat_runtime FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_runtime_delete BEFORE DELETE ON s2_combat_runtime FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_member_update BEFORE UPDATE ON s2_combat_member FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_member_delete BEFORE DELETE ON s2_combat_member FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_event_update BEFORE UPDATE ON s2_combat_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_event_delete BEFORE DELETE ON s2_combat_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_dependency_update BEFORE UPDATE ON s2_combat_dependency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_dependency_delete BEFORE DELETE ON s2_combat_dependency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_character_result_update BEFORE UPDATE ON s2_combat_character_result FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_character_result_delete BEFORE DELETE ON s2_combat_character_result FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_command_point_update BEFORE UPDATE ON s2_combat_command_point FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_command_point_delete BEFORE DELETE ON s2_combat_command_point FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_command_deployment_update BEFORE UPDATE ON s2_combat_command_deployment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_command_deployment_delete BEFORE DELETE ON s2_combat_command_deployment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_command_tie_update BEFORE UPDATE ON s2_combat_command_tie FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_command_tie_delete BEFORE DELETE ON s2_combat_command_tie FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_roll_update BEFORE UPDATE ON s2_combat_roll FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_roll_delete BEFORE DELETE ON s2_combat_roll FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_fact_update BEFORE UPDATE ON s2_combat_fact FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_fact_delete BEFORE DELETE ON s2_combat_fact FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_state_update BEFORE UPDATE ON s2_combat_state FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_state_delete BEFORE DELETE ON s2_combat_state FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_actor_update BEFORE UPDATE ON s2_combat_actor FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_actor_delete BEFORE DELETE ON s2_combat_actor FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_initiative_update BEFORE UPDATE ON s2_combat_initiative FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_initiative_delete BEFORE DELETE ON s2_combat_initiative FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_flow_update BEFORE UPDATE ON s2_combat_flow FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_flow_delete BEFORE DELETE ON s2_combat_flow FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_frame_update BEFORE UPDATE ON s2_combat_frame FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_frame_delete BEFORE DELETE ON s2_combat_frame FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_window_update BEFORE UPDATE ON s2_combat_window FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_window_delete BEFORE DELETE ON s2_combat_window FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_opportunity_update BEFORE UPDATE ON s2_combat_opportunity FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_opportunity_delete BEFORE DELETE ON s2_combat_opportunity FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_opportunity_item_update BEFORE UPDATE ON s2_combat_opportunity_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_opportunity_item_delete BEFORE DELETE ON s2_combat_opportunity_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_movement_update BEFORE UPDATE ON s2_combat_movement FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_movement_delete BEFORE DELETE ON s2_combat_movement FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_movement_point_update BEFORE UPDATE ON s2_combat_movement_point FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_movement_point_delete BEFORE DELETE ON s2_combat_movement_point FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_attack_update BEFORE UPDATE ON s2_combat_attack FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_attack_delete BEFORE DELETE ON s2_combat_attack FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_attack_die_update BEFORE UPDATE ON s2_combat_attack_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_combat_attack_die_delete BEFORE DELETE ON s2_combat_attack_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_character_combat_input_update BEFORE UPDATE ON s2_character_combat_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
CREATE TRIGGER s2_character_combat_input_delete BEFORE DELETE ON s2_character_combat_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 combat execution fact';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END
INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (33, 'V033__schema2-combat-runtime.sql', 'df5ddd066e6694d836daf2891ead4271e5ae6e2ed1cb2bf13a009d75d72c83e7');
