package com.dndtool.module.composition;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2EquipmentHealthCompositionTest {
    @Test void negativeDefinitionAndSetFieldTokensRemainSignedThroughStoredRestoration() {
        for (int value : List.of(-1000, -10, -1, 0, 1000)) {
            var files = Schema2EquipmentHealthFixtures.packages();
            Schema2GrantFixtures.edit(files, 0, "rules.json", root -> row(root,"adventure.vital_effect").addProperty("modifier", value));
            Schema2GrantFixtures.edit(files, 1, "changes.json", root -> modifierChange(root).addProperty("value", value));
            var material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
            for (String key : List.of("vital_effect.flame", "vital_effect.converted")) {
                var effect = material.projection().rules().stream().filter(r -> r.identity().ruleKey().equals(key))
                        .map(FinalVitalEffect.class::cast).findFirst().orElseThrow();
                assertEquals(value, effect.definition().modifier());
            }
            var changed = material.projection().chains().stream().filter(c -> c.field()==Field.MODIFIER
                    && ((RuleIdentity)c.target()).ruleKey().equals("vital_effect.converted")).findFirst().orElseThrow();
            assertEquals(new IntegerValue(value), changed.steps().getLast().after().value());
            var restored = ProductionSchema2Projection.restore(material.members(), material.projection().rules(),
                    material.projection().entries(), material.projection().chains());
            assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
            assertArrayEquals(material.chainsJson(), restored.chainsJson());
        }
    }
    @Test void modifierLexemesRejectNegativeZeroFractionsExponentsCoercionsAndOutOfRangeIntegers() {
        for (String token : List.of("-0", "-0.0", "-1.0", "-1e0", "1E0", "\"-1\"", "true", "null",
                "-1001", "1001", "-2147483648", "-2147483649", "2147483648", "+1", "01")) {
            for (int packageIndex : List.of(0,1)) {
                var files = Schema2EquipmentHealthFixtures.packages();
                String path = packageIndex==0 ? "rules.json" : "changes.json";
                Schema2GrantFixtures.edit(files, packageIndex, path, root -> {
                    if(packageIndex==0) row(root,"adventure.vital_effect").addProperty("modifier",777);
                    else modifierChange(root).addProperty("value",777);
                });
                String text = new String(files.get(packageIndex).get(path),StandardCharsets.UTF_8);
                String field = packageIndex==0 ? "modifier" : "value";
                files.get(packageIndex).put(path,text.replace("\""+field+"\":777", "\""+field+"\":"+token).getBytes(StandardCharsets.UTF_8));
                assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files),path+"/"+token);
            }
        }
    }
    @Test void fixedTypesReferencesNegativeModifierAndScopedClearsKeepCompleteProvenance() {
        var material=Schema2EquipmentHealthFixtures.projection();var rows=material.projection().rules();
        assertEquals(6,rows.stream().filter(FinalItem.class::isInstance).count());assertEquals(4,rows.stream().filter(FinalVitalEffect.class::isInstance).count());
        var heavy=rows.stream().filter(r->r.identity().ruleKey().equals("item.heavy_armor")).map(FinalItem.class::cast).findFirst().orElseThrow();
        assertEquals(EquipmentKind.HEAVY_ARMOR,heavy.definition().kind());assertEquals(18,heavy.definition().armorClass());assertEquals(15,heavy.definition().strengthMinimum());assertFalse(heavy.definition().stealthDisadvantage());assertNull(heavy.definition().stealthSkill());
        var flame=rows.stream().filter(r->r.identity().ruleKey().equals("vital_effect.flame")).map(FinalVitalEffect.class::cast).findFirst().orElseThrow();
        assertEquals(-3,flame.definition().modifier());assertEquals(DamageType.FIRE,flame.definition().damageType());
        var converted=rows.stream().filter(r->r.identity().ruleKey().equals("vital_effect.converted")).map(FinalVitalEffect.class::cast).findFirst().orElseThrow();
        assertEquals(VitalEffectKind.HEALING,converted.definition().kind());assertEquals(0,converted.definition().diceCount());assertEquals(7,converted.definition().modifier());assertNull(converted.definition().damageType());
        for(Field field:List.of(Field.STEALTH_SKILL,Field.DAMAGE_TYPE)) {
            var cleared=material.projection().chains().stream().filter(c->c.field()==field&&c.steps().size()==2).findFirst().orElseThrow();
            assertEquals(StateKind.PRESENT,cleared.steps().getFirst().after().state());assertEquals("clear_field",cleared.steps().getLast().op());assertEquals(StateKind.ABSENT,cleared.steps().getLast().after().state());
        }
        var restored=ProductionSchema2Projection.restore(material.members(),rows,material.projection().entries(),material.projection().chains());
        assertArrayEquals(material.finalRulesJson(),restored.finalRulesJson());assertArrayEquals(material.chainsJson(),restored.chainsJson());
    }
    @Test void closedShapesAlgorithmsAndCompleteMechanicsRejectInvalidAuthors() {
        for(String type:List.of("equipment.item","adventure.health","adventure.vital_effect")) {
            var files=Schema2EquipmentHealthFixtures.packages();
            Schema2GrantFixtures.edit(files,0,"rules.json",root->row(root,type).addProperty("algorithm","UNKNOWN_SCRIPT"));
            assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files));
            var extra=Schema2EquipmentHealthFixtures.packages();Schema2GrantFixtures.edit(extra,0,"rules.json",root->row(root,type).addProperty("script","anything"));
            assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(extra));
        }
        for(var invalid:List.of(Map.entry("hands",new JsonPrimitive(1)),Map.entry("armor_class",new JsonPrimitive(0)),Map.entry("strength_minimum",new JsonPrimitive(13)),Map.entry("stealth_disadvantage",new JsonPrimitive(true)))) {
            var files=Schema2EquipmentHealthFixtures.packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->row(root,"equipment.item").add(invalid.getKey(),invalid.getValue()));
            assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files));
        }
        for(var invalid:List.of(Map.entry("dice_count",21),Map.entry("dice_sides",3),Map.entry("modifier",-1001),Map.entry("modifier",1001),Map.entry("source_page",95))) {
            var files=Schema2EquipmentHealthFixtures.packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->row(root,"adventure.vital_effect").addProperty(invalid.getKey(),invalid.getValue()));
            assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files));
        }
    }
    @Test void typedReferenceCategoryStealthSkillAvailabilityAndHealthUniquenessFailClosed() {
        for(String target:List.of("proficiency.weapon","proficiency.strength","proficiency.missing")) {
            var files=Schema2EquipmentHealthFixtures.packages();
            Schema2GrantFixtures.edit(files,0,"rules.json",root->{for(var element:root.getAsJsonArray()){var value=element.getAsJsonObject();if(value.getAsJsonObject("identity").get("rule_key").getAsString().equals("item.heavy_armor"))value.add("stealth_skill",Schema2GrantFixtures.rule("vitals_base","character.proficiency",target));}});
            assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
        }
        var wrong=Schema2EquipmentHealthFixtures.packages();Schema2GrantFixtures.edit(wrong,0,"rules.json",root->row(root,"equipment.item").add("proficiency",Schema2GrantFixtures.rule("vitals_base","character.proficiency","proficiency.weapon")));
        assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(wrong)));
        var duplicate=Schema2EquipmentHealthFixtures.packages();Schema2GrantFixtures.edit(duplicate,0,"rules.json",root->{var extra=row(root,"adventure.health").deepCopy();extra.getAsJsonObject("identity").addProperty("rule_key","health.second");root.getAsJsonArray().add(extra);});
        assertEquals("S2_HEALTH_AMBIGUOUS",assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(duplicate))).diagnostic().code());
    }
    @Test void validFinalValueCannotHideInvalidOriginalSignedModifierOrAlgorithm() {
        var material=Schema2EquipmentHealthFixtures.projection();
        for(Field field:List.of(Field.MODIFIER,Field.ALGORITHM)) {
            var chains=new ArrayList<>(material.projection().chains());
            for(int i=0;i<chains.size();i++){var chain=chains.get(i);if(chain.field()!=field||chain.steps().size()!=2||!((RuleIdentity)chain.target()).ruleKey().equals("vital_effect.converted"))continue;
                var steps=new ArrayList<>(chain.steps());var first=steps.getFirst();var last=steps.getLast();
                var bad=new FieldState(StateKind.PRESENT,field==Field.MODIFIER?new IntegerValue(-1001):new TextValue("UNKNOWN_ORIGINAL"));
                steps.set(0,new ChainStep(first.op(),first.before(),bad,first.provider(),first.loadIndex(),first.authorLocation(),first.origin()));steps.set(1,new ChainStep(last.op(),bad,last.after(),last.provider(),last.loadIndex(),last.authorLocation(),last.origin()));chains.set(i,new FieldChain(chain.target(),field,steps));
            }
            assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.restore(material.members(),material.projection().rules(),material.projection().entries(),chains));
        }
    }
    private static JsonObject modifierChange(JsonElement root){for(var element:root.getAsJsonArray()){var row=element.getAsJsonObject();if(row.has("field")&&row.get("field").getAsString().equals("modifier"))return row;}throw new AssertionError("modifier change");}
    private static JsonObject row(JsonElement root,String type){for(var element:root.getAsJsonArray()){var row=element.getAsJsonObject();if(row.getAsJsonObject("identity").get("rule_type").getAsString().equals(type))return row;}throw new AssertionError(type);}
}
