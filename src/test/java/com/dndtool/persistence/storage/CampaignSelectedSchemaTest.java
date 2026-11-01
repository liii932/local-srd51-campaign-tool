package com.dndtool.persistence.storage;

import com.dndtool.service.storage.CampaignRules;
import com.dndtool.service.storage.StorageException;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CampaignSelectedSchemaTest {
    @Test void selectedCapabilitiesProduceTheirCompleteFutureWriteFootprintAndNoUnselectedDomain() throws Exception {
        var catalog = StorageSchemaCatalog.bundled();
        for (var selected : List.of(List.of(CampaignRules.BASE), List.of(CampaignRules.BASE, CampaignRules.HEALING),
                List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.RUNTIME))) {
            var plan = catalog.plan(CampaignRules.roots(selected));
            assertEquals(selected.contains(CampaignRules.HEALING), plan.tableNames().contains("health_healing"));
            for (String table : List.of("rules_runtime_material", "characters_runtime_character", "scenes_world", "combat_scene",
                    "history_runtime_root", "resources_character", "equipment_instance", "spellcasting_book", "actions_frame",
                    "effects_instance", "monsters_instance", "manual_character", "manual_encounter", "runtime_profile"))
                assertEquals(selected.contains(CampaignRules.RUNTIME), plan.tableNames().contains(table), table);
            try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:"); var sql = connection.createStatement()) {
                sql.execute("PRAGMA foreign_keys=ON"); sql.execute("BEGIN IMMEDIATE"); plan.create(connection); plan.verify(connection);
                Set<String> actual = new HashSet<>();
                try (var rows = sql.executeQuery("SELECT name FROM sqlite_schema WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                    while (rows.next()) actual.add(rows.getString(1));
                }
                assertEquals(new HashSet<>(plan.tableNames()), actual);
                if (selected.contains(CampaignRules.RUNTIME)) {
                    try (var rows = sql.executeQuery("SELECT profile FROM runtime_profile")) {
                        assertTrue(rows.next()); assertEquals("srd51-campaign-domains-1", rows.getString(1)); assertFalse(rows.next());
                    }
                    assertEquals(0, count(sql, "manual_character")); assertEquals(0, count(sql, "actions_frame"));
                }
                sql.execute("ROLLBACK");
            }
        }
    }

    @Test void missingOrDuplicateRuleDependenciesFailBeforeAnyDatabaseIsAllocated() {
        for (var selected : List.of(List.of(CampaignRules.RUNTIME), List.of(CampaignRules.BASE, CampaignRules.RUNTIME),
                List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.HEALING), List.of(CampaignRules.HEALING, CampaignRules.BASE)))
            assertEquals("UNSUPPORTED_RULE_SELECTION", assertThrows(StorageException.class, () -> CampaignRules.roots(selected)).code());
    }
    private static int count(java.sql.Statement sql, String table) throws Exception {
        try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + table)) { assertTrue(rows.next()); return rows.getInt(1); }
    }
}
