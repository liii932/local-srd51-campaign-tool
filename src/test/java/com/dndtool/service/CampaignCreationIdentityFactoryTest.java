package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CampaignCreationIdentityFactoryTest {
    @Test
    void generatesCanonicalLowercaseVersionFourKeyOnTheServer() {
        CampaignCreationIdentityFactory factory = new CampaignCreationIdentityFactory(
                () -> UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));

        String key = factory.newCampaignKey();

        assertEquals("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", key);
        assertTrue(key.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }

    @Test
    void generatesAFreshKeyForEachNewCampaign() {
        Deque<UUID> generated = new ArrayDeque<>();
        generated.add(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
        generated.add(UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab"));
        CampaignCreationIdentityFactory factory =
                new CampaignCreationIdentityFactory(generated::removeFirst);

        assertNotEquals(factory.newCampaignKey(), factory.newCampaignKey());
    }

    @Test
    void failsClosedForNullWrongVersionOrWrongVariant() {
        CampaignCreationIdentityFactory nullFactory =
                new CampaignCreationIdentityFactory(() -> null);
        CampaignCreationIdentityFactory wrongVersionFactory =
                new CampaignCreationIdentityFactory(
                        () -> UUID.fromString("aaaaaaaa-bbbb-1ccc-8ddd-eeeeeeeeeeee"));
        CampaignCreationIdentityFactory wrongVariantFactory =
                new CampaignCreationIdentityFactory(
                        () -> UUID.fromString("aaaaaaaa-bbbb-4ccc-1ddd-eeeeeeeeeeee"));

        assertThrows(NullPointerException.class, nullFactory::newCampaignKey);
        assertThrows(IllegalStateException.class, wrongVersionFactory::newCampaignKey);
        assertThrows(IllegalStateException.class, wrongVariantFactory::newCampaignKey);
    }
}
