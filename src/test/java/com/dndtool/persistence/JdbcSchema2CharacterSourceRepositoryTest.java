package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.ProductionRuleArtifact;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Typed original author material, independently checked against original JSON fixture rows. */
class JdbcSchema2CharacterSourceRepositoryTest {
    private static final List<String> TABLES = List.of("rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change");

    @Test void everyOriginalTypedDeclarationAndChangeIsStoredInTheCallerTransaction() throws Exception {
        var fixture = stored();
        var expected = new Schema2SourceReadFixture(Schema2GrantFixtures.fixedArtifacts());
        assertEquals(6, fixture.rows(TABLES.get(0)).size());
        assertEquals(9, fixture.rows(TABLES.get(1)).size());
        assertEquals(13, fixture.rows(TABLES.get(2)).size());
        assertOriginalRows(expected, fixture);
        JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, Schema2GrantFixtures.fixedArtifacts());
        assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks); assertEquals(0, fixture.closes);
    }

    @Test void originalIndexesSpanAllSameKindFilesInPathOrderBeforeDomainFiltering() throws Exception {
        var files = Schema2GrantFixtures.packages();
        split(files.get(0), "rules.json", 8, "definitions");
        split(files.get(0), "members.json", 6, "entries");
        split(files.get(1), "changes.json", 4, "changes");
        var artifacts = Schema2GrantFixtures.artifacts(files);
        var fixture = stored(artifacts);
        assertOriginalRows(new Schema2SourceReadFixture(artifacts), fixture);
        assertEquals(8, row(fixture, TABLES.get(0), "grants_base", "race.human", null, null).get("author_index"));
        assertEquals(7, row(fixture, TABLES.get(1), "grants_base", "race.human", "extra_languages", null).get("author_index"));
        assertEquals(7, row(fixture, TABLES.get(2), "grants_extension", "class.scholar", "disabled_tool", "enabled").get("author_index"));
        JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, artifacts);
    }

    @Test void missingExtraWrongOwnerAndEveryDualRepresentationMismatchAreRejected() throws Exception {
        List<Fault> faults = new ArrayList<>();
        for (String table : TABLES) {
            faults.add(new Fault(table + " missing row", f -> f.rows(table).removeFirst()));
            faults.add(new Fault(table + " duplicate row", f -> f.rows(table).add(new LinkedHashMap<>(f.rows(table).getFirst()))));
            faults.add(new Fault(table + " wrong package", f -> f.rows(table).getFirst().put("artifact_module_key", ascii("other"))));
            faults.add(new Fault(table + " wrong version", f -> f.rows(table).getFirst().put("release_version", ascii("2"))));
            faults.add(new Fault(table + " wrong author index", f -> f.rows(table).getFirst().put("author_index", 255)));
        }
        faults.add(new Fault("author description replaced by final value", f -> change(f, TABLES.get(0), "grants_base", "race.human", null, null,
                "description", "Human proficiency metadata after extension.")));
        faults.add(new Fault("author subrace parent replaced by intermediate value", f -> change(f, TABLES.get(0), "grants_base", "subrace.north", null, null,
                "parent_race_rule_key", ascii("race.elf"))));
        faults.add(new Fault("author choice count replaced by final count", f -> change(f, TABLES.get(1), "grants_base", "race.human", "extra_languages", null,
                "grant_count", 1))); // Original declaration is 2; composed final is 1.
        faults.add(new Fault("author fixed target replaced by intermediate module", f -> change(f, TABLES.get(1), "grants_base", "race.human", "native_language", null,
                "grant_target_module_key", ascii("grants_extension"))));
        faults.add(new Fault("fixed author grant gains choice count", f -> change(f, TABLES.get(1), "grants_base", "race.human", "native_language", null,
                "grant_count", 1)));
        faults.add(new Fault("set operation replaced by clear", f -> change(f, TABLES.get(2), "grants_extension", "race.human", null, "description",
                "operation", ascii("clear_field"))));
        faults.add(new Fault("intermediate parent change replaced by final parent", f -> change(f, TABLES.get(2), "grants_extension", "subrace.north", null, "parent_race",
                "reference_rule_key", ascii("race.human"))));
        faults.add(new Fault("historical language grant category changed", f -> change(f, TABLES.get(2), "grants_extension", "race.human", "native_language", "grant",
                "grant_category", ascii("TOOL"))));
        for (var fault : faults) {
            var fixture = stored(); fault.mutation().accept(fixture);
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, Schema2GrantFixtures.fixedArtifacts()), fault.name());
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
        }
    }

    @Test void failuresOrWrongAffectedRowsAtEveryTypedInsertLeaveNoRowsAfterCallerRollback() throws Exception {
        var good = stored();
        for (int write = 1; write <= good.writes; write++) for (int affected : new int[]{-1, 0, 2}) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = write; fixture.affected = affected;
            assertThrows(SQLException.class, () -> {
                for (var artifact : Schema2GrantFixtures.fixedArtifacts()) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact);
            });
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
            fixture.connection.rollback(); assertTrue(fixture.tables.isEmpty());
        }
    }

    @Test void sourceReaderValidatesTypedRowsInUnselectedArtifactsAndKeepsOldEmptyProjectionsReadable() throws Exception {
        var fixture = new Schema2SourceReadFixture(Schema2GrantFixtures.fixedArtifacts());
        assertEquals(3, fixture.load(fixture.selections()).artifacts().size());
        fixture = new Schema2SourceReadFixture(Schema2GrantFixtures.fixedArtifacts());
        fixture.rows().get(TABLES.get(2)).removeLast();
        var corrupted = fixture;
        assertThrows(SQLException.class, () -> corrupted.load(corrupted.selections().subList(0, 1)));
        assertEquals(0, corrupted.commits); assertEquals(1, corrupted.rollbacks);
        var old = new Schema2SourceReadFixture();
        assertEquals(3, old.load(old.selections()).artifacts().size());
        for (String table : TABLES) assertTrue(old.rows().get(table).isEmpty());
    }

    @Test void writableExplicitTransactionIsRequiredForTypedInsertsAndReadsDoNotWrite() throws Exception {
        for (boolean readOnly : new boolean[]{false, true}) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.autoCommit = !readOnly; fixture.readOnly = readOnly;
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.store(fixture.connection, Schema2GrantFixtures.fixedArtifacts().getFirst()));
            assertEquals(0, fixture.writes);
        }
        var fixture = stored(); int writes = fixture.writes; fixture.readOnly = true;
        JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, Schema2GrantFixtures.fixedArtifacts());
        assertEquals(writes, fixture.writes);
    }

    private static Schema2RuntimeJdbcFixture stored() throws SQLException {
        return stored(Schema2GrantFixtures.fixedArtifacts());
    }
    private static Schema2RuntimeJdbcFixture stored(List<ProductionRuleArtifact> artifacts) throws SQLException {
        var fixture = new Schema2RuntimeJdbcFixture();
        for (var artifact : artifacts) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact);
        return fixture;
    }
    private static void assertOriginalRows(Schema2SourceReadFixture expected, Schema2RuntimeJdbcFixture actual) {
        for (String table : TABLES) {
            var wanted = expected.rows().get(table); var found = actual.rows(table);
            assertEquals(wanted.size(), found.size(), table);
            for (var row : wanted) {
                var matches = found.stream().filter(candidate -> Arrays.equals((byte[]) row.get("artifact_module_key"), (byte[]) candidate.get("artifact_module_key"))
                        && Arrays.equals((byte[]) row.get("release_version"), (byte[]) candidate.get("release_version"))
                        && row.get("author_index").equals(candidate.get("author_index"))).toList();
                assertEquals(1, matches.size(), table + " original author index " + row.get("author_index"));
                var match = matches.getFirst(); assertEquals(row.keySet(), match.keySet());
                for (String column : row.keySet()) {
                    Object value = row.get(column);
                    if (value instanceof byte[] bytes) assertArrayEquals(bytes, (byte[]) match.get(column), column);
                    else assertEquals(value, match.get(column), column);
                }
            }
        }
    }
    private static Map<String, Object> row(Schema2RuntimeJdbcFixture fixture, String table, String artifact, String rule, String entry, String field) {
        var matches = fixture.rows(table).stream().filter(value -> Arrays.equals(ascii(artifact), (byte[]) value.get("artifact_module_key"))
                && Arrays.equals(ascii(rule), (byte[]) value.get("rule_key"))
                && (entry == null || Arrays.equals(ascii(entry), (byte[]) value.get("entry_key")))
                && (field == null || Arrays.equals(ascii(field), (byte[]) value.get("field_key")))).toList();
        assertEquals(1, matches.size(), table + " " + artifact + "/" + rule + "/" + entry + "/" + field);
        return matches.getFirst();
    }
    private static void change(Schema2RuntimeJdbcFixture fixture, String table, String artifact, String rule, String entry, String field, String column, Object replacement) {
        var target = row(fixture, table, artifact, rule, entry, field);
        Object original = target.get(column);
        if (replacement instanceof byte[] bytes) assertFalse(original instanceof byte[] prior && Arrays.equals(bytes, prior), column + " mutation must change author data");
        else assertNotEquals(original, replacement, column + " mutation must change author data");
        target.put(column, replacement);
    }
    private static void split(Map<String, byte[]> files, String path, int at, String kind) {
        var rows = Schema2GrantFixtures.json(files.remove(path)).getAsJsonArray();
        var first = new JsonArray(); var second = new JsonArray();
        for (int index = 0; index < rows.size(); index++) (index < at ? first : second).add(rows.get(index));
        files.put("a-" + path, first.toString().getBytes(StandardCharsets.UTF_8));
        files.put("z-" + path, second.toString().getBytes(StandardCharsets.UTF_8));
        var header = Schema2GrantFixtures.json(files.get("author-package.json")).getAsJsonObject();
        var declarations = new JsonArray();
        for (var element : header.getAsJsonArray("files")) {
            if (!element.getAsJsonObject().get("path").getAsString().equals(path)) declarations.add(element);
        }
        for (String prefix : List.of("z-", "a-")) {
            var declaration = new JsonObject(); declaration.addProperty("kind", kind); declaration.addProperty("path", prefix + path);
            declarations.add(declaration);
        }
        header.add("files", declarations);
        files.put("author-package.json", header.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private record Fault(String name, Consumer<Schema2RuntimeJdbcFixture> mutation) { }
}
