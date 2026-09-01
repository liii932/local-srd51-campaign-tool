package com.dndtool.web;

import com.dndtool.persistence.JdbcCharacterCreationRepository;
import com.dndtool.persistence.JdbcCampaignModuleBindingRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.security.HostRequestSecurityFilter;
import com.dndtool.service.CharacterCreationService;
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

/** Creates a blank PC/NPC or a template-backed NPC inside the protected host boundary. */
@WebServlet(name = "HostCharacterCreationServlet", urlPatterns = "/api/host/characters")
public final class HostCharacterCreationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private transient CharacterCreator characterCreator;

    public HostCharacterCreationServlet() {
        // Required by the Servlet container.
    }

    HostCharacterCreationServlet(CharacterCreator characterCreator) {
        this.characterCreator = characterCreator;
    }

    @Override
    public void init() throws ServletException {
        if (characterCreator != null) {
            return;
        }
        try {
            Object resource = InitialContext.doLookup(JNDI_NAME);
            if (!(resource instanceof DataSource dataSource)) {
                throw new NamingException("Configured resource is not a DataSource");
            }
            CharacterCreationService service = new CharacterCreationService(
                    new JdbcModuleCatalogRepository(dataSource),
                    new JdbcCharacterCreationRepository(dataSource),
                    new JdbcCampaignModuleBindingRepository(dataSource));
            characterCreator = service::create;
        } catch (NamingException exception) {
            throw new ServletException("Character creation is unavailable");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        final CharacterCreationService.Result result;
        try {
            result = characterCreator.create(
                    request.getParameter("campaignKey"),
                    request.getParameter("characterType"),
                    request.getParameter("characterName"),
                    request.getParameter("templateKey"),
                    attribute(request, HostRequestSecurityFilter.REQUEST_ID_ATTRIBUTE),
                    attribute(request, HostRequestSecurityFilter.REQUEST_DIGEST_ATTRIBUTE));
        } catch (SQLException exception) {
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "{\"status\":\"ERROR\",\"code\":\"DATABASE_UNAVAILABLE\"}");
            return;
        }

        switch (result.status()) {
            case CREATED -> write(response, HttpServletResponse.SC_CREATED,
                    successJson("CREATED", result));
            case ALREADY_SUCCEEDED -> write(response, HttpServletResponse.SC_OK,
                    successJson("ALREADY_SUCCEEDED", result));
            case INVALID_REQUEST -> error(
                    response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST");
            case IDEMPOTENCY_CONFLICT -> error(
                    response, HttpServletResponse.SC_CONFLICT, "IDEMPOTENCY_CONFLICT");
            case CAMPAIGN_UNAVAILABLE -> error(
                    response, HttpServletResponse.SC_CONFLICT, "CAMPAIGN_UNAVAILABLE");
            case TEMPLATE_UNAVAILABLE -> error(
                    response, HttpServletResponse.SC_BAD_REQUEST, "TEMPLATE_UNAVAILABLE");
            case MODULE_UNAVAILABLE -> error(
                    response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MODULE_UNAVAILABLE");
            case MODULE_HASH_MISMATCH -> error(
                    response, HttpServletResponse.SC_CONFLICT, "MODULE_HASH_MISMATCH");
        }
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String string ? string : null;
    }

    private static String successJson(
            String status, CharacterCreationService.Result result) {
        // Both values are server-owned canonical non-negative identifiers.
        return "{\"status\":\"" + status + "\",\"characterKey\":\""
                + result.characterKey() + "\",\"rowVersion\":" + result.rowVersion() + "}";
    }

    private static void error(HttpServletResponse response, int status, String code)
            throws IOException {
        write(response, status,
                "{\"status\":\"ERROR\",\"code\":\"" + code + "\"}");
    }

    private static void write(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.getWriter().write(body);
    }

    /** Test seam that keeps response tests independent from JNDI and MySQL. */
    @FunctionalInterface
    interface CharacterCreator {
        CharacterCreationService.Result create(
                String campaignKey,
                String characterType,
                String characterName,
                String templateKey,
                String requestId,
                String requestDigestSha256) throws SQLException;
    }
}
