package com.dndtool.service;

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

        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);
                EncounterStateRepository.SavedEncounter saved =
                        repository.initialize(connection, command);
                connection.commit();
                restore(connection, original);
                return saved;
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private static void rollbackAndRestore(
            Connection connection,
            ConnectionState original,
            Exception failure) {
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

    private static void restore(Connection connection, ConnectionState original) throws SQLException {
        connection.setAutoCommit(original.autoCommit());
        connection.setReadOnly(original.readOnly());
        connection.setTransactionIsolation(original.isolation());
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

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(),
                    connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
