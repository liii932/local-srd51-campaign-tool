package com.dndtool.module.composition;

import java.io.IOException;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Independent fixed check author files; no business eligibility is implied. */
public final class Schema2CheckFixtures {
    public static final RuleIdentity CHECK = new RuleIdentity("checks_base", "adventure.check", "check.srd2014");
    private Schema2CheckFixtures() { }
    public static List<Map<String, byte[]>> packages() {
        List<Map<String, byte[]>> result = new ArrayList<>();
        for (String module : List.of("checks_base", "checks_extension")) {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put(HEADER_PATH, resource(module + "/" + HEADER_PATH));
            String path = module.equals("checks_base") ? "rules.json" : "changes.json";
            files.put(path, resource(module + "/" + path)); result.add(files);
        }
        return result;
    }
    public static List<ProductionRuleArtifact> artifacts() { return Schema2GrantFixtures.artifacts(packages()); }
    public static ProductionSchema2Projection projection() { return ProductionSchema2Projection.compose(artifacts()); }
    private static byte[] resource(String path) {
        try (var input = Schema2CheckFixtures.class.getResourceAsStream("/schema2-checks/" + path)) {
            if (input == null) throw new AssertionError(path);
            return input.readAllBytes();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
}
