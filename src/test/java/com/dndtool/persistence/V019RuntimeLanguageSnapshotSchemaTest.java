package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Static source regression checks only; real MySQL constraints/grants need isolated acceptance. */
class V019RuntimeLanguageSnapshotSchemaTest {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V019__runtime-language-snapshot.sql");

    @Test void completeOldHistoryIsPrecheckedBeforePersistentDdlAndLedgerIsLast() throws Exception {
        String sql = Files.readString(MIGRATION);
        String preflight = sql.substring(0, sql.indexOf("CREATE TABLE runtime_run_identity"));
        assertTrue(preflight.contains("CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'"));
        assertTrue(preflight.contains("(SELECT COUNT(*) FROM schema_meta) = 18"));
        assertTrue(preflight.contains("STRICT_TRANS_TABLES"));
        for (var row : SchemaMigrations.loadExpectations().subList(0, 18)) {
            assertTrue(preflight.contains("schema_version = " + row.version() + " AND"));
            assertTrue(preflight.contains("_binary'" + row.scriptName() + "'"));
            assertTrue(preflight.contains("_binary'" + row.scriptSha256() + "'"));
        }
        assertFalse(preflight.contains("USE "));
        String footer = sql.substring(sql.indexOf("-- CHECKSUM-SCOPE-END"));
        assertTrue(footer.contains("INSERT INTO schema_meta"));
        assertTrue(footer.contains("'" + SchemaMigrations.V019_APPROVED_SHA256 + "'"));
        assertEquals(SchemaMigrations.V019_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(footer.stripTrailing().endsWith("ELSE NULL END;"));
        assertFalse(sql.contains("IF NOT EXISTS"));
    }

    @Test void exactlyThreeLocalInnoDbTablesAndOnlyRestrictedLocalForeignKeys() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertEquals(List.of("runtime_run_identity", "runtime_rule_snapshot", "runtime_rule_language"),
                Pattern.compile("(?m)^CREATE TABLE ([a-z_]+)").matcher(sql).results().map(m -> m.group(1)).toList());
        assertEquals(3, count(sql, "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin"));
        assertEquals(2, count(sql, "FOREIGN KEY"));
        assertEquals(2, count(sql, "ON UPDATE RESTRICT ON DELETE RESTRICT"));
        String identity = table(sql, "runtime_run_identity");
        assertFalse(identity.contains("REFERENCES"));
        assertTrue(identity.contains("UNIQUE KEY uq_runtime_identity_snapshot (snapshot_id)"));
        assertTrue(identity.contains("UNIQUE KEY uq_runtime_identity_pair (run_id, snapshot_id)"));
        assertTrue(identity.contains("run_id <> snapshot_id"));
        assertTrue(table(sql, "runtime_rule_snapshot").contains("REFERENCES runtime_run_identity (run_id, snapshot_id)"));
        assertTrue(table(sql, "runtime_rule_snapshot").contains("UNIQUE KEY uq_runtime_snapshot_run (run_id)"));
        assertTrue(table(sql, "runtime_rule_snapshot").contains("UNIQUE KEY uq_runtime_snapshot_pair (run_id, snapshot_id)"));
        assertTrue(table(sql, "runtime_rule_language").contains("PRIMARY KEY (snapshot_id, language_key)"));
        assertTrue(table(sql, "runtime_rule_language").contains("UNIQUE KEY uq_runtime_language_sort (snapshot_id, sort_order)"));
        for (String forbidden : List.of("dnd_tool_rules", "source_release_id", "installation_revision", "CASCADE",
                "REPLACE INTO", "CREATE VIEW", "UPDATE module_", "INSERT INTO campaign", "CREATE DATABASE")) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
    }

    @Test void uuidBitsExactBinaryFieldsAndPartitionScopeCannotSilentlyBroaden() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertEquals(5, count(sql, "VARBINARY(16)"));
        assertEquals(5, count(sql, "& 240) = 64"));
        assertEquals(5, count(sql, "& 192) = 128"));
        assertEquals(5, Pattern.compile("OCTET_LENGTH\\((run_id|snapshot_id)\\) = 16").matcher(sql).results().count());
        String head = table(sql, "runtime_rule_snapshot");
        for (String expected : List.of("module_key VARBINARY(128)", "release_version VARBINARY(64)",
                "hash_algorithm VARBINARY(7)", "release_status VARBINARY(8)", "material_scope VARBINARY(9)",
                "content_sha256 VARBINARY(64) NULL", "canonical_format_version > 0 AND archive_format_version > 0",
                "material_scope = _binary'PARTITION' AND release_status = _binary'DRAFT' AND content_sha256 IS NULL",
                "material_scope = _binary'COMPLETE' AND content_sha256 IS NOT NULL", "[^0-9a-f]", "USING latin1")) {
            assertTrue(head.contains(expected), expected);
        }
        assertFalse(head.contains("UNIQUE KEY uq_runtime_snapshot_content"));
    }

    @Test void languageSixFieldsAndIndependentClosedCategoryMatrixArePreserved() throws Exception {
        String language = table(Files.readString(MIGRATION), "runtime_rule_language");
        for (String field : List.of("language_key VARBINARY(128)", "display_name VARCHAR(120)",
                "description VARCHAR(1000)", "category VARBINARY(8)", "source_page SMALLINT", "sort_order SMALLINT")) {
            assertTrue(language.contains(field + " NOT NULL"));
        }
        List<Set<String>> categories = List.of(
                Set.of("common", "dwarvish", "elvish", "giant", "gnomish", "goblin", "halfling", "orc"),
                Set.of("abyssal", "celestial", "deep_speech", "draconic", "infernal", "primordial", "sylvan", "undercommon"),
                Set.of("druidic", "thieves_cant"));
        var pairs = Pattern.compile("category = _binary'(STANDARD|EXOTIC|SECRET)' AND language_key IN \\(([^)]+)\\)")
                .matcher(language).results().toList();
        assertEquals(List.of("STANDARD", "EXOTIC", "SECRET"), pairs.stream().map(m -> m.group(1)).toList());
        for (int i = 0; i < 3; i++) {
            assertEquals(categories.get(i), Pattern.compile("_binary'language[.]([a-z_]+)'").matcher(pairs.get(i).group(2))
                    .results().map(m -> m.group(1)).collect(java.util.stream.Collectors.toSet()));
        }
        assertTrue(language.contains("source_page BETWEEN 3 AND 74"));
        assertTrue(language.contains("sort_order BETWEEN 1 AND 18"));
        assertTrue(language.contains("CHAR_LENGTH(display_name) BETWEEN 1 AND 120"));
        assertTrue(language.contains("CHAR_LENGTH(description) BETWEEN 1 AND 1000"));
        assertEquals(2, count(language, "[[:cntrl:]]")); assertEquals(2, count(language, "[^[:space:]]"));
    }

    @Test void immutableTriggersUtcAndLeastPrivilegeTemplateAreExplicit() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertEquals(6, count(sql, "CREATE TRIGGER "));
        assertTrue(sql.contains("SET NEW.registered_at = UTC_TIMESTAMP(6)"));
        assertTrue(sql.contains("SET NEW.created_at = UTC_TIMESTAMP(6)"));
        for (String trigger : List.of("runtime_identity_update", "runtime_identity_delete", "runtime_snapshot_update", "runtime_language_update")) {
            int start = sql.indexOf("CREATE TRIGGER " + trigger);
            assertTrue(sql.substring(start, sql.indexOf("END$$", start)).contains("SIGNAL SQLSTATE '45000'"));
        }
        String grants = Files.readString(Path.of("database/grants/runtime-language-snapshot.sql"));
        var statements = grants.lines().filter(line -> line.startsWith("GRANT ")).toList();
        assertEquals(List.of(
                "GRANT SELECT, INSERT ON `dnd_tool_se`.`runtime_run_identity` TO 'dnd_tool_se_app'@'127.0.0.1';",
                "GRANT SELECT, INSERT, DELETE ON `dnd_tool_se`.`runtime_rule_snapshot` TO 'dnd_tool_se_app'@'127.0.0.1';",
                "GRANT SELECT, INSERT, DELETE ON `dnd_tool_se`.`runtime_rule_language` TO 'dnd_tool_se_app'@'127.0.0.1';"), statements);
        assertTrue(grants.contains("DELETE+INSERT"));
        String verify = Files.readString(Path.of("database/verify/v019-runtime-language-snapshot.sql"))
                .replaceAll("(?m)^--.*$", "");
        for (String statement : verify.split(";")) if (!statement.isBlank()) {
            assertTrue(statement.stripLeading().startsWith("SELECT ") || statement.stripLeading().startsWith("SHOW "));
        }
    }

    private static String table(String sql, String name) {
        int start = sql.indexOf("CREATE TABLE " + name);
        return sql.substring(start, sql.indexOf(";", start));
    }
    private static long count(String haystack, String needle) {
        return Pattern.compile(Pattern.quote(needle)).matcher(haystack).results().count();
    }
}
