package com.dndtool.service;

import java.text.Normalizer;

/** Applies the reviewed TRIM_THEN_NFC_V1 storage policy to character names. */
public final class CharacterNamePolicy {
    public static final int MINIMUM_CODE_POINTS = 1;
    public static final int MAXIMUM_CODE_POINTS = 80;

    private CharacterNamePolicy() {
    }

    /**
     * Removes Unicode leading/trailing whitespace, normalizes to NFC, then validates the stored
     * value by Unicode code points. Names are display labels only: this method does not query for
     * uniqueness or participate in character identity matching.
     */
    public static String normalize(String value) {
        if (value == null) {
            throw invalid();
        }
        String normalized = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < MINIMUM_CODE_POINTS || codePoints > MAXIMUM_CODE_POINTS
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        return normalized;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid character name");
    }
}
