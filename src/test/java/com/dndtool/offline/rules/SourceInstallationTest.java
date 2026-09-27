package com.dndtool.offline.rules;

import com.dndtool.persistence.RuleSchemaMigrations;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** JDBC protocol/fault tests, not a claim of MySQL engine, privilege or physical durability acceptance. */
class SourceInstallationTest {
    @TempDir Path temp;
    private RuleArtifact artifact;
    @BeforeEach void input()throws Exception {artifact=OfflineTestSupport.artifact(temp);}
    @Test void firstInstallationAndSameArtifactNewIntentHaveDistinctPermanentResults()throws Exception {
        var db=new Database();
        try(var attempt=attempt(db,0)) {
            var result=attempt.service.install(attempt.ticket,artifact);
            assertEquals(SourceInstallation.Status.COMMITTED,result.status());assertEquals(1,result.acceptedRevision());
            assertEquals("PARTITION",result.acceptance().scope());assertNull(result.acceptance().observedContentSha256());
            assertEquals(23,db.dml);assertEquals(1,db.commits);assertEquals(4L,db.control[2]);assertEquals(1L,db.control[3]);
            assertEquals(18,db.languages.size());assertNull(attempt.store.pending());
        }
        db.resetFaults();
        try(var attempt=attempt(db,1)) {
            assertEquals(SourceInstallation.Status.COMMITTED,attempt.service.install(attempt.ticket,artifact).status());
            assertEquals(40,db.dml);assertEquals(2,db.facts.size());assertEquals(6L,db.control[2]);assertEquals(2L,db.control[3]);
        }
        assertTrue(db.sql.stream().noneMatch(s->s.contains("dnd_tool_se")||s.contains("LOCK TABLES")||s.contains("SAVEPOINT")));
        assertTrue(db.sql.stream().filter(s->s.contains("FOR UPDATE")).allMatch(s->s.contains("rule_installation_control")||s.startsWith("SELECT id FROM rule_release")));
    }
    @TestFactory Stream<DynamicTest> everyWritePointExceptionZeroAndMultipleRowsRollBack()throws Exception {
        // 23 first-root and 40 replacement statements, each independently failed three ways.
        List<DynamicTest> tests=new ArrayList<>();
        for(boolean existing:List.of(false,true))for(int point=1;point<=(existing?40:23);point++)for(String failure:List.of("exception","zero","multiple")) {
            int selected=point;String name=(existing?"replace":"first")+"-"+point+"-"+failure;
            tests.add(DynamicTest.dynamicTest(name,()->{
                var db=new Database();if(existing)seed(db);
                String before=db.snapshot();db.failPoint=selected;db.failure=failure;
                try(var attempt=attempt(db,existing?1:0)) {
                    var result=attempt.service.install(attempt.ticket,artifact);
                    assertEquals(SourceInstallation.Status.ROLLED_BACK,result.status());assertEquals(before,db.snapshot());
                    assertEquals(1,db.rollbacks);assertEquals(0,db.commits);assertNull(attempt.store.pending());
                }
            }));
        }
        return tests.stream();
    }
    @Test void realNinthRowReadbackCorruptionRollsBackAllWrites()throws Exception {
        var db=new Database();db.corruptReadback=true;
        try(var attempt=attempt(db,0)) {
            assertEquals(SourceInstallation.Status.ROLLED_BACK,attempt.service.install(attempt.ticket,artifact).status());
            assertTrue(db.languages.isEmpty());assertTrue(db.facts.isEmpty());assertEquals(23,db.dml);
        }
    }
    @Test void commitResponseLostRemainsUnknownAndExplicitNoInputResolutionFindsAcceptance()throws Exception {
        var db=new Database();db.commitLost=true;
        OperationTicket ticket;Path state;
        try(var attempt=attempt(db,0)) {
            ticket=attempt.ticket;state=attempt.state;
            assertEquals(SourceInstallation.Status.UNKNOWN,attempt.service.install(ticket,artifact).status());
            assertEquals(ticket,attempt.store.pending());assertEquals(0,db.rollbacks);assertEquals(1,db.facts.size());
            assertThrows(Exception.class,()->attempt.service.install(ticket,artifact));
        }
        db.resetFaults();db.resolution=true;var evidence=OfflineTestSupport.evidence(temp,state,"RESOLVE",ticket.operationId());
        try(var store=new TicketStore(state)) {
            var service=new SourceInstallation(evidence,store,db::open);
            var result=service.resolve(store.load(ticket.operationId()+".json"));
            assertEquals(SourceInstallation.Status.COMMITTED,result.status());assertEquals(0,db.dml);assertEquals(0,db.commits);assertEquals(1,db.rollbacks);assertNull(store.pending());
        }
    }
    @Test void failedRollbackIncludingZeroDmlIsUnknownAndNotRetried()throws Exception {
        var db=new Database();db.badControl=true;db.rollbackFails=true;
        try(var a=attempt(db,0)) {
            assertEquals(SourceInstallation.Status.UNKNOWN,a.service.install(a.ticket,artifact).status());
            assertEquals(0,db.dml);assertEquals(1,db.rollbacks);assertNotNull(a.store.pending());
        }
    }
    @Test void commitSuccessThenCloseFailureStaysCommittedAndPreservesBlock()throws Exception {
        var db=new Database();db.closeFails=true;
        try(var a=attempt(db,0)) {
            assertEquals(SourceInstallation.Status.COMMITTED,a.service.install(a.ticket,artifact).status());
            assertEquals(1,db.commits);assertNotNull(a.store.pending());assertEquals(1,db.facts.size());
        }
    }
    @Test void corruptPendingAfterAcknowledgedCommitPreservesAcceptanceAndBlock()throws Exception {
        var db=new Database();
        try(var a=attempt(db,0)) {
            db.afterClose=()->Files.writeString(a.state.resolve("pending"),"corrupt pending intent");
            var result=a.service.install(a.ticket,artifact);
            assertEquals(SourceInstallation.Status.COMMITTED,result.status());
            assertEquals(a.ticket.operationId(),result.acceptance().operationId());
            assertEquals(1,result.acceptedRevision());assertEquals(1,db.commits);
            assertTrue(result.detail().contains("maintenance marker requires operator resolution"));
            assertEquals("corrupt pending intent",Files.readString(a.state.resolve("pending")));
            assertThrows(Exception.class,()->a.service.install(a.ticket,artifact));
            assertEquals(1,db.opens);
        }
    }
    @Test void oldAcceptedReplayPrecedesStaleExpectedReleasedCurrentAndNeedsNoArtifact()throws Exception {
        var db=new Database();OperationTicket original;Path state;
        try(var a=attempt(db,0)){original=a.ticket;state=a.state;assertEquals(SourceInstallation.Status.COMMITTED,a.service.install(original,artifact).status());}
        db.resetFaults();try(var a=attempt(db,1)){assertEquals(SourceInstallation.Status.COMMITTED,a.service.install(a.ticket,artifact).status());}
        // A future fully reviewed publisher would produce this state; the resolver reports its fact,
        // never claims that this language-only profile validates complete content or authorizes new writes.
        db.roots.getFirst()[6]=b("a".repeat(64));db.roots.getFirst()[7]=b("RELEASED");db.roots.getFirst()[10]=Database.NOW;
        db.facts.getLast()[9]=b("COMPLETE");db.facts.getLast()[10]=b("a".repeat(64));db.resetFaults();
        db.resolution=true;var evidence=OfflineTestSupport.evidence(temp,state,"RESOLVE",original.operationId());
        try(var store=new TicketStore(state)) {
            var result=new SourceInstallation(evidence,store,db::open).resolve(original);
            assertEquals(SourceInstallation.Status.COMMITTED,result.status());assertEquals(1,result.acceptedRevision());assertEquals(2,result.currentRevision());
            assertEquals("RELEASED",result.currentStatus());assertEquals("PARTITION",result.acceptance().scope());assertEquals(0,db.dml);
            assertNull(result.acceptance().observedContentSha256());assertNull(store.pending());
        }
        db.resetFaults();db.resolution=false;var installEvidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
        try(var store=new TicketStore(state)) {
            var result=new SourceInstallation(installEvidence,store,db::open).install(original,artifact);
            assertEquals(SourceInstallation.Status.COMMITTED,result.status());assertEquals(1,result.acceptedRevision());assertEquals(2,result.currentRevision());
            assertEquals("RELEASED",result.currentStatus());assertEquals("PARTITION",result.acceptance().scope());assertNull(result.acceptance().observedContentSha256());
            assertEquals(0,db.dml);assertEquals(0,db.commits);assertEquals(1,db.rollbacks);assertNull(store.pending());
        }
    }
    @TestFactory Stream<DynamicTest> originalLanguageReceiptCannotClaimCompleteAcceptance() {
        List<DynamicTest> tests=new ArrayList<>();
        for(boolean resolution:List.of(false,true))for(boolean historical:List.of(false,true)) {
            String name=(resolution?"resolve-unknown":"install-replay")+"-"+(historical?"historical":"current");
            tests.add(DynamicTest.dynamicTest(name,()->{
                var db=new Database();db.commitLost=resolution;OperationTicket original;Path state;
                try(var a=attempt(db,0)) {
                    original=a.ticket;state=a.state;
                    assertEquals(resolution?SourceInstallation.Status.UNKNOWN:SourceInstallation.Status.COMMITTED,a.service.install(original,artifact).status());
                    assertEquals(resolution?original:null,a.store.pending());
                }
                db.resetFaults();
                if(historical)try(var later=attempt(db,1)) {
                    assertEquals(SourceInstallation.Status.COMMITTED,later.service.install(later.ticket,artifact).status());
                }
                // Preserve a structurally valid permanent chain: COMPLETE carries a valid digest,
                // and only the current receipt's observation must match the current root.
                db.facts.getFirst()[9]=b("COMPLETE");db.facts.getFirst()[10]=b("c".repeat(64));
                if(!historical)db.roots.getFirst()[6]=b("c".repeat(64));
                db.resetFaults();db.resolution=resolution;String before=db.snapshot();
                var evidence=OfflineTestSupport.evidence(temp,state,resolution?"RESOLVE":"INSTALL",resolution?original.operationId():null);
                try(var store=new TicketStore(state)) {
                    var service=new SourceInstallation(evidence,store,db::open);
                    var result=resolution?service.resolve(original):service.install(original,artifact);
                    assertEquals(SourceInstallation.Status.CONFLICT,result.status());assertNull(result.acceptance());assertNull(result.acceptedRevision());
                    assertEquals(before,db.snapshot());assertEquals(0,db.dml);assertEquals(0,db.commits);assertEquals(1,db.rollbacks);
                    assertEquals(1,db.opens);assertEquals(1,db.closes);assertEquals(0,db.restoreAutoCommit);assertEquals(original,store.pending());
                    var installEvidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
                    var next=OperationTicket.create(artifact,historical?2:1,installEvidence);store.save(next);
                    var installer=new SourceInstallation(installEvidence,store,db::open);
                    assertThrows(java.io.IOException.class,()->installer.install(next,artifact));
                    assertEquals(1,db.opens);assertEquals(0,db.dml);assertEquals(0,db.commits);assertEquals(original,store.pending());
                }
            }));
        }
        return tests.stream();
    }
    @Test void absenceRequiresIsolatedOriginalIntentAndReadOnlyBarrier()throws Exception {
        var db=new Database();OperationTicket ticket;Path state;
        try(var a=attempt(db,0)){ticket=a.ticket;state=a.state;a.store.begin(ticket);assertThrows(Exception.class,()->a.service.resolve(ticket));}
        db.resolution=true;var evidence=OfflineTestSupport.evidence(temp,state,"RESOLVE",ticket.operationId());
        try(var store=new TicketStore(state)) {
            var result=new SourceInstallation(evidence,store,db::open).resolve(ticket);
            assertEquals(SourceInstallation.Status.NOT_COMMITTED,result.status());assertEquals(0,db.dml);assertEquals(1,db.rollbacks);assertTrue(db.sql.stream().anyMatch(s->s.endsWith("FOR UPDATE")));
        }
    }
    @Test void resolutionReleaseFailureIsUnknownEvenIfAnotherRollbackWouldSucceed()throws Exception {
        var db=new Database();OperationTicket ticket;Path state;
        try(var a=attempt(db,0)){ticket=a.ticket;state=a.state;}
        db.rollbackFails=true;db.resolution=true;var evidence=OfflineTestSupport.evidence(temp,state,"RESOLVE",ticket.operationId());
        try(var store=new TicketStore(state)) {
            assertEquals(SourceInstallation.Status.UNKNOWN,new SourceInstallation(evidence,store,db::open).resolve(ticket).status());
            assertEquals(1,db.rollbacks);assertEquals(ticket,store.pending());
        }
    }
    @Test void schemaGrantsAndConnectionOwnershipFailBeforeBusinessSql()throws Exception {
        for(String fault:List.of("schema","grant","role","ledger")) {
            var db=new Database();db.preflightFault=fault;
            try(var a=attempt(db,0)) {
                assertEquals(SourceInstallation.Status.NOT_EXECUTED,a.service.install(a.ticket,artifact).status());
                assertEquals(0,db.dml);assertEquals(0,db.commits);assertFalse(db.sql.stream().anyMatch(s->s.contains("FOR UPDATE")));
                assertEquals(1,db.opens);assertEquals(1,db.closes);assertEquals(0,db.restoreAutoCommit);
            }
        }
    }
    @Test void everyResolutionFailureRetainsOriginalUnknownMarker()throws Exception {
        for(String phase:List.of("open","schema","grant","set-rc","set-auto","lock","history")) {
            var db=new Database();OperationTicket ticket;Path state;
            try(var a=attempt(db,0)){ticket=a.ticket;state=a.state;a.store.begin(ticket);}
            var evidence=OfflineTestSupport.evidence(temp,state,"RESOLVE",ticket.operationId());
            db.preflightFault=phase;db.resolution=true;
            try(var store=new TicketStore(state)) {
                var result=new SourceInstallation(evidence,store,db::open).resolve(ticket);
                assertEquals(SourceInstallation.Status.UNKNOWN,result.status(),phase);assertEquals(ticket,store.pending(),phase);assertEquals(0,db.dml);
            }
        }
    }
    @Test void acceptedReplayReleaseFailureIsUnknownWithoutSecondRollback()throws Exception {
        var db=new Database();OperationTicket ticket;Path state;
        try(var a=attempt(db,0)){ticket=a.ticket;state=a.state;a.service.install(ticket,artifact);}
        db.resetFaults();db.rollbackFails=true;
        var evidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
        try(var store=new TicketStore(state)) {
            var result=new SourceInstallation(evidence,store,db::open).install(ticket,artifact);
            assertEquals(SourceInstallation.Status.UNKNOWN,result.status());assertEquals(1,db.rollbacks);assertEquals(0,db.dml);assertEquals(ticket,store.pending());
        }
    }
    @Test void legitimateEmptyRootIsChargedOnceAndSortPermutationReplacesWholePartition()throws Exception {
        var db=new Database();db.roots.add(new Object[]{1L,b("dnd5e2014_srd51_se"),b("1"),2,2,b("SHA-256"),null,b("DRAFT"),0L,Database.NOW,null});db.control[2]=2L;
        try(var a=attempt(db,0)){assertEquals(SourceInstallation.Status.COMMITTED,a.service.install(a.ticket,artifact).status());assertEquals(22,db.dml);assertEquals(4L,db.control[2]);}
        db.resetFaults();Path language=temp.resolve("author/character/languages.json");String content=Files.readString(language);
        // Last JSON member can end without a comma; use token-aware replacement for a complete reversal.
        var matcher=java.util.regex.Pattern.compile("\"sort_order\"\\s*:\\s*(\\d+)").matcher(content);StringBuilder changed=new StringBuilder();
        while(matcher.find())matcher.appendReplacement(changed,"\"sort_order\": "+(19-Integer.parseInt(matcher.group(1))));matcher.appendTail(changed);Files.writeString(language,changed.toString());
        String hash=RuleArtifact.build(temp.resolve("author"),temp.resolve("permuted"));RuleArtifact replacement=RuleArtifact.read(temp.resolve("permuted"),hash);
        Path state=OfflineTestSupport.privateDirectory(temp,"permutation-state");var evidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);
        try(var store=new TicketStore(state)) {
            var ticket=OperationTicket.create(replacement,1,evidence);store.save(ticket);
            assertEquals(SourceInstallation.Status.COMMITTED,new SourceInstallation(evidence,store,db::open).install(ticket,replacement).status());
            assertEquals(40,db.dml);assertEquals(18,db.languages.getFirst()[6]);
        }
    }
    @Test void historicalCompleteDigestSurvivesCurrentPartitionNull()throws Exception {
        var db=new Database();seed(db);
        try(var a=attempt(db,1)){a.service.install(a.ticket,artifact);}
        db.resetFaults();db.facts.getFirst()[9]=b("COMPLETE");db.facts.getFirst()[10]=b("c".repeat(64));
        try(var a=attempt(db,2)){assertEquals(SourceInstallation.Status.COMMITTED,a.service.install(a.ticket,artifact).status());}
        assertArrayEquals(b("c".repeat(64)),(byte[])db.facts.getFirst()[10]);assertNull(db.roots.getFirst()[6]);assertNull(db.facts.getLast()[10]);
    }
    @Test void changedIntentStaleGenerationCorruptHistoryAndUnsupportedVersionReject()throws Exception {
        var db=new Database();seed(db);
        try(var a=attempt(db,0)) {assertEquals(SourceInstallation.Status.ROLLED_BACK,a.service.install(a.ticket,artifact).status());assertEquals(0,db.dml);}
        for(String fault:List.of("count","gap","partition","protocol","extra-control","language")) {
            db=new Database();seed(db);
            switch(fault) {
                case "count"->db.control[2]=5L;case "gap"->db.facts.getFirst()[1]=2L;case "partition"->db.partitions.clear();
                case "protocol"->db.facts.getFirst()[5]=2;case "extra-control"->db.extraControl=true;case "language"->db.languages.remove(8);
            }
            try(var a=attempt(db,1)){assertEquals(SourceInstallation.Status.ROLLED_BACK,a.service.install(a.ticket,artifact).status(),fault);assertEquals(0,db.dml);}
        }
    }
    @Test void sameIdWithDifferentPermanentFingerprintConflictsWithoutDml()throws Exception {
        var db=new Database();OperationTicket ticket;Path state;
        try(var a=attempt(db,0)){ticket=a.ticket;state=a.state;a.service.install(ticket,artifact);}
        db.facts.getFirst()[4]=b("b".repeat(64));db.resetFaults();
        db.resolution=true;var evidence=OfflineTestSupport.evidence(temp,state,"RESOLVE",ticket.operationId());
        try(var store=new TicketStore(state)) {
            assertEquals(SourceInstallation.Status.CONFLICT,new SourceInstallation(evidence,store,db::open).resolve(ticket).status());assertEquals(0,db.dml);
        }
    }
    @Test void metadataBudgetIsExactAndReplaysDoNotConsumeIt()throws Exception {
        var db=new Database();OperationTicket accepted;Path acceptedState;
        try(var a=attempt(db,0)){accepted=a.ticket;acceptedState=a.state;a.service.install(a.ticket,artifact);}db.resetFaults();
        // Existing empty roots cost exactly one each and carry no domain/install rows.
        for(int id=2;id<=16381;id++)db.roots.add(new Object[]{(long)id,b("empty.root"+id),b("1"),2,2,b("SHA-256"),null,b("DRAFT"),0L,Database.NOW,null});
        db.control[2]=16384L;
        try(var a=attempt(db,1)) {assertEquals(SourceInstallation.Status.ROLLED_BACK,a.service.install(a.ticket,artifact).status());assertEquals(0,db.dml);}
        db.resetFaults();var evidence=OfflineTestSupport.evidence(temp,acceptedState,"RESOLVE",accepted.operationId());db.resolution=true;
        try(var store=new TicketStore(acceptedState)) {
            assertEquals(SourceInstallation.Status.COMMITTED,new SourceInstallation(evidence,store,db::open).resolve(accepted).status());
            assertEquals(0,db.dml);assertEquals(16384L,db.control[2]);
        }
    }
    private void seed(Database db)throws Exception {
        try(var a=attempt(db,0)){assertEquals(SourceInstallation.Status.COMMITTED,a.service.install(a.ticket,artifact).status());}
        db.resetFaults();
    }
    private Attempt attempt(Database db,long expected)throws Exception {
        Path state=OfflineTestSupport.privateDirectory(temp,"state-"+UUID.randomUUID());
        var evidence=OfflineTestSupport.evidence(temp,state,"INSTALL",null);var store=new TicketStore(state);
        var ticket=OperationTicket.create(artifact,expected,evidence);store.save(ticket);
        return new Attempt(state,store,ticket,new SourceInstallation(evidence,store,db::open));
    }
    private record Attempt(Path state,TicketStore store,OperationTicket ticket,SourceInstallation service)implements AutoCloseable {
        @Override public void close()throws Exception {store.close();}
    }
    static byte[] b(String text){return text.getBytes(StandardCharsets.US_ASCII);}
    static final class Database {
        static final LocalDateTime NOW=LocalDateTime.of(2026,1,1,0,0);
        Object[] control={1,1,1L,0L};List<Object[]> roots=new ArrayList<>(),languages=new ArrayList<>(),facts=new ArrayList<>(),partitions=new ArrayList<>();
        List<String> sql=new ArrayList<>();int dml,commits,rollbacks,opens,closes,restoreAutoCommit,failPoint;String failure="",preflightFault="";
        boolean commitLost,rollbackFails,closeFails,badControl,extraControl,corruptReadback,resolution;
        @FunctionalInterface interface CloseAction {void run()throws Exception;}
        CloseAction afterClose=()->{};
        void resetFaults(){dml=commits=rollbacks=opens=closes=restoreAutoCommit=failPoint=0;failure=preflightFault="";commitLost=rollbackFails=closeFails=badControl=extraControl=corruptReadback=false;sql.clear();}
        String snapshot(){return Arrays.deepToString(control)+Arrays.deepToString(roots.toArray())+Arrays.deepToString(languages.toArray())+Arrays.deepToString(facts.toArray())+Arrays.deepToString(partitions.toArray());}
        Connection open()throws SQLException {
            if(preflightFault.equals("open"))throw new SQLException("open failed");
            opens++;Object[] oldControl=control.clone();var oldRoots=copy(roots);var oldLanguages=copy(languages);var oldFacts=copy(facts);var oldPartitions=copy(partitions);
            boolean[] auto={true},sLocked={false},rootLocked={false};
            return proxy(Connection.class,(p,m,a)->switch(m.getName()) {
                case "getAutoCommit"->auto[0];
                case "setAutoCommit"->{if(preflightFault.equals("set-auto"))throw new SQLException("set auto failed");if((boolean)a[0])restoreAutoCommit++;auto[0]=(boolean)a[0];yield null;}
                case "setTransactionIsolation"->{if(preflightFault.equals("set-rc"))throw new SQLException("set rc failed");assertEquals(Connection.TRANSACTION_READ_COMMITTED,a[0]);yield null;}
                case "prepareStatement"->{String text=(String)a[0];sql.add(text);if(!auto[0]&&!sLocked[0])assertTrue(text.startsWith("SELECT control_id")&&text.endsWith("FOR UPDATE"),text);
                    if(preflightFault.equals("lock")&&text.endsWith("FOR UPDATE"))throw new SQLException("S lock failed");
                    if(preflightFault.equals("history")&&text.contains("FROM rule_package_installation"))throw new SQLException("history read failed");
                    if(text.endsWith("FOR UPDATE")){if(text.contains("rule_installation_control"))sLocked[0]=true;else rootLocked[0]=true;}
                    if(text.contains("FROM rule_package_installation")&&!oldRoots.isEmpty()&&text.contains("ORDER BY")&&!resolution)assertTrue(rootLocked[0],"Target root must precede permanent facts");
                    if(resolution)assertFalse(text.startsWith("SELECT id FROM rule_release"),"Resolution does not lock roots");
                    yield statement(text);}
                case "commit"->{commits++;if(commitLost)throw new SQLException("lost commit response");yield null;}
                case "rollback"->{rollbacks++;if(rollbackFails)throw new SQLException("lost rollback response");control=oldControl.clone();roots=copy(oldRoots);languages=copy(oldLanguages);facts=copy(oldFacts);partitions=copy(oldPartitions);yield null;}
                case "close"->{closes++;if(closeFails)throw new SQLException("close failed");afterClose.run();yield null;}
                case "isClosed"->false;
                default->throw new AssertionError("Unexpected connection method "+m.getName());
            });
        }
        PreparedStatement statement(String text) {
            Map<Integer,Object> params=new HashMap<>();int[] max={0};
            return proxy(PreparedStatement.class,(p,m,a)->switch(m.getName()) {
                case "setQueryTimeout"->{assertEquals(5,a[0]);yield null;}
                case "setMaxRows"->{max[0]=(int)a[0];assertTrue(max[0]>0&&max[0]<=16385);yield null;}
                case "setBytes","setString","setLong","setInt"->{params.put((int)a[0],a[1]);yield null;}
                case "setNull"->{params.put((int)a[0],null);yield null;}
                case "executeQuery"->result(query(text,params),max[0]);
                case "executeUpdate"->{dml++;if(dml==failPoint) {if(failure.equals("exception"))throw new SQLException("write failed");yield failure.equals("zero")?0:2;}update(text,params);yield 1;}
                case "close"->null;default->throw new AssertionError("Unexpected statement method "+m.getName());
            });
        }
        List<Object[]> query(String text,Map<Integer,Object> params) {
            if(text.startsWith("SELECT CAST(DATABASE()"))return one(new Object[]{b(preflightFault.equals("schema")?"wrong":"dnd_tool_rules"),"12345678-1234-1234-1234-123456789abc","installer@127.0.0.1",preflightFault.equals("role")?"admin":"NONE",0L,0L,"STRICT_TRANS_TABLES"});
            if(text.equals("SHOW GRANTS"))return one(new Object[]{preflightFault.equals("grant")?"GRANT ALL":OfflineTestSupport.GRANT});
            if(text.contains("FROM rule_schema_meta")){var expected=RuleSchemaMigrations.expectations().getFirst();return one(new Object[]{expected.version(),b(expected.scriptName()),b(preflightFault.equals("ledger")?"f".repeat(64):expected.scriptSha256())});}
            if(text.contains("FROM rule_installation_control")) {
                if(badControl)return List.of();if(extraControl&&!text.contains("WHERE"))return List.of(control,new Object[]{2,1,1L,0L});return one(control);
            }
            if(text.startsWith("SELECT id FROM rule_release"))return one(new Object[]{params.get(1)});
            if(text.startsWith("SELECT installation_revision"))return roots.isEmpty()?List.of():one(new Object[]{roots.getFirst()[8],roots.getFirst()[7],2,2,b("SHA-256")});
            if(text.startsWith("SELECT id, installation_revision"))return roots.isEmpty()?List.of():one(new Object[]{roots.getFirst()[0],roots.getFirst()[8],roots.getFirst()[6],roots.getFirst()[7],2,2,b("SHA-256")});
            if(text.contains("FROM rule_release"))return copy(roots);
            if(text.contains("FROM rule_package_installation_partition"))return copy(partitions);
            if(text.contains("FROM rule_package_installation"))return copy(facts);
            if(text.contains("FROM rule_language")) {
                var rows=copy(languages);if(corruptReadback&&dml>0&&rows.size()==18)rows.get(8)[2]="corrupted ninth row";return rows;
            }
            throw new AssertionError("Unexpected SQL "+text);
        }
        void update(String text,Map<Integer,Object> p) {
            if(text.startsWith("INSERT INTO rule_release")){roots.add(new Object[]{1L,p.get(1),p.get(2),p.get(3),p.get(4),p.get(5),null,b("DRAFT"),0L,NOW,null});return;}
            if(text.startsWith("DELETE FROM rule_language")){assertTrue(languages.removeIf(row->row[0].equals(p.get(1))&&Arrays.equals((byte[])row[1],(byte[])p.get(2))));return;}
            if(text.startsWith("INSERT INTO rule_language")){languages.add(values(p,7));languages.sort(Comparator.comparing(row->new String((byte[])row[1],StandardCharsets.US_ASCII)));return;}
            if(text.startsWith("UPDATE rule_release")){var row=roots.getFirst();assertEquals(row[8],p.get(8));row[3]=p.get(1);row[4]=p.get(2);row[5]=p.get(3);row[6]=null;row[8]=p.get(4);return;}
            if(text.startsWith("INSERT INTO rule_package_installation_partition")){partitions.add(values(p,3));return;}
            if(text.startsWith("INSERT INTO rule_package_installation")){Object[] row=Arrays.copyOf(values(p,11),12);row[11]=NOW;facts.add(row);return;}
            if(text.startsWith("UPDATE rule_installation_control")){assertEquals(control[2],p.get(3));assertEquals(control[3],p.get(4));control[2]=p.get(1);control[3]=p.get(2);return;}
            throw new AssertionError("Unexpected DML "+text);
        }
        static ResultSet result(List<Object[]> input,int max) {
            assertTrue(max>0);List<Object[]> rows=input.subList(0,Math.min(max,input.size()));int[] cursor={-1};int columns=rows.isEmpty()?1:rows.getFirst().length;
            return proxy(ResultSet.class,(p,m,a)->switch(m.getName()) {
                case "next"->++cursor[0]<rows.size();case "getObject"->rows.get(cursor[0])[(int)a[0]-1];case "close"->null;
                case "getMetaData"->proxy(ResultSetMetaData.class,(q,n,b)->{if(n.getName().equals("getColumnCount"))return columns;throw new AssertionError(n.getName());});
                default->throw new AssertionError("Unexpected result method "+m.getName());
            });
        }
        static Object[] values(Map<Integer,Object> map,int n){Object[] row=new Object[n];for(int i=0;i<n;i++)row[i]=map.get(i+1);return row;}
        static List<Object[]> one(Object[] row){List<Object[]> result=new ArrayList<>();result.add(row.clone());return result;}
        static List<Object[]> copy(List<Object[]> source){List<Object[]> copy=new ArrayList<>();for(var row:source)copy.add(row.clone());return copy;}
        @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    }
}
