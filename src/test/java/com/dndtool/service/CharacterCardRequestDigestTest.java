package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

final class CharacterCardRequestDigestTest {
    @Test
    void matchesIndependentLengthPrefixedVectorAndBindsEveryField() {
        String digest = CharacterCardRequestDigest.sha256(
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                7,
                "SET_FIELD",
                "ability.strength",
                "15",
                "",
                "");

        assertEquals(
                "98488e4401940c1667b79ed5ce691b39692eab098b360acbe10cc9967f8a764f",
                digest);
        assertNotEquals(digest, CharacterCardRequestDigest.sha256(
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                8,
                "SET_FIELD",
                "ability.strength",
                "15",
                "",
                ""));
    }
}
