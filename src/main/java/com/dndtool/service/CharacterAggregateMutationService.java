package com.dndtool.service;

import com.dndtool.persistence.CharacterAggregateMutationRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Validates the public optimistic-lock token before entering the aggregate transaction. */
public final class CharacterAggregateMutationService {
    private final CharacterAggregateMutationRepository repository;

    public CharacterAggregateMutationService(CharacterAggregateMutationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Result mutate(
            String characterKey, long expectedRowVersion, AggregateChange change)
            throws SQLException {
        if (!isCanonicalUuid(characterKey) || expectedRowVersion < 0 || change == null) {
            return new Result(Status.INVALID_REQUEST, null);
        }
        CharacterAggregateMutationRepository.Result persisted = repository.mutate(
                new CharacterAggregateMutationRepository.Command(
                        characterKey, expectedRowVersion),
                (connection, character) -> change.apply(connection, new CharacterIdentity(
                        character.id(), character.campaignId(), character.moduleReleaseId())));
        return switch (persisted.status()) {
            case UPDATED -> new Result(Status.UPDATED, persisted.rowVersion());
            case NOT_FOUND -> new Result(Status.NOT_FOUND, null);
            case VERSION_CONFLICT ->
                    new Result(Status.VERSION_CONFLICT, persisted.rowVersion());
        };
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

    public record Result(Status status, Long rowVersion) {
    }

    public enum Status {
        UPDATED,
        NOT_FOUND,
        VERSION_CONFLICT,
        INVALID_REQUEST
    }

    /** Future field, class, proficiency, item and audit writes implement this operation. */
    @FunctionalInterface
    public interface AggregateChange {
        void apply(Connection connection, CharacterIdentity character) throws SQLException;
    }

    /** Database identities made available only inside the locked mutation transaction. */
    public record CharacterIdentity(long id, long campaignId, long moduleReleaseId) {
    }
}
