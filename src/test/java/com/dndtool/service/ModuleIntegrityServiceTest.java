package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.persistence.CampaignModuleBindingRepository;
import com.dndtool.persistence.CharacterModuleBindingRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises the fail-closed comparison of actual, published and frozen module digests. */
final class ModuleIntegrityServiceTest {
    private static final String SHA =
            BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;

    @Test
    void validBuiltInReleaseIsRequiredEvenBeforeAnyCampaignExists() throws Exception {
        CountingModules modules = new CountingModules(Optional.of(catalog("RELEASED", SHA)));
        ModuleIntegrityService service = service(modules, List.of(), SHA);

        assertEquals(ModuleIntegrityService.Status.READY, service.verifyAll());
        assertEquals(1, modules.calls);
    }

    @Test
    void matchingFrozenCampaignBindingReusesVerifiedRelease() throws Exception {
        CountingModules modules = new CountingModules(Optional.of(catalog("RELEASED", SHA)));
        CampaignModuleBindingRepository.Binding binding = binding(
                CampaignCreationService.MODULE_KEY,
                CampaignCreationService.RELEASE_VERSION,
                SHA);

        assertEquals(ModuleIntegrityService.Status.READY,
                service(modules, List.of(binding), List.of(character(SHA)), SHA).verifyAll());
        assertEquals(1, modules.calls);
    }

    @Test
    void rejectsCharacterSavedIdentityOrHashThatDiffersFromItsCampaignBinding()
            throws Exception {
        CampaignModuleBindingRepository.Binding binding = binding(
                CampaignCreationService.MODULE_KEY,
                CampaignCreationService.RELEASE_VERSION,
                SHA);
        for (CharacterModuleBindingRepository.Binding character : List.of(
                character("0".repeat(64)),
                new CharacterModuleBindingRepository.Binding(
                        binding.campaignKey(), "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff",
                        "module.other", CampaignCreationService.RELEASE_VERSION, SHA),
                new CharacterModuleBindingRepository.Binding(
                        binding.campaignKey(), "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff",
                        CampaignCreationService.MODULE_KEY, "2", SHA),
                new CharacterModuleBindingRepository.Binding(
                        "cccccccc-dddd-4eee-8fff-aaaaaaaaaaaa",
                        "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff",
                        CampaignCreationService.MODULE_KEY,
                        CampaignCreationService.RELEASE_VERSION, SHA))) {
            ModuleIntegrityService service = service(
                    new CountingModules(Optional.of(catalog("RELEASED", SHA))),
                    List.of(binding), List.of(character), SHA);
            assertEquals(ModuleIntegrityService.Status.MODULE_HASH_MISMATCH,
                    service.verifyAll());
        }
    }

    @Test
    void rejectsMissingDraftPublishedActualAndFrozenHashes() throws Exception {
        assertMismatch(Optional.empty(), List.of(), SHA);
        assertMismatch(Optional.of(catalog("DRAFT", null)), List.of(), SHA);
        assertMismatch(Optional.of(catalog("RELEASED", "0".repeat(64))), List.of(), SHA);
        assertMismatch(Optional.of(catalog("RELEASED", SHA)), List.of(), "0".repeat(64));
        assertMismatch(Optional.of(catalog("RELEASED", SHA)),
                List.of(binding(CampaignCreationService.MODULE_KEY,
                        CampaignCreationService.RELEASE_VERSION, "0".repeat(64))), SHA);
    }

    @Test
    void missingCampaignModuleRowAndCanonicalFailureAreMismatches() throws Exception {
        CampaignModuleBindingRepository.Binding broken = new CampaignModuleBindingRepository.Binding(
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", "ACTIVE", null, null, null);
        assertMismatch(Optional.of(catalog("RELEASED", SHA)), List.of(broken), SHA);

        ModuleIntegrityService service = new ModuleIntegrityService(
                new CountingModules(Optional.of(catalog("RELEASED", SHA))),
                () -> List.of(),
                () -> List.of(),
                ignored -> { throw new ModuleCanonicalException(); },
                new BuiltinModuleHashManifest());
        assertEquals(ModuleIntegrityService.Status.MODULE_HASH_MISMATCH, service.verifyAll());
    }

    @Test
    void databaseFailureIsPropagatedForGenericAvailabilityHandling() {
        ModuleIntegrityService service = new ModuleIntegrityService(
                (key, version) -> { throw new SQLException("synthetic"); },
                () -> List.of(),
                () -> List.of());

        assertThrows(SQLException.class, service::verifyAll);
    }

    private static void assertMismatch(
            Optional<ModuleCatalog> catalog,
            List<CampaignModuleBindingRepository.Binding> bindings,
            String actual) throws Exception {
        ModuleIntegrityService service = service(new CountingModules(catalog), bindings, actual);
        assertEquals(ModuleIntegrityService.Status.MODULE_HASH_MISMATCH, service.verifyAll());
    }

    private static ModuleIntegrityService service(
            ModuleCatalogRepository modules,
            List<CampaignModuleBindingRepository.Binding> bindings,
            String actual) {
        return new ModuleIntegrityService(
                modules,
                () -> bindings,
                () -> List.of(),
                ignored -> actual,
                new BuiltinModuleHashManifest());
    }

    private static ModuleIntegrityService service(
            ModuleCatalogRepository modules,
            List<CampaignModuleBindingRepository.Binding> bindings,
            List<CharacterModuleBindingRepository.Binding> characters,
            String actual) {
        return new ModuleIntegrityService(
                modules,
                () -> bindings,
                () -> characters,
                ignored -> actual,
                new BuiltinModuleHashManifest());
    }

    private static CampaignModuleBindingRepository.Binding binding(
            String key, String version, String frozenSha) {
        return new CampaignModuleBindingRepository.Binding(
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                "ACTIVE",
                key,
                version,
                frozenSha);
    }

    private static CharacterModuleBindingRepository.Binding character(String savedSha) {
        return new CharacterModuleBindingRepository.Binding(
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff",
                CampaignCreationService.MODULE_KEY,
                CampaignCreationService.RELEASE_VERSION,
                savedSha);
    }

    private static ModuleCatalog catalog(String status, String publishedSha) {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        CampaignCreationService.MODULE_KEY,
                        CampaignCreationService.RELEASE_VERSION,
                        1,
                        "SHA-256",
                        publishedSha,
                        status),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static final class CountingModules implements ModuleCatalogRepository {
        private final Optional<ModuleCatalog> result;
        private int calls;

        private CountingModules(Optional<ModuleCatalog> result) {
            this.result = result;
        }

        @Override
        public Optional<ModuleCatalog> findByIdentity(String key, String version) {
            calls++;
            return result;
        }
    }
}
