package com.dndtool.module;

import java.util.ArrayList;
import java.util.List;

/** Parser and evaluator for frozen {@code advancement-value-profile-v1} ranges. */
public final class AdvancementValueProfile {
    private static final int MAXIMUM_PROFILE_LENGTH = 1000;
    private final List<Range> ranges;

    private AdvancementValueProfile(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    public static AdvancementValueProfile parse(String value) {
        if (value == null || value.isEmpty() || value.length() > MAXIMUM_PROFILE_LENGTH
                || !isAscii(value)) {
            throw invalid();
        }
        List<Range> ranges = new ArrayList<>();
        int previousMaximum = 0;
        for (String segment : value.split(",", -1)) {
            int colon = segment.indexOf(':');
            if (colon <= 0 || colon == segment.length() - 1
                    || segment.indexOf(':', colon + 1) >= 0) {
                throw invalid();
            }
            int[] bounds = bounds(segment.substring(0, colon));
            if (!ranges.isEmpty() && bounds[0] != previousMaximum + 1) throw invalid();
            Value expression = expression(segment.substring(colon + 1));
            ranges.add(new Range(bounds[0], bounds[1], expression));
            previousMaximum = bounds[1];
        }
        if (ranges.isEmpty() || previousMaximum != 20) throw invalid();
        return new AdvancementValueProfile(ranges);
    }

    public ResolvedValue atLevel(int classLevel, int charismaModifier) {
        if (classLevel < 1 || classLevel > 20) throw invalid();
        for (Range range : ranges) {
            if (classLevel >= range.minimumLevel() && classLevel <= range.maximumLevel()) {
                return range.value().resolve(classLevel, charismaModifier);
            }
        }
        return new ResolvedValue(0, false);
    }

    public int firstLevel() {
        return ranges.getFirst().minimumLevel();
    }

    public boolean constantsOnly() {
        return ranges.stream().allMatch(range -> range.value().kind() == Kind.CONSTANT);
    }

    private static int[] bounds(String value) {
        int dash = value.indexOf('-');
        int minimum = number(dash < 0 ? value : value.substring(0, dash));
        int maximum = number(dash < 0 ? value : value.substring(dash + 1));
        if (minimum < 1 || maximum > 20 || minimum > maximum) throw invalid();
        return new int[] {minimum, maximum};
    }

    private static int number(String value) {
        if (value.isEmpty() || value.length() > 2 || !value.chars().allMatch(Character::isDigit)) {
            throw invalid();
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static Value expression(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                long number = Long.parseLong(value);
                if (number < 1 || number > 1_000_000) throw invalid();
                return new Value(Kind.CONSTANT, number);
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }
        return switch (value) {
            case "CLASS_LEVEL" -> new Value(Kind.CLASS_LEVEL, 0);
            case "FIVE_TIMES_CLASS_LEVEL" -> new Value(Kind.FIVE_TIMES_CLASS_LEVEL, 0);
            case "CHARISMA_MODIFIER_MINIMUM_ONE" ->
                    new Value(Kind.CHARISMA_MODIFIER_MINIMUM_ONE, 0);
            case "ONE_PLUS_CHARISMA_MODIFIER_MINIMUM_ONE" ->
                    new Value(Kind.ONE_PLUS_CHARISMA_MODIFIER_MINIMUM_ONE, 0);
            case "UNLIMITED" -> new Value(Kind.UNLIMITED, 0);
            default -> throw invalid();
        };
    }

    private static boolean isAscii(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f || Character.isISOControl(character)) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid advancement value profile");
    }

    public record ResolvedValue(long maximum, boolean unlimited) {
        public ResolvedValue {
            if (maximum < 0 || unlimited && maximum != 0) throw invalid();
        }
    }

    private record Range(int minimumLevel, int maximumLevel, Value value) {
    }

    private record Value(Kind kind, long constant) {
        ResolvedValue resolve(int classLevel, int charismaModifier) {
            return switch (kind) {
                case CONSTANT -> new ResolvedValue(constant, false);
                case CLASS_LEVEL -> new ResolvedValue(classLevel, false);
                case FIVE_TIMES_CLASS_LEVEL -> new ResolvedValue(5L * classLevel, false);
                case CHARISMA_MODIFIER_MINIMUM_ONE ->
                        new ResolvedValue(Math.max(1, charismaModifier), false);
                case ONE_PLUS_CHARISMA_MODIFIER_MINIMUM_ONE ->
                        new ResolvedValue(Math.max(1, 1L + charismaModifier), false);
                case UNLIMITED -> new ResolvedValue(0, true);
            };
        }
    }

    private enum Kind {
        CONSTANT,
        CLASS_LEVEL,
        FIVE_TIMES_CLASS_LEVEL,
        CHARISMA_MODIFIER_MINIMUM_ONE,
        ONE_PLUS_CHARISMA_MODIFIER_MINIMUM_ONE,
        UNLIMITED
    }
}
