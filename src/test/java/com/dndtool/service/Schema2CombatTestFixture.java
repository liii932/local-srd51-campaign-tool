package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;

/** Complete finite author/preparation fixtures for the real character and combat consumers. */
public final class Schema2CombatTestFixture {
    public static final RuleIdentity COMBAT=new RuleIdentity(BASE,"adventure.combat","combat.standard");
    public static final RuleIdentity WEAPON_ATTACK=new RuleIdentity(BASE,"equipment.weapon_attack","weapon_attack.ordinary");
    public static final RuleIdentity TWO_HANDED_ATTACK=new RuleIdentity(BASE,"equipment.weapon_attack","weapon_attack.two_handed");
    public static final RuleIdentity MOVEMENT=new RuleIdentity(BASE,"character.movement","movement.human");
    private Schema2CombatTestFixture() { }
    public static List<Map<String,byte[]>> packages() {
        var files=Schema2CheckTestFixture.packages("normal");
        JsonArray definitions=Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
        JsonObject combat=definition(COMBAT,91);combat.addProperty("algorithm","SRD_2014_COMBAT_V1");definitions.add(combat);
        JsonObject movement=definition(MOVEMENT,7);movement.add("owner",jsonIdentity(new RuleIdentity(BASE,"character.race","race.human")));
        movement.addProperty("walk_speed_feet",30);movement.addProperty("algorithm","SRD_2014_WALKING_SPEED_V1");definitions.add(movement);
        for(var pair:List.of(Map.entry(WEAPON_ATTACK,ITEM_WEAPON),Map.entry(TWO_HANDED_ATTACK,ITEM_TWO_HANDED))) {
            JsonObject attack=definition(pair.getKey(),65);attack.add("item",jsonIdentity(pair.getValue()));attack.addProperty("ability_policy","FINESSE");
            attack.addProperty("dice_count",1);attack.addProperty("dice_sides",8);attack.addProperty("damage_type","SLASHING");
            attack.addProperty("reach_feet",5);attack.addProperty("algorithm","SRD_2014_MELEE_WEAPON_ATTACK_V1");definitions.add(attack);
        }
        files.getFirst().put("rules.json",definitions.toString().getBytes(StandardCharsets.UTF_8));return files;
    }
    public static ProductionSchema2Projection material() {return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages()));}
    public static ProductionSchema2Projection material(int speed,int reach) {
        var files=packages();JsonArray rows=Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
        for(var element:rows) {JsonObject row=element.getAsJsonObject();String type=row.getAsJsonObject("identity").get("rule_type").getAsString();
            if(type.equals("character.movement"))row.addProperty("walk_speed_feet",speed);
            if(type.equals("equipment.weapon_attack"))row.addProperty("reach_feet",reach);
        }
        files.getFirst().put("rules.json",rows.toString().getBytes(StandardCharsets.UTF_8));return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }
    public static Context context(ProductionSchema2Projection material,UUID campaign,UUID character,Identity identity,UUID preparation) {
        var initial=fullPlan(material,identity,campaign,character);
        State state=new Schema2CharacterProgressionRules().initial(material,initial,preparation,fullAbilities(),INITIAL_CHOICES);
        var equipment=new Schema2CharacterEquipmentRules();UUID instance=UUID.randomUUID();
        state=equipment.apply(material,state,Schema2CheckTestFixture.equipment("ITEM_ACQUIRED",instance,ITEM_WEAPON,"CARRIED"),instance);
        state=equipment.apply(material,state,Schema2CheckTestFixture.equipment("ITEM_EQUIPPED",instance,null,"MAIN_HAND"),UUID.randomUUID());
        return new Context(material,initial,state);
    }
    public static Context context(ProductionSchema2Projection material,UUID campaign,UUID character) {
        return context(material,campaign,character,new Identity(UUID.randomUUID(),UUID.randomUUID()),UUID.randomUUID());
    }
    public static Context withState(Context context,State state) {return new Context(context.material(),context.initial(),state);}
    public static State withHealth(State state,long hp,HealthState health) {
        return new State(state.identity(),state.campaignId(),state.characterId(),state.preparationOperationId(),state.characterClass(),state.abilities(),state.level(),state.proficiencyBonus(),hp,state.maximumHitPoints(),state.classes(),state.hitDicePools(),state.subclasses(),state.features(),state.feats(),state.proficiencies(),state.version(),state.resources(),state.rest(),state.equipment(),health);
    }
    private static JsonObject definition(RuleIdentity id,int page) {
        JsonObject row=new JsonObject();row.add("identity",jsonIdentity(id));row.addProperty("display_name",id.ruleKey());row.addProperty("description","Finite ordinary internal melee fixture.");row.addProperty("source_page",page);return row;
    }
}
