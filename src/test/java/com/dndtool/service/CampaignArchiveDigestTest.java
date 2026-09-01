package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class CampaignArchiveDigestTest {
    @Test
    void computesCanonicalRawByteSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                CampaignArchiveDigest.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }
}
