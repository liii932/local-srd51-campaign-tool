package com.dndtool.persistence;

import com.dndtool.service.D20CheckCalculator;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Appends one immutable d20 snapshot to an existing caller-owned transaction.
 *
 * <p>The surrounding command service must lock and verify the campaign and participating character
 * aggregates before rolling. This repository then advances the event sequence and writes the root
 * event, check row and every ordered candidate without committing or rolling back the connection.
 */
public interface CheckExecutionRepository {

    /** Writes the complete snapshot and returns the generated identities needed by idempotency. */
    SavedCheck append(Connection connection, Command command) throws SQLException;

    /**
     * Database identities and server-computed values required to persist one completed check.
     */
    record Command(
            long campaignId,
            long expectedEventTail,
            long moduleReleaseId,
            long executorCharacterId,
            String eventKey,
            String checkKey,
            String modifierSourceKey,
            String manualName,
            D20CheckCalculator.Result calculation) {
    }

    /** Generated root identities returned to the surrounding transaction coordinator. */
    record SavedCheck(long gameEventId, long checkExecutionId, long eventSequence) {
    }
}
