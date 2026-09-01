package com.dndtool.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Canonical digest input for one DM direct-position command. */
public final class EntityPositionRequestDigest {
    private EntityPositionRequestDigest() {
    }

    public static String canonicalPayloadSha256(
            long campaignId,
            long moduleReleaseId,
            String characterKey,
            String mapKey,
            String nodeKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] field : List.of(
                    utf8("SET_STAGE3_ENTITY_POSITION"),
                    longBytes(campaignId),
                    longBytes(moduleReleaseId),
                    utf8(characterKey),
                    utf8(mapKey),
                    utf8(nodeKey))) {
                digest.update(intBytes(field.length));
                digest.update(field);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Stable HTTP form that deliberately excludes database-only ids. */
    public static String canonicalPayloadSha256(
            String campaignKey,
            String characterKey,
            String mapKey,
            String nodeKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] field : List.of(
                    utf8("SET_STAGE3_ENTITY_POSITION_V2"),
                    utf8(campaignKey),
                    utf8(characterKey),
                    utf8(mapKey),
                    utf8(nodeKey))) {
                digest.update(intBytes(field.length));
                digest.update(field);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }
}
