package com.dndtool.service;

import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.CharacterVersionRepository.VersionExpectation;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/** Validates and canonicalizes a DM request to directly replace one encounter node. */
public final class EntityPositionService {
    public static final String MODULE_KEY = BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY;
    public static final String RELEASE_VERSION = BuiltinModuleReleaseRegistry.RELEASE_VERSION_1;
    public static final String MAP_KEY = "map.tavern_cellar";
    public static final String MAP_TYPE = "NODE";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STABLE_KEY = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private final ModuleCatalog.Release release;
    private final Set<String> nodeKeys;

    public EntityPositionService(ModuleCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        release = validateRelease(catalog.release());
        validateMap(catalog.mapDefinitions());
        nodeKeys = validateNodes(catalog.mapNodes());
    }

    public PreparedRequest prepare(Request request) {
        if (request == null
                || !isCanonicalUuid(request.requestId())
                || request.campaignId() <= 0
                || request.moduleReleaseId() <= 0
                || !isCanonicalUuid(request.characterKey())
                || request.expectedRowVersion() < 0
                || request.expectedRowVersion() == Long.MAX_VALUE) {
            throw reject(Rejection.INVALID_REQUEST, "Direct-position request identity is invalid");
        }
        if (request.nodeKey() == null || !nodeKeys.contains(request.nodeKey())) {
            throw reject(Rejection.NODE_NOT_FOUND,
                    "Destination does not belong to the frozen tavern-cellar map");
        }

        String payloadSha256 = EntityPositionRequestDigest.canonicalPayloadSha256(
                request.campaignId(), request.moduleReleaseId(), request.characterKey(),
                MAP_KEY, request.nodeKey());
        VersionExpectation target = new VersionExpectation(
                request.characterKey(), request.expectedRowVersion());
        String requestDigest = CheckRequestDigest.sha256(
                payloadSha256, target, List.of());
        CharacterVersionService.Request versionRequest =
                new CharacterVersionService.Request(
                        request.campaignId(), request.moduleReleaseId(), payloadSha256,
                        requestDigest, target, List.of());
        if (request.campaignKey() != null || request.suppliedRequestDigestSha256() != null) {
            if (!isCanonicalUuidV4(request.campaignKey())) {
                throw reject(Rejection.INVALID_REQUEST, "Direct-position campaign key is invalid");
            }
            payloadSha256 = EntityPositionRequestDigest.canonicalPayloadSha256(
                    request.campaignKey(), request.characterKey(), MAP_KEY, request.nodeKey());
            requestDigest = CheckRequestDigest.sha256(payloadSha256, target, List.of());
            if (!secureEquals(requestDigest, request.suppliedRequestDigestSha256())) {
                throw reject(Rejection.INVALID_REQUEST, "Direct-position digest is invalid");
            }
            versionRequest = new CharacterVersionService.Request(
                    request.campaignId(), request.moduleReleaseId(), payloadSha256,
                    requestDigest, target, List.of());
        }
        return new PreparedRequest(
                request.requestId(), request.campaignId(), request.moduleReleaseId(),
                release.moduleKey(), release.releaseVersion(), release.contentSha256(),
                request.characterKey(), request.expectedRowVersion(), MAP_KEY,
                request.nodeKey(), requestDigest, versionRequest);
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
        long matching = maps.stream().filter(map -> MAP_KEY.equals(map.mapKey())).count();
        boolean exact = maps.stream().anyMatch(
                map -> MAP_KEY.equals(map.mapKey()) && MAP_TYPE.equals(map.mapType()));
        if (matching != 1 || !exact) {
            throw new IllegalStateException("Frozen tavern-cellar map definition is invalid");
        }
    }

    private static Set<String> validateNodes(List<ModuleCatalog.MapNode> nodes) {
        Set<String> result = new HashSet<>();
        for (ModuleCatalog.MapNode node : nodes) {
            if (!MAP_KEY.equals(node.mapKey())) continue;
            if (!STABLE_KEY.matcher(node.nodeKey()).matches() || !result.add(node.nodeKey())) {
                throw new IllegalStateException("Frozen tavern-cellar node catalog is invalid");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Frozen tavern-cellar map has no nodes");
        }
        return Set.copyOf(result);
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isCanonicalUuidV4(String value) {
        if (!isCanonicalUuid(value)) return false;
        UUID uuid = UUID.fromString(value);
        return uuid.version() == 4 && uuid.variant() == 2;
    }

    private static boolean secureEquals(String expected, String supplied) {
        return supplied != null && supplied.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        supplied.getBytes(StandardCharsets.US_ASCII));
    }

    private static DirectPositionException reject(Rejection rejection, String message) {
        return new DirectPositionException(rejection, message);
    }

    public record Request(
            String requestId,
            long campaignId,
            long moduleReleaseId,
            String campaignKey,
            String characterKey,
            long expectedRowVersion,
            String nodeKey,
            String suppliedRequestDigestSha256) {
        public Request(
                String requestId,
                long campaignId,
                long moduleReleaseId,
                String characterKey,
                long expectedRowVersion,
                String nodeKey) {
            this(requestId, campaignId, moduleReleaseId, null, characterKey,
                    expectedRowVersion, nodeKey, null);
        }
    }

    public record PreparedRequest(
            String requestId,
            long campaignId,
            long moduleReleaseId,
            String moduleKey,
            String releaseVersion,
            String contentSha256,
            String characterKey,
            long expectedRowVersion,
            String mapKey,
            String nodeKey,
            String requestDigestSha256,
            CharacterVersionService.Request versionRequest) {
    }

    public enum Rejection {
        INVALID_REQUEST,
        NODE_NOT_FOUND
    }

    public static final class DirectPositionException extends IllegalArgumentException {
        private final Rejection rejection;

        private DirectPositionException(Rejection rejection, String message) {
            super(message);
            this.rejection = rejection;
        }

        public Rejection rejection() {
            return rejection;
        }
    }
}
