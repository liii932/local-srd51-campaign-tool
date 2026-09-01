package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V011CompleteCharacterCatalogDraftTest {
    private static final String RESOURCE =
            "/db/migration/V011__complete_character_catalog_draft.sql";
    private static final Pattern DEFINITION = Pattern.compile(
            "(?m)^\\('([^']+)', '([^']+)',");

    @Test
    void installsTheCompleteCharacterDirectoryAsDraftOnly() throws Exception {
        String sql = loadSql();

        assertTrue(sql.contains("'dnd5e2014_srd51_se', '1', 2, 'SHA-256', NULL, 'DRAFT'"));
        assertTrue(sql.contains("CREATE TABLE `module_catalog_definition_v2`"));
        assertTrue(sql.contains("CREATE TABLE `module_catalog_attribute_v2`"));
        assertTrue(sql.contains("CREATE TABLE `module_catalog_relation_v2`"));
        assertFalse(sql.contains("'RELEASED'"));
        assertFalse(sql.contains("UPDATE module_release"));
        assertFalse(sql.contains("INSERT INTO campaign"));

        Set<String> identities = new HashSet<>();
        Matcher matcher = DEFINITION.matcher(seedInsert(sql));
        int rows = 0;
        while (matcher.find()) {
            rows++;
            assertTrue(identities.add(matcher.group(1) + "\u0000" + matcher.group(2)));
        }

        assertEquals(9, countType(identities, "character.race"));
        assertEquals(4, countType(identities, "character.subrace"));
        assertEquals(1, countType(identities, "character.background"));
        assertEquals(18, countType(identities, "character.language"));
        assertEquals(37, countType(identities, "character.tool"));
        assertEquals(12, countType(identities, "character.class"));
        assertEquals(12, countType(identities, "character.subclass"));
        assertEquals(274, countType(identities, "character.feature"));
        assertEquals(16, countType(identities, "character.resource"));
        assertEquals(1, countType(identities, "character.feat"));
        assertEquals(rows, identities.size());

        assertTrue(identities.contains("character.subclass\u0000subclass.berserker"));
        assertTrue(identities.contains("character.subclass\u0000subclass.evocation"));
        assertTrue(identities.contains("character.feature\u0000feature.warlock.invocation.witch_sight"));
        assertTrue(identities.contains("character.feature\u0000feature.hunter.uncanny_dodge"));
        assertTrue(identities.contains("character.feat\u0000feat.grappler"));
    }

    @Test
    void seedRowsHaveClosedRequiredAttributesAndNoDanglingPrimaryRelations()
            throws Exception {
        List<SeedRow> rows = seedRows(loadSql());
        Set<String> identities = rows.stream()
                .map(row -> row.definitionType() + "\u0000" + row.definitionKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        for (SeedRow row : rows) {
            int page = Integer.parseInt(row.sourcePage());
            assertTrue(page >= 3 && page <= 74);
            if (!"NULL".equals(row.relationType())) {
                assertTrue(identities.contains(row.targetType() + "\u0000" + row.targetKey()));
            }
            switch (row.definitionType()) {
                case "character.subrace" ->
                        assertEquals("subrace.parent_race", row.relationType());
                case "character.subclass" ->
                        assertEquals("subclass.parent_class", row.relationType());
                case "character.class" ->
                        assertTrue(Set.of("6", "8", "10", "12").contains(row.hitDieSides()));
                case "character.feature" -> {
                    int level = Integer.parseInt(row.minimumLevel());
                    assertTrue(level >= 1 && level <= 20);
                    assertEquals("feature.owner", row.relationType());
                }
                case "character.resource" -> {
                    assertTrue(Set.of("SHORT_REST", "LONG_REST", "SPECIAL")
                            .contains(row.recovery()));
                    assertEquals("resource.owner", row.relationType());
                }
                case "character.feat" -> assertEquals("13", row.minimumStrength());
                default -> {
                    // Root definitions have source and category attributes only.
                }
            }
        }
    }

    @Test
    void approvedManifestPreservesV011ThroughV017AsForwardOnlyHistory() throws Exception {
        var expectations = SchemaMigrations.loadExpectations();

        assertEquals(17, expectations.size());
        assertEquals(11, expectations.get(10).version());
        assertEquals("V011__complete_character_catalog_draft.sql",
                expectations.get(10).scriptName());
        assertEquals(12, expectations.get(11).version());
        assertEquals("V012__level_one_character_creation.sql",
                expectations.get(11).scriptName());
        assertEquals(13, expectations.get(12).version());
        assertEquals("V013__level_advancement_hit_dice.sql",
                expectations.get(12).scriptName());
        assertEquals(14, expectations.get(13).version());
        assertEquals("V014__class_feature_lifecycle.sql",
                expectations.get(13).scriptName());
        assertEquals(15, expectations.get(14).version());
        assertEquals("V015__multiclass_asi_feat_draft.sql",
                expectations.get(14).scriptName());
        assertEquals(16, expectations.get(15).version());
        assertEquals("V016__starting_proficiency_baseline_draft.sql",
                expectations.get(15).scriptName());
        assertEquals(17, expectations.get(16).version());
        assertEquals("V017__character_archive_v2_origin.sql",
                expectations.get(16).scriptName());
    }

    private static int countType(Set<String> identities, String type) {
        String prefix = type + "\u0000";
        return (int) identities.stream().filter(identity -> identity.startsWith(prefix)).count();
    }

    private static String seedInsert(String sql) {
        int start = sql.indexOf("INSERT INTO `v011_character_seed`");
        int end = sql.indexOf("INSERT INTO `module_catalog_definition_v2`", start);
        assertTrue(start >= 0 && end > start);
        return sql.substring(start, end);
    }

    private static List<SeedRow> seedRows(String sql) {
        return seedInsert(sql).lines()
                .map(String::strip)
                .filter(line -> line.startsWith("('") && (line.endsWith("),")
                        || line.endsWith(");")))
                .map(line -> line.substring(1, line.length() - 2))
                .map(line -> line.split(", ", -1))
                .map(parts -> {
                    assertEquals(13, parts.length);
                    return new SeedRow(
                            value(parts[0]), value(parts[1]), value(parts[4]), value(parts[5]),
                            value(parts[6]), value(parts[7]), value(parts[8]), value(parts[9]),
                            value(parts[10]), value(parts[11]), value(parts[12]));
                })
                .toList();
    }

    private static String value(String sqlValue) {
        if ("NULL".equals(sqlValue)) {
            return sqlValue;
        }
        assertTrue(sqlValue.startsWith("'") && sqlValue.endsWith("'"));
        return sqlValue.substring(1, sqlValue.length() - 1).replace("''", "'");
    }

    private static String loadSql() throws Exception {
        try (InputStream input = V011CompleteCharacterCatalogDraftTest.class
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing V011 migration");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record SeedRow(
            String definitionType,
            String definitionKey,
            String sourcePage,
            String minimumLevel,
            String category,
            String hitDieSides,
            String recovery,
            String minimumStrength,
            String relationType,
            String targetType,
            String targetKey) {
    }
}
