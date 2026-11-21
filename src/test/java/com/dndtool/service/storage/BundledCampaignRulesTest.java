package com.dndtool.service.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledCampaignRulesTest {
    @Test
    void applicationUsesFixedRuleFixturesFromTheTestClasspath() throws Exception {
        Path fixtures = Path.of("src/test/fixtures/application-rules");
        for (String name : new String[] {"srd51-runtime", "srd51-characters", "srd51-magic",
                "dm-runtime-house-rules", "dm-runtime-check-variant"}) {
            String resource = "storage/rules/" + name + ".zip";
            var resolved = BundledCampaignRules.class.getResource("/" + resource);
            assertNotNull(resolved, resource);
            assertTrue(Files.isSameFile(fixtures.resolve(resource), Path.of(resolved.toURI())), resolved::toString);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {BundledCampaignRules.STANDARD, BundledCampaignRules.CHECK_VARIANT})
    void applicationRestoresTheSelectedCombinationFromFrozenMaterial(String combination) {
        var material = BundledCampaignRules.projection(combination);
        var restored = CampaignDomainData.Material.of(material).restore().projection();
        assertNotSame(material, restored);
        assertArrayEquals(material.configurationJson(), restored.configurationJson());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
    }
}
