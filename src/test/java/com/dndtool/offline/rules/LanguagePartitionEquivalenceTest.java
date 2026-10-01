package com.dndtool.offline.rules;

import com.dndtool.module.CharacterCatalogAuthorPackageReader;
import com.dndtool.module.LanguagePartition;
import com.dndtool.module.ModuleCanonicalEncoderV2;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.persistence.JdbcRuntimeLanguageSnapshotRepository;
import com.dndtool.persistence.JdbcRuntimeLanguageSnapshotRepository.Identity;
import com.dndtool.persistence.JdbcSourceLanguageRepository;
import com.dndtool.module.ModuleCatalog;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.offline.rules.LanguagePartitionOracle.*;

/** Production boundary composition over JDBC proxies; no real MySQL or full-release acceptance. */
class LanguagePartitionEquivalenceTest {
    private static final JdbcRuntimeLanguageSnapshotRepository RUNTIME = new JdbcRuntimeLanguageSnapshotRepository();
    @TempDir Path temp;

    @Test void authorArtifactInstalledRowsSourceAndRuntimeReadMatchIndependentMatrixAndVector() throws Exception {
        var artifact = artifact(""); var db = install(artifact); var fixture = new LanguagePartitionJdbcFixture(db);
        assertBoundary(baseline(), artifact.author().languages(), vector());
        assertInstalled(baseline(), db);
        var source = new JdbcSourceLanguageRepository(fixture.source()).load();
        assertBoundary(baseline(), source, vector());
        var id = Identity.random();
        try (var transaction = fixture.runtime()) {
            var appended = RUNTIME.append(transaction, id, source);
            assertEquals(id, appended.identity()); assertBoundary(baseline(), appended.partition(), vector());
            assertStored(baseline(), fixture.languages, "snapshot_id"); assertHead(fixture);
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
            transaction.commit();
        }
        try (var transaction = fixture.runtime()) {
            assertBoundary(baseline(), RUNTIME.read(transaction, id).partition(), vector());
            transaction.rollback();
        }
        assertEquals(1, fixture.commits); assertEquals(1, fixture.sourceOpens);
    }

    @Test void authorArrayAndSqlResultOrderDoNotBecomeContent() throws Exception {
        var artifact = artifact("reverse"); var db = install(artifact); var fixture = new LanguagePartitionJdbcFixture(db);
        fixture.reverseRows = true;
        assertBoundary(baseline(), artifact.author().languages(), vector()); assertInstalled(baseline(), db);
        var source = new JdbcSourceLanguageRepository(fixture.source()).load();
        assertBoundary(baseline(), source, vector());
        var id = Identity.random();
        try (var tx = fixture.runtime()) {
            assertBoundary(baseline(), RUNTIME.append(tx, id, source).partition(), vector());
            assertStored(baseline(), fixture.languages, "snapshot_id"); tx.commit();
        }
        try (var tx = fixture.runtime()) { assertBoundary(baseline(), RUNTIME.read(tx, id).partition(), vector()); tx.rollback(); }
    }

    @TestFactory Stream<DynamicTest> legalContentChangesSurviveEveryBoundaryAndChangeBytes() {
        return Stream.of("display_name", "description", "source_page", "sort_order", "unicode").map(field -> DynamicTest.dynamicTest(field, () -> {
            var artifact = artifact(field); var expected = changed(field); var db = install(artifact);
            var fixture = new LanguagePartitionJdbcFixture(db); fixture.reverseRows = true;
            byte[] bytes = canonical(artifact.author().languages());
            assertFalse(Arrays.equals(vector(), bytes), "Legal content change must affect canonical bytes");
            assertBoundary(expected, artifact.author().languages(), bytes); assertInstalled(expected, db);
            var source = new JdbcSourceLanguageRepository(fixture.source()).load(); assertBoundary(expected, source, bytes);
            var id = Identity.random();
            try (var tx = fixture.runtime()) {
                assertBoundary(expected, RUNTIME.append(tx, id, source).partition(), bytes);
                assertStored(expected, fixture.languages, "snapshot_id"); assertHead(fixture); tx.commit();
            }
            try (var tx = fixture.runtime()) { assertBoundary(expected, RUNTIME.read(tx, id).partition(), bytes); tx.rollback(); }
        }));
    }

    @Test void sourceIdsAndRevisionsAreExcludedWhileSameKeysRemainBoundToDistinctSnapshots() throws Exception {
        var artifact = artifact(""); var db = install(artifact); var fixture = new LanguagePartitionJdbcFixture(db);
        var first = new JdbcSourceLanguageRepository(fixture.source()).load();
        // Coherently relabel the source identity, then perform an actual second installation/revision.
        db.roots.getFirst()[0] = 701L;
        for (var r : db.languages) r[0] = 701L;
        for (var r : db.tools) r[0] = 701L;
        for (var r : db.facts) r[0] = 701L;
        for (var r : db.partitions) r[0] = 701L;
        installInto(db, artifact, 1);
        assertEquals(2L, db.roots.getFirst()[8]); assertEquals(701L, db.roots.getFirst()[0]);
        var second = new JdbcSourceLanguageRepository(fixture.source()).load();
        assertBoundary(baseline(), first, vector()); assertBoundary(baseline(), second, vector());
        var idA = Identity.random(); var idB = Identity.random(); var idC = Identity.random();
        try (var tx = fixture.runtime()) { RUNTIME.append(tx, idA, first); RUNTIME.append(tx, idB, second); tx.commit(); }
        var changedArtifact = artifact("source_page"); installInto(db, changedArtifact, 2);
        var third = new JdbcSourceLanguageRepository(fixture.source()).load(); assertPartition(changed("source_page"), third);
        try (var tx = fixture.runtime()) { RUNTIME.append(tx, idC, third); tx.commit(); }
        assertEquals(3, fixture.registrations.size()); assertEquals(3, fixture.heads.size()); assertEquals(54, fixture.languages.size());
        fixture.sourceOffline = true;
        assertThrows(SQLException.class, () -> new JdbcSourceLanguageRepository(fixture.source()).load());
        int opens = fixture.sourceOpens, queries = fixture.sourceQueries;
        try (var tx = fixture.runtime()) {
            assertBoundary(baseline(), RUNTIME.read(tx, idA).partition(), vector());
            assertBoundary(baseline(), RUNTIME.read(tx, idB).partition(), vector());
            assertBoundary(changed("source_page"), RUNTIME.read(tx, idC).partition(), canonical(third));
            assertThrows(SQLException.class, () -> RUNTIME.read(tx, new Identity(idA.runId(), idC.snapshotId())));
            tx.rollback();
        }
        assertEquals(opens, fixture.sourceOpens); assertEquals(queries, fixture.sourceQueries);
        // This proves only this partition's runtime read path, not all Host/business source isolation.
    }

    @TestFactory Stream<DynamicTest> corruptSourceOrRuntimeRowsFailAcrossTheComposedBoundary() {
        Map<String, Consumer<List<Map<String, Object>>>> faults = new LinkedHashMap<>();
        faults.put("missing", rows -> rows.remove(8));
        faults.put("nineteenth", rows -> rows.add(new HashMap<>(rows.get(8))));
        faults.put("missing-description", rows -> rows.get(8).put("description", null));
        faults.put("page-as-text", rows -> rows.get(8).put("source_page", "59"));
        faults.put("non-nfc", rows -> rows.get(8).put("display_name", "e\u0301"));
        faults.put("duplicate-order", rows -> rows.get(8).put("sort_order", 1));
        faults.put("wrong-category", rows -> rows.get(8).put("category", LanguagePartitionJdbcFixture.b("SECRET")));
        return faults.entrySet().stream().flatMap(fault -> Stream.of(false, true).map(runtime -> DynamicTest.dynamicTest(
                (runtime ? "runtime-" : "source-") + fault.getKey(), () -> {
                    var db = install(artifact("")); var fixture = new LanguagePartitionJdbcFixture(db);
                    if (!runtime) {
                        fixture.sourceFault = fault.getValue();
                        assertThrows(SQLException.class, () -> new JdbcSourceLanguageRepository(fixture.source()).load());
                        assertTrue(fixture.registrations.isEmpty()); assertEquals(0, fixture.runtimeQueries);
                    } else {
                        var partition = new JdbcSourceLanguageRepository(fixture.source()).load(); var id = Identity.random();
                        try (var tx = fixture.runtime()) { RUNTIME.append(tx, id, partition); tx.commit(); }
                        fixture.runtimeFault = fault.getValue();
                        try (var tx = fixture.runtime()) { assertThrows(SQLException.class, () -> RUNTIME.read(tx, id)); tx.rollback(); }
                        assertEquals(18, fixture.languages.size(), "A bad read must not repair stored data");
                    }
                })));
    }

    @Test void wrongSourceAndRuntimeRowAssociationsCannotBorrowAnotherIdentity() throws Exception {
        var db = install(artifact("")); var fixture = new LanguagePartitionJdbcFixture(db);
        var source = new JdbcSourceLanguageRepository(fixture.source()).load();
        fixture.sourceFault = rows -> rows.get(8).put("release_id", 999L);
        assertThrows(SQLException.class, () -> new JdbcSourceLanguageRepository(fixture.source()).load());
        var id = Identity.random();
        try (var tx = fixture.runtime()) { RUNTIME.append(tx, id, source); tx.commit(); }
        fixture.runtimeFault = rows -> rows.get(8).put("snapshot_id", Identity.random().snapshotBytes());
        try (var tx = fixture.runtime()) { assertThrows(SQLException.class, () -> RUNTIME.read(tx, id)); tx.rollback(); }
    }

    @TestFactory Stream<DynamicTest> ninthWriteReadbackAndLaterCallerFailureRollbackOnlyNewTransaction() {
        return Stream.of("ninth-write", "readback", "later-caller").map(fault -> DynamicTest.dynamicTest(fault, () -> {
            var db = install(artifact("")); String sourceBefore = db.snapshot(); var fixture = new LanguagePartitionJdbcFixture(db);
            var source = new JdbcSourceLanguageRepository(fixture.source()).load(); var oldId = Identity.random();
            try (var tx = fixture.runtime()) { RUNTIME.append(tx, oldId, source); tx.commit(); }
            int committed = fixture.commits; fixture.languageWrites = 0; var failedId = Identity.random();
            if (fault.equals("ninth-write")) fixture.failLanguageWrite = 9;
            if (fault.equals("readback")) fixture.runtimeFault = rows -> rows.get(8).put("description", "Changed valid readback.");
            try (var tx = fixture.runtime()) {
                SQLException failure;
                if (fault.equals("later-caller")) {
                    failure = assertThrows(SQLException.class, () -> {
                        var appended = RUNTIME.append(tx, failedId, source);
                        assertBoundary(baseline(), appended.partition(), vector());
                        throw new SQLException("Later caller operation failed");
                    });
                    assertEquals("Later caller operation failed", failure.getMessage());
                } else {
                    failure = assertThrows(SQLException.class, () -> RUNTIME.append(tx, failedId, source));
                    assertEquals(fault.equals("ninth-write") ? "Ninth language write failed"
                            : "Invalid runtime language partition", failure.getMessage());
                }
                assertEquals(committed, fixture.commits); assertEquals(0, fixture.rollbacks);
                assertEquals(2, fixture.registrations.size()); assertEquals(2, fixture.heads.size());
                assertEquals(fault.equals("ninth-write") ? 26 : 36, fixture.languages.size());
                tx.rollback();
            }
            assertEquals(1, fixture.rollbacks); assertEquals(1, fixture.registrations.size());
            assertEquals(1, fixture.heads.size()); assertStored(baseline(), fixture.languages, "snapshot_id");
            fixture.runtimeFault = rows -> {};
            try (var tx = fixture.runtime()) {
                assertBoundary(baseline(), RUNTIME.read(tx, oldId).partition(), vector());
                assertThrows(SQLException.class, () -> RUNTIME.read(tx, failedId)); tx.rollback();
            }
            assertEquals(sourceBefore, db.snapshot(), "Runtime rollback cannot alter the installed source");
        }));
    }

    @Test void independentOracleRejectsIdenticalCommonModeLossAndWrongLogicalScalar() throws Exception {
        var good = projection(artifact("").author().languages());
        // Simulate the same defect at both sides: pairwise equality alone would pass.
        var lost = good.catalogDefinitions().stream().map(d -> new ModuleCatalog.CatalogDefinition(
                d.definitionType(), d.definitionKey(), d.displayName(), "omitted", d.sortOrder())).toList();
        var sourceLost = catalog(lost, good.catalogAttributes()); var runtimeLost = catalog(lost, good.catalogAttributes());
        assertEquals(sourceLost, runtimeLost);
        for (var bad : List.of(sourceLost, runtimeLost)) {
            assertThrows(AssertionError.class, () -> assertProjection(baseline(), bad));
            assertFalse(Arrays.equals(vector(), new ModuleCanonicalEncoderV2().encode(bad)));
        }
        var mistyped = good.catalogAttributes().stream().map(a -> a.attributeKey().equals("source.page")
                ? new ModuleCatalog.CatalogAttribute(a.definitionType(), a.definitionKey(), a.attributeKey(),
                    a.attributeOrder(), "TEXT", new ModuleCatalog.TextValue("59")) : a).toList();
        var sourceText = catalog(good.catalogDefinitions(), mistyped); var runtimeText = catalog(good.catalogDefinitions(), mistyped);
        assertEquals(sourceText, runtimeText);
        for (var bad : List.of(sourceText, runtimeText)) {
            assertThrows(AssertionError.class, () -> assertProjection(baseline(), bad));
            // Existing domain validation also rejects this scalar; do not weaken it to emit bytes.
            assertThrows(ModuleCanonicalException.class, () -> new ModuleCanonicalEncoderV2().encode(bad));
        }
    }

    private RuleArtifact artifact(String change) throws Exception {
        Path parent = Files.createDirectory(temp.resolve("input-" + UUID.randomUUID()));
        Path author = Files.createDirectory(parent.resolve("author")); Files.createDirectory(author.resolve("character"));
        for (String name : List.of("author-package.json", "character/languages.json", "character/tools.json", "package-guide.md", "notice.md")) {
            Files.copy(Path.of("rule-packages/srd51-complete").resolve(name), author.resolve(name));
        }
        Path file = parent.resolve("author/character/languages.json");
        JsonArray rows = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        if (change.equals("reverse")) {
            JsonArray reversed = new JsonArray(); for (int i = 17; i >= 0; i--) reversed.add(rows.get(i)); rows = reversed;
        } else {
            var common = rows.get(2).getAsJsonObject();
            switch (change) {
                case "display_name" -> common.addProperty(change, "Common revised");
                case "description" -> common.addProperty(change, "Revised Common description.");
                case "source_page" -> common.addProperty(change, 60);
                case "sort_order" -> { common.addProperty(change, 4); rows.get(3).getAsJsonObject().addProperty(change, 3); }
                case "unicode" -> {
                    common.addProperty("display_name", "😀".repeat(120));
                    common.addProperty("description", "é" + "😀".repeat(999));
                }
                case "" -> { }
                default -> throw new AssertionError(change);
            }
        }
        if (!change.isEmpty()) Files.writeString(file, rows.toString());
        // Exercise the author entry separately as well as the artifact's production reader.
        var direct = new CharacterCatalogAuthorPackageReader().read(Files.readAllBytes(parent.resolve("author/author-package.json")), Files.readAllBytes(file), Files.readAllBytes(parent.resolve("author/character/tools.json")));
        Path output = parent.resolve("changed"); String hash = RuleArtifact.build(parent.resolve("author"), output);
        var result = RuleArtifact.read(output, hash); assertEquals(direct.languages(), result.author().languages()); return result;
    }

    private SourceInstallationTest.Database install(RuleArtifact artifact) throws Exception {
        var db = new SourceInstallationTest.Database(); installInto(db, artifact, 0); return db;
    }

    private void installInto(SourceInstallationTest.Database db, RuleArtifact artifact, long expectedRevision) throws Exception {
        Path state = OfflineTestSupport.privateDirectory(temp, "state-" + UUID.randomUUID());
        var evidence = OfflineTestSupport.evidence(temp, state, "INSTALL", null);
        try (var store = new TicketStore(state)) {
            var ticket = OperationTicket.create(artifact, expectedRevision, evidence); store.save(ticket);
            var result = new SourceInstallation(evidence, store,
                    () -> LanguagePartitionJdbcFixture.installationConnection(db)).install(ticket, artifact);
            assertEquals(SourceInstallation.Status.COMMITTED, result.status());
            assertEquals(expectedRevision + 1, result.acceptedRevision()); assertEquals("PARTITION", result.acceptance().scope());
            assertNull(result.acceptance().observedContentSha256()); assertNull(store.pending());
        }
    }

    private static void assertInstalled(List<Row> expected, SourceInstallationTest.Database db) {
        var rows = db.languages.stream().map(r -> LanguagePartitionJdbcFixture.row(
                "release_id, " + LanguagePartitionJdbcFixture.LANGUAGE_COLUMNS, r)).toList();
        assertStored(expected, rows, "release_id");
        assertArrayEquals(LanguagePartitionJdbcFixture.b("DRAFT"), (byte[]) db.roots.getFirst()[7]);
        assertNull(db.roots.getFirst()[6]); assertNull(db.facts.getLast()[10]);
    }

    private static void assertHead(LanguagePartitionJdbcFixture fixture) {
        var head = fixture.heads.getFirst();
        assertArrayEquals(LanguagePartitionJdbcFixture.b("DRAFT"), (byte[]) head.get("release_status"));
        assertArrayEquals(LanguagePartitionJdbcFixture.b("PARTITION"), (byte[]) head.get("material_scope"));
        assertNull(head.get("content_sha256"));
        assertFalse(head.containsKey("release_id")); assertFalse(head.containsKey("installation_revision"));
    }

    private static void assertBoundary(List<Row> expected, LanguagePartition actual, byte[] bytes) throws Exception {
        assertPartition(expected, actual); assertArrayEquals(bytes, canonical(actual));
    }
}
