-- Original typed schema 2 character growth, resource and rest declarations/operations.
-- Existing artifacts remain immutable; missing equivalent typed rows fail complete source readback.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v005_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO rules_v005_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM rule_schema_meta) = 4
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__rule-source-schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__tool-catalog.sql' AND CAST(script_sha256 AS BINARY) = _binary'2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__schema2-source-ledger.sql' AND CAST(script_sha256 AS BINARY) = _binary'4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__schema2-character-grant-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'9578db2143bfb14eb077e403d5395bb3089a7ab6e54f6926f42b7dbcac4e9269')) = 4
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('rule_schema2_class_progression', 'rule_schema2_resource', 'rule_schema2_advancement_change', 'rule_schema2_subclass', 'rule_schema2_feature', 'rule_schema2_feat', 'rule_schema2_proficiency', 'rule_schema2_proficiency_set', 'rule_schema2_class_level', 'rule_schema2_proficiency_member', 'rule_schema2_growth_grant', 'rule_schema2_ability_requirement', 'rule_schema2_rest')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v005_preflight;

CREATE TABLE rule_schema2_class_progression (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_class_module_key VARBINARY(64) NOT NULL,
    owner_class_rule_type VARBINARY(20) NOT NULL,
    owner_class_rule_key VARBINARY(128) NOT NULL,
    hit_die_sides INT NOT NULL,
    proficiency_bonus_profile VARCHAR(1000) NOT NULL,
    supported_maximum_level INT NOT NULL,
    advancement_scope VARBINARY(32) NOT NULL,
    multiclass_prerequisite_present TINYINT NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_source_progression_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_source_progression_identity (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_source_progression_shape CHECK (author_index BETWEEN 0 AND 255 AND module_key = artifact_module_key AND rule_type = _binary'character.class_progression' AND source_page BETWEEN 3 AND 74 AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^progression[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_source_progression_owner CHECK (owner_class_rule_type = _binary'character.class' AND REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_source_progression_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_source_progression_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_source_progression_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_source_progression_profile CHECK (hit_die_sides IN (6, 8, 10, 12) AND supported_maximum_level BETWEEN 1 AND 20 AND advancement_scope IN (_binary'STATISTICS_AND_RESOURCES_ONLY', _binary'CHARACTER_ADVANCEMENT') AND multiclass_prerequisite_present IN (0, 1) AND (advancement_scope <> _binary'CHARACTER_ADVANCEMENT' OR multiclass_prerequisite_present = 1) AND CHAR_LENGTH(proficiency_bonus_profile) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(proficiency_bonus_profile, '[[:cntrl:]]') AND REGEXP_LIKE(proficiency_bonus_profile, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_resource (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_class_module_key VARBINARY(64) NOT NULL,
    owner_class_rule_type VARBINARY(20) NOT NULL,
    owner_class_rule_key VARBINARY(128) NOT NULL,
    maximum_profile VARCHAR(1000) NOT NULL,
    recovery_profile VARCHAR(200) NOT NULL,
    execution_mode VARBINARY(9) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_source_resource_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_source_resource_identity (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_source_resource_shape CHECK (author_index BETWEEN 0 AND 255 AND module_key = artifact_module_key AND rule_type = _binary'character.resource' AND source_page BETWEEN 3 AND 74 AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^resource[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_source_resource_owner CHECK (owner_class_rule_type = _binary'character.class' AND REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_source_resource_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_source_resource_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_source_resource_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_source_resource_profile CHECK (execution_mode IN (_binary'AUTOMATIC', _binary'BLOCKED') AND CHAR_LENGTH(maximum_profile) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(maximum_profile, '[[:cntrl:]]') AND REGEXP_LIKE(maximum_profile, '[^[:space:]]') AND CHAR_LENGTH(recovery_profile) BETWEEN 1 AND 200 AND NOT REGEXP_LIKE(recovery_profile, '[[:cntrl:]]') AND REGEXP_LIKE(recovery_profile, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_subclass (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_class_module_key VARBINARY(64) NOT NULL,
    owner_class_rule_type VARBINARY(32) NOT NULL,
    owner_class_rule_key VARBINARY(128) NOT NULL,
    selection_level INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_subclass_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_subclass (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_src_subclass_0 CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'character.subclass' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^subclass[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_subclass_1 CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_subclass_2 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_subclass_3 CHECK (source_page BETWEEN 3 AND 74 AND CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_subclass_4 CHECK (owner_class_rule_type IN (_binary'character.class') AND REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_subclass_5 CHECK (selection_level BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_feature (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_module_key VARBINARY(64) NOT NULL,
    owner_rule_type VARBINARY(32) NOT NULL,
    owner_rule_key VARBINARY(128) NOT NULL,
    level INT NOT NULL,
    feature_kind VARBINARY(7) NOT NULL,
    execution_mode VARBINARY(15) NOT NULL,
    execution_algorithm VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_feature_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_feature (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_src_feature_0 CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'character.feature' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^feature[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_feature_1 CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_feature_2 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_feature_3 CHECK (source_page BETWEEN 3 AND 74 AND CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_feature_4 CHECK (owner_rule_type IN (_binary'character.class', _binary'character.subclass') AND REGEXP_LIKE(CONVERT(owner_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(owner_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_feature_5 CHECK (level BETWEEN 1 AND 20),
    CONSTRAINT ck_s2_src_feature_6 CHECK (feature_kind IN (_binary'FEATURE', _binary'OPTION')),
    CONSTRAINT ck_s2_src_feature_7 CHECK ((execution_mode = _binary'AUTOMATIC' AND execution_algorithm = _binary'AUTOMATIC_RESOURCE_LIFECYCLE_V1') OR (execution_mode = _binary'DM_ADJUDICATION' AND execution_algorithm IN (_binary'BOUNDED_SUBCLASS_SELECTION_V1', _binary'BOUNDED_FEATURE_SELECTION_V1', _binary'BOUNDED_DM_ADJUDICATION_V1')) OR (execution_mode = _binary'BLOCKED' AND execution_algorithm IN (_binary'BLOCKED_ISSUE_8_V1', _binary'BLOCKED_SPELL_SYSTEM_V1', _binary'BLOCKED_DOWNSTREAM_SYSTEM_V1')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_feat (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    execution_mode VARBINARY(15) NOT NULL,
    execution_algorithm VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_feat_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_feat (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_src_feat_0 CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'character.feat' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^feat[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_feat_1 CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_feat_2 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_feat_3 CHECK (source_page BETWEEN 3 AND 74 AND CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_feat_4 CHECK (execution_mode = _binary'BLOCKED' AND execution_algorithm = _binary'BLOCKED_DOWNSTREAM_SYSTEM_V1')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_proficiency (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    category VARBINARY(6) NOT NULL,
    ability_key VARBINARY(32) NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_proficiency_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_proficiency (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_src_proficiency_0 CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'character.proficiency' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^proficiency[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_proficiency_1 CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_proficiency_2 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_proficiency_3 CHECK (source_page BETWEEN 3 AND 74 AND CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_proficiency_4 CHECK (category IN (_binary'SKILL', _binary'ARMOR', _binary'WEAPON')),
    CONSTRAINT ck_s2_src_proficiency_5 CHECK ((category = _binary'SKILL' AND ability_key IS NOT NULL AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) OR (category <> _binary'SKILL' AND ability_key IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_proficiency_set (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    category VARBINARY(6) NOT NULL,
    minimum_enabled_members INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_proficiency_set_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_proficiency_set (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_src_proficiency_set_0 CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'catalog.proficiency_set' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^proficiency_set[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_proficiency_set_1 CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_proficiency_set_2 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_proficiency_set_3 CHECK (category IN (_binary'SKILL', _binary'ARMOR', _binary'WEAPON', _binary'TOOL')),
    CONSTRAINT ck_s2_src_proficiency_set_4 CHECK (minimum_enabled_members BETWEEN 1 AND 128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_class_level (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    level INT NOT NULL,
    asi_available TINYINT NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_class_level_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_class_level (artifact_module_key, release_version, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key),
    CONSTRAINT ck_s2_src_class_level_0 CHECK (author_index BETWEEN 0 AND 127 AND module_key = artifact_module_key AND rule_type = _binary'character.class_progression' AND collection_key IN (_binary'levels') AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^progression[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_]', 'c')),
    CONSTRAINT ck_s2_src_class_level_1 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_class_level_2 CHECK (level BETWEEN 1 AND 20),
    CONSTRAINT ck_s2_src_class_level_3 CHECK (asi_available IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_proficiency_member (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    proficiency_module_key VARBINARY(64) NOT NULL,
    proficiency_rule_type VARBINARY(32) NOT NULL,
    proficiency_rule_key VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_proficiency_member_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_proficiency_member (artifact_module_key, release_version, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key),
    CONSTRAINT ck_s2_src_proficiency_member_0 CHECK (author_index BETWEEN 0 AND 127 AND module_key = artifact_module_key AND rule_type = _binary'catalog.proficiency_set' AND collection_key IN (_binary'members') AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^proficiency_set[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_]', 'c')),
    CONSTRAINT ck_s2_src_proficiency_member_1 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_proficiency_member_2 CHECK (proficiency_rule_type IN (_binary'character.proficiency', _binary'character.tool') AND REGEXP_LIKE(CONVERT(proficiency_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(proficiency_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_rule_key USING latin1), '[^a-z0-9_.]', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_growth_grant (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    growth_grant_kind VARBINARY(6) NULL,
    growth_grant_target_module_key VARBINARY(64) NULL,
    growth_grant_target_rule_type VARBINARY(32) NULL,
    growth_grant_target_rule_key VARBINARY(128) NULL,
    growth_grant_count INT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_growth_grant_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_growth_grant (artifact_module_key, release_version, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key),
    CONSTRAINT ck_s2_src_growth_grant_0 CHECK (author_index BETWEEN 0 AND 127 AND module_key = artifact_module_key AND rule_type = _binary'character.class_progression' AND collection_key IN (_binary'starting_proficiencies', _binary'multiclass_proficiencies') AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^progression[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_]', 'c')),
    CONSTRAINT ck_s2_src_growth_grant_1 CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))),
    CONSTRAINT ck_s2_src_growth_grant_2 CHECK (growth_grant_kind IS NOT NULL AND growth_grant_target_module_key IS NOT NULL AND growth_grant_target_rule_type IS NOT NULL AND growth_grant_target_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(growth_grant_target_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(growth_grant_target_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(growth_grant_target_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(growth_grant_target_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((growth_grant_kind = _binary'FIXED' AND growth_grant_count IS NULL AND growth_grant_target_rule_type IN (_binary'character.proficiency', _binary'character.tool')) OR (growth_grant_kind = _binary'CHOICE' AND growth_grant_count IS NOT NULL AND growth_grant_count BETWEEN 1 AND 16 AND growth_grant_target_rule_type = _binary'catalog.proficiency_set')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_ability_requirement (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    author_kind VARBINARY(11) NOT NULL,
    group_index INT NOT NULL,
    alternative_index INT NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    minimum_value INT NOT NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index, author_kind, group_index, alternative_index),
    CONSTRAINT fk_s2_src_ability_requirement_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s2_src_ability_requirement (artifact_module_key, release_version, author_index, author_kind, ability_key),
    CONSTRAINT ck_s2_src_ability_requirement_0 CHECK (((author_kind = _binary'definitions' AND author_index BETWEEN 0 AND 63) OR (author_kind = _binary'changes' AND author_index BETWEEN 0 AND 255)) AND group_index BETWEEN 0 AND 5 AND alternative_index BETWEEN 0 AND 1),
    CONSTRAINT ck_s2_src_ability_requirement_1 CHECK (minimum_value BETWEEN 1 AND 20 AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_rest (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    algorithm VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    UNIQUE KEY uq_s2_src_rest (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT fk_s2_src_rest_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_src_rest_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'adventure.rest' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^rest[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_rest_profile CHECK (source_page = 87 AND algorithm = _binary'SRD_2014_REST_V1'),
    CONSTRAINT ck_s2_src_rest_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_rest_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_src_rest_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_advancement_change (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    target_kind VARBINARY(5) NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NULL,
    module_key VARBINARY(64) NULL,
    entry_key VARBINARY(64) NULL,
    operation VARBINARY(11) NOT NULL,
    field_key VARBINARY(32) NOT NULL,
    text_value VARCHAR(1000) NULL,
    integer_value INT NULL,
    boolean_value TINYINT NULL,
    reference_module_key VARBINARY(64) NULL,
    reference_rule_type VARBINARY(32) NULL,
    reference_rule_key VARBINARY(128) NULL,
    growth_grant_kind VARBINARY(6) NULL,
    growth_grant_target_module_key VARBINARY(64) NULL,
    growth_grant_target_rule_type VARBINARY(32) NULL,
    growth_grant_target_rule_key VARBINARY(128) NULL,
    growth_grant_count INT NULL,
    prerequisite_value TINYINT NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_src_advancement_change_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_src_advancement_change_0 CHECK (author_index BETWEEN 0 AND 255 AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_src_advancement_change_1 CHECK ((rule_type = _binary'character.class_progression' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^progression[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type = _binary'character.resource' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^resource[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type = _binary'character.subclass' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^subclass[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type = _binary'character.feature' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^feature[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type = _binary'character.feat' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^feat[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type = _binary'character.proficiency' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^proficiency[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type = _binary'catalog.proficiency_set' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^proficiency_set[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type = _binary'adventure.rest' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^rest[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c'))),
    CONSTRAINT ck_s2_src_advancement_change_2 CHECK ((target_kind = _binary'rule' AND collection_key IS NULL AND module_key IS NULL AND entry_key IS NULL AND ((rule_type = _binary'character.class_progression' AND field_key IN (_binary'display_name', _binary'author_note', _binary'enabled', _binary'description', _binary'source_page', _binary'owner_class', _binary'hit_die_sides', _binary'proficiency_bonus_profile', _binary'supported_maximum_level', _binary'advancement_scope', _binary'multiclass_prerequisite')) OR (rule_type = _binary'character.resource' AND field_key IN (_binary'display_name', _binary'author_note', _binary'enabled', _binary'description', _binary'source_page', _binary'owner_class', _binary'maximum_profile', _binary'recovery_profile', _binary'execution_mode')) OR (rule_type = _binary'character.subclass' AND field_key IN (_binary'display_name', _binary'author_note', _binary'enabled', _binary'description', _binary'source_page', _binary'owner_class', _binary'selection_level')) OR (rule_type = _binary'character.feature' AND field_key IN (_binary'display_name', _binary'author_note', _binary'enabled', _binary'description', _binary'source_page', _binary'owner', _binary'level', _binary'feature_kind', _binary'execution_mode', _binary'execution_algorithm')) OR (rule_type = _binary'character.feat' AND field_key IN (_binary'display_name', _binary'author_note', _binary'enabled', _binary'description', _binary'source_page', _binary'prerequisite', _binary'execution_mode', _binary'execution_algorithm')) OR (rule_type = _binary'character.proficiency' AND field_key IN (_binary'display_name', _binary'author_note', _binary'enabled', _binary'description', _binary'source_page', _binary'category', _binary'ability_key')) OR (rule_type = _binary'catalog.proficiency_set' AND field_key IN (_binary'display_name', _binary'author_note', _binary'enabled', _binary'category', _binary'minimum_enabled_members')) OR (rule_type = _binary'adventure.rest' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'algorithm', _binary'author_note', _binary'enabled')))) OR (target_kind = _binary'entry' AND collection_key IS NOT NULL AND module_key IS NOT NULL AND entry_key IS NOT NULL AND REGEXP_LIKE(CONVERT(module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_]', 'c') AND ((rule_type = _binary'character.class_progression' AND ((collection_key = _binary'levels' AND field_key IN (_binary'level', _binary'asi_available', _binary'author_note', _binary'enabled')) OR (collection_key IN (_binary'starting_proficiencies', _binary'multiclass_proficiencies') AND field_key IN (_binary'growth_grant', _binary'author_note', _binary'enabled')))) OR (rule_type = _binary'catalog.proficiency_set' AND collection_key = _binary'members' AND field_key IN (_binary'proficiency', _binary'author_note', _binary'enabled'))))),
    CONSTRAINT ck_s2_src_advancement_change_3 CHECK ((reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL) OR (reference_module_key IS NOT NULL AND reference_rule_type IS NOT NULL AND reference_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((field_key = _binary'owner_class' AND reference_rule_type = _binary'character.class') OR (field_key = _binary'owner' AND reference_rule_type IN (_binary'character.class', _binary'character.subclass')) OR (field_key = _binary'proficiency' AND reference_rule_type IN (_binary'character.proficiency', _binary'character.tool'))))),
    CONSTRAINT ck_s2_src_advancement_change_4 CHECK ((growth_grant_kind IS NULL AND growth_grant_target_module_key IS NULL AND growth_grant_target_rule_type IS NULL AND growth_grant_target_rule_key IS NULL AND growth_grant_count IS NULL) OR (growth_grant_kind IS NOT NULL AND growth_grant_target_module_key IS NOT NULL AND growth_grant_target_rule_type IS NOT NULL AND growth_grant_target_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(growth_grant_target_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(growth_grant_target_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(growth_grant_target_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(growth_grant_target_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((growth_grant_kind = _binary'FIXED' AND growth_grant_count IS NULL AND growth_grant_target_rule_type IN (_binary'character.proficiency', _binary'character.tool')) OR (growth_grant_kind = _binary'CHOICE' AND growth_grant_count IS NOT NULL AND growth_grant_count BETWEEN 1 AND 16 AND growth_grant_target_rule_type = _binary'catalog.proficiency_set')))),
    CONSTRAINT ck_s2_src_advancement_change_5 CHECK (text_value IS NULL OR (CHAR_LENGTH(text_value) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(text_value, '[[:cntrl:]]') AND REGEXP_LIKE(text_value, '[^[:space:]]') AND (field_key <> _binary'display_name' OR CHAR_LENGTH(text_value) <= 120) AND (field_key <> _binary'recovery_profile' OR CHAR_LENGTH(text_value) <= 200) AND (field_key <> _binary'execution_algorithm' OR CHAR_LENGTH(text_value) <= 128))),
    CONSTRAINT ck_s2_src_advancement_change_6 CHECK ((operation = _binary'clear_field' AND field_key = _binary'author_note' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL AND growth_grant_kind IS NULL AND prerequisite_value IS NULL) OR (operation = _binary'set_enabled' AND field_key = _binary'enabled' AND boolean_value IS NOT NULL AND boolean_value IN (0, 1) AND text_value IS NULL AND integer_value IS NULL AND reference_module_key IS NULL AND growth_grant_kind IS NULL AND prerequisite_value IS NULL) OR (operation = _binary'set_field' AND ((text_value IS NOT NULL) + (integer_value IS NOT NULL) + (boolean_value IS NOT NULL) + (reference_module_key IS NOT NULL) + (growth_grant_kind IS NOT NULL) + (prerequisite_value IS NOT NULL)) = 1 AND ((field_key IN (_binary'display_name', _binary'description', _binary'author_note', _binary'proficiency_bonus_profile', _binary'maximum_profile', _binary'recovery_profile') AND text_value IS NOT NULL) OR (field_key = _binary'advancement_scope' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'STATISTICS_AND_RESOURCES_ONLY', _binary'CHARACTER_ADVANCEMENT')) OR (field_key = _binary'execution_mode' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'AUTOMATIC', _binary'DM_ADJUDICATION', _binary'BLOCKED') AND (rule_type <> _binary'character.resource' OR CAST(text_value AS BINARY) <> _binary'DM_ADJUDICATION')) OR (field_key = _binary'execution_algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'AUTOMATIC_RESOURCE_LIFECYCLE_V1', _binary'BOUNDED_SUBCLASS_SELECTION_V1', _binary'BOUNDED_FEATURE_SELECTION_V1', _binary'BOUNDED_DM_ADJUDICATION_V1', _binary'BLOCKED_ISSUE_8_V1', _binary'BLOCKED_SPELL_SYSTEM_V1', _binary'BLOCKED_DOWNSTREAM_SYSTEM_V1')) OR (field_key = _binary'feature_kind' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'FEATURE', _binary'OPTION')) OR (field_key = _binary'category' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'SKILL', _binary'ARMOR', _binary'WEAPON', _binary'TOOL') AND (rule_type <> _binary'character.proficiency' OR CAST(text_value AS BINARY) <> _binary'TOOL')) OR (field_key = _binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) = _binary'SRD_2014_REST_V1') OR (field_key = _binary'ability_key' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) OR (field_key = _binary'source_page' AND integer_value IS NOT NULL AND ((rule_type = _binary'adventure.rest' AND integer_value = 87) OR (rule_type <> _binary'adventure.rest' AND integer_value BETWEEN 3 AND 74))) OR (field_key = _binary'hit_die_sides' AND integer_value IS NOT NULL AND integer_value IN (6, 8, 10, 12)) OR (field_key IN (_binary'supported_maximum_level', _binary'selection_level', _binary'level') AND integer_value IS NOT NULL AND integer_value BETWEEN 1 AND 20) OR (field_key = _binary'minimum_enabled_members' AND integer_value IS NOT NULL AND integer_value BETWEEN 1 AND 128) OR (field_key IN (_binary'owner_class', _binary'owner', _binary'proficiency') AND reference_module_key IS NOT NULL) OR (field_key = _binary'asi_available' AND boolean_value IS NOT NULL AND boolean_value IN (0, 1)) OR (field_key = _binary'growth_grant' AND growth_grant_kind IS NOT NULL) OR (field_key IN (_binary'multiclass_prerequisite', _binary'prerequisite') AND prerequisite_value IS NOT NULL AND prerequisite_value = 1))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


DELIMITER $$
CREATE TRIGGER rule_s2_progression_insert BEFORE INSERT ON rule_schema2_class_progression FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_class_progression WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Advancement material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_progression_update BEFORE UPDATE ON rule_schema2_class_progression FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 advancement material';
END$$
CREATE TRIGGER rule_s2_progression_delete BEFORE DELETE ON rule_schema2_class_progression FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 advancement material';
END$$
CREATE TRIGGER rule_s2_resource_insert BEFORE INSERT ON rule_schema2_resource FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_resource WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Advancement material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_resource_update BEFORE UPDATE ON rule_schema2_resource FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 advancement material';
END$$
CREATE TRIGGER rule_s2_resource_delete BEFORE DELETE ON rule_schema2_resource FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 advancement material';
END$$
CREATE TRIGGER rule_s2_advancement_insert BEFORE INSERT ON rule_schema2_advancement_change FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_advancement_change WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 4096 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Advancement material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_advancement_update BEFORE UPDATE ON rule_schema2_advancement_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 advancement material';
END$$
CREATE TRIGGER rule_s2_advancement_delete BEFORE DELETE ON rule_schema2_advancement_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 advancement material';
END$$
CREATE TRIGGER rule_s2_subclass_insert BEFORE INSERT ON rule_schema2_subclass FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_subclass WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_subclass_update BEFORE UPDATE ON rule_schema2_subclass FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_subclass_delete BEFORE DELETE ON rule_schema2_subclass FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_feature_insert BEFORE INSERT ON rule_schema2_feature FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_feature WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_feature_update BEFORE UPDATE ON rule_schema2_feature FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_feature_delete BEFORE DELETE ON rule_schema2_feature FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_feat_insert BEFORE INSERT ON rule_schema2_feat FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_feat WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_feat_update BEFORE UPDATE ON rule_schema2_feat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_feat_delete BEFORE DELETE ON rule_schema2_feat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_proficiency_insert BEFORE INSERT ON rule_schema2_proficiency FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_proficiency WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_proficiency_update BEFORE UPDATE ON rule_schema2_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_proficiency_delete BEFORE DELETE ON rule_schema2_proficiency FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_proficiency_set_insert BEFORE INSERT ON rule_schema2_proficiency_set FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_proficiency_set WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_proficiency_set_update BEFORE UPDATE ON rule_schema2_proficiency_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_proficiency_set_delete BEFORE DELETE ON rule_schema2_proficiency_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_class_level_insert BEFORE INSERT ON rule_schema2_class_level FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_class_level WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 512 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_class_level_update BEFORE UPDATE ON rule_schema2_class_level FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_class_level_delete BEFORE DELETE ON rule_schema2_class_level FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_proficiency_member_insert BEFORE INSERT ON rule_schema2_proficiency_member FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_proficiency_member WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 512 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_proficiency_member_update BEFORE UPDATE ON rule_schema2_proficiency_member FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_proficiency_member_delete BEFORE DELETE ON rule_schema2_proficiency_member FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_growth_grant_insert BEFORE INSERT ON rule_schema2_growth_grant FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_growth_grant WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 512 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_growth_grant_update BEFORE UPDATE ON rule_schema2_growth_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_growth_grant_delete BEFORE DELETE ON rule_schema2_growth_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_ability_requirement_insert BEFORE INSERT ON rule_schema2_ability_requirement FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_ability_requirement WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 1920 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Growth material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_ability_requirement_update BEFORE UPDATE ON rule_schema2_ability_requirement FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 growth material';
END$$
CREATE TRIGGER rule_s2_ability_requirement_delete BEFORE DELETE ON rule_schema2_ability_requirement FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 growth material';
END$$
CREATE TRIGGER rule_s2_rest_insert BEFORE INSERT ON rule_schema2_rest FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_rest WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rest material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_rest_update BEFORE UPDATE ON rule_schema2_rest FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 rest material';
END$$
CREATE TRIGGER rule_s2_rest_delete BEFORE DELETE ON rule_schema2_rest FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 rest material';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO rule_schema_meta (schema_version, script_name, script_sha256)
VALUES (5, 'V005__schema2-advancement-source.sql', '2dfdecf8cb30fcc71f7e011550ce3b32334340aab5ff546f4e71e53e13147e5c');
