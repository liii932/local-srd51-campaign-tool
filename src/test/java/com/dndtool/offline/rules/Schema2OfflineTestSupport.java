package com.dndtool.offline.rules;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class Schema2OfflineTestSupport {
    private Schema2OfflineTestSupport() { }
    static MaintenanceEvidence evidence(Path parent,Path state,String permission,UUID operation)throws Exception {
        Path dir=evidenceDirectory(parent,state,permission,operation);return MaintenanceEvidence.readSchema2(dir);
    }
    static Path evidenceDirectory(Path parent,Path state,String permission,UUID operation)throws Exception {
        Path dir=OfflineTestSupport.privateDirectory(parent,"schema2-evidence-"+UUID.randomUUID());
        Map<String,Object> map=new LinkedHashMap<>();map.put("evidence_version",2);map.put("target","isolated-source");map.put("lineage","source-history-1");
        map.put("jdbc_url","jdbc:mysql://127.0.0.1:33306/dnd_tool_rules");map.put("user",permission.equals("ACCEPT")?"accepter":"installer");
        map.put("server_uuid","12345678-1234-1234-1234-123456789abc");map.put("current_user",(permission.equals("ACCEPT")?"accepter":"installer")+"@127.0.0.1");
        map.put("grants_sha256",RuleArtifact.sha256((OfflineTestSupport.GRANT+"\n").getBytes(StandardCharsets.UTF_8)));
        for(String kind:List.of("schema","history","isolation")) {
            byte[] report=("Independent protocol-test fixture "+kind+"; permission="+permission+"; isolated="+operation+"; checkpoint=0; shared_state="+state).getBytes(StandardCharsets.UTF_8);
            Files.write(dir.resolve(kind+"-audit.txt"),report);map.put(kind+"_audit_sha256",RuleArtifact.sha256(report));
        }
        map.put("expires_at","2999-01-01T00:00:00Z");map.put("permission",permission);map.put("isolated_operation",operation==null?"none":operation.toString());
        map.put("history_minimum_version",0);map.put("schema2_history_minimum_version",0);map.put("state_directory",state.toString());
        var json=new StringJoiner(",","{","}");map.forEach((key,value)->json.add(OfflineJson.quote(key)+":"+(value instanceof String s?OfflineJson.quote(s):value)));
        Files.writeString(dir.resolve("maintenance.json"),json.toString());return dir;
    }
    static ProductionRuleArtifact empty(String module,String version)throws Exception {return artifact(module,version,"extension","[]",false);}
    static ProductionRuleArtifact artifact(String module,String version,String role,String definitions,boolean changedZip)throws Exception {
        String header="{\"author_schema_version\":2,\"module_key\":\""+module+"\",\"release_version\":\""+version+"\",\"package_display_name\":\"Review content\",\"role\":\""+role
                +"\",\"dependencies\":"+(role.equals("base")?"[]":"[{\"module_key\":\"base\",\"supported_versions\":[\"1\"]}]")
                +",\"compatible_combinations\":"+(role.equals("base")?"[{}]":"[{\"base\":\"1\"}]")
                +",\"load_order_constraints\":[],\"files\":[{\"kind\":\"definitions\",\"path\":\"definitions.json\"}]}";
        var files=new LinkedHashMap<String,byte[]>();files.put("author-package.json",(header+(changedZip?"\n":"")).getBytes(StandardCharsets.UTF_8));
        files.put("definitions.json",definitions.getBytes(StandardCharsets.UTF_8));files.put("LICENSE","A license for actual review\n".getBytes(StandardCharsets.UTF_8));
        return ProductionRuleArtifact.read(Schema2SourceJdbcFixture.zip(files));
    }
}
