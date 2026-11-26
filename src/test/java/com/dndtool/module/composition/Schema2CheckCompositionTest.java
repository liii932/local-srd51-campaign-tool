package com.dndtool.module.composition;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CheckCompositionTest {
    private static final String ALTERNATE_ABILITY = "allow_alternate_skill_ability";

    @Test void fixedAuthorFilesProduceTypedCheckAndAllOriginalOverrideFacts() {
        var material = Schema2CheckFixtures.projection();
        var check = assertInstanceOf(FinalCheck.class, material.projection().rules().getFirst());
        assertEquals(Schema2CheckFixtures.CHECK, check.identity()); assertTrue(check.enabled());
        assertEquals("Reviewed ability check", check.definition().displayName());
        assertEquals("Server-owned ability check mechanism.", check.definition().description());
        assertEquals(77, check.definition().sourcePage());
        assertEquals("SRD_2014_ABILITY_CHECK_V1", check.definition().algorithm()); assertNull(check.definition().authorNote());
        assertTrue(check.definition().allowAlternateSkillAbility());
        assertEquals(7, material.projection().chains().size());
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
                var row = root.getAsJsonArray().asList().getLast().getAsJsonObject();
                assertEquals(ALTERNATE_ABILITY, row.get("field").getAsString());
                row.add("value", invalid);
            });
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(override));
        }

        var cleared = Schema2CheckFixtures.packages();
        Schema2GrantFixtures.edit(cleared, 1, "changes.json", root -> {
            var row = root.getAsJsonArray().asList().getLast().getAsJsonObject();
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
                definition.description(), definition.sourcePage(), definition.algorithm(), false, definition.authorNote()), check.enabled());
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), List.of(forged), material.projection().entries(), material.projection().chains()));

        var incomplete = material.projection().chains().stream()
                .filter(c -> c.field() != Field.ALLOW_ALTERNATE_SKILL_ABILITY).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), material.projection().rules(), material.projection().entries(), incomplete));
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
