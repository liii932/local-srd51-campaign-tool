package com.dndtool.service;

import java.text.Normalizer;

/** Applies the two reviewed Unicode policies used by host command special paths. */
public final class CheckTextPolicy {
    private CheckTextPolicy() {
    }

    /** Trims a DM-provided manual label, normalizes it to NFC, and limits it to 80 code points. */
    public static String normalizeManualName(String value) {
        return normalize(value, true, 80, "Invalid manual check name");
    }

    /** Preserves message spacing while normalizing NFC and limiting the text to 500 code points. */
    public static String normalizeNoteMessage(String value) {
        return normalize(value, false, 500, "Invalid note message");
    }

    private static String normalize(
            String value, boolean trim, int maximumCodePoints, String errorMessage) {
        if (value == null) throw new IllegalArgumentException(errorMessage);
        String prepared = trim ? value.strip() : value;
        String normalized = Normalizer.normalize(prepared, Normalizer.Form.NFC);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < 1
                || codePoints > maximumCodePoints
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }
}
