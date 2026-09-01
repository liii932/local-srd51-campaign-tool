package com.dndtool.web;

import com.dndtool.persistence.JdbcCampaignCreationRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CampaignCreationService;
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

/** Creates a campaign through the protected host command boundary. */
@WebServlet(name = "HostCampaignCreationServlet", urlPatterns = "/api/host/campaigns")
public final class HostCampaignCreationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient CampaignCreator campaignCreator;

    public HostCampaignCreationServlet() {
        // Required by the Servlet container.
    }

    HostCampaignCreationServlet(CampaignCreator campaignCreator) {
        this.campaignCreator = campaignCreator;
    }

    @Override
    public void init() throws ServletException {
        if (campaignCreator != null) {
            return;
        }
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            CampaignCreationService service = new CampaignCreationService(
                    new JdbcModuleCatalogRepository(dataSource),
                    new JdbcCampaignCreationRepository(dataSource));
            campaignCreator = service::create;
        } catch (NamingException exception) {
            // Never include JNDI/provider details or credentials in the public failure.
            throw new ServletException("Campaign creation is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        String requestId = attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE);
        String requestDigest = attribute(
                request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE);

        final CampaignCreationService.Result result;
        try {
            result = campaignCreator.create(
                    request.getParameter("campaignName"), requestId, requestDigest);
        } catch (SQLException exception) {
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "{\"status\":\"ERROR\",\"code\":\"DATABASE_UNAVAILABLE\"}");
            return;
        }

        switch (result.status()) {
            case CREATED -> write(response, HttpServletResponse.SC_CREATED,
                    successJson("CREATED", result.campaignKey()));
            case ALREADY_SUCCEEDED -> write(response, HttpServletResponse.SC_OK,
                    successJson("ALREADY_SUCCEEDED", result.campaignKey()));
            case INVALID_REQUEST -> write(response, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"status\":\"ERROR\",\"code\":\"INVALID_REQUEST\"}");
            case IDEMPOTENCY_CONFLICT -> write(response, HttpServletResponse.SC_CONFLICT,
                    "{\"status\":\"ERROR\",\"code\":\"IDEMPOTENCY_CONFLICT\"}");
            case ACTIVE_CAMPAIGN_EXISTS -> write(response, HttpServletResponse.SC_CONFLICT,
                    "{\"status\":\"ERROR\",\"code\":\"ACTIVE_CAMPAIGN_EXISTS\"}");
            case RELEASE_UNAVAILABLE -> write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "{\"status\":\"ERROR\",\"code\":\"MODULE_UNAVAILABLE\"}");
            case MODULE_HASH_MISMATCH -> write(response, HttpServletResponse.SC_CONFLICT,
                    "{\"status\":\"ERROR\",\"code\":\"MODULE_HASH_MISMATCH\"}");
        }
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String string ? string : null;
    }

    private static String successJson(String status, String campaignKey) {
        // campaignKey is server-generated canonical UUID and needs no general JSON escaper.
        return "{\"status\":\"" + status + "\",\"campaignKey\":\""
                + campaignKey + "\"}";
    }

    private static void write(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.getWriter().write(body);
    }

    /** Small seam that keeps HTTP response tests independent from JNDI and MySQL. */
    @FunctionalInterface
    interface CampaignCreator {
        CampaignCreationService.Result create(
                String campaignName, String requestId, String requestDigestSha256)
                throws SQLException;
    }
}
