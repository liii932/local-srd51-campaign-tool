package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Consistent read-only projection for the host overview page. */
public interface HostOverviewRepository {
    Optional<Snapshot> findActive() throws SQLException;

    record Snapshot(
            Campaign campaign,
            Binding binding,
            List<CharacterSummary> characters,
            List<ItemSummary> items,
            List<EventSummary> events,
            MapView map,
            Encounter encounter) {
        public Snapshot {
            characters = List.copyOf(characters);
            items = List.copyOf(items);
            events = List.copyOf(events);
        }
    }

    record Campaign(
            String campaignKey,
            String campaignName,
            String campaignStatus,
            long rowVersion,
            long hostStateEpoch) {
    }

    /** Stored and referenced release identities are kept separate so the service can fail closed. */
    record Binding(
            long moduleReleaseId,
            String frozenModuleKey,
            String frozenReleaseVersion,
            String frozenContentSha256,
            String releaseModuleKey,
            String releaseVersion,
            String releaseContentSha256,
            String releaseStatus) {
    }

    record CharacterSummary(
            String characterKey,
            String characterType,
            String characterName,
            String characterStatus,
            long rowVersion,
            Long currentHp,
            Long maximumHp,
            Long armorClass,
            int itemCount) {
    }

    record ItemSummary(
            String characterKey,
            String characterName,
            String sourceKind,
            String itemKey,
            String itemName,
            String itemDescription,
            int quantity,
            String itemStatus) {
    }

    record EventSummary(
            long eventSequence,
            String eventType,
            String subjectName,
            String eventText,
            String checkKey,
            String manualName,
            String rollModeKey,
            String modifierSourceKey,
            Integer modifierValue,
            Integer totalValue,
            Integer difficultyClass,
            String checkResult) {
    }

    record MapView(
            String mapKey,
            String mapType,
            boolean instantiated,
            String partyNodeKey,
            List<MapNode> nodes,
            List<MapConnection> connections) {
        public MapView {
            nodes = List.copyOf(nodes);
            connections = List.copyOf(connections);
        }
    }

    record MapNode(String nodeKey, String displayName) {
    }

    record MapConnection(
            String endpointLowKey,
            String endpointLowName,
            String endpointHighKey,
            String endpointHighName) {
    }

    record Encounter(String battleStatus, List<Participant> participants) {
        public Encounter {
            participants = List.copyOf(participants);
        }
    }

    record Participant(
            String characterKey,
            String characterName,
            String characterType,
            String faction,
            String nodeKey,
            String nodeName) {
    }
}
