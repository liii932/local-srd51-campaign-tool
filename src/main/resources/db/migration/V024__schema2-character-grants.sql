-- Typed INTERNAL character sources and stable proficiency slots. No game or installation eligibility.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v024_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v024_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 23
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0')) = 23
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('s2_runtime_character_source', 's2_runtime_proficiency_grant')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v024_preflight;

CREATE TABLE s2_runtime_character_source (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(20) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    parent_race_module_key VARBINARY(64) NULL,
    parent_race_rule_type VARBINARY(20) NULL,
    parent_race_rule_key VARBINARY(128) NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_type, rule_key),
    UNIQUE KEY uq_s2_character_source_index (snapshot_id, rule_index),
    CONSTRAINT fk_s2_character_source_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_character_source_parent FOREIGN KEY (snapshot_id, parent_race_module_key, parent_race_rule_type, parent_race_rule_key) REFERENCES s2_runtime_character_source (snapshot_id, module_key, rule_type, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_source_shape CHECK (rule_index BETWEEN 0 AND 255 AND rule_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND enabled IN (0, 1) AND source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_s2_character_source_parent CHECK ((rule_type = _binary'character.subrace' AND parent_race_module_key IS NOT NULL AND parent_race_rule_type IS NOT NULL AND parent_race_rule_key IS NOT NULL AND parent_race_rule_type = _binary'character.race') OR (rule_type <> _binary'character.subrace' AND parent_race_module_key IS NULL AND parent_race_rule_type IS NULL AND parent_race_rule_key IS NULL)),
    CONSTRAINT ck_s2_character_source_key CHECK (REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_character_source_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_character_source_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_character_source_note CHECK ((author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_proficiency_grant (
    snapshot_id VARBINARY(16) NOT NULL,
    entry_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(20) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(18) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    grant_category VARBINARY(8) NULL,
    grant_kind VARBINARY(6) NULL,
    grant_target_module_key VARBINARY(64) NULL,
    grant_target_rule_key VARBINARY(128) NULL,
    grant_count INT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_module_key, rule_type, rule_key, module_key, entry_key),
    UNIQUE KEY uq_s2_proficiency_grant_index (snapshot_id, entry_index),
    CONSTRAINT fk_s2_proficiency_grant_owner FOREIGN KEY (snapshot_id, rule_module_key, rule_type, rule_key) REFERENCES s2_runtime_character_source (snapshot_id, module_key, rule_type, rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_proficiency_grant_member FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_proficiency_grant_target_member FOREIGN KEY (snapshot_id, grant_target_module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_proficiency_grant_shape CHECK (entry_index BETWEEN 0 AND 511 AND collection_key = _binary'proficiency_grants' AND rule_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND enabled IN (0, 1) AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_]', 'c')),
    CONSTRAINT ck_s2_proficiency_grant_atom CHECK (grant_category IS NOT NULL AND grant_category IN (_binary'LANGUAGE', _binary'TOOL') AND grant_kind IS NOT NULL AND grant_target_module_key IS NOT NULL AND grant_target_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(grant_target_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(grant_target_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(grant_target_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(grant_target_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((grant_kind = _binary'FIXED' AND grant_count IS NULL) OR (grant_kind = _binary'CHOICE' AND grant_count IS NOT NULL AND grant_count BETWEEN 1 AND 16))),
    CONSTRAINT ck_s2_proficiency_grant_note CHECK ((author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE s2_runtime_field_chain MODIFY collection_key VARBINARY(18) NULL;
ALTER TABLE s2_runtime_field_chain DROP CHECK ck_s2_chain_target;
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
);

ALTER TABLE s2_runtime_chain_step ADD COLUMN before_grant_category VARBINARY(8) NULL,
    ADD COLUMN before_grant_kind VARBINARY(6) NULL,
    ADD COLUMN before_grant_target_module_key VARBINARY(64) NULL,
    ADD COLUMN before_grant_target_rule_key VARBINARY(128) NULL,
    ADD COLUMN before_grant_count INT NULL;
ALTER TABLE s2_runtime_chain_step DROP CHECK ck_s2_step_before;
ALTER TABLE s2_runtime_chain_step ADD CONSTRAINT ck_s2_step_before CHECK (
    (before_state IN (_binary'UNINITIALIZED', _binary'ABSENT') AND before_text_value IS NULL AND before_integer_value IS NULL AND before_boolean_value IS NULL AND before_reference_module_key IS NULL AND before_reference_rule_key IS NULL AND before_grant_category IS NULL AND before_grant_kind IS NULL AND before_grant_target_module_key IS NULL AND before_grant_target_rule_key IS NULL AND before_grant_count IS NULL)
    OR (before_state = _binary'PRESENT' AND ((before_text_value IS NOT NULL) + (before_integer_value IS NOT NULL) + (before_boolean_value IS NOT NULL) + (before_reference_module_key IS NOT NULL) + (before_grant_category IS NOT NULL)) = 1 AND ((before_reference_module_key IS NULL AND before_reference_rule_key IS NULL) OR (before_reference_module_key IS NOT NULL AND before_reference_rule_key IS NOT NULL))
        AND (before_boolean_value IS NULL OR before_boolean_value IN (0, 1)) AND ((before_grant_category IS NULL AND before_grant_kind IS NULL AND before_grant_target_module_key IS NULL AND before_grant_target_rule_key IS NULL AND before_grant_count IS NULL) OR (before_grant_category IS NOT NULL AND before_grant_category IN (_binary'LANGUAGE', _binary'TOOL') AND before_grant_kind IS NOT NULL AND before_grant_target_module_key IS NOT NULL AND before_grant_target_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(before_grant_target_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(before_grant_target_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(before_grant_target_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(before_grant_target_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((before_grant_kind = _binary'FIXED' AND before_grant_count IS NULL) OR (before_grant_kind = _binary'CHOICE' AND before_grant_count IS NOT NULL AND before_grant_count BETWEEN 1 AND 16)))))
);
ALTER TABLE s2_runtime_chain_step ADD COLUMN after_grant_category VARBINARY(8) NULL,
    ADD COLUMN after_grant_kind VARBINARY(6) NULL,
    ADD COLUMN after_grant_target_module_key VARBINARY(64) NULL,
    ADD COLUMN after_grant_target_rule_key VARBINARY(128) NULL,
    ADD COLUMN after_grant_count INT NULL;
ALTER TABLE s2_runtime_chain_step DROP CHECK ck_s2_step_after;
ALTER TABLE s2_runtime_chain_step ADD CONSTRAINT ck_s2_step_after CHECK (
    (after_state IN (_binary'UNINITIALIZED', _binary'ABSENT') AND after_text_value IS NULL AND after_integer_value IS NULL AND after_boolean_value IS NULL AND after_reference_module_key IS NULL AND after_reference_rule_key IS NULL AND after_grant_category IS NULL AND after_grant_kind IS NULL AND after_grant_target_module_key IS NULL AND after_grant_target_rule_key IS NULL AND after_grant_count IS NULL)
    OR (after_state = _binary'PRESENT' AND ((after_text_value IS NOT NULL) + (after_integer_value IS NOT NULL) + (after_boolean_value IS NOT NULL) + (after_reference_module_key IS NOT NULL) + (after_grant_category IS NOT NULL)) = 1 AND ((after_reference_module_key IS NULL AND after_reference_rule_key IS NULL) OR (after_reference_module_key IS NOT NULL AND after_reference_rule_key IS NOT NULL))
        AND (after_boolean_value IS NULL OR after_boolean_value IN (0, 1)) AND ((after_grant_category IS NULL AND after_grant_kind IS NULL AND after_grant_target_module_key IS NULL AND after_grant_target_rule_key IS NULL AND after_grant_count IS NULL) OR (after_grant_category IS NOT NULL AND after_grant_category IN (_binary'LANGUAGE', _binary'TOOL') AND after_grant_kind IS NOT NULL AND after_grant_target_module_key IS NOT NULL AND after_grant_target_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(after_grant_target_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(after_grant_target_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(after_grant_target_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(after_grant_target_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((after_grant_kind = _binary'FIXED' AND after_grant_count IS NULL) OR (after_grant_kind = _binary'CHOICE' AND after_grant_count IS NOT NULL AND after_grant_count BETWEEN 1 AND 16)))))
);

DELIMITER $$
CREATE TRIGGER s2_character_source_update BEFORE UPDATE ON s2_runtime_character_source FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character material';
END$$
CREATE TRIGGER s2_character_source_delete BEFORE DELETE ON s2_runtime_character_source FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 character material';
END$$
CREATE TRIGGER s2_proficiency_grant_update BEFORE UPDATE ON s2_runtime_proficiency_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character material';
END$$
CREATE TRIGGER s2_proficiency_grant_delete BEFORE DELETE ON s2_runtime_proficiency_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 character material';
END$$
CREATE TRIGGER s2_proficiency_grant_insert BEFORE INSERT ON s2_runtime_proficiency_grant FOR EACH ROW
BEGIN
    IF NOT ((NEW.grant_category = _binary'LANGUAGE' AND NEW.grant_kind = _binary'FIXED' AND EXISTS (SELECT 1 FROM s2_runtime_language WHERE snapshot_id = NEW.snapshot_id AND module_key = NEW.grant_target_module_key AND rule_key = NEW.grant_target_rule_key))
        OR (NEW.grant_category = _binary'LANGUAGE' AND NEW.grant_kind = _binary'CHOICE' AND EXISTS (SELECT 1 FROM s2_runtime_language_set WHERE snapshot_id = NEW.snapshot_id AND module_key = NEW.grant_target_module_key AND rule_key = NEW.grant_target_rule_key))
        OR (NEW.grant_category = _binary'TOOL' AND NEW.grant_kind = _binary'FIXED' AND EXISTS (SELECT 1 FROM s2_runtime_tool WHERE snapshot_id = NEW.snapshot_id AND module_key = NEW.grant_target_module_key AND rule_key = NEW.grant_target_rule_key))
        OR (NEW.grant_category = _binary'TOOL' AND NEW.grant_kind = _binary'CHOICE' AND EXISTS (SELECT 1 FROM s2_runtime_tool_set WHERE snapshot_id = NEW.snapshot_id AND module_key = NEW.grant_target_module_key AND rule_key = NEW.grant_target_rule_key))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing typed grant target in snapshot';
    END IF;
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (24, 'V024__schema2-character-grants.sql', '8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57');
