package com.dndtool.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Computes the domain-separated digest used by character-creation idempotency. */
public final class CharacterCreationRequestDigest {
    private static final byte[] DOMAIN =
            "DND_TOOL_SE_CREATE_CHARACTER_V1".getBytes(StandardCharsets.US_ASCII);

    private CharacterCreationRequestDigest() {
    }

    public static String sha256(
            String campaignKey, String characterType, String characterName, String templateKey) {
        byte[][] fields = {
                DOMAIN,
                utf8(campaignKey),
                utf8(characterType),
                utf8(characterName),
                utf8(templateKey == null ? "" : templateKey)
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
