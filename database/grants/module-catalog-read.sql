-- Runtime-account read grants for the immutable built-in module catalog.
-- Run this file with a database administrator account that has GRANT OPTION.
-- Do not run it as dnd_tool_se_app, dnd_tool_se_migrator, or dnd_tool_se_agent.
--
-- The application recomputes the released module hash during startup and host
-- diagnostics, so it must read every canonical definition table.  Keep these
-- grants table-specific: the runtime account receives no module INSERT, UPDATE,
-- DELETE, DDL, account-management privilege, or GRANT OPTION.

GRANT SELECT ON `dnd_tool_se`.`module_rule_constant`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_field_definition`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_class_definition`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_proficiency_tier`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_proficiency_bonus_band`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_skill_definition`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_save_definition`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_item_template`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_entity_template`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_entity_template_value`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_entity_template_class_level`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_entity_template_proficiency`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_check_definition`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_roll_mode`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_event_template`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_event_check`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_event_effect`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_effect_definition`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_effect_parameter`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_map_definition`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_map_node`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se`.`module_map_connection`
    TO 'dnd_tool_se_app'@'127.0.0.1';

-- Verify with the administrator account.  The result must retain the existing
-- schema_meta/module_release and runtime-table grants, add the 22 SELECT grants
-- above, and contain no module-definition write privilege or GRANT OPTION.
SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
