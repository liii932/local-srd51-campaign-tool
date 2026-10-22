package com.dndtool.offline.rules;

import java.nio.file.Path;
import java.util.Arrays;

/** Explicit offline commands only. No startup hooks, Web entry, migration, release or automatic retry. */
public final class RulePackageCli {
    private RulePackageCli() { }
    public static void main(String[] args)throws Exception {
        int code;
        try {code=run(args);}
        catch(Exception failure) {
            if(args.length==0||!args[0].startsWith("schema2-"))throw failure;
            System.err.println(schema2Failure(failure));code=2;
        }
        if(code!=0)System.exit(code);
    }
    static String schema2Failure(Exception failure) {
        if(failure instanceof com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception invalid)
            return "ERROR code="+invalid.diagnostic().code()+" stage="+invalid.diagnostic().stage();
        String message=failure.getMessage();
        if(message!=null&&message.matches("S2_[A-Z_]+"))return "ERROR code="+message;
        return "ERROR code="+(failure instanceof java.io.IOException?"S2_LOCAL_STATE":failure instanceof java.sql.SQLException?"S2_SOURCE_FAILURE":"S2_INPUT_OR_AUTHORIZATION");
    }
    static int run(String[] args)throws Exception {
        if(args.length>0&&args[0].startsWith("schema2-"))return schema2(args);
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
    private static int schema2(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("schema2-verify")) {
            var artifact=productionArtifact(args[1]);
            System.out.println("Verified transport only: "+artifact.moduleKey()+"@"+artifact.releaseVersion()+"; role="+artifact.role()
                    +"; profile="+artifact.canonicalProfile()+"; raw_zip_sha256="+artifact.rawZipSha256()+"; logical_sha256="+artifact.logicalSha256());
            return 0;
        }
        boolean prepareInstall=args.length==3&&args[0].equals("schema2-prepare-install");
        boolean install=args.length==4&&args[0].equals("schema2-install");
        boolean prepareAccept=args.length==4&&args[0].equals("schema2-prepare-accept");
        boolean accept=args.length==4&&args[0].equals("schema2-accept");
        boolean resolve=args.length==3&&args[0].equals("schema2-resolve");
        if(!prepareInstall&&!install&&!prepareAccept&&!accept&&!resolve)
            throw new IllegalArgumentException("Usage: schema2-verify ZIP | schema2-prepare-install EVIDENCE ZIP | schema2-install EVIDENCE ORIGINAL_TICKET ZIP | schema2-prepare-accept ACCEPT_EVIDENCE MODULE_KEY EXACT_VERSION | schema2-accept ACCEPT_EVIDENCE ORIGINAL_TICKET EXPLICIT_REVIEW_SHA256 | schema2-resolve RESOLVE_EVIDENCE ORIGINAL_TICKET");
        var evidence=MaintenanceEvidence.readSchema2(Path.of(args[1]));
        // Permission mismatch is rejected even before requesting a credential.
        if(prepareInstall||install)evidence.requireSchema2Install();else if(prepareAccept||accept)evidence.requireAccept();
        var input=prepareInstall?productionArtifact(args[2]):install?productionArtifact(args[3]):null;
        if(input!=null)Schema2InstallationEligibility.requireEligible(input);
        var console=System.console();if(console==null)throw new IllegalStateException("Interactive password input required");
        char[] password=console.readPassword(prepareAccept||accept?"Independent source accepter password: ":"Source maintenance password: ");
        if(password==null)throw new IllegalStateException("Password not provided");
        try(var tickets=new TicketStore(evidence.stateDirectory())) {
            var service=new Schema2SourceInstallation(evidence,tickets,password);
            if(prepareInstall||prepareAccept) {
                var prepared=prepareInstall?service.prepareInstall(input):service.prepareAccept(args[2],args[3]);
                if(prepared.existing()!=null)System.out.println(prepared.existing());
                else {
                    var ticket=prepared.ticket();System.out.println("Prepared and durably read back "+ticket.operationId()+".json; no source DML");
                    if(prepareAccept) {
                        System.out.println("Read every declaration and license in "+tickets.reviewPath(ticket)+" and its exact companion .review.zip before explicitly accepting.");
                        System.out.println("review_sha256="+ticket.reviewSha256());
                    }
                }
                return 0;
            }
            var ticket=tickets.loadSchema2(args[2]);
            var result=resolve?service.resolve(ticket):install?service.install(ticket,input):service.accept(ticket,args[3]);
            System.out.println(result);
            return result.status()==Schema2SourceInstallation.Status.COMMITTED||result.status()==Schema2SourceInstallation.Status.NOT_COMMITTED?0:2;
        } finally {Arrays.fill(password,'\0');}
    }
    private static com.dndtool.module.composition.ProductionRuleArtifact productionArtifact(String path)throws java.io.IOException {
        return com.dndtool.module.composition.ProductionRuleArtifact.read(SecureFiles.read(Path.of(path),com.dndtool.module.composition.ProductionRuleArtifact.MAX_ZIP_BYTES));
    }
}
