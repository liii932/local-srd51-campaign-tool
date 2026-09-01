package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.persistence.CampaignArchiveRepository;
import com.dndtool.persistence.CampaignArchiveRepository.Campaign;
import com.dndtool.persistence.CampaignArchiveRepository.CharacterState;
import com.dndtool.persistence.CampaignArchiveRepository.CheckSnapshot;
import com.dndtool.persistence.CampaignArchiveRepository.ClassLevel;
import com.dndtool.persistence.CampaignArchiveRepository.Encounter;
import com.dndtool.persistence.CampaignArchiveRepository.EventSnapshot;
import com.dndtool.persistence.CampaignArchiveRepository.FieldValue;
import com.dndtool.persistence.CampaignArchiveRepository.ItemState;
import com.dndtool.persistence.CampaignArchiveRepository.MapState;
import com.dndtool.persistence.CampaignArchiveRepository.ModuleBinding;
import com.dndtool.persistence.CampaignArchiveRepository.Participant;
import com.dndtool.persistence.CampaignArchiveRepository.Proficiency;
import com.dndtool.persistence.CampaignArchiveRepository.Snapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CampaignArchiveExportServiceTest {
    static final String CAMPAIGN_KEY =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    static final String CHARACTER_KEY =
            "11111111-1111-4111-8111-111111111111";
    private static final String SHA =
            BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;

    @Test
    void writesCompleteDeterministicVersionOneCurrentStateAndRecentContext()
            throws Exception {
        Snapshot snapshot = snapshot();
        CampaignArchiveExportService service = service(Optional.of(snapshot));

        CampaignArchiveExportService.Result first = service.export(CAMPAIGN_KEY);
        CampaignArchiveExportService.Result second = service.export(CAMPAIGN_KEY);

        assertEquals(CampaignArchiveExportService.Status.READY, first.status());
        assertEquals("campaign-save.json", first.file().fileName());
        assertEquals(
                new String(first.file().content(), StandardCharsets.UTF_8),
                new String(second.file().content(), StandardCharsets.UTF_8));

        JsonObject document = JsonParser.parseString(
                new String(first.file().content(), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, document.get("formatVersion").getAsInt());
        assertEquals(CAMPAIGN_KEY,
                document.getAsJsonObject("campaign").get("campaignKey").getAsString());
        assertEquals(SHA,
                document.getAsJsonObject("module").get("contentSha256").getAsString());
        assertEquals(1, document.getAsJsonArray("characters").size());
        assertEquals(4, document.getAsJsonArray("fields").size());
        assertEquals("12.500000000000000000",
                document.getAsJsonArray("fields").get(2).getAsJsonObject()
                        .get("value").getAsString());
        assertEquals(2, document.getAsJsonArray("items").size());
        assertEquals("node.entry",
                document.getAsJsonArray("maps").get(0).getAsJsonObject()
                        .get("partyNodeKey").getAsString());
        assertEquals(1,
                document.getAsJsonArray("maps").get(0).getAsJsonObject()
                        .getAsJsonObject("encounter").getAsJsonArray("participants").size());
        assertEquals(2, document.getAsJsonArray("recentEvents").size());
        assertTrue(document.getAsJsonArray("recentEvents").get(0).getAsJsonObject()
                .get("check").isJsonNull());
        assertEquals("check.skill",
                document.getAsJsonArray("recentEvents").get(1).getAsJsonObject()
                        .getAsJsonObject("check").get("checkKey").getAsString());
        assertFalse(new String(first.file().content(), StandardCharsets.UTF_8)
                .contains("rowVersion"));
    }

    @Test
    void usesExactStableRelationshipSchemaAndOmitsInternalOrHistoricalFields()
            throws Exception {
        CampaignArchiveExportService.Result result =
                service(Optional.of(snapshot())).export(CAMPAIGN_KEY);
        JsonObject document = JsonParser.parseString(
                new String(result.file().content(), StandardCharsets.UTF_8)).getAsJsonObject();

        assertKeys(document,
                "formatVersion", "campaign", "module", "characters", "fields",
                "classLevels", "skillProficiencies", "saveProficiencies",
                "items", "maps", "recentEvents");
        assertKeys(document.getAsJsonObject("campaign"),
                "campaignKey", "campaignName", "campaignStatus");
        assertKeys(document.getAsJsonObject("module"),
                "moduleKey", "releaseVersion", "contentSha256");
        assertKeys(document.getAsJsonArray("characters").get(0).getAsJsonObject(),
                "characterKey", "characterType", "characterName", "characterStatus");
        for (JsonElement value : document.getAsJsonArray("fields")) {
            assertKeys(value.getAsJsonObject(),
                    "characterKey", "fieldKey", "valueType", "value");
            assertEquals(CHARACTER_KEY,
                    value.getAsJsonObject().get("characterKey").getAsString());
        }
        assertStableCharacterRelations(document, "classLevels");
        assertStableCharacterRelations(document, "skillProficiencies");
        assertStableCharacterRelations(document, "saveProficiencies");
        assertStableCharacterRelations(document, "items");
        assertKeys(document.getAsJsonArray("classLevels").get(0).getAsJsonObject(),
                "characterKey", "classKey", "level");
        assertKeys(document.getAsJsonArray("skillProficiencies").get(0).getAsJsonObject(),
                "characterKey", "targetKey", "proficiencyKey");
        assertKeys(document.getAsJsonArray("saveProficiencies").get(0).getAsJsonObject(),
                "characterKey", "targetKey", "proficiencyKey");
        for (JsonElement value : document.getAsJsonArray("items")) {
            assertKeys(value.getAsJsonObject(),
                    "characterKey", "sourceKind", "itemKey", "itemName",
                    "itemDescription", "quantity", "itemStatus");
        }

        JsonObject map = document.getAsJsonArray("maps").get(0).getAsJsonObject();
        assertKeys(map, "mapKey", "mapType", "partyNodeKey", "encounter");
        JsonObject encounter = map.getAsJsonObject("encounter");
        assertKeys(encounter, "battleStatus", "participants");
        JsonObject participant = encounter.getAsJsonArray("participants")
                .get(0).getAsJsonObject();
        assertKeys(participant, "characterKey", "faction", "nodeKey");
        assertEquals(CHARACTER_KEY, participant.get("characterKey").getAsString());

        JsonObject note = document.getAsJsonArray("recentEvents").get(0).getAsJsonObject();
        JsonObject checkEvent = document.getAsJsonArray("recentEvents")
                .get(1).getAsJsonObject();
        assertKeys(note,
                "eventSequence", "eventType", "subjectCharacterKey", "eventText", "check");
        assertKeys(checkEvent,
                "eventSequence", "eventType", "subjectCharacterKey", "eventText", "check");
        assertTrue(note.get("subjectCharacterKey").isJsonNull());
        assertEquals(CHARACTER_KEY, checkEvent.get("subjectCharacterKey").getAsString());
        assertKeys(checkEvent.getAsJsonObject("check"),
                "eventKey", "checkKey", "rollModeKey", "modifierSourceKey",
                "manualName", "modifierValue", "totalValue", "difficultyClass",
                "checkResult");

        Set<String> propertyNames = new HashSet<>();
        collectPropertyNames(document, propertyNames);
        Set<String> forbidden = Set.of(
                "id", "rowVersion", "hostStateEpoch", "requestId", "requestDigest",
                "operationType", "operationStatus", "moduleDefinitions", "fieldChanges",
                "diceRolls", "effectPlans", "history", "createdAt", "updatedAt");
        assertTrue(propertyNames.stream().noneMatch(forbidden::contains), propertyNames.toString());
        assertTrue(propertyNames.stream().noneMatch(name -> name.endsWith("Id")),
                propertyNames.toString());
        Set<String> forbiddenSecurityFragments = Set.of(
                "session", "cookie", "csrf", "password", "credential", "secret",
                "privatekey", "certificate", "tomcat", "localpath", "database",
                "jdbc", "public", "player", "owner", "approval", "network", "epoch");
        assertTrue(propertyNames.stream().map(String::toLowerCase).noneMatch(name ->
                forbiddenSecurityFragments.stream().anyMatch(name::contains)),
                propertyNames.toString());
    }

    @Test
    void rejectsInvalidKeyBeforeReadingAndDistinguishesMissingCampaign() throws Exception {
        CountingRepository repository = new CountingRepository(Optional.empty());
        CampaignArchiveExportService service = new CampaignArchiveExportService(repository);

        CampaignArchiveExportService.Result invalid = service.export("not-a-key");
        CampaignArchiveExportService.Result missing = service.export(CAMPAIGN_KEY);

        assertEquals(CampaignArchiveExportService.Status.INVALID_REQUEST, invalid.status());
        assertEquals(CampaignArchiveExportService.Status.NOT_FOUND, missing.status());
        assertEquals(1, repository.calls);
        assertNull(invalid.file());
        assertNull(missing.file());
    }

    @Test
    void failsClosedForModuleDriftUnknownRelationshipAndMoreThanFiftyEvents()
            throws Exception {
        Snapshot valid = snapshot();
        ModuleBinding drifted = new ModuleBinding(
                valid.module().frozenModuleKey(), valid.module().frozenReleaseVersion(),
                "0".repeat(64), valid.module().releaseModuleKey(),
                valid.module().releaseVersion(), valid.module().canonicalFormatVersion(),
                valid.module().hashAlgorithm(), "0".repeat(64),
                valid.module().releaseStatus());
        assertInvalid(with(valid, drifted, valid.fields(), valid.recentEvents()));

        List<FieldValue> unknown = List.of(new FieldValue(
                "22222222-2222-4222-8222-222222222222", "hp.current", "INTEGER",
                null, 1L, null, null));
        assertInvalid(with(valid, valid.module(), unknown, valid.recentEvents()));

        List<EventSnapshot> tooMany = new ArrayList<>();
        for (int index = 1; index <= 51; index++) {
            tooMany.add(new EventSnapshot(index, "NOTE", null, "消息 " + index, null));
        }
        assertInvalid(with(valid, valid.module(), valid.fields(), tooMany));
    }

    @Test
    void rejectsMalformedTypedValueDuplicateRelationAndInvalidCheckResult()
            throws Exception {
        Snapshot valid = snapshot();
        FieldValue malformed = new FieldValue(
                CHARACTER_KEY, "hp.current", "INTEGER", "10", 10L, null, null);
        assertInvalid(with(valid, valid.module(), List.of(malformed), valid.recentEvents()));

        List<FieldValue> duplicate = List.of(valid.fields().getFirst(), valid.fields().getFirst());
        assertInvalid(with(valid, valid.module(), duplicate, valid.recentEvents()));

        CheckSnapshot impossible = new CheckSnapshot(
                null, "check.skill", "roll.normal", "skill.perception", null,
                4, 10, 12, "SUCCESS");
        List<EventSnapshot> events = List.of(
                new EventSnapshot(1, "CHECK_EXECUTED", CHARACTER_KEY, null, impossible));
        assertInvalid(with(valid, valid.module(), valid.fields(), events));

        ItemState item = valid.items().getFirst();
        assertInvalid(withItems(valid, List.of(new ItemState(
                item.characterKey(), item.sourceKind(), item.itemKey(), item.itemName(),
                item.itemDescription(), 1000, item.itemStatus()))));
    }

    private static void assertInvalid(Snapshot snapshot) throws Exception {
        CampaignArchiveExportService.Result result =
                service(Optional.of(snapshot)).export(CAMPAIGN_KEY);
        assertEquals(CampaignArchiveExportService.Status.INVALID_STATE, result.status());
        assertNull(result.file());
    }

    private static void assertKeys(JsonObject object, String... expected) {
        assertEquals(Set.of(expected), object.keySet());
    }

    private static void assertStableCharacterRelations(JsonObject document, String arrayName) {
        for (JsonElement value : document.getAsJsonArray(arrayName)) {
            assertEquals(CHARACTER_KEY,
                    value.getAsJsonObject().get("characterKey").getAsString());
        }
    }

    private static void collectPropertyNames(JsonElement value, Set<String> names) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                names.add(entry.getKey());
                collectPropertyNames(entry.getValue(), names);
            }
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(child -> collectPropertyNames(child, names));
        }
    }

    private static CampaignArchiveExportService service(Optional<Snapshot> snapshot) {
        return new CampaignArchiveExportService(ignored -> snapshot);
    }

    static Snapshot snapshot() {
        Campaign campaign = new Campaign(CAMPAIGN_KEY, "测试战役", "ACTIVE");
        ModuleBinding module = new ModuleBinding(
                CampaignCreationService.MODULE_KEY, CampaignCreationService.RELEASE_VERSION,
                SHA, CampaignCreationService.MODULE_KEY,
                CampaignCreationService.RELEASE_VERSION, 1, "SHA-256", SHA, "RELEASED");
        List<CharacterState> characters = List.of(new CharacterState(
                CHARACTER_KEY, "PC", "Aria", "ACTIVE",
                CampaignCreationService.MODULE_KEY,
                CampaignCreationService.RELEASE_VERSION, SHA));
        List<FieldValue> fields = List.of(
                new FieldValue(CHARACTER_KEY, "field.a_text", "TEXT",
                        "备注", null, null, null),
                new FieldValue(CHARACTER_KEY, "field.b_integer", "INTEGER",
                        null, 12L, null, null),
                new FieldValue(CHARACTER_KEY, "field.c_decimal", "DECIMAL",
                        null, null, new BigDecimal("12.500000000000000000"), null),
                new FieldValue(CHARACTER_KEY, "field.d_boolean", "BOOLEAN",
                        null, null, null, true));
        List<ClassLevel> classes = List.of(
                new ClassLevel(CHARACTER_KEY, "class.fighter", 2));
        List<Proficiency> skills = List.of(
                new Proficiency(CHARACTER_KEY, "skill.perception", "proficiency.full"));
        List<Proficiency> saves = List.of(
                new Proficiency(CHARACTER_KEY, "save.strength", "proficiency.full"));
        List<ItemState> items = List.of(
                new ItemState(CHARACTER_KEY, "MODULE", "item.backpack",
                        "背包", "常用背包", 2, "ACTIVE"),
                new ItemState(CHARACTER_KEY, "TEMPORARY", null,
                        "钥匙", "临时钥匙", 1, "ARCHIVED"));
        List<MapState> maps = List.of(new MapState(
                "map.tavern_cellar", "NODE", "node.entry",
                new Encounter("ACTIVE", List.of(
                        new Participant(CHARACTER_KEY, "ALLY", "node.cellar")))));
        List<EventSnapshot> events = List.of(
                new EventSnapshot(8, "NOTE", null, "进入地窖。", null),
                new EventSnapshot(9, "CHECK_EXECUTED", CHARACTER_KEY, "锁已打开。",
                        new CheckSnapshot(
                                "event.skill_check", "check.skill", "roll.normal",
                                "skill.perception", null, 4, 14, 12, "SUCCESS")));
        return new Snapshot(
                campaign, module, characters, fields, classes, skills, saves,
                items, maps, events);
    }

    private static Snapshot with(
            Snapshot original,
            ModuleBinding module,
            List<FieldValue> fields,
            List<EventSnapshot> events) {
        return new Snapshot(
                original.campaign(), module, original.characters(), fields,
                original.classLevels(), original.skillProficiencies(),
                original.saveProficiencies(), original.items(), original.maps(), events);
    }

    private static Snapshot withItems(Snapshot original, List<ItemState> items) {
        return new Snapshot(
                original.campaign(), original.module(), original.characters(), original.fields(),
                original.classLevels(), original.skillProficiencies(),
                original.saveProficiencies(), items, original.maps(), original.recentEvents());
    }

    private static final class CountingRepository implements CampaignArchiveRepository {
        private final Optional<Snapshot> result;
        private int calls;

        private CountingRepository(Optional<Snapshot> result) {
            this.result = result;
        }

        @Override
        public Optional<Snapshot> findByCampaignKey(String campaignKey) {
            calls++;
            return result;
        }
    }
}
