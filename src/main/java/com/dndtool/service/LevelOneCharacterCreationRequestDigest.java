package com.dndtool.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable confirmation digest; the preview digest commits to every bounded rule choice. */
public final class LevelOneCharacterCreationRequestDigest {
    private static final String DOMAIN = "DND_TOOL_SE_LEVEL_ONE_CONFIRM_V1";

    private LevelOneCharacterCreationRequestDigest() {
    }

    public static String sha256(String campaignKey, String characterName, String previewDigest) {
        String canonical = DOMAIN + '\n' + length(campaignKey) + length(characterName)
                + length(previewDigest);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String length(String value) {
        if (value == null) return "-1:\n";
        return value.getBytes(StandardCharsets.UTF_8).length + ":" + value + "\n";
    }
}
