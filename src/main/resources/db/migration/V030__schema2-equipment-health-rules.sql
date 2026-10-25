-- Bounded equipment and direct health typed snapshot mechanisms.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v030_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v030_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0 OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 29
    AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version = 20 AND CAST(script_name AS BINARY) = _binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version = 21 AND CAST(script_name AS BINARY) = _binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY) = _binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version = 22 AND CAST(script_name AS BINARY) = _binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version = 23 AND CAST(script_name AS BINARY) = _binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY) = _binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version = 24 AND CAST(script_name AS BINARY) = _binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY) = _binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version = 25 AND CAST(script_name AS BINARY) = _binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY) = _binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version = 26 AND CAST(script_name AS BINARY) = _binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY) = _binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version = 27 AND CAST(script_name AS BINARY) = _binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY) = _binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version = 28 AND CAST(script_name AS BINARY) = _binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY) = _binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a') OR (schema_version = 29 AND CAST(script_name AS BINARY) = _binary'V029__schema2-character-check-history.sql' AND CAST(script_sha256 AS BINARY) = _binary'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b')) = 29
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('s2_runtime_equipment_item','s2_runtime_health','s2_runtime_vital_effect')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v030_preflight;

CREATE TABLE s2_runtime_equipment_item (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    kind VARBINARY(16) NOT NULL,
    armor_class INT NOT NULL,
    strength_minimum INT NOT NULL,
    stealth_disadvantage TINYINT NOT NULL,
    hands INT NOT NULL,
    proficiency_module VARBINARY(64) NOT NULL,
    proficiency_type VARBINARY(32) NOT NULL,
    proficiency_key VARBINARY(128) NOT NULL,
    stealth_skill_module VARBINARY(64) NULL,
    stealth_skill_type VARBINARY(32) NULL,
    stealth_skill_key VARBINARY(128) NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_run_equipment_item (snapshot_id, rule_index),
    CONSTRAINT fk_run_equipment_item FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_equipment_item_identity CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0,1) AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^item[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_run_equipment_item_0 CHECK (source_page BETWEEN 62 AND 73 AND algorithm = _binary'SRD_2014_EQUIPMENT_V1'),
    CONSTRAINT ck_run_equipment_item_1 CHECK (((kind IN (_binary'LIGHT_ARMOR',_binary'MEDIUM_ARMOR') AND armor_class BETWEEN 10 AND 20 AND strength_minimum=0 AND hands=0) OR (kind=_binary'HEAVY_ARMOR' AND armor_class BETWEEN 10 AND 20 AND strength_minimum IN (0,13,15) AND hands=0) OR (kind=_binary'SHIELD' AND armor_class=2 AND strength_minimum=0 AND stealth_disadvantage=0 AND hands=1) OR (kind IN (_binary'TOOL',_binary'WEAPON') AND armor_class=0 AND strength_minimum=0 AND stealth_disadvantage=0 AND hands IN (1,2)))),
    CONSTRAINT ck_run_equipment_item_2 CHECK ((proficiency_module IS NOT NULL AND proficiency_type IS NOT NULL AND proficiency_key IS NOT NULL AND REGEXP_LIKE(CONVERT(proficiency_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_module USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(proficiency_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_key USING latin1), '[^a-z0-9_.]', 'c')) AND ((kind=_binary'TOOL' AND proficiency_type=_binary'character.tool') OR (kind<>_binary'TOOL' AND proficiency_type=_binary'character.proficiency'))),
    CONSTRAINT ck_run_equipment_item_3 CHECK ((stealth_skill_module IS NULL AND stealth_skill_type IS NULL AND stealth_skill_key IS NULL OR (stealth_skill_module IS NOT NULL AND stealth_skill_type IS NOT NULL AND stealth_skill_key IS NOT NULL AND REGEXP_LIKE(CONVERT(stealth_skill_module USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(stealth_skill_module USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(stealth_skill_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(stealth_skill_key USING latin1), '[^a-z0-9_.]', 'c'))) AND ((stealth_disadvantage=0 AND stealth_skill_module IS NULL) OR (stealth_disadvantage=1 AND stealth_skill_module IS NOT NULL AND stealth_skill_type=_binary'character.proficiency'))),
    CONSTRAINT ck_run_equipment_item_4 CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_health (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_run_health (snapshot_id, rule_index),
    CONSTRAINT fk_run_health FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_health_identity CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0,1) AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^health[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_run_health_0 CHECK (source_page = 98 AND algorithm = _binary'SRD_2014_HEALTH_V1'),
    CONSTRAINT ck_run_health_1 CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_vital_effect (
    snapshot_id VARBINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
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
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_run_vital_effect (snapshot_id, rule_index),
    CONSTRAINT fk_run_vital_effect FOREIGN KEY (snapshot_id, module_key) REFERENCES s2_runtime_member (snapshot_id, module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_vital_effect_identity CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0,1) AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^vital_effect[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_run_vital_effect_0 CHECK (source_page BETWEEN 96 AND 99 AND algorithm = _binary'DIRECT_VITAL_EFFECT_V1'),
    CONSTRAINT ck_run_vital_effect_1 CHECK (dice_count BETWEEN 0 AND 20 AND ((dice_count=0 AND dice_sides=0) OR (dice_count>0 AND dice_sides IN (4,6,8,10,12))) AND modifier BETWEEN -1000 AND 1000),
    CONSTRAINT ck_run_vital_effect_2 CHECK (((kind=_binary'DAMAGE' AND damage_type IS NOT NULL AND damage_type IN (_binary'ACID', _binary'BLUDGEONING', _binary'COLD', _binary'FIRE', _binary'FORCE', _binary'LIGHTNING', _binary'NECROTIC', _binary'PIERCING', _binary'POISON', _binary'PSYCHIC', _binary'RADIANT', _binary'SLASHING', _binary'THUNDER')) OR (kind IN (_binary'HEALING',_binary'TEMP_HP') AND damage_type IS NULL))),
    CONSTRAINT ck_run_vital_effect_3 CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

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
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.class_progression' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner_class', _binary'hit_die_sides', _binary'proficiency_bonus_profile', _binary'supported_maximum_level', _binary'advancement_scope', _binary'multiclass_prerequisite', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.resource' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner_class', _binary'maximum_profile', _binary'recovery_profile', _binary'execution_mode', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.subclass' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner_class', _binary'selection_level', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.feature' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'owner', _binary'level', _binary'feature_kind', _binary'execution_mode', _binary'execution_algorithm', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.feat' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'prerequisite', _binary'execution_mode', _binary'execution_algorithm', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'character.proficiency' AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'category', _binary'ability_key', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type = _binary'catalog.proficiency_set' AND field_key IN (_binary'display_name', _binary'category', _binary'minimum_enabled_members', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type IN (_binary'adventure.rest', _binary'adventure.check') AND field_key IN (_binary'display_name', _binary'description', _binary'source_page', _binary'algorithm', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'character.class_progression' AND collection_key IS NOT NULL AND collection_key = _binary'levels' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'level', _binary'asi_available', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'character.class_progression' AND collection_key IS NOT NULL AND collection_key = _binary'starting_proficiencies' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'growth_grant', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'character.class_progression' AND collection_key IS NOT NULL AND collection_key = _binary'multiclass_proficiencies' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'growth_grant', _binary'author_note', _binary'enabled'))
    OR (target_kind = _binary'entry' AND rule_type = _binary'catalog.proficiency_set' AND collection_key IS NOT NULL AND collection_key = _binary'members' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'proficiency', _binary'author_note', _binary'enabled'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'equipment.item' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'kind',_binary'armor_class',_binary'strength_minimum',_binary'stealth_disadvantage',_binary'hands',_binary'proficiency',_binary'stealth_skill'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'adventure.health' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'adventure.vital_effect' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'kind',_binary'dice_count',_binary'dice_sides',_binary'modifier',_binary'damage_type'))
);

DELIMITER $$
CREATE TRIGGER s2_runtime_equipment_item_update BEFORE UPDATE ON s2_runtime_equipment_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution snapshot';
END$$
CREATE TRIGGER s2_runtime_equipment_item_delete BEFORE DELETE ON s2_runtime_equipment_item FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution snapshot';
END$$
CREATE TRIGGER s2_runtime_health_update BEFORE UPDATE ON s2_runtime_health FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution snapshot';
END$$
CREATE TRIGGER s2_runtime_health_delete BEFORE DELETE ON s2_runtime_health FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution snapshot';
END$$
CREATE TRIGGER s2_runtime_vital_effect_update BEFORE UPDATE ON s2_runtime_vital_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution snapshot';
END$$
CREATE TRIGGER s2_runtime_vital_effect_delete BEFORE DELETE ON s2_runtime_vital_effect FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 execution snapshot';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta(schema_version,script_name,script_sha256)
VALUES (30,'V030__schema2-equipment-health-rules.sql','b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3');
