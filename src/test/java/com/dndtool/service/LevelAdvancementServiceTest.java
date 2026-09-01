package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndtool.persistence.LevelAdvancementRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LevelAdvancementServiceTest {
    private static final String CHARACTER = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String REQUEST = "123e4567-e89b-42d3-a456-426614174000";
    private static final String HASH = "a".repeat(64);

    @Test
    void previewConsumesNoRandomnessAndConfirmationPersistsServerRoll() throws Exception {
        FakeRepository repository = new FakeRepository();
        AtomicInteger rolls = new AtomicInteger();
        LevelAdvancementService service = service(repository, sides -> {
            rolls.incrementAndGet();
            return 7;
        });
        LevelAdvancementRules.Request request = request("SERVER_ROLL");

        LevelAdvancementService.PreviewResult preview = service.preview(request);
        assertEquals(LevelAdvancementService.Status.PREVIEW_READY, preview.status());
        assertEquals(0, rolls.get());
        String requestDigest = LevelAdvancementRequestDigest.sha256(
                CHARACTER, 2, "SERVER_ROLL", preview.prepared().previewDigestSha256());
        LevelAdvancementService.ConfirmResult result = service.confirm(request, 9, 4,
                preview.prepared().previewDigestSha256(), REQUEST, requestDigest);

        assertEquals(LevelAdvancementService.Status.ADVANCED, result.status());
        assertEquals(7, result.hitDieRoll());
        assertEquals(9, result.hitPointIncrease());
        assertEquals(1, rolls.get());
        assertEquals("ADVANCE_CHARACTER_LEVEL", repository.operationType);
    }

    @Test
    void staleRowVersionIsRejectedBeforeRandomness() throws Exception {
        FakeRepository repository = new FakeRepository();
        repository.status = LevelAdvancementRepository.Result.Status.STALE_ROW_VERSION;
        AtomicInteger rolls = new AtomicInteger();
        LevelAdvancementService service = service(repository, sides -> {
            rolls.incrementAndGet();
            return 1;
        });
        LevelAdvancementRules.Request request = request("SERVER_ROLL");
        LevelAdvancementRules.Prepared preview = service.preview(request).prepared();
        String digest = LevelAdvancementRequestDigest.sha256(
                CHARACTER, 2, "SERVER_ROLL", preview.previewDigestSha256());

        LevelAdvancementService.ConfirmResult result = service.confirm(
                request, 9, 4, preview.previewDigestSha256(), REQUEST, digest);

        assertEquals(LevelAdvancementService.Status.STALE_ROW_VERSION, result.status());
        assertEquals(0, rolls.get());
        assertNull(result.hitDieRoll());
    }

    @Test
    void standardProductionGateRejectsDraftRelease() throws Exception {
        FakeRepository repository = new FakeRepository();
        ModuleCatalog draft = catalog("DRAFT", null);
        LevelAdvancementService service = new LevelAdvancementService(
                (module, release) -> Optional.of(draft), repository);

        assertEquals(LevelAdvancementService.Status.MODULE_UNAVAILABLE,
                service.preview(request("FIXED_AVERAGE")).status());
        assertFalse(repository.confirmed);
    }

    @Test
    void completedRequestReplaysBeforeCurrentLevelIsRevalidated() throws Exception {
        FakeRepository repository = new FakeRepository();
        repository.completed = new LevelAdvancementRepository.Result(
                LevelAdvancementRepository.Result.Status.ALREADY_SUCCEEDED,
                CHARACTER, 5L, 7, 9);
        AtomicInteger rolls = new AtomicInteger();
        LevelAdvancementService service = service(repository, sides -> {
            rolls.incrementAndGet();
            return 1;
        });
        LevelAdvancementRules.Request request = request("SERVER_ROLL");
        String preview = "c".repeat(64);
        String digest = LevelAdvancementRequestDigest.sha256(
                CHARACTER, 2, "SERVER_ROLL", preview);

        LevelAdvancementService.ConfirmResult result = service.confirm(
                request, 9, 4, preview, REQUEST, digest);

        assertEquals(LevelAdvancementService.Status.ALREADY_SUCCEEDED, result.status());
        assertEquals(7, result.hitDieRoll());
        assertEquals(0, rolls.get());
        assertFalse(repository.confirmed);
    }

    private static LevelAdvancementService service(
            FakeRepository repository, java.util.function.IntUnaryOperator roller) {
        return new LevelAdvancementService(
                (module, release) -> Optional.of(catalog("RELEASED", HASH)), repository,
                new LevelAdvancementRules(), (catalog, context) -> HASH, roller);
    }

    private static LevelAdvancementRules.Request request(String hpChoice) {
        return new LevelAdvancementRules.Request(CHARACTER, 2, hpChoice);
    }

    private static ModuleCatalog catalog(String status, String hash) {
        ModuleCatalog.Release release = new ModuleCatalog.Release(
                "dnd5e2014_srd51_se", "1", 2, "SHA-256", hash, status);
        List<ModuleCatalog.CatalogDefinition> definitions = List.of(
                definition("character.class", "class.fighter", 1),
                definition("character.resource", "resource.fighter.action_surge", 1),
                definition("character.resource", "resource.fighter.second_wind", 2),
                definition("character.resource", "resource.hit_dice.d10", 3),
                definition("character.resource", "resource.hit_points", 4));
        List<ModuleCatalog.CatalogAttribute> attributes = List.of(
                integer("character.class", "class.fighter", "class.hit_die_sides", 10),
                text("character.class", "class.fighter", "class.proficiency_bonus_profile",
                        "1-4:2,5-8:3,9-12:4,13-16:5,17-20:6"),
                text("character.resource", "resource.fighter.action_surge",
                        "resource.maximum_profile", "2-16:1,17-20:2"),
                identifier("character.resource", "resource.fighter.action_surge",
                        "resource.execution_mode", "AUTOMATIC"),
                text("character.resource", "resource.fighter.action_surge",
                        "resource.recovery_profile", "1-20:SHORT_REST"),
                text("character.resource", "resource.fighter.second_wind",
                        "resource.maximum_profile", "1-20:1"),
                identifier("character.resource", "resource.fighter.second_wind",
                        "resource.execution_mode", "AUTOMATIC"),
                text("character.resource", "resource.fighter.second_wind",
                        "resource.recovery_profile", "1-20:SHORT_REST"));
        return new ModuleCatalog(release, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), definitions, attributes,
                List.of(owner("resource.fighter.action_surge"),
                        owner("resource.fighter.second_wind")));
    }

    private static ModuleCatalog.CatalogDefinition definition(String type, String key, int order) {
        return new ModuleCatalog.CatalogDefinition(type, key, key, key, order);
    }

    private static ModuleCatalog.CatalogAttribute integer(
            String type, String key, String attribute, long value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "INTEGER",
                new ModuleCatalog.IntegerValue(value));
    }

    private static ModuleCatalog.CatalogAttribute text(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "TEXT",
                new ModuleCatalog.TextValue(value));
    }

    private static ModuleCatalog.CatalogAttribute identifier(
            String type, String key, String attribute, String value) {
        return new ModuleCatalog.CatalogAttribute(type, key, attribute, 1, "IDENTIFIER",
                new ModuleCatalog.IdentifierValue(value));
    }

    private static ModuleCatalog.CatalogRelation owner(String resource) {
        return new ModuleCatalog.CatalogRelation("character.resource", resource,
                "resource.owner", "character.class", "class.fighter", 1);
    }

    private static final class FakeRepository implements LevelAdvancementRepository {
        private Result.Status status = Result.Status.ADVANCED;
        private boolean confirmed;
        private String operationType;
        private Result completed;

        @Override
        public Optional<PreviewContext> findPreviewContext(String characterKey) {
            return Optional.of(new PreviewContext(
                    "11111111-2222-4333-8444-555555555555", CHARACTER, 9, 4,
                    "dnd5e2014_srd51_se", "1", HASH, "class.fighter", 1, 1, 14, 8,
                    List.of(new ResourceState("resource.hit_points", 20, 20, false),
                            new ResourceState("resource.hit_dice.d10", 1, 1, false),
                            new ResourceState(
                                    "resource.fighter.second_wind", 1, 1, false))));
        }

        @Override
        public Optional<Result> findCompleted(String requestId, String requestDigestSha256) {
            return Optional.ofNullable(completed);
        }

        @Override
        public Result confirm(Command command, HitPointResolver resolver) {
            confirmed = true;
            operationType = "ADVANCE_CHARACTER_LEVEL";
            if (status != Result.Status.ADVANCED) {
                return new Result(status, null, null, null, null);
            }
            HitPointResolution hp = resolver.resolve(
                    command.hitDieSides(), command.constitutionModifier());
            return new Result(status, CHARACTER, 5L, hp.hitDieRoll(), hp.hitPointIncrease());
        }
    }
}
