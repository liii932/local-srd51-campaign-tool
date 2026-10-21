-- Apply only with explicit authorization after the V022 object and account audit.
-- DELETE is limited to the existing caller-owned whole-campaign replacement/cleanup path.
-- Grant facts are immutable; this account receives no UPDATE or rule-definition rights here.
GRANT SELECT, INSERT, DELETE ON `dnd_tool_se`.`character_proficiency_grant_v2`
    TO 'dnd_tool_se_app'@'127.0.0.1';
