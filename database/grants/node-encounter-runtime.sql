-- Incremental runtime-account grants for V010 node-map and encounter state.
-- Run the whole file only with an administrator account that has GRANT OPTION,
-- after V010 has passed its read-only structure verification.
--
-- Map instances are immutable identities. Positions and active encounter state
-- are mutable; participant and entity rows may be removed from an ACTIVE
-- encounter. No runtime table receives DDL or schema-wide privileges.

GRANT SELECT, INSERT ON `dnd_tool_se`.`map_instance`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`party_world_position`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`battle_state`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON `dnd_tool_se`.`battle_participant`
    TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON `dnd_tool_se`.`entity_position`
    TO 'dnd_tool_se_app'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
