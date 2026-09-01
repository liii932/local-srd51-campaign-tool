package com.dndtool.service;

import com.dndtool.persistence.CharacterVersionRepository.VersionExpectation;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Binds a canonical host command check payload to executor and possible-target versions. */
public final class CheckRequestDigest {
    private static final byte[] DOMAIN =
            "DND_TOOL_SE_EXECUTE_STAGE3_CHECK_V1".getBytes(StandardCharsets.US_ASCII);

    private CheckRequestDigest() {
    }

    public static String sha256(
            String canonicalPayloadSha256,
            VersionExpectation executor,
            List<VersionExpectation> possibleTargets) {
        List<VersionExpectation> targets = new ArrayList<>(possibleTargets);
        targets.sort(Comparator.comparing(VersionExpectation::characterKey)
                .thenComparingLong(VersionExpectation::expectedRowVersion));
        List<byte[]> fields = new ArrayList<>();
        fields.add(DOMAIN);
        fields.add(utf8(canonicalPayloadSha256));
        fields.add(utf8(executor.characterKey()));
        fields.add(utf8(Long.toString(executor.expectedRowVersion())));
        fields.add(utf8(Integer.toString(targets.size())));
        for (VersionExpectation target : targets) {
            fields.add(utf8(target.characterKey()));
            fields.add(utf8(Long.toString(target.expectedRowVersion())));
        }
        int size = 0;
        for (byte[] field : fields) size = Math.addExact(size, Math.addExact(4, field.length));
        ByteBuffer payload = ByteBuffer.allocate(size);
        for (byte[] field : fields) payload.putInt(field.length).put(field);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.array()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
