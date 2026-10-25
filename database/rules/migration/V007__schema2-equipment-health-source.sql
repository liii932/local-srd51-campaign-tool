-- Original bounded equipment and direct health mechanism declarations and changes.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v007_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO rules_v007_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM rule_schema_meta) = 6
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__rule-source-schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__tool-catalog.sql' AND CAST(script_sha256 AS BINARY) = _binary'2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__schema2-source-ledger.sql' AND CAST(script_sha256 AS BINARY) = _binary'4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__schema2-character-grant-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'9578db2143bfb14eb077e403d5395bb3089a7ab6e54f6926f42b7dbcac4e9269') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__schema2-advancement-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'2dfdecf8cb30fcc71f7e011550ce3b32334340aab5ff546f4e71e53e13147e5c') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__schema2-check-source.sql' AND CAST(script_sha256 AS BINARY) = _binary'9c495e9af9ce06b1e98fba9e44ef159cebbe8ef5f058254e54f8f83a0c7aca30')) = 6
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('rule_schema2_equipment_item','rule_schema2_health','rule_schema2_vital_effect','rule_schema2_execution_change')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v007_preflight;

CREATE TABLE rule_schema2_equipment_item (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    kind VARBINARY(16) NOT NULL,
    armor_class INT NOT NULL,
    strength_minimum INT NOT NULL,
    stealth_disadvantage TINYINT NOT NULL,
    hands INT NOT NULL,
    proficiency_module_key VARBINARY(64) NOT NULL,
    proficiency_rule_type VARBINARY(32) NOT NULL,
    proficiency_rule_key VARBINARY(128) NOT NULL,
    stealth_skill_module_key VARBINARY(64) NULL,
    stealth_skill_rule_type VARBINARY(32) NULL,
    stealth_skill_rule_key VARBINARY(128) NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    UNIQUE KEY uq_src_equipment_item (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT fk_src_equipment_item FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_equipment_item_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'equipment.item' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^item[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_equipment_item_0 CHECK (source_page BETWEEN 62 AND 73 AND algorithm = _binary'SRD_2014_EQUIPMENT_V1'),
    CONSTRAINT ck_src_equipment_item_1 CHECK (((kind IN (_binary'LIGHT_ARMOR',_binary'MEDIUM_ARMOR') AND armor_class BETWEEN 10 AND 20 AND strength_minimum=0 AND hands=0) OR (kind=_binary'HEAVY_ARMOR' AND armor_class BETWEEN 10 AND 20 AND strength_minimum IN (0,13,15) AND hands=0) OR (kind=_binary'SHIELD' AND armor_class=2 AND strength_minimum=0 AND stealth_disadvantage=0 AND hands=1) OR (kind IN (_binary'TOOL',_binary'WEAPON') AND armor_class=0 AND strength_minimum=0 AND stealth_disadvantage=0 AND hands IN (1,2)))),
    CONSTRAINT ck_src_equipment_item_2 CHECK ((proficiency_module_key IS NOT NULL AND proficiency_rule_type IS NOT NULL AND proficiency_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(proficiency_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(proficiency_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_rule_key USING latin1), '[^a-z0-9_.]', 'c')) AND ((kind=_binary'TOOL' AND proficiency_rule_type=_binary'character.tool') OR (kind<>_binary'TOOL' AND proficiency_rule_type=_binary'character.proficiency'))),
    CONSTRAINT ck_src_equipment_item_3 CHECK ((stealth_skill_module_key IS NULL AND stealth_skill_rule_type IS NULL AND stealth_skill_rule_key IS NULL OR (stealth_skill_module_key IS NOT NULL AND stealth_skill_rule_type IS NOT NULL AND stealth_skill_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(stealth_skill_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(stealth_skill_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(stealth_skill_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(stealth_skill_rule_key USING latin1), '[^a-z0-9_.]', 'c'))) AND ((stealth_disadvantage=0 AND stealth_skill_module_key IS NULL) OR (stealth_disadvantage=1 AND stealth_skill_module_key IS NOT NULL AND stealth_skill_rule_type=_binary'character.proficiency'))),
    CONSTRAINT ck_src_equipment_item_4 CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_health (
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
    UNIQUE KEY uq_src_health (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT fk_src_health FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_health_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'adventure.health' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^health[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_health_0 CHECK (source_page = 98 AND algorithm = _binary'SRD_2014_HEALTH_V1'),
    CONSTRAINT ck_src_health_1 CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_vital_effect (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    kind VARBINARY(16) NOT NULL,
    dice_count INT NOT NULL,
    dice_sides INT NOT NULL,
    modifier INT NOT NULL,
    damage_type VARBINARY(16) NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    UNIQUE KEY uq_src_vital_effect (artifact_module_key, release_version, module_key, rule_type, rule_key),
    CONSTRAINT fk_src_vital_effect FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_vital_effect_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key = artifact_module_key AND rule_type = _binary'adventure.vital_effect' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^vital_effect[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_vital_effect_0 CHECK (source_page BETWEEN 96 AND 99 AND algorithm = _binary'DIRECT_VITAL_EFFECT_V1'),
    CONSTRAINT ck_src_vital_effect_1 CHECK (dice_count BETWEEN 0 AND 20 AND ((dice_count=0 AND dice_sides=0) OR (dice_count>0 AND dice_sides IN (4,6,8,10,12))) AND modifier BETWEEN -1000 AND 1000),
    CONSTRAINT ck_src_vital_effect_2 CHECK (((kind=_binary'DAMAGE' AND damage_type IS NOT NULL AND damage_type IN (_binary'ACID', _binary'BLUDGEONING', _binary'COLD', _binary'FIRE', _binary'FORCE', _binary'LIGHTNING', _binary'NECROTIC', _binary'PIERCING', _binary'POISON', _binary'PSYCHIC', _binary'RADIANT', _binary'SLASHING', _binary'THUNDER')) OR (kind IN (_binary'HEALING',_binary'TEMP_HP') AND damage_type IS NULL))),
    CONSTRAINT ck_src_vital_effect_3 CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_execution_change (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    operation VARBINARY(11) NOT NULL,
    field_key VARBINARY(32) NOT NULL,
    text_value VARCHAR(1000) NULL,
    integer_value INT NULL,
    boolean_value TINYINT NULL,
    reference_module_key VARBINARY(64) NULL,
    reference_rule_type VARBINARY(32) NULL,
    reference_rule_key VARBINARY(128) NULL,
    PRIMARY KEY (artifact_module_key,release_version,author_index),
    CONSTRAINT fk_src_execution_change FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact(module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_execution_change_identity CHECK (author_index BETWEEN 0 AND 255 AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND ((rule_type=_binary'equipment.item' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^item[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type=_binary'adventure.health' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^health[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')) OR (rule_type=_binary'adventure.vital_effect' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^vital_effect[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')))),
    CONSTRAINT ck_src_execution_change_field CHECK (field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled') OR (rule_type=_binary'equipment.item' AND field_key IN (_binary'kind',_binary'armor_class',_binary'strength_minimum',_binary'stealth_disadvantage',_binary'hands',_binary'proficiency',_binary'stealth_skill')) OR (rule_type=_binary'adventure.vital_effect' AND field_key IN (_binary'kind',_binary'dice_count',_binary'dice_sides',_binary'modifier',_binary'damage_type'))),
    CONSTRAINT ck_src_execution_change_text CHECK (text_value IS NULL OR ((CHAR_LENGTH(text_value) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(text_value, '[[:cntrl:]]') AND REGEXP_LIKE(text_value, '[^[:space:]]')) AND (field_key<>_binary'display_name' OR CHAR_LENGTH(text_value)<=120))),
    CONSTRAINT ck_src_execution_change_ref CHECK ((reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL OR (reference_module_key IS NOT NULL AND reference_rule_type IS NOT NULL AND reference_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '[^a-z0-9_.]', 'c'))) AND (reference_module_key IS NULL OR (field_key=_binary'proficiency' AND reference_rule_type IN (_binary'character.proficiency',_binary'character.tool')) OR (field_key=_binary'stealth_skill' AND reference_rule_type=_binary'character.proficiency'))),
    CONSTRAINT ck_src_execution_change_atom CHECK (
        (operation=_binary'clear_field' AND field_key IN (_binary'author_note',_binary'stealth_skill',_binary'damage_type') AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL)
        OR (operation=_binary'set_enabled' AND field_key=_binary'enabled' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NOT NULL AND boolean_value IN (0,1) AND reference_module_key IS NULL)
        OR (operation=_binary'set_field' AND ((text_value IS NOT NULL)+(integer_value IS NOT NULL)+(boolean_value IS NOT NULL)+(reference_module_key IS NOT NULL))=1 AND (
            (field_key IN (_binary'display_name',_binary'description',_binary'author_note') AND text_value IS NOT NULL)
            OR (field_key=_binary'algorithm' AND text_value IS NOT NULL AND ((rule_type=_binary'equipment.item' AND CAST(text_value AS BINARY)=_binary'SRD_2014_EQUIPMENT_V1') OR (rule_type=_binary'adventure.health' AND CAST(text_value AS BINARY)=_binary'SRD_2014_HEALTH_V1') OR (rule_type=_binary'adventure.vital_effect' AND CAST(text_value AS BINARY)=_binary'DIRECT_VITAL_EFFECT_V1')))
            OR (field_key=_binary'source_page' AND integer_value IS NOT NULL AND ((rule_type=_binary'equipment.item' AND integer_value BETWEEN 62 AND 73) OR (rule_type=_binary'adventure.health' AND integer_value=98) OR (rule_type=_binary'adventure.vital_effect' AND integer_value BETWEEN 96 AND 99)))
            OR (field_key=_binary'kind' AND text_value IS NOT NULL AND ((rule_type=_binary'equipment.item' AND CAST(text_value AS BINARY) IN (_binary'LIGHT_ARMOR',_binary'MEDIUM_ARMOR',_binary'HEAVY_ARMOR',_binary'SHIELD',_binary'TOOL',_binary'WEAPON')) OR (rule_type=_binary'adventure.vital_effect' AND CAST(text_value AS BINARY) IN (_binary'DAMAGE',_binary'HEALING',_binary'TEMP_HP'))))
            OR (field_key=_binary'armor_class' AND integer_value IS NOT NULL AND integer_value BETWEEN 0 AND 20)
            OR (field_key=_binary'strength_minimum' AND integer_value IS NOT NULL AND integer_value IN (0,13,15))
            OR (field_key=_binary'stealth_disadvantage' AND boolean_value IS NOT NULL AND boolean_value IN (0,1))
            OR (field_key=_binary'hands' AND integer_value IS NOT NULL AND integer_value BETWEEN 0 AND 2)
            OR (field_key IN (_binary'proficiency',_binary'stealth_skill') AND reference_module_key IS NOT NULL)
            OR (field_key=_binary'dice_count' AND integer_value IS NOT NULL AND integer_value BETWEEN 0 AND 20)
            OR (field_key=_binary'dice_sides' AND integer_value IS NOT NULL AND integer_value IN (0,4,6,8,10,12))
            OR (field_key=_binary'modifier' AND integer_value IS NOT NULL AND integer_value BETWEEN -1000 AND 1000)
            OR (field_key=_binary'damage_type' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'ACID', _binary'BLUDGEONING', _binary'COLD', _binary'FIRE', _binary'FORCE', _binary'LIGHTNING', _binary'NECROTIC', _binary'PIERCING', _binary'POISON', _binary'PSYCHIC', _binary'RADIANT', _binary'SLASHING', _binary'THUNDER'))
        )))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER rule_s2_equipment_item_insert BEFORE INSERT ON rule_schema2_equipment_item FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_equipment_item WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_equipment_item_update BEFORE UPDATE ON rule_schema2_equipment_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
CREATE TRIGGER rule_s2_equipment_item_delete BEFORE DELETE ON rule_schema2_equipment_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
CREATE TRIGGER rule_s2_health_insert BEFORE INSERT ON rule_schema2_health FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_health WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_health_update BEFORE UPDATE ON rule_schema2_health FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
CREATE TRIGGER rule_s2_health_delete BEFORE DELETE ON rule_schema2_health FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
CREATE TRIGGER rule_s2_vital_effect_insert BEFORE INSERT ON rule_schema2_vital_effect FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_vital_effect WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_vital_effect_update BEFORE UPDATE ON rule_schema2_vital_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
CREATE TRIGGER rule_s2_vital_effect_delete BEFORE DELETE ON rule_schema2_vital_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
CREATE TRIGGER rule_s2_execution_change_insert BEFORE INSERT ON rule_schema2_execution_change FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_execution_change WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=4096 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_execution_change_update BEFORE UPDATE ON rule_schema2_execution_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
CREATE TRIGGER rule_s2_execution_change_delete BEFORE DELETE ON rule_schema2_execution_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution material';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO rule_schema_meta (schema_version,script_name,script_sha256)
VALUES (7,'V007__schema2-equipment-health-source.sql','d5bc25f01389e7811280b97c36202b066142efb6ee5521cbb7fccb0def25de45');
