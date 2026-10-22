-- Original schema 2 character-source declarations/operations. No executable business state.
-- Preserves prior original artifacts; this migration never guesses typed values from ZIPs.
-- The complete reader must reject any new-domain ZIP missing its equivalent typed rows.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v004_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO rules_v004_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM rule_schema_meta) = 3
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__rule-source-schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__tool-catalog.sql' AND CAST(script_sha256 AS BINARY) = _binary'2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__schema2-source-ledger.sql' AND CAST(script_sha256 AS BINARY) = _binary'4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf')) = 3
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('rule_schema2_character_source', 'rule_schema2_proficiency_grant', 'rule_schema2_character_change')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v004_preflight;

CREATE TABLE rule_schema2_character_source (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(20) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    parent_race_module_key VARBINARY(64) NULL,
    parent_race_rule_key VARBINARY(128) NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_source_character_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_source_character_index CHECK (author_index BETWEEN 0 AND 255),
    UNIQUE KEY uq_s2_source_character_identity (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT ck_s2_source_character_shape CHECK (module_key = artifact_module_key AND rule_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND source_page BETWEEN 3 AND 74 AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_source_character_parent CHECK ((rule_type = _binary'character.subrace' AND parent_race_module_key IS NOT NULL AND parent_race_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(parent_race_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(parent_race_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(parent_race_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(parent_race_rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type <> _binary'character.subrace' AND parent_race_module_key IS NULL AND parent_race_rule_key IS NULL)),
    CONSTRAINT ck_s2_source_character_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_s2_source_character_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')),
    CONSTRAINT ck_s2_source_character_note CHECK ((author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_proficiency_grant (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
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
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_source_grant_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_source_grant_index CHECK (author_index BETWEEN 0 AND 511),
    UNIQUE KEY uq_s2_source_grant_identity (artifact_module_key, release_version, rule_module_key, rule_type, rule_key, module_key, entry_key),
    CONSTRAINT ck_s2_source_grant_owner CHECK (module_key = artifact_module_key AND rule_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND collection_key = _binary'proficiency_grants' AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_]', 'c')),
    CONSTRAINT ck_s2_source_grant_atom CHECK (grant_category IS NOT NULL AND grant_category IN (_binary'LANGUAGE', _binary'TOOL') AND grant_kind IS NOT NULL AND grant_target_module_key IS NOT NULL AND grant_target_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(grant_target_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(grant_target_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(grant_target_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(grant_target_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((grant_kind = _binary'FIXED' AND grant_count IS NULL) OR (grant_kind = _binary'CHOICE' AND grant_count IS NOT NULL AND grant_count BETWEEN 1 AND 16))),
    CONSTRAINT ck_s2_source_grant_note CHECK ((author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_character_change (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    target_kind VARBINARY(5) NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(20) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(18) NULL,
    module_key VARBINARY(64) NULL,
    entry_key VARBINARY(64) NULL,
    operation VARBINARY(11) NOT NULL,
    field_key VARBINARY(23) NOT NULL,
    text_value VARCHAR(1000) NULL,
    integer_value INT NULL,
    boolean_value TINYINT NULL,
    reference_module_key VARBINARY(64) NULL,
    reference_rule_type VARBINARY(20) NULL,
    reference_rule_key VARBINARY(128) NULL,
    grant_category VARBINARY(8) NULL,
    grant_kind VARBINARY(6) NULL,
    grant_target_module_key VARBINARY(64) NULL,
    grant_target_rule_key VARBINARY(128) NULL,
    grant_count INT NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_s2_source_change_artifact FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_source_change_index CHECK (author_index BETWEEN 0 AND 4095),
    CONSTRAINT ck_s2_source_change_target CHECK (rule_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class') AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((target_kind = _binary'rule' AND collection_key IS NULL AND module_key IS NULL AND entry_key IS NULL AND (field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'author_note', _binary'enabled') OR (rule_type = _binary'character.subrace' AND field_key = _binary'parent_race'))) OR (target_kind = _binary'entry' AND collection_key IS NOT NULL AND collection_key = _binary'proficiency_grants' AND module_key IS NOT NULL AND entry_key IS NOT NULL AND REGEXP_LIKE(CONVERT(module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_]', 'c') AND field_key IN (_binary'grant', _binary'author_note', _binary'enabled')))),
    CONSTRAINT ck_s2_source_change_grant CHECK ((grant_category IS NULL AND grant_kind IS NULL AND grant_target_module_key IS NULL AND grant_target_rule_key IS NULL AND grant_count IS NULL) OR (grant_category IS NOT NULL AND grant_category IN (_binary'LANGUAGE', _binary'TOOL') AND grant_kind IS NOT NULL AND grant_target_module_key IS NOT NULL AND grant_target_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(grant_target_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(grant_target_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(grant_target_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(grant_target_rule_key USING latin1), '[^a-z0-9_.]', 'c') AND ((grant_kind = _binary'FIXED' AND grant_count IS NULL) OR (grant_kind = _binary'CHOICE' AND grant_count IS NOT NULL AND grant_count BETWEEN 1 AND 16)))),
    CONSTRAINT ck_s2_source_change_reference CHECK ((reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL) OR (reference_module_key IS NOT NULL AND reference_rule_type IS NOT NULL AND reference_rule_key IS NOT NULL AND reference_rule_type = _binary'character.race')),
    CONSTRAINT ck_s2_source_change_text CHECK (text_value IS NULL OR (CHAR_LENGTH(text_value) BETWEEN 1 AND 1000 AND (field_key <> _binary'display_name' OR CHAR_LENGTH(text_value) <= 120) AND NOT REGEXP_LIKE(text_value, '[[:cntrl:]]') AND REGEXP_LIKE(text_value, '[^[:space:]]'))),
    CONSTRAINT ck_s2_source_change_value CHECK (
        (operation = _binary'clear_field' AND field_key = _binary'author_note' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL AND grant_category IS NULL)
        OR (operation = _binary'set_enabled' AND field_key = _binary'enabled' AND boolean_value IS NOT NULL AND boolean_value IN (0, 1) AND text_value IS NULL AND integer_value IS NULL AND reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL AND grant_category IS NULL)
        OR (operation = _binary'set_field' AND ((text_value IS NOT NULL) + (integer_value IS NOT NULL) + (boolean_value IS NOT NULL) + (reference_module_key IS NOT NULL) + (grant_category IS NOT NULL)) = 1
            AND ((field_key IN (_binary'display_name', _binary'description', _binary'author_note') AND text_value IS NOT NULL)
                OR (field_key = _binary'source_page' AND integer_value IS NOT NULL AND integer_value BETWEEN 3 AND 74)
                OR (field_key = _binary'parent_race' AND reference_module_key IS NOT NULL AND reference_rule_type IS NOT NULL AND reference_rule_key IS NOT NULL AND reference_rule_type = _binary'character.race' AND REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '[^a-z0-9_.]', 'c'))
                OR (field_key = _binary'grant' AND grant_category IS NOT NULL))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER rule_s2_character_insert BEFORE INSERT ON rule_schema2_character_source FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_character_source WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Character material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_character_update BEFORE UPDATE ON rule_schema2_character_source FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character material';
END$$
CREATE TRIGGER rule_s2_character_delete BEFORE DELETE ON rule_schema2_character_source FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 character material';
END$$
CREATE TRIGGER rule_s2_grant_insert BEFORE INSERT ON rule_schema2_proficiency_grant FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_proficiency_grant WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 512 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Character material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_grant_update BEFORE UPDATE ON rule_schema2_proficiency_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character material';
END$$
CREATE TRIGGER rule_s2_grant_delete BEFORE DELETE ON rule_schema2_proficiency_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 character material';
END$$
CREATE TRIGGER rule_s2_change_insert BEFORE INSERT ON rule_schema2_character_change FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key = NEW.artifact_module_key AND release_version = NEW.release_version AND installation_sequence = current_count + 1)
        OR (SELECT COUNT(*) FROM rule_schema2_character_change WHERE artifact_module_key = NEW.artifact_module_key AND release_version = NEW.release_version) >= 4096 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Character material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_change_update BEFORE UPDATE ON rule_schema2_character_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character material';
END$$
CREATE TRIGGER rule_s2_change_delete BEFORE DELETE ON rule_schema2_character_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 character material';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO rule_schema_meta (schema_version, script_name, script_sha256)
VALUES (4, 'V004__schema2-character-grant-source.sql', '9578db2143bfb14eb077e403d5395bb3089a7ab6e54f6926f42b7dbcac4e9269');
