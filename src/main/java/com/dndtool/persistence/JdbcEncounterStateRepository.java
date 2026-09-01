package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** JDBC implementation for the initial node-map encounter aggregate. */
public final class JdbcEncounterStateRepository implements EncounterStateRepository {
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MODULE_KEY =
            Pattern.compile("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*");
    private static final Pattern RELEASE_VERSION =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern STABLE_KEY = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private static final String LOCK_CAMPAIGN_SQL = """
            SELECT campaign_status
              FROM campaign
             WHERE id = ?
             FOR UPDATE
            """;
    private static final String READ_BINDING_SQL = """
            SELECT module_release_id, frozen_module_key, frozen_release_version, frozen_content_sha256
              FROM campaign_module
             WHERE campaign_id = ?
            """;
    private static final String READ_MAP_SQL = """
            SELECT map_definition.map_type
              FROM module_map_definition AS map_definition
              JOIN module_release AS release_definition
                ON release_definition.id = map_definition.module_release_id
             WHERE map_definition.module_release_id = ?
               AND map_definition.map_key = ?
               AND release_definition.module_key = ?
               AND release_definition.release_version = ?
               AND release_definition.content_sha256 = ?
               AND release_definition.release_status = 'RELEASED'
            """;
    private static final String READ_NODES_SQL = """
            SELECT node_key
              FROM module_map_node
             WHERE module_release_id = ?
               AND map_key = ?
             ORDER BY node_key
            """;
    private static final String LOCK_CHARACTER_SQL = """
            SELECT character_key, campaign_id, module_release_id, character_status,
                   saved_module_key, saved_release_version, saved_content_sha256
              FROM character_record
             WHERE id = ?
             FOR UPDATE
            """;
    private static final String INSERT_MAP_INSTANCE_SQL = """
            INSERT INTO map_instance (campaign_id, module_release_id, map_key, map_type)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_PARTY_POSITION_SQL = """
            INSERT INTO party_world_position
                (campaign_id, map_instance_id, module_release_id, map_key, node_key)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_BATTLE_SQL = """
            INSERT INTO battle_state (campaign_id, map_instance_id, module_release_id, map_key)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_PARTICIPANT_SQL = """
            INSERT INTO battle_participant (battle_id, campaign_id, character_id, faction)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_ENTITY_POSITION_SQL = """
            INSERT INTO entity_position
                (battle_id, campaign_id, active_campaign_id, map_instance_id,
                 module_release_id, map_key, character_id, node_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Override
    public SavedEncounter initialize(Connection connection, Command command) throws SQLException {
        requireCallerTransaction(connection);
        ValidatedCommand validated = validate(command);

        // All catalog and target validation precedes the first insert so failed requests consume no state.
        requireActiveCampaign(connection, validated.command().campaignId());
        requireMatchingBinding(connection, validated.command());
        requireMatchingMap(connection, validated.command());
        requireKnownNodes(connection, validated);
        List<ResolvedParticipant> resolved = resolveAndLockParticipants(connection, validated);

        long mapInstanceId = insertMapInstance(connection, validated.command());
        insertPartyPosition(connection, validated.command(), mapInstanceId);
        long battleId = insertBattle(connection, validated.command(), mapInstanceId);

        List<SavedParticipant> savedParticipants = new ArrayList<>(resolved.size());
        for (ResolvedParticipant participant : resolved) {
            long participantId = insertParticipant(connection, validated.command(), battleId, participant);
            insertEntityPosition(connection, validated.command(), battleId, mapInstanceId, participant);
            savedParticipants.add(new SavedParticipant(
                    participantId,
                    participant.characterId(),
                    participant.placement().characterKey(),
                    participant.placement().faction(),
                    participant.placement().nodeKey()));
        }
        return new SavedEncounter(mapInstanceId, battleId, savedParticipants);
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        if (connection.getAutoCommit()
                || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw new SQLException("A writable SERIALIZABLE caller-owned transaction is required");
        }
    }

    private static ValidatedCommand validate(Command command) throws SQLException {
        Objects.requireNonNull(command, "command");
        if (command.campaignId() <= 0
                || command.moduleReleaseId() <= 0
                || command.moduleKey() == null
                || !MODULE_KEY.matcher(command.moduleKey()).matches()
                || command.releaseVersion() == null
                || !RELEASE_VERSION.matcher(command.releaseVersion()).matches()
                || !SHA_256.matcher(command.contentSha256()).matches()
                || !EncounterStateServiceIdentity.MAP_KEY.equals(command.mapKey())
                || !EncounterStateServiceIdentity.MAP_TYPE.equals(command.mapType())
                || !STABLE_KEY.matcher(command.partyNodeKey()).matches()) {
            throw new SQLException("Encounter command identity is invalid");
        }

        Set<String> characterKeys = new HashSet<>();
        Set<String> requiredNodes = new HashSet<>();
        requiredNodes.add(command.partyNodeKey());
        for (ParticipantPlacement participant : command.participants()) {
            if (participant == null
                    || !CANONICAL_UUID.matcher(participant.characterKey()).matches()
                    || participant.faction() == null
                    || !STABLE_KEY.matcher(participant.nodeKey()).matches()
                    || !characterKeys.add(participant.characterKey())) {
                throw new SQLException("Encounter participant command is invalid");
            }
            requiredNodes.add(participant.nodeKey());
        }
        return new ValidatedCommand(command, Set.copyOf(requiredNodes));
    }

    private static void requireActiveCampaign(Connection connection, long campaignId) throws SQLException {
        try (PreparedStatement statement = prepare(connection, LOCK_CAMPAIGN_SQL)) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"ACTIVE".equals(result.getString(1)) || result.next()) {
                    throw new SQLException("Campaign is missing or not active");
                }
            }
        }
    }

    private static void requireMatchingBinding(Connection connection, Command command) throws SQLException {
        try (PreparedStatement statement = prepare(connection, READ_BINDING_SQL)) {
            statement.setLong(1, command.campaignId());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || result.getLong(1) != command.moduleReleaseId()
                        || !command.moduleKey().equals(result.getString(2))
                        || !command.releaseVersion().equals(result.getString(3))
                        || !command.contentSha256().equals(result.getString(4))
                        || result.next()) {
                    throw new SQLException("Campaign frozen module binding does not match the command");
                }
            }
        }
    }

    private static void requireMatchingMap(Connection connection, Command command) throws SQLException {
        try (PreparedStatement statement = prepare(connection, READ_MAP_SQL)) {
            statement.setLong(1, command.moduleReleaseId());
            statement.setString(2, command.mapKey());
            statement.setString(3, command.moduleKey());
            statement.setString(4, command.releaseVersion());
            statement.setString(5, command.contentSha256());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !command.mapType().equals(result.getString(1)) || result.next()) {
                    throw new SQLException("Frozen map definition does not match the command");
                }
            }
        }
    }

    private static void requireKnownNodes(Connection connection, ValidatedCommand validated) throws SQLException {
        Set<String> available = new HashSet<>();
        Command command = validated.command();
        try (PreparedStatement statement = prepare(connection, READ_NODES_SQL)) {
            statement.setLong(1, command.moduleReleaseId());
            statement.setString(2, command.mapKey());
            statement.setMaxRows(1000);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    available.add(result.getString(1));
                }
            }
        }
        if (!available.containsAll(validated.requiredNodes())) {
            throw new SQLException("One or more positions reference an unknown frozen map node");
        }
    }

    private static List<ResolvedParticipant> resolveAndLockParticipants(
            Connection connection,
            ValidatedCommand validated) throws SQLException {
        if (validated.command().participants().isEmpty()) {
            return List.of();
        }

        Map<String, ParticipantPlacement> placements = new HashMap<>();
        for (ParticipantPlacement placement : validated.command().participants()) {
            placements.put(placement.characterKey(), placement);
        }
        String placeholders = String.join(", ", Collections.nCopies(placements.size(), "?"));
        String resolveSql = "SELECT id, character_key FROM character_record "
                + "WHERE campaign_id = ? AND character_key IN (" + placeholders + ") ORDER BY id";
        List<ResolvedParticipant> resolved = new ArrayList<>(placements.size());
        try (PreparedStatement statement = prepare(connection, resolveSql)) {
            statement.setLong(1, validated.command().campaignId());
            int parameter = 2;
            for (String characterKey : placements.keySet().stream().sorted().toList()) {
                statement.setString(parameter++, characterKey);
            }
            statement.setMaxRows(placements.size() + 1);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String characterKey = result.getString(2);
                    ParticipantPlacement placement = placements.get(characterKey);
                    if (placement == null) {
                        throw new SQLException("Character resolution returned an unexpected row");
                    }
                    resolved.add(new ResolvedParticipant(result.getLong(1), placement));
                }
            }
        }
        if (resolved.size() != placements.size()) {
            throw new SQLException("One or more encounter characters do not belong to the campaign");
        }
        resolved.sort(Comparator.comparingLong(ResolvedParticipant::characterId));
        for (ResolvedParticipant participant : resolved) {
            lockAndVerifyCharacter(connection, validated.command(), participant);
        }
        return List.copyOf(resolved);
    }

    private static void lockAndVerifyCharacter(
            Connection connection,
            Command command,
            ResolvedParticipant participant) throws SQLException {
        try (PreparedStatement statement = prepare(connection, LOCK_CHARACTER_SQL)) {
            statement.setLong(1, participant.characterId());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !participant.placement().characterKey().equals(result.getString(1))
                        || result.getLong(2) != command.campaignId()
                        || result.getLong(3) != command.moduleReleaseId()
                        || !"ACTIVE".equals(result.getString(4))) {
                    throw new SQLException("Encounter character is missing, archived, or bound to another module");
                }
                String savedModuleKey = result.getString(5);
                String savedReleaseVersion = result.getString(6);
                String savedContentSha256 = result.getString(7);
                if (result.next()) {
                    throw new SQLException("Encounter character lock was not unique");
                }
                if (!command.moduleKey().equals(savedModuleKey)
                        || !command.releaseVersion().equals(savedReleaseVersion)
                        || !command.contentSha256().equals(savedContentSha256)) {
                    throw new EncounterStateRepository.ModuleHashMismatchException();
                }
            }
        }
    }

    private static long insertMapInstance(Connection connection, Command command) throws SQLException {
        try (PreparedStatement statement = prepareGenerated(connection, INSERT_MAP_INSTANCE_SQL)) {
            statement.setLong(1, command.campaignId());
            statement.setLong(2, command.moduleReleaseId());
            statement.setString(3, command.mapKey());
            statement.setString(4, command.mapType());
            return executeSingleInsert(statement, "map instance");
        }
    }

    private static void insertPartyPosition(
            Connection connection,
            Command command,
            long mapInstanceId) throws SQLException {
        try (PreparedStatement statement = prepare(connection, INSERT_PARTY_POSITION_SQL)) {
            statement.setLong(1, command.campaignId());
            statement.setLong(2, mapInstanceId);
            statement.setLong(3, command.moduleReleaseId());
            statement.setString(4, command.mapKey());
            statement.setString(5, command.partyNodeKey());
            executeSingleUpdate(statement, "party world position");
        }
    }

    private static long insertBattle(
            Connection connection,
            Command command,
            long mapInstanceId) throws SQLException {
        try (PreparedStatement statement = prepareGenerated(connection, INSERT_BATTLE_SQL)) {
            statement.setLong(1, command.campaignId());
            statement.setLong(2, mapInstanceId);
            statement.setLong(3, command.moduleReleaseId());
            statement.setString(4, command.mapKey());
            return executeSingleInsert(statement, "battle state");
        }
    }

    private static long insertParticipant(
            Connection connection,
            Command command,
            long battleId,
            ResolvedParticipant participant) throws SQLException {
        try (PreparedStatement statement = prepareGenerated(connection, INSERT_PARTICIPANT_SQL)) {
            statement.setLong(1, battleId);
            statement.setLong(2, command.campaignId());
            statement.setLong(3, participant.characterId());
            statement.setString(4, participant.placement().faction().name());
            return executeSingleInsert(statement, "battle participant");
        }
    }

    private static void insertEntityPosition(
            Connection connection,
            Command command,
            long battleId,
            long mapInstanceId,
            ResolvedParticipant participant) throws SQLException {
        try (PreparedStatement statement = prepare(connection, INSERT_ENTITY_POSITION_SQL)) {
            statement.setLong(1, battleId);
            statement.setLong(2, command.campaignId());
            statement.setLong(3, command.campaignId());
            statement.setLong(4, mapInstanceId);
            statement.setLong(5, command.moduleReleaseId());
            statement.setString(6, command.mapKey());
            statement.setLong(7, participant.characterId());
            statement.setString(8, participant.placement().nodeKey());
            executeSingleUpdate(statement, "entity position");
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static PreparedStatement prepareGenerated(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static long executeSingleInsert(PreparedStatement statement, String target) throws SQLException {
        executeSingleUpdate(statement, target);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("Insert did not return the generated " + target + " id");
            }
            long id = keys.getLong(1);
            if (id <= 0 || keys.next()) {
                throw new SQLException("Insert returned invalid generated keys for " + target);
            }
            return id;
        }
    }

    private static void executeSingleUpdate(PreparedStatement statement, String target) throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw new SQLException("Expected one affected row for " + target);
        }
    }

    private record ValidatedCommand(Command command, Set<String> requiredNodes) {
    }

    private record ResolvedParticipant(long characterId, ParticipantPlacement placement) {
    }

    /** Keeps persistence validation independent from the service implementation class. */
    private static final class EncounterStateServiceIdentity {
        private static final String MAP_KEY = "map.tavern_cellar";
        private static final String MAP_TYPE = "NODE";

        private EncounterStateServiceIdentity() {
        }
    }
}
