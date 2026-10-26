package com.dndtool.module.composition;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CombatCompositionTest {
    @Test void typedRulesAndEveryOverrideRestoreWithExactProductionBytesAndFullProvenance() {
        var material=Schema2CombatFixtures.projection();
        var weapon=material.projection().rules().stream().filter(FinalWeaponAttack.class::isInstance).map(FinalWeaponAttack.class::cast).findFirst().orElseThrow();
        assertEquals(WeaponAbilityPolicy.FINESSE,weapon.definition().abilityPolicy());assertEquals(2,weapon.definition().diceCount());
        assertEquals(6,weapon.definition().diceSides());assertEquals(DamageType.SLASHING,weapon.definition().damageType());assertEquals(5,weapon.definition().reachFeet());
        assertEquals(new RuleIdentity("combat_base","equipment.item","item.blade"),weapon.definition().item());
        var movement=material.projection().rules().stream().filter(FinalMovement.class::isInstance).map(FinalMovement.class::cast).findFirst().orElseThrow();
        assertEquals(35,movement.definition().walkSpeedFeet());assertNull(movement.definition().authorNote());
        var chain=material.projection().chains().stream().filter(c->c.field()==Field.WALK_SPEED_FEET).findFirst().orElseThrow();
        assertEquals(new IntegerValue(30),chain.steps().getFirst().after().value());assertEquals(new IntegerValue(35),chain.steps().getLast().after().value());
        assertEquals("combat_extension",chain.steps().getLast().provider().moduleKey());
        var restored=ProductionSchema2Projection.restore(material.members(),material.projection().rules(),material.projection().entries(),material.projection().chains());
        assertArrayEquals(material.finalRulesJson(),restored.finalRulesJson());assertArrayEquals(material.chainsJson(),restored.chainsJson());
    }
    @Test void unknownAlgorithmsFieldsEnumsAndOutOfRangeParametersFailClosed() {
        for(String type:List.of("adventure.combat","equipment.weapon_attack","character.movement")) {
            reject(type,"algorithm",new JsonPrimitive("SCRIPT"));reject(type,"unknown",new JsonPrimitive(true));
            reject(type,"source_page",new JsonPrimitive(1000));reject(type,"description",JsonNull.INSTANCE);
        }
        for(String field:List.of("dice_count","dice_sides","reach_feet")) reject("equipment.weapon_attack",field,new JsonPrimitive(0));
        reject("equipment.weapon_attack","dice_count",new JsonPrimitive(5));reject("equipment.weapon_attack","dice_sides",new JsonPrimitive(7));
        reject("equipment.weapon_attack","reach_feet",new JsonPrimitive(6));reject("equipment.weapon_attack","damage_type",new JsonPrimitive("FIRE"));
        reject("equipment.weapon_attack","ability_policy",new JsonPrimitive("DEXTERITY"));
        for(int speed:List.of(0,6,121))reject("character.movement","walk_speed_feet",new JsonPrimitive(speed));
    }
    @Test void duplicateCombatItemProfilesAndMovementSourcesAreRejected() {
        for(String type:List.of("adventure.combat","equipment.weapon_attack","character.movement")) {
            var files=Schema2CombatFixtures.packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->{var extra=row(root,type).deepCopy();var id=extra.getAsJsonObject("identity");id.addProperty("rule_key",id.get("rule_key").getAsString()+"_duplicate");root.getAsJsonArray().add(extra);});
            assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
        }
    }
    @Test void missingWrongTypedDisabledAndWrongKindSourcesCannotMasqueradeAsRealWeaponsOrRaces() {
        for(String field:List.of("item","owner")) {
            String type=field.equals("item")?"equipment.weapon_attack":"character.movement";
            var files=Schema2CombatFixtures.packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->row(root,type).add(field,Schema2GrantFixtures.rule("combat_base","character.proficiency","proficiency.weapon")));
            assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
        }
        var missing=Schema2CombatFixtures.packages();Schema2GrantFixtures.edit(missing,0,"rules.json",root->row(root,"equipment.weapon_attack").add("item",Schema2GrantFixtures.rule("combat_base","equipment.item","item.missing")));
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(missing)));
        var wrongKind=Schema2CombatFixtures.packages();Schema2GrantFixtures.edit(wrongKind,0,"rules.json",root->{
            var armor=row(root,"character.proficiency").deepCopy();armor.getAsJsonObject("identity").addProperty("rule_key","proficiency.armor");armor.addProperty("category","ARMOR");root.getAsJsonArray().add(armor);
            var item=row(root,"equipment.item");item.addProperty("kind","SHIELD");item.addProperty("armor_class",2);item.add("proficiency",Schema2GrantFixtures.rule("combat_base","character.proficiency","proficiency.armor"));
        });
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(wrongKind)));
        var disabled=Schema2CombatFixtures.packages();Schema2GrantFixtures.edit(disabled,1,"changes.json",root->{JsonObject change=new JsonObject();change.addProperty("op","set_enabled");change.add("target",Schema2GrantFixtures.rule("combat_base","equipment.item","item.blade"));change.addProperty("value",false);root.getAsJsonArray().add(change);});
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(disabled)));
    }
    @Test void requiredDamageCannotBeClearedAndInvalidOriginalParametersCannotHideBehindValidOverrides() {
        var files=Schema2CombatFixtures.packages();Schema2GrantFixtures.edit(files,1,"changes.json",root->{var change=new JsonObject();change.addProperty("op","clear_field");change.add("target",Schema2GrantFixtures.rule("combat_base","equipment.weapon_attack","weapon_attack.blade"));change.addProperty("field","damage_type");root.getAsJsonArray().add(change);});
        assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files));
        var material=Schema2CombatFixtures.projection();var chains=new ArrayList<>(material.projection().chains());
        for(int i=0;i<chains.size();i++){var chain=chains.get(i);if(chain.field()!=Field.WALK_SPEED_FEET)continue;var steps=new ArrayList<>(chain.steps());var first=steps.getFirst();var last=steps.getLast();var bad=new FieldState(StateKind.PRESENT,new IntegerValue(31));steps.set(0,new ChainStep(first.op(),first.before(),bad,first.provider(),first.loadIndex(),first.authorLocation(),first.origin()));steps.set(1,new ChainStep(last.op(),bad,last.after(),last.provider(),last.loadIndex(),last.authorLocation(),last.origin()));chains.set(i,new FieldChain(chain.target(),chain.field(),steps));}
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.restore(material.members(),material.projection().rules(),material.projection().entries(),chains));
    }
    private static void reject(String type,String field,JsonElement value){var files=Schema2CombatFixtures.packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->row(root,type).add(field,value));assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)),type+"/"+field);}
    private static JsonObject row(JsonElement root,String type){for(var element:root.getAsJsonArray()){var row=element.getAsJsonObject();if(row.getAsJsonObject("identity").get("rule_type").getAsString().equals(type))return row;}throw new AssertionError(type);}
}
