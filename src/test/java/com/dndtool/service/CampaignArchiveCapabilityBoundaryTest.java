package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.service.storage.CampaignCodec;
import com.dndtool.service.storage.CampaignJson;
import com.dndtool.service.storage.StorageException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CampaignArchiveCapabilityBoundaryTest {
    @Test
    void oldFormatsCannotOpenAsCompleteCampaigns() {
        var codec = new CampaignCodec();
        for (int format : List.of(1, 2)) {
            var file = CampaignJson.read("""
                    {"applicationModel":"campaign-spaces-3","campaign":{},"domains":{},
                     "fileKey":"11111111-1111-4111-8111-111111111111","fileKind":"RULE_BOUND_CAMPAIGN",
                     "formatVersion":3,"integrity":{}}
                    """.getBytes(StandardCharsets.UTF_8));
            file.addProperty("formatVersion", format);
            assertEquals("INVALID_FILE", assertThrows(StorageException.class,
                    () -> codec.decode(CampaignJson.canonical(file))).code());
        }
    }
}
