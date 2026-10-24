package com.dndtool.service;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.BusinessWriteTestSupport;
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
    void sharedGuardRejectsRuntimeRemainderBeforeBusinessWork() throws Exception {
        Fixture fixture = new Fixture();
        var importer = new CampaignArchiveV2DraftImportService(fixture.dataSource(),
                (connection, state) -> {
                    throw new AssertionError("guard must reject before draft import");
                });
        try (var ignored = BusinessWriteTestSupport.runtimeRemainder()) {
            SQLException failure = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,
                    () -> importer.importState(CampaignArchiveV2CharacterStateCodec.write(state(), CHARACTERS), CHARACTERS));
            assertEquals("BWB04", failure.getSQLState());
        }
        assertFalse(fixture.calls.contains("commit"));
    }

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
    void retainsEveryGrantAgainstIndependentBytesAndRejectsForgedOrMissingProvenance() {
        var state = state().validated(CHARACTERS, CharacterProficiencyArchiveTestData.catalog());
        assertEquals(4, state.proficiencyGrants().size());
        String grantJson = "\"proficiencyGrants\":["
                + grantJson("LANGUAGE", "character.background", "background.acolyte", "creation.language.choice", "CHOICE", "language.celestial") + ","
                + grantJson("LANGUAGE", "character.class", "class.fighter", "creation.language.fixed", "FIXED", "language.common") + ","
                + grantJson("LANGUAGE", "character.race", "race.human", "creation.language.fixed", "FIXED", "language.common") + ","
                + grantJson("TOOL", "character.background", "background.acolyte", "creation.tool.fixed", "FIXED", "tool.smith_tools") + "]";
        assertTrue(json().contains(grantJson));
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_STRUCTURE,
                json().replace(grantJson, "\"omittedGrants\":[]"));
        assertStatus(CampaignArchiveV2CharacterStateCodec.Status.INVALID_VALUE,
                json().replace("\"sourceKey\":\"race.human\"", "\"sourceKey\":\"race.dwarf\""));
        var missingDuplicateSource = withGrants(state, state.proficiencyGrants().stream()
                .filter(row -> !row.grant().sourceType().equals("character.class")).toList());
        // The target remains granted by the race; the exact catalog still requires the class fact.
        assertThrows(IllegalArgumentException.class, () -> missingDuplicateSource.validated(
                CHARACTERS, CharacterProficiencyArchiveTestData.catalog()));
        var first = state.proficiencyGrants().getFirst();
        var grant = first.grant();
        var forged = new com.dndtool.module.CharacterProficiencyGrant(grant.category(),
                "other_module", grant.sourceType(), grant.sourceKey(), grant.grantSlotKey(),
                grant.grantKind(), grant.targetModuleKey(), grant.proficiencyKey(), grant.suppressed());
        List<ProficiencyGrant> rows = new ArrayList<>(state.proficiencyGrants());
        rows.set(0, new ProficiencyGrant(CHARACTER, forged));
        assertThrows(IllegalArgumentException.class, () -> withGrants(state, rows).validated(
                CHARACTERS, CharacterProficiencyArchiveTestData.catalog()));
    }

    private static String grantJson(String category, String sourceType, String sourceKey,
            String slot, String kind, String target) {
        return "{\"characterKey\":\"" + CHARACTER + "\",\"category\":\"" + category
                + "\",\"sourceModuleKey\":\"dnd5e2014_srd51_se\",\"sourceType\":\"" + sourceType
                + "\",\"sourceKey\":\"" + sourceKey + "\",\"grantSlotKey\":\"" + slot
                + "\",\"grantKind\":\"" + kind + "\",\"targetModuleKey\":\"dnd5e2014_srd51_se\""
                + ",\"proficiencyKey\":\"" + target + "\",\"suppressed\":false}";
    }

    private static CampaignArchiveV2CharacterState withGrants(
            CampaignArchiveV2CharacterState state, List<ProficiencyGrant> grants) {
        return new CampaignArchiveV2CharacterState(2, state.eventTail(), state.stateEvents(),
                state.creationSnapshots(), state.creationSelections(), state.resources(),
                state.classLevels(), state.subclasses(), state.features(), state.featureChoices(),
                state.feats(), state.multiclassProficiencies(), grants);
    }

    @Test
    void previewReportsEveryCurrentCharacterRulesPartitionAndRawDigest() {
        byte[] content = CampaignArchiveV2CharacterStateCodec.write(state(), CHARACTERS);

        CampaignArchiveV2DraftPreview.Result result =
                new CampaignArchiveV2DraftPreview().preview(content, CHARACTERS);

        assertEquals(CampaignArchiveV2DraftPreview.Status.READY, result.status());
        assertEquals(12, result.preview().eventTail());
        assertEquals(new CampaignArchiveV2DraftPreview.Counts(
                2, 1, 4, 1, 2, 1, 1, 1, 1, 1, 4), result.preview().counts());
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
                valid.featureChoices(), valid.feats(), valid.multiclassProficiencies(), valid.proficiencyGrants()));
    }

    @Test
    void draftExportConnectsConsistentReaderToStrictBytesAndRestoresConnection() throws Exception {
        Fixture fixture = new Fixture();
        var service = new CampaignArchiveV2DraftExportService(fixture.dataSource(),
                (connection, campaign, release, catalog) -> {
                    assertFalse(connection.getAutoCommit());
                    assertTrue(connection.isReadOnly());
                    assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
                    assertEquals(7, campaign);
                    assertEquals(9, release);
                    fixture.calls.add("read");
                    return state();
                });
        byte[] bytes = service.exportState(7, 9, CharacterProficiencyArchiveTestData.catalog());
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains(
                grantJson("TOOL", "character.background", "background.acolyte",
                        "creation.tool.fixed", "FIXED", "tool.smith_tools")));
        assertEquals(List.of("read", "commit"), fixture.calls);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
        assertTrue(fixture.closed);
        Fixture failed = new Fixture();
        assertThrows(SQLException.class, () -> new CampaignArchiveV2DraftExportService(
                failed.dataSource(), (connection, campaign, release, catalog) -> {
                    throw new SQLException("bad export source");
                }).exportState(7, 9, CharacterProficiencyArchiveTestData.catalog()));
        assertEquals(List.of("rollback"), failed.calls);
        assertTrue(failed.autoCommit);
        assertTrue(failed.closed);
        Fixture brokenProvenance = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> new CampaignArchiveV2DraftExportService(
                brokenProvenance.dataSource(), (connection, campaign, release, catalog) ->
                        withGrants(state(), List.of())).exportState(
                                7, 9, CharacterProficiencyArchiveTestData.catalog()));
        assertEquals(List.of("rollback"), brokenProvenance.calls);
        assertTrue(brokenProvenance.closed);
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
                List.of(new CreationSelection(CHARACTER, "SKILL", 1, "skill.athletics"),
                        new CreationSelection(CHARACTER, "LANGUAGE", 1, "language.common"),
                        new CreationSelection(CHARACTER, "LANGUAGE", 2, "language.celestial"),
                        new CreationSelection(CHARACTER, "TOOL", 1, "tool.smith_tools")),
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
                        CHARACTER, "class.rogue", "proficiency.armor.light", 8)),
                CharacterProficiencyArchiveTestData.grants().stream()
                        .map(grant -> new ProficiencyGrant(CHARACTER, grant)).toList());
    }

    private static CampaignArchiveV2CharacterState withEvents(
            CampaignArchiveV2CharacterState state, List<StateEvent> events) {
        return new CampaignArchiveV2CharacterState(
                2, 12, events, state.creationSnapshots(), state.creationSelections(),
                state.resources(), state.classLevels(), state.subclasses(), state.features(),
                state.featureChoices(), state.feats(), state.multiclassProficiencies(), state.proficiencyGrants());
    }

    private static CampaignArchiveV2CharacterState withFeats(
            CampaignArchiveV2CharacterState state, List<FeatState> feats) {
        return new CampaignArchiveV2CharacterState(
                2, 12, state.stateEvents(), state.creationSnapshots(), state.creationSelections(),
                state.resources(), state.classLevels(), state.subclasses(), state.features(),
                state.featureChoices(), feats, state.multiclassProficiencies(), state.proficiencyGrants());
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
            return BusinessWriteTestSupport.withGuard((DataSource) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getConnection")) {
                            connectionRequested = true;
                            return connection();
                        }
                        return defaultValue(method.getReturnType());
                    }));
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
