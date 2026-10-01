package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdvancementValueProfileTest {
    @Test
    void resolvesFrozenConstantsFormulasAndUnlimitedValues() {
        AdvancementValueProfile profile = AdvancementValueProfile.parse(
                "1-2:2,3-5:CLASS_LEVEL,6:CHARISMA_MODIFIER_MINIMUM_ONE,"
                        + "7:ONE_PLUS_CHARISMA_MODIFIER_MINIMUM_ONE,"
                        + "8:FIVE_TIMES_CLASS_LEVEL,9-20:UNLIMITED");

        assertEquals(2, profile.atLevel(1, 3).maximum());
        assertEquals(4, profile.atLevel(4, 3).maximum());
        assertEquals(3, profile.atLevel(6, 3).maximum());
        assertEquals(4, profile.atLevel(7, 3).maximum());
        assertEquals(40, profile.atLevel(8, 3).maximum());
        assertTrue(profile.atLevel(20, 3).unlimited());
    }

    @Test
    void returnsUnavailableBeforeTheFirstFrozenRange() {
        AdvancementValueProfile profile = AdvancementValueProfile.parse("9-20:1");

        assertEquals(0, profile.atLevel(8, 0).maximum());
        assertEquals(1, profile.atLevel(9, 0).maximum());
    }

    @Test
    void rejectsGapsOverlapsDescendingRangesAndUnknownAlgorithms() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementValueProfile.parse("1-2:1,4-20:2"));
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementValueProfile.parse("1-3:1,3-20:2"));
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementValueProfile.parse("2-1:1"));
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementValueProfile.parse("1-20:CLIENT_ROLL"));
    }

    @Test
    void typedRangesAreValidatedCopiedAndResolveLikeText() {
        var ranges = new ArrayList<>(List.of(
                new AdvancementValueProfile.Range(3, 19,
                        new AdvancementValueProfile.Value(AdvancementValueProfile.Kind.CLASS_LEVEL, 0)),
                new AdvancementValueProfile.Range(20, 20,
                        new AdvancementValueProfile.Value(AdvancementValueProfile.Kind.UNLIMITED, 0))));
        var profile = AdvancementValueProfile.ofRanges(ranges);
        ranges.clear();
        var parsed = AdvancementValueProfile.parse("3-19:CLASS_LEVEL,20:UNLIMITED");
        assertEquals(parsed.ranges(), profile.ranges());
        for (int level = 1; level <= 20; level++) {
            assertEquals(parsed.atLevel(level, 2), profile.atLevel(level, 2));
        }
        assertThrows(UnsupportedOperationException.class, () -> profile.ranges().clear());
        assertThrows(IllegalArgumentException.class, () -> AdvancementValueProfile.ofRanges(null));
        assertThrows(IllegalArgumentException.class, () -> AdvancementValueProfile.ofRanges(List.of()));
        assertThrows(IllegalArgumentException.class, () -> AdvancementValueProfile.ofRanges(
                Arrays.asList((AdvancementValueProfile.Range) null)));
        var value = new AdvancementValueProfile.Value(AdvancementValueProfile.Kind.CONSTANT, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new AdvancementValueProfile.Range(0, 20, value));
        assertThrows(IllegalArgumentException.class,
                () -> new AdvancementValueProfile.Range(1, 21, value));
        assertThrows(IllegalArgumentException.class,
                () -> new AdvancementValueProfile.Range(5, 4, value));
        assertThrows(IllegalArgumentException.class,
                () -> new AdvancementValueProfile.Range(1, 20, null));
        assertThrows(IllegalArgumentException.class, () -> AdvancementValueProfile.ofRanges(List.of(
                new AdvancementValueProfile.Range(1, 19, value))));
        for (int next : new int[] {4, 6}) {
            assertThrows(IllegalArgumentException.class, () -> AdvancementValueProfile.ofRanges(List.of(
                    new AdvancementValueProfile.Range(1, 4, value),
                    new AdvancementValueProfile.Range(next, 20, value))));
        }
    }

    @Test
    void typedExpressionsRejectUnusedConstantsAndInvalidMagnitude() {
        assertThrows(IllegalArgumentException.class, () -> new AdvancementValueProfile.Value(null, 0));
        for (long invalid : new long[] {-1, 0, 1_000_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new AdvancementValueProfile.Value(
                    AdvancementValueProfile.Kind.CONSTANT, invalid));
        }
        for (var kind : AdvancementValueProfile.Kind.values()) {
            if (kind != AdvancementValueProfile.Kind.CONSTANT) {
                assertThrows(IllegalArgumentException.class,
                        () -> new AdvancementValueProfile.Value(kind, 1));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
    void proficiencyRejectsACorruptionAtEveryIndividualLevel(int corruptedLevel) {
        StringBuilder text = new StringBuilder();
        for (int level = 1; level <= 20; level++) {
            if (level > 1) text.append(',');
            text.append(level).append(':').append(level == corruptedLevel ? 7 : 2 + (level - 1) / 4);
        }
        assertThrows(IllegalArgumentException.class,
                () -> AdvancementValueProfile.parseProficiencyBonus(text.toString()));
    }

    @Test
    void proficiencyAllowsEquivalentSegmentationButRequiresConstantsAndFullCoverage() {
        var profile = AdvancementValueProfile.parseProficiencyBonus(
                "1:2,2-4:2,5-8:3,9-12:4,13-16:5,17-20:6");
        for (int level = 1; level <= 20; level++) {
            assertEquals(2 + (level - 1) / 4, profile.atLevel(level, 0).maximum());
        }
        for (String invalid : List.of("2-4:2,5-8:3,9-12:4,13-16:5,17-20:6",
                "1-20:CLASS_LEVEL", "1-20:UNLIMITED")) {
            assertThrows(IllegalArgumentException.class,
                    () -> AdvancementValueProfile.parseProficiencyBonus(invalid));
        }
    }
}
