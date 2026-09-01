package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.persistence.CharacterModuleBindingRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CharacterLifecycleCommandServiceTest {
    private static final String CHARACTER = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String CAMPAIGN = "11111111-2222-4333-8444-555555555555";
    private static final String REQUEST = "123e4567-e89b-42d3-a456-426614174000";

    @Test
    void unpublishedFrozenIdentityFailsBeforeCatalogOrMutationAccess() throws Exception {
        AtomicBoolean catalogCalled = new AtomicBoolean();
        AtomicBoolean mutationCalled = new AtomicBoolean();
        ModuleCatalogRepository modules = (key, version) -> {
            catalogCalled.set(true);
            return Optional.<ModuleCatalog>empty();
        };
        CharacterModuleBindingRepository bindings = () -> List.of(
                new CharacterModuleBindingRepository.Binding(
                        CAMPAIGN, CHARACTER,
                        BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY,
                        BuiltinModuleReleaseRegistry.RELEASE_VERSION_1,
                        "a".repeat(64)));
        CharacterLifecycleCommandService service = new CharacterLifecycleCommandService(
                modules,
                command -> {
                    mutationCalled.set(true);
                    return null;
                },
                bindings);
        String digest = CharacterLifecycleRequestDigest.sha256(
                CHARACTER, 0, "RENAME", "Renamed");

        CharacterLifecycleCommandService.Result result = service.mutate(
                CHARACTER, "0", "RENAME", "Renamed", REQUEST, digest);

        assertEquals(CharacterLifecycleCommandService.Status.MODULE_UNAVAILABLE,
                result.status());
        assertFalse(catalogCalled.get());
        assertFalse(mutationCalled.get());
    }
}
