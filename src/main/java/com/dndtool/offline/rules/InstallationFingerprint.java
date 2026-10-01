package com.dndtool.offline.rules;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** The CD-09 version-1, seventeen-field byte protocol. IDs/targets/local paths are excluded. */
public final class InstallationFingerprint {
    private InstallationFingerprint() { }
    public static String digest(RuleArtifact artifact,long expectedRevision) {return RuleArtifact.sha256(encode(artifact,expectedRevision));}
    public static byte[] encode(RuleArtifact artifact,long revision) {
        if(revision<0)throw OfflineJson.bad();
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            lp(out,"DND_TOOL_SE_SOURCE_INSTALLATION_REQUEST_V1");out.writeInt(1);out.writeShort(17);
            var h=artifact.author().header();
            field(out,1,1,"INSTALL");field(out,2,1,h.identity().moduleKey());field(out,3,1,h.identity().releaseVersion());
            number(out,4,revision);number(out,5,h.authorSchemaVersion());number(out,6,1);
            number(out,7,h.canonicalFormatVersion());number(out,8,h.archiveFormatVersion());field(out,9,1,h.hashAlgorithm());
            field(out,10,2,h.packageDisplayName());field(out,11,1,"PARTITION");field(out,12,0,new byte[0]);
            field(out,13,1,artifact.manifestSha256());field(out,14,1,"srd51-character-catalog");number(out,15,1);
            var mapping=new ByteArrayOutputStream();var map=new DataOutputStream(mapping);
            map.writeInt(2);lp(map,"character.language");lp(map,"character/languages.json");
            lp(map,"character.tool");lp(map,"character/tools.json");field(out,16,4,mapping.toByteArray());
            var files=new ByteArrayOutputStream();var f=new DataOutputStream(files);f.writeInt(artifact.files().size());
            for(var file:artifact.files()) {lp(f,file.path());lp(f,file.role());f.writeLong(file.byteLength());lp(f,file.rawSha256());}
            field(out,17,5,files.toByteArray());return bytes.toByteArray();
        } catch(IOException impossible){throw new AssertionError(impossible);}
    }
    private static void lp(DataOutputStream out,String text)throws IOException {byte[] b=text.getBytes(StandardCharsets.UTF_8);out.writeInt(b.length);out.write(b);}
    private static void number(DataOutputStream out,int index,long number)throws IOException {
        var b=new ByteArrayOutputStream();new DataOutputStream(b).writeLong(number);field(out,index,3,b.toByteArray());
    }
    private static void field(DataOutputStream out,int index,int tag,String text)throws IOException {field(out,index,tag,text.getBytes(StandardCharsets.UTF_8));}
    private static void field(DataOutputStream out,int index,int tag,byte[] payload)throws IOException {out.writeShort(index);out.writeByte(tag);out.writeInt(payload.length);out.write(payload);}
}
