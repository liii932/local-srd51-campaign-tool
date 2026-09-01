-- Narrows the existing campaign UPDATE privilege so campaign_key cannot be changed
-- by the runtime account after creation. Run the whole file with an administrator
-- account that has GRANT OPTION. Existing SELECT and INSERT grants are unaffected.
--
-- Execute REVOKE and GRANT in this order during one maintenance step. Current
-- production code updates only internal_event_tail; later mutable campaign columns
-- must receive explicit column grants when their checklist item is implemented.

REVOKE UPDATE ON `dnd_tool_se`.`campaign`
    FROM 'dnd_tool_se_app'@'127.0.0.1';

GRANT UPDATE (`internal_event_tail`) ON `dnd_tool_se`.`campaign`
    TO 'dnd_tool_se_app'@'127.0.0.1';

SHOW GRANTS FOR 'dnd_tool_se_app'@'127.0.0.1';
