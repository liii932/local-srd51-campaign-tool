-- Typed schema 2 tools and tool sets; preserves INTERNAL and all production eligibility gates.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v023_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v023_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0
        OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 22
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
            AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1')
        OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99')
        OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b')) = 22
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
        AND table_name IN ('s2_runtime_tool', 's2_runtime_tool_set', 's2_runtime_tool_entry'))
    AND NOT EXISTS (SELECT 1 FROM information_schema.triggers WHERE trigger_schema = DATABASE()
        AND trigger_name IN ('s2_tool_update', 's2_tool_delete', 's2_tool_set_update', 's2_tool_set_delete', 's2_tool_entry_update', 's2_tool_entry_delete'))
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v023_preflight;

CREATE TABLE s2_runtime_tool (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARBINARY(18) NOT NULL,
    source_page INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_tool_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_tool_snapshot_id FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_tool_index CHECK (rule_index BETWEEN 0 AND 255),
    CONSTRAINT ck_s2_tool_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_s2_tool_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c')),
    CONSTRAINT ck_s2_tool_page CHECK (source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_tool_category CHECK (category IN (_binary'ARTISAN', _binary'KIT', _binary'GAMING_SET', _binary'MUSICAL_INSTRUMENT', _binary'NAVIGATION', _binary'VEHICLE')),
    CONSTRAINT ck_s2_tool_display_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_tool_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_tool_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_tool_set (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    minimum_enabled_members INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_s2_tool_set_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_tool_set_snapshot_id FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_tool_set_index CHECK (rule_index BETWEEN 0 AND 255),
    CONSTRAINT ck_s2_tool_set_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_s2_tool_set_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c')),
    CONSTRAINT ck_s2_tool_set_minimum CHECK (minimum_enabled_members BETWEEN 1 AND 128),
    CONSTRAINT ck_s2_tool_set_display_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_tool_set_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_tool_entry (
    snapshot_id VARBINARY(16) NOT NULL,
    entry_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(7) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    tool_module_key VARBINARY(64) NOT NULL,
    tool_rule_key VARBINARY(128) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_module_key, rule_key, module_key, entry_key),
    UNIQUE KEY uq_s2_tool_entry_index (snapshot_id, entry_index),
    CONSTRAINT fk_s2_tool_entry_owner_snapshot_id FOREIGN KEY (snapshot_id, rule_module_key, rule_key) REFERENCES s2_runtime_tool_set (snapshot_id, module_key, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_tool_entry_tool_snapshot_id FOREIGN KEY (snapshot_id, tool_module_key, tool_rule_key) REFERENCES s2_runtime_tool (snapshot_id, module_key, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_tool_entry_member_snapshot_id FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_tool_entry_shape CHECK (entry_index BETWEEN 0 AND 511 AND collection_key = _binary'members' AND enabled IN (0, 1) AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c')),
    CONSTRAINT ck_s2_tool_entry_note CHECK (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE s2_runtime_field_chain DROP CHECK ck_s2_chain_target;
ALTER TABLE s2_runtime_field_chain ADD CONSTRAINT ck_s2_chain_target CHECK (
    (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL
        AND ((rule_type IN (_binary'character.language', _binary'character.tool')
            AND field_key IN (_binary'display_name', _binary'description', _binary'category', _binary'source_page', _binary'author_note', _binary'enabled'))
        OR (rule_type IN (_binary'catalog.language_set', _binary'catalog.tool_set')
            AND field_key IN (_binary'display_name', _binary'minimum_enabled_members', _binary'author_note', _binary'enabled'))))
    OR (target_kind = _binary'entry' AND collection_key = _binary'members' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL
        AND ((rule_type = _binary'catalog.language_set' AND field_key IN (_binary'language', _binary'author_note', _binary'enabled'))
            OR (rule_type = _binary'catalog.tool_set' AND field_key IN (_binary'tool', _binary'author_note', _binary'enabled'))))
);

DELIMITER $$
CREATE TRIGGER s2_tool_update BEFORE UPDATE ON s2_runtime_tool FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_tool_delete BEFORE DELETE ON s2_runtime_tool FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_tool_set_update BEFORE UPDATE ON s2_runtime_tool_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_tool_set_delete BEFORE DELETE ON s2_runtime_tool_set FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_tool_entry_update BEFORE UPDATE ON s2_runtime_tool_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
CREATE TRIGGER s2_tool_entry_delete BEFORE DELETE ON s2_runtime_tool_entry FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL schema 2 material';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version, script_name, script_sha256, description)
VALUES (23, 'V023__schema2-runtime-tools.sql', '7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0', 'Typed schema 2 tool snapshot rows and field chains');
