package com.dndtool.module.composition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2GrantFixtures.edit;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ImprovedCriticalCompositionTest {
    private static final RuleIdentity FIGHTER = new RuleIdentity(BASE, "character.class", "class.fighter");
    private static final RuleIdentity CHAMPION = new RuleIdentity(BASE, "character.subclass", "subclass.champion");
    private static final RuleIdentity IMPROVED_CRITICAL = new RuleIdentity(BASE, "character.feature", "feature.improved_critical");

    @Test void originalDeclarationRequiresItsExactSourceAndAcquisitionShape() {
        Map<String, JsonElement> mutations = Map.of(
                "owner", jsonIdentity(FIGHTER), "source_page", new JsonPrimitive(24),
                "level", new JsonPrimitive(4), "feature_kind", new JsonPrimitive("OPTION"),
                "execution_mode", new JsonPrimitive("BLOCKED"));
        for (var mutation : mutations.entrySet()) {
            var packages = championPackages();
            edit(packages, 0, "rules.json", rows -> rows.getAsJsonArray().get(rows.getAsJsonArray().size() - 1)
                    .getAsJsonObject().add(mutation.getKey(), mutation.getValue()));
            assertEquals("S2_TYPE", assertThrows(Schema2Exception.class,
                    () -> Schema2GrantFixtures.artifacts(packages), mutation.getKey()).diagnostic().code());
        }
    }

    @Test void finalOverridesCannotChangeTheRequiredShapeOrMoveChampionToAnotherClass() {
        var wrongLevel = championPackages();
        change(wrongLevel, IMPROVED_CRITICAL, "level", new JsonPrimitive(4));
        assertRejected(wrongLevel, "S2_TYPE");

        var wrongSelection = championPackages();
        change(wrongSelection, CHAMPION, "selection_level", new JsonPrimitive(2));
        assertRejected(wrongSelection, "S2_TYPE");

        var wrongClass = championPackages();
        change(wrongClass, CHAMPION, "owner_class", jsonIdentity(CLASS));
        assertRejected(wrongClass, "S2_TYPE");
    }

    @Test void laterOverridesCannotRepairAnInvalidOriginalChampionRelationship() {
        Map<String, JsonElement> invalid = Map.of("owner_class", jsonIdentity(CLASS), "selection_level", new JsonPrimitive(2));
        Map<String, JsonElement> correct = Map.of("owner_class", jsonIdentity(FIGHTER), "selection_level", new JsonPrimitive(3));
        for (var mutation : invalid.entrySet()) {
            var packages = championPackages();
            edit(packages, 0, "rules.json", rows -> {
                var champion = rows.getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(row -> jsonIdentity(CHAMPION).equals(row.get("identity"))).findFirst().orElseThrow();
                champion.add(mutation.getKey(), mutation.getValue());
            });
            change(packages, CHAMPION, mutation.getKey(), correct.get(mutation.getKey()));
            var artifacts = Schema2GrantFixtures.artifacts(packages);
            var error = assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.compose(artifacts));
            assertEquals("S2_TYPE", error.diagnostic().code());
            assertEquals(Stage.AUTHOR, error.diagnostic().stage());
        }
    }

    @Test void enabledCapabilityNeedsBothOwnersAndCannotBeDuplicated() {
        for (RuleIdentity owner : List.of(FIGHTER, CHAMPION)) {
            var packages = championPackages();
            change(packages, owner, null, new JsonPrimitive(false));
            assertRejected(packages, "S2_REFERENCE_MISSING");
        }
        var duplicate = championPackages();
        RuleIdentity second = new RuleIdentity(EXT, "character.feature", "feature.second_improved_critical");
        edit(duplicate, 1, "rules.json", rows -> rows.getAsJsonArray().add(improvedCritical(second)));
        assertRejected(duplicate, "S2_IMPROVED_CRITICAL_AMBIGUOUS");

        change(duplicate, IMPROVED_CRITICAL, null, new JsonPrimitive(false));
        var material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(duplicate));
        assertEquals(List.of(second), material.projection().rules().stream()
                .filter(FinalFeature.class::isInstance).filter(FinalRule::enabled).map(FinalRule::identity).toList());
    }

    private static List<Map<String, byte[]>> championPackages() {
        var packages = packages();
        edit(packages, 0, "rules.json", rows -> {
            var champion = definition(CHAMPION);
            champion.add("owner_class", jsonIdentity(FIGHTER));
            champion.addProperty("selection_level", 3);
            rows.getAsJsonArray().add(definition(FIGHTER));
            rows.getAsJsonArray().add(champion);
            rows.getAsJsonArray().add(improvedCritical(IMPROVED_CRITICAL));
        });
        return packages;
    }

    private static JsonObject improvedCritical(RuleIdentity identity) {
        var feature = definition(identity);
        feature.add("owner", jsonIdentity(CHAMPION));
        feature.addProperty("level", 3);
        feature.addProperty("feature_kind", "FEATURE");
        feature.addProperty("execution_mode", "AUTOMATIC");
        feature.addProperty("execution_algorithm", CHAMPION_IMPROVED_CRITICAL_ALGORITHM);
        return feature;
    }

    private static JsonObject definition(RuleIdentity identity) {
        var row = new JsonObject();
        row.add("identity", jsonIdentity(identity));
        row.addProperty("display_name", identity.ruleKey());
        row.addProperty("description", "Finite Champion composition fixture.");
        row.addProperty("source_page", 25);
        return row;
    }

    private static void change(List<Map<String, byte[]>> packages, RuleIdentity target, String field, JsonElement value) {
        edit(packages, 1, "changes.json", rows -> {
            var row = new JsonObject();
            row.addProperty("op", field == null ? "set_enabled" : "set_field");
            row.add("target", jsonIdentity(target));
            if (field != null) row.addProperty("field", field);
            row.add("value", value);
            rows.getAsJsonArray().add(row);
        });
    }

    private static void assertRejected(List<Map<String, byte[]>> packages, String code) {
        var artifacts = Schema2GrantFixtures.artifacts(packages);
        assertEquals(code, assertThrows(Schema2Exception.class,
                () -> ProductionSchema2Projection.compose(artifacts)).diagnostic().code());
    }
}
