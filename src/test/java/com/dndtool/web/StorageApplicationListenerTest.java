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

    @Test void contextParameterTakesPrecedenceEvenWhenEmpty() {
        Fixture context = new Fixture("");
        assertEquals("", StorageApplicationListener.configuredRoot(context.context, "/environment"));
        assertEquals("/environment", StorageApplicationListener.configuredRoot(new Fixture(null).context, "/environment"));
        assertNull(StorageApplicationListener.configuredRoot(new Fixture(null).context, null));
        assertTrue(StorageApplicationListener.enabled(context.context));
    }

    @Test void absentConfigurationLeavesLegacyModeSelected() {
        Fixture fixture = new Fixture(null);
        new StorageApplicationListener(null).contextInitialized(fixture.event());
        assertEquals(false, fixture.attributes.get(StorageApplicationListener.ENABLED_ATTRIBUTE));
        assertFalse(fixture.attributes.containsKey(StorageApplicationListener.SERVICE_ATTRIBUTE));
        assertTrue(fixture.logs.isEmpty());
    }

    @Test void explicitInvalidRootsKeepStorageModeAndNeverExposeTheRoot() {
        for (String value : List.of("", "  ", "relative-store", temporary + "\nprivate")) {
            Fixture fixture = new Fixture(value);
            new StorageApplicationListener(temporary.resolve("unused-environment").toString())
                    .contextInitialized(fixture.event());
            assertTrue(StorageApplicationListener.enabled(fixture.context));
            assertEquals("STORAGE_UNAVAILABLE", fixture.attributes.get(StorageApplicationListener.ERROR_ATTRIBUTE));
            assertThrows(IllegalStateException.class, () -> StorageApplicationListener.service(fixture.context));
            assertEquals(List.of("Current campaign storage is unavailable."), fixture.logs);
        }
        assertFalse(Files.exists(temporary.resolve("unused-environment")));
    }

    @Test void rootInsideDeployedApplicationIsRejectedBeforeCreation() {
        Fixture fixture = new Fixture(temporary.resolve("webapp/private").toString());
        fixture.deployedRoot = temporary.resolve("webapp").toString();
        new StorageApplicationListener(null).contextInitialized(fixture.event());
        assertEquals("STORAGE_UNAVAILABLE", fixture.attributes.get(StorageApplicationListener.ERROR_ATTRIBUTE));
        assertFalse(Files.exists(temporary.resolve("webapp")));
    }

    @Test void environmentRootIsOwnedUntilNormalContextShutdown() throws Exception {
        Path root = temporary.resolve("environment-store");
        Fixture fixture = new Fixture(null);
        StorageApplicationListener listener = new StorageApplicationListener(root.toString());
        listener.contextInitialized(fixture.event());
        assertTrue(StorageApplicationListener.enabled(fixture.context));
        assertEquals("IDLE", StorageApplicationListener.service(fixture.context).status().state());
        assertThrows(RuntimeException.class, () -> new CampaignStorageService(root));
        listener.contextDestroyed(fixture.event());
        assertFalse(fixture.attributes.containsKey(StorageApplicationListener.SERVICE_ATTRIBUTE));
        try (CampaignStorageService reopened = new CampaignStorageService(root)) {
            assertEquals("IDLE", reopened.status().state());
        }
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
