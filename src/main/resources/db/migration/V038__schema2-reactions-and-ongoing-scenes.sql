-- Forward-only DRAFT reactions, fixed environmental suppression and persistent scene ownership.

-- CHECKSUM-SCOPE-BEGIN

-- The DRAFT graph and request profile replace the earlier INTERNAL combat encoding.
-- All earlier combat facts, including ENDED histories, require a separately reviewed transfer or recovery plan.
-- This migration refuses that working set before persistent DDL and never converts or deletes it.
CREATE TEMPORARY TABLE v038_preflight (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO v038_preflight(ok) SELECT CASE WHEN CAST(DATABASE() AS BINARY)=_binary'dnd_tool_se' AND (FIND_IN_SET('STRICT_TRANS_TABLES',@@SESSION.sql_mode)>0 OR FIND_IN_SET('STRICT_ALL_TABLES',@@SESSION.sql_mode)>0) AND NOT EXISTS (SELECT 1 FROM s2_combat_runtime) AND (SELECT COUNT(*) FROM schema_meta)=37 AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version=1 AND CAST(script_name AS BINARY)=_binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version=2 AND CAST(script_name AS BINARY)=_binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version=3 AND CAST(script_name AS BINARY)=_binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version=4 AND CAST(script_name AS BINARY)=_binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY)=_binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version=5 AND CAST(script_name AS BINARY)=_binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version=6 AND CAST(script_name AS BINARY)=_binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version=7 AND CAST(script_name AS BINARY)=_binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY)=_binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version=8 AND CAST(script_name AS BINARY)=_binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version=9 AND CAST(script_name AS BINARY)=_binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version=10 AND CAST(script_name AS BINARY)=_binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version=11 AND CAST(script_name AS BINARY)=_binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version=12 AND CAST(script_name AS BINARY)=_binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY)=_binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version=13 AND CAST(script_name AS BINARY)=_binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY)=_binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version=14 AND CAST(script_name AS BINARY)=_binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY)=_binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version=15 AND CAST(script_name AS BINARY)=_binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version=16 AND CAST(script_name AS BINARY)=_binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version=17 AND CAST(script_name AS BINARY)=_binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY)=_binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version=18 AND CAST(script_name AS BINARY)=_binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY)=_binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version=19 AND CAST(script_name AS BINARY)=_binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version=20 AND CAST(script_name AS BINARY)=_binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version=21 AND CAST(script_name AS BINARY)=_binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version=22 AND CAST(script_name AS BINARY)=_binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY)=_binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version=23 AND CAST(script_name AS BINARY)=_binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY)=_binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version=24 AND CAST(script_name AS BINARY)=_binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY)=_binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version=25 AND CAST(script_name AS BINARY)=_binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY)=_binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version=26 AND CAST(script_name AS BINARY)=_binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY)=_binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version=27 AND CAST(script_name AS BINARY)=_binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version=28 AND CAST(script_name AS BINARY)=_binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a') OR (schema_version=29 AND CAST(script_name AS BINARY)=_binary'V029__schema2-character-check-history.sql' AND CAST(script_sha256 AS BINARY)=_binary'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b') OR (schema_version=30 AND CAST(script_name AS BINARY)=_binary'V030__schema2-equipment-health-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3') OR (schema_version=31 AND CAST(script_name AS BINARY)=_binary'V031__schema2-equipment-health-history.sql' AND CAST(script_sha256 AS BINARY)=_binary'1b9f8959ad5c3d6e4b417d2672a7a06cf8df572e9059e4addfde01856e57b79e') OR (schema_version=32 AND CAST(script_name AS BINARY)=_binary'V032__schema2-combat-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'e1bc7521e953b640b42c869f734961d8e9153e2036788468c9ea13db39be5cae') OR (schema_version=33 AND CAST(script_name AS BINARY)=_binary'V033__schema2-combat-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'df5ddd066e6694d836daf2891ead4271e5ae6e2ed1cb2bf13a009d75d72c83e7') OR (schema_version=34 AND CAST(script_name AS BINARY)=_binary'V034__schema2-combat-time-ground.sql' AND CAST(script_sha256 AS BINARY)=_binary'b9872fe91f87cf0d0c8cb2c16e955c6d0e4e566aed52d56071afa89217affe1f') OR (schema_version=35 AND CAST(script_name AS BINARY)=_binary'V035__schema2-spell-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'80765ab1aa82342d0d1d6ed54f38df6caa94b539ddf19d2ca3f4872fd5ecfdaf') OR (schema_version=36 AND CAST(script_name AS BINARY)=_binary'V036__schema2-spell-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'd92de4fe6fd7b76abd3a8b4b63dce7ab98bb38352d714b9a34a61498b1f7b689') OR (schema_version=37 AND CAST(script_name AS BINARY)=_binary'V037__schema2-wizard-environment-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'7937263eb20e92adab82785aed6dead9ad99bd5fa46c941505c483bbc0f68cfb'))=37 AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('s2_combat_ready_input','s2_combat_environment','s2_character_spellbook_command'))=0 THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v038_preflight;

-- Wizard knowledge and prepared-list facts retain the character transaction owner.
ALTER TABLE s2_character_event DROP CHECK ck_s2_character_command_0;
ALTER TABLE s2_character_event ADD CONSTRAINT ck_s2_character_command_0 CHECK (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED', _binary'RESOURCE_SPENT', _binary'RESOURCES_RECOVERED', _binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED', _binary'CHECK_RESOLVED', _binary'ITEM_ACQUIRED', _binary'ITEM_REMOVED', _binary'ITEM_EQUIPPED', _binary'ITEM_UNEQUIPPED', _binary'HEALTH_EFFECT_RESOLVED', _binary'DEATH_SAVE_RESOLVED', _binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_DEATH_SAVE_RESOLVED', _binary'COMBAT_TIME_ADVANCED', _binary'COMBAT_RESOURCE_SPENT', _binary'COMBAT_HEALING_APPLIED', _binary'SPELLBOOK_INITIALIZED', _binary'SPELLBOOK_ADVANCED', _binary'SPELLBOOK_PREPARED'));
ALTER TABLE s2_character_event_state
    ADD spellbook_casting_module VARBINARY(64) NULL,
    ADD spellbook_casting_type VARBINARY(40) NULL,
    ADD spellbook_casting_key VARBINARY(128) NULL,
    ADD spellbook_learned_level INT NULL,
    ADD learned_spell_count INT NOT NULL DEFAULT 0,
    ADD prepared_spell_count INT NOT NULL DEFAULT 0,
    ADD spellbook_prepared_rest_minute BIGINT NULL,
    ADD spellbook_prepared_rest_second INT NULL,
    ADD CONSTRAINT ck_s238_spellbook CHECK (
        (spellbook_casting_module IS NULL AND spellbook_casting_type IS NULL AND spellbook_casting_key IS NULL
         AND spellbook_learned_level IS NULL AND learned_spell_count=0 AND prepared_spell_count=0
         AND spellbook_prepared_rest_minute IS NULL AND spellbook_prepared_rest_second IS NULL)
        OR (spellbook_casting_module IS NOT NULL AND OCTET_LENGTH(spellbook_casting_module) BETWEEN 1 AND 64
         AND spellbook_casting_type IS NOT NULL AND spellbook_casting_type=_binary'character.wizard_spellcasting'
         AND spellbook_casting_key IS NOT NULL AND OCTET_LENGTH(spellbook_casting_key) BETWEEN 1 AND 128
         AND spellbook_learned_level IS NOT NULL AND spellbook_learned_level BETWEEN 1 AND 5
         AND learned_spell_count=4+2*spellbook_learned_level AND prepared_spell_count BETWEEN 1 AND 15
         AND ((spellbook_prepared_rest_minute IS NULL AND spellbook_prepared_rest_second IS NULL)
           OR (spellbook_prepared_rest_minute IS NOT NULL AND spellbook_prepared_rest_second IS NOT NULL
             AND spellbook_prepared_rest_minute BETWEEN 0 AND 1000000000 AND spellbook_prepared_rest_second BETWEEN 0 AND 59))));
CREATE TABLE s2_character_event_learned_spell (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    spell_index INT NOT NULL,
    spell_module VARBINARY(64) NOT NULL,
    spell_type VARBINARY(40) NOT NULL,
    spell_key VARBINARY(128) NOT NULL,
    acquired_class_level INT NOT NULL,
    PRIMARY KEY(character_id,event_sequence,phase,spell_index),
    UNIQUE KEY uq_s238_learned(character_id,event_sequence,phase,spell_module,spell_type,spell_key),
    CONSTRAINT fk_s238_learned FOREIGN KEY(character_id,event_sequence,phase) REFERENCES s2_character_event_state(character_id,event_sequence,phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_learned CHECK(spell_index BETWEEN 0 AND 13 AND spell_type=_binary'adventure.spell'
        AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128 AND acquired_class_level BETWEEN 1 AND 5)) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE s2_character_event_prepared_spell (
    character_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    spell_index INT NOT NULL,
    spell_module VARBINARY(64) NOT NULL,
    spell_type VARBINARY(40) NOT NULL,
    spell_key VARBINARY(128) NOT NULL,
    PRIMARY KEY(character_id,event_sequence,phase,spell_index),
    UNIQUE KEY uq_s238_prepared(character_id,event_sequence,phase,spell_module,spell_type,spell_key),
    CONSTRAINT fk_s238_prepared FOREIGN KEY(character_id,event_sequence,phase) REFERENCES s2_character_event_state(character_id,event_sequence,phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_prepared CHECK(spell_index BETWEEN 0 AND 14 AND spell_type=_binary'adventure.spell'
        AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128)) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE s2_character_spellbook_command (
    character_id VARBINARY(16) NOT NULL, event_sequence INT NOT NULL,
    casting_module VARBINARY(64) NOT NULL, casting_type VARBINARY(40) NOT NULL, casting_key VARBINARY(128) NOT NULL,
    learned_count INT NOT NULL, prepared_count INT NOT NULL,
    PRIMARY KEY(character_id,event_sequence),
    CONSTRAINT fk_s238_book_cmd FOREIGN KEY(character_id,event_sequence) REFERENCES s2_character_event(character_id,event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_book_cmd CHECK(casting_type=_binary'character.wizard_spellcasting' AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64
        AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128 AND learned_count IN(0,2,6) AND prepared_count BETWEEN 1 AND 15)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE s2_character_spellbook_choice (
    character_id VARBINARY(16) NOT NULL, event_sequence INT NOT NULL, choice_kind VARBINARY(8) NOT NULL, spell_index INT NOT NULL,
    spell_module VARBINARY(64) NOT NULL, spell_type VARBINARY(40) NOT NULL, spell_key VARBINARY(128) NOT NULL,
    PRIMARY KEY(character_id,event_sequence,choice_kind,spell_index),
    UNIQUE KEY uq_s238_book_choice(character_id,event_sequence,choice_kind,spell_module,spell_type,spell_key),
    CONSTRAINT fk_s238_book_choice FOREIGN KEY(character_id,event_sequence) REFERENCES s2_character_spellbook_command(character_id,event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_book_choice CHECK(((choice_kind=_binary'LEARNED' AND spell_index BETWEEN 0 AND 5) OR (choice_kind=_binary'PREPARED' AND spell_index BETWEEN 0 AND 14))
        AND spell_type=_binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;
DELIMITER $$
CREATE TRIGGER s2_spellbook_command_insert BEFORE INSERT ON s2_character_spellbook_command FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM s2_character_event WHERE character_id=NEW.character_id AND event_sequence=NEW.event_sequence
       AND ((event_kind=_binary'SPELLBOOK_INITIALIZED' AND NEW.learned_count=6) OR (event_kind=_binary'SPELLBOOK_ADVANCED' AND NEW.learned_count=2) OR (event_kind=_binary'SPELLBOOK_PREPARED' AND NEW.learned_count=0))) THEN
       SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Schema2 spellbook command mismatch';
    END IF;
END$$
CREATE TRIGGER s2_character_event_learned_spell_update BEFORE UPDATE ON s2_character_event_learned_spell FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
CREATE TRIGGER s2_character_event_learned_spell_delete BEFORE DELETE ON s2_character_event_learned_spell FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
CREATE TRIGGER s2_character_event_prepared_spell_update BEFORE UPDATE ON s2_character_event_prepared_spell FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
CREATE TRIGGER s2_character_event_prepared_spell_delete BEFORE DELETE ON s2_character_event_prepared_spell FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
CREATE TRIGGER s2_character_spellbook_command_update BEFORE UPDATE ON s2_character_spellbook_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
CREATE TRIGGER s2_character_spellbook_command_delete BEFORE DELETE ON s2_character_spellbook_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
CREATE TRIGGER s2_character_spellbook_choice_update BEFORE UPDATE ON s2_character_spellbook_choice FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
CREATE TRIGGER s2_character_spellbook_choice_delete BEFORE DELETE ON s2_character_spellbook_choice FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 character spellbook fact';
END$$
DELIMITER ;

ALTER TABLE s2_combat_event ADD ready_key VARBINARY(16) NULL, ADD zone_key VARBINARY(16) NULL, ADD seconds INT NOT NULL DEFAULT 0;

ALTER TABLE s2_combat_event DROP CHECK ck_c33_2;

ALTER TABLE s2_combat_event ADD CONSTRAINT ck_c33_2 CHECK (command_kind IN (_binary'START', _binary'END_TURN', _binary'DASH', _binary'DISENGAGE', _binary'DODGE', _binary'STAND_UP', _binary'BEGIN_ATTACK', _binary'ATTACK', _binary'FORFEIT_ATTACKS', _binary'MOVE', _binary'PASS_REACTION', _binary'DECLARE_OPPORTUNITY_ATTACK', _binary'DECLARE_SHIELD', _binary'DECLARE_COUNTERSPELL', _binary'RELEASE_READY', _binary'ADVANCE_WINDOW', _binary'CONTINUE_MOVEMENT', _binary'CONTINUE_FLOW', _binary'CAST', _binary'READY_ATTACK', _binary'READY_MOVE', _binary'READY_SPELL', _binary'CREATE_ENVIRONMENT_ZONE', _binary'END_ENVIRONMENT_ZONE', _binary'EXPLORATION_HEALTH', _binary'EXPLORATION_ADVANCE_TIME', _binary'EXPLORATION_MOVE', _binary'REENTER_COMBAT', _binary'CLOSE_SCENE', _binary'END_CONCENTRATION', _binary'END_COMBAT') AND event_sequence BETWEEN 1 AND 128 AND OCTET_LENGTH(request_digest)=64 AND NOT REGEXP_LIKE(CONVERT(request_digest USING latin1),'[^0-9a-f]') AND ((command_kind=_binary'START' AND event_sequence=1 AND actor_id IS NULL) OR (command_kind<>_binary'START' AND event_sequence>1 AND actor_id IS NOT NULL)) AND ((command_kind IN (_binary'ATTACK',_binary'EXPLORATION_HEALTH') AND target_id IS NOT NULL) OR (command_kind NOT IN (_binary'ATTACK',_binary'EXPLORATION_HEALTH') AND target_id IS NULL)) AND ((command_kind IN (_binary'ATTACK',_binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NOT NULL) OR (command_kind NOT IN (_binary'ATTACK',_binary'DECLARE_OPPORTUNITY_ATTACK') AND item_id IS NULL)) AND ((command_kind IN (_binary'PASS_REACTION',_binary'DECLARE_OPPORTUNITY_ATTACK',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL',_binary'RELEASE_READY',_binary'ADVANCE_WINDOW',_binary'CONTINUE_MOVEMENT',_binary'CONTINUE_FLOW') AND flow_key IS NOT NULL AND leaf_key IS NOT NULL AND window_key IS NOT NULL AND ((command_kind IN (_binary'CONTINUE_MOVEMENT',_binary'CONTINUE_FLOW') AND opportunity_key IS NULL) OR (command_kind IN (_binary'PASS_REACTION',_binary'DECLARE_OPPORTUNITY_ATTACK',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL',_binary'RELEASE_READY',_binary'ADVANCE_WINDOW') AND opportunity_key IS NOT NULL))) OR (command_kind NOT IN (_binary'PASS_REACTION',_binary'DECLARE_OPPORTUNITY_ATTACK',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL',_binary'RELEASE_READY',_binary'ADVANCE_WINDOW',_binary'CONTINUE_MOVEMENT',_binary'CONTINUE_FLOW') AND flow_key IS NULL AND leaf_key IS NULL AND window_key IS NULL AND opportunity_key IS NULL)) AND ((command_kind=_binary'RELEASE_READY' AND ready_key IS NOT NULL) OR (command_kind<>_binary'RELEASE_READY' AND ready_key IS NULL)) AND ((command_kind=_binary'END_ENVIRONMENT_ZONE' AND zone_key IS NOT NULL) OR (command_kind<>_binary'END_ENVIRONMENT_ZONE' AND zone_key IS NULL)) AND ((command_kind=_binary'EXPLORATION_ADVANCE_TIME' AND seconds BETWEEN 1 AND 60) OR (command_kind<>_binary'EXPLORATION_ADVANCE_TIME' AND seconds=0)) AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(operation_id) = 16 AND (ORD(SUBSTRING(operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(operation_id, 9, 1)) & 192) = 128) AND (actor_id IS NULL OR (OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128)) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (item_id IS NULL OR (OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)) AND (flow_key IS NULL OR (OCTET_LENGTH(flow_key) = 16 AND (ORD(SUBSTRING(flow_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(flow_key, 9, 1)) & 192) = 128)) AND (leaf_key IS NULL OR (OCTET_LENGTH(leaf_key) = 16 AND (ORD(SUBSTRING(leaf_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(leaf_key, 9, 1)) & 192) = 128)) AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)) AND (ready_key IS NULL OR (OCTET_LENGTH(ready_key) = 16 AND (ORD(SUBSTRING(ready_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(ready_key, 9, 1)) & 192) = 128)) AND (zone_key IS NULL OR (OCTET_LENGTH(zone_key) = 16 AND (ORD(SUBSTRING(zone_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(zone_key, 9, 1)) & 192) = 128)));

ALTER TABLE s2_combat_state MODIFY combat_status VARBINARY(16) NOT NULL;

ALTER TABLE s2_combat_state DROP CHECK ck_c33_10;

ALTER TABLE s2_combat_state ADD CONSTRAINT ck_c33_10 CHECK (state_index = 0 AND rule_type = _binary'adventure.combat' AND combat_status IN (_binary'ACTIVE', _binary'EXPLORATION', _binary'ENDED') AND round_number >= 1 AND turn_number >= 1 AND active_ordinal BETWEEN 0 AND 15 AND ((phase = _binary'AFTER' AND row_version = event_sequence - 1) OR (phase = _binary'BEFORE' AND event_sequence > 1 AND row_version = event_sequence - 2)) AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128);

ALTER TABLE s2_combat_state ADD ready_count INT NOT NULL DEFAULT 0, ADD trigger_count INT NOT NULL DEFAULT 0, ADD shield_count INT NOT NULL DEFAULT 0, ADD holder_count INT NOT NULL DEFAULT 0, ADD zone_count INT NOT NULL DEFAULT 0, ADD cause_count INT NOT NULL DEFAULT 0, ADD CONSTRAINT ck_s238_state_counts CHECK (ready_count BETWEEN 0 AND 128 AND trigger_count BETWEEN 0 AND 1024 AND shield_count BETWEEN 0 AND 128 AND holder_count BETWEEN 0 AND 16 AND zone_count BETWEEN 0 AND 16 AND cause_count BETWEEN 0 AND 512);

ALTER TABLE s2_combat_flow ADD movement_count INT NOT NULL DEFAULT 0, ADD release_count INT NOT NULL DEFAULT 0, ADD check_count INT NOT NULL DEFAULT 0, ADD CONSTRAINT ck_s238_flow_counts CHECK (movement_count BETWEEN 0 AND 1024 AND release_count BETWEEN 0 AND 1024 AND check_count BETWEEN 0 AND 1024);

ALTER TABLE s2_combat_frame DROP CHECK ck_c33_14;

ALTER TABLE s2_combat_frame ADD CONSTRAINT ck_c33_14 CHECK (frame_index BETWEEN 0 AND 1023 AND frame_kind IN (_binary'MOVEMENT', _binary'ATTACK', _binary'CAST', _binary'READY_RELEASE', _binary'HEALTH', _binary'SAVING_THROW', _binary'REACTION_SELECTION') AND invocation IN (_binary'ROOT', _binary'REACTION', _binary'CONTINUATION') AND frame_status IN (_binary'RUNNABLE', _binary'WAITING_WINDOW', _binary'WAITING_CHILD', _binary'PENDING', _binary'DECIDED', _binary'TERMINAL') AND stage IN (_binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'MOVEMENT_CONTINUE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'ATTACK_RESOLVED', _binary'CAST_BEFORE_COMPLETION', _binary'CAST_RESOLVED', _binary'READY_TRIGGER_AFTER', _binary'READY_RELEASE_RESOLVED', _binary'SAVING_THROW_RESOLVED', _binary'HEALTH_RESOLVED', _binary'MOVEMENT_COMPLETED', _binary'MOVEMENT_STOPPED') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(owner_key) = 16 AND (ORD(SUBSTRING(owner_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(owner_key, 9, 1)) & 192) = 128 AND (parent_frame_key IS NULL OR (OCTET_LENGTH(parent_frame_key) = 16 AND (ORD(SUBSTRING(parent_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(parent_frame_key, 9, 1)) & 192) = 128)) AND (window_key IS NULL OR (OCTET_LENGTH(window_key) = 16 AND (ORD(SUBSTRING(window_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(window_key, 9, 1)) & 192) = 128)) AND (opportunity_key IS NULL OR (OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128)));

ALTER TABLE s2_combat_opportunity ADD child_frame_key VARBINARY(16) NULL, ADD option_count INT NOT NULL DEFAULT 0;

ALTER TABLE s2_combat_opportunity DROP CHECK ck_c33_16;

ALTER TABLE s2_combat_opportunity ADD CONSTRAINT ck_c33_16 CHECK (opportunity_index BETWEEN 0 AND 127 AND priority = opportunity_index AND opportunity_status IN (_binary'PENDING', _binary'PASSED', _binary'DECLARED', _binary'COMPLETED', _binary'INELIGIBLE') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(opportunity_key) = 16 AND (ORD(SUBSTRING(opportunity_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(opportunity_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128 AND (selection_frame_key IS NULL OR (OCTET_LENGTH(selection_frame_key) = 16 AND (ORD(SUBSTRING(selection_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(selection_frame_key, 9, 1)) & 192) = 128)) AND (child_frame_key IS NULL OR (OCTET_LENGTH(child_frame_key) = 16 AND (ORD(SUBSTRING(child_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(child_frame_key, 9, 1)) & 192) = 128)) AND option_count BETWEEN 1 AND 32 AND (child_frame_key IS NULL OR (OCTET_LENGTH(child_frame_key) = 16 AND (ORD(SUBSTRING(child_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(child_frame_key, 9, 1)) & 192) = 128)));

ALTER TABLE s2_combat_opportunity DROP COLUMN attack_frame_key;

ALTER TABLE s2_combat_movement_point DROP FOREIGN KEY fk_c33_19_0;

ALTER TABLE s2_combat_movement DROP PRIMARY KEY, ADD PRIMARY KEY (combat_id,event_sequence,phase,flow_index,movement_index), ADD readied TINYINT NOT NULL DEFAULT 0, ADD allowance_feet INT NOT NULL DEFAULT 0, ADD point_count INT NOT NULL DEFAULT 0;

ALTER TABLE s2_combat_movement DROP CHECK ck_c33_18;

ALTER TABLE s2_combat_movement ADD CONSTRAINT ck_c33_18 CHECK (movement_index BETWEEN 0 AND 1023 AND origin_x BETWEEN -10000 AND 10000 AND origin_y BETWEEN -10000 AND 10000 AND cursor BETWEEN 0 AND 64 AND spent BETWEEN 0 AND 1000000 AND movement_status IN (_binary'PENDING', _binary'COMPLETED', _binary'STOPPED') AND phase IN (_binary'BEFORE', _binary'AFTER') AND OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND OCTET_LENGTH(movement_key) = 16 AND (ORD(SUBSTRING(movement_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(movement_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128 AND readied IN (0,1) AND allowance_feet BETWEEN 0 AND 120 AND point_count BETWEEN 1 AND 64);

ALTER TABLE s2_combat_movement_point ADD movement_index INT NOT NULL DEFAULT 0, DROP PRIMARY KEY, ADD PRIMARY KEY (combat_id,event_sequence,phase,flow_index,movement_index,point_index), ADD CONSTRAINT fk_s238_movement_point FOREIGN KEY (combat_id,event_sequence,phase,flow_index,movement_index) REFERENCES s2_combat_movement (combat_id,event_sequence,phase,flow_index,movement_index) ON UPDATE RESTRICT ON DELETE RESTRICT, ADD CONSTRAINT ck_s238_movement_point CHECK (movement_index BETWEEN 0 AND 1023);

ALTER TABLE s2_combat_attack ADD attack_status VARBINARY(24) NOT NULL DEFAULT _binary'RESOLVED', ADD initial_hit TINYINT NOT NULL DEFAULT 0, ADD final_armor_class INT NOT NULL DEFAULT 0, ADD CONSTRAINT ck_s238_attack CHECK (attack_status IN (_binary'HIT_PENDING_DAMAGE', _binary'RESOLVED') AND initial_hit IN (0,1) AND final_armor_class BETWEEN 0 AND 105);

ALTER TABLE s2_combat_cast_command ADD slot_level INT NOT NULL DEFAULT 1, ADD target_cast_frame_key VARBINARY(16) NULL;

ALTER TABLE s2_combat_cast_command DROP CHECK ck_s236_1;

ALTER TABLE s2_combat_cast_command ADD CONSTRAINT ck_s236_1 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND command_index = 0 AND target_count BETWEEN 1 AND 16 AND (material_item_id IS NULL OR (OCTET_LENGTH(material_item_id) = 16 AND (ORD(SUBSTRING(material_item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(material_item_id, 9, 1)) & 192) = 128)) AND casting_type IN (_binary'character.spellcasting', _binary'character.wizard_spellcasting') AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128 AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128 AND slot_level BETWEEN 1 AND 9 AND (target_cast_frame_key IS NULL OR (OCTET_LENGTH(target_cast_frame_key) = 16 AND (ORD(SUBSTRING(target_cast_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_cast_frame_key, 9, 1)) & 192) = 128)));

ALTER TABLE s2_combat_cast ADD cast_status VARBINARY(12) NOT NULL DEFAULT _binary'RESOLVED', ADD target_cast_frame_key VARBINARY(16) NULL, ADD ready_key VARBINARY(16) NULL;

ALTER TABLE s2_combat_cast DROP CHECK ck_s236_5;

ALTER TABLE s2_combat_cast ADD CONSTRAINT ck_s236_5 CHECK (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128 AND event_sequence BETWEEN 1 AND 128 AND cast_index BETWEEN 0 AND 1023 AND target_count BETWEEN 1 AND 16 AND healing_die_count BETWEEN 0 AND 20 AND OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128 AND OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128 AND slot_level BETWEEN 1 AND 9 AND (material_item_id IS NULL OR (OCTET_LENGTH(material_item_id) = 16 AND (ORD(SUBSTRING(material_item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(material_item_id, 9, 1)) & 192) = 128)) AND (effect_key IS NULL OR (OCTET_LENGTH(effect_key) = 16 AND (ORD(SUBSTRING(effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(effect_key, 9, 1)) & 192) = 128)) AND healing_modifier BETWEEN -5 AND 10 AND requested_healing BETWEEN 0 AND 65535 AND applied_healing BETWEEN 0 AND requested_healing AND casting_type IN (_binary'character.spellcasting', _binary'character.wizard_spellcasting') AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128 AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128 AND slot_type = _binary'character.resource' AND OCTET_LENGTH(slot_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(slot_key) BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE', _binary'AFTER') AND flow_index BETWEEN 0 AND 127 AND cast_status IN (_binary'PENDING', _binary'HELD', _binary'RESOLVED', _binary'COUNTERED') AND (target_cast_frame_key IS NULL OR (OCTET_LENGTH(target_cast_frame_key) = 16 AND (ORD(SUBSTRING(target_cast_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_cast_frame_key, 9, 1)) & 192) = 128)) AND (ready_key IS NULL OR (OCTET_LENGTH(ready_key) = 16 AND (ORD(SUBSTRING(ready_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(ready_key, 9, 1)) & 192) = 128)) AND (cast_status=_binary'RESOLVED' OR (effect_key IS NULL AND healing_die_count=0 AND requested_healing=0 AND applied_healing=0)));

CREATE TABLE s2_combat_ready_input (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(8) NOT NULL,
    ready_index INT NOT NULL,
    trigger_kind VARBINARY(48) NOT NULL,
    trigger_subject_id VARBINARY(16) NULL,
    trigger_x INT NULL,
    trigger_y INT NULL,
    response_kind VARBINARY(12) NOT NULL,
    target_id VARBINARY(16) NULL,
    item_id VARBINARY(16) NULL,
    point_count INT NOT NULL,
    target_count INT NOT NULL,
    material_item_id VARBINARY(16) NULL,
    slot_level INT NOT NULL,
    target_cast_frame_key VARBINARY(16) NULL,
    casting_module VARBINARY(64) NULL,
    casting_type VARBINARY(40) NULL,
    casting_key VARBINARY(128) NULL,
    spell_module VARBINARY(64) NULL,
    spell_type VARBINARY(40) NULL,
    spell_key VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, ready_index),
    CONSTRAINT fk_s238_ready_input FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_ready_input CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'COMMAND',_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (trigger_subject_id IS NULL OR (OCTET_LENGTH(trigger_subject_id) = 16 AND (ORD(SUBSTRING(trigger_subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(trigger_subject_id, 9, 1)) & 192) = 128)) AND (trigger_x IS NULL OR trigger_x BETWEEN -10000 AND 10000) AND (trigger_y IS NULL OR trigger_y BETWEEN -10000 AND 10000) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (item_id IS NULL OR (OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)) AND (material_item_id IS NULL OR (OCTET_LENGTH(material_item_id) = 16 AND (ORD(SUBSTRING(material_item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(material_item_id, 9, 1)) & 192) = 128)) AND (target_cast_frame_key IS NULL OR (OCTET_LENGTH(target_cast_frame_key) = 16 AND (ORD(SUBSTRING(target_cast_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_cast_frame_key, 9, 1)) & 192) = 128)) AND ready_index BETWEEN 0 AND 127 AND trigger_kind IN (_binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'CAST_BEFORE_COMPLETION', _binary'CREATURE_ENTER_CELL_AFTER', _binary'ATTACK_COMPLETED', _binary'CAST_COMPLETED') AND response_kind IN (_binary'ATTACK', _binary'MOVE', _binary'SPELL') AND point_count BETWEEN 0 AND 64 AND target_count BETWEEN 0 AND 16 AND slot_level BETWEEN 0 AND 9 AND ((trigger_x IS NULL AND trigger_y IS NULL) OR (trigger_x IS NOT NULL AND trigger_y IS NOT NULL)) AND ((casting_module IS NULL AND casting_type IS NULL AND casting_key IS NULL) OR (casting_module IS NOT NULL AND casting_type IS NOT NULL AND casting_key IS NOT NULL AND casting_type IN (_binary'character.spellcasting',_binary'character.wizard_spellcasting') AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128)) AND ((spell_module IS NULL AND spell_type IS NULL AND spell_key IS NULL) OR (spell_module IS NOT NULL AND spell_type IS NOT NULL AND spell_key IS NOT NULL AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128)) AND ((response_kind=_binary'ATTACK' AND target_id IS NOT NULL AND item_id IS NOT NULL AND point_count=0 AND target_count=0 AND casting_module IS NULL AND spell_module IS NULL AND slot_level=0 AND material_item_id IS NULL AND target_cast_frame_key IS NULL) OR (response_kind=_binary'MOVE' AND target_id IS NULL AND item_id IS NULL AND point_count BETWEEN 1 AND 64 AND target_count=0 AND casting_module IS NULL AND spell_module IS NULL AND slot_level=0 AND material_item_id IS NULL AND target_cast_frame_key IS NULL) OR (response_kind=_binary'SPELL' AND target_id IS NULL AND item_id IS NULL AND point_count=0 AND target_count BETWEEN 1 AND 3 AND casting_module IS NOT NULL AND spell_module IS NOT NULL AND slot_level BETWEEN 1 AND 9)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_ready_point (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(8) NOT NULL,
    ready_index INT NOT NULL,
    point_index INT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, ready_index, point_index),
    CONSTRAINT fk_s238_ready_point FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_ready_point CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'COMMAND',_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND ready_index BETWEEN 0 AND 127 AND point_index BETWEEN 0 AND 63)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_ready_target (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(8) NOT NULL,
    ready_index INT NOT NULL,
    target_index INT NOT NULL,
    target_id VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, ready_index, target_index),
    CONSTRAINT fk_s238_ready_target FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_ready_target CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'COMMAND',_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128) AND ready_index BETWEEN 0 AND 127 AND target_index BETWEEN 0 AND 15)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_zone_command (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    command_index INT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    rule_module VARBINARY(64) NOT NULL,
    rule_type VARBINARY(40) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, command_index),
    CONSTRAINT fk_s238_zone_command FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_zone_command CHECK (event_sequence BETWEEN 1 AND 128 AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND command_index=0 AND (rule_type = _binary'adventure.environment' AND OCTET_LENGTH(rule_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(rule_key) BETWEEN 1 AND 128))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_health_command (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    command_index INT NOT NULL,
    temporary_choice VARBINARY(8) NULL,
    adjudication VARBINARY(16384) NULL,
    rule_module VARBINARY(64) NOT NULL,
    rule_type VARBINARY(40) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    effect_module VARBINARY(64) NULL,
    effect_type VARBINARY(40) NULL,
    effect_key VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, command_index),
    CONSTRAINT fk_s238_health_command FOREIGN KEY (combat_id, event_sequence) REFERENCES s2_combat_event (combat_id, event_sequence) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_health_command CHECK (event_sequence BETWEEN 1 AND 128 AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND command_index=0 AND (temporary_choice IS NULL OR temporary_choice IN (_binary'KEEP',_binary'REPLACE')) AND (rule_type = _binary'adventure.health' AND OCTET_LENGTH(rule_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(rule_key) BETWEEN 1 AND 128) AND ((effect_module IS NULL AND effect_type IS NULL AND effect_key IS NULL) OR (effect_module IS NOT NULL AND effect_type IS NOT NULL AND effect_key IS NOT NULL AND effect_type = _binary'adventure.vital_effect' AND OCTET_LENGTH(effect_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(effect_key) BETWEEN 1 AND 128)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_reaction_option (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    window_index INT NOT NULL,
    opportunity_index INT NOT NULL,
    option_index INT NOT NULL,
    reaction_kind VARBINARY(24) NOT NULL,
    item_id VARBINARY(16) NULL,
    slot_level INT NOT NULL,
    ready_key VARBINARY(16) NULL,
    casting_module VARBINARY(64) NULL,
    casting_type VARBINARY(40) NULL,
    casting_key VARBINARY(128) NULL,
    spell_module VARBINARY(64) NULL,
    spell_type VARBINARY(40) NULL,
    spell_key VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index, option_index),
    CONSTRAINT fk_s238_reaction_option FOREIGN KEY (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index) REFERENCES s2_combat_opportunity (combat_id, event_sequence, phase, flow_index, window_index, opportunity_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_reaction_option CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (item_id IS NULL OR (OCTET_LENGTH(item_id) = 16 AND (ORD(SUBSTRING(item_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(item_id, 9, 1)) & 192) = 128)) AND (ready_key IS NULL OR (OCTET_LENGTH(ready_key) = 16 AND (ORD(SUBSTRING(ready_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(ready_key, 9, 1)) & 192) = 128)) AND flow_index BETWEEN 0 AND 127 AND window_index BETWEEN 0 AND 1023 AND opportunity_index BETWEEN 0 AND 127 AND option_index BETWEEN 0 AND 31 AND reaction_kind IN (_binary'OPPORTUNITY_ATTACK', _binary'SHIELD', _binary'COUNTERSPELL', _binary'READY_RELEASE') AND slot_level BETWEEN 0 AND 9 AND ((casting_module IS NULL AND casting_type IS NULL AND casting_key IS NULL) OR (casting_module IS NOT NULL AND casting_type IS NOT NULL AND casting_key IS NOT NULL AND casting_type IN (_binary'character.spellcasting',_binary'character.wizard_spellcasting') AND OCTET_LENGTH(casting_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(casting_key) BETWEEN 1 AND 128)) AND ((spell_module IS NULL AND spell_type IS NULL AND spell_key IS NULL) OR (spell_module IS NOT NULL AND spell_type IS NOT NULL AND spell_key IS NOT NULL AND spell_type = _binary'adventure.spell' AND OCTET_LENGTH(spell_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(spell_key) BETWEEN 1 AND 128)) AND ((reaction_kind=_binary'OPPORTUNITY_ATTACK' AND item_id IS NOT NULL AND casting_module IS NULL AND spell_module IS NULL AND slot_level=0 AND ready_key IS NULL) OR (reaction_kind IN (_binary'SHIELD',_binary'COUNTERSPELL') AND item_id IS NULL AND casting_module IS NOT NULL AND spell_module IS NOT NULL AND slot_level BETWEEN 1 AND 9 AND ready_key IS NULL) OR (reaction_kind=_binary'READY_RELEASE' AND ready_key IS NOT NULL AND item_id IS NULL AND casting_module IS NULL AND spell_module IS NULL AND slot_level=0)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_window_trigger (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    window_index INT NOT NULL,
    trigger_key VARBINARY(16) NOT NULL,
    trigger_kind VARBINARY(48) NOT NULL,
    source_frame_key VARBINARY(16) NOT NULL,
    subject_id VARBINARY(16) NOT NULL,
    target_id VARBINARY(16) NULL,
    x INT NULL,
    y INT NULL,
    ordinal INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, window_index),
    CONSTRAINT fk_s238_window_trigger FOREIGN KEY (combat_id, event_sequence, phase, flow_index, window_index) REFERENCES s2_combat_window (combat_id, event_sequence, phase, flow_index, window_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_window_trigger CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(trigger_key) = 16 AND (ORD(SUBSTRING(trigger_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(trigger_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(source_frame_key) = 16 AND (ORD(SUBSTRING(source_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(source_frame_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (x IS NULL OR x BETWEEN -10000 AND 10000) AND (y IS NULL OR y BETWEEN -10000 AND 10000) AND trigger_kind IN (_binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'CAST_BEFORE_COMPLETION', _binary'CREATURE_ENTER_CELL_AFTER', _binary'ATTACK_COMPLETED', _binary'CAST_COMPLETED') AND ordinal BETWEEN 0 AND 1023 AND ((x IS NULL AND y IS NULL) OR (x IS NOT NULL AND y IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_ready_release (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    release_index INT NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    ready_key VARBINARY(16) NOT NULL,
    trigger_key VARBINARY(16) NOT NULL,
    completed TINYINT NOT NULL,
    outcome VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, release_index),
    CONSTRAINT fk_s238_ready_release FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_ready_release CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(ready_key) = 16 AND (ORD(SUBSTRING(ready_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(ready_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(trigger_key) = 16 AND (ORD(SUBSTRING(trigger_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(trigger_key, 9, 1)) & 192) = 128) AND release_index BETWEEN 0 AND 1023 AND completed IN (0,1) AND ((completed=0 AND outcome IS NULL) OR (completed=1 AND outcome IS NOT NULL AND OCTET_LENGTH(outcome) BETWEEN 1 AND 128)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_ability_check (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    flow_index INT NOT NULL,
    check_index INT NOT NULL,
    frame_key VARBINARY(16) NOT NULL,
    subject_id VARBINARY(16) NOT NULL,
    check_kind VARBINARY(48) NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    d20 INT NOT NULL,
    ability_modifier INT NOT NULL,
    proficiency_bonus INT NOT NULL,
    difficulty_class INT NOT NULL,
    total INT NOT NULL,
    success TINYINT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, flow_index, check_index),
    CONSTRAINT fk_s238_ability_check FOREIGN KEY (combat_id, event_sequence, phase, flow_index) REFERENCES s2_combat_flow (combat_id, event_sequence, phase, flow_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s238_ability_check CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(frame_key) = 16 AND (ORD(SUBSTRING(frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(frame_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128) AND check_index BETWEEN 0 AND 1023 AND d20 BETWEEN 1 AND 20 AND ability_modifier BETWEEN -5 AND 10 AND proficiency_bonus BETWEEN 0 AND 6 AND difficulty_class BETWEEN 0 AND 32767 AND total=d20+ability_modifier+proficiency_bonus AND success=IF(total>=difficulty_class,1,0) AND ability_key IN (_binary'ability.strength',_binary'ability.dexterity',_binary'ability.constitution',_binary'ability.intelligence',_binary'ability.wisdom',_binary'ability.charisma'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_ready (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    ready_index INT NOT NULL,
    ready_key VARBINARY(16) NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    declared_turn_number BIGINT NOT NULL,
    expires_at_turn_number BIGINT NOT NULL,
    ready_status VARBINARY(12) NOT NULL,
    held_cast_frame_key VARBINARY(16) NULL,
    end_reason VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, ready_index),
    CONSTRAINT fk_s238_ready FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s238_ready0 (combat_id, event_sequence, phase, ready_key),
    CONSTRAINT ck_s238_ready CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(ready_key) = 16 AND (ORD(SUBSTRING(ready_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(ready_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128) AND (held_cast_frame_key IS NULL OR (OCTET_LENGTH(held_cast_frame_key) = 16 AND (ORD(SUBSTRING(held_cast_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(held_cast_frame_key, 9, 1)) & 192) = 128)) AND ready_index BETWEEN 0 AND 127 AND declared_turn_number BETWEEN 1 AND 1000000 AND expires_at_turn_number>declared_turn_number AND ready_status IN (_binary'PREPARING', _binary'ARMED', _binary'RELEASED', _binary'EXPIRED', _binary'LOST'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_trigger (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    trigger_index INT NOT NULL,
    trigger_key VARBINARY(16) NOT NULL,
    trigger_kind VARBINARY(48) NOT NULL,
    source_frame_key VARBINARY(16) NOT NULL,
    subject_id VARBINARY(16) NOT NULL,
    target_id VARBINARY(16) NULL,
    x INT NULL,
    y INT NULL,
    ordinal INT NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, trigger_index),
    CONSTRAINT fk_s238_trigger FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s238_trigger0 (combat_id, event_sequence, phase, trigger_key),
    CONSTRAINT ck_s238_trigger CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(trigger_key) = 16 AND (ORD(SUBSTRING(trigger_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(trigger_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(source_frame_key) = 16 AND (ORD(SUBSTRING(source_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(source_frame_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(subject_id) = 16 AND (ORD(SUBSTRING(subject_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(subject_id, 9, 1)) & 192) = 128) AND (target_id IS NULL OR (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128)) AND (x IS NULL OR x BETWEEN -10000 AND 10000) AND (y IS NULL OR y BETWEEN -10000 AND 10000) AND trigger_index BETWEEN 0 AND 1023 AND trigger_kind IN (_binary'MOVEMENT_LEAVE_REACH_BEFORE', _binary'ATTACK_HIT_BEFORE_DAMAGE', _binary'CAST_BEFORE_COMPLETION', _binary'CREATURE_ENTER_CELL_AFTER', _binary'ATTACK_COMPLETED', _binary'CAST_COMPLETED') AND ordinal BETWEEN 0 AND 1023 AND ((x IS NULL AND y IS NULL) OR (x IS NOT NULL AND y IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_shield (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    shield_index INT NOT NULL,
    effect_key VARBINARY(16) NOT NULL,
    cast_frame_key VARBINARY(16) NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    started_turn_number BIGINT NOT NULL,
    expires_at_turn_number BIGINT NOT NULL,
    effect_status VARBINARY(8) NOT NULL,
    end_reason VARBINARY(128) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, shield_index),
    CONSTRAINT fk_s238_shield FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s238_shield0 (combat_id, event_sequence, phase, effect_key),
    CONSTRAINT ck_s238_shield CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(effect_key) = 16 AND (ORD(SUBSTRING(effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(effect_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(cast_frame_key) = 16 AND (ORD(SUBSTRING(cast_frame_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(cast_frame_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128) AND shield_index BETWEEN 0 AND 127 AND started_turn_number BETWEEN 1 AND 1000000 AND expires_at_turn_number>started_turn_number AND ((effect_status=_binary'ACTIVE' AND end_reason IS NULL) OR (effect_status=_binary'ENDED' AND end_reason IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_concentration (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    holder_index INT NOT NULL,
    actor_id VARBINARY(16) NOT NULL,
    holder_kind VARBINARY(16) NOT NULL,
    source_key VARBINARY(16) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, holder_index),
    CONSTRAINT fk_s238_concentration FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s238_concentration0 (combat_id, event_sequence, phase, actor_id),
    CONSTRAINT ck_s238_concentration CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(actor_id) = 16 AND (ORD(SUBSTRING(actor_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(actor_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(source_key) = 16 AND (ORD(SUBSTRING(source_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(source_key, 9, 1)) & 192) = 128) AND holder_index BETWEEN 0 AND 15 AND holder_kind IN (_binary'BLESS_EFFECT', _binary'READY_SPELL'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_environment (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    zone_index INT NOT NULL,
    zone_key VARBINARY(16) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    created_operation_id VARBINARY(16) NOT NULL,
    ended_operation_id VARBINARY(16) NULL,
    rule_module VARBINARY(64) NOT NULL,
    rule_type VARBINARY(40) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, zone_index),
    CONSTRAINT fk_s238_environment FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s238_environment0 (combat_id, event_sequence, phase, zone_key),
    CONSTRAINT ck_s238_environment CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(zone_key) = 16 AND (ORD(SUBSTRING(zone_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(zone_key, 9, 1)) & 192) = 128) AND x BETWEEN -10000 AND 10000 AND y BETWEEN -10000 AND 10000 AND (OCTET_LENGTH(created_operation_id) = 16 AND (ORD(SUBSTRING(created_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(created_operation_id, 9, 1)) & 192) = 128) AND (ended_operation_id IS NULL OR (OCTET_LENGTH(ended_operation_id) = 16 AND (ORD(SUBSTRING(ended_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(ended_operation_id, 9, 1)) & 192) = 128)) AND zone_index BETWEEN 0 AND 15 AND (rule_type = _binary'adventure.environment' AND OCTET_LENGTH(rule_module) BETWEEN 1 AND 64 AND OCTET_LENGTH(rule_key) BETWEEN 1 AND 128))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_combat_suppression (
    combat_id VARBINARY(16) NOT NULL,
    event_sequence INT NOT NULL,
    phase VARBINARY(6) NOT NULL,
    cause_index INT NOT NULL,
    cause_key VARBINARY(16) NOT NULL,
    zone_key VARBINARY(16) NOT NULL,
    effect_key VARBINARY(16) NOT NULL,
    target_id VARBINARY(16) NOT NULL,
    established_operation_id VARBINARY(16) NOT NULL,
    released_operation_id VARBINARY(16) NULL,
    release_reason VARBINARY(16) NULL,
    PRIMARY KEY (combat_id, event_sequence, phase, cause_index),
    CONSTRAINT fk_s238_suppression FOREIGN KEY (combat_id, event_sequence, phase) REFERENCES s2_combat_state (combat_id, event_sequence, phase) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_s238_suppression0 (combat_id, event_sequence, phase, cause_key),
    CONSTRAINT ck_s238_suppression CHECK (event_sequence BETWEEN 1 AND 128 AND phase IN (_binary'BEFORE',_binary'AFTER') AND (OCTET_LENGTH(combat_id) = 16 AND (ORD(SUBSTRING(combat_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(combat_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(cause_key) = 16 AND (ORD(SUBSTRING(cause_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(cause_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(zone_key) = 16 AND (ORD(SUBSTRING(zone_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(zone_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(effect_key) = 16 AND (ORD(SUBSTRING(effect_key, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(effect_key, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(target_id) = 16 AND (ORD(SUBSTRING(target_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(target_id, 9, 1)) & 192) = 128) AND (OCTET_LENGTH(established_operation_id) = 16 AND (ORD(SUBSTRING(established_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(established_operation_id, 9, 1)) & 192) = 128) AND (released_operation_id IS NULL OR (OCTET_LENGTH(released_operation_id) = 16 AND (ORD(SUBSTRING(released_operation_id, 7, 1)) & 240) = 64 AND (ORD(SUBSTRING(released_operation_id, 9, 1)) & 192) = 128)) AND cause_index BETWEEN 0 AND 511 AND ((released_operation_id IS NULL AND release_reason IS NULL) OR (released_operation_id IS NOT NULL AND release_reason IS NOT NULL AND release_reason IN (_binary'LEFT_ZONE', _binary'ZONE_ENDED', _binary'EFFECT_ENDED'))))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE s2_combat_ready_point ADD CONSTRAINT fk_s238_point_input FOREIGN KEY (combat_id,event_sequence,phase,ready_index) REFERENCES s2_combat_ready_input (combat_id,event_sequence,phase,ready_index) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE s2_combat_ready_target ADD CONSTRAINT fk_s238_target_input FOREIGN KEY (combat_id,event_sequence,phase,ready_index) REFERENCES s2_combat_ready_input (combat_id,event_sequence,phase,ready_index) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE s2_character_combat_input DROP CHECK ck_s2_combat_seconds;

ALTER TABLE s2_character_combat_input ADD CONSTRAINT ck_s2_combat_seconds CHECK (seconds_advanced BETWEEN 0 AND 60 AND (seconds_advanced = 0 OR (damage_amount = 0 AND critical = 0)));

ALTER TABLE s2_character_event DROP CHECK ck_s2_character_command_0;

ALTER TABLE s2_character_event ADD CONSTRAINT ck_s2_character_command_0 CHECK (event_kind IN (_binary'INITIALIZED', _binary'CHARACTER_ADVANCED', _binary'STATISTICS_ADVANCED', _binary'RESOURCE_SPENT', _binary'RESOURCES_RECOVERED', _binary'REST_STARTED', _binary'TIME_ADVANCED', _binary'REST_COMPLETED', _binary'HIT_DIE_SPENT', _binary'REST_ENDED', _binary'CHECK_RESOLVED', _binary'ITEM_ACQUIRED', _binary'ITEM_REMOVED', _binary'ITEM_EQUIPPED', _binary'ITEM_UNEQUIPPED', _binary'HEALTH_EFFECT_RESOLVED', _binary'DEATH_SAVE_RESOLVED', _binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_DEATH_SAVE_RESOLVED', _binary'COMBAT_TIME_ADVANCED', _binary'COMBAT_RESOURCE_SPENT', _binary'COMBAT_HEALING_APPLIED', _binary'SPELLBOOK_INITIALIZED', _binary'SPELLBOOK_ADVANCED', _binary'SPELLBOOK_PREPARED',_binary'COMBAT_ENVIRONMENT_HEALTH'));

DROP TRIGGER s2_combat_cast_command_insert;

DROP TRIGGER s2_character_combat_input_insert;

DROP TRIGGER s2_health_event_insert;

DELIMITER $$

CREATE TRIGGER s2_combat_cast_command_insert BEFORE INSERT ON s2_combat_cast_command FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM s2_combat_event WHERE combat_id=NEW.combat_id AND event_sequence=NEW.event_sequence AND command_kind IN (_binary'CAST',_binary'DECLARE_SHIELD',_binary'DECLARE_COUNTERSPELL')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Schema2 cast command mismatch';
    END IF;
END$$

CREATE TRIGGER s2_character_combat_input_insert BEFORE INSERT ON s2_character_combat_input FOR EACH ROW
BEGIN
    IF NOT EXISTS(SELECT 1 FROM s2_character_event WHERE operation_id=NEW.operation_id AND character_id=NEW.character_id AND event_sequence=NEW.event_sequence AND ((event_kind IN (_binary'COMBAT_DAMAGE_APPLIED',_binary'COMBAT_HEALING_APPLIED',_binary'COMBAT_ENVIRONMENT_HEALTH') AND NEW.seconds_advanced=0) OR (event_kind IN (_binary'COMBAT_DEATH_SAVE_RESOLVED',_binary'COMBAT_RESOURCE_SPENT') AND NEW.seconds_advanced=0 AND NEW.damage_amount=0 AND NEW.critical=0) OR (event_kind=_binary'COMBAT_TIME_ADVANCED' AND NEW.seconds_advanced BETWEEN 1 AND 60 AND NEW.damage_amount=0 AND NEW.critical=0))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Schema2 scene character cause mismatch';
    END IF;
END$$

CREATE TRIGGER s2_health_event_insert BEFORE INSERT ON s2_character_health_event FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM s2_character_event WHERE character_id = NEW.character_id AND event_sequence = NEW.event_sequence AND ((event_kind IN (_binary'HEALTH_EFFECT_RESOLVED',_binary'COMBAT_ENVIRONMENT_HEALTH') AND NEW.effect_module IS NOT NULL AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL) OR (event_kind = _binary'DEATH_SAVE_RESOLVED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NOT NULL) OR (event_kind = _binary'COMBAT_DEATH_SAVE_RESOLVED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count BETWEEN 0 AND 1 AND NEW.death_d20 IS NOT NULL) OR (event_kind IN (_binary'COMBAT_DAMAGE_APPLIED', _binary'COMBAT_TIME_ADVANCED') AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.die_count = 0 AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL) OR (event_kind = _binary'COMBAT_HEALING_APPLIED' AND NEW.effect_module IS NULL AND NEW.temporary_choice IS NULL AND NEW.death_d20 IS NULL AND NEW.recovery_d4 IS NULL))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Schema2 health event mismatch';
    END IF;
END$$

CREATE TRIGGER s2_combat_ready_input_update BEFORE UPDATE ON s2_combat_ready_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_input_delete BEFORE DELETE ON s2_combat_ready_input FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_point_update BEFORE UPDATE ON s2_combat_ready_point FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_point_delete BEFORE DELETE ON s2_combat_ready_point FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_target_update BEFORE UPDATE ON s2_combat_ready_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_target_delete BEFORE DELETE ON s2_combat_ready_target FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_zone_command_update BEFORE UPDATE ON s2_combat_zone_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_zone_command_delete BEFORE DELETE ON s2_combat_zone_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_health_command_update BEFORE UPDATE ON s2_combat_health_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_health_command_delete BEFORE DELETE ON s2_combat_health_command FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_reaction_option_update BEFORE UPDATE ON s2_combat_reaction_option FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_reaction_option_delete BEFORE DELETE ON s2_combat_reaction_option FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_window_trigger_update BEFORE UPDATE ON s2_combat_window_trigger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_window_trigger_delete BEFORE DELETE ON s2_combat_window_trigger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_release_update BEFORE UPDATE ON s2_combat_ready_release FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_release_delete BEFORE DELETE ON s2_combat_ready_release FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ability_check_update BEFORE UPDATE ON s2_combat_ability_check FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ability_check_delete BEFORE DELETE ON s2_combat_ability_check FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_update BEFORE UPDATE ON s2_combat_ready FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_ready_delete BEFORE DELETE ON s2_combat_ready FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_trigger_update BEFORE UPDATE ON s2_combat_trigger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_trigger_delete BEFORE DELETE ON s2_combat_trigger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_shield_update BEFORE UPDATE ON s2_combat_shield FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_shield_delete BEFORE DELETE ON s2_combat_shield FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_concentration_update BEFORE UPDATE ON s2_combat_concentration FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_concentration_delete BEFORE DELETE ON s2_combat_concentration FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_environment_update BEFORE UPDATE ON s2_combat_environment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_environment_delete BEFORE DELETE ON s2_combat_environment FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_suppression_update BEFORE UPDATE ON s2_combat_suppression FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

CREATE TRIGGER s2_combat_suppression_delete BEFORE DELETE ON s2_combat_suppression FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 scene execution fact';
END$$

DELIMITER ;

-- CHECKSUM-SCOPE-END
INSERT INTO schema_meta (schema_version, script_name, script_sha256)
VALUES (38, 'V038__schema2-reactions-and-ongoing-scenes.sql', '3449378f943deea0fee5b3711c13f2a67fde7edaafe0646f6b650a513de37ee5');
