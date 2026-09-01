package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** JDBC transaction for a versioned lifecycle update and its complete audit trail. */
public final class JdbcCharacterLifecycleMutationRepository
        implements CharacterLifecycleMutationRepository {
    private static final String LOCK_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, character_id, result_status
            FROM host_operation WHERE request_id = ? FOR UPDATE
            """;
    private static final String LOCK_CHARACTER_SQL = """
            SELECT cr.id, cr.campaign_id, cr.module_release_id, cr.character_name,
                   cr.character_type, cr.character_status, cr.row_version,
                   cr.saved_module_key, cr.saved_release_version, cr.saved_content_sha256
            FROM character_record AS cr
            WHERE cr.character_key = ?
            FOR UPDATE
            """;
    private static final String LOCK_CAMPAIGN_SQL = """
            SELECT internal_event_tail FROM campaign
            WHERE id = ? AND campaign_status = 'ACTIVE' FOR UPDATE
            """;
    private static final String LOAD_FROZEN_BINDING_SQL = """
            SELECT cm.module_release_id AS frozen_release_id,
                   cm.frozen_module_key, cm.frozen_release_version,
                   cm.frozen_content_sha256, mr.module_key, mr.release_version,
                   mr.content_sha256, mr.release_status
            FROM campaign_module AS cm
            JOIN module_release AS mr ON mr.id = ?
            WHERE cm.campaign_id = ?
            """;
    private static final String UPDATE_NAME_SQL =
            "UPDATE character_record SET character_name = ? WHERE id = ?";
    private static final String UPDATE_TYPE_SQL =
            "UPDATE character_record SET character_type = ? WHERE id = ?";
    private static final String UPDATE_STATUS_SQL =
            "UPDATE character_record SET character_status = ? WHERE id = ?";
    private static final String ADVANCE_VERSION_SQL = """
            UPDATE character_record SET row_version = row_version + 1
            WHERE id = ? AND row_version = ?
            """;
    private static final String ADVANCE_EVENT_TAIL_SQL = """
            UPDATE campaign SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id, event_text)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_text, new_text)
            VALUES (?, ?, ?, 1, ?, 'TEXT', ?, ?)
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, character_id, result_status, completed_at)
            VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    private final DataSource dataSource;

    public JdbcCharacterLifecycleMutationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Result mutate(Command command) throws SQLException {
        validate(command);
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                Result replay = findExistingOperation(connection, command);
                if (replay != null) {
                    connection.commit();
                    restore(connection, original);
                    return replay;
                }
                LockedCharacter character = lockCharacter(connection, command.characterKey());
                Result rejected = rejectInvalidState(command, character);
                if (rejected != null) {
                    connection.rollback();
                    restore(connection, original);
                    return rejected;
                }

                Change change = changeFor(command, character);
                if (change.oldValue().equals(change.newValue())) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Status.NO_CHANGE, character.rowVersion());
                }

                updateRoot(connection, command.action(), change.newValue(), character.id());
                long nextSequence = Math.addExact(character.eventTail(), 1L);
                advanceEventTail(connection, character, nextSequence);
                long eventId = insertEvent(connection, command, character, nextSequence);
                insertChange(connection, command, character, change, eventId);
                advanceVersion(connection, command, character);
                insertOperation(connection, command, character);

                long nextVersion = Math.addExact(character.rowVersion(), 1L);
                connection.commit();
                restore(connection, original);
                return new Result(Status.UPDATED, nextVersion);
            } catch (SQLException | RuntimeException exception) {
                rollbackAndRestore(connection, original, exception);
                throw exception;
            }
        }
    }

    private static Result findExistingOperation(Connection connection, Command command)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                String digest = requireString(result, "request_digest_sha256");
                String operation = requireString(result, "operation_type");
                long characterId = result.getLong("character_id");
                boolean missingCharacter = result.wasNull();
                String status = requireString(result, "result_status");
                if (result.next()) {
                    throw invalidState();
                }
                if (!digest.equals(command.requestDigestSha256())
                        || !operation.equals(command.action().operationType())) {
                    return new Result(Status.IDEMPOTENCY_CONFLICT, null);
                }
                if (missingCharacter || characterId <= 0 || !"SUCCEEDED".equals(status)) {
                    throw invalidState();
                }
                return new Result(Status.ALREADY_SUCCEEDED,
                        Math.addExact(command.expectedRowVersion(), 1L));
            }
        }
    }

    private static LockedCharacter lockCharacter(Connection connection, String characterKey)
            throws SQLException {
        LockedCharacterRoot root;
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CHARACTER_SQL)) {
            statement.setString(1, characterKey);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                root = new LockedCharacterRoot(
                        positiveLong(result, "id"), positiveLong(result, "campaign_id"),
                        positiveLong(result, "module_release_id"),
                        requireString(result, "character_name"),
                        requireString(result, "character_type"),
                        requireString(result, "character_status"),
                        nonNegativeLong(result, "row_version"),
                        requireString(result, "saved_module_key"),
                        requireString(result, "saved_release_version"),
                        requireString(result, "saved_content_sha256"));
                if (result.next()) {
                    throw invalidState();
                }
            }
        }
        Long eventTail = lockCampaign(connection, root.campaignId());
        if (eventTail == null) return null;
        // Frozen bindings and released modules remain read-only even during a
        // lifecycle mutation; only mutable aggregate roots require row locks.
        return loadFrozenBinding(connection, root, eventTail);
    }

    private static Long lockCampaign(Connection connection, long campaignId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CAMPAIGN_SQL)) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                long eventTail = nonNegativeLong(result, "internal_event_tail");
                if (result.next()) throw invalidState();
                return eventTail;
            }
        }
    }

    private static LockedCharacter loadFrozenBinding(
            Connection connection, LockedCharacterRoot root, long eventTail)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOAD_FROZEN_BINDING_SQL)) {
            statement.setLong(1, root.moduleReleaseId());
            statement.setLong(2, root.campaignId());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                LockedCharacter character = new LockedCharacter(
                        root.id(), root.campaignId(), root.moduleReleaseId(),
                        root.characterName(), root.characterType(), root.characterStatus(),
                        root.rowVersion(), root.savedModuleKey(), root.savedReleaseVersion(),
                        root.savedContentSha256(), eventTail,
                        positiveLong(result, "frozen_release_id"),
                        requireString(result, "frozen_module_key"),
                        requireString(result, "frozen_release_version"),
                        requireString(result, "frozen_content_sha256"),
                        requireString(result, "module_key"),
                        requireString(result, "release_version"),
                        requireString(result, "content_sha256"),
                        requireString(result, "release_status"));
                if (result.next()) throw invalidState();
                return character;
            }
        }
    }

    private static Result rejectInvalidState(Command command, LockedCharacter character) {
        if (character == null) {
            return new Result(Status.NOT_FOUND, null);
        }
        if (character.rowVersion() != command.expectedRowVersion()) {
            return new Result(Status.VERSION_CONFLICT, character.rowVersion());
        }
        boolean bindingMatches = character.moduleReleaseId() == character.frozenReleaseId()
                && "RELEASED".equals(character.releaseStatus())
                && command.moduleKey().equals(character.savedModuleKey())
                && command.moduleKey().equals(character.frozenModuleKey())
                && command.moduleKey().equals(character.moduleKey())
                && command.releaseVersion().equals(character.savedReleaseVersion())
                && command.releaseVersion().equals(character.frozenReleaseVersion())
                && command.releaseVersion().equals(character.releaseVersion())
                && command.contentSha256().equals(character.savedContentSha256())
                && command.contentSha256().equals(character.frozenContentSha256())
                && command.contentSha256().equals(character.releaseContentSha256());
        return bindingMatches ? null : new Result(Status.MODULE_BINDING_MISMATCH, null);
    }

    private static Change changeFor(Command command, LockedCharacter character) {
        return switch (command.action()) {
            case RENAME -> new Change(character.characterName(), command.newValue());
            case CHANGE_TYPE -> new Change(character.characterType(), command.newValue());
            case ARCHIVE -> new Change(character.characterStatus(), "ARCHIVED");
            case RESTORE -> new Change(character.characterStatus(), "ACTIVE");
        };
    }

    private static void updateRoot(
            Connection connection, Action action, String value, long characterId)
            throws SQLException {
        String sql = switch (action) {
            case RENAME -> UPDATE_NAME_SQL;
            case CHANGE_TYPE -> UPDATE_TYPE_SQL;
            case ARCHIVE, RESTORE -> UPDATE_STATUS_SQL;
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setLong(2, characterId);
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static void advanceEventTail(
            Connection connection, LockedCharacter character, long nextSequence)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADVANCE_EVENT_TAIL_SQL)) {
            statement.setLong(1, nextSequence);
            statement.setLong(2, character.campaignId());
            statement.setLong(3, character.eventTail());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static long insertEvent(
            Connection connection, Command command, LockedCharacter character, long sequence)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, character.campaignId());
            statement.setLong(2, sequence);
            statement.setString(3, command.action().eventType());
            statement.setLong(4, character.id());
            statement.setString(5, eventText(command.action()));
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw invalidState();
                }
                long id = keys.getLong(1);
                if (keys.wasNull() || id <= 0 || keys.next()) {
                    throw invalidState();
                }
                return id;
            }
        }
    }

    private static void insertChange(
            Connection connection,
            Command command,
            LockedCharacter character,
            Change change,
            long eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHANGE_SQL)) {
            statement.setLong(1, eventId);
            statement.setLong(2, character.campaignId());
            statement.setLong(3, character.id());
            statement.setString(4, command.action().changeKey());
            statement.setString(5, change.oldValue());
            statement.setString(6, change.newValue());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static void advanceVersion(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADVANCE_VERSION_SQL)) {
            statement.setLong(1, character.id());
            statement.setLong(2, command.expectedRowVersion());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static void insertOperation(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setString(2, command.requestDigestSha256());
            statement.setString(3, command.action().operationType());
            statement.setLong(4, character.campaignId());
            statement.setLong(5, character.id());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static String eventText(Action action) {
        return switch (action) {
            case RENAME -> "Renamed character";
            case CHANGE_TYPE -> "Changed character type";
            case ARCHIVE -> "Archived character";
            case RESTORE -> "Restored character";
        };
    }

    private static void validate(Command command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.action(), "action");
        if (!isCanonicalUuid(command.requestId()) || !isCanonicalUuid(command.characterKey())
                || command.expectedRowVersion() < 0
                || command.expectedRowVersion() == Long.MAX_VALUE
                || !isSha256(command.requestDigestSha256())
                || !isSha256(command.contentSha256())) {
            throw new IllegalArgumentException("Invalid lifecycle mutation command");
        }
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value <= 0) {
            throw invalidState();
        }
        return value;
    }

    private static long nonNegativeLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value < 0) {
            throw invalidState();
        }
        return value;
    }

    private static String requireString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null) {
            throw invalidState();
        }
        return value;
    }

    private static void requireOne(int updateCount) throws SQLException {
        if (updateCount != 1) {
            throw invalidState();
        }
    }

    private static SQLException invalidState() {
        return new SQLException("Invalid character lifecycle persistence state");
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
        connection.setTransactionIsolation(original.transactionIsolation());
    }

    private record Change(String oldValue, String newValue) {
    }

    private record LockedCharacter(
            long id,
            long campaignId,
            long moduleReleaseId,
            String characterName,
            String characterType,
            String characterStatus,
            long rowVersion,
            String savedModuleKey,
            String savedReleaseVersion,
            String savedContentSha256,
            long eventTail,
            long frozenReleaseId,
            String frozenModuleKey,
            String frozenReleaseVersion,
            String frozenContentSha256,
            String moduleKey,
            String releaseVersion,
            String releaseContentSha256,
            String releaseStatus) {
    }

    private record LockedCharacterRoot(
            long id,
            long campaignId,
            long moduleReleaseId,
            String characterName,
            String characterType,
            String characterStatus,
            long rowVersion,
            String savedModuleKey,
            String savedReleaseVersion,
            String savedContentSha256) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int transactionIsolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
