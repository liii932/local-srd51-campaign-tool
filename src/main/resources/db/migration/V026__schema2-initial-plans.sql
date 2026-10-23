-- Typed INTERNAL initialization preparations; no business character, history or execution authority.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v026_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v026_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 25
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version = 24 AND CAST(script_name AS BINARY) = _binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version = 25 AND CAST(script_name AS BINARY) = _binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33')) = 25
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('s2_initial_plan', 's2_initial_plan_slot', 's2_initial_plan_choice', 's2_initial_plan_grant')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v026_preflight;

CREATE TABLE s2_initial_plan (
    operation_id VARBINARY(16) NOT NULL,
    run_id VARBINARY(16) NOT NULL,
    snapshot_id VARBINARY(16) NOT NULL,
    campaign_id VARBINARY(16) NOT NULL,
    character_id VARBINARY(16) NOT NULL,
    race_module VARBINARY(64) NOT NULL,
    race_key VARBINARY(128) NOT NULL,
    subrace_module VARBINARY(64) NULL,
    subrace_key VARBINARY(128) NULL,
    background_module VARBINARY(64) NOT NULL,
    background_key VARBINARY(128) NOT NULL,
    class_module VARBINARY(64) NOT NULL,
    class_key VARBINARY(128) NOT NULL,
    slot_count INT NOT NULL,
    choice_count INT NOT NULL,
    grant_count INT NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uq_s2_initial_snapshot (snapshot_id),
    CONSTRAINT fk_s2_initial_operation FOREIGN KEY (operation_id) REFERENCES s2_runtime_operation (operation_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_s2_initial_snapshot FOREIGN KEY (run_id, snapshot_id) REFERENCES s2_runtime_snapshot (run_id, snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_initial_subrace CHECK ((subrace_module IS NULL AND subrace_key IS NULL) OR (subrace_module IS NOT NULL AND subrace_key IS NOT NULL)),
    CONSTRAINT ck_s2_initial_counts CHECK (slot_count BETWEEN 0 AND 512 AND choice_count BETWEEN 0 AND 256 AND grant_count BETWEEN 0 AND 768),
    CONSTRAINT ck_s2_initial_ids CHECK (OCTET_LENGTH(campaign_id) = 16 AND (ORD(SUBSTRING(campaign_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(campaign_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(character_id) = 16 AND (ORD(SUBSTRING(character_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(character_id, 9, 1)) & 192) = 128 AND campaign_id <> character_id AND campaign_id NOT IN (operation_id, run_id, snapshot_id) AND character_id NOT IN (operation_id, run_id, snapshot_id))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_initial_plan_slot (
    operation_id VARBINARY(16) NOT NULL,
    slot_index INT NOT NULL,
    source_module VARBINARY(64) NOT NULL,
    source_type VARBINARY(20) NOT NULL,
    source_key VARBINARY(128) NOT NULL,
    slot_module VARBINARY(64) NOT NULL,
    slot_key VARBINARY(64) NOT NULL,
    category VARBINARY(8) NOT NULL,
    kind VARBINARY(6) NOT NULL,
    target_module VARBINARY(64) NOT NULL,
    target_type VARBINARY(20) NOT NULL,
    target_key VARBINARY(128) NOT NULL,
    selection_count INT NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (operation_id, slot_index),
    UNIQUE KEY uq_s2_initial_slot (operation_id, source_module, source_type, source_key, slot_module, slot_key),
    CONSTRAINT fk_s2_initial_slot FOREIGN KEY (operation_id) REFERENCES s2_initial_plan (operation_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_initial_slot CHECK (slot_index BETWEEN 0 AND 511 AND enabled IN (0, 1) AND source_type IN (_binary'character.race', _binary'character.subrace', _binary'character.background', _binary'character.class')),
    CONSTRAINT ck_s2_initial_atom CHECK (category IN (_binary'LANGUAGE', _binary'TOOL') AND ((kind = _binary'FIXED' AND selection_count IS NULL AND ((category = _binary'LANGUAGE' AND target_type = _binary'character.language') OR (category = _binary'TOOL' AND target_type = _binary'character.tool'))) OR (kind = _binary'CHOICE' AND selection_count IS NOT NULL AND selection_count BETWEEN 1 AND 16 AND ((category = _binary'LANGUAGE' AND target_type = _binary'catalog.language_set') OR (category = _binary'TOOL' AND target_type = _binary'catalog.tool_set')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_initial_plan_choice (
    operation_id VARBINARY(16) NOT NULL,
    choice_index INT NOT NULL,
    slot_index INT NOT NULL,
    target_module VARBINARY(64) NOT NULL,
    target_type VARBINARY(20) NOT NULL,
    target_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (operation_id, choice_index),
    UNIQUE KEY uq_s2_initial_choice (operation_id, slot_index, target_module, target_type, target_key),
    CONSTRAINT fk_s2_initial_choice FOREIGN KEY (operation_id, slot_index) REFERENCES s2_initial_plan_slot (operation_id, slot_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_initial_choice CHECK (choice_index BETWEEN 0 AND 255 AND target_type IN (_binary'character.language', _binary'character.tool'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_initial_plan_grant (
    operation_id VARBINARY(16) NOT NULL,
    grant_index INT NOT NULL,
    slot_index INT NOT NULL,
    category VARBINARY(8) NOT NULL,
    kind VARBINARY(6) NOT NULL,
    target_module VARBINARY(64) NOT NULL,
    target_type VARBINARY(20) NOT NULL,
    target_key VARBINARY(128) NOT NULL,
    suppressed TINYINT NOT NULL,
    PRIMARY KEY (operation_id, grant_index),
    UNIQUE KEY uq_s2_initial_grant (operation_id, slot_index, target_module, target_type, target_key),
    CONSTRAINT fk_s2_initial_grant FOREIGN KEY (operation_id, slot_index) REFERENCES s2_initial_plan_slot (operation_id, slot_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_initial_grant CHECK (grant_index BETWEEN 0 AND 767 AND suppressed IN (0, 1) AND kind IN (_binary'FIXED', _binary'CHOICE') AND ((category = _binary'LANGUAGE' AND target_type = _binary'character.language') OR (category = _binary'TOOL' AND target_type = _binary'character.tool')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER s2_initial_plan_insert BEFORE INSERT ON s2_initial_plan FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_runtime_operation WHERE operation_id = NEW.operation_id AND run_id = NEW.run_id AND snapshot_id = NEW.snapshot_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Initial plan receipt identity mismatch';
    END IF;
END$$
CREATE TRIGGER s2_initial_plan_update BEFORE UPDATE ON s2_initial_plan FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL initialization preparation';
END$$
CREATE TRIGGER s2_initial_plan_delete BEFORE DELETE ON s2_initial_plan FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent INTERNAL initialization preparation';
END$$
CREATE TRIGGER s2_initial_plan_slot_update BEFORE UPDATE ON s2_initial_plan_slot FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL initialization preparation';
END$$
CREATE TRIGGER s2_initial_plan_slot_delete BEFORE DELETE ON s2_initial_plan_slot FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent INTERNAL initialization preparation';
END$$
CREATE TRIGGER s2_initial_plan_choice_update BEFORE UPDATE ON s2_initial_plan_choice FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL initialization preparation';
END$$
CREATE TRIGGER s2_initial_plan_choice_delete BEFORE DELETE ON s2_initial_plan_choice FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent INTERNAL initialization preparation';
END$$
CREATE TRIGGER s2_initial_plan_grant_update BEFORE UPDATE ON s2_initial_plan_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable INTERNAL initialization preparation';
END$$
CREATE TRIGGER s2_initial_plan_grant_delete BEFORE DELETE ON s2_initial_plan_grant FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent INTERNAL initialization preparation';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (26, 'V026__schema2-initial-plans.sql', '06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa');
