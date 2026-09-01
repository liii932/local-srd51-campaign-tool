package com.dndtool.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Immutable application registry of built-in rule-release identities and their wire formats.
 * Database rows are installed separately; a registry entry never proves that a release exists.
 */
public final class BuiltinModuleReleaseRegistry {
    public static final String LEGACY_MODULE_KEY = "dnd5e2014_srd51_se_v1";
    public static final String COMPLETE_MODULE_KEY = "dnd5e2014_srd51_se";
    public static final String RELEASE_VERSION_1 = "1";
    public static final int LEGACY_CANONICAL_FORMAT_VERSION = 1;
    public static final int LEGACY_ARCHIVE_FORMAT_VERSION = 1;
    public static final int COMPLETE_CANONICAL_FORMAT_VERSION = 2;
    public static final int COMPLETE_ARCHIVE_FORMAT_VERSION = 2;
    public static final String LEGACY_CONTENT_SHA256 =
            "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e";

    private static final Pattern MODULE_KEY =
            Pattern.compile("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*");
    private static final Pattern RELEASE_VERSION =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private static final Identity DEFAULT_IDENTITY =
            new Identity(LEGACY_MODULE_KEY, RELEASE_VERSION_1);
    private static final List<Descriptor> STANDARD_RELEASES = List.of(
            new Descriptor(
                    DEFAULT_IDENTITY,
                    LEGACY_CANONICAL_FORMAT_VERSION,
                    LEGACY_ARCHIVE_FORMAT_VERSION,
                    "SHA-256",
                    LEGACY_CONTENT_SHA256,
                    ReleaseStatus.RELEASED),
            new Descriptor(
                    new Identity(COMPLETE_MODULE_KEY, RELEASE_VERSION_1),
                    COMPLETE_CANONICAL_FORMAT_VERSION,
                    COMPLETE_ARCHIVE_FORMAT_VERSION,
                    "SHA-256",
                    null,
                    ReleaseStatus.DRAFT));

    private final Map<Identity, Descriptor> releases;
    private final Descriptor defaultRelease;

    public BuiltinModuleReleaseRegistry() {
        this(STANDARD_RELEASES, DEFAULT_IDENTITY);
    }

    BuiltinModuleReleaseRegistry(List<Descriptor> definitions, Identity defaultIdentity) {
        Objects.requireNonNull(definitions, "release definitions");
        Objects.requireNonNull(defaultIdentity, "default identity");
        Map<Identity, Descriptor> indexed = new LinkedHashMap<>();
        for (Descriptor descriptor : definitions) {
            validateDescriptor(descriptor);
            if (indexed.putIfAbsent(descriptor.identity(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate built-in release identity");
            }
        }
        Descriptor selected = indexed.get(defaultIdentity);
        if (selected == null || selected.releaseStatus() != ReleaseStatus.RELEASED) {
            throw new IllegalArgumentException("Default built-in release must be released");
        }
        releases = Map.copyOf(indexed);
        defaultRelease = selected;
    }

    /** Explicit release used only when creating a new campaign. */
    public Descriptor defaultRelease() {
        return defaultRelease;
    }

    /** Returns every application-approved release, excluding planned drafts. */
    public List<Descriptor> released() {
        return releases.values().stream()
                .filter(value -> value.releaseStatus() == ReleaseStatus.RELEASED)
                .toList();
    }

    public Optional<Descriptor> find(String moduleKey, String releaseVersion) {
        if (!validIdentity(moduleKey, releaseVersion)) {
            return Optional.empty();
        }
        return Optional.ofNullable(releases.get(new Identity(moduleKey, releaseVersion)));
    }

    public Resolution resolveReleased(String moduleKey, String releaseVersion) {
        if (!validIdentity(moduleKey, releaseVersion)) {
            return new Resolution(ResolutionStatus.INVALID_IDENTITY, null);
        }
        Descriptor descriptor = releases.get(new Identity(moduleKey, releaseVersion));
        if (descriptor == null) {
            return new Resolution(ResolutionStatus.UNKNOWN_RELEASE, null);
        }
        if (descriptor.releaseStatus() != ReleaseStatus.RELEASED) {
            return new Resolution(ResolutionStatus.UNPUBLISHED_RELEASE, null);
        }
        return new Resolution(ResolutionStatus.READY, descriptor);
    }

    private static boolean validIdentity(String moduleKey, String releaseVersion) {
        return moduleKey != null && releaseVersion != null
                && MODULE_KEY.matcher(moduleKey).matches()
                && RELEASE_VERSION.matcher(releaseVersion).matches();
    }

    private static void validateDescriptor(Descriptor descriptor) {
        if (descriptor == null
                || !validIdentity(
                        descriptor.identity().moduleKey(),
                        descriptor.identity().releaseVersion())
                || descriptor.canonicalFormatVersion() <= 0
                || descriptor.archiveFormatVersion() <= 0
                || !"SHA-256".equals(descriptor.hashAlgorithm())) {
            throw new IllegalArgumentException("Malformed built-in release descriptor");
        }
        boolean hasDigest = descriptor.contentSha256() != null
                && SHA256.matcher(descriptor.contentSha256()).matches();
        if ((descriptor.releaseStatus() == ReleaseStatus.RELEASED) != hasDigest) {
            throw new IllegalArgumentException("Published release digest is invalid");
        }
    }

    public record Identity(String moduleKey, String releaseVersion) {
        public Identity {
            Objects.requireNonNull(moduleKey, "module key");
            Objects.requireNonNull(releaseVersion, "release version");
        }
    }

    public record Descriptor(
            Identity identity,
            int canonicalFormatVersion,
            int archiveFormatVersion,
            String hashAlgorithm,
            String contentSha256,
            ReleaseStatus releaseStatus) {
        public Descriptor {
            Objects.requireNonNull(identity, "release identity");
            Objects.requireNonNull(hashAlgorithm, "hash algorithm");
            Objects.requireNonNull(releaseStatus, "release status");
        }

        public String moduleKey() {
            return identity.moduleKey();
        }

        public String releaseVersion() {
            return identity.releaseVersion();
        }
    }

    public record Resolution(ResolutionStatus status, Descriptor descriptor) {
        public Resolution {
            Objects.requireNonNull(status, "resolution status");
            if ((status == ResolutionStatus.READY) != (descriptor != null)) {
                throw new IllegalArgumentException("Release resolution does not match status");
            }
        }
    }

    public enum ReleaseStatus {
        DRAFT,
        RELEASED
    }

    public enum ResolutionStatus {
        READY,
        INVALID_IDENTITY,
        UNKNOWN_RELEASE,
        UNPUBLISHED_RELEASE
    }
}
