package com.dndtool.offline.rules;

import java.nio.file.Path;
import java.util.Arrays;

/** Explicit offline commands only. No startup hooks, Web entry, migration, release or automatic retry. */
public final class RulePackageCli {
    private RulePackageCli() { }
    public static void main(String[] args)throws Exception {
        int code=run(args);if(code!=0)System.exit(code);
    }
    static int run(String[] args)throws Exception {
        if(args.length==3 && args[0].equals("build")) {
            System.out.println("installation_manifest_sha256="+RuleArtifact.build(Path.of(args[1]),Path.of(args[2])));return 0;
        }
        if(args.length==3 && args[0].equals("verify")) {
            var artifact=RuleArtifact.read(Path.of(args[1]),args[2]);
            System.out.println("PARTITION character.language: 18 languages; manifest="+artifact.manifestSha256());return 0;
        }
        boolean prepare=args.length==4&&args[0].equals("prepare"),install=args.length==5&&args[0].equals("install"),resolve=args.length==3&&args[0].equals("resolve");
        if(!prepare&&!install&&!resolve)throw new IllegalArgumentException("Usage: build SOURCE NEW_OUTPUT | verify ARTIFACT MANIFEST_SHA256 | prepare EVIDENCE ARTIFACT MANIFEST_SHA256 | install EVIDENCE TICKET_FILE ARTIFACT MANIFEST_SHA256 | resolve EVIDENCE TICKET_FILE");
        var evidence=MaintenanceEvidence.read(Path.of(args[1]));
        var console=System.console();if(console==null)throw new IllegalStateException("Interactive password input required");
        char[] password=console.readPassword("Source installer password: ");if(password==null)throw new IllegalStateException("Password not provided");
        try(var tickets=new TicketStore(evidence.stateDirectory())) {
            var service=new SourceInstallation(evidence,tickets,password);
            if(prepare) {
                var ticket=service.prepare(RuleArtifact.read(Path.of(args[2]),args[3]));
                System.out.println("Prepared and durably read back "+ticket.operationId()+".json; no source DML");
            } else {
                var ticket=tickets.load(args[2]);
                var result=resolve?service.resolve(ticket):service.install(ticket,RuleArtifact.read(Path.of(args[3]),args[4]));
                System.out.println(result);
                if(result.status()!=SourceInstallation.Status.COMMITTED&&result.status()!=SourceInstallation.Status.NOT_COMMITTED)return 2;
            }
        } finally {Arrays.fill(password,'\0');}
        return 0;
    }
}
