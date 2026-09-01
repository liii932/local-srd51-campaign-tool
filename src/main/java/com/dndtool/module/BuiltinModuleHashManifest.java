package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.util.Optional;

/** Immutable application manifest of built-in releases approved for canonical comparison. */
public final class BuiltinModuleHashManifest implements ModuleHashManifest {
    public static final String DND5E2014_SRD51_SE_V1_SHA256 =
            BuiltinModuleReleaseRegistry.LEGACY_CONTENT_SHA256;

    private final BuiltinModuleReleaseRegistry registry;

    public BuiltinModuleHashManifest() {
        this(new BuiltinModuleReleaseRegistry());
    }

    BuiltinModuleHashManifest(BuiltinModuleReleaseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Optional<String> expectedSha256(ModuleCatalog.Release release) {
        if (release == null) {
            return Optional.empty();
        }
        BuiltinModuleReleaseRegistry.Resolution resolved = registry.resolveReleased(
                release.moduleKey(), release.releaseVersion());
        if (resolved.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY) {
            return Optional.empty();
        }
        BuiltinModuleReleaseRegistry.Descriptor descriptor = resolved.descriptor();
        if (descriptor.canonicalFormatVersion() != release.canonicalFormatVersion()
                || !descriptor.hashAlgorithm().equals(release.hashAlgorithm())) {
            return Optional.empty();
        }
        return Optional.of(descriptor.contentSha256());
    }
}
