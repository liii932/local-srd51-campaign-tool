package com.dndtool.web;

import com.dndtool.persistence.JdbcCampaignArchiveImportIdempotencyRepository;
import com.dndtool.persistence.JdbcCampaignArchiveImportRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CampaignArchiveConfirmationService;
import com.dndtool.service.CampaignSaveFileService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Executes the preview-bound archive import and rotates the host browser session on success. */
@WebServlet(name = "HostArchiveImportServlet", urlPatterns = "/api/host/archive/import")
@MultipartConfig(
        fileSizeThreshold = 0,
        maxFileSize = CampaignSaveFileService.MAX_BYTES,
        maxRequestSize = CampaignSaveFileService.MAX_BYTES + 65_536L)
public final class HostArchiveImportServlet extends HttpServlet {
    static final String PREVIEW_DIGEST_HEADER = "X-Archive-Preview-SHA256";
    static final String CONFIRMED_CAMPAIGN_HEADER = "X-Confirmed-Archive-Campaign-Key";

    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";
    private static final String EXPIRED_SESSION_COOKIE =
            HostRequestSecurityFilter.SESSION_COOKIE_NAME
                    + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0";

    private transient ArchiveImporter importer;

    public HostArchiveImportServlet() {
        // Required by the Servlet container.
    }

    HostArchiveImportServlet(ArchiveImporter importer) {
        this.importer = importer;
    }

    @Override
    public void init() throws ServletException {
        if (importer != null) return;
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            CampaignArchiveConfirmationService service =
                    new CampaignArchiveConfirmationService(
                            dataSource,
                            new JdbcModuleCatalogRepository(dataSource),
                            new JdbcCampaignArchiveImportIdempotencyRepository(),
                            new JdbcCampaignArchiveImportRepository());
            importer = service::confirm;
        } catch (NamingException exception) {
            throw new ServletException("Archive import is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "SESSION_REQUIRED");
            return;
        }

        ArchiveMultipartSupport.Result upload = ArchiveMultipartSupport.read(request);
        if (upload.status() != ArchiveMultipartSupport.Status.READY) {
            switch (upload.status()) {
                case MULTIPART_REQUIRED -> writeError(
                        response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "MULTIPART_REQUIRED");
                case INVALID_UPLOAD -> writeError(
                        response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_UPLOAD");
                case FILE_TOO_LARGE -> writeError(
                        response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "FILE_TOO_LARGE");
                case READY -> throw new AssertionError("READY upload has no content");
            }
            return;
        }

        String requestId = stringAttribute(
                request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
        String requestDigest = stringAttribute(
                request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE);
        CampaignArchiveConfirmationService.Request command =
                new CampaignArchiveConfirmationService.Request(
                        requestId,
                        requestDigest,
                        request.getHeader(PREVIEW_DIGEST_HEADER),
                        request.getHeader(CONFIRMED_CAMPAIGN_HEADER),
                        upload.content());

        final CampaignArchiveConfirmationService.Result result;
        try {
            result = importer.importArchive(command);
        } catch (IllegalArgumentException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_CONFIRMATION");
            return;
        } catch (SQLException exception) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE");
            return;
        }

        if (result.status() == CampaignArchiveConfirmationService.Status.COMPLETED) {
            invalidate(session);
            response.setHeader("Set-Cookie", EXPIRED_SESSION_COOKIE);
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", request.getContextPath() + "/host");
            return;
        }
        switch (result.status()) {
            case FILE_TOO_LARGE -> writeError(
                    response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "FILE_TOO_LARGE");
            case DIGEST_MISMATCH, INVALID_ARCHIVE, INVALID_CATALOG_REFERENCE -> writeError(
                    response, HttpServletResponse.SC_BAD_REQUEST, result.status().name());
            case MODULE_UNAVAILABLE, MODULE_HASH_MISMATCH, IDEMPOTENCY_CONFLICT,
                    ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED, PREVIEW_STATE_CHANGED,
                    UNEXPECTED_ARCHIVE_CONFIRMATION, STABLE_IDENTITY_CONFLICT -> writeError(
                    response, HttpServletResponse.SC_CONFLICT, result.status().name());
            case READY, COMPLETED -> writeError(
                    response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "IMPORT_UNAVAILABLE");
        }
    }

    private static void invalidate(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Concurrent invalidation already achieved the same security boundary.
        }
    }

    private static String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String string ? string : null;
    }

    private static void writeError(
            HttpServletResponse response, int status, String code) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setStatus(status);
        response.getWriter().write(
                "{\"status\":\"ERROR\",\"code\":\"" + code + "\"}");
    }

    @FunctionalInterface
    interface ArchiveImporter {
        CampaignArchiveConfirmationService.Result importArchive(
                CampaignArchiveConfirmationService.Request request) throws SQLException;
    }
}
