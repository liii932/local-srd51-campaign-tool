package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/** JDBC transaction owner for one confirmed canonical-v2 level advancement. */
public final class JdbcLevelAdvancementRepository implements LevelAdvancementRepository {
    private static final String PREVIEW_CHARACTER_SQL = """
            SELECT cr.id, cr.character_key, cr.row_version,
                   c.campaign_key, c.internal_event_tail,
                   cm.frozen_module_key, cm.frozen_release_version,
                   cm.frozen_content_sha256,
                   cs.final_constitution, cs.final_charisma
            FROM character_record AS cr
            JOIN campaign AS c ON c.id = cr.campaign_id
            JOIN campaign_module AS cm ON cm.campaign_id = c.id
            JOIN character_creation_snapshot_v2 AS cs ON cs.character_id = cr.id
            WHERE cr.character_key = ? AND cr.character_type = 'PC'
              AND cr.character_status = 'ACTIVE' AND c.campaign_status = 'ACTIVE'
            """;
    private static final String CLASS_LEVEL_SQL = """
            SELECT class_key, class_level FROM character_class_level_v2
            WHERE character_id = ? ORDER BY class_key
            """;
    private static final String RESOURCE_SQL = """
            SELECT resource_key, current_value, maximum_value, is_unlimited
            FROM character_resource_state_v2
            WHERE character_id = ? ORDER BY resource_key
            """;
    private static final String OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, character_id,
                   game_event_id, result_status
            FROM host_operation WHERE request_id = ? FOR UPDATE
            """;
    private static final String COMPLETED_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, character_id,
                   game_event_id, result_status
            FROM host_operation WHERE request_id = ?
            """;
    private static final String REPLAY_SQL = """
            SELECT cr.character_key, a.new_row_version, a.hit_die_roll,
                   a.hit_point_increase
            FROM character_level_advancement_v2 AS a
            JOIN character_record AS cr ON cr.id = a.character_id
            WHERE a.game_event_id = ? AND a.character_id = ?
            """;
    private static final String LOCK_CHARACTER_SQL = """
            SELECT cr.id, cr.campaign_id, cr.module_release_id, cr.row_version,
                   c.internal_event_tail
            FROM character_record AS cr
            JOIN campaign AS c ON c.id = cr.campaign_id
            WHERE cr.character_key = ? AND cr.character_type = 'PC'
              AND cr.character_status = 'ACTIVE' AND c.campaign_status = 'ACTIVE'
            FOR UPDATE
            """;
    private static final String BINDING_SQL = """
            SELECT frozen_module_key, frozen_release_version, frozen_content_sha256
            FROM campaign_module WHERE campaign_id = ? AND module_release_id = ?
            """;
    private static final String SCORES_SQL = """
            SELECT cs.final_strength + COALESCE(SUM(CASE WHEN ac.ability_key = 'ability.strength' THEN ac.new_score - ac.previous_score ELSE 0 END), 0) AS strength,
                   cs.final_dexterity + COALESCE(SUM(CASE WHEN ac.ability_key = 'ability.dexterity' THEN ac.new_score - ac.previous_score ELSE 0 END), 0) AS dexterity,
                   cs.final_constitution + COALESCE(SUM(CASE WHEN ac.ability_key = 'ability.constitution' THEN ac.new_score - ac.previous_score ELSE 0 END), 0) AS constitution,
                   cs.final_intelligence + COALESCE(SUM(CASE WHEN ac.ability_key = 'ability.intelligence' THEN ac.new_score - ac.previous_score ELSE 0 END), 0) AS intelligence,
                   cs.final_wisdom + COALESCE(SUM(CASE WHEN ac.ability_key = 'ability.wisdom' THEN ac.new_score - ac.previous_score ELSE 0 END), 0) AS wisdom,
                   cs.final_charisma + COALESCE(SUM(CASE WHEN ac.ability_key = 'ability.charisma' THEN ac.new_score - ac.previous_score ELSE 0 END), 0) AS charisma
            FROM character_creation_snapshot_v2 AS cs
            LEFT JOIN character_ability_score_change_v2 AS ac ON ac.character_id = cs.character_id
            WHERE cs.character_id = ?
            GROUP BY cs.character_id, cs.final_strength, cs.final_dexterity,
                     cs.final_constitution, cs.final_intelligence, cs.final_wisdom,
                     cs.final_charisma
            """;
    private static final String FEATS_SQL = """
            SELECT feat_key FROM character_feat_state_v2
            WHERE character_id = ? ORDER BY feat_key
            """;
    private static final String SUBCLASSES_SQL = """
            SELECT class_key, subclass_key FROM character_subclass_state_v2
            WHERE character_id = ? ORDER BY class_key
            """;
    private static final String FEATURES_SQL = """
            SELECT feature_key FROM character_feature_state_v2
            WHERE character_id = ? ORDER BY feature_key
            """;
    private static final String STARTING_PROFICIENCY_SQL = """
            SELECT a.text_value AS proficiency_profile
            FROM character_creation_snapshot_v2 AS s
            JOIN module_catalog_attribute_v2 AS a
              ON a.module_release_id = s.module_release_id
             AND a.definition_type = s.class_type
             AND a.definition_key = s.class_key
             AND a.attribute_key = 'class.starting_proficiency_profile'
             AND a.attribute_order = 1 AND a.value_type = 'TEXT'
            WHERE s.character_id = ?
            """;
    private static final String PROFICIENCIES_SQL = """
            SELECT selection_key AS proficiency_key
            FROM character_creation_selection_v2
            WHERE character_id = ? AND selection_kind IN ('SKILL', 'TOOL')
            UNION
            SELECT proficiency_key
            FROM character_multiclass_proficiency_v2
            WHERE character_id = ?
            ORDER BY proficiency_key
            """;
    private static final String LOCK_CLASS_SQL = """
            SELECT class_key, class_level FROM character_class_level_v2
            WHERE character_id = ? ORDER BY class_key FOR UPDATE
            """;
    private static final String LOCK_RESOURCES_SQL = """
            SELECT resource_key, current_value, maximum_value, is_unlimited
            FROM character_resource_state_v2
            WHERE character_id = ? ORDER BY resource_key FOR UPDATE
            """;
    private static final String UPDATE_CLASS_SQL = """
            UPDATE character_class_level_v2 SET class_level = ?
            WHERE character_id = ? AND class_key = ? AND class_level = ?
            """;
    private static final String INSERT_CLASS_SQL = """
            INSERT INTO character_class_level_v2 (
                character_id, module_release_id, class_type, class_key, class_level)
            VALUES (?, ?, 'character.class', ?, 1)
            """;
    private static final String UPDATE_RESOURCE_SQL = """
            UPDATE character_resource_state_v2
            SET current_value = ?, maximum_value = ?, is_unlimited = ?
            WHERE character_id = ? AND resource_key = ?
              AND current_value = ? AND maximum_value = ? AND is_unlimited = ?
            """;
    private static final String INSERT_RESOURCE_SQL = """
            INSERT INTO character_resource_state_v2 (
                character_id, module_release_id, resource_type, resource_key,
                current_value, maximum_value, is_unlimited)
            VALUES (?, ?, 'character.resource', ?, ?, ?, ?)
            """;
    private static final String UPDATE_CHARACTER_SQL = """
            UPDATE character_record SET row_version = row_version + 1
            WHERE id = ? AND row_version = ?
            """;
    private static final String UPDATE_TAIL_SQL = """
            UPDATE campaign SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id)
            VALUES (?, ?, 'CHARACTER_LEVEL_ADVANCED', ?)
            """;
    private static final String INSERT_ADVANCEMENT_SQL = """
            INSERT INTO character_level_advancement_v2 (
                game_event_id, character_id, module_release_id,
                preview_digest_sha256, request_digest_sha256,
                class_type, class_key, previous_total_level, new_total_level,
                previous_class_level, new_class_level, hp_choice_algorithm,
                hit_die_sides, hit_die_roll, constitution_modifier,
                hit_point_increase, previous_maximum_hit_points,
                new_maximum_hit_points, previous_proficiency_bonus,
                new_proficiency_bonus, previous_row_version, new_row_version)
            VALUES (?, ?, ?, ?, ?, 'character.class', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SUBCLASS_STATE_SQL = """
            INSERT INTO character_subclass_state_v2 (
                character_id, module_release_id, class_type, class_key,
                subclass_type, subclass_key, selected_at_class_level, acquired_event_id)
            VALUES (?, ?, 'character.class', ?, 'character.subclass', ?, ?, ?)
            """;
    private static final String INSERT_FEATURE_STATE_SQL = """
            INSERT INTO character_feature_state_v2 (
                character_id, module_release_id, feature_type, feature_key,
                acquired_at_class_level, execution_mode, execution_algorithm,
                acquired_event_id)
            VALUES (?, ?, 'character.feature', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_ADVANCEMENT_CHOICE_SQL = """
            INSERT INTO character_advancement_choice_v2 (
                game_event_id, character_id, module_release_id,
                target_class_type, target_class_key, choice_type,
                feat_type, feat_key, spell_aggregation_status)
            VALUES (?, ?, ?, 'character.class', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_ABILITY_SCORE_CHANGE_SQL = """
            INSERT INTO character_ability_score_change_v2 (
                game_event_id, character_id, module_release_id,
                ability_key, previous_score, new_score)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_FEAT_STATE_SQL = """
            INSERT INTO character_feat_state_v2 (
                character_id, module_release_id, feat_type, feat_key, acquired_event_id)
            VALUES (?, ?, 'character.feat', ?, ?)
            """;
    private static final String INSERT_MULTICLASS_PROFICIENCY_SQL = """
            INSERT INTO character_multiclass_proficiency_v2 (
                character_id, module_release_id, class_type, class_key,
                proficiency_key, acquired_event_id)
            VALUES (?, ?, 'character.class', ?, ?, ?)
            """;
    private static final String INSERT_RESOURCE_CHANGE_SQL = """
            INSERT INTO character_level_resource_change_v2 (
                game_event_id, character_id, module_release_id,
                resource_type, resource_key,
                previous_current_value, previous_maximum_value,
                previous_is_unlimited, new_current_value,
                new_maximum_value, new_is_unlimited)
            VALUES (?, ?, ?, 'character.resource', ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_INTEGER_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_integer, new_integer)
            VALUES (?, ?, ?, ?, ?, 'INTEGER', ?, ?)
            """;
    private static final String INSERT_BOOLEAN_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_boolean, new_boolean)
            VALUES (?, ?, ?, ?, ?, 'BOOLEAN', ?, ?)
            """;
    private static final String INSERT_REFERENCE_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_reference, new_reference)
            VALUES (?, ?, ?, ?, ?, 'REFERENCE', ?, ?)
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, operation_type, request_digest_sha256, result_status,
                campaign_id, character_id, game_event_id, completed_at)
            VALUES (?, 'ADVANCE_CHARACTER_LEVEL', ?, 'SUCCEEDED', ?, ?, ?,
                    CURRENT_TIMESTAMP(6))
            """;

    private final DataSource dataSource;

    public JdbcLevelAdvancementRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<PreviewContext> findPreviewContext(String characterKey) throws SQLException {
        Connection connection = dataSource.getConnection();
        ConnectionState original = ConnectionState.capture(connection);
        try {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            BasePreview base = previewBase(connection, characterKey);
            if (base == null) {
                connection.commit();
                restoreAndClose(connection, original);
                return Optional.empty();
            }
            List<ClassState> classes = classes(connection, base.characterId(), false);
            if (classes.isEmpty()) throw invalidRow();
            ClassState selectedClass = classes.getFirst();
            int totalLevel = classes.stream().mapToInt(ClassState::level).sum();
            AbilityScores scores = scores(connection, base.characterId());
            List<ResourceState> resources = resources(connection, base.characterId(), false);
            java.util.Set<String> feats = feats(connection, base.characterId());
            java.util.Set<String> proficiencies = proficiencies(
                    connection, base.characterId());
            Map<String, String> subclasses = subclasses(connection, base.characterId());
            java.util.Set<String> features = features(connection, base.characterId());
            connection.commit();
            restoreAndClose(connection, original);
            return Optional.of(new PreviewContext(
                    base.campaignKey(), characterKey, base.eventTail(), base.rowVersion(),
                    base.moduleKey(), base.releaseVersion(), base.hash(), selectedClass.classKey(),
                    selectedClass.level(), totalLevel, scores.constitution(), scores.charisma(),
                    resources, classes.stream().map(value -> new ClassLevel(
                            value.classKey(), value.level())).toList(), scores.asMap(), feats,
                    proficiencies, subclasses, features));
        } catch (SQLException | RuntimeException exception) {
            rollbackAndRestore(connection, original, exception);
            try { connection.close(); } catch (SQLException failure) {
                exception.addSuppressed(failure);
            }
            throw exception;
        }
    }

    @Override
    public Optional<Result> findCompleted(String requestId, String requestDigestSha256)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        COMPLETED_OPERATION_SQL)) {
            statement.setString(1, requestId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                String digest = required(result, "request_digest_sha256");
                String type = required(result, "operation_type");
                String status = required(result, "result_status");
                long characterId = result.getLong("character_id");
                boolean missingCharacter = result.wasNull();
                long eventId = result.getLong("game_event_id");
                boolean missingEvent = result.wasNull();
                if (result.next() || !requestDigestSha256.equals(digest)
                        || !"ADVANCE_CHARACTER_LEVEL".equals(type)
                        || !"SUCCEEDED".equals(status) || missingCharacter || missingEvent) {
                    return Optional.of(rejected(Result.Status.IDEMPOTENCY_CONFLICT));
                }
                return Optional.of(replay(connection, characterId, eventId));
            }
        }
    }

    @Override
    public Result confirm(Command command, HitPointResolver resolver) throws SQLException {
        java.util.Objects.requireNonNull(command);
        java.util.Objects.requireNonNull(resolver);
        Connection connection = dataSource.getConnection();
        ConnectionState original = ConnectionState.capture(connection);
        try {
            connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);

            Result replay = lockOperation(connection, command);
            if (replay != null) return commit(connection, original, replay);
            LockedCharacter character = lockCharacter(connection, command.expected().characterKey());
            if (character == null) return rollback(connection, original,
                    rejected(Result.Status.CHARACTER_UNAVAILABLE));
            if (!bindingMatches(connection, character, command.expected())) {
                return rollback(connection, original,
                        rejected(Result.Status.MODULE_BINDING_MISMATCH));
            }
            if (character.eventTail() != command.expected().expectedEventTail()) {
                return rollback(connection, original, rejected(Result.Status.STALE_PREVIEW));
            }
            if (character.rowVersion() != command.expected().expectedRowVersion()) {
                return rollback(connection, original, rejected(Result.Status.STALE_ROW_VERSION));
            }
            AbilityScores scores = scores(connection, character.characterId());
            List<ClassState> lockedClasses = classes(connection, character.characterId(), true);
            List<ResourceState> lockedResources = resources(
                    connection, character.characterId(), true);
            java.util.Set<String> lockedFeats = feats(connection, character.characterId());
            java.util.Set<String> lockedProficiencies = proficiencies(
                    connection, character.characterId());
            Map<String, String> lockedSubclasses = subclasses(
                    connection, character.characterId());
            java.util.Set<String> lockedFeatures = features(
                    connection, character.characterId());
            if (!authoritativeStateMatches(command, lockedClasses, scores, lockedResources,
                    lockedFeats, lockedProficiencies, lockedSubclasses, lockedFeatures)) {
                return rollback(connection, original,
                        rejected(Result.Status.AUTHORITATIVE_STATE_MISMATCH));
            }
            List<ResourceChange> declaredChanges = validatedChanges(command, lockedResources);
            if (declaredChanges == null) {
                return rollback(connection, original,
                        rejected(Result.Status.AUTHORITATIVE_STATE_MISMATCH));
            }

            // This is deliberately the first random-capable call in the transaction.
            HitPointResolution hitPoints = resolver.resolve(
                    command.hitDieSides(), command.constitutionModifier());
            if (!validResolution(command, hitPoints)) {
                throw new SQLException("Invalid server hit-point resolution");
            }
            ResourceState previousHp = resourceMap(lockedResources).get("resource.hit_points");
            ResourceState newHp = new ResourceState("resource.hit_points",
                    previousHp.currentValue() + hitPoints.hitPointIncrease(),
                    previousHp.maximumValue() + hitPoints.hitPointIncrease(), false);
            List<ResourceChange> allChanges = new ArrayList<>(declaredChanges);
            allChanges.add(new ResourceChange("resource.hit_points", previousHp, newHp));
            allChanges.sort(Comparator.comparing(ResourceChange::resourceKey));

            updateClass(connection, character, command);
            applyResources(connection, character, allChanges);
            updateCharacter(connection, character.characterId(), character.rowVersion());
            long eventSequence = character.eventTail() + 1;
            updateTail(connection, character.campaignId(), character.eventTail(), eventSequence);
            long eventId = insertEvent(
                    connection, character.campaignId(), eventSequence, character.characterId());
            insertAdvancement(connection, eventId, character, command, hitPoints, previousHp, newHp);
            insertFeatureTransition(connection, eventId, character, command);
            insertAdvancementChoice(connection, eventId, character, command);
            insertResourceChanges(connection, eventId, character, allChanges);
            insertAudit(connection, eventId, character, command, hitPoints, allChanges);
            insertOperation(connection, eventId, character, command);
            connection.commit();
            restoreAndClose(connection, original);
            return new Result(Result.Status.ADVANCED, command.expected().characterKey(),
                    character.rowVersion() + 1, hitPoints.hitDieRoll(),
                    hitPoints.hitPointIncrease());
        } catch (SQLException | RuntimeException exception) {
            rollbackAndRestore(connection, original, exception);
            try { connection.close(); } catch (SQLException failure) {
                exception.addSuppressed(failure);
            }
            throw exception;
        }
    }

    private static BasePreview previewBase(Connection connection, String characterKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PREVIEW_CHARACTER_SQL)) {
            statement.setString(1, characterKey);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                BasePreview value = new BasePreview(result.getLong("id"),
                        required(result, "campaign_key"), result.getLong("internal_event_tail"),
                        result.getLong("row_version"), required(result, "frozen_module_key"),
                        required(result, "frozen_release_version"),
                        required(result, "frozen_content_sha256"),
                        result.getInt("final_constitution"), result.getInt("final_charisma"));
                if (result.next()) throw invalidRow();
                return value;
            }
        }
    }

    private static Result lockOperation(Connection connection, Command command)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                String digest = required(result, "request_digest_sha256");
                String type = required(result, "operation_type");
                String status = required(result, "result_status");
                long characterId = result.getLong("character_id");
                boolean missingCharacter = result.wasNull();
                long eventId = result.getLong("game_event_id");
                boolean missingEvent = result.wasNull();
                if (!command.requestDigestSha256().equals(digest)
                        || !"ADVANCE_CHARACTER_LEVEL".equals(type)
                        || !"SUCCEEDED".equals(status) || missingCharacter || missingEvent) {
                    return rejected(Result.Status.IDEMPOTENCY_CONFLICT);
                }
                return replay(connection, characterId, eventId);
            }
        }
    }

    private static Result replay(Connection connection, long characterId, long eventId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(REPLAY_SQL)) {
            statement.setLong(1, eventId);
            statement.setLong(2, characterId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return rejected(Result.Status.IDEMPOTENCY_CONFLICT);
                String key = required(result, "character_key");
                long rowVersion = result.getLong("new_row_version");
                int roll = result.getInt("hit_die_roll");
                Integer nullableRoll = result.wasNull() ? null : roll;
                int increase = result.getInt("hit_point_increase");
                if (result.next()) return rejected(Result.Status.IDEMPOTENCY_CONFLICT);
                return new Result(Result.Status.ALREADY_SUCCEEDED, key, rowVersion,
                        nullableRoll, increase);
            }
        }
    }

    private static LockedCharacter lockCharacter(Connection connection, String characterKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CHARACTER_SQL)) {
            statement.setString(1, characterKey);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                LockedCharacter value = new LockedCharacter(result.getLong("id"),
                        result.getLong("campaign_id"), result.getLong("module_release_id"),
                        result.getLong("row_version"), result.getLong("internal_event_tail"));
                if (result.next()) throw invalidRow();
                return value;
            }
        }
    }

    private static boolean bindingMatches(Connection connection, LockedCharacter character,
            PreviewContext expected) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(BINDING_SQL)) {
            statement.setLong(1, character.campaignId());
            statement.setLong(2, character.releaseId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return false;
                boolean matches = expected.moduleKey().equals(required(result, "frozen_module_key"))
                        && expected.releaseVersion().equals(
                                required(result, "frozen_release_version"))
                        && expected.contentSha256().equals(
                                required(result, "frozen_content_sha256"));
                return matches && !result.next();
            }
        }
    }

    private static AbilityScores scores(Connection connection, long characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SCORES_SQL)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidRow();
                AbilityScores value = new AbilityScores(result.getInt("strength"),
                        result.getInt("dexterity"), result.getInt("constitution"),
                        result.getInt("intelligence"), result.getInt("wisdom"),
                        result.getInt("charisma"));
                if (result.next()) throw invalidRow();
                return value;
            }
        }
    }

    private static List<ClassState> classes(
            Connection connection, long characterId, boolean locked) throws SQLException {
        String sql = locked ? LOCK_CLASS_SQL : CLASS_LEVEL_SQL;
        List<ClassState> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new ClassState(
                        required(result, "class_key"), result.getInt("class_level")));
            }
        }
        return List.copyOf(values);
    }

    private static java.util.Set<String> feats(Connection connection, long characterId)
            throws SQLException {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(FEATS_SQL)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!values.add(required(result, "feat_key"))) throw invalidRow();
                }
            }
        }
        return java.util.Set.copyOf(values);
    }

    private static Map<String, String> subclasses(
            Connection connection, long characterId) throws SQLException {
        Map<String, String> values = new java.util.TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SUBCLASSES_SQL)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (values.putIfAbsent(required(result, "class_key"),
                            required(result, "subclass_key")) != null) throw invalidRow();
                }
            }
        }
        return Map.copyOf(values);
    }

    private static java.util.Set<String> features(
            Connection connection, long characterId) throws SQLException {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(FEATURES_SQL)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!values.add(required(result, "feature_key"))) throw invalidRow();
                }
            }
        }
        return java.util.Set.copyOf(values);
    }

    private static java.util.Set<String> proficiencies(
            Connection connection, long characterId) throws SQLException {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(STARTING_PROFICIENCY_SQL)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidRow();
                String previous = null;
                for (String value : required(result, "proficiency_profile").split(",", -1)) {
                    if (!value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+")
                            || value.length() > 128
                            || previous != null && previous.compareTo(value) >= 0
                            || !values.add(value)) throw invalidRow();
                    previous = value;
                }
                if (result.next()) throw invalidRow();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(PROFICIENCIES_SQL)) {
            statement.setLong(1, characterId);
            statement.setLong(2, characterId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!values.add(required(result, "proficiency_key"))) throw invalidRow();
                }
            }
        }
        return java.util.Set.copyOf(values);
    }

    private static List<ResourceState> resources(
            Connection connection, long characterId, boolean locked) throws SQLException {
        String sql = locked ? LOCK_RESOURCES_SQL : RESOURCE_SQL;
        List<ResourceState> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new ResourceState(required(result, "resource_key"),
                            result.getLong("current_value"), result.getLong("maximum_value"),
                            result.getBoolean("is_unlimited")));
                }
            }
        }
        return List.copyOf(values);
    }

    private static boolean authoritativeStateMatches(Command command,
            List<ClassState> selectedClasses, AbilityScores scores,
            List<ResourceState> lockedResources, java.util.Set<String> lockedFeats,
            java.util.Set<String> lockedProficiencies,
            Map<String, String> lockedSubclasses,
            java.util.Set<String> lockedFeatures) {
        PreviewContext expected = command.expected();
        Map<String, Integer> actualClasses = new java.util.TreeMap<>();
        selectedClasses.forEach(value -> actualClasses.put(value.classKey(), value.level()));
        Map<String, Integer> expectedClasses = new java.util.TreeMap<>();
        expected.classLevels().forEach(value -> expectedClasses.put(
                value.classKey(), value.classLevel()));
        return actualClasses.equals(expectedClasses)
                && actualClasses.values().stream().mapToInt(Integer::intValue).sum()
                        == expected.totalLevel()
                && scores.asMap().equals(expected.abilityScores())
                && lockedFeats.equals(expected.acquiredFeats())
                && lockedProficiencies.equals(expected.acquiredProficiencies())
                && lockedSubclasses.equals(expected.subclassesByClass())
                && lockedFeatures.equals(expected.acquiredFeatures())
                && command.targetLevel() == expected.totalLevel() + 1
                && command.targetLevel() <= 20
                && resourceMap(lockedResources).equals(resourceMap(expected.resources()));
    }

    private static List<ResourceChange> validatedChanges(
            Command command, List<ResourceState> lockedResources) {
        Map<String, ResourceState> actual = resourceMap(lockedResources);
        Map<String, ResourceChange> unique = new HashMap<>();
        for (ResourceChange change : command.resourceChanges()) {
            if (change == null || "resource.hit_points".equals(change.resourceKey())
                    || change.next() == null || !change.resourceKey().equals(
                            change.next().resourceKey())
                    || change.previous() != null && !change.resourceKey().equals(
                            change.previous().resourceKey())
                    || unique.putIfAbsent(change.resourceKey(), change) != null
                    || !java.util.Objects.equals(actual.get(change.resourceKey()),
                            change.previous()) || !validState(change.next())) {
                return null;
            }
        }
        ResourceState hp = actual.get("resource.hit_points");
        return hp != null && validState(hp) && !hp.unlimited()
                ? unique.values().stream().sorted(
                        Comparator.comparing(ResourceChange::resourceKey)).toList()
                : null;
    }

    private static boolean validState(ResourceState state) {
        return state.unlimited()
                ? state.currentValue() == 0 && state.maximumValue() == 0
                : state.maximumValue() > 0 && state.currentValue() >= 0
                        && state.currentValue() <= state.maximumValue();
    }

    private static boolean validResolution(Command command, HitPointResolution value) {
        if (value == null || value.hitPointIncrease() < 1) return false;
        int retroactive = retroactiveConstitutionIncrease(command);
        if ("FIXED_AVERAGE".equals(command.hpChoiceAlgorithm())) {
            return value.hitDieRoll() == null && value.hitPointIncrease() == Math.max(
                    1, command.hitDieSides() / 2 + 1 + command.constitutionModifier())
                    + retroactive;
        }
        return "SERVER_ROLL".equals(command.hpChoiceAlgorithm())
                && value.hitDieRoll() != null && value.hitDieRoll() >= 1
                && value.hitDieRoll() <= command.hitDieSides()
                && value.hitPointIncrease() == Math.max(
                        1, value.hitDieRoll() + command.constitutionModifier()) + retroactive;
    }

    private static int retroactiveConstitutionIncrease(Command command) {
        if (command.advancementChoice() == null) return 0;
        int previous = command.expected().abilityScores().get("ability.constitution");
        int next = command.advancementChoice().abilityScores().get("ability.constitution");
        return command.expected().totalLevel()
                * (Math.floorDiv(next - 10, 2) - Math.floorDiv(previous - 10, 2));
    }

    private static void updateClass(
            Connection connection, LockedCharacter character, Command command)
            throws SQLException {
        String classKey = command.advancementChoice() == null
                ? command.expected().classKey() : command.advancementChoice().targetClassKey();
        int previous = command.advancementChoice() == null
                ? command.expected().classLevel()
                : command.advancementChoice().previousClassLevel();
        if (previous == 0) {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_CLASS_SQL)) {
                statement.setLong(1, character.characterId());
                statement.setLong(2, character.releaseId());
                statement.setString(3, classKey);
                requireOne(statement.executeUpdate());
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_CLASS_SQL)) {
            statement.setInt(1, previous + 1);
            statement.setLong(2, character.characterId());
            statement.setString(3, classKey);
            statement.setInt(4, previous);
            requireOne(statement.executeUpdate());
        }
    }

    private static void applyResources(Connection connection, LockedCharacter character,
            List<ResourceChange> changes) throws SQLException {
        for (ResourceChange change : changes) {
            if (change.previous() == null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        INSERT_RESOURCE_SQL)) {
                    statement.setLong(1, character.characterId());
                    statement.setLong(2, character.releaseId());
                    statement.setString(3, change.resourceKey());
                    statement.setLong(4, change.next().currentValue());
                    statement.setLong(5, change.next().maximumValue());
                    statement.setBoolean(6, change.next().unlimited());
                    requireOne(statement.executeUpdate());
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        UPDATE_RESOURCE_SQL)) {
                    statement.setLong(1, change.next().currentValue());
                    statement.setLong(2, change.next().maximumValue());
                    statement.setBoolean(3, change.next().unlimited());
                    statement.setLong(4, character.characterId());
                    statement.setString(5, change.resourceKey());
                    statement.setLong(6, change.previous().currentValue());
                    statement.setLong(7, change.previous().maximumValue());
                    statement.setBoolean(8, change.previous().unlimited());
                    requireOne(statement.executeUpdate());
                }
            }
        }
    }

    private static void updateCharacter(Connection connection, long characterId, long rowVersion)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_CHARACTER_SQL)) {
            statement.setLong(1, characterId);
            statement.setLong(2, rowVersion);
            requireOne(statement.executeUpdate());
        }
    }

    private static void updateTail(Connection connection, long campaignId, long previous, long next)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_TAIL_SQL)) {
            statement.setLong(1, next);
            statement.setLong(2, campaignId);
            statement.setLong(3, previous);
            requireOne(statement.executeUpdate());
        }
    }

    private static long insertEvent(Connection connection, long campaignId, long eventSequence,
            long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, campaignId);
            statement.setLong(2, eventSequence);
            statement.setLong(3, characterId);
            requireOne(statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing event id");
                return keys.getLong(1);
            }
        }
    }

    private static void insertAdvancement(Connection connection, long eventId,
            LockedCharacter character, Command command, HitPointResolution hitPoints,
            ResourceState previousHp, ResourceState newHp) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ADVANCEMENT_SQL)) {
            int index = 1;
            statement.setLong(index++, eventId);
            statement.setLong(index++, character.characterId());
            statement.setLong(index++, character.releaseId());
            statement.setString(index++, command.previewDigestSha256());
            statement.setString(index++, command.requestDigestSha256());
            String classKey = command.advancementChoice() == null
                    ? command.expected().classKey() : command.advancementChoice().targetClassKey();
            int previousClassLevel = command.advancementChoice() == null
                    ? command.expected().classLevel()
                    : command.advancementChoice().previousClassLevel();
            statement.setString(index++, classKey);
            statement.setInt(index++, command.expected().totalLevel());
            statement.setInt(index++, command.targetLevel());
            statement.setInt(index++, previousClassLevel);
            statement.setInt(index++, previousClassLevel + 1);
            statement.setString(index++, command.hpChoiceAlgorithm());
            statement.setInt(index++, command.hitDieSides());
            if (hitPoints.hitDieRoll() == null) statement.setNull(index++, Types.TINYINT);
            else statement.setInt(index++, hitPoints.hitDieRoll());
            statement.setInt(index++, command.constitutionModifier());
            statement.setInt(index++, hitPoints.hitPointIncrease());
            statement.setLong(index++, previousHp.maximumValue());
            statement.setLong(index++, newHp.maximumValue());
            statement.setInt(index++, command.previousProficiencyBonus());
            statement.setInt(index++, command.newProficiencyBonus());
            statement.setLong(index++, character.rowVersion());
            statement.setLong(index, character.rowVersion() + 1);
            requireOne(statement.executeUpdate());
        }
    }

    private static void insertFeatureTransition(Connection connection, long eventId,
            LockedCharacter character, Command command) throws SQLException {
        if (command.featureTransition() == null) return;
        com.dndtool.service.ClassFeatureRules.Transition transition = command.featureTransition();
        if (transition.newlySelectedSubclassKey() != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_SUBCLASS_STATE_SQL)) {
                statement.setLong(1, character.characterId());
                statement.setLong(2, character.releaseId());
                statement.setString(3, command.advancementChoice().targetClassKey());
                statement.setString(4, transition.newlySelectedSubclassKey());
                statement.setInt(5, command.advancementChoice().previousClassLevel() + 1);
                statement.setLong(6, eventId);
                requireOne(statement.executeUpdate());
            }
        }
        if (!transition.featureUnlocks().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_FEATURE_STATE_SQL)) {
                for (com.dndtool.service.ClassFeatureRules.FeatureRule feature
                        : transition.featureUnlocks()) {
                    statement.setLong(1, character.characterId());
                    statement.setLong(2, character.releaseId());
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

    private static void insertAdvancementChoice(Connection connection, long eventId,
            LockedCharacter character, Command command) throws SQLException {
        if (command.advancementChoice() == null) return;
        com.dndtool.service.CharacterAdvancementChoiceRules.Prepared choice =
                command.advancementChoice();
        String choiceType = choice.featKey() != null ? "FEAT"
                : choice.abilityIncreases().isEmpty() ? "NONE" : "ABILITY_SCORE_IMPROVEMENT";
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_ADVANCEMENT_CHOICE_SQL)) {
            statement.setLong(1, eventId);
            statement.setLong(2, character.characterId());
            statement.setLong(3, character.releaseId());
            statement.setString(4, choice.targetClassKey());
            statement.setString(5, choiceType);
            if (choice.featKey() == null) {
                statement.setNull(6, Types.VARCHAR);
                statement.setNull(7, Types.VARCHAR);
            } else {
                statement.setString(6, "character.feat");
                statement.setString(7, choice.featKey());
            }
            statement.setString(8, choice.multiclass()
                    ? "BLOCKED_PENDING_SPELL_SYSTEM" : "NOT_APPLICABLE");
            requireOne(statement.executeUpdate());
        }
        if (!choice.abilityIncreases().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_ABILITY_SCORE_CHANGE_SQL)) {
                for (String ability : choice.abilityIncreases().keySet()) {
                    int previous = command.expected().abilityScores().get(ability);
                    statement.setLong(1, eventId);
                    statement.setLong(2, character.characterId());
                    statement.setLong(3, character.releaseId());
                    statement.setString(4, ability);
                    statement.setInt(5, previous);
                    statement.setInt(6, choice.abilityScores().get(ability));
                    statement.addBatch();
                }
                requireBatch(statement.executeBatch(), choice.abilityIncreases().size());
            }
        }
        if (choice.featKey() != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_FEAT_STATE_SQL)) {
                statement.setLong(1, character.characterId());
                statement.setLong(2, character.releaseId());
                statement.setString(3, choice.featKey());
                statement.setLong(4, eventId);
                requireOne(statement.executeUpdate());
            }
        }
        if (!choice.proficiencyGrants().isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    INSERT_MULTICLASS_PROFICIENCY_SQL)) {
                for (String proficiency : choice.proficiencyGrants()) {
                    statement.setLong(1, character.characterId());
                    statement.setLong(2, character.releaseId());
                    statement.setString(3, choice.targetClassKey());
                    statement.setString(4, proficiency);
                    statement.setLong(5, eventId);
                    statement.addBatch();
                }
                requireBatch(statement.executeBatch(), choice.proficiencyGrants().size());
            }
        }
    }

    private static void insertResourceChanges(Connection connection, long eventId,
            LockedCharacter character, List<ResourceChange> changes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_RESOURCE_CHANGE_SQL)) {
            for (ResourceChange change : changes) {
                statement.setLong(1, eventId);
                statement.setLong(2, character.characterId());
                statement.setLong(3, character.releaseId());
                statement.setString(4, change.resourceKey());
                if (change.previous() == null) {
                    statement.setNull(5, Types.BIGINT);
                    statement.setNull(6, Types.BIGINT);
                    statement.setNull(7, Types.TINYINT);
                } else {
                    statement.setLong(5, change.previous().currentValue());
                    statement.setLong(6, change.previous().maximumValue());
                    statement.setBoolean(7, change.previous().unlimited());
                }
                statement.setLong(8, change.next().currentValue());
                statement.setLong(9, change.next().maximumValue());
                statement.setBoolean(10, change.next().unlimited());
                requireOne(statement.executeUpdate());
            }
        }
    }

    private static void insertAudit(Connection connection, long eventId,
            LockedCharacter character, Command command, HitPointResolution hitPoints,
            List<ResourceChange> changes) throws SQLException {
        int order = 1;
        order = integerChange(connection, eventId, character, order, "character.level.total",
                (long) command.expected().totalLevel(), (long) command.targetLevel());
        int previousClassLevel = command.advancementChoice() == null
                ? command.expected().classLevel()
                : command.advancementChoice().previousClassLevel();
        String targetClassKey = command.advancementChoice() == null
                ? command.expected().classKey()
                : command.advancementChoice().targetClassKey();
        order = referenceChange(connection, eventId, character, order,
                "advancement.class", null, targetClassKey);
        order = integerChange(connection, eventId, character, order, "character.class.level",
                (long) previousClassLevel, (long) previousClassLevel + 1);
        order = integerChange(connection, eventId, character, order,
                "character.proficiency_bonus", (long) command.previousProficiencyBonus(),
                (long) command.newProficiencyBonus());
        order = referenceChange(connection, eventId, character, order,
                "advancement.hp_choice_algorithm", null, command.hpChoiceAlgorithm());
        order = integerChange(connection, eventId, character, order,
                "advancement.hit_die_sides", null, (long) command.hitDieSides());
        if (hitPoints.hitDieRoll() != null) {
            order = integerChange(connection, eventId, character, order,
                    "advancement.hit_die_roll", null, hitPoints.hitDieRoll().longValue());
        }
        order = integerChange(connection, eventId, character, order,
                "advancement.hit_point_increase", null,
                (long) hitPoints.hitPointIncrease());
        if (command.featureTransition() != null) {
            if (command.featureTransition().newlySelectedSubclassKey() != null) {
                order = referenceChange(connection, eventId, character, order,
                        "advancement.subclass", null,
                        command.featureTransition().newlySelectedSubclassKey());
            }
            for (com.dndtool.service.ClassFeatureRules.FeatureRule feature
                    : command.featureTransition().featureUnlocks()) {
                order = referenceChange(connection, eventId, character, order,
                        "advancement.feature", null, feature.featureKey());
            }
        }
        if (command.advancementChoice() != null) {
            for (String ability : command.advancementChoice().abilityIncreases().keySet()) {
                order = integerChange(connection, eventId, character, order, ability,
                        (long) command.expected().abilityScores().get(ability),
                        (long) command.advancementChoice().abilityScores().get(ability));
            }
            if (command.advancementChoice().featKey() != null) {
                order = referenceChange(connection, eventId, character, order,
                        "advancement.feat", null, command.advancementChoice().featKey());
            }
            for (String proficiency : command.advancementChoice().proficiencyGrants()) {
                order = referenceChange(connection, eventId, character, order,
                        "advancement.multiclass_proficiency", null, proficiency);
            }
        }
        for (ResourceChange change : changes) {
            Long oldCurrent = change.previous() == null
                    ? null : change.previous().currentValue();
            Long oldMaximum = change.previous() == null
                    ? null : change.previous().maximumValue();
            Boolean oldUnlimited = change.previous() == null
                    ? null : change.previous().unlimited();
            order = integerChange(connection, eventId, character, order,
                    change.resourceKey() + ".current", oldCurrent,
                    change.next().currentValue());
            order = integerChange(connection, eventId, character, order,
                    change.resourceKey() + ".maximum", oldMaximum,
                    change.next().maximumValue());
            order = booleanChange(connection, eventId, character, order,
                    change.resourceKey() + ".unlimited", oldUnlimited,
                    change.next().unlimited());
        }
    }

    private static int integerChange(Connection connection, long eventId,
            LockedCharacter character, int order, String key, Long oldValue, Long newValue)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_INTEGER_CHANGE_SQL)) {
            commonChange(statement, eventId, character, order, key);
            nullableLong(statement, 6, oldValue);
            nullableLong(statement, 7, newValue);
            requireOne(statement.executeUpdate());
            return order + 1;
        }
    }

    private static int booleanChange(Connection connection, long eventId,
            LockedCharacter character, int order, String key, Boolean oldValue, Boolean newValue)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_BOOLEAN_CHANGE_SQL)) {
            commonChange(statement, eventId, character, order, key);
            nullableBoolean(statement, 6, oldValue);
            nullableBoolean(statement, 7, newValue);
            requireOne(statement.executeUpdate());
            return order + 1;
        }
    }

    private static int referenceChange(Connection connection, long eventId,
            LockedCharacter character, int order, String key, String oldValue, String newValue)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_REFERENCE_CHANGE_SQL)) {
            commonChange(statement, eventId, character, order, key);
            nullableString(statement, 6, oldValue);
            nullableString(statement, 7, newValue);
            requireOne(statement.executeUpdate());
            return order + 1;
        }
    }

    private static void commonChange(PreparedStatement statement, long eventId,
            LockedCharacter character, int order, String key) throws SQLException {
        statement.setLong(1, eventId);
        statement.setLong(2, character.campaignId());
        statement.setLong(3, character.characterId());
        statement.setInt(4, order);
        statement.setString(5, key);
    }

    private static void insertOperation(Connection connection, long eventId,
            LockedCharacter character, Command command) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setString(2, command.requestDigestSha256());
            statement.setLong(3, character.campaignId());
            statement.setLong(4, character.characterId());
            statement.setLong(5, eventId);
            requireOne(statement.executeUpdate());
        }
    }

    private static Map<String, ResourceState> resourceMap(List<ResourceState> resources) {
        Map<String, ResourceState> result = new HashMap<>();
        for (ResourceState resource : resources) {
            if (resource == null || result.putIfAbsent(resource.resourceKey(), resource) != null) {
                return Map.of("", new ResourceState("", -1, -1, false));
            }
        }
        return result;
    }

    private static Result rejected(Result.Status status) {
        return new Result(status, null, null, null, null);
    }

    private static void requireOne(int count) throws SQLException {
        if (count != 1) throw new SQLException("Authoritative update count mismatch");
    }

    private static void requireBatch(int[] counts, int expected) throws SQLException {
        if (counts.length != expected) throw new SQLException("Authoritative batch mismatch");
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new SQLException("Authoritative batch mismatch");
            }
        }
    }

    private static void nullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static void nullableBoolean(PreparedStatement statement, int index, Boolean value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.TINYINT);
        else statement.setBoolean(index, value);
    }

    private static void nullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static String required(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null) throw invalidRow();
        return value;
    }

    private static SQLException invalidRow() {
        return new SQLException("Invalid authoritative database row");
    }

    private static Result commit(Connection connection, ConnectionState original, Result result)
            throws SQLException {
        connection.commit();
        restoreAndClose(connection, original);
        return result;
    }

    private static Result rollback(Connection connection, ConnectionState original, Result result)
            throws SQLException {
        connection.rollback();
        restoreAndClose(connection, original);
        return result;
    }

    private static void restoreAndClose(Connection connection, ConnectionState original)
            throws SQLException {
        restore(connection, original);
        connection.close();
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

    private record BasePreview(long characterId, String campaignKey, long eventTail,
            long rowVersion, String moduleKey, String releaseVersion, String hash,
            int constitutionScore, int charismaScore) {
    }

    private record LockedCharacter(long characterId, long campaignId, long releaseId,
            long rowVersion, long eventTail) {
    }

    private record ClassState(String classKey, int level) {
    }

    private record AbilityScores(int strength, int dexterity, int constitution,
            int intelligence, int wisdom, int charisma) {
        Map<String, Integer> asMap() {
            return Map.of("ability.strength", strength, "ability.dexterity", dexterity,
                    "ability.constitution", constitution,
                    "ability.intelligence", intelligence, "ability.wisdom", wisdom,
                    "ability.charisma", charisma);
        }
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
