package com.dndtool.module.composition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Production artifact bytes use shared JSON escaping, but an independent, explicit digest domain. */
final class ProductionSchema2Codec {
    static final String CANONICAL_PROFILE = "schema2-production-json-1";
    static final String COMPOSITION_ALGORITHM = "schema2-production-projection-1";
    private static final byte[] PREFIX = "local-dnd/schema2-production/1/".getBytes(StandardCharsets.UTF_8);

    private ProductionSchema2Codec() { }

    static byte[] canonical(Object value) { return Schema2ValidationCodec.canonical(value); }

    /** Exact canonical UTF-8 size without allocating canonical strings or byte arrays. */
    static long canonicalByteLength(Object value) {
        if (value instanceof java.util.Map<?, ?> object) {
            long size = 2 + Math.max(0, object.size() - 1);
            for (var entry : object.entrySet()) size += canonicalByteLength(entry.getKey()) + 1 + canonicalByteLength(entry.getValue());
            return size;
        }
        if (value instanceof java.util.List<?> array) {
            long size = 2 + Math.max(0, array.size() - 1);
            for (Object entry : array) size += canonicalByteLength(entry);
            return size;
        }
        if (value instanceof String text) {
            long size = 2;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\\' || c == '\b' || c == '\t' || c == '\n' || c == '\f' || c == '\r') size += 2;
                else if (c < 0x20) size += 6;
                else if (c < 0x80) size++;
                else if (c < 0x800) size += 2;
                else if (Character.isHighSurrogate(c)) {
                    if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("Invalid canonical scalar");
                    size += 4;
                } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid canonical scalar");
                else size += 3;
            }
            return size;
        }
        if (value instanceof Integer integer) return integer.toString().length();
        if (value instanceof Boolean bool) return bool ? 4 : 5;
        throw new IllegalArgumentException("Unsupported canonical value");
    }

    static String digest(String domain, Object value) {
        if (!java.util.Set.of("module-logic", "composition-config", "final-rules", "full-chain").contains(domain)) {
            throw new IllegalArgumentException("Unsupported production digest domain");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PREFIX);
            digest.update(domain.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(canonical(value)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
