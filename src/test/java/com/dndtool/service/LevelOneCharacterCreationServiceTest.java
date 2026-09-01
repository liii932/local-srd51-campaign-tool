package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndtool.persistence.LevelOneCharacterCreationRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LevelOneCharacterCreationServiceTest {
    private static final String CAMPAIGN = "11111111-2222-4333-8444-555555555555";
    private static final String REQUEST = "123e4567-e89b-42d3-a456-426614174000";
    private static final String CHARACTER = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String HASH = "a".repeat(64);

    @Test
    void previewIsReadOnlyAndConfirmPersistsTheExactServerPreparedSnapshot() throws Exception {
        FakeRepository repository = new FakeRepository();
        LevelOneCharacterCreationService service = service(repository);

        LevelOneCharacterCreationService.PreviewResult preview = service.preview(request());
        assertEquals(LevelOneCharacterCreationService.Status.PREVIEW_READY, preview.status());
        assertNull(repository.command);

        String digest = LevelOneCharacterCreationRequestDigest.sha256(
                CAMPAIGN, "山丘守卫", preview.prepared().previewDigestSha256());
        LevelOneCharacterCreationService.ConfirmResult confirmed = service.confirm(
                request(), 7L, preview.prepared().previewDigestSha256(), REQUEST, digest);

        assertEquals(LevelOneCharacterCreationService.Status.CREATED, confirmed.status());
        assertEquals(CHARACTER, confirmed.characterKey());
        assertNotNull(repository.command);
        assertEquals(preview.prepared().finalAbilityScores(),
                repository.command.finalAbilityScores());
        assertEquals(13, repository.command.maximumHitPoints());
    }

    @Test
    void changedEventTailMakesPreviewStaleBeforeAnyAuthoritativeWrite() throws Exception {
        FakeRepository repository = new FakeRepository();
        LevelOneCharacterCreationService service = service(repository);
        var preview = service.preview(request()).prepared();
        repository.tail = 8L;
        String digest = LevelOneCharacterCreationRequestDigest.sha256(
                CAMPAIGN, "山丘守卫", preview.previewDigestSha256());

        var result = service.confirm(request(), 7L, preview.previewDigestSha256(), REQUEST, digest);

        assertEquals(LevelOneCharacterCreationService.Status.STALE_PREVIEW, result.status());
        assertNull(repository.command);
    }

    @Test
    void standardProductionGateRejectsTheDraftRelease() throws Exception {
        FakeRepository repository = new FakeRepository();
        LevelOneCharacterCreationService service = new LevelOneCharacterCreationService(
                (module, release) -> Optional.of(catalog("DRAFT", null)), repository);

        assertEquals(LevelOneCharacterCreationService.Status.MODULE_UNAVAILABLE,
                service.preview(request()).status());
        assertNull(repository.command);
    }

    private static LevelOneCharacterCreationService service(FakeRepository repository) {
        return new LevelOneCharacterCreationService(
                (module, release) -> Optional.of(catalog("RELEASED", HASH)), repository,
                new LevelOneCharacterRules(), (catalog, context) -> HASH,
                () -> UUID.fromString(CHARACTER));
    }

    private static LevelOneCharacterRules.Request request() {
        return new LevelOneCharacterRules.Request(CAMPAIGN, "山丘守卫", "race.dwarf",
                "subrace.hill_dwarf", "background.acolyte", "class.fighter", null,
                Map.of("ability.strength", 15, "ability.dexterity", 12,
                        "ability.constitution", 14, "ability.intelligence", 10,
                        "ability.wisdom", 13, "ability.charisma", 8),
                List.of(), List.of("skill.athletics"),
                List.of("language.celestial", "language.draconic"),
                List.of("tool.smith_tools"), List.of(
                        "starting.background.acolyte.equipment", "starting.class.fighter.a"));
    }

    private static ModuleCatalog catalog(String status, String hash) {
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", hash, status);
        List<ModuleCatalog.CatalogDefinition> definitions = List.of(
                definition("character.race", "race.dwarf"),
                definition("character.subrace", "subrace.hill_dwarf"),
                definition("character.background", "background.acolyte"),
                definition("character.class", "class.fighter"),
                definition("character.feature", "feature.fighter.second_wind"),
                definition("character.resource", "resource.hit_dice.d10"),
                new ModuleCatalog.CatalogDefinition("character.resource",
                        "resource.fighter.second_wind", "resource.fighter.second_wind",
                        "resource.fighter.second_wind", 2));
        List<ModuleCatalog.CatalogAttribute> attributes = List.of(
                profile("character.race", "race.dwarf",
                        "bonus=constitution+2|language=common,dwarvish|subrace=required|tool=1:brewer_supplies,mason_tools,smith_tools"),
                profile("character.subrace", "subrace.hill_dwarf", "bonus=wisdom+1"),
                profile("character.background", "background.acolyte",
                        "language=2:celestial,draconic|skill=insight,religion|start=1:starting.background.acolyte.equipment"),
                profile("character.class", "class.fighter",
                        "hp=10|save=constitution,strength|skill=1:athletics,history,insight|start=1:starting.class.fighter.a,starting.class.fighter.b"),
                new ModuleCatalog.CatalogAttribute("character.class", "class.fighter",
                        "creation.ability_method", 1, "IDENTIFIER",
                        new ModuleCatalog.IdentifierValue("ability.standard_array_v1")),
                new ModuleCatalog.CatalogAttribute("character.class", "class.fighter",
                        "class.hit_die_sides", 1, "INTEGER",
                        new ModuleCatalog.IntegerValue(10)),
                profileValue("character.class", "class.fighter",
                        "class.proficiency_bonus_profile",
                        "1-4:2,5-8:3,9-12:4,13-16:5,17-20:6"),
                new ModuleCatalog.CatalogAttribute("character.feature",
                        "feature.fighter.second_wind", "feature.level", 1, "INTEGER",
                        new ModuleCatalog.IntegerValue(1)),
                identifier("character.feature", "feature.fighter.second_wind",
                        "catalog.category", "BASE"),
                identifier("character.feature", "feature.fighter.second_wind",
                        "feature.execution_mode", "AUTOMATIC"),
                identifier("character.feature", "feature.fighter.second_wind",
                        "feature.execution_algorithm", "AUTOMATIC_RESOURCE_LIFECYCLE_V1"),
                profileValue("character.resource", "resource.fighter.second_wind",
                        "resource.maximum_profile", "1-20:1"),
                identifier("character.resource", "resource.fighter.second_wind",
                        "resource.execution_mode", "AUTOMATIC"),
                profileValue("character.resource", "resource.fighter.second_wind",
                        "resource.recovery_profile", "1-20:SHORT_REST"));
        return new ModuleCatalog(release,
                List.<ModuleCatalog.RuleConstant>of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), definitions, attributes,
                List.of(new ModuleCatalog.CatalogRelation("character.subrace",
                                "subrace.hill_dwarf", "subrace.parent_race", "character.race",
                                "race.dwarf", 1),
                        new ModuleCatalog.CatalogRelation("character.feature",
                                "feature.fighter.second_wind", "feature.owner",
                                "character.class", "class.fighter", 1),
                        new ModuleCatalog.CatalogRelation("character.resource",
                                "resource.fighter.second_wind", "resource.owner",
                                "character.class", "class.fighter", 1)));
    }

    private static ModuleCatalog.CatalogDefinition definition(String type, String key) {
        return new ModuleCatalog.CatalogDefinition(type, key, key, key, 1);
    }

    private static ModuleCatalog.CatalogAttribute profile(String type, String key, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, "creation.level_one_profile", 1,
                "TEXT", new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute profileValue(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1,
                "TEXT", new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute identifier(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1,
                "IDENTIFIER", new ModuleCatalog.IdentifierValue(value));
    }

    private static final class FakeRepository implements LevelOneCharacterCreationRepository {
        private long tail = 7L;
        private Command command;

        @Override
        public Optional<PreviewContext> findPreviewContext(String campaignKey) {
            return Optional.of(new PreviewContext(CAMPAIGN, tail,
                    "dnd5e2014_srd51_se", "1", HASH));
        }

        @Override
        public Result confirm(Command command) {
            this.command = command;
            return new Result(Result.Status.CREATED, command.characterKey(), 0L);
        }
    }
}
