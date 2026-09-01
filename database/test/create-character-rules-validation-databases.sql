-- Disposable MySQL setup for character-rules integration and migration validation.
--
-- Run manually as an authorized local MySQL account administrator. Replace each
-- REPLACE_WITH_* placeholder only in an unsaved editor buffer or a repository-
-- external temporary copy. Never commit, log, or pass a password on a command line.
--
-- This script creates no production database, applies no application migration,
-- grants no global privilege, and writes no business data. The dedicated schemas
-- and accounts must be removed separately after validation.

-- MySqlIntegrationIT: tests create TEMPORARY TABLES and transactional test rows.
CREATE DATABASE IF NOT EXISTS `dnd_tool_se_it`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'dnd_tool_se_it'@'127.0.0.1'
    IDENTIFIED BY 'REPLACE_WITH_INTEGRATION_TEST_PASSWORD';
CREATE USER IF NOT EXISTS 'dnd_tool_se_it'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_INTEGRATION_TEST_PASSWORD';
ALTER USER 'dnd_tool_se_it'@'127.0.0.1'
    IDENTIFIED BY 'REPLACE_WITH_INTEGRATION_TEST_PASSWORD';
ALTER USER 'dnd_tool_se_it'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_INTEGRATION_TEST_PASSWORD';

GRANT SELECT, INSERT, UPDATE, CREATE TEMPORARY TABLES
    ON `dnd_tool_se_it`.*
    TO 'dnd_tool_se_it'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, CREATE TEMPORARY TABLES
    ON `dnd_tool_se_it`.*
    TO 'dnd_tool_se_it'@'localhost';

-- V001-V016 migration-chain validation. Validation uses repository-external
-- temporary copies whose single USE `dnd_tool_se` statement is changed to
-- USE `dnd_tool_se_migration_it`; approved migration files remain untouched.
CREATE DATABASE IF NOT EXISTS `dnd_tool_se_migration_it`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'dnd_tool_se_migration_it'@'127.0.0.1'
    IDENTIFIED BY 'REPLACE_WITH_MIGRATION_TEST_PASSWORD';
CREATE USER IF NOT EXISTS 'dnd_tool_se_migration_it'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_MIGRATION_TEST_PASSWORD';
ALTER USER 'dnd_tool_se_migration_it'@'127.0.0.1'
    IDENTIFIED BY 'REPLACE_WITH_MIGRATION_TEST_PASSWORD';
ALTER USER 'dnd_tool_se_migration_it'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_MIGRATION_TEST_PASSWORD';

GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, ALTER, DROP, INDEX, REFERENCES, TRIGGER, CREATE TEMPORARY TABLES
    ON `dnd_tool_se_migration_it`.*
    TO 'dnd_tool_se_migration_it'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, ALTER, DROP, INDEX, REFERENCES, TRIGGER, CREATE TEMPORARY TABLES
    ON `dnd_tool_se_migration_it`.*
    TO 'dnd_tool_se_migration_it'@'localhost';

-- Read-only verification remains separate from migration authority.
CREATE USER IF NOT EXISTS 'dnd_tool_se_validation_ro'@'127.0.0.1'
    IDENTIFIED BY 'REPLACE_WITH_READ_ONLY_TEST_PASSWORD';
CREATE USER IF NOT EXISTS 'dnd_tool_se_validation_ro'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_READ_ONLY_TEST_PASSWORD';
ALTER USER 'dnd_tool_se_validation_ro'@'127.0.0.1'
    IDENTIFIED BY 'REPLACE_WITH_READ_ONLY_TEST_PASSWORD';
ALTER USER 'dnd_tool_se_validation_ro'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_READ_ONLY_TEST_PASSWORD';

GRANT SELECT ON `dnd_tool_se_migration_it`.*
    TO 'dnd_tool_se_validation_ro'@'127.0.0.1';
GRANT SELECT ON `dnd_tool_se_migration_it`.*
    TO 'dnd_tool_se_validation_ro'@'localhost';

FLUSH PRIVILEGES;
