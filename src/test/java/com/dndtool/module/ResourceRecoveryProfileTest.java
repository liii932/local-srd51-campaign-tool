package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceRecoveryProfileTest {
    @Test
    void resolvesEveryLevelAndTheCompleteRestMatrix() {
        ResourceRecoveryProfile profile = ResourceRecoveryProfile.parse(
                "1-4:LONG_REST,5-20:SHORT_REST");
        for (int level = 1; level <= 20; level++) {
            assertEquals(level < 5 ? ResourceRecoveryProfile.Rest.LONG_REST
                    : ResourceRecoveryProfile.Rest.SHORT_REST, profile.atLevel(level));
            assertEquals(level >= 5,
                    profile.atLevel(level).recoversOn(ResourceRecoveryProfile.Rest.SHORT_REST));
            assertTrue(profile.atLevel(level).recoversOn(ResourceRecoveryProfile.Rest.LONG_REST));
        }
        assertFalse(ResourceRecoveryProfile.Rest.LONG_REST.recoversOn(
                ResourceRecoveryProfile.Rest.SHORT_REST));
    }

    @Test
    void structuredRangesUseTheSameValidationAndAreImmutable() {
        var ranges = new ArrayList<>(List.of(
                new ResourceRecoveryProfile.Range(1, 4, ResourceRecoveryProfile.Rest.LONG_REST),
                new ResourceRecoveryProfile.Range(5, 20, ResourceRecoveryProfile.Rest.SHORT_REST)));
        var profile = ResourceRecoveryProfile.ofRanges(ranges);
        ranges.clear();
        assertEquals(ResourceRecoveryProfile.parse("1-4:LONG_REST,5-20:SHORT_REST").ranges(),
                profile.ranges());
        assertThrows(UnsupportedOperationException.class, () -> profile.ranges().clear());
        assertThrows(IllegalArgumentException.class, () -> ResourceRecoveryProfile.ofRanges(null));
        assertThrows(IllegalArgumentException.class, () -> ResourceRecoveryProfile.ofRanges(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ResourceRecoveryProfile.ofRanges(
                Arrays.asList((ResourceRecoveryProfile.Range) null)));
        assertThrows(IllegalArgumentException.class, () -> ResourceRecoveryProfile.ofRanges(List.of(
                new ResourceRecoveryProfile.Range(2, 20, ResourceRecoveryProfile.Rest.LONG_REST))));
        assertThrows(IllegalArgumentException.class, () -> ResourceRecoveryProfile.ofRanges(List.of(
                new ResourceRecoveryProfile.Range(1, 19, ResourceRecoveryProfile.Rest.LONG_REST))));
        for (int next : new int[] {4, 6}) {
            assertThrows(IllegalArgumentException.class, () -> ResourceRecoveryProfile.ofRanges(List.of(
                    new ResourceRecoveryProfile.Range(1, 4, ResourceRecoveryProfile.Rest.LONG_REST),
                    new ResourceRecoveryProfile.Range(next, 20, ResourceRecoveryProfile.Rest.SHORT_REST))));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1-19:SHORT_REST", "2-20:LONG_REST", "0-20:SHORT_REST",
            "1-21:SHORT_REST", "1-5:LONG_REST,5-20:SHORT_REST", "1-4:LONG_REST,6-20:SHORT_REST",
            "5-20:SHORT_REST,1-4:LONG_REST", "1-4:LONG_REST,5-20:CLIENT_REST",
            "1-4:LONG_REST,20-5:SHORT_REST", "1:LONG_REST,2-20:SHORT_REST",
            "01-20:SHORT_REST", "1-20:short_rest", "1-20:SHORT_REST,", "1-20:SHORT_REST\n",
            "1-20:SHORT_REST\u0085", "１-20:SHORT_REST", "1-20:SHORT_REST\uD800"})
    void rejectsMalformedInputIncludingInvalidLaterBands(String text) {
        assertThrows(IllegalArgumentException.class, () -> ResourceRecoveryProfile.parse(text));
    }

    @Test
    void rejectsInvalidTypedFieldsAndLookupBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRecoveryProfile.Range(0, 20, ResourceRecoveryProfile.Rest.LONG_REST));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRecoveryProfile.Range(1, 21, ResourceRecoveryProfile.Rest.LONG_REST));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRecoveryProfile.Range(5, 4, ResourceRecoveryProfile.Rest.LONG_REST));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceRecoveryProfile.Range(1, 20, null));
        var profile = ResourceRecoveryProfile.parse("1-20:SHORT_REST");
        assertThrows(IllegalArgumentException.class, () -> profile.atLevel(0));
        assertThrows(IllegalArgumentException.class, () -> profile.atLevel(21));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceRecoveryProfile.Rest.SHORT_REST.recoversOn(null));
        assertThrows(IllegalArgumentException.class,
                () -> ResourceRecoveryProfile.parse("1-20:SHORT_REST".repeat(20)));
    }
}
