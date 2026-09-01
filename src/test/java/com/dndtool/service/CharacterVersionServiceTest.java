package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CharacterVersionRepository;
import com.dndtool.persistence.CharacterVersionRepository.LockCommand;
import com.dndtool.persistence.CharacterVersionRepository.LockResult;
import com.dndtool.persistence.CharacterVersionRepository.LockedCharacter;
import com.dndtool.persistence.CharacterVersionRepository.LockedScope;
import com.dndtool.persistence.CharacterVersionRepository.VersionExpectation;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CharacterVersionServiceTest {
    private static final String EXECUTOR = "11111111-1111-1111-1111-111111111111";
    private static final String TARGET_A = "22222222-2222-2222-2222-222222222222";
    private static final String TARGET_B = "33333333-3333-3333-3333-333333333333";
    private static final String PAYLOAD = "a".repeat(64);

    @Test
    void locksNormalizedScopeBeforeWorkAndAdvancesOnlyModifiedCharacters() throws Exception {
        Fixture fixture = new Fixture();
        CharacterVersionService service = new CharacterVersionService(fixture);
        VersionExpectation executor = new VersionExpectation(EXECUTOR, 2L);
        List<VersionExpectation> normalizedTargets = List.of(
                new VersionExpectation(TARGET_A, 4L),
                new VersionExpectation(TARGET_B, 6L));
        String digest = CheckRequestDigest.sha256(PAYLOAD, executor, normalizedTargets);

        CharacterVersionService.Result<String> result = service.executeLocked(
                connection(), new CharacterVersionService.Request(
                        7L, 11L, PAYLOAD, digest, executor,
                        List.of(normalizedTargets.get(1), normalizedTargets.get(0),
                                normalizedTargets.get(0))),
                Set.of(TARGET_A, TARGET_B), (connection, scope) -> {
                    assertTrue(fixture.lockCalled);
                    fixture.workCalled = true;
                    return new CharacterVersionService.LockedWorkResult<>(
                            "done", Set.of(11L, 12L));
                });

        assertEquals(CharacterVersionRepository.Status.LOCKED, result.status());
        assertEquals("done", result.value());
        assertEquals(Map.of(11L, 5L, 12L, 7L), result.advancedRowVersions());
        assertEquals(List.of(TARGET_A, TARGET_B), fixture.command.possibleTargets().stream()
                .map(VersionExpectation::characterKey).toList());
        assertEquals(Set.of(11L, 12L), fixture.modifiedIds);
        assertFalse(result.advancedRowVersions().containsKey(10L));
    }

    @Test
    void versionConflictDoesNotEnterRandomOrEventWork() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lockResult = LockResult.rejected(
                CharacterVersionRepository.Status.VERSION_CONFLICT, TARGET_A, 5L);
        CharacterVersionService service = new CharacterVersionService(fixture);
        VersionExpectation executor = new VersionExpectation(EXECUTOR, 2L);
        List<VersionExpectation> targets = List.of(new VersionExpectation(TARGET_A, 4L));

        CharacterVersionService.Result<String> result = service.executeLocked(
                connection(), request(executor, targets), Set.of(TARGET_A),
                (connection, scope) -> {
                    fixture.workCalled = true;
                    return new CharacterVersionService.LockedWorkResult<>("bad", Set.of());
                });

        assertEquals(CharacterVersionRepository.Status.VERSION_CONFLICT, result.status());
        assertEquals(TARGET_A, result.rejectedCharacterKey());
        assertEquals(5L, result.currentRowVersion());
        assertFalse(fixture.workCalled);
        assertFalse(fixture.advanceCalled);
    }

    @Test
    void rejectsForgedDigestAndConflictingDuplicateBeforeRepository() {
        Fixture fixture = new Fixture();
        CharacterVersionService service = new CharacterVersionService(fixture);
        VersionExpectation executor = new VersionExpectation(EXECUTOR, 2L);
        assertThrows(IllegalArgumentException.class, () -> service.executeLocked(
                connection(), new CharacterVersionService.Request(
                        7L, 11L, PAYLOAD, "b".repeat(64), executor, List.of()),
                Set.of(), (connection, scope) -> null));
        List<VersionExpectation> conflicting = List.of(
                new VersionExpectation(TARGET_A, 4L),
                new VersionExpectation(TARGET_A, 5L));
        assertThrows(IllegalArgumentException.class, () -> service.executeLocked(
                connection(), new CharacterVersionService.Request(
                        7L, 11L, PAYLOAD,
                        CheckRequestDigest.sha256(PAYLOAD, executor, conflicting),
                        executor, conflicting),
                Set.of(TARGET_A), (connection, scope) -> null));
        assertFalse(fixture.lockCalled);
    }

    @Test
    void rejectsMissingOrExtraVersionedTargetsBeforeLocking() {
        Fixture fixture = new Fixture();
        CharacterVersionService service = new CharacterVersionService(fixture);
        VersionExpectation executor = new VersionExpectation(EXECUTOR, 2L);
        List<VersionExpectation> targets = List.of(new VersionExpectation(TARGET_A, 4L));

        assertThrows(IllegalArgumentException.class, () -> service.executeLocked(
                connection(), request(executor, targets), Set.of(TARGET_A, TARGET_B),
                (connection, scope) -> null));
        assertThrows(IllegalArgumentException.class, () -> service.executeLocked(
                connection(), request(executor, targets), Set.of(),
                (connection, scope) -> null));
        assertFalse(fixture.lockCalled);
    }

    private static CharacterVersionService.Request request(
            VersionExpectation executor, List<VersionExpectation> targets) {
        return new CharacterVersionService.Request(
                7L, 11L, PAYLOAD,
                CheckRequestDigest.sha256(PAYLOAD, executor, targets),
                executor, targets);
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> null);
    }

    private static final class Fixture implements CharacterVersionRepository {
        private final LockedCharacter executor =
                new LockedCharacter(10L, EXECUTOR, 7L, 11L, 2L);
        private final LockedCharacter targetA =
                new LockedCharacter(11L, TARGET_A, 7L, 11L, 4L);
        private final LockedCharacter targetB =
                new LockedCharacter(12L, TARGET_B, 7L, 11L, 6L);
        private LockResult lockResult = LockResult.locked(
                new LockedScope(7L, 11L, 30L, executor, List.of(executor, targetA, targetB)));
        private LockCommand command;
        private boolean lockCalled;
        private boolean workCalled;
        private boolean advanceCalled;
        private Set<Long> modifiedIds = Set.of();

        @Override
        public LockResult lockBeforeRoll(Connection connection, LockCommand command) {
            this.command = command;
            lockCalled = true;
            return lockResult;
        }

        @Override
        public Map<Long, Long> advanceModifiedVersions(
                Connection connection, LockedScope scope, Set<Long> modifiedCharacterIds)
                throws SQLException {
            advanceCalled = true;
            modifiedIds = Set.copyOf(modifiedCharacterIds);
            Map<Long, Long> next = new java.util.HashMap<>();
            for (LockedCharacter character : scope.charactersById()) {
                if (modifiedCharacterIds.contains(character.id())) {
                    next.put(character.id(), character.rowVersion() + 1L);
                }
            }
            return next;
        }
    }
}
