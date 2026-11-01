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
        fields.put("modules", List.of("srd51-vitality@1", "srd51-healing@1"));
        assertEquals("8af3624b50257e7c1e936ac8f7e13b5f47b0f16cf444652603b03df16b9b99b5",
                StorageRequestDigest.sha256("create", "9007199254740993", "0", fields));
        String digest = StorageRequestDigest.sha256("create", "9007199254740993", "0", fields);
        assertNotEquals(digest, StorageRequestDigest.sha256("create", "9007199254740992", "0", fields));
        assertNotEquals(digest, StorageRequestDigest.sha256("create", "9007199254740993", "1", fields));
        assertNotEquals(digest, StorageRequestDigest.sha256("open", "9007199254740993", "0", fields));
    }
}
