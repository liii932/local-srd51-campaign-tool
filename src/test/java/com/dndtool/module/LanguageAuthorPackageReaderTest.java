package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.persistence.ModuleCatalog;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class LanguageAuthorPackageReaderTest {
    private static final Path SOURCE = Path.of("rule-packages/srd51-complete");
    private static final String MATRIX = """
            abyssal|Abyssal|EXOTIC
            celestial|Celestial|EXOTIC
            common|Common|STANDARD
            deep_speech|Deep Speech|EXOTIC
            draconic|Draconic|EXOTIC
            druidic|Druidic|SECRET
            dwarvish|Dwarvish|STANDARD
            elvish|Elvish|STANDARD
            giant|Giant|STANDARD
            gnomish|Gnomish|STANDARD
            goblin|Goblin|STANDARD
            halfling|Halfling|STANDARD
            infernal|Infernal|EXOTIC
            orc|Orc|STANDARD
            primordial|Primordial|EXOTIC
            sylvan|Sylvan|EXOTIC
            thieves_cant|Thieves Cant|SECRET
            undercommon|Undercommon|EXOTIC
            """;
    private final LanguageAuthorPackageReader reader = new LanguageAuthorPackageReader();

    @Test
    void formalSourceMatchesEveryIndependentFieldAndTypedProjection() throws Exception {
        var result = read(header(), languages());
        assertEquals("PARTITION", result.verificationScope());
        assertEquals(new BuiltinModuleReleaseRegistry.Identity("dnd5e2014_srd51_se", "1"), result.header().identity());
        assertEquals(1, result.header().authorSchemaVersion());
        assertEquals(2, result.header().canonicalFormatVersion());
        assertEquals(2, result.header().archiveFormatVersion());
        assertEquals("SHA-256", result.header().hashAlgorithm());
        assertEquals(18, result.partition().definitions().size());
        assertEquals(36, result.partition().attributes().size());
        String[] expected = MATRIX.strip().split("\n");
        for (int i = 0; i < expected.length; i++) {
            String[] fields = expected[i].split("\\|");
            var row = result.partition().languages().get(i);
            String key = "language." + fields[0];
            String description = fields[1] + " is an SRD 5.1 language catalog entry.";
            assertEquals(key, row.languageKey());
            assertEquals(fields[1], row.displayName());
            assertEquals(description, row.description());
            assertEquals(fields[2], row.category().name());
            assertEquals(59, row.sourcePage());
            assertEquals(i + 1, row.sortOrder());
            var definition = result.partition().definitions().get(i);
            assertEquals("character.language", definition.definitionType());
            assertEquals(key, definition.definitionKey());
            assertEquals(fields[1], definition.displayName());
            assertEquals(description, definition.description());
            assertEquals(i + 1, definition.sortOrder());
            var category = assertInstanceOf(LanguagePartition.CategoryAttribute.class, result.partition().attributes().get(i * 2));
            var page = assertInstanceOf(LanguagePartition.SourcePageAttribute.class, result.partition().attributes().get(i * 2 + 1));
            for (var attribute : List.of(category, page)) {
                assertEquals("character.language", attribute.definitionType());
                assertEquals(key, attribute.definitionKey());
                assertEquals(1, attribute.attributeOrder());
            }
            assertEquals("catalog.category", category.attributeKey());
            assertEquals("IDENTIFIER", category.valueType());
            assertEquals(fields[2], category.value().name());
            assertEquals("source.page", page.attributeKey());
            assertEquals("INTEGER", page.valueType());
            assertEquals(59, page.value());
        }
        var registry = new BuiltinModuleReleaseRegistry();
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.UNPUBLISHED_RELEASE,
                registry.resolveReleased(result.header().identity().moduleKey(), "1").status());
    }

    @Test
    void matchesIndependentCanonicalBytesWithoutDefiningAPartitionDigest() throws Exception {
        String hex = Files.readString(Path.of("src/test/resources/module-canonical-v2-languages.hex"));
        assertArrayEquals(HexFormat.of().parseHex(hex.replaceAll("\\s", "")), canonical(read(header(), languages())));
    }

    @Test
    void arrayOrderDoesNotReplaceExplicitDisplayOrderAndCollectionsAreImmutable() throws Exception {
        var original = read(header(), languages());
        JsonArray array = JsonParser.parseString(languages()).getAsJsonArray();
        JsonArray reversed = new JsonArray();
        for (int i = 17; i >= 0; i--) reversed.add(array.get(i));
        assertEquals(original.partition(), read(header(), reversed.toString()).partition());
        assertArrayEquals(canonical(original), canonical(read(header(), reversed.toString())));
        array.get(2).getAsJsonObject().addProperty("sort_order", 4);
        array.get(3).getAsJsonObject().addProperty("sort_order", 3);
        assertFalse(Arrays.equals(canonical(original), canonical(read(header(), array.toString()))));
        assertThrows(UnsupportedOperationException.class, () -> original.partition().languages().clear());
        var mutable = new ArrayList<>(original.partition().languages());
        var copy = new LanguagePartition(mutable);
        mutable.clear();
        assertEquals(18, copy.languages().size());
    }

    @Test
    void rejectsMissingExtraRepeatedKeysRowsAndWrongCategories() throws Exception {
        for (String category : List.of("UNKNOWN", "standard", "STANDARD ", "SECRET")) {
            rejects(header(), change("category", category, 2));
        }
        rejects(header(), change("language_key", "language.fake", 2));
        rejects(header(), change("language_key", "language.abyssal", 2));
        JsonArray rows = JsonParser.parseString(languages()).getAsJsonArray();
        rows.remove(2);
        rejects(header(), rows.toString());
        rows.add(rows.get(0)); rows.add(rows.get(0));
        rejects(header(), rows.toString());
        for (String field : List.of("language_key", "display_name", "description", "category", "source_page", "sort_order")) {
            rows = JsonParser.parseString(languages()).getAsJsonArray();
            rows.get(0).getAsJsonObject().remove(field);
            rejects(header(), rows.toString());
            rows = JsonParser.parseString(languages()).getAsJsonArray();
            rows.get(0).getAsJsonObject().add(field, com.google.gson.JsonNull.INSTANCE);
            rejects(header(), rows.toString());
            rejects(header(), languages().replaceFirst("\"" + field + "\"\\s*:", "\"" + field + "\":null,\"" + field + "\":"));
        }
        rejects(header(), languages().replaceFirst("\\{", "{\"unknown\":1,"));
        rejects(header(), languages().replaceFirst("\"category\"", "\"cat\\\\u0065gory\":\"EXOTIC\",\"category\""));
    }

    @Test
    void headerIsClosedExactAndCannotClaimApprovalCompletenessOrContentDigest() throws Exception {
        var object = JsonParser.parseString(header()).getAsJsonObject();
        for (String field : new ArrayList<>(object.keySet())) {
            var copy = object.deepCopy(); copy.remove(field); rejects(copy.toString(), languages());
            copy = object.deepCopy(); copy.add(field, null); rejects(copy.toString(), languages());
        }
        for (String field : List.of("content_sha256", "observed_content_sha256", "release_status", "verification_scope", "raw_sha256")) {
            var copy = object.deepCopy(); copy.addProperty(field, "COMPLETE"); rejects(copy.toString(), languages());
        }
        for (String replacement : List.of("dnd5e2014_srd51_se_v1", "dnd5e2014_srd51_se ", "Ｄnd5e2014_srd51_se")) {
            rejects(header().replace("dnd5e2014_srd51_se", replacement), languages());
        }
        for (String version : List.of("01", "1.0", "vA", "va", "a".repeat(65))) {
            rejects(header().replace("\"release_version\": \"1\"", "\"release_version\": \"" + version + "\""), languages());
        }
        rejects(header().replace("SHA-256", "sha256"), languages());
    }

    @Test
    void onlyExactSinglePartitionPathCanBeDeclared() throws Exception {
        for (String path : List.of("../languages.json", "/character/languages.json", "C:/languages.json",
                "character\\\\languages.json", "character/./languages.json", "character/%6canguages.json",
                "con.json", "nul.txt", "com1/rules.json", "aux/data.json", "character/lpt9.json", "Character/languages.json")) {
            rejects(header().replace("character/languages.json", path), languages());
        }
        rejects(header().replace("character.language", "character.race"), languages());
        var object = JsonParser.parseString(header()).getAsJsonObject();
        var declarations = object.getAsJsonArray("partitions");
        declarations.add(declarations.get(0).deepCopy());
        rejects(object.toString(), languages());
        declarations.remove(0); declarations.remove(0);
        rejects(object.toString(), languages());
    }

    @Test
    void integerTokensHaveNoCoercionsAndRangesAreEnforced() throws Exception {
        for (String token : List.of("\"59\"", "59.0", "5.9e1", "059", "+59", "-59", "0", "2147483648", "9999999999999999999", "null", "true")) {
            rejects(header(), languages().replaceFirst("\"source_page\": 59", "\"source_page\": " + token));
        }
        for (String field : List.of("author_schema_version", "canonical_format_version", "archive_format_version")) {
            for (String token : List.of("\"1\"", "01", "1.0", "1e0", "0", "2147483648", "3")) {
                rejects(header().replaceFirst("\"" + field + "\": [12]", "\"" + field + "\": " + token), languages());
            }
        }
        for (int page : List.of(3, 74)) assertEquals(page, read(header(), changeNumber("source_page", page, 0)).partition().languages().getFirst().sourcePage());
        for (int page : List.of(2, 75)) rejects(header(), changeNumber("source_page", page, 0));
        for (int order : List.of(0, 19, 2)) rejects(header(), changeNumber("sort_order", order, 0));
    }

    @Test
    void strictUnicodeNormalizesOnlyAuthorTextAndCountsCodePoints() throws Exception {
        for (String field : List.of("display_name", "description")) {
            int max = field.equals("display_name") ? 120 : 1000;
            assertDoesNotThrow(() -> read(header(), change(field, "😀".repeat(max), 0)));
            rejects(header(), change(field, "😀".repeat(max + 1), 0));
            for (String value : List.of("", " ", "\u00a0", "\u0000", "\n", "\t", "\r", "\u007f", "\u0085")) rejects(header(), change(field, value, 0));
        }
        var composed = read(header(), change("display_name", "é", 0));
        var decomposed = read(header(), change("display_name", "e\u0301", 0));
        assertEquals(composed.partition(), decomposed.partition());
        assertNotEquals(composed.authorFiles().get(1).rawSha256(), decomposed.authorFiles().get(1).rawSha256());
        assertArrayEquals(canonical(composed), canonical(decomposed));
        for (String escaped : List.of("\\ud800", "\\udfff", "\\ud800x", "\\ud800\\ud800")) {
            rejects(header(), languages().replace("\"Abyssal\"", "\"" + escaped + "\""));
        }
        assertThrows(IllegalArgumentException.class, () -> new LanguagePartition.Language("language.abyssal", "e\u0301", "ok", LanguagePartition.Category.EXOTIC, 59, 1));
        assertThrows(IllegalArgumentException.class, () -> new LanguagePartition.Language("language.common", "ok", "ok", LanguagePartition.Category.SECRET, 59, 1));
    }

    @Test
    void packageTextAndWhitespaceChangeRawEvidenceButNotCanonicalProjection() throws Exception {
        var original = read(header(), languages());
        for (String value : List.of("é", "e\u0301", "😀".repeat(120))) {
            var object = JsonParser.parseString(header()).getAsJsonObject();
            object.addProperty("package_display_name", value);
            var changed = read(object.toString(), languages() + "\n\t ");
            assertArrayEquals(canonical(original), canonical(changed));
            assertNotEquals(original.authorFiles(), changed.authorFiles());
        }
        for (String value : List.of("😀".repeat(121), "", "\u00a0", "\n")) {
            var object = JsonParser.parseString(header()).getAsJsonObject();
            object.addProperty("package_display_name", value); rejects(object.toString(), languages());
        }
        for (var file : original.authorFiles()) {
            byte[] bytes = Files.readAllBytes(SOURCE.resolve(file.path()));
            assertEquals(bytes.length, file.byteLength());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), file.rawSha256());
        }
    }

    @Test
    void eachMutableContentFieldAffectsItsIndependentProjectionAndCanonicalBytes() throws Exception {
        byte[] original = canonical(read(header(), languages()));
        for (String field : List.of("display_name", "description")) {
            var result = read(header(), change(field, "Changed", 0));
            assertEquals("Changed", field.equals("display_name") ? result.partition().definitions().getFirst().displayName()
                    : result.partition().definitions().getFirst().description());
            assertFalse(Arrays.equals(original, canonical(result)));
        }
        var result = read(header(), changeNumber("source_page", 74, 0));
        assertEquals(74, ((LanguagePartition.SourcePageAttribute) result.partition().attributes().get(1)).value());
        assertFalse(Arrays.equals(original, canonical(result)));
    }

    @Test
    void rejectsMalformedJsonUtf8AndResourceExhaustionBeforePublishingResult() throws Exception {
        for (String json : List.of("", "\ufeff" + header(), header() + "{}", "/*comment*/" + header(),
                header().replaceFirst("\\{", "{\"module_key\":\"dnd5e2014_srd51_se\","),
                "[[[[[1]]]]]", "{unquoted:1}", "{'a':1}", "[1,]", "{\"a\":1,}")) rejects(json, languages());
        byte[] valid = languages().getBytes(StandardCharsets.UTF_8);
        for (byte[] bad : List.of(new byte[]{(byte)0xc0,(byte)0xaf}, new byte[]{(byte)0xed,(byte)0xa0,(byte)0x80}, new byte[]{(byte)0xe2,(byte)0x82}, new byte[]{(byte)0xff})) {
            assertThrows(IllegalArgumentException.class, () -> reader.read(bad, valid));
            assertThrows(IllegalArgumentException.class, () -> reader.read(header().getBytes(StandardCharsets.UTF_8), bad));
        }
        assertThrows(IllegalArgumentException.class, () -> reader.read(new byte[LanguageAuthorPackageReader.MAX_HEADER_BYTES + 1], valid));
        assertThrows(IllegalArgumentException.class, () -> reader.read(header().getBytes(StandardCharsets.UTF_8), new byte[LanguageAuthorPackageReader.MAX_LANGUAGE_BYTES + 1]));
        rejects(header(), change("description", "a".repeat(LanguageAuthorPackageReader.MAX_STRING_UNITS + 1), 0));
        assertThrows(IllegalArgumentException.class, () -> reader.read(null, valid));
        assertThrows(IllegalArgumentException.class, () -> new LanguagePartition(Collections.nCopies(18, null)));
    }

    @Test
    void exactByteBudgetsAllowWhitespaceAndBoundsRejectAdversarialShapesPromptly() throws Exception {
        byte[] header = header().getBytes(StandardCharsets.UTF_8);
        byte[] rows = languages().getBytes(StandardCharsets.UTF_8);
        byte[] paddedHeader = Arrays.copyOf(header, LanguageAuthorPackageReader.MAX_HEADER_BYTES);
        byte[] paddedRows = Arrays.copyOf(rows, LanguageAuthorPackageReader.MAX_LANGUAGE_BYTES);
        Arrays.fill(paddedHeader, header.length, paddedHeader.length, (byte) ' ');
        Arrays.fill(paddedRows, rows.length, paddedRows.length, (byte) '\n');
        assertEquals(LanguageAuthorPackageReader.MAX_TOTAL_BYTES, paddedHeader.length + paddedRows.length);
        assertEquals(read(header(), languages()).partition(), reader.read(paddedHeader, paddedRows).partition());
        String eighteenNumbers = "[" + String.join(",", Collections.nCopies(18, "1")) + "]";
        String nested = "[" + String.join(",", Collections.nCopies(18, eighteenNumbers)) + "]";
        String tokenBomb = "[" + String.join(",", Collections.nCopies(18, nested)) + "]";
        String cachedHeader = header();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            rejects(cachedHeader, tokenBomb);
            rejects(cachedHeader, "[".repeat(10000) + "1" + "]".repeat(10000));
            rejects(cachedHeader, "\"" + "a".repeat(LanguageAuthorPackageReader.MAX_STRING_UNITS + 1) + "\"");
        });
    }

    @Test
    void maximumLegalTextAndRawInputsFitTheBoundedPureReaderProfile() throws Exception {
        var head = JsonParser.parseString(header()).getAsJsonObject();
        head.addProperty("package_display_name", "😀".repeat(120));
        var input = JsonParser.parseString(languages()).getAsJsonArray();
        for (var value : input) {
            value.getAsJsonObject().addProperty("display_name", "😀".repeat(120));
            value.getAsJsonObject().addProperty("description", "😀".repeat(1000));
        }
        // Exercise escaped maximum-length supplementary text.
        String escaped = input.toString().replace("😀", "\\ud83d\\ude00");
        byte[] rawHeader = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] rawRows = escaped.getBytes(StandardCharsets.UTF_8);
        assertTrue(rawRows.length <= LanguageAuthorPackageReader.MAX_LANGUAGE_BYTES);
        byte[] paddedHeader = Arrays.copyOf(rawHeader, LanguageAuthorPackageReader.MAX_HEADER_BYTES);
        byte[] paddedRows = Arrays.copyOf(rawRows, LanguageAuthorPackageReader.MAX_LANGUAGE_BYTES);
        Arrays.fill(paddedHeader, rawHeader.length, paddedHeader.length, (byte)' ');
        Arrays.fill(paddedRows, rawRows.length, paddedRows.length, (byte)' ');
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            var parsed = reader.read(paddedHeader, paddedRows);
            assertEquals(18, parsed.partition().languages().size());
            assertEquals(1000, parsed.partition().languages().getFirst().description().codePointCount(0, 2000));
            assertEquals(36, parsed.partition().attributes().size());
        });
    }

    private String header() throws Exception { return Files.readString(SOURCE.resolve("author-package.json")); }
    private String languages() throws Exception { return Files.readString(SOURCE.resolve("character/languages.json")); }
    private LanguageAuthorPackageReader.Result read(String head, String rows) {
        return reader.read(head.getBytes(StandardCharsets.UTF_8), rows.getBytes(StandardCharsets.UTF_8));
    }
    private void rejects(String head, String rows) { assertThrows(IllegalArgumentException.class, () -> read(head, rows)); }
    private String change(String field, String value, int row) throws Exception {
        var array = JsonParser.parseString(languages()).getAsJsonArray();
        array.get(row).getAsJsonObject().addProperty(field, value); return array.toString();
    }
    private String changeNumber(String field, int value, int row) throws Exception {
        var array = JsonParser.parseString(languages()).getAsJsonArray();
        array.get(row).getAsJsonObject().addProperty(field, value); return array.toString();
    }

    /** Test-only adapter to the existing DRAFT encoder, never a production full-release factory. */
    private byte[] canonical(LanguageAuthorPackageReader.Result result) throws Exception {
        var definitions = result.partition().definitions().stream().map(row -> new ModuleCatalog.CatalogDefinition(
                row.definitionType(), row.definitionKey(), row.displayName(), row.description(), row.sortOrder())).toList();
        var attributes = result.partition().attributes().stream().map(row -> new ModuleCatalog.CatalogAttribute(
                row.definitionType(), row.definitionKey(), row.attributeKey(), row.attributeOrder(), row.valueType(),
                row instanceof LanguagePartition.CategoryAttribute category ? new ModuleCatalog.IdentifierValue(category.value().name())
                        : new ModuleCatalog.IntegerValue(((LanguagePartition.SourcePageAttribute) row).value()))).toList();
        var head = result.header();
        var release = new ModuleCatalog.Release(head.identity().moduleKey(), head.identity().releaseVersion(),
                head.canonicalFormatVersion(), head.hashAlgorithm(), null, "DRAFT");
        return new ModuleCanonicalEncoderV2().encode(new ModuleCatalog(release,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), definitions, attributes, List.of()));
    }
}
