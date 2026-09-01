package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps V009 runtime privileges limited to immutable check-record insertion. */
final class CheckRuntimeGrantsTest {
    @Test
    void grantsOnlySelectAndInsertOnTheFourV009RuntimeTables() throws Exception {
        String sql = Files.readString(
                Path.of("database/grants/check-runtime.sql"), StandardCharsets.UTF_8);
        String statements = sql.replaceAll("(?m)^\\s*--.*$", "");

        Matcher grants = Pattern.compile(
                "GRANT\\s+SELECT,\\s*INSERT\\s+ON\\s+`dnd_tool_se`\\.`([^`]+)`",
                Pattern.CASE_INSENSITIVE).matcher(statements);
        int count = 0;
        while (grants.find()) {
            count++;
            assertTrue(java.util.Set.of(
                    "check_execution", "dice_roll", "check_effect",
                    "check_effect_parameter_value").contains(grants.group(1)));
        }
        assertEquals(4, count);
        assertFalse(Pattern.compile(
                "(?im)^\\s*GRANT[^;]*(?:UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|"
                        + "GRANT\\s+OPTION)").matcher(statements).find());
        assertFalse(Pattern.compile("(?im)^\\s*(?:INSERT|UPDATE|DELETE|REVOKE)\\s+")
                .matcher(statements).find());
        assertFalse(statements.contains("`dnd_tool_se`.*"));
    }
}
