package com.dndtool.service.storage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.dndtool.service.storage.CampaignData.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignFileVectorTest {
    @Test void completeFileMatchesIndependentlyCalculatedCanonicalBytesAndDigests() throws Exception {
        // The checked-in vector was constructed from the file contract with Python json/hashlib,
        // without invoking CampaignCodec, CampaignJson or CampaignRules for its expected bytes.
        byte[] fixture;
        try (var input = getClass().getResourceAsStream("/storage/vitality-known-file.json")) {
            assertNotNull(input);
            fixture = input.readAllBytes();
        }
        assertEquals('\n', fixture[fixture.length - 1]);
        byte[] canonical = Arrays.copyOf(fixture, fixture.length - 1);
        String character = "00000000-0000-4000-8000-000000000002";
        Document expected = new Document("00000000-0000-4000-8000-000000000003",
                new Snapshot("00000000-0000-4000-8000-000000000001", "测试",
                        List.of("srd51-vitality@1", "srd51-healing@1"),
                        List.of(new CharacterFact(character, "角色", 20, 11)),
                        List.of(new EventFact(1, "CHARACTER_CREATED", character, 3, 3, 0, 0, "角色", 20),
                                new EventFact(2, "HEALED", character, 3, 11, 8, 8, "", 0))));
        CampaignCodec codec = new CampaignCodec();
        assertEquals(expected, codec.decode(fixture));
        assertArrayEquals(canonical, codec.encode(expected));
    }
}
