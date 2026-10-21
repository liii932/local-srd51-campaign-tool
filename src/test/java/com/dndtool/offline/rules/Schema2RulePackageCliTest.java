package com.dndtool.offline.rules;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class Schema2RulePackageCliTest {
    @TempDir Path temp;
    @Test void actualEntrypointPrintsStableDiagnosticWithoutExceptionStackOrCredential()throws Exception {
        Path malformed=temp.resolve("bad.zip");Files.write(malformed,new byte[22]);
        Process process=command("schema2-verify",malformed.toString());
        assertTrue(process.waitFor(10,TimeUnit.SECONDS));assertEquals(2,process.exitValue());
        String result=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("ERROR code=S2_ZIP stage=PARSE\n",result.replace("\r\n","\n"));
    }
    @Test void incompleteBaseIsRejectedByActualPreparationBeforeInteractiveCredentialOrSourceAccess()throws Exception {
        Path state=OfflineTestSupport.privateDirectory(temp,"state");Path evidence=Schema2OfflineTestSupport.evidenceDirectory(temp,state,"INSTALL",null);
        var artifact=Schema2OfflineTestSupport.artifact("incomplete_base","1","base","[]",false);Path zip=temp.resolve("base.zip");Files.write(zip,artifact.originalZip());
        Process process=command("schema2-prepare-install",evidence.toString(),zip.toString());
        assertTrue(process.waitFor(10,TimeUnit.SECONDS));assertEquals(2,process.exitValue());
        String result=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("ERROR code=S2_BASE_INCOMPLETE stage=QUALIFICATION\n",result.replace("\r\n","\n"));
        try(var tickets=new TicketStore(state)){tickets.requireClear();}
    }
    private Process command(String... args)throws Exception {
        var command=new java.util.ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        command.add("-cp");command.add(System.getProperty("java.class.path"));command.add(RulePackageCli.class.getName());command.addAll(java.util.List.of(args));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }
}
