package com.dndtool.web;

import com.dndtool.service.CampaignSaveFileService.ExportFile;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/** Writes one already bounded JSON save without clearing security headers set by host filters. */
final class CampaignSaveDownloadResponse {
    private CampaignSaveDownloadResponse() {
    }

    static void write(HttpServletResponse response, ExportFile file) throws IOException {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(file, "save file");
        byte[] content = file.content();

        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(file.mediaType());
        response.setHeader(
                "Content-Disposition", "attachment; filename=\"" + file.fileName() + "\"");
        response.setContentLengthLong(content.length);
        // Bytes were completely validated before the response is committed, so oversize output
        // can never leave a truncated download with a successful status.
        response.getOutputStream().write(content);
    }
}
