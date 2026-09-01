package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Computes the lowercase SHA-256 of one canonical module projection. */
public final class ModuleContentHasher {
    private final ModuleCanonicalEncoderRegistry encoders;

    public ModuleContentHasher() {
        this(new ModuleCanonicalEncoderRegistry());
    }

    ModuleContentHasher(ModuleCanonicalEncoderRegistry encoders) {
        this.encoders = Objects.requireNonNull(encoders);
    }

    /** Returns exactly 64 lowercase ASCII hexadecimal characters. */
    public String sha256(ModuleCatalog catalog) throws ModuleCanonicalException {
        byte[] canonical = encoders.encode(catalog);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory in every Java 21 runtime supported by the project.
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
