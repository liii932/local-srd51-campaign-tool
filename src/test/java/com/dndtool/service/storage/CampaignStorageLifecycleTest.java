package com.dndtool.service.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.service.storage.CampaignStorageService.Boundary;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Precise file ownership, publication, explicit loss and shutdown boundaries. */
class CampaignStorageLifecycleTest {
    @TempDir Path temporary;
    private static final List<String> RULES = List.of(CampaignRules.BASE, CampaignRules.HEALING);
    private Path root() { return temporary.resolve("managed"); }
    private static String id() { return UUID.randomUUID().toString(); }

    @Test void importPreservesPortableIdentityAndBytesWithoutAllocatingOrActivating() throws Exception {
        byte[] bytes = fileBytes(); var expected = new CampaignCodec().decode(bytes);
        try (var service = new CampaignStorageService(root())) {
            var imported = service.importFile(bytes);
            assertEquals(expected.fileKey(), imported.fileKey());
            assertEquals(expected.snapshot().name(), imported.name());
            assertEquals(expected.snapshot().history().size(), imported.eventTail());
            assertEquals(CampaignJson.sha256(bytes), imported.sha256());
            assertEquals("IDLE", service.status().state());
            assertTrue(service.status().runId().isEmpty());
            assertEquals(List.of(expected.fileKey()), service.status().saveFiles());
            assertTrue(names(root().resolve("runs")).isEmpty());
            assertEquals(Set.of(expected.fileKey() + ".json"), names(root().resolve("saves")));
            assertArrayEquals(bytes, Files.readAllBytes(service.savedFile(expected.fileKey())));
            var opened = service.open(expected.fileKey());
            assertEquals(expected.snapshot().campaignId(), opened.campaignId());
            assertEquals(expected.snapshot().history().size(), opened.history().size());
        }
    }

    @Test void damagedOrOversizedInputHasNoFilesystemEffects() throws Exception {
        byte[] damaged = fileBytes(); damaged[damaged.length / 2] ^= 1;
        try (var service = new CampaignStorageService(root())) {
            assertThrows(StorageException.class, () -> service.importFile(damaged));
            assertCode("INVALID_FILE", () -> service.importFile(null));
            assertCode("INVALID_FILE", () -> service.importFile(new byte[CampaignJson.MAX_BYTES + 1]));
            assertTrue(names(root().resolve("saves")).isEmpty());
            assertTrue(names(root().resolve("runs")).isEmpty());
        }
    }

    @Test void fileIdentityConflictNeverOverwritesExistingBytes() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        try (var service = new CampaignStorageService(root())) {
            service.importFile(bytes);
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes));
            assertArrayEquals(bytes, Files.readAllBytes(root().resolve("saves").resolve(key + ".json")));
            assertEquals(Set.of(key + ".json"), names(root().resolve("saves")));
        }
    }

    @Test void interruptedImportBeforePublicationCanReuseOnlyItsExactVerifiedStagingBytes() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        AtomicReference<Boundary> fault = new AtomicReference<>(Boundary.AFTER_IMPORT_TEMP);
        try (var service = faultService(fault)) {
            assertThrows(IllegalStateException.class, () -> service.importFile(bytes));
            assertEquals(Set.of(key + ".tmp"), names(root().resolve("saves")));
            assertTrue(service.status().saveFiles().isEmpty());
            assertEquals(key, service.importFile(bytes).fileKey());
            assertEquals(Set.of(key + ".json"), names(root().resolve("saves")));
        }
    }

    @Test void interruptedImportAfterPublicationRetainsTheCompleteFileWithoutClaimingAnotherImport() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        AtomicReference<Boundary> fault = new AtomicReference<>(Boundary.AFTER_IMPORT_PUBLISH);
        try (var service = faultService(fault)) {
            assertThrows(IllegalStateException.class, () -> service.importFile(bytes));
            assertEquals(List.of(key), service.status().saveFiles());
            assertArrayEquals(bytes, Files.readAllBytes(service.savedFile(key)));
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes));
            assertFalse(Files.exists(root().resolve("saves").resolve(key + ".tmp")));
            assertArrayEquals(bytes, Files.readAllBytes(service.savedFile(key)));
            assertEquals(Set.of(key + ".json"), names(root().resolve("saves")));
        }
    }

    @Test void exactDeletionAfterInterruptedPublicationRemovesTheVerifiedStagingHardLinkFirst() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        AtomicReference<Boundary> fault = new AtomicReference<>(Boundary.AFTER_IMPORT_PUBLISH);
        try (var service = faultService(fault)) {
            assertThrows(IllegalStateException.class, () -> service.importFile(bytes));
            Path target = root().resolve("saves").resolve(key + ".json"), staging = root().resolve("saves").resolve(key + ".tmp");
            assertTrue(Files.isSameFile(target, staging));
            service.deleteSavedFile(key);
            assertTrue(names(root().resolve("saves")).isEmpty());
            assertEquals("IDLE", service.status().state());
        }
    }

    @Test void identicalCompleteStagingCopyCanBeCleanedWithoutClaimingAnotherImport() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        try (var service = new CampaignStorageService(root())) {
            service.importFile(bytes);
            Path target = root().resolve("saves").resolve(key + ".json"), staging = root().resolve("saves").resolve(key + ".tmp");
            Files.write(staging, bytes); assertFalse(Files.isSameFile(target, staging));
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes)); assertFalse(Files.exists(staging));
            Files.write(staging, bytes); service.deleteSavedFile(key);
            assertTrue(names(root().resolve("saves")).isEmpty());
        }
    }

    @Test void retryAndExactDeletionRetainPartialForeignAndLinkedStaging() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        Path staging = root().resolve("saves").resolve(key + ".tmp"), external = temporary.resolve("external-staging.json");
        try (var service = new CampaignStorageService(root())) {
            service.importFile(bytes);
            byte[] partial = Arrays.copyOf(bytes, bytes.length / 2); Files.write(staging, partial);
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes)); assertArrayEquals(partial, Files.readAllBytes(staging));
            service.deleteSavedFile(key); assertArrayEquals(partial, Files.readAllBytes(staging));
            Files.delete(staging); service.importFile(bytes);
            byte[] foreign = bytes.clone(); foreign[foreign.length / 2] ^= 1; Files.write(staging, foreign);
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes)); assertArrayEquals(foreign, Files.readAllBytes(staging));
            service.deleteSavedFile(key); assertArrayEquals(foreign, Files.readAllBytes(staging));
            Files.delete(staging); service.importFile(bytes);
            Files.write(external, bytes); Files.createSymbolicLink(staging, external);
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes)); assertTrue(Files.isSymbolicLink(staging));
            service.deleteSavedFile(key); assertTrue(Files.isSymbolicLink(staging)); assertArrayEquals(bytes, Files.readAllBytes(external));
        }
    }

    @Test void conflictingPublishedContentCannotAuthorizeCleanupOfAnotherCompleteStagingDocument() throws Exception {
        byte[] bytes = fileBytes(); CampaignCodec codec = new CampaignCodec(); var document = codec.decode(bytes); var source = document.snapshot();
        byte[] different = codec.encode(new CampaignData.Document(document.fileKey(), new CampaignData.Snapshot(source.campaignId(), "另一个同身份内容", source.rules(), source.characters(), source.history(), source.domains())));
        try (var service = new CampaignStorageService(root())) {
            service.importFile(different);
            Path staging = root().resolve("saves").resolve(document.fileKey() + ".tmp"); Files.write(staging, bytes);
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes));
            assertArrayEquals(different, Files.readAllBytes(service.savedFile(document.fileKey()))); assertArrayEquals(bytes, Files.readAllBytes(staging));
            service.deleteSavedFile(document.fileKey()); assertArrayEquals(bytes, Files.readAllBytes(staging));
        }
    }

    @Test void incompleteOrForeignImportStagingIsRetainedAndNeverReplaced() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        byte[] partial = Arrays.copyOf(bytes, bytes.length / 2);
        try (var service = new CampaignStorageService(root())) {
            Path stage = root().resolve("saves").resolve(key + ".tmp"); Files.write(stage, partial);
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes));
            assertArrayEquals(partial, Files.readAllBytes(stage));
            assertFalse(Files.exists(root().resolve("saves").resolve(key + ".json")));
        }
    }

    @Test void importAndSavedFileDeletionRequireNoActiveOrResidualRun() throws Exception {
        byte[] bytes = fileBytes(); String file, run;
        try (var service = new CampaignStorageService(root())) {
            var created = service.create("受保护运行", RULES); file = service.status().saveFiles().getFirst(); run = service.status().runId();
            assertCode("RUN_EXISTS", () -> service.importFile(bytes));
            assertCode("RUN_EXISTS", () -> service.deleteSavedFile(file));
            assertCode("RUN_EXISTS", () -> service.discardResidual(run));
            assertEquals(created.campaignId(), service.current().campaignId());
        }
        try (var service = new CampaignStorageService(root())) {
            assertCode("RESIDUAL_RUN", () -> service.importFile(bytes));
            assertCode("RESIDUAL_RUN", () -> service.deleteSavedFile(file));
            assertTrue(Files.exists(root().resolve("saves").resolve(file + ".json")));
        }
    }

    @Test void deleteRemovesOnlyTheExactManagedSaveAndRejectsPathOrIdentityForgery() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey(), forged = id();
        try (var service = new CampaignStorageService(root())) {
            service.importFile(bytes);
            Path other = root().resolve("saves").resolve(forged + ".json"); Files.write(other, bytes);
            assertCode("INVALID_FILE", () -> service.deleteSavedFile("../" + key));
            assertCode("INVALID_FILE", () -> service.deleteSavedFile(forged));
            assertArrayEquals(bytes, Files.readAllBytes(other));
            service.deleteSavedFile(key);
            assertFalse(Files.exists(root().resolve("saves").resolve(key + ".json")));
            assertEquals(Set.of(forged + ".json"), names(root().resolve("saves")));
        }
    }

    @Test void managedFileLinksCannotBeImportedOverOrDeleted() throws Exception {
        byte[] bytes = fileBytes(); String key = new CampaignCodec().decode(bytes).fileKey();
        Path external = temporary.resolve("external.json"); Files.write(external, bytes);
        try (var service = new CampaignStorageService(root())) {
            Path target = root().resolve("saves").resolve(key + ".json"); Files.createSymbolicLink(target, external);
            assertCode("FILE_CONFLICT", () -> service.importFile(bytes));
            assertCode("STORAGE_IO", () -> service.deleteSavedFile(key));
            assertTrue(Files.isSymbolicLink(target)); assertArrayEquals(bytes, Files.readAllBytes(external));
        }
    }

    @Test void explicitAbandonmentDropsOnlyTheIdentifiedRunAndRetainsEverySavedArtifact() throws Exception {
        String run; Set<String> saves;
        try (var service = new CampaignStorageService(root())) {
            var created = service.create("明确放弃", RULES);
            service.addCharacter(id(), created.epoch(), 0, "未保存角色", 12, 4);
            run = service.status().runId(); saves = names(root().resolve("saves"));
        }
        try (var service = new CampaignStorageService(root())) {
            assertCode("INVALID_FILE", () -> service.discardResidual("../" + run));
            assertCode("RUN_NOT_FOUND", () -> service.discardResidual(id()));
            assertTrue(Files.exists(root().resolve("runs").resolve(run).resolve("run.db")));
            service.discardResidual(run);
            assertEquals("IDLE", service.status().state());
            assertTrue(names(root().resolve("runs")).isEmpty());
            assertEquals(saves, names(root().resolve("saves")));
        }
    }

    @Test void unknownResidualContentBlocksAbandonmentBeforeAnyMaterialIsRemoved() throws Exception {
        String run = residual(); Path directory = root().resolve("runs").resolve(run);
        Path unknown = directory.resolve("do-not-delete.txt"); Files.writeString(unknown, "user-owned"); Set<String> before = names(directory);
        try (var service = new CampaignStorageService(root())) {
            assertCode("CLEANUP_BLOCKED", () -> service.discardResidual(run));
            assertEquals(before, names(directory)); assertEquals("user-owned", Files.readString(unknown));
            assertFalse(Files.exists(directory.resolve("discard.json")));
        }
    }

    @Test void residualLinksAndAnotherRunLockPreventAbandonment() throws Exception {
        String run = residual(); Path directory = root().resolve("runs").resolve(run);
        Path input = directory.resolve("input.json"), external = temporary.resolve("private-input.json");
        Files.move(input, external); Files.createSymbolicLink(input, external);
        try (var service = new CampaignStorageService(root())) {
            assertCode("CLEANUP_BLOCKED", () -> service.discardResidual(run));
            assertTrue(Files.isSymbolicLink(input)); assertTrue(Files.exists(directory.resolve("run.db")));
            Files.delete(input); Files.move(external, input);
            try (FileChannel channel = FileChannel.open(directory.resolve("run.lock"), StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
                assertCode("RUN_LOCKED", () -> service.discardResidual(run));
                assertTrue(Files.exists(directory.resolve("run.db")));
            }
        }
    }

    @Test void damagedDatabaseCanBeExplicitlyAbandonedButUnboundPreparationCannot() throws Exception {
        String run = residual(); Path directory = root().resolve("runs").resolve(run);
        Files.write(directory.resolve("run.db"), new byte[]{1, 2, 3});
        byte[] original = Files.readAllBytes(directory.resolve("input.json")); Files.write(directory.resolve("input.json"), new byte[]{4, 5, 6});
        try (var service = new CampaignStorageService(root())) {
            assertCode("STORAGE_INTEGRITY", () -> service.discardResidual(run));
            assertTrue(Files.exists(directory.resolve("run.db")));
            Files.write(directory.resolve("input.json"), original);
            assertThrows(StorageException.class, () -> service.recover(run));
            assertEquals("BLOCKED", service.status().state());
            service.discardResidual(run);
            assertEquals("IDLE", service.status().state());
        }
    }

    @Test void interruptedIsolatedAbandonmentCanOnlyContinueDiscardAndSurvivesRestart() throws Exception {
        String run = residual(); AtomicReference<Boundary> fault = new AtomicReference<>(Boundary.AFTER_DISCARD_ISOLATION);
        try (var service = faultService(fault)) {
            assertThrows(IllegalStateException.class, () -> service.discardResidual(run));
            assertEquals(List.of(run), service.status().residualRunIds());
            assertCode("RUN_DISCARDED", () -> service.recover(run));
            assertCode("RESIDUAL_RUN", () -> service.create("不能替代", RULES));
            assertTrue(Files.exists(root().resolve("runs").resolve(".discard-" + run).resolve("run.db")));
        }
        try (var service = new CampaignStorageService(root())) {
            service.discardResidual(run);
            assertEquals("IDLE", service.status().state()); assertTrue(names(root().resolve("runs")).isEmpty());
        }
    }

    @Test void discardMarkerWithoutPreparationCannotAuthorizeDeletionOfASurvivingDatabase() throws Exception {
        String run = residual(); AtomicReference<Boundary> fault = new AtomicReference<>(Boundary.AFTER_DISCARD_ISOLATION);
        try (var service = faultService(fault)) { assertThrows(IllegalStateException.class, () -> service.discardResidual(run)); }
        Path directory = root().resolve("runs").resolve(".discard-" + run);
        Files.delete(directory.resolve("input.json")); Files.delete(directory.resolve("allocation.json"));
        long databaseSize = Files.size(directory.resolve("run.db"));
        try (var service = new CampaignStorageService(root())) {
            assertCode("STORAGE_INTEGRITY", () -> service.discardResidual(run));
            assertEquals(databaseSize, Files.size(directory.resolve("run.db")));
            assertTrue(Files.exists(directory.resolve("initialized.json")));
            assertTrue(Files.exists(directory.resolve("discard.json")));
        }
    }

    @Test void shutdownRetainsLatestUnsavedStateForExplicitRecoveryWhileEndCleansNormally() throws Exception {
        String run;
        try (var service = new CampaignStorageService(root())) {
            var created = service.create("关闭保留", RULES);
            service.addCharacter(id(), created.epoch(), 0, "角色", 12, 4); run = service.status().runId();
        }
        try (var service = new CampaignStorageService(root())) {
            assertEquals("RESIDUAL", service.status().state());
            var restored = service.recover(run); assertEquals(1, restored.revision()); assertEquals(1, restored.characters().size());
            service.end(id(), restored.epoch(), restored.revision());
            assertEquals("IDLE", service.status().state()); assertTrue(names(root().resolve("runs")).isEmpty());
        }
    }

    private String residual() throws Exception {
        try (var service = new CampaignStorageService(root())) { service.create("本地残留", RULES); return service.status().runId(); }
    }
    private CampaignStorageService faultService(AtomicReference<Boundary> fault) throws Exception {
        return new CampaignStorageService(root(), boundary -> { if (fault.compareAndSet(boundary, null)) throw new IllegalStateException("injected " + boundary); });
    }
    private static byte[] fileBytes() throws Exception {
        try (var input = CampaignStorageLifecycleTest.class.getResourceAsStream("/storage/vitality-known-file.json")) { return input.readAllBytes(); }
    }
    private static Set<String> names(Path directory) throws Exception {
        try (var entries = Files.list(directory)) { return entries.map(path -> path.getFileName().toString()).collect(Collectors.toSet()); }
    }
    private static void assertCode(String code, Runnable action) { assertEquals(code, assertThrows(StorageException.class, action::run).code()); }
}
