package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CharacterNamePolicyTest {
    @Test
    void trimsThenNormalizesEquivalentUnicodeToNfc() {
        assertEquals("Caf\u00e9", CharacterNamePolicy.normalize(" \tCafe\u0301　"));
        assertEquals("Caf\u00e9", CharacterNamePolicy.normalize("Caf\u00e9"));
    }

    @Test
    void countsUnicodeCodePointsInsteadOfUtf16CodeUnits() {
        String eightySupplementaryCharacters = "\ud83d\udc09".repeat(80);

        assertEquals(
                eightySupplementaryCharacters,
                CharacterNamePolicy.normalize(eightySupplementaryCharacters));
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNamePolicy.normalize("\ud83d\udc09".repeat(81)));
    }

    @Test
    void rejectsMissingBlankAndControlCharacterNames() {
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize("　 \t"));
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize("Mage\nOne"));
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize("Mage\u0085One"));
    }

}
