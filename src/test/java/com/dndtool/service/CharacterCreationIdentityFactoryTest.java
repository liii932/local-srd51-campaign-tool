package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.CharacterCreationIdentityFactory.CharacterType;
import com.dndtool.service.CharacterCreationIdentityFactory.NewCharacterRequest;
import com.dndtool.service.CharacterCreationIdentityFactory.PreparedCharacter;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CharacterCreationIdentityFactoryTest {
    private static final String CAMPAIGN_KEY = "11111111-2222-4333-8444-555555555555";

    @Test
    void ordinaryRequestHasNoCharacterKeyAndPreparedIdentityIsImmutable() {
        assertFalse(Arrays.stream(NewCharacterRequest.class.getRecordComponents())
                .anyMatch(component -> "characterKey".equals(component.getName())));
        assertTrue(Modifier.isFinal(PreparedCharacter.class.getModifiers()));
        assertTrue(Arrays.stream(PreparedCharacter.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertFalse(Arrays.stream(PreparedCharacter.class.getMethods())
                .anyMatch(method -> method.getName().startsWith("set")));
    }

    @Test
    void addsCanonicalLowercaseVersionFourUuidOnTheServer() {
        UUID generated = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        CharacterCreationIdentityFactory factory =
                new CharacterCreationIdentityFactory(() -> generated);

        PreparedCharacter prepared = factory.prepare(
                new NewCharacterRequest(CAMPAIGN_KEY, CharacterType.PC, "Aria"));

        assertEquals("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", prepared.characterKey());
        assertEquals(CAMPAIGN_KEY, prepared.campaignKey());
        assertEquals(CharacterType.PC, prepared.characterType());
        assertEquals("Aria", prepared.characterName());
        assertTrue(prepared.characterKey().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }

    @Test
    void generatesAFreshKeyForEachNewCharacter() {
        Deque<UUID> generated = new ArrayDeque<>();
        generated.add(UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"));
        generated.add(UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab"));
        CharacterCreationIdentityFactory factory =
                new CharacterCreationIdentityFactory(generated::removeFirst);
        NewCharacterRequest request =
                new NewCharacterRequest(CAMPAIGN_KEY, CharacterType.NPC, "Guard");

        assertNotEquals(
                factory.prepare(request).characterKey(),
                factory.prepare(request).characterKey());
    }

    @Test
    void failsClosedIfTheGeneratorDoesNotReturnAStandardRandomUuid() {
        CharacterCreationIdentityFactory nullFactory =
                new CharacterCreationIdentityFactory(() -> null);
        CharacterCreationIdentityFactory wrongVersionFactory =
                new CharacterCreationIdentityFactory(
                        () -> UUID.fromString("aaaaaaaa-bbbb-1ccc-8ddd-eeeeeeeeeeee"));
        NewCharacterRequest request =
                new NewCharacterRequest(CAMPAIGN_KEY, CharacterType.PC, "Aria");

        assertThrows(NullPointerException.class, () -> nullFactory.prepare(request));
        assertThrows(IllegalStateException.class, () -> wrongVersionFactory.prepare(request));
    }
}
