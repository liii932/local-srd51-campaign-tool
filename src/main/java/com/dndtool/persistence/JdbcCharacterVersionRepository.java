package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** JDBC implementation of the campaign-first, stable-id host command locking protocol. */
public final class JdbcCharacterVersionRepository
        implements CharacterVersionRepository {
    private static final String LOCK_CAMPAIGN_SQL = """
            SELECT internal_event_tail
            FROM campaign
            WHERE id = ? AND campaign_status = 'ACTIVE'
            FOR UPDATE
            """;
    private static final String LOAD_BINDING_SQL = """
            SELECT module_release_id, frozen_module_key,
                   frozen_release_version, frozen_content_sha256
            FROM campaign_module
            WHERE campaign_id = ?
            """;
    private static final String LOCK_CHARACTER_SQL = """
            SELECT character_key, campaign_id, module_release_id,
                   character_status, row_version, saved_module_key,
                   saved_release_version, saved_content_sha256
            FROM character_record
            WHERE id = ?
            FOR UPDATE
            """;
    private static final String ADVANCE_VERSION_SQL = """
            UPDATE character_record
            SET row_version = row_version + 1
            WHERE id = ? AND row_version = ?
            """;

    @Override
    public LockResult lockBeforeRoll(Connection connection, LockCommand command)
            throws SQLException {
        validateCommand(command);
        requireCallerTransaction(connection);
        Long eventTail = lockCampaign(connection, command.campaignId());
        if (eventTail == null) {
            return LockResult.rejected(Status.CAMPAIGN_NOT_FOUND, null, null);
        }
        Binding binding = loadBinding(connection, command.campaignId());
        if (binding == null || binding.moduleReleaseId() != command.moduleReleaseId()) {
            return LockResult.rejected(Status.MODULE_HASH_MISMATCH, null, null);
        }

        LinkedHashMap<String, VersionExpectation> expectations = expectations(command);
        Map<String, Long> resolvedIds = resolveIds(connection, expectations.keySet());
        if (resolvedIds.size() != expectations.size()) {
            String missing = expectations.keySet().stream()
                    .filter(key -> !resolvedIds.containsKey(key)).findFirst().orElse(null);
            return LockResult.rejected(Status.CHARACTER_NOT_FOUND, missing, null);
        }

        List<Map.Entry<String, Long>> identities = new ArrayList<>(resolvedIds.entrySet());
        identities.sort(Map.Entry.comparingByValue());
        List<LockedRow> lockedRows = new ArrayList<>(identities.size());
        for (Map.Entry<String, Long> identity : identities) {
            LockedRow row = lockCharacter(connection, identity.getValue());
            if (row == null || !identity.getKey().equals(row.character().characterKey())) {
                throw invalidState("Resolved host command character identity changed");
            }
            lockedRows.add(row);
        }

        // Every row is locked before any version decision, preserving one stable lock set/order.
        List<LockedCharacter> locked = new ArrayList<>(lockedRows.size());
        LockedCharacter executor = null;
        for (LockedRow row : lockedRows) {
            LockedCharacter character = row.character();
            VersionExpectation expected = expectations.get(character.characterKey());
            if (character.campaignId() != command.campaignId()
                    || character.moduleReleaseId() != command.moduleReleaseId()
                    || !row.active()) {
                return LockResult.rejected(
                        Status.CHARACTER_INVALID, character.characterKey(), character.rowVersion());
            }
            if (!binding.matches(row.savedModule())) {
                return LockResult.rejected(Status.MODULE_HASH_MISMATCH, null, null);
            }
            if (character.rowVersion() != expected.expectedRowVersion()) {
                return LockResult.rejected(
                        Status.VERSION_CONFLICT, character.characterKey(), character.rowVersion());
            }
            locked.add(character);
            if (character.characterKey().equals(command.executor().characterKey())) {
                executor = character;
            }
        }
        if (executor == null) throw invalidState("Locked host command executor is missing");
        return LockResult.locked(new LockedScope(
                command.campaignId(), command.moduleReleaseId(), eventTail,
                executor, locked));
    }

    @Override
    public Map<Long, Long> advanceModifiedVersions(
            Connection connection, LockedScope scope, Set<Long> modifiedCharacterIds)
            throws SQLException {
        validateScope(scope, modifiedCharacterIds);
        requireCallerTransaction(connection);
        Map<Long, LockedCharacter> locked = new HashMap<>();
        for (LockedCharacter character : scope.charactersById()) {
            if (locked.putIfAbsent(character.id(), character) != null) {
                throw new IllegalArgumentException("Duplicate locked host command character");
            }
        }
        List<Long> orderedIds = new ArrayList<>(modifiedCharacterIds);
        orderedIds.sort(Long::compareTo);
        LinkedHashMap<Long, Long> advanced = new LinkedHashMap<>();
        for (Long id : orderedIds) {
            LockedCharacter character = locked.get(id);
            if (character == null) {
                throw new IllegalArgumentException("Modified character was not locked");
            }
            long nextVersion = Math.addExact(character.rowVersion(), 1L);
            try (PreparedStatement statement = connection.prepareStatement(ADVANCE_VERSION_SQL)) {
                statement.setLong(1, character.id());
                statement.setLong(2, character.rowVersion());
                statement.setQueryTimeout(5);
                if (statement.executeUpdate() != 1) {
                    throw invalidState("Host command character version advance failed");
                }
            }
            advanced.put(id, nextVersion);
        }
        return Map.copyOf(advanced);
    }

    private static Long lockCampaign(Connection connection, long campaignId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CAMPAIGN_SQL)) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                long tail = nonNegativeLong(result, "internal_event_tail");
                if (result.next()) throw invalidState("Host command campaign lock was not unique");
                return tail;
            }
        }
    }

    private static Binding loadBinding(Connection connection, long campaignId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_BINDING_SQL)) {
            statement.setLong(1, campaignId);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                Binding binding = new Binding(
                        positiveLong(result, "module_release_id"),
                        requiredString(result, "frozen_module_key"),
                        requiredString(result, "frozen_release_version"),
                        requiredString(result, "frozen_content_sha256"));
                if (result.next()) throw invalidState("Host command campaign binding was not unique");
                return binding;
            }
        }
    }

    private static Map<String, Long> resolveIds(
            Connection connection, Set<String> characterKeys) throws SQLException {
        String placeholders = String.join(", ", java.util.Collections.nCopies(
                characterKeys.size(), "?"));
        String sql = "SELECT id, character_key FROM character_record "
                + "WHERE character_key IN (" + placeholders + ") ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String key : characterKeys) statement.setString(index++, key);
            statement.setMaxRows(characterKeys.size() + 1);
            statement.setQueryTimeout(5);
            LinkedHashMap<String, Long> resolved = new LinkedHashMap<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long id = positiveLong(result, "id");
                    String key = requiredString(result, "character_key");
                    if (!characterKeys.contains(key) || resolved.putIfAbsent(key, id) != null) {
                        throw invalidState("Host command character resolution was invalid");
                    }
                }
            }
            return resolved;
        }
    }

    private static LockedRow lockCharacter(Connection connection, long id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_CHARACTER_SQL)) {
            statement.setLong(1, id);
            statement.setMaxRows(2);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                String key = requiredString(result, "character_key");
                long campaignId = positiveLong(result, "campaign_id");
                long releaseId = positiveLong(result, "module_release_id");
                boolean active = "ACTIVE".equals(requiredString(result, "character_status"));
                long version = nonNegativeLong(result, "row_version");
                SavedModule savedModule = new SavedModule(
                        requiredString(result, "saved_module_key"),
                        requiredString(result, "saved_release_version"),
                        requiredString(result, "saved_content_sha256"));
                if (result.next()) throw invalidState("Host command character lock was not unique");
                return new LockedRow(
                        new LockedCharacter(id, key, campaignId, releaseId, version),
                        active, savedModule);
            }
        }
    }

    private static LinkedHashMap<String, VersionExpectation> expectations(LockCommand command) {
        LinkedHashMap<String, VersionExpectation> expectations = new LinkedHashMap<>();
        expectations.put(command.executor().characterKey(), command.executor());
        for (VersionExpectation target : command.possibleTargets()) {
            VersionExpectation existing = expectations.putIfAbsent(target.characterKey(), target);
            if (existing != null
                    && existing.expectedRowVersion() != target.expectedRowVersion()) {
                throw new IllegalArgumentException("Conflicting host command character versions");
            }
        }
        return expectations;
    }

    private static void validateCommand(LockCommand command) {
        if (command == null || command.campaignId() <= 0 || command.moduleReleaseId() <= 0
                || command.possibleTargets() == null) {
            throw new IllegalArgumentException("Invalid host command lock command");
        }
        validateExpectation(command.executor());
        for (VersionExpectation expectation : command.possibleTargets()) {
            validateExpectation(expectation);
        }
        expectations(command);
    }

    private static void validateExpectation(VersionExpectation expectation) {
        if (expectation == null || !isCanonicalUuid(expectation.characterKey())
                || expectation.expectedRowVersion() < 0
                || expectation.expectedRowVersion() == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid host command character expectation");
        }
    }

    private static void validateScope(LockedScope scope, Set<Long> modifiedIds) {
        if (scope == null || scope.campaignId() <= 0 || scope.moduleReleaseId() <= 0
                || scope.expectedEventTail() < 0 || modifiedIds == null) {
            throw new IllegalArgumentException("Invalid locked host command scope");
        }
        for (Long id : modifiedIds) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Invalid modified host command character id");
            }
        }
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw invalidState(
                    "Host command locking requires a writable caller-owned serializable transaction");
        }
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Required host command value is missing");
        return value;
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value <= 0) throw invalidState("Invalid positive host command value");
        return value;
    }

    private static long nonNegativeLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value < 0 || value == Long.MAX_VALUE) {
            throw invalidState("Invalid host command version value");
        }
        return value;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private record LockedRow(
            LockedCharacter character, boolean active, SavedModule savedModule) {
    }

    private record SavedModule(String moduleKey, String releaseVersion, String contentSha256) {
    }

    private record Binding(
            long moduleReleaseId,
            String moduleKey,
            String releaseVersion,
            String contentSha256) {
        private boolean matches(SavedModule saved) {
            return moduleKey.equals(saved.moduleKey())
                    && releaseVersion.equals(saved.releaseVersion())
                    && contentSha256.equals(saved.contentSha256());
        }
    }
}
