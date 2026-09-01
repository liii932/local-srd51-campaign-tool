package com.dndtool.service;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class CampaignArchiveV2CharacterStateTest {
    private static final String CHARACTER = "11111111-1111-4111-8111-111111111111";
    private static final Set<String> CHARACTERS = Set.of(CHARACTER);

    @Test
    void writesAndStrictlyReadsCanonicalDatabaseIdFreeEventClosure() {
        CampaignArchiveV2CharacterState input = state();

        byte[] first = CampaignArchiveV2CharacterStateCodec.write(input, CHARACTERS);
        byte[] second = CampaignArchiveV2CharacterStateCodec.write(input, CHARACTERS);
        CampaignArchiveV2CharacterStateCodec.Result read =
                CampaignArchiveV2CharacterStateCodec.read(first, CHARACTERS);

        assertEquals(CampaignArchiveV2CharacterStateCodec.Status.READY, read.status());
        assertEquals(read.state(), CampaignArchiveV2CharacterStateValidator.normalize(
                input, CHARACTERS));
        assertEquals(new String(first, StandardCharsets.UTF_8),
                new String(second, StandardCharsets.UTF_8));
        String json = new String(first, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"archiveFormatVersion\":2"));
        assertTrue(json.contains("\"eventTail\":12"));
        assertTrue(json.contains("\"acquiredEventSequence\":8"));
        assertFalse(json.matches("(?s).*\"(?:id|.*Id|rowVersion|hostStateEpoch)\".*"));
        assertFalse(json.contains("recentEvents"));
    }

    @Test
    void previewReportsEveryCurrentCharacterRulesPartitionAndRawDigest() {
        byte[] content = CampaignArchiveV2CharacterStateCodec.write(state(), CHARACTERS);

        CampaignArchiveV2DraftPreview.Result result =
                new CampaignArchiveV2DraftPreview().preview(content, CHARACTERS);

        assertEquals(CampaignArchiveV2DraftPreview.Status.READY, result.status());
        assertEquals(12, result.preview().eventTail());
        assertEquals(new CampaignArchiveV2DraftPreview.Counts(
                2, 1, 1, 1, 2, 1, 1, 1, 1, 1), result.preview().counts());
        assertEquals(CampaignArchiveDigest.sha256(content), result.preview().rawPayloadSha256());
    }

    @Test
    void rejectsUnknownDuplicateMalformedAndUnsupportedJsonWithoutFallback() {
        String valid = json();
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.DUPLICATE_KEY,
                valid.replace("\"eventTail\":12", "\"eventTail\":12,\"eventTail\":12"));
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_STRUCTURE,
                valid.replace("\"eventTail\":12", "\"eventTail\":12,\"unknown\":true"));
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_VALUE,
                valid.replace("\"archiveFormatVersion\":2", "\"archiveFormatVersion\":1"));
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_VALUE,
                valid.replace("\"classLevel\":4", "\"classLevel\":4.0"));
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_STRUCTURE,
                valid.replace("\"unlimited\":false", "\"unlimited\":\"false\""));
    }

    @Test
    void enforcesFileUtf8NestingArrayAndIntegerBoundariesBeforeFallback() {
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.FILE_TOO_LARGE,
                new byte[CampaignSaveFileService.MAX_BYTES + 1]);
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_UTF8,
                new byte[] {(byte) 0xC3, (byte) 0x28});
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_UTF8,
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'});

        String nested = "{\"nested\":".repeat(17) + "0" + "}".repeat(17);
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_STRUCTURE, nested);

        StringBuilder oversizedArray = new StringBuilder("{\"values\":[");
        for (int index = 0; index <= 100_000; index++) {
            if (index > 0) oversizedArray.append(',');
            oversizedArray.append("null");
        }
        oversizedArray.append("]}");
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_STRUCTURE,
                oversizedArray.toString());

        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_VALUE,
                json().replace("\"classLevel\":4", "\"classLevel\":4e0"));
    }

    @Test
    void rejectsDanglingWrongSubjectWrongTypeAndUnreferencedStateEvents() {
        CampaignArchiveV2CharacterState valid = state();
        assertInvalid(withEvents(valid, List.of(
                new StateEvent(1, "LEVEL_ONE_CHARACTER_CREATED", CHARACTER),
                new StateEvent(8, "CHARACTER_LEVEL_ADVANCED",
                        "22222222-2222-4222-8222-222222222222"))));
        assertInvalid(withFeats(valid, List.of(new FeatState(CHARACTER, "feat.grappler", 1))));
        assertInvalid(withEvents(valid, List.of(
                new StateEvent(1, "LEVEL_ONE_CHARACTER_CREATED", CHARACTER),
                new StateEvent(8, "CHARACTER_LEVEL_ADVANCED", CHARACTER),
                new StateEvent(9, "CHARACTER_LEVEL_ADVANCED", CHARACTER))));
        assertInvalid(new CampaignArchiveV2CharacterState(
                2, 7, valid.stateEvents(), valid.creationSnapshots(), valid.creationSelections(),
                valid.resources(), valid.classLevels(), valid.subclasses(), valid.features(),
                valid.featureChoices(), valid.feats(), valid.multiclassProficiencies()));
    }

    @Test
    void draftImportOwnsOneSerializableTransactionAndRollsBackInjectedFailure()
            throws Exception {
        byte[] content = CampaignArchiveV2CharacterStateCodec.write(state(), CHARACTERS);
        Fixture success = new Fixture();
        CampaignArchiveV2DraftImportService service = new CampaignArchiveV2DraftImportService(
                success.dataSource(), (connection, imported) -> {
                    assertFalse(connection.getAutoCommit());
                    assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                            connection.getTransactionIsolation());
                    assertEquals(state().feats(), imported.feats());
                    success.calls.add("work");
                });

        assertEquals(CampaignArchiveV2DraftImportService.Status.COMPLETED,
                service.importState(content, CHARACTERS));
        assertEquals(List.of("work", "commit"), success.calls);
        assertTrue(success.autoCommit);
        assertFalse(success.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, success.isolation);
        assertTrue(success.closed);

        Fixture failed = new Fixture();
        CampaignArchiveV2DraftImportService failing = new CampaignArchiveV2DraftImportService(
                failed.dataSource(), (connection, imported) -> {
                    failed.calls.add("partial-write");
                    throw new SQLException("injected format-2 import failure");
                });
        SQLException exception = assertThrows(SQLException.class,
                () -> failing.importState(content, CHARACTERS));
        assertEquals("injected format-2 import failure", exception.getMessage());
        assertEquals(List.of("partial-write", "rollback"), failed.calls);
        assertTrue(failed.autoCommit);
        assertTrue(failed.closed);

        Fixture invalid = new Fixture();
        CampaignArchiveV2DraftImportService rejected = new CampaignArchiveV2DraftImportService(
                invalid.dataSource(), (connection, imported) -> {
                    throw new AssertionError("invalid archive reached import work");
                });
        assertEquals(CampaignArchiveV2DraftImportService.Status.INVALID_ARCHIVE,
                rejected.importState("{}".getBytes(StandardCharsets.UTF_8), CHARACTERS));
        assertFalse(invalid.connectionRequested);
    }

    private static CampaignArchiveV2CharacterState state() {
        List<AbilityScore> abilities = List.of(
                new AbilityScore("ability.wisdom", 10, 10),
                new AbilityScore("ability.strength", 15, 16),
                new AbilityScore("ability.dexterity", 14, 14),
                new AbilityScore("ability.constitution", 13, 14),
                new AbilityScore("ability.intelligence", 12, 12),
                new AbilityScore("ability.charisma", 8, 8));
        return new CampaignArchiveV2CharacterState(
                2,
                12,
                List.of(
                        new StateEvent(8, "CHARACTER_LEVEL_ADVANCED", CHARACTER),
                        new StateEvent(1, "LEVEL_ONE_CHARACTER_CREATED", CHARACTER)),
                List.of(new CreationSnapshot(
                        CHARACTER, 1, "a".repeat(64), "b".repeat(64),
                        "ability.standard_array_v1", "race.human", null,
                        "background.acolyte", "class.fighter", abilities, 12)),
                List.of(new CreationSelection(
                        CHARACTER, "SKILL", 1, "skill.athletics")),
                List.of(new ResourceState(
                        CHARACTER, "resource.hit_points", 12, 12, false)),
                List.of(
                        new ClassLevel(CHARACTER, "class.rogue", 1),
                        new ClassLevel(CHARACTER, "class.fighter", 4)),
                List.of(new SubclassState(
                        CHARACTER, "class.fighter", "subclass.champion", 3, 8)),
                List.of(new FeatureState(
                        CHARACTER, "feature.fighter.fighting_style", 1,
                        "DM_ADJUDICATION", "DM_ADJUDICATION_FIGHTING_STYLE_V1", 1)),
                List.of(new FeatureChoice(
                        CHARACTER, "feature.fighter.fighting_style", 1,
                        "character.feature", "feature.fighter.fighting_style.archery", 1)),
                List.of(new FeatState(CHARACTER, "feat.grappler", 8)),
                List.of(new MulticlassProficiency(
                        CHARACTER, "class.rogue", "proficiency.armor.light", 8)));
    }

    private static CampaignArchiveV2CharacterState withEvents(
            CampaignArchiveV2CharacterState state, List<StateEvent> events) {
        return new CampaignArchiveV2CharacterState(
                2, 12, events, state.creationSnapshots(), state.creationSelections(),
                state.resources(), state.classLevels(), state.subclasses(), state.features(),
                state.featureChoices(), state.feats(), state.multiclassProficiencies());
    }

    private static CampaignArchiveV2CharacterState withFeats(
            CampaignArchiveV2CharacterState state, List<FeatState> feats) {
        return new CampaignArchiveV2CharacterState(
                2, 12, state.stateEvents(), state.creationSnapshots(), state.creationSelections(),
                state.resources(), state.classLevels(), state.subclasses(), state.features(),
                state.featureChoices(), feats, state.multiclassProficiencies());
    }

    private static void assertInvalid(CampaignArchiveV2CharacterState state) {
        assertThrows(IllegalArgumentException.class,
                () -> CampaignArchiveV2CharacterStateCodec.write(state, CHARACTERS));
    }

    private static String json() {
        return new String(CampaignArchiveV2CharacterStateCodec.write(state(), CHARACTERS),
                StandardCharsets.UTF_8);
    }

    private static void assertStatus(
            CampaignArchiveV2CharacterStateCodec.Status status, String json) {
        assertStatus(status, json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertStatus(
            CampaignArchiveV2CharacterStateCodec.Status status, byte[] content) {
        CampaignArchiveV2CharacterStateCodec.Result result =
                CampaignArchiveV2CharacterStateCodec.read(content, CHARACTERS);
        assertEquals(status, result.status());
        assertNull(result.state());
    }

    private static final class Fixture {
        private final List<String> calls = new ArrayList<>();
        private boolean connectionRequested;
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean closed;

        private DataSource dataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getConnection")) {
                            connectionRequested = true;
                            return connection();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> { autoCommit = (boolean) args[0]; yield null; }
                        case "isReadOnly" -> readOnly;
                        case "setReadOnly" -> { readOnly = (boolean) args[0]; yield null; }
                        case "getTransactionIsolation" -> isolation;
                        case "setTransactionIsolation" -> { isolation = (int) args[0]; yield null; }
                        case "commit" -> { calls.add("commit"); yield null; }
                        case "rollback" -> { calls.add("rollback"); yield null; }
                        case "close" -> { closed = true; yield null; }
                        case "isClosed" -> closed;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
