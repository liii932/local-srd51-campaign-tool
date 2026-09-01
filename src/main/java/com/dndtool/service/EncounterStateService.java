package com.dndtool.service;

import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.EncounterStateRepository;
import com.dndtool.persistence.EncounterStateRepository.Command;
import com.dndtool.persistence.EncounterStateRepository.ParticipantPlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Builds a persistence command only from the frozen built-in map catalog. */
public final class EncounterStateService {
    public static final String MODULE_KEY = BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY;
    public static final String RELEASE_VERSION = BuiltinModuleReleaseRegistry.RELEASE_VERSION_1;
    public static final String MAP_KEY = "map.tavern_cellar";
    public static final String MAP_TYPE = "NODE";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STABLE_KEY = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private final ModuleCatalog.Release release;
    private final Set<String> nodeKeys;

    public EncounterStateService(ModuleCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        release = validateRelease(catalog.release());
        validateMap(catalog.mapDefinitions());
        nodeKeys = validateNodes(catalog.mapNodes());
    }

    public Command prepare(
            long campaignId,
            long moduleReleaseId,
            String partyNodeKey,
            List<ParticipantRequest> participants) {
        if (campaignId <= 0 || moduleReleaseId <= 0) {
            throw reject(Rejection.INVALID_REQUEST, "Campaign and module release ids must be positive");
        }
        requireKnownNode(partyNodeKey);
        if (participants == null) {
            throw reject(Rejection.INVALID_REQUEST, "Participants are required");
        }

        Set<String> characterKeys = new HashSet<>();
        List<ParticipantPlacement> placements = new ArrayList<>(participants.size());
        for (ParticipantRequest participant : participants) {
            if (participant == null
                    || !CANONICAL_UUID.matcher(participant.characterKey()).matches()
                    || participant.faction() == null) {
                throw reject(Rejection.INVALID_REQUEST, "Participant identity or faction is invalid");
            }
            if (!characterKeys.add(participant.characterKey())) {
                throw reject(Rejection.DUPLICATE_PARTICIPANT, "A character can join the encounter only once");
            }
            requireKnownNode(participant.nodeKey());
            placements.add(new ParticipantPlacement(
                    participant.characterKey(), participant.faction(), participant.nodeKey()));
        }

        // Canonical ordering keeps lock and write order independent of request ordering.
        placements.sort(Comparator.comparing(ParticipantPlacement::characterKey));
        return new Command(
                campaignId,
                moduleReleaseId,
                release.moduleKey(),
                release.releaseVersion(),
                release.contentSha256(),
                MAP_KEY,
                MAP_TYPE,
                partyNodeKey,
                placements);
    }

    private static ModuleCatalog.Release validateRelease(ModuleCatalog.Release candidate) {
        BuiltinModuleReleaseRegistry.Resolution resolved =
                new BuiltinModuleReleaseRegistry().resolveReleased(
                        candidate.moduleKey(), candidate.releaseVersion());
        if (resolved.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY
                || !"RELEASED".equals(candidate.releaseStatus())
                || candidate.canonicalFormatVersion()
                        != resolved.descriptor().canonicalFormatVersion()
                || !resolved.descriptor().hashAlgorithm().equals(candidate.hashAlgorithm())
                || !SHA_256.matcher(candidate.contentSha256()).matches()) {
            throw new IllegalStateException("Frozen module release identity is invalid");
        }
        return candidate;
    }

    private static void validateMap(List<ModuleCatalog.MapDefinition> maps) {
        long matchingMaps = maps.stream().filter(map -> MAP_KEY.equals(map.mapKey())).count();
        boolean exactMap = maps.stream().anyMatch(
                map -> MAP_KEY.equals(map.mapKey()) && MAP_TYPE.equals(map.mapType()));
        if (matchingMaps != 1 || !exactMap) {
            throw new IllegalStateException("Frozen tavern-cellar map definition is invalid");
        }
    }

    private static Set<String> validateNodes(List<ModuleCatalog.MapNode> nodes) {
        Set<String> result = new HashSet<>();
        for (ModuleCatalog.MapNode node : nodes) {
            if (!MAP_KEY.equals(node.mapKey())) {
                continue;
            }
            if (!STABLE_KEY.matcher(node.nodeKey()).matches() || !result.add(node.nodeKey())) {
                throw new IllegalStateException("Frozen tavern-cellar node catalog is invalid");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Frozen tavern-cellar map has no nodes");
        }
        return Set.copyOf(result);
    }

    private void requireKnownNode(String nodeKey) {
        if (nodeKey == null || !nodeKeys.contains(nodeKey)) {
            throw reject(Rejection.NODE_NOT_FOUND, "Map node does not belong to the frozen tavern-cellar map");
        }
    }

    private static EncounterStateException reject(Rejection rejection, String message) {
        return new EncounterStateException(rejection, message);
    }

    public record ParticipantRequest(
            String characterKey,
            EncounterStateRepository.Faction faction,
            String nodeKey) {
    }

    public enum Rejection {
        INVALID_REQUEST,
        NODE_NOT_FOUND,
        DUPLICATE_PARTICIPANT
    }

    public static final class EncounterStateException extends IllegalArgumentException {
        private final Rejection rejection;

        private EncounterStateException(Rejection rejection, String message) {
            super(message);
            this.rejection = rejection;
        }

        public Rejection rejection() {
            return rejection;
        }
    }
}
