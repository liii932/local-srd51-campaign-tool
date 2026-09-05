package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleCanonicalEncoderV2Test {
    private static final String VECTOR_RESOURCE = "/module-canonical-v2-character.hex";
    private final ModuleCanonicalEncoderV2 encoder = new ModuleCanonicalEncoderV2();

    @Test
    void matchesIndependentCharacterCatalogGoldenVector() throws Exception {
        CatalogBuilder builder = new CatalogBuilder();
        builder.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.race", "race.elf", "Elfe\u0301", "Line one\r\nLine two", 1));
        builder.attributes.add(new ModuleCatalog.CatalogAttribute(
                "character.race", "race.elf", "source.page", 1,
                "INTEGER", new ModuleCatalog.IntegerValue(3)));

        assertArrayEquals(loadIndependentVector(), encoder.encode(builder.build()));
    }

    @Test
    void normalizesNfcLineEndingsAndIgnoresInputOrder() throws Exception {
        CatalogBuilder first = new CatalogBuilder();
        first.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.race", "race.elf", "Elfe\u0301", "A\r\nB\r", 1));
        first.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.race", "race.human", "Human", "Human", 2));
        first.attributes.add(attribute("race.human", 4));
        first.attributes.add(attribute("race.elf", 3));

        CatalogBuilder equivalent = new CatalogBuilder();
        equivalent.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.race", "race.human", "Human", "Human", 2));
        equivalent.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.race", "race.elf", "Elf\u00e9", "A\nB\n", 1));
        equivalent.attributes.add(attribute("race.elf", 3));
        equivalent.attributes.add(attribute("race.human", 4));

        assertArrayEquals(encoder.encode(first.build()), encoder.encode(equivalent.build()));

        equivalent.definitions.set(0, new ModuleCatalog.CatalogDefinition(
                "character.race", "race.human", "Human changed", "Human", 2));
        assertFalse(java.util.Arrays.equals(
                encoder.encode(first.build()), encoder.encode(equivalent.build())));
    }

    @Test
    void excludesReleaseStatusStoredDigestAndDatabaseMetadataFromDigestDomain() throws Exception {
        CatalogBuilder draft = new CatalogBuilder();
        draft.definitions.add(race("race.human", "Human", 1));
        draft.attributes.add(attribute("race.human", 4));

        CatalogBuilder released = new CatalogBuilder();
        released.release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", "a".repeat(64), "RELEASED");
        released.definitions.add(race("race.human", "Human", 1));
        released.attributes.add(attribute("race.human", 4));

        assertArrayEquals(encoder.encode(draft.build()), encoder.encode(released.build()));
    }

    @Test
    void acceptsCoreHitDiceWithoutInventingAClassOwnerOrMaximumProfile() {
        CatalogBuilder builder = new CatalogBuilder();
        builder.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.resource", "resource.hit_dice.d10",
                "d10 Hit Dice", "d10 Hit Dice", 1));
        builder.attributes.add(new ModuleCatalog.CatalogAttribute(
                "character.resource", "resource.hit_dice.d10", "source.page", 1,
                "INTEGER", new ModuleCatalog.IntegerValue(56)));
        builder.attributes.add(new ModuleCatalog.CatalogAttribute(
                "character.resource", "resource.hit_dice.d10", "catalog.category", 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue("CORE")));
        builder.attributes.add(new ModuleCatalog.CatalogAttribute(
                "character.resource", "resource.hit_dice.d10", "resource.recovery", 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue("LONG_REST")));

        assertDoesNotThrow(() -> encoder.encode(builder.build()));
    }

    @Test
    void validatesClosedClassFeatureAndResourceLifecycleAttributes() {
        CatalogBuilder builder = new CatalogBuilder();
        builder.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.class", "class.fighter", "Fighter", "Fighter", 1));
        builder.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.subclass", "subclass.champion", "Champion", "Champion", 1));
        builder.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.feature", "feature.fighter.second_wind",
                "Second Wind", "Second Wind", 1));
        builder.definitions.add(new ModuleCatalog.CatalogDefinition(
                "character.resource", "resource.fighter.second_wind",
                "Second Wind Uses", "Second Wind Uses", 1));
        builder.attributes.add(integer(
                "character.class", "class.fighter", "source.page", 24));
        builder.attributes.add(integer(
                "character.class", "class.fighter", "class.hit_die_sides", 10));
        builder.attributes.add(text("character.class", "class.fighter",
                "class.proficiency_bonus_profile",
                "1-4:2,5-8:3,9-12:4,13-16:5,17-20:6"));
        builder.attributes.add(text("character.class", "class.fighter",
                "class.multiclass_prerequisite",
                "ability.strength>=13|ability.dexterity>=13"));
        builder.attributes.add(text("character.class", "class.fighter",
                "class.multiclass_proficiency_profile",
                "grant=armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple"));
        builder.attributes.add(text("character.class", "class.fighter",
                "class.asi_levels", "4,6,8,12,14,16,19"));
        builder.attributes.add(text("character.class", "class.fighter",
                "class.starting_proficiency_profile",
                "armor.heavy,armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple"));
        builder.attributes.add(identifier("character.class", "class.fighter",
                "class.multiclass_spellcasting_progression", "NONE"));
        builder.attributes.add(integer(
                "character.subclass", "subclass.champion", "source.page", 25));
        builder.attributes.add(integer("character.subclass", "subclass.champion",
                "subclass.selection_level", 3));
        builder.attributes.add(integer("character.feature",
                "feature.fighter.second_wind", "source.page", 24));
        builder.attributes.add(integer("character.feature",
                "feature.fighter.second_wind", "feature.level", 1));
        builder.attributes.add(identifier("character.feature",
                "feature.fighter.second_wind", "feature.execution_mode", "AUTOMATIC"));
        builder.attributes.add(identifier("character.feature",
                "feature.fighter.second_wind", "feature.execution_algorithm",
                "AUTOMATIC_RESOURCE_LIFECYCLE_V1"));
        builder.attributes.add(integer("character.resource",
                "resource.fighter.second_wind", "source.page", 24));
        builder.attributes.add(identifier("character.resource",
                "resource.fighter.second_wind", "resource.recovery", "SHORT_REST"));
        builder.attributes.add(text("character.resource", "resource.fighter.second_wind",
                "resource.maximum_profile", "1-20:1"));
        builder.attributes.add(identifier("character.resource",
                "resource.fighter.second_wind", "resource.execution_mode", "AUTOMATIC"));
        builder.attributes.add(text("character.resource", "resource.fighter.second_wind",
                "resource.recovery_profile", "1-20:SHORT_REST"));
        builder.relations.add(new ModuleCatalog.CatalogRelation(
                "character.subclass", "subclass.champion", "subclass.parent_class",
                "character.class", "class.fighter", 1));
        builder.relations.add(new ModuleCatalog.CatalogRelation(
                "character.feature", "feature.fighter.second_wind", "feature.owner",
                "character.class", "class.fighter", 1));
        builder.relations.add(new ModuleCatalog.CatalogRelation(
                "character.resource", "resource.fighter.second_wind", "resource.owner",
                "character.class", "class.fighter", 1));

        assertDoesNotThrow(() -> encoder.encode(builder.build()));

        int progressionIndex = java.util.stream.IntStream.range(0, builder.attributes.size())
                .filter(index -> "class.multiclass_spellcasting_progression".equals(
                        builder.attributes.get(index).attributeKey()))
                .findFirst().orElseThrow();
        ModuleCatalog.CatalogAttribute progression = builder.attributes.get(progressionIndex);
        builder.attributes.set(progressionIndex, identifier("character.class", "class.fighter",
                "class.multiclass_spellcasting_progression", "CLIENT_DEFINED"));
        assertRejected(builder);
        builder.attributes.set(progressionIndex, progression);

        builder.attributes.removeIf(row -> "resource.recovery_profile".equals(
                row.attributeKey()));
        assertRejected(builder);
    }

    @Test
    void rejectsLegacyRowsMalformedUnicodeDuplicatesGapsAndDanglingRelations() {
        CatalogBuilder legacyRows = validRaceCatalog();
        legacyRows.ruleConstants.add(new ModuleCatalog.RuleConstant(
                "legacy.value", "INTEGER", new ModuleCatalog.IntegerValue(1)));
        assertRejected(legacyRows);

        CatalogBuilder malformedUnicode = validRaceCatalog();
        malformedUnicode.definitions.set(0, race("race.human", "\uD800", 1));
        assertRejected(malformedUnicode);

        CatalogBuilder control = validRaceCatalog();
        control.definitions.set(0, race("race.human", "Human\u0085", 1));
        assertRejected(control);

        CatalogBuilder duplicate = validRaceCatalog();
        duplicate.definitions.add(race("race.human", "Duplicate", 2));
        assertRejected(duplicate);

        CatalogBuilder orderingGap = validRaceCatalog();
        orderingGap.definitions.set(0, race("race.human", "Human", 2));
        assertRejected(orderingGap);

        CatalogBuilder badValueType = validRaceCatalog();
        badValueType.attributes.set(0, new ModuleCatalog.CatalogAttribute(
                "character.race", "race.human", "source.page", 1,
                "INTEGER", new ModuleCatalog.TextValue("4")));
        assertRejected(badValueType);

        CatalogBuilder wrongAttributeOwner = validRaceCatalog();
        wrongAttributeOwner.attributes.add(new ModuleCatalog.CatalogAttribute(
                "character.race", "race.human", "class.hit_die_sides", 1,
                "INTEGER", new ModuleCatalog.IntegerValue(8)));
        assertRejected(wrongAttributeOwner);

        CatalogBuilder unknownCategory = validRaceCatalog();
        unknownCategory.attributes.add(new ModuleCatalog.CatalogAttribute(
                "character.race", "race.human", "catalog.category", 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue("UNKNOWN")));
        assertRejected(unknownCategory);

        CatalogBuilder dangling = validRaceCatalog();
        dangling.relations.add(new ModuleCatalog.CatalogRelation(
                "character.subrace", "subrace.high_elf", "subrace.parent_race",
                "character.race", "race.missing", 1));
        assertRejected(dangling);

        CatalogBuilder wrongRelationShape = validRaceCatalog();
        wrongRelationShape.relations.add(new ModuleCatalog.CatalogRelation(
                "character.race", "race.human", "subrace.parent_race",
                "character.race", "race.human", 1));
        assertRejected(wrongRelationShape);
    }

    private void assertRejected(CatalogBuilder builder) {
        assertThrows(ModuleCanonicalException.class, () -> encoder.encode(builder.build()));
    }

    private static CatalogBuilder validRaceCatalog() {
        CatalogBuilder builder = new CatalogBuilder();
        builder.definitions.add(race("race.human", "Human", 1));
        builder.attributes.add(attribute("race.human", 4));
        return builder;
    }

    private static ModuleCatalog.CatalogDefinition race(String key, String name, int order) {
        return new ModuleCatalog.CatalogDefinition(
                "character.race", key, name, name, order);
    }

    private static ModuleCatalog.CatalogAttribute attribute(String key, int page) {
        return new ModuleCatalog.CatalogAttribute(
                "character.race", key, "source.page", 1,
                "INTEGER", new ModuleCatalog.IntegerValue(page));
    }

    private static ModuleCatalog.CatalogAttribute integer(
            String type, String key, String attribute, long value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1,
                "INTEGER", new ModuleCatalog.IntegerValue(value));
    }

    private static ModuleCatalog.CatalogAttribute text(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1,
                "TEXT", new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute identifier(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue(value));
    }

    private static byte[] loadIndependentVector() throws IOException {
        try (InputStream input = ModuleCanonicalEncoderV2Test.class
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
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", null, "DRAFT");
        private final List<ModuleCatalog.RuleConstant> ruleConstants = new ArrayList<>();
        private final List<ModuleCatalog.CatalogDefinition> definitions = new ArrayList<>();
        private final List<ModuleCatalog.CatalogAttribute> attributes = new ArrayList<>();
        private final List<ModuleCatalog.CatalogRelation> relations = new ArrayList<>();

        private ModuleCatalog build() {
            return new ModuleCatalog(
                    release,
                    ruleConstants,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(),
                    definitions, attributes, relations);
        }
    }
}
