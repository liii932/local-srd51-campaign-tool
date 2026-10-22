package com.dndtool.persistence;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static migration evidence; never substitutes for disposable MySQL replay. */
class Schema2RuntimeSnapshotSchemaTest {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V021__schema2-runtime-snapshot.sql");
    private static final List<String> TABLES = List.of("identity", "snapshot", "member", "language", "language_set", "language_entry", "field_chain", "chain_step", "control", "operation");
    @Test void migrationPreflightsAllImmutableHistoryAndRecordsOnlyCompletedForwardDdl() throws Exception {
        String sql = Files.readString(MIGRATION); String preflight = sql.substring(0, sql.indexOf("CREATE TABLE s2_runtime_identity"));
        assertTrue(preflight.contains("CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'"));
        assertTrue(preflight.contains("(SELECT COUNT(*) FROM schema_meta) = 20"));
        for (var row : SchemaMigrations.loadExpectations().subList(0, 20)) {
            assertTrue(preflight.contains("schema_version = " + row.version() + " AND CAST(script_name AS BINARY) = _binary'" + row.scriptName() + "'"));
            assertTrue(preflight.contains("_binary'" + row.scriptSha256() + "'"));
        }
        assertEquals(SchemaMigrations.V021_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        String footer = sql.substring(sql.indexOf("-- CHECKSUM-SCOPE-END"));
        assertTrue(footer.contains("VALUES (21, 'V021__schema2-runtime-snapshot.sql', '" + SchemaMigrations.V021_APPROVED_SHA256 + "'"));
        assertTrue(sql.contains("STRICT_TRANS_TABLES")); assertFalse(sql.contains("IF NOT EXISTS"));
    }
    @Test void fullTypedTablesStayInternalAndSeparateFromLegacyPartitionAndSourceSchema() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertEquals(TABLES.stream().map(name -> "s2_runtime_" + name).toList(), Pattern.compile("(?m)^CREATE TABLE ([a-z0-9_]+)").matcher(sql).results().map(match -> match.group(1)).toList());
        assertEquals(10, count(sql, "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin"));
        for (String forbidden : List.of("runtime_rule_snapshot", "dnd_tool_rules", "original_zip", " JSON", " BLOB", "INSERT INTO campaign", "UPDATE module_", "CASCADE")) assertFalse(sql.contains(forbidden), forbidden);
        for (String required : List.of("material_scope = _binary'INTERNAL'", "PRIMARY KEY (run_id)", "UNIQUE KEY uq_s2_identity_snapshot (snapshot_id)",
                "REFERENCES s2_runtime_identity (run_id, snapshot_id)", "description VARCHAR(1000)", "minimum_enabled_members INT", "language_rule_key VARBINARY(128)",
                "before_state VARBINARY(13)", "after_reference_rule_key VARBINARY(128)", "author_pointer VARCHAR(4096)", "operation_kind VARBINARY(7)",
                "operation_sequence BETWEEN 1 AND 16", "row_version = snapshot_count", "snapshot_count BETWEEN 0 AND 16")) assertTrue(sql.contains(required), required);
    }
    @Test void uuidBitsAndImmutableRowsAndMinimalIncrementalGrantsProtectRunBinding() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertEquals(5, count(sql, "& 240) = 64")); assertEquals(5, count(sql, "& 192) = 128"));
        assertEquals(23, count(sql, "CREATE TRIGGER "));
        for (String table : TABLES) if (!table.equals("control")) {
            assertTrue(sql.contains("BEFORE UPDATE ON s2_runtime_" + table)); assertTrue(sql.contains("BEFORE DELETE ON s2_runtime_" + table));
        }
        assertTrue(sql.contains("NEW.row_version <> OLD.row_version + 1")); assertTrue(sql.contains("NEW.snapshot_count <> OLD.snapshot_count + 1"));
        assertTrue(sql.contains("SET NEW.created_at = UTC_TIMESTAMP(6)"));
        String grants = Files.readString(Path.of("database/grants/schema2-runtime-snapshot.sql"));
        for (String table : TABLES) assertTrue(grants.contains((table.equals("control") ? "GRANT SELECT, UPDATE" : "GRANT SELECT, INSERT") + " ON `dnd_tool_se`.`s2_runtime_" + table + "` TO 'dnd_tool_se_app'@'127.0.0.1';"));
        assertFalse(grants.contains("GRANT ALL")); assertFalse(grants.contains("DELETE ON")); assertFalse(grants.contains("dnd_tool_rules"));
    }
    private static long count(String value, String needle) { return Pattern.compile(Pattern.quote(needle)).matcher(value).results().count(); }
}
