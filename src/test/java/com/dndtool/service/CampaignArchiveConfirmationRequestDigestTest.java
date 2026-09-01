package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CampaignArchiveConfirmationRequestDigestTest {
    private static final String RAW = "1".repeat(64);
    private static final String CAMPAIGN = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @Test
    void isDeterministicAndUsesLowercaseSha256() {
        String digest = CampaignArchiveConfirmationRequestDigest.sha256(RAW, CAMPAIGN);

        assertEquals("71e6ba58aec898be8c993e0300c3128b33970b7f07faa03cbf20c55152e7757e",
                digest);
        assertEquals(digest,
                CampaignArchiveConfirmationRequestDigest.sha256(RAW, CAMPAIGN));
        assertEquals(64, digest.length());
        assertEquals(digest.toLowerCase(), digest);
    }

    @Test
    void distinguishesNoArchiveDecisionFromAnExplicitCampaign() {
        assertNotEquals(
                CampaignArchiveConfirmationRequestDigest.sha256(RAW, null),
                CampaignArchiveConfirmationRequestDigest.sha256(RAW, CAMPAIGN));
    }

    @Test
    void changingEitherBoundValueChangesTheDigest() {
        String baseline = CampaignArchiveConfirmationRequestDigest.sha256(RAW, CAMPAIGN);

        assertNotEquals(baseline, CampaignArchiveConfirmationRequestDigest.sha256(
                "2".repeat(64), CAMPAIGN));
        assertNotEquals(baseline, CampaignArchiveConfirmationRequestDigest.sha256(
                RAW, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"));
    }

    @Test
    void rejectsMalformedRawDigestsAndCampaignKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> CampaignArchiveConfirmationRequestDigest.sha256("ABC", null));
        assertThrows(IllegalArgumentException.class,
                () -> CampaignArchiveConfirmationRequestDigest.sha256(RAW, "not-a-key"));
        assertThrows(IllegalArgumentException.class,
                () -> CampaignArchiveConfirmationRequestDigest.sha256(
                        RAW, "aaaaaaaa-aaaa-1aaa-8aaa-aaaaaaaaaaaa"));
    }
}
