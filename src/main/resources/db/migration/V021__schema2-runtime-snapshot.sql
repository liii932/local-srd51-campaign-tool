-- Finite typed schema 2 INTERNAL snapshots. No campaign execution or full-base qualification.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v021_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v021_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0
        OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 20
    AND (SELECT COUNT(*) FROM schema_meta WHERE
        (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644')
        OR         (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8')
        OR         (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225')
        OR         (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f')
        OR         (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6')
        OR         (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b')
        OR         (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef')
        OR         (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709')
        OR         (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c')
        OR         (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87')
        OR         (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb')
        OR         (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2')
        OR         (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d')
        OR         (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361')
        OR         (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd')
        OR         (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda')
        OR         (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0')
        OR         (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a')
        OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173')
        OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1')) = 20
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name IN
            ('s2_runtime_identity', 's2_runtime_snapshot', 's2_runtime_member', 's2_runtime_language', 's2_runtime_language_set', 's2_runtime_language_entry', 's2_runtime_field_chain', 's2_runtime_chain_step', 's2_runtime_control', 's2_runtime_operation'))
    AND NOT EXISTS (SELECT 1 FROM information_schema.triggers
        WHERE trigger_schema = DATABASE() AND trigger_name IN
            ('s2_identity_update', 's2_identity_delete', 's2_snapshot_update', 's2_snapshot_delete', 's2_member_update', 's2_member_delete', 's2_language_update', 's2_language_delete', 's2_language_set_update', 's2_language_set_delete', 's2_language_entry_update', 's2_language_entry_delete', 's2_field_chain_update', 's2_field_chain_delete', 's2_chain_step_update', 's2_chain_step_delete', 's2_operation_update', 's2_operation_delete', 's2_identity_time', 's2_snapshot_time', 's2_operation_time', 's2_control_update', 's2_control_delete'))
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v021_preflight;

CREATE TABLE s2_runtime_identity (
    run_id VARBINARY(16) NOT NULL,
    snapshot_id VARBINARY(16) NOT NULL,
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (run_id),
    UNIQUE KEY uq_s2_identity_snapshot (snapshot_id),
    UNIQUE KEY uq_s2_identity_pair (run_id, snapshot_id),
    CONSTRAINT ck_s2_identity_uuid CHECK (OCTET_LENGTH(run_id) = 16 AND (ORD(SUBSTRING(run_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(run_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(snapshot_id) = 16 AND (ORD(SUBSTRING(snapshot_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(snapshot_id, 9, 1)) & 192) = 128 AND run_id <> snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_snapshot (
    snapshot_id VARBINARY(16) NOT NULL,
    run_id VARBINARY(16) NOT NULL,
    material_scope VARBINARY(8) NOT NULL,
    canonical_profile VARBINARY(25) NOT NULL,
    composition_algorithm VARBINARY(31) NOT NULL,
    composition_sha256 VARBINARY(64) NOT NULL,
    final_rules_sha256 VARBINARY(64) NOT NULL,
    chain_sha256 VARBINARY(64) NOT NULL,
    member_count INT NOT NULL,
    rule_count INT NOT NULL,
    entry_count INT NOT NULL,
    chain_count INT NOT NULL,
    step_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uq_s2_snapshot_run (run_id),
    UNIQUE KEY uq_s2_snapshot_pair (run_id, snapshot_id),
    CONSTRAINT fk_s2_snapshot_run_id FOREIGN KEY (run_id, snapshot_id) REFERENCES s2_runtime_identity (run_id, snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_snapshot_profile CHECK (material_scope = _binary'INTERNAL' AND canonical_profile = _binary'schema2-production-json-1' AND composition_algorithm = _binary'schema2-production-projection-1'),
    CONSTRAINT ck_s2_snapshot_digests CHECK (OCTET_LENGTH(composition_sha256) = 64 AND NOT REGEXP_LIKE(CONVERT(composition_sha256 USING latin1), '[^0-9a-f]') AND OCTET_LENGTH(final_rules_sha256) = 64 AND NOT REGEXP_LIKE(CONVERT(final_rules_sha256 USING latin1), '[^0-9a-f]') AND OCTET_LENGTH(chain_sha256) = 64 AND NOT REGEXP_LIKE(CONVERT(chain_sha256 USING latin1), '[^0-9a-f]')),
    CONSTRAINT ck_s2_snapshot_counts CHECK (member_count BETWEEN 1 AND 4 AND rule_count BETWEEN 0 AND 256 AND entry_count BETWEEN 0 AND 512 AND chain_count BETWEEN 0 AND 3072 AND step_count BETWEEN 0 AND 4096)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_member (
    snapshot_id VARBINARY(16) NOT NULL,
    load_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    role VARBINARY(13) NOT NULL,
    raw_zip_sha256 VARBINARY(64) NOT NULL,
    logical_sha256 VARBINARY(64) NOT NULL,
    PRIMARY KEY (snapshot_id, load_index),
    UNIQUE KEY uq_s2_member_module (snapshot_id, module_key),
    CONSTRAINT fk_s2_member_snapshot_id FOREIGN KEY (snapshot_id) REFERENCES s2_runtime_snapshot (snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_member_order CHECK (load_index BETWEEN 0 AND 3 AND ((load_index = 0 AND role = _binary'BASE') OR (load_index > 0 AND role IN (_binary'EXTENSION', _binary'COMPATIBILITY')))),
    CONSTRAINT ck_s2_member_digests CHECK (OCTET_LENGTH(raw_zip_sha256) = 64 AND NOT REGEXP_LIKE(CONVERT(raw_zip_sha256 USING latin1), '[^0-9a-f]') AND OCTET_LENGTH(logical_sha256) = 64 AND NOT REGEXP_LIKE(CONVERT(logical_sha256 USING latin1), '[^0-9a-f]')),
    CONSTRAINT ck_s2_member_identifiers CHECK (REGEXP_LIKE(CONVERT(module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND REGEXP_LIKE(CONVERT(release_version USING latin1), '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_language (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARBINARY(8) NOT NULL,
    source_page INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_language_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_language_snapshot_id FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_language_index CHECK (rule_index BETWEEN 0 AND 255),
    CONSTRAINT ck_s2_language_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_s2_language_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c')),
    CONSTRAINT ck_s2_language_page CHECK (source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_language_category CHECK (category IN (_binary'STANDARD', _binary'EXOTIC', _binary'SECRET')),
    CONSTRAINT ck_s2_language_display_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_language_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_language_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_language_set (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    minimum_enabled_members INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_language_set_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_language_set_snapshot_id FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_language_set_index CHECK (rule_index BETWEEN 0 AND 255),
    CONSTRAINT ck_s2_language_set_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_s2_language_set_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c')),
    CONSTRAINT ck_s2_language_set_minimum CHECK (minimum_enabled_members BETWEEN 1 AND 128),
    CONSTRAINT ck_s2_language_set_display_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_language_set_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_language_entry (
    snapshot_id VARBINARY(16) NOT NULL,
    entry_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(7) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    language_module_key VARBINARY(64) NOT NULL,
    language_rule_key VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_module_key, rule_key, module_key, entry_key),
    UNIQUE KEY uq_s2_entry_index (snapshot_id, entry_index),
    CONSTRAINT fk_s2_entry_owner_snapshot_id FOREIGN KEY (snapshot_id, rule_module_key, rule_key) REFERENCES s2_runtime_language_set (snapshot_id, module_key, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_entry_language_snapshot_id FOREIGN KEY (snapshot_id, language_module_key, language_rule_key) REFERENCES s2_runtime_language (snapshot_id, module_key, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_entry_member_snapshot_id FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_entry_shape CHECK (entry_index BETWEEN 0 AND 511 AND collection_key = _binary'members' AND enabled IN (0, 1) AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c')),
    CONSTRAINT ck_s2_entry_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_field_chain (
    snapshot_id VARBINARY(16) NOT NULL,
    chain_index INT NOT NULL,
    target_kind VARBINARY(5) NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(20) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(7) NULL,
    entry_module_key VARBINARY(64) NULL,
    entry_key VARBINARY(64) NULL,
    field_key VARBINARY(23) NOT NULL,
    step_count INT NOT NULL,
    PRIMARY KEY (snapshot_id, chain_index),
    CONSTRAINT fk_s2_chain_snapshot_id FOREIGN KEY (snapshot_id) REFERENCES s2_runtime_snapshot (snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_chain_budget CHECK (chain_index BETWEEN 0 AND 3071 AND step_count BETWEEN 1 AND 4),
    CONSTRAINT ck_s2_chain_target CHECK ((target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND ((rule_type = _binary'character.language' AND field_key IN (_binary'display_name', _binary'description', _binary'category', _binary'source_page', _binary'author_note', _binary'enabled')) OR (rule_type = _binary'catalog.language_set' AND field_key IN (_binary'display_name', _binary'minimum_enabled_members', _binary'author_note', _binary'enabled')))) OR (target_kind = _binary'entry' AND rule_type = _binary'catalog.language_set' AND collection_key = _binary'members' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'language', _binary'author_note', _binary'enabled')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_chain_step (
    snapshot_id VARBINARY(16) NOT NULL,
    chain_index INT NOT NULL,
    step_index INT NOT NULL,
    operation VARBINARY(11) NOT NULL,
    provider_module_key VARBINARY(64) NOT NULL,
    provider_release_version VARBINARY(64) NOT NULL,
    load_index INT NOT NULL,
    raw_zip_sha256 VARBINARY(64) NOT NULL,
    author_path VARBINARY(240) NOT NULL,
    author_pointer VARCHAR(4096) NOT NULL,
    origin VARBINARY(16) NOT NULL,
    before_state VARBINARY(13) NOT NULL,
    before_text_value VARCHAR(1000) NULL,
    before_integer_value INT NULL,
    before_boolean_value TINYINT NULL,
    before_reference_module_key VARBINARY(64) NULL,
    before_reference_rule_key VARBINARY(128) NULL,
    CONSTRAINT ck_s2_step_before CHECK ((before_state IN (_binary'UNINITIALIZED', _binary'ABSENT') AND before_text_value IS NULL AND before_integer_value IS NULL AND before_boolean_value IS NULL AND before_reference_module_key IS NULL AND before_reference_rule_key IS NULL) OR (before_state = _binary'PRESENT' AND ((before_text_value IS NOT NULL) + (before_integer_value IS NOT NULL) + (before_boolean_value IS NOT NULL) + (before_reference_module_key IS NOT NULL)) = 1 AND ((before_reference_module_key IS NULL AND before_reference_rule_key IS NULL) OR (before_reference_module_key IS NOT NULL AND before_reference_rule_key IS NOT NULL)) AND (before_boolean_value IS NULL OR before_boolean_value IN (0, 1)))),
    after_state VARBINARY(13) NOT NULL,
    after_text_value VARCHAR(1000) NULL,
    after_integer_value INT NULL,
    after_boolean_value TINYINT NULL,
    after_reference_module_key VARBINARY(64) NULL,
    after_reference_rule_key VARBINARY(128) NULL,
    CONSTRAINT ck_s2_step_after CHECK ((after_state IN (_binary'UNINITIALIZED', _binary'ABSENT') AND after_text_value IS NULL AND after_integer_value IS NULL AND after_boolean_value IS NULL AND after_reference_module_key IS NULL AND after_reference_rule_key IS NULL) OR (after_state = _binary'PRESENT' AND ((after_text_value IS NOT NULL) + (after_integer_value IS NOT NULL) + (after_boolean_value IS NOT NULL) + (after_reference_module_key IS NOT NULL)) = 1 AND ((after_reference_module_key IS NULL AND after_reference_rule_key IS NULL) OR (after_reference_module_key IS NOT NULL AND after_reference_rule_key IS NOT NULL)) AND (after_boolean_value IS NULL OR after_boolean_value IN (0, 1)))),
    PRIMARY KEY (snapshot_id, chain_index, step_index),
    CONSTRAINT fk_s2_step_snapshot_id FOREIGN KEY (snapshot_id, chain_index) REFERENCES s2_runtime_field_chain (snapshot_id, chain_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_step_member_snapshot_id FOREIGN KEY (snapshot_id, load_index) REFERENCES s2_runtime_member (snapshot_id, load_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_step_shape CHECK (step_index BETWEEN 0 AND 3 AND load_index BETWEEN 0 AND 3 AND after_state <> _binary'UNINITIALIZED' AND ((step_index = 0 AND operation = _binary'initial' AND before_state = _binary'UNINITIALIZED' AND origin IN (_binary'declared', _binary'omitted_optional', _binary'default_enabled')) OR (step_index > 0 AND operation IN (_binary'set_field', _binary'clear_field', _binary'set_enabled') AND before_state <> _binary'UNINITIALIZED' AND origin = _binary'explicit'))),
    CONSTRAINT ck_s2_step_digest CHECK (OCTET_LENGTH(raw_zip_sha256) = 64 AND NOT REGEXP_LIKE(CONVERT(raw_zip_sha256 USING latin1), '[^0-9a-f]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_control (
    control_id INT NOT NULL,
    protocol_version INT NOT NULL,
    row_version BIGINT NOT NULL,
    snapshot_count INT NOT NULL,
    PRIMARY KEY (control_id),
    CONSTRAINT ck_s2_control CHECK (control_id = 1 AND protocol_version = 1 AND row_version BETWEEN 0 AND 16 AND snapshot_count BETWEEN 0 AND 16 AND row_version = snapshot_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

INSERT INTO s2_runtime_control (control_id, protocol_version, row_version, snapshot_count) VALUES (1, 1, 0, 0);

CREATE TABLE s2_runtime_operation (
    operation_id VARBINARY(16) NOT NULL,
    operation_kind VARBINARY(7) NOT NULL,
    operation_digest_sha256 VARBINARY(64) NOT NULL,
    run_id VARBINARY(16) NOT NULL,
    snapshot_id VARBINARY(16) NOT NULL,
    operation_sequence BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (operation_id),
    UNIQUE KEY uq_s2_operation_run (run_id),
    UNIQUE KEY uq_s2_operation_snapshot (snapshot_id),
    UNIQUE KEY uq_s2_operation_sequence (operation_sequence),
    CONSTRAINT fk_s2_operation_run_id FOREIGN KEY (run_id, snapshot_id) REFERENCES s2_runtime_identity (run_id, snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_operation_uuid CHECK (OCTET_LENGTH(operation_id) = 16 AND (ORD(SUBSTRING(operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(operation_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(run_id) = 16 AND (ORD(SUBSTRING(run_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(run_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(snapshot_id) = 16 AND (ORD(SUBSTRING(snapshot_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(snapshot_id, 9, 1)) & 192) = 128 AND run_id <> snapshot_id AND operation_id <> run_id AND operation_id <> snapshot_id),
    CONSTRAINT ck_s2_operation_digest CHECK (OCTET_LENGTH(operation_digest_sha256) = 64 AND NOT REGEXP_LIKE(CONVERT(operation_digest_sha256 USING latin1), '[^0-9a-f]')),
    CONSTRAINT ck_s2_operation_kind CHECK (operation_kind IN (_binary'CAPTURE', _binary'RESTORE')),
    CONSTRAINT ck_s2_operation_sequence CHECK (operation_sequence BETWEEN 1 AND 16)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER s2_identity_update BEFORE UPDATE ON s2_runtime_identity FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_identity_delete BEFORE DELETE ON s2_runtime_identity FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_snapshot_update BEFORE UPDATE ON s2_runtime_snapshot FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_snapshot_delete BEFORE DELETE ON s2_runtime_snapshot FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_member_update BEFORE UPDATE ON s2_runtime_member FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_member_delete BEFORE DELETE ON s2_runtime_member FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_language_update BEFORE UPDATE ON s2_runtime_language FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_language_delete BEFORE DELETE ON s2_runtime_language FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_language_set_update BEFORE UPDATE ON s2_runtime_language_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_language_set_delete BEFORE DELETE ON s2_runtime_language_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_language_entry_update BEFORE UPDATE ON s2_runtime_language_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_language_entry_delete BEFORE DELETE ON s2_runtime_language_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_field_chain_update BEFORE UPDATE ON s2_runtime_field_chain FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_field_chain_delete BEFORE DELETE ON s2_runtime_field_chain FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_chain_step_update BEFORE UPDATE ON s2_runtime_chain_step FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_chain_step_delete BEFORE DELETE ON s2_runtime_chain_step FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_operation_update BEFORE UPDATE ON s2_runtime_operation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_operation_delete BEFORE DELETE ON s2_runtime_operation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_identity_time BEFORE INSERT ON s2_runtime_identity FOR EACH ROW
BEGIN
    SET NEW.registered_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER s2_snapshot_time BEFORE INSERT ON s2_runtime_snapshot FOR EACH ROW
BEGIN
    SET NEW.created_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER s2_operation_time BEFORE INSERT ON s2_runtime_operation FOR EACH ROW
BEGIN
    SET NEW.created_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER s2_control_update BEFORE UPDATE ON s2_runtime_control FOR EACH ROW
BEGIN
    IF NEW.control_id <> OLD.control_id OR NEW.protocol_version <> OLD.protocol_version
        OR NEW.row_version <> OLD.row_version + 1 OR NEW.snapshot_count <> OLD.snapshot_count + 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid schema 2 control transition';
    END IF;
END$$
CREATE TRIGGER s2_control_delete BEFORE DELETE ON s2_runtime_control FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema 2 control';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version, script_name, script_sha256, description)
VALUES (21, 'V021__schema2-runtime-snapshot.sql', '4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99', 'Finite INTERNAL schema 2 typed runtime snapshots');
