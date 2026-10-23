package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Complete typed row readback with independent fixed final-rule and chain bytes. */
class JdbcSchema2GrantSnapshotTest {
    private static final JdbcSchema2RuntimeSnapshotRepository REPOSITORY = new JdbcSchema2RuntimeSnapshotRepository();
    private static final Identity ID = Identity.random();
    private static final ProductionSchema2Projection PROJECTION = ProductionSchema2Projection.compose(Schema2GrantFixtures.fixedArtifacts());

    @Test void completeCharacterSourcesSlotsAndAtomicGrantHistoryMatchFixedGoldenBytes() throws Exception {
        var fixture = stored();
        assertEquals(6, fixture.rows("s2_runtime_character_source").size());
        assertEquals(9, fixture.rows("s2_runtime_proficiency_grant").size());
        assertEquals(15, fixture.rows("s2_runtime_snapshot").getFirst().get("rule_count"));
        assertEquals(18, fixture.rows("s2_runtime_snapshot").getFirst().get("entry_count"));
        assertEquals(135, fixture.rows("s2_runtime_field_chain").size());
        assertEquals(152, fixture.rows("s2_runtime_chain_step").size());
        var north = row(fixture, "s2_runtime_character_source", "rule_key", "subrace.north");
        assertArrayEquals(ascii("grants_base"), (byte[]) north.get("parent_race_module_key"));
        assertArrayEquals(ascii("character.race"), (byte[]) north.get("parent_race_rule_type"));
        assertArrayEquals(ascii("race.human"), (byte[]) north.get("parent_race_rule_key"));
        var choice = row(fixture, "s2_runtime_proficiency_grant", "entry_key", "extra_languages");
        assertEquals(1, choice.get("grant_count"));
        var changingGrant = chainSteps(fixture, "shared_language", "grant");
        assertEquals(List.of("FIXED", "CHOICE", "FIXED"), changingGrant.stream()
                .map(step -> new String((byte[]) step.get("after_grant_kind"), StandardCharsets.US_ASCII)).toList());
        var actual = REPOSITORY.read(fixture.connection, ID).projection();
        for (String file : List.of("configuration", "final-rules", "chains")) {
            byte[] bytes = switch (file) { case "configuration" -> actual.configurationJson(); case "final-rules" -> actual.finalRulesJson(); default -> actual.chainsJson(); };
            assertArrayEquals(Schema2GrantFixtures.resource("expected/" + file + ".json"), bytes);
        }
        assertEquals(1, fixture.commits); assertEquals(0, fixture.rollbacks); assertEquals(0, fixture.closes);
        fixture.readOnly = true; int writes = fixture.writes;
        REPOSITORY.read(fixture.connection, ID); assertEquals(writes, fixture.writes);
    }

    @Test void missingExtraMixedScopeMalformedCompoundAndForgedReferencesFailClosed() throws Exception {
        var mutations = new ArrayList<Consumer<Schema2RuntimeJdbcFixture>>();
        for (String table : List.of("s2_runtime_character_source", "s2_runtime_proficiency_grant")) {
            mutations.add(f -> f.rows(table).removeFirst());
            mutations.add(f -> f.rows(table).add(new LinkedHashMap<>(f.rows(table).getFirst())));
            mutations.add(f -> f.rows(table).getFirst().put("snapshot_id", Identity.random().snapshotBytes()));
        }
        mutations.add(f -> row(f, "s2_runtime_character_source", "rule_key", "subrace.north").put("parent_race_rule_type", ascii("character.class")));
        mutations.add(f -> row(f, "s2_runtime_character_source", "rule_key", "subrace.north").put("parent_race_module_key", null));
        mutations.add(f -> row(f, "s2_runtime_character_source", "rule_key", "race.human").put("parent_race_rule_key", ascii("race.human")));
        mutations.add(f -> f.rows("s2_runtime_character_source").getFirst().put("rule_index", 0));
        mutations.add(f -> f.rows("s2_runtime_proficiency_grant").getFirst().put("entry_index", 0));
        mutations.add(f -> f.rows("s2_runtime_proficiency_grant").getFirst().put("collection_key", ascii("members")));
        mutations.add(f -> f.rows("s2_runtime_proficiency_grant").getFirst().put("grant_category", ascii("SKILL")));
        mutations.add(f -> f.rows("s2_runtime_proficiency_grant").getFirst().put("grant_kind", ascii("BOTH")));
        mutations.add(f -> row(f, "s2_runtime_proficiency_grant", "entry_key", "native_language").put("grant_count", 1));
        mutations.add(f -> row(f, "s2_runtime_proficiency_grant", "entry_key", "extra_languages").put("grant_count", null));
        mutations.add(f -> row(f, "s2_runtime_proficiency_grant", "entry_key", "extra_languages").put("grant_count", "1"));
        mutations.add(f -> row(f, "s2_runtime_proficiency_grant", "entry_key", "extra_languages").put("grant_target_rule_key", ascii("tool_set.initial")));
        mutations.add(f -> row(f, "s2_runtime_proficiency_grant", "entry_key", "native_language").put("grant_target_module_key", ascii("grants_extension")));
        for (String column : List.of("after_text_value", "after_integer_value", "after_boolean_value", "after_reference_module_key", "after_reference_rule_key"))
            mutations.add(f -> chainSteps(f, "shared_language", "grant").getLast().put(column,
                    column.equals("after_text_value") ? "stray" : column.contains("reference") ? ascii("stray") : 1));
        mutations.add(f -> chainSteps(f, "shared_language", "grant").getFirst().put("before_grant_count", 1));
        mutations.add(f -> chainSteps(f, "shared_language", "grant").getLast().put("after_grant_kind", ascii("CHOICE")));
        mutations.add(f -> chainSteps(f, "shared_language", "grant").getLast().put("after_grant_target_module_key", ascii("missing")));
        mutations.add(f -> chainSteps(f, "shared_language", "grant").getLast().put("before_grant_count", 2));
        mutations.add(f -> chainSteps(f, "shared_language", "grant").getFirst().put("author_pointer", "/9/grant/target"));
        mutations.add(f -> row(f, "s2_runtime_field_chain", "entry_key", "shared_language").put("collection_key", null));
        for (var mutation : mutations) {
            var fixture = stored(); mutation.accept(fixture);
            assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, ID));
            assertEquals(1, fixture.commits); assertEquals(0, fixture.rollbacks);
        }
    }

    @Test void typedWriteAndReadbackFailuresRequireCallerRollbackWithoutPartialSnapshot() throws Exception {
        var good = stored(); var inserts = good.sql.stream().filter(sql -> sql.startsWith("INSERT INTO ")).toList();
        List<Integer> failurePoints = new ArrayList<>();
        for (int index = 0; index < inserts.size(); index++)
            if (inserts.get(index).startsWith("INSERT INTO s2_runtime_character_source (")
                    || inserts.get(index).startsWith("INSERT INTO s2_runtime_proficiency_grant (")) failurePoints.add(index + 1);
        for (int point : failurePoints) for (int affected : new int[]{-1, 0, 2}) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = point; fixture.affected = affected;
            try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) {
                assertThrows(SQLException.class, () -> REPOSITORY.store(transaction.connection(), ID, PROJECTION));
                assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
                transaction.rollback();
            }
            assertTrue(fixture.tables.isEmpty());
        }
        var fixture = new Schema2RuntimeJdbcFixture();
        fixture.beforeRead = f -> row(f, "s2_runtime_proficiency_grant", "entry_key", "extra_languages").put("grant_count", 2);
        try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) {
            assertThrows(SQLException.class, () -> REPOSITORY.store(transaction.connection(), ID, PROJECTION));
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
            transaction.rollback();
        }
        assertTrue(fixture.tables.isEmpty());
    }

    private static Schema2RuntimeJdbcFixture stored() throws SQLException {
        var fixture = new Schema2RuntimeJdbcFixture();
        try (var transaction = BusinessWriteTestSupport.internal(fixture.connection)) {
            REPOSITORY.store(transaction.connection(), ID, PROJECTION); transaction.commit();
        }
        return fixture;
    }
    private static Map<String, Object> row(Schema2RuntimeJdbcFixture fixture, String table, String column, String value) {
        return fixture.rows(table).stream().filter(row -> row.get(column) instanceof byte[] bytes
                && java.util.Arrays.equals(ascii(value), bytes)).findFirst().orElseThrow();
    }
    private static List<Map<String, Object>> chainSteps(Schema2RuntimeJdbcFixture fixture, String entry, String field) {
        Object index = fixture.rows("s2_runtime_field_chain").stream().filter(row -> java.util.Arrays.equals(ascii(entry), (byte[]) row.get("entry_key"))
                && java.util.Arrays.equals(ascii(field), (byte[]) row.get("field_key"))).findFirst().orElseThrow().get("chain_index");
        return fixture.rows("s2_runtime_chain_step").stream().filter(row -> row.get("chain_index").equals(index)).toList();
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
}
