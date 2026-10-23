package com.dndtool.service;

import com.dndtool.persistence.BusinessWriteTransactions;
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

        try (BusinessWriteTransactions.Session writeTransaction =
                BusinessWriteTransactions.openLegacy(
                        dataSource, Connection.TRANSACTION_SERIALIZABLE)) {
            Connection connection = writeTransaction.connection();
            EncounterCommandRepository.Lookup existing = commandRepository.find(
                    connection, new EncounterCommandRepository.Command(
                            request.requestId(), request.requestDigestSha256(),
                            request.campaignId()));
            if (existing.status() == EncounterCommandRepository.Status.CONFLICT) {
                writeTransaction.rollback();
                return Result.failure(Status.IDEMPOTENCY_CONFLICT);
            }
            if (existing.status() == EncounterCommandRepository.Status.REPLAY) {
                writeTransaction.commit();
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
            writeTransaction.commit();
            return Result.completed(saved, event);
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
}
