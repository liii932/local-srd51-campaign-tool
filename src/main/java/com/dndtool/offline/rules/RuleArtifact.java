package com.dndtool.offline.rules;

import com.dndtool.module.LanguageAuthorPackageReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

/** Complete fixed language artifact, including documentation/license bytes. No database access. */
public final class RuleArtifact {
    public static final String MANIFEST="installation-manifest.json";
    public static final int MAX_MANIFEST=16384, MAX_DOCUMENT=65536, MAX_TOTAL=417792;
    static final Map<String,String> ROLES=Map.of("author-package.json","author-header",
            "character/languages.json","author-partition","package-guide.md","documentation","notice.md","license");
    private final LanguageAuthorPackageReader.Result author;
    private final List<FileFact> files;
    private final String manifestSha256;
    private RuleArtifact(LanguageAuthorPackageReader.Result author,List<FileFact> files,String hash) {
        this.author=author; this.files=List.copyOf(files); this.manifestSha256=hash;
    }
    public LanguageAuthorPackageReader.Result author() {return author;}
    public List<FileFact> files() {return files;}
    public String manifestSha256() {return manifestSha256;}
    public record FileFact(String path,String role,long byteLength,String rawSha256) { }
    public static RuleArtifact read(Path directory,String expectedManifestHash) throws IOException {
        digestText(expectedManifestHash);
        try(var root=SecureFiles.directory(directory)) {
            // Inventory first. Every descriptor remains anchored even if an ancestor is renamed.
            var expected=new TreeSet<>(ROLES.keySet()); expected.add("character"); expected.add(MANIFEST);
            if(!SecureFiles.inventory(root).equals(expected)) throw new IOException("Artifact inventory mismatch");
            byte[] manifest=SecureFiles.read(root,MANIFEST,MAX_MANIFEST);
            if(!sha256(manifest).equals(expectedManifestHash)) throw new IOException("External manifest hash mismatch");
            var map=OfflineJson.object(OfflineJson.parse(manifest),"installation_manifest_version","author_schema_version",
                    "module_key","release_version","canonical_format_version","archive_format_version","hash_algorithm",
                    "verification_scope","partition_keys","files");
            if(OfflineJson.number(map,"installation_manifest_version")!=1 || OfflineJson.number(map,"author_schema_version")!=1
                    || OfflineJson.number(map,"canonical_format_version")!=2 || OfflineJson.number(map,"archive_format_version")!=2
                    || !OfflineJson.string(map,"module_key").equals("dnd5e2014_srd51_se")
                    || !OfflineJson.string(map,"release_version").equals("1")
                    || !OfflineJson.string(map,"hash_algorithm").equals("SHA-256")
                    || !OfflineJson.string(map,"verification_scope").equals("PARTITION")
                    || !OfflineJson.array(map.get("partition_keys")).equals(List.of("character.language"))) throw OfflineJson.bad();
            var listed=OfflineJson.array(map.get("files")); if(listed.size()!=4) throw OfflineJson.bad();
            Map<String,byte[]> bytes=new TreeMap<>(); List<FileFact> facts=new ArrayList<>(); long total=manifest.length;
            for(Object item:listed) {
                var row=OfflineJson.object(item,"path","role","byte_length","raw_sha256");
                String path=OfflineJson.string(row,"path"),role=OfflineJson.string(row,"role"),hash=OfflineJson.string(row,"raw_sha256");
                path(path);digestText(hash);
                if(!role.equals(ROLES.get(path)) || bytes.containsKey(path)) throw OfflineJson.bad();
                long size=OfflineJson.number(row,"byte_length"); int max=limit(path);
                if(size>max) throw OfflineJson.bad();
                byte[] raw=SecureFiles.read(root,path,max);total+=raw.length;
                if(total>MAX_TOTAL || raw.length!=size || !sha256(raw).equals(hash)) throw OfflineJson.bad();
                bytes.put(path,raw);facts.add(new FileFact(path,role,size,hash));
            }
            if(!bytes.keySet().equals(ROLES.keySet())) throw OfflineJson.bad();
            try(var again=root.newDirectoryStream(Path.of("."),java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if(!SecureFiles.inventory(again).equals(expected))throw new IOException("Artifact inventory changed");
            }
            var author=new LanguageAuthorPackageReader().read(bytes.get("author-package.json"),bytes.get("character/languages.json"));
            return new RuleArtifact(author,facts.stream().sorted(Comparator.comparing(FileFact::path)).toList(),expectedManifestHash);
        }
    }
    /** Build into a new directory; existing artifacts are never overwritten. External hash is returned. */
    public static String build(Path source,Path output) throws IOException {
        Map<String,byte[]> bytes=new TreeMap<>();
        try(var root=SecureFiles.directory(source)) {
            var expected=new TreeSet<>(ROLES.keySet());expected.add("character");
            if(!SecureFiles.inventory(root).equals(expected)) throw new IOException("Author inventory mismatch");
            for(String path:ROLES.keySet()) bytes.put(path,SecureFiles.read(root,path,limit(path)));
        }
        new LanguageAuthorPackageReader().read(bytes.get("author-package.json"),bytes.get("character/languages.json"));
        StringBuilder json=new StringBuilder("{\"installation_manifest_version\":1,\"author_schema_version\":1,\"module_key\":\"dnd5e2014_srd51_se\",\"release_version\":\"1\",\"canonical_format_version\":2,\"archive_format_version\":2,\"hash_algorithm\":\"SHA-256\",\"verification_scope\":\"PARTITION\",\"partition_keys\":[\"character.language\"],\"files\":[");
        boolean comma=false;
        for(var entry:bytes.entrySet()) {
            if(comma)json.append(',');comma=true;
            json.append("{\"path\":").append(OfflineJson.quote(entry.getKey())).append(",\"role\":")
                .append(OfflineJson.quote(ROLES.get(entry.getKey()))).append(",\"byte_length\":").append(entry.getValue().length)
                .append(",\"raw_sha256\":").append(OfflineJson.quote(sha256(entry.getValue()))).append('}');
        }
        byte[] manifest=json.append("]}\n").toString().getBytes(StandardCharsets.UTF_8);
        java.nio.file.Files.createDirectory(output);
        java.nio.file.Files.createDirectory(output.resolve("character"));
        // Output parent is operator-controlled; exclusive creates never replace existing files.
        for(var entry:bytes.entrySet()) java.nio.file.Files.write(output.resolve(entry.getKey()),entry.getValue(),java.nio.file.StandardOpenOption.CREATE_NEW);
        java.nio.file.Files.write(output.resolve(MANIFEST),manifest,java.nio.file.StandardOpenOption.CREATE_NEW);
        String hash=sha256(manifest);read(output,hash);return hash;
    }
    static int limit(String path) {return switch(path) {case "author-package.json"->8192;case "character/languages.json"->262144;default->MAX_DOCUMENT;};}
    static void path(String value) {
        if(value.length()>255 || !value.matches("(?:[a-z0-9]+(?:-[a-z0-9]+)*/)*[a-z0-9]+(?:-[a-z0-9]+)*[.][a-z0-9]+")) throw OfflineJson.bad();
        for(String segment:value.split("/")) {
            String base=segment.split("[.]",2)[0];
            if(base.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) throw OfflineJson.bad();
        }
    }
    static String sha256(byte[] value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static void digestText(String value) {if(value==null || !value.matches("[0-9a-f]{64}"))throw OfflineJson.bad();}
}
