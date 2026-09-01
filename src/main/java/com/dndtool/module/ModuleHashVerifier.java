package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Compares a computed canonical digest with an independently approved manifest entry. */
public final class ModuleHashVerifier {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final ModuleContentHasher hasher;
    private final ModuleHashManifest manifest;

    public ModuleHashVerifier(ModuleContentHasher hasher, ModuleHashManifest manifest) {
        this.hasher = Objects.requireNonNull(hasher);
        this.manifest = Objects.requireNonNull(manifest);
    }

    /**
     * Returns only a stable category. Callers must not turn a mismatch into a warning or expose
     * canonical bytes and database values through an HTTP error response.
     */
    public Status verify(ModuleCatalog catalog) throws ModuleCanonicalException {
        if (catalog == null) {
            throw new ModuleCanonicalException();
        }
        Optional<String> expected = manifest.expectedSha256(catalog.release());
        if (expected.isEmpty()) {
            return Status.UNSUPPORTED_RELEASE;
        }
        String expectedSha256 = expected.orElseThrow();
        if (!SHA256.matcher(expectedSha256).matches()) {
            throw new IllegalStateException("Invalid application module hash manifest");
        }

        String actualSha256 = hasher.sha256(catalog);
        boolean matches = MessageDigest.isEqual(
                expectedSha256.getBytes(StandardCharsets.US_ASCII),
                actualSha256.getBytes(StandardCharsets.US_ASCII));
        return matches ? Status.MATCH : Status.HASH_MISMATCH;
    }

    public enum Status {
        MATCH,
        HASH_MISMATCH,
        UNSUPPORTED_RELEASE
    }
}
