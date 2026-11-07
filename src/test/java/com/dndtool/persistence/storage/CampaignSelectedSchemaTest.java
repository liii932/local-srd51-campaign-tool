package com.dndtool.persistence.storage;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CampaignSelectedSchemaTest {
    @Test void currentRuntimePlanCreatesTheFullFutureWriteFootprint() throws Exception {
        var plan = StorageSchemaCatalog.bundled().plan(Set.of("runtime"));
        assertTrue(plan.tableNames().containsAll(Set.of("rules_runtime_material", "characters_runtime_character",
                "scenes_world", "scenes_space", "scenes_clock", "scenes_placement", "combat_scene", "history_runtime_root",
                "resources_character", "equipment_instance", "spellcasting_book", "actions_frame", "effects_instance",
                "monsters_campaign", "monsters_instance", "manual_character", "runtime_profile")));
        assertFalse(plan.tableNames().contains("health_healing"));
        assertFalse(plan.tableNames().contains("characters_character"));
        assertFalse(plan.tableNames().contains("manual_encounter"));

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var sql = connection.createStatement()) {
            sql.execute("PRAGMA foreign_keys=ON");
            sql.execute("BEGIN IMMEDIATE");
            plan.create(connection);
            plan.verify(connection);
            Set<String> actual = new HashSet<>();
            try (var rows = sql.executeQuery("SELECT name FROM sqlite_schema WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (rows.next()) actual.add(rows.getString(1));
            }
            assertEquals(new HashSet<>(plan.tableNames()), actual);
            try (var rows = sql.executeQuery("SELECT profile FROM runtime_profile")) {
                assertTrue(rows.next());
                assertEquals("srd51-campaign-domains-1", rows.getString(1));
                assertFalse(rows.next());
            }
            assertEquals(0, count(sql, "manual_character"));
            assertEquals(0, count(sql, "actions_frame"));
            sql.execute("ROLLBACK");
        }
    }

    private static int count(java.sql.Statement sql, String table) throws Exception {
        try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
