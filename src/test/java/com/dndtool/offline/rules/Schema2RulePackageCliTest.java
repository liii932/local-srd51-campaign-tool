package com.dndtool.offline.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Schema2RulePackageCliTest {
    @TempDir Path temp;

    @Test void verifyReportsTheActualFilesystemOrParsingRejection() throws Exception {
        Path malformed = Files.write(temp.resolve("bad.zip"), new byte[22]);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                RulePackageCli.class.getName(), "schema2-verify", malformed.toString())
                .redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "schema2-verify subprocess timed out");
            assertEquals(2, process.exitValue());
            String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String expected = OfflineTestSupport.supportsSecureDirectoryHandles(temp)
                    ? "ERROR code=S2_ZIP stage=PARSE" : "ERROR code=S2_LOCAL_STATE";
            assertEquals(expected + System.lineSeparator(), result);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
