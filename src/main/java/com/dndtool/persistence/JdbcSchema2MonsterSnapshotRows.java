package com.dndtool.persistence;

import java.sql.*;
import java.util.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.persistence.JdbcSchema2MonsterAtoms.*;

/** Caller-owned typed monster mirrors; all rule, entry and chain atoms are consumed exactly once. */
final class JdbcSchema2MonsterSnapshotRows {
    private static final String KEY="snapshot_id, owner_kind, owner_index, step_index";
    private JdbcSchema2MonsterSnapshotRows() { }
    static boolean supports(FinalRule rule){return rule instanceof FinalMonster;}
    static void store(Connection c,Identity id,int index,FinalRule row)throws SQLException {
        var d=((FinalMonster)row).definition();store(c,new Row("s2_runtime_monster","snapshot_id, rule_index, module_key, rule_key, display_name, description, source_page, author_note, enabled",Arrays.asList(id.snapshotBytes(),index,ascii(d.identity().moduleKey()),ascii(d.identity().ruleKey()),d.displayName(),d.description(),d.sourcePage(),d.authorNote(),row.enabled()?1:0)));
        storeAtom(c,id,"RULE",index,-1,new MonsterStatBlockValue(d.statBlock()));
    }
    static boolean storeEntry(Connection c,Identity id,int index,FinalEntry row)throws SQLException {
        if(!(row.entry() instanceof MonsterActionEntry d))return false;var k=d.identity();
        store(c,new Row("s2_runtime_monster_action","snapshot_id, entry_index, rule_module_key, rule_key, collection_key, module_key, entry_key, author_note, enabled",Arrays.asList(id.snapshotBytes(),index,ascii(k.rule().moduleKey()),ascii(k.rule().ruleKey()),ascii(k.collectionKey()),ascii(k.moduleKey()),ascii(k.entryKey()),d.authorNote(),row.enabled()?1:0)));
        storeAtom(c,id,"ENTRY",index,-1,new MonsterActionProfileValue(d.actionProfile()));return true;
    }
    static void storeChain(Connection c,Identity id,int chain,int step,String phase,FieldState state)throws SQLException {
        if(state.value() instanceof MonsterStatBlockValue||state.value() instanceof MonsterActionProfileValue)storeAtom(c,id,phase,chain,step,state.value());
    }
    private static void storeAtom(Connection c,Identity id,String kind,int index,int step,FieldValue value)throws SQLException {for(var row:rows("s2_runtime_",KEY,List.of(id.snapshotBytes(),ascii(kind),index,step),value))store(c,row);}
    private static void store(Connection c,Row row)throws SQLException {JdbcSchema2MonsterAtoms.store(c,row);}
    static Map<Integer,FinalRule> readRules(Connection c,Identity id,Atoms atoms)throws SQLException {
        Map<Integer,FinalRule> result=new TreeMap<>();for(var r:read(c,id,"s2_runtime_monster","rule_index",256)){
            int i=number(r,"rule_index");var key=new RuleIdentity(ascii(r,"module_key"),"adventure.monster",ascii(r,"rule_key"));
            require(result.putIfAbsent(i,new FinalMonster(new MonsterDefinition(key,text(r,"display_name"),text(r,"description"),number(r,"source_page"),((MonsterStatBlockValue)atoms.take("RULE",i,-1,Field.STAT_BLOCK)).value(),note(r)),bool(r,"enabled")))==null);
        }return result;
    }
    static Map<Integer,FinalEntry> readEntries(Connection c,Identity id,Atoms atoms)throws SQLException {
        Map<Integer,FinalEntry> result=new TreeMap<>();for(var r:read(c,id,"s2_runtime_monster_action","entry_index",512)){
            int i=number(r,"entry_index");var key=new EntryIdentity(new RuleIdentity(ascii(r,"rule_module_key"),"adventure.monster",ascii(r,"rule_key")),ascii(r,"collection_key"),ascii(r,"module_key"),ascii(r,"entry_key"));
            require(result.putIfAbsent(i,new FinalEntry(new MonsterActionEntry(key,((MonsterActionProfileValue)atoms.take("ENTRY",i,-1,Field.ACTION_PROFILE)).value(),note(r)),bool(r,"enabled")))==null);
        }return result;
    }
    static Atoms readAtoms(Connection c,Identity id)throws SQLException {
        Map<AtomKey,FieldValue> values=new HashMap<>();Map<String,Map<AtomKey,List<Map<String,Object>>>> children=new HashMap<>();
        for(String table:COLUMNS.keySet())if(!Set.of("monster_stat","monster_action_value").contains(table)){
            var groups=new HashMap<AtomKey,List<Map<String,Object>>>();int bound=table.equals("monster_defense")?39:table.equals("monster_skill")?18:table.equals("monster_condition")?15:table.equals("monster_save")?6:16;
            for(var r:read(c,id,"s2_runtime_"+table,"owner_kind, owner_index, step_index, ordinal",(4096*2+768)*bound)){
                AtomKey key=key(r);require(!key.kind.equals("ENTRY"));var group=groups.computeIfAbsent(key,ignored->new ArrayList<>());require(number(r,"ordinal")==group.size()&&group.size()<bound);group.add(r);
            }children.put(table,groups);
        }
        for(var r:read(c,id,"s2_runtime_monster_stat","owner_kind, owner_index, step_index",4096*2+256)){
            AtomKey key=key(r);require(!key.kind.equals("ENTRY"));var childRows=new HashMap<String,List<Map<String,Object>>>();
            for(var table:children.entrySet()){var group=table.getValue().remove(key);childRows.put(table.getKey(),group==null?List.of():group);}
            require(values.putIfAbsent(key,new MonsterStatBlockValue(stat(r,childRows)))==null);
        }
        for(var group:children.values())require(group.isEmpty());
        for(var r:read(c,id,"s2_runtime_monster_action_value","owner_kind, owner_index, step_index",4096*2+512)){AtomKey key=key(r);require(!key.kind.equals("RULE"));require(values.putIfAbsent(key,new MonsterActionProfileValue(action(r)))==null);}
        return new Atoms(values);
    }
    static final class Atoms {
        private final Map<AtomKey,FieldValue> values;
        private Atoms(Map<AtomKey,FieldValue> values){this.values=values;}
        FieldValue take(String kind,int index,int step,Field field)throws SQLException {var v=values.remove(new AtomKey(kind,index,step));require(field==Field.STAT_BLOCK?v instanceof MonsterStatBlockValue:field==Field.ACTION_PROFILE&&v instanceof MonsterActionProfileValue);return v;}
        void complete()throws SQLException{require(values.isEmpty());}
    }
    private record AtomKey(String kind,int index,int step) { }
    private static AtomKey key(Map<String,Object> r)throws SQLException {String kind=ascii(r,"owner_kind");int index=number(r,"owner_index"),step=number(r,"step_index");if(kind.equals("RULE"))require(index>=0&&index<256&&step==-1);else if(kind.equals("ENTRY"))require(index>=0&&index<512&&step==-1);else require(Set.of("BEFORE","AFTER").contains(kind)&&index>=0&&index<3072&&step>=0&&step<4);return new AtomKey(kind,index,step);}
    private static String note(Map<String,Object> row)throws SQLException{return row.get("author_note")==null?null:text(row,"author_note");}
    private static List<Map<String,Object>> read(Connection c,Identity id,String table,String order,int max)throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM "+table+" WHERE snapshot_id = ? ORDER BY "+order+" LIMIT "+(max+1))){s.setQueryTimeout(5);s.setMaxRows(max+1);s.setBytes(1,id.snapshotBytes());try(var r=s.executeQuery()){List<Map<String,Object>> result=new ArrayList<>();String columns=table.equals("s2_runtime_monster")?"snapshot_id, rule_index, module_key, rule_key, display_name, description, source_page, author_note, enabled":table.equals("s2_runtime_monster_action")?"snapshot_id, entry_index, rule_module_key, rule_key, collection_key, module_key, entry_key, author_note, enabled":KEY+", "+COLUMNS.get(table.substring("s2_runtime_".length()));while(r.next()){require(result.size()<max);var row=readRow(r,columns);require(row.get("snapshot_id") instanceof byte[]&&Arrays.equals(id.snapshotBytes(),(byte[])row.get("snapshot_id")));result.add(row);}return result;}}
    }
}
