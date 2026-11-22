package com.dndtool.service.storage;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the application in a fresh JVM with one bundled rules resource absent or replaced. */
public final class BundledRuleTestProcess {
    private BundledRuleTestProcess() { }

    public static void run(Path temporary, String zipName, byte[] replacement, Class<?> worker,
                           String... arguments) throws Exception {
        runWithResource(temporary, "storage/rules/" + zipName + ".zip", replacement, worker, arguments);
    }

    static void runWithResource(Path temporary, String resource, byte[] replacement, Class<?> worker,
                                String... arguments) throws Exception {
        Path output = temporary.resolve("bundled-rule-child.txt");
        Path override = temporary.resolve("replacement-resource");
        if (replacement != null) Files.write(override, replacement);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
                "-cp", classpath, BundledRuleTestProcess.class.getName(), resource,
                replacement == null ? "" : override.toString(), worker.getName()));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Bundled-rule test worker did not complete");
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String resource = args[0];
        URL replacement = args[1].isEmpty() ? null : Path.of(args[1]).toUri().toURL();
        URL[] classpath = Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
                .map(BundledRuleTestProcess::classpathUrl).toArray(URL[]::new);
        try (var isolated = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader()) {
            @Override public URL findResource(String name) {
                return resource.equals(name) ? replacement : super.findResource(name);
            }

            @Override public Enumeration<URL> findResources(String name) throws IOException {
                if (!resource.equals(name)) return super.findResources(name);
                return replacement == null ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(replacement));
            }
        }) {
            // Dependencies, application code and the worker must all use this same filtered classpath.
            Thread.currentThread().setContextClassLoader(isolated);
            Class<?> worker = Class.forName(args[2], true, isolated);
            String[] arguments = Arrays.copyOfRange(args, 3, args.length);
            worker.getMethod("main", String[].class).invoke(null, (Object) arguments);
        }
    }

    private static URL classpathUrl(String entry) {
        try { return Path.of(entry).toUri().toURL(); }
        catch (MalformedURLException invalid) { throw new IllegalArgumentException("Invalid test classpath entry", invalid); }
    }
}
