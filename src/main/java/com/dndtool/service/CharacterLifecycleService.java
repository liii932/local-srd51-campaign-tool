package com.dndtool.service;

import com.dndtool.service.CharacterCreationIdentityFactory.CharacterType;
import com.dndtool.service.CharacterCreationIdentityFactory.NewCharacterRequest;
import com.dndtool.service.CharacterCreationIdentityFactory.PreparedCharacter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Applies identity-preserving type and archive state changes and prepares safe copies. */
public final class CharacterLifecycleService {
    private static final int COPY_KEY_ATTEMPTS = 4;
    private static final String UPDATE_TYPE_SQL = """
            UPDATE character_record
            SET character_type = ?
            WHERE id = ?
            """;
    private static final String UPDATE_STATUS_SQL = """
            UPDATE character_record
            SET character_status = ?
            WHERE id = ?
            """;

    private final CharacterAggregateMutationService mutationService;
    private final CharacterCreationIdentityFactory identityFactory;

    public CharacterLifecycleService(
            CharacterAggregateMutationService mutationService) {
        this(mutationService, new CharacterCreationIdentityFactory());
    }

    /** Package-private identity factory injection keeps copy-key tests deterministic. */
    CharacterLifecycleService(
            CharacterAggregateMutationService mutationService,
            CharacterCreationIdentityFactory identityFactory) {
        this.mutationService = Objects.requireNonNull(mutationService);
        this.identityFactory = Objects.requireNonNull(identityFactory);
    }

    /** Changes only PC/NPC type; the locked stable key is never part of the UPDATE list. */
    public CharacterAggregateMutationService.Result changeType(
            String characterKey, long expectedRowVersion, CharacterType newType)
            throws SQLException {
        if (newType == null) {
            return invalid();
        }
        return mutationService.mutate(
                characterKey,
                expectedRowVersion,
                (connection, character) -> updateRootValue(
                        connection, UPDATE_TYPE_SQL, newType.name(), character.id()));
    }

    /** Marks a character archived without deleting it or changing its stable identity. */
    public CharacterAggregateMutationService.Result archive(
            String characterKey, long expectedRowVersion) throws SQLException {
        return changeStatus(characterKey, expectedRowVersion, CharacterStatus.ARCHIVED);
    }

    /** Restores an archived character without replacing or reusing its stable identity. */
    public CharacterAggregateMutationService.Result restore(
            String characterKey, long expectedRowVersion) throws SQLException {
        return changeStatus(characterKey, expectedRowVersion, CharacterStatus.ACTIVE);
    }

    /**
     * Prepares the independent identity and normalized display values for a future copy
     * transaction. The source key is input-only and can never become the copy key.
     */
    public CopyPlan prepareCopy(CopySource source) {
        Objects.requireNonNull(source, "source");
        if (!isCanonicalUuid(source.sourceCharacterKey())) {
            throw new IllegalArgumentException("Invalid source character key");
        }
        NewCharacterRequest request = new NewCharacterRequest(
                source.campaignKey(), source.characterType(), source.characterName());
        for (int attempt = 0; attempt < COPY_KEY_ATTEMPTS; attempt++) {
            PreparedCharacter copy = identityFactory.prepare(request);
            if (!source.sourceCharacterKey().equals(copy.characterKey())) {
                return new CopyPlan(source.sourceCharacterKey(), copy);
            }
        }
        throw new IllegalStateException("Unable to allocate a distinct character copy key");
    }

    private CharacterAggregateMutationService.Result changeStatus(
            String characterKey, long expectedRowVersion, CharacterStatus status)
            throws SQLException {
        return mutationService.mutate(
                characterKey,
                expectedRowVersion,
                (connection, character) -> updateRootValue(
                        connection, UPDATE_STATUS_SQL, status.name(), character.id()));
    }

    private static void updateRootValue(
            Connection connection, String sql, String value, long characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setLong(2, characterId);
            statement.setQueryTimeout(5);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Invalid character lifecycle persistence state");
            }
        }
    }

    private static CharacterAggregateMutationService.Result invalid() {
        return new CharacterAggregateMutationService.Result(
                CharacterAggregateMutationService.Status.INVALID_REQUEST, null);
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

    /** Current lifecycle states stored in character_record. */
    public enum CharacterStatus {
        ACTIVE,
        ARCHIVED
    }

    /** Current source values needed to prepare, but not yet persist, an independent copy. */
    public record CopySource(
            String sourceCharacterKey,
            String campaignKey,
            CharacterType characterType,
            String characterName) {
        public CopySource {
            Objects.requireNonNull(sourceCharacterKey, "sourceCharacterKey");
            Objects.requireNonNull(campaignKey, "campaignKey");
            Objects.requireNonNull(characterType, "characterType");
            Objects.requireNonNull(characterName, "characterName");
        }
    }

    /** Source identity plus the separately generated values for the future copy transaction. */
    public record CopyPlan(String sourceCharacterKey, PreparedCharacter copy) {
        public CopyPlan {
            Objects.requireNonNull(sourceCharacterKey, "sourceCharacterKey");
            Objects.requireNonNull(copy, "copy");
        }
    }
}
