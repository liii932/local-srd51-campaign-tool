package com.dndtool.offline.rules;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleArtifactTest {
    @TempDir Path temp;
    @Test void completeArtifactHasOnlyPartitionEvidenceAndIndependentFingerprint()throws Exception {
        var artifact=OfflineTestSupport.artifact(temp);
        assertEquals(4,artifact.files().size());assertEquals(18,artifact.author().partition().languages().size());
        byte[] expected=HexFormat.of().parseHex(Files.readString(Path.of("src/test/resources/source-installation-fingerprint-v1.hex")).strip());
        assertArrayEquals(expected,InstallationFingerprint.encode(artifact,0));
        assertNotEquals(InstallationFingerprint.digest(artifact,0),InstallationFingerprint.digest(artifact,1));
    }
    @Test void rawFileChangeAndExternalHashMismatchFail()throws Exception {
        var a=OfflineTestSupport.artifact(temp);Path directory=temp.resolve("artifact");
        assertThrows(Exception.class,()->RuleArtifact.read(directory,"0".repeat(64)));
        Files.writeString(directory.resolve("notice.md"),"Changed");assertThrows(Exception.class,()->RuleArtifact.read(directory,a.manifestSha256()));
    }
    @Test void inventoryRejectsExtraMissingSymlinkAndNestedDirectory()throws Exception {
        var a=OfflineTestSupport.artifact(temp);Path dir=temp.resolve("artifact"),notice=dir.resolve("notice.md");
        Files.writeString(dir.resolve("extra.txt"),"x");assertThrows(Exception.class,()->RuleArtifact.read(dir,a.manifestSha256()));Files.delete(dir.resolve("extra.txt"));
        Files.delete(notice);assertThrows(Exception.class,()->RuleArtifact.read(dir,a.manifestSha256()));
        Files.createSymbolicLink(notice,temp.resolve("author/notice.md"));assertThrows(Exception.class,()->RuleArtifact.read(dir,a.manifestSha256()));Files.delete(notice);
        Files.createDirectory(notice);assertThrows(Exception.class,()->RuleArtifact.read(dir,a.manifestSha256()));
    }
    @Test void strictManifestRejectsUnknownNullCompleteAndDuplicates()throws Exception {
        var a=OfflineTestSupport.artifact(temp);Path file=temp.resolve("artifact/installation-manifest.json");String original=Files.readString(file);
        for(String broken:List.of(original.replace("\"PARTITION\"","\"COMPLETE\""),original.replace("\"files\":","\"observed_content_sha256\":null,\"files\":"),
                original.replace("\"files\":","\"package_display_name\":\"x\",\"files\":"),original.replace("\"files\":","\"files\":[],\"files\":"),
                original.replace("notice.md","con.md"),original.replace("\"byte_length\":8","\"byte_length\":8.0"))) {
            Files.writeString(file,broken);String hash=RuleArtifact.sha256(broken.getBytes(StandardCharsets.UTF_8));
            assertThrows(Exception.class,()->RuleArtifact.read(temp.resolve("artifact"),hash));
        }
    }
    @Test void parserRejectsUnicodeDigitsAndAllCoercions() {
        for(String token:List.of("١","1١","1１","１1","0١","01","1.0","1e0","-1","true","null","9223372036854775808","[1,]","{\"a\":1,\"\\u0061\":2}"))
            assertThrows(IllegalArgumentException.class,()->OfflineJson.parse(token.getBytes(StandardCharsets.UTF_8)),token);
        assertEquals(0L,OfflineJson.parse("0".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Long.MAX_VALUE,OfflineJson.parse("9223372036854775807".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void pathsUsePortableReservedNameRules() {
        for(String path:List.of("../x.json","/a.json","a\\x.json","CON.json","con.json","nul.txt","aux/data.json","character/lpt9.json","com1/rules.json","a%20.json","a./x.json"))
            assertThrows(IllegalArgumentException.class,()->RuleArtifact.path(path));
        RuleArtifact.path("console.json");RuleArtifact.path("com10/rules.json");
    }
    @Test void boundedDocumentCapacityAndReaderDeadline()throws Exception {
        OfflineTestSupport.artifact(temp);Path source=temp.resolve("author");
        for(String name:List.of("author-package.json","character/languages.json")) {
            byte[] original=Files.readAllBytes(source.resolve(name));byte[] padded=Arrays.copyOf(original,RuleArtifact.limit(name));
            Arrays.fill(padded,original.length,padded.length,(byte)' ');Files.write(source.resolve(name),padded);
        }
        Files.write(source.resolve("notice.md"),new byte[RuleArtifact.MAX_DOCUMENT]);Files.write(source.resolve("package-guide.md"),new byte[RuleArtifact.MAX_DOCUMENT]);
        var result=assertTimeoutPreemptively(Duration.ofSeconds(5),()->RuleArtifact.build(source,temp.resolve("maximum")));
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->RuleArtifact.read(temp.resolve("maximum"),result));
        Path manifest=temp.resolve("maximum/installation-manifest.json");byte[] original=Files.readAllBytes(manifest);
        byte[] full=Arrays.copyOf(original,RuleArtifact.MAX_MANIFEST);Arrays.fill(full,original.length,full.length,(byte)' ');Files.write(manifest,full);
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->RuleArtifact.read(temp.resolve("maximum"),RuleArtifact.sha256(full)));
        Files.write(source.resolve("notice.md"),new byte[RuleArtifact.MAX_DOCUMENT+1]);
        assertThrows(Exception.class,()->RuleArtifact.build(source,temp.resolve("too-large")));
    }
    @Test void malformedUtf8DepthStringAndTokenBudgetsFail() {
        assertThrows(Exception.class,()->OfflineJson.parse(new byte[]{(byte)0xc0,(byte)0xaf}));
        for(String bad:List.of("[[[[[[0]]]]]]","\""+"x".repeat(4097)+"\"","["+"0,".repeat(32)+"0]","\"\\ud800\"","\ufeff{}"))
            assertThrows(Exception.class,()->OfflineJson.parse(bad.getBytes(StandardCharsets.UTF_8)));
    }
}
