package com.dndtool.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Binds raw archive bytes and the exact previewed active-campaign archive decision. */
public final class CampaignArchiveConfirmationRequestDigest {
    private static final byte[] DOMAIN =
            "DND_TOOL_SE_IMPORT_CAMPAIGN_ARCHIVE_V1".getBytes(StandardCharsets.US_ASCII);

    private CampaignArchiveConfirmationRequestDigest() {
    }

    public static String sha256(String rawFileSha256, String confirmedArchiveCampaignKey) {
        if (rawFileSha256 == null || !rawFileSha256.matches("[0-9a-f]{64}")
                || confirmedArchiveCampaignKey != null
                && !canonicalCampaignKey(confirmedArchiveCampaignKey)) {
            throw new IllegalArgumentException("Invalid archive confirmation digest input");
        }
        byte[] rawDigest = rawFileSha256.getBytes(StandardCharsets.US_ASCII);
        byte[] confirmedKey = confirmedArchiveCampaignKey == null
                ? null : confirmedArchiveCampaignKey.getBytes(StandardCharsets.US_ASCII);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeField(output, DOMAIN);
                writeField(output, rawDigest);
                if (confirmedKey == null) {
                    output.writeInt(-1);
                } else {
                    writeField(output, confirmedKey);
                }
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeField(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    private static boolean canonicalCampaignKey(String value) {
        if (value.length() != 36) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
