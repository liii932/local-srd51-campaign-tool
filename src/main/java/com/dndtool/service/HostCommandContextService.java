package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import com.dndtool.persistence.HostCommandContextRepository;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Verifies the active frozen release before returning server-only command ids. */
public final class HostCommandContextService {
    private final HostCommandContextRepository contextRepository;
    private final ModuleReleaseVerifier releaseVerifier;

    public HostCommandContextService(
            HostCommandContextRepository contextRepository,
            ModuleCatalogRepository moduleRepository) {
        this.contextRepository = Objects.requireNonNull(contextRepository, "contextRepository");
        releaseVerifier = new ModuleReleaseVerifier(
                Objects.requireNonNull(moduleRepository, "moduleRepository"),
                new ModuleContentHasher()::sha256,
                new BuiltinModuleHashManifest());
    }

    public Result loadActive() throws SQLException {
        Optional<HostCommandContextRepository.Context> found =
                contextRepository.findActive();
        if (found.isEmpty()) return new Result(Status.CAMPAIGN_NOT_FOUND, null);
        HostCommandContextRepository.Context stored = found.orElseThrow();
        if (!validStoredShape(stored)) return new Result(Status.INVALID_STATE, null);

        ModuleReleaseVerifier.Result verified = releaseVerifier.verify(
                stored.frozenModuleKey(), stored.frozenReleaseVersion());
        if (verified.status() == ModuleReleaseVerifier.Status.RELEASE_UNAVAILABLE) {
            return new Result(Status.MODULE_UNAVAILABLE, null);
        }
        if (verified.status() != ModuleReleaseVerifier.Status.READY
                || !ModuleReleaseVerifier.secureEquals(
                        verified.contentSha256(), stored.frozenContentSha256())
                || !ModuleReleaseVerifier.secureEquals(
                        verified.contentSha256(), stored.releaseContentSha256())) {
            return new Result(Status.MODULE_HASH_MISMATCH, null);
        }
        return new Result(
                Status.READY,
                new Context(
                        stored.campaignId(), stored.campaignKey(), stored.moduleReleaseId(),
                        verified.catalog(), verified.contentSha256()));
    }

    private static boolean validStoredShape(HostCommandContextRepository.Context value) {
        return value != null
                && value.campaignId() > 0
                && canonicalUuidV4(value.campaignKey())
                && value.moduleReleaseId() > 0
                && value.frozenModuleKey() != null
                && value.frozenReleaseVersion() != null
                && value.frozenModuleKey().equals(value.releaseModuleKey())
                && value.frozenReleaseVersion().equals(value.releaseVersion())
                && "RELEASED".equals(value.releaseStatus())
                && sha256(value.frozenContentSha256())
                && sha256(value.releaseContentSha256());
    }

    private static boolean canonicalUuidV4(String value) {
        if (value == null) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    public record Result(Status status, Context context) {
        public Result {
            Objects.requireNonNull(status, "status");
            if ((status == Status.READY) != (context != null)) {
                throw new IllegalArgumentException("Command context does not match status");
            }
        }
    }

    public record Context(
            long campaignId,
            String campaignKey,
            long moduleReleaseId,
            ModuleCatalog catalog,
            String contentSha256) {
        public Context {
            Objects.requireNonNull(campaignKey, "campaignKey");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(contentSha256, "contentSha256");
        }
    }

    public enum Status {
        READY,
        CAMPAIGN_NOT_FOUND,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        INVALID_STATE
    }
}
