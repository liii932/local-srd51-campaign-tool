package com.dndtool.persistence;

import com.dndtool.service.Schema2CombatState;
import com.dndtool.service.Schema2CombatTimeRules;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source/schema contracts only; these assertions never execute a MySQL statement. */
class Schema2MonsterBreathRuntimeSchemaTest {
    private static final Path MIGRATION=Path.of("src/main/resources/db/migration/V040__schema2-monster-breath-runtime.sql");
    private static final Path VERIFY=Path.of("database/verify/schema2-monster-breath.sql");
    private static final List<String> ROOT=List.of("combat_id","event_sequence");
    private static final List<String> STATE=List.of("combat_id","event_sequence","phase");
    private static final List<String> BATCH=List.of("combat_id","event_sequence","phase","batch_index");
    private record Edge(String table,String parent,List<String> key) { }
    private static final List<Edge> GRAPH=List.of(
        new Edge("s2_combat_monster_deployment","s2_combat_event",ROOT),
        new Edge("s2_combat_breath_input","s2_combat_event",ROOT),
        new Edge("s2_combat_monster","s2_combat_state",STATE),
        new Edge("s2_combat_monster_action","s2_combat_monster",List.of("combat_id","event_sequence","phase","monster_index")),
        new Edge("s2_combat_save_damage_batch","s2_combat_state",STATE),
        new Edge("s2_combat_breath_candidate","s2_combat_save_damage_batch",BATCH),
        new Edge("s2_combat_breath_target","s2_combat_save_damage_batch",BATCH),
        new Edge("s2_combat_breath_die","s2_combat_save_damage_batch",BATCH),
        new Edge("s2_combat_breath_check","s2_combat_save_damage_batch",BATCH),
        new Edge("s2_combat_breath_packet","s2_combat_save_damage_batch",BATCH),
        new Edge("s2_combat_breath_defense","s2_combat_breath_packet",List.of("combat_id","event_sequence","phase","batch_index","packet_index")),
        new Edge("s2_combat_breath_concentration","s2_combat_save_damage_batch",BATCH),
        new Edge("s2_combat_monster_rest_input","s2_combat_event",ROOT),
        new Edge("s2_combat_monster_rest","s2_combat_state",STATE));

    @Test void approvedRuleAndRuntimePayloadsAreDistinctAndTheEmptySceneGatePrecedesPersistentDdl()throws Exception {
        var runtime=SchemaMigrations.loadExpectations();var source=RuleSchemaMigrations.expectations();
        assertEquals(40,runtime.size());assertEquals(11,source.size());
        for(int version:List.of(39,40)) {
            var expected=runtime.get(version-1);String sql=Files.readString(Path.of("src/main/resources/db/migration",expected.scriptName()));
            assertEquals(version,expected.version());assertEquals(expected.scriptSha256(),SchemaMigrations.canonicalPayloadSha256(sql));
            assertTrue(sql.substring(sql.indexOf("-- CHECKSUM-SCOPE-END")).contains("'"+expected.scriptSha256()+"'"));
        }
        var expected=source.getLast();assertEquals(11,expected.version());assertEquals("V011__schema2-monster-source.sql",expected.scriptName());
        assertEquals(expected.scriptSha256(),RuleSchemaMigrations.canonicalPayloadSha256(Files.readAllBytes(Path.of("database/rules/migration",expected.scriptName()))));
        assertNull(getClass().getResource("/db/migration/"+expected.scriptName()));
        String sql=Files.readString(MIGRATION);int gate=sql.indexOf("AND NOT EXISTS (SELECT 1 FROM s2_combat_runtime)");
        assertTrue(gate>0);String gateStatement=sql.substring(sql.lastIndexOf(';',gate)+1,sql.indexOf(';',gate));
        assertTrue(gateStatement.contains("INSERT INTO v040_preflight"));
        assertTrue(gateStatement.contains("(SELECT COUNT(*) FROM schema_meta)=39"));
        assertFalse(gateStatement.contains("combat_status"),"ENDED histories must also block this migration");
        var persistent=Pattern.compile("(?m)^(?:ALTER TABLE|CREATE TABLE) ").matcher(sql);assertTrue(persistent.find());
        assertTrue(sql.indexOf(';',gate)<persistent.start(),"the failing temporary-table gate must finish before persistent DDL");
        assertFalse(Pattern.compile("(?i)\\b(?:DROP TABLE|TRUNCATE|DELETE FROM|UPDATE)\\s+s2_").matcher(sql).find());
    }

    @Test void everyTypedChildRetainsTheExactLocalParentAndUnconditionalImmutability()throws Exception {
        String sql=Files.readString(MIGRATION);var tables=tables(sql);
        assertEquals(new TreeSet<>(GRAPH.stream().map(Edge::table).toList()),new TreeSet<>(tables.keySet()));
        for(var edge:GRAPH) {
            String body=tables.get(edge.table());var fk=Pattern.compile("FOREIGN KEY \\(([^)]+)\\) REFERENCES (\\w+) \\(([^)]+)\\) ON UPDATE (\\w+) ON DELETE (\\w+)").matcher(body);
            assertTrue(fk.find(),edge.table());assertEquals(edge.parent(),fk.group(2),edge.table());
            assertEquals(edge.key(),columns(fk.group(1)),edge.table());assertEquals(edge.key(),columns(fk.group(3)),edge.table());
            assertEquals("RESTRICT",fk.group(4));assertEquals("RESTRICT",fk.group(5));assertFalse(fk.find());
            assertTrue(body.contains("PRIMARY KEY ("),edge.table());
            for(String event:List.of("UPDATE","DELETE")) {
                var trigger=Pattern.compile("CREATE TRIGGER \\w+ BEFORE "+event+" ON "+edge.table()+" FOR EACH ROW\\s+BEGIN\\s+(.*?)\\s+END\\$\\$",Pattern.DOTALL).matcher(sql);
                assertTrue(trigger.find(),edge.table()+" "+event);
                assertTrue(trigger.group(1).matches("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='[^']+';"),"no conditional update/delete escape: "+edge.table());
                assertFalse(trigger.find());
            }
        }
        assertFalse(Pattern.compile("(?i)\\bJSON(?:_\\w+)?\\b|REFERENCES\\s+[`\\w]+\\.").matcher(sql).find());
    }

    @Test void sqlAndJavaShareClosedFrameStageTriggerAndCommandDomains()throws Exception {
        String sql=Files.readString(MIGRATION);
        assertEquals(names(Schema2CombatState.Kind.values()),enumValues(alter(sql,"s2_combat_event"),"command_kind"));
        assertEquals(names(Schema2CombatState.FrameKind.values()),enumValues(alter(sql,"s2_combat_frame"),"frame_kind"));
        assertEquals(names(Schema2CombatState.Stage.values()),enumValues(alter(sql,"s2_combat_frame"),"stage"));
        for(String table:List.of("s2_combat_ready_input","s2_combat_window_trigger","s2_combat_trigger"))
            assertEquals(names(Schema2CombatState.TriggerKind.values()),enumValues(alter(sql,table),"trigger_kind"),table);
        var tables=tables(sql);
        assertEquals(names(Schema2CombatState.BatchStatus.values()),enumValues(tables.get("s2_combat_save_damage_batch"),"batch_status"));
        assertEquals(names(Schema2CombatState.ConcentrationDamageStatus.values()),enumValues(tables.get("s2_combat_breath_concentration"),"obligation_status"));
        assertEquals(names(Schema2CombatState.MonsterRestStatus.values()),enumValues(tables.get("s2_combat_monster_rest"),"rest_status"));
    }

    @Test void rawContinuationKeepsItsExactFlowAndLeafWithoutInventingAWindow()throws Exception {
        String event=compact(alter(Files.readString(MIGRATION),"s2_combat_event"));
        assertTrue(event.contains("flow_key IS NOT NULL AND leaf_key IS NOT NULL"));
        assertTrue(Pattern.compile("(?:command_kind\\s*=\\s*_binary'CONTINUE_FLOW'\\s+OR\\s+window_key IS NOT NULL|window_key IS NOT NULL\\s+OR\\s+command_kind\\s*=\\s*_binary'CONTINUE_FLOW')").matcher(event).find(),
                "only CONTINUE_FLOW may omit window_key; raw SAVE_DAMAGE has a real leaf and no window");
        assertTrue(event.contains("command_kind IN (_binary'CONTINUE_MOVEMENT',_binary'CONTINUE_FLOW') AND opportunity_key IS NULL"));
        assertTrue(event.contains("opportunity_key IS NOT NULL"));
    }

    @Test void rollNullabilityCountsAndRestTimeHaveExplicitBoundaries()throws Exception {
        String sql=Files.readString(MIGRATION);var tables=tables(sql);String batch=compact(tables.get("s2_combat_save_damage_batch"));
        for(String count:List.of("candidate_count BETWEEN 2 AND 64","target_count BETWEEN 0 AND 64","die_count IN (0,6)","check_count BETWEEN 0 AND 128","packet_count BETWEEN 0 AND 64","obligation_count BETWEEN 0 AND 64"))assertTrue(batch.contains(count),count);
        assertTrue(batch.contains("target_count=0 AND shared_roll_key IS NULL AND die_count=0 AND raw_total IS NULL AND effective_total IS NULL"));
        assertTrue(batch.contains("target_count>0 AND shared_roll_key IS NOT NULL AND die_count=6"));
        assertTrue(batch.contains("raw_total IS NOT NULL"));assertTrue(batch.contains("effective_total IS NOT NULL"));
        assertTrue(compact(tables.get("s2_combat_breath_die")).contains("die_index BETWEEN 0 AND 5 AND die_value BETWEEN 1 AND 6"));
        String check=compact(tables.get("s2_combat_breath_check"));
        assertTrue(check.contains("check_mode=_binary'AUTO_FAILURE' AND first_d20 IS NULL AND second_d20 IS NULL AND success=0"));
        assertTrue(check.contains("check_mode=_binary'ROLL' AND first_d20 IS NOT NULL"));
        assertTrue(check.contains("advantage=disadvantage AND second_d20 IS NULL"));
        assertTrue(check.contains("advantage<>disadvantage AND second_d20 IS NOT NULL"));
        assertTrue(check.contains("blessing_effect_key IS NULL AND blessing_d4 IS NULL"));
        assertTrue(check.contains("blessing_effect_key IS NOT NULL AND blessing_d4 IS NOT NULL"),"a present Bless source requires its retained die");
        String obligation=compact(tables.get("s2_combat_breath_concentration"));
        assertTrue(obligation.contains("holder_source_key IS NOT NULL AND check_index IS NOT NULL"),"a nullable BETWEEN alone permits SQL UNKNOWN");
        assertTrue(obligation.contains("obligation_status IN (_binary'NOT_REQUIRED',_binary'FORCED_ENDED') AND check_index IS NULL"));
        String rest=compact(tables.get("s2_combat_monster_rest"));
        assertTrue(rest.contains("completes_at_second-started_at_second=IF(rest_kind=_binary'SHORT',3600,28800)"));
        assertTrue(rest.contains("rest_status=_binary'ACTIVE' AND finished_operation_id IS NULL"));
        assertTrue(rest.contains("rest_status<>_binary'ACTIVE' AND finished_operation_id IS NOT NULL"));
        assertTrue(alter(sql,"s2_character_combat_input").contains("seconds_advanced BETWEEN 0 AND 28800"));
        assertThrows(IllegalArgumentException.class,()->Schema2CombatTimeRules.advanceSeconds(null,null,61));
        var method=Arrays.stream(Schema2CombatTimeRules.class.getDeclaredMethods()).filter(m->m.getName().equals("advanceRestSeconds")).findFirst().orElseThrow();
        assertFalse(Modifier.isPublic(method.getModifiers()));
        Set<String> longTimeCallers=new TreeSet<>();
        try(var paths=Files.list(Path.of("src/main/java/com/dndtool/service"))) {
            for(Path p:paths.filter(p->p.toString().endsWith(".java")).toList())
                if(Files.readString(p).contains("Schema2CombatTimeRules.advanceRestSeconds("))longTimeCallers.add(p.getFileName().toString());
        }
        assertEquals(Set.of("Schema2MonsterRestRules.java","Schema2CombatCharacterFacts.java"),longTimeCallers);
    }

    @Test void verificationIsReadOnlyAndEveryNewTableHasExactSelectInsertGrants()throws Exception {
        String verify=Files.readString(VERIFY),grants=Files.readString(Path.of("database/grants/schema2-combat-runtime.sql"));
        String statements=verify.replaceAll("(?m)--[^\\r\\n]*","");
        for(String statement:statements.split(";"))if(!statement.isBlank()) {
            assertTrue(statement.stripLeading().matches("(?is)(SELECT|SHOW|WITH)\\b.*"));
            String code=statement.replaceAll("'[^']*'","''");
            assertFalse(Pattern.compile("(?i)\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|GRANT|SET|CALL|OUTFILE|DUMPFILE|INTO)\\b").matcher(code).find(),statement);
        }
        for(var edge:GRAPH) {
            assertTrue(verify.contains(edge.table()),edge.table());
            var grant=Pattern.compile("GRANT ([A-Z, ]+) ON `dnd_tool_se`\\.`"+edge.table()+"`").matcher(grants);
            assertTrue(grant.find(),edge.table());assertEquals(Set.of("SELECT","INSERT"),new HashSet<>(columns(grant.group(1))));assertFalse(grant.find());
        }
        for(String count:List.of("monster_count","batch_count","monster_rest_count","action_count","candidate_count","target_count","die_count","check_count","packet_count","obligation_count","defense_count"))
            assertTrue(verify.contains(count+" <> (SELECT COUNT(*) FROM "),count);
        assertTrue(verify.contains("SUM(d.die_value)"));assertTrue(verify.contains("information_schema.key_column_usage"));
        assertTrue(verify.contains("shared_roll_key"));assertTrue(verify.contains("holder_source_key"));assertTrue(verify.contains("NOT EXISTS"));
    }

    private static Map<String,String> tables(String sql) {
        var result=new LinkedHashMap<String,String>();var m=Pattern.compile("CREATE TABLE (\\w+) \\((.*?)\\) ENGINE=InnoDB[^;]*;",Pattern.DOTALL).matcher(sql);
        while(m.find())assertNull(result.put(m.group(1),m.group(2)));return result;
    }
    private static String alter(String sql,String table) {
        var matcher=Pattern.compile("ALTER\\s+TABLE\\s+"+Pattern.quote(table)+"\\s+ADD\\s+CONSTRAINT\\b.*?;",Pattern.DOTALL).matcher(sql);
        assertTrue(matcher.find(),table);return matcher.group();
    }
    private static Set<String> enumValues(String body,String column) {
        var m=Pattern.compile("\\b"+column+" IN \\(([^)]+)\\)").matcher(body);assertTrue(m.find(),column);
        Set<String> result=new TreeSet<>();var literal=Pattern.compile("_binary'([^']+)'").matcher(m.group(1));while(literal.find())assertTrue(result.add(literal.group(1)));return result;
    }
    private static Set<String> names(Enum<?>[] values) {Set<String> result=new TreeSet<>();for(var value:values)result.add(value.name());return result;}
    private static List<String> columns(String text) {return Arrays.stream(text.split(",")).map(String::trim).toList();}
    private static String compact(String text) {return text.replaceAll("\\s+"," ");}
}
