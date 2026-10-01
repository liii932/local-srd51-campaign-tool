package com.dndtool.offline.rules;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

final class OfflineTestSupport {
    static final String GRANT="GRANT SELECT ON `dnd_tool_rules`.`rule_release` TO `installer`@`127.0.0.1`";
    static Path privateDirectory(Path parent,String name)throws Exception {
        Path dir=Files.createDirectory(parent.resolve(name));Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));return dir;
    }
    static RuleArtifact artifact(Path parent)throws Exception {
        Path source=Files.createDirectory(parent.resolve("author"));Files.createDirectory(source.resolve("character"));
        for(String file:List.of("author-package.json","character/languages.json","character/tools.json")) {
            String input=Files.readString(Path.of("rule-packages/srd51-complete").resolve(file)).replace("\r\n","\n");
            Files.writeString(source.resolve(file),input);
        }
        Files.writeString(source.resolve("package-guide.md"),"Documentation\n");Files.writeString(source.resolve("notice.md"),"License\n");
        Path output=parent.resolve("artifact");String hash=RuleArtifact.build(source,output);return RuleArtifact.read(output,hash);
    }
    static MaintenanceEvidence evidence(Path parent,Path state,String permission,UUID operation)throws Exception {
        Path dir=privateDirectory(parent,"evidence-"+UUID.randomUUID());
        Map<String,Object> map=new LinkedHashMap<>();map.put("evidence_version",1);map.put("target","isolated-source");map.put("lineage","source-history-1");
        map.put("jdbc_url","jdbc:mysql://127.0.0.1:33306/dnd_tool_rules");map.put("user","installer");map.put("server_uuid","12345678-1234-1234-1234-123456789abc");
        map.put("current_user","installer@127.0.0.1");map.put("grants_sha256",RuleArtifact.sha256((GRANT+"\n").getBytes(StandardCharsets.UTF_8)));
        for(String type:List.of("schema","history","isolation")) {
            byte[] report=("Independent test fixture for "+type).getBytes(StandardCharsets.UTF_8);
            Files.write(dir.resolve(type+"-audit.txt"),report);map.put(type+"_audit_sha256",RuleArtifact.sha256(report));
        }
        map.put("expires_at","2999-01-01T00:00:00Z");map.put("permission",permission);map.put("isolated_operation",operation==null?"none":operation.toString());
        map.put("history_minimum_version",0);map.put("state_directory",state.toString());
        StringJoiner json=new StringJoiner(",","{","}");map.forEach((k,v)->json.add(OfflineJson.quote(k)+":"+(v instanceof String s?OfflineJson.quote(s):v)));
        Files.writeString(dir.resolve("maintenance.json"),json.toString());return MaintenanceEvidence.read(dir);
    }
}
