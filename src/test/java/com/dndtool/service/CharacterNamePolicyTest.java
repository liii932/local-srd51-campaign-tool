package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.service.CharacterCreationIdentityFactory.CharacterType;
import com.dndtool.service.CharacterCreationIdentityFactory.NewCharacterRequest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CharacterNamePolicyTest {
    private static final String CAMPAIGN_KEY = "11111111-2222-4333-8444-555555555555";
    private static final UUID GENERATED_KEY =
            UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

    @Test
    void trimsThenNormalizesEquivalentUnicodeToNfc() {
        assertEquals("Caf\u00e9", CharacterNamePolicy.normalize(" \tCafe\u0301　"));
        assertEquals("Caf\u00e9", CharacterNamePolicy.normalize("Caf\u00e9"));
    }

    @Test
    void countsUnicodeCodePointsInsteadOfUtf16CodeUnits() {
        String eightySupplementaryCharacters = "\ud83d\udc09".repeat(80);

        assertEquals(
                eightySupplementaryCharacters,
                CharacterNamePolicy.normalize(eightySupplementaryCharacters));
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterNamePolicy.normalize("\ud83d\udc09".repeat(81)));
    }

    @Test
    void rejectsMissingBlankAndControlCharacterNames() {
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize("　 \t"));
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize("Mage\nOne"));
        assertThrows(IllegalArgumentException.class, () -> CharacterNamePolicy.normalize("Mage\u0085One"));
    }

    @Test
    void creationStoresNormalizedNameAndRejectsBeforeAllocatingAKey() {
        AtomicInteger allocations = new AtomicInteger();
        CharacterCreationIdentityFactory factory = new CharacterCreationIdentityFactory(() -> {
            allocations.incrementAndGet();
            return GENERATED_KEY;
        });

        assertEquals(
                "Caf\u00e9",
                factory.prepare(new NewCharacterRequest(
                        CAMPAIGN_KEY, CharacterType.PC, "  Cafe\u0301  ")).characterName());
        assertEquals(1, allocations.get());
        assertThrows(IllegalArgumentException.class, () -> factory.prepare(
                new NewCharacterRequest(CAMPAIGN_KEY, CharacterType.PC, "\n")));
        assertEquals(1, allocations.get());
    }

    @Test
    void equalDisplayNamesRemainIndependentFromGeneratedIdentity() {
        UUID first = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        UUID second = UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab");
        UUID[] generated = {first, second};
        AtomicInteger next = new AtomicInteger();
        CharacterCreationIdentityFactory factory =
                new CharacterCreationIdentityFactory(() -> generated[next.getAndIncrement()]);
        NewCharacterRequest request =
                new NewCharacterRequest(CAMPAIGN_KEY, CharacterType.NPC, "守卫");

        var firstCharacter = factory.prepare(request);
        var secondCharacter = factory.prepare(request);

        assertEquals(firstCharacter.characterName(), secondCharacter.characterName());
        assertNotEquals(firstCharacter.characterKey(), secondCharacter.characterKey());
    }
}
