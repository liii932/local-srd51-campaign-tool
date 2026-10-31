package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opts in to the new owner; the shared Wizard fixture retains BLOCKED metadata. */
public final class Schema2ArcaneRecoveryTestFixture {
    private Schema2ArcaneRecoveryTestFixture() { }
    public static List<Map<String, byte[]>> packages() {
        var files = Schema2WizardFixtures.packages();
        var rows = Schema2GrantFixtures.json(files.get(1).get("rules.json")).getAsJsonArray();
        int changed = 0;
        for (var row : rows) {
            var rule = row.getAsJsonObject();
            if (rule.has("execution_algorithm") && rule.get("execution_algorithm").getAsString().equals(Schema2ArcaneRecoveryRules.ALGORITHM)) {
                rule.addProperty("execution_mode", "AUTOMATIC"); changed++;
            }
        }
        assertEquals(1, changed);
        files.get(1).put("rules.json", rows.toString().getBytes(StandardCharsets.UTF_8));
        return files;
    }
    public static ProductionSchema2Projection material() {
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages()));
    }
    public static Command rest(String kind, RestInput input) { return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, input); }
    public static Command start(String kind) { return rest("REST_STARTED", new RestInput(kind, null, 0, 0, Map.of())); }
    public static Command time(String activity, int minutes) { return rest("TIME_ADVANCED", new RestInput(null, activity, minutes, 0, Map.of())); }
    public static Command finish(Map<Integer, Integer> slots) { return rest("REST_COMPLETED", new RestInput(null, null, 0, 0, Map.of(), slots)); }
    public static Command end() { return rest("REST_ENDED", new RestInput(null, null, 0, 0, Map.of())); }
    public static ResourceState slot(State state, int rank) {
        var identity = switch (rank) { case 1 -> SLOT1; case 2 -> SLOT2; case 3 -> SLOT3; default -> throw new AssertionError(rank); };
        return state.resources().stream().filter(r -> r.identity().equals(identity)).findFirst().orElseThrow();
    }
    public static State spend(ProductionSchema2Projection material, State state, int rank, int count) {
        return new Schema2CharacterProgressionRules().spend(material, state, slot(state, rank).identity(), count);
    }
    public static State apply(ProductionSchema2Projection material, State state, Command command) {
        return new Schema2CharacterRestRules().apply(material, state, command, null);
    }
    public static State shortRest(ProductionSchema2Projection material, State state, Map<Integer, Integer> selected) {
        state = apply(material, state, start("SHORT_REST"));
        state = apply(material, state, time("LIGHT", 60));
        return apply(material, state, finish(selected));
    }
}
