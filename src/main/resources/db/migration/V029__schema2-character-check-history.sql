-- Typed immutable check input and saved results for the INTERNAL character consumer.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v029_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v029_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 28
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version = 24 AND CAST(script_name AS BINARY) = _binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version = 25 AND CAST(script_name AS BINARY) = _binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version = 26 AND CAST(script_name AS BINARY) = _binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY) = _binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version = 27 AND CAST(script_name AS BINARY) = _binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY) = _binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version = 28 AND CAST(script_name AS BINARY) = _binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a')) = 28
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 's2_character_check') = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v029_preflight;

ALTER TABLE s2_character_event DROP CHECK ck_s2_character_command_0;
ALTER TABLE s2_character_event ADD CONSTRAINT ck_s2_character_command_0 CHECK (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED', _binary'RESOURCE_SPENT', _binary'RESOURCES_RECOVERED', _binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED', _binary'CHECK_RESOLVED'));

CREATE TABLE s2_character_check (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    check_module VARBINARY(64) NOT NULL,
    check_type VARBINARY(32) NOT NULL,
    check_key VARBINARY(128) NOT NULL,
    check_kind VARBINARY(8) NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    proficiency_module VARBINARY(64) NULL,
    proficiency_type VARBINARY(32) NULL,
    proficiency_key VARBINARY(128) NULL,
    difficulty_class INT NOT NULL,
    advantage TINYINT NOT NULL,
    disadvantage TINYINT NOT NULL,
    adjudication VARCHAR(500) NOT NULL,
    first_d20 INT NOT NULL,
    second_d20 INT NULL,
    selected_d20 INT NOT NULL,
    ability_modifier INT NOT NULL,
    proficiency_bonus INT NOT NULL,
    total INT NOT NULL,
    success TINYINT NOT NULL,
    effective_advantage TINYINT NOT NULL,
    effective_disadvantage TINYINT NOT NULL,
    PRIMARY KEY (character_id, event_sequence),
    CONSTRAINT fk_s2_character_check_event FOREIGN KEY (character_id, event_sequence) REFERENCES s2_character_event (character_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_character_check_identity CHECK (event_sequence BETWEEN 2 AND 128 AND check_type = _binary'adventure.check' AND REGEXP_LIKE(CONVERT(check_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(check_module USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(check_key USING latin1), '^check[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(check_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_s2_character_check_input CHECK (check_kind IN (_binary'ABILITY', _binary'SKILL', _binary'TOOL') AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma') AND difficulty_class BETWEEN 0 AND 30 AND advantage IN (0, 1) AND disadvantage IN (0, 1) AND CHAR_LENGTH(adjudication) BETWEEN 1 AND 500 AND CHAR_LENGTH(TRIM(adjudication)) > 0 AND NOT REGEXP_LIKE(adjudication, '[[:cntrl:]]')),
    CONSTRAINT ck_s2_character_check_proficiency CHECK ((check_kind = _binary'ABILITY' AND proficiency_module IS NULL AND proficiency_type IS NULL AND proficiency_key IS NULL) OR (check_kind IN (_binary'SKILL', _binary'TOOL') AND proficiency_module IS NOT NULL AND proficiency_type IS NOT NULL AND proficiency_key IS NOT NULL AND REGEXP_LIKE(CONVERT(proficiency_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_module USING latin1), '[^a-z0-9_]', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_key USING latin1), '[^a-z0-9_.]', 'c') AND ((check_kind = _binary'SKILL' AND proficiency_type = _binary'character.proficiency' AND REGEXP_LIKE(CONVERT(proficiency_key USING latin1), '^proficiency[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c')) OR (check_kind = _binary'TOOL' AND proficiency_type = _binary'character.tool' AND REGEXP_LIKE(CONVERT(proficiency_key USING latin1), '^tool[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c'))))),
    CONSTRAINT ck_s2_character_check_dice CHECK (effective_advantage IN (0, 1) AND effective_disadvantage IN (0, 1) AND first_d20 BETWEEN 1 AND 20 AND selected_d20 BETWEEN 1 AND 20 AND ((effective_advantage = effective_disadvantage AND second_d20 IS NULL AND selected_d20 = first_d20) OR (effective_advantage <> effective_disadvantage AND second_d20 IS NOT NULL AND second_d20 BETWEEN 1 AND 20 AND selected_d20 = IF(effective_advantage = 1, GREATEST(first_d20, second_d20), LEAST(first_d20, second_d20))))),
    CONSTRAINT ck_s2_character_check_result CHECK (ability_modifier BETWEEN -5 AND 10 AND proficiency_bonus BETWEEN 0 AND 6 AND total = selected_d20 + ability_modifier + proficiency_bonus AND success IN (0, 1) AND success = (total >= difficulty_class) AND (check_kind <> _binary'ABILITY' OR proficiency_bonus = 0))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER s2_character_check_insert BEFORE INSERT ON s2_character_check FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND event_kind = _binary'CHECK_RESOLVED' AND hit_die_roll IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 character check event mismatch';
    END IF;
END$$
CREATE TRIGGER s2_character_check_update BEFORE UPDATE ON s2_character_check FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character check fact';
END$$
CREATE TRIGGER s2_character_check_delete BEFORE DELETE ON s2_character_check FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 character check fact';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (29, 'V029__schema2-character-check-history.sql', 'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b');
