-- Typed equipment snapshots, health states, commands and saved randomness for INTERNAL execution.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v031_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v031_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 30
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version = 24 AND CAST(script_name AS BINARY) = _binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version = 25 AND CAST(script_name AS BINARY) = _binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version = 26 AND CAST(script_name AS BINARY) = _binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY) = _binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version = 27 AND CAST(script_name AS BINARY) = _binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY) = _binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version = 28 AND CAST(script_name AS BINARY) = _binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a') OR (schema_version = 29 AND CAST(script_name AS BINARY) = _binary'V029__schema2-character-check-history.sql' AND CAST(script_sha256 AS BINARY) = _binary'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b') OR (schema_version = 30 AND CAST(script_name AS BINARY) = _binary'V030__schema2-equipment-health-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3')) = 30
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('s2_character_event_equipment', 's2_character_equipment_command', 's2_character_health_event', 's2_character_health_die')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v031_preflight;

ALTER TABLE s2_character_event DROP CHECK ck_s2_character_command_0;
ALTER TABLE s2_character_event ADD CONSTRAINT ck_s2_character_command_0 CHECK (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED', _binary'RESOURCE_SPENT', _binary'RESOURCES_RECOVERED', _binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED', _binary'CHECK_RESOLVED', _binary'ITEM_ACQUIRED', _binary'ITEM_REMOVED', _binary'ITEM_EQUIPPED', _binary'ITEM_UNEQUIPPED', _binary'HEALTH_EFFECT_RESOLVED', _binary'DEATH_SAVE_RESOLVED'));

ALTER TABLE s2_character_event_state ADD equipment_count INT NOT NULL DEFAULT 0,
    ADD temporary_hit_points INT NOT NULL DEFAULT 0, ADD death_successes INT NOT NULL DEFAULT 0,
    ADD death_failures INT NOT NULL DEFAULT 0, ADD stable TINYINT NOT NULL DEFAULT 0,
    ADD dead TINYINT NOT NULL DEFAULT 0, ADD stable_recovery_minute BIGINT NULL;
ALTER TABLE s2_character_event_state ADD CONSTRAINT ck_s2_character_health_state CHECK (equipment_count BETWEEN 0 AND 32 AND temporary_hit_points BETWEEN 0 AND 65535 AND death_successes BETWEEN 0 AND 2 AND death_failures BETWEEN 0 AND 2 AND stable IN (0, 1) AND dead IN (0, 1) AND ((dead = 1 AND hit_points = 0 AND death_successes = 0 AND death_failures = 0 AND stable = 0 AND stable_recovery_minute IS NULL) OR (dead = 0 AND ((hit_points > 0 AND death_successes = 0 AND death_failures = 0 AND stable = 0 AND stable_recovery_minute IS NULL) OR (hit_points = 0 AND ((stable = 1 AND death_successes = 0 AND death_failures = 0 AND stable_recovery_minute IS NOT NULL AND stable_recovery_minute > minute AND stable_recovery_minute <= 1000000000) OR (stable = 0 AND stable_recovery_minute IS NULL)))))));

CREATE TABLE s2_character_event_equipment (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    equipment_index INT NOT NULL,
    item_id VARBINARY(16) NOT NULL,
    item_module VARBINARY(64) NOT NULL,
    item_type VARBINARY(32) NOT NULL,
    item_key VARBINARY(128) NOT NULL,
    location VARBINARY(16) NOT NULL,
    worn_slot INT GENERATED ALWAYS AS (IF(location = _binary'WORN', 1, NULL)) STORED,
    main_hand_slot INT GENERATED ALWAYS AS (IF(location IN (_binary'MAIN_HAND', _binary'BOTH_HANDS'), 1, NULL)) STORED,
    off_hand_slot INT GENERATED ALWAYS AS (IF(location IN (_binary'OFF_HAND', _binary'BOTH_HANDS'), 1, NULL)) STORED,
    PRIMARY KEY (character_id, event_sequence, phase, equipment_index),
    UNIQUE KEY uq_s2_equipment_item (character_id, event_sequence, phase, item_id),
    UNIQUE KEY uq_s2_equipment_worn (character_id, event_sequence, phase, worn_slot),
    UNIQUE KEY uq_s2_equipment_main_hand (character_id, event_sequence, phase, main_hand_slot),
    UNIQUE KEY uq_s2_equipment_off_hand (character_id, event_sequence, phase, off_hand_slot),
    CONSTRAINT fk_s2_equipment_state FOREIGN KEY (character_id, event_sequence, phase) REFERENCES s2_character_event_state (character_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_equipment_state CHECK (equipment_index BETWEEN 0 AND 31 AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128 AND item_type = _binary'equipment.item' AND location IN (_binary'CARRIED', _binary'WORN', _binary'MAIN_HAND', _binary'OFF_HAND', _binary'BOTH_HANDS')),
    CONSTRAINT ck_s2_equipment_identity CHECK (REGEXP_LIKE(CONVERT(item_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(item_module USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(item_key USING latin1), '^item[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(item_key USING latin1), '[^a-z0-9_.]', 'c'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_equipment_command (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    item_id VARBINARY(16) NOT NULL,
    item_module VARBINARY(64) NULL,
    item_type VARBINARY(32) NULL,
    item_key VARBINARY(128) NULL,
    location VARBINARY(16) NULL,
    adjudication VARCHAR(500) NOT NULL,
    PRIMARY KEY (character_id, event_sequence),
    CONSTRAINT fk_s2_equipment_command_event FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_equipment_command CHECK (event_sequence BETWEEN 2 AND 128 AND OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128 AND (location IS NULL OR location IN (_binary'CARRIED', _binary'WORN', _binary'MAIN_HAND', _binary'OFF_HAND', _binary'BOTH_HANDS')) AND ((item_module IS NULL AND item_type IS NULL AND item_key IS NULL) OR (item_module IS NOT NULL AND item_type IS NOT NULL AND item_key IS NOT NULL AND item_type = _binary'equipment.item' AND REGEXP_LIKE(CONVERT(item_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(item_module USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(item_key USING latin1), '^item[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(item_key USING latin1), '[^a-z0-9_.]', 'c'))) AND CHAR_LENGTH(adjudication) BETWEEN 1 AND 500 AND CHAR_LENGTH(TRIM(adjudication)) > 0 AND NOT REGEXP_LIKE(adjudication, '[[:cntrl:]]'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_health_event (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    health_module VARBINARY(64) NOT NULL,
    health_type VARBINARY(32) NOT NULL,
    health_key VARBINARY(128) NOT NULL,
    effect_module VARBINARY(64) NULL,
    effect_type VARBINARY(32) NULL,
    effect_key VARBINARY(128) NULL,
    temporary_choice VARBINARY(8) NULL,
    adjudication VARCHAR(500) NOT NULL,
    die_count INT NOT NULL,
    death_d20 INT NULL,
    recovery_d4 INT NULL,
    rolled_amount INT NOT NULL,
    applied_amount INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence),
    CONSTRAINT fk_s2_health_event FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_health_event_identity CHECK (event_sequence BETWEEN 2 AND 128 AND health_type = _binary'adventure.health' AND REGEXP_LIKE(CONVERT(health_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(health_module USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(health_key USING latin1), '^health[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(health_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_health_effect_identity CHECK ((effect_module IS NULL AND effect_type IS NULL AND effect_key IS NULL AND temporary_choice IS NULL) OR (effect_module IS NOT NULL AND effect_type IS NOT NULL AND effect_key IS NOT NULL AND effect_type = _binary'adventure.vital_effect' AND REGEXP_LIKE(CONVERT(effect_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(effect_module USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(effect_key USING latin1), '^vital_effect[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(effect_key USING latin1), '[^a-z0-9_.]', 'c') AND (temporary_choice IS NULL OR temporary_choice IN (_binary'KEEP', _binary'REPLACE')))),
    CONSTRAINT ck_s2_health_event_result CHECK (die_count BETWEEN 0 AND 20 AND (death_d20 IS NULL OR death_d20 BETWEEN 1 AND 20) AND (recovery_d4 IS NULL OR recovery_d4 BETWEEN 1 AND 4) AND rolled_amount >= 0 AND applied_amount >= 0 AND CHAR_LENGTH(adjudication) BETWEEN 1 AND 500 AND CHAR_LENGTH(TRIM(adjudication)) > 0 AND NOT REGEXP_LIKE(adjudication, '[[:cntrl:]]'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_character_health_die (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    die_index INT NOT NULL,
    die_value INT NOT NULL,
    PRIMARY KEY (character_id, event_sequence, die_index),
    CONSTRAINT fk_s2_health_die_event FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_health_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_health_die CHECK (die_index BETWEEN 0 AND 19 AND die_value BETWEEN 1 AND 12)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER s2_equipment_command_insert BEFORE INSERT ON s2_character_equipment_command FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND ((event_kind = _binary'ITEM_ACQUIRED' AND NEW.item_id = operation_id AND NEW.item_module IS NOT NULL AND NEW.location = _binary'CARRIED') OR (event_kind = _binary'ITEM_EQUIPPED' AND NEW.item_module IS NULL AND NEW.location IN (_binary'WORN', _binary'MAIN_HAND', _binary'OFF_HAND', _binary'BOTH_HANDS')) OR (event_kind IN (_binary'ITEM_REMOVED', _binary'ITEM_UNEQUIPPED') AND NEW.item_module IS NULL AND NEW.location IS NULL))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 equipment command event mismatch';
    END IF;
END$$
CREATE TRIGGER s2_health_event_insert BEFORE INSERT ON s2_character_health_event FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND ((event_kind = _binary'HEALTH_EFFECT_RESOLVED' AND NEW.effect_module IS NOT NULL AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL) OR (event_kind = _binary'DEATH_SAVE_RESOLVED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NOT NULL))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 health event mismatch';
    END IF;
END$$
CREATE TRIGGER s2_character_equipment_state_update BEFORE UPDATE ON s2_character_event_equipment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
CREATE TRIGGER s2_character_equipment_state_delete BEFORE DELETE ON s2_character_event_equipment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
CREATE TRIGGER s2_character_equipment_command_update BEFORE UPDATE ON s2_character_equipment_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
CREATE TRIGGER s2_character_equipment_command_delete BEFORE DELETE ON s2_character_equipment_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
CREATE TRIGGER s2_character_health_event_update BEFORE UPDATE ON s2_character_health_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
CREATE TRIGGER s2_character_health_event_delete BEFORE DELETE ON s2_character_health_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
CREATE TRIGGER s2_character_health_die_update BEFORE UPDATE ON s2_character_health_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
CREATE TRIGGER s2_character_health_die_delete BEFORE DELETE ON s2_character_health_die FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 equipment health fact';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (31, 'V031__schema2-equipment-health-history.sql', '1b9f8959ad5c3d6e4b417d2672a7a06cf8df572e9059e4addfde01856e57b79e');
