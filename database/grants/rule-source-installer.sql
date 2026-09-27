-- Separate account-administrator checkpoint after schema verification; creates no accounts.
-- Review inherited roles and existing grants separately; these statements never revoke excess access.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema_meta` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_release` TO 'dnd_tool_rules_installer'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_language` TO 'dnd_tool_rules_installer'@'127.0.0.1';
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
