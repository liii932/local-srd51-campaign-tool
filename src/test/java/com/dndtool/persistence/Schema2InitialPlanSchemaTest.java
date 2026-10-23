package com.dndtool.persistence;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2InitialPlanSchemaTest {
    @Test void preparationTablesAreTypedImmutableAndBoundToTheSameOperationAndSnapshot() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V026__schema2-initial-plans.sql")) {
            sql = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(SchemaMigrations.V026_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        for (String table : java.util.List.of("s2_initial_plan", "s2_initial_plan_slot", "s2_initial_plan_choice", "s2_initial_plan_grant")) {
            assertTrue(sql.contains("CREATE TABLE " + table + " ("));
            assertTrue(sql.contains("BEFORE UPDATE ON " + table));
            assertTrue(sql.contains("BEFORE DELETE ON " + table));
        }
        assertTrue(sql.contains("FOREIGN KEY (operation_id) REFERENCES s2_runtime_operation"));
        assertTrue(sql.contains("FOREIGN KEY (run_id, snapshot_id) REFERENCES s2_runtime_snapshot"));
        assertTrue(sql.contains("operation_id = NEW.operation_id AND run_id = NEW.run_id AND snapshot_id = NEW.snapshot_id"));
        assertTrue(sql.contains("slot_count BETWEEN 0 AND 512 AND choice_count BETWEEN 0 AND 256 AND grant_count BETWEEN 0 AND 768"));
        assertFalse(sql.contains(" JSON"));
        assertFalse(sql.contains("INSERT INTO campaign"));
        assertFalse(sql.contains("INSERT INTO character"));
    }
}
