-- DnD Tool SE built-in module draft data
--
-- Execute this file exactly once as dnd_tool_se_migrator@127.0.0.1 while
-- Tomcat is stopped and only after V002__stage2_module_schema.sql succeeds.
-- The migration installs the complete reviewed definition set as DRAFT data.
-- It deliberately does not publish the release, create a campaign, grant
-- privileges, or modify any previously installed migration.
--
-- Do not add IF NOT EXISTS, INSERT IGNORE, REPLACE, or an upsert clause. A
-- duplicate identity or missing prerequisite must stop the migration instead
-- of hiding schema drift or partially replacing canonical module content.

USE `dnd_tool_se`;

-- V003 contains only transactional InnoDB DML. Keep the release definitions
-- and their schema_meta identity in one transaction so an interrupted seed is
-- not intentionally committed as a partial canonical module.
START TRANSACTION;

-- CHECKSUM-SCOPE-BEGIN
-- The schema checker hashes this marked payload as UTF-8 after normalizing
-- line endings to LF. The resulting SHA-256 is recorded in schema_meta below.

INSERT INTO `module_release` (
    `module_key`,
    `release_version`,
    `canonical_format_version`,
    `hash_algorithm`,
    `content_sha256`,
    `release_status`,
    `released_at`
) VALUES (
    'dnd5e2014_srd51_se_v1',
    '1',
    1,
    'SHA-256',
    NULL,
    'DRAFT',
    NULL
);

SET @module_release_id = LAST_INSERT_ID();

-- Cross-entity limits and algorithm identifiers that are not represented by
-- a more specific definition table.
INSERT INTO `module_rule_constant` (
    `module_release_id`, `constant_key`, `value_type`, `text_value`,
    `identifier_value`, `integer_value`, `decimal_value`, `boolean_value`
) VALUES
    (@module_release_id, 'stable_key.pattern', 'TEXT',
        '[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*', NULL, NULL, NULL, NULL),
    (@module_release_id, 'character.key.format', 'IDENTIFIER',
        NULL, 'LOWERCASE_UUID_V1', NULL, NULL, NULL),
    (@module_release_id, 'character.name.normalization', 'IDENTIFIER',
        NULL, 'TRIM_THEN_NFC_V1', NULL, NULL, NULL),
    (@module_release_id, 'character.name.minimum_code_points', 'INTEGER',
        NULL, NULL, 1, NULL, NULL),
    (@module_release_id, 'character.name.maximum_code_points', 'INTEGER',
        NULL, NULL, 80, NULL, NULL),
    (@module_release_id, 'character.name.reject_control_characters', 'BOOLEAN',
        NULL, NULL, NULL, NULL, 1),
    (@module_release_id, 'character.total_level.minimum', 'INTEGER',
        NULL, NULL, 0, NULL, NULL),
    (@module_release_id, 'character.total_level.maximum', 'INTEGER',
        NULL, NULL, 20, NULL, NULL),
    (@module_release_id, 'formula.ability_modifier', 'IDENTIFIER',
        NULL, 'FLOOR_DIV_SCORE_MINUS_10_BY_2_V1', NULL, NULL, NULL),
    (@module_release_id, 'formula.total_level', 'IDENTIFIER',
        NULL, 'SUM_CLASS_LEVELS_V1', NULL, NULL, NULL),
    (@module_release_id, 'formula.proficiency_contribution', 'IDENTIFIER',
        NULL, 'NONE_HALF_FLOOR_FULL_DOUBLE_V1', NULL, NULL, NULL),
    (@module_release_id, 'formula.skill_bonus', 'IDENTIFIER',
        NULL, 'ABILITY_MODIFIER_PLUS_PROFICIENCY_V1', NULL, NULL, NULL),
    (@module_release_id, 'formula.saving_throw_bonus', 'IDENTIFIER',
        NULL, 'ABILITY_MODIFIER_PLUS_PROFICIENCY_V1', NULL, NULL, NULL),
    (@module_release_id, 'item.removal_mode', 'IDENTIFIER',
        NULL, 'ARCHIVE_V1', NULL, NULL, NULL),
    (@module_release_id, 'check.manual.modifier.minimum', 'INTEGER',
        NULL, NULL, -99, NULL, NULL),
    (@module_release_id, 'check.manual.modifier.maximum', 'INTEGER',
        NULL, NULL, 99, NULL, NULL),
    (@module_release_id, 'roll.d20.minimum', 'INTEGER',
        NULL, NULL, 1, NULL, NULL),
    (@module_release_id, 'roll.d20.maximum', 'INTEGER',
        NULL, NULL, 20, NULL, NULL),
    (@module_release_id, 'roll.random_source', 'IDENTIFIER',
        NULL, 'SERVER_UNIFORM_V1', NULL, NULL, NULL),
    (@module_release_id, 'check.dc.minimum', 'INTEGER',
        NULL, NULL, 0, NULL, NULL),
    (@module_release_id, 'check.dc.maximum', 'INTEGER',
        NULL, NULL, 60, NULL, NULL),
    (@module_release_id, 'check.success_comparison', 'IDENTIFIER',
        NULL, 'TOTAL_GREATER_THAN_OR_EQUAL_DC_V1', NULL, NULL, NULL),
    (@module_release_id, 'check.natural_1_has_special_result', 'BOOLEAN',
        NULL, NULL, NULL, NULL, 0),
    (@module_release_id, 'check.natural_20_has_special_result', 'BOOLEAN',
        NULL, NULL, NULL, NULL, 0),
    (@module_release_id, 'effect.transaction_mode', 'IDENTIFIER',
        NULL, 'ATOMIC_ALL_OR_NOTHING_V1', NULL, NULL, NULL);

-- PC and NPC share these ten physically typed, editable base fields. Derived
-- modifiers and bonuses are intentionally absent because Java computes them.
INSERT INTO `module_field_definition` (
    `module_release_id`, `field_key`, `display_name`, `data_type`,
    `default_text`, `default_integer`, `default_decimal`, `default_boolean`,
    `minimum_integer`, `maximum_integer`, `minimum_decimal`, `maximum_decimal`,
    `dependent_max_field_key`, `unit`, `description`
) VALUES
    (@module_release_id, 'ability.strength', '力量', 'INTEGER',
        NULL, 10, NULL, NULL, 1, 30, NULL, NULL, NULL, NULL, '基础力量属性'),
    (@module_release_id, 'ability.dexterity', '敏捷', 'INTEGER',
        NULL, 10, NULL, NULL, 1, 30, NULL, NULL, NULL, NULL, '基础敏捷属性'),
    (@module_release_id, 'ability.constitution', '体质', 'INTEGER',
        NULL, 10, NULL, NULL, 1, 30, NULL, NULL, NULL, NULL, '基础体质属性'),
    (@module_release_id, 'ability.intelligence', '智力', 'INTEGER',
        NULL, 10, NULL, NULL, 1, 30, NULL, NULL, NULL, NULL, '基础智力属性'),
    (@module_release_id, 'ability.wisdom', '感知', 'INTEGER',
        NULL, 10, NULL, NULL, 1, 30, NULL, NULL, NULL, NULL, '基础感知属性'),
    (@module_release_id, 'ability.charisma', '魅力', 'INTEGER',
        NULL, 10, NULL, NULL, 1, 30, NULL, NULL, NULL, NULL, '基础魅力属性'),
    (@module_release_id, 'hp.maximum', '最大 HP', 'INTEGER',
        NULL, 1, NULL, NULL, 1, 999, NULL, NULL, NULL, NULL,
        '生命值上限，不由职业或体质自动推导'),
    (@module_release_id, 'hp.current', '当前 HP', 'INTEGER',
        NULL, 1, NULL, NULL, 0, NULL, NULL, NULL, 'hp.maximum', NULL,
        '当前生命值，不能超过最大 HP'),
    (@module_release_id, 'armor_class', 'AC', 'INTEGER',
        NULL, 10, NULL, NULL, 0, 99, NULL, NULL, NULL, NULL,
        '护甲等级，不由装备或属性自动推导'),
    (@module_release_id, 'speed.ground', '地面速度', 'INTEGER',
        NULL, 30, NULL, NULL, 0, 999, NULL, NULL, NULL, '英尺',
        '单一地面速度');

INSERT INTO `module_class_definition` (
    `module_release_id`, `class_key`, `display_name`
) VALUES
    (@module_release_id, 'class.barbarian', '野蛮人'),
    (@module_release_id, 'class.bard', '吟游诗人'),
    (@module_release_id, 'class.cleric', '牧师'),
    (@module_release_id, 'class.druid', '德鲁伊'),
    (@module_release_id, 'class.fighter', '战士'),
    (@module_release_id, 'class.monk', '武僧'),
    (@module_release_id, 'class.paladin', '圣武士'),
    (@module_release_id, 'class.ranger', '游侠'),
    (@module_release_id, 'class.rogue', '游荡者'),
    (@module_release_id, 'class.sorcerer', '术士'),
    (@module_release_id, 'class.warlock', '邪术师'),
    (@module_release_id, 'class.wizard', '法师');

INSERT INTO `module_proficiency_tier` (
    `module_release_id`, `proficiency_key`, `enum_code`, `numerator`,
    `denominator`, `rounding_algorithm`
) VALUES
    (@module_release_id, 'proficiency.none', 'NONE', 0, 1, 'EXACT'),
    (@module_release_id, 'proficiency.half', 'HALF', 1, 2, 'FLOOR'),
    (@module_release_id, 'proficiency.full', 'FULL', 1, 1, 'EXACT'),
    (@module_release_id, 'proficiency.expertise', 'EXPERTISE', 2, 1, 'EXACT');

INSERT INTO `module_proficiency_bonus_band` (
    `module_release_id`, `minimum_total_level`, `maximum_total_level`, `bonus`
) VALUES
    (@module_release_id, 0, 4, 2),
    (@module_release_id, 5, 8, 3),
    (@module_release_id, 9, 12, 4),
    (@module_release_id, 13, 16, 5),
    (@module_release_id, 17, 20, 6);

INSERT INTO `module_skill_definition` (
    `module_release_id`, `skill_key`, `display_name`, `ability_field_key`
) VALUES
    (@module_release_id, 'skill.acrobatics', '体操', 'ability.dexterity'),
    (@module_release_id, 'skill.animal_handling', '驯兽', 'ability.wisdom'),
    (@module_release_id, 'skill.arcana', '奥秘', 'ability.intelligence'),
    (@module_release_id, 'skill.athletics', '运动', 'ability.strength'),
    (@module_release_id, 'skill.deception', '欺瞒', 'ability.charisma'),
    (@module_release_id, 'skill.history', '历史', 'ability.intelligence'),
    (@module_release_id, 'skill.insight', '洞悉', 'ability.wisdom'),
    (@module_release_id, 'skill.intimidation', '威吓', 'ability.charisma'),
    (@module_release_id, 'skill.investigation', '调查', 'ability.intelligence'),
    (@module_release_id, 'skill.medicine', '医药', 'ability.wisdom'),
    (@module_release_id, 'skill.nature', '自然', 'ability.intelligence'),
    (@module_release_id, 'skill.perception', '察觉', 'ability.wisdom'),
    (@module_release_id, 'skill.performance', '表演', 'ability.charisma'),
    (@module_release_id, 'skill.persuasion', '游说', 'ability.charisma'),
    (@module_release_id, 'skill.religion', '宗教', 'ability.intelligence'),
    (@module_release_id, 'skill.sleight_of_hand', '巧手', 'ability.dexterity'),
    (@module_release_id, 'skill.stealth', '隐匿', 'ability.dexterity'),
    (@module_release_id, 'skill.survival', '求生', 'ability.wisdom');

INSERT INTO `module_save_definition` (
    `module_release_id`, `save_key`, `ability_field_key`
) VALUES
    (@module_release_id, 'save.strength', 'ability.strength'),
    (@module_release_id, 'save.dexterity', 'ability.dexterity'),
    (@module_release_id, 'save.constitution', 'ability.constitution'),
    (@module_release_id, 'save.intelligence', 'ability.intelligence'),
    (@module_release_id, 'save.wisdom', 'ability.wisdom'),
    (@module_release_id, 'save.charisma', 'ability.charisma');

INSERT INTO `module_item_template` (
    `module_release_id`, `item_key`, `display_name`, `description`
) VALUES
    (@module_release_id, 'item.backpack', '背包', '用于携带物品的普通背包'),
    (@module_release_id, 'item.rope_hempen_50ft', '50 英尺麻绳', '普通麻绳'),
    (@module_release_id, 'item.torch', '火把', '普通照明用火把');

INSERT INTO `module_entity_template` (
    `module_release_id`, `template_key`, `display_name`
) VALUES
    (@module_release_id, 'npc.commoner', '平民'),
    (@module_release_id, 'npc.guard', '守卫'),
    (@module_release_id, 'npc.wolf', '狼');

-- Each NPC explicitly stores every base field. No missing row acts as an
-- implicit default in the canonical projection.
INSERT INTO `module_entity_template_value` (
    `module_release_id`, `template_key`, `field_key`, `value_type`,
    `text_value`, `integer_value`, `decimal_value`, `boolean_value`
) VALUES
    (@module_release_id, 'npc.commoner', 'ability.strength', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'ability.dexterity', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'ability.constitution', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'ability.intelligence', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'ability.wisdom', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'ability.charisma', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'hp.maximum', 'INTEGER', NULL, 4, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'hp.current', 'INTEGER', NULL, 4, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'armor_class', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.commoner', 'speed.ground', 'INTEGER', NULL, 30, NULL, NULL),
    (@module_release_id, 'npc.guard', 'ability.strength', 'INTEGER', NULL, 13, NULL, NULL),
    (@module_release_id, 'npc.guard', 'ability.dexterity', 'INTEGER', NULL, 12, NULL, NULL),
    (@module_release_id, 'npc.guard', 'ability.constitution', 'INTEGER', NULL, 12, NULL, NULL),
    (@module_release_id, 'npc.guard', 'ability.intelligence', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.guard', 'ability.wisdom', 'INTEGER', NULL, 11, NULL, NULL),
    (@module_release_id, 'npc.guard', 'ability.charisma', 'INTEGER', NULL, 10, NULL, NULL),
    (@module_release_id, 'npc.guard', 'hp.maximum', 'INTEGER', NULL, 11, NULL, NULL),
    (@module_release_id, 'npc.guard', 'hp.current', 'INTEGER', NULL, 11, NULL, NULL),
    (@module_release_id, 'npc.guard', 'armor_class', 'INTEGER', NULL, 16, NULL, NULL),
    (@module_release_id, 'npc.guard', 'speed.ground', 'INTEGER', NULL, 30, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'ability.strength', 'INTEGER', NULL, 12, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'ability.dexterity', 'INTEGER', NULL, 15, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'ability.constitution', 'INTEGER', NULL, 12, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'ability.intelligence', 'INTEGER', NULL, 3, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'ability.wisdom', 'INTEGER', NULL, 12, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'ability.charisma', 'INTEGER', NULL, 6, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'hp.maximum', 'INTEGER', NULL, 11, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'hp.current', 'INTEGER', NULL, 11, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'armor_class', 'INTEGER', NULL, 13, NULL, NULL),
    (@module_release_id, 'npc.wolf', 'speed.ground', 'INTEGER', NULL, 40, NULL, NULL);

-- v1 templates intentionally have no class levels. Every one of their 18
-- skills and six saving throws is nevertheless stored explicitly as NONE.
INSERT INTO `module_entity_template_proficiency` (
    `module_release_id`, `template_key`, `target_kind`, `skill_key`, `save_key`,
    `proficiency_key`
)
SELECT
    template.`module_release_id`, template.`template_key`, 'SKILL',
    skill.`skill_key`, NULL, 'proficiency.none'
FROM `module_entity_template` AS template
JOIN `module_skill_definition` AS skill
    ON skill.`module_release_id` = template.`module_release_id`
WHERE template.`module_release_id` = @module_release_id;

INSERT INTO `module_entity_template_proficiency` (
    `module_release_id`, `template_key`, `target_kind`, `skill_key`, `save_key`,
    `proficiency_key`
)
SELECT
    template.`module_release_id`, template.`template_key`, 'SAVING_THROW',
    NULL, save_definition.`save_key`, 'proficiency.none'
FROM `module_entity_template` AS template
JOIN `module_save_definition` AS save_definition
    ON save_definition.`module_release_id` = template.`module_release_id`
WHERE template.`module_release_id` = @module_release_id;

INSERT INTO `module_check_definition` (
    `module_release_id`, `check_key`, `enum_code`, `modifier_algorithm`
) VALUES
    (@module_release_id, 'check.ability', 'ABILITY', 'ABILITY_MODIFIER_V1'),
    (@module_release_id, 'check.skill', 'SKILL', 'SKILL_BONUS_V1'),
    (@module_release_id, 'check.saving_throw', 'SAVING_THROW', 'SAVING_THROW_BONUS_V1'),
    (@module_release_id, 'check.manual', 'MANUAL', 'MANUAL_MODIFIER_V1');

INSERT INTO `module_roll_mode` (
    `module_release_id`, `roll_mode_key`, `enum_code`, `candidate_count`,
    `selection_algorithm`
) VALUES
    (@module_release_id, 'roll.normal', 'NORMAL', 1, 'ONLY_CANDIDATE_V1'),
    (@module_release_id, 'roll.advantage', 'ADVANTAGE', 2, 'HIGHEST_FIRST_ON_TIE_V1'),
    (@module_release_id, 'roll.disadvantage', 'DISADVANTAGE', 2, 'LOWEST_FIRST_ON_TIE_V1');

INSERT INTO `module_event_template` (
    `module_release_id`, `event_key`, `display_name`
) VALUES
    (@module_release_id, 'event.note', '记录说明'),
    (@module_release_id, 'event.ability_check', '属性检定'),
    (@module_release_id, 'event.skill_check', '技能检定'),
    (@module_release_id, 'event.saving_throw', '豁免检定');

INSERT INTO `module_effect_definition` (
    `module_release_id`, `effect_key`, `execution_algorithm`
) VALUES
    (@module_release_id, 'effect.adjust_current_hp', 'ADJUST_CURRENT_HP_CLAMP_V1'),
    (@module_release_id, 'effect.grant_module_item', 'GRANT_MODULE_ITEM_V1'),
    (@module_release_id, 'effect.grant_temporary_item', 'GRANT_TEMPORARY_ITEM_V1'),
    (@module_release_id, 'effect.set_entity_position', 'SET_ENTITY_NODE_POSITION_V1'),
    (@module_release_id, 'effect.append_event_message', 'APPEND_EVENT_MESSAGE_V1');

INSERT INTO `module_event_check` (
    `module_release_id`, `event_key`, `check_key`
) VALUES
    (@module_release_id, 'event.ability_check', 'check.ability'),
    (@module_release_id, 'event.skill_check', 'check.skill'),
    (@module_release_id, 'event.saving_throw', 'check.saving_throw');

-- Notes can only append a message. Check events may use any of the five
-- closed effects; they never execute arbitrary SQL, JSON, or script content.
INSERT INTO `module_event_effect` (
    `module_release_id`, `event_key`, `effect_key`
) VALUES
    (@module_release_id, 'event.note', 'effect.append_event_message'),
    (@module_release_id, 'event.ability_check', 'effect.adjust_current_hp'),
    (@module_release_id, 'event.ability_check', 'effect.grant_module_item'),
    (@module_release_id, 'event.ability_check', 'effect.grant_temporary_item'),
    (@module_release_id, 'event.ability_check', 'effect.set_entity_position'),
    (@module_release_id, 'event.ability_check', 'effect.append_event_message'),
    (@module_release_id, 'event.skill_check', 'effect.adjust_current_hp'),
    (@module_release_id, 'event.skill_check', 'effect.grant_module_item'),
    (@module_release_id, 'event.skill_check', 'effect.grant_temporary_item'),
    (@module_release_id, 'event.skill_check', 'effect.set_entity_position'),
    (@module_release_id, 'event.skill_check', 'effect.append_event_message'),
    (@module_release_id, 'event.saving_throw', 'effect.adjust_current_hp'),
    (@module_release_id, 'event.saving_throw', 'effect.grant_module_item'),
    (@module_release_id, 'event.saving_throw', 'effect.grant_temporary_item'),
    (@module_release_id, 'event.saving_throw', 'effect.set_entity_position'),
    (@module_release_id, 'event.saving_throw', 'effect.append_event_message');

INSERT INTO `module_effect_parameter` (
    `module_release_id`, `effect_key`, `parameter_key`, `data_type`,
    `reference_kind`, `minimum_integer`, `maximum_integer`, `minimum_decimal`,
    `maximum_decimal`, `text_normalization`, `reject_control_characters`,
    `parameter_order`
) VALUES
    (@module_release_id, 'effect.adjust_current_hp', 'target_character',
        'REFERENCE', 'CHARACTER', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (@module_release_id, 'effect.adjust_current_hp', 'amount',
        'INTEGER', NULL, -999, 999, NULL, NULL, NULL, NULL, 2),
    (@module_release_id, 'effect.grant_module_item', 'target_character',
        'REFERENCE', 'CHARACTER', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (@module_release_id, 'effect.grant_module_item', 'item_template',
        'REFERENCE', 'ITEM_TEMPLATE', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (@module_release_id, 'effect.grant_module_item', 'quantity',
        'INTEGER', NULL, 1, 999, NULL, NULL, NULL, NULL, 3),
    (@module_release_id, 'effect.grant_temporary_item', 'target_character',
        'REFERENCE', 'CHARACTER', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (@module_release_id, 'effect.grant_temporary_item', 'name',
        'TEXT', NULL, 1, 80, NULL, NULL, 'NFC', 1, 2),
    (@module_release_id, 'effect.grant_temporary_item', 'description',
        'TEXT', NULL, 0, 500, NULL, NULL, 'NFC', 1, 3),
    (@module_release_id, 'effect.grant_temporary_item', 'quantity',
        'INTEGER', NULL, 1, 999, NULL, NULL, NULL, NULL, 4),
    (@module_release_id, 'effect.set_entity_position', 'target_character',
        'REFERENCE', 'CHARACTER', NULL, NULL, NULL, NULL, NULL, NULL, 1),
    (@module_release_id, 'effect.set_entity_position', 'map',
        'REFERENCE', 'MAP', NULL, NULL, NULL, NULL, NULL, NULL, 2),
    (@module_release_id, 'effect.set_entity_position', 'node',
        'REFERENCE', 'NODE', NULL, NULL, NULL, NULL, NULL, NULL, 3),
    (@module_release_id, 'effect.append_event_message', 'message',
        'TEXT', NULL, 1, 500, NULL, NULL, 'NFC', 1, 1);

INSERT INTO `module_map_definition` (
    `module_release_id`, `map_key`, `map_type`
) VALUES
    (@module_release_id, 'map.tavern_cellar', 'NODE');

INSERT INTO `module_map_node` (
    `module_release_id`, `map_key`, `node_key`, `display_name`
) VALUES
    (@module_release_id, 'map.tavern_cellar', 'node.street', '街道'),
    (@module_release_id, 'map.tavern_cellar', 'node.entry', '酒馆入口'),
    (@module_release_id, 'map.tavern_cellar', 'node.common_room', '大厅'),
    (@module_release_id, 'map.tavern_cellar', 'node.cellar', '地窖');

-- Endpoints are stored in unsigned UTF-8 byte order, not narrative order.
INSERT INTO `module_map_connection` (
    `module_release_id`, `map_key`, `endpoint_low_key`, `endpoint_high_key`
) VALUES
    (@module_release_id, 'map.tavern_cellar', 'node.entry', 'node.street'),
    (@module_release_id, 'map.tavern_cellar', 'node.common_room', 'node.entry'),
    (@module_release_id, 'map.tavern_cellar', 'node.cellar', 'node.common_room');

-- CHECKSUM-SCOPE-END

INSERT INTO `schema_meta` (
    `schema_version`,
    `script_name`,
    `script_sha256`,
    `description`
) VALUES (
    3,
    'V003__builtin_module_draft.sql',
    '2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225',
    'Complete built-in module definition installed as DRAFT'
);

COMMIT;

-- content_sha256 remains NULL and release_status remains DRAFT by design.
-- A later migration may publish only the independently canonicalized and
-- application-approved definition set, then install immutable protection.
