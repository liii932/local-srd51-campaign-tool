package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the complete closed d20 algorithm set and every persisted numeric boundary. */
final class D20CheckCalculatorTest {

    @Test
    void normalRollConsumesAndSelectsExactlyOneCandidate() {
        SequenceD20Source source = new SequenceD20Source(12, 19);
        D20CheckCalculator.Result result = calculator(source).calculate("roll.normal", 3, 15);

        assertEquals(List.of(new D20CheckCalculator.Candidate(1, 12, true)), result.candidates());
        assertEquals(12, result.selectedValue());
        assertEquals(15, result.totalValue());
        assertEquals(15, result.difficultyClass());
        assertEquals(D20CheckCalculator.Outcome.SUCCESS, result.outcome());
        assertEquals(1, source.consumed());
    }

    @Test
    void advantageSelectsHighestAndKeepsFirstCandidateOnTie() {
        D20CheckCalculator.Result highest =
                calculator(new SequenceD20Source(7, 16)).calculate("roll.advantage", 0, 10);
        D20CheckCalculator.Result tied =
                calculator(new SequenceD20Source(14, 14)).calculate("roll.advantage", 0, 10);

        assertEquals(List.of(
                new D20CheckCalculator.Candidate(1, 7, false),
                new D20CheckCalculator.Candidate(2, 16, true)), highest.candidates());
        assertEquals(List.of(
                new D20CheckCalculator.Candidate(1, 14, true),
                new D20CheckCalculator.Candidate(2, 14, false)), tied.candidates());
    }

    @Test
    void disadvantageSelectsLowestAndKeepsFirstCandidateOnTie() {
        D20CheckCalculator.Result lowest = calculator(new SequenceD20Source(17, 4))
                .calculate("roll.disadvantage", 2, 7);
        D20CheckCalculator.Result tied = calculator(new SequenceD20Source(6, 6))
                .calculate("roll.disadvantage", 0, 7);

        assertEquals(List.of(
                new D20CheckCalculator.Candidate(1, 17, false),
                new D20CheckCalculator.Candidate(2, 4, true)), lowest.candidates());
        assertEquals(D20CheckCalculator.Outcome.FAILURE, lowest.outcome());
        assertEquals(List.of(
                new D20CheckCalculator.Candidate(1, 6, true),
                new D20CheckCalculator.Candidate(2, 6, false)), tied.candidates());
    }

    @Test
    void comparisonUsesTotalGreaterThanOrEqualToDifficultyClass() {
        D20CheckCalculator.Result equal =
                calculator(new SequenceD20Source(10)).calculate("roll.normal", 5, 15);
        D20CheckCalculator.Result below =
                calculator(new SequenceD20Source(10)).calculate("roll.normal", 4, 15);

        assertEquals(D20CheckCalculator.Outcome.SUCCESS, equal.outcome());
        assertEquals(D20CheckCalculator.Outcome.FAILURE, below.outcome());
    }

    @Test
    void naturalOneAndTwentyHaveNoSpecialOutcome() {
        D20CheckCalculator.Result naturalOneSucceeds =
                calculator(new SequenceD20Source(1)).calculate("roll.normal", 59, 60);
        D20CheckCalculator.Result naturalTwentyFails =
                calculator(new SequenceD20Source(20)).calculate("roll.normal", -20, 1);

        assertEquals(D20CheckCalculator.Outcome.SUCCESS, naturalOneSucceeds.outcome());
        assertEquals(D20CheckCalculator.Outcome.FAILURE, naturalTwentyFails.outcome());
    }

    @Test
    void modifierAndDifficultyClassAcceptOnlyDatabaseBackedRanges() {
        assertEquals(-98, calculator(new SequenceD20Source(1))
                .calculate("roll.normal", -99, 0).totalValue());
        assertEquals(119, calculator(new SequenceD20Source(20))
                .calculate("roll.normal", 99, 60).totalValue());

        assertThrows(IllegalArgumentException.class,
                () -> calculator(new SequenceD20Source(10))
                        .calculate("roll.normal", -100, 10));
        assertThrows(IllegalArgumentException.class,
                () -> calculator(new SequenceD20Source(10))
                        .calculate("roll.normal", 100, 10));
        assertThrows(IllegalArgumentException.class,
                () -> calculator(new SequenceD20Source(10))
                        .calculate("roll.normal", 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> calculator(new SequenceD20Source(10))
                        .calculate("roll.normal", 0, 61));
    }

    @Test
    void invalidInputIsRejectedBeforeRandomSourceIsConsumed() {
        SequenceD20Source source = new SequenceD20Source(10);
        D20CheckCalculator calculator = calculator(source);

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate("roll.unknown", 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(null, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate("roll.normal", 100, 10));
        assertEquals(0, source.consumed());
    }

    @Test
    void outOfRangeServerRollFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> calculator(new SequenceD20Source(0))
                        .calculate("roll.normal", 0, 10));
        assertThrows(IllegalStateException.class,
                () -> calculator(new SequenceD20Source(21))
                        .calculate("roll.normal", 0, 10));
    }

    @Test
    void malformedOrExpandedRollModeCatalogFailsClosed() {
        List<ModuleCatalog.RollMode> wrongAlgorithm = List.of(
                new ModuleCatalog.RollMode(
                        "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1"),
                new ModuleCatalog.RollMode(
                        "roll.advantage", "ADVANTAGE", 2, "LOWEST_FIRST_ON_TIE_V1"),
                new ModuleCatalog.RollMode(
                        "roll.disadvantage", "DISADVANTAGE", 2, "LOWEST_FIRST_ON_TIE_V1"));
        List<ModuleCatalog.RollMode> expanded = List.of(
                normalMode(), advantageMode(), disadvantageMode(),
                new ModuleCatalog.RollMode("roll.other", "OTHER", 1, "ONLY_CANDIDATE_V1"));

        assertThrows(IllegalStateException.class,
                () -> new D20CheckCalculator(catalog(wrongAlgorithm), () -> 10));
        assertThrows(IllegalStateException.class,
                () -> new D20CheckCalculator(catalog(expanded), () -> 10));
    }

    private static D20CheckCalculator calculator(D20CheckCalculator.D20Source source) {
        return new D20CheckCalculator(
                catalog(List.of(normalMode(), advantageMode(), disadvantageMode())), source);
    }

    private static ModuleCatalog.RollMode normalMode() {
        return new ModuleCatalog.RollMode(
                "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1");
    }

    private static ModuleCatalog.RollMode advantageMode() {
        return new ModuleCatalog.RollMode(
                "roll.advantage", "ADVANTAGE", 2, "HIGHEST_FIRST_ON_TIE_V1");
    }

    private static ModuleCatalog.RollMode disadvantageMode() {
        return new ModuleCatalog.RollMode(
                "roll.disadvantage", "DISADVANTAGE", 2, "LOWEST_FIRST_ON_TIE_V1");
    }

    private static ModuleCatalog catalog(List<ModuleCatalog.RollMode> rollModes) {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", null, "RELEASED"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                rollModes,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    /** Deterministic source that also proves invalid inputs do not consume a candidate. */
    private static final class SequenceD20Source implements D20CheckCalculator.D20Source {
        private final ArrayDeque<Integer> values;
        private int consumed;

        private SequenceD20Source(Integer... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public int roll() {
            consumed++;
            return values.removeFirst();
        }

        private int consumed() {
            return consumed;
        }
    }
}
