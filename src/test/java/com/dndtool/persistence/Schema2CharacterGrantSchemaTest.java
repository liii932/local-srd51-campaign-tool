package com.dndtool.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** SQL source and capabilities only; MySQL enforcement remains a separate authorized checkpoint. */
class Schema2CharacterGrantSchemaTest {
    @Test void sourceForwardMigrationPreservesExistingArtifactsAndAddsOnlyOriginalTypedMaterial() throws Exception {
        String sql = source();
        var approval = RuleSchemaMigrations.expectations().get(3);
        assertEquals(4, approval.version());
        assertEquals(approval.scriptSha256(), RuleSchemaMigrations.canonicalPayloadSha256(sql.getBytes(StandardCharsets.UTF_8)));
        assertTrue(sql.contains("VALUES (4, '" + approval.scriptName() + "', '" + approval.scriptSha256() + "')"));
        String preflight = sql.substring(0, sql.indexOf("CREATE TABLE rule_schema2_character_source"));
        assertTrue(preflight.contains("(SELECT COUNT(*) FROM rule_schema_meta) = 3"));
        for (var prior : RuleSchemaMigrations.expectations().subList(0, 3)) {
            assertTrue(preflight.contains("schema_version = " + prior.version() + " AND CAST(script_name AS BINARY) = _binary'" + prior.scriptName() + "'"));
            assertTrue(preflight.contains("_binary'" + prior.scriptSha256() + "'"));
        }
        assertFalse(preflight.contains("FROM rule_schema2_artifact"));
        assertFalse(preflight.contains("FROM rule_schema2_acceptance"));
        assertEquals(Set.of("rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change"),
                Pattern.compile("(?m)^CREATE TABLE (\\w+)").matcher(sql).results().map(m -> m.group(1)).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, count(sql, "FOREIGN KEY"));
        assertEquals(3, count(sql, "REFERENCES rule_schema2_artifact (module_key, release_version)"));
        assertEquals(9, count(sql, "CREATE TRIGGER "));
        for (String table : List.of("rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change")) {
            assertTrue(sql.contains("BEFORE UPDATE ON " + table)); assertTrue(sql.contains("BEFORE DELETE ON " + table));
        }
        for (String forbidden : List.of("ALTER TABLE rule_schema2_artifact", "ALTER TABLE rule_schema2_acceptance", "INSERT INTO rule_schema2_character", "dnd_tool_se.", " JSON", " BLOB", "CASCADE", "IF NOT EXISTS")) assertFalse(sql.contains(forbidden), forbidden);
    }

    @Test void sourceAtomicValuesExcludeIllegalClearsPartialReferencesAndUnboundedText() throws Exception {
        String sql = source();
        assertTrue(sql.contains("operation = _binary'clear_field' AND field_key = _binary'author_note'"));
        assertTrue(sql.contains("CONSTRAINT ck_s2_source_change_reference CHECK ((reference_module_key IS NULL AND reference_rule_type IS NULL AND reference_rule_key IS NULL) OR (reference_module_key IS NOT NULL AND reference_rule_type IS NOT NULL AND reference_rule_key IS NOT NULL AND reference_rule_type = _binary'character.race'))"));
        assertTrue(sql.contains("CHAR_LENGTH(text_value) BETWEEN 1 AND 1000"));
        assertTrue(sql.contains("field_key <> _binary'display_name' OR CHAR_LENGTH(text_value) <= 120"));
        assertTrue(sql.contains("NOT REGEXP_LIKE(text_value, '[[:cntrl:]]')"));
        assertTrue(sql.contains("REGEXP_LIKE(text_value, '[^[:space:]]')"));
        assertTrue(sql.contains("installation_sequence = current_count + 1"));
        for (String maximum : List.of("author_index BETWEEN 0 AND 255", "author_index BETWEEN 0 AND 511", "author_index BETWEEN 0 AND 4095")) assertTrue(sql.contains(maximum));
    }

    @Test void runtimeForwardMigrationBindsParentsSlotsAndAtomicStatesWithoutRewritingPriorSnapshots() throws Exception {
        String sql = runtime();
        assertEquals(SchemaMigrations.V024_APPROVED_SHA256, SchemaMigrations.canonicalPayloadSha256(sql));
        assertTrue(sql.contains("VALUES (24, 'V024__schema2-character-grants.sql', '" + SchemaMigrations.V024_APPROVED_SHA256 + "')"));
        String preflight = sql.substring(0, sql.indexOf("CREATE TABLE s2_runtime_character_source"));
        assertTrue(preflight.contains("(SELECT COUNT(*) FROM schema_meta) = 23"));
        for (var prior : SchemaMigrations.loadExpectations().subList(0, 23)) {
            assertTrue(preflight.contains("schema_version = " + prior.version() + " AND CAST(script_name AS BINARY) = _binary'" + prior.scriptName() + "'"));
            assertTrue(preflight.contains("_binary'" + prior.scriptSha256() + "'"));
        }
        assertFalse(preflight.contains("FROM s2_runtime_snapshot"));
        assertTrue(sql.contains("REFERENCES s2_runtime_character_source (snapshot_id, module_key, rule_type, rule_key)"));
        for (String table : List.of("language", "language_set", "tool", "tool_set"))
            assertTrue(sql.contains("FROM s2_runtime_" + table + " WHERE snapshot_id = NEW.snapshot_id AND module_key = NEW.grant_target_module_key AND rule_key = NEW.grant_target_rule_key"));
        assertTrue(sql.contains("collection_key IS NOT NULL AND collection_key = _binary'members'"));
        assertTrue(sql.contains("collection_key IS NOT NULL AND collection_key = _binary'proficiency_grants'"));
        for (String prefix : List.of("before_", "after_")) {
            for (String column : List.of("grant_category", "grant_kind", "grant_target_module_key", "grant_target_rule_key", "grant_count"))
                assertTrue(sql.contains("ADD COLUMN " + prefix + column));
            assertTrue(sql.contains(prefix + "grant_count BETWEEN 1 AND 16"));
            assertTrue(sql.contains(prefix + "grant_kind = _binary'FIXED' AND " + prefix + "grant_count IS NULL"));
        }
        for (String forbidden : List.of("UPDATE s2_runtime_snapshot", "INSERT INTO s2_runtime_character_source", "UPDATE module_release", "DROP TABLE", "dnd_tool_rules", "CREATE USER", "GRANT ")) assertFalse(sql.contains(forbidden), forbidden);
    }

    @Test void typedMaterialPrivilegesAndReadOnlyAuditRemainSeparateFromAcceptanceAndGameWrites() throws Exception {
        String installer = Files.readString(Path.of("database/grants/rule-source-installer.sql"));
        String accepter = Files.readString(Path.of("database/grants/rule-source-accepter.sql"));
        for (String table : List.of("rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change")) {
            assertTrue(installer.contains("ON `dnd_tool_rules`.`" + table + "`"));
            assertTrue(accepter.contains("GRANT SELECT ON `dnd_tool_rules`.`" + table + "`"));
        }
        assertFalse(accepter.contains("GRANT INSERT ON"));
        assertFalse(accepter.contains("INSERT (artifact_module_key"));
        assertFalse(installer.contains("ON `dnd_tool_rules`.`rule_schema2_acceptance` TO") && installer.contains("INSERT (module_key, release_version, acceptance_sequence"));
        String runtimeGrants = Files.readString(Path.of("database/grants/schema2-runtime-snapshot.sql"));
        for (String table : List.of("s2_runtime_character_source", "s2_runtime_proficiency_grant"))
            assertTrue(runtimeGrants.contains("GRANT SELECT, INSERT ON `dnd_tool_se`.`" + table + "` TO 'dnd_tool_se_app'@'127.0.0.1';"));
        assertFalse(runtimeGrants.contains("dnd_tool_rules"));
        for (String file : List.of("database/verify/character-proficiency-grants.sql", "database/verify/rule-schema2-source-ledger.sql")) {
            String audit = Files.readString(Path.of(file)).replaceAll("(?m)--[^\\r\\n]*", "");
            for (String statement : audit.split(";")) if (!statement.isBlank())
                assertTrue(statement.strip().startsWith("SELECT ") || statement.strip().startsWith("SHOW GRANTS "));
        }
    }

    private static String source() throws Exception { return Files.readString(Path.of("database/rules/migration/V004__schema2-character-grant-source.sql")); }
    private static String runtime() throws Exception { return Files.readString(Path.of("src/main/resources/db/migration/V024__schema2-character-grants.sql")); }
    private static long count(String text, String token) { return Pattern.compile(Pattern.quote(token)).matcher(text).results().count(); }
}
