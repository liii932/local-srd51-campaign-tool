package com.dndtool.persistence;

import com.dndtool.persistence.CharacterCardMutationRepository.Action;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Executes one validated card or item change together with audit and idempotency rows. */
public final class JdbcCharacterCardMutationRepository
        implements CharacterCardMutationRepository {
    private static final String LOCK_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, character_id, result_status
            FROM host_operation WHERE request_id = ? FOR UPDATE
            """;
    private static final String CURRENT_VERSION_SQL =
            "SELECT row_version FROM character_record WHERE id = ?";
    private static final String LOCK_CHARACTER_SQL = """
            SELECT cr.id, cr.campaign_id, cr.module_release_id, cr.row_version,
                   cr.saved_module_key, cr.saved_release_version, cr.saved_content_sha256
            FROM character_record AS cr
            WHERE cr.character_key = ? FOR UPDATE
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
    private static final String LOCK_INTEGER_FIELDS_SQL = """
            SELECT field_key, value_type, integer_value
            FROM character_field_value WHERE character_id = ? FOR UPDATE
            """;
    private static final String UPDATE_INTEGER_FIELD_SQL = """
            UPDATE character_field_value SET integer_value = ?
            WHERE character_id = ? AND field_key = ? AND value_type = 'INTEGER'
            """;
    private static final String LOCK_CLASSES_SQL = """
            SELECT class_key, class_level FROM character_class_level
            WHERE character_id = ? FOR UPDATE
            """;
    private static final String INSERT_CLASS_SQL = """
            INSERT INTO character_class_level
                (character_id, module_release_id, class_key, class_level)
            VALUES (?, ?, ?, ?)
            """;
    private static final String UPDATE_CLASS_SQL = """
            UPDATE character_class_level SET class_level = ?
            WHERE character_id = ? AND class_key = ?
            """;
    private static final String DELETE_CLASS_SQL = """
            DELETE FROM character_class_level WHERE character_id = ? AND class_key = ?
            """;
    private static final String LOCK_SKILL_SQL = """
            SELECT proficiency_key FROM character_skill_proficiency
            WHERE character_id = ? AND skill_key = ? FOR UPDATE
            """;
    private static final String UPDATE_SKILL_SQL = """
            UPDATE character_skill_proficiency SET proficiency_key = ?
            WHERE character_id = ? AND skill_key = ?
            """;
    private static final String LOCK_SAVE_SQL = """
            SELECT proficiency_key FROM character_save_proficiency
            WHERE character_id = ? AND save_key = ? FOR UPDATE
            """;
    private static final String UPDATE_SAVE_SQL = """
            UPDATE character_save_proficiency SET proficiency_key = ?
            WHERE character_id = ? AND save_key = ?
            """;
    private static final String INSERT_MODULE_ITEM_SQL = """
            INSERT INTO item_instance (
                character_id, source_kind, module_release_id, item_key,
                item_name, item_description, quantity)
            VALUES (?, 'MODULE', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_TEMPORARY_ITEM_SQL = """
            INSERT INTO item_instance (
                character_id, source_kind, module_release_id, item_key,
                item_name, item_description, quantity)
            VALUES (?, 'TEMPORARY', NULL, NULL, ?, ?, ?)
            """;
    private static final String LOCK_ITEM_SQL = """
            SELECT source_kind, item_key, item_name, item_description, quantity, item_status
            FROM item_instance WHERE id = ? AND character_id = ? FOR UPDATE
            """;
    private static final String UPDATE_ITEM_QUANTITY_SQL =
            "UPDATE item_instance SET quantity = ? WHERE id = ? AND character_id = ?";
    private static final String UPDATE_ITEM_STATUS_SQL =
            "UPDATE item_instance SET item_status = ? WHERE id = ? AND character_id = ?";
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
                change_key, value_type, old_text, new_text,
                old_integer, new_integer, old_reference, new_reference)
            VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String ADVANCE_VERSION_SQL = """
            UPDATE character_record SET row_version = row_version + 1
            WHERE id = ? AND row_version = ?
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, character_id, result_status, completed_at)
            VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    private final DataSource dataSource;

    public JdbcCharacterCardMutationRepository(DataSource dataSource) {
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
                final Change change;
                try {
                    change = applyChange(connection, command, character);
                } catch (MissingItemException exception) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Status.TARGET_NOT_FOUND, character.rowVersion());
                }
                if (change == null) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Status.NO_CHANGE, character.rowVersion());
                }

                long nextSequence = Math.addExact(character.eventTail(), 1L);
                requireOne(update(connection, ADVANCE_EVENT_TAIL_SQL,
                        nextSequence, character.campaignId(), character.eventTail()));
                long eventId = insertEvent(
                        connection, command, character, nextSequence, change.eventText());
                insertChange(connection, character, eventId, change);
                requireOne(update(connection, ADVANCE_VERSION_SQL,
                        character.id(), command.expectedRowVersion()));
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
                if (!result.next()) return null;
                String digest = requireString(result, "request_digest_sha256");
                String operation = requireString(result, "operation_type");
                long characterId = result.getLong("character_id");
                boolean missingCharacter = result.wasNull();
                String status = requireString(result, "result_status");
                if (result.next()) throw invalidState();
                if (!digest.equals(command.requestDigestSha256())
                        || !operation.equals(command.action().operationType())) {
                    return new Result(Status.IDEMPOTENCY_CONFLICT, null);
                }
                if (missingCharacter || characterId <= 0 || !"SUCCEEDED".equals(status)) {
                    throw invalidState();
                }
                return new Result(Status.ALREADY_SUCCEEDED,
                        currentVersion(connection, characterId));
            }
        }
    }

    private static long currentVersion(Connection connection, long characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CURRENT_VERSION_SQL)) {
            statement.setLong(1, characterId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState();
                long version = nonNegativeLong(result, "row_version");
                if (result.next()) throw invalidState();
                return version;
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
                if (!result.next()) return null;
                root = new LockedCharacterRoot(
                        positiveLong(result, "id"), positiveLong(result, "campaign_id"),
                        positiveLong(result, "module_release_id"),
                        nonNegativeLong(result, "row_version"),
                        requireString(result, "saved_module_key"),
                        requireString(result, "saved_release_version"),
                        requireString(result, "saved_content_sha256"));
                if (result.next()) throw invalidState();
            }
        }
        Long eventTail = lockCampaign(connection, root.campaignId());
        if (eventTail == null) return null;
        // Frozen bindings and released module rows are immutable. Loading them
        // without FOR UPDATE preserves their intentionally SELECT-only grants.
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
                        root.rowVersion(), root.savedModuleKey(),
                        root.savedReleaseVersion(), root.savedContentSha256(),
                        eventTail, positiveLong(result, "frozen_release_id"),
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
        if (character == null) return new Result(Status.NOT_FOUND, null);
        if (character.rowVersion() != command.expectedRowVersion()) {
            return new Result(Status.VERSION_CONFLICT, character.rowVersion());
        }
        boolean matches = character.moduleReleaseId() == character.frozenReleaseId()
                && "RELEASED".equals(character.releaseStatus())
                && command.moduleKey().equals(character.savedModuleKey())
                && command.moduleKey().equals(character.frozenModuleKey())
                && command.moduleKey().equals(character.releaseModuleKey())
                && command.releaseVersion().equals(character.savedReleaseVersion())
                && command.releaseVersion().equals(character.frozenReleaseVersion())
                && command.releaseVersion().equals(character.releaseVersion())
                && command.contentSha256().equals(character.savedContentSha256())
                && command.contentSha256().equals(character.frozenContentSha256())
                && command.contentSha256().equals(character.releaseContentSha256());
        return matches ? null : new Result(Status.MODULE_BINDING_MISMATCH, null);
    }

    private static Change applyChange(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        return switch (command.action()) {
            case SET_FIELD -> setIntegerField(connection, command, character);
            case SET_CLASS_LEVEL -> setClassLevel(connection, command, character);
            case SET_SKILL_PROFICIENCY -> setProficiency(
                    connection, command, character, LOCK_SKILL_SQL, UPDATE_SKILL_SQL);
            case SET_SAVE_PROFICIENCY -> setProficiency(
                    connection, command, character, LOCK_SAVE_SQL, UPDATE_SAVE_SQL);
            case ADD_MODULE_ITEM -> addModuleItem(connection, command, character);
            case ADD_TEMPORARY_ITEM -> addTemporaryItem(connection, command, character);
            case SET_ITEM_QUANTITY, ARCHIVE_ITEM, RESTORE_ITEM ->
                    updateItem(connection, command, character);
        };
    }

    private static Change setIntegerField(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        Map<String, Long> values = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(LOCK_INTEGER_FIELDS_SQL)) {
            statement.setLong(1, character.id());
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (!"INTEGER".equals(requireString(result, "value_type"))) {
                        throw invalidState();
                    }
                    if (values.put(requireString(result, "field_key"),
                            requiredLong(result, "integer_value")) != null) {
                        throw invalidState();
                    }
                }
            }
        }
        if (values.size() != command.integerFieldRules().size()
                || !values.containsKey(command.targetKey())) {
            throw invalidState();
        }
        long oldValue = values.put(command.targetKey(), command.integerValue().longValue());
        for (IntegerFieldRule rule : command.integerFieldRules()) {
            Long value = values.get(rule.fieldKey());
            if (value == null || value < rule.minimum() || value > rule.maximum()) {
                throw new IllegalArgumentException("Character field is outside module bounds");
            }
            if (rule.dependentMaximumFieldKey() != null) {
                Long maximum = values.get(rule.dependentMaximumFieldKey());
                if (maximum == null || value > maximum) {
                    throw new IllegalArgumentException("Dependent character field is invalid");
                }
            }
        }
        long newValue = command.integerValue();
        if (oldValue == newValue) return null;
        requireOne(update(connection, UPDATE_INTEGER_FIELD_SQL,
                newValue, character.id(), command.targetKey()));
        return Change.integer(command.targetKey(), oldValue, newValue, "Updated character field");
    }

    private static Change setClassLevel(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        Map<String, Integer> levels = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CLASSES_SQL)) {
            statement.setLong(1, character.id());
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int level = result.getInt("class_level");
                    if (result.wasNull() || level < 1 || level > 20
                            || levels.put(requireString(result, "class_key"), level) != null) {
                        throw invalidState();
                    }
                }
            }
        }
        Integer oldValue = levels.get(command.targetKey());
        int newValue = command.integerValue();
        if (Objects.equals(oldValue, newValue == 0 ? null : newValue)) return null;
        int total = levels.values().stream().mapToInt(Integer::intValue).sum()
                - (oldValue == null ? 0 : oldValue) + newValue;
        if (total > 20) throw new IllegalArgumentException("Total class level exceeds 20");

        if (newValue == 0) {
            if (oldValue == null) return null;
            requireOne(update(connection, DELETE_CLASS_SQL, character.id(), command.targetKey()));
        } else if (oldValue == null) {
            requireOne(update(connection, INSERT_CLASS_SQL,
                    character.id(), character.moduleReleaseId(), command.targetKey(), newValue));
        } else {
            requireOne(update(connection, UPDATE_CLASS_SQL,
                    newValue, character.id(), command.targetKey()));
        }
        return Change.integer(
                command.targetKey(), oldValue, newValue == 0 ? null : newValue,
                "Updated character class level");
    }

    private static Change setProficiency(
            Connection connection,
            Command command,
            LockedCharacter character,
            String lockSql,
            String updateSql) throws SQLException {
        String oldValue;
        try (PreparedStatement statement = connection.prepareStatement(lockSql)) {
            statement.setLong(1, character.id());
            statement.setString(2, command.targetKey());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState();
                oldValue = requireString(result, "proficiency_key");
                if (result.next()) throw invalidState();
            }
        }
        if (oldValue.equals(command.textValue())) return null;
        requireOne(update(connection, updateSql,
                command.textValue(), character.id(), command.targetKey()));
        return Change.reference(
                command.targetKey(), oldValue, command.textValue(),
                "Updated character proficiency");
    }

    private static Change addModuleItem(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        requireOne(update(connection, INSERT_MODULE_ITEM_SQL,
                character.id(), character.moduleReleaseId(), command.targetKey(),
                command.textValue(), command.description(), command.integerValue()));
        return Change.reference(
                command.targetKey(), null, command.targetKey(), "Added module item");
    }

    private static Change addTemporaryItem(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        requireOne(update(connection, INSERT_TEMPORARY_ITEM_SQL,
                character.id(), command.textValue(), command.description(),
                command.integerValue()));
        return Change.text(
                "item.temporary", null, command.textValue(), "Added temporary item");
    }

    private static Change updateItem(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        long itemId = parsePositiveLong(command.targetKey());
        LockedItem item;
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ITEM_SQL)) {
            statement.setLong(1, itemId);
            statement.setLong(2, character.id());
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new MissingItemException();
                item = new LockedItem(
                        requireString(result, "source_kind"), result.getString("item_key"),
                        requireString(result, "item_name"),
                        requireString(result, "item_description"),
                        positiveInt(result, "quantity"), requireString(result, "item_status"));
                if (result.next()) throw invalidState();
            }
        }
        return switch (command.action()) {
            case SET_ITEM_QUANTITY -> {
                int quantity = command.integerValue();
                if (item.quantity() == quantity) yield null;
                requireOne(update(connection, UPDATE_ITEM_QUANTITY_SQL,
                        quantity, itemId, character.id()));
                yield Change.integer(
                        "item.quantity", item.quantity(), quantity, "Updated item quantity");
            }
            case ARCHIVE_ITEM, RESTORE_ITEM -> {
                String status = command.action() == Action.ARCHIVE_ITEM
                        ? "ARCHIVED" : "ACTIVE";
                if (status.equals(item.itemStatus())) yield null;
                requireOne(update(connection, UPDATE_ITEM_STATUS_SQL,
                        status, itemId, character.id()));
                yield Change.text(
                        "item.status", item.itemStatus(), status,
                        command.action() == Action.ARCHIVE_ITEM
                                ? "Archived item" : "Restored item");
            }
            default -> throw invalidState();
        };
    }

    private static long insertEvent(
            Connection connection,
            Command command,
            LockedCharacter character,
            long sequence,
            String eventText) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, character.campaignId());
            statement.setLong(2, sequence);
            statement.setString(3, command.action().eventType());
            statement.setLong(4, character.id());
            statement.setString(5, eventText);
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw invalidState();
                long id = keys.getLong(1);
                if (keys.wasNull() || id <= 0 || keys.next()) throw invalidState();
                return id;
            }
        }
    }

    private static void insertChange(
            Connection connection, LockedCharacter character, long eventId, Change change)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHANGE_SQL)) {
            statement.setLong(1, eventId);
            statement.setLong(2, character.campaignId());
            statement.setLong(3, character.id());
            statement.setString(4, change.changeKey());
            statement.setString(5, change.valueType());
            nullableString(statement, 6, change.oldText());
            nullableString(statement, 7, change.newText());
            nullableLong(statement, 8, change.oldInteger());
            nullableLong(statement, 9, change.newInteger());
            nullableString(statement, 10, change.oldReference());
            nullableString(statement, 11, change.newReference());
            statement.setQueryTimeout(5);
            requireOne(statement.executeUpdate());
        }
    }

    private static void insertOperation(
            Connection connection, Command command, LockedCharacter character)
            throws SQLException {
        requireOne(update(connection, INSERT_OPERATION_SQL,
                command.requestId(), command.requestDigestSha256(),
                command.action().operationType(), character.campaignId(), character.id()));
    }

    private static int update(Connection connection, String sql, Object... values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                Object value = values[index];
                if (value instanceof String string) statement.setString(index + 1, string);
                else if (value instanceof Integer integer) statement.setInt(index + 1, integer);
                else if (value instanceof Long number) statement.setLong(index + 1, number);
                else throw new IllegalArgumentException("Unsupported SQL parameter");
            }
            statement.setQueryTimeout(5);
            return statement.executeUpdate();
        }
    }

    private static void nullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static void nullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static void validate(Command command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.action(), "action");
        Objects.requireNonNull(command.targetKey(), "targetKey");
        boolean invalidShape = switch (command.action()) {
            case SET_FIELD, SET_CLASS_LEVEL, SET_ITEM_QUANTITY ->
                    command.integerValue() == null;
            case SET_SKILL_PROFICIENCY, SET_SAVE_PROFICIENCY ->
                    command.textValue() == null;
            case ADD_MODULE_ITEM, ADD_TEMPORARY_ITEM -> command.textValue() == null
                    || command.description() == null || command.integerValue() == null;
            case ARCHIVE_ITEM, RESTORE_ITEM -> false;
        };
        if (invalidShape
                || !isCanonicalUuid(command.requestId())
                || !isCanonicalUuid(command.characterKey())
                || command.expectedRowVersion() < 0
                || command.expectedRowVersion() == Long.MAX_VALUE
                || !isSha256(command.requestDigestSha256())
                || !isSha256(command.contentSha256())) {
            throw new IllegalArgumentException("Invalid character card command");
        }
    }

    private static long parsePositiveLong(String value) throws SQLException {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw invalidState();
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalidState();
        }
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static int positiveInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        if (result.wasNull() || value <= 0) throw invalidState();
        return value;
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

    private static String requireString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null) throw invalidState();
        return value;
    }

    private static void requireOne(int count) throws SQLException {
        if (count != 1) throw invalidState();
    }

    private static SQLException invalidState() {
        return new SQLException("Invalid character card mutation persistence state");
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

    private record LockedCharacter(
            long id,
            long campaignId,
            long moduleReleaseId,
            long rowVersion,
            String savedModuleKey,
            String savedReleaseVersion,
            String savedContentSha256,
            long eventTail,
            long frozenReleaseId,
            String frozenModuleKey,
            String frozenReleaseVersion,
            String frozenContentSha256,
            String releaseModuleKey,
            String releaseVersion,
            String releaseContentSha256,
            String releaseStatus) {
    }

    private record LockedCharacterRoot(
            long id,
            long campaignId,
            long moduleReleaseId,
            long rowVersion,
            String savedModuleKey,
            String savedReleaseVersion,
            String savedContentSha256) {
    }

    private record LockedItem(
            String sourceKind,
            String itemKey,
            String itemName,
            String itemDescription,
            int quantity,
            String itemStatus) {
    }

    private record Change(
            String changeKey,
            String valueType,
            String oldText,
            String newText,
            Long oldInteger,
            Long newInteger,
            String oldReference,
            String newReference,
            String eventText) {
        static Change integer(String key, Number oldValue, Number newValue, String eventText) {
            return new Change(
                    key, "INTEGER", null, null,
                    oldValue == null ? null : oldValue.longValue(),
                    newValue == null ? null : newValue.longValue(),
                    null, null, eventText);
        }

        static Change text(String key, String oldValue, String newValue, String eventText) {
            return new Change(
                    key, "TEXT", oldValue, newValue,
                    null, null, null, null, eventText);
        }

        static Change reference(String key, String oldValue, String newValue, String eventText) {
            return new Change(
                    key, "REFERENCE", null, null,
                    null, null, oldValue, newValue, eventText);
        }
    }

    private static final class MissingItemException extends SQLException {
        private static final long serialVersionUID = 1L;

        MissingItemException() {
            super("Item does not belong to character");
        }
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
