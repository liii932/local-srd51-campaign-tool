-- Separate account-administrator checkpoint after schema verification; creates no accounts.
-- Review inherited roles and existing grants separately; these statements never revoke excess access.
GRANT SELECT ON `dnd_tool_rules`.`rule_schema_meta` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_release` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_language` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_tool` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_package_installation` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_package_installation_partition` TO 'dnd_tool_rules_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_rules`.`rule_installation_control` TO 'dnd_tool_rules_app'@'127.0.0.1';
