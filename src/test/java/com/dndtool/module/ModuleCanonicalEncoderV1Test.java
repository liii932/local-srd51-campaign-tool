package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.ModuleCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleCanonicalEncoderV1Test {
    private static final String VECTOR_RESOURCE =
            "/module-canonical-v1-empty-sections.hex";
    private final ModuleCanonicalEncoderV1 encoder = new ModuleCanonicalEncoderV1();

    @Test
    void matchesIndependentContainerVectorIncludingEveryEmptyPartition() throws Exception {
        byte[] expected = loadIndependentVector();
        byte[] actual = encoder.encode(new CatalogBuilder().build());

        assertArrayEquals(expected, actual);
    }

    @Test
    void normalizesNfcLineEndingsExactDecimalsAndInputOrder() throws Exception {
        CatalogBuilder first = new CatalogBuilder();
        first.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "z.text", "TEXT", new ModuleCatalog.TextValue("Cafe\u0301\r\n尾\r")));
        first.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "a.decimal", "DECIMAL", new ModuleCatalog.DecimalValue(
                        new BigDecimal("0.50"))));
        first.bonusBands.add(new ModuleCatalog.ProficiencyBonusBand(9, 12, 4));
        first.bonusBands.add(new ModuleCatalog.ProficiencyBonusBand(0, 4, 2));

        CatalogBuilder equivalent = new CatalogBuilder();
        equivalent.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "a.decimal", "DECIMAL", new ModuleCatalog.DecimalValue(
                        new BigDecimal("5E-1"))));
        equivalent.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "z.text", "TEXT", new ModuleCatalog.TextValue("Café\n尾\n")));
        equivalent.bonusBands.add(new ModuleCatalog.ProficiencyBonusBand(0, 4, 2));
        equivalent.bonusBands.add(new ModuleCatalog.ProficiencyBonusBand(9, 12, 4));

        assertArrayEquals(encoder.encode(first.build()), encoder.encode(equivalent.build()));

        equivalent.ruleConstants.set(0, new ModuleCatalog.RuleConstant(
                "a.decimal", "DECIMAL", new ModuleCatalog.DecimalValue(
                        new BigDecimal("0.6"))));
        assertFalse(java.util.Arrays.equals(
                encoder.encode(first.build()), encoder.encode(equivalent.build())));
    }

    @Test
    void emitsTheSpecifiedScalarTagsLengthsPayloadsAndExplicitNulls() throws Exception {
        CatalogBuilder builder = new CatalogBuilder();
        builder.ruleConstants.addAll(List.of(
                new ModuleCatalog.RuleConstant(
                        "a.text", "TEXT", new ModuleCatalog.TextValue("狼")),
                new ModuleCatalog.RuleConstant(
                        "b.identifier", "IDENTIFIER",
                        new ModuleCatalog.IdentifierValue("NORMAL")),
                new ModuleCatalog.RuleConstant(
                        "c.integer", "INTEGER", new ModuleCatalog.IntegerValue(-99)),
                new ModuleCatalog.RuleConstant(
                        "d.decimal", "DECIMAL",
                        new ModuleCatalog.DecimalValue(new BigDecimal("0.50"))),
                new ModuleCatalog.RuleConstant(
                        "e.boolean", "BOOLEAN", new ModuleCatalog.BooleanValue(true))));
        builder.fields.add(new ModuleCatalog.FieldDefinition(
                "field.sample", "示例", "INTEGER", new ModuleCatalog.IntegerValue(0),
                null, null, null, null, "说明"));

        byte[] encoded = encoder.encode(builder.build());

        assertContains(encoded, HexFormat.of().parseHex("0100000003e78bbc"));
        assertContains(encoded, HexFormat.of().parseHex("02000000064e4f524d414c"));
        assertContains(encoded, HexFormat.of().parseHex("03000000032d3939"));
        assertContains(encoded, HexFormat.of().parseHex("0400000003302e35"));
        assertContains(encoded, HexFormat.of().parseHex("050000000101"));
        assertContains(encoded, namedNull("minimum_value"));
        assertContains(encoded, namedNull("maximum_value"));
        assertContains(encoded, namedNull("dependent_max_field_key"));
        assertContains(encoded, namedNull("unit"));
    }

    @Test
    void comparesNormalizedUtf8AsUnsignedBytesRatherThanUtf16OrLocaleText() {
        assertTrue(ModuleCanonicalEncoderV1.compareCanonicalUtf8("e\u0301", "é") == 0);
        // UTF-8 orders U+E000 (EE...) before U+10000 (F0...), unlike UTF-16 code units.
        assertTrue(ModuleCanonicalEncoderV1.compareCanonicalUtf8("\uE000", "\uD800\uDC00") < 0);
        assertTrue(ModuleCanonicalEncoderV1.compareCanonicalUtf8("abc", "abcd") < 0);
    }

    @Test
    void rejectsMalformedUnknownDuplicateMismatchedAndDanglingDefinitions() {
        CatalogBuilder wrongVersion = new CatalogBuilder();
        wrongVersion.release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se_v1", "1", 2, "SHA-256", null, "DRAFT");
        assertRejected(wrongVersion);

        CatalogBuilder malformedUnicode = new CatalogBuilder();
        malformedUnicode.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "bad.text", "TEXT", new ModuleCatalog.TextValue("\uD800")));
        assertRejected(malformedUnicode);

        CatalogBuilder duplicate = new CatalogBuilder();
        duplicate.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "same.key", "INTEGER", new ModuleCatalog.IntegerValue(1)));
        duplicate.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "same.key", "INTEGER", new ModuleCatalog.IntegerValue(2)));
        assertRejected(duplicate);

        CatalogBuilder invalidStableKey = new CatalogBuilder();
        invalidStableKey.fields.add(new ModuleCatalog.FieldDefinition(
                "Ability.Strength", "力量", "INTEGER", new ModuleCatalog.IntegerValue(10),
                new ModuleCatalog.IntegerValue(1), new ModuleCatalog.IntegerValue(30),
                null, null, "力量"));
        assertRejected(invalidStableKey);

        CatalogBuilder mismatchedType = new CatalogBuilder();
        mismatchedType.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "bad.type", "INTEGER", new ModuleCatalog.DecimalValue(BigDecimal.ONE)));
        assertRejected(mismatchedType);

        CatalogBuilder nonAsciiIdentifier = new CatalogBuilder();
        nonAsciiIdentifier.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "bad.identifier", "IDENTIFIER", new ModuleCatalog.IdentifierValue("é")));
        assertRejected(nonAsciiIdentifier);

        CatalogBuilder danglingSkill = new CatalogBuilder();
        danglingSkill.skills.add(new ModuleCatalog.SkillDefinition(
                "skill.test", "测试", "ability.missing"));
        assertRejected(danglingSkill);

        CatalogBuilder unknownAlgorithm = new CatalogBuilder();
        unknownAlgorithm.rollModes.add(new ModuleCatalog.RollMode(
                "roll.test", "TEST", 1, "UNKNOWN_V1"));
        assertRejected(unknownAlgorithm);

        CatalogBuilder wrongCandidateCount = new CatalogBuilder();
        wrongCandidateCount.rollModes.add(new ModuleCatalog.RollMode(
                "roll.test", "NORMAL", 2, "ONLY_CANDIDATE_V1"));
        assertRejected(wrongCandidateCount);

        CatalogBuilder outOfRangeDefault = new CatalogBuilder();
        outOfRangeDefault.fields.add(new ModuleCatalog.FieldDefinition(
                "field.test", "测试", "INTEGER", new ModuleCatalog.IntegerValue(11),
                new ModuleCatalog.IntegerValue(1), new ModuleCatalog.IntegerValue(10),
                null, null, "测试字段"));
        assertRejected(outOfRangeDefault);

        CatalogBuilder overlappingBands = new CatalogBuilder();
        overlappingBands.bonusBands.add(new ModuleCatalog.ProficiencyBonusBand(0, 4, 2));
        overlappingBands.bonusBands.add(new ModuleCatalog.ProficiencyBonusBand(4, 8, 3));
        assertRejected(overlappingBands);

        CatalogBuilder invalidBandRange = new CatalogBuilder();
        invalidBandRange.bonusBands.add(new ModuleCatalog.ProficiencyBonusBand(-1, 4, 2));
        assertRejected(invalidBandRange);

        CatalogBuilder invalidClassLevel = new CatalogBuilder();
        invalidClassLevel.classes.add(new ModuleCatalog.ClassDefinition(
                "class.fighter", "战士"));
        invalidClassLevel.templates.add(new ModuleCatalog.EntityTemplate(
                "npc.guard", "守卫"));
        invalidClassLevel.classLevels.add(new ModuleCatalog.EntityTemplateClassLevel(
                "npc.guard", "class.fighter", 21));
        assertRejected(invalidClassLevel);

        CatalogBuilder reversedConnection = new CatalogBuilder();
        reversedConnection.maps.add(new ModuleCatalog.MapDefinition("map.test", "NODE"));
        reversedConnection.mapNodes.add(new ModuleCatalog.MapNode(
                "map.test", "node.a", "A"));
        reversedConnection.mapNodes.add(new ModuleCatalog.MapNode(
                "map.test", "node.b", "B"));
        reversedConnection.mapConnections.add(new ModuleCatalog.MapConnection(
                "map.test", "node.b", "node.a"));
        assertRejected(reversedConnection);
    }

    private void assertRejected(CatalogBuilder builder) {
        assertThrows(ModuleCanonicalException.class, () -> encoder.encode(builder.build()));
    }

    private static byte[] namedNull(String fieldName) {
        byte[] name = fieldName.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[4 + name.length + 1];
        result[3] = (byte) name.length;
        System.arraycopy(name, 0, result, 4, name.length);
        // The final byte is the NULL type tag 0x00.
        return result;
    }

    private static void assertContains(byte[] value, byte[] expectedSubsequence) {
        outer:
        for (int start = 0; start <= value.length - expectedSubsequence.length; start++) {
            for (int offset = 0; offset < expectedSubsequence.length; offset++) {
                if (value[start + offset] != expectedSubsequence[offset]) {
                    continue outer;
                }
            }
            return;
        }
        throw new AssertionError("Expected canonical byte subsequence is absent");
    }

    private static byte[] loadIndependentVector() throws IOException {
        try (InputStream input = ModuleCanonicalEncoderV1Test.class
                .getResourceAsStream(VECTOR_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing canonical test vector");
            }
            String hex = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replaceAll("\\s", "");
            return HexFormat.of().parseHex(hex);
        }
    }

    /** Mutable test fixture; production inputs remain immutable ModuleCatalog records. */
    private static final class CatalogBuilder {
        private ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", null, "DRAFT");
        private final List<ModuleCatalog.RuleConstant> ruleConstants = new ArrayList<>();
        private final List<ModuleCatalog.FieldDefinition> fields = new ArrayList<>();
        private final List<ModuleCatalog.ClassDefinition> classes = new ArrayList<>();
        private final List<ModuleCatalog.ProficiencyTier> tiers = new ArrayList<>();
        private final List<ModuleCatalog.ProficiencyBonusBand> bonusBands = new ArrayList<>();
        private final List<ModuleCatalog.SkillDefinition> skills = new ArrayList<>();
        private final List<ModuleCatalog.SaveDefinition> saves = new ArrayList<>();
        private final List<ModuleCatalog.ItemTemplate> items = new ArrayList<>();
        private final List<ModuleCatalog.EntityTemplate> templates = new ArrayList<>();
        private final List<ModuleCatalog.EntityTemplateValue> templateValues = new ArrayList<>();
        private final List<ModuleCatalog.EntityTemplateClassLevel> classLevels = new ArrayList<>();
        private final List<ModuleCatalog.EntityTemplateProficiency> proficiencies =
                new ArrayList<>();
        private final List<ModuleCatalog.CheckDefinition> checks = new ArrayList<>();
        private final List<ModuleCatalog.RollMode> rollModes = new ArrayList<>();
        private final List<ModuleCatalog.EventTemplate> events = new ArrayList<>();
        private final List<ModuleCatalog.EventCheck> eventChecks = new ArrayList<>();
        private final List<ModuleCatalog.EventEffect> eventEffects = new ArrayList<>();
        private final List<ModuleCatalog.EffectDefinition> effects = new ArrayList<>();
        private final List<ModuleCatalog.EffectParameter> parameters = new ArrayList<>();
        private final List<ModuleCatalog.MapDefinition> maps = new ArrayList<>();
        private final List<ModuleCatalog.MapNode> mapNodes = new ArrayList<>();
        private final List<ModuleCatalog.MapConnection> mapConnections = new ArrayList<>();

        private ModuleCatalog build() {
            return new ModuleCatalog(
                    release,
                    ruleConstants,
                    fields,
                    classes,
                    tiers,
                    bonusBands,
                    skills,
                    saves,
                    items,
                    templates,
                    templateValues,
                    classLevels,
                    proficiencies,
                    checks,
                    rollModes,
                    events,
                    eventChecks,
                    eventEffects,
                    effects,
                    parameters,
                    maps,
                    mapNodes,
                    mapConnections);
        }
    }
}
