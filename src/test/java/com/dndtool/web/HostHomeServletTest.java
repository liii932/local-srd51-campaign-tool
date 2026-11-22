package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HostHomeServletTest {
    private static final String ORIGIN = "http://127.0.0.1:8080";
    private static final String ASSETS = "/host/assets/";
    private static final List<String> RETIRED_ASSETS = List.of(
            "host-console.css", "host-campaign.js", "host-character.js", "host-character-card.js",
            "host-character-lifecycle.js", "host-level-one-character.js", "host-level-advancement.js",
            "host-event.js", "host-map.js", "host-archive.js");

    @TempDir Path temporary;

    @Test void descriptorExposesTheCampaignThroughTheNormalLoopbackRoutes() throws Exception {
        try (var launcher = StorageLoopLauncher.start(temporary.resolve("storage"));
                var client = HttpClient.newHttpClient()) {
            for (String path : List.of("/", "/host", "/host/")) {
                var response = get(client, path);
                assertEquals(303, response.statusCode(), path);
                assertEquals("/host/storage", response.headers().firstValue("Location").orElseThrow());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            }
            assertEquals("OK", get(client, "/health").body());
            var table = get(client, "/host/storage");
            assertEquals(200, table.statusCode());
            for (String id : List.of("storage-create", "domain-create", "domain-profile", "domain-growth",
                    "domain-check", "domain-position", "domain-scene-enter", "domain-combat",
                    "domain-rule-catalog", "rule-library-import", "storage-save", "storage-last-download",
                    "storage-open", "storage-import", "storage-recover")) {
                assertTrue(table.body().contains("id=\"" + id + "\""), id);
            }
            for (String asset : List.of("host-storage.js", "host-storage.css",
                    "host-player-participation.js", "host-player-participation.css", "local-invite-qr.js")) {
                assertTrue(table.body().contains("\"" + ASSETS + asset + "\""), asset);
                assertEquals(200, get(client, ASSETS + asset).statusCode(), asset);
            }
            for (String asset : RETIRED_ASSETS) assertFalse(table.body().contains(ASSETS + asset), asset);
            for (String path : List.of("/api/host/status", "/api/host/diagnostics/database", "/host/archive",
                    "/host/rules", "/api/host/rules", "/api/host/characters/card", "/api/host/archive/export",
                    "/player", "/display", "/api/public/state")) {
                assertEquals(404, get(client, path).statusCode(), path);
            }
            for (String path : List.of("/api/host/campaigns", "/api/host/characters",
                    "/api/host/characters/level-one", "/api/host/characters/level-up",
                    "/api/host/characters/card", "/api/host/characters/lifecycle", "/api/host/events/check",
                    "/api/host/maps/position", "/api/host/maps/encounter", "/api/host/archive/validate",
                    "/api/host/archive/import")) {
                var request = HttpRequest.newBuilder(URI.create(ORIGIN + path))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                assertEquals(404, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode(), path);
            }
        }
    }

    // The source launcher mounts src/main/webapp directly, so this checks the post-removal source tree.
    @Test void retiredHostAssetsAreNotServed() throws Exception {
        try (var launcher = StorageLoopLauncher.start(temporary.resolve("storage"));
                var client = HttpClient.newHttpClient()) {
            for (String asset : RETIRED_ASSETS) {
                assertEquals(404, get(client, ASSETS + asset).statusCode(), asset);
            }
        }
    }

    private static HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(ORIGIN + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
