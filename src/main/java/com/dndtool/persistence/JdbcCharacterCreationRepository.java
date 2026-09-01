package com.dndtool.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;

/** JDBC transaction for one idempotent character creation and its first audit event. */
public final class JdbcCharacterCreationRepository implements CharacterCreationRepository {
    private static final String OPERATION_TYPE = "CREATE_CHARACTER";
    private static final String LOCK_OPERATION_SQL = """
            SELECT request_digest_sha256, operation_type, character_id, result_status
            FROM host_operation
            WHERE request_id = ?
            FOR UPDATE
            """;
    private static final String LOCK_CAMPAIGN_SQL = """
            SELECT c.id, c.internal_event_tail
            FROM campaign AS c
            WHERE c.campaign_key = ? AND c.campaign_status = 'ACTIVE'
            FOR UPDATE
            """;
    private static final String LOAD_CAMPAIGN_MODULE_SQL = """
            SELECT module_release_id, frozen_module_key,
                   frozen_release_version, frozen_content_sha256
            FROM campaign_module
            WHERE campaign_id = ?
            """;
    private static final String INSERT_CHARACTER_SQL = """
            INSERT INTO character_record (
                campaign_id, module_release_id, character_key, character_type,
                character_name, saved_module_key, saved_release_version,
                saved_content_sha256)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_FIELD_SQL = """
            INSERT INTO character_field_value (
                character_id, module_release_id, field_key, value_type,
                text_value, integer_value, decimal_value, boolean_value)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_CLASS_SQL = """
            INSERT INTO character_class_level (
                character_id, module_release_id, class_key, class_level)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_SKILL_SQL = """
            INSERT INTO character_skill_proficiency (
                character_id, module_release_id, skill_key, proficiency_key)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_SAVE_SQL = """
            INSERT INTO character_save_proficiency (
                character_id, module_release_id, save_key, proficiency_key)
            VALUES (?, ?, ?, ?)
            """;
    private static final String ADVANCE_EVENT_TAIL_SQL = """
            UPDATE campaign SET internal_event_tail = ?
            WHERE id = ? AND internal_event_tail = ?
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id, event_text)
            VALUES (?, ?, 'CHARACTER_CREATED', ?, ?)
            """;
    private static final String INSERT_CHANGE_SQL = """
            INSERT INTO field_change (
                game_event_id, campaign_id, character_id, change_order,
                change_key, value_type, old_text, new_text,
                old_integer, new_integer, old_decimal, new_decimal,
                old_boolean, new_boolean, old_reference, new_reference)
            VALUES (?, ?, ?, ?, ?, ?, NULL, ?, NULL, ?, NULL, ?, NULL, ?, NULL, ?)
            """;
    private static final String INSERT_OPERATION_SQL = """
            INSERT INTO host_operation (
                request_id, request_digest_sha256, operation_type,
                campaign_id, character_id, result_status, completed_at)
            VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', CURRENT_TIMESTAMP(6))
            """;

    private final DataSource dataSource;

    public JdbcCharacterCreationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Result create(Command command) throws SQLException {
        Objects.requireNonNull(command);
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
                CampaignBinding binding = lockCampaign(connection, command.campaignKey());
                if (binding == null) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Result.Status.CAMPAIGN_UNAVAILABLE, null, null);
                }
                if (!binding.matches(command)) {
                    connection.rollback();
                    restore(connection, original);
                    return new Result(Result.Status.MODULE_BINDING_MISMATCH, null, null);
                }

                long characterId = insertCharacter(connection, command, binding);
                insertFields(connection, command, binding, characterId);
                insertClasses(connection, command, binding, characterId);
                insertProficiencies(
                        connection, INSERT_SKILL_SQL,
                        command.skillProficiencies(), binding, characterId);
                insertProficiencies(
                        connection, INSERT_SAVE_SQL,
                        command.saveProficiencies(), binding, characterId);

                long eventSequence = Math.addExact(binding.eventTail(), 1L);
                advanceEventTail(connection, binding, eventSequence);
                long eventId = insertEvent(
                        connection, command, binding, characterId, eventSequence);
                insertAuditChanges(connection, command, binding, characterId, eventId);
                insertOperation(connection, command, binding.campaignId(), characterId);

                connection.commit();
                restore(connection, original);
                return new Result(Result.Status.CREATED, command.characterKey(), 0L);
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
                String operationType = requireString(result, "operation_type");
                long characterId = result.getLong("character_id");
                boolean characterMissing = result.wasNull();
                String status = requireString(result, "result_status");
                if (result.next()) {
                    throw invalidState();
                }
                if (!digest.equals(command.requestDigestSha256())
                        || !OPERATION_TYPE.equals(operationType)) {
                    return new Result(Result.Status.IDEMPOTENCY_CONFLICT, null, null);
                }
                if (!"SUCCEEDED".equals(status) || characterMissing || characterId <= 0) {
                    throw invalidState();
                }
                return findCharacter(connection, characterId);
            }
        }
    }

    private static Result findCharacter(Connection connection, long characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT character_key, row_version FROM character_record WHERE id = ?")) {
            statement.setLong(1, characterId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw invalidState();
                }
                String key = requireString(result, "character_key");
                long version = result.getLong("row_version");
                if (result.wasNull() || version < 0 || result.next()) {
                    throw invalidState();
                }
                return new Result(Result.Status.ALREADY_SUCCEEDED, key, version);
            }
        }
    }

    private static CampaignBinding lockCampaign(Connection connection, String campaignKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CAMPAIGN_SQL)) {
            statement.setString(1, campaignKey);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                long campaignId = positiveLong(result, "id");
                long eventTail = nonNegativeLong(result, "internal_event_tail");
                if (result.next()) {
                    throw invalidState();
                }
                // Only the campaign row is mutable in this transaction. Keeping the
                // immutable binding out of FOR UPDATE preserves its SELECT-only grant.
                return loadCampaignModule(connection, campaignId, eventTail);
            }
        }
    }

    private static CampaignBinding loadCampaignModule(
            Connection connection, long campaignId, long eventTail) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOAD_CAMPAIGN_MODULE_SQL)) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                CampaignBinding binding = new CampaignBinding(
                        campaignId,
                        eventTail,
                        positiveLong(result, "module_release_id"),
                        requireString(result, "frozen_module_key"),
                        requireString(result, "frozen_release_version"),
                        requireString(result, "frozen_content_sha256"));
                if (result.next()) {
                    throw invalidState();
                }
                return binding;
            }
        }
    }

    private static long insertCharacter(
            Connection connection, Command command, CampaignBinding binding)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_CHARACTER_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, binding.campaignId());
            statement.setLong(2, binding.releaseId());
            statement.setString(3, command.characterKey());
            statement.setString(4, command.characterType());
            statement.setString(5, command.characterName());
            statement.setString(6, binding.moduleKey());
            statement.setString(7, binding.releaseVersion());
            statement.setString(8, binding.contentSha256());
            statement.setQueryTimeout(5);
            return executeInsertWithKey(statement);
        }
    }

    private static void insertFields(
            Connection connection,
            Command command,
            CampaignBinding binding,
            long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_FIELD_SQL)) {
            for (FieldValue field : command.fieldValues()) {
                statement.setLong(1, characterId);
                statement.setLong(2, binding.releaseId());
                statement.setString(3, field.fieldKey());
                statement.setString(4, field.valueType());
                bindScalarColumns(statement, 5, field.valueType(), field.value());
                statement.setQueryTimeout(5);
                if (statement.executeUpdate() != 1) {
                    throw invalidState();
                }
            }
        }
    }

    private static void insertClasses(
            Connection connection,
            Command command,
            CampaignBinding binding,
            long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CLASS_SQL)) {
            for (ClassLevel value : command.classLevels()) {
                statement.setLong(1, characterId);
                statement.setLong(2, binding.releaseId());
                statement.setString(3, value.classKey());
                statement.setInt(4, value.level());
                statement.setQueryTimeout(5);
                if (statement.executeUpdate() != 1) {
                    throw invalidState();
                }
            }
        }
    }

    private static void insertProficiencies(
            Connection connection,
            String sql,
            Iterable<Proficiency> values,
            CampaignBinding binding,
            long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Proficiency value : values) {
                statement.setLong(1, characterId);
                statement.setLong(2, binding.releaseId());
                statement.setString(3, value.targetKey());
                statement.setString(4, value.proficiencyKey());
                statement.setQueryTimeout(5);
                if (statement.executeUpdate() != 1) {
                    throw invalidState();
                }
            }
        }
    }

    private static void advanceEventTail(
            Connection connection, CampaignBinding binding, long nextSequence)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADVANCE_EVENT_TAIL_SQL)) {
            statement.setLong(1, nextSequence);
            statement.setLong(2, binding.campaignId());
            statement.setLong(3, binding.eventTail());
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw invalidState();
            }
        }
    }

    private static long insertEvent(
            Connection connection,
            Command command,
            CampaignBinding binding,
            long characterId,
            long sequence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EVENT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, binding.campaignId());
            statement.setLong(2, sequence);
            statement.setLong(3, characterId);
            statement.setString(4, command.templateKey() == null
                    ? "Created blank " + command.characterType() + " character"
                    : "Created NPC from reviewed template");
            statement.setQueryTimeout(5);
            return executeInsertWithKey(statement);
        }
    }

    private static void insertAuditChanges(
            Connection connection,
            Command command,
            CampaignBinding binding,
            long characterId,
            long eventId) throws SQLException {
        int order = 1;
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CHANGE_SQL)) {
            insertChange(statement, eventId, binding.campaignId(), characterId, order++,
                    "character.type", "TEXT", command.characterType());
            insertChange(statement, eventId, binding.campaignId(), characterId, order++,
                    "character.name", "TEXT", command.characterName());
            if (command.templateKey() != null) {
                insertChange(statement, eventId, binding.campaignId(), characterId, order++,
                        "character.template", "REFERENCE", command.templateKey());
            }
            for (FieldValue field : command.fieldValues()) {
                insertScalarChange(statement, eventId, binding.campaignId(), characterId,
                        order++, field.fieldKey(), field.valueType(), field.value());
            }
            for (ClassLevel value : command.classLevels()) {
                insertChange(statement, eventId, binding.campaignId(), characterId, order++,
                        "class.level." + value.classKey(), "INTEGER", (long) value.level());
            }
            for (Proficiency value : command.skillProficiencies()) {
                insertChange(statement, eventId, binding.campaignId(), characterId, order++,
                        "skill.proficiency." + value.targetKey(),
                        "REFERENCE", value.proficiencyKey());
            }
            for (Proficiency value : command.saveProficiencies()) {
                insertChange(statement, eventId, binding.campaignId(), characterId, order++,
                        "save.proficiency." + value.targetKey(),
                        "REFERENCE", value.proficiencyKey());
            }
        }
    }

    private static void insertScalarChange(
            PreparedStatement statement,
            long eventId,
            long campaignId,
            long characterId,
            int order,
            String key,
            String valueType,
            ModuleCatalog.ScalarValue value) throws SQLException {
        Object scalar = switch (value) {
            case ModuleCatalog.TextValue text -> text.value();
            case ModuleCatalog.IntegerValue integer -> integer.value();
            case ModuleCatalog.DecimalValue decimal -> decimal.value();
            case ModuleCatalog.BooleanValue bool -> bool.value();
            case ModuleCatalog.IdentifierValue identifier -> identifier.value();
        };
        insertChange(statement, eventId, campaignId, characterId, order, key, valueType, scalar);
    }

    private static void insertChange(
            PreparedStatement statement,
            long eventId,
            long campaignId,
            long characterId,
            int order,
            String key,
            String valueType,
            Object value) throws SQLException {
        statement.setLong(1, eventId);
        statement.setLong(2, campaignId);
        statement.setLong(3, characterId);
        statement.setInt(4, order);
        statement.setString(5, key);
        statement.setString(6, valueType);
        statement.setNull(7, java.sql.Types.VARCHAR);
        statement.setNull(8, java.sql.Types.BIGINT);
        statement.setNull(9, java.sql.Types.DECIMAL);
        statement.setNull(10, java.sql.Types.TINYINT);
        statement.setNull(11, java.sql.Types.VARCHAR);
        switch (valueType) {
            case "TEXT" -> statement.setString(7, (String) value);
            case "INTEGER" -> statement.setLong(8, (Long) value);
            case "DECIMAL" -> statement.setBigDecimal(9, (BigDecimal) value);
            case "BOOLEAN" -> statement.setBoolean(10, (Boolean) value);
            case "REFERENCE" -> statement.setString(11, (String) value);
            default -> throw invalidState();
        }
        statement.setQueryTimeout(5);
        if (statement.executeUpdate() != 1) {
            throw invalidState();
        }
    }

    private static void insertOperation(
            Connection connection,
            Command command,
            long campaignId,
            long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPERATION_SQL)) {
            statement.setString(1, command.requestId());
            statement.setString(2, command.requestDigestSha256());
            statement.setString(3, OPERATION_TYPE);
            statement.setLong(4, campaignId);
            statement.setLong(5, characterId);
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw invalidState();
            }
        }
    }

    private static void bindScalarColumns(
            PreparedStatement statement,
            int start,
            String valueType,
            ModuleCatalog.ScalarValue value) throws SQLException {
        statement.setNull(start, java.sql.Types.VARCHAR);
        statement.setNull(start + 1, java.sql.Types.BIGINT);
        statement.setNull(start + 2, java.sql.Types.DECIMAL);
        statement.setNull(start + 3, java.sql.Types.TINYINT);
        switch (valueType) {
            case "TEXT" -> statement.setString(
                    start, ((ModuleCatalog.TextValue) value).value());
            case "INTEGER" -> statement.setLong(
                    start + 1, ((ModuleCatalog.IntegerValue) value).value());
            case "DECIMAL" -> statement.setBigDecimal(
                    start + 2, ((ModuleCatalog.DecimalValue) value).value());
            case "BOOLEAN" -> statement.setBoolean(
                    start + 3, ((ModuleCatalog.BooleanValue) value).value());
            default -> throw invalidState();
        }
    }

    private static long executeInsertWithKey(PreparedStatement statement) throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw invalidState();
        }
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

    private static String requireString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || result.wasNull()) {
            throw invalidState();
        }
        return value;
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

    private static SQLException invalidState() {
        return new SQLException("Invalid character creation persistence state");
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
        connection.setTransactionIsolation(original.transactionIsolation());
    }

    private record CampaignBinding(
            long campaignId,
            long eventTail,
            long releaseId,
            String moduleKey,
            String releaseVersion,
            String contentSha256) {
        private boolean matches(Command command) {
            return moduleKey.equals(command.moduleKey())
                    && releaseVersion.equals(command.releaseVersion())
                    && contentSha256.equals(command.contentSha256());
        }
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int transactionIsolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
