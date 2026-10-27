package com.dndtool.module.composition;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2WizardCompositionTest {
    @Test void literalWizardEnvironmentAndLearningMemberAreIndependentCanonicalVectors() {
        var material=projection();JsonObject encoded=JsonParser.parseString(new String(material.finalRulesJson(),StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject expected=JsonParser.parseString("""
                {"identity":{"kind":"rule","module_key":"growth_extension","rule_type":"character.wizard_spellcasting","rule_key":"spellcasting.wizard"},
                 "display_name":"spellcasting.wizard","description":"Bounded Wizard source and closed spell metadata.","source_page":53,
                 "owner_class":{"kind":"rule","module_key":"growth_extension","rule_type":"character.class","rule_key":"class.wizard"},
                 "required_feature":{"kind":"rule","module_key":"growth_extension","rule_type":"character.feature","rule_key":"feature.wizard_spellcasting"},
                 "slot_resource":{"kind":"rule","module_key":"growth_extension","rule_type":"character.resource","rule_key":"resource.wizard.slot1"},
                 "rank2_slot_resource":{"kind":"rule","module_key":"growth_extension","rule_type":"character.resource","rule_key":"resource.wizard.slot2"},
                 "rank3_slot_resource":{"kind":"rule","module_key":"growth_extension","rule_type":"character.resource","rule_key":"resource.wizard.slot3"},
                 "ability_key":"ability.intelligence","algorithm":"SRD_2014_WIZARD_PREPARED_V1","enabled":true}
                """).getAsJsonObject();
        assertEquals(expected,find(encoded.getAsJsonArray("rules"),"spellcasting.wizard"));
        assertEquals(JsonParser.parseString("""
                {"identity":{"kind":"rule","module_key":"growth_extension","rule_type":"adventure.environment","rule_key":"environment.antimagic_zone"},
                 "display_name":"environment.antimagic_zone","description":"Bounded Wizard source and closed spell metadata.","source_page":0,"algorithm":"DM_ANTIMAGIC_ZONE_V1","enabled":true}
                """),find(encoded.getAsJsonArray("rules"),"environment.antimagic_zone"));
        var members=material.projection().entries().stream().filter(e->e.entry() instanceof SpellbookEntry).toList();assertEquals(19,members.size());
        assertEquals(1,spellLevel("SRD_2014_SHIELD_V1"));assertEquals(3,spellLevel("SRD_2014_COUNTERSPELL_V1"));assertFalse(wizardSpell("SRD_2014_BLESS_V1"));
        assertEquals(179,spellSourcePage("SRD_2014_SHIELD_V1"));assertEquals(131,spellSourcePage("SRD_2014_COUNTERSPELL_V1"));
        assertEquals(114,spellSourcePage("SRD_2014_ALARM_METADATA_V1"));assertEquals(1,spellLevel("SRD_2014_ALARM_METADATA_V1"));
        var member=members.stream().filter(e->e.entry().target().equals(SHIELD)).findFirst().orElseThrow();
        assertEquals(new EntryIdentity(CASTING,"spells","growth_extension","shield"),member.entry().identity());
        JsonObject expectedMember=JsonParser.parseString("""
                {"identity":{"kind":"entry","rule":{"kind":"rule","module_key":"growth_extension","rule_type":"character.wizard_spellcasting","rule_key":"spellcasting.wizard"},
                 "collection_key":"spells","module_key":"growth_extension","entry_key":"shield"},
                 "spell":{"kind":"rule","module_key":"growth_extension","rule_type":"adventure.spell","rule_key":"spell.shield"},"enabled":true}
                """).getAsJsonObject();
        List<JsonObject> serializedMembers=new ArrayList<>();for(var e:encoded.getAsJsonArray("entries"))if(e.getAsJsonObject().get("identity").equals(expectedMember.get("identity")))serializedMembers.add(e.getAsJsonObject());
        assertEquals(List.of(expectedMember),serializedMembers);
        var chain=material.projection().chains().stream().filter(c->c.target().equals(CASTING)&&c.field()==Field.ABILITY_KEY).findFirst().orElseThrow();
        assertEquals(2,chain.steps().size());assertEquals(chain.steps().getFirst().after(),chain.steps().getLast().after());assertEquals("wizard_adjustment",chain.steps().getLast().provider().moduleKey());
        var restored=ProductionSchema2Projection.restore(material.members(),material.projection().rules(),material.projection().entries(),material.projection().chains());
        assertArrayEquals(material.finalRulesJson(),restored.finalRulesJson());assertArrayEquals(material.chainsJson(),restored.chainsJson());
    }
    @Test void wizardCannotBorrowAFeatureSlotPoolOrNonWizardSpell() {
        for(var value:List.of(Map.entry(SLOT1,"1-20:2"),Map.entry(SLOT2,"3-20:3"),Map.entry(SLOT3,"1-20:2"))) {
            var files=packages();edit(files,value.getKey(),r->r.addProperty("maximum_profile",value.getValue()));reject(files);
        }
        var wrong=packages();edit(wrong,SPELLCASTING,r->r.addProperty("execution_algorithm","LIFE_DOMAIN_LEVEL1_V1"));reject(wrong);
        var nonWizard=packages();edit(nonWizard,spell("alarm"),r->{r.addProperty("algorithm","SRD_2014_BLESS_V1");r.addProperty("source_page",122);});reject(nonWizard);
        var masked=packages();edit(masked,spell("alarm"),r->{r.addProperty("algorithm","SRD_2014_BLESS_V1");r.addProperty("source_page",122);});
        Schema2GrantFixtures.edit(masked,2,"changes.json",root->{for(var pair:List.of(Map.entry("algorithm",new JsonPrimitive("SRD_2014_ALARM_METADATA_V1")),Map.entry("source_page",new JsonPrimitive(114)))){var change=new JsonObject();change.addProperty("op","set_field");change.add("target",Schema2ProgressionFixtures.jsonIdentity(spell("alarm")));change.addProperty("field",pair.getKey());change.add("value",pair.getValue());root.getAsJsonArray().add(change);}});
        reject(masked);
        var duplicate=packages();edit(duplicate,CASTING,r->r.add("rank2_slot_resource",Schema2ProgressionFixtures.jsonIdentity(SLOT1)));reject(duplicate);
        var insufficient=packages();Schema2GrantFixtures.edit(insufficient,1,"growth.json",root->{var rows=root.getAsJsonArray();for(int i=rows.size()-1;i>=0;i--)if(rows.get(i).getAsJsonObject().getAsJsonObject("identity").get("collection_key").getAsString().equals("spells")&&!rows.get(i).getAsJsonObject().getAsJsonObject("identity").get("entry_key").getAsString().equals("shield"))rows.remove(i);});reject(insufficient);
    }
    @Test void unknownAlgorithmsFakeAntimagicSpellsAndMetadataSourcePagesFailClosed() {
        var unknown=packages();edit(unknown,SHIELD,r->r.addProperty("algorithm","FREE_SCRIPT"));reject(unknown);
        var page=packages();edit(page,COUNTERSPELL,r->r.addProperty("source_page",179));reject(page);
        var env=packages();edit(env,ENVIRONMENT,r->r.addProperty("algorithm","SRD_2014_ANTIMAGIC_FIELD_V1"));reject(env);
        var origin=packages();edit(origin,ENVIRONMENT,r->r.addProperty("source_page",114));reject(origin);
        assertThrows(IllegalArgumentException.class,()->spellLevel("UNKNOWN"));
    }
    private static JsonObject find(JsonArray rows,String key){for(var e:rows){var r=e.getAsJsonObject();if(key.equals(r.getAsJsonObject("identity").get("rule_key").getAsString()))return r;}throw new AssertionError(key);}
    private static void edit(List<Map<String,byte[]>> files,RuleIdentity id,java.util.function.Consumer<JsonObject> mutation){Schema2GrantFixtures.edit(files,1,"rules.json",root->mutation.accept(find(root.getAsJsonArray(),id.ruleKey())));}
    private static void reject(List<Map<String,byte[]>> files){assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));}
}
