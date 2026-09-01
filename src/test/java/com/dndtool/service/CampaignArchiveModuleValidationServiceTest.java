package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CampaignArchiveModuleValidationServiceTest {
    private static final String SHA =
            BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;

    @Test
    void acceptsExactReleaseAndAllFrozenCatalogReferences() throws Exception {
        ModuleCatalog catalog = catalog(release("RELEASED", SHA));
        CountingRepository repository = new CountingRepository(Optional.of(catalog));

        CampaignArchiveModuleValidationService.Result result =
                service(repository, SHA).validate(validDocument());

        assertEquals(CampaignArchiveModuleValidationService.Status.READY, result.status());
        assertSame(catalog, result.catalog());
        assertEquals(1, repository.calls);
        assertEquals(CampaignCreationService.MODULE_KEY, repository.lastKey);
        assertEquals(CampaignCreationService.RELEASE_VERSION, repository.lastVersion);
    }

    @Test
    void rejectsMissingUnpublishedAndUnapprovedRelease() throws Exception {
        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_UNAVAILABLE,
                service(new CountingRepository(Optional.empty()), SHA), validDocument());
        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_UNAVAILABLE,
                service(new CountingRepository(Optional.of(catalog(release("DRAFT", SHA)))), SHA),
                validDocument());

        CampaignArchiveModuleValidationService unapproved =
                new CampaignArchiveModuleValidationService(
                        new CountingRepository(Optional.of(catalog(release("RELEASED", SHA)))),
                        ignored -> SHA,
                        ignored -> Optional.empty());
        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_UNAVAILABLE,
                unapproved, validDocument());

        CampaignArchiveDocument source = validDocument();
        CampaignArchiveDocument differentVersion = withModule(source,
                new CampaignArchiveDocument.ModuleReference(
                        source.module().moduleKey(), "2", source.module().contentSha256()));
        CountingRepository missingVersion = new CountingRepository(Optional.empty());
        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_UNAVAILABLE,
                service(missingVersion, SHA), differentVersion);
        assertEquals(0, missingVersion.calls);
        assertNull(missingVersion.lastVersion);
    }

    @Test
    void rejectsArchivePublishedActualAndCanonicalHashDrift() throws Exception {
        ModuleCatalog validCatalog = catalog(release("RELEASED", SHA));
        CampaignArchiveDocument source = validDocument();
        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_HASH_MISMATCH,
                service(new CountingRepository(Optional.of(validCatalog)), SHA),
                withModule(source, new CampaignArchiveDocument.ModuleReference(
                        source.module().moduleKey(), source.module().releaseVersion(),
                        "0".repeat(64))));

        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_HASH_MISMATCH,
                service(new CountingRepository(Optional.of(
                        catalog(release("RELEASED", "0".repeat(64))))), SHA), source);
        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_HASH_MISMATCH,
                service(new CountingRepository(Optional.of(validCatalog)), "0".repeat(64)), source);

        CampaignArchiveModuleValidationService canonicalFailure =
                new CampaignArchiveModuleValidationService(
                        new CountingRepository(Optional.of(validCatalog)),
                        ignored -> { throw new ModuleCanonicalException(); },
                        new BuiltinModuleHashManifest());
        assertStatus(CampaignArchiveModuleValidationService.Status.MODULE_HASH_MISMATCH,
                canonicalFailure, source);
    }

    @Test
    void rejectsUnknownFrozenDirectoryKeysAndRelationships() throws Exception {
        CampaignArchiveModuleValidationService service = service(
                new CountingRepository(Optional.of(catalog(release("RELEASED", SHA)))), SHA);
        CampaignArchiveDocument source = validDocument();
        String character = source.characters().getFirst().characterKey();

        assertInvalidReference(service, withFields(source, List.of(
                new CampaignArchiveDocument.FieldValue(
                        character, "field.unknown", "TEXT", "备注", null, null, null))));
        assertInvalidReference(service, withClassLevels(source, List.of(
                new CampaignArchiveDocument.ClassLevel(character, "class.unknown", 2))));
        assertInvalidReference(service, withSkillProficiencies(source, List.of(
                new CampaignArchiveDocument.Proficiency(
                        character, "skill.unknown", "proficiency.full"))));
        assertInvalidReference(service, withSkillProficiencies(source, List.of(
                new CampaignArchiveDocument.Proficiency(
                        character, "skill.perception", "proficiency.unknown"))));
        assertInvalidReference(service, withSaveProficiencies(source, List.of(
                new CampaignArchiveDocument.Proficiency(
                        character, "save.unknown", "proficiency.full"))));
        assertInvalidReference(service, withItems(source, List.of(
                new CampaignArchiveDocument.ItemState(
                        character, "MODULE", "item.unknown", "背包", "常用背包", 2,
                        "ACTIVE"))));

        CampaignArchiveDocument.MapState map = source.maps().getFirst();
        assertInvalidReference(service, withMaps(source, List.of(
                new CampaignArchiveDocument.MapState(
                        map.mapKey(), map.mapType(), "node.unknown", map.encounter()))));
        assertInvalidReference(service, withMaps(source, List.of(
                new CampaignArchiveDocument.MapState(
                        map.mapKey(), map.mapType(), map.partyNodeKey(),
                        new CampaignArchiveDocument.Encounter("ACTIVE", List.of(
                                new CampaignArchiveDocument.Participant(
                                        character, "ALLY", "node.unknown")))))));

        CampaignArchiveDocument.EventSnapshot event = source.recentEvents().getLast();
        CampaignArchiveDocument.CheckSnapshot check = event.check();
        assertInvalidReference(service, withEvents(source, List.of(
                source.recentEvents().getFirst(),
                new CampaignArchiveDocument.EventSnapshot(
                        event.eventSequence(), event.eventType(), event.subjectCharacterKey(),
                        event.eventText(),
                        new CampaignArchiveDocument.CheckSnapshot(
                                check.eventKey(), check.checkKey(), "roll.unknown",
                                check.modifierSourceKey(), check.manualName(),
                                check.modifierValue(), check.totalValue(),
                                check.difficultyClass(), check.checkResult())))));
        assertInvalidReference(service, withEvents(source, List.of(
                source.recentEvents().getFirst(), checkedEvent(event,
                        new CampaignArchiveDocument.CheckSnapshot(
                                "event.unknown", check.checkKey(), check.rollModeKey(),
                                check.modifierSourceKey(), check.manualName(),
                                check.modifierValue(), check.totalValue(),
                                check.difficultyClass(), check.checkResult())))));
        assertInvalidReference(service, withEvents(source, List.of(
                source.recentEvents().getFirst(), checkedEvent(event,
                        new CampaignArchiveDocument.CheckSnapshot(
                                check.eventKey(), "check.unknown", check.rollModeKey(),
                                check.modifierSourceKey(), check.manualName(),
                                check.modifierValue(), check.totalValue(),
                                check.difficultyClass(), check.checkResult())))));
        assertInvalidReference(service, withEvents(source, List.of(
                source.recentEvents().getFirst(), checkedEvent(event,
                        new CampaignArchiveDocument.CheckSnapshot(
                                check.eventKey(), check.checkKey(), check.rollModeKey(),
                                "skill.unknown", check.manualName(),
                                check.modifierValue(), check.totalValue(),
                                check.difficultyClass(), check.checkResult())))));
    }

    @Test
    void rejectsFieldBoundsModuleItemSnapshotAndEventRelationshipDrift() throws Exception {
        CampaignArchiveModuleValidationService service = service(
                new CountingRepository(Optional.of(catalog(release("RELEASED", SHA)))), SHA);
        CampaignArchiveDocument source = validDocument();
        String character = source.characters().getFirst().characterKey();

        assertInvalidReference(service, withFields(source, List.of(
                new CampaignArchiveDocument.FieldValue(
                        character, "field.b_integer", "INTEGER", null, 31L, null, null))));
        assertInvalidReference(service, withFields(source, List.of(
                new CampaignArchiveDocument.FieldValue(
                        character, "field.b_integer", "TEXT", "12", null, null, null))));
        assertInvalidReference(service, withItems(source, List.of(
                new CampaignArchiveDocument.ItemState(
                        character, "MODULE", "item.backpack", "背包", "被篡改", 2,
                        "ACTIVE"))));

        CampaignArchiveDocument.EventSnapshot event = source.recentEvents().getLast();
        CampaignArchiveDocument.CheckSnapshot check = event.check();
        assertInvalidReference(service, withEvents(source, List.of(
                source.recentEvents().getFirst(),
                new CampaignArchiveDocument.EventSnapshot(
                        event.eventSequence(), event.eventType(), event.subjectCharacterKey(),
                        event.eventText(),
                        new CampaignArchiveDocument.CheckSnapshot(
                                "event.note", check.checkKey(), check.rollModeKey(),
                                check.modifierSourceKey(), check.manualName(),
                                check.modifierValue(), check.totalValue(),
                                check.difficultyClass(), check.checkResult())))));
    }

    @Test
    void propagatesDatabaseFailureForLaterAvailabilityMapping() {
        CampaignArchiveModuleValidationService service =
                new CampaignArchiveModuleValidationService((key, version) -> {
                    throw new SQLException("synthetic");
                });

        assertThrows(SQLException.class, () -> service.validate(validDocument()));
    }

    private static void assertInvalidReference(
            CampaignArchiveModuleValidationService service,
            CampaignArchiveDocument document) throws Exception {
        assertStatus(CampaignArchiveModuleValidationService.Status.INVALID_CATALOG_REFERENCE,
                service, document);
    }

    private static CampaignArchiveDocument.EventSnapshot checkedEvent(
            CampaignArchiveDocument.EventSnapshot source,
            CampaignArchiveDocument.CheckSnapshot check) {
        return new CampaignArchiveDocument.EventSnapshot(
                source.eventSequence(), source.eventType(), source.subjectCharacterKey(),
                source.eventText(), check);
    }

    private static void assertStatus(
            CampaignArchiveModuleValidationService.Status expected,
            CampaignArchiveModuleValidationService service,
            CampaignArchiveDocument document) throws Exception {
        CampaignArchiveModuleValidationService.Result result = service.validate(document);
        assertEquals(expected, result.status());
        assertNull(result.catalog());
    }

    private static CampaignArchiveModuleValidationService service(
            ModuleCatalogRepository repository, String actualHash) {
        return new CampaignArchiveModuleValidationService(
                repository, ignored -> actualHash, new BuiltinModuleHashManifest());
    }

    private static CampaignArchiveDocument validDocument() {
        String json = CampaignArchiveJsonWriter.write(CampaignArchiveExportServiceTest.snapshot());
        CampaignArchiveReader.Result result = new CampaignArchiveReader().read(
                json.getBytes(StandardCharsets.UTF_8));
        if (result.status() != CampaignArchiveReader.Status.READY) {
            throw new IllegalStateException("Test archive did not pass the strict reader");
        }
        return result.document();
    }

    private static ModuleCatalog.Release release(String status, String contentSha256) {
        return new ModuleCatalog.Release(
                CampaignCreationService.MODULE_KEY,
                CampaignCreationService.RELEASE_VERSION,
                1,
                "SHA-256",
                contentSha256,
                status);
    }

    private static ModuleCatalog catalog(ModuleCatalog.Release release) {
        return new ModuleCatalog(
                release,
                List.of(),
                List.of(
                        new ModuleCatalog.FieldDefinition(
                                "field.a_text", "文本", "TEXT",
                                new ModuleCatalog.TextValue(""), null, null, null, null, ""),
                        new ModuleCatalog.FieldDefinition(
                                "field.b_integer", "整数", "INTEGER",
                                new ModuleCatalog.IntegerValue(10),
                                new ModuleCatalog.IntegerValue(1),
                                new ModuleCatalog.IntegerValue(30), null, null, ""),
                        new ModuleCatalog.FieldDefinition(
                                "field.c_decimal", "小数", "DECIMAL",
                                new ModuleCatalog.DecimalValue(BigDecimal.ZERO),
                                new ModuleCatalog.DecimalValue(BigDecimal.ZERO),
                                new ModuleCatalog.DecimalValue(new BigDecimal("100")),
                                null, null, ""),
                        new ModuleCatalog.FieldDefinition(
                                "field.d_boolean", "布尔", "BOOLEAN",
                                new ModuleCatalog.BooleanValue(false), null, null, null, null, "")),
                List.of(new ModuleCatalog.ClassDefinition("class.fighter", "战士")),
                List.of(new ModuleCatalog.ProficiencyTier(
                        "proficiency.full", "FULL", 1, 1, "EXACT")),
                List.of(),
                List.of(new ModuleCatalog.SkillDefinition(
                        "skill.perception", "察觉", "field.b_integer")),
                List.of(new ModuleCatalog.SaveDefinition(
                        "save.strength", "field.b_integer")),
                List.of(new ModuleCatalog.ItemTemplate(
                        "item.backpack", "背包", "常用背包")),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.CheckDefinition(
                        "check.skill", "SKILL", "SKILL_BONUS_V1")),
                List.of(new ModuleCatalog.RollMode(
                        "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1")),
                List.of(
                        new ModuleCatalog.EventTemplate("event.note", "记录说明"),
                        new ModuleCatalog.EventTemplate("event.skill_check", "技能检定")),
                List.of(new ModuleCatalog.EventCheck("event.skill_check", "check.skill")),
                List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.entry", "入口"),
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar", "地窖")),
                List.of());
    }

    private static CampaignArchiveDocument withModule(
            CampaignArchiveDocument source,
            CampaignArchiveDocument.ModuleReference module) {
        return copy(source, module, source.fields(), source.classLevels(),
                source.skillProficiencies(), source.items(), source.maps(), source.recentEvents());
    }

    private static CampaignArchiveDocument withFields(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.FieldValue> fields) {
        return copy(source, source.module(), fields, source.classLevels(),
                source.skillProficiencies(), source.items(), source.maps(), source.recentEvents());
    }

    private static CampaignArchiveDocument withClassLevels(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.ClassLevel> classes) {
        return copy(source, source.module(), source.fields(), classes,
                source.skillProficiencies(), source.items(), source.maps(), source.recentEvents());
    }

    private static CampaignArchiveDocument withSkillProficiencies(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.Proficiency> skills) {
        return copy(source, source.module(), source.fields(), source.classLevels(), skills,
                source.items(), source.maps(), source.recentEvents());
    }

    private static CampaignArchiveDocument withSaveProficiencies(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.Proficiency> saves) {
        return new CampaignArchiveDocument(
                source.formatVersion(), source.campaign(), source.module(), source.characters(),
                source.fields(), source.classLevels(), source.skillProficiencies(), saves,
                source.items(), source.maps(), source.recentEvents());
    }

    private static CampaignArchiveDocument withItems(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.ItemState> items) {
        return copy(source, source.module(), source.fields(), source.classLevels(),
                source.skillProficiencies(), items, source.maps(), source.recentEvents());
    }

    private static CampaignArchiveDocument withMaps(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.MapState> maps) {
        return copy(source, source.module(), source.fields(), source.classLevels(),
                source.skillProficiencies(), source.items(), maps, source.recentEvents());
    }

    private static CampaignArchiveDocument withEvents(
            CampaignArchiveDocument source,
            List<CampaignArchiveDocument.EventSnapshot> events) {
        return copy(source, source.module(), source.fields(), source.classLevels(),
                source.skillProficiencies(), source.items(), source.maps(), events);
    }

    private static CampaignArchiveDocument copy(
            CampaignArchiveDocument source,
            CampaignArchiveDocument.ModuleReference module,
            List<CampaignArchiveDocument.FieldValue> fields,
            List<CampaignArchiveDocument.ClassLevel> classes,
            List<CampaignArchiveDocument.Proficiency> skills,
            List<CampaignArchiveDocument.ItemState> items,
            List<CampaignArchiveDocument.MapState> maps,
            List<CampaignArchiveDocument.EventSnapshot> events) {
        return new CampaignArchiveDocument(
                source.formatVersion(), source.campaign(), module, source.characters(), fields,
                classes, skills, source.saveProficiencies(), items, maps, events);
    }

    private static final class CountingRepository implements ModuleCatalogRepository {
        private final Optional<ModuleCatalog> result;
        private int calls;
        private String lastKey;
        private String lastVersion;

        private CountingRepository(Optional<ModuleCatalog> result) {
            this.result = result;
        }

        @Override
        public Optional<ModuleCatalog> findByIdentity(String moduleKey, String releaseVersion) {
            calls++;
            lastKey = moduleKey;
            lastVersion = releaseVersion;
            return result;
        }
    }
}
