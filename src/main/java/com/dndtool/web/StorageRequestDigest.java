package com.dndtool.web;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Binds an HTTP command to its action, optimistic versions and exact submitted field values. */
public final class StorageRequestDigest {
    private StorageRequestDigest() { }

    public static String sha256(String action, String epoch, String revision,
            Map<String, List<String>> fields) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            string(out, "DND_STORAGE_HTTP_V1");
            string(out, action);
            string(out, epoch);
            string(out, revision);
            out.writeInt(fields.size());
            for (var field : new TreeMap<>(fields).entrySet()) {
                string(out, field.getKey());
                out.writeInt(field.getValue().size());
                for (String value : field.getValue()) string(out, value);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("STORAGE_DIGEST_UNAVAILABLE");
        }
    }

    private static void string(DataOutputStream out, String value) throws java.io.IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
