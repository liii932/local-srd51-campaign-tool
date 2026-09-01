package com.dndtool.service;

import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleHashManifest;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Shared fail-closed verification of one installed, application-approved module release. */
final class ModuleReleaseVerifier {
    private static final Pattern MODULE_KEY =
            Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Pattern RELEASE_VERSION =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final ModuleCatalogRepository repository;
    private final DigestComputer hasher;
    private final ModuleHashManifest manifest;
    private final BuiltinModuleReleaseRegistry registry;

    ModuleReleaseVerifier(
            ModuleCatalogRepository repository,
            DigestComputer hasher,
            ModuleHashManifest manifest) {
        this(repository, hasher, manifest, new BuiltinModuleReleaseRegistry());
    }

    ModuleReleaseVerifier(
            ModuleCatalogRepository repository,
            DigestComputer hasher,
            ModuleHashManifest manifest,
            BuiltinModuleReleaseRegistry registry) {
        this.repository = Objects.requireNonNull(repository);
        this.hasher = Objects.requireNonNull(hasher);
        this.manifest = Objects.requireNonNull(manifest);
        this.registry = Objects.requireNonNull(registry);
    }

    Result verify(String moduleKey, String releaseVersion) throws SQLException {
        if (!validIdentity(moduleKey, releaseVersion)) {
            return failure(Status.RELEASE_UNAVAILABLE);
        }
        BuiltinModuleReleaseRegistry.Resolution resolution =
                registry.resolveReleased(moduleKey, releaseVersion);
        if (resolution.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY) {
            return failure(Status.RELEASE_UNAVAILABLE);
        }
        BuiltinModuleReleaseRegistry.Descriptor descriptor = resolution.descriptor();
        Optional<ModuleCatalog> found = repository.findByIdentity(moduleKey, releaseVersion);
        if (found.isEmpty()) {
            return failure(Status.RELEASE_UNAVAILABLE);
        }

        ModuleCatalog catalog = found.orElseThrow();
        ModuleCatalog.Release release = catalog.release();
        if (release == null
                || !moduleKey.equals(release.moduleKey())
                || !releaseVersion.equals(release.releaseVersion())
                || release.canonicalFormatVersion() != descriptor.canonicalFormatVersion()
                || !descriptor.hashAlgorithm().equals(release.hashAlgorithm())
                || !isSha256(release.contentSha256())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }
        if (!"RELEASED".equals(release.releaseStatus())) {
            return failure(Status.RELEASE_UNAVAILABLE);
        }

        String approved = manifest.expectedSha256(release).orElse(null);
        if (!isSha256(approved)) {
            return failure(Status.RELEASE_UNAVAILABLE);
        }
        if (!secureEquals(approved, release.contentSha256())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }

        final String actual;
        try {
            actual = hasher.sha256(catalog);
        } catch (ModuleCanonicalException exception) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }
        if (!isSha256(actual) || !secureEquals(approved, actual)) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }
        return new Result(Status.READY, catalog, approved);
    }

    private static Result failure(Status status) {
        return new Result(status, null, null);
    }

    private static boolean validIdentity(String moduleKey, String releaseVersion) {
        return moduleKey != null && releaseVersion != null
                && MODULE_KEY.matcher(moduleKey).matches()
                && RELEASE_VERSION.matcher(releaseVersion).matches();
    }

    private static boolean isSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    static boolean secureEquals(String left, String right) {
        return left != null && right != null
                && MessageDigest.isEqual(
                        left.getBytes(StandardCharsets.US_ASCII),
                        right.getBytes(StandardCharsets.US_ASCII));
    }

    enum Status {
        READY,
        RELEASE_UNAVAILABLE,
        MODULE_HASH_MISMATCH
    }

    record Result(Status status, ModuleCatalog catalog, String contentSha256) {
        Result {
            Objects.requireNonNull(status);
            boolean ready = status == Status.READY;
            if (ready != (catalog != null) || ready != (contentSha256 != null)) {
                throw new IllegalArgumentException("Verified release does not match status");
            }
        }
    }

    @FunctionalInterface
    interface DigestComputer {
        String sha256(ModuleCatalog catalog) throws ModuleCanonicalException;
    }
}
