package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndtool.persistence.CharacterAggregateMutationRepository;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class CharacterAggregateMutationServiceTest {
    private static final String CHARACTER_KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

    @Test
    void rejectsInvalidIdentityOrVersionBeforeCallingPersistence() throws SQLException {
        AtomicBoolean called = new AtomicBoolean();
        CharacterAggregateMutationService service = new CharacterAggregateMutationService(
                (command, mutation) -> {
                    called.set(true);
                    return null;
                });

        assertEquals(CharacterAggregateMutationService.Status.INVALID_REQUEST,
                service.mutate("NOT_UUID", 0, (connection, character) -> { }).status());
        assertEquals(CharacterAggregateMutationService.Status.INVALID_REQUEST,
                service.mutate(CHARACTER_KEY, -1, (connection, character) -> { }).status());
        assertEquals(CharacterAggregateMutationService.Status.INVALID_REQUEST,
                service.mutate(CHARACTER_KEY, 0, null).status());
        assertFalse(called.get());
    }

    @Test
    void passesStableKeyExpectedVersionAndLockedIdentityToTheChange() throws SQLException {
        AtomicBoolean changeApplied = new AtomicBoolean();
        CharacterAggregateMutationRepository repository = (command, mutation) -> {
            assertEquals(CHARACTER_KEY, command.characterKey());
            assertEquals(7, command.expectedRowVersion());
            mutation.apply(null,
                    new CharacterAggregateMutationRepository.LockedCharacter(11, 22, 33));
            return new CharacterAggregateMutationRepository.Result(
                    CharacterAggregateMutationRepository.Result.Status.UPDATED, 8L);
        };
        CharacterAggregateMutationService service =
                new CharacterAggregateMutationService(repository);

        CharacterAggregateMutationService.Result result = service.mutate(
                CHARACTER_KEY, 7, (connection, character) -> {
                    changeApplied.set(true);
                    assertEquals(11, character.id());
                    assertEquals(22, character.campaignId());
                    assertEquals(33, character.moduleReleaseId());
                });

        assertEquals(CharacterAggregateMutationService.Status.UPDATED, result.status());
        assertEquals(8L, result.rowVersion());
        assertEquals(true, changeApplied.get());
    }

    @Test
    void mapsMissingAndStaleResultsWithoutApplyingAChange() throws SQLException {
        CharacterAggregateMutationService missing = serviceReturning(
                CharacterAggregateMutationRepository.Result.Status.NOT_FOUND, null);
        CharacterAggregateMutationService stale = serviceReturning(
                CharacterAggregateMutationRepository.Result.Status.VERSION_CONFLICT, 9L);

        assertEquals(CharacterAggregateMutationService.Status.NOT_FOUND,
                missing.mutate(CHARACTER_KEY, 7, (connection, character) -> { }).status());
        CharacterAggregateMutationService.Result conflict = stale.mutate(
                CHARACTER_KEY, 7, (connection, character) -> { });
        assertEquals(CharacterAggregateMutationService.Status.VERSION_CONFLICT, conflict.status());
        assertEquals(9L, conflict.rowVersion());
    }

    private static CharacterAggregateMutationService serviceReturning(
            CharacterAggregateMutationRepository.Result.Status status, Long version) {
        return new CharacterAggregateMutationService((command, mutation) ->
                new CharacterAggregateMutationRepository.Result(status, version));
    }
}
