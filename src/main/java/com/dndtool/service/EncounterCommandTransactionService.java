package com.dndtool.service;

import com.dndtool.persistence.EncounterCommandRepository;
import com.dndtool.persistence.EncounterStateRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Atomically persists initial map state, one root event and its idempotency result. */
public final class EncounterCommandTransactionService {
    private final DataSource dataSource;
    private final EncounterStateService commandService;
    private final EncounterStateRepository stateRepository;
    private final EncounterCommandRepository commandRepository;

    public EncounterCommandTransactionService(
            DataSource dataSource,
            EncounterStateService commandService,
            EncounterStateRepository stateRepository,
            EncounterCommandRepository commandRepository) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.commandRepository = Objects.requireNonNull(commandRepository, "commandRepository");
    }

    public Result initialize(Request request) throws SQLException {
        validate(request);
        String actualDigest = EncounterRequestDigest.sha256(
                request.campaignKey(), request.partyNodeKey(), request.participants());
        if (!secureEquals(actualDigest, request.requestDigestSha256())) {
            return Result.failure(Status.INVALID_REQUEST);
        }
        EncounterStateRepository.Command prepared = commandService.prepare(
                request.campaignId(), request.moduleReleaseId(), request.partyNodeKey(),
                request.participants());

        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);
                EncounterCommandRepository.Lookup existing = commandRepository.find(
                        connection, new EncounterCommandRepository.Command(
                                request.requestId(), request.requestDigestSha256(),
                                request.campaignId()));
                if (existing.status() == EncounterCommandRepository.Status.CONFLICT) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.failure(Status.IDEMPOTENCY_CONFLICT);
                }
                if (existing.status() == EncounterCommandRepository.Status.REPLAY) {
                    connection.commit();
                    restore(connection, original);
                    return Result.replayed(existing.replay());
                }

                EncounterStateRepository.SavedEncounter saved =
                        stateRepository.initialize(connection, prepared);
                EncounterCommandRepository.SavedEvent event =
                        commandRepository.appendEvent(connection, request.campaignId());
                commandRepository.complete(
                        connection, new EncounterCommandRepository.Completion(
                                request.requestId(), request.requestDigestSha256(),
                                request.campaignId(), event.gameEventId()));
                connection.commit();
                restore(connection, original);
                return Result.completed(saved, event);
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private static void validate(Request request) {
        if (request == null || !canonicalUuid(request.requestId())
                || !canonicalUuidV4(request.campaignKey())
                || request.campaignId() <= 0 || request.moduleReleaseId() <= 0
                || request.participants() == null) {
            throw new IllegalArgumentException("Invalid encounter command request");
        }
    }

    private static boolean canonicalUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean canonicalUuidV4(String value) {
        if (!canonicalUuid(value)) return false;
        UUID uuid = UUID.fromString(value);
        return uuid.version() == 4 && uuid.variant() == 2;
    }

    private static boolean secureEquals(String expected, String supplied) {
        return supplied != null && supplied.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        supplied.getBytes(StandardCharsets.US_ASCII));
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
        connection.setTransactionIsolation(original.isolation());
    }

    public record Request(
            String requestId,
            String requestDigestSha256,
            long campaignId,
            String campaignKey,
            long moduleReleaseId,
            String partyNodeKey,
            List<EncounterStateService.ParticipantRequest> participants) {
        public Request {
            participants = participants == null ? null : List.copyOf(participants);
        }
    }

    public record Result(
            Status status,
            EncounterStateRepository.SavedEncounter savedEncounter,
            Long gameEventId,
            Long eventSequence,
            boolean replayed) {
        private static Result completed(
                EncounterStateRepository.SavedEncounter saved,
                EncounterCommandRepository.SavedEvent event) {
            return new Result(Status.COMPLETED, saved, event.gameEventId(),
                    event.eventSequence(), false);
        }

        private static Result replayed(EncounterCommandRepository.SavedEvent event) {
            return new Result(Status.COMPLETED, null, event.gameEventId(),
                    event.eventSequence(), true);
        }

        private static Result failure(Status status) {
            return new Result(status, null, null, null, false);
        }
    }

    public enum Status { COMPLETED, INVALID_REQUEST, IDEMPOTENCY_CONFLICT }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
