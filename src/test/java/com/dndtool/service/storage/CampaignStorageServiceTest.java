package com.dndtool.service.storage;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.service.storage.CampaignStorageService.*;

class CampaignStorageServiceTest {
    @TempDir Path temporary;
    private Path root() { return temporary.resolve("managed"); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static final List<String> RULES = List.of(CampaignRules.BASE, CampaignRules.HEALING);

    @Test void actualLifecyclePersistsTypedHealthAndCompleteHistoryAcrossSaveEndAndFreshOpen() throws Exception {
        String finalFile, firstRun, campaign; long firstEpoch;
        try (var host = new CampaignStorageService(root())) {
            var empty = host.create("测试战役", RULES); firstRun = host.status().runId(); firstEpoch = empty.epoch(); campaign = empty.campaignId();
            try (Connection c = database(firstRun); ResultSet r = c.createStatement().executeQuery("SELECT count(*) FROM health_healing")) { assertTrue(r.next()); assertEquals(0, r.getInt(1)); }
            var added = host.addCharacter(id(), empty.epoch(), 0, "受伤角色", 20, 3);
            var healed = host.heal(id(), added.epoch(), 1, added.characters().getFirst().id(), 1, 8);
            assertEquals(11, healed.characters().getFirst().currentHp()); assertEquals(2, healed.history().size());
            assertEquals(3, healed.lastCommand().beforeHp()); assertEquals(11, healed.lastCommand().afterHp());
            assertEquals(8, healed.history().getLast().applied()); assertEquals(2, healed.characters().getFirst().version());
            var save = host.save(id(), healed.epoch(), 2); byte[] bytes = Files.readAllBytes(host.savedFile(save.fileKey()));
            String portable = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            for (String forbidden : List.of("epoch", "runId", "requestId", "storageModel", "schemaDigest", "result_revision")) assertFalse(portable.contains(forbidden), forbidden);
            assertEquals(healed.history().size(), new CampaignCodec().decode(bytes).snapshot().history().size());
            finalFile = host.end(id(), healed.epoch(), 2).fileKey(); assertEquals("IDLE", host.status().state());
            assertEquals(0, Files.list(root().resolve("runs")).count());
            var reopened = host.open(finalFile);
            assertEquals(campaign, reopened.campaignId()); assertNotEquals(firstRun, host.status().runId()); assertNotEquals(firstEpoch, reopened.epoch());
            assertEquals(0, reopened.revision()); assertEquals(11, reopened.characters().getFirst().currentHp());
            assertEquals(healed.history(), reopened.history()); assertEquals(2, reopened.characters().getFirst().version());
        }
    }

    @Test void explicitResidualRecoveryPreservesLatestUnsavedFactsAndRotatesEpoch() throws Exception {
        String run, character, request = id(), creationRequest = id(); long epoch;
        try (var host = new CampaignStorageService(root())) {
            var v = host.create("残留", RULES); epoch = v.epoch();
            v = host.addCharacter(creationRequest, epoch, 0, "角色", 20, 3); character = v.characters().getFirst().id();
            host.heal(request, epoch, 1, character, 1, 8); run = host.status().runId();
        }
        try (var restarted = new CampaignStorageService(root())) {
            assertEquals("RESIDUAL", restarted.status().state());
            assertCode("RESIDUAL_RUN", () -> restarted.create("不能替换", RULES));
            var restored = restarted.recover(run); assertNotEquals(epoch, restored.epoch()); assertEquals(2, restored.revision()); assertEquals(11, restored.characters().getFirst().currentHp());
            assertCode("STALE_EPOCH", () -> restarted.heal(id(), epoch, 2, character, 2, 1));
            assertCode("STALE_EPOCH", () -> restarted.addCharacter(creationRequest, epoch, 0, "角色", 20, 3));
            assertCode("STALE_EPOCH", () -> restarted.heal(request, epoch, 1, character, 1, 8));
            assertTrue(restarted.addCharacter(creationRequest, restored.epoch(), 0, "角色", 20, 3).lastCommand().replayed());
            var replay = restarted.heal(request, restored.epoch(), 1, character, 1, 8);
            assertTrue(replay.lastCommand().replayed()); assertEquals(2, replay.lastCommand().acceptedRevision());
            assertEquals(3, replay.lastCommand().beforeHp()); assertEquals(11, replay.lastCommand().afterHp()); assertEquals(2, replay.history().size());
            assertCode("IDEMPOTENCY_CONFLICT", () -> restarted.heal(request, restored.epoch(), 1, character, 1, 9));
        }
    }

    @Test void businessFailureAfterStateUpdateRollsBackStateRootHistoryAndReceiptTogether() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(); String request = id();
        try (var host = faultHost(fail)) {
            var v = host.create("回滚", RULES); v = host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3);
            long epoch = v.epoch(); String character = v.characters().getFirst().id();
            fail.set(Boundary.AFTER_CHARACTER_UPDATE);
            assertThrows(IllegalStateException.class, () -> host.heal(request, epoch, 1, character, 1, 8));
            var unchanged = host.current(); assertEquals(1, unchanged.revision()); assertEquals(3, unchanged.characters().getFirst().currentHp()); assertEquals(1, unchanged.history().size()); assertEquals(1, unchanged.characters().getFirst().version());
            var retried = host.heal(request, epoch, 1, character, 1, 8); assertEquals(2, retried.revision()); assertFalse(retried.lastCommand().replayed());
        }
    }

    @Test void lostBusinessAcknowledgmentBlocksThenRecoversTheOriginalAcceptedResult() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(); String request = id();
        try (var host = faultHost(fail)) {
            var v = host.create("提交后中断", RULES); v = host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3);
            long epoch = v.epoch(); String character = v.characters().getFirst().id(), run = host.status().runId();
            fail.set(Boundary.AFTER_BUSINESS_COMMIT);
            assertCode("COMMIT_UNKNOWN", () -> host.heal(request, epoch, 1, character, 1, 8));
            assertEquals("BLOCKED", host.status().state()); assertCode("RUN_BLOCKED", () -> host.heal(id(), epoch, 2, character, 2, 1));
            var recovered = host.recover(run); var replay = host.heal(request, recovered.epoch(), 1, character, 1, 8);
            assertTrue(replay.lastCommand().replayed()); assertEquals(2, replay.history().size()); assertEquals(11, replay.lastCommand().afterHp());
        }
    }

    @Test void failedP2RetainsExactSourceAndRestartDoesNotAdvertiseOrDownloadUnacceptedArtifact() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(); String run, request = id(), pending;
        try (var host = faultHost(fail)) {
            var v = host.create("保存恢复", RULES); v = host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3); run = host.status().runId();
            fail.set(Boundary.AFTER_P2); long epoch = v.epoch();
            assertThrows(IllegalStateException.class, () -> host.save(request, epoch, 1));
            try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT file_key FROM core_save WHERE accepted=0")) {
                assertTrue(r.next()); pending = com.dndtool.persistence.storage.CampaignRepository.uuid(r.getBytes(1));
            }
            assertFalse(host.status().saveFiles().contains(pending)); assertCode("SAVE_NOT_ACCEPTED", () -> host.savedFile(pending));
        }
        try (var restarted = new CampaignStorageService(root())) {
            assertTrue(restarted.status().saveFiles().isEmpty()); assertCode("RESIDUAL_RUN", () -> restarted.savedFile(pending));
            var v = restarted.recover(run); assertEquals(1, v.revision()); assertEquals(3, v.characters().getFirst().currentHp());
            assertEquals(pending, restarted.save(request, v.epoch(), 1).fileKey()); assertTrue(Files.exists(restarted.savedFile(pending)));
            assertEquals(2, restarted.status().saveFiles().size());
        }
    }

    @Test void initializationDdlAndSeedsRollbackAndExactOriginalInputCanBeRetried() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(Boundary.AFTER_DDL); String run;
        try (var host = faultHost(fail)) {
            assertThrows(IllegalStateException.class, () -> host.create("原始输入", RULES)); run = host.status().runId();
            try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT count(*) FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%'")) { r.next(); assertEquals(0, r.getInt(1)); }
        }
        try (var restarted = new CampaignStorageService(root())) {
            var restored = restarted.recover(run); assertEquals("原始输入", restored.name()); assertEquals(0, restored.revision()); assertEquals("ACTIVE", restarted.status().state());
        }
    }

    @Test void missingOrMalformedCommittedDatabaseNeverGetsRecreatedOrRepaired() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) { host.create("完整库", RULES); run = host.status().runId(); }
        Path database = root().resolve("runs").resolve(run).resolve("run.db"); Files.delete(database);
        try (var restarted = new CampaignStorageService(root())) {
            assertEquals("RESIDUAL", restarted.status().state()); assertFalse(Files.exists(database));
            assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); assertFalse(Files.exists(database));
        }
    }

    @Test void frozenRulesAndActualSchemaAreVerifiedOnRecoveryWithoutRepair() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) { host.create("规则核验", RULES); run = host.status().runId(); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE rules_vitality SET algorithm='unsupported'"); }
        try (var restarted = new CampaignStorageService(root())) { assertThrows(StorageException.class, () -> restarted.recover(run)); assertEquals("BLOCKED", restarted.status().state()); }
    }

    @Test void exactCleanupPreservesUnknownFilesAndCanContinueFromForcedTombstone() throws Exception {
        String run, finalKey;
        try (var host = new CampaignStorageService(root())) {
            var v = host.create("清理", RULES); run = host.status().runId(); Path unknown = root().resolve("runs").resolve(run).resolve("user-note.txt"); Files.writeString(unknown, "retain");
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
            assertCode("UNSUPPORTED_RULE_SELECTION", () -> host.create("缺base", List.of(CampaignRules.HEALING)));
            assertEquals("IDLE", host.status().state()); assertEquals(0, Files.list(root().resolve("runs")).count());
            assertCode("INSTANCE_LOCKED", () -> { try (var ignored = new CampaignStorageService(root())) { } catch (java.io.IOException e) { throw new RuntimeException(e); } });
            var v = host.create("边界", RULES); long epoch = v.epoch();
            assertCode("INVALID_HP", () -> host.addCharacter(id(), epoch, 0, "角色", 65536, 1));
            assertCode("INVALID_HP", () -> host.addCharacter(id(), epoch, 0, "角色", 20, 0));
            v = host.addCharacter(id(), epoch, 0, "角色", 65535, 1); String character = v.characters().getFirst().id();
            assertCode("INVALID_AMOUNT", () -> host.heal(id(), epoch, 1, character, 1, 65536));
            assertCode("STALE_REVISION", () -> host.heal(id(), epoch, 0, character, 1, 1));
            assertCode("STALE_CHARACTER", () -> host.heal(id(), epoch, 1, character, 0, 1));
            v = host.heal(id(), epoch, 1, character, 1, 65535); assertEquals(65535, v.characters().getFirst().currentHp()); assertEquals(65534, v.history().getLast().applied());
        }
    }

    @Test void forgedCleanupMarkerCannotDeleteAnUnsealedExistingAuthority() throws Exception {
        String run, initialFile;
        try (var host = new CampaignStorageService(root())) {
            host.create("未封存", RULES); run = host.status().runId(); initialFile = host.status().saveFiles().getFirst();
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
            var v = host.create("意图绑定", RULES); v = host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3);
            host.heal(id(), v.epoch(), 1, v.characters().getFirst().id(), 1, 8); run = host.status().runId();
        }
        byte[] original;
        try (Connection c = database(run); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT intent FROM core_command WHERE kind='HEAL'")) { r.next(); original = r.getBytes(1); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE core_command SET intent=zeroblob(32) WHERE kind='HEAL'"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); PreparedStatement s = c.prepareStatement("UPDATE core_command SET intent=? WHERE kind='HEAL'")) { s.setBytes(1, original); s.executeUpdate(); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE core_save SET intent=zeroblob(32)"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT current_hp FROM characters_character")) { r.next(); assertEquals(11, r.getLong(1)); }
    }

    @Test void importedCharacterHistorySuppliesTheOriginalCasForAcceptedIntentVerification() throws Exception {
        String run, request = id(), character;
        try (var host = new CampaignStorageService(root())) {
            var v = host.create("导入后命令", RULES); v = host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3);
            v = host.heal(id(), v.epoch(), 1, v.characters().getFirst().id(), 1, 8);
            String file = host.end(id(), v.epoch(), 2).fileKey(); v = host.open(file); character = v.characters().getFirst().id();
            assertEquals(0, v.revision()); assertEquals(2, v.characters().getFirst().version());
            v = host.heal(request, v.epoch(), 0, character, 2, 2);
            assertEquals(1, v.lastCommand().acceptedRevision()); assertEquals(13, v.lastCommand().afterHp()); run = host.status().runId();
        }
        try (var restarted = new CampaignStorageService(root())) {
            var v = restarted.recover(run); v = restarted.heal(request, v.epoch(), 0, character, 2, 2);
            assertTrue(v.lastCommand().replayed()); assertEquals(11, v.lastCommand().beforeHp()); assertEquals(13, v.lastCommand().afterHp()); assertEquals(3, v.history().size());
        }
    }

    @Test void acceptedCheckpointRevisionCannotClaimAnotherValidHistoryPrefix() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) {
            var v = host.create("保存版本", RULES); v = host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3);
            host.save(id(), v.epoch(), 1); host.heal(id(), v.epoch(), 1, v.characters().getFirst().id(), 1, 8); run = host.status().runId();
        }
        var intent = new com.google.gson.JsonArray(); intent.add("SAVE_MANUAL"); intent.add("2");
        byte[] digest = HexFormat.of().parseHex(CampaignJson.digest("DND-VITALITY-COMMAND", CampaignJson.canonical(intent)));
        try (Connection c = database(run); PreparedStatement s = c.prepareStatement("UPDATE core_save SET revision=2,intent=? WHERE purpose='MANUAL'")) { s.setBytes(1, digest); s.executeUpdate(); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
    }

    @Test void deletingReceiptsCannotReclassifyUnsavedLocalHistoryAsAnImportedBaseline() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) {
            var v = host.create("初始基线", RULES); host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3); run = host.status().runId();
        }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM core_command"); s.executeUpdate("UPDATE core_control SET revision=0"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT count(*) FROM history_event")) { r.next(); assertEquals(1, r.getInt(1)); }
    }

    @Test void initializationIdentityIsBoundEvenBeforeTheFirstCheckpointIsAccepted() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(Boundary.AFTER_INIT_COMMIT); String run;
        try (var host = faultHost(fail)) { assertThrows(IllegalStateException.class, () -> host.create("原始名称", RULES)); run = host.status().runId(); }
        try (Connection c = database(run); Statement s = c.createStatement()) { s.executeUpdate("UPDATE core_control SET name='changed'"); }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
    }

    @Test void malformedSqliteTextIsRejectedWithoutReplacementOrWriteback() throws Exception {
        String run;
        try (var host = new CampaignStorageService(root())) {
            var v = host.create("严格文本", RULES); host.addCharacter(id(), v.epoch(), 0, "角色", 20, 3); run = host.status().runId();
        }
        try (Connection c = database(run); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE characters_character SET name=CAST(x'80' AS TEXT)");
            s.executeUpdate("UPDATE history_character_created SET name=CAST(x'80' AS TEXT)");
        }
        try (var restarted = new CampaignStorageService(root())) { assertCode("STORAGE_INTEGRITY", () -> restarted.recover(run)); }
        try (Connection c = database(run); ResultSet r = c.createStatement().executeQuery("SELECT hex(name) FROM characters_character")) { r.next(); assertEquals("80", r.getString(1)); }
    }

    @Test void otherwiseValidUtf16DatabaseIsRejectedWithoutReencodingOrSchemaRepair() throws Exception {
        AtomicReference<Boundary> fail = new AtomicReference<>(Boundary.AFTER_INIT_COMMIT); String run;
        try (var host = faultHost(fail)) { assertThrows(IllegalStateException.class, () -> host.create("编码", RULES)); run = host.status().runId(); }
        var repository = new com.dndtool.persistence.storage.CampaignRepository();
        com.dndtool.persistence.storage.CampaignRepository.Control control;
        CampaignData.Snapshot snapshot; List<String> modules;
        try (Connection c = database(run)) { control = repository.control(c); snapshot = repository.snapshot(c); modules = repository.modules(c); }
        Files.delete(root().resolve("runs").resolve(run).resolve("run.db"));
        try (Connection c = database(run); Statement s = c.createStatement()) {
            s.execute("PRAGMA encoding='UTF-16le'"); s.execute("PRAGMA foreign_keys=ON"); s.execute("BEGIN IMMEDIATE");
            var plan = com.dndtool.persistence.storage.StorageSchemaCatalog.bundled().plan(CampaignRules.roots(RULES));
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
