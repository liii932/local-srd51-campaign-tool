package com.dndtool.module.composition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2GrantFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SubraceHitPointsCompositionTest {
    private static final RuleIdentity NORTH = new RuleIdentity(BASE, "character.subrace", "subrace.north");
    private static final RuleIdentity SAME_KEY_OTHER_MODULE = new RuleIdentity(EXT, "character.subrace", "subrace.north");
    private static final RuleIdentity PROFILE = new RuleIdentity(EXT, "character.subrace_hit_points", "subrace_hit_points.north");
    private static final RuleIdentity SECOND = new RuleIdentity(EXT, "character.subrace_hit_points", "subrace_hit_points.second");

    @Test void bonusFieldsAcceptIndependentBoundsAndRetainTheExactOwnerAndOverrideChain() {
        assertTrue(profiles(compose(packages())).isEmpty());
        var files = withProfile();
        change(files, PROFILE, "first_level_bonus", new JsonPrimitive(5));
        change(files, PROFILE, "per_additional_level_bonus", new JsonPrimitive(0));
        var material = compose(files);
        var finalRow = profiles(material).getFirst();
        assertTrue(finalRow.enabled());
        var definition = finalRow.definition();
        assertEquals(NORTH, definition.owner());
        assertEquals(4, definition.sourcePage());
        assertEquals(SUBRACE_HIT_POINTS_ALGORITHM, definition.algorithm());
        assertEquals(5, definition.firstLevelBonus());
        assertEquals(0, definition.perAdditionalLevelBonus());

        for (var field : Map.of(Field.FIRST_LEVEL_BONUS, 5, Field.PER_ADDITIONAL_LEVEL_BONUS, 0).entrySet()) {
            var chain = material.projection().chains().stream()
                    .filter(value -> value.target().equals(PROFILE) && value.field() == field.getKey()).findFirst().orElseThrow();
            assertEquals(new IntegerValue(1), chain.steps().getFirst().after().value());
            assertEquals(new IntegerValue(field.getValue()), chain.steps().getLast().after().value());
            assertEquals(COMPAT, chain.steps().getLast().provider().moduleKey());
        }
        var projection = material.projection();
        var restored = ProductionSchema2Projection.restore(material.members(), projection.rules(), projection.entries(), projection.chains());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
    }

    @Test void invalidValuesAndMissingRequiredFieldsFailInAuthorsAndOverrides() {
        for (String field : List.of("first_level_bonus", "per_additional_level_bonus")) {
            for (JsonElement bad : List.of(new JsonPrimitive(-1), new JsonPrimitive(6), new JsonPrimitive(1.5), new JsonPrimitive("1"))) {
                assertBadAtom(field, bad);
            }
            var missing = withProfile();
            edit(missing, 1, "rules.json", rows -> rows.getAsJsonArray().asList().getLast().getAsJsonObject().remove(field));
            assertThrows(Schema2Exception.class, () -> artifacts(missing), field);
            var cleared = withProfile();
            edit(cleared, 2, "changes.json", rows -> {
                var operation = operation("clear_field", PROFILE);
                operation.addProperty("field", field);
                rows.getAsJsonArray().add(operation);
            });
            assertThrows(Schema2Exception.class, () -> artifacts(cleared), field);
        }
        assertBadAtom("owner", rule(BASE, "character.race", "race.human"));
        assertBadAtom("source_page", new JsonPrimitive(3));
        assertBadAtom("algorithm", new JsonPrimitive("UNKNOWN"));
        var wrongPrefix = withProfile();
        edit(wrongPrefix, 1, "rules.json", rows -> rows.getAsJsonArray().asList().getLast().getAsJsonObject()
                .add("identity", rule(EXT, PROFILE.ruleType(), "movement.north")));
        assertThrows(Schema2Exception.class, () -> artifacts(wrongPrefix));
    }

    @Test void exactOwnerMustBeAvailableAndDirectlyDeclaredAsADependency() {
        var missing = withProfile();
        change(missing, PROFILE, "owner", identity(SAME_KEY_OTHER_MODULE));
        assertRejected(missing, "S2_REFERENCE_MISSING");

        var forward = packages();
        addOtherSubrace(forward);
        var earlyProfile = new RuleIdentity(BASE, PROFILE.ruleType(), PROFILE.ruleKey());
        edit(forward, 0, "rules.json", rows -> rows.getAsJsonArray().add(profile(earlyProfile, SAME_KEY_OTHER_MODULE)));
        assertRejected(forward, "S2_DIRECT_DEPENDENCY");

        var disabled = withProfile();
        change(disabled, NORTH, null, new JsonPrimitive(false));
        assertRejected(disabled, "S2_REFERENCE_MISSING");

        change(disabled, PROFILE, null, new JsonPrimitive(false));
        assertFalse(profiles(compose(disabled)).getFirst().enabled());
    }

    @Test void uniquenessUsesTheEntireSubraceIdentityAndOnlyEnabledProfiles() {
        var duplicate = withProfile();
        edit(duplicate, 1, "rules.json", rows -> rows.getAsJsonArray().add(profile(SECOND, NORTH)));
        assertRejected(duplicate, "S2_SUBRACE_HIT_POINTS_AMBIGUOUS");
        change(duplicate, SECOND, null, new JsonPrimitive(false));
        assertEquals(List.of(PROFILE), profiles(compose(duplicate)).stream().filter(FinalRule::enabled).map(FinalRule::identity).toList());

        var exact = withProfile();
        addOtherSubrace(exact);
        edit(exact, 1, "rules.json", rows -> rows.getAsJsonArray().add(profile(SECOND, SAME_KEY_OTHER_MODULE)));
        var owners = profiles(compose(exact)).stream().map(row -> row.definition().owner()).toList();
        assertTrue(owners.containsAll(List.of(NORTH, SAME_KEY_OTHER_MODULE)));
        assertEquals(2, owners.size());
    }

    @Test void frozenRestoreRejectsMalformedRowsMissingChainsAndAnOriginalReferenceToALaterDeclaration() {
        var packages = packages();
        var earlyProfile = new RuleIdentity(BASE, PROFILE.ruleType(), PROFILE.ruleKey());
        edit(packages, 0, "rules.json", rows -> rows.getAsJsonArray().add(profile(earlyProfile, NORTH)));
        addOtherSubrace(packages);
        change(packages, earlyProfile, "owner", identity(SAME_KEY_OTHER_MODULE));
        var material = compose(packages);
        var projection = material.projection();

        List<FinalRule> invalidRules = projection.rules().stream().map(value -> {
            if (!(value instanceof FinalSubraceHitPoints hitPoints)) return value;
            var definition = hitPoints.definition();
            return new FinalSubraceHitPoints(new SubraceHitPointsDefinition(definition.identity(), definition.displayName(),
                    definition.description(), definition.sourcePage(), definition.owner(), 6, definition.perAdditionalLevelBonus(),
                    definition.algorithm(), definition.authorNote()), hitPoints.enabled());
        }).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(material.members(),
                invalidRules, projection.entries(), projection.chains()));

        var incomplete = projection.chains().stream().filter(value ->
                !value.target().equals(earlyProfile) || value.field() != Field.FIRST_LEVEL_BONUS).toList();
        assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), projection.rules(), projection.entries(), incomplete)).diagnostic().code());

        var invalidOrigin = projection.chains().stream().map(chain -> {
            if (!chain.target().equals(earlyProfile) || chain.field() != Field.OWNER) return chain;
            var original = chain.steps().getFirst();
            var override = chain.steps().getLast();
            return new FieldChain(chain.target(), chain.field(), List.of(
                    new ChainStep(original.op(), original.before(), override.after(), original.provider(), original.loadIndex(),
                            original.authorLocation(), original.origin()),
                    new ChainStep(override.op(), override.after(), override.after(), override.provider(), override.loadIndex(),
                            override.authorLocation(), override.origin())));
        }).toList();
        assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), projection.rules(), projection.entries(), invalidOrigin)).diagnostic().code());
    }

    private static void assertBadAtom(String field, JsonElement input) {
        var authored = withProfile();
        edit(authored, 1, "rules.json", rows -> rows.getAsJsonArray().asList().getLast().getAsJsonObject().add(field, input));
        assertThrows(Schema2Exception.class, () -> artifacts(authored), field + "/" + input);
        var overridden = withProfile();
        change(overridden, PROFILE, field, input);
        assertThrows(Schema2Exception.class, () -> artifacts(overridden), field + "/" + input);
    }

    private static List<Map<String, byte[]>> withProfile() {
        var files = packages();
        edit(files, 1, "rules.json", rows -> rows.getAsJsonArray().add(profile(PROFILE, NORTH)));
        return files;
    }

    private static void addOtherSubrace(List<Map<String, byte[]>> files) {
        edit(files, 1, "rules.json", rows -> {
            var source = new JsonObject();
            source.add("identity", identity(SAME_KEY_OTHER_MODULE));
            source.addProperty("display_name", "A different subrace with the same key");
            source.addProperty("description", "An independent source identity.");
            source.addProperty("source_page", 4);
            source.add("parent_race", rule(BASE, "character.race", "race.human"));
            rows.getAsJsonArray().add(source);
        });
    }

    private static JsonObject profile(RuleIdentity id, RuleIdentity owner) {
        var row = new JsonObject();
        row.add("identity", identity(id));
        row.addProperty("display_name", "Subrace hit points");
        row.addProperty("description", "A bounded hit point contribution.");
        row.addProperty("source_page", 4);
        row.add("owner", identity(owner));
        row.addProperty("first_level_bonus", 1);
        row.addProperty("per_additional_level_bonus", 1);
        row.addProperty("algorithm", SUBRACE_HIT_POINTS_ALGORITHM);
        return row;
    }

    private static void change(List<Map<String, byte[]>> files, RuleIdentity target, String field, JsonElement value) {
        edit(files, 2, "changes.json", rows -> {
            var operation = operation(field == null ? "set_enabled" : "set_field", target);
            if (field != null) operation.addProperty("field", field);
            operation.add("value", value);
            rows.getAsJsonArray().add(operation);
        });
    }

    private static JsonObject operation(String op, RuleIdentity target) {
        var row = new JsonObject();
        row.addProperty("op", op);
        row.add("target", identity(target));
        return row;
    }

    private static JsonObject identity(RuleIdentity identity) {
        return rule(identity.moduleKey(), identity.ruleType(), identity.ruleKey());
    }

    private static ProductionSchema2Projection compose(List<Map<String, byte[]>> files) {
        return ProductionSchema2Projection.compose(artifacts(files));
    }

    private static List<FinalSubraceHitPoints> profiles(ProductionSchema2Projection material) {
        return material.projection().rules().stream().filter(FinalSubraceHitPoints.class::isInstance)
                .map(FinalSubraceHitPoints.class::cast).toList();
    }

    private static void assertRejected(List<Map<String, byte[]>> files, String code) {
        var artifacts = artifacts(files);
        assertEquals(code, assertThrows(Schema2Exception.class,
                () -> ProductionSchema2Projection.compose(artifacts)).diagnostic().code());
    }
}
