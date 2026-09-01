package com.dndtool.service;

import com.dndtool.persistence.CharacterVersionRepository;
import com.dndtool.persistence.EntityPositionCommandRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

/** Owns the atomic, idempotent DM direct-position transaction. */
public final class EntityPositionTransactionService {
    private final DataSource dataSource;
    private final EntityPositionService commandService;
    private final CharacterVersionService versionService;
    private final EntityPositionCommandRepository repository;

    public EntityPositionTransactionService(
            DataSource dataSource,
            EntityPositionService commandService,
            CharacterVersionService versionService,
            EntityPositionCommandRepository repository) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.versionService = Objects.requireNonNull(versionService, "versionService");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Result position(EntityPositionService.Request request) throws SQLException {
        // Frozen-catalog validation happens before a connection, lock or idempotency range is used.
        EntityPositionService.PreparedRequest prepared = commandService.prepare(request);
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                EntityPositionCommandRepository.Lookup existing = repository.find(
                        connection,
                        new EntityPositionCommandRepository.IdempotencyCommand(
                                prepared.requestId(), prepared.requestDigestSha256(),
                                prepared.campaignId()));
                if (existing.status() == EntityPositionCommandRepository.Status.CONFLICT) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.idempotencyConflict();
                }
                if (existing.status() == EntityPositionCommandRepository.Status.REPLAY) {
                    if (!prepared.characterKey().equals(existing.replay().characterKey())
                            || !prepared.nodeKey().equals(existing.replay().nodeKey())) {
                        throw new SQLException("Replayed direct-position result is inconsistent");
                    }
                    connection.commit();
                    restore(connection, original);
                    return Result.replayed(existing.replay());
                }

                CharacterVersionService.Result<WorkResult> versioned =
                        versionService.executeLocked(
                                connection,
                                prepared.versionRequest(),
                                Set.of(),
                                (transaction, scope) -> executeLocked(
                                        transaction, scope, prepared));
                if (versioned.status() != CharacterVersionRepository.Status.LOCKED) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.rejected(
                            versioned.status(), versioned.rejectedCharacterKey(),
                            versioned.currentRowVersion());
                }

                WorkResult work = versioned.value();
                Long rowVersion = work.move().changed()
                        ? versioned.advancedRowVersions().get(work.event().characterId())
                        : prepared.expectedRowVersion();
                if (rowVersion == null) {
                    throw new SQLException("Direct-position version result is missing");
                }
                repository.complete(
                        connection,
                        new EntityPositionCommandRepository.Completion(
                                prepared.requestId(), prepared.requestDigestSha256(),
                                prepared.campaignId(), work.event().gameEventId()));
                connection.commit();
                restore(connection, original);
                return Result.completed(work.move(), work.event(), rowVersion);
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private CharacterVersionService.LockedWorkResult<WorkResult> executeLocked(
            Connection connection,
            CharacterVersionRepository.LockedScope scope,
            EntityPositionService.PreparedRequest prepared) throws SQLException {
        CharacterVersionRepository.LockedCharacter target = scope.executor();
        EntityPositionCommandRepository.MoveResult move = repository.move(
                connection,
                new EntityPositionCommandRepository.MoveCommand(
                        scope.campaignId(), scope.moduleReleaseId(),
                        prepared.moduleKey(), prepared.releaseVersion(),
                        prepared.contentSha256(), prepared.mapKey(), target.id(),
                        target.characterKey(), prepared.nodeKey()));
        EntityPositionCommandRepository.SavedEvent event = repository.appendEvent(
                connection,
                new EntityPositionCommandRepository.EventCommand(
                        scope.campaignId(), scope.expectedEventTail(), target.id(),
                        prepared.nodeKey()));
        // Direct placement changes no other aggregate; a same-node command is a durable no-op.
        Set<Long> modified = move.changed() ? Set.of(target.id()) : Set.of();
        return new CharacterVersionService.LockedWorkResult<>(
                new WorkResult(move, event), modified);
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

    public record Result(
            Status status,
            Long gameEventId,
            Long eventSequence,
            Long characterId,
            String previousNodeKey,
            String nodeKey,
            Long rowVersion,
            boolean changed,
            boolean replayed,
            String rejectedCharacterKey,
            Long currentRowVersion) {
        private static Result completed(
                EntityPositionCommandRepository.MoveResult move,
                EntityPositionCommandRepository.SavedEvent event,
                long rowVersion) {
            return new Result(
                    Status.COMPLETED, event.gameEventId(), event.eventSequence(),
                    event.characterId(), move.previousNodeKey(), move.nodeKey(), rowVersion,
                    move.changed(), false, null, null);
        }

        private static Result replayed(EntityPositionCommandRepository.Replay replay) {
            return new Result(
                    Status.COMPLETED, replay.gameEventId(), replay.eventSequence(),
                    replay.characterId(), null, replay.nodeKey(), null,
                    false, true, null, null);
        }

        private static Result idempotencyConflict() {
            return new Result(
                    Status.IDEMPOTENCY_CONFLICT, null, null, null, null, null, null,
                    false, false, null, null);
        }

        private static Result rejected(
                CharacterVersionRepository.Status status,
                String characterKey,
                Long currentVersion) {
            return new Result(
                    Status.valueOf(status.name()), null, null, null, null, null, null,
                    false, false, characterKey, currentVersion);
        }
    }

    public enum Status {
        COMPLETED,
        CAMPAIGN_NOT_FOUND,
        CHARACTER_NOT_FOUND,
        CHARACTER_INVALID,
        MODULE_HASH_MISMATCH,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT
    }

    private record WorkResult(
            EntityPositionCommandRepository.MoveResult move,
            EntityPositionCommandRepository.SavedEvent event) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
