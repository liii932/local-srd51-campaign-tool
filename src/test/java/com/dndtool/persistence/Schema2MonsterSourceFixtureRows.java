package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent original-JSON oracle. Does not call production monster decoders or row projectors. */
final class Schema2MonsterSourceFixtureRows {
    static final List<String> TABLES=List.of("rule_schema2_monster","rule_schema2_monster_action","rule_schema2_monster_change","rule_schema2_monster_stat","rule_schema2_monster_save","rule_schema2_monster_skill","rule_schema2_monster_defense","rule_schema2_monster_condition","rule_schema2_monster_language","rule_schema2_monster_trait","rule_schema2_monster_action_value");
    private Schema2MonsterSourceFixtureRows() { }
    static void seed(Map<String,List<Map<String,Object>>> tables,ProductionRuleArtifact artifact,String kind,int index,JsonObject value,JsonObject owner) {
        var row=key(artifact,index);JsonObject target=value.getAsJsonObject(kind.equals("changes")?"target":"identity");boolean entry=target.get("kind").getAsString().equals("entry");
        row.put("rule_module_key",bytes(owner.get("module_key").getAsString()));row.put("rule_type",bytes("adventure.monster"));row.put("rule_key",bytes(owner.get("rule_key").getAsString()));
        if(kind.equals("definitions")){for(String f:List.of("display_name","description","author_note"))row.put(f,value.has(f)?value.get(f).getAsString():null);row.put("source_page",value.get("source_page").getAsInt());tables.get(TABLES.get(0)).add(row);stat(tables,artifact,"DEFINITION",index,value.getAsJsonObject("stat_block"));return;}
        for(String f:List.of("collection_key","module_key","entry_key"))row.put(f,entry?bytes(target.get(f).getAsString()):null);
        if(kind.equals("entries")){row.put("author_note",value.has("author_note")?value.get("author_note").getAsString():null);tables.get(TABLES.get(1)).add(row);action(tables,artifact,"ENTRY",index,value.getAsJsonObject("action_profile"));return;}
        row.put("target_kind",bytes(entry?"entry":"rule"));String op=value.get("op").getAsString(),field=op.equals("set_enabled")?"enabled":value.get("field").getAsString();row.put("operation",bytes(op));row.put("field_key",bytes(field));for(String f:List.of("text_value","integer_value","boolean_value","atom_kind"))row.put(f,null);
        if(op.equals("set_enabled"))row.put("boolean_value",value.get("value").getAsBoolean()?1:0);
        else if(op.equals("set_field")){var atom=value.get("value");switch(field){case "stat_block"->{row.put("atom_kind",bytes("STAT_BLOCK"));stat(tables,artifact,"CHANGE",index,atom.getAsJsonObject());}case "action_profile"->{row.put("atom_kind",bytes("ACTION_PROFILE"));action(tables,artifact,"CHANGE",index,atom.getAsJsonObject());}case "source_page"->row.put("integer_value",atom.getAsInt());default->row.put("text_value",atom.getAsString());}}
        tables.get(TABLES.get(2)).add(row);
    }
    private static void stat(Map<String,List<Map<String,Object>>> tables,ProductionRuleArtifact artifact,String kind,int index,JsonObject value){
        var row=atomKey(artifact,kind,index);
        for(String f:List.of("size","creature_type"))row.put(f,bytes(value.get(f).getAsString()));for(String f:List.of("alignment","armor_description"))row.put(f,value.get(f).getAsString());
        for(String f:List.of("armor_class","hit_points","hit_dice_count","hit_dice_sides","hit_dice_modifier","walk_speed_feet","darkvision_feet","blindsight_feet","tremorsense_feet","truesight_feet","passive_perception","challenge_numerator","challenge_denominator","experience_points"))row.put(f,value.get(f).getAsInt());
        for(String f:List.of("strength","dexterity","constitution","intelligence","wisdom","charisma"))row.put(f,value.getAsJsonObject("abilities").get(f).getAsInt());tables.get("rule_schema2_monster_stat").add(row);
        for(var pair:Map.of("saving_throws","save","skills","skill","damage_defenses","defense","condition_immunities","condition","languages","language","traits","trait").entrySet()){
            JsonArray values=value.getAsJsonArray(pair.getKey());for(int i=0;i<values.size();i++){var child=atomKey(artifact,kind,index);child.put("ordinal",i);var raw=values.get(i);
                if(pair.getValue().equals("condition"))child.put("condition_key",bytes(raw.getAsString()));else {var o=raw.getAsJsonObject();for(var field:o.entrySet()){String name=field.getKey().equals("key")?"trait_key":field.getKey().equals("scope")?"trait_scope":field.getKey();var atom=field.getValue().getAsJsonPrimitive();if(atom.isNumber())child.put(name,atom.getAsInt());else if(atom.isBoolean())child.put(name,atom.getAsBoolean()?1:0);else child.put(name,Set.of("name","display_name","description").contains(name)?atom.getAsString():bytes(atom.getAsString()));}}
                tables.get("rule_schema2_monster_"+pair.getValue()).add(child);
            }
        }
    }
    private static void action(Map<String,List<Map<String,Object>>> tables,ProductionRuleArtifact artifact,String kind,int index,JsonObject value){
        var row=atomKey(artifact,kind,index);for(String f:List.of("display_name","description"))row.put(f,value.get(f).getAsString());row.put("source_page",value.get("source_page").getAsInt());row.put("mode",bytes(value.get("mode").getAsString()));
        for(String f:List.of("mechanism","save_ability_key","save_dc_mode","save_dc","fail_outcome","success_outcome","damage_dice_count","damage_dice_sides","damage_type","damage_delivery","damage_ability_modifier","area_shape","area_length_feet","area_origin_policy","recharge_dice_sides","recharge_minimum_roll","recharge_turn_start","recharge_short_rest","recharge_long_rest"))row.put(f,null);
        if(value.has("mechanism")){row.put("mechanism",bytes(value.get("mechanism").getAsString()));for(String parent:List.of("save_policy","damage_profile","area","recharge"))for(var field:value.getAsJsonObject(parent).entrySet()){
            String name=field.getKey();if(parent.equals("save_policy")&&!Set.of("fail_outcome","success_outcome").contains(name))name="save_"+name;else if(parent.equals("damage_profile"))name=name.equals("damage_type")?name:"damage_"+name;else if(!parent.equals("save_policy"))name=parent+"_"+name;
            var atom=field.getValue().getAsJsonPrimitive();row.put(name,atom.isBoolean()?atom.getAsBoolean()?1:0:atom.isNumber()?atom.getAsInt():bytes(atom.getAsString()));
        }}tables.get("rule_schema2_monster_action_value").add(row);
    }
    private static Map<String,Object> key(ProductionRuleArtifact a,int i){var r=new LinkedHashMap<String,Object>();r.put("artifact_module_key",bytes(a.moduleKey()));r.put("release_version",bytes(a.releaseVersion()));r.put("author_index",i);return r;}
    private static Map<String,Object> atomKey(ProductionRuleArtifact a,String k,int i){var r=key(a,i);r.remove("author_index");r.put("owner_kind",bytes(k));r.put("owner_index",i);return r;}
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.US_ASCII);}
}
