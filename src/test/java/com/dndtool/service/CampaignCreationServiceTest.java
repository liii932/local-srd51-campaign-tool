package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.persistence.CampaignCreationRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignCreationServiceTest {
    private static final String MODULE_SHA =
            BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;
    private static final String REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final UUID CAMPAIGN_UUID =
            UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

    @Test
    void validatesModuleNormalizesNameAndPassesServerDigestToAtomicRepository()
            throws Exception {
        CapturingRepository persistence = new CapturingRepository(
                CampaignCreationRepository.Result.Status.CREATED);
        CampaignCreationService service = service(
                Optional.of(catalog("RELEASED", MODULE_SHA)), persistence, MODULE_SHA);
        String expectedName = "Café 冒险";
        String requestDigest = CampaignCreationRequestDigest.sha256(expectedName);

        CampaignCreationService.Result result = service.create(
                "  Cafe\u0301 冒险  ", REQUEST_ID, requestDigest);

        assertEquals(CampaignCreationService.Status.CREATED, result.status());
        assertEquals(CAMPAIGN_UUID.toString(), result.campaignKey());
        assertEquals(expectedName, persistence.command.campaignName());
        assertEquals(requestDigest, persistence.command.requestDigestSha256());
        assertEquals(MODULE_SHA, persistence.command.contentSha256());
        assertEquals(CampaignCreationService.MODULE_KEY, persistence.command.moduleKey());
    }

    @Test
    void rejectsMissingDraftOrHashMismatchedReleaseBeforeAnyWrite() throws Exception {
        CapturingRepository persistence = new CapturingRepository(
                CampaignCreationRepository.Result.Status.CREATED);
        String digest = CampaignCreationRequestDigest.sha256("战役");

        assertEquals(
                CampaignCreationService.Status.RELEASE_UNAVAILABLE,
                service(Optional.empty(), persistence, MODULE_SHA)
                        .create("战役", REQUEST_ID, digest).status());
        assertEquals(
                CampaignCreationService.Status.RELEASE_UNAVAILABLE,
                service(Optional.of(catalog("DRAFT", null)), persistence, MODULE_SHA)
                        .create("战役", REQUEST_ID, digest).status());
        assertEquals(
                CampaignCreationService.Status.MODULE_HASH_MISMATCH,
                service(Optional.of(catalog("RELEASED", MODULE_SHA)), persistence, "0".repeat(64))
                        .create("战役", REQUEST_ID, digest).status());
        assertNull(persistence.command);
    }

    @Test
    void rejectsInvalidNameIdentifierAndClientDigestBeforeReadingModule() throws Exception {
        CountingModuleRepository modules = new CountingModuleRepository(
                Optional.of(catalog("RELEASED", MODULE_SHA)));
        CapturingRepository persistence = new CapturingRepository(
                CampaignCreationRepository.Result.Status.CREATED);
        CampaignCreationService service = service(modules, persistence, MODULE_SHA);

        assertEquals(CampaignCreationService.Status.INVALID_REQUEST,
                service.create("   ", REQUEST_ID, "0".repeat(64)).status());
        assertEquals(CampaignCreationService.Status.INVALID_REQUEST,
                service.create("战役", "NOT_UUID", "0".repeat(64)).status());
        assertEquals(CampaignCreationService.Status.INVALID_REQUEST,
                service.create("战役", REQUEST_ID, "0".repeat(64)).status());

        assertEquals(0, modules.calls);
        assertNull(persistence.command);
    }

    @Test
    void mapsIdempotencyAndActiveCampaignResultsWithoutInventingKeys() throws Exception {
        String digest = CampaignCreationRequestDigest.sha256("战役");
        CapturingRepository replay = new CapturingRepository(
                CampaignCreationRepository.Result.Status.ALREADY_SUCCEEDED);
        replay.returnedCampaignKey = "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff";
        CampaignCreationService.Result replayed = service(
                Optional.of(catalog("RELEASED", MODULE_SHA)), replay, MODULE_SHA)
                .create("战役", REQUEST_ID, digest);
        assertEquals(CampaignCreationService.Status.ALREADY_SUCCEEDED, replayed.status());
        assertEquals(replay.returnedCampaignKey, replayed.campaignKey());

        CapturingRepository conflict = new CapturingRepository(
                CampaignCreationRepository.Result.Status.IDEMPOTENCY_CONFLICT);
        CampaignCreationService.Result conflicted = service(
                Optional.of(catalog("RELEASED", MODULE_SHA)), conflict, MODULE_SHA)
                .create("战役", REQUEST_ID, digest);
        assertEquals(CampaignCreationService.Status.IDEMPOTENCY_CONFLICT, conflicted.status());
        assertNull(conflicted.campaignKey());

        CapturingRepository active = new CapturingRepository(
                CampaignCreationRepository.Result.Status.ACTIVE_CAMPAIGN_EXISTS);
        assertEquals(CampaignCreationService.Status.ACTIVE_CAMPAIGN_EXISTS,
                service(Optional.of(catalog("RELEASED", MODULE_SHA)), active, MODULE_SHA)
                        .create("战役", REQUEST_ID, digest).status());
    }

    @Test
    void requestDigestIsDomainSeparatedAndSensitiveToNormalizedBusinessValue() {
        String first = CampaignCreationRequestDigest.sha256("战役一");
        String same = CampaignCreationRequestDigest.sha256("战役一");
        String changed = CampaignCreationRequestDigest.sha256("战役二");

        assertEquals(64, first.length());
        assertEquals(first, same);
        org.junit.jupiter.api.Assertions.assertNotEquals(first, changed);
    }

    private static CampaignCreationService service(
            Optional<ModuleCatalog> catalog,
            CampaignCreationRepository persistence,
            String actualHash) {
        return service(new CountingModuleRepository(catalog), persistence, actualHash);
    }

    private static CampaignCreationService service(
            ModuleCatalogRepository modules,
            CampaignCreationRepository persistence,
            String actualHash) {
        return new CampaignCreationService(
                modules,
                persistence,
                ignored -> actualHash,
                new BuiltinModuleHashManifest(),
                new CampaignCreationIdentityFactory(() -> CAMPAIGN_UUID));
    }

    private static ModuleCatalog catalog(String status, String storedHash) {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        CampaignCreationService.MODULE_KEY,
                        CampaignCreationService.RELEASE_VERSION,
                        1,
                        "SHA-256",
                        storedHash,
                        status),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static final class CountingModuleRepository implements ModuleCatalogRepository {
        private final Optional<ModuleCatalog> catalog;
        private int calls;

        private CountingModuleRepository(Optional<ModuleCatalog> catalog) {
            this.catalog = catalog;
        }

        @Override
        public Optional<ModuleCatalog> findByIdentity(String moduleKey, String releaseVersion) {
            calls++;
            return catalog;
        }
    }

    private static final class CapturingRepository implements CampaignCreationRepository {
        private final Result.Status returnedStatus;
        private Command command;
        private String returnedCampaignKey;

        private CapturingRepository(Result.Status returnedStatus) {
            this.returnedStatus = returnedStatus;
        }

        @Override
        public Result create(Command command) {
            this.command = command;
            String key = returnedStatus == Result.Status.CREATED
                    ? command.campaignKey()
                    : returnedCampaignKey;
            return new Result(returnedStatus, key);
        }
    }
}
