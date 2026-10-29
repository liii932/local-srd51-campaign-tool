package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.persistence.JdbcSchema2MonsterAtoms.*;

/** Every original monster declaration, action and whole-atom override is verified against its ZIP. */
final class JdbcSchema2MonsterSourceRows {
    private static final String PACKAGE="artifact_module_key, release_version, author_index";
    private static final String RULE="rule_module_key, rule_type, rule_key";
    private static final String ENTRY=RULE+", collection_key, module_key, entry_key";
    private static final String ATOM="artifact_module_key, release_version, owner_kind, owner_index";
    static final Map<String,String> TABLES;
    static {var m=new LinkedHashMap<String,String>();m.put("rule_schema2_monster",PACKAGE+", "+RULE+", display_name, description, source_page, author_note");m.put("rule_schema2_monster_action",PACKAGE+", "+ENTRY+", author_note");m.put("rule_schema2_monster_change",PACKAGE+", target_kind, "+ENTRY+", operation, field_key, text_value, integer_value, boolean_value, atom_kind");COLUMNS.forEach((table,columns)->m.put("rule_schema2_"+table,ATOM+", "+columns));TABLES=Collections.unmodifiableMap(m);}
    private JdbcSchema2MonsterSourceRows() { }
    static void store(Connection c,ProductionRuleArtifact artifact)throws SQLException {for(var row:expected(artifact))JdbcSchema2MonsterAtoms.store(c,row);}
    static void verifyAll(Connection c,Collection<ProductionRuleArtifact> artifacts)throws SQLException {
        List<Row> expected=new ArrayList<>(),actual=new ArrayList<>();for(var artifact:artifacts)expected.addAll(expected(artifact));
        for(var entry:TABLES.entrySet()) {
            String table=entry.getKey(),columns=entry.getValue();int maximum=Schema2SourceLedger.MAX_ARTIFACTS*256*39;
            String order=columns.startsWith(ATOM)?"artifact_module_key, release_version, owner_kind, owner_index"+(columns.contains(", ordinal, ")?", ordinal":""):"artifact_module_key, release_version, author_index";
            try(var s=c.prepareStatement("SELECT "+columns+" FROM "+table+" ORDER BY "+order)){s.setQueryTimeout(5);s.setMaxRows(maximum+1);try(var rs=s.executeQuery()){int count=0;while(rs.next()){require(count++<maximum);List<Object> values=new ArrayList<>();for(int i=0;i<columns.split(", ").length;i++)values.add(rs.getObject(i+1));actual.add(new Row(table,columns,values));}}}
        }
        expected.sort(ORDER);actual.sort(ORDER);require(expected.size()==actual.size());for(int i=0;i<expected.size();i++){var e=expected.get(i);var a=actual.get(i);require(e.table().equals(a.table())&&e.values().size()==a.values().size());for(int j=0;j<e.values().size();j++)require(same(e.values().get(j),a.values().get(j)));}
    }
    private static List<Row> expected(ProductionRuleArtifact artifact) {
        List<Row> rows=new ArrayList<>();var author=artifact.authorPackage();
        for(int i=0;i<author.definitions().size();i++)if(author.definitions().get(i) instanceof MonsterDefinition d){int index=author.definitionAuthorIndex(i);var v=prefix(artifact,index);rule(v,d.identity());Collections.addAll(v,d.displayName(),d.description(),d.sourcePage(),d.authorNote());rows.add(new Row("rule_schema2_monster",TABLES.get("rule_schema2_monster"),v));rows.addAll(rows("rule_schema2_",ATOM,atomKey(artifact,"DEFINITION",index),new MonsterStatBlockValue(d.statBlock())));}
        for(int i=0;i<author.entries().size();i++)if(author.entries().get(i) instanceof MonsterActionEntry d){int index=author.entryAuthorIndex(i);var v=prefix(artifact,index);entry(v,d.identity());v.add(d.authorNote());rows.add(new Row("rule_schema2_monster_action",TABLES.get("rule_schema2_monster_action"),v));rows.addAll(rows("rule_schema2_",ATOM,atomKey(artifact,"ENTRY",index),new MonsterActionProfileValue(d.actionProfile())));}
        for(int i=0;i<author.changes().size();i++){
            var change=author.changes().get(i);RuleIdentity r=change.target() instanceof RuleIdentity rule?rule:((EntryIdentity)change.target()).rule();if(!r.ruleType().equals("adventure.monster"))continue;
            int index=author.changeAuthorIndex(i);var v=prefix(artifact,index);v.add(ascii(change.target() instanceof RuleIdentity?"rule":"entry"));rule(v,r);
            if(change.target() instanceof EntryIdentity id)Collections.addAll(v,ascii(id.collectionKey()),ascii(id.moduleKey()),ascii(id.entryKey()));else Collections.addAll(v,null,null,null);
            var value=change instanceof SetField set?set.value():change instanceof SetEnabled enabled?new BooleanValue(enabled.value()):null;
            Collections.addAll(v,ascii(change instanceof SetField?"set_field":change instanceof ClearField?"clear_field":"set_enabled"),ascii(change.field().key()),value instanceof TextValue text?text.value():null,value instanceof IntegerValue number?number.value():null,value instanceof BooleanValue bool?bool.value()?1:0:null,value instanceof MonsterStatBlockValue?ascii("STAT_BLOCK"):value instanceof MonsterActionProfileValue?ascii("ACTION_PROFILE"):null);
            rows.add(new Row("rule_schema2_monster_change",TABLES.get("rule_schema2_monster_change"),v));
            if(value instanceof MonsterStatBlockValue||value instanceof MonsterActionProfileValue)rows.addAll(rows("rule_schema2_",ATOM,atomKey(artifact,"CHANGE",index),value));
        }
        return rows;
    }
    private static List<Object> prefix(ProductionRuleArtifact a,int i){return new ArrayList<>(List.of(ascii(a.moduleKey()),ascii(a.releaseVersion()),i));}
    private static List<Object> atomKey(ProductionRuleArtifact a,String kind,int i){return List.of(ascii(a.moduleKey()),ascii(a.releaseVersion()),ascii(kind),i);}
    private static void rule(List<Object> v,RuleIdentity r){Collections.addAll(v,ascii(r.moduleKey()),ascii(r.ruleType()),ascii(r.ruleKey()));}
    private static void entry(List<Object> v,EntryIdentity d){rule(v,d.rule());Collections.addAll(v,ascii(d.collectionKey()),ascii(d.moduleKey()),ascii(d.entryKey()));}
    private static final Comparator<Row> ORDER=Comparator.comparing(Row::table).thenComparing(JdbcSchema2MonsterSourceRows::orderKey);
    private static String orderKey(Row r){StringBuilder b=new StringBuilder();int count=r.columns().startsWith(ATOM)?(r.columns().contains(", ordinal, ")?5:4):3;for(Object v:r.values().subList(0,count))b.append(v instanceof byte[] bytes?new String(bytes,StandardCharsets.ISO_8859_1):v instanceof Number n?String.format(Locale.ROOT,"%08d",n.intValue()):"INVALID").append('\0');return b.toString();}
    private static boolean same(Object e,Object a){if(e instanceof byte[] b)return a instanceof byte[] x&&Arrays.equals(b,x);if(e instanceof Integer n)return(a instanceof Byte||a instanceof Short||a instanceof Integer||a instanceof Long)&&n.longValue()==((Number)a).longValue();return Objects.equals(e,a);}
}
