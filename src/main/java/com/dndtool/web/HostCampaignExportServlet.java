package com.dndtool.web;

import com.dndtool.service.HostCommandService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Downloads the active campaign as one bounded save from the protected host boundary. */
@WebServlet(name = "HostCampaignExportServlet", urlPatterns = "/api/host/archive/export")
public final class HostCampaignExportServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private transient ActiveExporter exporter;

    public HostCampaignExportServlet() {
    }

    HostCampaignExportServlet(ActiveExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public void init() throws ServletException {
        if (exporter != null) return;
        try {
            Object resource = InitialContext.doLookup("java:comp/env/jdbc/DndToolSE");
            if (!(resource instanceof DataSource dataSource)) throw new NamingException();
            exporter = new HostCommandService(dataSource)::exportActive;
        } catch (NamingException exception) {
            throw new ServletException("Campaign export is unavailable");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        final HostCommandService.ExportResult result;
        try {
            result = exporter.export();
        } catch (SQLException exception) {
            error(response, 503, "DATABASE_UNAVAILABLE");
            return;
        }
        if (result.status() == HostCommandService.Status.READY) {
            CampaignSaveDownloadResponse.write(response, result.file());
            return;
        }
        int http = switch (result.status()) {
            case CAMPAIGN_NOT_FOUND -> 404;
            case EXPORT_TOO_LARGE -> 413;
            case MODULE_UNAVAILABLE -> 503;
            case INVALID_REQUEST -> 400;
            default -> 409;
        };
        error(response, http, result.status().name());
    }

    private static void error(HttpServletResponse response, int status, String code)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":\"ERROR\",\"code\":\"" + code + "\"}");
    }

    @FunctionalInterface
    interface ActiveExporter {
        HostCommandService.ExportResult export() throws SQLException;
    }
}
