package com.dndtool.module.composition;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit executable author package; the established metadata vector remains unchanged. */
public final class Schema2SpellSaveDamageFixtures {
    private Schema2SpellSaveDamageFixtures() { }
    public static List<Map<String,byte[]>> packages() {
        var files=Schema2WizardFixtures.packages();
        // The Wizard vector's final package only reasserts INT. Omit that optional adjustment
        // from this new combination so the two actual monster packages fit the four-package bound.
        files.removeLast();
        // Reuse the real bounded cleric producer for cross-owner concentration/Ready evidence.
        files.set(0,Schema2SpellFixtures.packages().getFirst());
        var extension=files.get(1);var rules=Schema2GrantFixtures.json(extension.get("rules.json")).getAsJsonArray();
        for(var element:rules) {var row=element.getAsJsonObject();
            if(row.has("algorithm")&&row.get("algorithm").getAsString().equals("SRD_2014_BURNING_HANDS_METADATA_V1"))row.addProperty("algorithm","SRD_2014_BURNING_HANDS_V1");
        }
        extension.put("rules.json",rules.toString().getBytes(StandardCharsets.UTF_8));
        return Schema2MonsterFixtures.appendTo(files,false);
    }
    public static List<ProductionRuleArtifact> artifacts() {return Schema2GrantFixtures.artifacts(packages());}
    public static ProductionSchema2Projection projection() {return ProductionSchema2Projection.compose(artifacts());}
}
