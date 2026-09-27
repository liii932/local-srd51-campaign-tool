package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuiltinModuleReleaseRegistryTest {
    private static final String APPROVED_SHA256 =
            "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e";
    private final BuiltinModuleReleaseRegistry registry =
            new BuiltinModuleReleaseRegistry();

    @Test
    void explicitlySelectsLegacyV1AsTheOnlyReleasedDefault() {
        var selected = registry.defaultRelease();

        assertEquals(BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, selected.moduleKey());
        assertEquals("1", selected.releaseVersion());
        assertEquals(1, selected.canonicalFormatVersion());
        assertEquals(1, selected.archiveFormatVersion());
        assertEquals("SHA-256", selected.hashAlgorithm());
        assertEquals(APPROVED_SHA256, selected.contentSha256());
        assertEquals(BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED,
                selected.releaseStatus());
        assertEquals(List.of(selected), registry.released());
        assertEquals(selected, registry.resolveReleased(selected.moduleKey(), "1").descriptor());
    }

    @Test
    void reservesTheApprovedCompleteFamilyMappingButKeepsItUnpublished() {
        var planned = registry.find(
                BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY, "1").orElseThrow();

        assertEquals(2, planned.canonicalFormatVersion());
        assertEquals(2, planned.archiveFormatVersion());
        assertEquals(BuiltinModuleReleaseRegistry.ReleaseStatus.DRAFT,
                planned.releaseStatus());
        assertNull(planned.contentSha256());
        assertNull(registry.resolveReleased(planned.moduleKey(), "1").descriptor());
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
    }

    @Test
    void rejectsMalformedKeysWithoutRepairingThem() {
        for (String key : List.of("", "INVALID", " module.valid", "module.valid ",
                "module.valid\u00a0", "ｍodule.valid", "module.é", "module.e\u0301",
                "module.\ud800", "module.😀", "module.\u0000", "module.\u001f",
                "module.\u007f", "module.\u0085", ".module", "module.", "module..key",
                "module.1", "module-key", "a".repeat(129))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new BuiltinModuleReleaseRegistry.Identity(key, "1"), key);
            assertInvalidLookup(key, "1");
        }
        assertThrows(NullPointerException.class,
                () -> new BuiltinModuleReleaseRegistry.Identity(null, "1"));
        assertInvalidLookup(null, "1");
        assertStandardApprovalUnchanged();
    }

    @Test
    void rejectsMalformedReleaseVersionsWithoutRepairingThem() {
        for (String version : List.of("", " 1", "1 ", "1\u00a0", "１", "é", "e\u0301",
                "\ud800", "😀", "1\u0000", "1\u001f", "1\u007f", "1\u0085", "_1",
                ".1", "-1", "v+1", "a".repeat(65))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new BuiltinModuleReleaseRegistry.Identity("module.valid", version), version);
            assertInvalidLookup(BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, version);
        }
        assertThrows(NullPointerException.class,
                () -> new BuiltinModuleReleaseRegistry.Identity("module.valid", null));
        assertInvalidLookup(BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, null);
        assertStandardApprovalUnchanged();
    }

    @Test
    void acceptsAsciiBoundariesButDoesNotRegisterThem() {
        for (String key : List.of("a", "a".repeat(128), "module.a_0.b1")) {
            for (String version : List.of("1", "a".repeat(64), "A0._-")) {
                var identity = new BuiltinModuleReleaseRegistry.Identity(key, version);
                assertEquals(key, identity.moduleKey());
                assertEquals(version, identity.releaseVersion());
                assertUnknownLookup(key, version);
            }
        }
        assertStandardApprovalUnchanged();
    }

    @Test
    void comparesReleaseStringsExactlyInsteadOfNumericallyOrByCase() {
        var identities = List.of("1", "01", "1.0", "vA", "va").stream()
                .map(version -> new BuiltinModuleReleaseRegistry.Identity(
                        BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, version))
                .toList();
        assertEquals(5, Set.copyOf(identities).size());
        assertEquals(registry.defaultRelease().identity(), identities.getFirst());
        for (var identity : identities.subList(1, identities.size())) {
            assertUnknownLookup(identity.moduleKey(), identity.releaseVersion());
        }
        assertStandardApprovalUnchanged();
    }

    @Test
    void requiresPositiveFormatIntegersWithoutClaimingFormatSupport() {
        for (int invalid : new int[] {Integer.MIN_VALUE, -1, 0}) {
            assertThrows(IllegalArgumentException.class,
                    () -> descriptor(invalid, 1, "SHA-256", APPROVED_SHA256,
                            BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED));
            assertThrows(IllegalArgumentException.class,
                    () -> descriptor(1, invalid, "SHA-256", APPROVED_SHA256,
                            BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED));
        }
        for (int positive : new int[] {1, 2, 3, Integer.MAX_VALUE}) {
            var structural = descriptor(positive, positive, "SHA-256", APPROVED_SHA256,
                    BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED);
            assertEquals(positive, structural.canonicalFormatVersion());
            assertEquals(positive, structural.archiveFormatVersion());
            if (positive != 1) {
                assertNotEquals(registry.defaultRelease(), structural);
            }
        }
        assertStandardApprovalUnchanged();
    }

    @Test
    void requiresExactHashAlgorithmAndReleasedDigestLexicalForm() {
        for (String algorithm : List.of("", "sha256", "SHA256", "sha-256", "SHA-256 ", "SHA-512")) {
            assertThrows(IllegalArgumentException.class,
                    () -> descriptor(1, 1, algorithm, APPROVED_SHA256,
                            BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED));
        }
        for (String digest : Arrays.asList(null, "", "a".repeat(63), "a".repeat(65),
                "A".repeat(64), "g".repeat(64), "０".repeat(64), " " + "a".repeat(63))) {
            assertThrows(IllegalArgumentException.class,
                    () -> descriptor(1, 1, "SHA-256", digest,
                            BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED));
        }
        String differentDigest = "0" + APPROVED_SHA256.substring(1);
        assertNotEquals(APPROVED_SHA256, differentDigest);
        assertEquals(differentDigest, descriptor(1, 1, "SHA-256", differentDigest,
                BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED).contentSha256());
        assertStandardApprovalUnchanged();
    }

    @Test
    void draftApprovalHasNoDigestEvenWhenANonNullDigestIsMalformed() {
        assertNull(descriptor(2, 2, "SHA-256", null,
                BuiltinModuleReleaseRegistry.ReleaseStatus.DRAFT).contentSha256());
        for (String digest : List.of("", "malformed", "a".repeat(63), "A".repeat(64),
                APPROVED_SHA256)) {
            assertThrows(IllegalArgumentException.class,
                    () -> descriptor(2, 2, "SHA-256", digest,
                            BuiltinModuleReleaseRegistry.ReleaseStatus.DRAFT));
        }
        assertStandardApprovalUnchanged();
    }

    @Test
    void constructionRejectsNullContractsAndMissingOrDraftDefaults() {
        var legacy = registry.defaultRelease();
        var draft = registry.find(BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY, "1").orElseThrow();
        assertThrows(NullPointerException.class, () -> new BuiltinModuleReleaseRegistry.Descriptor(
                null, 1, 1, "SHA-256", APPROVED_SHA256,
                BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED));
        assertThrows(NullPointerException.class, () -> descriptor(1, 1, null, APPROVED_SHA256,
                BuiltinModuleReleaseRegistry.ReleaseStatus.RELEASED));
        assertThrows(NullPointerException.class,
                () -> descriptor(1, 1, "SHA-256", APPROVED_SHA256, null));
        assertThrows(NullPointerException.class,
                () -> new BuiltinModuleReleaseRegistry(null, legacy.identity()));
        assertThrows(NullPointerException.class,
                () -> new BuiltinModuleReleaseRegistry(List.of(legacy), null));
        assertThrows(IllegalArgumentException.class,
                () -> new BuiltinModuleReleaseRegistry(Arrays.asList(legacy, null), legacy.identity()));
        assertThrows(IllegalArgumentException.class,
                () -> new BuiltinModuleReleaseRegistry(List.of(), legacy.identity()));
        assertThrows(IllegalArgumentException.class,
                () -> new BuiltinModuleReleaseRegistry(List.of(legacy), draft.identity()));
        assertThrows(IllegalArgumentException.class,
                () -> new BuiltinModuleReleaseRegistry(List.of(legacy, draft), draft.identity()));
        assertStandardApprovalUnchanged();
    }

    private void assertInvalidLookup(String key, String version) {
        assertTrue(registry.find(key, version).isEmpty());
        var result = registry.resolveReleased(key, version);
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.INVALID_IDENTITY, result.status());
        assertNull(result.descriptor());
    }

    private void assertUnknownLookup(String key, String version) {
        assertTrue(registry.find(key, version).isEmpty());
        var result = registry.resolveReleased(key, version);
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.UNKNOWN_RELEASE, result.status());
        assertNull(result.descriptor());
    }

    private void assertStandardApprovalUnchanged() {
        assertEquals(APPROVED_SHA256, registry.defaultRelease().contentSha256());
        assertEquals(BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, registry.defaultRelease().moduleKey());
        assertEquals("1", registry.defaultRelease().releaseVersion());
        assertEquals(1, registry.defaultRelease().canonicalFormatVersion());
        assertEquals(1, registry.defaultRelease().archiveFormatVersion());
        assertEquals(List.of(registry.defaultRelease()), registry.released());
        assertEquals(BuiltinModuleReleaseRegistry.ResolutionStatus.UNPUBLISHED_RELEASE,
                registry.resolveReleased(BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY, "1").status());
    }

    private BuiltinModuleReleaseRegistry.Descriptor descriptor(
            int canonical, int archive, String algorithm, String digest,
            BuiltinModuleReleaseRegistry.ReleaseStatus status) {
        return new BuiltinModuleReleaseRegistry.Descriptor(
                registry.defaultRelease().identity(), canonical, archive, algorithm, digest, status);
    }
}
