package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.persistence.CharacterLifecycleMutationRepository;
import com.dndtool.persistence.CharacterLifecycleMutationRepository.Action;
import com.dndtool.persistence.CharacterModuleBindingRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Validates host lifecycle commands before entering the atomic JDBC mutation boundary. */
public final class CharacterLifecycleCommandService {
    private final ModuleCatalogRepository moduleRepository;
    private final CharacterLifecycleMutationRepository mutationRepository;
    private final ModuleContentHasher hasher;
    private final BuiltinModuleHashManifest manifest;
    private final CharacterModuleBindingRepository characterBindings;
    private final BuiltinModuleReleaseRegistry releaseRegistry;

    public CharacterLifecycleCommandService(
            ModuleCatalogRepository moduleRepository,
            CharacterLifecycleMutationRepository mutationRepository,
            CharacterModuleBindingRepository characterBindings) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository);
        this.mutationRepository = Objects.requireNonNull(mutationRepository);
        this.hasher = new ModuleContentHasher();
        this.manifest = new BuiltinModuleHashManifest();
        this.characterBindings = Objects.requireNonNull(characterBindings);
        this.releaseRegistry = new BuiltinModuleReleaseRegistry();
    }

    public Result mutate(
            String characterKey,
            String rowVersion,
            String actionValue,
            String value,
            String requestId,
            String requestDigestSha256) throws SQLException {
        final long expectedRowVersion;
        final Action action;
        final String normalizedValue;
        try {
            expectedRowVersion = parseRowVersion(rowVersion);
            action = Action.valueOf(actionValue == null ? "" : actionValue);
            normalizedValue = normalizeValue(action, value);
        } catch (IllegalArgumentException exception) {
            return failure(Status.INVALID_REQUEST);
        }
        if (!isCanonicalUuid(characterKey) || !isCanonicalUuid(requestId)
                || !isSha256(requestDigestSha256)) {
            return failure(Status.INVALID_REQUEST);
        }

        String actualDigest = CharacterLifecycleRequestDigest.sha256(
                characterKey, expectedRowVersion, action.name(), normalizedValue);
        if (!secureEquals(actualDigest, requestDigestSha256)) {
            return failure(Status.INVALID_REQUEST);
        }

        Optional<CharacterModuleBindingRepository.Binding> bound =
                characterBindings.findByCharacterKey(characterKey);
        if (bound.isEmpty()) {
            return failure(Status.NOT_FOUND);
        }
        CharacterModuleBindingRepository.Binding binding = bound.orElseThrow();
        BuiltinModuleReleaseRegistry.Resolution resolved = releaseRegistry.resolveReleased(
                binding.savedModuleKey(), binding.savedReleaseVersion());
        if (resolved.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY) {
            return failure(Status.MODULE_UNAVAILABLE);
        }
        Optional<ModuleCatalog> found = moduleRepository.findByIdentity(
                binding.savedModuleKey(), binding.savedReleaseVersion());
        if (found.isEmpty()) {
            return failure(Status.MODULE_UNAVAILABLE);
        }
        ModuleCatalog catalog = found.orElseThrow();
        if (catalog.release() == null
                || !binding.savedModuleKey().equals(catalog.release().moduleKey())
                || !binding.savedReleaseVersion().equals(
                        catalog.release().releaseVersion())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }
        String approvedHash = verifyRelease(catalog);
        if (approvedHash == null || !approvedHash.equals(binding.savedContentSha256())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }

        CharacterLifecycleMutationRepository.Result persisted = mutationRepository.mutate(
                new CharacterLifecycleMutationRepository.Command(
                        requestId, requestDigestSha256, characterKey, expectedRowVersion,
                        action, normalizedValue, binding.savedModuleKey(),
                        binding.savedReleaseVersion(), approvedHash));
        return switch (persisted.status()) {
            case UPDATED -> new Result(Status.UPDATED, persisted.rowVersion());
            case ALREADY_SUCCEEDED ->
                    new Result(Status.ALREADY_SUCCEEDED, persisted.rowVersion());
            case NOT_FOUND -> failure(Status.NOT_FOUND);
            case VERSION_CONFLICT ->
                    new Result(Status.VERSION_CONFLICT, persisted.rowVersion());
            case IDEMPOTENCY_CONFLICT -> failure(Status.IDEMPOTENCY_CONFLICT);
            case MODULE_BINDING_MISMATCH -> failure(Status.MODULE_HASH_MISMATCH);
            case NO_CHANGE -> new Result(Status.NO_CHANGE, persisted.rowVersion());
        };
    }

    private String verifyRelease(ModuleCatalog catalog) {
        ModuleCatalog.Release release = catalog.release();
        if (!"RELEASED".equals(release.releaseStatus()) || release.contentSha256() == null) {
            return null;
        }
        String expected = manifest.expectedSha256(release).orElse(null);
        final String actual;
        try {
            actual = hasher.sha256(catalog);
        } catch (ModuleCanonicalException exception) {
            return null;
        }
        return expected != null
                        && secureEquals(expected, release.contentSha256())
                        && secureEquals(expected, actual)
                ? expected : null;
    }

    private static long parseRowVersion(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("Invalid row version");
        }
        long parsed = Long.parseLong(value);
        if (parsed == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Row version cannot be advanced");
        }
        return parsed;
    }

    private static String normalizeValue(Action action, String value) {
        return switch (action) {
            case RENAME -> CharacterNamePolicy.normalize(value);
            case CHANGE_TYPE -> {
                String type = value == null ? "" : value;
                if (!"PC".equals(type) && !"NPC".equals(type)) {
                    throw new IllegalArgumentException("Invalid character type");
                }
                yield type;
            }
            case ARCHIVE, RESTORE -> {
                if (value != null && !value.isBlank()) {
                    throw new IllegalArgumentException("Lifecycle status command has no value");
                }
                yield "";
            }
        };
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static Result failure(Status status) {
        return new Result(status, null);
    }

    public record Result(Status status, Long rowVersion) {
    }

    public enum Status {
        UPDATED,
        ALREADY_SUCCEEDED,
        INVALID_REQUEST,
        NOT_FOUND,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        NO_CHANGE,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH
    }
}
