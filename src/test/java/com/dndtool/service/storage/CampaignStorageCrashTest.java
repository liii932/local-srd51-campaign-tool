package com.dndtool.service.storage;

import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.service.storage.CampaignLifecycleFixture.*;

import com.dndtool.service.storage.CampaignStorageService.Boundary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

/** Abrupt process exit, real rollback journal, new JVM ownership and explicit same-version recovery. */
class CampaignStorageCrashTest {
    private static final String COMMAND = "11111111-1111-4111-8111-111111111111";
    private static final String SAVE = "22222222-2222-4222-8222-222222222222";
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(value = Boundary.class, names = {"AFTER_PREPARATION_DIRECTORY", "AFTER_PREPARATION_LOCK", "AFTER_INPUT_STAGING_CREATE", "DURING_INPUT_WRITE"})
    void killedBeforeCompleteInputRequiresExactExplicitAbandonment(Boundary boundary) throws Exception {
        Path root = crash(boundary);
        try (var service = new CampaignStorageService(root)) {
            String run = onlyResidual(service);
            assertEquals("RESIDUAL", service.status().state());
            assertEquals("PREPARATION_INCOMPLETE", assertThrows(StorageException.class, () -> service.recover(run)).code());
            assertEquals("RESIDUAL_RUN", assertThrows(StorageException.class, () -> service.create("other", CampaignRuleFixtures.rules().binding().members())).code());
            service.discardResidual(run);
            assertEquals("IDLE", service.status().state());
            assertTrue(service.status().saveFiles().isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Boundary.class, names = {"AFTER_INPUT_LINK", "AFTER_INPUT_WRITE", "AFTER_ALLOCATION_STAGING_CREATE", "DURING_ALLOCATION_WRITE", "AFTER_ALLOCATION_LINK", "AFTER_ALLOCATION_WRITE", "AFTER_PREPARATION_PUBLISH"})
    void killedAfterBoundInputRecoversOnlyOriginalPreparation(Boundary boundary) throws Exception {
        Path root = crash(boundary);
        try (var service = new CampaignStorageService(root)) {
            var restored = service.recover(onlyResidual(service));
            assertEquals("林间营地", restored.name()); assertEquals(0, restored.revision());
            assertTrue(restored.history().isEmpty()); assertEquals(1, service.status().saveFiles().size());
            assertEquals("ACTIVE", service.status().state());
        }
    }

    @Test void killedAfterDatabasePreparationBeforeDatabaseCreationMustNotGuessThatNoCommitExists() throws Exception {
        Path root = crash(Boundary.AFTER_DATABASE_PREPARATION);
        try (var service = new CampaignStorageService(root)) {
            String run = onlyResidual(service);
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.recover(run)).code());
            assertFalse(Files.exists(root.resolve("runs").resolve(run).resolve("run.db")));
            service.discardResidual(run); assertEquals("IDLE", service.status().state());
        }
    }

    @Test void interruptedDdlRollsBackAndOnlyTheOriginalPreparationIsRetried() throws Exception {
        Path root = crash(Boundary.AFTER_DDL);
        try (var service = new CampaignStorageService(root)) {
            assertEquals("RESIDUAL", service.status().state());
            assertThrows(StorageException.class, () -> service.create("other", CampaignRuleFixtures.rules().binding().members()));
            var restored = service.recover(onlyResidual(service));
            assertEquals("林间营地", restored.name());
            assertEquals(0, restored.revision());
            assertTrue(cards(restored) == 0);
            assertEquals(1, service.status().saveFiles().size());
            assertEquals("ACTIVE", service.status().state());
        }
    }

    @Test void committedInitializationContinuesItsFirstCheckpointWithoutDdl() throws Exception {
        Path root = crash(Boundary.AFTER_INIT_COMMIT);
        try (var service = noMoreDdl(root)) {
            var restored = service.recover(onlyResidual(service));
            assertEquals(0, restored.revision());
            assertTrue(restored.history().isEmpty());
            assertEquals(1, service.status().saveFiles().size());
        }
    }

    @Test void committedInitializationWithoutCompletionMarkerStillNeverReseeds() throws Exception {
        Path root = crash(Boundary.AFTER_INIT_COMMIT);
        String run;
        try (var service = new CampaignStorageService(root)) { run = onlyResidual(service); }
        // Same persisted state as a process halt after commit and before the optional marker write.
        Files.delete(root.resolve("runs").resolve(run).resolve("initialized.json"));
        try (var service = noMoreDdl(root)) {
            var restored = service.recover(run);
            assertEquals(0, restored.revision()); assertTrue(restored.history().isEmpty());
            assertEquals(1, service.status().saveFiles().size());
        }
    }

    @Test void killedFieldEditBeforeCommitLeavesNoPartialHpHistoryOrReceipt() throws Exception {
        Path root = crash(Boundary.AFTER_CHARACTER_UPDATE);
        try (var service = noMoreDdl(root)) {
            var restored = service.recover(onlyResidual(service));
            assertEquals(1, restored.revision());
            assertEquals(11, hp(restored));
            assertEquals(0, version(restored));
            assertEquals(1, restored.history().size());
            var retried = setHp(service, COMMAND, restored.epoch(), 1,
                    cardId(restored), 0, 9);
            assertEquals(9, hp(retried));
            assertEquals(2, retried.revision());
            assertEquals(2, retried.history().size());
        }
    }

    @Test void killedResponseAfterCommitReplaysOriginalReceiptWithoutApplyingAgain() throws Exception {
        Path root = crash(Boundary.AFTER_BUSINESS_COMMIT);
        try (var service = noMoreDdl(root)) {
            var restored = service.recover(onlyResidual(service));
            assertEquals(2, restored.revision());
            assertEquals(9, hp(restored));
            var replayed = setHp(service, COMMAND, restored.epoch(), 1,
                    cardId(restored), 0, 9);
            assertEquals(9, hp(replayed));
            assertEquals(2, replayed.revision());
            assertEquals(2, replayed.history().size());
            assertTrue(replayed.lastCommand().replayed());
            assertEquals(2, replayed.lastCommand().acceptedRevision());
            assertEquals(cardId(restored), replayed.lastCommand().subjectId());
            assertEquals(1, version(replayed));
        }
    }

    @Test void killedAfterFilePublicationAcceptsTheSameFileAndPreservesSource() throws Exception {
        Path root = crash(Boundary.AFTER_P2);
        Set<String> published = savedNames(root);
        assertEquals(2, published.size());
        try (var service = noMoreDdl(root)) {
            assertEquals("RESIDUAL", service.status().state());
            assertTrue(service.status().saveFiles().isEmpty());
            var restored = service.recover(onlyResidual(service));
            assertEquals(9, hp(restored));
            assertEquals(2, restored.history().size());
            var receipt = service.save(SAVE, restored.epoch(), 2);
            assertTrue(published.contains(receipt.fileKey() + ".json"));
            assertEquals(published, savedNames(root));
            assertEquals(2, service.status().saveFiles().size());
        }
    }

    private Path crash(Boundary boundary) throws Exception {
        Path root = temporary.resolve("campaign-store");
        Path output = temporary.resolve("child-output.txt");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-cp", classpath,
                Child.class.getName(), root.toString(), boundary.name())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Crash worker did not reach its exact boundary");
            assertEquals(73, process.exitValue(), () -> {
                try { return Files.readString(output); }
                catch (Exception e) { return "Worker failed before the intended halt"; }
            });
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
        return root;
    }

    private static CampaignStorageService noMoreDdl(Path root) throws Exception {
        return new CampaignStorageService(root, boundary -> {
            if (boundary == Boundary.AFTER_DDL) fail("Committed run must never be initialized again");
        });
    }

    private static String onlyResidual(CampaignStorageService service) {
        var residuals = service.status().residualRunIds();
        assertEquals(1, residuals.size());
        return residuals.getFirst();
    }


    private static Set<String> savedNames(Path root) throws Exception {
        try (var entries = Files.list(root.resolve("saves"))) {
            return entries.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).collect(Collectors.toSet());
        }
    }

    public static final class Child {
        public static void main(String[] args) throws Exception {
            Boundary target = Boundary.valueOf(args[1]);
            AtomicBoolean armed = new AtomicBoolean(target.ordinal() <= Boundary.AFTER_INIT_COMMIT.ordinal());
            try (var service = new CampaignStorageService(Path.of(args[0]), boundary -> {
                if (armed.get() && boundary == target) Runtime.getRuntime().halt(73);
            })) {
                var created = CampaignRuleFixtures.create(service, "林间营地");
                var added = addCard(service, UUID.randomUUID().toString(), created.epoch(), 0, "林间向导");
                if (target == Boundary.AFTER_CHARACTER_UPDATE || target == Boundary.AFTER_BUSINESS_COMMIT) armed.set(true);
                var edited = setHp(service, COMMAND, added.epoch(), 1, cardId(added), 0, 9);
                if (target == Boundary.AFTER_P2) {
                    armed.set(true);
                    service.save(SAVE, edited.epoch(), 2);
                }
                throw new AssertionError("Requested process boundary was never reached");
            }
        }
    }
}
