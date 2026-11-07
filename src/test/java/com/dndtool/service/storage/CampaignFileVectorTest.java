package com.dndtool.service.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CampaignFileVectorTest {
    @Test void completeFileMatchesIndependentCanonicalEnvelopeAndDigest() throws Exception {
        var snapshot = CampaignLifecycleFixture.snapshot();
        var document = new CampaignData.Document(CampaignLifecycleFixture.FILE, snapshot);
        String unsigned = "{\"applicationModel\":\"campaign-spaces-3\",\"campaign\":{\"campaignKey\":\""
                + CampaignLifecycleFixture.CAMPAIGN + "\",\"eventTail\":\"2\",\"name\":\"测试\"},\"domains\":"
                + CampaignDomainCodec.text(snapshot.domains()) + ",\"fileKey\":\"" + CampaignLifecycleFixture.FILE
                + "\",\"fileKind\":\"RULE_BOUND_CAMPAIGN\",\"formatVersion\":3";
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update("DND-CAMPAIGN-FILE\0".getBytes(StandardCharsets.US_ASCII));
        sha.update("1\0".getBytes(StandardCharsets.US_ASCII));
        String digest = HexFormat.of().formatHex(sha.digest((unsigned + "}").getBytes(StandardCharsets.UTF_8)));
        byte[] canonical = (unsigned + ",\"integrity\":{\"algorithm\":\"SHA-256\",\"canonicalVersion\":1,\"digest\":\""
                + digest + "\"}}").getBytes(StandardCharsets.UTF_8);
        CampaignCodec codec = new CampaignCodec();
        assertEquals(document, codec.decode(canonical));
        assertArrayEquals(canonical, codec.encode(document));
    }
}
