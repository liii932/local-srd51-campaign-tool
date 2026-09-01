package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.service.CampaignArchiveDocument;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JdbcCampaignArchiveImportRepositoryTest {
    private static final String CAMPAIGN = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String OTHER = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String CHARACTER = "11111111-1111-4111-8111-111111111111";
    private static final long HOST_STATE_EPOCH = 424_242L;
    private static final long OLD_HOST_STATE_EPOCH = 17L;
    private final JdbcCampaignArchiveImportRepository repository =
            new JdbcCampaignArchiveImportRepository();

    @Test
    void createsEveryAuthoritativeSectionWithFreshLocalIds() throws Exception {
        Fixture fixture = new Fixture();

        long campaignId = repository.importArchive(
                fixture.connection(), command(document("ACTIVE"), null));

        assertEquals(7L, campaignId);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertEquals(0, fixture.executed.stream().filter(row -> row.sql().startsWith("DELETE "))
                .count());
        assertTrue(fixture.containsWrite("INSERT INTO campaign ("));
        assertTrue(fixture.containsWrite("INSERT INTO campaign_module"));
        assertTrue(fixture.containsWrite("INSERT INTO character_record"));
        assertEquals(4, fixture.writeCount("INSERT INTO character_field_value"));
        assertEquals(2, fixture.writeCount("INSERT INTO item_instance"));
        assertTrue(fixture.containsWrite("INSERT INTO map_instance"));
        assertTrue(fixture.containsWrite("INSERT INTO party_world_position"));
        assertTrue(fixture.containsWrite("INSERT INTO battle_state"));
        assertTrue(fixture.containsWrite("INSERT INTO battle_participant"));
        assertTrue(fixture.containsWrite("INSERT INTO entity_position"));
        assertEquals(2, fixture.writeCount("INSERT INTO game_event"));
        assertEquals(1, fixture.writeCount("INSERT INTO check_execution"));
        Executed root = fixture.firstWrite("INSERT INTO campaign (");
        assertEquals(Map.of(
                1, CAMPAIGN, 2, "测试战役", 3, "ACTIVE",
                4, HOST_STATE_EPOCH, 5, 9L), root.bound());
        assertTrue(root.sql().contains("host_state_epoch, row_version, internal_event_tail"));
        assertTrue(root.sql().contains("VALUES (?, ?, ?, ?, 0, ?)"));
        Executed character = fixture.firstWrite("INSERT INTO character_record");
        assertTrue(character.sql().contains("saved_content_sha256, row_version"));
        assertTrue(character.sql().contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"));
        Executed check = fixture.firstWrite("INSERT INTO check_execution");
        assertEquals(52L, check.bound().get(1));
        assertEquals(11L, check.bound().get(4));
        assertEquals("event.skill_check", check.bound().get(5));
    }

    @Test
    void replacementClearsEveryOldSectionThenRebuildsWithoutChildMerge() throws Exception {
        Fixture fixture = new Fixture();
        fixture.campaignRows.add(row(
                "id", 7L, "campaign_key", CAMPAIGN, "campaign_status", "ACTIVE",
                "host_state_epoch", OLD_HOST_STATE_EPOCH));
        fixture.characterRows.add(row(
                "id", 11L, "campaign_id", 7L, "character_key", CHARACTER));

        long campaignId = repository.importArchive(
                fixture.connection(), command(document("ACTIVE"), null));

        assertEquals(7L, campaignId);
        List<Executed> deletes = fixture.executed.stream()
                .filter(write -> write.sql().startsWith("DELETE ")).toList();
        assertEquals(34, deletes.size());
        assertTrue(deletes.stream().allMatch(write -> Long.valueOf(7L).equals(write.bound().get(1))));
        for (String table : List.of(
                "host_operation", "character_feature_adjudication_v2",
                "character_resource_recovery_v2", "character_feature_choice_v2",
                "character_feat_state_v2", "character_multiclass_proficiency_v2",
                "character_feature_state_v2", "character_subclass_state_v2",
                "character_ability_score_change_v2", "character_advancement_choice_v2",
                "character_level_resource_change_v2", "character_level_advancement_v2",
                "character_class_level_v2", "character_resource_state_v2",
                "character_creation_selection_v2", "character_creation_snapshot_v2",
                "check_effect_parameter_value", "check_effect", "dice_roll",
                "field_change", "check_execution", "entity_position", "battle_participant",
                "battle_state", "party_world_position", "map_instance", "item_instance",
                "character_field_value", "character_class_level",
                "character_skill_proficiency", "character_save_proficiency",
                "game_event", "character_record", "campaign_module")) {
            assertTrue(deletes.stream().anyMatch(write -> write.sql().contains(table)), table);
        }
        assertFalse(fixture.containsWrite("INSERT INTO campaign ("));
        Executed update = fixture.firstWrite("UPDATE campaign SET campaign_name");
        assertEquals(Map.of(
                        1, "测试战役", 2, "ACTIVE", 3, HOST_STATE_EPOCH,
                        4, 9L, 5, 7L, 6, CAMPAIGN),
                update.bound());
        assertTrue(update.sql().contains("host_state_epoch = ?"));
        assertTrue(update.sql().contains("row_version = 0"));
        assertTrue(fixture.executed.indexOf(update) > fixture.executed.indexOf(deletes.getLast()));
        assertTrue(fixture.containsWrite("INSERT INTO character_record"));
        assertTrue(fixture.executed.stream().noneMatch(write ->
                write.sql().contains("ON DUPLICATE KEY") || write.sql().contains("MERGE ")));
    }

    @Test
    void rejectsCharacterKeysOwnedByAnotherCampaignBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        fixture.characterRows.add(row(
                "id", 11L, "campaign_id", 9L, "character_key", CHARACTER));

        CampaignArchiveImportRepository.RejectionException exception = assertThrows(
                CampaignArchiveImportRepository.RejectionException.class,
                () -> repository.importArchive(
                        fixture.connection(), command(document("ARCHIVED"), null)));

        assertEquals(CampaignArchiveImportRepository.Rejection.STABLE_IDENTITY_CONFLICT,
                exception.rejection());
        assertTrue(fixture.executed.isEmpty());
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void rejectsDuplicatedStoredCampaignIdentityBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        fixture.campaignRows.add(row(
                "id", 7L, "campaign_key", CAMPAIGN, "campaign_status", "ARCHIVED",
                "host_state_epoch", OLD_HOST_STATE_EPOCH));
        fixture.campaignRows.add(row(
                "id", 8L, "campaign_key", CAMPAIGN, "campaign_status", "ARCHIVED",
                "host_state_epoch", OLD_HOST_STATE_EPOCH + 1L));

        assertThrows(SQLException.class, () -> repository.importArchive(
                fixture.connection(), command(document("ARCHIVED"), null)));

        assertTrue(fixture.executed.isEmpty());
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void activeImportRequiresExplicitConfirmationBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        fixture.campaignRows.add(row(
                "id", 8L, "campaign_key", OTHER, "campaign_status", "ACTIVE",
                "host_state_epoch", OLD_HOST_STATE_EPOCH));

        CampaignArchiveImportRepository.RejectionException exception = assertThrows(
                CampaignArchiveImportRepository.RejectionException.class,
                () -> repository.importArchive(
                        fixture.connection(), command(document("ACTIVE"), null)));

        assertEquals(CampaignArchiveImportRepository.Rejection
                .ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED, exception.rejection());
        assertTrue(fixture.executed.isEmpty());
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void exactPreviewedConfirmationArchivesOnlyTheCurrentOtherActiveCampaign()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.campaignRows.add(row(
                "id", 8L, "campaign_key", OTHER, "campaign_status", "ACTIVE",
                "host_state_epoch", OLD_HOST_STATE_EPOCH));

        long campaignId = repository.importArchive(
                fixture.connection(), command(document("ACTIVE"), OTHER));

        assertEquals(7L, campaignId);
        Executed archive = fixture.firstWrite("UPDATE campaign SET campaign_status = 'ARCHIVED'");
        assertEquals(Map.of(1, 8L, 2, OTHER), archive.bound());
        assertTrue(fixture.executed.indexOf(archive)
                < fixture.executed.indexOf(fixture.firstWrite("INSERT INTO campaign (")));
        assertEquals(1, fixture.writeCount("UPDATE campaign SET campaign_status = 'ARCHIVED'"));
        assertTrue(fixture.executed.stream()
                .filter(write -> write.sql().startsWith("DELETE "))
                .noneMatch(write -> Long.valueOf(8L).equals(write.bound().get(1))));
    }

    @Test
    void staleOrUnexpectedConfirmationFailsBeforeRebuildingTheTarget() {
        Fixture stale = new Fixture();
        stale.campaignRows.add(row(
                "id", 8L, "campaign_key", OTHER, "campaign_status", "ACTIVE",
                "host_state_epoch", OLD_HOST_STATE_EPOCH));
        String different = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

        CampaignArchiveImportRepository.RejectionException changed = assertThrows(
                CampaignArchiveImportRepository.RejectionException.class,
                () -> repository.importArchive(
                        stale.connection(), command(document("ACTIVE"), different)));

        assertEquals(CampaignArchiveImportRepository.Rejection.PREVIEW_STATE_CHANGED,
                changed.rejection());
        assertTrue(stale.executed.isEmpty());

        Fixture archived = new Fixture();
        CampaignArchiveImportRepository.RejectionException unexpected = assertThrows(
                CampaignArchiveImportRepository.RejectionException.class,
                () -> repository.importArchive(
                        archived.connection(), command(document("ARCHIVED"), OTHER)));
        assertEquals(CampaignArchiveImportRepository.Rejection.UNEXPECTED_ARCHIVE_CONFIRMATION,
                unexpected.rejection());
        assertTrue(archived.executed.isEmpty());
    }

    @Test
    void archivedImportLeavesAnotherActiveCampaignUntouched() throws Exception {
        Fixture fixture = new Fixture();
        fixture.campaignRows.add(row(
                "id", 8L, "campaign_key", OTHER, "campaign_status", "ACTIVE",
                "host_state_epoch", OLD_HOST_STATE_EPOCH));

        long campaignId = repository.importArchive(
                fixture.connection(), command(document("ARCHIVED"), null));

        assertEquals(7L, campaignId);
        Executed root = fixture.firstWrite("INSERT INTO campaign (");
        assertEquals("ARCHIVED", root.bound().get(3));
        assertFalse(fixture.containsWrite("UPDATE campaign SET campaign_name"));
        assertTrue(fixture.executed.stream()
                .filter(write -> write.sql().startsWith("DELETE "))
                .noneMatch(write -> Long.valueOf(8L).equals(write.bound().get(1))));
    }

    @Test
    void missingReleaseAndPersistenceFailureFailClosedWithoutOwningTransaction() {
        Fixture missing = new Fixture();
        missing.releaseAvailable = false;
        assertThrows(SQLException.class,
                () -> repository.importArchive(
                        missing.connection(), command(document("ARCHIVED"), null)));
        assertTrue(missing.executed.isEmpty());

        Fixture failing = new Fixture();
        failing.campaignRows.add(row(
                "id", 8L, "campaign_key", OTHER, "campaign_status", "ACTIVE",
                "host_state_epoch", OLD_HOST_STATE_EPOCH));
        failing.failWriteContaining = "INSERT INTO item_instance";
        assertThrows(SQLException.class,
                () -> repository.importArchive(
                        failing.connection(), command(document("ACTIVE"), OTHER)));
        assertFalse(failing.committed);
        assertFalse(failing.rolledBack);
        assertTrue(failing.containsWrite("UPDATE campaign SET campaign_status = 'ARCHIVED'"));
        assertTrue(failing.containsWrite("INSERT INTO character_record"));
    }

    @Test
    void rejectsInvalidDocumentAndConnectionBeforePreparingSql() {
        Fixture autoCommit = new Fixture();
        autoCommit.autoCommit = true;
        assertThrows(SQLException.class, () -> repository.importArchive(
                autoCommit.connection(), command(document("ARCHIVED"), null)));
        assertTrue(autoCommit.preparedSql.isEmpty());

        Fixture invalid = new Fixture();
        CampaignArchiveDocument valid = document("ARCHIVED");
        CampaignArchiveDocument malformed = new CampaignArchiveDocument(
                1,
                new CampaignArchiveDocument.Campaign(
                        "not-a-key", valid.campaign().campaignName(), "ARCHIVED"),
                valid.module(), valid.characters(), valid.fields(), valid.classLevels(),
                valid.skillProficiencies(), valid.saveProficiencies(), valid.items(),
                valid.maps(), valid.recentEvents());
        assertThrows(IllegalArgumentException.class,
                () -> repository.importArchive(invalid.connection(), command(malformed, null)));
        assertTrue(invalid.preparedSql.isEmpty());

        Fixture invalidConfirmation = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> repository.importArchive(
                invalidConfirmation.connection(), command(document("ACTIVE"), "not-a-key")));
        assertTrue(invalidConfirmation.preparedSql.isEmpty());
    }

    @Test
    void rejectsDuplicateTypedItemPositionAndReferenceConflictsBeforePreparingSql() {
        CampaignArchiveDocument valid = document("ARCHIVED");
        CampaignArchiveDocument.CharacterState character = valid.characters().getFirst();
        CampaignArchiveDocument.FieldValue field = valid.fields().getFirst();
        CampaignArchiveDocument.ItemState item = valid.items().getFirst();
        CampaignArchiveDocument.MapState map = valid.maps().getFirst();
        CampaignArchiveDocument.MapState secondEncounter = new CampaignArchiveDocument.MapState(
                "map.second", "NODE", "node.entry", map.encounter());
        CampaignArchiveDocument.Participant unknownParticipant =
                new CampaignArchiveDocument.Participant(
                        "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "ALLY", "node.entry");
        CampaignArchiveDocument.MapState badReference = new CampaignArchiveDocument.MapState(
                map.mapKey(), map.mapType(), map.partyNodeKey(),
                new CampaignArchiveDocument.Encounter(
                        "ACTIVE", List.of(unknownParticipant)));
        List<CampaignArchiveDocument> conflicts = List.of(
                copy(valid, List.of(character, character), valid.fields(), valid.items(),
                        valid.maps()),
                copy(valid, valid.characters(), concat(valid.fields(), field), valid.items(),
                        valid.maps()),
                copy(valid, valid.characters(), valid.fields(),
                        List.of(new CampaignArchiveDocument.ItemState(
                                item.characterKey(), item.sourceKind(), item.itemKey(),
                                item.itemName(), item.itemDescription(), 1000, item.itemStatus())),
                        valid.maps()),
                copy(valid, valid.characters(), valid.fields(), valid.items(),
                        List.of(map, secondEncounter)),
                copy(valid, valid.characters(), valid.fields(), valid.items(),
                        List.of(badReference)));

        for (CampaignArchiveDocument conflict : conflicts) {
            Fixture fixture = new Fixture();
            assertThrows(IllegalArgumentException.class, () -> repository.importArchive(
                    fixture.connection(), command(conflict, null)));
            assertTrue(fixture.preparedSql.isEmpty());
            assertTrue(fixture.executed.isEmpty());
        }
    }

    @Test
    void permitsIdenticalModuleItemsAsIndependentInstances() throws Exception {
        CampaignArchiveDocument valid = document("ARCHIVED");
        CampaignArchiveDocument.ItemState first = valid.items().getFirst();
        CampaignArchiveDocument repeated = copy(
                valid,
                valid.characters(),
                valid.fields(),
                concat(valid.items(), first),
                valid.maps());
        Fixture fixture = new Fixture();

        repository.importArchive(fixture.connection(), command(repeated, null));

        assertEquals(3, fixture.writeCount("INSERT INTO item_instance"));
    }

    @Test
    void rejectsNonFreshHostStateEpochBeforeArchivingOrRebuilding() {
        Fixture targetCollision = new Fixture();
        targetCollision.campaignRows.add(row(
                "id", 7L, "campaign_key", CAMPAIGN, "campaign_status", "ARCHIVED",
                "host_state_epoch", HOST_STATE_EPOCH));

        assertThrows(SQLException.class, () -> repository.importArchive(
                targetCollision.connection(), command(document("ARCHIVED"), null)));
        assertTrue(targetCollision.executed.isEmpty());

        Fixture activeCollision = new Fixture();
        activeCollision.campaignRows.add(row(
                "id", 8L, "campaign_key", OTHER, "campaign_status", "ACTIVE",
                "host_state_epoch", HOST_STATE_EPOCH));

        assertThrows(SQLException.class, () -> repository.importArchive(
                activeCollision.connection(), command(document("ACTIVE"), OTHER)));
        assertTrue(activeCollision.executed.isEmpty());
    }

    private static CampaignArchiveImportRepository.Command command(
            CampaignArchiveDocument document, String confirmedArchiveCampaignKey) {
        return new CampaignArchiveImportRepository.Command(
                document, confirmedArchiveCampaignKey, HOST_STATE_EPOCH);
    }

    private static <T> List<T> concat(List<T> values, T extra) {
        List<T> result = new ArrayList<>(values);
        result.add(extra);
        return List.copyOf(result);
    }

    private static CampaignArchiveDocument copy(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.CharacterState> characters,
            List<CampaignArchiveDocument.FieldValue> fields,
            List<CampaignArchiveDocument.ItemState> items,
            List<CampaignArchiveDocument.MapState> maps) {
        return new CampaignArchiveDocument(
                source.formatVersion(), source.campaign(), source.module(), characters, fields,
                source.classLevels(), source.skillProficiencies(), source.saveProficiencies(),
                items, maps, source.recentEvents());
    }

    private static CampaignArchiveDocument document(String campaignStatus) {
        String sha = BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;
        return new CampaignArchiveDocument(
                1,
                new CampaignArchiveDocument.Campaign(CAMPAIGN, "测试战役", campaignStatus),
                new CampaignArchiveDocument.ModuleReference(
                        "dnd5e2014_srd51_se_v1", "1", sha),
                List.of(new CampaignArchiveDocument.CharacterState(
                        CHARACTER, "PC", "Aria", "ACTIVE")),
                List.of(
                        new CampaignArchiveDocument.FieldValue(
                                CHARACTER, "field.a_text", "TEXT",
                                "备注", null, null, null),
                        new CampaignArchiveDocument.FieldValue(
                                CHARACTER, "field.b_integer", "INTEGER",
                                null, 12L, null, null),
                        new CampaignArchiveDocument.FieldValue(
                                CHARACTER, "field.c_decimal", "DECIMAL",
                                null, null, new BigDecimal("12.500000000000000000"), null),
                        new CampaignArchiveDocument.FieldValue(
                                CHARACTER, "field.d_boolean", "BOOLEAN",
                                null, null, null, true)),
                List.of(new CampaignArchiveDocument.ClassLevel(
                        CHARACTER, "class.fighter", 2)),
                List.of(new CampaignArchiveDocument.Proficiency(
                        CHARACTER, "skill.perception", "proficiency.full")),
                List.of(new CampaignArchiveDocument.Proficiency(
                        CHARACTER, "save.strength", "proficiency.full")),
                List.of(
                        new CampaignArchiveDocument.ItemState(
                                CHARACTER, "MODULE", "item.backpack",
                                "背包", "常用背包", 2, "ACTIVE"),
                        new CampaignArchiveDocument.ItemState(
                                CHARACTER, "TEMPORARY", null,
                                "钥匙", "临时钥匙", 1, "ARCHIVED")),
                List.of(new CampaignArchiveDocument.MapState(
                        "map.tavern_cellar", "NODE", "node.entry",
                        new CampaignArchiveDocument.Encounter("ACTIVE", List.of(
                                new CampaignArchiveDocument.Participant(
                                        CHARACTER, "ALLY", "node.cellar"))))),
                List.of(
                        new CampaignArchiveDocument.EventSnapshot(
                                8, "NOTE", null, "进入地窖。", null),
                        new CampaignArchiveDocument.EventSnapshot(
                                9, "CHECK_EXECUTED", CHARACTER, "锁已打开。",
                                new CampaignArchiveDocument.CheckSnapshot(
                                        "event.skill_check", "check.skill", "roll.normal",
                                        "skill.perception", null, 4, 14, 12, "SUCCESS"))));
    }

    private static Map<Object, Object> row(Object... values) {
        Map<Object, Object> row = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(values[index], values[index + 1]);
        }
        return row;
    }

    private record Executed(String sql, Map<Integer, Object> bound) {
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<Executed> executed = new ArrayList<>();
        private final List<Map<Object, Object>> campaignRows = new ArrayList<>();
        private final List<Map<Object, Object>> characterRows = new ArrayList<>();
        private boolean releaseAvailable = true;
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean committed;
        private boolean rolledBack;
        private String failWriteContaining;
        private int eventKey = 50;

        private Connection connection() {
            return proxy(Connection.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                case "prepareStatement" -> statement(arguments[0].toString());
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement statement(String originalSql) {
            String sql = originalSql.replaceAll("\\s+", " ").trim();
            preparedSql.add(sql);
            Map<Integer, Object> bound = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setString", "setLong", "setInt", "setBoolean", "setBigDecimal" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "setNull" -> { bound.put((int) arguments[0], null); yield null; }
                        case "executeQuery" -> query(sql);
                        case "executeUpdate" -> {
                            if (failWriteContaining != null && sql.contains(failWriteContaining)) {
                                throw new SQLException("synthetic import write failure");
                            }
                            executed.add(new Executed(sql, frozen(bound)));
                            yield sql.startsWith("DELETE ") ? 0 : 1;
                        }
                        case "getGeneratedKeys" -> generatedKeys(sql);
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet query(String sql) {
            if (sql.contains("FROM module_release")) {
                return rows(releaseAvailable ? List.of(row("id", 5L)) : List.of());
            }
            if (sql.contains("FROM campaign")) return rows(campaignRows);
            if (sql.contains("FROM character_record")) return rows(characterRows);
            throw new AssertionError("Unexpected query: " + sql);
        }

        private ResultSet generatedKeys(String sql) {
            long key;
            if (sql.startsWith("INSERT INTO campaign (")) key = 7L;
            else if (sql.startsWith("INSERT INTO character_record")) key = 11L;
            else if (sql.startsWith("INSERT INTO map_instance")) key = 21L;
            else if (sql.startsWith("INSERT INTO battle_state")) key = 31L;
            else if (sql.startsWith("INSERT INTO battle_participant")) key = 41L;
            else if (sql.startsWith("INSERT INTO game_event")) key = ++eventKey;
            else throw new AssertionError("Unexpected generated key SQL: " + sql);
            return rows(List.of(row(1, key)));
        }

        private boolean containsWrite(String prefix) {
            return executed.stream().anyMatch(write -> write.sql().startsWith(prefix));
        }

        private long writeCount(String prefix) {
            return executed.stream().filter(write -> write.sql().startsWith(prefix)).count();
        }

        private Executed firstWrite(String prefix) {
            return executed.stream().filter(write -> write.sql().startsWith(prefix))
                    .findFirst().orElseThrow();
        }
    }

    private static Map<Integer, Object> frozen(Map<Integer, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static ResultSet rows(List<Map<Object, Object>> rows) {
        int[] index = {-1};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getString" -> {
                Object value = rows.get(index[0]).get(arguments[0]);
                wasNull[0] = value == null;
                yield value == null ? null : value.toString();
            }
            case "getLong" -> {
                Object value = rows.get(index[0]).get(arguments[0]);
                wasNull[0] = value == null;
                yield value == null ? 0L : ((Number) value).longValue();
            }
            case "wasNull" -> wasNull[0];
            default -> defaultValue(method.getReturnType());
        });
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
