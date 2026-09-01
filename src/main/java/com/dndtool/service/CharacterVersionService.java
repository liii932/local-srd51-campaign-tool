package com.dndtool.service;

import com.dndtool.persistence.CharacterVersionRepository;
import com.dndtool.persistence.CharacterVersionRepository.LockCommand;
import com.dndtool.persistence.CharacterVersionRepository.LockResult;
import com.dndtool.persistence.CharacterVersionRepository.LockedScope;
import com.dndtool.persistence.CharacterVersionRepository.VersionExpectation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Enforces the pre-roll optimistic-version boundary for a host command check. */
public final class CharacterVersionService {
    private final CharacterVersionRepository repository;

    public CharacterVersionService(CharacterVersionRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /**
     * Invokes {@code work} only after all versions match. The work must use the same connection
     * for dice, events and effects; {@code requiredTargetKeys} must come from both validated effect
     * branches. Returning modified ids advances each aggregate exactly once.
     */
    public <T> Result<T> executeLocked(
            Connection connection,
            Request request,
            Set<String> requiredTargetKeys,
            LockedWork<T> work) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(work, "locked work is required");
        PreparedScope prepared = prepare(request, requiredTargetKeys);
        LockResult lock = repository.lockBeforeRoll(connection, new LockCommand(
                request.campaignId(), request.moduleReleaseId(),
                prepared.executor(), prepared.targets()));
        if (lock.status() != CharacterVersionRepository.Status.LOCKED) {
            return new Result<>(lock.status(), null, Map.of(),
                    lock.rejectedCharacterKey(), lock.currentRowVersion());
        }
        LockedWorkResult<T> workResult = Objects.requireNonNull(
                work.execute(connection, lock.scope()), "locked work result is required");
        Set<Long> modifiedIds = Set.copyOf(Objects.requireNonNull(
                workResult.modifiedCharacterIds(), "modified character ids are required"));
        Map<Long, Long> versions = repository.advanceModifiedVersions(
                connection, lock.scope(), modifiedIds);
        return new Result<>(CharacterVersionRepository.Status.LOCKED,
                workResult.value(), versions, null, null);
    }

    private static PreparedScope prepare(Request request, Set<String> requiredTargetKeys) {
        if (request == null || request.campaignId() <= 0 || request.moduleReleaseId() <= 0
                || !isSha256(request.canonicalPayloadSha256())
                || !isSha256(request.requestDigestSha256())) {
            throw invalidRequest();
        }
        VersionExpectation executor = validateExpectation(request.executor());
        LinkedHashMap<String, VersionExpectation> uniqueTargets = new LinkedHashMap<>();
        if (request.possibleTargets() == null) throw invalidRequest();
        for (VersionExpectation raw : request.possibleTargets()) {
            VersionExpectation target = validateExpectation(raw);
            VersionExpectation existing = uniqueTargets.putIfAbsent(target.characterKey(), target);
            if (existing != null
                    && existing.expectedRowVersion() != target.expectedRowVersion()) {
                throw invalidRequest();
            }
        }
        List<VersionExpectation> targets = new ArrayList<>(uniqueTargets.values());
        targets.sort(Comparator.comparing(VersionExpectation::characterKey));
        if (requiredTargetKeys == null) throw invalidRequest();
        Set<String> validatedRequiredKeys = new HashSet<>();
        for (String key : requiredTargetKeys) {
            if (!isCanonicalUuid(key) || !validatedRequiredKeys.add(key)) throw invalidRequest();
        }
        if (!validatedRequiredKeys.equals(uniqueTargets.keySet())) throw invalidRequest();
        String actual = CheckRequestDigest.sha256(
                request.canonicalPayloadSha256(), executor, targets);
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                request.requestDigestSha256().getBytes(StandardCharsets.US_ASCII))) {
            throw invalidRequest();
        }
        return new PreparedScope(executor, List.copyOf(targets));
    }

    private static VersionExpectation validateExpectation(VersionExpectation expectation) {
        if (expectation == null || !isCanonicalUuid(expectation.characterKey())
                || expectation.expectedRowVersion() < 0
                || expectation.expectedRowVersion() == Long.MAX_VALUE) {
            throw invalidRequest();
        }
        return expectation;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static IllegalArgumentException invalidRequest() {
        return new IllegalArgumentException("Invalid host command character version request");
    }

    public record Request(
            long campaignId,
            long moduleReleaseId,
            String canonicalPayloadSha256,
            String requestDigestSha256,
            VersionExpectation executor,
            List<VersionExpectation> possibleTargets) {
        public Request {
            possibleTargets = possibleTargets == null ? null : List.copyOf(possibleTargets);
        }
    }

    @FunctionalInterface
    public interface LockedWork<T> {
        LockedWorkResult<T> execute(Connection connection, LockedScope scope) throws SQLException;
    }

    public record LockedWorkResult<T>(T value, Set<Long> modifiedCharacterIds) {
        public LockedWorkResult {
            modifiedCharacterIds = Set.copyOf(modifiedCharacterIds);
        }
    }

    public record Result<T>(
            CharacterVersionRepository.Status status,
            T value,
            Map<Long, Long> advancedRowVersions,
            String rejectedCharacterKey,
            Long currentRowVersion) {
        public Result {
            advancedRowVersions = Map.copyOf(advancedRowVersions);
        }
    }

    private record PreparedScope(
            VersionExpectation executor, List<VersionExpectation> targets) {
    }
}
