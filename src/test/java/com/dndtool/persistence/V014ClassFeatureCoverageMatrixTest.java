package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Independent per-class audit of the V011 rows classified by V014's closed policy. */
final class V014ClassFeatureCoverageMatrixTest {
    private static final Path V011 = Path.of(
            "src/main/resources/db/migration/V011__complete_character_catalog_draft.sql");
    private static final Pattern SUBCLASS = Pattern.compile(
            "^\\('character\\.subclass', '([^']+)', '[^']*', '[^']*', '[^']*', NULL, "
                    + "'BASE', NULL, NULL, NULL, 'subclass\\.parent_class', "
                    + "'character\\.class', '([^']+)'\\)[,;]$");
    private static final Pattern FEATURE = Pattern.compile(
            "^\\('character\\.feature', '([^']+)', '[^']*', '[^']*', '[^']*', "
                    + "'([1-9]|1[0-9]|20)', '([^']+)', NULL, NULL, NULL, "
                    + "'feature\\.owner', '(character\\.class|character\\.subclass)', "
                    + "'([^']+)'\\)[,;]$");
    private static final Pattern SPELL = Pattern.compile(
            "(^|[.])(spellcasting|pact_magic|mystic_arcanum|spell_mastery|"
                    + "signature_spells|magical_secrets|additional_magical_secrets|"
                    + "bonus_cantrip|circle_spells|oath_spells|expanded_spell_list|"
                    + "arcane_recovery)($|[.])");
    private static final Pattern ADJUDICATED = Pattern.compile(
            "[.](primal_path|bard_college|divine_domain|druid_circle|"
                    + "martial_archetype|monastic_tradition|sacred_oath|ranger_archetype|"
                    + "roguish_archetype|sorcerous_origin|otherworldly_patron|"
                    + "arcane_tradition|expertise|fighting_style|favored_enemy|"
                    + "natural_explorer)$");
    private static final Set<String> AUTOMATIC = Set.of(
            "feature.barbarian.rage",
            "feature.bard.bardic_inspiration",
            "feature.bard.font_of_inspiration",
            "feature.cleric.channel_divinity",
            "feature.druid.wild_shape",
            "feature.fighter.second_wind",
            "feature.fighter.action_surge",
            "feature.fighter.indomitable",
            "feature.monk.ki",
            "feature.paladin.divine_sense",
            "feature.paladin.lay_on_hands",
            "feature.rogue.stroke_of_luck",
            "feature.sorcerer.font_of_magic");
    private static final Set<String> DIRECT_ADJUDICATION = Set.of(
            "feature.cleric.divine_intervention",
            "feature.ranger.primeval_awareness");

    @ParameterizedTest(name = "{0}: auto={1}, dm={2}, blocked={3}, total={4}")
    @MethodSource("expectedMatrix")
    void everyCatalogFeatureHasOneDisposition(
            String classKey, long automatic, long adjudicated, long blocked, long total)
            throws Exception {
        List<Row> rows = rows();
        List<Row> classRows = rows.stream()
                .filter(row -> classKey.equals(row.classKey())).toList();

        assertEquals(total, classRows.size());
        assertEquals(automatic, count(classRows, "AUTOMATIC"));
        assertEquals(adjudicated, count(classRows, "DM_ADJUDICATION"));
        assertEquals(blocked, count(classRows, "BLOCKED"));
        assertTrue(classRows.stream().allMatch(row -> row.level() >= 1 && row.level() <= 20));
    }

    private static Stream<Arguments> expectedMatrix() {
        return Stream.of(
                Arguments.of("class.barbarian", 1, 1, 16, 18),
                Arguments.of("class.bard", 2, 2, 11, 15),
                Arguments.of("class.cleric", 1, 2, 9, 12),
                Arguments.of("class.druid", 1, 1, 12, 14),
                Arguments.of("class.fighter", 3, 2, 7, 12),
                Arguments.of("class.monk", 1, 1, 21, 23),
                Arguments.of("class.paladin", 2, 2, 15, 19),
                Arguments.of("class.ranger", 0, 5, 23, 28),
                Arguments.of("class.rogue", 1, 2, 15, 18),
                Arguments.of("class.sorcerer", 1, 1, 17, 19),
                Arguments.of("class.warlock", 0, 4, 43, 47),
                Arguments.of("class.wizard", 0, 1, 10, 11));
    }

    private static List<Row> rows() throws Exception {
        List<String> lines = Files.readAllLines(V011, StandardCharsets.UTF_8);
        Map<String, String> subclassOwners = new HashMap<>();
        for (String line : lines) {
            Matcher matcher = SUBCLASS.matcher(line);
            if (matcher.matches()) subclassOwners.put(matcher.group(1), matcher.group(2));
        }
        assertEquals(12, subclassOwners.size());

        List<Row> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (String line : lines) {
            Matcher matcher = FEATURE.matcher(line);
            if (!matcher.matches()) continue;
            String key = matcher.group(1);
            int level = Integer.parseInt(matcher.group(2));
            String category = matcher.group(3);
            String ownerType = matcher.group(4);
            String ownerKey = matcher.group(5);
            String classKey = "character.class".equals(ownerType)
                    ? ownerKey : subclassOwners.get(ownerKey);
            assertTrue(classKey != null);
            assertTrue(keys.add(key));
            result.add(new Row(key, classKey, level, disposition(key, category)));
        }
        assertEquals(236, result.size());
        assertEquals(13, count(result, "AUTOMATIC"));
        assertEquals(24, count(result, "DM_ADJUDICATION"));
        assertEquals(199, count(result, "BLOCKED"));
        return List.copyOf(result);
    }

    private static String disposition(String key, String category) {
        if (key.endsWith(".ability_score_improvement")) return "BLOCKED";
        if (SPELL.matcher(key).find() || key.startsWith("feature.warlock.invocation.")
                || key.startsWith("feature.sorcerer.metamagic.")) return "BLOCKED";
        if ("OPTION".equals(category) || ADJUDICATED.matcher(key).find()
                || DIRECT_ADJUDICATION.contains(key)) return "DM_ADJUDICATION";
        return AUTOMATIC.contains(key) ? "AUTOMATIC" : "BLOCKED";
    }

    private static long count(List<Row> rows, String disposition) {
        return rows.stream().filter(row -> disposition.equals(row.disposition())).count();
    }

    private record Row(String key, String classKey, int level, String disposition) {
    }
}
