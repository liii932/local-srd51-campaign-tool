package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndtool.persistence.HostOverviewRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class HostOverviewServiceTest {
    @Test
    void acceptsFrozenOverviewWithDisplayOnlyTopology() throws Exception {
        HostOverviewService.Result result = service(snapshot()).load();

        assertEquals(HostOverviewService.Status.READY, result.status());
        assertEquals("map.tavern_cellar", result.snapshot().map().mapKey());
        assertEquals(1, result.snapshot().encounter().participants().size());
    }

    @Test
    void returnsEmptyWithoutAnActiveCampaign() throws Exception {
        HostOverviewService.Result result = service(null).load();

        assertEquals(HostOverviewService.Status.EMPTY, result.status());
        assertNull(result.snapshot());
    }

    @Test
    void moduleMismatchStopsOverviewReadAndPreservesStableFailureCategory() throws Exception {
        AtomicBoolean repositoryCalled = new AtomicBoolean();
        HostOverviewService service = new HostOverviewService(
                () -> {
                    repositoryCalled.set(true);
                    return Optional.of(snapshot());
                },
                () -> ModuleIntegrityService.Status.MODULE_HASH_MISMATCH);

        HostOverviewService.Result result = service.load();

        assertEquals(HostOverviewService.Status.MODULE_HASH_MISMATCH, result.status());
        assertNull(result.snapshot());
        assertEquals(false, repositoryCalled.get());
    }

    @Test
    void failsClosedOnReleaseNodeConnectionOrParticipantDrift() throws Exception {
        HostOverviewRepository.Snapshot base = snapshot();
        HostOverviewRepository.Binding badBinding = new HostOverviewRepository.Binding(
                11L, "dnd5e2014_srd51_se_v1", "1", "a".repeat(64),
                "dnd5e2014_srd51_se_v1", "1", "b".repeat(64), "RELEASED");
        assertInvalid(copy(base, badBinding, base.map(), base.encounter()));

        HostOverviewRepository.MapView badConnection = new HostOverviewRepository.MapView(
                "map.tavern_cellar", "NODE", true, "node.entry",
                base.map().nodes(), List.of(new HostOverviewRepository.MapConnection(
                        "node.entry", "Entry", "node.unknown", "Unknown")));
        assertInvalid(copy(base, base.binding(), badConnection, base.encounter()));

        HostOverviewRepository.Encounter badParticipant =
                new HostOverviewRepository.Encounter("ACTIVE", List.of(
                        new HostOverviewRepository.Participant(
                                "11111111-1111-4111-8111-111111111111", "Guard", "NPC",
                                "ALLY", "node.unknown", "Unknown")));
        assertInvalid(copy(base, base.binding(), base.map(), badParticipant));
    }

    @Test
    void rejectsInstantiatedMapWithoutPartyNode() throws Exception {
        HostOverviewRepository.Snapshot base = snapshot();
        HostOverviewRepository.MapView map = new HostOverviewRepository.MapView(
                base.map().mapKey(), base.map().mapType(), true, null,
                base.map().nodes(), base.map().connections());

        assertInvalid(copy(base, base.binding(), map, base.encounter()));
    }

    private static void assertInvalid(HostOverviewRepository.Snapshot snapshot)
            throws Exception {
        assertEquals(HostOverviewService.Status.INVALID_STATE,
                service(snapshot).load().status());
    }

    private static HostOverviewService service(
            HostOverviewRepository.Snapshot snapshot) {
        return new HostOverviewService(
                () -> Optional.ofNullable(snapshot),
                () -> ModuleIntegrityService.Status.READY);
    }

    private static HostOverviewRepository.Snapshot snapshot() {
        List<HostOverviewRepository.MapNode> nodes = List.of(
                new HostOverviewRepository.MapNode("node.entry", "Entry"),
                new HostOverviewRepository.MapNode("node.cellar", "Cellar"));
        HostOverviewRepository.MapView map = new HostOverviewRepository.MapView(
                "map.tavern_cellar", "NODE", true, "node.entry", nodes,
                List.of(new HostOverviewRepository.MapConnection(
                        "node.cellar", "Cellar", "node.entry", "Entry")));
        HostOverviewRepository.Encounter encounter =
                new HostOverviewRepository.Encounter("ACTIVE", List.of(
                        new HostOverviewRepository.Participant(
                                "11111111-1111-4111-8111-111111111111", "Guard", "NPC",
                                "ALLY", "node.cellar", "Cellar")));
        return new HostOverviewRepository.Snapshot(
                new HostOverviewRepository.Campaign(
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "Campaign", "ACTIVE", 0L, 0L),
                new HostOverviewRepository.Binding(
                        11L, "dnd5e2014_srd51_se_v1", "1", "a".repeat(64),
                        "dnd5e2014_srd51_se_v1", "1", "a".repeat(64), "RELEASED"),
                List.of(), List.of(), List.of(), map, encounter);
    }

    private static HostOverviewRepository.Snapshot copy(
            HostOverviewRepository.Snapshot source,
            HostOverviewRepository.Binding binding,
            HostOverviewRepository.MapView map,
            HostOverviewRepository.Encounter encounter) {
        return new HostOverviewRepository.Snapshot(
                source.campaign(), binding, source.characters(), source.items(), source.events(),
                map, encounter);
    }
}
