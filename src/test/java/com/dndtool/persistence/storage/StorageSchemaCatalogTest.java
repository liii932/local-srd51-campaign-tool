package com.dndtool.persistence.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StorageSchemaCatalogTest {
    static final String UUID = "d158ee4d-96d5-4d6b-9cf9-c83514da69b8";
    static final String SCHEMA = """
        {"declarationVersion":1,"applicationModel":"srd51-vitality-1","storageModel":"sqlite-vitality-1","dialect":"sqlite-strict-1","modules":[
          {"id":"core","dependsOn":[],"tables":[
            {"name":"core_kind","columns":[{"name":"kind","type":"text","nullable":false,"minCodePoints":2,"maxCodePoints":3,"ascii":true}],"primaryKey":["kind"],"uniqueKeys":[],"foreignKeys":[],"checks":[],"indexes":[]},
            {"name":"core_campaign","columns":[{"name":"id","type":"uuid","nullable":false},{"name":"name","type":"text","nullable":false,"minCodePoints":1,"maxCodePoints":80,"ascii":false}],"primaryKey":["id"],"uniqueKeys":[],"foreignKeys":[],"checks":[],"indexes":[]}
          ],"seeds":[{"table":"core_kind","rows":[{"kind":"PC"},{"kind":"NPC"}]}]},
          {"id":"characters","dependsOn":["core"],"tables":[
            {"name":"characters_character","columns":[
              {"name":"id","type":"uuid","nullable":false},
              {"name":"campaign_id","type":"uuid","nullable":false},
              {"name":"kind","type":"text","nullable":false,"minCodePoints":2,"maxCodePoints":3,"ascii":true},
              {"name":"name","type":"text","nullable":false,"minCodePoints":1,"maxCodePoints":80,"ascii":false,"default":"O'Brien"},
              {"name":"hp_max","type":"int64","nullable":false,"minimum":"1"},
              {"name":"hp_current","type":"int64","nullable":false,"minimum":"0"},
              {"name":"archived","type":"boolean","nullable":false,"default":false},
              {"name":"digest","type":"digest256","nullable":true},
              {"name":"payload","type":"bytes","nullable":true,"maxBytes":8}
            ],"primaryKey":["id"],"uniqueKeys":[{"name":"uq_characters_campaign_id","columns":["campaign_id","id"]}],
            "foreignKeys":[
              {"name":"fk_characters_campaign","columns":["campaign_id"],"references":{"table":"core_campaign","columns":["id"]},"onDelete":"NO_ACTION","onUpdate":"NO_ACTION","deferred":false},
              {"name":"fk_characters_kind","columns":["kind"],"references":{"table":"core_kind","columns":["kind"]},"onDelete":"NO_ACTION","onUpdate":"NO_ACTION","deferred":false}
            ],"checks":[{"name":"ck_characters_hp","expression":{"op":"le","left":{"column":"hp_current"},"right":{"column":"hp_max"}}}],
            "indexes":[{"name":"idx_characters_kind","columns":["kind"],"unique":false}]
            }
          ],"seeds":[]}
        ]}
        """;

    static StorageSchemaCatalog read(String json) { return StorageSchemaCatalog.read(json.getBytes(StandardCharsets.UTF_8)); }
    static JsonObject schema() { return JsonParser.parseString(SCHEMA).getAsJsonObject(); }
    static JsonObject module(JsonObject root, int index) { return root.getAsJsonArray("modules").get(index).getAsJsonObject(); }
    static JsonObject character(JsonObject root) { return module(root, 1).getAsJsonArray("tables").get(0).getAsJsonObject(); }

    @Test
    void independentCanonicalVectorFixesDefaultsFieldOrderAndDomainSeparatedDigest() {
        String minimal = """
            {"declarationVersion":1,"applicationModel":"srd51-vitality-1","storageModel":"sqlite-vitality-1","dialect":"sqlite-strict-1","modules":[{"id":"core","dependsOn":[],"tables":[{"name":"core_flag","columns":[{"name":"id","type":"int64","nullable":false,"minimum":"0","maximum":"1"}],"primaryKey":["id"],"uniqueKeys":[],"foreignKeys":[],"checks":[],"indexes":[]}],"seeds":[{"table":"core_flag","rows":[{"id":"0"}]}]}]}
            """;
        String canonical = """
            {"applicationModel":"srd51-vitality-1","declarationVersion":1,"dialect":"sqlite-strict-1","implementationContract":"sqlite-vitality-schema-1","modules":[{"dependsOn":[],"id":"core","seeds":[{"rows":[{"id":"0"}],"table":"core_flag"}],"tables":[{"checks":[],"columns":[{"default":null,"hasDefault":false,"maximum":"1","minimum":"0","name":"id","nullable":false,"type":"int64"}],"foreignKeys":[],"indexes":[],"name":"core_flag","primaryKey":["id"],"uniqueKeys":[]}]}],"storageModel":"sqlite-vitality-1"}
            """.strip();
        StoragePlan plan = read(minimal).plan(Set.of("core"));
        assertEquals(canonical, plan.canonicalPlan());
        assertEquals("f0b3a6f3c2db1a8a7b66c5a24a92ba3ea2edd3f870cad540fc5f37f8f7e12862", plan.digest());
    }

    @Test
    void deterministicClosureDigestAndReasonsIgnoreUnorderedDeclarationCollections() {
        StoragePlan first = read(SCHEMA).plan(Set.of("characters"));
        JsonObject reordered = schema();
        JsonArray tables = module(reordered, 0).getAsJsonArray("tables");
        tables.add(tables.remove(0));
        JsonArray seeds = module(reordered, 0).getAsJsonArray("seeds").get(0).getAsJsonObject().getAsJsonArray("rows");
        seeds.add(seeds.remove(0));
        JsonArray modules = reordered.getAsJsonArray("modules");
        modules.add(modules.remove(0));
        StoragePlan second = read(reordered.toString()).plan(Set.of("characters", "core"));
        assertEquals(List.of("core", "characters"), first.moduleIds());
        assertEquals(List.of("core_campaign", "core_kind", "characters_character"), first.tableNames());
        assertEquals(List.of("characters", "core"), first.inclusionReasons().get("core"));
        assertEquals(first.digest(), second.digest());
        assertEquals(first.canonicalPlan(), second.canonicalPlan());
        assertEquals(first.createStatements(), second.createStatements());
        assertTrue(first.digest().matches("[0-9a-f]{64}"));
        assertFalse(first.createStatements().stream().anyMatch(sql -> sql.contains("IF NOT EXISTS")));
        assertThrows(UnsupportedOperationException.class, () -> first.inclusionReasons().put("bad", List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidJson")
    void rejectsMalformedJsonBeforePlanning(String name, byte[] input, String code) {
        assertEquals(code, assertThrows(StorageSchemaException.class, () -> StorageSchemaCatalog.read(input)).code());
    }

    static Stream<Arguments> invalidJson() {
        return Stream.of(
                Arguments.of("bom", ("\ufeff" + SCHEMA).getBytes(StandardCharsets.UTF_8), "BOM"),
                Arguments.of("bad utf8", new byte[]{(byte) 0xc3, 0x28}, "UTF8"),
                Arguments.of("duplicate", SCHEMA.replace("\"declarationVersion\":1", "\"declarationVersion\":1,\"declarationVersion\":1").getBytes(StandardCharsets.UTF_8), "DUPLICATE_JSON_KEY"),
                Arguments.of("trailing", (SCHEMA + "{}").getBytes(StandardCharsets.UTF_8), "JSON"),
                Arguments.of("comment", ("/*x*/" + SCHEMA).getBytes(StandardCharsets.UTF_8), "JSON"),
                Arguments.of("orphan surrogate", SCHEMA.replace("O'Brien", "\\ud800").getBytes(StandardCharsets.UTF_8), "UNICODE"),
                Arguments.of("unknown field", SCHEMA.replace("\"declarationVersion\":1", "\"extra\":1,\"declarationVersion\":1").getBytes(StandardCharsets.UTF_8), "UNKNOWN_FIELD"),
                Arguments.of("missing field", SCHEMA.replace("\"declarationVersion\":1,", "").getBytes(StandardCharsets.UTF_8), "MISSING_FIELD"),
                Arguments.of("resource budget", new byte[StorageSchemaCatalog.MAX_BYTES + 1], "BUDGET"));
    }

    @Test
    void rejectsInvalidTypesDefaultsRangesAndUnicode() {
        assertCode(SCHEMA.replace("\"minimum\":\"1\"", "\"minimum\":1"), "STRING");
        assertCode(SCHEMA.replace("\"minimum\":\"1\"", "\"minimum\":\"01\""), "INT64");
        assertCode(SCHEMA.replace("\"minimum\":\"1\"", "\"minimum\":\"9223372036854775808\""), "INT64");
        assertCode(SCHEMA.replace("\"type\":\"uuid\"", "\"type\":\"json\""), "UNSUPPORTED_TYPE");
        assertCode(SCHEMA.replace("\"default\":false", "\"default\":0"), "BOOLEAN");
        assertCode(SCHEMA.replace("O'Brien", "e\\u0301"), "TEXT");
        assertCode(SCHEMA.replace("O'Brien", "a\\u007fb"), "TEXT");
        assertCode(SCHEMA.replace("\"ascii\":true", "\"ascii\":true,\"default\":\"汉字\""), "TEXT");
        assertCode(SCHEMA.replace("\"type\":\"uuid\",\"nullable\":false", "\"type\":\"uuid\",\"nullable\":false,\"default\":\"d158ee4d-96d5-3d6b-9cf9-c83514da69b8\""), "UUID");
        assertCode(SCHEMA.replace("\"maxBytes\":8", "\"maxBytes\":8,\"default\":\"YQ\""), "BYTES");
    }

    @Test
    void rejectsDependencyReferencesParentKeysAndMissingChildIndexes() {
        JsonObject root = schema();
        module(root, 0).getAsJsonArray("dependsOn").add("characters");
        assertCode(root.toString(), "DEPENDENCY_CYCLE");
        root = schema();
        module(root, 1).add("dependsOn", new JsonArray());
        assertCode(root.toString(), "FK_DEPENDENCY");
        root = schema();
        character(root).getAsJsonArray("foreignKeys").get(0).getAsJsonObject().getAsJsonObject("references").addProperty("table", "core_missing");
        assertCode(root.toString(), "FK_TARGET");
        root = schema();
        character(root).add("indexes", new JsonArray());
        assertCode(root.toString(), "FK_CHILD_INDEX");
        root = schema();
        character(root).getAsJsonArray("columns").get(1).getAsJsonObject().addProperty("type", "digest256");
        assertCode(root.toString(), "FK_TYPE");
        root = schema();
        character(root).getAsJsonArray("foreignKeys").get(0).getAsJsonObject().getAsJsonObject("references").getAsJsonArray("columns").set(0, new com.google.gson.JsonPrimitive("name"));
        assertCode(root.toString(), "FK_PARENT_KEY");
        assertThrows(StorageSchemaException.class, () -> read(SCHEMA).plan(Set.of("unknown")));
    }

    @Test
    void rejectsUnknownExpressionKeysMismatchedTypesAndBudgets() {
        assertCode(SCHEMA.replace("\"op\":\"le\"", "\"op\":\"sql\""), "PREDICATE_OPERATOR");
        assertCode(SCHEMA.replace("\"column\":\"hp_max\"", "\"column\":\"name\""), "PREDICATE_TYPE");
        String nested = "{\"op\":\"notNull\",\"column\":\"name\"}";
        for (int i = 0; i < 16; i++) nested = "{\"op\":\"not\",\"arg\":" + nested + "}";
        JsonObject root = schema();
        character(root).getAsJsonArray("checks").get(0).getAsJsonObject().add("expression", JsonParser.parseString(nested));
        assertCode(root.toString(), "PREDICATE_BUDGET");
        root = schema();
        JsonArray columns = character(root).getAsJsonArray("columns");
        for (int i = columns.size(); i < 257; i++) {
            JsonObject column = new JsonObject(); column.addProperty("name", "column_" + i); column.addProperty("type", "int64"); column.addProperty("nullable", false); columns.add(column);
        }
        assertCode(root.toString(), "BUDGET");
    }

    @Test
    void seedRowsMustBeCompleteTypedAndUnique() {
        assertCode(SCHEMA.replace("{\"kind\":\"PC\"}", "{}"), "MISSING_FIELD");
        assertCode(SCHEMA.replace("{\"kind\":\"PC\"}", "{\"kind\":\"NPC\"}"), "DUPLICATE_SEED");
        assertCode(SCHEMA.replace("{\"kind\":\"PC\"}", "{\"kind\":1}"), "STRING");
    }

    static void assertCode(String json, String code) {
        assertEquals(code, assertThrows(StorageSchemaException.class, () -> read(json)).code());
    }
}
