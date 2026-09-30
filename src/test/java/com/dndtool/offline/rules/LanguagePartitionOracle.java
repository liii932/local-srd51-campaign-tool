package com.dndtool.offline.rules;

import com.dndtool.module.LanguagePartition;
import com.dndtool.module.ModuleCanonicalEncoderV2;
import com.dndtool.module.ModuleCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Independent six-field expectations; never obtains expected values from a production reader/mapper. */
final class LanguagePartitionOracle {
    record Row(String key, String name, String description, String category, int page, int order) {}

    private static final String MATRIX = """
            language.abyssal|Abyssal|Abyssal is an SRD 5.1 language catalog entry.|EXOTIC|59|1
            language.celestial|Celestial|Celestial is an SRD 5.1 language catalog entry.|EXOTIC|59|2
            language.common|Common|Common is an SRD 5.1 language catalog entry.|STANDARD|59|3
            language.deep_speech|Deep Speech|Deep Speech is an SRD 5.1 language catalog entry.|EXOTIC|59|4
            language.draconic|Draconic|Draconic is an SRD 5.1 language catalog entry.|EXOTIC|59|5
            language.druidic|Druidic|Druidic is an SRD 5.1 language catalog entry.|SECRET|59|6
            language.dwarvish|Dwarvish|Dwarvish is an SRD 5.1 language catalog entry.|STANDARD|59|7
            language.elvish|Elvish|Elvish is an SRD 5.1 language catalog entry.|STANDARD|59|8
            language.giant|Giant|Giant is an SRD 5.1 language catalog entry.|STANDARD|59|9
            language.gnomish|Gnomish|Gnomish is an SRD 5.1 language catalog entry.|STANDARD|59|10
            language.goblin|Goblin|Goblin is an SRD 5.1 language catalog entry.|STANDARD|59|11
            language.halfling|Halfling|Halfling is an SRD 5.1 language catalog entry.|STANDARD|59|12
            language.infernal|Infernal|Infernal is an SRD 5.1 language catalog entry.|EXOTIC|59|13
            language.orc|Orc|Orc is an SRD 5.1 language catalog entry.|STANDARD|59|14
            language.primordial|Primordial|Primordial is an SRD 5.1 language catalog entry.|EXOTIC|59|15
            language.sylvan|Sylvan|Sylvan is an SRD 5.1 language catalog entry.|EXOTIC|59|16
            language.thieves_cant|Thieves Cant|Thieves Cant is an SRD 5.1 language catalog entry.|SECRET|59|17
            language.undercommon|Undercommon|Undercommon is an SRD 5.1 language catalog entry.|EXOTIC|59|18
            """;

    static List<Row> baseline() {
        return MATRIX.lines().map(line -> line.split("\\|", -1))
                .map(v -> new Row(v[0], v[1], v[2], v[3], Integer.parseInt(v[4]), Integer.parseInt(v[5])))
                .toList();
    }

    static byte[] vector() throws Exception {
        return HexFormat.of().parseHex(Files.readString(
                Path.of("src/test/resources/module-canonical-v2-languages.hex")).replaceAll("\\s", ""));
    }

    static void assertPartition(List<Row> expected, LanguagePartition actual) {
        assertEquals(18, actual.languages().size());
        for (int i = 0; i < 18; i++) {
            var e = expected.get(i); var a = actual.languages().get(i);
            assertEquals(e, new Row(a.languageKey(), a.displayName(), a.description(),
                    a.category().name(), a.sourcePage(), a.sortOrder()), e.key());
        }
        assertProjection(expected, projection(actual));
    }

    static void assertStored(List<Row> expected, List<Map<String, Object>> actual, String identityColumn) {
        assertEquals(18, actual.size());
        var byKey = actual.stream().collect(java.util.stream.Collectors.toMap(
                r -> new String(assertInstanceOf(byte[].class, r.get("language_key")), StandardCharsets.US_ASCII), r -> r));
        for (var e : expected) {
            var r = byKey.get(e.key()); assertNotNull(r, e.key());
            assertEquals(java.util.Set.of(identityColumn, "language_key", "display_name", "description",
                    "category", "source_page", "sort_order"), r.keySet());
            assertEquals(e.name(), r.get("display_name")); assertEquals(e.description(), r.get("description"));
            assertArrayEquals(e.category().getBytes(StandardCharsets.US_ASCII), assertInstanceOf(byte[].class, r.get("category")));
            assertEquals(e.page(), assertInstanceOf(Integer.class, r.get("source_page")));
            assertEquals(e.order(), assertInstanceOf(Integer.class, r.get("sort_order")));
        }
    }

    static void assertProjection(List<Row> expected, ModuleCatalog actual) {
        assertEquals(18, actual.catalogDefinitions().size());
        assertEquals(36, actual.catalogAttributes().size());
        assertEquals(0, actual.catalogRelations().size());
        for (int i = 0; i < 18; i++) {
            var e = expected.get(i); var definition = actual.catalogDefinitions().get(i);
            assertEquals(new ModuleCatalog.CatalogDefinition("character.language", e.key(),
                    e.name(), e.description(), e.order()), definition);
            var category = actual.catalogAttributes().get(i * 2);
            var page = actual.catalogAttributes().get(i * 2 + 1);
            assertEquals(new ModuleCatalog.CatalogAttribute("character.language", e.key(),
                    "catalog.category", 1, "IDENTIFIER", new ModuleCatalog.IdentifierValue(e.category())), category);
            assertEquals(new ModuleCatalog.CatalogAttribute("character.language", e.key(),
                    "source.page", 1, "INTEGER", new ModuleCatalog.IntegerValue(e.page())), page);
            assertInstanceOf(ModuleCatalog.IdentifierValue.class, category.value());
            assertInstanceOf(ModuleCatalog.IntegerValue.class, page.value());
        }
    }

    /** Actual-side adapter only. It confers neither full-catalog completeness nor release approval. */
    static ModuleCatalog projection(LanguagePartition actual) {
        var definitions = actual.definitions().stream().map(d -> new ModuleCatalog.CatalogDefinition(
                d.definitionType(), d.definitionKey(), d.displayName(), d.description(), d.sortOrder())).toList();
        var attributes = actual.attributes().stream().map(a -> new ModuleCatalog.CatalogAttribute(
                a.definitionType(), a.definitionKey(), a.attributeKey(), a.attributeOrder(), a.valueType(),
                a instanceof LanguagePartition.CategoryAttribute c ? new ModuleCatalog.IdentifierValue(c.value().name())
                        : new ModuleCatalog.IntegerValue(((LanguagePartition.SourcePageAttribute) a).value()))).toList();
        return catalog(definitions, attributes);
    }

    static ModuleCatalog catalog(List<ModuleCatalog.CatalogDefinition> definitions,
                                 List<ModuleCatalog.CatalogAttribute> attributes) {
        return new ModuleCatalog(new ModuleCatalog.Release("dnd5e2014_srd51_se", "1", 2, "SHA-256", null, "DRAFT"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), definitions, attributes, List.of());
    }

    static byte[] canonical(LanguagePartition actual) throws Exception {
        return new ModuleCanonicalEncoderV2().encode(projection(actual));
    }

    static List<Row> changed(String field) {
        var rows = new ArrayList<>(baseline()); var old = rows.get(2);
        rows.set(2, new Row(old.key(), field.equals("unicode") ? "😀".repeat(120)
                        : field.equals("display_name") ? "Common revised" : old.name(),
                field.equals("unicode") ? "é" + "😀".repeat(999)
                        : field.equals("description") ? "Revised Common description." : old.description(), old.category(),
                field.equals("source_page") ? 60 : old.page(), field.equals("sort_order") ? 4 : old.order()));
        if (field.equals("sort_order")) {
            var other = rows.get(3);
            rows.set(3, new Row(other.key(), other.name(), other.description(), other.category(), other.page(), 3));
        }
        return List.copyOf(rows);
    }
}
