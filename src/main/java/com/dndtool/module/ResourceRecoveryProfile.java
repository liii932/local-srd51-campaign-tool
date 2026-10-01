package com.dndtool.module;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Immutable, fully validated class-level ranges for short/long-rest recovery. */
public final class ResourceRecoveryProfile {
    private static final int MAXIMUM_PROFILE_LENGTH = 200;
    private static final Pattern BAND = Pattern.compile(
            "([1-9]|1[0-9]|20)-([1-9]|1[0-9]|20):(SHORT_REST|LONG_REST)");
    private final List<Range> ranges;

    private ResourceRecoveryProfile(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    public static ResourceRecoveryProfile parse(String text) {
        if (text == null || text.isEmpty() || text.length() > MAXIMUM_PROFILE_LENGTH) {
            throw invalid();
        }
        List<Range> ranges = new ArrayList<>();
        for (String band : text.split(",", -1)) {
            var matcher = BAND.matcher(band);
            if (!matcher.matches()) throw invalid();
            ranges.add(new Range(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), Rest.valueOf(matcher.group(3))));
        }
        return ofRanges(ranges);
    }

    public static ResourceRecoveryProfile ofRanges(List<Range> ranges) {
        if (ranges == null || ranges.isEmpty() || ranges.size() > 20) throw invalid();
        int expectedLevel = 1;
        for (Range range : ranges) {
            if (range == null || range.minimumLevel() != expectedLevel) throw invalid();
            expectedLevel = range.maximumLevel() + 1;
        }
        if (expectedLevel != 21) throw invalid();
        return new ResourceRecoveryProfile(ranges);
    }

    public List<Range> ranges() {
        return ranges;
    }

    public Rest atLevel(int classLevel) {
        if (classLevel < 1 || classLevel > 20) throw invalid();
        for (Range range : ranges) {
            if (classLevel >= range.minimumLevel() && classLevel <= range.maximumLevel()) {
                return range.rest();
            }
        }
        throw new IllegalStateException("Validated recovery profile has no matching range");
    }

    public record Range(int minimumLevel, int maximumLevel, Rest rest) {
        public Range {
            if (minimumLevel < 1 || maximumLevel > 20 || minimumLevel > maximumLevel
                    || rest == null) throw invalid();
        }
    }

    public enum Rest {
        SHORT_REST,
        LONG_REST;

        public boolean recoversOn(Rest completedRest) {
            if (completedRest == null) throw invalid();
            return this == completedRest || this == SHORT_REST && completedRest == LONG_REST;
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid resource recovery profile");
    }
}
