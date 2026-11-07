package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.LocalRuleLibrary.Boundary.*;
import static com.dndtool.service.storage.LocalRuleLibrary.OperationState.*;
import static com.dndtool.service.storage.LocalRuleLibrary.REVIEW_PROTOCOL;
import static org.junit.jupiter.api.Assertions.*;

class LocalRuleLibraryTest {
    private static final String DM = "local-dm";
    @TempDir Path storage;

    @Test void importAndAcceptanceAreIndependentExactAndReplayTheOriginalReceipt() throws Exception {
        LocalRuleLibrary library = new LocalRuleLibrary(storage);
        assertEquals(List.of(), library.inventory());
        assertFalse(Files.exists(root()));
        byte[] bytes = baseBytes();
        StoredMember expected = member(bytes);
        assertIssue("RULE_PACKAGE_MISSING", "BIND", expected, () -> library.readAccepted(List.of(expected)));
        assertCode("RULE_PACKAGE_INVALID", () -> library.importArtifact(id(), new byte[]{1, 2, 3}));
        assertFalse(Files.exists(root()));

        String importId = id();
        var imported = library.importArtifact(importId, bytes);
        assertEquals(expected, imported.imported().member());
        assertArrayEquals(bytes, Files.readAllBytes(original(expected)));
        assertEquals(imported, library.importArtifact(importId, bytes));
        assertEquals(imported, library.inspect(importId).importReceipt());
        assertEquals(imported.imported(), library.importArtifact(id(), bytes).imported());
        var review = library.review(expected.moduleKey(), expected.releaseVersion());
        assertArrayEquals(bytes, review.artifact().originalZip());
        assertNull(review.acceptance());
        assertIssue("RULE_PACKAGE_NOT_ACCEPTED", "BIND", expected, () -> library.readAccepted(List.of(expected)));
        assertCode("RULE_REVIEW_INVALID", () -> library.accept(id(), expected, "old-review", DM));
        assertCode("IDEMPOTENCY_CONFLICT", () -> library.accept(importId, expected, REVIEW_PROTOCOL, DM));
        var wrong = new StoredMember(expected.moduleKey(), expected.releaseVersion(), expected.role(),
                "0".repeat(64), expected.logicalSha256());
        assertIssue("RULE_PACKAGE_IDENTITY_CONFLICT", "ACCEPT", wrong, () -> library.accept(id(), wrong, REVIEW_PROTOCOL, DM));

        String acceptId = id();
        var accepted = library.accept(acceptId, expected, REVIEW_PROTOCOL, DM);
        assertEquals(accepted, library.accept(acceptId, expected, REVIEW_PROTOCOL, DM));
        assertEquals(accepted, library.recover(acceptId).acceptanceReceipt());
        assertEquals(accepted.acceptance(), library.inventory().getFirst().acceptance());
        assertArrayEquals(bytes, library.readAccepted(List.of(expected)).getFirst().originalZip());
        assertEquals(1, rows("rule_import"));
        assertEquals(1, rows("rule_acceptance"));
    }

    @Test void identicalLogicalContentWithDifferentZipBytesCannotReuseTheVersionOrOperation() throws Exception {
        byte[] bytes = baseBytes(), repacked = repack(bytes);
        StoredMember expected = member(bytes), other = member(repacked);
        assertNotEquals(expected.rawZipSha256(), other.rawZipSha256());
        assertEquals(expected.logicalSha256(), other.logicalSha256());
        LocalRuleLibrary library = new LocalRuleLibrary(storage);
        String importId = id();
        library.importArtifact(importId, bytes);
        assertIssue("RULE_PACKAGE_IDENTITY_CONFLICT", "IMPORT", other, () -> library.importArtifact(id(), repacked));
        assertIssue("IDEMPOTENCY_CONFLICT", "IMPORT", other, () -> library.importArtifact(importId, repacked));
        assertFalse(Files.exists(original(other)));
        assertArrayEquals(bytes, Files.readAllBytes(original(expected)));
        assertEquals(1, rows("rule_operation"));
    }

    @Test void missingCorruptAndLostLedgerBlockBindingAndDoNotRecreateRegisteredFacts() throws Exception {
        LocalRuleLibrary library = new LocalRuleLibrary(storage);
        byte[] bytes = baseBytes();
        StoredMember expected = library.importArtifact(id(), bytes).imported().member();
        library.accept(id(), expected, REVIEW_PROTOCOL, DM);
        Files.delete(original(expected));
        assertIssue("RULE_PACKAGE_MISSING", "BIND", expected, () -> library.readAccepted(List.of(expected)));
        assertIssue("RULE_PACKAGE_MISSING", "IMPORT", expected, () -> library.importArtifact(id(), bytes));
        assertFalse(Files.exists(original(expected)));
        assertNotNull(library.inventory().getFirst().acceptance());

        Files.write(original(expected), new byte[]{3, 4});
        assertIssue("RULE_PACKAGE_CORRUPT", "BIND", expected, () -> library.readAccepted(List.of(expected)));
        assertIssue("RULE_PACKAGE_CORRUPT", "REVIEW", expected,
                () -> library.review(expected.moduleKey(), expected.releaseVersion()));
        Files.delete(root().resolve("ledger.db"));
        assertCode("RULE_LIBRARY_INTEGRITY", () -> library.importArtifact(id(), bytes));
        assertCode("RULE_LIBRARY_INTEGRITY", () -> library.readAccepted(List.of(expected)));
        assertFalse(Files.exists(root().resolve("ledger.db")));
        assertArrayEquals(new byte[]{3, 4}, Files.readAllBytes(original(expected)));
    }

    @Test void interruptionAfterPublicationKeepsTheOriginalIntentUntilVerifiedRetry() throws Exception {
        LocalRuleLibrary library = faulting(AFTER_ARTIFACT_PUBLISH);
        byte[] bytes = baseBytes();
        StoredMember expected = member(bytes);
        String importId = id();
        assertThrows(IllegalStateException.class, () -> library.importArtifact(importId, bytes));
        assertArrayEquals(bytes, Files.readAllBytes(original(expected)));
        assertEquals(0, rows("rule_import"));
        assertEquals(PENDING, library.inspect(importId).state());
        assertIssue("RULE_LIBRARY_BLOCKED", "BIND", expected, () -> library.readAccepted(List.of(expected)));

        LocalRuleLibrary reopened = new LocalRuleLibrary(storage);
        assertEquals(RETRYABLE, reopened.recover(importId).state());
        assertEquals(RETRYABLE, reopened.inspect(importId).state());
        assertCode("IDEMPOTENCY_CONFLICT", () -> reopened.importArtifact(importId, repack(bytes)));
        assertIssue("RULE_LIBRARY_BLOCKED", "IMPORT", expected, () -> reopened.importArtifact(id(), bytes));
        var imported = reopened.importArtifact(importId, bytes);
        assertEquals(importId, imported.imported().operationId());
        assertEquals(COMMITTED, reopened.recover(importId).state());
        assertEquals(1, rows("rule_import"));
        assertEquals(1, rows("rule_operation"));
    }

    @Test void unknownAcceptanceCommitRemainsBlockedAcrossInstancesUntilVerified() throws Exception {
        LocalRuleLibrary library = new LocalRuleLibrary(storage);
        StoredMember expected = library.importArtifact(id(), baseBytes()).imported().member();
        String acceptId = id();
        assertIssue("RULE_LIBRARY_COMMIT_UNKNOWN", "ACCEPT", expected,
                () -> faulting(AFTER_BUSINESS_COMMIT).accept(acceptId, expected, REVIEW_PROTOCOL, DM));
        assertEquals(1, rows("rule_acceptance"));
        LocalRuleLibrary reopened = new LocalRuleLibrary(storage);
        assertEquals(PENDING, reopened.inspect(acceptId).state());
        assertIssue("RULE_LIBRARY_BLOCKED", "BIND", expected, () -> reopened.readAccepted(List.of(expected)));
        assertIssue("RULE_LIBRARY_BLOCKED", "ACCEPT", expected, () -> reopened.accept(id(), expected, REVIEW_PROTOCOL, DM));
        byte[] original = Files.readAllBytes(original(expected));
        Files.delete(original(expected));
        assertIssue("RULE_PACKAGE_MISSING", "RECOVER", expected, () -> reopened.recover(acceptId));
        assertEquals(PENDING, reopened.inspect(acceptId).state());
        Files.write(original(expected), original);
        var result = reopened.recover(acceptId);
        assertEquals(COMMITTED, result.state());
        assertEquals(result.acceptanceReceipt(), reopened.accept(acceptId, expected, REVIEW_PROTOCOL, DM));
        assertEquals(expected, member(reopened.readAccepted(List.of(expected)).getFirst().originalZip()));
        assertEquals(1, rows("rule_acceptance"));
    }

    @Test void interruptedFirstInitializationCanResumeWithoutInventingFacts() throws Exception {
        byte[] bytes = baseBytes();
        for (var boundary : List.of(AFTER_LEDGER_FILE_CREATE, AFTER_LEDGER_SCHEMA_COMMIT)) {
            Path isolated = storage.resolve(boundary.name());
            ManagedCampaignFiles.createDirectory(isolated);
            LocalRuleLibrary interrupted = new LocalRuleLibrary(isolated, current -> {
                if (current == boundary) throw new IllegalStateException("injected");
            });
            String importId = id();
            assertThrows(IllegalStateException.class, () -> interrupted.importArtifact(importId, bytes));
            assertFalse(Files.exists(isolated.resolve("rule-library/initialized")));
            LocalRuleLibrary reopened = new LocalRuleLibrary(isolated);
            assertEquals(importId, reopened.importArtifact(importId, bytes).imported().operationId());
            assertEquals(COMMITTED, reopened.inspect(importId).state());
            assertNull(reopened.inventory().getFirst().acceptance());
        }
    }

    @Test void competingInstancesCannotPublishASecondBodyForTheSameVersion() throws Exception {
        byte[] bytes = baseBytes(), repacked = repack(bytes);
        CountDownLatch published = new CountDownLatch(1), release = new CountDownLatch(1);
        LocalRuleLibrary writer = new LocalRuleLibrary(storage, boundary -> {
            if (boundary != AFTER_ARTIFACT_PUBLISH) return;
            published.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("test release timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var importing = executor.submit(() -> writer.importArtifact(id(), bytes));
            try {
                assertTrue(published.await(20, TimeUnit.SECONDS));
                LocalRuleLibrary other = new LocalRuleLibrary(storage);
                assertCode("RULE_LIBRARY_BUSY", () -> other.importArtifact(id(), repacked));
            } finally {
                release.countDown();
            }
            assertEquals(member(bytes), importing.get(20, TimeUnit.SECONDS).imported().member());
        }
        LocalRuleLibrary other = new LocalRuleLibrary(storage);
        assertCode("RULE_PACKAGE_IDENTITY_CONFLICT", () -> other.importArtifact(id(), repacked));
        assertEquals(1, rows("rule_import"));
    }

    private LocalRuleLibrary faulting(LocalRuleLibrary.Boundary selected) {
        AtomicReference<LocalRuleLibrary.Boundary> once = new AtomicReference<>(selected);
        return new LocalRuleLibrary(storage, boundary -> {
            if (once.compareAndSet(boundary, null)) throw new IllegalStateException("injected");
        });
    }

    private Path root() { return storage.resolve("rule-library"); }
    private Path original(StoredMember member) { return root().resolve("originals/" + member.rawZipSha256() + ".zip"); }

    private int rows(String table) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + root().resolve("ledger.db").toUri() + "?mode=ro");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static byte[] baseBytes() throws Exception {
        try (var stream = LocalRuleLibraryTest.class.getResourceAsStream("/storage/rules/srd51-runtime.zip")) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }

    private static StoredMember member(byte[] bytes) {
        var artifact = ProductionRuleArtifact.read(bytes);
        return new StoredMember(artifact.moduleKey(), artifact.releaseVersion(), artifact.role(),
                artifact.rawZipSha256(), artifact.logicalSha256());
    }

    private static byte[] repack(byte[] bytes) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var member : ProductionRuleArtifact.read(bytes).originalMembers().entrySet()) {
                byte[] body = member.getValue();
                var crc = new CRC32();
                crc.update(body);
                var entry = new ZipEntry(member.getKey());
                entry.setTimeLocal(LocalDateTime.of(2024, 1, 1, 0, 0));
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(body.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(body);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static LocalRuleLibraryException assertCode(String code, Executable action) {
        var failure = assertThrows(LocalRuleLibraryException.class, action);
        assertEquals(code, failure.code());
        return failure;
    }

    private static void assertIssue(String code, String stage, StoredMember member, Executable action) {
        var failure = assertCode(code, action);
        assertEquals(new LocalRuleLibraryException.Issue(stage, member.moduleKey(), member.releaseVersion(),
                member.rawZipSha256(), member.logicalSha256()), failure.issue());
    }
}
