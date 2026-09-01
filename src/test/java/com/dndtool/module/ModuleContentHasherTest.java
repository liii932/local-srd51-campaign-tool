package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModuleContentHasherTest {
    private static final String EMPTY_VECTOR_SHA256 =
            "db0859175cd7a9c57763febeaacaa9079aba4876636f94db41dbb0dae393c0cc";

    private final ModuleContentHasher hasher = new ModuleContentHasher();

    @Test
    void messageDigestMatchesTheIndependentEmptyContainerDigest() throws Exception {
        String digest = hasher.sha256(catalog(defaultRelease(), List.of()));

        assertEquals(EMPTY_VECTOR_SHA256, digest);
        assertEquals(64, digest.length());
        assertEquals(digest.toLowerCase(java.util.Locale.ROOT), digest);
    }

    @Test
    void anyCanonicalBusinessValueChangeChangesTheDigest() throws Exception {
        ModuleCatalog first = catalog(defaultRelease(), List.of(
                new ModuleCatalog.RuleConstant(
                        "test.value", "INTEGER", new ModuleCatalog.IntegerValue(1))));
        ModuleCatalog changed = catalog(defaultRelease(), List.of(
                new ModuleCatalog.RuleConstant(
                        "test.value", "INTEGER", new ModuleCatalog.IntegerValue(2))));

        assertNotEquals(hasher.sha256(first), hasher.sha256(changed));
    }

    @Test
    void unknownCanonicalFormatNeverFallsBackToARegisteredEncoder() {
        ModuleCatalog.Release unsupported = new ModuleCatalog.Release(
                BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY,
                "1", 3, "SHA-256", null, "DRAFT");

        assertThrows(ModuleCanonicalException.class,
                () -> hasher.sha256(catalog(unsupported, List.of())));
    }

    @Test
    void builtInManifestContainsOnlyTheReviewedV003IdentityAndDigest() {
        BuiltinModuleHashManifest manifest = new BuiltinModuleHashManifest();

        assertEquals(
                Optional.of("8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e"),
                manifest.expectedSha256(defaultRelease()));
        assertEquals(Optional.empty(), manifest.expectedSha256(new ModuleCatalog.Release(
                "dnd5e2014_srd51_se_v1", "2", 1, "SHA-256", null, "DRAFT")));
        assertEquals(Optional.empty(), manifest.expectedSha256(null));
    }

    @Test
    void verifierDistinguishesMatchMismatchAndUnsupportedRelease() throws Exception {
        ModuleCatalog empty = catalog(defaultRelease(), List.of());
        ModuleHashVerifier matching = new ModuleHashVerifier(
                hasher, release -> Optional.of(EMPTY_VECTOR_SHA256));
        ModuleHashVerifier mismatching = new ModuleHashVerifier(
                hasher, release -> Optional.of("0".repeat(64)));
        ModuleHashVerifier unsupported = new ModuleHashVerifier(
                hasher, release -> Optional.empty());

        assertEquals(ModuleHashVerifier.Status.MATCH, matching.verify(empty));
        assertEquals(ModuleHashVerifier.Status.HASH_MISMATCH, mismatching.verify(empty));
        assertEquals(ModuleHashVerifier.Status.UNSUPPORTED_RELEASE, unsupported.verify(empty));
    }

    @Test
    void malformedApplicationManifestFailsClosed() {
        ModuleHashVerifier verifier = new ModuleHashVerifier(
                hasher, release -> Optional.of("NOT_A_SHA256"));

        assertThrows(
                IllegalStateException.class,
                () -> verifier.verify(catalog(defaultRelease(), List.of())));
    }

    private static ModuleCatalog.Release defaultRelease() {
        return new ModuleCatalog.Release(
                "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", null, "DRAFT");
    }

    private static ModuleCatalog catalog(
            ModuleCatalog.Release release,
            List<ModuleCatalog.RuleConstant> ruleConstants) {
        return new ModuleCatalog(
                release,
                ruleConstants,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
