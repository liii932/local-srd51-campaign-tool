package com.dndtool.module.composition;

import com.dndtool.module.ToolCatalogOracle;
import com.dndtool.module.ToolPartition;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2Fixtures.canonical;
import static com.dndtool.module.composition.Schema2Fixtures.json;
import static org.junit.jupiter.api.Assertions.*;

/** Tools have independent field, real-ZIP, final-byte and complete-provenance oracles. */
class Schema2ToolCompositionTest {
    static final List<String> MODULES = List.of("tools_base", "tools_extension", "tools_compatibility");
    private static final Schema2CompositionKernel KERNEL = new Schema2CompositionKernel();

    @Test void realThirtySevenToolsRetainTheirExistingTypedPartitionFields() {
        var base = fixedArtifacts().getFirst().authorPackage();
        var actual = base.definitions().stream().filter(ToolDefinition.class::isInstance).map(ToolDefinition.class::cast).toList();
        var expected = ToolCatalogOracle.rows();
        assertEquals(37, actual.size());
        assertEquals(Arrays.stream(ToolPartition.Category.values()).map(Enum::name).toList(),
                Arrays.stream(ToolCategory.values()).map(Enum::name).toList());
        for (int i = 0; i < expected.size(); i++) {
            var a = actual.get(i); var e = expected.get(i);
            assertEquals("tools_base", a.identity().moduleKey()); assertEquals("character.tool", a.identity().ruleType());
            assertEquals(e.key(), a.identity().ruleKey()); assertEquals(e.name(), a.displayName());
            assertEquals(e.description(), a.description()); assertEquals(e.category(), a.category().name());
            assertEquals(e.page(), a.sourcePage());
            assertEquals(i == 0 ? "Base tool annotation." : null, a.authorNote());
        }
        assertEquals(37, base.entries().stream().filter(ToolEntry.class::isInstance).count());
        var language = base.entries().stream().filter(LanguageEntry.class::isInstance).map(LanguageEntry.class::cast).findFirst().orElseThrow();
        assertEquals(language.language(), language.target());
        assertEquals("character.language", language.target().ruleType());
        var tool = base.entries().stream().filter(ToolEntry.class::isInstance).map(ToolEntry.class::cast).findFirst().orElseThrow();
        assertEquals(tool.tool(), tool.target());
    }

    @Test void fixedRealZipsComposeAndRestoreToAllIndependentProductionBytesAndDigests() {
        var artifacts = fixedArtifacts(); var expected = json(resource("expected/digests.json")).getAsJsonObject();
        for (var artifact : artifacts) {
            assertArrayEquals(resource("expected/" + artifact.moduleKey() + "-logic.json"), artifact.logicalJson());
            assertEquals(expected.getAsJsonObject("raw").get(artifact.moduleKey()).getAsString(), artifact.rawZipSha256());
            assertEquals(expected.getAsJsonObject("logic").get(artifact.moduleKey()).getAsString(), artifact.logicalSha256());
        }
        var projection = ProductionSchema2Projection.compose(artifacts);
        assertProductionOracle(projection);
        assertEquals(40, projection.projection().rules().size()); assertEquals(39, projection.projection().entries().size());
        assertEquals(353, projection.projection().chains().size());
        assertEquals(366, projection.projection().chains().stream().mapToInt(c -> c.steps().size()).sum());
        var restored = ProductionSchema2Projection.restore(projection.members(), projection.projection().rules(),
                projection.projection().entries(), projection.projection().chains());
        assertProductionOracle(restored);
        assertDoesNotThrow(() -> ProductionSchema2Projection.verifyChains(artifacts, resource("expected/chains.json")));
        assertEquals("S2_BASE_INCOMPLETE", assertThrows(Schema2Exception.class,
                () -> KERNEL.requireProductionQualification(restored.projection())).diagnostic().code());
        var reference = projection.projection().chains().stream().filter(c -> c.field() == Field.TOOL
                && c.target().moduleKey().equals("tools_extension")).findFirst().orElseThrow();
        assertEquals(2, reference.steps().size());
        assertEquals(new IdentityValue(tool("vehicles_land")), reference.steps().getLast().after().value());
        var identical = projection.projection().chains().stream().filter(c -> c.field() == Field.CATEGORY
                && c.target().equals(tool("alchemist_supplies"))).findFirst().orElseThrow();
        assertEquals(2, identical.steps().size());
        assertEquals(identical.steps().getLast().before(), identical.steps().getLast().after());
    }

    @Test void toolDefinitionsAndMembersRejectMissingNullUnknownAndGenericFields() {
        for (String field : List.of("identity", "display_name", "description", "category", "source_page")) {
            var missing = packages(); edit(missing, 0, "rules.json", root -> row(root, 0).remove(field)); rejects("S2_SHAPE", missing);
            var nul = packages(); edit(nul, 0, "rules.json", root -> row(root, 0).add(field, JsonNull.INSTANCE)); rejects("S2_SHAPE", nul);
        }
        for (String field : List.of("sort_order", "attributes", "tool_key", "minimum_enabled_members", "unknown")) {
            var bad = packages(); edit(bad, 0, "rules.json", root -> row(root, 0).addProperty(field, 1)); rejects("S2_SHAPE", bad);
        }
        for (String field : List.of("identity", "tool")) {
            var bad = packages(); edit(bad, 0, "members.json", root -> row(root, 0).remove(field)); rejects("S2_SHAPE", bad);
        }
        var languageMember = packages(); edit(languageMember, 0, "members.json", root -> {
            var row = row(root, 0); row.add("language", row.remove("tool"));
        }); rejects("S2_SHAPE", languageMember);
        var both = packages(); edit(both, 0, "members.json", root -> row(root, 0).add("language", identity("tools_base", "character.language", "language.common")));
        rejects("S2_SHAPE", both);
        var owner = packages(); edit(owner, 0, "members.json", root -> row(root, 0).getAsJsonObject("identity").getAsJsonObject("rule").addProperty("rule_type", "character.tool"));
        rejects("S2_TYPE", owner);
        var collection = packages(); edit(collection, 0, "members.json", root -> row(root, 0).getAsJsonObject("identity").addProperty("collection_key", "tools"));
        rejects("S2_TYPE", collection);
    }

    @Test void toolTextUsesCodePointsNfcControlsAndTheExistingPageRange() {
        for (String field : List.of("display_name", "description", "author_note")) {
            int maximum = field.equals("display_name") ? 120 : 1000;
            var valid = packages(); edit(valid, 0, "rules.json", root -> row(root, 0).addProperty(field, "😀".repeat(maximum)));
            assertDoesNotThrow(() -> compose(valid));
            for (String value : List.of("", "\u00a0", "bad\u0085", "bad\u007f", "😀".repeat(maximum + 1))) {
                var bad = packages(); edit(bad, 0, "rules.json", root -> row(root, 0).addProperty(field, value)); rejects("S2_TEXT", bad);
            }
            var nfc = packages(); edit(nfc, 0, "rules.json", root -> row(root, 0).addProperty(field, "e\u0301"));
            var definition = (ToolDefinition) KERNEL.read(nfc.getFirst()).definitions().stream().filter(ToolDefinition.class::isInstance).findFirst().orElseThrow();
            assertEquals("é", switch (field) { case "display_name" -> definition.displayName(); case "description" -> definition.description(); default -> definition.authorNote(); });
        }
        for (int page : List.of(3, 74)) {
            var valid = packages(); edit(valid, 0, "rules.json", root -> row(root, 0).addProperty("source_page", page));
            assertDoesNotThrow(() -> KERNEL.read(valid.getFirst()));
        }
        for (String token : List.of("2", "75", "70.0", "7e1", "\"70\"", "true")) {
            var bad = packages(); String text = new String(bad.getFirst().get("rules.json"), StandardCharsets.UTF_8);
            bad.getFirst().put("rules.json", text.replaceFirst("\"source_page\": 70", "\"source_page\": " + token).getBytes(StandardCharsets.UTF_8));
            rejects("S2_TYPE", bad);
        }
    }

    @Test void toolsAndLanguagesCannotCrossTheirCategoryOrReferenceDomains() {
        for (String category : List.of("STANDARD", "UNKNOWN", "artisan", "KIT ")) {
            var bad = packages(); edit(bad, 0, "rules.json", root -> row(root, 0).addProperty("category", category)); rejects("S2_TYPE", bad);
            var changed = packages(); edit(changed, 2, "changes.json", root -> row(root, 4).addProperty("value", category)); rejects("S2_TYPE", changed);
        }
        var languageCategory = packages(); edit(languageCategory, 0, "rules.json", root -> row(root, 38).addProperty("category", "ARTISAN")); rejects("S2_TYPE", languageCategory);
        var wrongTarget = packages(); edit(wrongTarget, 0, "members.json", root -> row(root, 0).add("tool", identity("tools_base", "character.language", "language.common")));
        rejects("S2_TYPE", wrongTarget);
        var wrongOverride = packages(); edit(wrongOverride, 2, "changes.json", root -> row(root, 2).add("value", identity("tools_base", "character.language", "language.common")));
        rejects("S2_TYPE", wrongOverride);
        var wrongField = packages(); edit(wrongField, 2, "changes.json", root -> row(root, 2).addProperty("field", "language")); rejects("S2_FIELD_UNKNOWN", wrongField);
        var requiredClear = packages(); edit(requiredClear, 2, "changes.json", root -> { var change = row(root, 2); change.addProperty("op", "clear_field"); change.remove("value"); });
        rejects("S2_FIELD_NOT_CLEARABLE", requiredClear);
    }

    @Test void originalOwnershipDirectDependenciesAndKnownReferencesRemainRequired() {
        var missing = packages(); edit(missing, 0, "members.json", root -> row(root, 0).getAsJsonObject("tool").addProperty("rule_key", "tool.missing"));
        rejects("S2_REFERENCE_MISSING", missing);
        var wrongOwner = packages(); edit(wrongOwner, 0, "rules.json", root -> row(root, 0).getAsJsonObject("identity").addProperty("module_key", "tools_extension"));
        rejects("S2_ORIGINAL_OWNER", wrongOwner);
        var duplicate = packages(); edit(duplicate, 0, "rules.json", root -> root.getAsJsonArray().add(row(root, 0).deepCopy())); rejects("S2_DUPLICATE_IDENTITY", duplicate);
        var noDirect = packages(); edit(noDirect, 2, "author-package.json", root -> {
            var h = root.getAsJsonObject(); h.getAsJsonArray("dependencies").remove(0);
            h.getAsJsonArray("compatible_combinations").get(0).getAsJsonObject().remove("tools_base");
        }); rejects("S2_DIRECT_DEPENDENCY", noDirect);
        var entryOwner = packages(); edit(entryOwner, 1, "members.json", root -> row(root, 0).getAsJsonObject("identity").addProperty("module_key", "tools_base"));
        rejects("S2_ORIGINAL_OWNER", entryOwner);
    }

    @Test void enabledDistinctToolsDetermineBoundedMinimumEvenForADisabledSet() {
        for (String mutation : List.of("duplicate-count", "disabled-tool", "disabled-member", "disabled-set")) {
            var bad = packages();
            if (mutation.equals("duplicate-count")) edit(bad, 2, "changes.json", root -> row(root, 5).addProperty("value", 38));
            if (mutation.equals("disabled-tool")) edit(bad, 2, "changes.json", root -> row(root, 0).addProperty("value", false));
            if (mutation.equals("disabled-member")) edit(bad, 2, "changes.json", root -> row(root, 1).addProperty("value", false));
            if (mutation.equals("disabled-set")) edit(bad, 2, "changes.json", root -> {
                row(root, 5).addProperty("value", 38);
                var change = new JsonObject(); change.addProperty("op", "set_enabled");
                change.add("target", identity("tools_base", "catalog.tool_set", "tool_set.all")); change.addProperty("value", false);
                root.getAsJsonArray().add(change);
            });
            rejects("S2_CANDIDATES_INSUFFICIENT", bad);
        }
        for (int minimum : List.of(0, 129)) {
            var bad = packages(); edit(bad, 2, "changes.json", root -> row(root, 5).addProperty("value", minimum)); rejects("S2_TYPE", bad);
        }
        assertEquals("S2_CANDIDATES_INSUFFICIENT", assertThrows(Schema2Exception.class,
                () -> KERNEL.composeFiles(packages().subList(0, 2))).diagnostic().code());
    }

    @Test void storedToolRowsRejectNoncanonicalFieldsWrongEntryTypeAndMissingOrWrongTypedChains() {
        var original = ProductionSchema2Projection.compose(fixedArtifacts());
        for (String name : List.of("Changed", "e\u0301", "bad\u0085")) {
            var rules = new ArrayList<>(original.projection().rules()); int index = toolIndex(rules);
            var old = (FinalTool) rules.get(index); var d = old.definition();
            rules.set(index, new FinalTool(new ToolDefinition(d.identity(), name, d.description(), d.category(), d.sourcePage(), d.authorNote()), old.enabled()));
            assertThrows(RuntimeException.class, () -> restore(original, rules, original.projection().entries(), original.projection().chains()));
        }
        var entries = new ArrayList<>(original.projection().entries()); int index = 0;
        while (!(entries.get(index).entry() instanceof ToolEntry)) index++;
        var old = entries.get(index);
        entries.set(index, new FinalEntry(new LanguageEntry(old.entry().identity(), old.entry().target(), old.entry().authorNote()), old.enabled()));
        assertThrows(RuntimeException.class, () -> restore(original, original.projection().rules(), entries, original.projection().chains()));
        for (String mutation : List.of("missing", "language-field", "language-reference")) {
            var chains = new ArrayList<>(original.projection().chains()); int chainIndex = 0;
            while (chains.get(chainIndex).field() != Field.TOOL) chainIndex++;
            var chain = chains.get(chainIndex);
            if (mutation.equals("missing")) chains.remove(chainIndex);
            else if (mutation.equals("language-field")) chains.set(chainIndex, new FieldChain(chain.target(), Field.LANGUAGE, chain.steps()));
            else {
                var steps = new ArrayList<>(chain.steps()); var step = steps.getFirst();
                steps.set(0, new ChainStep(step.op(), step.before(), new FieldState(StateKind.PRESENT,
                        new IdentityValue(new RuleIdentity("tools_base", "character.language", "language.common"))),
                        step.provider(), step.loadIndex(), step.authorLocation(), step.origin()));
                chains.set(chainIndex, new FieldChain(chain.target(), chain.field(), steps));
            }
            assertThrows(RuntimeException.class, () -> restore(original, original.projection().rules(), original.projection().entries(), chains), mutation);
        }
    }

    @Test void storedToolReferenceCannotPrecedeItsDeclarationEvenWithConsistentFinalRowsAndChains() {
        var files = packages();
        var header = json(files.get(1).get("author-package.json")).getAsJsonObject();
        var declaration = new JsonObject(); declaration.addProperty("kind", "definitions"); declaration.addProperty("path", "future.json");
        header.getAsJsonArray("files").add(declaration); files.get(1).put("author-package.json", canonical(header));
        var definition = new JsonObject(); definition.add("identity", identity("tools_extension", "character.tool", "tool.future"));
        definition.addProperty("display_name", "Future tool"); definition.addProperty("description", "Tool declared by the extension.");
        definition.addProperty("category", "KIT"); definition.addProperty("source_page", 70);
        var definitions = new JsonArray(); definitions.add(definition); files.get(1).put("future.json", canonical(definitions));
        var artifacts = files.stream().map(f -> ProductionRuleArtifact.read(Schema2ValidationCodec.rawZip(f))).toList();
        var original = ProductionSchema2Projection.compose(artifacts);
        var chains = new ArrayList<>(original.projection().chains()); int index = 0;
        while (chains.get(index).field() != Field.TOOL || !chains.get(index).target().moduleKey().equals("tools_base")) index++;
        var chain = chains.get(index); var initial = chain.steps().getFirst(); var compat = original.members().get(2);
        var future = new FieldState(StateKind.PRESENT, new IdentityValue(new RuleIdentity("tools_extension", "character.tool", "tool.future")));
        var steps = List.of(new ChainStep("initial", initial.before(), future, initial.provider(), initial.loadIndex(), initial.authorLocation(), initial.origin()),
                new ChainStep("set_field", future, initial.after(), new Provider(compat.moduleKey(), compat.releaseVersion()), 2,
                        new Location(compat.moduleKey(), compat.releaseVersion(), compat.rawZipSha256(), "changes.json", "/7", null), "explicit"));
        chains.set(index, new FieldChain(chain.target(), Field.TOOL, steps));
        assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class,
                () -> restore(original, original.projection().rules(), original.projection().entries(), chains)).diagnostic().code());
    }

    @Test void toolSetsDoNotOpenUnknownRulesOrMechanismExecution() {
        for (String type : List.of("character.unknown", "character.tool_use", "mechanism.tool_proficiency")) {
            var bad = packages(); edit(bad, 0, "rules.json", root -> row(root, 0).getAsJsonObject("identity").addProperty("rule_type", type));
            rejects("S2_TYPE", bad);
        }
        for (String field : List.of("display_name", "minimum_enabled_members")) {
            var bad = packages(); edit(bad, 0, "rules.json", root -> row(root, 37).remove(field)); rejects("S2_SHAPE", bad);
        }
        var wrong = packages(); edit(wrong, 0, "rules.json", root -> row(root, 37).addProperty("description", "A generic bag is forbidden."));
        rejects("S2_SHAPE", wrong);
    }

    private static int toolIndex(List<FinalRule> rules) {
        int index = 0; while (!(rules.get(index) instanceof FinalTool)) index++; return index;
    }
    private static ProductionSchema2Projection restore(ProductionSchema2Projection original, List<FinalRule> rules,
                                                       List<FinalEntry> entries, List<FieldChain> chains) {
        return ProductionSchema2Projection.restore(original.members(), rules, entries, chains);
    }
    private static void assertProductionOracle(ProductionSchema2Projection actual) {
        assertArrayEquals(resource("expected/configuration.json"), actual.configurationJson());
        assertArrayEquals(resource("expected/final-rules.json"), actual.finalRulesJson());
        assertArrayEquals(resource("expected/chains.json"), actual.chainsJson());
        var expected = json(resource("expected/digests.json")).getAsJsonObject();
        assertEquals(expected.get("config").getAsString(), actual.compositionSha256());
        assertEquals(expected.get("rules").getAsString(), actual.finalRulesSha256());
        assertEquals(expected.get("chain").getAsString(), actual.chainSha256());
    }
    static byte[] resource(String path) {
        try (var input = Schema2ToolCompositionTest.class.getResourceAsStream("/schema2-tools/" + path)) {
            if (input == null) throw new AssertionError("Missing fixed tool fixture " + path);
            return input.readAllBytes();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
    static List<ProductionRuleArtifact> fixedArtifacts() {
        return MODULES.stream().map(module -> ProductionRuleArtifact.read(HexFormat.of().parseHex(
                new String(resource(module + ".zip.hex"), StandardCharsets.US_ASCII).strip()))).toList();
    }
    private static List<Map<String, byte[]>> packages() {
        var result = new ArrayList<Map<String, byte[]>>();
        for (String module : MODULES) {
            var files = new LinkedHashMap<String, byte[]>(); files.put("author-package.json", resource(module + "/author-package.json"));
            for (var declaration : json(files.get("author-package.json")).getAsJsonObject().getAsJsonArray("files")) {
                String path = declaration.getAsJsonObject().get("path").getAsString(); files.put(path, resource(module + "/" + path));
            }
            result.add(files);
        }
        return result;
    }
    private static Projection compose(List<Map<String, byte[]>> packages) { return KERNEL.composeFiles(packages); }
    private static JsonObject row(JsonElement root, int index) { return root.getAsJsonArray().get(index).getAsJsonObject(); }
    private static void edit(List<Map<String, byte[]>> p, int index, String path, Consumer<JsonElement> mutation) {
        var json = json(p.get(index).get(path)); mutation.accept(json); p.get(index).put(path, canonical(json));
    }
    private static JsonObject identity(String module, String type, String key) {
        var result = new JsonObject(); result.addProperty("kind", "rule"); result.addProperty("module_key", module);
        result.addProperty("rule_type", type); result.addProperty("rule_key", key); return result;
    }
    private static RuleIdentity tool(String key) { return new RuleIdentity("tools_base", "character.tool", "tool." + key); }
    private static void rejects(String code, List<Map<String, byte[]>> packages) {
        assertEquals(code, assertThrows(Schema2Exception.class, () -> compose(packages)).diagnostic().code());
    }
}
