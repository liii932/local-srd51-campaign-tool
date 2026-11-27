package com.dndtool.module.composition;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CheckCompositionTest {
    private static final String ALTERNATE_ABILITY = "allow_alternate_skill_ability";
    private static final String GROUP_THRESHOLD = "group_success_threshold";

    @Test void fixedAuthorFilesProduceTypedCheckAndAllOriginalOverrideFacts() {
        var material = Schema2CheckFixtures.projection();
        var check = assertInstanceOf(FinalCheck.class, material.projection().rules().getFirst());
        assertEquals(Schema2CheckFixtures.CHECK, check.identity()); assertTrue(check.enabled());
        assertEquals("Reviewed ability check", check.definition().displayName());
        assertEquals("Server-owned ability check mechanism.", check.definition().description());
        assertEquals(77, check.definition().sourcePage());
        assertEquals("SRD_2014_ABILITY_CHECK_V1", check.definition().algorithm()); assertNull(check.definition().authorNote());
        assertTrue(check.definition().allowAlternateSkillAbility());
        assertEquals(12, check.definition().passiveBaseScore());
        assertEquals(3, check.definition().passiveAdvantageAdjustment());
        assertEquals(2, check.definition().groupSuccessNumerator());
        assertEquals(3, check.definition().groupSuccessDenominator());
        assertEquals(2, check.definition().contestMinimumWinningMargin());
        assertEquals(11, material.projection().chains().size());
        var groupThreshold = groupThresholdChain(material);
        assertEquals(List.of(new GroupSuccessThreshold(1, 2), new GroupSuccessThreshold(2, 3)),
                groupThreshold.steps().stream().map(step -> step.after().value()).toList());
        assertEquals(List.of("checks_base", "checks_extension"), groupThreshold.steps().stream()
                .map(step -> step.provider().moduleKey()).toList());
        assertIntegerChain(material, Field.PASSIVE_BASE_SCORE, 10, 12);
        assertIntegerChain(material, Field.PASSIVE_ADVANTAGE_ADJUSTMENT, 5, 3);
        assertIntegerChain(material, Field.CONTEST_MINIMUM_WINNING_MARGIN, 1, 2);
        var alternate = material.projection().chains().stream()
                .filter(c -> c.field() == Field.ALLOW_ALTERNATE_SKILL_ABILITY).findFirst().orElseThrow();
        assertEquals(List.of(false, true), alternate.steps().stream()
                .map(s -> ((BooleanValue) s.after().value()).value()).toList());
        assertEquals(List.of("checks_base", "checks_extension"), alternate.steps().stream()
                .map(s -> s.provider().moduleKey()).toList());
        var names = material.projection().chains().stream().filter(c -> c.field() == Field.DISPLAY_NAME).findFirst().orElseThrow();
        assertEquals(List.of("Ability check", "Reviewed ability check"), names.steps().stream().map(s -> ((TextValue) s.after().value()).value()).toList());
        assertEquals(List.of("checks_base", "checks_extension"), names.steps().stream().map(s -> s.provider().moduleKey()).toList());
        var enabled = material.projection().chains().stream().filter(c -> c.field() == Field.ENABLED).findFirst().orElseThrow();
        assertEquals(List.of(true, true), enabled.steps().stream().map(s -> ((BooleanValue) s.after().value()).value()).toList());
        ProductionSchema2Projection.verifyChains(Schema2CheckFixtures.artifacts(), material.chainsJson());
        var restored = ProductionSchema2Projection.restore(material.members(), material.projection().rules(), material.projection().entries(), material.projection().chains());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson()); assertArrayEquals(material.chainsJson(), restored.chainsJson());
    }
    @Test void originalAndOverrideAlgorithmsPagesKeysAndFieldsAreClosedBeforeComposition() {
        for (String value : List.of("CUSTOM_SCRIPT", "SRD_2014_REST_V1", "SRD_2014_ABILITY_CHECK_V1 ")) {
            var files = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(files, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).addProperty("algorithm", value));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(files));
            var changed = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(changed, 1, "changes.json", root -> Schema2GrantFixtures.row(root, 2).addProperty("value", value));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(changed));
        }
        for (int page : new int[]{0, 70, 76, 78, 87}) {
            var files = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(files, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).addProperty("source_page", page));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(files));
        }
        for (String key : List.of("rest.srd2014", "check.", "check..srd2014")) {
            var files = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(files, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).getAsJsonObject("identity").addProperty("rule_key", key));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(files));
        }
        var unknown = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(unknown, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).addProperty("script", "return 20"));
        assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(unknown));
    }
    @Test void alternateAbilityRequiresAnExplicitBooleanThatCannotBeCleared() {
        var missing = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(missing, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).remove(ALTERNATE_ABILITY));
        assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(missing));

        for (var invalid : List.of(new JsonPrimitive("true"), new JsonPrimitive(1), JsonNull.INSTANCE)) {
            var original = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(original, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).add(ALTERNATE_ABILITY, invalid));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(original));

            var override = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(override, 1, "changes.json", root -> {
                override(root, ALTERNATE_ABILITY).add("value", invalid);
            });
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(override));
        }

        var cleared = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(cleared, 1, "changes.json", root -> {
            var row = override(root, ALTERNATE_ABILITY);
            row.addProperty("op", "clear_field");
            row.remove("value");
        });
        assertEquals("S2_FIELD_NOT_CLEARABLE", assertThrows(Schema2Exception.class,
                () -> Schema2GrantFixtures.artifacts(cleared)).diagnostic().code());
    }

    @Test void aStoredCheckOptionMustAgreeWithItsCompleteBooleanChain() {
        var material = Schema2CheckFixtures.projection();
        var check = assertInstanceOf(FinalCheck.class, material.projection().rules().getFirst());
        var definition = check.definition();
        var forged = new FinalCheck(new CheckDefinition(definition.identity(), definition.displayName(),
                definition.description(), definition.sourcePage(), definition.algorithm(), false,
                definition.passiveBaseScore(), definition.passiveAdvantageAdjustment(),
                definition.groupSuccessThreshold(), definition.contestMinimumWinningMargin(), definition.authorNote()), check.enabled());
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), List.of(forged), material.projection().entries(), material.projection().chains()));

        var incomplete = material.projection().chains().stream()
                .filter(c -> c.field() != Field.ALLOW_ALTERNATE_SKILL_ABILITY).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), material.projection().rules(), material.projection().entries(), incomplete));
    }

    @Test void checkParametersRequireExactBoundedIntegersInBothDefinitionsAndOverrides() {
        var limits = Map.of(Field.PASSIVE_BASE_SCORE, List.of(1, 20),
                Field.PASSIVE_ADVANTAGE_ADJUSTMENT, List.of(0, 10),
                Field.CONTEST_MINIMUM_WINNING_MARGIN, List.of(1, 3));
        for (var entry : limits.entrySet()) {
            String field = entry.getKey().key();
            int min = entry.getValue().getFirst(), max = entry.getValue().getLast();
            for (int bound : entry.getValue()) {
                var files = Schema2CheckFixtures.packages();
                Schema2GrantFixtures.edit(files, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).addProperty(field, bound));
                Schema2GrantFixtures.edit(files, 1, "changes.json", root -> override(root, field).addProperty("value", bound));
                var material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
                assertIntegerChain(material, entry.getKey(), bound, bound);
            }

            var missing = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(missing, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).remove(field));
            assertEquals("S2_SHAPE", assertThrows(Schema2Exception.class,
                    () -> Schema2GrantFixtures.artifacts(missing)).diagnostic().code());

            for (var invalid : List.of(new JsonPrimitive(min - 1), new JsonPrimitive(max + 1),
                    new JsonPrimitive(Integer.toString(min)), new JsonPrimitive(true), new JsonPrimitive((double) min),
                    JsonParser.parseString(min + "e0"), JsonNull.INSTANCE)) {
                var original = Schema2CheckFixtures.packages();
                Schema2GrantFixtures.edit(original, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).add(field, invalid));
                assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(original));

                var changed = Schema2CheckFixtures.packages();
                Schema2GrantFixtures.edit(changed, 1, "changes.json", root -> override(root, field).add("value", invalid));
                assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(changed));
            }

            var cleared = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(cleared, 1, "changes.json", root -> {
                var row = override(root, field);
                row.addProperty("op", "clear_field");
                row.remove("value");
            });
            assertEquals("S2_FIELD_NOT_CLEARABLE", assertThrows(Schema2Exception.class,
                    () -> Schema2GrantFixtures.artifacts(cleared)).diagnostic().code());
        }
    }

    @Test void groupSuccessThresholdIsRequiredAndCanOnlyBeOverriddenAsOneBoundedFraction() {
        var missing = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(missing, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0).remove(GROUP_THRESHOLD));
        assertEquals("S2_SHAPE", assertThrows(Schema2Exception.class,
                () -> Schema2GrantFixtures.artifacts(missing)).diagnostic().code());

        for (String invalid : List.of("null", "[]", "{}", "{\"denominator\":2}",
                "{\"numerator\":1,\"denominator\":2,\"extra\":0}", "{\"numerator\":0,\"denominator\":2}",
                "{\"numerator\":1,\"denominator\":0}", "{\"numerator\":3,\"denominator\":2}",
                "{\"numerator\":1,\"denominator\":11}", "{\"numerator\":1.0,\"denominator\":2}",
                "{\"numerator\":1,\"denominator\":\"2\"}", "{\"numerator\":2,\"denominator\":4}",
                "{\"numerator\":10,\"denominator\":10}")) {
            var original = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(original, 0, "rules.json", root ->
                    Schema2GrantFixtures.row(root, 0).add(GROUP_THRESHOLD, JsonParser.parseString(invalid)));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(original), invalid);

            var changed = Schema2CheckFixtures.packages();
            Schema2GrantFixtures.edit(changed, 1, "changes.json", root ->
                    override(root, GROUP_THRESHOLD).add("value", JsonParser.parseString(invalid)));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(changed), invalid);
        }

        var cleared = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(cleared, 1, "changes.json", root -> {
            var row = override(root, GROUP_THRESHOLD);
            row.addProperty("op", "clear_field");
            row.remove("value");
        });
        assertEquals("S2_FIELD_NOT_CLEARABLE", assertThrows(Schema2Exception.class,
                () -> Schema2GrantFixtures.artifacts(cleared)).diagnostic().code());

        var partial = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(partial, 1, "changes.json", root -> {
            var row = override(root, GROUP_THRESHOLD);
            row.addProperty("field", GROUP_THRESHOLD + ".numerator");
            row.addProperty("value", 2);
        });
        assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(partial));

        var packages = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(packages, 0, "rules.json", root -> Schema2GrantFixtures.row(root, 0)
                .add(GROUP_THRESHOLD, JsonParser.parseString("{\"numerator\":1,\"denominator\":1}")));
        Schema2GrantFixtures.edit(packages, 1, "changes.json", root -> override(root, GROUP_THRESHOLD)
                .add("value", JsonParser.parseString("{\"numerator\":9,\"denominator\":10}")));
        var changed = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages));
        assertEquals(List.of(new GroupSuccessThreshold(1, 1), new GroupSuccessThreshold(9, 10)),
                groupThresholdChain(changed).steps().stream().map(step -> step.after().value()).toList());
        assertNotEquals(Schema2CheckFixtures.projection().finalRulesSha256(), changed.finalRulesSha256());
    }

    @Test void storedGroupThresholdMustMatchTheCompleteValidChain() {
        var material = Schema2CheckFixtures.projection();
        var check = assertInstanceOf(FinalCheck.class, material.projection().rules().getFirst());
        var definition = check.definition();
        var forged = new FinalCheck(new CheckDefinition(definition.identity(), definition.displayName(),
                definition.description(), definition.sourcePage(), definition.algorithm(),
                definition.allowAlternateSkillAbility(), definition.passiveBaseScore(),
                definition.passiveAdvantageAdjustment(), new GroupSuccessThreshold(1, 2),
                definition.contestMinimumWinningMargin(), definition.authorNote()), true);
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), List.of(forged), material.projection().entries(), material.projection().chains()));

        var threshold = groupThresholdChain(material);
        var original = threshold.steps().getFirst();
        var changed = threshold.steps().getLast();
        var invalid = new FieldState(StateKind.PRESENT, new GroupSuccessThreshold(3, 2));
        var altered = new FieldChain(threshold.target(), threshold.field(), List.of(
                new ChainStep(original.op(), original.before(), invalid, original.provider(),
                        original.loadIndex(), original.authorLocation(), original.origin()),
                new ChainStep(changed.op(), invalid, changed.after(), changed.provider(),
                        changed.loadIndex(), changed.authorLocation(), changed.origin())));
        var chains = material.projection().chains().stream().map(chain -> chain.equals(threshold) ? altered : chain).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), material.projection().rules(), material.projection().entries(), chains));
    }

    @Test void validFinalRatioCannotMaskUnreducedIntermediateOverride() {
        var packages = Schema2CheckFixtures.packages();
        packages.add(new LinkedHashMap<>(packages.getLast()));
        Schema2GrantFixtures.edit(packages, 2, HEADER_PATH, root -> root.getAsJsonObject()
                .addProperty("module_key", "checks_final"));
        Schema2GrantFixtures.edit(packages, 2, "changes.json", root -> {
            var change = override(root, GROUP_THRESHOLD).deepCopy();
            change.add("value", JsonParser.parseString("{\"numerator\":9,\"denominator\":10}"));
            root.getAsJsonArray().asList().clear();
            root.getAsJsonArray().add(change);
        });
        var material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages));
        var restored = ProductionSchema2Projection.restore(material.members(), material.projection().rules(),
                material.projection().entries(), material.projection().chains());
        assertEquals(material.chainSha256(), restored.chainSha256());

        var threshold = groupThresholdChain(material);
        assertEquals(3, threshold.steps().size());
        var original = threshold.steps().getFirst();
        var middle = threshold.steps().get(1);
        var last = threshold.steps().getLast();
        var unreduced = new FieldState(StateKind.PRESENT, new GroupSuccessThreshold(2, 4));
        var altered = new FieldChain(threshold.target(), threshold.field(), List.of(original,
                new ChainStep(middle.op(), middle.before(), unreduced, middle.provider(), middle.loadIndex(),
                        middle.authorLocation(), middle.origin()),
                new ChainStep(last.op(), unreduced, last.after(), last.provider(), last.loadIndex(),
                        last.authorLocation(), last.origin())));
        var chains = material.projection().chains().stream().map(chain -> chain.equals(threshold) ? altered : chain).toList();
        assertEquals("S2_TYPE", assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), material.projection().rules(), material.projection().entries(), chains)).diagnostic().code());
    }

    private static FieldChain groupThresholdChain(ProductionSchema2Projection material) {
        return material.projection().chains().stream()
                .filter(chain -> chain.field() == Field.GROUP_SUCCESS_THRESHOLD).findFirst().orElseThrow();
    }

    @Test void validFinalIntegerParametersCannotMaskAnOutOfRangeOriginalStoredValue() {
        var material = Schema2CheckFixtures.projection();
        var invalid = new FieldState(StateKind.PRESENT, new IntegerValue(0));
        for (var field : List.of(Field.PASSIVE_BASE_SCORE, Field.CONTEST_MINIMUM_WINNING_MARGIN)) {
            var chains = material.projection().chains().stream().map(chain -> {
                if (chain.field() != field) return chain;
                var original = chain.steps().getFirst();
                var changed = chain.steps().getLast();
                return new FieldChain(chain.target(), chain.field(), List.of(
                        new ChainStep(original.op(), original.before(), invalid, original.provider(),
                                original.loadIndex(), original.authorLocation(), original.origin()),
                        new ChainStep(changed.op(), invalid, changed.after(), changed.provider(),
                                changed.loadIndex(), changed.authorLocation(), changed.origin())));
            }).toList();
            assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                    material.members(), material.projection().rules(), material.projection().entries(), chains));
        }
    }

    @Test void storedContestMarginMustMatchItsCompleteChain() {
        var material = Schema2CheckFixtures.projection();
        var check = assertInstanceOf(FinalCheck.class, material.projection().rules().getFirst());
        var definition = check.definition();
        var forged = new FinalCheck(new CheckDefinition(definition.identity(), definition.displayName(),
                definition.description(), definition.sourcePage(), definition.algorithm(),
                definition.allowAlternateSkillAbility(), definition.passiveBaseScore(), definition.passiveAdvantageAdjustment(),
                definition.groupSuccessThreshold(), 3, definition.authorNote()), true);
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), List.of(forged), material.projection().entries(), material.projection().chains()));

        var incomplete = material.projection().chains().stream()
                .filter(chain -> chain.field() != Field.CONTEST_MINIMUM_WINNING_MARGIN).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), material.projection().rules(), material.projection().entries(), incomplete));
    }

    private static JsonObject override(JsonElement root, String field) {
        return root.getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> row.has("field") && row.get("field").getAsString().equals(field))
                .findFirst().orElseThrow();
    }

    private static void assertIntegerChain(ProductionSchema2Projection material, Field field, int original, int overridden) {
        var chain = material.projection().chains().stream().filter(c -> c.field() == field).findFirst().orElseThrow();
        assertEquals(List.of(original, overridden), chain.steps().stream().map(step -> ((IntegerValue) step.after().value()).value()).toList());
        assertEquals(List.of("checks_base", "checks_extension"), chain.steps().stream().map(step -> step.provider().moduleKey()).toList());
    }

    @Test void atMostOneEnabledRuleAndDirectOriginalOwnerDependencyRemainRequired() {
        var files = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(files, 0, "rules.json", root -> {
            var extra = Schema2GrantFixtures.row(root, 0).deepCopy(); extra.getAsJsonObject("identity").addProperty("rule_key", "check.second"); root.getAsJsonArray().add(extra);
        });
        assertEquals("S2_CHECK_AMBIGUOUS", assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files))).diagnostic().code());
        var undeclared = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(undeclared, 1, HEADER_PATH, root -> root.getAsJsonObject().add("dependencies", new com.google.gson.JsonArray()));
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(undeclared)));
    }
    @Test void validFinalAlgorithmCannotMaskUnknownOriginalStoredChainAtom() {
        var material = Schema2CheckFixtures.projection(); var chains = new ArrayList<>(material.projection().chains());
        for (int i = 0; i < chains.size(); i++) if (chains.get(i).field() == Field.ALGORITHM) {
            var chain = chains.get(i); var steps = new ArrayList<>(chain.steps()); var original = steps.getFirst(); var override = steps.getLast();
            var bad = new FieldState(StateKind.PRESENT, new TextValue("UNKNOWN_ORIGINAL_ALGORITHM"));
            steps.set(0, new ChainStep(original.op(), original.before(), bad, original.provider(), original.loadIndex(), original.authorLocation(), original.origin()));
            steps.set(steps.size() - 1, new ChainStep(override.op(), bad, override.after(), override.provider(), override.loadIndex(), override.authorLocation(), override.origin()));
            chains.set(i, new FieldChain(chain.target(), chain.field(), steps));
        }
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(material.members(), material.projection().rules(), material.projection().entries(), chains));
    }
}
