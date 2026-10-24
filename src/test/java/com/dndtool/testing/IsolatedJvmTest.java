package com.dndtool.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs process-lifetime fault scenarios without a production reset or admission bypass. */
public final class IsolatedJvmTest {
    private IsolatedJvmTest() { }

    public static void run(Class<?> entryPoint, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                entryPoint.getName()));
        command.addAll(Arrays.asList(arguments));
        Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
        // Cold WSL JVM startup and composition setup are test overhead, not a product latency promise.
        boolean completed = child.waitFor(60, TimeUnit.SECONDS);
        if (!completed) child.destroyForcibly();
        assertTrue(completed, "Isolated JVM scenario timed out: " + Arrays.toString(arguments));
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, child.exitValue(), Arrays.toString(arguments) + ": " + output);
    }
}
