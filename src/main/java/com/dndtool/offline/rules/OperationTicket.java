package com.dndtool.offline.rules;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Finite original intent. A ticket is not evidence that a database committed. */
public record OperationTicket(UUID operationId,String fingerprint,String target,String lineage,
                              long expectedRevision,String manifestSha256,String profile) {
    public OperationTicket(UUID operationId,String fingerprint,String target,String lineage,
                           long expectedRevision,String manifestSha256) {
        this(operationId,fingerprint,target,lineage,expectedRevision,manifestSha256,"srd51-character-catalog");
    }
    public OperationTicket {
        if(operationId==null || operationId.version()!=4 || operationId.variant()!=2 || expectedRevision<0)throw OfflineJson.bad();
        RuleArtifact.digestText(fingerprint);RuleArtifact.digestText(manifestSha256); token(target);token(lineage);
        if(!Set.of("srd51-language","srd51-character-catalog").contains(profile))throw OfflineJson.bad();
    }
    static OperationTicket create(RuleArtifact artifact,long expected,MaintenanceEvidence evidence) {
        String fingerprint=InstallationFingerprint.digest(artifact,expected);
        return new OperationTicket(UUID.randomUUID(),fingerprint,evidence.target(),evidence.lineage(),expected,artifact.manifestSha256());
    }
    public byte[] operationBytes() {return ByteBuffer.allocate(16).putLong(operationId.getMostSignificantBits()).putLong(operationId.getLeastSignificantBits()).array();}
    byte[] encode() {
        String json="{\"ticket_version\":1,\"source_operation_id\":"+OfflineJson.quote(operationId.toString())
            +",\"operation_fingerprint_version\":1,\"operation_digest_sha256\":"+OfflineJson.quote(fingerprint)
            +",\"module_key\":\"dnd5e2014_srd51_se\",\"release_version\":\"1\",\"expected_installation_revision\":"+expectedRevision
            +",\"installation_profile_key\":"+OfflineJson.quote(profile)+",\"installation_profile_version\":1,\"target\":"+OfflineJson.quote(target)
            +",\"lineage\":"+OfflineJson.quote(lineage)+",\"installation_manifest_sha256\":"+OfflineJson.quote(manifestSha256)+"}\n";
        return json.getBytes(StandardCharsets.UTF_8);
    }
    static OperationTicket decode(byte[] bytes) {
        var map=OfflineJson.object(OfflineJson.parse(bytes),"ticket_version","source_operation_id","operation_fingerprint_version",
                "operation_digest_sha256","module_key","release_version","expected_installation_revision","installation_profile_key",
                "installation_profile_version","target","lineage","installation_manifest_sha256");
        if(OfflineJson.number(map,"ticket_version")!=1 || OfflineJson.number(map,"operation_fingerprint_version")!=1
                || !OfflineJson.string(map,"module_key").equals("dnd5e2014_srd51_se") || !OfflineJson.string(map,"release_version").equals("1")
                || OfflineJson.number(map,"installation_profile_version")!=1)throw OfflineJson.bad();
        String id=OfflineJson.string(map,"source_operation_id");UUID uuid=UUID.fromString(id);
        if(!uuid.toString().equals(id))throw OfflineJson.bad();
        return new OperationTicket(uuid,OfflineJson.string(map,"operation_digest_sha256"),OfflineJson.string(map,"target"),
                OfflineJson.string(map,"lineage"),OfflineJson.number(map,"expected_installation_revision"),OfflineJson.string(map,"installation_manifest_sha256"),
                OfflineJson.string(map,"installation_profile_key"));
    }
    static void token(String text) {if(text==null || !text.matches("[a-z0-9][a-z0-9.-]{0,127}"))throw OfflineJson.bad();}
}
