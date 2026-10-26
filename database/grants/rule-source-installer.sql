-- Separate account-administrator checkpoint after schema verification; creates no accounts.
-- Review inherited roles and existing grants separately; these statements never revoke excess access.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema_meta` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_release` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_language` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT, INSERT, DELETE ON `dnd_tool_rules`.`rule_tool` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_package_installation` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_package_installation_partition` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_installation_control` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (module_key, release_version, canonical_format_version, archive_format_version, hash_algorithm),
    UPDATE (canonical_format_version, archive_format_version, hash_algorithm, content_sha256, installation_revision)
    ON `dnd_tool_rules`.`rule_release` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT, DELETE, UPDATE (display_name, description, category, source_page, sort_order)
    ON `dnd_tool_rules`.`rule_language` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (release_id, installation_revision, source_operation_id, operation_fingerprint_version,
    operation_digest_sha256, author_schema_version, installation_manifest_version, installation_manifest_sha256,
    package_display_name, verification_scope, observed_content_sha256)
    ON `dnd_tool_rules`.`rule_package_installation` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT ON `dnd_tool_rules`.`rule_package_installation_partition` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT UPDATE (metadata_row_count, row_version)
    ON `dnd_tool_rules`.`rule_installation_control` TO 'dnd_tool_rules_installer'@'127.0.0.1';
-- Schema 2 installation is independent of explicit acceptance.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_artifact` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_acceptance` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_control` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (module_key, release_version, installation_sequence, source_operation_id,
    operation_fingerprint_version, operation_digest_sha256, author_schema_version, artifact_protocol_version,
    canonical_profile, package_role, raw_zip_sha256, logical_sha256, raw_zip_byte_length, expanded_byte_length, original_zip)
    ON `dnd_tool_rules`.`rule_schema2_artifact` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT UPDATE (metadata_row_count, installation_count, raw_zip_byte_count, row_version)
    ON `dnd_tool_rules`.`rule_schema2_control` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_character_source` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, parent_race_module_key, parent_race_rule_key, author_note) ON `dnd_tool_rules`.`rule_schema2_character_source` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_grant` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, grant_category, grant_kind, grant_target_module_key, grant_target_rule_key, grant_count, author_note) ON `dnd_tool_rules`.`rule_schema2_proficiency_grant` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_character_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, target_kind, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key, grant_category, grant_kind, grant_target_module_key, grant_target_rule_key, grant_count) ON `dnd_tool_rules`.`rule_schema2_character_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_class_progression` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, hit_die_sides, proficiency_bonus_profile, supported_maximum_level, advancement_scope, multiclass_prerequisite_present, author_note) ON `dnd_tool_rules`.`rule_schema2_class_progression` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_resource` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, maximum_profile, recovery_profile, execution_mode, author_note) ON `dnd_tool_rules`.`rule_schema2_resource` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_advancement_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, target_kind, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key, growth_grant_kind, growth_grant_target_module_key, growth_grant_target_rule_type, growth_grant_target_rule_key, growth_grant_count, prerequisite_value) ON `dnd_tool_rules`.`rule_schema2_advancement_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_subclass` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, selection_level, author_note) ON `dnd_tool_rules`.`rule_schema2_subclass` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_feature` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_module_key, owner_rule_type, owner_rule_key, level, feature_kind, execution_mode, execution_algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_feature` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_feat` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, execution_mode, execution_algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_feat` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, category, ability_key, author_note) ON `dnd_tool_rules`.`rule_schema2_proficiency` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_set` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, category, minimum_enabled_members, author_note) ON `dnd_tool_rules`.`rule_schema2_proficiency_set` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_class_level` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, level, asi_available, author_note) ON `dnd_tool_rules`.`rule_schema2_class_level` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_member` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, proficiency_module_key, proficiency_rule_type, proficiency_rule_key, author_note) ON `dnd_tool_rules`.`rule_schema2_proficiency_member` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_growth_grant` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, growth_grant_kind, growth_grant_target_module_key, growth_grant_target_rule_type, growth_grant_target_rule_key, growth_grant_count, author_note) ON `dnd_tool_rules`.`rule_schema2_growth_grant` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_ability_requirement` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, author_kind, group_index, alternative_index, ability_key, minimum_value) ON `dnd_tool_rules`.`rule_schema2_ability_requirement` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_rest` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_rest` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_check` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_check` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_equipment_item` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, kind, armor_class, strength_minimum, stealth_disadvantage, hands, proficiency_module_key, proficiency_rule_type, proficiency_rule_key, stealth_skill_module_key, stealth_skill_rule_type, stealth_skill_rule_key, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_equipment_item` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_execution_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key) ON `dnd_tool_rules`.`rule_schema2_execution_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_health` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_health` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_vital_effect` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, kind, dice_count, dice_sides, modifier, damage_type, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_vital_effect` TO 'dnd_tool_rules_installer'@'127.0.0.1';

-- Typed combat source material; installation does not grant game eligibility.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_combat` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_combat` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_weapon_attack` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, item_module_key, item_rule_type, item_rule_key, ability_policy, dice_count, dice_sides, damage_type, reach_feet, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_weapon_attack` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_movement` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, module_key, rule_type, rule_key, display_name, description, source_page, owner_module_key, owner_rule_type, owner_rule_key, walk_speed_feet, algorithm, author_note) ON `dnd_tool_rules`.`rule_schema2_movement` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_combat_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT INSERT (artifact_module_key, release_version, author_index, rule_module_key, rule_type, rule_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key) ON `dnd_tool_rules`.`rule_schema2_combat_change` TO 'dnd_tool_rules_installer'@'127.0.0.1';
