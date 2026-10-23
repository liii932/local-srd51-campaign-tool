package com.dndtool.service;

import com.dndtool.persistence.BusinessWriteTransactions;
import com.dndtool.persistence.EncounterStateRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Owns the transaction that creates one map instance and its initial encounter state. */
public final class EncounterStateTransactionService {
    private final DataSource dataSource;
    private final EncounterStateService commandService;
    private final EncounterStateRepository repository;

    public EncounterStateTransactionService(
            DataSource dataSource,
            EncounterStateService commandService,
            EncounterStateRepository repository) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public EncounterStateRepository.SavedEncounter initialize(Request request)
            throws SQLException {
        Objects.requireNonNull(request, "request");
        // Frozen-catalog and request validation happens before a connection or row lock is acquired.
        EncounterStateRepository.Command command = commandService.prepare(
                request.campaignId(),
                request.moduleReleaseId(),
                request.partyNodeKey(),
                request.participants());

        try (BusinessWriteTransactions.Session writeTransaction =
                BusinessWriteTransactions.openLegacy(
                        dataSource, Connection.TRANSACTION_SERIALIZABLE)) {
            Connection connection = writeTransaction.connection();
            EncounterStateRepository.SavedEncounter saved =
                    repository.initialize(connection, command);
            writeTransaction.commit();
            return saved;
        }
    }

    public record Request(
            long campaignId,
            long moduleReleaseId,
            String partyNodeKey,
            List<EncounterStateService.ParticipantRequest> participants) {
        public Request {
            participants = participants == null ? null : List.copyOf(participants);
        }
    }
}
