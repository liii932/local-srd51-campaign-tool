package com.dndtool.offline.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RulePackageCliTest {
    @TempDir Path temp;

    @Test
    void verifyReportsBothValidatedPartitionsAndManifest() throws Exception {
        var artifact = OfflineTestSupport.artifact(temp);

        CommandResult result = verify(artifact.manifestSha256());

        assertEquals(0, result.exitCode(), result.stderr());
        assertEquals("PARTITION character.language=18 character.tool=37 manifest="
                + artifact.manifestSha256() + System.lineSeparator(), result.stdout());
    }

    @Test
    void verifyFailsWithoutSuccessOutputForWrongManifestOrMissingToolFile() throws Exception {
        var artifact = OfflineTestSupport.artifact(temp);

        assertRejected(verify("0".repeat(64)));

        Files.delete(temp.resolve("artifact/character/tools.json"));
        assertRejected(verify(artifact.manifestSha256()));
    }

    private CommandResult verify(String manifest) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                RulePackageCli.class.getName(), "verify", temp.resolve("artifact").toString(), manifest)
                .start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "verify subprocess timed out");
            return new CommandResult(process.exitValue(),
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                    new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void assertRejected(CommandResult result) {
        assertNotEquals(0, result.exitCode());
        assertEquals("", result.stdout());
    }

    private record CommandResult(int exitCode, String stdout, String stderr) { }
}
