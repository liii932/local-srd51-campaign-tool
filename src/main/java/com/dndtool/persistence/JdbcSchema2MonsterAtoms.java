package com.dndtool.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Explicit typed scalar and ordinal-child projections shared by source and runtime mirrors. */
final class JdbcSchema2MonsterAtoms {
    static final String STAT="size, creature_type, alignment, armor_class, armor_description, hit_points, hit_dice_count, hit_dice_sides, hit_dice_modifier, walk_speed_feet, strength, dexterity, constitution, intelligence, wisdom, charisma, darkvision_feet, blindsight_feet, tremorsense_feet, truesight_feet, passive_perception, challenge_numerator, challenge_denominator, experience_points";
    static final String ACTION="display_name, description, source_page, mode, mechanism, save_ability_key, save_dc_mode, save_dc, fail_outcome, success_outcome, damage_dice_count, damage_dice_sides, damage_type, damage_delivery, damage_ability_modifier, area_shape, area_length_feet, area_origin_policy, recharge_dice_sides, recharge_minimum_roll, recharge_turn_start, recharge_short_rest, recharge_long_rest";
    static final Map<String,String> COLUMNS;
    static {var m=new LinkedHashMap<String,String>();m.put("monster_stat",STAT);m.put("monster_save","ordinal, ability_key, bonus");m.put("monster_skill","ordinal, skill_key, bonus");m.put("monster_defense","ordinal, kind, damage_type, qualifier");m.put("monster_condition","ordinal, condition_key");m.put("monster_language","ordinal, name, understands, speaks");m.put("monster_trait","ordinal, trait_key, trait_scope, display_name, description");m.put("monster_action_value",ACTION);COLUMNS=Collections.unmodifiableMap(m);}
    private JdbcSchema2MonsterAtoms() { }
    record Row(String table,String columns,List<Object> values) { }
    static List<Row> rows(String prefix,String keyColumns,List<Object> key,FieldValue atom) {
        List<Row> rows=new ArrayList<>();
        if(atom instanceof MonsterStatBlockValue value) {
            var d=value.value();var a=d.abilities();
            add(rows,prefix,keyColumns,key,"monster_stat",STAT,ascii(d.size().name()),ascii(d.creatureType().name()),d.alignment(),d.armorClass(),d.armorDescription(),d.hitPoints(),d.hitDiceCount(),d.hitDiceSides(),d.hitDiceModifier(),d.walkSpeedFeet(),a.strength(),a.dexterity(),a.constitution(),a.intelligence(),a.wisdom(),a.charisma(),d.darkvisionFeet(),d.blindsightFeet(),d.tremorsenseFeet(),d.truesightFeet(),d.passivePerception(),d.challengeNumerator(),d.challengeDenominator(),d.experiencePoints());
            for(int i=0;i<d.savingThrows().size();i++){var v=d.savingThrows().get(i);add(rows,prefix,keyColumns,key,"monster_save",COLUMNS.get("monster_save"),i,ascii(v.abilityKey()),v.bonus());}
            for(int i=0;i<d.skills().size();i++){var v=d.skills().get(i);add(rows,prefix,keyColumns,key,"monster_skill",COLUMNS.get("monster_skill"),i,ascii(v.skillKey()),v.bonus());}
            for(int i=0;i<d.damageDefenses().size();i++){var v=d.damageDefenses().get(i);add(rows,prefix,keyColumns,key,"monster_defense",COLUMNS.get("monster_defense"),i,ascii(v.kind().name()),ascii(v.damageType().name()),ascii(v.qualifier().name()));}
            for(int i=0;i<d.conditionImmunities().size();i++)add(rows,prefix,keyColumns,key,"monster_condition",COLUMNS.get("monster_condition"),i,ascii(d.conditionImmunities().get(i)));
            for(int i=0;i<d.languages().size();i++){var v=d.languages().get(i);add(rows,prefix,keyColumns,key,"monster_language",COLUMNS.get("monster_language"),i,v.name(),bit(v.understands()),bit(v.speaks()));}
            for(int i=0;i<d.traits().size();i++){var v=d.traits().get(i);add(rows,prefix,keyColumns,key,"monster_trait",COLUMNS.get("monster_trait"),i,ascii(v.key()),ascii(v.scope().name()),v.displayName(),v.description());}
        } else if(atom instanceof MonsterActionProfileValue value) {
            var d=value.value();var s=d.savePolicy();var damage=d.damageProfile();var area=d.area();var recharge=d.recharge();
            add(rows,prefix,keyColumns,key,"monster_action_value",ACTION,d.displayName(),d.description(),d.sourcePage(),ascii(d.mode().name()),nullable(d.mechanism()),s==null?null:ascii(s.abilityKey()),s==null?null:ascii(s.dcMode()),s==null?null:s.dc(),s==null?null:ascii(s.failOutcome()),s==null?null:ascii(s.successOutcome()),damage==null?null:damage.diceCount(),damage==null?null:damage.diceSides(),damage==null?null:ascii(damage.damageType().name()),damage==null?null:ascii(damage.delivery()),damage==null?null:ascii(damage.abilityModifier()),area==null?null:ascii(area.shape()),area==null?null:area.lengthFeet(),area==null?null:ascii(area.originPolicy()),recharge==null?null:recharge.diceSides(),recharge==null?null:recharge.minimumRoll(),recharge==null?null:bit(recharge.turnStart()),recharge==null?null:bit(recharge.shortRest()),recharge==null?null:bit(recharge.longRest()));
        } else throw new IllegalArgumentException("Unsupported monster atom");
        return rows;
    }
    private static void add(List<Row> out,String prefix,String columns,List<Object> key,String suffix,String fields,Object... data){var values=new ArrayList<>(key);Collections.addAll(values,data);out.add(new Row(prefix+suffix,columns+", "+fields,values));}
    static void store(Connection c,Row row)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO "+row.table()+" ("+row.columns()+") VALUES ("+String.join(", ",Collections.nCopies(row.values().size(),"?"))+")")){s.setQueryTimeout(5);bind(s,row.values());require(s.executeUpdate()==1);}
    }
    static void bind(PreparedStatement s,List<?> values)throws SQLException{for(int i=0;i<values.size();i++){Object v=values.get(i);if(v instanceof byte[] b)s.setBytes(i+1,b);else if(v instanceof String t)s.setString(i+1,t);else if(v instanceof Integer n)s.setInt(i+1,n);else if(v==null)s.setNull(i+1,Types.NULL);else throw invalid();}}
    static Map<String,Object> readRow(ResultSet r,String columns)throws SQLException {var row=new LinkedHashMap<String,Object>();for(String column:columns.split(", "))row.put(column,r.getObject(column));return row;}
    static MonsterActionProfile action(Map<String,Object> r)throws SQLException {
        var mode=MonsterActionMode.valueOf(ascii(r,"mode"));String name=text(r,"display_name"),description=text(r,"description");int page=number(r,"source_page");
        if(mode==MonsterActionMode.BLOCKED_METADATA){boolean extras=false;for(String k:ACTION.split(", "))if(!Set.of("display_name","description","source_page","mode").contains(k))extras|=r.get(k)!=null;require(!extras);return new MonsterActionProfile(name,description,page,mode,null,null,null,null,null);}
        return new MonsterActionProfile(name,description,page,mode,ascii(r,"mechanism"),new MonsterSavePolicy(ascii(r,"save_ability_key"),ascii(r,"save_dc_mode"),number(r,"save_dc"),ascii(r,"fail_outcome"),ascii(r,"success_outcome")),new MonsterDamageProfile(number(r,"damage_dice_count"),number(r,"damage_dice_sides"),DamageType.valueOf(ascii(r,"damage_type")),ascii(r,"damage_delivery"),ascii(r,"damage_ability_modifier")),new MonsterArea(ascii(r,"area_shape"),number(r,"area_length_feet"),ascii(r,"area_origin_policy")),new MonsterRecharge(number(r,"recharge_dice_sides"),number(r,"recharge_minimum_roll"),bool(r,"recharge_turn_start"),bool(r,"recharge_short_rest"),bool(r,"recharge_long_rest")));
    }
    static MonsterStatBlock stat(Map<String,Object> r,Map<String,List<Map<String,Object>>> children)throws SQLException {
        List<MonsterSaveBonus> saves=new ArrayList<>();for(var v:children.get("monster_save"))saves.add(new MonsterSaveBonus(ascii(v,"ability_key"),number(v,"bonus")));
        List<MonsterSkillBonus> skills=new ArrayList<>();for(var v:children.get("monster_skill"))skills.add(new MonsterSkillBonus(ascii(v,"skill_key"),number(v,"bonus")));
        List<MonsterDamageDefense> defenses=new ArrayList<>();for(var v:children.get("monster_defense"))defenses.add(new MonsterDamageDefense(MonsterDefenseKind.valueOf(ascii(v,"kind")),DamageType.valueOf(ascii(v,"damage_type")),MonsterDefenseQualifier.valueOf(ascii(v,"qualifier"))));
        List<String> conditions=new ArrayList<>();for(var v:children.get("monster_condition"))conditions.add(ascii(v,"condition_key"));
        List<MonsterLanguage> languages=new ArrayList<>();for(var v:children.get("monster_language"))languages.add(new MonsterLanguage(text(v,"name"),bool(v,"understands"),bool(v,"speaks")));
        List<MonsterTrait> traits=new ArrayList<>();for(var v:children.get("monster_trait"))traits.add(new MonsterTrait(ascii(v,"trait_key"),MonsterTraitScope.valueOf(ascii(v,"trait_scope")),text(v,"display_name"),text(v,"description")));
        return new MonsterStatBlock(MonsterSize.valueOf(ascii(r,"size")),MonsterCreatureType.valueOf(ascii(r,"creature_type")),text(r,"alignment"),number(r,"armor_class"),text(r,"armor_description"),number(r,"hit_points"),number(r,"hit_dice_count"),number(r,"hit_dice_sides"),number(r,"hit_dice_modifier"),number(r,"walk_speed_feet"),new MonsterAbilities(number(r,"strength"),number(r,"dexterity"),number(r,"constitution"),number(r,"intelligence"),number(r,"wisdom"),number(r,"charisma")),saves,skills,defenses,conditions,number(r,"darkvision_feet"),number(r,"blindsight_feet"),number(r,"tremorsense_feet"),number(r,"truesight_feet"),number(r,"passive_perception"),languages,number(r,"challenge_numerator"),number(r,"challenge_denominator"),number(r,"experience_points"),traits);
    }
    static int number(Map<String,Object> r,String k)throws SQLException{Object v=r.get(k);require(v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long);long n=((Number)v).longValue();require(n>=Integer.MIN_VALUE&&n<=Integer.MAX_VALUE);return(int)n;}
    static boolean bool(Map<String,Object> r,String k)throws SQLException{int n=number(r,k);require(n==0||n==1);return n==1;}
    static String ascii(Map<String,Object> r,String k)throws SQLException{Object v=r.get(k);require(v instanceof byte[]);byte[] b=(byte[])v;require(b.length>0&&b.length<=128);for(byte c:b)require(c>=0x21&&c<=0x7e);return new String(b,StandardCharsets.US_ASCII);}
    static String text(Map<String,Object> r,String k)throws SQLException{Object v=r.get(k);require(v instanceof String);return(String)v;}
    static byte[] ascii(String v){return v.getBytes(StandardCharsets.US_ASCII);}
    private static byte[] nullable(String v){return v==null?null:ascii(v);}
    private static int bit(boolean v){return v?1:0;}
    static void require(boolean b)throws SQLException{if(!b)throw invalid();}
    static SQLException invalid(){return new SQLException("S2_MONSTER_MATERIAL_CORRUPT");}
}
