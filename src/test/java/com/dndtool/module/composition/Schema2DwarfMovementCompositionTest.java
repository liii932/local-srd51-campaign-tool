package com.dndtool.module.composition;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2GrantFixtures.edit;
import static com.dndtool.module.composition.Schema2GrantFixtures.rule;
import static org.junit.jupiter.api.Assertions.*;

class Schema2DwarfMovementCompositionTest {
    private static final String BASE = "combat_base";
    private static final String EXTENSION = "combat_extension";
    private static final RuleIdentity DWARF = new RuleIdentity(BASE, "character.race", "race.dwarf");
    private static final RuleIdentity MOVEMENT = new RuleIdentity(BASE, "character.movement", "movement.dwarf");
    private static final RuleIdentity ARMOR_MOVEMENT = new RuleIdentity(BASE, "equipment.armor_movement", "armor_movement.heavy_armor");

    @Test void dwarfSpeedPolicyAndSharedPenaltyCanBeOverriddenAndFrozenIndependently() {
        var packages = dwarfPackages();
        change(packages, MOVEMENT, "walk_speed_feet", new JsonPrimitive(40));
        change(packages, MOVEMENT, "heavy_armor_speed_policy", new JsonPrimitive("IGNORE"));
        change(packages, ARMOR_MOVEMENT, "heavy_armor_speed_penalty_feet", new JsonPrimitive(15));
        var material = compose(packages);
        var projection = material.projection();
        var movement = (FinalMovement) projection.rules().stream().filter(row -> row.identity().equals(MOVEMENT))
                .findFirst().orElseThrow();
        assertTrue(movement.enabled());
        assertEquals(DWARF, movement.definition().owner());
        assertEquals(WALKING_SPEED_ALGORITHM, movement.definition().algorithm());
        assertEquals(40, movement.definition().walkSpeedFeet());
        assertEquals(HeavyArmorSpeedPolicy.IGNORE, movement.definition().heavyArmorSpeedPolicy());
        assertTrue(projection.rules().stream().anyMatch(row -> row instanceof FinalMovement normal && normal.enabled()
                && normal.definition().heavyArmorSpeedPolicy() == HeavyArmorSpeedPolicy.APPLY
                && normal.definition().walkSpeedFeet() == 35));
        assertEquals(15, penalty(material));
        for (Field field : List.of(Field.WALK_SPEED_FEET, Field.HEAVY_ARMOR_SPEED_POLICY, Field.HEAVY_ARMOR_SPEED_PENALTY_FEET)) {
            RuleIdentity target = field == Field.HEAVY_ARMOR_SPEED_PENALTY_FEET ? ARMOR_MOVEMENT : MOVEMENT;
            var chain = projection.chains().stream().filter(row -> row.target().equals(target) && row.field() == field)
                    .findFirst().orElseThrow();
            assertEquals(EXTENSION, chain.steps().getLast().provider().moduleKey());
        }
        assertRoundTrip(material);
    }

    @Test void ignorePolicyAcceptsAnExactSubraceOwnerWithItsOwnSourcePage() {
        var packages = dwarfPackages();
        RuleIdentity subrace = new RuleIdentity(BASE, "character.subrace", "subrace.custom");
        edit(packages, 0, "rules.json", rows -> {
            var row = find(rows, DWARF.ruleKey()).deepCopy();
            row.add("identity", rule(BASE, subrace.ruleType(), subrace.ruleKey()));
            row.add("parent_race", rule(BASE, DWARF.ruleType(), DWARF.ruleKey()));
            rows.getAsJsonArray().add(row);
            var movement = find(rows, MOVEMENT.ruleKey());
            movement.add("owner", row.get("identity").deepCopy());
            movement.addProperty("heavy_armor_speed_policy", "IGNORE");
            movement.addProperty("source_page", 7);
        });
        var material = compose(packages);
        var row = (FinalMovement) material.projection().rules().stream().filter(rule -> rule.identity().equals(MOVEMENT))
                .findFirst().orElseThrow();
        assertEquals(subrace, row.definition().owner());
        assertEquals(HeavyArmorSpeedPolicy.IGNORE, row.definition().heavyArmorSpeedPolicy());
        assertRoundTrip(material);
    }

    @Test void missingMalformedAndRetiredMovementParametersAreRejectedOnAuthorInput() {
        assertBadAtom(MOVEMENT, "heavy_armor_speed_policy", new JsonPrimitive("EXEMPT"));
        assertBadAtom(MOVEMENT, "heavy_armor_speed_policy", new JsonPrimitive(false));
        assertBadAtom(MOVEMENT, "heavy_armor_speed_policy", JsonNull.INSTANCE);
        assertBadAtom(MOVEMENT, "algorithm", new JsonPrimitive("SRD_2014_DWARF_WALKING_SPEED_V1"));
        assertBadAtom(ARMOR_MOVEMENT, "source_page", new JsonPrimitive(64));
        var wrongSharedIdentity = dwarfPackages();
        edit(wrongSharedIdentity, 0, "rules.json", rows -> find(rows, ARMOR_MOVEMENT.ruleKey()).add("identity",
                rule(BASE, ARMOR_MOVEMENT.ruleType(), "armor_movement.other")));
        assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(wrongSharedIdentity));
        for (int value : List.of(-5, 1, 125)) {
            assertBadAtom(ARMOR_MOVEMENT, "heavy_armor_speed_penalty_feet", new JsonPrimitive(value));
        }
        for (var field : List.of(Map.entry(MOVEMENT, "heavy_armor_speed_policy"),
                Map.entry(ARMOR_MOVEMENT, "heavy_armor_speed_penalty_feet"))) {
            var packages = dwarfPackages();
            edit(packages, 0, "rules.json", rows -> find(rows, field.getKey().ruleKey()).remove(field.getValue()));
            assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(packages));
        }
    }

    @Test void enabledMovementNeedsItsExactDeclaredAvailableOwnerAndRemainsUnique() {
        var missing = dwarfPackages();
        change(missing, MOVEMENT, "owner", rule(EXTENSION, "character.race", "race.dwarf"));
        assertComposeRejected(missing, "S2_REFERENCE_MISSING");

        var undeclaredDependency = dwarfPackages();
        edit(undeclaredDependency, 0, "rules.json", rows -> find(rows, MOVEMENT.ruleKey())
                .add("owner", rule(EXTENSION, "character.race", "race.dwarf")));
        assertComposeRejected(undeclaredDependency, "S2_DIRECT_DEPENDENCY");

        var disabled = dwarfPackages();
        change(disabled, DWARF, null, new JsonPrimitive(false));
        assertComposeRejected(disabled, "S2_REFERENCE_MISSING");

        var duplicate = dwarfPackages();
        edit(duplicate, 0, "rules.json", rows -> {
            var second = find(rows, MOVEMENT.ruleKey()).deepCopy();
            second.add("identity", rule(BASE, "character.movement", "movement.dwarf_second"));
            rows.getAsJsonArray().add(second);
        });
        assertComposeRejected(duplicate, "S2_MOVEMENT_AMBIGUOUS");
    }

    @Test void sharedPenaltyAcceptsBothBoundariesAndRejectsEnabledDuplicates() {
        for (int value : List.of(0, 120)) {
            var packages = dwarfPackages();
            change(packages, ARMOR_MOVEMENT, "heavy_armor_speed_penalty_feet", new JsonPrimitive(value));
            var material = compose(packages);
            assertEquals(value, penalty(material));
            assertRoundTrip(material);
        }

        var duplicate = dwarfPackages();
        var other = find(Schema2GrantFixtures.json(duplicate.getFirst().get("rules.json")), ARMOR_MOVEMENT.ruleKey()).deepCopy();
        other.add("identity", rule(EXTENSION, ARMOR_MOVEMENT.ruleType(), ARMOR_MOVEMENT.ruleKey()));
        edit(duplicate, 1, HEADER_PATH, header -> {
            var file = new JsonObject();
            file.addProperty("kind", "definitions");
            file.addProperty("path", "rules.json");
            header.getAsJsonObject().getAsJsonArray("files").add(file);
        });
        duplicate.get(1).put("rules.json", ("[" + other + "]").getBytes(StandardCharsets.UTF_8));
        assertComposeRejected(duplicate, "S2_ARMOR_MOVEMENT_AMBIGUOUS");
    }

    @Test void partialProjectionMayDeferSharedPenaltyToApplicationQualification() {
        var missing = dwarfPackages();
        edit(missing, 0, "rules.json", rows -> rows.getAsJsonArray().remove(find(rows, ARMOR_MOVEMENT.ruleKey())));
        var missingMaterial = compose(missing);
        assertTrue(missingMaterial.projection().rules().stream().noneMatch(FinalArmorMovement.class::isInstance));
        assertRoundTrip(missingMaterial);

        var disabled = dwarfPackages();
        change(disabled, ARMOR_MOVEMENT, null, new JsonPrimitive(false));
        var disabledMaterial = compose(disabled);
        assertTrue(disabledMaterial.projection().rules().stream()
                .filter(FinalArmorMovement.class::isInstance).noneMatch(FinalRule::enabled));
        assertRoundTrip(disabledMaterial);
    }

    @Test void freezeRejectsInvalidRowsIncompleteChainsAndMalformedOriginalSteps() {
        var packages = dwarfPackages();
        change(packages, ARMOR_MOVEMENT, "heavy_armor_speed_penalty_feet", new JsonPrimitive(15));
        var material = compose(packages);
        var projection = material.projection();
        List<FinalRule> invalidRules = projection.rules().stream().map(row -> {
            if (!(row instanceof FinalArmorMovement armor)) return row;
            var def = armor.definition();
            return new FinalArmorMovement(new ArmorMovementDefinition(def.identity(), def.displayName(), def.description(),
                    def.sourcePage(), -5, def.algorithm(), def.authorNote()), true);
        }).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), invalidRules, projection.entries(), projection.chains()));

        List<FinalRule> missingPolicyRules = projection.rules().stream().map(row -> {
            if (!(row instanceof FinalMovement movement)) return row;
            var def = movement.definition();
            return new FinalMovement(new MovementDefinition(def.identity(), def.displayName(), def.description(),
                    def.sourcePage(), def.owner(), def.walkSpeedFeet(), null, def.algorithm(), def.authorNote()), true);
        }).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), missingPolicyRules, projection.entries(), projection.chains()));

        var incompleteChains = projection.chains().stream().filter(chain ->
                !chain.target().equals(ARMOR_MOVEMENT) || chain.field() != Field.HEAVY_ARMOR_SPEED_PENALTY_FEET).toList();
        assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), projection.rules(), projection.entries(), incompleteChains)).diagnostic().code());

        var invalidChains = projection.chains().stream().map(chain -> {
            if (!chain.target().equals(ARMOR_MOVEMENT) || chain.field() != Field.HEAVY_ARMOR_SPEED_PENALTY_FEET) return chain;
            var initial = chain.steps().getFirst();
            var override = chain.steps().getLast();
            var bad = new FieldState(StateKind.PRESENT, new IntegerValue(-5));
            return new FieldChain(chain.target(), chain.field(), List.of(
                    new ChainStep(initial.op(), initial.before(), bad, initial.provider(), initial.loadIndex(),
                            initial.authorLocation(), initial.origin()),
                    new ChainStep(override.op(), bad, override.after(), override.provider(), override.loadIndex(),
                            override.authorLocation(), override.origin())));
        }).toList();
        assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(
                material.members(), projection.rules(), projection.entries(), invalidChains));
    }

    private static void assertBadAtom(RuleIdentity target, String field, JsonElement value) {
        var authored = dwarfPackages();
        edit(authored, 0, "rules.json", rows -> find(rows, target.ruleKey()).add(field, value));
        assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(authored), field + "/" + value);
        var overridden = dwarfPackages();
        change(overridden, target, field, value);
        assertThrows(Schema2Exception.class, () -> Schema2GrantFixtures.artifacts(overridden), field + "/" + value);
    }

    private static List<Map<String, byte[]>> dwarfPackages() {
        var files = Schema2CombatFixtures.packages();
        edit(files, 0, "rules.json", rows -> {
            var race = find(rows, "race.human").deepCopy();
            race.add("identity", rule(BASE, DWARF.ruleType(), DWARF.ruleKey()));
            race.addProperty("display_name", "Dwarf");
            race.addProperty("source_page", 3);
            rows.getAsJsonArray().add(race);
            var movement = find(rows, "movement.human").deepCopy();
            movement.add("identity", rule(BASE, MOVEMENT.ruleType(), MOVEMENT.ruleKey()));
            movement.add("owner", race.get("identity").deepCopy());
            movement.addProperty("source_page", 3);
            movement.addProperty("walk_speed_feet", 25);
            rows.getAsJsonArray().add(movement);
        });
        return files;
    }

    private static JsonObject find(JsonElement rows, String key) {
        for (var element : rows.getAsJsonArray()) {
            JsonObject row = element.getAsJsonObject();
            if (row.getAsJsonObject("identity").get("rule_key").getAsString().equals(key)) return row;
        }
        throw new AssertionError(key);
    }

    private static void change(List<Map<String, byte[]>> packages, RuleIdentity target, String field, JsonElement value) {
        edit(packages, 1, "changes.json", rows -> {
            var operation = new JsonObject();
            operation.addProperty("op", field == null ? "set_enabled" : "set_field");
            operation.add("target", rule(target.moduleKey(), target.ruleType(), target.ruleKey()));
            if (field != null) operation.addProperty("field", field);
            operation.add("value", value);
            rows.getAsJsonArray().add(operation);
        });
    }

    private static int penalty(ProductionSchema2Projection material) {
        return material.projection().rules().stream().filter(FinalRule::enabled)
                .filter(FinalArmorMovement.class::isInstance).map(FinalArmorMovement.class::cast)
                .findFirst().orElseThrow().definition().heavyArmorSpeedPenaltyFeet();
    }

    private static void assertRoundTrip(ProductionSchema2Projection material) {
        var projection = material.projection();
        var restored = ProductionSchema2Projection.restore(material.members(), projection.rules(), projection.entries(), projection.chains());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
    }

    private static ProductionSchema2Projection compose(List<Map<String, byte[]>> packages) {
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages));
    }

    private static void assertComposeRejected(List<Map<String, byte[]>> packages, String code) {
        var artifacts = Schema2GrantFixtures.artifacts(packages);
        assertEquals(code, assertThrows(Schema2Exception.class,
                () -> ProductionSchema2Projection.compose(artifacts)).diagnostic().code());
    }
}
