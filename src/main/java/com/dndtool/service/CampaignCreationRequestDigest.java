package com.dndtool.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical request digest for the CREATE_CAMPAIGN idempotency contract. */
public final class CampaignCreationRequestDigest {
    private static final byte[] DOMAIN =
            "DND_TOOL_SE_CREATE_CAMPAIGN_V1".getBytes(StandardCharsets.US_ASCII);

    private CampaignCreationRequestDigest() {
    }

    /** Hashes the already validated TRIM_THEN_NFC campaign name with a length prefix. */
    public static String sha256(String normalizedCampaignName) {
        byte[] name = normalizedCampaignName.getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(DOMAIN.length);
                output.write(DOMAIN);
                output.writeInt(name.length);
                output.write(name);
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
