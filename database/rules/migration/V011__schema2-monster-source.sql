-- Forward-only typed monster author material and whole-atom provenance.

-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v011_preflight (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO rules_v011_preflight(ok) SELECT CASE WHEN CAST(DATABASE() AS BINARY)=_binary'dnd_tool_rules' AND (FIND_IN_SET('STRICT_TRANS_TABLES',@@SESSION.sql_mode)>0 OR FIND_IN_SET('STRICT_ALL_TABLES',@@SESSION.sql_mode)>0) AND (SELECT COUNT(*) FROM rule_schema_meta)=10 AND (SELECT COUNT(*) FROM rule_schema_meta WHERE (schema_version=1 AND CAST(script_name AS BINARY)=_binary'V001__rule-source-schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') OR (schema_version=2 AND CAST(script_name AS BINARY)=_binary'V002__tool-catalog.sql' AND CAST(script_sha256 AS BINARY)=_binary'2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40') OR (schema_version=3 AND CAST(script_name AS BINARY)=_binary'V003__schema2-source-ledger.sql' AND CAST(script_sha256 AS BINARY)=_binary'4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf') OR (schema_version=4 AND CAST(script_name AS BINARY)=_binary'V004__schema2-character-grant-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'9578db2143bfb14eb077e403d5395bb3089a7ab6e54f6926f42b7dbcac4e9269') OR (schema_version=5 AND CAST(script_name AS BINARY)=_binary'V005__schema2-advancement-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'2dfdecf8cb30fcc71f7e011550ce3b32334340aab5ff546f4e71e53e13147e5c') OR (schema_version=6 AND CAST(script_name AS BINARY)=_binary'V006__schema2-check-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'9c495e9af9ce06b1e98fba9e44ef159cebbe8ef5f058254e54f8f83a0c7aca30') OR (schema_version=7 AND CAST(script_name AS BINARY)=_binary'V007__schema2-equipment-health-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'd5bc25f01389e7811280b97c36202b066142efb6ee5521cbb7fccb0def25de45') OR (schema_version=8 AND CAST(script_name AS BINARY)=_binary'V008__schema2-combat-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'eb0bf9e4675cfed03ec5e5e57f026b57e6bd3409edf98eaf6a9e6e680485cfe2') OR (schema_version=9 AND CAST(script_name AS BINARY)=_binary'V009__schema2-spell-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'7ffe0707112cf3051d0a80c346360be5e4f933f8046830321fd3ac4f68196cfc') OR (schema_version=10 AND CAST(script_name AS BINARY)=_binary'V010__schema2-wizard-environment-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'3386950595e0c24eade84c6b9b1c38145ffe8aefcb786b6cc66f663ebd2c3d74'))=10 AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('rule_schema2_monster','rule_schema2_monster_action','rule_schema2_monster_change','rule_schema2_monster_stat','rule_schema2_monster_save','rule_schema2_monster_skill','rule_schema2_monster_defense','rule_schema2_monster_condition','rule_schema2_monster_language','rule_schema2_monster_trait','rule_schema2_monster_action_value'))=0 THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v011_preflight;

CREATE TABLE rule_schema2_monster (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_src_monster FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact(module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_src_monster (artifact_module_key,release_version,rule_module_key,rule_type,rule_key),
    CONSTRAINT ck_src_monster CHECK (author_index BETWEEN 0 AND 63 AND rule_module_key=artifact_module_key AND rule_type=_binary'adventure.monster' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^monster[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND source_page BETWEEN 258 AND 403 AND (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_action (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    entry_key VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_src_monster_action FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact(module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    UNIQUE KEY uq_src_monster_action (artifact_module_key,release_version,rule_module_key,rule_type,rule_key,collection_key,module_key,entry_key),
    CONSTRAINT ck_src_monster_action CHECK (author_index BETWEEN 0 AND 127 AND module_key=artifact_module_key AND rule_type=_binary'adventure.monster' AND collection_key=_binary'actions' AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_.]', 'c') AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^monster[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND REGEXP_LIKE(CONVERT(entry_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(entry_key USING latin1), '[^a-z0-9_.]', 'c') AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_change (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    target_kind VARBINARY(5) NOT NULL,
    rule_module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    collection_key VARBINARY(32) NULL,
    module_key VARBINARY(64) NULL,
    entry_key VARBINARY(64) NULL,
    operation VARBINARY(16) NOT NULL,
    field_key VARBINARY(32) NOT NULL,
    text_value VARCHAR(1000) NULL,
    integer_value INT NULL,
    boolean_value TINYINT NULL,
    atom_kind VARBINARY(16) NULL,
    PRIMARY KEY (artifact_module_key, release_version, author_index),
    CONSTRAINT fk_src_monster_change FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact(module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_change CHECK (author_index BETWEEN 0 AND 255 AND rule_type=_binary'adventure.monster' AND ((target_kind=_binary'rule' AND collection_key IS NULL AND module_key IS NULL AND entry_key IS NULL AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'stat_block',_binary'author_note',_binary'enabled')) OR (target_kind=_binary'entry' AND collection_key IS NOT NULL AND collection_key=_binary'actions' AND module_key IS NOT NULL AND entry_key IS NOT NULL AND field_key IN (_binary'action_profile',_binary'author_note',_binary'enabled'))) AND ((operation=_binary'clear_field' AND field_key=_binary'author_note' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND atom_kind IS NULL) OR (operation=_binary'set_enabled' AND field_key=_binary'enabled' AND text_value IS NULL AND integer_value IS NULL AND atom_kind IS NULL AND boolean_value IS NOT NULL AND boolean_value IN (0,1)) OR (operation=_binary'set_field' AND ((text_value IS NOT NULL)+(integer_value IS NOT NULL)+(boolean_value IS NOT NULL)+(atom_kind IS NOT NULL))=1 AND ((field_key IN (_binary'display_name',_binary'description',_binary'author_note') AND text_value IS NOT NULL) OR (field_key=_binary'source_page' AND integer_value IS NOT NULL AND integer_value BETWEEN 258 AND 403) OR (field_key=_binary'stat_block' AND atom_kind IS NOT NULL AND atom_kind=_binary'STAT_BLOCK') OR (field_key=_binary'action_profile' AND atom_kind IS NOT NULL AND atom_kind=_binary'ACTION_PROFILE')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_stat (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
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
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index),
    CONSTRAINT fk_src_monster_stat FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact(module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_stat CHECK (owner_index BETWEEN 0 AND 255 AND ((owner_kind=_binary'DEFINITION' AND owner_index<64) OR owner_kind=_binary'CHANGE') AND size IN (_binary'TINY', _binary'SMALL', _binary'MEDIUM', _binary'LARGE', _binary'HUGE', _binary'GARGANTUAN') AND creature_type IN (_binary'ABERRATION', _binary'BEAST', _binary'CELESTIAL', _binary'CONSTRUCT', _binary'DRAGON', _binary'ELEMENTAL', _binary'FEY', _binary'FIEND', _binary'GIANT', _binary'HUMANOID', _binary'MONSTROSITY', _binary'OOZE', _binary'PLANT', _binary'UNDEAD') AND armor_class BETWEEN 1 AND 30 AND hit_points BETWEEN 1 AND 100000 AND hit_dice_count BETWEEN 1 AND 1000 AND hit_dice_sides IN (4,6,8,10,12,20) AND hit_dice_modifier BETWEEN -5000 AND 10000 AND walk_speed_feet BETWEEN 0 AND 240 AND strength BETWEEN 1 AND 30 AND dexterity BETWEEN 1 AND 30 AND constitution BETWEEN 1 AND 30 AND intelligence BETWEEN 1 AND 30 AND wisdom BETWEEN 1 AND 30 AND charisma BETWEEN 1 AND 30 AND darkvision_feet BETWEEN 0 AND 360 AND blindsight_feet BETWEEN 0 AND 360 AND tremorsense_feet BETWEEN 0 AND 360 AND truesight_feet BETWEEN 0 AND 360 AND passive_perception BETWEEN 1 AND 40 AND challenge_numerator BETWEEN 0 AND 30 AND challenge_denominator IN (1,2,4,8) AND (challenge_denominator=1 OR challenge_numerator=1) AND experience_points BETWEEN 0 AND 200000 AND (CHAR_LENGTH(alignment) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(alignment, '[[:cntrl:]]') AND REGEXP_LIKE(alignment, '[^[:space:]]')) AND (CHAR_LENGTH(armor_description) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(armor_description, '[[:cntrl:]]') AND REGEXP_LIKE(armor_description, '[^[:space:]]')) AND hit_dice_modifier=hit_dice_count*FLOOR((constitution-10)/2) AND hit_points=GREATEST(1,FLOOR(hit_dice_count*(hit_dice_sides+1)/2)+hit_dice_modifier) AND ((size=_binary'TINY' AND hit_dice_sides=4) OR (size=_binary'SMALL' AND hit_dice_sides=6) OR (size=_binary'MEDIUM' AND hit_dice_sides=8) OR (size=_binary'LARGE' AND hit_dice_sides=10) OR (size=_binary'HUGE' AND hit_dice_sides=12) OR (size=_binary'GARGANTUAN' AND hit_dice_sides=20)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_save (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    ordinal INT NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    bonus INT NOT NULL,
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index, ordinal),
    CONSTRAINT fk_src_monster_save FOREIGN KEY (artifact_module_key,release_version,owner_kind,owner_index) REFERENCES rule_schema2_monster_stat (artifact_module_key,release_version,owner_kind,owner_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_save CHECK (ordinal BETWEEN 0 AND 5 AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma') AND bonus BETWEEN -5 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_skill (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    ordinal INT NOT NULL,
    skill_key VARBINARY(32) NOT NULL,
    bonus INT NOT NULL,
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index, ordinal),
    CONSTRAINT fk_src_monster_skill FOREIGN KEY (artifact_module_key,release_version,owner_kind,owner_index) REFERENCES rule_schema2_monster_stat (artifact_module_key,release_version,owner_kind,owner_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_skill CHECK (ordinal BETWEEN 0 AND 17 AND skill_key IN (_binary'skill.acrobatics', _binary'skill.animal_handling', _binary'skill.arcana', _binary'skill.athletics', _binary'skill.deception', _binary'skill.history', _binary'skill.insight', _binary'skill.intimidation', _binary'skill.investigation', _binary'skill.medicine', _binary'skill.nature', _binary'skill.perception', _binary'skill.performance', _binary'skill.persuasion', _binary'skill.religion', _binary'skill.sleight_of_hand', _binary'skill.stealth', _binary'skill.survival') AND bonus BETWEEN -5 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_defense (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    ordinal INT NOT NULL,
    kind VARBINARY(16) NOT NULL,
    damage_type VARBINARY(16) NOT NULL,
    qualifier VARBINARY(32) NOT NULL,
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index, ordinal),
    CONSTRAINT fk_src_monster_defense FOREIGN KEY (artifact_module_key,release_version,owner_kind,owner_index) REFERENCES rule_schema2_monster_stat (artifact_module_key,release_version,owner_kind,owner_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_defense CHECK (ordinal BETWEEN 0 AND 38 AND kind IN (_binary'IMMUNITY', _binary'RESISTANCE', _binary'VULNERABILITY') AND damage_type IN (_binary'ACID', _binary'BLUDGEONING', _binary'COLD', _binary'FIRE', _binary'FORCE', _binary'LIGHTNING', _binary'NECROTIC', _binary'PIERCING', _binary'POISON', _binary'PSYCHIC', _binary'RADIANT', _binary'SLASHING', _binary'THUNDER') AND ((qualifier=_binary'ALL') OR (qualifier=_binary'NONMAGICAL_ATTACKS' AND kind=_binary'RESISTANCE' AND damage_type IN (_binary'BLUDGEONING', _binary'PIERCING', _binary'SLASHING'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_condition (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    ordinal INT NOT NULL,
    condition_key VARBINARY(32) NOT NULL,
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index, ordinal),
    CONSTRAINT fk_src_monster_condition FOREIGN KEY (artifact_module_key,release_version,owner_kind,owner_index) REFERENCES rule_schema2_monster_stat (artifact_module_key,release_version,owner_kind,owner_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_condition CHECK (ordinal BETWEEN 0 AND 14 AND condition_key IN (_binary'blinded', _binary'charmed', _binary'deafened', _binary'exhaustion', _binary'frightened', _binary'grappled', _binary'incapacitated', _binary'invisible', _binary'paralyzed', _binary'petrified', _binary'poisoned', _binary'prone', _binary'restrained', _binary'stunned', _binary'unconscious'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_language (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    ordinal INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    understands TINYINT NOT NULL,
    speaks TINYINT NOT NULL,
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index, ordinal),
    CONSTRAINT fk_src_monster_language FOREIGN KEY (artifact_module_key,release_version,owner_kind,owner_index) REFERENCES rule_schema2_monster_stat (artifact_module_key,release_version,owner_kind,owner_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_language CHECK (ordinal BETWEEN 0 AND 15 AND (CHAR_LENGTH(name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(name, '[[:cntrl:]]') AND REGEXP_LIKE(name, '[^[:space:]]')) AND understands=1 AND speaks IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_trait (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
    ordinal INT NOT NULL,
    trait_key VARBINARY(64) NOT NULL,
    trait_scope VARBINARY(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index, ordinal),
    CONSTRAINT fk_src_monster_trait FOREIGN KEY (artifact_module_key,release_version,owner_kind,owner_index) REFERENCES rule_schema2_monster_stat (artifact_module_key,release_version,owner_kind,owner_index) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_trait CHECK (ordinal BETWEEN 0 AND 15 AND REGEXP_LIKE(CONVERT(trait_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(trait_key USING latin1), '[^a-z0-9_.]', 'c') AND trait_scope IN (_binary'PERCEPTION_HEARING_SMELL_ONLY', _binary'ATTACK_ROLL_PACK_TACTICS_ONLY', _binary'UNSUPPORTED') AND (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_monster_action_value (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    owner_kind VARBINARY(16) NOT NULL,
    owner_index INT NOT NULL,
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
    PRIMARY KEY (artifact_module_key, release_version, owner_kind, owner_index),
    CONSTRAINT fk_src_monster_action_value FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact(module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_monster_action_value CHECK (owner_index BETWEEN 0 AND 255 AND ((owner_kind=_binary'ENTRY' AND owner_index<128) OR owner_kind=_binary'CHANGE') AND (CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND source_page BETWEEN 258 AND 403 AND ((mode=_binary'BLOCKED_METADATA' AND mechanism IS NULL AND save_ability_key IS NULL AND save_dc_mode IS NULL AND save_dc IS NULL AND fail_outcome IS NULL AND success_outcome IS NULL AND damage_dice_count IS NULL AND damage_dice_sides IS NULL AND damage_type IS NULL AND damage_delivery IS NULL AND damage_ability_modifier IS NULL AND area_shape IS NULL AND area_length_feet IS NULL AND area_origin_policy IS NULL AND recharge_dice_sides IS NULL AND recharge_minimum_roll IS NULL AND recharge_turn_start IS NULL AND recharge_short_rest IS NULL AND recharge_long_rest IS NULL) OR (mode=_binary'SAVE_DAMAGE_BATCH_V1' AND mechanism IS NOT NULL AND save_ability_key IS NOT NULL AND save_dc_mode IS NOT NULL AND save_dc IS NOT NULL AND fail_outcome IS NOT NULL AND success_outcome IS NOT NULL AND damage_dice_count IS NOT NULL AND damage_dice_sides IS NOT NULL AND damage_type IS NOT NULL AND damage_delivery IS NOT NULL AND damage_ability_modifier IS NOT NULL AND area_shape IS NOT NULL AND area_length_feet IS NOT NULL AND area_origin_policy IS NOT NULL AND recharge_dice_sides IS NOT NULL AND recharge_minimum_roll IS NOT NULL AND recharge_turn_start IS NOT NULL AND recharge_short_rest IS NOT NULL AND recharge_long_rest IS NOT NULL AND mechanism=_binary'HELL_HOUND_FIRE_BREATH_V1' AND save_ability_key=_binary'ability.dexterity' AND save_dc_mode=_binary'FIXED_DC' AND save_dc=12 AND fail_outcome=_binary'FULL' AND success_outcome=_binary'HALF' AND damage_dice_count=6 AND damage_dice_sides=6 AND damage_type=_binary'FIRE' AND damage_delivery=_binary'OTHER_DELIVERY' AND damage_ability_modifier=_binary'NO_ABILITY_MODIFIER' AND area_shape=_binary'CONE' AND area_length_feet=15 AND area_origin_policy=_binary'EXCLUDE_ORIGIN' AND recharge_dice_sides=6 AND recharge_minimum_roll=5 AND recharge_turn_start=1 AND recharge_short_rest=1 AND recharge_long_rest=1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE rule_schema2_proficiency MODIFY category VARBINARY(16) NOT NULL;
ALTER TABLE rule_schema2_proficiency_set MODIFY category VARBINARY(16) NOT NULL;
ALTER TABLE rule_schema2_proficiency DROP CHECK ck_s2_src_proficiency_4;
ALTER TABLE rule_schema2_proficiency ADD CONSTRAINT ck_s2_src_proficiency_4 CHECK (category IN (_binary'SKILL', _binary'SAVING_THROW', _binary'ARMOR', _binary'WEAPON'));
ALTER TABLE rule_schema2_proficiency DROP CHECK ck_s2_src_proficiency_5;
ALTER TABLE rule_schema2_proficiency ADD CONSTRAINT ck_s2_src_proficiency_5 CHECK ((category IN (_binary'SKILL',_binary'SAVING_THROW') AND ability_key IS NOT NULL AND ability_key IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) OR (category NOT IN (_binary'SKILL',_binary'SAVING_THROW') AND ability_key IS NULL));
ALTER TABLE rule_schema2_proficiency_set DROP CHECK ck_s2_src_proficiency_set_3;
ALTER TABLE rule_schema2_proficiency_set ADD CONSTRAINT ck_s2_src_proficiency_set_3 CHECK (category IN (_binary'SKILL', _binary'SAVING_THROW', _binary'ARMOR', _binary'WEAPON', _binary'TOOL'));
ALTER TABLE rule_schema2_advancement_change DROP CHECK ck_s2_src_advancement_change_6;
ALTER TABLE rule_schema2_advancement_change ADD CONSTRAINT ck_s2_src_advancement_change_6 CHECK ((operation = _binary'clear_field' AND field_key = _binary'author_note' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL AND growth_grant_kind IS NULL AND prerequisite_value IS NULL) OR (operation = _binary'set_enabled' AND field_key = _binary'enabled' AND boolean_value IS NOT NULL AND boolean_value IN (0, 1) AND text_value IS NULL AND integer_value IS NULL AND reference_module_key IS NULL AND growth_grant_kind IS NULL AND prerequisite_value IS NULL) OR (operation = _binary'set_field' AND ((text_value IS NOT NULL) + (integer_value IS NOT NULL) + (boolean_value IS NOT NULL) + (reference_module_key IS NOT NULL) + (growth_grant_kind IS NOT NULL) + (prerequisite_value IS NOT NULL)) = 1 AND ((field_key IN (_binary'display_name', _binary'description', _binary'author_note', _binary'proficiency_bonus_profile', _binary'maximum_profile', _binary'recovery_profile') AND text_value IS NOT NULL) OR (field_key = _binary'advancement_scope' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'STATISTICS_AND_RESOURCES_ONLY', _binary'CHARACTER_ADVANCEMENT')) OR (field_key = _binary'execution_mode' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'AUTOMATIC', _binary'DM_ADJUDICATION', _binary'BLOCKED') AND (rule_type <> _binary'character.resource' OR CAST(text_value AS BINARY) <> _binary'DM_ADJUDICATION')) OR (field_key = _binary'execution_algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'AUTOMATIC_RESOURCE_LIFECYCLE_V1', _binary'LIFE_DOMAIN_LEVEL1_V1',_binary'SRD_2014_WIZARD_SPELLCASTING_V1', _binary'BOUNDED_SUBCLASS_SELECTION_V1', _binary'BOUNDED_FEATURE_SELECTION_V1', _binary'BOUNDED_DM_ADJUDICATION_V1', _binary'BLOCKED_ISSUE_8_V1', _binary'BLOCKED_SPELL_SYSTEM_V1', _binary'BLOCKED_DOWNSTREAM_SYSTEM_V1',_binary'SRD_2014_WIZARD_ARCANE_RECOVERY_V1',_binary'SRD_2014_EVOCATION_SAVANT_V1',_binary'SRD_2014_SCULPT_SPELLS_V1')) OR (field_key = _binary'feature_kind' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'FEATURE', _binary'OPTION')) OR (field_key = _binary'category' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'SKILL', _binary'SAVING_THROW', _binary'ARMOR', _binary'WEAPON', _binary'TOOL') AND (rule_type <> _binary'character.proficiency' OR CAST(text_value AS BINARY) <> _binary'TOOL')) OR (field_key = _binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) = _binary'SRD_2014_REST_V1') OR (field_key = _binary'ability_key' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) OR (field_key = _binary'source_page' AND integer_value IS NOT NULL AND ((rule_type = _binary'adventure.rest' AND integer_value = 87) OR (rule_type <> _binary'adventure.rest' AND integer_value BETWEEN 3 AND 74))) OR (field_key = _binary'hit_die_sides' AND integer_value IS NOT NULL AND integer_value IN (6, 8, 10, 12)) OR (field_key IN (_binary'supported_maximum_level', _binary'selection_level', _binary'level') AND integer_value IS NOT NULL AND integer_value BETWEEN 1 AND 20) OR (field_key = _binary'minimum_enabled_members' AND integer_value IS NOT NULL AND integer_value BETWEEN 1 AND 128) OR (field_key IN (_binary'owner_class', _binary'owner', _binary'proficiency') AND reference_module_key IS NOT NULL) OR (field_key = _binary'asi_available' AND boolean_value IS NOT NULL AND boolean_value IN (0, 1)) OR (field_key = _binary'growth_grant' AND growth_grant_kind IS NOT NULL) OR (field_key IN (_binary'multiclass_prerequisite', _binary'prerequisite') AND prerequisite_value IS NOT NULL AND prerequisite_value = 1))));

DELIMITER $$

CREATE TRIGGER rule_s2_monster_insert BEFORE INSERT ON rule_schema2_monster FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_update BEFORE UPDATE ON rule_schema2_monster FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_delete BEFORE DELETE ON rule_schema2_monster FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_action_insert BEFORE INSERT ON rule_schema2_monster_action FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_action_update BEFORE UPDATE ON rule_schema2_monster_action FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_action_delete BEFORE DELETE ON rule_schema2_monster_action FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_change_insert BEFORE INSERT ON rule_schema2_monster_change FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_change_update BEFORE UPDATE ON rule_schema2_monster_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_change_delete BEFORE DELETE ON rule_schema2_monster_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_stat_insert BEFORE INSERT ON rule_schema2_monster_stat FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_stat_update BEFORE UPDATE ON rule_schema2_monster_stat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_stat_delete BEFORE DELETE ON rule_schema2_monster_stat FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_save_insert BEFORE INSERT ON rule_schema2_monster_save FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_save_update BEFORE UPDATE ON rule_schema2_monster_save FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_save_delete BEFORE DELETE ON rule_schema2_monster_save FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_skill_insert BEFORE INSERT ON rule_schema2_monster_skill FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_skill_update BEFORE UPDATE ON rule_schema2_monster_skill FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_skill_delete BEFORE DELETE ON rule_schema2_monster_skill FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_defense_insert BEFORE INSERT ON rule_schema2_monster_defense FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_defense_update BEFORE UPDATE ON rule_schema2_monster_defense FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_defense_delete BEFORE DELETE ON rule_schema2_monster_defense FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_condition_insert BEFORE INSERT ON rule_schema2_monster_condition FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_condition_update BEFORE UPDATE ON rule_schema2_monster_condition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_condition_delete BEFORE DELETE ON rule_schema2_monster_condition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_language_insert BEFORE INSERT ON rule_schema2_monster_language FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_language_update BEFORE UPDATE ON rule_schema2_monster_language FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_language_delete BEFORE DELETE ON rule_schema2_monster_language FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_trait_insert BEFORE INSERT ON rule_schema2_monster_trait FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_trait_update BEFORE UPDATE ON rule_schema2_monster_trait FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_trait_delete BEFORE DELETE ON rule_schema2_monster_trait FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_action_value_insert BEFORE INSERT ON rule_schema2_monster_action_value FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Monster material requires current installation transaction';
    END IF;
END$$

CREATE TRIGGER rule_s2_monster_action_value_update BEFORE UPDATE ON rule_schema2_monster_action_value FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

CREATE TRIGGER rule_s2_monster_action_value_delete BEFORE DELETE ON rule_schema2_monster_action_value FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable monster material';
END$$

DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO rule_schema_meta (schema_version,script_name,script_sha256)
VALUES (11,'V011__schema2-monster-source.sql','fc939ad72e2495b60db8a710bc03c12a8c2c47d3c0e560a394d337c9a05cf6c2');
