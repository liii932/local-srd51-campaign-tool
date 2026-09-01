package com.dndtool.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Computes the canonical lower-case digest that binds preview and later confirmation bytes. */
public final class CampaignArchiveDigest {
    private CampaignArchiveDigest() {
    }

    public static String sha256(byte[] content) {
        Objects.requireNonNull(content, "archive content");
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
