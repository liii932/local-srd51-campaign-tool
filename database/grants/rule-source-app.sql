-- Separate account-administrator checkpoint after schema verification; creates no accounts.
-- Review inherited roles and existing grants separately; these statements never revoke excess access.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema_meta` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_release` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_language` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_tool` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_package_installation` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_package_installation_partition` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_installation_control` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_artifact` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_acceptance` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_control` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_character_source` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_grant` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_character_change` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_class_progression` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_resource` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_advancement_change` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_subclass` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_feature` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_feat` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_set` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_class_level` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_member` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_growth_grant` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_ability_requirement` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_rest` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_check` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_equipment_item` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_execution_change` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_health` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_vital_effect` TO 'dnd_tool_rules_app'@'127.0.0.1';

-- Typed combat source material; installation does not grant game eligibility.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_combat` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_weapon_attack` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_movement` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_combat_change` TO 'dnd_tool_rules_app'@'127.0.0.1';

-- Typed finite Life domain spell source; all role boundaries remain separate.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_spell` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_spellcasting` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_spell_change` TO 'dnd_tool_rules_app'@'127.0.0.1';

-- Typed Wizard learning catalogs and fixed environmental mechanisms.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_wizard_spellcasting` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_environment` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_spellbook_member` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_wizard_change` TO 'dnd_tool_rules_app'@'127.0.0.1';

-- Typed monster declarations, whole-atom changes and independent profile rows.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_action` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_change` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_stat` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_save` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_skill` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_defense` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_condition` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_language` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_trait` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_monster_action_value` TO 'dnd_tool_rules_app'@'127.0.0.1';
