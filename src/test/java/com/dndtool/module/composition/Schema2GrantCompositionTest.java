package com.dndtool.module.composition;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2GrantFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2GrantCompositionTest {
    private static final Schema2CompositionKernel KERNEL = new Schema2CompositionKernel();

    @Test void fixedIndependentBytesCoverAllFieldsChainsAndFiveDigestKinds() {
        var artifacts = fixedArtifacts();
        var expected = json(resource("expected/digests.json")).getAsJsonObject();
        for (var artifact : artifacts) {
            assertArrayEquals(resource("expected/" + artifact.moduleKey() + "-logic.json"), artifact.logicalJson());
            assertEquals(expected.getAsJsonObject("raw").get(artifact.moduleKey()).getAsString(), artifact.rawZipSha256());
            assertEquals(expected.getAsJsonObject("logic").get(artifact.moduleKey()).getAsString(), artifact.logicalSha256());
        }
        var actual = ProductionSchema2Projection.compose(artifacts);
        assertArrayEquals(resource("expected/configuration.json"), actual.configurationJson());
        assertArrayEquals(resource("expected/final-rules.json"), actual.finalRulesJson());
        assertArrayEquals(resource("expected/chains.json"), actual.chainsJson());
        assertEquals(expected.get("config").getAsString(), actual.compositionSha256());
        assertEquals(expected.get("rules").getAsString(), actual.finalRulesSha256());
        assertEquals(expected.get("chain").getAsString(), actual.chainSha256());
        assertEquals(15, actual.projection().rules().size());
        assertEquals(18, actual.projection().entries().size());
        assertEquals(135, actual.projection().chains().size());
        assertEquals(152, actual.projection().chains().stream().mapToInt(c -> c.steps().size()).sum());
        var restored = restore(actual, actual.projection().rules(), actual.projection().entries(), actual.projection().chains());
        assertArrayEquals(actual.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(actual.chainsJson(), restored.chainsJson());
        ProductionSchema2Projection.verifyChains(artifacts, resource("expected/chains.json"));
        assertEquals(6, actual.projection().rules().stream().filter(FinalCharacterSource.class::isInstance).count());
        assertEquals(9, actual.projection().entries().stream().filter(e -> e.entry() instanceof ProficiencyGrantEntry).count());
        assertThrows(Schema2Exception.class, () -> KERNEL.requireProductionQualification(actual.projection()));
    }

    @Test void grantsAreOneAtomicClosedValueAndPreserveSourceAndTargetIdentities() {
        var projection = ProductionSchema2Projection.compose(fixedArtifacts()).projection();
        var chain = projection.chains().stream().filter(c -> c.field() == Field.GRANT
                && c.target() instanceof EntryIdentity e && e.entryKey().equals("shared_language")).findFirst().orElseThrow();
        assertEquals(List.of(FixedGrant.class, ChoiceGrant.class, FixedGrant.class), chain.steps().stream()
                .map(step -> ((GrantValue) step.after().value()).value().getClass()).toList());
        assertEquals(List.of("/9/grant", "/4", "/3"), chain.steps().stream().map(s -> s.authorLocation().pointer()).toList());
        var commonFacts = projection.entries().stream().filter(e -> e.entry() instanceof ProficiencyGrantEntry g
                && g.grant() instanceof FixedGrant f && f.target().ruleKey().equals("language.common")).toList();
        assertEquals(3, commonFacts.size());
        assertEquals(2, commonFacts.stream().map(e -> e.entry().target()).distinct().count());
        assertEquals(2, commonFacts.stream().map(e -> e.entry().identity().rule()).distinct().count());
        var noteChain = projection.chains().stream().filter(c -> c.field() == Field.AUTHOR_NOTE
                && c.target() instanceof RuleIdentity r && r.ruleKey().equals("race.human")).findFirst().orElseThrow();
        assertEquals(StateKind.ABSENT, noteChain.steps().getLast().after().state());
    }

    @Test void malformedSourceAndGrantFieldsFailClosedAtTheAtomicBoundary() {
        for (String mutation : List.of("missing-parent", "wrong-parent", "parent-on-race", "source-bag", "grant-bag",
                "source-type", "unknown-kind", "missing-target", "missing-count", "null-grant", "mixed-kind", "wrong-category",
                "wrong-target-type", "wrong-candidate-type", "boolean-count", "fractional-count", "zero-count", "over-count", "nested-field", "clear-grant")) {
            var files = packages();
            switch (mutation) {
                case "missing-parent" -> edit(files, 0, "rules.json", root -> find(root, "subrace.north").remove("parent_race"));
                case "wrong-parent" -> edit(files, 0, "rules.json", root -> find(root, "subrace.north").add("parent_race", rule(BASE, "character.class", "class.scholar")));
                case "parent-on-race" -> edit(files, 0, "rules.json", root -> find(root, "race.human").add("parent_race", rule(BASE, "character.race", "race.human")));
                case "source-bag" -> edit(files, 0, "rules.json", root -> find(root, "race.human").addProperty("speed", 30));
                case "source-type" -> edit(files, 0, "rules.json", root -> find(root, "race.human").getAsJsonObject("identity").addProperty("rule_type", "character.feat"));
                case "grant-bag" -> edit(files, 0, "members.json", root -> find(root, "native_language").getAsJsonObject("grant").addProperty("spellcasting", true));
                case "unknown-kind" -> edit(files, 0, "members.json", root -> find(root, "native_language").getAsJsonObject("grant").addProperty("kind", "FIXED_AND_CHOICE"));
                case "missing-target" -> edit(files, 0, "members.json", root -> find(root, "native_language").getAsJsonObject("grant").remove("target"));
                case "missing-count" -> edit(files, 0, "members.json", root -> find(root, "extra_languages").getAsJsonObject("grant").remove("count"));
                case "null-grant" -> edit(files, 0, "members.json", root -> find(root, "native_language").add("grant", com.google.gson.JsonNull.INSTANCE));
                case "mixed-kind" -> edit(files, 0, "members.json", root -> find(root, "extra_languages").getAsJsonObject("grant").add("target", rule(BASE, "character.language", "language.common")));
                case "wrong-category" -> edit(files, 0, "members.json", root -> find(root, "native_language").getAsJsonObject("grant").addProperty("category", "SKILL"));
                case "wrong-target-type" -> edit(files, 0, "members.json", root -> find(root, "native_language").getAsJsonObject("grant").add("target", rule(BASE, "character.tool", "tool.flute")));
                case "wrong-candidate-type" -> edit(files, 0, "members.json", root -> find(root, "extra_languages").getAsJsonObject("grant").add("candidates", rule(BASE, "catalog.tool_set", "tool_set.initial")));
                case "boolean-count" -> edit(files, 0, "members.json", root -> find(root, "extra_languages").getAsJsonObject("grant").addProperty("count", true));
                case "fractional-count" -> edit(files, 0, "members.json", root -> find(root, "extra_languages").getAsJsonObject("grant").addProperty("count", 1.5));
                case "zero-count" -> edit(files, 0, "members.json", root -> find(root, "extra_languages").getAsJsonObject("grant").addProperty("count", 0));
                case "over-count" -> edit(files, 0, "members.json", root -> find(root, "extra_languages").getAsJsonObject("grant").addProperty("count", 17));
                case "nested-field" -> edit(files, 2, "changes.json", root -> row(root, 2).addProperty("field", "grant.count"));
                case "clear-grant" -> edit(files, 2, "changes.json", root -> { var change = row(root, 2); change.addProperty("op", "clear_field"); change.remove("value"); });
                default -> throw new AssertionError(mutation);
            }
            assertThrows(Schema2Exception.class, () -> KERNEL.composeFiles(files), mutation);
        }
    }

    @Test void oneCompleteReplacementMayChangeGrantBranchCategoryAndReferenceTogether() {
        var files = packages();
        var slot = find(json(files.get(0).get("members.json")), "instrument").getAsJsonObject("identity").deepCopy();
        edit(files, 2, "changes.json", root -> {
            var replacement = row(root, 2).deepCopy();
            replacement.add("target", slot);
            root.getAsJsonArray().add(replacement);
        });
        var result = KERNEL.composeFiles(files);
        var grant = (ProficiencyGrantEntry) result.entries().stream()
                .filter(row -> row.entry().identity().entryKey().equals("instrument")).findFirst().orElseThrow().entry();
        assertEquals(new ChoiceGrant(GrantCategory.LANGUAGE,
                new RuleIdentity(BASE, "catalog.language_set", "language_set.initial"), 1), grant.grant());
        assertEquals(new EntryIdentity(new RuleIdentity(BASE, "character.subrace", "subrace.north"),
                "proficiency_grants", BASE, "instrument"), grant.identity());
    }

    @Test void aPackageCannotDisableItsOwnNewSourceEvenWhenALaterPackageRepeatsTheChange() {
        var files = packages();
        edit(files, 1, "changes.json", root -> root.getAsJsonArray().add(
                disable(rule(EXT, "character.class", "class.retired"))));
        var failure = assertThrows(Schema2Exception.class, () -> KERNEL.composeFiles(files));
        assertEquals("S2_TARGET_NOT_PREVIOUS", failure.diagnostic().code());
        assertEquals("/8", failure.diagnostic().locations().getFirst().pointer());
        assertEquals(EXT, failure.diagnostic().locations().getFirst().moduleKey());
    }

    @Test void badReferencesAndIndirectOwnersAreRejectedEvenWhenLaterDisabledOrOverwritten() {
        for (String mutation : List.of("grant", "parent", "slot-owner", "missing-dependency", "owner")) {
            var files = packages();
            switch (mutation) {
                case "grant" -> edit(files, 0, "members.json", root -> find(root, "native_language").getAsJsonObject("grant")
                        .add("target", rule(BASE, "character.language", "language.missing")));
                case "parent" -> edit(files, 0, "rules.json", root -> find(root, "subrace.north").getAsJsonObject("parent_race").addProperty("rule_key", "race.missing"));
                case "slot-owner" -> edit(files, 1, "members.json", root -> find(root, "unused_choice").getAsJsonObject("identity").getAsJsonObject("rule").addProperty("rule_key", "class.missing"));
                case "owner" -> edit(files, 1, "members.json", root -> find(root, "extension_language").getAsJsonObject("identity").addProperty("module_key", BASE));
                case "missing-dependency" -> {
                    edit(files, 2, "author-package.json", root -> {
                        root.getAsJsonObject().getAsJsonArray("dependencies").remove(0);
                        root.getAsJsonObject().getAsJsonArray("compatible_combinations").get(0).getAsJsonObject().remove(BASE);
                    });
                }
                default -> throw new AssertionError(mutation);
            }
            var failure = assertThrows(Schema2Exception.class, () -> KERNEL.composeFiles(files), mutation);
            assertEquals(mutation.equals("owner") ? "S2_ORIGINAL_OWNER" : mutation.equals("missing-dependency")
                    ? "S2_DIRECT_DEPENDENCY" : "S2_REFERENCE_MISSING", failure.diagnostic().code());
            assertFalse(failure.diagnostic().locations().isEmpty());
        }
    }

    @Test void choicesCountDistinctEnabledTargetsAndRespectSourceSlotAndSetEnablement() {
        for (String mutation : List.of("distinct", "disabled-target", "disabled-set")) {
            var files = packages();
            if (mutation.equals("distinct")) edit(files, 2, "changes.json", root -> row(root, 2).getAsJsonObject("value").addProperty("count", 4));
            else if (mutation.equals("disabled-target")) edit(files, 2, "changes.json", root -> { row(root, 2).getAsJsonObject("value").addProperty("count", 3); root.getAsJsonArray().add(disable(rule(BASE, "character.language", "language.elvish"))); });
            else edit(files, 2, "changes.json", root -> root.getAsJsonArray().add(disable(rule(BASE, "catalog.language_set", "language_set.initial"))));
            assertEquals("S2_CANDIDATES_INSUFFICIENT", assertThrows(Schema2Exception.class, () -> KERNEL.composeFiles(files)).diagnostic().code());
        }
        for (boolean disableSource : List.of(false, true)) {
            var files = packages();
            edit(files, 2, "changes.json", root -> {
                row(root, 2).getAsJsonObject("value").addProperty("count", 16);
                var target = disableSource ? rule(BASE, "character.race", "race.human")
                        : find(json(files.get(0).get("members.json")), "extra_languages").getAsJsonObject("identity").deepCopy();
                root.getAsJsonArray().add(disable(target));
            });
            assertDoesNotThrow(() -> KERNEL.composeFiles(files));
        }
    }

    @Test void storedRowsRevalidateAtomicGrantsParentReferencesAndDeclarationPositions() {
        var original = ProductionSchema2Projection.compose(fixedArtifacts());
        for (String mutation : List.of("future-grant", "future-parent", "grant-location", "split-location", "invalid-count", "missing-chain")) {
            var chains = new ArrayList<>(original.projection().chains());
            int index = 0;
            Field field = mutation.equals("future-parent") ? Field.PARENT_RACE : Field.GRANT;
            while (chains.get(index).field() != field || field == Field.GRANT
                    && !((EntryIdentity) chains.get(index).target()).entryKey().equals("native_language")) index++;
            var chain = chains.get(index); var steps = new ArrayList<>(chain.steps()); var first = steps.getFirst();
            FieldState after = first.after(); Location location = first.authorLocation();
            if (mutation.equals("future-grant")) after = new FieldState(StateKind.PRESENT,
                    new GrantValue(new FixedGrant(GrantCategory.LANGUAGE, new RuleIdentity(EXT, "character.language", "language.common"))));
            else if (mutation.equals("future-parent")) after = new FieldState(StateKind.PRESENT,
                    new IdentityValue(new RuleIdentity(EXT, "character.race", "race.elf")));
            else if (mutation.equals("invalid-count")) after = new FieldState(StateKind.PRESENT,
                    new GrantValue(new ChoiceGrant(GrantCategory.LANGUAGE, new RuleIdentity(BASE, "catalog.language_set", "language_set.initial"), 17)));
            else if (mutation.equals("grant-location") || mutation.equals("split-location")) location = new Location(location.moduleKey(),
                    location.releaseVersion(), location.rawZipSha256(), location.path(), mutation.equals("grant-location") ? "/6/grant/target" : "/12/grant", null);
            if (mutation.equals("missing-chain")) chains.remove(index);
            else {
                steps.set(0, new ChainStep(first.op(), first.before(), after, first.provider(), first.loadIndex(), location, first.origin()));
                if (mutation.startsWith("future")) {
                    var next = steps.get(1); steps.set(1, new ChainStep(next.op(), after, next.after(), next.provider(), next.loadIndex(), next.authorLocation(), next.origin()));
                }
                chains.set(index, new FieldChain(chain.target(), chain.field(), steps));
            }
            assertThrows(Schema2Exception.class, () -> restore(original, original.projection().rules(), original.projection().entries(), chains), mutation);
        }
    }

    @Test void everyStoredSourceFieldAndOperationKeepsAnAtomicAuthorLocation() {
        var original = ProductionSchema2Projection.compose(fixedArtifacts());
        for (int index = 0; index < original.projection().chains().size(); index++) {
            var chain = original.projection().chains().get(index);
            RuleIdentity owner = chain.target() instanceof RuleIdentity rule ? rule : ((EntryIdentity) chain.target()).rule();
            if (!List.of("character.race", "character.subrace", "character.background", "character.class").contains(owner.ruleType())) continue;
            for (int stepIndex = 0; stepIndex < chain.steps().size(); stepIndex++) {
                var step = chain.steps().get(stepIndex);
                var location = step.authorLocation();
                for (String mutation : List.of("nested", "header", "over-budget")) {
                    String pointer = switch (mutation) {
                        case "nested" -> location.pointer() + "/unexpected";
                        case "over-budget" -> "/256" + (stepIndex == 0 && step.origin().equals("declared") ? "/" + chain.field().key() : "");
                        default -> location.pointer();
                    };
                    var changedLocation = new Location(location.moduleKey(), location.releaseVersion(), location.rawZipSha256(),
                            mutation.equals("header") ? HEADER_PATH : location.path(), pointer, null);
                    var steps = new ArrayList<>(chain.steps());
                    steps.set(stepIndex, new ChainStep(step.op(), step.before(), step.after(), step.provider(),
                            step.loadIndex(), changedLocation, step.origin()));
                    var chains = new ArrayList<>(original.projection().chains());
                    chains.set(index, new FieldChain(chain.target(), chain.field(), steps));
                    assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class, () ->
                            restore(original, original.projection().rules(), original.projection().entries(), chains),
                            chain.target() + " " + chain.field() + " " + mutation).diagnostic().code());
                }
            }
        }
    }

    private static JsonObject find(com.google.gson.JsonElement root, String key) {
        for (var item : root.getAsJsonArray()) {
            var row = item.getAsJsonObject(); var identity = row.getAsJsonObject("identity");
            if (identity.has("rule_key") && identity.get("rule_key").getAsString().equals(key)
                    || identity.has("entry_key") && identity.get("entry_key").getAsString().equals(key)) return row;
        }
        throw new AssertionError("Missing " + key);
    }
    private static JsonObject disable(JsonObject target) {
        var result = new JsonObject(); result.addProperty("op", "set_enabled"); result.add("target", target); result.addProperty("value", false); return result;
    }
    private static ProductionSchema2Projection restore(ProductionSchema2Projection original, List<FinalRule> rules,
                                                       List<FinalEntry> entries, List<FieldChain> chains) {
        return ProductionSchema2Projection.restore(original.members(), rules, entries, chains);
    }
}
