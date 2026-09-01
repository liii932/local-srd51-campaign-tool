package com.dndtool.web;

import com.dndtool.persistence.JdbcCampaignArchivePreviewRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CampaignArchiveDigest;
import com.dndtool.service.CampaignArchivePreviewService;
import com.dndtool.service.CampaignSaveFileService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.SQLException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Accepts one bounded archive upload through the protected host command boundary. */
@WebServlet(name = "HostArchiveUploadServlet", urlPatterns = "/api/host/archive/validate")
@MultipartConfig(
        fileSizeThreshold = 0,
        maxFileSize = CampaignSaveFileService.MAX_BYTES,
        maxRequestSize = CampaignSaveFileService.MAX_BYTES + 65_536L)
public final class HostArchiveUploadServlet extends HttpServlet {
    static final String ARCHIVE_PART_NAME = ArchiveMultipartSupport.ARCHIVE_PART_NAME;
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient ArchiveValidator validator;

    public HostArchiveUploadServlet() {
        // Required by the Servlet container.
    }

    HostArchiveUploadServlet(ArchiveValidator validator) {
        this.validator = validator;
    }

    @Override
    public void init() throws ServletException {
        if (validator != null) return;
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            CampaignArchivePreviewService service = new CampaignArchivePreviewService(
                    new JdbcModuleCatalogRepository(dataSource),
                    new JdbcCampaignArchivePreviewRepository(dataSource));
            validator = service::preview;
        } catch (NamingException exception) {
            throw new ServletException("Archive validation is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
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
        byte[] content = upload.content();

        String requestDigest = stringAttribute(
                request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE);
        if (!matchesDigest(content, requestDigest)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "REQUEST_DIGEST_MISMATCH");
            return;
        }

        final CampaignArchivePreviewService.Result result;
        try {
            result = validator.validate(content);
        } catch (SQLException exception) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE");
            return;
        }

        switch (result.status()) {
            case READY -> write(response, HttpServletResponse.SC_OK,
                    successJson(result.preview()));
            case FILE_TOO_LARGE -> writeError(response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "FILE_TOO_LARGE");
            case INVALID_ARCHIVE -> writeError(response,
                    HttpServletResponse.SC_BAD_REQUEST, "INVALID_ARCHIVE");
            case INVALID_CATALOG_REFERENCE -> writeError(response,
                    HttpServletResponse.SC_BAD_REQUEST, "INVALID_CATALOG_REFERENCE");
            case MODULE_UNAVAILABLE -> writeError(response,
                    HttpServletResponse.SC_CONFLICT, "MODULE_UNAVAILABLE");
            case MODULE_HASH_MISMATCH -> writeError(response,
                    HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
        }
    }

    private static boolean matchesDigest(byte[] content, String expected) {
        if (expected == null || expected.length() != 64) return false;
        return MessageDigest.isEqual(
                CampaignArchiveDigest.sha256(content)
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static String successJson(CampaignArchivePreviewService.Preview preview) {
        CampaignArchivePreviewService.ObjectCounts counts = preview.counts();
        String active = preview.activeCampaign() == null
                ? "null"
                : "{\"campaignKey\":" + JsonSupport.quote(
                        preview.activeCampaign().campaignKey())
                        + ",\"campaignName\":" + JsonSupport.quote(
                                preview.activeCampaign().campaignName()) + "}";
        return "{\"status\":\"READY\",\"preview\":{"
                + "\"mode\":\"" + preview.mode().name() + "\","
                + "\"campaign\":{\"campaignKey\":"
                + JsonSupport.quote(preview.campaign().campaignKey())
                + ",\"campaignName\":"
                + JsonSupport.quote(preview.campaign().campaignName())
                + ",\"campaignStatus\":\""
                + preview.campaign().campaignStatus() + "\"},"
                + "\"counts\":{"
                + "\"characters\":" + counts.characters() + ","
                + "\"fields\":" + counts.fields() + ","
                + "\"classLevels\":" + counts.classLevels() + ","
                + "\"skillProficiencies\":" + counts.skillProficiencies() + ","
                + "\"saveProficiencies\":" + counts.saveProficiencies() + ","
                + "\"items\":" + counts.items() + ","
                + "\"maps\":" + counts.maps() + ","
                + "\"encounters\":" + counts.encounters() + ","
                + "\"participants\":" + counts.participants() + ","
                + "\"recentEvents\":" + counts.recentEvents() + ","
                + "\"checks\":" + counts.checks() + "},"
                + "\"activeCampaignImpact\":\""
                + preview.activeCampaignImpact().name() + "\","
                + "\"activeCampaign\":" + active + ","
                + "\"rawFileSha256\":\"" + preview.rawFileSha256() + "\","
                + "\"irreversibleWarning\":" + preview.irreversibleWarning()
                + "}}";
    }

    private static String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String string ? string : null;
    }

    private static void writeError(
            HttpServletResponse response, int status, String code) throws IOException {
        write(response, status, "{\"status\":\"ERROR\",\"code\":\"" + code + "\"}");
    }

    private static void write(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.getWriter().write(body);
    }

    /** Test seam keeping the multipart HTTP contract independent of JNDI and MySQL. */
    @FunctionalInterface
    interface ArchiveValidator {
        CampaignArchivePreviewService.Result validate(byte[] content)
                throws SQLException;
    }
}
