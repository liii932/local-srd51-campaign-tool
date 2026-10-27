package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Original Wizard learning catalogs and fixed DM environments, compared to every source ZIP. */
final class JdbcSchema2WizardSourceRows {
    private static final String PACKAGE="artifact_module_key, release_version, author_index";
    private static final String RULE="module_key, rule_type, rule_key";
    private static final String COMMON=PACKAGE+", "+RULE+", display_name, description, source_page";
    private static final String WIZARD=COMMON+", owner_class_module_key, owner_class_rule_type, owner_class_rule_key, required_feature_module_key, required_feature_rule_type, required_feature_rule_key, slot_resource_module_key, slot_resource_rule_type, slot_resource_rule_key, rank2_slot_resource_module_key, rank2_slot_resource_rule_type, rank2_slot_resource_rule_key, rank3_slot_resource_module_key, rank3_slot_resource_rule_type, rank3_slot_resource_rule_key, ability_key, algorithm, author_note";
    private static final String ENVIRONMENT=COMMON+", algorithm, author_note";
    private static final String MEMBER=PACKAGE+", rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, spell_module_key, spell_rule_type, spell_rule_key, author_note";
    private static final String CHANGE=PACKAGE+", target_kind, rule_module_key, rule_type, rule_key, collection_key, entry_module_key, entry_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key";
    private static final Map<String,String> TABLES=Map.of("rule_schema2_wizard_spellcasting",WIZARD,"rule_schema2_environment",ENVIRONMENT,"rule_schema2_spellbook_member",MEMBER,"rule_schema2_wizard_change",CHANGE);
    private JdbcSchema2WizardSourceRows() { }
    static void store(Connection connection,ProductionRuleArtifact artifact)throws SQLException {
        for(Row row:expected(artifact))try(var statement=connection.prepareStatement("INSERT INTO "+row.table+" ("+row.columns+") VALUES ("+String.join(", ",Collections.nCopies(row.values.size(),"?"))+")")) {
            statement.setQueryTimeout(5);
            for(int i=0;i<row.values.size();i++) {
                Object v=row.values.get(i);
                if(v instanceof byte[] b)statement.setBytes(i+1,b);
                else if(v instanceof String t)statement.setString(i+1,t);
                else if(v instanceof Integer n)statement.setInt(i+1,n);
                else if(v==null)statement.setNull(i+1,Types.NULL);
                else throw invalid();
            }
            require(statement.executeUpdate()==1);
        }
    }
    static void verifyAll(Connection connection,Collection<ProductionRuleArtifact> artifacts)throws SQLException {
        List<Row> wanted=new ArrayList<>(),actual=new ArrayList<>();
        for(var artifact:artifacts)wanted.addAll(expected(artifact));
        for(String table:TABLES.keySet().stream().sorted().toList()) {
            String columns=TABLES.get(table);int maximum=Schema2SourceLedger.MAX_ARTIFACTS*(table.endsWith("_change")?4096:table.endsWith("_member")?512:256);
            try(var statement=connection.prepareStatement("SELECT "+columns+" FROM "+table+" ORDER BY artifact_module_key, release_version, author_index")) {
                statement.setQueryTimeout(5);statement.setMaxRows(maximum+1);
                try(var rows=statement.executeQuery()) {
                    int count=0;
                    while(rows.next()) {
                        require(count++<maximum);List<Object> values=new ArrayList<>();
                        for(int i=0;i<columns.split(", ").length;i++)values.add(rows.getObject(i+1));
                        actual.add(new Row(table,columns,values));
                    }
                }
            }
        }
        wanted.sort(ORDER);actual.sort(ORDER);require(wanted.size()==actual.size());
        for(int i=0;i<wanted.size();i++) {
            Row expected=wanted.get(i),found=actual.get(i);require(expected.table.equals(found.table)&&expected.values.size()==found.values.size());
            for(int j=0;j<expected.values.size();j++)require(same(expected.values.get(j),found.values.get(j)));
        }
    }
    private static List<Row> expected(ProductionRuleArtifact artifact)throws SQLException {
        var author=artifact.authorPackage();List<Row> rows=new ArrayList<>();
        for(int i=0;i<author.definitions().size();i++) {
            Definition d=author.definitions().get(i);
            if(!(d instanceof WizardSpellcastingDefinition)&&!(d instanceof EnvironmentDefinition))continue;
            List<Object> values=prefix(artifact,author.definitionAuthorIndex(i));reference(values,d.identity());values.add(d.displayName());String table;
            if(d instanceof WizardSpellcastingDefinition w) {
                table="rule_schema2_wizard_spellcasting";Collections.addAll(values,w.description(),w.sourcePage());
                for(RuleIdentity ref:List.of(w.ownerClass(),w.requiredFeature(),w.slotResource(),w.rank2SlotResource(),w.rank3SlotResource()))reference(values,ref);
                Collections.addAll(values,ascii(w.abilityKey()),ascii(w.algorithm()),w.authorNote());
            }else {
                var e=(EnvironmentDefinition)d;table="rule_schema2_environment";Collections.addAll(values,e.description(),e.sourcePage(),ascii(e.algorithm()),e.authorNote());
            }
            rows.add(new Row(table,TABLES.get(table),values));
        }
        for(int i=0;i<author.entries().size();i++)if(author.entries().get(i) instanceof SpellbookEntry entry) {
            List<Object> values=prefix(artifact,author.entryAuthorIndex(i));var key=entry.identity();reference(values,key.rule());
            Collections.addAll(values,ascii(key.collectionKey()),ascii(key.moduleKey()),ascii(key.entryKey()));reference(values,entry.spell());values.add(entry.authorNote());
            rows.add(new Row("rule_schema2_spellbook_member",MEMBER,values));
        }
        for(int i=0;i<author.changes().size();i++) {
            Change change=author.changes().get(i);Identity target=change.target();RuleIdentity owner=target instanceof RuleIdentity r?r:((EntryIdentity)target).rule();
            if(!Set.of("character.wizard_spellcasting","adventure.environment").contains(owner.ruleType()))continue;
            List<Object> values=prefix(artifact,author.changeAuthorIndex(i));values.add(ascii(target instanceof RuleIdentity?"rule":"entry"));reference(values,owner);
            if(target instanceof EntryIdentity entry)Collections.addAll(values,ascii(entry.collectionKey()),ascii(entry.moduleKey()),ascii(entry.entryKey()));else Collections.addAll(values,null,null,null);
            Collections.addAll(values,ascii(change instanceof SetField?"set_field":change instanceof ClearField?"clear_field":"set_enabled"),ascii(change.field().key()));
            FieldValue value=change instanceof SetField set?set.value():change instanceof SetEnabled enabled?new BooleanValue(enabled.value()):null;
            Collections.addAll(values,value instanceof TextValue text?text.value():null,value instanceof IntegerValue n?n.value():null,value instanceof BooleanValue b?b.value()?1:0:null);
            reference(values,value instanceof IdentityValue ref?ref.value():null);rows.add(new Row("rule_schema2_wizard_change",CHANGE,values));
        }
        return rows;
    }
    private static List<Object> prefix(ProductionRuleArtifact a,int index){return new ArrayList<>(List.of(ascii(a.moduleKey()),ascii(a.releaseVersion()),index));}
    private static void reference(List<Object> v,RuleIdentity r){Collections.addAll(v,r==null?null:ascii(r.moduleKey()),r==null?null:ascii(r.ruleType()),r==null?null:ascii(r.ruleKey()));}
    private static final Comparator<Row> ORDER=Comparator.comparing((Row r)->r.table).thenComparing(r->key(r.values.get(0))).thenComparing(r->key(r.values.get(1))).thenComparingInt(r->r.values.get(2) instanceof Number n?n.intValue():-1);
    private static String key(Object v){return v instanceof byte[] b?new String(b,StandardCharsets.ISO_8859_1):"";}
    private static boolean same(Object a,Object b){if(a instanceof byte[] bytes)return b instanceof byte[] found&&Arrays.equals(bytes,found);if(a instanceof Integer n)return (b instanceof Byte||b instanceof Short||b instanceof Integer||b instanceof Long)&&n.longValue()==((Number)b).longValue();return Objects.equals(a,b);}
    private static byte[] ascii(String v){return v.getBytes(StandardCharsets.US_ASCII);}
    private static void require(boolean valid)throws SQLException{if(!valid)throw invalid();}
    private static SQLException invalid(){return new SQLException("S2_WIZARD_SOURCE_CORRUPT");}
    private record Row(String table,String columns,List<Object> values) { }
}
