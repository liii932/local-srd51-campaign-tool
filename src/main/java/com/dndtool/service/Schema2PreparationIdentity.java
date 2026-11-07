package com.dndtool.service;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Distinct server-owned UUIDv4 identities, stored in exact network byte order. */
public record Schema2PreparationIdentity(UUID runId, UUID snapshotId) {
    public Schema2PreparationIdentity {
        if (!valid(runId) || !valid(snapshotId) || runId.equals(snapshotId)) {
            throw new IllegalArgumentException("Invalid schema 2 runtime identity");
        }
    }

    public static Schema2PreparationIdentity random() {
        UUID run = UUID.randomUUID();
        UUID snapshot;
        do {
            snapshot = UUID.randomUUID();
        } while (snapshot.equals(run));
        return new Schema2PreparationIdentity(run, snapshot);
    }

    public static Schema2PreparationIdentity fromBytes(byte[] run, byte[] snapshot) {
        return new Schema2PreparationIdentity(decode(run), decode(snapshot));
    }

    public byte[] runBytes() { return encode(runId); }
    public byte[] snapshotBytes() { return encode(snapshotId); }

    private static boolean valid(UUID value) {
        return value != null && value.version() == 4 && value.variant() == 2;
    }

    private static UUID decode(byte[] bytes) {
        if (bytes == null || bytes.length != 16) throw new IllegalArgumentException("Invalid UUID");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        UUID value = new UUID(buffer.getLong(), buffer.getLong());
        if (!valid(value)) throw new IllegalArgumentException("Invalid UUID");
        return value;
    }

    private static byte[] encode(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
    }
}
