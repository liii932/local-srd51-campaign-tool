package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.module.ModuleHashManifest;
import com.dndtool.persistence.CampaignCreationRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Validates and creates one campaign bound to the reviewed built-in release. */
public final class CampaignCreationService {
    public static final String MODULE_KEY = BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY;
    public static final String RELEASE_VERSION = BuiltinModuleReleaseRegistry.RELEASE_VERSION_1;

    private final ModuleCatalogRepository moduleRepository;
    private final CampaignCreationRepository campaignRepository;
    private final DigestComputer hasher;
    private final ModuleHashManifest manifest;
    private final CampaignCreationIdentityFactory identityFactory;
    private final BuiltinModuleReleaseRegistry releaseRegistry;

    public CampaignCreationService(
            ModuleCatalogRepository moduleRepository,
            CampaignCreationRepository campaignRepository) {
        this(moduleRepository, campaignRepository, new ModuleContentHasher()::sha256,
                new BuiltinModuleHashManifest(), new CampaignCreationIdentityFactory(),
                new BuiltinModuleReleaseRegistry());
    }

    CampaignCreationService(
            ModuleCatalogRepository moduleRepository,
            CampaignCreationRepository campaignRepository,
            DigestComputer hasher,
            ModuleHashManifest manifest,
            CampaignCreationIdentityFactory identityFactory) {
        this(moduleRepository, campaignRepository, hasher, manifest, identityFactory,
                new BuiltinModuleReleaseRegistry());
    }

    CampaignCreationService(
            ModuleCatalogRepository moduleRepository,
            CampaignCreationRepository campaignRepository,
            DigestComputer hasher,
            ModuleHashManifest manifest,
            CampaignCreationIdentityFactory identityFactory,
            BuiltinModuleReleaseRegistry releaseRegistry) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository);
        this.campaignRepository = Objects.requireNonNull(campaignRepository);
        this.hasher = Objects.requireNonNull(hasher);
        this.manifest = Objects.requireNonNull(manifest);
        this.identityFactory = Objects.requireNonNull(identityFactory);
        this.releaseRegistry = Objects.requireNonNull(releaseRegistry);
    }

    public Result create(String campaignName, String requestId, String requestDigestSha256)
            throws SQLException {
        final String normalizedName;
        try {
            normalizedName = normalizeName(campaignName);
        } catch (IllegalArgumentException exception) {
            return new Result(Status.INVALID_REQUEST, null);
        }
        if (!isCanonicalUuid(requestId) || !isSha256(requestDigestSha256)) {
            return new Result(Status.INVALID_REQUEST, null);
        }
        String actualRequestDigest = CampaignCreationRequestDigest.sha256(normalizedName);
        if (!secureEquals(actualRequestDigest, requestDigestSha256)) {
            return new Result(Status.INVALID_REQUEST, null);
        }

        BuiltinModuleReleaseRegistry.Descriptor selected = releaseRegistry.defaultRelease();
        Optional<ModuleCatalog> found = moduleRepository.findByIdentity(
                selected.moduleKey(), selected.releaseVersion());
        if (found.isEmpty()) {
            return new Result(Status.RELEASE_UNAVAILABLE, null);
        }
        ModuleCatalog catalog = found.orElseThrow();
        ModuleCatalog.Release release = catalog.release();
        if (release == null
                || !selected.moduleKey().equals(release.moduleKey())
                || !selected.releaseVersion().equals(release.releaseVersion())
                || selected.canonicalFormatVersion() != release.canonicalFormatVersion()
                || !selected.hashAlgorithm().equals(release.hashAlgorithm())) {
            return new Result(Status.MODULE_HASH_MISMATCH, null);
        }
        if (!"RELEASED".equals(release.releaseStatus()) || release.contentSha256() == null) {
            return new Result(Status.RELEASE_UNAVAILABLE, null);
        }

        String expected = manifest.expectedSha256(release).orElse(null);
        final String actual;
        try {
            actual = hasher.sha256(catalog);
        } catch (ModuleCanonicalException exception) {
            return new Result(Status.MODULE_HASH_MISMATCH, null);
        }
        if (expected == null
                || !secureEquals(expected, release.contentSha256())
                || !secureEquals(expected, actual)) {
            return new Result(Status.MODULE_HASH_MISMATCH, null);
        }

        // Identity is allocated only after the complete request and frozen release validate.
        String campaignKey = identityFactory.newCampaignKey();
        CampaignCreationRepository.Result persisted = campaignRepository.create(
                new CampaignCreationRepository.Command(
                        requestId,
                        requestDigestSha256,
                        campaignKey,
                        normalizedName,
                        release.moduleKey(),
                        release.releaseVersion(),
                        expected));
        return switch (persisted.status()) {
            case CREATED -> new Result(Status.CREATED, persisted.campaignKey());
            case ALREADY_SUCCEEDED ->
                    new Result(Status.ALREADY_SUCCEEDED, persisted.campaignKey());
            case IDEMPOTENCY_CONFLICT -> new Result(Status.IDEMPOTENCY_CONFLICT, null);
            case ACTIVE_CAMPAIGN_EXISTS -> new Result(Status.ACTIVE_CAMPAIGN_EXISTS, null);
            case RELEASE_UNAVAILABLE -> new Result(Status.RELEASE_UNAVAILABLE, null);
        };
    }

    static String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Campaign name is required");
        }
        String normalized = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
        int codePoints = normalized.codePointCount(0, normalized.length());
        boolean hasControl = normalized.codePoints().anyMatch(Character::isISOControl);
        if (codePoints < 1 || codePoints > 80 || hasControl) {
            throw new IllegalArgumentException("Invalid campaign name");
        }
        return normalized;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null) {
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

    public record Result(Status status, String campaignKey) {
    }

    public enum Status {
        CREATED,
        ALREADY_SUCCEEDED,
        INVALID_REQUEST,
        IDEMPOTENCY_CONFLICT,
        ACTIVE_CAMPAIGN_EXISTS,
        RELEASE_UNAVAILABLE,
        MODULE_HASH_MISMATCH
    }

    @FunctionalInterface
    interface DigestComputer {
        String sha256(ModuleCatalog catalog) throws ModuleCanonicalException;
    }
}
