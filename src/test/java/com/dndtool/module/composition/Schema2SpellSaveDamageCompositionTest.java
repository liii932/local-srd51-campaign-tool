package com.dndtool.module.composition;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellSaveDamageCompositionTest {
    @Test void explicitAlgorithmRetainsExactSrdPageAndOldMetadataRemainsDistinct() {
        assertEquals(1,Schema2CompositionKernel.spellLevel("SRD_2014_BURNING_HANDS_V1"));assertEquals(123,Schema2CompositionKernel.spellSourcePage("SRD_2014_BURNING_HANDS_V1"));
        var material=Schema2SpellSaveDamageFixtures.projection();
        assertTrue(material.projection().rules().stream().anyMatch(r->r instanceof Schema2CompositionKernel.FinalSpell s&&s.definition().algorithm().equals("SRD_2014_BURNING_HANDS_V1")));
        assertTrue(Schema2WizardFixtures.projection().projection().rules().stream().anyMatch(r->r instanceof Schema2CompositionKernel.FinalSpell s&&s.definition().algorithm().equals("SRD_2014_BURNING_HANDS_METADATA_V1")));
        var files=Schema2SpellSaveDamageFixtures.packages();var rows=Schema2GrantFixtures.json(files.get(1).get("rules.json")).getAsJsonArray();
        for(var element:rows){var row=element.getAsJsonObject();if(row.has("algorithm")&&row.get("algorithm").getAsString().equals("SRD_2014_BURNING_HANDS_V1"))row.addProperty("source_page",124);}
        files.get(1).put("rules.json",rows.toString().getBytes(StandardCharsets.UTF_8));assertThrows(RuntimeException.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
    }
}
