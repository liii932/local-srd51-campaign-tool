package com.dndtool.service.storage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.service.storage.CampaignLifecycleFixture.*;
import static com.dndtool.service.storage.CampaignStorageService.*;

class CampaignStorageServiceTest {
    @TempDir Path temporary;
    private Path root() { return temporary.resolve("managed"); }
    private static String id() { return UUID.randomUUID().toString(); }

    @Test void actualLifecyclePersistsFreeCardAndCompleteHistoryAcrossSaveEndAndFreshOpen() throws Exception {
        String finalFile, firstRun, campaign; long firstEpoch;
        try (var host = new CampaignStorageService(root())) {
            var empty = CampaignRuleFixtures.create(host, "测试战役"); firstRun = host.status().runId(); firstEpoch = empty.epoch(); campaign = empty.campaignId();
            try (Connection c = database(firstRun); ResultSet r = c.createStatement().executeQuery("SELECT count(*) FROM manual_character")) { assertTrue(r.next()); assertEquals(0, r.getInt(1)); }
            var added = addCard(host, id(), empty.epoch(), 0, "受伤角色");
            var edited = setHp(host, id(), added.epoch(), 1, cardId(added), 0, 8);
            assertEquals(8, hp(edited)); assertEquals(2, edited.history().size());
            assertEquals(11, hp(added)); assertEquals(cardId(added), edited.lastCommand().subjectId());
            assertEquals(1, version(edited));
            var save = host.save(id(), edited.epoch(), 2); byte[] bytes = Files.readAllBytes(host.savedFile(save.fileKey()));
            String portable = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            for (String forbidden : List.of("epoch", "runId", "requestId", "storageModel", "schemaDigest", "result_revision")) assertFalse(portable.contains(forbidden), forbidden);
            assertEquals(edited.history().size(), new CampaignCodec().decode(bytes).snapshot().history().size());
            finalFile = host.end(id(), edited.epoch(), 2).fileKey(); assertEquals("IDLE", host.status().state());
            assertEquals(0, Files.list(root().resolve("runs")).count());
            var reopened = host.open(finalFile);
            assertEquals(campaign, reopened.campaignId()); assertNotEquals(firstRun, host.status().runId()); assertNotEquals(firstEpoch, reopened.epoch());
            assertEquals(0, reopened.revision()); assertEquals(8, hp(reopened));
            assertEquals(edited.history(), reopened.history()); assertEquals(1, version(reopened));
        }
    }

    @Test void explicitResidualRecoveryPreservesLatestUnsavedFactsAndRotatesEpoch() throws Exception {
        String run, character, request = id(), creationRequest = id(); long epoch;
        try (var host = new CampaignStorageService(root())) {
            var v = CampaignRuleFixtures.create(host, "残留"); epoch = v.epoch();
            v = addCard(host, creationRequest, epoch, 0, "角色"); character = cardId(v);
            setHp(host, request, epoch, 1, character, 0, 8); run = host.status().runId();
        }
        try (var restarted = new CampaignStorageService(root())) {
            assertEquals("RESIDUAL", restarted.status().state());
            assertCode("RESIDUAL_RUN", () -> restarted.create("不能替换", CampaignRuleFixtures.rules().binding().members()));
            var restored = restarted.recover(run); assertNotEquals(epoch, restored.epoch()); assertEquals(2, restored.revision()); assertEquals(8, hp(restored));
            assertCode("STALE_EPOCH", () -> setHp(restarted, id(), epoch, 2, character, 1, 1));
            assertCode("STALE_EPOCH", () -> addCard(restarted, creationRequest, epoch, 0, "角色"));
            assertCode("STALE_EPOCH", () -> setHp(restarted, request, epoch, 1, character, 0, 8));
            assertTrue(addCard(restarted, creationRequest, restored.epoch(), 0, "角色").lastCommand().replayed());
            var replay = setHp(restarted, request, restored.epoch(), 1, character, 0, 8);
            assertTrue(replay.lastCommand().replayed()); assertEquals(2, replay.lastCommand().acceptedRevision());
            assertEquals(8, hp(replay)); assertEquals(character, replay.lastCommand().subjectId()); assertEquals(2, replay.history().size());
            assertCode("IDEMPOTENCY_CONFLICT", () -> setHp(restarted, request, restored.epoch(), 1, character, 0, 9));
        }
    }

    @Test void businessFailureAfterStateUpdateRollsBackStateRootHistoryAndReceiptTogether() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(); String request = id();
        try (var host = faultHost(fail)) {
            var v = CampaignRuleFixtures.create(host, "回滚"); v = addCard(host, id(), v.epoch(), 0, "角色");
            long epoch = v.epoch(); String character = cardId(v);
            fail.set(Boundary.AFTER_CHARACTER_UPDATE);
            assertThrows(IllegalStateException.class, () -> setHp(host, request, epoch, 1, character, 0, 8));
            var unchanged = host.current(); assertEquals(1, unchanged.revision()); assertEquals(11, hp(unchanged)); assertEquals(1, unchanged.history().size()); assertEquals(0, version(unchanged));
            var retried = setHp(host, request, epoch, 1, character, 0, 8); assertEquals(2, retried.revision()); assertFalse(retried.lastCommand().replayed());
        }
    }

    @Test void lostBusinessAcknowledgmentBlocksThenRecoversTheOriginalAcceptedResult() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(); String request = id();
        try (var host = faultHost(fail)) {
            var v = CampaignRuleFixtures.create(host, "提交后中断"); v = addCard(host, id(), v.epoch(), 0, "角色");
            long epoch = v.epoch(); String character = cardId(v), run = host.status().runId();
            fail.set(Boundary.AFTER_BUSINESS_COMMIT);
            assertCode("COMMIT_UNKNOWN", () -> setHp(host, request, epoch, 1, character, 0, 8));
            assertEquals("BLOCKED", host.status().state()); assertCode("RUN_BLOCKED", () -> setHp(host, id(), epoch, 2, character, 1, 1));
            var recovered = host.recover(run); var replay = setHp(host, request, recovered.epoch(), 1, character, 0, 8);
            assertTrue(replay.lastCommand().replayed()); assertEquals(2, replay.history().size()); assertEquals(8, hp(replay));
        }
    }

    @Test void failedP2RetainsExactSourceAndRestartDoesNotAdvertiseOrDownloadUnacceptedArtifact() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(); String run, request = id(), pending;
        try (var host = faultHost(fail)) {
            var v = CampaignRuleFixtures.create(host, "保存恢复"); v = addCard(host, id(), v.epoch(), 0, "角色"); run = host.status().runId();
            fail.set(Boundary.AFTER_P2); long epoch = v.epoch();
            assertThrows(IllegalStateException.class, () -> host.save(request, epoch, 1));
            try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT file_key FROM core_save WHERE accepted=0")) {
                assertTrue(r.next()); pending = com.dndtool.persistence.storage.CampaignRepository.uuid(r.getBytes(1));
            }
            assertFalse(host.status().saveFiles().contains(pending)); assertCode("SAVE_NOT_ACCEPTED", () -> host.savedFile(pending));
        }
        try (var restarted = new CampaignStorageService(root())) {
            assertTrue(restarted.status().saveFiles().isEmpty()); assertCode("RESIDUAL_RUN", () -> restarted.savedFile(pending));
            var v = restarted.recover(run); assertEquals(1, v.revision()); assertEquals(11, hp(v));
            assertEquals(pending, restarted.save(request, v.epoch(), 1).fileKey()); assertTrue(Files.exists(restarted.savedFile(pending)));
            assertEquals(2, restarted.status().saveFiles().size());
        }
    }

    @Test void initializationDdlAndSeedsRollbackAndExactOriginalInputCanBeRetried() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(Boundary.AFTER_DDL); String run;
        try (var host = faultHost(fail)) {
            assertThrows(IllegalStateException.class, () -> CampaignRuleFixtures.create(host, "原始输入")); run = host.status().runId();
            try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT count(*) FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%'")) { r.next(); assertEquals(0, r.getInt(1)); }
        }
        try (var restarted = new CampaignStorageService(root())) {
            var restored = restarted.recover(run); assertEquals("原始输入", restored.name()); assertEquals(0, restored.revision()); assertEquals("ACTIVE", restarted.status().state());
        }
    }

    @Test void missingOrMalformedCommittedDatabaseNeverGetsRecreatedOrRepaired() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) { CampaignRuleFixtures.create(host, "完整库"); run = host.status().runId(); }
        Path database = root().resolve("runs").resolve(run).resolve("run.db"); Files.delete(database);
        try (var restarted = new CampaignStorageService(root())) {
            assertEquals("RESIDUAL", restarted.status().state()); assertFalse(Files.exists(database));
            assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); assertFalse(Files.exists(database));
        }
    }

    @Test void frozenRulesAndActualSchemaAreVerifiedOnRecoveryWithoutRepair() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) { CampaignRuleFixtures.create(host, "规则核验"); run = host.status().runId(); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE rules_runtime_material SET document='{}'"); }
        try (var restarted = new CampaignStorageService(root())) { assertThrows(StorageException.class, () -> restarted.recover(run)); assertEquals("BLOCKED", restarted.status().state()); }
    }

    @Test void exactCleanupPreservesUnknownFilesAndCanContinueFromForcedTombstone() throws Exception {
        String run, finalKey;
        try (var host = new CampaignStorageService(root())) {
            var v = CampaignRuleFixtures.create(host, "清理"); run = host.status().runId(); Path unknown = root().resolve("runs").resolve(run).resolve("user-note.txt"); Files.writeString(unknown, "retain");
            assertCode("CLEANUP_BLOCKED", () -> host.end(id(), v.epoch(), 0)); assertEquals("retain", Files.readString(unknown));
            assertTrue(Files.exists(unknown.getParent().resolve("run.db"))); finalKey = host.savedCampaigns().getLast().fileKey();
        }
        Files.delete(root().resolve("runs").resolve(run).resolve("user-note.txt"));
        try (var restarted = new CampaignStorageService(root())) {
            restarted.recover(run); assertEquals("IDLE", restarted.status().state()); assertTrue(Files.exists(restarted.savedFile(finalKey)));
        }
    }

    @Test void ruleDependenciesHpBoundariesStaleVersionsAndInstanceLockFailBeforeWrites() throws Exception {
        try (var host = new CampaignStorageService(root())) {
            assertEquals("RULE_BINDING_INVALID", assertThrows(LocalRuleLibraryException.class,
                    () -> host.create("缺base", List.of())).code());
            assertEquals("IDLE", host.status().state()); assertEquals(0, Files.list(root().resolve("runs")).count());
            assertCode("INSTANCE_LOCKED", () -> { try (var ignored = new CampaignStorageService(root())) { } catch (java.io.IOException e) { throw new RuntimeException(e); } });
            var v = CampaignRuleFixtures.create(host, "边界"); long epoch = v.epoch();
            v = addCard(host, id(), epoch, 0, "角色"); String character = cardId(v);
            assertCode("INVALID_FILE", () -> setHp(host, id(), epoch, 1, character, 0, 12));
            assertCode("INVALID_FILE", () -> setHp(host, id(), epoch, 1, character, 0, -1));
            assertCode("STALE_REVISION", () -> setHp(host, id(), epoch, 0, character, 0, 1));
            assertCode("STALE_CHARACTER", () -> setHp(host, id(), epoch, 1, character, 1, 1));
            assertEquals(11, hp(host.current()));
            v = setHp(host, id(), epoch, 1, character, 0, 0);
            assertEquals(0, hp(v)); assertEquals(1, version(v));
        }
    }

    @Test void forgedCleanupMarkerCannotDeleteAnUnsealedExistingAuthority() throws Exception {
        String run, initialFile;
        try (var host = new CampaignStorageService(root())) {
            CampaignRuleFixtures.create(host, "未封存"); run = host.status().runId(); initialFile = host.status().saveFiles().getFirst();
        }
        Path directory = root().resolve("runs").resolve(run), saved = root().resolve("saves").resolve(initialFile + ".json");
        com.google.gson.JsonObject marker = new com.google.gson.JsonObject(); marker.addProperty("runId", run); marker.addProperty("fileKey", initialFile); marker.addProperty("rawDigest", CampaignJson.sha256(Files.readAllBytes(saved))); marker.addProperty("revision", "0");
        Files.write(directory.resolve("cleanup.json"), CampaignJson.canonical(marker));
        try (var restarted = new CampaignStorageService(root())) {
            assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); assertTrue(Files.exists(directory.resolve("run.db"))); assertTrue(Files.exists(directory.resolve("input.json")));
        }
    }

    @Test void acceptedBusinessAndSaveIntentDigestsAreRecomputedFromTheirActualFacts() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) {
            var v = CampaignRuleFixtures.create(host, "意图绑定"); v = addCard(host, id(), v.epoch(), 0, "角色");
            setHp(host, id(), v.epoch(), 1, cardId(v), 0, 8); run = host.status().runId();
        }
        byte[] original;
        try (Connection c = database(run); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT intent FROM core_command WHERE result_revision=2")) { r.next(); original = r.getBytes(1); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE core_command SET intent=zeroblob(32) WHERE result_revision=2"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); PreparedStatement s = c.prepareStatement("UPDATE core_command SET intent=? WHERE result_revision=2")) { s.setBytes(1, original); s.executeUpdate(); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE core_save SET intent=zeroblob(32)"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT document FROM scenes_world")) {
            assertTrue(r.next());
            var world = CampaignJson.read(r.getString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var fields = world.getAsJsonObject("manual").getAsJsonArray("characters").get(0)
                    .getAsJsonObject().getAsJsonObject("current").getAsJsonArray("fields");
            assertTrue(fields.asList().stream().map(com.google.gson.JsonElement::getAsJsonArray)
                    .anyMatch(field -> field.get(0).getAsString().equals("hp.current") && field.get(1).getAsInt() == 8));
        }
    }

    @Test void importedCharacterHistorySuppliesTheOriginalCasForAcceptedIntentVerification() throws Exception {
        String run, request = id(), character;
        try (var host = new CampaignStorageService(root())) {
            var v = CampaignRuleFixtures.create(host, "导入后命令"); v = addCard(host, id(), v.epoch(), 0, "角色");
            v = setHp(host, id(), v.epoch(), 1, cardId(v), 0, 8);
            String file = host.end(id(), v.epoch(), 2).fileKey(); v = host.open(file); character = cardId(v);
            assertEquals(0, v.revision()); assertEquals(1, version(v));
            v = setHp(host, request, v.epoch(), 0, character, 1, 2);
            assertEquals(1, v.lastCommand().acceptedRevision()); assertEquals(2, hp(v)); run = host.status().runId();
        }
        try (var restarted = new CampaignStorageService(root())) {
            var v = restarted.recover(run); v = setHp(restarted, request, v.epoch(), 0, character, 1, 2);
            assertTrue(v.lastCommand().replayed()); assertEquals(2, hp(v)); assertEquals(3, v.history().size());
        }
    }

    @Test void acceptedCheckpointRevisionCannotClaimAnotherValidHistoryPrefix() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) {
            var v = CampaignRuleFixtures.create(host, "保存版本"); v = addCard(host, id(), v.epoch(), 0, "角色");
            host.save(id(), v.epoch(), 1); setHp(host, id(), v.epoch(), 1, cardId(v), 0, 8); run = host.status().runId();
        }
        var intent = new com.google.gson.JsonArray(); intent.add("SAVE_MANUAL"); intent.add("2");
        byte[] digest = HexFormat.of().parseHex(CampaignJson.digest("DND-VITALITY-COMMAND", CampaignJson.canonical(intent)));
        try (Connection c = database(run); PreparedStatement s = c.prepareStatement("UPDATE core_save SET revision=2,intent=? WHERE purpose='MANUAL'")) { s.setBytes(1, digest); s.executeUpdate(); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
    }

    @Test void deletingReceiptsCannotReclassifyUnsavedLocalHistoryAsAnImportedBaseline() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) {
            var v = CampaignRuleFixtures.create(host, "初始基线"); addCard(host, id(), v.epoch(), 0, "角色"); run = host.status().runId();
        }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM core_command"); s.executeUpdate("UPDATE core_control SET revision=0"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT count(*) FROM history_runtime_root")) { r.next(); assertEquals(1, r.getInt(1)); }
    }

    @Test void initializationIdentityIsBoundEvenBeforeTheFirstCheckpointIsAccepted() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(Boundary.AFTER_INIT_COMMIT); String run;
        try (var host = faultHost(fail)) { assertThrows(IllegalStateException.class, () -> CampaignRuleFixtures.create(host, "原始名称")); run = host.status().runId(); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE core_control SET name='changed'"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
    }

    @Test void malformedSqliteTextIsRejectedWithoutReplacementOrWriteback() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) {
            var v = CampaignRuleFixtures.create(host, "严格文本"); addCard(host, id(), v.epoch(), 0, "角色"); run = host.status().runId();
        }
        try (Connection c = database(run); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE core_control SET name=CAST(x'80' AS TEXT)");
        }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT hex(name) FROM core_control")) { r.next(); assertEquals("80", r.getString(1)); }
    }

    @Test void otherwiseValidUtf16DatabaseIsRejectedWithoutReencodingOrSchemaRepair() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(Boundary.AFTER_INIT_COMMIT); String run;
        try (var host = faultHost(fail)) { assertThrows(IllegalStateException.class, () -> CampaignRuleFixtures.create(host, "编码")); run = host.status().runId(); }
        var repository = new com.dndtool.persistence.storage.CampaignRepository();
        com.dndtool.persistence.storage.CampaignRepository.Control control;
        CampaignData.Snapshot snapshot; List<String> modules;
        try (Connection c = database(run)) { control = repository.control(c); snapshot = repository.snapshot(c); modules = repository.modules(c); }
        Files.delete(root().resolve("runs").resolve(run).resolve("run.db"));
        try (Connection c = database(run); Statement s = c.createStatement()) {
            s.execute("PRAGMA encoding='UTF-16le'"); s.execute("PRAGMA foreign_keys=ON"); s.execute("BEGIN IMMEDIATE");
            var plan = com.dndtool.persistence.storage.StorageSchemaCatalog.bundled().plan(Set.of("runtime"));
            plan.create(c); repository.initialize(c, control, modules, snapshot); plan.verify(c); s.execute("COMMIT");
        }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_DATABASE", () -> restarted.recover(run)); }
        try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("PRAGMA encoding")) { r.next(); assertEquals("UTF-16le", r.getString(1)); }
    }

    @Test void rejectingASecondJvmLocalOwnerDoesNotReleaseTheOperatingSystemLock() throws Exception {
        try (var host = new CampaignStorageService(root())) {
            assertCode("INSTANCE_LOCKED", () -> { try (var ignored = new CampaignStorageService(root())) { } catch (java.io.IOException e) { throw new RuntimeException(e); } });
            String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Process process = new ProcessBuilder(javaExecutable, "-cp", classpath, RootLockProbe.class.getName(), root().toString()).redirectErrorStream(true).start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Lock probe timed out");
                assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } finally { if (process.isAlive()) process.destroyForcibly(); }
        }
        try (var reopened = new CampaignStorageService(root())) { assertEquals("IDLE", reopened.status().state()); }
    }

    public static final class RootLockProbe {
        public static void main(String[] args) throws Exception {
            try (var ignored = new CampaignStorageService(Path.of(args[0]))) { System.exit(23); }
            catch (StorageException e) { if (e.code().equals("INSTANCE_LOCKED")) return; throw e; }
        }
    }

    private CampaignStorageService faultHost(AtomicReference<Boundary> failure) throws Exception {
        return new CampaignStorageService(root(), boundary -> { if (failure.compareAndSet(boundary, null)) throw new IllegalStateException("injected " + boundary); });
    }
    private Connection database(String run) throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + root().resolve("runs").resolve(run).resolve("run.db")); }
    private static void assertCode(String code, Runnable action) { assertEquals(code, assertThrows(StorageException.class, action::run).code()); }
}
