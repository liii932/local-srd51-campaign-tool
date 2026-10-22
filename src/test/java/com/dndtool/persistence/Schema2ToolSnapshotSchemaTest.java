package com.dndtool.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ToolSnapshotSchemaTest {
    @Test void forwardMigrationPreservesTypedSnapshotReferencesAndImmutability() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V023__schema2-runtime-tools.sql"));
        assertEquals(SchemaMigrations.V023_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("VALUES (23, 'V023__schema2-runtime-tools.sql', '" + SchemaMigrations.V023_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains(SchemaMigrations.V022_APPROVED_SHA256));
        assertTrue(sql.contains("(SELECT COUNT(*) FROM schema_meta) = 22"));
        for (SchemaMigrations.Expectation migration : SchemaMigrations.loadExpectations()) {
            if (migration.version() >= 23) break;
            assertTrue(sql.contains("schema_version = " + migration.version()
                    + " AND CAST(script_name AS BINARY) = _binary'" + migration.scriptName() + "'"),
                    "Missing exact predecessor " + migration.version());
            assertTrue(sql.contains("_binary'" + migration.scriptSha256() + "'"));
        }
        for (String table : List.of("tool", "tool_set", "tool_entry")) {
            assertTrue(sql.contains("CREATE TABLE s2_runtime_" + table + " ("));
            assertTrue(sql.contains("CREATE TRIGGER s2_" + table + "_update BEFORE UPDATE"));
            assertTrue(sql.contains("CREATE TRIGGER s2_" + table + "_delete BEFORE DELETE"));
        }
        assertTrue(sql.contains("REFERENCES s2_runtime_tool (snapshot_id, module_key, rule_key)"));
        assertTrue(sql.contains("REFERENCES s2_runtime_tool_set (snapshot_id, module_key, rule_key)"));
        assertTrue(sql.contains("REFERENCES s2_runtime_member (snapshot_id, module_key)"));
        assertTrue(sql.contains("category VARBINARY(18)"));
        assertTrue(sql.contains("_binary'MUSICAL_INSTRUMENT'"));
        assertTrue(sql.contains("rule_type = _binary'catalog.tool_set' AND field_key IN (_binary'tool'"));
        assertTrue(sql.contains("rule_type = _binary'catalog.language_set' AND field_key IN (_binary'language'"));
        assertFalse(sql.contains("UPDATE s2_runtime_snapshot"));
        assertFalse(sql.contains("INSERT INTO s2_runtime_tool"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS"));
    }
}
