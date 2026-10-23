-- Apply only with explicit authorization after V026 object and account review.
-- Preparations remain INTERNAL; no campaign, character, event or lifecycle privileges are added.
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_initial_plan` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_initial_plan_slot` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_initial_plan_choice` TO 'dnd_tool_se_app'@'127.0.0.1';
GRANT SELECT, INSERT ON `dnd_tool_se`.`s2_initial_plan_grant` TO 'dnd_tool_se_app'@'127.0.0.1';
