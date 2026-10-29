package com.dndtool.module.composition;

import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Closed stat blocks and action atoms. Metadata never grants an executable trait or attack. */
final class Schema2MonsterCodec {
    private Schema2MonsterCodec() { }
    private static final Set<String> ABILITIES=Set.of("ability.strength","ability.dexterity","ability.constitution","ability.intelligence","ability.wisdom","ability.charisma");
    private static final Set<String> SKILLS=Set.of("skill.acrobatics","skill.animal_handling","skill.arcana","skill.athletics","skill.deception","skill.history","skill.insight","skill.intimidation","skill.investigation","skill.medicine","skill.nature","skill.perception","skill.performance","skill.persuasion","skill.religion","skill.sleight_of_hand","skill.stealth","skill.survival");
    private static final Set<String> CONDITIONS=Set.of("blinded","charmed","deafened","exhaustion","frightened","grappled","incapacitated","invisible","paralyzed","petrified","poisoned","prone","restrained","stunned","unconscious");
    static MonsterStatBlock statBlock(Object input,Location s) {
        var r=shape(input,Set.of("size","creature_type","alignment","armor_class","armor_description","hit_points","hit_dice_count","hit_dice_sides","hit_dice_modifier","walk_speed_feet","abilities","saving_throws","skills","damage_defenses","condition_immunities","darkvision_feet","blindsight_feet","tremorsense_feet","truesight_feet","passive_perception","languages","challenge_numerator","challenge_denominator","experience_points","traits"),Set.of(),s);
        var a=shape(r.get("abilities"),Set.of("strength","dexterity","constitution","intelligence","wisdom","charisma"),Set.of(),s.child("abilities"));
        var abilities=new MonsterAbilities(n(a,"strength",1,30,s),n(a,"dexterity",1,30,s),n(a,"constitution",1,30,s),n(a,"intelligence",1,30,s),n(a,"wisdom",1,30,s),n(a,"charisma",1,30,s));
        List<MonsterSaveBonus> saves=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(var x:bounded(r,"saving_throws",6,s)) {var v=shape(x,Set.of("ability_key","bonus"),Set.of(),s.child("saving_throws"));String k=member(v.get("ability_key"),ABILITIES,s);unique(seen,k,s);saves.add(new MonsterSaveBonus(k,n(v,"bonus",-5,30,s)));}
        List<MonsterSkillBonus> skills=new ArrayList<>();seen.clear();
        for(var x:bounded(r,"skills",18,s)) {var v=shape(x,Set.of("skill_key","bonus"),Set.of(),s.child("skills"));String k=member(v.get("skill_key"),SKILLS,s);unique(seen,k,s);skills.add(new MonsterSkillBonus(k,n(v,"bonus",-5,30,s)));}
        List<MonsterDamageDefense> defenses=new ArrayList<>();seen.clear();
        for(var x:bounded(r,"damage_defenses",39,s)) {var v=shape(x,Set.of("kind","damage_type","qualifier"),Set.of(),s.child("damage_defenses"));var kind=enumValue(MonsterDefenseKind.class,v.get("kind"),s);var type=enumValue(DamageType.class,v.get("damage_type"),s);var qualifier=enumValue(MonsterDefenseQualifier.class,v.get("qualifier"),s);unique(seen,kind+":"+type+":"+qualifier,s);if(qualifier==MonsterDefenseQualifier.NONMAGICAL_ATTACKS && (kind!=MonsterDefenseKind.RESISTANCE || !Set.of(DamageType.BLUDGEONING,DamageType.PIERCING,DamageType.SLASHING).contains(type)))throw bad(s);defenses.add(new MonsterDamageDefense(kind,type,qualifier));}
        List<String> conditions=new ArrayList<>();seen.clear();
        for(var x:bounded(r,"condition_immunities",15,s)){String k=member(x,CONDITIONS,s);unique(seen,k,s);conditions.add(k);}
        List<MonsterLanguage> languages=new ArrayList<>();seen.clear();
        for(var x:bounded(r,"languages",16,s)){var v=shape(x,Set.of("name","understands","speaks"),Set.of(),s.child("languages"));String k=text(v.get("name"),120,s);unique(seen,k,s);boolean understands=booleanAtom(v.get("understands"),s),speaks=booleanAtom(v.get("speaks"),s);if(!understands)throw bad(s);languages.add(new MonsterLanguage(k,understands,speaks));}
        List<MonsterTrait> traits=new ArrayList<>();seen.clear();
        for(var x:bounded(r,"traits",16,s)){var v=shape(x,Set.of("key","scope","display_name","description"),Set.of(),s.child("traits"));String k=identifier(v.get("key"),"[a-z][a-z0-9_]{0,63}",s);unique(seen,k,s);traits.add(new MonsterTrait(k,enumValue(MonsterTraitScope.class,v.get("scope"),s),text(v.get("display_name"),120,s),text(v.get("description"),1000,s)));}
        int diceSides=n(r,"hit_dice_sides",4,20,s);if(!Set.of(4,6,8,10,12,20).contains(diceSides))throw bad(s);
        int numerator=n(r,"challenge_numerator",0,30,s),denominator=n(r,"challenge_denominator",1,8,s);
        if(denominator!=1 && (numerator!=1 || !Set.of(2,4,8).contains(denominator)))throw bad(s);
        var size=enumValue(MonsterSize.class,r.get("size"),s);
        if(diceSides!=switch(size){case TINY->4;case SMALL->6;case MEDIUM->8;case LARGE->10;case HUGE->12;case GARGANTUAN->20;})throw bad(s);
        int count=n(r,"hit_dice_count",1,1000,s),modifier=n(r,"hit_dice_modifier",-5000,10000,s),hp=n(r,"hit_points",1,100000,s);
        if(modifier!=count*Math.floorDiv(abilities.constitution()-10,2) || hp!=Math.max(1,Math.floorDiv(count*(diceSides+1),2)+modifier))throw bad(s);
        return new MonsterStatBlock(size,enumValue(MonsterCreatureType.class,r.get("creature_type"),s),text(r.get("alignment"),120,s),n(r,"armor_class",1,30,s),text(r.get("armor_description"),120,s),hp,count,diceSides,modifier,n(r,"walk_speed_feet",0,240,s),abilities,saves,skills,defenses,conditions,n(r,"darkvision_feet",0,360,s),n(r,"blindsight_feet",0,360,s),n(r,"tremorsense_feet",0,360,s),n(r,"truesight_feet",0,360,s),n(r,"passive_perception",1,40,s),languages,numerator,denominator,n(r,"experience_points",0,200000,s),traits);
    }
    static MonsterActionProfile actionProfile(Object input,Location s) {
        var initial=object(input,s);var mode=enumValue(MonsterActionMode.class,required(initial,"mode",s),s.child("mode"));
        Set<String> required=new HashSet<>(Set.of("display_name","description","source_page","mode"));
        if(mode==MonsterActionMode.SAVE_DAMAGE_BATCH_V1)required.addAll(Set.of("mechanism","save_policy","damage_profile","area","recharge"));
        var r=shape(input,required,Set.of(),s);String name=text(r.get("display_name"),120,s),description=text(r.get("description"),1000,s);int page=n(r,"source_page",258,403,s);
        if(mode==MonsterActionMode.BLOCKED_METADATA)return new MonsterActionProfile(name,description,page,mode,null,null,null,null,null);
        exact(r.get("mechanism"),"HELL_HOUND_FIRE_BREATH_V1",s);
        var save=shape(r.get("save_policy"),Set.of("ability_key","dc_mode","dc","fail_outcome","success_outcome"),Set.of(),s.child("save_policy"));
        exact(save.get("ability_key"),"ability.dexterity",s);exact(save.get("dc_mode"),"FIXED_DC",s);int dc=n(save,"dc",12,12,s);exact(save.get("fail_outcome"),"FULL",s);exact(save.get("success_outcome"),"HALF",s);
        var damage=shape(r.get("damage_profile"),Set.of("dice_count","dice_sides","damage_type","delivery","ability_modifier"),Set.of(),s.child("damage_profile"));
        n(damage,"dice_count",6,6,s);n(damage,"dice_sides",6,6,s);exact(damage.get("damage_type"),"FIRE",s);exact(damage.get("delivery"),"OTHER_DELIVERY",s);exact(damage.get("ability_modifier"),"NO_ABILITY_MODIFIER",s);
        var area=shape(r.get("area"),Set.of("shape","length_feet","origin_policy"),Set.of(),s.child("area"));exact(area.get("shape"),"CONE",s);n(area,"length_feet",15,15,s);exact(area.get("origin_policy"),"EXCLUDE_ORIGIN",s);
        var recharge=shape(r.get("recharge"),Set.of("dice_sides","minimum_roll","turn_start","short_rest","long_rest"),Set.of(),s.child("recharge"));n(recharge,"dice_sides",6,6,s);n(recharge,"minimum_roll",5,5,s);
        for(String k:List.of("turn_start","short_rest","long_rest"))if(!booleanAtom(recharge.get(k),s))throw bad(s);
        return new MonsterActionProfile(name,description,page,mode,"HELL_HOUND_FIRE_BREATH_V1",new MonsterSavePolicy("ability.dexterity","FIXED_DC",dc,"FULL","HALF"),new MonsterDamageProfile(6,6,DamageType.FIRE,"OTHER_DELIVERY","NO_ABILITY_MODIFIER"),new MonsterArea("CONE",15,"EXCLUDE_ORIGIN"),new MonsterRecharge(6,5,true,true,true));
    }
    static Map<String,Object> statBlockJson(MonsterStatBlock d) {
        var a=d.abilities();
        return json("size",d.size().name(),"creature_type",d.creatureType().name(),"alignment",d.alignment(),"armor_class",d.armorClass(),"armor_description",d.armorDescription(),"hit_points",d.hitPoints(),"hit_dice_count",d.hitDiceCount(),"hit_dice_sides",d.hitDiceSides(),"hit_dice_modifier",d.hitDiceModifier(),"walk_speed_feet",d.walkSpeedFeet(),"abilities",json("strength",a.strength(),"dexterity",a.dexterity(),"constitution",a.constitution(),"intelligence",a.intelligence(),"wisdom",a.wisdom(),"charisma",a.charisma()),"saving_throws",d.savingThrows().stream().map(v->json("ability_key",v.abilityKey(),"bonus",v.bonus())).toList(),"skills",d.skills().stream().map(v->json("skill_key",v.skillKey(),"bonus",v.bonus())).toList(),"damage_defenses",d.damageDefenses().stream().map(v->json("kind",v.kind().name(),"damage_type",v.damageType().name(),"qualifier",v.qualifier().name())).toList(),"condition_immunities",d.conditionImmunities(),"darkvision_feet",d.darkvisionFeet(),"blindsight_feet",d.blindsightFeet(),"tremorsense_feet",d.tremorsenseFeet(),"truesight_feet",d.truesightFeet(),"passive_perception",d.passivePerception(),"languages",d.languages().stream().map(v->json("name",v.name(),"understands",v.understands(),"speaks",v.speaks())).toList(),"challenge_numerator",d.challengeNumerator(),"challenge_denominator",d.challengeDenominator(),"experience_points",d.experiencePoints(),"traits",d.traits().stream().map(v->json("key",v.key(),"scope",v.scope().name(),"display_name",v.displayName(),"description",v.description())).toList());
    }
    static Map<String,Object> actionProfileJson(MonsterActionProfile d) {
        var r=json("display_name",d.displayName(),"description",d.description(),"source_page",d.sourcePage(),"mode",d.mode().name());
        if(d.mode()==MonsterActionMode.BLOCKED_METADATA){if(d.mechanism()!=null||d.savePolicy()!=null||d.damageProfile()!=null||d.area()!=null||d.recharge()!=null)throw new IllegalArgumentException("Blocked monster metadata has executable fields");return r;}
        var save=d.savePolicy();var damage=d.damageProfile();var area=d.area();var recharge=d.recharge();
        r.put("mechanism",d.mechanism());r.put("save_policy",json("ability_key",save.abilityKey(),"dc_mode",save.dcMode(),"dc",save.dc(),"fail_outcome",save.failOutcome(),"success_outcome",save.successOutcome()));r.put("damage_profile",json("dice_count",damage.diceCount(),"dice_sides",damage.diceSides(),"damage_type",damage.damageType().name(),"delivery",damage.delivery(),"ability_modifier",damage.abilityModifier()));r.put("area",json("shape",area.shape(),"length_feet",area.lengthFeet(),"origin_policy",area.originPolicy()));r.put("recharge",json("dice_sides",recharge.diceSides(),"minimum_roll",recharge.minimumRoll(),"turn_start",recharge.turnStart(),"short_rest",recharge.shortRest(),"long_rest",recharge.longRest()));return r;
    }
    private static List<?> bounded(Map<String,Object> row,String k,int max,Location s){var rows=array(row.get(k),s.child(k));if(rows.size()>max)throw bad(s.child(k));return rows;}
    private static int n(Map<String,Object> row,String k,int min,int max,Location s){return integer(row.get(k),min,max,s.child(k));}
    private static String member(Object raw,Set<String> accepted,Location s){String k=string(raw,s);if(!accepted.contains(k))throw bad(s);return k;}
    private static void unique(Set<String> seen,String key,Location s){if(!seen.add(key))throw bad(s);}
    private static void exact(Object raw,String expected,Location s){if(!string(raw,s).equals(expected))throw bad(s);}
    private static Schema2Exception bad(Location s){return fail("S2_TYPE",Stage.AUTHOR,"Unsupported or inconsistent monster atom",s);}
}
