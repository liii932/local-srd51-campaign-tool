package com.dndtool.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;

/** Typed growth/rest/check material rows and atomic prerequisite children, in the caller's snapshot transaction. */
final class JdbcSchema2GrowthSnapshotRows {
    private JdbcSchema2GrowthSnapshotRows() { }
    static boolean supports(FinalRule row) { return row instanceof FinalClassProgression || row instanceof FinalResource || row instanceof FinalSubclass || row instanceof FinalFeature || row instanceof FinalFeat || row instanceof FinalProficiency || row instanceof FinalProficiencySet || row instanceof FinalRest || row instanceof FinalCheck; }
    static void storeRule(Connection c, Identity id, int index, FinalRule row) throws SQLException {
        RuleIdentity key = row.identity(); String table, columns; List<Object> values = new ArrayList<>(List.of(id.snapshotBytes(), index, ascii(key.moduleKey()), ascii(key.ruleKey())));
        if (row instanceof FinalProficiencySet set) {
            table = "proficiency_set"; columns = "display_name, category, minimum_enabled_members, author_note, enabled";
            Collections.addAll(values, set.definition().displayName(), ascii(set.definition().category().name()), set.definition().minimumEnabledMembers(), set.definition().authorNote(), bit(row.enabled()));
        } else {
            String name, description, note; int page;
            if (row instanceof FinalClassProgression v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else if (row instanceof FinalResource v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else if (row instanceof FinalSubclass v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else if (row instanceof FinalFeature v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else if (row instanceof FinalFeat v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else if (row instanceof FinalProficiency v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else if (row instanceof FinalCheck v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else if (row instanceof FinalRest v) { name=v.definition().displayName(); description=v.definition().description(); page=v.definition().sourcePage(); note=v.definition().authorNote(); }
            else throw invalid();
            Collections.addAll(values, name, description, page);
            String specific;
            if (row instanceof FinalClassProgression v) {
                var d=v.definition(); table="class_progression"; specific="owner_class_module, owner_class_type, owner_class_key, hit_die_sides, proficiency_bonus_profile, supported_maximum_level, advancement_scope, prerequisite_groups";
                values.addAll(reference(d.ownerClass())); Collections.addAll(values,d.hitDieSides(),d.proficiencyBonusProfile(),d.supportedMaximumLevel(),ascii(d.advancementScope().name()),d.multiclassPrerequisite()==null?null:d.multiclassPrerequisite().allOf().size());
            } else if (row instanceof FinalResource v) {
                var d=v.definition(); table="resource"; specific="owner_class_module, owner_class_type, owner_class_key, maximum_profile, recovery_profile, execution_mode";
                values.addAll(reference(d.ownerClass())); Collections.addAll(values,d.maximumProfile(),d.recoveryProfile(),ascii(d.executionMode().name()));
            } else if (row instanceof FinalSubclass v) { var d=v.definition(); table="subclass"; specific="owner_class_module, owner_class_type, owner_class_key, selection_level"; values.addAll(reference(d.ownerClass())); values.add(d.selectionLevel()); }
            else if (row instanceof FinalFeature v) { var d=v.definition(); table="feature"; specific="owner_module, owner_type, owner_key, feature_level, feature_kind, execution_mode, execution_algorithm"; values.addAll(reference(d.owner())); Collections.addAll(values,d.level(),ascii(d.kind().name()),ascii(d.executionMode().name()),ascii(d.executionAlgorithm())); }
            else if (row instanceof FinalFeat v) { var d=v.definition(); table="feat"; specific="prerequisite_groups, execution_mode, execution_algorithm"; Collections.addAll(values,d.prerequisite().allOf().size(),ascii(d.executionMode().name()),ascii(d.executionAlgorithm())); }
            else if (row instanceof FinalProficiency v) { var d=v.definition(); table="proficiency"; specific="category, ability_key"; Collections.addAll(values,ascii(d.category().name()),d.abilityKey()==null?null:ascii(d.abilityKey())); }
            else if (row instanceof FinalCheck v) { var d=v.definition(); table="check"; specific="algorithm"; values.add(ascii(d.algorithm())); }
            else { var d=((FinalRest)row).definition(); table="rest"; specific="algorithm"; values.add(ascii(d.algorithm())); }
            columns="display_name, description, source_page, "+specific+", author_note, enabled"; Collections.addAll(values,note,bit(row.enabled()));
        }
        insert(c,"s2_runtime_"+table,"snapshot_id, rule_index, module_key, rule_key, "+columns,values.toArray());
        AbilityPrerequisite predicate=row instanceof FinalClassProgression v?v.definition().multiclassPrerequisite():row instanceof FinalFeat v?v.definition().prerequisite():null;
        if(predicate!=null) storePredicate(c,id,index,-1,null,predicate);
    }
    static Map<Integer, FinalRule> readRules(Connection c, Identity id) throws SQLException {
        var predicates=readPredicates(c,id,false); Map<Integer,FinalRule> result=new TreeMap<>();
        for(String type:List.of("class_progression","resource","subclass","feature","feat","proficiency","proficiency_set","rest","check")) {
            rows(c,id,"s2_runtime_"+type,"rule_index",256,r->{
                int index=number(r,"rule_index"); RuleIdentity key=new RuleIdentity(ascii(r,"module_key",64),type.equals("proficiency_set")?"catalog.proficiency_set":type.equals("rest")?"adventure.rest":type.equals("check")?"adventure.check":"character."+type,ascii(r,"rule_key",128));
                String name=text(r,"display_name",120),note=nullableText(r,"author_note",1000); boolean enabled=bool(r,"enabled"); FinalRule rule;
                if(type.equals("proficiency_set")) rule=new FinalProficiencySet(new ProficiencySetDefinition(key,name,ProficiencyCategory.valueOf(ascii(r,"category",16)),number(r,"minimum_enabled_members"),note),enabled);
                else {
                    String description=text(r,"description",1000); int page=number(r,"source_page");
                    rule=switch(type) {
                        case "class_progression" -> new FinalClassProgression(new ClassProgressionDefinition(key,name,description,page,reference(r,"owner_class"),number(r,"hit_die_sides"),text(r,"proficiency_bonus_profile",1000),number(r,"supported_maximum_level"),ProgressionScope.valueOf(ascii(r,"advancement_scope",40)),r.getObject("prerequisite_groups")==null?null:predicates.take(index,-1,null,number(r,"prerequisite_groups")),note),enabled);
                        case "resource" -> new FinalResource(new ResourceDefinition(key,name,description,page,reference(r,"owner_class"),text(r,"maximum_profile",1000),text(r,"recovery_profile",200),ResourceExecution.valueOf(ascii(r,"execution_mode",16)),note),enabled);
                        case "subclass" -> new FinalSubclass(new SubclassDefinition(key,name,description,page,reference(r,"owner_class"),number(r,"selection_level"),note),enabled);
                        case "feature" -> new FinalFeature(new FeatureDefinition(key,name,description,page,reference(r,"owner"),number(r,"feature_level"),FeatureKind.valueOf(ascii(r,"feature_kind",16)),FeatureExecution.valueOf(ascii(r,"execution_mode",20)),ascii(r,"execution_algorithm",128),note),enabled);
                        case "feat" -> new FinalFeat(new FeatDefinition(key,name,description,page,predicates.take(index,-1,null,number(r,"prerequisite_groups")),FeatureExecution.valueOf(ascii(r,"execution_mode",20)),ascii(r,"execution_algorithm",128),note),enabled);
                        case "proficiency" -> new FinalProficiency(new ProficiencyDefinition(key,name,description,page,ProficiencyCategory.valueOf(ascii(r,"category",16)),r.getObject("ability_key")==null?null:ascii(r,"ability_key",32),note),enabled);
                        case "check" -> new FinalCheck(new CheckDefinition(key,name,description,page,ascii(r,"algorithm",64),note),enabled);
                        case "rest" -> new FinalRest(new RestDefinition(key,name,description,page,ascii(r,"algorithm",64),note),enabled);
                        default -> throw invalid();
                    };
                }
                require(result.putIfAbsent(index,rule)==null); return true;
            });
        }
        predicates.complete(); return result;
    }
    static boolean storeEntry(Connection c, Identity id, int index, FinalEntry row) throws SQLException {
        Entry entry=row.entry(); if(!(entry instanceof ClassLevelEntry || entry instanceof ProficiencyMemberEntry || entry instanceof GrowthProficiencyGrantEntry))return false;
        EntryIdentity key=entry.identity(); List<Object> values=new ArrayList<>(List.of(id.snapshotBytes(),index,ascii(key.rule().moduleKey()),ascii(key.rule().ruleKey()),ascii(key.collectionKey()),ascii(key.moduleKey()),ascii(key.entryKey()))); String suffix,fields;
        if(entry instanceof ClassLevelEntry level){suffix="class_level_entry";fields="class_level, asi_available";Collections.addAll(values,level.level(),bit(level.asiAvailable()));}
        else if(entry instanceof ProficiencyMemberEntry member){suffix="proficiency_entry";fields="target_module, target_type, target_key";values.addAll(reference(member.proficiency()));}
        else {suffix="growth_grant";fields=GROWTH_COLUMNS;values.addAll(growth(((GrowthProficiencyGrantEntry)entry).grant()));}
        Collections.addAll(values,entry.authorNote(),bit(row.enabled()));
        insert(c,"s2_runtime_"+suffix,"snapshot_id, entry_index, rule_module_key, rule_key, collection_key, module_key, entry_key, "+fields+", author_note, enabled",values.toArray()); return true;
    }
    static Map<Integer,FinalEntry> readEntries(Connection c,Identity id)throws SQLException{
        Map<Integer,FinalEntry> result=new TreeMap<>();
        for(String type:List.of("class_level_entry","proficiency_entry","growth_grant"))rows(c,id,"s2_runtime_"+type,"entry_index",512,r->{
            RuleIdentity owner=new RuleIdentity(ascii(r,"rule_module_key",64),type.equals("proficiency_entry")?"catalog.proficiency_set":"character.class_progression",ascii(r,"rule_key",128));
            EntryIdentity key=new EntryIdentity(owner,ascii(r,"collection_key",32),ascii(r,"module_key",64),ascii(r,"entry_key",64)); String note=nullableText(r,"author_note",1000);
            Entry value=type.equals("class_level_entry")?new ClassLevelEntry(key,number(r,"class_level"),bool(r,"asi_available"),note):type.equals("proficiency_entry")?new ProficiencyMemberEntry(key,reference(r,"target"),note):new GrowthProficiencyGrantEntry(key,readGrowth(r,""),note);
            require(result.putIfAbsent(number(r,"entry_index"),new FinalEntry(value,bool(r,"enabled")))==null);return true;
        });return result;
    }
    static final String EXTRA_STATE="reference_rule_type, prerequisite_groups, growth_kind, growth_target_module, growth_target_type, growth_target_key, growth_count";
    static final String GROWTH_COLUMNS="growth_kind, growth_target_module, growth_target_type, growth_target_key, growth_count";
    static List<Object> extraState(FieldState state,Field field){
        FieldValue value=state.value(); List<Object> values=new ArrayList<>();
        values.add(value instanceof IdentityValue ref&&(field==Field.OWNER||field==Field.PROFICIENCY||field==Field.STEALTH_SKILL||field==Field.ITEM)?ascii(ref.value().ruleType()):null);
        values.add(value instanceof PrerequisiteValue predicate?predicate.value().allOf().size():null);
        values.addAll(value instanceof GrowthGrantValue grant?growth(grant.value()):Arrays.asList(null,null,null,null,null));return values;
    }
    static List<Object> growth(GrowthGrantSpec spec){RuleIdentity target=spec instanceof FixedGrowthGrant fixed?fixed.target():((ChoiceGrowthGrant)spec).candidates();return Arrays.asList(ascii(spec instanceof FixedGrowthGrant?"FIXED":"CHOICE"),ascii(target.moduleKey()),ascii(target.ruleType()),ascii(target.ruleKey()),spec instanceof ChoiceGrowthGrant choice?choice.count():null);}
    static GrowthGrantSpec readGrowth(ResultSet r,String prefix)throws SQLException{
        String kind=ascii(r,prefix+"growth_kind",6);RuleIdentity target=new RuleIdentity(ascii(r,prefix+"growth_target_module",64),ascii(r,prefix+"growth_target_type",32),ascii(r,prefix+"growth_target_key",128));
        if(kind.equals("FIXED")){require(r.getObject(prefix+"growth_count")==null);return new FixedGrowthGrant(target);}require(kind.equals("CHOICE"));return new ChoiceGrowthGrant(target,number(r,prefix+"growth_count"));
    }
    static void storeChainPredicate(Connection c,Identity id,int chain,int step,String phase,FieldState state)throws SQLException{if(state.value() instanceof PrerequisiteValue predicate)storePredicate(c,id,chain,step,phase,predicate.value());}
    private static void storePredicate(Connection c,Identity id,int index,int step,String phase,AbilityPrerequisite value)throws SQLException{
        for(int group=0;group<value.allOf().size();group++)for(int term=0;term<value.allOf().get(group).anyOf().size();term++){
            AbilityMinimum minimum=value.allOf().get(group).anyOf().get(term);
            if(phase==null)insert(c,"s2_runtime_rule_prerequisite","snapshot_id, rule_index, group_index, term_index, ability_key, minimum_value",id.snapshotBytes(),index,group,term,ascii(minimum.abilityKey()),minimum.minimum());
            else insert(c,"s2_runtime_chain_prerequisite","snapshot_id, chain_index, step_index, phase, group_index, term_index, ability_key, minimum_value",id.snapshotBytes(),index,step,ascii(phase),group,term,ascii(minimum.abilityKey()),minimum.minimum());
        }
    }
    static Predicates readPredicates(Connection c,Identity id,boolean chain)throws SQLException{
        Predicates predicates=new Predicates();
        rows(c,id,chain?"s2_runtime_chain_prerequisite":"s2_runtime_rule_prerequisite",chain?"chain_index, step_index, phase, group_index, term_index":"rule_index, group_index, term_index",chain?4096*12:256*6,r->{
            PredicateKey key=new PredicateKey(number(r,chain?"chain_index":"rule_index"),chain?number(r,"step_index"):-1,chain?ascii(r,"phase",6):null);
            predicates.rows.computeIfAbsent(key,ignored->new ArrayList<>()).add(new Term(number(r,"group_index"),number(r,"term_index"),new AbilityMinimum(ascii(r,"ability_key",32),number(r,"minimum_value"))));return true;
        });return predicates;
    }
    static final class Predicates{
        final Map<PredicateKey,List<Term>> rows=new HashMap<>();
        AbilityPrerequisite take(int index,int step,String phase,int count)throws SQLException{
            require(count>=0&&count<=6);List<Term> terms=rows.remove(new PredicateKey(index,step,phase));if(terms==null)terms=List.of();
            List<RequirementGroup> groups=new ArrayList<>();int cursor=0;
            for(int group=0;group<count;group++){
                List<AbilityMinimum> minimums=new ArrayList<>();
                while(cursor<terms.size()&&terms.get(cursor).group==group){Term term=terms.get(cursor++);require(term.index==minimums.size()&&minimums.size()<2);minimums.add(term.minimum);}
                require(!minimums.isEmpty());groups.add(new RequirementGroup(minimums));
            }
            require(cursor==terms.size());return new AbilityPrerequisite(groups);
        }
        void complete()throws SQLException{require(rows.isEmpty());}
    }
    private record PredicateKey(int index,int step,String phase){}
    private record Term(int group,int index,AbilityMinimum minimum){}
    private static List<Object> reference(RuleIdentity id){return List.of(ascii(id.moduleKey()),ascii(id.ruleType()),ascii(id.ruleKey()));}
    private static RuleIdentity reference(ResultSet r,String prefix)throws SQLException{return new RuleIdentity(ascii(r,prefix+"_module",64),ascii(r,prefix+"_type",32),ascii(r,prefix+"_key",128));}
    private static int bit(boolean value){return value?1:0;}
    private static boolean bool(ResultSet r,String column)throws SQLException{int value=number(r,column);require(value<=1);return value==1;}
    private static int number(ResultSet r,String column)throws SQLException{Object value=r.getObject(column);require(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long);long number=((Number)value).longValue();require(number>=0&&number<=Integer.MAX_VALUE);return(int)number;}
    private static byte[] ascii(String value){return value.getBytes(StandardCharsets.US_ASCII);}
    private static String ascii(ResultSet r,String column,int maximum)throws SQLException{Object value=r.getObject(column);require(value instanceof byte[]);byte[] bytes=(byte[])value;require(bytes.length>=1&&bytes.length<=maximum);for(byte b:bytes)require(b>=0x21&&b<=0x7e);return new String(bytes,StandardCharsets.US_ASCII);}
    private static String text(ResultSet r,String column,int maximum)throws SQLException{Object value=r.getObject(column);require(value instanceof String);String text=(String)value;require(!text.isEmpty()&&text.codePointCount(0,text.length())<=maximum);return text;}
    private static String nullableText(ResultSet r,String column,int maximum)throws SQLException{return r.getObject(column)==null?null:text(r,column,maximum);}
    private static void insert(Connection c,String table,String columns,Object...values)throws SQLException{try(PreparedStatement s=c.prepareStatement("INSERT INTO "+table+" ("+columns+") VALUES ("+String.join(", ",Collections.nCopies(values.length,"?"))+")")){s.setQueryTimeout(5);for(int i=0;i<values.length;i++){Object value=values[i];if(value==null)s.setNull(i+1,Types.NULL);else if(value instanceof byte[] bytes)s.setBytes(i+1,bytes);else if(value instanceof String text)s.setString(i+1,text);else if(value instanceof Integer n)s.setInt(i+1,n);else throw invalid();}require(s.executeUpdate()==1);}}
    private static <T> List<T> rows(Connection c,Identity id,String table,String order,int maximum,Reader<T> mapper)throws SQLException{try(PreparedStatement s=c.prepareStatement("SELECT * FROM "+table+" WHERE snapshot_id = ? ORDER BY "+order+" LIMIT "+(maximum+1))){s.setQueryTimeout(5);s.setMaxRows(maximum+1);s.setBytes(1,id.snapshotBytes());try(ResultSet r=s.executeQuery()){List<T> result=new ArrayList<>();while(r.next()){Object storedId=r.getObject("snapshot_id");require(result.size()<maximum&&storedId instanceof byte[]&&Arrays.equals(id.snapshotBytes(),(byte[])storedId));try{result.add(mapper.read(r));}catch(IllegalArgumentException|NullPointerException malformed){throw new SQLException("S2_GROWTH_SNAPSHOT_CORRUPT",malformed);}}return result;}}}
    private static void require(boolean value)throws SQLException{if(!value)throw invalid();}
    private static SQLException invalid(){return new SQLException("S2_GROWTH_SNAPSHOT_CORRUPT");}
    @FunctionalInterface private interface Reader<T>{T read(ResultSet r)throws SQLException;}
}
