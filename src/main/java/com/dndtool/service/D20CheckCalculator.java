package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Performs the host command server-side {@code 1d20 +/- modifier} calculation.
 *
 * <p>The caller supplies only a frozen roll-mode key, a server-derived modifier and a DC. Dice
 * candidates and the selected result always come from the injected server source; this class does
 * not accept client-provided rolls or implement general dice expressions, combat resolution,
 * advanced dice mechanics, or natural-1/natural-20 special outcomes.
 */
public final class D20CheckCalculator {
    private static final int MINIMUM_D20 = 1;
    private static final int MAXIMUM_D20 = 20;
    private static final int MINIMUM_MODIFIER = -99;
    private static final int MAXIMUM_MODIFIER = 99;
    private static final int MINIMUM_DC = 0;
    private static final int MAXIMUM_DC = 60;

    private static final String NORMAL_KEY = "roll.normal";
    private static final String ADVANTAGE_KEY = "roll.advantage";
    private static final String DISADVANTAGE_KEY = "roll.disadvantage";

    private final Map<String, ModuleCatalog.RollMode> rollModes;
    private final D20Source d20Source;

    /** Uses a process-local random source for normal server execution. */
    public D20CheckCalculator(ModuleCatalog catalog) {
        this(catalog, () -> ThreadLocalRandom.current().nextInt(MINIMUM_D20, MAXIMUM_D20 + 1));
    }

    /**
     * Accepts an explicit source so tests can prove candidate order and tie selection exactly.
     */
    public D20CheckCalculator(ModuleCatalog catalog, D20Source d20Source) {
        Objects.requireNonNull(catalog, "catalog");
        this.d20Source = Objects.requireNonNull(d20Source, "d20Source");
        this.rollModes = validateAndIndexRollModes(catalog.rollModes());
    }

    /**
     * Rolls all module-declared candidates, selects one, and compares its modified total to the DC.
     * Input validation completes before the first random value is consumed.
     */
    public Result calculate(String rollModeKey, int modifierValue, int difficultyClass) {
        ModuleCatalog.RollMode rollMode = requireRollMode(rollModeKey);
        requireBetween(
                modifierValue,
                MINIMUM_MODIFIER,
                MAXIMUM_MODIFIER,
                "Modifier must be between -99 and 99");
        requireBetween(
                difficultyClass,
                MINIMUM_DC,
                MAXIMUM_DC,
                "Difficulty class must be between 0 and 60");

        List<Integer> rolledValues = new ArrayList<>(rollMode.candidateCount());
        for (int index = 0; index < rollMode.candidateCount(); index++) {
            int rolledValue = d20Source.roll();
            if (rolledValue < MINIMUM_D20 || rolledValue > MAXIMUM_D20) {
                throw new IllegalStateException("D20 source returned a value outside 1 through 20");
            }
            rolledValues.add(rolledValue);
        }

        int selectedIndex = selectCandidate(rollMode.selectionAlgorithm(), rolledValues);
        int selectedValue = rolledValues.get(selectedIndex);
        int totalValue = Math.addExact(selectedValue, modifierValue);
        Outcome outcome = totalValue >= difficultyClass ? Outcome.SUCCESS : Outcome.FAILURE;

        List<Candidate> candidates = new ArrayList<>(rolledValues.size());
        for (int index = 0; index < rolledValues.size(); index++) {
            // Candidate order is one-based to match dice_roll.candidate_order.
            candidates.add(new Candidate(index + 1, rolledValues.get(index), index == selectedIndex));
        }
        return new Result(
                rollModeKey,
                candidates,
                selectedValue,
                modifierValue,
                totalValue,
                difficultyClass,
                outcome);
    }

    private ModuleCatalog.RollMode requireRollMode(String rollModeKey) {
        if (rollModeKey == null || rollModeKey.isBlank()) {
            throw new IllegalArgumentException("Roll mode is required");
        }
        ModuleCatalog.RollMode rollMode = rollModes.get(rollModeKey);
        if (rollMode == null) {
            throw new IllegalArgumentException("Unsupported roll mode");
        }
        return rollMode;
    }

    private static int selectCandidate(String algorithm, List<Integer> rolledValues) {
        return switch (algorithm) {
            case "ONLY_CANDIDATE_V1" -> 0;
            case "HIGHEST_FIRST_ON_TIE_V1" -> selectExtreme(rolledValues, true);
            case "LOWEST_FIRST_ON_TIE_V1" -> selectExtreme(rolledValues, false);
            default -> throw invalidRollModeCatalog();
        };
    }

    private static int selectExtreme(List<Integer> rolledValues, boolean selectHighest) {
        int selectedIndex = 0;
        for (int index = 1; index < rolledValues.size(); index++) {
            int comparison = Integer.compare(rolledValues.get(index), rolledValues.get(selectedIndex));
            // Strict comparison intentionally keeps the first candidate when values tie.
            if ((selectHighest && comparison > 0) || (!selectHighest && comparison < 0)) {
                selectedIndex = index;
            }
        }
        return selectedIndex;
    }

    private static Map<String, ModuleCatalog.RollMode> validateAndIndexRollModes(
            List<ModuleCatalog.RollMode> rows) {
        if (rows == null || rows.size() != 3) {
            throw invalidRollModeCatalog();
        }
        Map<String, ModuleCatalog.RollMode> indexed = new HashMap<>();
        for (ModuleCatalog.RollMode row : rows) {
            if (row == null
                    || row.rollModeKey() == null
                    || indexed.putIfAbsent(row.rollModeKey(), row) != null) {
                throw invalidRollModeCatalog();
            }
        }
        requireMode(indexed, NORMAL_KEY, "NORMAL", 1, "ONLY_CANDIDATE_V1");
        requireMode(
                indexed,
                ADVANTAGE_KEY,
                "ADVANTAGE",
                2,
                "HIGHEST_FIRST_ON_TIE_V1");
        requireMode(
                indexed,
                DISADVANTAGE_KEY,
                "DISADVANTAGE",
                2,
                "LOWEST_FIRST_ON_TIE_V1");
        return Map.copyOf(indexed);
    }

    private static void requireMode(
            Map<String, ModuleCatalog.RollMode> indexed,
            String key,
            String enumCode,
            int candidateCount,
            String selectionAlgorithm) {
        ModuleCatalog.RollMode mode = indexed.get(key);
        if (mode == null
                || !enumCode.equals(mode.enumCode())
                || candidateCount != mode.candidateCount()
                || !selectionAlgorithm.equals(mode.selectionAlgorithm())) {
            throw invalidRollModeCatalog();
        }
    }

    private static void requireBetween(int value, int minimum, int maximum, String message) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(message);
        }
    }

    private static IllegalStateException invalidRollModeCatalog() {
        return new IllegalStateException("Invalid d20 roll-mode rules in module catalog");
    }

    /** Supplies one server-generated d20 candidate per invocation. */
    @FunctionalInterface
    public interface D20Source {
        int roll();
    }

    /** One ordered candidate ready for later persistence in {@code dice_roll}. */
    public record Candidate(int order, int rolledValue, boolean selected) {
    }

    /** The only two host command comparison outcomes. */
    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    /** Immutable calculation output; no caller-supplied algorithm or roll is retained. */
    public record Result(
            String rollModeKey,
            List<Candidate> candidates,
            int selectedValue,
            int modifierValue,
            int totalValue,
            int difficultyClass,
            Outcome outcome) {
        public Result {
            candidates = List.copyOf(candidates);
        }
    }
}
