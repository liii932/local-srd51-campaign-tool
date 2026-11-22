package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.dndtool.service.storage.BundledCampaignRules.CHECK_VARIANT;
import static com.dndtool.service.storage.BundledCampaignRules.STANDARD;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRuleBundleIsolationTest {
    private static final String UNAVAILABLE = "BUNDLED_RULES_UNAVAILABLE";
    private static final String STANDARD_ZIP = "dm-runtime-house-rules";
    private static final String VARIANT_ZIP = "dm-runtime-check-variant";
    private static final String BASE_ZIP = "srd51-runtime";
    private static final String MANIFEST_RESOURCE = "storage/rules/bundled-members.tsv";

    @TempDir Path temporary;

    @Test void manifestAndCandidatesResolveFromFixedApplicationFixtures() throws Exception {
        assertResourceFile(MANIFEST_RESOURCE, Path.of("target/fixture-rule-manifest").resolve(MANIFEST_RESOURCE));
        var zipNames = List.of(BASE_ZIP, "srd51-characters", "srd51-magic", STANDARD_ZIP, VARIANT_ZIP);
        var candidates = BundledCampaignRules.candidates();
        assertEquals(zipNames.size(), candidates.size());
        for (String name : zipNames) {
            String resource = "storage/rules/" + name + ".zip";
            Path fixture = Path.of("src/test/fixtures/application-rules").resolve(resource);
            assertResourceFile(resource, fixture);
            byte[] original = Files.readAllBytes(fixture);
            var artifact = ProductionRuleArtifact.read(original);
            var expected = new StoredMember(artifact.moduleKey(), artifact.releaseVersion(), artifact.role(),
                    artifact.rawZipSha256(), artifact.logicalSha256());
            var candidate = candidates.stream().filter(item -> item.member().equals(expected)).findFirst().orElseThrow();
            assertTrue(candidate.available(), resource);
            assertArrayEquals(original, BundledCampaignRules.importCandidate(expected).originalZip(), resource);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void standardCreatesAndReopensWithVariantZipMissingOrMalformed(boolean malformed) throws Exception {
        BundledRuleTestProcess.run(temporary, VARIANT_ZIP, malformed ? new byte[] {1, 2, 3} : null,
                Child.class, "variant", temporary.resolve("store").toString());
    }

    @Test void aValidWrongZipCannotReplaceStandardOrBlockOtherRules() throws Exception {
        Path standard = temporary.resolve("standard-store");
        String standardFile = createStandardWithAcceptedRules(standard);

        var original = new ArrayList<>(BundledCampaignRules.artifacts(STANDARD));
        var customZip = alternateHouseRules(original.getLast());
        original.set(original.size() - 1, ProductionRuleArtifact.read(customZip));
        var custom = ProductionSchema2Projection.compose(original);
        var expected = BundledCampaignRules.projection().members().getLast();
        var alternate = custom.members().getLast();
        assertEquals(expected.moduleKey(), alternate.moduleKey());
        assertEquals(expected.releaseVersion(), alternate.releaseVersion());
        assertNotEquals(expected.rawZipSha256(), alternate.rawZipSha256());
        assertNotEquals(expected.logicalSha256(), alternate.logicalSha256());

        Path customSave = temporary.resolve("custom.json");
        Files.write(customSave, new CampaignCodec().encode(new CampaignData.Document(id(),
                new CampaignData.Snapshot(id(), "Custom source", CampaignDomainData.State.initial(custom)))));

        BundledRuleTestProcess.run(temporary, STANDARD_ZIP, customZip, Child.class,
                "standard", standard.toString(), standardFile, customSave.toString());
    }

    @Test void missingBaseDisablesBothBundledCombinations() throws Exception {
        BundledRuleTestProcess.run(temporary, BASE_ZIP, null, Child.class,
                "shared", temporary.resolve("store").toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void standardFileCannotUseTheLocalLibraryWithManifestMissingOrMalformed(boolean malformed) throws Exception {
        Path root = temporary.resolve("standard-store");
        String file = createStandardWithAcceptedRules(root);
        BundledRuleTestProcess.runWithResource(temporary, MANIFEST_RESOURCE, malformed ? new byte[] {1, 2, 3} : null,
                Child.class, "manifest", root.toString(), file);
    }

    public static final class Child {
        public static void main(String[] args) throws Exception {
            Path root = Path.of(args[1]);
            switch (args[0]) {
                case "variant" -> missingVariant(root);
                case "standard" -> mismatchedStandard(root, args[2], Path.of(args[3]));
                case "shared" -> missingShared(root);
                case "manifest" -> missingManifest(root, args[2]);
                default -> throw new IllegalArgumentException(args[0]);
            }
        }

        private static void missingVariant(Path root) throws Exception {
            assertUnavailable(() -> BundledCampaignRules.projection(CHECK_VARIANT));
            var standard = BundledCampaignRules.projection().members();
            var variant = new ArrayList<>(standard.subList(0, 3));
            variant.add(assertUnavailableCandidate(BundledCampaignRules.HOUSE_RULE_MODULE, "10"));
            try (var service = new CampaignStorageService(root)) {
                assertRejectedWithoutRun(root, service, LocalRuleLibraryException.class, "RULE_PACKAGE_MISSING",
                        () -> service.create("Unaccepted variant", variant));
                var created = service.create("Standard campaign");
                assertMembers(standard, service);
                var reopened = service.open(end(service));
                assertEquals(created.campaignId(), reopened.campaignId());
                assertMembers(standard, service);
                end(service);
                assertFalse(Files.exists(root.resolve("rule-library")));
            }
        }

        private static void mismatchedStandard(Path root, String standardFile, Path customSave) throws Exception {
            assertUnavailable(BundledCampaignRules::projection);
            assertUnavailableCandidate(BundledCampaignRules.HOUSE_RULE_MODULE, "9");
            try (var service = new CampaignStorageService(root)) {
                var standardMembers = assertStandardRejected(root, service, standardFile);
                var variant = BundledCampaignRules.projection(CHECK_VARIANT).members();
                assertEquals(standardMembers.subList(0, 3), variant.subList(0, 3));
                var variantOriginal = BundledCampaignRules.importCandidate(variant.getLast());
                assertEquals(variant.getLast(), importArtifact(service, variantOriginal));
                accept(service, variant.getLast());
                var created = service.create("Variant campaign", variant);
                assertMembers(variant, service);
                var reopened = service.open(end(service));
                assertEquals(created.campaignId(), reopened.campaignId());
                assertMembers(variant, service);
                end(service);

                openCustomWithLocalAcceptance(root.resolveSibling("custom-store"),
                        Files.readAllBytes(customSave), variant.subList(0, 3));
            }
        }

        private static void openCustomWithLocalAcceptance(Path root, byte[] portable,
                List<StoredMember> shared) throws Exception {
            var members = boundMembers(portable);
            try (var service = new CampaignStorageService(root)) {
                String file = service.importFile(portable).fileKey();
                assertRejectedWithoutRun(root, service, LocalRuleLibraryException.class, "RULE_PACKAGE_MISSING",
                        () -> service.open(file));
                for (var member : shared) {
                    assertEquals(member, importArtifact(service, BundledCampaignRules.importCandidate(member)));
                    accept(service, member);
                }
                try (var resource = BundledCampaignRules.class.getResourceAsStream("/storage/rules/" + STANDARD_ZIP + ".zip")) {
                    assertNotNull(resource);
                    var replacement = ProductionRuleArtifact.read(resource.readAllBytes());
                    assertEquals(members.getLast(), importArtifact(service, replacement));
                }
                assertRejectedWithoutRun(root, service, LocalRuleLibraryException.class, "RULE_PACKAGE_NOT_ACCEPTED",
                        () -> service.open(file));
                accept(service, members.getLast());
                assertEquals("Custom source", service.open(file).name());
                assertMembers(members, service);
                end(service);
                assertArrayEquals(portable, Files.readAllBytes(service.savedFile(file)));
            }
        }

        private static void missingShared(Path root) throws Exception {
            assertUnavailable(() -> BundledCampaignRules.projection(STANDARD));
            assertUnavailable(() -> BundledCampaignRules.projection(CHECK_VARIANT));
            assertUnavailableCandidate(BundledCampaignRules.CORE_MODULE, "5");
            try (var service = new CampaignStorageService(root)) {
                assertRejectedWithoutRun(root, service, StorageException.class, UNAVAILABLE,
                        () -> service.create("Unavailable standard"));
                assertFalse(Files.exists(root.resolve("rule-library")));
            }
        }

        private static void missingManifest(Path root, String file) throws Exception {
            assertUnavailable(() -> BundledCampaignRules.projection(CHECK_VARIANT));
            assertTrue(BundledCampaignRules.candidates().isEmpty());
            try (var service = new CampaignStorageService(root)) {
                var standard = assertStandardRejected(root, service, file);
                assertUnavailable(() -> BundledCampaignRules.importCandidate(standard.getLast()));
            }
        }
    }

    private static void assertResourceFile(String resource, Path expected) throws Exception {
        var resolved = BundledCampaignRules.class.getResource("/" + resource);
        assertNotNull(resolved, resource);
        assertTrue(Files.isSameFile(expected, Path.of(resolved.toURI())), resolved::toString);
    }

    private static String createStandardWithAcceptedRules(Path root) throws Exception {
        try (var service = new CampaignStorageService(root)) {
            service.create("Standard source");
            String file = end(service);
            CampaignRuleFixtures.install(service, STANDARD);
            return file;
        }
    }

    private static List<StoredMember> assertStandardRejected(Path root, CampaignStorageService service,
            String file) throws Exception {
        var original = Files.readAllBytes(service.savedFile(file));
        var members = boundMembers(original);
        assertEquals(4, service.ruleLibrary().readAccepted(members).size());
        assertRejectedWithoutRun(root, service, StorageException.class, UNAVAILABLE,
                () -> service.create("Unavailable standard"));
        assertRejectedWithoutRun(root, service, StorageException.class, UNAVAILABLE, () -> service.open(file));
        assertArrayEquals(original, Files.readAllBytes(service.savedFile(file)));
        return members;
    }

    private static StoredMember assertUnavailableCandidate(String module, String release) {
        var candidate = BundledCampaignRules.candidates().stream()
                .filter(item -> item.member().moduleKey().equals(module) && item.member().releaseVersion().equals(release))
                .findFirst().orElseThrow();
        assertFalse(candidate.available());
        assertUnavailable(() -> BundledCampaignRules.importCandidate(candidate.member()));
        return candidate.member();
    }

    private static void assertUnavailable(Executable action) {
        assertEquals(UNAVAILABLE, assertThrows(StorageException.class, action).code());
    }

    private static void assertRejectedWithoutRun(Path root, CampaignStorageService service,
            Class<? extends RuntimeException> type, String code, Executable action) throws Exception {
        var before = service.status();
        var error = assertThrows(type, action);
        assertEquals(code, error instanceof StorageException storage ? storage.code()
                : ((LocalRuleLibraryException) error).code());
        assertEquals(before, service.status());
        try (var entries = Files.list(root.resolve("runs"))) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    private static List<StoredMember> boundMembers(byte[] portable) {
        return new CampaignCodec().decode(portable).snapshot().domains().material().binding().members();
    }

    private static void assertMembers(List<StoredMember> expected, CampaignStorageService service) {
        assertEquals(expected, service.accessSnapshot().snapshot().domains().material().binding().members());
    }

    private static StoredMember importArtifact(CampaignStorageService service, ProductionRuleArtifact artifact) {
        return service.ruleLibrary().importArtifact(id(), artifact.originalZip()).imported().member();
    }

    private static void accept(CampaignStorageService service, StoredMember member) {
        service.ruleLibrary().accept(id(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
    }

    private static String end(CampaignStorageService service) {
        var current = service.current();
        return service.end(id(), current.epoch(), current.revision()).fileKey();
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static byte[] alternateHouseRules(ProductionRuleArtifact original) throws Exception {
        var members = new TreeMap<>(original.originalMembers());
        var rules = JsonParser.parseString(new String(members.get("rules.json"), StandardCharsets.UTF_8)).getAsJsonArray();
        rules.asList().stream().map(rule -> rule.getAsJsonObject())
                .filter(rule -> "vital_effect.dm_damage".equals(rule.getAsJsonObject("identity").get("rule_key").getAsString()))
                .findFirst().orElseThrow().addProperty("modifier", 3);
        members.put("rules.json", rules.toString().getBytes(StandardCharsets.UTF_8));
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var member : members.entrySet()) {
                byte[] content = member.getValue();
                var crc = new CRC32();
                crc.update(content);
                var entry = new ZipEntry(member.getKey());
                entry.setTimeLocal(LocalDateTime.of(2020, 1, 1, 0, 0));
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(content);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
