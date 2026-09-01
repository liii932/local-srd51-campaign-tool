package com.dndtool.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable confirmation digest for a server-prepared level advancement. */
public final class LevelAdvancementRequestDigest {
    private static final String DOMAIN = "DND_TOOL_SE_LEVEL_ADVANCEMENT_CONFIRM_V1";
    private static final String DOMAIN_V2 = "DND_TOOL_SE_LEVEL_ADVANCEMENT_CONFIRM_V2";

    private LevelAdvancementRequestDigest() {
    }

    public static String sha256(String characterKey, int targetLevel,
            String hpChoiceAlgorithm, String previewDigest) {
        return digest(DOMAIN + '\n' + length(characterKey) + targetLevel + '\n'
                + length(hpChoiceAlgorithm) + length(previewDigest));
    }

    public static String sha256(LevelAdvancementRules.Request request, String previewDigest) {
        if (request.targetClassKey() == null) {
            return sha256(request.characterKey(), request.targetLevel(),
                    request.hpChoiceAlgorithm(), previewDigest);
        }
        StringBuilder canonical = new StringBuilder(DOMAIN_V2).append('\n')
                .append(length(request.characterKey())).append(request.targetLevel()).append('\n')
                .append(length(request.hpChoiceAlgorithm()))
                .append(length(request.targetClassKey()))
                .append(length(request.subclassKey()));
        request.abilityIncreases().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(length(entry.getKey()))
                        .append(entry.getValue()).append('\n'));
        canonical.append(length(request.featKey()));
        request.proficiencyChoices().stream().sorted()
                .forEach(value -> canonical.append(length(value)));
        canonical.append(length(previewDigest));
        return digest(canonical.toString());
    }

    private static String digest(String canonical) {
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
