package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.ModuleCatalog;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuiltinModuleHashManifestTest {
    private static final String APPROVED_SHA256 =
            "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e";
    private final BuiltinModuleHashManifest manifest = new BuiltinModuleHashManifest();

    @Test
    void approvedDigestIsIndependentOfSourceClaimsAndDoesNotVerifyThem() {
        String differentDigest = "0" + APPROVED_SHA256.substring(1);
        assertNotEquals(APPROVED_SHA256, differentDigest);
        for (String digest : Arrays.asList(APPROVED_SHA256, differentDigest, null, "",
                "A".repeat(64), "a".repeat(63))) {
            for (String status : Arrays.asList("RELEASED", "DRAFT", "UNKNOWN", null)) {
                assertEquals(Optional.of(APPROVED_SHA256), manifest.expectedSha256(release(
                        BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, "1", 1, "SHA-256",
                        digest, status)));
            }
        }
        assertStandardApprovalUnchanged();
    }

    @Test
    void onlyExactRegisteredCanonicalFormatAndHashAlgorithmHaveAnExpectedDigest() {
        for (int format : new int[] {Integer.MIN_VALUE, -1, 0, 2, 3, Integer.MAX_VALUE}) {
            assertTrue(manifest.expectedSha256(release(
                    BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, "1", format, "SHA-256",
                    APPROVED_SHA256, "RELEASED")).isEmpty());
        }
        for (String algorithm : Arrays.asList(null, "", "sha256", "SHA256", "sha-256",
                "SHA-256 ", "SHA-512")) {
            assertTrue(manifest.expectedSha256(release(
                    BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, "1", 1, algorithm,
                    APPROVED_SHA256, "RELEASED")).isEmpty());
        }
        assertStandardApprovalUnchanged();
    }

    @Test
    void sourceClaimsCannotApproveUnknownOrDraftIdentities() {
        for (String key : Arrays.asList(null, "", "INVALID", "a".repeat(129),
                "module.unknown", BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY)) {
            for (int format : new int[] {1, 2}) {
                assertTrue(manifest.expectedSha256(release(key, "1", format, "SHA-256",
                        APPROVED_SHA256, "RELEASED")).isEmpty());
            }
        }
        for (String version : Arrays.asList(null, "", " 1", "1 ", "a".repeat(65),
                "01", "1.0", "vA", "va", "999")) {
            assertTrue(manifest.expectedSha256(release(
                    BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, version, 1, "SHA-256",
                    APPROVED_SHA256, "RELEASED")).isEmpty());
        }
        assertTrue(manifest.expectedSha256(null).isEmpty());
        assertStandardApprovalUnchanged();
    }

    private void assertStandardApprovalUnchanged() {
        var registry = new BuiltinModuleReleaseRegistry();
        assertEquals(APPROVED_SHA256, registry.defaultRelease().contentSha256());
        assertEquals(BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, registry.defaultRelease().moduleKey());
        assertEquals(List.of(registry.defaultRelease()), registry.released());
        assertEquals(Optional.of(APPROVED_SHA256), manifest.expectedSha256(release(
                BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, "1", 1, "SHA-256",
                APPROVED_SHA256, "RELEASED")));
    }

    private static ModuleCatalog.Release release(String key, String version, int canonical,
            String algorithm, String digest, String status) {
        return new ModuleCatalog.Release(key, version, canonical, algorithm, digest, status);
    }
}
