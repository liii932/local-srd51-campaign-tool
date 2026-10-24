package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2ProgressionFixtures;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original author facts travel through the source store and complete accepted-source reader. */
class JdbcSchema2AdvancementSourceRepositoryTest {
    private static final List<String> TABLES = List.of("rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change");

    @Test void typedOriginalFactsReferencesAndOperationsRoundTripWithoutUsingFinalComposedValues() throws Exception {
        var artifacts = artifactsWithEveryValueKind();
        var stored = stored(artifacts);
        var independent = new Schema2SourceReadFixture(artifacts);
        assertEquals(1, stored.rows(TABLES.get(0)).size());
        assertEquals(4, stored.rows(TABLES.get(1)).size());
        assertEquals(14, stored.rows(TABLES.get(2)).size());
        assertEquals(3, stored.rows(TABLES.get(0)).getFirst().get("author_index"));
        assertEquals("1-2:2,3-20:3", row(stored, TABLES.get(1), Schema2ProgressionFixtures.BASE, Schema2ProgressionFixtures.ENERGY, null).get("maximum_profile"));
        assertEquals("1-2:3,3-20:4", row(stored, TABLES.get(2), Schema2ProgressionFixtures.EXT, Schema2ProgressionFixtures.ENERGY, "maximum_profile").get("text_value"));
        for (String table : TABLES) {
            var wanted = independent.rows().get(table); var found = stored.rows(table);
            assertEquals(wanted.size(), found.size(), table);
            for (var row : wanted) {
                var matches = found.stream().filter(candidate -> Arrays.equals((byte[]) row.get("artifact_module_key"), (byte[]) candidate.get("artifact_module_key"))
                        && Arrays.equals((byte[]) row.get("release_version"), (byte[]) candidate.get("release_version"))
                        && row.get("author_index").equals(candidate.get("author_index"))).toList();
                assertEquals(1, matches.size()); var match = matches.getFirst(); assertEquals(row.keySet(), match.keySet());
                for (String column : row.keySet()) {
                    if (row.get(column) instanceof byte[] bytes) assertArrayEquals(bytes, (byte[]) match.get(column), column);
                    else assertEquals(row.get(column), match.get(column), column);
                }
            }
        }
        JdbcSchema2CharacterSourceRepository.verifyAll(stored.connection, artifacts);
        assertEquals(2, independent.load(independent.selections()).artifacts().size());
        assertEquals(0, stored.commits); assertEquals(0, stored.rollbacks); assertEquals(0, stored.closes);
    }

    @Test void everyTypedColumnMissingExtraOrOrphanRowFailsClosed() throws Exception {
        var artifacts = artifactsWithEveryValueKind();
        for (String table : TABLES) {
            var baseline = stored(artifacts);
            for (String column : baseline.rows(table).getFirst().keySet()) {
                var fixture = stored(artifacts); var row = fixture.rows(table).getFirst(); Object prior = row.get(column);
                row.put(column, prior instanceof byte[] bytes ? ascii(new String(bytes, StandardCharsets.US_ASCII) + "_wrong")
                        : prior instanceof Integer value ? value + 1 : prior == null ? "unexpected" : prior + " wrong");
                assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, artifacts), table + "/" + column);
            }
            var missing = stored(artifacts); missing.rows(table).removeFirst();
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(missing.connection, artifacts));
            var extra = stored(artifacts); extra.rows(table).add(new LinkedHashMap<>(extra.rows(table).getFirst()));
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(extra.connection, artifacts));
        }
    }

    @Test void acceptedSourceReadChecksUnselectedResourcesAndCannotTreatTypedOmissionsAsAnEmptyProjection() throws Exception {
        var valid = new Schema2SourceReadFixture(Schema2ProgressionFixtures.artifacts());
        assertEquals(1, valid.load(valid.selections().subList(0, 1)).artifacts().size());
        for (String table : TABLES) {
            var fixture = new Schema2SourceReadFixture(Schema2ProgressionFixtures.artifacts());
            fixture.rows().get(table).removeLast();
            assertThrows(SQLException.class, () -> fixture.load(fixture.selections().subList(0, 1)), table);
            assertEquals(0, fixture.commits); assertEquals(1, fixture.rollbacks);
        }
        var fixture = new Schema2SourceReadFixture(Schema2ProgressionFixtures.artifacts());
        fixture.rows().get("rule_schema2_resource").getLast().put("owner_class_module_key", ascii("wrong_owner"));
        assertThrows(SQLException.class, () -> fixture.load(fixture.selections().subList(0, 1)));
    }

    @Test void everyInsertFailureAndWrongAffectedCountRemainInsideCallerRollback() throws Exception {
        var artifacts = artifactsWithEveryValueKind();
        var good = stored(artifacts);
        for (int write = 1; write <= good.writes; write++) for (int affected : new int[]{-1, 0, 2}) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = write; fixture.affected = affected;
            assertThrows(SQLException.class, () -> {
                for (var artifact : artifacts) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact);
            });
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
            fixture.connection.rollback(); assertTrue(fixture.tables.isEmpty());
        }
    }

    private static List<ProductionRuleArtifact> artifactsWithEveryValueKind() {
        var packages = Schema2ProgressionFixtures.packages();
        var changes = Schema2GrantFixtures.json(packages.get(1).get("changes.json")).getAsJsonArray();
        for (String field : List.of("display_name", "description", "author_note", "proficiency_bonus_profile", "advancement_scope")) {
            String value = switch (field) {
                case "proficiency_bonus_profile" -> "1:2,2-4:2,5-8:3,9-12:4,13-16:5,17-20:6";
                case "advancement_scope" -> "STATISTICS_AND_RESOURCES_ONLY";
                default -> "Original author " + field;
            };
            changes.add(change("set_field", Schema2ProgressionFixtures.PROGRESSION, field, new com.google.gson.JsonPrimitive(value)));
        }
        for (String field : List.of("source_page", "hit_die_sides", "supported_maximum_level"))
            changes.add(change("set_field", Schema2ProgressionFixtures.PROGRESSION, field,
                    new com.google.gson.JsonPrimitive(field.equals("source_page") ? 57 : field.equals("hit_die_sides") ? 12 : 2)));
        changes.add(change("set_field", Schema2ProgressionFixtures.PROGRESSION, "owner_class", Schema2ProgressionFixtures.jsonIdentity(Schema2ProgressionFixtures.CLASS)));
        changes.add(change("clear_field", Schema2ProgressionFixtures.ENERGY, "author_note", null));
        changes.add(change("set_enabled", Schema2ProgressionFixtures.ENERGY, null, new com.google.gson.JsonPrimitive(false)));
        changes.add(change("set_field", Schema2ProgressionFixtures.ENERGY, "execution_mode", new com.google.gson.JsonPrimitive("BLOCKED")));
        packages.get(1).put("changes.json", changes.toString().getBytes(StandardCharsets.UTF_8));
        return Schema2GrantFixtures.artifacts(packages);
    }
    private static JsonObject change(String op, RuleIdentity target,
            String field, com.google.gson.JsonElement value) {
        var row = new JsonObject(); row.addProperty("op", op); row.add("target", Schema2ProgressionFixtures.jsonIdentity(target));
        if (field != null) row.addProperty("field", field); if (value != null) row.add("value", value); return row;
    }
    private static Schema2RuntimeJdbcFixture stored(List<ProductionRuleArtifact> artifacts) throws SQLException {
        var fixture = new Schema2RuntimeJdbcFixture();
        for (var artifact : artifacts) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact);
        return fixture;
    }
    private static Map<String, Object> row(Schema2RuntimeJdbcFixture fixture, String table, String artifact, RuleIdentity target, String field) {
        String targetModule = table.equals("rule_schema2_advancement_change") ? "rule_module_key" : "module_key";
        var matches = fixture.rows(table).stream().filter(row -> Arrays.equals(ascii(artifact), (byte[]) row.get("artifact_module_key"))
                && Arrays.equals(ascii("1"), (byte[]) row.get("release_version"))
                && Arrays.equals(ascii(target.moduleKey()), (byte[]) row.get(targetModule))
                && Arrays.equals(ascii(target.ruleType()), (byte[]) row.get("rule_type"))
                && Arrays.equals(ascii(target.ruleKey()), (byte[]) row.get("rule_key"))
                && (field == null || Arrays.equals(ascii(field), (byte[]) row.get("field_key")))).toList();
        assertEquals(1, matches.size(), table + " author " + artifact + "/1 target " + target + " field " + field);
        return matches.getFirst();
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
}
