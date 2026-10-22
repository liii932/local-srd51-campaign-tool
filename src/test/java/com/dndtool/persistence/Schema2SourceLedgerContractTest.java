package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Offline SQL structure/capability checks; actual engine acceptance is a separate authorized checkpoint. */
class Schema2SourceLedgerContractTest {
    private static final Path SOURCE = Path.of("database/rules/migration/V003__schema2-source-ledger.sql");
    @Test
    void forwardMigrationAddsOnlyThreeLocalInnoDbTablesAndAnEmptyPermanentControl() throws Exception {
        String sql = Files.readString(SOURCE);
        var tables = blocks(sql, "CREATE TABLE (\\w+) \\((.*?)\\) ENGINE=InnoDB");
        assertEquals(Set.of("rule_schema2_artifact", "rule_schema2_acceptance", "rule_schema2_control"), tables.keySet());
        assertEquals(1, count(sql, "FOREIGN KEY"));
        assertTrue(sql.contains("FOREIGN KEY (module_key, release_version, installed_operation_id)\n        REFERENCES rule_schema2_artifact (module_key, release_version, source_operation_id)\n        ON UPDATE RESTRICT ON DELETE RESTRICT"));
        assertTrue(sql.contains("VALUES (1, 1, 1, 0, 0, 0, 0)"));
        for (String forbidden : List.of("ALTER TABLE", "AUTO_INCREMENT", "CASCADE", "dnd_tool_se.", "CREATE DATABASE", "CREATE USER", "USE ", "IF NOT EXISTS"))
            assertFalse(stripComments(sql).contains(forbidden), forbidden);
        assertTrue(sql.contains("(SELECT COUNT(*) FROM rule_schema_meta) = 2"));
        assertTrue(sql.contains("schema_version = 1")); assertTrue(sql.contains("schema_version = 2"));
        assertTrue(sql.contains("VALUES (3, 'V003__schema2-source-ledger.sql'"));
        for (String table : List.of("rule_schema2_artifact", "rule_schema2_acceptance")) {
            String body = tables.get(table);
            assertTrue(body.contains("module_key VARBINARY(64)")); assertTrue(body.contains("release_version VARBINARY(64)"));
            assertTrue(body.contains("PRIMARY KEY (module_key, release_version)"));
            assertTrue(body.contains("'^[a-z][a-z0-9_]{0,63}$'"));
            assertTrue(body.contains("canonical_profile = _binary'schema2-production-json-1'"));
        }
        assertTrue(tables.get("rule_schema2_artifact").contains("original_zip MEDIUMBLOB NOT NULL"));
        assertTrue(sql.contains("raw_zip_byte_length BETWEEN 1 AND 2097152"));
        assertTrue(sql.contains("OCTET_LENGTH(original_zip) = raw_zip_byte_length"));
        assertTrue(sql.contains("expanded_byte_length BETWEEN 1 AND 1146880"));
        String control = tables.get("rule_schema2_control");
        assertTrue(control.contains("installation_count BETWEEN 0 AND 16"));
        assertTrue(control.contains("acceptance_count BETWEEN 0 AND installation_count"));
        assertTrue(control.contains("metadata_row_count = 1 + installation_count + acceptance_count"));
        assertTrue(control.contains("row_version = installation_count + acceptance_count"));
        assertTrue(control.contains("raw_zip_byte_count BETWEEN 0 AND 8388608"));
    }

    @Test
    void triggersShareOriginalBarrierGenerateTimeAndPermanentlyProtectInstallationsAndAcceptances() throws Exception {
        String sql = Files.readString(SOURCE);
        var triggers = blocks(sql, "CREATE TRIGGER (\\w+) (.*?)END\\$\\$");
        assertEquals(9, triggers.size());
        for (String name : List.of("rule_s2_artifact_insert", "rule_s2_acceptance_insert", "rule_s2_control_update"))
            assertTrue(triggers.get(name).contains("FROM rule_installation_control WHERE control_id = 1 FOR UPDATE"), name);
        for (String name : List.of("rule_s2_artifact_update", "rule_s2_artifact_delete", "rule_s2_acceptance_update", "rule_s2_acceptance_delete", "rule_s2_control_delete")) {
            assertTrue(triggers.get(name).contains("SIGNAL SQLSTATE '45000'"), name);
            assertFalse(triggers.get(name).contains("IF "), name);
        }
        assertTrue(triggers.get("rule_s2_artifact_insert").contains("SET NEW.installed_at = UTC_TIMESTAMP(6)"));
        assertTrue(triggers.get("rule_s2_artifact_insert").contains("CAST(SHA2(NEW.original_zip, 256) AS BINARY) <> NEW.raw_zip_sha256"));
        assertTrue(triggers.get("rule_s2_artifact_insert").contains("SUM(expanded_byte_length), 0) FROM rule_schema2_artifact) + NEW.expanded_byte_length > 8388608"));
        String acceptance = triggers.get("rule_s2_acceptance_insert");
        assertTrue(acceptance.contains("SET NEW.accepted_at = UTC_TIMESTAMP(6)"));
        for (String binding : List.of("module_key", "release_version", "installation_sequence", "author_schema_version", "artifact_protocol_version", "canonical_profile", "package_role", "raw_zip_sha256", "logical_sha256"))
            assertTrue(acceptance.contains(binding + " = NEW." + binding), binding);
        assertTrue(acceptance.contains("source_operation_id = NEW.installed_operation_id"));
        assertTrue(acceptance.contains("operation_digest_sha256 = NEW.installed_operation_digest_sha256"));
        assertFalse(triggers.get("rule_s2_artifact_insert").contains("INSERT INTO rule_schema2_acceptance"));
        assertFalse(Pattern.compile("(?<!@)@[A-Za-z_]").matcher(stripComments(sql)).find());
    }

    @Test
    void accepterHasExactAcceptanceCapabilityAndOnlyLockCapabilityOnTheSharedLegacyControl() throws Exception {
        String grants = stripComments(Files.readString(Path.of("database/grants/rule-source-accepter.sql")));
        var matches = Pattern.compile("GRANT (.*?) ON `dnd_tool_rules`[.]`(\\w+)` TO 'dnd_tool_rules_accepter'@'127[.]0[.]0[.]1';", Pattern.DOTALL).matcher(grants);
        Set<String> reads = new HashSet<>(); Map<String, String> writes = new HashMap<>();
        while (matches.find()) {
            String privileges = matches.group(1).replaceAll("\\s+", " ").trim();
            if (privileges.equals("SELECT")) reads.add(matches.group(2));
            else assertNull(writes.put(matches.group(2), privileges));
        }
        assertEquals("", matches.replaceAll("").trim());
        assertEquals(Set.of("rule_schema_meta", "rule_installation_control", "rule_schema2_artifact", "rule_schema2_acceptance", "rule_schema2_control", "rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change"), reads);
        assertEquals(Map.of("rule_installation_control", "UPDATE (control_id)",
                "rule_schema2_acceptance", "INSERT (module_key, release_version, acceptance_sequence, acceptance_operation_id, operation_fingerprint_version, operation_digest_sha256, installed_operation_id, installed_operation_digest_sha256, installation_sequence, author_schema_version, artifact_protocol_version, canonical_profile, package_role, raw_zip_sha256, logical_sha256, review_sha256)",
                "rule_schema2_control", "UPDATE (metadata_row_count, acceptance_count, row_version)"), writes);
        assertFalse(grants.contains("GRANT OPTION")); assertFalse(grants.contains(".*"));
    }

    @Test
    void newVerificationScriptIsReadOnlyAndShowsExactBindingsWithoutDumpingRawZip() throws Exception {
        String sql = stripComments(Files.readString(Path.of("database/verify/rule-schema2-source-ledger.sql")));
        for (String statement : sql.split(";")) {
            String normalized = statement.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            assertTrue(normalized.startsWith("SELECT "), normalized);
            for (String forbidden : List.of(" INTO ", " FOR UPDATE", " FOR SHARE", " LOCK ", "GET_LOCK("))
                assertFalse(normalized.contains(forbidden), normalized);
        }
        assertTrue(sql.contains("SHA2(original_zip, 256)"));
        assertTrue(sql.contains("AS exact_binding"));
        assertTrue(sql.contains("WHERE referenced_table_schema = DATABASE()"));
    }
    private static int count(String text, String token) { return text.split(Pattern.quote(token), -1).length - 1; }
    private static String stripComments(String text) { return text.replaceAll("(?m)--[^\\r\\n]*", ""); }
    private static Map<String, String> blocks(String sql, String regex) {
        Map<String, String> result = new LinkedHashMap<>(); var matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(sql);
        while (matcher.find()) assertNull(result.put(matcher.group(1), matcher.group(2)));
        return result;
    }
}

