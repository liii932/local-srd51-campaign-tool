package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.service.storage.CampaignStorageService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StorageApplicationListenerTest {
    @TempDir Path temporary;

    @Test void noOverridesUsesTheRuntimeUsersHome() {
        Path home = temporary.resolve("home");
        Fixture fixture = new Fixture(null);
        StorageApplicationListener listener = new StorageApplicationListener(null, home.toString());
        listener.contextInitialized(fixture.event());
        try {
            assertEquals("IDLE", StorageApplicationListener.service(fixture.context).status().state());
            assertTrue(Files.isRegularFile(home.resolve(".dndtool/campaigns/instance.lock")));
            assertTrue(fixture.logs.isEmpty());
        } finally {
            listener.contextDestroyed(fixture.event());
        }
    }

    @Test void contextOverridesEnvironmentAndEnvironmentOverridesDefault() {
        Path home = temporary.resolve("home");
        Path override = temporary.resolve("context");
        Path environment = temporary.resolve("environment");
        var listener = new StorageApplicationListener(environment.toString(), home.toString());

        Fixture configured = new Fixture(override.toString());
        listener.contextInitialized(configured.event());
        try {
            assertEquals("IDLE", StorageApplicationListener.service(configured.context).status().state());
            assertTrue(Files.isRegularFile(override.resolve("instance.lock")));
            assertFalse(Files.exists(environment));
        } finally {
            listener.contextDestroyed(configured.event());
        }

        Fixture unconfigured = new Fixture(null);
        listener.contextInitialized(unconfigured.event());
        try {
            assertEquals("IDLE", StorageApplicationListener.service(unconfigured.context).status().state());
            assertTrue(Files.isRegularFile(environment.resolve("instance.lock")));
            assertFalse(Files.exists(home));
        } finally {
            listener.contextDestroyed(unconfigured.event());
        }
    }

    @Test void invalidOverridesNeverSelectAnotherDirectory() {
        Path environment = temporary.resolve("unused-environment");
        Path home = temporary.resolve("unused-home");
        var listener = new StorageApplicationListener(environment.toString(), home.toString());
        for (String value : List.of("", "  ", "relative-store", temporary + "\nprivate", temporary + "\u0080private")) {
            Fixture fixture = new Fixture(value);
            listener.contextInitialized(fixture.event());
            assertProblem(fixture, "STORAGE_ROOT_INVALID");
        }
        Fixture emptyEnvironment = new Fixture(null);
        new StorageApplicationListener("", home.toString()).contextInitialized(emptyEnvironment.event());
        assertProblem(emptyEnvironment, "STORAGE_ROOT_INVALID");
        assertFalse(Files.exists(environment));
        assertFalse(Files.exists(home));
    }

    @Test void rootsWithinDeployedAndGitTreesAreRejectedBeforeCreation() throws Exception {
        Fixture expanded = new Fixture(temporary.resolve("webapp/private").toString());
        expanded.deployedRoot = temporary.resolve("webapp").toString();
        var listener = new StorageApplicationListener(null, temporary.toString());
        listener.contextInitialized(expanded.event());
        assertProblem(expanded, "STORAGE_ROOT_INVALID");
        assertFalse(Files.exists(temporary.resolve("webapp")));

        Path repository = Files.createDirectory(temporary.resolve("repository"));
        Files.createFile(repository.resolve(".git"));
        Fixture withinGit = new Fixture(repository.resolve("private").toString());
        listener.contextInitialized(withinGit.event());
        assertProblem(withinGit, "STORAGE_ROOT_INVALID");
        assertFalse(Files.exists(repository.resolve("private")));
    }

    @Test void unusableDirectoryReturnsOnlyTheStableFailureCode() throws Exception {
        Path notDirectory = Files.createFile(temporary.resolve("private-name"));
        Fixture fixture = new Fixture(notDirectory.toString());
        new StorageApplicationListener(null, temporary.toString()).contextInitialized(fixture.event());
        assertProblem(fixture, "STORAGE_UNAVAILABLE");
    }

    @Test void liveOwnerExcludesSecondContextUntilShutdown() throws Exception {
        Path root = temporary.resolve("environment-store");
        var listener = new StorageApplicationListener(root.toString(), temporary.toString());
        Fixture owner = new Fixture(null);
        listener.contextInitialized(owner.event());
        try {
            Fixture duplicate = new Fixture(null);
            listener.contextInitialized(duplicate.event());
            assertProblem(duplicate, "STORAGE_ROOT_IN_USE");
            listener.contextDestroyed(duplicate.event());
            assertEquals("IDLE", StorageApplicationListener.service(owner.context).status().state());
        } finally {
            listener.contextDestroyed(owner.event());
        }
        assertFalse(owner.attributes.containsKey(StorageApplicationListener.SERVICE_ATTRIBUTE));
        try (CampaignStorageService reopened = new CampaignStorageService(root)) {
            assertEquals("IDLE", reopened.status().state());
        }
    }

    private static void assertProblem(Fixture fixture, String code) {
        assertFalse(fixture.attributes.containsKey(StorageApplicationListener.SERVICE_ATTRIBUTE));
        assertEquals(code, StorageApplicationListener.startupError(fixture.context));
        var unavailable = assertThrows(IllegalStateException.class,
                () -> StorageApplicationListener.service(fixture.context));
        assertEquals(code, unavailable.getMessage());
        assertEquals(List.of("Current campaign storage failed to initialize: " + code), fixture.logs);
    }

    private static final class Fixture {
        final Map<String, Object> attributes = new HashMap<>();
        final List<String> logs = new ArrayList<>();
        String deployedRoot;
        final ServletContext context;

        Fixture(String root) {
            context = (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(),
                    new Class<?>[] {ServletContext.class}, (ignored, method, arguments) -> switch (method.getName()) {
                        case "getAttribute" -> attributes.get(arguments[0]);
                        case "setAttribute" -> { attributes.put((String) arguments[0], arguments[1]); yield null; }
                        case "removeAttribute" -> { attributes.remove(arguments[0]); yield null; }
                        case "getInitParameter" -> root;
                        case "getRealPath" -> deployedRoot;
                        case "log" -> { logs.add((String) arguments[0]); yield null; }
                        default -> throw new AssertionError("Unexpected context call: " + method.getName());
                    });
        }

        ServletContextEvent event() { return new ServletContextEvent(context); }
    }
}
