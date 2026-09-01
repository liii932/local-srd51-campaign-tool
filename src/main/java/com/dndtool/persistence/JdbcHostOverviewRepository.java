package com.dndtool.persistence;

import com.dndtool.service.EncounterStateService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Reads one bounded host overview in a consistent read-only transaction. */
public final class JdbcHostOverviewRepository implements HostOverviewRepository {
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int EVENT_LIMIT = 20;

    private static final String CAMPAIGN_SQL = """
            SELECT campaign_root.id, campaign_root.campaign_key,
                   campaign_root.campaign_name, campaign_root.campaign_status,
                   campaign_root.row_version, campaign_root.host_state_epoch,
                   binding.module_release_id, binding.frozen_module_key,
                   binding.frozen_release_version, binding.frozen_content_sha256,
                   release_root.module_key, release_root.release_version,
                   release_root.content_sha256, release_root.release_status
            FROM campaign AS campaign_root
            JOIN campaign_module AS binding
              ON binding.campaign_id = campaign_root.id
            JOIN module_release AS release_root
              ON release_root.id = binding.module_release_id
            WHERE campaign_root.campaign_status = 'ACTIVE'
            ORDER BY campaign_root.id
            """;
    private static final String CHARACTER_SQL = """
            SELECT character_root.id, character_root.character_key,
                   character_root.character_type, character_root.character_name,
                   character_root.character_status, character_root.row_version,
                   MAX(CASE WHEN field_value.field_key = 'hp.current'
                            THEN field_value.integer_value END) AS current_hp,
                   MAX(CASE WHEN field_value.field_key = 'hp.maximum'
                            THEN field_value.integer_value END) AS maximum_hp,
                   MAX(CASE WHEN field_value.field_key = 'armor_class'
                            THEN field_value.integer_value END) AS armor_class,
                   (SELECT COUNT(*) FROM item_instance AS owned_item
                     WHERE owned_item.character_id = character_root.id) AS item_count
            FROM character_record AS character_root
            LEFT JOIN character_field_value AS field_value
              ON field_value.character_id = character_root.id
             AND field_value.field_key IN ('hp.current', 'hp.maximum', 'armor_class')
            WHERE character_root.campaign_id = ?
            GROUP BY character_root.id, character_root.character_key,
                     character_root.character_type, character_root.character_name,
                     character_root.character_status, character_root.row_version
            ORDER BY character_root.character_status, character_root.character_name,
                     character_root.id
            """;
    private static final String ITEM_SQL = """
            SELECT character_root.character_key, character_root.character_name,
                   owned_item.source_kind, owned_item.item_key, owned_item.item_name,
                   owned_item.item_description, owned_item.quantity, owned_item.item_status
            FROM item_instance AS owned_item
            JOIN character_record AS character_root
              ON character_root.id = owned_item.character_id
            WHERE character_root.campaign_id = ?
            ORDER BY owned_item.item_status, character_root.character_name, owned_item.id
            """;
    private static final String EVENT_SQL = """
            SELECT event_root.event_sequence, event_root.event_type, event_root.event_text,
                   subject_root.character_name AS subject_name,
                   check_root.check_key, check_root.manual_name,
                   check_root.roll_mode_key, check_root.modifier_source_key,
                   check_root.modifier_value, check_root.total_value,
                   check_root.difficulty_class, check_root.check_result
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
            LIMIT 20
            """;
    private static final String MAP_DEFINITION_SQL = """
            SELECT map_type
            FROM module_map_definition
            WHERE module_release_id = ? AND map_key = ?
            """;
    private static final String MAP_NODE_SQL = """
            SELECT node_key, display_name
            FROM module_map_node
            WHERE module_release_id = ? AND map_key = ?
            ORDER BY node_key
            """;
    private static final String MAP_CONNECTION_SQL = """
            SELECT connection_root.endpoint_low_key, low_node.display_name AS low_name,
                   connection_root.endpoint_high_key, high_node.display_name AS high_name
            FROM module_map_connection AS connection_root
            JOIN module_map_node AS low_node
              ON low_node.module_release_id = connection_root.module_release_id
             AND low_node.map_key = connection_root.map_key
             AND low_node.node_key = connection_root.endpoint_low_key
            JOIN module_map_node AS high_node
              ON high_node.module_release_id = connection_root.module_release_id
             AND high_node.map_key = connection_root.map_key
             AND high_node.node_key = connection_root.endpoint_high_key
            WHERE connection_root.module_release_id = ? AND connection_root.map_key = ?
            ORDER BY connection_root.endpoint_low_key, connection_root.endpoint_high_key
            """;
    private static final String MAP_INSTANCE_SQL = """
            SELECT map_instance_root.id, party_position.node_key AS party_node_key,
                   active_battle.id AS battle_id, active_battle.battle_status
            FROM map_instance AS map_instance_root
            LEFT JOIN party_world_position AS party_position
              ON party_position.campaign_id = map_instance_root.campaign_id
             AND party_position.map_instance_id = map_instance_root.id
             AND party_position.module_release_id = map_instance_root.module_release_id
             AND party_position.map_key = map_instance_root.map_key
            LEFT JOIN battle_state AS active_battle
              ON active_battle.campaign_id = map_instance_root.campaign_id
             AND active_battle.map_instance_id = map_instance_root.id
             AND active_battle.module_release_id = map_instance_root.module_release_id
             AND active_battle.map_key = map_instance_root.map_key
             AND active_battle.battle_status = 'ACTIVE'
            WHERE map_instance_root.campaign_id = ?
              AND map_instance_root.module_release_id = ?
              AND map_instance_root.map_key = ?
            """;
    private static final String PARTICIPANT_SQL = """
            SELECT character_root.character_key, character_root.character_name,
                   character_root.character_type, participant.faction,
                   position.node_key, node_root.display_name AS node_name
            FROM battle_participant AS participant
            JOIN character_record AS character_root
              ON character_root.id = participant.character_id
             AND character_root.campaign_id = participant.campaign_id
            JOIN entity_position AS position
              ON position.battle_id = participant.battle_id
             AND position.campaign_id = participant.campaign_id
             AND position.character_id = participant.character_id
            JOIN module_map_node AS node_root
              ON node_root.module_release_id = position.module_release_id
             AND node_root.map_key = position.map_key
             AND node_root.node_key = position.node_key
            WHERE participant.battle_id = ? AND participant.campaign_id = ?
            ORDER BY participant.faction, character_root.character_name, participant.id
            """;

    private final DataSource dataSource;

    public JdbcHostOverviewRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<Snapshot> findActive() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                Root root = activeCampaign(connection);
                if (root == null) {
                    connection.commit();
                    restore(connection, original);
                    return Optional.empty();
                }
                MapState mapState = mapState(connection, root);
                Snapshot snapshot = new Snapshot(
                        root.campaign(), root.binding(),
                        characters(connection, root.id()),
                        items(connection, root.id()),
                        events(connection, root.id()),
                        mapState.map(), mapState.encounter());
                connection.commit();
                restore(connection, original);
                return Optional.of(snapshot);
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private static Root activeCampaign(Connection connection) throws SQLException {
        try (PreparedStatement statement = prepare(connection, CAMPAIGN_SQL)) {
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                long id = positiveLong(result, "id");
                Campaign campaign = new Campaign(
                        requiredString(result, "campaign_key"),
                        requiredString(result, "campaign_name"),
                        requiredString(result, "campaign_status"),
                        nonNegativeLong(result, "row_version"),
                        nonNegativeLong(result, "host_state_epoch"));
                Binding binding = new Binding(
                        positiveLong(result, "module_release_id"),
                        requiredString(result, "frozen_module_key"),
                        requiredString(result, "frozen_release_version"),
                        requiredString(result, "frozen_content_sha256"),
                        requiredString(result, "module_key"),
                        requiredString(result, "release_version"),
                        requiredString(result, "content_sha256"),
                        requiredString(result, "release_status"));
                if (result.next()) throw invalidState("More than one active campaign exists");
                return new Root(id, campaign, binding);
            }
        }
    }

    private static List<CharacterSummary> characters(Connection connection, long campaignId)
            throws SQLException {
        List<CharacterSummary> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, CHARACTER_SQL)) {
            statement.setLong(1, campaignId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    positiveLong(result, "id");
                    values.add(new CharacterSummary(
                            requiredString(result, "character_key"),
                            requiredString(result, "character_type"),
                            requiredString(result, "character_name"),
                            requiredString(result, "character_status"),
                            nonNegativeLong(result, "row_version"),
                            nullableLong(result, "current_hp"),
                            nullableLong(result, "maximum_hp"),
                            nullableLong(result, "armor_class"),
                            nonNegativeInt(result, "item_count")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<ItemSummary> items(Connection connection, long campaignId)
            throws SQLException {
        List<ItemSummary> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, ITEM_SQL)) {
            statement.setLong(1, campaignId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new ItemSummary(
                            requiredString(result, "character_key"),
                            requiredString(result, "character_name"),
                            requiredString(result, "source_kind"),
                            result.getString("item_key"),
                            requiredString(result, "item_name"),
                            requiredNonNullString(result, "item_description"),
                            positiveInt(result, "quantity"),
                            requiredString(result, "item_status")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<EventSummary> events(Connection connection, long campaignId)
            throws SQLException {
        List<EventSummary> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, EVENT_SQL)) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(EVENT_LIMIT);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new EventSummary(
                            positiveLong(result, "event_sequence"),
                            requiredString(result, "event_type"),
                            result.getString("subject_name"),
                            result.getString("event_text"),
                            result.getString("check_key"),
                            result.getString("manual_name"),
                            result.getString("roll_mode_key"),
                            result.getString("modifier_source_key"),
                            nullableInt(result, "modifier_value"),
                            nullableInt(result, "total_value"),
                            nullableInt(result, "difficulty_class"),
                            result.getString("check_result")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static MapState mapState(Connection connection, Root root) throws SQLException {
        String mapKey = EncounterStateService.MAP_KEY;
        String mapType;
        try (PreparedStatement statement = prepare(connection, MAP_DEFINITION_SQL)) {
            statement.setLong(1, root.binding().moduleReleaseId());
            statement.setString(2, mapKey);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState("Frozen host map definition is missing");
                mapType = requiredString(result, "map_type");
                if (result.next()) throw invalidState("Frozen host map definition is not unique");
            }
        }
        List<MapNode> nodes = nodes(connection, root.binding().moduleReleaseId(), mapKey);
        List<MapConnection> connections = connections(
                connection, root.binding().moduleReleaseId(), mapKey);
        Instance instance = instance(connection, root, mapKey);
        Encounter encounter = instance == null || instance.battleId() == null
                ? new Encounter(null, List.of())
                : new Encounter(instance.battleStatus(), participants(
                        connection, instance.battleId(), root.id()));
        return new MapState(
                new MapView(
                        mapKey, mapType, instance != null,
                        instance == null ? null : instance.partyNodeKey(), nodes, connections),
                encounter);
    }

    private static List<MapNode> nodes(Connection connection, long releaseId, String mapKey)
            throws SQLException {
        List<MapNode> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, MAP_NODE_SQL)) {
            statement.setLong(1, releaseId);
            statement.setString(2, mapKey);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new MapNode(
                            requiredString(result, "node_key"),
                            requiredString(result, "display_name")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<MapConnection> connections(
            Connection connection, long releaseId, String mapKey) throws SQLException {
        List<MapConnection> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, MAP_CONNECTION_SQL)) {
            statement.setLong(1, releaseId);
            statement.setString(2, mapKey);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new MapConnection(
                            requiredString(result, "endpoint_low_key"),
                            requiredString(result, "low_name"),
                            requiredString(result, "endpoint_high_key"),
                            requiredString(result, "high_name")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static Instance instance(Connection connection, Root root, String mapKey)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, MAP_INSTANCE_SQL)) {
            statement.setLong(1, root.id());
            statement.setLong(2, root.binding().moduleReleaseId());
            statement.setString(3, mapKey);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                positiveLong(result, "id");
                String partyNode = result.getString("party_node_key");
                Long battleId = nullablePositiveLong(result, "battle_id");
                String battleStatus = result.getString("battle_status");
                if ((battleId == null) != (battleStatus == null) || result.next()) {
                    throw invalidState("Host map instance state is invalid");
                }
                return new Instance(partyNode, battleId, battleStatus);
            }
        }
    }

    private static List<Participant> participants(
            Connection connection, long battleId, long campaignId) throws SQLException {
        List<Participant> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, PARTICIPANT_SQL)) {
            statement.setLong(1, battleId);
            statement.setLong(2, campaignId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new Participant(
                            requiredString(result, "character_key"),
                            requiredString(result, "character_name"),
                            requiredString(result, "character_type"),
                            requiredString(result, "faction"),
                            requiredString(result, "node_key"),
                            requiredString(result, "node_name")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Required host overview text is missing");
        return value;
    }

    private static String requiredNonNullString(ResultSet result, String column)
            throws SQLException {
        String value = result.getString(column);
        if (value == null) throw invalidState("Required host overview text is missing");
        return value;
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value <= 0) throw invalidState("Invalid host overview identity");
        return value;
    }

    private static Long nullablePositiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) return null;
        if (value <= 0) throw invalidState("Invalid host overview identity");
        return value;
    }

    private static long nonNegativeLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value < 0) throw invalidState("Invalid host overview counter");
        return value;
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static int positiveInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull() || value <= 0) throw invalidState("Invalid host overview value");
        return value;
    }

    private static int nonNegativeInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull() || value < 0) throw invalidState("Invalid host overview count");
        return value;
    }

    private static Integer nullableInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
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

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private record Root(long id, Campaign campaign, Binding binding) {
    }

    private record Instance(String partyNodeKey, Long battleId, String battleStatus) {
    }

    private record MapState(MapView map, Encounter encounter) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
