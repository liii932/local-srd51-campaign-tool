package com.dndtool.service.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.service.storage.CampaignStorageService.Boundary;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CampaignPreparationTest {
    @TempDir Path temporary;
    private Path root() { return temporary.resolve("managed"); }

    @Test void unknownPreparationEntryPreventsRecoveryAndDiscardBeforeAnyMutation() throws Exception {
        Path preparation = interrupted(Boundary.AFTER_INPUT_WRITE);
        Files.writeString(preparation.resolve("personal.txt"), "keep");
        Set<String> before = names(preparation);
        try (var service = new CampaignStorageService(root())) {
            String id = service.status().residualRunIds().getFirst();
            assertEquals("CLEANUP_BLOCKED", assertThrows(StorageException.class, () -> service.recover(id)).code());
            assertEquals("CLEANUP_BLOCKED", assertThrows(StorageException.class, () -> service.discardResidual(id)).code());
            assertEquals(before, names(preparation));
        }
    }

    @Test void finalizedInputWithChangedIdentityOrDamagedBytesIsAlwaysRetained() throws Exception {
        Path preparation = interrupted(Boundary.AFTER_INPUT_WRITE);
        Path input = preparation.resolve("input.json"); byte[] bytes = Files.readAllBytes(input); bytes[bytes.length / 2] ^= 1;
        Files.write(input, bytes);
        try (var service = new CampaignStorageService(root())) {
            String id = service.status().residualRunIds().getFirst();
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.recover(id)).code());
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.discardResidual(id)).code());
            assertArrayEquals(bytes, Files.readAllBytes(input));
        }
    }

    @Test void completeForeignDocumentInPartialSlotCannotBeDiscardedAsTruncation() throws Exception {
        Path preparation = interrupted(Boundary.DURING_INPUT_WRITE);
        byte[] foreign = CampaignLifecycleFixture.fileBytes();
        Files.write(preparation.resolve("input.part"), foreign);
        try (var service = new CampaignStorageService(root())) {
            String id = service.status().residualRunIds().getFirst();
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.discardResidual(id)).code());
            assertArrayEquals(foreign, Files.readAllBytes(preparation.resolve("input.part")));
        }
    }

    @Test void wrongAllocationPrefixIsRetained() throws Exception {
        Path preparation = interrupted(Boundary.DURING_ALLOCATION_WRITE);
        Path part = preparation.resolve("allocation.part"); Files.writeString(part, "unrecognized");
        try (var service = new CampaignStorageService(root())) {
            String id = service.status().residualRunIds().getFirst();
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.recover(id)).code());
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.discardResidual(id)).code());
            assertEquals("unrecognized", Files.readString(part));
        }
    }

    @Test void allocationWriteInterruptedDuringRecoveryStaysStagedAndRecoversAgain() throws Exception {
        Path preparation = interrupted(Boundary.AFTER_INPUT_WRITE);
        String id;
        try (var service = new CampaignStorageService(root(), boundary -> {
            if (boundary == Boundary.DURING_ALLOCATION_WRITE) throw new IllegalStateException("interrupted recovery");
        })) {
            id = service.status().residualRunIds().getFirst();
            assertThrows(IllegalStateException.class, () -> service.recover(id));
            assertFalse(Files.exists(preparation.resolve("allocation.json")));
            assertTrue(Files.size(preparation.resolve("allocation.part")) > 0);
        }
        try (var service = new CampaignStorageService(root())) {
            assertEquals("原始输入", service.recover(id).name());
            assertEquals("ACTIVE", service.status().state());
        }
    }

    @Test void databaseInPreparationNamespaceIsNeverDeletedOrInitialized() throws Exception {
        Path preparation = interrupted(Boundary.AFTER_ALLOCATION_WRITE);
        Files.writeString(preparation.resolve("run.db"), "keep");
        try (var service = new CampaignStorageService(root())) {
            String id = service.status().residualRunIds().getFirst();
            assertEquals("CLEANUP_BLOCKED", assertThrows(StorageException.class, () -> service.recover(id)).code());
            assertEquals("CLEANUP_BLOCKED", assertThrows(StorageException.class, () -> service.discardResidual(id)).code());
            assertEquals("keep", Files.readString(preparation.resolve("run.db")));
        }
    }

    @Test void existingPublishedIdentityIsNeverReplacedByPreparationPromotion() throws Exception {
        Path preparation = interrupted(Boundary.AFTER_ALLOCATION_WRITE);
        String id = preparation.getFileName().toString().substring(9, 45);
        Path published = root().resolve("runs").resolve(id); ManagedCampaignFiles.createDirectory(published);
        Files.writeString(published.resolve("private.txt"), "keep");
        try (var service = new CampaignStorageService(root())) {
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.recover(id)).code());
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.discardResidual(id)).code());
            assertEquals("keep", Files.readString(published.resolve("private.txt")));
            assertTrue(Files.exists(preparation.resolve("input.json")));
        }
    }

    @Test void bareUuidEmptyDirectoryHasNoPreparationOwnership() throws Exception {
        try (var service = new CampaignStorageService(root())) {
            String id = UUID.randomUUID().toString(); Path unknown = root().resolve("runs").resolve(id); ManagedCampaignFiles.createDirectory(unknown);
            assertThrows(StorageException.class, () -> service.recover(id));
            assertThrows(StorageException.class, () -> service.discardResidual(id));
            assertTrue(Files.isDirectory(unknown));
        }
    }

    private Path interrupted(Boundary target) throws Exception {
        try (var service = new CampaignStorageService(root(), boundary -> { if (boundary == target) throw new IllegalStateException("injected"); })) {
            assertThrows(IllegalStateException.class, () -> CampaignRuleFixtures.create(service, "原始输入"));
            try (var entries = Files.list(root().resolve("runs"))) { return entries.findFirst().orElseThrow(); }
        }
    }
    private static Set<String> names(Path directory) throws Exception {
        try (var entries = Files.list(directory)) { return entries.map(p -> p.getFileName().toString()).collect(Collectors.toSet()); }
    }
}
