package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Packaged SQL evidence; enforcement and pool abort remain real-MySQL acceptance checkpoints. */
class BusinessWriteGuardSchemaTest {
    @Test void forwardMigrationChecksExactHistoryAndCreatesOnePermanentImmutableLockRoot() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V025__business-write-guard.sql"));
        assertEquals(SchemaMigrations.V025_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        String preflight = sql.substring(0, sql.indexOf("CREATE TABLE business_write_guard"));
        assertTrue(preflight.contains("(SELECT COUNT(*) FROM schema_meta) = 24"));
        for (var prior : SchemaMigrations.loadExpectations().subList(0, 24)) {
            assertTrue(preflight.contains("schema_version = " + prior.version() + " AND CAST(script_name AS BINARY) = _binary'" + prior.scriptName() + "'"));
            assertTrue(preflight.contains("_binary'" + prior.scriptSha256() + "'"));
        }
        for (String required : List.of("STRICT_TRANS_TABLES", "PRIMARY KEY (guard_id)", "guard_id = 1 AND protocol_version = 1",
                "INSERT INTO business_write_guard (guard_id, protocol_version) VALUES (1, 1)",
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin",
                "BEFORE INSERT ON business_write_guard", "BEFORE UPDATE ON business_write_guard", "BEFORE DELETE ON business_write_guard"))
            assertTrue(sql.contains(required), required);
        for (String forbidden : List.of("IF NOT EXISTS", "ALTER TABLE", "DROP TABLE", "INSERT INTO campaign", "UPDATE module_release", "READY", "UNCERTAIN"))
            assertFalse(sql.contains(forbidden), forbidden);
        assertTrue(sql.contains("VALUES (25, 'V025__business-write-guard.sql', '" + SchemaMigrations.V025_APPROVED_SHA256 + "')"));
    }

    @Test void appCanOnlyReadGuardAndAuditCannotWrite() throws Exception {
        String grants = Files.readString(Path.of("database/grants/business-write-guard.sql"));
        assertTrue(grants.contains("GRANT SELECT ON `dnd_tool_se`.`business_write_guard` TO 'dnd_tool_se_app'@'127.0.0.1';"));
        for (String table : List.of("campaign", "s2_runtime_identity", "runtime_run_identity"))
            assertTrue(grants.contains("GRANT SELECT ON `dnd_tool_se`.`" + table + "` TO 'dnd_tool_se_app'@'127.0.0.1';"));
        for (String forbidden : List.of("GRANT ALL", "GRANT INSERT", "GRANT UPDATE", "GRANT DELETE", "dnd_tool_rules")) assertFalse(grants.contains(forbidden));
        String audit = Files.readString(Path.of("database/verify/business-write-guard.sql")).replaceAll("(?m)--[^\\r\\n]*", "");
        for (String statement : audit.split(";")) if (!statement.isBlank())
            assertTrue(statement.strip().startsWith("SELECT ") || statement.strip().startsWith("SHOW GRANTS "));
    }
}
