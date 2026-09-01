package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.persistence.CampaignModuleBindingRepository;
import com.dndtool.persistence.CharacterCreationRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CharacterCreationServiceTest {
    private static final String HASH =
            BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;
    private static final String CAMPAIGN = "11111111-2222-4333-8444-555555555555";
    private static final String REQUEST = "123e4567-e89b-42d3-a456-426614174000";
    private static final UUID CHARACTER =
            UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

    @Test
    void createsBlankPcWithModuleDefaultsAndExplicitNoneProficiencies() throws Exception {
        CapturingRepository persistence = new CapturingRepository();
        CharacterCreationService service = service(catalog(), persistence, HASH);
        String digest = CharacterCreationRequestDigest.sha256(CAMPAIGN, "PC", "Aria", null);

        CharacterCreationService.Result result = service.create(
                CAMPAIGN, "PC", "  Aria  ", "", REQUEST, digest);

        assertEquals(CharacterCreationService.Status.CREATED, result.status());
        assertEquals(CHARACTER.toString(), result.characterKey());
        assertEquals(0L, result.rowVersion());
        assertEquals("PC", persistence.command.characterType());
        assertNull(persistence.command.templateKey());
        assertEquals(List.of(10L, 1L), persistence.command.fieldValues().stream()
                .map(value -> ((ModuleCatalog.IntegerValue) value.value()).value()).toList());
        assertEquals(List.of("proficiency.none"), persistence.command.skillProficiencies()
                .stream().map(CharacterCreationRepository.Proficiency::proficiencyKey).toList());
        assertEquals(List.of("proficiency.none"), persistence.command.saveProficiencies()
                .stream().map(CharacterCreationRepository.Proficiency::proficiencyKey).toList());
    }

    @Test
    void additionalModuleFieldBecomesAnotherTypedValueWithoutChangingTheServiceShape()
            throws Exception {
        ModuleCatalog base = catalog();
        List<ModuleCatalog.FieldDefinition> fields = new ArrayList<>(base.fieldDefinitions());
        fields.add(new ModuleCatalog.FieldDefinition(
                "note.tactical", "战术备注", "TEXT",
                new ModuleCatalog.TextValue(""), null, null,
                null, null, "战术备注"));
        CapturingRepository persistence = new CapturingRepository();
        CharacterCreationService service = service(withFields(base, fields), persistence, HASH);
        String digest = CharacterCreationRequestDigest.sha256(CAMPAIGN, "PC", "Aria", null);

        CharacterCreationService.Result result = service.create(
                CAMPAIGN, "PC", "Aria", null, REQUEST, digest);

        assertEquals(CharacterCreationService.Status.CREATED, result.status());
        assertEquals(3, persistence.command.fieldValues().size());
        CharacterCreationRepository.FieldValue added =
                persistence.command.fieldValues().getLast();
        assertEquals("note.tactical", added.fieldKey());
        assertEquals("TEXT", added.valueType());
        assertEquals(new ModuleCatalog.TextValue(""), added.value());
    }

    @Test
    void createsNpcFromCompleteReviewedTemplate() throws Exception {
        CapturingRepository persistence = new CapturingRepository();
        CharacterCreationService service = service(catalog(), persistence, HASH);
        String digest = CharacterCreationRequestDigest.sha256(
                CAMPAIGN, "NPC", "守卫甲", "npc.guard");

        CharacterCreationService.Result result = service.create(
                CAMPAIGN, "NPC", "守卫甲", "npc.guard", REQUEST, digest);

        assertEquals(CharacterCreationService.Status.CREATED, result.status());
        assertEquals("npc.guard", persistence.command.templateKey());
        assertEquals(List.of(13L, 11L), persistence.command.fieldValues().stream()
                .map(value -> ((ModuleCatalog.IntegerValue) value.value()).value()).toList());
        assertEquals(List.of(new CharacterCreationRepository.ClassLevel("class.fighter", 1)),
                persistence.command.classLevels());
        assertEquals("proficiency.full",
                persistence.command.skillProficiencies().getFirst().proficiencyKey());
    }

    @Test
    void rejectsPcTemplateUnknownTemplateAndBadDigestBeforePersistence() throws Exception {
        CapturingRepository persistence = new CapturingRepository();
        CharacterCreationService service = service(catalog(), persistence, HASH);

        assertEquals(CharacterCreationService.Status.INVALID_REQUEST,
                service.create(CAMPAIGN, "PC", "Aria", "npc.guard", REQUEST,
                        CharacterCreationRequestDigest.sha256(
                                CAMPAIGN, "PC", "Aria", "npc.guard")).status());
        assertEquals(CharacterCreationService.Status.TEMPLATE_UNAVAILABLE,
                service.create(CAMPAIGN, "NPC", "未知", "npc.unknown", REQUEST,
                        CharacterCreationRequestDigest.sha256(
                                CAMPAIGN, "NPC", "未知", "npc.unknown")).status());
        assertEquals(CharacterCreationService.Status.INVALID_REQUEST,
                service.create(CAMPAIGN, "NPC", "守卫甲", "npc.guard", REQUEST,
                        "0".repeat(64)).status());
        assertNull(persistence.command);
    }

    @Test
    void rejectsModuleMismatchBeforePersistence() throws Exception {
        CapturingRepository persistence = new CapturingRepository();
        CharacterCreationService service = service(catalog(), persistence, "0".repeat(64));
        String digest = CharacterCreationRequestDigest.sha256(CAMPAIGN, "PC", "Aria", null);
        assertEquals(CharacterCreationService.Status.MODULE_HASH_MISMATCH,
                service.create(CAMPAIGN, "PC", "Aria", null, REQUEST, digest).status());
        assertNull(persistence.command);
    }

    @Test
    void unpublishedCampaignBindingFailsBeforeCatalogOrPersistence() throws Exception {
        AtomicBoolean catalogCalled = new AtomicBoolean();
        CapturingRepository persistence = new CapturingRepository();
        CharacterCreationService service = new CharacterCreationService(
                (key, version) -> {
                    catalogCalled.set(true);
                    return Optional.empty();
                },
                persistence,
                new CharacterCreationIdentityFactory(() -> CHARACTER),
                ignored -> HASH,
                new BuiltinModuleHashManifest(),
                () -> List.of(new CampaignModuleBindingRepository.Binding(
                        CAMPAIGN, "ACTIVE",
                        BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY,
                        BuiltinModuleReleaseRegistry.RELEASE_VERSION_1,
                        "a".repeat(64))),
                new BuiltinModuleReleaseRegistry());
        String digest = CharacterCreationRequestDigest.sha256(
                CAMPAIGN, "PC", "Aria", null);

        assertEquals(CharacterCreationService.Status.MODULE_UNAVAILABLE,
                service.create(CAMPAIGN, "PC", "Aria", null, REQUEST, digest).status());
        assertFalse(catalogCalled.get());
        assertNull(persistence.command);
    }

    @Test
    void requestDigestSeparatesTemplateTypeAndName() {
        String blank = CharacterCreationRequestDigest.sha256(CAMPAIGN, "NPC", "守卫", null);
        assertEquals(blank,
                CharacterCreationRequestDigest.sha256(CAMPAIGN, "NPC", "守卫", ""));
        org.junit.jupiter.api.Assertions.assertNotEquals(blank,
                CharacterCreationRequestDigest.sha256(
                        CAMPAIGN, "NPC", "守卫", "npc.guard"));
        org.junit.jupiter.api.Assertions.assertNotEquals(blank,
                CharacterCreationRequestDigest.sha256(CAMPAIGN, "PC", "守卫", null));
    }

    private static CharacterCreationService service(
            ModuleCatalog catalog, CapturingRepository persistence, String actualHash) {
        CharacterCreationIdentityFactory identity =
                new CharacterCreationIdentityFactory(() -> CHARACTER);
        return new CharacterCreationService(
                (key, version) -> Optional.of(catalog),
                persistence,
                identity,
                ignored -> actualHash,
                new BuiltinModuleHashManifest(),
                () -> List.of(new CampaignModuleBindingRepository.Binding(
                        CAMPAIGN, "ACTIVE", CampaignCreationService.MODULE_KEY,
                        CampaignCreationService.RELEASE_VERSION, HASH)),
                new BuiltinModuleReleaseRegistry());
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        CampaignCreationService.MODULE_KEY,
                        CampaignCreationService.RELEASE_VERSION,
                        1, "SHA-256", HASH, "RELEASED"),
                List.of(),
                List.of(
                        new ModuleCatalog.FieldDefinition(
                                "ability.strength", "力量", "INTEGER",
                                new ModuleCatalog.IntegerValue(10),
                                new ModuleCatalog.IntegerValue(1),
                                new ModuleCatalog.IntegerValue(30), null, null, "力量"),
                        new ModuleCatalog.FieldDefinition(
                                "hp.maximum", "HP", "INTEGER",
                                new ModuleCatalog.IntegerValue(1),
                                new ModuleCatalog.IntegerValue(1),
                                new ModuleCatalog.IntegerValue(999), null, null, "HP")),
                List.of(new ModuleCatalog.ClassDefinition("class.fighter", "战士")),
                List.of(
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.none", "NONE", 0, 1, "EXACT"),
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.full", "FULL", 1, 1, "EXACT")),
                List.of(),
                List.of(new ModuleCatalog.SkillDefinition(
                        "skill.athletics", "运动", "ability.strength")),
                List.of(new ModuleCatalog.SaveDefinition(
                        "save.strength", "ability.strength")),
                List.of(),
                List.of(new ModuleCatalog.EntityTemplate("npc.guard", "守卫")),
                List.of(
                        new ModuleCatalog.EntityTemplateValue(
                                "npc.guard", "ability.strength", "INTEGER",
                                new ModuleCatalog.IntegerValue(13)),
                        new ModuleCatalog.EntityTemplateValue(
                                "npc.guard", "hp.maximum", "INTEGER",
                                new ModuleCatalog.IntegerValue(11))),
                List.of(new ModuleCatalog.EntityTemplateClassLevel(
                        "npc.guard", "class.fighter", 1)),
                List.of(
                        new ModuleCatalog.EntityTemplateProficiency(
                                "npc.guard", "SKILL", "skill.athletics", "proficiency.full"),
                        new ModuleCatalog.EntityTemplateProficiency(
                                "npc.guard", "SAVING_THROW", "save.strength", "proficiency.none")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    /** Copies one immutable catalog while replacing only its field-definition data rows. */
    private static ModuleCatalog withFields(
            ModuleCatalog source, List<ModuleCatalog.FieldDefinition> fields) {
        return new ModuleCatalog(
                source.release(),
                source.ruleConstants(),
                fields,
                source.classDefinitions(),
                source.proficiencyTiers(),
                source.proficiencyBonusBands(),
                source.skillDefinitions(),
                source.saveDefinitions(),
                source.itemTemplates(),
                source.entityTemplates(),
                source.entityTemplateValues(),
                source.entityTemplateClassLevels(),
                source.entityTemplateProficiencies(),
                source.checkDefinitions(),
                source.rollModes(),
                source.eventTemplates(),
                source.eventChecks(),
                source.eventEffects(),
                source.effectDefinitions(),
                source.effectParameters(),
                source.mapDefinitions(),
                source.mapNodes(),
                source.mapConnections());
    }

    private static final class CapturingRepository implements CharacterCreationRepository {
        private Command command;

        @Override
        public Result create(Command command) {
            this.command = command;
            return new Result(Result.Status.CREATED, command.characterKey(), 0L);
        }
    }
}
