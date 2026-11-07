package com.dndtool.service.storage;

import com.dndtool.persistence.storage.CampaignDomainRepository;
import com.dndtool.persistence.storage.CampaignRepository;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.service.storage.CampaignDomainData.*;
import static com.dndtool.service.storage.CampaignStorageService.Boundary;

class CampaignDomainRecoveryTest {
    @TempDir Path temporary;
    private static String id() { return UUID.randomUUID().toString(); }
    private static JsonObject character() {
        JsonObject input = new JsonObject(); input.addProperty("name", "原角色"); input.addProperty("kind", "PC");
        input.addProperty("classKey", "fighter"); input.addProperty("backgroundKey", "acolyte"); return input;
    }

    @Test void committedInitializationAndUnknownBusinessResultResumeWithoutReinitializingOrReplaying() throws Exception {
        Path root = temporary.resolve("recover"); String run, request = id(); long epoch;
        AtomicReference<Boundary> fail = new AtomicReference<>(Boundary.AFTER_INIT_COMMIT);
        try (var service = new CampaignStorageService(root, boundary -> {
            if (fail.compareAndSet(boundary, null)) throw new IllegalStateException("injected");
        })) {
            assertThrows(RuntimeException.class, () -> CampaignRuleFixtures.create(service, "完整领域")); run = service.status().runId();
        }
        try (var service = new CampaignStorageService(root, boundary -> {
            if (fail.compareAndSet(boundary, null)) throw new IllegalStateException("injected");
        })) {
            var current = service.recover(run); epoch = current.epoch();
            assertEquals(0, current.domain().getAsJsonArray("characters").size());
            assertEquals(0, current.revision()); assertFalse(service.status().saveFiles().isEmpty());
            fail.set(Boundary.AFTER_BUSINESS_COMMIT);
            assertEquals("COMMIT_UNKNOWN", assertThrows(StorageException.class,
                    () -> service.domainCommand(request, epoch, 0, "character-create", character())).code());
        }
        try (var service = new CampaignStorageService(root)) {
            var current = service.recover(run); assertNotEquals(epoch, current.epoch());
            assertEquals(1, current.revision()); assertEquals(1, current.domain().getAsJsonArray("characters").size());
            var replay = service.domainCommand(request, current.epoch(), 0, "character-create", character());
            assertTrue(replay.lastCommand().replayed()); assertEquals(current.domain(), replay.domain());
            assertEquals(current.history(), replay.history());
        }
    }

    @Test void validButRewrittenImportedRootIsRejectedAgainstTheRetainedInput() throws Exception {
        Path root = temporary.resolve("import-binding"); String run;
        try (var service = new CampaignStorageService(root)) {
            var current = CampaignRuleFixtures.create(service, "初始绑定");
            current = service.domainCommand(id(), current.epoch(), 0, "character-create", character());
            String file = service.end(id(), current.epoch(), current.revision()).fileKey(); service.open(file); run = service.status().runId();
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("runs").resolve(run).resolve("run.db"));
             var sql = connection.createStatement()) {
            var original = new CampaignRepository().snapshot(connection);
            var state = original.domains();
            var first = state.roots().getFirst();
            var input = character();
            input.addProperty("name", "改写过去");
            var empty = new CampaignData.Snapshot(original.campaignId(), original.name(), State.initial(state.material().restore().projection()));
            var changed = new CampaignDomainCommands().apply(empty, first.kind(), input, first.operationId(), first.intent(),
                    sides -> { throw new AssertionError("Character creation requested a die"); });
            assertDoesNotThrow(() -> CampaignData.validate(new CampaignData.Snapshot(original.campaignId(), original.name(), changed)));
            sql.execute("PRAGMA foreign_keys=ON"); sql.execute("BEGIN IMMEDIATE"); new CampaignDomainRepository().write(connection, changed); sql.execute("COMMIT");
        }
        try (var service = new CampaignStorageService(root)) {
            assertEquals("STORAGE_INTEGRITY", assertThrows(StorageException.class, () -> service.recover(run)).code());
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("runs").resolve(run).resolve("run.db"))) {
            assertEquals("改写过去", new CampaignDomainRepository().read(connection).characters().getFirst().name());
        }
    }
}
