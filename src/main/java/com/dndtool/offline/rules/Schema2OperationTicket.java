package com.dndtool.offline.rules;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable exact original intent; never an assertion that installation or DM acceptance committed. */
public record Schema2OperationTicket(UUID operationId, Kind kind, String target, String lineage,
        long expectedVersion, long expectedInstallations, long expectedAcceptances,
        String moduleKey, String releaseVersion, String role, String canonicalProfile,
        String rawZipSha256, String logicalSha256, String installedOperationId,
        String installedOperationDigest, long installationSequence, String reviewSha256) {
    public enum Kind { INSTALL, ACCEPT }
    public Schema2OperationTicket {
        if(operationId==null || operationId.version()!=4 || operationId.variant()!=2 || kind==null
                || expectedVersion<0 || expectedInstallations<0 || expectedInstallations>JdbcSchema2RuleSource.MAX_ARTIFACTS
                || expectedAcceptances<0 || expectedAcceptances>expectedInstallations
                || expectedVersion!=Math.addExact(expectedInstallations,expectedAcceptances))throw OfflineJson.bad();
        OperationTicket.token(target);OperationTicket.token(lineage);
        if(moduleKey==null || !moduleKey.matches("[a-z][a-z0-9_]{0,63}")
                || releaseVersion==null || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")
                || !Set.of("BASE","EXTENSION","COMPATIBILITY").contains(role)
                || !ProductionRuleArtifact.CANONICAL_PROFILE.equals(canonicalProfile))throw OfflineJson.bad();
        RuleArtifact.digestText(rawZipSha256);RuleArtifact.digestText(logicalSha256);
        if(kind==Kind.INSTALL) {
            if(!"none".equals(installedOperationId) || !"none".equals(installedOperationDigest)
                    || installationSequence!=0 || !"none".equals(reviewSha256))throw OfflineJson.bad();
        } else {
            UUID installed=UUID.fromString(installedOperationId);
            if(installed.version()!=4 || installed.variant()!=2 || !installed.toString().equals(installedOperationId)
                    || installationSequence<1 || installationSequence>expectedInstallations)throw OfflineJson.bad();
            RuleArtifact.digestText(installedOperationDigest);RuleArtifact.digestText(reviewSha256);
        }
    }
    static Schema2OperationTicket install(ProductionRuleArtifact artifact,JdbcSchema2RuleSource.Control control,MaintenanceEvidence evidence) {
        return new Schema2OperationTicket(UUID.randomUUID(),Kind.INSTALL,evidence.target(),evidence.lineage(),
                control.version(),control.installations(),control.acceptances(),artifact.moduleKey(),artifact.releaseVersion(),
                artifact.role().name(),artifact.canonicalProfile(),artifact.rawZipSha256(),artifact.logicalSha256(),"none","none",0,"none");
    }
    static Schema2OperationTicket accept(JdbcSchema2RuleSource.Installed installed,JdbcSchema2RuleSource.Control control,
            MaintenanceEvidence evidence,String reviewSha256) {
        return new Schema2OperationTicket(UUID.randomUUID(),Kind.ACCEPT,evidence.target(),evidence.lineage(),
                control.version(),control.installations(),control.acceptances(),installed.identity().moduleKey(),installed.identity().releaseVersion(),
                installed.role(),installed.canonicalProfile(),installed.rawZipSha256(),installed.logicalSha256(),
                installed.operationId().toString(),installed.operationDigest(),installed.sequence(),reviewSha256);
    }
    public String fingerprint() {return RuleArtifact.sha256(("DND_TOOL_SCHEMA2_SOURCE_OPERATION_V1\n"+intent()).getBytes(StandardCharsets.UTF_8));}
    private String intent() {
        return "{\"kind\":"+q(kind.name())+",\"target\":"+q(target)+",\"lineage\":"+q(lineage)
                +",\"expected_version\":"+expectedVersion+",\"expected_installations\":"+expectedInstallations
                +",\"expected_acceptances\":"+expectedAcceptances+",\"module_key\":"+q(moduleKey)
                +",\"release_version\":"+q(releaseVersion)+",\"role\":"+q(role)
                +",\"author_schema_version\":2,\"artifact_protocol_version\":1,\"canonical_profile\":"+q(canonicalProfile)
                +",\"raw_zip_sha256\":"+q(rawZipSha256)+",\"logical_sha256\":"+q(logicalSha256)
                +",\"installed_operation_id\":"+q(installedOperationId)+",\"installed_operation_digest\":"+q(installedOperationDigest)
                +",\"installation_sequence\":"+installationSequence+",\"review_protocol_version\":1,\"review_sha256\":"+q(reviewSha256)+"}";
    }
    byte[] encode() {
        return ("{\"ticket_version\":2,\"operation_id\":"+q(operationId.toString())+",\"fingerprint_version\":1,\"fingerprint\":"
                +q(fingerprint())+",\"intent\":"+intent()+"}\n").getBytes(StandardCharsets.UTF_8);
    }
    static Schema2OperationTicket decode(byte[] bytes) {
        var envelope=OfflineJson.object(OfflineJson.parse(bytes),"ticket_version","operation_id","fingerprint_version","fingerprint","intent");
        if(OfflineJson.number(envelope,"ticket_version")!=2 || OfflineJson.number(envelope,"fingerprint_version")!=1)throw OfflineJson.bad();
        var map=OfflineJson.object(envelope.get("intent"),"kind","target","lineage","expected_version","expected_installations","expected_acceptances",
                "module_key","release_version","role","author_schema_version","artifact_protocol_version","canonical_profile","raw_zip_sha256","logical_sha256",
                "installed_operation_id","installed_operation_digest","installation_sequence","review_protocol_version","review_sha256");
        if(OfflineJson.number(map,"author_schema_version")!=2 || OfflineJson.number(map,"artifact_protocol_version")!=1
                || OfflineJson.number(map,"review_protocol_version")!=1)throw OfflineJson.bad();
        String id=s(envelope,"operation_id");UUID uuid=UUID.fromString(id);if(!uuid.toString().equals(id))throw OfflineJson.bad();
        var ticket=new Schema2OperationTicket(uuid,Kind.valueOf(s(map,"kind")),s(map,"target"),s(map,"lineage"),
                n(map,"expected_version"),n(map,"expected_installations"),n(map,"expected_acceptances"),s(map,"module_key"),s(map,"release_version"),
                s(map,"role"),s(map,"canonical_profile"),s(map,"raw_zip_sha256"),s(map,"logical_sha256"),s(map,"installed_operation_id"),
                s(map,"installed_operation_digest"),n(map,"installation_sequence"),s(map,"review_sha256"));
        if(!ticket.fingerprint().equals(s(envelope,"fingerprint")))throw OfflineJson.bad();return ticket;
    }
    boolean matches(ProductionRuleArtifact artifact) {
        return moduleKey.equals(artifact.moduleKey())&&releaseVersion.equals(artifact.releaseVersion())&&role.equals(artifact.role().name())
                &&canonicalProfile.equals(artifact.canonicalProfile())&&rawZipSha256.equals(artifact.rawZipSha256())&&logicalSha256.equals(artifact.logicalSha256());
    }
    private static String q(String text){return OfflineJson.quote(text);}
    private static String s(Map<String,Object> map,String key){return OfflineJson.string(map,key);}
    private static long n(Map<String,Object> map,String key){return OfflineJson.number(map,key);}
}
