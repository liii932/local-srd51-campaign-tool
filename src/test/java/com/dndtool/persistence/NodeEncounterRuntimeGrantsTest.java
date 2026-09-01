package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Keeps V010 runtime privileges table-specific and outside the migration. */
final class NodeEncounterRuntimeGrantsTest {
    private static final Path GRANT_FILE =
            Path.of("database/grants/node-encounter-runtime.sql");

    @Test
    void mapInstanceRemainsInsertOnlyAfterCreation() throws Exception {
        String sql = statements();

        assertTrue(sql.contains(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`map_instance`"));
        assertFalse(Pattern.compile(
                "GRANT[^;]*(?:UPDATE|DELETE)[^;]*`dnd_tool_se`\\.`map_instance`",
                Pattern.CASE_INSENSITIVE).matcher(sql).find());
    }

    @Test
    void mutablePrivilegesStayOnTheFourStateRelationshipTables() throws Exception {
        String sql = statements();

        for (String table : new String[] {"party_world_position", "battle_state"}) {
            assertTrue(sql.contains(
                    "GRANT SELECT, INSERT, UPDATE ON `dnd_tool_se`.`" + table + "`"));
        }
        for (String table : new String[] {"battle_participant", "entity_position"}) {
            assertTrue(sql.contains(
                    "GRANT SELECT, INSERT, UPDATE, DELETE ON `dnd_tool_se`.`" + table + "`"));
        }
        assertFalse(Pattern.compile(
                "(?im)^\\s*GRANT[^;]*(?:CREATE|ALTER|DROP|TRUNCATE|GRANT\\s+OPTION)")
                .matcher(sql).find());
        assertFalse(sql.contains("`dnd_tool_se`.*"));
        assertFalse(Pattern.compile("(?im)^\\s*(?:INSERT|UPDATE|DELETE|REVOKE)\\s+")
                .matcher(sql).find());
    }

    private static String statements() throws Exception {
        return Files.readString(GRANT_FILE, StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*--.*$", "");
    }
}
