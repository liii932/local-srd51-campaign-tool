package com.dndtool.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageRequestDigestTest {
    @Test void unicodeAndEpochOutsideJavascriptIntegerPrecisionUseExactUtf8Fields() {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("name", List.of("营地 e\u0301 🧙"));
        String base = "test_base@1@" + "a".repeat(64) + "@" + "b".repeat(64) + "@BASE";
        String extension = "test_extension@2@" + "c".repeat(64) + "@" + "d".repeat(64) + "@EXTENSION";
        fields.put("members", List.of(base, extension));
        assertEquals("fa9009bdf328f6cf90c6c6b8b999828f671ce1cbd413a6f2ed609941ad5ff256",
                StorageRequestDigest.sha256("create", "9007199254740993", "0", fields));
        String digest = StorageRequestDigest.sha256("create", "9007199254740993", "0", fields);
        assertNotEquals(digest, StorageRequestDigest.sha256("create", "9007199254740992", "0", fields));
        assertNotEquals(digest, StorageRequestDigest.sha256("create", "9007199254740993", "1", fields));
        assertNotEquals(digest, StorageRequestDigest.sha256("open", "9007199254740993", "0", fields));
        fields.put("members", List.of(extension, base));
        assertNotEquals(digest, StorageRequestDigest.sha256("create", "9007199254740993", "0", fields));
    }
}
