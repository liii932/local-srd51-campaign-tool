package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.annotation.WebServlet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtectedHostCommandEntryTest {
    private static final Path HOST_VIEW = Path.of("src/main/webapp/WEB-INF/views/host.jsp");
    private static final Path MAP_SCRIPT =
            Path.of("src/main/webapp/host/assets/host-map.js");

    @Test
    void allNewRoutesStayInsideTheProtectedHostNamespace() {
        assertRoute(HostCheckServlet.class, "/api/host/events/check");
        assertRoute(HostEncounterServlet.class, "/api/host/maps/encounter");
        assertRoute(HostPositionServlet.class, "/api/host/maps/position");
        assertRoute(HostCampaignExportServlet.class, "/api/host/archive/export");
        assertRoute(HostLevelOneCharacterCreationServlet.class,
                "/api/host/characters/level-one");
        assertRoute(HostLevelAdvancementServlet.class,
                "/api/host/characters/level-up");
    }

    @Test
    void hostUiExposesCheckMapAndExportControls() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);
        String map = Files.readString(MAP_SCRIPT, StandardCharsets.UTF_8);

        for (String id : List.of(
                "active-campaign-export", "active-campaign-key",
                "host-encounter-form", "host-party-node", "host-participants",
                "host-position-form", "host-position-character",
                "host-position-version", "host-position-node")) {
            assertTrue(view.contains("id=\"" + id + "\""), id);
        }
        for (String route : List.of(
                "/api/host/events/check", "/api/host/maps/encounter",
                "/api/host/maps/position", "/api/host/archive/export")) {
            assertTrue(view.contains(route), route);
        }
        assertTrue(map.contains("DND_TOOL_SE_INITIALIZE_STAGE3_ENCOUNTER_V1"));
        assertTrue(map.contains("SET_STAGE3_ENTITY_POSITION_V2"));
        assertTrue(map.contains("X-CSRF-Token"));
        assertTrue(map.contains("X-Host-State-Epoch"));
        assertTrue(map.contains("X-Object-Row-Version"));
        assertTrue(map.contains("X-Request-Id"));
        assertTrue(map.contains("X-Request-Digest"));
    }

    @Test
    void hostResponsesDoNotNameInternalDatabaseIdentifiers() throws Exception {
        for (Path file : List.of(
                Path.of("src/main/java/com/dndtool/web/HostCheckServlet.java"),
                Path.of("src/main/java/com/dndtool/web/HostEncounterServlet.java"),
                Path.of("src/main/java/com/dndtool/web/HostPositionServlet.java"))) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            assertFalse(source.contains("\\\"campaignId\\\""), file.toString());
            assertFalse(source.contains("\\\"characterId\\\""), file.toString());
            assertFalse(source.contains("\\\"moduleReleaseId\\\""), file.toString());
            assertFalse(source.contains("\\\"gameEventId\\\""), file.toString());
            assertFalse(source.contains("\\\"battleId\\\""), file.toString());
            assertFalse(source.contains("\\\"mapInstanceId\\\""), file.toString());
        }
    }

    private static void assertRoute(Class<?> type, String route) {
        WebServlet annotation = type.getAnnotation(WebServlet.class);
        assertTrue(annotation != null, type.getSimpleName());
        assertArrayEquals(new String[] {route}, annotation.urlPatterns());
        assertTrue(route.startsWith("/api/host/"));
    }
}
