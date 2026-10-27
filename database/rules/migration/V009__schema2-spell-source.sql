-- Finite Life domain level-one spell declarations and accurate source/snapshot references.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v009_preflight (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO rules_v009_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY)=_binary'dnd_tool_rules'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES',@@SESSION.sql_mode)>0 OR FIND_IN_SET('STRICT_ALL_TABLES',@@SESSION.sql_mode)>0)
    AND (SELECT COUNT(*) FROM rule_schema_meta)=8
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE (schema_version=1 AND CAST(script_name AS BINARY)=_binary'V001__rule-source-schema.sql' AND CAST(script_sha256 AS BINARY)=_binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') OR (schema_version=2 AND CAST(script_name AS BINARY)=_binary'V002__tool-catalog.sql' AND CAST(script_sha256 AS BINARY)=_binary'2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40') OR (schema_version=3 AND CAST(script_name AS BINARY)=_binary'V003__schema2-source-ledger.sql' AND CAST(script_sha256 AS BINARY)=_binary'4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf') OR (schema_version=4 AND CAST(script_name AS BINARY)=_binary'V004__schema2-character-grant-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'9578db2143bfb14eb077e403d5395bb3089a7ab6e54f6926f42b7dbcac4e9269') OR (schema_version=5 AND CAST(script_name AS BINARY)=_binary'V005__schema2-advancement-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'2dfdecf8cb30fcc71f7e011550ce3b32334340aab5ff546f4e71e53e13147e5c') OR (schema_version=6 AND CAST(script_name AS BINARY)=_binary'V006__schema2-check-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'9c495e9af9ce06b1e98fba9e44ef159cebbe8ef5f058254e54f8f83a0c7aca30') OR (schema_version=7 AND CAST(script_name AS BINARY)=_binary'V007__schema2-equipment-health-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'd5bc25f01389e7811280b97c36202b066142efb6ee5521cbb7fccb0def25de45') OR (schema_version=8 AND CAST(script_name AS BINARY)=_binary'V008__schema2-combat-source.sql' AND CAST(script_sha256 AS BINARY)=_binary'eb0bf9e4675cfed03ec5e5e57f026b57e6bd3409edf98eaf6a9e6e680485cfe2'))=8
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('rule_schema2_spell','rule_schema2_spellcasting','rule_schema2_spell_change'))=0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v009_preflight;

CREATE TABLE rule_schema2_spell (
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
    PRIMARY KEY (artifact_module_key,release_version,author_index),
    UNIQUE KEY uq_src_spell (artifact_module_key,release_version,module_key,rule_type,rule_key),
    CONSTRAINT fk_src_spell FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact (module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_spell_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key=artifact_module_key AND rule_type=_binary'adventure.spell' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^spell[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spell_text CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))),
    CONSTRAINT ck_src_spell_algorithm CHECK ((algorithm=_binary'SRD_2014_BLESS_V1' AND source_page=122) OR (algorithm=_binary'SRD_2014_CURE_WOUNDS_V1' AND source_page=132))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_spellcasting (
    artifact_module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    author_index INT NOT NULL,
    module_key VARBINARY(64) NOT NULL,
    rule_type VARBINARY(32) NOT NULL,
    rule_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    source_page INT NOT NULL,
    owner_class_module_key VARBINARY(64) NOT NULL,
    owner_class_rule_type VARBINARY(32) NOT NULL,
    owner_class_rule_key VARBINARY(128) NOT NULL,
    required_subclass_module_key VARBINARY(64) NOT NULL,
    required_subclass_rule_type VARBINARY(32) NOT NULL,
    required_subclass_rule_key VARBINARY(128) NOT NULL,
    required_feature_module_key VARBINARY(64) NOT NULL,
    required_feature_rule_type VARBINARY(32) NOT NULL,
    required_feature_rule_key VARBINARY(128) NOT NULL,
    slot_resource_module_key VARBINARY(64) NOT NULL,
    slot_resource_rule_type VARBINARY(32) NOT NULL,
    slot_resource_rule_key VARBINARY(128) NOT NULL,
    bless_module_key VARBINARY(64) NOT NULL,
    bless_rule_type VARBINARY(32) NOT NULL,
    bless_rule_key VARBINARY(128) NOT NULL,
    cure_wounds_module_key VARBINARY(64) NOT NULL,
    cure_wounds_rule_type VARBINARY(32) NOT NULL,
    cure_wounds_rule_key VARBINARY(128) NOT NULL,
    material_item_module_key VARBINARY(64) NOT NULL,
    material_item_rule_type VARBINARY(32) NOT NULL,
    material_item_rule_key VARBINARY(128) NOT NULL,
    ability_key VARBINARY(32) NOT NULL,
    algorithm VARBINARY(64) NOT NULL,
    author_note VARCHAR(1000) NULL,
    PRIMARY KEY (artifact_module_key,release_version,author_index),
    UNIQUE KEY uq_src_spellcasting (artifact_module_key,release_version,module_key,rule_type,rule_key),
    CONSTRAINT fk_src_spellcasting FOREIGN KEY (artifact_module_key,release_version) REFERENCES rule_schema2_artifact (module_key,release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_spellcasting_identity CHECK (author_index BETWEEN 0 AND 63 AND module_key=artifact_module_key AND rule_type=_binary'character.spellcasting' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^spellcasting[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spellcasting_text CHECK ((CHAR_LENGTH(display_name) BETWEEN 1 AND 120 AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]') AND REGEXP_LIKE(display_name, '[^[:space:]]')) AND (CHAR_LENGTH(description) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(description, '[[:cntrl:]]') AND REGEXP_LIKE(description, '[^[:space:]]')) AND (author_note IS NULL OR (CHAR_LENGTH(author_note) BETWEEN 1 AND 1000 AND NOT REGEXP_LIKE(author_note, '[[:cntrl:]]') AND REGEXP_LIKE(author_note, '[^[:space:]]')))),
    CONSTRAINT ck_src_spellcasting_algorithm CHECK (algorithm=_binary'LIFE_DOMAIN_LEVEL1_V1' AND source_page BETWEEN 16 AND 17 AND ability_key=_binary'ability.wisdom'),
    CONSTRAINT ck_src_spellcasting_owner_class CHECK (owner_class_rule_type=_binary'character.class' AND REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(owner_class_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spellcasting_required_subclass CHECK (required_subclass_rule_type=_binary'character.subclass' AND REGEXP_LIKE(CONVERT(required_subclass_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(required_subclass_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(required_subclass_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(required_subclass_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spellcasting_required_feature CHECK (required_feature_rule_type=_binary'character.feature' AND REGEXP_LIKE(CONVERT(required_feature_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(required_feature_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(required_feature_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(required_feature_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spellcasting_slot_resource CHECK (slot_resource_rule_type=_binary'character.resource' AND REGEXP_LIKE(CONVERT(slot_resource_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(slot_resource_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(slot_resource_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(slot_resource_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spellcasting_bless CHECK (bless_rule_type=_binary'adventure.spell' AND REGEXP_LIKE(CONVERT(bless_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(bless_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(bless_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(bless_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spellcasting_cure_wounds CHECK (cure_wounds_rule_type=_binary'adventure.spell' AND REGEXP_LIKE(CONVERT(cure_wounds_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(cure_wounds_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(cure_wounds_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(cure_wounds_rule_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_src_spellcasting_material_item CHECK (material_item_rule_type=_binary'equipment.item' AND REGEXP_LIKE(CONVERT(material_item_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(material_item_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(material_item_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(material_item_rule_key USING latin1), '[^a-z0-9_.]', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

ALTER TABLE rule_schema2_equipment_item MODIFY proficiency_module_key VARBINARY(64) NULL;
ALTER TABLE rule_schema2_equipment_item MODIFY proficiency_rule_type VARBINARY(32) NULL;
ALTER TABLE rule_schema2_equipment_item MODIFY proficiency_rule_key VARBINARY(128) NULL;
ALTER TABLE rule_schema2_equipment_item DROP CHECK ck_src_equipment_item_1;
ALTER TABLE rule_schema2_equipment_item ADD CONSTRAINT ck_src_equipment_item_1 CHECK (((kind IN (_binary'LIGHT_ARMOR',_binary'MEDIUM_ARMOR') AND armor_class BETWEEN 10 AND 20 AND strength_minimum=0 AND hands=0) OR (kind=_binary'HEAVY_ARMOR' AND armor_class BETWEEN 10 AND 20 AND strength_minimum IN (0,13,15) AND hands=0) OR (kind=_binary'SHIELD' AND armor_class=2 AND strength_minimum=0 AND stealth_disadvantage=0 AND hands=1) OR (kind IN (_binary'TOOL',_binary'WEAPON') AND armor_class=0 AND strength_minimum=0 AND stealth_disadvantage=0 AND hands IN (1,2))) OR (kind=_binary'HOLY_SYMBOL' AND armor_class=0 AND strength_minimum=0 AND stealth_disadvantage=0 AND hands=1));
ALTER TABLE rule_schema2_equipment_item DROP CHECK ck_src_equipment_item_2;
ALTER TABLE rule_schema2_equipment_item ADD CONSTRAINT ck_src_equipment_item_2 CHECK ((kind=_binary'HOLY_SYMBOL' AND proficiency_module_key IS NULL AND proficiency_rule_type IS NULL AND proficiency_rule_key IS NULL) OR (kind<>_binary'HOLY_SYMBOL' AND ((proficiency_module_key IS NOT NULL AND proficiency_rule_type IS NOT NULL AND proficiency_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(proficiency_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(proficiency_rule_key USING latin1), '^[a-z][a-z0-9_.]{0,127}$', 'c') AND NOT REGEXP_LIKE(CONVERT(proficiency_rule_key USING latin1), '[^a-z0-9_.]', 'c')) AND ((kind=_binary'TOOL' AND proficiency_rule_type=_binary'character.tool') OR (kind<>_binary'TOOL' AND proficiency_rule_type=_binary'character.proficiency')))));

CREATE TABLE rule_schema2_spell_change (
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
    UNIQUE KEY uq_src_spell_change (artifact_module_key, release_version, rule_module_key, rule_type, rule_key, field_key),
    CONSTRAINT fk_src_spell_change FOREIGN KEY (artifact_module_key, release_version) REFERENCES rule_schema2_artifact (module_key, release_version) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_src_spell_change_identity CHECK (author_index BETWEEN 0 AND 255 AND REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_module_key USING latin1), '[^a-z0-9_]', 'c') AND ((rule_type=_binary'adventure.spell' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^spell[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled')) OR (rule_type=_binary'character.spellcasting' AND REGEXP_LIKE(CONVERT(rule_key USING latin1), '^spellcasting[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(rule_key USING latin1), '[^a-z0-9_.]', 'c') AND field_key IN (_binary'display_name',_binary'description',_binary'source_page',_binary'algorithm',_binary'author_note',_binary'enabled',_binary'owner_class',_binary'required_subclass',_binary'required_feature',_binary'slot_resource',_binary'bless',_binary'cure_wounds',_binary'material_item',_binary'ability_key')))),
    CONSTRAINT ck_src_spell_change_ref CHECK ((reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL) OR (reference_module_key IS NOT NULL AND reference_rule_type IS NOT NULL AND reference_rule_key IS NOT NULL AND REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_module_key USING latin1), '[^a-z0-9_]', 'c') AND REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z0-9_]+)*$', 'c') AND NOT REGEXP_LIKE(CONVERT(reference_rule_key USING latin1), '[^a-z0-9_.]', 'c'))),
    CONSTRAINT ck_src_spell_change_value CHECK (
        (operation=_binary'clear_field' AND field_key=_binary'author_note' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL)
        OR (operation=_binary'set_enabled' AND field_key=_binary'enabled' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IN (0,1) AND boolean_value IS NOT NULL AND reference_module_key IS NULL)
        OR (operation=_binary'set_field' AND ((text_value IS NOT NULL)+(integer_value IS NOT NULL)+(boolean_value IS NOT NULL)+(reference_module_key IS NOT NULL))=1 AND (
            (field_key IN (_binary'display_name',_binary'description',_binary'author_note') AND text_value IS NOT NULL AND CHAR_LENGTH(text_value) BETWEEN 1 AND IF(field_key=_binary'display_name',120,1000) AND NOT REGEXP_LIKE(text_value, '[[:cntrl:]]') AND REGEXP_LIKE(text_value, '[^[:space:]]'))
            OR (rule_type=_binary'adventure.spell' AND field_key=_binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'SRD_2014_BLESS_V1',_binary'SRD_2014_CURE_WOUNDS_V1'))
            OR (rule_type=_binary'character.spellcasting' AND field_key=_binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY)=_binary'LIFE_DOMAIN_LEVEL1_V1')
            OR (rule_type=_binary'adventure.spell' AND field_key=_binary'source_page' AND integer_value IS NOT NULL AND integer_value IN (122,132))
            OR (rule_type=_binary'character.spellcasting' AND field_key=_binary'source_page' AND integer_value IS NOT NULL AND integer_value BETWEEN 16 AND 17)
            OR (field_key=_binary'ability_key' AND text_value IS NOT NULL AND CAST(text_value AS BINARY)=_binary'ability.wisdom')
            OR (field_key=_binary'owner_class' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'character.class')
            OR (field_key=_binary'required_subclass' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'character.subclass')
            OR (field_key=_binary'required_feature' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'character.feature')
            OR (field_key=_binary'slot_resource' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'character.resource')
            OR (field_key=_binary'bless' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'adventure.spell')
            OR (field_key=_binary'cure_wounds' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'adventure.spell')
            OR (field_key=_binary'material_item' AND reference_module_key IS NOT NULL AND reference_rule_type=_binary'equipment.item')
        )))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;


ALTER TABLE rule_schema2_advancement_change DROP CHECK ck_s2_src_advancement_change_6;
ALTER TABLE rule_schema2_advancement_change ADD CONSTRAINT ck_s2_src_advancement_change_6 CHECK ((operation = _binary'clear_field' AND field_key = _binary'author_note' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL AND growth_grant_kind IS NULL AND prerequisite_value IS NULL) OR (operation = _binary'set_enabled' AND field_key = _binary'enabled' AND boolean_value IS NOT NULL AND boolean_value IN (0, 1) AND text_value IS NULL AND integer_value IS NULL AND reference_module_key IS NULL AND growth_grant_kind IS NULL AND prerequisite_value IS NULL) OR (operation = _binary'set_field' AND ((text_value IS NOT NULL) + (integer_value IS NOT NULL) + (boolean_value IS NOT NULL) + (reference_module_key IS NOT NULL) + (growth_grant_kind IS NOT NULL) + (prerequisite_value IS NOT NULL)) = 1 AND ((field_key IN (_binary'display_name', _binary'description', _binary'author_note', _binary'proficiency_bonus_profile', _binary'maximum_profile', _binary'recovery_profile') AND text_value IS NOT NULL) OR (field_key = _binary'advancement_scope' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'STATISTICS_AND_RESOURCES_ONLY', _binary'CHARACTER_ADVANCEMENT')) OR (field_key = _binary'execution_mode' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'AUTOMATIC', _binary'DM_ADJUDICATION', _binary'BLOCKED') AND (rule_type <> _binary'character.resource' OR CAST(text_value AS BINARY) <> _binary'DM_ADJUDICATION')) OR (field_key = _binary'execution_algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'AUTOMATIC_RESOURCE_LIFECYCLE_V1', _binary'LIFE_DOMAIN_LEVEL1_V1', _binary'BOUNDED_SUBCLASS_SELECTION_V1', _binary'BOUNDED_FEATURE_SELECTION_V1', _binary'BOUNDED_DM_ADJUDICATION_V1', _binary'BLOCKED_ISSUE_8_V1', _binary'BLOCKED_SPELL_SYSTEM_V1', _binary'BLOCKED_DOWNSTREAM_SYSTEM_V1')) OR (field_key = _binary'feature_kind' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'FEATURE', _binary'OPTION')) OR (field_key = _binary'category' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'SKILL', _binary'ARMOR', _binary'WEAPON', _binary'TOOL') AND (rule_type <> _binary'character.proficiency' OR CAST(text_value AS BINARY) <> _binary'TOOL')) OR (field_key = _binary'algorithm' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) = _binary'SRD_2014_REST_V1') OR (field_key = _binary'ability_key' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'ability.strength', _binary'ability.dexterity', _binary'ability.constitution', _binary'ability.intelligence', _binary'ability.wisdom', _binary'ability.charisma')) OR (field_key = _binary'source_page' AND integer_value IS NOT NULL AND ((rule_type = _binary'adventure.rest' AND integer_value = 87) OR (rule_type <> _binary'adventure.rest' AND integer_value BETWEEN 3 AND 74))) OR (field_key = _binary'hit_die_sides' AND integer_value IS NOT NULL AND integer_value IN (6, 8, 10, 12)) OR (field_key IN (_binary'supported_maximum_level', _binary'selection_level', _binary'level') AND integer_value IS NOT NULL AND integer_value BETWEEN 1 AND 20) OR (field_key = _binary'minimum_enabled_members' AND integer_value IS NOT NULL AND integer_value BETWEEN 1 AND 128) OR (field_key IN (_binary'owner_class', _binary'owner', _binary'proficiency') AND reference_module_key IS NOT NULL) OR (field_key = _binary'asi_available' AND boolean_value IS NOT NULL AND boolean_value IN (0, 1)) OR (field_key = _binary'growth_grant' AND growth_grant_kind IS NOT NULL) OR (field_key IN (_binary'multiclass_prerequisite', _binary'prerequisite') AND prerequisite_value IS NOT NULL AND prerequisite_value = 1))));
ALTER TABLE rule_schema2_execution_change DROP CHECK ck_src_execution_change_atom;
ALTER TABLE rule_schema2_execution_change ADD CONSTRAINT ck_src_execution_change_atom CHECK (
        (operation=_binary'clear_field' AND field_key IN (_binary'author_note',_binary'stealth_skill',_binary'damage_type') AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NULL AND reference_module_key IS NULL)
        OR (operation=_binary'set_enabled' AND field_key=_binary'enabled' AND text_value IS NULL AND integer_value IS NULL AND boolean_value IS NOT NULL AND boolean_value IN (0,1) AND reference_module_key IS NULL)
        OR (operation=_binary'set_field' AND ((text_value IS NOT NULL)+(integer_value IS NOT NULL)+(boolean_value IS NOT NULL)+(reference_module_key IS NOT NULL))=1 AND (
            (field_key IN (_binary'display_name',_binary'description',_binary'author_note') AND text_value IS NOT NULL)
            OR (field_key=_binary'algorithm' AND text_value IS NOT NULL AND ((rule_type=_binary'equipment.item' AND CAST(text_value AS BINARY)=_binary'SRD_2014_EQUIPMENT_V1') OR (rule_type=_binary'adventure.health' AND CAST(text_value AS BINARY)=_binary'SRD_2014_HEALTH_V1') OR (rule_type=_binary'adventure.vital_effect' AND CAST(text_value AS BINARY)=_binary'DIRECT_VITAL_EFFECT_V1')))
            OR (field_key=_binary'source_page' AND integer_value IS NOT NULL AND ((rule_type=_binary'equipment.item' AND integer_value BETWEEN 62 AND 73) OR (rule_type=_binary'adventure.health' AND integer_value=98) OR (rule_type=_binary'adventure.vital_effect' AND integer_value BETWEEN 96 AND 99)))
            OR (field_key=_binary'kind' AND text_value IS NOT NULL AND ((rule_type=_binary'equipment.item' AND CAST(text_value AS BINARY) IN (_binary'LIGHT_ARMOR',_binary'MEDIUM_ARMOR',_binary'HEAVY_ARMOR',_binary'SHIELD',_binary'TOOL',_binary'WEAPON',_binary'HOLY_SYMBOL')) OR (rule_type=_binary'adventure.vital_effect' AND CAST(text_value AS BINARY) IN (_binary'DAMAGE',_binary'HEALING',_binary'TEMP_HP'))))
            OR (field_key=_binary'armor_class' AND integer_value IS NOT NULL AND integer_value BETWEEN 0 AND 20)
            OR (field_key=_binary'strength_minimum' AND integer_value IS NOT NULL AND integer_value IN (0,13,15))
            OR (field_key=_binary'stealth_disadvantage' AND boolean_value IS NOT NULL AND boolean_value IN (0,1))
            OR (field_key=_binary'hands' AND integer_value IS NOT NULL AND integer_value BETWEEN 0 AND 2)
            OR (field_key IN (_binary'proficiency',_binary'stealth_skill') AND reference_module_key IS NOT NULL)
            OR (field_key=_binary'dice_count' AND integer_value IS NOT NULL AND integer_value BETWEEN 0 AND 20)
            OR (field_key=_binary'dice_sides' AND integer_value IS NOT NULL AND integer_value IN (0,4,6,8,10,12))
            OR (field_key=_binary'modifier' AND integer_value IS NOT NULL AND integer_value BETWEEN -1000 AND 1000)
            OR (field_key=_binary'damage_type' AND text_value IS NOT NULL AND CAST(text_value AS BINARY) IN (_binary'ACID', _binary'BLUDGEONING', _binary'COLD', _binary'FIRE', _binary'FORCE', _binary'LIGHTNING', _binary'NECROTIC', _binary'PIERCING', _binary'POISON', _binary'PSYCHIC', _binary'RADIANT', _binary'SLASHING', _binary'THUNDER'))
        )));

DELIMITER $$
CREATE TRIGGER rule_s2_spell_insert BEFORE INSERT ON rule_schema2_spell FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_spell WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Spell material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_spell_update BEFORE UPDATE ON rule_schema2_spell FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 spell material';
END$$
CREATE TRIGGER rule_s2_spell_delete BEFORE DELETE ON rule_schema2_spell FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 spell material';
END$$
CREATE TRIGGER rule_s2_spellcasting_insert BEFORE INSERT ON rule_schema2_spellcasting FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_spellcasting WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=256 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Spell material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_spellcasting_update BEFORE UPDATE ON rule_schema2_spellcasting FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 spell material';
END$$
CREATE TRIGGER rule_s2_spellcasting_delete BEFORE DELETE ON rule_schema2_spellcasting FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 spell material';
END$$
CREATE TRIGGER rule_s2_spell_change_insert BEFORE INSERT ON rule_schema2_spell_change FOR EACH ROW
BEGIN
    DECLARE current_count BIGINT DEFAULT NULL;
    SELECT installation_count INTO current_count FROM rule_schema2_control WHERE control_id=1 FOR UPDATE;
    IF current_count IS NULL OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE module_key=NEW.artifact_module_key AND release_version=NEW.release_version AND installation_sequence=current_count+1)
        OR (SELECT COUNT(*) FROM rule_schema2_spell_change WHERE artifact_module_key=NEW.artifact_module_key AND release_version=NEW.release_version)>=4096 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Spell material requires current installation transaction';
    END IF;
END$$
CREATE TRIGGER rule_s2_spell_change_update BEFORE UPDATE ON rule_schema2_spell_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 spell material';
END$$
CREATE TRIGGER rule_s2_spell_change_delete BEFORE DELETE ON rule_schema2_spell_change FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable schema2 spell material';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

INSERT INTO rule_schema_meta (schema_version,script_name,script_sha256)
VALUES (9,'V009__schema2-spell-source.sql','7ffe0707112cf3051d0a80c346360be5e4f933f8046830321fd3ac4f68196cfc');
