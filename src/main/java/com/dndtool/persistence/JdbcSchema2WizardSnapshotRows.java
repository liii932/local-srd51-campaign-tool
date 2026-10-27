package com.dndtool.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Frozen Wizard learning catalogs and fixed environments in the caller-owned snapshot transaction. */
final class JdbcSchema2WizardSnapshotRows {
    private JdbcSchema2WizardSnapshotRows() { }
    static boolean supports(FinalRule row){return row instanceof FinalWizardSpellcasting||row instanceof FinalEnvironment;}
    static void store(Connection connection,Identity id,int index,FinalRule row)throws SQLException {
        List<Object> values=new ArrayList<>(List.of(id.snapshotBytes(),index,ascii(row.identity().moduleKey()),ascii(row.identity().ruleKey()),row.definition().displayName()));String table,columns;
        if(row instanceof FinalWizardSpellcasting wizard) {
            var d=wizard.definition();table="wizard_spellcasting";columns="owner_class_module_key, owner_class_rule_type, owner_class_rule_key, required_feature_module_key, required_feature_rule_type, required_feature_rule_key, slot_resource_module_key, slot_resource_rule_type, slot_resource_rule_key, rank2_slot_resource_module_key, rank2_slot_resource_rule_type, rank2_slot_resource_rule_key, rank3_slot_resource_module_key, rank3_slot_resource_rule_type, rank3_slot_resource_rule_key, ability_key, algorithm";
            Collections.addAll(values,d.description(),d.sourcePage());for(RuleIdentity ref:List.of(d.ownerClass(),d.requiredFeature(),d.slotResource(),d.rank2SlotResource(),d.rank3SlotResource()))reference(values,ref);Collections.addAll(values,ascii(d.abilityKey()),ascii(d.algorithm()));
        }else if(row instanceof FinalEnvironment environment) {
            var d=environment.definition();table="environment";columns="algorithm";Collections.addAll(values,d.description(),d.sourcePage(),ascii(d.algorithm()));
        }else throw invalid();
        Collections.addAll(values,row.definition().authorNote(),row.enabled()?1:0);
        insert(connection,"s2_runtime_"+table,"snapshot_id, rule_index, module_key, rule_key, display_name, description, source_page, "+columns+", author_note, enabled",values);
    }
    static boolean storeEntry(Connection connection,Identity id,int index,FinalEntry row)throws SQLException {
        if(!(row.entry() instanceof SpellbookEntry entry))return false;
        var k=entry.identity();List<Object> values=new ArrayList<>(List.of(id.snapshotBytes(),index,ascii(k.rule().moduleKey()),ascii(k.rule().ruleKey()),ascii(k.collectionKey()),ascii(k.moduleKey()),ascii(k.entryKey())));
        reference(values,entry.spell());Collections.addAll(values,entry.authorNote(),row.enabled()?1:0);
        insert(connection,"s2_runtime_spellbook_member","snapshot_id, entry_index, rule_module_key, rule_key, collection_key, module_key, entry_key, spell_module_key, spell_rule_type, spell_rule_key, author_note, enabled",values);return true;
    }
    static Map<Integer,FinalRule> read(Connection connection,Identity id)throws SQLException {
        Map<Integer,FinalRule> result=new TreeMap<>();
        for(String table:List.of("wizard_spellcasting","environment"))try(var statement=connection.prepareStatement("SELECT * FROM s2_runtime_"+table+" WHERE snapshot_id = ? ORDER BY rule_index LIMIT 257")) {
            statement.setQueryTimeout(5);statement.setMaxRows(257);statement.setBytes(1,id.snapshotBytes());
            try(var rows=statement.executeQuery()) {
                int count=0;
                while(rows.next()) {
                    require(count++<256);snapshot(rows,id);RuleIdentity key=new RuleIdentity(ascii(rows,"module_key",64),table.equals("environment")?"adventure.environment":"character.wizard_spellcasting",ascii(rows,"rule_key",128));
                    String name=text(rows,"display_name",120),description=text(rows,"description",1000),note=nullableText(rows,"author_note",1000),algorithm=ascii(rows,"algorithm",64);int page=number(rows,"source_page",0,1000),index=number(rows,"rule_index",0,255);boolean enabled=bool(rows,"enabled");FinalRule value;
                    try {
                        value=table.equals("environment")?new FinalEnvironment(new EnvironmentDefinition(key,name,description,page,algorithm,note),enabled)
                                :new FinalWizardSpellcasting(new WizardSpellcastingDefinition(key,name,description,page,reference(rows,"owner_class"),reference(rows,"required_feature"),reference(rows,"slot_resource"),reference(rows,"rank2_slot_resource"),reference(rows,"rank3_slot_resource"),ascii(rows,"ability_key",32),algorithm,note),enabled);
                    }catch(IllegalArgumentException|NullPointerException malformed){throw new SQLException("S2_WIZARD_SNAPSHOT_CORRUPT",malformed);}
                    require(result.putIfAbsent(index,value)==null);
                }
            }
        }
        return result;
    }
    static Map<Integer,FinalEntry> readEntries(Connection connection,Identity id)throws SQLException {
        Map<Integer,FinalEntry> result=new TreeMap<>();
        try(var statement=connection.prepareStatement("SELECT * FROM s2_runtime_spellbook_member WHERE snapshot_id = ? ORDER BY entry_index LIMIT 513")) {
            statement.setQueryTimeout(5);statement.setMaxRows(513);statement.setBytes(1,id.snapshotBytes());
            try(var rows=statement.executeQuery()) {
                int count=0;
                while(rows.next()) {
                    require(count++<512);snapshot(rows,id);RuleIdentity owner=new RuleIdentity(ascii(rows,"rule_module_key",64),"character.wizard_spellcasting",ascii(rows,"rule_key",128));
                    EntryIdentity key=new EntryIdentity(owner,ascii(rows,"collection_key",32),ascii(rows,"module_key",64),ascii(rows,"entry_key",64));
                    FinalEntry entry=new FinalEntry(new SpellbookEntry(key,reference(rows,"spell"),nullableText(rows,"author_note",1000)),bool(rows,"enabled"));
                    require(result.putIfAbsent(number(rows,"entry_index",0,511),entry)==null);
                }
            }
        }
        return result;
    }
    private static void snapshot(ResultSet rows,Identity id)throws SQLException{Object raw=rows.getObject("snapshot_id");require(raw instanceof byte[] bytes&&Arrays.equals(bytes,id.snapshotBytes()));}
    private static void insert(Connection c,String table,String columns,List<Object> values)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO "+table+" ("+columns+") VALUES ("+String.join(", ",Collections.nCopies(values.size(),"?"))+")")) {
            s.setQueryTimeout(5);
            for(int i=0;i<values.size();i++) {
                Object v=values.get(i);if(v instanceof byte[] b)s.setBytes(i+1,b);else if(v instanceof String t)s.setString(i+1,t);else if(v instanceof Integer n)s.setInt(i+1,n);else if(v==null)s.setNull(i+1,Types.NULL);else throw invalid();
            }
            require(s.executeUpdate()==1);
        }
    }
    private static void reference(List<Object> values,RuleIdentity r){Collections.addAll(values,ascii(r.moduleKey()),ascii(r.ruleType()),ascii(r.ruleKey()));}
    private static RuleIdentity reference(ResultSet r,String prefix)throws SQLException{return new RuleIdentity(ascii(r,prefix+"_module_key",64),ascii(r,prefix+"_rule_type",32),ascii(r,prefix+"_rule_key",128));}
    private static int number(ResultSet r,String column,int min,int max)throws SQLException{Object v=r.getObject(column);require(v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long);long n=((Number)v).longValue();require(n>=min&&n<=max);return(int)n;}
    private static boolean bool(ResultSet r,String column)throws SQLException{return number(r,column,0,1)==1;}
    private static String ascii(ResultSet r,String column,int max)throws SQLException{Object v=r.getObject(column);require(v instanceof byte[]);byte[] bytes=(byte[])v;require(bytes.length>0&&bytes.length<=max);for(byte b:bytes)require(b>=0x21&&b<=0x7e);return new String(bytes,StandardCharsets.US_ASCII);}
    private static String text(ResultSet r,String column,int max)throws SQLException{Object v=r.getObject(column);require(v instanceof String);String t=(String)v;require(!t.isEmpty()&&t.codePointCount(0,t.length())<=max);return t;}
    private static String nullableText(ResultSet r,String column,int max)throws SQLException{return r.getObject(column)==null?null:text(r,column,max);}
    private static byte[] ascii(String value){return value.getBytes(StandardCharsets.US_ASCII);}
    private static void require(boolean value)throws SQLException{if(!value)throw invalid();}
    private static SQLException invalid(){return new SQLException("S2_WIZARD_SNAPSHOT_CORRUPT");}
}
