package com.dndtool.module.composition;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellCompositionTest {
    @Test void productionSpellJsonAndReferenceChainsMatchIndependentLiteralContract() throws Exception {
        var files=packages();var artifacts=Schema2GrantFixtures.artifacts(files);
        var material=ProductionSchema2Projection.compose(artifacts);
        // Written from the authored spell contract: no production encoder constructs this oracle.
        JsonArray expected=JsonParser.parseString("""
                [
                  {
                    "identity":{"kind":"rule","module_key":"growth_base","rule_type":"adventure.spell","rule_key":"spell.bless"},
                    "display_name":"spell.bless","description":"Finite Life domain level-one author source.",
                    "source_page":122,"algorithm":"SRD_2014_BLESS_V1",
                    "author_note":"Explicit supported spell note.","enabled":true
                  },
                  {
                    "identity":{"kind":"rule","module_key":"growth_base","rule_type":"adventure.spell","rule_key":"spell.cure_wounds"},
                    "display_name":"spell.cure_wounds","description":"Finite Life domain level-one author source.",
                    "source_page":132,"algorithm":"SRD_2014_CURE_WOUNDS_V1","enabled":true
                  },
                  {
                    "identity":{"kind":"rule","module_key":"growth_base","rule_type":"character.spellcasting","rule_key":"spellcasting.life_domain"},
                    "display_name":"spellcasting.life_domain","description":"Finite Life domain level-one author source.",
                    "source_page":17,"algorithm":"LIFE_DOMAIN_LEVEL1_V1","ability_key":"ability.wisdom",
                    "owner_class":{"kind":"rule","module_key":"growth_base","rule_type":"character.class","rule_key":"class.guard"},
                    "required_subclass":{"kind":"rule","module_key":"growth_base","rule_type":"character.subclass","rule_key":"subclass.guard"},
                    "required_feature":{"kind":"rule","module_key":"growth_base","rule_type":"character.feature","rule_key":"feature.life_domain_spellcasting"},
                    "slot_resource":{"kind":"rule","module_key":"growth_base","rule_type":"character.resource","rule_key":"resource.spell_slot.rank1"},
                    "bless":{"kind":"rule","module_key":"growth_base","rule_type":"adventure.spell","rule_key":"spell.bless"},
                    "cure_wounds":{"kind":"rule","module_key":"growth_base","rule_type":"adventure.spell","rule_key":"spell.cure_wounds"},
                    "material_item":{"kind":"rule","module_key":"growth_base","rule_type":"equipment.item","rule_key":"item.holy_symbol"},
                    "enabled":true
                  },
                  {
                    "identity":{"kind":"rule","module_key":"growth_base","rule_type":"equipment.item","rule_key":"item.holy_symbol"},
                    "display_name":"item.holy_symbol","description":"Finite Life domain level-one author source.",
                    "source_page":70,"algorithm":"SRD_2014_EQUIPMENT_V1","kind":"HOLY_SYMBOL",
                    "armor_class":0,"strength_minimum":0,"stealth_disadvantage":false,"hands":1,"enabled":true
                  }
                ]
                """).getAsJsonArray();
        JsonArray actual=JsonParser.parseString(new String(material.finalRulesJson(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rules");
        for(JsonElement literal:expected) {
            JsonObject identity=literal.getAsJsonObject().getAsJsonObject("identity");
            List<JsonElement> matches=new ArrayList<>();
            for(JsonElement rule:actual)if(identity.equals(rule.getAsJsonObject().get("identity")))matches.add(rule);
            assertEquals(1,matches.size(),identity.toString());assertEquals(literal,matches.getFirst(),identity.toString());
        }
        JsonObject casting=expected.get(2).getAsJsonObject(),identity=casting.getAsJsonObject("identity");
        JsonArray authorRows=JsonParser.parseString(new String(files.getFirst().get("rules.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        int declaration=-1;
        for(int i=0;i<authorRows.size();i++)if(identity.equals(authorRows.get(i).getAsJsonObject().get("identity")))declaration=i;
        assertTrue(declaration>=0);
        JsonArray chains=JsonParser.parseString(new String(material.chainsJson(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("chains");
        String baseZip=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifacts.getFirst().originalZip()));
        String extensionZip=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifacts.get(1).originalZip()));
        JsonArray authorChanges=JsonParser.parseString(new String(files.get(1).get("changes.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        int materialChange=-1;
        for(int i=0;i<authorChanges.size();i++) {
            JsonObject change=authorChanges.get(i).getAsJsonObject();
            if(identity.equals(change.get("target"))&&new JsonPrimitive("material_item").equals(change.get("field")))materialChange=i;
        }
        assertTrue(materialChange>=0);
        assertEquals(casting.get("material_item"),authorChanges.get(materialChange).getAsJsonObject().get("value"));
        for(String field:List.of("owner_class","required_subclass","required_feature","slot_resource","bless","cure_wounds","material_item")) {
            List<JsonObject> matches=new ArrayList<>();
            for(JsonElement encoded:chains) {
                JsonObject chain=encoded.getAsJsonObject();
                if(identity.equals(chain.get("target"))&&field.equals(chain.get("field").getAsString()))matches.add(chain);
            }
            assertEquals(1,matches.size(),field);JsonArray steps=matches.getFirst().getAsJsonArray("steps");
            assertEquals(field.equals("material_item")?2:1,steps.size(),field);
            JsonObject present=new JsonObject();present.addProperty("state","present");present.add("value",casting.get(field).deepCopy());
            JsonObject initial=steps.get(0).getAsJsonObject();
            assertEquals(JsonParser.parseString("{\"state\":\"uninitialized\"}"),initial.get("before"),field);
            assertEquals(present,initial.get("after"),field);
            assertEquals("initial",initial.get("op").getAsString());assertEquals("declared",initial.get("origin").getAsString());
            assertEquals(JsonParser.parseString("{\"module_key\":\"growth_base\",\"release_version\":\"1\"}"),initial.get("provider"));
            assertEquals(0,initial.get("load_index").getAsInt());
            assertAuthorLocation(initial,baseZip,"rules.json","/"+declaration+"/"+field);
            if(field.equals("material_item")) {
                JsonObject override=steps.get(1).getAsJsonObject();
                assertEquals(present,override.get("before"));assertEquals(present,override.get("after"));
                assertEquals("set_field",override.get("op").getAsString());assertEquals("explicit",override.get("origin").getAsString());
                assertEquals(JsonParser.parseString("{\"module_key\":\"growth_extension\",\"release_version\":\"1\"}"),override.get("provider"));
                assertEquals(1,override.get("load_index").getAsInt());
                assertAuthorLocation(override,extensionZip,"changes.json","/"+materialChange);
            }
        }
    }
    private static void assertAuthorLocation(JsonObject step,String rawZip,String path,String pointer) {
        JsonObject location=step.getAsJsonObject("author_location");
        assertEquals(Set.of("raw_zip_sha256","path","pointer"),location.keySet());
        assertEquals(rawZip,location.get("raw_zip_sha256").getAsString());
        assertEquals(path,location.get("path").getAsString());assertEquals(pointer,location.get("pointer").getAsString());
    }
    @Test void completeLifeDomainSourcesHolySymbolAndProductionProvenanceRestoreExactly() {
        var material=projection();
        var casting=material.projection().rules().stream().filter(FinalSpellcasting.class::isInstance).map(FinalSpellcasting.class::cast).findFirst().orElseThrow();
        assertEquals("LIFE_DOMAIN_LEVEL1_V1",casting.definition().algorithm());assertEquals("ability.wisdom",casting.definition().abilityKey());
        assertEquals(SLOT_RESOURCE,casting.definition().slotResource());assertEquals(LIFE_SUBCLASS,casting.definition().requiredSubclass());
        assertEquals(LIFE_FEATURE,casting.definition().requiredFeature());assertEquals(BLESS,casting.definition().bless());assertEquals(CURE_WOUNDS,casting.definition().cureWounds());
        var symbol=material.projection().rules().stream().filter(FinalItem.class::isInstance).map(FinalItem.class::cast).filter(v->v.identity().equals(HOLY_SYMBOL)).findFirst().orElseThrow();
        assertEquals(EquipmentKind.HOLY_SYMBOL,symbol.definition().kind());assertNull(symbol.definition().proficiency());
        var chain=material.projection().chains().stream().filter(v->v.target().equals(CASTING)&&v.field()==Field.MATERIAL_ITEM).findFirst().orElseThrow();
        assertEquals(2,chain.steps().size());assertEquals(chain.steps().getFirst().after(),chain.steps().getLast().after());
        var restored=ProductionSchema2Projection.restore(material.members(),material.projection().rules(),material.projection().entries(),material.projection().chains());
        assertArrayEquals(material.finalRulesJson(),restored.finalRulesJson());assertArrayEquals(material.chainsJson(),restored.chainsJson());
    }
    @Test void malformedUnknownAndOutOfSliceSpellOrCastingDeclarationsFailClosed() {
        for(String type:List.of("adventure.spell","character.spellcasting")) {
            reject(type,"algorithm",new JsonPrimitive("SCRIPT"));reject(type,"unknown",new JsonPrimitive(true));
            reject(type,"source_page",new JsonPrimitive(1000));reject(type,"description",JsonNull.INSTANCE);
        }
        reject("character.spellcasting","ability_key",new JsonPrimitive("ability.charisma"));
        for(String field:List.of("owner_class","required_subclass","required_feature","slot_resource","bless","cure_wounds","material_item"))
            reject("character.spellcasting",field,Schema2GrantFixtures.rule("growth_base","adventure.spell","spell.missing"));
        reject("adventure.spell","source_page",new JsonPrimitive(132));
    }
    @Test void profileCannotBorrowWrongSlotCapacitySubclassFeatureOrMaterialKinds() {
        for(String field:List.of("maximum_profile","recovery_profile","execution_mode")) {
            var files=packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->row(root,SLOT_RESOURCE).addProperty(field,
                    field.equals("maximum_profile")?"1-20:3":field.equals("recovery_profile")?"1-20:SHORT_REST":"BLOCKED"));
            assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
        }
        for(var entry:List.of(Map.entry(LIFE_SUBCLASS,"selection_level"),Map.entry(LIFE_FEATURE,"level"))) {
            var files=packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->row(root,entry.getKey()).addProperty(entry.getValue(),2));
            assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
        }
        var wrong=packages();Schema2GrantFixtures.edit(wrong,0,"rules.json",root->{var symbol=row(root,HOLY_SYMBOL);symbol.addProperty("kind","WEAPON");});
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(wrong)));
        var extra=packages();Schema2GrantFixtures.edit(extra,0,"rules.json",root->{var copy=row(root,CASTING).deepCopy();copy.getAsJsonObject("identity").addProperty("rule_key","spellcasting.other");root.getAsJsonArray().add(copy);});
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(extra)));
    }
    @Test void requiredReferencesCannotBeClearedAndDisabledSourcesAreNotExecutable() {
        for(String field:List.of("required_subclass","required_feature","slot_resource","bless","cure_wounds","material_item","ability_key")) {
            var files=packages();Schema2GrantFixtures.edit(files,1,"changes.json",root->{var change=new JsonObject();change.addProperty("op","clear_field");change.add("target",Schema2GrantFixtures.rule(CASTING.moduleKey(),CASTING.ruleType(),CASTING.ruleKey()));change.addProperty("field",field);root.getAsJsonArray().add(change);});
            assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files));
        }
        for(RuleIdentity source:List.of(SLOT_RESOURCE,LIFE_SUBCLASS,LIFE_FEATURE,BLESS,CURE_WOUNDS,HOLY_SYMBOL)) {
            var files=packages();Schema2GrantFixtures.edit(files,1,"changes.json",root->{var change=new JsonObject();change.addProperty("op","set_enabled");change.add("target",Schema2GrantFixtures.rule(source.moduleKey(),source.ruleType(),source.ruleKey()));change.addProperty("value",false);root.getAsJsonArray().add(change);});
            assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
        }
    }
    private static void reject(String type,String field,JsonElement value) {
        var files=packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->{for(var element:root.getAsJsonArray()){var row=element.getAsJsonObject();if(row.getAsJsonObject("identity").get("rule_type").getAsString().equals(type)){row.add(field,value);return;}}throw new AssertionError(type);});
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)),type+"/"+field);
    }
    private static JsonObject row(JsonElement root,RuleIdentity identity) {
        for(var element:root.getAsJsonArray()){var row=element.getAsJsonObject();var id=row.getAsJsonObject("identity");if(id.get("rule_type").getAsString().equals(identity.ruleType())&&id.get("rule_key").getAsString().equals(identity.ruleKey()))return row;}throw new AssertionError(identity);
    }
}
