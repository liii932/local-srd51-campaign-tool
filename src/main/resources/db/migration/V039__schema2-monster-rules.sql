-- Forward-only typed monster author material and whole-atom provenance.

-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v039_preflight (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO v039_preflight(ok) SELECT CASE WHEN CAST(DATABASE() AS BINARY)=_binary'dnd_tool_se' AND (FIND_IN_SET('STRICT_TRANS_TABLES',@@SESSION.sql_mode)>0 OR FIND_IN_SET('STRICT_ALL_TABLES',@@SESSION.sql_mode)>0) AND (SELECT COUNT(*) FROM schema_meta)=38 AND (SELECT COUNT(*) FROM schema_meta WHERE (schema_version=1 AND CAST(script_name AS BINARY)=_binary'V001__stage1_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644') OR (schema_version=2 AND CAST(script_name AS BINARY)=_binary'V002__stage2_module_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8') OR (schema_version=3 AND CAST(script_name AS BINARY)=_binary'V003__builtin_module_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225') OR (schema_version=4 AND CAST(script_name AS BINARY)=_binary'V004__release_builtin_module.sql' AND CAST(script_sha256 AS BINARY)=_binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f') OR (schema_version=5 AND CAST(script_name AS BINARY)=_binary'V005__stage2_character_event_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6') OR (schema_version=6 AND CAST(script_name AS BINARY)=_binary'V006__stage2_character_field_value_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b') OR (schema_version=7 AND CAST(script_name AS BINARY)=_binary'V007__character_creation_idempotency.sql' AND CAST(script_sha256 AS BINARY)=_binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef') OR (schema_version=8 AND CAST(script_name AS BINARY)=_binary'V008__stage2_simple_item_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709') OR (schema_version=9 AND CAST(script_name AS BINARY)=_binary'V009__stage3_check_execution_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c') OR (schema_version=10 AND CAST(script_name AS BINARY)=_binary'V010__stage3_node_encounter_schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87') OR (schema_version=11 AND CAST(script_name AS BINARY)=_binary'V011__complete_character_catalog_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb') OR (schema_version=12 AND CAST(script_name AS BINARY)=_binary'V012__level_one_character_creation.sql' AND CAST(script_sha256 AS BINARY)=_binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2') OR (schema_version=13 AND CAST(script_name AS BINARY)=_binary'V013__level_advancement_hit_dice.sql' AND CAST(script_sha256 AS BINARY)=_binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d') OR (schema_version=14 AND CAST(script_name AS BINARY)=_binary'V014__class_feature_lifecycle.sql' AND CAST(script_sha256 AS BINARY)=_binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361') OR (schema_version=15 AND CAST(script_name AS BINARY)=_binary'V015__multiclass_asi_feat_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd') OR (schema_version=16 AND CAST(script_name AS BINARY)=_binary'V016__starting_proficiency_baseline_draft.sql' AND CAST(script_sha256 AS BINARY)=_binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda') OR (schema_version=17 AND CAST(script_name AS BINARY)=_binary'V017__character_archive_v2_origin.sql' AND CAST(script_sha256 AS BINARY)=_binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0') OR (schema_version=18 AND CAST(script_name AS BINARY)=_binary'V018__multiclass_spell_slot_foundation.sql' AND CAST(script_sha256 AS BINARY)=_binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a') OR (schema_version=19 AND CAST(script_name AS BINARY)=_binary'V019__runtime-language-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173') OR (schema_version=20 AND CAST(script_name AS BINARY)=_binary'V020__runtime-tool-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1') OR (schema_version=21 AND CAST(script_name AS BINARY)=_binary'V021__schema2-runtime-snapshot.sql' AND CAST(script_sha256 AS BINARY)=_binary'4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99') OR (schema_version=22 AND CAST(script_name AS BINARY)=_binary'V022__character-proficiency-grants.sql' AND CAST(script_sha256 AS BINARY)=_binary'9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b') OR (schema_version=23 AND CAST(script_name AS BINARY)=_binary'V023__schema2-runtime-tools.sql' AND CAST(script_sha256 AS BINARY)=_binary'7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0') OR (schema_version=24 AND CAST(script_name AS BINARY)=_binary'V024__schema2-character-grants.sql' AND CAST(script_sha256 AS BINARY)=_binary'8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57') OR (schema_version=25 AND CAST(script_name AS BINARY)=_binary'V025__business-write-guard.sql' AND CAST(script_sha256 AS BINARY)=_binary'cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33') OR (schema_version=26 AND CAST(script_name AS BINARY)=_binary'V026__schema2-initial-plans.sql' AND CAST(script_sha256 AS BINARY)=_binary'06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa') OR (schema_version=27 AND CAST(script_name AS BINARY)=_binary'V027__schema2-character-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f') OR (schema_version=28 AND CAST(script_name AS BINARY)=_binary'V028__schema2-check-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a') OR (schema_version=29 AND CAST(script_name AS BINARY)=_binary'V029__schema2-character-check-history.sql' AND CAST(script_sha256 AS BINARY)=_binary'f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b') OR (schema_version=30 AND CAST(script_name AS BINARY)=_binary'V030__schema2-equipment-health-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3') OR (schema_version=31 AND CAST(script_name AS BINARY)=_binary'V031__schema2-equipment-health-history.sql' AND CAST(script_sha256 AS BINARY)=_binary'1b9f8959ad5c3d6e4b417d2672a7a06cf8df572e9059e4addfde01856e57b79e') OR (schema_version=32 AND CAST(script_name AS BINARY)=_binary'V032__schema2-combat-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'e1bc7521e953b640b42c869f734961d8e9153e2036788468c9ea13db39be5cae') OR (schema_version=33 AND CAST(script_name AS BINARY)=_binary'V033__schema2-combat-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'df5ddd066e6694d836daf2891ead4271e5ae6e2ed1cb2bf13a009d75d72c83e7') OR (schema_version=34 AND CAST(script_name AS BINARY)=_binary'V034__schema2-combat-time-ground.sql' AND CAST(script_sha256 AS BINARY)=_binary'b9872fe91f87cf0d0c8cb2c16e955c6d0e4e566aed52d56071afa89217affe1f') OR (schema_version=35 AND CAST(script_name AS BINARY)=_binary'V035__schema2-spell-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'80765ab1aa82342d0d1d6ed54f38df6caa94b539ddf19d2ca3f4872fd5ecfdaf') OR (schema_version=36 AND CAST(script_name AS BINARY)=_binary'V036__schema2-spell-runtime.sql' AND CAST(script_sha256 AS BINARY)=_binary'd92de4fe6fd7b76abd3a8b4b63dce7ab98bb38352d714b9a34a61498b1f7b689') OR (schema_version=37 AND CAST(script_name AS BINARY)=_binary'V037__schema2-wizard-environment-rules.sql' AND CAST(script_sha256 AS BINARY)=_binary'7937263eb20e92adab82785aed6dead9ad99bd5fa46c941505c483bbc0f68cfb') OR (schema_version=38 AND CAST(script_name AS BINARY)=_binary'V038__schema2-reactions-and-ongoing-scenes.sql' AND CAST(script_sha256 AS BINARY)=_binary'3449378f943deea0fee5b3711c13f2a67fde7edaafe0646f6b650a513de37ee5'))=38 AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('s2_runtime_monster','s2_runtime_monster_action','s2_runtime_monster_stat','s2_runtime_monster_save','s2_runtime_monster_skill','s2_runtime_monster_defense','s2_runtime_monster_condition','s2_runtime_monster_language','s2_runtime_monster_trait','s2_runtime_monster_action_value'))=0 THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v039_preflight;

CREATE TABLE s2_runtime_monster (
    snapshot_id BINARY(16) NOT NULL,
    rule_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, module_key, rule_key),
    UNIQUE KEY uq_run_monster (snapshot_id,rule_index),
    CONSTRAINT fk_run_monster FOREIGN KEY (snapshot_id,module_key) REFERENCES s2_runtime_member(snapshot_id,module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0,1) AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^monster[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND source_page BETWEEN 258 AND 403 AND (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_action (
    snapshot_id BINARY(16) NOT NULL,
    entry_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    enabled TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, rule_module_key, rule_key, collection_key, module_key, entry_key),
    UNIQUE KEY uq_run_monster_action (snapshot_id,entry_index),
    CONSTRAINT fk_run_monster_action_owner FOREIGN KEY (snapshot_id,rule_module_key,rule_key) REFERENCES s2_runtime_monster(snapshot_id,module_key,rule_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_run_monster_action_module FOREIGN KEY (snapshot_id,module_key) REFERENCES s2_runtime_member(snapshot_id,module_key) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_action CHECK (entry_index BETWEEN 0 AND 511 AND enabled IN (0,1) AND collection_key=_binary'actions' AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_.]', 'c') AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_stat (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    size VARBINARY(16) NOT NULL,
    creature_type VARBINARY(16) NOT NULL,
    alignment VARCHAR(120) NOT NULL,
    armor_class INT NOT NULL,
    armor_description VARCHAR(120) NOT NULL,
    hit_points INT NOT NULL,
    hit_dice_count INT NOT NULL,
    hit_dice_sides INT NOT NULL,
    hit_dice_modifier INT NOT NULL,
    walk_speed_feet INT NOT NULL,
    strength INT NOT NULL,
    dexterity INT NOT NULL,
    constitution INT NOT NULL,
    intelligence INT NOT NULL,
    wisdom INT NOT NULL,
    charisma INT NOT NULL,
    darkvision_feet INT NOT NULL,
    blindsight_feet INT NOT NULL,
    tremorsense_feet INT NOT NULL,
    truesight_feet INT NOT NULL,
    passive_perception INT NOT NULL,
    challenge_numerator INT NOT NULL,
    challenge_denominator INT NOT NULL,
    experience_points INT NOT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index),
    CONSTRAINT fk_run_monster_stat FOREIGN KEY (snapshot_id) REFERENCES s2_runtime_snapshot(snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_stat CHECK (((owner_kind=_binary'RULE' AND owner_index BETWEEN 0 AND 255 AND step_index=-1) OR (owner_kind IN (_binary'BEFORE',_binary'AFTER') AND owner_index BETWEEN 0 AND 3071 AND step_index BETWEEN 0 AND 3)) AND size IN (_binary'TINY', _binary'SMALL', _binary'MEDIUM', _binary'LARGE', _binary'HUGE', _binary'GARGANTUAN') AND creature_type IN (_binary'ABERRATION', _binary'BEAST', _binary'CELESTIAL', _binary'CONSTRUCT', _binary'DRAGON', _binary'ELEMENTAL', _binary'FEY', _binary'FIEND', _binary'GIANT', _binary'HUMANOID', _binary'MONSTROSITY', _binary'OOZE', _binary'PLANT', _binary'UNDEAD') AND armor_class BETWEEN 1 AND 30 AND hit_points BETWEEN 1 AND 100000 AND hit_dice_count BETWEEN 1 AND 1000 AND hit_dice_sides IN (4,6,8,10,12,20) AND hit_dice_modifier BETWEEN -5000 AND 10000 AND walk_speed_feet BETWEEN 0 AND 240 AND strength BETWEEN 1 AND 30 AND dexterity BETWEEN 1 AND 30 AND constitution BETWEEN 1 AND 30 AND intelligence BETWEEN 1 AND 30 AND wisdom BETWEEN 1 AND 30 AND charisma BETWEEN 1 AND 30 AND darkvision_feet BETWEEN 0 AND 360 AND blindsight_feet BETWEEN 0 AND 360 AND tremorsense_feet BETWEEN 0 AND 360 AND truesight_feet BETWEEN 0 AND 360 AND passive_perception BETWEEN 1 AND 40 AND challenge_numerator BETWEEN 0 AND 30 AND challenge_denominator IN (1,2,4,8) AND (challenge_denominator=1 OR challenge_numerator=1) AND experience_points BETWEEN 0 AND 200000 AND (CHAR_LENGTH(alignment) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(alignment, '[[:cntrl:]]') AND REGEXP_LIKE(alignment, '[^[:space:]]')) AND (CHAR_LENGTH(armor_description) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(armor_description, '[[:cntrl:]]') AND REGEXP_LIKE(armor_description, '[^[:space:]]')) AND hit_dice_modifier=hit_dice_count*FLOOR((constitution-10)/2) AND hit_points=GREATEST(1,FLOOR(hit_dice_count*(hit_dice_sides+1)/2)+hit_dice_modifier) AND ((size=_binary'TINY' AND hit_dice_sides=4) OR (size=_binary'SMALL' AND hit_dice_sides=6) OR (size=_binary'MEDIUM' AND hit_dice_sides=8) OR (size=_binary'LARGE' AND hit_dice_sides=10) OR (size=_binary'HUGE' AND hit_dice_sides=12) OR (size=_binary'GARGANTUAN' AND hit_dice_sides=20)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_save (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    ordinal INT NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    bonus INT NOT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index, ordinal),
    CONSTRAINT fk_run_monster_save FOREIGN KEY (snapshot_id,owner_kind,owner_index,step_index) REFERENCES s2_runtime_monster_stat (snapshot_id,owner_kind,owner_index,step_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_save CHECK (ordinal BETWEEN 0 AND 5 AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma') AND bonus BETWEEN -5 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_skill (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    ordinal INT NOT NULL,
    skill_key VARBINARY(32) NOT NULL,
    bonus INT NOT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index, ordinal),
    CONSTRAINT fk_run_monster_skill FOREIGN KEY (snapshot_id,owner_kind,owner_index,step_index) REFERENCES s2_runtime_monster_stat (snapshot_id,owner_kind,owner_index,step_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_skill CHECK (ordinal BETWEEN 0 AND 17 AND skill_key IN (_binary'skill.acrobatics', _binary'skill.animal_handling', _binary'skill.arcana', _binary'skill.athletics', _binary'skill.deception', _binary'skill.history', _binary'skill.insight', _binary'skill.intimidation', _binary'skill.investigation', _binary'skill.medicine', _binary'skill.nature', _binary'skill.perception', _binary'skill.performance', _binary'skill.persuasion', _binary'skill.religion', _binary'skill.sleight_of_hand', _binary'skill.stealth', _binary'skill.survival') AND bonus BETWEEN -5 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_defense (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    ordinal INT NOT NULL,
    kind VARBINARY(16) NOT NULL,
    damage_type VARBINARY(16) NOT NULL,
    qualifier VARBINARY(32) NOT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index, ordinal),
    CONSTRAINT fk_run_monster_defense FOREIGN KEY (snapshot_id,owner_kind,owner_index,step_index) REFERENCES s2_runtime_monster_stat (snapshot_id,owner_kind,owner_index,step_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_defense CHECK (ordinal BETWEEN 0 AND 38 AND kind IN (_binary'IMMUNITY', _binary'RESISTANCE', _binary'VULNERABILITY') AND damage_type IN (_binary'ACID', _binary'BLUDGEONING', _binary'COLD', _binary'FIRE', _binary'FORCE', _binary'LIGHTNING', _binary'NECROTIC', _binary'PIERCING', _binary'POISON', _binary'PSYCHIC', _binary'RADIANT', _binary'SLASHING', _binary'THUNDER') AND ((qualifier=_binary'ALL') OR (qualifier=_binary'NONMAGICAL_ATTACKS' AND kind=_binary'RESISTANCE' AND damage_type IN (_binary'BLUDGEONING', _binary'PIERCING', _binary'SLASHING'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_condition (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    ordinal INT NOT NULL,
    condition_key VARBINARY(32) NOT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index, ordinal),
    CONSTRAINT fk_run_monster_condition FOREIGN KEY (snapshot_id,owner_kind,owner_index,step_index) REFERENCES s2_runtime_monster_stat (snapshot_id,owner_kind,owner_index,step_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_condition CHECK (ordinal BETWEEN 0 AND 14 AND condition_key IN (_binary'blinded', _binary'charmed', _binary'deafened', _binary'exhaustion', _binary'frightened', _binary'grappled', _binary'incapacitated', _binary'invisible', _binary'paralyzed', _binary'petrified', _binary'poisoned', _binary'prone', _binary'restrained', _binary'stunned', _binary'unconscious'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_language (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    ordinal INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    understands TINYINT NOT NULL,
    speaks TINYINT NOT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index, ordinal),
    CONSTRAINT fk_run_monster_language FOREIGN KEY (snapshot_id,owner_kind,owner_index,step_index) REFERENCES s2_runtime_monster_stat (snapshot_id,owner_kind,owner_index,step_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_language CHECK (ordinal BETWEEN 0 AND 15 AND (CHAR_LENGTH(name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(name, '[[:cntrl:]]') AND REGEXP_LIKE(name, '[^[:space:]]')) AND understands=1 AND speaks IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_trait (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    ordinal INT NOT NULL,
    trait_key VARBINARY(64) NOT NULL,
    trait_scope VARBINARY(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index, ordinal),
    CONSTRAINT fk_run_monster_trait FOREIGN KEY (snapshot_id,owner_kind,owner_index,step_index) REFERENCES s2_runtime_monster_stat (snapshot_id,owner_kind,owner_index,step_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_trait CHECK (ordinal BETWEEN 0 AND 15 AND REGEXP_LIKE(CONVERT(trait_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(trait_key USING latin1), '[^a-z0-9_.]', 'c') AND trait_scope IN (_binary'PERCEPTION_HEARING_SMELL_ONLY', _binary'ATTACK_ROLL_PACK_TACTICS_ONLY', _binary'UNSUPPORTED') AND (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE s2_runtime_monster_action_value (
    snapshot_id BINARY(16) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    step_index INT NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    mode VARBINARY(32) NOT NULL,
    mechanism VARBINARY(64) NULL,
    save_ability_key VARBINARY(32) NULL,
    save_dc_mode VARBINARY(16) NULL,
    save_dc INT NULL,
    fail_outcome VARBINARY(8) NULL,
    success_outcome VARBINARY(8) NULL,
    damage_dice_count INT NULL,
    damage_dice_sides INT NULL,
    damage_type VARBINARY(16) NULL,
    damage_delivery VARBINARY(32) NULL,
    damage_ability_modifier VARBINARY(32) NULL,
    area_shape VARBINARY(16) NULL,
    area_length_feet INT NULL,
    area_origin_policy VARBINARY(32) NULL,
    recharge_dice_sides INT NULL,
    recharge_minimum_roll INT NULL,
    recharge_turn_start TINYINT NULL,
    recharge_short_rest TINYINT NULL,
    recharge_long_rest TINYINT NULL,
    PRIMARY KEY (snapshot_id, owner_kind, owner_index, step_index),
    CONSTRAINT fk_run_monster_action_value FOREIGN KEY (snapshot_id) REFERENCES s2_runtime_snapshot(snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_run_monster_action_value CHECK (((owner_kind=_binary'ENTRY' AND owner_index BETWEEN 0 AND 511 AND step_index=-1) OR (owner_kind IN (_binary'BEFORE',_binary'AFTER') AND owner_index BETWEEN 0 AND 3071 AND step_index BETWEEN 0 AND 3)) AND (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND source_page BETWEEN 258 AND 403 AND ((mode=_binary'BLOCKED_METADATA' AND mechanism IS NULL AND save_ability_key IS NULL AND save_dc_mode IS NULL AND save_dc IS NULL AND fail_outcome IS NULL AND success_outcome IS NULL AND damage_dice_count IS NULL AND damage_dice_sides IS NULL AND damage_type IS NULL AND damage_delivery IS NULL AND damage_ability_modifier IS NULL AND area_shape IS NULL AND area_length_feet IS NULL AND area_origin_policy IS NULL AND recharge_dice_sides IS NULL AND recharge_minimum_roll IS NULL AND recharge_turn_start IS NULL AND recharge_short_rest IS NULL AND recharge_long_rest IS NULL) OR (mode=_binary'SAVE_DAMAGE_BATCH_V1' AND mechanism IS NOT NULL AND save_ability_key IS NOT NULL AND save_dc_mode IS NOT NULL AND save_dc IS NOT NULL AND fail_outcome IS NOT NULL AND success_outcome IS NOT NULL AND damage_dice_count IS NOT NULL AND damage_dice_sides IS NOT NULL AND damage_type IS NOT NULL AND damage_delivery IS NOT NULL AND damage_ability_modifier IS NOT NULL AND area_shape IS NOT NULL AND area_length_feet IS NOT NULL AND area_origin_policy IS NOT NULL AND recharge_dice_sides IS NOT NULL AND recharge_minimum_roll IS NOT NULL AND recharge_turn_start IS NOT NULL AND recharge_short_rest IS NOT NULL AND recharge_long_rest IS NOT NULL AND mechanism=_binary'HELL_HOUND_FIRE_BREATH_V1' AND save_ability_key=_binary'ability.dexterity' AND save_dc_mode=_binary'FIXED_DC' AND save_dc=12 AND fail_outcome=_binary'FULL' AND success_outcome=_binary'HALF' AND damage_dice_count=6 AND damage_dice_sides=6 AND damage_type=_binary'FIRE' AND damage_delivery=_binary'OTHER_DELIVERY' AND damage_ability_modifier=_binary'NO_ABILITY_MODIFIER' AND area_shape=_binary'CONE' AND area_length_feet=15 AND area_origin_policy=_binary'EXCLUDE_ORIGIN' AND recharge_dice_sides=6 AND recharge_minimum_roll=5 AND recharge_turn_start=1 AND recharge_short_rest=1 AND recharge_long_rest=1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE s2_runtime_proficiency DROP CHECK ck_s2_proficiency_shape;
ALTER TABLE s2_runtime_proficiency ADD CONSTRAINT ck_s2_proficiency_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND category IN (_binary'SKILL', _binary'SAVING_THROW', _binary'ARMOR', _binary'WEAPON') AND ((category IN (_binary'SKILL',_binary'SAVING_THROW') AND ability_key IS NOT NULL AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) OR (category NOT IN (_binary'SKILL',_binary'SAVING_THROW') AND ability_key IS NULL)) AND source_page BETWEEN 3 AND 74);
ALTER TABLE s2_runtime_proficiency_set DROP CHECK ck_s2_proficiency_set_shape;
ALTER TABLE s2_runtime_proficiency_set ADD CONSTRAINT ck_s2_proficiency_set_shape CHECK (rule_index BETWEEN 0 AND 255 AND enabled IN (0, 1) AND category IN (_binary'SKILL', _binary'SAVING_THROW', _binary'ARMOR', _binary'WEAPON', _binary'TOOL') AND minimum_enabled_members BETWEEN 1 AND 128);
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
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'adventure.combat' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'equipment.weapon_attack' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'item',_binary'ability_policy',_binary'dice_count',_binary'dice_sides',_binary'damage_type',_binary'reach_feet'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'character.movement' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'owner',_binary'walk_speed_feet'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'adventure.spell' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND rule_type=_binary'character.spellcasting' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'owner_class',_binary'required_subclass',_binary'required_feature',_binary'slot_resource',_binary'bless',_binary'cure_wounds',_binary'material_item',_binary'ability_key'))
    OR (target_kind=_binary'rule' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND ((rule_type=_binary'character.wizard_spellcasting' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'owner_class',_binary'required_feature',_binary'slot_resource',_binary'rank2_slot_resource',_binary'rank3_slot_resource',_binary'ability_key')) OR (rule_type=_binary'adventure.environment' AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled'))))
    OR (target_kind=_binary'entry' AND rule_type=_binary'character.wizard_spellcasting' AND collection_key=_binary'spells' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'spell',_binary'author_note',_binary'enabled'))
    OR (target_kind=_binary'rule' AND rule_type=_binary'adventure.monster' AND collection_key IS NULL AND entry_module_key IS NULL AND entry_key IS NULL AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'stat_block',_binary'author_note',_binary'enabled'))
    OR (target_kind=_binary'entry' AND rule_type=_binary'adventure.monster' AND collection_key=_binary'actions' AND entry_module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'action_profile',_binary'author_note',_binary'enabled'))
);

DELIMITER $$

CREATE TRIGGER s2_runtime_monster_update BEFORE UPDATE ON s2_runtime_monster FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_delete BEFORE DELETE ON s2_runtime_monster FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_action_update BEFORE UPDATE ON s2_runtime_monster_action FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_action_delete BEFORE DELETE ON s2_runtime_monster_action FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_stat_update BEFORE UPDATE ON s2_runtime_monster_stat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_stat_delete BEFORE DELETE ON s2_runtime_monster_stat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_save_update BEFORE UPDATE ON s2_runtime_monster_save FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_save_delete BEFORE DELETE ON s2_runtime_monster_save FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_skill_update BEFORE UPDATE ON s2_runtime_monster_skill FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_skill_delete BEFORE DELETE ON s2_runtime_monster_skill FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_defense_update BEFORE UPDATE ON s2_runtime_monster_defense FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_defense_delete BEFORE DELETE ON s2_runtime_monster_defense FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_condition_update BEFORE UPDATE ON s2_runtime_monster_condition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_condition_delete BEFORE DELETE ON s2_runtime_monster_condition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_language_update BEFORE UPDATE ON s2_runtime_monster_language FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_language_delete BEFORE DELETE ON s2_runtime_monster_language FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_trait_update BEFORE UPDATE ON s2_runtime_monster_trait FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_trait_delete BEFORE DELETE ON s2_runtime_monster_trait FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_action_value_update BEFORE UPDATE ON s2_runtime_monster_action_value FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER s2_runtime_monster_action_value_delete BEFORE DELETE ON s2_runtime_monster_action_value FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO schema_meta (schema_version,script_name,script_sha256)
VALUES (39,'V039__schema2-monster-rules.sql','1128fbaf4955634a04e206e40a5af0a3e532fc2035511d140ce53a3a9bd71ea4');
