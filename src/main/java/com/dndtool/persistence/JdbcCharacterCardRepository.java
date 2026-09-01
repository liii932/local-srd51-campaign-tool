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
import javax.sql.DataSource;

/** Reads one complete card in a repeatable-read transaction without locking or mutation. */
public final class JdbcCharacterCardRepository implements CharacterCardRepository {
    private static final String ROOT_SQL = """
            SELECT cr.id, cr.character_key, cr.character_type, cr.character_name,
                   cr.character_status, cr.row_version,
                   cr.saved_module_key, cr.saved_release_version, cr.saved_content_sha256,
                   cm.frozen_module_key, cm.frozen_release_version, cm.frozen_content_sha256,
                   mr.module_key, mr.release_version, mr.content_sha256, mr.release_status
            FROM character_record AS cr
            JOIN campaign AS c ON c.id = cr.campaign_id AND c.campaign_status = 'ACTIVE'
            JOIN campaign_module AS cm ON cm.campaign_id = cr.campaign_id
            JOIN module_release AS mr ON mr.id = cr.module_release_id
            WHERE cr.character_key = ?
            """;
    private static final String FIELD_SQL = """
            SELECT field_key, value_type, text_value, integer_value,
                   decimal_value, boolean_value
            FROM character_field_value
            WHERE character_id = ?
            ORDER BY field_key
            """;
    private static final String CLASS_SQL = """
            SELECT class_key, class_level FROM character_class_level
            WHERE character_id = ? ORDER BY class_key
            """;
    private static final String SKILL_SQL = """
            SELECT skill_key, proficiency_key FROM character_skill_proficiency
            WHERE character_id = ? ORDER BY skill_key
            """;
    private static final String SAVE_SQL = """
            SELECT save_key, proficiency_key FROM character_save_proficiency
            WHERE character_id = ? ORDER BY save_key
            """;
    private static final String ITEM_SQL = """
            SELECT id, source_kind, item_key, item_name, item_description,
                   quantity, item_status
            FROM item_instance WHERE character_id = ?
            ORDER BY item_status, id
            """;

    private final DataSource dataSource;

    public JdbcCharacterCardRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<Snapshot> findByCharacterKey(String characterKey) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                Root root = findRoot(connection, characterKey);
                if (root == null) {
                    connection.commit();
                    restore(connection, original);
                    return Optional.empty();
                }
                Snapshot snapshot = new Snapshot(
                        root.characterKey(), root.characterType(), root.characterName(),
                        root.characterStatus(), root.rowVersion(), root.binding(),
                        fields(connection, root.id()), classes(connection, root.id()),
                        proficiencies(connection, root.id(), SKILL_SQL, "skill_key"),
                        proficiencies(connection, root.id(), SAVE_SQL, "save_key"),
                        items(connection, root.id()));
                connection.commit();
                restore(connection, original);
                return Optional.of(snapshot);
            } catch (SQLException | RuntimeException exception) {
                rollbackAndRestore(connection, original, exception);
                throw exception;
            }
        }
    }

    private static Root findRoot(Connection connection, String characterKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ROOT_SQL)) {
            statement.setString(1, characterKey);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Root root = new Root(
                        positiveLong(result, "id"), requireString(result, "character_key"),
                        requireString(result, "character_type"),
                        requireString(result, "character_name"),
                        requireString(result, "character_status"),
                        nonNegativeLong(result, "row_version"),
                        new Binding(
                                requireString(result, "saved_module_key"),
                                requireString(result, "saved_release_version"),
                                requireString(result, "saved_content_sha256"),
                                requireString(result, "frozen_module_key"),
                                requireString(result, "frozen_release_version"),
                                requireString(result, "frozen_content_sha256"),
                                requireString(result, "module_key"),
                                requireString(result, "release_version"),
                                requireString(result, "content_sha256"),
                                requireString(result, "release_status")));
                if (result.next()) throw invalidState();
                return root;
            }
        }
    }

    private static List<FieldValue> fields(Connection connection, long characterId)
            throws SQLException {
        List<FieldValue> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, FIELD_SQL, characterId);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String type = requireString(result, "value_type");
                ModuleCatalog.ScalarValue value = switch (type) {
                    case "TEXT" -> new ModuleCatalog.TextValue(
                            requireString(result, "text_value"));
                    case "INTEGER" -> new ModuleCatalog.IntegerValue(
                            requiredLong(result, "integer_value"));
                    case "DECIMAL" -> new ModuleCatalog.DecimalValue(
                            requiredDecimal(result, "decimal_value"));
                    case "BOOLEAN" -> new ModuleCatalog.BooleanValue(
                            requiredBoolean(result, "boolean_value"));
                    default -> throw invalidState();
                };
                values.add(new FieldValue(requireString(result, "field_key"), type, value));
            }
        }
        return List.copyOf(values);
    }

    private static List<ClassLevel> classes(Connection connection, long characterId)
            throws SQLException {
        List<ClassLevel> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, CLASS_SQL, characterId);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                int level = result.getInt("class_level");
                if (result.wasNull() || level < 1 || level > 20) throw invalidState();
                values.add(new ClassLevel(requireString(result, "class_key"), level));
            }
        }
        return List.copyOf(values);
    }

    private static List<Proficiency> proficiencies(
            Connection connection, long characterId, String sql, String keyColumn)
            throws SQLException {
        List<Proficiency> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, sql, characterId);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(new Proficiency(
                        requireString(result, keyColumn),
                        requireString(result, "proficiency_key")));
            }
        }
        return List.copyOf(values);
    }

    private static List<Item> items(Connection connection, long characterId)
            throws SQLException {
        List<Item> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, ITEM_SQL, characterId);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                long id = positiveLong(result, "id");
                String source = requireString(result, "source_kind");
                String itemKey = result.getString("item_key");
                int quantity = result.getInt("quantity");
                if (result.wasNull() || quantity < 1 || quantity > 999
                        || ("MODULE".equals(source) && itemKey == null)
                        || ("TEMPORARY".equals(source) && itemKey != null)) {
                    throw invalidState();
                }
                values.add(new Item(
                        id, source, itemKey, requireString(result, "item_name"),
                        requireString(result, "item_description"), quantity,
                        requireString(result, "item_status")));
            }
        }
        return List.copyOf(values);
    }

    private static PreparedStatement childStatement(
            Connection connection, String sql, long characterId) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setLong(1, characterId);
        statement.setQueryTimeout(5);
        return statement;
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = requiredLong(result, column);
        if (value <= 0) throw invalidState();
        return value;
    }

    private static long nonNegativeLong(ResultSet result, String column) throws SQLException {
        long value = requiredLong(result, column);
        if (value < 0) throw invalidState();
        return value;
    }

    private static long requiredLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) throw invalidState();
        return value;
    }

    private static BigDecimal requiredDecimal(ResultSet result, String column)
            throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        if (value == null) throw invalidState();
        return value;
    }

    private static boolean requiredBoolean(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull() || (value != 0 && value != 1)) throw invalidState();
        return value == 1;
    }

    private static String requireString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null) throw invalidState();
        return value;
    }

    private static SQLException invalidState() {
        return new SQLException("Invalid character card persistence state");
    }

    private static void rollbackAndRestore(
            Connection connection, ConnectionState original, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            failure.addSuppressed(exception);
        }
        try {
            restore(connection, original);
        } catch (SQLException exception) {
            failure.addSuppressed(exception);
        }
    }

    private static void restore(Connection connection, ConnectionState original)
            throws SQLException {
        connection.setAutoCommit(original.autoCommit());
        connection.setReadOnly(original.readOnly());
        connection.setTransactionIsolation(original.isolation());
    }

    private record Root(
            long id,
            String characterKey,
            String characterType,
            String characterName,
            String characterStatus,
            long rowVersion,
            Binding binding) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
