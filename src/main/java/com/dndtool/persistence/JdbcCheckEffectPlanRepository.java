package com.dndtool.persistence;

import com.dndtool.service.CheckRequestPolicy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** JDBC writer for the ordered, physically typed effect plan tables. */
public final class JdbcCheckEffectPlanRepository implements CheckEffectPlanRepository {
    private static final String INSERT_EFFECT_SQL = """
            INSERT INTO check_effect (
                check_execution_id, module_release_id, effect_branch, effect_order, effect_key
            ) VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_PARAMETER_SQL = """
            INSERT INTO check_effect_parameter_value (
                check_effect_id, module_release_id, effect_key, parameter_key, parameter_order,
                value_type, reference_value, integer_value, decimal_value, text_value, boolean_value
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final Set<String> EFFECT_ALGORITHMS = Set.of(
            "ADJUST_CURRENT_HP_CLAMP_V1",
            "GRANT_MODULE_ITEM_V1",
            "GRANT_TEMPORARY_ITEM_V1",
            "SET_ENTITY_NODE_POSITION_V1",
            "APPEND_EVENT_MESSAGE_V1");
    private static final Set<String> PARAMETER_TYPES = Set.of(
            "REFERENCE", "INTEGER", "DECIMAL", "TEXT", "BOOLEAN");
    private static final String STABLE_KEY = "[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*";

    @Override
    public SavedPlan append(Connection connection, Command command) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        validate(command);
        requireCallerTransaction(connection);

        List<SavedEffect> saved = new ArrayList<>();
        appendBranch(connection, command, command.success(), saved);
        appendBranch(connection, command, command.failure(), saved);
        return new SavedPlan(command.checkExecutionId(), saved);
    }

    private static void appendBranch(
            Connection connection, Command command, BranchPlan branch, List<SavedEffect> saved)
            throws SQLException {
        validateBranch(branch);
        for (EffectPlan plan : branch.effects()) {
            CheckRequestPolicy.PreparedEffect effect = plan.effect();
            long effectId = insertEffect(connection, command, branch, plan);
            List<Long> parameterIds = insertParameters(connection, command, effectId, effect);
            saved.add(new SavedEffect(
                    effectId, branch.branch(), plan.effectOrder(), effect.effectKey(), parameterIds));
        }
    }

    private static long insertEffect(
            Connection connection, Command command, BranchPlan branch, EffectPlan plan)
            throws SQLException {
        CheckRequestPolicy.PreparedEffect effect = plan.effect();
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EFFECT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.checkExecutionId());
            statement.setLong(2, command.moduleReleaseId());
            statement.setString(3, branch.branch().name());
            statement.setInt(4, plan.effectOrder());
            statement.setString(5, effect.effectKey());
            return executeInsertWithKey(statement, "check effect");
        }
    }

    private static List<Long> insertParameters(
            Connection connection,
            Command command,
            long effectId,
            CheckRequestPolicy.PreparedEffect effect)
            throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_PARAMETER_SQL, Statement.RETURN_GENERATED_KEYS)) {
            for (CheckRequestPolicy.PreparedParameter parameter : effect.parameters()) {
                bindParameter(statement, command, effectId, effect.effectKey(), parameter);
                if (statement.executeUpdate() != 1) {
                    throw invalidState("Effect parameter was not inserted exactly once");
                }
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw invalidState("Effect parameter generated key is missing");
                    long id = keys.getLong(1);
                    if (keys.wasNull() || id <= 0 || keys.next()) {
                        throw invalidState("Effect parameter generated key is invalid");
                    }
                    ids.add(id);
                }
            }
        }
        return List.copyOf(ids);
    }

    private static void bindParameter(
            PreparedStatement statement,
            Command command,
            long effectId,
            String effectKey,
            CheckRequestPolicy.PreparedParameter parameter)
            throws SQLException {
        statement.setLong(1, effectId);
        statement.setLong(2, command.moduleReleaseId());
        statement.setString(3, effectKey);
        statement.setString(4, parameter.parameterKey());
        statement.setInt(5, parameter.parameterOrder());
        statement.setString(6, parameter.dataType());
        clearTypedColumns(statement);
        CheckRequestPolicy.Value value = parameter.value();
        switch (value) {
            case CheckRequestPolicy.ReferenceValue reference ->
                    statement.setString(7, reference.value());
            case CheckRequestPolicy.IntegerValue integer ->
                    statement.setLong(8, integer.value());
            case CheckRequestPolicy.DecimalValue decimal ->
                    statement.setBigDecimal(9, decimal.value());
            case CheckRequestPolicy.TextValue text ->
                    statement.setString(10, text.value());
            case CheckRequestPolicy.BooleanValue bool ->
                    statement.setBoolean(11, bool.value());
        }
    }

    private static void clearTypedColumns(PreparedStatement statement) throws SQLException {
        statement.setNull(7, Types.VARCHAR);
        statement.setNull(8, Types.BIGINT);
        statement.setNull(9, Types.DECIMAL);
        statement.setNull(10, Types.VARCHAR);
        statement.setNull(11, Types.TINYINT);
    }

    private static void validate(Command command) {
        if (command == null || command.checkExecutionId() <= 0 || command.moduleReleaseId() <= 0) {
            throw new IllegalArgumentException("Invalid effect plan identity");
        }
        if (command.success().branch() != EffectBranch.SUCCESS
                || command.failure().branch() != EffectBranch.FAILURE) {
            throw new IllegalArgumentException("Effect plan branches are mismatched");
        }
        validateBranch(command.success());
        validateBranch(command.failure());
    }

    private static void validateBranch(BranchPlan branch) {
        if (branch == null || branch.effects() == null) {
            throw new IllegalArgumentException("Effect branch is required");
        }
        int expectedOrder = 1;
        int messageCount = 0;
        for (EffectPlan plan : branch.effects()) {
            if (plan == null || plan.effectOrder() != expectedOrder || plan.effect() == null) {
                throw new IllegalArgumentException("Effect order must be one-based and contiguous");
            }
            CheckRequestPolicy.PreparedEffect effect = plan.effect();
            if (!stableKey(effect.effectKey())
                    || !EFFECT_ALGORITHMS.contains(effect.executionAlgorithm())) {
                throw new IllegalArgumentException("Invalid effect definition");
            }
            if ("effect.append_event_message".equals(effect.effectKey()) && ++messageCount > 1) {
                throw new IllegalArgumentException("Message effect may appear once per branch");
            }
            validateParameters(effect.parameters());
            expectedOrder++;
        }
    }

    private static void validateParameters(List<CheckRequestPolicy.PreparedParameter> parameters) {
        if (parameters == null) throw new IllegalArgumentException("Effect parameters are required");
        Set<String> keys = new HashSet<>();
        int expectedOrder = 1;
        for (CheckRequestPolicy.PreparedParameter parameter : parameters) {
            if (parameter == null
                    || parameter.parameterOrder() != expectedOrder
                    || !stableKey(parameter.parameterKey())
                    || !keys.add(parameter.parameterKey())
                    || !PARAMETER_TYPES.contains(parameter.dataType())
                    || parameter.value() == null) {
                throw new IllegalArgumentException("Invalid effect parameter definition");
            }
            validateValue(parameter.dataType(), parameter.value());
            expectedOrder++;
        }
    }

    private static void validateValue(String type, CheckRequestPolicy.Value value) {
        switch (type) {
            case "REFERENCE" -> {
                if (!(value instanceof CheckRequestPolicy.ReferenceValue reference)
                        || reference.value() == null
                        || reference.value().isBlank()
                        || reference.value().codePoints().anyMatch(Character::isISOControl)
                        || reference.value().codePointCount(0, reference.value().length()) > 255
                        || (!reference.value().matches(STABLE_KEY)
                                && !isCanonicalUuid(reference.value()))) {
                    throw new IllegalArgumentException("Invalid reference parameter value");
                }
            }
            case "INTEGER" -> requireType(value, CheckRequestPolicy.IntegerValue.class);
            case "DECIMAL" -> {
                if (!(value instanceof CheckRequestPolicy.DecimalValue decimal)
                        || decimal.value() == null
                        || decimal.value().precision() > 38
                        || decimal.value().scale() > 18) {
                    throw new IllegalArgumentException("Invalid decimal parameter value");
                }
            }
            case "TEXT" -> {
                if (!(value instanceof CheckRequestPolicy.TextValue text)
                        || text.value() == null
                        || !Normalizer.normalize(text.value(), Normalizer.Form.NFC).equals(text.value())
                        || text.value().codePointCount(0, text.value().length()) > 2000
                        || text.value().codePoints().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("Invalid text parameter value");
                }
            }
            case "BOOLEAN" -> requireType(value, CheckRequestPolicy.BooleanValue.class);
            default -> throw new IllegalArgumentException("Unsupported parameter type");
        }
    }

    private static void requireType(Object value, Class<?> type) {
        if (!type.isInstance(value)) throw new IllegalArgumentException("Parameter type mismatch");
    }

    private static boolean stableKey(String value) {
        return value != null && value.matches(STABLE_KEY);
    }

    private static boolean isCanonicalUuid(String value) {
        try {
            return java.util.UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection.getAutoCommit()
                || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw new SQLException(
                    "Effect plan persistence requires a writable caller-owned serializable transaction");
        }
    }

    private static long executeInsertWithKey(PreparedStatement statement, String label)
            throws SQLException {
        if (statement.executeUpdate() != 1) throw invalidState(label + " was not inserted exactly once");
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw invalidState(label + " generated key is missing");
            long id = keys.getLong(1);
            if (keys.wasNull() || id <= 0 || keys.next()) throw invalidState(label + " generated key is invalid");
            return id;
        }
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }
}
