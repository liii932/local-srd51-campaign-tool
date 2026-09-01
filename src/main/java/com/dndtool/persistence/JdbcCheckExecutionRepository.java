package com.dndtool.persistence;

import com.dndtool.service.D20CheckCalculator;
import com.dndtool.service.CheckTextPolicy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Objects;

/** JDBC implementation that writes a check snapshot inside the caller's serializable transaction. */
public final class JdbcCheckExecutionRepository implements CheckExecutionRepository {
    private static final String ADVANCE_EVENT_TAIL_SQL = """
            UPDATE campaign
            SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id
            ) VALUES (?, ?, 'CHECK_EXECUTED', ?)
            """;
    private static final String INSERT_CHECK_SQL = """
            INSERT INTO check_execution (
                game_event_id, campaign_id, module_release_id, executor_character_id,
                event_key, check_key, roll_mode_key, modifier_source_key, manual_name,
                modifier_value, total_value, difficulty_class, check_result
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_DIE_SQL = """
            INSERT INTO dice_roll (
                check_execution_id, candidate_order, rolled_value, is_selected
            ) VALUES (?, ?, ?, ?)
            """;

    @Override
    public SavedCheck append(Connection connection, Command command) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        validate(command);
        requireCallerTransaction(connection);

        long eventSequence;
        try {
            eventSequence = Math.addExact(command.expectedEventTail(), 1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Event sequence is exhausted", exception);
        }
        advanceEventTail(connection, command, eventSequence);
        long gameEventId = insertEvent(connection, command, eventSequence);
        long checkExecutionId = insertCheck(connection, command, gameEventId);
        insertCandidates(connection, command.calculation().candidates(), checkExecutionId);
        return new SavedCheck(gameEventId, checkExecutionId, eventSequence);
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection.getAutoCommit()
                || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw new SQLException(
                    "Check persistence requires a writable caller-owned serializable transaction");
        }
    }

    private static void advanceEventTail(
            Connection connection, Command command, long eventSequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADVANCE_EVENT_TAIL_SQL)) {
            statement.setLong(1, eventSequence);
            statement.setLong(2, command.campaignId());
            statement.setLong(3, command.expectedEventTail());
            if (statement.executeUpdate() != 1) {
                throw invalidState("Campaign event tail changed before check persistence");
            }
        }
    }

    private static long insertEvent(
            Connection connection, Command command, long eventSequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.campaignId());
            statement.setLong(2, eventSequence);
            statement.setLong(3, command.executorCharacterId());
            return executeInsertWithKey(statement, "game event");
        }
    }

    private static long insertCheck(
            Connection connection, Command command, long gameEventId) throws SQLException {
        D20CheckCalculator.Result calculation = command.calculation();
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_CHECK_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, gameEventId);
            statement.setLong(2, command.campaignId());
            statement.setLong(3, command.moduleReleaseId());
            statement.setLong(4, command.executorCharacterId());
            setNullableString(statement, 5, command.eventKey());
            statement.setString(6, command.checkKey());
            statement.setString(7, calculation.rollModeKey());
            setNullableString(statement, 8, command.modifierSourceKey());
            setNullableString(statement, 9, command.manualName());
            statement.setInt(10, calculation.modifierValue());
            statement.setInt(11, calculation.totalValue());
            statement.setInt(12, calculation.difficultyClass());
            statement.setString(13, calculation.outcome().name());
            return executeInsertWithKey(statement, "check execution");
        }
    }

    private static void insertCandidates(
            Connection connection,
            List<D20CheckCalculator.Candidate> candidates,
            long checkExecutionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DIE_SQL)) {
            for (D20CheckCalculator.Candidate candidate : candidates) {
                statement.setLong(1, checkExecutionId);
                statement.setInt(2, candidate.order());
                statement.setInt(3, candidate.rolledValue());
                statement.setBoolean(4, candidate.selected());
                if (statement.executeUpdate() != 1) {
                    throw invalidState("D20 candidate was not inserted exactly once");
                }
            }
        }
    }

    private static long executeInsertWithKey(PreparedStatement statement, String label)
            throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw invalidState(label + " was not inserted exactly once");
        }
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw invalidState(label + " generated key is missing");
            }
            long id = keys.getLong(1);
            if (keys.wasNull() || id <= 0 || keys.next()) {
                throw invalidState(label + " generated key is invalid");
            }
            return id;
        }
    }

    private static void setNullableString(
            PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void validate(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Check persistence command is required");
        }
        if (command.campaignId() <= 0
                || command.expectedEventTail() < 0
                || command.moduleReleaseId() <= 0
                || command.executorCharacterId() <= 0
                || command.checkKey() == null
                || command.checkKey().isBlank()
                || command.calculation() == null) {
            throw new IllegalArgumentException("Invalid check persistence identity");
        }
        validateSourceShape(command);
        validateCalculation(command.calculation());
    }

    private static void validateSourceShape(Command command) {
        if ("check.manual".equals(command.checkKey())) {
            if (command.eventKey() != null
                    || command.modifierSourceKey() != null
                    || command.manualName() == null
                    || !command.manualName().equals(
                            CheckTextPolicy.normalizeManualName(command.manualName()))) {
                throw new IllegalArgumentException("Invalid manual check persistence shape");
            }
        } else if (command.manualName() != null) {
            throw new IllegalArgumentException("Manual name is only valid for manual checks");
        }
    }

    private static void validateCalculation(D20CheckCalculator.Result calculation) {
        List<D20CheckCalculator.Candidate> candidates = calculation.candidates();
        if (calculation.rollModeKey() == null) {
            throw new IllegalArgumentException("Persisted roll mode is required");
        }
        int expectedCount = switch (calculation.rollModeKey()) {
            case "roll.normal" -> 1;
            case "roll.advantage", "roll.disadvantage" -> 2;
            default -> throw new IllegalArgumentException("Unsupported persisted roll mode");
        };
        if (candidates.size() != expectedCount
                || calculation.modifierValue() < -99
                || calculation.modifierValue() > 99
                || calculation.difficultyClass() < 0
                || calculation.difficultyClass() > 60
                || calculation.outcome() == null) {
            throw new IllegalArgumentException("Invalid d20 calculation snapshot");
        }

        int selectedCount = 0;
        int selectedValue = 0;
        int selectedIndex = -1;
        for (int index = 0; index < candidates.size(); index++) {
            D20CheckCalculator.Candidate candidate = candidates.get(index);
            if (candidate == null
                    || candidate.order() != index + 1
                    || candidate.rolledValue() < 1
                    || candidate.rolledValue() > 20) {
                throw new IllegalArgumentException("Invalid ordered d20 candidate");
            }
            if (candidate.selected()) {
                selectedCount++;
                selectedValue = candidate.rolledValue();
                selectedIndex = index;
            }
        }
        int expectedSelectedIndex = expectedSelectedIndex(calculation.rollModeKey(), candidates);
        int expectedTotal = Math.addExact(selectedValue, calculation.modifierValue());
        D20CheckCalculator.Outcome expectedOutcome =
                expectedTotal >= calculation.difficultyClass()
                        ? D20CheckCalculator.Outcome.SUCCESS
                        : D20CheckCalculator.Outcome.FAILURE;
        if (selectedCount != 1
                || selectedIndex != expectedSelectedIndex
                || selectedValue != calculation.selectedValue()
                || expectedTotal != calculation.totalValue()
                || expectedOutcome != calculation.outcome()) {
            throw new IllegalArgumentException("Inconsistent d20 calculation snapshot");
        }
    }

    private static int expectedSelectedIndex(
            String rollModeKey, List<D20CheckCalculator.Candidate> candidates) {
        if ("roll.normal".equals(rollModeKey)) return 0;
        boolean selectHighest = "roll.advantage".equals(rollModeKey);
        int selectedIndex = 0;
        for (int index = 1; index < candidates.size(); index++) {
            int comparison = Integer.compare(
                    candidates.get(index).rolledValue(),
                    candidates.get(selectedIndex).rolledValue());
            if ((selectHighest && comparison > 0) || (!selectHighest && comparison < 0)) {
                selectedIndex = index;
            }
        }
        return selectedIndex;
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }
}
