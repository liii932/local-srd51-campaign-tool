package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuiltinModuleReleaseRegistryTest {
    private final BuiltinModuleReleaseRegistry registry =
            new BuiltinModuleReleaseRegistry();

    @Test
    void explicitlySelectsLegacyV1AsTheOnlyReleasedDefault() {
        var selected = registry.defaultRelease();

        assertEquals(BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, selected.moduleKey());
        assertEquals("1", selected.releaseVersion());
        assertEquals(1, selected.canonicalFormatVersion());
        assertEquals(1, selected.archiveFormatVersion());
        assertEquals(List.of(selected), registry.released());
    }

    @Test
    void reservesTheApprovedCompleteFamilyMappingButKeepsItUnpublished() {
        var planned = registry.find(
                BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY, "1").orElseThrow();

        assertEquals(2, planned.canonicalFormatVersion());
        assertEquals(2, planned.archiveFormatVersion());
        assertEquals(BuiltinModuleReleaseRegistry.ReleaseStatus.DRAFT,
                planned.releaseStatus());
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.UNPUBLISHED_RELEASE,
                registry.resolveReleased(planned.moduleKey(), planned.releaseVersion()).status());
    }

    @Test
    void malformedUnknownAndDuplicateIdentitiesFailClosed() {
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.INVALID_IDENTITY,
                registry.resolveReleased("INVALID", "1").status());
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.UNKNOWN_RELEASE,
                registry.resolveReleased("module.unknown", "1").status());

        var legacy = registry.defaultRelease();
        assertThrows(IllegalArgumentException.class, () ->
                new BuiltinModuleReleaseRegistry(
                        List.of(legacy, legacy), legacy.identity()));
        var malformed = new BuiltinModuleReleaseRegistry.Descriptor(
                new BuiltinModuleReleaseRegistry.Identity("module.valid", "1"),
                1, 1, "SHA-256", null,
                BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED);
        assertThrows(IllegalArgumentException.class, () ->
                new BuiltinModuleReleaseRegistry(
                        List.of(malformed), malformed.identity()));
    }
}
