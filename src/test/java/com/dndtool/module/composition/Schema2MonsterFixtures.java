package com.dndtool.module.composition;

import com.dndtool.service.Schema2CombatTestFixture;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;

/** Original SRD stat blocks, real action identities and independent whole-atom source vectors. */
public final class Schema2MonsterFixtures {
    public static final RuleIdentity HELL_HOUND=monster("hell_hound"),MUMMY=monster("mummy"),COMMONER=monster("commoner");
    public static final EntryIdentity FIRE_BREATH=new EntryIdentity(HELL_HOUND,"actions","monster_base","fire_breath");
    public static final RuleIdentity DEX_SAVE=new RuleIdentity(BASE,"character.proficiency","proficiency.save.dexterity"),CON_SAVE=new RuleIdentity(BASE,"character.proficiency","proficiency.save.constitution");
    private Schema2MonsterFixtures() { }
    private static RuleIdentity monster(String key){return new RuleIdentity("monster_base","adventure.monster","monster."+key);}
    public static List<Map<String,byte[]>> packages(){return appendTo(Schema2CombatTestFixture.packages(),true);}
    /** Spell examples retain their existing character grant policy; callers may author a different explicit save basis. */
    public static List<Map<String,byte[]>> spellPackages(){return appendTo(Schema2SpellFixtures.packages(),false);}
    public static List<Map<String,byte[]>> appendTo(List<Map<String,byte[]>> files,boolean guardSavingThrows) {
        if(guardSavingThrows){
            JsonArray definitions=Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
            JsonArray entries=Schema2GrantFixtures.json(files.getFirst().get("growth.json")).getAsJsonArray();
            for(var pair:List.of(Map.entry(DEX_SAVE,"ability.dexterity"),Map.entry(CON_SAVE,"ability.constitution"))){
                var d=new JsonObject();d.add("identity",jsonIdentity(pair.getKey()));d.addProperty("display_name",pair.getKey().ruleKey());d.addProperty("description","Explicit initial saving throw training in this bounded guard author vector.");d.addProperty("category","SAVING_THROW");d.addProperty("ability_key",pair.getValue());
                // Author proficiency declarations use the established character catalog page interval.
                d.addProperty("source_page",56);definitions.add(d);
                var slot=new JsonObject();var id=new JsonObject();id.addProperty("kind","entry");id.add("rule",jsonIdentity(PROGRESSION));id.addProperty("collection_key","starting_proficiencies");id.addProperty("module_key",BASE);id.addProperty("entry_key",pair.getKey().equals(DEX_SAVE)?"save_dexterity":"save_constitution");slot.add("identity",id);var grant=new JsonObject();grant.addProperty("kind","FIXED");grant.add("target",jsonIdentity(pair.getKey()));slot.add("growth_grant",grant);entries.add(slot);
            }
            files.getFirst().put("rules.json",bytes(definitions.toString()));files.getFirst().put("growth.json",bytes(entries.toString()));
        }
        for(String module:List.of("monster_base","monster_extension")){var file=new LinkedHashMap<String,byte[]>();file.put(HEADER_PATH,resource(module+"/"+HEADER_PATH));if(module.equals("monster_base")){file.put("monsters.json",resource(module+"/monsters.json"));file.put("actions.json",resource(module+"/actions.json"));}else file.put("changes.json",resource(module+"/changes.json"));files.add(file);}
        return files;
    }
    public static List<ProductionRuleArtifact> artifacts(){return Schema2GrantFixtures.artifacts(packages());}
    public static ProductionSchema2Projection projection(){return ProductionSchema2Projection.compose(artifacts());}
    public static ProductionSchema2Projection spellProjection(){return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(spellPackages()));}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static byte[] resource(String path){try(var input=Schema2MonsterFixtures.class.getResourceAsStream("/schema2-monster/"+path)){if(input==null)throw new AssertionError(path);return input.readAllBytes();}catch(IOException e){throw new AssertionError(e);}}
}
