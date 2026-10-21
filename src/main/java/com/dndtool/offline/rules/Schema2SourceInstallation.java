package com.dndtool.offline.rules;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/** Dedicated offline install/DM-accept coordinator; repositories never own transaction completion. */
public final class Schema2SourceInstallation {
    public enum Status { NOT_EXECUTED, ROLLED_BACK, COMMITTED, UNKNOWN, NOT_COMMITTED, CONFLICT }
    public record Result(Status status,String code,UUID operationId,Long factSequence,boolean acceptanceFact,
                         String moduleKey,String releaseVersion,String rawZipSha256,String logicalSha256,String detail) { }
    public record Preparation(Schema2OperationTicket ticket,Result existing) { }
    @FunctionalInterface interface Connections {Connection open()throws SQLException;}
    @FunctionalInterface interface Ledgers {Ledger open(Connection connection);}
    interface Ledger {
        void preflight(MaintenanceEvidence evidence)throws SQLException;
        JdbcSchema2RuleSource.Control lock()throws SQLException;
        JdbcSchema2RuleSource.State read(JdbcSchema2RuleSource.Control control,MaintenanceEvidence evidence)throws SQLException;
        JdbcSchema2RuleSource.Installed install(JdbcSchema2RuleSource.State before,Schema2OperationTicket ticket,ProductionRuleArtifact artifact)throws SQLException;
        JdbcSchema2RuleSource.Accepted accept(JdbcSchema2RuleSource.State before,Schema2OperationTicket ticket,JdbcSchema2RuleSource.Installed installed)throws SQLException;
    }
    private final MaintenanceEvidence evidence;
    private final TicketStore tickets;
    private final Connections connections;
    private final Ledgers ledgers;

    public Schema2SourceInstallation(MaintenanceEvidence evidence,TicketStore tickets,char[] password)throws IOException {
        this(evidence,tickets,()->connect(evidence,password));
    }
    Schema2SourceInstallation(MaintenanceEvidence evidence,TicketStore tickets,Connections connections)throws IOException {
        this(evidence,tickets,connections,Schema2SourceInstallation::jdbcLedger);
    }
    private static Ledger jdbcLedger(Connection connection) {
        return new Ledger() {
            final JdbcRuleSource preflight=new JdbcRuleSource(connection);
            final JdbcSchema2RuleSource source=new JdbcSchema2RuleSource(connection);
            public void preflight(MaintenanceEvidence input)throws SQLException {preflight.preflight(input);}
            public JdbcSchema2RuleSource.Control lock()throws SQLException {return source.lock();}
            public JdbcSchema2RuleSource.State read(JdbcSchema2RuleSource.Control control,MaintenanceEvidence input)throws SQLException {
                source.validateLegacyCheckpoint(input.minimumVersion());
                return source.read(control,input.schema2MinimumVersion());
            }
            public JdbcSchema2RuleSource.Installed install(JdbcSchema2RuleSource.State state,Schema2OperationTicket ticket,ProductionRuleArtifact artifact)throws SQLException {
                return source.install(state,ticket.operationId(),ticket.fingerprint(),artifact);
            }
            public JdbcSchema2RuleSource.Accepted accept(JdbcSchema2RuleSource.State state,Schema2OperationTicket ticket,JdbcSchema2RuleSource.Installed installed)throws SQLException {
                return source.accept(state,ticket.operationId(),ticket.fingerprint(),installed,ticket.reviewSha256());
            }
        };
    }
    Schema2SourceInstallation(MaintenanceEvidence evidence,TicketStore tickets,Connections connections,Ledgers ledgers)throws IOException {
        this.evidence=evidence;this.tickets=tickets;this.connections=connections;this.ledgers=ledgers;tickets.bind(evidence);
    }
    public Preparation prepareInstall(ProductionRuleArtifact artifact)throws SQLException,IOException {
        evidence.requireSchema2Install();tickets.requireClear();Schema2InstallationEligibility.requireEligible(artifact);
        JdbcSchema2RuleSource.State state=observe();
        var prior=state.installations().get(identity(artifact));
        if(prior!=null) {
            if(!same(prior,artifact))throw new IllegalStateException("S2_VERSION_CONFLICT");
            return new Preparation(null,installed(prior,"Original exact installation; no new operation or source DML"));
        }
        var ticket=Schema2OperationTicket.install(artifact,state.control(),evidence);tickets.save(ticket);return new Preparation(ticket,null);
    }
    /** The review is a bounded full-content export, durably read back before the original ticket exists. */
    public Preparation prepareAccept(String moduleKey,String releaseVersion)throws SQLException,IOException {
        evidence.requireAccept();tickets.requireClear();JdbcSchema2RuleSource.State state=observe();
        var identity=new JdbcSchema2RuleSource.Identity(moduleKey,releaseVersion);
        var installed=state.installations().get(identity);if(installed==null)throw new IllegalStateException("S2_NOT_INSTALLED");
        Schema2InstallationEligibility.requireEligible(installed.artifact());
        var prior=state.acceptances().get(identity);
        if(prior!=null)return new Preparation(null,accepted(prior,"Original exact local DM acceptance; no new operation or source DML"));
        byte[] review=Schema2Review.create(evidence,state.control(),installed);
        var ticket=Schema2OperationTicket.accept(installed,state.control(),evidence,RuleArtifact.sha256(review));
        tickets.saveReview(ticket,review,installed.artifact().originalZip());tickets.save(ticket);return new Preparation(ticket,null);
    }
    private JdbcSchema2RuleSource.State observe()throws SQLException {
        try(Connection connection=connections.open()) {
            fresh(connection);var ledger=ledgers.open(connection);ledger.preflight(evidence);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);connection.setAutoCommit(false);
            try {var state=ledger.read(ledger.lock(),evidence);connection.rollback();return state;}
            catch(SQLException|RuntimeException failure) {try{connection.rollback();}catch(SQLException release){failure.addSuppressed(release);}throw failure;}
        }
    }
    public Result install(Schema2OperationTicket ticket,ProductionRuleArtifact artifact)throws IOException {
        evidence.requireSchema2Install();evidence.match(ticket);tickets.requireClear();
        if(ticket.kind()!=Schema2OperationTicket.Kind.INSTALL || !saved(ticket) || !ticket.matches(artifact))
            return failure(Status.CONFLICT,"S2_TICKET_MISMATCH","Original exact artifact does not match reliable ticket");
        Schema2InstallationEligibility.requireEligible(artifact);tickets.begin(ticket);return transaction(ticket,artifact,false);
    }
    public Result accept(Schema2OperationTicket ticket,String explicitlyReviewedSha256)throws IOException {
        evidence.requireAccept();evidence.match(ticket);tickets.requireClear();
        if(ticket.kind()!=Schema2OperationTicket.Kind.ACCEPT || !saved(ticket) || !ticket.reviewSha256().equals(explicitlyReviewedSha256)
                || !ticket.reviewSha256().equals(RuleArtifact.sha256(tickets.review(ticket)))
                || !ticket.rawZipSha256().equals(RuleArtifact.sha256(tickets.reviewZip(ticket))))
            return failure(Status.CONFLICT,"S2_REVIEW_MISMATCH","Explicit exact content review does not match reliable ticket");
        tickets.begin(ticket);return transaction(ticket,null,false);
    }
    public Result resolve(Schema2OperationTicket ticket)throws IOException {
        evidence.requireResolution(ticket);if(!saved(ticket))throw new IOException("Original reliable ticket required");
        var pending=tickets.pendingSchema2();
        if(pending!=null&&!pending.equals(ticket))throw new IOException("Another operation remains unresolved");
        if(pending==null)tickets.begin(ticket);return transaction(ticket,null,true);
    }
    private Result transaction(Schema2OperationTicket ticket,ProductionRuleArtifact artifact,boolean resolution)throws IOException {
        Connection connection=null;boolean transaction=false,completionCalled=false,closed=false;
        Result outcome=failure(Status.NOT_EXECUTED,"S2_PREFLIGHT","No source write executed");
        try {
            connection=connections.open();fresh(connection);var source=ledgers.open(connection);source.preflight(evidence);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);transaction=true;connection.setAutoCommit(false);
            var state=source.read(source.lock(),evidence);
            var install=state.installationOperations().get(ticket.operationId());var acceptance=state.acceptanceOperations().get(ticket.operationId());
            if(install!=null||acceptance!=null) {
                if(ticket.kind()==Schema2OperationTicket.Kind.INSTALL && install!=null
                        && install.operationDigest().equals(ticket.fingerprint()) && ticket.matches(install.artifact())
                        && install.sequence()==ticket.expectedInstallations()+1)
                    outcome=installed(install,"Original immutable installation; read-only replay");
                else if(ticket.kind()==Schema2OperationTicket.Kind.ACCEPT && acceptance!=null && acceptedMatches(ticket,acceptance))
                    outcome=accepted(acceptance,"Original immutable local acceptance; read-only replay");
                else outcome=failure(Status.CONFLICT,"S2_OPERATION_CONFLICT","Permanent original operation differs from reliable intent");
                completionCalled=true;connection.rollback();transaction=false;
            } else if(resolution) {
                completionCalled=true;connection.rollback();transaction=false;
                outcome=failure(Status.NOT_COMMITTED,"S2_NOT_COMMITTED","Original UUID absent from independently attested complete permanent history");
            } else {
                if(state.control().version()!=ticket.expectedVersion() || state.control().installations()!=ticket.expectedInstallations()
                        || state.control().acceptances()!=ticket.expectedAcceptances())throw new IllegalStateException("S2_STALE_SOURCE");
                if(ticket.kind()==Schema2OperationTicket.Kind.INSTALL) {
                    Schema2InstallationEligibility.requireEligible(artifact);
                    if(state.installations().containsKey(identity(artifact)))throw new IllegalStateException("S2_VERSION_CONFLICT");
                    var fact=source.install(state,ticket,artifact);
                    outcome=installed(fact,"Exact original schema 2 artifact installed; no DM acceptance or runtime qualification");
                } else {
                    var fact=state.installations().get(new JdbcSchema2RuleSource.Identity(ticket.moduleKey(),ticket.releaseVersion()));
                    if(fact==null || !ticket.matches(fact.artifact()) || !ticket.installedOperationId().equals(fact.operationId().toString())
                            || !ticket.installedOperationDigest().equals(fact.operationDigest()) || ticket.installationSequence()!=fact.sequence())
                        throw new IllegalStateException("S2_INSTALLATION_MISMATCH");
                    Schema2InstallationEligibility.requireEligible(fact.artifact());
                    if(!Arrays.equals(tickets.review(ticket),Schema2Review.create(evidence,state.control(),fact))
                            || !Arrays.equals(tickets.reviewZip(ticket),fact.artifact().originalZip()))throw new IllegalStateException("S2_REVIEW_MISMATCH");
                    if(state.acceptances().containsKey(fact.identity()))throw new IllegalStateException("S2_ACCEPTANCE_CONFLICT");
                    outcome=accepted(source.accept(state,ticket,fact),"Exact reviewed original content locally accepted; no runtime or complete-base qualification");
                }
                completionCalled=true;connection.commit();transaction=false;
            }
        } catch(Exception failure) {
            String code=code(failure);
            if(completionCalled)outcome=failure(Status.UNKNOWN,code,"Transaction completion uncertain; explicit original-intent resolution required");
            else if(transaction) {
                try {connection.rollback();transaction=false;outcome=failure(Status.ROLLED_BACK,code,"Complete rollback acknowledged; no partial source facts");}
                catch(Exception rollbackFailure){outcome=failure(Status.UNKNOWN,code,"Rollback uncertain; explicit original-intent resolution required");}
            } else outcome=failure(Status.NOT_EXECUTED,code,"Preflight failed before source transaction");
            if(resolution)outcome=failure(Status.UNKNOWN,code,"Original outcome remains unknown; resolution did not complete");
        } finally {
            if(connection!=null)try {connection.close();closed=true;}catch(Exception closeFailure){/* Never pooled or restored. */}
            else closed=true;
        }
        if(outcome.status()!=Status.UNKNOWN && outcome.status()!=Status.CONFLICT && closed) {
            try {tickets.resolved(ticket);}catch(Exception journalFailure){return detail(outcome,"; maintenance marker retained for explicit resolution");}
        }
        return closed?outcome:detail(outcome,"; connection close uncertain, maintenance marker retained");
    }
    private boolean saved(Schema2OperationTicket ticket)throws IOException {return ticket.equals(tickets.loadSchema2(ticket.operationId()+".json"));}
    private static void fresh(Connection connection)throws SQLException {if(!connection.getAutoCommit())throw new SQLException("Dedicated fresh connection required");}
    private static JdbcSchema2RuleSource.Identity identity(ProductionRuleArtifact artifact){return new JdbcSchema2RuleSource.Identity(artifact.moduleKey(),artifact.releaseVersion());}
    private static boolean same(JdbcSchema2RuleSource.Installed installed,ProductionRuleArtifact artifact) {
        return installed.identity().equals(identity(artifact))&&installed.rawZipSha256().equals(artifact.rawZipSha256())
                &&installed.logicalSha256().equals(artifact.logicalSha256())&&installed.role().equals(artifact.role().name())
                &&installed.canonicalProfile().equals(artifact.canonicalProfile())&&Arrays.equals(installed.artifact().originalZip(),artifact.originalZip());
    }
    private static boolean acceptedMatches(Schema2OperationTicket t,JdbcSchema2RuleSource.Accepted a) {
        return a.operationDigest().equals(t.fingerprint())&&a.sequence()==t.expectedAcceptances()+1
                &&a.identity().equals(new JdbcSchema2RuleSource.Identity(t.moduleKey(),t.releaseVersion()))
                &&a.installedOperationId().toString().equals(t.installedOperationId())&&a.installedOperationDigest().equals(t.installedOperationDigest())
                &&a.installationSequence()==t.installationSequence()&&a.canonicalProfile().equals(t.canonicalProfile())&&a.role().equals(t.role())
                &&a.rawZipSha256().equals(t.rawZipSha256())&&a.logicalSha256().equals(t.logicalSha256())&&a.reviewSha256().equals(t.reviewSha256());
    }
    private static Result installed(JdbcSchema2RuleSource.Installed fact,String detail) {
        return new Result(Status.COMMITTED,"S2_INSTALLED",fact.operationId(),fact.sequence(),false,fact.identity().moduleKey(),fact.identity().releaseVersion(),fact.rawZipSha256(),fact.logicalSha256(),detail);
    }
    private static Result accepted(JdbcSchema2RuleSource.Accepted fact,String detail) {
        return new Result(Status.COMMITTED,"S2_ACCEPTED",fact.operationId(),fact.sequence(),true,fact.identity().moduleKey(),fact.identity().releaseVersion(),fact.rawZipSha256(),fact.logicalSha256(),detail);
    }
    private static Result failure(Status status,String code,String detail) {return new Result(status,code,null,null,false,null,null,null,null,detail);}
    private static Result detail(Result result,String suffix){return new Result(result.status(),result.code(),result.operationId(),result.factSequence(),result.acceptanceFact(),result.moduleKey(),result.releaseVersion(),result.rawZipSha256(),result.logicalSha256(),result.detail()+suffix);}
    private static String code(Exception failure) {
        if(failure instanceof Schema2Exception invalid)return invalid.diagnostic().code();
        if(failure instanceof JdbcSchema2RuleSource.SourceException invalid)return invalid.code();
        return failure.getMessage()!=null&&failure.getMessage().matches("S2_[A-Z_]+")?failure.getMessage():"S2_SOURCE_FAILURE";
    }
    private static Connection connect(MaintenanceEvidence evidence,char[] password)throws SQLException {
        Properties properties=new Properties();properties.setProperty("user",evidence.user());properties.setProperty("password",new String(password));
        properties.setProperty("autoReconnect","false");properties.setProperty("allowMultiQueries","false");properties.setProperty("allowLoadLocalInfile","false");
        properties.setProperty("allowUrlInLocalInfile","false");properties.setProperty("connectionTimeZone","UTC");properties.setProperty("forceConnectionTimeZoneToSession","true");
        properties.setProperty("characterEncoding","UTF-8");properties.setProperty("connectTimeout","5000");properties.setProperty("socketTimeout","15000");
        properties.setProperty("useAffectedRows","true");return DriverManager.getConnection(evidence.url(),properties);
    }
}
