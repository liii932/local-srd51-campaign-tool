package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Persists the first host-command map and encounter state inside a caller-owned transaction.
 * Implementations must never commit or roll back the supplied connection.
 */
public interface EncounterStateRepository {

    SavedEncounter initialize(Connection connection, Command command) throws SQLException;

    /** Stable internal signal for a saved character identity that drifted from its frozen module. */
    final class ModuleHashMismatchException extends SQLException {
        private static final long serialVersionUID = 1L;

        public ModuleHashMismatchException() {
            super("Encounter character module binding does not match the frozen release");
        }
    }

    enum Faction {
        ALLY,
        ENEMY,
        NEUTRAL
    }

    record ParticipantPlacement(String characterKey, Faction faction, String nodeKey) {
        public ParticipantPlacement {
            Objects.requireNonNull(characterKey, "characterKey");
            Objects.requireNonNull(faction, "faction");
            Objects.requireNonNull(nodeKey, "nodeKey");
        }
    }

    record Command(
            long campaignId,
            long moduleReleaseId,
            String moduleKey,
            String releaseVersion,
            String contentSha256,
            String mapKey,
            String mapType,
            String partyNodeKey,
            List<ParticipantPlacement> participants) {
        public Command {
            Objects.requireNonNull(moduleKey, "moduleKey");
            Objects.requireNonNull(releaseVersion, "releaseVersion");
            Objects.requireNonNull(contentSha256, "contentSha256");
            Objects.requireNonNull(mapKey, "mapKey");
            Objects.requireNonNull(mapType, "mapType");
            Objects.requireNonNull(partyNodeKey, "partyNodeKey");
            participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        }
    }

    record SavedParticipant(
            long participantId,
            long characterId,
            String characterKey,
            Faction faction,
            String nodeKey) {
    }

    record SavedEncounter(
            long mapInstanceId,
            long battleId,
            List<SavedParticipant> participants) {
        public SavedEncounter {
            participants = List.copyOf(participants);
        }
    }
}
