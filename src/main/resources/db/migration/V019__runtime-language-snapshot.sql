-- Runtime language partition storage only. No production initialization or activation.
-- Connect explicitly to dnd_tool_se with strict SQL mode and utf8mb4; stop on the first error.
-- Apply once in an authorized maintenance boundary after complete V001--V018 verification.
-- DDL implicitly commits: partial failure requires reviewed repair, never blind replay.
-- CHECKSUM-SCOPE-BEGIN
-- A temporary assertion rejects wrong targets/history before any persistent DDL.
CREATE TEMPORARY TABLE v019_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v019_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0
        OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 18
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
            AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a')) = 18
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name IN
            ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language'))
    AND NOT EXISTS (SELECT 1 FROM information_schema.triggers
        WHERE trigger_schema = DATABASE() AND trigger_name IN
            ('runtime_identity_insert', 'runtime_identity_update', 'runtime_identity_delete',
             'runtime_snapshot_insert', 'runtime_snapshot_update', 'runtime_language_update'))
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v019_preflight;

CREATE TABLE runtime_run_identity (
    run_id VARBINARY(16) NOT NULL PRIMARY KEY,
    snapshot_id VARBINARY(16) NOT NULL,
    registered_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_runtime_identity_snapshot (snapshot_id),
    UNIQUE KEY uq_runtime_identity_pair (run_id, snapshot_id),
    CONSTRAINT ck_runtime_identity_run CHECK (OCTET_LENGTH(run_id) = 16
        AND (ORD(SUBSTRING(run_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(run_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_runtime_identity_snapshot CHECK (OCTET_LENGTH(snapshot_id) = 16
        AND (ORD(SUBSTRING(snapshot_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(snapshot_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_runtime_identity_distinct CHECK (run_id <> snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE runtime_rule_snapshot (
    snapshot_id VARBINARY(16) NOT NULL PRIMARY KEY,
    run_id VARBINARY(16) NOT NULL,
    module_key VARBINARY(128) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    canonical_format_version INT NOT NULL,
    archive_format_version INT NOT NULL,
    hash_algorithm VARBINARY(7) NOT NULL,
    release_status VARBINARY(8) NOT NULL,
    material_scope VARBINARY(9) NOT NULL,
    content_sha256 VARBINARY(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    UNIQUE KEY uq_runtime_snapshot_run (run_id),
    UNIQUE KEY uq_runtime_snapshot_pair (run_id, snapshot_id),
    CONSTRAINT fk_runtime_snapshot_identity FOREIGN KEY (run_id, snapshot_id)
        REFERENCES runtime_run_identity (run_id, snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_runtime_snapshot_run CHECK (OCTET_LENGTH(run_id) = 16
        AND (ORD(SUBSTRING(run_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(run_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_runtime_snapshot_id CHECK (OCTET_LENGTH(snapshot_id) = 16
        AND (ORD(SUBSTRING(snapshot_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(snapshot_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_runtime_snapshot_key CHECK (OCTET_LENGTH(module_key) BETWEEN 1 AND 128
        AND REGEXP_LIKE(CONVERT(module_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')
        AND NOT REGEXP_LIKE(CONVERT(module_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_runtime_snapshot_version CHECK (OCTET_LENGTH(release_version) BETWEEN 1 AND 64
        AND REGEXP_LIKE(CONVERT(release_version USING latin1), '^[A-Za-z0-9]', 'c')
        AND NOT REGEXP_LIKE(CONVERT(release_version USING latin1), '[^A-Za-z0-9._-]', 'c')),
    CONSTRAINT ck_runtime_snapshot_formats CHECK (canonical_format_version > 0 AND archive_format_version > 0),
    CONSTRAINT ck_runtime_snapshot_algorithm CHECK (hash_algorithm = _binary'SHA-256'),
    CONSTRAINT ck_runtime_snapshot_status CHECK (release_status IN (_binary'DRAFT', _binary'RELEASED')),
    CONSTRAINT ck_runtime_snapshot_digest CHECK (content_sha256 IS NULL OR
        (OCTET_LENGTH(content_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(content_sha256 USING latin1), '[^0-9a-f]', 'c'))),
    CONSTRAINT ck_runtime_snapshot_scope CHECK (
        (material_scope = _binary'PARTITION' AND release_status = _binary'DRAFT' AND content_sha256 IS NULL)
        OR (material_scope = _binary'COMPLETE' AND content_sha256 IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE runtime_rule_language (
    snapshot_id VARBINARY(16) NOT NULL,
    language_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARBINARY(8) NOT NULL,
    source_page SMALLINT NOT NULL,
    sort_order SMALLINT NOT NULL,
    PRIMARY KEY (snapshot_id, language_key),
    UNIQUE KEY uq_runtime_language_sort (snapshot_id, sort_order),
    CONSTRAINT fk_runtime_language_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES runtime_rule_snapshot (snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_runtime_language_snapshot CHECK (OCTET_LENGTH(snapshot_id) = 16
        AND (ORD(SUBSTRING(snapshot_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(snapshot_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_runtime_language_category CHECK (
        (category = _binary'STANDARD' AND language_key IN (_binary'language.common', _binary'language.dwarvish',
            _binary'language.elvish', _binary'language.giant', _binary'language.gnomish', _binary'language.goblin',
            _binary'language.halfling', _binary'language.orc'))
        OR (category = _binary'EXOTIC' AND language_key IN (_binary'language.abyssal', _binary'language.celestial',
            _binary'language.deep_speech', _binary'language.draconic', _binary'language.infernal',
            _binary'language.primordial', _binary'language.sylvan', _binary'language.undercommon'))
        OR (category = _binary'SECRET' AND language_key IN (_binary'language.druidic', _binary'language.thieves_cant'))),
    CONSTRAINT ck_runtime_language_page CHECK (source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_runtime_language_sort CHECK (sort_order BETWEEN 1 AND 18),
    CONSTRAINT ck_runtime_language_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120
        AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]')
        AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_runtime_language_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000
        AND NOT REGEXP_LIKE(description, '[[:cntrl:]]')
        AND REGEXP_LIKE(description, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER runtime_identity_insert BEFORE INSERT ON runtime_run_identity FOR EACH ROW
BEGIN
    SET NEW.registered_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER runtime_identity_update BEFORE UPDATE ON runtime_run_identity FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent runtime identity';
END$$
CREATE TRIGGER runtime_identity_delete BEFORE DELETE ON runtime_run_identity FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent runtime identity';
END$$
CREATE TRIGGER runtime_snapshot_insert BEFORE INSERT ON runtime_rule_snapshot FOR EACH ROW
BEGIN
    SET NEW.created_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER runtime_snapshot_update BEFORE UPDATE ON runtime_rule_snapshot FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable runtime snapshot';
END$$
CREATE TRIGGER runtime_language_update BEFORE UPDATE ON runtime_rule_language FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable runtime language';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

-- Record last, only after every statement and the separately reviewed object checks succeed.
-- These checks do not make DDL atomic or prove the full definitions/privileges.
INSERT INTO schema_meta (schema_version, script_name, script_sha256, description)
SELECT 19, 'V019__runtime-language-snapshot.sql', '6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173',
    CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
        AND (SELECT COUNT(*) FROM schema_meta) = 18
        AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
            AND table_type = 'BASE TABLE' AND engine = 'InnoDB'
            AND table_name IN ('runtime_run_identity', 'runtime_rule_snapshot', 'runtime_rule_language')) = 3
        AND (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = DATABASE()
            AND trigger_name IN ('runtime_identity_insert', 'runtime_identity_update', 'runtime_identity_delete',
                'runtime_snapshot_insert', 'runtime_snapshot_update', 'runtime_language_update')) = 6
        AND (SELECT COUNT(*) FROM runtime_run_identity) = 0
        AND (SELECT COUNT(*) FROM runtime_rule_snapshot) = 0
        AND (SELECT COUNT(*) FROM runtime_rule_language) = 0
    THEN 'Runtime DRAFT language partition storage' ELSE NULL END;
