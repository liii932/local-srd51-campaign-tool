package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CombatState.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

/** Explicit physical projection of monster and batch facts; no connections or transactions are owned here. */
final class JdbcSchema2MonsterBreathRows {
    private JdbcSchema2MonsterBreathRows() { }
    static final List<String> TABLES = List.of("s2_combat_monster_deployment", "s2_combat_breath_input",
            "s2_combat_monster", "s2_combat_monster_action", "s2_combat_save_damage_batch",
            "s2_combat_breath_candidate", "s2_combat_breath_target", "s2_combat_breath_die",
            "s2_combat_breath_check", "s2_combat_breath_packet", "s2_combat_breath_defense",
            "s2_combat_breath_concentration", "s2_combat_monster_rest_input", "s2_combat_monster_rest", "s2_combat_spell_damage_source");
    @FunctionalInterface interface Source { List<Map<String,Object>> rows(String table, String order, int maximum) throws SQLException; }
    record CommandParts(List<MonsterDeployment> deployments, BreathInput breath, MonsterRestInput rest) { }
    record StateParts(List<MonsterInstance> monsters, List<SaveDamageBatch> batches, List<MonsterRest> rests) { }

    static CommandParts readCommand(Source source) throws SQLException {
        List<MonsterDeployment> deployments = new ArrayList<>();
        for (var r : source.rows(TABLES.get(0), "deployment_index", 64)) {
            ordinal(r, "deployment_index", deployments.size());
            deployments.add(new MonsterDeployment(id(r,"monster_id"), rule(r,"monster"),
                    new Position(n(r,"x"), n(r,"y")), n(r,"side")));
        }
        var inputs = source.rows(TABLES.get(1), "input_index", 1);
        BreathInput input = null;
        if (!inputs.isEmpty()) { var r=inputs.getFirst(); ordinal(r,"input_index",0); input=new BreathInput(entry(r,"action"),n(r,"direction")); }
        var rests=source.rows(TABLES.get(12),"input_index",1);MonsterRestInput rest=null;
        if(!rests.isEmpty()){var r=rests.getFirst();ordinal(r,"input_index",0);rest=new MonsterRestInput(nullableId(r,"rest_key"),en(MonsterRestKind.class,r,"rest_kind"));}
        return new CommandParts(List.copyOf(deployments),input,rest);
    }

    static void commandRows(Map<String,List<Map<String,Object>>> out, UUID combat, long sequence, Command command) {
        for (int i=0;i<command.monsterDeployments().size();i++) {
            var d=command.monsterDeployments().get(i);
            var r=base(combat,sequence,"deployment_index",i,"monster_id",bytes(d.monsterId()),"x",d.position().x(),"y",d.position().y(),"side",d.side());
            rule(r,"monster",d.monster()); add(out,TABLES.get(0),r);
        }
        if (command.breathInput()!=null) {
            var r=base(combat,sequence,"input_index",0,"direction",command.breathInput().direction());
            entry(r,"action",command.breathInput().action()); add(out,TABLES.get(1),r);
        }
        if(command.monsterRestInput()!=null) {var r=command.monsterRestInput();add(out,TABLES.get(12),base(combat,sequence,"input_index",0,"rest_key",nullableBytes(r.restKey()),"rest_kind",ascii(r.kind())));}
    }

    static StateParts readState(Source source, int monsterCount, int batchCount, int restCount) throws SQLException {
        List<MonsterInstance> monsters=new ArrayList<>();
        var uses=source.rows(TABLES.get(3),"monster_index, action_index",1024);
        for (var r:source.rows(TABLES.get(2),"monster_index",64)) {
            int index=monsters.size(); ordinal(r,"monster_index",index); List<MonsterActionUse> actions=new ArrayList<>();
            for(var u:uses) if(n(u,"monster_index")==index) { ordinal(u,"action_index",actions.size()); actions.add(new MonsterActionUse(entry(u,"action"),bool(u,"available"))); }
            require(actions.size()==n(r,"action_count"));
            monsters.add(new MonsterInstance(id(r,"monster_id"),rule(r,"monster"),n(r,"hit_points"),bool(r,"dead"),actions));
        }
        List<SaveDamageBatch> batches=new ArrayList<>();
        var candidates=source.rows(TABLES.get(5),"batch_index, candidate_index",8192);
        var targets=source.rows(TABLES.get(6),"batch_index, target_index",8192);
        var dice=source.rows(TABLES.get(7),"batch_index, die_index",768);
        var checks=source.rows(TABLES.get(8),"batch_index, check_index",16384);
        var packets=source.rows(TABLES.get(9),"batch_index, packet_index",8192);
        var defenses=source.rows(TABLES.get(10),"batch_index, packet_index, defense_index",65536);
        var obligations=source.rows(TABLES.get(11),"batch_index, obligation_index",8192);
        var spellSources=source.rows(TABLES.get(14),"batch_index",128);
        for(var r:source.rows(TABLES.get(4),"batch_index",128)) {
            int index=batches.size(); ordinal(r,"batch_index",index);
            List<AreaCandidate> area=new ArrayList<>(); List<CreatureRef> selected=new ArrayList<>(); List<Integer> rolled=new ArrayList<>();
            List<BatchCheck> savedChecks=new ArrayList<>(); List<DamagePacket> savedPackets=new ArrayList<>(); List<ConcentrationDamageObligation> savedObligations=new ArrayList<>();
            for(var c:candidates) if(n(c,"batch_index")==index) {
                ordinal(c,"candidate_index",area.size()); area.add(new AreaCandidate(creature(c),new Position(n(c,"x"),n(c,"y")),bool(c,"included"),text(c,"reason")));
            }
            for(var t:targets) if(n(t,"batch_index")==index) { ordinal(t,"target_index",selected.size()); selected.add(creature(t)); }
            for(var d:dice) if(n(d,"batch_index")==index) { ordinal(d,"die_index",rolled.size()); rolled.add(n(d,"die_value")); }
            for(var c:checks) if(n(c,"batch_index")==index) {
                ordinal(c,"check_index",savedChecks.size());
                savedChecks.add(new BatchCheck(n(c,"target_ordinal"),text(c,"purpose"),text(c,"ability_key"),en(CheckMode.class,c,"check_mode"),
                        nullableInt(c,"first_d20"),nullableInt(c,"second_d20"),n(c,"ability_modifier"),n(c,"proficiency_bonus"),bool(c,"advantage"),bool(c,"disadvantage"),
                        nullableId(c,"blessing_effect_key"),nullableInt(c,"blessing_d4"),n(c,"difficulty_class"),n(c,"total"),bool(c,"success")));
            }
            for(var p:packets) if(n(p,"batch_index")==index) {
                int packet=savedPackets.size(); ordinal(p,"packet_index",packet); List<DefenseContribution> contributions=new ArrayList<>();
                for(var d:defenses) if(n(d,"batch_index")==index&&n(d,"packet_index")==packet) {
                    ordinal(d,"defense_index",contributions.size()); contributions.add(new DefenseContribution(rule(d,"source"),text(d,"defense_kind")));
                }
                require(contributions.size()==n(p,"defense_count"));
                savedPackets.add(new DamagePacket(id(p,"packet_key"),n(p,"target_ordinal"),id(p,"shared_roll_key"),n(p,"save_check_index"),
                        n(p,"before_hit_points"),n(p,"before_temporary_hit_points"),n(p,"maximum_hit_points"),contributions,n(p,"damage_amount"),
                        n(p,"absorbed_temporary_hit_points"),n(p,"after_hit_points"),n(p,"after_temporary_hit_points"),bool(p,"dead")));
            }
            for(var o:obligations) if(n(o,"batch_index")==index) {
                ordinal(o,"obligation_index",savedObligations.size()); savedObligations.add(new ConcentrationDamageObligation(n(o,"target_ordinal"),
                        nullableId(o,"holder_source_key"),id(o,"packet_key"),n(o,"damage_amount"),n(o,"difficulty_class"),en(ConcentrationDamageStatus.class,o,"obligation_status"),nullableInt(o,"check_index")));
            }
            require(area.size()==n(r,"candidate_count")&&selected.size()==n(r,"target_count")&&rolled.size()==n(r,"die_count")
                    &&savedChecks.size()==n(r,"check_count")&&savedPackets.size()==n(r,"packet_count")&&savedObligations.size()==n(r,"obligation_count"));
            UUID roll=nullableId(r,"shared_roll_key"); require(roll!=null||rolled.isEmpty()&&r.get("raw_total")==null&&r.get("effective_total")==null);
            SharedDamageRoll shared=roll==null?null:new SharedDamageRoll(roll,rolled,n(r,"raw_total"),n(r,"effective_total"));
            var proof=new TargetSetProof(text(r,"space_policy"),number(r,"roster_version"),new Position(n(r,"origin_x"),n(r,"origin_y")),n(r,"direction"),area,selected);
            var matches=spellSources.stream().filter(v->{try{return n(v,"batch_index")==index;}catch(SQLException failure){throw new IllegalArgumentException(failure);}}).toList();require(matches.size()<=1);
            SpellDamageSource spellSource=null;
            if(!matches.isEmpty()) {var v=matches.getFirst();spellSource=new SpellDamageSource(id(v,"cast_frame_key"),rule(v,"spell"),n(v,"slot_level"),n(v,"ability_modifier"),n(v,"proficiency_bonus"));}
            batches.add(new SaveDamageBatch(id(r,"batch_key"),id(r,"frame_key"),id(r,"source_id"),nullableEntry(r,"action"),id(r,"release_operation_id"),number(r,"released_turn_number"),
                    proof,en(BatchStatus.class,r,"batch_status"),shared,savedChecks,savedPackets,savedObligations,spellSource));
        }
        List<MonsterRest> rests=new ArrayList<>();
        for(var r:source.rows(TABLES.get(13),"rest_index",128)) {ordinal(r,"rest_index",rests.size());rests.add(new MonsterRest(id(r,"rest_key"),id(r,"monster_id"),en(MonsterRestKind.class,r,"rest_kind"),number(r,"started_at_second"),number(r,"completes_at_second"),en(MonsterRestStatus.class,r,"rest_status"),nullableId(r,"finished_operation_id")));}
        require(monsters.size()==monsterCount&&batches.size()==batchCount&&rests.size()==restCount);
        return new StateParts(List.copyOf(monsters),List.copyOf(batches),List.copyOf(rests));
    }

    static void stateRows(Map<String,List<Map<String,Object>>> out, UUID combat,long sequence,String phase,Battle state) {
        for(int i=0;i<state.monsterRests().size();i++) {var r=state.monsterRests().get(i);add(out,TABLES.get(13),phase(combat,sequence,phase,"rest_index",i,"rest_key",bytes(r.restKey()),"monster_id",bytes(r.monsterId()),"rest_kind",ascii(r.kind()),"started_at_second",r.startedAtSecond(),"completes_at_second",r.completesAtSecond(),"rest_status",ascii(r.status()),"finished_operation_id",nullableBytes(r.finishedOperationId())));}
        for(int i=0;i<state.monsters().size();i++) {
            var m=state.monsters().get(i); var r=phase(combat,sequence,phase,"monster_index",i,"monster_id",bytes(m.monsterId()),"hit_points",m.hitPoints(),"dead",bit(m.dead()),"action_count",m.actionUses().size());
            rule(r,"monster",m.monster()); add(out,TABLES.get(2),r);
            for(int ai=0;ai<m.actionUses().size();ai++) {var a=m.actionUses().get(ai);var row=phase(combat,sequence,phase,"monster_index",i,"action_index",ai,"available",bit(a.available()));entry(row,"action",a.action());add(out,TABLES.get(3),row);}
        }
        for(int bi=0;bi<state.saveDamageBatches().size();bi++) {
            var batch=state.saveDamageBatches().get(bi);var proof=batch.targetProof();var roll=batch.sharedRoll();
            var root=phase(combat,sequence,phase,"batch_index",bi,"batch_key",bytes(batch.batchKey()),"frame_key",bytes(batch.frameKey()),"source_id",bytes(batch.sourceId()),"release_operation_id",bytes(batch.releaseOperationId()),
                    "released_turn_number",batch.releasedTurnNumber(),"space_policy",ascii(proof.policy()),"roster_version",proof.rosterVersion(),"origin_x",proof.origin().x(),"origin_y",proof.origin().y(),"direction",proof.direction(),
                    "batch_status",ascii(batch.status()),"shared_roll_key",nullableBytes(roll==null?null:roll.rollKey()),"raw_total",roll==null?null:roll.rawTotal(),"effective_total",roll==null?null:roll.effectiveTotal(),
                    "candidate_count",proof.candidates().size(),"target_count",proof.targets().size(),"die_count",roll==null?0:roll.dice().size(),"check_count",batch.checks().size(),"packet_count",batch.packets().size(),"obligation_count",batch.concentrationObligations().size());
            nullableEntry(root,"action",batch.action());add(out,TABLES.get(4),root);
            if(batch.spellSource()!=null) {var source=batch.spellSource();var r=batch(combat,sequence,phase,bi,"cast_frame_key",bytes(source.castFrameKey()),"slot_level",source.slotLevel(),"ability_modifier",source.abilityModifier(),"proficiency_bonus",source.proficiencyBonus());rule(r,"spell",source.spell());add(out,TABLES.get(14),r);}
            for(int i=0;i<proof.candidates().size();i++) {var c=proof.candidates().get(i);var r=batch(combat,sequence,phase,bi,"candidate_index",i,"x",c.position().x(),"y",c.position().y(),"included",bit(c.included()),"reason",ascii(c.reason()));creature(r,c.creature());add(out,TABLES.get(5),r);}
            for(int i=0;i<proof.targets().size();i++) {var r=batch(combat,sequence,phase,bi,"target_index",i);creature(r,proof.targets().get(i));add(out,TABLES.get(6),r);}
            if(roll!=null)for(int i=0;i<roll.dice().size();i++)add(out,TABLES.get(7),batch(combat,sequence,phase,bi,"die_index",i,"die_value",roll.dice().get(i)));
            for(int i=0;i<batch.checks().size();i++) {
                var c=batch.checks().get(i);add(out,TABLES.get(8),batch(combat,sequence,phase,bi,"check_index",i,"target_ordinal",c.targetOrdinal(),"purpose",ascii(c.purpose()),"ability_key",ascii(c.abilityKey()),"check_mode",ascii(c.mode()),
                        "first_d20",c.firstD20(),"second_d20",c.secondD20(),"ability_modifier",c.abilityModifier(),"proficiency_bonus",c.proficiencyBonus(),"advantage",bit(c.advantage()),"disadvantage",bit(c.disadvantage()),
                        "blessing_effect_key",nullableBytes(c.blessingEffectKey()),"blessing_d4",c.blessingD4(),"difficulty_class",c.difficultyClass(),"total",c.total(),"success",bit(c.success())));
            }
            for(int pi=0;pi<batch.packets().size();pi++) {
                var p=batch.packets().get(pi);add(out,TABLES.get(9),batch(combat,sequence,phase,bi,"packet_index",pi,"packet_key",bytes(p.packetKey()),"target_ordinal",p.targetOrdinal(),"shared_roll_key",bytes(p.sharedRollKey()),"save_check_index",p.saveCheckIndex(),
                        "before_hit_points",p.beforeHitPoints(),"before_temporary_hit_points",p.beforeTemporaryHitPoints(),"maximum_hit_points",p.maximumHitPoints(),"defense_count",p.defenses().size(),"damage_amount",p.damageAmount(),"absorbed_temporary_hit_points",p.absorbedTemporaryHitPoints(),
                        "after_hit_points",p.afterHitPoints(),"after_temporary_hit_points",p.afterTemporaryHitPoints(),"dead",bit(p.dead())));
                for(int di=0;di<p.defenses().size();di++) {var d=p.defenses().get(di);var r=batch(combat,sequence,phase,bi,"packet_index",pi,"defense_index",di,"defense_kind",ascii(d.kind()));rule(r,"source",d.source());add(out,TABLES.get(10),r);}
            }
            for(int i=0;i<batch.concentrationObligations().size();i++) {
                var o=batch.concentrationObligations().get(i);add(out,TABLES.get(11),batch(combat,sequence,phase,bi,"obligation_index",i,"target_ordinal",o.targetOrdinal(),"holder_source_key",nullableBytes(o.holderSourceKey()),"packet_key",bytes(o.packetKey()),"damage_amount",o.damageAmount(),"difficulty_class",o.difficultyClass(),"obligation_status",ascii(o.status()),"check_index",o.checkIndex()));
            }
        }
    }

    private static CreatureRef creature(Map<String,Object> r)throws SQLException{return new CreatureRef(en(CreatureKind.class,r,"creature_kind"),id(r,"creature_id"));}
    private static void creature(Map<String,Object> r,CreatureRef c){r.putAll(row("creature_kind",ascii(c.kind()),"creature_id",bytes(c.creatureId())));}
    private static EntryIdentity entry(Map<String,Object> r,String p)throws SQLException{return new EntryIdentity(rule(r,p+"_rule"),text(r,p+"_collection"),text(r,p+"_module"),text(r,p+"_key"));}
    private static EntryIdentity nullableEntry(Map<String,Object> r,String p)throws SQLException {
        if(r.get(p+"_rule_module")!=null)return entry(r,p);
        for(String suffix:List.of("_rule_type","_rule_key","_collection","_module","_key"))require(r.get(p+suffix)==null);return null;
    }
    private static void nullableEntry(Map<String,Object> r,String p,EntryIdentity e) {
        if(e!=null)entry(r,p,e);else for(String suffix:List.of("_rule_module","_rule_type","_rule_key","_collection","_module","_key"))r.put(p+suffix,null);
    }
    private static void entry(Map<String,Object> r,String p,EntryIdentity e){rule(r,p+"_rule",e.rule());r.putAll(row(p+"_collection",ascii(e.collectionKey()),p+"_module",ascii(e.moduleKey()),p+"_key",ascii(e.entryKey())));}
    private static RuleIdentity rule(Map<String,Object> r,String p)throws SQLException{return new RuleIdentity(text(r,p+"_module"),text(r,p+"_type"),text(r,p+"_key"));}
    private static void rule(Map<String,Object> r,String p,RuleIdentity id){r.putAll(row(p+"_module",ascii(id.moduleKey()),p+"_type",ascii(id.ruleType()),p+"_key",ascii(id.ruleKey())));}
    private static Map<String,Object> batch(UUID combat,long sequence,String phase,int index,Object...v){var r=phase(combat,sequence,phase,"batch_index",index);r.putAll(row(v));return r;}
    private static Map<String,Object> phase(UUID combat,long sequence,String phase,Object...v){var r=base(combat,sequence,"phase",ascii(phase));r.putAll(row(v));return r;}
    private static Map<String,Object> base(UUID combat,long sequence,Object...v){var r=row("combat_id",bytes(combat),"event_sequence",sequence);r.putAll(row(v));return r;}
    private static Map<String,Object> row(Object...v){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)r.put((String)v[i],v[i+1]);return r;}
    private static void add(Map<String,List<Map<String,Object>>> out,String table,Map<String,Object> row){out.computeIfAbsent(table,k->new ArrayList<>()).add(row);}
    private static byte[] ascii(Object o){return o.toString().getBytes(StandardCharsets.US_ASCII);}
    private static int bit(boolean b){return b?1:0;}
    private static byte[] nullableBytes(UUID id){return id==null?null:bytes(id);}
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    private static UUID id(Map<String,Object> r,String key)throws SQLException {Object o=r.get(key);require(o instanceof byte[]&&((byte[])o).length==16);var b=ByteBuffer.wrap((byte[])o);UUID id=new UUID(b.getLong(),b.getLong());require(id.version()==4&&id.variant()==2);return id;}
    private static UUID nullableId(Map<String,Object> r,String key)throws SQLException{return r.get(key)==null?null:id(r,key);}
    private static long number(Map<String,Object> r,String key)throws SQLException {Object o=r.get(key);require(o instanceof Byte||o instanceof Short||o instanceof Integer||o instanceof Long);return ((Number)o).longValue();}
    private static int n(Map<String,Object> r,String key)throws SQLException {long n=number(r,key);require(n>=Integer.MIN_VALUE&&n<=Integer.MAX_VALUE);return (int)n;}
    private static Integer nullableInt(Map<String,Object> r,String key)throws SQLException{return r.get(key)==null?null:n(r,key);}
    private static boolean bool(Map<String,Object> r,String key)throws SQLException {int v=n(r,key);require(v==0||v==1);return v==1;}
    private static String text(Map<String,Object> r,String key)throws SQLException {Object o=r.get(key);require(o instanceof byte[]&&((byte[])o).length>0&&((byte[])o).length<=128);byte[] b=(byte[])o;for(byte v:b)require(v>=32&&v<=126);return new String(b,StandardCharsets.US_ASCII);}
    private static <T extends Enum<T>>T en(Class<T> type,Map<String,Object> r,String key)throws SQLException {try{return Enum.valueOf(type,text(r,key));}catch(IllegalArgumentException invalid){throw new SQLException("S2_COMBAT_RUNTIME_CORRUPT",invalid);}}
    private static void ordinal(Map<String,Object> r,String key,int index)throws SQLException{require(n(r,key)==index);}
    private static void require(boolean valid)throws SQLException{if(!valid)throw new SQLException("S2_COMBAT_RUNTIME_CORRUPT");}
}
