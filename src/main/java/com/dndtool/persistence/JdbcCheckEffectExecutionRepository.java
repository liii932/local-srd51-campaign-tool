package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** JDBC implementation for the host-command effects that mutate existing authoritative state. */
public final class JdbcCheckEffectExecutionRepository
        implements CheckEffectExecutionRepository {
    private static final String LOAD_RESULT_SQL = """
            SELECT check_result
            FROM check_execution
            WHERE id = ? AND campaign_id = ? AND module_release_id = ? AND game_event_id = ?
            """;
    private static final String LOCK_HP_SQL = """
            SELECT field_key, integer_value
            FROM character_field_value
            WHERE character_id = ?
              AND module_release_id = ?
              AND field_key IN ('hp.current', 'hp.maximum')
              AND value_type = 'INTEGER'
            ORDER BY field_key
            FOR UPDATE
            """;
    private static final String UPDATE_HP_SQL = """
            UPDATE character_field_value
            SET integer_value = ?
            WHERE character_id = ? AND module_release_id = ?
              AND field_key = 'hp.current' AND value_type = 'INTEGER'
            """;
    private static final String INSERT_MODULE_ITEM_SQL = """
            INSERT INTO item_instance (
                character_id, source_kind, module_release_id, item_key,
                item_name, item_description, quantity
            ) VALUES (?, 'MODULE', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_TEMPORARY_ITEM_SQL = """
            INSERT INTO item_instance (
                character_id, source_kind, module_release_id, item_key,
                item_name, item_description, quantity
            ) VALUES (?, 'TEMPORARY', NULL, NULL, ?, ?, ?)
            """;
    private static final String INSERT_INTEGER_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_integer, new_integer
            ) VALUES (?, ?, ?, ?, ?, 'INTEGER', ?, ?)
            """;
    private static final String INSERT_REFERENCE_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_reference, new_reference
            ) VALUES (?, ?, ?, ?, ?, 'REFERENCE', NULL, ?)
            """;
    private static final String INSERT_TEXT_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_text, new_text
            ) VALUES (?, ?, ?, ?, 'item.temporary', 'TEXT', NULL, ?)
            """;
    private static final String UPDATE_EVENT_MESSAGE_SQL = """
            UPDATE game_event
            SET event_text = ?
            WHERE id = ? AND campaign_id = ?
              AND event_type = 'CHECK_EXECUTED' AND event_text IS NULL
            """;
    private static final String LOCK_ACTIVE_BATTLE_SQL = """
            SELECT id, map_instance_id, module_release_id, map_key
            FROM battle_state
            WHERE campaign_id = ? AND battle_status = 'ACTIVE'
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

    @Override
    public void preflightPositions(Connection connection, PositionPreflight preflight)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        validatePreflight(preflight);
        requireCallerTransaction(connection);
        preflightPositionScope(connection, preflight);
    }

    @Override
    public AppliedEffects execute(Connection connection, Command command) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        validate(command);
        requireCallerTransaction(connection);

        PositionScope positionScope = preflightPositionScope(
                connection,
                new PositionPreflight(
                        command.campaignId(), command.moduleReleaseId(),
                        command.success(), command.failure()));

        CheckEffectPlanRepository.EffectBranch selected = loadResultBranch(connection, command);
        BranchActions branch = selected == CheckEffectPlanRepository.EffectBranch.SUCCESS
                ? command.success() : command.failure();
        int changeOrder = 1;
        List<Long> itemIds = new ArrayList<>();
        Set<Long> modifiedCharacterIds = new LinkedHashSet<>();
        boolean messageWritten = false;
        int positionChangeCount = 0;
        for (Action action : branch.actions()) {
            if (action instanceof AdjustCurrentHp hp) {
                if (adjustHp(connection, command, hp, changeOrder)) {
                    modifiedCharacterIds.add(hp.targetCharacterId());
                    changeOrder++;
                }
            } else if (action instanceof GrantModuleItem item) {
                itemIds.add(insertModuleItem(connection, command, item));
                insertReferenceChange(connection, command, item, changeOrder++);
                modifiedCharacterIds.add(item.targetCharacterId());
            } else if (action instanceof GrantTemporaryItem item) {
                itemIds.add(insertTemporaryItem(connection, item));
                insertTextChange(connection, command, item, changeOrder++);
                modifiedCharacterIds.add(item.targetCharacterId());
            } else if (action instanceof SetEntityPosition position) {
                if (updateEntityPosition(connection, command, positionScope, position)) {
                    positionChangeCount++;
                    modifiedCharacterIds.add(position.targetCharacterId());
                }
            } else if (action instanceof AppendEventMessage message) {
                if (messageWritten) throw invalidState("Message effect was selected twice");
                requireOne(updateEventMessage(connection, command, message));
                messageWritten = true;
            } else {
                throw invalidState("Unsupported host command effect action");
            }
        }
        return new AppliedEffects(
                selected, changeOrder - 1, positionChangeCount,
                itemIds, modifiedCharacterIds, messageWritten);
    }

    private static PositionScope preflightPositionScope(
            Connection connection,
            PositionPreflight preflight) throws SQLException {
        List<SetEntityPosition> positions = positionActions(preflight);
        if (positions.isEmpty()) return null;

        BattleContext battle = lockActiveBattle(connection, preflight);
        Set<String> requiredNodes = new HashSet<>();
        Set<Long> requiredCharacters = new HashSet<>();
        for (SetEntityPosition position : positions) {
            if (!battle.mapKey().equals(position.mapKey())) {
                throw invalidState("Position effect references another map");
            }
            requiredNodes.add(position.nodeKey());
            requiredCharacters.add(position.targetCharacterId());
        }
        requireFrozenNodes(connection, preflight.moduleReleaseId(), battle.mapKey(), requiredNodes);
        Map<Long, String> currentNodes = lockParticipants(
                connection, preflight, battle, requiredCharacters);
        return new PositionScope(battle, new HashMap<>(currentNodes));
    }

    private static List<SetEntityPosition> positionActions(PositionPreflight preflight) {
        List<SetEntityPosition> positions = new ArrayList<>();
        for (Action action : preflight.success().actions()) {
            if (action instanceof SetEntityPosition position) positions.add(position);
        }
        for (Action action : preflight.failure().actions()) {
            if (action instanceof SetEntityPosition position) positions.add(position);
        }
        return List.copyOf(positions);
    }

    private static BattleContext lockActiveBattle(
            Connection connection,
            PositionPreflight preflight) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ACTIVE_BATTLE_SQL)) {
            statement.setLong(1, preflight.campaignId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState("Active encounter is missing");
                BattleContext battle = new BattleContext(
                        requiredLong(result, "id"),
                        requiredLong(result, "map_instance_id"),
                        requiredLong(result, "module_release_id"),
                        result.getString("map_key"));
                if (result.next()
                        || battle.moduleReleaseId() != preflight.moduleReleaseId()
                        || !stableKey(battle.mapKey())) {
                    throw invalidState("Active encounter does not match the frozen module");
                }
                return battle;
            }
        }
    }

    private static void requireFrozenNodes(
            Connection connection,
            long moduleReleaseId,
            String mapKey,
            Set<String> requiredNodes) throws SQLException {
        List<String> nodes = requiredNodes.stream().sorted().toList();
        String placeholders = String.join(", ", Collections.nCopies(nodes.size(), "?"));
        String sql = "SELECT node_key FROM module_map_node "
                + "WHERE module_release_id = ? AND map_key = ? "
                + "AND node_key IN (" + placeholders + ") ORDER BY node_key";
        Set<String> found = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, moduleReleaseId);
            statement.setString(2, mapKey);
            int parameter = 3;
            for (String node : nodes) statement.setString(parameter++, node);
            statement.setMaxRows(nodes.size() + 1);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) found.add(result.getString("node_key"));
            }
        }
        if (!found.equals(requiredNodes)) {
            throw invalidState("Position effect references an unknown frozen node");
        }
    }

    private static Map<Long, String> lockParticipants(
            Connection connection,
            PositionPreflight preflight,
            BattleContext battle,
            Set<Long> requiredCharacters) throws SQLException {
        List<Long> characterIds = requiredCharacters.stream().sorted().toList();
        String placeholders = String.join(", ", Collections.nCopies(characterIds.size(), "?"));
        String sql = """
                SELECT participant.character_id, position.node_key
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
                  AND character_root.module_release_id = ?
                  AND character_root.character_status = 'ACTIVE'
                  AND participant.character_id IN (%s)
                ORDER BY participant.character_id
                FOR UPDATE
                """.formatted(placeholders);
        Map<Long, String> currentNodes = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, battle.battleId());
            statement.setLong(2, preflight.campaignId());
            statement.setLong(3, preflight.campaignId());
            statement.setLong(4, battle.mapInstanceId());
            statement.setLong(5, preflight.moduleReleaseId());
            statement.setString(6, battle.mapKey());
            statement.setLong(7, preflight.moduleReleaseId());
            int parameter = 8;
            for (long characterId : characterIds) statement.setLong(parameter++, characterId);
            statement.setMaxRows(characterIds.size() + 1);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long characterId = requiredLong(result, "character_id");
                    String nodeKey = result.getString("node_key");
                    if (nodeKey == null || currentNodes.putIfAbsent(characterId, nodeKey) != null) {
                        throw invalidState("Active encounter participant position is invalid");
                    }
                }
            }
        }
        if (!currentNodes.keySet().equals(requiredCharacters)) {
            throw invalidState("Position target is not an active encounter participant");
        }
        return Map.copyOf(currentNodes);
    }

    private static boolean updateEntityPosition(
            Connection connection,
            Command command,
            PositionScope scope,
            SetEntityPosition action) throws SQLException {
        if (scope == null || !scope.currentNodes().containsKey(action.targetCharacterId())) {
            throw invalidState("Position target was not preflighted");
        }
        String currentNode = scope.currentNodes().get(action.targetCharacterId());
        if (action.nodeKey().equals(currentNode)) return false;
        BattleContext battle = scope.battle();
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_ENTITY_POSITION_SQL)) {
            statement.setString(1, action.nodeKey());
            statement.setLong(2, battle.battleId());
            statement.setLong(3, command.campaignId());
            statement.setLong(4, command.campaignId());
            statement.setLong(5, battle.mapInstanceId());
            statement.setLong(6, command.moduleReleaseId());
            statement.setString(7, battle.mapKey());
            statement.setLong(8, action.targetCharacterId());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
        scope.currentNodes().put(action.targetCharacterId(), action.nodeKey());
        return true;
    }

    private static CheckEffectPlanRepository.EffectBranch loadResultBranch(
            Connection connection, Command command) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_RESULT_SQL)) {
            statement.setLong(1, command.checkExecutionId());
            statement.setLong(2, command.campaignId());
            statement.setLong(3, command.moduleReleaseId());
            statement.setLong(4, command.gameEventId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState("Check execution is missing");
                String value = result.getString("check_result");
                if (value == null || result.next()) throw invalidState("Check result is invalid");
                return switch (value) {
                    case "SUCCESS" -> CheckEffectPlanRepository.EffectBranch.SUCCESS;
                    case "FAILURE" -> CheckEffectPlanRepository.EffectBranch.FAILURE;
                    default -> throw invalidState("Check result is invalid");
                };
            }
        }
    }

    private static boolean adjustHp(
            Connection connection, Command command, AdjustCurrentHp action, int changeOrder)
            throws SQLException {
        Long current = null;
        Long maximum = null;
        try (PreparedStatement statement = connection.prepareStatement(LOCK_HP_SQL)) {
            statement.setLong(1, action.targetCharacterId());
            statement.setLong(2, command.moduleReleaseId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String fieldKey = result.getString("field_key");
                    long value = requiredLong(result, "integer_value");
                    if ("hp.current".equals(fieldKey)) {
                        if (current != null) {
                            throw invalidState("Character HP fields are invalid");
                        }
                        current = value;
                    } else if ("hp.maximum".equals(fieldKey)) {
                        if (maximum != null) {
                            throw invalidState("Character HP fields are invalid");
                        }
                        maximum = value;
                    } else {
                        throw invalidState("Character HP fields are invalid");
                    }
                }
            }
        }
        if (current == null || maximum == null) {
            throw invalidState("Character HP fields are missing");
        }
        if (current < 0 || maximum < 1 || maximum > 999 || current > maximum) {
            throw invalidState("Character HP fields are invalid");
        }
        long adjusted = Math.max(0L, Math.min(maximum, current + action.amount()));
        if (adjusted == current) return false;
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_HP_SQL)) {
            statement.setLong(1, adjusted);
            statement.setLong(2, action.targetCharacterId());
            statement.setLong(3, command.moduleReleaseId());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_INTEGER_CHANGE_SQL)) {
            bindChangeIdentity(statement, command, action.targetCharacterId(), changeOrder);
            statement.setString(5, "hp.current");
            statement.setLong(6, current);
            statement.setLong(7, adjusted);
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
        return true;
    }

    private static long insertModuleItem(
            Connection connection, Command command, GrantModuleItem action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_MODULE_ITEM_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, action.targetCharacterId());
            statement.setLong(2, command.moduleReleaseId());
            statement.setString(3, action.itemKey());
            statement.setString(4, action.itemName());
            statement.setString(5, action.itemDescription());
            statement.setInt(6, action.quantity());
            return executeInsertWithKey(statement, "module item");
        }
    }

    private static long insertTemporaryItem(
            Connection connection, GrantTemporaryItem action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_TEMPORARY_ITEM_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, action.targetCharacterId());
            statement.setString(2, action.itemName());
            statement.setString(3, action.itemDescription());
            statement.setInt(4, action.quantity());
            return executeInsertWithKey(statement, "temporary item");
        }
    }

    private static void insertReferenceChange(
            Connection connection, Command command, GrantModuleItem action, int changeOrder)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_REFERENCE_CHANGE_SQL)) {
            bindChangeIdentity(statement, command, action.targetCharacterId(), changeOrder);
            statement.setString(5, action.itemKey());
            statement.setString(6, action.itemKey());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static void insertTextChange(
            Connection connection, Command command, GrantTemporaryItem action, int changeOrder)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TEXT_CHANGE_SQL)) {
            bindChangeIdentity(statement, command, action.targetCharacterId(), changeOrder);
            statement.setString(5, action.itemName());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static int updateEventMessage(
            Connection connection, Command command, AppendEventMessage action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_EVENT_MESSAGE_SQL)) {
            statement.setString(1, action.message());
            statement.setLong(2, command.gameEventId());
            statement.setLong(3, command.campaignId());
            statement.setQueryTimeout(5);
            return statement.executeUpdate();
        }
    }

    private static void bindChangeIdentity(
            PreparedStatement statement,
            Command command,
            long characterId,
            int changeOrder) throws SQLException {
        statement.setLong(1, command.gameEventId());
        statement.setLong(2, command.campaignId());
        statement.setLong(3, characterId);
        statement.setInt(4, changeOrder);
    }

    private static long executeInsertWithKey(PreparedStatement statement, String label)
            throws SQLException {
        statement.setQueryTimeout(5);
        requireOne(statement.executeUpdate());
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw invalidState(label + " generated key is missing");
            long id = keys.getLong(1);
            if (keys.wasNull() || id <= 0 || keys.next()) {
                throw invalidState(label + " generated key is invalid");
            }
            return id;
        }
    }

    private static void validate(Command command) {
        if (command == null || command.checkExecutionId() <= 0 || command.campaignId() <= 0
                || command.moduleReleaseId() <= 0 || command.gameEventId() <= 0
                || command.success().branch() != CheckEffectPlanRepository.EffectBranch.SUCCESS
                || command.failure().branch() != CheckEffectPlanRepository.EffectBranch.FAILURE) {
            throw new IllegalArgumentException("Invalid host command effect execution identity");
        }
        validateBranch(command.success());
        validateBranch(command.failure());
    }

    private static void validatePreflight(PositionPreflight preflight) {
        if (preflight == null || preflight.campaignId() <= 0 || preflight.moduleReleaseId() <= 0
                || preflight.success().branch() != CheckEffectPlanRepository.EffectBranch.SUCCESS
                || preflight.failure().branch() != CheckEffectPlanRepository.EffectBranch.FAILURE) {
            throw new IllegalArgumentException("Invalid host command position preflight identity");
        }
        validateBranch(preflight.success());
        validateBranch(preflight.failure());
    }

    private static void validateBranch(BranchActions branch) {
        int order = 1;
        int messages = 0;
        for (Action action : branch.actions()) {
            if (action == null || action.effectOrder() != order++) {
                throw new IllegalArgumentException("Effect actions must be one-based and contiguous");
            }
            if (action instanceof AdjustCurrentHp hp) {
                requireTarget(hp.targetCharacterId(), hp.targetCharacterKey());
                if (hp.amount() < -999 || hp.amount() > 999) throw invalidAction();
            } else if (action instanceof GrantModuleItem item) {
                requireTarget(item.targetCharacterId(), item.targetCharacterKey());
                if (!stableKey(item.itemKey()) || !validText(item.itemName(), 1, 80)
                        || !validText(item.itemDescription(), 0, 500)
                        || item.quantity() < 1 || item.quantity() > 999) throw invalidAction();
            } else if (action instanceof GrantTemporaryItem item) {
                requireTarget(item.targetCharacterId(), item.targetCharacterKey());
                if (!validText(item.itemName(), 1, 80)
                        || !validText(item.itemDescription(), 0, 500)
                        || item.quantity() < 1 || item.quantity() > 999) throw invalidAction();
            } else if (action instanceof SetEntityPosition position) {
                requireTarget(position.targetCharacterId(), position.targetCharacterKey());
                if (!"map.tavern_cellar".equals(position.mapKey())
                        || !stableKey(position.nodeKey())) throw invalidAction();
            } else if (action instanceof AppendEventMessage message) {
                if (++messages > 1 || !validText(message.message(), 1, 500)) throw invalidAction();
            } else {
                throw invalidAction();
            }
        }
    }

    private static void requireTarget(long id, String key) {
        if (id <= 0 || !isCanonicalUuid(key)) throw invalidAction();
    }

    private static boolean validText(String value, int minimum, int maximum) {
        if (value == null || !Normalizer.isNormalized(value, Normalizer.Form.NFC)
                || value.codePoints().anyMatch(Character::isISOControl)) return false;
        int length = value.codePointCount(0, value.length());
        return length >= minimum && length <= maximum;
    }

    private static boolean stableKey(String value) {
        return value != null && value.matches("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*");
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw invalidState("Effect execution requires a writable caller-owned serializable transaction");
        }
    }

    private static long requiredLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull()) throw invalidState("Required effect value is missing");
        return value;
    }

    private static void requireOne(int count) throws SQLException {
        if (count != 1) throw invalidState("Effect write did not affect exactly one row");
    }

    private static IllegalArgumentException invalidAction() {
        return new IllegalArgumentException("Invalid host command effect action");
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private record BattleContext(
            long battleId,
            long mapInstanceId,
            long moduleReleaseId,
            String mapKey) {
    }

    private record PositionScope(BattleContext battle, Map<Long, String> currentNodes) {
    }
}
