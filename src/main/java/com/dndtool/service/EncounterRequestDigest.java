package com.dndtool.service;

import com.dndtool.service.EncounterStateService.ParticipantRequest;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Stable HTTP digest for initializing the one built-in node-map encounter. */
public final class EncounterRequestDigest {
    private static final String DOMAIN = "DND_TOOL_SE_INITIALIZE_STAGE3_ENCOUNTER_V1";

    private EncounterRequestDigest() {
    }

    public static String sha256(
            String campaignKey, String partyNodeKey, List<ParticipantRequest> participants) {
        List<ParticipantRequest> ordered = participants.stream()
                .sorted(Comparator.comparing(ParticipantRequest::characterKey))
                .toList();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, DOMAIN);
                write(output, campaignKey);
                write(output, partyNodeKey);
                write(output, Integer.toString(ordered.size()));
                for (ParticipantRequest participant : ordered) {
                    write(output, participant.characterKey());
                    write(output, participant.faction().name());
                    write(output, participant.nodeKey());
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

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] encoded = Objects.requireNonNull(value, "digest field")
                .getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
