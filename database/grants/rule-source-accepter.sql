-- Separate account-administrator checkpoint; creates no account and changes no passwords.
-- Review inherited roles/existing grants. No installation, release, DDL or legacy content capability.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema_meta` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_installation_control` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
-- A column capability permits SELECT FOR UPDATE on S; the acceptance path never mutates it.
-- Verify this actual column-level locking behavior in separately authorized MySQL acceptance.
GRANT UPDATE (control_id) ON `dnd_tool_rules`.`rule_installation_control` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_artifact` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_acceptance` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_control` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT INSERT (module_key, release_version, acceptance_sequence, acceptance_operation_id,
    operation_fingerprint_version, operation_digest_sha256, installed_operation_id,
    installed_operation_digest_sha256, installation_sequence, author_schema_version, artifact_protocol_version,
    canonical_profile, package_role, raw_zip_sha256, logical_sha256, review_sha256)
    ON `dnd_tool_rules`.`rule_schema2_acceptance` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT UPDATE (metadata_row_count, acceptance_count, row_version)
    ON `dnd_tool_rules`.`rule_schema2_control` TO 'dnd_tool_rules_accepter'@'127.0.0.1';

GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_character_source` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_grant` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_character_change` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_class_progression` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_resource` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_advancement_change` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_subclass` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_feature` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_feat` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_set` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_class_level` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_proficiency_member` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_growth_grant` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_ability_requirement` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_rest` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_check` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_equipment_item` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_execution_change` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_health` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_schema2_vital_effect` TO 'dnd_tool_rules_accepter'@'127.0.0.1';
