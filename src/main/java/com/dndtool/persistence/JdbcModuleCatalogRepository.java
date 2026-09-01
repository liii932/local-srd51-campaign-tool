package com.dndtool.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/** Loads every canonical module section through prepared, read-only JDBC queries. */
public final class JdbcModuleCatalogRepository
        implements ModuleCatalogRepository, TransactionalModuleCatalogRepository {
    private static final Pattern MODULE_KEY =
            Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Pattern RELEASE_VERSION =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final String RELEASE_SQL = """
            SELECT id, module_key, release_version, canonical_format_version,
                   hash_algorithm, content_sha256, release_status
            FROM module_release
            WHERE module_key = ? AND release_version = ?
            """;
    private static final String RULE_CONSTANT_SQL = """
            SELECT constant_key, value_type, text_value, identifier_value,
                   integer_value, decimal_value, boolean_value
            FROM module_rule_constant
            WHERE module_release_id = ?
            ORDER BY constant_key
            """;
    private static final String FIELD_DEFINITION_SQL = """
            SELECT field_key, display_name, data_type,
                   default_text, default_integer, default_decimal, default_boolean,
                   minimum_integer, maximum_integer, minimum_decimal, maximum_decimal,
                   dependent_max_field_key, unit, description
            FROM module_field_definition
            WHERE module_release_id = ?
            ORDER BY field_key
            """;
    private static final String CLASS_DEFINITION_SQL = """
            SELECT class_key, display_name
            FROM module_class_definition
            WHERE module_release_id = ?
            ORDER BY class_key
            """;
    private static final String PROFICIENCY_TIER_SQL = """
            SELECT proficiency_key, enum_code, numerator, denominator, rounding_algorithm
            FROM module_proficiency_tier
            WHERE module_release_id = ?
            ORDER BY proficiency_key
            """;
    private static final String PROFICIENCY_BONUS_SQL = """
            SELECT minimum_total_level, maximum_total_level, bonus
            FROM module_proficiency_bonus_band
            WHERE module_release_id = ?
            ORDER BY minimum_total_level, maximum_total_level, bonus
            """;
    private static final String SKILL_DEFINITION_SQL = """
            SELECT skill_key, display_name, ability_field_key
            FROM module_skill_definition
            WHERE module_release_id = ?
            ORDER BY skill_key
            """;
    private static final String SAVE_DEFINITION_SQL = """
            SELECT save_key, ability_field_key
            FROM module_save_definition
            WHERE module_release_id = ?
            ORDER BY save_key
            """;
    private static final String ITEM_TEMPLATE_SQL = """
            SELECT item_key, display_name, description
            FROM module_item_template
            WHERE module_release_id = ?
            ORDER BY item_key
            """;
    private static final String ENTITY_TEMPLATE_SQL = """
            SELECT template_key, display_name
            FROM module_entity_template
            WHERE module_release_id = ?
            ORDER BY template_key
            """;
    private static final String ENTITY_TEMPLATE_VALUE_SQL = """
            SELECT template_key, field_key, value_type,
                   text_value, integer_value, decimal_value, boolean_value
            FROM module_entity_template_value
            WHERE module_release_id = ?
            ORDER BY template_key, field_key
            """;
    private static final String ENTITY_TEMPLATE_CLASS_SQL = """
            SELECT template_key, class_key, level
            FROM module_entity_template_class_level
            WHERE module_release_id = ?
            ORDER BY template_key, class_key
            """;
    private static final String ENTITY_TEMPLATE_PROFICIENCY_SQL = """
            SELECT template_key, target_kind, target_key, proficiency_key
            FROM module_entity_template_proficiency
            WHERE module_release_id = ?
            ORDER BY template_key, target_kind, target_key
            """;
    private static final String CHECK_DEFINITION_SQL = """
            SELECT check_key, enum_code, modifier_algorithm
            FROM module_check_definition
            WHERE module_release_id = ?
            ORDER BY check_key
            """;
    private static final String ROLL_MODE_SQL = """
            SELECT roll_mode_key, enum_code, candidate_count, selection_algorithm
            FROM module_roll_mode
            WHERE module_release_id = ?
            ORDER BY roll_mode_key
            """;
    private static final String EVENT_TEMPLATE_SQL = """
            SELECT event_key, display_name
            FROM module_event_template
            WHERE module_release_id = ?
            ORDER BY event_key
            """;
    private static final String EVENT_CHECK_SQL = """
            SELECT event_key, check_key
            FROM module_event_check
            WHERE module_release_id = ?
            ORDER BY event_key, check_key
            """;
    private static final String EVENT_EFFECT_SQL = """
            SELECT event_key, effect_key
            FROM module_event_effect
            WHERE module_release_id = ?
            ORDER BY event_key, effect_key
            """;
    private static final String EFFECT_DEFINITION_SQL = """
            SELECT effect_key, execution_algorithm
            FROM module_effect_definition
            WHERE module_release_id = ?
            ORDER BY effect_key
            """;
    private static final String EFFECT_PARAMETER_SQL = """
            SELECT effect_key, parameter_key, data_type, reference_kind,
                   minimum_integer, maximum_integer, minimum_decimal, maximum_decimal,
                   text_normalization, reject_control_characters, parameter_order
            FROM module_effect_parameter
            WHERE module_release_id = ?
            ORDER BY effect_key, parameter_order, parameter_key
            """;
    private static final String MAP_DEFINITION_SQL = """
            SELECT map_key, map_type
            FROM module_map_definition
            WHERE module_release_id = ?
            ORDER BY map_key
            """;
    private static final String MAP_NODE_SQL = """
            SELECT map_key, node_key, display_name
            FROM module_map_node
            WHERE module_release_id = ?
            ORDER BY map_key, node_key
            """;
    private static final String MAP_CONNECTION_SQL = """
            SELECT map_key, endpoint_low_key, endpoint_high_key
            FROM module_map_connection
            WHERE module_release_id = ?
            ORDER BY map_key, endpoint_low_key, endpoint_high_key
            """;
    private static final String CATALOG_DEFINITION_V2_SQL = """
            SELECT definition_type, definition_key, display_name, description, sort_order
            FROM module_catalog_definition_v2
            WHERE module_release_id = ?
            ORDER BY definition_type, definition_key
            """;
    private static final String CATALOG_ATTRIBUTE_V2_SQL = """
            SELECT definition_type, definition_key, attribute_key, attribute_order,
                   value_type, text_value, identifier_value, integer_value,
                   decimal_value, boolean_value
            FROM module_catalog_attribute_v2
            WHERE module_release_id = ?
            ORDER BY definition_type, definition_key, attribute_key, attribute_order
            """;
    private static final String CATALOG_RELATION_V2_SQL = """
            SELECT source_type, source_key, relation_type,
                   target_type, target_key, relation_order
            FROM module_catalog_relation_v2
            WHERE module_release_id = ?
            ORDER BY source_type, source_key, relation_type, relation_order,
                     target_type, target_key
            """;

    private final DataSource dataSource;

    public JdbcModuleCatalogRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<ModuleCatalog> findByIdentity(String moduleKey, String releaseVersion)
            throws SQLException {
        validateIdentity(moduleKey, releaseVersion);

        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                configureReadOnlyTransaction(connection);
                Optional<ModuleCatalog> result = findByIdentity(
                        connection, moduleKey, releaseVersion);
                connection.commit();
                restoreConnection(connection, original);
                return result;
            } catch (SQLException | RuntimeException exception) {
                rollbackAndRestore(connection, original, exception);
                throw exception;
            }
        }
    }

    /** Uses the caller's snapshot and deliberately does not commit, roll back or close it. */
    @Override
    public Optional<ModuleCatalog> findByIdentity(
            Connection connection, String moduleKey, String releaseVersion) throws SQLException {
        validateIdentity(moduleKey, releaseVersion);
        if (connection == null || connection.getAutoCommit()) {
            throw new SQLException("Module catalog requires a caller-owned transaction");
        }
        Optional<ReleaseRow> release = findRelease(connection, moduleKey, releaseVersion);
        return release.isEmpty()
                ? Optional.empty()
                : Optional.of(loadCatalog(connection, release.orElseThrow()));
    }

    private static void validateIdentity(String moduleKey, String releaseVersion) {
        if (moduleKey == null
                || releaseVersion == null
                || !MODULE_KEY.matcher(moduleKey).matches()
                || !RELEASE_VERSION.matcher(releaseVersion).matches()) {
            throw new IllegalArgumentException("Invalid module release identity");
        }
    }

    private static void configureReadOnlyTransaction(Connection connection) throws SQLException {
        // Configure the session before disabling auto-commit so every SELECT shares one snapshot.
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
    }

    private static Optional<ReleaseRow> findRelease(
            Connection connection, String moduleKey, String releaseVersion) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RELEASE_SQL)) {
            statement.setString(1, moduleKey);
            statement.setString(2, releaseVersion);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                ReleaseRow release = new ReleaseRow(
                        result.getLong("id"),
                        new ModuleCatalog.Release(
                                requireString(result, "module_key"),
                                requireString(result, "release_version"),
                                result.getInt("canonical_format_version"),
                                requireString(result, "hash_algorithm"),
                                result.getString("content_sha256"),
                                requireString(result, "release_status")));
                if (result.wasNull() || result.next()) {
                    throw invalidDefinition();
                }
                return Optional.of(release);
            }
        }
    }

    private static ModuleCatalog loadCatalog(Connection connection, ReleaseRow release)
            throws SQLException {
        if (release.release().canonicalFormatVersion() == 2) {
            return loadCatalogV2(connection, release);
        }
        long releaseId = release.id();
        return new ModuleCatalog(
                release.release(),
                query(connection, RULE_CONSTANT_SQL, releaseId,
                        JdbcModuleCatalogRepository::readRuleConstant),
                query(connection, FIELD_DEFINITION_SQL, releaseId,
                        JdbcModuleCatalogRepository::readFieldDefinition),
                query(connection, CLASS_DEFINITION_SQL, releaseId,
                        result -> new ModuleCatalog.ClassDefinition(
                                requireString(result, "class_key"),
                                requireString(result, "display_name"))),
                query(connection, PROFICIENCY_TIER_SQL, releaseId,
                        result -> new ModuleCatalog.ProficiencyTier(
                                requireString(result, "proficiency_key"),
                                requireString(result, "enum_code"),
                                result.getInt("numerator"),
                                result.getInt("denominator"),
                                requireString(result, "rounding_algorithm"))),
                query(connection, PROFICIENCY_BONUS_SQL, releaseId,
                        result -> new ModuleCatalog.ProficiencyBonusBand(
                                result.getInt("minimum_total_level"),
                                result.getInt("maximum_total_level"),
                                result.getInt("bonus"))),
                query(connection, SKILL_DEFINITION_SQL, releaseId,
                        result -> new ModuleCatalog.SkillDefinition(
                                requireString(result, "skill_key"),
                                requireString(result, "display_name"),
                                requireString(result, "ability_field_key"))),
                query(connection, SAVE_DEFINITION_SQL, releaseId,
                        result -> new ModuleCatalog.SaveDefinition(
                                requireString(result, "save_key"),
                                requireString(result, "ability_field_key"))),
                query(connection, ITEM_TEMPLATE_SQL, releaseId,
                        result -> new ModuleCatalog.ItemTemplate(
                                requireString(result, "item_key"),
                                requireString(result, "display_name"),
                                requireString(result, "description"))),
                query(connection, ENTITY_TEMPLATE_SQL, releaseId,
                        result -> new ModuleCatalog.EntityTemplate(
                                requireString(result, "template_key"),
                                requireString(result, "display_name"))),
                query(connection, ENTITY_TEMPLATE_VALUE_SQL, releaseId,
                        JdbcModuleCatalogRepository::readEntityTemplateValue),
                query(connection, ENTITY_TEMPLATE_CLASS_SQL, releaseId,
                        result -> new ModuleCatalog.EntityTemplateClassLevel(
                                requireString(result, "template_key"),
                                requireString(result, "class_key"),
                                result.getInt("level"))),
                query(connection, ENTITY_TEMPLATE_PROFICIENCY_SQL, releaseId,
                        result -> new ModuleCatalog.EntityTemplateProficiency(
                                requireString(result, "template_key"),
                                requireString(result, "target_kind"),
                                requireString(result, "target_key"),
                                requireString(result, "proficiency_key"))),
                query(connection, CHECK_DEFINITION_SQL, releaseId,
                        result -> new ModuleCatalog.CheckDefinition(
                                requireString(result, "check_key"),
                                requireString(result, "enum_code"),
                                requireString(result, "modifier_algorithm"))),
                query(connection, ROLL_MODE_SQL, releaseId,
                        result -> new ModuleCatalog.RollMode(
                                requireString(result, "roll_mode_key"),
                                requireString(result, "enum_code"),
                                result.getInt("candidate_count"),
                                requireString(result, "selection_algorithm"))),
                query(connection, EVENT_TEMPLATE_SQL, releaseId,
                        result -> new ModuleCatalog.EventTemplate(
                                requireString(result, "event_key"),
                                requireString(result, "display_name"))),
                query(connection, EVENT_CHECK_SQL, releaseId,
                        result -> new ModuleCatalog.EventCheck(
                                requireString(result, "event_key"),
                                requireString(result, "check_key"))),
                query(connection, EVENT_EFFECT_SQL, releaseId,
                        result -> new ModuleCatalog.EventEffect(
                                requireString(result, "event_key"),
                                requireString(result, "effect_key"))),
                query(connection, EFFECT_DEFINITION_SQL, releaseId,
                        result -> new ModuleCatalog.EffectDefinition(
                                requireString(result, "effect_key"),
                                requireString(result, "execution_algorithm"))),
                query(connection, EFFECT_PARAMETER_SQL, releaseId,
                        JdbcModuleCatalogRepository::readEffectParameter),
                query(connection, MAP_DEFINITION_SQL, releaseId,
                        result -> new ModuleCatalog.MapDefinition(
                                requireString(result, "map_key"),
                                requireString(result, "map_type"))),
                query(connection, MAP_NODE_SQL, releaseId,
                        result -> new ModuleCatalog.MapNode(
                                requireString(result, "map_key"),
                                requireString(result, "node_key"),
                                requireString(result, "display_name"))),
                query(connection, MAP_CONNECTION_SQL, releaseId,
                        result -> new ModuleCatalog.MapConnection(
                                requireString(result, "map_key"),
                                requireString(result, "endpoint_low_key"),
                                requireString(result, "endpoint_high_key"))));
    }

    private static ModuleCatalog loadCatalogV2(Connection connection, ReleaseRow release)
            throws SQLException {
        long releaseId = release.id();
        return new ModuleCatalog(
                release.release(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                query(connection, CATALOG_DEFINITION_V2_SQL, releaseId,
                        result -> new ModuleCatalog.CatalogDefinition(
                                requireString(result, "definition_type"),
                                requireString(result, "definition_key"),
                                requireString(result, "display_name"),
                                requireString(result, "description"),
                                result.getInt("sort_order"))),
                query(connection, CATALOG_ATTRIBUTE_V2_SQL, releaseId,
                        JdbcModuleCatalogRepository::readCatalogAttribute),
                query(connection, CATALOG_RELATION_V2_SQL, releaseId,
                        result -> new ModuleCatalog.CatalogRelation(
                                requireString(result, "source_type"),
                                requireString(result, "source_key"),
                                requireString(result, "relation_type"),
                                requireString(result, "target_type"),
                                requireString(result, "target_key"),
                                result.getInt("relation_order"))));
    }

    private static ModuleCatalog.RuleConstant readRuleConstant(ResultSet result)
            throws SQLException {
        String valueType = requireString(result, "value_type");
        return new ModuleCatalog.RuleConstant(
                requireString(result, "constant_key"),
                valueType,
                requireScalar(readScalar(
                        result,
                        valueType,
                        "text_value",
                        "identifier_value",
                        "integer_value",
                        "decimal_value",
                        "boolean_value")));
    }

    private static ModuleCatalog.FieldDefinition readFieldDefinition(ResultSet result)
            throws SQLException {
        String dataType = requireString(result, "data_type");
        return new ModuleCatalog.FieldDefinition(
                requireString(result, "field_key"),
                requireString(result, "display_name"),
                dataType,
                requireScalar(readScalar(
                        result,
                        dataType,
                        "default_text",
                        null,
                        "default_integer",
                        "default_decimal",
                        "default_boolean")),
                readBound(result, dataType, "minimum_integer", "minimum_decimal"),
                readBound(result, dataType, "maximum_integer", "maximum_decimal"),
                result.getString("dependent_max_field_key"),
                result.getString("unit"),
                requireString(result, "description"));
    }

    private static ModuleCatalog.EntityTemplateValue readEntityTemplateValue(ResultSet result)
            throws SQLException {
        String valueType = requireString(result, "value_type");
        return new ModuleCatalog.EntityTemplateValue(
                requireString(result, "template_key"),
                requireString(result, "field_key"),
                valueType,
                requireScalar(readScalar(
                        result,
                        valueType,
                        "text_value",
                        null,
                        "integer_value",
                        "decimal_value",
                        "boolean_value")));
    }

    private static ModuleCatalog.EffectParameter readEffectParameter(ResultSet result)
            throws SQLException {
        String dataType = requireString(result, "data_type");
        return new ModuleCatalog.EffectParameter(
                requireString(result, "effect_key"),
                requireString(result, "parameter_key"),
                dataType,
                result.getString("reference_kind"),
                readBound(result, dataType, "minimum_integer", "minimum_decimal"),
                readBound(result, dataType, "maximum_integer", "maximum_decimal"),
                result.getString("text_normalization"),
                nullableBoolean(result, "reject_control_characters"),
                result.getInt("parameter_order"));
    }

    private static ModuleCatalog.CatalogAttribute readCatalogAttribute(ResultSet result)
            throws SQLException {
        String valueType = requireString(result, "value_type");
        return new ModuleCatalog.CatalogAttribute(
                requireString(result, "definition_type"),
                requireString(result, "definition_key"),
                requireString(result, "attribute_key"),
                result.getInt("attribute_order"),
                valueType,
                requireScalar(readScalar(
                        result,
                        valueType,
                        "text_value",
                        "identifier_value",
                        "integer_value",
                        "decimal_value",
                        "boolean_value")));
    }

    private static <T> List<T> query(
            Connection connection,
            String sql,
            long releaseId,
            RowMapper<T> rowMapper) throws SQLException {
        List<T> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, releaseId);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(rowMapper.map(result));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static ModuleCatalog.ScalarValue readScalar(
            ResultSet result,
            String valueType,
            String textColumn,
            String identifierColumn,
            String integerColumn,
            String decimalColumn,
            String booleanColumn) throws SQLException {
        return switch (valueType) {
            case "TEXT" -> {
                String value = result.getString(textColumn);
                yield value == null ? null : new ModuleCatalog.TextValue(value);
            }
            case "IDENTIFIER" -> {
                if (identifierColumn == null) {
                    throw invalidDefinition();
                }
                String value = result.getString(identifierColumn);
                yield value == null ? null : new ModuleCatalog.IdentifierValue(value);
            }
            case "INTEGER" -> {
                Long value = nullableLong(result, integerColumn);
                yield value == null ? null : new ModuleCatalog.IntegerValue(value);
            }
            case "DECIMAL" -> {
                BigDecimal value = result.getBigDecimal(decimalColumn);
                yield value == null ? null : new ModuleCatalog.DecimalValue(value);
            }
            case "BOOLEAN" -> {
                Boolean value = nullableBoolean(result, booleanColumn);
                yield value == null ? null : new ModuleCatalog.BooleanValue(value);
            }
            default -> throw invalidDefinition();
        };
    }

    private static ModuleCatalog.ScalarValue readBound(
            ResultSet result,
            String dataType,
            String integerColumn,
            String decimalColumn) throws SQLException {
        return switch (dataType) {
            case "DECIMAL" -> {
                BigDecimal value = result.getBigDecimal(decimalColumn);
                yield value == null ? null : new ModuleCatalog.DecimalValue(value);
            }
            case "INTEGER", "TEXT" -> {
                Long value = nullableLong(result, integerColumn);
                yield value == null ? null : new ModuleCatalog.IntegerValue(value);
            }
            case "REFERENCE", "BOOLEAN" -> null;
            default -> throw invalidDefinition();
        };
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
        boolean value = result.getBoolean(column);
        return result.wasNull() ? null : value;
    }

    private static String requireString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null) {
            throw invalidDefinition();
        }
        return value;
    }

    private static ModuleCatalog.ScalarValue requireScalar(ModuleCatalog.ScalarValue value)
            throws SQLException {
        if (value == null) {
            throw invalidDefinition();
        }
        return value;
    }

    private static SQLException invalidDefinition() {
        // Do not include database values in exception text; callers only need the failure class.
        return new SQLException("Invalid module definition row");
    }

    private static void rollbackAndRestore(
            Connection connection,
            ConnectionState original,
            Exception primary) {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException rollbackFailure) {
            primary.addSuppressed(rollbackFailure);
        }
        try {
            restoreConnection(connection, original);
        } catch (SQLException restoreFailure) {
            primary.addSuppressed(restoreFailure);
        }
    }

    private static void restoreConnection(Connection connection, ConnectionState original)
            throws SQLException {
        // Return pooled connections with the same observable state in which they were borrowed.
        connection.setAutoCommit(original.autoCommit());
        connection.setTransactionIsolation(original.transactionIsolation());
        connection.setReadOnly(original.readOnly());
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet result) throws SQLException;
    }

    private record ReleaseRow(long id, ModuleCatalog.Release release) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int transactionIsolation) {
        static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(),
                    connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
