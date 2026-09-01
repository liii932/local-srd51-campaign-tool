package com.dndtool.persistence;

import com.dndtool.service.EntityPositionService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** JDBC boundary for direct positioning without path, movement or encounter automation. */
public final class JdbcEntityPositionCommandRepository implements EntityPositionCommandRepository {
    static final String OPERATION_TYPE = "SET_STAGE3_ENTITY_POSITION";
    static final String EVENT_TYPE = "ENTITY_POSITION_SET";
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STABLE_KEY = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private static final String LOCK_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, campaign_id,
                   result_status, game_event_id
            FROM host_operation
            WHERE request_id = ?
            FOR UPDATE
            """;
    private static final String LOAD_REPLAY_SQL = """
            SELECT event_root.event_sequence, event_root.subject_character_id,
                   event_root.event_text, character_root.character_key
            FROM game_event AS event_root
            JOIN character_record AS character_root
              ON character_root.id = event_root.subject_character_id
             AND character_root.campaign_id = event_root.campaign_id
            WHERE event_root.id = ? AND event_root.campaign_id = ?
              AND event_root.event_type = 'ENTITY_POSITION_SET'
            """;
    private static final String LOCK_ACTIVE_BATTLE_SQL = """
            SELECT id, map_instance_id, module_release_id, map_key
            FROM battle_state
            WHERE campaign_id = ? AND battle_status = 'ACTIVE'
            FOR UPDATE
            """;
    private static final String READ_NODE_SQL = """
            SELECT node_key
            FROM module_map_node
            WHERE module_release_id = ? AND map_key = ? AND node_key = ?
            """;
    private static final String LOCK_PARTICIPANT_POSITION_SQL = """
            SELECT participant.character_id, character_root.character_key, position.node_key
            FROM battle_participant AS participant
            JOIN entity_position AS position
              ON position.battle_id = participant.battle_id
             AND position.campaign_id = participant.campaign_id
             AND position.character_id = participant.character_id
            JOIN character_record AS character_root
              ON character_root.id = participant.character_id
             AND character_root.campaign_id = participant.campaign_id
            WHERE participant.battle_id = ?
              AND participant.campaign_id = ?
              AND position.active_campaign_id = ?
              AND position.map_instance_id = ?
              AND position.module_release_id = ?
              AND position.map_key = ?
              AND participant.character_id = ?
              AND character_root.module_release_id = ?
              AND character_root.character_status = 'ACTIVE'
            FOR UPDATE
            """;
    private static final String UPDATE_ENTITY_POSITION_SQL = """
            UPDATE entity_position
            SET node_key = ?
            WHERE battle_id = ?
              AND campaign_id = ?
              AND active_campaign_id = ?
              AND map_instance_id = ?
              AND module_release_id = ?
              AND map_key = ?
              AND character_id = ?
            """;
    private static final String ADVANCE_EVENT_TAIL_SQL = """
            UPDATE campaign
            SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id, event_text
            ) VALUES (?, ?, 'ENTITY_POSITION_SET', ?, ?)
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, game_event_id, result_status, completed_at
            ) VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    @Override
    public Lookup find(Connection connection, IdempotencyCommand command) throws SQLException {
        validate(command);
        requireCallerTransaction(connection);
        try (PreparedStatement statement = prepare(connection, LOCK_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Lookup.fresh();
                String digest = requiredString(result, "request_digest_sha256");
                String operation = requiredString(result, "operation_type");
                Long campaignId = nullablePositiveLong(result, "campaign_id");
                String status = requiredString(result, "result_status");
                Long gameEventId = nullablePositiveLong(result, "game_event_id");
                if (result.next()) throw invalidState("Direct-position request identity was not unique");
                if (!command.requestDigestSha256().equals(digest)
                        || !OPERATION_TYPE.equals(operation)) {
                    return Lookup.conflict();
                }
                if (campaignId == null || campaignId != command.campaignId()
                        || gameEventId == null || !"SUCCEEDED".equals(status)) {
                    throw invalidState("Direct-position idempotency result is incomplete");
                }
                return Lookup.replay(loadReplay(connection, campaignId, gameEventId));
            }
        }
    }

    @Override
    public MoveResult move(Connection connection, MoveCommand command) throws SQLException {
        validate(command);
        requireCallerTransaction(connection);
        Battle battle = lockActiveBattle(connection, command);
        requireNode(connection, command);
        String currentNode = lockParticipantPosition(connection, command, battle);
        if (command.nodeKey().equals(currentNode)) {
            return new MoveResult(currentNode, command.nodeKey(), false);
        }
        try (PreparedStatement statement = prepare(connection, UPDATE_ENTITY_POSITION_SQL)) {
            statement.setString(1, command.nodeKey());
            statement.setLong(2, battle.id());
            statement.setLong(3, command.campaignId());
            statement.setLong(4, command.campaignId());
            statement.setLong(5, battle.mapInstanceId());
            statement.setLong(6, command.moduleReleaseId());
            statement.setString(7, command.mapKey());
            statement.setLong(8, command.characterId());
            requireOne(statement.executeUpdate(), "Entity position was not updated exactly once");
        }
        return new MoveResult(currentNode, command.nodeKey(), true);
    }

    @Override
    public SavedEvent appendEvent(Connection connection, EventCommand command) throws SQLException {
        validate(command);
        requireCallerTransaction(connection);
        long nextSequence;
        try {
            nextSequence = Math.addExact(command.expectedEventTail(), 1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Event sequence is exhausted", exception);
        }
        try (PreparedStatement statement = prepare(connection, ADVANCE_EVENT_TAIL_SQL)) {
            statement.setLong(1, nextSequence);
            statement.setLong(2, command.campaignId());
            statement.setLong(3, command.expectedEventTail());
            requireOne(statement.executeUpdate(), "Campaign event tail changed before positioning");
        }
        long eventId;
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setLong(1, command.campaignId());
            statement.setLong(2, nextSequence);
            statement.setLong(3, command.characterId());
            statement.setString(4, command.nodeKey());
            requireOne(statement.executeUpdate(), "Direct-position event was not inserted exactly once");
            eventId = generatedKey(statement, "Direct-position event");
        }
        return new SavedEvent(eventId, nextSequence, command.characterId(), command.nodeKey());
    }

    @Override
    public void complete(Connection connection, Completion completion) throws SQLException {
        validate(completion);
        requireCallerTransaction(connection);
        try (PreparedStatement statement = prepare(connection, INSERT_OPERATION_SQL)) {
            statement.setString(1, completion.requestId());
            statement.setString(2, completion.requestDigestSha256());
            statement.setString(3, OPERATION_TYPE);
            statement.setLong(4, completion.campaignId());
            statement.setLong(5, completion.gameEventId());
            requireOne(statement.executeUpdate(), "Direct-position operation was not inserted exactly once");
        }
    }

    private static Replay loadReplay(Connection connection, long campaignId, long eventId)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, LOAD_REPLAY_SQL)) {
            statement.setLong(1, eventId);
            statement.setLong(2, campaignId);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState("Replayed direct-position event is missing");
                long sequence = positiveLong(result, "event_sequence");
                long characterId = positiveLong(result, "subject_character_id");
                String characterKey = requiredString(result, "character_key");
                String nodeKey = requiredString(result, "event_text");
                if (!canonicalUuid(characterKey)
                        || !STABLE_KEY.matcher(nodeKey).matches() || result.next()) {
                    throw invalidState("Replayed direct-position event is invalid");
                }
                return new Replay(eventId, sequence, characterId, characterKey, nodeKey);
            }
        }
    }

    private static Battle lockActiveBattle(Connection connection, MoveCommand command)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, LOCK_ACTIVE_BATTLE_SQL)) {
            statement.setLong(1, command.campaignId());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState("Active encounter is missing");
                Battle battle = new Battle(
                        positiveLong(result, "id"),
                        positiveLong(result, "map_instance_id"),
                        positiveLong(result, "module_release_id"),
                        requiredString(result, "map_key"));
                if (result.next()
                        || battle.moduleReleaseId() != command.moduleReleaseId()
                        || !command.mapKey().equals(battle.mapKey())) {
                    throw invalidState("Active encounter does not match the frozen map");
                }
                return battle;
            }
        }
    }

    private static void requireNode(Connection connection, MoveCommand command) throws SQLException {
        try (PreparedStatement statement = prepare(connection, READ_NODE_SQL)) {
            statement.setLong(1, command.moduleReleaseId());
            statement.setString(2, command.mapKey());
            statement.setString(3, command.nodeKey());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !command.nodeKey().equals(requiredString(result, "node_key"))
                        || result.next()) {
                    throw invalidState("Direct-position destination is not a frozen map node");
                }
            }
        }
    }

    private static String lockParticipantPosition(
            Connection connection, MoveCommand command, Battle battle) throws SQLException {
        try (PreparedStatement statement = prepare(connection, LOCK_PARTICIPANT_POSITION_SQL)) {
            statement.setLong(1, battle.id());
            statement.setLong(2, command.campaignId());
            statement.setLong(3, command.campaignId());
            statement.setLong(4, battle.mapInstanceId());
            statement.setLong(5, command.moduleReleaseId());
            statement.setString(6, command.mapKey());
            statement.setLong(7, command.characterId());
            statement.setLong(8, command.moduleReleaseId());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw invalidState("Position target is not an active encounter participant");
                }
                long characterId = positiveLong(result, "character_id");
                String characterKey = requiredString(result, "character_key");
                String nodeKey = requiredString(result, "node_key");
                if (result.next() || characterId != command.characterId()
                        || !characterKey.equals(command.characterKey())
                        || !STABLE_KEY.matcher(nodeKey).matches()) {
                    throw invalidState("Active encounter participant position is invalid");
                }
                return nodeKey;
            }
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static long generatedKey(PreparedStatement statement, String label) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw invalidState(label + " generated key is missing");
            long id = keys.getLong(1);
            if (keys.wasNull() || id <= 0 || keys.next()) {
                throw invalidState(label + " generated key is invalid");
            }
            return id;
        }
    }

    private static void requireOne(int count, String message) throws SQLException {
        if (count != 1) throw invalidState(message);
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        if (connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw invalidState("Direct positioning requires a writable caller-owned serializable transaction");
        }
    }

    private static void validate(IdempotencyCommand command) {
        if (command == null || !canonicalUuid(command.requestId())
                || !sha256(command.requestDigestSha256()) || command.campaignId() <= 0) {
            throw new IllegalArgumentException("Invalid direct-position idempotency command");
        }
    }

    private static void validate(MoveCommand command) {
        if (command == null || command.campaignId() <= 0 || command.moduleReleaseId() <= 0
                || !EntityPositionService.MODULE_KEY.equals(command.moduleKey())
                || !EntityPositionService.RELEASE_VERSION.equals(command.releaseVersion())
                || !sha256(command.contentSha256())
                || !EntityPositionService.MAP_KEY.equals(command.mapKey())
                || command.characterId() <= 0 || !canonicalUuid(command.characterKey())
                || !stableKey(command.nodeKey())) {
            throw new IllegalArgumentException("Invalid direct-position move command");
        }
    }

    private static void validate(EventCommand command) {
        if (command == null || command.campaignId() <= 0 || command.expectedEventTail() < 0
                || command.expectedEventTail() == Long.MAX_VALUE || command.characterId() <= 0
                || !stableKey(command.nodeKey())) {
            throw new IllegalArgumentException("Invalid direct-position event command");
        }
    }

    private static void validate(Completion completion) {
        if (completion == null || !canonicalUuid(completion.requestId())
                || !sha256(completion.requestDigestSha256())
                || completion.campaignId() <= 0 || completion.gameEventId() <= 0) {
            throw new IllegalArgumentException("Invalid direct-position completion");
        }
    }

    private static boolean canonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sha256(String value) {
        return value != null && SHA_256.matcher(value).matches();
    }

    private static boolean stableKey(String value) {
        return value != null && STABLE_KEY.matcher(value).matches();
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Required direct-position value is missing");
        return value;
    }

    private static Long nullablePositiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) return null;
        if (value <= 0) throw invalidState("Invalid direct-position identity");
        return value;
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        Long value = nullablePositiveLong(result, column);
        if (value == null) throw invalidState("Required direct-position identity is missing");
        return value;
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private record Battle(long id, long mapInstanceId, long moduleReleaseId, String mapKey) {
    }
}
