package com.dndtool.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Reads one campaign archive snapshot in a consistent, non-locking transaction. */
public final class JdbcCampaignArchiveRepository implements CampaignArchiveRepository {
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int EVENT_LIMIT = 50;

    private static final String CAMPAIGN_SQL = """
            SELECT campaign_root.id, campaign_root.campaign_key,
                   campaign_root.campaign_name, campaign_root.campaign_status,
                   binding.module_release_id, binding.frozen_module_key,
                   binding.frozen_release_version, binding.frozen_content_sha256,
                   release_root.module_key, release_root.release_version,
                   release_root.canonical_format_version, release_root.hash_algorithm,
                   release_root.content_sha256, release_root.release_status
            FROM campaign AS campaign_root
            JOIN campaign_module AS binding
              ON binding.campaign_id = campaign_root.id
            JOIN module_release AS release_root
              ON release_root.id = binding.module_release_id
            WHERE campaign_root.campaign_key = ?
            """;
    private static final String CHARACTER_SQL = """
            SELECT character_root.character_key, character_root.character_type,
                   character_root.character_name, character_root.character_status,
                   character_root.saved_module_key,
                   character_root.saved_release_version,
                   character_root.saved_content_sha256
            FROM character_record AS character_root
            WHERE character_root.campaign_id = ?
              AND character_root.module_release_id = ?
            ORDER BY character_root.character_key
            """;
    private static final String FIELD_SQL = """
            SELECT character_root.character_key, field_value.field_key,
                   field_value.value_type, field_value.text_value,
                   field_value.integer_value, field_value.decimal_value,
                   field_value.boolean_value
            FROM character_field_value AS field_value
            JOIN character_record AS character_root
              ON character_root.id = field_value.character_id
             AND character_root.module_release_id = field_value.module_release_id
            WHERE character_root.campaign_id = ?
              AND field_value.module_release_id = ?
            ORDER BY character_root.character_key, field_value.field_key
            """;
    private static final String CLASS_SQL = """
            SELECT character_root.character_key, class_level.class_key,
                   class_level.class_level
            FROM character_class_level AS class_level
            JOIN character_record AS character_root
              ON character_root.id = class_level.character_id
             AND character_root.module_release_id = class_level.module_release_id
            WHERE character_root.campaign_id = ?
              AND class_level.module_release_id = ?
            ORDER BY character_root.character_key, class_level.class_key
            """;
    private static final String SKILL_SQL = """
            SELECT character_root.character_key, proficiency.skill_key AS target_key,
                   proficiency.proficiency_key
            FROM character_skill_proficiency AS proficiency
            JOIN character_record AS character_root
              ON character_root.id = proficiency.character_id
             AND character_root.module_release_id = proficiency.module_release_id
            WHERE character_root.campaign_id = ?
              AND proficiency.module_release_id = ?
            ORDER BY character_root.character_key, proficiency.skill_key
            """;
    private static final String SAVE_SQL = """
            SELECT character_root.character_key, proficiency.save_key AS target_key,
                   proficiency.proficiency_key
            FROM character_save_proficiency AS proficiency
            JOIN character_record AS character_root
              ON character_root.id = proficiency.character_id
             AND character_root.module_release_id = proficiency.module_release_id
            WHERE character_root.campaign_id = ?
              AND proficiency.module_release_id = ?
            ORDER BY character_root.character_key, proficiency.save_key
            """;
    private static final String ITEM_SQL = """
            SELECT character_root.character_key, owned_item.source_kind,
                   owned_item.module_release_id, owned_item.item_key,
                   owned_item.item_name, owned_item.item_description,
                   owned_item.quantity, owned_item.item_status
            FROM item_instance AS owned_item
            JOIN character_record AS character_root
              ON character_root.id = owned_item.character_id
            WHERE character_root.campaign_id = ?
            ORDER BY character_root.character_key, owned_item.id
            """;
    private static final String MAP_SQL = """
            SELECT map_root.id, map_root.module_release_id,
                   map_root.map_key, map_root.map_type,
                   party_position.node_key AS party_node_key,
                   active_battle.id AS battle_id,
                   active_battle.battle_status
            FROM map_instance AS map_root
            LEFT JOIN party_world_position AS party_position
              ON party_position.campaign_id = map_root.campaign_id
             AND party_position.map_instance_id = map_root.id
             AND party_position.module_release_id = map_root.module_release_id
             AND party_position.map_key = map_root.map_key
            LEFT JOIN battle_state AS active_battle
              ON active_battle.campaign_id = map_root.campaign_id
             AND active_battle.map_instance_id = map_root.id
             AND active_battle.module_release_id = map_root.module_release_id
             AND active_battle.map_key = map_root.map_key
             AND active_battle.battle_status = 'ACTIVE'
            WHERE map_root.campaign_id = ?
            ORDER BY map_root.map_key
            """;
    private static final String PARTICIPANT_SQL = """
            SELECT character_root.character_key, participant.faction,
                   position.node_key
            FROM battle_participant AS participant
            JOIN character_record AS character_root
              ON character_root.id = participant.character_id
             AND character_root.campaign_id = participant.campaign_id
            JOIN entity_position AS position
              ON position.battle_id = participant.battle_id
             AND position.campaign_id = participant.campaign_id
             AND position.character_id = participant.character_id
            WHERE participant.battle_id = ?
              AND participant.campaign_id = ?
              AND position.map_instance_id = ?
              AND position.module_release_id = ?
              AND position.map_key = ?
            ORDER BY character_root.character_key
            """;
    private static final String EVENT_SQL = """
            SELECT event_root.event_sequence, event_root.event_type,
                   event_root.event_text,
                   subject_root.character_key AS subject_character_key,
                   check_root.id AS check_id, check_root.module_release_id,
                   check_root.event_key, check_root.check_key,
                   check_root.roll_mode_key, check_root.modifier_source_key,
                   check_root.manual_name, check_root.modifier_value,
                   check_root.total_value, check_root.difficulty_class,
                   check_root.check_result
            FROM game_event AS event_root
            LEFT JOIN character_record AS subject_root
              ON subject_root.id = event_root.subject_character_id
             AND subject_root.campaign_id = event_root.campaign_id
            LEFT JOIN check_execution AS check_root
              ON check_root.game_event_id = event_root.id
             AND check_root.campaign_id = event_root.campaign_id
            WHERE event_root.campaign_id = ?
              AND (check_root.id IS NOT NULL OR event_root.event_type = 'NOTE')
            ORDER BY event_root.event_sequence DESC
            LIMIT 50
            """;

    private final DataSource dataSource;

    public JdbcCampaignArchiveRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<Snapshot> findByCampaignKey(String campaignKey) throws SQLException {
        if (!isCanonicalVersionFourUuid(campaignKey)) {
            throw new IllegalArgumentException("Invalid campaign archive key");
        }
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                Root root = campaign(connection, campaignKey);
                if (root == null) {
                    connection.commit();
                    restore(connection, original);
                    return Optional.empty();
                }
                Snapshot snapshot = new Snapshot(
                        root.campaign(), root.module(),
                        characters(connection, root),
                        fields(connection, root),
                        classLevels(connection, root),
                        proficiencies(connection, root, SKILL_SQL),
                        proficiencies(connection, root, SAVE_SQL),
                        items(connection, root),
                        maps(connection, root),
                        recentEvents(connection, root));
                connection.commit();
                restore(connection, original);
                return Optional.of(snapshot);
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private static Root campaign(Connection connection, String campaignKey) throws SQLException {
        try (PreparedStatement statement = prepare(connection, CAMPAIGN_SQL)) {
            statement.setString(1, campaignKey);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Root root = new Root(
                        positiveLong(result, "id"),
                        positiveLong(result, "module_release_id"),
                        new Campaign(
                                requiredString(result, "campaign_key"),
                                requiredString(result, "campaign_name"),
                                requiredString(result, "campaign_status")),
                        new ModuleBinding(
                                requiredString(result, "frozen_module_key"),
                                requiredString(result, "frozen_release_version"),
                                requiredString(result, "frozen_content_sha256"),
                                requiredString(result, "module_key"),
                                requiredString(result, "release_version"),
                                positiveInt(result, "canonical_format_version"),
                                requiredString(result, "hash_algorithm"),
                                requiredString(result, "content_sha256"),
                                requiredString(result, "release_status")));
                if (result.next()) throw invalidState("Duplicate campaign archive root");
                return root;
            }
        }
    }

    private static List<CharacterState> characters(Connection connection, Root root)
            throws SQLException {
        List<CharacterState> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, CHARACTER_SQL, root);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(new CharacterState(
                        requiredString(result, "character_key"),
                        requiredString(result, "character_type"),
                        requiredString(result, "character_name"),
                        requiredString(result, "character_status"),
                        requiredString(result, "saved_module_key"),
                        requiredString(result, "saved_release_version"),
                        requiredString(result, "saved_content_sha256")));
            }
        }
        return List.copyOf(values);
    }

    private static List<FieldValue> fields(Connection connection, Root root)
            throws SQLException {
        List<FieldValue> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, FIELD_SQL, root);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                FieldValue value = new FieldValue(
                        requiredString(result, "character_key"),
                        requiredString(result, "field_key"),
                        requiredString(result, "value_type"),
                        result.getString("text_value"),
                        nullableLong(result, "integer_value"),
                        result.getBigDecimal("decimal_value"),
                        nullableBoolean(result, "boolean_value"));
                if (!hasExactlyOneTypedValue(value)) {
                    throw invalidState("Invalid typed campaign archive field");
                }
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static List<ClassLevel> classLevels(Connection connection, Root root)
            throws SQLException {
        List<ClassLevel> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, CLASS_SQL, root);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(new ClassLevel(
                        requiredString(result, "character_key"),
                        requiredString(result, "class_key"),
                        positiveInt(result, "class_level")));
            }
        }
        return List.copyOf(values);
    }

    private static List<Proficiency> proficiencies(
            Connection connection, Root root, String sql) throws SQLException {
        List<Proficiency> values = new ArrayList<>();
        try (PreparedStatement statement = childStatement(connection, sql, root);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(new Proficiency(
                        requiredString(result, "character_key"),
                        requiredString(result, "target_key"),
                        requiredString(result, "proficiency_key")));
            }
        }
        return List.copyOf(values);
    }

    private static List<ItemState> items(Connection connection, Root root) throws SQLException {
        List<ItemState> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, ITEM_SQL)) {
            statement.setLong(1, root.campaignId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String sourceKind = requiredString(result, "source_kind");
                    Long moduleReleaseId = nullablePositiveLong(result, "module_release_id");
                    String itemKey = result.getString("item_key");
                    boolean validSource = "MODULE".equals(sourceKind)
                            ? moduleReleaseId != null
                                && moduleReleaseId == root.moduleReleaseId()
                                && itemKey != null
                            : "TEMPORARY".equals(sourceKind)
                                && moduleReleaseId == null && itemKey == null;
                    if (!validSource) throw invalidState("Invalid campaign archive item source");
                    values.add(new ItemState(
                            requiredString(result, "character_key"), sourceKind, itemKey,
                            requiredString(result, "item_name"),
                            requiredNonNullString(result, "item_description"),
                            positiveInt(result, "quantity"),
                            requiredString(result, "item_status")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<MapState> maps(Connection connection, Root root) throws SQLException {
        List<MapRoot> roots = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, MAP_SQL)) {
            statement.setLong(1, root.campaignId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long mapId = positiveLong(result, "id");
                    long moduleReleaseId = positiveLong(result, "module_release_id");
                    if (moduleReleaseId != root.moduleReleaseId()) {
                        throw invalidState("Campaign archive map release mismatch");
                    }
                    Long battleId = nullablePositiveLong(result, "battle_id");
                    String battleStatus = result.getString("battle_status");
                    if ((battleId == null) != (battleStatus == null)) {
                        throw invalidState("Campaign archive encounter is incomplete");
                    }
                    roots.add(new MapRoot(
                            mapId,
                            requiredString(result, "map_key"),
                            requiredString(result, "map_type"),
                            requiredString(result, "party_node_key"),
                            battleId,
                            battleStatus));
                }
            }
        }

        List<MapState> values = new ArrayList<>();
        for (MapRoot map : roots) {
            Encounter encounter = map.battleId() == null ? null : new Encounter(
                    map.battleStatus(), participants(connection, root, map));
            values.add(new MapState(
                    map.mapKey(), map.mapType(), map.partyNodeKey(), encounter));
        }
        return List.copyOf(values);
    }

    private static List<Participant> participants(
            Connection connection, Root root, MapRoot map) throws SQLException {
        List<Participant> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, PARTICIPANT_SQL)) {
            statement.setLong(1, map.battleId());
            statement.setLong(2, root.campaignId());
            statement.setLong(3, map.mapId());
            statement.setLong(4, root.moduleReleaseId());
            statement.setString(5, map.mapKey());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new Participant(
                            requiredString(result, "character_key"),
                            requiredString(result, "faction"),
                            requiredString(result, "node_key")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<EventSnapshot> recentEvents(Connection connection, Root root)
            throws SQLException {
        List<EventSnapshot> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, EVENT_SQL)) {
            statement.setLong(1, root.campaignId());
            statement.setMaxRows(EVENT_LIMIT);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Long checkId = nullablePositiveLong(result, "check_id");
                    Long checkReleaseId = nullablePositiveLong(result, "module_release_id");
                    CheckSnapshot check = null;
                    if (checkId != null) {
                        if (checkReleaseId == null
                                || checkReleaseId != root.moduleReleaseId()) {
                            throw invalidState("Campaign archive check release mismatch");
                        }
                        check = new CheckSnapshot(
                                result.getString("event_key"),
                                requiredString(result, "check_key"),
                                requiredString(result, "roll_mode_key"),
                                result.getString("modifier_source_key"),
                                result.getString("manual_name"),
                                requiredInt(result, "modifier_value"),
                                requiredInt(result, "total_value"),
                                requiredInt(result, "difficulty_class"),
                                requiredString(result, "check_result"));
                    } else if (checkReleaseId != null) {
                        throw invalidState("Campaign archive check identity is incomplete");
                    }
                    values.add(new EventSnapshot(
                            positiveLong(result, "event_sequence"),
                            requiredString(result, "event_type"),
                            result.getString("subject_character_key"),
                            result.getString("event_text"),
                            check));
                }
            }
        }
        // SQL selects the newest bounded window; archive JSON is chronological.
        Collections.reverse(values);
        return List.copyOf(values);
    }

    private static PreparedStatement childStatement(
            Connection connection, String sql, Root root) throws SQLException {
        PreparedStatement statement = prepare(connection, sql);
        statement.setLong(1, root.campaignId());
        statement.setLong(2, root.moduleReleaseId());
        return statement;
    }

    private static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static boolean hasExactlyOneTypedValue(FieldValue value) {
        int count = (value.textValue() == null ? 0 : 1)
                + (value.integerValue() == null ? 0 : 1)
                + (value.decimalValue() == null ? 0 : 1)
                + (value.booleanValue() == null ? 0 : 1);
        if (count != 1) return false;
        return switch (value.valueType()) {
            case "TEXT" -> value.textValue() != null;
            case "INTEGER" -> value.integerValue() != null;
            case "DECIMAL" -> value.decimalValue() != null;
            case "BOOLEAN" -> value.booleanValue() != null;
            default -> false;
        };
    }

    private static boolean isCanonicalVersionFourUuid(String value) {
        if (value == null) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Required archive text is missing");
        return value;
    }

    private static String requiredNonNullString(ResultSet result, String column)
            throws SQLException {
        String value = result.getString(column);
        if (value == null) throw invalidState("Required archive text is missing");
        return value;
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value <= 0) throw invalidState("Invalid archive identity");
        return value;
    }

    private static Long nullablePositiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) return null;
        if (value <= 0) throw invalidState("Invalid archive identity");
        return value;
    }

    private static int positiveInt(ResultSet result, String column) throws SQLException {
        int value = requiredInt(result, column);
        if (value <= 0) throw invalidState("Invalid archive value");
        return value;
    }

    private static int requiredInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull()) throw invalidState("Required archive number is missing");
        return value;
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull()) return null;
        if (value == 0) return false;
        if (value == 1) return true;
        throw invalidState("Invalid archive boolean");
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private static void rollbackAndRestore(
            Connection connection, ConnectionState original, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        try {
            restore(connection, original);
        } catch (SQLException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private static void restore(Connection connection, ConnectionState original)
            throws SQLException {
        connection.setAutoCommit(original.autoCommit());
        connection.setReadOnly(original.readOnly());
        connection.setTransactionIsolation(original.isolation());
    }

    private record Root(
            long campaignId,
            long moduleReleaseId,
            Campaign campaign,
            ModuleBinding module) {
    }

    private record MapRoot(
            long mapId,
            String mapKey,
            String mapType,
            String partyNodeKey,
            Long battleId,
            String battleStatus) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
