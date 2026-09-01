package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CharacterRulesValidationDatabaseBootstrapTest {
    private static final Path SCRIPT = Path.of(
            "database/test/create-character-rules-validation-databases.sql");

    @Test
    void createsOnlyDedicatedDisposableSchemasAndKeepsResponsibilitiesSeparate()
            throws Exception {
        String sql = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS `dnd_tool_se_it`"));
        assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS `dnd_tool_se_migration_it`"));
        assertTrue(sql.contains("'dnd_tool_se_it'@'127.0.0.1'"));
        assertTrue(sql.contains("'dnd_tool_se_migration_it'@'127.0.0.1'"));
        assertTrue(sql.contains("'dnd_tool_se_validation_ro'@'127.0.0.1'"));
        assertTrue(sql.contains("GRANT SELECT ON `dnd_tool_se_migration_it`.*"));
        assertTrue(sql.contains("TRIGGER, CREATE TEMPORARY TABLES"));
        assertTrue(sql.contains("REPLACE_WITH_INTEGRATION_TEST_PASSWORD"));
        assertTrue(sql.contains("REPLACE_WITH_MIGRATION_TEST_PASSWORD"));
        assertTrue(sql.contains("REPLACE_WITH_READ_ONLY_TEST_PASSWORD"));
        assertFalse(sql.contains("ON *.*"));
        assertFalse(sql.contains("CREATE DATABASE `dnd_tool_se`"));
        assertFalse(sql.contains("GRANT ALL"));
        assertFalse(sql.contains("WITH GRANT OPTION"));
        assertFalse(sql.contains("REPLACE_WITH_LOCAL_TEST_PASSWORD"));
    }
}
