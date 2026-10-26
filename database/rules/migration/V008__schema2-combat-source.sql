-- Original typed combat, melee weapon and walking-speed declarations and overrides.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v008_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO rules_v008_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM rule_schema_meta) = 7
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__rule-source-schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__tool-catalog.sql' AND CAST(script_sha256 AS BINARY) = _binary'2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__schema2-source-ledger.sql' AND CAST(script_sha256 AS BINARY) = _binary'4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__schema2-character-grant-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'9578db2143bfb14eb077e403d5395bb3089a7ab6e54f6926f42b7dbcac4e9269') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__schema2-advancement-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'2dfdecf8cb30fcc71f7e011550ce3b32334340aab5ff546f4e71e53e13147e5c') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__schema2-check-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'9c495e9af9ce06b1e98fba9e44ef159cebbe8ef5f058254e54f8f83a0c7aca30') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__schema2-equipment-health-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'd5bc25f01389e7811280b97c36202b066142efb6ee5521cbb7fccb0def25de45')) = 7
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('rule_schema2_combat','rule_schema2_weapon_attack','rule_schema2_movement','rule_schema2_combat_change')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v008_preflight;

CREATE TABLE rule_schema2_combat (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    UNIQUE KEY uq_src_combat (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT fk_src_combat FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_combat_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key=artifact_module_key AND rule_type=_binary'adventure.combat' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^combat[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_combat_algorithm CHECK (source_page BETWEEN 90 AND 95 AND algorithm=_binary'SRD_2014_COMBAT_V1'),
    CONSTRAINT ck_src_combat_text CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_weapon_attack (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    item_module_key VARBINARY(64) NOT NULL,
    item_rule_type VARBINARY(32) NOT NULL,
    item_rule_key VARBINARY(128) NOT NULL,
    ability_policy VARBINARY(16) NOT NULL,
    dice_count INT NOT NULL,
    dice_sides INT NOT NULL,
    damage_type VARBINARY(16) NOT NULL,
    reach_feet INT NOT NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    UNIQUE KEY uq_src_weapon_attack (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT fk_src_weapon_attack FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_weapon_attack_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key=artifact_module_key AND rule_type=_binary'equipment.weapon_attack' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^weapon_attack[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_weapon_attack_algorithm CHECK (source_page BETWEEN 62 AND 73 AND algorithm=_binary'SRD_2014_MELEE_WEAPON_ATTACK_V1'),
    CONSTRAINT ck_src_weapon_attack_0 CHECK (item_rule_type=_binary'equipment.item'),
    CONSTRAINT ck_src_weapon_attack_1 CHECK (ability_policy IN (_binary'STRENGTH',_binary'FINESSE') AND dice_count BETWEEN 1 AND 4 AND dice_sides IN (4,6,8,10,12) AND damage_type IN (_binary'BLUDGEONING',_binary'PIERCING',_binary'SLASHING') AND reach_feet IN (5,10)),
    CONSTRAINT ck_src_weapon_attack_2 CHECK (REGEXP_LIKE(CONVERT(item_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(item_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(item_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(item_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_weapon_attack_text CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_movement (
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
    walk_speed_feet INT NOT NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    UNIQUE KEY uq_src_movement (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT fk_src_movement FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_movement_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key=artifact_module_key AND rule_type=_binary'character.movement' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^movement[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_movement_algorithm CHECK (source_page BETWEEN 3 AND 74 AND algorithm=_binary'SRD_2014_WALKING_SPEED_V1'),
    CONSTRAINT ck_src_movement_0 CHECK (owner_rule_type IN (_binary'character.race',_binary'character.subrace')),
    CONSTRAINT ck_src_movement_1 CHECK (walk_speed_feet BETWEEN 5 AND 120 AND MOD(walk_speed_feet,5)=0),
    CONSTRAINT ck_src_movement_2 CHECK (REGEXP_LIKE(CONVERT(owner_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(owner_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_movement_text CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_combat_change (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    operation VARBINARY(16) NOT NULL,
    field_key VARBINARY(23) NOT NULL,
    text_value VARCHAR(1000) NULL,
    integer_value INT NULL,
    boolean_value TINYINT NULL,
    reference_module_key VARBINARY(64) NULL,
    reference_rule_type VARBINARY(32) NULL,
    reference_rule_key VARBINARY(128) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    UNIQUE KEY uq_src_combat_change (artifact_module_key, release_version, rule_module_key, rule_type, rule_key, field_key),
    CONSTRAINT fk_src_combat_change FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_combat_change_identity CHECK (author_index BETWEEN 0 AND 255 AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND (
        (rule_type=_binary'adventure.combat' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^combat[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled')) OR
        (rule_type=_binary'equipment.weapon_attack' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^weapon_attack[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'item',_binary'ability_policy',_binary'dice_count',_binary'dice_sides',_binary'damage_type',_binary'reach_feet')) OR
        (rule_type=_binary'character.movement' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^movement[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'owner',_binary'walk_speed_feet')))),
    CONSTRAINT ck_src_combat_change_ref CHECK ((reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL) OR (reference_module_key IS NOT NULL AND reference_rule_type IS NOT NULL AND reference_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '[^a-z0-9_.]', 'c'))),
    CONSTRAINT ck_src_combat_change_value CHECK (
        (operation=_binary'clear_field' AND field_key=_binary'author_note' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL)
        OR (operation=_binary'set_enabled' AND field_key=_binary'enabled' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IN (0,1) AND boolean_value IS NOT NULL AND reference_module_key IS NULL)
        OR (operation=_binary'set_field' AND ((text_value IS NOT NULL)+(integer_value IS NOT NULL)+(boolean_value IS NOT NULL)+(reference_module_key IS NOT NULL))=1 AND (
            (field_key IN (_binary'display_name',_binary'description',_binary'author_note') AND text_value IS NOT NULL AND CHAR_LENGTH(text_value) BETWEEN 1 AND IF(field_key=_binary'display_name',120,1000) AND NOT REGEXP_LIKE(text_value, '[[:cntrl:]]') AND REGEXP_LIKE(text_value, '[^[:space:]]'))
            OR (rule_type=_binary'adventure.combat' AND field_key=_binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY)=_binary'SRD_2014_COMBAT_V1')
            OR (rule_type=_binary'adventure.combat' AND field_key=_binary'source_page' AND integer_value IS NOT NULL AND integer_value BETWEEN 90 AND 95)
            OR (rule_type=_binary'equipment.weapon_attack' AND field_key=_binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY)=_binary'SRD_2014_MELEE_WEAPON_ATTACK_V1')
            OR (rule_type=_binary'equipment.weapon_attack' AND field_key=_binary'source_page' AND integer_value IS NOT NULL AND integer_value BETWEEN 62 AND 73)
            OR (rule_type=_binary'character.movement' AND field_key=_binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY)=_binary'SRD_2014_WALKING_SPEED_V1')
            OR (rule_type=_binary'character.movement' AND field_key=_binary'source_page' AND integer_value IS NOT NULL AND integer_value BETWEEN 3 AND 74)
            OR (field_key=_binary'item' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'equipment.item')
            OR (field_key=_binary'owner' AND reference_module_key IS NOT NULL AND reference_rule_type IN (_binary'character.race',_binary'character.subrace'))
            OR (field_key=_binary'ability_policy' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'STRENGTH',_binary'FINESSE'))
            OR (field_key=_binary'dice_count' AND integer_value IS NOT NULL AND integer_value BETWEEN 1 AND 4)
            OR (field_key=_binary'dice_sides' AND integer_value IS NOT NULL AND integer_value IN (4,6,8,10,12))
            OR (field_key=_binary'damage_type' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'BLUDGEONING',_binary'PIERCING',_binary'SLASHING'))
            OR (field_key=_binary'reach_feet' AND integer_value IS NOT NULL AND integer_value IN (5,10))
            OR (field_key=_binary'walk_speed_feet' AND integer_value IS NOT NULL AND integer_value BETWEEN 5 AND 120 AND MOD(integer_value,5)=0)
        )))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER rule_s2_combat_insert BEFORE INSERT ON rule_schema2_combat FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_combat WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Combat material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_combat_update BEFORE UPDATE ON rule_schema2_combat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
CREATE TRIGGER rule_s2_combat_delete BEFORE DELETE ON rule_schema2_combat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
CREATE TRIGGER rule_s2_weapon_attack_insert BEFORE INSERT ON rule_schema2_weapon_attack FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_weapon_attack WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Combat material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_weapon_attack_update BEFORE UPDATE ON rule_schema2_weapon_attack FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
CREATE TRIGGER rule_s2_weapon_attack_delete BEFORE DELETE ON rule_schema2_weapon_attack FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
CREATE TRIGGER rule_s2_movement_insert BEFORE INSERT ON rule_schema2_movement FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_movement WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Combat material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_movement_update BEFORE UPDATE ON rule_schema2_movement FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
CREATE TRIGGER rule_s2_movement_delete BEFORE DELETE ON rule_schema2_movement FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
CREATE TRIGGER rule_s2_combat_change_insert BEFORE INSERT ON rule_schema2_combat_change FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_combat_change WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=4096 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Combat material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_combat_change_update BEFORE UPDATE ON rule_schema2_combat_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
CREATE TRIGGER rule_s2_combat_change_delete BEFORE DELETE ON rule_schema2_combat_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 combat material';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO rule_schema_meta (schema_version,script_name,script_sha256)
VALUES (8,'V008__schema2-combat-source.sql','eb0bf9e4675cfed03ec5e5e57f026b57e6bd3409edf98eaf6a9e6e680485cfe2');
