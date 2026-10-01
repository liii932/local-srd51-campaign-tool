package com.dndtool.offline.rules;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/** Synchronous, single-owner offline transaction coordinator; never attached to an application pool. */
public final class SourceInstallation {
    public enum Status { NOT_EXECUTED, ROLLED_BACK, COMMITTED, UNKNOWN, NOT_COMMITTED, CONFLICT }
    public record Acceptance(UUID operationId,String fingerprint,String moduleKey,String releaseVersion,long revision,
                             String scope,List<String> partitions,String manifestSha256,String packageDisplayName,
                             String observedContentSha256,java.time.LocalDateTime installedAt) { }
    public record Result(Status status,Long acceptedRevision,Long currentRevision,String currentStatus,String detail,Acceptance acceptance) { }
    @FunctionalInterface interface Connections {Connection open()throws SQLException;}
    private final MaintenanceEvidence evidence;
    private final TicketStore tickets;
    private final Connections connections;
    /** The credential is obtained externally, never from an artifact or ticket. */
    public SourceInstallation(MaintenanceEvidence evidence,TicketStore tickets,char[] password)throws IOException {
        this(evidence,tickets,()->{
            Properties properties=new Properties();properties.setProperty("user",evidence.user());properties.setProperty("password",new String(password));
            properties.setProperty("autoReconnect","false");properties.setProperty("allowMultiQueries","false");
            properties.setProperty("allowLoadLocalInfile","false");properties.setProperty("allowUrlInLocalInfile","false");
            properties.setProperty("connectionTimeZone","UTC");properties.setProperty("forceConnectionTimeZoneToSession","true");
            properties.setProperty("characterEncoding","UTF-8");properties.setProperty("connectTimeout","5000");properties.setProperty("socketTimeout","15000");
            properties.setProperty("useAffectedRows","true");
            return DriverManager.getConnection(evidence.url(),properties);
        });
    }
    SourceInstallation(MaintenanceEvidence evidence,TicketStore tickets,Connections connections)throws IOException {
        this.evidence=evidence;this.tickets=tickets;this.connections=connections;tickets.bind(evidence);
    }
    /** No source DML; preparation observes an optimistic revision, then creates and reads back a ticket. */
    public OperationTicket prepare(RuleArtifact artifact)throws SQLException,IOException {
        evidence.requireInstall();tickets.requireClear();long expected;
        try(Connection connection=connections.open()) {
            if(!connection.getAutoCommit())throw new SQLException("Dedicated fresh connection required");
            var source=new JdbcRuleSource(connection);source.preflight(evidence);expected=source.expectedRevision();
        }
        OperationTicket ticket=OperationTicket.create(artifact,expected,evidence);tickets.save(ticket);return ticket;
    }
    public Result install(OperationTicket ticket,RuleArtifact artifact)throws IOException {
        evidence.requireInstall();evidence.match(ticket);tickets.requireClear();
        if(!ticket.profile().equals("srd51-character-catalog") || !ticket.equals(tickets.load(ticket.operationId()+".json")) || !ticket.manifestSha256().equals(artifact.manifestSha256())
                || !ticket.fingerprint().equals(InstallationFingerprint.digest(artifact,ticket.expectedRevision())))
            return result(Status.CONFLICT,null,null,"Original input does not match ticket");
        tickets.begin(ticket);return transaction(ticket,artifact,false);
    }
    /** Explicit original-intent lookup. Requires independently attested isolation; consumes no artifact. */
    public Result resolve(OperationTicket ticket)throws IOException {
        evidence.requireResolution(ticket);
        if(!ticket.equals(tickets.load(ticket.operationId()+".json")))throw new IOException("Original reliable ticket required");
        OperationTicket pending=tickets.pending();if(pending!=null&&!pending.equals(ticket))throw new IOException("Another operation remains unresolved");
        // If no marker exists, keep one before entering the barrier so a crash cannot silently clear maintenance.
        if(pending==null)tickets.begin(ticket);
        return transaction(ticket,null,true);
    }
    private Result transaction(OperationTicket ticket,RuleArtifact artifact,boolean resolution)throws IOException {
        Connection connection=null;boolean transaction=false,commitCalled=false,releaseCalled=false,closed=false;
        Result outcome=result(Status.NOT_EXECUTED,null,null,"No source write executed");
        try {
            connection=connections.open();
            if(!connection.getAutoCommit())throw new SQLException("Dedicated fresh connection required");
            var source=new JdbcRuleSource(connection);source.preflight(evidence);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            transaction=true;connection.setAutoCommit(false);
            // No installation business SQL precedes the permanent S lock in this transaction.
            var control=source.lock();
            var state=source.read(control,evidence.minimumVersion(),!resolution);
            var accepted=state.operations().get(ticket.operationId());
            if(accepted!=null) {
                var root=state.roots().get(accepted.releaseId());
                if(!accepted.fingerprint().equals(ticket.fingerprint()) || !root.isTarget()
                        || accepted.revision()!=ticket.expectedRevision()+1 || !accepted.manifest().equals(ticket.manifestSha256())
                        || accepted.authorVersion()!=1 || accepted.manifestVersion()!=1
                        || !accepted.scope().equals("PARTITION") || accepted.observed()!=null)
                    outcome=result(Status.CONFLICT,null,root,"Permanent operation identity conflict");
                else outcome=accepted(accepted,root,state,"Original immutable acceptance; current COMPLETE content, if any, is outside this profile");
                // Read-only replay/resolve releases S using rollback, not a commit or DML.
                releaseCalled=true;connection.rollback();transaction=false;
            } else if(resolution) {
                releaseCalled=true;connection.rollback();transaction=false;
                outcome=result(Status.NOT_COMMITTED,null,state.target(),"No acceptance in independently attested continuous permanent history");
            } else {
                var installed=source.install(state,ticket,artifact);
                commitCalled=true;connection.commit();transaction=false;
                outcome=accepted(installed.fact(),installed.root(),null,"Language/tool partitions committed; no full content digest or release approval");
            }
        } catch(Exception failure) {
            if(commitCalled||releaseCalled)outcome=result(Status.UNKNOWN,null,null,"Transaction completion uncertain; explicit resolution required");
            else if(transaction) {
                try {connection.rollback();transaction=false;outcome=result(Status.ROLLED_BACK,null,null,"Complete rollback acknowledged; attempt rejected");}
                catch(Exception rollbackFailure) {outcome=result(Status.UNKNOWN,null,null,"Rollback uncertain; explicit resolution required");}
            } else outcome=result(Status.NOT_EXECUTED,null,null,"Preflight failed before source transaction");
            // Ending this new read-only transaction says nothing about the original attempt.
            if(resolution)outcome=result(Status.UNKNOWN,null,null,"Resolution incomplete; original outcome remains unknown");
        } finally {
            if(connection!=null)try{connection.close();closed=true;}catch(Exception closeFailure){/* Dedicated connection is never pooled or reused. */}
            else closed=true;
        }
        // After acknowledged commit a close/journal/output failure never changes the accepted classification.
        // Retain the marker when close is uncertain. No connection-state restoration enables a late commit.
        if(outcome.status()!=Status.UNKNOWN && outcome.status()!=Status.CONFLICT && closed) {
            try{tickets.resolved(ticket);}catch(Exception journalFailure) {
                return new Result(outcome.status(),outcome.acceptedRevision(),outcome.currentRevision(),outcome.currentStatus(),outcome.detail()+"; maintenance marker requires operator resolution",outcome.acceptance());
            }
        }
        if(!closed)return new Result(outcome.status(),outcome.acceptedRevision(),outcome.currentRevision(),outcome.currentStatus(),
                outcome.detail()+"; connection close uncertain, maintenance marker retained",outcome.acceptance());
        return outcome;
    }
    private static Result result(Status status,Long accepted,JdbcRuleSource.Root root,String detail) {
        return new Result(status,accepted,root==null?null:root.revision(),root==null?null:root.status(),detail,null);
    }
    private static Result accepted(JdbcRuleSource.Fact fact,JdbcRuleSource.Root root,JdbcRuleSource.State state,String detail) {
        List<String> partitions=state==null?List.of("character.language","character.tool"):
                state.partitions().stream().filter(p->p.revision().equals(new JdbcRuleSource.Revision(fact.releaseId(),fact.revision())))
                        .map(JdbcRuleSource.Partition::key).sorted().toList();
        var acceptance=new Acceptance(fact.operation(),fact.fingerprint(),root.key(),root.release(),fact.revision(),fact.scope(),
                partitions,fact.manifest(),fact.name(),fact.observed(),fact.installedAt());
        return new Result(Status.COMMITTED,fact.revision(),root.revision(),root.status(),detail,acceptance);
    }
}
