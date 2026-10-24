-- Typed character growth, resources and single-character rest execution; full campaign lifecycle remains unavailable.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v027_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v027_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 26
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version = 24 AND CAST(script_name AS BINARY) = _binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version = 25 AND CAST(script_name AS BINARY) = _binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version = 26 AND CAST(script_name AS BINARY) = _binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY) = _binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa')) = 26
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('s2_runtime_class_progression', 's2_runtime_resource', 's2_runtime_subclass', 's2_runtime_feature', 's2_runtime_feat', 's2_runtime_proficiency', 's2_runtime_proficiency_set', 's2_runtime_rest', 's2_runtime_class_level_entry', 's2_runtime_proficiency_entry', 's2_runtime_growth_grant', 's2_runtime_rule_prerequisite', 's2_runtime_chain_prerequisite', 's2_character_runtime', 's2_character_event', 's2_character_event_state', 's2_character_event_resource', 's2_character_event_class', 's2_character_event_hit_die', 's2_character_event_subclass', 's2_character_event_feature', 's2_character_event_feat', 's2_character_event_proficiency', 's2_character_command_ability', 's2_character_command_proficiency', 's2_character_command_hit_die')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v027_preflight;

CREATE TABLE s2_runtime_class_progression (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_class_module VARBINARY(64) NOT NULL,
    owner_class_type VARBINARY(20) NOT NULL,
    owner_class_key VARBINARY(128) NOT NULL,
    hit_die_sides INT NOT NULL,
    proficiency_bonus_profile VARCHAR(1000) NOT NULL,
    supported_maximum_level INT NOT NULL,
    advancement_scope VARBINARY(40) NOT NULL,
    prerequisite_groups INT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_class_progression_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_class_progression_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_class_progression_owner FOREIGN KEY (snapshot_id, owner_class_module, owner_class_type, owner_class_key) REFERENCES s2_runtime_character_source (snapshot_id, module_key, rule_type, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_class_progression_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND owner_class_type = _binary'character.class' AND hit_die_sides IN (6, 8, 10, 12) AND supported_maximum_level BETWEEN 1 AND 20 AND CHAR_LENGTH(proficiency_bonus_profile) BETWEEN 1 AND 1000 AND ((advancement_scope = _binary'STATISTICS_AND_RESOURCES_ONLY' AND prerequisite_groups IS NULL) OR (advancement_scope = _binary'CHARACTER_ADVANCEMENT' AND prerequisite_groups IS NOT NULL AND prerequisite_groups BETWEEN 0 AND 6)) AND source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_class_progression_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^progression[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_class_progression_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_resource (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_class_module VARBINARY(64) NOT NULL,
    owner_class_type VARBINARY(20) NOT NULL,
    owner_class_key VARBINARY(128) NOT NULL,
    maximum_profile VARCHAR(1000) NOT NULL,
    recovery_profile VARCHAR(200) NOT NULL,
    execution_mode VARBINARY(20) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_resource_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_resource_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_resource_owner FOREIGN KEY (snapshot_id, owner_class_module, owner_class_type, owner_class_key) REFERENCES s2_runtime_character_source (snapshot_id, module_key, rule_type, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_resource_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND owner_class_type = _binary'character.class' AND execution_mode IN (_binary'AUTOMATIC', _binary'BLOCKED') AND CHAR_LENGTH(maximum_profile) BETWEEN 1 AND 1000 AND CHAR_LENGTH(recovery_profile) BETWEEN 1 AND 200 AND source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_resource_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^resource[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_resource_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_subclass (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_class_module VARBINARY(64) NOT NULL,
    owner_class_type VARBINARY(20) NOT NULL,
    owner_class_key VARBINARY(128) NOT NULL,
    selection_level INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_subclass_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_subclass_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_subclass_owner FOREIGN KEY (snapshot_id, owner_class_module, owner_class_type, owner_class_key) REFERENCES s2_runtime_character_source (snapshot_id, module_key, rule_type, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_subclass_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND owner_class_type = _binary'character.class' AND selection_level BETWEEN 1 AND 20 AND source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_subclass_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^subclass[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_subclass_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_feature (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_module VARBINARY(64) NOT NULL,
    owner_type VARBINARY(32) NOT NULL,
    owner_key VARBINARY(128) NOT NULL,
    feature_level INT NOT NULL,
    feature_kind VARBINARY(16) NOT NULL,
    execution_mode VARBINARY(20) NOT NULL,
    execution_algorithm VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_feature_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_feature_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_feature_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND owner_type IN (_binary'character.class', _binary'character.subclass') AND feature_level BETWEEN 1 AND 20 AND feature_kind IN (_binary'FEATURE', _binary'OPTION') AND execution_mode IN (_binary'AUTOMATIC', _binary'DM_ADJUDICATION', _binary'BLOCKED') AND source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_feature_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^feature[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_feature_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_feat (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    prerequisite_groups INT NOT NULL,
    execution_mode VARBINARY(20) NOT NULL,
    execution_algorithm VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_feat_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_feat_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_feat_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND prerequisite_groups BETWEEN 0 AND 6 AND execution_mode IN (_binary'AUTOMATIC', _binary'DM_ADJUDICATION', _binary'BLOCKED') AND source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_feat_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^feat[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_feat_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_proficiency (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    category VARBINARY(16) NOT NULL,
    ability_key VARBINARY(32) NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_proficiency_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_proficiency_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_proficiency_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND category IN (_binary'SKILL', _binary'ARMOR', _binary'WEAPON') AND ((category = _binary'SKILL' AND ability_key IS NOT NULL AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) OR (category <> _binary'SKILL' AND ability_key IS NULL)) AND source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_proficiency_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^proficiency[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_proficiency_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_proficiency_set (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    category VARBINARY(16) NOT NULL,
    minimum_enabled_members INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_proficiency_set_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_proficiency_set_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_proficiency_set_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND category IN (_binary'SKILL', _binary'ARMOR', _binary'WEAPON', _binary'TOOL') AND minimum_enabled_members BETWEEN 1 AND 128),
    CONSTRAINT ck_s2_proficiency_set_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^proficiency_set[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_proficiency_set_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_rest (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_rest_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_rest_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_rest_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND source_page = 87 AND algorithm = _binary'SRD_2014_REST_V1'),
    CONSTRAINT ck_s2_rest_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^rest[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_rest_text CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND (author_note IS NULL OR CHAR_LENGTH(author_note) BETWEEN 1 AND 1000))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_class_level_entry (
    snapshot_id VARBINARY(16) NOT NULL,
    entry_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    class_level INT NOT NULL,
    asi_available TINYINT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_module_key, rule_key, collection_key, module_key, entry_key),
    UNIQUE KEY uq_s2_class_level_entry_index (snapshot_id, entry_index),
    CONSTRAINT fk_s2_class_level_entry_parent FOREIGN KEY (snapshot_id, rule_module_key, rule_key) REFERENCES s2_runtime_class_progression (snapshot_id, module_key, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_class_level_entry_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_class_level_entry_shape CHECK (entry_index BETWEEN 0 AND 511 AND enabled IN (0, 1) AND collection_key = _binary'levels' AND class_level BETWEEN 1 AND 20 AND asi_available IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_proficiency_entry (
    snapshot_id VARBINARY(16) NOT NULL,
    entry_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    target_module VARBINARY(64) NOT NULL,
    target_type VARBINARY(32) NOT NULL,
    target_key VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_module_key, rule_key, collection_key, module_key, entry_key),
    UNIQUE KEY uq_s2_proficiency_entry_index (snapshot_id, entry_index),
    CONSTRAINT fk_s2_proficiency_entry_parent FOREIGN KEY (snapshot_id, rule_module_key, rule_key) REFERENCES s2_runtime_proficiency_set (snapshot_id, module_key, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_proficiency_entry_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_proficiency_entry_shape CHECK (entry_index BETWEEN 0 AND 511 AND enabled IN (0, 1) AND collection_key = _binary'members' AND target_type IN (_binary'character.proficiency', _binary'character.tool'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_growth_grant (
    snapshot_id VARBINARY(16) NOT NULL,
    entry_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    growth_kind VARBINARY(6) NOT NULL,
    growth_target_module VARBINARY(64) NOT NULL,
    growth_target_type VARBINARY(32) NOT NULL,
    growth_target_key VARBINARY(128) NOT NULL,
    growth_count INT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_module_key, rule_key, collection_key, module_key, entry_key),
    UNIQUE KEY uq_s2_growth_grant_index (snapshot_id, entry_index),
    CONSTRAINT fk_s2_growth_grant_parent FOREIGN KEY (snapshot_id, rule_module_key, rule_key) REFERENCES s2_runtime_class_progression (snapshot_id, module_key, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_growth_grant_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_growth_grant_shape CHECK (entry_index BETWEEN 0 AND 511 AND enabled IN (0, 1) AND collection_key IN (_binary'starting_proficiencies', _binary'multiclass_proficiencies') AND ((growth_kind = _binary'FIXED' AND growth_count IS NULL AND growth_target_type IN (_binary'character.proficiency', _binary'character.tool')) OR (growth_kind = _binary'CHOICE' AND growth_count IS NOT NULL AND growth_count BETWEEN 1 AND 16 AND growth_target_type = _binary'catalog.proficiency_set')))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_rule_prerequisite (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    group_index INT NOT NULL,
    term_index INT NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    minimum_value INT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_index, group_index, term_index),
    CONSTRAINT fk_s2_rule_prerequisite_snapshot FOREIGN KEY (snapshot_id) REFERENCES s2_runtime_snapshot (snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_rule_prerequisite CHECK (rule_index BETWEEN 0 AND 255 AND group_index BETWEEN 0 AND 5 AND term_index BETWEEN 0 AND 1 AND minimum_value BETWEEN 1 AND 30 AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_chain_prerequisite (
    snapshot_id VARBINARY(16) NOT NULL,
    chain_index INT NOT NULL,
    step_index INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    group_index INT NOT NULL,
    term_index INT NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    minimum_value INT NOT NULL,
    PRIMARY KEY (snapshot_id, chain_index, step_index, phase, group_index, term_index),
    CONSTRAINT fk_s2_chain_prerequisite FOREIGN KEY (snapshot_id, chain_index, step_index) REFERENCES s2_runtime_chain_step (snapshot_id, chain_index, step_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_chain_prerequisite CHECK (phase IN (_binary'BEFORE', _binary'AFTER') AND group_index BETWEEN 0 AND 5 AND term_index BETWEEN 0 AND 1 AND minimum_value BETWEEN 1 AND 30 AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_runtime (
    character_id VARBINARY(16) NOT NULL,
    campaign_id VARBINARY(16) NOT NULL,
    run_id VARBINARY(16) NOT NULL,
    snapshot_id VARBINARY(16) NOT NULL,
    preparation_operation_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (character_id),
    UNIQUE KEY uq_s2_character_preparation (preparation_operation_id),
    UNIQUE KEY uq_s2_character_run (run_id),
    CONSTRAINT fk_s2_character_preparation FOREIGN KEY (preparation_operation_id) REFERENCES s2_initial_plan (operation_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_character_snapshot FOREIGN KEY (run_id, snapshot_id) REFERENCES s2_runtime_snapshot (run_id, snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    operation_id VARBINARY(16) NOT NULL,
    request_digest VARBINARY(64) NOT NULL,
    event_kind VARBINARY(32) NOT NULL,
    resource_module VARBINARY(64) NULL,
    resource_type VARBINARY(32) NULL,
    resource_key VARBINARY(128) NULL,
    resource_amount BIGINT NOT NULL,
    recovery_trigger VARBINARY(16) NULL,
    hp_choice VARBINARY(64) NULL,
    target_class_module VARBINARY(64) NULL,
    target_class_type VARBINARY(32) NULL,
    target_class_key VARBINARY(128) NULL,
    chosen_subclass_module VARBINARY(64) NULL,
    chosen_subclass_type VARBINARY(32) NULL,
    chosen_subclass_key VARBINARY(128) NULL,
    chosen_feat_module VARBINARY(64) NULL,
    chosen_feat_type VARBINARY(32) NULL,
    chosen_feat_key VARBINARY(128) NULL,
    ability_choice_count INT NOT NULL,
    proficiency_choice_count INT NOT NULL,
    rest_kind VARBINARY(16) NULL,
    rest_activity VARBINARY(16) NULL,
    rest_minutes BIGINT NOT NULL,
    rest_hit_die_sides INT NOT NULL,
    recovered_hit_die_count INT NOT NULL,
    hit_die_roll INT NULL,
    PRIMARY KEY (character_id, event_sequence),
    UNIQUE KEY uq_s2_character_operation (operation_id),
    CONSTRAINT fk_s2_character_event FOREIGN KEY (character_id) REFERENCES s2_character_runtime (character_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_command_0 CHECK (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED', _binary'RESOURCE_SPENT', _binary'RESOURCES_RECOVERED', _binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED')),
    CONSTRAINT ck_s2_character_command_1 CHECK (event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(operation_id) = 16 AND (ORD(SUBSTRING(operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(operation_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(request_digest) = 64 AND NOT REGEXP_LIKE(CONVERT(request_digest USING latin1), '[^0-9a-f]')),
    CONSTRAINT ck_s2_character_command_2 CHECK (((event_kind = _binary'INITIALIZED' AND event_sequence = 1) OR (event_kind <> _binary'INITIALIZED' AND event_sequence > 1))),
    CONSTRAINT ck_s2_character_command_3 CHECK (((event_kind = _binary'RESOURCE_SPENT' AND resource_module IS NOT NULL AND resource_type IS NOT NULL AND resource_type = _binary'character.resource' AND resource_key IS NOT NULL AND resource_amount BETWEEN 1 AND 1000000) OR (event_kind <> _binary'RESOURCE_SPENT' AND resource_module IS NULL AND resource_type IS NULL AND resource_key IS NULL AND resource_amount = 0))),
    CONSTRAINT ck_s2_character_command_4 CHECK ((resource_key IS NULL OR (REGEXP_LIKE(CONVERT(resource_key USING latin1), '^resource[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(resource_key USING latin1), '[^a-z0-9_.]', 'c')))),
    CONSTRAINT ck_s2_character_command_5 CHECK (((event_kind = _binary'RESOURCES_RECOVERED' AND recovery_trigger IS NOT NULL AND recovery_trigger IN (_binary'SHORT_REST', _binary'LONG_REST')) OR (event_kind <> _binary'RESOURCES_RECOVERED' AND recovery_trigger IS NULL))),
    CONSTRAINT ck_s2_character_command_6 CHECK (((event_kind IN (_binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED') AND hp_choice IS NOT NULL AND hp_choice IN (_binary'FIXED_AVERAGE', _binary'SERVER_ROLL') AND ((hp_choice = _binary'FIXED_AVERAGE' AND hit_die_roll IS NULL) OR (hp_choice = _binary'SERVER_ROLL' AND hit_die_roll BETWEEN 1 AND 12 AND hit_die_roll IS NOT NULL))) OR (event_kind NOT IN (_binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED') AND hp_choice IS NULL))),
    CONSTRAINT ck_s2_character_command_7 CHECK (((event_kind = _binary'CHARACTER_ADVANCED' AND target_class_module IS NOT NULL AND target_class_type IS NOT NULL AND target_class_type = _binary'character.class' AND target_class_key IS NOT NULL) OR (event_kind <> _binary'CHARACTER_ADVANCED' AND target_class_module IS NULL AND target_class_type IS NULL AND target_class_key IS NULL))),
    CONSTRAINT ck_s2_character_command_8 CHECK (ability_choice_count BETWEEN 0 AND 2 AND proficiency_choice_count BETWEEN 0 AND 64 AND (event_kind = _binary'CHARACTER_ADVANCED' OR ability_choice_count = 0) AND (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED') OR proficiency_choice_count = 0)),
    CONSTRAINT ck_s2_character_command_9 CHECK (((chosen_subclass_module IS NULL AND chosen_subclass_type IS NULL AND chosen_subclass_key IS NULL) OR (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED') AND chosen_subclass_module IS NOT NULL AND chosen_subclass_type IS NOT NULL AND chosen_subclass_type = _binary'character.subclass' AND chosen_subclass_key IS NOT NULL))),
    CONSTRAINT ck_s2_character_command_10 CHECK (((chosen_feat_module IS NULL AND chosen_feat_type IS NULL AND chosen_feat_key IS NULL) OR (event_kind IN (_binary'CHARACTER_ADVANCED') AND chosen_feat_module IS NOT NULL AND chosen_feat_type IS NOT NULL AND chosen_feat_type = _binary'character.feat' AND chosen_feat_key IS NOT NULL))),
    CONSTRAINT ck_s2_character_command_11 CHECK ((chosen_feat_module IS NULL OR ability_choice_count = 0)),
    CONSTRAINT ck_s2_character_command_12 CHECK (((event_kind = _binary'REST_STARTED' AND rest_kind IS NOT NULL AND rest_kind IN (_binary'SHORT_REST', _binary'LONG_REST') AND rest_activity IS NULL AND rest_minutes = 0 AND rest_hit_die_sides = 0 AND recovered_hit_die_count = 0 AND hit_die_roll IS NULL) OR (event_kind = _binary'TIME_ADVANCED' AND rest_kind IS NULL AND rest_activity IS NOT NULL AND rest_activity IN (_binary'SLEEP', _binary'LIGHT', _binary'STRENUOUS', _binary'NONE') AND rest_minutes BETWEEN 1 AND 1440 AND rest_hit_die_sides = 0 AND recovered_hit_die_count = 0 AND hit_die_roll IS NULL) OR (event_kind = _binary'REST_COMPLETED' AND rest_kind IS NULL AND rest_activity IS NULL AND rest_minutes = 0 AND rest_hit_die_sides = 0 AND recovered_hit_die_count BETWEEN 0 AND 4 AND hit_die_roll IS NULL) OR (event_kind = _binary'HIT_DIE_SPENT' AND rest_kind IS NULL AND rest_activity IS NULL AND rest_minutes = 0 AND rest_hit_die_sides IN (6, 8, 10, 12) AND recovered_hit_die_count = 0 AND hit_die_roll IS NOT NULL AND hit_die_roll BETWEEN 1 AND rest_hit_die_sides) OR (event_kind = _binary'REST_ENDED' AND rest_kind IS NULL AND rest_activity IS NULL AND rest_minutes = 0 AND rest_hit_die_sides = 0 AND recovered_hit_die_count = 0 AND hit_die_roll IS NULL) OR (event_kind NOT IN (_binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED') AND rest_kind IS NULL AND rest_activity IS NULL AND rest_minutes = 0 AND rest_hit_die_sides = 0 AND recovered_hit_die_count = 0 AND (event_kind IN (_binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED') OR hit_die_roll IS NULL))))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_state (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    class_module VARBINARY(64) NOT NULL,
    class_type VARBINARY(32) NOT NULL,
    class_key VARBINARY(128) NOT NULL,
    strength INT NOT NULL,
    dexterity INT NOT NULL,
    constitution INT NOT NULL,
    intelligence INT NOT NULL,
    wisdom INT NOT NULL,
    charisma INT NOT NULL,
    total_level INT NOT NULL,
    proficiency_bonus INT NOT NULL,
    hit_points BIGINT NOT NULL,
    maximum_hit_points BIGINT NOT NULL,
    row_version BIGINT NOT NULL,
    resource_count INT NOT NULL,
    class_count INT NOT NULL,
    hit_die_count INT NOT NULL,
    subclass_count INT NOT NULL,
    feature_count INT NOT NULL,
    feat_count INT NOT NULL,
    proficiency_count INT NOT NULL,
    minute BIGINT NOT NULL,
    last_long_rest_end_minute BIGINT NULL,
    rest_module VARBINARY(64) NULL,
    rest_type VARBINARY(32) NULL,
    rest_key VARBINARY(128) NULL,
    rest_kind VARBINARY(16) NULL,
    rest_start_minute BIGINT NULL,
    rest_start_hit_points INT NULL,
    sleep_minutes BIGINT NULL,
    light_minutes BIGINT NULL,
    strenuous_minutes BIGINT NULL,
    rest_phase VARBINARY(24) NULL,
    last_activity VARBINARY(16) NULL,
    consecutive_strenuous_minutes BIGINT NULL,
    interrupted TINYINT NULL,
    PRIMARY KEY (character_id, event_sequence, phase),
    CONSTRAINT fk_s2_character_state_event FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_state_shape CHECK (class_type = _binary'character.class' AND total_level BETWEEN 1 AND 20 AND proficiency_bonus = 2 + FLOOR((total_level - 1) / 4) AND maximum_hit_points BETWEEN 1 AND 65535 AND hit_points BETWEEN 0 AND maximum_hit_points AND resource_count BETWEEN 0 AND 32 AND class_count BETWEEN 1 AND 20 AND hit_die_count BETWEEN 1 AND 4 AND subclass_count BETWEEN 0 AND 20 AND feature_count BETWEEN 0 AND 64 AND feat_count BETWEEN 0 AND 20 AND proficiency_count BETWEEN 0 AND 64 AND ((phase = _binary'AFTER' AND row_version = event_sequence - 1) OR (phase = _binary'BEFORE' AND event_sequence > 1 AND row_version = event_sequence - 2))),
    CONSTRAINT ck_s2_character_abilities CHECK (strength BETWEEN 1 AND 30 AND dexterity BETWEEN 1 AND 30 AND constitution BETWEEN 1 AND 30 AND intelligence BETWEEN 1 AND 30 AND wisdom BETWEEN 1 AND 30 AND charisma BETWEEN 1 AND 30),
    CONSTRAINT ck_s2_character_clock CHECK (minute BETWEEN 0 AND 1000000000 AND (last_long_rest_end_minute IS NULL OR last_long_rest_end_minute BETWEEN 0 AND minute) AND ((rest_module IS NULL AND rest_type IS NULL AND rest_key IS NULL AND rest_kind IS NULL AND rest_start_minute IS NULL AND rest_start_hit_points IS NULL AND sleep_minutes IS NULL AND light_minutes IS NULL AND strenuous_minutes IS NULL AND rest_phase IS NULL AND consecutive_strenuous_minutes IS NULL AND interrupted IS NULL AND last_activity IS NULL) OR (rest_module IS NOT NULL AND rest_type IS NOT NULL AND rest_key IS NOT NULL AND rest_kind IS NOT NULL AND rest_start_minute IS NOT NULL AND rest_start_hit_points IS NOT NULL AND sleep_minutes IS NOT NULL AND light_minutes IS NOT NULL AND strenuous_minutes IS NOT NULL AND rest_phase IS NOT NULL AND consecutive_strenuous_minutes IS NOT NULL AND interrupted IS NOT NULL AND rest_type = _binary'adventure.rest' AND rest_kind IN (_binary'SHORT_REST', _binary'LONG_REST') AND rest_start_minute BETWEEN 0 AND minute AND rest_start_hit_points BETWEEN 0 AND 65535 AND sleep_minutes BETWEEN 0 AND 1000000000 AND light_minutes BETWEEN 0 AND 1000000000 AND strenuous_minutes BETWEEN 0 AND 1000000000 AND sleep_minutes + light_minutes + strenuous_minutes <= minute - rest_start_minute AND (rest_phase = _binary'IN_PROGRESS' OR (rest_phase = _binary'SPENDING_HIT_DICE' AND rest_kind = _binary'SHORT_REST')) AND (last_activity IS NULL OR last_activity IN (_binary'SLEEP', _binary'LIGHT', _binary'STRENUOUS')) AND consecutive_strenuous_minutes BETWEEN 0 AND strenuous_minutes AND interrupted IN (0, 1))))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_resource (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    resource_index INT NOT NULL,
    resource_module VARBINARY(64) NOT NULL,
    resource_type VARBINARY(32) NOT NULL,
    resource_key VARBINARY(128) NOT NULL,
    current_value BIGINT NOT NULL,
    maximum_value BIGINT NOT NULL,
    unlimited TINYINT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, phase, resource_index),
    UNIQUE KEY uq_s2_character_resource_identity (character_id, event_sequence, phase, resource_module, resource_type, resource_key),
    CONSTRAINT fk_s2_character_resource_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_resource_shape CHECK (resource_index BETWEEN 0 AND 31 AND resource_type = _binary'character.resource' AND maximum_value BETWEEN 0 AND 1000000 AND current_value BETWEEN 0 AND maximum_value AND unlimited IN (0, 1) AND (unlimited = 0 OR (current_value = 0 AND maximum_value = 0)) AND REGEXP_LIKE(CONVERT(resource_key USING latin1), '^resource[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(resource_key USING latin1), '[^a-z0-9_.]', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_class (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    class_index INT NOT NULL,
    class_module VARBINARY(64) NOT NULL,
    class_type VARBINARY(32) NOT NULL,
    class_key VARBINARY(128) NOT NULL,
    class_level INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, phase, class_index),
    UNIQUE KEY uq_s2_character_class_identity (character_id, event_sequence, phase, class_module, class_type, class_key),
    CONSTRAINT fk_s2_character_class_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_class_shape CHECK (class_index BETWEEN 0 AND 19 AND class_type = _binary'character.class' AND class_level BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_hit_die (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    pool_index INT NOT NULL,
    sides INT NOT NULL,
    current_value INT NOT NULL,
    maximum_value INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, phase, pool_index),
    UNIQUE KEY uq_s2_character_hit_die_identity (character_id, event_sequence, phase, sides),
    CONSTRAINT fk_s2_character_hit_die_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_hit_die_shape CHECK (pool_index BETWEEN 0 AND 3 AND sides IN (6, 8, 10, 12) AND maximum_value BETWEEN 1 AND 20 AND current_value BETWEEN 0 AND maximum_value)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_subclass (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    subclass_index INT NOT NULL,
    class_module VARBINARY(64) NOT NULL,
    class_type VARBINARY(32) NOT NULL,
    class_key VARBINARY(128) NOT NULL,
    subclass_module VARBINARY(64) NOT NULL,
    subclass_type VARBINARY(32) NOT NULL,
    subclass_key VARBINARY(128) NOT NULL,
    acquired_class_level INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, phase, subclass_index),
    UNIQUE KEY uq_s2_character_subclass_identity (character_id, event_sequence, phase, class_module, class_type, class_key),
    CONSTRAINT fk_s2_character_subclass_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_subclass_shape CHECK (subclass_index BETWEEN 0 AND 19 AND class_type = _binary'character.class' AND subclass_type = _binary'character.subclass' AND acquired_class_level BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_feature (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    feature_index INT NOT NULL,
    feature_module VARBINARY(64) NOT NULL,
    feature_type VARBINARY(32) NOT NULL,
    feature_key VARBINARY(128) NOT NULL,
    owner_module VARBINARY(64) NOT NULL,
    owner_type VARBINARY(32) NOT NULL,
    owner_key VARBINARY(128) NOT NULL,
    acquired_class_level INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, phase, feature_index),
    UNIQUE KEY uq_s2_character_feature_identity (character_id, event_sequence, phase, feature_module, feature_type, feature_key),
    CONSTRAINT fk_s2_character_feature_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_feature_shape CHECK (feature_index BETWEEN 0 AND 63 AND feature_type = _binary'character.feature' AND owner_type IN (_binary'character.class', _binary'character.subclass') AND acquired_class_level BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_feat (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    feat_index INT NOT NULL,
    feat_module VARBINARY(64) NOT NULL,
    feat_type VARBINARY(32) NOT NULL,
    feat_key VARBINARY(128) NOT NULL,
    class_module VARBINARY(64) NOT NULL,
    class_type VARBINARY(32) NOT NULL,
    class_key VARBINARY(128) NOT NULL,
    acquired_class_level INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, phase, feat_index),
    UNIQUE KEY uq_s2_character_feat_identity (character_id, event_sequence, phase, feat_module, feat_type, feat_key),
    CONSTRAINT fk_s2_character_feat_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_feat_shape CHECK (feat_index BETWEEN 0 AND 19 AND feat_type = _binary'character.feat' AND class_type = _binary'character.class' AND acquired_class_level BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_event_proficiency (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    proficiency_index INT NOT NULL,
    source_module VARBINARY(64) NOT NULL,
    source_type VARBINARY(32) NOT NULL,
    source_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    slot_module VARBINARY(64) NOT NULL,
    slot_key VARBINARY(64) NOT NULL,
    target_module VARBINARY(64) NOT NULL,
    target_type VARBINARY(32) NOT NULL,
    target_key VARBINARY(128) NOT NULL,
    acquired_total_level INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, phase, proficiency_index),
    UNIQUE KEY uq_s2_character_proficiency_identity (character_id, event_sequence, phase, source_module, source_type, source_key, collection_key, slot_module, slot_key, target_module, target_type, target_key),
    CONSTRAINT fk_s2_character_proficiency_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_proficiency_shape CHECK (proficiency_index BETWEEN 0 AND 63 AND ((source_type = _binary'character.class_progression' AND collection_key IN (_binary'starting_proficiencies', _binary'multiclass_proficiencies')) OR (source_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND collection_key = _binary'proficiency_grants')) AND target_type IN (_binary'character.proficiency', _binary'character.tool') AND acquired_total_level BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_command_ability (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    choice_index INT NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    increase_value INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, choice_index),
    CONSTRAINT fk_s2_character_command_ability FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_command_ability CHECK (choice_index BETWEEN 0 AND 1 AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma') AND increase_value BETWEEN 1 AND 2)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_command_proficiency (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    choice_index INT NOT NULL,
    source_module VARBINARY(64) NOT NULL,
    source_type VARBINARY(32) NOT NULL,
    source_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    slot_module VARBINARY(64) NOT NULL,
    slot_key VARBINARY(64) NOT NULL,
    target_module VARBINARY(64) NOT NULL,
    target_type VARBINARY(32) NOT NULL,
    target_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (character_id, event_sequence, choice_index),
    CONSTRAINT fk_s2_character_command_proficiency FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_command_proficiency CHECK (choice_index BETWEEN 0 AND 63 AND source_type = _binary'character.class_progression' AND collection_key IN (_binary'starting_proficiencies', _binary'multiclass_proficiencies') AND target_type IN (_binary'character.proficiency', _binary'character.tool'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_command_hit_die (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    pool_index INT NOT NULL,
    sides INT NOT NULL,
    recovered_value INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, pool_index),
    CONSTRAINT fk_s2_character_command_hit_die FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_command_hit_die CHECK (pool_index BETWEEN 0 AND 3 AND sides IN (6, 8, 10, 12) AND recovered_value BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE s2_runtime_field_chain DROP CHECK ck_s2_chain_target;
ALTER TABLE s2_runtime_field_chain MODIFY rule_type VARBINARY(32) NOT NULL, MODIFY field_key VARBINARY(32) NOT NULL, MODIFY collection_key VARBINARY(32) NULL;
ALTER TABLE s2_runtime_field_chain ADD CONSTRAINT ck_s2_chain_target CHECK (
    (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL
        AND ((rule_type IN (_binary'character.language', _binary'character.tool')
            AND field_key IN (_binary'display_name', _binary'description', _binary'category', _binary'source_page', _binary'author_note', _binary'enabled'))
        OR (rule_type IN (_binary'catalog.language_set', _binary'catalog.tool_set')
            AND field_key IN (_binary'display_name', _binary'minimum_enabled_members', _binary'author_note', _binary'enabled'))))
    OR (target_kind = _binary'entry' AND collection_key IS NOT NULL AND collection_key = _binary'members' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL
        AND ((rule_type = _binary'catalog.language_set' AND field_key IN (_binary'language', _binary'author_note', _binary'enabled'))
            OR (rule_type = _binary'catalog.tool_set' AND field_key IN (_binary'tool', _binary'author_note', _binary'enabled'))))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL
        AND rule_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND (field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'author_note', _binary'enabled') OR (rule_type = _binary'character.subrace' AND field_key = _binary'parent_race')))
    OR (target_kind = _binary'entry' AND rule_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND collection_key IS NOT NULL AND collection_key = _binary'proficiency_grants' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'grant', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.class_progression' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner_class', _binary'hit_die_sides', _binary'proficiency_bonus_profile', _binary'supported_maximum_level', _binary'advancement_scope', _binary'multiclass_prerequisite', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.resource' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner_class', _binary'maximum_profile', _binary'recovery_profile', _binary'execution_mode', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.subclass' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner_class', _binary'selection_level', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.feature' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner', _binary'level', _binary'feature_kind', _binary'execution_mode', _binary'execution_algorithm', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.feat' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'prerequisite', _binary'execution_mode', _binary'execution_algorithm', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.proficiency' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'category', _binary'ability_key', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'catalog.proficiency_set' AND field_key IN (_binary'display_name', _binary'category', _binary'minimum_enabled_members', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'adventure.rest' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'algorithm', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'character.class_progression' AND collection_key IS NOT NULL AND collection_key = _binary'levels' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'level', _binary'asi_available', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'character.class_progression' AND collection_key IS NOT NULL AND collection_key = _binary'starting_proficiencies' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'growth_grant', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'character.class_progression' AND collection_key IS NOT NULL AND collection_key = _binary'multiclass_proficiencies' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'growth_grant', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'catalog.proficiency_set' AND collection_key IS NOT NULL AND collection_key = _binary'members' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'proficiency', _binary'author_note', _binary'enabled'))
);
ALTER TABLE s2_runtime_chain_step ADD before_reference_rule_type VARBINARY(32) NULL, ADD before_prerequisite_groups INT NULL, ADD before_growth_kind VARBINARY(6) NULL, ADD before_growth_target_module VARBINARY(64) NULL, ADD before_growth_target_type VARBINARY(32) NULL, ADD before_growth_target_key VARBINARY(128) NULL, ADD before_growth_count INT NULL;
ALTER TABLE s2_runtime_chain_step DROP CHECK ck_s2_step_before;
ALTER TABLE s2_runtime_chain_step ADD CONSTRAINT ck_s2_step_before CHECK (((before_state IN (_binary'UNINITIALIZED', _binary'ABSENT') AND before_text_value IS NULL AND before_integer_value IS NULL AND before_boolean_value IS NULL AND before_reference_module_key IS NULL AND before_reference_rule_key IS NULL AND before_grant_category IS NULL AND before_grant_kind IS NULL AND before_grant_target_module_key IS NULL AND before_grant_target_rule_key IS NULL AND before_grant_count IS NULL AND before_reference_rule_type IS NULL AND before_prerequisite_groups IS NULL AND before_growth_kind IS NULL AND before_growth_target_module IS NULL AND before_growth_target_type IS NULL AND before_growth_target_key IS NULL AND before_growth_count IS NULL) OR (before_state = _binary'PRESENT' AND ((before_text_value IS NOT NULL) + (before_integer_value IS NOT NULL) + (before_boolean_value IS NOT NULL) + (before_reference_module_key IS NOT NULL) + (before_grant_kind IS NOT NULL) + (before_prerequisite_groups IS NOT NULL) + (before_growth_kind IS NOT NULL)) = 1 AND ((before_reference_module_key IS NULL AND before_reference_rule_key IS NULL AND before_reference_rule_type IS NULL) OR (before_reference_module_key IS NOT NULL AND before_reference_rule_key IS NOT NULL AND (before_reference_rule_type IS NULL OR before_reference_rule_type IN (_binary'character.class', _binary'character.subclass', _binary'character.proficiency', _binary'character.tool')))) AND (before_boolean_value IS NULL OR before_boolean_value IN (0, 1)) AND (before_prerequisite_groups IS NULL OR before_prerequisite_groups BETWEEN 0 AND 6) AND ((before_grant_category IS NULL AND before_grant_kind IS NULL AND before_grant_target_module_key IS NULL AND before_grant_target_rule_key IS NULL AND before_grant_count IS NULL) OR (before_grant_category IS NOT NULL AND before_grant_category IN (_binary'LANGUAGE', _binary'TOOL') AND before_grant_kind IS NOT NULL AND before_grant_target_module_key IS NOT NULL AND before_grant_target_rule_key IS NOT NULL AND ((before_grant_kind = _binary'FIXED' AND before_grant_count IS NULL) OR (before_grant_kind = _binary'CHOICE' AND before_grant_count IS NOT NULL AND before_grant_count BETWEEN 1 AND 16)))) AND ((before_growth_kind IS NULL AND before_growth_target_module IS NULL AND before_growth_target_type IS NULL AND before_growth_target_key IS NULL AND before_growth_count IS NULL) OR (before_growth_kind IS NOT NULL AND before_growth_target_module IS NOT NULL AND before_growth_target_type IS NOT NULL AND before_growth_target_key IS NOT NULL AND ((before_growth_kind = _binary'FIXED' AND before_growth_target_type IN (_binary'character.proficiency', _binary'character.tool') AND before_growth_count IS NULL) OR (before_growth_kind = _binary'CHOICE' AND before_growth_target_type = _binary'catalog.proficiency_set' AND before_growth_count IS NOT NULL AND before_growth_count BETWEEN 1 AND 16)))))));
ALTER TABLE s2_runtime_chain_step ADD after_reference_rule_type VARBINARY(32) NULL, ADD after_prerequisite_groups INT NULL, ADD after_growth_kind VARBINARY(6) NULL, ADD after_growth_target_module VARBINARY(64) NULL, ADD after_growth_target_type VARBINARY(32) NULL, ADD after_growth_target_key VARBINARY(128) NULL, ADD after_growth_count INT NULL;
ALTER TABLE s2_runtime_chain_step DROP CHECK ck_s2_step_after;
ALTER TABLE s2_runtime_chain_step ADD CONSTRAINT ck_s2_step_after CHECK (((after_state IN (_binary'UNINITIALIZED', _binary'ABSENT') AND after_text_value IS NULL AND after_integer_value IS NULL AND after_boolean_value IS NULL AND after_reference_module_key IS NULL AND after_reference_rule_key IS NULL AND after_grant_category IS NULL AND after_grant_kind IS NULL AND after_grant_target_module_key IS NULL AND after_grant_target_rule_key IS NULL AND after_grant_count IS NULL AND after_reference_rule_type IS NULL AND after_prerequisite_groups IS NULL AND after_growth_kind IS NULL AND after_growth_target_module IS NULL AND after_growth_target_type IS NULL AND after_growth_target_key IS NULL AND after_growth_count IS NULL) OR (after_state = _binary'PRESENT' AND ((after_text_value IS NOT NULL) + (after_integer_value IS NOT NULL) + (after_boolean_value IS NOT NULL) + (after_reference_module_key IS NOT NULL) + (after_grant_kind IS NOT NULL) + (after_prerequisite_groups IS NOT NULL) + (after_growth_kind IS NOT NULL)) = 1 AND ((after_reference_module_key IS NULL AND after_reference_rule_key IS NULL AND after_reference_rule_type IS NULL) OR (after_reference_module_key IS NOT NULL AND after_reference_rule_key IS NOT NULL AND (after_reference_rule_type IS NULL OR after_reference_rule_type IN (_binary'character.class', _binary'character.subclass', _binary'character.proficiency', _binary'character.tool')))) AND (after_boolean_value IS NULL OR after_boolean_value IN (0, 1)) AND (after_prerequisite_groups IS NULL OR after_prerequisite_groups BETWEEN 0 AND 6) AND ((after_grant_category IS NULL AND after_grant_kind IS NULL AND after_grant_target_module_key IS NULL AND after_grant_target_rule_key IS NULL AND after_grant_count IS NULL) OR (after_grant_category IS NOT NULL AND after_grant_category IN (_binary'LANGUAGE', _binary'TOOL') AND after_grant_kind IS NOT NULL AND after_grant_target_module_key IS NOT NULL AND after_grant_target_rule_key IS NOT NULL AND ((after_grant_kind = _binary'FIXED' AND after_grant_count IS NULL) OR (after_grant_kind = _binary'CHOICE' AND after_grant_count IS NOT NULL AND after_grant_count BETWEEN 1 AND 16)))) AND ((after_growth_kind IS NULL AND after_growth_target_module IS NULL AND after_growth_target_type IS NULL AND after_growth_target_key IS NULL AND after_growth_count IS NULL) OR (after_growth_kind IS NOT NULL AND after_growth_target_module IS NOT NULL AND after_growth_target_type IS NOT NULL AND after_growth_target_key IS NOT NULL AND ((after_growth_kind = _binary'FIXED' AND after_growth_target_type IN (_binary'character.proficiency', _binary'character.tool') AND after_growth_count IS NULL) OR (after_growth_kind = _binary'CHOICE' AND after_growth_target_type = _binary'catalog.proficiency_set' AND after_growth_count IS NOT NULL AND after_growth_count BETWEEN 1 AND 16)))))));

DELIMITER $$
CREATE TRIGGER s2_character_runtime_insert BEFORE INSERT ON s2_character_runtime FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_initial_plan WHERE operation_id = NEW.preparation_operation_id AND character_id = NEW.character_id AND campaign_id = NEW.campaign_id AND run_id = NEW.run_id AND snapshot_id = NEW.snapshot_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 character preparation binding mismatch';
    END IF;
END$$
CREATE TRIGGER s2_character_event_insert BEFORE INSERT ON s2_character_event FOR EACH ROW
BEGIN
    IF NEW.event_sequence <> (SELECT COALESCE(MAX(event_sequence), 0) + 1 FROM s2_character_event WHERE character_id = NEW.character_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 character event sequence mismatch';
    END IF;
END$$
CREATE TRIGGER s2_runtime_class_progression_update BEFORE UPDATE ON s2_runtime_class_progression FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_class_progression_delete BEFORE DELETE ON s2_runtime_class_progression FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_resource_update BEFORE UPDATE ON s2_runtime_resource FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_resource_delete BEFORE DELETE ON s2_runtime_resource FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_subclass_update BEFORE UPDATE ON s2_runtime_subclass FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_subclass_delete BEFORE DELETE ON s2_runtime_subclass FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_feature_update BEFORE UPDATE ON s2_runtime_feature FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_feature_delete BEFORE DELETE ON s2_runtime_feature FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_feat_update BEFORE UPDATE ON s2_runtime_feat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_feat_delete BEFORE DELETE ON s2_runtime_feat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_proficiency_update BEFORE UPDATE ON s2_runtime_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_proficiency_delete BEFORE DELETE ON s2_runtime_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_proficiency_set_update BEFORE UPDATE ON s2_runtime_proficiency_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_proficiency_set_delete BEFORE DELETE ON s2_runtime_proficiency_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_rest_update BEFORE UPDATE ON s2_runtime_rest FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_rest_delete BEFORE DELETE ON s2_runtime_rest FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_class_level_entry_update BEFORE UPDATE ON s2_runtime_class_level_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_class_level_entry_delete BEFORE DELETE ON s2_runtime_class_level_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_proficiency_entry_update BEFORE UPDATE ON s2_runtime_proficiency_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_proficiency_entry_delete BEFORE DELETE ON s2_runtime_proficiency_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_growth_grant_update BEFORE UPDATE ON s2_runtime_growth_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_growth_grant_delete BEFORE DELETE ON s2_runtime_growth_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_rule_prerequisite_update BEFORE UPDATE ON s2_runtime_rule_prerequisite FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_rule_prerequisite_delete BEFORE DELETE ON s2_runtime_rule_prerequisite FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_chain_prerequisite_update BEFORE UPDATE ON s2_runtime_chain_prerequisite FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_runtime_chain_prerequisite_delete BEFORE DELETE ON s2_runtime_chain_prerequisite FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_runtime_update BEFORE UPDATE ON s2_character_runtime FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_runtime_delete BEFORE DELETE ON s2_character_runtime FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_update BEFORE UPDATE ON s2_character_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_delete BEFORE DELETE ON s2_character_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_state_update BEFORE UPDATE ON s2_character_event_state FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_state_delete BEFORE DELETE ON s2_character_event_state FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_resource_update BEFORE UPDATE ON s2_character_event_resource FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_resource_delete BEFORE DELETE ON s2_character_event_resource FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_class_update BEFORE UPDATE ON s2_character_event_class FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_class_delete BEFORE DELETE ON s2_character_event_class FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_hit_die_update BEFORE UPDATE ON s2_character_event_hit_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_hit_die_delete BEFORE DELETE ON s2_character_event_hit_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_subclass_update BEFORE UPDATE ON s2_character_event_subclass FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_subclass_delete BEFORE DELETE ON s2_character_event_subclass FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_feature_update BEFORE UPDATE ON s2_character_event_feature FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_feature_delete BEFORE DELETE ON s2_character_event_feature FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_feat_update BEFORE UPDATE ON s2_character_event_feat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_feat_delete BEFORE DELETE ON s2_character_event_feat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_proficiency_update BEFORE UPDATE ON s2_character_event_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_event_proficiency_delete BEFORE DELETE ON s2_character_event_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_command_ability_update BEFORE UPDATE ON s2_character_command_ability FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_command_ability_delete BEFORE DELETE ON s2_character_command_ability FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_command_proficiency_update BEFORE UPDATE ON s2_character_command_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_command_proficiency_delete BEFORE DELETE ON s2_character_command_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_command_hit_die_update BEFORE UPDATE ON s2_character_command_hit_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
CREATE TRIGGER s2_character_command_hit_die_delete BEFORE DELETE ON s2_character_command_hit_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character execution fact';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (27, 'V027__schema2-character-runtime.sql', '157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f');
