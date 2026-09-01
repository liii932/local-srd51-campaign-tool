package com.dndtool.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Computes the domain-separated digest for a character lifecycle command. */
public final class CharacterLifecycleRequestDigest {
    private static final byte[] DOMAIN =
            "DND_TOOL_SE_MUTATE_CHARACTER_LIFECYCLE_V1"
                    .getBytes(StandardCharsets.US_ASCII);

    private CharacterLifecycleRequestDigest() {
    }

    public static String sha256(
            String characterKey, long expectedRowVersion, String action, String value) {
        byte[][] fields = {
                DOMAIN,
                utf8(characterKey),
                utf8(Long.toString(expectedRowVersion)),
                utf8(action),
                utf8(value == null ? "" : value)
        };
        int size = 0;
        for (byte[] field : fields) {
            size = Math.addExact(size, Math.addExact(4, field.length));
        }
        ByteBuffer payload = ByteBuffer.allocate(size);
        for (byte[] field : fields) {
            payload.putInt(field.length).put(field);
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.array()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
