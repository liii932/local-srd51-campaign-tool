-- Disposable MySQL database bootstrap for MySqlIntegrationIT.
-- Review and run this script in an authenticated SQL client with a separately authorized administrator account.
-- Replace the placeholder password locally before execution. Do not commit the
-- edited copy or put the password in Maven arguments, logs, or chat.
--
-- This suite creates all required tables as TEMPORARY TABLE per test connection;
-- V001-V010 application migrations must not be run in this disposable database.

CREATE DATABASE IF NOT EXISTS `dnd_tool_se_it`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'dnd_tool_se_it'@'127.0.0.1'
    IDENTIFIED BY 'REPLACE_WITH_LOCAL_TEST_PASSWORD';
CREATE USER IF NOT EXISTS 'dnd_tool_se_it'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_LOCAL_TEST_PASSWORD';

GRANT SELECT, INSERT, UPDATE, CREATE TEMPORARY TABLES
    ON `dnd_tool_se_it`.*
    TO 'dnd_tool_se_it'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, CREATE TEMPORARY TABLES
    ON `dnd_tool_se_it`.*
    TO 'dnd_tool_se_it'@'localhost';

FLUSH PRIVILEGES;
