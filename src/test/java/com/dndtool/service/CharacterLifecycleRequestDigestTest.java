package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

final class CharacterLifecycleRequestDigestTest {
    private static final String KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    @Test
    void digestIsStableAndSeparatesVersionActionAndValue() {
        String digest = CharacterLifecycleRequestDigest.sha256(KEY, 7, "RENAME", "Aria");

        assertEquals(digest,
                CharacterLifecycleRequestDigest.sha256(KEY, 7, "RENAME", "Aria"));
        assertNotEquals(digest,
                CharacterLifecycleRequestDigest.sha256(KEY, 8, "RENAME", "Aria"));
        assertNotEquals(digest,
                CharacterLifecycleRequestDigest.sha256(KEY, 7, "CHANGE_TYPE", "Aria"));
        assertNotEquals(digest,
                CharacterLifecycleRequestDigest.sha256(KEY, 7, "RENAME", "aria"));
    }

    @Test
    void nullAndEmptyValueHaveTheSameCanonicalEncoding() {
        assertEquals(
                CharacterLifecycleRequestDigest.sha256(KEY, 0, "ARCHIVE", null),
                CharacterLifecycleRequestDigest.sha256(KEY, 0, "ARCHIVE", ""));
    }
}
