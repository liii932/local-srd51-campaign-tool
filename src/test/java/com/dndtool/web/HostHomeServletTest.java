package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertTrue(table.body().contains("host-storage.js"));
            for (String path : List.of("/api/host/diagnostics/database", "/host/archive",
                    "/api/host/rules", "/player", "/display", "/api/public/state")) {
                assertEquals(404, get(client, path).statusCode(), path);
            }
        }
    }

    private static HttpResponse<String> get(HttpClient client, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(ORIGIN + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
