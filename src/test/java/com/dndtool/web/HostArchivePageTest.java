package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class HostArchivePageTest {
    private static final Path VIEW =
            Path.of("src/main/webapp/WEB-INF/views/host-archive.jsp");
    private static final Path SCRIPT =
            Path.of("src/main/webapp/host/assets/host-archive.js");
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");

    @Test
    void uploadSurfaceExistsOnlyUnderProtectedHostPaths() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        String webXml = Files.readString(WEB_XML, StandardCharsets.UTF_8);

        assertTrue(view.contains("/host/assets/host-archive.js"));
        assertTrue(view.contains("/api/host/archive/validate"));
        assertTrue(view.contains("/api/host/archive/import"));
        assertTrue(view.contains("id=\"archive-upload-form\""));
        assertTrue(view.contains("type=\"file\""));
        assertTrue(webXml.contains("<url-pattern>/host/*</url-pattern>"));
        assertTrue(webXml.contains("<url-pattern>/api/host/*</url-pattern>"));
        for (String forbidden : List.of("/display", "/api/public", "/api/player")) {
            assertFalse(view.contains(forbidden), forbidden);
        }
    }

    @Test
    void browserSendsRawDigestAndExistingHostCommandHeadersWithMultipartBody()
            throws Exception {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        for (String value : List.of(
                "SHA-256",
                "FormData",
                "X-CSRF-Token",
                "X-Host-State-Epoch",
                "X-Object-Row-Version",
                "X-Request-Id",
                "X-Request-Digest",
                "credentials: \"same-origin\"")) {
            assertTrue(script.contains(value), value);
        }
        assertFalse(script.contains("Content-Type"));
        assertFalse(script.contains("localStorage"));
        assertFalse(script.contains("sessionStorage"));
    }

    @Test
    void pageBindsExplicitImportConfirmationToTheDisplayedPreview() throws Exception {
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertTrue(view.contains("只有预览成功并再次明确确认后才会执行导入"));
        for (String id : List.of(
                "archive-preview",
                "archive-preview-mode",
                "archive-preview-campaign",
                "archive-preview-status",
                "archive-preview-sha256",
                "archive-preview-impact",
                "archive-preview-counts",
                "archive-preview-warning",
                "archive-import-button",
                "archive-import-result")) {
            assertTrue(view.contains("id=\"" + id + "\""), id);
        }
        assertTrue(view.contains("不可撤销警告"));
        assertTrue(view.contains("确认并执行导入"));
        for (String value : List.of(
                "CREATE", "OTHER_WILL_BE_ARCHIVED", "rawFileSha256",
                "irreversibleWarning", "replaceChildren", "textContent",
                "DND_TOOL_SE_IMPORT_CAMPAIGN_ARCHIVE_V1",
                "setInt32(0, value, false)", "int32(-1)",
                "X-Archive-Preview-SHA256",
                "X-Confirmed-Archive-Campaign-Key",
                "response.redirected", "window.location.replace")) {
            assertTrue(script.contains(value), value);
        }
        assertTrue(script.contains("form.dataset.importEndpoint"));
        assertTrue(script.contains("rawDigest !== currentPreview.rawFileSha256"));
        assertFalse(script.contains("localStorage"));
        assertFalse(script.contains("sessionStorage"));
    }
}
