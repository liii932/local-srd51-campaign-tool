package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.service.storage.CampaignRuleFixtures;
import com.dndtool.service.storage.CampaignStorageService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StorageReadinessTest {
    @TempDir Path temporary;

    @Test void idleAndActiveReadinessInspectActualPrivateStoreWithoutCreatingBusinessState() throws Exception {
        Path root = temporary.resolve("store");
        try (CampaignStorageService service = new CampaignStorageService(root)) {
            Fixture idle = check(service);
            assertEquals(200, idle.status);
            assertEquals("SQLITE", idle.body().get("mode").getAsString());
            assertTrue(idle.body().get("ready").getAsBoolean());
            assertEquals("IDLE", idle.body().get("state").getAsString());
            assertEquals(0, service.status().epoch());
            Path library = root.resolve("rule-library");
            assertFalse(Files.exists(library));
            CampaignRuleFixtures.create(service, "Readiness");
            Files.move(library, root.resolve("retained-rules"));
            Files.writeString(library, "unavailable rule library");
            long revision = service.current().revision();
            Fixture active = check(service);
            assertEquals(200, active.status);
            assertEquals("ACTIVE", active.body().get("state").getAsString());
            assertEquals(revision, service.current().revision());
        }
    }

    @Test void residualRequiresExplicitRecoveryAndReadinessDoesNotRecoverIt() throws Exception {
        Path root = temporary.resolve("store");
        String runId;
        try (CampaignStorageService service = new CampaignStorageService(root)) {
            CampaignRuleFixtures.create(service, "Readiness");
            runId = service.status().runId();
        }
        try (CampaignStorageService service = new CampaignStorageService(root)) {
            Fixture result = check(service);
            assertEquals(503, result.status);
            assertFalse(result.body().get("ready").getAsBoolean());
            assertEquals("RESIDUAL", result.body().get("state").getAsString());
            assertEquals("STORAGE_RECOVERY_REQUIRED", result.body().get("code").getAsString());
            assertEquals(List.of(runId), service.status().residualRunIds());
            service.recover(runId);
            assertEquals(200, check(service).status);
        }
    }

    @Test void malformedSavedFileFailsReadinessWithoutExposingPaths() throws Exception {
        Path root = temporary.resolve("private-store");
        try (CampaignStorageService service = new CampaignStorageService(root)) {
            Files.writeString(root.resolve("saves").resolve(UUID.randomUUID() + ".json"), "not a campaign");
            assertUnavailable(check(service));
        }
    }

    @Test void unreadableActiveDatabaseIsBlockedAndDoesNotClaimPlayableReadiness() throws Exception {
        Path root = temporary.resolve("store");
        try (CampaignStorageService service = new CampaignStorageService(root)) {
            CampaignRuleFixtures.create(service, "Readiness");
            Path database = root.resolve("runs").resolve(service.status().runId()).resolve("run.db");
            Files.write(database, new byte[] {1, 2, 3});
            Fixture result = check(service);
            assertEquals(503, result.status);
            assertEquals("BLOCKED", result.body().get("state").getAsString());
            assertEquals("STORAGE_BLOCKED", result.body().get("code").getAsString());
        }
    }

    @Test void missingAndClosedServicesFailReadinessWithTheSameStableCategory() throws Exception {
        assertUnavailable(check(null));
        CampaignStorageService service = new CampaignStorageService(temporary.resolve("store"));
        service.close();
        assertUnavailable(check(service));
    }

    private static void assertUnavailable(Fixture result) {
        assertEquals(503, result.status);
        assertEquals("{\"status\":\"ERROR\",\"mode\":\"SQLITE\",\"ready\":false,"
                + "\"state\":\"UNAVAILABLE\",\"code\":\"STORAGE_UNAVAILABLE\"}", result.output.toString());
    }

    private static Fixture check(CampaignStorageService service) throws Exception {
        Fixture result = new Fixture();
        ServletContext context = proxy(ServletContext.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getAttribute" -> StorageApplicationListener.ENABLED_ATTRIBUTE.equals(arguments[0]) ? true : service;
            default -> throw new AssertionError("Unexpected context call: " + method.getName());
        });
        HttpServletRequest request = proxy(HttpServletRequest.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getServletContext" -> context;
            case "getPathInfo" -> "/readiness";
            default -> throw new AssertionError("Unexpected request call: " + method.getName());
        });
        HttpServletResponse response = proxy(HttpServletResponse.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "setStatus" -> { result.status = (int) arguments[0]; yield null; }
            case "setContentType" -> { assertEquals("application/json; charset=UTF-8", arguments[0]); yield null; }
            case "getWriter" -> new PrintWriter(result.output);
            default -> throw new AssertionError("Unexpected response call: " + method.getName());
        });
        new HostStorageServlet().doGet(request, response);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static final class Fixture {
        int status;
        final StringWriter output = new StringWriter();
        JsonObject body() { return JsonParser.parseString(output.toString()).getAsJsonObject(); }
    }
}
