package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/** JDBC transaction owner for confirmed canonical-v2 level-one characters. */
public final class JdbcLevelOneCharacterCreationRepository
        implements LevelOneCharacterCreationRepository {
    private static final String PREVIEW_CONTEXT_SQL = """
            SELECT c.campaign_key, c.internal_event_tail,
                   cm.frozen_module_key, cm.frozen_release_version,
                   cm.frozen_content_sha256
            FROM campaign AS c
            JOIN campaign_module AS cm ON cm.campaign_id = c.id
            WHERE c.campaign_key = ? AND c.campaign_status = 'ACTIVE'
            """;
    private static final String OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, character_id, result_status
            FROM host_operation WHERE request_id = ? FOR UPDATE
            """;
    private static final String CAMPAIGN_SQL = """
            SELECT id, internal_event_tail FROM campaign
            WHERE campaign_key = ? AND campaign_status = 'ACTIVE' FOR UPDATE
            """;
    private static final String BINDING_SQL = """
            SELECT module_release_id, frozen_module_key, frozen_release_version,
                   frozen_content_sha256
            FROM campaign_module WHERE campaign_id = ?
            """;
    private static final String CHARACTER_SQL = """
            SELECT character_key, row_version FROM character_record WHERE id = ?
            """;
    private static final String INSERT_CHARACTER_SQL = """
            INSERT INTO character_record (
                campaign_id, module_release_id, character_key, character_type,
                character_name, saved_module_key, saved_release_version,
                saved_content_sha256)
            VALUES (?, ?, ?, 'PC', ?, ?, ?, ?)
            """;
    private static final String INSERT_SNAPSHOT_SQL = """
            INSERT INTO character_creation_snapshot_v2 (
                character_id, module_release_id, preview_digest_sha256,
                request_digest_sha256, ability_method_key,
                race_type, race_key, subrace_type, subrace_key,
                background_type, background_key, class_type, class_key,
                base_strength, base_dexterity, base_constitution,
                base_intelligence, base_wisdom, base_charisma,
                final_strength, final_dexterity, final_constitution,
                final_intelligence, final_wisdom, final_charisma,
                maximum_hit_points)
            VALUES (?, ?, ?, ?, 'ability.standard_array_v1',
                    'character.race', ?, ?, ?, 'character.background', ?,
                    'character.class', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SELECTION_SQL = """
            INSERT INTO character_creation_selection_v2 (
                character_id, module_release_id, selection_kind,
                selection_order, selection_key)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_CLASS_LEVEL_SQL = """
            INSERT INTO character_class_level_v2 (
                character_id, module_release_id, class_type, class_key, class_level)
            VALUES (?, ?, 'character.class', ?, 1)
            """;
    private static final String INSERT_RESOURCE_SQL = """
            INSERT INTO character_resource_state_v2 (
                character_id, module_release_id, resource_type, resource_key,
                current_value, maximum_value, is_unlimited)
            VALUES (?, ?, 'character.resource', ?, ?, ?, ?)
            """;
    private static final String UPDATE_TAIL_SQL = """
            UPDATE campaign SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id)
            VALUES (?, ?, 'LEVEL_ONE_CHARACTER_CREATED', ?)
            """;
    private static final String INSERT_REFERENCE_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, new_reference)
            VALUES (?, ?, ?, ?, ?, 'REFERENCE', ?)
            """;
    private static final String INSERT_INTEGER_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, new_integer)
            VALUES (?, ?, ?, ?, ?, 'INTEGER', ?)
            """;
    private static final String INSERT_SUBCLASS_STATE_SQL = """
            INSERT INTO character_subclass_state_v2 (
                character_id, module_release_id, class_type, class_key,
                subclass_type, subclass_key, selected_at_class_level, acquired_event_id)
            VALUES (?, ?, 'character.class', ?, 'character.subclass', ?, 1, ?)
            """;
    private static final String INSERT_FEATURE_STATE_SQL = """
            INSERT INTO character_feature_state_v2 (
                character_id, module_release_id, feature_type, feature_key,
                acquired_at_class_level, execution_mode, execution_algorithm,
                acquired_event_id)
            VALUES (?, ?, 'character.feature', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, operation_type, request_digest_sha256, result_status,
                campaign_id, character_id, game_event_id, completed_at)
            VALUES (?, 'CREATE_LEVEL_ONE_CHARACTER', ?, 'SUCCEEDED', ?, ?, ?, CURRENT_TIMESTAMP(6))
            """;

    private final DataSource dataSource;

    public JdbcLevelOneCharacterCreationRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<PreviewContext> findPreviewContext(String campaignKey) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(PREVIEW_CONTEXT_SQL)) {
            statement.setString(1, campaignKey);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                PreviewContext context = new PreviewContext(
                        required(result, "campaign_key"),
                        result.getLong("internal_event_tail"),
                        required(result, "frozen_module_key"),
                        required(result, "frozen_release_version"),
                        required(result, "frozen_content_sha256"));
                if (result.next()) throw new SQLException("Duplicate campaign preview context");
                return Optional.of(context);
            }
        }
    }

    @Override
    public Result confirm(Command command) throws SQLException {
        Connection connection = dataSource.getConnection();
        ConnectionState original = ConnectionState.capture(connection);
        try {
            connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);

            Result replay = lockOperation(connection, command);
            if (replay != null) {
                connection.commit();
                restore(connection, original);
                connection.close();
                return replay;
            }
            Campaign campaign = lockCampaign(connection, command.campaignKey());
            if (campaign == null) return rollback(connection, original,
                    new Result(Result.Status.CAMPAIGN_UNAVAILABLE, null, null));
            Binding binding = binding(connection, campaign.id());
            if (binding == null || !binding.matches(command)) return rollback(connection, original,
                    new Result(Result.Status.MODULE_BINDING_MISMATCH, null, null));
            if (campaign.eventTail() != command.expectedEventTail()) return rollback(
                    connection, original,
                    new Result(Result.Status.STALE_PREVIEW, null, null));

            long characterId = insertCharacter(connection, campaign.id(), binding.releaseId(), command);
            insertSnapshot(connection, characterId, binding.releaseId(), command);
            insertSelections(connection, characterId, binding.releaseId(), command);
            insertClassLevel(connection, characterId, binding.releaseId(), command);
            insertResources(connection, characterId, binding.releaseId(), command);
            long eventSequence = campaign.eventTail() + 1;
            updateTail(connection, campaign.id(), campaign.eventTail(), eventSequence);
            long eventId = insertEvent(connection, campaign.id(), eventSequence, characterId);
            insertInitialFeatures(connection, eventId, characterId, binding.releaseId(), command);
            insertAudit(connection, eventId, campaign.id(), characterId, command);
            insertOperation(connection, command, campaign.id(), characterId, eventId);
            connection.commit();
            restore(connection, original);
            connection.close();
            return new Result(Result.Status.CREATED, command.characterKey(), 0L);
        } catch (SQLException | RuntimeException exception) {
            rollbackAndRestore(connection, original, exception);
            try { connection.close(); } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private static Result lockOperation(Connection connection, Command command) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                String digest = required(result, "request_digest_sha256");
                String type = required(result, "operation_type");
                String status = required(result, "result_status");
                long characterId = result.getLong("character_id");
                if (!command.requestDigestSha256().equals(digest)
                        || !"CREATE_LEVEL_ONE_CHARACTER".equals(type)
                        || !"SUCCEEDED".equals(status) || result.wasNull()) {
                    return new Result(Result.Status.IDEMPOTENCY_CONFLICT, null, null);
                }
                return replayCharacter(connection, characterId);
            }
        }
    }

    private static Result replayCharacter(Connection connection, long characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHARACTER_SQL)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new Result(Result.Status.IDEMPOTENCY_CONFLICT, null, null);
                }
                return new Result(Result.Status.ALREADY_SUCCEEDED,
                        required(result, "character_key"), result.getLong("row_version"));
            }
        }
    }

    private static Campaign lockCampaign(Connection connection, String campaignKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CAMPAIGN_SQL)) {
            statement.setString(1, campaignKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new Campaign(result.getLong("id"), result.getLong("internal_event_tail"))
                        : null;
            }
        }
    }

    private static Binding binding(Connection connection, long campaignId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(BINDING_SQL)) {
            statement.setLong(1, campaignId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new Binding(result.getLong("module_release_id"),
                        required(result, "frozen_module_key"),
                        required(result, "frozen_release_version"),
                        required(result, "frozen_content_sha256")) : null;
            }
        }
    }

    private static long insertCharacter(Connection connection, long campaignId, long releaseId,
            Command command) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_CHARACTER_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, campaignId);
            statement.setLong(2, releaseId);
            statement.setString(3, command.characterKey());
            statement.setString(4, command.characterName());
            statement.setString(5, command.moduleKey());
            statement.setString(6, command.releaseVersion());
            statement.setString(7, command.contentSha256());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing character id");
                return keys.getLong(1);
            }
        }
    }

    private static void insertSnapshot(Connection connection, long characterId, long releaseId,
            Command command) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SNAPSHOT_SQL)) {
            int index = 1;
            statement.setLong(index++, characterId);
            statement.setLong(index++, releaseId);
            statement.setString(index++, command.previewDigestSha256());
            statement.setString(index++, command.requestDigestSha256());
            statement.setString(index++, command.raceKey());
            if (command.subraceKey() == null) {
                statement.setNull(index++, Types.VARCHAR);
                statement.setNull(index++, Types.VARCHAR);
            } else {
                statement.setString(index++, "character.subrace");
                statement.setString(index++, command.subraceKey());
            }
            statement.setString(index++, command.backgroundKey());
            statement.setString(index++, command.classKey());
            for (String ability : abilityOrder()) {
                statement.setInt(index++, command.baseAbilityScores().get(ability));
            }
            for (String ability : abilityOrder()) {
                statement.setInt(index++, command.finalAbilityScores().get(ability));
            }
            statement.setInt(index, command.maximumHitPoints());
            statement.executeUpdate();
        }
    }

    private static void insertSelections(Connection connection, long characterId, long releaseId,
            Command command) throws SQLException {
        Map<String, Integer> orders = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SELECTION_SQL)) {
            for (Selection selection : command.selections()) {
                statement.setLong(1, characterId);
                statement.setLong(2, releaseId);
                statement.setString(3, selection.kind());
                statement.setInt(4, orders.merge(selection.kind(), 1, Integer::sum));
                statement.setString(5, selection.key());
                statement.executeUpdate();
            }
        }
    }

    private static void insertClassLevel(Connection connection, long characterId, long releaseId,
            Command command) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CLASS_LEVEL_SQL)) {
            statement.setLong(1, characterId);
            statement.setLong(2, releaseId);
            statement.setString(3, command.classKey());
            statement.executeUpdate();
        }
    }

    private static void insertResources(Connection connection, long characterId, long releaseId,
            Command command) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RESOURCE_SQL)) {
            statement.setLong(1, characterId);
            statement.setLong(2, releaseId);
            statement.setString(3, "resource.hit_points");
            statement.setInt(4, command.maximumHitPoints());
            statement.setInt(5, command.maximumHitPoints());
            statement.setBoolean(6, false);
            statement.executeUpdate();
            for (InitialResource resource : command.initialResources()) {
                statement.setLong(1, characterId);
                statement.setLong(2, releaseId);
                statement.setString(3, resource.resourceKey());
                statement.setLong(4, resource.currentValue());
                statement.setLong(5, resource.maximumValue());
                statement.setBoolean(6, resource.unlimited());
                statement.executeUpdate();
            }
        }
    }

    private static void updateTail(Connection connection, long campaignId, long expected, long value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_TAIL_SQL)) {
            statement.setLong(1, value);
            statement.setLong(2, campaignId);
            statement.setLong(3, expected);
            if (statement.executeUpdate() != 1) throw new SQLException("Campaign tail update failed");
        }
    }

    private static long insertEvent(Connection connection, long campaignId, long sequence,
            long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, campaignId);
            statement.setLong(2, sequence);
            statement.setLong(3, characterId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing event id");
                return keys.getLong(1);
            }
        }
    }

    private static void insertInitialFeatures(Connection connection, long eventId,
            long characterId, long releaseId, Command command) throws SQLException {
        if (command.featureTransition() == null) return;
        com.dndtool.service.ClassFeatureRules.Transition transition = command.featureTransition();
        if (transition.newlySelectedSubclassKey() != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_SUBCLASS_STATE_SQL)) {
                statement.setLong(1, characterId);
                statement.setLong(2, releaseId);
                statement.setString(3, command.classKey());
                statement.setString(4, transition.newlySelectedSubclassKey());
                statement.setLong(5, eventId);
                requireOne(statement.executeUpdate());
            }
        }
        if (!transition.featureUnlocks().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_FEATURE_STATE_SQL)) {
                for (com.dndtool.service.ClassFeatureRules.FeatureRule feature
                        : transition.featureUnlocks()) {
                    statement.setLong(1, characterId);
                    statement.setLong(2, releaseId);
                    statement.setString(3, feature.featureKey());
                    statement.setInt(4, feature.level());
                    statement.setString(5, feature.executionMode());
                    statement.setString(6, feature.executionAlgorithm());
                    statement.setLong(7, eventId);
                    statement.addBatch();
                }
                requireBatch(statement.executeBatch(), transition.featureUnlocks().size());
            }
        }
    }

    private static void insertAudit(Connection connection, long eventId, long campaignId,
            long characterId, Command command) throws SQLException {
        int order = 1;
        order = referenceChange(connection, eventId, campaignId, characterId, order,
                "creation.race", command.raceKey());
        if (command.subraceKey() != null) order = referenceChange(connection, eventId, campaignId,
                characterId, order, "creation.subrace", command.subraceKey());
        order = referenceChange(connection, eventId, campaignId, characterId, order,
                "creation.background", command.backgroundKey());
        order = referenceChange(connection, eventId, campaignId, characterId, order,
                "creation.class", command.classKey());
        if (command.featureTransition() != null) {
            if (command.featureTransition().newlySelectedSubclassKey() != null) {
                order = referenceChange(connection, eventId, campaignId, characterId, order,
                        "creation.class_subclass",
                        command.featureTransition().newlySelectedSubclassKey());
            }
            for (com.dndtool.service.ClassFeatureRules.FeatureRule feature
                    : command.featureTransition().featureUnlocks()) {
                order = referenceChange(connection, eventId, campaignId, characterId, order,
                        "creation.class_feature", feature.featureKey());
            }
        }
        for (String ability : abilityOrder()) {
            order = integerChange(connection, eventId, campaignId, characterId, order,
                    ability, command.finalAbilityScores().get(ability));
        }
        Map<String, Integer> selectionOrders = new HashMap<>();
        for (Selection selection : command.selections()) {
            String kind = selection.kind().toLowerCase(java.util.Locale.ROOT);
            int selectionOrder = selectionOrders.merge(kind, 1, Integer::sum);
            order = referenceChange(connection, eventId, campaignId, characterId, order,
                    "creation.selection." + kind + ".value_" + selectionOrder,
                    selection.key());
        }
        order = integerChange(connection, eventId, campaignId, characterId, order,
                "character.level.total", 1);
        order = integerChange(connection, eventId, campaignId, characterId, order,
                "character.class.level", 1);
        order = integerChange(connection, eventId, campaignId, characterId, order,
                "character.proficiency_bonus", 2);
        order = integerChange(connection, eventId, campaignId, characterId, order,
                "resource.hit_points.current", command.maximumHitPoints());
        order = integerChange(connection, eventId, campaignId, characterId, order,
                "resource.hit_points.maximum", command.maximumHitPoints());
        for (InitialResource resource : command.initialResources()) {
            order = integerChange(connection, eventId, campaignId, characterId, order,
                    resource.resourceKey() + ".current", Math.toIntExact(resource.currentValue()));
            order = integerChange(connection, eventId, campaignId, characterId, order,
                    resource.resourceKey() + ".maximum", Math.toIntExact(resource.maximumValue()));
        }
    }

    private static int referenceChange(Connection connection, long eventId, long campaignId,
            long characterId, int order, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_REFERENCE_CHANGE_SQL)) {
            commonChange(statement, eventId, campaignId, characterId, order, key);
            statement.setString(6, value);
            statement.executeUpdate();
            return order + 1;
        }
    }

    private static int integerChange(Connection connection, long eventId, long campaignId,
            long characterId, int order, String key, int value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_INTEGER_CHANGE_SQL)) {
            commonChange(statement, eventId, campaignId, characterId, order, key);
            statement.setInt(6, value);
            statement.executeUpdate();
            return order + 1;
        }
    }

    private static void commonChange(PreparedStatement statement, long eventId, long campaignId,
            long characterId, int order, String key) throws SQLException {
        statement.setLong(1, eventId);
        statement.setLong(2, campaignId);
        statement.setLong(3, characterId);
        statement.setInt(4, order);
        statement.setString(5, key);
    }

    private static void insertOperation(Connection connection, Command command, long campaignId,
            long characterId, long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setString(2, command.requestDigestSha256());
            statement.setLong(3, campaignId);
            statement.setLong(4, characterId);
            statement.setLong(5, eventId);
            statement.executeUpdate();
        }
    }

    private static String required(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null) throw new SQLException("Invalid database row");
        return value;
    }

    private static String[] abilityOrder() {
        return new String[] {"ability.strength", "ability.dexterity", "ability.constitution",
                "ability.intelligence", "ability.wisdom", "ability.charisma"};
    }

    private static void requireOne(int count) throws SQLException {
        if (count != 1) throw new SQLException("Unexpected update count");
    }

    private static void requireBatch(int[] counts, int expected) throws SQLException {
        if (counts.length != expected) throw new SQLException("Unexpected batch count");
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new SQLException("Unexpected batch result");
            }
        }
    }

    private static Result rollback(Connection connection, ConnectionState original, Result result)
            throws SQLException {
        connection.rollback();
        restore(connection, original);
        connection.close();
        return result;
    }

    private static void rollbackAndRestore(Connection connection, ConnectionState original,
            Exception primary) {
        try { if (!connection.getAutoCommit()) connection.rollback(); }
        catch (SQLException failure) { primary.addSuppressed(failure); }
        try { restore(connection, original); }
        catch (SQLException failure) { primary.addSuppressed(failure); }
    }

    private static void restore(Connection connection, ConnectionState original)
            throws SQLException {
        connection.setAutoCommit(original.autoCommit());
        connection.setTransactionIsolation(original.isolation());
        connection.setReadOnly(original.readOnly());
    }

    private record Campaign(long id, long eventTail) { }
    private record Binding(long releaseId, String moduleKey, String releaseVersion, String hash) {
        boolean matches(Command command) {
            return moduleKey.equals(command.moduleKey())
                    && releaseVersion.equals(command.releaseVersion())
                    && hash.equals(command.contentSha256());
        }
    }
    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
