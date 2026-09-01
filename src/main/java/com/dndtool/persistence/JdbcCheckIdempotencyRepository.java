package com.dndtool.persistence;

import com.dndtool.service.D20CheckCalculator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JDBC idempotency boundary for immutable host command check results. */
public final class JdbcCheckIdempotencyRepository
        implements CheckIdempotencyRepository {
    static final String OPERATION_TYPE = "EXECUTE_STAGE3_CHECK";
    private static final String LOCK_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, campaign_id,
                   result_status, game_event_id
            FROM host_operation
            WHERE request_id = ?
            FOR UPDATE
            """;
    private static final String LOAD_CHECK_SQL = """
            SELECT ge.event_sequence, ce.id AS check_execution_id,
                   ce.roll_mode_key, ce.modifier_value, ce.total_value,
                   ce.difficulty_class, ce.check_result
            FROM game_event AS ge
            JOIN check_execution AS ce
              ON ce.game_event_id = ge.id AND ce.campaign_id = ge.campaign_id
            WHERE ge.id = ? AND ge.campaign_id = ?
              AND ge.event_type = 'CHECK_EXECUTED'
            """;
    private static final String LOAD_DICE_SQL = """
            SELECT candidate_order, rolled_value, is_selected
            FROM dice_roll
            WHERE check_execution_id = ?
            ORDER BY candidate_order
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, game_event_id, result_status, completed_at)
            VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    @Override
    public Lookup find(Connection connection, Command command) throws SQLException {
        validate(command);
        requireCallerTransaction(connection);
        ExistingOperation existing;
        try (PreparedStatement statement = connection.prepareStatement(LOCK_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Lookup.fresh();
                existing = new ExistingOperation(
                        requiredString(result, "request_digest_sha256"),
                        requiredString(result, "operation_type"),
                        nullablePositiveLong(result, "campaign_id"),
                        requiredString(result, "result_status"),
                        nullablePositiveLong(result, "game_event_id"));
                if (result.next()) throw invalidState("Host command request identity was not unique");
            }
        }
        if (!existing.digest().equals(command.requestDigestSha256())
                || !OPERATION_TYPE.equals(existing.operation())) {
            return Lookup.conflict();
        }
        if (existing.campaignId() == null || existing.campaignId() != command.campaignId()
                || existing.gameEventId() == null || !"SUCCEEDED".equals(existing.status())) {
            throw invalidState("Host command idempotency result is incomplete");
        }
        return Lookup.replay(loadReplay(
                connection, existing.campaignId(), existing.gameEventId()));
    }

    @Override
    public void complete(Connection connection, Completion completion) throws SQLException {
        validate(completion);
        requireCallerTransaction(connection);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPERATION_SQL)) {
            statement.setString(1, completion.requestId());
            statement.setString(2, completion.requestDigestSha256());
            statement.setString(3, OPERATION_TYPE);
            statement.setLong(4, completion.campaignId());
            statement.setLong(5, completion.gameEventId());
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw invalidState("Host command idempotency result was not inserted exactly once");
            }
        }
    }

    private static Replay loadReplay(Connection connection, long campaignId, long gameEventId)
            throws SQLException {
        long eventSequence;
        long checkExecutionId;
        String rollMode;
        int modifier;
        int total;
        int difficultyClass;
        D20CheckCalculator.Outcome outcome;
        try (PreparedStatement statement = connection.prepareStatement(LOAD_CHECK_SQL)) {
            statement.setLong(1, gameEventId);
            statement.setLong(2, campaignId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState("Replayed host command check is missing");
                eventSequence = positiveLong(result, "event_sequence");
                checkExecutionId = positiveLong(result, "check_execution_id");
                rollMode = requiredString(result, "roll_mode_key");
                modifier = requiredInt(result, "modifier_value");
                total = requiredInt(result, "total_value");
                difficultyClass = requiredInt(result, "difficulty_class");
                try {
                    outcome = D20CheckCalculator.Outcome.valueOf(
                            requiredString(result, "check_result"));
                } catch (IllegalArgumentException exception) {
                    throw invalidState("Replayed host command outcome is invalid");
                }
                if (result.next()) throw invalidState("Replayed host command check was not unique");
            }
        }

        List<D20CheckCalculator.Candidate> candidates = loadCandidates(
                connection, checkExecutionId);
        D20CheckCalculator.Result calculation = restoreCalculation(
                rollMode, candidates, modifier, total, difficultyClass, outcome);
        return new Replay(
                new CheckExecutionRepository.SavedCheck(
                        gameEventId, checkExecutionId, eventSequence),
                calculation);
    }

    private static List<D20CheckCalculator.Candidate> loadCandidates(
            Connection connection, long checkExecutionId) throws SQLException {
        List<D20CheckCalculator.Candidate> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD_DICE_SQL)) {
            statement.setLong(1, checkExecutionId);
            statement.setMaxRows(3);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int order = positiveInt(result, "candidate_order");
                    int value = positiveInt(result, "rolled_value");
                    int selected = requiredInt(result, "is_selected");
                    if (selected != 0 && selected != 1) {
                        throw invalidState("Replayed d20 selection flag is invalid");
                    }
                    candidates.add(new D20CheckCalculator.Candidate(order, value, selected == 1));
                }
            }
        }
        return candidates;
    }

    private static D20CheckCalculator.Result restoreCalculation(
            String rollMode,
            List<D20CheckCalculator.Candidate> candidates,
            int modifier,
            int total,
            int difficultyClass,
            D20CheckCalculator.Outcome outcome) throws SQLException {
        int expectedCount = switch (rollMode) {
            case "roll.normal" -> 1;
            case "roll.advantage", "roll.disadvantage" -> 2;
            default -> throw invalidState("Replayed roll mode is unsupported");
        };
        if (candidates.size() != expectedCount || modifier < -99 || modifier > 99
                || difficultyClass < 0 || difficultyClass > 60) {
            throw invalidState("Replayed d20 snapshot bounds are invalid");
        }
        int selectedIndex = -1;
        for (int index = 0; index < candidates.size(); index++) {
            D20CheckCalculator.Candidate candidate = candidates.get(index);
            if (candidate.order() != index + 1
                    || candidate.rolledValue() < 1 || candidate.rolledValue() > 20) {
                throw invalidState("Replayed d20 candidates are invalid");
            }
            if (candidate.selected()) {
                if (selectedIndex >= 0) throw invalidState("Replayed d20 selection is not unique");
                selectedIndex = index;
            }
        }
        if (selectedIndex < 0) throw invalidState("Replayed d20 selection is missing");
        int expectedSelected = expectedSelectedIndex(rollMode, candidates);
        int selectedValue = candidates.get(selectedIndex).rolledValue();
        int expectedTotal = Math.addExact(selectedValue, modifier);
        D20CheckCalculator.Outcome expectedOutcome = expectedTotal >= difficultyClass
                ? D20CheckCalculator.Outcome.SUCCESS : D20CheckCalculator.Outcome.FAILURE;
        if (selectedIndex != expectedSelected || total != expectedTotal || outcome != expectedOutcome) {
            throw invalidState("Replayed d20 calculation is inconsistent");
        }
        return new D20CheckCalculator.Result(
                rollMode, candidates, selectedValue, modifier, total, difficultyClass, outcome);
    }

    private static int expectedSelectedIndex(
            String rollMode, List<D20CheckCalculator.Candidate> candidates) {
        if ("roll.normal".equals(rollMode)) return 0;
        boolean highest = "roll.advantage".equals(rollMode);
        int selected = 0;
        for (int index = 1; index < candidates.size(); index++) {
            int comparison = Integer.compare(
                    candidates.get(index).rolledValue(), candidates.get(selected).rolledValue());
            if ((highest && comparison > 0) || (!highest && comparison < 0)) selected = index;
        }
        return selected;
    }

    private static void validate(Command command) {
        if (command == null || !isCanonicalUuid(command.requestId())
                || !isSha256(command.requestDigestSha256()) || command.campaignId() <= 0) {
            throw new IllegalArgumentException("Invalid host command idempotency command");
        }
    }

    private static void validate(Completion completion) {
        if (completion == null || !isCanonicalUuid(completion.requestId())
                || !isSha256(completion.requestDigestSha256())
                || completion.campaignId() <= 0 || completion.gameEventId() <= 0) {
            throw new IllegalArgumentException("Invalid host command idempotency completion");
        }
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw invalidState(
                    "Host command idempotency requires a writable caller-owned serializable transaction");
        }
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Required replay value is missing");
        return value;
    }

    private static Long nullablePositiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) return null;
        if (value <= 0) throw invalidState("Invalid replay identity");
        return value;
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        Long value = nullablePositiveLong(result, column);
        if (value == null) throw invalidState("Required replay identity is missing");
        return value;
    }

    private static int positiveInt(ResultSet result, String column) throws SQLException {
        int value = requiredInt(result, column);
        if (value <= 0) throw invalidState("Invalid positive replay value");
        return value;
    }

    private static int requiredInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull()) throw invalidState("Required replay integer is missing");
        return value;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private record ExistingOperation(
            String digest,
            String operation,
            Long campaignId,
            String status,
            Long gameEventId) {
    }
}
