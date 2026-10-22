package com.dndtool.module.composition;

import com.google.gson.JsonArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionRuleArtifactTest {
    @Test
    void originalDeflatedBytesAndIndependentProductionEncodingVectorsMatchExactly() {
        var expected = json(productionResource("expected/digests.json")).getAsJsonObject();
        var artifacts = fixedArtifacts();
        for (int i = 0; i < artifacts.size(); i++) {
            var artifact = artifacts.get(i);
            String module = MODULES.get(i);
            assertArrayEquals(fixedZip(module), artifact.originalZip());
            assertEquals(expected.getAsJsonObject("raw").get(module).getAsString(), artifact.rawZipSha256());
            assertEquals(sha(artifact.originalZip()), artifact.rawZipSha256());
            assertArrayEquals(productionResource("expected/" + module + "-logic.json"), artifact.logicalJson());
            assertEquals(expected.getAsJsonObject("logic").get(module).getAsString(), artifact.logicalSha256());
            assertNotEquals(artifact.authorPackage().logicalSha256(), artifact.logicalSha256());
            assertNotEquals(KERNEL.read(packages().get(i)).rawZipSha256(), artifact.rawZipSha256());
            assertEquals("schema2-production-json-1", artifact.canonicalProfile());
            assertEquals("Finite schema-2 transport fixture. No qualification.\n",
                    new String(artifact.originalMembers().get("NOTICE"), StandardCharsets.UTF_8));
        }
        var projection = ProductionSchema2Projection.compose(artifacts);
        assertArrayEquals(productionResource("expected/configuration.json"), projection.configurationJson());
        assertArrayEquals(productionResource("expected/final-rules.json"), projection.finalRulesJson());
        assertArrayEquals(productionResource("expected/chains.json"), projection.chainsJson());
        assertEquals(expected.get("config").getAsString(), projection.compositionSha256());
        assertEquals(expected.get("rules").getAsString(), projection.finalRulesSha256());
        assertEquals(expected.get("chain").getAsString(), projection.chainSha256());
        assertDoesNotThrow(() -> ProductionSchema2Projection.verifyChains(artifacts, projection.chainsJson()));
        for (var chain : projection.projection().chains()) for (var step : chain.steps()) {
            var provider = artifacts.stream().filter(a -> a.moduleKey().equals(step.provider().moduleKey())).findFirst().orElseThrow();
            assertEquals(provider.rawZipSha256(), step.authorLocation().rawZipSha256());
        }
        var failure = assertThrows(Schema2CompositionKernel.Schema2Exception.class,
                () -> KERNEL.requireProductionQualification(projection.projection()));
        assertEquals("S2_BASE_INCOMPLETE", failure.diagnostic().code());
    }

    @Test
    void repackMetadataAndFileSplittingPreserveLogicAndFinalRulesWhileBindingOriginalSources() {
        var original = fixedArtifacts();
        var before = ProductionSchema2Projection.compose(original);
        for (String mutation : List.of("stored", "metadata", "split", "format")) {
            var files = packages();
            if (mutation.equals("metadata")) files.getFirst().put("README.md", "Bounded metadata.\n".getBytes(StandardCharsets.UTF_8));
            if (mutation.equals("format")) raw(files, 0, "rules.json", "\n " + new String(files.getFirst().get("rules.json"), StandardCharsets.UTF_8) + "\n");
            if (mutation.equals("split")) {
                var definitions = json(files, 0, "rules.json").getAsJsonArray();
                var moved = new JsonArray(); moved.add(definitions.remove(2));
                put(files, 0, "rules.json", definitions); register(files, 0, "second-rules.json", "definitions", moved);
            }
            var changed = new ArrayList<>(original);
            changed.set(0, ProductionRuleArtifact.read(zip(files.getFirst(), mutation.equals("stored") ? 0 : 8)));
            var after = ProductionSchema2Projection.compose(changed);
            assertEquals(original.getFirst().logicalSha256(), changed.getFirst().logicalSha256(), mutation);
            assertNotEquals(original.getFirst().rawZipSha256(), changed.getFirst().rawZipSha256(), mutation);
            assertArrayEquals(before.finalRulesJson(), after.finalRulesJson(), mutation);
            assertNotEquals(before.compositionSha256(), after.compositionSha256(), mutation);
            assertNotEquals(before.chainSha256(), after.chainSha256(), mutation);
        }
    }

    @Test
    void completeProductionChainsRetainSameValueOperationsAndRejectTamperingAndValidationEnvelopes() {
        var artifacts = fixedArtifacts();
        var before = ProductionSchema2Projection.compose(artifacts);
        var files = packages(); edit(files, 2, "changes.json", value -> value.getAsJsonArray().remove(0));
        var changed = new ArrayList<>(artifacts); changed.set(2, ProductionRuleArtifact.read(zip(files.get(2), 8)));
        var after = ProductionSchema2Projection.compose(changed);
        assertArrayEquals(before.finalRulesJson(), after.finalRulesJson());
        assertNotEquals(before.chainSha256(), after.chainSha256());
        assertNotEquals(artifacts.get(2).logicalSha256(), changed.get(2).logicalSha256());
        var altered = json(before.chainsJson());
        altered.getAsJsonObject().getAsJsonArray("chains").get(0).getAsJsonObject().getAsJsonArray("steps")
                .get(0).getAsJsonObject().getAsJsonObject("author_location").addProperty("pointer", "/999");
        for (byte[] invalid : List.of(canonical(altered), KERNEL.compose(artifacts.stream().map(ProductionRuleArtifact::authorPackage).toList()).chainsJson())) {
            var failure = assertThrows(Schema2CompositionKernel.Schema2Exception.class,
                    () -> ProductionSchema2Projection.verifyChains(artifacts, invalid));
            assertEquals("S2_CHAIN_MISMATCH", failure.diagnostic().code());
        }
        var reversed = List.of(artifacts.get(0), artifacts.get(2), artifacts.get(1));
        assertThrows(Schema2CompositionKernel.Schema2Exception.class, () -> ProductionSchema2Projection.compose(reversed));
    }

    @Test
    void validIndependentExtensionOrdersBindConfigurationAndFullChainsWithoutChangingFinalRules() {
        var base = ProductionRuleArtifact.read(zip(packages().getFirst(), 0));
        var first = independentExtension("first_extension", 0);
        var second = independentExtension("second_extension", 1);
        var forward = ProductionSchema2Projection.compose(List.of(base, first, second));
        var reverse = ProductionSchema2Projection.compose(List.of(base, second, first));
        assertArrayEquals(forward.finalRulesJson(), reverse.finalRulesJson());
        assertEquals(forward.finalRulesSha256(), reverse.finalRulesSha256());
        assertNotEquals(forward.compositionSha256(), reverse.compositionSha256());
        assertNotEquals(forward.chainSha256(), reverse.chainSha256());
        var budgetOnly = new java.util.AbstractList<ProductionRuleArtifact>() {
            @Override public int size() { return 5; }
            @Override public ProductionRuleArtifact get(int index) { throw new AssertionError("Oversized selection must not be traversed"); }
        };
        var failure = assertThrows(Schema2CompositionKernel.Schema2Exception.class, () -> ProductionSchema2Projection.compose(budgetOnly));
        assertEquals("S2_BUDGET", failure.diagnostic().code());
    }

    @Test
    void compressedBodyMayExpandBeyondEntireZipAndSnapshotsRemainImmutable() {
        var files = packages().getFirst();
        byte[] rules = files.get("rules.json"); byte[] padded = Arrays.copyOf(rules, 10000);
        Arrays.fill(padded, rules.length, padded.length, (byte) ' '); files.put("rules.json", padded);
        byte[] transport = zip(files, 8);
        assertTrue(padded.length > transport.length);
        var artifact = ProductionRuleArtifact.read(transport);
        byte[] exact = transport.clone(); Arrays.fill(transport, (byte) 0);
        for (byte[] bytes : files.values()) Arrays.fill(bytes, (byte) 0);
        assertArrayEquals(exact, artifact.originalZip());
        String logic = artifact.logicalSha256(); Arrays.fill(artifact.originalZip(), (byte) 0); Arrays.fill(artifact.logicalJson(), (byte) 0);
        assertEquals(logic, artifact.logicalSha256()); assertArrayEquals(exact, artifact.originalZip());
        var members = artifact.originalMembers(); Arrays.fill(members.get("rules.json"), (byte) 0);
        assertEquals('[', artifact.originalMembers().get("rules.json")[0]);
        assertThrows(UnsupportedOperationException.class, members::clear);
        var projection = ProductionSchema2Projection.compose(List.of(artifact));
        byte[] config = projection.configurationJson(), finals = projection.finalRulesJson(), chains = projection.chainsJson();
        Arrays.fill(projection.configurationJson(), (byte) 0); Arrays.fill(projection.finalRulesJson(), (byte) 0); Arrays.fill(projection.chainsJson(), (byte) 0);
        assertArrayEquals(config, projection.configurationJson()); assertArrayEquals(finals, projection.finalRulesJson()); assertArrayEquals(chains, projection.chainsJson());
    }

    @Test
    void sharedStrictAuthorReaderNormalizesUnicodeAndReportsActualOriginalZipForFailures() {
        var nfc = packages(); var nfd = packages();
        edit(nfc, 0, "rules.json", rows -> row(rows, 0).addProperty("display_name", "é"));
        edit(nfd, 0, "rules.json", rows -> row(rows, 0).addProperty("display_name", "e\u0301"));
        var composed = ProductionRuleArtifact.read(zip(nfc.getFirst(), 8));
        var decomposed = ProductionRuleArtifact.read(zip(nfd.getFirst(), 8));
        assertEquals(composed.logicalSha256(), decomposed.logicalSha256());
        assertNotEquals(composed.rawZipSha256(), decomposed.rawZipSha256());
        var supplementary = packages(); edit(supplementary, 0, "rules.json", rows -> row(rows, 0).addProperty("display_name", "😀".repeat(120)));
        assertDoesNotThrow(() -> ProductionRuleArtifact.read(zip(supplementary.getFirst(), 8)));
        for (String mutation : List.of("control", "overlong", "utf8", "missing", "manifest")) {
            var files = packages();
            switch (mutation) {
                case "control" -> edit(files, 0, "rules.json", rows -> row(rows, 0).addProperty("display_name", "bad\u0085"));
                case "overlong" -> edit(files, 0, "rules.json", rows -> row(rows, 0).addProperty("display_name", "😀".repeat(121)));
                case "utf8" -> files.getFirst().put("rules.json", new byte[]{(byte) 0xc0, (byte) 0xaf});
                case "missing" -> files.getFirst().remove("author-package.json");
                case "manifest" -> files.getFirst().put("undeclared.json", new byte[]{'[', ']'});
            }
            byte[] transport = zip(files.getFirst(), 8);
            var diagnostic = rejectedZip(transport);
            assertFalse(diagnostic.locations().isEmpty());
            for (var location : diagnostic.locations()) assertEquals(sha(transport), location.rawZipSha256(), mutation);
        }
    }

    @Test
    void rejectsUnsafePathsDuplicatesCaseCollisionsAndUnlistedOrMalformedMetadata() {
        for (String path : List.of("../bad.json", "/bad.json", "C:bad.json", "a\\bad.json", "a//bad.json", "a/./bad.json", "a/../bad.json", "folder/", "bad name.json", "é.json", "bad.json.", "RULES.json", "unexpected.txt")) {
            var files = new LinkedHashMap<>(packages().getFirst()); files.put(path, new byte[]{'[', ']'});
            rejectedZip(zip(files, 0));
        }
        var duplicates = new ArrayList<>(packages().getFirst().entrySet()); duplicates.add(duplicates.getFirst());
        rejectedZip(zipEntries(duplicates, 0));
        for (byte[] body : List.of(new byte[]{0}, new byte[]{(byte) 0xff}, "\ufeffnotice".getBytes(StandardCharsets.UTF_8))) {
            var files = new LinkedHashMap<>(packages().getFirst()); files.put("NOTICE", body); rejectedZip(zip(files, 0));
        }
        var files = packages(); edit(files, 0, "author-package.json", root -> {
            var declaration = new com.google.gson.JsonObject(); declaration.addProperty("kind", "definitions"); declaration.addProperty("path", "NOTICE");
            root.getAsJsonObject().getAsJsonArray("files").add(declaration);
        });
        files.getFirst().put("NOTICE", new byte[]{'[', ']'}); rejectedZip(zip(files.getFirst(), 0));
    }

    @Test
    void rejectsCentralLocalDisagreementEncryptionDescriptorsSymlinksZip64OverlapAndTrailingBytes() {
        byte[] fixed = zip(packages().getFirst(), 0);
        int central = central(fixed);
        for (String mutation : List.of("flags", "descriptor", "method", "symlink", "zip64", "local-crc", "crc", "local-name", "version", "extra", "comment", "disk", "overlap", "count", "suffix", "truncated", "prefix")) {
            byte[] bad = fixed.clone();
            switch (mutation) {
                case "flags" -> { set16(bad, 6, 1); set16(bad, central + 8, 1); }
                case "descriptor" -> { set16(bad, 6, 8); set16(bad, central + 8, 8); }
                case "method" -> { set16(bad, 8, 12); set16(bad, central + 10, 12); }
                case "symlink" -> set32(bad, central + 38, 0120777L << 16);
                case "zip64" -> set32(bad, central + 24, 0xffffffffL);
                case "local-crc" -> bad[14] ^= 1;
                case "crc" -> { bad[14] ^= 1; bad[central + 16] ^= 1; }
                case "local-name" -> bad[30] ^= 1;
                case "version" -> set16(bad, 4, 10);
                case "extra" -> set16(bad, central + 30, 1);
                case "comment" -> set16(bad, central + 32, 1);
                case "disk" -> set16(bad, central + 34, 1);
                case "overlap" -> set32(bad, central + 46 + u16(bad, central + 28) + 42, 0);
                case "count" -> { set16(bad, bad.length - 22 + 8, 1); set16(bad, bad.length - 22 + 10, 1); }
                case "suffix" -> bad = Arrays.copyOf(bad, bad.length + 1);
                case "truncated" -> bad = Arrays.copyOf(bad, bad.length - 1);
                case "prefix" -> { bad = new byte[fixed.length + 1]; System.arraycopy(fixed, 0, bad, 1, fixed.length); }
            }
            var diagnostic = rejectedZip(bad);
            assertEquals("S2_ZIP", diagnostic.code(), mutation);
            assertEquals(sha(bad), diagnostic.locations().getFirst().rawZipSha256(), mutation);
        }
    }

    @Test
    void rejectsRawAndExpansionBudgetsBeforeAuthorParsingAndDeflateSizeMismatch() {
        var tooLarge = new byte[ProductionRuleArtifact.MAX_ZIP_BYTES + 1];
        assertEquals("S2_BUDGET", rejectedZip(tooLarge).code());
        for (String mutation : List.of("header", "file", "metadata", "ratio", "count")) {
            var files = new LinkedHashMap<>(packages().getFirst());
            switch (mutation) {
                case "header" -> files.put("author-package.json", new byte[16385]);
                case "file" -> files.put("rules.json", new byte[65537]);
                case "metadata" -> files.put("NOTICE", new byte[32769]);
                case "ratio" -> files.put("rules.json", new byte[65536]);
                case "count" -> { for (int i = 0; i < 20; i++) files.put("extra" + i + ".json", new byte[0]); }
            }
            assertEquals("S2_BUDGET", rejectedZip(zip(files, mutation.equals("ratio") ? 8 : 0)).code(), mutation);
        }
        var invalid = new LinkedHashMap<>(packages().getFirst()); invalid.put("author-package.json", new byte[]{'!'}); invalid.put("rules.json", new byte[65537]);
        assertEquals("S2_BUDGET", rejectedZip(zip(invalid, 0)).code());
        byte[] deflated = zip(packages().getFirst(), 8); int central = central(deflated);
        int declared = (int) u32(deflated, central + 24);
        set32(deflated, 22, declared - 1); set32(deflated, central + 24, declared - 1);
        assertEquals("S2_ZIP", rejectedZip(deflated).code());
    }

    static List<ProductionRuleArtifact> fixedArtifacts() { return MODULES.stream().map(module -> ProductionRuleArtifact.read(fixedZip(module))).toList(); }
    private static ProductionRuleArtifact independentExtension(String module, int changeIndex) {
        var input = packages(); var header = header(input, 1);
        header.addProperty("module_key", module);
        header.getAsJsonArray("load_order_constraints").get(0).getAsJsonObject().addProperty("after", module);
        var files = new JsonArray(); files.add(header.getAsJsonArray("files").get(2).deepCopy()); header.add("files", files);
        var changes = new JsonArray(); changes.add(json(input, 1, "changes.json").getAsJsonArray().get(changeIndex).deepCopy());
        return ProductionRuleArtifact.read(zip(Map.of("author-package.json", canonical(header), "changes.json", canonical(changes)), 8));
    }
    static byte[] fixedZip(String module) { return HexFormat.of().parseHex(new String(productionResource(module + ".zip.hex"), StandardCharsets.US_ASCII).strip()); }
    static byte[] productionResource(String path) {
        try (var input = ProductionRuleArtifactTest.class.getResourceAsStream("/schema2-production/" + path)) {
            if (input == null) throw new AssertionError("Missing fixed production fixture " + path);
            return input.readAllBytes();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
    private static Schema2CompositionKernel.Diagnostic rejectedZip(byte[] transport) {
        return assertThrows(Schema2CompositionKernel.Schema2Exception.class, () -> ProductionRuleArtifact.read(transport)).diagnostic();
    }
    static byte[] zip(Map<String, byte[]> files, int method) { return zipEntries(new ArrayList<>(new TreeMap<>(files).entrySet()), method); }
    private static byte[] zipEntries(List<Map.Entry<String, byte[]>> files, int method) {
        var local = new ByteArrayOutputStream(); var central = new ByteArrayOutputStream();
        for (var file : files) {
            byte[] name = file.getKey().getBytes(StandardCharsets.UTF_8), body = file.getValue(), compressed = method == 0 ? body : deflate(body);
            var crc = new CRC32(); crc.update(body); int offset = local.size();
            u32(local, 0x04034b50); u16(local, 20); u16(local, 0); u16(local, method); u16(local, 0); u16(local, 33);
            u32(local, crc.getValue()); u32(local, compressed.length); u32(local, body.length); u16(local, name.length); u16(local, 0); local.writeBytes(name); local.writeBytes(compressed);
            u32(central, 0x02014b50); u16(central, 0x0314); u16(central, 20); u16(central, 0); u16(central, method); u16(central, 0); u16(central, 33);
            u32(central, crc.getValue()); u32(central, compressed.length); u32(central, body.length); u16(central, name.length); u16(central, 0); u16(central, 0); u16(central, 0); u16(central, 0); u32(central, 0100644L << 16); u32(central, offset); central.writeBytes(name);
        }
        int offset = local.size(); local.writeBytes(central.toByteArray()); u32(local, 0x06054b50); u16(local, 0); u16(local, 0);
        u16(local, files.size()); u16(local, files.size()); u32(local, central.size()); u32(local, offset); u16(local, 0); return local.toByteArray();
    }
    private static byte[] deflate(byte[] body) {
        var deflater = new Deflater(6, true); var result = new ByteArrayOutputStream();
        try { deflater.setInput(body); deflater.finish(); byte[] chunk = new byte[1024]; while (!deflater.finished()) result.write(chunk, 0, deflater.deflate(chunk)); }
        finally { deflater.end(); } return result.toByteArray();
    }
    private static int central(byte[] zip) { return (int) u32(zip, zip.length - 22 + 16); }
    private static int u16(byte[] bytes, int at) { return (bytes[at] & 255) | (bytes[at + 1] & 255) << 8; }
    private static long u32(byte[] bytes, int at) { return Integer.toUnsignedLong(u16(bytes, at) | u16(bytes, at + 2) << 16); }
    private static void set16(byte[] bytes, int at, int value) { bytes[at] = (byte) value; bytes[at + 1] = (byte) (value >>> 8); }
    private static void set32(byte[] bytes, int at, long value) { set16(bytes, at, (int) value); set16(bytes, at + 2, (int) (value >>> 16)); }
    private static void u16(ByteArrayOutputStream out, long value) { out.write((int) value & 255); out.write((int) (value >>> 8) & 255); }
    private static void u32(ByteArrayOutputStream out, long value) { u16(out, value); u16(out, value >>> 16); }
}
