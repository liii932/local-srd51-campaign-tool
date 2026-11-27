package com.dndtool.module.composition;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2MonsterCompositionTest {
    @Test void originalStatBlocksDefensesRealActionIdentityAndWholeAtomProvenanceSurviveProductionRestore(){
        var p=Schema2MonsterFixtures.projection();var monsters=p.projection().rules().stream().filter(FinalMonster.class::isInstance).map(FinalMonster.class::cast).toList();assertEquals(3,monsters.size());
        var h=monsters.stream().filter(m->m.identity().equals(Schema2MonsterFixtures.HELL_HOUND)).findFirst().orElseThrow().definition();
        assertEquals(new MonsterAbilities(17,12,14,6,13,6),h.statBlock().abilities());assertEquals(15,h.statBlock().armorClass());assertEquals(45,h.statBlock().hitPoints());assertEquals(50,h.statBlock().walkSpeedFeet());assertEquals(List.of(new MonsterDamageDefense(MonsterDefenseKind.IMMUNITY,DamageType.FIRE,MonsterDefenseQualifier.ALL)),h.statBlock().damageDefenses());assertEquals(new MonsterLanguage("Infernal",true,false),h.statBlock().languages().getFirst());assertEquals(List.of(MonsterTraitScope.PERCEPTION_HEARING_SMELL_ONLY,MonsterTraitScope.ATTACK_ROLL_PACK_TACTICS_ONLY),h.statBlock().traits().stream().map(MonsterTrait::scope).toList());assertNull(h.authorNote());
        var mummy=monsters.stream().filter(m->m.identity().equals(Schema2MonsterFixtures.MUMMY)).findFirst().orElseThrow().definition().statBlock();assertEquals(58,mummy.hitPoints());assertEquals(6,mummy.damageDefenses().size());assertTrue(mummy.damageDefenses().contains(new MonsterDamageDefense(MonsterDefenseKind.VULNERABILITY,DamageType.FIRE,MonsterDefenseQualifier.ALL)));assertFalse(mummy.damageDefenses().contains(new MonsterDamageDefense(MonsterDefenseKind.IMMUNITY,DamageType.FIRE,MonsterDefenseQualifier.ALL)));assertEquals(3,mummy.damageDefenses().stream().filter(d->d.qualifier()==MonsterDefenseQualifier.NONMAGICAL_ATTACKS).count());
        var action=p.projection().entries().stream().map(FinalEntry::entry).filter(MonsterActionEntry.class::isInstance).map(MonsterActionEntry.class::cast).filter(a->a.identity().equals(Schema2MonsterFixtures.FIRE_BREATH)).findFirst().orElseThrow();
        assertEquals(MonsterActionMode.SAVE_DAMAGE_BATCH_V1,action.actionProfile().mode());assertEquals(new MonsterSavePolicy("ability.dexterity","FIXED_DC",12,"FULL","HALF"),action.actionProfile().savePolicy());assertEquals(new MonsterDamageProfile(6,6,DamageType.FIRE,"OTHER_DELIVERY","NO_ABILITY_MODIFIER"),action.actionProfile().damageProfile());assertEquals(new MonsterArea("CONE",15,"EXCLUDE_ORIGIN"),action.actionProfile().area());assertEquals(new MonsterRecharge(6,5,true,true,true),action.actionProfile().recharge());
        var chain=p.projection().chains().stream().filter(c->c.target().equals(action.identity())&&c.field()==Field.ACTION_PROFILE).findFirst().orElseThrow();assertEquals(2,chain.steps().size());assertEquals("monster_extension",chain.steps().getLast().provider().moduleKey());assertEquals(chain.steps().getFirst().after(),chain.steps().getLast().before());
        var restored=ProductionSchema2Projection.restore(p.members(),p.projection().rules(),p.projection().entries(),p.projection().chains());assertArrayEquals(p.finalRulesJson(),restored.finalRulesJson());assertArrayEquals(p.chainsJson(),restored.chainsJson());
        // Independent field-for-field oracle reads static original JSON, not a production value encoder.
        var original=JsonParser.parseString(new String(Schema2MonsterFixtures.packages().get(2).get("monsters.json"),StandardCharsets.UTF_8)).getAsJsonArray();var stored=JsonParser.parseString(new String(p.finalRulesJson(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rules");
        for(var raw:original){var expected=raw.getAsJsonObject().deepCopy();if(expected.getAsJsonObject("identity").get("rule_key").getAsString().equals("monster.hell_hound"))expected.remove("author_note");expected.addProperty("enabled",true);assertTrue(stored.asList().contains(expected));}
    }
    @Test void unknownNestedFieldsNullsDuplicatesUnsupportedProfilesAndPartialAtomsFailClosed(){
        for(String field:List.of("size","abilities","skills","damage_defenses","traits","languages"))reject("monsters.json",root->root.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("stat_block").add(field,JsonNull.INSTANCE));
        reject("monsters.json",root->root.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("stat_block").addProperty("unknown",true));
        reject("monsters.json",root->root.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("stat_block").getAsJsonArray("traits").get(0).getAsJsonObject().remove("scope"));
        reject("monsters.json",root->root.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("stat_block").getAsJsonArray("traits").get(0).getAsJsonObject().addProperty("scope","SCRIPT"));
        reject("monsters.json",root->{var d=root.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("stat_block");d.getAsJsonArray("damage_defenses").add(d.getAsJsonArray("damage_defenses").get(0).deepCopy());});
        for(String field:List.of("save_policy","damage_profile","area","recharge","mechanism"))reject("actions.json",root->root.getAsJsonArray().get(1).getAsJsonObject().getAsJsonObject("action_profile").remove(field));
        reject("actions.json",root->root.getAsJsonArray().get(1).getAsJsonObject().getAsJsonObject("action_profile").getAsJsonObject("save_policy").addProperty("dc",13));
        reject("actions.json",root->root.getAsJsonArray().get(1).getAsJsonObject().getAsJsonObject("action_profile").getAsJsonObject("damage_profile").addProperty("dice_count",7));
        reject("actions.json",root->root.getAsJsonArray().get(1).getAsJsonObject().getAsJsonObject("action_profile").getAsJsonObject("area").addProperty("unknown",true));
        reject("actions.json",root->root.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("action_profile").addProperty("mechanism","HELL_HOUND_FIRE_BREATH_V1"));
        var files=Schema2MonsterFixtures.packages();String text=new String(files.get(2).get("actions.json"),StandardCharsets.UTF_8);files.get(2).put("actions.json",text.replace("\"dc\": 12","\"dc\": 12, \"dc\": 12").getBytes(StandardCharsets.UTF_8));assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files));
    }
    @Test void fakeOwnersDisabledSourcesAndFieldClearsCannotCreateRunnableActions(){
        reject("actions.json",root->root.getAsJsonArray().get(1).getAsJsonObject().getAsJsonObject("identity").getAsJsonObject("rule").addProperty("rule_key","monster.missing"));
        var files=Schema2MonsterFixtures.packages();Schema2GrantFixtures.edit(files,3,"changes.json",root->{var c=new JsonObject();c.addProperty("op","set_enabled");c.add("target",Schema2ProgressionFixtures.jsonIdentity(Schema2MonsterFixtures.HELL_HOUND));c.addProperty("value",false);root.getAsJsonArray().add(c);});var disabled=files;assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(disabled)));
        files=Schema2MonsterFixtures.packages();Schema2GrantFixtures.edit(files,3,"changes.json",root->{var c=root.getAsJsonArray().get(0).getAsJsonObject();c.addProperty("op","clear_field");c.remove("value");});var invalid=files;assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(invalid));
    }
    @Test void actualSavingThrowProficiencyAtomsRequireAnAbilityAndRemainDistinctFromSkills(){
        var material=Schema2MonsterFixtures.projection();var definitions=material.projection().rules().stream().filter(FinalProficiency.class::isInstance).map(FinalProficiency.class::cast).filter(p->p.definition().category()==ProficiencyCategory.SAVING_THROW).toList();assertEquals(2,definitions.size());assertEquals(Set.of("ability.dexterity","ability.constitution"),new HashSet<>(definitions.stream().map(p->p.definition().abilityKey()).toList()));
        var files=Schema2MonsterFixtures.packages();Schema2GrantFixtures.edit(files,0,"rules.json",root->{for(var d:root.getAsJsonArray())if(d.getAsJsonObject().has("category")&&d.getAsJsonObject().get("category").getAsString().equals("SAVING_THROW")){d.getAsJsonObject().remove("ability_key");break;}});assertThrows(Schema2Exception.class,()->Schema2GrantFixtures.artifacts(files));
    }

    @Test void ordinaryMeleeAcceptsAnotherOwnerAndFreezesOrderedDamageWithTheWholeReplacement() {
        var files = Schema2MonsterFixtures.packages();
        EntryIdentity club = new EntryIdentity(Schema2MonsterFixtures.COMMONER, "actions", "monster_base", "club");
        var target = Schema2GrantFixtures.json(files.get(2).get("actions.json")).getAsJsonArray().get(5)
                .getAsJsonObject().getAsJsonObject("identity");
        var original = meleeProfile();
        original.getAsJsonObject("attack_profile").getAsJsonArray("damage").remove(1);
        var replacement = meleeProfile();
        var damage = replacement.getAsJsonObject("attack_profile").getAsJsonArray("damage");
        damage.add(damage.remove(0));
        replacement.getAsJsonObject("attack_profile").addProperty("reach_feet", 10);
        Schema2GrantFixtures.edit(files, 2, "actions.json", root -> root.getAsJsonArray().get(5).getAsJsonObject()
                .add("action_profile", original));
        Schema2GrantFixtures.edit(files, 3, "changes.json", root -> {
            var change = new JsonObject();
            change.addProperty("op", "set_field");
            change.add("target", target);
            change.addProperty("field", "action_profile");
            change.add("value", replacement);
            root.getAsJsonArray().add(change);
        });
        var projection = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
        var action = projection.projection().entries().stream().map(FinalEntry::entry)
                .filter(MonsterActionEntry.class::isInstance).map(MonsterActionEntry.class::cast)
                .filter(entry -> entry.identity().equals(club)).findFirst().orElseThrow().actionProfile();
        assertEquals(MonsterActionMode.MELEE_ATTACK_V1, action.mode());
        assertEquals(MONSTER_MELEE_ATTACK_MECHANISM, action.mechanism());
        assertEquals(new MonsterAttackProfile(2, 10, List.of(new MonsterAttackDamage(DamageType.FIRE, 1, 6, 1),
                new MonsterAttackDamage(DamageType.BLUDGEONING, 1, 4, -1))), action.attackProfile());
        assertEquals(replacement, Schema2GrantFixtures.json(ProductionSchema2Codec.canonical(Schema2MonsterCodec.actionProfileJson(action))));
        var chain = projection.projection().chains().stream()
                .filter(row -> row.target().equals(club) && row.field() == Field.ACTION_PROFILE).findFirst().orElseThrow();
        assertEquals(2, chain.steps().size());
        var declaration = ((MonsterActionProfileValue) chain.steps().getFirst().after().value()).value();
        assertEquals(new MonsterAttackProfile(2, 5, List.of(new MonsterAttackDamage(DamageType.BLUDGEONING, 1, 4, -1))),
                declaration.attackProfile());
        assertEquals(chain.steps().getFirst().after(), chain.steps().getLast().before());
        assertEquals(new MonsterActionProfileValue(action), chain.steps().getLast().after().value());

        var restored = ProductionSchema2Projection.restore(projection.members(), projection.projection().rules(),
                projection.projection().entries(), projection.projection().chains());
        assertArrayEquals(projection.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(projection.chainsJson(), restored.chainsJson());
        var malformed = new MonsterActionProfile(action.displayName(), action.description(), action.sourcePage(),
                action.mode(), action.mechanism(), new MonsterSavePolicy("ability.dexterity", "FIXED_DC", 12, "FULL", "HALF"),
                null, null, null, action.attackProfile());
        var altered = projection.projection().entries().stream().map(row -> row.entry().identity().equals(club)
                ? new FinalEntry(new MonsterActionEntry(club, malformed, null), row.enabled()) : row).toList();
        assertThrows(IllegalArgumentException.class, () -> ProductionSchema2Projection.restore(projection.members(),
                projection.projection().rules(), altered, projection.projection().chains()));
    }

    @Test void ordinaryMeleeRejectsIncompleteMixedDuplicateAndOutOfBoundsAtoms() {
        rejectMelee(profile -> profile.remove("attack_profile"));
        rejectMelee(profile -> profile.addProperty("mechanism", "SCRIPT"));
        rejectMelee(profile -> profile.add("save_policy", new JsonObject()));
        rejectMelee(profile -> profile.addProperty("mode", "BLOCKED_METADATA"));
        rejectMelee(profile -> profile.getAsJsonObject("attack_profile").addProperty("hit_bonus", 21));
        rejectMelee(profile -> profile.getAsJsonObject("attack_profile").addProperty("reach_feet", 6));
        rejectMelee(profile -> profile.getAsJsonObject("attack_profile").add("damage", new JsonArray()));
        rejectMelee(profile -> {
            var damage = profile.getAsJsonObject("attack_profile").getAsJsonArray("damage");
            damage.add(damage.get(0).deepCopy());
        });
        rejectMelee(profile -> firstDamage(profile).addProperty("damage_type", "FIRE"));
        rejectMelee(profile -> firstDamage(profile).addProperty("damage_type", "UNKNOWN"));
        rejectMelee(profile -> firstDamage(profile).addProperty("dice_count", 11));
        rejectMelee(profile -> firstDamage(profile).addProperty("dice_sides", 5));
        rejectMelee(profile -> firstDamage(profile).addProperty("modifier", -11));
        rejectMelee(profile -> firstDamage(profile).remove("modifier"));
        rejectMelee(profile -> firstDamage(profile).addProperty("delivery", "SCRIPT"));
    }

    private static JsonObject meleeProfile() {
        return JsonParser.parseString("""
                {
                  "display_name": "Club",
                  "description": "Finite monster attack fixture.",
                  "source_page": 398,
                  "mode": "MELEE_ATTACK_V1",
                  "mechanism": "MONSTER_MELEE_ATTACK_V1",
                  "attack_profile": {
                    "hit_bonus": 2,
                    "reach_feet": 5,
                    "damage": [
                      {"damage_type": "BLUDGEONING", "dice_count": 1, "dice_sides": 4, "modifier": -1},
                      {"damage_type": "FIRE", "dice_count": 1, "dice_sides": 6, "modifier": 1}
                    ]
                  }
                }
                """).getAsJsonObject();
    }

    private static JsonObject firstDamage(JsonObject profile) {
        return profile.getAsJsonObject("attack_profile").getAsJsonArray("damage").get(0).getAsJsonObject();
    }

    private static void rejectMelee(java.util.function.Consumer<JsonObject> edit) {
        var profile = meleeProfile();
        edit.accept(profile);
        reject("actions.json", root -> root.getAsJsonArray().get(5).getAsJsonObject().add("action_profile", profile));
    }

    private static void reject(String path,java.util.function.Consumer<JsonElement> edit){var files=Schema2MonsterFixtures.packages();Schema2GrantFixtures.edit(files,2,path,edit);assertThrows(Schema2Exception.class,()->ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)),path);}
}
