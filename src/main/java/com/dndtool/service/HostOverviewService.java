package com.dndtool.service;

import com.dndtool.persistence.HostOverviewRepository;
import com.dndtool.persistence.HostOverviewRepository.MapConnection;
import com.dndtool.persistence.HostOverviewRepository.MapNode;
import com.dndtool.persistence.HostOverviewRepository.Snapshot;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Fails closed unless the host projection belongs to the frozen built-in host command rules. */
public final class HostOverviewService {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STABLE_KEY = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private final HostOverviewRepository repository;
    private final IntegrityVerifier integrityVerifier;

    public HostOverviewService(
            HostOverviewRepository repository,
            ModuleIntegrityService integrityService) {
        this(repository, Objects.requireNonNull(integrityService, "integrityService")::verifyAll);
    }

    HostOverviewService(
            HostOverviewRepository repository,
            IntegrityVerifier integrityVerifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.integrityVerifier = Objects.requireNonNull(
                integrityVerifier, "integrityVerifier");
    }

    public Result load() throws SQLException {
        if (integrityVerifier.verify() != ModuleIntegrityService.Status.READY) {
            return new Result(Status.MODULE_HASH_MISMATCH, null);
        }
        Optional<Snapshot> found = repository.findActive();
        if (found.isEmpty()) return new Result(Status.EMPTY, null);
        Snapshot snapshot = found.get();
        if (!valid(snapshot)) return new Result(Status.INVALID_STATE, null);
        return new Result(Status.READY, snapshot);
    }

    private static boolean valid(Snapshot snapshot) {
        if (snapshot == null || snapshot.campaign() == null || snapshot.binding() == null
                || snapshot.map() == null || snapshot.encounter() == null) {
            return false;
        }
        HostOverviewRepository.Binding binding = snapshot.binding();
        if (binding.moduleReleaseId() <= 0
                || !EncounterStateService.MODULE_KEY.equals(binding.frozenModuleKey())
                || !EncounterStateService.RELEASE_VERSION.equals(
                        binding.frozenReleaseVersion())
                || !binding.frozenModuleKey().equals(binding.releaseModuleKey())
                || !binding.frozenReleaseVersion().equals(binding.releaseVersion())
                || !binding.frozenContentSha256().equals(binding.releaseContentSha256())
                || !SHA_256.matcher(binding.frozenContentSha256()).matches()
                || !"RELEASED".equals(binding.releaseStatus())) {
            return false;
        }
        if (!EncounterStateService.MAP_KEY.equals(snapshot.map().mapKey())
                || !EncounterStateService.MAP_TYPE.equals(snapshot.map().mapType())) {
            return false;
        }

        Set<String> nodes = new HashSet<>();
        for (MapNode node : snapshot.map().nodes()) {
            if (node == null || node.displayName() == null || node.displayName().isBlank()
                    || !stable(node.nodeKey()) || !nodes.add(node.nodeKey())) {
                return false;
            }
        }
        if (nodes.isEmpty()
                || (snapshot.map().instantiated() && snapshot.map().partyNodeKey() == null)
                || (snapshot.map().partyNodeKey() != null
                        && !nodes.contains(snapshot.map().partyNodeKey()))) {
            return false;
        }
        for (MapConnection connection : snapshot.map().connections()) {
            if (connection == null
                    || !nodes.contains(connection.endpointLowKey())
                    || !nodes.contains(connection.endpointHighKey())
                    || connection.endpointLowKey().compareTo(connection.endpointHighKey()) >= 0) {
                return false;
            }
        }
        if (snapshot.encounter().battleStatus() != null
                && !"ACTIVE".equals(snapshot.encounter().battleStatus())) {
            return false;
        }
        return snapshot.encounter().participants().stream().allMatch(participant ->
                participant != null
                        && nodes.contains(participant.nodeKey())
                        && Set.of("ALLY", "ENEMY", "NEUTRAL").contains(participant.faction()));
    }

    private static boolean stable(String value) {
        return value != null && STABLE_KEY.matcher(value).matches();
    }

    public record Result(Status status, Snapshot snapshot) {
        public Result {
            Objects.requireNonNull(status, "overview status is required");
            if ((status == Status.READY) != (snapshot != null)) {
                throw new IllegalArgumentException("Overview snapshot does not match status");
            }
        }
    }

    public enum Status {
        READY,
        EMPTY,
        MODULE_HASH_MISMATCH,
        INVALID_STATE
    }

    @FunctionalInterface
    interface IntegrityVerifier {
        ModuleIntegrityService.Status verify() throws SQLException;
    }
}
