package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CharacterAggregateMutationRepository;
import com.dndtool.service.CharacterCreationIdentityFactory.CharacterType;
import com.dndtool.service.CharacterLifecycleService.CopySource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CharacterLifecycleServiceTest {
    private static final String CHARACTER_KEY = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String CAMPAIGN_KEY = "11111111-2222-4333-8444-555555555555";

    @Test
    void typeChangeUsesOptimisticBoundaryWithoutUpdatingIdentity() throws SQLException {
        MutationFixture fixture = new MutationFixture();
        CharacterLifecycleService service = fixture.service();

        CharacterAggregateMutationService.Result result =
                service.changeType(CHARACTER_KEY, 7, CharacterType.NPC);

        assertEquals(CharacterAggregateMutationService.Status.UPDATED, result.status());
        assertEquals(8L, result.rowVersion());
        assertEquals(CHARACTER_KEY, fixture.command.characterKey());
        assertEquals(7, fixture.command.expectedRowVersion());
        assertTrue(fixture.sql.contains("SET character_type = ?"));
        assertFalse(fixture.sql.contains("SET character_key"));
        assertFalse(fixture.sql.toUpperCase().contains("DELETE"));
        assertEquals("NPC", fixture.values.get(1));
        assertEquals(91L, fixture.values.get(2));
    }

    @Test
    void archiveAndRestoreOnlyWriteLifecycleStatus() throws SQLException {
        MutationFixture archive = new MutationFixture();
        CharacterAggregateMutationService.Result archived =
                archive.service().archive(CHARACTER_KEY, 2);
        MutationFixture restore = new MutationFixture();
        CharacterAggregateMutationService.Result restored =
                restore.service().restore(CHARACTER_KEY, 3);

        assertEquals(CharacterAggregateMutationService.Status.UPDATED, archived.status());
        assertEquals(CharacterAggregateMutationService.Status.UPDATED, restored.status());
        assertTrue(archive.sql.contains("SET character_status = ?"));
        assertTrue(restore.sql.contains("SET character_status = ?"));
        assertEquals(CHARACTER_KEY, archive.command.characterKey());
        assertEquals(CHARACTER_KEY, restore.command.characterKey());
        assertEquals("ARCHIVED", archive.values.get(1));
        assertEquals("ACTIVE", restore.values.get(1));
        assertFalse(archive.sql.toUpperCase().contains("DELETE"));
        assertFalse(restore.sql.toUpperCase().contains("DELETE"));
    }

    @Test
    void nullTypeIsRejectedBeforeEnteringTheMutationTransaction() throws SQLException {
        MutationFixture fixture = new MutationFixture();

        CharacterAggregateMutationService.Result result =
                fixture.service().changeType(CHARACTER_KEY, 0, null);

        assertEquals(CharacterAggregateMutationService.Status.INVALID_REQUEST, result.status());
        assertEquals(null, fixture.command);
    }

    @Test
    void copyRetriesSourceCollisionAndReturnsIndependentNormalizedIdentity() {
        Deque<UUID> generated = new ArrayDeque<>();
        generated.add(UUID.fromString(CHARACTER_KEY));
        generated.add(UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab"));
        CharacterCreationIdentityFactory identityFactory =
                new CharacterCreationIdentityFactory(generated::removeFirst);
        CharacterLifecycleService service = new CharacterLifecycleService(
                new CharacterAggregateMutationService((command, mutation) -> null),
                identityFactory);

        CharacterLifecycleService.CopyPlan plan = service.prepareCopy(new CopySource(
                CHARACTER_KEY, CAMPAIGN_KEY, CharacterType.PC, "  Cafe\u0301  "));

        assertEquals(CHARACTER_KEY, plan.sourceCharacterKey());
        assertNotEquals(plan.sourceCharacterKey(), plan.copy().characterKey());
        assertEquals("01234567-89ab-4cde-8fab-0123456789ab", plan.copy().characterKey());
        assertEquals("Caf\u00e9", plan.copy().characterName());
        assertEquals(CharacterType.PC, plan.copy().characterType());
    }

    @Test
    void lifecycleApiExposesNoPhysicalDeleteOperation() {
        assertFalse(Arrays.stream(CharacterLifecycleService.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("delete")));
    }

    private static final class MutationFixture {
        private CharacterAggregateMutationRepository.Command command;
        private String sql;
        private final Map<Integer, Object> values = new HashMap<>();

        private CharacterLifecycleService service() {
            CharacterAggregateMutationRepository repository = (received, mutation) -> {
                command = received;
                mutation.apply(connection(),
                        new CharacterAggregateMutationRepository.LockedCharacter(91, 92, 93));
                return new CharacterAggregateMutationRepository.Result(
                        CharacterAggregateMutationRepository.Result.Status.UPDATED,
                        received.expectedRowVersion() + 1);
            };
            return new CharacterLifecycleService(
                    new CharacterAggregateMutationService(repository));
        }

        private Connection connection() {
            return proxy(Connection.class, (ignored, method, arguments) -> {
                if ("prepareStatement".equals(method.getName())) {
                    sql = (String) arguments[0];
                    return statement();
                }
                return defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement statement() {
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setString", "setLong" -> {
                            values.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeUpdate" -> 1;
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class || type == short.class || type == int.class || type == long.class) {
            return 0;
        }
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
