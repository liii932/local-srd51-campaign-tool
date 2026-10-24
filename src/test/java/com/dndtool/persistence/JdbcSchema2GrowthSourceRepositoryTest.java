package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.Schema2ProgressionFixtures;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Typed source facts, including original nested predicates and complete entry targets. */
class JdbcSchema2GrowthSourceRepositoryTest {
    private static final List<String> TABLES = List.of("rule_schema2_class_progression", "rule_schema2_resource", "rule_schema2_advancement_change",
            "rule_schema2_subclass", "rule_schema2_feature", "rule_schema2_feat", "rule_schema2_proficiency", "rule_schema2_proficiency_set",
            "rule_schema2_class_level", "rule_schema2_proficiency_member", "rule_schema2_growth_grant", "rule_schema2_ability_requirement", "rule_schema2_rest");

    @Test void everyTypedDefinitionEntryAndCompoundChangeMatchesIndependentOriginalJsonProjection() throws Exception {
        var artifacts = materials(); var stored = stored(artifacts); var expected = new Schema2SourceReadFixture(artifacts);
        for (String table : TABLES) {
            var wanted = expected.rows().get(table); var found = stored.rows(table);
            assertFalse(wanted.isEmpty(), table); assertEquals(wanted.size(), found.size(), table);
            for (var row : wanted) {
                List<String> identity = new ArrayList<>(List.of("artifact_module_key", "release_version", "author_index"));
                if (table.equals("rule_schema2_ability_requirement")) identity.addAll(List.of("author_kind", "group_index", "alternative_index"));
                var matches = found.stream().filter(candidate -> identity.stream().allMatch(column -> same(row.get(column), candidate.get(column)))).toList();
                assertEquals(1, matches.size(), table + " " + identity); var match = matches.getFirst(); assertEquals(row.keySet(), match.keySet());
                for (String column : row.keySet()) assertTrue(same(row.get(column), match.get(column)), table + "/" + column);
            }
        }
        assertTrue(stored.rows("rule_schema2_class_progression").stream().allMatch(row -> row.get("multiclass_prerequisite_present").equals(1)));
        assertTrue(stored.rows("rule_schema2_ability_requirement").stream().anyMatch(row -> row.get("group_index").equals(1)));
        assertTrue(stored.rows("rule_schema2_ability_requirement").stream().anyMatch(row -> row.get("alternative_index").equals(1)));
        assertTrue(stored.rows("rule_schema2_advancement_change").stream().anyMatch(row -> same(row.get("target_kind"), bytes("entry"))));
        assertTrue(stored.rows("rule_schema2_advancement_change").stream().anyMatch(row -> row.get("prerequisite_value") != null));
        assertTrue(stored.rows("rule_schema2_advancement_change").stream().anyMatch(row -> same(row.get("growth_grant_kind"), bytes("CHOICE"))));
        JdbcSchema2CharacterSourceRepository.verifyAll(stored.connection, artifacts);
        assertEquals(2, expected.load(expected.selections()).artifacts().size());
        assertEquals(0, stored.commits); assertEquals(0, stored.rollbacks); assertEquals(0, stored.closes);
    }

    @Test void everyTypedColumnAndEveryMissingExtraRowIncludingNestedPredicateAtomsFailsClosed() throws Exception {
        var artifacts = materials(); var baseline = stored(artifacts);
        for (String table : TABLES) {
            assertFalse(baseline.rows(table).isEmpty(), table);
            for (String column : baseline.rows(table).getFirst().keySet()) {
                var fixture = stored(artifacts); var row = fixture.rows(table).getFirst(); Object value = row.get(column);
                row.put(column, value instanceof byte[] array ? bytes(new String(array, StandardCharsets.US_ASCII) + "_wrong")
                        : value instanceof Integer integer ? integer + 1 : value == null ? "unexpected" : value + " wrong");
                assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, artifacts), table + "/" + column);
            }
            var missing = stored(artifacts); missing.rows(table).removeLast();
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(missing.connection, artifacts), table);
            var extra = stored(artifacts); extra.rows(table).add(new LinkedHashMap<>(extra.rows(table).getFirst()));
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(extra.connection, artifacts), table);
        }
        var malformed = stored(artifacts); malformed.rows("rule_schema2_ability_requirement").getFirst().put("group_index", null);
        assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(malformed.connection, artifacts));
    }

    @Test void omittedAndExplicitlyEmptyPrerequisitesRemainDifferentAndUnselectedChildCorruptionIsRejected() throws Exception {
        var partial = stored(Schema2ProgressionFixtures.artifacts());
        assertEquals(0, partial.rows("rule_schema2_class_progression").getFirst().get("multiclass_prerequisite_present"));
        assertTrue(partial.rows("rule_schema2_ability_requirement").isEmpty());
        var files = Schema2ProgressionFixtures.fullPackages(); var definitions = json(files.get(0).get("rules.json"));
        for (var element : definitions) if (element.getAsJsonObject().has("multiclass_prerequisite"))
            element.getAsJsonObject().add("multiclass_prerequisite", JsonParser.parseString("{\"all_of\":[]}"));
        files.get(0).put("rules.json", definitions.toString().getBytes(StandardCharsets.UTF_8));
        var empty = stored(Schema2GrantFixtures.artifacts(files));
        assertTrue(empty.rows("rule_schema2_class_progression").stream().allMatch(row -> row.get("multiclass_prerequisite_present").equals(1)));
        JdbcSchema2CharacterSourceRepository.verifyAll(empty.connection, Schema2GrantFixtures.artifacts(files));
        var fixture = new Schema2SourceReadFixture(materials());
        assertEquals(1, fixture.load(fixture.selections().subList(0, 1)).artifacts().size());
        fixture = new Schema2SourceReadFixture(materials());
        fixture.rows().get("rule_schema2_ability_requirement").getLast().put("minimum_value", 20);
        var corrupt = fixture;
        assertThrows(SQLException.class, () -> corrupt.load(corrupt.selections().subList(0, 1)));
        assertEquals(0, corrupt.commits); assertEquals(1, corrupt.rollbacks);
    }

    @Test void allNewTypedWritesStillBelongToTheCallerAndEveryFailureCanRollBackAllRows() throws Exception {
        var artifacts = materials(); int writes = stored(artifacts).writes;
        for (int write = 1; write <= writes; write++) for (int affected : new int[]{-1, 0, 2}) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = write; fixture.affected = affected;
            assertThrows(SQLException.class, () -> { for (var artifact : artifacts) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact); });
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
            fixture.connection.rollback(); assertTrue(fixture.tables.isEmpty());
        }
    }

    private static List<ProductionRuleArtifact> materials() {
        var files = Schema2ProgressionFixtures.fullPackages(); var definitions = json(files.get(0).get("rules.json"));
        JsonObject progression = definition(definitions, "character.class_progression");
        progression.add("multiclass_prerequisite", JsonParser.parseString("{\"all_of\":[{\"any_of\":[{\"ability\":\"ability.strength\",\"minimum\":13},{\"ability\":\"ability.dexterity\",\"minimum\":13}]},{\"any_of\":[{\"ability\":\"ability.charisma\",\"minimum\":12}]}]}"));
        files.get(0).put("rules.json", definitions.toString().getBytes(StandardCharsets.UTF_8));
        var changes = json(files.get(1).get("changes.json"));
        change(changes, progression.getAsJsonObject("identity"), "multiclass_prerequisite", JsonParser.parseString("{\"all_of\":[{\"any_of\":[{\"ability\":\"ability.wisdom\",\"minimum\":11}]},{\"any_of\":[{\"ability\":\"ability.intelligence\",\"minimum\":12},{\"ability\":\"ability.constitution\",\"minimum\":13}]}]}"));
        var subclass = definition(definitions, "character.subclass");
        change(changes, subclass.getAsJsonObject("identity"), "selection_level", new JsonPrimitive(4));
        change(changes, subclass.getAsJsonObject("identity"), "owner_class", subclass.getAsJsonObject("owner_class").deepCopy());
        var feature = definition(definitions, "character.feature");
        for (var pair : Map.of("feature_kind", "OPTION", "execution_mode", "BLOCKED", "execution_algorithm", "BLOCKED_DOWNSTREAM_SYSTEM_V1").entrySet())
            change(changes, feature.getAsJsonObject("identity"), pair.getKey(), new JsonPrimitive(pair.getValue()));
        change(changes, feature.getAsJsonObject("identity"), "owner", feature.getAsJsonObject("owner").deepCopy());
        change(changes, feature.getAsJsonObject("identity"), "level", new JsonPrimitive(2));
        var feat = definition(definitions, "character.feat");
        change(changes, feat.getAsJsonObject("identity"), "prerequisite", JsonParser.parseString("{\"all_of\":[]}"));
        var proficiency = definition(definitions, "character.proficiency");
        change(changes, proficiency.getAsJsonObject("identity"), "ability_key", new JsonPrimitive("ability.dexterity"));
        change(changes, proficiency.getAsJsonObject("identity"), "category", new JsonPrimitive("SKILL"));
        var set = definition(definitions, "catalog.proficiency_set");
        change(changes, set.getAsJsonObject("identity"), "minimum_enabled_members", new JsonPrimitive(2));
        var rest = definition(definitions, "adventure.rest");
        change(changes, rest.getAsJsonObject("identity"), "algorithm", new JsonPrimitive("SRD_2014_REST_V1"));
        change(changes, rest.getAsJsonObject("identity"), "source_page", new JsonPrimitive(87));
        var entries = json(files.get(0).get("growth.json"));
        var level = entries.get(0).getAsJsonObject();
        change(changes, level.getAsJsonObject("identity"), "level", new JsonPrimitive(1));
        change(changes, level.getAsJsonObject("identity"), "asi_available", new JsonPrimitive(true));
        for (var element : entries) {
            var value = element.getAsJsonObject();
            if (value.has("proficiency")) { change(changes, value.getAsJsonObject("identity"), "proficiency", value.getAsJsonObject("proficiency").deepCopy()); break; }
        }
        for (var element : entries) {
            var value = element.getAsJsonObject();
            if (value.has("growth_grant") && value.getAsJsonObject("growth_grant").get("kind").getAsString().equals("CHOICE")) {
                var grant = value.getAsJsonObject("growth_grant").deepCopy(); grant.addProperty("count", 2);
                change(changes, value.getAsJsonObject("identity"), "growth_grant", grant); break;
            }
        }
        files.get(1).put("changes.json", changes.toString().getBytes(StandardCharsets.UTF_8));
        return Schema2GrantFixtures.artifacts(files);
    }
    private static JsonArray json(byte[] bytes) { return Schema2GrantFixtures.json(bytes).getAsJsonArray(); }
    private static JsonObject definition(JsonArray rows, String type) {
        for (var row : rows) if (row.getAsJsonObject().getAsJsonObject("identity").get("rule_type").getAsString().equals(type)) return row.getAsJsonObject();
        throw new AssertionError("Missing definition " + type);
    }
    private static void change(JsonArray changes, JsonObject target, String field, JsonElement value) {
        var row = new JsonObject(); row.addProperty("op", "set_field"); row.add("target", target.deepCopy()); row.addProperty("field", field); row.add("value", value); changes.add(row);
    }
    private static Schema2RuntimeJdbcFixture stored(List<ProductionRuleArtifact> artifacts) throws SQLException {
        var fixture = new Schema2RuntimeJdbcFixture(); for (var artifact : artifacts) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact); return fixture;
    }
    private static boolean same(Object left, Object right) { return left instanceof byte[] array ? right instanceof byte[] other && Arrays.equals(array, other) : Objects.equals(left, right); }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
}
