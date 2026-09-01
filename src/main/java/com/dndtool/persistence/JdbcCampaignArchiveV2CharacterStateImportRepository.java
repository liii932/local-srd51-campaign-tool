package com.dndtool.persistence;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;

import com.dndtool.service.CampaignArchiveV2CharacterState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Restores the reviewed format-2 DRAFT current-state projection without owning the transaction. */
public final class JdbcCampaignArchiveV2CharacterStateImportRepository
        implements CampaignArchiveV2CharacterStateImportRepository {
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id, event_text)
            VALUES (?, ?, ?, ?, NULL)
            """;
    private static final String INSERT_SNAPSHOT_SQL = """
            INSERT INTO character_creation_snapshot_v2 (
                character_id, module_release_id, preview_digest_sha256,
                request_digest_sha256, ability_method_key, race_type, race_key,
                subrace_type, subrace_key, background_type, background_key,
                class_type, class_key, base_strength, base_dexterity,
                base_constitution, base_intelligence, base_wisdom, base_charisma,
                final_strength, final_dexterity, final_constitution,
                final_intelligence, final_wisdom, final_charisma, maximum_hit_points)
            VALUES (?, ?, ?, ?, ?, 'character.race', ?, ?, ?,
                    'character.background', ?, 'character.class', ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SELECTION_SQL = """
            INSERT INTO character_creation_selection_v2 (
                character_id, module_release_id, selection_kind,
                selection_order, selection_key)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_RESOURCE_SQL = """
            INSERT INTO character_resource_state_v2 (
                character_id, module_release_id, resource_type, resource_key,
                current_value, maximum_value, is_unlimited)
            VALUES (?, ?, 'character.resource', ?, ?, ?, ?)
            """;
    private static final String INSERT_CLASS_SQL = """
            INSERT INTO character_class_level_v2 (
                character_id, module_release_id, class_type, class_key, class_level)
            VALUES (?, ?, 'character.class', ?, ?)
            """;
    private static final String INSERT_SUBCLASS_SQL = """
            INSERT INTO character_subclass_state_v2 (
                character_id, module_release_id, class_type, class_key,
                subclass_type, subclass_key, selected_at_class_level, acquired_event_id)
            VALUES (?, ?, 'character.class', ?, 'character.subclass', ?, ?, ?)
            """;
    private static final String INSERT_FEATURE_SQL = """
            INSERT INTO character_feature_state_v2 (
                character_id, module_release_id, feature_type, feature_key,
                acquired_at_class_level, execution_mode, execution_algorithm,
                acquired_event_id)
            VALUES (?, ?, 'character.feature', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_FEATURE_CHOICE_SQL = """
            INSERT INTO character_feature_choice_v2 (
                character_id, module_release_id, source_feature_type,
                source_feature_key, choice_order, choice_type, choice_key,
                acquired_event_id)
            VALUES (?, ?, 'character.feature', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_FEAT_SQL = """
            INSERT INTO character_feat_state_v2 (
                character_id, module_release_id, feat_type, feat_key,
                acquired_event_id, state_origin)
            VALUES (?, ?, 'character.feat', ?, ?, 'ARCHIVE_RESTORE')
            """;
    private static final String INSERT_MULTICLASS_PROFICIENCY_SQL = """
            INSERT INTO character_multiclass_proficiency_v2 (
                character_id, module_release_id, class_type, class_key,
                proficiency_key, acquired_event_id, state_origin)
            VALUES (?, ?, 'character.class', ?, ?, ?, 'ARCHIVE_RESTORE')
            """;

    @Override
    public void append(Connection connection, Command command) throws SQLException {
        requireCallerTransaction(connection);
        Objects.requireNonNull(command, "format-2 character import command");
        CampaignArchiveV2CharacterState state = command.state().validated(
                command.characterIds().keySet());
        Map<Long, Long> eventIds = insertEvents(connection, command, state);
        insertSnapshots(connection, command, state);
        insertSelections(connection, command, state);
        insertResources(connection, command, state);
        insertClasses(connection, command, state);
        insertSubclasses(connection, command, state, eventIds);
        insertFeatures(connection, command, state, eventIds);
        insertFeatureChoices(connection, command, state, eventIds);
        insertFeats(connection, command, state, eventIds);
        insertMulticlassProficiencies(connection, command, state, eventIds);
    }

    private static Map<Long, Long> insertEvents(
            Connection connection, Command command, CampaignArchiveV2CharacterState state)
            throws SQLException {
        Map<Long, Long> ids = new HashMap<>();
        for (StateEvent event : state.stateEvents()) {
            try (PreparedStatement statement = prepareGenerated(connection, INSERT_EVENT_SQL)) {
                statement.setLong(1, command.campaignId());
                statement.setLong(2, event.eventSequence());
                statement.setString(3, event.eventType());
                statement.setLong(4, character(command, event.subjectCharacterKey()));
                long id = insert(statement, "format-2 state event");
                if (ids.putIfAbsent(event.eventSequence(), id) != null) {
                    throw invalidState("Duplicate format-2 event sequence");
                }
            }
        }
        return Map.copyOf(ids);
    }

    private static void insertSnapshots(
            Connection connection, Command command, CampaignArchiveV2CharacterState state)
            throws SQLException {
        for (CreationSnapshot snapshot : state.creationSnapshots()) {
            Map<String, AbilityScore> abilities = new HashMap<>();
            for (AbilityScore ability : snapshot.abilities()) {
                abilities.put(ability.abilityKey(), ability);
            }
            try (PreparedStatement statement = prepare(connection, INSERT_SNAPSHOT_SQL)) {
                statement.setLong(1, character(command, snapshot.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, snapshot.previewDigestSha256());
                statement.setString(4, snapshot.requestDigestSha256());
                statement.setString(5, snapshot.abilityMethodKey());
                statement.setString(6, snapshot.raceKey());
                if (snapshot.subraceKey() == null) {
                    statement.setNull(7, Types.VARCHAR);
                    statement.setNull(8, Types.VARCHAR);
                } else {
                    statement.setString(7, "character.subrace");
                    statement.setString(8, snapshot.subraceKey());
                }
                statement.setString(9, snapshot.backgroundKey());
                statement.setString(10, snapshot.classKey());
                int index = 11;
                for (String key : new String[] {
                        "ability.strength", "ability.dexterity", "ability.constitution",
                        "ability.intelligence", "ability.wisdom", "ability.charisma"}) {
                    statement.setInt(index++, abilities.get(key).baseScore());
                }
                for (String key : new String[] {
                        "ability.strength", "ability.dexterity", "ability.constitution",
                        "ability.intelligence", "ability.wisdom", "ability.charisma"}) {
                    statement.setInt(index++, abilities.get(key).finalScore());
                }
                statement.setInt(index, snapshot.maximumHitPoints());
                one(statement, "format-2 creation snapshot");
            }
        }
    }

    private static void insertSelections(
            Connection connection, Command command, CampaignArchiveV2CharacterState state)
            throws SQLException {
        for (CreationSelection value : state.creationSelections()) {
            try (PreparedStatement statement = prepare(connection, INSERT_SELECTION_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.selectionKind());
                statement.setInt(4, value.selectionOrder());
                statement.setString(5, value.selectionKey());
                one(statement, "format-2 creation selection");
            }
        }
    }

    private static void insertResources(
            Connection connection, Command command, CampaignArchiveV2CharacterState state)
            throws SQLException {
        for (ResourceState value : state.resources()) {
            try (PreparedStatement statement = prepare(connection, INSERT_RESOURCE_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.resourceKey());
                statement.setLong(4, value.currentValue());
                statement.setLong(5, value.maximumValue());
                statement.setInt(6, value.unlimited() ? 1 : 0);
                one(statement, "format-2 resource state");
            }
        }
    }

    private static void insertClasses(
            Connection connection, Command command, CampaignArchiveV2CharacterState state)
            throws SQLException {
        for (ClassLevel value : state.classLevels()) {
            try (PreparedStatement statement = prepare(connection, INSERT_CLASS_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.classKey());
                statement.setInt(4, value.classLevel());
                one(statement, "format-2 class level");
            }
        }
    }

    private static void insertSubclasses(
            Connection connection, Command command, CampaignArchiveV2CharacterState state,
            Map<Long, Long> eventIds) throws SQLException {
        for (SubclassState value : state.subclasses()) {
            try (PreparedStatement statement = prepare(connection, INSERT_SUBCLASS_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.classKey());
                statement.setString(4, value.subclassKey());
                statement.setInt(5, value.selectedAtClassLevel());
                statement.setLong(6, event(eventIds, value.acquiredEventSequence()));
                one(statement, "format-2 subclass state");
            }
        }
    }

    private static void insertFeatures(
            Connection connection, Command command, CampaignArchiveV2CharacterState state,
            Map<Long, Long> eventIds) throws SQLException {
        for (FeatureState value : state.features()) {
            try (PreparedStatement statement = prepare(connection, INSERT_FEATURE_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.featureKey());
                statement.setInt(4, value.acquiredAtClassLevel());
                statement.setString(5, value.executionMode());
                statement.setString(6, value.executionAlgorithm());
                statement.setLong(7, event(eventIds, value.acquiredEventSequence()));
                one(statement, "format-2 feature state");
            }
        }
    }

    private static void insertFeatureChoices(
            Connection connection, Command command, CampaignArchiveV2CharacterState state,
            Map<Long, Long> eventIds) throws SQLException {
        for (FeatureChoice value : state.featureChoices()) {
            try (PreparedStatement statement = prepare(connection, INSERT_FEATURE_CHOICE_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.sourceFeatureKey());
                statement.setInt(4, value.choiceOrder());
                statement.setString(5, value.choiceType());
                statement.setString(6, value.choiceKey());
                statement.setLong(7, event(eventIds, value.acquiredEventSequence()));
                one(statement, "format-2 feature choice");
            }
        }
    }

    private static void insertFeats(
            Connection connection, Command command, CampaignArchiveV2CharacterState state,
            Map<Long, Long> eventIds) throws SQLException {
        for (FeatState value : state.feats()) {
            try (PreparedStatement statement = prepare(connection, INSERT_FEAT_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.featKey());
                statement.setLong(4, event(eventIds, value.acquiredEventSequence()));
                one(statement, "format-2 feat state");
            }
        }
    }

    private static void insertMulticlassProficiencies(
            Connection connection, Command command, CampaignArchiveV2CharacterState state,
            Map<Long, Long> eventIds) throws SQLException {
        for (MulticlassProficiency value : state.multiclassProficiencies()) {
            try (PreparedStatement statement = prepare(
                    connection, INSERT_MULTICLASS_PROFICIENCY_SQL)) {
                statement.setLong(1, character(command, value.characterKey()));
                statement.setLong(2, command.moduleReleaseId());
                statement.setString(3, value.classKey());
                statement.setString(4, value.proficiencyKey());
                statement.setLong(5, event(eventIds, value.acquiredEventSequence()));
                one(statement, "format-2 multiclass proficiency");
            }
        }
    }

    private static long character(Command command, String key) throws SQLException {
        Long id = command.characterIds().get(key);
        if (id == null || id <= 0) throw invalidState("Unknown format-2 character key");
        return id;
    }

    private static long event(Map<Long, Long> eventIds, long sequence) throws SQLException {
        Long id = eventIds.get(sequence);
        if (id == null || id <= 0) throw invalidState("Unknown format-2 state event");
        return id;
    }

    private static PreparedStatement prepare(Connection connection, String sql)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static PreparedStatement prepareGenerated(Connection connection, String sql)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static long insert(PreparedStatement statement, String description)
            throws SQLException {
        if (statement.executeUpdate() != 1) throw invalidState(description + " was not inserted");
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw invalidState(description + " identity is missing");
            long id = keys.getLong(1);
            if (keys.wasNull() || id <= 0 || keys.next()) {
                throw invalidState(description + " identity is invalid");
            }
            return id;
        }
    }

    private static void one(PreparedStatement statement, String description)
            throws SQLException {
        if (statement.executeUpdate() != 1) throw invalidState(description + " was not inserted");
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.getAutoCommit()) {
            throw new SQLException("Format-2 character import requires a caller-owned transaction");
        }
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }
}
