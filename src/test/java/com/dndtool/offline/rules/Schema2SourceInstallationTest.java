package com.dndtool.offline.rules;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real coordinator + SQL adapter with an in-memory JDBC protocol peer; no real DB claims. */
class Schema2SourceInstallationTest {
    @TempDir Path temp;
    private Schema2SourceJdbcFixture db;
    private Path state;
    private TicketStore tickets;
    private MaintenanceEvidence installer,accepter;
    private ProductionRuleArtifact artifact;
    private boolean closeFails,preflightFails;
    @BeforeEach void setup()throws Exception {
        db=new Schema2SourceJdbcFixture();state=OfflineTestSupport.privateDirectory(temp,"state");tickets=new TicketStore(state);
        installer=Schema2OfflineTestSupport.evidence(temp,state,"INSTALL",null);accepter=Schema2OfflineTestSupport.evidence(temp,state,"ACCEPT",null);
        artifact=Schema2OfflineTestSupport.empty("empty_extension","01");
    }
    @AfterEach void close()throws Exception {tickets.close();}
    @Test void productionCoordinatorFactoryIncludesRealPreflightAndBothSourceLocks()throws Exception {
        var preflight=new SourceInstallationTest.Database();var businessConnection=db.open();
        var calls=new ArrayList<String>();
        Connection unified=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement")) {
                String sql=(String)args[0];calls.add(sql);
                if(sql.startsWith("SELECT CAST(DATABASE()")||sql.equals("SHOW GRANTS")||sql.contains("FROM rule_schema_meta"))return preflight.statement(sql);
            }
            try{return method.invoke(businessConnection,args);}catch(InvocationTargetException failure){throw failure.getCause();}
        });
        // This test does not inject a Ledger: the production constructor executes real preflight + SQL repository.
        var service=new Schema2SourceInstallation(installer,tickets,()->unified);
        var ticket=Schema2OperationTicket.install(artifact,new JdbcSchema2RuleSource.Control(1,0,0,0,0),installer);tickets.save(ticket);
        assertEquals(Schema2SourceInstallation.Status.COMMITTED,service.install(ticket,artifact).status());
        assertEquals(2,db.writes);assertEquals(1,db.commits);
        assertTrue(calls.stream().anyMatch(sql->sql.equals("SHOW GRANTS")));
        int shared=index(calls,"FROM rule_installation_control WHERE");int schema2=index(calls,"FROM rule_schema2_control WHERE");
        assertTrue(shared>=0&&schema2>shared);assertNull(tickets.pendingSchema2());
    }
    @Test void exactOriginalContentInstallationAndSeparateExplicitAcceptance()throws Exception {
        var service=service(installer);var prepared=service.prepareInstall(artifact);var ticket=prepared.ticket();
        assertEquals(0,db.writes);assertEquals(ticket,tickets.loadSchema2(ticket.operationId()+".json"));assertNull(tickets.pendingSchema2());
        assertEquals(Schema2SourceInstallation.Status.COMMITTED,service.install(ticket,artifact).status());assertEquals(2,db.writes);assertEquals(1,db.commits);
        assertTrue(db.acceptances.isEmpty());assertNull(tickets.pendingSchema2());
        assertThrows(Exception.class,()->service.prepareAccept("empty_extension","01"));
        var review=service(accepter).prepareAccept("empty_extension","01").ticket();
        String text=Files.readString(tickets.reviewPath(review));
        assertTrue(text.contains("Complete parsed logical declarations"));assertTrue(text.contains("A license for actual review"));
        assertTrue(text.contains("\"kind\":\"definitions\"")||text.contains("\\\"kind\\\":\\\"definitions\\\""));
        assertArrayEquals(artifact.originalZip(),tickets.reviewZip(review));assertEquals(RuleArtifact.sha256(tickets.review(review)),review.reviewSha256());
        assertThrows(Exception.class,()->service.accept(review,review.reviewSha256()));
        int writes=db.writes;var wrong=service(accepter).accept(review,"a".repeat(64));assertEquals("S2_REVIEW_MISMATCH",wrong.code());assertEquals(writes,db.writes);
        assertNull(tickets.pendingSchema2());var accepted=service(accepter).accept(review,review.reviewSha256());
        assertEquals(Schema2SourceInstallation.Status.COMMITTED,accepted.status());assertTrue(accepted.acceptanceFact());assertEquals(4,db.writes);
        assertEquals(review.operationId(),accepted.operationId());assertEquals(1,db.artifacts.size());assertEquals(1,db.acceptances.size());
        assertNull(tickets.pendingSchema2());
    }
    @Test void exactReimportReusesOriginalUuidWhileSameVersionRepackRefuses()throws Exception {
        var service=service(installer);var original=service.prepareInstall(artifact).ticket();service.install(original,artifact);int writes=db.writes;
        var again=service.prepareInstall(artifact);assertNull(again.ticket());assertEquals(original.operationId(),again.existing().operationId());assertEquals(writes,db.writes);
        var repacked=Schema2OfflineTestSupport.artifact("empty_extension","01","extension","[]",true);
        assertEquals(artifact.logicalSha256(),repacked.logicalSha256());assertNotEquals(artifact.rawZipSha256(),repacked.rawZipSha256());
        assertEquals("S2_VERSION_CONFLICT",assertThrows(IllegalStateException.class,()->service.prepareInstall(repacked)).getMessage());
        var review=service(accepter).prepareAccept("empty_extension","01").ticket();service(accepter).accept(review,review.reviewSha256());writes=db.writes;
        var accepted=service(accepter).prepareAccept("empty_extension","01");assertNull(accepted.ticket());assertEquals(review.operationId(),accepted.existing().operationId());assertEquals(writes,db.writes);
        assertEquals(Schema2SourceInstallation.Status.COMMITTED,service.install(original,artifact).status());assertEquals(writes,db.writes);
    }
    @Test void incompleteBaseAndNonIntegratedMechanismRejectBeforeAnySourceAccess()throws Exception {
        var base=Schema2OfflineTestSupport.artifact("incomplete_base","1","base","[]",false);
        String definitions="[{\"identity\":{\"kind\":\"rule\",\"module_key\":\"content_extension\",\"rule_type\":\"character.language\",\"rule_key\":\"language\"},\"display_name\":\"Language\",\"description\":\"Content\",\"category\":\"STANDARD\",\"source_page\":3}]";
        var content=Schema2OfflineTestSupport.artifact("content_extension","1","extension",definitions,false);
        assertEquals("S2_BASE_INCOMPLETE",assertThrows(Schema2Exception.class,()->service(installer).prepareInstall(base)).diagnostic().code());
        assertEquals("S2_MECHANISM_UNSUPPORTED",assertThrows(Schema2Exception.class,()->service(installer).prepareInstall(content)).diagnostic().code());
        assertEquals(0,db.writes);assertTrue(db.queries.isEmpty());assertEquals(0,db.commits);assertNull(tickets.pendingSchema2());
    }
    @Test void sourceCounterChangeNeverRewritesExpectedIntent()throws Exception {
        var service=service(installer);var stale=service.prepareInstall(artifact).ticket();
        var second=Schema2OfflineTestSupport.empty("second_extension","1");var live=service.prepareInstall(second).ticket();service.install(live,second);int writes=db.writes;
        var result=service.install(stale,artifact);assertEquals(Schema2SourceInstallation.Status.ROLLED_BACK,result.status());assertEquals("S2_STALE_SOURCE",result.code());
        assertEquals(writes,db.writes);assertEquals(0,stale.expectedVersion());assertEquals(stale,tickets.loadSchema2(stale.operationId()+".json"));assertNull(tickets.pendingSchema2());
    }
    @Test void tamperedReadableReviewFailsBeforePendingOrSourceDml()throws Exception {
        var installation=service(installer).prepareInstall(artifact).ticket();service(installer).install(installation,artifact);
        var review=service(accepter).prepareAccept("empty_extension","01").ticket();int writes=db.writes;
        Files.writeString(tickets.reviewPath(review),"A different review");
        assertEquals("S2_REVIEW_MISMATCH",service(accepter).accept(review,review.reviewSha256()).code());assertEquals(writes,db.writes);assertNull(tickets.pendingSchema2());
    }
    @Test void selfConsistentForgedReviewCannotReplaceActualInstalledContentReview()throws Exception {
        var installation=service(installer).prepareInstall(artifact).ticket();service(installer).install(installation,artifact);
        var review=service(accepter).prepareAccept("empty_extension","01").ticket();byte[] other="A false self-consistent review".getBytes(StandardCharsets.UTF_8);
        var forged=new Schema2OperationTicket(UUID.randomUUID(),review.kind(),review.target(),review.lineage(),review.expectedVersion(),review.expectedInstallations(),review.expectedAcceptances(),
                review.moduleKey(),review.releaseVersion(),review.role(),review.canonicalProfile(),review.rawZipSha256(),review.logicalSha256(),review.installedOperationId(),review.installedOperationDigest(),review.installationSequence(),RuleArtifact.sha256(other));
        tickets.saveReview(forged,other,artifact.originalZip());tickets.save(forged);int writes=db.writes;
        var result=service(accepter).accept(forged,forged.reviewSha256());assertEquals(Schema2SourceInstallation.Status.ROLLED_BACK,result.status());assertEquals("S2_REVIEW_MISMATCH",result.code());assertEquals(writes,db.writes);assertTrue(db.acceptances.isEmpty());
    }
    @Test void everyInstallationWriteAndAffectedCountFailureRollsBackTheWholePostImage()throws Exception {
        for(boolean affected:List.of(false,true))for(int point=1;point<=2;point++) {
            var ticket=service(installer).prepareInstall(artifact).ticket();db.writes=0;
            if(affected)db.wrongAffectedWrite=point;else db.failWrite=point;
            assertEquals(Schema2SourceInstallation.Status.ROLLED_BACK,service(installer).install(ticket,artifact).status());
            assertTrue(db.artifacts.isEmpty());assertTrue(db.acceptances.isEmpty());assertEquals(0L,db.control.get("row_version"));assertNull(tickets.pendingSchema2());
            db.failWrite=db.wrongAffectedWrite=db.writes=0;
        }
    }
    @Test void everyAcceptanceWriteAndAffectedCountFailurePreservesInstallationAndRollsBackAcceptance()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();service(installer).install(original,artifact);
        for(boolean affected:List.of(false,true))for(int point=1;point<=2;point++) {
            var review=service(accepter).prepareAccept("empty_extension","01").ticket();db.writes=0;
            if(affected)db.wrongAffectedWrite=point;else db.failWrite=point;
            assertEquals(Schema2SourceInstallation.Status.ROLLED_BACK,service(accepter).accept(review,review.reviewSha256()).status());
            assertEquals(1,db.artifacts.size());assertTrue(db.acceptances.isEmpty());assertEquals(1L,db.control.get("row_version"));assertNull(tickets.pendingSchema2());
            db.failWrite=db.wrongAffectedWrite=db.writes=0;
        }
    }
    @Test void lostInstallCommitResponseRetainsOriginalMarkerUntilIndependentReadOnlyResolution()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();db.commitResponseLost=true;
        assertEquals(Schema2SourceInstallation.Status.UNKNOWN,service(installer).install(original,artifact).status());assertEquals(original,tickets.pendingSchema2());
        assertThrows(Exception.class,()->service(installer).prepareInstall(Schema2OfflineTestSupport.empty("another_extension","1")));
        assertThrows(Exception.class,()->service(installer).resolve(original));
        db.commitResponseLost=false;db.writes=0;int rollback=db.rollbacks;
        var resolver=Schema2OfflineTestSupport.evidence(temp,state,"RESOLVE",original.operationId());var result=service(resolver).resolve(original);
        assertEquals(Schema2SourceInstallation.Status.COMMITTED,result.status());assertEquals(original.operationId(),result.operationId());assertEquals(0,db.writes);assertEquals(rollback+1,db.rollbacks);assertNull(tickets.pendingSchema2());
    }
    @Test void lostAcceptanceCommitResolvesOriginalAcceptanceWithoutOldReviewFiles()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();service(installer).install(original,artifact);
        var review=service(accepter).prepareAccept("empty_extension","01").ticket();db.commitResponseLost=true;
        assertEquals(Schema2SourceInstallation.Status.UNKNOWN,service(accepter).accept(review,review.reviewSha256()).status());
        Files.delete(tickets.reviewPath(review));Files.delete(state.resolve(review.operationId()+".review.zip"));db.commitResponseLost=false;db.writes=0;
        var resolver=Schema2OfflineTestSupport.evidence(temp,state,"RESOLVE",review.operationId());var result=service(resolver).resolve(review);
        assertEquals(Schema2SourceInstallation.Status.COMMITTED,result.status());assertTrue(result.acceptanceFact());assertEquals(review.operationId(),result.operationId());assertEquals(0,db.writes);
    }
    @Test void rollbackFailureThenAbsenceRequiresIndependentIsolationAndCompleteHistory()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();db.failWrite=1;db.rollbackFails=true;
        assertEquals(Schema2SourceInstallation.Status.UNKNOWN,service(installer).install(original,artifact).status());assertEquals(original,tickets.pendingSchema2());
        db.failWrite=0;db.rollbackFails=false;db.writes=0;
        var resolver=Schema2OfflineTestSupport.evidence(temp,state,"RESOLVE",original.operationId());
        assertEquals(Schema2SourceInstallation.Status.NOT_COMMITTED,service(resolver).resolve(original).status());assertEquals(0,db.writes);assertNull(tickets.pendingSchema2());
    }
    @Test void acknowledgedCommitRetainsCommittedClassificationWhenCloseFails()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();closeFails=true;
        var result=service(installer).install(original,artifact);assertEquals(Schema2SourceInstallation.Status.COMMITTED,result.status());assertEquals(original,tickets.pendingSchema2());
        assertEquals(1,db.commits);assertEquals(1,db.artifacts.size());
    }
    @Test void unresolvedReleaseOrPreflightFailureNeverProvesOriginalAbsence()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();tickets.begin(original);
        var resolver=Schema2OfflineTestSupport.evidence(temp,state,"RESOLVE",original.operationId());db.rollbackFails=true;
        assertEquals(Schema2SourceInstallation.Status.UNKNOWN,service(resolver).resolve(original).status());assertEquals(original,tickets.pendingSchema2());
        db.rollbackFails=false;preflightFails=true;
        assertEquals(Schema2SourceInstallation.Status.UNKNOWN,service(resolver).resolve(original).status());assertEquals(original,tickets.pendingSchema2());assertEquals(0,db.writes);
    }
    @Test void sameUuidChangedPermanentIntentConflictsAndRetainsBarrier()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();service(installer).install(original,artifact);
        db.artifacts.getFirst().put("operation_digest_sha256",Schema2SourceJdbcFixture.b("f".repeat(64)));db.writes=0;
        var resolver=Schema2OfflineTestSupport.evidence(temp,state,"RESOLVE",original.operationId());
        assertEquals(Schema2SourceInstallation.Status.CONFLICT,service(resolver).resolve(original).status());assertEquals(original,tickets.pendingSchema2());assertEquals(0,db.writes);
    }
    @Test void preflightFailureBeforeSourceLockExecutesNoDmlAndClearsAcknowledgedAttempt()throws Exception {
        var original=service(installer).prepareInstall(artifact).ticket();db.queries.clear();preflightFails=true;
        assertEquals(Schema2SourceInstallation.Status.NOT_EXECUTED,service(installer).install(original,artifact).status());assertEquals(0,db.writes);assertTrue(db.queries.isEmpty());assertNull(tickets.pendingSchema2());
    }
    private Schema2SourceInstallation service(MaintenanceEvidence evidence)throws Exception {
        return new Schema2SourceInstallation(evidence,tickets,()->{
            Connection actual=db.open();
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try {Object result=method.invoke(actual,args);if(method.getName().equals("close")&&closeFails)throw new SQLException("close failed");return result;}
                catch(InvocationTargetException failure){throw failure.getCause();}
            });
        },connection->new Schema2SourceInstallation.Ledger() {
            final JdbcSchema2RuleSource source=new JdbcSchema2RuleSource(connection);
            public void preflight(MaintenanceEvidence ignored)throws SQLException {if(preflightFails)throw new SQLException("preflight failed");}
            public JdbcSchema2RuleSource.Control lock()throws SQLException {return source.lock();}
            public JdbcSchema2RuleSource.State read(JdbcSchema2RuleSource.Control control,MaintenanceEvidence input)throws SQLException {source.validateLegacyCheckpoint(input.minimumVersion());return source.read(control,input.schema2MinimumVersion());}
            public JdbcSchema2RuleSource.Installed install(JdbcSchema2RuleSource.State before,Schema2OperationTicket ticket,ProductionRuleArtifact input)throws SQLException {return source.install(before,ticket.operationId(),ticket.fingerprint(),input);}
            public JdbcSchema2RuleSource.Accepted accept(JdbcSchema2RuleSource.State before,Schema2OperationTicket ticket,JdbcSchema2RuleSource.Installed input)throws SQLException {return source.accept(before,ticket.operationId(),ticket.fingerprint(),input,ticket.reviewSha256());}
        });
    }
    private static int index(List<String> queries,String needle){for(int i=0;i<queries.size();i++)if(queries.get(i).contains(needle))return i;return -1;}
}
