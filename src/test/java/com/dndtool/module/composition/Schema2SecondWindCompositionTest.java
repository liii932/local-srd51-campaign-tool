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

class Schema2SecondWindCompositionTest {
    private static final RuleIdentity FIGHTER = new RuleIdentity(BASE, "character.class", "class.fighter");
    private static final RuleIdentity SECOND_WIND = new RuleIdentity(BASE, "character.feature", "feature.second_wind");
    private static final RuleIdentity RESOURCE = fighterSecondWindResource(FIGHTER);

    @Test void exactResourceBindsAtCompositionAndDirectRestore() {
        var projection = compose(secondWindPackages());
        var stored = projection.projection();
        var reopened = ProductionSchema2Projection.restore(projection.members(), stored.rules(), stored.entries(), stored.chains());
        assertEquals(stored.rules(), reopened.projection().rules());
        assertTrue(stored.rules().stream().anyMatch(row -> row.enabled() && row.identity().equals(SECOND_WIND)));
    }

    @Test void aLaterOverrideCannotRepairTheOriginalResourceProfile() {
        var packages = secondWindPackages();
        edit(packages, 0, "rules.json", rows -> rows.getAsJsonArray().get(rows.getAsJsonArray().size() - 2)
                .getAsJsonObject().addProperty("maximum_profile", "1-20:2"));
        change(packages, RESOURCE, "maximum_profile", new JsonPrimitive("1-20:1"));
        var failure = assertThrows(Schema2Exception.class, () -> compose(packages));
        assertEquals("S2_TYPE", failure.diagnostic().code());
        assertEquals(Stage.AUTHOR, failure.diagnostic().stage());
    }

    @Test void theFinalResourceMustStillBeEnabledAndRecoverOnShortRest() {
        var disabled = secondWindPackages();
        change(disabled, RESOURCE, null, new JsonPrimitive(false));
        assertEquals("S2_REFERENCE_MISSING", assertThrows(Schema2Exception.class, () -> compose(disabled)).diagnostic().code());

        var longRestOnly = secondWindPackages();
        change(longRestOnly, RESOURCE, "recovery_profile", new JsonPrimitive("1-20:LONG_REST"));
        assertEquals("S2_TYPE", assertThrows(Schema2Exception.class, () -> compose(longRestOnly)).diagnostic().code());
    }

    @Test void onlyAnEnabledFirstLevelFighterCanHaveOneAutomaticSecondWind() {
        var invalidFields = Map.of("execution_mode", new JsonPrimitive("BLOCKED"), "feature_kind", new JsonPrimitive("OPTION"),
                "level", new JsonPrimitive(2), "source_page", new JsonPrimitive(25));
        for (var invalid : invalidFields.entrySet()) {
            var packages = secondWindPackages();
            edit(packages, 0, "rules.json", rows -> rows.getAsJsonArray().get(rows.getAsJsonArray().size() - 1)
                    .getAsJsonObject().add(invalid.getKey(), invalid.getValue()));
            assertEquals("S2_TYPE", assertThrows(Schema2Exception.class, () -> compose(packages), invalid.getKey()).diagnostic().code());
        }

        var disabledOwner = secondWindPackages();
        change(disabledOwner, FIGHTER, null, new JsonPrimitive(false));
        assertEquals("S2_REFERENCE_MISSING", assertThrows(Schema2Exception.class, () -> compose(disabledOwner)).diagnostic().code());

        var duplicate = secondWindPackages();
        RuleIdentity other = new RuleIdentity(EXT, "character.feature", "feature.second_wind_duplicate");
        edit(duplicate, 1, "rules.json", rows -> rows.getAsJsonArray().add(secondWindFeature(other)));
        assertEquals("S2_SECOND_WIND_AMBIGUOUS", assertThrows(Schema2Exception.class, () -> compose(duplicate)).diagnostic().code());
    }

    private static ProductionSchema2Projection compose(List<Map<String, byte[]>> packages) {
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages));
    }

    private static List<Map<String, byte[]>> secondWindPackages() {
        var packages = packages();
        edit(packages, 0, "rules.json", rows -> {
            var resource = definition(RESOURCE);
            resource.add("owner_class", jsonIdentity(FIGHTER));
            resource.addProperty("maximum_profile", "1-20:1");
            resource.addProperty("recovery_profile", "1-20:SHORT_REST");
            resource.addProperty("execution_mode", "AUTOMATIC");
            rows.getAsJsonArray().add(definition(FIGHTER));
            rows.getAsJsonArray().add(resource);
            rows.getAsJsonArray().add(secondWindFeature(SECOND_WIND));
        });
        return packages;
    }

    private static JsonObject secondWindFeature(RuleIdentity identity) {
        var feature = definition(identity);
        feature.add("owner", jsonIdentity(FIGHTER));
        feature.addProperty("level", 1);
        feature.addProperty("feature_kind", "FEATURE");
        feature.addProperty("execution_mode", "AUTOMATIC");
        feature.addProperty("execution_algorithm", FIGHTER_SECOND_WIND_ALGORITHM);
        return feature;
    }

    private static JsonObject definition(RuleIdentity identity) {
        var row = new JsonObject();
        row.add("identity", jsonIdentity(identity));
        row.addProperty("display_name", identity.ruleKey());
        row.addProperty("description", "Second Wind composition fixture.");
        row.addProperty("source_page", 24);
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
}
