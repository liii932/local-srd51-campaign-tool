package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
}
