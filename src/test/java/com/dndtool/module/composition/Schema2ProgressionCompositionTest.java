package com.dndtool.module.composition;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2GrantFixtures.edit;
import static com.dndtool.module.composition.Schema2GrantFixtures.rule;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ProgressionCompositionTest {
    @Test void independentExplicitModelCoversFinalBytesCompleteChainsAndBothProductionDigestKinds() throws Exception {
        assertIndependent(artifacts(), "");
        assertIndependent(fullArtifacts(), "full-");
    }
    private static void assertIndependent(List<ProductionRuleArtifact> artifacts, String prefix) throws Exception {
        var material = ProductionSchema2Projection.compose(artifacts);
        byte[] expectedRules = expected(prefix + "expected-final-rules.json");
        // These fixtures were enumerated independently from the finite contract and encoded with
        // Python stdlib JSON. They are not captures or outputs of the production implementation.
        assertArrayEquals(expectedRules, material.finalRulesJson());
        assertEquals(new String(expected(prefix + "expected-final-rules-sha256.txt"), StandardCharsets.US_ASCII).strip(),
                material.finalRulesSha256());
        String chains = new String(expected(prefix + "expected-chains-template.json"), StandardCharsets.UTF_8)
                .replace("${BASE_RAW}", sha256(artifacts.get(0).originalZip()))
                .replace("${EXT_RAW}", sha256(artifacts.get(1).originalZip()))
                .replace("${COMPOSITION}", material.compositionSha256());
        byte[] expectedChains = chains.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expectedChains, material.chainsJson());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("local-dnd/schema2-production/1/full-chain\0".getBytes(StandardCharsets.US_ASCII));
        assertEquals(HexFormat.of().formatHex(digest.digest(expectedChains)), material.chainSha256());
    }

    @Test void typedDefinitionsRetainCompleteOwnersAndEveryAtomicFieldChain() {
        var material = projection();
        var progression = (FinalClassProgression) material.projection().rules().stream()
                .filter(row -> row.identity().equals(PROGRESSION)).findFirst().orElseThrow();
        assertEquals(CLASS, progression.definition().ownerClass());
        assertEquals(10, progression.definition().hitDieSides());
        assertEquals(3, progression.definition().supportedMaximumLevel());
        assertEquals(ProgressionScope.STATISTICS_AND_RESOURCES_ONLY, progression.definition().advancementScope());
        var energy = (FinalResource) material.projection().rules().stream()
                .filter(row -> row.identity().equals(ENERGY)).findFirst().orElseThrow();
        assertEquals("1-2:3,3-20:4", energy.definition().maximumProfile());
        assertEquals("1-20:LONG_REST", energy.definition().recoveryProfile());
        assertEquals(2, material.projection().rules().stream().filter(row -> row.identity().ruleKey().equals("resource.energy")).count());
        var chain = material.projection().chains().stream().filter(row -> row.target().equals(ENERGY)
                && row.field() == Field.MAXIMUM_PROFILE).findFirst().orElseThrow();
        assertEquals(List.of("1-2:2,3-20:3", "1-2:3,3-20:4"), chain.steps().stream()
                .map(step -> ((TextValue) step.after().value()).value()).toList());
        assertEquals(List.of(BASE, EXT), chain.steps().stream().map(step -> step.provider().moduleKey()).toList());
        var restored = ProductionSchema2Projection.restore(material.members(), material.projection().rules(),
                material.projection().entries(), material.projection().chains());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
        assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().requireProductionQualification(material.projection()));
    }

    @Test void completeProfilesAndBoundedScopeRejectUnknownFutureBranchesBeforeComposition() {
        for (String mutation : List.of("future-maximum", "future-recovery", "proficiency", "die", "scope", "maximum-level",
                "missing-scope", "extra-features", "execution", "wrong-owner-type", "blocked-profile", "disabled-profile")) {
            var files = packages();
            edit(files, 0, "rules.json", root -> {
                JsonObject progression = root.getAsJsonArray().get(3).getAsJsonObject();
                JsonObject resource = root.getAsJsonArray().get(4).getAsJsonObject();
                switch (mutation) {
                    case "future-maximum" -> resource.addProperty("maximum_profile", "1-19:2,20:UNKNOWN");
                    case "future-recovery" -> resource.addProperty("recovery_profile", "1-19:SHORT_REST,20-20:UNKNOWN");
                    case "proficiency" -> progression.addProperty("proficiency_bonus_profile", "1-19:2,20:6");
                    case "die" -> progression.addProperty("hit_die_sides", 7);
                    case "scope" -> progression.addProperty("advancement_scope", "COMPLETE_CLASS_ADVANCEMENT");
                    case "maximum-level" -> progression.addProperty("supported_maximum_level", 21);
                    case "missing-scope" -> progression.remove("advancement_scope");
                    case "extra-features" -> progression.addProperty("features", "silently skip choices");
                    case "execution" -> resource.addProperty("execution_mode", "DM_OVERRIDE");
                    case "wrong-owner-type" -> resource.add("owner_class", rule(BASE, "character.race", "race.human"));
                    case "blocked-profile" -> root.getAsJsonArray().get(6).getAsJsonObject()
                            .addProperty("maximum_profile", "1-19:1,20:UNKNOWN");
                    case "disabled-profile" -> resource.addProperty("recovery_profile", "1-19:SHORT_REST,20-20:UNKNOWN");
                    default -> throw new AssertionError(mutation);
                }
            });
            if (mutation.equals("disabled-profile")) edit(files, 1, "changes.json", root -> {
                var disable = new JsonObject(); disable.addProperty("op", "set_enabled");
                disable.add("target", jsonIdentity(ENERGY)); disable.addProperty("value", false); root.getAsJsonArray().add(disable);
            });
            assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().composeFiles(files), mutation);
        }
    }

    @Test void missingOwnersAreRejectedEvenWhenTheirResourcesWillBeDisabled() {
        var files = packages();
        edit(files, 0, "rules.json", root -> root.getAsJsonArray().get(4).getAsJsonObject()
                .add("owner_class", rule(BASE, "character.class", "class.missing")));
        edit(files, 1, "changes.json", root -> {
            var change = new JsonObject(); change.addProperty("op", "set_enabled");
            change.add("target", jsonIdentity(ENERGY)); change.addProperty("value", false); root.getAsJsonArray().add(change);
        });
        assertEquals("S2_REFERENCE_MISSING", assertThrows(Schema2Exception.class,
                () -> new Schema2CompositionKernel().composeFiles(files)).diagnostic().code());
    }

    @Test void twoEnabledProgressionsCannotCompeteForOneClass() {
        var files = packages();
        edit(files, 0, "rules.json", root -> {
            var duplicate = root.getAsJsonArray().get(3).deepCopy().getAsJsonObject();
            duplicate.getAsJsonObject("identity").addProperty("rule_key", "progression.other");
            root.getAsJsonArray().add(duplicate);
        });
        assertEquals("S2_PROGRESSION_AMBIGUOUS", assertThrows(Schema2Exception.class,
                () -> new Schema2CompositionKernel().composeFiles(files)).diagnostic().code());
    }

    @Test void newDomainIdentitiesRequireTheirExactPrefixesAndNonemptySegments() {
        for (String type : List.of("character.resource", "character.class_progression")) {
            for (String key : List.of("bare", "resource.", "progression.", "resource..energy", "progression..guard",
                    "resource.energy.", "progression.guard.", "resource.9energy", "progression.9guard")) {
                var files = packages();
                edit(files, 0, "rules.json", root -> root.getAsJsonArray().get(type.equals("character.resource") ? 4 : 3)
                        .getAsJsonObject().getAsJsonObject("identity").addProperty("rule_key", key));
                assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().composeFiles(files), type + " " + key);
            }
        }
    }

    @Test void storedGrowthRowsCannotDropAFieldOrInventNestedProvenance() {
        var material = projection();
        int index = 0;
        while (!material.projection().chains().get(index).target().equals(PROGRESSION)
                || material.projection().chains().get(index).field() != Field.ADVANCEMENT_SCOPE) index++;
        var chains = new ArrayList<>(material.projection().chains());
        chains.remove(index);
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(material.members(),
                material.projection().rules(), material.projection().entries(), chains));
        chains.clear(); chains.addAll(material.projection().chains());
        var original = chains.get(index); var step = original.steps().getFirst(); var location = step.authorLocation();
        var forged = new Location(location.moduleKey(), location.releaseVersion(), location.rawZipSha256(),
                location.path(), location.pointer() + "/future", null);
        chains.set(index, new FieldChain(original.target(), original.field(), List.of(new ChainStep(step.op(),
                step.before(), step.after(), step.provider(), step.loadIndex(), forged, step.origin()))));
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(material.members(),
                material.projection().rules(), material.projection().entries(), chains));
    }

    @Test void fullGrowthHasExplicitTypedPrerequisitesLevelsGrantsAndRestWithoutInstallationEligibility() {
        var material = fullProjection();
        var progression = (FinalClassProgression) material.projection().rules().stream()
                .filter(row -> row.identity().equals(PROGRESSION)).findFirst().orElseThrow();
        var expected = new AbilityPrerequisite(List.of(new RequirementGroup(List.of(
                new AbilityMinimum("ability.strength", 13), new AbilityMinimum("ability.dexterity", 13)))));
        assertEquals(expected, progression.definition().multiclassPrerequisite());
        var chain = material.projection().chains().stream().filter(row -> row.target().equals(PROGRESSION)
                && row.field() == Field.MULTICLASS_PREREQUISITE).findFirst().orElseThrow();
        assertEquals(new PrerequisiteValue(expected), chain.steps().getFirst().after().value());
        assertEquals("/3/multiclass_prerequisite", chain.steps().getFirst().authorLocation().pointer());
        assertEquals(10, material.projection().entries().stream().filter(row -> row.entry() instanceof ClassLevelEntry).count());
        assertEquals(1, material.projection().rules().stream().filter(FinalRest.class::isInstance).count());
        var selected = material.projection().entries().stream().filter(row -> row.entry().identity().equals(INITIAL_SKILL_SLOT)).findFirst().orElseThrow();
        assertEquals(new ChoiceGrowthGrant(SKILL_SET, 1), ((GrowthProficiencyGrantEntry) selected.entry()).grant());
        var restored = ProductionSchema2Projection.restore(material.members(), material.projection().rules(), material.projection().entries(), material.projection().chains());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson()); assertArrayEquals(material.chainsJson(), restored.chainsJson());
        assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().requireProductionQualification(material.projection()));
    }

    @Test void malformedFullGrowthAtomsCoverageAndModeCombinationsFailClosed() {
        for (String mutation : List.of("missing-prerequisite", "empty-group", "unknown-ability", "duplicate-ability", "minimum",
                "missing-level", "disabled-level", "duplicate-level", "wrong-member-category", "missing-skill-ability", "rest-algorithm")) {
            var files = fullPackages();
            if (mutation.equals("missing-level")) edit(files, 0, "growth.json", root -> root.getAsJsonArray().remove(0));
            else if (mutation.equals("disabled-level")) edit(files, 1, "changes.json", root -> {
                var change = new JsonObject(); change.addProperty("op", "set_enabled");
                change.add("target", com.dndtool.module.composition.Schema2GrantFixtures.json(files.get(0).get("growth.json")).getAsJsonArray().get(0).getAsJsonObject().get("identity"));
                change.addProperty("value", false); root.getAsJsonArray().add(change);
            });
            else if (mutation.equals("duplicate-level")) edit(files, 0, "growth.json", root -> root.getAsJsonArray().get(1).getAsJsonObject().addProperty("level", 1));
            else edit(files, 0, "rules.json", root -> {
                var progression = root.getAsJsonArray().get(3).getAsJsonObject();
                var predicate = progression.getAsJsonObject("multiclass_prerequisite");
                var group = predicate.getAsJsonArray("all_of").get(0).getAsJsonObject().getAsJsonArray("any_of");
                switch (mutation) {
                    case "missing-prerequisite" -> progression.remove("multiclass_prerequisite");
                    case "empty-group" -> { while (!group.isEmpty()) group.remove(0); }
                    case "unknown-ability" -> group.get(0).getAsJsonObject().addProperty("ability", "ability.luck");
                    case "duplicate-ability" -> group.get(1).getAsJsonObject().addProperty("ability", "ability.strength");
                    case "minimum" -> group.get(0).getAsJsonObject().addProperty("minimum", 21);
                    case "wrong-member-category" -> find(root, "proficiency.skill.athletics").addProperty("category", "WEAPON");
                    case "missing-skill-ability" -> find(root, "proficiency.skill.athletics").remove("ability_key");
                    case "rest-algorithm" -> find(root, "rest.srd2014").addProperty("algorithm", "SKIP_TIME_AND_EFFECTS");
                    default -> throw new AssertionError(mutation);
                }
            });
            assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().composeFiles(files), mutation);
        }
    }

    @Test void fullGrowthDeclarationRequiresItsOwnPrerequisiteBeforeLaterPackagesAreRead() {
        var files = fullPackages();
        edit(files, 0, "rules.json", root -> find(root, "progression.guard").remove("multiclass_prerequisite"));
        edit(files, 1, "changes.json", root -> root.getAsJsonArray().add(atomicChange(PROGRESSION,
                "multiclass_prerequisite", com.google.gson.JsonParser.parseString("{\"all_of\":[]}"))));
        assertAll(
                () -> assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().read(files.getFirst())),
                () -> assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().composeFiles(files)));
    }

    @ParameterizedTest
    @CsvSource({
            "character.feature, feature.training, execution_algorithm, UNKNOWN_ALGORITHM, AUTOMATIC_RESOURCE_LIFECYCLE_V1",
            "character.feat, feat.grappler, execution_algorithm, UNKNOWN_ALGORITHM, BLOCKED_DOWNSTREAM_SYSTEM_V1",
            "character.feat, feat.grappler, execution_algorithm, BLOCKED_SPELL_SYSTEM_V1, BLOCKED_DOWNSTREAM_SYSTEM_V1",
            "character.feat, feat.grappler, execution_mode, AUTOMATIC, BLOCKED",
            "character.feat, feat.grappler, execution_mode, DM_ADJUDICATION, BLOCKED",
            "character.proficiency, proficiency.skill.athletics, category, TOOL, SKILL"
    })
    void unsupportedTargetLocalAtomCannotBeHiddenByALaterOverride(String type, String key, String field,
                                                                 String invalid, String replacement) {
        var files = fullPackages(); var target = new RuleIdentity(BASE, type, key);
        edit(files, 1, "changes.json", root -> root.getAsJsonArray()
                .add(atomicChange(target, field, new com.google.gson.JsonPrimitive(invalid))));
        files.add(repairPackage(files.get(1), atomicChange(target, field, new com.google.gson.JsonPrimitive(replacement))));
        assertAll(
                () -> assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().read(files.get(1))),
                () -> assertThrows(Schema2Exception.class, () -> new Schema2CompositionKernel().composeFiles(files)));
    }

    @Test void supportedModeAndAlgorithmMayChangeTogetherInOnePackage() {
        var files = fullPackages(); var target = new RuleIdentity(BASE, "character.feature", "feature.training");
        edit(files, 1, "changes.json", root -> {
            root.getAsJsonArray().add(atomicChange(target, "execution_mode", new com.google.gson.JsonPrimitive("DM_ADJUDICATION")));
            root.getAsJsonArray().add(atomicChange(target, "execution_algorithm", new com.google.gson.JsonPrimitive("BOUNDED_DM_ADJUDICATION_V1")));
        });
        var material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
        var feature = (FinalFeature) material.projection().rules().stream().filter(row -> row.identity().equals(target)).findFirst().orElseThrow();
        assertEquals(FeatureExecution.DM_ADJUDICATION, feature.definition().executionMode());
        assertEquals("BOUNDED_DM_ADJUDICATION_V1", feature.definition().executionAlgorithm());
        assertDoesNotThrow(() -> ProductionSchema2Projection.restore(material.members(), material.projection().rules(),
                material.projection().entries(), material.projection().chains()));
    }

    private static JsonObject atomicChange(RuleIdentity target, String field, com.google.gson.JsonElement value) {
        var row = new JsonObject(); row.addProperty("op", "set_field"); row.add("target", jsonIdentity(target));
        row.addProperty("field", field); row.add("value", value); return row;
    }

    private static Map<String, byte[]> repairPackage(Map<String, byte[]> extension, JsonObject change) {
        var header = Schema2GrantFixtures.json(extension.get(HEADER_PATH)).getAsJsonObject();
        header.addProperty("module_key", "growth_repair"); header.addProperty("package_display_name", "Later valid override");
        var dependency = new JsonObject(); dependency.addProperty("module_key", EXT);
        dependency.add("supported_versions", com.google.gson.JsonParser.parseString("[\"1\"]"));
        header.getAsJsonArray("dependencies").add(dependency);
        header.getAsJsonArray("compatible_combinations").get(0).getAsJsonObject().addProperty(EXT, "1");
        header.add("files", com.google.gson.JsonParser.parseString("[{\"kind\":\"changes\",\"path\":\"changes.json\"}]"));
        var changes = new com.google.gson.JsonArray(); changes.add(change);
        return new LinkedHashMap<>(Map.of(HEADER_PATH, header.toString().getBytes(StandardCharsets.UTF_8),
                "changes.json", changes.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonObject find(com.google.gson.JsonElement root, String key) {
        for (var row : root.getAsJsonArray()) if (row.getAsJsonObject().getAsJsonObject("identity").get("rule_key").getAsString().equals(key)) return row.getAsJsonObject();
        throw new AssertionError("Missing " + key);
    }
    @Test void unsupportedInitialAsiAndConflictingSubclassSelectionTimesRejectTheirDeclarations() {
        var initialAsi = fullPackages();
        edit(initialAsi, 0, "growth.json", root -> root.getAsJsonArray().get(0).getAsJsonObject().addProperty("asi_available", true));
        assertEquals("S2_GROWTH_INITIAL_ASI_UNSUPPORTED", assertThrows(Schema2Exception.class,
                () -> new Schema2CompositionKernel().composeFiles(initialAsi)).diagnostic().code());
        var mixedTimes = fullPackages();
        edit(mixedTimes, 1, "rules.json", root -> {
            var subclass = find(com.dndtool.module.composition.Schema2GrantFixtures.json(mixedTimes.get(0).get("rules.json")), "subclass.guard").deepCopy();
            subclass.add("identity", rule(EXT, "character.subclass", "subclass.late"));
            subclass.addProperty("selection_level", 4); root.getAsJsonArray().add(subclass);
        });
        assertEquals("S2_SUBCLASS_LEVEL_MISMATCH", assertThrows(Schema2Exception.class,
                () -> new Schema2CompositionKernel().composeFiles(mixedTimes)).diagnostic().code());
    }
    private static byte[] expected(String name) {
        try (var input = Schema2ProgressionCompositionTest.class.getResourceAsStream("/schema2-progression/" + name)) {
            if (input == null) throw new AssertionError("Missing independent expected " + name);
            return input.readAllBytes();
        } catch (IOException error) { throw new AssertionError(error); }
    }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
